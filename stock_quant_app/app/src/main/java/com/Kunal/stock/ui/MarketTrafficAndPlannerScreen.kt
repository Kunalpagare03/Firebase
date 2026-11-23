package com.Kunal.stock.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketTrafficAndPlannerScreen() {
    var trafficMode by remember { mutableStateOf("INFO") } // INFO or STRICT
    var capitalInput by remember { mutableStateOf("1000000") } // Rs 10 Lakhs
    var niftyPrice by remember { mutableStateOf(24150.0) }
    var niftySma50 by remember { mutableStateOf(23920.0) }
    var niftySma200 by remember { mutableStateOf(23200.0) }
    var vixValue by remember { mutableStateOf(14.2) }
    var breadthPct by remember { mutableStateOf(64.5) }

    // State calculation
    val aboveSma200 = niftyPrice > niftySma200
    val aboveSma50 = niftyPrice > niftySma50
    val isPanic = vixValue >= 22.0 || breadthPct < 30.0

    val (stateNum, colorName, sizeMult, stateDesc, stateBg) = when {
        aboveSma200 && aboveSma50 -> Tuple5(1, "GREEN", 1.0, "Nifty > SMA200 and > SMA50 (Full Bullish Trend)", Color(0xFF1B5E20))
        aboveSma200 && !aboveSma50 -> Tuple5(2, "AMBER", 1.0, "Nifty > SMA200 but < SMA50 (Bullish Pullback)", Color(0xFFF57F17))
        !aboveSma200 && aboveSma50 -> Tuple5(3, "AMBER", 0.5, "Nifty < SMA200 but > SMA50 (Counter-trend Rally)", Color(0xFFE65100))
        !aboveSma200 && !aboveSma50 && isPanic -> Tuple5(4, "AMBER", 0.5, "Nifty < both & Panic (Capitulation Dip Buy)", Color(0xFFD84315))
        else -> Tuple5(5, "RED", 0.0, "Nifty < both & No Panic (Downtrend Grind - WAIT, Paper Trade Only)", Color(0xFFB71C1C))
    }

    val effectiveSize = if (trafficMode == "STRICT") sizeMult else 1.0
    val totalCapital = capitalInput.toDoubleOrNull() ?: 1_000_000.0
    val riskPerTrade = totalCapital * 0.01 * effectiveSize // 1% risk adjusted

    // Sample planned slots
    val plannedPicks = remember {
        listOf(
            PlannedPickItem("TRENT", "SWING", "S10 (Crash Dip)", "Consumer Services", 2840.0, 2612.80, 84.5, 43, 122120.0, 9770.0),
            PlannedPickItem("BEL", "POSITIONAL", "N08 (Leader Dip)", "Capital Goods", 288.5, 265.42, 88.0, 433, 124920.0, 9993.0),
            PlannedPickItem("HAL", "POSITIONAL", "S09 (RSI2 Panic)", "Capital Goods", 4420.0, 4066.40, 79.2, 28, 123760.0, 9900.0)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. MARKET TRAFFIC LIGHT BANNER ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = stateBg.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STATE $stateNum: $colorName",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Sizing: ${effectiveSize}x ($trafficMode)",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stateDesc,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.White.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Nifty 50", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("₹${"%,.0f".format(niftyPrice)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("SMA200: ₹${"%,.0f".format(niftySma200)}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                        Column {
                            Text("India VIX", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("${vixValue}", color = if (vixValue >= 22) Color.Yellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (isPanic) "PANIC ACTIVE" else "Normal", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                        Column {
                            Text("Breadth >SMA50", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("${breadthPct}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Liquid Universe", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // ── 2. TRAFFIC LIGHT MODE SWITCHER & CAPITAL SIZING ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚙ Regime Sizing & Capital Controls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = trafficMode == "INFO",
                            onClick = { trafficMode = "INFO" },
                            label = { Text("INFO Mode (Advisory)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = trafficMode == "STRICT",
                            onClick = { trafficMode = "STRICT" },
                            label = { Text("STRICT Mode (Enforce Size)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Capital: ₹${"%,.0f".format(totalCapital)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("1% Risk per Trade: ₹${"%,.0f".format(riskPerTrade)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Hard Stop: 8%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── 3. 10-SLOT TRADE PLANNER ALLOCATION ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Active Trade Planner (10 Slot System)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Used: 3 / 10 Slots",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Slot limits summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Swing Slots", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1 / 4", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Max 5 Days", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Positional Slots", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("2 / 6", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Max 22 Days", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sector Exposure", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Max 2/sec", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Cap Goods: 2", fontSize = 9.sp, color = Color(0xFFFFB300))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("New Tonight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3 / 3", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Max 3/night", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Planned Picks Cards
        items(plannedPicks) { pick ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pick.symbol,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (pick.horizon == "SWING") Color(0xFF00E676).copy(0.15f) else Color(0xFF40C4FF).copy(0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = pick.horizon,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pick.horizon == "SWING") Color(0xFF00E676) else Color(0xFF40C4FF)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Tech Score: ${pick.techScore}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Setup: ${pick.setup} • Sector: ${pick.sector}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Planned Entry", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.2f".format(pick.plannedEntry)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("8% Stop Loss", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.2f".format(pick.stopPrice)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF5252))
                        }
                        Column {
                            Text("Quantity", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${pick.qty} shares", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Risk (₹)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.0f".format(pick.riskRs)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

data class PlannedPickItem(
    val symbol: String,
    val horizon: String,
    val setup: String,
    val sector: String,
    val plannedEntry: Double,
    val stopPrice: Double,
    val techScore: Double,
    val qty: Int,
    val allocatedCapital: Double,
    val riskRs: Double
)

data class Tuple5<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)
