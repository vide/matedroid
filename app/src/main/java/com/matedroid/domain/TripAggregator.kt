package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.Trip
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Assembles a Trip domain object from its constituent drive and charge legs.
 *
 * Shared by TripDetector (for newly detected trips) and TripRepository (for
 * rebuilding persisted SavedTrips from their leg references). The aggregator
 * itself enforces no detection rules — callers decide whether a given leg set
 * is eligible (the detector applies the 300 km / 2-drive / 1-charge thresholds).
 */
object TripAggregator {

    /**
     * Build a Trip from chronologically-ordered legs.
     * Returns null if [drives] is empty (a Trip's start/end metadata comes from the first/last drive).
     * [name] is carried through to [Trip.name] so saved trips' custom names reach the UI.
     */
    fun buildTrip(
        drives: List<DriveSummary>,
        charges: List<ChargeSummary>,
        name: String? = null
    ): Trip? {
        if (drives.isEmpty()) return null

        val totalDistance = drives.sumOf { it.distance }
        val totalDrivingMin = drives.sumOf { it.durationMin }
        val firstStart = parseDateTime(drives.first().startDate)
        val lastEnd = parseDateTime(drives.last().endDate)
        val totalMin = if (firstStart != null && lastEnd != null) {
            ChronoUnit.MINUTES.between(firstStart, lastEnd).toInt()
        } else totalDrivingMin
        val totalEnergyConsumed = drives.mapNotNull { it.energyConsumed }.sum()
        val totalEnergyCharged = charges.sumOf { it.energyAdded }
        val costs = charges.mapNotNull { it.cost }
        val totalCost = if (costs.isNotEmpty()) costs.sum() else null
        val maxSpeed = drives.maxOf { it.speedMax }
        val avgEfficiency = if (totalDistance > 0) {
            (totalEnergyConsumed * 1000.0) / totalDistance
        } else null

        return Trip(
            drives = drives.toList(),
            charges = charges.toList(),
            totalDistance = totalDistance,
            totalDrivingDurationMin = totalDrivingMin,
            totalDurationMin = totalMin,
            totalEnergyConsumed = totalEnergyConsumed,
            totalEnergyCharged = totalEnergyCharged,
            totalChargeCost = totalCost,
            avgEfficiency = avgEfficiency,
            maxSpeed = maxSpeed,
            startAddress = drives.first().startAddress,
            endAddress = drives.last().endAddress,
            startDate = drives.first().startDate,
            endDate = drives.last().endDate,
            startBatteryLevel = drives.first().startBatteryLevel,
            endBatteryLevel = drives.last().endBatteryLevel,
            name = name
        )
    }

    private fun parseDateTime(dateStr: String): LocalDateTime? {
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
}
