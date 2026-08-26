package com.stockflow.forecasting.domain

import com.stockflow.forecasting.persistence.DemandPattern
import com.stockflow.forecasting.persistence.ForecastModelCode
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

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

class SimpleExponentialSmoothingForecastModel(
    private val alpha: Double
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.SIMPLE_EXPONENTIAL_SMOOTHING

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Exponential smoothing requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }

        var level = history.first().toDouble()
        history.drop(1).forEach { observation ->
            level = alpha * observation.toDouble() + (1.0 - alpha) * level
        }
        return List(horizonDays) { level.toQuantity() }
    }
}

class HoltLinearTrendForecastModel(
    private val alpha: Double,
    private val beta: Double
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.HOLT_LINEAR_TREND

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Holt trend forecast requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }
        require(beta in 0.01..0.99) { "beta must be between 0.01 and 0.99" }
        if (history.size == 1) return List(horizonDays) { history.first().nonNegative() }

        var level = history.first().toDouble()
        var trend = history[1].toDouble() - history[0].toDouble()
        history.drop(1).forEach { observation ->
            val previousLevel = level
            level = alpha * observation.toDouble() + (1.0 - alpha) * (level + trend)
            trend = beta * (level - previousLevel) + (1.0 - beta) * trend
        }

        return (1..horizonDays).map { step -> (level + step * trend).toQuantity() }
    }
}

class HoltWintersAdditiveForecastModel(
    private val alpha: Double,
    private val beta: Double,
    private val gamma: Double,
    private val periodDays: Int
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.HOLT_WINTERS_ADDITIVE

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Holt-Winters forecast requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }
        require(beta in 0.01..0.99) { "beta must be between 0.01 and 0.99" }
        require(gamma in 0.01..0.99) { "gamma must be between 0.01 and 0.99" }
        require(periodDays >= 2) { "seasonal period must be at least two days" }

        if (history.size < periodDays * 2) {
            return HoltLinearTrendForecastModel(alpha, beta).forecast(history, horizonDays)
        }

        val values = history.map(BigDecimal::toDouble)
        val firstSeasonAverage = values.take(periodDays).average()
        val secondSeasonAverage = values.drop(periodDays).take(periodDays).average()
        var level = firstSeasonAverage
        var trend = (secondSeasonAverage - firstSeasonAverage) / periodDays
        val seasonals = MutableList(periodDays) { index -> values[index] - firstSeasonAverage }

        for (index in periodDays until values.size) {
            val observation = values[index]
            val seasonIndex = index % periodDays
            val previousLevel = level
            val previousSeasonal = seasonals[seasonIndex]
            level = alpha * (observation - previousSeasonal) + (1.0 - alpha) * (level + trend)
            trend = beta * (level - previousLevel) + (1.0 - beta) * trend
            seasonals[seasonIndex] = gamma * (observation - level) + (1.0 - gamma) * previousSeasonal
        }

        return (1..horizonDays).map { step ->
            val seasonIndex = (values.size + step - 1) % periodDays
            (level + step * trend + seasonals[seasonIndex]).toQuantity()
        }
    }
}

class CrostonSbaForecastModel(
    private val alpha: Double
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.CROSTON_SBA

    override fun forecast(history: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Croston forecast requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }

        val values = history.map { it.toDouble().coerceAtLeast(0.0) }
        val firstPositiveIndex = values.indexOfFirst { it > 0.0 }
        if (firstPositiveIndex < 0) return List(horizonDays) { BigDecimal.ZERO.setScale(6) }

        var demandEstimate = values[firstPositiveIndex]
        var intervalEstimate = (firstPositiveIndex + 1).toDouble()
        var interval = 1

        for (index in firstPositiveIndex + 1 until values.size) {
            val observation = values[index]
            if (observation > 0.0) {
                demandEstimate += alpha * (observation - demandEstimate)
                intervalEstimate += alpha * (interval - intervalEstimate)
                interval = 1
            } else {
                interval++
            }
        }

        val sbaCorrection = 1.0 - alpha / 2.0
        val prediction = if (intervalEstimate <= 0.0) 0.0 else sbaCorrection * demandEstimate / intervalEstimate
        return List(horizonDays) { prediction.toQuantity() }
    }
}

data class PreprocessedDemand(
    val history: List<BigDecimal>,
    val demandPattern: DemandPattern,
    val zeroDemandRatio: BigDecimal,
    val outliersAdjusted: Int
)

object DemandPreprocessor {
    fun preprocess(history: List<BigDecimal>, treatOutliers: Boolean): PreprocessedDemand {
        require(history.isNotEmpty()) { "Demand history cannot be empty" }
        val nonNegative = history.map(BigDecimal::nonNegative)
        val positiveValues = nonNegative.filter { it.signum() > 0 }.map(BigDecimal::toDouble).sorted()
        val adjusted = nonNegative.toMutableList()
        var outlierCount = 0

        if (treatOutliers && positiveValues.size >= 8) {
            val q1 = percentile(positiveValues, 0.25)
            val q3 = percentile(positiveValues, 0.75)
            val iqr = q3 - q1
            val lower = (q1 - 1.5 * iqr).coerceAtLeast(0.0)
            val upper = q3 + 1.5 * iqr
            adjusted.indices.forEach { index ->
                val original = adjusted[index].toDouble()
                if (original > 0.0 && (original < lower || original > upper)) {
                    adjusted[index] = original.coerceIn(lower, upper).toQuantity()
                    outlierCount++
                }
            }
        }

        val zeroCount = nonNegative.count { it.signum() == 0 }
        val zeroRatio = BigDecimal.valueOf(zeroCount.toDouble() / nonNegative.size)
            .setScale(6, RoundingMode.HALF_UP)

        return PreprocessedDemand(
            history = adjusted,
            demandPattern = classifyDemandPattern(nonNegative),
            zeroDemandRatio = zeroRatio,
            outliersAdjusted = outlierCount
        )
    }

    fun classifyDemandPattern(history: List<BigDecimal>): DemandPattern {
        val positive = history.filter { it.signum() > 0 }.map(BigDecimal::toDouble)
        if (positive.isEmpty()) return DemandPattern.INTERMITTENT

        val averageDemandInterval = history.size.toDouble() / positive.size.toDouble()
        val mean = positive.average()
        val variance = positive.map { (it - mean).pow(2) }.average()
        val coefficientOfVariationSquared = if (mean == 0.0) 0.0 else variance / mean.pow(2)

        return when {
            averageDemandInterval < 1.32 && coefficientOfVariationSquared < 0.49 -> DemandPattern.SMOOTH
            averageDemandInterval < 1.32 && coefficientOfVariationSquared >= 0.49 -> DemandPattern.ERRATIC
            averageDemandInterval >= 1.32 && coefficientOfVariationSquared < 0.49 -> DemandPattern.INTERMITTENT
            else -> DemandPattern.LUMPY
        }
    }

    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.size == 1) return sortedValues.first()
        val position = percentile * (sortedValues.size - 1)
        val lowerIndex = position.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(sortedValues.lastIndex)
        val fraction = position - lowerIndex
        return sortedValues[lowerIndex] + fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex])
    }
}

private fun Iterable<BigDecimal>.sumBigDecimal(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)
private fun BigDecimal.nonNegative(): BigDecimal = if (signum() < 0) BigDecimal.ZERO else this
private fun Double.toQuantity(): BigDecimal = BigDecimal.valueOf(coerceAtLeast(0.0)).setScale(6, RoundingMode.HALF_UP)
