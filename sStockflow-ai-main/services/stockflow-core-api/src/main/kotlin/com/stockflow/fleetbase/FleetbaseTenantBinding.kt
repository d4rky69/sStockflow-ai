package com.stockflow.fleetbase

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class FleetbaseTenantBinding(
    @Value("\${stockflow.fleetbase.tenant-id:TEN-ACME-PHARMA}") tenantId: String,
    @Value("\${stockflow.fleetbase.organization-id:}") organizationId: String
) {
    private val mappedTenantId = tenantId.trim()
    private val expectedOrganizationId = organizationId.trim()

    fun view(requestedTenantId: String): FleetbaseTenantMappingView = FleetbaseTenantMappingView(
        tenantId = requestedTenantId,
        mapped = mappedTenantId.isNotBlank() && requestedTenantId == mappedTenantId,
        expectedOrganizationId = expectedOrganizationId.takeIf { it.isNotBlank() },
        organizationVerificationEnabled = expectedOrganizationId.isNotBlank()
    )

    fun requireMapped(requestedTenantId: String) {
        if (requestedTenantId.isBlank()) throw FleetbaseIntegrationException(
            HttpStatus.BAD_REQUEST,
            "MISSING_TENANT",
            "X-Tenant-ID is required"
        )
        if (mappedTenantId.isBlank() || requestedTenantId != mappedTenantId) throw FleetbaseIntegrationException(
            HttpStatus.FORBIDDEN,
            "FLEETBASE_TENANT_NOT_MAPPED",
            "The requested StockFlow tenant is not mapped to the configured Fleetbase organization"
        )
    }

    fun matchesOrganization(organizationId: String): Boolean =
        expectedOrganizationId.isBlank() || expectedOrganizationId == organizationId

    fun verificationEnabled(): Boolean = expectedOrganizationId.isNotBlank()

    fun requireOrganizationId(): String = expectedOrganizationId.takeIf { it.isNotBlank() }
        ?: throw FleetbaseIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FLEETBASE_ORGANIZATION_NOT_PINNED",
            "FLEETBASE_ORGANIZATION_ID must be configured before an order linkage can be prepared"
        )
}
