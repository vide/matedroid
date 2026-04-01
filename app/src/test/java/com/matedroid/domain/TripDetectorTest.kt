package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetectorTest {

    private val detector = TripDetector()

    private fun drive(
        id: Int,
        startDate: String,
        endDate: String,
        distance: Double = 200.0,
        durationMin: Int = 120,
        startAddress: String = "Start $id",
        endAddress: String = "End $id"
    ) = DriveSummary(
        driveId = id,
        carId = 1,
        startDate = startDate,
        endDate = endDate,
        durationMin = durationMin,
        startAddress = startAddress,
        endAddress = endAddress,
        distance = distance,
        speedMax = 130,
        speedAvg = 100,
        powerMax = 200,
        powerMin = -60,
        startBatteryLevel = 80,
        endBatteryLevel = 40,
        outsideTempAvg = 20.0,
        insideTempAvg = 22.0,
        energyConsumed = 30.0,
        efficiency = 150.0
    )

    private fun charge(
        id: Int,
        startDate: String,
        endDate: String,
        energyAdded: Double = 40.0
    ) = ChargeSummary(
        chargeId = id,
        carId = 1,
        startDate = startDate,
        endDate = endDate,
        durationMin = 30,
        address = "Charger $id",
        latitude = 48.0,
        longitude = 2.0,
        energyAdded = energyAdded,
        energyUsed = null,
        cost = null,
        startBatteryLevel = 20,
        endBatteryLevel = 80,
        outsideTempAvg = null,
        odometer = 50000.0
    )

    @Test
    fun `no trips from single drive`() {
        val drives = listOf(drive(1, "2024-01-01T08:00:00Z", "2024-01-01T10:00:00Z"))
        val result = detector.detectTrips(drives, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no trips without DC charge`() {
        val drives = listOf(
            drive(1, "2024-01-01T08:00:00Z", "2024-01-01T10:00:00Z"),
            drive(2, "2024-01-01T10:30:00Z", "2024-01-01T12:00:00Z")
        )
        val result = detector.detectTrips(drives, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `basic trip - two drives with one DC charge`() {
        val drives = listOf(
            drive(1, "2024-01-01T08:00:00Z", "2024-01-01T10:00:00Z"),
            drive(2, "2024-01-01T10:45:00Z", "2024-01-01T13:00:00Z")
        )
        val charges = listOf(
            charge(1, "2024-01-01T10:05:00Z", "2024-01-01T10:35:00Z")
        )
        val result = detector.detectTrips(drives, charges)
        assertEquals(1, result.size)
        assertEquals(2, result[0].drives.size)
        assertEquals(1, result[0].charges.size)
    }

    @Test
    fun `trip under 300km is excluded`() {
        val drives = listOf(
            drive(1, "2024-01-01T08:00:00Z", "2024-01-01T09:00:00Z", distance = 100.0),
            drive(2, "2024-01-01T09:45:00Z", "2024-01-01T10:30:00Z", distance = 100.0)
        )
        val charges = listOf(
            charge(1, "2024-01-01T09:05:00Z", "2024-01-01T09:35:00Z")
        )
        val result = detector.detectTrips(drives, charges)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `three-leg trip with two DC charges`() {
        val drives = listOf(
            drive(1, "2024-01-01T06:00:00Z", "2024-01-01T08:00:00Z", distance = 200.0),
            drive(2, "2024-01-01T08:45:00Z", "2024-01-01T10:30:00Z", distance = 200.0),
            drive(3, "2024-01-01T11:15:00Z", "2024-01-01T13:00:00Z", distance = 200.0)
        )
        val charges = listOf(
            charge(1, "2024-01-01T08:05:00Z", "2024-01-01T08:35:00Z"),
            charge(2, "2024-01-01T10:35:00Z", "2024-01-01T11:05:00Z")
        )
        val result = detector.detectTrips(drives, charges)
        assertEquals(1, result.size)
        assertEquals(3, result[0].drives.size)
        assertEquals(2, result[0].charges.size)
        assertEquals(600.0, result[0].totalDistance, 0.1)
    }

    @Test
    fun `gap too large breaks trip`() {
        val drives = listOf(
            drive(1, "2024-01-01T08:00:00Z", "2024-01-01T10:00:00Z"),
            drive(2, "2024-01-01T14:00:00Z", "2024-01-01T16:00:00Z")
        )
        val charges = listOf(
            charge(1, "2024-01-01T10:05:00Z", "2024-01-01T10:35:00Z")
        )
        val result = detector.detectTrips(drives, charges)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `micro drives are filtered out`() {
        val drives = listOf(
            drive(1, "2024-01-01T08:00:00Z", "2024-01-01T10:00:00Z", distance = 200.0),
            drive(2, "2024-01-01T10:02:00Z", "2024-01-01T10:04:00Z", distance = 0.5),
            drive(3, "2024-01-01T10:45:00Z", "2024-01-01T13:00:00Z", distance = 200.0)
        )
        val charges = listOf(
            charge(1, "2024-01-01T10:05:00Z", "2024-01-01T10:35:00Z")
        )
        val result = detector.detectTrips(drives, charges)
        assertEquals(1, result.size)
        assertEquals(2, result[0].drives.size)
    }
}
