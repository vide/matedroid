package com.matedroid.domain

import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.model.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only bridge between the cost analytics UI/widget and the Room cache.
 *
 * All heavy lifting stays in [CostAnalyticsCalculator]; this class just picks
 * the right rows out of the DAOs and augments them with the user's currency
 * choice (from [SettingsDataStore]).
 */
@Singleton
class CostAnalyticsRepository @Inject constructor(
    private val chargeSummaryDao: ChargeSummaryDao,
    private val driveSummaryDao: DriveSummaryDao,
    private val settingsDataStore: SettingsDataStore,
) {

    /**
     * Load metrics for the given range, currency symbol, and the raw charge
     * rows in case the caller wants to render per-charge detail later.
     *
     * @param carId Selected car id.
     * @param range Half-open [startDate, endDate) window, or null for "all time".
     *   Dates use the TeslaMate API DateTime format ("yyyy-MM-dd'T'HH:mm:ss").
     */
    suspend fun loadMetrics(
        carId: Int,
        range: DateRange? = null,
    ): CostAnalyticsSnapshot = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.settings.first()
        val currency = Currency.findByCode(settings.currencyCode)

        val (charges, distance) = if (range == null) {
            val all = chargeSummaryDao.getAllForCar(carId)
            val dist = driveSummaryDao.sumDistance(carId)
            all to dist
        } else {
            val ch = chargeSummaryDao.getInRange(carId, range.startDate, range.endDate)
            val dist = driveSummaryDao.sumDistanceInRange(carId, range.startDate, range.endDate)
            ch to dist
        }

        val metrics = CostAnalyticsCalculator.calculate(charges, distance)
        CostAnalyticsSnapshot(
            metrics = metrics,
            charges = charges,
            currencySymbol = currency.symbol,
            currencyCode = currency.code,
            unitOfLength = settings.unitOfLength,
        )
    }

    /** Persist TeslaMate's unit-of-length for offline widget/screen labeling. */
    suspend fun cacheUnitOfLength(unitOfLength: String) {
        if (unitOfLength == "km" || unitOfLength == "mi") {
            settingsDataStore.saveUnitOfLength(unitOfLength)
        }
    }

    data class DateRange(
        val startDate: String,
        val endDate: String,
    ) {
        companion object {
            private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

            /**
             * Half-open range covering the last [days] days (inclusive of today).
             * Matches [com.matedroid.widget.TripSummaryWidgetDateRange] so the
             * screen and widget agree on window semantics.
             */
            fun lastDays(days: Long, today: LocalDate = LocalDate.now()): DateRange {
                require(days > 0) { "days must be positive" }
                val start = today.minusDays(days - 1).atStartOfDay()
                val end = today.plusDays(1).atStartOfDay()
                return DateRange(start.format(FORMATTER), end.format(FORMATTER))
            }
        }
    }
}

data class CostAnalyticsSnapshot(
    val metrics: CostAnalyticsMetrics,
    val charges: List<ChargeSummary>,
    val currencySymbol: String,
    val currencyCode: String,
    val unitOfLength: String,
)
