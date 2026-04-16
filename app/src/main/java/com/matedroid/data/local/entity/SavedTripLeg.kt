package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One leg of a SavedTrip: either a drive or a charge, referenced by id.
 *
 * Legs are ordered by [position] within the parent trip. The index on
 * (legType, legId) allows efficient lookup of "which trips include drive/charge X",
 * needed when the edit UI checks whether a leg is already part of another trip.
 */
@Entity(
    tableName = "saved_trip_legs",
    primaryKeys = ["tripId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SavedTrip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["legType", "legId"])]
)
data class SavedTripLeg(
    val tripId: Long,
    val position: Int,
    val legType: String,
    val legId: Int
) {
    companion object {
        const val TYPE_DRIVE = "DRIVE"
        const val TYPE_CHARGE = "CHARGE"
    }
}
