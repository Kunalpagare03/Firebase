package com.Kunal.stock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private fun todaySlot(hhmm: Int): String = when {
    hhmm < 915  -> "PRE_MARKET"
    hhmm < 930  -> "SLOT_915_930"
    hhmm < 945  -> "SLOT_930_945"
    hhmm < 1030 -> "SLOT_945_1030"
    hhmm < 1015 -> "SLOT_1015"
    hhmm < 1330 -> "SLOT_1030_1330"
    hhmm < 1315 -> "SLOT_1315_EXPIRY"
    hhmm < 1515 -> "AFTER_1330"
    else        -> "CLOSE"
}

private fun isTodayTuesday(): Boolean =
    istNow().get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY

@Composable
fun SimpleSetupScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Day Plan", "Setups", "Hero Zero", "Checklist", "Risk Calc")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        // Tab Row
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
            0 -> DayPlanTab()
            1 -> SetupsTab()
            2 -> HeroZeroTab()
            3 -> ChecklistTab()
            4 -> RiskCalcTab()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 1. DAY PLAN TAB — Time-based schedule with live IST slot highlight
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
    val activeSlot = todaySlot(hhmm)

    data class TimeSlot(
        val time: String,
        val action: String,
        val slot: String,
        val urgency: Color
    )

    val slots = listOf(
        TimeSlot("Before 9:15",    "Write: yesterday H/L/Close. UP or DOWN day?",        "PRE_MARKET",      BlueCool),
        TimeSlot("9:15 – 9:30",   "DO NOTHING. Watch 1st 15-min candle. Mark H & L.",    "SLOT_915_930",    AmberWarn),
        TimeSlot("9:30 – 9:45",   "Still wait. Check candle size (Setup 1 rule).",       "SLOT_930_945",    AmberWarn),
        TimeSlot("9:45 – 10:30",  "BEST TIME → Setup 1 (ORB). One trade only.",         "SLOT_945_1030",   GreenBull),
        TimeSlot("10:15",         "Check 1-hour candle direction → CE or PE? (Setup 2).","SLOT_1015",       BlueCool),
        TimeSlot("10:30 – 13:30", "Manage open trade only. No new entry.",              "SLOT_1030_1330",  Color.Gray),
        TimeSlot("13:15 (expiry)","Hero Zero check. Nifty ATM ≤ Rs 24. VWAP dir only.", "SLOT_1315_EXPIRY",RedBear),
        TimeSlot("After 13:30",   "NO NEW TRADES. Exception: expiry Setup 3 only.",     "AFTER_1330",      RedBear),
        TimeSlot("15:15",         "Close everything. Write journal.",                    "CLOSE",           Color.Gray)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            // Current time banner
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = GreenBull, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    val h = hhmm / 100
                    val m = hhmm % 100
                    Text(
                        "IST  %02d:%02d".format(h, m),
                        color = GreenBull,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    if (isTodayTuesday()) {
                        Surface(
                            color = RedBear.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "  NIFTY EXPIRY DAY (Tue)  ",
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

        items(slots) { slot ->
            val isActive = slot.slot == activeSlot
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) slot.urgency.copy(alpha = 0.18f) else CardDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isActive) Modifier.border(1.5.dp, slot.urgency, RoundedCornerShape(10.dp))
                        else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.width(90.dp)) {
                        Text(slot.time, color = slot.urgency, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (isActive) {
                            Text("▶ NOW", color = slot.urgency, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(slot.action, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. SETUPS TAB
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

@Composable
fun SetupCard1() {
    SetupCardBase(
        title = "Setup 1 — 15-min Opening Range Breakout",
        badge = "PAPER TRADE FIRST",
        badgeColor = AmberWarn,
        backtest = "+0.06R on 43 Nifty trades (not profitable yet — paper only)",
        color = AmberWarn
    ) {
        RuleRow("When", "9:15-9:30 candle forms. Mark its HIGH and LOW.")
        RuleRow("Range check", "Size must be < 0.6% of Nifty. Skip if too wide.")
        RuleRow("BUY CE", "9:45-10:00 candle CLOSES above the HIGH AND above VWAP.")
        RuleRow("BUY PE", "9:45-10:00 candle CLOSES below the LOW AND below VWAP.")
        RuleRow("Entry", "Next 5-min candle open.")
        RuleRow("Stop (index)", "(High + Low) / 2  ← midpoint of the range.")
        RuleRow("Target (index)", "Entry + 2 × risk.")
        RuleRow("Skip if", "First close is on WRONG side of VWAP, or against Setup 2 after 10:15.")
        RuleRow("Review rule", "After 30 real trades: avg loss > 0.25R? → back to paper.")
    }
}

@Composable
fun SetupCard2() {
    SetupCardBase(
        title = "Setup 2 — First-Hour Direction (10:15 Check)",
        badge = "FILTER ONLY — not a trade",
        badgeColor = BlueCool,
        backtest = "Nifty closed higher 69.8% of time after STRONG GREEN. Grade B signal.",
        color = BlueCool
    ) {
        RuleRow("When", "At 10:15 — the 9:15-10:15 candle is complete.")
        RuleRow("STRONG GREEN", "Close > Open, body ≥ 60% of size, size ≥ 1.2× 20-day avg → CE only all day.")
        RuleRow("STRONG RED", "No special direction. Red first hour ≠ day falls. Do not short because of it.")
        RuleRow("Any other", "No direction. Follow Setup 1 rules only.")
        RuleRow("Exit", "Not a trade. Direction valid until 15:15.")
        RuleRow("Note", "Use it as a filter, not a reason to buy alone. Avg move after 10:15 ≈ +40 Nifty pts.")
    }
}

@Composable
fun SetupCard3() {
    SetupCardBase(
        title = "Setup 3 — Expiry Day Hero Zero (13:15)",
        badge = "EXPIRY ONLY — small money",
        badgeColor = RedBear,
        backtest = "Nifty ATM avg expiry payoff Rs 39.3. Max price Rs 24 → rule keeps you safe.",
        color = RedBear
    ) {
        RuleRow("When", "13:15 IST on EXPIRY day only. (Nifty = Tuesday weekly).")
        RuleRow("Never", "Buy cheap options at 9:15 on expiry. 62-98% become zero.")
        RuleRow("Direction", "Price ABOVE open AND VWAP → CE. BELOW open AND VWAP → PE. Mixed → NO TRADE.")
        RuleRow("Strike", "ATM (nearest) or 1 strike OTM only. Never 2+ strikes away.")
        RuleRow("Price rule", "BUY only if option price < Max price (see table below). Higher → NO TRADE.")
        RuleRow("Max prices", "Nifty ATM ≤ Rs 24 | Nifty 1-OTM ≤ Rs 7 | BankNifty ATM ≤ Rs 36 | BN 1-OTM ≤ Rs 11")
        RuleRow("Money", "Risk only 0.25-0.5% capital. Expect to lose most of the time.")
        RuleRow("Exit", "Sell at 3× buy price OR at 15:15 — whichever first. No stop loss.")
    }
}

@Composable
fun SetupCard4() {
    SetupCardBase(
        title = "Setup 4 — Shooting Star on 15-min (after a rise)",
        badge = "PAPER ONLY",
        badgeColor = Color.Gray,
        backtest = "+0.86R Nifty (45 trades). Too few trades to trust. Paper trade.",
        color = Color.Gray
    ) {
        RuleRow("Look for", "Nifty gone UP for ≥ 5 candles. 15-min candle has LONG upper wick. Smaller body than normal.")
        RuleRow("Entry", "Sell PE at start of NEXT 15-min candle. Best 9:45–10:30. No entry after 14:30.")
        RuleRow("Stop loss", "High of the shooting star candle.")
        RuleRow("Target", "Index goes 30 points above high of the star candle.")
        RuleRow("Exit", "2× risk (risk = stop − entry price). Or 15:15.")
    }
}

@Composable
fun SetupCard5() {
    SetupCardBase(
        title = "Setup 5 — Previous Day High/Low Breakout (5-min)",
        badge = "PAPER ONLY — Nifty only",
        badgeColor = Color.Gray,
        backtest = "+0.12R on 28 trades (Nifty). Bank Nifty lost money — never use on BN.",
        color = Color.Gray
    ) {
        RuleRow("Skip", "If Nifty gaps above yesterday's high or below yesterday's low (gap day).")
        RuleRow("BUY CE", "5-min candle CLOSES above yesterday's high. Window: 9:30–11:00.")
        RuleRow("BUY PE", "5-min candle CLOSES below yesterday's low.")
        RuleRow("Entry", "Start of next 5-min candle.")
        RuleRow("Stop loss", "0.25% below (CE) or above (PE) the next 5-min candle low/high.")
        RuleRow("Target", "2× risk. Exit at 15:15 if no target.")
    }
}

@Composable
fun NeverDoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RedBear, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = RedBear, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("6. NEVER Do These (data says they lose)", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            val nevers = listOf(
                "Buy just because of a 5-min hammer / bullish engulfing candle",
                "Trade OI walls (call/put OI) as support or resistance",
                "Expiry: buy to lose at max pain",
                "Buy after a gap-down hoping it fills",
                "Take Nifty trades after 13:30",
                "Trade Bank Nifty midday or end-of-day setups",
                "Buy cheap options on expiry morning (62-98% become zero)",
                "Guess the next candle from the last candle (50/50)"
            )
            nevers.forEach { n ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("✗", color = RedBear, fontSize = 13.sp, modifier = Modifier.width(18.dp))
                    Text(n, color = Color(0xFFFFCDD2), fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. HERO ZERO TAB
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun HeroZeroTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12. Hero Zero Deep Dive", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "OTM CE/PE that opened at Rs 1-20 at 9:15 on expiry day (1,000 qty traded). " +
                        "Bought at open + Rs 0.5 spread. Rs 50 charges per trade.",
                        color = Color.LightGray, fontSize = 12.sp
                    )
                }
            }
        }

        item {
            // Max price table
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Max Price to Pay at 13:15 (Section 4 rules)", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    HeroTableRow("Index", "Strike", "Avg Expiry Payoff", "MAX Price", isHeader = true)
                    HeroTableRow("Nifty",      "ATM",             "Rs 33.3",  "Rs 24")
                    HeroTableRow("Nifty",      "1 OTM (50 pts)",  "Rs 13.5",  "Rs 7")
                    HeroTableRow("Bank Nifty", "ATM",             "Rs 78.4",  "Rs 36")
                    HeroTableRow("Bank Nifty", "1 OTM (100 pts)", "Rs 41.5",  "Rs 11")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "If price is ABOVE max → NO TRADE. Max = fair value minus safety margin.",
                        color = Color(0xFFFFCDD2), fontSize = 11.sp
                    )
                }
            }
        }

        item {
            // Exit results
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12.1 Which Exit is Best? (Rs 10-20, 0.5-1.5% OTM)", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    InfoBullet("No exit makes money on any index.", RedBear)
                    InfoBullet("Least bad: Sensex sell at Rs 200 (−15%).", AmberWarn)
                    InfoBullet("Nifty hold to expiry: −38%.", RedBear)
                    InfoBullet("Bank Nifty sell at Rs 200: −69%. Avoid BN hero zero.", RedBear)
                    InfoBullet("Sensex is least bad; Nifty worse; Bank Nifty worst.", AmberWarn)
                }
            }
        }

        item {
            // 12.4 Rules if you still want to buy
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12.4 Rules if You Still Want to Buy Cheap Options", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    RuleRow("NEVER buy", "Rs 0.05-2 options. Almost never reach Rs 100. Lose ~100%.")
                    RuleRow("Best range", "Rs 5-10 options, 0.5-1.5% away from index.")
                    RuleRow("Prefer", "Sensex or Nifty. AVOID Bank Nifty (only 2 jackpot days in 42 expiries).")
                    RuleRow("Sell order", "PUT a SELL order (target Rs 100 or 10x) AS SOON AS you buy. Many spikes fall back to zero.")
                    RuleRow("Timing", "Buy only after market starts trending (see 13:15 hero zero rule).")
                    RuleRow("Max spend", "0.25% of capital per expiry. Losing runs of 30-40 expiries in a row happen.")
                    RuleRow("Exit", "Sell at 3× buy price OR 15:15 — whichever first. No stop loss.")
                }
            }
        }

        item {
            // Sensex special Section 10
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("10. Sensex Special — Expiry Day Hero Zero", color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Best Sensex result: expiry-day 13:15 direction + ATM + below VWAP filter, better path.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    RuleRow("SX-1 (main)", "13:15 Sensex expiry. VWAP side rule. ATM. Buy only if price ≤ Rs 84. 1-OTM if price ≤ Rs 43.")
                    RuleRow("SX-2 (early)", "17:15 same side rule. ATM below Rs 119. More time = higher fair value, but option costs more.")
                    RuleRow("SX-3 (breakout)", "13:15 — within top 15% of day's range (CE) or bottom 15% (PE). ATM below Rs 77.")
                    RuleRow("SX-4 (late)", "14:15 ATM only. Below Rs 100 for OTM. Only if 2 strikes OTM became mid-OTM 17.2% of time.")
                    RuleRow("SX-5 (straddle)", "13:15 — buy ATM CE AND PE together if no idea. Buy only if BOTH together cost ≤ Rs 160.")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Best time: 10:30-11:30 IST is only positive entry window for Sensex intraday (+0.08R). " +
                        "Entries 12:30-13:30 and 13:30-14:30 lose.",
                        color = AmberWarn, fontSize = 11.sp
                    )
                }
            }
        }

        item {
            // 11.5 Rules
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("11.5 Sensex Hero Zero Rules (Rs 1-20 max)", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    RuleRow("Max price", "Rs 1-20. Never more. Keeps loss per expiry small (Rs 200-400 per lot).")
                    RuleRow("Best options", "Rs 10-20, strike 0.5-1.5% away from Sensex. Best chance of jackpot (~0%).")
                    RuleRow("Sell order", "Put Rs 200 SELL order immediately after buying. 42.2% of Rs 200 options fell back to Rs 0 by expiry.")
                    RuleRow("At 10:15", "If Sensex moved against in 1st hour → what is left? Use 1-OTM to pick the side.")
                    RuleRow("Max spend", "0.25% capital per expiry. Losing runs of 30+ expiries in a row. It's a lottery ticket.")
                    RuleRow("Exit", "Sell at 3× buy price or at 15:15. No stop loss (whole premium is the risk).")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedBear.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, RedBear, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("How Much You Can Really Earn", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No setup has proven profits at 1 lot. The positive numbers come from small samples whose 95% ranges include losses.\n\n" +
                        "Hero zero earns money ONLY if you get the price at or below the max price.\n" +
                        "At the usual market price it lost about Rs 600-870 per expiry.\n\n" +
                        "With the Rs 1-20 cap, Sensex hero zero lost money in every version tested.",
                        color = Color(0xFFFFCDD2), fontSize = 12.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. CHECKLIST TAB
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun ChecklistTab() {
    val questions = listOf(
        "Is the time between 9:45 and 11:00? (Setup 1 window)",
        "Did a 15-min candle CLOSE outside the opening range?",
        "Is price on the correct side of VWAP?",
        "After 10:15: does the trade match the Setup 2 direction?",
        "Do I know my index stop-level, target and exit time (15:15)?",
        "Is my loss at the stop at most 0.5% of capital?"
    )
    val answers = remember { mutableStateListOf(*Array(questions.size) { false }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("8. Pre-Trade Checklist", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("All YES → take the trade. Any NO → no trade.", color = AmberWarn, fontSize = 12.sp)
                }
            }
        }

        items(questions.size) { i ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (answers[i]) GreenBull.copy(alpha = 0.15f) else CardDark
                ),
                modifier = Modifier.fillMaxWidth()
                    .then(if (answers[i]) Modifier.border(1.dp, GreenBull, RoundedCornerShape(10.dp)) else Modifier)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = answers[i],
                        onCheckedChange = { answers[i] = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GreenBull,
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(questions[i], color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            val allYes = answers.all { it }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (allYes) GreenBull.copy(alpha = 0.2f) else RedBear.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (allYes) GreenBull else RedBear, RoundedCornerShape(10.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (allYes) "✓  ALL YES — TAKE THE TRADE" else "✗  NOT READY — WAIT",
                        color = if (allYes) GreenBull else RedBear,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Stop Rules", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("• Daily stop: after losing 2× your per-trade risk, stop for the day.", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Weekly stop: after losing 5× your per-trade risk, stop for the week.", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Always exit by the index stop level — never move stop further away.", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { for (i in answers.indices) answers[i] = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text("Reset Checklist")
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. RISK CALC TAB
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun RiskCalcTab() {
    var capitalStr by remember { mutableStateOf("") }
    val capital = capitalStr.toDoubleOrNull() ?: 0.0

    val maxRisk05 = capital * 0.005
    val maxRisk025 = capital * 0.0025

    // Nifty: 65 qty, ~0.5 delta, need 55 index points to cover 1R
    // Approx: 1 Nifty lot risk in index points = 55 pts × 65 qty × 0.5 delta ≈ Rs 1800
    // Simplified: maxLots = floor(maxRisk / 1800)
    val niftyRiskPerLot = 1800.0
    val bnRiskPerLot = 1500.0 // approx for Bank Nifty

    val niftyLots05 = if (capital > 0) (maxRisk05 / niftyRiskPerLot).toInt().coerceAtLeast(0) else 0
    val bnLots05 = if (capital > 0) (maxRisk05 / bnRiskPerLot).toInt().coerceAtLeast(0) else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("7. Money Rules — Risk Calculator", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Max risk: 0.5% of capital per trade. Hero zero: 0.25% per expiry.", color = Color.LightGray, fontSize = 12.sp)
                    Text("Lots: Nifty = 65 qty | Bank Nifty = 30 qty", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Enter Your Capital (Rs)", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = capitalStr,
                        onValueChange = { capitalStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Capital in Rs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenBull,
                            focusedLabelColor = GreenBull,
                            cursorColor = GreenBull,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                        RiskRow("Hero Zero max per expiry (0.25%)", "Rs %,.0f".format(maxRisk025), BlueCool)
                        RiskRow("Nifty lots (~Rs 1800/lot risk)", "$niftyLots05 lot${if (niftyLots05 != 1) "s" else ""} (${niftyLots05 * 65} qty)", GreenBull)
                        RiskRow("Bank Nifty lots (~Rs 1500/lot risk)", "$bnLots05 lot${if (bnLots05 != 1) "s" else ""} (${bnLots05 * 30} qty)", GreenBull)

                        if (niftyLots05 == 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⚠ 0.5% capital < Rs 1,800 (1 Nifty lot risk). PAPER TRADE until capital grows.",
                                color = RedBear,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                // Example: if capital < 4L, warn
                if (capital < 400_000) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberWarn.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth().border(1.dp, AmberWarn, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Book Reference: Rs 4,00,000 capital", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "→ Max loss Rs 2,000 per trade → about 1 Nifty lot for Setup 1.\n" +
                                "If 1 lot risks more than 0.5% of your capital, paper trade until capital is taller.",
                                color = Color.LightGray, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Quick Reference", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Rs 2,00,000 capital → Rs 1,000 max risk → paper trade (< 1 Nifty lot)", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 4,00,000 capital → Rs 2,000 max risk → 1 Nifty lot", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 8,00,000 capital → Rs 4,000 max risk → 2 Nifty lots / 2 BN lots", color = Color.LightGray, fontSize = 12.sp)
                    Text("Rs 16,00,000 capital → Rs 8,000 max risk → 4 Nifty lots / 5 BN lots", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Bottom line from Section 9:", color = RedBear, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "No setup in this guide has proven profits yet. Paper trade Setup 1 for at least 3 months before real money. Use Setups 2 and 3 only with very small money.",
                        color = Color(0xFFFFCDD2), fontSize = 12.sp
                    )
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
    title: String,
    badge: String,
    badgeColor: Color,
    backtest: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = badgeColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "  $badge  ",
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
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
        Text(
            "$label:",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(80.dp)
        )
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
fun HeroTableRow(col1: String, col2: String, col3: String, col4: String, isHeader: Boolean = false) {
    val weight = if (isHeader) FontWeight.Bold else FontWeight.Normal
    val color = if (isHeader) AmberWarn else Color.White
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(col1, color = color, fontSize = 11.sp, fontWeight = weight, modifier = Modifier.weight(1.2f))
        Text(col2, color = color, fontSize = 11.sp, fontWeight = weight, modifier = Modifier.weight(1.5f))
        Text(col3, color = color, fontSize = 11.sp, fontWeight = weight, modifier = Modifier.weight(1.5f))
        Text(col4, color = if (isHeader) color else GreenBull, fontSize = 11.sp, fontWeight = weight, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RiskRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}
