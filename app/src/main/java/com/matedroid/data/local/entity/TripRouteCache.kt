package com.matedroid.data.local.entity

import androidx.room.Entity

/**
 * Caches one drive leg's GPS route for a trip so subsequent views skip API calls.
 *
 * Each row holds one segment (drive leg). The tripKey groups all segments for a trip
 * and is a hash of the sorted drive IDs, ensuring cache invalidation if trip
 * composition changes. segmentIndex preserves the original leg order.
 */
@Entity(
    tableName = "trip_route_cache",
    primaryKeys = ["tripKey", "segmentIndex"]
)
data class TripRouteCache(
    val tripKey: String,
    val segmentIndex: Int,
    val segmentJson: String,    // JSON array of {lat, lon} objects for one segment
    val createdAt: Long         // System.currentTimeMillis()
)
