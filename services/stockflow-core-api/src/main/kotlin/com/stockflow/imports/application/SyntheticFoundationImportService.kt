package com.stockflow.imports.application

import com.stockflow.common.errors.InvalidImportException
import com.stockflow.common.errors.ResourceNotFoundException
import com.stockflow.imports.persistence.ImportErrorEntity
import com.stockflow.imports.persistence.ImportErrorRepository
import com.stockflow.imports.persistence.ImportJobEntity
import com.stockflow.imports.persistence.ImportJobRepository
import com.stockflow.imports.persistence.ImportMode
import com.stockflow.imports.persistence.ImportStatus
import com.stockflow.inventory.persistence.BatchInventoryEntity
import com.stockflow.inventory.persistence.BatchInventoryRepository
import com.stockflow.product.persistence.ProductEntity
import com.stockflow.product.persistence.ProductRepository
import com.stockflow.product.persistence.SkuEntity
import com.stockflow.product.persistence.SkuRepository
import com.stockflow.tenant.persistence.TenantEntity
import com.stockflow.tenant.persistence.TenantRepository
import com.stockflow.warehouse.persistence.WarehouseEntity
import com.stockflow.warehouse.persistence.WarehouseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SyntheticFoundationImportService(
    private val importJobRepository: ImportJobRepository,
    private val importErrorRepository: ImportErrorRepository,
    private val tenantRepository: TenantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val productRepository: ProductRepository,
    private val skuRepository: SkuRepository,
    private val batchInventoryRepository: BatchInventoryRepository
) {
    companion object {
        private const val TENANTS = "reference/tenants.csv"
        private const val WAREHOUSES = "reference/warehouses.csv"
        private const val PRODUCTS = "reference/products.csv"
        private const val SKUS = "reference/skus.csv"
        private const val BATCHES = "transactions/batch_inventory.csv"
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

        val bytes = file.bytes
        val job = importJobRepository.save(
            ImportJobEntity(
                tenantId = tenantId,
                fileName = file.originalFilename ?: "synthetic-foundation.zip",
                fileSha256 = sha256(bytes),
                importMode = mode,
                status = ImportStatus.RUNNING
            )
        )

        return try {
            val entries = CsvPackageReader.readZip(bytes)
            val tenantFile = CsvPackageReader.requiredEntry(entries, TENANTS)
            val warehouseFile = CsvPackageReader.requiredEntry(entries, WAREHOUSES)
            val productFile = CsvPackageReader.requiredEntry(entries, PRODUCTS)
            val skuFile = CsvPackageReader.requiredEntry(entries, SKUS)
            val batchFile = CsvPackageReader.requiredEntry(entries, BATCHES)

            val tenantRows = CsvPackageReader.parseCsv(tenantFile.second)
            val warehouseRows = CsvPackageReader.parseCsv(warehouseFile.second)
            val productRows = CsvPackageReader.parseCsv(productFile.second)
            val skuRows = CsvPackageReader.parseCsv(skuFile.second)
            val batchRows = CsvPackageReader.parseCsv(batchFile.second)

            job.totalRows = (tenantRows.size + warehouseRows.size + productRows.size + skuRows.size + batchRows.size).toLong()

            val errors = mutableListOf<ImportErrorEntity>()
            val stagedTenant = stageTenant(job, tenantFile.first, tenantRows, tenantId, errors)
            val stagedWarehouses = stageWarehouses(job, warehouseFile.first, warehouseRows, tenantId, errors)
            val stagedProducts = stageProducts(job, productFile.first, productRows, tenantId, errors)
            val productIds = stagedProducts.map { it.productId }.toMutableSet().apply {
                addAll(productRepository.findAllByTenantIdOrderByProductName(tenantId).map { it.productId })
            }
            val stagedSkus = stageSkus(job, skuFile.first, skuRows, tenantId, productIds, errors)
            val warehouseIds = stagedWarehouses.map { it.warehouseId }.toMutableSet().apply {
                addAll(warehouseRepository.findAllByTenantIdAndActiveTrueOrderByWarehouseName(tenantId).map { it.warehouseId })
            }
            val skuIds = stagedSkus.map { it.skuId }.toMutableSet().apply {
                addAll(skuRepository.findAllByTenantIdAndActiveTrueOrderBySkuName(tenantId).map { it.skuId })
            }
            val stagedBatches = stageBatches(
                job, batchFile.first, batchRows, tenantId, warehouseIds, skuIds, errors
            )

            importErrorRepository.saveAll(errors)
            job.rejectedRows = errors.map { it.fileName to it.rowNumber }.distinct().size.toLong()
            job.ignoredRows = countIgnored(tenantRows, warehouseRows, productRows, skuRows, batchRows, tenantId)
            job.acceptedRows = listOf(
                if (stagedTenant == null) 0 else 1,
                stagedWarehouses.size,
                stagedProducts.size,
                stagedSkus.size,
                stagedBatches.size
            ).sum().toLong()

            val tenantAvailable = stagedTenant != null || tenantRepository.findById(tenantId).isPresent
            val shouldApply = mode == ImportMode.UPSERT && tenantAvailable && (!strict || errors.isEmpty())
            if (shouldApply) {
                stagedTenant?.let { tenantRepository.save(it) }
                warehouseRepository.saveAll(stagedWarehouses)
                productRepository.saveAll(stagedProducts)
                skuRepository.saveAll(stagedSkus)
                batchInventoryRepository.saveAll(stagedBatches)
            }

            job.status = when {
                mode == ImportMode.VALIDATE_ONLY && errors.isEmpty() -> ImportStatus.VALIDATED
                mode == ImportMode.VALIDATE_ONLY -> ImportStatus.REJECTED
                !tenantAvailable -> ImportStatus.REJECTED
                strict && errors.isNotEmpty() -> ImportStatus.REJECTED
                errors.isNotEmpty() -> ImportStatus.COMPLETED_WITH_ERRORS
                else -> ImportStatus.COMPLETED
            }
            job.message = when {
                mode == ImportMode.VALIDATE_ONLY && errors.isEmpty() -> "Package is valid and ready for UPSERT"
                mode == ImportMode.VALIDATE_ONLY -> "Validation completed with ${job.rejectedRows} rejected rows"
                !tenantAvailable -> "Import rejected because the tenant master row is missing or invalid"
                strict && errors.isNotEmpty() -> "Strict import rejected; no domain records were written"
                errors.isNotEmpty() -> "Valid records imported; rejected rows are available from the error endpoint"
                else -> "Foundation data imported successfully"
            }
            complete(job)
        } catch (error: Exception) {
            job.status = ImportStatus.FAILED
            job.message = error.message ?: error.javaClass.simpleName
            complete(job)
        }
    }

    @Transactional(readOnly = true)
    fun job(tenantId: String, importJobId: UUID): ImportJobView =
        importJobRepository.findByImportJobIdAndTenantId(importJobId, tenantId)?.toView()
            ?: throw ResourceNotFoundException("Import job '$importJobId' was not found")

    @Transactional(readOnly = true)
    fun recentJobs(tenantId: String): List<ImportJobView> =
        importJobRepository.findTop20ByTenantIdOrderByStartedAtDesc(tenantId).map { it.toView() }

    @Transactional(readOnly = true)
    fun errors(tenantId: String, importJobId: UUID): List<ImportErrorView> {
        job(tenantId, importJobId)
        return importErrorRepository.findAllByImportJobIdOrderByRowNumberAsc(importJobId).map {
            ImportErrorView(
                importErrorId = it.importErrorId,
                fileName = it.fileName,
                rowNumber = it.rowNumber,
                errorCode = it.errorCode,
                fieldName = it.fieldName,
                rejectedValue = it.rejectedValue,
                message = it.message
            )
        }
    }

    private fun stageTenant(
        job: ImportJobEntity,
        fileName: String,
        rows: List<CsvRow>,
        tenantId: String,
        errors: MutableList<ImportErrorEntity>
    ): TenantEntity? {
        val matching = rows.filter { it.values["tenant_id"] == tenantId }
        if (matching.isEmpty()) {
            errors += importError(job, fileName, 1, "TENANT_NOT_FOUND", "tenant_id", tenantId,
                "No tenant row matches X-Tenant-ID '$tenantId'")
            return null
        }
        if (matching.size > 1) {
            errors += importError(job, fileName, matching[1].rowNumber, "DUPLICATE_KEY", "tenant_id", tenantId,
                "More than one tenant row uses '$tenantId'")
        }
        return mapRow(job, fileName, matching.first(), errors) { row ->
            val existing = tenantRepository.findById(tenantId).orElse(TenantEntity(tenantId = tenantId))
            existing.tenantName = required(row, "tenant_name")
            existing.vertical = required(row, "vertical")
            existing.currency = required(row, "currency")
            existing.timezone = required(row, "timezone")
            existing.active = boolean(row, "active")
            existing.updatedAt = LocalDateTime.now()
            existing
        }
    }

    private fun stageWarehouses(
        job: ImportJobEntity,
        fileName: String,
        rows: List<CsvRow>,
        tenantId: String,
        errors: MutableList<ImportErrorEntity>
    ): List<WarehouseEntity> {
        val seen = mutableSetOf<String>()
        return rows.filter { it.values["tenant_id"] == tenantId }.mapNotNull { row ->
            mapRow(job, fileName, row, errors) { values ->
                val id = required(values, "warehouse_id")
                if (!seen.add(id)) invalid("DUPLICATE_KEY", "warehouse_id", id, "Duplicate warehouse '$id'")
                val globalEntity = warehouseRepository.findById(id).orElse(null)
                if (globalEntity != null && globalEntity.tenantId != tenantId) {
                    invalid("CROSS_TENANT_ID_CONFLICT", "warehouse_id", id,
                        "Warehouse ID '$id' already belongs to tenant '${globalEntity.tenantId}'")
                }
                val entity = globalEntity ?: WarehouseEntity(warehouseId = id, tenantId = tenantId)
                entity.warehouseName = required(values, "warehouse_name")
                entity.city = required(values, "city")
                entity.state = required(values, "state")
                entity.country = values["country"].orEmpty().ifBlank { "India" }
                entity.latitude = decimalOrNull(values, "latitude")
                entity.longitude = decimalOrNull(values, "longitude")
                entity.capacityUnits = nonNegativeLong(values, "capacity_units")
                entity.coldChainAvailable = boolean(values, "cold_chain_available")
                entity.active = boolean(values, "active")
                entity.updatedAt = LocalDateTime.now()
                entity
            }
        }
    }

    private fun stageProducts(
        job: ImportJobEntity,
        fileName: String,
        rows: List<CsvRow>,
        tenantId: String,
        errors: MutableList<ImportErrorEntity>
    ): List<ProductEntity> {
        val seen = mutableSetOf<String>()
        return rows.filter { it.values["tenant_id"] == tenantId }.mapNotNull { row ->
            mapRow(job, fileName, row, errors) { values ->
                val id = required(values, "product_id")
                if (!seen.add(id)) invalid("DUPLICATE_KEY", "product_id", id, "Duplicate product '$id'")
                val globalEntity = productRepository.findById(id).orElse(null)
                if (globalEntity != null && globalEntity.tenantId != tenantId) {
                    invalid("CROSS_TENANT_ID_CONFLICT", "product_id", id,
                        "Product ID '$id' already belongs to tenant '${globalEntity.tenantId}'")
                }
                val entity = globalEntity ?: ProductEntity(productId = id, tenantId = tenantId)
                entity.productName = required(values, "product_name")
                entity.vertical = required(values, "vertical")
                entity.category = required(values, "category")
                entity.criticality = values["criticality"].orEmpty().ifBlank { "MEDIUM" }
                entity.shelfLifeControlled = boolean(values, "shelf_life_controlled", default = true)
                entity.coldChainRequired = boolean(values, "cold_chain_required", default = false)
                entity.active = boolean(values, "active")
                entity.updatedAt = LocalDateTime.now()
                entity
            }
        }
    }

    private fun stageSkus(
        job: ImportJobEntity,
        fileName: String,
        rows: List<CsvRow>,
        tenantId: String,
        productIds: Set<String>,
        errors: MutableList<ImportErrorEntity>
    ): List<SkuEntity> {
        val seen = mutableSetOf<String>()
        return rows.mapNotNull { row ->
            val declaredTenant = row.values["tenant_id"].orEmpty()
            val productId = row.values["product_id"].orEmpty()
            val belongsToTenant = when {
                declaredTenant.isNotBlank() -> declaredTenant == tenantId
                productId in productIds -> true
                else -> false
            }
            if (!belongsToTenant) return@mapNotNull null
            mapRow(job, fileName, row, errors) { values ->
                val id = required(values, "sku_id")
                if (!seen.add(id)) invalid("DUPLICATE_KEY", "sku_id", id, "Duplicate SKU '$id'")
                val resolvedProductId = required(values, "product_id")
                if (resolvedProductId !in productIds) {
                    invalid("UNKNOWN_PRODUCT", "product_id", resolvedProductId,
                        "Product '$resolvedProductId' does not exist in tenant '$tenantId'")
                }
                val globalEntity = skuRepository.findById(id).orElse(null)
                if (globalEntity != null && globalEntity.tenantId != tenantId) {
                    invalid("CROSS_TENANT_ID_CONFLICT", "sku_id", id,
                        "SKU ID '$id' already belongs to tenant '${globalEntity.tenantId}'")
                }
                val entity = globalEntity ?: SkuEntity(skuId = id, tenantId = tenantId)
                entity.productId = resolvedProductId
                entity.skuName = required(values, "sku_name")
                entity.brand = values["brand"]?.ifBlank { null }
                entity.packSize = values["pack_size"]?.ifBlank { null }
                entity.baseUom = required(values, "base_uom")
                entity.unitCost = nonNegativeDecimal(values, "unit_cost")
                entity.sellingPrice = nonNegativeDecimal(values, "selling_price")
                entity.currency = required(values, "currency")
                entity.minimumSafetyStock = nonNegativeLong(values, "minimum_safety_stock")
                entity.reorderMultiple = positiveLong(values, "reorder_multiple")
                entity.defaultShelfLifeDays = positiveIntOrNull(values, "default_shelf_life_days")
                entity.fefoRequired = boolean(values, "fefo_required")
                entity.demandProfile = values["demand_profile"].orEmpty().ifBlank { "STABLE" }
                entity.active = boolean(values, "active")
                entity.updatedAt = LocalDateTime.now()
                entity
            }
        }
    }

    private fun stageBatches(
        job: ImportJobEntity,
        fileName: String,
        rows: List<CsvRow>,
        tenantId: String,
        warehouseIds: Set<String>,
        skuIds: Set<String>,
        errors: MutableList<ImportErrorEntity>
    ): List<BatchInventoryEntity> {
        val seen = mutableSetOf<String>()
        return rows.filter { it.values["tenant_id"] == tenantId }.mapNotNull { row ->
            mapRow(job, fileName, row, errors) { values ->
                val snapshotDate = localDate(values, "snapshot_date")
                val warehouseId = required(values, "warehouse_id")
                val skuId = required(values, "sku_id")
                val batchNumber = required(values, "batch_number")
                val key = "$snapshotDate|$tenantId|$warehouseId|$skuId|$batchNumber"
                if (!seen.add(key)) invalid("DUPLICATE_KEY", "batch_number", batchNumber,
                    "Duplicate batch snapshot '$key'")
                if (warehouseId !in warehouseIds) invalid("UNKNOWN_WAREHOUSE", "warehouse_id", warehouseId,
                    "Warehouse '$warehouseId' does not exist in tenant '$tenantId'")
                if (skuId !in skuIds) invalid("UNKNOWN_SKU", "sku_id", skuId,
                    "SKU '$skuId' does not exist in tenant '$tenantId'")

                val available = nonNegativeLong(values, "available_quantity")
                val reserved = nonNegativeLong(values, "reserved_quantity")
                val blocked = nonNegativeLong(values, "blocked_quantity")
                if (reserved + blocked > available) {
                    invalid("INVALID_ALLOCATION", "reserved_quantity", reserved.toString(),
                        "Reserved plus blocked quantity cannot exceed available quantity")
                }
                val manufacture = localDateOrNull(values, "manufacture_date")
                val expiry = localDateOrNull(values, "expiry_date")
                if (manufacture != null && expiry != null && !expiry.isAfter(manufacture)) {
                    invalid("INVALID_DATE_RANGE", "expiry_date", expiry.toString(),
                        "Expiry date must be after manufacture date")
                }
                val entity = batchInventoryRepository
                    .findBySnapshotDateAndTenantIdAndWarehouseIdAndSkuIdAndBatchNumber(
                        snapshotDate, tenantId, warehouseId, skuId, batchNumber
                    ) ?: BatchInventoryEntity(
                    batchInventoryId = UUID.nameUUIDFromBytes(key.toByteArray()),
                    snapshotDate = snapshotDate,
                    tenantId = tenantId,
                    warehouseId = warehouseId,
                    skuId = skuId,
                    batchNumber = batchNumber
                )
                entity.manufactureDate = manufacture
                entity.expiryDate = expiry
                entity.availableQuantity = available
                entity.reservedQuantity = reserved
                entity.blockedQuantity = blocked
                entity.unitCost = nonNegativeDecimal(values, "unit_cost")
                entity.currency = required(values, "currency")
                entity.storageConditionCode = required(values, "storage_condition_code")
                entity.lastMovementAt = offsetDateTimeOrNull(values, "last_movement_at")?.toLocalDateTime()
                entity.updatedAt = LocalDateTime.now()
                entity
            }
        }
    }

    private fun countIgnored(
        tenantRows: List<CsvRow>,
        warehouseRows: List<CsvRow>,
        productRows: List<CsvRow>,
        skuRows: List<CsvRow>,
        batchRows: List<CsvRow>,
        tenantId: String
    ): Long {
        val explicitTenantRows = listOf(tenantRows, warehouseRows, productRows, batchRows)
            .sumOf { rows -> rows.count { it.values["tenant_id"] != tenantId } }
        val ignoredSkus = skuRows.count {
            it.values["tenant_id"].orEmpty().isNotBlank() && it.values["tenant_id"] != tenantId
        }
        return (explicitTenantRows + ignoredSkus).toLong()
    }

    private fun <T> mapRow(
        job: ImportJobEntity,
        fileName: String,
        row: CsvRow,
        errors: MutableList<ImportErrorEntity>,
        mapper: (Map<String, String>) -> T
    ): T? = try {
        mapper(row.values)
    } catch (error: RowValidationException) {
        errors += importError(job, fileName, row.rowNumber, error.code, error.fieldName,
            error.rejectedValue, error.message)
        null
    } catch (error: Exception) {
        errors += importError(job, fileName, row.rowNumber, "INVALID_VALUE", null, null,
            error.message ?: error.javaClass.simpleName)
        null
    }

    private fun complete(job: ImportJobEntity): ImportJobView {
        job.completedAt = LocalDateTime.now()
        job.updatedAt = LocalDateTime.now()
        return importJobRepository.save(job).toView()
    }

    private fun importError(
        job: ImportJobEntity,
        fileName: String,
        rowNumber: Long,
        code: String,
        fieldName: String?,
        rejectedValue: String?,
        message: String
    ) = ImportErrorEntity(
        importJobId = job.importJobId,
        fileName = fileName,
        rowNumber = rowNumber,
        errorCode = code,
        fieldName = fieldName,
        rejectedValue = rejectedValue?.take(500),
        message = message.take(1000)
    )

    private fun required(values: Map<String, String>, field: String): String =
        values[field]?.trim()?.takeIf { it.isNotEmpty() }
            ?: invalid("REQUIRED_FIELD", field, null, "Field '$field' is required")

    private fun boolean(values: Map<String, String>, field: String, default: Boolean? = null): Boolean {
        val raw = values[field]?.trim().orEmpty()
        if (raw.isBlank() && default != null) return default
        return when (raw.lowercase()) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> invalid("INVALID_BOOLEAN", field, raw, "Field '$field' must be true or false")
        }
    }

    private fun nonNegativeLong(values: Map<String, String>, field: String): Long {
        val raw = required(values, field)
        return raw.toLongOrNull()?.takeIf { it >= 0 }
            ?: invalid("INVALID_NUMBER", field, raw, "Field '$field' must be a non-negative whole number")
    }

    private fun positiveLong(values: Map<String, String>, field: String): Long {
        val raw = required(values, field)
        return raw.toLongOrNull()?.takeIf { it > 0 }
            ?: invalid("INVALID_NUMBER", field, raw, "Field '$field' must be greater than zero")
    }

    private fun positiveIntOrNull(values: Map<String, String>, field: String): Int? {
        val raw = values[field]?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.toIntOrNull()?.takeIf { it > 0 }
            ?: invalid("INVALID_NUMBER", field, raw, "Field '$field' must be blank or greater than zero")
    }

    private fun nonNegativeDecimal(values: Map<String, String>, field: String): BigDecimal {
        val raw = required(values, field)
        return raw.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
            ?: invalid("INVALID_NUMBER", field, raw, "Field '$field' must be a non-negative decimal")
    }

    private fun decimalOrNull(values: Map<String, String>, field: String): BigDecimal? {
        val raw = values[field]?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.toBigDecimalOrNull()
            ?: invalid("INVALID_NUMBER", field, raw, "Field '$field' must be a decimal")
    }

    private fun localDate(values: Map<String, String>, field: String): LocalDate {
        val raw = required(values, field)
        return runCatching { LocalDate.parse(raw) }.getOrElse {
            invalid("INVALID_DATE", field, raw, "Field '$field' must use ISO date format yyyy-MM-dd")
        }
    }

    private fun localDateOrNull(values: Map<String, String>, field: String): LocalDate? {
        val raw = values[field]?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { LocalDate.parse(raw) }.getOrElse {
            invalid("INVALID_DATE", field, raw, "Field '$field' must be blank or use yyyy-MM-dd")
        }
    }

    private fun offsetDateTimeOrNull(values: Map<String, String>, field: String): OffsetDateTime? {
        val raw = values[field]?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw) }.getOrElse {
            invalid("INVALID_TIMESTAMP", field, raw, "Field '$field' must be an ISO-8601 timestamp")
        }
    }

    private fun invalid(code: String, field: String?, value: String?, message: String): Nothing =
        throw RowValidationException(code, field, value, message)

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

private class RowValidationException(
    val code: String,
    val fieldName: String?,
    val rejectedValue: String?,
    override val message: String
) : RuntimeException(message)
