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
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.SentryEvent
import com.matedroid.data.repository.SentryStateRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.ChargingNotificationManager
import com.matedroid.notification.SentryNotificationManager
import com.matedroid.service.ChargingMonitorService
import com.matedroid.widget.CarWidgetUpdateWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
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
    private val settingsDataStore: SettingsDataStore,
    private val chargingNotificationManager: ChargingNotificationManager,
    private val sentryStateRepository: SentryStateRepository,
    private val sentryNotificationManager: SentryNotificationManager,
    private val chargeSessionStateDataStore: ChargeSessionStateDataStore
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

        // Check if server is configured. Don't re-arm the frequent chain — the 15-min
        // periodic backstop (a cheap local settings read, no network) resumes polling
        // once the user configures a server.
        val settings = settingsDataStore.settings.first()
        if (!settings.isConfigured) {
            Log.d(TAG, "Server not configured, skipping check")
            return Result.success()
        }

        try {
            // Get list of cars
            val carsResult = teslamateRepository.getCars()
            val cars = when (carsResult) {
                is ApiResult.Success -> carsResult.data
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${carsResult.message}")
                    scheduleNextCheck(frequent = true)
                    return Result.retry()
                }
            }

            if (cars.isEmpty()) {
                Log.d(TAG, "No cars found")
                scheduleNextCheck(frequent = false)
                return Result.success()
            }

            Log.d(TAG, "Checking status for ${cars.size} cars")

            // Check each car, collecting which ones need the shared monitor service.
            // The start/stop decision must aggregate across ALL cars: stopping inside the
            // per-car loop made any idle car kill the service (and every charging
            // notification with it) 30 s after another car's iteration started it.
            val needMonitor = mutableListOf<Pair<CarData, CarStatus>>()
            var anyCheckFailed = false
            var anyWantsFrequentPolling = false
            for (car in cars) {
                val outcome = try {
                    checkCarStatus(car)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking car ${car.carId}", e)
                    CheckOutcome.Failed
                }
                when (outcome) {
                    is CheckOutcome.NeedsMonitor -> needMonitor.add(car to outcome.status)
                    CheckOutcome.Failed -> anyCheckFailed = true
                    is CheckOutcome.Idle -> {
                        // Plugged-in cars can start charging any moment; sentry-armed cars
                        // need 30s polling to catch the ~1-minute alert window.
                        if (outcome.status.pluggedIn == true || outcome.status.sentryMode == true) {
                            anyWantsFrequentPolling = true
                        }
                    }
                }
            }

            if (needMonitor.isNotEmpty()) {
                try {
                    ChargingMonitorService.start(appContext)
                } catch (e: Exception) {
                    // On Android 12+, can't start foreground service from background.
                    // Fall back to showing notifications directly (won't update in real-time).
                    Log.w(TAG, "Cannot start foreground service, showing notifications directly: ${e.message}")
                    for ((car, status) in needMonitor) {
                        if (status.isCharging) {
                            val liveChargeAvailable = teslamateRepository.isCurrentChargeAvailable(car.carId)
                            chargingNotificationManager.showChargingNotification(
                                car, status, liveChargeAvailable,
                                chronometerBaseMs = status.stateSinceEpochMs
                            )
                        }
                    }
                }
            } else if (!anyCheckFailed) {
                // Only stop when we positively know no car is charging — a failed check
                // (transient network error) shouldn't tear down an active monitor.
                Log.d(TAG, "No car needs monitoring, stopping monitor service")
                ChargingMonitorService.stop(appContext)
            }

            Log.d(TAG, "Check complete")
            // Back off to the idle cadence when nothing is (about to be) charging and no
            // car is sentry-armed; keep 30s on errors so recovery is quick.
            val frequent = needMonitor.isNotEmpty() || anyCheckFailed || anyWantsFrequentPolling
            scheduleNextCheck(frequent)
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in worker", e)
            scheduleNextCheck(frequent = true)
            return Result.retry()
        }
    }

    /** Result of a per-car check; the caller aggregates these into one service start/stop decision. */
    private sealed interface CheckOutcome {
        /** Car is charging (or DC-finished-but-plugged) — the monitor service must run. */
        data class NeedsMonitor(val status: CarStatus) : CheckOutcome
        /** Car positively does not need monitoring (status carried for cadence decisions). */
        data class Idle(val status: CarStatus) : CheckOutcome
        /** Status unknown (fetch failed) — must not count as idle. */
        data object Failed : CheckOutcome
    }

    /**
     * Per-car check: persists the DC-session flag, handles sentry events, and cancels the
     * charging notification for idle cars. Starting or stopping the shared monitor service
     * is the caller's job — it must aggregate across all cars.
     */
    private suspend fun checkCarStatus(car: CarData): CheckOutcome {
        // The car object is already in hand from doWork()'s getCars() — no refetch.
        val carId = car.carId

        val statusResult = teslamateRepository.getCarStatus(carId)
        val statusData = when (statusResult) {
            is ApiResult.Success -> statusResult.data
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch status for car $carId: ${statusResult.message}")
                return CheckOutcome.Failed
            }
        }

        val status = statusData.status

        // Persist whether the active session is DC; this is the only moment we can
        // tell (post-completion `charger_phases` is null regardless of charge type).
        if (status.isCharging && status.isDcCharging) {
            chargeSessionStateDataStore.setLastSessionDc(carId, true)
        } else if (status.pluggedIn == false) {
            chargeSessionStateDataStore.clear(carId)
        }

        val dcFinishedPluggedIn = status.isChargeCompletePluggedIn &&
            chargeSessionStateDataStore.wasLastSessionDc(carId)

        // --- Charging ---
        val chargingOutcome = if (status.isCharging) {
            Log.d(TAG, "Car $carId is charging at ${status.batteryLevel}%")
            CheckOutcome.NeedsMonitor(status)
        } else if (dcFinishedPluggedIn) {
            // DC charge finished but cable still plugged — keep service alive
            Log.d(TAG, "Car $carId DC charge finished but still plugged in")
            CheckOutcome.NeedsMonitor(status)
        } else {
            Log.d(TAG, "Car $carId is not charging")
            chargingNotificationManager.cancelNotification(carId)
            CheckOutcome.Idle(status)
        }

        // --- Sentry ---
        val sentryMode = status.sentryMode ?: false
        val isSentryAlerted = status.isSentryAlerted

        when (val event = sentryStateRepository.processStatus(carId, sentryMode, isSentryAlerted, status.latitude, status.longitude, status.geofence)) {
            is SentryEvent.AlertDetected -> {
                Log.d(TAG, "Sentry alert #${event.count} for car $carId (notify=${event.shouldNotify})")
                sentryNotificationManager.showSentryAlert(
                    carName = car.displayName,
                    carId = carId,
                    eventCount = event.count,
                    shouldAlert = event.shouldNotify
                )
                CarWidgetUpdateWorker.scheduleImmediateUpdate(appContext)
            }
            is SentryEvent.SessionEnded -> {
                Log.d(TAG, "Sentry session ended for car $carId")
                sentryNotificationManager.cancelNotification(carId)
            }
            null -> { /* no event */ }
        }

        return chargingOutcome
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
