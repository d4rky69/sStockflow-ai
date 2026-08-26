package com.stockflow.imports.application

import com.stockflow.common.errors.InvalidImportException
import com.stockflow.imports.persistence.ImportErrorEntity
import com.stockflow.imports.persistence.ImportErrorRepository
import com.stockflow.imports.persistence.ImportJobEntity
import com.stockflow.imports.persistence.ImportJobRepository
import com.stockflow.imports.persistence.ImportMode
import com.stockflow.imports.persistence.ImportStatus
import com.stockflow.product.persistence.SkuRepository
import com.stockflow.tenant.persistence.TenantRepository
import com.stockflow.warehouse.persistence.WarehouseRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.SqlParameterValue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.security.MessageDigest
import java.sql.Date
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class SyntheticSalesImportService(
    private val importJobRepository: ImportJobRepository,
    private val importErrorRepository: ImportErrorRepository,
    private val tenantRepository: TenantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val skuRepository: SkuRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    companion object {
        private const val RETAILERS = "reference/retailers.csv"
        private const val SALES = "transactions/sales_history.csv"
        private const val MAX_RECORDED_ERRORS = 1000
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    fun importPackage(
        tenantId: String,
        file: MultipartFile,
        mode: ImportMode,
        strict: Boolean
    ): ImportJobView {
        if (file.isEmpty) throw InvalidImportException("Uploaded ZIP is empty")
        if (!file.originalFilename.orEmpty().lowercase().endsWith(".zip")) {
            throw InvalidImportException("Only ZIP packages are supported")
        }
        if (tenantRepository.findByTenantIdAndActiveTrue(tenantId) == null) {
            throw InvalidImportException("Tenant '$tenantId' must be imported before sales history")
        }

        val bytes = file.bytes
        val job = importJobRepository.save(
            ImportJobEntity(
                tenantId = tenantId,
                importType = "SYNTHETIC_SALES",
                fileName = file.originalFilename ?: "synthetic-sales.zip",
                fileSha256 = sha256(bytes),
                importMode = mode,
                status = ImportStatus.RUNNING
            )
        )

        return try {
            val entries = CsvPackageReader.readZip(bytes)
            val retailerFile = CsvPackageReader.requiredEntry(entries, RETAILERS)
            val salesFile = CsvPackageReader.requiredEntry(entries, SALES)
            val retailerRows = CsvPackageReader.parseCsv(retailerFile.second)
            val salesRows = CsvPackageReader.parseCsv(salesFile.second)
            job.totalRows = (retailerRows.size + salesRows.size).toLong()

            val warehouseIds = warehouseRepository
                .findAllByTenantIdAndActiveTrueOrderByWarehouseName(tenantId)
                .map { it.warehouseId }
                .toSet()
            val skuIds = skuRepository
                .findAllByTenantIdAndActiveTrueOrderBySkuName(tenantId)
                .map { it.skuId }
                .toSet()

            val errors = mutableListOf<ImportErrorEntity>()
            var rejectedRows = 0L
            fun reject(fileName: String, rowNumber: Long, code: String, field: String?, value: String?, message: String) {
                rejectedRows++
                if (errors.size < MAX_RECORDED_ERRORS) {
                    errors += ImportErrorEntity(
                        importJobId = job.importJobId,
                        fileName = fileName,
                        rowNumber = rowNumber,
                        errorCode = code,
                        fieldName = field,
                        rejectedValue = value?.take(500),
                        message = message.take(1000)
                    )
                }
            }

            val retailers = mutableListOf<RetailerRow>()
            val retailerIds = mutableSetOf<String>()
            retailerRows.filter { it.values["tenant_id"] == tenantId }.forEach { row ->
                try {
                    val values = row.values
                    val retailerId = required(values, "retailer_id")
                    if (!retailerIds.add(retailerId)) rowError("DUPLICATE_KEY", "retailer_id", retailerId, "Duplicate retailer '$retailerId'")
                    val warehouseId = required(values, "warehouse_id")
                    if (warehouseId !in warehouseIds) rowError("UNKNOWN_WAREHOUSE", "warehouse_id", warehouseId, "Warehouse '$warehouseId' is not available for tenant '$tenantId'")
                    retailers += RetailerRow(
                        retailerId = retailerId,
                        tenantId = tenantId,
                        retailerName = required(values, "retailer_name"),
                        retailerType = required(values, "retailer_type"),
                        warehouseId = warehouseId,
                        city = required(values, "city"),
                        region = required(values, "region"),
                        creditDays = nonNegativeInt(values, "credit_days"),
                        active = boolean(values, "active")
                    )
                } catch (error: SalesRowValidationException) {
                    reject(retailerFile.first, row.rowNumber, error.code, error.field, error.value, error.message ?: error.code)
                }
            }

            val existingRetailerIds = jdbcTemplate.queryForList(
                "SELECT retailer_id FROM retailer WHERE tenant_id = ?",
                String::class.java,
                tenantId
            ).toMutableSet()
            existingRetailerIds.addAll(retailers.map { it.retailerId })

            val sales = mutableListOf<SalesRow>()
            val seenSalesKeys = mutableSetOf<String>()
            salesRows.filter { it.values["tenant_id"] == tenantId }.forEach { row ->
                try {
                    val values = row.values
                    val salesDate = localDate(values, "sales_date")
                    val warehouseId = required(values, "warehouse_id")
                    val retailerId = required(values, "retailer_id")
                    val skuId = required(values, "sku_id")
                    if (warehouseId !in warehouseIds) rowError("UNKNOWN_WAREHOUSE", "warehouse_id", warehouseId, "Warehouse '$warehouseId' is not available for tenant '$tenantId'")
                    if (retailerId !in existingRetailerIds) rowError("UNKNOWN_RETAILER", "retailer_id", retailerId, "Retailer '$retailerId' is not available for tenant '$tenantId'")
                    if (skuId !in skuIds) rowError("UNKNOWN_SKU", "sku_id", skuId, "SKU '$skuId' is not available for tenant '$tenantId'")
                    val naturalKey = "$tenantId|$salesDate|$warehouseId|$retailerId|$skuId"
                    if (!seenSalesKeys.add(naturalKey)) rowError("DUPLICATE_KEY", "sku_id", skuId, "Duplicate sales row '$naturalKey'")

                    val ordered = nonNegativeLong(values, "ordered_quantity")
                    val fulfilled = nonNegativeLong(values, "fulfilled_quantity")
                    val sold = nonNegativeLong(values, "sales_quantity")
                    val returned = nonNegativeLong(values, "return_quantity")
                    val lost = nonNegativeLong(values, "lost_sales_quantity")
                    if (fulfilled > ordered) rowError("INVALID_QUANTITY", "fulfilled_quantity", fulfilled.toString(), "Fulfilled quantity cannot exceed ordered quantity")
                    if (sold > fulfilled) rowError("INVALID_QUANTITY", "sales_quantity", sold.toString(), "Sales quantity cannot exceed fulfilled quantity")
                    if (returned > sold) rowError("INVALID_QUANTITY", "return_quantity", returned.toString(), "Return quantity cannot exceed sales quantity")

                    sales += SalesRow(
                        salesHistoryId = UUID.nameUUIDFromBytes(naturalKey.toByteArray()),
                        salesDate = salesDate,
                        tenantId = tenantId,
                        warehouseId = warehouseId,
                        retailerId = retailerId,
                        skuId = skuId,
                        orderedQuantity = ordered,
                        fulfilledQuantity = fulfilled,
                        salesQuantity = sold,
                        returnQuantity = returned,
                        lostSalesQuantity = lost,
                        unitSellingPrice = nonNegativeDecimal(values, "unit_selling_price"),
                        promotionId = values["promotion_id"]?.trim()?.ifBlank { null },
                        stockoutFlag = boolean(values, "stockout_flag")
                    )
                } catch (error: SalesRowValidationException) {
                    reject(salesFile.first, row.rowNumber, error.code, error.field, error.value, error.message ?: error.code)
                }
            }

            importErrorRepository.saveAll(errors)
            job.rejectedRows = rejectedRows
            job.ignoredRows = (
                retailerRows.count { it.values["tenant_id"] != tenantId } +
                    salesRows.count { it.values["tenant_id"] != tenantId }
                ).toLong()
            job.acceptedRows = (retailers.size + sales.size).toLong()

            val shouldApply = mode == ImportMode.UPSERT && (!strict || rejectedRows == 0L)
            if (shouldApply) {
                upsertRetailers(retailers)
                upsertSales(sales)
                job.status = if (rejectedRows == 0L) ImportStatus.COMPLETED else ImportStatus.COMPLETED_WITH_ERRORS
                job.message = "Retailers and sales history imported successfully"
            } else if (mode == ImportMode.VALIDATE_ONLY && rejectedRows == 0L) {
                job.status = ImportStatus.VALIDATED
                job.message = "Package is valid and ready for UPSERT"
            } else {
                job.status = ImportStatus.REJECTED
                job.message = if (errors.size >= MAX_RECORDED_ERRORS) {
                    "Package rejected. The first $MAX_RECORDED_ERRORS validation errors were recorded."
                } else {
                    "Package rejected because validation errors were found"
                }
            }
            complete(job)
        } catch (error: Exception) {
            job.status = ImportStatus.FAILED
            job.message = error.message?.take(1000)
            complete(job)
            throw error
        }
    }

    private fun upsertRetailers(rows: List<RetailerRow>) {
        if (rows.isEmpty()) return
        val h2 = databaseProduct().contains("H2", ignoreCase = true)
        val sql = if (h2) {
            """MERGE INTO retailer (retailer_id, tenant_id, retailer_name, retailer_type, warehouse_id, city, region, credit_days, active, updated_at)
               KEY(retailer_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""".trimIndent()
        } else {
            """INSERT INTO retailer (retailer_id, tenant_id, retailer_name, retailer_type, warehouse_id, city, region, credit_days, active, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
               ON CONFLICT (retailer_id) DO UPDATE SET
                 tenant_id = EXCLUDED.tenant_id,
                 retailer_name = EXCLUDED.retailer_name,
                 retailer_type = EXCLUDED.retailer_type,
                 warehouse_id = EXCLUDED.warehouse_id,
                 city = EXCLUDED.city,
                 region = EXCLUDED.region,
                 credit_days = EXCLUDED.credit_days,
                 active = EXCLUDED.active,
                 updated_at = CURRENT_TIMESTAMP""".trimIndent()
        }
        rows.chunked(BATCH_SIZE).forEach { batch ->
            jdbcTemplate.batchUpdate(sql, batch.map {
                arrayOf<Any>(it.retailerId, it.tenantId, it.retailerName, it.retailerType, it.warehouseId, it.city, it.region, it.creditDays, it.active)
            })
        }
    }

    private fun upsertSales(rows: List<SalesRow>) {
        if (rows.isEmpty()) return
        val h2 = databaseProduct().contains("H2", ignoreCase = true)
        val sql = if (h2) {
            """MERGE INTO sales_history (
                 sales_history_id, sales_date, tenant_id, warehouse_id, retailer_id, sku_id,
                 ordered_quantity, fulfilled_quantity, sales_quantity, return_quantity,
                 lost_sales_quantity, unit_selling_price, promotion_id, stockout_flag, updated_at
               ) KEY(sales_history_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""".trimIndent()
        } else {
            """INSERT INTO sales_history (
                 sales_history_id, sales_date, tenant_id, warehouse_id, retailer_id, sku_id,
                 ordered_quantity, fulfilled_quantity, sales_quantity, return_quantity,
                 lost_sales_quantity, unit_selling_price, promotion_id, stockout_flag, updated_at
               ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
               ON CONFLICT (sales_history_id) DO UPDATE SET
                 ordered_quantity = EXCLUDED.ordered_quantity,
                 fulfilled_quantity = EXCLUDED.fulfilled_quantity,
                 sales_quantity = EXCLUDED.sales_quantity,
                 return_quantity = EXCLUDED.return_quantity,
                 lost_sales_quantity = EXCLUDED.lost_sales_quantity,
                 unit_selling_price = EXCLUDED.unit_selling_price,
                 promotion_id = EXCLUDED.promotion_id,
                 stockout_flag = EXCLUDED.stockout_flag,
                 updated_at = CURRENT_TIMESTAMP""".trimIndent()
        }
        rows.chunked(BATCH_SIZE).forEach { batch ->
            jdbcTemplate.batchUpdate(sql, batch.map {
                arrayOf<Any>(
                    it.salesHistoryId, Date.valueOf(it.salesDate), it.tenantId, it.warehouseId,
                    it.retailerId, it.skuId, it.orderedQuantity, it.fulfilledQuantity,
                    it.salesQuantity, it.returnQuantity, it.lostSalesQuantity,
                    it.unitSellingPrice, SqlParameterValue(Types.VARCHAR, it.promotionId), it.stockoutFlag
                )
            })
        }
    }

    private fun databaseProduct(): String = jdbcTemplate.dataSource!!.connection.use { it.metaData.databaseProductName }

    private fun complete(job: ImportJobEntity): ImportJobView {
        job.completedAt = LocalDateTime.now()
        job.updatedAt = LocalDateTime.now()
        return importJobRepository.save(job).toView()
    }

    private fun required(values: Map<String, String>, field: String): String =
        values[field]?.trim()?.takeIf { it.isNotEmpty() }
            ?: rowError("REQUIRED_FIELD", field, null, "Field '$field' is required")

    private fun nonNegativeLong(values: Map<String, String>, field: String): Long {
        val raw = required(values, field)
        return raw.toLongOrNull()?.takeIf { it >= 0 }
            ?: rowError("INVALID_NUMBER", field, raw, "Field '$field' must be a non-negative whole number")
    }

    private fun nonNegativeInt(values: Map<String, String>, field: String): Int {
        val raw = required(values, field)
        return raw.toIntOrNull()?.takeIf { it >= 0 }
            ?: rowError("INVALID_NUMBER", field, raw, "Field '$field' must be a non-negative whole number")
    }

    private fun nonNegativeDecimal(values: Map<String, String>, field: String): BigDecimal {
        val raw = required(values, field)
        return raw.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
            ?: rowError("INVALID_NUMBER", field, raw, "Field '$field' must be a non-negative decimal")
    }

    private fun boolean(values: Map<String, String>, field: String): Boolean {
        val raw = required(values, field)
        return when (raw.lowercase()) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> rowError("INVALID_BOOLEAN", field, raw, "Field '$field' must be true or false")
        }
    }

    private fun localDate(values: Map<String, String>, field: String): LocalDate {
        val raw = required(values, field)
        return runCatching { LocalDate.parse(raw) }.getOrElse {
            rowError("INVALID_DATE", field, raw, "Field '$field' must use yyyy-MM-dd")
        }
    }

    private fun rowError(code: String, field: String?, value: String?, message: String): Nothing =
        throw SalesRowValidationException(code, field, value, message)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun ImportJobEntity.toView() = ImportJobView(
        importJobId = importJobId,
        tenantId = tenantId,
        importType = importType,
        fileName = fileName,
        fileSha256 = fileSha256,
        importMode = importMode,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        totalRows = totalRows,
        acceptedRows = acceptedRows,
        rejectedRows = rejectedRows,
        ignoredRows = ignoredRows,
        message = message
    )
}

private data class RetailerRow(
    val retailerId: String,
    val tenantId: String,
    val retailerName: String,
    val retailerType: String,
    val warehouseId: String,
    val city: String,
    val region: String,
    val creditDays: Int,
    val active: Boolean
)

private data class SalesRow(
    val salesHistoryId: UUID,
    val salesDate: LocalDate,
    val tenantId: String,
    val warehouseId: String,
    val retailerId: String,
    val skuId: String,
    val orderedQuantity: Long,
    val fulfilledQuantity: Long,
    val salesQuantity: Long,
    val returnQuantity: Long,
    val lostSalesQuantity: Long,
    val unitSellingPrice: BigDecimal,
    val promotionId: String?,
    val stockoutFlag: Boolean
)

private class SalesRowValidationException(
    val code: String,
    val field: String?,
    val value: String?,
    override val message: String
) : RuntimeException(message)
