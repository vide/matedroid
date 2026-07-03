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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.matedroid.R

/**
 * Fullscreen chrome shared by the line-chart wrappers: renders [inline] with a small
 * fullscreen button in the lower-right corner, and on tap opens a landscape dialog
 * (orientation lock + hidden system bars + back button) hosting [fullscreen], to which
 * it passes the height available for the chart body.
 *
 * @param hasTimeLabels whether the chart draws an X-axis label row (reserves space for it).
 */
@Composable
fun FullscreenChartFrame(
    modifier: Modifier = Modifier,
    hasTimeLabels: Boolean,
    inline: @Composable () -> Unit,
    fullscreen: @Composable (chartHeight: Dp) -> Unit,
) {
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    Box(modifier = modifier) {
        inline()

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
        FullscreenChartOverlay(
            activity = activity,
            hasTimeLabels = hasTimeLabels,
            onDismiss = { isFullscreen = false },
            chart = fullscreen
        )
    }
}

@Composable
private fun FullscreenChartOverlay(
    activity: Activity?,
    hasTimeLabels: Boolean,
    onDismiss: () -> Unit,
    chart: @Composable (chartHeight: Dp) -> Unit,
) {
    val view = LocalView.current

    // Lock orientation to landscape and hide system bars for the duration of the overlay.
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val verticalPadding = 48.dp // top + bottom padding
            val timeLabelHeight = if (hasTimeLabels) 20.dp else 0.dp
            val availableChartHeight = maxHeight - verticalPadding - timeLabelHeight

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 56.dp,  // Space for back button
                        end = 24.dp,
                        top = 24.dp,
                        bottom = 24.dp
                    )
            ) {
                chart(availableChartHeight)
            }

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
                    contentDescription = stringResource(R.string.exit_fullscreen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
