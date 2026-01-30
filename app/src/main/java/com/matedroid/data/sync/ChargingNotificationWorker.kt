package com.matedroid.data.sync

import android.content.Context
import android.util.Log
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
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.ChargingNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker for monitoring charging sessions.
 *
 * Runs every 1 minute (debug: 30 seconds) to check charging state for all cars
 * and shows/updates/cancels notifications based on charging status.
 */
@HiltWorker
class ChargingNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val chargingNotificationManager: ChargingNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ChargingNotificationWorker"
        const val WORK_NAME = "charging_notification_work"

        // Debug: 30 seconds, Release: 1 minute
        // Note: WorkManager enforces 15-minute minimum for PeriodicWorkRequest
        private val INTERVAL_SECONDS = if (BuildConfig.DEBUG) 30L else 60L

        /**
         * Schedule periodic charging notification monitoring.
         *
         * Uses self-rescheduling OneTimeWorkRequest pattern to achieve
         * shorter intervals than WorkManager's 15-minute minimum.
         */
        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Always use OneTimeWorkRequest with delay for shorter intervals
            val request = OneTimeWorkRequestBuilder<ChargingNotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Log.d(TAG, "Scheduled charging notification check (${INTERVAL_SECONDS}s interval)")
        }

        /**
         * Cancel periodic charging notification monitoring.
         */
        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled charging notification work")
        }

        /**
         * Run charging check immediately (for debugging).
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ChargingNotificationWorker>()
                .setConstraints(constraints)
                .addTag("$TAG-immediate")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Triggered immediate charging check")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting charging notification check")

        // Check if server is configured
        val settings = settingsDataStore.settings.first()
        if (!settings.isConfigured) {
            Log.d(TAG, "Server not configured, skipping charging check")
            scheduleNextCheck()
            return Result.success()
        }

        try {
            // Get list of cars
            val carsResult = teslamateRepository.getCars()
            val cars = when (carsResult) {
                is ApiResult.Success -> carsResult.data
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${carsResult.message}")
                    scheduleNextCheck()
                    return Result.retry()
                }
            }

            if (cars.isEmpty()) {
                Log.d(TAG, "No cars found")
                scheduleNextCheck()
                return Result.success()
            }

            Log.d(TAG, "Checking charging status for ${cars.size} cars")

            // Check each car
            for (car in cars) {
                try {
                    checkCarCharging(car.carId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking charging for car ${car.carId}", e)
                }
            }

            Log.d(TAG, "Charging check complete")
            scheduleNextCheck()
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in charging worker", e)
            scheduleNextCheck()
            return Result.retry()
        }
    }

    private suspend fun checkCarCharging(carId: Int) {
        // Get car info and status
        val carsResult = teslamateRepository.getCars()
        val car = when (carsResult) {
            is ApiResult.Success -> carsResult.data.find { it.carId == carId }
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch car info: ${carsResult.message}")
                return
            }
        }

        if (car == null) {
            Log.e(TAG, "Car $carId not found")
            return
        }

        val statusResult = teslamateRepository.getCarStatus(carId)
        val statusData = when (statusResult) {
            is ApiResult.Success -> statusResult.data
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch status for car $carId: ${statusResult.message}")
                return
            }
        }

        val status = statusData.status ?: return

        if (status.isCharging) {
            // Car is charging - show/update notification
            Log.d(TAG, "Car $carId is charging at ${status.batteryLevel}%")
            chargingNotificationManager.showChargingNotification(car, status)
        } else {
            // Car is not charging - cancel notification
            Log.d(TAG, "Car $carId is not charging")
            chargingNotificationManager.cancelNotification(carId)
        }
    }

    /**
     * Schedule the next check using self-rescheduling pattern.
     */
    private fun scheduleNextCheck() {
        schedulePeriodicWork(appContext)
    }
}
