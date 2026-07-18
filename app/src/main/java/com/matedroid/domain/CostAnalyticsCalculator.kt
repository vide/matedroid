package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary

/**
 * Cost breakdown metrics for a list of [ChargeSummary] rows over a date range.
 *
 * Reuses [TripCostCoverage] so screens and widgets can render coverage tags with
 * the same vocabulary as the trip summary. Cost/100 distance is intentionally
 * left null unless every charge in range has a numeric cost ("Complete") —
 * either recorded by TeslaMate or estimated from the user's utility rates.
 * Partial pricing is still exposed as a known total, but never as a per-distance
 * rate.
 */
data class CostAnalyticsMetrics(
    val chargeCount: Int = 0,
    /**
     * Charges with a TeslaMate-recorded cost (including 0.0 free sessions).
     * Same meaning as [recordedChargeCount]; kept for backwards compatibility.
     */
    val pricedCount: Int = 0,
    val zeroCostCount: Int = 0,
    /**
     * Charges still missing a numeric cost after attempting estimation
     * (no recorded cost AND no utility rate applied).
     */
    val missingCostCount: Int = 0,
    /** Sum of TeslaMate-recorded costs only. Null when none are recorded. */
    val totalKnownCost: Double? = null,
    val energyAddedTotal: Double = 0.0,
    /** Energy of charges that contributed to [totalKnownCost] (recorded only). */
    val energyAddedPriced: Double = 0.0,
    /** Average $/kWh from recorded charges only (never from estimates). */
    val avgCostPerKwh: Double? = null,
    /** Uses [totalEffectiveCost] when coverage is Complete. Null otherwise. */
    val costPer100Distance: Double? = null,
    val totalDistance: Double = 0.0,
    val costCoverage: TripCostCoverage = TripCostCoverage.None,
    /**
     * Sum of estimated costs (from utility rates) for charges that had no
     * recorded cost. Null when no estimates were produced.
     */
    val totalEstimatedCost: Double? = null,
    /** Sum of recorded + estimated costs. Null when neither is available. */
    val totalEffectiveCost: Double? = null,
    /** Number of charges whose cost came from a utility-rate estimate. */
    val estimatedChargeCount: Int = 0,
    /** Number of charges with a recorded TeslaMate cost (mirrors [pricedCount]). */
    val recordedChargeCount: Int = 0,
    /** True when at least one estimate contributed to [totalEffectiveCost]. */
    val usesEstimates: Boolean = false,
)

object CostAnalyticsCalculator {

    /**
     * Backwards-compatible calculator: computes metrics using only TeslaMate's
     * recorded costs. No utility-rate estimation is attempted.
     *
     * @param charges every charge in the selected range (priced or not).
     * @param totalDistance driven distance in the same range, already in the
     *   user's unit system (km or mi); no conversion is applied.
     */
    fun calculate(
        charges: List<ChargeSummary>,
        totalDistance: Double,
    ): CostAnalyticsMetrics = calculate(
        charges = charges,
        totalDistance = totalDistance,
        rates = UtilityRates(),
        dcChargeIds = emptySet(),
    )

    /**
     * Full calculator that optionally estimates missing costs from local
     * utility [rates]. Charges whose id appears in [dcChargeIds] are considered
     * DC/fast-charge for rate selection; all others are treated as AC/home.
     *
     * Estimates only run when the recorded cost is null AND a matching rate is
     * configured (with DC falling back to the home rate). Estimates never
     * overwrite a recorded cost, and never mutate the input rows.
     */
    fun calculate(
        charges: List<ChargeSummary>,
        totalDistance: Double,
        rates: UtilityRates,
        dcChargeIds: Set<Int>,
    ): CostAnalyticsMetrics {
        if (charges.isEmpty()) {
            return CostAnalyticsMetrics(totalDistance = totalDistance)
        }

        var recordedTotal = 0.0
        var recordedEnergy = 0.0
        var recordedCount = 0
        var zeroCostCount = 0
        var estimatedTotal = 0.0
        var estimatedCount = 0
        var missingCount = 0
        val energyAddedTotal = charges.sumOf { it.energyAdded }

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
                    val amount = resolved.amount ?: 0.0
                    recordedTotal += amount
                    recordedEnergy += charge.energyAdded
                    recordedCount++
                    if (amount == 0.0) zeroCostCount++
                }
                CostSource.Estimated -> {
                    estimatedTotal += resolved.amount ?: 0.0
                    estimatedCount++
                }
                CostSource.Missing -> {
                    missingCount++
                }
            }
        }

        val totalKnownCost = if (recordedCount == 0) null else recordedTotal
        val totalEstimatedCost = if (estimatedCount == 0) null else estimatedTotal
        // Effective cost = whichever of recorded + estimated is available.
        val totalEffectiveCost = when {
            recordedCount == 0 && estimatedCount == 0 -> null
            recordedCount == 0 -> estimatedTotal
            estimatedCount == 0 -> recordedTotal
            else -> recordedTotal + estimatedTotal
        }

        // Coverage: Complete when every charge has a numeric cost (recorded OR
        // estimated). Partial when at least one has any cost. None otherwise.
        val anyCost = recordedCount + estimatedCount
        val coverage = when {
            missingCount == 0 && anyCost > 0 -> TripCostCoverage.Complete
            anyCost == 0 -> TripCostCoverage.None
            else -> TripCostCoverage.Partial
        }

        // Average $/kWh stays honest by using only the recorded numerator and
        // the energy that fed it — estimates are excluded here on purpose so
        // this figure reflects what TeslaMate actually saw.
        val avgCostPerKwh = if (recordedCount > 0 && recordedEnergy > 0.0) {
            recordedTotal / recordedEnergy
        } else {
            null
        }

        // Cost/100 distance is only honest when every charge in the range has
        // a cost (recorded OR estimated) and we drove somewhere.
        val costPer100Distance = totalEffectiveCost
            ?.takeIf { coverage == TripCostCoverage.Complete && totalDistance > 0.0 }
            ?.let { it * 100.0 / totalDistance }

        return CostAnalyticsMetrics(
            chargeCount = charges.size,
            pricedCount = recordedCount,
            zeroCostCount = zeroCostCount,
            missingCostCount = missingCount,
            totalKnownCost = totalKnownCost,
            energyAddedTotal = energyAddedTotal,
            energyAddedPriced = recordedEnergy,
            avgCostPerKwh = avgCostPerKwh,
            costPer100Distance = costPer100Distance,
            totalDistance = totalDistance,
            costCoverage = coverage,
            totalEstimatedCost = totalEstimatedCost,
            totalEffectiveCost = totalEffectiveCost,
            estimatedChargeCount = estimatedCount,
            recordedChargeCount = recordedCount,
            usesEstimates = estimatedCount > 0,
        )
    }
}
