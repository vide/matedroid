package com.matedroid.ui.screens.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCellLayoutTest {

    // Roughly what the hero (44sp figure + caps caption) and the lost-share pill measure at 1x.
    private val hero = 60f
    private val pill = 20f

    @Test
    fun mockupValues_matchTheMockupGeometry() {
        val c = layoutCell(88.0, hero, pill)
        // Inner area 15..313 (298): 88 % puts the fill top at 50.76
        assertEquals(50.76f, c.fillTop, 0.01f)
        assertEquals(10f, c.anchors[0], 0.001f)
        assertEquals((15f + 50.76f) / 2f, c.anchors[1], 0.01f)
        assertEquals(50.76f, c.anchors[2], 0.01f)
        assertTrue(c.heroInFill)
        // The pill is centred in the sliver and fits inside it
        assertEquals(c.anchors[1] - pill / 2f, c.lossPillTop!!, 0.01f)
        assertTrue(c.lossPillTop!! >= CellSpec.INNER_TOP)
        assertTrue(c.lossPillTop!! + pill <= c.fillTop)
        assertTrue(c.showKeys)
    }

    @Test
    fun labels_fanOut_soTheyNeverOverlap() {
        val c = layoutCell(99.0, hero, pill)
        for (i in 1 until c.labelTops.size) {
            assertTrue(c.labelTops[i] - c.labelTops[i - 1] >= CellSpec.LABEL_PITCH - 0.001f)
        }
        assertEquals(c.anchors[0] - CellSpec.VALUE_HALF, c.labelTops[0], 0.001f)
    }

    @Test
    fun thinSliver_clampsThePillUnderTheInnerTop() {
        val c = layoutCell(96.5, hero, pill)
        assertEquals(CellSpec.INNER_TOP + 2f, c.lossPillTop!!, 0.001f)
        // It straddles the edge between lost and usable
        assertTrue(c.lossPillTop!! + pill > c.fillTop)
    }

    @Test
    fun noLoss_hasNoPill() {
        assertNull(layoutCell(100.0, hero, pill).lossPillTop)
    }

    @Test
    fun lowHealth_putsTheFigureAboveTheFill_andThePillAboveTheFigure() {
        val c = layoutCell(20.0, hero, pill)
        assertFalse(c.heroInFill)
        assertTrue(c.heroTop + hero <= c.fillTop)
        assertTrue(c.heroTop >= CellSpec.INNER_TOP)
        assertNotNull(c.lossPillTop)
        assertTrue(c.lossPillTop!! + pill <= c.heroTop)
    }

    @Test
    fun emptyFill_dropsTheKeys() {
        val c = layoutCell(0.0, hero, pill)
        assertFalse(c.heroInFill)
        // Labels pushed to the bottom collide with the column keys, so the keys go
        assertFalse(c.showKeys)
    }

    @Test
    fun outOfRangeInputs_areClamped() {
        val c = layoutCell(120.0, hero, pill)
        assertEquals(CellSpec.INNER_TOP, c.fillTop, 0.001f)
        assertNull(c.lossPillTop)
    }
}
