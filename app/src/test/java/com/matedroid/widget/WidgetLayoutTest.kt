package com.matedroid.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Specification table for widget display fields per size and charge state.
 *
 * | Feature                  | 1×1 | 1×2 | 2×1          | 2×2               | 3×2            |
 * |--------------------------|-----|-----|--------------|-------------------|----------------|
 * | Temperatures             |  ✗  |  ✗  |  ✓           |  ✓                |  ✓             |
 * | Available mileage        |  ✗  |  ✗  |  ✗           |  not charging     |  always        |
 * | Charge limit             |  ✗  |  ✗  |  charging ✓  |  always           |  always        |
 * | Voltage / current / ph   |  ✗  |  ✗  |  ✗           |  charging ✓       |  charging ✓    |
 * | Location (home screen)   |  ✗  |  ✗  |  ✗           |  ✓                |  ✓             |
 *
 * Fields always present at every size (not tested here):
 *   car picture, battery SoC % + bar, car name, status icons,
 *   AC/DC badge, glow + kWh added + time to full (when charging).
 */
class WidgetLayoutTest {

    // Representative dp values — chosen well inside each threshold bucket
    // so tests do not depend on the exact threshold values.
    // Calibrated against Pixel 6a measurements: 1-col=179, 2-col=277, 3-col=374 dp;
    // 1-row=96, 2-row=202 dp.
    private val W_NARROW  = 100f   // 1-column  (< NARROW_WIDTH_DP  = 220)
    private val W_MEDIUM  = 270f   // 2-column  ([NARROW, WIDE)      = 220–320)
    private val W_WIDE    = 380f   // 3-column  (≥ WIDE_WIDTH_DP     = 320)
    private val H_COMPACT =  60f   // 1-row     (< COMPACT_HEIGHT_DP = 130)
    private val H_TALL    = 150f   // 2-row     (≥ COMPACT_HEIGHT_DP = 130)

    // -------------------------------------------------------------------------
    // 1×1
    // -------------------------------------------------------------------------

    @Test
    fun `1x1 not charging - nothing extra shown`() {
        val l = computeWidgetLayout(W_NARROW, H_COMPACT, isCharging = false)
        assertFalse(l.showTemperatures)
        assertFalse(l.showMileage)
        assertFalse(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    @Test
    fun `1x1 charging - nothing extra shown`() {
        val l = computeWidgetLayout(W_NARROW, H_COMPACT, isCharging = true)
        assertFalse(l.showTemperatures)
        assertFalse(l.showMileage)
        assertFalse(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    // -------------------------------------------------------------------------
    // 1×2  (same content as 1×1, taller layout)
    // -------------------------------------------------------------------------

    @Test
    fun `1x2 not charging - nothing extra shown`() {
        val l = computeWidgetLayout(W_NARROW, H_TALL, isCharging = false)
        assertFalse(l.showTemperatures)
        assertFalse(l.showMileage)
        assertFalse(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    @Test
    fun `1x2 charging - nothing extra shown`() {
        val l = computeWidgetLayout(W_NARROW, H_TALL, isCharging = true)
        assertFalse(l.showTemperatures)
        assertFalse(l.showMileage)
        assertFalse(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    // -------------------------------------------------------------------------
    // 2×1
    // -------------------------------------------------------------------------

    @Test
    fun `2x1 not charging - temps only`() {
        val l = computeWidgetLayout(W_MEDIUM, H_COMPACT, isCharging = false)
        assertTrue(l.showTemperatures)
        assertFalse(l.showMileage)
        assertFalse(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    @Test
    fun `2x1 charging - temps and charge limit no mileage or volts`() {
        val l = computeWidgetLayout(W_MEDIUM, H_COMPACT, isCharging = true)
        assertTrue(l.showTemperatures)
        assertFalse(l.showMileage)
        assertTrue(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertFalse(l.showLocation)
    }

    // -------------------------------------------------------------------------
    // 2x2
    // -------------------------------------------------------------------------

    @Test
    fun `2x2 not charging - temps mileage and charge limit no volts`() {
        val l = computeWidgetLayout(W_MEDIUM, H_TALL, isCharging = false)
        assertTrue(l.showTemperatures)
        assertTrue(l.showMileage)
        assertTrue(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertTrue(l.showLocation)
    }

    @Test
    fun `2x2 charging - temps charge limit and volts no mileage`() {
        val l = computeWidgetLayout(W_MEDIUM, H_TALL, isCharging = true)
        assertTrue(l.showTemperatures)
        assertFalse(l.showMileage)
        assertTrue(l.showChargeLimit)
        assertTrue(l.showVoltageCurrentPhases)
        assertTrue(l.showLocation)
    }

    // -------------------------------------------------------------------------
    // 3×2
    // -------------------------------------------------------------------------

    @Test
    fun `3x2 not charging - temps mileage charge limit no volts`() {
        val l = computeWidgetLayout(W_WIDE, H_TALL, isCharging = false)
        assertTrue(l.showTemperatures)
        assertTrue(l.showMileage)
        assertTrue(l.showChargeLimit)
        assertFalse(l.showVoltageCurrentPhases)
        assertTrue(l.showLocation)
    }

    @Test
    fun `3x2 charging - everything shown including mileage`() {
        val l = computeWidgetLayout(W_WIDE, H_TALL, isCharging = true)
        assertTrue(l.showTemperatures)
        assertTrue(l.showMileage)        // unlike 2×2, mileage stays when charging
        assertTrue(l.showChargeLimit)
        assertTrue(l.showVoltageCurrentPhases)
        assertTrue(l.showLocation)
    }
}
