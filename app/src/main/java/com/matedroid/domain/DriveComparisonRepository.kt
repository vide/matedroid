package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.util.haversineMeters
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/** One drive in a comparison set, assembled from cached summary data. */
data class ComparableDrive(
    val driveId: Int,
    val startDate: String,
    val startAddress: String,
    val endAddress: String,
    val efficiency: Double?,
    val distance: Double,
    val durationMin: Int,
    val speedAvg: Int,
    val outsideTempAvg: Double?,
    val isBase: Boolean
)

/** A base drive plus the other drives along the same route. */
data class DriveComparison(
    val base: ComparableDrive,
    val others: List<ComparableDrive>,
    val endpointRadiusMeters: Double
) {
    val all: List<ComparableDrive> get() = others + base
    val totalCount: Int get() = others.size + 1
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
    ): DriveComparison? {
        val summaries = driveSummaryDao.getAllForCar(carId)
        val baseSummary = summaries.firstOrNull { it.driveId == baseDriveId } ?: return null

        val aggregates = aggregateDao.getDriveAggregatesForCar(carId).associateBy { it.driveId }
        val baseAgg = aggregates[baseDriveId] ?: return null
        val baseStartLat = baseAgg.startLatitude ?: return null
        val baseStartLon = baseAgg.startLongitude ?: return null
        val baseEndLat = baseAgg.endLatitude ?: return null
        val baseEndLon = baseAgg.endLongitude ?: return null
        val baseDistance = baseSummary.distance

        fun toComparable(s: DriveSummary): ComparableDrive = ComparableDrive(
            driveId = s.driveId,
            startDate = s.startDate,
            startAddress = s.startAddress,
            endAddress = s.endAddress,
            efficiency = s.efficiency,
            distance = s.distance,
            durationMin = s.durationMin,
            speedAvg = s.speedAvg,
            outsideTempAvg = s.outsideTempAvg,
            isBase = s.driveId == baseDriveId
        )

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

        if (others.isEmpty()) return null
        return DriveComparison(
            base = toComparable(baseSummary),
            others = others,
            endpointRadiusMeters = endpointRadiusMeters
        )
    }
}
