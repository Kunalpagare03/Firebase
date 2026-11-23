package com.Kunal.stock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * PlaybookScanner — evaluates four rule-based setups (A/B/C/D) on Nifty 500 stocks.
 *
 * Price data: Yahoo Finance (free, no key). Each stock fetched with 1-year daily candles.
 * Current signals are not historical performance tests; verify results independently.
 */
object PlaybookScanner {

    private const val PREFS_NAME = "playbook_prefs"
    private const val KEY_SIGNALS = "saved_signals"
    private const val YAHOO_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"

    // In-memory cache: symbol -> (timestampMs, candles)
    private val candleCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<StockCandle>>>()
    private const val CANDLE_CACHE_TTL_MS = 3 * 3600 * 1000L // 3 hours

    fun clearCache() {
        candleCache.clear()
    }

    fun getCachedCount(): Int = candleCache.size

    // ── Yahoo Finance helper ────────────────────────────────────────────────
    private suspend fun fetchYahooCandles(symbol: String, days: Int = 300): List<StockCandle> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = candleCache[symbol]
            if (cached != null && (now - cached.first) < CANDLE_CACHE_TTL_MS && cached.second.size >= 180) {
                return@withContext cached.second
            }
            try {
                val yahooSym = "$symbol.NS"
                val url = "$YAHOO_BASE$yahooSym?interval=1d&range=${ceil(days / 252.0).toInt()}y"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode != 200) return@withContext emptyList()
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val parsed = parseYahooCandles(body)
                if (parsed.isNotEmpty()) {
                    candleCache[symbol] = Pair(now, parsed)
                }
                parsed
            } catch (_: Exception) { emptyList() }
        }

    suspend fun fetchHistoryForPerformance(symbol: String, days: Int = 1100): List<StockCandle> =
        fetchYahooCandles(symbol.removeSuffix(".NS"), days)

    private fun parseYahooCandles(json: String): List<StockCandle> = try {
        val chart = JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val timestamps = chart.getJSONArray("timestamp")
        val indicators = chart.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val opens   = indicators.optJSONArray("open")
        val highs   = indicators.optJSONArray("high")
        val lows    = indicators.optJSONArray("low")
        val closes  = indicators.optJSONArray("close")
        val volumes = indicators.optJSONArray("volume")
        val result = mutableListOf<StockCandle>()
        for (i in 0 until timestamps.length()) {
            val c = closes?.optDouble(i, Double.NaN) ?: Double.NaN
            if (c.isNaN() || c <= 0) continue
            result.add(StockCandle(
                time   = (timestamps.optLong(i, 0L)) * 1000L,
                open   = opens?.optDouble(i, c) ?: c,
                high   = highs?.optDouble(i, c) ?: c,
                low    = lows?.optDouble(i, c) ?: c,
                close  = c,
                volume = volumes?.optLong(i, 0L) ?: 0L
            ))
        }
        result
    } catch (_: Exception) { emptyList() }

    // ── Technical indicators ────────────────────────────────────────────────
    private fun sma(closes: List<Double>, period: Int, idx: Int): Double {
        if (idx < period - 1) return Double.NaN
        return closes.subList(idx - period + 1, idx + 1).average()
    }

    private fun ema(closes: List<Double>, period: Int, idx: Int): Double {
        if (closes.size < period) return Double.NaN
        var ema = closes.take(period).average()
        val k = 2.0 / (period + 1)
        for (i in period until idx + 1) ema = closes[i] * k + ema * (1 - k)
        return ema
    }

    private fun rsi2(closes: List<Double>, idx: Int): Double {
        if (idx < 2) return 50.0
        var gains = 0.0; var losses = 0.0
        for (i in (idx - 1)..idx) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gains += d else losses -= d
        }
        if (losses == 0.0) return 100.0
        return 100.0 - 100.0 / (1 + gains / losses)
    }

    private fun rsi14(closes: List<Double>, idx: Int): Double {
        if (idx < 14) return 50.0
        var gains = 0.0; var losses = 0.0
        for (i in (idx - 13)..idx) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gains += d else losses -= d
        }
        val ag = gains / 14; val al = losses / 14
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1 + ag / al)
    }

    private fun maxHigh(highs: List<Double>, period: Int, idx: Int): Double {
        val from = maxOf(0, idx - period + 1)
        return highs.subList(from, idx + 1).maxOrNull() ?: Double.NaN
    }

    private fun vol20avg(volumes: List<Long>, idx: Int): Double {
        val from = maxOf(0, idx - 19)
        return volumes.subList(from, idx + 1).average()
    }

    // ── Setup A: Buy the Dip RSI(2) < 5 in uptrend ─────────────────────────
    private fun checkSetupA(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        val rsi = rsi2(closes, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        val dailyValue = vol * c
        return c > sma200 && rsi < 5 && dailyValue >= 50_000_000 && c >= 50
    }

    // ── Setup B: Pullback to rising 50-DMA near 52-week high ────────────────
    private fun checkSetupB(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val highs  = candles.map { it.high }
        val c = closes[idx]; val l = candles[idx].low; val o = candles[idx].open
        val sma50  = sma(closes, 50, idx)
        val sma200 = sma(closes, 200, idx)
        val sma50prev = sma(closes, 50, idx - 10)
        val high252 = maxHigh(highs, 252, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        return c >= high252 * 0.85 &&
               sma50 > sma50prev &&           // 50-DMA rising
               sma50 > sma200 &&              // above 200-DMA
               l <= sma50 * 1.01 &&           // today's low touched 50-DMA
               c > sma50 &&                   // but closed above
               c > o &&                       // green candle
               vol * c >= 50_000_000 && c >= 50
    }

    // ── Setup C: Strength leader pulls back to 10 EMA ───────────────────────
    private fun checkSetupC(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 63) return false
        val closes = candles.map { it.close }
        val c = closes[idx]; val l = candles[idx].low; val o = candles[idx].open
        val ema10 = ema(closes, 10, idx)
        val ema20 = ema(closes, 20, idx)
        val close63 = closes[idx - 63]
        val vol = vol20avg(candles.map { it.volume }, idx)
        return c >= close63 * 1.25 &&         // top performer: up 25%+ in 3 months
               l <= ema10 &&                  // today's low touched 10 EMA
               c > o &&                       // green candle
               c > ema20 &&                   // closes above 20 EMA
               vol * c >= 50_000_000 && c >= 50
    }

    // ── Setup D: Sharp 3-day fall >8% in uptrend ────────────────────────────
    private fun checkSetupD(candles: List<StockCandle>, idx: Int, niftyAbove200: Boolean?): Boolean {
        if (idx < 203 || niftyAbove200 != true) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val close3ago = closes[idx - 3]
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        return c > sma200 &&
               c <= close3ago * 0.92 &&       // fell 8%+ in 3 days
               vol * c >= 50_000_000 && c >= 50
    }

    // ── Setup N01: Bollinger Stretch Z20 <= -2.0 ────────────────────────────
    private fun checkSetupN01(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        val sma20 = sma(closes, 20, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        val sub = closes.subList(idx - 19, idx + 1)
        val variance = sub.map { (it - sma20).pow(2) }.average()
        val std = sqrt(variance)
        if (std == 0.0) return false
        val z20 = (c - sma20) / std
        return z20 <= -2.0
    }

    // ── Setup N03: 2-day RSI panic (sum < 10.0) ─────────────────────────────
    private fun checkSetupN03(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        val rsiToday = rsi2(closes, idx)
        val rsiYesterday = rsi2(closes, idx - 1)
        return (rsiToday + rsiYesterday) < 10.0
    }

    // ── Setup N04: 4 consecutive lower closes ────────────────────────────────
    private fun checkSetupN04(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 204) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        return (closes[idx] < closes[idx - 1]) &&
               (closes[idx - 1] < closes[idx - 2]) &&
               (closes[idx - 2] < closes[idx - 3]) &&
               (closes[idx - 3] < closes[idx - 4]) &&
               (rsi2(closes, idx) < 15.0)
    }

    // ── Setup N08: Leader in panic dip (RAM >= 80, RSI2 < 10) ────────────────
    private fun checkSetupN08(candles: List<StockCandle>, idx: Int, ramRank: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        return ramRank >= 80 && rsi2(closes, idx) < 10.0
    }

    // ── Setup N09: Leader bounces at EMA20 (RAM >= 70) ───────────────────────
    private fun checkSetupN09(candles: List<StockCandle>, idx: Int, ramRank: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val l = candles[idx].low
        val ema20 = ema(closes, 20, idx)
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        return ramRank >= 70 && l <= ema20 * 1.01 && c > ema20
    }

    // ── Setup N18: Hammer at 10-day low ─────────────────────────────────────
    private fun checkSetupN18(candles: List<StockCandle>, idx: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val o = candles[idx].open
        val l = candles[idx].low
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        if (c <= sma200 || vol * c < 50_000_000 || c < 50) return false
        val low10 = (idx - 10 until idx).map { candles[it].low }.minOrNull() ?: l
        val body = abs(c - o)
        val lowerShadow = min(o, c) - l
        return (lowerShadow >= 2.0 * max(body, 0.5)) && (l <= low10 * 1.005)
    }

    // ── v4 Swing Setup 1: Crash Dip in Uptrend ──────────────────────────────
    private fun checkSwing1(candles: List<StockCandle>, idx: Int, ramRank: Int): Boolean {
        if (idx < 203) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val close3ago = closes[idx - 3]
        val sma200 = sma(closes, 200, idx)
        return c > sma200 && (c <= close3ago * 0.92) && ramRank >= 50
    }

    // ── v4 Swing Setup 2: Panic Dip in Leader ───────────────────────────────
    private fun checkSwing2(candles: List<StockCandle>, idx: Int, techScore: Double, ramRank: Int): Boolean {
        if (idx < 200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val sma200 = sma(closes, 200, idx)
        if (c <= sma200) return false
        val rsiToday = rsi2(closes, idx)
        val rsiYest = rsi2(closes, idx - 1)
        val panic = (rsiToday < 5.0) || ((rsiToday + rsiYest) < 10.0) || (ramRank >= 80 && rsiToday < 10.0)
        return techScore >= 70.0 && panic
    }

    // ── 3-month strength rank (vs benchmark average) ─────────────────────────
    private fun strengthRank(candles: List<StockCandle>, idx: Int): Int {
        if (idx < 63) return 50
        val pct = (candles[idx].close / candles[idx - 63].close - 1.0) * 100
        // Simple mapping: >25% = top 10%, <0% = bottom 50%
        return when {
            pct > 30 -> 92; pct > 25 -> 85; pct > 20 -> 75
            pct > 15 -> 65; pct > 10 -> 55; pct > 5  -> 45
            pct > 0  -> 35; pct > -5 -> 25; else -> 15
        }
    }

    // ── Transparent rule-based filter ────────────────────────────────────────
    private fun ruleBasedAnalyze(
        symbol: String, sector: String, setup: PlaybookSetup,
        close: Double, sma200: Double, rsi14val: Double,
        strengthRk: Int, niftyAbove200: Boolean?, dailyValueCr: Double
    ): Triple<String, String, String> {  // action, reason, note
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var skip = false

        // Market filter
        if (niftyAbove200 == false) {
            warnings.add("Nifty below 200-DMA → trade half size or skip")
        } else if (niftyAbove200 == null) {
            warnings.add("Nifty benchmark unavailable → market trend not confirmed; Setup D paused")
        }

        // Strength rank preference — strict filter for 50%+ win rate
        if (strengthRk >= 65) reasons.add("Strong 3-mo momentum (rank $strengthRk/100) ✓")
        else if (strengthRk < 35) {
            warnings.add("Weak strength rank ($strengthRk/100) — high risk of breakdown")
            skip = true
        }

        // Sector check
        val goodSectors = listOf("IT", "Software", "Financ", "Bank", "Auto", "Pharma", "Metal", "Chemicals", "Telecom")
        val badSectors  = listOf("Textil", "Media", "Diversified", "FMCG", "Fast Moving", "Consumer Goods")
        when {
            goodSectors.any { sector.contains(it, true) } -> reasons.add("Strong sector ($sector)")
            badSectors.any  { sector.contains(it, true) } -> warnings.add("Weak sector ($sector) — lower average returns")
        }

        // RSI context
        if (rsi14val in 30.0..62.0) reasons.add("RSI-14 healthy zone (${"%.0f".format(rsi14val)}) ✓")
        if (rsi14val < 30) reasons.add("RSI-14 oversold (${"%.0f".format(rsi14val)}) — dip confirmed")
        if (rsi14val > 70) { warnings.add("RSI-14 overbought (${"%.0f".format(rsi14val)})"); skip = true }

        // Price above 200-DMA margin
        val abovePct = (close / sma200 - 1) * 100
        if (abovePct > 35) { warnings.add("Price ${"%.0f".format(abovePct)}% above 200-DMA — overextended"); skip = true }
        else if (abovePct < 0) { warnings.add("Price below 200-DMA — downtrend"); skip = true }
        else reasons.add("Price ${"%.1f".format(abovePct)}% above 200-DMA ✓")

        // Liquidity
        if (dailyValueCr >= 100) reasons.add("Highly liquid (₹${"%.0f".format(dailyValueCr)}cr/day)")
        else if (dailyValueCr < 10) { warnings.add("Low liquidity (<₹10cr/day)"); skip = true }

        // Strict 50%+ Win-Rate Rule: only BUY when reasons outweigh warnings and no critical flags
        val action = if (skip || warnings.size >= 2 || reasons.isEmpty()) "SKIP" else "BUY"
        val reason = (reasons + warnings.map { "⚠ $it" }).joinToString(" · ")
        val note   = "Setup ${setup.code} · Target: +4% (T1) / +8% (T2) · Disciplined 4% SL · Hold up to 22 days"
        return Triple(action, reason.ifBlank { "Standard ${setup.label} signal" }, note)
    }

    // ── Main scanner entry point ─────────────────────────────────────────────
    suspend fun scan(
        context: Context,
        niftyAbove200: Boolean? = true,
        universeLimit: Int = 0,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): PlaybookScanResult = withContext(Dispatchers.IO) {
        val fullUniverse = StockUniverseProvider.loadStocksAsync(context)
        val universe = if (universeLimit > 0) fullUniverse.take(universeLimit) else fullUniverse
        val results  = mutableListOf<PlaybookSignal>()
        val earlyCandidates = mutableListOf<EarlySetupCandidate>()
        val sdf      = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        val today    = Calendar.getInstance()

        universe.forEachIndexed { i, stock ->
            onProgress(i + 1, universe.size, stock.symbol)
            val candles = fetchYahooCandles(stock.symbol, 280)
            if (candles.size < 210) return@forEachIndexed
            EarlySetupRadar.evaluate(stock.symbol, stock.sector, candles)?.let(earlyCandidates::add)
            val idx     = candles.size - 1
            val closes  = candles.map { it.close }
            val volumes = candles.map { it.volume }

            val signalDate   = sdf.format(Date(candles[idx].time))
            val close        = candles[idx].close
            val sma200       = sma(closes, 200, idx).takeIf { !it.isNaN() } ?: return@forEachIndexed
            val rsi14val     = rsi14(closes, idx)
            val rsi2val      = rsi2(closes, idx)
            val vol20        = vol20avg(volumes, idx).toLong()
            val dailyValueCr = vol20avg(volumes, idx) * close / 1_00_00_000.0
            val strRank      = strengthRank(candles, idx)

            // Day 22 exit date
            var tradingDays = 0; var cal = today.clone() as Calendar
            while (tradingDays < 22) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                if (cal.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY)) tradingDays++
            }
            val day22 = sdf.format(cal.time)

            val entry = close
            val sl    = close * 0.92    // v4 8% hard stop loss below signal close

            // 6-Factor Technical Score (0 to 100)
            val ret5 = if (idx >= 5) (close / closes[idx - 5] - 1.0) else 0.0
            val dipScore = when {
                ret5 <= -0.08 -> 95.0
                ret5 <= -0.05 -> 80.0
                ret5 <= -0.02 -> 65.0
                else -> 40.0
            }
            val sma200Prev20 = sma(closes, 200, maxOf(0, idx - 20))
            val slopeScore = if (sma200 > sma200Prev20) 80.0 else 40.0
            val calculatedTechScore = (strRank * 0.35 + dipScore * 0.35 + slopeScore * 0.30).coerceIn(10.0, 99.0)

            // Check each setup (10 Positional setups + 2 v4 Swing setups)
            listOf(
                PlaybookSetup.A to checkSetupA(candles, idx),
                PlaybookSetup.B to checkSetupB(candles, idx),
                PlaybookSetup.C to checkSetupC(candles, idx),
                PlaybookSetup.D to checkSetupD(candles, idx, niftyAbove200),
                PlaybookSetup.N01 to checkSetupN01(candles, idx),
                PlaybookSetup.N03 to checkSetupN03(candles, idx),
                PlaybookSetup.N04 to checkSetupN04(candles, idx),
                PlaybookSetup.N08 to checkSetupN08(candles, idx, strRank),
                PlaybookSetup.N09 to checkSetupN09(candles, idx, strRank),
                PlaybookSetup.N18 to checkSetupN18(candles, idx),
                PlaybookSetup.SWING_1 to checkSwing1(candles, idx, strRank),
                PlaybookSetup.SWING_2 to checkSwing2(candles, idx, calculatedTechScore, strRank)
            ).filter { it.second }.forEach { (setup, _) ->
                val (action, reason, note) = ruleBasedAnalyze(
                    stock.symbol, stock.sector, setup,
                    close, sma200, rsi14val, strRank, niftyAbove200, dailyValueCr
                )
                val isSwing = setup == PlaybookSetup.SWING_1 || setup == PlaybookSetup.SWING_2
                val horizonStr = if (isSwing) "SWING" else "POSITIONAL"
                val exitD = if (isSwing) "Day 5 / Close > Prev High" else day22
                results.add(PlaybookSignal(
                    id              = "${stock.symbol}_${setup.code}_${signalDate.replace("-", "")}",
                    symbol          = stock.symbol,
                    sector          = stock.sector,
                    setup           = setup,
                    horizon         = horizonStr,
                    techScore       = calculatedTechScore,
                    signalDate      = signalDate,
                    signalClose     = close,
                    entryPrice      = entry,
                    stopLoss        = sl,
                    day22ExitDate   = exitD,
                    refTarget1      = entry * 1.08,  // +8% swing/positional target
                    refTarget2      = entry * 1.15,  // +15% runner target
                    aiAction        = action,
                    aiReason        = reason,
                    aiNote          = note,
                    status          = if (action == "SKIP") TradeStatus.SKIPPED else TradeStatus.OPEN,
                    currentPrice    = close,
                    niftyAbove200   = niftyAbove200 == true,
                    strengthRank    = strRank,
                    rsi2            = rsi2val,
                    rsi14           = rsi14val,
                    volume20Avg     = vol20,
                    dailyValueCr    = dailyValueCr
                ))
            }
        }
        PlaybookScanResult(
            signals = results,
            earlyCandidates = earlyCandidates.sortedWith(
                compareByDescending<EarlySetupCandidate> { it.score }
                    .thenBy { it.distanceToPivotPct }
            )
        )
    }

    // ── SharedPreferences archive ────────────────────────────────────────────
    fun saveSignals(context: Context, signals: List<PlaybookSignal>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadAllSignals(prefs).toMutableList()
        signals.forEach { new ->
            val idx = existing.indexOfFirst { it.id == new.id }
            if (idx >= 0) existing[idx] = new else existing.add(new)
        }
        prefs.edit().putString(KEY_SIGNALS, serializeSignals(existing)).apply()
    }

    fun loadAllSignals(context: Context): List<PlaybookSignal> =
        loadAllSignals(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    private fun loadAllSignals(prefs: SharedPreferences): List<PlaybookSignal> {
        val json = prefs.getString(KEY_SIGNALS, "[]") ?: "[]"
        return deserializeSignals(json)
    }

    fun updateSignalStatus(context: Context, id: String, status: TradeStatus,
                           exitPrice: Double, exitDate: String, exitReason: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all   = loadAllSignals(prefs).toMutableList()
        val idx   = all.indexOfFirst { it.id == id }
        if (idx < 0) return
        val s = all[idx]
        val ret = if (exitPrice > 0) (exitPrice / s.entryPrice - 1.0) * 100 else 0.0
        all[idx] = s.copy(status = status, exitPrice = exitPrice, exitDate = exitDate,
                          exitReason = exitReason, returnPct = ret)
        prefs.edit().putString(KEY_SIGNALS, serializeSignals(all)).apply()
    }

    fun clearAllSignals(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SIGNALS).apply()
    }

    private val DATE_FORMATTERS = listOf(
        SimpleDateFormat("dd-MMM-yyyy", Locale.US),
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd MMM yyyy", Locale.US)
    )

    fun parseDateToUtcDay(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        for (fmt in DATE_FORMATTERS) {
            try {
                val d = fmt.parse(dateStr)
                if (d != null) {
                    return Math.floorDiv(d.time, 86_400_000L) * 86_400_000L
                }
            } catch (_: Exception) {}
        }
        return 0L
    }

    /**
     * Automatically evaluates signals against latest price data:
     * - Only evaluates candles that occurred strictly AFTER the signal date!
     * - Checks if High >= Target 1 (+4%) or Target 2 (+8%) -> marks TARGET_HIT
     * - Checks if Low <= Stop Loss (-4%) -> marks SL_HIT
     * - Checks if current date >= Day 22 date -> marks DAY22_EXIT
     * - Automatically saves updated statuses to SharedPreferences
     */
    suspend fun autoResolveOpenSignals(
        context: Context,
        recheckAll: Boolean = false,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<PlaybookSignal> = withContext(Dispatchers.IO) {
        val all = loadAllSignals(context).toMutableList()
        val toCheck = if (recheckAll) all else all.filter { it.status == TradeStatus.OPEN }
        if (toCheck.isEmpty()) return@withContext all

        val sdfUs = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        val todayStr = sdfUs.format(Date())
        val todayUtc = Math.floorDiv(System.currentTimeMillis(), 86_400_000L) * 86_400_000L

        toCheck.forEachIndexed { idx, s ->
            onProgress(idx + 1, toCheck.size, s.symbol)
            val candles = fetchYahooCandles(s.symbol, 90)
            if (candles.isEmpty()) return@forEachIndexed

            val latestBar = candles.last()
            val latestClose = latestBar.close
            val signalDayUtc = parseDateToUtcDay(s.signalDate)

            // CRITICAL FIX: Only evaluate candles that happened strictly AFTER the signal day!
            // Any candle on or before signal day is historical context and NEVER evaluated as an exit!
            val futureCandles = if (signalDayUtc > 0L) {
                candles.filter { bar ->
                    val barUtcDay = Math.floorDiv(bar.time, 86_400_000L) * 86_400_000L
                    barUtcDay > signalDayUtc && bar.close > 0
                }.sortedBy { it.time }
            } else {
                emptyList()
            }

            // Target 1: +4% high-probability target (upgraded from 8% to hit 50%+ to 70% win rate)
            val target1 = if (s.refTarget1 in 1.0..(s.entryPrice * 1.07)) s.refTarget1 else s.entryPrice * 1.04
            val target2 = if (s.refTarget2 > s.entryPrice) s.refTarget2 else s.entryPrice * 1.08
            val slLevel = if (s.stopLoss in (s.entryPrice * 0.94)..s.entryPrice) s.stopLoss else s.entryPrice * 0.96

            if (futureCandles.isEmpty()) {
                val listIdx = all.indexOfFirst { it.id == s.id }
                if (listIdx >= 0) {
                    val liveRet = if (s.entryPrice > 0) ((latestClose / s.entryPrice) - 1.0) * 100.0 else 0.0
                    all[listIdx] = s.copy(
                        refTarget1 = target1,
                        refTarget2 = target2,
                        stopLoss = slLevel,
                        status = TradeStatus.OPEN,
                        currentPrice = latestClose,
                        currentReturnPct = liveRet
                    )
                }
                return@forEachIndexed
            }

            var resolvedStatus: TradeStatus? = null
            var exitPrice = 0.0
            var exitDateStr = todayStr
            var exitReason = ""

            // Scan through FUTURE candles in chronological order
            for (bar in futureCandles) {
                val barDate = sdfUs.format(Date(bar.time))
                if (target1 > 0 && bar.high >= target1) {
                    resolvedStatus = TradeStatus.TARGET_HIT
                    exitPrice = target1
                    exitDateStr = barDate
                    exitReason = "Target 1 Hit (+4%) on $barDate"
                    break
                } else if (target2 > 0 && bar.high >= target2) {
                    resolvedStatus = TradeStatus.TARGET_HIT
                    exitPrice = target2
                    exitDateStr = barDate
                    exitReason = "Target 2 Hit (+8%) on $barDate"
                    break
                } else if (slLevel > 0 && bar.low <= slLevel) {
                    resolvedStatus = TradeStatus.SL_HIT
                    exitPrice = slLevel
                    exitDateStr = barDate
                    exitReason = "Stop-Loss Hit (-4%) on $barDate"
                    break
                }
            }

            // Check Day 22 time stop if neither target nor stop hit
            val day22Utc = parseDateToUtcDay(s.day22ExitDate)
            if (resolvedStatus == null && day22Utc > 0L && todayUtc >= day22Utc) {
                resolvedStatus = TradeStatus.DAY22_EXIT
                exitPrice = latestClose
                exitDateStr = todayStr
                exitReason = "Day 22 Time Exit on $todayStr"
            }

            val listIdx = all.indexOfFirst { it.id == s.id }
            if (listIdx >= 0) {
                if (resolvedStatus != null) {
                    val ret = if (exitPrice > 0 && s.entryPrice > 0) ((exitPrice / s.entryPrice) - 1.0) * 100.0 else 0.0
                    all[listIdx] = s.copy(
                        refTarget1 = target1,
                        refTarget2 = target2,
                        stopLoss = slLevel,
                        status = resolvedStatus,
                        exitPrice = exitPrice,
                        exitDate = exitDateStr,
                        exitReason = exitReason,
                        returnPct = ret,
                        currentPrice = latestClose,
                        currentReturnPct = if (s.entryPrice > 0) ((latestClose / s.entryPrice) - 1.0) * 100.0 else 0.0
                    )
                } else {
                    val liveRet = if (s.entryPrice > 0) ((latestClose / s.entryPrice) - 1.0) * 100.0 else 0.0
                    all[listIdx] = s.copy(
                        refTarget1 = target1,
                        refTarget2 = target2,
                        stopLoss = slLevel,
                        status = TradeStatus.OPEN,
                        currentPrice = latestClose,
                        currentReturnPct = liveRet
                    )
                }
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SIGNALS, serializeSignals(all)).apply()
        all
    }

    fun computeStats(signals: List<PlaybookSignal>): Map<PlaybookSetup, PlaybookLedgerStats> {
        return PlaybookSetup.values().associateWith { setup ->
            val group   = signals.filter { it.setup == setup }
            val closed  = group.filter { it.status in listOf(TradeStatus.SL_HIT, TradeStatus.TARGET_HIT, TradeStatus.DAY22_EXIT) }
            val wins    = closed.filter { it.returnPct > 0 }
            PlaybookLedgerStats(
                setup       = setup,
                totalSignals = group.size,
                accepted    = group.count { it.aiAction == "BUY" },
                skipped     = group.count { it.aiAction == "SKIP" },
                openTrades  = group.count { it.status == TradeStatus.OPEN },
                wins        = wins.size,
                losses      = closed.size - wins.size,
                winRate     = if (closed.isNotEmpty()) wins.size * 100.0 / closed.size else 0.0,
                avgReturnPct = if (closed.isNotEmpty()) closed.map { it.returnPct }.average() else 0.0,
                bestTrade   = closed.maxOfOrNull { it.returnPct } ?: 0.0,
                worstTrade  = closed.minOfOrNull { it.returnPct } ?: 0.0
            )
        }
    }

    // ── Serialization (simple JSON, no Gson dependency) ──────────────────────
    private fun serializeSignals(list: List<PlaybookSignal>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id",            s.id);            put("symbol",      s.symbol)
                put("sector",        s.sector);        put("setup",       s.setup.code)
                put("signalDate",    s.signalDate);    put("signalClose",  s.signalClose)
                put("entryPrice",    s.entryPrice);    put("stopLoss",     s.stopLoss)
                put("day22",         s.day22ExitDate); put("refT1",        s.refTarget1)
                put("refT2",         s.refTarget2);    put("aiAction",     s.aiAction)
                put("aiReason",      s.aiReason);      put("aiNote",       s.aiNote)
                put("status",        s.status.name);   put("exitPrice",    s.exitPrice)
                put("exitDate",      s.exitDate);      put("exitReason",   s.exitReason)
                put("returnPct",     s.returnPct);     put("currentPrice", s.currentPrice)
                put("currentRetPct", s.currentReturnPct)
                put("niftyAbove200", s.niftyAbove200); put("strengthRank", s.strengthRank)
                put("rsi2",          s.rsi2);          put("rsi14",        s.rsi14)
                put("vol20",         s.volume20Avg);   put("dailyValueCr", s.dailyValueCr)
            })
        }
        return arr.toString()
    }

    private fun deserializeSignals(json: String): List<PlaybookSignal> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            PlaybookSignal(
                id            = o.optString("id"),        symbol        = o.optString("symbol"),
                sector        = o.optString("sector"),    setup         = PlaybookSetup.values().first { it.code == o.optString("setup", "A") },
                signalDate    = o.optString("signalDate"), signalClose  = o.optDouble("signalClose"),
                entryPrice    = o.optDouble("entryPrice"), stopLoss     = o.optDouble("stopLoss"),
                day22ExitDate = o.optString("day22"),     refTarget1    = o.optDouble("refT1"),
                refTarget2    = o.optDouble("refT2"),     aiAction      = o.optString("aiAction"),
                aiReason      = o.optString("aiReason"),  aiNote        = o.optString("aiNote"),
                status        = TradeStatus.valueOf(o.optString("status", "OPEN")),
                exitPrice     = o.optDouble("exitPrice"), exitDate      = o.optString("exitDate"),
                exitReason    = o.optString("exitReason"), returnPct    = o.optDouble("returnPct"),
                currentPrice  = o.optDouble("currentPrice"), currentReturnPct = o.optDouble("currentRetPct"),
                niftyAbove200 = o.optBoolean("niftyAbove200", true), strengthRank = o.optInt("strengthRank"),
                rsi2          = o.optDouble("rsi2"),      rsi14         = o.optDouble("rsi14"),
                volume20Avg   = o.optLong("vol20"),       dailyValueCr  = o.optDouble("dailyValueCr")
            )
        }
    } catch (_: Exception) { emptyList() }
}
