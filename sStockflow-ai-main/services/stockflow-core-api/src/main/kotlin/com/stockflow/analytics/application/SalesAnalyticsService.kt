package com.stockflow.analytics.application

import com.stockflow.common.errors.InvalidImportException
import com.stockflow.tenant.persistence.TenantRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Date
import java.time.LocalDate

@Service
class SalesAnalyticsService(
    private val jdbcTemplate: JdbcTemplate,
    private val tenantRepository: TenantRepository
) {
    fun summary(tenantId: String, dateFrom: LocalDate?, dateTo: LocalDate?): SalesSummaryView {
        requireTenant(tenantId)
        val (where, args) = dateFilter(tenantId, dateFrom, dateTo)
        return jdbcTemplate.queryForObject(
            """SELECT
                 COUNT(*) AS transaction_rows,
                 COALESCE(SUM(ordered_quantity), 0) AS ordered_quantity,
                 COALESCE(SUM(fulfilled_quantity), 0) AS fulfilled_quantity,
                 COALESCE(SUM(sales_quantity), 0) AS sales_quantity,
                 COALESCE(SUM(return_quantity), 0) AS return_quantity,
                 COALESCE(SUM(lost_sales_quantity), 0) AS lost_sales_quantity,
                 COALESCE(SUM(sales_quantity * unit_selling_price), 0) AS gross_sales_value,
                 COALESCE(SUM(CASE WHEN stockout_flag THEN 1 ELSE 0 END), 0) AS stockout_rows
               FROM sales_history sh
               $where""".trimIndent(),
            { rs, _ ->
                val ordered = rs.getLong("ordered_quantity")
                val fulfilled = rs.getLong("fulfilled_quantity")
                SalesSummaryView(
                    tenantId = tenantId,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    transactionRows = rs.getLong("transaction_rows"),
                    orderedQuantity = ordered,
                    fulfilledQuantity = fulfilled,
                    salesQuantity = rs.getLong("sales_quantity"),
                    returnQuantity = rs.getLong("return_quantity"),
                    lostSalesQuantity = rs.getLong("lost_sales_quantity"),
                    grossSalesValue = rs.getBigDecimal("gross_sales_value").setScale(2, RoundingMode.HALF_UP),
                    stockoutRows = rs.getLong("stockout_rows"),
                    fulfilmentRatePercent = if (ordered == 0L) BigDecimal.ZERO.setScale(2) else
                        BigDecimal.valueOf(fulfilled).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(ordered), 2, RoundingMode.HALF_UP)
                )
            },
            *args.toTypedArray()
        )!!
    }

    fun topSkus(tenantId: String, dateFrom: LocalDate?, dateTo: LocalDate?, limit: Int): List<TopSkuSalesView> {
        requireTenant(tenantId)
        val safeLimit = limit.coerceIn(1, 100)
        val (where, baseArgs) = dateFilter(tenantId, dateFrom, dateTo)
        val args = baseArgs + safeLimit
        return jdbcTemplate.query(
            """SELECT sh.sku_id, s.sku_name,
                 SUM(sh.sales_quantity) AS sales_quantity,
                 SUM(sh.sales_quantity * sh.unit_selling_price) AS gross_sales_value,
                 SUM(sh.lost_sales_quantity) AS lost_sales_quantity,
                 SUM(CASE WHEN sh.stockout_flag THEN 1 ELSE 0 END) AS stockout_rows
               FROM sales_history sh
               JOIN sku s ON s.sku_id = sh.sku_id AND s.tenant_id = sh.tenant_id
               $where
               GROUP BY sh.sku_id, s.sku_name
               ORDER BY sales_quantity DESC, sh.sku_id
               LIMIT ?""".trimIndent(),
            { rs, _ -> TopSkuSalesView(
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                salesQuantity = rs.getLong("sales_quantity"),
                grossSalesValue = rs.getBigDecimal("gross_sales_value").setScale(2, RoundingMode.HALF_UP),
                lostSalesQuantity = rs.getLong("lost_sales_quantity"),
                stockoutRows = rs.getLong("stockout_rows")
            ) },
            *args.toTypedArray()
        )
    }

    private fun dateFilter(tenantId: String, dateFrom: LocalDate?, dateTo: LocalDate?): Pair<String, List<Any>> {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw InvalidImportException("dateTo must be on or after dateFrom")
        }
        val clauses = mutableListOf("sh.tenant_id = ?")
        val args = mutableListOf<Any>(tenantId)
        if (dateFrom != null) {
            clauses += "sh.sales_date >= ?"
            args += Date.valueOf(dateFrom)
        }
        if (dateTo != null) {
            clauses += "sh.sales_date <= ?"
            args += Date.valueOf(dateTo)
        }
        return "WHERE ${clauses.joinToString(" AND ")}" to args
    }

    private fun requireTenant(tenantId: String) {
        if (tenantRepository.findByTenantIdAndActiveTrue(tenantId) == null) {
            throw InvalidImportException("Unknown or inactive tenant '$tenantId'")
        }
    }
}
