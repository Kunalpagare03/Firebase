package com.Kunal.stock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionSignalConsensusTest {
    @Test
    fun alignedBullishEvidenceProducesLiveCallFromNearAtmChain() {
        val now = System.currentTimeMillis()
        val data = marketData(now, risingPrices(), bullishChain())

        val consensus = OptionSignalConsensus.evaluate(data, true, true, now)
        val signal = OptionSignalConsensus.buildLiveSignal(data, consensus)

        assertTrue(consensus.tradeEligible)
        assertEquals("BULLISH", consensus.direction)
        assertEquals("BUY CALL", signal.action)
        assertEquals("CE", signal.optionType)
        assertEquals(25_300.0, signal.strike, 0.0)
        assertEquals(120.0, signal.estimatedPremium, 0.0)
        assertTrue(signal.targetSpot > data.spotPrice)
        assertTrue(signal.stopLossSpot < data.spotPrice)
        assertTrue(signal.targetPremium > signal.estimatedPremium)
        assertTrue(signal.stopLossPremium < signal.estimatedPremium)
    }

    @Test
    fun alignedBearishEvidenceProducesLivePut() {
        val now = System.currentTimeMillis()
        val data = marketData(now, fallingPrices(), bearishChain()).copy(
            supportZones = listOf(25_290.0),
            resistanceZones = listOf(25_330.0)
        )

        val consensus = OptionSignalConsensus.evaluate(data, true, true, now)
        val signal = OptionSignalConsensus.buildLiveSignal(data, consensus)

        assertTrue(consensus.tradeEligible)
        assertEquals("BEARISH", consensus.direction)
        assertEquals("BUY PUT", signal.action)
        assertEquals("PE", signal.optionType)
        assertTrue(signal.targetSpot < data.spotPrice)
        assertTrue(signal.stopLossSpot > data.spotPrice)
    }

    @Test
    fun blocksSignalWhenPriceAndOptionFlowDisagree() {
        val now = System.currentTimeMillis()
        val data = marketData(now, risingPrices(), bearishChain())

        val consensus = OptionSignalConsensus.evaluate(data, true, true, now)
        val signal = OptionSignalConsensus.buildLiveSignal(data, consensus)

        assertFalse(consensus.tradeEligible)
        assertEquals("WAIT", signal.action)
        assertTrue(consensus.blocker.contains("do not agree") ||
            consensus.blocker.contains("opposes"))
    }

    @Test
    fun requiresFreshUnderlyingAndOptionChainBeforeIssuingTrade() {
        val now = System.currentTimeMillis()
        val data = marketData(now, risingPrices(), bullishChain())

        assertFalse(OptionSignalConsensus.evaluate(data, false, true, now).tradeEligible)
        val staleChain = OptionSignalConsensus.evaluate(data, true, false, now)
        assertFalse(staleChain.tradeEligible)
        assertEquals(0.0, staleChain.optionFlowScore, 0.0)
        assertTrue(staleChain.trendLabel.contains("CHAIN STALE"))
    }

    @Test
    fun identifiesEarlyBullishAndBearishReversalWatchesBeforeConfirmation() {
        val now = System.currentTimeMillis()
        val bullishTurn = marketData(now, fallingThenRisingPrices(), bullishChain())
            .copy(supportZones = listOf(25_300.0))
        val bearishTurn = marketData(now, risingThenFallingPrices(), bearishChain())
            .copy(resistanceZones = listOf(25_300.0))

        val bullishWatch = OptionSignalConsensus.evaluate(bullishTurn, true, true, now)
        val bearishWatch = OptionSignalConsensus.evaluate(bearishTurn, true, true, now)

        assertEquals("EARLY BULLISH REVERSAL WATCH", bullishWatch.earlyReversalWatch)
        assertEquals("EARLY BEARISH REVERSAL WATCH", bearishWatch.earlyReversalWatch)
        assertFalse(bullishWatch.tradeEligible)
        assertFalse(bearishWatch.tradeEligible)
        assertTrue(bullishWatch.blocker.contains("reversal risk"))
        assertTrue(bearishWatch.blocker.contains("reversal risk"))
    }

    @Test
    fun refusesToRecommendWhenTheDirectionHasNoLivePremium() {
        val now = System.currentTimeMillis()
        val data = marketData(now, risingPrices(), bullishChain()).copy(
            optionChain = bullishChain().map { it.copy(callLtp = 0.0) }
        )

        val consensus = OptionSignalConsensus.evaluate(data, true, true, now)
        val signal = OptionSignalConsensus.buildLiveSignal(data, consensus)

        assertFalse(consensus.tradeEligible)
        assertEquals("WAIT", signal.action)
        assertTrue(consensus.blocker.contains("premium"))
    }

    @Test
    fun trendQualityPenalizesChoppyPathsWithTheSameRecentDirection() {
        val now = System.currentTimeMillis()
        val smooth = (0..30).map { 25_000.0 + it * 5.0 }
        val choppy = smooth.mapIndexed { index, price ->
            if (index in 8..24) price + if (index % 2 == 0) 15.0 else -15.0 else price
        }

        val smoothConsensus = OptionSignalConsensus.evaluate(
            marketData(now, smooth, bullishChain()), true, true, now
        )
        val choppyConsensus = OptionSignalConsensus.evaluate(
            marketData(now, choppy, bullishChain()), true, true, now
        )

        assertTrue(smoothConsensus.trendEfficiency > choppyConsensus.trendEfficiency)
        assertTrue(smoothConsensus.regressionFit > choppyConsensus.regressionFit)
        assertTrue(smoothConsensus.priceActionScore > choppyConsensus.priceActionScore)
        assertTrue(smoothConsensus.evidence.any { it.contains("Trend persistence") })
    }

    @Test
    fun reportsLongerHorizonAsUnavailableUntilEnoughHistoryExists() {
        val now = System.currentTimeMillis()
        val data = marketData(now, (0..8).map { 25_200.0 + it * 5.0 }, bullishChain())

        val consensus = OptionSignalConsensus.evaluate(data, true, true, now)

        assertTrue(consensus.evidence.any { it.contains("15m unavailable") })
    }

    private fun marketData(
        now: Long,
        prices: List<Double>,
        chain: List<OptionStrike>
    ) = OptionSentimentData(
        symbol = "NIFTY",
        spotPrice = prices.last(),
        finalSignal = "Bearish",
        chartTrend = if (prices.last() >= prices.first()) "Bullish" else "Bearish",
        trendline = if (prices.last() >= prices.first()) "Bullish" else "Bearish",
        chartPattern = if (prices.last() >= prices.first()) "Bullish continuation" else "Bearish continuation",
        supportZones = listOf(prices.last() - 10.0),
        resistanceZones = listOf(prices.last() + 200.0),
        pcrOi = if (chain.first().callBuild == "Long Buildup") 1.3 else 0.7,
        pcrVolume = if (chain.first().callBuild == "Long Buildup") 1.2 else 0.8,
        oiDirectionalRead = if (chain.first().callBuild == "Long Buildup") "Leaning Bullish" else "Leaning Bearish",
        optionChain = chain,
        spotHistory = prices.mapIndexed { index, price ->
            ChartPoint(now - (prices.lastIndex - index) * 60_000L + 30_000L, price)
        }
    )

    private fun bullishChain() = listOf(
        OptionStrike(
            strike = 25_300.0,
            callOi = 10_000,
            putOi = 12_000,
            totalOi = 22_000,
            callOiChange = 600,
            putOiChange = 500,
            callVolume = 3_000,
            putVolume = 2_500,
            callLtp = 120.0,
            putLtp = 110.0,
            callBuild = "Long Buildup",
            putBuild = "Short Buildup"
        )
    )

    private fun bearishChain() = listOf(
        OptionStrike(
            strike = 25_300.0,
            callOi = 12_000,
            putOi = 10_000,
            totalOi = 22_000,
            callOiChange = 600,
            putOiChange = 500,
            callVolume = 2_500,
            putVolume = 3_000,
            callLtp = 110.0,
            putLtp = 120.0,
            callBuild = "Short Buildup",
            putBuild = "Long Buildup"
        )
    )

    private fun risingPrices(): List<Double> =
        (0..20).map { 25_200.0 + it * 5.0 }

    private fun fallingPrices(): List<Double> =
        (0..20).map { 25_400.0 - it * 5.0 }

    private fun fallingThenRisingPrices(): List<Double> =
        (0..25).map { 25_500.0 - it * 8.0 } +
            (1..5).map { 25_300.0 + it * 8.0 }

    private fun risingThenFallingPrices(): List<Double> =
        (0..25).map { 25_100.0 + it * 8.0 } +
            (1..5).map { 25_300.0 - it * 8.0 }
}
