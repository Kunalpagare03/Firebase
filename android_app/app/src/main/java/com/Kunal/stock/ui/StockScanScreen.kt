package com.Kunal.stock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Kunal.stock.data.FirestoreRepository
import kotlinx.coroutines.launch

@Composable
fun StockScanScreen(repository: FirestoreRepository = remember { FirestoreRepository() }) {
    var data by remember { mutableStateOf<Map<String, Any>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            data = repository.getStockScans()
        }
    }

    if (data == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val results = data!!["results"] as? List<Map<String, Any>> ?: emptyList()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { stock ->
                StockItem(stock)
            }
        }
    }
}

@Composable
fun StockItem(stock: Map<String, Any>) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stock["symbol"].toString(), style = MaterialTheme.typography.titleLarge)
            Text(text = "Score: ${stock["score"]} | RSI: ${stock["rsi14"]}")
            Text(text = "Pattern: ${stock["pattern"]}")
            Text(text = "Target: ${stock["target_2pct"]} | Stop: ${stock["stop_loss"]}")
            Text(text = "Signals: ${(stock["signals"] as? List<*>)?.joinToString(", ") ?: ""}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
