package com.stockflow.analytics.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Date
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DemandAnalyticsControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate
) {
    @Test
    fun `demand summary and sku metrics are calculated from sales history`() {
        insertRetailer("RET-DEMAND-001")
        insertSale("RET-DEMAND-001", Date.valueOf("2026-07-01"), 20, 20, 0, false)
        insertSale("RET-DEMAND-001", Date.valueOf("2026-07-02"), 15, 10, 5, true)

        mockMvc.get("/api/v1/analytics/demand/summary") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            param("windowDays", "30")
        }.andExpect {
            status { isOk() }
            jsonPath("$.transactionRows") { value(2) }
            jsonPath("$.salesQuantity") { value(30) }
            jsonPath("$.lostSalesQuantity") { value(5) }
            jsonPath("$.stockoutRows") { value(1) }
            jsonPath("$.averageDailyDemand") { value(1.00) }
        }

        mockMvc.get("/api/v1/analytics/demand/skus") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            param("windowDays", "30")
            param("limit", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].skuId") { value("SKU-PARA-650") }
            jsonPath("$[0].salesQuantity") { value(30) }
        }
    }

    private fun insertRetailer(retailerId: String) {
        jdbcTemplate.update(
            """INSERT INTO retailer
               (retailer_id, tenant_id, retailer_name, retailer_type, warehouse_id, city, region, credit_days, active)
               VALUES (?, 'TEN-ACME-PHARMA', 'Demand Test Retailer', 'PHARMACY', 'WH-CHENNAI', 'Chennai', 'SOUTH', 30, TRUE)""",
            retailerId
        )
    }

    private fun insertSale(
        retailerId: String,
        salesDate: Date,
        ordered: Long,
        sold: Long,
        lost: Long,
        stockout: Boolean
    ) {
        jdbcTemplate.update(
            """INSERT INTO sales_history
               (sales_history_id, sales_date, tenant_id, warehouse_id, retailer_id, sku_id,
                ordered_quantity, fulfilled_quantity, sales_quantity, return_quantity,
                lost_sales_quantity, unit_selling_price, promotion_id, stockout_flag)
               VALUES (?, ?, 'TEN-ACME-PHARMA', 'WH-CHENNAI', ?, 'SKU-PARA-650', ?, ?, ?, 0, ?, ?, NULL, ?)""",
            UUID.randomUUID(), salesDate, retailerId, ordered, sold, sold, lost, BigDecimal("25.00"), stockout
        )
    }
}
