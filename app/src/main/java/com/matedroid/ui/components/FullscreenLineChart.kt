package com.matedroid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
    convertValue: (Float) -> Float = { it },
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null,
    annotationRanges: List<AnnotationRange> = emptyList()
) {
    if (data.size < 2) return

    FullscreenChartFrame(
        modifier = modifier,
        hasTimeLabels = timeLabels.isNotEmpty(),
        inline = {
            OptimizedLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                externalSelectedFraction = externalSelectedFraction,
                onXSelected = onXSelected,
                fractionToTimeLabel = fractionToTimeLabel,
                annotationRanges = annotationRanges,
                modifier = Modifier.fillMaxWidth()
            )
        },
        fullscreen = { chartHeight ->
            OptimizedLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                annotationRanges = annotationRanges,
                chartHeight = chartHeight,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
