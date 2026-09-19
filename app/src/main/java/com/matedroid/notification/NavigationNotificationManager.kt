package com.matedroid.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.matedroid.MainActivity
import com.matedroid.R
import com.matedroid.data.api.models.ActiveRoute
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.map.SlippyMap
import com.matedroid.data.map.StaticMapRenderer
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.util.formatDuration
import com.matedroid.util.formatTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * The live notification shown while the car is navigating: destination, time left, arrival
 * time, and a map framing the car and where it is going.
 *
 * Its own channel, so turning navigation off in Android's settings leaves the charging,
 * sentry and tyre-pressure notifications alone.
 */
@Singleton
class NavigationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapRenderer: StaticMapRenderer
) {
    companion object {
        private const val TAG = "NavigationNotificationManager"
        const val CHANNEL_ID = "navigation_route_channel"
        const val NOTIFICATION_ID_BASE = 5000

        /** Android's recommended big-picture size: 2:1, 1024px wide. */
        private const val MAP_WIDTH_PX = 1024
        private const val MAP_HEIGHT_PX = 512

        /** Keeps a marker at the extreme of the span from being drawn half off the canvas. */
        private const val MAP_PADDING_PX = 72

        /**
         * Closest zoom the map will go to. Arriving, the two markers converge and the frame
         * would otherwise zoom to the housenumber, which tells the user nothing they cannot
         * see out of the window.
         */
        private const val MAP_MAX_ZOOM = 15

        /**
         * Shortest gap between two redraws of the map.
         *
         * The status is polled every 30s and the car has always moved a little, so without
         * this the picture would be re-stitched — and new tiles pulled — on every single
         * poll for the whole drive. The text still updates at the polling rate; only the
         * artwork lags, which nobody can see.
         */
        private const val MAP_MIN_REDRAW_MS = 60_000L

        /**
         * How coarsely the frame origin is quantised for [MapKey]. A frame that has shifted
         * by less than this is the same picture to the eye.
         */
        private const val MAP_KEY_QUANTUM_PX = 24.0
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    /** Identifies a rendered frame: same key, same picture. */
    private data class MapKey(val zoom: Int, val originX: Long, val originY: Long, val dark: Boolean)

    private data class RenderedMap(val bitmap: Bitmap, val key: MapKey, val renderedAtMs: Long)

    private val renderedMaps = ConcurrentHashMap<Int, RenderedMap>()

    /**
     * Show or update the navigation notification for a car.
     *
     * Suspends while the map is fetched. The notification is posted either way: a map that
     * cannot be drawn (offline, tile server down) costs the picture, not the notification.
     */
    suspend fun showNavigationNotification(
        car: CarData,
        status: CarStatus,
        route: ActiveRoute,
        units: Units?
    ) {
        createNotificationChannel()

        val destination = route.destination ?: return
        val map = renderMap(car, status, route)
        val notification = buildNotification(car, route, units, destination, map)

        notificationManager.notify(NOTIFICATION_ID_BASE + car.carId, notification)
        Log.d(
            TAG,
            "Navigation notification for car ${car.carId}: $destination, " +
                "${route.minutesToArrivalRounded} min left, map=${map != null}"
        )
    }

    /** Take the notification down — the route ended, or the car stopped reporting one. */
    fun cancelNotification(carId: Int) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + carId)
        renderedMaps.remove(carId)
        Log.d(TAG, "Cancelled navigation notification for car $carId")
    }

    /**
     * Create the channel up front, so it is listed in Android's settings before the car has
     * ever navigated anywhere and the user can turn it off in advance.
     */
    fun ensureChannelExists() {
        createNotificationChannel()
    }

    private fun buildNotification(
        car: CarData,
        route: ActiveRoute,
        units: Units?,
        destination: String,
        map: Bitmap?
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(destination)
            .setContentText(etaLine(route))
            .setSmallIcon(R.drawable.ic_notification_navigation)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // It ends by itself when the car arrives, and it updates every poll — being
            // ongoing keeps it out of the way of the alerting notifications and stops a
            // stray swipe from taking it away for the rest of the drive.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(createContentIntent(car.carId))

        // The secondary figures live in the header line, which survives both collapsed and
        // expanded: BigPictureStyle's summary would replace the ETA rather than join it.
        remainingLine(route, units)?.let { builder.setSubText(it) }

        if (map != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(map)
                    .setBigContentTitle(destination)
            )
        }

        return builder.build()
    }

    /** "23m · arriving at 18:04", with the traffic delay folded in when there is one. */
    private fun etaLine(route: ActiveRoute): String {
        val minutes = route.minutesToArrivalRounded ?: return ""
        val resources = context.resources
        val left = formatDuration(resources, minutes)
        val arrival = LocalDateTime.now()
            .plusMinutes(minutes.toLong())
            .formatTime(Locale.getDefault(), is24Hour(context))

        val delay = route.trafficDelayMinutes
        return if (delay != null) {
            context.getString(
                R.string.navigation_notification_eta_traffic,
                left,
                formatDuration(resources, delay),
                arrival
            )
        } else {
            context.getString(R.string.navigation_notification_eta, left, arrival)
        }
    }

    /** "29.8 km" on its own, or "29.8 km · 32% on arrival" when the car predicts a level. */
    private fun remainingLine(route: ActiveRoute, units: Units?): String? {
        val distance = route.distanceToArrival ?: return null
        val formatted = UnitFormatter.formatDistance(distance, units)
        val energy = route.energyAtArrival
            ?: return formatted
        return context.getString(
            R.string.navigation_notification_remaining_energy,
            formatted,
            energy
        )
    }

    /**
     * Draw the map framing the car and the destination, reusing the last one when nothing
     * worth redrawing has changed.
     *
     * Returns null when the car has no position — a map of the destination alone would be
     * a different picture from the one promised, so the notification goes out without it.
     */
    private suspend fun renderMap(car: CarData, status: CarStatus, route: ActiveRoute): Bitmap? {
        val carLat = status.latitude ?: return null
        val carLon = status.longitude ?: return null
        val destLat = route.location?.latitude ?: return null
        val destLon = route.location?.longitude ?: return null

        val carPoint = SlippyMap.Point(carLat, carLon)
        val destinationPoint = SlippyMap.Point(destLat, destLon)
        val dark = isDarkTheme()

        val frame = SlippyMap.frameFor(
            points = listOf(carPoint, destinationPoint),
            widthPx = MAP_WIDTH_PX,
            heightPx = MAP_HEIGHT_PX,
            paddingPx = MAP_PADDING_PX,
            maxZoom = MAP_MAX_ZOOM
        )
        val key = MapKey(
            zoom = frame.zoom,
            originX = (frame.originPxX / MAP_KEY_QUANTUM_PX).roundToLong(),
            originY = (frame.originPxY / MAP_KEY_QUANTUM_PX).roundToLong(),
            dark = dark
        )

        val cached = renderedMaps[car.carId]
        val tooSoon = cached != null &&
            System.currentTimeMillis() - cached.renderedAtMs < MAP_MIN_REDRAW_MS
        if (cached != null && !cached.bitmap.isRecycled && (cached.key == key || tooSoon)) {
            return cached.bitmap
        }

        val palette = CarColorPalettes.forExteriorColor(car.carExterior?.exteriorColor, dark)
        val accent = palette.accent.toArgb()

        val bitmap = mapRenderer.render(
            frame = frame,
            markers = listOf(
                StaticMapRenderer.Marker(carPoint, StaticMapRenderer.Marker.Style.DOT, accent),
                StaticMapRenderer.Marker(destinationPoint, StaticMapRenderer.Marker.Style.PIN, accent)
            ),
            dark = dark,
            connectMarkers = true
        )

        if (bitmap != null) {
            // The outgoing bitmap is deliberately not recycled: a notification posted a
            // moment ago may still be drawing it, and recycling under the system UI
            // crashes it.
            renderedMaps[car.carId] = RenderedMap(bitmap, key, System.currentTimeMillis())
            return bitmap
        }
        // Rather than no picture at all, keep showing the last good one.
        return cached?.bitmap?.takeUnless { it.isRecycled }
    }

    private fun isDarkTheme(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun is24Hour(context: Context): Boolean =
        android.text.format.DateFormat.is24HourFormat(context)

    private fun createContentIntent(carId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CAR_ID", carId)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + carId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.navigation_channel_name),
                // Silent: it refreshes every 30 seconds for a whole drive, and a sound or a
                // heads-up each time would be unbearable.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.navigation_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
