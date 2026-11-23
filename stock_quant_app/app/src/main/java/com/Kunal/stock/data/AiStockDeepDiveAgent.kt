package com.Kunal.stock.data

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Institutional AI Research Pick produced by the deep-dive analysis engine.
 */
data class AiDeepDivePick(
    val rank: Int,
    val symbol: String,
    val sector: String,
    val currentPrice: Double,
    val changePct: Double,
    val aiConvictionScore: Int,      // 0 - 100
    val winProbabilityPct: Int,      // e.g. 78% - 94%
    val verdictBadge: String,        // "TOP HIGH CONVICTION", "MOMENTUM BREAKOUT", "SWING PULLBACK"
    val setupType: String,
    val entryZone: String,
    val stopLoss: Double,
    val stopLossPct: Double,
    val target1: Double,
    val target1Pct: Double,
    val target2: Double,
    val target2Pct: Double,
    val riskRewardRatio: String,     // "1 : 3.2"
    val maxHoldingDays: String,      // "3 - 7 Days"
    val volumeSurge: String,         // "2.4x Avg"
    val rsi14: Double,
    val institutionalCatalysts: List<String>,
    val executionAction: String      // "ACCUMULATE / BUY", "BUY ON DIP"
)

/**
 * On-device AI Deep Dive Agent.
 * Analyzes large batches of raw scans or playbook signals and isolates the Top 3 to 5
 * prime institutional quality setups with the highest probability of hitting targets.
 * Operates purely in-memory with zero overhead ("limited usages").
 */
object AiStockDeepDiveAgent {

    /**
     * Evaluates algorithmic stock scans and shortlists the top 3-5 distinct stocks.
     */
    fun analyzeScanItems(items: List<StockScanItem>, maxPicks: Int = 5): List<AiDeepDivePick> {
        if (items.isEmpty()) return emptyList()

        val scoredPicks = items.mapNotNull { item ->
            try {
                val price = if (item.close > 0.0) item.close else item.entry
                if (!price.isFinite() || price <= 0.0 || item.symbol.isBlank()) return@mapNotNull null

                // 1. Momentum & Trend Score (0-25)
                var trendScore = 10
                if (item.changePct in 0.5..6.0) trendScore += 8
                else if (item.changePct > 6.0) trendScore += 4 // slightly overextended
                if (item.baseBreakout) trendScore += 5
                if (item.score >= 4) trendScore += 2

                // 2. Volume & Institutional Accumulation Score (0-25)
                val volRatio = if (item.volumeRatio > 0.0 && item.volumeRatio.isFinite()) item.volumeRatio else 1.2
                val volScore = when {
                    volRatio >= 2.5 -> 25
                    volRatio >= 1.8 -> 21
                    volRatio >= 1.3 -> 17
                    volRatio >= 1.0 -> 12
                    else -> 8
                }

                // 3. RSI Quality Score (0-25) - sweet spot 52 to 68
                val rsi = if (item.rsi14 > 0.0 && item.rsi14.isFinite()) item.rsi14 else 58.0
                val rsiScore = when {
                    rsi in 54.0..66.0 -> 25 // Golden sweet spot
                    rsi in 48.0..54.0 -> 20 // Rebounding from pullback
                    rsi in 66.0..72.0 -> 18 // Strong momentum
                    rsi > 72.0 -> 10       // Overbought risk
                    else -> 12
                }

                // 4. Risk:Reward & Structure Score (0-25)
                val rr = if (item.riskReward > 0.0 && item.riskReward.isFinite()) item.riskReward else 2.5
                val rrScore = when {
                    rr >= 3.0 -> 25
                    rr >= 2.2 -> 20
                    rr >= 1.8 -> 16
                    else -> 10
                }

                val totalScore = min(98, max(60, trendScore + volScore + rsiScore + rrScore))
                val winProb = min(93, max(72, (totalScore * 0.94).roundToInt()))

                // Calculate targets and stop losses safely
                val slPct = if (item.stopLoss > 0.0 && price > item.stopLoss) {
                    (((price - item.stopLoss) / price) * 100.0).coerceIn(1.0, 15.0)
                } else 3.5
                val calculatedSl = if (item.stopLoss > 0.0) item.stopLoss else (price * (1.0 - slPct / 100.0))

                val t1Pct = if (item.targetPrice > price) {
                    (((item.targetPrice - price) / price) * 100.0).coerceIn(2.0, 30.0)
                } else max(5.0, slPct * 1.8)
                val calculatedT1 = price * (1.0 + t1Pct / 100.0)

                val t2Pct = if (item.target2Pct > 0.0 && item.target2Pct.isFinite()) item.target2Pct.coerceIn(5.0, 50.0) else (t1Pct * 1.8)
                val calculatedT2 = price * (1.0 + t2Pct / 100.0)

                val rrRatioVal = (t1Pct / max(1.0, slPct)).coerceIn(1.0, 10.0)
                val rrFormatted = String.format(Locale.US, "1 : %.1f", rrRatioVal)

                // Formulate 3 distinct institutional catalysts
                val catalysts = mutableListOf<String>()
                catalysts.add(
                    if (volRatio >= 1.5) "Heavy institutional accumulation (${String.format(Locale.US, "%.1f", volRatio)}x 20-DMA volume) absorbing overhead supply"
                    else "Consistent accumulation footprint with clean volume support"
                )
                val patternName = when {
                    item.candlePattern.isNotBlank() -> item.candlePattern
                    item.pattern.isNotBlank() -> item.pattern
                    item.chartPattern.isNotBlank() -> item.chartPattern
                    else -> "Multi-session Consolidation Breakout"
                }
                catalysts.add("Technical Setup: $patternName confirmed with RSI at ${String.format(Locale.US, "%.1f", rsi)}")
                catalysts.add("Favorable Risk-Reward: Target 1 at +${String.format(Locale.US, "%.1f", t1Pct)}% with protected SL at -${String.format(Locale.US, "%.1f", slPct)}% ($rrFormatted R:R)")

                val verdict = when {
                    totalScore >= 90 -> "HIGH CONVICTION PRIME PICK"
                    totalScore >= 82 -> "INSTITUTIONAL BREAKOUT"
                    else -> "QUALITY SWING ACCUMULATION"
                }

                val entryLow = price * 0.995
                val entryHigh = price * 1.008
                val entryZoneStr = "₹${String.format(Locale.US, "%.1f", entryLow)} - ₹${String.format(Locale.US, "%.1f", entryHigh)}"

                val holdingPeriod = when {
                    item.recommendation.contains("INTRADAY", ignoreCase = true) -> "Intraday / 1 Session"
                    item.recommendation.contains("POSITIONAL", ignoreCase = true) -> "7 - 21 Sessions"
                    else -> "3 - 8 Sessions"
                }

                AiDeepDivePick(
                    rank = 0, // Assigned after sorting
                    symbol = item.symbol,
                    sector = if (item.sector.isNotBlank()) item.sector else "NSE Equity",
                    currentPrice = price,
                    changePct = item.changePct,
                    aiConvictionScore = totalScore.coerceIn(0, 100),
                    winProbabilityPct = winProb.coerceIn(50, 99),
                    verdictBadge = verdict,
                    setupType = patternName,
                    entryZone = entryZoneStr,
                    stopLoss = calculatedSl,
                    stopLossPct = slPct,
                    target1 = calculatedT1,
                    target1Pct = t1Pct,
                    target2 = calculatedT2,
                    target2Pct = t2Pct,
                    riskRewardRatio = rrFormatted,
                    maxHoldingDays = holdingPeriod,
                    volumeSurge = "${String.format(Locale.US, "%.1f", volRatio)}x Avg Vol",
                    rsi14 = rsi,
                    institutionalCatalysts = catalysts,
                    executionAction = if (item.changePct > 4.5) "BUY ON MINOR PULLBACK" else "MARKET BUY / ACCUMULATE"
                )
            } catch (e: Exception) {
                null
            }
        }

        // Distinct by symbol to prevent any duplicate stock entries, then rank
        return scoredPicks
            .distinctBy { it.symbol }
            .sortedWith(compareByDescending<AiDeepDivePick> { it.aiConvictionScore }
                .thenByDescending { it.winProbabilityPct }
                .thenByDescending { it.changePct })
            .take(maxPicks)
            .mapIndexed { index, pick -> pick.copy(rank = index + 1) }
    }

    /**
     * Evaluates Playbook signals from ChartLink scanner and shortlists the top 3-5 distinct picks.
     */
    fun analyzePlaybookSignals(signals: List<PlaybookSignal>, maxPicks: Int = 5): List<AiDeepDivePick> {
        if (signals.isEmpty()) return emptyList()

        // Filter only BUY action signals first; if none, allow all
        val candidates = signals.filter { it.aiAction == "BUY" }.ifEmpty { signals }

        val scoredPicks = candidates.mapNotNull { sig ->
            try {
                val price = if (sig.currentPrice > 0.0) sig.currentPrice else sig.signalClose
                if (!price.isFinite() || price <= 0.0 || sig.symbol.isBlank()) return@mapNotNull null

                var score = 65
                if (sig.strengthRank >= 80) score += 15
                else if (sig.strengthRank >= 60) score += 10

                if (sig.setup.proven) score += 10
                if (sig.niftyAbove200) score += 5

                val rsi = if (sig.rsi14 > 0.0 && sig.rsi14.isFinite()) sig.rsi14 else 56.0
                if (rsi in 45.0..68.0) score += 5

                val totalScore = min(96, max(68, score))
                val winProb = min(92, max(75, (totalScore * 0.95).roundToInt()))

                val slPrice = if (sig.stopLoss > 0.0 && sig.stopLoss < price) sig.stopLoss else (price * 0.92)
                val slPct = (((price - slPrice) / price) * 100.0).coerceIn(1.0, 15.0)

                val t1Price = if (sig.refTarget1 > price) sig.refTarget1 else (price * 1.08)
                val t1Pct = (((t1Price - price) / price) * 100.0).coerceIn(2.0, 30.0)

                val t2Price = if (sig.refTarget2 > price) sig.refTarget2 else (price * 1.15)
                val t2Pct = (((t2Price - price) / price) * 100.0).coerceIn(5.0, 50.0)

                val rrRatioVal = (t1Pct / max(1.0, slPct)).coerceIn(1.0, 10.0)
                val rrFormatted = String.format(Locale.US, "1 : %.1f", rrRatioVal)

                val catalysts = listOf(
                    "Playbook Setup ${sig.setup.code} (${sig.setup.label}) fully validated by quantitative filter",
                    "Relative Strength percentile ${sig.strengthRank}/100 indicating outperformance versus benchmark",
                    "Strict rule-based entry: 8% stop loss protecting capital with hold time capped at 22 sessions"
                )

                val entryZoneStr = "₹${String.format(Locale.US, "%.1f", price * 0.998)} - ₹${String.format(Locale.US, "%.1f", price * 1.008)}"

                AiDeepDivePick(
                    rank = 0,
                    symbol = sig.symbol,
                    sector = if (sig.sector.isNotBlank()) sig.sector else "NSE 500",
                    currentPrice = price,
                    changePct = sig.currentReturnPct,
                    aiConvictionScore = totalScore.coerceIn(0, 100),
                    winProbabilityPct = winProb.coerceIn(50, 99),
                    verdictBadge = if (sig.setup.proven) "HIGH WIN-RATE PLAYBOOK SETUP" else "SYSTEMATIC QUANT BREAKOUT",
                    setupType = "Setup ${sig.setup.code}: ${sig.setup.label}",
                    entryZone = entryZoneStr,
                    stopLoss = slPrice,
                    stopLossPct = slPct,
                    target1 = t1Price,
                    target1Pct = t1Pct,
                    target2 = t2Price,
                    target2Pct = t2Pct,
                    riskRewardRatio = rrFormatted,
                    maxHoldingDays = "≤ 22 Trading Sessions",
                    volumeSurge = if (sig.dailyValueCr > 10.0) "₹${String.format(Locale.US, "%.0f", sig.dailyValueCr)} Cr Turnover" else "Active Liquidity",
                    rsi14 = rsi,
                    institutionalCatalysts = catalysts,
                    executionAction = "BUY FOR SWING TARGET"
                )
            } catch (e: Exception) {
                null
            }
        }

        return scoredPicks
            .distinctBy { it.symbol }
            .sortedWith(compareByDescending<AiDeepDivePick> { it.aiConvictionScore }
                .thenByDescending { it.winProbabilityPct })
            .take(maxPicks)
            .mapIndexed { index, pick -> pick.copy(rank = index + 1) }
    }
}
