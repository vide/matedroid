package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IceComparisonCalculatorTest {

    // ============== isConfigured ==============

    @Test
    fun `isConfigured is false when values are null`() {
        assertFalse(IceAssumptions().isConfigured)
        assertFalse(IceAssumptions(economyValue = 6.0).isConfigured)
        assertFalse(IceAssumptions(fuelPrice = 1.8).isConfigured)
    }

    @Test
    fun `isConfigured is false when values are not strictly positive`() {
        assertFalse(IceAssumptions(economyValue = 0.0, fuelPrice = 1.8).isConfigured)
        assertFalse(IceAssumptions(economyValue = -1.0, fuelPrice = 1.8).isConfigured)
        assertFalse(IceAssumptions(economyValue = 6.0, fuelPrice = 0.0).isConfigured)
        assertFalse(IceAssumptions(economyValue = 6.0, fuelPrice = -0.1).isConfigured)
        assertFalse(
            IceAssumptions(
                economyValue = Double.POSITIVE_INFINITY,
                fuelPrice = 1.8,
            ).isConfigured
        )
        assertFalse(
            IceAssumptions(
                economyValue = 6.0,
                fuelPrice = Double.NaN,
            ).isConfigured
        )
    }

    @Test
    fun `isConfigured is true when both values are strictly positive`() {
        assertTrue(
            IceAssumptions(
                economyValue = 6.5,
                economyIsMpg = false,
                fuelPrice = 1.75,
                fuelPriceIsPerGallon = false,
            ).isConfigured
        )
    }

    // ============== Metric path (L/100km + $/L, distance in km) ==============

    @Test
    fun `metric path uses liters and price per liter directly`() {
        val assumptions = IceAssumptions(
            economyValue = 7.0,
            economyIsMpg = false,
            fuelPrice = 1.80,
            fuelPriceIsPerGallon = false,
        )
        val result = IceComparisonCalculator.compare(
            distance = 500.0,
            distanceIsMiles = false,
            teslaCost = 25.0,
            assumptions = assumptions,
        )

        // 500 km * (7 / 100) = 35 L; 35 * 1.80 = 63.0
        assertEquals(35.0, result.iceVolumeUsed ?: 0.0, 0.0001)
        assertEquals(63.0, result.iceCost ?: 0.0, 0.0001)
        assertEquals(38.0, result.savings ?: 0.0, 0.0001)
        assertEquals(38.0 / 63.0 * 100.0, result.savingsPercent ?: 0.0, 0.0001)
        assertFalse(result.usedImperialVolume)
    }

    // ============== Imperial path (mpg + $/gal, distance in mi) ==============

    @Test
    fun `imperial path uses gallons and price per gallon directly`() {
        val assumptions = IceAssumptions(
            economyValue = 30.0,
            economyIsMpg = true,
            fuelPrice = 3.60,
            fuelPriceIsPerGallon = true,
        )
        val result = IceComparisonCalculator.compare(
            distance = 300.0,
            distanceIsMiles = true,
            teslaCost = 12.0,
            assumptions = assumptions,
        )

        // 300 mi / 30 mpg = 10 gal; 10 * 3.60 = 36.0
        assertEquals(10.0, result.iceVolumeUsed ?: 0.0, 0.0001)
        assertEquals(36.0, result.iceCost ?: 0.0, 0.0001)
        assertEquals(24.0, result.savings ?: 0.0, 0.0001)
        assertEquals(24.0 / 36.0 * 100.0, result.savingsPercent ?: 0.0, 0.0001)
        assertTrue(result.usedImperialVolume)
    }

    // ============== Mixed unit: assumptions in mpg / $ per gallon, distance km ==============

    @Test
    fun `mpg economy is converted to L per 100 km when distance is km`() {
        // 30 mpg ≈ 7.8405 L/100 km. For 100 km: liters ≈ 7.8405.
        // $3.60 / gal ≈ 0.9510 / L. Cost ≈ 7.8405 * 0.9510 ≈ 7.4562
        val assumptions = IceAssumptions(
            economyValue = 30.0,
            economyIsMpg = true,
            fuelPrice = 3.60,
            fuelPriceIsPerGallon = true,
        )
        val result = IceComparisonCalculator.compare(
            distance = 100.0,
            distanceIsMiles = false,
            teslaCost = null,
            assumptions = assumptions,
        )

        val expectedLiters = 235.214583 / 30.0 // ≈ 7.8405
        val expectedPricePerLiter = 3.60 / 3.785411784
        val expectedCost = expectedLiters * expectedPricePerLiter

        assertEquals(expectedLiters, result.iceVolumeUsed ?: 0.0, 0.0001)
        assertEquals(expectedCost, result.iceCost ?: 0.0, 0.0001)
        assertFalse(result.usedImperialVolume)
        assertNull(result.savings)
        assertNull(result.savingsPercent)
    }

    // ============== Mixed unit: assumptions in L/100km / $ per liter, distance mi ==============

    @Test
    fun `L per 100 km economy is converted to mpg when distance is miles`() {
        // 7.8405 L/100km ≈ 30 mpg. 200 mi at 30 mpg = 6.6667 gal.
        // $1.80/L * 3.7854 = $6.8137/gal. Cost ≈ 6.6667 * 6.8137 ≈ 45.42
        val assumptions = IceAssumptions(
            economyValue = 235.214583 / 30.0, // exact "30 mpg" in L/100km
            economyIsMpg = false,
            fuelPrice = 1.80,
            fuelPriceIsPerGallon = false,
        )
        val result = IceComparisonCalculator.compare(
            distance = 200.0,
            distanceIsMiles = true,
            teslaCost = 20.0,
            assumptions = assumptions,
        )

        val expectedGallons = 200.0 / 30.0
        val expectedPricePerGallon = 1.80 * 3.785411784
        val expectedCost = expectedGallons * expectedPricePerGallon

        assertEquals(expectedGallons, result.iceVolumeUsed ?: 0.0, 0.0001)
        assertEquals(expectedCost, result.iceCost ?: 0.0, 0.0001)
        assertTrue(result.usedImperialVolume)
        assertEquals(expectedCost - 20.0, result.savings ?: 0.0, 0.0001)
    }

    // ============== Missing / edge cases ==============

    @Test
    fun `missing assumptions produce null ice cost but preserve tesla cost`() {
        val result = IceComparisonCalculator.compare(
            distance = 500.0,
            distanceIsMiles = false,
            teslaCost = 25.0,
            assumptions = IceAssumptions(),
        )
        assertNull(result.iceCost)
        assertNull(result.savings)
        assertNull(result.savingsPercent)
        assertNull(result.iceVolumeUsed)
        assertEquals(25.0, result.teslaCost ?: 0.0, 0.0001)
        assertEquals(500.0, result.distance, 0.0001)
    }

    @Test
    fun `zero distance produces null ice cost`() {
        val result = IceComparisonCalculator.compare(
            distance = 0.0,
            distanceIsMiles = false,
            teslaCost = 0.0,
            assumptions = IceAssumptions(
                economyValue = 7.0,
                fuelPrice = 1.8,
            ),
        )
        assertNull(result.iceCost)
        assertNull(result.iceVolumeUsed)
        assertNull(result.savings)
        assertNull(result.savingsPercent)
    }

    @Test
    fun `negative distance is treated as no comparison`() {
        val result = IceComparisonCalculator.compare(
            distance = -1.0,
            distanceIsMiles = true,
            teslaCost = null,
            assumptions = IceAssumptions(
                economyValue = 30.0,
                economyIsMpg = true,
                fuelPrice = 3.6,
                fuelPriceIsPerGallon = true,
            ),
        )
        assertNull(result.iceCost)
    }

    @Test
    fun `tesla cost null yields null savings but ice cost is still computed`() {
        val result = IceComparisonCalculator.compare(
            distance = 100.0,
            distanceIsMiles = false,
            teslaCost = null,
            assumptions = IceAssumptions(
                economyValue = 6.0,
                fuelPrice = 1.5,
            ),
        )
        // 100 * 6/100 = 6 L; 6 * 1.5 = 9.0
        assertEquals(9.0, result.iceCost ?: 0.0, 0.0001)
        assertNull(result.savings)
        assertNull(result.savingsPercent)
        assertNull(result.teslaCost)
    }

    @Test
    fun `negative savings when tesla costs more than gasoline`() {
        val result = IceComparisonCalculator.compare(
            distance = 100.0,
            distanceIsMiles = false,
            teslaCost = 50.0,
            assumptions = IceAssumptions(
                economyValue = 6.0,
                fuelPrice = 1.5,
            ),
        )
        // ICE = 9.0, Tesla = 50.0 → savings = -41.0
        assertEquals(-41.0, result.savings ?: 0.0, 0.0001)
        assertEquals(-41.0 / 9.0 * 100.0, result.savingsPercent ?: 0.0, 0.0001)
    }

    @Test
    fun `metric round trip for a common European assumption`() {
        // 6.5 L/100km, €1.75/L, 1234 km: liters = 80.21, cost = 140.3675
        val result = IceComparisonCalculator.compare(
            distance = 1234.0,
            distanceIsMiles = false,
            teslaCost = 61.7,
            assumptions = IceAssumptions(
                economyValue = 6.5,
                economyIsMpg = false,
                fuelPrice = 1.75,
                fuelPriceIsPerGallon = false,
            ),
        )
        val expectedLiters = 1234.0 * 6.5 / 100.0
        val expectedCost = expectedLiters * 1.75
        assertEquals(expectedLiters, result.iceVolumeUsed ?: 0.0, 0.0001)
        assertEquals(expectedCost, result.iceCost ?: 0.0, 0.0001)
        assertEquals(expectedCost - 61.7, result.savings ?: 0.0, 0.0001)
    }
}
