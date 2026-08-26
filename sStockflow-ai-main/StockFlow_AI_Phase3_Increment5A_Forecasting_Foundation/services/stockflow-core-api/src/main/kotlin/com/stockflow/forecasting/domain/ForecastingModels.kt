package com.stockflow.forecasting.domain

import com.stockflow.forecasting.persistence.ForecastModelCode
import java.math.BigDecimal
import java.math.RoundingMode

interface DemandForecastModel {
    val code: ForecastModelCode
    fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal>
}

class NaiveForecastModel : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.NAIVE

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Naive forecast requires at least one observation" }
        return List(horizonDays) { history.last().nonNegative() }
    }
}

class MovingAverageForecastModel(
    private val window: Int
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.MOVING_AVERAGE

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Moving-average forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonDays) {
            val sample = values.takeLast(window.coerceAtMost(values.size))
            val prediction = sample.sumBigDecimal()
                .divide(BigDecimal.valueOf(sample.size.toLong()), 6, RoundingMode.HALF_UP)
                .nonNegative()
            output += prediction
            values += prediction
        }
        return output
    }
}

class WeightedMovingAverageForecastModel(
    private val window: Int
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.WEIGHTED_MOVING_AVERAGE

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Weighted moving-average forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonDays) {
            val sample = values.takeLast(window.coerceAtMost(values.size))
            val weightedTotal = sample.mapIndexed { index, value ->
                value.multiply(BigDecimal.valueOf((index + 1).toLong()))
            }.sumBigDecimal()
            val totalWeight = sample.indices.sumOf { it + 1 }
            val prediction = weightedTotal
                .divide(BigDecimal.valueOf(totalWeight.toLong()), 6, RoundingMode.HALF_UP)
                .nonNegative()
            output += prediction
            values += prediction
        }
        return output
    }
}

class SeasonalNaiveForecastModel(
    private val periodDays: Int
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.SEASONAL_NAIVE

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Seasonal-naive forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonDays) {
            val sourceIndex = (values.size - periodDays).coerceAtLeast(0)
            val prediction = values[sourceIndex].nonNegative()
            output += prediction
            values += prediction
        }
        return output
    }
}

private fun Iterable<BigDecimal>.sumBigDecimal(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)
private fun BigDecimal.nonNegative(): BigDecimal = if (signum() < 0) BigDecimal.ZERO else this
