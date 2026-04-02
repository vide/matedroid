package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches GPS route segments for a trip so subsequent views skip API calls.
 *
 * The tripKey is a hash of the sorted, concatenated drive IDs that make up the trip,
 * ensuring cache invalidation if trip composition changes.
 */
@Entity(tableName = "trip_route_cache")
data class TripRouteCache(
    @PrimaryKey val tripKey: String,
    val routeJson: String,      // JSON-serialized List<TripRouteSegment>
    val createdAt: Long         // System.currentTimeMillis()
)
