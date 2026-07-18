package com.matedroid.widget

import com.matedroid.data.api.models.Units
import com.matedroid.domain.CostAnalyticsMetrics
import com.matedroid.domain.TripCostCoverage
import com.matedroid.domain.model.UnitFormatter

/**
 * Turn a [CostAnalyticsMetrics] into a preformatted
 * [CostSummaryWidgetDisplayData] snapshot for the Cost Summary widget.
 *
 * Cost/100 distance is intentionally left as [noValue] unless every charge in
 * the range is priced. The widget UI can then honestly show a partial coverage
 * badge without inventing a rate that would mislead the user.
 */
object CostSummaryWidgetFormatter {

    fun format(
        carId: Int,
        carName: String,
        periodLabel: String,
        metrics: CostAnalyticsMetrics,
        currencySymbol: String,
        noValue: String,
        updatedValue: String,
        units: Units?,
    ): CostSummaryWidgetDisplayData {
        val hasCharges = metrics.chargeCount > 0

        val coverage = when (metrics.costCoverage) {
            TripCostCoverage.None -> if (metrics.chargeCount == 0) {
                CostSummaryWidgetCoverage.None
            } else {
                CostSummaryWidgetCoverage.Missing
            }
            TripCostCoverage.Partial -> CostSummaryWidgetCoverage.Partial
            TripCostCoverage.Complete -> CostSummaryWidgetCoverage.Complete
        }

        val costValue = metrics.totalKnownCost
            ?.let { UnitFormatter.formatCost(it, currencySymbol) }
            ?: noValue

        val costPerDistanceValue = metrics.costPer100Distance
            ?.let { UnitFormatter.formatCost(it, currencySymbol) }
            ?: noValue

        val avgCostPerKwhValue = metrics.avgCostPerKwh
            ?.let { UnitFormatter.formatCost(it, currencySymbol, perKwh = true) }
            ?: noValue

        return CostSummaryWidgetDisplayData(
            carId = carId,
            carName = carName,
            periodLabel = periodLabel,
            hasCharges = hasCharges,
            costValue = costValue,
            costPerDistanceValue = costPerDistanceValue,
            avgCostPerKwhValue = avgCostPerKwhValue,
            energyValue = UnitFormatter.formatEnergy(metrics.energyAddedTotal),
            distanceUnit = UnitFormatter.getDistanceUnit(units),
            costCoverage = coverage,
            chargesWithCost = metrics.pricedCount,
            chargeCount = metrics.chargeCount,
            updatedValue = updatedValue,
        )
    }
}
