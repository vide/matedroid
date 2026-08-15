package com.matedroid.domain.model

import com.matedroid.data.api.models.Units

/**
 * Utility object for formatting values based on unit preferences from TeslamateApi.
 * Supports metric (km, °C, bar) and imperial (mi, °F, psi) units.
 *
 * IMPORTANT: TeslamateAPI already returns all values pre-converted to the user's preferred
 * unit system. Do NOT apply any conversion here — just format the number and attach the
 * correct unit label.
 *
 * Exception: elevation units.  We need implement the conversion here until API/Teslamate do itself
 */
object UnitFormatter {

    /**
     * Format elevation value with appropriate unit label.
     * Value is also converted here as the API do not convert to ft when user select mi instead of km.
     */
    fun formatElevation(value: Int?, units: Units?): String {
        val v = value ?: 0
        return if (units?.isImperial == true) {
            "%,d ft".format((v * 3.28084).toInt())
        } else {
            "%,d m".format(v)
        }
    }

    /**
     * Format an elevation *change* with an explicit sign, so a climb reads "+120 m" and a
     * descent "-107 m". Negative values already carry their sign from the number formatting.
     */
    fun formatSignedElevation(value: Int?, units: Units?): String {
        val formatted = formatElevation(value, units)
        return if ((value ?: 0) > 0) "+$formatted" else formatted
    }

    /**
     * Get the elevation value
     */
    fun getElevationValue(value: Float, units: Units?): Float {
        return if (units?.isImperial == true) (value * 3.28084f) else value
    }

    /**
     * Get the elevation unit label
     */
    fun getElevationUnit(units: Units?): String {
        return if (units?.isImperial == true) "ft" else "m"
    }

    /**
     * Format distance value with appropriate unit label.
     * Value is already in km (metric) or mi (imperial) as returned by the API.
     */
    fun formatDistance(value: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            "%,.${decimals}f mi".format(value)
        } else {
            "%,.${decimals}f km".format(value)
        }
    }

    /**
     * Get the distance unit label
     */
    fun getDistanceUnit(units: Units?): String {
        return if (units?.isImperial == true) "mi" else "km"
    }

    /**
     * Format temperature value with appropriate unit label.
     * Value is already in °C (metric) or °F (imperial) as returned by the API.
     */
    fun formatTemperature(value: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.unitOfTemperature == "F") {
            "%.${decimals}f°F".format(value)
        } else {
            "%.${decimals}f°C".format(value)
        }
    }

    /**
     * Get the temperature unit label
     */
    fun getTemperatureUnit(units: Units?): String {
        return if (units?.unitOfTemperature == "F") "°F" else "°C"
    }

    /**
     * Format pressure value with appropriate unit label.
     * Note: TeslamateAPI returns pressure already in the user's preferred unit,
     * so no conversion is needed - just format and add the label.
     */
    fun formatPressure(value: Double, units: Units?, decimals: Int = 1): String {
        val unit = if (units?.unitOfPressure == "psi") "psi" else "bar"
        return "%.${decimals}f %s".format(value, unit)
    }

    /**
     * Get the pressure unit label
     */
    fun getPressureUnit(units: Units?): String {
        return if (units?.unitOfPressure == "psi") "psi" else "bar"
    }

    /**
     * Format efficiency (Wh/km or Wh/mi).
     * Value is already in Wh/km (metric) or Wh/mi (imperial) as returned by the API.
     */
    fun formatEfficiency(value: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            "%.${decimals}f Wh/mi".format(value)
        } else {
            "%.${decimals}f Wh/km".format(value)
        }
    }

    /**
     * Get the efficiency unit label
     */
    fun getEfficiencyUnit(units: Units?): String {
        return if (units?.isImperial == true) "Wh/mi" else "Wh/km"
    }

    /**
     * Format speed value with appropriate unit label.
     * Value is already in km/h (metric) or mph (imperial) as returned by the API.
     */
    fun formatSpeed(value: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.isImperial == true) {
            "%.${decimals}f mph".format(value)
        } else {
            "%.${decimals}f km/h".format(value)
        }
    }

    /**
     * Get the speed unit label
     */
    fun getSpeedUnit(units: Units?): String {
        return if (units?.isImperial == true) "mph" else "km/h"
    }

    /**
     * Format an energy amount given in kWh, rolling over to MWh at 1,000 kWh.
     * Energy is not affected by the unit system, so no [Units] is needed.
     */
    fun formatEnergy(kwh: Double): String =
        if (kwh >= 1000) "%,.1f MWh".format(kwh / 1000) else "%.0f kWh".format(kwh)

    /**
     * Format a monetary amount with its currency [symbol]. Uses 2 decimals for
     * absolute amounts and 3 for per-kWh prices (which are typically well below 1).
     */
    fun formatCost(value: Double, symbol: String, perKwh: Boolean = false): String =
        if (perKwh) "%,.3f %s".format(value, symbol) else "%,.2f %s".format(value, symbol)
}
