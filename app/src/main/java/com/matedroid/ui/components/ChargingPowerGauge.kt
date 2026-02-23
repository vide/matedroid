package com.matedroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.MateDroidTheme

/**
 * A compact circular gauge showing charging power with an AC/DC badge.
 *
 * The gauge displays:
 * - A circular arc showing progress toward maximum power/current
 * - The current power value in kW in the center
 * - An AC or DC badge to the left of the gauge
 *
 * @param powerKw Current charging power in kW
 * @param isDcCharging True for DC charging, false for AC
 * @param gaugeProgress Progress value from 0.0 to 1.0 for the gauge fill
 * @param palette Color palette for theming
 * @param modifier Modifier for the entire component
 * @param gaugeSize Diameter of the circular gauge
 */
@Composable
fun ChargingPowerGauge(
    powerKw: Int,
    isDcCharging: Boolean,
    gaugeProgress: Float,
    palette: CarColorPalette,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 41.dp
) {
    val gaugeColor = if (isDcCharging) palette.dcColor else palette.acColor
    val trackColor = gaugeColor.copy(alpha = 0.2f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // AC/DC Badge
        ChargeTypeBadge(isDcCharging = isDcCharging, palette = palette)

        Spacer(modifier = Modifier.width(8.dp))

        // Circular Gauge with power value in center
        Box(
            modifier = Modifier.size(gaugeSize),
            contentAlignment = Alignment.Center
        ) {
            // Draw the gauge arcs
            Canvas(modifier = Modifier.size(gaugeSize)) {
                val strokeWidth = 4.dp.toPx()
                val arcSize = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Sweep angle: 270 degrees (leaving 90 degree gap at bottom)
                val startAngle = 135f  // Start at bottom-left
                val sweepAngle = 270f  // Sweep to bottom-right

                // Track (background arc)
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                val progressSweep = sweepAngle * gaugeProgress.coerceIn(0f, 1f)
                if (progressSweep > 0) {
                    drawArc(
                        color = gaugeColor,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Power value and kW unit stacked in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$powerKw",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor,
                    lineHeight = MaterialTheme.typography.titleSmall.lineHeight * 0.9f
                )
                Text(
                    text = "kW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = gaugeColor,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 0.8f
                )
            }
        }
    }
}

/**
 * Small badge showing AC or DC charging type.
 */
@Composable
private fun ChargeTypeBadge(isDcCharging: Boolean, palette: CarColorPalette) {
    val backgroundColor = if (isDcCharging) palette.dcColor else palette.acColor
    val text = if (isDcCharging) "DC" else "AC"

    Box(
        modifier = Modifier
            .width(26.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// Helper function to calculate gauge progress for DC charging (power-based)
fun calculateDcGaugeProgress(powerKw: Int, maxPowerKw: Int): Float {
    if (maxPowerKw <= 0) return 0f
    return (powerKw.toFloat() / maxPowerKw).coerceIn(0f, 1f)
}

// Helper function to calculate gauge progress for AC charging (current-based)
fun calculateAcGaugeProgress(actualCurrent: Int?, maxCurrent: Int?): Float {
    if (actualCurrent == null || maxCurrent == null || maxCurrent <= 0) return 0f
    return (actualCurrent.toFloat() / maxCurrent).coerceIn(0f, 1f)
}

@Preview(showBackground = true)
@Composable
private fun ChargingPowerGaugePreviewDc() {
    MateDroidTheme {
        ChargingPowerGauge(
            powerKw = 150,
            isDcCharging = true,
            gaugeProgress = 0.6f,  // 150/250
            palette = CarColorPalettes.forExteriorColor("White", false)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChargingPowerGaugePreviewAc() {
    MateDroidTheme {
        ChargingPowerGauge(
            powerKw = 11,
            isDcCharging = false,
            gaugeProgress = 0.5f,  // 16A/32A
            palette = CarColorPalettes.forExteriorColor("White", false)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChargingPowerGaugePreviewLow() {
    MateDroidTheme {
        ChargingPowerGauge(
            powerKw = 3,
            isDcCharging = false,
            gaugeProgress = 0.1f,
            palette = CarColorPalettes.forExteriorColor("White", false)
        )
    }
}
