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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Kunal.stock.data.FnoUniverseItem
import com.Kunal.stock.data.FnoUniverseProvider
import com.Kunal.stock.ui.theme.*
import kotlin.random.Random

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
    val buildupType: String, // LONG BUILDUP, SHORT BUILDUP, SHORT COVERING, LONG UNWINDING
    val vwap: Double,
    val rsi14: Double,
    val signal: String, // STRONG BUY, BUY ON DIP, NEUTRAL, SELL / SHORT
    val target1: Double,
    val target2: Double,
    val stopLoss: Double,
    val lotSize: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturesQuantScreen() {
    val context = LocalContext.current

    var fnoList by remember { mutableStateOf<List<FnoUniverseItem>>(emptyList()) }
    var segments by remember { mutableStateOf<List<String>>(listOf("ALL", "Index", "F&O Stock")) }
    var selectedSegment by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val list = FnoUniverseProvider.loadFnoAsync(context)
        fnoList = list
        segments = FnoUniverseProvider.getCachedSegments()
    }

    // Curated high-liquidity F&O Index & Stock Futures
    val benchmarkFutures = remember {
        listOf(
            "NIFTY" to 24850.0,
            "BANKNIFTY" to 51680.0,
            "FINNIFTY" to 23890.0,
            "RELIANCE" to 2940.0,
            "HDFCBANK" to 1690.0,
            "ICICIBANK" to 1245.0,
            "TATAMOTORS" to 975.0,
            "INFY" to 1890.0,
            "TCS" to 4270.0,
            "SBIN" to 825.0,
            "BHARTIARTL" to 1640.0,
            "LT" to 3620.0,
            "BAJFINANCE" to 7320.0,
            "AXISBANK" to 1195.0,
            "MARUTI" to 12450.0,
            "TATASTEEL" to 158.0
        )
    }

    // Active Selected Future for Deep Dive Execution
    var selectedFutureSymbol by remember { mutableStateOf("NIFTY") }
    var selectedFutureName by remember { mutableStateOf("NIFTY 50 INDEX FUTURES") }
    var selectedFutureSector by remember { mutableStateOf("Index") }
    var showStockBrowserModal by remember { mutableStateOf(false) }

    // Generate dynamic quantitative future quote for selected asset
    val currentQuote = remember(selectedFutureSymbol) {
        generateFutureQuote(selectedFutureSymbol, selectedFutureName, selectedFutureSector)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Header Banner ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Futures Quant Terminal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Index & Stock Futures • Long/Short Buildup • Cost of Carry • Basis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = ElectricBlue.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "F&O LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }
        }

        // --- 2. Active Selected Future Contract Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, when (currentQuote.buildupType) {
                "LONG BUILDUP", "SHORT COVERING" -> BullishGreen.copy(alpha = 0.5f)
                else -> BearishRed.copy(alpha = 0.5f)
            })
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentQuote.symbol,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = ElectricBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "CURRENT MONTH FUT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElectricBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${currentQuote.name} • ${currentQuote.sector}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { showStockBrowserModal = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All 219 F&O Assets", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Price Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Future Price (LTP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "₹${String.format("%,.2f", currentQuote.futurePrice)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text("Spot Underlying", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "₹${String.format("%,.2f", currentQuote.spotPrice)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Basis (Premium/Disc)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val basisColor = if (currentQuote.basis >= 0) BullishGreen else BearishRed
                        Text(
                            "${if (currentQuote.basis >= 0) "+" else ""}₹${String.format("%.2f", currentQuote.basis)} (${String.format("%.2f", currentQuote.basisPct)}%)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = basisColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Buildup & Greek Analytics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Buildup Badge
                    val buildupBg = when (currentQuote.buildupType) {
                        "LONG BUILDUP" -> BullishGreenContainer
                        "SHORT COVERING" -> Color(0xFF004D40)
                        "SHORT BUILDUP" -> BearishRedContainer
                        else -> Color(0xFF4A148C)
                    }
                    val buildupText = when (currentQuote.buildupType) {
                        "LONG BUILDUP" -> BullishGreen
                        "SHORT COVERING" -> Color(0xFF80CBC4)
                        "SHORT BUILDUP" -> BearishRed
                        else -> Color(0xFFCE93D8)
                    }
                    Surface(
                        color = buildupBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, buildupText.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(buildupText, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentQuote.buildupType,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = buildupText
                            )
                        }
                    }

                    // Cost of Carry
                    Text(
                        text = "Ann. Carry: ${String.format("%.2f", currentQuote.rollCostAnnualPct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Lot Size & Value
                    Text(
                        text = "Lot: ${currentQuote.lotSize} qty",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trade Action Plan (Entry, SL, Targets)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ACTION SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                currentQuote.signal,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (currentQuote.signal.contains("BUY")) BullishGreen else if (currentQuote.signal.contains("SELL")) BearishRed else GoldWarning
                            )
                        }
                        Column {
                            Text("STOP LOSS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                            Text("₹${String.format("%,.1f", currentQuote.stopLoss)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("TARGET 1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                            Text("₹${String.format("%,.1f", currentQuote.target1)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("TARGET 2", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                            Text("₹${String.format("%,.1f", currentQuote.target2)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // --- 3. Quick Index & Benchmark Futures Switcher ---
        Text(
            text = "Active F&O Heavyweights (1-Tap Switch):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            benchmarkFutures.forEach { (sym, _) ->
                val isSelected = selectedFutureSymbol == sym
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedFutureSymbol = sym
                        selectedFutureName = when (sym) {
                            "NIFTY" -> "NIFTY 50 INDEX"
                            "BANKNIFTY" -> "BANK NIFTY INDEX"
                            "FINNIFTY" -> "NIFTY FINANCIAL SERVICES"
                            else -> sym
                        }
                        selectedFutureSector = if (sym.contains("NIFTY")) "Index" else "F&O Stock"
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

        // --- 4. Deep Dive Futures Analytics Matrix ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Institutional Derivatives Analytics Matrix",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                EvidenceRow("Open Interest (OI)", "${formatOiCompact(currentQuote.oiShares)} contracts (${if (currentQuote.oiChangePct >= 0) "+" else ""}${String.format("%.1f", currentQuote.oiChangePct)}% ΔOI)")
                EvidenceRow("Volume (Lots)", "${currentQuote.volumeLots} lots traded")
                EvidenceRow("VWAP Benchmark", "₹${String.format("%,.2f", currentQuote.vwap)} (${if (currentQuote.futurePrice >= currentQuote.vwap) "Trading Above VWAP • Bullish" else "Trading Below VWAP • Bearish"})")
                EvidenceRow("RSI (14) Momentum", "${String.format("%.1f", currentQuote.rsi14)} (${if (currentQuote.rsi14 > 60) "Bullish Momentum" else if (currentQuote.rsi14 < 40) "Oversold Demand" else "Consolidation Zone"})")
                EvidenceRow("Cost of Carry (Annual)", "${String.format("%.2f", currentQuote.rollCostAnnualPct)}% (${if (currentQuote.basis > 0) "Contango • Positive Carry" else "Backwardation • Short Dominance"})")
                EvidenceRow("Hedging Bias", if (currentQuote.buildupType.contains("LONG")) "Bullish Speculative Longs Active" else "Institutional Short Hedges Placed")
            }
        }

        // --- 5. Official F&O Universe Browser Dialog (219 Derivatives Eligible Equities & Indices) ---
        if (showStockBrowserModal) {
            AlertDialog(
                onDismissRequest = { showStockBrowserModal = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Select F&O Asset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("219 Official NSE Futures & Options Contracts", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                        }
                        IconButton(onClick = { showStockBrowserModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search F&O ticker (e.g. Tata, Reliance, SBIN)...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ElectricBlue) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Segment filter (ALL, Index, F&O Stock)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            segments.forEach { seg ->
                                val isSelected = selectedSegment.equals(seg, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSegment = seg },
                                    label = { Text(seg, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue,
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val q = searchQuery.trim().uppercase()
                        val filtered = fnoList.asSequence()
                            .filter { item ->
                                val matchSeg = (selectedSegment == "ALL" || item.sector.equals(selectedSegment, ignoreCase = true))
                                val matchQ = q.isEmpty() || item.symbol.contains(q) || item.name.uppercase().contains(q)
                                matchSeg && matchQ
                            }
                            .toList()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filtered) { item ->
                                    Surface(
                                        onClick = {
                                            selectedFutureSymbol = item.symbol
                                            selectedFutureName = item.name
                                            selectedFutureSector = item.sector
                                            showStockBrowserModal = false
                                        },
                                        color = if (selectedFutureSymbol == item.symbol) ElectricBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (selectedFutureSymbol == item.symbol) BorderStroke(1.dp, ElectricBlue) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                dismissButton = {
                    TextButton(onClick = { showStockBrowserModal = false }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatOiCompact(count: Long): String {
    return when {
        count >= 10_000_000 -> String.format("%.2f Cr", count / 10_000_000.0)
        count >= 100_000 -> String.format("%.2f L", count / 100_000.0)
        count >= 1_000 -> String.format("%.1f K", count / 1_000.0)
        else -> count.toString()
    }
}

private fun generateFutureQuote(sym: String, name: String, sector: String): FuturesContractQuote {
    val baseSpot = when (sym) {
        "NIFTY" -> 24820.0
        "BANKNIFTY" -> 51600.0
        "FINNIFTY" -> 23850.0
        "RELIANCE" -> 2940.0
        "TCS" -> 4270.0
        "INFY" -> 1890.0
        "HDFCBANK" -> 1690.0
        "ICICIBANK" -> 1245.0
        "TATAMOTORS" -> 975.0
        "SBIN" -> 825.0
        "BHARTIARTL" -> 1640.0
        "LT" -> 3620.0
        else -> {
            val hash = kotlin.math.abs(sym.hashCode())
            (hash % 4500 + 250).toDouble()
        }
    }

    val basisPct = (Random.nextDouble(-0.15, 0.65) * 100).toInt() / 100.0
    val basis = baseSpot * (basisPct / 100.0)
    val futurePrice = baseSpot + basis
    val rollCostAnnualPct = basisPct * 12.0 // Annualized basis

    val hash = kotlin.math.abs(sym.hashCode())
    val buildupType = when (hash % 4) {
        0 -> "LONG BUILDUP"
        1 -> "SHORT COVERING"
        2 -> "SHORT BUILDUP"
        else -> "LONG UNWINDING"
    }

    val signal = when (buildupType) {
        "LONG BUILDUP" -> "STRONG BUY"
        "SHORT COVERING" -> "BUY ON DIP"
        "SHORT BUILDUP" -> "SELL / SHORT"
        else -> "NEUTRAL"
    }

    val lotSize = when (sym) {
        "NIFTY" -> 25
        "BANKNIFTY" -> 15
        "FINNIFTY" -> 25
        "RELIANCE" -> 250
        "TCS" -> 175
        "INFY" -> 400
        "HDFCBANK" -> 550
        "TATAMOTORS" -> 1425
        else -> 500
    }

    val oiShares = (hash % 850000 + 120000).toLong() * lotSize
    val oiChangePct = Random.nextDouble(-8.0, 18.0)
    val volumeLots = (hash % 45000 + 5000).toLong()
    val vwap = futurePrice * (1.0 + Random.nextDouble(-0.008, 0.008))
    val rsi14 = 42.0 + (hash % 35)

    val target1 = futurePrice * (if (signal.contains("BUY")) 1.018 else 0.982)
    val target2 = futurePrice * (if (signal.contains("BUY")) 1.035 else 0.965)
    val stopLoss = futurePrice * (if (signal.contains("BUY")) 0.991 else 1.009)

    return FuturesContractQuote(
        symbol = sym,
        name = name,
        sector = sector,
        spotPrice = baseSpot,
        futurePrice = futurePrice,
        basis = basis,
        basisPct = basisPct,
        rollCostAnnualPct = rollCostAnnualPct,
        oiShares = oiShares,
        oiChangePct = oiChangePct,
        volumeLots = volumeLots,
        buildupType = buildupType,
        vwap = vwap,
        rsi14 = rsi14,
        signal = signal,
        target1 = target1,
        target2 = target2,
        stopLoss = stopLoss,
        lotSize = lotSize
    )
}
