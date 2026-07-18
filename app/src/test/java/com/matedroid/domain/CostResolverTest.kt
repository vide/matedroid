package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostResolverTest {

    // ---------------- resolveChargeCost ----------------

    @Test
    fun `recorded cost is always preferred over estimation`() {
        val result = CostResolver.resolveChargeCost(
            cost = 12.5,
            energyAdded = 50.0,
            isDc = false,
            rates = UtilityRates(homePerKwh = 0.20),
        )
        assertEquals(CostSource.Recorded, result.source)
        assertEquals(12.5, result.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `zero cost is recorded not missing`() {
        val result = CostResolver.resolveChargeCost(
            cost = 0.0,
            energyAdded = 30.0,
            isDc = true,
            rates = UtilityRates(homePerKwh = 0.20, dcPerKwh = 0.40),
        )
        // Recorded 0.0 (a free session) must survive; estimation must not overwrite.
        assertEquals(CostSource.Recorded, result.source)
        assertEquals(0.0, result.amount ?: -1.0, 0.0001)
    }

    @Test
    fun `null cost with home rate yields ac estimate`() {
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 40.0,
            isDc = false,
            rates = UtilityRates(homePerKwh = 0.15),
        )
        assertEquals(CostSource.Estimated, result.source)
        assertEquals(6.0, result.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `null cost with dc rate yields dc estimate`() {
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 20.0,
            isDc = true,
            rates = UtilityRates(homePerKwh = 0.10, dcPerKwh = 0.50),
        )
        assertEquals(CostSource.Estimated, result.source)
        assertEquals(10.0, result.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `dc estimate falls back to home rate when dc rate is missing`() {
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 25.0,
            isDc = true,
            rates = UtilityRates(homePerKwh = 0.20),
        )
        assertEquals(CostSource.Estimated, result.source)
        assertEquals(5.0, result.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `null cost with no rates configured yields missing`() {
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 30.0,
            isDc = true,
            rates = UtilityRates(),
        )
        assertEquals(CostSource.Missing, result.source)
        assertNull(result.amount)
    }

    @Test
    fun `ac charge with only dc rate configured yields missing`() {
        // AC never falls back to DC; only DC falls back to home.
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 30.0,
            isDc = false,
            rates = UtilityRates(dcPerKwh = 0.40),
        )
        assertEquals(CostSource.Missing, result.source)
        assertNull(result.amount)
    }

    @Test
    fun `negative or non finite energy is treated as missing`() {
        val rates = UtilityRates(homePerKwh = 0.20)
        assertEquals(
            CostSource.Missing,
            CostResolver.resolveChargeCost(null, -1.0, false, rates).source,
        )
        assertEquals(
            CostSource.Missing,
            CostResolver.resolveChargeCost(null, Double.NaN, false, rates).source,
        )
        assertEquals(
            CostSource.Missing,
            CostResolver.resolveChargeCost(null, Double.POSITIVE_INFINITY, false, rates).source,
        )
    }

    @Test
    fun `zero energy estimate is zero not missing`() {
        val result = CostResolver.resolveChargeCost(
            cost = null,
            energyAdded = 0.0,
            isDc = false,
            rates = UtilityRates(homePerKwh = 0.20),
        )
        assertEquals(CostSource.Estimated, result.source)
        assertEquals(0.0, result.amount ?: -1.0, 0.0001)
    }

    // ---------------- estimateDriveCost ----------------

    @Test
    fun `drive cost uses home rate`() {
        val result = CostResolver.estimateDriveCost(
            energyConsumed = 12.0,
            rates = UtilityRates(homePerKwh = 0.25, dcPerKwh = 0.60),
        )
        assertEquals(CostSource.Estimated, result.source)
        assertEquals(3.0, result.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `drive cost is missing without home rate even if dc is set`() {
        val result = CostResolver.estimateDriveCost(
            energyConsumed = 12.0,
            rates = UtilityRates(dcPerKwh = 0.60),
        )
        assertEquals(CostSource.Missing, result.source)
        assertNull(result.amount)
    }

    @Test
    fun `drive cost is missing when energy is null or negative`() {
        val rates = UtilityRates(homePerKwh = 0.25)
        assertEquals(CostSource.Missing, CostResolver.estimateDriveCost(null, rates).source)
        assertEquals(CostSource.Missing, CostResolver.estimateDriveCost(-1.0, rates).source)
    }

    // ---------------- UtilityRates ----------------

    @Test
    fun `utility rates isConfigured reflects any set rate`() {
        assertEquals(false, UtilityRates().isConfigured)
        assertEquals(true, UtilityRates(homePerKwh = 0.1).isConfigured)
        assertEquals(true, UtilityRates(dcPerKwh = 0.4).isConfigured)
        assertEquals(true, UtilityRates(homePerKwh = 0.1, dcPerKwh = 0.4).isConfigured)
    }

    @Test
    fun `rateFor picks dc then falls back to home when dc missing`() {
        val both = UtilityRates(homePerKwh = 0.1, dcPerKwh = 0.4)
        assertEquals(0.1, both.rateFor(isDc = false) ?: 0.0, 0.0001)
        assertEquals(0.4, both.rateFor(isDc = true) ?: 0.0, 0.0001)

        val homeOnly = UtilityRates(homePerKwh = 0.1)
        assertEquals(0.1, homeOnly.rateFor(isDc = true) ?: 0.0, 0.0001)
        assertEquals(0.1, homeOnly.rateFor(isDc = false) ?: 0.0, 0.0001)

        val dcOnly = UtilityRates(dcPerKwh = 0.4)
        assertEquals(0.4, dcOnly.rateFor(isDc = true) ?: 0.0, 0.0001)
        assertNull(dcOnly.rateFor(isDc = false))
    }
}
