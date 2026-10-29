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
 * PlaybookScanner — runs all 4 backtested setups (A/B/C/D) on Nifty 500 stocks.
 *
 * Price data: Yahoo Finance (free, no key). Each stock fetched with 1-year daily candles.
 * Conditions evaluated exactly as per "Stock Swing & Positional Playbook" 2012-2026 data.
 */
object PlaybookScanner {

    private const val PREFS_NAME = "playbook_prefs"
    private const val KEY_SIGNALS = "saved_signals"
    private const val YAHOO_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"

    // ── Yahoo Finance helper ────────────────────────────────────────────────
    private suspend fun fetchYahooCandles(symbol: String, days: Int = 300): List<StockCandle> =
        withContext(Dispatchers.IO) {
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
                parseYahooCandles(body)
            } catch (_: Exception) { emptyList() }
        }

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
    private fun checkSetupD(candles: List<StockCandle>, idx: Int, niftyAbove200: Boolean): Boolean {
        if (idx < 203 || !niftyAbove200) return false
        val closes = candles.map { it.close }
        val c = closes[idx]
        val close3ago = closes[idx - 3]
        val sma200 = sma(closes, 200, idx)
        val vol = vol20avg(candles.map { it.volume }, idx)
        return c > sma200 &&
               c <= close3ago * 0.92 &&       // fell 8%+ in 3 days
               vol * c >= 50_000_000 && c >= 50
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

    // ── AI Agent filter ──────────────────────────────────────────────────────
    private fun aiAgentAnalyze(
        symbol: String, sector: String, setup: PlaybookSetup,
        close: Double, sma200: Double, rsi14val: Double,
        strengthRk: Int, niftyAbove200: Boolean, dailyValueCr: Double
    ): Triple<String, String, String> {  // action, reason, note
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var skip = false

        // Market filter
        if (!niftyAbove200) {
            warnings.add("Nifty below 200-DMA → trade half size or skip")
        }

        // Strength rank preference
        if (strengthRk >= 70) reasons.add("Strong 3-mo momentum (rank $strengthRk/100)")
        else if (strengthRk < 20 && setup != PlaybookSetup.A) {
            warnings.add("Weak strength rank ($strengthRk/100) — Setup ${setup.code} works better on leaders")
        }

        // Sector check
        val goodSectors = listOf("IT", "Software", "Financ", "Bank", "Auto", "Pharma", "Metal", "Chemicals", "Telecom")
        val badSectors  = listOf("Textil", "Media", "Diversified", "FMCG", "Fast Moving", "Consumer Goods")
        when {
            goodSectors.any { sector.contains(it, true) } -> reasons.add("Strong sector ($sector)")
            badSectors.any  { sector.contains(it, true) } -> warnings.add("Weak sector ($sector) — lower average returns")
        }

        // RSI context
        if (rsi14val < 30) reasons.add("RSI-14 oversold (${"%.0f".format(rsi14val)}) — dip confirmed")
        if (rsi14val > 70) { warnings.add("RSI-14 overbought (${"%.0f".format(rsi14val)})"); if (setup == PlaybookSetup.A) skip = true }

        // Price above 200-DMA margin
        val abovePct = (close / sma200 - 1) * 100
        if (abovePct > 40) { warnings.add("Price ${"%.0f".format(abovePct)}% above 200-DMA — stretched") }
        else reasons.add("Price ${"%.1f".format(abovePct)}% above 200-DMA ✓")

        // Liquidity
        if (dailyValueCr >= 100) reasons.add("Highly liquid (₹${"%.0f".format(dailyValueCr)}cr/day)")

        val action = if (skip || (warnings.size >= 3 && reasons.isEmpty())) "SKIP" else "BUY"
        val reason = (reasons + warnings.map { "⚠ $it" }).joinToString(" · ")
        val note   = "Setup ${setup.code} [PROVEN ${if (setup.proven) "✅" else "⚠"}] · Win ${setup.winRate}% · Avg +${setup.avgPct}%/trade · Hold up to 22 days · Risk 1% capital (8% SL → 12.5% position)"
        return Triple(action, reason.ifBlank { "Standard ${setup.label} signal" }, note)
    }

    // ── Main scanner entry point ─────────────────────────────────────────────
    suspend fun scan(
        context: Context,
        niftyAbove200: Boolean = true,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<PlaybookSignal> = withContext(Dispatchers.IO) {
        val universe = StockUniverseProvider.loadStocksAsync(context)
        val results  = mutableListOf<PlaybookSignal>()
        val sdf      = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val today    = Calendar.getInstance()

        universe.forEachIndexed { i, stock ->
            onProgress(i + 1, universe.size, stock.symbol)
            val candles = fetchYahooCandles(stock.symbol, 280)
            if (candles.size < 210) return@forEachIndexed
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

            val entry = close * 1.003   // estimated next-day open (+0.3% gap)
            val sl    = close * 0.92    // 8% below signal close

            // Check each setup
            listOf(
                PlaybookSetup.A to checkSetupA(candles, idx),
                PlaybookSetup.B to checkSetupB(candles, idx),
                PlaybookSetup.C to checkSetupC(candles, idx),
                PlaybookSetup.D to checkSetupD(candles, idx, niftyAbove200)
            ).filter { it.second }.forEach { (setup, _) ->
                val (action, reason, note) = aiAgentAnalyze(
                    stock.symbol, stock.sector, setup,
                    close, sma200, rsi14val, strRank, niftyAbove200, dailyValueCr
                )
                results.add(PlaybookSignal(
                    id              = "${stock.symbol}_${setup.code}_${signalDate.replace("-", "")}",
                    symbol          = stock.symbol,
                    sector          = stock.sector,
                    setup           = setup,
                    signalDate      = signalDate,
                    signalClose     = close,
                    entryPrice      = entry,
                    stopLoss        = sl,
                    day22ExitDate   = day22,
                    refTarget1      = entry * 1.08,
                    refTarget2      = entry * 1.15,
                    aiAction        = action,
                    aiReason        = reason,
                    aiNote          = note,
                    status          = if (action == "SKIP") TradeStatus.SKIPPED else TradeStatus.OPEN,
                    currentPrice    = close,
                    niftyAbove200   = niftyAbove200,
                    strengthRank    = strRank,
                    rsi2            = rsi2val,
                    rsi14           = rsi14val,
                    volume20Avg     = vol20,
                    dailyValueCr    = dailyValueCr
                ))
            }
        }
        results
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
