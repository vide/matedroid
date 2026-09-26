package com.matedroid.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnreadableDaySearchTest {

    private fun day(s: String) = LocalDate.parse(s)

    /** Mimics TeslamateAPI: a period fails when it holds a whole bad entry (start >= from, end <= to). */
    private class FakeServer(private val badEntries: List<Pair<LocalDate, LocalDate>>) {
        var requests = 0
        suspend fun fails(from: LocalDate, to: LocalDate): Boolean {
            requests++
            return badEntries.any { (start, end) -> start >= from && end <= to }
        }
    }

    private suspend fun FakeServer.search(from: String, to: String, maxDays: Int = UnreadableDaySearch.MAX_DAYS) =
        UnreadableDaySearch.find(day(from), day(to), maxDays, ::fails)

    @Test
    fun `finds the day of a bad entry inside one day`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-19")))

        val found = UnreadableDaySearch.find(UnreadableDaySearch.EARLIEST, day("2026-09-26"), fails = server::fails)

        assertEquals(listOf(day("2026-06-19")), found?.days)
        assertFalse(found!!.mayBeMore)
        // ~log2(5400 days) halvings, two requests each, plus the initial check.
        assertTrue("took ${server.requests} requests", server.requests <= 30)
    }

    @Test
    fun `two bad entries on the same day are one day`() = runTest {
        // The real case behind #385: two hand-entered charges on 2026-06-19, the second overnight.
        val server = FakeServer(listOf(
            day("2026-06-19") to day("2026-06-19"),
            day("2026-06-19") to day("2026-06-20"),
        ))

        assertEquals(listOf(day("2026-06-19")), server.search("2025-09-27", "2026-09-26")?.days)
    }

    @Test
    fun `finds every bad day in the period, oldest first`() = runTest {
        val bad = listOf("2026-08-02", "2025-11-15", "2026-03-01").map { day(it) to day(it) }
        val server = FakeServer(bad)

        val found = server.search("2025-01-01", "2026-09-26")

        assertEquals(listOf(day("2025-11-15"), day("2026-03-01"), day("2026-08-02")), found?.days)
        assertFalse(found!!.mayBeMore)
    }

    @Test
    fun `stops at the day limit and says there may be more`() = runTest {
        val bad = (1..8).map { day("2026-0$it-10") to day("2026-0$it-10") }
        val server = FakeServer(bad)

        val found = server.search("2026-01-01", "2026-09-26", maxDays = 3)

        assertEquals(3, found?.days?.size)
        assertTrue(found!!.mayBeMore)
    }

    @Test
    fun `exactly the limit is not reported as more`() = runTest {
        val bad = (1..3).map { day("2026-0$it-10") to day("2026-0$it-10") }
        val server = FakeServer(bad)

        val found = server.search("2026-01-01", "2026-09-26", maxDays = 3)

        assertEquals(3, found?.days?.size)
        assertFalse(found!!.mayBeMore)
    }

    @Test
    fun `finds an entry that runs past midnight across a split point`() = runTest {
        // 2026-01-01..2026-01-04: mid is 01-02, so an entry from 01-02 to 01-03 is in neither half.
        val server = FakeServer(listOf(day("2026-01-02") to day("2026-01-03")))

        assertEquals(listOf(day("2026-01-02")), server.search("2026-01-01", "2026-01-04")?.days)
    }

    @Test
    fun `finds an overnight entry wherever it falls in a long period`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-20")))

        assertEquals(listOf(day("2026-06-19")), server.search("2025-09-27", "2026-09-26")?.days)
    }

    @Test
    fun `a period that loads has nothing to find`() = runTest {
        val server = FakeServer(listOf(day("2026-06-19") to day("2026-06-19")))

        assertNull(server.search("2025-01-01", "2025-12-31"))
        assertEquals(1, server.requests)
    }

    @Test
    fun `any other failure ends the search and keeps what was found`() = runTest {
        val server = FakeServer(listOf(day("2026-02-10") to day("2026-02-10"), day("2026-11-10") to day("2026-11-10")))
        var calls = 0
        // Let the search pin down the first day, then fail every request after that.
        val found = UnreadableDaySearch.find(day("2026-01-01"), day("2026-12-31")) { from, to ->
            if (calls++ < 24) server.fails(from, to) else null
        }

        assertTrue(found!!.mayBeMore)
        assertTrue(found.days.all { it == day("2026-02-10") || it == day("2026-11-10") })
    }
}
