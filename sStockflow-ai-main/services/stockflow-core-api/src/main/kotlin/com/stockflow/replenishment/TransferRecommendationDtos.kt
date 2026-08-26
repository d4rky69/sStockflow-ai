package com.stockflow.replenishment

import java.math.BigDecimal
import java.time.LocalDate

data class TransferRecommendationView(
    val recommendationId: String,
    val skuId: String,
    val skuName: String,
    val sourceWarehouseId: String,
    val sourceWarehouseName: String,
    val destinationWarehouseId: String,
    val destinationWarehouseName: String,
    val recommendedQuantity: Long,
    val sourceUsableBefore: Long,
    val sourceUsableAfter: Long,
    val sourceSafetyStock: Long,
    val destinationUsableBefore: Long,
    val destinationTargetStock: Long,
    val distanceKm: BigDecimal,
    val vehicleType: String,
    val vehicleCapacityUnits: Long,
    val trips: Int,
    val estimatedTransferCost: BigDecimal,
    val estimatedPurchaseCost: BigDecimal,
    val estimatedSavings: BigDecimal,
    val workingCapitalMoved: BigDecimal,
    val estimatedCarbonKgCo2e: BigDecimal,
    val risk: String,
    val confidencePercent: Int,
    val explanation: String,
    val constraintsChecked: List<String>,
    val assumptions: List<String>,
    val asOfDate: LocalDate
)

data class TransferRecommendationSummaryView(
    val asOfDate: LocalDate?,
    val recommendationCount: Int,
    val recommendedUnits: Long,
    val estimatedSavings: BigDecimal,
    val workingCapitalMoved: BigDecimal,
    val estimatedCarbonKgCo2e: BigDecimal,
    val recommendations: List<TransferRecommendationView>
)
