package com.Kunal.stock.ui

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * LivePriceFetcher — fetches real-time NSE prices via Yahoo Finance v8 chart endpoint.
 *
 * Yahoo Finance symbol mapping:
 *  NIFTY      -> ^NSEI
 *  BANKNIFTY  -> ^NSEBANK
 *  SENSEX     -> ^BSESN
 *  INDIAVIX   -> ^INDIAVIX
 *  FINNIFTY   -> ^CNXFIN
 *  MIDCPNIFTY -> ^NSEMDCP50
 *  Stocks     -> SYMBOL.NS  (e.g. RELIANCE.NS)
 */
object LivePriceFetcher {

    // Cache: symbol -> (price, fetchTimeMs)
    private val priceCache = mutableMapOf<String, Pair<Double, Long>>()
    private const val CACHE_TTL_MS = 30_000L   // 30 seconds cache

    /** Maps our internal symbol to Yahoo Finance ticker */
    fun toYahooSymbol(sym: String): String {
        val clean = sym.trim().removeSuffix(".NS")
        return when (clean.uppercase()) {
            "NIFTY", "^NSEI"            -> "^NSEI"
            "BANKNIFTY", "^NSEBANK"     -> "^NSEBANK"
            "SENSEX", "^BSESN"          -> "^BSESN"
            "INDIAVIX", "INDIA_VIX", "VIX", "^INDIAVIX" -> "^INDIAVIX"
            "FINNIFTY", "CNXFIN"        -> "^CNXFIN"
            "MIDCPNIFTY", "^NSEMDCP50"  -> "^NSEMDCP50"
            "NIFTYNXT50"                -> "^NSMIDCP100"
            else                        -> "$clean.NS"
        }
    }

    private fun fetchSingleSymbolLive(symbol: String): Double? {
        val yahooSym = toYahooSymbol(symbol)
        val endpoints = listOf(
            "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSym?interval=1m&range=1d",
            "https://query2.finance.yahoo.com/v8/finance/chart/$yahooSym?interval=1m&range=1d"
        )
        for (urlStr in endpoints) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout    = 4000
                    requestMethod  = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val root = JSONObject(body)
                    val resultArr = root.optJSONObject("chart")?.optJSONArray("result")
                    if (resultArr != null && resultArr.length() > 0) {
                        val meta = resultArr.getJSONObject(0).optJSONObject("meta")
                        val price = meta?.optDouble("regularMarketPrice", Double.NaN) ?: Double.NaN
                        if (!price.isNaN() && price > 0.0) {
                            return price
                        }
                        val prevClose = meta?.optDouble("chartPreviousClose", Double.NaN) ?: Double.NaN
                        if (!prevClose.isNaN() && prevClose > 0.0) {
                            return prevClose
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        return null
    }

    /**
     * Fetch live prices for a list of symbols in parallel using coroutines.
     * Returns a map of symbol -> live price.
     */
    suspend fun fetchPrices(symbols: List<String>): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val result = mutableMapOf<String, Double>()
            val toFetch = mutableListOf<String>()

            for (sym in symbols) {
                val cached = priceCache[sym]
                if (cached != null && (now - cached.second) < CACHE_TTL_MS) {
                    result[sym] = cached.first
                } else {
                    toFetch.add(sym)
                }
            }

            if (toFetch.isEmpty()) return@withContext result

            // Parallel async requests
            val deferreds = toFetch.map { sym ->
                async {
                    val price = fetchSingleSymbolLive(sym)
                    if (price != null && price > 0.0) {
                        sym to price
                    } else null
                }
            }
            deferreds.awaitAll().filterNotNull().forEach { (sym, price) ->
                result[sym] = price
                priceCache[sym] = price to System.currentTimeMillis()
            }
            result
        }

    /** Fetch a single symbol price (suspending). Returns null on failure. */
    suspend fun fetchPrice(symbol: String): Double? =
        fetchPrices(listOf(symbol))[symbol]

    /** Clear price cache (call on app resume to force refresh). */
    fun clearCache() = priceCache.clear()

    // ── Fallback 2026 realistic baseline prices (used when completely offline) ──────────────
    val fallbackPrices: Map<String, Double> = mapOf(
        "NIFTY"      to 25518.0, "BANKNIFTY"  to 63260.0, "SENSEX"     to 82970.0,
        "FINNIFTY"   to 24800.0, "MIDCPNIFTY" to 13400.0, "NIFTYNXT50" to 73200.0,
        "INDIAVIX"   to 13.8,    "INDIA_VIX"  to 13.8,
        "RELIANCE"   to 1357.0,  "TCS"        to 2460.0,  "INFY"       to 1106.0,
        "HDFCBANK"   to 827.0,   "ICICIBANK"  to 1607.0,  "KOTAKBANK"  to 1880.0,
        "AXISBANK"   to 1190.0,  "SBIN"       to 1140.0,  "BANKBARODA" to 255.0,
        "LT"         to 3680.0,  "LTIM"       to 5800.0,  "WIPRO"      to 540.0,
        "HCLTECH"    to 1950.0,  "TECHM"      to 1690.0,  "MPHASIS"    to 2950.0,
        "TATAMOTORS" to 820.0,   "MARUTI"     to 11600.0, "M&M"        to 3100.0,
        "BAJAJ-AUTO" to 10100.0, "EICHERMOT"  to 5100.0,  "HEROMOTOCO" to 4980.0,
        "TATASTEEL"  to 146.0,   "JSWSTEEL"   to 980.0,   "HINDALCO"   to 690.0,
        "COALINDIA"  to 420.0,   "NTPC"       to 395.0,   "POWERGRID"  to 345.0,
        "ONGC"       to 290.0,   "BPCL"       to 345.0,   "IOC"        to 168.0,
        "BHARTIARTL" to 1680.0,  "IDEA"       to 10.5,    "HFCL"       to 160.0,
        "SUNPHARMA"  to 1980.0,  "DRREDDY"    to 6650.0,  "CIPLA"      to 1630.0,
        "DIVISLAB"   to 5450.0,  "BIOCON"     to 340.0,   "AUROPHARMA" to 1350.0,
        "BAJFINANCE" to 925.0,   "BAJAJFINSV" to 1760.0,  "HDFC"       to 2850.0,
        "CHOLAFIN"   to 1490.0,  "MUTHOOTFIN" to 2150.0,  "SBILIFE"    to 1720.0,
        "HINDUNILVR" to 2480.0,  "ITC"        to 478.0,   "NESTLEIND"  to 2320.0,
        "BRITANNIA"  to 5550.0,  "MARICO"     to 630.0,   "DABUR"      to 565.0,
        "ASIANPAINT" to 3020.0,  "BERGERPAINTS" to 595.0, "PIDILITIND" to 3050.0,
        "GRASIM"     to 2720.0,  "ULTRACEMCO" to 11300.0, "SHREECEM"   to 25200.0,
        "AMBUJACEM"  to 590.0,   "ACC"        to 2120.0,
        "ADANIENT"   to 2980.0,  "ADANIPORTS" to 1510.0,  "ADANIPOWER" to 580.0,
        "HAVELLS"    to 1870.0,  "VOLTAS"     to 1650.0,  "DIXON"      to 16800.0,
        "TITAN"      to 3550.0,  "KALYANKJIL" to 770.0,   "TRENT"      to 7300.0,
        "APOLLOHOSP" to 7150.0,  "MAXHEALTH"  to 1090.0,  "FORTIS"     to 690.0,
        "PFC"        to 495.0,   "RECLTD"     to 550.0,   "IRFC"       to 220.0,
        "ZOMATO"     to 290.0,   "POLICYBZR"  to 1880.0,  "PAYTM"      to 1080.0,
        "NYKAA"      to 215.0,   "DELHIVERY"  to 460.0,   "BEL"        to 310.0,
        "HAL"        to 4400.0
    )

    fun getCachedPrice(symbol: String): Double? {
        val now = System.currentTimeMillis()
        val cached = priceCache[symbol]
        return if (cached != null && (now - cached.second) < CACHE_TTL_MS) cached.first else null
    }

    fun getBestPrice(symbol: String, sector: String = ""): Double {
        getCachedPrice(symbol)?.let { return it }
        val clean = symbol.trim().removeSuffix(".NS").uppercase()
        fallbackPrices[clean]?.let { return it }
        fallbackPrices[symbol]?.let { return it }
        val h = kotlin.math.abs(clean.hashCode())
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
