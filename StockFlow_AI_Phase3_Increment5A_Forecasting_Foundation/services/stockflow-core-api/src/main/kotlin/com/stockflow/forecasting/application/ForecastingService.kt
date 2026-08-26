package com.stockflow.forecasting.application

import com.stockflow.common.errors.InvalidForecastException
import com.stockflow.common.errors.ResourceNotFoundException
import com.stockflow.forecasting.domain.DemandForecastModel
import com.stockflow.forecasting.domain.MovingAverageForecastModel
import com.stockflow.forecasting.domain.NaiveForecastModel
import com.stockflow.forecasting.domain.SeasonalNaiveForecastModel
import com.stockflow.forecasting.domain.WeightedMovingAverageForecastModel
import com.stockflow.forecasting.persistence.ForecastConfidence
import com.stockflow.forecasting.persistence.ForecastConfigurationEntity
import com.stockflow.forecasting.persistence.ForecastConfigurationRepository
import com.stockflow.forecasting.persistence.ForecastExceptionEntity
import com.stockflow.forecasting.persistence.ForecastExceptionRepository
import com.stockflow.forecasting.persistence.ForecastModelCode
import com.stockflow.forecasting.persistence.ForecastModelPerformanceEntity
import com.stockflow.forecasting.persistence.ForecastModelPerformanceRepository
import com.stockflow.forecasting.persistence.ForecastResultEntity
import com.stockflow.forecasting.persistence.ForecastResultRepository
import com.stockflow.forecasting.persistence.ForecastRunEntity
import com.stockflow.forecasting.persistence.ForecastRunRepository
import com.stockflow.forecasting.persistence.ForecastRunStatus
import com.stockflow.intelligence.application.InventoryIntelligenceQueryService
import com.stockflow.product.persistence.SkuRepository
import com.stockflow.warehouse.persistence.WarehouseRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.sqrt

@Service
class ForecastingService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val intelligenceQueryService: InventoryIntelligenceQueryService,
    private val warehouseRepository: WarehouseRepository,
    private val skuRepository: SkuRepository,
    private val configurationRepository: ForecastConfigurationRepository,
    private val runRepository: ForecastRunRepository,
    private val performanceRepository: ForecastModelPerformanceRepository,
    private val resultRepository: ForecastResultRepository,
    private val exceptionRepository: ForecastExceptionRepository
) {
    @Transactional
    fun createRun(tenantId: String, request: CreateForecastRunRequest): ForecastRunView {
        intelligenceQueryService.requireTenant(tenantId)
        validateRequest(tenantId, request)

        val configuration = configurationRepository.findByTenantIdAndActiveTrue(tenantId)
            ?: configurationRepository.save(ForecastConfigurationEntity(tenantId = tenantId))
        val latestSalesDate = intelligenceQueryService.salesAsOfDate(tenantId)
            ?: throw ResourceNotFoundException("No sales history is available for tenant '$tenantId'")
        val asOfDate = request.asOfDate ?: latestSalesDate
        if (asOfDate.isAfter(latestSalesDate)) {
            throw InvalidForecastException(
                "asOfDate $asOfDate is after the latest available sales date $latestSalesDate"
            )
        }
        val historyDays = request.historyDays ?: configuration.defaultHistoryDays
        val fromDate = asOfDate.minusDays(historyDays.toLong() - 1)
        val requestedModels = requestedModels(configuration, request)
        val observations = demandObservations(
            tenantId = tenantId,
            fromDate = fromDate,
            asOfDate = asOfDate,
            warehouseId = request.warehouseId,
            skuId = request.skuId
        )
        if (observations.isEmpty()) {
            throw ResourceNotFoundException("No demand history matched the requested forecast scope")
        }

        val grouped = observations.groupBy { PositionKey(it.warehouseId, it.skuId) }
        val now = LocalDateTime.now()
        val run = runRepository.save(
            ForecastRunEntity(
                tenantId = tenantId,
                asOfDate = asOfDate,
                horizonDays = request.horizonDays,
                historyDays = historyDays,
                requestedWarehouseId = request.warehouseId,
                requestedSkuId = request.skuId,
                status = ForecastRunStatus.RUNNING,
                positionsRequested = grouped.size,
                startedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )

        var processed = 0
        var failed = 0
        grouped.toSortedMap(compareBy<PositionKey> { it.warehouseId }.thenBy { it.skuId })
            .forEach { (key, rows) ->
                try {
                    val metadata = rows.first()
                    val history = completeHistory(fromDate, asOfDate, rows)
                    if (history.size < configuration.minimumHistoryDays) {
                        throw InvalidForecastException(
                            "At least ${configuration.minimumHistoryDays} history days are required; found ${history.size}"
                        )
                    }
                    if (history.all { it.compareTo(BigDecimal.ZERO) == 0 }) {
                        throw InvalidForecastException("Demand history contains no positive sales quantity")
                    }

                    val models = models(configuration, requestedModels)
                    val scores = models.map { model ->
                        backtest(
                            model = model,
                            history = history,
                            backtestDays = configuration.backtestDays,
                            minimumTrainingDays = (configuration.minimumHistoryDays / 2).coerceAtLeast(14)
                        )
                    }
                    val selected = scores.minWithOrNull(
                        compareBy<ModelScore> { it.mape ?: BigDecimal("999999") }
                            .thenBy { it.mae }
                            .thenBy { it.model.code.name }
                    ) ?: throw InvalidForecastException("No forecasting model could be evaluated")
                    val confidence = confidence(selected, history.size)

                    performanceRepository.saveAll(scores.map { score ->
                        ForecastModelPerformanceEntity(
                            forecastRunId = run.forecastRunId,
                            tenantId = tenantId,
                            warehouseId = key.warehouseId,
                            skuId = key.skuId,
                            modelCode = score.model.code,
                            trainingSampleCount = history.size,
                            backtestPoints = score.backtestPoints,
                            mae = score.mae,
                            rmse = score.rmse,
                            mape = score.mape,
                            bias = score.bias,
                            selectedModel = score.model.code == selected.model.code
                        )
                    })

                    val forecasts = selected.model.forecast(history, request.horizonDays)
                    val intervalWidth = selected.rmse.multiply(BigDecimal("1.96"))
                    resultRepository.saveAll(forecasts.mapIndexed { index, value ->
                        val quantity = value.nonNegative().setScale(4, RoundingMode.HALF_UP)
                        ForecastResultEntity(
                            forecastRunId = run.forecastRunId,
                            tenantId = tenantId,
                            warehouseId = metadata.warehouseId,
                            skuId = metadata.skuId,
                            forecastDate = asOfDate.plusDays(index.toLong() + 1),
                            horizonDay = index + 1,
                            modelCode = selected.model.code,
                            forecastQuantity = quantity,
                            lowerBound = quantity.subtract(intervalWidth).nonNegative()
                                .setScale(4, RoundingMode.HALF_UP),
                            upperBound = quantity.add(intervalWidth).setScale(4, RoundingMode.HALF_UP),
                            confidence = confidence
                        )
                    })
                    processed++
                } catch (error: InvalidForecastException) {
                    failed++
                    exceptionRepository.save(
                        ForecastExceptionEntity(
                            forecastRunId = run.forecastRunId,
                            tenantId = tenantId,
                            warehouseId = key.warehouseId,
                            skuId = key.skuId,
                            exceptionCode = "INSUFFICIENT_OR_UNUSABLE_HISTORY",
                            message = error.message ?: "Forecast generation failed"
                        )
                    )
                }
            }

        run.positionsProcessed = processed
        run.positionsFailed = failed
        run.completedAt = LocalDateTime.now()
        run.status = when {
            processed == 0 -> ForecastRunStatus.FAILED
            failed == 0 -> ForecastRunStatus.COMPLETED
            else -> ForecastRunStatus.COMPLETED_WITH_ERRORS
        }
        run.message = "Generated ${processed * request.horizonDays} daily forecast values for $processed positions; $failed positions failed"
        run.updatedAt = LocalDateTime.now()
        return runRepository.save(run).toView()
    }

    fun runs(tenantId: String): List<ForecastRunView> {
        intelligenceQueryService.requireTenant(tenantId)
        return runRepository.findTop20ByTenantIdOrderByStartedAtDesc(tenantId).map { it.toView() }
    }

    fun run(tenantId: String, runId: UUID): ForecastRunView {
        intelligenceQueryService.requireTenant(tenantId)
        return requireRun(tenantId, runId).toView()
    }

    fun latest(
        tenantId: String,
        runId: UUID?,
        warehouseId: String?,
        skuId: String?,
        limit: Int
    ): List<ForecastPositionView> {
        intelligenceQueryService.requireTenant(tenantId)
        val run = runId?.let { requireRun(tenantId, it) } ?: latestCompletedRun(tenantId)
        val safeLimit = limit.coerceIn(1, 250)
        val results = resultRepository
            .findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAscForecastDateAsc(run.forecastRunId)
            .asSequence()
            .filter { warehouseId == null || it.warehouseId == warehouseId }
            .filter { skuId == null || it.skuId == skuId }
            .groupBy { PositionKey(it.warehouseId, it.skuId) }
            .entries
            .take(safeLimit)

        val selectedPerformance = performanceRepository
            .findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAscModelCodeAsc(run.forecastRunId)
            .filter { it.selectedModel }
            .associateBy { PositionKey(it.warehouseId, it.skuId) }
        val names = positionNames(tenantId)
        val inventoryDate = intelligenceQueryService.inventoryAsOfDate(tenantId)
        val inventory = inventoryDate?.let { date ->
            intelligenceQueryService.inventoryPositions(tenantId, date)
                .associateBy { PositionKey(it.warehouseId, it.skuId) }
        }.orEmpty()

        return results.mapNotNull { (key, values) ->
            val performance = selectedPerformance[key] ?: return@mapNotNull null
            val identity = names[key] ?: PositionNames(key.warehouseId, key.skuId)
            val positionInventory = inventory[key]
            val forecastValues = values.map {
                ForecastValueView(
                    forecastDate = it.forecastDate,
                    horizonDay = it.horizonDay,
                    forecastQuantity = it.forecastQuantity,
                    lowerBound = it.lowerBound,
                    upperBound = it.upperBound
                )
            }
            val total = forecastValues.fold(BigDecimal.ZERO) { acc, value ->
                acc.add(value.forecastQuantity)
            }.setScale(4, RoundingMode.HALF_UP)
            ForecastPositionView(
                forecastRunId = run.forecastRunId,
                tenantId = tenantId,
                asOfDate = run.asOfDate,
                warehouseId = key.warehouseId,
                warehouseName = identity.warehouseName,
                skuId = key.skuId,
                skuName = identity.skuName,
                selectedModel = performance.modelCode,
                confidence = values.first().confidence,
                trainingSampleCount = performance.trainingSampleCount,
                backtestPoints = performance.backtestPoints,
                mae = performance.mae,
                rmse = performance.rmse,
                mape = performance.mape,
                bias = performance.bias,
                horizonDays = run.horizonDays,
                totalForecastQuantity = total,
                averageDailyForecast = total.divide(
                    BigDecimal.valueOf(run.horizonDays.toLong()), 4, RoundingMode.HALF_UP
                ),
                usableInventory = positionInventory?.usableQuantity,
                inventoryDataAvailable = positionInventory != null,
                projectedStockoutDate = projectedStockoutDate(
                    positionInventory?.usableQuantity,
                    forecastValues
                ),
                forecastValues = forecastValues
            )
        }
    }

    fun summary(tenantId: String, runId: UUID?): ForecastSummaryView {
        val positions = latest(tenantId, runId, null, null, 250)
        if (positions.isEmpty()) {
            throw ResourceNotFoundException("No forecast results are available")
        }
        val first = positions.first()
        return ForecastSummaryView(
            tenantId = tenantId,
            forecastRunId = first.forecastRunId,
            asOfDate = first.asOfDate,
            horizonDays = first.horizonDays,
            positionsForecasted = positions.size,
            highConfidenceCount = positions.count { it.confidence == ForecastConfidence.HIGH },
            mediumConfidenceCount = positions.count { it.confidence == ForecastConfidence.MEDIUM },
            lowConfidenceCount = positions.count { it.confidence == ForecastConfidence.LOW },
            projectedStockoutCount = positions.count { it.projectedStockoutDate != null },
            totalForecastQuantity = positions.fold(BigDecimal.ZERO) { acc, item ->
                acc.add(item.totalForecastQuantity)
            }.setScale(4, RoundingMode.HALF_UP),
            modelUsage = positions.groupingBy { it.selectedModel }.eachCount()
        )
    }

    fun performance(tenantId: String, runId: UUID?): List<ForecastModelPerformanceView> {
        intelligenceQueryService.requireTenant(tenantId)
        val run = runId?.let { requireRun(tenantId, it) } ?: latestCompletedRun(tenantId)
        val names = positionNames(tenantId)
        return performanceRepository
            .findAllByForecastRunIdOrderByWarehouseIdAscSkuIdAscModelCodeAsc(run.forecastRunId)
            .map { entity ->
                val identity = names[PositionKey(entity.warehouseId, entity.skuId)]
                    ?: PositionNames(entity.warehouseId, entity.skuId)
                ForecastModelPerformanceView(
                    forecastRunId = run.forecastRunId,
                    warehouseId = entity.warehouseId,
                    warehouseName = identity.warehouseName,
                    skuId = entity.skuId,
                    skuName = identity.skuName,
                    modelCode = entity.modelCode,
                    trainingSampleCount = entity.trainingSampleCount,
                    backtestPoints = entity.backtestPoints,
                    mae = entity.mae,
                    rmse = entity.rmse,
                    mape = entity.mape,
                    bias = entity.bias,
                    selectedModel = entity.selectedModel
                )
            }
    }

    fun exceptions(tenantId: String, runId: UUID): List<ForecastExceptionView> {
        requireRun(tenantId, runId)
        return exceptionRepository.findAllByForecastRunIdOrderByCreatedAtAsc(runId).map {
            ForecastExceptionView(
                exceptionCode = it.exceptionCode,
                warehouseId = it.warehouseId,
                skuId = it.skuId,
                message = it.message,
                createdAt = it.createdAt
            )
        }
    }

    private fun validateRequest(tenantId: String, request: CreateForecastRunRequest) {
        if (request.horizonDays !in setOf(7, 30, 90)) {
            throw InvalidForecastException("horizonDays must be one of 7, 30 or 90")
        }
        request.warehouseId?.let { warehouseId ->
            if (warehouseRepository.findByWarehouseIdAndTenantIdAndActiveTrue(warehouseId, tenantId) == null) {
                throw ResourceNotFoundException("Warehouse '$warehouseId' was not found for tenant '$tenantId'")
            }
        }
        request.skuId?.let { skuId ->
            if (skuRepository.findBySkuIdAndTenantIdAndActiveTrue(skuId, tenantId) == null) {
                throw ResourceNotFoundException("SKU '$skuId' was not found for tenant '$tenantId'")
            }
        }
    }

    private fun requestedModels(
        configuration: ForecastConfigurationEntity,
        request: CreateForecastRunRequest
    ): Set<ForecastModelCode> {
        val enabled = configuration.enabledModels.split(',')
            .mapNotNull { code -> runCatching { ForecastModelCode.valueOf(code.trim()) }.getOrNull() }
            .toSet()
        val requested = request.models?.takeIf { it.isNotEmpty() } ?: enabled
        val selected = requested.intersect(enabled)
        if (selected.isEmpty()) {
            throw InvalidForecastException("No requested forecasting model is enabled for this tenant")
        }
        return selected
    }

    private fun models(
        configuration: ForecastConfigurationEntity,
        modelCodes: Set<ForecastModelCode>
    ): List<DemandForecastModel> = modelCodes.sortedBy { it.name }.map { code ->
        when (code) {
            ForecastModelCode.NAIVE -> NaiveForecastModel()
            ForecastModelCode.MOVING_AVERAGE -> MovingAverageForecastModel(configuration.movingAverageWindow)
            ForecastModelCode.WEIGHTED_MOVING_AVERAGE ->
                WeightedMovingAverageForecastModel(configuration.movingAverageWindow)
            ForecastModelCode.SEASONAL_NAIVE -> SeasonalNaiveForecastModel(configuration.seasonalPeriodDays)
        }
    }

    private fun backtest(
        model: DemandForecastModel,
        history: List<BigDecimal>,
        backtestDays: Int,
        minimumTrainingDays: Int
    ): ModelScore {
        val start = maxOf(minimumTrainingDays, history.size - backtestDays)
        val predictions = mutableListOf<BigDecimal>()
        val actuals = mutableListOf<BigDecimal>()
        for (index in start until history.size) {
            val training = history.subList(0, index)
            if (training.isEmpty()) continue
            predictions += model.forecast(training, 1).first().nonNegative()
            actuals += history[index]
        }
        if (predictions.isEmpty()) {
            throw InvalidForecastException("Not enough observations for backtesting")
        }

        val absoluteErrors = predictions.indices.map { index ->
            predictions[index].subtract(actuals[index]).abs()
        }
        val squaredErrors = predictions.indices.map { index ->
            val error = predictions[index].subtract(actuals[index]).toDouble()
            error * error
        }
        val percentageErrors = predictions.indices.mapNotNull { index ->
            val actual = actuals[index]
            if (actual.compareTo(BigDecimal.ZERO) == 0) null else
                predictions[index].subtract(actual).abs()
                    .multiply(BigDecimal("100"))
                    .divide(actual.abs(), 8, RoundingMode.HALF_UP)
        }
        val signedErrors = predictions.indices.map { index -> predictions[index].subtract(actuals[index]) }

        return ModelScore(
            model = model,
            backtestPoints = predictions.size,
            mae = absoluteErrors.averageBigDecimal(6),
            rmse = BigDecimal.valueOf(sqrt(squaredErrors.average())).setScale(6, RoundingMode.HALF_UP),
            mape = percentageErrors.takeIf { it.isNotEmpty() }?.averageBigDecimal(6),
            bias = signedErrors.averageBigDecimal(6)
        )
    }

    private fun confidence(score: ModelScore, historyDays: Int): ForecastConfidence = when {
        score.mape != null && score.mape <= BigDecimal("15") && historyDays >= 60 -> ForecastConfidence.HIGH
        score.mape != null && score.mape <= BigDecimal("30") && historyDays >= 28 -> ForecastConfidence.MEDIUM
        score.mape == null && score.mae <= BigDecimal("5") && historyDays >= 60 -> ForecastConfidence.MEDIUM
        else -> ForecastConfidence.LOW
    }

    private fun completeHistory(
        fromDate: LocalDate,
        asOfDate: LocalDate,
        observations: List<DemandObservation>
    ): List<BigDecimal> {
        val byDate = observations.associate { it.salesDate to it.quantity }
        val days = ChronoUnit.DAYS.between(fromDate, asOfDate).toInt() + 1
        return (0 until days).map { offset ->
            byDate[fromDate.plusDays(offset.toLong())] ?: BigDecimal.ZERO
        }
    }

    private fun demandObservations(
        tenantId: String,
        fromDate: LocalDate,
        asOfDate: LocalDate,
        warehouseId: String?,
        skuId: String?
    ): List<DemandObservation> {
        val params = MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("fromDate", fromDate)
            .addValue("asOfDate", asOfDate)
        val scopeFilters = buildString {
            if (warehouseId != null) {
                append(" AND sh.warehouse_id = :warehouseId")
                params.addValue("warehouseId", warehouseId)
            }
            if (skuId != null) {
                append(" AND sh.sku_id = :skuId")
                params.addValue("skuId", skuId)
            }
        }
        val sql = """
            SELECT
                sh.warehouse_id,
                w.warehouse_name,
                sh.sku_id,
                s.sku_name,
                sh.sales_date,
                SUM(sh.sales_quantity) AS daily_quantity
            FROM sales_history sh
            JOIN warehouse w ON w.warehouse_id = sh.warehouse_id AND w.tenant_id = sh.tenant_id
            JOIN sku s ON s.sku_id = sh.sku_id AND s.tenant_id = sh.tenant_id
            WHERE sh.tenant_id = :tenantId
              AND sh.sales_date BETWEEN :fromDate AND :asOfDate
              $scopeFilters
            GROUP BY sh.warehouse_id, w.warehouse_name, sh.sku_id, s.sku_name, sh.sales_date
            ORDER BY sh.warehouse_id, sh.sku_id, sh.sales_date
            """.trimIndent()
        return jdbcTemplate.query(sql, params) { rs, _ ->
            DemandObservation(
                warehouseId = rs.getString("warehouse_id"),
                warehouseName = rs.getString("warehouse_name"),
                skuId = rs.getString("sku_id"),
                skuName = rs.getString("sku_name"),
                salesDate = rs.getDate("sales_date").toLocalDate(),
                quantity = rs.getBigDecimal("daily_quantity") ?: BigDecimal.ZERO
            )
        }
    }

    private fun positionNames(tenantId: String): Map<PositionKey, PositionNames> = jdbcTemplate.query(
        """
        SELECT DISTINCT sh.warehouse_id, w.warehouse_name, sh.sku_id, s.sku_name
        FROM sales_history sh
        JOIN warehouse w ON w.warehouse_id = sh.warehouse_id AND w.tenant_id = sh.tenant_id
        JOIN sku s ON s.sku_id = sh.sku_id AND s.tenant_id = sh.tenant_id
        WHERE sh.tenant_id = :tenantId
        """.trimIndent(),
        mapOf("tenantId" to tenantId)
    ) { rs, _ ->
        PositionKey(rs.getString("warehouse_id"), rs.getString("sku_id")) to PositionNames(
            warehouseName = rs.getString("warehouse_name"),
            skuName = rs.getString("sku_name")
        )
    }.toMap()

    private fun projectedStockoutDate(
        usableInventory: Long?,
        forecasts: List<ForecastValueView>
    ): LocalDate? {
        if (usableInventory == null) return null
        var remaining = BigDecimal.valueOf(usableInventory.toLong())
        forecasts.forEach { value ->
            remaining = remaining.subtract(value.forecastQuantity)
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) return value.forecastDate
        }
        return null
    }

    private fun latestCompletedRun(tenantId: String): ForecastRunEntity = runRepository
        .findTop20ByTenantIdOrderByStartedAtDesc(tenantId)
        .firstOrNull { it.status == ForecastRunStatus.COMPLETED || it.status == ForecastRunStatus.COMPLETED_WITH_ERRORS }
        ?: throw ResourceNotFoundException("No completed forecast run is available for tenant '$tenantId'")

    private fun requireRun(tenantId: String, runId: UUID): ForecastRunEntity =
        runRepository.findByForecastRunIdAndTenantId(runId, tenantId)
            ?: throw ResourceNotFoundException("Forecast run '$runId' was not found for tenant '$tenantId'")

    private fun ForecastRunEntity.toView(): ForecastRunView = ForecastRunView(
        forecastRunId = forecastRunId,
        tenantId = tenantId,
        asOfDate = asOfDate,
        horizonDays = horizonDays,
        historyDays = historyDays,
        requestedWarehouseId = requestedWarehouseId,
        requestedSkuId = requestedSkuId,
        status = status,
        positionsRequested = positionsRequested,
        positionsProcessed = positionsProcessed,
        positionsFailed = positionsFailed,
        startedAt = startedAt,
        completedAt = completedAt,
        message = message
    )
}

private data class DemandObservation(
    val warehouseId: String,
    val warehouseName: String,
    val skuId: String,
    val skuName: String,
    val salesDate: LocalDate,
    val quantity: BigDecimal
)

private data class PositionKey(val warehouseId: String, val skuId: String)
private data class PositionNames(val warehouseName: String, val skuName: String)

private data class ModelScore(
    val model: DemandForecastModel,
    val backtestPoints: Int,
    val mae: BigDecimal,
    val rmse: BigDecimal,
    val mape: BigDecimal?,
    val bias: BigDecimal
)

private fun List<BigDecimal>.averageBigDecimal(scale: Int): BigDecimal =
    fold(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(size.toLong()), scale, RoundingMode.HALF_UP)

private fun BigDecimal.nonNegative(): BigDecimal = if (signum() < 0) BigDecimal.ZERO else this
