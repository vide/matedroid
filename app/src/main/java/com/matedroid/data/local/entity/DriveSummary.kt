package com.matedroid.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Drive summary data from /drives list endpoint.
 * Contains all fields needed for Quick Stats.
 */
@Immutable
@Entity(
    tableName = "drives_summary",
    indices = [
        Index(value = ["carId"]),
        Index(value = ["carId", "startDate"])
    ]
)
data class DriveSummary(
    @PrimaryKey
    val driveId: Int,
    val carId: Int,

    // Timing
    val startDate: String,
    val endDate: String,
    val durationMin: Int,

    // Location
    val startAddress: String,
    val endAddress: String,

    // Distance & Speed
    val distance: Double,           // km
    val speedMax: Int,              // km/h
    val speedAvg: Int,              // km/h

    // Power
    val powerMax: Int,              // kW (acceleration)
    val powerMin: Int,              // kW (regen, negative)

    // Battery
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,

    // Temperature (averages from list endpoint)
    val outsideTempAvg: Double?,
    val insideTempAvg: Double?,

    // Energy
    val energyConsumed: Double?,    // kWh

    // Computed efficiency (Wh/km)
    val efficiency: Double?
)
