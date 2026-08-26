package com.stockflow.analytics.api

import com.stockflow.analytics.application.SalesAnalyticsService
import com.stockflow.analytics.application.SalesSummaryView
import com.stockflow.analytics.application.TopSkuSalesView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/analytics/sales")
class SalesAnalyticsController(
    private val salesAnalyticsService: SalesAnalyticsService
) {
    @GetMapping("/summary")
    fun summary(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?
    ): SalesSummaryView = salesAnalyticsService.summary(tenantId, dateFrom, dateTo)

    @GetMapping("/top-skus")
    fun topSkus(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<TopSkuSalesView> = salesAnalyticsService.topSkus(tenantId, dateFrom, dateTo, limit)
}
