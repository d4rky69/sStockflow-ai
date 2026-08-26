package com.stockflow.risk.api

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
class InventoryRiskControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate
) {
    @Test
    fun `risk engine detects stockout and near expiry conditions`() {
        jdbcTemplate.update(
            """INSERT INTO retailer
               (retailer_id, tenant_id, retailer_name, retailer_type, warehouse_id, city, region, credit_days, active)
               VALUES ('RET-RISK-001', 'TEN-ACME-PHARMA', 'Risk Test Retailer', 'PHARMACY', 'WH-CHENNAI', 'Chennai', 'SOUTH', 30, TRUE)"""
        )
        jdbcTemplate.update(
            """INSERT INTO sales_history
               (sales_history_id, sales_date, tenant_id, warehouse_id, retailer_id, sku_id,
                ordered_quantity, fulfilled_quantity, sales_quantity, return_quantity,
                lost_sales_quantity, unit_selling_price, promotion_id, stockout_flag)
               VALUES (?, ?, 'TEN-ACME-PHARMA', 'WH-CHENNAI', 'RET-RISK-001', 'SKU-PARA-650',
                       10000, 10000, 10000, 0, 0, ?, NULL, FALSE)""",
            UUID.randomUUID(), Date.valueOf("2026-07-26"), BigDecimal("25.00")
        )

        mockMvc.get("/api/v1/risks/stockout") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].riskType") { value("STOCKOUT_RISK") }
            jsonPath("$[0].warehouseId") { value("WH-CHENNAI") }
            jsonPath("$[0].severity") { value("HIGH") }
        }

        mockMvc.get("/api/v1/risks/expiry") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            param("days", "60")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].riskType") { value("NEAR_EXPIRY") }
            jsonPath("$[0].batchNumber") { value("B2456") }
            jsonPath("$[0].daysToExpiry") { value(45) }
        }
    }
}
