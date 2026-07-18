package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostAnalyticsCalculatorTest {

    @Test
    fun `empty charge list yields empty metrics`() {
        val metrics = CostAnalyticsCalculator.calculate(emptyList(), totalDistance = 0.0)
        assertEquals(0, metrics.chargeCount)
        assertEquals(TripCostCoverage.None, metrics.costCoverage)
        assertNull(metrics.totalKnownCost)
        assertNull(metrics.costPer100Distance)
        assertNull(metrics.avgCostPerKwh)
    }

    @Test
    fun `all charges priced yields complete coverage and cost per 100`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 30.0, cost = 12.0),
                charge(id = 2, energy = 20.0, cost = 8.0),
            ),
            totalDistance = 500.0,
        )

        assertEquals(2, metrics.chargeCount)
        assertEquals(2, metrics.pricedCount)
        assertEquals(0, metrics.missingCostCount)
        assertEquals(0, metrics.zeroCostCount)
        assertEquals(20.0, metrics.totalKnownCost ?: 0.0, 0.001)
        assertEquals(50.0, metrics.energyAddedTotal, 0.001)
        assertEquals(50.0, metrics.energyAddedPriced, 0.001)
        assertEquals(0.4, metrics.avgCostPerKwh ?: 0.0, 0.001)
        assertEquals(4.0, metrics.costPer100Distance ?: 0.0, 0.001)
        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
    }

    @Test
    fun `partial coverage exposes total but withholds cost per 100`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 30.0, cost = 15.0),
                charge(id = 2, energy = 20.0, cost = null),
            ),
            totalDistance = 400.0,
        )

        assertEquals(TripCostCoverage.Partial, metrics.costCoverage)
        assertEquals(2, metrics.chargeCount)
        assertEquals(1, metrics.pricedCount)
        assertEquals(1, metrics.missingCostCount)
        assertEquals(15.0, metrics.totalKnownCost ?: 0.0, 0.001)
        // avg cost/kWh uses only priced energy so it stays honest even with gaps.
        assertEquals(0.5, metrics.avgCostPerKwh ?: 0.0, 0.001)
        assertNull(metrics.costPer100Distance)
    }

    @Test
    fun `no priced charges yields missing coverage`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 10.0, cost = null),
                charge(id = 2, energy = 25.0, cost = null),
            ),
            totalDistance = 300.0,
        )

        assertEquals(TripCostCoverage.None, metrics.costCoverage)
        assertEquals(0, metrics.pricedCount)
        assertEquals(2, metrics.missingCostCount)
        assertNull(metrics.totalKnownCost)
        assertNull(metrics.avgCostPerKwh)
        assertNull(metrics.costPer100Distance)
    }

    @Test
    fun `zero-cost charges count as priced and contribute to complete coverage`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 20.0, cost = 0.0),
                charge(id = 2, energy = 15.0, cost = 5.0),
            ),
            totalDistance = 200.0,
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertEquals(2, metrics.pricedCount)
        assertEquals(1, metrics.zeroCostCount)
        assertEquals(0, metrics.missingCostCount)
        assertEquals(5.0, metrics.totalKnownCost ?: -1.0, 0.001)
        // Average includes free-charging kWh in the denominator: 5 / 35 ≈ 0.143.
        assertEquals(5.0 / 35.0, metrics.avgCostPerKwh ?: 0.0, 0.001)
        assertEquals(2.5, metrics.costPer100Distance ?: 0.0, 0.001)
    }

    @Test
    fun `all charges free still count as complete but avg cost is zero`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(charge(id = 1, energy = 20.0, cost = 0.0)),
            totalDistance = 150.0,
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertEquals(0.0, metrics.totalKnownCost ?: -1.0, 0.001)
        assertEquals(0.0, metrics.avgCostPerKwh ?: -1.0, 0.001)
        assertEquals(0.0, metrics.costPer100Distance ?: -1.0, 0.001)
    }

    @Test
    fun `no distance driven yields null cost per 100 even with complete coverage`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(charge(id = 1, energy = 10.0, cost = 4.0)),
            totalDistance = 0.0,
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertNull(metrics.costPer100Distance)
        assertEquals(4.0, metrics.totalKnownCost ?: 0.0, 0.001)
    }

    // ============= Utility-rate estimation =============

    @Test
    fun `mixed recorded and estimated charges reach complete with usesEstimates true`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 30.0, cost = 15.0),   // recorded
                charge(id = 2, energy = 20.0, cost = null),   // AC, estimated at 0.10
            ),
            totalDistance = 500.0,
            rates = UtilityRates(homePerKwh = 0.10),
            dcChargeIds = emptySet(),
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertEquals(true, metrics.usesEstimates)
        assertEquals(1, metrics.recordedChargeCount)
        assertEquals(1, metrics.estimatedChargeCount)
        assertEquals(0, metrics.missingCostCount)
        assertEquals(15.0, metrics.totalKnownCost ?: 0.0, 0.001)
        assertEquals(2.0, metrics.totalEstimatedCost ?: 0.0, 0.001)
        assertEquals(17.0, metrics.totalEffectiveCost ?: 0.0, 0.001)
        // Cost/100 uses the effective total: 17 * 100 / 500 = 3.4
        assertEquals(3.4, metrics.costPer100Distance ?: 0.0, 0.001)
        // avg cost/kWh still comes from recorded only: 15 / 30 = 0.5
        assertEquals(0.5, metrics.avgCostPerKwh ?: 0.0, 0.001)
    }

    @Test
    fun `dc charges use dc rate over home rate`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 30.0, cost = null),   // AC → 0.10
                charge(id = 2, energy = 50.0, cost = null),   // DC → 0.40
            ),
            totalDistance = 400.0,
            rates = UtilityRates(homePerKwh = 0.10, dcPerKwh = 0.40),
            dcChargeIds = setOf(2),
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertEquals(true, metrics.usesEstimates)
        assertEquals(0, metrics.recordedChargeCount)
        assertEquals(2, metrics.estimatedChargeCount)
        assertNull(metrics.totalKnownCost)
        // 30 * 0.10 + 50 * 0.40 = 3 + 20 = 23
        assertEquals(23.0, metrics.totalEstimatedCost ?: 0.0, 0.001)
        assertEquals(23.0, metrics.totalEffectiveCost ?: 0.0, 0.001)
        // avg cost/kWh needs recorded data; here there is none.
        assertNull(metrics.avgCostPerKwh)
        // Cost/100 = 23 * 100 / 400 = 5.75
        assertEquals(5.75, metrics.costPer100Distance ?: 0.0, 0.001)
    }

    @Test
    fun `dc charge with no dc rate falls back to home rate`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(charge(id = 1, energy = 25.0, cost = null)),
            totalDistance = 200.0,
            rates = UtilityRates(homePerKwh = 0.20),
            dcChargeIds = setOf(1),
        )

        assertEquals(TripCostCoverage.Complete, metrics.costCoverage)
        assertEquals(5.0, metrics.totalEffectiveCost ?: 0.0, 0.001)
        assertEquals(1, metrics.estimatedChargeCount)
    }

    @Test
    fun `no rate configured leaves null-cost charges as missing`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(
                charge(id = 1, energy = 10.0, cost = 4.0),
                charge(id = 2, energy = 20.0, cost = null),
            ),
            totalDistance = 100.0,
            rates = UtilityRates(),
            dcChargeIds = emptySet(),
        )

        // Behaviour must match the recorded-only overload.
        assertEquals(TripCostCoverage.Partial, metrics.costCoverage)
        assertEquals(false, metrics.usesEstimates)
        assertEquals(1, metrics.missingCostCount)
        assertNull(metrics.totalEstimatedCost)
        assertEquals(4.0, metrics.totalEffectiveCost ?: 0.0, 0.001)
        // Partial coverage still hides cost/100.
        assertNull(metrics.costPer100Distance)
    }

    @Test
    fun `recorded zero cost is preserved even when a rate is configured`() {
        val metrics = CostAnalyticsCalculator.calculate(
            charges = listOf(charge(id = 1, energy = 20.0, cost = 0.0)),
            totalDistance = 100.0,
            rates = UtilityRates(homePerKwh = 0.30),
            dcChargeIds = emptySet(),
        )

        // The free session must not be overwritten by an estimate.
        assertEquals(false, metrics.usesEstimates)
        assertEquals(0.0, metrics.totalEffectiveCost ?: -1.0, 0.001)
        assertEquals(1, metrics.recordedChargeCount)
        assertEquals(0, metrics.estimatedChargeCount)
    }

    private fun charge(id: Int, energy: Double, cost: Double?): ChargeSummary =
        ChargeSummary(
            chargeId = id,
            carId = 1,
            startDate = "2026-07-01T10:00:00",
            endDate = "2026-07-01T10:30:00",
            durationMin = 30,
            address = "Charger $id",
            latitude = 0.0,
            longitude = 0.0,
            energyAdded = energy,
            energyUsed = null,
            cost = cost,
            startBatteryLevel = 30,
            endBatteryLevel = 80,
            outsideTempAvg = 20.0,
            odometer = 10_000.0,
        )
}
