package com.matedroid.domain

/**
 * The low-charge colouring of the battery percentage on the dashboard and the widget
 * (Settings → Display → "Warn below").
 *
 * The counterpart to [HighSocWarning]: red below the threshold, amber for the
 * [AMBER_MARGIN] points just above it, the palette colour otherwise. Both levels used to be
 * hardcoded (red below 20%, amber below 40%), which is the wrong line for a short-range car
 * used for commuting and for a long-range one that never sees a Supercharger.
 */
object LowSocWarning {
    /** Threshold that leaves the percentage in the palette colour at any level. */
    const val DISABLED = 0

    /** Red below 20%, the fixed behaviour the app had before this was configurable. */
    const val DEFAULT_THRESHOLD = 20

    /**
     * How far above the threshold the amber band reaches. At the default this puts amber
     * below 40%, exactly where it used to sit.
     */
    const val AMBER_MARGIN = 20

    /** Values offered in the Settings picker. [DISABLED] is offered first, as "Never". */
    val PRESETS = listOf(DISABLED, 10, 15, 20, 25, 30, 40)

    /** True when the level should read as low (red). */
    fun isLow(batteryLevel: Int, threshold: Int): Boolean =
        threshold != DISABLED && batteryLevel < threshold

    /**
     * True when the level is close to low (amber). Call sites check [isLow] first — this
     * range deliberately includes it, mirroring the `when` chain it replaced.
     */
    fun isGettingLow(batteryLevel: Int, threshold: Int): Boolean =
        threshold != DISABLED && batteryLevel < threshold + AMBER_MARGIN
}
