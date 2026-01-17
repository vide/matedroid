package com.matedroid.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * A line chart component with fullscreen capability.
 *
 * Displays the OptimizedLineChart with a small fullscreen icon in the lower-right corner.
 * When tapped, the chart expands to fullscreen in landscape mode with a back button overlay.
 */
@Composable
fun FullscreenLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    unit: String = "",
    showZeroLine: Boolean = false,
    fixedMinMax: Pair<Float, Float>? = null,
    timeLabels: List<String> = emptyList(),
    convertValue: (Float) -> Float = { it }
) {
    if (data.size < 2) return

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    Box(modifier = modifier) {
        // Regular chart
        OptimizedLineChart(
            data = data,
            color = color,
            unit = unit,
            showZeroLine = showZeroLine,
            fixedMinMax = fixedMinMax,
            timeLabels = timeLabels,
            convertValue = convertValue,
            modifier = Modifier.fillMaxWidth()
        )

        // Fullscreen icon button in lower-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isFullscreen = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Fullscreen overlay
    if (isFullscreen) {
        FullscreenChartOverlay(
            data = data,
            color = color,
            unit = unit,
            showZeroLine = showZeroLine,
            fixedMinMax = fixedMinMax,
            timeLabels = timeLabels,
            convertValue = convertValue,
            activity = activity,
            onDismiss = { isFullscreen = false }
        )
    }
}

@Composable
private fun FullscreenChartOverlay(
    data: List<Float>,
    color: Color,
    unit: String,
    showZeroLine: Boolean,
    fixedMinMax: Pair<Float, Float>?,
    timeLabels: List<String>,
    convertValue: (Float) -> Float,
    activity: Activity?,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    // Lock orientation to landscape and hide system bars
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        // Hide system bars on the activity window
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // Restore portrait mode and show system bars
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle back button press
    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = SecureFlagPolicy.Inherit
        )
    ) {
        // Get the dialog's window and make it truly fullscreen
        val dialogWindowProvider = LocalView.current.parent as? android.view.ViewGroup
        DisposableEffect(dialogWindowProvider) {
            val dialogWindow = dialogWindowProvider?.context as? android.app.Dialog
            dialogWindow?.window?.let { window ->
                // Make dialog fullscreen
                window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                window.setBackgroundDrawableResource(android.R.color.transparent)

                // Hide system bars in dialog window too
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose { }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Calculate chart height based on available space minus padding and time labels
            val verticalPadding = 48.dp // top + bottom padding
            val timeLabelHeight = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
            val availableChartHeight = maxHeight - verticalPadding - timeLabelHeight

            // Fullscreen chart with padding
            OptimizedLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                chartHeight = availableChartHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 56.dp,  // Space for back button
                        end = 24.dp,
                        top = 24.dp,
                        bottom = 24.dp
                    )
            )

            // Back button in top-left corner
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit fullscreen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
