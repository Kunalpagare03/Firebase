package com.Kunal.stock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockScanReturnCalculatorTest {
    private val signalDay = 1_704_067_200_000L
    private val oneDay = 86_400_000L

    @Test
    fun recordsTargetHitAndTradingSessionCount() {
        val result = StockScanReturnCalculator.evaluate(
            StockPerformanceSignal(signalDay, 100.0, 90.0, 110.0, true),
            listOf(
                candle(1, open = 100.0, high = 105.0, low = 99.0, close = 104.0),
                candle(2, open = 104.0, high = 108.0, low = 103.0, close = 107.0),
                candle(3, open = 109.0, high = 111.0, low = 106.0, close = 110.0)
            )
        )

        assertEquals(StockReturnOutcome.TARGET_HIT, result.first().outcome)
        assertEquals(3, result.first().eventDay)
        assertEquals(10.0, result.first().returnPct!!, 0.0001)
    }

    @Test
    fun resolvesSameCandleTargetAndStopConservativelyAsStop() {
        val result = StockScanReturnCalculator.evaluate(
            StockPerformanceSignal(signalDay, 100.0, 90.0, 110.0, true),
            listOf(candle(1, open = 100.0, high = 111.0, low = 89.0, close = 105.0))
        )

        assertEquals(StockReturnOutcome.STOP_HIT, result.first().outcome)
        assertEquals(1, result.first().eventDay)
        assertEquals(-10.0, result.first().returnPct!!, 0.0001)
    }

    @Test
    fun usesGapThroughStopAsTheEstimatedExitPrice() {
        val result = StockScanReturnCalculator.evaluate(
            StockPerformanceSignal(signalDay, 100.0, 90.0, 0.0, true),
            listOf(candle(1, open = 87.0, high = 92.0, low = 85.0, close = 88.0))
        )

        assertEquals(87.0, result.first().exitPrice!!, 0.0001)
        assertEquals(-13.0, result.first().returnPct!!, 0.0001)
    }

    @Test
    fun reportsCompletedSwingAndStillTrackingLongerHorizons() {
        val candles = (1..10).map { day ->
            candle(day, open = 100.0, high = 105.0, low = 99.0, close = 100.0 + day)
        }
        val result = StockScanReturnCalculator.evaluate(
            StockPerformanceSignal(signalDay, 100.0, 0.0, 0.0, false),
            candles
        )

        assertEquals(StockReturnOutcome.HORIZON_COMPLETE, result[0].outcome)
        assertEquals(10.0, result[0].returnPct!!, 0.0001)
        assertEquals(StockReturnOutcome.TRACKING, result[1].outcome)
        assertEquals(10, result[1].observedSessions)
        assertEquals(StockReturnOutcome.TRACKING, result[2].outcome)
        assertNull(result[2].eventDay)
    }

    private fun candle(day: Int, open: Double, high: Double, low: Double, close: Double) =
        StockCandle(
            time = signalDay + oneDay * day,
            open = open,
            high = high,
            low = low,
            close = close
        )
}
