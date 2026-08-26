package com.stockflow.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/security")
class SecurityController(
    private val securityService: TenantSecurityService
) {
    @GetMapping("/me")
    fun me(request: HttpServletRequest): TenantAccessContext =
        request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
            ?: throw TenantAccessDeniedException("Tenant security context is unavailable")

    @GetMapping("/memberships")
    fun memberships(request: HttpServletRequest): List<TenantMemberView> {
        val context = access(request)
        return securityService.members(context.tenantId)
    }

    @PutMapping("/memberships/{userId}")
    fun upsertMembership(
        request: HttpServletRequest,
        @PathVariable userId: String,
        @RequestBody body: UpsertMembershipRequest
    ): TenantMemberView = securityService.upsertMember(access(request), userId, body.email, body.displayName, body.roleCode, body.active, body.warehouseIds)

    private fun access(request: HttpServletRequest): TenantAccessContext =
        request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
            ?: throw TenantAccessDeniedException("Tenant security context is unavailable")

}

data class UpsertMembershipRequest(
    val email: String? = null,
    val displayName: String? = null,
    val roleCode: String,
    val active: Boolean = true,
    val warehouseIds: Set<String> = emptySet()
)
