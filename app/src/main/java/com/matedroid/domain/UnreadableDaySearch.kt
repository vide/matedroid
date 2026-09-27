package com.matedroid.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Finds the days holding entries TeslamateAPI can't read.
 *
 * TeslamateAPI fails a whole list query when one row has a NULL in a column it scans into a
 * non-nullable type (seen with hand-entered charges that have no range or temperature), and it
 * says only "Unable to load charges." — no ID, no date. Halving the period and asking again
 * names a bad day in ~log2(days) requests, so the user knows which entry to fix in TeslaMate.
 * Both halves are followed, so several bad days come out of one search instead of one per fix.
 *
 * The API filters on `start >= from AND end <= to`, so an entry running across the split point
 * belongs to neither half. When both halves load, that is the entry: it starts on the last day
 * of the first half. (If a half also fails for another reason, such a straddling entry is only
 * found once the others are fixed.)
 */
object UnreadableDaySearch {

    /** Lower bound for "All time": nothing TeslaMate logs can predate the first Model S. */
    val EARLIEST: LocalDate = LocalDate.of(2012, 1, 1)

    /** Enough to act on; each extra day costs another ~log2(days) requests. */
    const val MAX_DAYS = 5

    /**
     * @param days the bad days found, oldest first; empty when none could be pinned down.
     * @param mayBeMore true when the search stopped at [MAX_DAYS] or was aborted part-way,
     *   so there can be bad days beyond [days].
     */
    data class Result(val days: List<LocalDate>, val mayBeMore: Boolean)

    /**
     * @param fails asks whether [from]..[to] (whole days) is rejected: true = rejected,
     *   false = loads, null = the request failed for another reason, which ends the search.
     * @return null when [from]..[to] doesn't fail (or the first request failed otherwise).
     */
    suspend fun find(
        from: LocalDate,
        to: LocalDate,
        maxDays: Int = MAX_DAYS,
        fails: suspend (from: LocalDate, to: LocalDate) -> Boolean?
    ): Result? {
        if (fails(from, to) != true) return null
        val days = mutableListOf<LocalDate>()
        var stopped = false

        // Precondition: lo..hi is known to fail.
        suspend fun search(lo: LocalDate, hi: LocalDate) {
            if (stopped) return
            if (days.size >= maxDays) { stopped = true; return }
            if (lo == hi) { days += lo; return }
            val mid = lo.plusDays(ChronoUnit.DAYS.between(lo, hi) / 2)
            val left = fails(lo, mid) ?: run { stopped = true; return }
            val right = fails(mid.plusDays(1), hi) ?: run { stopped = true; return }
            if (left) search(lo, mid)
            if (right) search(mid.plusDays(1), hi)
            if (!left && !right) days += mid
        }

        search(from, to)
        return Result(days.distinct().sorted(), mayBeMore = stopped)
    }
}
