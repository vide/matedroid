package com.matedroid.ui.screens.trips

import com.matedroid.domain.model.Trip
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight in-memory cache for passing a Trip from the list screen to the detail screen
 * without re-running full trip detection. Falls back to detection if the cache misses.
 */
@Singleton
class TripCache @Inject constructor() {
    private var cached: Trip? = null

    fun put(trip: Trip) {
        cached = trip
    }

    /** Retrieve and clear. Returns null if no trip was cached or key doesn't match. */
    fun take(startDate: String): Trip? {
        val trip = cached
        cached = null
        return if (trip?.startDate == startDate) trip else null
    }
}
