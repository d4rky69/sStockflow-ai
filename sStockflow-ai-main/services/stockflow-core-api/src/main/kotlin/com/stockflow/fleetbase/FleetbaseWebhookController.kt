package com.stockflow.fleetbase

import com.stockflow.actions.FleetbaseAuditSummaryView
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/integrations/fleetbase")
class FleetbaseWebhookController(private val service: FleetbaseWebhookService) {
    @PostMapping("/webhooks", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader("X-Fleetbase-Signature", required = false) signature: String?
    ): FleetbaseWebhookAcknowledgement = service.receive(rawBody, signature)

    @GetMapping("/audit")
    fun audit(@RequestHeader("X-Tenant-ID") tenantId: String): FleetbaseAuditSummaryView = service.audit(tenantId)
}
