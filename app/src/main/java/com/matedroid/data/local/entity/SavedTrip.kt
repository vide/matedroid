package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A trip persisted as a first-class entity.
 *
 * Auto-detected trips are saved on first detection with source = AUTO_DETECTED.
 * Once persisted, the trip acts as the source of truth for the trips list.
 * Future user edits (adding legs, merging with another trip) transition the
 * source to USER_EDITED or USER_MERGED.
 *
 * Metrics (distance, duration, energy, etc.) are NOT stored here — they are
 * always derived from the referenced drive/charge legs at read time.
 */
@Entity(
    tableName = "saved_trips",
    indices = [Index(value = ["carId"])]
)
data class SavedTrip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Int,
    val name: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val SOURCE_AUTO_DETECTED = "AUTO_DETECTED"
        const val SOURCE_USER_EDITED = "USER_EDITED"
        const val SOURCE_USER_MERGED = "USER_MERGED"
    }
}
