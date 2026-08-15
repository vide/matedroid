package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighSocWarningTest {

    @Test
    fun `warns above the threshold when parked`() {
        assertTrue(HighSocWarning.shouldWarn(batteryLevel = 95, isCharging = false, threshold = 90))
    }

    @Test
    fun `does not warn at the threshold itself`() {
        assertFalse(HighSocWarning.shouldWarn(batteryLevel = 90, isCharging = false, threshold = 90))
    }

    @Test
    fun `never warns while charging`() {
        assertFalse(HighSocWarning.shouldWarn(batteryLevel = 100, isCharging = true, threshold = 90))
    }

    @Test
    fun `disabled threshold silences the warning even at full charge`() {
        assertFalse(
            HighSocWarning.shouldWarn(
                batteryLevel = 100,
                isCharging = false,
                threshold = HighSocWarning.DISABLED
            )
        )
    }

    @Test
    fun `default threshold keeps the previous above-90 behaviour`() {
        assertFalse(
            HighSocWarning.shouldWarn(90, isCharging = false, threshold = HighSocWarning.DEFAULT_THRESHOLD)
        )
        assertTrue(
            HighSocWarning.shouldWarn(91, isCharging = false, threshold = HighSocWarning.DEFAULT_THRESHOLD)
        )
    }

    @Test
    fun `presets ascend with the disabled option last`() {
        assertTrue(HighSocWarning.PRESETS.last() == HighSocWarning.DISABLED)
        assertTrue(HighSocWarning.PRESETS.contains(HighSocWarning.DEFAULT_THRESHOLD))
        val levels = HighSocWarning.PRESETS.dropLast(1)
        assertEquals(levels.sorted(), levels)
    }

    @Test
    fun `warning still fires one point below a full charge`() {
        assertTrue(HighSocWarning.shouldWarn(batteryLevel = 100, isCharging = false, threshold = 99))
        assertFalse(HighSocWarning.shouldWarn(batteryLevel = 99, isCharging = false, threshold = 99))
    }
}
