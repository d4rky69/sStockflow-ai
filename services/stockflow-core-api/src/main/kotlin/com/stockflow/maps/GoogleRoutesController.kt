package com.stockflow.maps

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/integrations/google-maps")
class GoogleRoutesController(private val service: GoogleRoutesService) {
    @PostMapping("/routes")
    fun computeRoute(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestBody command: GoogleRouteCommand
    ): GoogleRouteView = service.compute(tenantId, command)
}
