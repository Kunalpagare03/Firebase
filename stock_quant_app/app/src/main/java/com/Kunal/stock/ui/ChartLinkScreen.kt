package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Kunal.stock.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ─────────────────────────────────────────────────────────────────────────────
// COLOUR PALETTE (dark terminal style)
// ─────────────────────────────────────────────────────────────────────────────
private val GreenNeon   = Color(0xFF00E676)
private val RedNeon     = Color(0xFFFF5252)
private val YellowNeon  = Color(0xFFFFD740)
private val BluePill    = Color(0xFF40C4FF)
private val PurplePill  = Color(0xFFEA80FC)
private val SurfaceDark = Color(0xFF121212)
private val CardDark    = Color(0xFF1E1E1E)
private val CardMid     = Color(0xFF252525)

// ─────────────────────────────────────────────────────────────────────────────
// MAIN CHARTLINK SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartLinkScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("📡 Scanner", "🗂 Archive", "📊 Ledger Stats")

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        TabRow(selectedTabIndex = selectedTab,
            containerColor = CardDark, contentColor = GreenNeon) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(title, fontSize = 12.sp,
                        fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }
        when (selectedTab) {
            0 -> PlaybookScannerTab()
            1 -> PlaybookArchiveTab()
            2 -> PlaybookLedgerStatsTab()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1 — SCANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaybookScannerTab() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isScanning     by remember { mutableStateOf(false) }
    var progress       by remember { mutableStateOf(0) }
    var total          by remember { mutableStateOf(0) }
    var currentSymbol  by remember { mutableStateOf("") }
    var signals        by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }
    var niftyAbove200  by remember { mutableStateOf(true) }
    var filterSetup    by remember { mutableStateOf<PlaybookSetup?>(null) }
    var filterAction   by remember { mutableStateOf("ALL") }  // ALL / BUY / SKIP
    var expandedId     by remember { mutableStateOf<String?>(null) }
    var statusMsg      by remember { mutableStateOf("") }

    // Check Nifty vs 200-DMA on launch
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1d&range=1y"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000; readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val closes = org.json.JSONObject(body)
                        .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                        .getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                        .getJSONArray("close")
                    val len = closes.length()
                    if (len >= 200) {
                        var sum = 0.0
                        for (i in (len - 200) until len) sum += closes.optDouble(i, 0.0)
                        val sma200 = sum / 200
                        val latest = closes.optDouble(len - 1, 0.0)
                        withContext(Dispatchers.Main) { niftyAbove200 = latest > sma200 }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    val filtered = remember(signals, filterSetup, filterAction) {
        signals.filter { s ->
            (filterSetup == null || s.setup == filterSetup) &&
            (filterAction == "ALL" || s.aiAction == filterAction)
        }.sortedWith(compareByDescending<PlaybookSignal> { it.aiAction == "BUY" }
                        .thenByDescending { it.strengthRank })
    }

    Column(Modifier.fillMaxSize()) {

        // ── Market Status Banner ─────────────────────────────────────────────
        Surface(color = if (niftyAbove200) Color(0xFF1B5E20) else Color(0xFF7F1D1D),
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (niftyAbove200) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null, tint = if (niftyAbove200) GreenNeon else RedNeon,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (niftyAbove200) "✅ Nifty above 200-DMA — Full size. All 4 setups active."
                    else               "⚠️ Nifty below 200-DMA — Trade half size or skip Setup D.",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Risk Rule reminder ───────────────────────────────────────────────
        Surface(color = CardMid, modifier = Modifier.fillMaxWidth()) {
            Text(
                "📐 Risk Rules: 8% SL · Hold ≤ 22 days · Risk 1% capital/trade · Max 10 positions · Skip if opens >3% above close",
                color = YellowNeon.copy(alpha = 0.85f), fontSize = 10.sp,
                modifier = Modifier.padding(10.dp, 4.dp))
        }

        // ── Setup filter chips ────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            SetupChip("ALL", filterSetup == null, GreenNeon) { filterSetup = null }
            PlaybookSetup.values().forEach { setup ->
                SetupChip(
                    label = "Setup ${setup.code}${if (!setup.proven) " ⚠" else " ✅"}",
                    selected = filterSetup == setup,
                    color = if (setup.proven) GreenNeon else YellowNeon
                ) { filterSetup = if (filterSetup == setup) null else setup }
            }
            Spacer(Modifier.width(8.dp))
            SetupChip("BUY", filterAction == "BUY", BluePill)  { filterAction = if (filterAction == "BUY")  "ALL" else "BUY" }
            SetupChip("SKIP", filterAction == "SKIP", RedNeon) { filterAction = if (filterAction == "SKIP") "ALL" else "SKIP" }
        }

        // ── Scan button + progress ────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (isScanning) {
                Column {
                    LinearProgressIndicator(
                        progress = if (total > 0) progress.toFloat() / total else 0f,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = GreenNeon)
                    Spacer(Modifier.height(4.dp))
                    Text("Scanning $currentSymbol… ($progress / $total)",
                        color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isScanning = true; progress = 0; signals = emptyList()
                            try {
                                val result = PlaybookScanner.scan(context, niftyAbove200) { p, t, sym ->
                                    progress = p; total = t; currentSymbol = sym
                                }
                                signals = result
                                PlaybookScanner.saveSignals(context, result.filter { it.aiAction == "BUY" })
                                statusMsg = "✅ Scan complete — ${result.count { it.aiAction == "BUY" }} BUY signals from ${result.size} hits"
                            } catch (e: Exception) {
                                statusMsg = "❌ Scan error: ${e.message}"
                            }
                            isScanning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenNeon, contentColor = Color.Black)) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Playbook Scanner (All 4 Setups)", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, color = if (statusMsg.startsWith("✅")) GreenNeon else RedNeon,
                fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }

        // ── Results list ─────────────────────────────────────────────────────
        if (filtered.isEmpty() && !isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Tap 'Run Playbook Scanner' to scan Nifty 500", color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text("Runs all 4 proven setups (A/B/C/D)\nFetches live Yahoo Finance data",
                        color = Color.DarkGray, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { signal ->
                    PlaybookSignalCard(signal, expandedId == signal.id,
                        onClick = { expandedId = if (expandedId == signal.id) null else signal.id })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIGNAL CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaybookSignalCard(signal: PlaybookSignal, expanded: Boolean, onClick: () -> Unit) {
    val actionColor = if (signal.aiAction == "BUY") GreenNeon else RedNeon
    val setupColor  = if (signal.setup.proven) GreenNeon else YellowNeon

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (signal.aiAction == "BUY") GreenNeon.copy(0.3f) else Color.Gray.copy(0.2f))) {
        Column(Modifier.padding(14.dp)) {
            // Header row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(signal.symbol, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
                    Text(signal.sector, color = Color.Gray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = setupColor.copy(0.18f), shape = RoundedCornerShape(8.dp)) {
                        Text("Setup ${signal.setup.code}${if (!signal.setup.proven) " ⚠" else ""}",
                            color = setupColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(color = actionColor.copy(0.18f), shape = RoundedCornerShape(8.dp)) {
                        Text(signal.aiAction, color = actionColor, fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = Color.DarkGray, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            // Key levels row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceBlock("Entry",    "₹${String.format("%.2f", signal.entryPrice)}", Color.White)
                PriceBlock("SL (8%)",  "₹${String.format("%.2f", signal.stopLoss)}",   RedNeon)
                PriceBlock("T1 (+8%)", "₹${String.format("%.2f", signal.refTarget1)}", GreenNeon)
                PriceBlock("T2 (+15%)","₹${String.format("%.2f", signal.refTarget2)}", BluePill)
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Signal: ${signal.signalDate}", color = Color.Gray, fontSize = 10.sp)
                Text("Day 22 exit: ${signal.day22ExitDate}", color = YellowNeon, fontSize = 10.sp)
            }

            // AI reason
            Spacer(Modifier.height(6.dp))
            Text(signal.aiReason, color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    Divider(color = Color.DarkGray.copy(0.5f), thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))

                    // Setup description
                    Surface(color = setupColor.copy(0.08f), shape = RoundedCornerShape(8.dp)) {
                        Text(signal.aiNote, color = setupColor.copy(0.9f), fontSize = 10.sp,
                            modifier = Modifier.padding(10.dp))
                    }

                    Spacer(Modifier.height(10.dp))

                    // Technical indicators
                    Text("📊 Technical Snapshot", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TechBadge("RSI-2",  String.format("%.1f", signal.rsi2),   if (signal.rsi2 < 5) RedNeon else Color.Gray)
                        TechBadge("RSI-14", String.format("%.1f", signal.rsi14),  if (signal.rsi14 < 30) RedNeon else Color.Gray)
                        TechBadge("Strength", "${signal.strengthRank}%ile",       if (signal.strengthRank >= 70) GreenNeon else Color.Gray)
                        TechBadge("Value/day", "₹${String.format("%.0f", signal.dailyValueCr)}Cr", BluePill)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Risk calc
                    val positionPct = 12.5  // 1% risk / 8% SL
                    Surface(color = CardMid, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("💰 Position Sizing (₹10 Lakh portfolio)", color = YellowNeon, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Risk 1% = ₹10,000  |  Position size: ${positionPct.toInt()}% = ₹1.25L",
                                color = Color.LightGray, fontSize = 11.sp)
                            Text("Shares: ${String.format("%.0f", 125000.0 / signal.entryPrice)}  |  " +
                                 "SL loss: ₹10,000  |  T1 gain: ₹${String.format("%.0f", 125000.0 * 0.08)}",
                                color = Color.LightGray, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("🚀 ${signal.setup.label} · Win ${signal.setup.winRate}% · Avg +${signal.setup.avgPct}% per trade (backtested 2012-2026)",
                        color = Color.DarkGray, fontSize = 10.sp)
                }
            }

            // Expand toggle
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.DarkGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2 — ARCHIVE
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybookArchiveTab() {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    var signals  by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }
    var filter   by remember { mutableStateOf(TradeStatus.OPEN) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        signals = withContext(Dispatchers.IO) { PlaybookScanner.loadAllSignals(context) }
    }

    val filtered = remember(signals, filter) { signals.filter { it.status == filter } }

    Column(Modifier.fillMaxSize()) {
        // Status filter bar
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TradeStatus.values().forEach { s ->
                val count = signals.count { it.status == s }
                val col = when (s) {
                    TradeStatus.OPEN         -> BluePill
                    TradeStatus.SL_HIT       -> RedNeon
                    TradeStatus.TARGET_HIT   -> GreenNeon
                    TradeStatus.DAY22_EXIT   -> YellowNeon
                    TradeStatus.SKIPPED      -> Color.Gray
                }
                FilterChip(selected = filter == s, onClick = { filter = s },
                    label = { Text("${s.name.replace("_", " ")} ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = col.copy(0.2f), containerColor = CardDark,
                        selectedLabelColor = col, labelColor = Color.Gray),
                    shape = RoundedCornerShape(8.dp))
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ${filter.name.lowercase().replace("_", " ")} trades yet.\nRun scanner to populate archive.",
                    color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { signal ->
                    ArchiveTradeCard(signal, expandedId == signal.id,
                        onClick = { expandedId = if (expandedId == signal.id) null else signal.id },
                        onUpdateStatus = { newStatus, exitPrice, reason ->
                            val today = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                            scope.launch(Dispatchers.IO) {
                                PlaybookScanner.updateSignalStatus(context, signal.id, newStatus, exitPrice, today, reason)
                                signals = PlaybookScanner.loadAllSignals(context)
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun ArchiveTradeCard(
    signal: PlaybookSignal, expanded: Boolean, onClick: () -> Unit,
    onUpdateStatus: (TradeStatus, Double, String) -> Unit
) {
    val isOpen = signal.status == TradeStatus.OPEN
    val returnColor = when {
        signal.returnPct > 0  -> GreenNeon
        signal.returnPct < 0  -> RedNeon
        signal.status == TradeStatus.OPEN -> YellowNeon
        else -> Color.Gray
    }
    val statusColor = when (signal.status) {
        TradeStatus.OPEN       -> BluePill
        TradeStatus.SL_HIT     -> RedNeon
        TradeStatus.TARGET_HIT -> GreenNeon
        TradeStatus.DAY22_EXIT -> YellowNeon
        TradeStatus.SKIPPED    -> Color.Gray
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(signal.symbol, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 15.sp)
                    Text("Setup ${signal.setup.code} · ${signal.signalDate}", color = Color.Gray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = statusColor.copy(0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(signal.status.name.replace("_", " "), color = statusColor, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp, 2.dp))
                    }
                    if (!isOpen) {
                        Text("${if (signal.returnPct >= 0) "+" else ""}${String.format("%.1f", signal.returnPct)}%",
                            color = returnColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceBlock("Entry", "₹${String.format("%.2f", signal.entryPrice)}", Color.White)
                PriceBlock("SL",    "₹${String.format("%.2f", signal.stopLoss)}",   RedNeon)
                PriceBlock("T1",    "₹${String.format("%.2f", signal.refTarget1)}", GreenNeon)
                PriceBlock("Day22", signal.day22ExitDate, YellowNeon)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    Divider(color = Color.DarkGray.copy(0.5f), thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("AI: ${signal.aiReason}", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))

                    if (isOpen) {
                        Text("Mark Result:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutcomeButton("🎯 T1 Hit",  GreenNeon)   { onUpdateStatus(TradeStatus.TARGET_HIT, signal.refTarget1, "T1_HIT") }
                            OutcomeButton("🎯 T2 Hit",  BluePill)    { onUpdateStatus(TradeStatus.TARGET_HIT, signal.refTarget2, "T2_HIT") }
                            OutcomeButton("❌ SL Hit",  RedNeon)     { onUpdateStatus(TradeStatus.SL_HIT,     signal.stopLoss,    "SL_HIT") }
                            OutcomeButton("📅 Day22",   YellowNeon)  { onUpdateStatus(TradeStatus.DAY22_EXIT, signal.entryPrice,  "DAY22") }
                        }
                    } else {
                        Text("Exit: ${signal.exitDate} @ ₹${String.format("%.2f", signal.exitPrice)} (${signal.exitReason})",
                            color = returnColor, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 3 — LEDGER STATS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaybookLedgerStatsTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<Map<PlaybookSetup, PlaybookLedgerStats>>(emptyMap()) }
    var allSignals by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }

    LaunchedEffect(Unit) {
        val signals = withContext(Dispatchers.IO) { PlaybookScanner.loadAllSignals(context) }
        allSignals  = signals
        stats       = PlaybookScanner.computeStats(signals)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Summary bar
        val closed = allSignals.filter { it.status in listOf(TradeStatus.SL_HIT, TradeStatus.TARGET_HIT, TradeStatus.DAY22_EXIT) }
        val wins   = closed.count { it.returnPct > 0 }
        val avgRet = if (closed.isNotEmpty()) closed.map { it.returnPct }.average() else 0.0

        Surface(color = CardMid, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Overall Performance", color = GreenNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatBlock("Total Signals",  "${allSignals.size}",                          Color.White)
                    StatBlock("Closed",         "${closed.size}",                               YellowNeon)
                    StatBlock("Win Rate",       if (closed.isEmpty()) "—" else "${String.format("%.1f", wins * 100.0 / closed.size)}%", GreenNeon)
                    StatBlock("Avg Return",     if (closed.isEmpty()) "—" else "${String.format("%.2f", avgRet)}%",
                               if (avgRet >= 0) GreenNeon else RedNeon)
                }
            }
        }

        // Backtested reference vs actual
        Text("📚 Backtested Reference (2012–2026, 474 Nifty 500 stocks)", color = Color.Gray,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        PlaybookSetup.values().forEach { setup ->
            val s = stats[setup]
            SetupStatCard(setup, s)
        }

        // Warning
        Spacer(Modifier.height(4.dp))
        Surface(color = Color(0xFF1A1A00), shape = RoundedCornerShape(10.dp)) {
            Text(
                "⚠️ Note: Backtested win rates from 2012–2026 (11,000+ trades each). Your live results " +
                "may differ. Bad years = falling markets (2013, 2015, 2018, 2025 small/mid caps). " +
                "Always use 8% SL and trade half size when Nifty < 200-DMA. Paper trade first.",
                color = YellowNeon.copy(0.8f), fontSize = 10.sp, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SetupStatCard(setup: PlaybookSetup, stats: PlaybookLedgerStats?) {
    val col = if (setup.proven) GreenNeon else YellowNeon
    Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, col.copy(0.2f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = col.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("Setup ${setup.code}", color = col, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp, 3.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(setup.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (!setup.proven) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = YellowNeon.copy(0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text("PROMISING", color = YellowNeon, fontSize = 9.sp, modifier = Modifier.padding(4.dp, 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Backtested numbers
            Text("Backtested (14yr)", color = Color.DarkGray, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                StatBlock("Win Rate",   "${setup.winRate}%",   GreenNeon)
                StatBlock("Avg/Trade",  "+${setup.avgPct}%",   BluePill)
                StatBlock("Hold",       "≤22 days",            YellowNeon)
                StatBlock("Stop",       "8% SL",               RedNeon)
            }

            if (stats != null && stats.totalSignals > 0) {
                Divider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray.copy(0.5f), thickness = 0.5.dp)
                Text("Your Live Performance", color = Color.DarkGray, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatBlock("Signals", "${stats.totalSignals}", Color.White)
                    StatBlock("Accepted","${stats.accepted}",     BluePill)
                    StatBlock("Win Rate", if (stats.wins + stats.losses > 0)
                        "${String.format("%.0f", stats.winRate)}%" else "—", GreenNeon)
                    StatBlock("Avg Ret", if (stats.wins + stats.losses > 0)
                        "${String.format("%.1f", stats.avgReturnPct)}%" else "—",
                        if (stats.avgReturnPct >= 0) GreenNeon else RedNeon)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SetupChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        color = if (selected) color.copy(0.22f) else CardMid,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }.border(1.dp, if (selected) color else Color.DarkGray, RoundedCornerShape(20.dp))) {
        Text(label, color = if (selected) color else Color.Gray,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun PriceBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.DarkGray, fontSize = 9.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun TechBadge(label: String, value: String, color: Color) {
    Surface(color = color.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.padding(6.dp, 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.Gray, fontSize = 9.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(label, color = Color.DarkGray, fontSize = 9.sp)
    }
}

@Composable
private fun OutcomeButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, border = androidx.compose.foundation.BorderStroke(1.dp, color),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)) {
        Text(label, color = color, fontSize = 10.sp)
    }
}
