package com.Kunal.stock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlySetupRadarTest {
    @Test
    fun findsRisingStockNearPivotBeforeCloseBreaksOut() {
        val candidate = EarlySetupRadar.evaluate("TEST.NS", "Technology", reversalCandles())

        assertNotNull(candidate)
        assertEquals("PRE-BREAKOUT WATCH", candidate?.stage)
        assertTrue(candidate!!.score >= 40)
        assertTrue(candidate.distanceToPivotPct in 0.0..6.0)
        assertTrue(candidate.reasons.any { it.contains("higher", ignoreCase = true) })
    }

    @Test
    fun excludesAlreadyConfirmedCloseAbovePriorPivot() {
        val candles = reversalCandles().toMutableList()
        val last = candles.last()
        candles[candles.lastIndex] = last.copy(
            close = last.high + 2.0,
            high = last.high + 2.5
        )

        assertNull(EarlySetupRadar.evaluate("TEST.NS", "Technology", candles))
    }

    @Test
    fun identifiesShortTermNiftyRecoveryWhileStillBelowLongTermAverage() {
        val closes = buildList {
            repeat(180) { index -> add(100.0 + index * 0.05) }
            repeat(20) { index -> add(109.0 - index * 0.95) }
            repeat(10) { index -> add(90.0 + index * 1.5) }
        }

        val context = EarlySetupRadar.marketTrend(closes)

        assertNotNull(context)
        assertEquals("BEARISH REGIME · REVERSAL WATCH", context?.regime)
        assertTrue(context!!.change5dPct > 0.0)
        assertTrue(!context.above200)
    }

    @Test
    fun requiresEnoughHistoryForMarketRegime() {
        assertNull(EarlySetupRadar.marketTrend((1..100).map { it.toDouble() }))
    }

    private fun reversalCandles(): List<StockCandle> {
        val closes = mutableListOf<Double>()
        repeat(25) { index -> closes += 120.0 - index * 1.1 }
        closes += listOf(93.0, 92.6, 93.1, 92.7, 93.2, 92.8, 93.4, 93.0, 93.6, 93.2)
        closes += listOf(92.8, 92.9, 93.1, 93.4, 93.7, 94.0, 94.2, 94.5, 94.7, 94.8)
        return closes.mapIndexed { index, close ->
            val baseVolume = if (index >= closes.size - 5) 1_300L else 1_000L
            StockCandle(
                time = index.toLong(),
                open = close - 0.1,
                high = close + 0.7,
                low = close - 0.5,
                close = close,
                volume = baseVolume
            )
        }
    }
}
