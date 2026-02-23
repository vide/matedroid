package com.matedroid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

@Immutable
data class CarColorPalette(
    val surface: Color,
    val accent: Color,
    val accentDim: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val progressTrack: Color,
    val acColor: Color,  // AC charging color (~green)
    val dcColor: Color   // DC charging color (~orange)
)

/**
 * Color helper functions for HSL color space manipulation
 */
private data class HSL(val h: Float, val s: Float, val l: Float)

private fun Color.toHSL(): HSL {
    val r = red
    val g = green
    val b = blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var h = 0f
    val s: Float
    val l = (max + min) / 2f

    if (delta == 0f) {
        h = 0f
        s = 0f
    } else {
        s = if (l < 0.5f) delta / (max + min) else delta / (2f - max - min)

        h = when (max) {
            r -> ((g - b) / delta + (if (g < b) 6f else 0f)) / 6f
            g -> ((b - r) / delta + 2f) / 6f
            b -> ((r - g) / delta + 4f) / 6f
            else -> 0f
        }
    }

    return HSL(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hue = h.coerceIn(0f, 1f)
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)

    if (sat == 0f) {
        return Color(light, light, light, alpha)
    }

    val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
    val p = 2f * light - q

    fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var t2 = t
        if (t2 < 0f) t2 += 1f
        if (t2 > 1f) t2 -= 1f
        return when {
            t2 < 1f/6f -> p + (q - p) * 6f * t2
            t2 < 1f/2f -> q
            t2 < 2f/3f -> p + (q - p) * (2f/3f - t2) * 6f
            else -> p
        }
    }

    val r = hueToRgb(p, q, hue + 1f/3f)
    val g = hueToRgb(p, q, hue)
    val b = hueToRgb(p, q, hue - 1f/3f)

    return Color(r, g, b, alpha)
}

/**
 * Harmonize AC color (green) with the given accent color.
 * Maintains green identity while subtly shifting hue towards accent.
 */
private fun harmonizeAcColor(accent: Color, isDark: Boolean): Color {
    // Base green colors
    val baseGreenLight = Color(0xFF4CAF50)  // Material Green 500
    val baseGreenDark = Color(0xFF66BB6A)   // Material Green 400

    val baseGreen = if (isDark) baseGreenDark else baseGreenLight
    val greenHSL = baseGreen.toHSL()
    val accentHSL = accent.toHSL()

    // Blend hue towards accent (0.0 base color -> 1.0 accent color)
    val blendFactor = 0.20f
    val hueDiff = accentHSL.h - greenHSL.h
    val newHue = (greenHSL.h + hueDiff * blendFactor).coerceIn(0f, 1f)

    // Adjust saturation and lightness for better visibility
    val newSaturation = if (isDark) {
        (greenHSL.s * 0.95f).coerceIn(0.4f, 1f)
    } else {
        (greenHSL.s * 0.85f).coerceIn(0.3f, 0.9f)
    }

    val newLightness = if (isDark) {
        greenHSL.l * 0.55f  // Slightly brighter in dark mode
    } else {
        greenHSL.l * 0.95f  // Slightly softer in light mode
    }

    return hslToColor(newHue, newSaturation, newLightness.coerceIn(0.3f, 0.7f))
}

/**
 * Harmonize DC color (orange) with the given accent color.
 * Maintains orange identity while subtly shifting hue towards accent.
 */
private fun harmonizeDcColor(accent: Color, isDark: Boolean): Color {
    // Base orange colors
    val baseOrangeLight = Color(0xFFFF9800)  // Material Orange 500
    val baseOrangeDark = Color(0xFFFFA726)   // Material Orange 400

    val baseOrange = if (isDark) baseOrangeDark else baseOrangeLight
    val orangeHSL = baseOrange.toHSL()
    val accentHSL = accent.toHSL()

    // Blend hue towards accent (0.0 base color -> 1.0 accent color)
    val blendFactor = 0.10f
    val hueDiff = accentHSL.h - orangeHSL.h
    val newHue = (orangeHSL.h + hueDiff * blendFactor).coerceIn(0f, 1f)

    // Adjust saturation and lightness
    val newSaturation = if (isDark) {
        (orangeHSL.s * 1.05f).coerceIn(0.6f, 1f)
    } else {
        (orangeHSL.s * 0.95f).coerceIn(0.5f, 0.9f)
    }

    val newLightness = if (isDark) {
        orangeHSL.l * 0.55f
    } else {
        orangeHSL.l * 0.95f
    }

    return hslToColor(newHue, newSaturation, newLightness.coerceIn(0.35f, 0.75f))
}

// Default palette (used when no car color is available) - uses white car palette
val DefaultLightPalette = CarColorPalette(
    surface = Color(0xFFF5F3F0),
    accent = Color(0xFF8B7355),
    accentDim = Color(0xFF8B7355).copy(alpha = 0.3f),
    onSurface = Color(0xFF2A2520),
    onSurfaceVariant = Color(0xFF2A2520).copy(alpha = 0.7f),
    progressTrack = Color(0xFF2A2520).copy(alpha = 0.1f),
    acColor = harmonizeAcColor(accent = Color(0xFF8B7355), isDark = false),
    dcColor = harmonizeDcColor(accent = Color(0xFF8B7355), isDark = false)
)

val DefaultDarkPalette = CarColorPalette(
    surface = Color(0xFF1E2530),
    accent = Color(0xFF8BAEE8),
    accentDim = Color(0xFF8BAEE8).copy(alpha = 0.3f),
    onSurface = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFFE8EEF8).copy(alpha = 0.7f),
    progressTrack = Color(0xFFE8EEF8).copy(alpha = 0.1f),
    acColor = harmonizeAcColor(accent = Color(0xFF8BAEE8), isDark = true),
    dcColor = harmonizeDcColor(accent = Color(0xFF8BAEE8), isDark = true)
)

// Car-specific palettes
object CarColorPalettes {

    // White car - warm grey (same as old black, works well with white car)
    private val whiteLightPalette = CarColorPalette(
        surface = Color(0xFFF5F3F0),
        accent = Color(0xFF8B7355),
        accentDim = Color(0xFF8B7355).copy(alpha = 0.3f),
        onSurface = Color(0xFF2A2520),
        onSurfaceVariant = Color(0xFF2A2520).copy(alpha = 0.7f),
        progressTrack = Color(0xFF2A2520).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF8B7355), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF8B7355), isDark = false)
    )

    private val whiteDarkPalette = CarColorPalette(
        surface = Color(0xFF1E2530),
        accent = Color(0xFF8BAEE8),
        accentDim = Color(0xFF8BAEE8).copy(alpha = 0.3f),
        onSurface = Color(0xFFE8EEF8),
        onSurfaceVariant = Color(0xFFE8EEF8).copy(alpha = 0.7f),
        progressTrack = Color(0xFFE8EEF8).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF8BAEE8), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFF8BAEE8), isDark = true)
    )

    // Black car - darker grey (darker than midnight silver)
    private val blackLightPalette = CarColorPalette(
        surface = Color(0xFFD8DADC),
        accent = Color(0xFF505458),
        accentDim = Color(0xFF505458).copy(alpha = 0.3f),
        onSurface = Color(0xFF1E2022),
        onSurfaceVariant = Color(0xFF1E2022).copy(alpha = 0.7f),
        progressTrack = Color(0xFF1E2022).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF505458), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF505458), isDark = false)
    )

    private val blackDarkPalette = CarColorPalette(
        surface = Color(0xFF2A2520),
        accent = Color(0xFFC9A66B),
        accentDim = Color(0xFFC9A66B).copy(alpha = 0.3f),
        onSurface = Color(0xFFF5F3F0),
        onSurfaceVariant = Color(0xFFF5F3F0).copy(alpha = 0.7f),
        progressTrack = Color(0xFFF5F3F0).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFC9A66B), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFFC9A66B), isDark = true)
    )

    // Midnight Silver - cool grey
    private val midnightSilverLightPalette = CarColorPalette(
        surface = Color(0xFFECEEF0),
        accent = Color(0xFF6B7A8C),
        accentDim = Color(0xFF6B7A8C).copy(alpha = 0.3f),
        onSurface = Color(0xFF22262B),
        onSurfaceVariant = Color(0xFF22262B).copy(alpha = 0.7f),
        progressTrack = Color(0xFF22262B).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF6B7A8C), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF6B7A8C), isDark = false)
    )

    private val midnightSilverDarkPalette = CarColorPalette(
        surface = Color(0xFF22262B),
        accent = Color(0xFF8FA4B8),
        accentDim = Color(0xFF8FA4B8).copy(alpha = 0.3f),
        onSurface = Color(0xFFECEEF0),
        onSurfaceVariant = Color(0xFFECEEF0).copy(alpha = 0.7f),
        progressTrack = Color(0xFFECEEF0).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF8FA4B8), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFF8FA4B8), isDark = true)
    )

    // Deep Blue
    private val deepBlueLightPalette = CarColorPalette(
        surface = Color(0xFFE5EBF5),
        accent = Color(0xFF3B5998),
        accentDim = Color(0xFF3B5998).copy(alpha = 0.3f),
        onSurface = Color(0xFF1A2235),
        onSurfaceVariant = Color(0xFF1A2235).copy(alpha = 0.7f),
        progressTrack = Color(0xFF1A2235).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF3B5998), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF3B5998), isDark = false)
    )

    private val deepBlueDarkPalette = CarColorPalette(
        surface = Color(0xFF1A2235),
        accent = Color(0xFF6B8BC3),
        accentDim = Color(0xFF6B8BC3).copy(alpha = 0.3f),
        onSurface = Color(0xFFE5EBF5),
        onSurfaceVariant = Color(0xFFE5EBF5).copy(alpha = 0.7f),
        progressTrack = Color(0xFFE5EBF5).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF6B8BC3), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFF6B8BC3), isDark = true)
    )

    // Red Multi-Coat
    private val redLightPalette = CarColorPalette(
        surface = Color(0xFFF8E8E8),
        accent = Color(0xFFC45050),
        accentDim = Color(0xFFC45050).copy(alpha = 0.3f),
        onSurface = Color(0xFF2E1A1A),
        onSurfaceVariant = Color(0xFF2E1A1A).copy(alpha = 0.7f),
        progressTrack = Color(0xFF2E1A1A).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFC45050), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFFC45050), isDark = false)
    )

    private val redDarkPalette = CarColorPalette(
        surface = Color(0xFF2E1A1A),
        accent = Color(0xFFE07070),
        accentDim = Color(0xFFE07070).copy(alpha = 0.3f),
        onSurface = Color(0xFFF8E8E8),
        onSurfaceVariant = Color(0xFFF8E8E8).copy(alpha = 0.7f),
        progressTrack = Color(0xFFF8E8E8).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFE07070), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFFE07070), isDark = true)
    )

    // Quicksilver - warm silver
    private val quicksilverLightPalette = CarColorPalette(
        surface = Color(0xFFF0EDE8),
        accent = Color(0xFFA09080),
        accentDim = Color(0xFFA09080).copy(alpha = 0.3f),
        onSurface = Color(0xFF252320),
        onSurfaceVariant = Color(0xFF252320).copy(alpha = 0.7f),
        progressTrack = Color(0xFF252320).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFA09080), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFFA09080), isDark = false)
    )

    private val quicksilverDarkPalette = CarColorPalette(
        surface = Color(0xFF252320),
        accent = Color(0xFFB0A090),
        accentDim = Color(0xFFB0A090).copy(alpha = 0.3f),
        onSurface = Color(0xFFF0EDE8),
        onSurfaceVariant = Color(0xFFF0EDE8).copy(alpha = 0.7f),
        progressTrack = Color(0xFFF0EDE8).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFB0A090), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFFB0A090), isDark = true)
    )

    // Stealth Grey - cool grey
    private val stealthGreyLightPalette = CarColorPalette(
        surface = Color(0xFFECEDEE),
        accent = Color(0xFF606570),
        accentDim = Color(0xFF606570).copy(alpha = 0.3f),
        onSurface = Color(0xFF1E2022),
        onSurfaceVariant = Color(0xFF1E2022).copy(alpha = 0.7f),
        progressTrack = Color(0xFF1E2022).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF606570), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF606570), isDark = false)
    )

    private val stealthGreyDarkPalette = CarColorPalette(
        surface = Color(0xFF1E2022),
        accent = Color(0xFF909598),
        accentDim = Color(0xFF909598).copy(alpha = 0.3f),
        onSurface = Color(0xFFECEDEE),
        onSurfaceVariant = Color(0xFFECEDEE).copy(alpha = 0.7f),
        progressTrack = Color(0xFFECEDEE).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF909598), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFF909598), isDark = true)
    )

    // Ultra Red - vibrant
    private val ultraRedLightPalette = CarColorPalette(
        surface = Color(0xFFFAEBEB),
        accent = Color(0xFFE03030),
        accentDim = Color(0xFFE03030).copy(alpha = 0.3f),
        onSurface = Color(0xFF301818),
        onSurfaceVariant = Color(0xFF301818).copy(alpha = 0.7f),
        progressTrack = Color(0xFF301818).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFE03030), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFFE03030), isDark = false)
    )

    private val ultraRedDarkPalette = CarColorPalette(
        surface = Color(0xFF301818),
        accent = Color(0xFFFF5050),
        accentDim = Color(0xFFFF5050).copy(alpha = 0.3f),
        onSurface = Color(0xFFFAEBEB),
        onSurfaceVariant = Color(0xFFFAEBEB).copy(alpha = 0.7f),
        progressTrack = Color(0xFFFAEBEB).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFFF5050), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFFFF5050), isDark = true)
    )

    // Midnight Cherry Red - deep sophisticated red
    private val midnightCherryLightPalette = CarColorPalette(
        surface = Color(0xFFF5E5E8),
        accent = Color(0xFF8B3040),
        accentDim = Color(0xFF8B3040).copy(alpha = 0.3f),
        onSurface = Color(0xFF251518),
        onSurfaceVariant = Color(0xFF251518).copy(alpha = 0.7f),
        progressTrack = Color(0xFF251518).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFF8B3040), isDark = false),
        dcColor = harmonizeDcColor(accent = Color(0xFF8B3040), isDark = false)
    )

    private val midnightCherryDarkPalette = CarColorPalette(
        surface = Color(0xFF251518),
        accent = Color(0xFFC05068),
        accentDim = Color(0xFFC05068).copy(alpha = 0.3f),
        onSurface = Color(0xFFF5E5E8),
        onSurfaceVariant = Color(0xFFF5E5E8).copy(alpha = 0.7f),
        progressTrack = Color(0xFFF5E5E8).copy(alpha = 0.1f),
        acColor = harmonizeAcColor(accent = Color(0xFFC05068), isDark = true),
        dcColor = harmonizeDcColor(accent = Color(0xFFC05068), isDark = true)
    )

    fun forExteriorColor(exteriorColor: String?, darkTheme: Boolean): CarColorPalette {
        val colorKey = exteriorColor?.lowercase()?.replace(" ", "") ?: ""

        return when {
            colorKey.contains("white") || colorKey == "ppsw" ->
                if (darkTheme) whiteDarkPalette else whiteLightPalette

            colorKey.contains("black") || colorKey == "pbsb" || colorKey == "pmbl" ->
                if (darkTheme) blackDarkPalette else blackLightPalette

            colorKey.contains("midnightsilver") || colorKey.contains("steelgrey") || colorKey == "pmng" ->
                if (darkTheme) midnightSilverDarkPalette else midnightSilverLightPalette

            colorKey.contains("silver") || colorKey == "pmss" ->
                if (darkTheme) midnightSilverDarkPalette else midnightSilverLightPalette

            colorKey.contains("deepblue") || colorKey == "ppsb" ->
                if (darkTheme) deepBlueDarkPalette else deepBlueLightPalette

            colorKey.contains("quicksilver") || colorKey == "pn00" ->
                if (darkTheme) quicksilverDarkPalette else quicksilverLightPalette

            colorKey.contains("stealthgrey") || colorKey.contains("stealth") || colorKey == "pn01" ->
                if (darkTheme) stealthGreyDarkPalette else stealthGreyLightPalette

            colorKey.contains("midnightcherry") || colorKey == "pr00" ->
                if (darkTheme) midnightCherryDarkPalette else midnightCherryLightPalette

            colorKey.contains("ultrared") || colorKey == "pr01" ->
                if (darkTheme) ultraRedDarkPalette else ultraRedDarkPalette

            colorKey.contains("red") || colorKey == "ppmr" ->
                if (darkTheme) redDarkPalette else redLightPalette

            else -> if (darkTheme) DefaultDarkPalette else DefaultLightPalette
        }
    }
}

val LocalCarColorPalette = staticCompositionLocalOf { DefaultLightPalette }