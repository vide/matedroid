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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.matedroid.R
import com.matedroid.data.local.CarImageOverride
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.domain.model.WheelOption

/**
 * A dialog for manually selecting car appearance (variant and wheel style).
 *
 * @param model The car model from TeslamateAPI (e.g., "3", "Y")
 * @param colorCode The exterior color code (e.g., "PPSW", "PN01")
 * @param currentOverride The current manual override, if any
 * @param onDismiss Called when the dialog is dismissed
 * @param onConfirm Called when a selection is confirmed, with the new override
 * @param onReset Called when the user wants to reset to automatic detection
 */
@Composable
fun CarImagePickerDialog(
    model: String?,
    colorCode: String?,
    currentOverride: CarImageOverride?,
    onDismiss: () -> Unit,
    onConfirm: (CarImageOverride) -> Unit,
    onReset: () -> Unit
) {
    val variants = remember(model) { CarImageResolver.getVariantsForModel(model) }

    // If no variants available (Model S/X), show info message and dismiss
    if (variants.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.car_picker_title)) },
            text = {
                Text("No variants available for this model.")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
        return
    }

    // Initialize selected variant from override or first available
    var selectedVariant by remember(currentOverride, variants) {
        mutableStateOf(currentOverride?.variant ?: variants.first().id)
    }

    // Get wheels for selected variant
    val wheels = remember(selectedVariant, colorCode) {
        CarImageResolver.getWheelsForVariant(selectedVariant, colorCode)
    }

    // Initialize selected wheel from override or first available
    var selectedWheel by remember(currentOverride, wheels) {
        mutableStateOf(currentOverride?.wheelCode ?: wheels.firstOrNull()?.code ?: "")
    }

    // Update selected wheel when variant changes
    LaunchedEffect(selectedVariant) {
        val newWheels = CarImageResolver.getWheelsForVariant(selectedVariant, colorCode)
        if (newWheels.isNotEmpty() && newWheels.none { it.code == selectedWheel }) {
            selectedWheel = newWheels.first().code
        }
    }

    // Check if selection differs from current override
    val selectionChanged = currentOverride == null ||
            currentOverride.variant != selectedVariant ||
            currentOverride.wheelCode != selectedWheel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.car_picker_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
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

                Spacer(modifier = Modifier.height(16.dp))

                // Wheel selector
                Text(
                    text = stringResource(R.string.car_picker_wheel_style),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                WheelCarousel(
                    wheels = wheels,
                    selectedWheel = selectedWheel,
                    onWheelSelected = { selectedWheel = it },
                    variant = selectedVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.car_picker_tap_to_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                // Auto/Reset button
                if (currentOverride != null) {
                    TextButton(onClick = {
                        onReset()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.car_picker_auto))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Confirm button
                Button(
                    onClick = {
                        onConfirm(CarImageOverride(selectedVariant, selectedWheel))
                        onDismiss()
                    },
                    enabled = selectionChanged
                ) {
                    Text(stringResource(R.string.car_picker_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
    selectedWheel: String,
    onWheelSelected: (String) -> Unit,
    variant: String
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Scroll to selected wheel when it changes
    LaunchedEffect(selectedWheel) {
        val index = wheels.indexOfFirst { it.code == selectedWheel }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    val scaleFactor = remember(variant) {
        CarImageResolver.getScaleFactorForVariant(variant)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(wheels) { wheel ->
            val isSelected = wheel.code == selectedWheel
            WheelOptionItem(
                wheel = wheel,
                isSelected = isSelected,
                scaleFactor = scaleFactor,
                onClick = { onWheelSelected(wheel.code) }
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
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Car image
        Box(
            modifier = Modifier
                .size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = wheel.displayName,
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = scaleFactor * 0.9f
                            scaleY = scaleFactor * 0.9f
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                // Placeholder if image not found
                Box(
                    modifier = Modifier
                        .size(80.dp)
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

        Spacer(modifier = Modifier.height(4.dp))

        // Wheel name
        Text(
            text = wheel.displayName,
            style = MaterialTheme.typography.labelSmall,
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
