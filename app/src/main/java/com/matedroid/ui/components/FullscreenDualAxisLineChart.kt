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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.matedroid.R

/**
 * A dual-axis line chart with fullscreen capability.
 * Wraps DualAxisLineChart with a fullscreen icon and landscape overlay.
 */
@Composable
fun FullscreenDualAxisLineChart(
    dataLeft: List<Float>,
    dataRight: List<Float>,
    modifier: Modifier = Modifier,
    colorLeft: Color = MaterialTheme.colorScheme.tertiary,
    colorRight: Color = MaterialTheme.colorScheme.secondary,
    unitLeft: String = "V",
    unitRight: String = "A",
    timeLabels: List<String> = emptyList(),
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    if (dataLeft.size < 2 && dataRight.size < 2) return

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    Box(modifier = modifier) {
        DualAxisLineChart(
            dataLeft = dataLeft,
            dataRight = dataRight,
            colorLeft = colorLeft,
            colorRight = colorRight,
            unitLeft = unitLeft,
            unitRight = unitRight,
            timeLabels = timeLabels,
            externalSelectedFraction = externalSelectedFraction,
            onXSelected = onXSelected,
            fractionToTimeLabel = fractionToTimeLabel,
            modifier = Modifier.fillMaxWidth()
        )

        // Fullscreen icon button
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
                contentDescription = stringResource(R.string.fullscreen),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (isFullscreen) {
        FullscreenDualChartOverlay(
            dataLeft = dataLeft,
            dataRight = dataRight,
            colorLeft = colorLeft,
            colorRight = colorRight,
            unitLeft = unitLeft,
            unitRight = unitRight,
            timeLabels = timeLabels,
            activity = activity,
            onDismiss = { isFullscreen = false }
        )
    }
}

@Composable
private fun FullscreenDualChartOverlay(
    dataLeft: List<Float>,
    dataRight: List<Float>,
    colorLeft: Color,
    colorRight: Color,
    unitLeft: String,
    unitRight: String,
    timeLabels: List<String>,
    activity: Activity?,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { onDismiss() }

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
        val dialogWindowProvider = LocalView.current.parent as? android.view.ViewGroup
        DisposableEffect(dialogWindowProvider) {
            val dialogWindow = dialogWindowProvider?.context as? android.app.Dialog
            dialogWindow?.window?.let { window ->
                window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                window.setBackgroundDrawableResource(android.R.color.transparent)
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
            val verticalPadding = 48.dp
            val timeLabelHeight = if (timeLabels.isNotEmpty()) 20.dp else 0.dp
            val availableChartHeight = maxHeight - verticalPadding - timeLabelHeight

            DualAxisLineChart(
                dataLeft = dataLeft,
                dataRight = dataRight,
                colorLeft = colorLeft,
                colorRight = colorRight,
                unitLeft = unitLeft,
                unitRight = unitRight,
                timeLabels = timeLabels,
                chartHeight = availableChartHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 56.dp,
                        end = 24.dp,
                        top = 24.dp,
                        bottom = 24.dp
                    )
            )

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
                    contentDescription = stringResource(R.string.exit_fullscreen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
