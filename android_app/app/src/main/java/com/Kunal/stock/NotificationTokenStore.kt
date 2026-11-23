package com.Kunal.stock

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

internal object NotificationTokenStore {
    private const val TAG = "TradeAlertNotifications"

    fun register(userId: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("notification_tokens")
            .document(token)
            .set(
                mapOf(
                    "platform" to "android",
                    "updated_at" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to register this device for trade alerts.", error)
            }
    }

    fun unregister(userId: String, token: String, onComplete: (Exception?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("notification_tokens")
            .document(token)
            .delete()
            .addOnCompleteListener { task ->
                onComplete(if (task.isSuccessful) null else task.exception)
            }
    }
}
