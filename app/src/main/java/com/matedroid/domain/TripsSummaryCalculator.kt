package com.matedroid.domain

import com.matedroid.domain.model.Trip

enum class TripCostCoverage {
    None,
    Partial,
    Complete,
}

data class TripsSummaryMetrics(
    val tripCount: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDrivingMinutes: Int = 0,
    val totalEnergyConsumed: Double = 0.0,
    val totalEnergyCharged: Double = 0.0,
    val averageEfficiency: Double? = null,
    /** Sum of TeslaMate-recorded charge costs only. */
    val totalChargeCost: Double? = null,
    /** Uses [totalEffectiveCost] when coverage is Complete. */
    val costPer100Distance: Double? = null,
    val pricedChargeCount: Int = 0,
    val chargeCount: Int = 0,
    val costCoverage: TripCostCoverage = TripCostCoverage.None,
    /** Sum of estimated costs for null-cost charges. */
    val totalEstimatedCost: Double? = null,
    /** Sum of recorded + estimated costs. Null when neither is available. */
    val totalEffectiveCost: Double? = null,
    val estimatedChargeCount: Int = 0,
    val recordedChargeCount: Int = 0,
    val missingChargeCount: Int = 0,
    /** True when at least one charge cost was estimated from utility rates. */
    val usesEstimates: Boolean = false,
)

object TripsSummaryCalculator {

    /**
     * Backwards-compatible entry point: aggregates trips without applying any
     * utility-rate estimation. Existing callers keep the recorded-only view.
     */
    fun calculate(trips: List<Trip>): TripsSummaryMetrics =
        calculate(trips = trips, rates = UtilityRates(), dcChargeIds = emptySet())

    /**
     * Aggregate a filtered list of trips into summary metrics, optionally
     * estimating missing charge costs from local utility [rates]. Charges
     * whose id is in [dcChargeIds] are treated as DC/fast for rate selection.
     */
    fun calculate(
        trips: List<Trip>,
        rates: UtilityRates,
        dcChargeIds: Set<Int>,
    ): TripsSummaryMetrics {
        val totalDistance = trips.sumOf { it.totalDistance }
        val totalEnergyConsumed = trips.sumOf { it.totalEnergyConsumed }
        val charges = trips.flatMap { it.charges }

        var recordedTotal = 0.0
        var recordedCount = 0
        var estimatedTotal = 0.0
        var estimatedCount = 0
        var missingCount = 0

        for (charge in charges) {
            val isDc = charge.chargeId in dcChargeIds
            val resolved = CostResolver.resolveChargeCost(
                cost = charge.cost,
                energyAdded = charge.energyAdded,
                isDc = isDc,
                rates = rates,
            )
            when (resolved.source) {
                CostSource.Recorded -> {
                    recordedTotal += resolved.amount ?: 0.0
                    recordedCount++
                }
                CostSource.Estimated -> {
                    estimatedTotal += resolved.amount ?: 0.0
                    estimatedCount++
                }
                CostSource.Missing -> missingCount++
            }
        }

        val totalChargeCost = if (recordedCount == 0) null else recordedTotal
        val totalEstimatedCost = if (estimatedCount == 0) null else estimatedTotal
        val totalEffectiveCost = when {
            recordedCount == 0 && estimatedCount == 0 -> null
            recordedCount == 0 -> estimatedTotal
            estimatedCount == 0 -> recordedTotal
            else -> recordedTotal + estimatedTotal
        }

        val anyCost = recordedCount + estimatedCount
        val costCoverage = when {
            charges.isEmpty() || anyCost == 0 -> TripCostCoverage.None
            missingCount == 0 -> TripCostCoverage.Complete
            else -> TripCostCoverage.Partial
        }

        return TripsSummaryMetrics(
            tripCount = trips.size,
            totalDistance = totalDistance,
            totalDrivingMinutes = trips.sumOf { it.totalDrivingDurationMin },
            totalEnergyConsumed = totalEnergyConsumed,
            totalEnergyCharged = trips.sumOf { it.totalEnergyCharged },
            averageEfficiency = totalDistance
                .takeIf { it > 0.0 }
                ?.let { totalEnergyConsumed * 1000.0 / it },
            totalChargeCost = totalChargeCost,
            // Cost / 100 distance is only honest when every charge in range has
            // a cost (recorded or estimated). Known totals still surface under
            // Partial; the rate stays unavailable.
            costPer100Distance = totalEffectiveCost
                ?.takeIf { costCoverage == TripCostCoverage.Complete && totalDistance > 0.0 }
                ?.let { it * 100.0 / totalDistance },
            pricedChargeCount = recordedCount,
            chargeCount = charges.size,
            costCoverage = costCoverage,
            totalEstimatedCost = totalEstimatedCost,
            totalEffectiveCost = totalEffectiveCost,
            estimatedChargeCount = estimatedCount,
            recordedChargeCount = recordedCount,
            missingChargeCount = missingCount,
            usesEstimates = estimatedCount > 0,
        )
    }
}
