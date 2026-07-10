package com.matedroid.widget

/**
 * Persisted display snapshot for the read-only trip summary widget.
 *
 * The widget renderer reads only these preformatted values from Glance
 * preferences. The worker owns all database access and formatting.
 */
data class TripSummaryWidgetDisplayData(
    val carId: Int,
    val carName: String,
    val periodLabel: String,
    val hasDrives: Boolean,
    val distanceValue: String,
    val driveCountValue: String,
    val drivingDaysValue: String,
    val energyValue: String,
    val efficiencyValue: String,
    val costValue: String,
    val costPerDistanceValue: String,
    val distanceUnit: String,
    val updatedValue: String,
)
