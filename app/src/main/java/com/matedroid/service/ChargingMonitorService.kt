package com.matedroid.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.matedroid.R
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.notification.ChargingNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for monitoring charging sessions.
 *
 * Runs while the car is charging to provide real-time notification updates.
 * Uses a foreground notification (the charging notification itself) to stay alive.
 */
@AndroidEntryPoint
class ChargingMonitorService : Service() {

    companion object {
        private const val TAG = "ChargingMonitorService"
        private const val UPDATE_INTERVAL_MS = 30_000L  // 30 seconds
        private const val INITIAL_NOTIFICATION_ID = 3999  // Temporary ID for initial foreground

        fun start(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java)
            context.stopService(intent)
        }
    }

    @Inject lateinit var teslamateRepository: TeslamateRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var chargingNotificationManager: ChargingNotificationManager
    @Inject lateinit var chargeSessionStateDataStore: ChargeSessionStateDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3
    private var isMonitoring = false
    private val activeNotificationCarIds = mutableSetOf<Int>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isMonitoring) {
            Log.d(TAG, "Service already monitoring, ignoring start command")
            return START_STICKY
        }

        Log.d(TAG, "Service started")

        // Must call startForeground immediately (within 5 seconds)
        startForegroundImmediately()

        // Then start the monitoring loop
        startMonitoring()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed, cancelling ${activeNotificationCarIds.size} notifications")
        isMonitoring = false
        monitorJob?.cancel()

        // Explicitly cancel all charging notifications so they don't linger
        // when the service stops (e.g., after consecutive API failures due to VPN loss)
        for (carId in activeNotificationCarIds) {
            chargingNotificationManager.cancelNotification(carId)
        }
        activeNotificationCarIds.clear()

        // Also cancel the placeholder notification in case it was never replaced
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INITIAL_NOTIFICATION_ID)

        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Start foreground immediately with a placeholder notification.
     * This must be called within 5 seconds of startForegroundService().
     */
    private fun startForegroundImmediately() {
        // Ensure channel exists
        chargingNotificationManager.ensureChannelExists()

        val notification = NotificationCompat.Builder(this, ChargingNotificationManager.CHANNEL_ID)
            .setContentTitle(getString(R.string.charging_notification_title, ""))
            .setContentText(getString(R.string.charging_notification_loading))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    INITIAL_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(INITIAL_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Started foreground with placeholder notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            stopSelf()
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        isMonitoring = true
        monitorJob = serviceScope.launch {
            // Small delay to let things settle after service start
            delay(500)

            // Initial check
            val initialResult = performChargingCheck()
            if (!initialResult) {
                consecutiveFailures++
                Log.d(TAG, "Initial check failed, failure count: $consecutiveFailures")
            } else {
                consecutiveFailures = 0
            }

            // Continue monitoring
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)

                val stillCharging = performChargingCheck()
                if (stillCharging) {
                    consecutiveFailures = 0
                    Log.d(TAG, "Updated charging notification")
                } else {
                    consecutiveFailures++
                    Log.d(TAG, "Check returned no charging, failure count: $consecutiveFailures")

                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        Log.d(TAG, "Too many failures, stopping service")
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    /**
     * Update the foreground notification, falling back to direct notification on failure.
     */
    private fun updateForegroundNotification(
        notificationId: Int,
        notification: Notification,
        car: com.matedroid.data.api.models.CarData,
        status: com.matedroid.data.api.models.CarStatus,
        liveChargeAvailable: Boolean
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
            chargingNotificationManager.showChargingNotification(
                car, status, liveChargeAvailable,
                chronometerBaseMs = status.stateSinceEpochMs
            )
        }
    }

    /**
     * Check charging status and update notification.
     * Returns true if any car is charging, false otherwise.
     */
    private suspend fun performChargingCheck(): Boolean {
        try {
            val settings = settingsDataStore.settings.first()
            if (!settings.isConfigured) {
                Log.d(TAG, "Server not configured")
                return false
            }

            val carsResult = teslamateRepository.getCars()
            val cars = when (carsResult) {
                is ApiResult.Success -> carsResult.data
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch cars: ${carsResult.message}")
                    return false
                }
            }

            var anyCharging = false

            for (car in cars) {
                val statusResult = teslamateRepository.getCarStatus(car.carId)
                val statusData = when (statusResult) {
                    is ApiResult.Success -> statusResult.data
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to fetch status for car ${car.carId}: ${statusResult.message}")
                        continue
                    }
                }

                val status = statusData.status

                val notificationId = ChargingNotificationManager.NOTIFICATION_ID_BASE + car.carId

                // Only `isCharging && phases == 0` confirms DC. After completion phases
                // is null for any charge type, so we persist the in-session flag.
                if (status.isCharging && status.isDcCharging) {
                    chargeSessionStateDataStore.setLastSessionDc(car.carId, true)
                } else if (status.pluggedIn == false) {
                    chargeSessionStateDataStore.clear(car.carId)
                }

                val wasDcSession = chargeSessionStateDataStore.wasLastSessionDc(car.carId)
                val dcFinishedPluggedIn = status.isChargeCompletePluggedIn && wasDcSession

                when {
                    status.isCharging -> {
                        anyCharging = true
                        activeNotificationCarIds.add(car.carId)
                        Log.d(TAG, "Car ${car.carId} charging at ${status.batteryLevel}%")

                        val liveChargeAvailable = teslamateRepository.isCurrentChargeAvailable(car.carId)
                        val notification = chargingNotificationManager.buildNotification(
                            car, status, liveChargeAvailable,
                            chronometerBaseMs = status.stateSinceEpochMs
                        )
                        updateForegroundNotification(notificationId, notification, car, status, liveChargeAvailable)
                    }

                    dcFinishedPluggedIn -> {
                        // DC charge finished but cable still plugged — keep notification alive
                        anyCharging = true
                        activeNotificationCarIds.add(car.carId)
                        Log.d(TAG, "Car ${car.carId} DC charge finished but still plugged in")

                        val notification = chargingNotificationManager.buildNotification(
                            car, status,
                            dcFinishedPluggedIn = true,
                            chronometerBaseMs = status.stateSinceEpochMs
                        )
                        updateForegroundNotification(notificationId, notification, car, status, liveChargeAvailable = false)
                    }

                    else -> {
                        activeNotificationCarIds.remove(car.carId)
                        chargingNotificationManager.cancelNotification(car.carId)
                    }
                }
            }

            return anyCharging
        } catch (e: Exception) {
            Log.e(TAG, "Error during charging check", e)
            return false
        }
    }
}
