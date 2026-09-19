package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.TripRouteCache

@Dao
interface TripRouteCacheDao {

    @Query("SELECT * FROM trip_route_cache WHERE tripKey = :tripKey ORDER BY segmentIndex ASC")
    suspend fun getSegments(tripKey: String): List<TripRouteCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<TripRouteCache>)

    @Query("SELECT * FROM trip_route_cache ORDER BY tripKey ASC, segmentIndex ASC")
    suspend fun getAll(): List<TripRouteCache>

    /** Number of trips with a cached route, not the number of segments. */
    @Query("SELECT COUNT(DISTINCT tripKey) FROM trip_route_cache")
    suspend fun countTrips(): Int

    @Query("DELETE FROM trip_route_cache")
    suspend fun deleteAll()
}
