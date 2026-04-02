package com.matedroid.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Caches one drive leg's GPS route for a trip so subsequent views skip API calls.
 *
 * Each row holds one segment (drive leg). The tripKey groups all segments for a trip
 * and is a hash of the sorted drive IDs, ensuring cache invalidation if trip
 * composition changes. segmentIndex preserves the original leg order.
 *
 * Route data is stored as a packed binary blob: pairs of (latitude, longitude) as
 * IEEE 754 doubles in big-endian order (16 bytes per point).
 */
@Entity(
    tableName = "trip_route_cache",
    primaryKeys = ["tripKey", "segmentIndex"]
)
data class TripRouteCache(
    val tripKey: String,
    val segmentIndex: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val segmentData: ByteArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TripRouteCache) return false
        return tripKey == other.tripKey && segmentIndex == other.segmentIndex
    }

    override fun hashCode(): Int = 31 * tripKey.hashCode() + segmentIndex
}
