package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.util.haversineMeters
import javax.inject.Inject
import javax.inject.Singleton

/** One DC charge in a comparison set, assembled from cached summary + aggregate data. */
data class ComparableCharge(
    val chargeId: Int,
    val startDate: String,
    val address: String,
    val brand: String?,
    val peakKw: Int?,
    val energyAdded: Double,
    val durationMin: Int,
    val cost: Double?,
    val startBattery: Int,
    val endBattery: Int,
    val outsideTempAvg: Double?,
    val distanceMeters: Double,
    val isBase: Boolean
) {
    /** Cost per kWh, or null when the charge is free/uncosted or has no energy. */
    val costPerKwh: Double?
        get() = cost?.takeIf { it > 0 && energyAdded > 0 }?.let { it / energyAdded }
}

/** A base DC charge plus the other comparable DC charges in the same area. */
data class ChargeComparison(
    val base: ComparableCharge,
    val others: List<ComparableCharge>,
    val radiusMeters: Double
) {
    /** Base + others, for ranking and overlays. */
    val all: List<ComparableCharge> get() = others + base

    val totalCount: Int get() = others.size + 1

    /** 1-based rank of the base charge by peak power among all sessions (1 = highest peak). */
    val basePeakRank: Int
        get() = all.sortedByDescending { it.peakKw ?: -1 }.indexOfFirst { it.isBase } + 1
}

/**
 * Finds comparable DC charges for a given charge: same area (within a radius), DC-only. The data
 * comes entirely from the local cache (charge summaries + detail aggregates), so it's cheap and
 * works offline. AC charges and charges with no nearby DC sibling return null — nothing to compare.
 */
@Singleton
class ChargeComparisonRepository @Inject constructor(
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao
) {
    companion object {
        /** Generous enough to catch multiple CPOs at one motorway stop (e.g. a Supercharger and an Ionity). */
        const val DEFAULT_RADIUS_METERS = 2_000.0
    }

    suspend fun findComparable(
        carId: Int,
        baseChargeId: Int,
        radiusMeters: Double = DEFAULT_RADIUS_METERS
    ): ChargeComparison? {
        val summaries = chargeSummaryDao.getAllForCar(carId)
        val baseSummary = summaries.firstOrNull { it.chargeId == baseChargeId } ?: return null

        val dcIds = aggregateDao.getDcChargeIds(carId).toSet()
        if (baseChargeId !in dcIds) return null

        val aggregates = aggregateDao.getChargeAggregatesForCar(carId).associateBy { it.chargeId }

        fun toComparable(s: ChargeSummary, distanceMeters: Double): ComparableCharge {
            val agg = aggregates[s.chargeId]
            return ComparableCharge(
                chargeId = s.chargeId,
                startDate = s.startDate,
                address = s.address,
                brand = agg?.fastChargerBrand,
                peakKw = agg?.maxChargerPower,
                energyAdded = s.energyAdded,
                durationMin = s.durationMin,
                cost = s.cost,
                startBattery = s.startBatteryLevel,
                endBattery = s.endBatteryLevel,
                outsideTempAvg = s.outsideTempAvg,
                distanceMeters = distanceMeters,
                isBase = s.chargeId == baseChargeId
            )
        }

        val others = summaries
            .asSequence()
            .filter { it.chargeId != baseChargeId && it.chargeId in dcIds }
            .map { it to haversineMeters(baseSummary.latitude, baseSummary.longitude, it.latitude, it.longitude) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .map { (s, distance) -> toComparable(s, distance) }
            .sortedByDescending { it.peakKw ?: -1 }
            .toList()

        if (others.isEmpty()) return null
        return ChargeComparison(
            base = toComparable(baseSummary, 0.0),
            others = others,
            radiusMeters = radiusMeters
        )
    }
}
