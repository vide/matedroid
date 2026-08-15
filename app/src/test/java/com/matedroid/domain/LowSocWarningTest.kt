package com.matedroid.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowSocWarningTest {

    @Test
    fun `reads as low below the threshold`() {
        assertTrue(LowSocWarning.isLow(batteryLevel = 15, threshold = 20))
    }

    @Test
    fun `does not read as low at the threshold itself`() {
        assertFalse(LowSocWarning.isLow(batteryLevel = 20, threshold = 20))
    }

    @Test
    fun `amber band covers the points just above the threshold`() {
        assertTrue(LowSocWarning.isGettingLow(batteryLevel = 30, threshold = 20))
        assertFalse(LowSocWarning.isGettingLow(batteryLevel = 40, threshold = 20))
    }

    @Test
    fun `disabled threshold leaves every level alone`() {
        assertFalse(LowSocWarning.isLow(batteryLevel = 1, threshold = LowSocWarning.DISABLED))
        assertFalse(LowSocWarning.isGettingLow(batteryLevel = 1, threshold = LowSocWarning.DISABLED))
    }

    @Test
    fun `default threshold keeps the previous red-under-20 amber-under-40 behaviour`() {
        val default = LowSocWarning.DEFAULT_THRESHOLD
        assertTrue(LowSocWarning.isLow(19, default))
        assertFalse(LowSocWarning.isLow(20, default))
        assertTrue(LowSocWarning.isGettingLow(39, default))
        assertFalse(LowSocWarning.isGettingLow(40, default))
    }

    @Test
    fun `presets offer the disabled option first`() {
        assertTrue(LowSocWarning.PRESETS.first() == LowSocWarning.DISABLED)
        assertTrue(LowSocWarning.PRESETS.contains(LowSocWarning.DEFAULT_THRESHOLD))
    }
}
