package com.stockflow.fleetbase

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/integrations/fleetbase")
class FleetbaseIntegrationController(private val service: FleetbaseIntegrationService) {
    @GetMapping("/status")
    fun status(@RequestHeader("X-Tenant-ID") tenantId: String) = service.configuration(tenantId)

    @GetMapping("/organization")
    fun organization(@RequestHeader("X-Tenant-ID") tenantId: String): FleetbaseOrganizationView =
        service.organization(tenantId)

    @GetMapping("/vehicles")
    fun vehicles(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): FleetbaseVehicleListView {
        return service.listVehicles(tenantId, limit)
    }
}
