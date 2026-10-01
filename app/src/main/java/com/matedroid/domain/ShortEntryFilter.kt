package com.matedroid.domain

import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary

/**
 * Single source of truth for the "short drive / charge" rule behind the
 * `showShortDrivesCharges` setting (Settings → Display → "Show short drives / charges").
 *
 * When the setting is OFF (the default), entries matching the "short" definition are hidden
 * from every list-like surface: the drives list, the charges list, and the trip timelines /
 * leg lists. They are still counted in totals, averages and statistics — this filter is
 * purely presentational and never touches what is fetched or stored.
 *
 * The thresholds are user-configurable (Settings → Display). Like [UnitSystem], this object is
 * a process-wide mirror of the stored preference: restored at app start by
 * [com.matedroid.MateDroidApp] and written through by
 * [com.matedroid.ui.screens.settings.SettingsViewModel] whenever the user picks a new value.
 * Keeping the mirror here is what lets every [isSignificant] helper stay zero-argument, so no
 * call site has to thread thresholds through its own state.
 *
 * Distances are compared in the user's display unit, NOT in km: TeslamateAPI pre-converts
 * every distance it returns, and the configured threshold is picked from unit-aware presets,
 * so both sides of the comparison are already in the same unit. Do not scale through
 * [UnitSystem] here.
 *
 * IMPORTANT: keep ALL thresholds and the predicate logic here, and never re-implement them at a
 * call site. Any new screen that renders individual drives/charges MUST filter through one of the
 * [isSignificant] helpers below — that is what keeps the behaviour from silently diverging again
 * (it previously did: the trip timeline shipped without this filter and showed short legs).
 */
object ShortEntryFilter {
    /** A drive shorter than this many minutes is "short". */
    const val DEFAULT_MIN_DRIVE_DURATION_MIN = 1

    /** A drive shorter than this, in the user's distance unit, is "short". */
    const val DEFAULT_MIN_DRIVE_DISTANCE = 1.0

    /** A charge that added this much energy (kWh) or less is "short". */
    const val DEFAULT_MIN_CHARGE_ENERGY_KWH = 0.1

    /** Values offered in the Settings pickers. `0` means "no minimum" for that dimension. */
    val DRIVE_DURATION_PRESETS_MIN = listOf(0, 1, 2, 5, 10)
    val DRIVE_DISTANCE_PRESETS = listOf(0.0, 0.5, 1.0, 2.0, 5.0, 10.0)
    val CHARGE_ENERGY_PRESETS_KWH = listOf(0.0, 0.1, 0.5, 1.0, 2.0, 5.0)

    @Volatile
    var minDriveDurationMin: Int = DEFAULT_MIN_DRIVE_DURATION_MIN

    @Volatile
    var minDriveDistance: Double = DEFAULT_MIN_DRIVE_DISTANCE

    @Volatile
    var minChargeEnergyKwh: Double = DEFAULT_MIN_CHARGE_ENERGY_KWH

    /** True when a drive is significant enough to show in lists. */
    fun isSignificantDrive(durationMin: Int?, distance: Double?): Boolean =
        (durationMin ?: 0) >= minDriveDurationMin &&
            (distance ?: 0.0) >= minDriveDistance

    /**
     * True when a charge is significant enough to show in lists. A threshold of 0 means
     * "no minimum" — without the guard the strict `>` would still hide 0 kWh charges.
     */
    fun isSignificantCharge(energyKwh: Double?): Boolean =
        minChargeEnergyKwh <= 0.0 || (energyKwh ?: 0.0) > minChargeEnergyKwh
}

// Typed helpers — one per model that carries drives/charges. Adding a new drive/charge model?
// Add its helper here so every call site stays uniform and the rule can't drift.
fun DriveData.isSignificant(): Boolean =
    ShortEntryFilter.isSignificantDrive(durationMin, distance)

fun DriveSummary.isSignificant(): Boolean =
    ShortEntryFilter.isSignificantDrive(durationMin, distance)

fun ChargeData.isSignificant(): Boolean =
    ShortEntryFilter.isSignificantCharge(chargeEnergyAdded)

fun ChargeSummary.isSignificant(): Boolean =
    ShortEntryFilter.isSignificantCharge(energyAdded)
