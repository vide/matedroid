package com.matedroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.model.Currency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@HiltWorker
class TripSummaryWidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TripSummaryWidgetWorker"
        const val WORK_NAME = "trip_summary_widget_update"
        const val PERIODIC_WORK_NAME = "trip_summary_widget_update_periodic"
        private const val PERIODIC_INTERVAL_HOURS = 6L
        private const val RANGE_DAYS = 30L

        fun scheduleImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<TripSummaryWidgetUpdateWorker>()
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Scheduled immediate trip summary widget update")
        }

        fun scheduleWork(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<TripSummaryWidgetUpdateWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .addTag("$TAG-periodic")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "Scheduled trip summary widget update every ${PERIODIC_INTERVAL_HOURS}h")
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d(TAG, "Cancelled trip summary widget update work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting trip summary widget update")

        val manager = GlanceAppWidgetManager(appContext)
        val glanceIds = manager.getGlanceIds(TripSummaryWidget::class.java)

        if (glanceIds.isEmpty()) {
            Log.d(TAG, "No active trip summary widgets, skipping update")
            return Result.success()
        }

        val settings = settingsDataStore.settings.firstOrNull()
        val currency = Currency.findByCode(settings?.currencyCode ?: Currency.DEFAULT.code)
        val units = Units(unitOfLength = settings?.unitOfLength ?: "km")
        val (startDate, endDate) = TripSummaryWidgetDateRange.lastDays(days = RANGE_DAYS)

        for (glanceId in glanceIds) {
            try {
                val prefs = getAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId)
                val carId = prefs[TripSummaryWidget.CAR_ID_KEY] ?: continue
                val carName = prefs[TripSummaryWidget.CAR_NAME_KEY]
                    ?: appContext.getString(R.string.app_name)

                val displayData = buildDisplayData(
                    carId = carId,
                    carName = carName,
                    currencySymbol = currency.symbol,
                    units = units,
                    startDate = startDate,
                    endDate = endDate
                )
                TripSummaryWidget().updateWidget(appContext, glanceId, displayData)
                Log.d(TAG, "Updated trip summary widget for car $carId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating trip summary widget $glanceId", e)
            }
        }

        return Result.success()
    }

    private suspend fun buildDisplayData(
        carId: Int,
        carName: String,
        currencySymbol: String,
        units: Units,
        startDate: String,
        endDate: String,
    ): TripSummaryWidgetDisplayData {
        val metrics = TripSummaryWidgetMetrics(
            driveCount = driveSummaryDao.countInRange(carId, startDate, endDate),
            drivingDays = driveSummaryDao.countDrivingDaysInRange(carId, startDate, endDate),
            totalDistance = driveSummaryDao.sumDistanceInRange(carId, startDate, endDate),
            totalEnergy = driveSummaryDao.sumEnergyConsumedInRange(carId, startDate, endDate),
            avgEfficiency = driveSummaryDao.avgEfficiencyInRange(carId, startDate, endDate),
            totalCost = chargeSummaryDao.sumCostInRange(carId, startDate, endDate),
            chargesWithCost = chargeSummaryDao.countWithCostInRange(carId, startDate, endDate),
        )
        val noValue = appContext.getString(R.string.trip_widget_no_value)
        return TripSummaryWidgetFormatter.format(
            carId = carId,
            carName = carName,
            metrics = metrics,
            currencySymbol = currencySymbol,
            noValue = noValue,
            updatedValue = appContext.getString(
                R.string.trip_widget_updated,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            ),
            units = units,
        )
    }

}
