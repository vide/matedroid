package com.matedroid.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatterSplitTest {

    @Test
    fun splitsAtTheLastSpace() {
        assertEquals("1,234" to "km", UnitFormatter.splitValueUnit("1,234 km"))
        assertEquals("38.20" to "€", UnitFormatter.splitValueUnit("38.20 €"))
        assertEquals("152" to "Wh/km", UnitFormatter.splitValueUnit("152 Wh/km"))
    }

    @Test
    fun splitsTemperaturesWithoutASpace() {
        assertEquals("-3.5" to "°C", UnitFormatter.splitValueUnit("-3.5°C"))
        assertEquals("98.0" to "°F", UnitFormatter.splitValueUnit("98.0°F"))
    }

    @Test
    fun bareNumberHasNoUnit() {
        assertEquals("42" to "", UnitFormatter.splitValueUnit("42"))
    }
}
