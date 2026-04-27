package com.matedroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ThumbWidth = 26.dp
private val ThumbHeight = 48.dp
private val ThumbRightInset = 4.dp
// Distance from the right edge of the overlay to the right edge of the label —
// just past the typical fingertip width so the label peeks out from behind a
// dragging thumb without being obscured by it.
private val LabelLeftOffset = 56.dp
// Width of the indicator's Box overlay. Sized to comfortably fit the label pill
// extending to the left of the thumb. Touches outside the thumb pass through to
// the underlying LazyColumn since neither the Box nor the label intercept them.
private val OverlayWidth = 220.dp

private const val IDLE_FADE_DELAY_MS = 1100L
private const val FADE_DURATION_MS = 220
private const val LABEL_FADE_MS = 140

// When the list spans <= this many distinct months, the label shows day-of-month
// ("25 APR"). Above this, it shows month + 2-digit year ("APR '26") because year
// becomes the disambiguating axis once the list crosses many months.
private const val DAY_FORMAT_MONTH_THRESHOLD = 2

private const val DAY_PATTERN = "d MMM"
private const val YEAR_PATTERN = "MMM ''yy"

/**
 * Vertical scroll indicator overlaying the right edge of a [LazyListState]-backed
 * list. Idle: a small accent-colored pill thumb that fades after inactivity. While
 * the user is dragging the thumb, a separate floating label pill appears to the
 * left of the thumb (clear of the dragging finger) showing the date of the row
 * currently under the thumb. Format auto-adapts to the list's date range —
 * day-of-month for short ranges, month + year for long ones.
 *
 * **Perf shape.** Frequently-changing state (the scroll fraction, the drag
 * fraction, the fade alphas) is intentionally read inside deferred-lambda
 * modifiers (`Modifier.offset { ... }`, `Modifier.graphicsLayer { ... }`) rather
 * than in the composable body. Compose's snapshot system tracks those reads but
 * only re-runs the layout / draw pass when they change — the composable itself
 * doesn't recompose on every scroll frame, which is what made earlier versions
 * sluggish on long lists.
 *
 * Stack on top of the LazyColumn it controls — e.g.
 * `Modifier.align(Alignment.CenterEnd)` inside a parent `Box.fillMaxSize()`.
 *
 * @param state the LazyListState driving the list this indicator scrolls.
 * @param dateAt resolves a list-item index to its [LocalDate], or null for header
 *        items (filter chips, summary card, etc.) that don't carry a date.
 * @param accent the car palette accent color used for the thumb fill and label.
 * @param minItemsToShow the indicator stays hidden when the list has fewer items
 *        than this — short lists don't need fast-scroll affordance.
 */
@Composable
fun MonthScrollIndicator(
    state: LazyListState,
    dateAt: (itemIndex: Int) -> LocalDate?,
    accent: Color,
    modifier: Modifier = Modifier,
    minItemsToShow: Int = 20,
) {
    val totalItems by remember {
        derivedStateOf { state.layoutInfo.totalItemsCount }
    }
    val isScrollable by remember {
        derivedStateOf { state.canScrollForward || state.canScrollBackward }
    }
    if (totalItems < minItemsToShow || !isScrollable) return

    val handleDescription = stringResource(
        com.matedroid.R.string.scrollbar_drag_handle_description
    )

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val dayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(DAY_PATTERN, locale)
    }
    val yearFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(YEAR_PATTERN, locale)
    }

    // The lambda passed in is recreated on every recomposition of the caller, so
    // it can never be stable as a `remember` key. Hold the latest one in a
    // delegated state and read it from inside the derivedStateOf below.
    val currentDateAt by rememberUpdatedState(dateAt)

    // Distinct month count drives the label format. Computing this only when
    // `totalItems` changes (data loaded / filter changed) keeps it off the hot
    // scroll path — `derivedStateOf` would otherwise re-iterate every time the
    // captured dateAt lambda changes identity, which is every recomposition.
    val monthCount by remember {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf 0
            var count = 0
            var prev: YearMonth? = null
            for (i in 0 until total) {
                val date = currentDateAt(i) ?: continue
                val ym = YearMonth.from(date)
                if (ym != prev) {
                    count++
                    prev = ym
                }
            }
            count
        }
    }

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val isDragging by remember { derivedStateOf { dragFraction != null } }

    val thumbVisibility = remember { Animatable(1f) }
    val labelVisibility = remember { Animatable(0f) }
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }

    // Initial idle fade — show the thumb on first paint so the user knows the
    // affordance exists, then fade it out after a beat. Subsequent scroll events
    // re-show via the snapshotFlow below.
    LaunchedEffect(Unit) {
        delay(IDLE_FADE_DELAY_MS)
        if (dragFraction == null) {
            thumbVisibility.animateTo(0f, animationSpec = tween(FADE_DURATION_MS))
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            // Index-based fraction with explicit end cases. The end cases are
            // critical: in the middle of a long list `firstVisibleItemIndex /
            // (total - 1)` only ever reaches `(total - visibleCount) / (total - 1)`
            // — never 1.0. The `canScrollForward = false` override snaps the
            // thumb to the bottom when the list is genuinely at the end, and the
            // equivalent override pins it to the top at the start.
            val total = state.layoutInfo.totalItemsCount
            if (total <= 1) return@derivedStateOf 0f
            if (!state.canScrollBackward) return@derivedStateOf 0f
            if (!state.canScrollForward) return@derivedStateOf 1f
            val firstItemHeight = state.layoutInfo.visibleItemsInfo
                .firstOrNull()?.size?.toFloat() ?: 1f
            val offsetFraction = state.firstVisibleItemScrollOffset.toFloat() /
                firstItemHeight.coerceAtLeast(1f)
            val numerator = state.firstVisibleItemIndex + offsetFraction
            val denominator = (total - 1).coerceAtLeast(1).toFloat()
            (numerator / denominator).coerceIn(0f, 1f)
        }
    }

    // Fade the THUMB on scroll: snapshotFlow emits whenever scroll state or drag
    // state changes, collectLatest cancels the previous fade-out timer on each
    // emission, and fade-out only fires when the user isn't dragging.
    LaunchedEffect(state) {
        snapshotFlow {
            // Pack drag-state + scroll position into a single Long key so we
            // avoid allocating a wrapper class per emission. Bit 63 = isDragging,
            // bits 32..62 = firstVisibleItemIndex, bits 0..31 = scrollOffset.
            val isDragNow = if (dragFraction != null) 1L shl 63 else 0L
            val idx = (state.firstVisibleItemIndex.toLong() and 0x7FFF_FFFFL) shl 32
            val off = state.firstVisibleItemScrollOffset.toLong() and 0xFFFF_FFFFL
            isDragNow or idx or off
        }.drop(1).collectLatest { packed ->
            thumbVisibility.snapTo(1f)
            val isDragNow = (packed ushr 63) == 1L
            if (!isDragNow) {
                delay(IDLE_FADE_DELAY_MS)
                thumbVisibility.animateTo(0f, animationSpec = tween(FADE_DURATION_MS))
            }
        }
    }

    // The LABEL is bound strictly to drag. Fade in on drag-start, fade out on
    // drag-end / cancel. No idle fade timer of its own. Note: `isDragging` is a
    // derivedStateOf that only invalidates on null↔Float transitions — drag
    // updates that just change the Float don't re-trigger this effect.
    LaunchedEffect(isDragging) {
        labelVisibility.animateTo(
            if (isDragging) 1f else 0f,
            animationSpec = tween(LABEL_FADE_MS),
        )
    }

    // Label text is a derivedStateOf so it only invalidates when the rendered
    // string actually changes — many drag-fraction updates within the same row
    // produce the same date and don't recompose Text.
    val labelText by remember {
        derivedStateOf {
            val frac = dragFraction ?: return@derivedStateOf null
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf null
            val thumbCenterY =
                ((trackHeightPx - thumbHeightPx) * frac + thumbHeightPx / 2f)
                    .coerceAtLeast(0f)
            val nearest = info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2f) - thumbCenterY)
            } ?: return@derivedStateOf null
            val total = info.totalItemsCount
            var probe = nearest.index
            while (probe < total) {
                val date = currentDateAt(probe)
                if (date != null) {
                    val formatter =
                        if (monthCount in 1..DAY_FORMAT_MONTH_THRESHOLD) dayFormatter
                        else yearFormatter
                    return@derivedStateOf formatter.format(date).uppercase(locale)
                }
                probe++
            }
            null
        }
    }

    // Cached "should the thumb absorb touches?" boolean — only flips when the
    // fade animation crosses the threshold. Avoids re-attaching pointerInput
    // every frame as the alpha animates.
    val isThumbInteractive by remember {
        derivedStateOf { thumbVisibility.value > 0.1f }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(OverlayWidth)
            .onSizeChanged { trackHeightPx = it.height },
    ) {
        // Floating label, pinned to the thumb's vertical position but offset
        // far enough left to peek past a dragging finger. Position is a
        // deferred-lambda offset (re-evaluated at layout, not composition); alpha
        // is a graphicsLayer property (re-evaluated at draw, not composition).
        // Both readers stay off the recomposition path.
        if (isDragging) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accent,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val frac = dragFraction ?: scrollFraction
                        val y = ((trackHeightPx - thumbHeightPx) * frac).coerceAtLeast(0f)
                        IntOffset(0, y.toInt())
                    }
                    .padding(end = LabelLeftOffset)
                    .height(ThumbHeight)
                    .graphicsLayer { alpha = labelVisibility.value },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = labelText.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // Thumb itself: small icon-only pill against the right edge. Only attaches
        // its pointerInput when visible enough to be a sensible touch target —
        // otherwise touches in the thumb's area pass through to the LazyColumn
        // underneath, so swiping the right edge of a list with a faded-out thumb
        // doesn't accidentally trigger a thumb drag.
        val pointerMod = if (isThumbInteractive) {
            Modifier.pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = {
                        // Read scroll state freshly. Closing over the local
                        // `effectiveFraction` instead would freeze its first-
                        // composition value (0f) and snap the thumb back to the
                        // top every time the user grabs it.
                        dragFraction = dragFraction ?: scrollFraction
                    },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onVerticalDrag = { change, deltaY ->
                        change.consume()
                        val track = trackHeightPx.toFloat() - thumbHeightPx
                        if (track <= 0f) return@detectVerticalDragGestures
                        val current = dragFraction ?: scrollFraction
                        val newFrac = (current + deltaY / track).coerceIn(0f, 1f)
                        handleDrag(
                            newFrac = newFrac,
                            state = state,
                            onUpdate = { dragFraction = it },
                            coroutineScope = coroutineScope,
                        )
                    },
                )
            }
        } else Modifier

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val frac = dragFraction ?: scrollFraction
                    val y = ((trackHeightPx - thumbHeightPx) * frac).coerceAtLeast(0f)
                    IntOffset(0, y.toInt())
                }
                .graphicsLayer { alpha = thumbVisibility.value }
                .padding(end = ThumbRightInset)
                .size(width = ThumbWidth, height = ThumbHeight)
                .clip(RoundedCornerShape(13.dp))
                .background(accent)
                .semantics {
                    contentDescription = handleDescription
                    role = Role.Button
                }
                .then(pointerMod),
        ) {
            Icon(
                imageVector = Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun handleDrag(
    newFrac: Float,
    state: LazyListState,
    onUpdate: (Float) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return

    val maxIndex = (total - 1).coerceAtLeast(1)
    val rawIdxFloat = newFrac * maxIndex
    val idx = rawIdxFloat.toInt().coerceIn(0, total - 1)

    // Sub-item resolution: integer index + pixel offset within the item, so a
    // short list with only a handful of distinct integer index stops still
    // scrolls smoothly under the drag rather than stair-stepping.
    val itemPx = info.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 0f
    val offsetPx = ((rawIdxFloat - idx) * itemPx).roundToInt().coerceAtLeast(0)
    onUpdate(newFrac)
    coroutineScope.launch { state.scrollToItem(idx, offsetPx) }
}

/**
 * Parse the ISO-8601 datetime that TeslaMate emits (with or without offset) into a
 * [LocalDate]. Returns null on parse failure rather than throwing — the scroll
 * indicator simply treats unparseable rows as undated.
 */
internal fun String?.parseListItemDate(): LocalDate? {
    if (this.isNullOrBlank()) return null
    return try {
        val dt = try {
            OffsetDateTime.parse(this).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(this.replace("Z", ""))
        }
        dt.toLocalDate()
    } catch (_: Exception) {
        null
    }
}

internal fun String?.parseListItemYearMonth(): YearMonth? =
    parseListItemDate()?.let(YearMonth::from)
