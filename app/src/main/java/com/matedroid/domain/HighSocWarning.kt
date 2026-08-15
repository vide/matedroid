package com.matedroid.domain

/**
 * The "high state of charge" warning that sits next to the battery percentage on the
 * dashboard (Settings → Display → "Warn above").
 *
 * The level it appears at is user-configurable because the right answer depends on the pack:
 * NMC cars are happiest below 90% for daily use, while LFP cars (Standard Range Model 3/Y)
 * are meant to be charged to 100% regularly and should never be nagged about it — that is what
 * [DISABLED] is for. The trim badging TeslamateAPI reports is not a reliable way to tell the two
 * apart across markets and model years, so this is a setting rather than a detection.
 */
object HighSocWarning {
    /** Threshold that turns the warning off entirely. Rendered as "Never" in the picker. */
    const val DISABLED = 0

    /** Warn above 90%, the fixed behaviour the app had before this was configurable. */
    const val DEFAULT_THRESHOLD = 90

    /** Values offered in the Settings picker, ascending, with [DISABLED] last as "Never". */
    val PRESETS = listOf(70, 75, 80, 85, 90, 99, DISABLED)

    /**
     * True when the dashboard should flag the current charge level.
     *
     * A charging car is never flagged: it is on its way to the limit the user set in the car,
     * so the warning would fire on every charge to 100% instead of on sitting there full.
     */
    fun shouldWarn(batteryLevel: Int, isCharging: Boolean, threshold: Int): Boolean =
        threshold != DISABLED && !isCharging && batteryLevel > threshold
}
