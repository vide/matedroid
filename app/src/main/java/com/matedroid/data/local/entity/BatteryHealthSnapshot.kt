package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Time-series record of a car's battery health, taken from the TeslamateAPI
 * `/battery_health` endpoint every time the Battery screen loads (with a
 * per-day debounce so we don't accumulate near-identical rows).
 *
 * All numeric fields are stored raw as returned by the API — no unit
 * conversion. When displayed, formatters attach the correct label based on
 * the user's current unit preferences.
 */
@Entity(
    tableName = "battery_health_snapshots",
    indices = [Index(value = ["carId", "recordedAt"])]
)
data class BatteryHealthSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Int,
    /** Epoch milliseconds when the snapshot was captured on the device. */
    val recordedAt: Long,
    /** Max range at full charge (km or mi — as returned by the API). */
    val maxRange: Double? = null,
    /** Current range (km or mi). */
    val currentRange: Double? = null,
    /** Original usable capacity (kWh). */
    val maxCapacity: Double? = null,
    /** Current usable capacity after degradation (kWh). */
    val currentCapacity: Double? = null,
    /** Rated efficiency (Wh/km or Wh/mi). */
    val ratedEfficiency: Double? = null,
    /** Battery health percentage 0..100 as reported by the API. */
    val batteryHealthPercentage: Double? = null,
)
