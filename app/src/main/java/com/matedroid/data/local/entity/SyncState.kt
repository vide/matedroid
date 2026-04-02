package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks sync progress for each car.
 * Allows resuming sync across app sessions.
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey
    val carId: Int,

    // Summary sync tracking (list endpoints)
    val lastDriveSyncAt: Long = 0,
    val lastChargeSyncAt: Long = 0,

    // Detail sync tracking (individual endpoints)
    val lastDriveDetailId: Int = 0,
    val lastChargeDetailId: Int = 0,

    // Schema version for aggregate reprocessing
    val detailSchemaVersion: Int = SchemaVersion.CURRENT,

    // Progress tracking for UI
    val totalDrivesToProcess: Int = 0,
    val totalChargesToProcess: Int = 0,
    val drivesProcessed: Int = 0,
    val chargesProcessed: Int = 0,

    // Phase tracking
    val summariesSynced: Boolean = false,
    val detailsSynced: Boolean = false
)

/**
 * Schema versioning for aggregate fields.
 * When adding new computed fields, increment CURRENT and
 * records with older versions will be reprocessed.
 */
object SchemaVersion {
    const val CURRENT = 5

    // Changelog:
    // V1 (initial): elevation, temp extremes, power, climate, charger info
    // V2: startElevation, endElevation for net climb calculation
    // V3: startCountryCode, startCountryName from reverse geocoding first position
    // V4: startRegionName, startCity for drives; countryCode, countryName, regionName, city for charges
    // V5: endLatitude, endLongitude for trip country resolution without API call
}
