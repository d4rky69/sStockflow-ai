package com.stockflow.fleetbase

import com.stockflow.common.errors.ApiErrorHandler
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FleetbaseIntegrationControllerTest {
    private val client = FleetbaseClient(false, "https://api.fleetbase.io/v1", "", false, 2, 2)
    private val tenantBinding = FleetbaseTenantBinding("TEN-ACME-PHARMA", "")
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FleetbaseIntegrationController(FleetbaseIntegrationService(client, tenantBinding)))
        .setControllerAdvice(ApiErrorHandler())
        .build()

    @Test
    fun `status is secret safe while integration is disabled`() {
        mockMvc.get("/api/v1/integrations/fleetbase/status") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.enabled") { value(false) }
                jsonPath("$.configured") { value(false) }
                jsonPath("$.mode") { value("UNCONFIGURED") }
                jsonPath("$.writeOperationsEnabled") { value(false) }
                jsonPath("$.tenantMapping.mapped") { value(true) }
                jsonPath("$.tenantMapping.tenantId") { value("TEN-ACME-PHARMA") }
                jsonPath("$.apiKey") { doesNotExist() }
            }
    }

    @Test
    fun `vehicle listing fails safely when integration is disabled`() {
        mockMvc.get("/api/v1/integrations/fleetbase/vehicles") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("FLEETBASE_DISABLED") }
        }
    }

    @Test
    fun `vehicle listing rejects an unmapped tenant before calling Fleetbase`() {
        mockMvc.get("/api/v1/integrations/fleetbase/vehicles") {
            header("X-Tenant-ID", "TEN-OTHER")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FLEETBASE_TENANT_NOT_MAPPED") }
        }
    }
}
