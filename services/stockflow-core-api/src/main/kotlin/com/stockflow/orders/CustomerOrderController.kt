package com.stockflow.orders

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class CustomerOrderController(private val service: CustomerOrderService) {
    @GetMapping fun list(request: HttpServletRequest) = service.list(access(request))
    @GetMapping("/{id}") fun detail(request: HttpServletRequest, @PathVariable id: UUID) = service.detail(access(request), id)
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(request: HttpServletRequest, @RequestHeader("Idempotency-Key") key: String, @Valid @RequestBody body: CreateCustomerOrderRequest) = service.create(access(request), key, body)
    @PostMapping("/{id}/advance")
    fun advance(request: HttpServletRequest, @PathVariable id: UUID, @RequestBody body: AdvanceCustomerOrderRequest = AdvanceCustomerOrderRequest()) = service.advance(access(request), id, body.comment)

    private fun access(request: HttpServletRequest) =
        request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant security context is unavailable")
}
