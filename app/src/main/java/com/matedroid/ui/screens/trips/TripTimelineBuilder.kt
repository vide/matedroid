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

/** A drive or charge leg of a trip, in chronological order. */
sealed interface TripEvent {
    val startDate: String
    val endDate: String

    data class Drive(val drive: DriveSummary) : TripEvent {
        override val startDate: String get() = drive.startDate
        override val endDate: String get() = drive.endDate
    }

    data class Charge(val charge: ChargeSummary) : TripEvent {
        override val startDate: String get() = charge.startDate
        override val endDate: String get() = charge.endDate
    }
}

/**
 * Merge a trip's drives and charges into one chronological event list, applying the
 * short-entry filter unless [showShort] is set (mirrors the `showShortDrivesCharges`
 * setting — see [com.matedroid.domain.ShortEntryFilter] for the shared rule).
 *
 * Shared by the timeline builder and the trip detail leg list so both always agree on
 * which legs exist and in what order.
 */
fun mergeTripEvents(trip: Trip, showShort: Boolean): List<TripEvent> {
    val drives = if (showShort) trip.drives else trip.drives.filter { it.isSignificant() }
    val charges = if (showShort) trip.charges else trip.charges.filter { it.isSignificant() }
    return buildList<TripEvent> {
        drives.forEach { add(TripEvent.Drive(it)) }
        charges.forEach { add(TripEvent.Charge(it)) }
    }.sortedBy { it.startDate }
}

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
    val segments = mutableListOf<TripTimelineSegment>()
    var driveIdx = 0
    var chargeIdx = 0
    var prevEnd: LocalDateTime? = null

    for (event in mergeTripEvents(trip, showShort)) {
        val start = parseTimelineDate(event.startDate)
        if (prevEnd != null && start != null) {
            val gap = ChronoUnit.MINUTES.between(prevEnd, start)
            if (gap >= MIN_PARKING_GAP_MIN) {
                segments.add(TripTimelineSegment.Parking(durationMin = gap.toInt()))
            }
        }
        when (event) {
            is TripEvent.Drive -> {
                driveIdx++
                segments.add(
                    TripTimelineSegment.Drive(
                        durationMin = event.drive.durationMin,
                        index = driveIdx,
                        distanceKm = event.drive.distance,
                        driveId = event.drive.driveId
                    )
                )
            }
            is TripEvent.Charge -> {
                chargeIdx++
                segments.add(
                    TripTimelineSegment.Charge(
                        durationMin = event.charge.durationMin,
                        index = chargeIdx,
                        energyKwh = event.charge.energyAdded,
                        isDc = event.charge.chargeId in dcChargeIds,
                        chargeId = event.charge.chargeId
                    )
                )
            }
        }
        prevEnd = parseTimelineDate(event.endDate)
    }
    return segments
}

private fun parseTimelineDate(dateStr: String): LocalDateTime? =
    parseIsoDateTime(dateStr)
