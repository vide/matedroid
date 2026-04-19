package com.matedroid.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Charge summary data from /charges list endpoint.
 * Contains all fields needed for Quick Stats.
 */
@Immutable
@Entity(
    tableName = "charges_summary",
    indices = [
        Index(value = ["carId"]),
        Index(value = ["carId", "startDate"])
    ]
)
data class ChargeSummary(
    @PrimaryKey
    val chargeId: Int,
    val carId: Int,

    // Timing
    val startDate: String,
    val endDate: String,
    val durationMin: Int,

    // Location
    val address: String,
    val latitude: Double,
    val longitude: Double,

    // Energy
    val energyAdded: Double,        // kWh
    val energyUsed: Double?,        // kWh (may be null)

    // Cost
    val cost: Double?,              // Currency (may be null)

    // Battery
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,

    // Temperature (average from list endpoint)
    val outsideTempAvg: Double?,

    // Odometer at charge time
    val odometer: Double
)
