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
}
