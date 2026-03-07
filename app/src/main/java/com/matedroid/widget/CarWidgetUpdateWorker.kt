package com.matedroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.SentryStateRepository
import com.matedroid.data.repository.TeslamateRepository
import kotlinx.coroutines.flow.firstOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker that fetches car status and updates all active home screen widgets.
 *
 * Uses two strategies (same pattern as ChargingNotificationWorker):
 * 1. Self-rescheduling OneTimeWorkRequest: 1 min (debug) / 3 min (release)
 * 2. PeriodicWorkRequest (15 min) as a reliable fallback when the app is killed
 */
@HiltWorker
class CarWidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val teslamateRepository: TeslamateRepository,
    private val sentryStateRepository: SentryStateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val geocodingRepository: GeocodingRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CarWidgetUpdateWorker"
        const val WORK_NAME = "car_widget_update"
        const val PERIODIC_WORK_NAME = "car_widget_update_periodic"

        // Debug: 1 minute, Release: 3 minutes
        private val INTERVAL_MINUTES = if (BuildConfig.DEBUG) 1L else 3L

        /**
         * Enqueues an immediate (no-delay) update — used when a widget is first configured
         * so it shows real data right away instead of waiting for the next scheduled poll.
         */
        fun scheduleImmediateUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateRequest = OneTimeWorkRequestBuilder<CarWidgetUpdateWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
            Log.d(TAG, "Scheduled immediate widget update")
        }

        fun scheduleWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Strategy 1: self-rescheduling OneTimeWorkRequest for frequent updates
            val oneTimeRequest = OneTimeWorkRequestBuilder<CarWidgetUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            // Strategy 2: PeriodicWorkRequest as reliable backup (survives app death)
            val periodicRequest = PeriodicWorkRequestBuilder<CarWidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("$TAG-periodic")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            Log.d(TAG, "Scheduled widget update (${INTERVAL_MINUTES}min + 15min backup)")
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d(TAG, "Cancelled widget update work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting widget update")

        val manager = GlanceAppWidgetManager(appContext)
        val glanceIds = manager.getGlanceIds(CarWidget::class.java)

        if (glanceIds.isEmpty()) {
            Log.d(TAG, "No active widgets, skipping update")
            scheduleNextUpdate()
            return Result.success()
        }

        // Read image overrides once (same source as the dashboard)
        val imageOverrides = settingsDataStore.carImageOverrides.firstOrNull() ?: emptyMap()

        // Fetch all cars once to avoid redundant API calls
        val carsResult = teslamateRepository.getCars()
        val cars = when (carsResult) {
            is ApiResult.Success -> carsResult.data
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch cars: ${carsResult.message}")
                scheduleNextUpdate()
                return Result.retry()
            }
        }

        for (glanceId in glanceIds) {
            try {
                val prefs = getAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId)
                val carId = prefs[CarWidget.CAR_ID_KEY] ?: continue

                val car = cars.find { it.carId == carId } ?: continue
                val statusResult = teslamateRepository.getCarStatus(carId)
                val status = when (statusResult) {
                    is ApiResult.Success -> statusResult.data.status
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to fetch status for car $carId: ${statusResult.message}")
                        continue
                    }
                }

                val sentryEventCount = sentryStateRepository.getEventCount(carId)
                val locationText = resolveLocationText(status)
                val displayData = CarWidgetDisplayData.from(car, status).copy(
                    sentryEventCount = sentryEventCount,
                    imageOverride = imageOverrides[carId],
                    locationText = locationText
                )
                CarWidget().updateWidget(appContext, glanceId, displayData)
                Log.d(TAG, "Updated widget for car $carId (${car.displayName})")

            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $glanceId", e)
            }
        }

        scheduleNextUpdate()
        return Result.success()
    }

    /**
     * Resolves the display location text following the same priority as the dashboard:
     * 1. Geofence name (from TeslaMate)
     * 2. Reverse-geocoded address (via Nominatim)
     * 3. Raw coordinates formatted as "lat, lon"
     */
    private suspend fun resolveLocationText(status: CarStatus): String? {
        val geofence = status.geofence?.takeIf { it.isNotBlank() }
        if (geofence != null) return geofence

        val lat = status.latitude ?: return null
        val lon = status.longitude ?: return null

        val address = try {
            geocodingRepository.reverseGeocode(lat, lon)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed for widget", e)
            null
        }
        if (address != null) return address

        return "%.5f, %.5f".format(lat, lon)
    }

    private fun scheduleNextUpdate() {
        scheduleWork(appContext)
    }
}
