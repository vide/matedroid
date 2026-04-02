package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aggregated data computed from drive detail positions.
 * Populated by fetching /drives/{id} and computing extremes.
 *
 * This avoids storing raw position data (~500KB per drive)
 * while still enabling Deep Stats (~150 bytes per drive).
 */
@Entity(
    tableName = "drive_detail_aggregates",
    foreignKeys = [
        ForeignKey(
            entity = DriveSummary::class,
            parentColumns = ["driveId"],
            childColumns = ["driveId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["carId"]),
        Index(value = ["driveId"])
    ]
)
data class DriveDetailAggregate(
    @PrimaryKey
    val driveId: Int,
    val carId: Int,

    // Schema version for selective reprocessing
    val schemaVersion: Int,
    val computedAt: Long,

    // === Elevation ===
    val maxElevation: Int?,         // meters
    val minElevation: Int?,         // meters
    val startElevation: Int?,       // meters - first position elevation (V2)
    val endElevation: Int?,         // meters - last position elevation (V2)
    val elevationGain: Int?,        // Total meters climbed (accumulated)
    val elevationLoss: Int?,        // Total meters descended (accumulated)
    val hasElevationData: Boolean,  // False if all positions had null elevation

    // === Temperature extremes ===
    // More granular than summary averages
    val maxInsideTemp: Double?,     // Celsius
    val minInsideTemp: Double?,     // Celsius
    val maxOutsideTemp: Double?,    // Celsius
    val minOutsideTemp: Double?,    // Celsius

    // === Power extremes ===
    val maxPower: Int?,             // kW (peak acceleration)
    val minPower: Int?,             // kW (peak regen, negative)

    // === Climate usage ===
    val climateOnPositions: Int,    // Count of positions with climate on

    // === Metadata ===
    val positionCount: Int,         // Total positions in this drive

    // === Location (V3/V4) ===
    // Coordinates from first position (for geocoding cache lookup)
    val startLatitude: Double? = null,      // First position latitude (V4)
    val startLongitude: Double? = null,     // First position longitude (V4)
    // Extracted from reverse geocoding the first position
    val startCountryCode: String? = null,   // ISO 3166-1 alpha-2 (e.g., "IT", "US")
    val startCountryName: String? = null,   // Full name (e.g., "Italy", "United States")
    val startRegionName: String? = null,    // State/region (e.g., "Lazio", "California") (V4)
    val startCity: String? = null,          // City/town (e.g., "Rome", "San Francisco") (V4)

    // === End location (V9) ===
    // Coordinates from last position (for trip country resolution without API call)
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,

    // === Future extensibility ===
    // Store experimental data without schema changes
    val extraJson: String? = null
)
