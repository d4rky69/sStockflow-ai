package com.stockflow.foundation.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `foundation summary is tenant scoped and database backed`() {
        mockMvc.get("/api/v1/foundation/summary") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.tenant.tenantId") { value("TEN-ACME-PHARMA") }
            jsonPath("$.warehouseCount") { value(3) }
            jsonPath("$.productCount") { value(1) }
            jsonPath("$.skuCount") { value(1) }
            jsonPath("$.batchCount") { value(3) }
        }
    }

    @Test
    fun `batch API calculates usable quantity`() {
        mockMvc.get("/api/v1/inventory/batches") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            param("warehouseId", "WH-CHENNAI")
            param("skuId", "SKU-PARA-650")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].batchNumber") { value("B2456") }
            jsonPath("$[0].availableQuantity") { value(2450) }
            jsonPath("$[0].usableQuantity") { value(2391) }
        }
    }

    @Test
    fun `tenant header is mandatory`() {
        mockMvc.get("/api/v1/foundation/summary")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("MISSING_HEADER") }
            }
    }

    @Test
    fun `unknown tenant is rejected`() {
        mockMvc.get("/api/v1/foundation/summary") {
            header("X-Tenant-ID", "TEN-UNKNOWN")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INVALID_TENANT") }
        }
    }
}
