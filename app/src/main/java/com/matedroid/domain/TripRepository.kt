package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.SavedTripDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTrip
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SavedTripWithLegs
import com.matedroid.domain.model.Trip
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for reading and persisting trips.
 *
 * Ingests raw drives + DC charges, runs [TripDetector] to find new trips, and
 * silently persists them as [SavedTrip] rows on first detection. Subsequent calls
 * read from the saved trips table, skipping any detected fingerprint that is
 * already represented by a saved trip (either directly, or via the consumed set
 * populated by future edit/merge operations).
 *
 * The trip fingerprint is a SHA-256 of the sorted drive IDs — same format used
 * by [com.matedroid.data.local.entity.TripRouteCache] and
 * [com.matedroid.data.local.entity.TripCountryCache] keys, so the existing
 * caches remain valid.
 */
@Singleton
class TripRepository @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val aggregateDao: AggregateDao,
    private val savedTripDao: SavedTripDao,
    private val tripDetector: TripDetector
) {

    /** Returns all trips for a car (saved + newly auto-detected this call), newest first. */
    suspend fun getTrips(carId: Int): List<Trip> {
        val drives = driveSummaryDao.getAllChronological(carId)
        val dcCharges = aggregateDao.getDcChargeSummaries(carId)

        autoPersistNewTrips(carId, drives, dcCharges)

        val saved = savedTripDao.getAllWithLegs(carId)
        return buildTripsFromSaved(saved, drives, dcCharges)
            .sortedByDescending { it.startDate }
    }

    /** Fingerprint for a list of drive IDs. Stable regardless of order. */
    fun computeFingerprint(driveIds: List<Int>): String {
        val ids = driveIds.sorted().joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256").digest(ids.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Convenience: fingerprint of a [Trip]. */
    fun computeFingerprint(trip: Trip): String =
        computeFingerprint(trip.drives.map { it.driveId })

    /** Delete a saved trip. Cascade removes its legs and consumed fingerprints, letting the detector re-emit the originals. */
    suspend fun deleteTrip(tripId: Long) {
        savedTripDao.deleteTrip(tripId)
    }

    private suspend fun autoPersistNewTrips(
        carId: Int,
        drives: List<DriveSummary>,
        dcCharges: List<ChargeSummary>
    ) {
        val existing = savedTripDao.getAllWithLegs(carId)
        val existingFingerprints = existing.map { swl ->
            computeFingerprint(swl.driveIds())
        }.toSet()
        val consumedFingerprints = savedTripDao.getAllConsumedFingerprintsForCar(carId).toSet()
        val suppressed = existingFingerprints + consumedFingerprints

        val detected = tripDetector.detectTrips(drives, dcCharges)
        if (detected.isEmpty()) return

        val now = System.currentTimeMillis()
        for (trip in detected) {
            val fp = computeFingerprint(trip)
            if (fp in suppressed) continue

            savedTripDao.insertTripWithLegs(
                trip = SavedTrip(
                    carId = carId,
                    name = null,
                    source = SavedTrip.SOURCE_AUTO_DETECTED,
                    createdAt = now,
                    updatedAt = now
                ),
                legs = { tripId -> buildChronologicalLegs(tripId, trip) }
            )
        }
    }

    private fun buildChronologicalLegs(tripId: Long, trip: Trip): List<SavedTripLeg> {
        data class Event(val date: String, val type: String, val id: Int)

        val events = buildList {
            trip.drives.forEach { add(Event(it.startDate, SavedTripLeg.TYPE_DRIVE, it.driveId)) }
            trip.charges.forEach { add(Event(it.startDate, SavedTripLeg.TYPE_CHARGE, it.chargeId)) }
        }.sortedBy { it.date }

        return events.mapIndexed { index, e ->
            SavedTripLeg(tripId = tripId, position = index, legType = e.type, legId = e.id)
        }
    }

    /**
     * Resolve saved trip legs back into a Trip domain object.
     *
     * PR 1 scope: all saved trips are AUTO_DETECTED, so every charge leg is a DC charge
     * and resolves via [dcCharges]. When PR 2 introduces AC charges in user-edited trips,
     * this will need to consult the full charges_summary table.
     */
    private fun buildTripsFromSaved(
        saved: List<SavedTripWithLegs>,
        drives: List<DriveSummary>,
        dcCharges: List<ChargeSummary>
    ): List<Trip> {
        val drivesById = drives.associateBy { it.driveId }
        val chargesById = dcCharges.associateBy { it.chargeId }

        return saved.mapNotNull { swl ->
            val orderedLegs = swl.legs.sortedBy { it.position }
            val tripDrives = orderedLegs
                .filter { it.legType == SavedTripLeg.TYPE_DRIVE }
                .mapNotNull { drivesById[it.legId] }
            val tripCharges = orderedLegs
                .filter { it.legType == SavedTripLeg.TYPE_CHARGE }
                .mapNotNull { chargesById[it.legId] }
            TripAggregator.buildTrip(tripDrives, tripCharges)
        }
    }

    private fun SavedTripWithLegs.driveIds(): List<Int> =
        legs.filter { it.legType == SavedTripLeg.TYPE_DRIVE }.map { it.legId }
}
