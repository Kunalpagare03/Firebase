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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmAndGhostRadarScreen() {
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Calm Mode, 1: Ghost Ledger, 2: Rule Radar
    var peekCount by remember { mutableIntStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }

    val calmTrades = remember {
        listOf(
            CalmTradeItem(
                id = "EXP-20261123-NIFTY-A",
                structure = "NIFTY Iron Condor A (0.5% + 1.5%)",
                entryTime = "09:16 AM",
                legs = "SELL 24,150 CE & 23,850 PE / BUY 24,500 CE & 23,500 PE",
                status = "HOLDING TO 15:30 SETTLEMENT",
                stopOrdersAtBroker = true,
                simulatedPnL = 2850.0,
                maxLoss = 24810.0,
                peekLogged = 0
            ),
            CalmTradeItem(
                id = "EXP-20261123-SENSEX-LIVE",
                structure = "SENSEX Iron Condor (0.5% + 1.5% wings)",
                entryTime = "09:16 AM",
                legs = "SELL 79,200 CE & 78,400 PE / BUY 80,400 CE & 77,200 PE",
                status = "HOLDING TO 15:30 SETTLEMENT",
                stopOrdersAtBroker = true,
                simulatedPnL = 4620.0,
                maxLoss = 22314.0,
                peekLogged = 0
            )
        )
    }

    val ghostTrades = remember {
        listOf(
            GhostTradeItem(
                date = "20-Nov-2026",
                symbol = "NIFTY",
                setup = "Iron Condor A",
                reason = "VIX < 13 Filter (VIX was 12.2)",
                outcome = "SAVED YOU ₹8,078",
                outcomeType = "SAVED",
                amount = 8078.0,
                details = "Wild intra-day swing would have stopped out the 4x call leg. The low VIX rule saved you ₹8,078 a lot!"
            ),
            GhostTradeItem(
                date = "18-Nov-2026",
                symbol = "BANKNIFTY",
                setup = "9:20 Straddle",
                reason = "Event Window Block (RBI Policy)",
                outcome = "SAVED YOU ₹14,200",
                outcomeType = "SAVED",
                amount = 14200.0,
                details = "Event volatility spike pierced upper straddle breakeven. Skipping saved capital."
            ),
            GhostTradeItem(
                date = "14-Nov-2026",
                symbol = "FINNIFTY",
                setup = "Iron Fly 1% Wings",
                reason = "Daily Loss Limit Reached",
                outcome = "COST YOU ₹1,650",
                outcomeType = "COST",
                amount = -1650.0,
                details = "Expired at the money. Skipping cost ₹1,650, but risk management discipline was maintained."
            )
        )
    }

    val radarCirculars = remember {
        listOf(
            RadarItem(
                circularNumber = "SEBI/HO/MRD/2026/16-OCT",
                date = "15-Oct-2026",
                subject = "Index Derivatives Closing Session Auction & Settlement VWAP",
                impact = "SETTLEMENT RULE CHANGE",
                status = "TAGGED FOR BACKTEST AUDIT",
                details = "From 07-Dec-2026 expiring index options settle on index value from its stocks' 15:00-15:30 VWAP with live indicative value published. Affected condor setups will monitor settlement delta."
            ),
            RadarItem(
                circularNumber = "NSE/F&O/2026/LOT-REVISION",
                date = "01-Nov-2026",
                subject = "Revised Lot Sizes: Nifty 65, Bank Nifty 30, Fin Nifty 60, Sensex 20",
                impact = "CONTRACT SIZE UPDATE",
                status = "AUTOMATICALLY APPLIED",
                details = "Exchange files updated lot sizes. Position sizing math floor(5% capital / max loss) automatically adjusted in real time."
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Section Selector Tabs
        Surface(
            color = BgCard,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "🧘 Calm Mode (v8)",
                    1 to "👻 Ghost Ledger",
                    2 to "📡 Rule Radar"
                ).forEach { (idx, label) ->
                    val isSelected = selectedSection == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSection = idx }
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else BorderClr,
                                RoundedCornerShape(8.dp)
                            ),
                        color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else BgCard2
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
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
            when (selectedSection) {
                // ── 1. CALM MODE (Part A17, Prompt 28) ──
                0 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(GreenUp, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "CALM MODE ACTIVE",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = GreenUp
                                        )
                                    }
                                    Surface(
                                        color = BgCard2,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, BorderClr)
                                    ) {
                                        Text(
                                            text = "Peeks Logged: $peekCount",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            color = NeonGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "LIVE Condors run quietly without tick-by-tick P&L to prevent emotional premature exits. Stop orders are resting at the broker.",
                                    color = TextMid,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            peekCount++
                                            isRevealed = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonViolet.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Peek Once (/peek)", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { isRevealed = !isRevealed },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isRevealed) RedDown.copy(0.4f) else BgCard2),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isRevealed) "Hide P&L" else "15:35 Reveal", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    items(calmTrades) { trade ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            border = BorderStroke(1.dp, BorderClr)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trade.structure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextHigh
                                    )
                                    Text(
                                        text = trade.entryTime,
                                        fontSize = 11.sp,
                                        color = TextMid
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trade.legs,
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = BorderClr, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Next Action", fontSize = 10.sp, color = TextMid)
                                        Text("None until 15:30", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenUp)
                                    }
                                    Column {
                                        Text("Broker Stop Orders", fontSize = 10.sp, color = TextMid)
                                        Text("PLACED (4x/3x)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonGold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Unrealized P&L", fontSize = 10.sp, color = TextMid)
                                        if (isRevealed) {
                                            Text(
                                                text = "+₹${"%,.0f".format(trade.simulatedPnL)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = GreenUp
                                            )
                                        } else {
                                            Text(
                                                text = "🔒 MASKED (Calm)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = TextMid
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 2. GHOST LEDGER (Part A17, Prompt 28) ──
                1 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "👻 Ghost Ledger — Tracks Every Trade You Skipped",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = NeonViolet
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Every READY signal that was skipped due to low VIX (<13), event windows, wide spreads, or daily limits is tracked to settlement to prove if the rule actually saved you money.",
                                    color = TextMid,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Net Ghost Edge", fontSize = 10.sp, color = TextMid)
                                        Text("+₹20,628 SAVED", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = GreenUp)
                                    }
                                    Column {
                                        Text("Skipped Trades", fontSize = 10.sp, color = TextMid)
                                        Text("3 Expiries Tracked", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextHigh)
                                    }
                                }
                            }
                        }
                    }

                    items(ghostTrades) { ghost ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard2),
                            border = BorderStroke(0.5.dp, BorderClr)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ghost.symbol} • ${ghost.setup}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextHigh
                                    )
                                    Surface(
                                        color = if (ghost.outcomeType == "SAVED") GreenUp.copy(0.15f) else RedDown.copy(0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = ghost.outcome,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = if (ghost.outcomeType == "SAVED") GreenUp else RedDown,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Skip Reason: ${ghost.reason} • ${ghost.date}", fontSize = 11.sp, color = TextMid)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(ghost.details, fontSize = 11.sp, color = TextHigh.copy(alpha = 0.85f), lineHeight = 16.sp)
                            }
                        }
                    }
                }

                // ── 3. RULE-CHANGE RADAR (Part A17, Prompt 28) ──
                2 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard),
                            border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "📡 Rule-Change Radar (NSE & SEBI Circulars)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = NeonGold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Scans exchange feeds daily for lot size revisions, expiry weekday changes, STT hikes, and settlement rules. Protects your trading edge from overnight regulatory surprises.",
                                    color = TextMid,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    items(radarCirculars) { circular ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard2),
                            border = BorderStroke(0.5.dp, BorderClr)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = circular.circularNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = NeonCyan
                                    )
                                    Text(circular.date, fontSize = 10.sp, color = TextMid)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = circular.subject,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextHigh
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = NeonGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${circular.impact} • ${circular.status}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(circular.details, fontSize = 11.sp, color = TextMid, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CalmTradeItem(
    val id: String,
    val structure: String,
    val entryTime: String,
    val legs: String,
    val status: String,
    val stopOrdersAtBroker: Boolean,
    val simulatedPnL: Double,
    val maxLoss: Double,
    val peekLogged: Int
)

data class GhostTradeItem(
    val date: String,
    val symbol: String,
    val setup: String,
    val reason: String,
    val outcome: String,
    val outcomeType: String,
    val amount: Double,
    val details: String
)

data class RadarItem(
    val circularNumber: String,
    val date: String,
    val subject: String,
    val impact: String,
    val status: String,
    val details: String
)
