package com.Kunal.stock.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Kunal.stock.data.PlaybookScanner
import com.Kunal.stock.data.StockScanPerformanceRecord
import com.Kunal.stock.data.StockScanPerformanceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StockScannerHubScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Live Scanner", "ChartLink", "Archive", "Ledger")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Crossfade(targetState = selectedTab, label = "StockScannerTabTransition") { tab ->
            when (tab) {
                0 -> StockScanScreen()
                1 -> PlaybookScannerTab()
                2 -> CombinedArchiveScreen()
                else -> PlaybookLedgerStatsTab()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CombinedArchiveScreen() {
    var archiveSource by rememberSaveable { mutableIntStateOf(0) } // 0 = Scanner Folders, 1 = ChartLink Folders
    val context = LocalContext.current
    val performanceStore = remember(context) { StockScanPerformanceStore(context.applicationContext) }
    var performanceRecords by remember { mutableStateOf<List<StockScanPerformanceRecord>>(emptyList()) }
    var performanceSearch by rememberSaveable { mutableStateOf("") }
    var refreshingRecordId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(performanceStore, performanceSearch) {
        performanceRecords = withContext(Dispatchers.IO) {
            performanceStore.loadRecords(performanceSearch)
        }
    }
    DisposableEffect(performanceStore) {
        onDispose { performanceStore.close() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = archiveSource == 0,
                onClick = { archiveSource = 0 },
                label = { Text("📡 Scanner Day Folders") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = archiveSource == 1,
                onClick = { archiveSource = 1 },
                label = { Text("📊 ChartLink Day Folders") },
                modifier = Modifier.weight(1f)
            )
        }
        if (archiveSource == 0) {
            StockPerformanceTrackerContent(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                records = performanceRecords,
                searchQuery = performanceSearch,
                onSearchChange = { performanceSearch = it },
                refreshingRecordId = refreshingRecordId,
                onRefresh = { record ->
                    if (refreshingRecordId == null) {
                        scope.launch {
                            refreshingRecordId = record.id
                            try {
                                val candles = withContext(Dispatchers.IO) {
                                    PlaybookScanner.fetchHistoryForPerformance(record.symbol)
                                }
                                if (candles.isNotEmpty()) {
                                    performanceRecords = withContext(Dispatchers.IO) {
                                        performanceStore.recordPriceHistory(record.symbol, candles, performanceSearch)
                                    }
                                }
                            } catch (_: Exception) {}
                            refreshingRecordId = null
                        }
                    }
                }
            )
        } else {
            PlaybookArchiveTab()
        }
    }
}
