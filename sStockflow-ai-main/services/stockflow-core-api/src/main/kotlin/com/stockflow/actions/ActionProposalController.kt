package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/actions")
class ActionProposalController(private val service: ActionProposalService) {
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransfer(request: HttpServletRequest, @RequestHeader("Idempotency-Key") key: String, @Valid @RequestBody body: CreateTransferProposalRequest) = service.createTransfer(access(request), key, body)

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPurchase(request: HttpServletRequest, @RequestHeader("Idempotency-Key") key: String, @Valid @RequestBody body: CreatePurchaseProposalRequest) = service.createPurchase(access(request), key, body)

    @GetMapping("/proposals")
    fun proposals(request: HttpServletRequest, @RequestParam(required = false) status: String?, @RequestParam(required = false) type: String?) = service.list(access(request), status, type)

    @GetMapping("/proposals/{proposalId}")
    fun proposal(request: HttpServletRequest, @PathVariable proposalId: UUID) = service.get(access(request), proposalId)

    @GetMapping("/proposals/{proposalId}/history")
    fun history(request: HttpServletRequest, @PathVariable proposalId: UUID) = service.history(access(request), proposalId)

    @PostMapping("/proposals/{proposalId}/submit")
    fun submit(request: HttpServletRequest, @PathVariable proposalId: UUID, @RequestBody body: ProposalDecisionRequest = ProposalDecisionRequest()) = service.submit(access(request), proposalId, body.comment)

    @PostMapping("/proposals/{proposalId}/approve")
    fun approve(request: HttpServletRequest, @PathVariable proposalId: UUID, @RequestBody body: ProposalDecisionRequest = ProposalDecisionRequest()) = service.approve(access(request), proposalId, body.comment)

    @PostMapping("/proposals/{proposalId}/reject")
    fun reject(request: HttpServletRequest, @PathVariable proposalId: UUID, @RequestBody body: ProposalDecisionRequest) = service.reject(access(request), proposalId, body.comment)

    @PostMapping("/proposals/{proposalId}/cancel")
    fun cancel(request: HttpServletRequest, @PathVariable proposalId: UUID, @RequestBody body: ProposalDecisionRequest = ProposalDecisionRequest()) = service.cancel(access(request), proposalId, body.comment)

    private fun access(request: HttpServletRequest): TenantAccessContext = request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
        ?: throw org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant security context is unavailable")
}
