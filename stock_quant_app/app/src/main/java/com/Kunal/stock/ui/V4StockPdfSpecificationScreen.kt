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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SpecSection(
    val title: String,
    val subtitle: String,
    val iconName: String,
    val tag: String,
    val content: String,
    val codeSnippet: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V4StockPdfSpecificationScreen() {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("ALL", "PROMPTS 0-21", "10 SETUPS", "TRAFFIC LIGHT", "LONG-TERM", "PLANNER")

    val sections = remember {
        listOf(
            SpecSection(
                title = "Part A1: Executive Summary & 3 Time Horizons",
                subtitle = "Swing (up to 5 days), Positional (up to 22 days), Long-Term (3-5 years)",
                iconName = "Layers",
                tag = "OVERVIEW",
                content = """
                    • SWING (up to 5 trading days):
                      Under v4 (18-Nov-2026), uses ONLY 2 setups when Nifty 50 is above SMA200:
                      - Setup 1 Crash Dip: Strong uptrend stock (RAM rank >= 50) drops >= 8% in 3 days.
                      - Setup 2 Panic Dip in Leader: High Technical Score (>= 70) with RSI(2) panic (< 5, sum < 10, or RAM >= 80 & RSI < 10).
                      - Exit: Next open after first close above previous day's high, OR close of day 5. Stop: 8% below entry.
                      
                    • POSITIONAL (up to 22 trading days):
                      Uses all 10 scanner setups (7 dip + 3 trend setups).
                      - Stop: 8% below entry.
                      - Exit: Close of day 22 (or trailing stop after +8% profit).
                      - Priority: Ranked by 6-factor Technical Score.
                      
                    • LONG-TERM (3 to 5 years):
                      Uses Screener.in quality gate: 5-yr ROCE >= 15% (no year < 10%), sales & PAT growth >= 10%, D/E <= 0.5, CFO >= 70% of PAT.
                      - Buy timing: 3 tranches over 3 months on dips inside primary uptrend.
                """.trimIndent(),
                codeSnippet = """
                    # v4 Time Horizons & Exit Rules
                    SWING_MAX_DAYS = 5
                    SWING_EXIT = "Close > Previous Day High OR Day 5 Close"
                    POSITIONAL_MAX_DAYS = 22
                    STOP_LOSS_PCT = 0.08  # 8% hard stop
                    TRAILING_TRIGGER = 0.08  # +8% activates trailing
                    TRAILING_STEP = 0.02  # 2% below highest high
                """.trimIndent()
            ),
            SpecSection(
                title = "Part C4: Market Traffic Light (5 States)",
                subtitle = "Regime detection controlling sizing multiplier and paper-trading gate",
                iconName = "Traffic",
                tag = "TRAFFIC LIGHT",
                content = """
                    The Market Traffic Light inspects Nifty 50, India VIX, and Liquid Universe Breadth:
                    
                    • State 1 [GREEN, Size 1.0]:
                      Nifty > SMA200 AND Nifty > SMA50. Full bullish trending market.
                      
                    • State 2 [AMBER, Size 1.0]:
                      Nifty > SMA200 AND Nifty < SMA50. Bullish primary trend, healthy pullback.
                      
                    • State 3 [AMBER, Size 0.5]:
                      Nifty < SMA200 AND Nifty > SMA50. Counter-trend rally. Halve position sizing.
                      
                    • State 4 [AMBER, Size 0.5]:
                      Nifty < both AND Panic (India VIX >= 22 OR Market Breadth < 30%).
                      Capitulation wash opportunity - dip buys allowed at half size.
                      
                    • State 5 [RED, Size 0.0]:
                      Nifty < both AND NO Panic. Bearish grind. WAIT! Paper trade only.
                """.trimIndent(),
                codeSnippet = """
                    if nifty > sma200 and nifty > sma50:
                        state, color, size = 1, "GREEN", 1.0
                    elif nifty > sma200 and nifty < sma50:
                        state, color, size = 2, "AMBER", 1.0
                    elif nifty < sma200 and nifty > sma50:
                        state, color, size = 3, "AMBER", 0.5
                    elif nifty < sma200 and nifty < sma50 and (vix >= 22 or breadth < 30):
                        state, color, size = 4, "AMBER", 0.5
                    else:
                        state, color, size = 5, "RED", 0.0  # WAIT
                """.trimIndent()
            ),
            SpecSection(
                title = "Part C3 & F4: All 10 Scanner Setups & Chartink Queries",
                subtitle = "Base filter + 7 Dip setups + 3 Trend setups",
                iconName = "FilterList",
                tag = "10 SETUPS",
                content = """
                    BASE FILTER (Mandatory):
                    Close >= 50 AND Traded Value(20) >= Rs 5 Crore AND Close > SMA200.
                    
                    THE 10 SETUPS:
                    1. Setup A (S09): RSI(2) < 5
                    2. Setup B (P03): Pullback to rising 50-day SMA (within 2% of SMA50)
                    3. Setup C (S17): 3M return rank >= 90 pausing at EMA10
                    4. Setup D (S10): Crash Dip: Close <= 0.92 * Close 3 days ago (>=8% drop)
                    5. Setup N01: Bollinger Band stretch: Z20 <= -2.0
                    6. Setup N03: 2-day RSI panic: RSI(2)_today + RSI(2)_yesterday < 10.0
                    7. Setup N04: 4 consecutive lower closes with oversold RSI
                    8. Setup N08: Leader in panic dip: RAM rank >= 80 AND RSI(2) < 10.0
                    9. Setup N09: Leader bounces at EMA20: RAM rank >= 70, Low touches EMA20, Close > EMA20
                    10. Setup N18: Hammer at 10-day low: Lower shadow >= 2x body, Low <= Low10
                """.trimIndent(),
                codeSnippet = """
                    // Chartink Query Examples:
                    // Setup A (S09):
                    [0] 15 minute close > [0] 200 daily sma AND [0] 2 daily rsi < 5
                    
                    // Setup D (S10 Crash Dip):
                    [0] close <= 0.92 * [-3] close AND [0] close > [0] 200 daily sma
                    
                    // Setup N08 (Leader Panic Dip):
                    [0] 2 daily rsi < 10 AND [0] close > [0] 200 daily sma
                """.trimIndent()
            ),
            SpecSection(
                title = "Part C5: The 6-Factor Technical Score",
                subtitle = "Plain average of 6 percentile ranks inside tonight's candidate pool",
                iconName = "Analytics",
                tag = "PLANNER",
                content = """
                    The Technical Score (0 to 100) ranks candidates to decide who gets trade slots:
                    1. Quick Score Percentile: (RAM rank + 6-month return rank) / 2
                    2. ADX(14) Percentile: Trend strength
                    3. SMA200 Slope (20-day) Percentile: Primary long-term trend direction
                    4. Sector Breadth Percentile: % of sector stocks above SMA50
                    5. Up/Down Volume Ratio (50-day) Percentile: Accumulation vs distribution
                    6. 5-Day Return Percentile (INVERTED): Deepest drop gets rank 100 (lower return is better!)
                    
                    Formula: Technical Score = (Rank1 + Rank2 + Rank3 + Rank4 + Rank5 + Rank6) / 6.0
                """.trimIndent(),
                codeSnippet = """
                    def compute_technical_score(df):
                        r_quick = df['quick_score'].rank(pct=True) * 100
                        r_adx = df['adx14'].rank(pct=True) * 100
                        r_slope = df['sma200_slope'].rank(pct=True) * 100
                        r_sec = df['sector_breadth'].rank(pct=True) * 100
                        r_vol = df['up_down_vol'].rank(pct=True) * 100
                        r_dip = df['ret5'].rank(pct=True, ascending=False) * 100 # lower is better
                        return (r_quick + r_adx + r_slope + r_sec + r_vol + r_dip) / 6.0
                """.trimIndent()
            ),
            SpecSection(
                title = "Part C6 & C7: Trade Planner & Risk Allocation",
                subtitle = "10 open positions, 1% risk per trade, 8% stop, results veto",
                iconName = "AccountBalance",
                tag = "PLANNER",
                content = """
                    CONSTRAINTS & ALLOCATION RULES:
                    • Max Open Positions: 10 total (up to 4 Swing + up to 6 Positional)
                    • Sector Exposure Limit: Maximum 2 open trades per sector
                    • Nightly Deployment Limit: Maximum 3 new trades per morning
                    • Results Veto: Skip any stock reporting earnings within 2 days
                    • Max Entry Gap: If open gaps up > 3% above signal close, do not enter
                    
                    POSITION SIZING:
                    • Capital: Rs 10,00,000 (10 Lakhs)
                    • Risk per Trade: 1.0% = Rs 10,000 maximum loss
                    • Hard Stop Loss: 8% below entry price
                    • Quantity = floor(Risk_Rs / (0.08 * Entry_Price))
                    • Multiplied by Traffic Light Multiplier (1.0x, 0.5x, 0x)
                """.trimIndent(),
                codeSnippet = """
                    capital = 1_000_000.0
                    risk_rs = capital * 0.01  # Rs 10,000
                    stop_pct = 0.08  # 8%
                    qty = math.floor((risk_rs * traffic_multiplier) / (entry_price * stop_pct))
                    stop_price = round(entry_price * (1 - stop_pct), 2)
                """.trimIndent()
            ),
            SpecSection(
                title = "Part A1 & C8: Long-Term Compounder Engine (3-5 Years)",
                subtitle = "6-Rule Screener.in Quality Gate, 5-Part 100pt Score, 3-Tranche Timing",
                iconName = "Diamond",
                tag = "LONG-TERM",
                content = """
                    6-RULE QUALITY GATE:
                    1. ROCE 5-Year Average >= 15%
                    2. ROCE Minimum Single Year >= 10% (consistent quality)
                    3. 3-Year Sales Growth >= 10% CAGR
                    4. 3-Year PAT Growth >= 10% CAGR
                    5. Debt / Equity <= 0.50 (low debt or zero debt)
                    6. Operating Cash Flow (CFO) >= 70% of Net Profit (no paper profits)
                    
                    5-PART 100-POINT SCORING:
                    • Business Quality: 30 points
                    • Growth Trajectory: 25 points
                    • Financial Strength: 15 points
                    • Valuation Comfort: 15 points
                    • Technical Trend: 15 points (Close > SMA200, positive slope)
                    
                    3-TRANCHE BUY SCHEDULE:
                    Accumulate position in 3 equal parts over 3 months on dips inside primary uptrend.
                """.trimIndent(),
                codeSnippet = """
                    quality_pass = (
                        roce_5yr >= 15.0 and
                        roce_min >= 10.0 and
                        sales_gr >= 10.0 and
                        pat_gr >= 10.0 and
                        de_ratio <= 0.5 and
                        cfo_to_pat >= 0.70
                    )
                """.trimIndent()
            ),
            SpecSection(
                title = "Part D3: Prompts 0 to 21 Complete Build Blueprint",
                subtitle = "Exact prompts used to generate the entire system modules",
                iconName = "MenuBook",
                tag = "PROMPTS 0-21",
                content = """
                    THE 22 BUILD PROMPTS:
                    • Prompt 0: Project Brief & Architecture Roadmap
                    • Prompt 1: Config, Settings, SQLite DB (10 tables) & System Check
                    • Prompt 2: Nifty 500 Daily Price Ingestion (Safe Replace, 15:45 Candle Rule)
                    • Prompt 3: NSE Delivery Percentage & Earnings Results Calendar Veto
                    • Prompt 4: Technical Indicators (Wilder RSI2/RSI14, ATR14, Z20, RAM, Supertrend)
                    • Prompt 5: Daily Universe Ranks (RAM rank, 6M rank, Quick Score, Breadth)
                    • Prompt 6: The 10 Quantitative Scanners (Setups A, B, C, D, N01, N03, N04, N08, N09, N18)
                    • Prompt 7: Market Traffic Light (5 States, INFO vs STRICT sizing)
                    • Prompt 8: The 6-Factor Technical Score Engine
                    • Prompt 9: Trade Planner (Limits, Sector Caps, 1% Risk, 8% Stop Sizing)
                    • Prompt 10: Trade Management, Trailing Stops (+8% trigger) & Exit Engine
                    • Prompt 11: Nightly Reports (Word .docx, CSV & Markdown)
                    • Prompt 12: Nightly Runner Script & Pipeline Orchestrator
                    • Prompt 13: Screener.in Fundamentals Download & Parser (6-7s polite pace)
                    • Prompt 14: Long-Term Quality Gate, 5-Part Scoring & Monthly Dossier
                    • Prompt 15: AI Finalist Reviewer (News, Forensic Red Flags, Results Check)
                    • Prompt 16: AI 7-Analyst Deep Research (Bull, Bear, Fact Check, Conviction)
                    • Prompt 17: Interactive Streamlit Web UI (6 Tabs)
                    • Prompt 18: Performance Scoreboard (Forward returns vs Nifty 50)
                    • Prompt 19: Portfolio Backtester (17.2% CAGR target, 60% win rate)
                    • Prompt 20: Live Deployment Readiness Monitor (6-month paper trade check)
                    • Prompt 21: v4 Swing Revision (Setup 1 Crash Dip & Setup 2 Panic Dip in Leader)
                """.trimIndent(),
                codeSnippet = """
                    # Complete Prompt Sequence Verified:
                    # Prompts 0 through 21 fully implemented in stockapp/ and stock_quant_app!
                """.trimIndent()
            )
        )
    }

    val filteredSections = sections.filter { sec ->
        (selectedCategory == "ALL" || sec.tag == selectedCategory) &&
        (searchQuery.isEmpty() || sec.title.contains(searchQuery, ignoreCase = true) || sec.content.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search & Filter Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Prompts 0-21, 10 Setups, Rules...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 0.dp
                ) {
                    categories.forEach { cat ->
                        Tab(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            text = {
                                Text(
                                    cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        // List of Sections
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredSections) { sec ->
                SpecCard(sec)
            }
        }
    }
}

@Composable
fun SpecCard(section: SpecSection) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = section.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = section.tag,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = section.content,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (section.codeSnippet != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "PYTHON / SYSTEM IMPLEMENTATION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = section.codeSnippet,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
