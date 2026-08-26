package com.stockflow.forecasting.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ForecastConfigurationRepository : JpaRepository<ForecastConfigurationEntity, UUID> {
    fun findByTenantIdAndActiveTrue(tenantId: String): ForecastConfigurationEntity?
}

interface ForecastRunRepository : JpaRepository<ForecastRunEntity, UUID> {
    fun findByForecastRunIdAndTenantId(forecastRunId: UUID, tenantId: String): ForecastRunEntity?
    fun findTop20ByTenantIdOrderByStartedAtDesc(tenantId: String): List<ForecastRunEntity>
}

interface ForecastModelPerformanceRepository : JpaRepository<ForecastModelPerformanceEntity, UUID> {
    fun findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAscModelCodeAsc(
        forecastRunId: UUID
    ): List<ForecastModelPerformanceEntity>
}

interface ForecastResultRepository : JpaRepository<ForecastResultEntity, UUID> {
    fun findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAscForecastDateAsc(
        forecastRunId: UUID
    ): List<ForecastResultEntity>
}

interface ForecastExceptionRepository : JpaRepository<ForecastExceptionEntity, UUID> {
    fun findAllByForecastRunIdOrderByCreatedAtAsc(forecastRunId: UUID): List<ForecastExceptionEntity>
}

interface ForecastPositionDiagnosticRepository : JpaRepository<ForecastPositionDiagnosticEntity, UUID> {
    fun findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAsc(
        forecastRunId: UUID
    ): List<ForecastPositionDiagnosticEntity>
}
