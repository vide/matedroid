package com.matedroid.ui.screens.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCellLayoutTest {

    // Roughly what the hero (44sp figure + caps caption) and the today caption measure at 1x.
    private val hero = 60f
    private val today = 12f

    @Test
    fun mockupValues_matchTheMockupGeometry() {
        val c = layoutCell(88.0, 43, hero, today)
        // Inner area 15..313 (298): 88 % puts the fill top at 50.76
        assertEquals(50.76f, c.fillTop, 0.01f)
        // 43 % of the usable fill, not of the outline
        assertEquals(313f - (313f - 50.76f) * 0.43f, c.todayY, 0.01f)
        assertEquals(10f, c.anchors[0], 0.001f)
        assertEquals((15f + 50.76f) / 2f, c.anchors[1], 0.01f)
        assertEquals(50.76f, c.anchors[2], 0.01f)
        assertTrue(c.heroInFill)
        // The figure sits low in the fill, below the today line and clear of it
        assertTrue(c.heroTop > c.todayY)
        assertNotNull(c.todayLabelTop)
        assertTrue(c.todayLabelTop!! + today <= c.todayY)
        assertTrue(c.showKeys)
    }

    @Test
    fun labels_fanOut_soTheyNeverOverlap() {
        val c = layoutCell(99.0, 50, hero, today)
        for (i in 1 until c.labelTops.size) {
            assertTrue(c.labelTops[i] - c.labelTops[i - 1] >= CellSpec.LABEL_PITCH - 0.001f)
        }
        assertEquals(c.anchors[0] - CellSpec.VALUE_HALF, c.labelTops[0], 0.001f)
    }

    @Test
    fun lowCharge_movesTheFigureAboveTheTodayLine() {
        val c = layoutCell(88.0, 5, hero, today)
        assertTrue(c.heroInFill)
        assertTrue(c.heroTop + hero <= minOf(c.todayLabelTop ?: c.todayY, c.todayY))
        assertTrue(c.heroTop >= c.fillTop)
    }

    @Test
    fun fullCharge_putsTheTodayCaptionBelowTheLine() {
        val c = layoutCell(88.0, 100, hero, today)
        assertEquals(c.fillTop, c.todayY, 0.001f)
        assertNotNull(c.todayLabelTop)
        assertTrue(c.todayLabelTop!! > c.todayY)
    }

    @Test
    fun lowHealth_putsTheFigureAboveTheFill() {
        val c = layoutCell(20.0, 50, hero, today)
        assertFalse(c.heroInFill)
        assertTrue(c.heroTop + hero <= c.fillTop)
        assertTrue(c.heroTop >= CellSpec.INNER_TOP)
    }

    @Test
    fun emptyFill_dropsTheTodayCaption() {
        val c = layoutCell(0.0, 50, hero, today)
        assertNull(c.todayLabelTop)
        assertFalse(c.heroInFill)
        // Labels pushed to the bottom collide with the column keys, so the keys go
        assertFalse(c.showKeys)
    }

    @Test
    fun outOfRangeInputs_areClamped() {
        val c = layoutCell(120.0, 150, hero, today)
        assertEquals(CellSpec.INNER_TOP, c.fillTop, 0.001f)
        assertEquals(CellSpec.INNER_TOP, c.todayY, 0.001f)
    }
}
