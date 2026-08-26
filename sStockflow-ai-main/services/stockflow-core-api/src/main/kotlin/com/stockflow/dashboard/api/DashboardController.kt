package com.stockflow.dashboard.api

import com.stockflow.dashboard.application.DashboardOverviewService
import com.stockflow.dashboard.application.DashboardOverviewView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardOverviewService: DashboardOverviewService
) {
    @GetMapping("/overview")
    fun overview(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) warehouseId: String?
    ): DashboardOverviewView = dashboardOverviewService.getOverview(tenantId, warehouseId)
}
