package com.matedroid.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A SavedTrip together with its ordered legs, loaded in a single query. */
data class SavedTripWithLegs(
    @Embedded val trip: SavedTrip,
    @Relation(
        parentColumn = "id",
        entityColumn = "tripId"
    )
    val legs: List<SavedTripLeg>
)
