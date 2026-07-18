package com.matedroid.widget

/**
 * Persisted display snapshot for the read-only cost summary widget.
 *
 * The widget composable reads only these preformatted values from Glance
 * preferences. All database access and formatting live in
 * [CostSummaryWidgetUpdateWorker] + [CostSummaryWidgetFormatter].
 */
data class CostSummaryWidgetDisplayData(
    val carId: Int,
    val carName: String,
    val periodLabel: String,
    val hasCharges: Boolean,
    val costValue: String,
    val costPerDistanceValue: String,
    val avgCostPerKwhValue: String,
    val energyValue: String,
    val distanceUnit: String,
    val costCoverage: CostSummaryWidgetCoverage,
    val chargesWithCost: Int,
    val chargeCount: Int,
    val updatedValue: String,
)

/**
 * Coverage bucket for a widget snapshot — same semantics as
 * [com.matedroid.domain.TripCostCoverage] but kept local so the widget can be
 * built without pulling the domain enum through Glance preferences.
 */
enum class CostSummaryWidgetCoverage {
    /** No charges in the range at all. */
    None,

    /** Some charges recorded, but none carry a cost yet. */
    Missing,

    /** At least one but not every charge has a cost. Rate is unavailable. */
    Partial,

    /** Every charge in the range has a cost — cost/distance is honest. */
    Complete,
}
