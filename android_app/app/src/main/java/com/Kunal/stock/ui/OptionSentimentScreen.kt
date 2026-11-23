package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Kunal.stock.data.*
import com.Kunal.stock.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSentimentScreen(
    viewModel: OptionSentimentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val liveQuote by viewModel.liveQuote.collectAsState()
    val isTriggering by viewModel.isTriggering.collectAsState()
    val isRefreshing = isTriggering || scanStatus.isScanning
    var selectedOptionTab by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableLongStateOf(0L) }
    var quoteReceivedAt by remember { mutableLongStateOf(0L) }
    val liveChartTicks = remember(selectedSymbol) { mutableStateListOf<com.Kunal.stock.data.ChartPoint>() }
    LaunchedEffect(Unit) { while (true) { delay(1000); clockTick++ } }
    LaunchedEffect(liveQuote?.tickAtEpoch, liveQuote?.updatedAt) { quoteReceivedAt = clockTick }
    LaunchedEffect(liveQuote?.tickAtEpoch) {
        val quote = liveQuote
        if (quote != null && quote.spotPrice > 0 && quote.tickAtEpoch > 0) {
            liveChartTicks.add(com.Kunal.stock.data.ChartPoint((quote.tickAtEpoch * 1000).toLong(), quote.spotPrice))
            while (liveChartTicks.size > 10000) liveChartTicks.removeAt(0)
        }
    }
    // Yahoo Finance live fallback when Firestore quote is stale
    var yahooSpot by remember { mutableStateOf(0.0) }
    var yahooSpotUpdatedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedSymbol) {
        yahooSpot = 0.0
        yahooSpotUpdatedAt = 0L
        while (true) {
            val price = LivePriceFetcher.fetchPrice(selectedSymbol)
            if (price != null && price.isFinite() && price > 0.0) {
                yahooSpot = price
                yahooSpotUpdatedAt = System.currentTimeMillis()
            }
            delay(10_000)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "K-Quant Options Pro",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Limited Usages - SmartAPI Quant Terminal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    selectedSymbol,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        // --- 1. Symbol Switcher Chips + Refresh Button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("NIFTY", "BANKNIFTY", "SENSEX", "RELIANCE", "HDFCBANK", "ICICIBANK", "INFY", "TCS", "SBIN").forEach { sym ->
                    val isSelected = selectedSymbol == sym
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSymbol(sym) },
                        label = {
                            Text(
                                text = sym,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Refresh button
            FilledTonalIconButton(
                onClick = {
                    if (!isRefreshing) {
                        viewModel.triggerOptionRefresh { success, err ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "✅ Option chain refresh triggered!" else "❌ Failed: $err"
                                )
                            }
                        }
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isRefreshing) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh option chain",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        TabRow(selectedTabIndex = selectedOptionTab) {
            listOf("Overview", "Option chain", "Deep dive").forEachIndexed { index, title ->
                Tab(selected = selectedOptionTab == index, onClick = { selectedOptionTab = index }, text = { Text(title) })
            }
        }
        // Status label below chips
        if (scanStatus.lastScanTime.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(7.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRefreshing) "Updating option chain..." else "Updated ${scanStatus.lastScanTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (!isRefreshing) {
                    viewModel.triggerOptionRefresh { success, err ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Option analysis started. Live data will appear after the service completes."
                                else "Could not start option analysis: $err"
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRefreshing,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRefreshing) "Option analysis starting..." else "Start Option Analysis")
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Content based on State ---
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Streaming live option chain...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is UiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.setSymbol(selectedSymbol) }) {
                            Text("Retry Connection")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val quote = liveQuote
                val quoteAge = if (quote != null && quote.tickAtEpoch > 0) {
                    quote.tickAgeSeconds + (clockTick - quoteReceivedAt).coerceAtLeast(0)
                } else Long.MAX_VALUE
                val quoteIsFresh = quote != null && quoteAge <= 45
                val yahooSpotIsFresh = yahooSpot > 0.0 &&
                    System.currentTimeMillis() - yahooSpotUpdatedAt <= 90_000L
                val displaySpot = when {
                    quoteIsFresh -> quote?.spotPrice ?: state.data.spotPrice
                    yahooSpotIsFresh -> yahooSpot
                    else -> state.data.spotPrice
                }
                val baseLiveData = state.data.copy(
                    spotPrice = displaySpot,
                    spotHistory = (state.data.spotHistory + (quote?.spotHistory ?: emptyList()) + liveChartTicks)
                        .distinctBy { it.time }.sortedBy { it.time }.takeLast(10000),
                    marketStatusStr = when {
                        quoteIsFresh -> "ANGEL ONE LIVE · ${quoteAge}s · ${quote?.updatedAt}"
                        yahooSpotIsFresh -> "YAHOO DISPLAY ONLY · ₹${String.format("%,.2f", yahooSpot)} · Entry requires Angel One live ticks"
                        !quote?.statusMessage.isNullOrBlank() -> "LIVE FEED ISSUE · ${quote?.statusMessage}"
                        state.data.chainUpdatedAt.isNotBlank() -> "OPTION CHAIN SPOT · Updated ${state.data.chainUpdatedAt} · Live quote unavailable"
                        quote == null -> "LIVE QUOTE NOT RECEIVED · Check Angel One feed"
                        else -> "STALE QUOTE · Last tick ${quoteAge}s ago · ${quote.updatedAt}"
                    },
                    rapidActivity = quote?.rapidActivity ?: false,
                    rapidActivityNote = quote?.activityNote ?: "No rapid activity data",
                    liveTickRate10s = quote?.tickRate10s ?: 0.0,
                    liveMove10sPct = quote?.move10sPct ?: 0.0,
                    activityEvents = quote?.activityEvents ?: emptyList()
                )
                val chainIsFresh = isFreshOptionChainTimestamp(
                    parseOptionChainUpdatedAt(baseLiveData.chainUpdatedAt)
                )
                val consensus = OptionSignalConsensus.evaluate(
                    baseLiveData,
                    spotQuoteFresh = quoteIsFresh,
                    optionChainFresh = chainIsFresh
                )
                val confirmedSignal = OptionSignalConsensus.buildLiveSignal(baseLiveData, consensus)
                val liveData = baseLiveData.copy(
                    finalSignal = consensus.trendLabel,
                    combinedSentimentRead = if (consensus.tradeEligible) {
                        "Price, structure, support/resistance and option-chain flow are aligned."
                    } else {
                        consensus.blocker
                    },
                    tradeAction = confirmedSignal.action,
                    tradeTrigger = if (consensus.tradeEligible) {
                        "Conditional setup only; enter only while spot, chain and structure remain aligned."
                    } else {
                        consensus.blocker
                    },
                    tradeNote = consensus.blocker,
                    deepDirection = when (confirmedSignal.optionType) {
                        "CE" -> "CALL WATCH"
                        "PE" -> "PUT WATCH"
                        else -> "WAIT"
                    },
                    deepEntryCondition = consensus.blocker.ifBlank {
                        "Reconfirm the live underlying and option-chain evidence before entry."
                    },
                    deepTargetSpot = confirmedSignal.targetSpot,
                    deepInvalidationSpot = confirmedSignal.stopLossSpot,
                    candidateContract = confirmedSignal.recommendedContract
                        .takeIf { confirmedSignal.action != "WAIT" } ?: "WAIT",
                    candidatePremium = confirmedSignal.estimatedPremium,
                    analysisConfidence = when {
                        consensus.tradeEligible && consensus.evidenceStrength >= 55 -> "High"
                        consensus.tradeEligible -> "Moderate"
                        else -> "Low"
                    },
                    analysisSummary = consensus.blocker.ifBlank {
                        "Consensus uses live price momentum, chart/trendline structure, support/resistance and option-chain flow."
                    },
                    liveTradeSignal = confirmedSignal,
                    directionalConsensus = consensus
                )
                when (selectedOptionTab) {
                    1 -> OptionChainTab(liveData)
                    2 -> OptionDeepDiveTab(liveData, quoteIsFresh)
                    else -> OptionSentimentContent(liveData, quoteIsFresh)
                }
            }
        }
    }
}
}

@Composable
private fun OptionChainTab(data: OptionSentimentData) {
    val visibleStrikes = remember(data.optionChain, data.spotPrice) {
        val sorted = data.optionChain.sortedBy { it.strike }
        val atmIndex = sorted.indices.minByOrNull { index -> kotlin.math.abs(sorted[index].strike - data.spotPrice) }
        if (atmIndex == null) emptyList() else sorted.subList(
            (atmIndex - 6).coerceAtLeast(0),
            (atmIndex + 7).coerceAtMost(sorted.size)
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ATM strike window", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${data.symbol} · ${visibleStrikes.size} nearby strikes · Expiry ${data.chainExpiry.ifBlank { "current" }} · Updated ${data.chainUpdatedAt.ifBlank { "--" }}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("ATM ${String.format("%,.0f", data.spotPrice)} · 6 strikes above and 6 below when available", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("Session volume and OI update with the option-chain refresh; spot quote is live separately.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.optionChain.isEmpty()) {
            Text("Waiting for a fresh option-chain snapshot. Run Option Analysis while the market is open.")
        } else {
            val signalStrike = data.liveTradeSignal.strike
            val isSignalCall = data.liveTradeSignal.action.contains("CALL", ignoreCase = true) || data.liveTradeSignal.optionType.equals("CE", ignoreCase = true)
            val isSignalPut = data.liveTradeSignal.action.contains("PUT", ignoreCase = true) || data.liveTradeSignal.optionType.equals("PE", ignoreCase = true)

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    OptionChainRowHeader()
                    val atmStrikeIndex = visibleStrikes.indices.minByOrNull { i -> kotlin.math.abs(visibleStrikes[i].strike - data.spotPrice) } ?: -1
                    visibleStrikes.forEachIndexed { index, strike ->
                        // ATM separator row — inserted BEFORE the ATM strike row
                        if (index == atmStrikeIndex) {
                            Row(
                                modifier = Modifier
                                    .background(ElectricBlue.copy(alpha = 0.22f))
                                    .padding(vertical = 5.dp)
                                    .fillMaxWidth(0.85f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(8.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "◀ ATM  ₹${String.format("%,.0f", data.spotPrice)}  ▶",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElectricBlue
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(8.dp))
                            }
                        }
                        val isRecommended = signalStrike > 0 && kotlin.math.abs(strike.strike - signalStrike) < 0.5
                        OptionChainRow(strike, index, isRecommended = isRecommended, isSignalCall = isSignalCall, isSignalPut = isSignalPut)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionChainRowHeader() {
    Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 7.dp)) {
        listOf("Strike", "Call LTP", "Call ΔOI", "Call OI", "Call Vol", "Put Vol", "Put OI", "Put ΔOI", "Put LTP", "OI read").forEach {
            Text(it, Modifier.width(82.dp).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OptionChainRow(
    row: OptionStrike,
    index: Int,
    isRecommended: Boolean = false,
    isSignalCall: Boolean = false,
    isSignalPut: Boolean = false
) {
    val rowBg = when {
        isRecommended && isSignalCall -> BullishGreen.copy(alpha = 0.18f)
        isRecommended && isSignalPut -> BearishRed.copy(alpha = 0.18f)
        isRecommended -> ElectricBlue.copy(alpha = 0.18f)
        index % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.background
    }
    Row(
        Modifier.background(rowBg)
            .padding(vertical = 7.dp)
    ) {
        val values = listOf(
            if (isRecommended) "${String.format("%,.0f", row.strike)} ★" else "${String.format("%,.0f", row.strike)}",
            "₹${String.format("%.2f", row.callLtp)}",
            formatOi(row.callOiChange), formatOi(row.callOi), formatOi(row.callVolume),
            formatOi(row.putVolume), formatOi(row.putOi), formatOi(row.putOiChange),
            "₹${String.format("%.2f", row.putLtp)}", row.oiSide
        )
        values.forEachIndexed { cell, value ->
            val textColor = when {
                cell == 0 && isRecommended -> if (isSignalCall) BullishGreen else BearishRed
                cell == 1 && isRecommended && isSignalCall -> BullishGreen
                cell == 8 && isRecommended && isSignalPut -> BearishRed
                cell == 2 -> if (row.callOiChange >= 0) BearishRed else BullishGreen
                cell == 7 -> if (row.putOiChange >= 0) BullishGreen else BearishRed
                else -> MaterialTheme.colorScheme.onSurface
            }
            val textWeight = if (cell == 0 || (cell == 1 && isRecommended && isSignalCall) || (cell == 8 && isRecommended && isSignalPut)) FontWeight.ExtraBold else FontWeight.Normal
            Text(
                value,
                Modifier.width(82.dp).padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = textWeight,
                color = textColor
            )
        }
    }
}

@Composable
private fun OptionDeepDiveTab(data: OptionSentimentData, quoteIsFresh: Boolean) {
    val callVolume = data.optionChain.sumOf { it.callVolume }
    val putVolume = data.optionChain.sumOf { it.putVolume }
    val volumeLeaders = data.optionChain.sortedByDescending { it.callVolume + it.putVolume }.take(3)
    val oiLeaders = data.optionChain.sortedByDescending { it.totalOi }.take(3)
    val directionColor = when (data.deepDirection) {
        "CALL WATCH" -> BullishGreen
        "PUT WATCH" -> BearishRed
        else -> GoldWarning
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Market direction · conditional setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(data.directionalConsensus.trendLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = directionColor)
                if (data.directionalConsensus.earlyReversalWatch != "NONE") {
                    Text(
                        data.directionalConsensus.earlyReversalWatch,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldWarning
                    )
                }
                Text(
                    "Composite ${String.format(Locale.US, "%+.2f", data.directionalConsensus.compositeScore)} · " +
                        "${data.directionalConsensus.evidenceStrength}/100 evidence alignment (not a probability)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(data.analysisSummary.ifBlank { "Waiting for price and option-chain evidence." }, style = MaterialTheme.typography.bodyMedium)
                if (data.deepEntryCondition.isNotBlank()) {
                    Text(
                        data.deepEntryCondition,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (data.directionalConsensus.tradeEligible) MaterialTheme.colorScheme.onSurfaceVariant else GoldWarning
                    )
                }
                if (data.directionalConsensus.tradeEligible && data.candidateContract != "WAIT" && data.candidatePremium > 0) {
                    Text("ATM candidate: ${data.candidateContract} · ₹${String.format("%.2f", data.candidatePremium)}", fontWeight = FontWeight.Bold)
                } else {
                    Text("Entry: WAIT · no confirmed live option setup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (data.directionalConsensus.tradeEligible && data.deepTargetSpot > 0) "Target ₹${String.format("%.0f", data.deepTargetSpot)}" else "Target: WAIT",
                        color = if (data.directionalConsensus.tradeEligible && data.deepTargetSpot > 0) BullishGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        if (data.directionalConsensus.tradeEligible && data.deepInvalidationSpot > 0) "Invalidation ₹${String.format("%.0f", data.deepInvalidationSpot)}" else "Stop / invalidation: WAIT",
                        color = if (data.directionalConsensus.tradeEligible && data.deepInvalidationSpot > 0) BearishRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    "Evidence alignment ${data.directionalConsensus.evidenceStrength}/100 · heuristic, not a calibrated probability",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val diagnostics = OptionAnalysisDiagnosticsCalculator.calculate(
            data = data,
            spotQuoteFresh = quoteIsFresh
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Data quality & chain flow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        diagnostics.qualityRead,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diagnostics.qualityRead == "LIVE INPUTS AVAILABLE") BullishGreen else GoldWarning
                    )
                }
                EvidenceLine(
                    "Spot quote",
                    if (quoteIsFresh) "Live quote · current"
                    else "Stale or unavailable · live signal remains blocked"
                )
                EvidenceLine(
                    "Option chain",
                    when {
                        diagnostics.chainAgeSeconds == null -> "Timestamp missing or invalid"
                        diagnostics.chainFresh -> "Fresh · ${diagnostics.chainAgeSeconds / 60}m ${diagnostics.chainAgeSeconds % 60}s old"
                        else -> "Stale · ${diagnostics.chainAgeSeconds / 60}m ${diagnostics.chainAgeSeconds % 60}s old"
                    }
                )
                EvidenceLine(
                    "Chain coverage",
                    "${diagnostics.nearAtmRows} valid strikes within ±2% of spot · ${diagnostics.validChainRows} valid rows total"
                )
                EvidenceLine(
                    "Price history",
                    "${diagnostics.recentMinuteBuckets} sampled minute buckets in the last 2h · consensus requires at least 6"
                )
                val flow = diagnostics.nearAtmFlow
                EvidenceLine(
                    "Near-ATM ΔOI",
                    "Calls ${formatOi(flow.callOiChange)} · Puts ${formatOi(flow.putOiChange)}"
                )
                EvidenceLine(
                    "Near-ATM volume",
                    "Calls ${formatOi(flow.callVolume)} · Puts ${formatOi(flow.putVolume)}"
                )
                EvidenceLine(
                    "Backend build tags",
                    "Calls: ${flow.callBuildRead} · Puts: ${flow.putBuildRead}"
                )
                Text(
                    "OI, volume and buildup tags describe observed chain changes; they do not identify whether buyers or writers initiated trades. Data availability is not a guarantee of accuracy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Evidence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                EvidenceLine("Spot feed", if (quoteIsFresh) "Live Angel One · ₹${String.format("%,.2f", data.spotPrice)}" else "Stale or unavailable · ₹${String.format("%,.2f", data.spotPrice)}")
                EvidenceLine("Chart pattern", data.chartPattern.ifBlank { "Waiting for price history" })
                EvidenceLine("OI positioning", data.oiDirectionalRead.ifBlank { "Waiting for OI" })
                EvidenceLine("OI change", data.oiDeltas.ifBlank { "Waiting for OI change" })
                EvidenceLine("Volume PCR", if (data.volumePcr > 0) "${String.format("%.2f", data.volumePcr)} · ${data.marketPressure}" else "Waiting for option volume")
                EvidenceLine("Chain volume", "Calls ${formatOi(callVolume)} · Puts ${formatOi(putVolume)}")
                EvidenceLine("Volume lead", when {
                    callVolume > putVolume -> "Call volume leads by ${formatOi(callVolume - putVolume)}"
                    putVolume > callVolume -> "Put volume leads by ${formatOi(putVolume - callVolume)}"
                    else -> "Balanced or awaiting volume"
                })
                EvidenceLine("Top volume strikes", volumeLeaders.joinToString(" · ") { "${String.format("%.0f", it.strike)} (${formatOi(it.callVolume + it.putVolume)})" }.ifBlank { "--" })
                EvidenceLine("Top total OI strikes", oiLeaders.joinToString(" · ") { "${String.format("%.0f", it.strike)} (${formatOi(it.totalOi)})" }.ifBlank { "--" })
                EvidenceLine("Support", data.supportZones.take(3).joinToString(" · ").ifBlank { "--" })
                EvidenceLine("Resistance", data.resistanceZones.take(3).joinToString(" · ").ifBlank { "--" })
                data.directionalConsensus.evidence.forEachIndexed { index, item ->
                    EvidenceLine("Consensus ${index + 1}", item)
                }
                EvidenceLine("Fast tape", if (data.rapidActivity) "ALERT · ${data.liveTickRate10s} updates/s · ${data.liveMove10sPct}% move / 10s" else "${data.rapidActivityNote.ifBlank { "No alert" }} · ${data.liveTickRate10s} updates/s")
                Text("Fast-tape activity is a price/tick-rate proxy; it cannot identify a specific HFT participant.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Recent rapid-activity events", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (data.activityEvents.isEmpty()) {
                    Text("No rapid-activity event recorded in this session.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    data.activityEvents.takeLast(5).asReversed().forEach { event ->
                        EvidenceLine(event.at, "₹${String.format("%,.2f", event.spot)} · ${event.tickRate10s}/s · ${event.move10sPct}% / 10s")
                    }
                }
            }
        }

        // --- NEW QUANT EXPANSION: ADVANCED GREEKS & VOLATILITY DEEP DIVE MATRIX ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantitative Greeks & Volatility Surface", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(color = ElectricBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("CHAIN-DERIVED CONTEXT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                
                val atmStrike = data.optionChain.minByOrNull { kotlin.math.abs(it.strike - data.spotPrice) }
                val maxPainStrike = data.maxPain.takeIf { it > 0.0 && it.isFinite() }
                val callIv = atmStrike?.callIv?.takeIf { it > 0.0 && it.isFinite() }
                val putIv = atmStrike?.putIv?.takeIf { it > 0.0 && it.isFinite() }
                val atmIv = callIv ?: putIv
                val expiryMillis = parseOptionExpiryAtClose(data.chainExpiry)
                val timeToExpiryYears = expiryMillis
                    ?.let { (it - System.currentTimeMillis()).toDouble() / (365.0 * 24 * 60 * 60 * 1000) }
                    ?.takeIf { it > 0.0 }
                val oneStandardDeviationMove = if (data.spotPrice > 0.0 && atmIv != null && timeToExpiryYears != null) {
                    data.spotPrice * (atmIv / 100.0) * kotlin.math.sqrt(timeToExpiryYears)
                } else {
                    null
                }

                EvidenceLine(
                    "Max Pain",
                    maxPainStrike?.let { "₹${String.format("%,.0f", it)} · spot distance ₹${String.format("%+,.0f", it - data.spotPrice)}" }
                        ?: "Unavailable from current chain"
                )
                EvidenceLine("ATM Call / Put IV", "${callIv?.let { String.format("%.1f%%", it) } ?: "--"} / ${putIv?.let { String.format("%.1f%%", it) } ?: "--"}")
                EvidenceLine(
                    "ATM IV skew",
                    if (callIv != null && putIv != null) {
                        "Put − call: ${String.format("%+.2f", putIv - callIv)} volatility points"
                    } else {
                        "Unavailable; both ATM IVs are required"
                    }
                )
                EvidenceLine("Dealer gamma exposure", "Unavailable; max pain distance is not dealer GEX")
                EvidenceLine("Composite Market Regime", data.regimeName.ifBlank { "Unavailable" })
                EvidenceLine(
                    "Expected expiry move (1 SD)",
                    oneStandardDeviationMove?.let { "±₹${String.format("%.2f", it)} · spot × IV × √(DTE/365)" }
                        ?: "Unavailable; requires positive ATM IV and a future ISO expiry date"
                )
                EvidenceLine("Source Greeks", data.greeksSummary.ifBlank { "Unavailable; theta/gamma are not inferred from option premium" })
            }
        }
        LiveCandleChart(
            data.symbol, data.spotPrice, data.spotHistory, data.supportZones,
            data.resistanceZones,
            data.optionChain.sortedBy { kotlin.math.abs(it.strike - data.spotPrice) }
                .take(5).map { it.strike },
            liveTradeSignal = data.liveTradeSignal,
            optionChain = data.optionChain,
            optionExpiry = data.chainExpiry,
            spotQuoteFresh = quoteIsFresh,
            optionChainUpdatedAt = data.chainUpdatedAt
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSentimentContent(data: OptionSentimentData, spotQuoteFresh: Boolean = false) {
    val isBullish = data.finalSignal.contains("Bull", ignoreCase = true)
    val isBearish = data.finalSignal.contains("Bear", ignoreCase = true)

    val signalColor = when {
        isBullish -> BullishGreen
        isBearish -> BearishRed
        else -> NeutralSlate
    }

    val signalBg = when {
        isBullish -> BullishGreenContainer
        isBearish -> BearishRedContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Collapsible section state
    var showAlerts by remember { mutableStateOf(true) }
    var showProbabilityMomentum by remember { mutableStateOf(true) }
    var showHeroZero by remember { mutableStateOf(data.heroZeroAnalysis.isHeroZeroCandidate) }
    var showUnifiedAnalysis by remember { mutableStateOf(false) }
    var selectedAnalysisTab by remember { mutableIntStateOf(0) }
    val analysisTabs = listOf(
        "Quant",
        "AI agents",
        "Signal log",
        "Technical setup",
        "Chart",
        "Evidence",
        "Metrics / zones"
    )
    val analysisPurposes = listOf(
        "Purpose: summarize the live signal context, reported regime/EV, option-chain structure and risk evidence. It is not a historical backtest.",
        "Purpose: show the intended roles of Greeks, volatility, OI and futures analyses. The current agent pipeline uses seeded synthetic values, so it is excluded from live recommendations.",
        "Purpose: retain generated setups and observe later premiums for the same contract/expiry. Observed outcomes are not a validated strategy backtest.",
        "Purpose: combine price trend, chart pattern, momentum and the live consensus gate; WAIT means the evidence is insufficient or conflicting.",
        "Purpose: visualize sampled spot ticks as approximate candles with levels. The feed is not historical exchange OHLC.",
        "Purpose: inspect the underlying price, option-chain OI/volume changes, Greeks and risk flags used as evidence.",
        "Purpose: summarize PCR, max pain, OI barriers and support/resistance. These are context, not standalone trade triggers."
    )
    val showQuantAnalytics = showUnifiedAnalysis && selectedAnalysisTab == 0
    val showAgentPanel = showUnifiedAnalysis && selectedAnalysisTab == 1
    val showSignalLog = showUnifiedAnalysis && selectedAnalysisTab == 2
    val showTechnicalSetup = showUnifiedAnalysis && selectedAnalysisTab == 3
    val showChartOutlook = showUnifiedAnalysis && selectedAnalysisTab == 4
    val showMarketEvidence = showUnifiedAnalysis && selectedAnalysisTab == 5
    val showKeyMetrics = showUnifiedAnalysis && selectedAnalysisTab == 6

    // Archive option contracts only when an actual chain premium is available.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val signalLog = remember { mutableStateListOf<String>() }
    var archivedRecords by remember { mutableStateOf(emptyList<ArchivedOptionSignal>()) }
    var pendingContract by remember { mutableStateOf("") }
    var stableCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("option_signal_log", android.content.Context.MODE_PRIVATE)
        val legacy = (prefs.getString("log", "") ?: "").split("||").filter { it.isNotBlank() }
        val records = OptionSignalArchive.decode(prefs.getString("records_v1", "") ?: "")
        archivedRecords = records
        signalLog.clear()
        signalLog.addAll(records.asReversed().map { it.display() } + legacy.asReversed())
    }
    LaunchedEffect(data.symbol, data.chainUpdatedAt, data.liveTradeSignal, data.optionChain, spotQuoteFresh) {
        val incoming = data.liveTradeSignal.recommendedContract
        if (incoming == pendingContract) stableCount++ else { pendingContract = incoming; stableCount = 1 }
        val sig = data.liveTradeSignal
        val signalSide = optionSide(sig)
        val strike = sig.strike
        val chainTimestamp = parseOptionChainUpdatedAt(data.chainUpdatedAt)
        if (
            stableCount < 2 ||
            !spotQuoteFresh ||
            !isFreshOptionChainTimestamp(chainTimestamp) ||
            incoming.isBlank() ||
            incoming == "NO ACTIVE SIGNAL" ||
            sig.action.contains("WAIT", ignoreCase = true) ||
            signalSide.isBlank() ||
            strike <= 0.0
        ) return@LaunchedEffect

        val chainRow = data.optionChain.minByOrNull { kotlin.math.abs(it.strike - strike) }
            ?.takeIf { kotlin.math.abs(it.strike - strike) < 0.5 }
            ?: return@LaunchedEffect
        val entryPremium = if (signalSide == "CE") chainRow.callLtp else chainRow.putLtp
        if (!entryPremium.isFinite() || entryPremium <= 0.0) return@LaunchedEffect
        val targetPremium = sig.targetPremium.takeIf { it > entryPremium }
            ?: kotlin.math.round(entryPremium * 1.35 * 10.0) / 10.0
        val stopLossPremium = sig.stopLossPremium.takeIf { it > 0.0 && it < entryPremium }
            ?: kotlin.math.round(entryPremium * 0.82 * 10.0) / 10.0
        val candidate = ArchivedOptionSignal(
            createdAtMillis = chainTimestamp ?: return@LaunchedEffect,
            symbol = data.symbol,
            contract = "${data.symbol} ${strikeLabel(strike)} $signalSide",
            optionType = signalSide,
            strike = strike,
            entryPremium = entryPremium,
            targetPremium = targetPremium,
            stopLossPremium = stopLossPremium,
            expiry = data.chainExpiry
        )
        val prefs = ctx.getSharedPreferences("option_signal_log", android.content.Context.MODE_PRIVATE)
        val records = OptionSignalArchive.decode(prefs.getString("records_v1", "") ?: "")
        val updated = OptionSignalArchive.appendIfNew(records, candidate)
        if (updated != records) {
            prefs.edit().putString("records_v1", OptionSignalArchive.encode(updated)).apply()
            archivedRecords = updated
            signalLog.clear()
            val legacy = (prefs.getString("log", "") ?: "")
                .split("||").filter { it.isNotBlank() }.asReversed()
            signalLog.addAll(updated.asReversed().map { it.display() } + legacy)
        }
    }
    LaunchedEffect(data.symbol, data.chainExpiry, data.chainUpdatedAt, data.optionChain, spotQuoteFresh) {
        val observedAt = parseOptionChainUpdatedAt(data.chainUpdatedAt)
            ?: return@LaunchedEffect
        if (
            !spotQuoteFresh ||
            !isFreshOptionChainTimestamp(observedAt) ||
            data.chainExpiry.isBlank() ||
            data.optionChain.isEmpty()
        ) return@LaunchedEffect
        val prefs = ctx.getSharedPreferences("option_signal_log", android.content.Context.MODE_PRIVATE)
        val records = OptionSignalArchive.decode(prefs.getString("records_v1", "") ?: "")
        val updated = records.map { record ->
            if (
                record.outcome != OptionSignalOutcome.OPEN ||
                record.symbol != data.symbol
            ) {
                record
            } else if (
                record.expiry.isNotBlank() &&
                record.expiry != data.chainExpiry &&
                observedAt > record.createdAtMillis
            ) {
                record.copy(
                    outcome = OptionSignalOutcome.UNRESOLVED,
                    resolvedAtMillis = observedAt
                )
            } else {
                val row = data.optionChain.minByOrNull { kotlin.math.abs(it.strike - record.strike) }
                    ?.takeIf { kotlin.math.abs(it.strike - record.strike) < 0.5 }
                val premium = when (record.optionType) {
                    "CE" -> row?.callLtp ?: 0.0
                    "PE" -> row?.putLtp ?: 0.0
                    else -> 0.0
                }
                OptionSignalArchive.observePremium(
                    listOf(record),
                    record.symbol,
                    data.chainExpiry,
                    record.optionType,
                    record.strike,
                    premium,
                    observedAt
                ).single()
            }
        }
        if (updated != records) {
            prefs.edit().putString("records_v1", OptionSignalArchive.encode(updated)).apply()
            archivedRecords = updated
            signalLog.clear()
            val legacy = (prefs.getString("log", "") ?: "")
                .split("||").filter { it.isNotBlank() }.asReversed()
            signalLog.addAll(updated.asReversed().map { it.display() } + legacy)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ═══════════════════════════════════════════════════
        // SECTION 1: PRICE OVERVIEW (always visible)
        // ═══════════════════════════════════════════════════
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, signalColor.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${data.symbol}  ·  SPOT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (data.spotPrice > 0) String.format("₹%,.2f", data.spotPrice) else "₹ --",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (data.priceChangePct.isFinite() && data.priceChangePct != 0.0) {
                            val changeColor = if (data.priceChangePct > 0.0) BullishGreen else BearishRed
                            Text(
                                text = String.format("%+.2f%%  ·  session move", data.priceChangePct),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = changeColor
                            )
                        }
                    }
                    Surface(
                        color = signalBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, signalColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isBullish -> Icons.Default.TrendingUp
                                    isBearish -> Icons.Default.TrendingDown
                                    else -> Icons.Default.HorizontalRule
                                },
                                contentDescription = null,
                                tint = signalColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = data.finalSignal.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = signalColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.combinedSentimentRead,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (data.fetchedAt.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = data.fetchedAt,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (data.marketStatusStr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = data.marketStatusStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverviewMetricTile(
                        label = "OI PCR",
                        value = data.pcrOi.takeIf { it.isFinite() && it > 0.0 }
                            ?.let { String.format("%.2f", it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    OverviewMetricTile(
                        label = "Volume PCR",
                        value = data.pcrVolume.takeIf { it.isFinite() && it > 0.0 }
                            ?.let { String.format("%.2f", it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    OverviewMetricTile(
                        label = "Max pain",
                        value = data.maxPain.takeIf { it.isFinite() && it > 0.0 }
                            ?.let { String.format("%.0f", it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 2: LIVE SIGNAL (always visible)
        // Entry timing & premium levels based on live chain
        // ═══════════════════════════════════════════════════
        DashboardSectionHeader(
            title = "Live trade signal",
            description = "Entry timing & premium levels synced with live option chain",
            isExpanded = true,
            alwaysExpanded = true,
            onToggle = {}
        )
        LiveTradeSignalCard(data)

        // ═══════════════════════════════════════════════════
        // SECTION 3: MARKET ALERTS (collapsible)
        // Real-time institutional activity & momentum bursts
        // ═══════════════════════════════════════════════════
        val hasAlerts = data.hftBurstAlert.isActive || data.bigMomentumAlert.isImminent || data.bigMomentumAlert.title.isNotBlank()
        DashboardSectionHeader(
            title = "Market alerts ${if (hasAlerts) "· ACTIVE" else ""}",
            description = "Real-time HFT burst & big momentum fractal detections",
            isExpanded = showAlerts,
            onToggle = { showAlerts = !showAlerts }
        )
        AnimatedVisibility(visible = showAlerts) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (data.hftBurstAlert.isActive) HftBurstAlertCard(data.hftBurstAlert)
                if (data.bigMomentumAlert.isImminent || data.bigMomentumAlert.title.isNotBlank()) BigMomentumAlertCard(data.bigMomentumAlert)
                if (!hasAlerts) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No active alerts. Monitoring for HFT burst & momentum events.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 4: PROBABILITY & MOMENTUM (collapsible)
        // Multi-horizon directional probabilities & velocity scan
        // ═══════════════════════════════════════════════════
        DashboardSectionHeader(
            title = "Probabilities & momentum",
            description = "Publisher forecast is separate from the live price/chain consensus gate",
            isExpanded = showProbabilityMomentum,
            onToggle = { showProbabilityMomentum = !showProbabilityMomentum }
        )
        AnimatedVisibility(visible = showProbabilityMomentum) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UnifiedDirectionalProbabilityCard(data.multiHorizonProbabilities)
                VelocityMomentumScannerCard(data.liveTickRate10s, data.liveMove10sPct, data.rapidActivity, data.rapidActivityNote)
                FakeBreakoutCard(data.fakeBreakoutAnalysis)
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 5: HERO-ZERO MODULE (collapsible, auto-expanded when candidate)
        // Expiry-day ultra-premium capture strategy setup
        // ═══════════════════════════════════════════════════
        DashboardSectionHeader(
            title = "Hero-zero candidate screening",
            description = "Expiry-day candidate screening with live expiry, quote, contract and premium checks",
            isExpanded = showHeroZero,
            onToggle = { showHeroZero = !showHeroZero }
        )
        AnimatedVisibility(visible = showHeroZero) {
            HeroZeroCard(data.heroZeroAnalysis, data, spotQuoteFresh)
        }

        // All detailed analysis modules share one expandable dashboard section.
        DashboardSectionHeader(
            title = "Unified market analysis",
            description = "Quant · agents · signal history · technical setup · chart · evidence · levels",
            isExpanded = showUnifiedAnalysis,
            onToggle = { showUnifiedAnalysis = !showUnifiedAnalysis }
        )
        AnimatedVisibility(visible = showUnifiedAnalysis) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    analysisTabs.forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedAnalysisTab == index,
                            onClick = { selectedAnalysisTab = index },
                            label = { Text(title, maxLines = 1) }
                        )
                    }
                }
                Text(
                    analysisPurposes[selectedAnalysisTab],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Consensus math: S = 0.34·price + 0.23·structure + 0.19·support/resistance + 0.24·option flow. " +
                        "ER = |net move| / total path; trend slope is weighted by log-price regression R². " +
                        "Expected move = spot × annualized IV × √(DTE/365), only when IV and expiry are valid. " +
                                "Alignment score is not a probability; publisher horizon forecasts are not calibrated in-app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(visible = showQuantAnalytics) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamingStrategyBacktesterCard(data.liveTradeSignal)
                QuantRegimeEvCard(data.regimeName, data.expectedValue, data.liveTradeSignal)
                QuantOptionChainCard(data.quantOptionChain)
                TimeWindowReversalCard(data.timeWindowReversal)
                ProofOfEvidenceCard(data.proofOfEvidence)
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 6B: MULTI-AGENT AI PANEL (collapsible)
        // Greeks / IV Smile / OI agents + Master Supervisor
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showAgentPanel) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Important: the current Greeks, IV, OI and futures agent engine generates seeded illustrative values; " +
                        "they are not computed from this live option chain and must not be used as evidence or a trade signal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldWarning
                )
                val agentResult = remember(data.symbol, data.spotPrice) { runFullAgentPipeline(data.symbol, data.spotPrice) }
                val v8Setups = remember(data.symbol, data.spotPrice) { evaluateV8OptionSetups(data.symbol, data.spotPrice) }

                // ── Option App v8 Setups Card ──────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Option App v8 Setups (Official Specs)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                color = ElectricBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ElectricBlue)
                            ) {
                                Text("Lot: ${getOfficialLotSize(data.symbol)}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                            }
                        }

                        v8Setups.forEach { setup ->
                            val badgeColor = when (setup.mode) {
                                "LIVE" -> BullishGreen
                                "PAPER" -> GoldWarning
                                else -> ElectricBlue
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Text(setup.setupName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                Text(setup.mode, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                                            }
                                            if (setup.isCalmMasked) {
                                                Surface(color = ElectricBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                    Text("CALM", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                                }
                                            }
                                        }
                                    }
                                    Text(setup.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(setup.executionDetails, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Rule: ${setup.stopLossRule}", style = MaterialTheme.typography.labelSmall, color = GoldWarning)
                                }
                            }
                        }
                    }
                }

                agentResult.let { ar ->
                    // Master Supervisor Card
                    val masterColor = when (ar.masterConsensus.finalDirection) {
                        "BULLISH" -> BullishGreen; "BEARISH" -> BearishRed; else -> GoldWarning
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, masterColor.copy(alpha = 0.6f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountTree, null, tint = masterColor, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Master Supervisor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Surface(color = masterColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, masterColor)) {
                                    Text(ar.masterConsensus.finalSignal,
                                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = masterColor)
                                }
                            }
                            if (ar.masterConsensus.trendAlignmentLock) {
                                Surface(color = BearishRed.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                    Text("⚠ TREND LOCK: ",
                                        Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = BearishRed)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricPill("Confidence", "%")
                                MetricPill("Macro", ar.masterConsensus.macroTrend)
                                MetricPill("Risk", ar.masterConsensus.riskParameterStatus)
                            }
                            Text(ar.masterConsensus.crossClusterInsight.take(200),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Options Cluster
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(0.5.dp, ElectricBlue.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Options Cluster — ",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ElectricBlue)
                            listOf(ar.optionsCluster.greeksReport, ar.optionsCluster.ivSmileReport, ar.optionsCluster.openInterestReport).forEach { agent ->
                                val ac = when (agent.signalBias) { "BULLISH" -> BullishGreen; "BEARISH" -> BearishRed; else -> GoldWarning }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(agent.agentName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        Text(agent.outputSummary.take(90), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = ac.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text(" %",
                                            Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ac)
                                    }
                                }
                            }
                        }
                    }
                    // Futures Cluster
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(0.5.dp, GoldWarning.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Futures Cluster — ",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
                            listOf(ar.futuresCluster.longShortReport, ar.futuresCluster.premiumDiscountReport, ar.futuresCluster.deliveryVolumeReport).forEach { agent ->
                                val ac = when (agent.signalBias) { "BULLISH" -> BullishGreen; "BEARISH" -> BearishRed; else -> GoldWarning }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(agent.agentName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        Text(agent.outputSummary.take(90), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = ac.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text(" %",
                                            Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ac)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 6C: SIGNAL LOG & HERO-ZERO ARCHIVE
        // Auto-saved history of every generated signal
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showSignalLog) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Signal log
                if (signalLog.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("No signals logged yet. Signals are auto-saved when a valid live trade signal is generated.",
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Signal Archive — most recent first",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (archivedRecords.isNotEmpty()) {
                        val performance = OptionSignalArchive.performance(archivedRecords)
                        Text(
                            if (performance.targetHitRatePct == null) {
                                "Observed target-hit rate: unavailable · ${performance.unresolved} unresolved"
                            } else {
                                "Observed target-hit rate: ${String.format(Locale.US, "%.1f%%", performance.targetHitRatePct)} " +
                                    "(${performance.targetHits} target / ${performance.resolved} resolved; " +
                                    "${performance.stopHits} stop, ${performance.unresolved} unresolved)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            performance.averageObservedReturnPct?.let {
                                "Mean first-observed premium move: ${String.format(Locale.US, "%+.2f%%", it)} " +
                                    "(n=${performance.observedReturnSamples}, before costs)"
                            } ?: "Mean observed premium move: unavailable for legacy or unresolved records",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Permanent local archive. Outcomes use the first later chain snapshot crossing target/stop for the same contract and expiry; quote gaps can miss the path. Premium moves exclude fees, slippage and execution effects, so this is observational tracking, not a validated backtest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    signalLog.forEach { entry ->
                        val isCall = entry.contains("CALL", ignoreCase = true) || entry.contains("CE")
                        val isPut  = entry.contains("PUT",  ignoreCase = true) || entry.contains("PE")
                        val entryColor = when { isCall -> BullishGreen; isPut -> BearishRed; else -> ElectricBlue }
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, entryColor.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(entry, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Surface(color = entryColor.copy(0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text(if (isCall) "CE" else if (isPut) "PE" else "?",
                                        Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = entryColor)
                                }
                            }
                        }
                    }
                }
                // Hero-Zero Archive (re-reads from prefs)
                val hzPrefs = ctx.getSharedPreferences("hero_zero_prefs", android.content.Context.MODE_PRIVATE)
                val hzRaw = hzPrefs.getString("hero_zero_log", "") ?: ""
                val hzEntries = hzRaw.split("||").filter { it.isNotBlank() }.reversed()
                if (hzEntries.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Text("⭐ Hero-Zero Archive —  saved setups",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
                    hzEntries.take(10).forEach { hz ->
                        Surface(color = GoldWarning.copy(0.06f), shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, GoldWarning.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                            Text(hz, Modifier.padding(9.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 7: TECHNICAL SETUP (collapsible)
        // RSI/MACD confluence, deep analysis & chart pattern
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showTechnicalSetup) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val actionColor = when {
                    data.tradeAction.contains("CALL") -> BullishGreen
                    data.tradeAction.contains("PUT") -> BearishRed
                    else -> ElectricBlue
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = GoldWarning, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Technical Confluence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(color = actionColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text(data.tradeAction, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = actionColor)
                            }
                        }
                        if (data.tradeTrigger.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(data.tradeTrigger, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill("RSI", data.rsi)
                            MetricPill("MACD", data.macd)
                            MetricPill("Trend", data.trendline)
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, if (data.analysisConfidence == "High") BullishGreen.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Deep Option Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Surface(color = if (data.analysisConfidence == "High") BullishGreenContainer else MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)) {
                                Text("${data.analysisConfidence} confidence", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (data.analysisConfidence == "High") BullishGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (data.analysisSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(data.analysisSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        MetricPill("Chart", "${data.chartTrend}: ${data.chartPattern}")
                        Spacer(modifier = Modifier.height(6.dp))
                        MetricPill("Flow", "${data.marketPressure} · Vol PCR ${if (data.volumePcr > 0) String.format("%.2f", data.volumePcr) else "--"}")
                        Spacer(modifier = Modifier.height(6.dp))
                        MetricPill("Greeks", data.greeksSummary)
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 8: CHART & NEXT CANDLE OUTLOOK (collapsible)
        // Live spot chart & next-candle directional prediction
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showChartOutlook) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Auto live candle chart", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Built from synced spot samples; this feed does not provide historical exchange OHLC. Analysis only, not a price prediction.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    LiveCandleChart(
                        data.symbol, data.spotPrice, data.spotHistory, data.supportZones,
                        data.resistanceZones,
                        data.optionChain.sortedBy { kotlin.math.abs(it.strike - data.spotPrice) }.take(5).map { it.strike },
                        liveTradeSignal = data.liveTradeSignal,
                        optionChain = data.optionChain,
                        optionExpiry = data.chainExpiry,
                        spotQuoteFresh = spotQuoteFresh,
                        optionChainUpdatedAt = data.chainUpdatedAt
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("— Support", style = MaterialTheme.typography.labelSmall, color = BullishGreen)
                        Text("- - Resistance", style = MaterialTheme.typography.labelSmall, color = BearishRed)
                        Text("— Price", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Backend next-candle note", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(data.nextCandleOutlook, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // ── v8 Candlestick & Chart Pattern Identification ──────────
                    val detectedPatterns = remember(data.symbol, data.spotPrice) {
                        detectV8ChartPatterns(data.symbol, data.spotPrice)
                    }
                    if (detectedPatterns.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("🎯 Identified Chart & Candlestick Patterns (Part C3)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        Spacer(modifier = Modifier.height(4.dp))
                        detectedPatterns.forEach { pItem ->
                            val pColor = when (pItem.type) {
                                "BULLISH" -> BullishGreen
                                "BEARISH" -> BearishRed
                                else -> GoldWarning
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, pColor.copy(alpha = 0.5f))
                            ) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Text(pItem.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = pColor)
                                        Text("${pItem.confidence}% Conf", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = pColor)
                                    }
                                    Text(pItem.ruleDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Trigger: ₹${String.format("%,.1f", pItem.triggerLevel)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                        Text("SL: ₹${String.format("%,.1f", pItem.stopLoss)}", style = MaterialTheme.typography.labelSmall, color = BearishRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 9: MARKET EVIDENCE (collapsible)
        // OI flow, option chain evidence & Greeks summary
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showMarketEvidence) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionEvidenceCard(data)
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 9B: NSE OI TREND DEEP DIVE (F1-F11)
        // OI context, not a guaranteed directional prediction
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showKeyMetrics) {
            val oi = data.oiTrendReading
            val trendColor = if (oi.trend == "BULLISH") BullishGreen
                else if (oi.trend == "BEARISH") BearishRed
                else NeutralSlate
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, trendColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("NSE OI Trend Deep Dive · F1-F11", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(
                        if (oi.available) "${oi.symbol} · ${oi.exchangeDate} · ${oi.nearestExpiry}"
                        else "Historical NSE F&O reading unavailable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                Text("F11 Trend", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                Text(oi.trend, color = trendColor, fontWeight = FontWeight.Black)
                                Text("Score ${oi.trendScore}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                Text("F3 Futures", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                Text(oi.buildup, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Text(
                        "F1 Futures ${oi.futuresChangePct?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "--"} · " +
                            "F2 Futures OI ${oi.oiChangePct?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "--"}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        "PCR ${oi.pcr?.let { String.format("%.2f", it) } ?: "--"} · " +
                            "Fresh position dPCR ${String.format("%+.2f", oi.dpcr)} · " +
                            "Writer bias ${String.format("%+.2f", oi.writerBias)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        "Put wall ${oi.putWall?.let { String.format("%.0f", it) } ?: "--"} · " +
                            "Call wall ${oi.callWall?.let { String.format("%.0f", it) } ?: "--"} · " +
                            "Position ${String.format("%.0f%%", oi.wallPlace * 100)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        "Max pain ${oi.maxPain?.let { String.format("%.0f", it) } ?: "--"} " +
                            "(${oi.maxPainPct?.let { String.format(Locale.US, "%+.2f%%", it * 100) } ?: "--"})",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        "OI centre ${oi.oiCentre?.let { String.format("%.0f", it) } ?: "--"} " +
                            "(${oi.oiCentrePct?.let { String.format("%+.2f%%", it * 100) } ?: "--"}) · " +
                            "ATM move ${oi.expectedMove?.let { String.format("%.2f", it) } ?: "--"} " +
                            "(${oi.expectedMovePct?.let { String.format("%.2f%%", it * 100) } ?: "--"})",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        oi.testedHitRate.ifBlank { oi.limitation },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════
        // SECTION 10: KEY METRICS (collapsible)
        // PCR, Max Pain, OI Trend & support/resistance zones
        // ═══════════════════════════════════════════════════
        AnimatedVisibility(visible = showKeyMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        title = "PCR (OI)",
                        value = if (data.pcrOi > 0) String.format("%.2f", data.pcrOi) else "--",
                        subtitle = if (data.pcrOi > 1.1) "Bullish Bias" else if (data.pcrOi < 0.9) "Bearish Bias" else "Neutral",
                        valueColor = if (data.pcrOi > 1.1) BullishGreen else if (data.pcrOi < 0.9) BearishRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "PCR (Volume)",
                        value = if (data.pcrVolume > 0) String.format("%.2f", data.pcrVolume) else "--",
                        subtitle = "Volume Flow",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        title = "Max Pain Strike",
                        value = if (data.maxPain > 0) String.format("₹%,.0f", data.maxPain) else "--",
                        subtitle = "Option Seller Target",
                        valueColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "OI Trend",
                        value = data.oiTrend,
                        subtitle = data.oiBuildupOverall.ifEmpty { "Buildup Pace" },
                        valueColor = GoldWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Key Strike Barriers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Resistance: ", style = MaterialTheme.typography.bodySmall, color = BearishRed, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                data.resistanceZones.take(3).forEach { strike -> ZonePill(text = String.format("₹%,.0f", strike), color = BearishRed) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Support: ", style = MaterialTheme.typography.bodySmall, color = BullishGreen, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                data.supportZones.take(3).forEach { strike -> ZonePill(text = String.format("₹%,.0f", strike), color = BullishGreen) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    description: String,
    isExpanded: Boolean,
    alwaysExpanded: Boolean = false,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!alwaysExpanded) Modifier.clickable { onToggle() } else Modifier),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!alwaysExpanded) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OptionEvidenceCard(data: OptionSentimentData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Market evidence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            EvidenceLine("Flow", data.flow.ifBlank { "Waiting for live flow data" })
            EvidenceLine("OI change", data.oiDeltas.ifBlank { "Waiting for OI change data" })
            EvidenceLine(
                "Risk check",
                listOf(data.manipulationRisk, data.manipulationNote).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "No risk flag received" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Price action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            EvidenceLine("Read", data.priceAction.ifBlank { "Waiting for price-action data" })
            if (data.priceClose > 0) EvidenceLine("Close", "₹${String.format("%.2f", data.priceClose)} (${String.format("%+.2f", data.priceChangePct)}%)")
            if (data.sma20 > 0) EvidenceLine("20-session SMA", "₹${String.format("%.2f", data.sma20)}")
            Spacer(modifier = Modifier.height(12.dp))
            Text("ATM Greeks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (data.greekStrike > 0) EvidenceLine("Strike", "₹${String.format("%.0f", data.greekStrike)}")
            EvidenceLine("Call / Put delta", "${String.format("%.3f", data.callDelta)} / ${String.format("%.3f", data.putDelta)}")
            EvidenceLine("Gamma / Vega", "${String.format("%.5f", data.gamma)} / ${String.format("%.3f", data.vega)}")

            if (data.topStrikes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Top strikes by total OI", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Ranked by Call OI + Put OI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.topStrikes.take(5).forEachIndexed { index, strike ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalOi = strike.callOi + strike.putOi
                    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${index + 1}.  ₹${String.format("%,.0f", strike.strike)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Total ${formatOi(totalOi)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val callFraction = strike.callOi.toFloat() / totalOi.coerceAtLeast(1L).toFloat()
                                Box(Modifier.weight(callFraction.coerceIn(0.03f, 0.97f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(BearishRed))
                                Box(Modifier.weight((1f - callFraction).coerceIn(0.03f, 0.97f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(BullishGreen))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CALL  ${formatOi(strike.callOi)}", color = BearishRed, style = MaterialTheme.typography.labelSmall)
                                Text("PUT  ${formatOi(strike.putOi)}", color = BullishGreen, style = MaterialTheme.typography.labelSmall)
                            }
                            listOf(strike.callBuild, strike.putBuild, strike.oiSide).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(5.dp))
}

private fun formatOi(value: Long): String = when {
    value >= 10_000_000 -> String.format("%.2fCr", value / 10_000_000.0)
    value >= 100_000 -> String.format("%.2fL", value / 100_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LiveCandleChart(
    symbol: String,
    spotPrice: Double,
    points: List<com.Kunal.stock.data.ChartPoint>,
    supportZones: List<Double> = emptyList(),
    resistanceZones: List<Double> = emptyList(),
    strikeLevels: List<Double> = emptyList(),
    liveTradeSignal: LiveTradeSignal = LiveTradeSignal(),
    optionChain: List<OptionStrike> = emptyList(),
    optionExpiry: String = "",
    spotQuoteFresh: Boolean = false,
    optionChainUpdatedAt: String = ""
) {
    val resolutions = remember {
        listOf(
            ChartResolution("1D", 86_400_000L), ChartResolution("1h", 3_600_000L),
            ChartResolution("30m", 1_800_000L), ChartResolution("15m", 900_000L),
            ChartResolution("5m", 300_000L), ChartResolution("3m", 180_000L),
            ChartResolution("1m", 60_000L)
        )
    }
    var selectedResolution by remember { mutableStateOf(resolutions[4]) }
    var visibleBarCount by remember(selectedResolution) { mutableIntStateOf(40) }
    var barsFromLatest by remember(selectedResolution) { mutableFloatStateOf(0f) }
    var selectedCandleIndex by remember(selectedResolution) { mutableIntStateOf(-1) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val priceAxisWidthPx = with(density) { 66.dp.toPx() }
    val usablePoints = remember(points) { points.filter { it.time > 0 && it.value.isFinite() && it.value > 0 } }
    val chartPoints = remember(usablePoints, spotPrice) {
        if (usablePoints.isEmpty() && spotPrice > 0) listOf(com.Kunal.stock.data.ChartPoint(System.currentTimeMillis(), spotPrice))
        else usablePoints
    }
    val candlesByResolution = remember(chartPoints) {
        resolutions.associateWith { resolution ->
            chartPoints.asSequence()
                .filter { isNseRegularSessionPoint(it.time) }
                .groupBy { chartBucketStart(it.time, resolution) }
                .toSortedMap()
                .values
                .map { bucket ->
                    val ordered = bucket.sortedBy { it.time }
                    val prices = ordered.map { it.value }
                    CandleBar(
                        prices.first(), prices.maxOrNull() ?: prices.first(),
                        prices.minOrNull() ?: prices.first(), prices.last(), ordered.size.toLong(),
                        ordered.first().time
                    )
                }.takeLast(300)
        }
    }
    val candles = candlesByResolution[selectedResolution].orEmpty()
    val effectiveVisibleBarCount = if (candles.isEmpty()) 0
        else visibleBarCount.coerceIn(minOf(10, candles.size), candles.size)
    val maxBarsFromLatest = (candles.size - effectiveVisibleBarCount).coerceAtLeast(0)
    val effectiveBarsFromLatest = barsFromLatest.toInt().coerceIn(0, maxBarsFromLatest)
    val visibleStart = (candles.size - effectiveVisibleBarCount - effectiveBarsFromLatest).coerceAtLeast(0)
    val visibleCandles = if (effectiveVisibleBarCount == 0) emptyList()
        else candles.subList(visibleStart, visibleStart + effectiveVisibleBarCount)
    val timeframeReads = remember(candlesByResolution) {
        resolutions.map { resolution ->
            OptionChartAnalysis.readTimeframe(
                resolution.label,
                candlesByResolution[resolution].orEmpty().map { it.open to it.close }
            )
        }
    }
    val activeSignal = liveTradeSignal.recommendedContract.isNotBlank() &&
        liveTradeSignal.recommendedContract != "NO ACTIVE SIGNAL" &&
        liveTradeSignal.action.isNotBlank() &&
        !liveTradeSignal.action.contains("WAIT", ignoreCase = true)
    val optionType = when {
        liveTradeSignal.optionType.contains("CE", ignoreCase = true) ||
            liveTradeSignal.optionType.contains("CALL", ignoreCase = true) ||
            liveTradeSignal.recommendedContract.contains("CE", ignoreCase = true) -> "CE"
        liveTradeSignal.optionType.contains("PE", ignoreCase = true) ||
            liveTradeSignal.optionType.contains("PUT", ignoreCase = true) ||
            liveTradeSignal.recommendedContract.contains("PE", ignoreCase = true) -> "PE"
        else -> ""
    }
    val optionStrike = liveTradeSignal.strike.takeIf { it > 0.0 }
    val matchingOption = optionStrike?.let { strike ->
        optionChain.minByOrNull { kotlin.math.abs(it.strike - strike) }
            ?.takeIf { kotlin.math.abs(it.strike - strike) < 0.5 }
    }
    val actualOptionPremium = when (optionType) {
        "CE" -> matchingOption?.callLtp ?: 0.0
        "PE" -> matchingOption?.putLtp ?: 0.0
        else -> 0.0
    }
    val optionChainFresh =
        isFreshOptionChainTimestamp(parseOptionChainUpdatedAt(optionChainUpdatedAt))
    val btstAssessment = OptionChartAnalysis.assessBtst(
        timeframeReads,
        if (activeSignal) optionType else "",
        actualOptionPremium,
        spotQuoteFresh,
        optionChainFresh
    )
    val signalLevels = listOf(
        Triple("Entry", liveTradeSignal.entrySpot, Color(0xFF2962FF)),
        Triple("Target", liveTradeSignal.targetSpot, Color(0xFF089981)),
        Triple("Stop", liveTradeSignal.stopLossSpot, Color(0xFFF23645))
    ).filter { (_, price, _) ->
        price.isFinite() && price > 0.0 && spotPrice > 0.0 &&
            kotlin.math.abs(price - spotPrice) / spotPrice <= 0.05
    }
    val candleLow = visibleCandles.minOfOrNull { it.low } ?: spotPrice
    val candleHigh = visibleCandles.maxOfOrNull { it.high } ?: spotPrice
    val rangeBase = (candleHigh - candleLow).coerceAtLeast(spotPrice * 0.001)
    val padding = (rangeBase * 0.12).coerceAtLeast(spotPrice * 0.001)

    val priceLow = minOf(
        candleLow,
        spotPrice.takeIf { it > 0 } ?: candleLow,
        signalLevels.minOfOrNull { it.second } ?: Double.POSITIVE_INFINITY
    ) - padding
    val priceHigh = maxOf(
        candleHigh,
        spotPrice.takeIf { it > 0 } ?: candleHigh,
        signalLevels.maxOfOrNull { it.second } ?: Double.NEGATIVE_INFINITY
    ) + padding
    val spread = (priceHigh - priceLow).coerceAtLeast(1.0)

    val tvBackground = Color(0xFF131722)
    val tvGridColor = Color(0xFF2A2E39)
    val tvBullColor = Color(0xFF089981)
    val tvBearColor = Color(0xFFF23645)
    val tvBlueColor = Color(0xFF2962FF)

    val visibleStrikes = strikeLevels.distinct().filter { it in priceLow..priceHigh }
    val patternRead = identifyOptionsPricePattern(visibleCandles)
    val inspectedCandle = visibleCandles.getOrNull(selectedCandleIndex) ?: visibleCandles.lastOrNull()
    val changePct = if (visibleCandles.size >= 2) ((visibleCandles.last().close - visibleCandles.first().open) / visibleCandles.first().open) * 100.0 else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Chart header and timeframe controls ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2E39))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(if (spotQuoteFresh) tvBullColor else GoldWarning)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("$symbol · SPOT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(
                                if (spotQuoteFresh) "LIVE · ${patternRead.first}" else "DELAYED · ${patternRead.first}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (patternRead.second == "Bullish") tvBullColor else if (patternRead.second == "Bearish") tvBearColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = if (changePct >= 0) tvBullColor.copy(alpha = 0.14f) else tvBearColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
                            Text("₹${String.format(Locale.US, "%,.2f", spotPrice)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(String.format(Locale.US, "%+.2f%%", changePct), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (changePct >= 0) tvBullColor else tvBearColor)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        resolutions.forEach { resolution ->
                            val isSel = selectedResolution == resolution
                            Surface(
                                modifier = Modifier.clickable { selectedResolution = resolution },
                                color = if (isSel) tvBlueColor.copy(alpha = 0.22f) else Color.Transparent,
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    resolution.label,
                                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color(0xFF75A7FF) else Color(0xFFB2B5BE)
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Zoom", color = Color(0xFF858B98), style = MaterialTheme.typography.labelSmall)
                        TextButton(
                            onClick = {
                                visibleBarCount = (effectiveVisibleBarCount + 8).coerceAtMost(candles.size)
                                barsFromLatest = effectiveBarsFromLatest.toFloat()
                            },
                            enabled = effectiveVisibleBarCount < candles.size,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("−", color = Color.White, fontSize = 18.sp) }
                        Text(
                            if (candles.isEmpty()) "0 bars" else "$effectiveVisibleBarCount bars",
                            color = Color(0xFFB2B5BE),
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(
                            onClick = {
                                visibleBarCount = (effectiveVisibleBarCount - 8).coerceAtLeast(minOf(10, candles.size))
                                barsFromLatest = effectiveBarsFromLatest.toFloat()
                            },
                            enabled = effectiveVisibleBarCount > minOf(10, candles.size),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("+", color = Color.White, fontSize = 18.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                barsFromLatest = 0f
                                selectedCandleIndex = -1
                            },
                            enabled = effectiveBarsFromLatest > 0 || selectedCandleIndex >= 0,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Latest", color = Color(0xFF75A7FF), fontSize = 12.sp) }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            inspectedCandle?.let { formatChartTime(it.time, selectedResolution.label) } ?: "Waiting for samples",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8A8D98)
                        )
                        inspectedCandle?.let { candle ->
                            val candleColor = if (candle.close >= candle.open) tvBullColor else tvBearColor
                            Text("O ${priceLabel(candle.open)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB2B5BE))
                            Text("H ${priceLabel(candle.high)}", style = MaterialTheme.typography.labelSmall, color = tvBullColor)
                            Text("L ${priceLabel(candle.low)}", style = MaterialTheme.typography.labelSmall, color = tvBearColor)
                            Text("C ${priceLabel(candle.close)}", style = MaterialTheme.typography.labelSmall, color = candleColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Candlestick chart and price annotations ---
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tvBackground)
                    .pointerInput(candles, effectiveVisibleBarCount, effectiveBarsFromLatest) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            if (candles.isNotEmpty()) {
                                if (zoomChange.isFinite() && zoomChange != 1f) {
                                    visibleBarCount = (effectiveVisibleBarCount / zoomChange)
                                        .toInt().coerceIn(minOf(10, candles.size), candles.size)
                                }
                                if (pan.x != 0f && effectiveVisibleBarCount > 0) {
                                    val plotWidth = size.width - priceAxisWidthPx
                                    val barStep = plotWidth / effectiveVisibleBarCount
                                    if (barStep > 0f) {
                                        barsFromLatest = (effectiveBarsFromLatest - pan.x / barStep)
                                            .coerceIn(0f, (candles.size - effectiveVisibleBarCount).coerceAtLeast(0).toFloat())
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(candles, effectiveVisibleBarCount, effectiveBarsFromLatest) {
                        detectTapGestures { tap ->
                            val plotWidth = size.width - priceAxisWidthPx
                            if (visibleCandles.isNotEmpty() && plotWidth > 0f && tap.x < plotWidth) {
                                selectedCandleIndex = (tap.x / plotWidth * visibleCandles.size)
                                    .toInt().coerceIn(visibleCandles.indices)
                            }
                        }
                    }
            ) {
                if (candles.isEmpty()) {
                    drawLine(tvGridColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width - priceAxisWidthPx, size.height * 0.55f), 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "Waiting for in-session spot samples",
                        18f,
                        size.height * 0.5f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 12f
                        }
                    )
                    return@Canvas
                }

                val plotRight = size.width - priceAxisWidthPx
                val plotTop = 12f
                val priceHeight = size.height * 0.66f
                val volumeTop = size.height * 0.75f
                val volumeHeight = size.height * 0.17f
                val candleStep = plotRight / visibleCandles.size
                val candleWidth = (candleStep * 0.62f).coerceIn(2f, 14f)
                val axisPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(178, 181, 190)
                    textSize = 10f
                }

                fun y(value: Double) = (plotTop + priceHeight - ((value - priceLow) / spread * priceHeight).toFloat())
                    .coerceIn(plotTop, plotTop + priceHeight)

                for (grid in 0..4) {
                    val lineY = plotTop + priceHeight * grid / 4f
                    drawLine(tvGridColor, androidx.compose.ui.geometry.Offset(0f, lineY), androidx.compose.ui.geometry.Offset(plotRight, lineY), 1f)
                    val axisValue = priceHigh - spread * grid / 4.0
                    drawContext.canvas.nativeCanvas.drawText(
                        priceLabel(axisValue),
                        plotRight + 5f,
                        lineY + axisPaint.textSize * 0.35f,
                        axisPaint
                    )
                }
                drawLine(tvGridColor, androidx.compose.ui.geometry.Offset(plotRight, 0f), androidx.compose.ui.geometry.Offset(plotRight, size.height), 1f)

                supportZones.filter { it in priceLow..priceHigh }.forEach { level ->
                    val yL = y(level)
                    drawRect(tvBullColor.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, yL - 4f), androidx.compose.ui.geometry.Size(plotRight, 8f))
                    drawLine(tvBullColor, androidx.compose.ui.geometry.Offset(0f, yL), androidx.compose.ui.geometry.Offset(plotRight, yL), 1.25f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
                }

                resistanceZones.filter { it in priceLow..priceHigh }.forEach { level ->
                    val yR = y(level)
                    drawRect(tvBearColor.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, yR - 4f), androidx.compose.ui.geometry.Size(plotRight, 8f))
                    drawLine(tvBearColor, androidx.compose.ui.geometry.Offset(0f, yR), androidx.compose.ui.geometry.Offset(plotRight, yR), 1.25f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
                }

                visibleStrikes.forEach { level ->
                    drawLine(tvBlueColor.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(0f, y(level)), androidx.compose.ui.geometry.Offset(plotRight, y(level)), 1.25f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                }

                if (spotPrice in priceLow..priceHigh) {
                    val spotY = y(spotPrice)
                    drawLine(GoldWarning, androidx.compose.ui.geometry.Offset(0f, spotY), androidx.compose.ui.geometry.Offset(plotRight, spotY), 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
                }
                signalLevels.forEach { (label, price, color) ->
                    val levelY = y(price)
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(0f, levelY),
                        androidx.compose.ui.geometry.Offset(plotRight, levelY),
                        1.5f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "$label ₹${priceLabel(price)}",
                        6f,
                        (levelY - 3f).coerceAtLeast(11f),
                        android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            textSize = 10f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }

                val maxTicks = visibleCandles.maxOfOrNull { it.tickVolume }?.coerceAtLeast(1) ?: 1
                visibleCandles.forEachIndexed { index, candle ->
                    val x = (index + 0.5f) * candleStep
                    val color = if (candle.close >= candle.open) tvBullColor else tvBearColor

                    // High-Low Wick Line
                    drawLine(color, androidx.compose.ui.geometry.Offset(x, y(candle.high)), androidx.compose.ui.geometry.Offset(x, y(candle.low)), 1.5f)

                    // Solid Body Fill
                    val top = minOf(y(candle.open), y(candle.close))
                    val bottom = maxOf(y(candle.open), y(candle.close))
                    drawRect(color, androidx.compose.ui.geometry.Offset(x - candleWidth / 2f, top), androidx.compose.ui.geometry.Size(candleWidth, (bottom - top).coerceAtLeast(3f)))

                    // Volume Histogram Bar
                    val barHeight = (candle.tickVolume.toFloat() / maxTicks) * volumeHeight
                    drawRect(color.copy(alpha = 0.40f), androidx.compose.ui.geometry.Offset(x - candleWidth / 2f, volumeTop + volumeHeight - barHeight), androidx.compose.ui.geometry.Size(candleWidth, barHeight.coerceAtLeast(1f)))
                }

                // Volume Separator Line
                drawLine(tvGridColor, androidx.compose.ui.geometry.Offset(0f, volumeTop), androidx.compose.ui.geometry.Offset(plotRight, volumeTop), 1f)

                if (visibleCandles.size >= 2) {
                    val labelIndices = listOf(0, visibleCandles.size / 3, 2 * visibleCandles.size / 3, visibleCandles.lastIndex).distinct()
                    val timePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(138, 141, 152)
                        textSize = 10f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    labelIndices.forEach { index ->
                        drawContext.canvas.nativeCanvas.drawText(
                            formatChartTime(visibleCandles[index].time, selectedResolution.label),
                            (index + 0.5f) * candleStep,
                            size.height - 5f,
                            timePaint
                        )
                    }
                }

                if (selectedCandleIndex in visibleCandles.indices) {
                    val crosshairX = (selectedCandleIndex + 0.5f) * candleStep
                    val crosshairY = y(visibleCandles[selectedCandleIndex].close)
                    val crosshairPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        textSize = 10f
                    }
                    drawLine(
                        Color.LightGray.copy(alpha = 0.65f),
                        androidx.compose.ui.geometry.Offset(crosshairX, plotTop),
                        androidx.compose.ui.geometry.Offset(crosshairX, size.height - 18f),
                        1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
                    )
                    drawLine(
                        Color.LightGray.copy(alpha = 0.65f),
                        androidx.compose.ui.geometry.Offset(0f, crosshairY),
                        androidx.compose.ui.geometry.Offset(plotRight, crosshairY),
                        1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "VOL ${visibleCandles[selectedCandleIndex].tickVolume}",
                        6f,
                        volumeTop + 14f,
                        crosshairPaint
                    )
                }
                if (spotPrice in priceLow..priceHigh) {
                    val spotY = y(spotPrice)
                    val spotPaint = android.graphics.Paint().apply {
                        color = GoldWarning.toArgb()
                        textSize = 9f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawRect(
                        GoldWarning,
                        androidx.compose.ui.geometry.Offset(plotRight + 2f, spotY - 9f),
                        androidx.compose.ui.geometry.Size(priceAxisWidthPx - 4f, 18f)
                    )
                    spotPaint.color = android.graphics.Color.BLACK
                    drawContext.canvas.nativeCanvas.drawText(
                        priceLabel(spotPrice),
                        plotRight + 5f,
                        spotY + 3f,
                        spotPaint
                    )
                }
                if (activeSignal && optionType.isNotBlank() && optionStrike != null) {
                    val chipColor = if (optionType == "CE") tvBullColor else tvBearColor
                    val chipPaint = android.graphics.Paint().apply {
                        color = chipColor.toArgb()
                        textSize = 9f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "$optionStrike $optionType",
                        plotRight + 4f,
                        size.height - 22f,
                        chipPaint
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VOL · sampled ticks", color = Color(0xFF8A8D98), style = MaterialTheme.typography.labelSmall)
            Text("${visibleCandles.size}/${candles.size} bars · pinch to zoom, drag to pan, tap to inspect", color = Color(0xFF8A8D98), style = MaterialTheme.typography.labelSmall)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● Up", color = tvBullColor, style = MaterialTheme.typography.labelSmall)
            Text("● Down", color = tvBearColor, style = MaterialTheme.typography.labelSmall)
            Text("— Support", color = tvBullColor, style = MaterialTheme.typography.labelSmall)
            Text("- - Resistance", color = tvBearColor, style = MaterialTheme.typography.labelSmall)
            Text("- - Strikes", color = tvBlueColor, style = MaterialTheme.typography.labelSmall)
            Text("— Live spot", color = GoldWarning, style = MaterialTheme.typography.labelSmall)
        }

        Text(
            "Candles are reconstructed from spot samples (not exchange OHLC); missing bars are not filled. Weekends and out-of-session samples are excluded; an exchange holiday calendar is not applied.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            timeframeReads.filter { it.timeframe in setOf("1D", "1h", "30m", "15m", "5m") }.forEach { read ->
                val color = when (read.direction) {
                    OptionPriceDirection.BULLISH -> tvBullColor
                    OptionPriceDirection.BEARISH -> tvBearColor
                    OptionPriceDirection.SIDEWAYS, OptionPriceDirection.UNAVAILABLE ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    color = color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(read.timeframe, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (read.direction == OptionPriceDirection.UNAVAILABLE) "Building (${read.candleCount}/3)"
                            else "${read.direction.name} ${String.format(Locale.US, "%+.2f%%", read.changePct)}",
                            color = color,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
            border = BorderStroke(
                1.dp,
                if (btstAssessment.isConditionalSetup) tvBullColor.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (btstAssessment.isConditionalSetup) "BTST: CONDITIONAL WATCH" else "BTST: WAIT",
                    color = if (btstAssessment.isConditionalSetup) tvBullColor else GoldWarning,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(btstAssessment.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (activeSignal && optionType.isNotBlank() && optionStrike != null) {
                    Text(
                        "$symbol ${priceLabel(optionStrike)} $optionType · option LTP ₹${priceLabel(actualOptionPremium)}" +
                            (if (optionExpiry.isNotBlank()) " · $optionExpiry" else "") +
                            (if (optionChainFresh) "" else " · stale chain"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    if (liveTradeSignal.targetPremium > 0.0 || liveTradeSignal.stopLossPremium > 0.0) {
                        Text(
                            "Premium target ₹${priceLabel(liveTradeSignal.targetPremium)} · stop ₹${priceLabel(liveTradeSignal.stopLossPremium)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text("No verified CE/PE contract is available for this setup.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class ChartResolution(val label: String, val millis: Long)
private data class CandleBar(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val tickVolume: Long,
    val time: Long
)

private fun priceLabel(value: Double): String = String.format(Locale.US, "%,.2f", value)

private fun identifyOptionsPricePattern(candles: List<CandleBar>): Triple<String, String, String> {
    if (candles.size < 3) return Triple("Building price action", "Neutral", "Need more completed candles for a pattern read")
    val last = candles.last()
    val prior = candles[candles.lastIndex - 1]
    val range = (last.high - last.low).coerceAtLeast(0.01)
    val body = kotlin.math.abs(last.close - last.open)
    val upperWick = last.high - maxOf(last.open, last.close)
    val lowerWick = minOf(last.open, last.close) - last.low
    val engulfUp = last.close > last.open && prior.close < prior.open && last.open <= prior.close && last.close >= prior.open
    val engulfDown = last.close < last.open && prior.close > prior.open && last.open >= prior.close && last.close <= prior.open
    if (engulfUp) return Triple("Bullish engulfing", "Bullish", "Price action pattern · confirm against support and OI")
    if (engulfDown) return Triple("Bearish engulfing", "Bearish", "Price action pattern · confirm against resistance and OI")
    if (lowerWick >= maxOf(body * 2, range * 0.45) && upperWick <= range * 0.25)
        return Triple("Lower-wick rejection · possible support test", "Bullish", "Price action pattern · not a standalone entry signal")
    if (upperWick >= maxOf(body * 2, range * 0.45) && lowerWick <= range * 0.25)
        return Triple("Upper-wick rejection · possible resistance test", "Bearish", "Price action pattern · not a standalone entry signal")
    val recent = candles.takeLast(4)
    val ranges = recent.map { it.high - it.low }
    if (ranges.zipWithNext().all { (a, b) -> b <= a } && ranges.last() < ranges.first() * 0.72)
        return Triple("Range compression · breakout watch", "Neutral", "Tightening candles; wait for a confirmed break")
    val swing = candles.takeLast(8)
    val tolerance = (swing.maxOf { it.high } - swing.minOf { it.low }).coerceAtLeast(1.0) * 0.02
    val highs = swing.mapIndexedNotNull { index, candle -> index.takeIf { index in 1 until swing.lastIndex && candle.high >= swing[index - 1].high && candle.high >= swing[index + 1].high }?.let { candle.high } }
    val lows = swing.mapIndexedNotNull { index, candle -> index.takeIf { index in 1 until swing.lastIndex && candle.low <= swing[index - 1].low && candle.low <= swing[index + 1].low }?.let { candle.low } }
    if (highs.size >= 2 && kotlin.math.abs(highs.takeLast(2).last() - highs.takeLast(2).first()) <= tolerance)
        return Triple("Double-top area · possible supply", "Bearish", "Image-inspired structure · wait for neckline confirmation")
    if (lows.size >= 2 && kotlin.math.abs(lows.takeLast(2).last() - lows.takeLast(2).first()) <= tolerance)
        return Triple("Double-bottom area · possible demand", "Bullish", "Image-inspired structure · wait for neckline confirmation")
    return Triple("No clear setup · wait for structure", "Neutral", "Checks candles, compression and possible double tops/bottoms; complex QM/flag patterns need swing data")
}

private fun formatChartTime(epochMillis: Long, resolution: String): String {
    val pattern = if (resolution == "1D" || resolution == "1W") "dd MMM" else "HH:mm"
    return SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.format(Date(epochMillis))
}

private fun optionSide(signal: LiveTradeSignal): String = when {
    signal.optionType.contains("CE", ignoreCase = true) ||
        signal.optionType.contains("CALL", ignoreCase = true) -> "CE"
    signal.optionType.contains("PE", ignoreCase = true) ||
        signal.optionType.contains("PUT", ignoreCase = true) -> "PE"
    signal.recommendedContract.contains("CE", ignoreCase = true) -> "CE"
    signal.recommendedContract.contains("PE", ignoreCase = true) -> "PE"
    else -> ""
}

private fun strikeLabel(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

internal fun parseOptionChainUpdatedAt(value: String): Long? {
    if (value.isBlank()) return null
    val position = java.text.ParsePosition(0)
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.parse(value, position) ?: return null
    return timestamp.time.takeIf { position.index == value.length }
}

private fun parseOptionExpiryAtClose(value: String): Long? {
    if (value.isBlank()) return null
    val position = java.text.ParsePosition(0)
    val expiryDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.parse(value, position)?.takeIf { position.index == value.length } ?: return null
    return java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US).run {
        time = expiryDate
        set(java.util.Calendar.HOUR_OF_DAY, 15)
        set(java.util.Calendar.MINUTE, 30)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

private fun isFreshOptionChainTimestamp(timestampMillis: Long?): Boolean {
    timestampMillis ?: return false
    val ageMillis = System.currentTimeMillis() - timestampMillis
    return ageMillis in 0..(6 * 60 * 1000L)
}

private fun chartBucketStart(epochMillis: Long, resolution: ChartResolution): Long {
    val dayMillis = 86_400_000L
    val indiaOffsetMillis = 19_800_000L
    val marketOpenUtcOffset = 13_500_000L // 09:15 Asia/Kolkata
    val localDay = Math.floorDiv(epochMillis + indiaOffsetMillis, dayMillis)
    val dayOpen = localDay * dayMillis + marketOpenUtcOffset
    return when {
        resolution.label == "1W" -> {
            val monday = localDay - Math.floorMod(localDay + 3, 7)
            monday * dayMillis + marketOpenUtcOffset
        }
        resolution.label == "1D" -> dayOpen
        epochMillis < dayOpen -> dayOpen - dayMillis
        else -> dayOpen + (Math.floorDiv(epochMillis - dayOpen, resolution.millis) * resolution.millis)
    }
}

private fun isNseRegularSessionPoint(epochMillis: Long): Boolean {
    val dayMillis = 86_400_000L
    val indiaOffsetMillis = 19_800_000L
    val localMillis = epochMillis + indiaOffsetMillis
    val localDay = Math.floorDiv(localMillis, dayMillis)
    val weekday = Math.floorMod(localDay + 3, 7)
    val minuteOfDay = Math.floorMod(localMillis, dayMillis) / 60_000L
    return weekday in 0..4 && minuteOfDay in (9 * 60 + 15)..(15 * 60 + 30)
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ZonePill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun LiveTradeSignalCard(data: OptionSentimentData) {
    val signal = data.liveTradeSignal
    val signalIsActive = data.directionalConsensus.tradeEligible &&
        signal.recommendedContract.isNotBlank() &&
        signal.recommendedContract != "NO ACTIVE SIGNAL" &&
        signal.action != "WAIT" &&
        signal.strike > 0.0 &&
        signal.estimatedPremium > 0.0
    val isBuyCall = signalIsActive && signal.action == "BUY CALL" && signal.optionType == "CE"
    val isBuyPut = signalIsActive && signal.action == "BUY PUT" && signal.optionType == "PE"
    val isActionable = isBuyCall || isBuyPut

    val badgeColor = when {
        isBuyCall -> BullishGreen
        isBuyPut -> BearishRed
        else -> ElectricBlue
    }

    val containerBg = when {
        isBuyCall -> BullishGreenContainer.copy(alpha = 0.5f)
        isBuyPut -> BearishRedContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // --- REAL-TIME OPTION CHAIN SYNC ENGINE ---
    val allChainStrikes = data.optionChain
    val resolvedStrike = signal.strike.takeIf { isActionable } ?: 0.0

    // Find the exact matching strike row in Option Chain
    val matchedChainRow = if (resolvedStrike > 0 && allChainStrikes.isNotEmpty()) {
        allChainStrikes.find { kotlin.math.abs(it.strike - resolvedStrike) < 0.5 }
    } else null

    // Exact Live LTP from the Option Chain
    val liveChainLtp = when {
        isBuyCall -> matchedChainRow?.callLtp ?: 0.0
        isBuyPut -> matchedChainRow?.putLtp ?: 0.0
        else -> 0.0
    }

    val isLiveChainSynced = isActionable && liveChainLtp > 0.0

    val estEntryPremium = liveChainLtp.takeIf { isLiveChainSynced } ?: 0.0

    val contractType = if (isBuyCall) "CE" else if (isBuyPut) "PE" else ""
    val strikeDisplay = if (matchedChainRow != null) {
        val s = matchedChainRow.strike
        if (s % 1.0 == 0.0) "${s.toLong()}" else String.format("%.1f", s)
    } else if (resolvedStrike > 0) {
        if (resolvedStrike % 1.0 == 0.0) "${resolvedStrike.toLong()}" else String.format("%.1f", resolvedStrike)
    } else ""

    val displayContract = when {
        isActionable && strikeDisplay.isNotBlank() && contractType.isNotBlank() -> "${data.symbol} $strikeDisplay $contractType"
        else -> "WAIT · NO CONFIRMED CONTRACT"
    }

    val targetPrem = signal.targetPremium.takeIf { isActionable } ?: 0.0
    val target2Prem = if (isActionable && targetPrem > estEntryPremium) {
        estEntryPremium + (targetPrem - estEntryPremium) * 1.5
    } else 0.0
    val slPrem = signal.stopLossPremium.takeIf { isActionable } ?: 0.0

    // Live Strike Metrics from Option Chain
    val liveOi = if (isBuyCall) matchedChainRow?.callOi ?: 0L else matchedChainRow?.putOi ?: 0L
    val liveOiChange = if (isBuyCall) matchedChainRow?.callOiChange ?: 0L else matchedChainRow?.putOiChange ?: 0L
    val liveVolume = if (isBuyCall) matchedChainRow?.callVolume ?: 0L else matchedChainRow?.putVolume ?: 0L
    val liveIv = if (isBuyCall) matchedChainRow?.callIv ?: 0.0 else matchedChainRow?.putIv ?: 0.0
    val liveBuild = if (isBuyCall) matchedChainRow?.callBuild.orEmpty() else matchedChainRow?.putBuild.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title, Sync Badge & Action Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSENSUS-GATED LIVE SETUP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Option Chain Sync Indicator
                    Surface(
                        color = if (isLiveChainSynced) BullishGreen.copy(alpha = 0.2f) else GoldWarning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isLiveChainSynced) BullishGreen.copy(alpha = 0.7f) else GoldWarning.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = if (isLiveChainSynced) "● LIVE CHAIN" else "● WAIT GATE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isLiveChainSynced) BullishGreen else GoldWarning
                        )
                    }

                    // Direction Pill
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, badgeColor)
                    ) {
                        Text(
                            text = if (isBuyCall) "BUY CE" else if (isBuyPut) "BUY PE" else "WAIT",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                data.directionalConsensus.trendLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor
            )
            if (data.directionalConsensus.earlyReversalWatch != "NONE") {
                Text(
                    data.directionalConsensus.earlyReversalWatch,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldWarning
                )
            }
            Text(
                "Composite evidence ${String.format(Locale.US, "%+.2f", data.directionalConsensus.compositeScore)} · " +
                    "${data.directionalConsensus.evidenceStrength}/100 alignment score (not a probability)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Score = 0.34×price + 0.23×structure + 0.19×S/R + 0.24×option flow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Price model: ATR-normalized returns + EMA(5/13) + MACD histogram + log-price OLS slope + RSI(7)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (data.marketStatusStr.isNotBlank()) {
                Text(
                    data.marketStatusStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (data.marketStatusStr.startsWith("ANGEL ONE LIVE")) {
                        BullishGreen
                    } else {
                        GoldWarning
                    }
                )
            }
            Text(
                "Reversal = 0.32×momentum acceleration + 0.22×RSI turn + 0.18×MACD + 0.18×S/R + 0.10×chain flow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            data.directionalConsensus.evidence.take(4).forEach { evidence ->
                Text("• $evidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isActionable && data.directionalConsensus.blocker.isNotBlank()) {
                Text(
                    data.directionalConsensus.blocker,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldWarning
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlighted Contract & Live Entry Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = containerBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RECOMMENDED OPTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayContract,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (strikeDisplay.isNotBlank()) {
                            Text(
                                text = "Strike: ₹$strikeDisplay · ${if (isBuyCall) "CALL (CE)" else if (isBuyPut) "PUT (PE)" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isLiveChainSynced) "LIVE CHAIN LTP" else "ENTRY · WAITING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveChainSynced) BullishGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isLiveChainSynced) "₹${String.format("%.2f", estEntryPremium)}" else "WAIT",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLiveChainSynced) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        if (isLiveChainSynced) {
                            Text(
                                text = "Exact strike LTP",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = BullishGreen
                            )
                        } else {
                            Text(
                                "Fresh chain + confirmed setup required",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = GoldWarning
                            )
                        }
                    }
                }
            }

            // Live Strike Option Chain Depth Row
            if (matchedChainRow != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OI: ${formatOi(liveOi)} (${if (liveOiChange >= 0) "+" else ""}${formatOi(liveOiChange)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (liveOiChange >= 0) BullishGreen else BearishRed,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Vol: ${formatOi(liveVolume)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                        if (liveIv > 0) {
                            Text(
                                text = "IV: ${String.format("%.1f", liveIv)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue,
                                fontSize = 11.sp
                            )
                        }
                        if (liveBuild.isNotBlank()) {
                            Text(
                                text = liveBuild,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (liveBuild.contains("Long", ignoreCase = true)) BullishGreen else BearishRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Targets & Stop Loss Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Target 1 Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = BullishGreenContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, BullishGreen.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = BullishGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TARGET 1 · Δ/Γ EST.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BullishGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (targetPrem > 0) "₹${String.format("%.2f", targetPrem)}" else "WAIT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BullishGreen
                        )
                        if (signal.targetSpot > 0) {
                            Text("Spot ₹${String.format("%.0f", signal.targetSpot)}", style = MaterialTheme.typography.labelSmall, color = BullishGreen.copy(alpha = 0.8f))
                        } else {
                            Text("Needs confirmed setup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Target 2 Runner Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = BullishGreenContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, BullishGreen.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = BullishGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TARGET 2 · 1.5R", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BullishGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (target2Prem > 0) "₹${String.format("%.2f", target2Prem)}" else "WAIT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BullishGreen
                        )
                        Text(if (target2Prem > 0) "Runner Exit" else "Needs confirmed setup", style = MaterialTheme.typography.labelSmall, color = BullishGreen.copy(alpha = 0.8f))
                    }
                }

                // Stop Loss Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = BearishRedContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, BearishRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SouthEast, contentDescription = null, tint = BearishRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP · Δ/Γ EST.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BearishRed)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (slPrem > 0) "₹${String.format("%.2f", slPrem)}" else "WAIT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BearishRed
                        )
                        if (signal.stopLossSpot > 0) {
                            Text("Spot ₹${String.format("%.0f", signal.stopLossSpot)}", style = MaterialTheme.typography.labelSmall, color = BearishRed.copy(alpha = 0.8f))
                        } else {
                            Text("Needs confirmed setup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // R:R & Confidence row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill("Risk : Reward", signal.riskRewardRatio.ifBlank { "N/A" })
                MetricPill("Evidence score", if (isActionable) "${signal.confidenceScore}/100" else "WAIT")
                MetricPill("Spot Ref", "₹${String.format("%,.0f", if (signal.entrySpot > 0) signal.entrySpot else data.spotPrice)}")
            }
            Text(
                if (isActionable) {
                    "Premium target/stop use a delta-gamma approximation from the selected chain premium; IV changes, theta, slippage and expiry effects are not modeled."
                } else {
                    "Entry, target and stop are intentionally withheld until fresh price/chain evidence confirms a setup. ${data.directionalConsensus.blocker}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProofOfEvidenceCard(proof: ProofOfEvidence) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "3-Pillar Proof of Evidence",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Why we are taking this trade now",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle evidence"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EvidencePillarItem(
                        icon = Icons.Default.AccountBalance,
                        title = "1. Open Interest & Writer Positioning",
                        description = proof.oiFlowProof.ifBlank { "Heavy option writing concentration establishing defined support and resistance boundaries." },
                        tint = BullishGreen
                    )

                    EvidencePillarItem(
                        icon = Icons.Default.ShowChart,
                        title = "2. Price Action & Structural Trend",
                        description = proof.priceActionProof.ifBlank { "Multi-timeframe technical confluence confirming directional momentum." },
                        tint = ElectricBlue
                    )

                    EvidencePillarItem(
                        icon = Icons.Default.Bolt,
                        title = "3. Gamma / Delta Dealer Flow",
                        description = proof.gammaDeltaProof.ifBlank { "Market makers dynamically delta-hedging option volume expansion in favor of trend." },
                        tint = GoldWarning
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidencePillarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tint: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BigMomentumAlertCard(alert: BigMomentumAlert) {
    if (!alert.isImminent && alert.title.isBlank()) return

    val isAlertActive = alert.isImminent || alert.similarityPct >= 70
    val cardBorder = if (isAlertActive) GoldWarning else MaterialTheme.colorScheme.outline
    val headerBg = if (isAlertActive) Color(0xFF332600) else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationImportant,
                        contentDescription = null,
                        tint = GoldWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BIG MOMENTUM ALERT",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldWarning
                    )
                }

                if (alert.similarityPct > 0) {
                    Surface(
                        color = GoldWarning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GoldWarning)
                    ) {
                        Text(
                            text = "${alert.similarityPct}% Fractal Match",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GoldWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.title.ifBlank { "Volatility Squeeze Detected" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (alert.historicalPrecedent.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = headerBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, GoldWarning.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = GoldWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HISTORICAL PRECEDENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.historicalPrecedent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (alert.whyItHappenedHistory.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Why it happened: ${alert.whyItHappenedHistory}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (alert.actionPlan.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = BullishGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Plan: ${alert.actionPlan}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun HftBurstAlertCard(alert: HftBurstAlert) {
    if (!alert.isActive) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, BearishRed)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FlashOn, contentDescription = null, tint = BearishRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.burstType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = BearishRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = BearishRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${alert.surgeMultiplier}x Vol Surge",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BearishRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.recommendedReaction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun TimeWindowReversalCard(reversal: TimeWindowReversal) {
    val scoreColor = when {
        reversal.reversalScore >= 80 -> BullishGreen
        reversal.reversalScore >= 65 -> GoldWarning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Intraday Time & Candle Reversal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = scoreColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "${reversal.reversalScore}% Reversal Probability",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = reversal.timeWindow,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                        Text(
                            text = reversal.reversalType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pattern at Zone: ${reversal.candlestickPattern}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (reversal.commentary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reversal.commentary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FakeBreakoutCard(fake: FakeBreakoutAnalysis) {
    val isTrap = fake.hasTrap || fake.isFakeBreakout || fake.isFakeBreakdown
    val statusColor = when {
        fake.isFakeBreakout -> BearishRed
        fake.isFakeBreakdown -> BullishGreen
        fake.hasTrap -> GoldWarning
        else -> ElectricBlue
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (isTrap) statusColor else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fake Breakout & Trap Detector",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = fake.status.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
            }
            if (fake.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = fake.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill("Upper Wick", "${String.format("%.1f", fake.upperWickRatio * 100)}%")
                MetricPill("Lower Wick", "${String.format("%.1f", fake.lowerWickRatio * 100)}%")
                MetricPill("Vol Ratio", "${String.format("%.2f", fake.volumeRatio)}x")
            }
        }
    }
}

@Composable
fun QuantRegimeEvCard(regimeName: String, expectedValue: Double, signal: LiveTradeSignal) {
    val evColor = when {
        expectedValue > 0 -> BullishGreen
        expectedValue < 0 -> BearishRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val decisionColor = when (signal.action.uppercase()) {
        "BUY CALL", "BUY PE", "BUY_CE", "BUY_PE", "TRADE" -> BullishGreen
        "WAIT" -> GoldWarning
        else -> BearishRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quant Regime & Expected Value (EV / RR)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = decisionColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, decisionColor)
                ) {
                    Text(
                        text = if (signal.action.isBlank()) "WAIT" else signal.action,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = decisionColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Market Regime",
                    value = regimeName.ifBlank { "Unavailable" },
                    subtitle = "Reported by data source",
                    valueColor = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Expected Value (EV)",
                    value = if (expectedValue != 0.0) "${if (expectedValue > 0) "+" else ""}₹${String.format("%.2f", expectedValue)}" else "Unavailable",
                    subtitle = if (expectedValue != 0.0) "Reported expected value" else "Not provided",
                    valueColor = evColor,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill("Risk / Reward", signal.riskRewardRatio.ifBlank { "N/A" })
                MetricPill("Confidence Score", if (signal.confidenceScore > 0) "${signal.confidenceScore}%" else "N/A")
                MetricPill("Slippage & Costs", "Not modeled")
            }
        }
    }
}

@Composable
fun UnifiedDirectionalProbabilityCard(horizon: MultiHorizonProbabilities) {
    val forecasts = PublishedHorizonForecasts.validForecasts(horizon)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = GoldWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PUBLISHER HORIZON FORECAST",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text("UNCALIBRATED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (forecasts.isEmpty()) {
                Text(
                    "Forecast unavailable. The feed did not provide a valid bullish / bearish / sideways distribution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Horizon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Bull", style = MaterialTheme.typography.labelSmall, color = BullishGreen)
                    Text("Bear", style = MaterialTheme.typography.labelSmall, color = BearishRed)
                    Text("Side", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                forecasts.forEach { forecast ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(forecast.horizon, style = MaterialTheme.typography.labelMedium)
                        Text("${String.format(Locale.US, "%.1f", forecast.bullishPct)}%", style = MaterialTheme.typography.labelMedium, color = BullishGreen)
                        Text("${String.format(Locale.US, "%.1f", forecast.bearishPct)}%", style = MaterialTheme.typography.labelMedium, color = BearishRed)
                        Text("${String.format(Locale.US, "%.1f", forecast.sidewaysPct)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Publisher values are shown per horizon; they are not calibrated by this app and are not a chance-of-profit estimate.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VelocityMomentumScannerCard(
    tickRate10s: Double,
    move10sPct: Double,
    rapidActivity: Boolean,
    note: String
) {
    val isVolBurst = rapidActivity || tickRate10s >= 2.0
    val cardBorder = if (isVolBurst) GoldWarning else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isVolBurst) GoldWarning else ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VELOCITY & MOMENTUM SCANNER",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    color = if (isVolBurst) GoldWarning.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isVolBurst) "BURST ACTIVE" else "STEADY FLOW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isVolBurst) GoldWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Price Velocity",
                    value = "${String.format("%+.2f", move10sPct)}%/10s",
                    subtitle = "Rate of Price Change",
                    valueColor = if (move10sPct >= 0) BullishGreen else BearishRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Tick Velocity",
                    value = "${String.format("%.1f", tickRate10s)} ticks/s",
                    subtitle = "Orderflow Frequency",
                    valueColor = if (tickRate10s >= 2.0) GoldWarning else ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StreamingStrategyBacktesterCard(signal: LiveTradeSignal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE SIGNAL CONTEXT (NOT A BACKTEST)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Surface(
                    color = BullishGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "MARKET DATA",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Current signal: ${signal.action} (${signal.recommendedContract}). " +
                    "No verified historical options P&L or latency statistics are available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HeroZeroCard(
    hero: HeroZeroAnalysis,
    data: OptionSentimentData? = null,
    spotQuoteFresh: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val review = remember(hero, data, spotQuoteFresh) {
        evaluateHeroZeroReview(hero, data, spotQuoteFresh)
    }
    val isCandidate = hero.isHeroZeroCandidate && review.ready
    val badgeColor = if (isCandidate) BullishGreen else NeutralSlate
    var showHistory by remember { mutableStateOf(false) }

    // Load saved hero-zero history from SharedPreferences
    val savedEntries = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("hero_zero_prefs", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("hero_zero_log", "") ?: ""
        if (raw.isNotBlank()) {
            savedEntries.clear()
            savedEntries.addAll(raw.split("||").filter { it.isNotBlank() }.reversed())
        }
    }

    // Auto-save when a hero-zero candidate fires
    LaunchedEffect(
        hero.isHeroZeroCandidate,
        hero.contract,
        hero.entryPremium,
        hero.target1Premium,
        hero.stopLossPremium,
        data?.symbol,
        data?.spotPrice,
        data?.chainExpiry,
        data?.chainUpdatedAt,
        data?.optionChain,
        spotQuoteFresh
    ) {
        if (isCandidate && data != null && review.livePremium > 0.0) {
            val prefs = context.getSharedPreferences("hero_zero_prefs", android.content.Context.MODE_PRIVATE)
            val existing = prefs.getString("hero_zero_log", "") ?: ""
            val sdf = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
            val newEntry = "${sdf.format(java.util.Date())} | ${data.chainExpiry} | ${hero.contract} | live chain ₹${String.format("%.2f", review.livePremium)} | engine reference ₹${String.format("%.2f", hero.entryPremium)} | candidate only"
            val entries = existing.split("||").filter { it.isNotBlank() }.toMutableList()
            if (entries.none { it.contains(data.chainExpiry) && it.contains(hero.contract) }) {
                entries.add(newEntry)
                prefs.edit().putString("hero_zero_log", entries.joinToString("||")).apply()
                savedEntries.clear()
                savedEntries.addAll(entries.reversed())
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (isCandidate) BullishGreen else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = GoldWarning, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Expiry-Day 'Hero Zero' Module", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = if (isCandidate) "CANDIDATE · CHECKED" else "WAIT / WATCH",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isCandidate) {
                Text(hero.reason.ifBlank { "Engine candidate passed the live-data checks." },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hero.contract.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("CANDIDATE CONTRACT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(hero.contract, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LIVE CHAIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${String.format("%.2f", review.livePremium)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BullishGreen)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hero.expectedHoldingTime.isNotBlank()) {
                            Surface(color = ElectricBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text("Hold: ${hero.expectedHoldingTime}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
                            }
                        }
                        Surface(color = GoldWarning.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text("AUTO-ARCHIVE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldWarning)
                        }
                    }
                }
            } else {
                Text(
                    if (hero.isHeroZeroCandidate) {
                        "Backend candidate is not actionable in this view. Review failed checks below."
                    } else {
                        "Automatically checking the live expiry, quote, contract, premium and timing before archiving a candidate."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (review.checks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    review.checks.forEach { check ->
                        Text("• $check", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Candidate screening only—not a probability, buy signal, or validated options backtest. The full premium can be lost.",
                style = MaterialTheme.typography.labelSmall,
                color = BearishRed
            )

            // History section
            if (savedEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showHistory = !showHistory },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Setups (${savedEntries.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GoldWarning)
                    Icon(
                        if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = GoldWarning, modifier = Modifier.size(16.dp)
                    )
                }
                AnimatedVisibility(visible = showHistory) {
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        savedEntries.take(10).forEach { entry ->
                            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(entry, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (savedEntries.size > 10) {
                            Text("+ ${savedEntries.size - 10} more saved setups", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

internal data class HeroZeroReview(
    val ready: Boolean,
    val livePremium: Double = 0.0,
    val checks: List<String> = emptyList()
)

internal fun evaluateHeroZeroReview(
    hero: HeroZeroAnalysis,
    data: OptionSentimentData?,
    spotQuoteFresh: Boolean,
    nowMillis: Long = System.currentTimeMillis()
): HeroZeroReview {
    val checks = mutableListOf<String>()
    if (!hero.isHeroZeroCandidate) checks += "The backend has not detected a candidate."
    if (data == null) return HeroZeroReview(false, checks = checks + "Live option data is unavailable.")
    if (!spotQuoteFresh) checks += "Spot quote is stale or missing."

    val chainTimestamp = parseOptionChainUpdatedAt(data.chainUpdatedAt)
    val chainAge = chainTimestamp?.let { nowMillis - it }
    if (chainAge == null || chainAge !in 0..360_000L) checks += "Option-chain data is stale or missing."

    val india = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    val now = java.util.Calendar.getInstance(india).apply { timeInMillis = nowMillis }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = india }.format(now.time)
    if (data.chainExpiry != today) checks += "The selected option expiry is not today."
    val hhmm = now.get(java.util.Calendar.HOUR_OF_DAY) * 100 + now.get(java.util.Calendar.MINUTE)
    if (hhmm !in 1315..1400) checks += "Outside the configured 13:15-14:00 review window."

    val contractMatch = Regex("""^\s*([A-Z]+)\s+([0-9]+(?:\.[0-9]+)?)\s+(CE|PE)\s*$""")
        .matchEntire(hero.contract.uppercase(Locale.US))
    val contractSymbol = contractMatch?.groupValues?.getOrNull(1)
    val strike = contractMatch?.groupValues?.getOrNull(2)?.toDoubleOrNull()
    val side = contractMatch?.groupValues?.getOrNull(3)
    if (contractSymbol != data.symbol.uppercase(Locale.US) || strike == null || !strike.isFinite() || side == null) {
        checks += "Candidate contract does not match the selected symbol or has an invalid format."
    }

    val optionRow = if (strike != null && strike.isFinite()) {
        data.optionChain.firstOrNull { kotlin.math.abs(it.strike - strike) < 0.001 }
    } else {
        null
    }
    val livePremium = when (side) {
        "CE" -> optionRow?.callLtp
        "PE" -> optionRow?.putLtp
        else -> null
    } ?: 0.0
    if (optionRow == null || !livePremium.isFinite() || livePremium <= 0.0) {
        checks += "Candidate contract is not present with a live premium in the current chain."
    } else if (livePremium !in 3.0..50.0) {
        checks += "Live premium is outside the engine's ₹3-₹50 screening range."
    }

    if (!data.spotPrice.isFinite() || data.spotPrice <= 0.0 || strike == null || !strike.isFinite() ||
        kotlin.math.abs(data.spotPrice - strike) / data.spotPrice > 0.015
    ) {
        checks += "Strike is more than 1.5% from the current underlying."
    }
    if (!hero.entryPremium.isFinite() || hero.entryPremium <= 0.0 ||
        !hero.target1Premium.isFinite() || hero.target1Premium <= hero.entryPremium ||
        !hero.stopLossPremium.isFinite() || hero.stopLossPremium <= 0.0 ||
        hero.stopLossPremium >= hero.entryPremium
    ) {
        checks += "Candidate entry, target or stop values are missing or invalid."
    }
    return HeroZeroReview(checks.isEmpty(), livePremium, checks)
}

@Composable
fun QuantOptionChainCard(quant: QuantOptionChainAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quant Option Chain (GEX / Skew / Walls)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    "IV Skew",
                    quant.ivSkewPct?.takeIf { it.isFinite() }?.let { "${String.format("%+.1f", it)}%" } ?: "N/A"
                )
                MetricPill(
                    "Net GEX",
                    quant.netGexMillion?.takeIf { it.isFinite() }?.let { "${String.format("%.2f", it)}M" } ?: "N/A"
                )
                MetricPill("GEX Regime", quant.gexRegime.takeIf { it.isNotBlank() } ?: "Unavailable")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (quant.callWallStrike?.let { it.isFinite() && it > 0.0 } == true) {
                    MetricPill("Call Wall", "₹${String.format("%.0f", quant.callWallStrike)}")
                }
                if (quant.putWallStrike?.let { it.isFinite() && it > 0.0 } == true) {
                    MetricPill("Put Wall", "₹${String.format("%.0f", quant.putWallStrike)}")
                }
                if (quant.maxPainStrike?.let { it.isFinite() && it > 0.0 } == true) {
                    MetricPill("Max Pain", "₹${String.format("%.0f", quant.maxPainStrike)}")
                }
            }
            if (quant.institutionalRead.isNotBlank() && quant.institutionalRead != "Unavailable") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Institutional Read: ${quant.institutionalRead}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
