package com.matedroid.widget

import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarDetails
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.ClimateDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWidgetDisplayDataTest {

    private fun buildFullCarData() = CarData(
        carId = 1,
        name = "My Model Y",
        carDetails = CarDetails(model = "Y", trimBadging = "74D", vin = "VIN123", efficiency = 160.0),
        carExterior = CarExterior(exteriorColor = "DeepBlueMetallic", spoilerType = null, wheelType = "Gemini19"),
    )

    private fun buildFullStatus() = CarStatus(
        displayName = "My Model Y",
        state = "charging",
        stateSince = "2024-12-07T10:00:00Z",
        carStatus = CarStatusDetails(
            locked = true,
            sentryMode = false,
            windowsOpen = false,
            doorsOpen = false,
            trunkOpen = false,
            frunkOpen = false,
            isUserPresent = false
        ),
        climateDetails = ClimateDetails(
            isClimateOn = true,
            insideTemp = 21.5,
            outsideTemp = 12.0,
            isPreconditioning = false
        ),
        batteryDetails = BatteryDetails(
            batteryLevel = 75,
            usableBatteryLevel = 74,
            estBatteryRange = 390.0,
            ratedBatteryRange = 420.0,
            idealBatteryRange = 450.0
        ),
        chargingDetails = ChargingDetails(
            pluggedIn = true,
            chargingState = "Charging",
            chargeEnergyAdded = 12.3,
            chargeLimitSoc = 80,
            chargerActualCurrent = 32,
            chargerPhases = 2,   // => acPhases = 3
            chargerPower = 11,
            chargerVoltage = 230,
            chargeCurrentRequest = 32,
            chargeCurrentRequestMax = 32,
            timeToFullCharge = 1.25
        )
    )

    @Test
    fun fromCarStatus_mapsAllBatteryCardFields() {
        val car = buildFullCarData()
        val status = buildFullStatus()

        val data = CarWidgetDisplayData.from(car, status)

        assertEquals(1, data.carId)
        assertEquals("My Model Y", data.carName)
        assertEquals("DeepBlueMetallic", data.exteriorColor)
        assertEquals("Y", data.model)
        assertEquals("74D", data.trimBadging)
        assertEquals("Gemini19", data.wheelType)

        // State
        assertEquals("charging", data.state)
        assertEquals("2024-12-07T10:00:00Z", data.stateSince)
        assertTrue(data.isLocked)
        assertFalse(data.sentryModeActive)
        assertTrue(data.pluggedIn)

        // Climate
        assertEquals(12.0, data.outsideTemp)
        assertEquals(21.5, data.insideTemp)
        assertTrue(data.isClimateOn)

        // Battery
        assertEquals(75, data.batteryLevel)
        assertEquals(420.0, data.ratedBatteryRangeKm)
        assertEquals(80, data.chargeLimitSoc)

        // Charging
        assertTrue(data.isCharging)
        assertFalse(data.isDcCharging)     // chargerPhases = 2 → AC charging
        assertEquals(11, data.chargerPower)
        assertEquals(12.3, data.chargeEnergyAdded)
        assertEquals(1.25, data.timeToFullCharge)
        assertEquals(230, data.chargerVoltage)
        assertEquals(32, data.chargerActualCurrent)
        assertEquals(3, data.acPhases)     // chargerPhases 2 → 3-phase AC
    }

    @Test
    fun fromCarStatus_handlesNullsGracefully() {
        val car = CarData(carId = 2)
        val status = CarStatus()

        val data = CarWidgetDisplayData.from(car, status)

        // Should not throw; defaults to safe values
        assertEquals(2, data.carId)
        assertEquals("Tesla", data.carName)   // CarData.displayName fallback
        assertNull(data.exteriorColor)
        assertNull(data.model)
        assertNull(data.trimBadging)
        assertNull(data.wheelType)
        assertNull(data.state)
        assertNull(data.stateSince)
        assertFalse(data.isLocked)
        assertFalse(data.sentryModeActive)
        assertFalse(data.pluggedIn)
        assertNull(data.outsideTemp)
        assertNull(data.insideTemp)
        assertFalse(data.isClimateOn)
        assertEquals(0, data.batteryLevel)
        assertNull(data.ratedBatteryRangeKm)
        assertNull(data.chargeLimitSoc)
        assertFalse(data.isCharging)
        assertFalse(data.isDcCharging)
        assertNull(data.chargerPower)
        assertNull(data.chargeEnergyAdded)
        assertNull(data.timeToFullCharge)
        assertNull(data.chargerVoltage)
        assertNull(data.chargerActualCurrent)
        assertNull(data.acPhases)
    }

    @Test
    fun dcCharging_isDcCharging_whenPhasesNull() {
        val car = buildFullCarData()
        val status = buildFullStatus().copy(
            chargingDetails = buildFullStatus().chargingDetails!!.copy(
                chargerPhases = null   // DC charging: no phases
            )
        )

        val data = CarWidgetDisplayData.from(car, status)

        assertTrue(data.isDcCharging)
        assertNull(data.acPhases)
    }

    @Test
    fun acCharging_singlePhase_whenPhasesOne() {
        val car = buildFullCarData()
        val status = buildFullStatus().copy(
            chargingDetails = buildFullStatus().chargingDetails!!.copy(
                chargerPhases = 1
            )
        )

        val data = CarWidgetDisplayData.from(car, status)

        assertFalse(data.isDcCharging)
        assertEquals(1, data.acPhases)
    }
}
