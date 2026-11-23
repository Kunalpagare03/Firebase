package com.Kunal.stock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Kunal.stock.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val strategyType = MutableStateFlow("EMA_CROSS")
    val targetPct = MutableStateFlow(2.5f)
    val stopLossPct = MutableStateFlow(1.2f)
    val atrMultiplier = MutableStateFlow(1.5f)
    val pcrThreshold = MutableStateFlow(1.15f)
    val fastPeriod = MutableStateFlow(20)
    val slowPeriod = MutableStateFlow(50)
    val timeframe = MutableStateFlow("2y")

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _customResult = MutableStateFlow<UiState<BacktestResult>?>(null)
    private var backtestJob: Job? = null
    private var backtestGeneration = 0

    val uiState: StateFlow<UiState<BacktestResult>> = _customResult
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    val selectedStockName = MutableStateFlow("NIFTY 50 INDEX")
    val selectedStockSector = MutableStateFlow("Index")

    private val _allStocks = MutableStateFlow<List<FnoUniverseItem>>(emptyList())
    val allStocks: StateFlow<List<FnoUniverseItem>> = _allStocks.asStateFlow()

    private val _sectors = MutableStateFlow<List<String>>(listOf("ALL", "Index", "F&O Stock"))
    val sectors: StateFlow<List<String>> = _sectors.asStateFlow()

    val stockSearchQuery = MutableStateFlow("")
    val selectedSector = MutableStateFlow("ALL")

    val filteredStocks: StateFlow<List<FnoUniverseItem>> = combine(
        _allStocks,
        stockSearchQuery,
        selectedSector
    ) { stocks, query, sector ->
        val q = query.trim().uppercase()
        val list = if (stocks.isEmpty()) FnoUniverseProvider.benchmarkFno else stocks
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
        initialValue = FnoUniverseProvider.benchmarkFno
    )

    init {
        runBacktest()
    }

    fun loadStockUniverse(context: android.content.Context) {
        if (_allStocks.value.isEmpty()) {
            viewModelScope.launch {
                val list = FnoUniverseProvider.loadFnoAsync(context)
                _allStocks.value = list
                _sectors.value = FnoUniverseProvider.getCachedSegments()
            }
        }
    }

    fun setStock(item: FnoUniverseItem) {
        selectedSymbol.value = item.symbol
        selectedStockName.value = item.name
        selectedStockSector.value = item.sector
        _customResult.value = null
        runBacktest()
    }

    fun setSymbol(symbol: String, name: String? = null, sector: String? = null) {
        selectedSymbol.value = symbol
        selectedStockName.value = name ?: symbol
        selectedStockSector.value = sector ?: "F&O"
        _customResult.value = null
        runBacktest()
    }

    fun setStrategyType(type: String) {
        applyPreset(type)
    }

    fun setTarget(target: Float) {
        targetPct.value = (target * 10).toInt() / 10f
        invalidateResult()
    }

    fun setStopLoss(sl: Float) {
        stopLossPct.value = (sl * 10).toInt() / 10f
        invalidateResult()
    }

    fun setAtrMultiplier(mult: Float) {
        atrMultiplier.value = (mult * 10).toInt() / 10f
        invalidateResult()
    }

    fun setTimeframe(tf: String) {
        timeframe.value = tf
        invalidateResult()
    }

    fun applyPreset(type: String) {
        require(type in HistoricalBacktestEngine.supportedStrategyTypes) {
            "Unsupported daily-price strategy: $type"
        }
        strategyType.value = type
        when (type) {
            "EMA_CROSS" -> {
                fastPeriod.value = 20
                slowPeriod.value = 50
                targetPct.value = 2.0f
                stopLossPct.value = 1.0f
            }
            "EMA_PULLBACK" -> {
                fastPeriod.value = 20
                slowPeriod.value = 50
                targetPct.value = 2.5f
                stopLossPct.value = 1.2f
            }
            "RSI_REVERSAL" -> {
                fastPeriod.value = 14
                slowPeriod.value = 50
                targetPct.value = 2.0f
                stopLossPct.value = 1.0f
            }
            "RSI_MOMENTUM" -> {
                fastPeriod.value = 14
                slowPeriod.value = 50
                targetPct.value = 2.5f
                stopLossPct.value = 1.2f
            }
            "BREAKOUT" -> {
                fastPeriod.value = 20
                targetPct.value = 3.0f
                stopLossPct.value = 1.5f
            }
            "RVOL_BREAKOUT" -> {
                fastPeriod.value = 20
                targetPct.value = 2.5f
                stopLossPct.value = 1.2f
            }
            "ATR_SQUEEZE_BREAKOUT" -> {
                fastPeriod.value = 20
                targetPct.value = 3.0f
                stopLossPct.value = 1.5f
                atrMultiplier.value = 1.5f
            }
        }
        invalidateResult()
    }

    /**
     * Runs an algorithmic quantitative simulation with pattern recognition and risk ratios
     */
    fun runBacktest() {
        val generation = ++backtestGeneration
        backtestJob?.cancel()
        backtestJob = viewModelScope.launch {
            _isSimulating.value = true
            _customResult.value = UiState.Loading
            try {
                val sym = selectedSymbol.value
                val strat = strategyType.value
                val result = HistoricalBacktestEngine.run(
                    symbol = sym,
                    strategyType = strat,
                    timeframe = timeframe.value,
                    targetPct = targetPct.value.toDouble(),
                    stopLossPct = stopLossPct.value.toDouble(),
                    atrMultiplier = atrMultiplier.value.toDouble(),
                    fastPeriod = fastPeriod.value,
                    slowPeriod = slowPeriod.value,
                    strategyName = when (strat) {
                    "EMA_CROSS" -> "EMA 20/50 Crossover"
                    "EMA_PULLBACK" -> "EMA20 Trend Pullback"
                    "RSI_REVERSAL" -> "RSI Oversold Reversal"
                    "RSI_MOMENTUM" -> "RSI 50-Cross Trend"
                    "BREAKOUT" -> "20-Day Range Breakout"
                    "RVOL_BREAKOUT" -> "Relative-Volume Breakout"
                    "ATR_SQUEEZE_BREAKOUT" -> "ATR Squeeze + Volume Breakout"
                    else -> strat.replace('_', ' ')
                }
                )
                if (generation != backtestGeneration) return@launch
                _customResult.value = UiState.Success(result)
                repository.saveBacktestHistory(result) { saved, error ->
                    if (!saved) android.util.Log.e("BacktestViewModel", "Could not save backtest history: $error")
                }

                repository.saveStrategyConfig(
                    StrategyConfig(
                        symbol = sym,
                        strategyType = strat,
                        fastPeriod = fastPeriod.value,
                        slowPeriod = slowPeriod.value,
                        targetPct = targetPct.value.toDouble(),
                        stopLossPct = stopLossPct.value.toDouble(),
                        atrMultiplier = atrMultiplier.value.toDouble(),
                        period = timeframe.value
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == backtestGeneration) {
                    _customResult.value = UiState.Error(
                        error.localizedMessage ?: "Historical price data is unavailable."
                    )
                }
            } finally {
                if (generation == backtestGeneration) _isSimulating.value = false
            }
        }
    }

    private fun invalidateResult() {
        backtestGeneration++
        backtestJob?.cancel()
        backtestJob = null
        _isSimulating.value = false
        _customResult.value = UiState.Loading
    }

}
