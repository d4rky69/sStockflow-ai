package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/actions")
class TransferExecutionController(private val service: TransferExecutionService) {
    @PostMapping("/proposals/{proposalId}/execution") @ResponseStatus(HttpStatus.CREATED)
    fun create(request:HttpServletRequest,@PathVariable proposalId:UUID,@RequestHeader("Idempotency-Key") key:String,@Valid @RequestBody body:CreateTransferExecutionRequest=CreateTransferExecutionRequest())=service.create(access(request),proposalId,key,body)
    @GetMapping("/transfer-executions") fun list(request:HttpServletRequest)=service.list(access(request))
    @GetMapping("/transfer-executions/{id}") fun detail(request:HttpServletRequest,@PathVariable id:UUID)=service.detail(access(request),id)
    @PostMapping("/transfer-executions/{id}/reserve") fun reserve(request:HttpServletRequest,@PathVariable id:UUID,@RequestBody body:ProposalDecisionRequest=ProposalDecisionRequest())=service.reserve(access(request),id,body.comment)
    @PostMapping("/transfer-executions/{id}/dispatch") fun dispatch(request:HttpServletRequest,@PathVariable id:UUID,@RequestBody body:ProposalDecisionRequest=ProposalDecisionRequest())=service.dispatch(access(request),id,body.comment)
    @PostMapping("/transfer-executions/{id}/receive") fun receive(request:HttpServletRequest,@PathVariable id:UUID,@Valid @RequestBody body:ReceiveTransferRequest=ReceiveTransferRequest())=service.receive(access(request),id,body)
    @PostMapping("/transfer-executions/{id}/cancel") fun cancel(request:HttpServletRequest,@PathVariable id:UUID,@RequestBody body:ProposalDecisionRequest=ProposalDecisionRequest())=service.cancel(access(request),id,body.comment)
    private fun access(request:HttpServletRequest)=request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext ?: throw ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant security context is unavailable")
}
