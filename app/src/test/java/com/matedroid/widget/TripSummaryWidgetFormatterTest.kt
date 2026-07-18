package com.matedroid.widget

import com.matedroid.data.api.models.Units
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripSummaryWidgetFormatterTest {

    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun formatShowsNoValueWhenNoChargeCostWasRecorded() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model Y",
            periodLabel = "Last 30 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 4,
                drivingDays = 3,
                totalDistance = 300.0,
                totalEnergy = 48.0,
                avgEfficiency = 160.0,
                totalCost = 0.0,
                chargesWithCost = 0,
                chargeCount = 2,
                previousDistance = 250.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
        )

        assertTrue(data.hasDrives)
        assertEquals("Last 30 days", data.periodLabel)
        assertEquals("300 km", data.distanceValue)
        assertEquals("4", data.driveCountValue)
        assertEquals("3", data.drivingDaysValue)
        assertEquals("48 kWh", data.energyValue)
        assertEquals("160 Wh/km", data.efficiencyValue)
        assertEquals("N/A", data.costValue)
        assertEquals("N/A", data.costPerDistanceValue)
        assertEquals(TripSummaryWidgetTrend.Up, data.distanceTrend)
        assertEquals(20, data.distanceTrendPercent)
        assertEquals(TripSummaryWidgetCostCoverage.Missing, data.costCoverage)
        assertEquals("km", data.distanceUnit)
        assertEquals("Updated 9:00", data.updatedValue)
    }

    @Test
    fun formatShowsZeroCostWhenAZeroCostChargeWasRecorded() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model Y",
            periodLabel = "Last 7 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 3,
                drivingDays = 2,
                totalDistance = 250.0,
                totalEnergy = 40.0,
                avgEfficiency = 160.0,
                totalCost = 0.0,
                chargesWithCost = 1,
                chargeCount = 1,
                previousDistance = 250.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
        )

        assertEquals("0.00 \$", data.costValue)
        assertEquals("0.00 \$", data.costPerDistanceValue)
        assertEquals(TripSummaryWidgetTrend.Same, data.distanceTrend)
        assertEquals(TripSummaryWidgetCostCoverage.Complete, data.costCoverage)
    }

    @Test
    fun formatUsesImperialLabelsWhenUnitsAreImperial() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model 3",
            periodLabel = "Last 90 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 2,
                drivingDays = 2,
                totalDistance = 120.0,
                totalEnergy = 30.0,
                avgEfficiency = 250.0,
                totalCost = 12.0,
                chargesWithCost = 1,
                chargeCount = 2,
                previousDistance = 150.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
            units = Units(unitOfLength = "mi"),
        )

        assertEquals("120 mi", data.distanceValue)
        assertEquals("250 Wh/mi", data.efficiencyValue)
        assertEquals("N/A", data.costPerDistanceValue)
        assertEquals("12.00 \$", data.costValue)
        assertEquals("mi", data.distanceUnit)
        assertEquals(TripSummaryWidgetTrend.Down, data.distanceTrend)
        assertEquals(20, data.distanceTrendPercent)
        assertEquals(TripSummaryWidgetCostCoverage.Partial, data.costCoverage)
    }

    @Test
    fun formatShowsCostPerDistanceOnlyWhenEveryChargeIsPriced() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model Y",
            periodLabel = "Last 30 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 4,
                drivingDays = 3,
                totalDistance = 200.0,
                totalEnergy = 32.0,
                avgEfficiency = 160.0,
                totalCost = 20.0,
                chargesWithCost = 2,
                chargeCount = 2,
                previousDistance = 180.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
        )

        assertEquals("20.00 \$", data.costValue)
        assertEquals("10.00 \$", data.costPerDistanceValue)
        assertEquals(TripSummaryWidgetCostCoverage.Complete, data.costCoverage)
    }

    @Test
    fun formatMarksEmptyDriveRange() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model S",
            periodLabel = "Last 30 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 0,
                drivingDays = 0,
                totalDistance = 0.0,
                totalEnergy = 0.0,
                avgEfficiency = 0.0,
                totalCost = 0.0,
                chargesWithCost = 0,
                chargeCount = 0,
                previousDistance = 200.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
        )

        assertFalse(data.hasDrives)
        assertEquals("0 km", data.distanceValue)
        assertEquals("0", data.drivingDaysValue)
        assertEquals("N/A", data.efficiencyValue)
        assertEquals("N/A", data.costValue)
        assertEquals(TripSummaryWidgetTrend.None, data.distanceTrend)
        assertEquals(TripSummaryWidgetCostCoverage.None, data.costCoverage)
    }

    @Test
    fun formatMarksDistanceAsNewWhenPreviousRangeWasEmpty() {
        val data = TripSummaryWidgetFormatter.format(
            carId = 7,
            carName = "Model 3",
            periodLabel = "Last 7 days",
            metrics = TripSummaryWidgetMetrics(
                driveCount = 1,
                drivingDays = 1,
                totalDistance = 42.0,
                totalEnergy = 7.0,
                avgEfficiency = 167.0,
                totalCost = 0.0,
                chargesWithCost = 0,
                chargeCount = 0,
                previousDistance = 0.0,
            ),
            currencySymbol = "\$",
            noValue = "N/A",
            updatedValue = "Updated 9:00",
        )

        assertEquals(TripSummaryWidgetTrend.New, data.distanceTrend)
        assertEquals(0, data.distanceTrendPercent)
    }
}
