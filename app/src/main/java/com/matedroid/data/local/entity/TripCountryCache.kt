package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches the resolved country sequence for a trip.
 *
 * The tripKey is the same SHA-256 hash of sorted drive IDs used by TripRouteCache.
 * Countries are stored as a comma-separated string of ISO 3166-1 alpha-2 codes
 * in display order: [start, ...intermediates..., end].
 */
@Entity(tableName = "trip_country_cache")
data class TripCountryCache(
    @PrimaryKey
    val tripKey: String,
    val countries: String,      // e.g. "IT,FR,ES"
    val createdAt: Long
)
