package com.matedroid.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.matedroid.MainActivity
import com.matedroid.R

private val TRIP_WIDGET_BACKGROUND = Color(0xFF101413)
private val TRIP_WIDGET_PANEL = Color(0xFF18201E)
private val TRIP_WIDGET_ACCENT = Color(0xFF58D68D)
private val TRIP_WIDGET_SECONDARY = Color(0xFFFFC857)
private val TRIP_WIDGET_TEXT = Color.White
private val TRIP_WIDGET_MUTED = Color(0xB3FFFFFF)

/**
 * Read-only home screen widget focused on recent driving and charging summaries.
 *
 * All database work happens in [TripSummaryWidgetUpdateWorker]. Rendering uses
 * preformatted values from Glance preferences so the widget itself never talks
 * to TeslaMate or the car.
 */
class TripSummaryWidget : GlanceAppWidget() {

    companion object {
        val CAR_ID_KEY = intPreferencesKey("trip_summary_car_id")
        val HAS_DATA_KEY = booleanPreferencesKey("trip_summary_has_data")
        val HAS_DRIVES_KEY = booleanPreferencesKey("trip_summary_has_drives")
        val CAR_NAME_KEY = stringPreferencesKey("trip_summary_car_name")
        val DISTANCE_VALUE_KEY = stringPreferencesKey("trip_summary_distance_value")
        val DRIVE_COUNT_VALUE_KEY = stringPreferencesKey("trip_summary_drive_count_value")
        val DRIVING_DAYS_VALUE_KEY = stringPreferencesKey("trip_summary_driving_days_value")
        val ENERGY_VALUE_KEY = stringPreferencesKey("trip_summary_energy_value")
        val EFFICIENCY_VALUE_KEY = stringPreferencesKey("trip_summary_efficiency_value")
        val COST_VALUE_KEY = stringPreferencesKey("trip_summary_cost_value")
        val COST_PER_DISTANCE_VALUE_KEY = stringPreferencesKey("trip_summary_cost_per_distance_value")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("trip_summary_distance_unit")
        val UPDATED_VALUE_KEY = stringPreferencesKey("trip_summary_updated_value")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    internal fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val carId = prefs[CAR_ID_KEY]
        val hasData = prefs[HAS_DATA_KEY] ?: false
        val context = LocalContext.current

        GlanceTheme {
            val cornerMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
            } else {
                GlanceModifier
            }
            val openTripsIntent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("EXTRA_NAVIGATE_TO", "trips")
                .putExtra("EXTRA_SKIP_CAR_WIDGET_UPDATE", true)
                .apply {
                    if (carId != null) putExtra("EXTRA_CAR_ID", carId)
                }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .then(cornerMod)
                    .background(ColorProvider(TRIP_WIDGET_BACKGROUND))
                    .clickable(actionStartActivity(openTripsIntent))
            ) {
                when {
                    carId == null -> CenteredMessage(context.getString(R.string.widget_error_configure))
                    !hasData -> CenteredMessage(context.getString(R.string.widget_loading))
                    else -> SummaryContent(prefs)
                }
            }
        }
    }

    @Composable
    private fun CenteredMessage(text: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(TRIP_WIDGET_BACKGROUND))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = ColorProvider(TRIP_WIDGET_MUTED),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )
        }
    }

    @Composable
    private fun SummaryContent(prefs: Preferences) {
        val context = LocalContext.current
        val size = LocalSize.current
        val compact = size.height.value < 115f || size.width.value < 190f

        val carName = prefs[CAR_NAME_KEY].orEmpty()
        val hasDrives = prefs[HAS_DRIVES_KEY] ?: false
        val distance = prefs[DISTANCE_VALUE_KEY].orEmpty()
        val drives = prefs[DRIVE_COUNT_VALUE_KEY].orEmpty()
        val drivingDays = prefs[DRIVING_DAYS_VALUE_KEY].orEmpty()
        val energy = prefs[ENERGY_VALUE_KEY].orEmpty()
        val efficiency = prefs[EFFICIENCY_VALUE_KEY].orEmpty()
        val cost = prefs[COST_VALUE_KEY].orEmpty()
        val costPerDistance = prefs[COST_PER_DISTANCE_VALUE_KEY].orEmpty()
        val distanceUnit = prefs[DISTANCE_UNIT_KEY].orEmpty()
        val updated = prefs[UPDATED_VALUE_KEY].orEmpty()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 8.dp else 12.dp
                )
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (carName.isNotBlank()) {
                        Text(
                            text = carName,
                            style = TextStyle(
                                color = ColorProvider(TRIP_WIDGET_TEXT),
                                fontSize = if (compact) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = context.getString(R.string.trip_widget_period_last_30_days),
                        style = TextStyle(
                            color = ColorProvider(TRIP_WIDGET_MUTED),
                            fontSize = if (compact) 10.sp else 11.sp
                        ),
                        maxLines = 1
                    )
                }
                if (!compact && updated.isNotBlank()) {
                    Text(
                        text = updated,
                        style = TextStyle(
                            color = ColorProvider(TRIP_WIDGET_MUTED),
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(if (compact) 4.dp else 8.dp))

            if (!hasDrives) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(ColorProvider(TRIP_WIDGET_PANEL))
                        .cornerRadius(8.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.trip_widget_empty),
                        style = TextStyle(
                            color = ColorProvider(TRIP_WIDGET_MUTED),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = distance,
                            style = TextStyle(
                                color = ColorProvider(TRIP_WIDGET_ACCENT),
                                fontSize = if (compact) 22.sp else 30.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = context.getString(R.string.distance),
                            style = TextStyle(
                                color = ColorProvider(TRIP_WIDGET_MUTED),
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = drives,
                            style = TextStyle(
                                color = ColorProvider(TRIP_WIDGET_TEXT),
                                fontSize = if (compact) 18.sp else 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = context.getString(R.string.nav_drives),
                            style = TextStyle(
                                color = ColorProvider(TRIP_WIDGET_MUTED),
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                if (compact) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    CompactFooter(cost = cost, efficiency = efficiency)
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricTile(
                            label = context.getString(R.string.trip_widget_energy),
                            value = energy,
                            accent = TRIP_WIDGET_SECONDARY,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        MetricTile(
                            label = context.getString(R.string.cost),
                            value = cost,
                            accent = TRIP_WIDGET_ACCENT,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(7.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricTile(
                            label = context.getString(R.string.trip_widget_efficiency),
                            value = efficiency,
                            accent = TRIP_WIDGET_ACCENT,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        MetricTile(
                            label = context.getString(R.string.trip_widget_cost_per_distance, distanceUnit),
                            value = costPerDistance,
                            accent = TRIP_WIDGET_SECONDARY,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        MetricTile(
                            label = context.getString(R.string.trip_widget_driving_days),
                            value = drivingDays,
                            accent = TRIP_WIDGET_TEXT,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactFooter(cost: String, efficiency: String) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.trip_widget_compact_cost, cost),
                style = TextStyle(
                    color = ColorProvider(TRIP_WIDGET_TEXT),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = context.getString(R.string.trip_widget_compact_efficiency, efficiency),
                style = TextStyle(
                    color = ColorProvider(TRIP_WIDGET_MUTED),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun MetricTile(
        label: String,
        value: String,
        accent: Color,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Column(
            modifier = modifier
                .background(ColorProvider(TRIP_WIDGET_PANEL))
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(TRIP_WIDGET_MUTED),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }

    suspend fun updateWidget(
        context: Context,
        glanceId: GlanceId,
        data: TripSummaryWidgetDisplayData
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[CAR_ID_KEY] = data.carId
                this[HAS_DATA_KEY] = true
                this[HAS_DRIVES_KEY] = data.hasDrives
                this[CAR_NAME_KEY] = data.carName
                this[DISTANCE_VALUE_KEY] = data.distanceValue
                this[DRIVE_COUNT_VALUE_KEY] = data.driveCountValue
                this[DRIVING_DAYS_VALUE_KEY] = data.drivingDaysValue
                this[ENERGY_VALUE_KEY] = data.energyValue
                this[EFFICIENCY_VALUE_KEY] = data.efficiencyValue
                this[COST_VALUE_KEY] = data.costValue
                this[COST_PER_DISTANCE_VALUE_KEY] = data.costPerDistanceValue
                this[DISTANCE_UNIT_KEY] = data.distanceUnit
                this[UPDATED_VALUE_KEY] = data.updatedValue
            }
        }
        update(context, glanceId)
    }
}
