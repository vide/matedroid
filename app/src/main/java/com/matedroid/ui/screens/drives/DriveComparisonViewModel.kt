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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the comparison leaderboard is ranked (and, for line charts, which metric is plotted). */
enum class DriveCompareSort { EFFICIENCY, DURATION, SPEED }

/** A resampled point of a drive's overlay line: the metric [value] at a [distance] along the route. */
data class DriveCurvePoint(val distance: Float, val value: Float)

/** A drive's overlay line in the currently-selected metric. */
data class DriveSessionCurve(
    val driveId: Int,
    val isBase: Boolean,
    val points: List<DriveCurvePoint>
)

/** Raw per-point data for a drive, kept metric-independent so switching metric needs no refetch. */
private data class DriveCurveSample(val distance: Float, val speed: Float, val energyKwh: Float)
private data class DriveCurveData(val driveId: Int, val isBase: Boolean, val samples: List<DriveCurveSample>)

data class DriveComparisonUiState(
    val isLoading: Boolean = true,
    val comparison: DriveComparison? = null,
    val sort: DriveCompareSort = DriveCompareSort.SPEED,
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
    private val curveDataCache = mutableMapOf<Int, DriveCurveData>()
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
            refresh(carId)
        }
    }

    fun setSort(sort: DriveCompareSort) {
        _uiState.update { it.copy(sort = sort) }
        carId?.let { refresh(it) }
    }

    /** Rebuild the line overlay (curves + average) for the active metric. Duration uses bars, no curves. */
    private fun refresh(carId: Int) {
        val comparison = _uiState.value.comparison ?: return
        val sort = _uiState.value.sort

        // Duration is shown as bars from the leaderboard values — no per-point curves needed.
        if (sort == DriveCompareSort.DURATION) {
            curveFetchJob?.cancel()
            _uiState.update { it.copy(curves = emptyList(), averageCurve = emptyList(), curvesLoading = false) }
            return
        }

        val imperial = _uiState.value.units?.isImperial == true
        val ordered = listOf(comparison.base) + sortedOthers(comparison.others, sort).take(MAX_CURVES - 1)
        val sampleIds = sampleDriveIds(comparison.all.map { it.driveId })

        curveFetchJob?.cancel()
        curveFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(curvesLoading = true) }

            // Fetch any missing drive details concurrently (network calls overlap).
            suspend fun ensureFetched(ids: List<Int>) = coroutineScope {
                ids.distinct()
                    .filter { !curveDataCache.containsKey(it) }
                    .map { id -> async { id to buildCurveData(carId, id, id == comparison.base.driveId, imperial) } }
                    .awaitAll()
                    .forEach { (id, data) -> if (data != null) curveDataCache[id] = data }
            }

            // Phase 1: the overlaid curves — show them as soon as they're ready.
            ensureFetched(ordered.map { it.driveId })
            val curves = ordered.mapNotNull { drive ->
                curveDataCache[drive.driveId]?.let { data ->
                    DriveSessionCurve(drive.driveId, drive.isBase, metricPoints(data.samples, sort))
                }
            }
            _uiState.update { it.copy(curves = curves, curvesLoading = false) }

            // Phase 2: the dashed average from a wider sample — fills in afterwards.
            ensureFetched(sampleIds)
            val sampleCurves = sampleIds
                .mapNotNull { id -> curveDataCache[id]?.samples?.let { metricPoints(it, sort) } }
                .filter { it.size >= 2 }
            _uiState.update { it.copy(averageCurve = averageCurves(sampleCurves)) }
        }
    }

    /**
     * Per-point series for the chart. Speed is plotted directly; consumption is the instantaneous
     * Wh/(km|mi) over a short trailing-distance window (ΔkWh / Δdistance × 1000) — smooth and bounded,
     * unlike raw power/speed which blows up at low speed.
     */
    private fun metricPoints(samples: List<DriveCurveSample>, sort: DriveCompareSort): List<DriveCurvePoint> {
        if (sort != DriveCompareSort.EFFICIENCY) {
            return samples.map { DriveCurvePoint(it.distance, it.speed) }
        }
        val maxDistance = samples.lastOrNull()?.distance ?: 0f
        if (maxDistance <= 0f) return emptyList()
        val window = (maxDistance / 25f).coerceAtLeast(0.2f) // ~25 segments along the route
        val points = ArrayList<DriveCurvePoint>(samples.size)
        var lo = 0
        for (i in samples.indices) {
            while (lo < i && samples[i].distance - samples[lo].distance > window) lo++
            val dDist = samples[i].distance - samples[lo].distance
            if (dDist > 0f) {
                val dEnergy = samples[i].energyKwh - samples[lo].energyKwh
                points.add(DriveCurvePoint(samples[i].distance, (dEnergy / dDist) * 1000f))
            }
        }
        return points
    }

    private fun sortedOthers(others: List<ComparableDrive>, sort: DriveCompareSort): List<ComparableDrive> =
        when (sort) {
            DriveCompareSort.EFFICIENCY -> others.sortedBy { it.efficiency ?: Double.MAX_VALUE }
            DriveCompareSort.DURATION -> others.sortedBy { it.durationMin }
            DriveCompareSort.SPEED -> others.sortedByDescending { it.speedAvg }
        }

    /**
     * Fetch a drive and derive its per-point samples: display distance, display speed, and cumulative
     * energy used (kWh). Energy is integrated as power × (segment distance / speed), so no timestamps.
     */
    private suspend fun buildCurveData(carId: Int, driveId: Int, isBase: Boolean, imperial: Boolean): DriveCurveData? {
        val detail = when (val result = repository.getDriveDetail(carId, driveId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return null
        }
        val positions = detail.positions ?: return null
        val toDisplay = if (imperial) 0.621371f else 1f

        var cumDistanceDisplay = 0f
        var cumEnergyKwh = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null
        val samples = ArrayList<DriveCurveSample>(positions.size)
        positions.forEach { position ->
            val lat = position.latitude
            val lon = position.longitude
            val speed = position.speed
            if (lat != null && lon != null) {
                val pLat = prevLat
                val pLon = prevLon
                if (pLat != null && pLon != null) {
                    // Haversine over raw GPS is always km; convert the segment to the display unit
                    // so it matches the API speed, which arrives pre-converted (km/h or mph).
                    val segDisplay = haversineMeters(pLat, pLon, lat, lon) / 1000.0 * toDisplay
                    cumDistanceDisplay += segDisplay.toFloat()
                    val power = position.power
                    if (speed != null && speed > 0 && power != null) {
                        cumEnergyKwh += power.toDouble() * (segDisplay / speed.toDouble()) // kW × h
                    }
                }
                prevLat = lat
                prevLon = lon
                if (speed != null) {
                    samples.add(DriveCurveSample(cumDistanceDisplay, speed.toFloat(), cumEnergyKwh.toFloat()))
                }
            }
        }
        return if (samples.size >= 2) DriveCurveData(driveId, isBase, samples) else null
    }

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
            val values = curves.mapNotNull { interpolate(it, distance) }
            if (values.isNotEmpty()) result.add(DriveCurvePoint(distance, values.average().toFloat()))
        }
        return result
    }

    private fun interpolate(points: List<DriveCurvePoint>, distance: Float): Float? {
        if (points.isEmpty() || distance < points.first().distance || distance > points.last().distance) return null
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (distance <= b.distance) {
                val span = b.distance - a.distance
                if (span <= 0f) return a.value
                val t = (distance - a.distance) / span
                return a.value + t * (b.value - a.value)
            }
        }
        return points.last().value
    }

    companion object {
        const val MAX_CURVES = 5
        const val AVERAGE_SAMPLE = 10
    }
}
