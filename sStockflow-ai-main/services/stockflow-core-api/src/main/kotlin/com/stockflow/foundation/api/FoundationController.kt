package com.stockflow.foundation.api

import com.stockflow.foundation.application.BatchInventoryView
import com.stockflow.foundation.application.FoundationQueryService
import com.stockflow.foundation.application.FoundationSummary
import com.stockflow.foundation.application.SkuView
import com.stockflow.foundation.application.WarehouseView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class FoundationController(
    private val foundationQueryService: FoundationQueryService
) {
    @GetMapping("/foundation/summary")
    fun summary(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): FoundationSummary = foundationQueryService.summary(tenantId)

    @GetMapping("/warehouses")
    fun warehouses(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): List<WarehouseView> = foundationQueryService.warehouses(tenantId)

    @GetMapping("/warehouses/{warehouseId}")
    fun warehouse(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable warehouseId: String
    ): WarehouseView = foundationQueryService.warehouse(tenantId, warehouseId)

    @GetMapping("/skus")
    fun skus(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): List<SkuView> = foundationQueryService.skus(tenantId)

    @GetMapping("/inventory/batches")
    fun batches(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) warehouseId: String?,
        @RequestParam(required = false) skuId: String?
    ): List<BatchInventoryView> = foundationQueryService.batches(tenantId, warehouseId, skuId)
}
