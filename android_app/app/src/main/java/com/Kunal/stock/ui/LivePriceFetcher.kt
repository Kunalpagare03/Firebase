package com.Kunal.stock.ui

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * LivePriceFetcher — fetches real-time NSE prices via Yahoo Finance (no API key needed).
 *
 * Yahoo Finance symbol mapping:
 *  NIFTY      -> ^NSEI
 *  BANKNIFTY  -> ^NSEBANK
 *  FINNIFTY   -> ^CNXFIN
 *  MIDCPNIFTY -> ^NSEMDCP50
 *  Stocks     -> SYMBOL.NS  (e.g. RELIANCE.NS)
 */
object LivePriceFetcher {

    private const val BASE_URL =
        "https://query1.finance.yahoo.com/v7/finance/quote?symbols="

    // Cache: symbol -> (price, fetchTimeMs)
    private val priceCache = mutableMapOf<String, Pair<Double, Long>>()
    private const val CACHE_TTL_MS = 60_000L   // 1 minute cache

    /** Maps our internal symbol to Yahoo Finance ticker */
    fun toYahooSymbol(sym: String): String = when (sym) {
        "NIFTY"      -> "^NSEI"
        "BANKNIFTY"  -> "^NSEBANK"
        "FINNIFTY"   -> "^CNXFIN"
        "MIDCPNIFTY" -> "^NSEMDCP50"
        "NIFTYNXT50" -> "^NSMIDCP100"
        else         -> "$sym.NS"
    }

    /**
     * Fetch live prices for a list of symbols.
     * Returns a map of symbol -> live price. Missing symbols are absent from map.
     * Uses cache to avoid hammering Yahoo.
     */
    suspend fun fetchPrices(symbols: List<String>): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val result = mutableMapOf<String, Double>()
            val toFetch = mutableListOf<String>()

            // Serve from cache where fresh
            for (sym in symbols) {
                val cached = priceCache[sym]
                if (cached != null && (now - cached.second) < CACHE_TTL_MS) {
                    result[sym] = cached.first
                } else {
                    toFetch.add(sym)
                }
            }

            if (toFetch.isEmpty()) return@withContext result

            // Build Yahoo symbols string (batch up to 10 at a time)
            toFetch.chunked(10).forEach { batch ->
                try {
                    val yahooSymbols = batch.joinToString(",") { toYahooSymbol(it) }
                    val urlStr = "$BASE_URL$yahooSymbols&fields=regularMarketPrice,shortName"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout    = 6000
                        requestMethod  = "GET"
                        setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val quotes = JSONObject(body)
                            .getJSONObject("quoteResponse")
                            .getJSONArray("result")
                        for (i in 0 until quotes.length()) {
                            val q = quotes.getJSONObject(i)
                            val yahooTicker = q.optString("symbol", "")
                            // Reverse map yahoo -> our symbol
                            val ourSym = batch.firstOrNull { toYahooSymbol(it) == yahooTicker }
                                ?: continue
                            val price = q.optDouble("regularMarketPrice", Double.NaN)
                            if (!price.isNaN() && price > 0) {
                                result[ourSym] = price
                                priceCache[ourSym] = price to System.currentTimeMillis()
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    // Network unavailable — return whatever we have (cache or fallback)
                }
            }
            result
        }

    /** Fetch a single symbol price (suspending). Returns null on failure. */
    suspend fun fetchPrice(symbol: String): Double? =
        fetchPrices(listOf(symbol))[symbol]

    /** Clear price cache (call on app resume to force refresh). */
    fun clearCache() = priceCache.clear()

    // ── Fallback hardcoded prices (used when network unavailable) ──────────────
    val fallbackPrices: Map<String, Double> = mapOf(
        "NIFTY"      to 22699.0, "BANKNIFTY"  to 54267.0, "FINNIFTY"   to 23100.0,
        "MIDCPNIFTY" to 12100.0, "NIFTYNXT50" to 70800.0,
        "RELIANCE"   to 1158.0,  "TCS"        to 4180.0,  "INFY"       to 1820.0,
        "HDFCBANK"   to 1780.0,  "ICICIBANK"  to 1350.0,  "KOTAKBANK"  to 1820.0,
        "AXISBANK"   to 1180.0,  "SBIN"       to 820.0,   "BANKBARODA" to 245.0,
        "LT"         to 3610.0,  "LTIM"       to 5700.0,  "WIPRO"      to 560.0,
        "HCLTECH"    to 1920.0,  "TECHM"      to 1680.0,  "MPHASIS"    to 2900.0,
        "TATAMOTORS" to 414.0,   "MARUTI"     to 11579.0, "M&M"        to 3150.0,
        "BAJAJ-AUTO" to 10200.0, "EICHERMOT"  to 5050.0,  "HEROMOTOCO" to 4950.0,
        "TATASTEEL"  to 142.0,   "JSWSTEEL"   to 960.0,   "HINDALCO"   to 685.0,
        "COALINDIA"  to 415.0,   "NTPC"       to 390.0,   "POWERGRID"  to 340.0,
        "ONGC"       to 285.0,   "BPCL"       to 340.0,   "IOC"        to 165.0,
        "BHARTIARTL" to 1650.0,  "IDEA"       to 11.5,    "HFCL"       to 165.0,
        "SUNPHARMA"  to 1970.0,  "DRREDDY"    to 6600.0,  "CIPLA"      to 1620.0,
        "DIVISLAB"   to 5400.0,  "BIOCON"     to 345.0,   "AUROPHARMA" to 1340.0,
        "BAJFINANCE" to 917.0,   "BAJAJFINSV" to 1750.0,  "HDFC"       to 2800.0,
        "CHOLAFIN"   to 1480.0,  "MUTHOOTFIN" to 2100.0,  "SBILIFE"    to 1700.0,
        "HINDUNILVR" to 2450.0,  "ITC"        to 475.0,   "NESTLEIND"  to 2300.0,
        "BRITANNIA"  to 5500.0,  "MARICO"     to 620.0,   "DABUR"      to 560.0,
        "ASIANPAINT" to 3000.0,  "BERGERPAINTS" to 590.0, "PIDILITIND" to 3000.0,
        "GRASIM"     to 2700.0,  "ULTRACEMCO" to 11200.0, "SHREECEM"   to 25000.0,
        "AMBUJACEM"  to 580.0,   "ACC"        to 2100.0,
        "ADANIENT"   to 2950.0,  "ADANIPORTS" to 1490.0,  "ADANIPOWER" to 575.0,
        "HAVELLS"    to 1850.0,  "VOLTAS"     to 1640.0,  "DIXON"      to 16500.0,
        "TITAN"      to 3500.0,  "KALYANKJIL" to 760.0,   "TRENT"      to 7200.0,
        "APOLLOHOSP" to 7100.0,  "MAXHEALTH"  to 1080.0,  "FORTIS"     to 680.0,
        "PFC"        to 490.0,   "RECLTD"     to 545.0,   "IRFC"       to 215.0,
        "ZOMATO"     to 285.0,   "POLICYBZR"  to 1850.0,  "PAYTM"      to 1050.0,
        "NYKAA"      to 210.0,   "DELHIVERY"  to 450.0
    )

    /** Get price: live if cached, else null (caller should trigger async fetch). */
    fun getCachedPrice(symbol: String): Double? {
        val now = System.currentTimeMillis()
        val cached = priceCache[symbol]
        return if (cached != null && (now - cached.second) < CACHE_TTL_MS) cached.first else null
    }

    /** Best available price: cached live > fallback hardcoded > hash-based fallback */
    fun getBestPrice(symbol: String, sector: String = ""): Double {
        getCachedPrice(symbol)?.let { return it }
        fallbackPrices[symbol]?.let { return it }
        // sector hash fallback
        val h = kotlin.math.abs(symbol.hashCode())
        return when {
            sector.contains("Bank", ignoreCase = true)    -> (h % 800 + 400).toDouble()
            sector.contains("IT", ignoreCase = true)      -> (h % 3000 + 800).toDouble()
            sector.contains("Pharma", ignoreCase = true)  -> (h % 1500 + 400).toDouble()
            sector.contains("Auto", ignoreCase = true)    -> (h % 4000 + 600).toDouble()
            sector.contains("Metal", ignoreCase = true)   -> (h % 300 + 100).toDouble()
            sector.contains("Energy", ignoreCase = true)  -> (h % 400 + 150).toDouble()
            sector.contains("FMCG", ignoreCase = true)    -> (h % 2000 + 400).toDouble()
            sector.contains("Finance", ignoreCase = true) -> (h % 2000 + 500).toDouble()
            else -> (h % 2000 + 300).toDouble()
        }
    }
}
