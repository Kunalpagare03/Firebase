package com.Kunal.stock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel

// ─────────────────────────── COLOR PALETTE ───────────────────────────────────
private val SurfaceDark  = Color(0xFF1A1A2E)
private val CardDark     = Color(0xFF16213E)
private val CardDark2    = Color(0xFF0F3460)
private val GreenBull    = Color(0xFF00C853)
private val RedBear      = Color(0xFFD50000)
private val AmberWarn    = Color(0xFFFFAB00)
private val BlueCool     = Color(0xFF2196F3)

// ─────────────────────────── DATA MODELS ─────────────────────────────────────

private enum class SignalType { BUY_CE, BUY_PE, WAIT }

private data class PatternDef(
    val name: String,
    val signal: SignalType,
    val strength: String,
    val targetPct: String,
    val slRule: String,
    val timeValidity: String,
    val category: String    // "candle", "chart", "sd", "trend", "neutral"
)

private val ALL_PATTERNS: List<PatternDef> = listOf(
    // Bullish candles
    PatternDef("Hammer",               SignalType.BUY_CE, "Strong",   "1-2%",  "Below hammer low",         "1-3 candles",  "candle"),
    PatternDef("Inverted Hammer",      SignalType.BUY_CE, "Moderate", "1-1.5%","Below IH low",             "1-2 candles",  "candle"),
    PatternDef("Bullish Engulfing",    SignalType.BUY_CE, "Strong",   "2-3%",  "Below engulfing low",      "2-5 candles",  "candle"),
    PatternDef("Morning Star",         SignalType.BUY_CE, "Strong",   "2-4%",  "Below 3rd candle low",     "3-5 candles",  "candle"),
    PatternDef("Dragonfly Doji",       SignalType.BUY_CE, "Moderate", "1-2%",  "Below doji low",           "1-2 candles",  "candle"),
    PatternDef("Piercing Line",        SignalType.BUY_CE, "Moderate", "1.5-2%","Below 2nd candle low",     "2-3 candles",  "candle"),
    PatternDef("Tweezer Bottom",       SignalType.BUY_CE, "Moderate", "1-2%",  "Below twin lows",          "2-4 candles",  "candle"),
    PatternDef("Three White Soldiers", SignalType.BUY_CE, "Strong",   "3-5%",  "Below 3rd soldier low",    "5-10 candles", "candle"),
    PatternDef("Bullish Harami",       SignalType.BUY_CE, "Weak",     "1%",    "Below harami low",         "1-2 candles",  "candle"),
    PatternDef("Bullish Kicker",       SignalType.BUY_CE, "Strong",   "2-3%",  "Below gap low",            "2-5 candles",  "candle"),
    // Bearish candles
    PatternDef("Shooting Star",        SignalType.BUY_PE, "Strong",   "1-2%",  "Above star high",          "1-3 candles",  "candle"),
    PatternDef("Hanging Man",          SignalType.BUY_PE, "Moderate", "1-1.5%","Above HM high",            "1-2 candles",  "candle"),
    PatternDef("Bearish Engulfing",    SignalType.BUY_PE, "Strong",   "2-3%",  "Above engulfing high",     "2-5 candles",  "candle"),
    PatternDef("Evening Star",         SignalType.BUY_PE, "Strong",   "2-4%",  "Above 3rd candle high",    "3-5 candles",  "candle"),
    PatternDef("Gravestone Doji",      SignalType.BUY_PE, "Moderate", "1-2%",  "Above doji high",          "1-2 candles",  "candle"),
    PatternDef("Dark Cloud Cover",     SignalType.BUY_PE, "Moderate", "1.5-2%","Above 2nd candle high",    "2-3 candles",  "candle"),
    PatternDef("Tweezer Top",          SignalType.BUY_PE, "Moderate", "1-2%",  "Above twin highs",         "2-4 candles",  "candle"),
    PatternDef("Three Black Crows",    SignalType.BUY_PE, "Strong",   "3-5%",  "Above 3rd crow high",      "5-10 candles", "candle"),
    PatternDef("Bearish Harami",       SignalType.BUY_PE, "Weak",     "1%",    "Above harami high",        "1-2 candles",  "candle"),
    // Bullish chart patterns
    PatternDef("Inv Head & Shoulders", SignalType.BUY_CE, "Strong",   "5-8%",  "Below right shoulder",     "Multi-day",    "chart"),
    PatternDef("Double Bottom",        SignalType.BUY_CE, "Strong",   "5-10%", "Below neckline",           "Multi-day",    "chart"),
    PatternDef("Triple Bottom",        SignalType.BUY_CE, "Strong",   "7-12%", "Below support",            "Multi-day",    "chart"),
    PatternDef("Ascending Triangle",   SignalType.BUY_CE, "Strong",   "3-5%",  "Below ascending low",      "Multi-day",    "chart"),
    PatternDef("Bullish Flag",         SignalType.BUY_CE, "Strong",   "3-5%",  "Below flag low",           "3-5 candles",  "chart"),
    PatternDef("Falling Wedge",        SignalType.BUY_CE, "Strong",   "4-7%",  "Below wedge low",          "Multi-day",    "chart"),
    PatternDef("Bullish Wedge",        SignalType.BUY_CE, "Moderate", "3-5%",  "Below wedge support",      "Multi-day",    "chart"),
    PatternDef("Cup with Handle",      SignalType.BUY_CE, "Strong",   "5-8%",  "Below handle low",         "Multi-week",   "chart"),
    // Bearish chart patterns
    PatternDef("Head & Shoulders",     SignalType.BUY_PE, "Strong",   "5-8%",  "Above right shoulder",     "Multi-day",    "chart"),
    PatternDef("Double Top",           SignalType.BUY_PE, "Strong",   "5-10%", "Above neckline",           "Multi-day",    "chart"),
    PatternDef("Triple Top",           SignalType.BUY_PE, "Strong",   "7-12%", "Above resistance",         "Multi-day",    "chart"),
    PatternDef("Descending Triangle",  SignalType.BUY_PE, "Strong",   "3-5%",  "Above descending high",    "Multi-day",    "chart"),
    PatternDef("Bearish Flag",         SignalType.BUY_PE, "Strong",   "3-5%",  "Above flag high",          "3-5 candles",  "chart"),
    PatternDef("Rising Wedge",         SignalType.BUY_PE, "Strong",   "4-7%",  "Above wedge high",         "Multi-day",    "chart"),
    PatternDef("Bearish Wedge",        SignalType.BUY_PE, "Moderate", "3-5%",  "Above wedge resistance",   "Multi-day",    "chart"),
    // Supply/Demand
    PatternDef("DBR (Demand Zone)",    SignalType.BUY_CE, "Strong",   "2-4%",  "Below demand zone",        "Retest candle", "sd"),
    PatternDef("RBR (Demand Cont.)",   SignalType.BUY_CE, "Moderate", "1-3%",  "Below RBR base",           "Retest candle", "sd"),
    PatternDef("RBD (Supply Zone)",    SignalType.BUY_PE, "Strong",   "2-4%",  "Above supply zone",        "Retest candle", "sd"),
    PatternDef("DBD (Supply Cont.)",   SignalType.BUY_PE, "Moderate", "1-3%",  "Above DBD base",           "Retest candle", "sd"),
    // Trend / MA
    PatternDef("Golden Cross (50-200 MA)", SignalType.BUY_CE, "Strong",   "5-10%", "Below 50 MA",          "Multi-week",   "trend"),
    PatternDef("MA Bullish Crossover (5-8-13)", SignalType.BUY_CE, "Moderate", "2-4%", "Below 5 MA",       "2-5 candles",  "trend"),
    PatternDef("Demand Zone Retest",   SignalType.BUY_CE, "Strong",   "2-5%",  "Below demand zone",        "Retest candle", "trend"),
    PatternDef("Death Cross (50-200 MA)", SignalType.BUY_PE, "Strong",   "5-10%", "Above 50 MA",           "Multi-week",   "trend"),
    PatternDef("MA Bearish (5-8-13)",  SignalType.BUY_PE, "Moderate", "2-4%",  "Above 5 MA",              "2-5 candles",  "trend"),
    PatternDef("Supply Zone Retest",   SignalType.BUY_PE, "Strong",   "2-5%",  "Above supply zone",        "Retest candle", "trend"),
    // Neutral
    PatternDef("Spinning Top",         SignalType.WAIT,   "Weak",     "—",     "—",                        "—",            "neutral"),
    PatternDef("Doji",                 SignalType.WAIT,   "Weak",     "—",     "—",                        "—",            "neutral"),
    PatternDef("Symmetrical Triangle", SignalType.WAIT,   "Weak",     "—",     "Wait for breakout",        "—",            "neutral"),
    PatternDef("Pennant",              SignalType.WAIT,   "Weak",     "—",     "Wait for breakout",        "—",            "neutral"),
    PatternDef("Sideways Range",       SignalType.WAIT,   "Weak",     "—",     "Wait for range break",     "—",            "neutral"),
)

private val PATTERN_NAMES = ALL_PATTERNS.map { it.name }

// ─────────────────────────── MAIN SCREEN ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartSignalScreen(viewModel: OptionSentimentViewModel = viewModel()) {
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Live Evidence", "Pattern Reference", "Candles", "Supply/Demand", "Trend Tools", "Checklist")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDark)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = BlueCool, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Chart Signal Identifier", color = BlueCool, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("CE / PE", color = Color.Gray, fontSize = 12.sp)
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardDark,
            contentColor = GreenBull,
            edgePadding = 0.dp
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
                0 -> LiveOptionEvidenceTab(liveCheck)
                1 -> PatternSignalTab()
                2 -> CandlesTab()
                3 -> SupplyDemandTab()
                4 -> TrendToolsTab()
                5 -> ChartChecklistTab()
            }
        }
    }
}

@Composable
private fun LiveOptionEvidenceTab(result: LiveSetupCheckResult) {
    val ready = result.status == "CONDITIONAL CHECK PASSED"
    val statusColor = if (ready) GreenBull else AmberWarn
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${result.symbol.ifBlank { "Options" }} · ${result.status}",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(result.message, color = Color.White, fontSize = 13.sp)
                    Text(
                        "Spot: ${if (result.spotFresh) "FRESH" else "STALE / MISSING"}  ·  Chain: ${if (result.chainFresh) "FRESH" else "STALE / MISSING"}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    result.consensus?.let { consensus ->
                        Text(
                            "Evidence direction: ${consensus.direction} · ${consensus.evidence.joinToString(" · ")}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "Freshness and structure check only—not a calibrated probability or order recommendation.",
                        color = AmberWarn,
                        fontSize = 11.sp
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Manual chart tools", color = BlueCool, fontWeight = FontWeight.Bold)
                    Text(
                        "Pattern Reference, Candles, Supply/Demand and Trend Tools are visual checklists. They do not read chart images or validate a live setup.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────── TAB 0: PATTERN SIGNAL ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternSignalTab() {
    val trendOptions   = listOf("Uptrend", "Downtrend", "Sideways")
    var trend          by remember { mutableStateOf(trendOptions[0]) }
    var trendExpanded  by remember { mutableStateOf(false) }
    var pattern        by remember { mutableStateOf(PATTERN_NAMES[0]) }
    var patternExpanded by remember { mutableStateOf(false) }
    var confirmSR      by remember { mutableStateOf(false) }

    val selectedPatternDef = ALL_PATTERNS.firstOrNull { it.name == pattern }
    val signal: SignalType = when {
        selectedPatternDef == null -> SignalType.WAIT
        selectedPatternDef.signal == SignalType.WAIT -> SignalType.WAIT
        trend == "Sideways" -> SignalType.WAIT
        trend == "Uptrend"  && selectedPatternDef.signal == SignalType.BUY_CE -> SignalType.BUY_CE
        trend == "Uptrend"  && selectedPatternDef.signal == SignalType.BUY_PE -> SignalType.WAIT  // counter-trend
        trend == "Downtrend" && selectedPatternDef.signal == SignalType.BUY_PE -> SignalType.BUY_PE
        trend == "Downtrend" && selectedPatternDef.signal == SignalType.BUY_CE -> SignalType.WAIT // counter-trend
        else -> SignalType.WAIT
    }

    // Adjust strength based on SR confirmation
    val strengthLabel = when {
        selectedPatternDef == null -> "—"
        signal == SignalType.WAIT -> "No Signal"
        confirmSR && selectedPatternDef.strength == "Strong" -> "STRONG"
        confirmSR && selectedPatternDef.strength == "Moderate" -> "STRONG"
        !confirmSR && selectedPatternDef.strength == "Strong" -> "MODERATE"
        !confirmSR && selectedPatternDef.strength == "Moderate" -> "MODERATE"
        else -> "WEAK"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── INPUT SECTION ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📊 Signal Inputs", color = BlueCool, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)

                    // Trend dropdown
                    Text("Trend Direction", color = Color.Gray, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = trendExpanded,
                        onExpandedChange = { trendExpanded = !trendExpanded }
                    ) {
                        OutlinedTextField(
                            value = trend,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Trend", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = trendExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BlueCool,
                                unfocusedBorderColor = Color.Gray,
                                focusedContainerColor = CardDark2,
                                unfocusedContainerColor = CardDark2
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = trendExpanded,
                            onDismissRequest = { trendExpanded = false },
                            modifier = Modifier.background(CardDark2)
                        ) {
                            trendOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = Color.White) },
                                    onClick = { trend = opt; trendExpanded = false }
                                )
                            }
                        }
                    }

                    // Pattern dropdown
                    Text("Pattern Detected", color = Color.Gray, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = patternExpanded,
                        onExpandedChange = { patternExpanded = !patternExpanded }
                    ) {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Pattern", color = Color.Gray, fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patternExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BlueCool,
                                unfocusedBorderColor = Color.Gray,
                                focusedContainerColor = CardDark2,
                                unfocusedContainerColor = CardDark2
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = patternExpanded,
                            onDismissRequest = { patternExpanded = false },
                            modifier = Modifier
                                .background(CardDark2)
                                .heightIn(max = 300.dp)
                        ) {
                            PATTERN_NAMES.forEach { opt ->
                                val pDef = ALL_PATTERNS.firstOrNull { it.name == opt }
                                val dotColor = when (pDef?.signal) {
                                    SignalType.BUY_CE -> GreenBull
                                    SignalType.BUY_PE -> RedBear
                                    else -> Color.Gray
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(opt, color = Color.White, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = { pattern = opt; patternExpanded = false }
                                )
                            }
                        }
                    }

                    // Confirm S/R checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = confirmSR,
                            onCheckedChange = { confirmSR = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = GreenBull,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm Signal: Price at key S/R level?", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── SIGNAL OUTPUT ──
        item {
            SignalOutputCard(signal, strengthLabel, selectedPatternDef)
        }

        // ── PATTERN DETAIL ──
        if (selectedPatternDef != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pattern Details", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        InfoRow("Target Move", selectedPatternDef.targetPct)
                        InfoRow("SL Rule", selectedPatternDef.slRule)
                        InfoRow("Time Validity", selectedPatternDef.timeValidity)
                        InfoRow("Category", selectedPatternDef.category.uppercase())
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalOutputCard(signal: SignalType, strength: String, patternDef: PatternDef?) {
    val (bgColor, emoji, label, textColor) = when (signal) {
        SignalType.BUY_CE -> listOf(GreenBull.copy(alpha = 0.15f), "🟢", "BUY CE", GreenBull)
        SignalType.BUY_PE -> listOf(RedBear.copy(alpha = 0.15f), "🔴", "BUY PE", RedBear)
        SignalType.WAIT   -> listOf(Color.Gray.copy(alpha = 0.15f), "⚪", "WAIT / NO SIGNAL", Color.Gray)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor as Color),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji as String, fontSize = 48.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                label as String,
                color = textColor as Color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Signal Strength: $strength",
                color = when (strength) {
                    "STRONG"   -> GreenBull
                    "MODERATE" -> AmberWarn
                    else       -> Color.Gray
                },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (patternDef != null && signal != SignalType.WAIT) {
                Spacer(Modifier.height(8.dp))
                Divider(color = textColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniStat("Target", patternDef.targetPct, GreenBull)
                    MiniStat("SL", "Below/Above key", RedBear)
                    MiniStat("Valid", patternDef.timeValidity, BlueCool)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────── TAB 1: CANDLES ──────────────────────────────────

@Composable
fun CandlesTab() {
    val bullish = ALL_PATTERNS.filter { it.category == "candle" && it.signal == SignalType.BUY_CE }
    val bearish = ALL_PATTERNS.filter { it.category == "candle" && it.signal == SignalType.BUY_PE }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🕯 Candlestick Patterns", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Bullish column
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🟢 BULLISH → BUY CE",
                    color = GreenBull,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                bullish.forEach { p ->
                    CandlePatternCard(p, GreenBull)
                }
            }
            // Bearish column
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🔴 BEARISH → BUY PE",
                    color = RedBear,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                bearish.forEach { p ->
                    CandlePatternCard(p, RedBear)
                }
            }
        }

        // Neutral section
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("⚪ NEUTRAL — WAIT", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Spinning Top", "Doji").forEach { name ->
                        Text(
                            "• $name",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandlePatternCard(p: PatternDef, accentColor: Color) {
    val signal = if (p.signal == SignalType.BUY_CE) "BUY CE" else "BUY PE"
    Card(
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(signal, color = accentColor, fontSize = 10.sp)
            Text("SL: ${p.slRule}", color = Color.Gray, fontSize = 10.sp)
            Text("Target: ${p.targetPct}", color = Color.White, fontSize = 10.sp)
        }
    }
}

// ─────────────────────────── TAB 2: SUPPLY/DEMAND ────────────────────────────

private data class ZoneDef(
    val name: String,
    val zoneType: String,
    val signal: String,
    val action: String,
    val entryRule: String,
    val color: Color
)

private val ZONE_DEFS = listOf(
    ZoneDef(
        name = "RBD — Rally Base Drop",
        zoneType = "SUPPLY ZONE",
        signal = "BUY PE",
        action = "🔴 BUY PE when price retests the base",
        entryRule = "Wait for price to rally back into the base. Enter PE on rejection candle.",
        color = RedBear
    ),
    ZoneDef(
        name = "DBR — Drop Base Rally",
        zoneType = "DEMAND ZONE",
        signal = "BUY CE",
        action = "🟢 BUY CE when price drops back to the base",
        entryRule = "Wait for price to drop back into the base. Enter CE on reversal candle.",
        color = GreenBull
    ),
    ZoneDef(
        name = "DBD — Drop Base Drop",
        zoneType = "SUPPLY ZONE (Continuation)",
        signal = "BUY PE",
        action = "🔴 BUY PE — continuation sell",
        entryRule = "Price retests the base from below. Expect further drop. Enter PE.",
        color = RedBear
    ),
    ZoneDef(
        name = "RBR — Rally Base Rally",
        zoneType = "DEMAND ZONE (Continuation)",
        signal = "BUY CE",
        action = "🟢 BUY CE — continuation buy",
        entryRule = "Price retests the base from above. Expect further rally. Enter CE.",
        color = GreenBull
    ),
)

@Composable
fun SupplyDemandTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🏛 Smart Money — Supply & Demand Zones", color = BlueCool, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(
            "These zones mark where institutional orders were placed. When price returns to the zone, expect a reaction.",
            color = Color.LightGray,
            fontSize = 12.sp
        )
        ZONE_DEFS.forEach { z ->
            ZoneCard(z)
        }

        // Legend card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark2),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📌 Key Rule", color = AmberWarn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Zone must be fresh (not retested multiple times). The more explosive the departure, the stronger the zone.", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Confluence: Use zone + S/R + MA for STRONG signal", color = GreenBull, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ZoneCard(z: ZoneDef) {
    Card(
        colors = CardDefaults.cardColors(containerColor = z.color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(z.name, color = z.color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Text(z.zoneType, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Divider(color = z.color.copy(alpha = 0.3f), thickness = 0.5.dp)
            Text(z.action, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(z.entryRule, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

// ─────────────────────────── TAB 3: TREND TOOLS ──────────────────────────────

private data class MaSignal(val label: String, val action: String, val color: Color, val bias: String)

private val MA_SIGNALS = listOf(
    MaSignal("Golden Cross (50 > 200 MA)", "BUY CE ✅", GreenBull, "Uptrend — strong bullish bias"),
    MaSignal("Death Cross (50 < 200 MA)",  "BUY PE ✅", RedBear,   "Downtrend — strong bearish bias"),
    MaSignal("5-8-13 Bullish Stack",       "BUY CE ✅", GreenBull, "Short-term bullish momentum"),
    MaSignal("5-8-13 Bearish Stack",       "BUY PE ✅", RedBear,   "Short-term bearish momentum"),
    MaSignal("Price > 200 MA",             "CE Bias",   GreenBull.copy(alpha = 0.8f), "Overall uptrend — prefer CE"),
    MaSignal("Price < 200 MA",             "PE Bias",   RedBear.copy(alpha = 0.8f),   "Overall downtrend — prefer PE"),
)

@Composable
fun TrendToolsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📈 Trend & MA Signals", color = BlueCool, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)

        MA_SIGNALS.forEach { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = s.color.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(s.bias, color = Color.LightGray, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(s.action, color = s.color, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }

        Divider(color = Color.Gray.copy(alpha = 0.3f))

        // Confluence section
        Text("🔗 Confluence Trading", color = AmberWarn, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(
            "Confluence = multiple signals aligning at the same level. The more factors that agree, the stronger the trade.",
            color = Color.LightGray,
            fontSize = 12.sp
        )

        ConfluenceCard(
            count = 3,
            items = listOf("Trendline Support", "Key S/R Level", "MA Alignment"),
            strength = "STRONG SIGNAL — ENTER",
            color = GreenBull
        )
        ConfluenceCard(
            count = 2,
            items = listOf("Trendline Support", "Key S/R Level"),
            strength = "MODERATE — MANAGE RISK",
            color = AmberWarn
        )
        ConfluenceCard(
            count = 1,
            items = listOf("Only 1 factor aligning"),
            strength = "WEAK — DO NOT TRADE",
            color = RedBear
        )

        // MA stacking diagram
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark2),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("5-8-13 MA Stack Visual", color = BlueCool, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("BULLISH:  5 MA > 8 MA > 13 MA (Price above all) → BUY CE", color = GreenBull, fontSize = 12.sp)
                Text("BEARISH:  5 MA < 8 MA < 13 MA (Price below all) → BUY PE", color = RedBear, fontSize = 12.sp)
                Text("MIXED:    MAs tangled → WAIT for clarity", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConfluenceCard(count: Int, items: List<String>, strength: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$count / 3 Factors", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            items.forEach { item ->
                Text("• $item", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(strength, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────── TAB 4: CHECKLIST ────────────────────────────────

private data class CheckItem(val question: String)

private val CHECKLIST_ITEMS = listOf(
    CheckItem("Is pattern at a key S/R / Supply / Demand zone?"),
    CheckItem("Does higher timeframe agree? (15min if trading on 5min)"),
    CheckItem("RSI overbought (>70) for PE, oversold (<30) for CE?"),
    CheckItem("Is volume higher on the breakout candle?"),
    CheckItem("Is stop loss within 0.5% of option premium?"),
)

@Composable
fun ChartChecklistTab() {
    val checks = remember { mutableStateListOf(false, false, false, false, false) }
    val allYes = checks.all { it }
    val anyNo  = checks.any { !it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("✅ Pre-Trade Checklist", color = BlueCool, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(
            "Answer all 5 questions before entering a pattern-based trade. Partial confirmation = higher risk.",
            color = Color.LightGray,
            fontSize = 12.sp
        )

        CHECKLIST_ITEMS.forEachIndexed { i, item ->
            ChecklistItemCard(
                index = i + 1,
                question = item.question,
                checked = checks[i],
                onCheckedChange = { checks[i] = it }
            )
        }

        // Progress bar
        val progressCount = checks.count { it }
        val progress = progressCount / CHECKLIST_ITEMS.size.toFloat()
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Completion", color = Color.Gray, fontSize = 12.sp)
                    Text("$progressCount / ${CHECKLIST_ITEMS.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = when {
                        progress >= 1f -> GreenBull
                        progress >= 0.6f -> AmberWarn
                        else -> RedBear
                    },
                    trackColor = CardDark2
                )
            }
        }

        // Final verdict
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    allYes -> GreenBull.copy(alpha = 0.15f)
                    anyNo  -> AmberWarn.copy(alpha = 0.1f)
                    else   -> CardDark
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    allYes -> {
                        Text("🟢", fontSize = 36.sp)
                        Text("STRONG SIGNAL — ENTER", color = GreenBull, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Text("All 5 conditions met. Risk is managed. Proceed with confidence.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    anyNo -> {
                        Text("🟡", fontSize = 36.sp)
                        Text("WEAK — WAIT FOR MORE CONFIRMATION", color = AmberWarn, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text("One or more conditions not met. High risk — wait or skip the trade.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    else -> {
                        Text("⚪", fontSize = 36.sp)
                        Text("Complete the checklist above", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }

        // Reset button
        Button(
            onClick = { checks.indices.forEach { checks[it] = false } },
            colors = ButtonDefaults.buttonColors(containerColor = CardDark2),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset Checklist", color = Color.Gray)
        }
    }
}

@Composable
private fun ChecklistItemCard(
    index: Int,
    question: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (checked) GreenBull.copy(alpha = 0.1f) else CardDark
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$index.",
                color = if (checked) GreenBull else Color.Gray,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.width(28.dp)
            )
            Text(
                question,
                color = if (checked) Color.White else Color.LightGray,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = GreenBull,
                    uncheckedColor = Color.Gray
                )
            )
        }
    }
}
