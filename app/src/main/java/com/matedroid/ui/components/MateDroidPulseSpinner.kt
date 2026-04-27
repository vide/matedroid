package com.matedroid.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Indeterminate spinner that draws the MateDroid M+D logotype dim, with a brighter
 * accent pulse that moves around its outline forward and back in a loop. Built
 * specifically for filter-load feedback on the drives and charges lists, where
 * the deliberate `isLoading` suppression (added in 1.5.x to stop the full-screen
 * flash on filter change) leaves the user without any indication that work is in
 * flight. Stack this centered on top of the existing list — the list shows
 * through, the spinner just hovers.
 *
 * The pulse traces the *outline* of the filled letterforms (the existing notif-
 * ication path verbatim) — going around the outside edge of the M, then around
 * the D. It's not a centerline pass; it's a perimeter trace, which the eye
 * reads as the dot "writing" the letters since the outline IS the letter shape.
 *
 * Internally: the SVG path string is parsed once via [PathParser], the result
 * fed to a [PathMeasure] so we can sample (x, y) at any distance along it. An
 * infinite-repeat animation drives `progress` from 0 → 1 → 0; `progress * length`
 * gives the pulse's distance, the trail comes from a few smaller sampled
 * positions slightly behind it.
 */
@Composable
fun MateDroidPulseSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White,
    size: Dp = 110.dp,
    trackAlpha: Float = 0.55f,
    pulseDurationMillis: Int = 1700,
) {
    val outlinePath = remember {
        PathParser().parsePathString(MD_LOGO_PATH_DATA).toPath()
    }
    val measure = remember(outlinePath) {
        PathMeasure().apply { setPath(outlinePath, forceClosed = false) }
    }
    val totalLength = measure.length

    val transition = rememberInfiniteTransition(label = "matedroidPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "matedroidPulseProgress",
    )

    Canvas(modifier = modifier.size(size)) {
        // SVG viewport is 1024×1024, square. Letterforms are roughly centered
        // horizontally inside that viewport, so a uniform scale to the smallest
        // canvas dimension keeps everything in frame.
        val s = kotlin.math.min(this.size.width, this.size.height) / 1024f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(
                path = outlinePath,
                color = trackColor.copy(alpha = trackAlpha),
                style = Fill,
            )

            if (totalLength > 0f) {
                val pulseDistance = progress * totalLength

                // Comet trail: smaller dots fading behind the lead pulse. Spaced
                // by an absolute distance that scales with the path length so
                // small letterforms get a tight trail and big ones get a longer
                // one.
                val trailSpacing = totalLength * 0.012f
                val pulseRadius = 1024f * 0.032f
                for (i in TRAIL_STEPS downTo 1) {
                    val trailDistance = (pulseDistance - i * trailSpacing)
                        .coerceIn(0f, totalLength)
                    val trailPos = measure.getPosition(trailDistance)
                    val tFraction = i.toFloat() / TRAIL_STEPS
                    drawCircle(
                        color = color.copy(alpha = (1f - tFraction) * 0.7f),
                        radius = pulseRadius * (1f - tFraction * 0.55f),
                        center = trailPos,
                    )
                }

                val pulsePos = measure.getPosition(pulseDistance)
                // Soft glow around the lead pulse — three concentric circles
                // with falling alpha simulate a halo without needing a real
                // blur shader (which gets expensive in a draw-per-frame Canvas).
                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = pulseRadius * 2.4f,
                    center = pulsePos,
                )
                drawCircle(
                    color = color.copy(alpha = 0.30f),
                    radius = pulseRadius * 1.7f,
                    center = pulsePos,
                )
                drawCircle(
                    color = color,
                    radius = pulseRadius,
                    center = pulsePos,
                )
            }
        }
    }
}

private const val TRAIL_STEPS = 5

/**
 * Debounce a boolean loading flag so a spinner only appears after the load has
 * been in-flight for at least [delayMillis]. Sub-[delayMillis] loads never
 * trigger UI — the typical case for a snappy filter switch — and only the
 * genuinely slow loads (e.g. "All time" pulling a year of charges) get the
 * overlay treatment. Trade-off: there is a brief blind window at the start of
 * every load, but for [delayMillis] = 200 ms that's still below the perceptual
 * threshold for "missing feedback".
 */
@Composable
fun rememberDebouncedLoading(loading: Boolean, delayMillis: Long = 200L): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(delayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/**
 * Full-screen loading placeholder built around the [MateDroidPulseSpinner]. Drop
 * this in wherever the legacy `Box { CircularProgressIndicator() }` pattern was
 * sitting on a screen's primary load path. The spinner only paints once
 * [delayMillis] of sustained loading have elapsed — sub-threshold loads (cached
 * data, fast networks) end without ever showing the spinner, so navigation
 * feels snappy. The Box itself takes up the full screen area regardless, so the
 * surrounding layout geometry doesn't jump when the spinner appears.
 */
@Composable
fun MateDroidLoadingPlaceholder(
    color: Color = MaterialTheme.colorScheme.primary,
    delayMillis: Long = 200L,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // Scrim only after the debounce window — during the first
            // delayMillis the screen stays as it is (no flash), and once we
            // commit to showing the spinner the body dims so the white M+D
            // outline reads against any theme's surface color.
            .then(
                if (visible) Modifier.background(Color.Black.copy(alpha = 0.45f))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            MateDroidPulseSpinner(color = color)
        }
    }
}

// Path data verbatim from app/src/main/art/ic_notification.svg /
// app/src/main/res/drawable/ic_notification.xml — the outline of the filled M+D
// logotype. Kept in code so the spinner doesn't depend on a separate vector
// asset round-trip and we can feed it straight into PathParser.
private const val MD_LOGO_PATH_DATA = "M712.43157,201.18078 c -74,0 -158,0 -183.83594,-0.0784 -30.58986,-0.2759 -61.63101,-2.4233 -83.51562,31.15342 -25.91193,40.17431 -51.60515,80.89689 -77.45508,121.00419 -19.14598,29.70584 -36.82647,61.2458 -56.56445,89.82494 -43.59193,-65.57912 -86.18955,-133.77835 -127.8125,-201.15829 -22.31911,-36.13056 -51.86264,-40.75785 -83.19141,-40.84231 -4.529859,-0.0118 -12.155849,0.035 -16.545399,0.0681 -25.56896,0.38492 -51.71493,0.0301 -77.0500097,0.0185 9.6973997,18.28288 19.3228497,33.03563 27.0558697,53.72417 4.15911,17.05987 6.78612,37.64291 6.92696,68.13401 -0.16371,164.31073 0.0158,335.83058 -0.0382,499.99213 28.06346,-0.0477 79.025789,-0.0154 107.025789,-0.0154 0,-162.01399 -0.0644,-145.35238 0,-458.97416 29,49.37568 31.29561,47.86444 57.45702,89.43405 35.45351,55.23366 69.53532,112.58067 106.14258,166.03418 24.41521,-33.77529 45.83638,-72.21555 69.19141,-107.67479 37.94805,-61.44022 69.20899,-106.96966 109.20899,-174.86124 4,-7.71495 6,-12.34392 12,-16.9729 103,0 204.00242,-1.26635 308.94726,-0.50323 28.92681,0.38342 41.14668,10.6598 64.00976,39.41857 22.81344,28.52885 34.84171,73.85136 35.28906,118.63849 1.64015,56.59747 0.0992,120.63534 -26.19336,164.73229 -26.78959,44.63368 -50.57717,54.67283 -88.05272,55.74652 -69.98827,0.75942 -121,0 -191,0 V 397.14057 c -30.21905,0.0769 -79.78098,-0.0771 -110,0 0.39786,139.42294 0.19873,286.44245 0,425.86533 h 296 c 33.68301,-0.0839 101.75887,-11.01515 145.44723,-51.27727 46.72021,-41.56618 74.99949,-121.86062 79.91999,-203.38241 3.827,-75.30985 2.9834,-154.75859 -19.73054,-223.54977 -18.2408,-61.81237 -55.8662,-105.37383 -97.11914,-125.85014 -65.51754,-20.8515 -114.51754,-17.76553 -159.51754,-17.76553 z"
