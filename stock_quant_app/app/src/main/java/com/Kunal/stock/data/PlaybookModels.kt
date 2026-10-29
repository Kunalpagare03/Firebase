package com.Kunal.stock.data

// ── Playbook Setup Types ─────────────────────────────────────────────────────
enum class PlaybookSetup(val code: String, val label: String, val proven: Boolean, val winRate: Double, val avgPct: Double) {
    A("A", "RSI-2 Dip (Uptrend)", true, 47.0, 1.13),
    B("B", "50-DMA Pullback (52wk High)", true, 47.2, 1.02),
    C("C", "10-EMA Strength Leader", true, 43.0, 1.22),
    D("D", "Sharp 3-Day Fall (>8%)", false, 45.0, 1.76)
}

// ── A single signal hit from the scanner ────────────────────────────────────
data class PlaybookSignal(
    val id: String = "",
    val symbol: String = "",
    val sector: String = "",
    val setup: PlaybookSetup = PlaybookSetup.A,
    val signalDate: String = "",           // date of signal bar (close)
    val signalClose: Double = 0.0,         // close on signal day
    val entryPrice: Double = 0.0,          // next-day open (estimated = signalClose * 1.003)
    val stopLoss: Double = 0.0,            // 8% below signalClose
    val day22ExitDate: String = "",        // 22 trading days from signalDate
    val refTarget1: Double = 0.0,          // +8% reference (not hard target)
    val refTarget2: Double = 0.0,          // +15% reference
    val aiAction: String = "BUY",          // BUY | SKIP
    val aiReason: String = "",
    val aiNote: String = "",
    val status: TradeStatus = TradeStatus.OPEN,
    val exitPrice: Double = 0.0,
    val exitDate: String = "",
    val exitReason: String = "",           // "SL_HIT" | "DAY22" | "T1_HIT" | "T2_HIT" | "MANUAL"
    val returnPct: Double = 0.0,
    val currentPrice: Double = 0.0,
    val currentReturnPct: Double = 0.0,
    val niftyAbove200: Boolean = true,     // market filter at time of signal
    val strengthRank: Int = 0,             // 0-100 percentile 3-month return rank
    val rsi2: Double = 0.0,
    val rsi14: Double = 0.0,
    val volume20Avg: Long = 0L,
    val dailyValueCr: Double = 0.0
)

enum class TradeStatus { OPEN, SL_HIT, TARGET_HIT, DAY22_EXIT, SKIPPED }

// ── Backtest ledger aggregate ────────────────────────────────────────────────
data class PlaybookLedgerStats(
    val setup: PlaybookSetup,
    val totalSignals: Int = 0,
    val accepted: Int = 0,
    val skipped: Int = 0,
    val openTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val avgReturnPct: Double = 0.0,
    val bestTrade: Double = 0.0,
    val worstTrade: Double = 0.0
)
