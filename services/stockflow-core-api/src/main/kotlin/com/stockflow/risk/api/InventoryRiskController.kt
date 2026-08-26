package com.stockflow.risk.api

import com.stockflow.risk.application.InventoryRiskService
import com.stockflow.risk.application.InventoryRiskSummaryView
import com.stockflow.risk.application.InventoryRiskView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/risks")
class InventoryRiskController(
    private val inventoryRiskService: InventoryRiskService
) {
    @GetMapping("/summary")
    fun summary(@RequestHeader("X-Tenant-ID") tenantId: String): InventoryRiskSummaryView =
        inventoryRiskService.summary(tenantId)

    @GetMapping("/inventory")
    fun inventoryRisks(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<InventoryRiskView> = inventoryRiskService.risks(tenantId, type, severity, limit)

    @GetMapping("/stockout")
    fun stockoutRisks(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<InventoryRiskView> = inventoryRiskService.stockoutRisks(tenantId, limit)

    @GetMapping("/expiry")
    fun expiryRisks(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(defaultValue = "60") days: Int,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<InventoryRiskView> = inventoryRiskService.expiryRisks(tenantId, days, limit)
}
