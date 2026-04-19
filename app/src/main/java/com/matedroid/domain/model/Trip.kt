package com.matedroid.domain.model

import androidx.compose.runtime.Immutable
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary

/**
 * A highway/road trip: a sequence of drive -> DC charge -> drive [-> DC charge -> drive ...]
 * Computed from existing drive and charge data, not persisted in the database.
 */
@Immutable
data class Trip(
    val drives: List<DriveSummary>,
    val charges: List<ChargeSummary>,
    val totalDistance: Double,
    val totalDrivingDurationMin: Int,
    val totalDurationMin: Int,
    val totalEnergyConsumed: Double,
    val totalEnergyCharged: Double,
    val totalChargeCost: Double?,
    val avgEfficiency: Double?,
    val maxSpeed: Int,
    val startAddress: String,
    val endAddress: String,
    val startDate: String,
    val endDate: String,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,
    /** User-chosen name. Null means fall back to the default "startCity → endCity" label. */
    val name: String? = null
)
