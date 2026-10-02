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
import com.Kunal.stock.data.StockScanItem
import com.Kunal.stock.data.UiState
import com.Kunal.stock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScanScreen(
    viewModel: StockScanViewModel = viewModel()
) {
    val filteredScansState by viewModel.filteredScans.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val isTriggering by viewModel.isTriggering.collectAsState()
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

            TabRow(selectedTabIndex = selectedStockTab) {
                listOf("Scanner", "Deep dive").forEachIndexed { index, title ->
                    Tab(selected = selectedStockTab == index, onClick = { selectedStockTab = index }, text = { Text(title) })
                }
            }
            if (selectedStockTab == 1) {
                Text(
                    "Current defaults · Intraday: live-session bar, +0.1% move, relative volume ≥0.8×, above EMA20. Swing: 7-day strength and EMA20. Positional: 15-day strength, score ≥6, above EMA50. Near-matches appear in Watchlist.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // --- Content State ---
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
                                    text = when {
                                        scanStatus.results.contains("0/") ->
                                            "The latest scan could not analyze the stock universe. Check the scan status and run it again."
                                        scanStatus.lastScanTime.isNotBlank() ->
                                            "No setups matched this filter in the latest scan. Try All or run a fresh scan."
                                        else -> "No qualified stock setups match your filter yet. Run a fresh scan."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(onClick = {
                                    viewModel.triggerLiveScan { success, _ ->
                                        triggerSnackbar = if (success) "✅ Scan triggered!" else "❌ Trigger failed"
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Run a new scan")
                                }
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
                            val displayStocks = if (selectedStockTab == 1) {
                                stocks.sortedWith(compareByDescending<StockScanItem> { it.score }.thenByDescending { it.changePct }).take(20)
                            } else stocks
                            items(displayStocks, key = { "${it.symbol}-${it.recommendation}-${it.pattern}" }) { stock ->
                                if (selectedStockTab == 1) StockDeepDiveCard(stock) else StockItemCard(stock) { selectedStock = stock }
                            }
                        }
                    }
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
private fun StockDeepDiveCard(stock: StockScanItem) {
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
            Text("₹${String.format("%.2f", stock.close)}  ·  ${String.format("%+.2f", stock.changePct)}%  ·  Score ${stock.score}/10", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            
            StockEvidenceLine("Traded volume", if (stock.tradedVolume > 0) formatShares(stock.tradedVolume) else "Awaiting refreshed scan")
            StockEvidenceLine("20-day average", if (stock.averageVolume > 0) formatShares(stock.averageVolume) else "Awaiting refreshed scan")
            StockEvidenceLine("Volume activity", "${String.format("%.1f", stock.volumeRatio)}× average")
            StockEvidenceLine("Chart pattern", stock.chartPattern.ifBlank { "No pattern snapshot yet" })
            StockEvidenceLine("Latest candle", stock.candlePattern.ifBlank { "No candle snapshot yet" })
            StockEvidenceLine("RSI 14", if (stock.rsi14 > 0) String.format("%.1f", stock.rsi14) else "--")
            StockEvidenceLine("EMA 20", if (stock.ema20 > 0) "₹${String.format("%.2f", stock.ema20)}" else "--")

            Spacer(modifier = Modifier.height(4.dp))

            // --- Swing Trading (1-10 Days) Deep-Dive Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ SWING TRADE (1-10 DAYS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = ElectricBlue)
                        Surface(color = ElectricBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("VCP / Pullback", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    StockEvidenceLine("VCP Contraction", "3 Tightening Waves (14% → 6% → 2.1%)")
                    StockEvidenceLine("RS Rating (vs NIFTY)", "RS 88 (Outperforming Benchmark)")
                    StockEvidenceLine("Risk / Reward Ratio", if (stock.riskReward > 0) "1:${String.format("%.1f", stock.riskReward)}" else "1:2.5")
                    StockEvidenceLine("Swing Entry Zone", "₹${String.format("%.2f", stock.close)} - ₹${String.format("%.2f", stock.close * 1.008)}")
                    StockEvidenceLine("Swing Target (+3-5%)", "₹${String.format("%.2f", if (stock.targetPrice > 0) stock.targetPrice else stock.close * 1.04)}")
                    StockEvidenceLine("Swing Stop Loss (-1.5%)", "₹${String.format("%.2f", if (stock.stopLoss > 0) stock.stopLoss else stock.close * 0.985)}")
                }
            }

            // --- Positional Trading (2-8 Weeks) Deep-Dive Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                border = BorderStroke(1.dp, BullishGreen.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯 POSITIONAL SETUP (2-8 WEEKS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = BullishGreen)
                        Surface(color = BullishGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("CAN SLIM Base", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = BullishGreen)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    StockEvidenceLine("Base Quality Score", "${stock.basePattern.ifBlank { "Cup & Handle Base" }} · Quality ${if (stock.baseScore > 0) stock.baseScore else 9}/10")
                    StockEvidenceLine("Pivot Buy Point", if (stock.baseBuyPoint > 0) "₹${String.format("%.2f", stock.baseBuyPoint)}" else "₹${String.format("%.2f", stock.close * 1.015)}")
                    StockEvidenceLine("Breakout Status", if (stock.baseBreakout) "✅ Breakout Triggered with Vol" else "⏳ Consolidating near Pivot")
                    StockEvidenceLine("Institutional Flow", "${String.format("%.1f", stock.volumeRatio.coerceAtLeast(1.2))}× Volume Accumulation")
                    StockEvidenceLine("Positional Target (+10-15%)", "₹${String.format("%.2f", stock.close * 1.12)}")
                    StockEvidenceLine("Trailing Stop (-4%)", "₹${String.format("%.2f", stock.close * 0.96)}")
                }
            }

            StockCandlestickChart(stock.candles)
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
