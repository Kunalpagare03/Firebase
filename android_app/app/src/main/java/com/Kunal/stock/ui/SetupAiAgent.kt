package com.Kunal.stock.ui

import com.Kunal.stock.data.LiveQuote
import com.Kunal.stock.data.LiveTradeSignal
import com.Kunal.stock.data.OptionDirectionalConsensus
import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.OptionSignalConsensus
import com.Kunal.stock.data.UiState
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

internal data class LiveSetupCheckResult(
    val symbol: String,
    val status: String,
    val message: String,
    val spotFresh: Boolean,
    val chainFresh: Boolean,
    val currentExpiryDay: Boolean = false,
    val consensus: OptionDirectionalConsensus? = null,
    val signal: LiveTradeSignal? = null
)

internal object LiveSetupCheck {
    fun evaluate(
        state: UiState<OptionSentimentData>,
        quote: LiveQuote?,
        nowMillis: Long = System.currentTimeMillis()
    ): LiveSetupCheckResult {
        val source = (state as? UiState.Success)?.data
        if (source == null) {
            val message = when (state) {
                is UiState.Error -> state.message
                UiState.Loading -> "Waiting for the live option analysis feed."
                is UiState.Success -> "Live option analysis is unavailable."
            }
            return LiveSetupCheckResult("", "WAIT", message, false, false)
        }

        val tickMillis = quote?.tickAtEpoch
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it * 1000.0).toLong() }
        val quoteAgeMillis = tickMillis?.let { nowMillis - it }
        val spotFresh = quote?.spotPrice?.let { it.isFinite() && it > 0.0 } == true &&
            quoteAgeMillis != null && quoteAgeMillis in 0..5_000L
        val chainTimestamp = parseOptionChainUpdatedAt(source.chainUpdatedAt)
        val chainAgeMillis = chainTimestamp?.let { nowMillis - it }
        val chainFresh = chainAgeMillis != null && chainAgeMillis in 0..360_000L
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = india }
            .format(java.util.Date(nowMillis))
        val currentExpiryDay = source.chainExpiry == today
        val liveData = source.copy(
            spotPrice = quote?.spotPrice?.takeIf { it.isFinite() && it > 0.0 } ?: source.spotPrice,
            spotHistory = (source.spotHistory + (quote?.spotHistory ?: emptyList()))
                .distinctBy { it.time }
                .sortedBy { it.time }
                .takeLast(10_000)
        )
        val consensus = OptionSignalConsensus.evaluate(
            liveData,
            spotQuoteFresh = spotFresh,
            optionChainFresh = chainFresh,
            nowMillis = nowMillis
        )
        return LiveSetupCheckResult(
            symbol = source.symbol,
            status = if (consensus.tradeEligible) "CONDITIONAL CHECK PASSED" else "WAIT",
            message = if (consensus.tradeEligible) {
                "Live price, structure, levels, chain flow and freshness gates currently align. Verify execution risk before acting."
            } else {
                consensus.blocker
            },
            spotFresh = spotFresh,
            chainFresh = chainFresh,
            currentExpiryDay = currentExpiryDay,
            consensus = consensus,
            signal = OptionSignalConsensus.buildLiveSignal(liveData, consensus)
        )
    }
}

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
    val index: String = "NIFTY",        // "NIFTY" | "BANKNIFTY" | "FINNIFTY"
    val priceAboveOpen: Boolean = false,
    val priceAboveVwap: Boolean = false,
    val priceContextConfirmed: Boolean = false,
    // Capital
    val capital: Double = 0.0,
    val lotSize: Int = 0,
    val maxLossPerLot: Double = 0.0,
    val dailyPnlRs: Double = 0.0,
    val weeklyPnlRs: Double = 0.0,
    val selectedSetup: Int = 1          // 1-3 are checked by the agent
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
            h >= 1515 -> {
                issues.add("15:15 reached. Close positions.")
                "CLOSED"
            }
            expiry && h in 1315..1400 -> {
                activeSetups.add(3)
                "OPEN"
            }
            h < 915 -> {
                issues.add("Market is not open yet.")
                "CLOSED"
            }
            h < 930 -> {
                issues.add("9:15-9:30: first candle forming. Do not enter.")
                "WATCH"
            }
            h < 945 -> {
                issues.add("9:30-9:45: check opening range and wait.")
                "WATCH"
            }
            h in 945..1030 -> {
                activeSetups.add(1)
                if (h >= 1015) activeSetups.add(2)
                "OPEN"
            }
            h < 1330 -> {
                issues.add("10:30-13:30: manage existing positions; no new entry.")
                "CLOSED"
            }
            else -> {
                issues.add("No new entries after 13:30.")
                "CLOSED"
            }
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
        val rangeIsValid = input.rangeHigh.isFinite() && input.rangeLow.isFinite() &&
            input.niftyLevel.isFinite() && input.rangeHigh > input.rangeLow &&
            input.rangeLow > 0.0 && input.niftyLevel > 0.0
        val rangeSize = if (rangeIsValid) {
            (input.rangeHigh - input.rangeLow) / input.niftyLevel * 100.0
        } else {
            s1Met = false
            s1Issues.add("Enter a valid opening range and underlying price.")
            -1.0
        }

        if (rangeSize > 0.6) {
            s1Met = false
            s1Issues.add("Range size %.2f%% > 0.6%% — SKIP Setup 1 today".format(rangeSize))
        } else if (rangeSize > 0) {
            s1Issues.add("Range size %.2f%% < 0.6%% — OK".format(rangeSize))
        }

        if (rangeIsValid && input.currentPrice.isFinite() && input.currentPrice > 0.0 &&
            input.vwap.isFinite() && input.vwap > 0.0
        ) {
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
        } else {
            s1Met = false
            s1Issues.add("Enter current price and VWAP to validate a breakout.")
        }

        // Setup 2 — 1-hour direction
        var s2Direction = "N/A"
        val firstHourValid = input.firstHourOpen.isFinite() && input.firstHourClose.isFinite() &&
            input.firstHourHigh.isFinite() && input.firstHourLow.isFinite() &&
            input.avgFirstHourSize20d.isFinite() &&
            input.firstHourOpen > 0.0 && input.firstHourClose > 0.0 &&
            input.firstHourHigh >= maxOf(input.firstHourOpen, input.firstHourClose) &&
            input.firstHourLow <= minOf(input.firstHourOpen, input.firstHourClose) &&
            input.avgFirstHourSize20d > 0.0
        if (firstHourValid) {
            val body = abs(input.firstHourClose - input.firstHourOpen)
            val size = input.firstHourHigh - input.firstHourLow
            val bodyPct = if (size > 0) body / size * 100.0 else 0.0
            val sizeRatio = size / input.avgFirstHourSize20d
            val isGreen = input.firstHourClose > input.firstHourOpen

            s2Direction = when {
                isGreen && bodyPct >= 60 && sizeRatio >= 1.2 -> "CE_ONLY"
                else -> "NEUTRAL"
            }
        }

        // Setup 3 checks
        val s3Issues = mutableListOf<String>()
        var s3Met = true

        if (!input.optionPrice.isFinite() || input.optionPrice <= 0.0) {
            s3Met = false
            s3Issues.add("A live option premium is required.")
        } else if (input.optionPrice !in 3.0..50.0) {
            s3Met = false
            s3Issues.add("Premium is outside the engine's ₹3-₹50 screening range.")
        } else {
            s3Issues.add("Premium is inside the engine's screening range; this is not a value or profit check.")
        }

        val mixed = input.priceAboveOpen != input.priceAboveVwap
        if (!input.priceContextConfirmed) {
            s3Met = false
            s3Issues.add("Confirm the current price is checked against both today's open and VWAP.")
        } else if (mixed) {
            s3Met = false
            s3Issues.add("Mixed signal (open/VWAP disagree) — NO TRADE for Setup 3")
        } else {
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
        val capitalValid = input.capital.isFinite() && input.capital > 0.0
        val lotSizeValid = input.lotSize > 0
        val enteredMaxLoss = input.maxLossPerLot.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val riskPerLot = if (input.selectedSetup == 3 && input.optionPrice.isFinite() &&
            input.optionPrice > 0.0 && lotSizeValid
        ) {
            maxOf(input.optionPrice * input.lotSize, enteredMaxLoss)
        } else {
            enteredMaxLoss
        }
        val maxRisk = if (capitalValid) input.capital * 0.005 else 0.0
        val dailyPnl = input.dailyPnlRs.takeIf { it.isFinite() } ?: 0.0
        val weeklyPnl = input.weeklyPnlRs.takeIf { it.isFinite() } ?: 0.0
        val dailyLossLimit = if (capitalValid) input.capital * 0.01 else 0.0
        val weeklyLossLimit = if (capitalValid) input.capital * 0.03 else 0.0
        val dailyStopBreached = capitalValid && dailyPnl <= -dailyLossLimit
        val weeklyStopBreached = capitalValid && weeklyPnl <= -weeklyLossLimit
        val suggestedLots = if (riskPerLot > 0.0) (maxRisk / riskPerLot).toInt() else 0
        val msgs = mutableListOf<String>()

        if (!capitalValid) msgs.add("Capital is missing or invalid.")
        else msgs.add("Configured risk budget: ₹%.0f (0.5%% of ₹%.0f).".format(maxRisk, input.capital))
        if (dailyStopBreached) msgs.add("Daily loss stop reached: journal P&L ₹%.0f; limit is −₹%.0f.".format(dailyPnl, dailyLossLimit))
        else if (capitalValid) msgs.add("Daily journal P&L: ₹%.0f; daily stop: −₹%.0f (1%% of capital).".format(dailyPnl, dailyLossLimit))
        if (weeklyStopBreached) msgs.add("Weekly loss stop reached: journal P&L ₹%.0f; limit is −₹%.0f.".format(weeklyPnl, weeklyLossLimit))
        else if (capitalValid) msgs.add("Weekly journal P&L: ₹%.0f; weekly stop: −₹%.0f (3%% of capital).".format(weeklyPnl, weeklyLossLimit))
        if (!lotSizeValid) msgs.add("Enter the current exchange lot size; no fixed lot-size assumption is used.")
        if (riskPerLot <= 0.0) msgs.add("Enter maximum loss per lot to check affordability.")
        else if (suggestedLots == 0) msgs.add("Configured risk budget does not cover one lot.")
        else msgs.add("Affordable lots by entered risk: $suggestedLots.")

        return RiskGuardResult(
            capitalSafe = capitalValid && lotSizeValid && riskPerLot > 0.0 &&
                suggestedLots > 0 && !dailyStopBreached && !weeklyStopBreached,
            maxRiskRs = maxRisk,
            suggestedLots = suggestedLots,
            lotSize = input.lotSize,
            dailyStopBreached = dailyStopBreached,
            weeklyStopBreached = weeklyStopBreached,
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
        if (input.selectedSetup == 3) {
            if (!input.isExpiryDay || input.hhmm !in 1315..1400) {
                return SetupMasterVerdict(
                    verdict = "WAIT",
                    setup = "Setup 3 — Hero Zero",
                    side = "N/A",
                    confidence = 0,
                    entryNote = "Configured expiry window is not active",
                    stopNote = "Full premium may be lost",
                    targetNote = "No target is validated",
                    reasoning = listOf(timeResult.timeMessage),
                    warnings = listOf("Use the live Hero Zero screen for current expiry and chain checks."),
                    agentLogs = logs
                )
            }
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
            reasoning.add("Risk budget is based on entered capital, lot size and full-premium exposure.")
            riskResult.riskMessages.forEach { reasoning.add(it) }
            if (!riskResult.capitalSafe) {
                return SetupMasterVerdict(
                    verdict = "WATCH",
                    setup = "Setup 3 — Hero Zero",
                    side = side3,
                    confidence = 0,
                    entryNote = "Candidate only — risk capacity is not validated",
                    stopNote = "Maximum loss can be the full premium",
                    targetNote = "No target is guaranteed",
                    reasoning = reasoning,
                    warnings = listOf("Enter capital and verify current contract risk; no buy instruction is issued."),
                    agentLogs = logs
                )
            }

            return SetupMasterVerdict(
                verdict = "GO", setup = "Setup 3 — Hero Zero", side = side3,
                confidence = 0,
                entryNote = "Entered checks pass; confirm the live chain before any decision",
                stopNote = "Maximum loss can equal premium × lot size",
                targetNote = "No target or positive expectancy is validated",
                reasoning = reasoning, warnings = warnings, agentLogs = logs
            )
        }

        if (input.selectedSetup == 2) {
            if (2 !in timeResult.activeSetups) {
                return SetupMasterVerdict(
                    verdict = "WAIT", setup = "Setup 2 — First-hour filter", side = "N/A",
                    confidence = 0, entryNote = "Wait until the first-hour candle is complete",
                    stopNote = "-", targetNote = "Filter only; no entry or target",
                    reasoning = listOf(timeResult.timeMessage),
                    warnings = listOf("This filter is not a standalone trade signal."),
                    agentLogs = logs
                )
            }
            val hasDirection = ruleResult.setup2Direction == "CE_ONLY"
            return SetupMasterVerdict(
                verdict = if (hasDirection) "WATCH" else "WAIT",
                setup = "Setup 2 — First-hour filter",
                side = if (hasDirection) "CE" else "N/A",
                confidence = 0,
                entryNote = if (hasDirection) "Strong green first hour: CE direction filter only" else "No valid bullish filter was confirmed",
                stopNote = "-",
                targetNote = "No entry or target from this filter",
                reasoning = listOf(
                    if (hasDirection) "First-hour body and range meet the configured filter."
                    else "Check complete OHLC and 20-day average range inputs."
                ),
                warnings = listOf("Not a calibrated statistic or standalone trade signal."),
                agentLogs = logs
            )
        }

        // ── Setup 1 ORB ──
        if (input.selectedSetup == 1 && 1 in timeResult.activeSetups) {
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
            if (s2Filter == "CE_ONLY") reasoning.add("Setup 2 STRONG GREEN confirms: CE only today ✓")

            val mid = (input.rangeHigh + input.rangeLow) / 2.0
            val risk = abs(input.currentPrice - mid)
            val target = if (s1Side == "CE") input.currentPrice + (2 * risk) else input.currentPrice - (2 * risk)

            val conf = when {
                s2Filter == "CE_ONLY" && s1Side == "CE" -> 0
                else -> 0
            }

            if (!riskResult.capitalSafe) {
                return SetupMasterVerdict(
                    verdict = "WATCH",
                    setup = "Setup 1 — ORB",
                    side = s1Side,
                    confidence = 0,
                    entryNote = "Price rules align; risk capacity is not validated",
                    stopNote = "Index stop at %.1f (mid of range)".format(mid),
                    targetNote = "Index target at %.1f (2× risk)".format(target),
                    reasoning = reasoning,
                    warnings = listOf("Enter capital and verify option premium, lot size, spread, and maximum loss."),
                    agentLogs = logs
                )
            }

            return SetupMasterVerdict(
                verdict = "GO",
                setup = "Setup 1 — ORB",
                side = s1Side,
                confidence = conf,
                entryNote = "Entry rules pass; verify fresh contract and execution risk before any decision",
                stopNote  = "Index stop at %.1f (mid of range)".format(mid),
                targetNote = "Index target at %.1f (2× risk)".format(target),
                reasoning = reasoning,
                warnings = listOf("Rule checklist only; option costs, slippage and live contract premium are not modeled."),
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
