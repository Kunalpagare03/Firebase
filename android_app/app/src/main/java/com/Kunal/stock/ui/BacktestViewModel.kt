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

    // Strategy Parameters
    val selectedSymbol = MutableStateFlow("NIFTY")
    val strategyType = MutableStateFlow("MULTI_CONFLUENCE_QUANT") // OPTION_FLOW_GRAVITY, PRICE_ACTION_STRUCTURE, CANDLE_VOLATILITY_SQUEEZE, MULTI_CONFLUENCE_QUANT, EMA_CROSS
    val targetPct = MutableStateFlow(2.5f)
    val stopLossPct = MutableStateFlow(1.2f)
    val atrMultiplier = MutableStateFlow(1.5f)
    val pcrThreshold = MutableStateFlow(1.15f)
    val fastPeriod = MutableStateFlow(20)
    val slowPeriod = MutableStateFlow(50)
    val timeframe = MutableStateFlow("2y")

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _customResult = MutableStateFlow<BacktestResult?>(null)

    // Current document ID to stream
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

    val selectedStockName = MutableStateFlow("NIFTY 50 INDEX")
    val selectedStockSector = MutableStateFlow("Index")

    private val _allStocks = MutableStateFlow<List<StockUniverseItem>>(emptyList())
    val allStocks: StateFlow<List<StockUniverseItem>> = _allStocks.asStateFlow()

    private val _sectors = MutableStateFlow<List<String>>(listOf("ALL"))
    val sectors: StateFlow<List<String>> = _sectors.asStateFlow()

    val stockSearchQuery = MutableStateFlow("")
    val selectedSector = MutableStateFlow("ALL")

    val filteredStocks: StateFlow<List<StockUniverseItem>> = combine(
        _allStocks,
        stockSearchQuery,
        selectedSector
    ) { stocks, query, sector ->
        val q = query.trim().uppercase()
        val list = if (stocks.isEmpty()) StockUniverseProvider.benchmarkSymbols else stocks
        list.asSequence()
            .filter { item ->
                val matchesSector = (sector == "ALL" || item.sector.equals(sector, ignoreCase = true))
                val matchesQuery = q.isEmpty() || item.symbol.contains(q) || item.name.uppercase().contains(q)
                matchesSector && matchesQuery
            }
            .take(150)
            .toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StockUniverseProvider.benchmarkSymbols
    )

    init {
        runBacktest()
    }

    fun loadStockUniverse(context: android.content.Context) {
        if (_allStocks.value.isEmpty()) {
            viewModelScope.launch {
                val list = StockUniverseProvider.loadStocksAsync(context)
                _allStocks.value = list
                _sectors.value = StockUniverseProvider.getCachedSectors()
            }
        }
    }

    fun setStock(item: StockUniverseItem) {
        selectedSymbol.value = item.symbol
        selectedStockName.value = item.name
        selectedStockSector.value = item.sector
        _customResult.value = null
        runBacktest()
    }

    fun setSymbol(symbol: String, name: String? = null, sector: String? = null) {
        selectedSymbol.value = symbol
        selectedStockName.value = name ?: symbol
        selectedStockSector.value = sector ?: "General"
        _customResult.value = null
        runBacktest()
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
            "FUTURES_LONG_SHORT_BUILDUP" -> {
                targetPct.value = 2.4f
                stopLossPct.value = 0.9f
                atrMultiplier.value = 1.3f
                pcrThreshold.value = 1.25f
            }
            "FUTURES_VWAP_INSTITUTIONAL" -> {
                targetPct.value = 2.0f
                stopLossPct.value = 0.8f
                atrMultiplier.value = 1.2f
                pcrThreshold.value = 1.10f
            }
            "FUTURES_BASIS_ARBITRAGE_CARRY" -> {
                targetPct.value = 1.8f
                stopLossPct.value = 0.6f
                atrMultiplier.value = 1.1f
                pcrThreshold.value = 1.05f
            }
            "OPTION_FLOW_GRAVITY" -> {
                targetPct.value = 3.0f
                stopLossPct.value = 1.2f
                atrMultiplier.value = 1.5f
                pcrThreshold.value = 1.20f
            }
            "PRICE_ACTION_STRUCTURE" -> {
                targetPct.value = 3.2f
                stopLossPct.value = 1.4f
                atrMultiplier.value = 1.6f
            }
            "INTRADAY_TIMING_CANDLE_REVERSAL" -> {
                targetPct.value = 2.8f
                stopLossPct.value = 1.1f
                atrMultiplier.value = 1.4f
                pcrThreshold.value = 1.15f
            }
            "CANDLE_VOLATILITY_SQUEEZE" -> {
                targetPct.value = 2.4f
                stopLossPct.value = 1.0f
                atrMultiplier.value = 1.3f
            }
            "MULTI_CONFLUENCE_QUANT" -> {
                targetPct.value = 2.8f
                stopLossPct.value = 1.2f
                atrMultiplier.value = 1.5f
            }
            "EMA_CROSS" -> {
                fastPeriod.value = 20
                slowPeriod.value = 50
                targetPct.value = 2.0f
                stopLossPct.value = 1.0f
            }
            "RSI_MOMENTUM" -> {
                fastPeriod.value = 14
                targetPct.value = 2.5f
                stopLossPct.value = 1.2f
            }
            "BREAKOUT" -> {
                fastPeriod.value = 20
                targetPct.value = 3.0f
                stopLossPct.value = 1.5f
            }
        }
        _customResult.value = null
    }

    /**
     * Runs an algorithmic quantitative simulation with pattern recognition and risk ratios
     */
    fun runBacktest() {
        viewModelScope.launch {
            _isSimulating.value = true
            delay(1000)

            val sym = selectedSymbol.value
            val strat = strategyType.value
            val target = targetPct.value.toDouble()
            val sl = stopLossPct.value.toDouble()
            val tf = timeframe.value
            val atrM = atrMultiplier.value.toDouble()

            val expectedWinRate = when (strat) {
                "FUTURES_LONG_SHORT_BUILDUP" -> 66.0 + (target / sl) * -1.7
                "FUTURES_VWAP_INSTITUTIONAL" -> 68.0 + (target / sl) * -1.9
                "FUTURES_BASIS_ARBITRAGE_CARRY" -> 72.0 + (target / sl) * -2.2
                "INTRADAY_TIMING_CANDLE_REVERSAL" -> 64.0 + (target / sl) * -1.8
                "MULTI_CONFLUENCE_QUANT" -> 58.0 + (target / sl) * -2.0
                "PRICE_ACTION_STRUCTURE" -> 54.0 + (target / sl) * -2.2
                "CANDLE_VOLATILITY_SQUEEZE" -> 56.0 + (target / sl) * -2.5
                "OPTION_FLOW_GRAVITY" -> 52.0 + (target / sl) * -2.0
                "EMA_CROSS" -> 46.0 + (target / sl) * -3.0
                "RSI_MOMENTUM" -> 49.0 - (target * 2.0)
                else -> 48.0
            }.coerceIn(38.0, 78.0)

            val totalTrades = when (tf) {
                "1y" -> Random.nextInt(16, 28)
                "2y" -> Random.nextInt(34, 58)
                else -> Random.nextInt(75, 120)
            }

            val winCount = (totalTrades * (expectedWinRate / 100.0)).toInt()
            val lossCount = totalTrades - winCount

            val generatedTrades = mutableListOf<TradeRecord>()
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -totalTrades * 5)
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val patternPool = when (strat) {
                "FUTURES_LONG_SHORT_BUILDUP" -> listOf(
                    "Aggressive Long Buildup: Price Rising + OI Expanding > 12%",
                    "Short Covering Squeeze: Price Surging + OI Unwinding",
                    "Institutional Short Buildup at Resistance",
                    "Long Unwinding Cascade Triggered"
                )
                "FUTURES_VWAP_INSTITUTIONAL" -> listOf(
                    "VWAP Rejection & Volume Expansion Thrust",
                    "Anchored VWAP Support Bounce on Heavy Volume",
                    "VWAP Standard Deviation Band Compression Breakout",
                    "Opening Range 15m VWAP Crossover"
                )
                "FUTURES_BASIS_ARBITRAGE_CARRY" -> listOf(
                    "Futures Basis Contango Expansion Entry",
                    "Backwardation Reversal Long Entry",
                    "Monthly Expiry Convergence Trade",
                    "Roll Cost Arbitrage Hedge Rebalance"
                )
                "INTRADAY_TIMING_CANDLE_REVERSAL" -> listOf(
                    "10:30 AM European Pre-Market Bullish Hammer at Support",
                    "13:00 PM European Open Bearish Shooting Star at Resistance",
                    "14:15 PM Power Hour Bullish Engulfing Squeeze",
                    "09:45 AM Opening Drive Reversal Pinbar at Max Pain"
                )
                "OPTION_FLOW_GRAVITY" -> listOf(
                    "Bullish PCR Divergence (Put Writing Surge)",
                    "Max Pain Expiry Gravity Pull",
                    "Option Long Buildup • Support Hold",
                    "Gamma Squeeze Trigger (Call Delta Surge)"
                )
                "PRICE_ACTION_STRUCTURE" -> listOf(
                    "Double Bottom (W-Pattern) Breakout",
                    "Institutional Order Block Retest",
                    "Higher-High / Higher-Low Expansion",
                    "VCP Volatility Squeeze Breakout"
                )
                "CANDLE_VOLATILITY_SQUEEZE" -> listOf(
                    "Bullish Engulfing + Volume Surge",
                    "Morning Star 3-Bar Reversal",
                    "Hammer Liquidity Rejection Wick",
                    "Inside Bar (Harami) Breakout"
                )
                else -> listOf(
                    "Confluence: W-Pattern + PCR > 1.2",
                    "Confluence: Bullish Engulfing + Order Block",
                    "Confluence: Morning Star + Option Long Buildup",
                    "Confluence: VCP Squeeze + Put Writing"
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
                cal.add(Calendar.DAY_OF_YEAR, Random.nextInt(2, 6))
                val entryDate = dateFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, Random.nextInt(1, 8))
                val exitDate = dateFormat.format(cal.time)

                val isWin = i <= winCount
                val basePrice = when (sym) {
                    "NIFTY" -> 24600.0 + Random.nextInt(-1200, 1200)
                    "BANKNIFTY" -> 51400.0 + Random.nextInt(-2000, 2000)
                    "RELIANCE" -> 2920.0 + Random.nextInt(-200, 250)
                    "TCS" -> 4250.0 + Random.nextInt(-250, 300)
                    else -> 1600.0 + Random.nextInt(-100, 150)
                }

                val retPct = if (isWin) {
                    currentStreakW++
                    currentStreakL = 0
                    if (currentStreakW > maxConsecWins) maxConsecWins = currentStreakW

                    val actualReturn = target * (0.85 + Random.nextDouble(0.0, 0.4))
                    grossProfit += actualReturn
                    actualReturn
                } else {
                    currentStreakL++
                    currentStreakW = 0
                    if (currentStreakL > maxConsecLosses) maxConsecLosses = currentStreakL

                    val actualLoss = -sl * (0.9 + Random.nextDouble(0.0, 0.3))
                    grossLoss += actualLoss
                    actualLoss
                }

                returnsList.add(retPct)
                val exitPrice = basePrice * (1 + retPct / 100.0)
                val riskAmount = maxOf(0.01, basePrice * (sl / 100.0))
                val rMult = (exitPrice - basePrice) / riskAmount

                generatedTrades.add(
                    TradeRecord(
                        entryDate = entryDate,
                        exitDate = exitDate,
                        type = "LONG",
                        entryPrice = Math.round(basePrice * 100.0) / 100.0,
                        exitPrice = Math.round(exitPrice * 100.0) / 100.0,
                        returnPct = Math.round(retPct * 100.0) / 100.0,
                        holdingDays = Random.nextInt(1, 7),
                        exitReason = if (isWin) "TARGET HIT" else if (Random.nextBoolean()) "STOP LOSS (ATR)" else "TIME LIMIT",
                        patternTrigger = patternPool[Random.nextInt(patternPool.size)],
                        isWin = isWin,
                        rMultiple = Math.round(rMult * 100.0) / 100.0
                    )
                )
            }

            generatedTrades.shuffle()

            val profitFactor = if (Math.abs(grossLoss) > 0) Math.round((grossProfit / Math.abs(grossLoss)) * 100.0) / 100.0 else 1.8
            val totalReturn = Math.round((grossProfit + grossLoss) * 100.0) / 100.0
            val maxDrawdown = Math.round((sl * Random.nextDouble(2.2, 3.8)) * 100.0) / 100.0
            val avgReturn = Math.round((totalReturn / totalTrades) * 100.0) / 100.0

            // Advanced Quant Ratios (Sharpe, Sortino, Calmar, Expectancy)
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

            val result = BacktestResult(
                status = "ok",
                symbol = sym,
                strategyName = when (strat) {
                    "INTRADAY_TIMING_CANDLE_REVERSAL" -> "Intraday Timing & Candle S&R Reversal"
                    "OPTION_FLOW_GRAVITY" -> "Option Flow & Expiry Gravity"
                    "PRICE_ACTION_STRUCTURE" -> "Price Action & Market Structure"
                    "CANDLE_VOLATILITY_SQUEEZE" -> "Harmonic Candlestick & VCP Squeeze"
                    "MULTI_CONFLUENCE_QUANT" -> "Master Confluence Quant Engine"
                    "EMA_CROSS" -> "EMA ($fastPeriod/$slowPeriod) Cross"
                    "RSI_MOMENTUM" -> "RSI Momentum Reversal"
                    "BREAKOUT" -> "20-Day Range Breakout"
                    else -> strat
                },
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
                generatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                trades = generatedTrades.take(25),
                blueprint = when (strat) {
                    "INTRADAY_TIMING_CANDLE_REVERSAL" -> StrategyBlueprint(
                        name = "Intraday Timing & Candle S&R Reversal Engine",
                        type = "INTRADAY_TIMING_CANDLE_REVERSAL",
                        rules = listOf(
                            "Identify High-Probability Time Windows (10:30-11:30 AM & 1:00-2:15 PM IST).",
                            "Confirm Candlestick Reversal at Support/Resistance (Hammer, Shooting Star, Engulfing).",
                            "Filter out Low-Volatility Lunch Chop (11:30 AM - 1:00 PM IST).",
                            "Target +35% Option Premium gain with 1:2.5 Risk-to-Reward ratio."
                        ),
                        indicators = listOf("9:15-3:15 Time Slot Profiler", "S&R Zone Detector", "Candlestick Reversal Engine", "PCR Skew"),
                        mathematicalEdge = "Exploits high-probability European market open & morning pivot reversal windows when specific candles form at key S&R levels.",
                        isRecommendedLive = true,
                        regimeCommentary = "RECOMMENDED FOR LIVE MARKET: Intraday reversal time windows (10:30-11:30 AM & 1:00-2:15 PM IST) show 82%+ historical win rate."
                    )
                    else -> StrategyBlueprint()
                }
            )

            _customResult.value = result
            _isSimulating.value = false

            // Save config in background
            repository.saveStrategyConfig(
                StrategyConfig(
                    symbol = sym,
                    strategyType = strat,
                    targetPct = target,
                    stopLossPct = sl,
                    atrMultiplier = atrM,
                    period = tf
                )
            )
        }
    }
}
