package com.stockflow.forecasting.domain

import com.stockflow.forecasting.persistence.DemandPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ForecastingModelsTest {
    @Test
    fun `holt trend extends an increasing series`() {
        val history = (1..30).map { BigDecimal.valueOf(it * 10L) }
        val forecast = HoltLinearTrendForecastModel(alpha = 0.4, beta = 0.3)
            .forecast(history, 7)

        assertEquals(7, forecast.size)
        assertTrue(forecast.first() > history.last())
        assertTrue(forecast.last() > forecast.first())
    }

    @Test
    fun `holt winters preserves a weekly additive pattern`() {
        val week = listOf(100, 110, 120, 130, 140, 90, 80)
        val history = (0 until 8).flatMap { cycle ->
            week.map { BigDecimal.valueOf((it + cycle * 2).toLong()) }
        }
        val forecast = HoltWintersAdditiveForecastModel(
            alpha = 0.3,
            beta = 0.2,
            gamma = 0.2,
            periodDays = 7
        ).forecast(history, 7)

        assertEquals(7, forecast.size)
        assertTrue(forecast.all { it.signum() >= 0 })
        assertTrue(forecast[4] > forecast[6])
    }

    @Test
    fun `croston produces a stable nonzero forecast for intermittent demand`() {
        val history = List(60) { index ->
            if (index % 8 == 0) BigDecimal("40") else BigDecimal.ZERO
        }
        val forecast = CrostonSbaForecastModel(alpha = 0.2).forecast(history, 14)

        assertEquals(14, forecast.size)
        assertTrue(forecast.first() > BigDecimal.ZERO)
        assertTrue(forecast.distinct().size == 1)
    }

    @Test
    fun `preprocessor classifies intermittent demand and adjusts extreme outlier`() {
        val history = MutableList(60) { index ->
            if (index % 5 == 0) BigDecimal("20") else BigDecimal.ZERO
        }
        history[55] = BigDecimal("2000")

        val result = DemandPreprocessor.preprocess(history, treatOutliers = true)

        assertTrue(result.demandPattern in setOf(DemandPattern.INTERMITTENT, DemandPattern.LUMPY))
        assertTrue(result.zeroDemandRatio > BigDecimal("0.70"))
        assertTrue(result.outliersAdjusted >= 1)
        assertTrue(result.history[55] < BigDecimal("2000"))
    }

    @Test
    fun `croston classic and tsb produce positive intermittent forecasts`() {
        val history = List(84) { index ->
            if (index % 9 == 0) BigDecimal("45") else BigDecimal.ZERO
        }

        val classic = CrostonClassicForecastModel(alpha = 0.2).forecast(history, 7)
        val tsb = TsbForecastModel(demandAlpha = 0.2, probabilityBeta = 0.1).forecast(history, 7)

        assertEquals(7, classic.size)
        assertEquals(7, tsb.size)
        assertTrue(classic.all { it > BigDecimal.ZERO })
        assertTrue(tsb.all { it > BigDecimal.ZERO })
    }

    @Test
    fun `preprocessor exposes adi and cv squared`() {
        val history = List(60) { index ->
            if (index % 5 == 0) BigDecimal.valueOf((20 + index).toLong()) else BigDecimal.ZERO
        }

        val result = DemandPreprocessor.preprocess(history, treatOutliers = false)

        assertEquals(12, result.nonZeroObservations)
        assertTrue(result.averageDemandInterval >= BigDecimal("5.0"))
        assertTrue(result.coefficientVariationSquared >= BigDecimal.ZERO)
    }

    @Test
    fun `weekly aggregation preserves total demand and disaggregates forecast`() {
        val history = (1..14).map { BigDecimal.ONE }
        val weekly = DemandAggregation.weekly(history)
        val daily = DemandAggregation.dailyFromWeekly(listOf(BigDecimal("70")), 7)

        assertEquals(listOf(BigDecimal("7.000000"), BigDecimal("7.000000")), weekly)
        assertEquals(7, daily.size)
        assertTrue(daily.all { it == BigDecimal("10.000000") })
    }

}
