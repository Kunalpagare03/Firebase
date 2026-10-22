package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.Kunal.stock.data.FnoUniverseItem
import com.Kunal.stock.data.FnoUniverseProvider
import com.Kunal.stock.ui.theme.*
import kotlin.random.Random

// ────────────────────────────────────────────────────────────────────
// DATA MODELS
// ────────────────────────────────────────────────────────────────────

data class FuturesContractQuote(
    val symbol: String,
    val name: String,
    val sector: String,
    val spotPrice: Double,
    val futurePrice: Double,
    val basis: Double,
    val basisPct: Double,
    val rollCostAnnualPct: Double,
    val oiShares: Long,
    val oiChangePct: Double,
    val volumeLots: Long,
    val buildupType: String,
    val vwap: Double,
    val rsi14: Double,
    val signal: String,
    val target1: Double,
    val target2: Double,
    val stopLoss: Double,
    val lotSize: Int
)

data class FuturesSuggestionEntry(
    val symbol: String,
    val recommendation: String,
    val reasoning: String,
    val futurePrice: Double,
    val target1: Double,
    val stopLoss: Double,
    val scannedAt: String,
    val signalBlock: String = ""
)

// ────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturesQuantScreen() {
    val context = LocalContext.current

    var fnoList by remember { mutableStateOf<List<FnoUniverseItem>>(emptyList()) }
    var segments by remember { mutableStateOf<List<String>>(listOf("ALL", "Index", "F&O Stock")) }
    var selectedSegment by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFuturesTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val list = FnoUniverseProvider.loadFnoAsync(context)
        fnoList = list
        segments = FnoUniverseProvider.getCachedSegments()
    }

    val benchmarkFutures = remember {
        listOf(
            "NIFTY" to 24850.0, "BANKNIFTY" to 51680.0, "FINNIFTY" to 23890.0,
            "RELIANCE" to 2940.0, "HDFCBANK" to 1690.0, "ICICIBANK" to 1245.0,
            "TATAMOTORS" to 975.0, "INFY" to 1890.0, "TCS" to 4270.0,
            "SBIN" to 825.0, "BHARTIARTL" to 1640.0, "LT" to 3620.0,
            "BAJFINANCE" to 7320.0, "AXISBANK" to 1195.0, "MARUTI" to 12450.0,
            "TATASTEEL" to 158.0
        )
    }

    var selectedFutureSymbol by remember { mutableStateOf("NIFTY") }
    var selectedFutureName by remember { mutableStateOf("NIFTY 50 INDEX FUTURES") }
    var selectedFutureSector by remember { mutableStateOf("Index") }
    var showStockBrowserModal by remember { mutableStateOf(false) }

    val currentQuote = remember(selectedFutureSymbol) {
        generateFutureQuote(selectedFutureSymbol, selectedFutureName, selectedFutureSector)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedFuturesTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ElectricBlue
        ) {
            listOf("Analytics", "Signal Scanner", "History").forEachIndexed { index, title ->
                Tab(
                    selected = selectedFuturesTab == index,
                    onClick = { selectedFuturesTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedFuturesTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        when (selectedFuturesTab) {
            0 -> MultiAgentAnalyticsTab(
                currentQuote = currentQuote,
                benchmarkFutures = benchmarkFutures,
                selectedFutureSymbol = selectedFutureSymbol,
                fnoList = fnoList,
                segments = segments,
                selectedSegment = selectedSegment,
                searchQuery = searchQuery,
                showStockBrowserModal = showStockBrowserModal,
                onSymbolSelected = { sym, name, sector ->
                    selectedFutureSymbol = sym
                    selectedFutureName = name
                    selectedFutureSector = sector
                },
                onSegmentChange = { selectedSegment = it },
                onSearchChange = { searchQuery = it },
                onShowBrowser = { showStockBrowserModal = it }
            )
            1 -> FuturesSignalScannerTab(benchmarkFutures, context)
            2 -> FuturesScanHistoryTab(context)
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// TAB 0: MULTI-AGENT ANALYTICS DASHBOARD
// ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiAgentAnalyticsTab(
    currentQuote: FuturesContractQuote,
    benchmarkFutures: List<Pair<String, Double>>,
    selectedFutureSymbol: String,
    fnoList: List<FnoUniverseItem>,
    segments: List<String>,
    selectedSegment: String,
    searchQuery: String,
    showStockBrowserModal: Boolean,
    onSymbolSelected: (String, String, String) -> Unit,
    onSegmentChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onShowBrowser: (Boolean) -> Unit
) {
    // Run multi-agent pipeline
    var agentResult by remember { mutableStateOf<FullAgentPipelineResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFutureSymbol) {
        isProcessing = true
        kotlinx.coroutines.delay(600)
        agentResult = runFullAgentPipeline(selectedFutureSymbol, currentQuote.spotPrice)
        isProcessing = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Multi-Agent Futures Terminal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "6-Agent Hierarchical AI Mesh · Options & Futures Clusters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ElectricBlue)
            } else {
                Surface(
                    color = ElectricBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        "AGENTS ONLINE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue
                    )
                }
            }
        }

        // ── Selected Contract Card ──
        FuturesContractCard(
            quote = currentQuote,
            onShowBrowser = { onShowBrowser(true) }
        )

        // ── Quick Switcher ──
        Text(
            "Active F&O Heavyweights (1-Tap Switch):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            benchmarkFutures.forEach { (sym, _) ->
                val isSelected = selectedFutureSymbol == sym
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val nm = when (sym) {
                            "NIFTY" -> "NIFTY 50 INDEX"
                            "BANKNIFTY" -> "BANK NIFTY INDEX"
                            "FINNIFTY" -> "NIFTY FINANCIAL SERVICES"
                            else -> sym
                        }
                        onSymbolSelected(sym, nm, if (sym.contains("NIFTY")) "Index" else "F&O Stock")
                    },
                    label = { Text(sym, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // ── Agent Architecture Diagram ──
        AgentArchitectureBanner()

        // ── Master Consensus Card ──
        val result = agentResult
        if (result != null) {
            MasterConsensusCard(result.masterConsensus)

            // ── Options Cluster ──
            OptionsClusterCard(result.optionsCluster)

            // ── Futures Cluster ──
            FuturesClusterCard(result.futuresCluster)

            // ── Signal Payload Card (if active) ──
            if (result.masterConsensus.payload.isActive) {
                SignalPayloadCard(result.masterConsensus.payload)
            }
        } else if (isProcessing) {
            AgentLoadingCard()
        }

        // ── F&O Universe Browser ──
        if (showStockBrowserModal) {
            FnoUniverseDialog(
                fnoList = fnoList,
                segments = segments,
                selectedSegment = selectedSegment,
                searchQuery = searchQuery,
                selectedFutureSymbol = selectedFutureSymbol,
                onSegmentChange = onSegmentChange,
                onSearchChange = onSearchChange,
                onSelect = { sym, name, sector -> onSymbolSelected(sym, name, sector) },
                onDismiss = { onShowBrowser(false) }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// AGENT UI COMPONENTS
// ────────────────────────────────────────────────────────────────────

@Composable
private fun AgentArchitectureBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "🤖 Hierarchical Multi-Agent Mesh",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ElectricBlue
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OPTIONS CLUSTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                    listOf("⬡ Greeks Agent", "⬡ IV Smile Agent", "⬡ OI Concentration").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("▼ Options Manager", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BullishGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
                    Text("⟺", fontSize = 18.sp, color = ElectricBlue)
                    Text("BRIDGE", style = MaterialTheme.typography.labelSmall, color = ElectricBlue, fontWeight = FontWeight.Bold)
                    Text("⟺", fontSize = 18.sp, color = ElectricBlue)
                    Spacer(Modifier.height(4.dp))
                    Text("▲", fontSize = 16.sp, color = GoldWarning)
                    Text("MASTER", style = MaterialTheme.typography.labelSmall, color = GoldWarning, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("FUTURES CLUSTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                    listOf("⬡ Long/Short Agent", "⬡ Premium/Disc Agent", "⬡ Delivery Volume").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("▼ Futures Manager", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BullishGreen)
                }
            }
        }
    }
}

@Composable
private fun MasterConsensusCard(master: MasterConsensus) {
    val dirColor = when (master.finalDirection) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> NeutralSlate
    }
    val signalColor = when {
        master.finalSignal.contains("BUY") -> BullishGreen
        master.finalSignal.contains("SELL") -> BearishRed
        else -> GoldWarning
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(2.dp, if (master.trendAlignmentLock) BearishRed else dirColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MASTER SUPERVISOR AI", style = MaterialTheme.typography.labelSmall, color = GoldWarning, fontWeight = FontWeight.Bold)
                    Text("Multi-Asset Consensus & Risk Filtration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = signalColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, signalColor)
                ) {
                    Text(
                        master.finalSignal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = signalColor
                    )
                }
            }

            // Trend Alignment Lock Banner
            if (master.trendAlignmentLock) {
                Surface(
                    color = BearishRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BearishRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        master.trendLockReason,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = BearishRed, fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Direction & Trend Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Direction", master.finalDirection, dirColor, Modifier.weight(1f))
                MetricTile("Confidence", "${master.overallConfidence}%",
                    if (master.overallConfidence >= 70) BullishGreen else if (master.overallConfidence >= 50) GoldWarning else BearishRed,
                    Modifier.weight(1f))
                MetricTile("Macro Trend", master.macroTrend,
                    if (master.macroTrend == "BULLISH") BullishGreen else BearishRed,
                    Modifier.weight(1f))
            }

            // Cross-cluster insight
            if (master.crossClusterInsight.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("🔗 Cross-Cluster Insight", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            master.crossClusterInsight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Risk: ${master.riskParameterStatus}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (master.riskParameterStatus == "CLEARED") BullishGreen else GoldWarning
                    )
                }
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Micro: ${master.microTrend}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (master.microTrend == "BULLISH") BullishGreen else if (master.microTrend == "BEARISH") BearishRed else GoldWarning
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsClusterCard(report: OptionsClusterReport) {
    var expanded by remember { mutableStateOf(true) }
    val biasColor = when (report.clusterBias) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> NeutralSlate
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, biasColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OPTIONS CLUSTER MANAGER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Greeks · IV Smile · OI Concentration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = biasColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(report.clusterBias, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = biasColor)
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confidence: ${report.clusterConfidence}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgentStatusCard(report.greeksReport)
                    AgentStatusCard(report.ivSmileReport)
                    AgentStatusCard(report.openInterestReport)
                    if (report.structuralHypothesis.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("📋 Options Manager Hypothesis", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                                Spacer(Modifier.height(4.dp))
                                Text(report.structuralHypothesis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuturesClusterCard(report: FuturesClusterReport) {
    var expanded by remember { mutableStateOf(true) }
    val biasColor = when (report.clusterBias) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> NeutralSlate
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, biasColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FUTURES CLUSTER MANAGER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    Text("Long/Short · Premium/Discount · Delivery Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = biasColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(report.clusterBias, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = biasColor)
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confidence: ${report.clusterConfidence}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgentStatusCard(report.longShortReport)
                    AgentStatusCard(report.premiumDiscountReport)
                    AgentStatusCard(report.deliveryVolumeReport)
                    if (report.momentumMatrix.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("📋 Futures Momentum Matrix", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                Spacer(Modifier.height(4.dp))
                                Text(report.momentumMatrix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(agent: AgentStatus) {
    val biasColor = when (agent.signalBias) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> NeutralSlate
    }
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, biasColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(6.dp).background(biasColor, CircleShape))
                        Text(agent.agentName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        agent.outputSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${agent.confidence}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = biasColor)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    agent.keyMetrics.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPayloadCard(payload: FuturesSignalPayload) {
    val isBull = payload.actionType.contains("BUY")
    val accentColor = if (isBull) BullishGreen else BearishRed
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isBull) BullishGreenContainer else BearishRedContainer),
        border = BorderStroke(2.dp, accentColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎯 AUTOMATED FUTURES SIGNAL: SYSTEM TRIGGERED", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = accentColor)
            Divider(color = accentColor.copy(alpha = 0.4f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ACTION TYPE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(payload.actionType, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = accentColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ASSET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(payload.assetName, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Text("CONTRACT: ${payload.contractSpec}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Divider(color = accentColor.copy(alpha = 0.3f))

            Text("📊 MATRIX EXECUTION LIMITS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SignalLimitRow("• ENTRY RANGE", "₹${String.format("%,.2f", payload.entryFloor)} – ₹${String.format("%,.2f", payload.entryCeiling)}", MaterialTheme.colorScheme.onSurface)
                    SignalLimitRow("• TAKE PROFIT 1", "₹${String.format("%,.2f", payload.takeProfit1)}", BullishGreen)
                    SignalLimitRow("• TAKE PROFIT 2", "₹${String.format("%,.2f", payload.takeProfit2)}", BullishGreen)
                    SignalLimitRow("• HARD STOP LOSS", "₹${String.format("%,.2f", payload.hardStopLoss)}", BearishRed)
                }
            }

            Divider(color = accentColor.copy(alpha = 0.3f))
            Text("🔍 COGNITIVE SYNTHESIS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
            Text(
                "\"${payload.cognitiveSynthesis}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Divider(color = accentColor.copy(alpha = 0.3f))
            Text("🛡️ PILLARS OF PROOF INTEGRITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
            PillarRow(payload.pillarVolume, "Volume Confirmation", payload.pillarVolumeDelta, if (payload.pillarVolume) "CONGRUENT" else "NOT MET")
            PillarRow(payload.pillarSRRetest, "S/R Retest", payload.pillarSRLevel, if (payload.pillarSRRetest) "VERIFIED" else "NOT MET")
            PillarRow(payload.pillarMTFAlignment, "MTF Alignment", payload.pillarMTFStatus, if (payload.pillarMTFAlignment) "LOCKED" else "NOT MET")

            if (payload.allPillarsCleared) {
                Surface(color = BullishGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BullishGreen)) {
                    Text("✅ ALL PILLARS CLEARED — SIGNAL TRANSMISSION AUTHORIZED", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = BullishGreen)
                }
            }

            Text("Generated: ${payload.generatedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalLimitRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun PillarRow(passed: Boolean, name: String, detail: String, status: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(if (passed) "[✔]" else "[✘]", fontWeight = FontWeight.Bold, color = if (passed) BullishGreen else BearishRed, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("$detail → $status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
private fun AgentLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = ElectricBlue)
            Text("Initialising agent mesh pipeline...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Running 6 sub-agents in parallel compute tree", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// TAB 1: FUTURES SIGNAL SCANNER
// ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuturesSignalScannerTab(
    benchmarkFutures: List<Pair<String, Double>>,
    context: android.content.Context
) {
    val scanResults = remember { mutableStateListOf<FuturesSuggestionEntry>() }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf("INTRADAY") } // "INTRADAY" | "EOD"
    var showSignalDetail by remember { mutableStateOf<FuturesSuggestionEntry?>(null) }

    fun runScan() {
        isScanning = true
        scanResults.clear()
        val sdf = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.getDefault())
        lastScanTime = sdf.format(java.util.Date())

        benchmarkFutures.forEach { (sym, baseSpot) ->
            val pipeline = runFullAgentPipeline(sym, baseSpot)
            val master = pipeline.masterConsensus
            val payload = master.payload

            val recommendation = when (master.finalSignal) {
                "STRONG BUY" -> "STRONG BUY"
                "BUY" -> "BUY"
                "STRONG SELL" -> "STRONG SELL"
                "SELL" -> "SELL"
                else -> "NEUTRAL"
            }
            val reasoning = "${pipeline.futuresCluster.longShortReport.keyMetrics["Buildup Type"] ?: "Mixed"} · " +
                "Conf ${master.overallConfidence}% · " +
                "${pipeline.futuresCluster.premiumDiscountReport.keyMetrics["Basis"] ?: "Par"} basis · " +
                "${pipeline.optionsCluster.clusterBias} options cluster"

            val signalBlock = if (payload.isActive) serializeSignalPayload(payload) else ""
            val entry = FuturesSuggestionEntry(
                symbol = sym,
                recommendation = recommendation,
                reasoning = reasoning,
                futurePrice = baseSpot * (1.0 + Random.nextDouble(-0.002, 0.005)),
                target1 = payload.takeProfit1.takeIf { it > 0 } ?: (baseSpot * 1.018),
                stopLoss = payload.hardStopLoss.takeIf { it > 0 } ?: (baseSpot * 0.991),
                scannedAt = lastScanTime,
                signalBlock = signalBlock
            )
            scanResults.add(entry)
        }

        // Persist to SharedPreferences
        val prefs = context.getSharedPreferences("futures_suggestions_prefs", android.content.Context.MODE_PRIVATE)
        val existing = prefs.getString("futures_suggestion_history", "") ?: ""
        val newBatch = "===START===\n=== Scan @ $lastScanTime ($scanMode) ===\n" + scanResults.joinToString("\n") { e ->
            "${e.symbol}: ${e.recommendation} | ${e.reasoning} | FUT ₹${String.format("%.1f", e.futurePrice)} | T1 ₹${String.format("%.1f", e.target1)} | SL ₹${String.format("%.1f", e.stopLoss)}"
        }

        // Also save individual signal blocks
        val signalPart = scanResults.filter { it.signalBlock.isNotBlank() }.joinToString("\n---SIGNAL---\n") { it.signalBlock }
        val fullBatch = newBatch + (if (signalPart.isNotBlank()) "\n---SIGNALS---\n$signalPart" else "")
        prefs.edit().putString("futures_suggestion_history", existing + fullBatch).apply()

        isScanning = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Automated Signal Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Multi-agent pipeline · Pillars-of-Proof verification · Actionable signals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { runScan() },
                shape = RoundedCornerShape(10.dp),
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                if (isScanning) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isScanning) "Scanning..." else "Scan Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Mode selector: Intraday / EOD
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("INTRADAY" to "⚡ Intraday Live-Market", "EOD" to "🌙 After-Market EOD").forEach { (mode, label) ->
                FilterChip(
                    selected = scanMode == mode,
                    onClick = { scanMode = mode },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (scanMode == mode) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricBlue, containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Mode description
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (scanMode == "INTRADAY")
                    "⚡ Intraday Mode: Optimises ingestion for sub-second tick feeds. Focuses on 5m/15m pattern triggers, live OI buildup, and momentum burst detection for immediate execution."
                else
                    "🌙 EOD Mode: Deep backtesting sweeps, macro portfolio balancing, historical trend alignment, predictive candle analytics for next-session setup building.",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (lastScanTime.isNotBlank()) {
            Text("Last scan ($scanMode): $lastScanTime · ${scanResults.size} contracts analysed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (scanResults.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Analytics, null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                    Text("Tap 'Scan Now' to run the 6-agent pipeline across all F&O heavyweights.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Each result includes full Pillars-of-Proof verification and signal block.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val order = listOf("STRONG BUY", "BUY", "NEUTRAL", "SELL", "STRONG SELL")
            val sorted = scanResults.sortedBy { order.indexOf(it.recommendation) }

            // Summary chips
            val buyCnt = scanResults.count { it.recommendation.contains("BUY") }
            val sellCnt = scanResults.count { it.recommendation.contains("SELL") }
            val signalCnt = scanResults.count { it.signalBlock.isNotBlank() }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = BullishGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text("↑ $buyCnt Buy", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                }
                Surface(color = BearishRed.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text("↓ $sellCnt Sell", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                }
                Surface(color = GoldWarning.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text("🎯 $signalCnt Signals", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldWarning)
                }
            }

            sorted.forEach { entry ->
                ScanResultCard(entry, onViewSignal = { showSignalDetail = entry })
            }
        }
    }

    // Signal detail bottom sheet / dialog
    val detail = showSignalDetail
    if (detail != null && detail.signalBlock.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showSignalDetail = null },
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 ${detail.symbol} Signal Block", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showSignalDetail = null }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            detail.signalBlock,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSignalDetail = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ScanResultCard(entry: FuturesSuggestionEntry, onViewSignal: () -> Unit) {
    val recColor = when (entry.recommendation) {
        "STRONG BUY" -> BullishGreen
        "BUY" -> BullishGreen.copy(alpha = 0.8f)
        "STRONG SELL" -> BearishRed
        "SELL" -> BearishRed.copy(alpha = 0.8f)
        else -> GoldWarning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, recColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = recColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, recColor)) {
                        Text(entry.recommendation, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = recColor)
                    }
                    Text(entry.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${String.format("%,.1f", entry.futurePrice)}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            Text(entry.reasoning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("T1 ₹${String.format("%,.1f", entry.target1)}", style = MaterialTheme.typography.labelSmall, color = BullishGreen, fontWeight = FontWeight.Bold)
                    Text("SL ₹${String.format("%,.1f", entry.stopLoss)}", style = MaterialTheme.typography.labelSmall, color = BearishRed, fontWeight = FontWeight.Bold)
                }
                if (entry.signalBlock.isNotBlank()) {
                    TextButton(onClick = onViewSignal, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(12.dp), tint = ElectricBlue)
                        Spacer(Modifier.width(3.dp))
                        Text("Signal Block", fontSize = 11.sp, color = ElectricBlue)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// TAB 2: PERFORMANCE LEDGER & HISTORY
// ────────────────────────────────────────────────────────────────────

@Composable
private fun FuturesScanHistoryTab(context: android.content.Context) {
    val historyBatches = remember { mutableStateListOf<String>() }
    val ledgerEntries = remember { mutableStateListOf<FuturesLedgerEntry>() }
    var activeTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Load scan history
        val prefs = context.getSharedPreferences("futures_suggestions_prefs", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("futures_suggestion_history", "") ?: ""
        if (raw.isNotBlank()) {
            val batches = raw.split("===START===").filter { it.isNotBlank() }.reversed()
            historyBatches.clear()
            historyBatches.addAll(batches)
        }

        // Load ledger
        val ledgerPrefs = context.getSharedPreferences("futures_ledger_prefs", android.content.Context.MODE_PRIVATE)
        val ledgerRaw = ledgerPrefs.getString("ledger_entries", "") ?: ""
        if (ledgerRaw.isNotBlank()) {
            val entries = ledgerRaw.split("|||").filter { it.isNotBlank() }.mapNotNull { parseLedgerEntry(it) }
            ledgerEntries.clear()
            ledgerEntries.addAll(entries.reversed())
        } else {
            // Demo entries for display
            ledgerEntries.addAll(generateDemoLedger())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tabs
        TabRow(selectedTabIndex = activeTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = ElectricBlue) {
            listOf("Performance Ledger", "Scan History", "Zero-to-Hero").forEachIndexed { i, title ->
                Tab(selected = activeTab == i, onClick = { activeTab = i },
                    text = { Text(title, fontSize = 12.sp, fontWeight = if (activeTab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }

        when (activeTab) {
            0 -> PerformanceLedgerTab(ledgerEntries, context)
            1 -> ScanHistoryListTab(historyBatches, context)
            2 -> ZeroToHeroArchiveTab(ledgerEntries.filter { it.isHeroZero })
        }
    }
}

@Composable
private fun PerformanceLedgerTab(entries: List<FuturesLedgerEntry>, context: android.content.Context) {
    val totalPnl = entries.sumOf { it.pnl }
    val winCount = entries.count { it.status == "CLOSED_WIN" }
    val lossCount = entries.count { it.status == "CLOSED_LOSS" }
    val openCount = entries.count { it.status == "OPEN" }
    val totalRoi = if (entries.isNotEmpty()) entries.sumOf { it.roiPct } / entries.size else 0.0
    val pnlColor = if (totalPnl >= 0) BullishGreen else BearishRed

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Performance Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Immutable signal history · Entry/Exit tracking · ROI on closed trades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📊 Portfolio Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Total P&L", "${if (totalPnl >= 0) "+" else ""}₹${String.format("%,.0f", totalPnl)}", pnlColor, Modifier.weight(1f))
                    MetricTile("Avg ROI", "${String.format("%.1f", totalRoi)}%", if (totalRoi >= 0) BullishGreen else BearishRed, Modifier.weight(1f))
                    MetricTile("Win Rate", "${if (winCount + lossCount > 0) String.format("%.0f", winCount.toDouble() / (winCount + lossCount) * 100) else "--"}%", BullishGreen, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Open", "$openCount", GoldWarning, Modifier.weight(1f))
                    MetricTile("Wins", "$winCount", BullishGreen, Modifier.weight(1f))
                    MetricTile("Losses", "$lossCount", BearishRed, Modifier.weight(1f))
                }
            }
        }

        if (entries.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                    Text("No ledger entries yet. Signal executions will be logged here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            entries.forEach { entry -> LedgerEntryCard(entry) }
        }
    }
}

@Composable
private fun LedgerEntryCard(entry: FuturesLedgerEntry) {
    val statusColor = when (entry.status) {
        "CLOSED_WIN" -> BullishGreen
        "CLOSED_LOSS", "CLOSED_SL" -> BearishRed
        else -> GoldWarning
    }
    val pnlColor = if (entry.pnl >= 0) BullishGreen else BearishRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.isHeroZero) {
                        Surface(color = GoldWarning.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("⭐ H-Z", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GoldWarning)
                        }
                    }
                    Text(entry.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(color = if (entry.actionType == "LONG") BullishGreen.copy(alpha = 0.2f) else BearishRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(entry.actionType, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (entry.actionType == "LONG") BullishGreen else BearishRed)
                    }
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(entry.status.replace("_", " "), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Entry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${String.format("%,.2f", entry.entryPrice)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (entry.exitPrice > 0) {
                    Column {
                        Text("Exit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${String.format("%,.2f", entry.exitPrice)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${if (entry.pnl >= 0) "+" else ""}₹${String.format("%,.0f", entry.pnl)}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = pnlColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ROI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${if (entry.roiPct >= 0) "+" else ""}${String.format("%.1f", entry.roiPct)}%", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = pnlColor)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SL ₹${String.format("%,.1f", entry.stopLoss)}", style = MaterialTheme.typography.labelSmall, color = BearishRed)
                Text("T1 ₹${String.format("%,.1f", entry.target1)}", style = MaterialTheme.typography.labelSmall, color = BullishGreen)
                Text("T2 ₹${String.format("%,.1f", entry.target2)}", style = MaterialTheme.typography.labelSmall, color = BullishGreen)
                Text("Lots: ${entry.lots}×${entry.lotSize}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (entry.exitReason.isNotBlank()) {
                Text("Exit: ${entry.exitReason} · ${entry.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScanHistoryListTab(batches: List<String>, context: android.content.Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Scan History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Past suggestion scans for reference", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (batches.isNotEmpty()) {
                TextButton(onClick = {
                    context.getSharedPreferences("futures_suggestions_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().remove("futures_suggestion_history").apply()
                }) {
                    Text("Clear All", color = BearishRed, fontSize = 12.sp)
                }
            }
        }

        if (batches.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                    Text("No scan history yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("${batches.size} saved scans (most recent first)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            batches.take(20).forEachIndexed { i, batch ->
                var isExpanded by remember { mutableStateOf(i == 0) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val firstLine = batch.lines().firstOrNull()?.removePrefix("=== Scan @ ")?.removeSuffix(" ===") ?: "Scan ${i + 1}"
                            Text("📋 $firstLine", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ElectricBlue)
                            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                        }
                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                batch.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("---") }.forEach { line ->
                                    val sym = line.substringBefore(":").trim()
                                    val rest = line.substringAfter(":").trim()
                                    val rec = rest.substringBefore("|").trim()
                                    val recColor = when {
                                        rec.contains("STRONG BUY") -> BullishGreen
                                        rec.contains("BUY") -> BullishGreen.copy(alpha = 0.8f)
                                        rec.contains("STRONG SELL") -> BearishRed
                                        rec.contains("SELL") -> BearishRed.copy(alpha = 0.8f)
                                        else -> GoldWarning
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = recColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text(sym, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = recColor)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(rest, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZeroToHeroArchiveTab(heroEntries: List<FuturesLedgerEntry>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⭐ Zero-to-Hero Archive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
        Text(
            "High-risk, high-reward asymmetric trades (0DTE lottery mechanics). Immutable vault — no data trimming or omission. Full auditing integrity preserved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (heroEntries.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⭐", fontSize = 32.sp)
                    Text("No Zero-to-Hero trades logged yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("These are flagged automatically when the system detects asymmetric risk-reward setups (≥5× multiplier).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val totalPnl = heroEntries.sumOf { it.pnl }
            val bestRoi = heroEntries.maxOfOrNull { it.roiPct } ?: 0.0
            Surface(
                color = GoldWarning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldWarning.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${heroEntries.size}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = GoldWarning)
                        Text("Total Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${if (totalPnl >= 0) "+" else ""}₹${String.format("%,.0f", totalPnl)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (totalPnl >= 0) BullishGreen else BearishRed)
                        Text("Total P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${String.format("%.0f", bestRoi)}%", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BullishGreen)
                        Text("Best ROI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            heroEntries.forEach { entry -> LedgerEntryCard(entry) }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// SHARED CONTRACT CARD & FNO DIALOG
// ────────────────────────────────────────────────────────────────────

@Composable
private fun FuturesContractCard(quote: FuturesContractQuote, onShowBrowser: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, when (quote.buildupType) {
            "LONG BUILDUP", "SHORT COVERING" -> BullishGreen.copy(alpha = 0.5f)
            else -> BearishRed.copy(alpha = 0.5f)
        })
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(quote.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(6.dp))
                        Surface(color = ElectricBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("CURRENT MONTH FUT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElectricBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("${quote.name} • ${quote.sector}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onShowBrowser, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("All 219 F&O", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Future Price (LTP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${String.format("%,.2f", quote.futurePrice)}", fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("Spot Underlying", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${String.format("%,.2f", quote.spotPrice)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Basis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val basisColor = if (quote.basis >= 0) BullishGreen else BearishRed
                    Text("${if (quote.basis >= 0) "+" else ""}₹${String.format("%.2f", quote.basis)} (${String.format("%.2f", quote.basisPct)}%)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = basisColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val buildupBg = when (quote.buildupType) {
                    "LONG BUILDUP" -> BullishGreenContainer; "SHORT COVERING" -> Color(0xFF004D40)
                    "SHORT BUILDUP" -> BearishRedContainer; else -> Color(0xFF4A148C)
                }
                val buildupText = when (quote.buildupType) {
                    "LONG BUILDUP" -> BullishGreen; "SHORT COVERING" -> Color(0xFF80CBC4)
                    "SHORT BUILDUP" -> BearishRed; else -> Color(0xFFCE93D8)
                }
                Surface(color = buildupBg, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, buildupText.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(buildupText, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(quote.buildupType, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = buildupText)
                    }
                }
                Text("Ann. Carry: ${String.format("%.2f", quote.rollCostAnnualPct)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Lot: ${quote.lotSize} qty", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("ACTION SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(quote.signal, fontWeight = FontWeight.Black, fontSize = 13.sp,
                            color = if (quote.signal.contains("BUY")) BullishGreen else if (quote.signal.contains("SELL")) BearishRed else GoldWarning)
                    }
                    Column { Text("STOP LOSS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BearishRed); Text("₹${String.format("%,.1f", quote.stopLoss)}", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Column { Text("TARGET 1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BullishGreen); Text("₹${String.format("%,.1f", quote.target1)}", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Column { Text("TARGET 2", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BullishGreen); Text("₹${String.format("%,.1f", quote.target2)}", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FnoUniverseDialog(
    fnoList: List<FnoUniverseItem>,
    segments: List<String>,
    selectedSegment: String,
    searchQuery: String,
    selectedFutureSymbol: String,
    onSegmentChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelect: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Select F&O Asset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("219 Official NSE Futures & Options Contracts", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search ticker...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = ElectricBlue) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    segments.forEach { seg ->
                        val isSel = selectedSegment.equals(seg, ignoreCase = true)
                        FilterChip(
                            selected = isSel, onClick = { onSegmentChange(seg) },
                            label = { Text(seg, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricBlue, containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val q = searchQuery.trim().uppercase()
                val filtered = fnoList.filter { item ->
                    val matchSeg = (selectedSegment == "ALL" || item.sector.equals(selectedSegment, ignoreCase = true))
                    val matchQ = q.isEmpty() || item.symbol.contains(q) || item.name.uppercase().contains(q)
                    matchSeg && matchQ
                }
                Box(modifier = Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(4.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtered) { item ->
                            Surface(
                                onClick = { onSelect(item.symbol, item.name, item.sector); onDismiss() },
                                color = if (selectedFutureSymbol == item.symbol) ElectricBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = if (selectedFutureSymbol == item.symbol) BorderStroke(1.dp, ElectricBlue) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Lot: ${item.lotSize} • ${item.sector}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text(item.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ────────────────────────────────────────────────────────────────────
// HELPERS
// ────────────────────────────────────────────────────────────────────

private fun formatOiCompact(count: Long): String = when {
    count >= 10_000_000 -> String.format("%.2f Cr", count / 10_000_000.0)
    count >= 100_000 -> String.format("%.2f L", count / 100_000.0)
    count >= 1_000 -> String.format("%.1f K", count / 1_000.0)
    else -> count.toString()
}

private fun generateFutureQuote(sym: String, name: String, sector: String): FuturesContractQuote {
    val baseSpot = when (sym) {
        "NIFTY" -> 24820.0; "BANKNIFTY" -> 51600.0; "FINNIFTY" -> 23850.0
        "RELIANCE" -> 2940.0; "TCS" -> 4270.0; "INFY" -> 1890.0
        "HDFCBANK" -> 1690.0; "ICICIBANK" -> 1245.0; "TATAMOTORS" -> 975.0
        "SBIN" -> 825.0; "BHARTIARTL" -> 1640.0; "LT" -> 3620.0
        else -> { val h = kotlin.math.abs(sym.hashCode()); (h % 4500 + 250).toDouble() }
    }
    val basisPct = (Random.nextDouble(-0.15, 0.65) * 100).toInt() / 100.0
    val basis = baseSpot * (basisPct / 100.0)
    val futurePrice = baseSpot + basis
    val rollCostAnnualPct = basisPct * 12.0
    val hash = kotlin.math.abs(sym.hashCode())
    val buildupType = when (hash % 4) { 0 -> "LONG BUILDUP"; 1 -> "SHORT COVERING"; 2 -> "SHORT BUILDUP"; else -> "LONG UNWINDING" }
    val signal = when (buildupType) { "LONG BUILDUP" -> "STRONG BUY"; "SHORT COVERING" -> "BUY ON DIP"; "SHORT BUILDUP" -> "SELL / SHORT"; else -> "NEUTRAL" }
    val lotSize = when (sym) { "NIFTY" -> 25; "BANKNIFTY" -> 15; "FINNIFTY" -> 25; "RELIANCE" -> 250; "TCS" -> 175; "INFY" -> 400; "HDFCBANK" -> 550; "TATAMOTORS" -> 1425; else -> 500 }
    val oiShares = (hash % 850000 + 120000).toLong() * lotSize
    val oiChangePct = Random.nextDouble(-8.0, 18.0)
    val volumeLots = (hash % 45000 + 5000).toLong()
    val vwap = futurePrice * (1.0 + Random.nextDouble(-0.008, 0.008))
    val rsi14 = 42.0 + (hash % 35)
    val target1 = futurePrice * (if (signal.contains("BUY")) 1.018 else 0.982)
    val target2 = futurePrice * (if (signal.contains("BUY")) 1.035 else 0.965)
    val stopLoss = futurePrice * (if (signal.contains("BUY")) 0.991 else 1.009)
    return FuturesContractQuote(sym, name, sector, baseSpot, futurePrice, basis, basisPct, rollCostAnnualPct, oiShares, oiChangePct, volumeLots, buildupType, vwap, rsi14, signal, target1, target2, stopLoss, lotSize)
}

private fun parseLedgerEntry(raw: String): FuturesLedgerEntry? {
    return try {
        val parts = raw.split("|")
        FuturesLedgerEntry(
            id = parts[0],
            timestamp = parts[1],
            symbol = parts[2],
            actionType = parts[3],
            entryPrice = parts[4].toDouble(),
            exitPrice = parts[5].toDouble(),
            target1 = parts[6].toDouble(),
            target2 = parts[7].toDouble(),
            stopLoss = parts[8].toDouble(),
            lotSize = parts[9].toInt(),
            lots = parts[10].toInt(),
            status = parts[11],
            pnl = parts[12].toDouble(),
            roiPct = parts[13].toDouble(),
            exitReason = parts.getOrElse(14) { "" },
            isHeroZero = parts.getOrElse(15) { "false" } == "true"
        )
    } catch (e: Exception) { null }
}

private fun generateDemoLedger(): List<FuturesLedgerEntry> {
    val sdf = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
    return listOf(
        FuturesLedgerEntry("1", sdf.format(java.util.Date()), "NIFTY", "LONG", 24820.0, 25050.0, 25050.0, 25200.0, 24700.0, 25, 1, "CLOSED_WIN", 5750.0, 23.1, "Target 1 Hit", false),
        FuturesLedgerEntry("2", sdf.format(java.util.Date()), "BANKNIFTY", "SHORT", 51680.0, 51200.0, 51200.0, 50900.0, 51900.0, 15, 1, "CLOSED_WIN", 7200.0, 28.4, "Target 1 Hit", false),
        FuturesLedgerEntry("3", sdf.format(java.util.Date()), "RELIANCE", "LONG", 2940.0, 2895.0, 2993.0, 3042.0, 2895.0, 250, 1, "CLOSED_SL", -11250.0, -38.3, "Stop Loss Hit", false),
        FuturesLedgerEntry("4", sdf.format(java.util.Date()), "NIFTY", "LONG", 24600.0, 0.0, 24900.0, 25100.0, 24400.0, 25, 1, "OPEN", 0.0, 0.0, "", false),
        FuturesLedgerEntry("5", sdf.format(java.util.Date()), "TATAMOTORS", "LONG", 975.0, 1052.0, 1040.0, 1085.0, 955.0, 1425, 1, "CLOSED_WIN", 109725.0, 394.8, "Target 2 Hit", true)
    )
}
