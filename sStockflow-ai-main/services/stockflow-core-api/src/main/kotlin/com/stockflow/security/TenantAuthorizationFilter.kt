package com.stockflow.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class TenantAuthorizationFilter(
    private val tenantSecurityService: TenantSecurityService,
    @Value("\${stockflow.security.enabled:false}")
    private val securityEnabled: Boolean
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method.equals("OPTIONS", true) || request.requestURI.startsWith("/actuator/") || request.requestURI == "/error" ||
            (request.method.equals("POST", true) && request.requestURI == "/api/v1/integrations/fleetbase/webhooks")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
        if (authentication == null) {
            if (!securityEnabled && requiresDevelopmentContext(request.requestURI)) {
                val tenantId = request.getHeader("X-Tenant-ID").orEmpty().trim()
                val userId = request.getHeader("X-StockFlow-User-ID").orEmpty().trim().ifBlank { "local-prototype-user" }
                val email = request.getHeader("X-StockFlow-User-Email")?.trim()?.takeIf { it.isNotBlank() }
                try {
                    request.setAttribute(
                        TENANT_ACCESS_ATTRIBUTE,
                        tenantSecurityService.developmentContext(tenantId, userId, email)
                    )
                } catch (error: IllegalArgumentException) {
                    forbidden(response, "MISSING_TENANT", error.message ?: "X-Tenant-ID is required")
                    return
                } catch (error: TenantAccessDeniedException) {
                    forbidden(response, "TENANT_ACCESS_DENIED", error.message ?: "Tenant access denied")
                    return
                }
            }
            filterChain.doFilter(request, response)
            return
        }
        val tenantId = request.getHeader("X-Tenant-ID").orEmpty().trim()
        try {
            val context = tenantSecurityService.authorize(authentication.token, tenantId, requiredPermission(request))
            request.setAttribute(TENANT_ACCESS_ATTRIBUTE, context)
            filterChain.doFilter(request, response)
        } catch (error: IllegalArgumentException) {
            forbidden(response, "MISSING_TENANT", error.message ?: "X-Tenant-ID is required")
        } catch (error: TenantAccessDeniedException) {
            forbidden(response, "TENANT_ACCESS_DENIED", error.message ?: "Tenant access denied")
        }
    }

    private fun requiresDevelopmentContext(path: String): Boolean =
        path.startsWith("/api/v1/actions") ||
            path.startsWith("/api/v1/forecast-operations") ||
            path.startsWith("/api/v1/replenishment") ||
            path.startsWith("/api/v1/orders") ||
            path.startsWith("/api/v1/security")

    private fun requiredPermission(request: HttpServletRequest): String? {
        val path = request.requestURI
        if (path.startsWith("/api/v1/security/memberships")) return "USER_MANAGE"
        if (path == "/api/v1/actions/transfers" && request.method.equals("POST", true)) return "TRANSFER_PROPOSE"
        if (path == "/api/v1/actions/purchases" && request.method.equals("POST", true)) return "PURCHASE_PROPOSE"
        if ((path.endsWith("/approve") || path.endsWith("/reject")) && request.method.equals("POST", true)) return "PROPOSAL_APPROVE"
        if (path.contains("/execution") && request.method.equals("POST", true)) return "TRANSFER_EXECUTE"
        if (path.contains("/transfer-executions/") && request.method.equals("POST", true)) return "TRANSFER_EXECUTE"
        if ((path.contains("/purchase-order") || path.contains("/purchase-orders/")) && request.method.equals("POST", true)) return "PURCHASE_EXECUTE"
        if (path.startsWith("/api/v1/forecast-operations") && request.method.equals("POST", true)) return "FORECAST_RUN"
        if (path.startsWith("/api/v1/replenishment")) return "FORECAST_READ"
        if (path.startsWith("/api/v1/orders") && request.method.equals("POST", true)) return "ORDER_MANAGE"
        if (path.startsWith("/api/v1/actions/")) return null
        if (request.method.equals("GET", true)) return null
        return when {
            path.startsWith("/api/v1/imports") -> "IMPORT_MANAGE"
            path == "/api/v1/forecasts/runs" && request.method.equals("POST", true) -> "FORECAST_RUN"
            path.startsWith("/api/v1/forecasts/configuration") -> "FORECAST_RUN"
            path.startsWith("/api/v1/security") -> "USER_MANAGE"
            else -> "USER_MANAGE"
        }
    }

    private fun forbidden(response: HttpServletResponse, code: String, message: String) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        response.writer.write("{\"timestamp\":\"${Instant.now()}\",\"status\":403,\"code\":\"$code\",\"message\":\"$escaped\"}")
    }

    companion object {
        const val TENANT_ACCESS_ATTRIBUTE = "stockflowTenantAccess"
    }
}
