package com.matedroid.domain

import com.matedroid.data.api.models.ChargeBatteryDetails
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.DriveOdometerDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SinceLastChargeTest {

    private fun charge(
        id: Int,
        endDate: String? = "2026-08-16T22:30:00+02:00",
        energyAdded: Double? = 18.4,
        endLevel: Int? = 80
    ) = ChargeData(
        chargeId = id,
        endDate = endDate,
        chargeEnergyAdded = energyAdded,
        batteryDetails = ChargeBatteryDetails(endBatteryLevel = endLevel)
    )

    private fun drive(distance: Double?, energy: Double?) = DriveData(
        driveId = 1,
        odometerDetails = DriveOdometerDetails(distance = distance),
        energyConsumedNet = energy
    )

    @Test
    fun `anchor is the newest charge that added energy`() {
        val charges = listOf(
            charge(id = 3, energyAdded = 0.0), // plug-in blip, must be skipped
            charge(id = 2, energyAdded = 18.4),
            charge(id = 1, energyAdded = 25.0)
        )
        assertEquals(2, SinceLastChargeRepository.findAnchorCharge(charges)?.chargeId)
    }

    @Test
    fun `charges below the minimum added energy do not reset the cycle`() {
        val charges = listOf(charge(id = 2, energyAdded = 0.05), charge(id = 1, energyAdded = 12.0))
        assertEquals(1, SinceLastChargeRepository.findAnchorCharge(charges)?.chargeId)
    }

    @Test
    fun `a charge without an end date is never the anchor`() {
        val charges = listOf(charge(id = 2, endDate = null), charge(id = 1))
        assertEquals(1, SinceLastChargeRepository.findAnchorCharge(charges)?.chargeId)
    }

    @Test
    fun `no anchor when nothing added energy`() {
        assertNull(SinceLastChargeRepository.findAnchorCharge(listOf(charge(id = 1, energyAdded = 0.0))))
    }

    @Test
    fun `aggregates distance, energy and drive count`() {
        val stats = SinceLastChargeRepository.aggregate(
            anchor = charge(id = 1),
            drives = listOf(drive(100.0, 15.0), drive(50.0, 7.5), drive(null, null))
        )
        assertEquals(150.0, stats.distance, 0.001)
        assertEquals(22.5, stats.energyConsumedKwh, 0.001)
        assertEquals(3, stats.driveCount)
        assertEquals(80, stats.chargeEndBatteryLevel)
        assertEquals(150.0, stats.avgConsumptionWh!!, 0.001)
    }

    @Test
    fun `no average consumption without distance or energy`() {
        val zeroDistance = SinceLastChargeRepository.aggregate(charge(id = 1), listOf(drive(0.0, 5.0)))
        assertNull(zeroDistance.avgConsumptionWh)
        val noDrives = SinceLastChargeRepository.aggregate(charge(id = 1), emptyList())
        assertNull(noDrives.avgConsumptionWh)
        assertEquals(0, noDrives.driveCount)
    }

    @Test
    fun `offset dates convert to UTC Z form for the query parameter`() {
        assertEquals(
            "2026-08-16T20:30:00Z",
            SinceLastChargeRepository.toUtcRfc3339("2026-08-16T22:30:00+02:00")
        )
        assertEquals(
            "2026-08-16T20:30:00Z",
            SinceLastChargeRepository.toUtcRfc3339("2026-08-16T20:30:00Z")
        )
        assertNull(SinceLastChargeRepository.toUtcRfc3339("not a date"))
    }
}
