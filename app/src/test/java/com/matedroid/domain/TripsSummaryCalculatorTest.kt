package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripsSummaryCalculatorTest {

    @Test
    fun `calculate aggregates trip energy efficiency and complete costs`() {
        val summary = TripsSummaryCalculator.calculate(
            listOf(
                trip(id = 1, distance = 300.0, energyConsumed = 45.0, chargeCosts = listOf(12.0)),
                trip(id = 2, distance = 200.0, energyConsumed = 35.0, chargeCosts = listOf(8.0)),
            )
        )

        assertEquals(2, summary.tripCount)
        assertEquals(500.0, summary.totalDistance, 0.001)
        assertEquals(480, summary.totalDrivingMinutes)
        assertEquals(80.0, summary.totalEnergyConsumed, 0.001)
        assertEquals(60.0, summary.totalEnergyCharged, 0.001)
        assertEquals(160.0, summary.averageEfficiency ?: 0.0, 0.001)
        assertEquals(20.0, summary.totalChargeCost ?: 0.0, 0.001)
        assertEquals(4.0, summary.costPer100Distance ?: 0.0, 0.001)
        assertEquals(2, summary.pricedChargeCount)
        assertEquals(2, summary.chargeCount)
        assertEquals(TripCostCoverage.Complete, summary.costCoverage)
    }

    @Test
    fun `calculate exposes partial cost coverage without inventing a complete rate`() {
        val summary = TripsSummaryCalculator.calculate(
            listOf(
                trip(
                    id = 1,
                    distance = 400.0,
                    energyConsumed = 64.0,
                    chargeCosts = listOf(10.0, null),
                )
            )
        )

        assertEquals(10.0, summary.totalChargeCost ?: 0.0, 0.001)
        assertNull(summary.costPer100Distance)
        assertEquals(1, summary.pricedChargeCount)
        assertEquals(2, summary.chargeCount)
        assertEquals(TripCostCoverage.Partial, summary.costCoverage)
    }

    @Test
    fun `calculate leaves costs unavailable when no charge has a price`() {
        val summary = TripsSummaryCalculator.calculate(
            listOf(
                trip(id = 1, distance = 300.0, energyConsumed = 45.0, chargeCosts = listOf(null))
            )
        )

        assertNull(summary.totalChargeCost)
        assertNull(summary.costPer100Distance)
        assertEquals(TripCostCoverage.None, summary.costCoverage)
    }

    @Test
    fun `calculate treats zero-cost charges as priced and complete`() {
        val summary = TripsSummaryCalculator.calculate(
            listOf(
                trip(id = 1, distance = 200.0, energyConsumed = 30.0, chargeCosts = listOf(0.0))
            )
        )

        assertEquals(0.0, summary.totalChargeCost ?: -1.0, 0.001)
        assertEquals(0.0, summary.costPer100Distance ?: -1.0, 0.001)
        assertEquals(1, summary.pricedChargeCount)
        assertEquals(1, summary.chargeCount)
        assertEquals(TripCostCoverage.Complete, summary.costCoverage)
    }

    @Test
    fun `calculate returns empty metrics for an empty range`() {
        assertEquals(TripsSummaryMetrics(), TripsSummaryCalculator.calculate(emptyList()))
    }

    @Test
    fun `estimated costs fill in null-cost charges when rates are configured`() {
        val summary = TripsSummaryCalculator.calculate(
            trips = listOf(
                trip(
                    id = 1,
                    distance = 400.0,
                    energyConsumed = 60.0,
                    chargeCosts = listOf(10.0, null),
                )
            ),
            rates = UtilityRates(homePerKwh = 0.20),
            dcChargeIds = emptySet(),
        )

        assertEquals(TripCostCoverage.Complete, summary.costCoverage)
        assertEquals(true, summary.usesEstimates)
        assertEquals(10.0, summary.totalChargeCost ?: 0.0, 0.001)
        // 30 kWh * 0.20 = 6.0 estimated
        assertEquals(6.0, summary.totalEstimatedCost ?: 0.0, 0.001)
        assertEquals(16.0, summary.totalEffectiveCost ?: 0.0, 0.001)
        // Cost/100 uses effective total: 16 * 100 / 400 = 4.0
        assertEquals(4.0, summary.costPer100Distance ?: 0.0, 0.001)
        assertEquals(1, summary.estimatedChargeCount)
        assertEquals(1, summary.recordedChargeCount)
        assertEquals(0, summary.missingChargeCount)
    }

    @Test
    fun `dc charge ids drive dc rate selection`() {
        val summary = TripsSummaryCalculator.calculate(
            trips = listOf(
                trip(
                    id = 1,
                    distance = 300.0,
                    energyConsumed = 45.0,
                    chargeCosts = listOf(null),
                )
            ),
            rates = UtilityRates(homePerKwh = 0.10, dcPerKwh = 0.50),
            // trip helper builds chargeId = id*10 + index → 10 here.
            dcChargeIds = setOf(10),
        )

        // 30 kWh added * 0.50 DC rate = 15.0
        assertEquals(15.0, summary.totalEffectiveCost ?: 0.0, 0.001)
        assertEquals(1, summary.estimatedChargeCount)
    }

    private fun trip(
        id: Int,
        distance: Double,
        energyConsumed: Double,
        chargeCosts: List<Double?>,
    ): Trip {
        val drive = DriveSummary(
            driveId = id,
            carId = 1,
            startDate = "2026-07-0${id}T08:00:00",
            endDate = "2026-07-0${id}T12:00:00",
            durationMin = 240,
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
            efficiency = energyConsumed * 1000.0 / distance,
        )
        val charges = chargeCosts.mapIndexed { index, cost ->
            ChargeSummary(
                chargeId = id * 10 + index,
                carId = 1,
                startDate = "2026-07-0${id}T12:10:00",
                endDate = "2026-07-0${id}T12:40:00",
                durationMin = 30,
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
