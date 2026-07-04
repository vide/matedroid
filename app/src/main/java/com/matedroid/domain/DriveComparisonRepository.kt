package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.util.haversineMeters
import com.matedroid.util.parseIsoDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Exact duration in seconds from start/end timestamps, falling back to whole minutes when unparseable. */
internal fun durationSeconds(start: String, end: String?, durationMin: Int): Int {
    val s = parseIsoDateTime(start)
    val e = end?.let { parseIsoDateTime(it) }
    if (s != null && e != null) {
        val secs = ChronoUnit.SECONDS.between(s, e)
        if (secs > 0) return secs.toInt()
    }
    return durationMin * 60
}

/**
 * One drive in a comparison set, assembled from cached summary data.
 *
 * [durationSeconds] and [avgSpeedPrecise] are higher-resolution versions of [durationMin] /
 * [speedAvg] used only for ranking and percentage deltas (not for display), to avoid ties when
 * several runs round to the same whole minute or km/h.
 */
data class ComparableDrive(
    val driveId: Int,
    val startDate: String,
    val startAddress: String,
    val endAddress: String,
    val efficiency: Double?,
    val distance: Double,
    val durationMin: Int,
    val durationSeconds: Int,
    val speedAvg: Int,
    val avgSpeedPrecise: Double,
    val outsideTempAvg: Double?,
    val isBase: Boolean
)

/** Reference figures averaged across every drive in the comparison set. */
data class DriveAverage(
    val efficiency: Double?,
    val durationMin: Int,
    val durationSeconds: Int,
    val speedAvg: Int,
    val avgSpeedPrecise: Double,
    val count: Int
)

/** A base drive plus the other drives along the same route. */
data class DriveComparison(
    val base: ComparableDrive,
    val others: List<ComparableDrive>,
    val endpointRadiusMeters: Double
) {
    val all: List<ComparableDrive> get() = others + base
    val totalCount: Int get() = others.size + 1

    /** The "average drive" on this route, used as a reference in the leaderboard. */
    val average: DriveAverage
        get() {
            val list = all
            if (list.isEmpty()) return DriveAverage(null, 0, 0, 0, 0.0, 0)
            val efficiencies = list.mapNotNull { it.efficiency }
            return DriveAverage(
                efficiency = if (efficiencies.isNotEmpty()) efficiencies.average() else null,
                durationMin = list.sumOf { it.durationMin } / list.size,
                durationSeconds = list.sumOf { it.durationSeconds } / list.size,
                speedAvg = list.sumOf { it.speedAvg } / list.size,
                avgSpeedPrecise = list.map { it.avgSpeedPrecise }.average(),
                count = list.size
            )
        }
}

/**
 * Finds comparable drives along the same route as a given drive: same start and same end (within a
 * radius), and a similar total distance (so a detour to the same endpoints isn't counted as the same
 * route). Uses only the local cache — drive summaries for the metrics, drive aggregates for the
 * start/end coordinates. Returns null when the route's endpoints aren't known or nothing matches.
 */
@Singleton
class DriveComparisonRepository @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao
) {
    companion object {
        const val DEFAULT_ENDPOINT_RADIUS_METERS = 250.0
        /** A candidate's distance must be within ±25% of the base to count as the same route. */
        const val DISTANCE_TOLERANCE = 0.25
    }

    suspend fun findComparable(
        carId: Int,
        baseDriveId: Int,
        endpointRadiusMeters: Double = DEFAULT_ENDPOINT_RADIUS_METERS
    ): DriveComparison? = withContext(Dispatchers.Default) {
        // Loading the full history and running Haversine per row is CPU work — keep it off the
        // caller's (main) thread. Room still runs the queries themselves on its own executor.
        val summaries = driveSummaryDao.getAllForCar(carId)
        val baseSummary = summaries.firstOrNull { it.driveId == baseDriveId } ?: return@withContext null

        val aggregates = aggregateDao.getDriveAggregatesForCar(carId).associateBy { it.driveId }
        val baseAgg = aggregates[baseDriveId] ?: return@withContext null
        val baseStartLat = baseAgg.startLatitude ?: return@withContext null
        val baseStartLon = baseAgg.startLongitude ?: return@withContext null
        val baseEndLat = baseAgg.endLatitude ?: return@withContext null
        val baseEndLon = baseAgg.endLongitude ?: return@withContext null
        val baseDistance = baseSummary.distance

        fun toComparable(s: DriveSummary): ComparableDrive {
            val seconds = durationSeconds(s.startDate, s.endDate, s.durationMin)
            val precise = if (seconds > 0) s.distance / (seconds / 3600.0) else s.speedAvg.toDouble()
            return ComparableDrive(
                driveId = s.driveId,
                startDate = s.startDate,
                startAddress = s.startAddress,
                endAddress = s.endAddress,
                efficiency = s.efficiency,
                distance = s.distance,
                durationMin = s.durationMin,
                durationSeconds = seconds,
                speedAvg = s.speedAvg,
                avgSpeedPrecise = precise,
                outsideTempAvg = s.outsideTempAvg,
                isBase = s.driveId == baseDriveId
            )
        }

        val others = summaries
            .filter { s ->
                if (s.driveId == baseDriveId) return@filter false
                val agg = aggregates[s.driveId] ?: return@filter false
                val sLat = agg.startLatitude ?: return@filter false
                val sLon = agg.startLongitude ?: return@filter false
                val eLat = agg.endLatitude ?: return@filter false
                val eLon = agg.endLongitude ?: return@filter false
                val startClose = haversineMeters(baseStartLat, baseStartLon, sLat, sLon) <= endpointRadiusMeters
                val endClose = haversineMeters(baseEndLat, baseEndLon, eLat, eLon) <= endpointRadiusMeters
                val distanceOk = baseDistance <= 0.0 ||
                    abs(s.distance - baseDistance) / baseDistance <= DISTANCE_TOLERANCE
                startClose && endClose && distanceOk
            }
            .map { toComparable(it) }
            .sortedBy { it.efficiency ?: Double.MAX_VALUE }

        if (others.isEmpty()) return@withContext null
        DriveComparison(
            base = toComparable(baseSummary),
            others = others,
            endpointRadiusMeters = endpointRadiusMeters
        )
    }
}
