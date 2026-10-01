package com.matedroid.domain

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The thresholds live in a process-wide mirror, so every test restores the defaults afterwards
 * to keep the object from leaking state into the rest of the suite.
 */
class ShortEntryFilterTest {

    @Before
    @After
    fun resetThresholds() {
        ShortEntryFilter.minDriveDurationMin = ShortEntryFilter.DEFAULT_MIN_DRIVE_DURATION_MIN
        ShortEntryFilter.minDriveDistance = ShortEntryFilter.DEFAULT_MIN_DRIVE_DISTANCE
        ShortEntryFilter.minChargeEnergyKwh = ShortEntryFilter.DEFAULT_MIN_CHARGE_ENERGY_KWH
    }

    // ==================== Defaults ====================

    @Test
    fun `default drive thresholds keep the historical 1 min and 1 unit rule`() {
        assertFalse(ShortEntryFilter.isSignificantDrive(durationMin = 0, distance = 5.0))
        assertFalse(ShortEntryFilter.isSignificantDrive(durationMin = 5, distance = 0.9))
        assertTrue(ShortEntryFilter.isSignificantDrive(durationMin = 1, distance = 1.0))
        assertTrue(ShortEntryFilter.isSignificantDrive(durationMin = 30, distance = 42.0))
    }

    @Test
    fun `default charge threshold keeps the historical 0-1 kWh rule`() {
        assertFalse(ShortEntryFilter.isSignificantCharge(0.1))
        assertFalse(ShortEntryFilter.isSignificantCharge(0.05))
        assertTrue(ShortEntryFilter.isSignificantCharge(0.11))
        assertTrue(ShortEntryFilter.isSignificantCharge(50.0))
    }

    @Test
    fun `null values are treated as zero and filtered out`() {
        assertFalse(ShortEntryFilter.isSignificantDrive(durationMin = null, distance = null))
        assertFalse(ShortEntryFilter.isSignificantCharge(null))
    }

    // ==================== Configured thresholds ====================

    @Test
    fun `raising the duration threshold hides drives that were previously shown`() {
        val tenMinuteDrive = { ShortEntryFilter.isSignificantDrive(durationMin = 10, distance = 50.0) }

        assertTrue(tenMinuteDrive())

        ShortEntryFilter.minDriveDurationMin = 20
        assertFalse(tenMinuteDrive())
    }

    @Test
    fun `raising the distance threshold hides drives that were previously shown`() {
        val twoUnitDrive = { ShortEntryFilter.isSignificantDrive(durationMin = 30, distance = 2.0) }

        assertTrue(twoUnitDrive())

        ShortEntryFilter.minDriveDistance = 5.0
        assertFalse(twoUnitDrive())
    }

    @Test
    fun `raising the charge threshold hides charges that were previously shown`() {
        val oneKwhCharge = { ShortEntryFilter.isSignificantCharge(1.0) }

        assertTrue(oneKwhCharge())

        ShortEntryFilter.minChargeEnergyKwh = 2.0
        assertFalse(oneKwhCharge())
    }

    @Test
    fun `a drive must clear both the duration and the distance threshold`() {
        ShortEntryFilter.minDriveDurationMin = 5
        ShortEntryFilter.minDriveDistance = 5.0

        assertFalse(ShortEntryFilter.isSignificantDrive(durationMin = 10, distance = 1.0))
        assertFalse(ShortEntryFilter.isSignificantDrive(durationMin = 1, distance = 10.0))
        assertTrue(ShortEntryFilter.isSignificantDrive(durationMin = 10, distance = 10.0))
    }

    // ==================== "No minimum" (threshold 0) ====================

    @Test
    fun `zero duration threshold stops filtering on duration`() {
        ShortEntryFilter.minDriveDurationMin = 0
        ShortEntryFilter.minDriveDistance = 0.0

        assertTrue(ShortEntryFilter.isSignificantDrive(durationMin = 0, distance = 0.0))
    }

    @Test
    fun `zero charge threshold shows even a zero kWh charge`() {
        ShortEntryFilter.minChargeEnergyKwh = 0.0

        // Without the explicit guard the strict `>` comparison would still hide this.
        assertTrue(ShortEntryFilter.isSignificantCharge(0.0))
        assertTrue(ShortEntryFilter.isSignificantCharge(0.01))
    }

    // ==================== Presets ====================

    @Test
    fun `every preset list offers the default value so the picker always has a match`() {
        assertTrue(
            ShortEntryFilter.DEFAULT_MIN_DRIVE_DURATION_MIN
                in ShortEntryFilter.DRIVE_DURATION_PRESETS_MIN
        )
        assertTrue(
            ShortEntryFilter.DEFAULT_MIN_DRIVE_DISTANCE
                in ShortEntryFilter.DRIVE_DISTANCE_PRESETS
        )
        assertTrue(
            ShortEntryFilter.DEFAULT_MIN_CHARGE_ENERGY_KWH
                in ShortEntryFilter.CHARGE_ENERGY_PRESETS_KWH
        )
    }

    @Test
    fun `every preset list offers a no-minimum option`() {
        assertTrue(0 in ShortEntryFilter.DRIVE_DURATION_PRESETS_MIN)
        assertTrue(0.0 in ShortEntryFilter.DRIVE_DISTANCE_PRESETS)
        assertTrue(0.0 in ShortEntryFilter.CHARGE_ENERGY_PRESETS_KWH)
    }
}
