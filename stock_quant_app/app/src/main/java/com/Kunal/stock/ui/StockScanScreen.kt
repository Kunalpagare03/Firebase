package com.Kunal.stock.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Kunal.stock.data.ScanStatus
import com.Kunal.stock.data.StockCandle
import com.Kunal.stock.data.StockHorizonReturn
import com.Kunal.stock.data.PlaybookScanner
import com.Kunal.stock.data.StockReturnOutcome
import com.Kunal.stock.data.StockReturnHorizon
import com.Kunal.stock.data.StockScanPerformanceRecord
import com.Kunal.stock.data.StockScanPerformanceStore
import com.Kunal.stock.data.StockScanItem
import com.Kunal.stock.data.AiStockDeepDiveAgent
import com.Kunal.stock.data.AiDeepDivePick
import com.Kunal.stock.data.UiState
import com.Kunal.stock.ui.AiDeepDiveHeaderBanner
import com.Kunal.stock.ui.AiDeepDivePickCard
import com.Kunal.stock.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScanScreen(
    viewModel: StockScanViewModel = viewModel()
) {
    val filteredScansState by viewModel.filteredScans.collectAsState()
    val allScansState by viewModel.allScans.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val isTriggering by viewModel.isTriggering.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val performanceStore = remember(context) {
        StockScanPerformanceStore(context.applicationContext)
    }
    var performanceRecords by remember { mutableStateOf<List<StockScanPerformanceRecord>>(emptyList()) }
    var performanceError by remember { mutableStateOf<String?>(null) }
    var refreshingPerformanceId by remember { mutableStateOf<String?>(null) }
    var performanceSearch by rememberSaveable { mutableStateOf("") }
    var selectedStockTab by remember { mutableIntStateOf(0) }
    var selectedStock by remember { mutableStateOf<StockScanItem?>(null) }

    var triggerSnackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(triggerSnackbar) {
        triggerSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            triggerSnackbar = null
        }
    }

    LaunchedEffect(performanceStore, performanceSearch) {
        performanceRecords = withContext(Dispatchers.IO) {
            performanceStore.loadRecords(performanceSearch)
        }
    }
    LaunchedEffect(allScansState) {
        if (allScansState is UiState.Success<*>) {
            val items = (allScansState as UiState.Success<*>).data
                .let { it as? List<*> }
                ?.filterIsInstance<StockScanItem>()
                .orEmpty()
            try {
                performanceRecords = withContext(Dispatchers.IO) {
                    performanceStore.recordSnapshot(items, performanceSearch)
                }
                performanceError = null
            } catch (e: Exception) {
                performanceError = "Could not save scan performance locally: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }
    }
    LaunchedEffect(performanceError) {
        performanceError?.let {
            snackbarHostState.showSnackbar(it)
            performanceError = null
        }
    }
    DisposableEffect(performanceStore) {
        onDispose { performanceStore.close() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isTriggering && !scanStatus.isScanning) {
                        viewModel.triggerLiveScan { success, err ->
                            triggerSnackbar = if (success) "✅ Scan triggered — results update live!" else "❌ Failed: $err"
                        }
                    }
                },
                containerColor = if (scanStatus.isScanning || isTriggering)
                    MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
                contentColor = if (scanStatus.isScanning || isTriggering)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else Color.White,
                icon = {
                    if (isTriggering || scanStatus.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run Scan")
                    }
                },
                text = {
                    Text(
                        if (scanStatus.isScanning) "Scanning..." else "Run Live Scan",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // --- Live Status Banner ---
            LiveStatusBanner(scanStatus)

            TabRow(
                selectedTabIndex = selectedStockTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf("🎯 AI Shortlist (Top 5)", "📋 All Scans", "📁 Day Archive").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedStockTab == index,
                        onClick = { selectedStockTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedStockTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedStockTab) {
                // ── TAB 0: AI DEEP-DIVE PICKS (Top 3-5 Shortlist) ──
                0 -> {
                    when (val state = filteredScansState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "AI Agent analyzing scan universe for high-conviction setups...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is UiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is UiState.Success -> {
                            val stocks = state.data
                            if (stocks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AutoGraph,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(54.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "No Live Scan Results Yet",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Tap 'Run Live Scan' to scan the market. Once finished, the AI Deep-Dive Agent will automatically evaluate all candidates and shortlist the Top 3 to 5 highest-probability picks.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                viewModel.triggerLiveScan { success, _ ->
                                                    triggerSnackbar = if (success) "✅ Scan triggered!" else "❌ Trigger failed"
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Run Live Scan Now", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                val aiPicks = remember(stocks) {
                                    try {
                                        AiStockDeepDiveAgent.analyzeScanItems(stocks, maxPicks = 5)
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }

                                if (aiPicks.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AutoGraph,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "Evaluating Scanned Setups...",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Raw candidates (${stocks.size}) are ready in the All Scans tab. Tap below to inspect them.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            OutlinedButton(onClick = { selectedStockTab = 1 }) {
                                                Text("View All Scanned Feed")
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 80.dp)
                                    ) {
                                        item {
                                            AiDeepDiveHeaderBanner(
                                                totalEvaluated = stocks.size,
                                                shortlistedCount = aiPicks.size,
                                                sourceName = "NSE Algorithmic Scanner"
                                            )
                                        }

                                        items(aiPicks, key = { "ai-pick-${it.rank}-${it.symbol}" }) { pick ->
                                            AiDeepDivePickCard(
                                                pick = pick,
                                                onInspectTechnicals = {
                                                    selectedStock = stocks.firstOrNull { it.symbol == pick.symbol }
                                                        ?: StockScanItem(
                                                            symbol = pick.symbol,
                                                            close = pick.currentPrice,
                                                            changePct = pick.changePct,
                                                            sector = pick.sector
                                                        )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── TAB 1: ALL SCANS FEED (Full List with Search & Filters) ──
                1 -> {
                    // --- Search Bar ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search symbol, sector...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- Category Tabs (ALL / INTRADAY / SWING / POSITIONAL) ---
                    val categories = listOf("ALL", "INTRADAY", "SWING", "POSITIONAL", "WATCHLIST")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            val catColor = when (cat) {
                                "INTRADAY" -> ElectricBlue
                                "SWING" -> GoldWarning
                                "POSITIONAL" -> BullishGreen
                                else -> MaterialTheme.colorScheme.primary
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onCategorySelected(cat) },
                                label = {
                                    Text(
                                        text = cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = catColor,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // --- Metric Filter Chips ---
                    val filters = listOf("All", "Score ≥ 4", "Gainers", "Top R:R")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filters.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onFilterSelected(filter) },
                                label = {
                                    Text(
                                        text = filter,
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

                    Spacer(modifier = Modifier.height(10.dp))

                    when (val state = filteredScansState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Loading algorithmic stock scans...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is UiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = state.message, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                        is UiState.Success -> {
                            val stocks = state.data
                            if (stocks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No setups match your filter yet.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${stocks.size} setups qualified",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Filtered by $selectedFilter",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(stocks, key = { "${it.symbol}-${it.recommendation}-${it.pattern}" }) { stock ->
                                        StockItemCard(stock) { selectedStock = stock }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── TAB 2: DAY FOLDERS ARCHIVE ──
                2 -> {
                    StockPerformanceTrackerContent(
                        modifier = Modifier.fillMaxSize(),
                        records = performanceRecords,
                        searchQuery = performanceSearch,
                        onSearchChange = { performanceSearch = it },
                        refreshingRecordId = refreshingPerformanceId,
                        onRefresh = { record ->
                            if (refreshingPerformanceId == null) {
                                scope.launch {
                                    refreshingPerformanceId = record.id
                                    try {
                                        val candles = withContext(Dispatchers.IO) {
                                            PlaybookScanner.fetchHistoryForPerformance(record.symbol)
                                        }
                                        if (candles.isEmpty()) {
                                            throw IllegalStateException("No daily price history was returned for ${record.symbol}.")
                                        }
                                        performanceRecords = withContext(Dispatchers.IO) {
                                            performanceStore.recordPriceHistory(
                                                record.symbol,
                                                candles,
                                                performanceSearch
                                            )
                                        }
                                        performanceError = null
                                    } catch (e: Exception) {
                                        performanceError =
                                            "Could not refresh ${record.symbol}: ${e.localizedMessage ?: e.javaClass.simpleName}"
                                    } finally {
                                        refreshingPerformanceId = null
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    selectedStock?.let { stock ->
        Dialog(onDismissRequest = { selectedStock = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${stock.symbol.removeSuffix(".NS")} · Full details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { selectedStock = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close stock details")
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { StockDeepDiveCard(stock) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StockPerformanceTrackerContent(
    modifier: Modifier = Modifier,
    records: List<StockScanPerformanceRecord>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    refreshingRecordId: String?,
    onRefresh: (StockScanPerformanceRecord) -> Unit
) {
    val sdf = remember { java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault()) }
    val groupedByDay = remember(records) {
        records.groupBy { sdf.format(java.util.Date(it.signalDay)) }
    }
    var collapsedDays by remember { mutableStateOf<Set<String>>(emptySet()) }

    val totalTargets = records.count { rec -> rec.horizons.any { it.outcome == StockReturnOutcome.TARGET_HIT } }
    val totalStops   = records.count { rec -> rec.horizons.any { it.outcome == StockReturnOutcome.STOP_HIT } }
    val totalTracking = records.count { rec -> rec.horizons.any { it.outcome == StockReturnOutcome.TRACKING || it.outcome == StockReturnOutcome.WAITING } }
    val totalResolved = totalTargets + totalStops
    val winRate = if (totalResolved > 0) (totalTargets * 100.0 / totalResolved) else 0.0

    Column(modifier.fillMaxWidth()) {
        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📁 Live Scanner Day-Wise Folders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("${records.size} total records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯 Targets Hit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalTargets", color = BullishGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ SL Hit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalStops", color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳ Tracking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalTracking", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Win Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.1f", winRate)}%", color = if (winRate >= 50) BullishGreen else GoldWarning, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            singleLine = true,
            placeholder = { Text("Search scanned symbol or sector…") },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(10.dp)
        )

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank())
                        "No scan records saved yet. Keep this screen open until the latest scan loads or run a scan."
                    else
                        "No saved scan records match '$searchQuery'.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                groupedByDay.forEach { (dayDate, dayRecords) ->
                    val isFolderOpen = !collapsedDays.contains(dayDate)
                    val dayTargets = dayRecords.count { r -> r.horizons.any { it.outcome == StockReturnOutcome.TARGET_HIT } }
                    val dayStops   = dayRecords.count { r -> r.horizons.any { it.outcome == StockReturnOutcome.STOP_HIT } }
                    val dayOpen    = dayRecords.count { r -> r.horizons.any { it.outcome == StockReturnOutcome.TRACKING || it.outcome == StockReturnOutcome.WAITING } }

                    item(key = "scanner-day-$dayDate") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFolderOpen) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isFolderOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
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
                                        contentDescription = null,
                                        tint = if (isFolderOpen) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = dayDate,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(${dayRecords.size} stocks)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (dayTargets > 0) Text("🎯 $dayTargets Hit", color = BullishGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (dayStops > 0) Text("❌ $dayStops SL", color = BearishRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (dayOpen > 0) Text("⏳ $dayOpen Track", color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Icon(
                                        if (isFolderOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isFolderOpen) {
                        items(dayRecords, key = { it.id }) { record ->
                            StockPerformanceRecordCard(
                                record = record,
                                isRefreshing = refreshingRecordId == record.id,
                                isAnyRefreshing = refreshingRecordId != null,
                                onRefresh = { onRefresh(record) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockPerformanceRecordCard(
    record: StockScanPerformanceRecord,
    isRefreshing: Boolean,
    isAnyRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val signalDateStr = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault()).format(java.util.Date(record.signalDay))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(record.symbol.removeSuffix(".NS"), fontWeight = FontWeight.Bold)
                    Text(
                        listOf(record.sector, record.pattern, record.recommendation)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Entry ₹${String.format("%.2f", record.entryPrice)}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Scan $signalDateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "📅 Entry Date: $signalDateStr",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Target ₹${record.targetPrice.takeIf { it > 0 }?.let { String.format("%.2f", it) } ?: "—"}" +
                        " · Stop ₹${record.stopLoss.takeIf { it > 0 }?.let { String.format("%.2f", it) } ?: "—"}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            record.horizons.forEach { result ->
                StockHorizonResultRow(result)
            }
            TextButton(
                onClick = onRefresh,
                enabled = !isAnyRefreshing,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(" Refreshing prices…")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Refresh prices")
                }
            }
        }
    }
}

@Composable
private fun StockHorizonResultRow(result: StockHorizonReturn) {
    val color = when (result.outcome) {
        StockReturnOutcome.TARGET_HIT -> MaterialTheme.colorScheme.secondary
        StockReturnOutcome.STOP_HIT -> MaterialTheme.colorScheme.error
        StockReturnOutcome.HORIZON_COMPLETE -> MaterialTheme.colorScheme.primary
        StockReturnOutcome.TRACKING, StockReturnOutcome.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val returnText = result.returnPct?.let {
        "${if (it >= 0) "+" else ""}${String.format("%.2f", it)}%"
    } ?: "—"
    val eventDateStr = result.eventDate?.let { " on $it" } ?: (result.eventDay?.let { " on Day $it" } ?: "")
    val statusText = when (result.outcome) {
        StockReturnOutcome.TARGET_HIT ->
            "🎯 TARGET Hit$eventDateStr · Return $returnText"
        StockReturnOutcome.STOP_HIT ->
            "❌ STOP Hit$eventDateStr · Return $returnText"
        StockReturnOutcome.HORIZON_COMPLETE ->
            "Completed ${result.horizon.sessions} sessions · Return $returnText"
        StockReturnOutcome.TRACKING ->
            "⏳ Tracking ${result.observedSessions}/${result.horizon.sessions} sessions · Current $returnText"
        StockReturnOutcome.WAITING ->
            "Waiting for future daily candles"
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${result.horizon.label} (${result.horizon.sessions} trading days)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun StockDeepDiveCard(stock: StockScanItem) {
    val directionColor = when (stock.analysisSide) {
        "Bullish" -> BullishGreen
        "Bearish" -> BearishRed
        else -> GoldWarning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, directionColor.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stock.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${stock.sector} · ${stock.pattern}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stock.recommendation, color = directionColor, fontWeight = FontWeight.Bold)
            }
            Text(
                (stock.close.takeIf { it > 0 }?.let { "₹${String.format("%.2f", it)}" } ?: "Price unavailable") +
                    " · ${String.format("%+.2f", stock.changePct)}% · Score ${stock.score}/10",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            StockEvidenceLine("Traded volume", if (stock.tradedVolume > 0) formatShares(stock.tradedVolume) else "Awaiting refreshed scan")
            StockEvidenceLine("20-day average", if (stock.averageVolume > 0) formatShares(stock.averageVolume) else "Awaiting refreshed scan")
            StockEvidenceLine(
                "Volume activity",
                stock.volumeRatio.takeIf { it > 0 }?.let { "${String.format("%.1f", it)}× average" } ?: "Unavailable"
            )
            StockEvidenceLine("Chart pattern", stock.chartPattern.ifBlank { "No pattern snapshot yet" })
            StockEvidenceLine("Latest candle", stock.candlePattern.ifBlank { "No candle snapshot yet" })
            StockEvidenceLine("RSI 14", if (stock.rsi14 > 0) String.format("%.1f", stock.rsi14) else "--")
            StockEvidenceLine("EMA 20", if (stock.ema20 > 0) "₹${String.format("%.2f", stock.ema20)}" else "--")
            StockEvidenceLine(
                "EMA 20 position",
                when {
                    stock.close <= 0 || stock.ema20 <= 0 -> "Unavailable"
                    stock.close >= stock.ema20 -> "Close above EMA 20"
                    else -> "Close below EMA 20"
                }
            )
            StockEvidenceLine("Scan entry", stock.entry.takeIf { it > 0 }?.let { "₹${String.format("%.2f", it)}" } ?: "Not provided")
            StockEvidenceLine("Scan target", stock.targetPrice.takeIf { it > 0 }?.let { "₹${String.format("%.2f", it)}" } ?: "Not provided")
            StockEvidenceLine("Scan stop", stock.stopLoss.takeIf { it > 0 }?.let { "₹${String.format("%.2f", it)}" } ?: "Not provided")
            StockEvidenceLine(
                "Risk / reward",
                stock.riskReward.takeIf { it > 0 }?.let { "1:${String.format("%.1f", it)}" } ?: "Not provided"
            )
            StockEvidenceLine("Base pattern", stock.basePattern.ifBlank { "Not provided by scan" })
            StockEvidenceLine("Base score", stock.baseScore.takeIf { it > 0 }?.let { "$it/10" } ?: "Not provided")
            StockEvidenceLine("Breakout flag", if (stock.baseBreakout) "Reported by scan" else "Not reported")
            StockEvidenceLine("Scan bar date", stock.barDate.ifBlank { "Not provided" })

            if (stock.candles.isNotEmpty()) {
                StockCandlestickChart(stock.candles)
            } else {
                Text(
                    "No candle history was included in this scan snapshot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            stock.signals.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StockCandlestickChart(history: List<StockCandle>) {
    var weekly by remember { mutableStateOf(false) }
    val bars = remember(history, weekly) {
        val sorted = history.sortedBy { it.time }
        if (weekly) sorted.chunked(5).map { group ->
            StockCandle(
                time = group.first().time, open = group.first().open,
                high = group.maxOf { it.high }, low = group.minOf { it.low },
                close = group.last().close, volume = group.sumOf { it.volume }
            )
        } else sorted.takeLast(25)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Candlestick chart", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("1D") }, modifier = Modifier.height(32.dp))
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("1W") }, modifier = Modifier.height(32.dp))
            }
        }
        if (bars.isEmpty()) {
            Text("Candle history will appear after your next scan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(if (weekly) "Weekly candles · grouped by 5 trading sessions" else "Daily OHLC · lower bars are traded share volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val chartGridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val chartDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Canvas(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)) {
                val high = bars.maxOf { it.high }
                val low = bars.minOf { it.low }
                val spread = (high - low).coerceAtLeast(0.01)
                val priceHeight = size.height * 0.76f
                val volumeTop = size.height * 0.82f
                val volumeHeight = size.height * 0.14f
                val maxVolume = bars.maxOf { it.volume }.coerceAtLeast(1)
                val candleWidth = (size.width / bars.size * 0.72f).coerceIn(5f, 18f)
                fun y(price: Double) = ((high - price) / spread).toFloat() * (priceHeight - 10f) + 5f
                for (grid in 1..3) {
                    val gridY = priceHeight * grid / 4f
                    drawLine(chartGridColor, androidx.compose.ui.geometry.Offset(0f, gridY), androidx.compose.ui.geometry.Offset(size.width, gridY), 1f)
                }
                bars.forEachIndexed { index, bar ->
                    val x = (index + 0.5f) * size.width / bars.size
                    val color = if (bar.close >= bar.open) BullishGreen else BearishRed
                    drawLine(color, androidx.compose.ui.geometry.Offset(x, y(bar.high)), androidx.compose.ui.geometry.Offset(x, y(bar.low)), 1.5f)
                    val top = minOf(y(bar.open), y(bar.close))
                    val bottom = maxOf(y(bar.open), y(bar.close))
                    drawRect(color, androidx.compose.ui.geometry.Offset(x - candleWidth / 2f, top), androidx.compose.ui.geometry.Size(candleWidth, (bottom - top).coerceAtLeast(2f)))
                    drawRect(color, androidx.compose.ui.geometry.Offset(x - candleWidth / 2f, top), androidx.compose.ui.geometry.Size(candleWidth, (bottom - top).coerceAtLeast(2f)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    val volumeBarHeight = bar.volume.toFloat() / maxVolume * volumeHeight
                    drawRect(color.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(x - candleWidth / 2f, volumeTop + volumeHeight - volumeBarHeight), androidx.compose.ui.geometry.Size(candleWidth, volumeBarHeight.coerceAtLeast(1f)))
                }
                drawLine(chartDividerColor, androidx.compose.ui.geometry.Offset(0f, volumeTop), androidx.compose.ui.geometry.Offset(size.width, volumeTop), 1f)
            }
        }
    }
}

@Composable
private fun StockEvidenceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatShares(value: Long): String = when {
    value >= 10_000_000 -> String.format("%.2f Cr", value / 10_000_000.0)
    value >= 100_000 -> String.format("%.2f L", value / 100_000.0)
    else -> String.format("%,d", value)
}

@Composable
fun LiveStatusBanner(status: ScanStatus) {
    val isScanning = status.isScanning
    val animatedProgress by animateFloatAsState(
        targetValue = status.progress / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )
    val bannerColor by animateColorAsState(
        targetValue = if (isScanning) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                      else MaterialTheme.colorScheme.surfaceVariant,
        label = "bannerBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "bannerBorder"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bannerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isScanning) MaterialTheme.colorScheme.primary else BullishGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScanning) status.status else "Engine Ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (status.lastScanTime.isNotEmpty()) {
                    Text(
                        text = "Last: ${status.lastScanTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isScanning && animatedProgress > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${status.progress}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!isScanning && status.results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status.results,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StockItemCard(stock: StockScanItem, onClick: () -> Unit = {}) {
    val isPositive = stock.changePct >= 0
    val changeColor = if (isPositive) BullishGreen else BearishRed
    val changeBg = if (isPositive) BullishGreenContainer else BearishRedContainer

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Symbol, Price, Change %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stock.symbol.removeSuffix(".NS"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (stock.sector.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = stock.sector,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = if (stock.close > 0) String.format("₹%,.2f", stock.close) else "₹ --",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Change % Pill
                Surface(
                    color = changeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, changeColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%+.2f%%", stock.changePct),
                            color = changeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Badges Row: Pattern, RSI, Volume Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (stock.pattern.isNotEmpty()) {
                    StockTagPill(
                        label = stock.pattern,
                        bgColor = ElectricBlue.copy(alpha = 0.15f),
                        textColor = ElectricBlue
                    )
                }
                if (stock.rsi14 > 0) {
                    StockTagPill(
                        label = "RSI ${stock.rsi14.toInt()}",
                        bgColor = if (stock.rsi14 > 60) BullishGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background,
                        textColor = if (stock.rsi14 > 60) BullishGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stock.volumeRatio > 0) {
                    StockTagPill(
                        label = "Vol ${String.format("%.1fx", stock.volumeRatio)}",
                        bgColor = if (stock.volumeRatio > 2.0) GoldWarning.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background,
                        textColor = if (stock.volumeRatio > 2.0) GoldWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Score Badge
                Surface(
                    color = if (stock.score >= 4) BullishGreenContainer else MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, if (stock.score >= 4) BullishGreen else MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "Score ${stock.score}/10",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (stock.score >= 4) BullishGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Levels: Target, Stop Loss, Recommendation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Target (+2%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("₹%,.2f", stock.target2Pct),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BullishGreen
                    )
                }
                Column {
                    Text(
                        text = "Stop-Loss",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("₹%,.2f", stock.stopLoss),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BearishRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Recommendation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stock.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (stock.recommendation.contains("BUY", ignoreCase = true)) BullishGreen else NeutralSlate
                    )
                }
            }

            // Signals text
            if (stock.signals.isNotEmpty() && stock.signals.first().isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stock.signals.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StockTagPill(label: String, bgColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
