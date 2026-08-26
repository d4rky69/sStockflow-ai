package com.stockflow.foundation.application

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class FoundationSummary(
    val tenant: TenantView,
    val warehouseCount: Long,
    val productCount: Long,
    val skuCount: Long,
    val batchCount: Long
)

data class TenantView(
    val tenantId: String,
    val tenantName: String,
    val vertical: String,
    val currency: String,
    val timezone: String
)

data class WarehouseView(
    val warehouseId: String,
    val warehouseName: String,
    val city: String,
    val state: String,
    val country: String,
    val capacityUnits: Long,
    val coldChainAvailable: Boolean
)

data class SkuView(
    val skuId: String,
    val productId: String,
    val skuName: String,
    val baseUom: String,
    val unitCost: BigDecimal,
    val sellingPrice: BigDecimal,
    val currency: String,
    val minimumSafetyStock: Long,
    val reorderMultiple: Long,
    val defaultShelfLifeDays: Int?,
    val fefoRequired: Boolean,
    val demandProfile: String
)

data class BatchInventoryView(
    val batchInventoryId: UUID,
    val snapshotDate: LocalDate,
    val warehouseId: String,
    val skuId: String,
    val batchNumber: String,
    val manufactureDate: LocalDate?,
    val expiryDate: LocalDate?,
    val availableQuantity: Long,
    val reservedQuantity: Long,
    val blockedQuantity: Long,
    val usableQuantity: Long,
    val unitCost: BigDecimal,
    val currency: String,
    val storageConditionCode: String,
    val lastMovementAt: LocalDateTime?
)
