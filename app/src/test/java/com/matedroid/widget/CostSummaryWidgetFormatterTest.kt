package com.matedroid.widget

import com.matedroid.data.api.models.Units
import com.matedroid.domain.CostAnalyticsMetrics
import com.matedroid.domain.TripCostCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostSummaryWidgetFormatterTest {

    private val units = Units(unitOfLength = "km")
    private val symbol = "€"
    private val noValue = "N/A"

    @Test
    fun `complete coverage exposes cost per distance`() {
        val metrics = CostAnalyticsMetrics(
            chargeCount = 3,
            pricedCount = 3,
            totalKnownCost = 30.0,
            energyAddedTotal = 60.0,
            energyAddedPriced = 60.0,
            avgCostPerKwh = 0.5,
            costPer100Distance = 6.0,
            totalDistance = 500.0,
            costCoverage = TripCostCoverage.Complete,
        )

        val data = CostSummaryWidgetFormatter.format(
            carId = 1,
            carName = "Model 3",
            periodLabel = "Last 30 days",
            metrics = metrics,
            currencySymbol = symbol,
            noValue = noValue,
            updatedValue = "Updated 10:00",
            units = units,
        )

        assertEquals(CostSummaryWidgetCoverage.Complete, data.costCoverage)
        assertTrue("cost value should be populated", data.costValue != noValue)
        assertTrue(
            "cost per distance should be populated when complete",
            data.costPerDistanceValue != noValue,
        )
        assertTrue(data.avgCostPerKwhValue != noValue)
        assertEquals(3, data.chargeCount)
        assertEquals(3, data.chargesWithCost)
    }

    @Test
    fun `partial coverage withholds cost per distance but keeps total`() {
        val metrics = CostAnalyticsMetrics(
            chargeCount = 3,
            pricedCount = 2,
            missingCostCount = 1,
            totalKnownCost = 20.0,
            energyAddedTotal = 60.0,
            energyAddedPriced = 40.0,
            avgCostPerKwh = 0.5,
            costPer100Distance = null,
            totalDistance = 500.0,
            costCoverage = TripCostCoverage.Partial,
        )

        val data = CostSummaryWidgetFormatter.format(
            carId = 1,
            carName = "Model 3",
            periodLabel = "Last 30 days",
            metrics = metrics,
            currencySymbol = symbol,
            noValue = noValue,
            updatedValue = "Updated 10:00",
            units = units,
        )

        assertEquals(CostSummaryWidgetCoverage.Partial, data.costCoverage)
        assertTrue("total cost should still surface", data.costValue != noValue)
        assertEquals(noValue, data.costPerDistanceValue)
        assertTrue("avg cost/kWh comes from priced energy only", data.avgCostPerKwhValue != noValue)
    }

    @Test
    fun `no priced charges surfaces missing coverage and no totals`() {
        val metrics = CostAnalyticsMetrics(
            chargeCount = 2,
            pricedCount = 0,
            missingCostCount = 2,
            totalKnownCost = null,
            energyAddedTotal = 30.0,
            energyAddedPriced = 0.0,
            avgCostPerKwh = null,
            costPer100Distance = null,
            totalDistance = 200.0,
            costCoverage = TripCostCoverage.None,
        )

        val data = CostSummaryWidgetFormatter.format(
            carId = 1,
            carName = "Model 3",
            periodLabel = "Last 30 days",
            metrics = metrics,
            currencySymbol = symbol,
            noValue = noValue,
            updatedValue = "Updated 10:00",
            units = units,
        )

        assertEquals(CostSummaryWidgetCoverage.Missing, data.costCoverage)
        assertEquals(noValue, data.costValue)
        assertEquals(noValue, data.costPerDistanceValue)
        assertEquals(noValue, data.avgCostPerKwhValue)
        assertTrue("charges still counted", data.hasCharges)
    }

    @Test
    fun `zero charges reports none coverage and hasCharges false`() {
        val data = CostSummaryWidgetFormatter.format(
            carId = 1,
            carName = "Model 3",
            periodLabel = "Last 30 days",
            metrics = CostAnalyticsMetrics(),
            currencySymbol = symbol,
            noValue = noValue,
            updatedValue = "Updated 10:00",
            units = units,
        )

        assertEquals(CostSummaryWidgetCoverage.None, data.costCoverage)
        assertEquals(false, data.hasCharges)
    }
}
