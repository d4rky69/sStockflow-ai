package com.stockflow.replenishment

import java.math.BigDecimal
import java.time.LocalDate

data class ReplenishmentPlanView(
    val recommendationId: String,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val supplierId: String?,
    val supplierName: String,
    val leadTimeDays: Int,
    val usableQuantity: Long,
    val openPurchaseQuantity: Long,
    val averageDailyDemand: BigDecimal,
    val demandSource: String,
    val coverDays: BigDecimal?,
    val safetyStock: Long,
    val targetStock: Long,
    val reorderMultiple: Long,
    val recommendedQuantity: Long,
    val unitCost: BigDecimal,
    val plannedValue: BigDecimal,
    val needBy: LocalDate,
    val confidencePercent: Int,
    val risk: String,
    val status: String,
    val explanation: String,
    val asOfDate: LocalDate
)

data class ReplenishmentSummaryView(
    val asOfDate: LocalDate?,
    val targetCoverDays: Int,
    val recommendationCount: Int,
    val criticalCount: Int,
    val plannedSpend: BigDecimal,
    val openPurchaseQuantity: Long,
    val plans: List<ReplenishmentPlanView>
)
