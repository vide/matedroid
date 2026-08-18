package com.matedroid.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.domain.SinceLastChargeStats
import com.matedroid.ui.theme.CarColorPalette

/**
 * Shared height of every page in the dashboard carousel — the Location card's
 * map geometry (pin offset, scrim) is tuned to this value.
 */
internal val DASHBOARD_CAROUSEL_HEIGHT = 172.dp

private enum class CarouselPage { Location, SinceLastCharge }

/**
 * The dashboard's swipeable card slot: page 1 is the current-position map,
 * page 2 the "Since last charge" summary (issue #339). Pages that have no data
 * are omitted; with a single page it renders as a plain card, no dots.
 *
 * Pager position is deliberately NOT persisted to disk: rememberPagerState keeps
 * it through rotation and process recreation, but a cold start opens on the map.
 */
@Composable
internal fun DashboardCarousel(
    status: CarStatus,
    units: Units?,
    resolvedAddress: String?,
    sinceLastCharge: SinceLastChargeStats?,
    palette: CarColorPalette,
    onNavigateToDrives: () -> Unit
) {
    val pages = buildList {
        if (status.latitude != null && status.longitude != null) add(CarouselPage.Location)
        if (sinceLastCharge != null) add(CarouselPage.SinceLastCharge)
    }
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState { pages.size }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (pages[page]) {
                CarouselPage.Location -> LocationCard(
                    status = status,
                    units = units,
                    resolvedAddress = resolvedAddress,
                    palette = palette
                )
                CarouselPage.SinceLastCharge -> SinceLastChargeCard(
                    stats = sinceLastCharge!!,
                    currentBatteryLevel = status.batteryLevel,
                    units = units,
                    palette = palette,
                    onClick = onNavigateToDrives
                )
            }
        }

        // Dots below the cards, matching the multi-car selector pager — overlaying
        // them on the cards either covered the map or collided with the label text.
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) palette.accent
                                else palette.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/** A small translucent chip used on the carousel cards (map overlay, consumption stats). */
@Composable
internal fun CarouselChip(icon: ImageVector, text: String) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0E1216)
    val bg = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content.copy(alpha = 0.9f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.92f),
            maxLines = 1
        )
    }
}
