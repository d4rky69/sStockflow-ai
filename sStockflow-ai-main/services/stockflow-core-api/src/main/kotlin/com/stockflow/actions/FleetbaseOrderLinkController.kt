package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/actions")
class FleetbaseOrderLinkController(private val service: FleetbaseOrderLinkService) {
    @PostMapping("/transfer-executions/{executionId}/fleetbase-link")
    @ResponseStatus(HttpStatus.CREATED)
    fun prepare(
        request: HttpServletRequest,
        @PathVariable executionId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): FleetbaseOrderLinkView = service.prepare(access(request), executionId, idempotencyKey)

    @PostMapping("/transfer-executions/{executionId}/fleetbase-order")
    fun createRemoteOrder(
        request: HttpServletRequest,
        @PathVariable executionId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): FleetbaseOrderLinkView = service.createRemoteOrder(access(request), executionId, idempotencyKey)

    @GetMapping("/transfer-executions/{executionId}/fleetbase-link")
    fun byExecution(request: HttpServletRequest, @PathVariable executionId: UUID): FleetbaseOrderLinkView? =
        service.findForExecution(access(request), executionId)

    @GetMapping("/transfer-executions/{executionId}/fleetbase-tracking")
    fun tracking(request: HttpServletRequest, @PathVariable executionId: UUID): FleetbaseTrackingView =
        service.tracking(access(request), executionId)

    @PostMapping("/transfer-executions/{executionId}/fleetbase-reconcile")
    fun reconcile(request: HttpServletRequest, @PathVariable executionId: UUID): FleetbaseTrackingView =
        service.reconcile(access(request), executionId)

    @GetMapping("/fleetbase-order-links/{linkId}")
    fun get(request: HttpServletRequest, @PathVariable linkId: UUID): FleetbaseOrderLinkView =
        service.get(access(request), linkId)

    private fun access(request: HttpServletRequest): TenantAccessContext =
        request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant security context is unavailable")
}
