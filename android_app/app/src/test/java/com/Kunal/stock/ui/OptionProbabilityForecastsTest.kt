package com.Kunal.stock.ui

import com.Kunal.stock.data.MultiHorizonProbabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionProbabilityForecastsTest {
    @Test
    fun showsOnlyValidPublisherDistributionsAndPreservesAllThreeOutcomes() {
        val forecasts = PublishedHorizonForecasts.validForecasts(
            MultiHorizonProbabilities(
                horizon1CandleBullish = 30.0,
                horizon1CandleBearish = 55.0,
                horizon1CandleSideways = 15.0,
                horizon3CandleBullish = 20.0,
                horizon3CandleBearish = 60.0,
                horizon3CandleSideways = 20.0
            )
        )

        assertEquals(2, forecasts.size)
        assertEquals(55.0, forecasts.first().bearishPct, 0.0)
        assertEquals(15.0, forecasts.first().sidewaysPct, 0.0)
    }

    @Test
    fun missingOrMalformedForecastsRemainUnavailable() {
        val empty = PublishedHorizonForecasts.validForecasts(MultiHorizonProbabilities())
        val malformed = PublishedHorizonForecasts.validForecasts(
            MultiHorizonProbabilities(
                horizon1CandleBullish = 70.0,
                horizon1CandleBearish = 20.0,
                horizon1CandleSideways = 0.0
            )
        )

        assertTrue(empty.isEmpty())
        assertTrue(malformed.isEmpty())
    }
}
