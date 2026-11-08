package com.Kunal.stock.ui

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════════
// SETUP GUIDE AI AGENT — Multi-Agent Analysis Engine
// ───────────────────────────────────────────────────────────────────────────────
// 3 Sub-Agents → 1 Master Verdict
//   Agent 1: TimeContextAgent  — which setup window is active right now?
//   Agent 2: RuleCheckAgent    — do current user inputs meet entry rules?
//   Agent 3: RiskGuardAgent    — is capital / lot size within safe limits?
//   Master : SetupMasterAgent  — final GO / WAIT / NO-GO verdict
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Input from user / market ─────────────────────────────────────────────────
data class SetupAgentInput(
    // Time
    val hhmm: Int,
    val isExpiryDay: Boolean,
    // Setup 1 inputs
    val rangeHigh: Double = 0.0,
    val rangeLow: Double = 0.0,
    val currentPrice: Double = 0.0,
    val vwap: Double = 0.0,
    val niftyLevel: Double = 22500.0,
    // Setup 2 inputs
    val firstHourOpen: Double = 0.0,
    val firstHourClose: Double = 0.0,
    val firstHourHigh: Double = 0.0,
    val firstHourLow: Double = 0.0,
    val avgFirstHourSize20d: Double = 0.0,
    // Setup 3 inputs
    val optionPrice: Double = 0.0,
    val optionStrike: String = "ATM",   // "ATM" or "1-OTM"
    val index: String = "NIFTY",        // "NIFTY" | "BANKNIFTY" | "SENSEX"
    val priceAboveOpen: Boolean = false,
    val priceAboveVwap: Boolean = false,
    // Capital
    val capital: Double = 0.0,
    val selectedSetup: Int = 1          // 1-5
)

// ─── Sub-agent outputs ────────────────────────────────────────────────────────
data class TimeContextResult(
    val activeSetups: List<Int>,        // which setup numbers are valid right now
    val windowStatus: String,           // "OPEN" | "WATCH" | "CLOSED"
    val timeMessage: String,
    val expiryAlert: Boolean
)

data class RuleCheckResult(
    val setup1Met: Boolean,
    val setup1Issues: List<String>,
    val setup2Direction: String,        // "CE_ONLY" | "PE_ONLY" | "NEUTRAL" | "N/A"
    val setup3Met: Boolean,
    val setup3Issues: List<String>,
    val overallRuleScore: Int           // 0-100
)

data class RiskGuardResult(
    val capitalSafe: Boolean,
    val maxRiskRs: Double,
    val suggestedLots: Int,
    val lotSize: Int,
    val dailyStopBreached: Boolean,
    val weeklyStopBreached: Boolean,
    val riskMessages: List<String>
)

data class SetupMasterVerdict(
    val verdict: String,                // "GO", "WATCH", "WAIT", "NO-GO"
    val setup: String,
    val side: String,                   // "CE", "PE", "N/A"
    val confidence: Int,                // 0-100
    val entryNote: String,
    val stopNote: String,
    val targetNote: String,
    val reasoning: List<String>,
    val warnings: List<String>,
    val agentLogs: List<String>
)

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 1 — TIME CONTEXT AGENT
// ═══════════════════════════════════════════════════════════════════════════════
object TimeContextAgent {
    fun analyze(input: SetupAgentInput): TimeContextResult {
        val h = input.hhmm
        val expiry = input.isExpiryDay
        val activeSetups = mutableListOf<Int>()
        val issues = mutableListOf<String>()

        val window = when {
            h < 915  -> "CLOSED"
            h < 930  -> { issues.add("9:15-9:30: 1st candle forming. DO NOT enter."); "WATCH" }
            h < 945  -> { issues.add("9:30-9:45: Check range size. Still waiting."); "WATCH" }
            h in 945..1030 -> {
                activeSetups.add(1)
                activeSetups.add(5)
                "OPEN"
            }
            h in 1015..1030 -> {
                activeSetups.add(2)
                "OPEN"
            }
            h in 1030..1330 -> {
                issues.add("10:30-13:30: Manage existing trade only. No new entry.")
                "CLOSED"
            }
            expiry && h in 1315..1400 -> {
                activeSetups.add(3)
                "OPEN"
            }
            h >= 1515 -> { issues.add("15:15 reached. Close everything."); "CLOSED" }
            else -> { issues.add("No new trades after 13:30."); "CLOSED" }
        }

        val timeMsg = when (window) {
            "OPEN"  -> "✓ Trading window is OPEN for Setup ${activeSetups.joinToString(",")}"
            "WATCH" -> "👁 Watch mode — ${issues.firstOrNull() ?: "wait"}"
            else    -> "✗ ${issues.firstOrNull() ?: "Market closed or outside trading window"}"
        }

        return TimeContextResult(
            activeSetups = activeSetups,
            windowStatus = window,
            timeMessage = timeMsg,
            expiryAlert = expiry && h in 1300..1400
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 2 — RULE CHECK AGENT
// ═══════════════════════════════════════════════════════════════════════════════
object RuleCheckAgent {
    fun analyze(input: SetupAgentInput): RuleCheckResult {
        val s1Issues = mutableListOf<String>()
        var s1Met = true

        // Setup 1 checks
        val rangeSize = if (input.niftyLevel > 0 && input.rangeHigh > 0 && input.rangeLow > 0)
            (input.rangeHigh - input.rangeLow) / input.niftyLevel * 100.0 else -1.0

        if (rangeSize > 0.6) {
            s1Met = false
            s1Issues.add("Range size %.2f%% > 0.6%% — SKIP Setup 1 today".format(rangeSize))
        } else if (rangeSize > 0) {
            s1Issues.add("Range size %.2f%% < 0.6%% — OK".format(rangeSize))
        }

        if (input.currentPrice > 0 && input.rangeHigh > 0 && input.vwap > 0) {
            val aboveRange = input.currentPrice > input.rangeHigh
            val belowRange = input.currentPrice < input.rangeLow
            val aboveVwap  = input.currentPrice > input.vwap

            if (!aboveRange && !belowRange) {
                s1Met = false
                s1Issues.add("Price still inside opening range — wait for breakout")
            } else if (aboveRange && !aboveVwap) {
                s1Met = false
                s1Issues.add("Price above range HIGH but BELOW VWAP — no CE signal")
            } else if (belowRange && aboveVwap) {
                s1Met = false
                s1Issues.add("Price below range LOW but ABOVE VWAP — no PE signal")
            } else if (aboveRange && aboveVwap) {
                s1Issues.add("Price above range + above VWAP → CE signal valid")
            } else if (belowRange && !aboveVwap) {
                s1Issues.add("Price below range + below VWAP → PE signal valid")
            }
        }

        // Setup 2 — 1-hour direction
        var s2Direction = "N/A"
        if (input.firstHourClose > 0 && input.firstHourOpen > 0) {
            val body = abs(input.firstHourClose - input.firstHourOpen)
            val size = input.firstHourHigh - input.firstHourLow
            val bodyPct = if (size > 0) body / size * 100.0 else 0.0
            val sizeRatio = if (input.avgFirstHourSize20d > 0) size / input.avgFirstHourSize20d else 0.0
            val isGreen = input.firstHourClose > input.firstHourOpen

            s2Direction = when {
                isGreen && bodyPct >= 60 && sizeRatio >= 1.2 -> "CE_ONLY"
                !isGreen && bodyPct >= 60 && sizeRatio >= 1.2 -> "PE_ONLY"
                else -> "NEUTRAL"
            }
        }

        // Setup 3 checks
        val s3Issues = mutableListOf<String>()
        var s3Met = true

        val maxPrice = when {
            input.index == "NIFTY"     && input.optionStrike == "ATM"   -> 24.0
            input.index == "NIFTY"     && input.optionStrike == "1-OTM" -> 7.0
            input.index == "BANKNIFTY" && input.optionStrike == "ATM"   -> 36.0
            input.index == "BANKNIFTY" && input.optionStrike == "1-OTM" -> 11.0
            input.index == "SENSEX"    && input.optionStrike == "ATM"   -> 84.0
            input.index == "SENSEX"    && input.optionStrike == "1-OTM" -> 43.0
            else -> 24.0
        }

        if (input.optionPrice > maxPrice) {
            s3Met = false
            s3Issues.add("Option price Rs%.0f > max Rs%.0f — NO TRADE".format(input.optionPrice, maxPrice))
        } else if (input.optionPrice > 0) {
            s3Issues.add("Option price Rs%.0f ≤ max Rs%.0f — price OK".format(input.optionPrice, maxPrice))
        }

        val mixed = input.priceAboveOpen != input.priceAboveVwap
        if (mixed) {
            s3Met = false
            s3Issues.add("Mixed signal (open/VWAP disagree) — NO TRADE for Setup 3")
        } else if (input.optionPrice > 0) {
            s3Issues.add(if (input.priceAboveOpen) "Direction: CE (above open + VWAP)" else "Direction: PE (below open + VWAP)")
        }

        val score = when {
            s1Met && s3Met -> 85
            s1Met || s3Met -> 55
            s2Direction != "N/A" && s2Direction != "NEUTRAL" -> 40
            else -> 20
        }

        return RuleCheckResult(
            setup1Met = s1Met,
            setup1Issues = s1Issues,
            setup2Direction = s2Direction,
            setup3Met = s3Met,
            setup3Issues = s3Issues,
            overallRuleScore = score
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 3 — RISK GUARD AGENT
// ═══════════════════════════════════════════════════════════════════════════════
object RiskGuardAgent {
    fun analyze(input: SetupAgentInput): RiskGuardResult {
        val maxRisk = input.capital * 0.005
        val lotSize = if (input.index == "BANKNIFTY") 30 else 65
        val riskPerLot = if (input.index == "BANKNIFTY") 1500.0 else 1800.0
        val suggestedLots = if (maxRisk >= riskPerLot) (maxRisk / riskPerLot).toInt() else 0
        val msgs = mutableListOf<String>()

        if (input.capital <= 0) {
            msgs.add("Capital not entered — cannot validate risk")
        } else {
            msgs.add("Max risk per trade: Rs %.0f (0.5%% of Rs %.0f)".format(maxRisk, input.capital))
            if (suggestedLots == 0) {
                msgs.add("Capital too small for 1 lot. PAPER TRADE.")
            } else {
                msgs.add("Safe lots: $suggestedLots (${suggestedLots * lotSize} qty)")
            }
        }

        return RiskGuardResult(
            capitalSafe = suggestedLots > 0 || input.capital <= 0,
            maxRiskRs = maxRisk,
            suggestedLots = suggestedLots,
            lotSize = lotSize,
            dailyStopBreached = false,
            weeklyStopBreached = false,
            riskMessages = msgs
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MASTER AGENT — SYNTHESIZES ALL 3 AGENTS
// ═══════════════════════════════════════════════════════════════════════════════
object SetupMasterAgent {

    fun analyze(input: SetupAgentInput): SetupMasterVerdict {
        val logs = mutableListOf<String>()

        // Run all sub-agents
        logs.add("[Agent 1] TimeContextAgent — running...")
        val timeResult = TimeContextAgent.analyze(input)
        logs.add("[Agent 1] Window: ${timeResult.windowStatus} | Active setups: ${timeResult.activeSetups}")

        logs.add("[Agent 2] RuleCheckAgent — running...")
        val ruleResult = RuleCheckAgent.analyze(input)
        logs.add("[Agent 2] S1 met: ${ruleResult.setup1Met} | S2 dir: ${ruleResult.setup2Direction} | S3 met: ${ruleResult.setup3Met}")

        logs.add("[Agent 3] RiskGuardAgent — running...")
        val riskResult = RiskGuardAgent.analyze(input)
        logs.add("[Agent 3] Safe lots: ${riskResult.suggestedLots} | Capital safe: ${riskResult.capitalSafe}")

        logs.add("[Master] Synthesizing verdict...")

        val reasoning = mutableListOf<String>()
        val warnings  = mutableListOf<String>()

        // ── Window closed → immediate NO-GO ──
        if (timeResult.windowStatus == "CLOSED") {
            return SetupMasterVerdict(
                verdict = "NO-GO", setup = "N/A", side = "N/A", confidence = 0,
                entryNote = "Outside trading window",
                stopNote = "-", targetNote = "-",
                reasoning = listOf(timeResult.timeMessage),
                warnings = listOf("No new trades allowed at this time."),
                agentLogs = logs
            )
        }

        if (timeResult.windowStatus == "WATCH") {
            return SetupMasterVerdict(
                verdict = "WATCH", setup = "Setup 1", side = "N/A", confidence = 20,
                entryNote = "Wait — candle forming",
                stopNote = "Set after candle closes",
                targetNote = "2× risk from entry",
                reasoning = listOf(timeResult.timeMessage, "Mark candle HIGH and LOW for ORB"),
                warnings = warnings,
                agentLogs = logs
            )
        }

        // ── Expiry hero zero (Setup 3) ──
        if (input.isExpiryDay && input.hhmm in 1315..1400) {
            reasoning.add("Expiry day 13:15 — Setup 3 Hero Zero window")
            reasoning.addAll(ruleResult.setup3Issues)

            if (!ruleResult.setup3Met) {
                warnings.add("Setup 3 rules NOT met — do not trade")
                return SetupMasterVerdict(
                    verdict = "NO-GO", setup = "Setup 3", side = "N/A",
                    confidence = 10,
                    entryNote = "Conditions not met",
                    stopNote = "No stop — full premium is risk",
                    targetNote = "3× buy price",
                    reasoning = reasoning, warnings = warnings, agentLogs = logs
                )
            }

            val side3 = if (input.priceAboveOpen && input.priceAboveVwap) "CE" else "PE"
            val maxP = when {
                input.index == "NIFTY"     && input.optionStrike == "ATM"   -> "Rs 24"
                input.index == "BANKNIFTY" && input.optionStrike == "ATM"   -> "Rs 36"
                input.index == "SENSEX"    && input.optionStrike == "ATM"   -> "Rs 84"
                else -> "Rs 7"
            }
            reasoning.add("Risk: 0.25-0.5% capital only")
            riskResult.riskMessages.forEach { reasoning.add(it) }

            return SetupMasterVerdict(
                verdict = "GO", setup = "Setup 3 — Hero Zero", side = side3,
                confidence = 65,
                entryNote = "Buy $side3 ATM. Max price: $maxP",
                stopNote = "No stop loss — whole premium is risk",
                targetNote = "Sell at 3× buy price OR 15:15",
                reasoning = reasoning, warnings = warnings, agentLogs = logs
            )
        }

        // ── Setup 1 ORB ──
        if (1 in timeResult.activeSetups) {
            reasoning.add(timeResult.timeMessage)
            reasoning.addAll(ruleResult.setup1Issues)

            val s2Filter = ruleResult.setup2Direction
            val s1Side = when {
                input.currentPrice > input.rangeHigh && input.currentPrice > input.vwap -> "CE"
                input.currentPrice < input.rangeLow  && input.currentPrice < input.vwap -> "PE"
                else -> "N/A"
            }

            // Check Setup 2 conflict
            if (s2Filter == "CE_ONLY" && s1Side == "PE") {
                warnings.add("Setup 2 says CE only — PE trade NOT allowed today")
                return SetupMasterVerdict(
                    verdict = "NO-GO", setup = "Setup 1", side = "PE",
                    confidence = 5,
                    entryNote = "PE blocked by Setup 2 direction filter",
                    stopNote = "-", targetNote = "-",
                    reasoning = reasoning, warnings = warnings, agentLogs = logs
                )
            }

            if (!ruleResult.setup1Met || s1Side == "N/A") {
                warnings.addAll(ruleResult.setup1Issues.filter { it.contains("SKIP") || it.contains("wait") || it.contains("no") })
                return SetupMasterVerdict(
                    verdict = "WAIT", setup = "Setup 1", side = "N/A",
                    confidence = 25,
                    entryNote = "Entry conditions not yet met",
                    stopNote = "Mid of range: (${input.rangeHigh} + ${input.rangeLow}) / 2",
                    targetNote = "Entry + 2× risk",
                    reasoning = reasoning, warnings = warnings, agentLogs = logs
                )
            }

            // All rules met
            riskResult.riskMessages.forEach { reasoning.add(it) }
            if (riskResult.suggestedLots == 0) {
                warnings.add("Capital too small for 1 lot — PAPER TRADE this signal")
            }
            if (s2Filter == "CE_ONLY") reasoning.add("Setup 2 STRONG GREEN confirms: CE only today ✓")

            val mid = (input.rangeHigh + input.rangeLow) / 2.0
            val risk = input.currentPrice - mid
            val target = input.currentPrice + (2 * risk)

            val conf = when {
                s2Filter == "CE_ONLY" && s1Side == "CE" -> 72
                s2Filter == "PE_ONLY" && s1Side == "PE" -> 72
                riskResult.suggestedLots > 0            -> 58
                else                                     -> 45
            }

            return SetupMasterVerdict(
                verdict = "GO",
                setup = "Setup 1 — ORB",
                side = s1Side,
                confidence = conf,
                entryNote = "Buy $s1Side at next candle open (≈ ${input.currentPrice})",
                stopNote  = "Index stop at %.1f (mid of range)".format(mid),
                targetNote = "Index target at %.1f (2× risk)".format(target),
                reasoning = reasoning,
                warnings = if (riskResult.suggestedLots == 0) listOf("Paper trade only — capital < 1 lot") else warnings,
                agentLogs = logs
            )
        }

        // ── Default WAIT ──
        return SetupMasterVerdict(
            verdict = "WAIT", setup = "No setup", side = "N/A", confidence = 0,
            entryNote = "No active setup right now",
            stopNote = "-", targetNote = "-",
            reasoning = listOf("Window open but no setup conditions entered yet", "Fill in price, range, VWAP in the form above"),
            warnings = emptyList(),
            agentLogs = logs
        )
    }
}
