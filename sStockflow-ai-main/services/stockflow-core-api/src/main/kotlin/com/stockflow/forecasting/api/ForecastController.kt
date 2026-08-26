package com.stockflow.forecasting.api

import com.stockflow.forecasting.application.CreateForecastRunRequest
import com.stockflow.forecasting.application.ForecastAccuracySummaryView
import com.stockflow.forecasting.application.ForecastCalibrationSummaryView
import com.stockflow.forecasting.application.ForecastConfigurationView
import com.stockflow.forecasting.application.ForecastExceptionView
import com.stockflow.forecasting.application.ForecastModelPerformanceView
import com.stockflow.forecasting.application.ForecastPositionDiagnosticView
import com.stockflow.forecasting.application.ForecastPositionView
import com.stockflow.forecasting.application.ForecastRunView
import com.stockflow.forecasting.application.ForecastSummaryView
import com.stockflow.forecasting.application.ForecastingService
import com.stockflow.forecasting.application.UpdateForecastConfigurationRequest
import com.stockflow.forecasting.persistence.ForecastEligibilityStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/forecasts")
class ForecastController(
    private val forecastingService: ForecastingService
) {
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRun(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @Valid @RequestBody request: CreateForecastRunRequest
    ): ForecastRunView = forecastingService.createRun(tenantId, request)

    @GetMapping("/runs")
    fun runs(@RequestHeader("X-Tenant-ID") tenantId: String): List<ForecastRunView> =
        forecastingService.runs(tenantId)

    @GetMapping("/runs/{runId}")
    fun run(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable runId: UUID
    ): ForecastRunView = forecastingService.run(tenantId, runId)

    @GetMapping("/runs/{runId}/exceptions")
    fun exceptions(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable runId: UUID
    ): List<ForecastExceptionView> = forecastingService.exceptions(tenantId, runId)

    @GetMapping("/latest")
    fun latest(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(required = false) warehouseId: String?,
        @RequestParam(required = false) skuId: String?,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<ForecastPositionView> = forecastingService.latest(tenantId, runId, warehouseId, skuId, limit)

    @GetMapping("/summary")
    fun summary(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?
    ): ForecastSummaryView = forecastingService.summary(tenantId, runId)

    @GetMapping("/model-performance")
    fun modelPerformance(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?
    ): List<ForecastModelPerformanceView> = forecastingService.performance(tenantId, runId)

    @GetMapping("/accuracy-summary")
    fun accuracySummary(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?
    ): ForecastAccuracySummaryView = forecastingService.accuracySummary(tenantId, runId)

    @GetMapping("/diagnostics")
    fun diagnostics(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(required = false) warehouseId: String?,
        @RequestParam(required = false) skuId: String?,
        @RequestParam(required = false) eligibility: ForecastEligibilityStatus?,
        @RequestParam(defaultValue = "250") limit: Int
    ): List<ForecastPositionDiagnosticView> = forecastingService.diagnostics(
        tenantId, runId, warehouseId, skuId, eligibility, limit
    )

    @GetMapping("/diagnostics/{warehouseId}/{skuId}")
    fun diagnostic(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @PathVariable warehouseId: String,
        @PathVariable skuId: String,
        @RequestParam(required = false) runId: UUID?
    ): ForecastPositionDiagnosticView = forecastingService.diagnostic(tenantId, warehouseId, skuId, runId)

    @GetMapping("/calibration-summary")
    fun calibrationSummary(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?
    ): ForecastCalibrationSummaryView = forecastingService.calibrationSummary(tenantId, runId)

    @GetMapping("/configuration")
    fun configuration(
        @RequestHeader("X-Tenant-ID") tenantId: String
    ): ForecastConfigurationView = forecastingService.configuration(tenantId)

    @PutMapping("/configuration")
    fun updateConfiguration(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @Valid @RequestBody request: UpdateForecastConfigurationRequest
    ): ForecastConfigurationView = forecastingService.updateConfiguration(tenantId, request)

    @GetMapping("/stockout-projections")
    fun stockoutProjections(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<ForecastPositionView> = forecastingService.latest(tenantId, runId, null, null, limit)
        .filter { it.projectedStockoutDate != null }
}
