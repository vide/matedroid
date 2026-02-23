package com.matedroid.widget

/**
 * Describes which optional fields to render for a given widget size and state.
 *
 * Fields always shown at every size (not represented here):
 *   car picture, battery SoC % + bar, car name, status icons,
 *   AC/DC badge, glow (when charging), kWh added (when charging),
 *   time to full (when charging).
 */
data class WidgetLayout(
    /** Ext / Int temperature labels on the right side of the status bar. */
    val showTemperatures: Boolean,
    /** Available driving range in km. Hidden at 2×2 when charging. */
    val showMileage: Boolean,
    /** Charge limit % — as text label and as marker on the battery bar. */
    val showChargeLimit: Boolean,
    /** Charger voltage, current and phase count row. */
    val showVoltageCurrentPhases: Boolean,
)

/** Width threshold (dp) below which the widget is treated as 1-column wide.
 *  Calibrated on Pixel 6a: 1-col = 179 dp, 2-col = 277 dp. */
internal const val NARROW_WIDTH_DP  = 220f
/** Width threshold (dp) at or above which the widget is treated as 3-column wide.
 *  Calibrated on Pixel 6a: 2-col = 277 dp, 3-col = 374 dp. */
internal const val WIDE_WIDTH_DP    = 320f
/** Height threshold (dp) below which the widget is treated as 1-row tall.
 *  Calibrated on Pixel 6a: 1-row = 96 dp, 2-row = 202 dp. */
internal const val COMPACT_HEIGHT_DP = 130f

/**
 * Pure function — no Android dependencies — that maps widget dp dimensions and
 * charge state to the set of optional display fields.
 *
 * Size buckets derived from thresholds above:
 *
 *   1×1  width < NARROW,  height < COMPACT
 *   1×2  width < NARROW,  height ≥ COMPACT
 *   2×1  width in [NARROW, WIDE),  height < COMPACT
 *   2×2  width in [NARROW, WIDE),  height ≥ COMPACT
 *   3×2  width ≥ WIDE,    height ≥ COMPACT
 *
 * Display rules (see WidgetLayoutTest for the full specification table):
 *
 *   showTemperatures         — whenever width ≥ NARROW  (2×1 and larger)
 *   showMileage              — at 3×2 always; at 2×2 only when NOT charging
 *   showChargeLimit          — at 2×2 / 3×2 always; at 2×1 only when charging
 *   showVoltageCurrentPhases — at 2×2 / 3×2 only when charging
 */
fun computeWidgetLayout(widthDp: Float, heightDp: Float, isCharging: Boolean): WidgetLayout {
    val isNarrow  = widthDp  < NARROW_WIDTH_DP
    val isWide    = widthDp  >= WIDE_WIDTH_DP
    val isCompact = heightDp < COMPACT_HEIGHT_DP

    return WidgetLayout(
        showTemperatures         = !isNarrow,
        showMileage              = isWide || (!isNarrow && !isCompact && !isCharging),
        showChargeLimit          = !isNarrow && (!isCompact || isCharging),
        showVoltageCurrentPhases = !isNarrow && !isCompact && isCharging,
    )
}
