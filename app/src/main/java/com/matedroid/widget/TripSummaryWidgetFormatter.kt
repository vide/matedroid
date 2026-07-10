package com.matedroid.widget

import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

data class TripSummaryWidgetMetrics(
    val driveCount: Int,
    val drivingDays: Int,
    val totalDistance: Double,
    val totalEnergy: Double,
    val avgEfficiency: Double,
    val totalCost: Double,
    val chargesWithCost: Int,
    val chargeCount: Int,
    val previousDistance: Double,
)

object TripSummaryWidgetFormatter {

    fun format(
        carId: Int,
        carName: String,
        periodLabel: String,
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
        val distanceTrend = when {
            metrics.driveCount <= 0 -> TripSummaryWidgetTrend.None
            metrics.previousDistance <= 0 -> TripSummaryWidgetTrend.New
            else -> {
                val percent = ((metrics.totalDistance / metrics.previousDistance) - 1.0) * 100.0
                when (percent.roundToInt()) {
                    0 -> TripSummaryWidgetTrend.Same
                    in 1..Int.MAX_VALUE -> TripSummaryWidgetTrend.Up
                    else -> TripSummaryWidgetTrend.Down
                }
            }
        }
        val distanceTrendPercent = if (metrics.previousDistance > 0) {
            abs((((metrics.totalDistance / metrics.previousDistance) - 1.0) * 100.0).roundToInt())
        } else {
            0
        }
        val costCoverage = when {
            metrics.chargeCount <= 0 -> TripSummaryWidgetCostCoverage.None
            metrics.chargesWithCost <= 0 -> TripSummaryWidgetCostCoverage.Missing
            metrics.chargesWithCost < metrics.chargeCount -> TripSummaryWidgetCostCoverage.Partial
            else -> TripSummaryWidgetCostCoverage.Complete
        }

        return TripSummaryWidgetDisplayData(
            carId = carId,
            carName = carName,
            periodLabel = periodLabel,
            hasDrives = metrics.driveCount > 0,
            distanceValue = UnitFormatter.formatDistance(metrics.totalDistance, units = units, decimals = 0),
            distanceTrend = distanceTrend,
            distanceTrendPercent = distanceTrendPercent,
            driveCountValue = "%,d".format(metrics.driveCount),
            drivingDaysValue = "%,d".format(metrics.drivingDays),
            energyValue = UnitFormatter.formatEnergy(metrics.totalEnergy),
            efficiencyValue = efficiencyValue,
            costValue = costValue,
            costCoverage = costCoverage,
            chargesWithCost = metrics.chargesWithCost,
            chargeCount = metrics.chargeCount,
            costPerDistanceValue = costPerDistance,
            distanceUnit = UnitFormatter.getDistanceUnit(units),
            updatedValue = updatedValue,
        )
    }
}
