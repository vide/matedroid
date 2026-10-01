package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.GeocodeCache

@Dao
interface GeocodeCacheDao {

    @Query("SELECT * FROM geocode_cache WHERE gridLat = :gridLat AND gridLon = :gridLon")
    suspend fun get(gridLat: Int, gridLon: Int): GeocodeCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: GeocodeCache)

    @Query("SELECT COUNT(*) FROM geocode_cache")
    suspend fun count(): Int

    // Full cache read for batch matching — the table holds one small row per ~1.1km grid
    // cell ever visited, so this is cheap and replaces per-cell point queries.
    @Query("SELECT * FROM geocode_cache")
    suspend fun getAll(): List<GeocodeCache>

    @Query("SELECT gridLat, gridLon FROM geocode_cache")
    suspend fun getAllGridKeys(): List<GridKey>
}

/** A grid-cell key (0.01° precision). */
data class GridKey(
    val gridLat: Int,
    val gridLon: Int
)
