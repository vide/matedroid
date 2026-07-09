package com.matedroid.widget

import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter

data class TripSummaryWidgetMetrics(
    val driveCount: Int,
    val drivingDays: Int,
    val totalDistance: Double,
    val totalEnergy: Double,
    val avgEfficiency: Double,
    val totalCost: Double,
    val chargesWithCost: Int,
)

object TripSummaryWidgetFormatter {

    fun format(
        carId: Int,
        carName: String,
        metrics: TripSummaryWidgetMetrics,
        currencySymbol: String,
        noValue: String,
        updatedValue: String,
        units: Units? = null,
    ): TripSummaryWidgetDisplayData {
        val hasCostData = metrics.chargesWithCost > 0
        val costValue = if (hasCostData) {
            UnitFormatter.formatCost(metrics.totalCost, currencySymbol)
        } else {
            noValue
        }
        val costPerDistance = if (hasCostData && metrics.totalDistance > 0) {
            UnitFormatter.formatCost((metrics.totalCost / metrics.totalDistance) * 100, currencySymbol)
        } else {
            noValue
        }
        val efficiencyValue = if (metrics.avgEfficiency > 0) {
            UnitFormatter.formatEfficiency(metrics.avgEfficiency, units = units, decimals = 0)
        } else {
            noValue
        }

        return TripSummaryWidgetDisplayData(
            carId = carId,
            carName = carName,
            hasDrives = metrics.driveCount > 0,
            distanceValue = UnitFormatter.formatDistance(metrics.totalDistance, units = units, decimals = 0),
            driveCountValue = "%,d".format(metrics.driveCount),
            drivingDaysValue = "%,d".format(metrics.drivingDays),
            energyValue = UnitFormatter.formatEnergy(metrics.totalEnergy),
            efficiencyValue = efficiencyValue,
            costValue = costValue,
            costPerDistanceValue = costPerDistance,
            distanceUnit = UnitFormatter.getDistanceUnit(units),
            updatedValue = updatedValue,
        )
    }
}
