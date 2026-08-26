package com.stockflow.fleetbase

import com.stockflow.actions.FleetbaseAuditSummaryView
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class FleetbaseWebhookAcknowledgement(val accepted: Boolean, val duplicate: Boolean, val status: String)

@Service
class FleetbaseWebhookService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val client: FleetbaseClient,
    private val tenantBinding: FleetbaseTenantBinding,
    @Value("\${stockflow.fleetbase.webhook-secret:}") private val webhookSecret: String
) {
    fun configured(): Boolean = webhookSecret.isNotBlank()

    @Transactional
    fun receive(rawBody: String, suppliedSignature: String?): FleetbaseWebhookAcknowledgement {
        if (!configured()) throw FleetbaseIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FLEETBASE_WEBHOOK_NOT_CONFIGURED",
            "Fleetbase webhook processing is disabled until a server-side webhook secret is configured"
        )
        if (!validSignature(rawBody, suppliedSignature)) throw FleetbaseIntegrationException(
            HttpStatus.UNAUTHORIZED,
            "FLEETBASE_WEBHOOK_SIGNATURE_INVALID",
            "Fleetbase webhook signature verification failed"
        )
        val payload = try { objectMapper.readTree(rawBody) } catch (_: Exception) {
            throw FleetbaseIntegrationException(HttpStatus.BAD_REQUEST, "FLEETBASE_WEBHOOK_INVALID_JSON", "Fleetbase webhook payload is not valid JSON")
        }
        val eventId = payload.path("id").asText().trim()
        val eventName = payload.path("event").asText().trim()
        val data = payload.path("data")
        val orderId = data.path("id").asText().trim().takeIf { it.startsWith("order_") }
        if (eventId.isBlank() || eventName.isBlank()) throw FleetbaseIntegrationException(
            HttpStatus.BAD_REQUEST,
            "FLEETBASE_WEBHOOK_INVALID_PAYLOAD",
            "Fleetbase webhook payload must include id and event"
        )
        if (jdbc.queryForObject("SELECT COUNT(*) FROM fleetbase_webhook_event WHERE event_id=?", Int::class.java, eventId)!! > 0) {
            return FleetbaseWebhookAcknowledgement(true, true, "DUPLICATE_IGNORED")
        }

        val remoteStatus = data.path("status").asText().trim().takeIf { it.isNotBlank() }
            ?: eventName.substringAfter('.', "").takeIf { it.isNotBlank() }
        val link = orderId?.let { id -> jdbc.query(
            """SELECT l.link_id,l.tenant_id,l.link_status,e.status AS execution_status
                 FROM fleetbase_order_link l JOIN transfer_execution e ON e.execution_id=l.transfer_execution_id
                WHERE l.fleetbase_order_id=?""",
            { rs, _ -> WebhookLink(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)) }, id
        ).firstOrNull() }
        val processingStatus = if (link == null) "IGNORED" else "APPLIED"
        val payloadHash = sha256(rawBody)
        jdbc.update(
            """INSERT INTO fleetbase_webhook_event(event_id,event_name,fleetbase_order_id,tenant_id,link_id,payload_hash,processing_status,remote_status)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""",
            eventId, eventName, orderId, link?.tenantId, link?.linkId, payloadHash, processingStatus, remoteStatus
        )
        if (link != null) {
            val reconciliation = reconcile(link.executionStatus, link.linkStatus, remoteStatus)
            jdbc.update(
                """UPDATE fleetbase_order_link SET remote_status=?,last_webhook_at=CURRENT_TIMESTAMP,
                    reconciliation_status=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE link_id=?""",
                remoteStatus, reconciliation, link.linkId
            )
        }
        return FleetbaseWebhookAcknowledgement(true, false, processingStatus)
    }

    fun audit(tenantId: String): FleetbaseAuditSummaryView {
        tenantBinding.requireMapped(tenantId)
        val counts = jdbc.queryForMap(
            """SELECT COUNT(*) AS total_links,
                      COALESCE(SUM(CASE WHEN link_status='PREPARED' THEN 1 ELSE 0 END),0) AS prepared,
                      COALESCE(SUM(CASE WHEN link_status='CREATED' THEN 1 ELSE 0 END),0) AS created,
                      COALESCE(SUM(CASE WHEN link_status='DISPATCHED' THEN 1 ELSE 0 END),0) AS dispatched,
                      COALESCE(SUM(CASE WHEN link_status='FAILED' THEN 1 ELSE 0 END),0) AS failed,
                      COALESCE(SUM(CASE WHEN reconciliation_status IN ('REMOTE_AHEAD','LOCAL_AHEAD','REVIEW_REQUIRED') THEN 1 ELSE 0 END),0) AS issues,
                      MAX(last_webhook_at) AS last_webhook_at
                 FROM fleetbase_order_link WHERE tenant_id=?""",
            tenantId
        )
        val webhookEvents = jdbc.queryForObject("SELECT COUNT(*) FROM fleetbase_webhook_event WHERE tenant_id=?", Int::class.java, tenantId) ?: 0
        fun number(name: String) = (counts[name] as Number).toInt()
        val failed = number("failed")
        val issues = number("issues")
        val config = client.configuration()
        val rollout = when {
            !config.enabled || !config.configured -> "INTEGRATION_DISABLED"
            !configured() -> "WEBHOOK_SETUP_REQUIRED"
            failed > 0 || issues > 0 -> "ATTENTION_REQUIRED"
            else -> "READY"
        }
        return FleetbaseAuditSummaryView(
            tenantId = tenantId,
            totalLinks = number("total_links"),
            prepared = number("prepared"),
            created = number("created"),
            dispatched = number("dispatched"),
            failed = failed,
            reconciliationIssues = issues,
            webhookEvents = webhookEvents,
            lastWebhookAt = when (val value = counts["last_webhook_at"]) {
                is java.sql.Timestamp -> value.toLocalDateTime()
                is LocalDateTime -> value
                else -> null
            },
            writesEnabled = config.writeOperationsEnabled,
            webhookConfigured = configured(),
            rolloutStatus = rollout
        )
    }

    private fun validSignature(rawBody: String, supplied: String?): Boolean {
        val actual = supplied?.trim()?.removePrefix("sha256=")?.lowercase() ?: return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(rawBody.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(expected.toByteArray(StandardCharsets.US_ASCII), actual.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun reconcile(executionStatus: String, linkStatus: String, remoteStatus: String?): String {
        val remote = remoteStatus?.uppercase()
        return when {
            executionStatus == "RECEIVED" && remote == "COMPLETED" -> "MATCHED"
            executionStatus == "IN_TRANSIT" && remote in setOf("DISPATCHED", "STARTED", "IN_PROGRESS") -> "MATCHED"
            executionStatus == "RESERVED" && remote in setOf("DISPATCHED", "STARTED", "COMPLETED") -> "REMOTE_AHEAD"
            executionStatus in setOf("IN_TRANSIT", "RECEIVED") && remote in setOf("CREATED", null) -> "LOCAL_AHEAD"
            linkStatus == "CREATED" && remote == "CREATED" -> "MATCHED"
            else -> "REVIEW_REQUIRED"
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class WebhookLink(val linkId: String, val tenantId: String, val linkStatus: String, val executionStatus: String)
}
