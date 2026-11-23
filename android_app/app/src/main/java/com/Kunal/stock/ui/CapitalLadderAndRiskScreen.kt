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

data class CapitalStage(
    val stageNum: Int,
    val rangeText: String,
    val mode: String,
    val allowedStructures: String,
    val graduationRequirement: String,
    val isCurrent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapitalLadderAndRiskScreen() {
    var capitalText by remember { mutableStateOf("500000") } // Default Rs 5 Lakhs
    val capital = capitalText.toDoubleOrNull() ?: 500_000.0

    // Sizing & Risk calculations from Part C6 & Part A14
    val maxStructureLossAllowed = capital * 0.05 // 5% max loss per structure
    val dailyLossLimit = capital * 0.03 // 3% daily loss limit
    val heroZeroBudget = capital * 0.0025 // 0.25% budget per expiry

    val currentStageNum = when {
        capital < 50_000.0 -> 0
        capital < 300_000.0 -> 1
        capital < 500_000.0 -> 2
        capital < 1_000_000.0 -> 3
        else -> 4
    }

    val stages = remember(currentStageNum) {
        listOf(
            CapitalStage(
                stageNum = 0,
                rangeText = "₹5,000 to ₹50,000",
                mode = "PAPER ONLY",
                allowedStructures = "Zero live trades. ATM calls cost ₹8,453 (more than account). Every READY is paper-tracked.",
                graduationRequirement = "60 days of paper results + capital reaches ₹50,000 through monthly top-up.",
                isCurrent = currentStageNum == 0
            ),
            CapitalStage(
                stageNum = 1,
                rangeText = "₹50,000 to ₹3 Lakh",
                mode = "RESTRICTED LIVE",
                allowedStructures = "LIVE only if smallest structure max loss <= 5% of capital (₹14,000 requires ₹3L+). Savings to Nifty 50 index fund.",
                graduationRequirement = "Capital reaches ₹3 Lakh.",
                isCurrent = currentStageNum == 1
            ),
            CapitalStage(
                stageNum = 2,
                rangeText = "₹3 Lakh to ₹5 Lakh",
                mode = "1 LOT CONDORS",
                allowedStructures = "1 lot Sensex condor with 1.0% wings (max loss ₹14,308 = 4.8%), Nifty condor 1.0% wings (needs ₹3.1L).",
                graduationRequirement = "20 live trades inside backtest range + capital reaches ₹5 Lakh.",
                isCurrent = currentStageNum == 2
            ),
            CapitalStage(
                stageNum = 3,
                rangeText = "₹5 Lakh to ₹10 Lakh",
                mode = "FULL LIVE SETUPS",
                allowedStructures = "1 lot each of Part A5 LIVE setups: Nifty condor A (0.5% + 1.5% wings, 4x stop) & Sensex condor (0.5% + 1.5%, 3x stop).",
                graduationRequirement = "Two profitable quarters + capital reaches ₹10 Lakh.",
                isCurrent = currentStageNum == 3
            ),
            CapitalStage(
                stageNum = 4,
                rangeText = "₹10 Lakh+",
                mode = "SCALE TO 2 LOTS",
                allowedStructures = "2 lots, or add monthly Bank Nifty / Fin Nifty expiries once they reach 100 paper expiries.",
                graduationRequirement = "Disciplined multi-year compounding.",
                isCurrent = currentStageNum == 4
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. CAPITAL INPUT & STAGE HEADER ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧗 Capital Ladder & Risk Budget (Part A14 & C6)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = TextHigh
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enforces survival-first sizing: from ₹5,000 savings to ₹10 Lakh live condors.",
                        fontSize = 11.sp,
                        color = TextMid
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = capitalText,
                        onValueChange = { capitalText = it },
                        label = { Text("Trading Capital (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = BgCard2,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Active Stage", fontSize = 10.sp, color = TextMid)
                                Text("STAGE $currentStageNum: ${stages.find { it.stageNum == currentStageNum }?.mode}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonGold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("5% Max Loss Allowed", fontSize = 10.sp, color = TextMid)
                                Text("₹${"%,.0f".format(maxStructureLossAllowed)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenUp)
                            }
                        }
                    }
                }
            }
        }

        // ── 2. RISK CONTROLS & EVENT BLOCKS ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard2),
                border = BorderStroke(0.5.dp, BorderClr)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡 Hard Risk Limits (Part C6)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonCyan)
                    Divider(color = BorderClr, thickness = 0.5.dp)

                    RiskRuleRow("3% Daily Loss Cutoff", "₹${"%,.0f".format(dailyLossLimit)} — halts all new entries for the day")
                    RiskRuleRow("Max Open Structures", "2 structures maximum across all indices")
                    RiskRuleRow("Max Daily Trades", "3 trades per day")
                    RiskRuleRow("Hero-Zero Budget", "0.25% of capital = ₹${"%,.0f".format(heroZeroBudget)} per expiry")
                    RiskRuleRow("Time Cutoff", "No new entries after 13:30 (except 13:15 Hero-Zero)")
                    RiskRuleRow("Peak Drawdown Rule", "15% drop from peak -> drop 1 stage & paper-trade 20 trades")
                }
            }
        }

        // ── 3. THE 5 STAGES LADDER ──
        item {
            Text(
                text = "🪜 The 5 Growth Stages (Progressive Graduation)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextHigh
            )
        }

        items(stages) { stage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (stage.isCurrent) BgCard else BgCard2),
                border = BorderStroke(if (stage.isCurrent) 1.5.dp else 0.5.dp, if (stage.isCurrent) NeonCyan else BorderClr)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Stage ${stage.stageNum}: ${stage.rangeText}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = TextHigh
                            )
                            if (stage.isCurrent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(color = NeonCyan.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text("CURRENT", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Surface(
                            color = if (stage.stageNum == 0) RedDown.copy(0.15f) else GreenUp.copy(0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stage.mode,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stage.stageNum == 0) RedDown else GreenUp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stage.allowedStructures, fontSize = 11.sp, color = TextMid, lineHeight = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Graduation Rule: ${stage.graduationRequirement}", fontSize = 10.sp, color = NeonGold, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun RiskRuleRow(rule: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rule, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextHigh)
        Text(detail, fontSize = 11.sp, color = TextMid)
    }
}
