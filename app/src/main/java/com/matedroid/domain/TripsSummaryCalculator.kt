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
    val totalChargeCost: Double? = null,
    val costPer100Distance: Double? = null,
    val pricedChargeCount: Int = 0,
    val chargeCount: Int = 0,
    val costCoverage: TripCostCoverage = TripCostCoverage.None,
)

object TripsSummaryCalculator {

    fun calculate(trips: List<Trip>): TripsSummaryMetrics {
        val totalDistance = trips.sumOf { it.totalDistance }
        val totalEnergyConsumed = trips.sumOf { it.totalEnergyConsumed }
        val charges = trips.flatMap { it.charges }
        val chargeCosts = charges.mapNotNull { it.cost }
        val totalChargeCost = chargeCosts
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val costCoverage = when {
            charges.isEmpty() || chargeCosts.isEmpty() -> TripCostCoverage.None
            chargeCosts.size < charges.size -> TripCostCoverage.Partial
            else -> TripCostCoverage.Complete
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
            costPer100Distance = totalChargeCost
                ?.takeIf { totalDistance > 0.0 }
                ?.let { it * 100.0 / totalDistance },
            pricedChargeCount = chargeCosts.size,
            chargeCount = charges.size,
            costCoverage = costCoverage,
        )
    }
}
