package com.matedroid.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.matedroid.R
import com.matedroid.data.local.CarImageOverride
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.domain.model.WheelOption

/**
 * A dialog for manually selecting car appearance (variant and wheel style).
 *
 * Tapping a car image selects it, tapping again confirms the selection.
 *
 * @param model The car model from TeslamateAPI (e.g., "3", "Y")
 * @param exteriorColor The exterior color from TeslamateAPI (e.g., "MidnightSilver")
 * @param wheelType The wheel type from TeslamateAPI (e.g., "Photon18")
 * @param trimBadging The trim badging from TeslamateAPI (e.g., "74D", "P74D")
 * @param currentOverride The current manual override, if any
 * @param onDismiss Called when the dialog is dismissed
 * @param onConfirm Called when a selection is confirmed, with the new override
 * @param onReset Called when the user wants to reset to automatic detection
 */
@Composable
fun CarImagePickerDialog(
    model: String?,
    exteriorColor: String?,
    wheelType: String?,
    trimBadging: String?,
    currentOverride: CarImageOverride?,
    onDismiss: () -> Unit,
    onConfirm: (CarImageOverride) -> Unit,
    onReset: () -> Unit
) {
    val variants = remember(model) { CarImageResolver.getVariantsForModel(model) }
    val colorCode = remember(exteriorColor) { CarImageResolver.mapColor(exteriorColor) }

    // Compute the detected default from API data
    val detectedDefault = remember(model, exteriorColor, wheelType, trimBadging) {
        CarImageResolver.getDetectedDefault(model, exteriorColor, wheelType, trimBadging)
    }

    // If no variants available (Model S/X), show info message and dismiss
    if (variants.isEmpty()) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.car_picker_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No variants available for this model.")
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
        return
    }

    // Initialize selected variant from override or detected default
    var selectedVariant by remember(currentOverride, variants) {
        mutableStateOf(currentOverride?.variant ?: detectedDefault.variant)
    }

    // Get wheels for selected variant
    val wheels = remember(selectedVariant, colorCode) {
        CarImageResolver.getWheelsForVariant(selectedVariant, colorCode)
    }

    // Initialize selected wheel from override, or detected default if on default variant
    // Key on 'wheels' so it resets properly when variant changes (same as original working code)
    var selectedWheel by remember(currentOverride, wheels) {
        val initialWheel = currentOverride?.wheelCode
            ?: if (selectedVariant == detectedDefault.variant) detectedDefault.wheelCode else null
        mutableStateOf<String?>(initialWheel)
    }

    // Update selected wheel when variant changes - reset to default or null
    LaunchedEffect(selectedVariant) {
        val newWheels = CarImageResolver.getWheelsForVariant(selectedVariant, colorCode)
        if (newWheels.isNotEmpty() && (selectedWheel == null || newWheels.none { it.code == selectedWheel })) {
            selectedWheel = if (selectedVariant == detectedDefault.variant) {
                detectedDefault.wheelCode
            } else {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.car_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Variant selector
                Text(
                    text = stringResource(R.string.car_picker_model_variant),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                VariantChips(
                    variants = variants,
                    selectedVariant = selectedVariant,
                    onVariantSelected = { selectedVariant = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Wheel selector with reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.car_picker_wheel_style),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reset to default button - clears override and uses API-detected default
                    IconButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.car_picker_reset_default),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                WheelCarousel(
                    wheels = wheels,
                    selectedWheel = selectedWheel,
                    onWheelTapped = { wheelCode ->
                        if (selectedWheel == wheelCode) {
                            // Tap again on selected wheel - confirm selection
                            onConfirm(CarImageOverride(selectedVariant, wheelCode))
                            onDismiss()
                        } else {
                            // First tap - select this wheel
                            selectedWheel = wheelCode
                        }
                    },
                    variant = selectedVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Hint text - changes based on whether something is selected
                Text(
                    text = if (selectedWheel != null) {
                        stringResource(R.string.car_picker_tap_to_confirm)
                    } else {
                        stringResource(R.string.car_picker_tap_to_select)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedWheel != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selectedWheel != null) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

            }
        }
    }
}

@Composable
private fun VariantChips(
    variants: List<com.matedroid.domain.model.CarVariant>,
    selectedVariant: String,
    onVariantSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(variants) { variant ->
            FilterChip(
                selected = variant.id == selectedVariant,
                onClick = { onVariantSelected(variant.id) },
                label = {
                    Text(
                        text = getVariantDisplayName(variant.id),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun getVariantDisplayName(variantId: String): String {
    return when (variantId) {
        "my" -> stringResource(R.string.car_variant_my_legacy)
        "myjs" -> stringResource(R.string.car_variant_my_standard)
        "myj" -> stringResource(R.string.car_variant_my_premium)
        "myjp" -> stringResource(R.string.car_variant_my_performance)
        "m3" -> stringResource(R.string.car_variant_m3_legacy)
        "m3h" -> stringResource(R.string.car_variant_m3_highland)
        "m3hp" -> stringResource(R.string.car_variant_m3_highland_perf)
        else -> variantId
    }
}

@Composable
private fun WheelCarousel(
    wheels: List<WheelOption>,
    selectedWheel: String?,
    onWheelTapped: (String) -> Unit,
    variant: String
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Scroll to selected wheel when it changes
    LaunchedEffect(selectedWheel) {
        if (selectedWheel != null) {
            val index = wheels.indexOfFirst { it.code == selectedWheel }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val scaleFactor = remember(variant) {
        CarImageResolver.getScaleFactorForVariant(variant)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(wheels) { wheel ->
            val isSelected = wheel.code == selectedWheel
            WheelOptionItem(
                wheel = wheel,
                isSelected = isSelected,
                scaleFactor = scaleFactor,
                onClick = { onWheelTapped(wheel.code) }
            )
        }
    }
}

@Composable
private fun WheelOptionItem(
    wheel: WheelOption,
    isSelected: Boolean,
    scaleFactor: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val bitmap = remember(wheel.assetPath) {
        try {
            context.assets.open(wheel.assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val borderWidth = if (isSelected) 3.dp else 1.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Car image - rectangular, wider than tall to match car image aspect ratio
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = wheel.displayName,
                    modifier = Modifier
                        .width(200.dp)
                        .height(120.dp)
                        .graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                // Placeholder if image not found
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(96.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Wheel name
        Text(
            text = wheel.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
