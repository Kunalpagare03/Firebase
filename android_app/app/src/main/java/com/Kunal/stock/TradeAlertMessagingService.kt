package com.Kunal.stock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TradeAlertMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            FirebaseInstallations.getInstance().id
                .addOnSuccessListener { installationId ->
                    NotificationTokenStore.register(userId, installationId)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to retrieve the Firebase installation ID.", error)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val symbol = message.data["symbol"].orEmpty()
        val title = message.data["title"]?.takeIf { it.isNotBlank() }
            ?: "Trade alert"
        val body = message.data["body"]?.takeIf { it.isNotBlank() }
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Trade alert received, but notification permission is not granted.")
            return
        }

        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("trade_alert_symbol", symbol)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(symbol.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trade active alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New active index and stock option trade setups"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private companion object {
        const val TAG = "TradeAlertNotifications"
        const val CHANNEL_ID = "trade_active_alerts"
    }
}
