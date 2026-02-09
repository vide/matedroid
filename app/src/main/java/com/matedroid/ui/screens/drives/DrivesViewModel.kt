package com.matedroid.ui.screens.drives

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

enum class DriveChartGranularity {
    DAILY, WEEKLY, MONTHLY
}

enum class DriveDistanceFilter(
    val maxDistanceKm: Double?,
    val minDistanceKm: Double?
) {
    ALL(null, null),
    COMMUTE(10.0, null),           // < 10 km / 6 mi
    DAY_TRIP(100.0, 10.0),         // 10-100 km / 6-60 mi
    ROAD_TRIP(null, 100.0);        // > 100 km / 60 mi

    fun getLabel(units: Units?): String {
        val isImperial = units?.isImperial == true
        return when (this) {
            ALL -> "All"
            COMMUTE -> if (isImperial) "Commute (< 6 mi)" else "Commute (< 10 km)"
            DAY_TRIP -> if (isImperial) "Day trip (6-60 mi)" else "Day trip (10-100 km)"
            ROAD_TRIP -> if (isImperial) "Road trip (> 60 mi)" else "Road trip (> 100 km)"
        }
    }
}

data class DriveChartData(
    val label: String,
    val count: Int,
    val totalDistance: Double,
    val totalDurationMin: Int,
    val maxSpeed: Int,
    val sortKey: Long
)

enum class DriveDateFilter(@get:StringRes val labelRes: Int, val days: Long?) {
    TODAY(R.string.filter_today, 0),
    LAST_7_DAYS(R.string.filter_last_7_days, 7),
    LAST_30_DAYS(R.string.filter_last_30_days, 30),
    LAST_90_DAYS(R.string.filter_last_90_days, 90),
    LAST_YEAR(R.string.filter_last_year, 365),
    ALL_TIME(R.string.filter_all_time, null)
}

data class DrivesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val drives: List<DriveData> = emptyList(),
    val chartData: List<DriveChartData> = emptyList(),
    val chartGranularity: DriveChartGranularity = DriveChartGranularity.MONTHLY,
    val error: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val summary: DrivesSummary = DrivesSummary(),
    val units: Units? = null,
    val distanceFilter: DriveDistanceFilter = DriveDistanceFilter.ALL,
    val dateFilter: DriveDateFilter = DriveDateFilter.LAST_7_DAYS,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0
)

data class DrivesSummary(
    val totalDrives: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMin: Int = 0,
    val avgDistancePerDrive: Double = 0.0,
    val avgDurationPerDrive: Int = 0,
    val maxSpeedKmh: Int = 0
)

@HiltViewModel
class DrivesViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrivesUiState())
    val uiState: StateFlow<DrivesUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var showShortDrivesCharges: Boolean = false
    private var allDrives: List<DriveData> = emptyList()
    private var isInitialized: Boolean = false

    companion object {
        private const val MIN_DURATION_MINUTES = 1
        private const val MIN_DISTANCE_KM = 0.1
    }

    fun setCarId(id: Int) {
        if (carId == id && isInitialized) {
            // Already initialized with this car, don't reload
            return
        }
        carId = id
        loadUnits(id)

        // Only apply default filter on first initialization
        if (!isInitialized) {
            isInitialized = true
            setDateFilter(_uiState.value.dateFilter)
        }
    }

    fun setDateFilter(filter: DriveDateFilter) {
        val endDate = LocalDate.now()
        val startDate = filter.days?.let { days ->
            if (days > 0) endDate.minusDays(days - 1) else endDate
        }
        _uiState.update { it.copy(
            dateFilter = filter,
            startDate = startDate,
            endDate = if (filter.days != null) endDate else null
        )}
        loadDrives(startDate, if (filter.days != null) endDate else null)
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _uiState.update { it.copy(scrollPosition = index, scrollOffset = offset) }
    }

    fun setDistanceFilter(filter: DriveDistanceFilter) {
        _uiState.update { it.copy(distanceFilter = filter) }
        applyFiltersAndUpdateState()
    }

    fun refresh() {
        carId?.let {
            _uiState.update { it.copy(isRefreshing = true) }
            val state = _uiState.value
            loadDrives(state.startDate, state.endDate)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = repository.getCarStatus(carId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(units = result.data.units) }
                }
                is ApiResult.Error -> { /* ignore, units will default to metric */ }
            }
        }
    }

    private fun loadDrives(startDate: LocalDate? = null, endDate: LocalDate? = null) {
        val id = carId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            // Only show loading spinner on initial load, not when changing filters
            if (!state.isRefreshing && state.drives.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }

            // Load the display setting
            showShortDrivesCharges = settingsDataStore.showShortDrivesCharges.first()

            // API expects RFC3339 format:
            // 2006-01-02T15:04:05Z for UTC time
            // 2006-01-02T15:04:05+nn:00 for local time, with nn the timezone offset
            val zoneId = java.time.ZoneId.systemDefault()
            val offsetFormatter = DateTimeFormatter.ofPattern("xxx") // format: +01:00
            val startDateStr = startDate?.let {
                val offset = zoneId.rules.getOffset(it.atStartOfDay())
                "${it}T00:00:00${offset.toString()}"
            }
            val endDateStr = endDate?.let {
                val offset = zoneId.rules.getOffset(it.atTime(23, 59, 59))
                "${it}T23:59:59${offset.toString()}"
            }

            when (val result = repository.getDrives(id, startDateStr, endDateStr)) {
                is ApiResult.Success -> {
                    allDrives = result.data
                    val granularity = determineGranularity(startDate, endDate)

                    _uiState.update {
                        it.copy(
                            chartGranularity = granularity,
                            error = null
                        )
                    }

                    applyFiltersAndUpdateState()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun applyFiltersAndUpdateState() {
        val state = _uiState.value
        val distanceFilter = state.distanceFilter
        val granularity = state.chartGranularity

        // First apply short drives filter
        var filteredDrives = if (showShortDrivesCharges) {
            allDrives
        } else {
            allDrives.filter { drive ->
                (drive.durationMin ?: 0) >= MIN_DURATION_MINUTES &&
                    (drive.distance ?: 0.0) >= MIN_DISTANCE_KM
            }
        }

        // Apply distance filter for list display
        val displayDrives = filteredDrives.filter { drive ->
            val distance = drive.distance ?: 0.0
            val minOk = distanceFilter.minDistanceKm?.let { distance >= it } ?: true
            val maxOk = distanceFilter.maxDistanceKm?.let { distance < it } ?: true
            minOk && maxOk
        }

        // Apply distance filter to all drives for summary/charts (include short drives)
        val drivesForStats = allDrives.filter { drive ->
            val distance = drive.distance ?: 0.0
            val minOk = distanceFilter.minDistanceKm?.let { distance >= it } ?: true
            val maxOk = distanceFilter.maxDistanceKm?.let { distance < it } ?: true
            minOk && maxOk
        }

        // Calculate summary and chart data from filtered drives
        val summary = calculateSummary(drivesForStats)
        val chartData = calculateChartData(drivesForStats, granularity, state.startDate)

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                drives = displayDrives,
                summary = summary,
                chartData = chartData
            )
        }
    }

    private fun determineGranularity(startDate: LocalDate?, endDate: LocalDate?): DriveChartGranularity {
        if (startDate == null || endDate == null) return DriveChartGranularity.MONTHLY
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        return when {
            days <= 30 -> DriveChartGranularity.DAILY
            days <= 90 -> DriveChartGranularity.WEEKLY
            else -> DriveChartGranularity.MONTHLY
        }
    }

    private fun calculateChartData(drives: List<DriveData>, granularity: DriveChartGranularity, startDate: LocalDate?): List<DriveChartData> {
        if (drives.isEmpty()) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val weekFields = WeekFields.of(Locale.getDefault())

        //  Group the drives by day
        val drivesByDay = drives.mapNotNull { drive ->
            drive.startDate?.let {
                try {
                    val date = LocalDateTime.parse(it, formatter).toLocalDate()
                    date.toEpochDay() to drive
                } catch (e: Exception) { null }
            }
        }.groupBy({ it.first }, { it.second })

        return if (granularity == DriveChartGranularity.DAILY) {
            // DAILY ranges (today, last 7 and last 30 days
            // If not startDate (All Time), get the first trip, or today
            val start = startDate ?: (drivesByDay.keys.minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now())
            val end = LocalDate.now()
            val result = mutableListOf<DriveChartData>()
            var current = start
            while (!current.isAfter(end)) {
                val key = current.toEpochDay()
                val drivesInDay = drivesByDay[key] ?: emptyList()
                result.add(
                    createChartPoint(
                        label = current.format(DateTimeFormatter.ofPattern("d/M")),
                        sortKey = key,
                        drives = drivesInDay
                    )
                )
                current = current.plusDays(1)
            }
            result
        } else {
            // WEEKLY and MONTHLY ranges
            drives.mapNotNull { drive ->
                drive.startDate?.let { dateStr ->
                    try {
                        val date = LocalDateTime.parse(dateStr, formatter).toLocalDate()
                        val (label, sortKey) = when (granularity) {
                            DriveChartGranularity.WEEKLY -> {
                                val firstDay = date.with(weekFields.dayOfWeek(), 1)
                                "W${date.get(weekFields.weekOfYear())}" to firstDay.toEpochDay()
                            }
                            else -> { // MONTHLY
                                date.format(DateTimeFormatter.ofPattern("MMM yy")) to YearMonth.from(date).atDay(1).toEpochDay()
                            }
                        }
                        Triple(label, sortKey, drive)
                    } catch (e: Exception) { null }
                }
            }
                .groupBy { it.first to it.second }
                .map { (key, list) -> createChartPoint(key.first, key.second, list.map { it.third }) }
                .sortedBy { it.sortKey }
        }
    }

    // Helper function to centralize chart data creation
    private fun createChartPoint(label: String, sortKey: Long, drives: List<DriveData>): DriveChartData {
        return DriveChartData(
            label = label,
            count = drives.size,
            totalDistance = drives.sumOf { it.distance ?: 0.0 },
            totalDurationMin = drives.sumOf { it.durationMin ?: 0 },
            maxSpeed = drives.maxOfOrNull { it.speedMax ?: 0 } ?: 0,
            sortKey = sortKey
        )
    }

    private fun calculateSummary(drives: List<DriveData>): DrivesSummary {
        if (drives.isEmpty()) return DrivesSummary()

        val totalDistance = drives.sumOf { it.distance ?: 0.0 }
        val totalDuration = drives.sumOf { it.durationMin ?: 0 }
        val maxSpeed = drives.mapNotNull { it.speedMax }.maxOrNull() ?: 0
        val count = drives.size

        return DrivesSummary(
            totalDrives = count,
            totalDistanceKm = totalDistance,
            totalDurationMin = totalDuration,
            avgDistancePerDrive = if (count > 0) totalDistance / count else 0.0,
            avgDurationPerDrive = if (count > 0) totalDuration / count else 0,
            maxSpeedKmh = maxSpeed
        )
    }
}
