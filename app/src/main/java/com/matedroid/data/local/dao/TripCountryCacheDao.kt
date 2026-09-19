package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.TripCountryCache

@Dao
interface TripCountryCacheDao {

    @Query("SELECT * FROM trip_country_cache WHERE tripKey = :tripKey")
    suspend fun get(tripKey: String): TripCountryCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TripCountryCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(caches: List<TripCountryCache>)

    @Query("SELECT * FROM trip_country_cache ORDER BY tripKey ASC")
    suspend fun getAll(): List<TripCountryCache>

    @Query("DELETE FROM trip_country_cache")
    suspend fun deleteAll()
}
