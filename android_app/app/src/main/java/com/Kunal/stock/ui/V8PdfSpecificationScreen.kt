package com.Kunal.stock.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Kunal.stock.data.OptionSentimentData

// ── Theme Colors ─────────────────────────────────────────────────────────────
private val BgDark       = Color(0xFF0D0A1E)
private val CardBg       = Color(0xFF161338)
private val CardBg2      = Color(0xFF1E1A48)
private val CyanNeon     = Color(0xFF00DFD8)
private val VioletNeon   = Color(0xFF7B2FBE)
private val GreenBull    = Color(0xFF00E676)
private val RedBear      = Color(0xFFFF5252)
private val GoldWarn     = Color(0xFFFFD600)
private val TextWhite    = Color(0xFFF5F3FF)
private val TextMuted    = Color(0xFFA79BC4)

// ── Data Classes for PDF Specification ───────────────────────────────────────
data class PdfPrompt(
    val number: Int,
    val title: String,
    val category: String, // SETUP, MATH, PATTERN, ENGINE, V8_FEATURE
    val objective: String,
    val formulasAndRules: String,
    val codeSnippet: String
)

data class ChartPatternSpec(
    val name: String,
    val type: String, // BULLISH, BEARISH, NEUTRAL, SQUEEZE
    val triggerCondition: String,
    val stopLossRule: String,
    val promptRef: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V8PdfSpecificationScreen(
    currentData: OptionSentimentData? = null
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("📜 20 Prompts", "🎯 16 Patterns", "⚡ Setups", "🪜 Ladder & v8", "💻 Code")

    val allPrompts = remember { getPdfPromptsList() }
    val allPatterns = remember { getChartPatternsList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top Header Banner
        Surface(
            color = CardBg,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(0.5.dp, VioletNeon.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Option App v8 — PDF Specification",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "57-Page Architecture, 20 Prompts, 16 Patterns & Code",
                            color = CyanNeon,
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        color = GreenBull.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GreenBull)
                    ) {
                        Text(
                            "100% HEALTHY",
                            color = GreenBull,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Sub-tabs Row
                ScrollableTabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = Color.Transparent,
                    contentColor = CyanNeon,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    subTabs.forEachIndexed { idx, title ->
                        val isSel = selectedSubTab == idx
                        Tab(
                            selected = isSel,
                            onClick = { selectedSubTab = idx },
                            text = {
                                Text(
                                    title,
                                    color = if (isSel) CyanNeon else TextMuted,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }
        }

        // SubTab Content
        when (selectedSubTab) {
            0 -> PromptsTabContent(allPrompts)
            1 -> ChartPatternsTabContent(allPatterns, currentData?.spotPrice ?: 24200.0)
            2 -> SetupsTabContent()
            3 -> CapitalLadderAndV8FeaturesContent()
            4 -> SourceCodeTabContent()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-TAB 0: ALL 20 SPECIFICATION PROMPTS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun PromptsTabContent(prompts: List<PdfPrompt>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Complete Sequential Specification: Prompts 1 through 20",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        items(prompts) { p ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (expanded) CyanNeon else CardBg2)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = VioletNeon.copy(alpha = 0.25f),
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp),
                                border = BorderStroke(1.dp, VioletNeon)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${p.number}", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(p.title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(p.category, color = GoldWarn, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        Divider(color = CardBg2, thickness = 0.5.dp)
                        Spacer(Modifier.height(8.dp))

                        Text("🎯 Objective:", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(p.objective, color = TextWhite, fontSize = 12.sp)

                        Spacer(Modifier.height(6.dp))
                        Text("📐 Key Rules & Formulas:", color = GoldWarn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(p.formulasAndRules, color = TextMuted, fontSize = 11.sp)

                        Spacer(Modifier.height(8.dp))
                        Text("💻 Implementation Code Snippet:", color = GreenBull, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Surface(
                            color = BgDark,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                p.codeSnippet,
                                color = Color(0xFF80CBC4),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-TAB 1: 16 CANDLESTICK & CHART PATTERNS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChartPatternsTabContent(patterns: List<ChartPatternSpec>, currentSpot: Double) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("16 Candlestick & Chart Pattern Identifiers", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Part C3 mathematical pattern definitions with exact triggers & stop-losses.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        items(patterns) { pat ->
            val color = when (pat.type) {
                "BULLISH" -> GreenBull
                "BEARISH" -> RedBear
                "SQUEEZE" -> GoldWarn
                else -> CyanNeon
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(pat.name, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(pat.type, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Text("Trigger: ${pat.triggerCondition}", color = TextWhite, fontSize = 11.sp)
                    Text("Stop-Loss: ${pat.stopLossRule}", color = TextMuted, fontSize = 11.sp)
                    Text("Ref: ${pat.promptRef}", color = VioletNeon, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-TAB 2: SETUPS MATRIX
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SetupsTabContent() {
    val setups = listOf(
        Triple("Sensex Expiry Condor", "LIVE | Thursday 09:16 | 0.5% Short + 1.5% Long Wings", "Stop 3.0x on sold leg | Exit 15:15 | Calm Mode"),
        Triple("Nifty Condor A", "LIVE | Tuesday 09:16 | 0.5% Short + 1.5% Long Wings", "Stop 4.0x on sold leg | Exit 15:15 | Low-VIX Skip"),
        Triple("Nifty Condor B", "LIVE | Tuesday 09:16 | 0.5% Short + 2.0% Long Wings", "Stop 4.0x on sold leg | Exit 15:15 | Low-VIX Skip"),
        Triple("Bank Nifty Iron Fly", "PAPER | Monthly 09:20 | ATM Straddle + 1.5% Wings", "Defined wing stop | Exit 15:15"),
        Triple("Fin Nifty Iron Fly", "PAPER | Monthly 09:20 | ATM Straddle + 1.0% Wings", "Defined wing stop | Exit 15:15"),
        Triple("9:20 Short Straddle", "PAPER | Daily 09:20 | ATM CE + PE Short", "Stop 25% on each leg individually"),
        Triple("First Hour Bias", "BIAS | Daily 10:15 | 09:15-10:15 Range vs CPR", "Filters counter-trend breakout trades"),
        Triple("Expiry 13:15 Direction", "BIAS | Expiry 13:15 | VWAP & High/Low Breakout", "Sets directional momentum for afternoon"),
        Triple("Afternoon Hero-Zero", "PAPER | Expiry 13:15 | ATM or 0.25% OTM Strike", "Ask <= 70% Fair Black-76 Value | Target 2x-3x"),
        Triple("Trendline Breakout", "PAPER | Intraday 5m | 3-Touch Falling Line", "Breakout above line with volume spike >= 1.5σ"),
        Triple("Breakout Retest Spread", "PAPER | Intraday 5m | 60-Bar Resistance Retest", "Bull Put Spread on bounce at prior resistance"),
        Triple("Big Move Watch", "INFO | Daily | VIX >= 22 | Range >= 2.5% | Gap >= 1.2%", "Widen condor wings, reduce sizing by 50%")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Part A4 & C4 Setup Library Overview", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        items(setups) { (name, rule, stop) ->
            val isLive = rule.contains("LIVE")
            val isBias = rule.contains("BIAS")
            val badgeColor = if (isLive) GreenBull else if (isBias) CyanNeon else GoldWarn

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(if (isLive) "LIVE" else if (isBias) "BIAS" else "PAPER", color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(rule, color = TextMuted, fontSize = 11.sp)
                    Text("Execution Rule: $stop", color = GoldWarn, fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-TAB 3: CAPITAL LADDER & V8 FEATURES
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CapitalLadderAndV8FeaturesContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🪜 Capital Ladder (Starting from ₹5,000)", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• Stage 0 (< ₹50,000): Strictly PAPER trading only. Zero capital risk.", color = TextWhite, fontSize = 11.sp)
                    Text("• Stage 1 (₹50k - ₹3L): Max 1 Lot, defined risk condors/spreads only. 1 trade at a time.", color = TextWhite, fontSize = 11.sp)
                    Text("• Stage 2 (₹3L - ₹5L): Up to 2 Lots, max 2 concurrent open trades.", color = TextWhite, fontSize = 11.sp)
                    Text("• Stage 3 (₹5L - ₹10L): Up to 4 Lots, multi-leg structures allowed.", color = TextWhite, fontSize = 11.sp)
                    Text("• Stage 4 (₹10L+): Full multi-strategy allocation, portfolio risk rules apply.", color = TextWhite, fontSize = 11.sp)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VioletNeon.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🛡️ v8 Calm Mode (Prompt 15)", color = VioletNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Conceals fluctuating intraday P&L on Iron Condor positions from 09:15 until 15:35 reveal time. Prevents emotional early exits. Allows /peek audit in DB.", color = TextWhite, fontSize = 11.sp)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldWarn.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("👻 v8 Ghost Ledger (Prompt 17)", color = GoldWarn, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Records every READY trade that was skipped due to Low-VIX (<13) or Risk limits. Marks to market at EOD to calculate exact capital saved by discipline.", color = TextWhite, fontSize = 11.sp)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GreenBull.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📡 v8 Rule-Change Radar (Prompt 16)", color = GreenBull, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Scans active exchange contract files at 18:30 daily. Automatically places affected strategies on 30-day PAPER mode upon lot size or expiry shifts.", color = TextWhite, fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-TAB 4: RAW IMPLEMENTATION SOURCE CODE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SourceCodeTabContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Core Mathematical Modules & Architecture Code", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Black-76 Options Pricing & IV Bisection Solver", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(color = BgDark, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("""
fun black76Price(isCall: Boolean, F: Double, K: Double, T: Double, sigma: Double, r: Double = 0.06): Double {
    if (T <= 0 || sigma <= 0) return max(0.0, if (isCall) F - K else K - F)
    val sqrtT = sqrt(T)
    val d1 = (ln(F / K) + 0.5 * sigma * sigma * T) / (sigma * sqrtT)
    val d2 = d1 - sigma * sqrtT
    val df = exp(-r * T)
    return if (isCall) df * (F * cdf(d1) - K * cdf(d2)) else df * (K * cdf(-d2) - F * cdf(-d1))
}
                        """.trimIndent(), color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Afternoon Hero-Zero Discount Filter (Ask <= 70% of Fair)", color = GoldWarn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(color = BgDark, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("""
fun checkHeroZeroDiscount(askPrice: Double, fairPrice: Double, maxRatio: Double = 0.70): Boolean {
    if (fairPrice <= 0.0) return false
    return (askPrice / fairPrice) <= maxRatio
}
                        """.trimIndent(), color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA GENERATORS FOR PROMPTS 1-20 & 16 PATTERNS
// ═══════════════════════════════════════════════════════════════════════════════
private fun getPdfPromptsList(): List<PdfPrompt> = listOf(
    PdfPrompt(1, "Project Skeleton & config.yaml", "SETUP", "Establish project tree, config.yaml schema, lot sizes (Nifty 65, Bank Nifty 30, Fin Nifty 60, Sensex 20), risk limits.", "Daily loss limit: 3%, Max open trades: 3, Stale timeout: 60s.", "capital:\n  total_inr: 50000.0\n  daily_loss_limit_pct: 0.03\nlots:\n  NIFTY: 65\n  SENSEX: 20"),
    PdfPrompt(2, "SQLite Database & Calendar", "DATABASE", "Initialize 10 SQLite tables from Part C8 and trading calendar (09:15-15:30 IST, holidays, strike steps).", "Tables: candles, chain_snapshots, alerts, positions, backtest_trades, setup_stats, ai_calls, setup_changes, ghosts, peeks.", "CREATE TABLE positions (\n  id INTEGER PRIMARY KEY,\n  setup_name TEXT,\n  symbol TEXT,\n  mode TEXT,\n  status TEXT...);"),
    PdfPrompt(3, "Market Data Provider", "DATA", "Ingest live ticks from Angel One BFO, Dhan, and NSE fallback with caching and off-hours simulation.", "Cached spot and Black-76 option chain generator for offline replay.", "def get_option_chain(symbol, spot):\n    # Connects Angel BFO / Dhan / NSE"),
    PdfPrompt(4, "Technical Indicators", "ANALYTICS", "Implement Wilder's RSI(14), ATR(14), ADX(14), Supertrend(10,3), VWAP, TWAP, CPR (TC/BC/Pivot), and Floor Pivots.", "Pivot = (H+L+C)/3, BC = (H+L)/2, TC = 2*P - BC, R1 = 2*P - L, S1 = 2*P - H.", "def compute_cpr_and_pivots(high, low, close):\n    pivot = (high + low + close) / 3.0..."),
    PdfPrompt(5, "16 Candlestick & Chart Patterns", "PATTERNS", "Build detectors for all 16 specified patterns: Hammer, Shooting Star, Engulfing, Doji, Marubozu, NR7, 5m EMA 20 Pullback, 3-Touch Breakout, etc.", "Hammer: lower_wick >= 2*body. NR7: narrowest bar of 7. Breakout: close above 3-touch line.", "def detect_candlestick_patterns(df):\n    # Evaluates all 16 patterns"),
    PdfPrompt(6, "Black-76 Model & Greeks", "MATH", "Solve Black-76 European options pricing, IV bisection solver, and compute Greeks (Delta, Gamma, Theta, Vega).", "d1 = (ln(F/K) + 0.5*sigma^2*T) / (sigma*sqrt(T)), d2 = d1 - sigma*sqrt(T).", "def black76_iv(is_call, target_price, F, K, T, r):\n    # Bisection b/w 0.01 and 3.50"),
    PdfPrompt(7, "Option Chain Analytics", "CHAIN", "Compute Max Pain, Put-Call Ratio (OI and Volume), Call/Put OI Walls, and 252-day IV Rank.", "Max Pain = strike minimizing buyer payout. IVR = (IV - Min) / (Max - Min) * 100.", "def compute_max_pain(chain_rows):\n    # Min total cash payout"),
    PdfPrompt(8, "Transaction Costs & Slippage", "RISK", "Calculate statutory charges: Brokerage Rs 20, STT 0.1% on sell, Exchange BSE 0.05% / NSE 0.035%, GST 18%, Slippage 1.5 pts.", "Net P&L = Realized P&L - (Brokerage + STT + Exch + SEBI + Stamp + GST + Slippage).", "def calculate_trade_costs(exchange, action, price, qty)..."),
    PdfPrompt(9, "Historical Backtesting Engine", "RESEARCH", "Backtest multi-leg structures across historical Bhavcopies and log trades to backtest_trades.", "Stores net P&L, maximum drawdown, and leg structures.", "def record_backtest_trade(setup, symbol, entry, exit, pnl, costs)..."),
    PdfPrompt(10, "Expiry-Selling Setup Agents", "SETUPS", "Sensex Expiry Condor (0.5% + 1.5% wings, 3x stop), Nifty Condors A & B (4x stop), Iron Flies.", "Entry at 09:16 AM. Hard exit at 15:15 IST. Low-VIX rule skips if VIX < 13.0.", "class SensexCondorAgent(ExpiryCondorAgent):\n    # Thursday 09:16, 3x stop"),
    PdfPrompt(11, "Paper Setup Agents", "SETUPS", "Implement 9:20 AM Short Straddle (25% leg stop), 15m EMA 9/21 cross, Trendline breakout, Retest spread.", "All trade in PAPER mode until meeting Part F1 promotion criteria.", "class ShortStraddle920Agent(SetupAgent):\n    # 25% stop loss per leg"),
    PdfPrompt(12, "Directional Bias & Info Agents", "AGENTS", "First-Hour Bias (10:15 CPR alignment), 13:15 Direction (VWAP & session breakout), Big-Move Watch.", "Big-Move: VIX >= 22 | Day range >= 2.5% | Gap >= 1.2%. Widen wings, cut size 50%.", "class FirstHourBiasAgent(SetupAgent):\n    # 10:15 CPR bias"),
    PdfPrompt(13, "Afternoon Hero-Zero Setup", "SETUPS", "13:15 Expiry direction trade targeting ATM/0.25% OTM strike with Ask <= 70% of Black-76 fair value.", "Target: 2x to 3x premium. Max Risk: 1 Lot or defined capital outlay.", "class AfternoonHeroZeroAgent(SetupAgent):\n    # Discount check ask <= 0.70*fair"),
    PdfPrompt(14, "Capital Ladder & Portfolio Sizing", "CAPITAL", "Enforce Stages 0 to 4 starting from Rs 5,000. Stage 0 (<50k) is strictly paper trading.", "Stage 1 (50k-3L): 1 lot. Stage 2 (3L-5L): 2 lots. Stage 3 (5L-10L): 4 lots. Stage 4: 10L+.", "class CapitalLadder:\n    # Safety gates per stage"),
    PdfPrompt(15, "v8 Calm Mode", "V8_FEATURE", "Conceal fluctuating live MTM on Iron Condors until 15:35 reveal. Record /peek commands to SQLite.", "Eliminates panic-closing during morning noise. Audit log in peeks table.", "class CalmModeManager:\n    # Masks P&L until 15:35 reveal"),
    PdfPrompt(16, "v8 Rule-Change Radar", "V8_FEATURE", "Audit exchange lot sizes, expiries, and circulars at 18:30 daily. Demote changed setups to PAPER.", "Tracks active Nov 2026 contract specs (Nifty 65, Bank Nifty 30, Fin Nifty 60, Sensex 20).", "class RuleChangeRadar:\n    # Daily circular scan"),
    PdfPrompt(17, "v8 Ghost Ledger", "V8_FEATURE", "Track every skipped READY trade (Low VIX, loss limits). Mark-to-market at 15:35 to verify rule value.", "Measures exact capital saved by skipping negative-expectancy trades.", "class GhostLedger:\n    # Resolves ghosts at EOD"),
    PdfPrompt(18, "Notification Dispatcher", "ENGINE", "Dispatch multi-channel alerts: Telegram Bot, desktop notifications, and Windows audio beeps.", "Triggers on FORMING, READY, EXIT, CANCELLED, and RISK alerts.", "class NotificationManager:\n    # Telegram + Windows Beep"),
    PdfPrompt(19, "Clock-Driven Engine & Replay", "ENGINE", "Execute daily market timeline (09:14 -> 09:16 -> 10:15 -> 13:15 -> 15:15 -> 15:35) with --replay mode.", "Runs full simulated day in seconds for acceptance testing.", "class TradingEngine:\n    # Clock cycle & Replay"),
    PdfPrompt(20, "7-Page UI Dashboard & App", "UI", "Build comprehensive interactive terminal: Live, Chart, Option Chain, Positions, Setups, Scoreboard, Settings.", "Visual candlestick overlays, pattern annotations, and Calm mode reveal.", "streamlit run optionapp/app/ui.py")
)

private fun getChartPatternsList(): List<ChartPatternSpec> = listOf(
    ChartPatternSpec("Hammer", "BULLISH", "Lower wick >= 2x body at support level", "Close below low of hammer candle", "Prompt 5 (Part C3 #1)"),
    ChartPatternSpec("Shooting Star", "BEARISH", "Upper wick >= 2x body at resistance zone", "Close above high of shooting star", "Prompt 5 (Part C3 #2)"),
    ChartPatternSpec("Bullish Engulfing", "BULLISH", "Green body completely engulfs prior red body", "Low of the engulfing bar", "Prompt 5 (Part C3 #3)"),
    ChartPatternSpec("Bearish Engulfing", "BEARISH", "Red body completely engulfs prior green body", "High of the engulfing bar", "Prompt 5 (Part C3 #3)"),
    ChartPatternSpec("Standard / Dragonfly Doji", "NEUTRAL", "Open equals Close within 10% total bar range", "Breakout above/below doji wick extremes", "Prompt 5 (Part C3 #4)"),
    ChartPatternSpec("Morning Star (3-Bar)", "BULLISH", "Red bar -> gap down small body -> Green close > 50% bar 1", "Low of middle star candle", "Prompt 5 (Part C3 #5)"),
    ChartPatternSpec("Evening Star (3-Bar)", "BEARISH", "Green bar -> gap up small body -> Red close < 50% bar 1", "High of middle star candle", "Prompt 5 (Part C3 #5)"),
    ChartPatternSpec("Piercing Line", "BULLISH", "Gap down open, closes > 50% into prior red candle", "Lowest point of piercing bar", "Prompt 5 (Part C3 #6)"),
    ChartPatternSpec("Dark Cloud Cover", "BEARISH", "Gap up open, closes > 50% down into prior green candle", "Highest point of dark cloud bar", "Prompt 5 (Part C3 #6)"),
    ChartPatternSpec("Bullish / Bearish Marubozu", "BULLISH", "Solid body >= 90% total range, minimal wicks", "Opposite extreme of the Marubozu bar", "Prompt 5 (Part C3 #9)"),
    ChartPatternSpec("Inside Bar & NR7 Squeeze", "SQUEEZE", "Narrowest range of past 7 bars, high/low inside prev bar", "Breakout beyond mother bar extremes", "Prompt 5 (Part C3 #10)"),
    ChartPatternSpec("3-Touch Trendline Breakout", "BULLISH", "Price crosses above falling line touching 3 lower highs", "Swing low prior to breakout candle", "Prompt 5 (Part C3 #11)"),
    ChartPatternSpec("Double Bottom (W-Pattern)", "BULLISH", "Two tests of support level within 0.3% price difference", "Breakdown below support lows", "Prompt 5 (Part C3 #12)"),
    ChartPatternSpec("Double Top (M-Pattern)", "BEARISH", "Two tests of resistance level within 0.3% price difference", "Breakout above resistance highs", "Prompt 5 (Part C3 #12)"),
    ChartPatternSpec("5m EMA 20 Pullback", "BULLISH", "Wick touches 5m EMA 20 with immediate rejection bounce", "Swing low of pullback candle", "Prompt 5 (Part C3 #15)"),
    ChartPatternSpec("Key Reversal Day", "BEARISH", "Makes new 15-bar high but reverses to close below prev low", "High of reversal bar", "Prompt 5 (Part C3 #16)")
)
