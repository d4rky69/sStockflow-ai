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
class PurchaseOrderController(private val service: PurchaseOrderService) {
    @PostMapping("/proposals/{proposalId}/purchase-order") @ResponseStatus(HttpStatus.CREATED)
    fun create(request:HttpServletRequest,@PathVariable proposalId:UUID,@RequestHeader("Idempotency-Key") key:String,@Valid @RequestBody body:CreatePurchaseOrderRequest=CreatePurchaseOrderRequest())=service.create(access(request),proposalId,key,body)
    @GetMapping("/purchase-orders") fun list(request:HttpServletRequest)=service.list(access(request))
    @GetMapping("/purchase-orders/{id}") fun detail(request:HttpServletRequest,@PathVariable id:UUID)=service.detail(access(request),id)
    @PostMapping("/purchase-orders/{id}/send") fun send(request:HttpServletRequest,@PathVariable id:UUID,@RequestBody body:ProposalDecisionRequest=ProposalDecisionRequest())=service.send(access(request),id,body.comment)
    @PostMapping("/purchase-orders/{id}/acknowledge") fun acknowledge(request:HttpServletRequest,@PathVariable id:UUID,@Valid @RequestBody body:AcknowledgePurchaseOrderRequest=AcknowledgePurchaseOrderRequest())=service.acknowledge(access(request),id,body)
    @PostMapping("/purchase-orders/{id}/receive") fun receive(request:HttpServletRequest,@PathVariable id:UUID,@RequestHeader("Idempotency-Key") key:String,@Valid @RequestBody body:ReceivePurchaseOrderRequest)=service.receive(access(request),id,key,body)
    @PostMapping("/purchase-orders/{id}/cancel") fun cancel(request:HttpServletRequest,@PathVariable id:UUID,@RequestBody body:ProposalDecisionRequest=ProposalDecisionRequest())=service.cancel(access(request),id,body.comment)
    private fun access(request:HttpServletRequest)=request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext ?: throw ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant security context is unavailable")
}
