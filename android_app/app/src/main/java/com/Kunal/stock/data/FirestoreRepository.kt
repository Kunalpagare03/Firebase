package com.Kunal.stock.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getStockScans(): Map<String, Any>? {
        return try {
            val snapshot = db.collection("stock_scans").document("latest").get().await()
            snapshot.data
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOptionSentiment(): Map<String, Any>? {
        return try {
            // Default to NIFTY for now
            val snapshot = db.collection("option_sentiment").document("NIFTY").get().await()
            snapshot.data
        } catch (e: Exception) {
            null
        }
    }
}
