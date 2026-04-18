package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.SavedTripDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SavedTrip
import com.matedroid.data.local.entity.SavedTripConsumedFingerprint
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SavedTripWithLegs
import com.matedroid.domain.model.Trip
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
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
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val savedTripDao: SavedTripDao,
    private val tripDetector: TripDetector
) {

    /** Returns all trips for a car (saved + newly auto-detected this call), newest first. */
    suspend fun getTrips(carId: Int): List<Trip> {
        val drives = driveSummaryDao.getAllChronological(carId)
        val dcCharges = aggregateDao.getDcChargeSummaries(carId)
        val allCharges = chargeSummaryDao.getAllForCar(carId)

        autoPersistNewTrips(carId, drives, dcCharges)

        val saved = savedTripDao.getAllWithLegs(carId)
        return buildTripsFromSaved(saved, drives, allCharges)
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

    /** The user-chosen name for a trip, or null if the default (start → end) should be shown. */
    suspend fun getTripName(tripId: Long): String? = savedTripDao.getName(tripId)

    /** Set or clear a trip's custom name. Blank names are stored as null so the default is shown. */
    suspend fun renameTrip(tripId: Long, name: String?) {
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
        savedTripDao.updateName(tripId, normalized, System.currentTimeMillis())
    }

    // === Edit/merge (PR 2) ===

    /**
     * Extend [tripId] with [newLegs]. Drive/charge IDs already in the trip are ignored (dedup).
     * Legs are re-sorted chronologically and positions renumbered 0..N-1.
     * If the trip is currently AUTO_DETECTED, its original fingerprint is added to the consumed set
     * (so the detector won't re-emit it) and the source transitions to USER_EDITED.
     */
    suspend fun extendTripWithLegs(tripId: Long, newLegs: List<LegRef>) {
        if (newLegs.isEmpty()) return
        val existing = savedTripDao.getWithLegs(tripId) ?: return
        val carId = existing.trip.carId
        val drives = driveSummaryDao.getAllChronological(carId).associateBy { it.driveId }
        val charges = chargeSummaryDao.getAllForCar(carId).associateBy { it.chargeId }

        val combined = (existing.legs.map { LegRef(it.legType, it.legId) } + newLegs).distinct()
        val sorted = combined.sortedBy { ref -> legStartDate(ref, drives, charges) ?: "" }
        val legsToWrite = sorted.mapIndexed { index, ref ->
            SavedTripLeg(tripId = tripId, position = index, legType = ref.type, legId = ref.id)
        }

        val now = System.currentTimeMillis()
        if (existing.trip.source == SavedTrip.SOURCE_AUTO_DETECTED) {
            val priorFingerprint = computeFingerprint(existing.driveIds())
            savedTripDao.insertConsumedFingerprints(
                listOf(SavedTripConsumedFingerprint(savedTripId = tripId, fingerprint = priorFingerprint))
            )
            savedTripDao.updateSource(tripId, SavedTrip.SOURCE_USER_EDITED, now)
        } else {
            savedTripDao.updateSource(tripId, existing.trip.source, now)
        }
        savedTripDao.replaceLegs(tripId, legsToWrite)
    }

    /**
     * Merge [consumedTripId] into [keptTripId], creating a new USER_MERGED trip and deleting both originals.
     * Auto-fills all drives and charges that occurred between the two trips' date ranges.
     * Preserves the suppression set by inheriting both sources' fingerprints (current + previously consumed)
     * into the new trip's consumed set.
     */
    suspend fun mergeTrips(keptTripId: Long, consumedTripId: Long, carId: Int): Long? {
        val kept = savedTripDao.getWithLegs(keptTripId) ?: return null
        val consumed = savedTripDao.getWithLegs(consumedTripId) ?: return null

        val drives = driveSummaryDao.getAllChronological(carId).associateBy { it.driveId }
        val charges = chargeSummaryDao.getAllForCar(carId).associateBy { it.chargeId }

        val keptRange = tripRange(kept, drives, charges) ?: return null
        val consumedRange = tripRange(consumed, drives, charges) ?: return null
        val gapStart = minOf(keptRange.second, consumedRange.second)
        val gapEnd = maxOf(keptRange.first, consumedRange.first)

        val usedDriveIds = (kept.legs + consumed.legs)
            .filter { it.legType == SavedTripLeg.TYPE_DRIVE }
            .map { it.legId }.toSet()
        val usedChargeIds = (kept.legs + consumed.legs)
            .filter { it.legType == SavedTripLeg.TYPE_CHARGE }
            .map { it.legId }.toSet()

        val gapDrives = drives.values.filter {
            it.driveId !in usedDriveIds &&
                compareDates(it.startDate, gapStart) >= 0 &&
                compareDates(it.startDate, gapEnd) <= 0
        }.map { LegRef(SavedTripLeg.TYPE_DRIVE, it.driveId) }

        val gapCharges = charges.values.filter {
            it.chargeId !in usedChargeIds &&
                compareDates(it.startDate, gapStart) >= 0 &&
                compareDates(it.startDate, gapEnd) <= 0
        }.map { LegRef(SavedTripLeg.TYPE_CHARGE, it.chargeId) }

        val allRefs = (
            kept.legs.map { LegRef(it.legType, it.legId) } +
                consumed.legs.map { LegRef(it.legType, it.legId) } +
                gapDrives + gapCharges
            ).distinct().sortedBy { ref -> legStartDate(ref, drives, charges) ?: "" }

        val inheritedFingerprints = (
            savedTripDao.getConsumedFingerprints(keptTripId) +
                savedTripDao.getConsumedFingerprints(consumedTripId) +
                computeFingerprint(kept.driveIds()) +
                computeFingerprint(consumed.driveIds())
            ).distinct()

        val now = System.currentTimeMillis()
        // Inherit the kept trip's custom name; fall back to the consumed trip's name if only the
        // consumed one had been renamed. Either way, user-chosen names don't disappear at merge.
        val inheritedName = kept.trip.name ?: consumed.trip.name
        val newId = savedTripDao.insertTripWithLegs(
            trip = SavedTrip(
                carId = carId,
                name = inheritedName,
                source = SavedTrip.SOURCE_USER_MERGED,
                createdAt = now,
                updatedAt = now
            ),
            legs = { tripId ->
                allRefs.mapIndexed { index, ref ->
                    SavedTripLeg(tripId = tripId, position = index, legType = ref.type, legId = ref.id)
                }
            }
        )
        savedTripDao.insertConsumedFingerprints(
            inheritedFingerprints.map { SavedTripConsumedFingerprint(savedTripId = newId, fingerprint = it) }
        )
        savedTripDao.deleteTrip(keptTripId)
        savedTripDao.deleteTrip(consumedTripId)
        return newId
    }

    /** Trips for [carId] whose date range is within [windowDays] of [tripId]'s range, excluding [tripId] itself. */
    suspend fun getAdjacentTrips(tripId: Long, carId: Int, windowDays: Int = 14): List<Pair<Long, Trip>> {
        val allSaved = savedTripDao.getAllWithLegs(carId)
        val currentSwl = allSaved.find { it.trip.id == tripId } ?: return emptyList()
        val drives = driveSummaryDao.getAllChronological(carId).associateBy { it.driveId }
        val charges = chargeSummaryDao.getAllForCar(carId).associateBy { it.chargeId }

        val currentRange = tripRange(currentSwl, drives, charges) ?: return emptyList()
        val candidates = mutableListOf<Pair<Long, Trip>>()
        for (swl in allSaved) {
            if (swl.trip.id == tripId) continue
            val range = tripRange(swl, drives, charges) ?: continue
            // Other trip starts after current ends, OR other trip ends before current starts
            val afterGap = daysBetween(currentRange.second, range.first)
            val beforeGap = daysBetween(range.second, currentRange.first)
            val fitsAfter = afterGap != null && afterGap in 0..windowDays.toLong()
            val fitsBefore = beforeGap != null && beforeGap in 0..windowDays.toLong()
            if (!fitsAfter && !fitsBefore) continue
            val trip = TripAggregator.buildTrip(
                tripDrivesIn(swl, drives),
                tripChargesIn(swl, charges)
            ) ?: continue
            candidates.add(swl.trip.id to trip)
        }
        return candidates.sortedBy { it.second.startDate }
    }

    /** Drives and charges near [tripId]'s time range, not already part of any saved trip on [carId]. */
    suspend fun getEligibleNewLegs(tripId: Long, carId: Int, windowDays: Int = 2): EligibleLegs {
        val swl = savedTripDao.getWithLegs(tripId) ?: return EligibleLegs(emptyList(), emptyList())
        val allDrives = driveSummaryDao.getAllChronological(carId)
        val allCharges = chargeSummaryDao.getAllForCar(carId)
        val drivesById = allDrives.associateBy { it.driveId }
        val chargesById = allCharges.associateBy { it.chargeId }

        val range = tripRange(swl, drivesById, chargesById) ?: return EligibleLegs(emptyList(), emptyList())
        val windowStart = shiftDate(range.first, -windowDays.toLong())
        val windowEnd = shiftDate(range.second, windowDays.toLong())

        val usedDriveIds = savedTripDao.getUsedLegIds(carId, SavedTripLeg.TYPE_DRIVE).toSet()
        val usedChargeIds = savedTripDao.getUsedLegIds(carId, SavedTripLeg.TYPE_CHARGE).toSet()

        val drives = allDrives.filter {
            it.driveId !in usedDriveIds &&
                compareDates(it.startDate, windowStart) >= 0 &&
                compareDates(it.startDate, windowEnd) <= 0
        }
        val charges = allCharges.filter {
            it.chargeId !in usedChargeIds &&
                compareDates(it.startDate, windowStart) >= 0 &&
                compareDates(it.startDate, windowEnd) <= 0
        }
        return EligibleLegs(drives, charges)
    }

    // === Helpers ===

    private fun tripDrivesIn(swl: SavedTripWithLegs, drives: Map<Int, DriveSummary>): List<DriveSummary> =
        swl.legs.filter { it.legType == SavedTripLeg.TYPE_DRIVE }.mapNotNull { drives[it.legId] }

    private fun tripChargesIn(swl: SavedTripWithLegs, charges: Map<Int, ChargeSummary>): List<ChargeSummary> =
        swl.legs.filter { it.legType == SavedTripLeg.TYPE_CHARGE }.mapNotNull { charges[it.legId] }

    /** Returns (startDate, endDate) of a saved trip as ISO strings, derived from its legs. */
    private fun tripRange(
        swl: SavedTripWithLegs,
        drives: Map<Int, DriveSummary>,
        charges: Map<Int, ChargeSummary>
    ): Pair<String, String>? {
        val dates = mutableListOf<String>()
        for (leg in swl.legs) {
            when (leg.legType) {
                SavedTripLeg.TYPE_DRIVE -> drives[leg.legId]?.let { dates += it.startDate; dates += it.endDate }
                SavedTripLeg.TYPE_CHARGE -> charges[leg.legId]?.let { dates += it.startDate; dates += it.endDate }
            }
        }
        val start = dates.minOrNull() ?: return null
        val end = dates.maxOrNull() ?: return null
        return start to end
    }

    private fun legStartDate(
        ref: LegRef,
        drives: Map<Int, DriveSummary>,
        charges: Map<Int, ChargeSummary>
    ): String? = when (ref.type) {
        SavedTripLeg.TYPE_DRIVE -> drives[ref.id]?.startDate
        SavedTripLeg.TYPE_CHARGE -> charges[ref.id]?.startDate
        else -> null
    }

    private fun compareDates(a: String, b: String): Int = a.compareTo(b)

    private fun parseDate(s: String): LocalDateTime? = try {
        OffsetDateTime.parse(s).toLocalDateTime()
    } catch (e: DateTimeParseException) {
        try { LocalDateTime.parse(s.replace("Z", "")) } catch (e2: Exception) { null }
    }

    private fun daysBetween(a: String, b: String): Long? {
        val pa = parseDate(a) ?: return null
        val pb = parseDate(b) ?: return null
        return ChronoUnit.DAYS.between(pa, pb)
    }

    private fun shiftDate(s: String, days: Long): String {
        val parsed = parseDate(s) ?: return s
        return parsed.plusDays(days).toString()
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

    /** Resolve saved trip legs back into a Trip domain object, using the full charge set so AC legs resolve too. */
    private fun buildTripsFromSaved(
        saved: List<SavedTripWithLegs>,
        drives: List<DriveSummary>,
        allCharges: List<ChargeSummary>
    ): List<Trip> {
        val drivesById = drives.associateBy { it.driveId }
        val chargesById = allCharges.associateBy { it.chargeId }

        return saved.mapNotNull { swl ->
            val orderedLegs = swl.legs.sortedBy { it.position }
            val tripDrives = orderedLegs
                .filter { it.legType == SavedTripLeg.TYPE_DRIVE }
                .mapNotNull { drivesById[it.legId] }
            val tripCharges = orderedLegs
                .filter { it.legType == SavedTripLeg.TYPE_CHARGE }
                .mapNotNull { chargesById[it.legId] }
            TripAggregator.buildTrip(tripDrives, tripCharges, name = swl.trip.name)
        }
    }

    private fun SavedTripWithLegs.driveIds(): List<Int> =
        legs.filter { it.legType == SavedTripLeg.TYPE_DRIVE }.map { it.legId }

    /** Look up the saved-trip id that matches this trip's current drive set. */
    suspend fun findSavedTripId(carId: Int, trip: Trip): Long? {
        val targetFp = computeFingerprint(trip)
        return savedTripDao.getAllWithLegs(carId)
            .firstOrNull { computeFingerprint(it.driveIds()) == targetFp }
            ?.trip?.id
    }

    /** The saved trip (if any) that currently includes the given drive/charge leg. */
    suspend fun findTripContaining(carId: Int, legType: String, legId: Int): Pair<Long, Trip>? {
        val all = savedTripDao.getAllWithLegs(carId)
        val match = all.firstOrNull { swl ->
            swl.legs.any { it.legType == legType && it.legId == legId }
        } ?: return null

        val drives = driveSummaryDao.getAllChronological(carId).associateBy { it.driveId }
        val charges = chargeSummaryDao.getAllForCar(carId).associateBy { it.chargeId }
        val trip = TripAggregator.buildTrip(
            tripDrivesIn(match, drives),
            tripChargesIn(match, charges)
        ) ?: return null
        return match.trip.id to trip
    }

    /**
     * Remove the given leg from the trip.
     * - If the trip was AUTO_DETECTED, push its pre-edit fingerprint into the consumed set first.
     * - If the last drive leg is removed (trip ends up with 0 drives), delete the whole saved trip
     *   so the user doesn't end up with a ghost row that won't render.
     */
    suspend fun removeLegFromTrip(tripId: Long, ref: LegRef) {
        val existing = savedTripDao.getWithLegs(tripId) ?: return

        val remaining = existing.legs
            .filterNot { it.legType == ref.type && it.legId == ref.id }
            .sortedBy { it.position }
            .mapIndexed { index, leg ->
                SavedTripLeg(tripId = tripId, position = index, legType = leg.legType, legId = leg.legId)
            }

        val remainingDriveCount = remaining.count { it.legType == SavedTripLeg.TYPE_DRIVE }
        if (remainingDriveCount == 0) {
            savedTripDao.deleteTrip(tripId)
            return
        }

        if (existing.trip.source == SavedTrip.SOURCE_AUTO_DETECTED) {
            val priorFingerprint = computeFingerprint(existing.driveIds())
            savedTripDao.insertConsumedFingerprints(
                listOf(SavedTripConsumedFingerprint(savedTripId = tripId, fingerprint = priorFingerprint))
            )
            savedTripDao.updateSource(tripId, SavedTrip.SOURCE_USER_EDITED, System.currentTimeMillis())
        } else {
            savedTripDao.updateSource(tripId, existing.trip.source, System.currentTimeMillis())
        }
        savedTripDao.replaceLegs(tripId, remaining)
    }
}

/** A reference to either a drive or a charge by id, used when editing or merging trips. */
data class LegRef(val type: String, val id: Int)

/** Result of [TripRepository.getEligibleNewLegs]. */
data class EligibleLegs(
    val drives: List<com.matedroid.data.local.entity.DriveSummary>,
    val charges: List<com.matedroid.data.local.entity.ChargeSummary>
)
