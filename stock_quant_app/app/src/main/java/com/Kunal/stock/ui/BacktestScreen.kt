package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Kunal.stock.data.*
import com.Kunal.stock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    viewModel: BacktestViewModel = viewModel()
) {
    val backtestCategory by viewModel.backtestCategory.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val strategyType by viewModel.strategyType.collectAsState()
    val targetPct by viewModel.targetPct.collectAsState()
    val stopLossPct by viewModel.stopLossPct.collectAsState()
    val atrMultiplier by viewModel.atrMultiplier.collectAsState()
    val timeframe by viewModel.timeframe.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Strategy Specs, 1: Trade Log

    val isOptionMode = backtestCategory == "OPTION"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. Header Title ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Quantitative Strategy Lab",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isOptionMode) "Derivatives Flow • Greeks • Time-Window Reversals" else "Equity Price Action • Volume Breakouts • Swing Trading Strategies",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = ElectricBlue.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOptionMode) Icons.Default.Bolt else Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOptionMode) "OPTION QUANT" else "STOCK QUANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 1B. Dual-Mode Switcher: Stock vs Option Backtesting ---
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = if (isOptionMode) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = !isOptionMode,
                    onClick = { viewModel.setCategory("STOCK") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stock Strategy Lab", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = isOptionMode,
                    onClick = { viewModel.setCategory("OPTION") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Option Strategy Lab", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. Strategy Configuration Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOptionMode) "Option Strategy Model & Parameters" else "Equity Strategy Model & Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Asset Selector
                Text(
                    text = if (isOptionMode) "Derivatives Asset (Indices & F&O)" else "Stock Symbol",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableSymbols = if (isOptionMode) {
                        listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "RELIANCE", "HDFCBANK")
                    } else {
                        listOf("RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "TATAMOTORS", "BHARTIARTL", "SBIN", "LT", "ITC")
                    }
                    availableSymbols.forEach { sym ->
                        val isSelected = selectedSymbol == sym
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSymbol(sym) },
                            label = { Text(sym, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.dp
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Strategy Archetypes
                Text(
                    text = if (isOptionMode) "Option Algorithmic Archetype" else "Equity Strategy Archetype",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val strategies = if (isOptionMode) {
                        listOf(
                            "MULTI_CONFLUENCE_QUANT" to "⚡ 3-Pillar Master Option Confluence",
                            "INTRADAY_TIMING_CANDLE_REVERSAL" to "⏰ Intraday Timing & S&R Reversal",
                            "OPTION_FLOW_GRAVITY" to "🌊 Option Flow & Max Pain Gravity",
                            "GAMMA_SQUEEZE_EXPIRY" to "💥 Expiry High Gamma Squeeze (Hero-Zero)",
                            "CANDLE_VOLATILITY_SQUEEZE" to "🎯 ATM Option Volatility Squeeze",
                            "FAKE_BREAKOUT_TRAP_OPTION" to "🛡️ Fake Breakout Bull/Bear Trap Trade"
                        )
                    } else {
                        listOf(
                            "SWING_BREAKOUT" to "🚀 20-Day Swing Breakout & VCP",
                            "EMA_TREND_FOLLOWING" to "📈 EMA 20/50 Trend Following",
                            "RVOL_MOMENTUM_SURGE" to "⚡ RVOL High Volume Surge",
                            "SQUEEZE_BREAKOUT" to "🎯 Bollinger Volatility Squeeze",
                            "PRICE_ACTION_STRUCTURE" to "🏛️ Price Action (W-Pattern)",
                            "MULTI_CONFLUENCE_STOCK" to "🔥 Master Equity Confluence Quant"
                        )
                    }
                    strategies.forEach { (type, label) ->
                        val isSelected = strategyType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.applyPreset(type) },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.dp
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Target % Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOptionMode) "Option Premium Target" else "Take Profit Target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "+${String.format("%.1f", targetPct)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen
                    )
                }
                Slider(
                    value = targetPct,
                    onValueChange = { viewModel.setTarget(it) },
                    valueRange = if (isOptionMode) 10.0f..150.0f else 0.5f..15.0f,
                    steps = if (isOptionMode) 14 else 14,
                    colors = SliderDefaults.colors(
                        thumbColor = BullishGreen,
                        activeTrackColor = BullishGreen
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Stop Loss % Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOptionMode) "Option Premium Stop Loss" else "Stop Loss Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "-${String.format("%.1f", stopLossPct)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BearishRed
                    )
                }
                Slider(
                    value = stopLossPct,
                    onValueChange = { viewModel.setStopLoss(it) },
                    valueRange = if (isOptionMode) 5.0f..40.0f else 0.5f..6.0f,
                    steps = if (isOptionMode) 7 else 11,
                    colors = SliderDefaults.colors(
                        thumbColor = BearishRed,
                        activeTrackColor = BearishRed
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ATR Volatility Multiplier Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ATR Dynamic Volatility Trailing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format("%.1f", atrMultiplier)}x ATR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldWarning
                    )
                }
                Slider(
                    value = atrMultiplier,
                    onValueChange = { viewModel.setAtrMultiplier(it) },
                    valueRange = 1.0f..3.0f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldWarning,
                        activeTrackColor = GoldWarning
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Timeframe Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lookback Window",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1y", "2y", "5y").forEach { tf ->
                            val isSelected = timeframe == tf
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setTimeframe(tf) },
                                label = { Text(tf.uppercase()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = ElectricBlue,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Run Simulation Button
                Button(
                    onClick = { viewModel.runBacktest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSimulating,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSimulating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Computing Quantitative Simulation...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Quantitative Backtest", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- 3. Results Section ---
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Streaming quant backtest analytics...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is UiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.runBacktest() }) {
                            Text("Run Quant Simulation")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val result = state.data
                QuantBacktestResultView(
                    result = result,
                    activeTab = activeTab,
                    onTabChange = { activeTab = it }
                )
            }
        }
    }
}

@Composable
fun QuantBacktestResultView(
    result: BacktestResult,
    activeTab: Int,
    onTabChange: (Int) -> Unit
) {
    val isProfit = result.totalReturn >= 0
    val pnlColor = if (isProfit) BullishGreen else BearishRed

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero Performance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${result.symbol} • ${result.strategyName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%+.2f%%", result.totalReturn),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = pnlColor
                        )
                        Text(
                            text = "Net Cumulative P&L",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Win Rate Circle Badge
                    Surface(
                        color = if (result.winRate >= 50.0) BullishGreenContainer else BearishRedContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (result.winRate >= 50.0) BullishGreen else BearishRed)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${String.format("%.1f", result.winRate)}%",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = if (result.winRate >= 50.0) BullishGreen else BearishRed
                            )
                            Text(
                                text = "Win Rate",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (result.winRate >= 50.0) BullishGreen else BearishRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Quick Win vs Loss Bar
                val total = maxOf(1, result.wins + result.losses)
                val winRatio = result.wins.toFloat() / total

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${result.wins} Wins",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullishGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${result.losses} Losses",
                        style = MaterialTheme.typography.labelSmall,
                        color = BearishRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(maxOf(0.01f, winRatio))
                            .fillMaxHeight()
                            .background(BullishGreen)
                    )
                    Box(
                        modifier = Modifier
                            .weight(maxOf(0.01f, 1f - winRatio))
                            .fillMaxHeight()
                            .background(BearishRed)
                    )
                }
            }
        }

        // --- Quantitative Ratios Grid 1: Sharpe & Sortino ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Sharpe Ratio",
                value = String.format("%.2f", result.sharpeRatio),
                subtitle = if (result.sharpeRatio >= 1.0) "Excellent Alpha" else if (result.sharpeRatio >= 0) "Moderate" else "Sub-optimal",
                valueColor = if (result.sharpeRatio >= 1.0) BullishGreen else if (result.sharpeRatio >= 0) ElectricBlue else BearishRed,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Sortino Ratio",
                value = String.format("%.2f", result.sortinoRatio),
                subtitle = "Downside Risk-Adjusted",
                valueColor = if (result.sortinoRatio >= 1.0) BullishGreen else BearishRed,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Quantitative Ratios Grid 2: Profit Factor & Drawdown ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Profit Factor",
                value = String.format("%.2f", result.profitFactor),
                subtitle = if (result.profitFactor >= 1.2) "Profitable Edge" else "Low Margin",
                valueColor = if (result.profitFactor >= 1.0) BullishGreen else BearishRed,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Max Drawdown",
                value = "-${String.format("%.1f", result.maxDrawdown)}%",
                subtitle = "Peak-to-Trough",
                valueColor = BearishRed,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Quantitative Ratios Grid 3: Calmar & Expectancy ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Calmar Ratio",
                value = String.format("%.2f", result.calmarRatio),
                subtitle = "Return / Drawdown",
                valueColor = if (result.calmarRatio >= 1.0) BullishGreen else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Trade Expectancy",
                value = String.format("%+.2f%%", result.expectancy),
                subtitle = "Math Edge per Trade",
                valueColor = if (result.expectancy >= 0) BullishGreen else BearishRed,
                modifier = Modifier.weight(1f)
            )
        }

        // Tab Selector (Overview vs Trade Log)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { onTabChange(0) },
                    text = { Text("Strategy Specs", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { onTabChange(1) },
                    text = { Text("Simulated Trades (${result.trades.size})", fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Live Market Regime Recommendation Banner
        val recColor = if (result.blueprint.isRecommendedLive) BullishGreen else GoldWarning
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = recColor.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, recColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (result.blueprint.isRecommendedLive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = recColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (result.blueprint.isRecommendedLive) "RECOMMENDED FOR LIVE MARKET NOW" else "USE CAUTION IN LIVE MARKET",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = recColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = result.blueprint.regimeCommentary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (activeTab == 0) {
            // Strategy Specs Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quantitative Engine Blueprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SpecRow("Take Profit Target", "+${result.targetPct}%")
                    SpecRow("Stop Loss Threshold", "-${result.stopLossPct}%")
                    SpecRow("ATR Dynamic Trailing", "${result.atrMultiplier}x Volatility")
                    SpecRow("Risk / Reward Ratio", "1 : ${String.format("%.1f", result.targetPct / maxOf(0.1, result.stopLossPct))}")
                    SpecRow("Max Win Streak", "${result.maxConsecutiveWins} trades")
                    SpecRow("Max Loss Streak", "${result.maxConsecutiveLosses} trades")
                    SpecRow("Backtest Lookback", result.timeframe.uppercase())
                    SpecRow("Engine Status", "Calibrated (Quant Math V3)")
                }
            }

            FullStrategyBlueprintCard(result.blueprint)
        } else {
            // Trade History Log List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.trades.isEmpty()) {
                    Text(
                        text = "No individual trade records captured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    result.trades.forEach { trade ->
                        QuantTradeItemCard(trade)
                    }
                }
            }
        }
    }
}

@Composable
fun QuantTradeItemCard(trade: TradeRecord) {
    val isWin = trade.isWin
    val returnColor = if (isWin) BullishGreen else BearishRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(0.5.dp, returnColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isWin) BullishGreenContainer else BearishRedContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = trade.type,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = returnColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${trade.entryDate} → ${trade.exitDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = String.format("%+.2f%%", trade.returnPct),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = returnColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Trigger & Exit details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹%,.2f → ₹%,.2f (%d days)".format(trade.entryPrice, trade.exitPrice, trade.holdingDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = trade.exitReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trade.patternTrigger.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = ElectricBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, ElectricBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = trade.patternTrigger,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FullStrategyBlueprintCard(blueprint: StrategyBlueprint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quantitative Engine Blueprint: ${blueprint.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ElectricBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = blueprint.mathematicalEdge.ifBlank { "Exploits volatility compression and option seller positioning." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Strategy Execution Rules", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            for (rule in blueprint.rules) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", fontWeight = FontWeight.Bold, color = ElectricBlue)
                    Text(rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Core Indicators", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (ind in blueprint.indicators) {
                    MetricPill("Indicator", ind)
                }
            }
        }
    }
}
