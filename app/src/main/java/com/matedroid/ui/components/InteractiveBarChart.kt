package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BarChartData(
    val label: String,
    val value: Double,
    val displayValue: String = value.toString(),
    val color: Color? = null, // for C bar color
    val segments: List<BarSegment> = emptyList() // Segment list
)

data class BarSegment(
    val value: Double,
    val color: Color,
    val label: String
)

@Composable
fun InteractiveBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showEveryNthLabel: Int = 1,
    valueFormatter: (Double) -> String = { "%.0f".format(it) },
    yAxisFormatter: (Double) -> String = { if (it >= 1000) "%.0fk".format(it / 1000) else "%.0f".format(it) }
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val maxValue = data.maxOfOrNull { it.value } ?: 1.0
    val density = LocalDensity.current

    // Reset selection when data changes to avoid IndexOutOfBoundsException
    var selectedBarIndex by remember(data) { mutableStateOf<Int?>(null) }
    var tooltipPosition by remember(data) { mutableStateOf(Offset.Zero) }
    var containerWidth by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    containerWidth = it.size.width.toFloat()}
                .height(120.dp)
                .pointerInput(data) {
                    val yAxisWidth = 32.dp.toPx()
                    detectTapGestures { offset ->
                        if (offset.x > yAxisWidth) {
                            val chartWidth = size.width - yAxisWidth
                            val barWidth = chartWidth / data.size
                            val barIndex = ((offset.x - yAxisWidth) / barWidth).toInt()
                                .coerceIn(0, data.lastIndex)
                            if (selectedBarIndex == barIndex) {
                                selectedBarIndex = null
                            } else {
                                selectedBarIndex = barIndex
                                tooltipPosition = Offset(
                                    yAxisWidth + barIndex * barWidth + barWidth / 2,
                                    offset.y
                                )
                            }
                        }
                    }
                }
        ) {
            val yAxisWidth = 32.dp.toPx()
            val chartWidth = size.width - yAxisWidth
            val barWidth = chartWidth / data.size
            val maxBarHeight = size.height - 20.dp.toPx()

            // Draw Y-axis labels
            drawYAxisLabel(
                textMeasurer = textMeasurer,
                text = yAxisFormatter(maxValue),
                x = yAxisWidth - 4.dp.toPx(),
                y = 0f,
                color = labelColor,
                alignTop = true
            )

            drawYAxisLabel(
                textMeasurer = textMeasurer,
                text = "0",
                x = yAxisWidth - 4.dp.toPx(),
                y = maxBarHeight,
                color = labelColor,
                alignTop = false
            )

            // Draw bars
            data.forEachIndexed { index, barData ->
                val isSelected = index == selectedBarIndex
                val xPosition = yAxisWidth + index * barWidth + barWidth * 0.15f
                val barWidthRect = barWidth * 0.7f

                if (barData.segments.isNotEmpty()) {
                    // Stacked bars
                    var currentY = maxBarHeight // Start at the top of the chart
                    barData.segments.forEach { segment ->
                        val segmentHeight = if (maxValue > 0) {
                            (segment.value / maxValue * maxBarHeight).toFloat()
                        } else 0f

                        if (segmentHeight > 0) {
                            val baseColor = segment.color
                            val currentSegmentColor = if (isSelected) baseColor.copy(alpha = 0.7f) else baseColor

                            drawRect(
                                color = currentSegmentColor,
                                topLeft = Offset(
                                    x = xPosition,
                                    y = currentY - segmentHeight // Substract the segment height
                                ),
                                size = Size(
                                    width = barWidthRect,
                                    height = segmentHeight
                                )
                            )
                            // Update the "floor" for the next segment (AC or DC)
                            currentY -= segmentHeight
                        }
                    }
                } else {
                    // Non stacked bars
                    val barHeight = if (maxValue > 0) {
                        (barData.value / maxValue * maxBarHeight).toFloat()
                    } else 0f

                    if (barHeight > 0) {
                        val baseBarColor = barData.color ?: barColor
                        val currentBarColor = if (isSelected) baseBarColor.copy(alpha = 0.7f) else baseBarColor

                        drawRect(
                            color = currentBarColor,
                            topLeft = Offset(x = xPosition, y = maxBarHeight - barHeight),
                            size = Size(width = barWidthRect, height = barHeight)
                        )
                    }
                }

                // Draw X-axis label
                if (data.size <= 6 || index % showEveryNthLabel == 0) {
                    drawXAxisLabel(
                        textMeasurer = textMeasurer,
                        text = barData.label,
                        x = yAxisWidth + index * barWidth + barWidth / 2,
                        y = size.height - 2.dp.toPx(),
                        color = labelColor
                    )
                }
            }
        }

        // Tooltip - with bounds check for safety
        var tooltipWidth by remember { mutableStateOf(0f) }
        selectedBarIndex?.takeIf { it in data.indices }?.let { index ->
            val barData = data[index]
            // Use the container width to calculate the offset to not clip the tooltip
            val xOffset = (tooltipPosition.x - tooltipWidth / 2)
                .coerceIn(0f, containerWidth - tooltipWidth)

            Box(
                modifier = Modifier
                    .offset { IntOffset(xOffset.toInt(), 0) }
                    .onGloballyPositioned { coordinates ->
                        // This captures the ACTUAL size of the tooltip
                        tooltipWidth = coordinates.size.width.toFloat()
                    }
                    .background(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(2.dp)) {
                    // Title
                    Text(
                        text = barData.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
                    )
                    // Values
                    if (barData.segments.isNotEmpty()) {
                        // Show breakdown of segments (AC/DC)
                        barData.segments.filter { it.value > 0 }.forEach { segment ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                // Little dot (color)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(segment.color, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Name and value (e.g. AC: 12.5 kWh)
                                Text(
                                    text = "${segment.label}: ${valueFormatter(segment.value)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                        // Optional: Show total at the end
                        Spacer(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .height(0.5.dp)
                                .width(60.dp) // Give the divider a fixed small width instead of fillMaxWidth
                                .background(Color.Gray.copy(alpha = 0.5f))
                        )
                        Text(
                            text = "Total: ${valueFormatter(barData.value)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                        )
                    } else {
                        // If there are no segments, show the single value
                        Text(
                            text = valueFormatter(barData.value),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawYAxisLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    alignTop: Boolean
) {
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontSize = 9.sp,
            textAlign = TextAlign.End
        )
    )
    val yOffset = if (alignTop) 0f else -textLayoutResult.size.height.toFloat()
    drawText(
        textLayoutResult = textLayoutResult,
        color = color,
        topLeft = Offset(
            x = x - textLayoutResult.size.width,
            y = y + yOffset
        )
    )
}

private fun DrawScope.drawXAxisLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color
) {
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    )
    drawText(
        textLayoutResult = textLayoutResult,
        color = color,
        topLeft = Offset(
            x = x - textLayoutResult.size.width / 2,
            y = y - textLayoutResult.size.height
        )
    )
}
