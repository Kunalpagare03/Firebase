package com.Kunal.stock.ui

import com.Kunal.stock.data.BacktestResult
import com.Kunal.stock.data.StrategyBlueprint
import com.Kunal.stock.data.TradeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

internal object HistoricalBacktestEngine {
    internal data class Candle(
        val epochSeconds: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )

    internal val supportedStrategyTypes = setOf(
        "EMA_CROSS",
        "EMA_PULLBACK",
        "RSI_REVERSAL",
        "RSI_MOMENTUM",
        "BREAKOUT",
        "RVOL_BREAKOUT",
        "ATR_SQUEEZE_BREAKOUT"
    )

    suspend fun run(
        symbol: String,
        strategyType: String,
        timeframe: String,
        targetPct: Double,
        stopLossPct: Double,
        atrMultiplier: Double,
        fastPeriod: Int,
        slowPeriod: Int,
        strategyName: String
    ): BacktestResult = withContext(Dispatchers.IO) {
        require(strategyType in supportedStrategyTypes) {
            "Unsupported daily-price strategy: $strategyType"
        }
        val candles = fetchCandles(symbol, timeframe)
        if (candles.size < 60) {
            throw IllegalStateException("Yahoo Finance returned too little daily price history for $symbol.")
        }
        if (strategyType in setOf("RVOL_BREAKOUT", "ATR_SQUEEZE_BREAKOUT") &&
            candles.count { it.volume > 0L } < candles.size * 0.8
        ) {
            throw IllegalStateException("Yahoo Finance returned insufficient daily volume history for $symbol; this strategy needs reliable volume data.")
        }
        calculate(
            candles, symbol, strategyType, timeframe, targetPct, stopLossPct,
            atrMultiplier, fastPeriod, slowPeriod, strategyName
        )
    }

    private fun fetchCandles(symbol: String, timeframe: String): List<Candle> {
        val ticker = when (symbol.trim().uppercase().removeSuffix(".NS")) {
            "NIFTY" -> "^NSEI"
            "BANKNIFTY" -> "^NSEBANK"
            "SENSEX" -> "^BSESN"
            "FINNIFTY" -> "^CNXFINANCE"
            else -> if (symbol.endsWith(".BO", true)) symbol.trim().uppercase()
                else "${symbol.trim().uppercase().removeSuffix(".NS")}.NS"
        }
        val encodedTicker = URLEncoder.encode(ticker, Charsets.UTF_8.name())
        val range = if (timeframe in setOf("1y", "2y", "5y")) timeframe else "2y"
        val connection = (URL(
            "https://query1.finance.yahoo.com/v8/finance/chart/$encodedTicker?range=$range&interval=1d"
        ).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android historical backtest)")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Yahoo Finance history request failed (HTTP $status).")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(json).getJSONObject("chart").optJSONArray("result")
                ?.optJSONObject(0)
                ?: throw IllegalStateException("Yahoo Finance returned no history for $symbol.")
            val timestamps = result.optJSONArray("timestamp")
                ?: throw IllegalStateException("Yahoo Finance returned no daily timestamps for $symbol.")
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            val opens = quote.optJSONArray("open")
            val highs = quote.optJSONArray("high")
            val lows = quote.optJSONArray("low")
            val closes = quote.optJSONArray("close")
            val volumes = quote.optJSONArray("volume")
            return (0 until timestamps.length()).mapNotNull { index ->
                val open = opens?.optDouble(index, Double.NaN) ?: Double.NaN
                val high = highs?.optDouble(index, Double.NaN) ?: Double.NaN
                val low = lows?.optDouble(index, Double.NaN) ?: Double.NaN
                val close = closes?.optDouble(index, Double.NaN) ?: Double.NaN
                if (listOf(open, high, low, close).any { !it.isFinite() || it <= 0.0 }) null
                else Candle(
                    timestamps.optLong(index),
                    open,
                    high,
                    low,
                    close,
                    volumes?.optLong(index, 0L) ?: 0L
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun calculate(
        candles: List<Candle>,
        symbol: String,
        strategyType: String,
        timeframe: String,
        targetPct: Double,
        stopLossPct: Double,
        atrMultiplier: Double,
        fastPeriod: Int,
        slowPeriod: Int,
        strategyName: String
    ): BacktestResult {
        val closes = candles.map { it.close }
        val fastEma = emaSeries(closes, fastPeriod.coerceAtLeast(2))
        val slowEma = emaSeries(closes, slowPeriod.coerceAtLeast(3))
        val ema20 = emaSeries(closes, 20)
        val atr = atrSeries(candles)
        val trades = mutableListOf<TradeRecord>()
        var index = maxOf(20, slowPeriod.coerceAtLeast(3), 15)
        val maxHoldingBars = 20
        val feePct = 0.1
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

        while (index < candles.lastIndex - 1) {
            if (!isEntrySignal(candles, index, strategyType, fastEma, slowEma, ema20, atr)) {
                index++
                continue
            }

            val entryIndex = index + 1
            val entry = candles[entryIndex]
            val riskPct = maxOf(
                stopLossPct.coerceAtLeast(0.1),
                (atr[index] * atrMultiplier.coerceAtLeast(0.1) / entry.open * 100.0)
            )
            val stop = entry.open * (1.0 - riskPct / 100.0)
            val target = entry.open * (1.0 + targetPct.coerceAtLeast(0.1) / 100.0)
            val finalIndex = minOf(candles.lastIndex, entryIndex + maxHoldingBars)
            var exitIndex = finalIndex
            var exitPrice = candles[finalIndex].close
            var exitReason = "TIME EXIT"

            for (barIndex in entryIndex..finalIndex) {
                val bar = candles[barIndex]
                val hitStop = bar.low <= stop
                val hitTarget = bar.high >= target
                when {
                    hitStop -> {
                        exitIndex = barIndex
                        exitPrice = if (bar.open < stop) bar.open else stop
                        exitReason = "STOP LOSS (conservative same-bar ordering)"
                        break
                    }
                    hitTarget -> {
                        exitIndex = barIndex
                        exitPrice = target
                        exitReason = "TARGET HIT"
                        break
                    }
                }
            }

            val grossReturn = (exitPrice / entry.open - 1.0) * 100.0
            val netReturn = grossReturn - feePct
            trades += TradeRecord(
                entryDate = dateFormat.format(Date(entry.epochSeconds * 1000)),
                exitDate = dateFormat.format(Date(candles[exitIndex].epochSeconds * 1000)),
                type = "UNDERLYING LONG",
                entryPrice = round2(entry.open),
                exitPrice = round2(exitPrice),
                returnPct = round2(netReturn),
                holdingDays = (exitIndex - entryIndex).coerceAtLeast(1),
                exitReason = exitReason,
                patternTrigger = strategyType.replace('_', ' '),
                isWin = netReturn > 0.0,
                rMultiple = round2(netReturn / riskPct)
            )
            index = exitIndex + 1
        }

        val returns = trades.map { it.returnPct }
        val wins = returns.count { it > 0.0 }
        val losses = returns.size - wins
        val grossWins = returns.filter { it > 0.0 }.sum()
        val grossLosses = -returns.filter { it < 0.0 }.sum()
        var equity = 1.0
        var peak = 1.0
        var maxDrawdown = 0.0
        returns.forEach { tradeReturn ->
            equity *= 1.0 + tradeReturn / 100.0
            peak = maxOf(peak, equity)
            maxDrawdown = maxOf(maxDrawdown, (peak - equity) / peak * 100.0)
        }
        val average = returns.average().takeIf { it.isFinite() } ?: 0.0
        val variance = if (returns.size > 1) returns.map { (it - average) * (it - average) }.average() else 0.0
        val deviation = sqrt(variance).coerceAtLeast(0.0001)
        val downside = returns.filter { it < 0.0 }
        val downsideDeviation = if (downside.size > 1) {
            sqrt(downside.map { it * it }.average()).coerceAtLeast(0.0001)
        } else 0.0001
        val streaks = consecutiveStreaks(returns)
        val sourceNote = "Yahoo Finance daily underlying OHLCV; signal at prior close, entry at next open; " +
            "target/stop tested against subsequent daily high/low; stop is assumed first if both " +
            "levels touch in one bar; 0.1% round-trip costs assumed. Results are underlying-price " +
            "returns, not option-premium or futures P&L. Not a calibrated probability or live execution model."

        return BacktestResult(
            status = "ok",
            symbol = symbol,
            strategyName = strategyName,
            strategyType = strategyType,
            timeframe = timeframe,
            totalTrades = trades.size,
            wins = wins,
            losses = losses,
            winRate = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            totalReturn = round2((equity - 1.0) * 100.0),
            profitFactor = if (grossLosses == 0.0) if (grossWins > 0.0) 0.0 else 0.0 else grossWins / grossLosses,
            maxDrawdown = round2(maxDrawdown),
            sharpeRatio = round2(if (trades.isEmpty()) 0.0 else average / deviation * sqrt(trades.size.toDouble())),
            sortinoRatio = round2(if (trades.isEmpty()) 0.0 else average / downsideDeviation * sqrt(trades.size.toDouble())),
            calmarRatio = round2(if (maxDrawdown == 0.0) 0.0 else ((equity - 1.0) * 100.0) / maxDrawdown),
            expectancy = round2(average),
            avgTradeReturn = round2(average),
            maxConsecutiveWins = streaks.first,
            maxConsecutiveLosses = streaks.second,
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            trades = trades.asReversed(),
            blueprint = StrategyBlueprint(
                name = strategyName,
                type = strategyType,
                rules = listOf(
                    when (strategyType) {
                        "EMA_CROSS" -> "Signal: fast daily EMA crosses above slow daily EMA."
                        "EMA_PULLBACK" -> "Signal: price reclaims EMA20 while fast EMA remains above slow EMA."
                        "RSI_REVERSAL" -> "Signal: RSI(14) is below 35 and the close rises versus the prior close."
                        "RSI_MOMENTUM" -> "Signal: RSI(14) crosses above 50 with close above EMA20 and EMA20 above slow EMA."
                        "BREAKOUT" -> "Signal: close breaks above the prior 20-session high."
                        "RVOL_BREAKOUT" -> "Signal: bullish close clears prior high with volume above 1.5x the prior 20-session mean."
                        "ATR_SQUEEZE_BREAKOUT" -> "Signal: ATR contracts at least 20% versus its prior 20-session mean, then price breaks the prior high on volume."
                        else -> "Signal: unsupported strategy."
                    },
                    "Signals are evaluated after each completed daily candle; entries use the next day's open.",
                    "Profit target ${targetPct}% and stop distance max(${stopLossPct}%, ${atrMultiplier} x daily ATR).",
                    "At most one position is open; positions exit at target, stop, or after $maxHoldingBars daily bars.",
                    "Long-only underlying returns; option premium, futures P&L, short trades, spread and market impact are not modeled."
                ),
                indicators = listOf("Daily OHLCV", "EMA $fastPeriod/$slowPeriod", "ATR(14)"),
                mathematicalEdge = sourceNote,
                isRecommendedLive = false,
                regimeCommentary = "Historical Yahoo Finance price test only; past results do not predict future returns."
            )
        )
    }

    internal fun isEntrySignal(
        candles: List<Candle>,
        index: Int,
        type: String,
        fastEma: List<Double>,
        slowEma: List<Double>,
        ema20: List<Double>,
        atr: List<Double>
    ): Boolean {
        if (index <= 0 || index >= candles.size || type.uppercase() !in supportedStrategyTypes) return false
        val current = candles[index]
        val previous = candles[index - 1]
        return when (type.uppercase()) {
            "EMA_CROSS" ->
                fastEma[index] > slowEma[index] && fastEma[index - 1] <= slowEma[index - 1]
            "EMA_PULLBACK" ->
                current.close > ema20[index] && previous.close <= ema20[index - 1] &&
                    fastEma[index] > slowEma[index]
            "RSI_REVERSAL" ->
                rsi(candles, index) < 35.0 && current.close > previous.close
            "RSI_MOMENTUM" ->
                rsi(candles, index - 1) <= 50.0 && rsi(candles, index) > 50.0 &&
                    ema20[index] > slowEma[index] && current.close > ema20[index]
            "BREAKOUT" ->
                index >= 20 && current.close > candles.subList(index - 20, index).maxOf { it.high }
            "RVOL_BREAKOUT" ->
                current.close > current.open &&
                    current.volume > candles.subList(index - 20, index).map { it.volume }.average() * 1.5 &&
                    current.close > previous.high
            "ATR_SQUEEZE_BREAKOUT" -> {
                if (index < 41) {
                    false
                } else {
                    val priorRangeHigh = candles.subList(index - 20, index).maxOf { it.high }
                    val earlierAtrMean = atr.subList(index - 21, index - 1).average()
                    val priorVolumeMean = candles.subList(index - 20, index).map { it.volume }.average()
                    atr[index - 1] <= earlierAtrMean * 0.8 &&
                        current.close > priorRangeHigh &&
                        current.close > current.open &&
                        current.volume > priorVolumeMean * 1.2
                }
            }
            else -> false
        }
    }

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        val result = MutableList(values.size) { Double.NaN }
        if (values.size < period) return result
        val multiplier = 2.0 / (period + 1)
        var current = values.take(period).average()
        result[period - 1] = current
        for (index in period until values.size) {
            current = values[index] * multiplier + current * (1.0 - multiplier)
            result[index] = current
        }
        return result
    }

    private fun atrSeries(candles: List<Candle>): List<Double> {
        val ranges = candles.indices.map { index ->
            val candle = candles[index]
            val previousClose = if (index == 0) candle.close else candles[index - 1].close
            maxOf(candle.high - candle.low, kotlin.math.abs(candle.high - previousClose), kotlin.math.abs(candle.low - previousClose))
        }
        return ranges.indices.map { index ->
            ranges.subList(maxOf(0, index - 13), index + 1).average()
        }
    }

    private fun rsi(candles: List<Candle>, index: Int): Double {
        val changes = (maxOf(1, index - 13)..index).map { candles[it].close - candles[it - 1].close }
        val gains = changes.filter { it > 0 }.sum() / 14.0
        val losses = -changes.filter { it < 0 }.sum() / 14.0
        return if (losses == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + gains / losses)
    }

    private fun consecutiveStreaks(returns: List<Double>): Pair<Int, Int> {
        var wins = 0
        var losses = 0
        var currentWins = 0
        var currentLosses = 0
        returns.forEach { result ->
            if (result > 0) {
                currentWins++
                currentLosses = 0
            } else {
                currentLosses++
                currentWins = 0
            }
            wins = maxOf(wins, currentWins)
            losses = maxOf(losses, currentLosses)
        }
        return wins to losses
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
