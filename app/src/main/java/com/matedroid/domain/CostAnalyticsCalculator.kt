package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary

/**
 * Cost breakdown metrics for a list of [ChargeSummary] rows over a date range.
 *
 * Reuses [TripCostCoverage] so screens and widgets can render coverage tags with
 * the same vocabulary as the trip summary. Cost/100 distance is intentionally
 * left null unless every charge in range is priced ("Complete"); partial
 * pricing is still exposed as a known total, but never as a per-distance rate.
 */
data class CostAnalyticsMetrics(
    val chargeCount: Int = 0,
    val pricedCount: Int = 0,
    val zeroCostCount: Int = 0,
    val missingCostCount: Int = 0,
    val totalKnownCost: Double? = null,
    val energyAddedTotal: Double = 0.0,
    val energyAddedPriced: Double = 0.0,
    val avgCostPerKwh: Double? = null,
    val costPer100Distance: Double? = null,
    val totalDistance: Double = 0.0,
    val costCoverage: TripCostCoverage = TripCostCoverage.None,
)

object CostAnalyticsCalculator {

    /**
     * @param charges every charge in the selected range (priced or not).
     * @param totalDistance driven distance in the same range, already in the
     *   user's unit system (km or mi); no conversion is applied.
     */
    fun calculate(
        charges: List<ChargeSummary>,
        totalDistance: Double,
    ): CostAnalyticsMetrics {
        if (charges.isEmpty()) {
            return CostAnalyticsMetrics(totalDistance = totalDistance)
        }

        val priced = charges.filter { it.cost != null }
        val zeroCostCount = priced.count { (it.cost ?: 0.0) == 0.0 }
        val missingCostCount = charges.size - priced.size
        val totalKnownCost = if (priced.isEmpty()) null else priced.sumOf { it.cost ?: 0.0 }
        val energyAddedTotal = charges.sumOf { it.energyAdded }
        val energyAddedPriced = priced.sumOf { it.energyAdded }
        val coverage = when {
            priced.isEmpty() -> TripCostCoverage.None
            priced.size < charges.size -> TripCostCoverage.Partial
            else -> TripCostCoverage.Complete
        }

        val avgCostPerKwh = if (totalKnownCost != null && energyAddedPriced > 0.0) {
            totalKnownCost / energyAddedPriced
        } else {
            null
        }

        // Cost/100 distance is only honest when every charge in the range is priced.
        // A partially-priced total would understate the true rate, so we withhold it.
        val costPer100Distance = totalKnownCost
            ?.takeIf { coverage == TripCostCoverage.Complete && totalDistance > 0.0 }
            ?.let { it * 100.0 / totalDistance }

        return CostAnalyticsMetrics(
            chargeCount = charges.size,
            pricedCount = priced.size,
            zeroCostCount = zeroCostCount,
            missingCostCount = missingCostCount,
            totalKnownCost = totalKnownCost,
            energyAddedTotal = energyAddedTotal,
            energyAddedPriced = energyAddedPriced,
            avgCostPerKwh = avgCostPerKwh,
            costPer100Distance = costPer100Distance,
            totalDistance = totalDistance,
            costCoverage = coverage,
        )
    }
}
