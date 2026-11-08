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

private fun isTuesday(): Boolean =
    istNow().get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY

// ─── Live Signal Model ───────────────────────────────────────────────────────
data class LiveSignal(
    val status: String,          // "TRADE", "WATCH", "WAIT", "CLOSED"
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
            "TRADE", "Setup 1 — ORB WINDOW OPEN",
            "9:45-10:00 — BEST TIME. Look for breakout!",
            listOf(
                "BUY CE: 15-min candle closes ABOVE range HIGH + above VWAP",
                "BUY PE: 15-min candle closes BELOW range LOW + below VWAP",
                "Entry: next candle open",
                "Stop: mid of range (High+Low)/2",
                "Target: Entry + 2×risk"
            ),
            GreenBull
        )
        hhmm < 1015 -> LiveSignal(
            "TRADE", "Setup 1 — ORB Still Open",
            "9:45-10:15 — ORB window still active",
            listOf(
                "Still valid: look for 15-min close outside range + VWAP side",
                "Entry on next candle open",
                "Stop: mid of range | Target: 2×risk"
            ),
            GreenBull
        )
        hhmm == 1015 || (hhmm in 1015..1030) -> LiveSignal(
            "TRADE", "Setup 2 — Check 1-Hour Candle Direction",
            "10:15 — Decide day direction (Setup 2)",
            listOf(
                "STRONG GREEN: Close>Open, body≥60% size, size≥1.2× avg → CE ONLY rest of day",
                "STRONG RED: No special direction (do NOT short just because of it)",
                "Any other candle: Follow Setup 1 rules only",
                "Also: last chance for Setup 1 entry before 10:30"
            ),
            BlueCool
        )
        hhmm < 1330 -> {
            val dir = if (hhmm < 1100) "MANAGE OPEN TRADE" else "HOLD — no new trades"
            LiveSignal(
                "WAIT", "Manage Trade Only",
                "10:30-13:30 — $dir",
                listOf(
                    "Only manage your existing open trade",
                    "No new entry unless it matches Setup 2 direction",
                    "Move stop to breakeven if trade is up 1R",
                    "Hard stop at 15:15 for all positions"
                ),
                Color.Gray
            )
        }
        hhmm < 1315 -> LiveSignal(
            "WAIT", "No New Trades",
            "After 13:30 — NO new trades allowed",
            listOf(
                "Only exception: Setup 3 Hero Zero on expiry day at 13:15",
                "Manage existing positions only"
            ),
            RedBear
        )
        isExpiry && hhmm in 1315..1400 -> LiveSignal(
            "TRADE", "Setup 3 — HERO ZERO WINDOW",
            "13:15 EXPIRY — Hero Zero check NOW!",
            listOf(
                "Direction: Price ABOVE open+VWAP → BUY CE",
                "Direction: Price BELOW open+VWAP → BUY PE",
                "Mixed signal → NO TRADE",
                "Strike: ATM or 1-OTM only",
                "Nifty ATM max price: Rs 24 | BN ATM: Rs 36",
                "If price above max → NO TRADE",
                "Risk: 0.25-0.5% capital only",
                "Exit: 3× buy price OR 15:15"
            ),
            RedBear
        )
        hhmm in 1315..1400 && !isExpiry -> LiveSignal(
            "WAIT", "No New Trades",
            "After 13:30 — market day winding down",
            listOf("No new trades today", "Close everything by 15:15", "Write your journal"),
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

// ─── Trade Journal Storage (in-memory for session) ────────────────────────────
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
fun SimpleSetupScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Signal", "AI Agent", "Day Plan", "Setups", "Hero Zero", "Journal", "Risk Calc", "Checklist")

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
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == i) GreenBull else Color.Gray
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> SignalTab()
            1 -> AiAgentTab()
            2 -> DayPlanTab()
            3 -> SetupsTab()
            4 -> HeroZeroTab()
            5 -> JournalTab()
            6 -> RiskCalcTab()
            7 -> ChecklistTab()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 0 — LIVE SIGNAL (THE MAIN NEW FEATURE)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SignalTab() {
    var hhmm by remember { mutableStateOf(istHHMM()) }
    val isExpiry = isTuesday()

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
                                .background(if (signal.status == "TRADE") GreenBull else if (signal.status == "WATCH") AmberWarn else Color.Gray)
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
                    QuickRule("One trade per day max (Setup 1).")
                    QuickRule("Only CE after STRONG GREEN 1-hour candle (Setup 2).")
                    QuickRule("Never trade after 13:30 (except expiry Setup 3).")
                    QuickRule("Max loss per trade: 0.5% of capital.")
                    QuickRule("Daily stop: 2× per-trade risk. Weekly: 5× risk.")
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
                        Text("NEVER Do These", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

        // ── BACKTEST PROOF ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Backtest Reference (Section 9)", color = PurpleAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    BacktestRow("Setup 1 (Nifty ORB original)", "43 trades", "+0.06R", AmberWarn)
                    BacktestRow("Setup 2 (1-hr STRONG GREEN)", "43 days", "Nifty up 69.8%", GreenBull)
                    BacktestRow("Setup 3 (Hero Zero ATM)", "94 expiries", "Avg payoff Rs 39.3", BlueCool)
                    BacktestRow("Setup 4 (Shooting star)", "45 Nifty trades", "+0.86R", Color.Gray)
                    BacktestRow("Setup 5 (Prev day H/L)", "28 trades", "+0.12R", Color.Gray)
                    Divider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        "⚠ No setup has proven profits at 1 lot yet. Paper trade Setup 1 for 3+ months before real money.",
                        color = Color(0xFFFFCDD2),
                        fontSize = 11.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 1 — DAY PLAN
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DayPlanTab() {
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
        Slot("9:45 – 10:30",  "BEST TIME → Setup 1 ORB. ONE trade only.",       945,  1030, GreenBull),
        Slot("10:15",         "Setup 2: 1-hour direction → CE or PE allowed?",  1015, 1016, BlueCool),
        Slot("10:30 – 13:30", "Manage open trade only. No new entry.",          1030, 1330, Color.Gray),
        Slot("13:15 (expiry)","Hero Zero Setup 3. Nifty ATM ≤ Rs 24.",          1315, 1400, RedBear),
        Slot("After 13:30",   "NO NEW TRADES. Exception: expiry Setup 3 only.", 1330, 1515, RedBear),
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
                    if (isTuesday()) {
                        Surface(color = RedBear.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                            Text("  NIFTY EXPIRY (Tue)  ", color = RedBear, fontSize = 11.sp,
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
    "+0.06R on 43 Nifty trades — NOT profitable yet. Paper trade only.", AmberWarn
) {
    RuleRow("When", "9:15-9:30 candle forms. Mark its HIGH and LOW.")
    RuleRow("Range check", "Size must be < 0.6% of Nifty. Skip today if too wide.")
    RuleRow("BUY CE", "9:45-10:00: 15-min candle CLOSES above HIGH AND above VWAP.")
    RuleRow("BUY PE", "9:45-10:00: 15-min candle CLOSES below LOW AND below VWAP.")
    RuleRow("Entry", "Next 5-min candle open.")
    RuleRow("Stop", "(High + Low) / 2 — midpoint of the opening range.")
    RuleRow("Target", "Entry + 2× risk (index points).")
    RuleRow("Skip if", "First close is on WRONG side of VWAP, or vs Setup 2 direction.")
    RuleRow("Review", "After 30 trades: avg loss > 0.25R? → back to paper trading.")
}

@Composable fun SetupCard2() = SetupCardBase(
    "Setup 2 — First-Hour Direction (10:15 Filter)", "🔵 FILTER ONLY — not a trade", BlueCool,
    "Nifty closed higher 69.8% of time after STRONG GREEN. Use as filter, not buy signal.", BlueCool
) {
    RuleRow("When", "At 10:15 — the 9:15-10:15 (1-hour) candle is complete.")
    RuleRow("STRONG GREEN", "Close > Open, body ≥ 60% of size, size ≥ 1.2× 20-day avg → CE only all day.")
    RuleRow("STRONG RED", "No special direction. Red 1st hour ≠ day falls. Do NOT short just for this.")
    RuleRow("Any other", "No direction. Follow Setup 1 rules only.")
    RuleRow("Note", "Avg move after 10:15 ≈ +40 Nifty pts only. Use as filter, not entry reason.")
}

@Composable fun SetupCard3() = SetupCardBase(
    "Setup 3 — Expiry Day Hero Zero (13:15 only)", "🔴 EXPIRY ONLY — small money", RedBear,
    "Nifty ATM avg payoff Rs 33.3. Max price Rs 24 keeps you safe. BN ATM max Rs 36.", RedBear
) {
    RuleRow("When", "13:15 IST — EXPIRY DAY only. (Nifty = Tuesday weekly.)")
    RuleRow("Never", "Buy cheap options at 9:15 on expiry. 62-98% become zero.")
    RuleRow("Direction", "Price ABOVE open AND VWAP → CE. BELOW open AND VWAP → PE. Mixed → NO TRADE.")
    RuleRow("Strike", "ATM (nearest) or 1-OTM only. NEVER 2+ strikes away.")
    RuleRow("Max price", "Nifty ATM ≤ Rs 24 | Nifty 1-OTM ≤ Rs 7 | BN ATM ≤ Rs 36 | BN 1-OTM ≤ Rs 11")
    RuleRow("Money", "Risk 0.25-0.5% capital only. Expect to lose most of the time.")
    RuleRow("Exit", "Sell at 3× buy price OR 15:15 — whichever comes first. No stop loss.")
}

@Composable fun SetupCard4() = SetupCardBase(
    "Setup 4 — Shooting Star on 15-min (after a rise)", "PAPER ONLY", Color.Gray,
    "+0.86R Nifty (45 trades). Too few trades to trust. Paper trade only.", Color.Gray
) {
    RuleRow("Look for", "Nifty gone UP ≥ 5 candles. 15-min candle has LONG upper wick. Small body.")
    RuleRow("Entry", "BUY PE at start of NEXT 15-min candle. Best 9:45-10:30. No entry after 14:30.")
    RuleRow("Stop", "High of the shooting star candle.")
    RuleRow("Target", "Index goes 30 pts above high of shooting star candle.")
    RuleRow("Exit", "2× risk (risk = stop − entry). Or 15:15.")
}

@Composable fun SetupCard5() = SetupCardBase(
    "Setup 5 — Previous Day H/L Breakout (5-min)", "PAPER ONLY — Nifty only", Color.Gray,
    "+0.12R on 28 trades (Nifty). Bank Nifty lost money — NEVER use on BN.", Color.Gray
) {
    RuleRow("Skip", "If Nifty gaps above yesterday's high or below yesterday's low.")
    RuleRow("BUY CE", "5-min candle CLOSES above yesterday's high. Window: 9:30-11:00.")
    RuleRow("BUY PE", "5-min candle CLOSES below yesterday's low.")
    RuleRow("Entry", "Start of next 5-min candle.")
    RuleRow("Stop", "0.25% below (CE) or above (PE) the next 5-min candle.")
    RuleRow("Target", "2× risk. Exit at 15:15 if no target hit.")
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
                Text("6. NEVER Do These (data says they lose)", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            listOf(
                "Buy just because of a 5-min hammer / bullish engulfing candle",
                "Trade OI walls (call/put OI) as support or resistance",
                "Expiry: buy to lose at max pain",
                "Buy after a gap-down hoping it fills",
                "Take Nifty trades after 13:30",
                "Trade Bank Nifty midday or end-of-day setups",
                "Buy cheap options on expiry morning (62-98% become zero)",
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
fun HeroZeroTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark2), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12. Hero Zero Deep Dive", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("OTM CE/PE opened at Rs 1-20 at 9:15 on expiry day. Buy at open + Rs 0.5 spread. Rs 50 charges per trade.", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Max Price at 13:15 (Section 4)", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    HeroTableRow("Index", "Strike", "Avg Payoff", "MAX PAY", true)
                    HeroTableRow("Nifty",      "ATM",             "Rs 33.3",  "Rs 24")
                    HeroTableRow("Nifty",      "1 OTM (50 pts)",  "Rs 13.5",  "Rs 7")
                    HeroTableRow("Bank Nifty", "ATM",             "Rs 78.4",  "Rs 36")
                    HeroTableRow("Bank Nifty", "1 OTM (100 pts)", "Rs 41.5",  "Rs 11")
                    Spacer(Modifier.height(6.dp))
                    Text("If price ABOVE max → NO TRADE (loses money on average)", color = Color(0xFFFFCDD2), fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12.1 Which Exit is Best? (Rs 10-20 options)", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    InfoBullet("No exit makes money on any index.", RedBear)
                    InfoBullet("Least bad: Sensex sell at Rs 200 (−15% avg).", AmberWarn)
                    InfoBullet("Nifty hold to expiry: −38% avg.", RedBear)
                    InfoBullet("Bank Nifty sell at Rs 200: −69%. AVOID BN hero zero.", RedBear)
                    InfoBullet("Sensex least bad → Nifty worse → Bank Nifty worst.", AmberWarn)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12.4 Rules if You Still Want to Buy", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    RuleRow("NEVER buy", "Rs 0.05-2 options. Almost never reach Rs 100. Lose ~100%.")
                    RuleRow("Best range", "Rs 5-10, strike 0.5-1.5% away from index.")
                    RuleRow("Prefer", "Sensex or Nifty. AVOID Bank Nifty.")
                    RuleRow("Sell order", "PUT Rs 100 or 10× SELL order AS SOON as you buy.")
                    RuleRow("Timing", "Buy only after market starts trending (see 13:15 rule).")
                    RuleRow("Max spend", "0.25% capital per expiry. 30-40 losing expiries in a row happen.")
                    RuleRow("Exit", "3× buy price OR 15:15 — whichever first. No stop loss.")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("10. Sensex Special (Section 10 rules)", color = PurpleAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    RuleRow("SX-1 (main)", "13:15 Sensex expiry. VWAP side rule. ATM ≤ Rs 84 | 1-OTM ≤ Rs 43.")
                    RuleRow("SX-2 (early)", "17:15 same side rule. ATM ≤ Rs 119.")
                    RuleRow("SX-3 (breakout)", "13:15 — top/bottom 15% of day range. ATM ≤ Rs 77.")
                    RuleRow("SX-5 (straddle)", "13:15 — buy ATM CE+PE together if no direction. Both ≤ Rs 160 total.")
                    Spacer(Modifier.height(4.dp))
                    Text("Best entry window Sensex: 10:30-11:30. Entries after 13:30 lose.", color = AmberWarn, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, RedBear, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Honest Truth (Section 9 Bottom Line)", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No setup in this guide has proven profits yet.\n\n" +
                        "Paper trade Setup 1 for at least 3 months before real money.\n" +
                        "Use Setups 2 and 3 only with very small money.\n\n" +
                        "Hero zero earns only if you get the price AT OR BELOW the max price. At the usual market price it lost Rs 600-870 per expiry.",
                        color = Color(0xFFFFCDD2), fontSize = 12.sp
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB 4 — TRADE JOURNAL (from image section 8)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun JournalTab() {
    val entries = remember { mutableStateListOf<JournalEntry>() }
    var showForm by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(1) }

    // Form fields
    var fDate      by remember { mutableStateOf("") }
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
                            JournalField("Date (DD/MM)", fDate, { fDate = it }, Modifier.weight(1f))
                            JournalField("Entry Time", fEntryTime, { fEntryTime = it }, Modifier.weight(1f))
                        }

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
                                entries.add(0, JournalEntry(
                                    nextId++, fDate, fSetup, fEntryTime, fSide, fStrike,
                                    fBuyPrice, fIdxEntry, fStop, fTarget, fExitTime, fExitReason, fPnl, fRules
                                ))
                                // reset
                                fDate = ""; fEntryTime = ""; fStrike = ""; fBuyPrice = ""
                                fIdxEntry = ""; fStop = ""; fTarget = ""; fExitTime = ""
                                fExitReason = ""; fPnl = ""; fRules = true; fSetup = "Setup 1"; fSide = "CE"
                                showForm = false
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
    val capital = capitalStr.toDoubleOrNull() ?: 0.0
    val maxRisk05  = capital * 0.005
    val maxRisk025 = capital * 0.0025
    val niftyRiskPerLot = 1800.0
    val bnRiskPerLot    = 1500.0
    val niftyLots = if (capital > 0) (maxRisk05 / niftyRiskPerLot).toInt().coerceAtLeast(0) else 0
    val bnLots    = if (capital > 0) (maxRisk05 / bnRiskPerLot).toInt().coerceAtLeast(0) else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark2), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("7. Money Rules — Risk Calculator", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Max risk: 0.5% per trade. Hero Zero: 0.25% per expiry.", color = Color.LightGray, fontSize = 12.sp)
                    Text("Nifty lot = 65 qty | Bank Nifty lot = 30 qty", color = Color.LightGray, fontSize = 12.sp)
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
                        RiskRow("Nifty lots (≈Rs 1,800/lot)", "$niftyLots lot${if (niftyLots != 1) "s" else ""} (${niftyLots * 65} qty)", GreenBull)
                        RiskRow("BankNifty lots (≈Rs 1,500/lot)", "$bnLots lot${if (bnLots != 1) "s" else ""} (${bnLots * 30} qty)", GreenBull)
                        if (niftyLots == 0) {
                            Text("⚠ Capital too small for 1 Nifty lot. PAPER TRADE until capital grows.", color = RedBear, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Rs 2,00,000 → Rs 1,000 max → Paper trade only (< 1 lot)", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 4,00,000 → Rs 2,000 max → 1 Nifty lot (book example)", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 8,00,000 → Rs 4,000 max → 2 Nifty lots / 2 BN lots", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 16,00,000 → Rs 8,000 max → 4 Nifty lots / 5 BN lots", color = Color.LightGray, fontSize = 12.sp)
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
            Text("Backtest: $backtest", color = Color.Gray, fontSize = 11.sp)
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
fun HeroTableRow(c1: String, c2: String, c3: String, c4: String, isHeader: Boolean = false) {
    val w = if (isHeader) FontWeight.Bold else FontWeight.Normal
    val col = if (isHeader) AmberWarn else Color.White
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(c1, color = col, fontSize = 11.sp, fontWeight = w, modifier = Modifier.weight(1.2f))
        Text(c2, color = col, fontSize = 11.sp, fontWeight = w, modifier = Modifier.weight(1.5f))
        Text(c3, color = col, fontSize = 11.sp, fontWeight = w, modifier = Modifier.weight(1.5f))
        Text(c4, color = if (isHeader) col else GreenBull, fontSize = 11.sp, fontWeight = w, modifier = Modifier.weight(1f))
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
fun BacktestRow(setup: String, sample: String, result: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(setup, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(2f))
        Text(sample, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        Text(result, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
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
fun AiAgentTab() {
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
    var optStrike    by remember { mutableStateOf("ATM") }
    var selIndex     by remember { mutableStateOf("NIFTY") }
    var aboveOpen    by remember { mutableStateOf(false) }
    var aboveVwap    by remember { mutableStateOf(false) }
    var capital      by remember { mutableStateOf("") }
    var selSetup     by remember { mutableIntStateOf(1) }

    var verdict      by remember { mutableStateOf<SetupMasterVerdict?>(null) }
    var isRunning    by remember { mutableStateOf(false) }
    var agentStep    by remember { mutableIntStateOf(0) } // 0=idle,1=A1,2=A2,3=A3,4=master

    val hhmm = remember { istHHMM() }
    val isExpiry = remember { isTuesday() }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        agentStep = 1; kotlinx.coroutines.delay(600)
        agentStep = 2; kotlinx.coroutines.delay(600)
        agentStep = 3; kotlinx.coroutines.delay(600)
        agentStep = 4; kotlinx.coroutines.delay(400)
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
            capital = capital.toDoubleOrNull() ?: 0.0,
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
                    Text("3 AI sub-agents analyze your inputs → Master gives GO / WAIT / NO-GO", color = Color.LightGray, fontSize = 12.sp)
                    Text("IST: ${fmtHHMM(hhmm)}${if (isExpiry) "  |  EXPIRY DAY" else ""}", color = if (isExpiry) RedBear else GreenBull, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Agent status row ──
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgentStatusCard("Agent 1\nTime Context", agentStep >= 1 && agentStep <= 4, agentStep > 1, BlueCool, Modifier.weight(1f))
                AgentStatusCard("Agent 2\nRule Check", agentStep >= 2 && agentStep <= 4, agentStep > 2, AmberWarn, Modifier.weight(1f))
                AgentStatusCard("Agent 3\nRisk Guard", agentStep >= 3 && agentStep <= 4, agentStep > 3, GreenBull, Modifier.weight(1f))
            }
        }

        // ── Input Section ──
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
                    Text("Setup 3 — Hero Zero (expiry 13:15)", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    // Index selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NIFTY","BANKNIFTY","SENSEX").forEach { idx ->
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
                    AiField("Option Price (Rs)", optionPrice, { optionPrice = it }, Modifier.fillMaxWidth())
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
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Capital (Rs) — for Risk Guard Agent", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    AiField("Your Capital (Rs)", capital, { capital = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), KeyboardType.Number)
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
                    Text("Run AI Analysis", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
                                Text("Side: ${v.side}  |  Confidence: ${v.confidence}%", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Confidence bar
                        LinearProgressIndicator(
                            progress = v.confidence / 100f,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = vColor,
                            trackColor = Color.DarkGray
                        )
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
                        Text("Book Worked Example — Setup 1 CE Side", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

        // ── Section 9 Backtest Table (always visible) ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Section 9 — Backtest of Exact Rules (last 60 days)", color = PurpleAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Option profit per lot = 0.5 × index pts × lot size − Rs 2 spread", color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    BacktestTableHeader()
                    BacktestTableRow("Nifty", "A: original rules (9:45-11:15, no time stop)", "43", "46.5%", "+0.06", "-12,562", "-17,443", AmberWarn)
                    BacktestTableRow("Nifty", "B: +60-min time stop", "43", "44.2%", "-0.11", "-14,801", "-16,085", RedBear)
                    BacktestTableRow("Nifty", "C: entry only 9:45-10:30", "35", "43.6%", "-0.11", "-6,811", "-16,885", RedBear)
                    BacktestTableRow("Nifty", "D: EXACT rules (time window + first-hour filter)", "31", "43.6%", "-0.19", "-5,211", "-16,088", RedBear)
                    BacktestTableRow("BankNifty", "A: original rules (9:45-11:15, no time stop)", "32", "37.5%", "-0.13", "-13,262", "-23,619", RedBear)
                    BacktestTableRow("BankNifty", "B: +30-min time stop", "32", "43.8%", "+0.09", "+4,396", "-2,137", AmberWarn)
                    BacktestTableRow("BankNifty", "C: entry only 9:45-10:30", "26", "42.3%", "+0.16", "+5,789", "-3,517", AmberWarn)
                    BacktestTableRow("BankNifty", "D: EXACT rules (time window + first-hour filter)", "26", "42.3%", "+0.34", "+5,750", "-7,513", AmberWarn)
                    Spacer(Modifier.height(8.dp))
                    Text("Key findings:", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("• Setup 1 NOT profitable as written. Adding 60-min time stop made Nifty worse.", color = Color.LightGray, fontSize = 11.sp)
                    Text("• Bank Nifty looked better WITH time stop (only 26-32 trades, 95% range includes losses).", color = Color.LightGray, fontSize = 11.sp)
                    Text("• First-hour filter (Setup 2) did not change any trade in these 60 days.", color = Color.LightGray, fontSize = 11.sp)
                    Text("• Every 95% range crosses zero — no version proven to make/lose money.", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Setup 3 (Hero Zero) with exact open+VWAP rule:", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Nifty: 44 expiries traded, 25 skipped (mixed signal), ATM avg payoff Rs 46(90)", color = Color.LightGray, fontSize = 11.sp)
                    Text("BankNifty: 89 expiries traded, ATM avg payoff Rs 69.7", color = Color.LightGray, fontSize = 11.sp)
                    Text("Max price stays well below values → price rule is on the safe side.", color = GreenBull, fontSize = 11.sp)
                }
            }
        }

        // ── HZ Variants Table (Section 10.3) ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Section 10.3 — Sensex Hero Zero Variants", color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Max price to pay per unit (lot = 20 for 2024, 10 from 2025)", color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    HZVariantHeader()
                    HZVariantRow("HZ-1 trend+VWAP hero (main)",   "13:15","ATM",   "118","61.9%","Rs 139","Rs 84")
                    HZVariantRow("HZ-1 trend+VWAP early",          "17:15","ATM",   "116","50.0%","Rs 86", "Rs 63")
                    HZVariantRow("HZ-2 ATM hero (no filter)",      "13:15","ATM",   "116","63.0%","Rs 164","Rs 109")
                    HZVariantRow("HZ-2 ATM hero (more time)",      "12:15","ATM",   "116","63.0%","Rs 184","Rs 109")
                    HZVariantRow("HZ-3 Breakfast hero (day H/L)",  "10:30","ATM",   "68", "44.8%","Rs 211","Rs 40")
                    HZVariantRow("HZ-4 late hero (last hour)",      "14:12","ATM",   "123","58.2%","Rs 416","Rs 54")
                    HZVariantRow("HZ-5 straddle CE+PE",            "13:15","CE+PE", "147","100%", "Rs 141","Rs 160")
                    HZVariantRow("Against day (red on green)",      "13:15","ATM",   "14", "35.8%","Rs 44", "Rs 48")
                    HZVariantRow("HZ-5 hero against day",          "17:15","1-OTM", "147","15.8%","Rs 44", "Rs 23")
                    Spacer(Modifier.height(6.dp))
                    Text("Simple rules per HZ variant:", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("HZ-1(main): 13:15, Rule SX-E1 side, ATM. Buy only if price < Rs 84. 1-OTM if price < Rs 43.", color = Color.LightGray, fontSize = 11.sp)
                    Text("HZ-1(early): 17:15 same side rule. ATM below Rs 119. More time = higher fair value but costs more.", color = Color.LightGray, fontSize = 11.sp)
                    Text("HZ-2(breakout): 13:15, within top 15% of day range (CE) or bottom 15% (PE). ATM < Rs 77.", color = Color.LightGray, fontSize = 11.sp)
                    Text("HZ-4(late): 14:15 ATM only, below Rs 100 for OTM. Only if 2-OTM became mid-OTM 17.2% of time.", color = Color.LightGray, fontSize = 11.sp)
                    Text("HZ-5(straddle): 13:15, buy ATM CE+PE if no direction. Only if BOTH together cost ≤ Rs 160.", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }

        // ── Sensex Plan in 5 lines ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BlueCool.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, BlueCool.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Sensex Plan in 5 Lines (Section 10)", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "1. On non-expiry days: treat Sensex like Nifty — first-hour direction; paper-trade the ORB; best entries 10:30-13:30.",
                        "2. On expiry day: do nothing until 13:15.",
                        "3. At 13:15 use Rule SX-E1 to pick the side.",
                        "4. Buy the ATM option ONLY if it is below Rs 84; otherwise no trade.",
                        "5. Exit at 3× or 15:15."
                    ).forEachIndexed { i, s ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("${i+1}. ", color = BlueCool, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(s, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── AI Agent helper composables ──────────────────────────────────────────────
@Composable
fun AgentStatusCard(label: String, isActive: Boolean, isDone: Boolean, color: Color, modifier: Modifier) {
    val bg = when {
        isDone   -> color.copy(alpha = 0.2f)
        isActive -> color.copy(alpha = 0.1f)
        else     -> CardDark
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = modifier) {
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

@Composable
fun BacktestTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("Index",   color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Version", color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.5f))
        Text("Trd",     color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
        Text("Win%",    color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
        Text("AvgR",    color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
        Text("Rs/lot",  color = AmberWarn, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
    }
    Divider(color = Color.DarkGray, thickness = 0.5.dp)
}

@Composable
fun BacktestTableRow(index: String, ver: String, trades: String, win: String, avgR: String, rsLot: String, drawdown: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(index,  color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(ver,    color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(2.5f))
        Text(trades, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(0.5f))
        Text(win,    color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(0.7f))
        Text(avgR,   color = color,           fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
        Text(rsLot,  color = color,           fontSize = 10.sp, modifier = Modifier.weight(0.9f))
    }
}

@Composable
fun HZVariantHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("Variant", color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.5f))
        Text("Time",    color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
        Text("Strike",  color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
        Text("Exp",     color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
        Text("0%",      color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
        Text("AvgVal",  color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
        Text("MAX",     color = PurpleAcc, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
    }
    Divider(color = Color.DarkGray, thickness = 0.5.dp)
}

@Composable
fun HZVariantRow(variant: String, time: String, strike: String, exp: String, zeroP: String, avg: String, max: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(variant, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(2.5f))
        Text(time,    color = Color.Gray,       fontSize = 10.sp, modifier = Modifier.weight(0.6f))
        Text(strike,  color = Color.Gray,       fontSize = 10.sp, modifier = Modifier.weight(0.6f))
        Text(exp,     color = Color.Gray,       fontSize = 10.sp, modifier = Modifier.weight(0.5f))
        Text(zeroP,   color = RedBear,          fontSize = 10.sp, modifier = Modifier.weight(0.6f))
        Text(avg,     color = AmberWarn,        fontSize = 10.sp, modifier = Modifier.weight(0.7f))
        Text(max,     color = GreenBull,        fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
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