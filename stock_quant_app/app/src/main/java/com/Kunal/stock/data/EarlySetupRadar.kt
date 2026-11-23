package com.Kunal.stock.data

import kotlin.math.max

data class MarketTrendContext(
    val latest: Double,
    val sma20: Double,
    val sma50: Double,
    val sma200: Double,
    val change5dPct: Double,
    val regime: String
) {
    val above200: Boolean get() = latest > sma200
}

data class EarlySetupCandidate(
    val symbol: String,
    val sector: String,
    val stage: String,
    val score: Int,
    val close: Double,
    val pivot: Double,
    val distanceToPivotPct: Double,
    val change5dPct: Double,
    val rsi14: Double,
    val volumeRatio: Double,
    val reasons: List<String>
)

data class PlaybookScanResult(
    val signals: List<PlaybookSignal>,
    val earlyCandidates: List<EarlySetupCandidate>
)

object EarlySetupRadar {
    fun marketTrend(closes: List<Double>): MarketTrendContext? {
        val valid = closes.filter { it.isFinite() && it > 0.0 }
        if (valid.size < 200) return null
        val latest = valid.last()
        val sma20 = valid.takeLast(20).average()
        val sma50 = valid.takeLast(50).average()
        val sma200 = valid.takeLast(200).average()
        val fiveDayReference = valid[valid.lastIndex - 5]
        val change5dPct = (latest / fiveDayReference - 1.0) * 100.0
        val priorSma20 = valid.dropLast(5).takeLast(20).average()
        val regime = when {
            latest > sma200 && latest > sma20 && sma20 > sma50 -> "BULLISH TREND"
            latest < sma200 && latest > sma20 && change5dPct > 0.0 ->
                "BEARISH REGIME · REVERSAL WATCH"
            latest < sma200 && (change5dPct > 0.0 || sma20 > priorSma20) ->
                "BEARISH · IMPROVING"
            latest < sma20 && latest < sma50 && change5dPct < 0.0 -> "BEARISH TREND"
            else -> "MIXED / TRANSITION"
        }
        return MarketTrendContext(latest, sma20, sma50, sma200, change5dPct, regime)
    }

    fun evaluate(symbol: String, sector: String, candles: List<StockCandle>): EarlySetupCandidate? {
        val bars = candles.filter { bar ->
            bar.close.isFinite() && bar.close > 0.0 && bar.high > 0.0 && bar.low > 0.0 &&
                bar.high >= bar.low && bar.volume >= 0
        }.sortedBy { it.time }
        if (bars.size < 30) return null

        val closes = bars.map { it.close }
        val current = bars.last()
        val pivot = bars.dropLast(1).takeLast(20).maxOf { it.high }
        if (pivot <= 0.0 || current.close >= pivot) return null

        val distanceToPivotPct = (pivot - current.close) / pivot * 100.0
        val fiveDayReference = closes[closes.lastIndex - 5]
        val change5dPct = (current.close / fiveDayReference - 1.0) * 100.0
        val recentLows = bars.takeLast(5).map { it.low }
        val previousLows = bars.dropLast(5).takeLast(5).map { it.low }
        val reboundFromLowPct = (current.close / bars.takeLast(10).minOf { it.low } - 1.0) * 100.0
        val currentEma10 = ema(closes, 10)
        val ema10FiveDaysAgo = ema(closes.dropLast(5), 10)
        val reclaimedEma10 = current.close > currentEma10 &&
            closes[closes.lastIndex - 3] <= ema(closes.dropLast(3), 10)
        val risingEma10 = currentEma10 > ema10FiveDaysAgo
        val higherLows = recentLows.average() > previousLows.average() * 1.003
        val currentRsi = rsi14(closes)
        val rsiFiveDaysAgo = rsi14(closes.dropLast(5))
        val rsiImproving = currentRsi in 43.0..68.0 && currentRsi - rsiFiveDaysAgo >= 3.0
        val priorVolume = bars.dropLast(5).takeLast(15).map { it.volume.toDouble() }.average()
        val recentVolume = bars.takeLast(5).map { it.volume.toDouble() }.average()
        val volumeRatio = if (priorVolume > 0.0) recentVolume / priorVolume else 0.0
        val volumeAccumulation = volumeRatio >= 1.15 && change5dPct > 0.0
        val recentRange = averageRangePct(bars.takeLast(5))
        val baseRange = averageRangePct(bars.dropLast(5).takeLast(15))
        val contracting = baseRange > 0.0 && recentRange <= baseRange * 0.82
        val nearPivot = distanceToPivotPct in 0.0..6.0
        val rebound = reboundFromLowPct >= 3.0 && change5dPct > 0.0 && current.close > currentEma10
        if (!nearPivot && !rebound) return null

        val reasons = mutableListOf<String>()
        var score = 0
        if (nearPivot) {
            score += if (distanceToPivotPct <= 3.0) 20 else 14
            reasons += "Within ${"%.1f".format(java.util.Locale.US, distanceToPivotPct)}% of the prior 20-session high"
        }
        if (rebound) {
            score += 20
            reasons += "Rebounded ${"%.1f".format(java.util.Locale.US, reboundFromLowPct)}% from the recent low with positive 5-session momentum"
        }
        if (reclaimedEma10) {
            score += 15
            reasons += "Recently reclaimed the 10-day EMA"
        }
        if (risingEma10 && current.close > currentEma10) {
            score += 10
            reasons += "10-day EMA is rising and price is holding above it"
        }
        if (higherLows) {
            score += 15
            reasons += "Recent lows are stepping higher"
        }
        if (rsiImproving) {
            score += 10
            reasons += "RSI is recovering into a constructive range"
        }
        if (volumeAccumulation) {
            score += 10
            reasons += "Recent volume is expanding on positive price progress"
        }
        if (contracting) {
            score += 10
            reasons += "Recent daily ranges are contracting near the setup area"
        }
        if (score < 40 || reasons.size < 2) return null

        val stage = if (nearPivot && (contracting || reclaimedEma10 || higherLows)) {
            "PRE-BREAKOUT WATCH"
        } else {
            "EARLY REVERSAL WATCH"
        }
        return EarlySetupCandidate(
            symbol = symbol,
            sector = sector,
            stage = stage,
            score = score.coerceAtMost(100),
            close = current.close,
            pivot = pivot,
            distanceToPivotPct = distanceToPivotPct,
            change5dPct = change5dPct,
            rsi14 = currentRsi,
            volumeRatio = volumeRatio,
            reasons = reasons
        )
    }

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.size < period) return Double.NaN
        var value = values.take(period).average()
        val alpha = 2.0 / (period + 1)
        for (index in period until values.size) {
            value = values[index] * alpha + value * (1.0 - alpha)
        }
        return value
    }

    private fun rsi14(values: List<Double>): Double {
        if (values.size < 15) return 50.0
        val changes = values.takeLast(15).zipWithNext { previous, current -> current - previous }
        val gains = changes.map { max(0.0, it) }.average()
        val losses = changes.map { max(0.0, -it) }.average()
        if (losses == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + gains / losses)
    }

    private fun averageRangePct(bars: List<StockCandle>): Double {
        if (bars.isEmpty()) return 0.0
        return bars.map { (it.high - it.low) / it.close }.average()
    }
}
