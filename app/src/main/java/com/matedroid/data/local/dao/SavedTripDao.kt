package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.matedroid.data.local.entity.SavedTrip
import com.matedroid.data.local.entity.SavedTripConsumedFingerprint
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.local.entity.SavedTripWithLegs

@Dao
abstract class SavedTripDao {

    @Transaction
    @Query("SELECT * FROM saved_trips WHERE carId = :carId ORDER BY id ASC")
    abstract suspend fun getAllWithLegs(carId: Int): List<SavedTripWithLegs>

    @Transaction
    @Query("SELECT * FROM saved_trips WHERE id = :tripId")
    abstract suspend fun getWithLegs(tripId: Long): SavedTripWithLegs?

    @Query("""
        SELECT fp.fingerprint FROM saved_trip_consumed_fingerprints fp
        INNER JOIN saved_trips st ON fp.savedTripId = st.id
        WHERE st.carId = :carId
    """)
    abstract suspend fun getAllConsumedFingerprintsForCar(carId: Int): List<String>

    @Insert
    abstract suspend fun insertTrip(trip: SavedTrip): Long

    @Insert
    abstract suspend fun insertLegs(legs: List<SavedTripLeg>)

    @Insert
    abstract suspend fun insertConsumedFingerprints(rows: List<SavedTripConsumedFingerprint>)

    /** Atomically insert a trip and its legs, returning the generated trip id. */
    @Transaction
    open suspend fun insertTripWithLegs(trip: SavedTrip, legs: (Long) -> List<SavedTripLeg>): Long {
        val tripId = insertTrip(trip)
        val resolved = legs(tripId)
        if (resolved.isNotEmpty()) insertLegs(resolved)
        return tripId
    }

    @Query("DELETE FROM saved_trips WHERE id = :tripId")
    abstract suspend fun deleteTrip(tripId: Long)

    // === Edit/merge helpers (PR 2) ===

    @Query("DELETE FROM saved_trip_legs WHERE tripId = :tripId")
    abstract suspend fun deleteLegs(tripId: Long)

    @Query("UPDATE saved_trips SET source = :source, updatedAt = :updatedAt WHERE id = :tripId")
    abstract suspend fun updateSource(tripId: Long, source: String, updatedAt: Long)

    @Query("SELECT name FROM saved_trips WHERE id = :tripId")
    abstract suspend fun getName(tripId: Long): String?

    @Query("UPDATE saved_trips SET name = :name, updatedAt = :updatedAt WHERE id = :tripId")
    abstract suspend fun updateName(tripId: Long, name: String?, updatedAt: Long)

    @Query("SELECT fingerprint FROM saved_trip_consumed_fingerprints WHERE savedTripId = :tripId")
    abstract suspend fun getConsumedFingerprints(tripId: Long): List<String>

    /** Drive/charge IDs already referenced by any saved trip belonging to [carId]. */
    @Query("""
        SELECT DISTINCT legId FROM saved_trip_legs
        WHERE legType = :legType
          AND tripId IN (SELECT id FROM saved_trips WHERE carId = :carId)
    """)
    abstract suspend fun getUsedLegIds(carId: Int, legType: String): List<Int>

    /** Atomically replace all legs of a trip. Used when adding legs (re-numbers positions). */
    @Transaction
    open suspend fun replaceLegs(tripId: Long, newLegs: List<SavedTripLeg>) {
        deleteLegs(tripId)
        if (newLegs.isNotEmpty()) insertLegs(newLegs)
    }
}
