package com.matedroid.ui.screens.dashboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarGeodata
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.data.api.models.CarVersions
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.TpmsDetails
import com.matedroid.data.api.models.ClimateDetails
import com.matedroid.domain.model.BatteryTypeHelper
import com.matedroid.ui.components.calculateAcGaugeProgress
import com.matedroid.ui.components.calculateDcGaugeProgress
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCharges: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToDrives: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToBattery: (carId: Int, efficiency: Double?, exteriorColor: String?) -> Unit = { _, _, _ -> },
    onNavigateToMileage: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToUpdates: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToStats: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCarSelector by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Car selector dialog
    if (showCarSelector && uiState.hasMultipleCars) {
        AlertDialog(
            onDismissRequest = { showCarSelector = false },
            title = { Text("Select Vehicle") },
            text = {
                Column {
                    uiState.cars.forEach { car ->
                        val isSelected = car.carId == uiState.selectedCarId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCar(car.carId)
                                    showCarSelector = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = car.displayName ?: "Car ${car.carId}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCarSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.hasMultipleCars) {
                        Row(
                            modifier = Modifier.clickable { showCarSelector = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(uiState.selectedCarName ?: "MateDroid")
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select car",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Text(uiState.selectedCarName ?: "MateDroid")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.carStatus != null -> {
                    DashboardContent(
                        status = uiState.carStatus!!,
                        units = uiState.units,
                        carModel = uiState.selectedCarModel,
                        carTrimBadging = uiState.selectedCarTrimBadging,
                        carExterior = uiState.selectedCarExterior,
                        resolvedAddress = uiState.resolvedAddress,
                        totalCharges = uiState.totalCharges,
                        totalDrives = uiState.totalDrives,
                        onNavigateToCharges = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToCharges(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToDrives = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToDrives(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToBattery = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToBattery(carId, uiState.selectedCarEfficiency, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToMileage = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToMileage(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToUpdates = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToUpdates(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToStats = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToStats(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        }
                    )
                }
                uiState.cars.isEmpty() && uiState.error == null -> {
                    EmptyContent()
                }
                uiState.error != null -> {
                    ErrorContent(message = uiState.error!!)
                }
                else -> {
                    // Car status still loading after cars loaded
                    LoadingContent()
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading vehicle data...")
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No vehicles found",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Make sure TeslamateApi is properly configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error loading data",
                style = MaterialTheme.typography.titleMedium,
                color = StatusError
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardContent(
    status: CarStatus,
    units: Units? = null,
    carModel: String? = null,
    carTrimBadging: String? = null,
    carExterior: CarExterior? = null,
    resolvedAddress: String? = null,
    totalCharges: Int? = null,
    totalDrives: Int? = null,
    onNavigateToCharges: () -> Unit = {},
    onNavigateToDrives: () -> Unit = {},
    onNavigateToBattery: () -> Unit = {},
    onNavigateToMileage: () -> Unit = {},
    onNavigateToUpdates: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(carExterior?.exteriorColor, isDarkTheme)

    // Scrollable content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Section with Car Image (tappable for battery health)
        BatteryCard(
            status = status,
            units = units,
            carModel = carModel,
            carTrimBadging = carTrimBadging,
            carExterior = carExterior,
            onNavigateToBattery = onNavigateToBattery,
            onNavigateToStats = onNavigateToStats
        )

        // Location Section - show if we have coordinates
        if (status.latitude != null && status.longitude != null) {
            LocationCard(status = status, units = units, resolvedAddress = resolvedAddress, palette = palette)
        }

        // Vehicle Info Card with navigation buttons
        VehicleInfoCard(
            status = status,
            units = units,
            palette = palette,
            totalCharges = totalCharges,
            totalDrives = totalDrives,
            onNavigateToCharges = onNavigateToCharges,
            onNavigateToDrives = onNavigateToDrives,
            onNavigateToMileage = onNavigateToMileage,
            onNavigateToUpdates = onNavigateToUpdates
        )
    }
}

/**
 * Creates a glow bitmap from the alpha channel of the source bitmap.
 * The glow follows the shape of the non-transparent pixels.
 *
 * @param source The source bitmap with transparency
 * @param glowColor The color for the glow effect
 * @param glowRadius The radius of the blur effect in pixels
 * @return A new bitmap containing only the glow effect
 */
private fun createGlowBitmap(source: Bitmap, glowColor: Color, glowRadius: Float): Bitmap {
    // Create a larger bitmap to accommodate the glow extending beyond the original bounds
    val padding = (glowRadius * 2).toInt()
    val glowBitmap = Bitmap.createBitmap(
        source.width + padding * 2,
        source.height + padding * 2,
        Bitmap.Config.ARGB_8888
    )

    val canvas = AndroidCanvas(glowBitmap)

    // Extract alpha from source first
    val alphaBitmap = source.extractAlpha()

    // Create paint with blur effect - use OUTER blur for glow effect
    val glowPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(
            (glowColor.alpha * 255).toInt(),
            (glowColor.red * 255).toInt(),
            (glowColor.green * 255).toInt(),
            (glowColor.blue * 255).toInt()
        )
        maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.OUTER)
    }

    // Draw the blurred alpha multiple times for a stronger glow effect
    repeat(3) {
        canvas.drawBitmap(alphaBitmap, padding.toFloat(), padding.toFloat(), glowPaint)
    }

    alphaBitmap.recycle()

    return glowBitmap
}

@Composable
private fun CarImage(
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    modifier: Modifier = Modifier,
    isCharging: Boolean = false,
    isDcCharging: Boolean = false,
    accentColor: Color = Color.Transparent,
    carSurfaceColor: Color = Color.Transparent,
    onNavigateToStats: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val assetPath = remember(carModel, carTrimBadging, carExterior) {
        CarImageResolver.getAssetPath(
            model = carModel,
            exteriorColor = carExterior?.exteriorColor,
            wheelType = carExterior?.wheelType,
            trimBadging = carTrimBadging
        )
    }

    val scaleFactor = remember(carModel, carTrimBadging, carExterior) {
        CarImageResolver.getScaleFactor(
            model = carModel,
            exteriorColor = carExterior?.exteriorColor,
            wheelType = carExterior?.wheelType,
            trimBadging = carTrimBadging
        )
    }

    val bitmap = remember(assetPath) {
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

    // Glow radius in pixels
    val glowRadius = 70f

    // AC/DC color tint
    val chargeTypeColor = if (isDcCharging) StatusWarning else StatusSuccess

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

    // Create single glow bitmap
    val glowBitmap = remember(bitmap, isCharging) {
        if (isCharging && bitmap != null) {
            createGlowBitmap(
                source = bitmap,
                glowColor = Color.White,
                glowRadius = glowRadius
            )
        } else {
            null
        }
    }

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
                    if (onNavigateToStats != null) {
                        Modifier.clickable(onClick = onNavigateToStats)
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
                contentDescription = "Car image - tap for stats",
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
                        contentDescription = "View stats",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicatorsRow(
    status: CarStatus,
    units: Units?,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: State and Lock
        Row(verticalAlignment = Alignment.CenterVertically) {
            // State indicator - icon changes based on state
            val stateIcon = when {
                status.isCharging || status.pluggedIn == true -> Icons.Filled.PowerSettingsNew
                status.state?.lowercase() == "online" -> Icons.Filled.Circle
                status.state?.lowercase() in listOf("asleep", "offline", "suspended") -> Icons.Filled.Bedtime
                else -> Icons.Filled.Circle
            }
            val stateColor = when {
                status.isCharging -> StatusSuccess
                status.pluggedIn == true -> palette.accent
                status.state?.lowercase() == "online" -> StatusSuccess
                else -> palette.onSurfaceVariant
            }
            Icon(
                imageVector = stateIcon,
                contentDescription = null,
                modifier = Modifier.size(if (stateIcon == Icons.Filled.Circle) 10.dp else 16.dp),
                tint = stateColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.state?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                style = MaterialTheme.typography.labelMedium,
                color = stateColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Locked indicator
            val isLocked = status.locked == true
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isLocked) StatusSuccess else StatusWarning
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isLocked) "Locked" else "Unlocked",
                style = MaterialTheme.typography.labelMedium,
                color = if (isLocked) StatusSuccess else StatusWarning
            )

            // Plug indicator (only when plugged in but not charging - charging already shows state)
            if (status.pluggedIn == true && !status.isCharging) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Power,
                    contentDescription = "Plugged in",
                    modifier = Modifier.size(16.dp),
                    tint = palette.accent
                )
            }
        }

        // Right side: Temperatures
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Inside temp
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = status.insideTemp?.let { UnitFormatter.formatTemperature(it, units) } ?: "--",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Outside temp
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = status.outsideTemp?.let { UnitFormatter.formatTemperature(it, units) } ?: "--",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent
            )
        }
    }
}

@Composable
private fun BatteryCard(
    status: CarStatus,
    units: Units?,
    carModel: String? = null,
    carTrimBadging: String? = null,
    carExterior: CarExterior? = null,
    onNavigateToBattery: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(carExterior?.exteriorColor, isDarkTheme)

    val batteryLevel = status.batteryLevel ?: 0
    val batteryColor = when {
        batteryLevel < 20 -> StatusError
        batteryLevel < 40 -> StatusWarning
        else -> palette.onSurface
    }
    val chargeLimit = status.chargeLimitSoc ?: 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
        ) {
            // Status indicators row at the top
            StatusIndicatorsRow(
                status = status,
                units = units,
                palette = palette,
                modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
            )

            // Car image with pulsing glow effect when charging
            CarImage(
                carModel = carModel,
                carTrimBadging = carTrimBadging,
                carExterior = carExterior,
                modifier = Modifier.fillMaxWidth(),
                isCharging = status.isCharging,
                isDcCharging = status.isDcCharging,
                accentColor = palette.accent,
                carSurfaceColor = palette.surface,
                onNavigateToStats = onNavigateToStats
            )

            // Battery info row - tappable to navigate to battery health
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.onSurface.copy(alpha = 0.06f))
                    .clickable(onClick = onNavigateToBattery)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Battery percentage with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = "Tap for battery health",
                        modifier = Modifier.size(28.dp),
                        tint = batteryColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                    if (status.isCharging) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Mini charging gauge with AC/DC badge
                        ChargingPowerGaugeCompact(
                            status = status,
                            carTrimBadging = carTrimBadging
                        )
                    }
                    if (batteryLevel > 90 && !status.isCharging) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "High charge level",
                            modifier = Modifier.size(20.dp),
                            tint = StatusWarning
                        )
                    }
                }

                // Center: Range and limit
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = status.ratedBatteryRangeKm?.let { UnitFormatter.formatDistance(it, units, 0) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                    Text(
                        text = "Limit: ${status.chargeLimitSoc ?: "--"}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }

                // Right: Chevron to indicate tappable
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.onSurfaceVariant
                )
            }

            // Charging section - always reserve space for consistent card height
            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar - always shown but different appearance when not charging
            ChargingProgressBar(
                currentLevel = batteryLevel,
                targetLevel = chargeLimit,
                isCharging = status.isCharging,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Charging info row - shows details when charging
            if (status.isCharging) {
                ChargingDetailsRow(
                    status = status,
                    palette = palette
                )
            }
        }
    }
}

@Composable
private fun ChargingProgressBar(
    currentLevel: Int,
    targetLevel: Int,
    isCharging: Boolean = false,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val currentFraction = currentLevel / 100f
    val targetFraction = targetLevel / 100f
    val solidGreen = StatusSuccess
    val dimmedGreen = StatusSuccess.copy(alpha = 0.3f)

    Canvas(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val width = size.width
        val height = size.height

        // Background
        drawRect(
            color = palette.progressTrack,
            size = size
        )

        if (isCharging) {
            // Charging: show green with target area
            // Dimmed green for target area (from current to target)
            if (targetFraction > currentFraction) {
                drawRect(
                    color = dimmedGreen,
                    topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width * (targetFraction - currentFraction),
                        height
                    )
                )
            }
            // Solid green for current charge level
            drawRect(
                color = solidGreen,
                size = androidx.compose.ui.geometry.Size(width * currentFraction, height)
            )
        } else {
            // Not charging: show accent color with limit marker
            // Dimmed accent for limit area
            if (targetFraction > currentFraction) {
                drawRect(
                    color = palette.accentDim,
                    topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width * (targetFraction - currentFraction),
                        height
                    )
                )
            }
            // Solid accent for current charge level
            drawRect(
                color = palette.accent,
                size = androidx.compose.ui.geometry.Size(width * currentFraction, height)
            )
        }
    }
}

/**
 * Compact inline gauge with AC/DC badge for the battery info row.
 */
@Composable
private fun ChargingPowerGaugeCompact(
    status: CarStatus,
    carTrimBadging: String?
) {
    val isDcCharging = status.isDcCharging
    val powerKw = status.chargerPower ?: 0
    val gaugeColor = if (isDcCharging) StatusWarning else StatusSuccess

    // Calculate gauge progress based on charging type
    val gaugeProgress = if (isDcCharging) {
        val maxPower = BatteryTypeHelper.getMaxDcPowerKw(carTrimBadging)
        calculateDcGaugeProgress(powerKw, maxPower)
    } else {
        calculateAcGaugeProgress(
            actualCurrent = status.chargerActualCurrent,
            maxCurrent = status.chargeCurrentRequestMax
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Mini circular gauge with power value
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(36.dp)) {
                val strokeWidth = 3.dp.toPx()
                val arcSize = size.minDimension - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
                val startAngle = 135f
                val sweepAngle = 270f

                // Track
                drawArc(
                    color = gaugeColor.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress
                val progressSweep = sweepAngle * gaugeProgress.coerceIn(0f, 1f)
                if (progressSweep > 0) {
                    drawArc(
                        color = gaugeColor,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Power value and kW label stacked in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$powerKw",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor,
                    lineHeight = 10.sp
                )
                Text(
                    text = "kW",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = gaugeColor,
                    lineHeight = 8.sp
                )
            }
        }

        // AC/DC badge
        Box(
            modifier = Modifier
                .background(
                    color = gaugeColor,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isDcCharging) "DC" else "AC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

/**
 * Row showing charging details below SoC bar.
 * AC: Voltage, Current, Phases + Energy added + Time remaining
 * DC: Energy added + Time remaining only
 */
@Composable
private fun ChargingDetailsRow(
    status: CarStatus,
    palette: CarColorPalette
) {
    val isDcCharging = status.isDcCharging

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: AC details (Voltage, Current, Phases) or empty for DC
        if (!isDcCharging) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voltage
                Text(
                    text = "${status.chargingDetails?.chargerVoltage ?: "--"} V",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                // Current
                Text(
                    text = "${status.chargerActualCurrent ?: "--"} A",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                // Phases badge
                val phases = status.chargerPhases
                if (phases != null && phases > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = palette.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${phases}φ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = palette.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Empty spacer for DC
            Spacer(modifier = Modifier.weight(1f))
        }

        // Right: Energy added and time remaining
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Energy added
            Text(
                text = "+${status.chargeEnergyAdded?.let { "%.1f".format(it) } ?: "0"} kWh",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )

            // Time remaining
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = palette.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = status.timeToFullCharge?.let { formatHoursMinutes(it) } ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocationCard(status: CarStatus, units: Units?, resolvedAddress: String? = null, palette: CarColorPalette) {
    val context = LocalContext.current
    val latitude = status.latitude
    val longitude = status.longitude
    val geofence = status.geofence
    val elevation = status.elevation

    // Location text: geofence name if available, then resolved address, then coordinates
    // Use takeIf to handle empty strings (API may return "" instead of null)
    val locationText = geofence?.takeIf { it.isNotBlank() }
        ?: resolvedAddress?.takeIf { it.isNotBlank() }
        ?: run {
            if (latitude != null && longitude != null) {
                "%.5f, %.5f".format(latitude, longitude)
            } else {
                "Unknown"
            }
        }

    // Format elevation with unit conversion
    val elevationText = elevation?.let {
        val isImperial = units?.unitOfLength == "mi"
        if (isImperial) {
            val feet = (it * 3.28084).toInt()
            "$feet ft"
        } else {
            "$it m"
        }
    }

    fun openInMaps() {
        if (latitude != null && longitude != null) {
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val intent = Intent(Intent.ACTION_VIEW, geoUri)
            context.startActivity(intent)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInMaps() },
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface
                )

                // Elevation row
                if (elevationText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Terrain,
                            contentDescription = null,
                            tint = palette.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Elevation",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = elevationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Small map showing car location
            if (latitude != null && longitude != null) {
                Spacer(modifier = Modifier.width(12.dp))
                SmallLocationMap(
                    latitude = latitude,
                    longitude = longitude,
                    onClick = { openInMaps() },
                    modifier = Modifier
                        .width(140.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun SmallLocationMap(
    latitude: Double,
    longitude: Double,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = "MateDroid/1.0"
        onDispose { }
    }

    Box(
        modifier = modifier.clickable { onClick() }
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)

                    // Disable all interactions for this small preview map
                    setBuiltInZoomControls(false)
                    isClickable = false
                    isFocusable = false

                    val carLocation = GeoPoint(latitude, longitude)
                    controller.setZoom(15.0)
                    controller.setCenter(carLocation)

                // Add a marker for the car
                val marker = Marker(this).apply {
                    position = carLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ctx.getDrawable(android.R.drawable.ic_menu_mylocation)
                }
                overlays.add(marker)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    }
}
@Composable
private fun VehicleInfoCard(
    status: CarStatus,
    units: Units?,
    palette: CarColorPalette,
    totalCharges: Int?,
    totalDrives: Int?,
    onNavigateToCharges: () -> Unit,
    onNavigateToDrives: () -> Unit,
    onNavigateToMileage: () -> Unit,
    onNavigateToUpdates: () -> Unit
) {
    val tpms = status.tpmsDetails

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vehicle Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation buttons - 2x2 grid of rectangular buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton(
                    title = "Charges",
                    value = totalCharges?.let { "%,d".format(it) } ?: "--",
                    icon = Icons.Filled.ElectricBolt,
                    palette = palette,
                    onClick = onNavigateToCharges,
                    modifier = Modifier.weight(1f)
                )
                NavButton(
                    title = "Drives",
                    value = totalDrives?.let { "%,d".format(it) } ?: "--",
                    icon = CustomIcons.SteeringWheel,
                    palette = palette,
                    onClick = onNavigateToDrives,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton(
                    title = "Mileage",
                    value = status.odometer?.let {
                        val value = UnitFormatter.formatDistanceValue(it, units, 0)
                        "%,.0f %s".format(value, UnitFormatter.getDistanceUnit(units))
                    } ?: "--",
                    icon = CustomIcons.Road,
                    palette = palette,
                    onClick = onNavigateToMileage,
                    modifier = Modifier.weight(1f)
                )
                NavButton(
                    title = "Software",
                    value = status.version ?: "--",
                    icon = Icons.Filled.Settings,
                    palette = palette,
                    onClick = onNavigateToUpdates,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tire pressure section - show if data available
            if (tpms != null && (tpms.pressureFl != null || tpms.pressureFr != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                TirePressureDisplay(tpms = tpms, units = units, palette = palette)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavButton(
    title: String,
    value: String,
    icon: ImageVector,
    palette: CarColorPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = palette.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TirePressureDisplay(
    tpms: TpmsDetails,
    units: Units?,
    palette: CarColorPalette
) {
    val okColor = StatusSuccess
    val warningColor = StatusWarning
    val carOutlineColor = palette.onSurfaceVariant.copy(alpha = 0.4f)

    // Use API warning flags only - no hardcoded thresholds
    val flColor = if (tpms.warningFl == true) warningColor else okColor
    val frColor = if (tpms.warningFr == true) warningColor else okColor
    val rlColor = if (tpms.warningRl == true) warningColor else okColor
    val rrColor = if (tpms.warningRr == true) warningColor else okColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left pressure values (FL, RL)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TirePressureItem(
                label = "FL",
                pressure = tpms.pressureFl,
                color = flColor,
                units = units,
                alignEnd = true
            )
            TirePressureItem(
                label = "RL",
                pressure = tpms.pressureRl,
                color = rlColor,
                units = units,
                alignEnd = true
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Tesla car outline (minimalist)
        Canvas(
            modifier = Modifier
                .width(32.dp)
                .height(48.dp)
        ) {
            val w = size.width
            val h = size.height

            // Draw Tesla-like car outline using path
            val path = androidx.compose.ui.graphics.Path().apply {
                // Start at top-left of hood
                moveTo(w * 0.25f, h * 0.22f)
                // Hood curve to top center
                quadraticTo(w * 0.25f, h * 0.08f, w * 0.5f, h * 0.08f)
                // Hood curve to top-right
                quadraticTo(w * 0.75f, h * 0.08f, w * 0.75f, h * 0.22f)
                // Right side down to rear
                lineTo(w * 0.82f, h * 0.35f)
                quadraticTo(w * 0.85f, h * 0.45f, w * 0.85f, h * 0.55f)
                lineTo(w * 0.85f, h * 0.78f)
                // Rear curve
                quadraticTo(w * 0.85f, h * 0.92f, w * 0.5f, h * 0.94f)
                quadraticTo(w * 0.15f, h * 0.92f, w * 0.15f, h * 0.78f)
                // Left side up to hood
                lineTo(w * 0.15f, h * 0.55f)
                quadraticTo(w * 0.15f, h * 0.45f, w * 0.18f, h * 0.35f)
                close()
            }

            drawPath(
                path = path,
                color = carOutlineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // Windshield
            val windshieldPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, h * 0.24f)
                quadraticTo(w * 0.5f, h * 0.20f, w * 0.70f, h * 0.24f)
                lineTo(w * 0.65f, h * 0.34f)
                quadraticTo(w * 0.5f, h * 0.32f, w * 0.35f, h * 0.34f)
                close()
            }
            drawPath(
                path = windshieldPath,
                color = carOutlineColor.copy(alpha = 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )

            // Rear window
            val rearPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, h * 0.74f)
                quadraticTo(w * 0.5f, h * 0.72f, w * 0.70f, h * 0.74f)
                lineTo(w * 0.65f, h * 0.82f)
                quadraticTo(w * 0.5f, h * 0.84f, w * 0.35f, h * 0.82f)
                close()
            }
            drawPath(
                path = rearPath,
                color = carOutlineColor.copy(alpha = 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right pressure values (FR, RR)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TirePressureItem(
                label = "FR",
                pressure = tpms.pressureFr,
                color = frColor,
                units = units,
                alignEnd = false
            )
            TirePressureItem(
                label = "RR",
                pressure = tpms.pressureRr,
                color = rrColor,
                units = units,
                alignEnd = false
            )
        }
    }
}

@Composable
private fun TirePressureItem(
    label: String,
    pressure: Double?,
    color: androidx.compose.ui.graphics.Color,
    units: Units?,
    alignEnd: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        if (alignEnd) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, shape = RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = pressure?.let { UnitFormatter.formatPressure(it, units, 1) } ?: "--",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        } else {
            Text(
                text = pressure?.let { UnitFormatter.formatPressure(it, units, 1) } ?: "--",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, shape = RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val innerModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    } else {
        Modifier.padding(8.dp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(innerModifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatHoursMinutes(hours: Double): String {
    val totalMinutes = (hours * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    MateDroidTheme {
        DashboardContent(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                odometer = 45678.0,
                carStatus = CarStatusDetails(locked = true),
                carGeodata = CarGeodata(geofence = "Home"),
                carVersions = CarVersions(version = "2024.8.7"),
                climateDetails = ClimateDetails(
                    isClimateOn = false,
                    insideTemp = 21.5,
                    outsideTemp = 15.2
                ),
                batteryDetails = BatteryDetails(
                    batteryLevel = 72,
                    ratedBatteryRange = 312.5
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 11,
                    chargerPhases = 3,  // AC charging
                    chargerVoltage = 230,
                    chargerActualCurrent = 16,
                    chargeCurrentRequestMax = 32,
                    chargeEnergyAdded = 15.3,
                    timeToFullCharge = 1.5,
                    chargeLimitSoc = 80
                )
            ),
            carTrimBadging = "74D"
        )
    }
}

@Preview(showBackground = true, name = "AC Charging - 11kW")
@Composable
private fun BatteryCardAcChargingPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 45,
                    ratedBatteryRange = 180.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 11,
                    chargerPhases = 3,  // AC = phases 1-3
                    chargerVoltage = 230,
                    chargerActualCurrent = 16,
                    chargeCurrentRequestMax = 16,  // 16/16 = 100% gauge fill
                    chargeEnergyAdded = 8.5,
                    timeToFullCharge = 2.5,
                    chargeLimitSoc = 80
                )
            ),
            units = null,
            carTrimBadging = "74D"
        )
    }
}

@Preview(showBackground = true, name = "DC Charging - 120kW")
@Composable
private fun BatteryCardDcChargingPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 60,
                    ratedBatteryRange = 240.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 120,  // 120/250 = 48% gauge fill
                    chargerPhases = 0,  // DC = phases 0 or null
                    chargeEnergyAdded = 35.5,
                    timeToFullCharge = 0.3,
                    chargeLimitSoc = 80
                )
            ),
            units = null,
            carTrimBadging = "74D"  // NMC battery, max 250kW
        )
    }
}

@Preview(showBackground = true, name = "DC Charging - LFP Battery")
@Composable
private fun BatteryCardDcChargingLfpPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 20,
                    ratedBatteryRange = 80.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 120,
                    chargerPhases = 0,  // DC
                    chargeEnergyAdded = 18.0,
                    timeToFullCharge = 0.4,
                    chargeLimitSoc = 100  // LFP can charge to 100%
                )
            ),
            units = null,
            carTrimBadging = "50"  // LFP battery, max 170kW
        )
    }
}
