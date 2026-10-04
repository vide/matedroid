package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Aggregated data computed from charge detail points.
 * Populated by fetching /charges/{id} and computing extremes.
 *
 * Critical for AC/DC ratio stats and max charge power records.
 */
@Entity(
    tableName = "charge_detail_aggregates",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSummary::class,
            parentColumns = ["chargeId"],
            childColumns = ["chargeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["carId"]),
        Index(value = ["chargeId"])
    ]
)
// Ships verbatim inside backup files, hence the Moshi adapter — see BackupSection.STATS.
@JsonClass(generateAdapter = true)
data class ChargeDetailAggregate(
    @PrimaryKey
    val chargeId: Int,
    val carId: Int,

    // Schema version for selective reprocessing
    val schemaVersion: Int,
    val computedAt: Long,

    // === Charger type (critical for AC/DC ratio) ===
    val isFastCharger: Boolean,     // DC = true, AC = false
    val fastChargerBrand: String?,  // e.g., "Tesla", "Ionity", etc.
    val connectorType: String?,     // e.g., "CCS", "IEC", etc.

    // === Power extremes ===
    val maxChargerPower: Int?,      // kW (peak charging power)
    val maxChargerVoltage: Int?,    // V
    val maxChargerCurrent: Int?,    // A
    val chargerPhases: Int?,        // 1-3 for AC

    // === Temperature ===
    val maxOutsideTemp: Double?,    // Celsius
    val minOutsideTemp: Double?,    // Celsius

    // === Metadata ===
    val chargePointCount: Int,      // Total data points in this charge

    // === Location (V4) ===
    // Extracted from charge summary coordinates via geocoding cache
    val countryCode: String? = null,    // ISO 3166-1 alpha-2 (e.g., "IT", "US")
    val countryName: String? = null,    // Full name (e.g., "Italy", "United States")
    val regionName: String? = null,     // State/region (e.g., "Lazio", "California")
    val city: String? = null,           // City/town (e.g., "Rome", "San Francisco")

    // === Future extensibility ===
    val extraJson: String? = null
)
