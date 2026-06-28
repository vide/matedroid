package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.matedroid.R
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.util.formatDuration
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime
import java.util.Locale
import kotlin.math.sqrt

/** A segment in a trip timeline, representing a slice of the trip's wall-clock time. */
@Immutable
sealed class TripTimelineSegment {
    abstract val durationMin: Int

    data class Drive(
        override val durationMin: Int,
        val index: Int,
        val distanceKm: Double,
        val driveId: Int
    ) : TripTimelineSegment()

    data class Charge(
        override val durationMin: Int,
        val index: Int,
        val energyKwh: Double,
        val isDc: Boolean,
        val chargeId: Int
    ) : TripTimelineSegment()

    data class Parking(
        override val durationMin: Int
    ) : TripTimelineSegment()
}

/** A country shown in the timeline's route header (start, end, or intermediate). */
@Immutable
data class TripTimelineCountry(
    val countryCode: String,
    val flagEmoji: String
)

/**
 * "Idle" segments (parking gaps and AC charges) share the same compression curve:
 * linear up to 30 min, sqrt-compressed beyond, scaled 0.5× so multi-hour/day stretches
 * stay visible without crushing the drives. DC sessions are left linear since they're
 * naturally bounded (~1h) and their duration is useful signal.
 */
private const val IDLE_LINEAR_THRESHOLD_MIN = 30f
private const val IDLE_COMPRESSION_SCALE = 0.5f

/**
 * Per-segment "base" share of the bar, added to every segment before the remaining width is
 * handed out strictly in proportion to duration. Using an *additive* base (instead of a hard
 * `max(weight, floor)`) keeps the briefest legs visible and tappable without inflating them to
 * the floor: a 2-minute drive no longer renders nearly as wide as one 40× longer. Capped so the
 * bases never swallow more than [SEGMENT_BASE_MAX_TOTAL] of the bar when there are many segments.
 */
private const val SEGMENT_BASE_FRACTION = 0.012f
private const val SEGMENT_BASE_MAX_TOTAL = 0.4f

/**
 * Horizontal trip timeline bar. Each segment's width is proportional to its duration,
 * with sqrt compression applied to Parking segments longer than 2h so multi-day idle
 * periods don't dominate the bar while drive/charge segments collapse to slivers.
 */
@Composable
fun TripTimeline(
    segments: List<TripTimelineSegment>,
    startDate: String,
    endDate: String,
    startCity: String,
    endCity: String,
    countries: List<TripTimelineCountry>,
    palette: CarColorPalette,
    totalDurationMin: Int,
    totalDrivingDurationMin: Int,
    totalChargingDurationMin: Int,
    onCountryClick: (countryCode: String) -> Unit = {},
    onDriveClick: (driveId: Int) -> Unit = {},
    onChargeClick: (chargeId: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val parkingColor = MaterialTheme.colorScheme.surfaceVariant
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    var selectedIndex by remember(segments) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.trip_timeline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoPanel(
                selection = selectedIndex?.let { idx -> segments.getOrNull(idx)?.let { idx to it } },
                startCity = startCity,
                endCity = endCity,
                countries = countries,
                palette = palette,
                parkingColor = parkingColor,
                onCountryClick = onCountryClick,
                onDriveClick = onDriveClick,
                onChargeClick = onChargeClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            TimelineBar(
                segments = segments,
                palette = palette,
                parkingColor = parkingColor,
                selectedIndex = selectedIndex,
                onSegmentTap = { idx ->
                    selectedIndex = if (selectedIndex == idx) null else idx
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Start / end clock times aligned to the bar's ends.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatClockTime(startDate, is24Hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatClockTime(endDate, is24Hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Driving / charging totals as hero tiles, with the overall total beneath.
            DurationSummary(
                totalDurationMin = totalDurationMin,
                totalDrivingDurationMin = totalDrivingDurationMin,
                totalChargingDurationMin = totalChargingDurationMin,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DurationSummary(
    totalDurationMin: Int,
    totalDrivingDurationMin: Int,
    totalChargingDurationMin: Int,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero tiles: driving + charging are the headline figures.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DurationTile(
                icon = CustomIcons.SteeringWheel,
                label = stringResource(R.string.trip_timeline_driving),
                value = formatDuration(resources, totalDrivingDurationMin),
                accent = palette.accent,
                modifier = Modifier.weight(1f)
            )
            if (totalChargingDurationMin > 0) {
                DurationTile(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.charging),
                    value = formatDuration(resources, totalChargingDurationMin),
                    accent = palette.accent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Overall total — demoted to a quiet caption beneath the tiles.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${formatDuration(resources, totalDurationMin)} · ${stringResource(R.string.total)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DurationTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun InfoPanel(
    selection: Pair<Int, TripTimelineSegment>?,
    startCity: String,
    endCity: String,
    countries: List<TripTimelineCountry>,
    palette: CarColorPalette,
    parkingColor: Color,
    onCountryClick: (countryCode: String) -> Unit,
    onDriveClick: (driveId: Int) -> Unit,
    onChargeClick: (chargeId: Int) -> Unit
) {
    // Reserve a constant height so tapping segments doesn't shift the bar position
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (selection != null) {
            SegmentInfoContent(
                segment = selection.second,
                palette = palette,
                parkingColor = parkingColor,
                onDriveClick = onDriveClick,
                onChargeClick = onChargeClick
            )
        } else {
            RouteHeaderContent(
                startCity = startCity,
                endCity = endCity,
                countries = countries,
                onCountryClick = onCountryClick
            )
        }
    }
}

@Composable
private fun SegmentInfoContent(
    segment: TripTimelineSegment,
    palette: CarColorPalette,
    parkingColor: Color,
    onDriveClick: (driveId: Int) -> Unit,
    onChargeClick: (chargeId: Int) -> Unit
) {
    val resources = LocalContext.current.resources
    val title: String
    val subtitle: String
    val dotColor: Color
    // Drives and charges link to their detail screen; parking gaps have no detail.
    val onClick: (() -> Unit)?
    when (segment) {
        is TripTimelineSegment.Drive -> {
            title = "${stringResource(R.string.trip_timeline_driving)} · " +
                    stringResource(R.string.trip_leg_drive, segment.index)
            subtitle = "%.1f km · %s".format(
                segment.distanceKm,
                formatDuration(resources, segment.durationMin)
            )
            dotColor = palette.accent
            onClick = { onDriveClick(segment.driveId) }
        }
        is TripTimelineSegment.Charge -> {
            val chargeLabel = if (segment.isDc) {
                stringResource(R.string.trip_timeline_charging_dc)
            } else {
                stringResource(R.string.trip_timeline_charging_ac)
            }
            title = "$chargeLabel · " +
                    stringResource(R.string.trip_leg_charge, segment.index)
            subtitle = "+%.1f kWh · %s".format(
                segment.energyKwh,
                formatDuration(resources, segment.durationMin)
            )
            dotColor = if (segment.isDc) palette.dcColor else palette.acColor
            onClick = { onChargeClick(segment.chargeId) }
        }
        is TripTimelineSegment.Parking -> {
            title = stringResource(R.string.trip_timeline_parked)
            subtitle = formatDuration(resources, segment.durationMin)
            dotColor = parkingColor
            onClick = null
        }
    }
    Row(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Chevron signals the detail is tappable (project convention for linked rows).
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RouteHeaderContent(
    startCity: String,
    endCity: String,
    countries: List<TripTimelineCountry>,
    onCountryClick: (countryCode: String) -> Unit
) {
    val startCountry = countries.firstOrNull()
    val endCountry = if (countries.size >= 2) countries.last() else startCountry
    val intermediate = if (countries.size > 2) countries.subList(1, countries.size - 1) else emptyList()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Endpoint(
            city = startCity,
            country = startCountry,
            alignment = Alignment.Start,
            onCountryClick = onCountryClick,
            modifier = Modifier.weight(1f)
        )
        if (intermediate.isNotEmpty()) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                intermediate.forEach { country ->
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = country.flagEmoji,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { onCountryClick(country.countryCode) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Endpoint(
            city = endCity,
            country = endCountry,
            alignment = Alignment.End,
            onCountryClick = onCountryClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Endpoint(
    city: String,
    country: TripTimelineCountry?,
    alignment: Alignment.Horizontal,
    onCountryClick: (countryCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnd = alignment == Alignment.End
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isEnd) Arrangement.End else Arrangement.Start
    ) {
        if (!isEnd && country != null) {
            Text(
                text = country.flagEmoji,
                fontSize = 22.sp,
                modifier = Modifier.clickable { onCountryClick(country.countryCode) }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = city,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isEnd && country != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = country.flagEmoji,
                fontSize = 22.sp,
                modifier = Modifier.clickable { onCountryClick(country.countryCode) }
            )
        }
    }
}

/**
 * Base width every segment gets in the scrollable detail bar, *added* to its proportional width
 * (not used as a hard floor). Keeps the briefest leg a tappable sliver while a longer leg always
 * stays proportionally wider — a 2-minute drive no longer matches the width of a 90-minute one.
 */
private val DETAIL_BASE_SEGMENT_DP = 6.dp

/** Dp per minute of (compressed) weight when sizing the detail bar. Higher = wider. */
private const val DETAIL_WEIGHT_TO_DP = 0.5f

@Composable
private fun TimelineBar(
    segments: List<TripTimelineSegment>,
    palette: CarColorPalette,
    parkingColor: Color,
    selectedIndex: Int?,
    onSegmentTap: (Int) -> Unit
) {
    val density = LocalDensity.current

    // Pixel widths each segment would occupy in the detail bar (not yet laid out).
    // Additive base + proportional, so brief legs stay thin instead of snapping to a fat floor.
    val detailWidthsPx = remember(segments, density.density) {
        val basePx = with(density) { DETAIL_BASE_SEGMENT_DP.toPx() }
        val pxPerWeight = DETAIL_WEIGHT_TO_DP * density.density
        segments.map { seg ->
            basePx + segmentWeight(seg) * pxPerWeight
        }
    }
    val totalDetailPx = detailWidthsPx.sum()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportPx = with(density) { maxWidth.toPx() }
        // Overflow only when the detail bar genuinely won't fit. Short trips fall through to the
        // single-bar rendering and look identical to the previous version.
        val overflows = totalDetailPx > viewportPx + 1f

        if (!overflows) {
            SingleBar(
                segments = segments,
                palette = palette,
                parkingColor = parkingColor,
                selectedIndex = selectedIndex,
                onSegmentTap = onSegmentTap
            )
        } else {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Minimap(
                    segments = segments,
                    palette = palette,
                    parkingColor = parkingColor,
                    totalDetailPx = totalDetailPx,
                    viewportPx = viewportPx,
                    scrollState = scrollState
                )
                Spacer(Modifier.height(8.dp))
                ScrollableDetailBar(
                    segments = segments,
                    widthsPx = detailWidthsPx,
                    palette = palette,
                    parkingColor = parkingColor,
                    selectedIndex = selectedIndex,
                    onSegmentTap = onSegmentTap,
                    scrollState = scrollState
                )
            }
        }
    }
}

/** Single horizontal bar filling the viewport — unchanged behaviour for short trips. */
@Composable
private fun SingleBar(
    segments: List<TripTimelineSegment>,
    palette: CarColorPalette,
    parkingColor: Color,
    selectedIndex: Int?,
    onSegmentTap: (Int) -> Unit
) {
    val density = LocalDensity.current
    val barHeightPx = with(density) { 16.dp.toPx() }
    val selectedExtraPx = with(density) { 4.dp.toPx() }
    val gapPx = with(density) { 1.dp.toPx() }
    val iconSizePx = with(density) { 11.dp.toPx() }

    val ratios = remember(segments) { computeSegmentRatios(segments) }
    val firstByKind = remember(segments) { firstOccurrenceByKind(segments) }
    val iconPainters = rememberSegmentIconPainters()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(segments) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    if (w <= 0f) return@detectTapGestures
                    var accum = 0f
                    for ((idx, r) in ratios.withIndex()) {
                        val segW = r * w
                        if (offset.x <= accum + segW) {
                            onSegmentTap(idx)
                            return@detectTapGestures
                        }
                        accum += segW
                    }
                    onSegmentTap(ratios.lastIndex)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val total = size.width
                var x = 0f
                ratios.forEachIndexed { idx, ratio ->
                    val segWidth = ratio * total
                    val isLast = idx == ratios.lastIndex
                    val drawWidth = if (isLast) segWidth else (segWidth - gapPx).coerceAtLeast(0f)
                    val seg = segments[idx]
                    val color = colorForSegment(seg, palette, parkingColor)
                    val isSelected = idx == selectedIndex
                    val thisBarHeight = if (isSelected) barHeightPx + selectedExtraPx else barHeightPx
                    val y = (size.height - thisBarHeight) / 2f
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(drawWidth, thisBarHeight)
                    )
                    val kind = seg.kind()
                    if (firstByKind[kind] == idx) {
                        iconPainters[kind]?.let { painter ->
                            drawSegmentIcon(
                                painter = painter,
                                centerX = x + drawWidth / 2f,
                                centerY = size.height / 2f,
                                iconSizePx = iconSizePx,
                                segmentWidthPx = drawWidth
                            )
                        }
                    }
                    x += segWidth
                }
            }
        }
    }
}

/**
 * Compressed non-interactive overview of the full trip. The viewport rectangle tracks the
 * scroll position of the paired [ScrollableDetailBar]. Tap anywhere to jump the detail bar.
 */
@Composable
private fun Minimap(
    segments: List<TripTimelineSegment>,
    palette: CarColorPalette,
    parkingColor: Color,
    totalDetailPx: Float,
    viewportPx: Float,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val ratios = remember(segments) { computeSegmentRatios(segments) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Bar is 6dp tall; indicator is 2.5× that (15dp) centered vertically so it stands out
    // above and below the bar. Outer container grows to accommodate the indicator and captures
    // taps + horizontal drags anywhere on the strip — both jump/slide the detail bar.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(segments, totalDetailPx) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    val target = (ratio * totalDetailPx - viewportPx / 2f).toInt()
                        .coerceAtLeast(0)
                    coroutineScope.launch { scrollState.scrollTo(target) }
                }
            }
            .pointerInput(totalDetailPx) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val minimapW = size.width.toFloat()
                    if (minimapW <= 0f) return@detectHorizontalDragGestures
                    scrollState.dispatchRawDelta(dragAmount * (totalDetailPx / minimapW))
                }
            }
    ) {
        val minimapWidthPx = with(density) { maxWidth.toPx() }
        val widthFrac by remember(totalDetailPx, viewportPx) {
            derivedStateOf {
                if (totalDetailPx <= 0f) 1f
                else (viewportPx / totalDetailPx).coerceIn(0.05f, 1f)
            }
        }
        val offsetFrac by remember(totalDetailPx, viewportPx) {
            derivedStateOf {
                if (totalDetailPx <= 0f) 0f
                else {
                    val wFrac = if (totalDetailPx <= 0f) 1f
                    else (viewportPx / totalDetailPx).coerceIn(0.05f, 1f)
                    (scrollState.value / totalDetailPx).coerceIn(0f, 1f - wFrac)
                }
            }
        }

        // The compressed bar — vertically centered
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(3.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var x = 0f
                ratios.forEachIndexed { idx, ratio ->
                    val w = ratio * size.width
                    drawRect(
                        color = colorForSegment(segments[idx], palette, parkingColor),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height)
                    )
                    x += w
                }
            }
        }

        // Viewport indicator — filled translucent rectangle in the car's accent color,
        // 15dp tall (2.5× the bar). CenterStart alignment anchors the indicator's left edge
        // to the container's left edge so `.offset` shifts it honestly from x=0.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((offsetFrac * minimapWidthPx).toInt(), 0) }
                .width(with(density) { (widthFrac * minimapWidthPx).toDp() })
                .height(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.accent.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun ScrollableDetailBar(
    segments: List<TripTimelineSegment>,
    widthsPx: List<Float>,
    palette: CarColorPalette,
    parkingColor: Color,
    selectedIndex: Int?,
    onSegmentTap: (Int) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val density = LocalDensity.current
    val barHeightPx = with(density) { 20.dp.toPx() }
    val selectedExtraPx = with(density) { 6.dp.toPx() }
    val gapPx = with(density) { 1.dp.toPx() }
    val iconSizePx = with(density) { 13.dp.toPx() }
    val totalDp = with(density) { widthsPx.sum().toDp() }

    val firstByKind = remember(segments) { firstOccurrenceByKind(segments) }
    val iconPainters = rememberSegmentIconPainters()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .width(totalDp)
                .height(44.dp)
                .pointerInput(segments, widthsPx) {
                    detectTapGestures { offset ->
                        var accum = 0f
                        for ((idx, w) in widthsPx.withIndex()) {
                            if (offset.x <= accum + w) {
                                onSegmentTap(idx)
                                return@detectTapGestures
                            }
                            accum += w
                        }
                        onSegmentTap(widthsPx.lastIndex)
                    }
                }
        ) {
            var x = 0f
            widthsPx.forEachIndexed { idx, w ->
                val isLast = idx == widthsPx.lastIndex
                val drawW = if (isLast) w else (w - gapPx).coerceAtLeast(0f)
                val seg = segments[idx]
                val color = colorForSegment(seg, palette, parkingColor)
                val isSelected = idx == selectedIndex
                val h = if (isSelected) barHeightPx + selectedExtraPx else barHeightPx
                val y = (size.height - h) / 2f
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(drawW, h)
                )
                val kind = seg.kind()
                if (firstByKind[kind] == idx) {
                    iconPainters[kind]?.let { painter ->
                        drawSegmentIcon(
                            painter = painter,
                            centerX = x + drawW / 2f,
                            centerY = size.height / 2f,
                            iconSizePx = iconSizePx,
                            segmentWidthPx = drawW
                        )
                    }
                }
                x += w
            }
        }
    }
}

private enum class SegmentKind { DRIVE, DC_CHARGE, AC_CHARGE, PARKING }

private fun TripTimelineSegment.kind(): SegmentKind = when (this) {
    is TripTimelineSegment.Drive -> SegmentKind.DRIVE
    is TripTimelineSegment.Charge -> if (isDc) SegmentKind.DC_CHARGE else SegmentKind.AC_CHARGE
    is TripTimelineSegment.Parking -> SegmentKind.PARKING
}

/** Index of the first segment of each distinct kind, keyed by [SegmentKind]. */
private fun firstOccurrenceByKind(segments: List<TripTimelineSegment>): Map<SegmentKind, Int> {
    val out = HashMap<SegmentKind, Int>(4)
    for ((idx, seg) in segments.withIndex()) {
        val k = seg.kind()
        if (k !in out) out[k] = idx
    }
    return out
}

@Composable
private fun rememberSegmentIconPainters(): Map<SegmentKind, VectorPainter> {
    val drive = rememberVectorPainter(com.matedroid.ui.icons.CustomIcons.SteeringWheel)
    val dc = rememberVectorPainter(Icons.Filled.ElectricBolt)
    val ac = rememberVectorPainter(Icons.Filled.Power)
    val parking = rememberVectorPainter(Icons.Filled.LocalParking)
    return remember(drive, dc, ac, parking) {
        mapOf(
            SegmentKind.DRIVE to drive,
            SegmentKind.DC_CHARGE to dc,
            SegmentKind.AC_CHARGE to ac,
            SegmentKind.PARKING to parking
        )
    }
}

/** Draw a small white icon centered on [centerX], [centerY] — skipped if the segment is too narrow. */
private fun DrawScope.drawSegmentIcon(
    painter: VectorPainter,
    centerX: Float,
    centerY: Float,
    iconSizePx: Float,
    segmentWidthPx: Float
) {
    if (segmentWidthPx < iconSizePx + 4f) return
    translate(centerX - iconSizePx / 2f, centerY - iconSizePx / 2f) {
        with(painter) {
            draw(
                size = Size(iconSizePx, iconSizePx),
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.95f))
            )
        }
    }
}

private fun colorForSegment(
    seg: TripTimelineSegment,
    palette: CarColorPalette,
    parkingColor: Color
): Color = when (seg) {
    is TripTimelineSegment.Drive -> palette.accent
    is TripTimelineSegment.Charge -> if (seg.isDc) palette.dcColor else palette.acColor
    is TripTimelineSegment.Parking -> parkingColor
}

/** Visual weight for one segment — drives and DC charges are honest, AC and parking are sqrt-compressed. */
private fun segmentWeight(seg: TripTimelineSegment): Float = when (seg) {
    is TripTimelineSegment.Parking -> compressIdle(seg.durationMin)
    is TripTimelineSegment.Charge ->
        if (seg.isDc) seg.durationMin.toFloat().coerceAtLeast(1f)
        else compressIdle(seg.durationMin)
    is TripTimelineSegment.Drive -> seg.durationMin.toFloat().coerceAtLeast(1f)
}

/**
 * Compute visual width ratios for each segment. Each segment gets a small fixed base share, and
 * the remaining width is split strictly in proportion to (compressed) duration. This keeps the
 * shortest legs as visible slivers while preserving honest proportions — unlike a hard minimum
 * floor, which would round every brief leg up to the same fat width. Ratios sum to 1.
 */
private fun computeSegmentRatios(segments: List<TripTimelineSegment>): List<Float> {
    if (segments.isEmpty()) return emptyList()
    val weights = segments.map { segmentWeight(it) }
    val total = weights.sum().coerceAtLeast(1f)
    val raw = weights.map { it / total }
    // Per-segment base, capped so all bases together never exceed SEGMENT_BASE_MAX_TOTAL.
    val base = SEGMENT_BASE_FRACTION.coerceAtMost(SEGMENT_BASE_MAX_TOTAL / segments.size)
    val proportionalShare = 1f - base * segments.size
    return raw.map { base + it * proportionalShare }
}

/** Linear up to 30min, then sqrt-compressed and scaled down. Used for both parking gaps and AC charges. */
private fun compressIdle(durationMin: Int): Float {
    val d = durationMin.toFloat().coerceAtLeast(1f)
    return if (d <= IDLE_LINEAR_THRESHOLD_MIN) d
    else IDLE_LINEAR_THRESHOLD_MIN +
            sqrt((d - IDLE_LINEAR_THRESHOLD_MIN) * IDLE_LINEAR_THRESHOLD_MIN) * IDLE_COMPRESSION_SCALE
}

private fun formatClockTime(dateStr: String, is24Hour: Boolean): String {
    val dt = parseIsoDateTime(dateStr) ?: return dateStr
    return dt.formatTime(Locale.getDefault(), is24Hour)
}

