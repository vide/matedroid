package com.matedroid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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

    FullscreenChartFrame(
        modifier = modifier,
        hasTimeLabels = timeLabels.isNotEmpty(),
        inline = {
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
        },
        fullscreen = { chartHeight ->
            DualAxisLineChart(
                dataLeft = dataLeft,
                dataRight = dataRight,
                colorLeft = colorLeft,
                colorRight = colorRight,
                unitLeft = unitLeft,
                unitRight = unitRight,
                timeLabels = timeLabels,
                chartHeight = chartHeight,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
