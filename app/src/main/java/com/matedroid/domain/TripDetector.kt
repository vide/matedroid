package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.Trip
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects highway/road trips from drive and DC charge data.
 *
 * A trip is a sequence of drives connected by DC charge stops.
 * Micro-drives (< 1 km, e.g. parking maneuvers at charger locations) are
 * filtered out before detection to avoid breaking chains.
 *
 * Gap thresholds:
 * - drive → charge: max 15 min
 * - charge → drive: max 45 min
 * - drive → drive:  max 30 min (e.g. highway rest stop without charging)
 * - charge → charge: max 45 min (charger hop at same location)
 *
 * Minimum: 2 real drives + 1 DC charge.
 */
@Singleton
class TripDetector @Inject constructor() {

    companion object {
        private const val MICRO_DRIVE_THRESHOLD_KM = 1.0
        private const val MIN_TRIP_DISTANCE_KM = 300.0
        private const val MAX_DRIVE_TO_CHARGE_GAP_MIN = 15L
        private const val MAX_CHARGE_TO_DRIVE_GAP_MIN = 45L
        private const val MAX_DRIVE_TO_DRIVE_GAP_MIN = 30L
    }

    private sealed class Event(val startDate: String, val endDate: String) {
        class Drive(val drive: DriveSummary) : Event(drive.startDate, drive.endDate)
        class Charge(val charge: ChargeSummary) : Event(charge.startDate, charge.endDate)
    }

    fun detectTrips(
        drives: List<DriveSummary>,
        dcCharges: List<ChargeSummary>
    ): List<Trip> {
        val realDrives = drives.filter { it.distance >= MICRO_DRIVE_THRESHOLD_KM }

        val events = mutableListOf<Event>()
        realDrives.forEach { events.add(Event.Drive(it)) }
        dcCharges.forEach { events.add(Event.Charge(it)) }
        events.sortBy { parseDateTime(it.startDate) ?: LocalDateTime.MIN }

        val trips = mutableListOf<Trip>()
        var currentDrives = mutableListOf<DriveSummary>()
        var currentCharges = mutableListOf<ChargeSummary>()
        var lastEventEnd: LocalDateTime? = null
        var lastWasDrive = false

        for (event in events) {
            val eventStart = parseDateTime(event.startDate) ?: continue

            if (lastEventEnd == null) {
                if (event is Event.Drive) {
                    currentDrives.add(event.drive)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = true
                }
                continue
            }

            val gapMin = ChronoUnit.MINUTES.between(lastEventEnd, eventStart)

            when {
                // drive → charge: max 15min gap
                lastWasDrive && event is Event.Charge && gapMin <= MAX_DRIVE_TO_CHARGE_GAP_MIN -> {
                    currentCharges.add(event.charge)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = false
                }
                // charge → drive: max 45min gap
                !lastWasDrive && event is Event.Drive && gapMin <= MAX_CHARGE_TO_DRIVE_GAP_MIN -> {
                    currentDrives.add(event.drive)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = true
                }
                // drive → drive: max 30min gap (e.g. rest stop without charging)
                lastWasDrive && event is Event.Drive && gapMin <= MAX_DRIVE_TO_DRIVE_GAP_MIN -> {
                    currentDrives.add(event.drive)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = true
                }
                // charge → charge: max 45min gap (charger hop at same location)
                !lastWasDrive && event is Event.Charge && gapMin <= MAX_CHARGE_TO_DRIVE_GAP_MIN -> {
                    currentCharges.add(event.charge)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = false
                }
                else -> {
                    emitTrip(currentDrives, currentCharges, trips)
                    currentDrives = mutableListOf()
                    currentCharges = mutableListOf()
                    lastEventEnd = null
                    lastWasDrive = false
                    if (event is Event.Drive) {
                        currentDrives.add(event.drive)
                        lastEventEnd = parseDateTime(event.endDate)
                        lastWasDrive = true
                    }
                }
            }
        }

        emitTrip(currentDrives, currentCharges, trips)
        return trips
    }

    private fun emitTrip(
        drives: List<DriveSummary>,
        charges: List<ChargeSummary>,
        trips: MutableList<Trip>
    ) {
        if (drives.size < 2 || charges.isEmpty()) return

        val totalDistance = drives.sumOf { it.distance }
        if (totalDistance < MIN_TRIP_DISTANCE_KM) return
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

        trips.add(
            Trip(
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
                endBatteryLevel = drives.last().endBatteryLevel
            )
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
