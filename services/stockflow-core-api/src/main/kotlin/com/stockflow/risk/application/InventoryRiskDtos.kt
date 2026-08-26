package com.stockflow.risk.application

import java.math.BigDecimal
import java.time.LocalDate

data class InventoryRiskView(
    val riskId: String,
    val tenantId: String,
    val riskType: String,
    val severity: String,
    val asOfDate: LocalDate,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val batchNumber: String?,
    val availableQuantity: Long,
    val usableQuantity: Long,
    val minimumSafetyStock: Long,
    val sales7: Long,
    val sales30: Long,
    val averageDailyDemand30: BigDecimal,
    val daysOfCover: BigDecimal?,
    val expiryDate: LocalDate?,
    val daysToExpiry: Long?,
    val inventoryValue: BigDecimal,
    val lostSales30: Long,
    val stockoutRows30: Long,
    val reason: String,
    val recommendedAction: String
)

data class InventoryRiskSummaryView(
    val tenantId: String,
    val asOfDate: LocalDate,
    val totalRisks: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val stockoutRiskCount: Int,
    val safetyStockBreachCount: Int,
    val inventoryDataGapCount: Int,
    val nearExpiryCount: Int,
    val expiredCount: Int,
    val excessInventoryCount: Int,
    val slowMovingCount: Int,
    val demandSurgeCount: Int,
    val riskExposureValue: BigDecimal
)
