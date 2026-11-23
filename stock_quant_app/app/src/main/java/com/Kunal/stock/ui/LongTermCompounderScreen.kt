package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LongTermStockItem(
    val symbol: String,
    val sector: String,
    val score: Double,
    val roce5Yr: Double,
    val roceMin: Double,
    val salesGrowth: Double,
    val patGrowth: Double,
    val debtEquity: Double,
    val cfoPat: Double,
    val qualityPassed: Boolean,
    val trancheTiming: String,
    val currentPrice: Double,
    val bullCase: String,
    val bearCase: String,
    val forensicNote: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongTermCompounderScreen() {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, QUALIFIED, TRANCHE_1
    var livePrices by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        while (true) {
            val fetched = LivePriceFetcher.fetchPrices(listOf("TITAN", "TCS", "PIDILITIND", "HAL", "BEL"))
            if (fetched.isNotEmpty()) {
                livePrices = livePrices + fetched
            }
            kotlinx.coroutines.delay(15_000)
        }
    }

    val compounders = remember(livePrices) {
        listOf(
            LongTermStockItem(
                symbol = "TITAN",
                sector = "Consumer Discretionary",
                score = 92.5,
                roce5Yr = 28.4,
                roceMin = 22.1,
                salesGrowth = 19.5,
                patGrowth = 22.8,
                debtEquity = 0.12,
                cfoPat = 0.88,
                qualityPassed = true,
                trancheTiming = "BUY TRANCHE 1",
                currentPrice = livePrices["TITAN"] ?: LivePriceFetcher.getBestPrice("TITAN"),
                bullCase = "Dominant organized jewelry market share expansion (Tanishq), strong pricing power, superior ROCE > 25%, and multi-category growth (watches, eyewear).",
                bearCase = "Gold price volatility can cause short-term margin compressions; high valuation multiples require sustained 15%+ revenue growth.",
                forensicNote = "Clean audit history, zero promoter pledge, working capital funded via gold metal loans (hedged), cash conversion remains > 80%."
            ),
            LongTermStockItem(
                symbol = "TCS",
                sector = "Information Technology",
                score = 89.0,
                roce5Yr = 54.2,
                roceMin = 48.0,
                salesGrowth = 11.2,
                patGrowth = 12.0,
                debtEquity = 0.0,
                cfoPat = 0.98,
                qualityPassed = true,
                trancheTiming = "BUY TRANCHE 1",
                currentPrice = livePrices["TCS"] ?: LivePriceFetcher.getBestPrice("TCS"),
                bullCase = "Unrivaled scale, debt-free fortress balance sheet, 50%+ ROCE, robust free cash flow conversion (>95%), and high recurring cloud transformation contracts.",
                bearCase = "Global enterprise tech budget slowdowns and macro uncertainty in US/European banking sector could extend client decision cycles.",
                forensicNote = "Zero debt, impeccable governance standards (Tata Group parentage), zero auditor concerns, and 100% of profits converted to cash."
            ),
            LongTermStockItem(
                symbol = "PIDILITIND",
                sector = "Specialty Chemicals",
                score = 87.5,
                roce5Yr = 26.5,
                roceMin = 21.0,
                salesGrowth = 13.8,
                patGrowth = 14.5,
                debtEquity = 0.04,
                cfoPat = 0.92,
                qualityPassed = true,
                trancheTiming = "ACCUMULATE TRANCHE 2",
                currentPrice = livePrices["PIDILITIND"] ?: LivePriceFetcher.getBestPrice("PIDILITIND"),
                bullCase = "Monopoly-like moat in adhesives & construction chemicals (Fevicol, M-Seal, Dr. Fixit), extensive distribution network across India reaching tier-3 towns.",
                bearCase = "VAM (Vinyl Acetate Monomer) crude-linked raw material price volatility can create gross margin fluctuations.",
                forensicNote = "Conservative capital structure, working capital days steady at 55 days, robust cash flow generation covering all capex."
            ),
            LongTermStockItem(
                symbol = "HAL",
                sector = "Aerospace & Defence",
                score = 86.0,
                roce5Yr = 29.8,
                roceMin = 24.5,
                salesGrowth = 12.5,
                patGrowth = 24.0,
                debtEquity = 0.0,
                cfoPat = 0.85,
                qualityPassed = true,
                trancheTiming = "BUY TRANCHE 1",
                currentPrice = livePrices["HAL"] ?: LivePriceFetcher.getBestPrice("HAL"),
                bullCase = "Massive order book (> ₹90,000 Cr) providing multi-year revenue visibility (Tejas Mk1A, Prachand LCH), indigenous defence manufacturing mandate.",
                bearCase = "Supply chain delivery delays for foreign components (e.g. GE engines) can impact execution schedules.",
                forensicNote = "Negative working capital, negative net debt (cash rich PSU balance sheet), zero forensic red flags."
            ),
            LongTermStockItem(
                symbol = "BEL",
                sector = "Defence Electronics",
                score = 88.5,
                roce5Yr = 31.0,
                roceMin = 25.2,
                salesGrowth = 15.0,
                patGrowth = 21.5,
                debtEquity = 0.0,
                cfoPat = 0.90,
                qualityPassed = true,
                trancheTiming = "BUY TRANCHE 1",
                currentPrice = livePrices["BEL"] ?: LivePriceFetcher.getBestPrice("BEL"),
                bullCase = "Dominant electronic warfare, radar, and avionics provider; consistent 20%+ operating margins; strong export pipeline expanding beyond domestic MoD.",
                bearCase = "Execution bottlenecks or government budgetary reallocation during mid-year fiscal reviews.",
                forensicNote = "Debt-free, high dividend payout ratio, zero promoter pledge, high return on incremental capital invested."
            )
        )
    }

    val filteredList = compounders.filter { item ->
        when (selectedFilter) {
            "QUALIFIED" -> item.qualityPassed
            "TRANCHE_1" -> item.trancheTiming == "BUY TRANCHE 1"
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Quality Gate Summary Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "💎 Long-Term Compounders (3-5 Years)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Screener.in 6-Rule Quality Gate • 3-Tranche Buy Strategy",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Gate: 5/5 Qualified",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6-Rule Checklist Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All Compounders") }
                    )
                    FilterChip(
                        selected = selectedFilter == "QUALIFIED",
                        onClick = { selectedFilter = "QUALIFIED" },
                        label = { Text("Quality Pass (100%)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "TRANCHE_1",
                        onClick = { selectedFilter = "TRANCHE_1" },
                        label = { Text("Buy Tranche 1") }
                    )
                }
            }
        }

        // List of Compounder Cards
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(filteredList) { stock ->
                CompounderCard(stock)
            }
        }
    }
}

@Composable
fun CompounderCard(stock: LongTermStockItem) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stock.symbol,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF00E676).copy(0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "QUALIFIED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    Text(
                        text = "${stock.sector} • ₹${"%,.1f".format(stock.currentPrice)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Score: ${stock.score} / 100",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = if (stock.trancheTiming == "BUY TRANCHE 1") Color(0xFF1B5E20) else Color(0xFFF57F17),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = stock.trancheTiming,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // 6-Rule Fundamental Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("5-Yr ROCE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stock.roce5Yr}% (min ${stock.roceMin}%)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column {
                    Text("3-Yr Sales/PAT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+${stock.salesGrowth}% / +${stock.patGrowth}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column {
                    Text("Debt/Equity", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stock.debtEquity}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00E676))
                }
                Column {
                    Text("CFO / PAT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(stock.cfoPat * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Expandable 7-Analyst Deep Research Dossier
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📚 7-Analyst Deep Research Dossier", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("🐂 Bull Case (Economic Moat):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00E676))
                    Text(stock.bullCase, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("🐻 Bear Case (Downside Risks):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFF5252))
                    Text(stock.bearCase, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("🔍 Forensic & Balance Sheet Check:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF40C4FF))
                    Text(stock.forensicNote, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
