package com.matedroid.domain

import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary

/**
 * Single source of truth for the "short drive / charge" rule behind the
 * `showShortDrivesCharges` setting (Settings → "Show short drives / charges").
 *
 * When the setting is OFF (the default), entries matching the "short" definition are hidden
 * from every list-like surface: the drives list, the charges list, and the trip timelines /
 * leg lists. They are still counted in totals, averages and statistics.
 *
 * "Short" means a drive under 1 minute OR under 1 km, or a charge that added 0.1 kWh or less.
 *
 * IMPORTANT: keep ALL thresholds and the predicate logic here, and never re-implement them at a
 * call site. Any new screen that renders individual drives/charges MUST filter through one of the
 * [isSignificant] helpers below — that is what keeps the behaviour from silently diverging again
 * (it previously did: the trip timeline shipped without this filter and showed short legs).
 */
object ShortEntryFilter {
    /** A drive shorter than this many minutes is "short". */
    const val MIN_DRIVE_DURATION_MIN = 1

    /** A drive shorter than this many km is "short". */
    const val MIN_DRIVE_DISTANCE_KM = 1.0

    /** A charge that added this much energy (kWh) or less is "short". */
    const val MIN_CHARGE_ENERGY_KWH = 0.1

    /** True when a drive is significant enough to show in lists. */
    fun isSignificantDrive(durationMin: Int?, distanceKm: Double?): Boolean =
        (durationMin ?: 0) >= MIN_DRIVE_DURATION_MIN &&
            (distanceKm ?: 0.0) >= MIN_DRIVE_DISTANCE_KM

    /** True when a charge is significant enough to show in lists. */
    fun isSignificantCharge(energyKwh: Double?): Boolean =
        (energyKwh ?: 0.0) > MIN_CHARGE_ENERGY_KWH
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
