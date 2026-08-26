package com.stockflow.forecasting.domain

import com.stockflow.forecasting.persistence.DemandPattern
import com.stockflow.forecasting.persistence.ForecastModelCode
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.pow

interface DemandForecastModel {
    val code: ForecastModelCode
    fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal>
}

class NaiveForecastModel : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.NAIVE

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Naive forecast requires at least one observation" }
        return List(horizonPeriods) { history.last().nonNegative() }
    }
}

class MovingAverageForecastModel(private val window: Int) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.MOVING_AVERAGE

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Moving-average forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonPeriods) {
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

class WeightedMovingAverageForecastModel(private val window: Int) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.WEIGHTED_MOVING_AVERAGE

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Weighted moving-average forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonPeriods) {
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

class SeasonalNaiveForecastModel(private val periodDays: Int) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.SEASONAL_NAIVE

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Seasonal-naive forecast requires at least one observation" }
        val values = history.toMutableList()
        val output = mutableListOf<BigDecimal>()
        repeat(horizonPeriods) {
            val sourceIndex = (values.size - periodDays).coerceAtLeast(0)
            val prediction = values[sourceIndex].nonNegative()
            output += prediction
            values += prediction
        }
        return output
    }
}

class SimpleExponentialSmoothingForecastModel(private val alpha: Double) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.SIMPLE_EXPONENTIAL_SMOOTHING

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Exponential smoothing requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }
        var level = history.first().toDouble()
        history.drop(1).forEach { observation ->
            level = alpha * observation.toDouble() + (1.0 - alpha) * level
        }
        return List(horizonPeriods) { level.toQuantity() }
    }
}

class HoltLinearTrendForecastModel(
    private val alpha: Double,
    private val beta: Double
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.HOLT_LINEAR_TREND

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Holt trend forecast requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }
        require(beta in 0.01..0.99) { "beta must be between 0.01 and 0.99" }
        if (history.size == 1) return List(horizonPeriods) { history.first().nonNegative() }

        var level = history.first().toDouble()
        var trend = history[1].toDouble() - history[0].toDouble()
        history.drop(1).forEach { observation ->
            val previousLevel = level
            level = alpha * observation.toDouble() + (1.0 - alpha) * (level + trend)
            trend = beta * (level - previousLevel) + (1.0 - beta) * trend
        }
        return (1..horizonPeriods).map { step -> (level + step * trend).toQuantity() }
    }
}

class HoltWintersAdditiveForecastModel(
    private val alpha: Double,
    private val beta: Double,
    private val gamma: Double,
    private val periodDays: Int
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.HOLT_WINTERS_ADDITIVE

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "Holt-Winters forecast requires at least one observation" }
        require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }
        require(beta in 0.01..0.99) { "beta must be between 0.01 and 0.99" }
        require(gamma in 0.01..0.99) { "gamma must be between 0.01 and 0.99" }
        require(periodDays >= 2) { "seasonal period must be at least two periods" }

        if (history.size < periodDays * 2) {
            return HoltLinearTrendForecastModel(alpha, beta).forecast(history, horizonPeriods)
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

        return (1..horizonPeriods).map { step ->
            val seasonIndex = (values.size + step - 1) % periodDays
            (level + step * trend + seasonals[seasonIndex]).toQuantity()
        }
    }
}

class CrostonClassicForecastModel(private val alpha: Double) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.CROSTON_CLASSIC

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> =
        List(horizonPeriods) { crostonEstimate(history, alpha, correction = 1.0).toQuantity() }
}

class CrostonSbaForecastModel(private val alpha: Double) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.CROSTON_SBA

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> =
        List(horizonPeriods) { crostonEstimate(history, alpha, correction = 1.0 - alpha / 2.0).toQuantity() }
}

class TsbForecastModel(
    private val demandAlpha: Double,
    private val probabilityBeta: Double
) : DemandForecastModel {
    override val code: ForecastModelCode = ForecastModelCode.TSB

    override fun forecast(history: List<BigDecimal>, horizonPeriods: Int): List<BigDecimal> {
        require(history.isNotEmpty()) { "TSB forecast requires at least one observation" }
        require(demandAlpha in 0.01..0.99) { "demandAlpha must be between 0.01 and 0.99" }
        require(probabilityBeta in 0.01..0.99) { "probabilityBeta must be between 0.01 and 0.99" }

        val values = history.map { it.toDouble().coerceAtLeast(0.0) }
        val firstPositiveIndex = values.indexOfFirst { it > 0.0 }
        if (firstPositiveIndex < 0) return List(horizonPeriods) { BigDecimal.ZERO.setScale(6) }

        var demandEstimate = values[firstPositiveIndex]
        var demandProbability = 1.0 / (firstPositiveIndex + 1).toDouble()
        for (index in firstPositiveIndex + 1 until values.size) {
            val observation = values[index]
            val occurrence = if (observation > 0.0) 1.0 else 0.0
            demandProbability += probabilityBeta * (occurrence - demandProbability)
            if (observation > 0.0) {
                demandEstimate += demandAlpha * (observation - demandEstimate)
            }
        }
        val prediction = demandProbability.coerceIn(0.0, 1.0) * demandEstimate
        return List(horizonPeriods) { prediction.toQuantity() }
    }
}

private fun crostonEstimate(history: List<BigDecimal>, alpha: Double, correction: Double): Double {
    require(history.isNotEmpty()) { "Croston forecast requires at least one observation" }
    require(alpha in 0.01..0.99) { "alpha must be between 0.01 and 0.99" }

    val values = history.map { it.toDouble().coerceAtLeast(0.0) }
    val firstPositiveIndex = values.indexOfFirst { it > 0.0 }
    if (firstPositiveIndex < 0) return 0.0

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
    return if (intervalEstimate <= 0.0) 0.0 else correction * demandEstimate / intervalEstimate
}

data class PreprocessedDemand(
    val history: List<BigDecimal>,
    val demandPattern: DemandPattern,
    val zeroDemandRatio: BigDecimal,
    val outliersAdjusted: Int,
    val nonZeroObservations: Int,
    val averageDemandInterval: BigDecimal,
    val coefficientVariationSquared: BigDecimal
)

object DemandPreprocessor {
    private const val ADI_THRESHOLD = 1.32
    private const val CV2_THRESHOLD = 0.49

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

        val nonZeroCount = nonNegative.count { it.signum() > 0 }
        val zeroRatio = BigDecimal.valueOf((nonNegative.size - nonZeroCount).toDouble() / nonNegative.size)
            .setScale(6, RoundingMode.HALF_UP)
        val adi = if (nonZeroCount == 0) {
            BigDecimal.valueOf(nonNegative.size.toLong()).setScale(6)
        } else {
            BigDecimal.valueOf(nonNegative.size.toDouble() / nonZeroCount.toDouble())
                .setScale(6, RoundingMode.HALF_UP)
        }
        val cv2 = coefficientVariationSquared(nonNegative)

        return PreprocessedDemand(
            history = adjusted,
            demandPattern = classifyDemandPattern(adi.toDouble(), cv2.toDouble()),
            zeroDemandRatio = zeroRatio,
            outliersAdjusted = outlierCount,
            nonZeroObservations = nonZeroCount,
            averageDemandInterval = adi,
            coefficientVariationSquared = cv2
        )
    }

    fun classifyDemandPattern(history: List<BigDecimal>): DemandPattern {
        val profile = preprocess(history, treatOutliers = false)
        return profile.demandPattern
    }

    private fun classifyDemandPattern(adi: Double, cv2: Double): DemandPattern = when {
        adi < ADI_THRESHOLD && cv2 < CV2_THRESHOLD -> DemandPattern.SMOOTH
        adi < ADI_THRESHOLD && cv2 >= CV2_THRESHOLD -> DemandPattern.ERRATIC
        adi >= ADI_THRESHOLD && cv2 < CV2_THRESHOLD -> DemandPattern.INTERMITTENT
        else -> DemandPattern.LUMPY
    }

    private fun coefficientVariationSquared(history: List<BigDecimal>): BigDecimal {
        val positive = history.filter { it.signum() > 0 }.map(BigDecimal::toDouble)
        if (positive.isEmpty()) return BigDecimal.ZERO.setScale(6)
        val mean = positive.average()
        if (mean == 0.0) return BigDecimal.ZERO.setScale(6)
        val variance = positive.map { (it - mean).pow(2) }.average()
        return BigDecimal.valueOf(variance / mean.pow(2)).setScale(6, RoundingMode.HALF_UP)
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

object DemandAggregation {
    fun weekly(history: List<BigDecimal>): List<BigDecimal> {
        if (history.size < 7) return emptyList()
        val leadingPartial = history.size % 7
        val aligned = if (leadingPartial == 0) history else history.drop(leadingPartial)
        return aligned.chunked(7).map { week -> week.sumBigDecimal().setScale(6, RoundingMode.HALF_UP) }
    }

    fun dailyFromWeekly(weeklyForecast: List<BigDecimal>, horizonDays: Int): List<BigDecimal> {
        val requiredWeeks = ceil(horizonDays / 7.0).toInt()
        require(weeklyForecast.size >= requiredWeeks) { "Weekly forecast does not cover the requested daily horizon" }
        return weeklyForecast.take(requiredWeeks)
            .flatMap { weeklyQuantity ->
                val daily = weeklyQuantity.divide(BigDecimal("7"), 6, RoundingMode.HALF_UP).nonNegative()
                List(7) { daily }
            }
            .take(horizonDays)
    }
}

private fun Iterable<BigDecimal>.sumBigDecimal(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)
private fun BigDecimal.nonNegative(): BigDecimal = if (signum() < 0) BigDecimal.ZERO else this
private fun Double.toQuantity(): BigDecimal = BigDecimal.valueOf(coerceAtLeast(0.0)).setScale(6, RoundingMode.HALF_UP)
