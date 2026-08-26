package com.stockflow.dashboard.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DashboardControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `dashboard overview returns database backed KPI data`() {
        mockMvc.get("/api/v1/dashboard/overview") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.asOf") { value("2026-07-26") }
            jsonPath("$.riskTotal") { value(4) }
            jsonPath("$.kpis.length()") { value(5) }
            jsonPath("$.networkMetrics.length()") { value(4) }
        }
    }
}
