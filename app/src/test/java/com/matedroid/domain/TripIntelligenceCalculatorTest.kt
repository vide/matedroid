package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripIntelligenceCalculatorTest {

    @Test
    fun `empty trips yield empty intelligence`() {
        val result = TripIntelligenceCalculator.calculate(emptyList())
        assertNull(result.mostEfficient)
        assertNull(result.leastEfficient)
        assertNull(result.cheapestPer100)
        assertNull(result.mostExpensivePer100)
        assertNull(result.longestDistance)
        assertNull(result.highestChargingOverhead)
        assertNull(result.highestChargingOverheadRatio)
        assertNull(result.energyGapKwh)
        assertNull(result.avgChargingMinutesPerTrip)
    }

    @Test
    fun `picks lowest and highest efficiency trips`() {
        val efficient = trip(id = 1, distance = 400.0, energyConsumed = 40.0, chargeCosts = listOf(10.0))
        val greedy = trip(id = 2, distance = 200.0, energyConsumed = 40.0, chargeCosts = listOf(10.0))

        val result = TripIntelligenceCalculator.calculate(listOf(efficient, greedy))

        assertEquals(efficient.startDate, result.mostEfficient?.startDate)
        assertEquals(greedy.startDate, result.leastEfficient?.startDate)
    }

    @Test
    fun `cheapest per 100 ignores trips with any unpriced charge`() {
        val fullyPricedCheap = trip(
            id = 1,
            distance = 400.0,
            energyConsumed = 60.0,
            chargeCosts = listOf(8.0, 4.0),
        )
        val fullyPricedExpensive = trip(
            id = 2,
            distance = 200.0,
            energyConsumed = 30.0,
            chargeCosts = listOf(20.0),
        )
        val partial = trip(
            id = 3,
            distance = 500.0,
            energyConsumed = 80.0,
            chargeCosts = listOf(1.0, null),
        )

        val result = TripIntelligenceCalculator.calculate(
            listOf(fullyPricedCheap, fullyPricedExpensive, partial)
        )

        assertEquals(fullyPricedCheap.startDate, result.cheapestPer100?.startDate)
        assertEquals(fullyPricedExpensive.startDate, result.mostExpensivePer100?.startDate)
    }

    @Test
    fun `cheapest per 100 nulls out when no trip is fully priced`() {
        val partial = trip(id = 1, distance = 500.0, energyConsumed = 60.0, chargeCosts = listOf(null))
        val result = TripIntelligenceCalculator.calculate(listOf(partial))

        assertNull(result.cheapestPer100)
        assertNull(result.mostExpensivePer100)
    }

    @Test
    fun `longest distance and charging overhead leader picked correctly`() {
        val short = trip(
            id = 1,
            distance = 100.0,
            energyConsumed = 20.0,
            chargeCosts = listOf(5.0),
            drivingMinutes = 60,
            chargeMinutes = 60,
        )
        val long = trip(
            id = 2,
            distance = 900.0,
            energyConsumed = 140.0,
            chargeCosts = listOf(30.0),
            drivingMinutes = 480,
            chargeMinutes = 30,
        )

        val result = TripIntelligenceCalculator.calculate(listOf(short, long))

        assertEquals(long.startDate, result.longestDistance?.startDate)
        assertEquals(short.startDate, result.highestChargingOverhead?.startDate)
        val ratio = result.highestChargingOverheadRatio ?: 0.0
        assertTrue("Expected ratio > 0.2 but was $ratio", ratio > 0.2)
    }

    @Test
    fun `energy gap and average charging minutes reflect the whole set`() {
        val a = trip(
            id = 1,
            distance = 300.0,
            energyConsumed = 50.0,
            chargeCosts = listOf(10.0),
            chargeMinutes = 40,
        )
        val b = trip(
            id = 2,
            distance = 200.0,
            energyConsumed = 30.0,
            chargeCosts = listOf(6.0),
            chargeMinutes = 20,
        )

        val result = TripIntelligenceCalculator.calculate(listOf(a, b))

        val expectedGap = (a.totalEnergyCharged + b.totalEnergyCharged) -
            (a.totalEnergyConsumed + b.totalEnergyConsumed)
        assertEquals(expectedGap, result.energyGapKwh ?: 0.0, 0.001)
        assertEquals(30.0, result.avgChargingMinutesPerTrip ?: 0.0, 0.001)
    }

    @Test
    fun `trips without charges are ignored for charging overhead but count elsewhere`() {
        val noCharges = trip(
            id = 1,
            distance = 250.0,
            energyConsumed = 40.0,
            chargeCosts = emptyList(),
        )

        val result = TripIntelligenceCalculator.calculate(listOf(noCharges))

        assertNotNull(result.longestDistance)
        assertNull(result.highestChargingOverhead)
        assertNull(result.highestChargingOverheadRatio)
        assertEquals(0.0, result.avgChargingMinutesPerTrip ?: -1.0, 0.001)
    }

    private fun trip(
        id: Int,
        distance: Double,
        energyConsumed: Double,
        chargeCosts: List<Double?>,
        drivingMinutes: Int = 240,
        chargeMinutes: Int = 30,
    ): Trip {
        val drive = DriveSummary(
            driveId = id,
            carId = 1,
            startDate = "2026-07-0${id}T08:00:00",
            endDate = "2026-07-0${id}T12:00:00",
            durationMin = drivingMinutes,
            startAddress = "Start",
            endAddress = "End",
            distance = distance,
            speedMax = 120,
            speedAvg = 80,
            powerMax = 150,
            powerMin = -50,
            startBatteryLevel = 90,
            endBatteryLevel = 20,
            outsideTempAvg = 20.0,
            insideTempAvg = 21.0,
            energyConsumed = energyConsumed,
            efficiency = if (distance > 0) energyConsumed * 1000.0 / distance else null,
        )
        val charges = chargeCosts.mapIndexed { index, cost ->
            ChargeSummary(
                chargeId = id * 10 + index,
                carId = 1,
                startDate = "2026-07-0${id}T12:10:00",
                endDate = "2026-07-0${id}T12:40:00",
                durationMin = chargeMinutes,
                address = "Charger",
                latitude = 0.0,
                longitude = 0.0,
                energyAdded = 30.0,
                energyUsed = null,
                cost = cost,
                startBatteryLevel = 20,
                endBatteryLevel = 70,
                outsideTempAvg = 20.0,
                odometer = 10_000.0,
            )
        }

        return TripAggregator.buildTrip(listOf(drive), charges)!!
    }
}
