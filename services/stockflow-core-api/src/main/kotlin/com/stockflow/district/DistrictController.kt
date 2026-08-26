package com.stockflow.district

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/districts")
class DistrictController(private val districtService: DistrictService) {

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_tenant')")
    fun getDistricts(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): ResponseEntity<List<DistrictRegistry>> {
        val districts = districtService.getDistrictsByTenant(tenantId)
        return ResponseEntity.ok(districts)
    }
}
