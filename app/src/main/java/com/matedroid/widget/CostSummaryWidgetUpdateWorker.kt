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
import com.matedroid.domain.CostAnalyticsCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Background worker that keeps the [CostSummaryWidget] snapshots up to date.
 *
 * The worker owns all DB access and formatting. Widget composables only read
 * the preformatted values written into Glance preferences.
 */
@HiltWorker
class CostSummaryWidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CostSummaryWidgetWorker"
        const val WORK_NAME = "cost_summary_widget_update"
        const val PERIODIC_WORK_NAME = "cost_summary_widget_update_periodic"
        private const val PERIODIC_INTERVAL_HOURS = 6L

        fun scheduleImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<CostSummaryWidgetUpdateWorker>()
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Log.d(TAG, "Scheduled immediate cost summary widget update")
        }

        fun scheduleWork(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<CostSummaryWidgetUpdateWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .addTag("$TAG-periodic")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest,
            )
            Log.d(TAG, "Scheduled cost summary widget update every ${PERIODIC_INTERVAL_HOURS}h")
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d(TAG, "Cancelled cost summary widget update work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting cost summary widget update")

        val manager = GlanceAppWidgetManager(appContext)
        val glanceIds = manager.getGlanceIds(CostSummaryWidget::class.java)

        if (glanceIds.isEmpty()) {
            Log.d(TAG, "No active cost summary widgets, skipping update")
            return Result.success()
        }

        val settings = settingsDataStore.settings.firstOrNull()
        val currency = Currency.findByCode(settings?.currencyCode ?: Currency.DEFAULT.code)
        // Use only the cached unit that was stored with the last summary sync so Room
        // values and labels stay aligned (never live-fetch settings here).
        val units = Units(
            unitOfLength = settings?.unitOfLength?.takeIf { it == "km" || it == "mi" } ?: "km"
        )

        for (glanceId in glanceIds) {
            try {
                val prefs = getAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId)
                val carId = prefs[CostSummaryWidget.CAR_ID_KEY] ?: continue
                val carName = prefs[CostSummaryWidget.CAR_NAME_KEY]
                    ?: appContext.getString(R.string.app_name)
                val range = CostSummaryWidgetRange.fromDays(
                    prefs[CostSummaryWidget.RANGE_DAYS_KEY] ?: CostSummaryWidgetRange.DEFAULT.days
                )
                val rangeValues = CostSummaryWidgetDateRange.lastDays(days = range.days.toLong())

                val charges = chargeSummaryDao.getInRange(
                    carId,
                    rangeValues.startDate,
                    rangeValues.endDate,
                )
                val distance = driveSummaryDao.sumDistanceInRange(
                    carId,
                    rangeValues.startDate,
                    rangeValues.endDate,
                )
                val metrics = CostAnalyticsCalculator.calculate(charges, distance)

                val displayData = CostSummaryWidgetFormatter.format(
                    carId = carId,
                    carName = carName,
                    periodLabel = appContext.getString(range.labelRes),
                    metrics = metrics,
                    currencySymbol = currency.symbol,
                    noValue = appContext.getString(R.string.cost_widget_no_value),
                    updatedValue = appContext.getString(
                        R.string.cost_widget_updated,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                    ),
                    units = units,
                )
                CostSummaryWidget().updateWidget(appContext, glanceId, displayData)
                Log.d(TAG, "Updated cost summary widget for car $carId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating cost summary widget $glanceId", e)
            }
        }

        return Result.success()
    }
}
