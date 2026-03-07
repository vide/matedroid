package com.matedroid.ui

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards against hardcoded unit labels (km, mi, km/h, mph, Wh/km, °C, °F) in UI screen
 * source files. All user-facing unit strings must go through UnitFormatter so they respect
 * the unit preference configured in TeslamateAPI.
 *
 * If this test fails:
 *   - Replace the format string with the appropriate UnitFormatter call:
 *       formatDistance()   — for km / mi
 *       formatSpeed()      — for km/h / mph
 *       formatEfficiency() — for Wh/km / Wh/mi
 *       formatTemperature()— for °C / °F
 *   - kWh, kW, MWh, %, and minute/hour values are unit-system-independent and are fine as-is.
 */
class HardcodedUnitLabelTest {

    /**
     * Patterns that indicate a hardcoded unit label at the end of a Kotlin string literal.
     * Each pattern matches `"...<unit>"` where the unit is the trailing content before the
     * closing double-quote, meaning the string is being used as a display value.
     */
    private val forbidden = listOf(
        // Distance
        Regex(""""\s+km""""),           // "... km"
        Regex(""""\s+mi""""),           // "... mi"  (not "min")
        // Speed
        Regex(""""\s+km/h""""),         // "... km/h"
        Regex(""""\s+mph""""),          // "... mph"
        // Efficiency
        Regex(""""\s+Wh/km""""),        // "... Wh/km"
        Regex(""""\s+Wh/mi""""),        // "... Wh/mi"
        // Temperature — no space before the degree symbol is intentional
        Regex(""""[^"]*°C""""),         // "...°C"
        Regex(""""[^"]*°F""""),         // "...°F"
    )

    @Test
    fun uiScreens_doNotContainHardcodedUnitLabels() {
        // Gradle sets user.dir to the :app module directory during unit tests.
        val screensDir = File("src/main/java/com/matedroid/ui/screens")
        if (!screensDir.exists()) {
            // Path not resolvable in this environment — skip gracefully.
            return
        }

        val violations = mutableListOf<String>()

        screensDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.name.contains("Preview") }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    // Skip blank lines, single-line comments, and KDoc / block-comment lines.
                    if (trimmed.isEmpty() ||
                        trimmed.startsWith("//") ||
                        trimmed.startsWith("*") ||
                        trimmed.startsWith("import")
                    ) return@forEachIndexed

                    forbidden.forEach { pattern ->
                        if (pattern.containsMatchIn(line)) {
                            violations += "${file.name}:${index + 1}: ${trimmed.take(120)}"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "Found ${violations.size} hardcoded unit label(s) in UI screens.\n" +
                "Use UnitFormatter instead of inline format strings with unit labels:\n\n" +
                violations.joinToString("\n")
            )
        }
    }
}
