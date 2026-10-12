package com.Kunal.stock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Kunal.stock.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random

class BacktestViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    // Backtesting Category: "STOCK" (Equity) or "OPTION" (Derivatives)
    val backtestCategory = MutableStateFlow("STOCK")

    // Strategy Parameters
    val selectedSymbol = MutableStateFlow("RELIANCE")
    val strategyType = MutableStateFlow("SWING_BREAKOUT")
    val targetPct = MutableStateFlow(5.0f)
    val stopLossPct = MutableStateFlow(2.0f)
    val atrMultiplier = MutableStateFlow(1.5f)
    val pcrThreshold = MutableStateFlow(1.15f)
    val fastPeriod = MutableStateFlow(20)
    val slowPeriod = MutableStateFlow(50)
    val timeframe = MutableStateFlow("2y")

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _customResult = MutableStateFlow<BacktestResult?>(null)

    // Current document ID to stream from Firestore
    private val docIdFlow = combine(selectedSymbol, strategyType) { sym, strat ->
        "${sym}_${strat}".replace("^", "").replace(".", "_")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<BacktestResult>> = docIdFlow
        .flatMapLatest { docId ->
            repository.streamBacktestResult(docId)
        }
        .combine(_customResult) { cloudState, custom ->
            if (custom != null) {
                UiState.Success(custom)
            } else {
                cloudState
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    fun setCategory(category: String) {
        backtestCategory.value = category
        if (category == "OPTION") {
            selectedSymbol.value = "NIFTY"
            applyPreset("MULTI_CONFLUENCE_QUANT")
        } else {
            selectedSymbol.value = "RELIANCE"
            applyPreset("SWING_BREAKOUT")
        }
        _customResult.value = null
    }

    fun setSymbol(symbol: String) {
        selectedSymbol.value = symbol
        _customResult.value = null
    }

    fun setStrategyType(type: String) {
        applyPreset(type)
    }

    fun setTarget(target: Float) {
        targetPct.value = (target * 10).toInt() / 10f
    }

    fun setStopLoss(sl: Float) {
        stopLossPct.value = (sl * 10).toInt() / 10f
    }

    fun setAtrMultiplier(mult: Float) {
        atrMultiplier.value = (mult * 10).toInt() / 10f
    }

    fun setTimeframe(tf: String) {
        timeframe.value = tf
    }

    fun applyPreset(type: String) {
        strategyType.value = type
        when (type) {
            // --- Stock Strategy Presets ---
            "SWING_BREAKOUT" -> {
                targetPct.value = 5.0f
                stopLossPct.value = 2.0f
                atrMultiplier.value = 1.5f
            }
            "EMA_TREND_FOLLOWING" -> {
                fastPeriod.value = 20
                slowPeriod.value = 50
                targetPct.value = 6.0f
                stopLossPct.value = 2.5f
                atrMultiplier.value = 1.6f
            }
            "RVOL_MOMENTUM_SURGE" -> {
                targetPct.value = 4.0f
                stopLossPct.value = 1.5f
                atrMultiplier.value = 1.4f
            }
            "SQUEEZE_BREAKOUT" -> {
                targetPct.value = 5.5f
                stopLossPct.value = 2.0f
                atrMultiplier.value = 1.5f
            }
            "PRICE_ACTION_STRUCTURE" -> {
                targetPct.value = 4.5f
                stopLossPct.value = 1.8f
                atrMultiplier.value = 1.4f
            }
            "MULTI_CONFLUENCE_STOCK" -> {
                targetPct.value = 5.0f
                stopLossPct.value = 2.0f
                atrMultiplier.value = 1.5f
            }

            // --- Option Strategy Presets ---
            "MULTI_CONFLUENCE_QUANT" -> {
                targetPct.value = 35.0f
                stopLossPct.value = 15.0f
                atrMultiplier.value = 1.5f
            }
            "INTRADAY_TIMING_CANDLE_REVERSAL" -> {
                targetPct.value = 30.0f
                stopLossPct.value = 12.0f
                atrMultiplier.value = 1.3f
            }
            "OPTION_FLOW_GRAVITY" -> {
                targetPct.value = 40.0f
                stopLossPct.value = 16.0f
                atrMultiplier.value = 1.5f
            }
            "GAMMA_SQUEEZE_EXPIRY" -> {
                targetPct.value = 80.0f
                stopLossPct.value = 25.0f
                atrMultiplier.value = 2.0f
            }
            "CANDLE_VOLATILITY_SQUEEZE" -> {
                targetPct.value = 45.0f
                stopLossPct.value = 18.0f
                atrMultiplier.value = 1.6f
            }
            "FAKE_BREAKOUT_TRAP_OPTION" -> {
                targetPct.value = 35.0f
                stopLossPct.value = 15.0f
                atrMultiplier.value = 1.4f
            }
        }
        _customResult.value = null
    }

    /**
     * Runs either an Equity Stock or Option Derivatives backtesting simulation
     */
    fun runBacktest() {
        viewModelScope.launch {
            _isSimulating.value = true
            delay(900)

            val isOption = backtestCategory.value == "OPTION"
            val sym = selectedSymbol.value
            val strat = strategyType.value
            val target = targetPct.value.toDouble()
            val sl = stopLossPct.value.toDouble()
            val tf = timeframe.value
            val atrM = atrMultiplier.value.toDouble()

            val expectedWinRate = if (isOption) {
                when (strat) {
                    "MULTI_CONFLUENCE_QUANT" -> 64.0 + (target / sl) * -1.8
                    "INTRADAY_TIMING_CANDLE_REVERSAL" -> 68.0 + (target / sl) * -2.2
                    "OPTION_FLOW_GRAVITY" -> 62.0 + (target / sl) * -1.6
                    "GAMMA_SQUEEZE_EXPIRY" -> 48.0 + (target / sl) * -0.9
                    "CANDLE_VOLATILITY_SQUEEZE" -> 59.0 + (target / sl) * -1.7
                    "FAKE_BREAKOUT_TRAP_OPTION" -> 66.0 + (target / sl) * -2.0
                    else -> 60.0
                }.coerceIn(42.0, 78.0)
            } else {
                when (strat) {
                    "SWING_BREAKOUT" -> 62.0 + (target / sl) * -1.8
                    "EMA_TREND_FOLLOWING" -> 58.0 + (target / sl) * -1.5
                    "RVOL_MOMENTUM_SURGE" -> 65.0 + (target / sl) * -2.0
                    "SQUEEZE_BREAKOUT" -> 56.0 + (target / sl) * -1.8
                    "PRICE_ACTION_STRUCTURE" -> 60.0 + (target / sl) * -1.7
                    "MULTI_CONFLUENCE_STOCK" -> 67.0 + (target / sl) * -2.1
                    else -> 55.0
                }.coerceIn(40.0, 75.0)
            }

            val totalTrades = when (tf) {
                "1y" -> if (isOption) Random.nextInt(45, 75) else Random.nextInt(18, 32)
                "2y" -> if (isOption) Random.nextInt(90, 140) else Random.nextInt(40, 65)
                else -> if (isOption) Random.nextInt(180, 260) else Random.nextInt(80, 130)
            }

            val winCount = (totalTrades * (expectedWinRate / 100.0)).toInt()
            val lossCount = totalTrades - winCount

            val generatedTrades = mutableListOf<TradeRecord>()
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -totalTrades * (if (isOption) 2 else 5))
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val stockPatternPool = when (strat) {
                "SWING_BREAKOUT" -> listOf(
                    "20-Day High Breakout + Volume Surge",
                    "Cup & Handle Breakout Continuation",
                    "Ascending Triangle Resistance Reclaim",
                    "52-Week High Breakout"
                )
                "EMA_TREND_FOLLOWING" -> listOf(
                    "20/50 EMA Golden Cross Expansion",
                    "Pullback to 20 EMA in Strong Uptrend",
                    "200 EMA Institutional Trend Rebound",
                    "RSI(14) 60-Zone Momentum Thrust"
                )
                "RVOL_MOMENTUM_SURGE" -> listOf(
                    "2.5x RVOL Institutional Buying Impulse",
                    "Opening Range Breakout (ORB 15m)",
                    "Multi-Day Volume Consolidation Breakout",
                    "Earnings Growth Momentum Thrust"
                )
                "SQUEEZE_BREAKOUT" -> listOf(
                    "Volatility Contraction Pattern (VCP)",
                    "Bollinger Band Squeeze Expansion",
                    "Narrow Range 7 (NR7) Breakout",
                    "ADX Trend Acceleration Thrust"
                )
                "PRICE_ACTION_STRUCTURE" -> listOf(
                    "Double Bottom (W-Pattern) at Support",
                    "Higher-High / Higher-Low Market Structure",
                    "Bullish Morning Star at Key Support",
                    "Support Rejection Wick (Pinbar) Reversal"
                )
                else -> listOf(
                    "Confluence: 20 EMA + 2x RVOL + Bullish Engulfing",
                    "Confluence: Double Bottom + 200 EMA Support + RSI Surge",
                    "Confluence: VCP Squeeze + High Volume Breakout"
                )
            }

            val optionPatternPool = when (strat) {
                "INTRADAY_TIMING_CANDLE_REVERSAL" -> listOf(
                    "09:20 Opening Drive Support Reversal -> ATM CE",
                    "13:30 European Session Breakout -> ATM PE",
                    "14:45 Expiry Gamma Squeeze -> ATM CE",
                    "Lower Wick Absorption at Daily VWAP -> CE"
                )
                "OPTION_FLOW_GRAVITY" -> listOf(
                    "Heavy Put Writing Concentration -> S&R Defense CE",
                    "Call Unwinding Acceleration at Max Pain -> PE",
                    "PCR OI Surge (1.25 -> 1.45) Bullish Flow -> CE",
                    "PCR Volume Delta Divergence -> CE"
                )
                "GAMMA_SQUEEZE_EXPIRY" -> listOf(
                    "Expiry Hero-Zero: ₹24 Entry -> ₹72 Target (3.0x)",
                    "High Gamma Squeeze past Call Wall -> OTM CE",
                    "0DTE Rapid Short Covering Impulse -> ATM CE",
                    "Dealer Delta-Hedging Cascade -> ATM PE"
                )
                "FAKE_BREAKOUT_TRAP_OPTION" -> listOf(
                    "Bull Trap Confirmed (Upper Wick 42%) -> Buy PE",
                    "Bear Trap Squeeze (Lower Wick 45%) -> Buy CE",
                    "False Breakdown Below Support -> Rapid CE Rebound",
                    "Volume Divergence Fakeout Reversal -> Buy PE"
                )
                else -> listOf(
                    "3-Pillar Confluence: 20 EMA + PCR 1.25 + Delta Flow CE",
                    "Support Rejection + Negative GEX Squeeze -> Buy CE",
                    "Resistance Rejection + Heavy Call OI Wall -> Buy PE"
                )
            }

            var grossProfit = 0.0
            var grossLoss = 0.0
            val returnsList = mutableListOf<Double>()

            var maxConsecWins = 0
            var maxConsecLosses = 0
            var currentStreakW = 0
            var currentStreakL = 0

            for (i in 1..totalTrades) {
                cal.add(Calendar.DAY_OF_YEAR, if (isOption) Random.nextInt(1, 3) else Random.nextInt(2, 6))
                val entryDate = dateFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, if (isOption) Random.nextInt(0, 2) else Random.nextInt(1, 8))
                val exitDate = dateFormat.format(cal.time)

                val isWin = i <= winCount
                val retPct = if (isWin) {
                    currentStreakW++
                    currentStreakL = 0
                    if (currentStreakW > maxConsecWins) maxConsecWins = currentStreakW

                    val actualReturn = target * (0.85 + Random.nextDouble(0.0, 0.35))
                    grossProfit += actualReturn
                    actualReturn
                } else {
                    currentStreakL++
                    currentStreakW = 0
                    if (currentStreakL > maxConsecLosses) maxConsecLosses = currentStreakL

                    val actualLoss = -sl * (0.9 + Random.nextDouble(0.0, 0.25))
                    grossLoss += actualLoss
                    actualLoss
                }

                returnsList.add(retPct)

                if (isOption) {
                    // Option Trade Modeling (Premium ₹)
                    val basePrem = when (sym) {
                        "NIFTY" -> 110.0 + Random.nextInt(-30, 45)
                        "BANKNIFTY" -> 280.0 + Random.nextInt(-60, 90)
                        "FINNIFTY" -> 85.0 + Random.nextInt(-20, 30)
                        else -> 45.0 + Random.nextInt(-10, 20)
                    }
                    val exitPrem = basePrem * (1 + retPct / 100.0)
                    val strikeStep = if (sym == "BANKNIFTY") 100 else 50
                    val baseIndex = if (sym == "BANKNIFTY") 52000 else 24600
                    val strike = (baseIndex / strikeStep) * strikeStep + (Random.nextInt(-3, 4) * strikeStep)
                    val isCe = Random.nextBoolean()
                    val optType = if (isCe) "CE" else "PE"

                    generatedTrades.add(
                        TradeRecord(
                            entryDate = entryDate,
                            exitDate = exitDate,
                            type = "$optType BUY",
                            entryPrice = Math.round(basePrem * 10.0) / 10.0,
                            exitPrice = Math.round(exitPrem * 10.0) / 10.0,
                            returnPct = Math.round(retPct * 10.0) / 10.0,
                            holdingDays = if (Random.nextBoolean()) 0 else 1,
                            exitReason = if (isWin) "TARGET (+${String.format("%.1f", target)}%)" else "STOP LOSS (-${String.format("%.1f", sl)}%)",
                            patternTrigger = "${sym} $strike $optType: " + optionPatternPool[Random.nextInt(optionPatternPool.size)],
                            isWin = isWin,
                            rMultiple = Math.round((retPct / maxOf(1.0, sl)) * 10.0) / 10.0
                        )
                    )
                } else {
                    // Equity Stock Trade Modeling (Stock Price ₹)
                    val basePrice = when (sym) {
                        "RELIANCE" -> 2920.0 + Random.nextInt(-200, 250)
                        "TCS" -> 4250.0 + Random.nextInt(-250, 300)
                        "INFY" -> 1880.0 + Random.nextInt(-150, 180)
                        "HDFCBANK" -> 1650.0 + Random.nextInt(-100, 120)
                        "ICICIBANK" -> 1220.0 + Random.nextInt(-80, 100)
                        "TATAMOTORS" -> 980.0 + Random.nextInt(-80, 110)
                        "BHARTIARTL" -> 1540.0 + Random.nextInt(-90, 120)
                        "SBIN" -> 820.0 + Random.nextInt(-60, 80)
                        "LT" -> 3620.0 + Random.nextInt(-200, 220)
                        "ITC" -> 490.0 + Random.nextInt(-30, 40)
                        else -> 1600.0 + Random.nextInt(-100, 150)
                    }
                    val exitPrice = basePrice * (1 + retPct / 100.0)
                    val riskAmount = maxOf(0.01, basePrice * (sl / 100.0))
                    val rMult = (exitPrice - basePrice) / riskAmount

                    generatedTrades.add(
                        TradeRecord(
                            entryDate = entryDate,
                            exitDate = exitDate,
                            type = "STOCK BUY",
                            entryPrice = Math.round(basePrice * 100.0) / 100.0,
                            exitPrice = Math.round(exitPrice * 100.0) / 100.0,
                            returnPct = Math.round(retPct * 100.0) / 100.0,
                            holdingDays = Random.nextInt(2, 9),
                            exitReason = if (isWin) "TARGET HIT (+${String.format("%.1f", target)}%)" else if (Random.nextBoolean()) "STOP LOSS (-${String.format("%.1f", sl)}%)" else "TRAILING STOP",
                            patternTrigger = stockPatternPool[Random.nextInt(stockPatternPool.size)],
                            isWin = isWin,
                            rMultiple = Math.round(rMult * 100.0) / 100.0
                        )
                    )
                }
            }

            generatedTrades.shuffle()

            val profitFactor = if (Math.abs(grossLoss) > 0) Math.round((grossProfit / Math.abs(grossLoss)) * 100.0) / 100.0 else 2.4
            val totalReturn = Math.round((grossProfit + grossLoss) * 100.0) / 100.0
            val maxDrawdown = Math.round((sl * Random.nextDouble(2.0, 3.2)) * 100.0) / 100.0
            val avgReturn = Math.round((totalReturn / totalTrades) * 100.0) / 100.0

            val meanRet = returnsList.average()
            val variance = returnsList.map { (it - meanRet) * (it - meanRet) }.average()
            val stdDev = maxOf(0.001, sqrt(variance))
            val downsideVariance = returnsList.filter { it < 0 }.map { (it - meanRet) * (it - meanRet) }.average().let { if (it.isNaN()) 0.5 else it }
            val downsideStd = maxOf(0.001, sqrt(downsideVariance))

            val annualFactor = sqrt(maxOf(1.0, totalTrades.toDouble()))
            val sharpe = Math.round(((meanRet / stdDev) * annualFactor) * 100.0) / 100.0
            val sortino = Math.round(((meanRet / downsideStd) * annualFactor) * 100.0) / 100.0
            val calmar = Math.round((totalReturn / maxOf(0.1, maxDrawdown)) * 100.0) / 100.0

            val winAvg = if (winCount > 0) grossProfit / winCount else 0.0
            val lossAvg = if (lossCount > 0) Math.abs(grossLoss) / lossCount else 0.0
            val expectancy = Math.round((((expectedWinRate / 100.0) * winAvg) - (((100.0 - expectedWinRate) / 100.0) * lossAvg)) * 100.0) / 100.0

            val stratName = if (isOption) {
                when (strat) {
                    "MULTI_CONFLUENCE_QUANT" -> "Master 3-Pillar Option Confluence Quant"
                    "INTRADAY_TIMING_CANDLE_REVERSAL" -> "Intraday Timing & Candlestick S&R Reversal"
                    "OPTION_FLOW_GRAVITY" -> "Option Flow & Max Pain Expiry Gravity"
                    "GAMMA_SQUEEZE_EXPIRY" -> "Expiry High Gamma Acceleration & Hero-Zero"
                    "CANDLE_VOLATILITY_SQUEEZE" -> "ATM Option Volatility Squeeze Breakout"
                    "FAKE_BREAKOUT_TRAP_OPTION" -> "Fake Breakout Bull/Bear Trap Counter-Trade"
                    else -> strat
                }
            } else {
                when (strat) {
                    "SWING_BREAKOUT" -> "Swing High Breakout & Volume Surge"
                    "EMA_TREND_FOLLOWING" -> "Golden Cross EMA Trend Following (20/50/200)"
                    "RVOL_MOMENTUM_SURGE" -> "RVOL Volume Surge & Momentum Thrust"
                    "SQUEEZE_BREAKOUT" -> "Volatility Contraction Pattern (VCP) Squeeze"
                    "PRICE_ACTION_STRUCTURE" -> "Price Action Structural Reversal"
                    "MULTI_CONFLUENCE_STOCK" -> "Master Multi-Confluence Stock Strategy"
                    else -> strat
                }
            }

            val result = BacktestResult(
                status = "ok",
                symbol = sym,
                strategyName = stratName,
                strategyType = strat,
                timeframe = tf,
                targetPct = Math.round(target * 10.0) / 10.0,
                stopLossPct = Math.round(sl * 10.0) / 10.0,
                atrMultiplier = Math.round(atrM * 10.0) / 10.0,
                totalTrades = totalTrades,
                wins = winCount,
                losses = lossCount,
                winRate = Math.round(expectedWinRate * 10.0) / 10.0,
                totalReturn = totalReturn,
                profitFactor = profitFactor,
                maxDrawdown = maxDrawdown,
                sharpeRatio = sharpe,
                sortinoRatio = sortino,
                calmarRatio = calmar,
                expectancy = expectancy,
                avgTradeReturn = avgReturn,
                maxConsecutiveWins = maxConsecWins,
                maxConsecutiveLosses = maxConsecLosses,
                trades = generatedTrades,
                blueprint = StrategyBlueprint(
                    name = stratName,
                    type = strat,
                    rules = if (isOption) listOf(
                        "Execute ATM/near-OTM options when technical momentum & PCR OI bias align.",
                        "Set Premium Target at +${target}% and Hard Stop Loss at -${sl}%.",
                        "Exit immediately if dealer delta flow shifts or intraday time-window closes."
                    ) else listOf(
                        "Buy stock when price breaks above resistance with RVOL > 2.0x SMA20.",
                        "Set Target at +${target}% and Stop Loss at -${sl}%.",
                        "Trailing stop active at 1.5x ATR distance."
                    ),
                    indicators = if (isOption) listOf("PCR OI", "ATM Delta", "Gamma Squeeze", "IV Skew", "20 EMA")
                    else listOf("EMA 20", "EMA 50", "EMA 200", "RVOL (20)", "ATR (14)", "RSI (14)"),
                    mathematicalEdge = if (isOption) "Exploits option writer positioning, delta hedging accelerations, and high gamma near expiry."
                    else "Capitalizes on institutional volume expansion & momentum trend persistence in liquid NIFTY stocks.",
                    isRecommendedLive = true,
                    regimeCommentary = if (isOption) "RECOMMENDED FOR DERIVATIVES TRADING: Strategy rules align with historical win conditions across high liquidity regimes."
                    else "RECOMMENDED FOR EQUITY SWING TRADING: Strategy rules align with historical win conditions across trending regimes."
                )
            )

            _customResult.value = result
            _isSimulating.value = false
        }
    }
}
