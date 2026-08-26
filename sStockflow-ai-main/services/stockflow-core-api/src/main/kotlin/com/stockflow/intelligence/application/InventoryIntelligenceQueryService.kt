package com.stockflow.intelligence.application

import com.stockflow.common.errors.InvalidImportException
import com.stockflow.tenant.persistence.TenantRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class InventoryIntelligenceQueryService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val tenantRepository: TenantRepository
) {
    fun requireTenant(tenantId: String) {
        if (tenantRepository.findByTenantIdAndActiveTrue(tenantId) == null) {
            throw InvalidImportException("Unknown or inactive tenant '$tenantId'")
        }
    }

    fun inventoryAsOfDate(tenantId: String): LocalDate? {
        requireTenant(tenantId)
        return jdbcTemplate.query(
            "SELECT MAX(snapshot_date) AS as_of_date FROM batch_inventory WHERE tenant_id = :tenantId",
            mapOf("tenantId" to tenantId)
        ) { rs, _ -> rs.getDate("as_of_date")?.toLocalDate() }.firstOrNull()
    }

    fun salesAsOfDate(tenantId: String): LocalDate? {
        requireTenant(tenantId)
        return jdbcTemplate.query(
            "SELECT MAX(sales_date) AS as_of_date FROM sales_history WHERE tenant_id = :tenantId",
            mapOf("tenantId" to tenantId)
        ) { rs, _ -> rs.getDate("as_of_date")?.toLocalDate() }.firstOrNull()
    }

    fun inventoryPositions(tenantId: String, asOfDate: LocalDate): List<InventoryPositionMetric> {
        val params = mapOf("tenantId" to tenantId, "asOfDate" to asOfDate)
        return jdbcTemplate.query(
            """
            SELECT
                bi.warehouse_id,
                w.warehouse_name,
                bi.sku_id,
                s.sku_name,
                s.minimum_safety_stock,
                SUM(bi.available_quantity) AS available_quantity,
                SUM(bi.reserved_quantity) AS reserved_quantity,
                SUM(bi.blocked_quantity) AS blocked_quantity,
                SUM(bi.available_quantity - bi.reserved_quantity - bi.blocked_quantity) AS usable_quantity,
                SUM((bi.available_quantity - bi.reserved_quantity - bi.blocked_quantity) * bi.unit_cost) AS inventory_value,
                SUM((bi.reserved_quantity + bi.blocked_quantity) * bi.unit_cost) AS blocked_value,
                MIN(bi.expiry_date) AS next_expiry_date
            FROM batch_inventory bi
            JOIN warehouse w ON w.warehouse_id = bi.warehouse_id AND w.tenant_id = bi.tenant_id
            JOIN sku s ON s.sku_id = bi.sku_id AND s.tenant_id = bi.tenant_id
            WHERE bi.tenant_id = :tenantId
              AND bi.snapshot_date = :asOfDate
            GROUP BY bi.warehouse_id, w.warehouse_name, bi.sku_id, s.sku_name, s.minimum_safety_stock
            ORDER BY bi.warehouse_id, bi.sku_id
            """.trimIndent(),
            params
        ) { rs, _ ->
            InventoryPositionMetric(
                warehouseId = rs.getString("warehouse_id"),
                warehouseName = rs.getString("warehouse_name"),
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                minimumSafetyStock = rs.getLong("minimum_safety_stock"),
                availableQuantity = rs.getLong("available_quantity"),
                reservedQuantity = rs.getLong("reserved_quantity"),
                blockedQuantity = rs.getLong("blocked_quantity"),
                usableQuantity = rs.getLong("usable_quantity"),
                inventoryValue = rs.getBigDecimal("inventory_value") ?: BigDecimal.ZERO,
                blockedValue = rs.getBigDecimal("blocked_value") ?: BigDecimal.ZERO,
                nextExpiryDate = rs.getDate("next_expiry_date")?.toLocalDate()
            )
        }
    }

    fun demandPositions(tenantId: String, asOfDate: LocalDate): List<DemandPositionMetric> {
        val params = MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("asOfDate", asOfDate)
            .addValue("start7", asOfDate.minusDays(6))
            .addValue("start30", asOfDate.minusDays(29))
            .addValue("start90", asOfDate.minusDays(89))

        return jdbcTemplate.query(
            """
            SELECT
                sh.warehouse_id,
                w.warehouse_name,
                sh.sku_id,
                s.sku_name,
                s.minimum_safety_stock,
                SUM(CASE WHEN sh.sales_date BETWEEN :start7 AND :asOfDate THEN sh.sales_quantity ELSE 0 END) AS sales_7,
                SUM(CASE WHEN sh.sales_date BETWEEN :start30 AND :asOfDate THEN sh.sales_quantity ELSE 0 END) AS sales_30,
                SUM(sh.sales_quantity) AS sales_90,
                SUM(CASE WHEN sh.sales_date BETWEEN :start30 AND :asOfDate THEN sh.return_quantity ELSE 0 END) AS returns_30,
                SUM(CASE WHEN sh.sales_date BETWEEN :start30 AND :asOfDate THEN sh.lost_sales_quantity ELSE 0 END) AS lost_sales_30,
                SUM(CASE WHEN sh.sales_date BETWEEN :start30 AND :asOfDate AND sh.stockout_flag THEN 1 ELSE 0 END) AS stockout_rows_30,
                SUM(CASE WHEN sh.sales_date BETWEEN :start30 AND :asOfDate THEN sh.sales_quantity * sh.unit_selling_price ELSE 0 END) AS gross_sales_value_30
            FROM sales_history sh
            JOIN warehouse w ON w.warehouse_id = sh.warehouse_id AND w.tenant_id = sh.tenant_id
            JOIN sku s ON s.sku_id = sh.sku_id AND s.tenant_id = sh.tenant_id
            WHERE sh.tenant_id = :tenantId
              AND sh.sales_date BETWEEN :start90 AND :asOfDate
            GROUP BY sh.warehouse_id, w.warehouse_name, sh.sku_id, s.sku_name, s.minimum_safety_stock
            ORDER BY sh.warehouse_id, sh.sku_id
            """.trimIndent(),
            params
        ) { rs, _ ->
            DemandPositionMetric(
                warehouseId = rs.getString("warehouse_id"),
                warehouseName = rs.getString("warehouse_name"),
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                minimumSafetyStock = rs.getLong("minimum_safety_stock"),
                sales7 = rs.getLong("sales_7"),
                sales30 = rs.getLong("sales_30"),
                sales90 = rs.getLong("sales_90"),
                returns30 = rs.getLong("returns_30"),
                lostSales30 = rs.getLong("lost_sales_30"),
                stockoutRows30 = rs.getLong("stockout_rows_30"),
                grossSalesValue30 = rs.getBigDecimal("gross_sales_value_30") ?: BigDecimal.ZERO
            )
        }
    }

    fun expiryBatches(tenantId: String, asOfDate: LocalDate): List<ExpiryBatchMetric> {
        val params = mapOf("tenantId" to tenantId, "asOfDate" to asOfDate)
        return jdbcTemplate.query(
            """
            SELECT
                bi.batch_inventory_id,
                bi.warehouse_id,
                w.warehouse_name,
                bi.sku_id,
                s.sku_name,
                bi.batch_number,
                bi.expiry_date,
                bi.available_quantity,
                bi.reserved_quantity,
                bi.blocked_quantity,
                (bi.available_quantity - bi.reserved_quantity - bi.blocked_quantity) AS usable_quantity,
                (bi.available_quantity - bi.reserved_quantity - bi.blocked_quantity) * bi.unit_cost AS inventory_value
            FROM batch_inventory bi
            JOIN warehouse w ON w.warehouse_id = bi.warehouse_id AND w.tenant_id = bi.tenant_id
            JOIN sku s ON s.sku_id = bi.sku_id AND s.tenant_id = bi.tenant_id
            WHERE bi.tenant_id = :tenantId
              AND bi.snapshot_date = :asOfDate
              AND bi.expiry_date IS NOT NULL
            ORDER BY bi.expiry_date, bi.warehouse_id, bi.sku_id, bi.batch_number
            """.trimIndent(),
            params
        ) { rs, _ ->
            ExpiryBatchMetric(
                batchInventoryId = rs.getString("batch_inventory_id"),
                warehouseId = rs.getString("warehouse_id"),
                warehouseName = rs.getString("warehouse_name"),
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                batchNumber = rs.getString("batch_number"),
                expiryDate = rs.getDate("expiry_date").toLocalDate(),
                availableQuantity = rs.getLong("available_quantity"),
                reservedQuantity = rs.getLong("reserved_quantity"),
                blockedQuantity = rs.getLong("blocked_quantity"),
                usableQuantity = rs.getLong("usable_quantity"),
                inventoryValue = rs.getBigDecimal("inventory_value") ?: BigDecimal.ZERO
            )
        }
    }
}

data class PositionKey(val warehouseId: String, val skuId: String)

data class InventoryPositionMetric(
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val minimumSafetyStock: Long,
    val availableQuantity: Long,
    val reservedQuantity: Long,
    val blockedQuantity: Long,
    val usableQuantity: Long,
    val inventoryValue: BigDecimal,
    val blockedValue: BigDecimal,
    val nextExpiryDate: LocalDate?
) {
    fun key(): PositionKey = PositionKey(warehouseId, skuId)
}

data class DemandPositionMetric(
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val minimumSafetyStock: Long,
    val sales7: Long,
    val sales30: Long,
    val sales90: Long,
    val returns30: Long,
    val lostSales30: Long,
    val stockoutRows30: Long,
    val grossSalesValue30: BigDecimal
) {
    fun key(): PositionKey = PositionKey(warehouseId, skuId)
}

data class ExpiryBatchMetric(
    val batchInventoryId: String,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val batchNumber: String,
    val expiryDate: LocalDate,
    val availableQuantity: Long,
    val reservedQuantity: Long,
    val blockedQuantity: Long,
    val usableQuantity: Long,
    val inventoryValue: BigDecimal
)
