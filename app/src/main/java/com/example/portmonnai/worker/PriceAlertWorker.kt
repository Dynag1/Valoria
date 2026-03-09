package com.example.portmonnai.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.portmonnai.data.repository.PortfolioRepository
import com.example.portmonnai.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PortfolioRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val PREFS_NAME = "price_alerts_prefs"
        private const val ALERT_THRESHOLD = 3.0
        private const val NOTIFIED_PREFIX = "notified_"
        private const val ALERTS_ENABLED_KEY = "alerts_enabled"
        private const val NOTIFICATION_MIN_INTERVAL_MS = 18 * 60 * 60 * 1000L // 18 hours to avoid repeat notification on same day
    }

    override suspend fun doWork(): Result {
        Log.d("PriceAlertWorker", "Checking prices for alerts...")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alertsEnabled = prefs.getBoolean(ALERTS_ENABLED_KEY, true)
        
        if (!alertsEnabled) {
            Log.d("PriceAlertWorker", "Alerts are disabled in settings.")
            return Result.success()
        }

        try {
            // First refresh prices
            val priceData = repository.fetchCurrentPrices()
            // Then get assets to see which one we have
            val portfolioAssets = repository.getPortfolioAssetsOnce()
            
            val notificationHelper = NotificationHelper(context)
            val now = System.currentTimeMillis()

            portfolioAssets.forEach { asset ->
                val id = asset.asset.id
                val change24h = asset.profitTodayPercentage
                val absChange = Math.abs(change24h)
                
                if (absChange >= ALERT_THRESHOLD) {
                    val lastNotified = prefs.getLong(NOTIFIED_PREFIX + id, 0L)
                    
                    if (now - lastNotified > NOTIFICATION_MIN_INTERVAL_MS) {
                        Log.d("PriceAlertWorker", "Sending alert for ${asset.asset.name}: $change24h%")
                        notificationHelper.sendPriceAlert(
                            asset.asset.name,
                            change24h,
                            asset.asset.currentPrice ?: 0.0
                        )
                        // Mark as notified today
                        prefs.edit().putLong(NOTIFIED_PREFIX + id, now).apply()
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("PriceAlertWorker", "Error checking prices", e)
            return Result.retry()
        }
    }
}
