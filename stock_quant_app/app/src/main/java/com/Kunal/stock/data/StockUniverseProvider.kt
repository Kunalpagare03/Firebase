package com.Kunal.stock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class StockUniverseItem(
    val name: String,
    val symbol: String,
    val sector: String
)

object StockUniverseProvider {
    @Volatile
    private var cachedStocks: List<StockUniverseItem>? = null
    @Volatile
    private var cachedSectors: List<String>? = null

    // Fallback benchmark stocks
    val benchmarkSymbols = listOf(
        StockUniverseItem("Reliance Industries", "RELIANCE", "Energy"),
        StockUniverseItem("Tata Consultancy Services", "TCS", "Software Services"),
        StockUniverseItem("Infosys", "INFOSYS", "Software Services"),
        StockUniverseItem("HDFC Bank", "HDFCBANK", "Financial services"),
        StockUniverseItem("ICICI Bank", "ICICIBANK", "Financial services"),
        StockUniverseItem("Tata Motors", "TATAMOTORS", "Automobile"),
        StockUniverseItem("Bharti Airtel", "BHARTIARTL", "Telecom"),
        StockUniverseItem("State Bank of India", "SBIN", "Financial services"),
        StockUniverseItem("Larsen & Toubro", "LT", "Engineering & Capital Goods"),
        StockUniverseItem("ITC", "ITC", "FMCG")
    )

    suspend fun loadStocksAsync(context: Context): List<StockUniverseItem> = withContext(Dispatchers.IO) {
        cachedStocks?.let { return@withContext it }
        val list = mutableListOf<StockUniverseItem>()
        try {
            context.assets.open("stocks_universe.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val jsonArray = JSONArray(jsonString)
                val len = jsonArray.length()
                for (i in 0 until len) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        StockUniverseItem(
                            name = obj.optString("name", ""),
                            symbol = obj.optString("symbol", ""),
                            sector = obj.optString("sector", "Other")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext benchmarkSymbols
        }
        cachedStocks = list
        cachedSectors = listOf("ALL") + list.map { it.sector }.distinct().sorted()
        list
    }

    fun getCachedStocks(): List<StockUniverseItem> = cachedStocks ?: benchmarkSymbols

    fun getCachedSectors(): List<String> = cachedSectors ?: listOf("ALL")
}
