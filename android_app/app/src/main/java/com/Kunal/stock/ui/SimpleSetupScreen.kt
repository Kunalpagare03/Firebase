package com.Kunal.stock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Kunal.stock.data.HeroZeroAnalysis
import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.UiState
import java.util.Calendar
import java.util.TimeZone

// ─── Colors ───────────────────────────────────────────────────────────────────
private val GreenBull   = Color(0xFF00C853)
private val RedBear     = Color(0xFFD50000)
private val AmberWarn   = Color(0xFFFFAB00)
private val BlueCool    = Color(0xFF2196F3)
private val PurpleAcc   = Color(0xFFCE93D8)
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark    = Color(0xFF16213E)
private val CardDark2   = Color(0xFF0F3460)

// ─── IST helpers ──────────────────────────────────────────────────────────────
private fun istNow(): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))

private fun istHHMM(): Int {
    val c = istNow()
    return c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE)
}

private fun fmtHHMM(hhmm: Int): String = "%02d:%02d".format(hhmm / 100, hhmm % 100)

private fun isCurrentExpiry(expiry: String): Boolean {
    if (expiry.isBlank()) return false
    val india = TimeZone.getTimeZone("Asia/Kolkata")
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
        isLenient = false
        timeZone = india
    }
    val position = java.text.ParsePosition(0)
    val parsed = date.parse(expiry, position) ?: return false
    return position.index == expiry.length && date.format(parsed) == date.format(istNow().time)
}

// ─── Live Signal Model ───────────────────────────────────────────────────────
data class LiveSignal(
    val status: String,          // "WATCH", "WAIT", "CLOSED"
    val setup: String,
    val headline: String,
    val details: List<String>,
    val color: Color
)

private fun computeSignal(hhmm: Int, isExpiry: Boolean): LiveSignal {
    return when {
        hhmm < 900 -> LiveSignal(
            "WAIT", "Market Closed", "Market not open yet",
            listOf("Write yesterday's High / Low / Close", "Was yesterday UP or DOWN day?"),
            Color.Gray
        )
        hhmm < 915 -> LiveSignal(
            "WAIT", "Pre-Open", "Prepare — market opens at 9:15",
            listOf("Note yesterday H/L/Close", "Decide if UP or DOWN day"),
            Color.Gray
        )
        hhmm < 930 -> LiveSignal(
            "WATCH", "Setup 1 — Candle Forming",
            "9:15-9:30 candle forming — DO NOTHING",
            listOf(
                "Watch the 1st 15-min candle",
                "Mark its HIGH and LOW on chart",
                "Do NOT enter yet"
            ),
            AmberWarn
        )
        hhmm < 945 -> LiveSignal(
            "WATCH", "Setup 1 — Range Check",
            "9:30-9:45 — Wait. Check candle size",
            listOf(
                "Range size must be < 0.6% of Nifty",
                "If range > 0.6%: SKIP Setup 1 today",
                "Still do NOT enter"
            ),
            AmberWarn
        )
        hhmm < 1000 -> LiveSignal(
            "WATCH", "Setup 1 — ORB review window",
            "Time window open; the app has not validated your opening range.",
            listOf(
                "Check a completed candle against the opening range and VWAP.",
                "Enter the range, current price and VWAP in Agent checks.",
                "This time guide is not a live setup signal."
            ),
            AmberWarn
        )
        hhmm < 1015 -> LiveSignal(
            "WATCH", "Setup 1 — ORB review window",
            "Time window open; wait for the entered rules to pass.",
            listOf(
                "Verify completed candle, range, current price and VWAP.",
                "No entry is generated from time alone."
            ),
            AmberWarn
        )
        hhmm == 1015 || (hhmm in 1015..1030) -> LiveSignal(
            "WATCH", "Setup 2 — First-hour filter window",
            "Complete and verify the first-hour candle before using this filter.",
            listOf(
                "The filter requires complete OHLC and a 20-day average range.",
                "A strong-green result is a directional filter, not an entry signal."
            ),
            AmberWarn
        )
        isExpiry && hhmm in 1315..1400 -> LiveSignal(
            "WATCH", "Setup 3 — Hero Zero review window",
            "Expiry window active; wait for the live candidate checks.",
            listOf(
                "Verify fresh spot and chain, selected expiry, matching contract and observed premium.",
                "Candidate status is not a probability or buy recommendation."
            ),
            AmberWarn
        )
        hhmm < 1330 -> LiveSignal(
            "WAIT", "Manage positions only",
            "10:30-13:30 — no new entry from the time guide.",
            listOf("Manage an open position using its planned risk limits.", "Do not treat the first-hour filter as an entry signal."),
            Color.Gray
        )
        hhmm < 1515 -> LiveSignal(
            "WAIT", "Close Positions",
            "Approaching 15:15 — prepare to close all",
            listOf(
                "Close ALL open positions before 15:15",
                "Write your trade journal",
                "Review: did you follow the rules?"
            ),
            AmberWarn
        )
        else -> LiveSignal(
            "CLOSED", "Market Closed", "Trading day over — write journal",
            listOf(
                "Record today's trade in journal",
                "Note: Setup used, entry, exit, P&L, rules followed?",
                "Prepare for tomorrow"
            ),
            Color.Gray
        )
    }
}

// ─── Trade Journal ────────────────────────────────────────────────────────────
data class JournalEntry(
    val id: Int,
    val date: String,
    val setup: String,
    val entryTime: String,
    val side: String,       // CE or PE
    val strike: String,
    val buyPrice: String,
    val indexEntry: String,
    val stop: String,
    val target: String,
    val exitTime: String,
    val exitReason: String,
    val pnlRs: String,
    val followedRules: Boolean
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SimpleSetupScreen(viewModel: OptionSentimentViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()
    val liveQuote by viewModel.liveQuote.collectAsState()
    var checkClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            checkClock = System.currentTimeMillis()
        }
    }
    val liveCheck = remember(uiState, liveQuote, checkClock) {
        LiveSetupCheck.evaluate(uiState, liveQuote, checkClock)
    }
    val liveData = (uiState as? UiState.Success)?.data?.let { source ->
        source.copy(
            spotPrice = liveQuote?.spotPrice?.takeIf { it.isFinite() && it > 0.0 } ?: source.spotPrice,
            spotHistory = (source.spotHistory + (liveQuote?.spotHistory ?: emptyList()))
                .distinctBy { it.time }
                .sortedBy { it.time }
                .takeLast(10_000)
        )
    }
    val tabs = listOf("Signal", "Agent", "Day Plan", "Setups", "Hero Zero", "Journal", "Risk Calc", "Checklist")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardDark,
            contentColor = GreenBull,
            edgePadding = 4.dp
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Text(
                            t,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == i) GreenBull else Color.Gray
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> SignalTab(liveCheck)
                1 -> AiAgentTab(liveData)
                2 -> DayPlanTab(liveData)
                3 -> SetupsTab()
                4 -> HeroZeroTab(liveData, liveCheck.spotFresh)
                5 -> JournalTab()
                6 -> RiskCalcTab()
                7 -> ChecklistTab()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 0 — LIVE SIGNAL (THE MAIN NEW FEATURE)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SignalTab(liveCheck: LiveSetupCheckResult? = null) {
    var hhmm by remember { mutableStateOf(istHHMM()) }
    val isExpiry = liveCheck?.currentExpiryDay == true

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            hhmm = istHHMM()
        }
    }

    val signal = computeSignal(hhmm, isExpiry)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LiveSetupStatusCard(liveCheck)
        }

        // ── CURRENT TIME ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (signal.status == "WATCH") AmberWarn else Color.Gray)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "IST  ${fmtHHMM(hhmm)}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }

                    if (isExpiry) {
                        Surface(
                            color = RedBear.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "  EXPIRY DAY  ",
                                color = RedBear,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── BIG SIGNAL CARD ──
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = signal.color.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, signal.color, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Status badge
                        Surface(
                            color = signal.color,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "  ${signal.status}  ",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            signal.setup,
                            color = signal.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        signal.headline,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    signal.details.forEach { detail ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("▸ ", color = signal.color, fontSize = 13.sp)
                            Text(detail, color = Color(0xFFE0E0E0), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ── NEXT WINDOW COUNTDOWN ──
        item {
            val nextLabel = when {
                hhmm < 945  -> "Setup 1 ORB window opens at 9:45"
                hhmm < 1015 -> "Setup 2 direction check at 10:15"
                hhmm < 1330 -> "No new trades after 13:30"
                isExpiry && hhmm < 1315 -> "Hero Zero Setup 3 at 13:15"
                else        -> "Market closes at 15:15"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Next: $nextLabel", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        // ── QUICK RULES REMINDER ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Quick Rules", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    QuickRule("Use the live evidence card; stale or missing inputs mean WAIT.")
                    QuickRule("Setup 1 and 2 checks require complete, manually verified inputs.")
                    QuickRule("Hero Zero requires today's chain expiry and fresh matching contract data.")
                    QuickRule("Time windows are guides only; they do not produce a trade signal.")
                }
            }
        }

        // ── NEVER DO THESE ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RedBear.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = RedBear, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Risk reminders", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "Buy on 5-min hammer / bullish engulfing alone",
                        "Use OI walls as support/resistance",
                        "Trade Bank Nifty midday or end-of-day",
                        "Buy cheap options at 9:15 on expiry morning",
                        "Take Nifty trades after 13:30",
                        "Move stop loss further away"
                    ).forEach { n ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("✗ ", color = RedBear, fontSize = 12.sp)
                            Text(n, color = Color(0xFFFFCDD2), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LiveSetupStatusCard(result: LiveSetupCheckResult?) {
    val passed = result?.status == "CONDITIONAL CHECK PASSED"
    val color = if (passed) GreenBull else AmberWarn
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (passed) Icons.Default.Verified else Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    result?.status ?: "WAIT",
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.weight(1f))
                Text(result?.symbol?.ifBlank { "Options" } ?: "Options", color = Color.LightGray, fontSize = 11.sp)
            }
            Text(result?.message ?: "Waiting for live option data.", color = Color.White, fontSize = 12.sp)
            val monitor = result?.signal
            if (passed && monitor != null && monitor.optionType.isNotBlank()) {
                Text(
                    "Evidence bias: ${result.consensus?.direction ?: "N/A"} · near-ATM monitor: ${monitor.recommendedContract} · chain premium ₹${"%.2f".format(monitor.estimatedPremium)}",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Spot ${if (result?.spotFresh == true) "FRESH" else "STALE / MISSING"}",
                    color = if (result?.spotFresh == true) GreenBull else RedBear,
                    fontSize = 10.sp
                )
                Text(
                    "Chain ${if (result?.chainFresh == true) "FRESH" else "STALE / MISSING"}",
                    color = if (result?.chainFresh == true) GreenBull else RedBear,
                    fontSize = 10.sp
                )
            }
            Text(
                "Live evidence gate only—not a probability or order recommendation. It does not check your manual ORB range.",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 1 — DAY PLAN
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DayPlanTab(data: OptionSentimentData? = null) {
    var currentTime by remember { mutableStateOf(istHHMM()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            currentTime = istHHMM()
        }
    }
    val hhmm = currentTime

    data class Slot(val time: String, val what: String, val from: Int, val to: Int, val col: Color)

    val slots = listOf(
        Slot("Before 9:15",    "Write yesterday H/L/Close. UP or DOWN?",         0,    915,  BlueCool),
        Slot("9:15 – 9:30",   "DO NOTHING. Watch 1st 15-min candle. Mark H/L.", 915,  930,  AmberWarn),
        Slot("9:30 – 9:45",   "Still wait. Check candle size (< 0.6% rule).",   930,  945,  AmberWarn),
        Slot("9:45 – 10:30",  "Setup 1 ORB review window; verify manual inputs.", 945, 1030, AmberWarn),
        Slot("10:15",         "Setup 2 first-hour filter; not an entry signal.", 1015, 1016, BlueCool),
        Slot("10:30 – 13:30", "Manage open trade only. No new entry.",          1030, 1330, Color.Gray),
        Slot("13:15 (expiry)","Hero Zero live-check window; candidate only.",    1315, 1400, RedBear),
        Slot("After 13:30",   "No new entries; expiry review window closes at 14:00.", 1330, 1515, RedBear),
        Slot("15:15",         "Close all. Write journal.",                       1515, 2359, Color.Gray)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark2), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IST  ${fmtHHMM(hhmm)}", color = GreenBull, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (isCurrentExpiry(data?.chainExpiry.orEmpty())) {
                        Surface(color = RedBear.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                            Text("  CURRENT CHAIN EXPIRY  ", color = RedBear, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
        items(slots) { s ->
            val active = hhmm >= s.from && hhmm < s.to
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (active) s.col.copy(alpha = 0.18f) else CardDark
                ),
                modifier = Modifier.fillMaxWidth()
                    .then(if (active) Modifier.border(1.5.dp, s.col, RoundedCornerShape(10.dp)) else Modifier)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.width(90.dp)) {
                        Text(s.time, color = s.col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (active) Text("▶ NOW", color = s.col, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(s.what, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 2 — SETUPS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SetupsTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SetupCard1() }
        item { SetupCard2() }
        item { SetupCard3() }
        item { SetupCard4() }
        item { SetupCard5() }
        item { NeverDoCard() }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable fun SetupCard1() = SetupCardBase(
    "Setup 1 — 15-min Opening Range Breakout (ORB)", "⚠ PAPER TRADE FIRST", AmberWarn,
    "Rule description only; no verified backtest result is loaded for this setup.", AmberWarn
) {
    RuleRow("When", "9:15-9:30 candle forms. Mark its HIGH and LOW.")
    RuleRow("Range check", "Size must be < 0.6% of Nifty. Skip today if too wide.")
    RuleRow("CE condition", "9:45-10:00: 15-min candle closes above HIGH and above VWAP.")
    RuleRow("PE condition", "9:45-10:00: 15-min candle closes below LOW and below VWAP.")
    RuleRow("Entry", "Next 5-min candle open.")
    RuleRow("Stop", "(High + Low) / 2 — midpoint of the opening range.")
    RuleRow("Target", "Entry + 2× risk (index points).")
    RuleRow("Skip if", "First close is on WRONG side of VWAP, or vs Setup 2 direction.")
    RuleRow("Review", "After 30 trades: avg loss > 0.25R? → back to paper trading.")
}

@Composable fun SetupCard2() = SetupCardBase(
    "Setup 2 — First-Hour Direction (10:15 Filter)", "🔵 FILTER ONLY — not a trade", BlueCool,
    "A directional filter hypothesis, not a calibrated statistic or standalone trade signal.", BlueCool
) {
    RuleRow("When", "At 10:15 — the 9:15-10:15 (1-hour) candle is complete.")
    RuleRow("STRONG GREEN", "Close > Open, body ≥ 60% of size, size ≥ 1.2× 20-day avg → CE only all day.")
    RuleRow("STRONG RED", "No special direction. Red 1st hour ≠ day falls. Do NOT short just for this.")
    RuleRow("Any other", "No direction. Follow Setup 1 rules only.")
    RuleRow("Note", "No historical performance statistic is provided for this filter.")
}

@Composable fun SetupCard3() = SetupCardBase(
    "Setup 3 — Expiry Day Hero Zero (13:15 only)", "🔴 EXPIRY ONLY — small money", RedBear,
    "Use the live Hero Zero tab for current candidate checks; no historical expectancy is claimed.", RedBear
) {
    RuleRow("Check", "Use the live expiry, fresh spot and chain, and matching contract premium.")
    RuleRow("Risk", "The full option premium can be lost; a candidate is not an entry instruction.")
    RuleRow("Evidence", "Auto-archived detections are observations, not a validated options backtest.")
}

@Composable fun SetupCard4() = SetupCardBase(
    "Setup 4 — Shooting Star on 15-min (after a rise)", "PAPER ONLY", Color.Gray,
    "Rule description only; no verified backtest result is loaded for this setup.", Color.Gray
) {
    RuleRow("Status", "Not supported by the live Setup Agent; no verified backtest is loaded.")
}

@Composable fun SetupCard5() = SetupCardBase(
    "Setup 5 — Previous Day H/L Breakout (5-min)", "PAPER ONLY — Nifty only", Color.Gray,
    "Rule description only; no verified backtest result is loaded for this setup.", Color.Gray
) {
    RuleRow("Status", "Not supported by the live Setup Agent; no verified backtest is loaded.")
}

@Composable
fun NeverDoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().border(1.dp, RedBear, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = RedBear, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Risk reminders", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            listOf(
                "Buy just because of a 5-min hammer / bullish engulfing candle",
                "Trade OI walls (call/put OI) as support or resistance",
                "Assume cheap expiry options are low-risk",
                "Buy after a gap-down hoping it fills",
                "Enter outside a verified setup window",
                "Trade Bank Nifty midday or end-of-day setups",
                "Treat OI walls, max pain, or one candle as a trade signal",
                "Guess the next candle from the last candle (50/50)"
            ).forEach { n ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("✗ ", color = RedBear, fontSize = 13.sp, modifier = Modifier.width(18.dp))
                    Text(n, color = Color(0xFFFFCDD2), fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 3 — HERO ZERO
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun HeroZeroTab(data: OptionSentimentData? = null, spotQuoteFresh: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeroZeroCard(data?.heroZeroAnalysis ?: HeroZeroAnalysis(), data, spotQuoteFresh)
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How the screen qualifies a candidate", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Requires a backend candidate, today's selected expiry, fresh spot and option-chain data, a matching live chain contract, premium in the engine's ₹3-₹50 range, and a strike within 1.5% of spot during the configured review window.", color = Color.LightGray, fontSize = 12.sp)
                Text("The backend's score is not a calibrated probability. Candidate records are observations, not proof of expected return.", color = AmberWarn, fontSize = 11.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 4 — TRADE JOURNAL (from image section 8)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun JournalTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = remember(context) {
        mutableStateListOf<JournalEntry>().apply {
            addAll(OptionTradeJournalStore.load(context))
        }
    }
    var showForm by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf((entries.maxOfOrNull { it.id } ?: 0) + 1) }
    var journalError by remember { mutableStateOf<String?>(null) }

    // Form fields
    var fDate      by remember { mutableStateOf(OptionTradeJournalStore.currentDate()) }
    var fSetup     by remember { mutableStateOf("Setup 1") }
    var fEntryTime by remember { mutableStateOf("") }
    var fSide      by remember { mutableStateOf("CE") }
    var fStrike    by remember { mutableStateOf("") }
    var fBuyPrice  by remember { mutableStateOf("") }
    var fIdxEntry  by remember { mutableStateOf("") }
    var fStop      by remember { mutableStateOf("") }
    var fTarget    by remember { mutableStateOf("") }
    var fExitTime  by remember { mutableStateOf("") }
    var fExitReason by remember { mutableStateOf("") }
    var fPnl       by remember { mutableStateOf("") }
    var fRules     by remember { mutableStateOf(true) }

    val setupOptions = listOf("Setup 1", "Setup 2", "Setup 3", "Setup 4", "Setup 5")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trade Journal", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Button(
                    onClick = { showForm = !showForm },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenBull),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(if (showForm) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text(if (showForm) "Cancel" else "New Trade", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Entry Form ──
        if (showForm) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GreenBull.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Log New Trade", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        // Date + Entry Time
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalField(
                                "Date (YYYY-MM-DD)",
                                fDate,
                                {
                                    fDate = it
                                    journalError = null
                                },
                                Modifier.weight(1f)
                            )
                            JournalField("Entry Time", fEntryTime, { fEntryTime = it }, Modifier.weight(1f))
                        }
                        journalError?.let { Text(it, color = RedBear, fontSize = 11.sp) }

                        // Setup selector
                        Text("Setup", color = Color.Gray, fontSize = 12.sp)
                        ScrollableTabRow(
                            selectedTabIndex = setupOptions.indexOf(fSetup).coerceAtLeast(0),
                            containerColor = Color.Transparent,
                            contentColor = GreenBull,
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            setupOptions.forEachIndexed { i, s ->
                                val sel = fSetup == s
                                Tab(selected = sel, onClick = { fSetup = s },
                                    text = {
                                        Text(s, fontSize = 11.sp, color = if (sel) GreenBull else Color.Gray,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    })
                            }
                        }

                        // CE/PE
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Side:", color = Color.Gray, fontSize = 12.sp)
                            listOf("CE", "PE").forEach { side ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { fSide = side }) {
                                    RadioButton(selected = fSide == side, onClick = { fSide = side },
                                        colors = RadioButtonDefaults.colors(selectedColor = GreenBull))
                                    Text(side, color = if (fSide == side) GreenBull else Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalField("Strike", fStrike, { fStrike = it }, Modifier.weight(1f), KeyboardType.Number)
                            JournalField("Buy Price (Rs)", fBuyPrice, { fBuyPrice = it }, Modifier.weight(1f), KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalField("Index Entry", fIdxEntry, { fIdxEntry = it }, Modifier.weight(1f), KeyboardType.Number)
                            JournalField("Stop (index)", fStop, { fStop = it }, Modifier.weight(1f), KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalField("Target (index)", fTarget, { fTarget = it }, Modifier.weight(1f), KeyboardType.Number)
                            JournalField("Exit Time", fExitTime, { fExitTime = it }, Modifier.weight(1f))
                        }
                        JournalField("Exit Reason (SL hit / Target / Time / Manual)", fExitReason, { fExitReason = it }, Modifier.fillMaxWidth())
                        JournalField("P&L (Rs, use - for loss)", fPnl, { fPnl = it }, Modifier.fillMaxWidth(), KeyboardType.Number)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fRules, onCheckedChange = { fRules = it },
                                colors = CheckboxDefaults.colors(checkedColor = GreenBull))
                            Text("Followed all rules?", color = Color.White, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (!OptionTradeJournalStore.isValidDate(fDate)) {
                                    journalError = "Enter a valid date (YYYY-MM-DD, DD/MM/YYYY, or DD/MM) so risk totals can include this trade."
                                } else {
                                    val entry = JournalEntry(
                                        nextId++, fDate, fSetup, fEntryTime, fSide, fStrike,
                                        fBuyPrice, fIdxEntry, fStop, fTarget, fExitTime, fExitReason, fPnl, fRules
                                    )
                                    entries.add(0, entry)
                                    OptionTradeJournalStore.save(context, entries)
                                    fDate = OptionTradeJournalStore.currentDate(); fEntryTime = ""; fStrike = ""; fBuyPrice = ""
                                    fIdxEntry = ""; fStop = ""; fTarget = ""; fExitTime = ""
                                    fExitReason = ""; fPnl = ""; fRules = true; fSetup = "Setup 1"; fSide = "CE"
                                    showForm = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenBull)
                        ) {
                            Text("Save Trade", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Stats summary ──
        if (entries.isNotEmpty()) {
            item {
                val wins = entries.count { (it.pnlRs.toDoubleOrNull() ?: 0.0) > 0 }
                val totalPnl = entries.sumOf { it.pnlRs.toDoubleOrNull() ?: 0.0 }
                val rulesFollowed = entries.count { it.followedRules }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatChip("Trades", "${entries.size}", BlueCool)
                        StatChip("Wins", "$wins", GreenBull)
                        StatChip("Rules OK", "$rulesFollowed/${entries.size}", AmberWarn)
                        val pnlColor = if (totalPnl >= 0) GreenBull else RedBear
                        StatChip("P&L", "₹%.0f".format(totalPnl), pnlColor)
                    }
                }
            }
        }

        // ── Journal entries ──
        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Book, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No trades logged yet", color = Color.Gray, fontSize = 14.sp)
                            Text("Tap 'New Trade' after each trade", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(entries) { e ->
            val pnlVal = e.pnlRs.toDoubleOrNull() ?: 0.0
            val pnlColor = if (pnlVal > 0) GreenBull else if (pnlVal < 0) RedBear else Color.Gray
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (pnlVal > 0) GreenBull.copy(alpha = 0.08f)
                                     else if (pnlVal < 0) RedBear.copy(alpha = 0.08f)
                                     else CardDark
                ),
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp,
                        if (pnlVal > 0) GreenBull.copy(alpha = 0.3f)
                        else if (pnlVal < 0) RedBear.copy(alpha = 0.3f)
                        else Color.DarkGray,
                        RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${e.date}  ${e.setup}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("₹${e.pnlRs}", color = pnlColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalChip(e.side, if (e.side == "CE") GreenBull else RedBear)
                        JournalChip("Strike: ${e.strike}", BlueCool)
                        JournalChip("Buy: ₹${e.buyPrice}", AmberWarn)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Entry ${e.entryTime} → Exit ${e.exitTime} | ${e.exitReason}", color = Color.Gray, fontSize = 11.sp)
                    Text("Index: Entry ${e.indexEntry}  SL ${e.stop}  TGT ${e.target}", color = Color.Gray, fontSize = 11.sp)
                    if (!e.followedRules) {
                        Spacer(Modifier.height(2.dp))
                        Text("⚠ Rules NOT followed", color = RedBear, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 5 — RISK CALCULATOR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun RiskCalcTab() {
    var capitalStr by remember { mutableStateOf("") }
    var lotSizeStr by remember { mutableStateOf("") }
    var premiumStr by remember { mutableStateOf("") }
    var stopPremiumStr by remember { mutableStateOf("") }
    val capital = capitalStr.toDoubleOrNull() ?: 0.0
    val lotSize = lotSizeStr.toIntOrNull()?.takeIf { it > 0 } ?: 0
    val premium = premiumStr.toDoubleOrNull() ?: 0.0
    val stopPremium = stopPremiumStr.toDoubleOrNull() ?: 0.0
    val maxRisk05  = capital * 0.005
    val maxRisk025 = capital * 0.0025
    val riskPerLot = (premium - stopPremium).takeIf { it > 0.0 && lotSize > 0 }?.times(lotSize) ?: 0.0
    val affordableLots = if (riskPerLot > 0.0) (maxRisk05 / riskPerLot).toInt() else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark2), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("7. Money Rules — Risk Calculator", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Max risk: 0.5% per trade. Hero Zero: 0.25% per expiry.", color = Color.LightGray, fontSize = 12.sp)
                    Text("Uses your entered contract lot size, premium and stop; it does not assume fixed index lot sizes.", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = capitalStr,
                        onValueChange = { capitalStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Your Capital (Rs)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenBull, focusedLabelColor = GreenBull,
                            cursorColor = GreenBull, unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalField("Current lot size", lotSizeStr, { lotSizeStr = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                        JournalField("Entry premium", premiumStr, { premiumStr = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    Spacer(Modifier.height(8.dp))
                    JournalField("Planned stop premium", stopPremiumStr, { stopPremiumStr = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), KeyboardType.Decimal)
                }
            }
        }

        if (capital > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenBull.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GreenBull, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Risk Breakdown", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        RiskRow("Capital", "Rs %,.0f".format(capital), Color.White)
                        RiskRow("Max risk per trade (0.5%)", "Rs %,.0f".format(maxRisk05), AmberWarn)
                        RiskRow("Hero Zero budget (0.25%)", "Rs %,.0f".format(maxRisk025), BlueCool)
                        if (riskPerLot > 0.0) {
                            RiskRow("Maximum loss per lot", "Rs %,.0f".format(riskPerLot), RedBear)
                            RiskRow("Affordable lots by risk budget", "$affordableLots lot${if (affordableLots != 1) "s" else ""} (${affordableLots * lotSize} qty)", GreenBull)
                        } else {
                            Text("Enter a lot size and a stop below the entry premium to calculate contract risk.", color = AmberWarn, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Quick Reference", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Risk per lot = (entry premium − stop premium) × lot size.", color = Color.LightGray, fontSize = 12.sp)
                    Text("Lot counts are estimates before slippage, brokerage, taxes and gap risk.", color = AmberWarn, fontSize = 12.sp)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SetupCardBase(
    title: String, badge: String, badgeColor: Color,
    backtest: String, color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Surface(color = badgeColor.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                Text("  $badge  ", color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(6.dp))
            Divider(color = Color.DarkGray, thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))
            Text("Note: $backtest", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun RuleRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = Color(0xFFB0BEC5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        Text(value, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun InfoBullet(text: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", color = color, fontSize = 12.sp)
        Text(text, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun RiskRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun QuickRule(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", color = AmberWarn, fontSize = 12.sp)
        Text(text, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun JournalField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, kbType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = kbType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GreenBull, focusedLabelColor = GreenBull,
            cursorColor = GreenBull, unfocusedBorderColor = Color.DarkGray,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            unfocusedLabelColor = Color.Gray
        )
    )
}

@Composable
fun JournalChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
        Text("  $text  ", color = color, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// AI AGENT TAB — 3 Sub-Agents + Master Verdict UI
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AiAgentTab(liveData: OptionSentimentData? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // ── Inputs ──
    var rangeHigh    by remember { mutableStateOf("") }
    var rangeLow     by remember { mutableStateOf("") }
    var currentPrice by remember { mutableStateOf("") }
    var vwap         by remember { mutableStateOf("") }
    var niftyLevel   by remember { mutableStateOf("22500") }
    var fhOpen       by remember { mutableStateOf("") }
    var fhClose      by remember { mutableStateOf("") }
    var fhHigh       by remember { mutableStateOf("") }
    var fhLow        by remember { mutableStateOf("") }
    var fhAvg20d     by remember { mutableStateOf("") }
    var optionPrice  by remember { mutableStateOf("") }
    var lotSize      by remember { mutableStateOf("") }
    var maxLossPerLot by remember { mutableStateOf("") }
    var optStrike    by remember { mutableStateOf("ATM") }
    var selIndex     by remember { mutableStateOf("NIFTY") }
    var aboveOpen    by remember { mutableStateOf(false) }
    var aboveVwap    by remember { mutableStateOf(false) }
    var priceContextConfirmed by remember { mutableStateOf(false) }
    var capital      by remember { mutableStateOf("") }
    var selSetup     by remember { mutableIntStateOf(1) }
    var selectedAgent by remember { mutableIntStateOf(-1) }

    var verdict      by remember { mutableStateOf<SetupMasterVerdict?>(null) }
    var isRunning    by remember { mutableStateOf(false) }
    var agentStep    by remember { mutableIntStateOf(0) } // 0=idle,1=A1,2=A2,3=A3,4=master

    var hhmm by remember { mutableIntStateOf(istHHMM()) }
    var isExpiry by remember { mutableStateOf(false) }
    LaunchedEffect(liveData?.chainExpiry) {
        while (true) {
            hhmm = istHHMM()
            isExpiry = isCurrentExpiry(liveData?.chainExpiry.orEmpty())
            kotlinx.coroutines.delay(5_000)
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        agentStep = 1; kotlinx.coroutines.delay(600)
        agentStep = 2; kotlinx.coroutines.delay(600)
        agentStep = 3; kotlinx.coroutines.delay(600)
        agentStep = 4; kotlinx.coroutines.delay(400)
        val journalPnl = OptionTradeJournalStore.pnlSummary(
            OptionTradeJournalStore.load(context),
            System.currentTimeMillis()
        )
        val input = SetupAgentInput(
            hhmm = hhmm, isExpiryDay = isExpiry,
            rangeHigh = rangeHigh.toDoubleOrNull() ?: 0.0,
            rangeLow = rangeLow.toDoubleOrNull() ?: 0.0,
            currentPrice = currentPrice.toDoubleOrNull() ?: 0.0,
            vwap = vwap.toDoubleOrNull() ?: 0.0,
            niftyLevel = niftyLevel.toDoubleOrNull() ?: 22500.0,
            firstHourOpen = fhOpen.toDoubleOrNull() ?: 0.0,
            firstHourClose = fhClose.toDoubleOrNull() ?: 0.0,
            firstHourHigh = fhHigh.toDoubleOrNull() ?: 0.0,
            firstHourLow = fhLow.toDoubleOrNull() ?: 0.0,
            avgFirstHourSize20d = fhAvg20d.toDoubleOrNull() ?: 0.0,
            optionPrice = optionPrice.toDoubleOrNull() ?: 0.0,
            optionStrike = optStrike,
            index = selIndex,
            priceAboveOpen = aboveOpen,
            priceAboveVwap = aboveVwap,
            priceContextConfirmed = priceContextConfirmed,
            capital = capital.toDoubleOrNull() ?: 0.0,
            lotSize = lotSize.toIntOrNull() ?: 0,
            maxLossPerLot = maxLossPerLot.toDoubleOrNull() ?: 0.0,
            dailyPnlRs = journalPnl.dailyPnlRs,
            weeklyPnlRs = journalPnl.weeklyPnlRs,
            selectedSetup = selSetup
        )
        verdict = SetupMasterAgent.analyze(input)
        agentStep = 0
        isRunning = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark2), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = PurpleAcc, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Multi-Agent Setup Analyzer", color = PurpleAcc, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Rule, timing and risk checks run locally; the result is a checklist, not an AI forecast.", color = Color.LightGray, fontSize = 12.sp)
                    Text("IST: ${fmtHHMM(hhmm)}${if (isExpiry) "  |  CHAIN EXPIRY TODAY" else ""}", color = if (isExpiry) RedBear else GreenBull, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Agent status row ──
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgentStatusCard("Agent 1\nTime Context", agentStep >= 1 && agentStep <= 4, agentStep > 1, BlueCool, selectedAgent == 0, { selectedAgent = 0 }, Modifier.weight(1f))
                AgentStatusCard("Agent 2\nRule Check", agentStep >= 2 && agentStep <= 4, agentStep > 2, AmberWarn, selectedAgent == 1, { selectedAgent = 1 }, Modifier.weight(1f))
                AgentStatusCard("Agent 3\nRisk Guard", agentStep >= 3 && agentStep <= 4, agentStep > 3, GreenBull, selectedAgent == 2, { selectedAgent = 2 }, Modifier.weight(1f))
            }
        }
        if (selectedAgent >= 0) {
            item {
                val detail = when (selectedAgent) {
                    0 -> "Time Context checks the current IST window and the expiry date from the live option chain."
                    1 -> "Rule Check validates entered range, price, VWAP and premium inputs; missing required values block a pass."
                    else -> "Risk Guard requires positive capital and a calculated affordable lot count. Missing capital is not treated as safe."
                }
                Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                    Text(detail, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }

        // ── Input Section ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select checks to run", color = PurpleAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "ORB", 2 to "First-hour filter", 3 to "Hero Zero").forEach { (number, label) ->
                            FilterChip(
                                selected = selSetup == number,
                                onClick = { selSetup = number },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup 1 — ORB Inputs (9:45-10:30)", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiField("Range HIGH", rangeHigh, { rangeHigh = it }, Modifier.weight(1f))
                        AiField("Range LOW", rangeLow, { rangeLow = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiField("Current Price", currentPrice, { currentPrice = it }, Modifier.weight(1f))
                        AiField("VWAP", vwap, { vwap = it }, Modifier.weight(1f))
                    }
                    AiField("Nifty Level (default 22500)", niftyLevel, { niftyLevel = it }, Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup 2 — 1-Hour Candle (10:15 check)", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiField("1H Open", fhOpen, { fhOpen = it }, Modifier.weight(1f))
                        AiField("1H Close", fhClose, { fhClose = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiField("1H High", fhHigh, { fhHigh = it }, Modifier.weight(1f))
                        AiField("1H Low", fhLow, { fhLow = it }, Modifier.weight(1f))
                    }
                    AiField("Avg 1H size (20-day, leave 0 if unknown)", fhAvg20d, { fhAvg20d = it }, Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup 3 — Hero Zero (configured expiry window)", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    // Index selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NIFTY","BANKNIFTY","FINNIFTY").forEach { idx ->
                            val sel = selIndex == idx
                            OutlinedButton(
                                onClick = { selIndex = idx },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (sel) RedBear.copy(alpha = 0.2f) else Color.Transparent,
                                    contentColor = if (sel) RedBear else Color.Gray
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) RedBear else Color.DarkGray),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(idx, fontSize = 11.sp) }
                        }
                    }
                    // Strike
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Strike:", color = Color.Gray, fontSize = 12.sp)
                        listOf("ATM","1-OTM").forEach { s ->
                            val sel = optStrike == s
                            OutlinedButton(
                                onClick = { optStrike = s },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (sel) AmberWarn.copy(alpha = 0.2f) else Color.Transparent,
                                    contentColor = if (sel) AmberWarn else Color.Gray
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) AmberWarn else Color.DarkGray),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(s, fontSize = 11.sp) }
                        }
                    }
                    AiField("Observed option premium (₹)", optionPrice, { optionPrice = it }, Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = aboveOpen, onCheckedChange = { aboveOpen = it },
                            colors = CheckboxDefaults.colors(checkedColor = GreenBull))
                        Text("Price above today's open", color = Color.White, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = aboveVwap, onCheckedChange = { aboveVwap = it },
                            colors = CheckboxDefaults.colors(checkedColor = GreenBull))
                        Text("Price above VWAP", color = Color.White, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = priceContextConfirmed,
                            onCheckedChange = { priceContextConfirmed = it },
                            colors = CheckboxDefaults.colors(checkedColor = GreenBull)
                        )
                        Text("I verified both values against live price data", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Risk Guard inputs", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Journal P&L stops new setup checks at a 1% daily loss or 3% weekly loss of entered capital.", color = AmberWarn, fontSize = 11.sp)
                    AiField("Your capital (₹)", capital, { capital = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), KeyboardType.Number)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiField("Current lot size", lotSize, { lotSize = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                        AiField("Max loss / lot (₹; optional for HZ)", maxLossPerLot, { maxLossPerLot = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    Text("Risk Guard uses the entered max loss; Hero Zero defaults to premium × lot size. Verify current exchange lot size, charges and slippage.", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        // ── RUN BUTTON ──
        item {
            Button(
                onClick = { if (!isRunning) { verdict = null; isRunning = true } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAcc),
                enabled = !isRunning
            ) {
                if (isRunning) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Agents running...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Run Setup Checks", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }

        // ── VERDICT ──
        verdict?.let { v ->
            val vColor = when (v.verdict) {
                "GO"    -> GreenBull
                "WATCH" -> AmberWarn
                "WAIT"  -> BlueCool
                else    -> RedBear
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = vColor.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().border(2.dp, vColor, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = vColor, shape = RoundedCornerShape(8.dp)) {
                                Text("  ${v.verdict}  ", color = Color.Black, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(v.setup, color = vColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Side: ${v.side}  |  Rule checklist only (not probability)", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Entry/Stop/Target
                        VerdictRow("Entry", v.entryNote, GreenBull)
                        VerdictRow("Stop",  v.stopNote,  RedBear)
                        VerdictRow("Target",v.targetNote, AmberWarn)
                    }
                }
            }

            if (v.reasoning.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Agent Reasoning", color = PurpleAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            v.reasoning.forEach { r ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("▸ ", color = PurpleAcc, fontSize = 12.sp)
                                    Text(r, color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (v.warnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth().border(1.dp, RedBear.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Warnings", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            v.warnings.forEach { w ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("⚠ ", color = RedBear, fontSize = 12.sp)
                                    Text(w, color = Color(0xFFFFCDD2), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Agent logs
            item {
                var showLogs by remember { mutableStateOf(false) }
                Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showLogs = !showLogs },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agent Logs (${v.agentLogs.size} steps)", color = Color.Gray, fontSize = 12.sp)
                        }
                        if (showLogs) {
                            Spacer(Modifier.height(6.dp))
                            v.agentLogs.forEach { log ->
                                Text(log, color = Color(0xFF607D8B), fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))
                            }
                        }
                    }
                }
            }

            // ── Worked Example (from Section 2 image) ──
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Illustrative calculation — not a backtest or recommendation", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        WorkedRow("9:15-9:30 candle", "High / Low", "22,800 / 22,720")
                        WorkedRow("Range size check", "80 pts = 0.35% (less than 0.6%)", "OK, trade allowed")
                        WorkedRow("Signal", "9:45-10:00 candle closes at 22,812 (above 22,800) and above VWAP", "BUY CE")
                        WorkedRow("Entry", "10:00 candle opens at 22,815 → Buy 22,800 CE", "Option ≈ Rs 140")
                        WorkedRow("Stop (index)", "(22,800 + 22,720) / 2", "22,760")
                        WorkedRow("Risk (index)", "22,815 − 22,760", "55 pts")
                        WorkedRow("Target (index)", "22,815 + 2 × 55", "22,925")
                        WorkedRow("Option loss if SL hit", "≈ 0.5 (delta) × 55 × 65 qty", "≈ Rs 1,800 / lot")
                        WorkedRow("Option profit if TGT", "≈ 0.55 × 110 pts × 65 qty", "≈ Rs 3,900 / lot")
                        WorkedRow("Exit", "22,760 (stop) | 22,925 (target) | 15:15", "-")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── AI Agent helper composables ──────────────────────────────────────────────
@Composable
fun AgentStatusCard(
    label: String,
    isActive: Boolean,
    isDone: Boolean,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val bg = when {
        isDone   -> color.copy(alpha = 0.2f)
        isActive -> color.copy(alpha = 0.1f)
        else     -> CardDark
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isSelected) color.copy(alpha = 0.24f) else bg),
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isActive && !isDone) {
                CircularProgressIndicator(color = color, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null, tint = if (isDone) color else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (isDone || isActive) color else Color.Gray, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AiField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, kbType: KeyboardType = KeyboardType.Number) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = kbType),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PurpleAcc, focusedLabelColor = PurpleAcc,
            cursorColor = PurpleAcc, unfocusedBorderColor = Color.DarkGray,
            unfocusedLabelColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White
        )
    )
}

@Composable
fun VerdictRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun WorkedRow(item: String, calc: String, result: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(item, color = Color(0xFFB0BEC5), fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        Text(calc, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(2f))
        Text(result, color = AmberWarn, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// TAB 7 â€” PRE-TRADE CHECKLIST (SECTION 8)
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
@Composable
fun ChecklistTab() {
    val questions = listOf(
        "Is the market in a clear trend (not sideways/choppy)?",
        "Is today NOT expiry day for Nifty (avoid Tuesday if unsure)?",
        "Did you wait for the 9:15â€“9:30 candle to fully close?",
        "Is the setup matching one of your 5 defined setups (no random trades)?",
        "Is your max loss within 1R (single trade risk as per capital rule)?",
        "Have you checked the VIX â€” is it below 20 (not in panic zone)?"
    )

    val checks = remember { mutableStateListOf(*Array(questions.size) { false }) }
    var resetKey by remember { mutableStateOf(0) }

    val allYes = checks.all { it }
    val anyNo  = checks.any { !it }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "âœ… Pre-Trade Checklist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GreenBull
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Answer ALL 6 questions YES before entering any trade",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
        }

        itemsIndexed(questions) { idx, q ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (checks[idx]) Color(0xFF0A3D0A) else CardDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checks[idx],
                        onCheckedChange = { checks[idx] = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GreenBull,
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Q${idx + 1}",
                            fontSize = 10.sp,
                            color = AmberWarn,
                            fontWeight = FontWeight.Bold
                        )
                        Text(q, fontSize = 13.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (checks[idx]) "YES" else "NO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (checks[idx]) GreenBull else RedBear
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            // â”€â”€ Verdict card â”€â”€
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (allYes) Color(0xFF003300) else Color(0xFF330000)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (allYes) "ðŸŸ¢ TAKE THE TRADE" else "ðŸ”´ NOT READY",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (allYes) GreenBull else RedBear
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (allYes) "All conditions met â€” proceed with discipline"
                        else "Fix the NO answers before entering",
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // â”€â”€ Risk rules â”€â”€
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "â›” Daily / Weekly Stop Rules",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarn
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("â€¢ Daily Stop: 2Ã— per-trade risk â†’ STOP for the day", fontSize = 12.sp, color = Color.White)
                    Text("â€¢ Weekly Stop: 5Ã— per-trade risk â†’ no more trades that week", fontSize = 12.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ðŸ“Š Review Rule",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueCool
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "After 30 Setup-1 trades:\n  Avg loss > 0.25R? â†’ Back to paper trading\n  Avg win > 0.5R?  â†’ Scale up 1 lot",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // â”€â”€ Never Do card â”€â”€
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0000)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "ðŸš« Never Do",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedBear
                    )
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "Never average down on a losing option",
                        "Never trade on result / RBI days",
                        "Never hold through 3:15 PM",
                        "Never buy OTM just because it's cheap",
                        "Never increase size after 2 consecutive losses"
                    ).forEach { rule ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("âœ— ", fontSize = 12.sp, color = RedBear, fontWeight = FontWeight.Bold)
                            Text(rule, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // â”€â”€ Reset button â”€â”€
        item {
            Button(
                onClick = {
                    for (i in checks.indices) checks[i] = false
                    resetKey++
                },
                colors = ButtonDefaults.buttonColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberWarn)
                Spacer(Modifier.width(6.dp))
                Text("Reset Checklist", color = AmberWarn, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}