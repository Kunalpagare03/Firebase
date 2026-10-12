package com.Kunal.stock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class FnoUniverseItem(
    val name: String,
    val symbol: String,
    val sector: String,
    val lotSize: Int
)

object FnoUniverseProvider {
    @Volatile
    private var cachedFno: List<FnoUniverseItem>? = null
    @Volatile
    private var cachedSegments: List<String>? = null

    // High-Liquidity Benchmark F&O Assets
    val benchmarkFno = listOf(
        FnoUniverseItem("NIFTY 50 INDEX", "NIFTY", "Index", 25),
        FnoUniverseItem("BANK NIFTY INDEX", "BANKNIFTY", "Index", 15),
        FnoUniverseItem("FINANCIAL SERVICES", "FINNIFTY", "Index", 25),
        FnoUniverseItem("MIDCAP NIFTY", "MIDCPNIFTY", "Index", 50),
        FnoUniverseItem("Reliance Industries", "RELIANCE", "F&O Stock", 250),
        FnoUniverseItem("HDFC Bank", "HDFCBANK", "F&O Stock", 550),
        FnoUniverseItem("ICICI Bank", "ICICIBANK", "F&O Stock", 700),
        FnoUniverseItem("Tata Motors", "TATAMOTORS", "F&O Stock", 1425),
        FnoUniverseItem("Infosys", "INFY", "F&O Stock", 400),
        FnoUniverseItem("Tata Consultancy Services", "TCS", "F&O Stock", 175),
        FnoUniverseItem("State Bank of India", "SBIN", "F&O Stock", 1500),
        FnoUniverseItem("Bharti Airtel", "BHARTIARTL", "F&O Stock", 950),
        FnoUniverseItem("Larsen & Toubro", "LT", "F&O Stock", 300),
        FnoUniverseItem("Bajaj Finance", "BAJFINANCE", "F&O Stock", 125),
        FnoUniverseItem("Axis Bank", "AXISBANK", "F&O Stock", 625),
        FnoUniverseItem("Maruti Suzuki", "MARUTI", "F&O Stock", 50),
        FnoUniverseItem("Tata Steel", "TATASTEEL", "F&O Stock", 5500)
    )

    suspend fun loadFnoAsync(context: Context): List<FnoUniverseItem> = withContext(Dispatchers.IO) {
        cachedFno?.let { return@withContext it }
        val list = mutableListOf<FnoUniverseItem>()
        try {
            context.assets.open("fno_universe.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val jsonArray = JSONArray(jsonString)
                val len = jsonArray.length()
                for (i in 0 until len) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        FnoUniverseItem(
                            name = obj.optString("name", ""),
                            symbol = obj.optString("symbol", ""),
                            sector = obj.optString("sector", "F&O Stock"),
                            lotSize = obj.optInt("lotSize", 500)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext benchmarkFno
        }
        cachedFno = list
        cachedSegments = listOf("ALL", "Index", "F&O Stock")
        list
    }

    fun getCachedFno(): List<FnoUniverseItem> = cachedFno ?: benchmarkFno

    fun getCachedSegments(): List<String> = cachedSegments ?: listOf("ALL", "Index", "F&O Stock")
}
