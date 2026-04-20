package com.matedroid.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import com.matedroid.R
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker for syncing stats data from TeslamateApi.
 *
 * Runs as a foreground service to prevent being killed when app is in background.
 * - Phase 1: Sync summaries (fast, for Quick Stats)
 * - Phase 2: Sync details (slow, for Deep Stats)
 */
@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val logCollector: SyncLogCollector
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DataSyncWorker"
        const val WORK_NAME = "data_sync_work"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sync_channel"
    }

    private fun log(message: String) = logCollector.log(TAG, message)
    private fun logError(message: String, error: Throwable? = null) = logCollector.logError(TAG, message, error)

    /**
     * Required for expedited work - provides foreground info for older API levels.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Syncing data...")
    }

    // Track if foreground service is available (may fail on Android 14+ from background)
    private var foregroundAvailable = true

    override suspend fun doWork(): Result {
        log("Starting data sync worker (attempt ${runAttemptCount})")

        // Run as foreground service to prevent being killed when screen is off
        // This shows a persistent notification during sync
        // On Android 14+, this may fail if started from background
        foregroundAvailable = trySetForeground("Starting sync...")

        try {
            // Get list of cars
            val carsResult = teslamateRepository.getCars()
            val cars = when (carsResult) {
                is ApiResult.Success -> carsResult.data
                is ApiResult.Error -> {
                    logError("Failed to fetch cars: ${carsResult.message}")
                    return when {
                        // Server not configured yet - this is normal on first run
                        // Return success (nothing to do) - sync will be triggered after settings are saved
                        carsResult.message.contains("not configured", ignoreCase = true) -> {
                            log("Server not configured, skipping sync")
                            Result.success()
                        }
                        isNetworkError(carsResult.message) -> {
                            log("Network error, will retry...")
                            Result.retry()
                        }
                        else -> Result.failure()
                    }
                }
            }

            if (cars.isEmpty()) {
                log("No cars found, nothing to sync")
                return Result.success()
            }

            log("Found ${cars.size} cars to sync")

            // Sync cars sequentially to better handle network errors
            var hasNetworkError = false
            for ((index, car) in cars.withIndex()) {
                try {
                    // Update notification with current car (only if foreground available)
                    trySetForeground("Syncing car ${index + 1}/${cars.size}...")

                    val success = syncRepository.syncCar(car.carId)
                    if (!success) {
                        log("Sync incomplete for car ${car.carId}, will retry")
                        hasNetworkError = true
                    }
                } catch (e: Exception) {
                    logError("Error syncing car ${car.carId}", e)
                    if (isNetworkException(e)) {
                        log("Network error during sync, will retry...")
                        hasNetworkError = true
                    } else {
                        syncManager.markSyncError(car.carId, e.message ?: "Unknown error")
                    }
                }
            }

            return if (hasNetworkError) {
                log("Sync incomplete due to network errors, scheduling retry")
                Result.retry()
            } else {
                log("Sync complete for all cars")
                // Schedule background geocoding for location data
                scheduleGeocoding()
                Result.success()
            }
        } catch (e: Exception) {
            logError("Unexpected error in sync worker", e)
            return if (isNetworkException(e)) {
                log("Network error, will retry...")
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Schedule background geocoding worker as a safety net.
     * Uses KEEP policy to avoid interrupting running geocoding (which may have
     * been started earlier by SyncRepository during drive/charge processing).
     */
    private fun scheduleGeocoding() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GeocodeWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .addTag(GeocodeWorker.TAG)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                GeocodeWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,  // Don't replace if already running
                request
            )

        log("Ensured geocoding worker is scheduled")
    }

    private fun isNetworkError(message: String?): Boolean {
        if (message == null) return false
        val networkKeywords = listOf("dns", "network", "connect", "timeout", "unreachable", "refused", "reset")
        return networkKeywords.any { message.lowercase().contains(it) }
    }

    private fun isNetworkException(e: Throwable): Boolean {
        return e is IOException ||
               e is UnknownHostException ||
               e.cause is IOException ||
               e.cause is UnknownHostException ||
               isNetworkError(e.message)
    }

    /**
     * Try to set foreground service. Returns true if successful, false otherwise.
     * On Android 14+, this may fail if the app is in the background.
     */
    private suspend fun trySetForeground(progress: String): Boolean {
        if (!foregroundAvailable) return false
        return try {
            setForeground(createForegroundInfo(progress))
            true
        } catch (e: Exception) {
            log("Could not set foreground service: ${e.message}")
            foregroundAvailable = false
            false
        }
    }

    /**
     * Create foreground info for the notification.
     */
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("MateDroid Sync")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background sync for stats data"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
