package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Fingerprints of auto-detected trips that are now represented by a saved trip.
 *
 * When the user edits or merges trips, their original auto-detected fingerprints
 * are stored here so TripDetector's output can be filtered — we don't want to
 * re-emit a trip that the user has already folded into a saved one.
 *
 * Cascade delete means: removing a SavedTrip releases its consumed fingerprints,
 * letting the auto-detector re-emit the originals on the next load. This gives
 * us implicit undo for edits/merges.
 *
 * Unused in PR 1 (auto-detected saves don't need consumed entries — their own
 * fingerprint equals the trip's current fingerprint). Populated by PR 2 when
 * edits and merges land.
 */
@Entity(
    tableName = "saved_trip_consumed_fingerprints",
    primaryKeys = ["savedTripId", "fingerprint"],
    foreignKeys = [
        ForeignKey(
            entity = SavedTrip::class,
            parentColumns = ["id"],
            childColumns = ["savedTripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["fingerprint"])]
)
data class SavedTripConsumedFingerprint(
    val savedTripId: Long,
    val fingerprint: String
)
