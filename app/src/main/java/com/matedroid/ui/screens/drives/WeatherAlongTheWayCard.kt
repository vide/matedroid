package com.matedroid.ui.screens.drives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.WeatherCondition
import com.matedroid.data.repository.WeatherPoint
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons

/**
 * Displays weather conditions along the drive route in a table format.
 *
 * Table columns:
 * 1. Time (HH:mm)
 * 2. Distance from start
 * 3. Weather icon + temperature
 *
 * @param weatherPoints List of weather data points along the route
 * @param units Unit settings for formatting
 * @param isLoading Whether weather data is still loading
 */
@Composable
fun WeatherAlongTheWayCard(
    weatherPoints: List<WeatherPoint>,
    units: Units?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = CustomIcons.WeatherPartlyCloudy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Weather Along the Way",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Loading weather data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (weatherPoints.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Weather data unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                // Table header
                WeatherTableHeader()

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )

                // Weather data rows
                weatherPoints.forEachIndexed { index, weatherPoint ->
                    val isLastPoint = index == weatherPoints.size - 1
                    WeatherTableRow(
                        weatherPoint = weatherPoint,
                        units = units,
                        isLastPoint = isLastPoint
                    )

                    if (!isLastPoint) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Time",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Distance",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Weather",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun WeatherTableRow(
    weatherPoint: WeatherPoint,
    units: Units?,
    isLastPoint: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Time column
        Text(
            text = weatherPoint.time,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // Distance column
        Text(
            text = formatWeatherDistance(weatherPoint.distanceKm, units, isLastPoint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Weather column (temperature + icon)
        Row(
            modifier = Modifier.weight(1.5f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = UnitFormatter.formatTemperature(weatherPoint.temperatureCelsius, units),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = getWeatherIcon(weatherPoint.weatherCondition),
                contentDescription = getWeatherDescription(weatherPoint.weatherCondition),
                modifier = Modifier.size(24.dp),
                tint = getWeatherIconColor(weatherPoint.weatherCondition)
            )
        }
    }
}

/**
 * Returns the appropriate weather icon for a given weather condition.
 */
private fun getWeatherIcon(condition: WeatherCondition): ImageVector {
    return when (condition) {
        WeatherCondition.CLEAR -> CustomIcons.WeatherSunny
        WeatherCondition.PARTLY_CLOUDY -> CustomIcons.WeatherPartlyCloudy
        WeatherCondition.FOG -> CustomIcons.WeatherFog
        WeatherCondition.DRIZZLE -> CustomIcons.WeatherDrizzle
        WeatherCondition.RAIN -> CustomIcons.WeatherRain
        WeatherCondition.SNOW -> CustomIcons.WeatherSnow
        WeatherCondition.THUNDERSTORM -> CustomIcons.WeatherThunderstorm
    }
}

/**
 * Returns the appropriate color for a weather icon.
 */
@Composable
private fun getWeatherIconColor(condition: WeatherCondition): Color {
    return when (condition) {
        WeatherCondition.CLEAR -> Color(0xFFFFC107) // Amber/Yellow for sun
        WeatherCondition.PARTLY_CLOUDY -> Color(0xFF78909C) // Blue Grey
        WeatherCondition.FOG -> Color(0xFF90A4AE) // Light Grey
        WeatherCondition.DRIZZLE -> Color(0xFF64B5F6) // Light Blue
        WeatherCondition.RAIN -> Color(0xFF1E88E5) // Blue
        WeatherCondition.SNOW -> Color(0xFF42A5F5) // Light Blue
        WeatherCondition.THUNDERSTORM -> Color(0xFF7E57C2) // Purple
    }
}

/**
 * Returns a human-readable description for a weather condition.
 */
private fun getWeatherDescription(condition: WeatherCondition): String {
    return when (condition) {
        WeatherCondition.CLEAR -> "Clear sky"
        WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
        WeatherCondition.FOG -> "Foggy"
        WeatherCondition.DRIZZLE -> "Light rain"
        WeatherCondition.RAIN -> "Rain"
        WeatherCondition.SNOW -> "Snow"
        WeatherCondition.THUNDERSTORM -> "Thunderstorm"
    }
}

/**
 * Formats distance for the weather table.
 * Shows "Start" for 0km, "End" for the last point, and formats with appropriate units otherwise.
 */
private fun formatWeatherDistance(distanceKm: Double, units: Units?, isLastPoint: Boolean): String {
    if (isLastPoint) {
        return "End"
    }

    if (distanceKm < 0.1) {
        return "Start"
    }

    val isImperial = units?.isImperial == true
    return if (isImperial) {
        val miles = distanceKm * 0.621371
        "%.1f mi".format(miles)
    } else {
        "%.1f km".format(distanceKm)
    }
}
