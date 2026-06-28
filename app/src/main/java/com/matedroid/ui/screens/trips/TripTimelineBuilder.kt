package com.matedroid.ui.screens.trips

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.isSignificant
import com.matedroid.domain.model.Trip
import com.matedroid.ui.components.TripTimelineSegment
import com.matedroid.util.parseIsoDateTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val MIN_PARKING_GAP_MIN = 5L

/**
 * Build a chronological list of TripTimelineSegments from a Trip.
 * Inserts Parking segments for any gap >= MIN_PARKING_GAP_MIN between consecutive legs.
 *
 * [dcChargeIds] is the set of charge IDs that are DC fast chargers, used to color the
 * charge segments correctly (orange for DC, green for AC). For legs not in the set, AC is assumed.
 *
 * [showShort] mirrors the `showShortDrivesCharges` setting: when false (the default), short
 * drives/charges are dropped from the timeline so tiny legs don't clutter the strip. See
 * [com.matedroid.domain.ShortEntryFilter] for the shared rule. Totals shown elsewhere on the
 * screen come from the full trip and are unaffected by this flag.
 */
fun buildTimelineSegments(
    trip: Trip,
    dcChargeIds: Set<Int> = emptySet(),
    showShort: Boolean
): List<TripTimelineSegment> {
    val drives = if (showShort) trip.drives else trip.drives.filter { it.isSignificant() }
    val charges = if (showShort) trip.charges else trip.charges.filter { it.isSignificant() }

    val allEvents = mutableListOf<Pair<String, Any>>()
    drives.forEach { allEvents.add(it.startDate to it) }
    charges.forEach { allEvents.add(it.startDate to it) }
    allEvents.sortBy { it.first }

    val segments = mutableListOf<TripTimelineSegment>()
    var driveIdx = 0
    var chargeIdx = 0
    var prevEnd: LocalDateTime? = null

    for ((_, event) in allEvents) {
        val (startStr, endStr) = when (event) {
            is DriveSummary -> event.startDate to event.endDate
            is ChargeSummary -> event.startDate to event.endDate
            else -> continue
        }
        val start = parseTimelineDate(startStr)
        if (prevEnd != null && start != null) {
            val gap = ChronoUnit.MINUTES.between(prevEnd, start)
            if (gap >= MIN_PARKING_GAP_MIN) {
                segments.add(TripTimelineSegment.Parking(durationMin = gap.toInt()))
            }
        }
        when (event) {
            is DriveSummary -> {
                driveIdx++
                segments.add(
                    TripTimelineSegment.Drive(
                        durationMin = event.durationMin,
                        index = driveIdx,
                        distanceKm = event.distance,
                        driveId = event.driveId
                    )
                )
            }
            is ChargeSummary -> {
                chargeIdx++
                segments.add(
                    TripTimelineSegment.Charge(
                        durationMin = event.durationMin,
                        index = chargeIdx,
                        energyKwh = event.energyAdded,
                        isDc = event.chargeId in dcChargeIds,
                        chargeId = event.chargeId
                    )
                )
            }
        }
        prevEnd = parseTimelineDate(endStr)
    }
    return segments
}

private fun parseTimelineDate(dateStr: String): LocalDateTime? =
    parseIsoDateTime(dateStr)
