package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.ui.theme.CarColorPalette
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.max
import kotlin.math.sqrt

/** A segment in a trip timeline, representing a slice of the trip's wall-clock time. */
sealed class TripTimelineSegment {
    abstract val durationMin: Int

    data class Drive(
        override val durationMin: Int,
        val index: Int,
        val distanceKm: Double
    ) : TripTimelineSegment()

    data class Charge(
        override val durationMin: Int,
        val index: Int,
        val energyKwh: Double,
        val isDc: Boolean
    ) : TripTimelineSegment()

    data class Parking(
        override val durationMin: Int
    ) : TripTimelineSegment()
}

/** A country shown in the timeline's route header (start, end, or intermediate). */
data class TripTimelineCountry(
    val countryCode: String,
    val flagEmoji: String
)

private const val PARKING_LINEAR_THRESHOLD_MIN = 120f
private const val MIN_SEGMENT_RATIO = 0.02f

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
    onCountryClick: (countryCode: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val parkingColor = MaterialTheme.colorScheme.surfaceVariant

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
                startDate = startDate,
                endDate = endDate,
                startCity = startCity,
                endCity = endCity,
                countries = countries,
                palette = palette,
                parkingColor = parkingColor,
                onCountryClick = onCountryClick
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatClockTime(startDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatClockTime(endDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(
    selection: Pair<Int, TripTimelineSegment>?,
    startDate: String,
    endDate: String,
    startCity: String,
    endCity: String,
    countries: List<TripTimelineCountry>,
    palette: CarColorPalette,
    parkingColor: Color,
    onCountryClick: (countryCode: String) -> Unit
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
                parkingColor = parkingColor
            )
        } else {
            RouteHeaderContent(
                startDate = startDate,
                endDate = endDate,
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
    parkingColor: Color
) {
    val title: String
    val subtitle: String
    val dotColor: Color
    when (segment) {
        is TripTimelineSegment.Drive -> {
            title = "${stringResource(R.string.trip_timeline_driving)} · " +
                    stringResource(R.string.trip_leg_drive, segment.index)
            subtitle = "%.1f km · %s".format(
                segment.distanceKm,
                formatTimelineDuration(segment.durationMin)
            )
            dotColor = palette.accent
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
                formatTimelineDuration(segment.durationMin)
            )
            dotColor = if (segment.isDc) palette.dcColor else palette.acColor
        }
        is TripTimelineSegment.Parking -> {
            title = stringResource(R.string.trip_timeline_parked)
            subtitle = formatTimelineDuration(segment.durationMin)
            dotColor = parkingColor
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
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
    }
}

@Composable
private fun RouteHeaderContent(
    startDate: String,
    endDate: String,
    startCity: String,
    endCity: String,
    countries: List<TripTimelineCountry>,
    onCountryClick: (countryCode: String) -> Unit
) {
    val startCountry = countries.firstOrNull()
    val endCountry = if (countries.size >= 2) countries.last() else startCountry
    val intermediate = if (countries.size > 2) countries.subList(1, countries.size - 1) else emptyList()
    val startDateShort = formatShortDate(startDate)
    val endDateShort = formatShortDate(endDate)
    val sameDay = startDateShort == endDateShort

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Endpoint(
            city = startCity,
            date = startDateShort,
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
            date = endDateShort,
            country = endCountry,
            alignment = Alignment.End,
            onCountryClick = onCountryClick,
            // When the trip starts and ends the same day, hide the duplicate date on the end side
            hideDate = sameDay,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Endpoint(
    city: String,
    date: String,
    country: TripTimelineCountry?,
    alignment: Alignment.Horizontal,
    onCountryClick: (countryCode: String) -> Unit,
    modifier: Modifier = Modifier,
    hideDate: Boolean = false
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
        Column(horizontalAlignment = alignment) {
            Text(
                text = city,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!hideDate) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
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

@Composable
private fun TimelineBar(
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
    val cornerRadiusPx = with(density) { 8.dp.toPx() }

    val ratios = remember(segments) { computeSegmentRatios(segments) }

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
                    val color: Color = when (seg) {
                        is TripTimelineSegment.Drive -> palette.accent
                        is TripTimelineSegment.Charge ->
                            if (seg.isDc) palette.dcColor else palette.acColor
                        is TripTimelineSegment.Parking -> parkingColor
                    }
                    val isSelected = idx == selectedIndex
                    val thisBarHeight = if (isSelected) barHeightPx + selectedExtraPx else barHeightPx
                    val y = (size.height - thisBarHeight) / 2f
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(drawWidth, thisBarHeight)
                    )
                    x += segWidth
                }
                // Redraw the rounded clip edges by overlaying small corner triangles? Not needed,
                // the parent Box already clips to RoundedCornerShape. Ignore cornerRadiusPx.
                @Suppress("UNUSED_EXPRESSION") cornerRadiusPx
            }
        }
    }
}

/** Compute visual width ratios for each segment, with parking sqrt-compression and a minimum visual width. */
private fun computeSegmentRatios(segments: List<TripTimelineSegment>): List<Float> {
    if (segments.isEmpty()) return emptyList()
    val weights = segments.map { seg ->
        when (seg) {
            is TripTimelineSegment.Parking -> compressParkingWeight(seg.durationMin)
            else -> seg.durationMin.toFloat().coerceAtLeast(1f)
        }
    }
    val total = weights.sum().coerceAtLeast(1f)
    val raw = weights.map { it / total }
    // Lift to minimum and renormalize (cap min so n * min <= 1)
    val effectiveMin = MIN_SEGMENT_RATIO.coerceAtMost(1f / segments.size)
    val lifted = raw.map { max(it, effectiveMin) }
    val liftedSum = lifted.sum()
    return lifted.map { it / liftedSum }
}

private fun compressParkingWeight(durationMin: Int): Float {
    val d = durationMin.toFloat().coerceAtLeast(1f)
    return if (d <= PARKING_LINEAR_THRESHOLD_MIN) d
    else PARKING_LINEAR_THRESHOLD_MIN +
            sqrt((d - PARKING_LINEAR_THRESHOLD_MIN) * PARKING_LINEAR_THRESHOLD_MIN)
}

private fun formatTimelineDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 24 -> {
            val days = h / 24
            val remH = h % 24
            if (remH > 0) "${days}d ${remH}h" else "${days}d"
        }
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

private fun formatClockTime(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofPattern("d MMM"))
    } catch (e: Exception) {
        dateStr
    }
}
