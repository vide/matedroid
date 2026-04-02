package com.matedroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.GeocodeCacheDao
import com.matedroid.data.local.dao.GeocodeProgressDao
import com.matedroid.data.local.dao.GeocodeQueueDao
import com.matedroid.data.local.dao.SentryAlertLogDao
import com.matedroid.data.local.dao.SyncStateDao
import com.matedroid.data.local.dao.TripCountryCacheDao
import com.matedroid.data.local.dao.TripRouteCacheDao
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.GeocodeCache
import com.matedroid.data.local.entity.GeocodeProgress
import com.matedroid.data.local.entity.GeocodeQueueItem
import com.matedroid.data.local.entity.SentryAlertLog
import com.matedroid.data.local.entity.SyncState
import com.matedroid.data.local.entity.TripCountryCache
import com.matedroid.data.local.entity.TripRouteCache

/**
 * Room database for storing stats data locally.
 *
 * Tables:
 * - sync_state: Tracks sync progress per car
 * - drives_summary: Drive list data for Quick Stats
 * - charges_summary: Charge list data for Quick Stats
 * - drive_detail_aggregates: Computed aggregates for Deep Stats
 * - charge_detail_aggregates: Computed aggregates for Deep Stats
 *
 * Storage estimate: ~10 MB for heavy user (15k drives, 8k charges)
 */
@Database(
    entities = [
        SyncState::class,
        DriveSummary::class,
        ChargeSummary::class,
        DriveDetailAggregate::class,
        ChargeDetailAggregate::class,
        GeocodeCache::class,
        GeocodeQueueItem::class,
        GeocodeProgress::class,
        SentryAlertLog::class,
        TripRouteCache::class,
        TripCountryCache::class
    ],
    version = 10,
    exportSchema = true
)
abstract class StatsDatabase : RoomDatabase() {

    abstract fun syncStateDao(): SyncStateDao
    abstract fun driveSummaryDao(): DriveSummaryDao
    abstract fun chargeSummaryDao(): ChargeSummaryDao
    abstract fun aggregateDao(): AggregateDao
    abstract fun geocodeCacheDao(): GeocodeCacheDao
    abstract fun geocodeQueueDao(): GeocodeQueueDao
    abstract fun geocodeProgressDao(): GeocodeProgressDao
    abstract fun sentryAlertLogDao(): SentryAlertLogDao
    abstract fun tripRouteCacheDao(): TripRouteCacheDao
    abstract fun tripCountryCacheDao(): TripCountryCacheDao

    companion object {
        const val DATABASE_NAME = "matedroid_stats.db"

        /** Migration from V1 to V2: Add start/end elevation for net climb calculation */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add startElevation and endElevation columns to drive_detail_aggregates
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startElevation INTEGER")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN endElevation INTEGER")
            }
        }

        /** Migration from V2 to V3: Fix isFastCharger using Teslamate's charger_phases logic */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Teslamate logic: DC charging has charger_phases = 0 or null
                // AC charging has charger_phases = 1, 2, or 3
                db.execSQL("""
                    UPDATE charge_detail_aggregates
                    SET isFastCharger = CASE
                        WHEN chargerPhases IS NULL OR chargerPhases = 0 THEN 1
                        ELSE 0
                    END
                """)
            }
        }

        /** Migration from V3 to V4: Add country fields to drive aggregates */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startCountryCode TEXT")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startCountryName TEXT")
            }
        }

        /** Migration from V4 to V5: Add geocoding cache tables and location fields */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create geocode_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS geocode_cache (
                        gridLat INTEGER NOT NULL,
                        gridLon INTEGER NOT NULL,
                        countryCode TEXT,
                        countryName TEXT,
                        regionName TEXT,
                        city TEXT,
                        cachedAt INTEGER NOT NULL,
                        PRIMARY KEY (gridLat, gridLon)
                    )
                """)

                // Create geocode_queue table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS geocode_queue (
                        gridLat INTEGER NOT NULL,
                        gridLon INTEGER NOT NULL,
                        carId INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        addedAt INTEGER NOT NULL,
                        attempts INTEGER NOT NULL DEFAULT 0,
                        lastAttemptAt INTEGER,
                        PRIMARY KEY (gridLat, gridLon)
                    )
                """)

                // Create geocode_progress table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS geocode_progress (
                        carId INTEGER NOT NULL,
                        totalLocations INTEGER NOT NULL DEFAULT 0,
                        processedLocations INTEGER NOT NULL DEFAULT 0,
                        lastUpdatedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (carId)
                    )
                """)

                // Add coordinate and region/city columns to drive_detail_aggregates
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startLatitude REAL")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startLongitude REAL")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startRegionName TEXT")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN startCity TEXT")

                // Add location columns to charge_detail_aggregates
                db.execSQL("ALTER TABLE charge_detail_aggregates ADD COLUMN countryCode TEXT")
                db.execSQL("ALTER TABLE charge_detail_aggregates ADD COLUMN countryName TEXT")
                db.execSQL("ALTER TABLE charge_detail_aggregates ADD COLUMN regionName TEXT")
                db.execSQL("ALTER TABLE charge_detail_aggregates ADD COLUMN city TEXT")
            }
        }

        /** Migration from V5 to V6: Add sentry alert history log table */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sentry_alert_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        carId INTEGER NOT NULL,
                        detectedAt INTEGER NOT NULL,
                        sessionStartedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_sentry_alert_log_carId_detectedAt
                    ON sentry_alert_log (carId, detectedAt)
                """)
            }
        }

        /** Migration from V6 to V7: Add trip route cache table (one row per segment) */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_route_cache (
                        tripKey TEXT NOT NULL,
                        segmentIndex INTEGER NOT NULL,
                        segmentJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY (tripKey, segmentIndex)
                    )
                """)
            }
        }

        /** Migration from V7 to V8: Recreate trip_route_cache with composite PK */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS trip_route_cache")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_route_cache (
                        tripKey TEXT NOT NULL,
                        segmentIndex INTEGER NOT NULL,
                        segmentJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY (tripKey, segmentIndex)
                    )
                """)
            }
        }

        /**
         * Migration from V8 to V9:
         * - Recreate trip_route_cache with binary BLOB instead of JSON text
         * - Add end coordinates to drive_detail_aggregates for trip country resolution
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS trip_route_cache")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_route_cache (
                        tripKey TEXT NOT NULL,
                        segmentIndex INTEGER NOT NULL,
                        segmentData BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY (tripKey, segmentIndex)
                    )
                """)
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN endLatitude REAL")
                db.execSQL("ALTER TABLE drive_detail_aggregates ADD COLUMN endLongitude REAL")
            }
        }

        /** Migration from V9 to V10: Add trip country cache table */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_country_cache (
                        tripKey TEXT NOT NULL,
                        countries TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY (tripKey)
                    )
                """)
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
    }
}
