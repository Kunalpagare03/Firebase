package com.Kunal.stock.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionChartAnalysisTest {
    @Test
    fun timeframeReadRequiresEnoughValidCandles() {
        val read = OptionChartAnalysis.readTimeframe(
            "5m",
            listOf(100.0 to 101.0, 101.0 to 102.0)
        )

        assertEquals(OptionPriceDirection.UNAVAILABLE, read.direction)
        assertEquals(2, read.candleCount)
    }

    @Test
    fun timeframeReadClassifiesMoveFromRecentCandles() {
        val read = OptionChartAnalysis.readTimeframe(
            "1h",
            listOf(100.0 to 100.2, 100.2 to 100.4, 100.4 to 101.0)
        )

        assertEquals(OptionPriceDirection.BULLISH, read.direction)
        assertTrue(read.changePct > 0.15)
    }

    @Test
    fun btstWaitsWhenTimeframesConflict() {
        val reads = alignedReads().toMutableList().apply {
            this[3] = this[3].copy(direction = OptionPriceDirection.BEARISH)
        }

        val assessment = OptionChartAnalysis.assessBtst(reads, "CE", 120.0, true, true)

        assertFalse(assessment.isConditionalSetup)
        assertTrue(assessment.reason.contains("not aligned"))
    }

    @Test
    fun btstRequiresFreshSpotQuoteAndLiveOptionPremium() {
        val reads = alignedReads()

        assertFalse(OptionChartAnalysis.assessBtst(reads, "CE", 120.0, false, true).isConditionalSetup)
        assertFalse(OptionChartAnalysis.assessBtst(reads, "CE", 120.0, true, false).isConditionalSetup)
        assertFalse(OptionChartAnalysis.assessBtst(reads, "CE", 0.0, true, true).isConditionalSetup)
    }

    @Test
    fun btstRequiresMatchingOptionSideAndAllTimeframes() {
        val reads = alignedReads()

        assertFalse(OptionChartAnalysis.assessBtst(reads, "PE", 120.0, true, true).isConditionalSetup)
        assertFalse(OptionChartAnalysis.assessBtst(reads.dropLast(1), "CE", 120.0, true, true).isConditionalSetup)
        assertTrue(OptionChartAnalysis.assessBtst(reads, "CE", 120.0, true, true).isConditionalSetup)
    }

    private fun alignedReads() = listOf("1D", "1h", "30m", "15m", "5m").map {
        OptionTimeframeRead(it, OptionPriceDirection.BULLISH, 0.5, 5)
    }
}
