package com.Kunal.stock.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Kunal.stock.data.StockScanItem
import com.Kunal.stock.data.UiState
import java.util.Locale
import kotlin.math.floor

@Composable
fun StockLabScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Research", "Portfolio Risk", "Backtest")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        Crossfade(targetState = selectedTab, label = "StockLabTabTransition") { tab ->
            when (tab) {
                0 -> StockResearchTab()
                1 -> PortfolioRiskTab()
                else -> BacktestScreen()
            }
        }
    }
}

@Composable
private fun StockResearchTab() {
    val viewModel: StockScanViewModel = viewModel()
    val scanState by viewModel.allScans.collectAsState()
    val isTriggering by viewModel.isTriggering.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStock by remember { mutableStateOf<StockScanItem?>(null) }

    if (selectedStock != null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { selectedStock = null }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Research results")
            }
            StockDeepDiveCard(selectedStock!!)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Technical research from the latest Stock Scanner snapshot. Company fundamentals and news are not integrated in this view.",
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Find a scanned symbol") }
        )
        Spacer(Modifier.height(8.dp))
        when (val state = scanState) {
            UiState.Loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text("Loading scan research", modifier = Modifier.padding(top = 8.dp))
            }
            is UiState.Error -> Text(
                state.message,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
            is UiState.Success<*> -> {
                val scanItems = (state.data as? List<*>)
                    ?.filterIsInstance<StockScanItem>()
                    .orEmpty()
                val matches = scanItems.filter {
                    query.isBlank() || it.symbol.contains(query.trim(), ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(36.dp))
                        Text("No matching scan rows.", modifier = Modifier.padding(8.dp))
                        Button(
                            onClick = { viewModel.triggerLiveScan() },
                            enabled = !isTriggering
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(if (isTriggering) " Requesting scan…" else " Run live scan")
                        }
                    }
                } else {
                    Text(
                        "${matches.size} scan records · select one for technical details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(matches, key = { "${it.symbol}-${it.barDate}-${it.pattern}" }) { stock ->
                            ResearchStockRow(stock) { selectedStock = stock }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchStockRow(stock: StockScanItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stock.symbol.removeSuffix(".NS"), fontWeight = FontWeight.Bold)
                Text(
                    listOf(stock.sector, stock.pattern).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { "No pattern label" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stock.close.takeIf { it > 0 }?.let { "₹${String.format(Locale.US, "%.2f", it)}" } ?: "Price N/A",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${String.format(Locale.US, "%+.2f", stock.changePct)}% · Score ${stock.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stock.changePct >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private data class RiskPositionDraft(
    val symbol: String = "",
    val entry: String = "",
    val stop: String = "",
    val quantity: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioRiskTab() {
    var capital by rememberSaveable { mutableStateOf("100000") }
    var riskPercent by rememberSaveable { mutableStateOf("1") }
    val positions = remember { mutableStateListOf(RiskPositionDraft()) }

    val capitalValue = capital.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    val riskPctValue = riskPercent.toDoubleOrNull()?.takeIf { it in 0.01..100.0 } ?: 0.0
    val portfolioRiskLimit = capitalValue * riskPctValue / 100.0
    val totalExposure = positions.sumOf { position ->
        val entry = position.entry.toDoubleOrNull() ?: 0.0
        val quantity = position.quantity.toDoubleOrNull() ?: 0.0
        if (entry > 0 && quantity > 0) entry * quantity else 0.0
    }
    val totalStopRisk = positions.sumOf { position ->
        val entry = position.entry.toDoubleOrNull() ?: 0.0
        val stop = position.stop.toDoubleOrNull() ?: 0.0
        val quantity = position.quantity.toDoubleOrNull() ?: 0.0
        if (entry > stop && stop > 0 && quantity > 0) (entry - stop) * quantity else 0.0
    }
    val positionsWithQuantity = positions.count { (it.quantity.toDoubleOrNull() ?: 0.0) > 0 }
    val invalidStopCount = positions.count {
        val quantity = it.quantity.toDoubleOrNull() ?: 0.0
        val entry = it.entry.toDoubleOrNull() ?: 0.0
        val stop = it.stop.toDoubleOrNull() ?: 0.0
        quantity > 0 && (entry <= 0 || stop <= 0 || stop >= entry)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Position sizing & portfolio stop risk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Enter your own holdings and stop levels. This calculator is local to this screen and does not place trades.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RiskNumberField("Capital (₹)", capital, { capital = it }, Modifier.weight(1f))
            RiskNumberField("Risk limit (%)", riskPercent, { riskPercent = it }, Modifier.weight(1f))
        }
        positions.forEachIndexed { index, position ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Position ${index + 1}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (positions.size > 1) {
                            FilterChip(
                                selected = false,
                                onClick = { positions.remove(position) },
                                label = { Text("Remove") }
                            )
                        }
                    }
                    RiskNumberField(
                        "Symbol / note",
                        position.symbol,
                        { positions[index] = position.copy(symbol = it) },
                        Modifier.fillMaxWidth(),
                        KeyboardType.Text
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RiskNumberField("Entry (₹)", position.entry, { positions[index] = position.copy(entry = it) }, Modifier.weight(1f))
                        RiskNumberField("Stop (₹)", position.stop, { positions[index] = position.copy(stop = it) }, Modifier.weight(1f))
                        RiskNumberField(
                            "Shares",
                            position.quantity,
                            { positions[index] = position.copy(quantity = it) },
                            Modifier.weight(1f),
                            KeyboardType.Number
                        )
                    }
                    val entry = position.entry.toDoubleOrNull() ?: 0.0
                    val stop = position.stop.toDoubleOrNull() ?: 0.0
                    val shareRisk = entry - stop
                    if (entry > 0 && stop > 0 && shareRisk > 0 && portfolioRiskLimit > 0) {
                        val allocatedBudget = portfolioRiskLimit / positions.size
                        val suggestedShares = minOf(
                            floor(allocatedBudget / shareRisk),
                            floor(capitalValue / positions.size / entry)
                        ).toLong().coerceAtLeast(0)
                        val stopDistancePct = (shareRisk / entry) * 100.0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Budget ₹${String.format(Locale.US, "%,.0f", allocatedBudget)} · Max $suggestedShares shares",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (suggestedShares > 0) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            positions[index] = position.copy(quantity = suggestedShares.toString())
                                        },
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        "⚡ Auto-Fill ($suggestedShares)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Circuit risk warning
                        if (stopDistancePct > 20.0) {
                            Text(
                                "🚨 Stop is ${String.format(Locale.US, "%.1f", stopDistancePct)}% below entry — exceeds standard 20% single-day circuit limit.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (stopDistancePct > 10.0) {
                            Text(
                                "⚠️ Wide stop (${String.format(Locale.US, "%.1f", stopDistancePct)}%). Beware of 10% daily circuit limits on mid/small-caps.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else if (position.entry.isNotBlank() && position.stop.isNotBlank()) {
                        Text(
                            "Entry and stop must be positive, with stop below entry, to size a long position.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Button(onClick = {
            positions.add(RiskPositionDraft())
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" Add position")
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Portfolio summary", fontWeight = FontWeight.Bold)
                Divider()
                RiskSummaryRow("Risk limit", "₹${String.format(Locale.US, "%,.2f", portfolioRiskLimit)}")
                RiskSummaryRow("Risk at entered stops", "₹${String.format(Locale.US, "%,.2f", totalStopRisk)}")
                RiskSummaryRow("Entered exposure", "₹${String.format(Locale.US, "%,.2f", totalExposure)}")
                val exposurePercent = if (capitalValue > 0) totalExposure * 100.0 / capitalValue else 0.0
                RiskSummaryRow("Capital deployed", "${String.format(Locale.US, "%.1f", exposurePercent)}%")
                val overLimit = totalStopRisk > portfolioRiskLimit && portfolioRiskLimit > 0
                val overCapital = totalExposure > capitalValue && capitalValue > 0
                Text(
                    when {
                        capitalValue <= 0 || riskPctValue <= 0 -> "Enter valid positive capital and a risk limit from 0.01% to 100%."
                        positionsWithQuantity == 0 -> "Enter share quantities to calculate current portfolio exposure and stop risk."
                        invalidStopCount > 0 -> "One or more positions has an invalid stop; its risk is not included in the total."
                        overLimit -> "Entered stop risk is above the configured portfolio risk limit."
                        overCapital -> "Entered exposure exceeds the capital entered; check the inputs or account for leverage."
                        else -> "Entered stop risk is within the configured limit."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overLimit || overCapital || invalidStopCount > 0)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Sizing is a mathematical estimate only. Gaps, slippage, liquidity, fees, and changing prices can make actual losses larger.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RiskNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun RiskSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
