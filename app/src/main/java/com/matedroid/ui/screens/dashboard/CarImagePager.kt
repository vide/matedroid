package com.matedroid.ui.screens.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.local.CarImageOverride
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.util.GlowBitmapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CarSelectorPager(
    cars: List<CarData>,
    selectedCarId: Int?,
    onSelectCar: (Int) -> Unit,
    carImageOverrides: Map<Int, CarImageOverride>,
    palette: CarColorPalette,
    isCharging: Boolean,
    isDcCharging: Boolean,
    onNavigateToStats: (() -> Unit)?,
    onCarImageLongPress: (() -> Unit)?,
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    imageOverride: CarImageOverride?
) {
    val isDarkTheme = isSystemInDarkTheme()
    if (cars.size > 1) {
        val initialPage = cars.indexOfFirst { it.carId == selectedCarId }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialPage) { cars.size }

        // Sync pager position when selected car changes externally
        LaunchedEffect(selectedCarId) {
            val targetPage = cars.indexOfFirst { it.carId == selectedCarId }.coerceAtLeast(0)
            if (targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }

        // Notify viewmodel when user swipes to a new car
        LaunchedEffect(pagerState.settledPage) {
            val car = cars.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            if (car.carId != selectedCarId) {
                onSelectCar(car.carId)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val car = cars[page]
            val isSettled = page == pagerState.settledPage
            val carPalette = CarColorPalettes.forExteriorColor(car.carExterior?.exteriorColor, isDarkTheme)
            CarImage(
                carModel = car.carDetails?.model,
                carTrimBadging = car.carDetails?.trimBadging,
                carExterior = car.carExterior,
                palette = carPalette,
                modifier = Modifier.fillMaxWidth(),
                isCharging = if (isSettled) isCharging else false,
                isDcCharging = if (isSettled) isDcCharging else false,
                accentColor = carPalette.accent,
                carSurfaceColor = carPalette.surface,
                imageOverride = carImageOverrides[car.carId],
                onNavigateToStats = if (isSettled) onNavigateToStats else null,
                onLongPress = if (isSettled) onCarImageLongPress else null
            )
        }

        // Dots indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(cars.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) palette.accent
                            else palette.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    } else {
        // Single car — existing behaviour unchanged
        CarImage(
            carModel = carModel,
            carTrimBadging = carTrimBadging,
            carExterior = carExterior,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            isCharging = isCharging,
            isDcCharging = isDcCharging,
            accentColor = palette.accent,
            carSurfaceColor = palette.surface,
            imageOverride = imageOverride,
            onNavigateToStats = onNavigateToStats,
            onLongPress = onCarImageLongPress
        )
    }
}

// createGlowBitmap moved to GlowBitmapRenderer for reuse by the home screen widget
private fun createGlowBitmap(source: Bitmap, glowColor: Color, glowRadius: Float): Bitmap =
    GlowBitmapRenderer.createGlowBitmap(source, glowColor, glowRadius)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarImage(
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    palette: CarColorPalette,
    modifier: Modifier = Modifier,
    isCharging: Boolean = false,
    isDcCharging: Boolean = false,
    accentColor: Color = Color.Transparent,
    carSurfaceColor: Color = Color.Transparent,
    imageOverride: CarImageOverride? = null,
    onNavigateToStats: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Use override if set and valid for current car config, otherwise auto-detect
    val colorCode = remember(carExterior) { CarImageResolver.mapColor(carExterior?.exteriorColor) }
    val isOverrideValid = remember(carModel, colorCode, carTrimBadging, carExterior, imageOverride) {
        if (imageOverride == null) false
        else CarImageResolver.getVariantsForModel(
            carModel, colorCode, carTrimBadging, carExterior?.wheelType
        ).any { it.id == imageOverride.variant }
    }

    val assetPath = remember(carModel, carTrimBadging, carExterior, imageOverride, isOverrideValid) {
        if (imageOverride != null && isOverrideValid) {
            CarImageResolver.getAssetPathForOverride(
                variant = imageOverride.variant,
                colorCode = colorCode,
                wheelCode = imageOverride.wheelCode
            )
        } else {
            CarImageResolver.getAssetPath(
                model = carModel,
                exteriorColor = carExterior?.exteriorColor,
                wheelType = carExterior?.wheelType,
                trimBadging = carTrimBadging
            )
        }
    }

    val scaleFactor = remember(carModel, carTrimBadging, carExterior, imageOverride, isOverrideValid) {
        if (imageOverride != null && isOverrideValid) {
            CarImageResolver.getScaleFactorForVariant(imageOverride.variant)
        } else {
            CarImageResolver.getScaleFactor(
                model = carModel,
                exteriorColor = carExterior?.exteriorColor,
                wheelType = carExterior?.wheelType,
                trimBadging = carTrimBadging
            )
        }
    }

    // Decode off the main thread: this runs per pager swipe between cars
    val bitmap = produceState<Bitmap?>(initialValue = null, assetPath) {
        // Reset before decoding so a stale image never shows when the car changes
        value = null
        value = withContext(Dispatchers.Default) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                // Try fallback to default
                try {
                    val fallbackPath = CarImageResolver.getDefaultAssetPath(carModel)
                    context.assets.open(fallbackPath).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }.value

    // Glow radius in pixels
    val glowRadius = 70f

    // AC/DC color tint
    val chargeTypeColor = if (isDcCharging) palette.dcColor else palette.acColor

    // Breathing animation - smooth in/out
    val infiniteTransition = rememberInfiniteTransition(label = "chargingBreath")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathProgress"
    )

    // Breathing glow: alpha pulses between 0.3 and 0.9
    val glowAlpha = 0.3f + (breathProgress * 0.6f)
    // Color subtly shifts between accent and a blend with AC/DC color
    val glowColor = androidx.compose.ui.graphics.lerp(accentColor, chargeTypeColor, breathProgress * 0.4f)

    // Create single glow bitmap — triple-blur render is expensive, keep it off the main thread
    val glowBitmap = produceState<Bitmap?>(initialValue = null, bitmap, isCharging) {
        // Reset so a stale glow never lingers over a different bitmap
        value = null
        value = if (isCharging && bitmap != null) {
            withContext(Dispatchers.Default) {
                createGlowBitmap(
                    source = bitmap,
                    glowColor = Color.White,
                    glowRadius = glowRadius
                )
            }
        } else {
            null
        }
    }.value

    // Calculate scale compensation for glow (glow bitmap is larger due to padding)
    val glowScaleCompensation = remember(bitmap, glowBitmap) {
        if (bitmap != null && glowBitmap != null) {
            glowBitmap.width.toFloat() / bitmap.width.toFloat()
        } else {
            1f
        }
    }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .height(210.dp)
                .then(
                    if (onNavigateToStats != null || onLongPress != null) {
                        Modifier.combinedClickable(
                            onClick = { onNavigateToStats?.invoke() },
                            onLongClick = { onLongPress?.invoke() }
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Draw breathing glow behind the car when charging
            if (glowBitmap != null && isCharging) {
                Image(
                    bitmap = glowBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleFactor * glowScaleCompensation
                            scaleY = scaleFactor * glowScaleCompensation
                            alpha = glowAlpha
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(glowColor, BlendMode.SrcIn)
                )
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.car_image_tap_for_stats),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                    },
                contentScale = ContentScale.Fit
            )
            // Stats button on middle-right side
            if (onNavigateToStats != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.view_stats),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
