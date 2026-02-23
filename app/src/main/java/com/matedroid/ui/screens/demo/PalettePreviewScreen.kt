package com.matedroid.ui.screens.demo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePreviewScreen(
    darkTheme: Boolean? = null,  // ← Optional parameter for dark mode
    onNavigateBack: () -> Unit = {}
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()  // ← Use system theme if not provided

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Palette Preview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isDark) "Dark Mode" else "Light Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Section: Charging cards
            Text(
                text = "Charging State",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // White car - Charging
            PreviewBatteryCard(
                carColor = "White",
                exteriorColor = "White",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("White", isDark),
                label = "White"
            )

            // Midnight Silver - Charging
            PreviewBatteryCard(
                carColor = "Midnight Silver",
                exteriorColor = "MidnightSilver",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("MidnightSilver", isDark),
                label = "Midnight Silver"
            )

            // Red Multi-Coat - Charging
            PreviewBatteryCard(
                carColor = "Red Multi-Coat",
                exteriorColor = "RedMulticoat",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = true,
                isDcCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("RedMulticoat", isDark),
                label = "Red Multi-Coat"
            )

            // Black car - Charging
            PreviewBatteryCard(
                carColor = "Black",
                exteriorColor = "SolidBlack",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("SolidBlack", isDark),
                label = "Black"
            )

            // Section: Not Charging cards
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Not Charging State",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // White car - Not charging
            PreviewBatteryCard(
                carColor = "White (Not Charging)",
                exteriorColor = "White",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = false
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("White", isDark),
                label = "White"
            )

            // Black car - Not charging
            PreviewBatteryCard(
                carColor = "Black (Not Charging)",
                exteriorColor = "SolidBlack",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = false
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("SolidBlack", isDark),
                label = "Black"
            )

            // Midnight Silver - Not charging
            PreviewBatteryCard(
                carColor = "Midnight Silver (Not Charging)",
                exteriorColor = "MidnightSilver",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = false
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("MidnightSilver", isDark),
                label = "Midnight Silver"
            )

            // Red - Not charging
            PreviewBatteryCard(
                carColor = "Red Multi-Coat (Not Charging)",
                exteriorColor = "RedMulticoat",
                model = "3",
                wheelType = "Pinwheel18",
                isDarkTheme = isDark,
                isCharging = false
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("RedMulticoat", isDark),
                label = "Red Multi-Coat"
            )

            // Section: Other models (Charging)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Other Models (Charging)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Model Y Legacy - Pearl White
            PreviewBatteryCard(
                carColor = "Pearl White (Model Y Legacy)",
                exteriorColor = "White",
                model = "Y",
                wheelType = "Gemini19",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("White", isDark),
                label = "Pearl White (Model Y)"
            )

            // Deep Blue (Model Y Legacy)
            PreviewBatteryCard(
                carColor = "Deep Blue (Model Y Legacy)",
                exteriorColor = "DeepBlue",
                model = "Y",
                wheelType = "Gemini19",
                isDarkTheme = isDark,
                isCharging = true,
                isDcCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("DeepBlue", isDark),
                label = "Deep Blue (Model Y)"
            )

            // Model Y Juniper
            PreviewBatteryCard(
                carColor = "Black Diamond (Model Y Juniper)",
                exteriorColor = "BlackDiamond",
                model = "Y",
                wheelType = "Photon18",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("BlackDiamond", isDark),
                label = "Black Diamond (Model Y)"
            )

            // Stealth Grey (Highland)
            PreviewBatteryCard(
                carColor = "Stealth Grey (Model 3 Highland)",
                exteriorColor = "StealthGrey",
                model = "3",
                wheelType = "Photon18",
                trimBadging = "MT336",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("StealthGrey", isDark),
                label = "Stealth Grey (Highland)"
            )

            // Model S
            PreviewBatteryCard(
                carColor = "Pearl White (Model S)",
                exteriorColor = "White",
                model = "S",
                wheelType = "Tempest19",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("White", isDark),
                label = "Pearl White (Model S)"
            )

            // Model X
            PreviewBatteryCard(
                carColor = "Deep Blue (Model X)",
                exteriorColor = "DeepBlue",
                model = "X",
                wheelType = "Cyberstream20",
                isDarkTheme = isDark,
                isCharging = true
            )

            ChargesPreview(
                palette = CarColorPalettes.forExteriorColor("DeepBlue", isDark),
                label = "Deep Blue (Model X)"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewBatteryCard(
    carColor: String,
    exteriorColor: String,
    model: String,
    wheelType: String,
    trimBadging: String? = null,
    isDarkTheme: Boolean,
    isCharging: Boolean = true,
    isDcCharging: Boolean = false
) {
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

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
            // Label
            Text(
                text = carColor,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )

            // Status indicators row
            StatusIndicatorsRowPreview(palette = palette, isCharging = isCharging)

            // Car image
            PreviewCarImage(
                model = model,
                exteriorColor = exteriorColor,
                wheelType = wheelType,
                trimBadging = trimBadging,
                modifier = Modifier.fillMaxWidth()
            )

            // Battery info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.Battery5Bar,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = palette.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCharging) "72%" else "65%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    if (isCharging) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ElectricBolt,
                            contentDescription = "Charging",
                            modifier = Modifier.size(20.dp),
                            tint = StatusSuccess
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isCharging) "312 km" else "280 km",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                    Text(
                        text = "Limit: 80%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            ChargingProgressBarPreview(
                currentLevel = if (isCharging) 72 else 65,
                targetLevel = 80,
                isCharging = isCharging,
                isDcCharging = isDcCharging,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Info row
            if (isCharging) {
                // Charging info row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "11 kW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = StatusSuccess
                    )
                    Text(
                        text = "+15.3 kWh",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = palette.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "1h 30m",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Not charging info row - show plugged status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Not plugged in",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                    Text(
                        text = "Last charged 2h ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicatorsRowPreview(
    palette: CarColorPalette,
    isCharging: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = if (isCharging) StatusSuccess else StatusWarning
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isCharging) "Charging" else "Parked",
                style = MaterialTheme.typography.labelMedium,
                color = if (isCharging) StatusSuccess else StatusWarning
            )

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = StatusSuccess
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Locked",
                style = MaterialTheme.typography.labelMedium,
                color = StatusSuccess
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "21°C",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "15°C",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent
            )
        }
    }
}

@Composable
private fun PreviewCarImage(
    model: String,
    exteriorColor: String,
    wheelType: String,
    trimBadging: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assetPath = remember(model, exteriorColor, wheelType, trimBadging) {
        CarImageResolver.getAssetPath(
            model = model,
            exteriorColor = exteriorColor,
            wheelType = wheelType,
            trimBadging = trimBadging
        )
    }

    val scaleFactor = remember(model, exteriorColor, wheelType, trimBadging) {
        CarImageResolver.getScaleFactor(
            model = model,
            exteriorColor = exteriorColor,
            wheelType = wheelType,
            trimBadging = trimBadging
        )
    }

    val bitmap = remember(assetPath) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            try {
                val fallbackPath = CarImageResolver.getDefaultAssetPath(model)
                context.assets.open(fallbackPath).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e2: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Box(
            modifier = modifier.height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Car image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                    },
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Box(
            modifier = modifier.height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No image", color = Color.Gray)
        }
    }
}

@Composable
private fun ChargingProgressBarPreview(
    currentLevel: Int,
    targetLevel: Int,
    isCharging: Boolean,
    isDcCharging: Boolean = false,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val currentFraction = currentLevel / 100f
    val targetFraction = targetLevel / 100f
    // Use AC/DC color when charging, StatusSuccess as fallback
    val chargeColor = if (isCharging) {
        if (isDcCharging) palette.dcColor else palette.acColor
    } else {
        StatusSuccess  // Fallback (not used in practice)
    }
    val dimmedChargeColor = chargeColor.copy(alpha = 0.3f)

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
            // Charging: show AC/DC color with target area
            // Dimmed color for target area (from current to target)
            if (targetFraction > currentFraction) {
                drawRect(
                    color = dimmedChargeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width * (targetFraction - currentFraction),
                        height
                    )
                )
            }
            // Solid AC/DC color for current charge level
            drawRect(
                color = chargeColor,
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
 * Preview component showing Charges filters and chart for demo purposes
 */
@Composable
private fun ChargesPreview(
    palette: CarColorPalette,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Label (e.g., "White - Light Mode")
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Charge Type Filters (Todos, AC, DC)
            ChargeTypeFiltersPreview(palette = palette)

            Spacer(modifier = Modifier.height(16.dp))

            // Chart
            ChargesChartPreview(palette = palette)
        }
    }
}

/**
 * Preview of charge type filter chips (Todos, AC, DC)
 */
@Composable
private fun ChargeTypeFiltersPreview(
    palette: CarColorPalette
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Todos - Selected
        FilterChip(
            selected = true,
            onClick = { },
            label = {
                Text(
                    text = "Todos",
                    color = Color.White
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.onSurfaceVariant,
                containerColor = Color.Transparent
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = true,
                borderWidth = 0.dp
            )
        )

        // AC
        FilterChip(
            selected = false,
            onClick = { },
            label = {
                Text(
                    text = "AC",
                    color = palette.acColor
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.acColor,
                containerColor = Color.Transparent
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = palette.acColor.copy(alpha = 0.6f),
                borderWidth = 1.dp
            )
        )

        // DC
        FilterChip(
            selected = false,
            onClick = { },
            label = {
                Text(
                    text = "DC",
                    color = palette.dcColor
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.dcColor,
                containerColor = Color.Transparent
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = palette.dcColor.copy(alpha = 0.6f),
                borderWidth = 1.dp
            )
        )
    }
}

/**
 * Preview of energy chart with AC/DC segments
 */
@Composable
private fun ChargesChartPreview(
    palette: CarColorPalette
) {
    Column {
        // Chart title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Energía por semana",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sample data - Weekly energy with AC/DC breakdown
        val sampleData = listOf(
            com.matedroid.ui.components.BarChartData(
                label = "W47",
                value = 165.0,
                displayValue = "165.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(165.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W48",
                value = 178.0,
                displayValue = "178.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(178.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W49",
                value = 248.0,
                displayValue = "248.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(248.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W50",
                value = 142.0,
                displayValue = "142.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(142.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W51",
                value = 98.0,
                displayValue = "98.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(98.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W52",
                value = 215.0,
                displayValue = "215.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(25.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(190.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W1",
                value = 235.0,
                displayValue = "235.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(45.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(190.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W2",
                value = 38.0,
                displayValue = "38.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(5.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(33.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W3",
                value = 52.0,
                displayValue = "52.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(52.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W4",
                value = 168.0,
                displayValue = "168.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(168.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W5",
                value = 125.0,
                displayValue = "125.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(125.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W6",
                value = 188.0,
                displayValue = "188.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(20.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(168.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W7",
                value = 145.0,
                displayValue = "145.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(145.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            ),
            com.matedroid.ui.components.BarChartData(
                label = "W8",
                value = 72.0,
                displayValue = "72.0 kWh",
                segments = listOf(
                    com.matedroid.ui.components.BarSegment(72.0, palette.acColor, "AC"),
                    com.matedroid.ui.components.BarSegment(0.0, palette.dcColor, "DC")
                )
            )
        )

        com.matedroid.ui.components.InteractiveBarChart(
            data = sampleData,
            modifier = Modifier.fillMaxWidth(),
            barColor = palette.accent,
            labelColor = palette.onSurfaceVariant,
            showEveryNthLabel = 3,
            valueFormatter = { v -> "%.0f kWh".format(v) }
        )
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun PalettePreviewScreenLightPreview() {
    com.matedroid.ui.theme.MateDroidTheme(darkTheme = false) {
        PalettePreviewScreen(darkTheme = false)
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
private fun PalettePreviewScreenDarkPreview() {
    com.matedroid.ui.theme.MateDroidTheme(darkTheme = true) {
        PalettePreviewScreen(darkTheme = true)
    }
}
