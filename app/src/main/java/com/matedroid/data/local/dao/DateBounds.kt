package com.matedroid.data.local.dao

/**
 * Sentinel date bounds for running range-parameterized DAO queries over all time.
 *
 * All date columns in the local database are non-null ISO-8601 TEXT
 * (e.g. "2024-12-07T00:00:00Z"), so SQLite compares them lexicographically.
 * `startDate >= '' AND startDate < '￿'` is therefore true for every row:
 * the empty string sorts before any date, and U+FFFF sorts after any ASCII text.
 *
 * This lets every "X vs XInRange" DAO query pair collapse into a single
 * range-parameterized query; all-time callers simply pass [MIN] and [MAX].
 */
object DateBounds {
    /** Lower bound matching all rows: the empty string sorts before any date. */
    const val MIN = ""

    /** Upper (exclusive) bound matching all rows: U+FFFF sorts after any ASCII date. */
    const val MAX = "￿"
}
