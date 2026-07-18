package com.matedroid.domain

import com.matedroid.domain.model.Trip

/**
 * Higher-order per-trip insights layered on top of [TripsSummaryCalculator].
 *
 * The summary answers "what did these trips add up to?"; this calculator answers
 * "which trip stood out?" — most/least efficient, cheapest/most expensive per 100 dist,
 * longest, biggest charging-overhead share, plus a couple of ensemble numbers
 * (energy gap and average charging minutes per trip).
 *
 * Metrics are exposed as typed [Trip] references + raw [Double]s so callers can
 * format with [com.matedroid.domain.model.UnitFormatter] rather than parsing pre-rendered strings.
 * Following the same "honest cost" rule as the summary, the cost-per-100
 * highlights only consider trips whose charges are all priced.
 */
data class TripIntelligence(
    /** Lowest Wh/dist among trips with distance>0 and a defined efficiency. */
    val mostEfficient: Trip? = null,
    val leastEfficient: Trip? = null,
    /** Lowest cost/100 dist among trips with [TripCostCoverage.Complete] and distance>0. */
    val cheapestPer100: Trip? = null,
    val mostExpensivePer100: Trip? = null,
    /** Highest [Trip.totalDistance]. */
    val longestDistance: Trip? = null,
    /**
     * Trip with the highest ratio of `sum(charge.durationMin) / trip.totalDurationMin`.
     * Only trips with `totalDurationMin > 0` and at least one charge are considered.
     */
    val highestChargingOverhead: Trip? = null,
    /** Ratio in `[0.0, 1.0]` corresponding to [highestChargingOverhead]. */
    val highestChargingOverheadRatio: Double? = null,
    /** `sum(charged) - sum(consumed)` across the filtered set. null when the set is empty. */
    val energyGapKwh: Double? = null,
    /** Mean charging minutes per trip across the filtered set. null when the set is empty. */
    val avgChargingMinutesPerTrip: Double? = null,
)

object TripIntelligenceCalculator {

    fun calculate(trips: List<Trip>): TripIntelligence {
        if (trips.isEmpty()) return TripIntelligence()

        val efficiencyEligible = trips.filter { it.totalDistance > 0.0 && it.avgEfficiency != null }
        val mostEfficient = efficiencyEligible.minByOrNull { it.avgEfficiency!! }
        val leastEfficient = efficiencyEligible.maxByOrNull { it.avgEfficiency!! }

        val fullyPriced = trips.filter { trip ->
            trip.totalDistance > 0.0 &&
                trip.charges.isNotEmpty() &&
                trip.charges.all { it.cost != null } &&
                trip.totalChargeCost != null
        }
        // Rank on cost/100dist so trips of different length compare fairly, matching
        // the summary card's "Cost / 100 km" figure.
        val cheapestPer100 = fullyPriced.minByOrNull { costPer100(it) }
        val mostExpensivePer100 = fullyPriced.maxByOrNull { costPer100(it) }

        val longestDistance = trips.maxByOrNull { it.totalDistance }

        val overheadEligible = trips.filter { it.totalDurationMin > 0 && it.charges.isNotEmpty() }
        val overheadRanked = overheadEligible.maxByOrNull { chargingOverheadRatio(it) }
        val overheadRatio = overheadRanked?.let(::chargingOverheadRatio)

        val energyGap = trips.sumOf { it.totalEnergyCharged } - trips.sumOf { it.totalEnergyConsumed }
        val totalChargeMinutes = trips.sumOf { trip -> trip.charges.sumOf { it.durationMin } }
        val avgChargingMinutesPerTrip = totalChargeMinutes.toDouble() / trips.size

        return TripIntelligence(
            mostEfficient = mostEfficient,
            leastEfficient = leastEfficient,
            cheapestPer100 = cheapestPer100,
            mostExpensivePer100 = mostExpensivePer100,
            longestDistance = longestDistance,
            highestChargingOverhead = overheadRanked,
            highestChargingOverheadRatio = overheadRatio,
            energyGapKwh = energyGap,
            avgChargingMinutesPerTrip = avgChargingMinutesPerTrip,
        )
    }

    private fun costPer100(trip: Trip): Double =
        trip.totalChargeCost!! * 100.0 / trip.totalDistance

    private fun chargingOverheadRatio(trip: Trip): Double {
        val chargeMinutes = trip.charges.sumOf { it.durationMin }
        return chargeMinutes.toDouble() / trip.totalDurationMin.toDouble()
    }
}
