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
internal fun PlaybookScannerTab() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isScanning     by remember { mutableStateOf(false) }
    var progress       by remember { mutableStateOf(0) }
    var total          by remember { mutableStateOf(0) }
    var currentSymbol  by remember { mutableStateOf("") }
    var signals        by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }
    var earlyCandidates by remember { mutableStateOf<List<EarlySetupCandidate>>(emptyList()) }
    var niftyContext by remember { mutableStateOf<MarketTrendContext?>(null) }
    var niftyContextError by remember { mutableStateOf<String?>(null) }
    var filterSetup    by remember { mutableStateOf<PlaybookSetup?>(null) }
    var filterAction   by remember { mutableStateOf("ALL") }  // ALL / BUY / SKIP
    var expandedId     by remember { mutableStateOf<String?>(null) }
    var statusMsg      by remember { mutableStateOf("") }
    var resultsViewMode by remember { mutableIntStateOf(0) } // 0: AI Deep-Dive Picks, 1: All Signals, 2: Early Radar

    // Read the benchmark regime once so a short-term turn can be seen before
    // Nifty has reclaimed its long-term 200-day average.
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val context = withContext(Dispatchers.IO) {
                    val url = "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1d&range=1y"
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    try {
                        if (conn.responseCode != 200) {
                            throw IllegalStateException("Nifty benchmark request returned HTTP ${conn.responseCode}.")
                        }
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val closes = org.json.JSONObject(body)
                            .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                            .getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                            .getJSONArray("close")
                        val values = (0 until closes.length()).mapNotNull { index ->
                            closes.optDouble(index).takeIf { it.isFinite() && it > 0.0 }
                        }
                        EarlySetupRadar.marketTrend(values)
                            ?: throw IllegalStateException("Nifty history did not contain 200 valid daily closes.")
                    } finally {
                        conn.disconnect()
                    }
                }
                niftyContext = context
                niftyContextError = null
            } catch (e: Exception) {
                niftyContext = null
                niftyContextError = e.localizedMessage ?: "Nifty benchmark data is unavailable."
            }
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

        val marketContext = niftyContext
        val marketColor = when {
            marketContext == null -> Color(0xFF5D4037)
            marketContext.above200 -> Color(0xFF1B5E20)
            marketContext.regime.contains("REVERSAL") || marketContext.regime.contains("IMPROVING") ->
                Color(0xFF795548)
            else -> Color(0xFF7F1D1D)
        }
        Surface(color = marketColor,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (marketContext?.change5dPct?.let { it >= 0.0 } == true)
                            Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (marketContext?.change5dPct?.let { it >= 0.0 } == true) GreenNeon else RedNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        marketContext?.regime ?: "Nifty regime unavailable · radar uses stock-only evidence",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (marketContext != null) {
                    Text(
                        "Nifty ${String.format("%.1f", marketContext.change5dPct)}% / 5 sessions · " +
                            "close ${String.format("%.0f", marketContext.latest)} · " +
                            "20-DMA ${if (marketContext.latest >= marketContext.sma20) "reclaimed" else "not reclaimed"} · " +
                            "200-DMA ${if (marketContext.above200) "above" else "below"}",
                        color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp
                    )
                } else {
                    Text(
                        niftyContextError ?: "Loading benchmark context…",
                        color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp
                    )
                }
            }
        }

        // ── Risk Rule reminder ───────────────────────────────────────────────
        Surface(color = CardMid, modifier = Modifier.fillMaxWidth()) {
            Text(
                "📐 Risk Rules: 8% SL · Hold ≤ 22 days · Risk 1% capital/trade · Max 10 positions · Skip if opens >3% above close",
                color = YellowNeon.copy(alpha = 0.85f), fontSize = 10.sp,
                modifier = Modifier.padding(10.dp, 4.dp))
        }



        // ── Universe Selector & Cache Bar ────────────────────────────────────
        var scanUniverseSize by remember { mutableIntStateOf(50) }
        var cachedCount by remember { mutableIntStateOf(PlaybookScanner.getCachedCount()) }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    50 to "Nifty 50 (⚡ Fast)",
                    100 to "Nifty 100",
                    0 to "All 500"
                ).forEach { (size, label) ->
                    val isSelected = scanUniverseSize == size
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { if (!isScanning) scanUniverseSize = size }
                            .border(
                                1.dp,
                                if (isSelected) GreenNeon else Color.Gray.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            ),
                        color = if (isSelected) GreenNeon.copy(alpha = 0.15f) else Color.Transparent
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) GreenNeon else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (cachedCount > 0) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            PlaybookScanner.clearCache()
                            cachedCount = PlaybookScanner.getCachedCount()
                        },
                    color = CardMid
                ) {
                    Text(
                        text = "⚡ $cachedCount cached ✕",
                        color = YellowNeon,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
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
                            isScanning = true
                            progress = 0
                            signals = emptyList()
                            earlyCandidates = emptyList()
                            resultsViewMode = 0
                            try {
                                val result = PlaybookScanner.scan(
                                    context = context,
                                    niftyAbove200 = marketContext?.above200,
                                    universeLimit = scanUniverseSize
                                ) { p, t, sym ->
                                    progress = p; total = t; currentSymbol = sym
                                }
                                signals = result.signals
                                earlyCandidates = result.earlyCandidates
                                cachedCount = PlaybookScanner.getCachedCount()
                                PlaybookScanner.saveSignals(context, result.signals.filter { it.aiAction == "BUY" })
                                val targetName = when (scanUniverseSize) {
                                    50 -> "Nifty 50"
                                    100 -> "Nifty 100"
                                    else -> "Nifty 500"
                                }
                                statusMsg = "✅ $targetName scan complete — ${result.signals.count { it.aiAction == "BUY" }} BUY signals · " +
                                    "${result.earlyCandidates.size} early-turn / pre-breakout watches from ${result.signals.size} setup hits"
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
                    val buttonTitle = when (scanUniverseSize) {
                        50 -> "Run Fast Scan (Nifty 50)"
                        100 -> "Run Scanner (Nifty 100)"
                        else -> "Run Full Universe Scan (500 Stocks)"
                    }
                    Text(buttonTitle, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, color = if (statusMsg.startsWith("✅")) GreenNeon else RedNeon,
                fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }

        // ── View Mode Selector (AI Shortlist vs All Signals vs Early Radar) ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to "🎯 AI Shortlist (Top 5)",
                1 to "📋 All Signals (${signals.size})",
                2 to "⚡ Early Radar (${earlyCandidates.size})"
            ).forEach { (mode, label) ->
                val isSelected = resultsViewMode == mode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { resultsViewMode = mode }
                        .border(
                            1.dp,
                            if (isSelected) GreenNeon else Color.Gray.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        ),
                    color = if (isSelected) GreenNeon.copy(alpha = 0.18f) else CardDark
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                        color = if (isSelected) GreenNeon else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Setup filter chips only shown when user chooses "All Signals" (Mode 1)
        if (resultsViewMode == 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
        }

        // ── Results list ─────────────────────────────────────────────────────
        when (resultsViewMode) {
            0 -> {
                // ── AI Deep-Dive Shortlist (Top 3-5 Picks) ──
                if (signals.isEmpty() && !isScanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoGraph, null, tint = GreenNeon, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("AI Agent Ready for Deep-Dive Analysis", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Run the scanner above. The AI Deep-Dive Agent will evaluate all signals and shortlist the Top 3 to 5 highest-probability picks with strict R:R and institutional edge.",
                                color = Color.Gray, fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else if (signals.isNotEmpty()) {
                    val aiShortlist = remember(signals) {
                        try {
                            AiStockDeepDiveAgent.analyzePlaybookSignals(signals, maxPicks = 5)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    if (aiShortlist.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text("No signals met the high-conviction criteria.", color = Color.Gray)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { resultsViewMode = 1 }) {
                                    Text("View All ${signals.size} Signals")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                AiDeepDiveHeaderBanner(
                                    totalEvaluated = signals.size,
                                    shortlistedCount = aiShortlist.size,
                                    sourceName = "ChartLink Playbook Engine"
                                )
                            }
                            items(aiShortlist, key = { "ai-chartlink-${it.rank}-${it.symbol}" }) { pick ->
                                AiDeepDivePickCard(pick = pick)
                            }
                        }
                    }
                }
            }
            1 -> {
                // ── All Signals ──
                if (filtered.isEmpty() && !isScanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No setups match the selected filter.", color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { signal ->
                            PlaybookSignalCard(
                                signal,
                                expandedId == signal.id,
                                onClick = { expandedId = if (expandedId == signal.id) null else signal.id }
                            )
                        }
                    }
                }
            }
            2 -> {
                // ── Early Radar ──
                if (earlyCandidates.isEmpty() && !isScanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.TrackChanges, null, tint = PurplePill, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No early-turn or pre-breakout candidates met the evidence threshold.", color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "WATCHLIST ONLY · Ranked by observable setup evidence before breakout confirmation.",
                                color = YellowNeon, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(earlyCandidates, key = { "early-${it.symbol}" }) { candidate ->
                            EarlySetupCandidateCard(candidate)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIGNAL CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EarlySetupCandidateCard(candidate: EarlySetupCandidate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PurplePill.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(candidate.sector, color = Color.Gray, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(candidate.stage, color = PurplePill, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text("Evidence score ${candidate.score}/100", color = YellowNeon, fontSize = 10.sp)
                }
            }
            Divider(color = Color.DarkGray, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceBlock("Close", "₹${String.format("%.2f", candidate.close)}", Color.White)
                PriceBlock("Pivot", "₹${String.format("%.2f", candidate.pivot)}", BluePill)
                PriceBlock("To pivot", "${String.format("%.1f", candidate.distanceToPivotPct)}%", YellowNeon)
            }
            Text(
                "5-session ${String.format("%+.1f", candidate.change5dPct)}% · " +
                    "RSI ${String.format("%.0f", candidate.rsi14)} · " +
                    "volume ${String.format("%.2f", candidate.volumeRatio)}× recent baseline",
                color = Color.LightGray, fontSize = 11.sp
            )
            candidate.reasons.forEach { reason ->
                Text("• $reason", color = Color.LightGray, fontSize = 10.sp)
            }
            Text(
                "Watch only: the pivot is not confirmed as support or resistance. Wait for a close/volume confirmation; the setup can fail.",
                color = YellowNeon.copy(alpha = 0.9f), fontSize = 10.sp
            )
        }
    }
}

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
                    Text("🚀 ${signal.setup.label} · Rule-based scan; historical performance not verified",
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
// TAB 2 — DAY-WISE FOLDERS AUTO-EVALUATED ARCHIVE
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybookArchiveTab() {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    var signals  by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }
    var filter   by remember { mutableStateOf<TradeStatus?>(null) } // null = All
    var expandedId by remember { mutableStateOf<String?>(null) }
    var collapsedDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isEvaluating by remember { mutableStateOf(false) }
    var evalProgress by remember { mutableStateOf(0) }
    var evalTotal    by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun runAutoEvaluation(recheckAll: Boolean = false) {
        scope.launch {
            isEvaluating = true
            evalProgress = 0
            try {
                val evaluated = PlaybookScanner.autoResolveOpenSignals(context, recheckAll = recheckAll) { p, t, _ ->
                    evalProgress = p
                    evalTotal = t
                }
                signals = evaluated
            } catch (_: Exception) {}
            isEvaluating = false
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { PlaybookScanner.loadAllSignals(context) }
        signals = loaded
        if (loaded.isNotEmpty()) {
            // Automatically re-evaluate and self-heal all historical signals against post-entry candles
            runAutoEvaluation(recheckAll = true)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset Archive") },
            text = { Text("Are you sure you want to clear all archived trade records? You can run the scanner again anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    PlaybookScanner.clearAllSignals(context)
                    signals = emptyList()
                }) {
                    Text("Clear All", color = RedNeon)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filtered = remember(signals, filter) {
        if (filter == null) signals else signals.filter { it.status == filter }
    }
    val groupedByDay = remember(filtered) {
        filtered.groupBy { it.signalDate }
    }

    val targetHits = signals.count { it.status == TradeStatus.TARGET_HIT }
    val slHits     = signals.count { it.status == TradeStatus.SL_HIT }
    val openCount  = signals.count { it.status == TradeStatus.OPEN }
    val closedCount = targetHits + slHits + signals.count { it.status == TradeStatus.DAY22_EXIT }
    val winRate    = if (closedCount > 0) (targetHits * 100.0 / closedCount) else 0.0

    Column(Modifier.fillMaxSize()) {
        // ── Top Summary Header (Auto Hit vs SL stats) ─────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("📁 Day-Wise Trade Folders", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Entry, Target Hit & SL dates tracked automatically", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !isEvaluating) { runAutoEvaluation(recheckAll = true) },
                            color = if (isEvaluating) CardMid else GreenNeon.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isEvaluating) "Checking $evalProgress/$evalTotal…" else "⚡ Re-check All",
                                color = if (isEvaluating) YellowNeon else GreenNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showClearDialog = true },
                            color = CardMid
                        ) {
                            Icon(Icons.Default.Delete, "Clear Archive", tint = Color.Gray, modifier = Modifier.size(18.dp).padding(2.dp))
                        }
                    }
                }
                Divider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PriceBlock("🎯 Targets Hit", "$targetHits", GreenNeon)
                    PriceBlock("❌ SL Hit", "$slHits", RedNeon)
                    PriceBlock("⏳ Open", "$openCount", BluePill)
                    PriceBlock("Win Rate", "${String.format("%.1f", winRate)}%", if (winRate >= 50) GreenNeon else YellowNeon)
                }
            }
        }

        // ── Status Filter Chips (All, OPEN, TARGET_HIT, SL_HIT, etc.) ────────
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("ALL (${signals.size})", fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenNeon.copy(0.2f),
                    containerColor = CardDark,
                    selectedLabelColor = GreenNeon,
                    labelColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            )
            TradeStatus.values().forEach { s ->
                val count = signals.count { it.status == s }
                val col = when (s) {
                    TradeStatus.OPEN         -> BluePill
                    TradeStatus.SL_HIT       -> RedNeon
                    TradeStatus.TARGET_HIT   -> GreenNeon
                    TradeStatus.DAY22_EXIT   -> YellowNeon
                    TradeStatus.SKIPPED      -> Color.Gray
                }
                FilterChip(
                    selected = filter == s,
                    onClick = { filter = if (filter == s) null else s },
                    label = { Text("${s.name.replace("_", " ")} ($count)", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = col.copy(0.2f),
                        containerColor = CardDark,
                        selectedLabelColor = col,
                        labelColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No ${filter?.name?.lowercase()?.replace("_", " ") ?: ""} trades in archive.\nRun scanner to populate signals.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedByDay.forEach { (dayDate, daySignals) ->
                    val dayWins = daySignals.count { it.status == TradeStatus.TARGET_HIT }
                    val dayLoss = daySignals.count { it.status == TradeStatus.SL_HIT }
                    val dayOpen = daySignals.count { it.status == TradeStatus.OPEN }
                    val isFolderOpen = !collapsedDays.contains(dayDate)

                    // ── DAY FOLDER HEADER (Collapsible Folder) ────────────────
                    item(key = "folder-$dayDate") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isFolderOpen) CardDark else CardMid),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFolderOpen) GreenNeon.copy(alpha = 0.4f) else Color.DarkGray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    collapsedDays = if (isFolderOpen) collapsedDays + dayDate else collapsedDays - dayDate
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isFolderOpen) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = "Day Folder",
                                        tint = if (isFolderOpen) GreenNeon else YellowNeon,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (dayDate.isNotBlank()) dayDate else "Unknown Date",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(${daySignals.size} ${if (daySignals.size == 1) "trade" else "trades"})",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (dayWins > 0) Text("🎯 $dayWins Win", color = GreenNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (dayLoss > 0) Text("❌ $dayLoss SL", color = RedNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (dayOpen > 0) Text("⏳ $dayOpen Open", color = BluePill, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Icon(
                                        if (isFolderOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ── TRADES INSIDE FOLDER (Visible when expanded) ─────────
                    if (isFolderOpen) {
                        items(daySignals, key = { it.id }) { signal ->
                            ArchiveTradeCard(
                                signal = signal,
                                expanded = expandedId == signal.id,
                                onClick = { expandedId = if (expandedId == signal.id) null else signal.id },
                                onUpdateStatus = { newStatus, exitPrice, reason ->
                                    val today = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                    scope.launch(Dispatchers.IO) {
                                        PlaybookScanner.updateSignalStatus(context, signal.id, newStatus, exitPrice, today, reason)
                                        signals = PlaybookScanner.loadAllSignals(context)
                                    }
                                }
                            )
                        }
                    }
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
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.DarkGray)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header Row: Symbol, Setup, Status, Return %
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(signal.symbol, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 15.sp)
                    Text("Setup ${signal.setup.code} · ${signal.sector}", color = Color.Gray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = statusColor.copy(0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(signal.status.name.replace("_", " "), color = statusColor, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp, 2.dp))
                    }
                    if (!isOpen) {
                        Text("${if (signal.returnPct >= 0) "+" else ""}${String.format("%.1f", signal.returnPct)}%",
                            color = returnColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    } else if (signal.currentPrice > 0) {
                        val liveRet = signal.currentReturnPct
                        val liveCol = if (liveRet >= 0) GreenNeon else RedNeon
                        Text("Live: ${if (liveRet >= 0) "+" else ""}${String.format("%.1f", liveRet)}%",
                            color = liveCol, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(color = Color.DarkGray.copy(alpha = 0.4f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── DATES & EXECUTION ROW ────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📅 Entry Date: ${signal.signalDate}", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Entry: ₹${String.format("%.2f", signal.entryPrice)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                when (signal.status) {
                    TradeStatus.TARGET_HIT -> {
                        Surface(color = GreenNeon.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "🎯 Target Hit Date: ${signal.exitDate.ifBlank { signal.signalDate }} @ ₹${String.format("%.2f", signal.exitPrice)} · ${signal.exitReason}",
                                color = GreenNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    TradeStatus.SL_HIT -> {
                        Surface(color = RedNeon.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "❌ Stop-Loss Hit Date: ${signal.exitDate.ifBlank { signal.signalDate }} @ ₹${String.format("%.2f", signal.exitPrice)} · ${signal.exitReason}",
                                color = RedNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    TradeStatus.DAY22_EXIT -> {
                        Surface(color = YellowNeon.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "📅 Day 22 Exit Date: ${signal.exitDate.ifBlank { signal.day22ExitDate }} @ ₹${String.format("%.2f", signal.exitPrice)}",
                                color = YellowNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    TradeStatus.OPEN -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⏳ Day 22 Max Exit Date: ${signal.day22ExitDate}", color = YellowNeon, fontSize = 10.sp)
                            Text("Live Spot: ₹${String.format("%.2f", signal.currentPrice.takeIf { it > 0 } ?: signal.entryPrice)}", color = BluePill, fontSize = 10.sp)
                        }
                    }
                    else -> {}
                }
            }

            Spacer(Modifier.height(8.dp))
            // Key Levels Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceBlock("SL (-4%)", "₹${String.format("%.2f", signal.stopLoss)}",   RedNeon)
                PriceBlock("T1 (+4%)", "₹${String.format("%.2f", signal.refTarget1)}", GreenNeon)
                PriceBlock("T2 (+8%)", "₹${String.format("%.2f", signal.refTarget2)}", BluePill)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    Divider(color = Color.DarkGray.copy(0.5f), thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("AI: ${signal.aiReason}", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))

                    if (isOpen) {
                        Text("Manual Override:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutcomeButton("🎯 T1 Hit",  GreenNeon)   { onUpdateStatus(TradeStatus.TARGET_HIT, signal.refTarget1, "Target 1 Hit (+4%)") }
                            OutcomeButton("🎯 T2 Hit",  BluePill)    { onUpdateStatus(TradeStatus.TARGET_HIT, signal.refTarget2, "Target 2 Hit (+8%)") }
                            OutcomeButton("❌ SL Hit",  RedNeon)     { onUpdateStatus(TradeStatus.SL_HIT,     signal.stopLoss,    "Stop-Loss Hit (-4%)") }
                            OutcomeButton("📅 Day22",   YellowNeon)  { onUpdateStatus(TradeStatus.DAY22_EXIT, signal.entryPrice,  "Day 22 Time Exit") }
                        }
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
internal fun PlaybookLedgerStatsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<Map<PlaybookSetup, PlaybookLedgerStats>>(emptyMap()) }
    var allSignals by remember { mutableStateOf<List<PlaybookSignal>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshStats() {
        scope.launch {
            isRefreshing = true
            try {
                val evaluated = PlaybookScanner.autoResolveOpenSignals(context, recheckAll = true)
                allSignals = evaluated
                stats = PlaybookScanner.computeStats(evaluated)
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        val signals = withContext(Dispatchers.IO) { PlaybookScanner.loadAllSignals(context) }
        allSignals  = signals
        stats       = PlaybookScanner.computeStats(signals)
        refreshStats()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Summary bar
        val closed = allSignals.filter { it.status in listOf(TradeStatus.SL_HIT, TradeStatus.TARGET_HIT, TradeStatus.DAY22_EXIT) }
        val wins   = closed.count { it.returnPct > 0 }
        val avgRet = if (closed.isNotEmpty()) closed.map { it.returnPct }.average() else 0.0

        Surface(color = CardMid, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall Performance", color = GreenNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isRefreshing) { refreshStats() },
                        color = if (isRefreshing) CardDark else GreenNeon.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isRefreshing) "Updating…" else "⚡ Recalculate Win Rate",
                            color = if (isRefreshing) YellowNeon else GreenNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
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

        // Historical performance is not independently validated.
        Text("📚 Rule-based setup reference (no verified historical statistics)", color = Color.Gray,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        PlaybookSetup.values().forEach { setup ->
            val s = stats[setup]
            SetupStatCard(setup, s)
        }

        // Warning
        Spacer(Modifier.height(4.dp))
        Surface(color = Color(0xFF1A1A00), shape = RoundedCornerShape(10.dp)) {
            Text(
                "⚠️ These screens evaluate current daily candles only; displayed setup labels and rules " +
                "are not validated historical performance or a recommendation. Backtest before use.",
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
                        Text("UNVERIFIED", color = YellowNeon, fontSize = 9.sp, modifier = Modifier.padding(4.dp, 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Text("Historical performance", color = Color.DarkGray, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                StatBlock("Win Rate",   "N/A",                 GreenNeon)
                StatBlock("Avg/Trade",  "N/A",                 BluePill)
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
