package com.example.portmonnai.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.portmonnai.MainActivity
import com.example.portmonnai.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "price_alerts"
        const val CHANNEL_NAME = "Alertes de prix"
        const val CHANNEL_DESC = "Notifications en cas de forte variation (>3%)"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPriceAlert(assetName: String, change24h: Double, currentPrice: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val sign = if (change24h > 0) "📈 +" else "📉 "
        val color = if (change24h > 0) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setContentTitle("Mouvement important : $assetName")
            .setContentText("$sign${String.format("%.2f", change24h)}% aujourd'hui. Prix : €${String.format("%.2f", currentPrice)}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(color)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        with(NotificationManagerCompat.from(context)) {
            // Use asset hash code as notification id to allow multiple alerts for different assets
            notify(assetName.hashCode(), builder.build())
        }
    }
}
