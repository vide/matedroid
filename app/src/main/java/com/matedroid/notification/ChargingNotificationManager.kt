package com.matedroid.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.matedroid.MainActivity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalettes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manager for charging session notifications.
 *
 * On Android 16+, displays Live Update notifications with a visual progress bar
 * showing battery level and charge progress.
 *
 * On older Android versions, displays standard persistent (dismissable) notifications.
 */
@Singleton
class ChargingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChargingNotificationManager"
        const val CHANNEL_ID = "charging_session_channel"
        const val NOTIFICATION_ID_BASE = 3000
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    /**
     * Show or update the charging notification for a car.
     *
     * @param car The car data (for name and image)
     * @param status The current car status with charging details
     */
    fun showChargingNotification(car: CarData, status: CarStatus, liveChargeAvailable: Boolean = false) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + car.carId
        val notification = buildNotification(car, status, liveChargeAvailable)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed charging notification for car ${car.carId}: ${status.batteryLevel}% -> ${status.chargeLimitSoc}%")
    }

    /**
     * Build a charging notification for a car (without showing it).
     * Used by ChargingMonitorService for foreground notification.
     */
    fun buildNotification(car: CarData, status: CarStatus, liveChargeAvailable: Boolean = false): Notification {
        createNotificationChannel()

        val carName = car.displayName
        val batteryLevel = status.batteryLevel ?: 0
        val chargeLimit = status.chargeLimitSoc ?: 80
        val chargerPower = status.chargerPower ?: 0
        val isDcCharging = status.isDcCharging
        val timeToFullCharge = status.timeToFullCharge

        val title = buildTitle(carName, timeToFullCharge)
        val contentText = buildContentText(
            batteryLevel = batteryLevel,
            chargeLimit = chargeLimit,
            chargerPower = chargerPower,
            isDcCharging = isDcCharging
        )
        val detailText = buildDetailText(status)

        return if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(
                car = car,
                title = title,
                contentText = contentText,
                detailText = detailText,
                batteryLevel = batteryLevel,
                chargeLimit = chargeLimit,
                liveChargeAvailable = liveChargeAvailable
            )
        } else {
            buildFallbackNotification(
                car = car,
                title = title,
                contentText = contentText,
                detailText = detailText,
                batteryLevel = batteryLevel,
                liveChargeAvailable = liveChargeAvailable
            )
        }
    }

    /**
     * Cancel the charging notification for a car.
     */
    fun cancelNotification(carId: Int) {
        val notificationId = NOTIFICATION_ID_BASE + carId
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled charging notification for car $carId")
    }

    /**
     * Ensure the notification channel exists.
     * Called by ChargingMonitorService before creating its foreground notification.
     */
    fun ensureChannelExists() {
        createNotificationChannel()
    }

    /**
     * Build title for the notification (e.g., "Elysa · Remaining 25 min").
     */
    private fun buildTitle(
        carName: String,
        timeToFullCharge: Double?
    ): String {
        val remaining = buildRemainingLabel(timeToFullCharge)
        val status = remaining ?: context.getString(R.string.charging_status_active)
        return "$carName \u00B7 $status"
    }

    /**
     * Build content text for the notification.
     */
    private fun buildContentText(
        batteryLevel: Int,
        chargeLimit: Int,
        chargerPower: Int,
        isDcCharging: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (chargerPower > 0) {
            val chargeType = if (isDcCharging) {
                context.getString(R.string.charging_dc)
            } else {
                context.getString(R.string.charging_ac)
            }
            parts.add(context.getString(R.string.charging_power_format, chargerPower, chargeType))
        }
        parts.add(context.getString(R.string.charging_soc_format, batteryLevel, chargeLimit))
        return parts.joinToString(" \u2022 ")
    }

    /**
     * Build secondary detail text (range/voltage/current/energy added).
     */
    private fun buildDetailText(status: CarStatus): String {
        val details = mutableListOf<String>()

        status.ratedBatteryRangeKm?.takeIf { it > 0 }?.let {
            details.add("${it.roundToInt()} km")
        }
        status.chargerVoltage?.takeIf { it > 0 }?.let {
            details.add("${it}V")
        }
        status.chargerActualCurrent?.takeIf { it > 0 }?.let {
            details.add("${it}A")
        }
        status.chargeEnergyAdded?.takeIf { it > 0 }?.let {
            details.add("+${"%.1f".format(it)} kWh")
        }

        return details.joinToString(" \u2022 ")
    }

    private fun formatTimeRemaining(hours: Double): String {
        val totalMinutes = (hours * 60).roundToInt().coerceAtLeast(1)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> context.getString(R.string.charging_time_h_m, h, m)
            h > 0 -> context.getString(R.string.charging_time_h, h)
            else -> context.getString(R.string.charging_time_m, m)
        }
    }

    private fun buildRemainingLabel(timeToFullCharge: Double?): String? {
        val hours = timeToFullCharge ?: return null
        if (hours <= 0) return null
        return context.getString(R.string.charging_remaining, formatTimeRemaining(hours))
    }

    /**
     * Prepare car image for square notification icon slot without distortion.
     * Keeps transparency and applies a subtle zoom so the car appears less tiny.
     */
    private fun prepareLargeIcon(raw: Bitmap): Bitmap {
        val size = maxOf(raw.width, raw.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val zoom = 1.12f
        val fitScale = min(size / raw.width.toFloat(), size / raw.height.toFloat()) * zoom
        val dstW = raw.width * fitScale
        val dstH = raw.height * fitScale
        val left = (size - dstW) / 2f
        val top = (size - dstH) / 2f

        canvas.drawBitmap(raw, null, RectF(left, top, left + dstW, top + dstH), paint)
        return out
    }

    private fun withAlpha(color: Int, alphaFraction: Float): Int {
        val alpha = (255f * alphaFraction).roundToInt().coerceIn(0, 255)
        return android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }

    private fun resolveAccentColor(car: CarData): Int {
        val palette = CarColorPalettes.forExteriorColor(
            car.carExterior?.exteriorColor,
            darkTheme = false
        )
        return palette.accent.toArgb()
    }

    private fun loadPreparedLargeIcon(car: CarData): Bitmap? {
        val raw = loadCarImage(car) ?: return null
        return prepareLargeIcon(raw)
    }

    private fun buildSummaryText(contentText: String, detailText: String): String {
        return if (detailText.isNotBlank()) "$contentText\n$detailText" else contentText
    }

    private fun buildFallbackStyle(
        contentText: String,
        detailText: String,
        largeIcon: Bitmap?
    ): NotificationCompat.Style {
        val summary = buildSummaryText(contentText, detailText)
        return if (largeIcon != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(largeIcon)
                .bigLargeIcon(null as Bitmap?)
                .setSummaryText(summary)
        } else {
            NotificationCompat.BigTextStyle().bigText(summary)
        }
    }

    /**
     * Build Android 16+ ProgressStyle notification with visual battery progress bar.
     * Uses NotificationCompat APIs (matching official Android sample).
     */
    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        car: CarData,
        title: String,
        contentText: String,
        detailText: String,
        batteryLevel: Int,
        chargeLimit: Int,
        liveChargeAvailable: Boolean
    ): Notification {
        val accentArgb = resolveAccentColor(car)
        val accentDimArgb = withAlpha(accentArgb, 0.45f)
        val grayArgb = android.graphics.Color.argb(80, 128, 128, 128)
        val largeIcon = loadPreparedLargeIcon(car)

        // Clamp values to safe ranges
        val soc = batteryLevel.coerceIn(0, 100)
        val limit = chargeLimit.coerceIn(soc, 100)

        Log.d(TAG, "ProgressStyle: soc=$soc, limit=$limit (segments: $soc, ${limit - soc}, ${100 - limit})")

        // 3 segments: charged (accent, bright) | charging-to-limit (accent, dimmed) | beyond limit (gray, dimmed)
        val segments = listOfNotNull(
            if (soc > 0) Notification.ProgressStyle.Segment(soc).setColor(accentArgb) else null,
            if (limit - soc > 0) Notification.ProgressStyle.Segment(limit - soc).setColor(accentDimArgb) else null,
            if (100 - limit > 0) Notification.ProgressStyle.Segment(100 - limit).setColor(grayArgb) else null,
        )

        val progressStyle = Notification.ProgressStyle()
            .setProgress(soc)
            .setStyledByProgress(true)
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_bolt)
            )
            .setProgressSegments(segments)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, soc, false)
            .setStyle(progressStyle)
            .setColor(accentArgb)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(createContentIntent(car.carId, liveChargeAvailable))

        if (detailText.isNotBlank()) {
            builder.setSubText(detailText)
        }

        // Add car image as large icon if available
        largeIcon?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        requestPromotedOngoing(builder)
        setShortCriticalTextIfAvailable(builder, soc)

        return builder.build()
    }

    /**
     * Build fallback notification for Android < 16.
     */
    private fun buildFallbackNotification(
        car: CarData,
        title: String,
        contentText: String,
        detailText: String,
        batteryLevel: Int,
        liveChargeAvailable: Boolean
    ): Notification {
        val accentArgb = resolveAccentColor(car)
        val largeIcon = loadPreparedLargeIcon(car)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, batteryLevel, false)
            .setColor(accentArgb)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .setContentIntent(createContentIntent(car.carId, liveChargeAvailable))
            .setStyle(buildFallbackStyle(contentText, detailText, largeIcon))

        largeIcon?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    @RequiresApi(36)
    private fun requestPromotedOngoing(builder: Notification.Builder) {
        val requestedViaMethod = runCatching {
            val method = Notification.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.javaPrimitiveType
            )
            method.invoke(builder, true)
            true
        }.getOrElse { false }

        if (!requestedViaMethod) {
            // Fallback for older preview SDKs where only extras key was available.
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
    }

    @RequiresApi(36)
    private fun setShortCriticalTextIfAvailable(builder: Notification.Builder, soc: Int) {
        runCatching {
            val method = Notification.Builder::class.java.getMethod(
                "setShortCriticalText",
                String::class.java
            )
            method.invoke(builder, "${soc}%")
        }
    }

    /**
     * Create a PendingIntent that opens the app.
     * When [liveChargeAvailable] is true, navigates to the current charge screen;
     * otherwise just opens the main activity.
     */
    private fun createContentIntent(carId: Int, liveChargeAvailable: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (liveChargeAvailable) {
                putExtra("EXTRA_NAVIGATE_TO", "current_charge")
                putExtra("EXTRA_CAR_ID", carId)
            }
        }
        return PendingIntent.getActivity(
            context,
            carId, // Use carId as requestCode for multi-car uniqueness
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Load car image from assets.
     */
    private fun loadCarImage(car: CarData): Bitmap? {
        return try {
            val assetPath = CarImageResolver.getAssetPath(
                model = car.carDetails?.model,
                exteriorColor = car.carExterior?.exteriorColor,
                wheelType = car.carExterior?.wheelType,
                trimBadging = car.carDetails?.trimBadging
            )

            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load car image", e)
            null
        }
    }

    /**
     * Create the notification channel for charging notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.charging_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT  // Default importance for lock screen visibility
            ).apply {
                description = context.getString(R.string.charging_channel_description)
                setShowBadge(false)  // Don't show app badge for ongoing charging
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)  // No sound
                enableVibration(false)  // No vibration
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Convert Compose Color to Android ARGB int.
     */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
