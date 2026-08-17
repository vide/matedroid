package com.matedroid.domain

/**
 * How long the app waits for a TeslamateAPI connection to be established
 * (Settings → Connection → "Connect timeout").
 *
 * OkHttp's connect timeout covers the TLS handshake as well as the TCP connect, and some
 * setups need a lot more than a local server does: a Tailscale Funnel handshake takes ~2s,
 * so the 1 second the app used to hardcode aborted the connection before it ever completed
 * and the screen just stayed empty.
 *
 * A single low number cannot serve everyone, hence the setting. The repository tries the
 * primary server on *every* request, so users with a primary + secondary URL (typically a
 * LAN address and a VPN address for the same server) pay this timeout on each call while on
 * the other network before the fallback kicks in — for them a short timeout is the point.
 * That is what [AUTO] balances, and why it is the default.
 */
object ConnectionTimeout {
    /**
     * Picks the timeout from the shape of the configuration instead of a fixed number.
     * Rendered as "Automatic" in the picker.
     */
    const val AUTO = 0

    /** Fast failover for a configured fallback server, the value the app used to hardcode. */
    const val WITH_FALLBACK_SECONDS = 1

    /** Room for a slow TLS handshake when there is no fallback server to race against. */
    const val WITHOUT_FALLBACK_SECONDS = 5

    /** Values offered in the Settings picker, with [AUTO] first as the default. */
    val PRESETS = listOf(AUTO, 1, 2, 3, 5, 10, 15)

    /**
     * The timeout to hand OkHttp, in seconds.
     *
     * @param setting the stored preference, [AUTO] or an explicit number of seconds
     * @param hasFallbackServer whether a secondary server URL is configured
     */
    fun resolveSeconds(setting: Int, hasFallbackServer: Boolean): Int = when {
        setting != AUTO -> setting
        hasFallbackServer -> WITH_FALLBACK_SECONDS
        else -> WITHOUT_FALLBACK_SECONDS
    }
}
