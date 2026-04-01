package com.matedroid.ui.screens.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripCountrySequenceTest {

    private fun seq(vararg codes: String) =
        TripDetailViewModel.buildCountrySequence(codes.toList())

    @Test
    fun `empty input returns empty`() {
        assertEquals(emptyList<String>(), seq())
    }

    @Test
    fun `single country`() {
        assertEquals(listOf("DE"), seq("DE"))
    }

    @Test
    fun `same start and end, no intermediates`() {
        // Round trip: DE → DE
        assertEquals(listOf("DE"), seq("DE", "DE"))
    }

    @Test
    fun `simple two-country trip`() {
        // DE → ES
        assertEquals(listOf("DE", "ES"), seq("DE", "ES"))
    }

    @Test
    fun `three countries in order`() {
        // DE → FR → ES
        assertEquals(listOf("DE", "FR", "ES"), seq("DE", "FR", "ES"))
    }

    @Test
    fun `end country revisited - must show as end not intermediate`() {
        // DE → FR → ES → FR (trip ends back in France)
        // Expected: [DE, ES, FR] — ES is intermediate, FR is the end
        assertEquals(listOf("DE", "ES", "FR"), seq("DE", "FR", "ES", "FR"))
    }

    @Test
    fun `start country revisited in middle`() {
        // DE → FR → DE → FR
        // Expected: [DE, FR] — start is DE, end is FR, no unique intermediates
        assertEquals(listOf("DE", "FR"), seq("DE", "FR", "DE", "FR"))
    }

    @Test
    fun `four countries linear`() {
        // DE → FR → ES → PT
        assertEquals(listOf("DE", "FR", "ES", "PT"), seq("DE", "FR", "ES", "PT"))
    }

    @Test
    fun `repeated intermediate countries are deduplicated`() {
        // DE → FR → CH → FR → IT
        // Expected: [DE, CH, IT] — FR appears as intermediate but same as... wait
        // FR is neither start (DE) nor end (IT), so it IS an intermediate
        // CH is also intermediate
        // But FR appears twice — dedup to one
        assertEquals(listOf("DE", "FR", "CH", "IT"), seq("DE", "FR", "CH", "FR", "IT"))
    }

    @Test
    fun `round trip through multiple countries`() {
        // DE → FR → ES → FR → DE
        // Expected: [DE, ES, DE] — FR excluded (neither start DE nor end DE),
        // wait: start=DE, end=DE, intermediates = FR, ES, FR → filter out DE → FR, ES → dedup → FR, ES
        // result: [DE, FR, ES, DE]
        assertEquals(listOf("DE", "FR", "ES", "DE"), seq("DE", "FR", "ES", "FR", "DE"))
    }

    @Test
    fun `many data points same country ignored`() {
        // All points in same country
        assertEquals(listOf("DE"), seq("DE", "DE", "DE", "DE"))
    }

    @Test
    fun `real scenario - germany to spain via france`() {
        // Multiple drives, each with start coords resolved:
        // Drive 1 start: DE, Charge 1: DE, Drive 2 start: FR,
        // Charge 2: FR, Drive 3 start: FR, Last drive end: ES
        assertEquals(
            listOf("DE", "FR", "ES"),
            seq("DE", "DE", "FR", "FR", "FR", "ES")
        )
    }
}
