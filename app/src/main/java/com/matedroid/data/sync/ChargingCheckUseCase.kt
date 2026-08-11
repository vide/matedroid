package com.matedroid.data.sync

import android.content.Context
import android.util.Log
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.SentryEvent
import com.matedroid.data.repository.SentryStateRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.SentryNotificationManager
import com.matedroid.widget.CarWidgetUpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single implementation of the periodic charging/sentry check, shared by
 * [ChargingNotificationWorker] (background 30s/5min chain) and
 * [com.matedroid.service.ChargingMonitorService] (foreground 30s loop while charging).
 *
 * It owns everything both callers must agree on: fetching car statuses, persisting the
 * DC-session flag, processing sentry events (including their notifications), and
 * classifying each car. What it deliberately does NOT do is post charging notifications
 * or start/stop the monitor service — the two callers render those differently
 * (startForeground vs. plain notify) and must aggregate across all cars themselves.
 */
@Singleton
class ChargingCheckUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val teslamateRepository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val chargeSessionStateDataStore: ChargeSessionStateDataStore,
    private val sentryStateRepository: SentryStateRepository,
    private val sentryNotificationManager: SentryNotificationManager
) {
    companion object {
        private const val TAG = "ChargingCheckUseCase"
    }

    sealed interface Result {
        /** No server configured — nothing to poll. */
        data object NotConfigured : Result

        /** The cars list itself couldn't be fetched. */
        data class Error(val message: String) : Result

        data class Checked(
            val cars: List<CarCheck>,
            /** True when at least one car's status fetch failed — those must not count as idle. */
            val anyCheckFailed: Boolean
        ) : Result
    }

    data class CarCheck(
        val car: CarData,
        val status: CarStatus,
        val isCharging: Boolean,
        val dcFinishedPluggedIn: Boolean
    ) {
        /** The shared monitor service must run while this is true for any car. */
        val needsMonitor: Boolean get() = isCharging || dcFinishedPluggedIn

        /**
         * 30s polling is warranted even when idle: plugged-in cars can start charging any
         * moment, sentry-armed cars have a ~1-minute alert window, and a driving car may
         * pull into a DC fast charger where plug-to-charging takes seconds.
         */
        val wantsFrequentPolling: Boolean
            get() = needsMonitor ||
                status.pluggedIn == true ||
                status.sentryMode == true ||
                status.state?.lowercase() == "driving"
    }

    suspend fun checkAllCars(): Result {
        val settings = settingsDataStore.settings.first()
        if (!settings.isConfigured) return Result.NotConfigured

        val cars = when (val carsResult = teslamateRepository.getCars()) {
            is ApiResult.Success -> carsResult.data
            is ApiResult.Error -> return Result.Error(carsResult.message)
        }

        var anyCheckFailed = false
        val checks = mutableListOf<CarCheck>()
        for (car in cars) {
            val check = try {
                checkCar(car)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking car ${car.carId}", e)
                null
            }
            if (check != null) checks.add(check) else anyCheckFailed = true
        }
        return Result.Checked(checks, anyCheckFailed)
    }

    private suspend fun checkCar(car: CarData): CarCheck? {
        val carId = car.carId

        val status = when (val statusResult = teslamateRepository.getCarStatus(carId)) {
            is ApiResult.Success -> statusResult.data.status
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch status for car $carId: ${statusResult.message}")
                return null
            }
        }

        // Persist whether the active session is DC; this is the only moment we can
        // tell (post-completion `charger_phases` is null regardless of charge type).
        if (status.isCharging && status.isDcCharging) {
            chargeSessionStateDataStore.setLastSessionDc(carId, true)
        } else if (status.pluggedIn == false) {
            chargeSessionStateDataStore.clear(carId)
        }

        val dcFinishedPluggedIn = status.isChargeCompletePluggedIn &&
            chargeSessionStateDataStore.wasLastSessionDc(carId)

        processSentry(car, status)

        return CarCheck(
            car = car,
            status = status,
            isCharging = status.isCharging,
            dcFinishedPluggedIn = dcFinishedPluggedIn
        )
    }

    private suspend fun processSentry(car: CarData, status: CarStatus) {
        val carId = car.carId
        val sentryMode = status.sentryMode ?: false

        when (val event = sentryStateRepository.processStatus(
            carId, sentryMode, status.isSentryAlerted, status.latitude, status.longitude, status.geofence
        )) {
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
    }
}
