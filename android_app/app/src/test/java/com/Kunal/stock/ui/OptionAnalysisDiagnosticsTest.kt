package com.Kunal.stock.ui

import com.Kunal.stock.data.ChartPoint
import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.OptionStrike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OptionAnalysisDiagnosticsTest {
    @Test
    fun reportsInputFreshnessCoverageAndNearAtmChainFlow() {
        val now = 1_800_000_000_000L
        val data = OptionSentimentData(
            spotPrice = 100.0,
            chainUpdatedAt = formatTimestamp(now - 90_000L),
            optionChain = listOf(
                OptionStrike(
                    strike = 99.0,
                    callOi = 100,
                    putOi = 120,
                    callOiChange = 10,
                    putOiChange = 20,
                    callVolume = 30,
                    putVolume = 40,
                    callBuild = "Long Buildup",
                    putBuild = "Short Buildup"
                ),
                OptionStrike(
                    strike = 101.0,
                    callOi = 80,
                    putOi = 90,
                    callOiChange = -2,
                    putOiChange = 3,
                    callVolume = 10,
                    putVolume = 15,
                    callBuild = "Long Buildup"
                ),
                OptionStrike(strike = 102.0, callOi = 500, putOi = 500),
                OptionStrike(strike = Double.NaN, callOi = 1)
            ),
            spotHistory = (0..7).map { minute ->
                ChartPoint(now - minute * 60_000L, 100.0 + minute)
            }
        )

        val result = OptionAnalysisDiagnosticsCalculator.calculate(data, true, now)

        assertTrue(result.chainFresh)
        assertEquals(90L, result.chainAgeSeconds)
        assertEquals(3, result.validChainRows)
        assertEquals(3, result.nearAtmRows)
        assertEquals(8, result.recentMinuteBuckets)
        assertEquals(8L, result.nearAtmFlow.callOiChange)
        assertEquals(23L, result.nearAtmFlow.putOiChange)
        assertEquals(40L, result.nearAtmFlow.callVolume)
        assertEquals(55L, result.nearAtmFlow.putVolume)
        assertEquals("Long Buildup ×2", result.nearAtmFlow.callBuildRead)
        assertEquals("Short Buildup ×1", result.nearAtmFlow.putBuildRead)
        assertEquals("LIVE INPUTS AVAILABLE", result.qualityRead)
    }

    @Test
    fun qualityReadCallsOutStaleInputsAndInsufficientHistory() {
        val now = 1_800_000_000_000L
        val data = OptionSentimentData(
            spotPrice = 100.0,
            chainUpdatedAt = formatTimestamp(now - 7 * 60_000L),
            optionChain = listOf(OptionStrike(strike = 100.0, callOi = 1))
        )

        val staleSpot = OptionAnalysisDiagnosticsCalculator.calculate(data, false, now)
        val staleChain = OptionAnalysisDiagnosticsCalculator.calculate(data, true, now)

        assertEquals("STALE SPOT QUOTE", staleSpot.qualityRead)
        assertFalse(staleChain.chainFresh)
        assertEquals("STALE OR UNAVAILABLE OPTION CHAIN", staleChain.qualityRead)

        val freshChain = data.copy(chainUpdatedAt = formatTimestamp(now))
        val limitedCoverage = OptionAnalysisDiagnosticsCalculator.calculate(freshChain, true, now)
        assertEquals("LIMITED NEAR-ATM CHAIN COVERAGE", limitedCoverage.qualityRead)

        val adequateCoverage = freshChain.copy(
            optionChain = listOf(
                OptionStrike(strike = 99.0, callOi = 10),
                OptionStrike(strike = 100.0, callOi = 10),
                OptionStrike(strike = 101.0, callOi = 10)
            )
        )
        val missingHistory = OptionAnalysisDiagnosticsCalculator.calculate(adequateCoverage, true, now)
        assertEquals("COLLECTING RECENT PRICE SAMPLES", missingHistory.qualityRead)
    }

    private fun formatTimestamp(value: Long) =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(value))
}
