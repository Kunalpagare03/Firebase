package com.Kunal.stock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Kunal.stock.data.FirestoreRepository
import kotlinx.coroutines.launch

@Composable
fun OptionSentimentScreen(repository: FirestoreRepository = remember { FirestoreRepository() }) {
    var data by remember { mutableStateOf<Map<String, Any>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            data = repository.getOptionSentiment()
        }
    }

    if (data == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(text = "NIFTY Option Sentiment", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            SentimentCard(data!!)
        }
    }
}

@Composable
fun SentimentCard(data: Map<String, Any>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Overall Read: ${data["combined_sentiment_read"]}", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(text = "Spot Price: ${data["spot_price"]}")
            Text(text = "PCR (OI): ${data["pcr_oi"]}")
            Text(text = "PCR (Volume): ${data["pcr_volume"]}")
            Text(text = "Max Pain Strike: ${data["max_pain"]}")
            Text(text = "OI Buildup: ${data["oi_buildup_overall"]}")
            
            val votes = data["votes"] as? Map<String, Any>
            if (votes != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Votes: Bullish ${votes["bullish"]} vs Bearish ${votes["bearish"]}")
            }
        }
    }
}
