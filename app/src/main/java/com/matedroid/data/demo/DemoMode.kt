package com.matedroid.data.demo

import java.time.Clock
import java.time.Instant

/**
 * Demo mode: the app running against a self-contained sample dataset instead of a
 * TeslaMate server.
 *
 * It exists so the app can be evaluated — by someone deciding whether TeslaMate is worth
 * setting up, and by app-store reviewers, who have no server to point the connection form
 * at and would otherwise never get past onboarding.
 *
 * The switch lives in the settings alongside the server URL rather than in a build flavour:
 * demo mode has to be reachable from the shipped release build, and has to be leavable
 * again without a reinstall.
 */
object DemoMode {
    /**
     * Stored as the server URL while demo mode is on.
     *
     * A sentinel rather than an extra "is the app configured?" condition: every gate that
     * decides whether onboarding is done already asks whether the server URL is blank, and
     * a scheme no real server can use keeps this value from ever being dialled. It is
     * [TeslamateApiFactory][com.matedroid.di.TeslamateApiFactory] that intercepts it, so no
     * request is ever built from this string.
     */
    const val SERVER_URL: String = "demo://matedroid"

    /** The demo dataset describes a single car, and the app addresses it by id. */
    const val CAR_ID: Int = 1

    fun isDemoUrl(url: String?): Boolean = url?.trim()?.trimEnd('/') == SERVER_URL

    /**
     * The clock the demo dataset reads "now" from.
     *
     * The dataset is anchored to the current day and the live session cycles on wall-clock
     * time, so two runs at different hours show a different car. The screenshot suite pins
     * this to a fixed instant so the pictures it takes are reproducible; nothing else touches
     * it and the app always runs on the system clock.
     */
    @Volatile
    internal var clock: Clock = Clock.systemUTC()

    internal fun now(): Instant = clock.instant()
}
