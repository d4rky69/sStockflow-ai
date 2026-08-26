package com.stockflow.fleetbase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(properties = [
    "stockflow.fleetbase.webhook-secret=webhook-test-secret",
    "stockflow.fleetbase.tenant-id=TEN-ACME-PHARMA",
    "stockflow.fleetbase.organization-id=company_test"
])
@ActiveProfiles("test")
@Transactional
class FleetbaseWebhookServiceTest {
    @Autowired lateinit var service: FleetbaseWebhookService
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `signed webhook is applied once and exposes reconciliation issue`() {
        val userId = "fleetbase-webhook-test-user"
        val proposalId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        val orderId = "order_webhook_test"
        jdbc.update("INSERT INTO app_user(user_id,email,display_name) VALUES (?, ?, ?)", userId, "fleetbase-webhook@stockflow.local", "Webhook Test")
        jdbc.update(
            """INSERT INTO action_proposal(proposal_id,tenant_id,proposal_type,status,sku_id,quantity,source_warehouse_id,
                destination_warehouse_id,currency,reason,idempotency_key,created_by)
               VALUES (?, 'TEN-ACME-PHARMA','TRANSFER','APPROVED','SKU-PARA-650',5,'WH-CHENNAI','WH-BENGALURU','INR','Webhook test',?,?)""",
            proposalId, "webhook-proposal-$proposalId", userId
        )
        jdbc.update(
            """INSERT INTO transfer_execution(execution_id,tenant_id,proposal_id,status,sku_id,source_warehouse_id,
                destination_warehouse_id,quantity,idempotency_key,created_by)
               VALUES (?, 'TEN-ACME-PHARMA',?,'RESERVED','SKU-PARA-650','WH-CHENNAI','WH-BENGALURU',5,?,?)""",
            executionId, proposalId, "webhook-execution-$executionId", userId
        )
        jdbc.update(
            """INSERT INTO fleetbase_order_link(link_id,tenant_id,transfer_execution_id,proposal_id,fleetbase_organization_id,
                fleetbase_order_id,fleetbase_internal_id,link_status,idempotency_key,request_fingerprint,created_by)
               VALUES (?, 'TEN-ACME-PHARMA',?,?, 'company_test',?,'SF-WEBHOOK-TEST','CREATED',?,'fingerprint',?)""",
            linkId, executionId, proposalId, orderId, "webhook-link-$linkId", userId
        )
        val raw = """{"id":"evt_stockflow_01","event":"order.dispatched","api_version":"v1","data":{"id":"$orderId","status":"dispatched"}}"""
        val signature = signature(raw)

        val accepted = service.receive(raw, "sha256=$signature")
        val duplicate = service.receive(raw, "sha256=$signature")

        assertTrue(accepted.accepted)
        assertEquals("APPLIED", accepted.status)
        assertTrue(duplicate.duplicate)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fleetbase_webhook_event WHERE event_id='evt_stockflow_01'", Int::class.java))
        assertEquals("REMOTE_AHEAD", jdbc.queryForObject("SELECT reconciliation_status FROM fleetbase_order_link WHERE link_id=?", String::class.java, linkId))
        val audit = service.audit("TEN-ACME-PHARMA")
        assertEquals(1, audit.webhookEvents)
        assertTrue(audit.reconciliationIssues >= 1)
    }

    @Test
    fun `invalid signature is rejected before payload processing`() {
        assertThrows(FleetbaseIntegrationException::class.java) {
            service.receive("{\"id\":\"evt_bad\",\"event\":\"order.created\"}", "sha256=bad")
        }
    }

    private fun signature(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("webhook-test-secret".toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
