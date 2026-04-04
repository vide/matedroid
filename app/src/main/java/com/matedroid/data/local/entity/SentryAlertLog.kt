package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent log of sentry alert events. Each row represents a deduplicated
 * alert (one per 65-second debounce window), matching the notification rules.
 */
@Entity(
    tableName = "sentry_alert_log",
    indices = [Index(value = ["carId", "detectedAt"])]
)
data class SentryAlertLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Int,
    val detectedAt: Long,
    val sessionStartedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)
