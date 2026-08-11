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
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.ChargingNotificationManager
import com.matedroid.service.ChargingMonitorService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker for monitoring charging sessions and sentry events.
 *
 * Runs every 30 seconds to check charging and sentry state for all cars,
 * and shows/updates/cancels notifications accordingly.
 */
@HiltWorker
class ChargingNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val chargingCheckUseCase: ChargingCheckUseCase,
    private val chargingNotificationManager: ChargingNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ChargingNotificationWorker"
        const val WORK_NAME = "charging_notification_work"
        const val PERIODIC_WORK_NAME = "charging_notification_periodic"

        private const val INTERVAL_SECONDS = 30L

        // Idle cadence: nothing is charging, plugged in, or sentry-armed, so the only job is
        // discovering a new charge/sentry session — 5 min keeps that latency acceptable while
        // cutting idle polling 10×. The dashboard's own 5 s poll covers the app-open case.
        private const val IDLE_INTERVAL_SECONDS = 300L

        /**
         * Schedule charging/sentry notification monitoring.
         *
         * Uses two strategies:
         * 1. Self-rescheduling OneTimeWorkRequest for frequent checks (30s active / 5min idle)
         * 2. PeriodicWorkRequest (15min) as reliable fallback when app is killed
         */
        fun schedulePeriodicWork(context: Context, intervalSeconds: Long = INTERVAL_SECONDS) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Strategy 1: OneTimeWorkRequest with delay for frequent checks
            val oneTimeRequest = OneTimeWorkRequestBuilder<ChargingNotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(intervalSeconds, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            // Strategy 2: PeriodicWorkRequest as reliable backup (survives app death)
            // This ensures notification is cancelled within 15 minutes even if app is killed
            val periodicRequest = PeriodicWorkRequestBuilder<ChargingNotificationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("$TAG-periodic")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't reset if already scheduled
                periodicRequest
            )

            Log.d(TAG, "Scheduled notification check (${intervalSeconds}s + 15min backup)")
        }

        /**
         * Cancel all charging notification monitoring.
         */
        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d(TAG, "Cancelled charging notification work")
        }

        /**
         * Run charging check immediately (for app startup or debugging).
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
        Log.d(TAG, "Starting notification check")

        // While the foreground monitor service is alive, its own 30s loop runs the exact
        // same check (including sentry) — polling here too doubled every API call and made
        // two writers race on the same DataStore and notification IDs.
        if (ChargingMonitorService.isRunning) {
            Log.d(TAG, "Monitor service is running, skipping duplicate check")
            scheduleNextCheck(frequent = true)
            return Result.success()
        }

        try {
            val checkResult = chargingCheckUseCase.checkAllCars()

            val checked = when (checkResult) {
                is ChargingCheckUseCase.Result.NotConfigured -> {
                    // Don't re-arm the frequent chain — the 15-min periodic backstop (a
                    // cheap local settings read, no network) resumes polling once the
                    // user configures a server.
                    Log.d(TAG, "Server not configured, skipping check")
                    return Result.success()
                }
                is ChargingCheckUseCase.Result.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${checkResult.message}")
                    scheduleNextCheck(frequent = true)
                    return Result.retry()
                }
                is ChargingCheckUseCase.Result.Checked -> checkResult
            }

            if (checked.cars.isEmpty() && !checked.anyCheckFailed) {
                Log.d(TAG, "No cars found")
                scheduleNextCheck(frequent = false)
                return Result.success()
            }

            // The service start/stop decision must aggregate across ALL cars: stopping
            // per idle car would kill the other car's charging notification every 30 s.
            val needMonitor = checked.cars.filter { it.needsMonitor }

            // Idle cars lose their charging notification (the check itself only classifies).
            for (check in checked.cars) {
                if (!check.needsMonitor) {
                    chargingNotificationManager.cancelNotification(check.car.carId)
                }
            }

            if (needMonitor.isNotEmpty()) {
                try {
                    ChargingMonitorService.start(appContext)
                } catch (e: Exception) {
                    // On Android 12+, can't start foreground service from background.
                    // Fall back to showing notifications directly (won't update in real-time).
                    Log.w(TAG, "Cannot start foreground service, showing notifications directly: ${e.message}")
                    for (check in needMonitor) {
                        if (check.isCharging) {
                            val liveChargeAvailable = teslamateRepository.isCurrentChargeAvailable(check.car.carId)
                            chargingNotificationManager.showChargingNotification(
                                check.car, check.status, liveChargeAvailable,
                                chronometerBaseMs = check.status.stateSinceEpochMs
                            )
                        }
                    }
                }
            } else if (!checked.anyCheckFailed) {
                // Only stop when we positively know no car is charging — a failed check
                // (transient network error) shouldn't tear down an active monitor.
                Log.d(TAG, "No car needs monitoring, stopping monitor service")
                ChargingMonitorService.stop(appContext)
            }

            Log.d(TAG, "Check complete")
            // Back off to the idle cadence when nothing is (about to be) charging and no
            // car is sentry-armed or driving; keep 30s on errors so recovery is quick.
            val frequent = checked.anyCheckFailed || checked.cars.any { it.wantsFrequentPolling }
            scheduleNextCheck(frequent)
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in worker", e)
            scheduleNextCheck(frequent = true)
            return Result.retry()
        }
    }

    /**
     * Schedule the next check using self-rescheduling pattern.
     * [frequent] = 30s (charging/plugged/sentry-armed/errors); otherwise the 5-min idle cadence.
     */
    private fun scheduleNextCheck(frequent: Boolean) {
        schedulePeriodicWork(
            appContext,
            if (frequent) INTERVAL_SECONDS else IDLE_INTERVAL_SECONDS
        )
    }
}
