package com.matedroid.ui.screens.battery

import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.BatteryHealth
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.ChargingDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsTest {

    private val health = BatteryHealth(
        maxRange = 533.0,
        currentRange = 507.1,
        maxCapacity = 78.4,
        currentCapacity = 74.6,
        ratedEfficiency = 13.7,
        batteryHealthPercentage = 95.2
    )

    private fun stats(
        health: BatteryHealth? = this.health,
        status: CarStatus? = null,
        fallbackEfficiency: Double = 0.0
    ) = computeBatteryStats(health, status, fallbackEfficiency, defaultCapacityKwh = 82.0)

    @Test
    fun apiEfficiency_isKwhPer100Units_scaledToWhPerUnit() {
        // The live TeslamateAPI returns 13.7 for a 137 Wh/km car.
        assertEquals(137.0, stats()!!.ratedEfficiencyWhPerUnit, 0.001)
    }

    @Test
    fun fallbackEfficiency_isKwhPerUnit_scaledToWhPerUnit() {
        val s = stats(health = health.copy(ratedEfficiency = null), fallbackEfficiency = 0.137)
        assertEquals(137.0, s!!.ratedEfficiencyWhPerUnit, 0.001)
    }

    @Test
    fun noEfficiencyAnywhere_usesDefault() {
        val s = stats(health = health.copy(ratedEfficiency = null), fallbackEfficiency = 0.0)
        assertEquals(150.0, s!!.ratedEfficiencyWhPerUnit, 0.001)
    }

    @Test
    fun healthLossAndRange_comeFromTheApiFigures() {
        val s = stats()!!
        assertEquals(95.2, s.healthPercent, 0.001)
        assertEquals(78.4, s.originalCapacity, 0.001)
        assertEquals(74.6, s.currentCapacity, 0.001)
        assertEquals(3.8, s.lossKwh, 0.01)
        assertEquals(533.0, s.maxRangeNew, 0.001)
        assertEquals(507.1, s.maxRangeNow, 0.001)
        assertEquals(25.9, s.rangeLoss, 0.01)
    }

    @Test
    fun missingCarStatus_zeroesTheCurrentFigures() {
        val s = stats(status = null)!!
        assertEquals(0, s.batteryLevel)
        assertEquals(0, s.usableBatteryLevel)
        assertEquals(0.0, s.estimatedRange, 0.0)
        assertEquals(0.0, s.ratedRange, 0.0)
        assertEquals(0.0, s.idealRange, 0.0)
    }

    @Test
    fun carStatus_feedsTheCurrentFigures() {
        val status = CarStatus(
            batteryDetails = BatteryDetails(
                batteryLevel = 80,
                usableBatteryLevel = 78,
                estBatteryRange = 350.0,
                ratedBatteryRange = 405.6,
                idealBatteryRange = 410.0
            )
        )
        val s = stats(status = status)
        assertNotNull(s)
        assertEquals(80, s!!.batteryLevel)
        assertEquals(78, s.usableBatteryLevel)
        assertEquals(350.0, s.estimatedRange, 0.001)
        assertEquals(405.6, s.ratedRange, 0.001)
        assertEquals(410.0, s.idealRange, 0.001)
    }

    @Test
    fun missingCarStatus_hasNoChargingState() {
        val s = stats(status = null)!!
        assertNull(s.chargeLimitSoc)
        assertFalse(s.isPluggedIn)
        assertFalse(s.isCharging)
    }

    @Test
    fun chargingCar_reportsPlugLimitAndCharging() {
        val status = CarStatus(
            chargingDetails = ChargingDetails(
                pluggedIn = true,
                chargingState = "Charging",
                chargeLimitSoc = 90
            )
        )
        val s = stats(status = status)!!
        assertEquals(90, s.chargeLimitSoc)
        assertTrue(s.isPluggedIn)
        assertTrue(s.isCharging)
    }

    @Test
    fun pluggedInNotCharging_isPluggedInOnly() {
        val status = CarStatus(
            chargingDetails = ChargingDetails(pluggedIn = true, chargingState = "Stopped")
        )
        val s = stats(status = status)!!
        assertTrue(s.isPluggedIn)
        assertFalse(s.isCharging)
    }

    @Test
    fun missingHealth_yieldsNoStats() {
        assertNull(stats(health = null))
    }
}
