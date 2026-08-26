package com.stockflow.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DevelopmentActionAuthorizationTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `local mode supplies an auditable user context for transfer proposals`() {
        val userId = "local-proposer-${UUID.randomUUID()}"

        mockMvc.post("/api/v1/actions/transfers") {
            header("X-Tenant-ID", "TEN-ACME-PHARMA")
            header("X-StockFlow-User-ID", userId)
            header("X-StockFlow-User-Email", "local-proposer@stockflow.local")
            header("Idempotency-Key", "test-${UUID.randomUUID()}")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "skuId": "SKU-PARA-650",
                  "quantity": 10,
                  "sourceWarehouseId": "WH-CHENNAI",
                  "destinationWarehouseId": "WH-BENGALURU",
                  "currency": "INR",
                  "reason": "Validate local proposal authorization"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.createdBy") { value(userId) }
        }
    }
}
