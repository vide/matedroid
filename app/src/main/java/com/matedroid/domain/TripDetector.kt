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
 * A trip is: drive → DC charge → drive [→ DC charge → drive ...]
 * - Max 15min gap between drive end and charge start
 * - Max 45min gap between charge end and next drive start
 * - Minimum 2 drives + 1 DC charge to qualify
 */
@Singleton
class TripDetector @Inject constructor() {

    companion object {
        private const val MAX_DRIVE_TO_CHARGE_GAP_MIN = 15L
        private const val MAX_CHARGE_TO_DRIVE_GAP_MIN = 45L
    }

    private sealed class Event(val startDate: String, val endDate: String) {
        class Drive(val drive: DriveSummary) : Event(drive.startDate, drive.endDate)
        class Charge(val charge: ChargeSummary) : Event(charge.startDate, charge.endDate)
    }

    /**
     * Detect trips from chronologically sorted drives and DC-only charges.
     * Both lists must be sorted by startDate ASC.
     */
    fun detectTrips(
        drives: List<DriveSummary>,
        dcCharges: List<ChargeSummary>
    ): List<Trip> {
        // Merge into a single timeline sorted by startDate
        val events = mutableListOf<Event>()
        drives.forEach { events.add(Event.Drive(it)) }
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
                // charge → charge within 45min (supercharger hop)
                !lastWasDrive && event is Event.Charge && gapMin <= MAX_CHARGE_TO_DRIVE_GAP_MIN -> {
                    currentCharges.add(event.charge)
                    lastEventEnd = parseDateTime(event.endDate)
                    lastWasDrive = false
                }
                else -> {
                    // Gap too large or wrong sequence — finalize and restart
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
