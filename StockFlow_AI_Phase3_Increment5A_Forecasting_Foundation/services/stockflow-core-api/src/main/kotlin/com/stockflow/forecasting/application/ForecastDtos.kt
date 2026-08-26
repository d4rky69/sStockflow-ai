package com.stockflow.forecasting.application

import com.stockflow.forecasting.persistence.ForecastConfidence
import com.stockflow.forecasting.persistence.ForecastModelCode
import com.stockflow.forecasting.persistence.ForecastRunStatus
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

    @field:Size(max = 4)
    val models: Set<ForecastModelCode>? = null
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
    val confidence: ForecastConfidence,
    val trainingSampleCount: Int,
    val backtestPoints: Int,
    val mae: BigDecimal,
    val rmse: BigDecimal,
    val mape: BigDecimal?,
    val bias: BigDecimal,
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
    val modelUsage: Map<ForecastModelCode, Int>
)

data class ForecastModelPerformanceView(
    val forecastRunId: UUID,
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val modelCode: ForecastModelCode,
    val trainingSampleCount: Int,
    val backtestPoints: Int,
    val mae: BigDecimal,
    val rmse: BigDecimal,
    val mape: BigDecimal?,
    val bias: BigDecimal,
    val selectedModel: Boolean
)

data class ForecastExceptionView(
    val exceptionCode: String,
    val warehouseId: String?,
    val skuId: String?,
    val message: String,
    val createdAt: LocalDateTime
)
