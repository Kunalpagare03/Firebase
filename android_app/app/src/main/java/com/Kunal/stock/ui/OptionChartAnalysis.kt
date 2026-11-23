package com.Kunal.stock.ui

internal enum class OptionPriceDirection {
    BULLISH,
    BEARISH,
    SIDEWAYS,
    UNAVAILABLE
}

internal data class OptionTimeframeRead(
    val timeframe: String,
    val direction: OptionPriceDirection,
    val changePct: Double,
    val candleCount: Int
)

internal data class BtstAssessment(
    val isConditionalSetup: Boolean,
    val direction: OptionPriceDirection,
    val reason: String
)

internal object OptionChartAnalysis {
    private val requiredTimeframes = listOf("1D", "1h", "30m", "15m", "5m")

    fun readTimeframe(
        timeframe: String,
        candles: List<Pair<Double, Double>>
    ): OptionTimeframeRead {
        val valid = candles.filter { (open, close) ->
            open.isFinite() && close.isFinite() && open > 0.0 && close > 0.0
        }.takeLast(12)
        if (valid.size < 3) {
            return OptionTimeframeRead(timeframe, OptionPriceDirection.UNAVAILABLE, 0.0, valid.size)
        }

        val changePct = (valid.last().second / valid.first().first - 1.0) * 100.0
        val direction = when {
            changePct >= 0.15 -> OptionPriceDirection.BULLISH
            changePct <= -0.15 -> OptionPriceDirection.BEARISH
            else -> OptionPriceDirection.SIDEWAYS
        }
        return OptionTimeframeRead(timeframe, direction, changePct, valid.size)
    }

    fun assessBtst(
        reads: List<OptionTimeframeRead>,
        optionType: String,
        actualOptionPremium: Double,
        spotQuoteFresh: Boolean,
        optionChainFresh: Boolean
    ): BtstAssessment {
        if (!spotQuoteFresh) {
            return BtstAssessment(false, OptionPriceDirection.UNAVAILABLE, "Spot quote is stale or unavailable.")
        }
        if (!optionChainFresh) {
            return BtstAssessment(false, OptionPriceDirection.UNAVAILABLE, "Option-chain quote is stale or unavailable.")
        }
        if (!actualOptionPremium.isFinite() || actualOptionPremium <= 0.0) {
            return BtstAssessment(false, OptionPriceDirection.UNAVAILABLE, "A live option-chain premium is required.")
        }

        val byTimeframe = reads.associateBy { it.timeframe }
        val requiredReads = requiredTimeframes.mapNotNull(byTimeframe::get)
        if (requiredReads.size != requiredTimeframes.size ||
            requiredReads.any { it.direction == OptionPriceDirection.UNAVAILABLE }
        ) {
            return BtstAssessment(false, OptionPriceDirection.UNAVAILABLE, "Waiting for at least 3 sampled candles on every requested timeframe.")
        }

        val direction = requiredReads.first().direction
        if (direction == OptionPriceDirection.SIDEWAYS ||
            requiredReads.any { it.direction != direction }
        ) {
            return BtstAssessment(false, direction, "The 1D/1h/30m/15m/5m trend is not aligned.")
        }

        val contractMatches = when (direction) {
            OptionPriceDirection.BULLISH -> optionType.contains("CE", ignoreCase = true) ||
                optionType.contains("CALL", ignoreCase = true)
            OptionPriceDirection.BEARISH -> optionType.contains("PE", ignoreCase = true) ||
                optionType.contains("PUT", ignoreCase = true)
            else -> false
        }
        if (!contractMatches) {
            return BtstAssessment(false, direction, "The suggested option side does not match the multi-timeframe direction.")
        }

        return BtstAssessment(
            true,
            direction,
            "Conditional watch only. Overnight gap, news and expiry risk are not modeled."
        )
    }
}
