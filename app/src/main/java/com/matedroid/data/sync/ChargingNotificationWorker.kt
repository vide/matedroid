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
import androidx.work.workDataOf
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.ChargingNotificationManager
import com.matedroid.service.ChargingMonitorService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker for monitoring charging sessions and sentry events.
 *
 * A self-rescheduling chain checks charging and sentry state for all cars and shows, updates
 * or cancels notifications accordingly. How soon the next check runs is decided in one place,
 * [cadenceAfter]; a 15-minute PeriodicWorkRequest is the backstop that survives app death.
 */
@HiltWorker
class ChargingNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val chargingCheckUseCase: ChargingCheckUseCase,
    private val chargingNotificationManager: ChargingNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    /** What the chain does next: wait [delaySeconds], and hand on [consecutiveFailures]. */
    data class Cadence(val delaySeconds: Long, val consecutiveFailures: Int)

    companion object {
        const val TAG = "ChargingNotificationWorker"
        const val WORK_NAME = "charging_notification_work"
        const val PERIODIC_WORK_NAME = "charging_notification_periodic"

        /** Input-data key: how many checks in a row have failed, for the error backoff. */
        internal const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"

        /** Active cadence: a car is charging, plugged in, sentry-armed or driving. */
        internal const val INTERVAL_SECONDS = 30L

        // Idle cadence: nothing is charging, plugged in, or sentry-armed, so the only job is
        // discovering a new charge/sentry session — 5 min keeps that latency acceptable while
        // cutting idle polling 10×. The dashboard's own 5 s poll covers the app-open case.
        internal const val IDLE_INTERVAL_SECONDS = 300L

        /**
         * Decide how long to wait before the next check.
         *
         * - [serviceRunning]: the monitor service polls every 30 s itself (sentry included) and
         *   re-arms this chain at 30 s from its onDestroy, so the chain is only a watchdog for
         *   a service that died with its process — it waits the idle interval;
         * - [frequent]: a car is charging, plugged in, sentry-armed or driving — 30 s, even if
         *   another car's check [failed], and the failure count starts over;
         * - [failed]: the cars list or a status fetch failed — 30 s for a blip, then doubling
         *   per consecutive failure up to the idle interval, so an unreachable server (LAN-only,
         *   VPN down) doesn't hold the radio up every 30 s for hours. Recovery still happens
         *   within five minutes;
         * - otherwise the idle interval.
         */
        fun cadenceAfter(
            serviceRunning: Boolean,
            frequent: Boolean,
            failed: Boolean,
            previousFailures: Int
        ): Cadence = when {
            serviceRunning -> Cadence(IDLE_INTERVAL_SECONDS, 0)
            frequent -> Cadence(INTERVAL_SECONDS, 0)
            failed -> {
                val failures = previousFailures + 1
                val backoff = INTERVAL_SECONDS shl minOf(failures - 1, 4)
                Cadence(minOf(backoff, IDLE_INTERVAL_SECONDS), failures)
            }
            else -> Cadence(IDLE_INTERVAL_SECONDS, 0)
        }

        /**
         * Arm the chain [intervalSeconds] from now, replacing whatever is pending, and make
         * sure the 15-minute backstop exists. Used at boot, by the chain re-arming itself, and
         * by the monitor service when it stops.
         */
        fun schedulePeriodicWork(
            context: Context,
            intervalSeconds: Long = INTERVAL_SECONDS,
            consecutiveFailures: Int = 0
        ) {
            enqueueChain(context, intervalSeconds, consecutiveFailures, ExistingWorkPolicy.REPLACE)
            enqueueBackstop(context)
            Log.d(TAG, "Scheduled notification check (${intervalSeconds}s, failures=$consecutiveFailures, + 15min backup)")
        }

        /**
         * Make sure monitoring is scheduled without disturbing a chain that is already pending.
         *
         * For process start: Application.onCreate runs every time WorkManager wakes the process
         * for any job (widget refresh, sync, TPMS, the backstop itself), and re-arming the chain
         * at 30 s each time defeated the idle cadence on devices that kill the process between
         * jobs. A pending 5-minute check is left alone; a chain is only created when none exists.
         */
        fun ensureScheduled(context: Context) {
            enqueueChain(context, INTERVAL_SECONDS, 0, ExistingWorkPolicy.KEEP)
            enqueueBackstop(context)
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
         * Run a charging check immediately: when the user opens the app (to clear a stale
         * notification), saves a server, or from the debug settings.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChargingNotificationWorker>()
                .setConstraints(networkConstraints())
                .addTag("$TAG-immediate")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Triggered immediate charging check")
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun enqueueChain(
            context: Context,
            delaySeconds: Long,
            consecutiveFailures: Int,
            policy: ExistingWorkPolicy
        ) {
            val request = OneTimeWorkRequestBuilder<ChargingNotificationWorker>()
                .setConstraints(networkConstraints())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_CONSECUTIVE_FAILURES to consecutiveFailures))
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }

        // Survives app death: a stale notification is cancelled within 15 minutes even if the
        // chain was lost, and polling resumes once a server gets configured.
        private fun enqueueBackstop(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChargingNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .addTag("$TAG-periodic")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't reset if already scheduled
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting notification check")
        val previousFailures = inputData.getInt(KEY_CONSECUTIVE_FAILURES, 0)

        // While the foreground monitor service is alive, its own 30s loop runs the exact
        // same check (including sentry) — polling here too doubled every API call and made
        // two writers race on the same DataStore and notification IDs.
        if (ChargingMonitorService.isRunning) {
            Log.d(TAG, "Monitor service is running, skipping duplicate check")
            scheduleNext(cadenceAfter(serviceRunning = true, frequent = false, failed = false, previousFailures = 0))
            return Result.success()
        }

        try {
            val checkResult = chargingCheckUseCase.checkAllCars()

            val checked = when (checkResult) {
                is ChargingCheckUseCase.Result.NotConfigured -> {
                    // Don't re-arm the chain — the 15-min periodic backstop (a cheap local
                    // settings read, no network) resumes polling once the user configures a
                    // server, and saving the connection settings runs a check right away.
                    Log.d(TAG, "Server not configured, skipping check")
                    return Result.success()
                }
                is ChargingCheckUseCase.Result.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${checkResult.message}")
                    scheduleNext(cadenceAfter(serviceRunning = false, frequent = false, failed = true, previousFailures))
                    return Result.success()
                }
                is ChargingCheckUseCase.Result.Checked -> checkResult
            }

            if (checked.cars.isEmpty() && !checked.anyCheckFailed) {
                Log.d(TAG, "No cars found")
                scheduleNext(cadenceAfter(serviceRunning = false, frequent = false, failed = false, previousFailures = 0))
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
            scheduleNext(
                cadenceAfter(
                    serviceRunning = false,
                    frequent = checked.cars.any { it.wantsFrequentPolling },
                    failed = checked.anyCheckFailed,
                    previousFailures = previousFailures
                )
            )
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in worker", e)
            scheduleNext(cadenceAfter(serviceRunning = false, frequent = false, failed = true, previousFailures))
            return Result.success()
        }
    }

    /**
     * Arm the next check. The chain is its own retry mechanism, which is why every path in
     * [doWork] returns Result.success(): a Result.retry() would make WorkManager retry the
     * backstop and runNow() instances too, with its own backoff, on top of the chain.
     */
    private fun scheduleNext(cadence: Cadence) {
        schedulePeriodicWork(appContext, cadence.delaySeconds, cadence.consecutiveFailures)
    }
}
