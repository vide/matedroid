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

private val COST_WIDGET_BACKGROUND = Color(0xFF101413)
private val COST_WIDGET_PANEL = Color(0xFF18201E)
private val COST_WIDGET_ACCENT = Color(0xFF58D68D)
private val COST_WIDGET_SECONDARY = Color(0xFFFFC857)
private val COST_WIDGET_TEXT = Color.White
private val COST_WIDGET_MUTED = Color(0xB3FFFFFF)

/**
 * Read-only home screen widget that surfaces charging-cost intelligence.
 *
 * All database work happens in [CostSummaryWidgetUpdateWorker]; rendering only
 * reads preformatted values from Glance preferences. The widget never talks to
 * TeslaMate or the car — it works purely off the Room cache built by the app.
 */
class CostSummaryWidget : GlanceAppWidget() {

    companion object {
        val CAR_ID_KEY = intPreferencesKey("cost_summary_car_id")
        val HAS_DATA_KEY = booleanPreferencesKey("cost_summary_has_data")
        val HAS_CHARGES_KEY = booleanPreferencesKey("cost_summary_has_charges")
        val CAR_NAME_KEY = stringPreferencesKey("cost_summary_car_name")
        val RANGE_DAYS_KEY = intPreferencesKey("cost_summary_range_days")
        val PERIOD_LABEL_KEY = stringPreferencesKey("cost_summary_period_label")
        val COST_VALUE_KEY = stringPreferencesKey("cost_summary_cost_value")
        val COST_PER_DISTANCE_VALUE_KEY = stringPreferencesKey("cost_summary_cost_per_distance_value")
        val AVG_COST_PER_KWH_VALUE_KEY = stringPreferencesKey("cost_summary_avg_cost_per_kwh_value")
        val ENERGY_VALUE_KEY = stringPreferencesKey("cost_summary_energy_value")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("cost_summary_distance_unit")
        val COVERAGE_KEY = stringPreferencesKey("cost_summary_coverage")
        val CHARGES_WITH_COST_KEY = intPreferencesKey("cost_summary_charges_with_cost")
        val CHARGE_COUNT_KEY = intPreferencesKey("cost_summary_charge_count")
        val UPDATED_VALUE_KEY = stringPreferencesKey("cost_summary_updated_value")
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
            val openCostsIntent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("EXTRA_NAVIGATE_TO", "costs")
                .putExtra("EXTRA_SKIP_CAR_WIDGET_UPDATE", true)
                .apply {
                    if (carId != null) putExtra("EXTRA_CAR_ID", carId)
                }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .then(cornerMod)
                    .background(ColorProvider(COST_WIDGET_BACKGROUND))
                    .clickable(actionStartActivity(openCostsIntent))
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
                .background(ColorProvider(COST_WIDGET_BACKGROUND))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = ColorProvider(COST_WIDGET_MUTED),
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
        val periodLabel = prefs[PERIOD_LABEL_KEY]
            ?: context.getString(R.string.cost_widget_period_last_30_days)
        val hasCharges = prefs[HAS_CHARGES_KEY] ?: false
        val cost = prefs[COST_VALUE_KEY].orEmpty()
        val costPerDistance = prefs[COST_PER_DISTANCE_VALUE_KEY].orEmpty()
        val avgPerKwh = prefs[AVG_COST_PER_KWH_VALUE_KEY].orEmpty()
        val energy = prefs[ENERGY_VALUE_KEY].orEmpty()
        val distanceUnit = prefs[DISTANCE_UNIT_KEY].orEmpty()
        val coverage = CostSummaryWidgetCoverage.values().firstOrNull {
            it.name == prefs[COVERAGE_KEY]
        } ?: CostSummaryWidgetCoverage.None
        val chargesWithCost = prefs[CHARGES_WITH_COST_KEY] ?: 0
        val chargeCount = prefs[CHARGE_COUNT_KEY] ?: 0
        val updated = prefs[UPDATED_VALUE_KEY].orEmpty()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 8.dp else 12.dp,
                )
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (carName.isNotBlank()) {
                        Text(
                            text = carName,
                            style = TextStyle(
                                color = ColorProvider(COST_WIDGET_TEXT),
                                fontSize = if (compact) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = periodLabel,
                        style = TextStyle(
                            color = ColorProvider(COST_WIDGET_MUTED),
                            fontSize = if (compact) 10.sp else 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (!compact && updated.isNotBlank()) {
                    Text(
                        text = updated,
                        style = TextStyle(
                            color = ColorProvider(COST_WIDGET_MUTED),
                            fontSize = 10.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(if (compact) 4.dp else 8.dp))

            if (!hasCharges) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(ColorProvider(COST_WIDGET_PANEL))
                        .cornerRadius(8.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.cost_widget_empty),
                        style = TextStyle(
                            color = ColorProvider(COST_WIDGET_MUTED),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 2,
                    )
                }
            } else {
                // Hero row: total known cost + coverage badge.
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = cost,
                            style = TextStyle(
                                color = ColorProvider(COST_WIDGET_ACCENT),
                                fontSize = if (compact) 22.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = coverageLabel(
                                context = context,
                                coverage = coverage,
                                chargesWithCost = chargesWithCost,
                                chargeCount = chargeCount,
                            ),
                            style = TextStyle(
                                color = ColorProvider(COST_WIDGET_MUTED),
                                fontSize = 10.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                if (compact) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    CompactFooter(
                        costPerDistance = costPerDistance,
                        avgPerKwh = avgPerKwh,
                        coverage = coverage,
                    )
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricTile(
                            label = context.getString(
                                R.string.cost_widget_cost_per_distance,
                                distanceUnit,
                            ),
                            value = if (coverage == CostSummaryWidgetCoverage.Complete) {
                                costPerDistance
                            } else {
                                context.getString(R.string.cost_widget_no_value)
                            },
                            accent = COST_WIDGET_ACCENT,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        MetricTile(
                            label = context.getString(R.string.cost_widget_avg_cost_per_kwh),
                            value = avgPerKwh,
                            accent = COST_WIDGET_SECONDARY,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(7.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricTile(
                            label = context.getString(R.string.cost_widget_energy),
                            value = energy,
                            accent = COST_WIDGET_SECONDARY,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        MetricTile(
                            label = context.getString(R.string.cost_widget_charges),
                            value = "%,d".format(chargeCount),
                            accent = COST_WIDGET_TEXT,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }

    private fun coverageLabel(
        context: Context,
        coverage: CostSummaryWidgetCoverage,
        chargesWithCost: Int,
        chargeCount: Int,
    ): String = when (coverage) {
        CostSummaryWidgetCoverage.None -> context.getString(R.string.cost_widget_no_charges)
        CostSummaryWidgetCoverage.Missing -> context.getString(R.string.cost_widget_cost_none_priced)
        CostSummaryWidgetCoverage.Partial -> context.getString(
            R.string.cost_widget_cost_partially_priced,
            chargesWithCost,
            chargeCount,
        )
        CostSummaryWidgetCoverage.Complete -> context.getString(R.string.cost_widget_cost_all_priced)
    }

    @Composable
    private fun CompactFooter(
        costPerDistance: String,
        avgPerKwh: String,
        coverage: CostSummaryWidgetCoverage,
    ) {
        val context = LocalContext.current
        // Compact mode has room for one primary rate. Prefer the honest cost/100
        // when complete, otherwise fall back to the per-kWh price which is still
        // truthful under partial coverage (it excludes unpriced charges).
        val primary = when (coverage) {
            CostSummaryWidgetCoverage.Complete -> context.getString(
                R.string.cost_widget_compact_cost_per_distance,
                costPerDistance,
            )
            CostSummaryWidgetCoverage.Partial,
            CostSummaryWidgetCoverage.Missing,
            CostSummaryWidgetCoverage.None -> context.getString(
                R.string.cost_widget_compact_cost_per_distance_unavailable,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = primary,
                style = TextStyle(
                    color = ColorProvider(COST_WIDGET_TEXT),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = context.getString(R.string.cost_widget_compact_avg_cost_per_kwh, avgPerKwh),
                style = TextStyle(
                    color = ColorProvider(COST_WIDGET_MUTED),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun MetricTile(
        label: String,
        value: String,
        accent: Color,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Column(
            modifier = modifier
                .background(ColorProvider(COST_WIDGET_PANEL))
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(COST_WIDGET_MUTED),
                    fontSize = 9.sp,
                ),
                maxLines = 1,
            )
        }
    }

    suspend fun updateWidget(
        context: Context,
        glanceId: GlanceId,
        data: CostSummaryWidgetDisplayData,
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[CAR_ID_KEY] = data.carId
                this[HAS_DATA_KEY] = true
                this[HAS_CHARGES_KEY] = data.hasCharges
                this[CAR_NAME_KEY] = data.carName
                this[PERIOD_LABEL_KEY] = data.periodLabel
                this[COST_VALUE_KEY] = data.costValue
                this[COST_PER_DISTANCE_VALUE_KEY] = data.costPerDistanceValue
                this[AVG_COST_PER_KWH_VALUE_KEY] = data.avgCostPerKwhValue
                this[ENERGY_VALUE_KEY] = data.energyValue
                this[DISTANCE_UNIT_KEY] = data.distanceUnit
                this[COVERAGE_KEY] = data.costCoverage.name
                this[CHARGES_WITH_COST_KEY] = data.chargesWithCost
                this[CHARGE_COUNT_KEY] = data.chargeCount
                this[UPDATED_VALUE_KEY] = data.updatedValue
            }
        }
        update(context, glanceId)
    }
}
