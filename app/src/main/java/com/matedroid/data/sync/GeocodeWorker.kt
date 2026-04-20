package com.matedroid.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import android.util.Log
import com.matedroid.R
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.entity.GeocodeCache
import com.matedroid.data.repository.GeocodingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

/**
 * Background worker for geocoding locations at Nominatim's rate limit (1 req/sec).
 *
 * Processes the geocode queue and updates location data for drives and charges.
 * Runs with low priority to avoid impacting app performance.
 */
@HiltWorker
class GeocodeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val geocodingRepository: GeocodingRepository,
    private val aggregateDao: AggregateDao,
    private val logCollector: SyncLogCollector
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "GeocodeWorker"
        const val WORK_NAME = "geocode_worker"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "geocode_channel"
        const val RATE_LIMIT_MS = 1100L  // Nominatim: max 1 req/sec, add buffer
        const val MAX_PER_RUN = 100      // Limit per worker run
    }

    private fun log(message: String) = logCollector.log(TAG, message)
    private fun logError(message: String, error: Throwable? = null) = logCollector.logError(TAG, message, error)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Identifying locations...")
    }

    private var foregroundAvailable = true

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== Starting geocode worker (attempt ${runAttemptCount}) ===")
        log("Starting geocode worker (attempt ${runAttemptCount})")

        // Log queue and cache state for diagnostics
        val totalQueue = geocodingRepository.getTotalQueueCount()
        val pendingQueue = geocodingRepository.getPendingCount()
        val failedQueue = geocodingRepository.getFailedCount()
        val cachedCount = geocodingRepository.getCachedCount()
        Log.d(TAG, "Queue state: total=$totalQueue, pending=$pendingQueue, failed=$failedQueue, cached=$cachedCount")
        log("Queue state: total=$totalQueue, pending=$pendingQueue, failed=$failedQueue, cached=$cachedCount")

        // If there are failed items but no pending items, reset and retry them
        if (pendingQueue == 0 && failedQueue > 0) {
            Log.d(TAG, "Resetting $failedQueue failed items to retry")
            log("Resetting $failedQueue failed items to retry")
            geocodingRepository.resetFailedItems()
        }

        // If queue is completely empty but we have cached items, progress should match cache
        // This handles the case where queue was cleared but progress wasn't updated
        if (totalQueue == 0 && cachedCount > 0) {
            geocodingRepository.syncProgressWithCache(cachedCount)
            Log.d(TAG, "Synced progress with cache count: $cachedCount")
            log("Synced progress with cache count: $cachedCount")
        }

        // Run as foreground service (optional - may fail from background)
        foregroundAvailable = trySetForeground("Identifying locations...")

        var processedCount = 0
        var consecutiveErrors = 0

        while (processedCount < MAX_PER_RUN && consecutiveErrors < 5) {
            val batch = geocodingRepository.getNextBatch(1)
            if (batch.isEmpty()) {
                Log.d(TAG, "Queue empty, geocoding complete")
                log("Queue empty, geocoding complete")
                break
            }

            val item = batch.first()
            Log.d(TAG, "Processing grid (${item.gridLat}, ${item.gridLon})")
            val result = geocodingRepository.geocodeAndCache(item)

            if (result != null) {
                Log.d(TAG, "Geocoded: ${result.city}, ${result.countryName}")
                // Update aggregates that match this grid cell
                updateAggregatesWithLocation(item.carId, result)

                // Update progress tracking
                geocodingRepository.markGeocoded(item.carId)

                processedCount++
                consecutiveErrors = 0

                // Update notification periodically
                if (processedCount % 10 == 0) {
                    val remaining = geocodingRepository.getPendingCount()
                    Log.d(TAG, "Progress: $processedCount processed, $remaining remaining")
                    trySetForeground("Identifying locations... ($remaining remaining)")
                }
            } else {
                consecutiveErrors++
                Log.w(TAG, "Geocoding failed for grid (${item.gridLat}, ${item.gridLon}), error count: $consecutiveErrors")
                log("Geocoding failed for grid (${item.gridLat}, ${item.gridLon}), error count: $consecutiveErrors")
            }

            // Rate limit
            delay(RATE_LIMIT_MS)
        }

        Log.d(TAG, "Processed $processedCount locations this run")
        log("Processed $processedCount locations this run")

        // Check if there's more work to do
        val remaining = geocodingRepository.getPendingCount()
        return if (remaining > 0) {
            Log.d(TAG, "$remaining locations remaining, re-enqueuing")
            log("$remaining locations remaining, re-enqueuing")
            // Re-enqueue ourselves immediately instead of using retry() to avoid backoff delays
            scheduleNext()
            Result.success()
        } else {
            Log.d(TAG, "All locations geocoded")
            log("All locations geocoded")
            Result.success()
        }
    }

    /**
     * Schedule the next batch of geocoding work immediately.
     * Uses REPLACE policy to ensure fresh start without backoff delays.
     */
    private fun scheduleNext() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<GeocodeWorker>()
            .setConstraints(constraints)
            .addTag(TAG)
            .build()

        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
    }

    /**
     * Update drive and charge aggregates that match the geocoded grid cell.
     */
    private suspend fun updateAggregatesWithLocation(carId: Int, cache: GeocodeCache) {
        // Update drive aggregates in this grid cell
        aggregateDao.updateDriveLocationsInGrid(
            carId = carId,
            gridLat = cache.gridLat,
            gridLon = cache.gridLon,
            countryCode = cache.countryCode,
            countryName = cache.countryName,
            regionName = cache.regionName,
            city = cache.city
        )

        // Update charge aggregates in this grid cell
        aggregateDao.updateChargeLocationsInGrid(
            carId = carId,
            gridLat = cache.gridLat,
            gridLon = cache.gridLon,
            countryCode = cache.countryCode,
            countryName = cache.countryName,
            regionName = cache.regionName,
            city = cache.city
        )
    }

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

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("MateDroid")
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
                "Location Identification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background geocoding for location stats"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
