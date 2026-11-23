package com.Kunal.stock.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class JournalPnlSummary(
    val dailyPnlRs: Double,
    val weeklyPnlRs: Double
)

internal object OptionTradeJournalStore {
    private const val PREFS_NAME = "option_trade_journal"
    private const val ENTRIES_KEY = "entries"
    private val indiaTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun load(context: Context): List<JournalEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENTRIES_KEY, null) ?: return emptyList()
        val stored = JSONArray(raw)
        return (0 until stored.length()).mapNotNull { index ->
            val item = stored.optJSONObject(index) ?: return@mapNotNull null
            JournalEntry(
                id = item.optInt("id"),
                date = item.optString("date"),
                setup = item.optString("setup"),
                entryTime = item.optString("entryTime"),
                side = item.optString("side"),
                strike = item.optString("strike"),
                buyPrice = item.optString("buyPrice"),
                indexEntry = item.optString("indexEntry"),
                stop = item.optString("stop"),
                target = item.optString("target"),
                exitTime = item.optString("exitTime"),
                exitReason = item.optString("exitReason"),
                pnlRs = item.optString("pnlRs"),
                followedRules = item.optBoolean("followedRules")
            )
        }
    }

    fun save(context: Context, entries: List<JournalEntry>) {
        val stored = JSONArray()
        entries.forEach { entry ->
            stored.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("date", entry.date)
                    .put("setup", entry.setup)
                    .put("entryTime", entry.entryTime)
                    .put("side", entry.side)
                    .put("strike", entry.strike)
                    .put("buyPrice", entry.buyPrice)
                    .put("indexEntry", entry.indexEntry)
                    .put("stop", entry.stop)
                    .put("target", entry.target)
                    .put("exitTime", entry.exitTime)
                    .put("exitReason", entry.exitReason)
                    .put("pnlRs", entry.pnlRs)
                    .put("followedRules", entry.followedRules)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ENTRIES_KEY, stored.toString())
            .apply()
    }

    fun currentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = indiaTimeZone }
            .format(Date())

    fun isValidDate(value: String): Boolean = parseDate(
        value,
        Calendar.getInstance(indiaTimeZone).get(Calendar.YEAR)
    ) != null

    fun pnlSummary(entries: List<JournalEntry>, nowMillis: Long = System.currentTimeMillis()): JournalPnlSummary {
        val now = Calendar.getInstance(indiaTimeZone).apply { timeInMillis = nowMillis }
        val today = dateOnly(now)
        val weekStart = (now.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var dailyPnl = 0.0
        var weeklyPnl = 0.0
        entries.forEach { entry ->
            val pnl = entry.pnlRs.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@forEach
            val tradeDate = parseDate(entry.date, now.get(Calendar.YEAR)) ?: return@forEach
            if (tradeDate == today) dailyPnl += pnl
            if (tradeDate in weekStart..today) weeklyPnl += pnl
        }
        return JournalPnlSummary(dailyPnl, weeklyPnl)
    }

    private fun dateOnly(calendar: Calendar): Long =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun parseDate(value: String, currentYear: Int): Long? {
        val candidates = listOf(
            "yyyy-MM-dd" to value,
            "dd/MM/yyyy" to value,
            "dd/MM/yyyy" to "$value/$currentYear"
        )
        candidates.forEach { (pattern, text) ->
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = indiaTimeZone
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(text, position)
            if (parsed != null && position.index == text.length) {
                val calendar = Calendar.getInstance(indiaTimeZone).apply { time = parsed }
                return dateOnly(calendar)
            }
        }
        return null
    }
}
