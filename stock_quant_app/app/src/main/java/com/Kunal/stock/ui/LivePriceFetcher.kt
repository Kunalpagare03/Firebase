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
 *  SENSEX     -> ^BSESN
 *  FINNIFTY   -> ^CNXFIN
 *  INDIAVIX   -> ^INDIAVIX
 *  Stocks     -> SYMBOL.NS  (e.g. RELIANCE.NS, TCS.NS)
 */
object LivePriceFetcher {

    private const val BASE_URL =
        "https://query1.finance.yahoo.com/v7/finance/quote?symbols="

    // Cache: symbol -> (price, fetchTimeMs)
    private val priceCache = mutableMapOf<String, Pair<Double, Long>>()
    private const val CACHE_TTL_MS = 60_000L   // 1 minute cache

    /** Maps our internal symbol to Yahoo Finance ticker */
    fun toYahooSymbol(sym: String): String = when (sym.trim().uppercase()) {
        "NIFTY", "^NSEI"           -> "^NSEI"
        "BANKNIFTY", "^NSEBANK"    -> "^NSEBANK"
        "SENSEX", "^BSESN"         -> "^BSESN"
        "FINNIFTY", "^CNXFIN"      -> "^CNXFIN"
        "INDIAVIX", "^INDIAVIX"    -> "^INDIAVIX"
        "MIDCPNIFTY", "^NSEMDCP50" -> "^NSEMDCP50"
        "NIFTYNXT50"               -> "^NSMIDCP100"
        else -> if (sym.startsWith("^") || sym.endsWith(".NS") || sym.endsWith(".BO")) sym else "$sym.NS"
    }

    /**
     * Fetch live prices for a list of symbols.
     * Returns a map of symbol -> live price. Missing symbols are absent from map.
     */
    suspend fun fetchPrices(symbols: List<String>): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val result = mutableMapOf<String, Double>()
            val toFetch = mutableListOf<String>()

            // Serve from cache where fresh
            for (sym in symbols) {
                val cleanSym = sym.trim().uppercase()
                val cached = priceCache[cleanSym]
                if (cached != null && (now - cached.second) < CACHE_TTL_MS) {
                    result[cleanSym] = cached.first
                } else {
                    toFetch.add(cleanSym)
                }
            }

            if (toFetch.isEmpty()) return@withContext result

            // Build Yahoo symbols string (batch up to 10 at a time)
            toFetch.chunked(10).forEach { batch ->
                try {
                    val yahooSymbols = batch.joinToString(",") { toYahooSymbol(it) }
                    val urlStr = "$BASE_URL$yahooSymbols&fields=regularMarketPrice,regularMarketChangePercent,shortName"
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
                            val ourSym = batch.firstOrNull { toYahooSymbol(it).equals(yahooTicker, ignoreCase = true) }
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
                    // Fallback to cache / default
                }
            }
            result
        }

    /** Fetch a single symbol price (suspending). Returns null on failure. */
    suspend fun fetchPrice(symbol: String): Double? =
        fetchPrices(listOf(symbol))[symbol.trim().uppercase()]

    /** Clear price cache (call on app resume to force refresh). */
    fun clearCache() = priceCache.clear()

    // ── Fallback baseline prices ──────────────────────────────────────────
    val fallbackPrices: Map<String, Double> = mapOf(
        "NIFTY"      to 24150.0, "^NSEI" to 24150.0,
        "BANKNIFTY"  to 51850.0, "^NSEBANK" to 51850.0,
        "SENSEX"     to 79450.0, "^BSESN" to 79450.0,
        "FINNIFTY"   to 23900.0,
        "INDIAVIX"   to 14.5,    "^INDIAVIX" to 14.5,
        "TITAN"      to 3380.0,  "TCS" to 4120.0,
        "PIDILITIND" to 3050.0,  "HAL" to 4420.0,
        "BEL"        to 288.5,   "TRENT" to 2840.0,
        "RELIANCE"   to 1260.0,  "INFY" to 1840.0,
        "HDFCBANK"   to 1750.0,  "ICICIBANK" to 1280.0,
        "ASIANPAINT" to 2890.0,  "POLYCAB" to 6750.0,
        "VBL"        to 1560.0,  "DIVISLAB" to 5850.0
    )

    fun getBestPrice(symbol: String): Double {
        val clean = symbol.trim().uppercase()
        val cached = priceCache[clean]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL_MS) {
            return cached.first
        }
        return fallbackPrices[clean] ?: 1000.0
    }
}
