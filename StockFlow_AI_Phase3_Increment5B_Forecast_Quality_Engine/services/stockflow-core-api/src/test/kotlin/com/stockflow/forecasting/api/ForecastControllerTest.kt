package com.stockflow.forecasting.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ForecastControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate
) {
    @Test
    fun `forecast run evaluates models and persists daily values`() {
        jdbcTemplate.update(
            """INSERT INTO retailer
               (retailer_id, tenant_id, retailer_name, retailer_type, warehouse_id, city, region, credit_days, active)
               VALUES ('RET-FORECAST-001', 'TEN-ACME-PHARMA', 'Forecast Test Retailer', 'PHARMACY',
                       'WH-CHENNAI', 'Chennai', 'SOUTH', 30, TRUE)"""
        )

        val start = LocalDate.of(2026, 5, 28)
        repeat(60) { offset ->
            val salesDate = start.plusDays(offset.toLong())
            val weekdayFactor = when (salesDate.dayOfWeek.value) {
                6, 7 -> 75L
                else -> 100L
            }
            val quantity = weekdayFactor + (offset / 14) * 5L
            jdbcTemplate.update(
                """INSERT INTO sales_history
                   (sales_history_id, sales_date, tenant_id, warehouse_id, retailer_id, sku_id,
                    ordered_quantity, fulfilled_quantity, sales_quantity, return_quantity,
                    lost_sales_quantity, unit_selling_price, promotion_id, stockout_flag)
                   VALUES (?, ?, 'TEN-ACME-PHARMA', 'WH-CHENNAI', 'RET-FORECAST-001', 'SKU-PARA-650',
                           ?, ?, ?, 0, 0, ?, NULL, FALSE)""",
                UUID.randomUUID(), Date.valueOf(salesDate), quantity, quantity, quantity, BigDecimal("25.00")
            )
        }

        val response = mockMvc.post("/api/v1/forecasts/runs") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "asOfDate": "2026-07-26",
                  "horizonDays": 7,
                  "historyDays": 60,
                  "warehouseId": "WH-CHENNAI",
                  "skuId": "SKU-PARA-650"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.positionsRequested") { value(1) }
            jsonPath("$.positionsProcessed") { value(1) }
            jsonPath("$.positionsFailed") { value(0) }
        }.andReturn().response.contentAsString

        check(response.contains("forecastRunId"))

        mockMvc.get("/api/v1/forecasts/latest") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            param("warehouseId", "WH-CHENNAI")
            param("skuId", "SKU-PARA-650")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].warehouseId") { value("WH-CHENNAI") }
            jsonPath("$[0].skuId") { value("SKU-PARA-650") }
            jsonPath("$[0].horizonDays") { value(7) }
            jsonPath("$[0].forecastValues.length()") { value(7) }
            jsonPath("$[0].trainingSampleCount") { value(60) }
            jsonPath("$[0].selectedModel") { exists() }
            jsonPath("$[0].demandPattern") { exists() }
            jsonPath("$[0].wape") { exists() }
            jsonPath("$[0].smape") { exists() }
            jsonPath("$[0].demandPattern") { exists() }
            jsonPath("$[0].wape") { exists() }
            jsonPath("$[0].smape") { exists() }
        }

        mockMvc.get("/api/v1/forecasts/model-performance") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(8) }
        }

        val selectedModelCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM forecast_model_performance WHERE selected_model = TRUE",
            Long::class.java
        )
        check(selectedModelCount == 1L)

        mockMvc.get("/api/v1/forecasts/summary") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.positionsForecasted") { value(1) }
            jsonPath("$.horizonDays") { value(7) }
        }

        mockMvc.get("/api/v1/forecasts/accuracy-summary") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.positionsEvaluated") { value(1) }
            jsonPath("$.averageWape") { exists() }
            jsonPath("$.modelUsage") { exists() }
            jsonPath("$.demandPatternUsage") { exists() }
        }

        mockMvc.get("/api/v1/forecasts/configuration") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabledModels.length()") { value(8) }
            jsonPath("$.outlierTreatmentEnabled") { value(true) }
        }

        mockMvc.get("/api/v1/forecasts/accuracy-summary") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.positionsEvaluated") { value(1) }
            jsonPath("$.averageWape") { exists() }
            jsonPath("$.modelUsage") { exists() }
            jsonPath("$.demandPatternUsage") { exists() }
        }

        mockMvc.get("/api/v1/forecasts/configuration") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabledModels.length()") { value(8) }
            jsonPath("$.outlierTreatmentEnabled") { value(true) }
        }
    }

    @Test
    fun `forecast run rejects unsupported horizon`() {
        mockMvc.post("/api/v1/forecasts/runs") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            contentType = MediaType.APPLICATION_JSON
            content = """{"horizonDays": 14, "historyDays": 60}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_FORECAST") }
        }
    }
}
