package com.matedroid.domain

/**
 * User-configured assumptions for the ICE (gasoline) cost comparison.
 *
 * MateDroid stays neutral about which unit system the user prefers here: the
 * economy figure can be entered in L/100 km or US mpg, and the fuel price can
 * be per liter or per US gallon. [IceComparisonCalculator] converts between
 * them internally when the assumptions don't match the length unit already
 * used for TeslaMate distances (km vs. mi), so the distance value from Room
 * is never converted.
 *
 * All fields are optional; the comparison is only surfaced when both a
 * strictly-positive [economyValue] and a strictly-positive [fuelPrice] are
 * available.
 */
data class IceAssumptions(
    /** Fuel economy value; L/100 km when [economyIsMpg] is false, US mpg otherwise. */
    val economyValue: Double? = null,
    /** True when [economyValue] is US mpg; false when it is L/100 km. */
    val economyIsMpg: Boolean = false,
    /** Fuel price value; per liter when [fuelPriceIsPerGallon] is false, per US gallon otherwise. */
    val fuelPrice: Double? = null,
    /** True when [fuelPrice] is per US gallon; false when it is per liter. */
    val fuelPriceIsPerGallon: Boolean = false,
) {
    /**
     * True when both economy and price are set to strictly-positive, finite
     * numbers. Anything else means the comparison should not be shown.
     */
    val isConfigured: Boolean
        get() {
            val e = economyValue
            val p = fuelPrice
            return e != null && e.isFinite() && e > 0.0 &&
                p != null && p.isFinite() && p > 0.0
        }
}

/**
 * Neutral side-by-side result comparing MateDroid's Tesla charging cost to the
 * equivalent gasoline cost implied by [IceAssumptions] over a given distance.
 *
 * Numbers are presented as-is: [savings] can be positive (gasoline more
 * expensive) or negative (gasoline cheaper). Both amounts stay in the currency
 * MateDroid already displays; no currency conversion is performed.
 */
data class IceComparisonResult(
    /** Distance covered, in the unit already used by TeslaMate (km or mi). */
    val distance: Double,
    /** Tesla / EV cost for the same distance, or null when unknown. */
    val teslaCost: Double?,
    /** Estimated gasoline cost, or null when assumptions are missing / distance ≤ 0. */
    val iceCost: Double?,
    /** iceCost - teslaCost, or null when either side is null. */
    val savings: Double?,
    /** savings / iceCost * 100, or null when iceCost is null or ≤ 0. */
    val savingsPercent: Double?,
    /**
     * Volume of gasoline the ICE car would have used for [distance].
     * Expressed in the unit indicated by [usedImperialVolume]. Null when the
     * gasoline cost couldn't be computed.
     */
    val iceVolumeUsed: Double?,
    /** True when [iceVolumeUsed] is in US gallons; false when it is in liters. */
    val usedImperialVolume: Boolean,
)

/**
 * Pure math: given a distance already in the user's length unit (km when
 * [distanceIsMiles] is false, mi when true), estimate what an equivalent
 * gasoline car would have cost for the same drive.
 *
 * The distance is **never** converted: TeslaMate already gave it to us in the
 * user's preferred unit. Only the ICE economy and fuel-price assumptions are
 * converted when they were entered in the "other" unit system.
 */
object IceComparisonCalculator {

    /** Liters per US gallon. */
    private const val LITERS_PER_US_GALLON: Double = 3.785411784

    /**
     * mpg ↔ L/100 km conversion constant.
     *
     * `mpg = US_GAL_TO_L * KM_PER_MI * 100 / L_per_100km`
     * = 3.785411784 * 1.609344 * 100 / 100 ≈ 235.214583
     */
    private const val MPG_L100_CONVERSION: Double = 235.214583

    fun compare(
        distance: Double,
        distanceIsMiles: Boolean,
        teslaCost: Double?,
        assumptions: IceAssumptions,
    ): IceComparisonResult {
        if (!distance.isFinite() || distance <= 0.0 || !assumptions.isConfigured) {
            return IceComparisonResult(
                distance = distance,
                teslaCost = teslaCost,
                iceCost = null,
                savings = null,
                savingsPercent = null,
                iceVolumeUsed = null,
                usedImperialVolume = distanceIsMiles,
            )
        }

        val economy = assumptions.economyValue!!
        val price = assumptions.fuelPrice!!

        val (volume, iceCost) = if (distanceIsMiles) {
            val mpg = if (assumptions.economyIsMpg) {
                economy
            } else {
                MPG_L100_CONVERSION / economy
            }
            val pricePerGallon = if (assumptions.fuelPriceIsPerGallon) {
                price
            } else {
                price * LITERS_PER_US_GALLON
            }
            val gallons = distance / mpg
            gallons to gallons * pricePerGallon
        } else {
            val lPer100 = if (assumptions.economyIsMpg) {
                MPG_L100_CONVERSION / economy
            } else {
                economy
            }
            val pricePerLiter = if (assumptions.fuelPriceIsPerGallon) {
                price / LITERS_PER_US_GALLON
            } else {
                price
            }
            val liters = distance * (lPer100 / 100.0)
            liters to liters * pricePerLiter
        }

        val savings = if (teslaCost != null) iceCost - teslaCost else null
        val savingsPercent = savings?.takeIf { iceCost > 0.0 }?.let { it / iceCost * 100.0 }

        return IceComparisonResult(
            distance = distance,
            teslaCost = teslaCost,
            iceCost = iceCost,
            savings = savings,
            savingsPercent = savingsPercent,
            iceVolumeUsed = volume,
            usedImperialVolume = distanceIsMiles,
        )
    }
}
