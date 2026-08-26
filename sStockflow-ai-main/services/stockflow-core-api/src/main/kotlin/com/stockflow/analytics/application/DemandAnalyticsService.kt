package com.stockflow.analytics.application

import com.stockflow.intelligence.application.InventoryIntelligenceQueryService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class DemandAnalyticsService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val intelligenceQueryService: InventoryIntelligenceQueryService
) {
    fun summary(tenantId: String, windowDays: Int): DemandSummaryView {
        val days = windowDays.coerceIn(1, 365)
        val asOfDate = intelligenceQueryService.salesAsOfDate(tenantId)
            ?: intelligenceQueryService.inventoryAsOfDate(tenantId)
            ?: LocalDate.now()
        val fromDate = asOfDate.minusDays(days.toLong() - 1)
        val params = mapOf("tenantId" to tenantId, "fromDate" to fromDate, "asOfDate" to asOfDate)

        return jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS transaction_rows,
                COUNT(DISTINCT sku_id) AS sku_count,
                COUNT(DISTINCT warehouse_id) AS warehouse_count,
                COALESCE(SUM(ordered_quantity), 0) AS ordered_quantity,
                COALESCE(SUM(fulfilled_quantity), 0) AS fulfilled_quantity,
                COALESCE(SUM(sales_quantity), 0) AS sales_quantity,
                COALESCE(SUM(return_quantity), 0) AS return_quantity,
                COALESCE(SUM(lost_sales_quantity), 0) AS lost_sales_quantity,
                COALESCE(SUM(CASE WHEN stockout_flag THEN 1 ELSE 0 END), 0) AS stockout_rows,
                COALESCE(SUM(sales_quantity * unit_selling_price), 0) AS gross_sales_value
            FROM sales_history
            WHERE tenant_id = :tenantId
              AND sales_date BETWEEN :fromDate AND :asOfDate
            """.trimIndent(),
            params
        ) { rs, _ ->
            val ordered = rs.getLong("ordered_quantity")
            val fulfilled = rs.getLong("fulfilled_quantity")
            val sales = rs.getLong("sales_quantity")
            DemandSummaryView(
                tenantId = tenantId,
                asOfDate = asOfDate,
                windowDays = days,
                transactionRows = rs.getLong("transaction_rows"),
                skuCount = rs.getLong("sku_count"),
                warehouseCount = rs.getLong("warehouse_count"),
                salesQuantity = sales,
                returnQuantity = rs.getLong("return_quantity"),
                lostSalesQuantity = rs.getLong("lost_sales_quantity"),
                stockoutRows = rs.getLong("stockout_rows"),
                averageDailyDemand = BigDecimal.valueOf(sales)
                    .divide(BigDecimal.valueOf(days.toLong()), 2, RoundingMode.HALF_UP),
                grossSalesValue = (rs.getBigDecimal("gross_sales_value") ?: BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP),
                fulfilmentRatePercent = if (ordered == 0L) BigDecimal.ZERO.setScale(2) else
                    BigDecimal.valueOf(fulfilled).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(ordered), 2, RoundingMode.HALF_UP)
            )
        }!!
    }

    fun skus(tenantId: String, windowDays: Int, limit: Int): List<DemandSkuView> {
        val days = windowDays.coerceIn(1, 365)
        val safeLimit = limit.coerceIn(1, 250)
        val asOfDate = intelligenceQueryService.salesAsOfDate(tenantId)
            ?: intelligenceQueryService.inventoryAsOfDate(tenantId)
            ?: LocalDate.now()
        val params = MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("fromDate", asOfDate.minusDays(days.toLong() - 1))
            .addValue("asOfDate", asOfDate)
            .addValue("limit", safeLimit)

        return jdbcTemplate.query(
            """
            SELECT
                sh.warehouse_id,
                w.warehouse_name,
                sh.sku_id,
                s.sku_name,
                SUM(sh.sales_quantity) AS sales_quantity,
                SUM(sh.return_quantity) AS return_quantity,
                SUM(sh.lost_sales_quantity) AS lost_sales_quantity,
                SUM(CASE WHEN sh.stockout_flag THEN 1 ELSE 0 END) AS stockout_rows,
                SUM(sh.sales_quantity * sh.unit_selling_price) AS gross_sales_value
            FROM sales_history sh
            JOIN warehouse w ON w.warehouse_id = sh.warehouse_id AND w.tenant_id = sh.tenant_id
            JOIN sku s ON s.sku_id = sh.sku_id AND s.tenant_id = sh.tenant_id
            WHERE sh.tenant_id = :tenantId
              AND sh.sales_date BETWEEN :fromDate AND :asOfDate
            GROUP BY sh.warehouse_id, w.warehouse_name, sh.sku_id, s.sku_name
            ORDER BY sales_quantity DESC, sh.warehouse_id, sh.sku_id
            LIMIT :limit
            """.trimIndent(),
            params
        ) { rs, _ ->
            val sales = rs.getLong("sales_quantity")
            DemandSkuView(
                warehouseId = rs.getString("warehouse_id"),
                warehouseName = rs.getString("warehouse_name"),
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                salesQuantity = sales,
                returnQuantity = rs.getLong("return_quantity"),
                lostSalesQuantity = rs.getLong("lost_sales_quantity"),
                stockoutRows = rs.getLong("stockout_rows"),
                averageDailyDemand = BigDecimal.valueOf(sales)
                    .divide(BigDecimal.valueOf(days.toLong()), 2, RoundingMode.HALF_UP),
                grossSalesValue = (rs.getBigDecimal("gross_sales_value") ?: BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
            )
        }
    }

    fun trend(tenantId: String, weeks: Int = 16): DemandTrendView {
        val safeWeeks = weeks.coerceIn(4, 26)
        val asOfDate = intelligenceQueryService.salesAsOfDate(tenantId)
            ?: intelligenceQueryService.inventoryAsOfDate(tenantId)
            ?: LocalDate.now()
        val startDate = asOfDate.minusDays((safeWeeks * 7L) - 1)
        val params = mapOf("tenantId" to tenantId, "startDate" to startDate, "asOfDate" to asOfDate)
        val daily = jdbcTemplate.query(
            """
            SELECT sales_date, SUM(sales_quantity) AS sales_quantity
            FROM sales_history
            WHERE tenant_id = :tenantId
              AND sales_date BETWEEN :startDate AND :asOfDate
            GROUP BY sales_date
            ORDER BY sales_date
            """.trimIndent(),
            params
        ) { rs, _ -> rs.getDate("sales_date").toLocalDate() to rs.getLong("sales_quantity") }.toMap()

        val labels = mutableListOf<String>()
        val actual = mutableListOf<Long>()
        val formatter = DateTimeFormatter.ofPattern("d MMM")
        repeat(safeWeeks) { index ->
            val weekStart = startDate.plusDays(index * 7L)
            val weekEnd = minOf(weekStart.plusDays(6), asOfDate)
            labels += weekStart.format(formatter)
            var total = 0L
            var day = weekStart
            while (!day.isAfter(weekEnd)) {
                total += daily[day] ?: 0L
                day = day.plusDays(1)
            }
            actual += total
        }

        val forecast = actual.indices.map { index ->
            val start = maxOf(0, index - 2)
            val values = actual.subList(start, index + 1)
            if (values.isEmpty()) 0L else values.sum() / values.size
        }
        return DemandTrendView(tenantId, asOfDate, labels, actual, forecast)
    }
}
