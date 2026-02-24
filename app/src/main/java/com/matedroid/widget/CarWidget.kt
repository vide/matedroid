package com.matedroid.widget

import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.currentState
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.util.GlowBitmapRenderer
import java.io.IOException
import kotlin.math.roundToInt

// Matches StatusSuccess / StatusError from Color.kt
private val ANDROID_STATUS_SUCCESS = android.graphics.Color.argb(230, 76, 175, 80)
private val ANDROID_STATUS_ERROR = android.graphics.Color.argb(255, 244, 67, 54)
private val ANDROID_STATUS_ERROR_DIM = android.graphics.Color.argb(178, 244, 67, 54)

/**
 * Home screen widget displaying real-time battery info for a configured car.
 *
 * All display data is persisted in Glance preferences so that [provideGlance]
 * can render real content without needing to inject [TeslamateRepository].
 * [updateWidget] writes every field from [CarWidgetDisplayData] into preferences
 * and then calls [update] to trigger a redraw.
 *
 * Uses [SizeMode.Exact] so the background bitmap is generated at the widget's
 * exact pixel dimensions, preventing any aspect-ratio distortion.
 */
class CarWidget : GlanceAppWidget() {

    companion object {
        // Glance preference keys — one per CarWidgetDisplayData field
        val CAR_ID_KEY = intPreferencesKey("car_id")
        val HAS_DATA_KEY = booleanPreferencesKey("has_data")
        val CAR_NAME_KEY = stringPreferencesKey("car_name")
        val EXTERIOR_COLOR_KEY = stringPreferencesKey("exterior_color")
        val MODEL_KEY = stringPreferencesKey("model")
        val TRIM_BADGING_KEY = stringPreferencesKey("trim_badging")
        val WHEEL_TYPE_KEY = stringPreferencesKey("wheel_type")
        val STATE_KEY = stringPreferencesKey("state")
        val IS_LOCKED_KEY = booleanPreferencesKey("is_locked")
        val SENTRY_MODE_KEY = booleanPreferencesKey("sentry_mode")
        val PLUGGED_IN_KEY = booleanPreferencesKey("plugged_in")
        val OUTSIDE_TEMP_KEY = floatPreferencesKey("outside_temp")   // Float.NaN if null
        val INSIDE_TEMP_KEY = floatPreferencesKey("inside_temp")     // Float.NaN if null
        val IS_CLIMATE_ON_KEY = booleanPreferencesKey("is_climate_on")
        val BATTERY_LEVEL_KEY = intPreferencesKey("battery_level")
        val RATED_RANGE_KEY = floatPreferencesKey("rated_range_km")  // -1 if null
        val CHARGE_LIMIT_KEY = intPreferencesKey("charge_limit_soc") // -1 if null
        val IS_CHARGING_KEY = booleanPreferencesKey("is_charging")
        val IS_DC_CHARGING_KEY = booleanPreferencesKey("is_dc_charging")
        val CHARGER_POWER_KEY = intPreferencesKey("charger_power")           // -1 if null
        val CHARGE_ENERGY_ADDED_KEY = floatPreferencesKey("charge_energy_added") // -1 if null
        val TIME_TO_FULL_KEY = floatPreferencesKey("time_to_full")           // -1 if null
        val CHARGER_VOLTAGE_KEY = intPreferencesKey("charger_voltage")       // -1 if null
        val CHARGER_CURRENT_KEY = intPreferencesKey("charger_current")       // -1 if null
        val AC_PHASES_KEY = intPreferencesKey("ac_phases")                   // -1 if null
        val SENTRY_EVENT_COUNT_KEY = intPreferencesKey("sentry_event_count")   // 0 = none
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact mode: LocalSize.current returns the actual rendered widget dimensions so the
    // background bitmap can be generated without distortion.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read state inside the composable so Glance observes the DataStore and
            // automatically re-renders whenever updateAppWidgetState writes new values.
            // Reading outside provideContent captured a one-time snapshot and caused
            // "Tap to configure" to persist even after confirmSelection wrote carId.
            val prefs = currentState<Preferences>()
            val carId = prefs[CAR_ID_KEY]
            val hasData = prefs[HAS_DATA_KEY] ?: false

            GlanceTheme {
                // Mirror the approach used by Glance's own Scaffold:
                // use system_app_widget_background_radius on API 31+, nothing on older devices
                // (pre-31 launchers don't clip widgets to rounded corners).
                val cornerMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
                } else {
                    GlanceModifier
                }
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .then(cornerMod)
                        .clickable(actionRunCallback<RefreshWidgetCallback>())
                ) {
                    when {
                        carId == null -> {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .background(ColorProvider(Color(0xFF1E2530)))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LocalContext.current.getString(com.matedroid.R.string.widget_error_configure),
                                    style = TextStyle(color = ColorProvider(Color.White))
                                )
                            }
                        }

                        !hasData -> {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .background(ColorProvider(Color(0xFF1E2530)))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LocalContext.current.getString(com.matedroid.R.string.widget_loading),
                                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.7f)))
                                )
                            }
                        }

                        else -> {
                            val ctx = LocalContext.current
                            val size = LocalSize.current
                            val density = ctx.resources.displayMetrics.density
                            val widthPx = (size.width.value * density).toInt().coerceAtLeast(100)
                            val heightPx = (size.height.value * density).toInt().coerceAtLeast(50)

                            val batteryLevel = prefs[BATTERY_LEVEL_KEY] ?: 0
                            val isCharging = prefs[IS_CHARGING_KEY] ?: false
                            val isDcCharging = prefs[IS_DC_CHARGING_KEY] ?: false
                            val carName = prefs[CAR_NAME_KEY] ?: ""
                            val ratedRange = prefs[RATED_RANGE_KEY]?.takeIf { it >= 0f }
                            val chargeLimit = prefs[CHARGE_LIMIT_KEY]?.takeIf { it >= 0 }
                            val chargeEnergyAdded = prefs[CHARGE_ENERGY_ADDED_KEY]?.takeIf { it >= 0f }
                            val timeToFull = prefs[TIME_TO_FULL_KEY]?.takeIf { it >= 0f }
                            val chargerVoltage = prefs[CHARGER_VOLTAGE_KEY]?.takeIf { it >= 0 }
                            val chargerCurrent = prefs[CHARGER_CURRENT_KEY]?.takeIf { it >= 0 }
                            val acPhases = prefs[AC_PHASES_KEY]?.takeIf { it >= 0 }

                            // Derive layout flags from widget size and charge state.
                            // isCompact drives padding/font-size; layout drives which fields appear.
                            val isCompact = size.height.value < COMPACT_HEIGHT_DP
                            val layout = computeWidgetLayout(size.width.value, size.height.value, isCharging)

                            // Bitmap generated at the exact widget pixel size — FillBounds is safe.
                            // showTemperatures is passed so the bitmap omits the right-side temp
                            // text at sizes where it is not part of the spec (1×n widgets).
                            val bgBitmap = buildBackgroundBitmap(
                                ctx, prefs, widthPx, heightPx, density, layout.showTemperatures
                            )
                            Image(
                                provider = ImageProvider(bgBitmap),
                                contentDescription = null,
                                modifier = GlanceModifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )

                            // Text overlay: battery data, range/limit and charging details,
                            // each field gated by the WidgetLayout spec flags.
                            Column(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .padding(
                                        horizontal = if (isCompact) 10.dp else 16.dp,
                                        vertical = if (isCompact) 6.dp else 10.dp
                                    )
                            ) {
                                Spacer(modifier = GlanceModifier.defaultWeight())

                                // Car name — shown at all sizes
                                if (carName.isNotEmpty()) {
                                    Text(
                                        text = carName,
                                        style = TextStyle(
                                            color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                                            fontSize = if (isCompact) 9.sp else 10.sp
                                        )
                                    )
                                }

                                // Battery % + AC/DC badge | range + charge limit (right-aligned)
                                val batteryColor = when {
                                    batteryLevel < 20 -> Color(0xFFEF5350)
                                    batteryLevel < 40 -> Color(0xFFFF9800)
                                    else -> Color.White
                                }
                                val batteryFontSize = when {
                                    isCompact && !layout.showTemperatures -> 16.sp  // 1×1
                                    isCompact -> 20.sp                              // 2×1
                                    else -> 24.sp
                                }
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$batteryLevel%",
                                        style = TextStyle(
                                            color = ColorProvider(batteryColor),
                                            fontSize = batteryFontSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (isCharging) {
                                        Text(
                                            text = if (isDcCharging) "  DC" else "  AC",
                                            style = TextStyle(
                                                color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                                                fontSize = if (isCompact) 10.sp else 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    if (layout.showMileage || layout.showChargeLimit) {
                                        Spacer(modifier = GlanceModifier.defaultWeight())
                                        val rightParts = buildList<String> {
                                            if (layout.showMileage && ratedRange != null)
                                                add("${ratedRange.roundToInt()} km")
                                            if (layout.showChargeLimit && chargeLimit != null)
                                                add("Limit: $chargeLimit%")
                                        }.joinToString("  ")
                                        if (rightParts.isNotEmpty()) {
                                            Text(
                                                text = rightParts,
                                                style = TextStyle(
                                                    color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                                                    fontSize = if (isCompact) 10.sp else 12.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                // Charging details:
                                //   kWh added + time to full  → all sizes when charging
                                //   voltage / current / phases → 2×2 and 3×2 only
                                if (isCharging) {
                                    val kwhTimePart = buildString {
                                        if (chargeEnergyAdded != null)
                                            append("+%.1f kWh".format(chargeEnergyAdded))
                                        if (timeToFull != null) {
                                            val h = timeToFull.toInt()
                                            val m = ((timeToFull - h) * 60).roundToInt()
                                            append(if (h > 0) " ${h}h ${m}m" else " ${m}m")
                                        }
                                    }.trim()
                                    val chargingText = if (layout.showVoltageCurrentPhases) {
                                        val voltPart = buildString {
                                            if (chargerVoltage != null) append("${chargerVoltage}V")
                                            if (chargerCurrent != null) append(" ${chargerCurrent}A")
                                            if (!isDcCharging && acPhases != null) append(" ${acPhases}φ")
                                        }.trim()
                                        listOf(voltPart, kwhTimePart)
                                            .filter { it.isNotEmpty() }
                                            .joinToString("  ")
                                    } else {
                                        kwhTimePart
                                    }
                                    if (chargingText.isNotEmpty()) {
                                        Text(
                                            text = chargingText,
                                            style = TextStyle(
                                                color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                                                fontSize = if (isCompact) 9.sp else 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Persists all [CarWidgetDisplayData] fields to Glance preferences and triggers
     * a redraw. This is the only way to get real data into the widget.
     */
    suspend fun updateWidget(context: Context, glanceId: GlanceId, data: CarWidgetDisplayData) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[CAR_ID_KEY] = data.carId
                this[HAS_DATA_KEY] = true
                this[CAR_NAME_KEY] = data.carName
                data.exteriorColor?.let { this[EXTERIOR_COLOR_KEY] = it }
                data.model?.let { this[MODEL_KEY] = it }
                data.trimBadging?.let { this[TRIM_BADGING_KEY] = it }
                data.wheelType?.let { this[WHEEL_TYPE_KEY] = it }
                data.state?.let { this[STATE_KEY] = it }
                this[IS_LOCKED_KEY] = data.isLocked
                this[SENTRY_MODE_KEY] = data.sentryModeActive
                this[PLUGGED_IN_KEY] = data.pluggedIn
                this[OUTSIDE_TEMP_KEY] = data.outsideTemp?.toFloat() ?: Float.NaN
                this[INSIDE_TEMP_KEY] = data.insideTemp?.toFloat() ?: Float.NaN
                this[IS_CLIMATE_ON_KEY] = data.isClimateOn
                this[BATTERY_LEVEL_KEY] = data.batteryLevel
                this[RATED_RANGE_KEY] = data.ratedBatteryRangeKm?.toFloat() ?: -1f
                this[CHARGE_LIMIT_KEY] = data.chargeLimitSoc ?: -1
                this[IS_CHARGING_KEY] = data.isCharging
                this[IS_DC_CHARGING_KEY] = data.isDcCharging
                this[CHARGER_POWER_KEY] = data.chargerPower ?: -1
                this[CHARGE_ENERGY_ADDED_KEY] = data.chargeEnergyAdded?.toFloat() ?: -1f
                this[TIME_TO_FULL_KEY] = data.timeToFullCharge?.toFloat() ?: -1f
                this[CHARGER_VOLTAGE_KEY] = data.chargerVoltage ?: -1
                this[CHARGER_CURRENT_KEY] = data.chargerActualCurrent ?: -1
                this[AC_PHASES_KEY] = data.acPhases ?: -1
                this[SENTRY_EVENT_COUNT_KEY] = data.sentryEventCount
            }
        }
        update(context, glanceId)
    }

    // -------------------------------------------------------------------------
    // Background bitmap generation
    // -------------------------------------------------------------------------

    /**
     * Generates the full background bitmap at the exact widget pixel size.
     * Layers (bottom to top):
     *  1. Palette surface color
     *  2. Car glow (if charging)
     *  3. Dimmed car image (aspect-ratio correct)
     *  4. Gradient scrim (dark at top and bottom, transparent in the middle)
     *  5. Status bar icons (state icon + lock + sentry dot + plug | temps)
     *  6. Progress bar at the very bottom
     */
    private fun buildBackgroundBitmap(
        context: Context,
        prefs: Preferences,
        width: Int,
        height: Int,
        density: Float = 2f,
        showTemperatures: Boolean = true
    ): Bitmap {
        val exteriorColor = prefs[EXTERIOR_COLOR_KEY]
        val model = prefs[MODEL_KEY]
        val trimBadging = prefs[TRIM_BADGING_KEY]
        val wheelType = prefs[WHEEL_TYPE_KEY]
        val state = prefs[STATE_KEY]
        val isLocked = prefs[IS_LOCKED_KEY] ?: false
        val sentryMode = prefs[SENTRY_MODE_KEY] ?: false
        val sentryEventCount = prefs[SENTRY_EVENT_COUNT_KEY] ?: 0
        val pluggedIn = prefs[PLUGGED_IN_KEY] ?: false
        val isClimateOn = prefs[IS_CLIMATE_ON_KEY] ?: false
        val isCharging = prefs[IS_CHARGING_KEY] ?: false
        val isDcCharging = prefs[IS_DC_CHARGING_KEY] ?: false
        val batteryLevel = prefs[BATTERY_LEVEL_KEY] ?: 0
        val chargeLimit = prefs[CHARGE_LIMIT_KEY]?.takeIf { it >= 0 }
        val outsideTemp = if (showTemperatures) prefs[OUTSIDE_TEMP_KEY]?.takeIf { !it.isNaN() } else null
        val insideTemp  = if (showTemperatures) prefs[INSIDE_TEMP_KEY]?.takeIf  { !it.isNaN() } else null

        val palette = CarColorPalettes.forExteriorColor(exteriorColor, darkTheme = true)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Solid background
        canvas.drawColor(colorToAndroidArgb(palette.surface))

        // Compact (1-cell-tall) mode uses tighter status-bar padding.
        val isCompact = height.toFloat() / density < COMPACT_HEIGHT_DP

        // Status bar: icon height matches the dashboard's compact icon size (16dp).
        // Top pad of 8dp and horizontal pad of 12dp keep all content well inside
        // the 16dp corner radius used by cornerRadius() above, preventing clipping.
        val iconSzPx = 16f * density
        val sbTopPadPx = if (isCompact) 5f * density else 8f * density
        val sbHorzPadPx = if (isCompact) 8f * density else 12f * density
        val statusBarH = iconSzPx + sbTopPadPx * 2f

        // 2 & 3. Car image (glow behind, dimmed car on top)
        // Cover mode: the car fills the full widget surface at all sizes.
        // The larger scale factor ensures neither dimension is left uncovered;
        // overflow is clipped by the canvas bounds.  The status bar, scrim and
        // progress bar are all drawn on top, so no space needs to be reserved.
        val carBitmap = loadCarBitmap(context, model, exteriorColor, wheelType, trimBadging)
        if (carBitmap != null) {
            val scaleByWidth = width.toFloat() / carBitmap.width
            val scaleByHeight = height.toFloat() / carBitmap.height
            val coverScale = maxOf(scaleByWidth, scaleByHeight)
            val scaledW = (carBitmap.width * coverScale).roundToInt().coerceAtLeast(1)
            val scaledH = (carBitmap.height * coverScale).roundToInt().coerceAtLeast(1)

            val carLeft = (width - scaledW) / 2f
            val carTop = (height - scaledH) / 2f

            // Glow first (behind the car)
            if (isCharging) {
                val glowColor = if (isDcCharging) palette.dcColor else palette.acColor
                val glowBitmap = GlowBitmapRenderer.createGlowBitmap(carBitmap, glowColor, 30f)
                val glowScaledW = (scaledW * 1.4f).roundToInt().coerceAtLeast(1)
                val glowScaledH = (scaledH * 1.4f).roundToInt().coerceAtLeast(1)
                val glowScaled = Bitmap.createScaledBitmap(glowBitmap, glowScaledW, glowScaledH, true)
                canvas.drawBitmap(
                    glowScaled,
                    (width - glowScaled.width) / 2f,
                    carTop - (glowScaled.height - scaledH) / 2f,
                    null
                )
                glowScaled.recycle()
                glowBitmap.recycle()
            }

            // Dimmed car on top
            val dimmed = GlowBitmapRenderer.createDimmedCarBitmap(carBitmap, 0.35f)
            val scaled = Bitmap.createScaledBitmap(dimmed, scaledW, scaledH, true)
            canvas.drawBitmap(scaled, carLeft, carTop, null)
            scaled.recycle()
            dimmed.recycle()
            carBitmap.recycle()
        }

        // 4. Gradient scrim: dark at top (status bar) + dark at bottom (text area)
        val scrimPaint = Paint()
        scrimPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                android.graphics.Color.argb(210, 0, 0, 0),
                android.graphics.Color.argb(0, 0, 0, 0),
                android.graphics.Color.argb(210, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // 5. Status bar icons (drawn after scrim so they are visible)
        drawStatusBar(
            context, canvas, sbTopPadPx, sbHorzPadPx, iconSzPx, width,
            state, isLocked, sentryMode, sentryEventCount, pluggedIn,
            isClimateOn, outsideTemp, insideTemp, palette
        )

        // 6. Progress bar at the very bottom
        val barH = (height * 0.06f).coerceAtLeast(8f).coerceAtMost(16f)
        val barTop = height - barH
        val barRadius = barH / 2f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Track
        barPaint.color = android.graphics.Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(RectF(0f, barTop, width.toFloat(), height.toFloat()), barRadius, barRadius, barPaint)

        // Charge limit zone (dimmed colour from current level to limit)
        if (chargeLimit != null && chargeLimit > batteryLevel) {
            val dimColor = if (isDcCharging) palette.dcColor else if (isCharging) palette.acColor else palette.accent
            barPaint.color = android.graphics.Color.argb(
                60,
                (dimColor.red * 255).toInt(),
                (dimColor.green * 255).toInt(),
                (dimColor.blue * 255).toInt()
            )
            canvas.drawRect(
                width * batteryLevel / 100f, barTop,
                width * chargeLimit / 100f, height.toFloat(),
                barPaint
            )
        }

        // Battery fill
        val fillColor = when {
            isCharging && isDcCharging -> palette.dcColor
            isCharging -> palette.acColor
            batteryLevel < 20 -> Color(0xFFEF5350)
            batteryLevel < 40 -> Color(0xFFFF9800)
            else -> palette.accent
        }
        barPaint.color = android.graphics.Color.argb(
            230,
            (fillColor.red * 255).toInt(),
            (fillColor.green * 255).toInt(),
            (fillColor.blue * 255).toInt()
        )
        val fillW = width * batteryLevel / 100f
        if (fillW > 0) {
            canvas.drawRoundRect(RectF(0f, barTop, fillW, height.toFloat()), barRadius, barRadius, barPaint)
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Status bar: drawn into the bitmap to match the dashboard layout exactly.
    // LEFT side:  state icon → lock icon → sentry dot (if active) → plug (if plugged, not charging)
    // RIGHT side: "Ext: XX°   Int: XX°" (green + bold when climate on)
    // -------------------------------------------------------------------------

    private fun drawStatusBar(
        context: Context,
        canvas: Canvas,
        topPad: Float,
        horzPad: Float,
        iconSz: Float,
        bitmapWidth: Int,
        state: String?,
        isLocked: Boolean,
        sentryMode: Boolean,
        sentryEventCount: Int,
        pluggedIn: Boolean,
        isClimateOn: Boolean,
        outsideTemp: Float?,
        insideTemp: Float?,
        palette: CarColorPalette
    ) {
        val stateLower = state?.lowercase()
        val isCharging = stateLower == "charging"
        val isDriving = stateLower == "driving"
        val isAsleep = stateLower in listOf("asleep", "suspended")
        val isAwake = stateLower in listOf("online", "charging", "driving", "updating")

        // State icon colour: StatusSuccess (green) if awake, onSurfaceVariant (grey) otherwise
        val stateColor = if (isAwake) ANDROID_STATUS_SUCCESS
        else colorToAndroidArgb(palette.onSurfaceVariant.copy(alpha = 0.8f))

        // onSurfaceVariant as an Android int (for lock + plug)
        val variantColor = colorToAndroidArgb(palette.onSurfaceVariant.copy(alpha = 0.85f))

        // Lock colour: grey when locked, light red when unlocked
        val lockColor = if (isLocked) variantColor else ANDROID_STATUS_ERROR_DIM

        val cy = topPad + iconSz / 2f    // vertical centre of the status bar row
        var cursorX = horzPad

        // --- State icon ---
        val stateIconRes = when {
            isCharging -> com.matedroid.R.drawable.ic_bolt
            isAsleep   -> com.matedroid.R.drawable.ic_bedtime
            isDriving  -> com.matedroid.R.drawable.ic_steering_wheel
            else       -> com.matedroid.R.drawable.ic_power_settings_new
        }
        drawIcon(context, canvas, stateIconRes, cursorX + iconSz / 2f, cy, iconSz, stateColor)
        val iconGap = iconSz * 0.5f   // gap between icons, ~8dp
        cursorX += iconSz + iconGap

        // --- Lock icon ---
        val lockIconRes = if (isLocked) com.matedroid.R.drawable.ic_lock
                          else          com.matedroid.R.drawable.ic_lock_open
        drawIcon(context, canvas, lockIconRes, cursorX + iconSz / 2f, cy, iconSz, lockColor)
        cursorX += iconSz + iconGap

        // --- Sentry dot (12dp-equivalent red circle, same as dashboard) + event count ---
        if (sentryMode) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ANDROID_STATUS_ERROR
                style = Paint.Style.FILL
            }
            val dotR = iconSz * 0.25f
            canvas.drawCircle(cursorX + dotR, cy, dotR, dotPaint)
            cursorX += dotR * 2f

            if (sentryEventCount > 0) {
                val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ANDROID_STATUS_ERROR
                    textSize = iconSz * 0.65f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.LEFT
                }
                val countBaseline = cy + countPaint.textSize * 0.36f
                val countGap = iconSz * 0.15f
                canvas.drawText("$sentryEventCount", cursorX + countGap, countBaseline, countPaint)
                cursorX += countGap + countPaint.measureText("$sentryEventCount")
            }

            cursorX += iconGap
        }

        // --- Plug icon (shown when plugged in but not currently charging) ---
        if (pluggedIn && !isCharging) {
            drawIcon(context, canvas, com.matedroid.R.drawable.ic_plug,
                cursorX + iconSz / 2f, cy, iconSz, variantColor)
        }

        // --- Temperatures (RIGHT side, right-aligned) ---
        val tempParts = buildList<String> {
            if (outsideTemp != null) add("Ext: %.0f°".format(outsideTemp))
            if (insideTemp != null) add("Int: %.0f°".format(insideTemp))
        }
        if (tempParts.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            textPaint.textSize = iconSz * 0.72f
            textPaint.textAlign = Paint.Align.RIGHT
            if (isClimateOn) {
                textPaint.color = ANDROID_STATUS_SUCCESS
                textPaint.typeface = Typeface.DEFAULT_BOLD
            } else {
                textPaint.color = variantColor
                textPaint.typeface = Typeface.DEFAULT
            }
            // Baseline: centre the text vertically within the icon row
            val textBaseline = cy + textPaint.textSize * 0.36f
            canvas.drawText(
                tempParts.joinToString("  "),
                bitmapWidth - horzPad,
                textBaseline,
                textPaint
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Draws an XML vector drawable centered at (cx, cy) within a sizePx × sizePx box,
     * tinted to [color]. Uses [ContextCompat] + [DrawableCompat] so it works on API 21+
     * without requiring the appcompat library directly.
     */
    private fun drawIcon(context: Context, canvas: Canvas, resId: Int, cx: Float, cy: Float, sizePx: Float, color: Int) {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
        DrawableCompat.setTint(DrawableCompat.wrap(drawable), color)
        val half = sizePx / 2f
        drawable.setBounds(
            (cx - half).toInt(),
            (cy - half).toInt(),
            (cx + half).toInt(),
            (cy + half).toInt()
        )
        drawable.draw(canvas)
    }

    private fun loadCarBitmap(
        context: Context,
        model: String?,
        exteriorColor: String?,
        wheelType: String?,
        trimBadging: String?
    ): Bitmap? {
        val assetPath = CarImageResolver.getAssetPath(model, exteriorColor, wheelType, trimBadging)
        return try {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (_: IOException) {
            try {
                val fallback = CarImageResolver.getDefaultAssetPath(model)
                context.assets.open(fallback).use { BitmapFactory.decodeStream(it) }
            } catch (_: IOException) {
                null
            }
        }
    }

    /** Converts a Compose [Color] to an Android packed ARGB int. */
    private fun colorToAndroidArgb(color: Color): Int = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}
