package com.matedroid.ui.screens.mileage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.model.Currency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.matedroid.util.parseIsoDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class YearlyMileage(
    val year: Int,
    val totalDistance: Double,
    val driveCount: Int,
    val totalEnergy: Double,
    val totalBatteryUsage: Double,
    val totalEnergyCost: Double? = null,
    val drives: List<DriveData>
)

data class MonthlyMileage(
    val yearMonth: YearMonth,
    val totalDistance: Double,
    val driveCount: Int,
    val totalEnergy: Double,
    val totalBatteryUsage: Double,
    val totalEnergyCost: Double? = null,
    val drives: List<DriveData>
)

data class DailyMileage(
    val date: LocalDate,
    val totalDistance: Double,
    val driveCount: Int,
    val totalEnergy: Double,
    val totalBatteryUsage: Double,
    val totalEnergyCost: Double? = null,
    val drives: List<DriveData>
)

data class MileageUiState(
    // Settings
    val currencySymbol: String = "€",
    val units: Units? = null,

    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val allDrives: List<DriveData> = emptyList(),
    val allCharges: List<ChargeData> = emptyList(),

    // Lifetime totals (year overview)
    val yearlyData: List<YearlyMileage> = emptyList(),
    val totalLifetimeDistance: Double = 0.0,
    val avgYearlyDistance: Double = 0.0,
    val firstDriveDate: LocalDate? = null,
    val totalLifetimeDriveCount: Int = 0,
    val totalLifetimeEnergy: Double = 0.0,
    val avgLifetimeEnergyDistance: Double = 0.0,
    val totalLifetimeEnergyCost: Double? = null,

    // Year detail view state
    val selectedYear: Int? = null,
    val monthlyData: List<MonthlyMileage> = emptyList(),
    val yearTotalDistance: Double = 0.0,
    val avgMonthlyDistance: Double = 0.0,
    val yearDriveCount: Int = 0,
    val yearTotalEnergy: Double = 0.0,
    val avgYearEnergyDistance: Double = 0.0,
    val yearTotalEnergyCost: Double? = null,

    // Month detail view state
    val selectedMonth: YearMonth? = null,
    val dailyData: List<DailyMileage> = emptyList(),

    // Day detail view state
    val selectedDay: LocalDate? = null,
    val selectedDayData: DailyMileage? = null
)

/**
 * Drive (or charge) paired with its parsed [LocalDateTime]. Held in private
 * VM-scoped lists so repeated year / month / day aggregations don't have to
 * re-parse the same `startDate` string. With ~500 drives + ~200 charges that's
 * upwards of 2,000 string-parse calls saved per re-aggregation.
 */
private data class TimedDrive(val drive: DriveData, val dateTime: LocalDateTime)
private data class TimedCharge(val charge: ChargeData, val dateTime: LocalDateTime)

/**
 * Bundle holding everything `aggregateByYear` writes back to UiState. Computed
 * off the main thread inside a `withContext(Dispatchers.Default)` block so the
 * UI thread is free during the (non-trivial) lifetime aggregation.
 */
private data class YearAggregation(
    val yearlyData: List<YearlyMileage>,
    val totalLifetimeDistance: Double,
    val totalLifetimeDriveCount: Int,
    val totalLifetimeEnergy: Double,
    val avgLifetimeEnergyDistance: Double,
    val totalLifetimeEnergyCost: Double?,
    val firstDriveDate: LocalDate?,
    val avgYearlyDistance: Double,
)

private data class MonthAggregation(
    val monthlyData: List<MonthlyMileage>,
    val yearTotalDistance: Double,
    val avgMonthlyDistance: Double,
    val yearDriveCount: Int,
    val yearTotalEnergy: Double,
    val avgYearEnergyDistance: Double,
    val yearTotalEnergyCost: Double?,
)

@HiltViewModel
class MileageViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MileageUiState())
    val uiState: StateFlow<MileageUiState> = _uiState.asStateFlow()

    private var carId: Int? = null

    // Pre-parsed drive / charge lists. Populated once per loadAllDrives,
    // reused across all subsequent year / month / day aggregations.
    private var timedDrives: List<TimedDrive> = emptyList()
    private var timedCharges: List<TimedCharge> = emptyList()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update { it.copy(currencySymbol = currency.symbol) }
        }
    }

    fun setCarId(id: Int) {
        if (carId != id) {
            carId = id
            // `loadUnits` is the single getCarStatus call. The previous version
            // also fired one inline here, doubling the network round-trip on
            // every screen entry.
            loadUnits(id)
            loadAllDrives()
        }
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> { /* default to metric */ }
            }
        }
    }

    fun refresh() {
        carId?.let {
            _uiState.update { it.copy(isRefreshing = true) }
            loadAllDrives()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun selectYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        viewModelScope.launch { aggregateByMonth(year) }
    }

    fun clearSelectedYear() {
        _uiState.update {
            it.copy(
                selectedYear = null,
                monthlyData = emptyList(),
                yearTotalDistance = 0.0,
                avgMonthlyDistance = 0.0,
                yearDriveCount = 0
            )
        }
    }

    fun selectMonth(yearMonth: YearMonth) {
        _uiState.update { it.copy(selectedMonth = yearMonth) }
        viewModelScope.launch { aggregateByDay(yearMonth) }
    }

    fun clearSelectedMonth() {
        _uiState.update { it.copy(selectedMonth = null, dailyData = emptyList()) }
    }

    fun selectDay(date: LocalDate) {
        val dayData = _uiState.value.dailyData.find { it.date == date }
        _uiState.update { it.copy(selectedDay = date, selectedDayData = dayData) }
    }

    fun clearSelectedDay() {
        _uiState.update { it.copy(selectedDay = null, selectedDayData = null) }
    }

    /**
     * Navigates directly to a specific day's detail view. Aggregations now run
     * on Dispatchers.Default — chained inside a single coroutine here so the
     * day's data is actually populated by the time `selectDay` looks for it.
     */
    fun navigateToDay(date: LocalDate) {
        val ym = YearMonth.of(date.year, date.month)
        viewModelScope.launch {
            _uiState.update { it.copy(selectedYear = date.year) }
            aggregateByMonth(date.year)
            _uiState.update { it.copy(selectedMonth = ym) }
            aggregateByDay(ym)
            val dayData = _uiState.value.dailyData.find { it.date == date }
            _uiState.update { it.copy(selectedDay = date, selectedDayData = dayData) }
        }
    }

    private fun loadAllDrives() {
        val id = carId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isRefreshing) {
                _uiState.update { it.copy(isLoading = true) }
            }

            val drivesDeferred = async { repository.getDrives(id) }
            val chargesDeferred = async { repository.getCharges(id) }

            val drivesResult = drivesDeferred.await()
            val chargesResult = chargesDeferred.await()

            when (drivesResult) {
                is ApiResult.Success -> {
                    val drives = drivesResult.data
                    val charges = (chargesResult as? ApiResult.Success)?.data ?: emptyList()
                    val isImperial = _uiState.value.units?.isImperial == true

                    // Pre-parse all dates and run the lifetime / yearly
                    // aggregation in a single Dispatchers.Default block so the
                    // UI thread stays free during the heavy lift. With 500
                    // drives the parsing alone is ~1,500 calls that previously
                    // ran on Main.
                    data class PreparedAndYear(
                        val timedDrives: List<TimedDrive>,
                        val timedCharges: List<TimedCharge>,
                        val agg: YearAggregation,
                    )
                    val prepared = withContext(Dispatchers.Default) {
                        val td = drives.mapNotNull { d ->
                            parseDateTime(d.startDate)?.let { TimedDrive(d, it) }
                        }
                        val tc = charges.mapNotNull { c ->
                            parseDateTime(c.startDate)?.let { TimedCharge(c, it) }
                        }
                        PreparedAndYear(td, tc, computeYearAggregation(td, tc, isImperial))
                    }

                    timedDrives = prepared.timedDrives
                    timedCharges = prepared.timedCharges

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            allDrives = drives,
                            allCharges = charges,
                            error = null,
                            yearlyData = prepared.agg.yearlyData,
                            totalLifetimeDistance = prepared.agg.totalLifetimeDistance,
                            avgYearlyDistance = prepared.agg.avgYearlyDistance,
                            firstDriveDate = prepared.agg.firstDriveDate,
                            totalLifetimeEnergy = prepared.agg.totalLifetimeEnergy,
                            avgLifetimeEnergyDistance = prepared.agg.avgLifetimeEnergyDistance,
                            totalLifetimeEnergyCost = prepared.agg.totalLifetimeEnergyCost,
                            totalLifetimeDriveCount = prepared.agg.totalLifetimeDriveCount,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = drivesResult.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun aggregateByMonth(year: Int) {
        val isImperial = _uiState.value.units?.isImperial == true
        val drivesSnapshot = timedDrives
        val chargesSnapshot = timedCharges
        val agg = withContext(Dispatchers.Default) {
            computeMonthAggregation(drivesSnapshot, chargesSnapshot, year, isImperial)
        }
        _uiState.update {
            it.copy(
                monthlyData = agg.monthlyData,
                yearTotalDistance = agg.yearTotalDistance,
                avgMonthlyDistance = agg.avgMonthlyDistance,
                yearTotalEnergy = agg.yearTotalEnergy,
                avgYearEnergyDistance = agg.avgYearEnergyDistance,
                yearTotalEnergyCost = agg.yearTotalEnergyCost,
                yearDriveCount = agg.yearDriveCount,
            )
        }
    }

    private suspend fun aggregateByDay(yearMonth: YearMonth) {
        val drivesSnapshot = timedDrives
        val chargesSnapshot = timedCharges
        val dailyData = withContext(Dispatchers.Default) {
            computeDailyAggregation(drivesSnapshot, chargesSnapshot, yearMonth)
        }
        _uiState.update { it.copy(dailyData = dailyData) }
    }

    // Get yearly data for chart
    fun getYearlyChartData(): List<Pair<Int, Double>> {
        val state = _uiState.value
        return state.yearlyData.map { it.year to it.totalDistance }
            .sortedBy { it.first }
    }

    // Get monthly data for chart (all 12 months, with 0 for missing months)
    fun getMonthlyChartData(): List<Pair<Int, Double>> {
        val state = _uiState.value
        val monthlyMap = state.monthlyData.associate { it.yearMonth.monthValue to it.totalDistance }

        return (1..12).map { month ->
            month to (monthlyMap[month] ?: 0.0)
        }
    }

    // Get daily data for chart within selected month
    fun getDailyChartData(): List<Pair<Int, Double>> {
        val state = _uiState.value
        val selectedMonth = state.selectedMonth ?: return emptyList()
        val dailyMap = state.dailyData.associate { it.date.dayOfMonth to it.totalDistance }

        val daysInMonth = selectedMonth.lengthOfMonth()
        return (1..daysInMonth).map { day ->
            day to (dailyMap[day] ?: 0.0)
        }.filter { it.second > 0 } // Only return days with data for cleaner chart
    }
}

// ---------------------------------------------------------------------------
// Pure aggregation helpers. Kept as top-level private functions so they have
// no dependency on VM state and can run safely on Dispatchers.Default — the
// caller passes in the snapshot of pre-parsed data they want aggregated.
// ---------------------------------------------------------------------------

private fun parseDateTime(dateStr: String?): LocalDateTime? =
    parseIsoDateTime(dateStr)

private fun computeYearAggregation(
    drives: List<TimedDrive>,
    charges: List<TimedCharge>,
    isImperial: Boolean,
): YearAggregation {
    val grouped = drives.groupBy { it.dateTime.year }
    val chargesByYear = charges.groupBy { it.dateTime.year }

    val yearlyData = grouped.map { (year, yearDrives) ->
        var totalDistance = 0.0
        var totalEnergy = 0.0
        var totalBatteryUsage = 0.0
        for (td in yearDrives) {
            totalDistance += td.drive.distance ?: 0.0
            totalEnergy += td.drive.energyConsumedNet ?: 0.0
            val start = td.drive.startBatteryLevel
            val end = td.drive.endBatteryLevel
            if (start != null && end != null) {
                totalBatteryUsage += (start - end).toDouble()
            }
        }
        val totalCost = chargesByYear[year]
            ?.mapNotNull { it.charge.cost }
            ?.sum()
            ?.takeIf { it > 0 }

        YearlyMileage(
            year = year,
            totalDistance = totalDistance,
            driveCount = yearDrives.size,
            totalEnergy = totalEnergy,
            totalBatteryUsage = totalBatteryUsage,
            totalEnergyCost = totalCost,
            drives = yearDrives.map { it.drive },
        )
    }.sortedByDescending { it.year }

    val totalLifetimeDistance = yearlyData.sumOf { it.totalDistance }
    val totalLifetimeDriveCount = yearlyData.sumOf { it.driveCount }
    val totalLifetimeEnergy = yearlyData.sumOf { it.totalEnergy }
    val distanceForEfficiency = if (isImperial) totalLifetimeDistance * 0.621371 else totalLifetimeDistance
    val avgLifetimeEnergyDistance = if (distanceForEfficiency > 0) (totalLifetimeEnergy * 1000.0) / distanceForEfficiency else 0.0
    val totalLifetimeEnergyCost = charges.mapNotNull { it.charge.cost }.sum().takeIf { it > 0 }

    val firstDriveDate = drives.minByOrNull { it.dateTime }?.dateTime?.toLocalDate()

    val avgYearlyDistance = if (firstDriveDate != null && totalLifetimeDistance > 0) {
        val daysSinceFirstDrive = java.time.temporal.ChronoUnit.DAYS.between(firstDriveDate, LocalDate.now())
        if (daysSinceFirstDrive > 0) {
            (totalLifetimeDistance / daysSinceFirstDrive) * 365
        } else {
            totalLifetimeDistance
        }
    } else {
        0.0
    }

    return YearAggregation(
        yearlyData = yearlyData,
        totalLifetimeDistance = totalLifetimeDistance,
        totalLifetimeDriveCount = totalLifetimeDriveCount,
        totalLifetimeEnergy = totalLifetimeEnergy,
        avgLifetimeEnergyDistance = avgLifetimeEnergyDistance,
        totalLifetimeEnergyCost = totalLifetimeEnergyCost,
        firstDriveDate = firstDriveDate,
        avgYearlyDistance = avgYearlyDistance,
    )
}

private fun computeMonthAggregation(
    drives: List<TimedDrive>,
    charges: List<TimedCharge>,
    year: Int,
    isImperial: Boolean,
): MonthAggregation {
    val yearDrives = drives.filter { it.dateTime.year == year }
    val grouped = yearDrives.groupBy { YearMonth.of(it.dateTime.year, it.dateTime.month) }
    val chargesByYearMonth = charges
        .filter { it.dateTime.year == year }
        .groupBy { YearMonth.of(it.dateTime.year, it.dateTime.month) }

    val monthlyData = grouped.map { (yearMonth, monthDrives) ->
        var totalDistance = 0.0
        var totalEnergy = 0.0
        var totalBatteryUsage = 0.0
        for (td in monthDrives) {
            totalDistance += td.drive.distance ?: 0.0
            totalEnergy += td.drive.energyConsumedNet ?: 0.0
            val start = td.drive.startBatteryLevel
            val end = td.drive.endBatteryLevel
            if (start != null && end != null) {
                totalBatteryUsage += (start - end).toDouble()
            }
        }
        val totalCost = chargesByYearMonth[yearMonth]
            ?.mapNotNull { it.charge.cost }
            ?.sum()
            ?.takeIf { it > 0 }

        MonthlyMileage(
            yearMonth = yearMonth,
            totalDistance = totalDistance,
            driveCount = monthDrives.size,
            totalEnergy = totalEnergy,
            totalBatteryUsage = totalBatteryUsage,
            totalEnergyCost = totalCost,
            drives = monthDrives.map { it.drive },
        )
    }.sortedByDescending { it.yearMonth }

    val yearTotalDistance = monthlyData.sumOf { it.totalDistance }
    val yearDriveCount = monthlyData.sumOf { it.driveCount }
    val avgMonthlyDistance = if (monthlyData.isNotEmpty()) yearTotalDistance / monthlyData.size else 0.0
    val yearTotalEnergy = monthlyData.sumOf { it.totalEnergy }
    val distanceForEfficiency = if (isImperial) yearTotalDistance * 0.621371 else yearTotalDistance
    val avgYearEnergyDistance = if (distanceForEfficiency > 0) (yearTotalEnergy * 1000.0) / distanceForEfficiency else 0.0
    val yearTotalEnergyCost = monthlyData.mapNotNull { it.totalEnergyCost }.sum().takeIf { it > 0 }

    return MonthAggregation(
        monthlyData = monthlyData,
        yearTotalDistance = yearTotalDistance,
        avgMonthlyDistance = avgMonthlyDistance,
        yearDriveCount = yearDriveCount,
        yearTotalEnergy = yearTotalEnergy,
        avgYearEnergyDistance = avgYearEnergyDistance,
        yearTotalEnergyCost = yearTotalEnergyCost,
    )
}

private fun computeDailyAggregation(
    drives: List<TimedDrive>,
    charges: List<TimedCharge>,
    yearMonth: YearMonth,
): List<DailyMileage> {
    val monthDrives = drives.filter {
        YearMonth.of(it.dateTime.year, it.dateTime.month) == yearMonth
    }
    val grouped = monthDrives.groupBy { it.dateTime.toLocalDate() }
    val chargesByDay = charges
        .filter { YearMonth.of(it.dateTime.year, it.dateTime.month) == yearMonth }
        .groupBy { it.dateTime.toLocalDate() }

    return grouped.map { (date, dayDrives) ->
        var totalDistance = 0.0
        var totalEnergy = 0.0
        var totalBatteryUsage = 0.0
        for (td in dayDrives) {
            totalDistance += td.drive.distance ?: 0.0
            totalEnergy += td.drive.energyConsumedNet ?: 0.0
            val start = td.drive.startBatteryLevel
            val end = td.drive.endBatteryLevel
            if (start != null && end != null) {
                totalBatteryUsage += (start - end).toDouble()
            }
        }
        val totalCost = chargesByDay[date]
            ?.mapNotNull { it.charge.cost }
            ?.sum()
            ?.takeIf { it > 0 }

        DailyMileage(
            date = date,
            totalDistance = totalDistance,
            driveCount = dayDrives.size,
            totalEnergy = totalEnergy,
            totalBatteryUsage = totalBatteryUsage,
            totalEnergyCost = totalCost,
            drives = dayDrives
                .sortedByDescending { it.dateTime }
                .map { it.drive },
        )
    }.sortedByDescending { it.date }
}
