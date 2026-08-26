package com.stockflow.analytics.api

import com.stockflow.analytics.application.DemandAnalyticsService
import com.stockflow.analytics.application.DemandSkuView
import com.stockflow.analytics.application.DemandSummaryView
import com.stockflow.analytics.application.DemandTrendView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analytics/demand")
class DemandAnalyticsController(
    private val demandAnalyticsService: DemandAnalyticsService
) {
    @GetMapping("/summary")
    fun summary(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "30") windowDays: Int
    ): DemandSummaryView = demandAnalyticsService.summary(tenantId, windowDays)

    @GetMapping("/skus")
    fun skus(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "30") windowDays: Int,
        @RequestParam(defaultValue = "25") limit: Int
    ): List<DemandSkuView> = demandAnalyticsService.skus(tenantId, windowDays, limit)

    @GetMapping("/trend")
    fun trend(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "16") weeks: Int
    ): DemandTrendView = demandAnalyticsService.trend(tenantId, weeks)
}
