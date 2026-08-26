package com.stockflow.forecasting.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "forecast_configuration")
open class ForecastConfigurationEntity(
    @Id
    @Column(name = "forecast_configuration_id", nullable = false)
    open var forecastConfigurationId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64, unique = true)
    open var tenantId: String = "",

    @Column(name = "default_history_days", nullable = false)
    open var defaultHistoryDays: Int = 180,

    @Column(name = "backtest_days", nullable = false)
    open var backtestDays: Int = 28,

    @Column(name = "moving_average_window", nullable = false)
    open var movingAverageWindow: Int = 7,

    @Column(name = "seasonal_period_days", nullable = false)
    open var seasonalPeriodDays: Int = 7,

    @Column(name = "minimum_history_days", nullable = false)
    open var minimumHistoryDays: Int = 28,

    @Column(name = "smoothing_alpha", nullable = false, precision = 5, scale = 4)
    open var smoothingAlpha: BigDecimal = BigDecimal("0.3000"),

    @Column(name = "trend_beta", nullable = false, precision = 5, scale = 4)
    open var trendBeta: BigDecimal = BigDecimal("0.2000"),

    @Column(name = "seasonal_gamma", nullable = false, precision = 5, scale = 4)
    open var seasonalGamma: BigDecimal = BigDecimal("0.2000"),

    @Column(name = "high_confidence_wape", nullable = false, precision = 7, scale = 2)
    open var highConfidenceWape: BigDecimal = BigDecimal("20.00"),

    @Column(name = "medium_confidence_wape", nullable = false, precision = 7, scale = 2)
    open var mediumConfidenceWape: BigDecimal = BigDecimal("40.00"),

    @Column(name = "outlier_treatment_enabled", nullable = false)
    open var outlierTreatmentEnabled: Boolean = true,

    @Column(name = "enabled_models", nullable = false, length = 300)
    open var enabledModels: String = DEFAULT_ENABLED_MODELS,

    @Column(name = "active", nullable = false)
    open var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val DEFAULT_ENABLED_MODELS =
            "NAIVE,MOVING_AVERAGE,WEIGHTED_MOVING_AVERAGE,SEASONAL_NAIVE," +
                "SIMPLE_EXPONENTIAL_SMOOTHING,HOLT_LINEAR_TREND,HOLT_WINTERS_ADDITIVE,CROSTON_SBA"
    }
}

@Entity
@Table(name = "forecast_run")
open class ForecastRunEntity(
    @Id
    @Column(name = "forecast_run_id", nullable = false)
    open var forecastRunId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "as_of_date", nullable = false)
    open var asOfDate: LocalDate = LocalDate.now(),

    @Column(name = "horizon_days", nullable = false)
    open var horizonDays: Int = 30,

    @Column(name = "history_days", nullable = false)
    open var historyDays: Int = 180,

    @Column(name = "requested_warehouse_id", length = 64)
    open var requestedWarehouseId: String? = null,

    @Column(name = "requested_sku_id", length = 80)
    open var requestedSkuId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    open var status: ForecastRunStatus = ForecastRunStatus.RUNNING,

    @Column(name = "positions_requested", nullable = false)
    open var positionsRequested: Int = 0,

    @Column(name = "positions_processed", nullable = false)
    open var positionsProcessed: Int = 0,

    @Column(name = "positions_failed", nullable = false)
    open var positionsFailed: Int = 0,

    @Column(name = "started_at", nullable = false)
    open var startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    open var completedAt: LocalDateTime? = null,

    @Column(name = "message", length = 1000)
    open var message: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class ForecastRunStatus {
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}

@Entity
@Table(name = "forecast_model_performance")
open class ForecastModelPerformanceEntity(
    @Id
    @Column(name = "forecast_model_performance_id", nullable = false)
    open var forecastModelPerformanceId: UUID = UUID.randomUUID(),

    @Column(name = "forecast_run_id", nullable = false)
    open var forecastRunId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "warehouse_id", nullable = false, length = 64)
    open var warehouseId: String = "",

    @Column(name = "sku_id", nullable = false, length = 80)
    open var skuId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "model_code", nullable = false, length = 60)
    open var modelCode: ForecastModelCode = ForecastModelCode.NAIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "demand_pattern", nullable = false, length = 30)
    open var demandPattern: DemandPattern = DemandPattern.SMOOTH,

    @Column(name = "training_sample_count", nullable = false)
    open var trainingSampleCount: Int = 0,

    @Column(name = "backtest_points", nullable = false)
    open var backtestPoints: Int = 0,

    @Column(name = "zero_demand_ratio", nullable = false, precision = 9, scale = 6)
    open var zeroDemandRatio: BigDecimal = BigDecimal.ZERO,

    @Column(name = "outliers_adjusted", nullable = false)
    open var outliersAdjusted: Int = 0,

    @Column(name = "mae", nullable = false, precision = 19, scale = 6)
    open var mae: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rmse", nullable = false, precision = 19, scale = 6)
    open var rmse: BigDecimal = BigDecimal.ZERO,

    @Column(name = "mape", precision = 19, scale = 6)
    open var mape: BigDecimal? = null,

    @Column(name = "wape", nullable = false, precision = 19, scale = 6)
    open var wape: BigDecimal = BigDecimal.ZERO,

    @Column(name = "smape", nullable = false, precision = 19, scale = 6)
    open var smape: BigDecimal = BigDecimal.ZERO,

    @Column(name = "bias", nullable = false, precision = 19, scale = 6)
    open var bias: BigDecimal = BigDecimal.ZERO,

    @Column(name = "selection_score", nullable = false, precision = 19, scale = 6)
    open var selectionScore: BigDecimal = BigDecimal.ZERO,

    @Column(name = "selected_model", nullable = false)
    open var selectedModel: Boolean = false,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ForecastModelCode {
    NAIVE,
    MOVING_AVERAGE,
    WEIGHTED_MOVING_AVERAGE,
    SEASONAL_NAIVE,
    SIMPLE_EXPONENTIAL_SMOOTHING,
    HOLT_LINEAR_TREND,
    HOLT_WINTERS_ADDITIVE,
    CROSTON_SBA
}

enum class DemandPattern {
    SMOOTH,
    ERRATIC,
    INTERMITTENT,
    LUMPY
}

@Entity
@Table(name = "forecast_result")
open class ForecastResultEntity(
    @Id
    @Column(name = "forecast_result_id", nullable = false)
    open var forecastResultId: UUID = UUID.randomUUID(),

    @Column(name = "forecast_run_id", nullable = false)
    open var forecastRunId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "warehouse_id", nullable = false, length = 64)
    open var warehouseId: String = "",

    @Column(name = "sku_id", nullable = false, length = 80)
    open var skuId: String = "",

    @Column(name = "forecast_date", nullable = false)
    open var forecastDate: LocalDate = LocalDate.now(),

    @Column(name = "horizon_day", nullable = false)
    open var horizonDay: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(name = "model_code", nullable = false, length = 60)
    open var modelCode: ForecastModelCode = ForecastModelCode.NAIVE,

    @Column(name = "forecast_quantity", nullable = false, precision = 19, scale = 4)
    open var forecastQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "lower_bound", nullable = false, precision = 19, scale = 4)
    open var lowerBound: BigDecimal = BigDecimal.ZERO,

    @Column(name = "upper_bound", nullable = false, precision = 19, scale = 4)
    open var upperBound: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    open var confidence: ForecastConfidence = ForecastConfidence.LOW,

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ForecastConfidence {
    HIGH,
    MEDIUM,
    LOW
}

@Entity
@Table(name = "forecast_exception")
open class ForecastExceptionEntity(
    @Id
    @Column(name = "forecast_exception_id", nullable = false)
    open var forecastExceptionId: UUID = UUID.randomUUID(),

    @Column(name = "forecast_run_id", nullable = false)
    open var forecastRunId: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false, length = 64)
    open var tenantId: String = "",

    @Column(name = "warehouse_id", length = 64)
    open var warehouseId: String? = null,

    @Column(name = "sku_id", length = 80)
    open var skuId: String? = null,

    @Column(name = "exception_code", nullable = false, length = 60)
    open var exceptionCode: String = "FORECAST_ERROR",

    @Column(name = "message", nullable = false, length = 1000)
    open var message: String = "",

    @Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now()
)
