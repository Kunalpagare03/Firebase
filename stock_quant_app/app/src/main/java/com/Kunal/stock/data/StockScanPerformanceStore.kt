package com.Kunal.stock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val DAY_MILLIS = 86_400_000L

private fun utcDay(epochMillis: Long): Long =
    Math.floorDiv(epochMillis, DAY_MILLIS) * DAY_MILLIS

private fun utcDate(day: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(day)
}

enum class StockReturnHorizon(val label: String, val sessions: Int) {
    SWING("Swing", 10),
    POSITIONAL("Positional", 30),
    LONG_TERM("Long-term", 252)
}

enum class StockReturnOutcome {
    WAITING,
    TRACKING,
    TARGET_HIT,
    STOP_HIT,
    HORIZON_COMPLETE
}

data class StockHorizonReturn(
    val horizon: StockReturnHorizon,
    val outcome: StockReturnOutcome,
    val observedSessions: Int,
    val returnPct: Double?,
    val exitPrice: Double?,
    val eventDay: Int?,
    val eventDate: String?
)

data class StockScanPerformanceRecord(
    val id: String,
    val symbol: String,
    val sector: String,
    val pattern: String,
    val recommendation: String,
    val signalDay: Long,
    val entryPrice: Double,
    val signalClose: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val isLongSignal: Boolean,
    val horizons: List<StockHorizonReturn>
)

internal data class StockPerformanceSignal(
    val signalDay: Long,
    val entryPrice: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val isLongSignal: Boolean
)

internal object StockScanReturnCalculator {
    fun evaluate(
        signal: StockPerformanceSignal,
        candles: List<StockCandle>
    ): List<StockHorizonReturn> {
        val forwardBars = candles
            .asSequence()
            .filter { it.close > 0 && utcDay(it.time) > signal.signalDay }
            .sortedBy { utcDay(it.time) }
            .distinctBy { utcDay(it.time) }
            .toList()

        return StockReturnHorizon.values().map { horizon ->
            evaluateHorizon(signal, forwardBars, horizon)
        }
    }

    private fun evaluateHorizon(
        signal: StockPerformanceSignal,
        forwardBars: List<StockCandle>,
        horizon: StockReturnHorizon
    ): StockHorizonReturn {
        if (signal.entryPrice <= 0) {
            return StockHorizonReturn(horizon, StockReturnOutcome.WAITING, 0, null, null, null, null)
        }

        val window = forwardBars.take(horizon.sessions)
        if (signal.isLongSignal) {
            window.forEachIndexed { index, candle ->
                val stopHit = signal.stopLoss > 0 && candle.low > 0 && candle.low <= signal.stopLoss
                val targetHit = signal.targetPrice > signal.entryPrice &&
                    candle.high >= signal.targetPrice

                if (stopHit || targetHit) {
                    val outcome = if (stopHit) StockReturnOutcome.STOP_HIT else StockReturnOutcome.TARGET_HIT
                    val fill = when (outcome) {
                        StockReturnOutcome.STOP_HIT ->
                            if (candle.open > 0 && candle.open < signal.stopLoss) candle.open else signal.stopLoss
                        else ->
                            if (candle.open >= signal.targetPrice) candle.open else signal.targetPrice
                    }
                    return StockHorizonReturn(
                        horizon = horizon,
                        outcome = outcome,
                        observedSessions = index + 1,
                        returnPct = (fill / signal.entryPrice - 1.0) * 100.0,
                        exitPrice = fill,
                        eventDay = index + 1,
                        eventDate = utcDate(utcDay(candle.time))
                    )
                }
            }
        }

        if (forwardBars.size >= horizon.sessions) {
            val horizonCandle = window.last()
            return StockHorizonReturn(
                horizon = horizon,
                outcome = StockReturnOutcome.HORIZON_COMPLETE,
                observedSessions = horizon.sessions,
                returnPct = (horizonCandle.close / signal.entryPrice - 1.0) * 100.0,
                exitPrice = horizonCandle.close,
                eventDay = null,
                eventDate = utcDate(utcDay(horizonCandle.time))
            )
        }

        val latest = forwardBars.lastOrNull()
        return if (latest == null) {
            StockHorizonReturn(horizon, StockReturnOutcome.WAITING, 0, null, null, null, null)
        } else {
            StockHorizonReturn(
                horizon = horizon,
                outcome = StockReturnOutcome.TRACKING,
                observedSessions = forwardBars.size,
                returnPct = (latest.close / signal.entryPrice - 1.0) * 100.0,
                exitPrice = latest.close,
                eventDay = null,
                eventDate = utcDate(utcDay(latest.time))
            )
        }
    }
}

class StockScanPerformanceStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    @Synchronized
    fun recordPriceHistory(
        symbol: String,
        candles: List<StockCandle>,
        symbolFilter: String? = null
    ): List<StockScanPerformanceRecord> {
        val normalizedSymbol = symbol.trim().uppercase(Locale.US)
        if (normalizedSymbol.isBlank()) return loadRecords(symbolFilter)

        val db = writableDatabase
        db.beginTransaction()
        try {
            insertCandles(db, normalizedSymbol, candles)
            pruneOldCandles(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return loadRecords(db, symbolFilter)
    }

    @Synchronized
    fun recordSnapshot(
        items: List<StockScanItem>,
        symbolFilter: String? = null
    ): List<StockScanPerformanceRecord> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { item ->
                val symbol = item.symbol.trim().uppercase(Locale.US)
                val candles = item.candles
                    .asSequence()
                    .filter { it.close > 0 && it.time > 0 }
                    .sortedBy { utcDay(it.time) }
                    .distinctBy { utcDay(it.time) }
                    .toList()
                if (symbol.isBlank()) return@forEach

                insertCandles(db, symbol, candles)

                val signalDay = parseSignalDay(item.barDate)
                    ?: candles.lastOrNull()?.let { utcDay(it.time) }
                    ?: return@forEach
                val entry = item.entry.takeIf { it > 0 } ?: item.close
                if (entry <= 0) return@forEach

                val pattern = item.pattern.trim()
                val id = "$symbol|$signalDay|${pattern.uppercase(Locale.US)}"
                val recommendation = item.recommendation.trim()
                val normalizedRecommendation = recommendation.uppercase(Locale.US)
                val isLong = normalizedRecommendation.contains("BUY") ||
                    normalizedRecommendation.contains("BULL") ||
                    item.analysisSide.equals("Bullish", ignoreCase = true)

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO scan_signals(
                        id, symbol, sector, pattern, recommendation, signal_day,
                        entry_price, signal_close, stop_loss, target_price, is_long
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        id,
                        symbol,
                        item.sector,
                        pattern,
                        recommendation,
                        signalDay,
                        entry,
                        item.close,
                        item.stopLoss,
                        item.targetPrice,
                        if (isLong) 1 else 0
                    )
                )
            }

            pruneOldCandles(db)
            db.execSQL(
                """
                DELETE FROM scan_signals
                WHERE id NOT IN (
                    SELECT id FROM scan_signals ORDER BY signal_day DESC LIMIT $MAX_SAVED_SIGNALS
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return loadRecords(db, symbolFilter)
    }

    private fun insertCandles(db: SQLiteDatabase, symbol: String, candles: List<StockCandle>) {
        candles.asSequence()
            .filter { it.close > 0 && it.time > 0 }
            .distinctBy { utcDay(it.time) }
            .forEach { candle ->
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO scan_candles(symbol, session_day, open, high, low, close)
                    VALUES(?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        symbol,
                        utcDay(candle.time),
                        candle.open,
                        candle.high,
                        candle.low,
                        candle.close
                    )
                )
            }
    }

    private fun pruneOldCandles(db: SQLiteDatabase) {
        val newestDay = db.rawQuery("SELECT MAX(session_day) FROM scan_candles", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }
        if (newestDay > 0) {
            db.delete(
                "scan_candles",
                "session_day < ?",
                arrayOf((newestDay - MAX_CANDLE_RETENTION_DAYS).toString())
            )
        }
    }

    @Synchronized
    fun loadRecords(symbolFilter: String? = null): List<StockScanPerformanceRecord> =
        loadRecords(readableDatabase, symbolFilter)

    private fun loadRecords(db: SQLiteDatabase, symbolFilter: String? = null): List<StockScanPerformanceRecord> {
        val records = mutableListOf<StockScanPerformanceRecord>()
        val normalizedFilter = symbolFilter?.trim()?.uppercase(Locale.US).orEmpty()
        val selection = if (normalizedFilter.isBlank()) "" else "WHERE symbol LIKE ?"
        db.rawQuery(
            """
            SELECT id, symbol, sector, pattern, recommendation, signal_day,
                   entry_price, signal_close, stop_loss, target_price, is_long
            FROM scan_signals
            $selection
            ORDER BY signal_day DESC, symbol ASC
            LIMIT $MAX_DISPLAYED_SIGNALS
            """.trimIndent(),
            if (normalizedFilter.isBlank()) null else arrayOf("%$normalizedFilter%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val signal = StockPerformanceSignal(
                    signalDay = cursor.getLong(5),
                    entryPrice = cursor.getDouble(6),
                    stopLoss = cursor.getDouble(8),
                    targetPrice = cursor.getDouble(9),
                    isLongSignal = cursor.getInt(10) == 1
                )
                val candles = loadCandles(db, cursor.getString(1), signal.signalDay)
                records.add(
                    StockScanPerformanceRecord(
                        id = cursor.getString(0),
                        symbol = cursor.getString(1),
                        sector = cursor.getString(2),
                        pattern = cursor.getString(3),
                        recommendation = cursor.getString(4),
                        signalDay = signal.signalDay,
                        entryPrice = signal.entryPrice,
                        signalClose = cursor.getDouble(7),
                        stopLoss = signal.stopLoss,
                        targetPrice = signal.targetPrice,
                        isLongSignal = signal.isLongSignal,
                        horizons = StockScanReturnCalculator.evaluate(signal, candles)
                    )
                )
            }
        }
        return records
    }

    private fun loadCandles(db: SQLiteDatabase, symbol: String, afterDay: Long): List<StockCandle> {
        val candles = mutableListOf<StockCandle>()
        db.rawQuery(
            """
            SELECT session_day, open, high, low, close
            FROM scan_candles
            WHERE symbol = ? AND session_day > ?
            ORDER BY session_day ASC
            LIMIT ${StockReturnHorizon.LONG_TERM.sessions}
            """.trimIndent(),
            arrayOf(symbol, afterDay.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                candles.add(
                    StockCandle(
                        time = cursor.getLong(0),
                        open = cursor.getDouble(1),
                        high = cursor.getDouble(2),
                        low = cursor.getDouble(3),
                        close = cursor.getDouble(4)
                    )
                )
            }
        }
        return candles
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE scan_candles(
                symbol TEXT NOT NULL,
                session_day INTEGER NOT NULL,
                open REAL NOT NULL,
                high REAL NOT NULL,
                low REAL NOT NULL,
                close REAL NOT NULL,
                PRIMARY KEY(symbol, session_day)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE scan_signals(
                id TEXT PRIMARY KEY NOT NULL,
                symbol TEXT NOT NULL,
                sector TEXT NOT NULL,
                pattern TEXT NOT NULL,
                recommendation TEXT NOT NULL,
                signal_day INTEGER NOT NULL,
                entry_price REAL NOT NULL,
                signal_close REAL NOT NULL,
                stop_loss REAL NOT NULL,
                target_price REAL NOT NULL,
                is_long INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX scan_signals_by_day ON scan_signals(signal_day DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "stock_scan_performance.db"
        private const val DATABASE_VERSION = 1
        private const val MAX_SAVED_SIGNALS = 50_000
        private const val MAX_DISPLAYED_SIGNALS = 500
        private const val MAX_CANDLE_RETENTION_DAYS = 1100L

        private fun parseSignalDay(value: String): Long? {
            val text = value.trim()
            if (text.isEmpty()) return null

            text.toLongOrNull()?.let { raw ->
                val millis = if (raw in 1_000_000_000L..99_999_999_999L) raw * 1000 else raw
                if (millis > 0) return utcDay(millis)
            }

            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ss",
                "dd-MMM-yyyy HH:mm:ss",
                "yyyy-MM-dd",
                "yyyyMMdd",
                "dd-MMM-yyyy",
                "dd/MM/yyyy"
            )
            patterns.forEach { pattern ->
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val position = ParsePosition(0)
                val date = parser.parse(text, position)
                if (date != null && position.index == text.length) return utcDay(date.time)
            }
            return null
        }
    }
}
