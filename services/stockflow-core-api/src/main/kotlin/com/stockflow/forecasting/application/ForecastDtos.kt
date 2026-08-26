package com.stockflow.forecasting.application

import com.stockflow.forecasting.persistence.DemandPattern
import com.stockflow.forecasting.persistence.ForecastAggregationLevel
import com.stockflow.forecasting.persistence.ForecastConfidence
import com.stockflow.forecasting.persistence.ForecastDiagnosticReason
import com.stockflow.forecasting.persistence.ForecastEligibilityStatus
import com.stockflow.forecasting.persistence.ForecastModelCode
import com.stockflow.forecasting.persistence.ForecastRunStatus
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreateForecastRunRequest(
    val asOfDate: LocalDate? = null,

    @field:Min(7)
    @field:Max(90)
    val horizonDays: Int = 30,

    @field:Min(28)
    @field:Max(365)
    val historyDays: Int? = null,

    @field:Size(max = 64)
    val warehouseId: String? = null,

    @field:Size(max = 80)
    val skuId: String? = null,

    @field:Size(max = 10)
    val models: Set<ForecastModelCode>? = null
)

data class UpdateForecastConfigurationRequest(
    @field:Min(28)
    @field:Max(365)
    val defaultHistoryDays: Int? = null,

    @field:Min(7)
    @field:Max(90)
    val backtestDays: Int? = null,

    @field:Min(2)
    @field:Max(30)
    val movingAverageWindow: Int? = null,

    @field:Min(2)
    @field:Max(30)
    val seasonalPeriodDays: Int? = null,

    @field:Min(14)
    @field:Max(365)
    val minimumHistoryDays: Int? = null,

    @field:Min(1)
    @field:Max(365)
    val minimumNonZeroObservations: Int? = null,

    @field:DecimalMin("0.01")
    @field:DecimalMax("0.99")
    val smoothingAlpha: BigDecimal? = null,

    @field:DecimalMin("0.01")
    @field:DecimalMax("0.99")
    val trendBeta: BigDecimal? = null,

    @field:DecimalMin("0.01")
    @field:DecimalMax("0.99")
    val seasonalGamma: BigDecimal? = null,

    @field:DecimalMin("0.00")
    @field:DecimalMax("1000.00")
    val highConfidenceWape: BigDecimal? = null,

    @field:DecimalMin("0.00")
    @field:DecimalMax("1000.00")
    val mediumConfidenceWape: BigDecimal? = null,

    @field:DecimalMin("0.00")
    @field:DecimalMax("100.00")
    val highConfidenceMase: BigDecimal? = null,

    @field:DecimalMin("0.00")
    @field:DecimalMax("100.00")
    val mediumConfidenceMase: BigDecimal? = null,

    @field:DecimalMin("0.01")
    @field:DecimalMax("1000.00")
    val maximumForecastableCvSquared: BigDecimal? = null,

    val weeklyAggregationEnabled: Boolean? = null,
    val outlierTreatmentEnabled: Boolean? = null,

    @field:Size(min = 1, max = 10)
    val enabledModels: Set<ForecastModelCode>? = null
)

data class ForecastConfigurationView(
    val tenantId: String,
    val defaultHistoryDays: Int,
    val backtestDays: Int,
    val movingAverageWindow: Int,
    val seasonalPeriodDays: Int,
    val minimumHistoryDays: Int,
    val minimumNonZeroObservations: Int,
    val smoothingAlpha: BigDecimal,
    val trendBeta: BigDecimal,
    val seasonalGamma: BigDecimal,
    val highConfidenceWape: BigDecimal,
    val mediumConfidenceWape: BigDecimal,
    val highConfidenceMase: BigDecimal,
    val mediumConfidenceMase: BigDecimal,
    val maximumForecastableCvSquared: BigDecimal,
    val weeklyAggregationEnabled: Boolean,
    val outlierTreatmentEnabled: Boolean,
    val enabledModels: Set<ForecastModelCode>,
    val active: Boolean,
    val updatedAt: LocalDateTime
)

data class ForecastRunView(
    val forecastRunId: UUID,
    val tenantId: String,
    val asOfDate: LocalDate,
    val horizonDays: Int,
    val historyDays: Int,
    val requestedWarehouseId: String?,
    val requestedSkuId: String?,
    val status: ForecastRunStatus,
    val positionsRequested: Int,
    val positionsProcessed: Int,
    val positionsFailed: Int,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val message: String?
)

data class ForecastValueView(
    val forecastDate: LocalDate,
    val horizonDay: Int,
    val forecastQuantity: BigDecimal,
    val lowerBound: BigDecimal,
    val upperBound: BigDecimal
)

data class ForecastPositionView(
    val forecastRunId: UUID,
    val tenantId: String,
    val asOfDate: LocalDate,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val selectedModel: ForecastModelCode,
    val selectedAggregation: ForecastAggregationLevel,
    val demandPattern: DemandPattern,
    val eligibilityStatus: ForecastEligibilityStatus,
    val diagnosticReasons: Set<ForecastDiagnosticReason>,
    val confidence: ForecastConfidence,
    val trainingSampleCount: Int,
    val backtestPoints: Int,
    val nonZeroObservations: Int,
    val zeroDemandRatio: BigDecimal,
    val averageDemandInterval: BigDecimal,
    val coefficientVariationSquared: BigDecimal,
    val outliersAdjusted: Int,
    val mae: BigDecimal,
    val rmse: BigDecimal,
    val mape: BigDecimal?,
    val wape: BigDecimal,
    val smape: BigDecimal,
    val mase: BigDecimal?,
    val rmsse: BigDecimal?,
    val bias: BigDecimal,
    val selectionScore: BigDecimal,
    val horizonDays: Int,
    val totalForecastQuantity: BigDecimal,
    val averageDailyForecast: BigDecimal,
    val usableInventory: Long?,
    val inventoryDataAvailable: Boolean,
    val projectedStockoutDate: LocalDate?,
    val forecastValues: List<ForecastValueView>
)

data class ForecastSummaryView(
    val tenantId: String,
    val forecastRunId: UUID,
    val asOfDate: LocalDate,
    val horizonDays: Int,
    val positionsForecasted: Int,
    val highConfidenceCount: Int,
    val mediumConfidenceCount: Int,
    val lowConfidenceCount: Int,
    val projectedStockoutCount: Int,
    val totalForecastQuantity: BigDecimal,
    val modelUsage: Map<ForecastModelCode, Int>,
    val aggregationUsage: Map<ForecastAggregationLevel, Int>
)

data class ForecastModelPerformanceView(
    val forecastRunId: UUID,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val modelCode: ForecastModelCode,
    val aggregationLevel: ForecastAggregationLevel,
    val demandPattern: DemandPattern,
    val eligibilityStatus: ForecastEligibilityStatus,
    val trainingSampleCount: Int,
    val backtestPoints: Int,
    val nonZeroObservations: Int,
    val zeroDemandRatio: BigDecimal,
    val averageDemandInterval: BigDecimal,
    val coefficientVariationSquared: BigDecimal,
    val outliersAdjusted: Int,
    val mae: BigDecimal,
    val rmse: BigDecimal,
    val mape: BigDecimal?,
    val wape: BigDecimal,
    val smape: BigDecimal,
    val mase: BigDecimal?,
    val rmsse: BigDecimal?,
    val bias: BigDecimal,
    val selectionScore: BigDecimal,
    val selectedModel: Boolean
)

data class ForecastAccuracySummaryView(
    val tenantId: String,
    val forecastRunId: UUID,
    val asOfDate: LocalDate,
    val positionsEvaluated: Int,
    val averageMae: BigDecimal,
    val averageRmse: BigDecimal,
    val averageMape: BigDecimal?,
    val averageWape: BigDecimal,
    val averageSmape: BigDecimal,
    val averageMase: BigDecimal?,
    val averageRmsse: BigDecimal?,
    val averageAbsoluteBias: BigDecimal,
    val highConfidenceCount: Int,
    val mediumConfidenceCount: Int,
    val lowConfidenceCount: Int,
    val modelUsage: Map<ForecastModelCode, Int>,
    val aggregationUsage: Map<ForecastAggregationLevel, Int>,
    val demandPatternUsage: Map<DemandPattern, Int>,
    val totalOutliersAdjusted: Int
)

data class ForecastPositionDiagnosticView(
    val forecastRunId: UUID,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val eligibilityStatus: ForecastEligibilityStatus,
    val demandPattern: DemandPattern,
    val selectedAggregation: ForecastAggregationLevel,
    val historyObservations: Int,
    val nonZeroObservations: Int,
    val zeroDemandRatio: BigDecimal,
    val averageDemandInterval: BigDecimal,
    val coefficientVariationSquared: BigDecimal,
    val outliersAdjusted: Int,
    val selectedModel: ForecastModelCode?,
    val selectedWape: BigDecimal?,
    val selectedMase: BigDecimal?,
    val selectedRmsse: BigDecimal?,
    val bestDailyWape: BigDecimal?,
    val reasonCodes: Set<ForecastDiagnosticReason>
)

data class ForecastCalibrationSummaryView(
    val tenantId: String,
    val forecastRunId: UUID,
    val asOfDate: LocalDate,
    val positionsAnalyzed: Int,
    val eligiblePositions: Int,
    val ineligiblePositions: Int,
    val dailySelectedCount: Int,
    val weeklySelectedCount: Int,
    val averageDemandInterval: BigDecimal,
    val averageCoefficientVariationSquared: BigDecimal,
    val averageSelectedWape: BigDecimal?,
    val averageBestDailyWape: BigDecimal?,
    val averageWapeImprovement: BigDecimal?,
    val averageMase: BigDecimal?,
    val averageRmsse: BigDecimal?,
    val eligibilityUsage: Map<ForecastEligibilityStatus, Int>,
    val demandPatternUsage: Map<DemandPattern, Int>,
    val modelUsage: Map<ForecastModelCode, Int>,
    val reasonUsage: Map<ForecastDiagnosticReason, Int>
)

data class ForecastExceptionView(
    val exceptionCode: String,
    val warehouseId: String?,
    val skuId: String?,
    val message: String,
    val createdAt: LocalDateTime
)
