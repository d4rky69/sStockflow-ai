package com.stockflow.replenishment

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/replenishment")
class TransferRecommendationController(private val service: TransferRecommendationService) {
    @GetMapping("/transfer-recommendations")
    fun recommendations(request: HttpServletRequest, @RequestParam(defaultValue = "30") targetCoverDays: Int) =
        service.recommendations(access(request), targetCoverDays)

    private fun access(request: HttpServletRequest): TenantAccessContext =
        request.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant security context is unavailable")
}
