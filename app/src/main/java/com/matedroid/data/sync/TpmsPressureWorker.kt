package com.matedroid.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matedroid.BuildConfig
import com.matedroid.R
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.TirePosition
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.repository.TpmsStateChange
import com.matedroid.data.repository.TpmsStateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker for monitoring tire pressure warnings.
 *
 * Runs every 15 minutes to check TPMS status for all cars and sends notifications
 * when tires enter or exit a warning state.
 */
@HiltWorker
class TpmsPressureWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val tpmsStateRepository: TpmsStateRepository,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "TpmsPressureWorker"
        const val WORK_NAME = "tpms_pressure_work"
        const val CHANNEL_ID = "tire_pressure_channel"
        private const val NOTIFICATION_ID_BASE = 2000

        // Debug: 3 minutes, Release: 15 minutes
        private val INTERVAL_MINUTES = if (BuildConfig.DEBUG) 3L else 15L

        /**
         * Schedule periodic TPMS monitoring work.
         * Uses 3-minute interval in debug builds, 15-minute in release.
         *
         * Note: WorkManager enforces a 15-minute minimum for PeriodicWorkRequest,
         * so in debug mode we use a self-rescheduling OneTimeWorkRequest pattern
         * to achieve shorter intervals.
         */
        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            if (BuildConfig.DEBUG) {
                // Debug: Use OneTimeWorkRequest with delay for shorter intervals
                // Use REPLACE policy so reinstalling updates the interval
                val request = OneTimeWorkRequestBuilder<TpmsPressureWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )

                Log.d(TAG, "Scheduled TPMS monitoring (debug mode, ${INTERVAL_MINUTES}min interval)")
            } else {
                // Release: Use PeriodicWorkRequest (15-minute minimum)
                val request = PeriodicWorkRequestBuilder<TpmsPressureWorker>(
                    INTERVAL_MINUTES, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

                Log.d(TAG, "Scheduled periodic TPMS monitoring (${INTERVAL_MINUTES}min interval)")
            }
        }

        /**
         * Cancel periodic TPMS monitoring work.
         */
        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic TPMS monitoring work")
        }

        /**
         * Run TPMS check immediately (for debugging).
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TpmsPressureWorker>()
                .setConstraints(constraints)
                .addTag("$TAG-immediate")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Triggered immediate TPMS check")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting TPMS pressure check")

        // Check if server is configured
        val settings = settingsDataStore.settings.first()
        if (!settings.isConfigured) {
            Log.d(TAG, "Server not configured, skipping TPMS check")
            return Result.success()
        }

        // Create notification channel
        createNotificationChannel()

        try {
            // Get list of cars
            val carsResult = teslamateRepository.getCars()
            val cars = when (carsResult) {
                is ApiResult.Success -> carsResult.data
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${carsResult.message}")
                    return Result.retry()
                }
            }

            if (cars.isEmpty()) {
                Log.d(TAG, "No cars found")
                return Result.success()
            }

            Log.d(TAG, "Checking TPMS for ${cars.size} cars")

            // Check each car
            for (car in cars) {
                try {
                    checkCarTpms(car.carId, car.displayName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking TPMS for car ${car.carId}", e)
                }
            }

            Log.d(TAG, "TPMS check complete")

            // In debug mode, reschedule the next check (self-rescheduling pattern)
            if (BuildConfig.DEBUG) {
                schedulePeriodicWork(appContext)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in TPMS worker", e)

            // In debug mode, reschedule even on failure
            if (BuildConfig.DEBUG) {
                schedulePeriodicWork(appContext)
            }

            return Result.retry()
        }
    }

    private suspend fun checkCarTpms(carId: Int, carName: String) {
        // Get car status
        val statusResult = teslamateRepository.getCarStatus(carId)
        val status = when (statusResult) {
            is ApiResult.Success -> statusResult.data.status
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch status for car $carId: ${statusResult.message}")
                return
            }
        }

        val tpmsDetails = status.tpmsDetails

        // Detect state change
        val stateChange = tpmsStateRepository.detectStateChange(carId, tpmsDetails)

        // Update stored state
        tpmsStateRepository.updateState(carId, tpmsDetails)

        // Show notification if state changed
        if (stateChange != null) {
            showNotification(carId, carName, stateChange)
        }
    }

    private fun showNotification(carId: Int, carName: String, stateChange: TpmsStateChange) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val (title, body) = when (stateChange) {
            is TpmsStateChange.WarningStarted -> {
                val tireNames = stateChange.tires.map { tire ->
                    getTireFullName(tire)
                }.joinToString(", ")

                Pair(
                    appContext.getString(R.string.tpms_notification_title),
                    appContext.getString(R.string.tpms_notification_body, carName, tireNames)
                )
            }
            is TpmsStateChange.WarningCleared -> {
                Pair(
                    appContext.getString(R.string.tpms_notification_title),
                    appContext.getString(R.string.tpms_notification_cleared, carName)
                )
            }
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Use different notification ID per car
        val notificationId = NOTIFICATION_ID_BASE + carId
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "Showed TPMS notification for car $carId: $body")
    }

    private fun getTireFullName(tire: TirePosition): String {
        return when (tire) {
            TirePosition.FL -> appContext.getString(R.string.tire_fl_full)
            TirePosition.FR -> appContext.getString(R.string.tire_fr_full)
            TirePosition.RL -> appContext.getString(R.string.tire_rl_full)
            TirePosition.RR -> appContext.getString(R.string.tire_rr_full)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.tpms_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.tpms_channel_description)
            }

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
