package com.Kunal.stock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Kunal.stock.data.FirestoreRepository
import com.Kunal.stock.data.LiveQuote
import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.ScanStatus
import com.Kunal.stock.data.StockScanItem
import com.Kunal.stock.data.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OptionSentimentViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _selectedSymbol = MutableStateFlow("NIFTY")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    val scanStatus: StateFlow<ScanStatus> = repository.streamOptionStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScanStatus()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveQuote: StateFlow<LiveQuote?> = _selectedSymbol
        .flatMapLatest { symbol -> repository.streamLiveQuote(symbol) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isTriggering = MutableStateFlow(false)
    val isTriggering: StateFlow<Boolean> = _isTriggering.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<OptionSentimentData>> = _selectedSymbol
        .flatMapLatest { symbol ->
            repository.streamOptionSentiment(symbol)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    fun setSymbol(symbol: String) {
        _selectedSymbol.value = symbol
    }

    fun triggerOptionRefresh(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isTriggering.value) return
        _isTriggering.value = true
        repository.triggerScan("option") { success, error ->
            _isTriggering.value = false
            onComplete(success, error)
        }
    }
}

class StockScanViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val scanStatus: StateFlow<ScanStatus> = repository.streamScanStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScanStatus()
        )

    private val _isTriggering = MutableStateFlow(false)
    val isTriggering: StateFlow<Boolean> = _isTriggering.asStateFlow()

    private val rawScansFlow: Flow<UiState<List<StockScanItem>>> = repository.streamStockScans()

    val filteredScans: StateFlow<UiState<List<StockScanItem>>> = combine(
        rawScansFlow,
        _searchQuery,
        _selectedFilter,
        _selectedCategory
    ) { state, query, filter, category ->
        when (state) {
            is UiState.Success -> {
                var list = state.data

                // Category filter (Intraday, Swing, Positional)
                if (category != "ALL") {
                    list = list.filter {
                        it.pattern.contains(category, ignoreCase = true) ||
                        it.recommendation.contains(category, ignoreCase = true)
                    }
                }

                // Query search
                if (query.isNotBlank()) {
                    list = list.filter {
                        it.symbol.contains(query, ignoreCase = true) ||
                        it.sector.contains(query, ignoreCase = true)
                    }
                }

                // Metric filters
                list = when (filter) {
                    "Score ≥ 4" -> list.filter { it.score >= 4 }
                    "Gainers" -> list.filter { it.changePct > 0 }
                    "Top R:R" -> list.sortedByDescending { it.riskReward }
                    else -> list
                }
                UiState.Success(list)
            }
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: String) {
        _selectedFilter.value = filter
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun triggerLiveScan(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isTriggering.value) return
        _isTriggering.value = true
        repository.triggerScan("stock") { success, error ->
            _isTriggering.value = false
            onComplete(success, error)
        }
    }
}
