package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgDeep     = Color(0xFF0F081D)
private val BgCard     = Color(0xFF18102B)
private val BgCard2    = Color(0xFF22163D)
private val NeonCyan   = Color(0xFF00F5FF)
private val NeonViolet = Color(0xFF9D4EDD)
private val NeonGold   = Color(0xFFFFD700)
private val GreenUp    = Color(0xFF00E676)
private val RedDown    = Color(0xFFFF5252)
private val TextHigh   = Color(0xFFF5F3FF)
private val TextMid    = Color(0xFFA79BC4)
private val BorderClr  = Color(0xFF352654)

data class OiReading(
    val symbol: String,
    val spot: Double,
    val futurePriceChg: Double,
    val futureOiChg: Double,
    val buildupClass: String,
    val pcr: Double,
    val freshPosRatio: Double,
    val writerBias: Double,
    val callWall: Double,
    val putWall: Double,
    val maxPain: Double,
    val expectedMove: Double,
    val trendScore: Int, // -4 to +4
    val verdict: String
)

data class OptionSetupRule(
    val code: String,
    val name: String,
    val index: String,
    val mode: String, // LIVE / PAPER / BIASED
    val entryRule: String,
    val stopRule: String,
    val targetRule: String,
    val statsWinRate: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OiTrendAndSetupsScreen() {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 11 OI Formulas (oi_trend.py), 1: Option Setups Catalog
    var selectedIndex by remember { mutableStateOf("NIFTY") }

    val oiReadings = remember {
        mapOf(
            "NIFTY" to OiReading(
                symbol = "NIFTY",
                spot = 24150.0,
                futurePriceChg = 0.95,
                futureOiChg = 4.08,
                buildupClass = "LONG BUILD-UP (+1)",
                pcr = 1.53,
                freshPosRatio = 1.0,
                writerBias = 0.51,
                callWall = 24500.0,
                putWall = 23800.0,
                maxPain = 24100.0,
                expectedMove = 232.60,
                trendScore = 2,
                verdict = "BULLISH (CE Side Bias)"
            ),
            "BANKNIFTY" to OiReading(
                symbol = "BANKNIFTY",
                spot = 51850.0,
                futurePriceChg = 2.34,
                futureOiChg = -2.05,
                buildupClass = "SHORT COVERING (+1)",
                pcr = 1.20,
                freshPosRatio = 0.28,
                writerBias = 0.41,
                callWall = 52500.0,
                putWall = 51000.0,
                maxPain = 51500.0,
                expectedMove = 834.80,
                trendScore = 2,
                verdict = "BULLISH (CE Side Bias)"
            ),
            "SENSEX" to OiReading(
                symbol = "SENSEX",
                spot = 79450.0,
                futurePriceChg = 0.82,
                futureOiChg = 3.12,
                buildupClass = "LONG BUILD-UP (+1)",
                pcr = 1.35,
                freshPosRatio = 0.45,
                writerBias = 0.38,
                callWall = 80000.0,
                putWall = 78500.0,
                maxPain = 79200.0,
                expectedMove = 680.0,
                trendScore = 1,
                verdict = "MILD BULLISH (CE Bias)"
            )
        )
    }

    val currentReading = oiReadings[selectedIndex] ?: oiReadings["NIFTY"]!!

    val setupCatalog = remember {
        listOf(
            OptionSetupRule(
                code = "S01",
                name = "Expiry Iron Condor (Nifty 4x Stop)",
                index = "NIFTY",
                mode = "LIVE",
                entryRule = "09:16 AM: Sell 0.5% OTM CE & PE, buy wings 1.5% further out at live bid/ask.",
                stopRule = "4x stop on sold legs; hold intact legs to 15:30 settlement.",
                targetRule = "Expiry close at 15:30; full premium decay.",
                statsWinRate = "404 expiries, 68% won, +₹649/lot (t 4.8)"
            ),
            OptionSetupRule(
                code = "S02",
                name = "Expiry Iron Condor (Sensex 3x Stop)",
                index = "SENSEX",
                mode = "LIVE",
                entryRule = "09:16 AM: Sell 0.5% OTM CE & PE, buy wings 1.5% further out.",
                stopRule = "3x stop on each sold leg; wings never sold early.",
                targetRule = "Expiry close at 15:30.",
                statsWinRate = "148 expiries, 66% won, +₹1,181/lot (t 4.1)"
            ),
            OptionSetupRule(
                code = "S03",
                name = "Hero Zero (13:15 Directional)",
                index = "NIFTY & SENSEX",
                mode = "PAPER",
                entryRule = "13:15 PM: Buy ATM or 0.25% OTM option in 13:15 direction (price vs VWAP) only if Ask <= 70% of fair value table.",
                stopRule = "Fixed small budget: 0.25% capital per expiry. Zero averaging.",
                targetRule = "Sell at 3x or exit at 15:15 sharp.",
                statsWinRate = "Tested 1,296 rules: 0.5-1% gap side has 5.2% 10x rate."
            ),
            OptionSetupRule(
                code = "S04",
                name = "First-Hour Bias (9:15-10:15)",
                index = "ALL INDICES",
                mode = "BIAS / FILTER",
                entryRule = "9:15-10:15 candle: green body >= 60% of range, range >= 1.2x 20-day avg.",
                stopRule = "Filter only: favours longs, warns against call selling/shorts.",
                targetRule = "73.2% closed higher on Nifty (41 days tested, p 0.004).",
                statsWinRate = "73% win edge as directional regime filter."
            ),
            OptionSetupRule(
                code = "S05",
                name = "15-Min EMA 9/21 Cross",
                index = "NIFTY, BANK NIFTY",
                mode = "PAPER",
                entryRule = "15-minute bar: EMA 9 crosses EMA 21 between 09:45 and 14:00. Enter at next bar open.",
                stopRule = "1.5x ATR(14) stop loss.",
                targetRule = "Target 2R, exit at 15:15.",
                statsWinRate = "97 trades, +0.08R edge. Paper tracking."
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Tab Switcher
        Surface(
            color = BgCard,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "🧮 11 OI Formulas (oi_trend.py)",
                    1 to "📑 v8 Setup Cards (A4/A5/C4)"
                ).forEach { (idx, label) ->
                    val isSelected = selectedTab == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = idx }
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else BorderClr,
                                RoundedCornerShape(8.dp)
                            ),
                        color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else BgCard2
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = if (isSelected) NeonCyan else TextMid,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedTab == 0) {
                // ── INDEX CHIP SELECTOR ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("NIFTY", "BANKNIFTY", "SENSEX").forEach { sym ->
                            val isSel = selectedIndex == sym
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedIndex = sym },
                                label = { Text(sym, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── SUMMARY TREND SCORE CARD (F11) ──
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        border = BorderStroke(1.dp, if (currentReading.trendScore >= 2) GreenUp else BorderClr)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${currentReading.symbol} OI Trend Reading",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = TextHigh
                                    )
                                    Text(
                                        text = "Spot: ₹${"%,.1f".format(currentReading.spot)} • 495 Days Tested",
                                        fontSize = 12.sp,
                                        color = TextMid
                                    )
                                }

                                Surface(
                                    color = if (currentReading.trendScore >= 2) GreenUp.copy(0.18f) else BgCard2,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (currentReading.trendScore >= 2) GreenUp else BorderClr)
                                ) {
                                    Text(
                                        text = "F11 Score: ${if (currentReading.trendScore > 0) "+${currentReading.trendScore}" else "${currentReading.trendScore}"}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = if (currentReading.trendScore >= 2) GreenUp else NeonGold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderClr, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Verdict: ${currentReading.verdict}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (currentReading.trendScore >= 2) GreenUp else TextHigh
                            )
                            Text(
                                text = "Tested edge: Next day up 51.0% (ATM CE bought at close made -Rs 724 on average). Used as context, not blind buyer signal.",
                                fontSize = 11.sp,
                                color = TextMid,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // ── 11 FORMULAS DETAIL GRID ──
                item {
                    Text(
                        text = "🔬 The 11 Open-Interest Formulas (Part Appendix A)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonCyan
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard2),
                        border = BorderStroke(0.5.dp, BorderClr)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormulaRow("F1/F2 Futures Price & OI Chg", "+${currentReading.futurePriceChg}% Price / +${currentReading.futureOiChg}% OI")
                            FormulaRow("F3 Build-Up Classification", currentReading.buildupClass)
                            FormulaRow("F4 Put-Call Ratio (PCR)", "${currentReading.pcr} (${if (currentReading.pcr >= 1.1) "+1 Bullish" else "0"})")
                            FormulaRow("F5 Fresh Position Ratio (dPCR)", "${currentReading.freshPosRatio}")
                            FormulaRow("F6 Writer Bias (Near the money)", "+${currentReading.writerBias} (+1 Put writing below)")
                            FormulaRow("F7 Call Wall (Kc) & Put Wall (Kp)", "Call: ${"%,.0f".format(currentReading.callWall)} / Put: ${"%,.0f".format(currentReading.putWall)}")
                            FormulaRow("F8 Max Pain Strike (K*)", "₹${"%,.0f".format(currentReading.maxPain)}")
                            FormulaRow("F10 Expected Move (ATM Straddle)", "₹${currentReading.expectedMove} (${String.format("%.2f", currentReading.expectedMove / currentReading.spot * 100)}%)")
                        }
                    }
                }
            } else {
                // ── OPTION SETUPS CATALOG (Part A4/A5/C4) ──
                item {
                    Text(
                        text = "🎯 Tested Option Setups (Expiry Condors, Hero-Zero, Biases)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonGold
                    )
                }

                items(setupCatalog) { setup ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        border = BorderStroke(1.dp, if (setup.mode == "LIVE") GreenUp.copy(0.4f) else BorderClr)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${setup.code}: ${setup.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextHigh
                                    )
                                }
                                Surface(
                                    color = if (setup.mode == "LIVE") GreenUp.copy(0.15f) else NeonGold.copy(0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = setup.mode,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (setup.mode == "LIVE") GreenUp else NeonGold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Index: ${setup.index}", fontSize = 11.sp, color = NeonCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = BorderClr, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("• Entry: ${setup.entryRule}", fontSize = 11.sp, color = TextHigh.copy(alpha = 0.9f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Stop: ${setup.stopRule}", fontSize = 11.sp, color = RedDown.copy(alpha = 0.9f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Target / Exit: ${setup.targetRule}", fontSize = 11.sp, color = GreenUp.copy(alpha = 0.9f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(color = BgCard2, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "📊 Historical Edge: ${setup.statsWinRate}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    color = TextMid
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextMid)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextHigh)
    }
}
