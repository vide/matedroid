package com.matedroid.domain

/**
 * User-configured local utility rates for charging cost estimation.
 *
 * MateDroid never converts units or currencies here: rates are stored and
 * consumed in the same "$/kWh" numeric form the user typed in Settings, and
 * paired with the currency they already picked for cost display.
 *
 * When TeslaMate has a recorded [Double] `cost` for a charge we always prefer
 * that (including 0.0 free sessions). These rates are only used to estimate a
 * cost when a charge is missing one.
 */
data class UtilityRates(
    /** Home / AC rate in the user's chosen currency, per kWh. */
    val homePerKwh: Double? = null,
    /**
     * Optional DC / public rate in the user's chosen currency, per kWh.
     * When null, DC estimates fall back to [homePerKwh] so one rate still works.
     */
    val dcPerKwh: Double? = null,
) {
    /**
     * Pick the rate that applies to a charge.
     *
     * If [isDc] is true and a DC rate is set, use it; otherwise fall back to
     * the home rate. Returning null means no estimation should be attempted.
     */
    fun rateFor(isDc: Boolean): Double? =
        if (isDc) (dcPerKwh ?: homePerKwh) else homePerKwh

    /** True when at least one rate is set (any estimation is possible). */
    val isConfigured: Boolean
        get() = homePerKwh != null || dcPerKwh != null
}

/** Where a cost figure came from. Estimates are always labelled as such. */
enum class CostSource {
    /** From TeslaMate's stored charge.cost (may be 0.0 for a free session). */
    Recorded,
    /** Computed as `energy * rate` because TeslaMate had no cost. */
    Estimated,
    /** No recorded cost and no rate configured — cost is unknown. */
    Missing,
}

/** Result of applying [CostResolver] to one charge or one drive. */
data class ResolvedCost(
    val amount: Double?,
    val source: CostSource,
)

/**
 * Central place that decides "what does this cost?" given an optional recorded
 * value, an energy figure (in kWh), an AC/DC flag, and the user's [UtilityRates].
 *
 * Rules:
 * 1. If [cost] is non-null → [CostSource.Recorded]. Never overridden, even 0.0.
 * 2. Else if a rate applies (per [UtilityRates.rateFor]) and [energyAdded] is
 *    finite and non-negative → estimate = energy * rate, [CostSource.Estimated].
 * 3. Else → [CostSource.Missing].
 *
 * The resolver never converts units. Callers must pass energy in kWh (which is
 * what TeslaMate returns for both AC and DC charges).
 */
object CostResolver {

    /**
     * Resolve the cost for a single charge.
     *
     * @param cost recorded TeslaMate cost, or null when unknown.
     * @param energyAdded kWh added by the charge (already in kWh from the API).
     * @param isDc whether this charge is DC/fast — used to pick the rate.
     * @param rates the user's configured local rates.
     */
    fun resolveChargeCost(
        cost: Double?,
        energyAdded: Double,
        isDc: Boolean,
        rates: UtilityRates,
    ): ResolvedCost {
        if (cost != null) return ResolvedCost(cost, CostSource.Recorded)
        val rate = rates.rateFor(isDc) ?: return ResolvedCost(null, CostSource.Missing)
        if (!energyAdded.isFinite() || energyAdded < 0.0) {
            return ResolvedCost(null, CostSource.Missing)
        }
        return ResolvedCost(energyAdded * rate, CostSource.Estimated)
    }

    /**
     * Estimate the electricity cost of a drive from its consumed energy using
     * the user's home / AC rate (drives don't have a natural DC concept).
     *
     * Returns [CostSource.Missing] when the home rate is unset or the energy
     * value is null / negative / not finite.
     */
    fun estimateDriveCost(
        energyConsumed: Double?,
        rates: UtilityRates,
    ): ResolvedCost {
        val energy = energyConsumed ?: return ResolvedCost(null, CostSource.Missing)
        val rate = rates.homePerKwh ?: return ResolvedCost(null, CostSource.Missing)
        if (!energy.isFinite() || energy < 0.0) {
            return ResolvedCost(null, CostSource.Missing)
        }
        return ResolvedCost(energy * rate, CostSource.Estimated)
    }
}
