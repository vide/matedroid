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
 * hatched sliver above it is what was lost, with the lost share printed on it as a pill.
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

    // Lost-share pill padding around its text
    const val PILL_PAD_H = 9f
    const val PILL_PAD_V = 3f
    const val TEXT_GAP = 3f
}

internal data class CellLayout(
    /** Top of the usable-now fill (cell y). */
    val fillTop: Float,
    /** Leader anchors (figure y): outline top, middle of the lost sliver, fill top. */
    val anchors: List<Float>,
    /** Tops of the three label rows (figure y), fanned out so they never overlap. */
    val labelTops: List<Float>,
    /** Top of the "−12,0 %" lost-share pill (cell y), or null when nothing was lost. */
    val lossPillTop: Float?,
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
 * @param lossPillHeight measured height of the lost-share pill, in figure units
 * @param labelPitch minimum vertical distance between label rows, in figure units
 */
internal fun layoutCell(
    healthPercent: Double,
    heroHeight: Float,
    lossPillHeight: Float,
    labelPitch: Float = CellSpec.LABEL_PITCH
): CellLayout {
    val health = (healthPercent.coerceIn(0.0, 100.0) / 100.0).toFloat()

    val fillTop = CellSpec.INNER_BOTTOM - CellSpec.INNER_H * health
    val sliverMid = (CellSpec.INNER_TOP + fillTop) / 2f

    val anchors = listOf(CellSpec.OUTLINE_TOP, sliverMid, fillTop)
    val labelTops = mutableListOf<Float>()
    anchors.forEachIndexed { i, a ->
        val wanted = a - CellSpec.VALUE_HALF
        labelTops += if (i == 0) wanted else max(wanted, labelTops[i - 1] + labelPitch)
    }

    // Hero: low in the fill, or above it when the fill is too short to hold it.
    val lowInFill = CellSpec.INNER_BOTTOM - CellSpec.HERO_BOTTOM_MARGIN - heroHeight
    val aboveFillTop = fillTop - 6f - heroHeight
    val (heroTop, heroInFill) = when {
        lowInFill >= fillTop + 4f -> lowInFill to true
        aboveFillTop >= CellSpec.INNER_TOP + 2f -> aboveFillTop to false
        else -> lowInFill to true
    }

    // Lost-share pill: centred in the sliver; on a young pack the sliver is thinner than the
    // pill, so it is held just under the inner top and straddles the edge with the fill. Its own
    // background keeps it legible on either. Above the hero when the hero sits in the sliver.
    val lossPillTop: Float? = if (healthPercent >= 99.95) null else {
        var top = max(sliverMid - lossPillHeight / 2f, CellSpec.INNER_TOP + 2f)
        if (!heroInFill) top = min(top, heroTop - CellSpec.TEXT_GAP - lossPillHeight)
        top.takeIf { it >= CellSpec.INNER_TOP }
    }

    val showKeys = labelTops.last() + CellSpec.LABEL_HEIGHT <= CellSpec.FIG_H - CellSpec.KEY_HEIGHT

    return CellLayout(
        fillTop = fillTop,
        anchors = anchors,
        labelTops = labelTops,
        lossPillTop = lossPillTop,
        heroTop = heroTop,
        heroInFill = heroInFill,
        showKeys = showKeys
    )
}
