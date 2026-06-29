package com.matedroid.ui.screens.drives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.ComparableDrive
import com.matedroid.domain.DriveComparison
import com.matedroid.domain.DriveComparisonRepository
import com.matedroid.util.haversineMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the comparison leaderboard is ranked. */
enum class DriveCompareSort { EFFICIENCY, DURATION, SPEED }

/** One point of a drive curve: speed at a given distance along the route, in display units. */
data class DriveCurvePoint(val distance: Float, val speed: Float)

/** A drive's speed-vs-distance curve, for the overlay chart. */
data class DriveSessionCurve(
    val driveId: Int,
    val isBase: Boolean,
    val points: List<DriveCurvePoint>
)

data class DriveComparisonUiState(
    val isLoading: Boolean = true,
    val comparison: DriveComparison? = null,
    val sort: DriveCompareSort = DriveCompareSort.EFFICIENCY,
    val units: Units? = null,
    val curves: List<DriveSessionCurve> = emptyList(),
    val curvesLoading: Boolean = false,
    val averageCurve: List<DriveCurvePoint> = emptyList()
)

@HiltViewModel
class DriveComparisonViewModel @Inject constructor(
    private val comparisonRepository: DriveComparisonRepository,
    private val repository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveComparisonUiState())
    val uiState: StateFlow<DriveComparisonUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var carId: Int? = null
    private val curveCache = mutableMapOf<Int, DriveSessionCurve>()
    private var curveFetchJob: Job? = null

    fun load(carId: Int, baseDriveId: Int) {
        if (loaded) return
        loaded = true
        this.carId = carId

        viewModelScope.launch {
            val units = when (val status = repository.getCarStatus(carId)) {
                is ApiResult.Success -> status.data.units
                is ApiResult.Error -> null
            }
            val comparison = comparisonRepository.findComparable(carId, baseDriveId)
            _uiState.update { it.copy(isLoading = false, comparison = comparison, units = units) }
            refreshCurves(carId)
            computeAverageCurve(carId)
        }
    }

    fun setSort(sort: DriveCompareSort) {
        _uiState.update { it.copy(sort = sort) }
        carId?.let { refreshCurves(it) }
    }

    private fun refreshCurves(carId: Int) {
        val comparison = _uiState.value.comparison ?: return
        val ordered = listOf(comparison.base) +
            sortedOthers(comparison.others, _uiState.value.sort).take(MAX_CURVES - 1)
        val imperial = _uiState.value.units?.isImperial == true

        curveFetchJob?.cancel()
        curveFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(curvesLoading = true) }
            ordered.forEach { drive ->
                if (!curveCache.containsKey(drive.driveId)) {
                    buildCurve(carId, drive.driveId, drive.isBase, imperial)?.let { curveCache[drive.driveId] = it }
                }
            }
            val curves = ordered.mapNotNull { curveCache[it.driveId] }
            _uiState.update { it.copy(curves = curves, curvesLoading = false) }
        }
    }

    private fun sortedOthers(others: List<ComparableDrive>, sort: DriveCompareSort): List<ComparableDrive> =
        when (sort) {
            DriveCompareSort.EFFICIENCY -> others.sortedBy { it.efficiency ?: Double.MAX_VALUE }
            DriveCompareSort.DURATION -> others.sortedBy { it.durationMin }
            DriveCompareSort.SPEED -> others.sortedByDescending { it.speedAvg }
        }

    private suspend fun buildCurve(carId: Int, driveId: Int, isBase: Boolean, imperial: Boolean): DriveSessionCurve? {
        val detail = when (val result = repository.getDriveDetail(carId, driveId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return null
        }
        val positions = detail.positions ?: return null
        val toDisplay = if (imperial) 0.621371f else 1f // km→mi and km/h→mph share the factor

        var cumulativeMeters = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null
        val points = ArrayList<DriveCurvePoint>(positions.size)
        positions.forEach { position ->
            val lat = position.latitude
            val lon = position.longitude
            if (lat != null && lon != null) {
                val pLat = prevLat
                val pLon = prevLon
                if (pLat != null && pLon != null) {
                    cumulativeMeters += haversineMeters(pLat, pLon, lat, lon)
                }
                prevLat = lat
                prevLon = lon
                position.speed?.let { speed ->
                    val distanceDisplay = (cumulativeMeters / 1000.0).toFloat() * toDisplay
                    points.add(DriveCurvePoint(distanceDisplay, speed.toFloat() * toDisplay))
                }
            }
        }
        return if (points.size >= 2) DriveSessionCurve(driveId, isBase, points) else null
    }

    /**
     * Builds a dashed "average" speed-vs-distance curve from a sample of the route's runs:
     * resamples each run onto a shared distance grid and averages the speed at each step.
     */
    private fun computeAverageCurve(carId: Int) {
        val comparison = _uiState.value.comparison ?: return
        val imperial = _uiState.value.units?.isImperial == true

        viewModelScope.launch {
            val sampleIds = sampleDriveIds(comparison.all.map { it.driveId })
            val sampled = sampleIds.mapNotNull { id ->
                curveCache[id]?.points
                    ?: buildCurve(carId, id, isBase = false, imperial = imperial)?.also { curveCache[id] = it }?.points
            }.filter { it.size >= 2 }

            val average = averageCurves(sampled)
            if (average.isNotEmpty()) {
                _uiState.update { it.copy(averageCurve = average) }
            }
        }
    }

    /** Pick up to [AVERAGE_SAMPLE] drive ids spread evenly across the set, to bound detail fetches. */
    private fun sampleDriveIds(ids: List<Int>): List<Int> {
        if (ids.size <= AVERAGE_SAMPLE) return ids
        return (0 until AVERAGE_SAMPLE)
            .map { ids[it * (ids.size - 1) / (AVERAGE_SAMPLE - 1)] }
            .distinct()
    }

    private fun averageCurves(curves: List<List<DriveCurvePoint>>): List<DriveCurvePoint> {
        if (curves.isEmpty()) return emptyList()
        val maxDistance = curves.maxOf { it.lastOrNull()?.distance ?: 0f }
        if (maxDistance <= 0f) return emptyList()

        val buckets = 40
        val result = ArrayList<DriveCurvePoint>(buckets + 1)
        for (b in 0..buckets) {
            val distance = maxDistance * b / buckets
            val speeds = curves.mapNotNull { interpolateSpeed(it, distance) }
            if (speeds.isNotEmpty()) {
                result.add(DriveCurvePoint(distance, speeds.average().toFloat()))
            }
        }
        return result
    }

    private fun interpolateSpeed(points: List<DriveCurvePoint>, distance: Float): Float? {
        if (points.isEmpty() || distance < points.first().distance || distance > points.last().distance) return null
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (distance <= b.distance) {
                val span = b.distance - a.distance
                if (span <= 0f) return a.speed
                val t = (distance - a.distance) / span
                return a.speed + t * (b.speed - a.speed)
            }
        }
        return points.last().speed
    }

    companion object {
        const val MAX_CURVES = 5
        /** How many runs to sample when computing the average curve (bounds detail fetches). */
        const val AVERAGE_SAMPLE = 10
    }
}
