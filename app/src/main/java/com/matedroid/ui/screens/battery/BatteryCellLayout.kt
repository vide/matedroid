package com.matedroid.ui.screens.battery

import kotlin.math.max
import kotlin.math.min

/*
 * Geometry of the Battery Health "cell" pictogram, in figure units (1 unit = 1 dp at full size;
 * the whole figure is scaled down proportionally on narrow screens). Kept free of Compose so the
 * placement rules can be unit-tested.
 *
 * Figure: 348 x 322. Label column (86) · gap · cell (140 wide, at x = 104) · gap · label column.
 * Inside the cell (cell coordinates): outline y 10..318 = the pack when new; inner area y 15..313;
 * the accent fill rises from the bottom to healthPercent of the inner height (usable now); the
 * hatched sliver above it is what was lost.
 */
internal object CellSpec {
    const val FIG_W = 348f
    const val FIG_H = 322f

    const val CELL_X = 104f
    const val CELL_W = 140f
    const val CELL_H = 320f

    // Cell coordinates (relative to CELL_X)
    const val OUTLINE_LEFT = 1f
    const val OUTLINE_TOP = 10f
    const val OUTLINE_W = 138f
    const val OUTLINE_H = 308f
    const val OUTLINE_RADIUS = 10f
    const val OUTLINE_STROKE = 2f

    const val CAP_X = 48f
    const val CAP_Y = 1f
    const val CAP_W = 44f
    const val CAP_H = 8f
    const val CAP_RADIUS = 3f

    const val INNER_LEFT = 6f
    const val INNER_RIGHT = 134f
    const val INNER_TOP = 15f
    const val INNER_BOTTOM = 313f
    const val INNER_RADIUS = 6f
    const val INNER_W = INNER_RIGHT - INNER_LEFT
    const val INNER_H = INNER_BOTTOM - INNER_TOP

    // Leaders (figure coordinates). Dots sit inside the cell, 14 units in from each side.
    const val RIGHT_DOT_X = CELL_X + CELL_W - 14f   // 230
    const val LEFT_DOT_X = CELL_X + 14f             // 118
    const val RIGHT_LABEL_X = 262f
    const val LABEL_W = 86f

    // Labels: value line is 18 high, so a label whose top is t has its value centred at t + 9.
    const val VALUE_HALF = 9f
    const val LABEL_PITCH = 43f
    const val LABEL_HEIGHT = 40f
    const val KEY_HEIGHT = 28f

    const val HERO_BOTTOM_MARGIN = 11f
    const val TEXT_GAP = 3f
}

internal data class CellLayout(
    /** Top of the usable-now fill (cell y). */
    val fillTop: Float,
    /** The today line (cell y): state of charge as a share of the usable fill. */
    val todayY: Float,
    /** Leader anchors (figure y): outline top, middle of the lost sliver, fill top. */
    val anchors: List<Float>,
    /** Tops of the three label rows (figure y), fanned out so they never overlap. */
    val labelTops: List<Float>,
    /** Top of the "43 % TODAY" caption (cell y), or null when there is no room for it. */
    val todayLabelTop: Float?,
    /** Top of the hero figure block (cell y). */
    val heroTop: Float,
    /** True when the hero sits inside the fill (knockout); false when it sits above the fill. */
    val heroInFill: Boolean,
    /** False when low anchors push the labels into the column keys at the foot. */
    val showKeys: Boolean
) {
    /** y of the centre of label row [i]'s value line (figure y): where its leader ends. */
    fun labelCenter(i: Int) = labelTops[i] + CellSpec.VALUE_HALF
}

/**
 * Places everything in the pictogram.
 *
 * @param heroHeight measured height of the health figure + caption block, in figure units
 * @param todayLabelHeight measured height of the today caption, in figure units
 * @param labelPitch minimum vertical distance between label rows, in figure units
 */
internal fun layoutCell(
    healthPercent: Double,
    usableSocPercent: Int,
    heroHeight: Float,
    todayLabelHeight: Float,
    labelPitch: Float = CellSpec.LABEL_PITCH
): CellLayout {
    val health = (healthPercent.coerceIn(0.0, 100.0) / 100.0).toFloat()
    val soc = usableSocPercent.coerceIn(0, 100) / 100f

    val fillTop = CellSpec.INNER_BOTTOM - CellSpec.INNER_H * health
    val fillH = CellSpec.INNER_BOTTOM - fillTop
    // State of charge is a share of the usable pack, so it is measured against the fill, not the
    // outline: drawing it against the outline would overstate today's energy by the lost share.
    val todayY = CellSpec.INNER_BOTTOM - fillH * soc

    val anchors = listOf(
        CellSpec.OUTLINE_TOP,
        (CellSpec.INNER_TOP + fillTop) / 2f,
        fillTop
    )
    val labelTops = mutableListOf<Float>()
    anchors.forEachIndexed { i, a ->
        val wanted = a - CellSpec.VALUE_HALF
        labelTops += if (i == 0) wanted else max(wanted, labelTops[i - 1] + labelPitch)
    }

    // Today caption: just above the line, or just below it when the line is near the fill top.
    val todayLabelTop: Float? = when {
        fillH < todayLabelHeight * 2 + 6f -> null
        todayY - CellSpec.TEXT_GAP - todayLabelHeight >= fillTop + 2f ->
            todayY - CellSpec.TEXT_GAP - todayLabelHeight
        todayY + CellSpec.TEXT_GAP + todayLabelHeight <= CellSpec.INNER_BOTTOM - 2f ->
            todayY + CellSpec.TEXT_GAP
        else -> null
    }

    // Band the hero must stay clear of: the today line and its caption.
    val blockedStart = min(todayLabelTop ?: todayY, todayY) - 3f
    val blockedEnd = max((todayLabelTop ?: todayY) + if (todayLabelTop != null) todayLabelHeight else 0f, todayY) + 3f

    fun fitsInFill(top: Float): Boolean {
        val bottom = top + heroHeight
        val clearOfToday = bottom <= blockedStart || top >= blockedEnd
        return top >= fillTop + 4f && bottom <= CellSpec.INNER_BOTTOM - 4f && clearOfToday
    }

    val candidates = listOf(
        CellSpec.INNER_BOTTOM - CellSpec.HERO_BOTTOM_MARGIN - heroHeight, // low in the fill
        blockedStart - heroHeight,                                         // above the today line
        blockedEnd                                                         // below the today line
    )
    val inFill = candidates.firstOrNull { fitsInFill(it) }
    val aboveFillTop = fillTop - 6f - heroHeight

    val (heroTop, heroInFill, todayTop) = when {
        inFill != null -> Triple(inFill, true, todayLabelTop)
        aboveFillTop >= CellSpec.INNER_TOP + 2f -> Triple(aboveFillTop, false, todayLabelTop)
        // No clean spot: keep the figure low in the fill and drop the today caption.
        else -> Triple(candidates[0], true, null)
    }

    val showKeys = labelTops.last() + CellSpec.LABEL_HEIGHT <= CellSpec.FIG_H - CellSpec.KEY_HEIGHT

    return CellLayout(
        fillTop = fillTop,
        todayY = todayY,
        anchors = anchors,
        labelTops = labelTops,
        todayLabelTop = todayTop,
        heroTop = heroTop,
        heroInFill = heroInFill,
        showKeys = showKeys
    )
}
