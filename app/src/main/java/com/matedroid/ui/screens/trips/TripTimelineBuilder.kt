package com.matedroid.ui.screens.trips

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.Trip
import com.matedroid.ui.components.TripTimelineSegment
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private const val MIN_PARKING_GAP_MIN = 5L

/**
 * Build a chronological list of TripTimelineSegments from a Trip.
 * Inserts Parking segments for any gap >= MIN_PARKING_GAP_MIN between consecutive legs.
 *
 * [dcChargeIds] is the set of charge IDs that are DC fast chargers, used to color the
 * charge segments correctly (orange for DC, green for AC). For legs not in the set, AC is assumed.
 */
fun buildTimelineSegments(
    trip: Trip,
    dcChargeIds: Set<Int> = emptySet()
): List<TripTimelineSegment> {
    val allEvents = mutableListOf<Pair<String, Any>>()
    trip.drives.forEach { allEvents.add(it.startDate to it) }
    trip.charges.forEach { allEvents.add(it.startDate to it) }
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
                        distanceKm = event.distance
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
                        isDc = event.chargeId in dcChargeIds
                    )
                )
            }
        }
        prevEnd = parseTimelineDate(endStr)
    }
    return segments
}

private fun parseTimelineDate(dateStr: String): LocalDateTime? {
    return try {
        OffsetDateTime.parse(dateStr).toLocalDateTime()
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        } catch (e2: Exception) {
            null
        }
    }
}
