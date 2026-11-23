package com.Kunal.stock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Kunal.stock.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // Strategy Parameters exclusively for Equity Stock Backtesting
    val selectedSymbol = MutableStateFlow("RELIANCE")
    val strategyType = MutableStateFlow("SWING_BREAKOUT")
    val targetPct = MutableStateFlow(5.0f)
    val stopLossPct = MutableStateFlow(2.0f)
    val atrMultiplier = MutableStateFlow(1.5f)
    val fastPeriod = MutableStateFlow(20)
    val slowPeriod = MutableStateFlow(50)
    val timeframe = MutableStateFlow("2y")

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _customResult = MutableStateFlow<UiState<BacktestResult>?>(null)
    private var backtestJob: Job? = null
    private var backtestGeneration = 0

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
            custom ?: cloudState
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    // Stock Universe & Selection (5,229 Stocks - 34 Sectors)
    val selectedStockName = MutableStateFlow("Reliance Industries")
    val selectedStockSector = MutableStateFlow("Energy")

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
        // Automatically compute initial simulation for baseline responsiveness
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
            "MOMENTUM_PULLBACK" -> {
                targetPct.value = 5.5f
                stopLossPct.value = 2.2f
                atrMultiplier.value = 1.5f
            }
            "SUPPORT_REVERSAL" -> {
                targetPct.value = 4.0f
                stopLossPct.value = 1.6f
                atrMultiplier.value = 1.3f
            }
            "MULTI_CONFLUENCE_STOCK" -> {
                targetPct.value = 5.0f
                stopLossPct.value = 2.0f
                atrMultiplier.value = 1.5f
            }
        }
        _customResult.value = null
    }

    /**
     * Runs an equity stock strategy backtesting simulation
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
                    "SWING_BREAKOUT" -> "20-Day Swing Breakout"
                    "EMA_TREND_FOLLOWING" -> "EMA Trend Following"
                    "RVOL_MOMENTUM_SURGE" -> "Relative Volume Momentum"
                    "SQUEEZE_BREAKOUT" -> "Volatility Squeeze Breakout"
                    "PRICE_ACTION_STRUCTURE" -> "Price Action Structure"
                    "MOMENTUM_PULLBACK" -> "Momentum Pullback"
                    "SUPPORT_REVERSAL" -> "Support Reversal"
                    "MULTI_CONFLUENCE_STOCK" -> "Multi-Confluence Price Strategy"
                    else -> strat.replace('_', ' ')
                }
                )
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
                _customResult.value = UiState.Error(
                    error.localizedMessage ?: "Historical price data is unavailable."
                )
            } finally {
                if (generation == backtestGeneration) _isSimulating.value = false
            }
        }
    }

}
