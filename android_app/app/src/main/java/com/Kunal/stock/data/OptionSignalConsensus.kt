package com.Kunal.stock.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

data class OptionDirectionalConsensus(
    val trendLabel: String = "UNAVAILABLE",
    val earlyReversalWatch: String = "NONE",
    val direction: String = "WAIT",
    val compositeScore: Double = 0.0,
    val priceActionScore: Double = 0.0,
    val trendEfficiency: Double = 0.0,
    val regressionFit: Double = 0.0,
    val structureScore: Double = 0.0,
    val supportResistanceScore: Double = 0.0,
    val optionFlowScore: Double = 0.0,
    val reversalScore: Double = 0.0,
    val evidenceStrength: Int = 0,
    val tradeEligible: Boolean = false,
    val blocker: String = "Waiting for live market evidence.",
    val evidence: List<String> = emptyList()
)

object OptionSignalConsensus {
    private data class MinuteBar(
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double
    )

    private data class PriceFeatures(
        val momentum5: Double,
        val momentum15: Double,
        val acceleration: Double,
        val rsiTurn: Double,
        val macdScore: Double,
        val slopeScore: Double,
        val efficiency5: Double,
        val efficiency15: Double?,
        val regressionFit: Double,
        val atrPct: Double,
        val bars: List<MinuteBar>,
        val rsi: Double
    )

    fun evaluate(
        data: OptionSentimentData,
        spotQuoteFresh: Boolean,
        optionChainFresh: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): OptionDirectionalConsensus {
        val bars = minuteBars(data.spotHistory, nowMillis)
        val price = priceFeatures(bars)
        val momentumAvailable = price != null && bars.size >= 6
        val momentum5 = price?.momentum5 ?: 0.0
        val momentum15 = price?.momentum15 ?: 0.0
        val trendEfficiency = price?.let {
            0.65 * it.efficiency5 + 0.35 * (it.efficiency15 ?: it.efficiency5)
        } ?: 0.0
        val priceActionScore = if (price == null) 0.0 else {
            val emaScore = emaTrendScore(price.bars.map { it.close }, price.atrPct)
            val rsiScore = ((price.rsi - 50.0) / 22.0).coerceIn(-1.0, 1.0)
            (
                0.30 * momentum5 +
                    0.16 * momentum15 +
                    0.12 * emaScore +
                    0.10 * price.macdScore +
                    0.08 * price.slopeScore +
                    0.08 * rsiScore +
                    0.16 * trendEfficiency
                )
                .coerceIn(-1.0, 1.0)
        }
        val structureScore = (
            0.42 * directionTextScore(data.chartTrend) +
                0.33 * directionTextScore(data.trendline) +
                0.25 * patternScore(data.chartPattern)
            ).coerceIn(-1.0, 1.0)
        val levelScore = supportResistanceScore(data, momentum5, bars)
        val observedOptionFlowScore = optionFlowScore(data)
        val optionFlowScore = observedOptionFlowScore.takeIf { optionChainFresh } ?: 0.0
        val compositeScore = (
            0.34 * priceActionScore +
                0.23 * structureScore +
                0.19 * levelScore +
                0.24 * optionFlowScore
            ).coerceIn(-1.0, 1.0)

        val rsiTurn = price?.rsiTurn ?: 0.0
        val reversalScore = (
            0.32 * (price?.acceleration.orZero()) +
                0.22 * rsiTurn +
                0.18 * price?.macdScore.orZero() +
                0.18 * levelScore +
                0.10 * optionFlowScore
            ).coerceIn(-1.0, 1.0)
        val broaderTrend = 0.60 * momentum15 + 0.40 * structureScore
        val earlyReversal = when {
            !spotQuoteFresh || !momentumAvailable -> "NONE"
            broaderTrend >= 0.18 && reversalScore <= -0.34 -> "EARLY BEARISH REVERSAL WATCH"
            broaderTrend <= -0.18 && reversalScore >= 0.34 -> "EARLY BULLISH REVERSAL WATCH"
            else -> "NONE"
        }
        val direction = when {
            compositeScore >= 0.24 -> "BULLISH"
            compositeScore <= -0.24 -> "BEARISH"
            else -> "MIXED"
        }
        val trendLabel = when {
            !spotQuoteFresh -> "STALE · WAITING FOR LIVE PRICE"
            !optionChainFresh -> "WAIT · OPTION CHAIN STALE"
            !momentumAvailable -> "WAIT · COLLECTING PRICE HISTORY"
            earlyReversal != "NONE" -> "$earlyReversal · NOT CONFIRMED"
            direction == "BULLISH" -> "BULLISH · CONFLUENCE"
            direction == "BEARISH" -> "BEARISH · CONFLUENCE"
            else -> "MIXED / TRANSITION"
        }

        val hasChainEvidence = hasDirectionalChainEvidence(data)
        val priceAligned = abs(priceActionScore) >= 0.18 &&
            sign(priceActionScore) == sign(optionFlowScore)
        val structureAligned = abs(structureScore) >= 0.12 &&
            sign(structureScore) == sign(compositeScore)
        val levelsAligned = abs(levelScore) >= 0.18 &&
            sign(levelScore) == sign(compositeScore)
        val levelsOrStructureConfirm = structureAligned || levelsAligned
        val strongEvidenceConflict = listOf(
            priceActionScore to 0.45,
            structureScore to 0.30,
            levelScore to 0.30,
            optionFlowScore to 0.45
        ).any { (score, minimumStrength) ->
            abs(score) >= minimumStrength && sign(score) != sign(compositeScore)
        }
        val chainPremiumAvailable = when (direction) {
            "BULLISH" -> data.optionChain.any { row ->
                row.strike > 0.0 && row.strike.isFinite() &&
                    abs(row.strike - data.spotPrice) / data.spotPrice <= 0.01 &&
                    row.callLtp.isFinite() && row.callLtp > 0.0
            }
            "BEARISH" -> data.optionChain.any { row ->
                row.strike > 0.0 && row.strike.isFinite() &&
                    abs(row.strike - data.spotPrice) / data.spotPrice <= 0.01 &&
                    row.putLtp.isFinite() && row.putLtp > 0.0
            }
            else -> false
        }
        val evidenceGroups = listOf(
            abs(priceActionScore) >= 0.18,
            abs(structureScore) >= 0.12 || abs(levelScore) >= 0.18,
            abs(optionFlowScore) >= 0.15
        ).count { it }

        val blocker = when {
            !spotQuoteFresh -> "WAIT: Angel One live underlying quote is stale or unavailable."
            !optionChainFresh -> "WAIT: option-chain snapshot is stale or unavailable."
            !momentumAvailable -> "WAIT: collect at least six recent one-minute price bars."
            !hasChainEvidence -> "WAIT: option-chain positioning evidence is incomplete."
            earlyReversal != "NONE" -> "WAIT: reversal risk is emerging; wait for price and chain confirmation."
            direction == "MIXED" -> "WAIT: combined price, structure, levels and chain score is not directional."
            !priceAligned -> "WAIT: price momentum and option-chain flow do not agree."
            strongEvidenceConflict -> "WAIT: a major price, structure, level or option-flow input opposes the combined bias."
            !levelsOrStructureConfirm -> "WAIT: trendline/chart structure or support/resistance does not confirm."
            evidenceGroups < 3 -> "WAIT: fewer than three independent evidence groups confirm."
            !chainPremiumAvailable -> "WAIT: no near-ATM option premium is available from the chain."
            abs(compositeScore) < 0.30 -> "WAIT: confluence score is below the trade threshold."
            else -> ""
        }
        val tradeEligible = blocker.isEmpty()
        val evidence = buildList {
            if (price != null) {
                add(
                    "Price: 5m ${formatReturnPct(price.bars, 5)}, " +
                        "15m ${formatReturnPct(price.bars, 15)}; " +
                        "EMA/MACD/slope/RSI composite ${formatScore(priceActionScore)}"
                )
                add(
                    "Trend persistence: ER5 ${formatScore(price.efficiency5)}, " +
                        "ER15 ${price.efficiency15?.let(::formatScore) ?: "unavailable"}; " +
                        "log-price regression R² ${formatScore(price.regressionFit)}"
                )
            } else {
                add("Price momentum unavailable: insufficient recent one-minute bars")
            }
            add("Chart/trendline/pattern score ${formatScore(structureScore)} (${data.chartTrend} · ${data.trendline} · ${data.chartPattern})")
            add("Support/resistance score ${formatScore(levelScore)}")
            add("Early reversal score ${formatScore(reversalScore)}")
            add(if (optionChainFresh) {
                "Option-chain flow score ${formatScore(optionFlowScore)} · PCR OI ${formatMetric(data.pcrOi)}"
            } else {
                "Option-chain flow ignored because the chain snapshot is stale"
            })
            if (earlyReversal != "NONE") {
                add("$earlyReversal · reversal score ${formatScore(reversalScore)}")
            }
        }
        return OptionDirectionalConsensus(
            trendLabel = trendLabel,
            earlyReversalWatch = earlyReversal,
            direction = direction,
            compositeScore = compositeScore,
            priceActionScore = priceActionScore,
            trendEfficiency = trendEfficiency,
            regressionFit = price?.regressionFit ?: 0.0,
            structureScore = structureScore,
            supportResistanceScore = levelScore,
            optionFlowScore = optionFlowScore,
            reversalScore = reversalScore,
            evidenceStrength = (abs(compositeScore) * 100.0).toInt().coerceIn(0, 100),
            tradeEligible = tradeEligible,
            blocker = blocker,
            evidence = evidence
        )
    }

    fun buildLiveSignal(
        data: OptionSentimentData,
        consensus: OptionDirectionalConsensus
    ): LiveTradeSignal {
        if (!consensus.tradeEligible || data.spotPrice <= 0.0) return LiveTradeSignal()
        val optionType = if (consensus.direction == "BULLISH") "CE" else "PE"
        val optionRow = data.optionChain
            .filter { row ->
                row.strike > 0.0 && row.strike.isFinite() &&
                    abs(row.strike - data.spotPrice) / data.spotPrice <= 0.01 &&
                    (if (optionType == "CE") row.callLtp else row.putLtp) > 0.0
            }
            .minByOrNull { abs(it.strike - data.spotPrice) }
            ?: return LiveTradeSignal()
        val entryPremium = if (optionType == "CE") optionRow.callLtp else optionRow.putLtp
        if (!entryPremium.isFinite() || entryPremium <= 0.0) return LiveTradeSignal()

        val price = priceFeatures(minuteBars(data.spotHistory, System.currentTimeMillis()))
        val atrMove = max(data.spotPrice * (price?.atrPct ?: 0.003) * 1.5, data.spotPrice * 0.0015)
        val support = data.supportZones.filter { it.isFinite() && it > 0.0 && it < data.spotPrice }
            .maxOrNull()
        val resistance = data.resistanceZones.filter { it.isFinite() && it > data.spotPrice }
            .minOrNull()
        val targetSpot = if (optionType == "CE") {
            resistance?.takeIf { it - data.spotPrice in (data.spotPrice * 0.0015)..(atrMove * 3.0) }
                ?: data.spotPrice + atrMove
        } else {
            support?.takeIf { data.spotPrice - it in (data.spotPrice * 0.0015)..(atrMove * 3.0) }
                ?: data.spotPrice - atrMove
        }
        val stopLossSpot = if (optionType == "CE") {
            support?.takeIf { data.spotPrice - it in (data.spotPrice * 0.001)..(atrMove * 2.0) }
                ?: data.spotPrice - max(atrMove / 1.5, data.spotPrice * 0.001)
        } else {
            resistance?.takeIf { it - data.spotPrice in (data.spotPrice * 0.001)..(atrMove * 2.0) }
                ?: data.spotPrice + max(atrMove / 1.5, data.spotPrice * 0.001)
        }
        val favorableMove = abs(targetSpot - data.spotPrice)
        val adverseMove = abs(stopLossSpot - data.spotPrice)
        val delta = abs(if (optionType == "CE") data.callDelta else data.putDelta)
            .takeIf { it in 0.05..1.0 } ?: 0.50
        val gamma = abs(data.gamma).takeIf { it.isFinite() } ?: 0.0
        val rewardPremium = premiumMove(delta, gamma, favorableMove).coerceIn(entryPremium * 0.10, entryPremium * 1.50)
        val riskPremium = premiumMove(delta, gamma, adverseMove).coerceIn(entryPremium * 0.10, entryPremium * 0.80)
        val targetPremium = entryPremium + rewardPremium
        val stopLossPremium = max(0.01, entryPremium - riskPremium)
        val ratio = rewardPremium / riskPremium
        val strikeLabel = if (optionRow.strike % 1.0 == 0.0) {
            optionRow.strike.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", optionRow.strike)
        }
        return LiveTradeSignal(
            recommendedContract = "${data.symbol} $strikeLabel $optionType",
            action = if (optionType == "CE") "BUY CALL" else "BUY PUT",
            optionType = optionType,
            strike = optionRow.strike,
            entrySpot = data.spotPrice,
            targetSpot = targetSpot,
            stopLossSpot = stopLossSpot,
            estimatedPremium = entryPremium,
            targetPremium = targetPremium,
            stopLossPremium = stopLossPremium,
            riskRewardRatio = String.format(Locale.US, "%.2f:1", ratio),
            confidenceScore = consensus.evidenceStrength
        )
    }

    private fun premiumMove(delta: Double, gamma: Double, spotMove: Double): Double =
        delta * spotMove + 0.5 * gamma * spotMove * spotMove

    private fun minuteBars(points: List<ChartPoint>, nowMillis: Long): List<MinuteBar> {
        val samples = points.mapNotNull { point ->
            if (!point.value.isFinite() || point.value <= 0.0 || point.time <= 0L) return@mapNotNull null
            val timestamp = if (point.time < 100_000_000_000L) point.time * 1000L else point.time
            if (timestamp > nowMillis + 60_000L || timestamp < nowMillis - 2 * 60 * 60 * 1000L) {
                return@mapNotNull null
            }
            timestamp to point.value
        }.sortedBy { it.first }
        val grouped = samples.groupBy { Math.floorDiv(it.first, 60_000L) * 60_000L }
        return grouped.entries.sortedBy { it.key }.map { (minute, values) ->
            MinuteBar(
                time = minute,
                open = values.first().second,
                high = values.maxOf { it.second },
                low = values.minOf { it.second },
                close = values.last().second
            )
        }
    }

    private fun priceFeatures(bars: List<MinuteBar>): PriceFeatures? {
        if (bars.size < 6) return null
        val fiveMinuteReturn = returnFraction(bars, 5) ?: return null
        val fifteenMinuteReturn = returnFraction(bars, 15)
        val recentBars = bars.takeLast(15)
        val ranges = recentBars.mapIndexed { index, bar ->
            val previousClose = recentBars.getOrNull(index - 1)?.close ?: bar.open
            max(bar.high - bar.low, max(abs(bar.high - previousClose), abs(bar.low - previousClose))) / bar.close
        }
        val atrPct = ranges.filter { it.isFinite() && it >= 0.0 }.average().takeIf { it.isFinite() }
            ?.coerceAtLeast(0.00015) ?: 0.00015
        val normalized5 = tanh(fiveMinuteReturn / (atrPct * sqrt(5.0) * 1.25))
        val normalized15 = fifteenMinuteReturn?.let {
            tanh(it / (atrPct * sqrt(15.0) * 1.25))
        } ?: 0.0
        val rsi = rsi(bars.takeLast(8).map { it.close })
        val priorRsi = if (bars.size >= 13) rsi(bars.dropLast(5).takeLast(8).map { it.close }) else rsi
        val closes = bars.map { it.close }
        val regressionTrend = regressionTrend(bars, atrPct)
        return PriceFeatures(
            momentum5 = normalized5,
            momentum15 = normalized15,
            acceleration = (normalized5 - normalized15).coerceIn(-1.0, 1.0),
            rsiTurn = ((rsi - priorRsi) / 18.0).coerceIn(-1.0, 1.0),
            macdScore = macdHistogramScore(closes, atrPct),
            slopeScore = regressionTrend.score,
            efficiency5 = directionalEfficiency(bars, 5) ?: 0.0,
            efficiency15 = directionalEfficiency(bars, 15),
            regressionFit = regressionTrend.fit,
            atrPct = atrPct,
            bars = bars,
            rsi = rsi
        )
    }

    private fun returnFraction(bars: List<MinuteBar>, minutes: Int): Double? {
        if (bars.size < 3) return null
        val latest = bars.last()
        val targetTime = latest.time - minutes * 60_000L
        val previous = bars.lastOrNull { it.time <= targetTime } ?: return null
        val elapsedMinutes = (latest.time - previous.time) / 60_000.0
        if (elapsedMinutes < minutes * 0.55 || elapsedMinutes > minutes * 1.8) return null
        return latest.close / previous.close - 1.0
    }

    private fun formatReturnPct(bars: List<MinuteBar>, minutes: Int): String =
        returnFraction(bars, minutes)?.let { formatPct(it * 100.0) } ?: "unavailable"

    private fun emaTrendScore(closes: List<Double>, atrPct: Double): Double {
        if (closes.size < 13) return 0.0
        val ema5 = ema(closes, 5)
        val ema13 = ema(closes, 13)
        return tanh(((ema5 - ema13) / closes.last()) / (atrPct * 1.5))
    }

    private fun macdHistogramScore(closes: List<Double>, atrPct: Double): Double {
        if (closes.size < 34) return 0.0
        val macd = (26..closes.lastIndex).map { end ->
            val sample = closes.subList(0, end + 1)
            ema(sample, 12) - ema(sample, 26)
        }
        val histogram = macd.last() - ema(macd, 9)
        return tanh(histogram / (closes.last() * atrPct * 0.35))
    }

    private data class RegressionTrend(val score: Double, val fit: Double)

    private fun regressionTrend(bars: List<MinuteBar>, atrPct: Double): RegressionTrend {
        val sample = bars.takeLast(15)
        if (sample.size < 6 || sample.any { it.close <= 0.0 }) return RegressionTrend(0.0, 0.0)
        if (sample.zipWithNext().any { (left, right) -> right.time - left.time > 150_000L }) {
            return RegressionTrend(0.0, 0.0)
        }
        val x = sample.map { (it.time - sample.first().time) / 60_000.0 }
        val y = sample.map { ln(it.close) }
        val xMean = x.average()
        val yMean = y.average()
        var covariance = 0.0
        var xVariance = 0.0
        var yVariance = 0.0
        sample.indices.forEach { index ->
            val dx = x[index] - xMean
            val dy = y[index] - yMean
            covariance += dx * dy
            xVariance += dx * dx
            yVariance += dy * dy
        }
        if (xVariance == 0.0 || yVariance == 0.0) return RegressionTrend(0.0, 0.0)
        val slopePerMinute = covariance / xVariance
        val rSquared = (covariance * covariance / (xVariance * yVariance)).coerceIn(0.0, 1.0)
        val score = tanh(slopePerMinute / (atrPct * 0.35)) * sqrt(rSquared)
        return RegressionTrend(score.coerceIn(-1.0, 1.0), rSquared)
    }

    private fun directionalEfficiency(bars: List<MinuteBar>, minutes: Int): Double? {
        if (bars.size < 4) return null
        val latest = bars.last()
        val targetTime = latest.time - minutes * 60_000L
        val startIndex = bars.indexOfLast { it.time <= targetTime }
        if (startIndex < 0) return null
        val sample = bars.subList(startIndex, bars.size)
        val elapsedMinutes = (latest.time - sample.first().time) / 60_000.0
        if (elapsedMinutes < minutes * 0.55 || elapsedMinutes > minutes * 1.8 ||
            sample.zipWithNext().any { (left, right) -> right.time - left.time > 150_000L }
        ) return null
        val pathLength = sample.zipWithNext().sumOf { (left, right) -> abs(right.close - left.close) }
        if (pathLength <= 0.0) return 0.0
        return ((sample.last().close - sample.first().close) / pathLength).coerceIn(-1.0, 1.0)
    }

    private fun ema(values: List<Double>, period: Int): Double {
        val alpha = 2.0 / (period + 1.0)
        var result = values.take(period).average()
        for (index in period until values.size) {
            result = alpha * values[index] + (1.0 - alpha) * result
        }
        return result
    }

    private fun rsi(values: List<Double>): Double {
        if (values.size < 3) return 50.0
        val changes = values.zipWithNext { previous, current -> current - previous }
        val gains = changes.sumOf { max(0.0, it) } / changes.size
        val losses = changes.sumOf { max(0.0, -it) } / changes.size
        if (losses == 0.0) return if (gains == 0.0) 50.0 else 100.0
        return 100.0 - 100.0 / (1.0 + gains / losses)
    }

    private fun supportResistanceScore(
        data: OptionSentimentData,
        momentum: Double,
        bars: List<MinuteBar>
    ): Double {
        val spot = data.spotPrice
        if (spot <= 0.0) return 0.0
        val localPivots = bars.drop(1).dropLast(1).mapIndexedNotNull { index, bar ->
            val centerIndex = index + 1
            val before = bars[centerIndex - 1]
            val after = bars[centerIndex + 1]
            when {
                bar.low <= before.low && bar.low <= after.low -> true to bar.low
                bar.high >= before.high && bar.high >= after.high -> false to bar.high
                else -> null
            }
        }
        val supports = (data.supportZones + localPivots.filter { it.first }.map { it.second })
            .filter { it.isFinite() && it > 0.0 }
        val resistances = (data.resistanceZones + localPivots.filterNot { it.first }.map { it.second })
            .filter { it.isFinite() && it > 0.0 }
        val supportBelow = supports.filter { it <= spot }.maxOrNull()
        val supportAbove = supports.filter { it > spot }.minOrNull()
        val resistanceBelow = resistances.filter { it < spot }.maxOrNull()
        val resistanceAbove = resistances.filter { it >= spot }.minOrNull()
        val nearbySupport = listOfNotNull(supportBelow, supportAbove).minByOrNull { abs(spot - it) }
        val nearbyResistance = listOfNotNull(resistanceBelow, resistanceAbove).minByOrNull { abs(spot - it) }
        val supportDistance = nearbySupport?.let { abs(spot - it) / spot }
        val resistanceDistance = nearbyResistance?.let { abs(it - spot) / spot }
        return when {
            resistanceBelow != null && spot > resistanceBelow * 1.001 && momentum > 0.12 -> 0.9
            supportAbove != null && spot < supportAbove * 0.999 && momentum < -0.12 -> -0.9
            supportBelow != null && supportDistance != null && supportDistance <= 0.003 && momentum > 0.12 -> 0.72
            resistanceAbove != null && resistanceDistance != null && resistanceDistance <= 0.003 && momentum < -0.12 -> -0.72
            supportDistance != null && supportDistance <= 0.003 && momentum < -0.3 -> -0.35
            resistanceDistance != null && resistanceDistance <= 0.003 && momentum > 0.3 -> 0.35
            else -> 0.0
        }
    }

    private fun optionFlowScore(data: OptionSentimentData): Double {
        val nearRows = data.optionChain.filter {
            it.strike > 0.0 && data.spotPrice > 0.0 &&
                abs(it.strike - data.spotPrice) / data.spotPrice <= 0.02
        }
        var weightedFlow = 0.0
        var totalWeight = 0.0
        nearRows.forEach { row ->
            val callSignal = buildupUnderlyingScore(row.callBuild, isCall = true)
            val putSignal = buildupUnderlyingScore(row.putBuild, isCall = false)
            val callWeight = max(abs(row.callOiChange).toDouble(), if (callSignal != 0.0) 1.0 else 0.0)
            val putWeight = max(abs(row.putOiChange).toDouble(), if (putSignal != 0.0) 1.0 else 0.0)
            weightedFlow += callSignal * callWeight + putSignal * putWeight
            totalWeight += callWeight + putWeight
        }
        val buildupScore = if (totalWeight > 0.0) weightedFlow / totalWeight else 0.0
        val pcrOiScore = ratioScore(data.pcrOi)
        val pcrVolumeScore = ratioScore(data.pcrVolume)
        val narrativeScore = when {
            containsAny(data.oiDirectionalRead, "bullish", "put-side support", "put writers") -> 0.5
            containsAny(data.oiDirectionalRead, "bearish", "call-side resistance", "call writers") -> -0.5
            else -> 0.0
        }
        return (
            0.65 * buildupScore +
                0.18 * pcrOiScore +
                0.08 * pcrVolumeScore +
                0.09 * narrativeScore
            ).coerceIn(-1.0, 1.0)
    }

    private fun hasDirectionalChainEvidence(data: OptionSentimentData): Boolean {
        val hasUsableRows = data.optionChain.any {
            it.strike > 0.0 && it.strike.isFinite() &&
                (it.totalOi > 0L || it.callOiChange != 0L || it.putOiChange != 0L ||
                    it.callBuild.isNotBlank() || it.putBuild.isNotBlank())
        }
        return hasUsableRows || data.pcrOi > 0.0 || data.pcrVolume > 0.0 ||
            data.oiDirectionalRead.isNotBlank()
    }

    private fun buildupUnderlyingScore(value: String, isCall: Boolean): Double {
        val text = value.lowercase(Locale.US)
        if (text.contains("long buildup")) return if (isCall) 1.0 else -1.0
        if (text.contains("short buildup")) return if (isCall) -1.0 else 1.0
        if (text.contains("short covering")) return if (isCall) 1.0 else -1.0
        if (text.contains("long unwinding")) return if (isCall) -1.0 else 1.0
        return 0.0
    }

    private fun ratioScore(value: Double): Double {
        if (!value.isFinite() || value <= 0.0) return 0.0
        return tanh(ln(value) / ln(1.35))
    }

    private fun directionTextScore(value: String): Double = when {
        containsAny(value, "bullish", "uptrend", "higher high", "breakout up") -> 1.0
        containsAny(value, "bearish", "downtrend", "lower low", "breakdown") -> -1.0
        else -> 0.0
    }

    private fun patternScore(value: String): Double = when {
        containsAny(value, "double-bottom", "bull flag", "bullish engulf", "inverse head", "ascending triangle", "bullish breakout") -> 1.0
        containsAny(value, "double-top", "bear flag", "bearish engulf", "head and shoulders", "descending triangle", "bearish breakdown") -> -1.0
        else -> directionTextScore(value) * 0.6
    }

    private fun containsAny(value: String, vararg phrases: String): Boolean {
        val text = value.lowercase(Locale.US)
        return phrases.any { text.contains(it) }
    }

    private fun sign(value: Double): Int = when {
        value > 0.0 -> 1
        value < 0.0 -> -1
        else -> 0
    }

    private fun formatPct(value: Double): String = String.format(Locale.US, "%+.3f%%", value)
    private fun formatScore(value: Double): String = String.format(Locale.US, "%+.2f", value)
    private fun formatMetric(value: Double): String =
        if (value.isFinite() && value > 0.0) String.format(Locale.US, "%.2f", value) else "unavailable"

    private fun Double?.orZero(): Double = this ?: 0.0
}
