package com.matedroid.domain.model

/**
 * Represents a car model variant for the image picker.
 *
 * @param id The internal variant ID (e.g., "my", "myj", "myjs", "myjp")
 */
data class CarVariant(
    val id: String
)

/**
 * Represents a wheel option for the image picker.
 *
 * @param code The wheel code (e.g., "WY18P", "WY19P")
 * @param displayName The human-readable wheel name
 * @param assetPath The full path to the asset for preview
 */
data class WheelOption(
    val code: String,
    val displayName: String,
    val assetPath: String
)

/**
 * Resolves Tesla car image assets based on vehicle configuration.
 *
 * Maps TeslamateAPI values (e.g., "MidnightSilver", "Pinwheel18CapKit")
 * to Tesla compositor codes (e.g., "PMNG", "W38B") to construct asset paths.
 *
 * All images are transparent PNGs (using bkba_opt=1 in compositor URLs).
 *
 * Supports both legacy models and new Highland/Juniper models:
 * - Legacy Model 3 (pre-2024): m3_{color}_{wheel}.png
 * - Highland Model 3 (2024+): m3h_{color}_{wheel}.png or m3hp_{color}_{wheel}.png
 * - Legacy Model Y (pre-2025): my_{color}_{wheel}.png
 * - Juniper Model Y Standard (2025+): myjs_{color}_{wheel}.png (classic headlights)
 * - Juniper Model Y Premium (2025+): myj_{color}_{wheel}.png (light strip headlights)
 * - Juniper Model Y Performance (2025+): myjp_{color}_{wheel}.png (light strip + red calipers)
 *
 * Juniper Model Y trim detection via trim_badging:
 * - "50" = Standard Range (18" Photon wheels)
 * - "74", "74D" = Long Range/Premium (19" Crossflow wheels)
 * - "P74D" = Performance (21" Überturbine wheels, red calipers)
 */
object CarImageResolver {

    // Color code mappings (TeslamateAPI -> Compositor)
    // Keys are normalized (lowercase, no spaces)
    private val COLOR_CODES = mapOf(
        // Legacy colors (available on old models)
        "black" to "PBSB",
        "solidblack" to "PBSB",
        "obsidianblack" to "PMBL",
        "midnightsilver" to "PMNG",
        "midnightsilvermetallic" to "PMNG",
        "steelgrey" to "PMNG",  // Older Model S/X name for similar grey
        "silver" to "PMSS",
        "silvermetallic" to "PMSS",
        "white" to "PPSW",
        "pearlwhite" to "PPSW",
        "pearlwhitemulticoat" to "PPSW",
        "deepblue" to "PPSB",  // Legacy deep blue
        "deepbluemetallic" to "PPSB",
        "blue" to "PPSB",
        "red" to "PPMR",
        "redmulticoat" to "PPMR",
        // New Highland/Juniper colors
        "quicksilver" to "PN00",
        "stealthgrey" to "PN01",
        "stealthgray" to "PN01",
        "midnightcherryred" to "PR00",
        "ultrared" to "PR01",
        // Juniper/Highland "Diamond Black". TeslamateAPI reports it as "DiamondBlack";
        // "blackdiamond" is kept as a defensive alias for the reversed spelling.
        "diamondblack" to "PX02",
        "blackdiamond" to "PX02",
        // Juniper Model Y blues (distinct from Legacy PPSB Deep Blue Metallic)
        "glacierblue" to "PB01",
        "marineblue" to "PB02"
    )

    // Colors only available on Highland/Juniper (not on Legacy)
    private val HIGHLAND_JUNIPER_COLORS = setOf("PN00", "PN01", "PR01", "PX02", "PB01", "PB02")

    // Colors only available on Legacy models (not on Highland/Juniper)
    private val LEGACY_ONLY_COLORS = setOf("PMNG", "PMSS", "PPMR", "PMBL")

    // Colors that are Legacy-only for Model Y but shared for Model 3
    // MY Juniper uses PX02 (Diamond Black) instead of PBSB, and PB01/PB02 instead of PPSB
    private val MY_LEGACY_ONLY_EXTRA = setOf("PBSB", "PPSB")

    // Colors available on Juniper Premium+Performance but NOT Standard
    // Juniper Standard only comes in: PPSW, PN01, PX02
    private val JUNIPER_PREMIUM_ONLY_COLORS = setOf("PN00", "PR01", "PB01")

    // Colors available only on Juniper Performance
    private val JUNIPER_PERF_ONLY_COLORS = setOf("PB02")

    // Wheel types only available on Highland Model 3 (normalized, lowercase)
    // Note: "helix19" from TeslamateAPI is actually Nova 19" wheel
    private val HIGHLAND_M3_WHEEL_TYPES = setOf("photon18", "glider18", "nova18", "nova19", "helix19", "w38a")

    // Wheel types only available on Juniper Model Y (normalized, lowercase)
    private val JUNIPER_MY_WHEEL_TYPES = setOf("photon18", "wy18p", "crossflow19", "wy19p", "helix20", "wy20a")

    // Legacy Model 3 valid colors
    private val LEGACY_M3_COLORS = setOf("PBSB", "PMNG", "PMSS", "PPSW", "PPSB", "PPMR", "PMBL")

    // Highland Model 3 valid colors
    private val HIGHLAND_M3_COLORS = setOf("PBSB", "PPSW", "PPSB", "PN00", "PN01", "PR01", "PX02")

    // Legacy Model Y valid colors
    private val LEGACY_MY_COLORS = setOf("PBSB", "PMNG", "PPSW", "PPSB", "PPMR")

    // Juniper Model Y Standard valid colors (limited to 3 colors with 18" Photon)
    private val JUNIPER_MY_STANDARD_COLORS = setOf("PPSW", "PN01", "PX02")

    // Juniper Model Y Premium valid colors (PN00/PR01/PB01 only with Premium MTY60 19"/20" wheels)
    private val JUNIPER_MY_COLORS = setOf("PPSW", "PN01", "PX02", "PN00", "PR01", "PB01")

    // Juniper Model Y Performance valid colors (all 7 colors including PB02 Marine Blue)
    private val JUNIPER_MY_PERF_COLORS = setOf("PPSW", "PN01", "PX02", "PB02", "PN00", "PR01", "PB01")

    // Wheel code mappings per model variant
    // Keys are patterns that match the start of the TeslamateAPI wheel type

    // Legacy Model 3 wheels
    private val WHEEL_PATTERNS_M3 = listOf(
        "pinwheel18" to "W38B",
        "aero18" to "W38B",
        "aeroturbine19" to "W39B",
        "stiletto19" to "W39B",
        "sport19" to "W39B",
        "uberturbine20" to "W32D",
        "performance20" to "W32P",
        "19" to "W39B",
        "20" to "W32P",
        "18" to "W38B"
    )

    // Highland Model 3 wheels
    // Note: Nova 19" (reported as "Helix19" by TeslamateAPI) not in compositor yet
    private val WHEEL_PATTERNS_M3H = listOf(
        "nova19" to "W38A",    // 19" Nova - compositor fallback to W38A
        "helix19" to "W38A",   // TeslamateAPI name for Nova 19" - fallback to W38A
        "photon18" to "W38A",
        "glider18" to "W38A",
        "nova18" to "W38A",
        "19" to "W38A",        // Default 19" to W38A (Nova fallback)
        "18" to "W38A"
    )

    // Highland Model 3 Performance wheels
    private val WHEEL_PATTERNS_M3HP = listOf(
        "performance20" to "W30P",
        "20" to "W30P"
    )

    // Legacy Model Y wheels
    // Note: Induction 20" (WY0S) and Überturbine 21" (WY1S) mapped to Performance 20" (WY20P)
    // as we don't have separate assets for those wheel styles
    private val WHEEL_PATTERNS_MY = listOf(
        "gemini19" to "WY19B",
        "pinwheel18" to "WY18B",
        "aero18" to "WY18B",
        "aeroturbine19" to "WY19B",
        "stiletto19" to "WY19B",
        "sport19" to "WY19B",
        "apollo19" to "WY19B",      // Fallback to Gemini 19"
        "induction20" to "WY20P",   // Fallback to Performance 20"
        "performance20" to "WY20P",
        "uberturbine21" to "WY20P", // Fallback to Performance 20"
        "21" to "WY20P",
        "20" to "WY20P",
        "19" to "WY19B",
        "18" to "WY18B"
    )

    // Juniper Model Y Premium wheels (19" Crossflow standard, 20" Helix optional)
    private val WHEEL_PATTERNS_MYJ = listOf(
        "helix20" to "WY20A",
        "crossflow19" to "WY19P",
        "20" to "WY20A",
        "19" to "WY19P"
    )

    // Juniper Model Y Standard wheels (18" Photon standard, 19" Crossflow in some markets)
    private val WHEEL_PATTERNS_MYJS = listOf(
        "photon18" to "WY18P",
        "crossflow19" to "WY19P",
        "19" to "WY19P",
        "18" to "WY18P"
    )

    // Juniper Model Y Performance wheels
    private val WHEEL_PATTERNS_MYJP = listOf(
        "uberturbine21" to "WY21A",
        "arachnid21" to "WY21A",   // Alternative 21" Performance wheel
        "21" to "WY21A"
    )

    // Model S wheels
    private val WHEEL_PATTERNS_MS = listOf(
        "tempest19" to "WT19",
        "19" to "WT19"
    )

    // Model X wheels
    private val WHEEL_PATTERNS_MX = listOf(
        "turbine22" to "WT22",     // 22" Turbine wheels
        "cyberstream20" to "WT20",
        "slipstream20" to "WT20",  // Older Model X wheel option
        "22" to "WT22",
        "20" to "WT20"
    )

    // Default wheels per model variant
    private val DEFAULT_WHEELS = mapOf(
        "m3" to "W38B",
        "m3h" to "W38A",
        "m3hp" to "W30P",
        "my" to "WY19B",
        "myjs" to "WY18P",
        "myj" to "WY19P",
        "myjp" to "WY21A",
        "ms" to "WT19",
        "mx" to "WT20"
    )

    // Default colors per model variant
    private val DEFAULT_COLORS = mapOf(
        "m3" to "PPSW",
        "m3h" to "PPSW",
        "m3hp" to "PPSW",
        "my" to "PPSW",
        "myjs" to "PPSW",
        "myj" to "PPSW",
        "myjp" to "PPSW",
        "ms" to "PPSW",
        "mx" to "PPSW"
    )

    // Model S/X valid colors (same as legacy Model Y)
    private val MODEL_SX_COLORS = setOf("PBSB", "PMNG", "PPSW", "PPSB", "PPMR")

    /**
     * Get the asset path for a car image based on its configuration.
     *
     * @param model The car model from TeslamateAPI (e.g., "3", "Y")
     * @param exteriorColor The exterior color from TeslamateAPI (e.g., "MidnightSilver")
     * @param wheelType The wheel type from TeslamateAPI (e.g., "Pinwheel18CapKit")
     * @param trimBadging The trim badging from TeslamateAPI (e.g., "74D", "P74D") - helps detect Performance
     * @return The asset path (e.g., "car_images/m3_PMNG_W38B.png")
     */
    fun getAssetPath(
        model: String?,
        exteriorColor: String?,
        wheelType: String?,
        trimBadging: String? = null
    ): String {
        val colorCode = mapColor(exteriorColor)
        val modelVariant = determineModelVariant(model, colorCode, wheelType, trimBadging)
        val resolvedColorCode = colorCode ?: DEFAULT_COLORS[modelVariant] ?: "PPSW"
        val wheelCode = mapWheel(modelVariant, wheelType) ?: DEFAULT_WHEELS[modelVariant] ?: "W38B"

        // Validate the color is available for this model variant, fallback if not
        val validatedColorCode = validateColorForVariant(modelVariant, resolvedColorCode)

        return "car_images/${modelVariant}_${validatedColorCode}_${wheelCode}.png"
    }

    /**
     * Get the scale factor for a model variant to ensure consistent display size.
     * Some models (Highland, Juniper, Model X) are rendered smaller by Tesla's compositor.
     */
    fun getScaleFactor(
        model: String?,
        exteriorColor: String?,
        wheelType: String?,
        trimBadging: String? = null
    ): Float {
        val colorCode = mapColor(exteriorColor)
        val modelVariant = determineModelVariant(model, colorCode, wheelType, trimBadging)
        return getScaleFactorForVariant(modelVariant)
    }

    /**
     * Get the scale factor for a specific model variant.
     */
    fun getScaleFactorForVariant(modelVariant: String): Float {
        return when (modelVariant) {
            "m3h", "m3hp" -> 1.35f  // Highland Model 3 renders smaller
            "myjs", "myj", "myjp" -> 1.25f  // Juniper Model Y renders smaller
            "mx" -> 1.4f            // Model X renders smaller
            else -> 1.0f            // Legacy models render at full size
        }
    }

    /**
     * Get the default asset path for a model when configuration is unavailable.
     */
    fun getDefaultAssetPath(model: String?): String {
        val modelVariant = when (model?.uppercase()) {
            "3" -> "m3"
            "Y" -> "my"
            "S" -> "ms"
            "X" -> "mx"
            else -> "m3"
        }
        val defaultColor = DEFAULT_COLORS[modelVariant] ?: "PPSW"
        val defaultWheel = DEFAULT_WHEELS[modelVariant] ?: "W38B"
        return "car_images/${modelVariant}_${defaultColor}_${defaultWheel}.png"
    }

    /**
     * Check if a specific asset exists (for fallback logic).
     * This should be called with actual asset checking from the AssetManager.
     */
    fun getFallbackAssetPath(
        model: String?,
        exteriorColor: String?,
        wheelType: String?,
        trimBadging: String? = null,
        assetExists: (String) -> Boolean
    ): String {
        // Try exact match first
        val exactPath = getAssetPath(model, exteriorColor, wheelType, trimBadging)
        if (assetExists(exactPath)) return exactPath

        // Try with default wheel
        val colorCode = mapColor(exteriorColor)
        val modelVariant = determineModelVariant(model, colorCode, wheelType, trimBadging)
        val validatedColor = validateColorForVariant(modelVariant, colorCode ?: DEFAULT_COLORS[modelVariant] ?: "PPSW")

        val defaultWheelPath = "car_images/${modelVariant}_${validatedColor}_${DEFAULT_WHEELS[modelVariant]}.png"
        if (assetExists(defaultWheelPath)) return defaultWheelPath

        // Try with default color
        val wheelCode = mapWheel(modelVariant, wheelType) ?: DEFAULT_WHEELS[modelVariant] ?: "W38B"
        val defaultColorPath = "car_images/${modelVariant}_${DEFAULT_COLORS[modelVariant]}_${wheelCode}.png"
        if (assetExists(defaultColorPath)) return defaultColorPath

        // Fall back to complete default
        return getDefaultAssetPath(model)
    }

    /**
     * Determine which model variant to use based on available data.
     *
     * Highland/Juniper detection heuristics:
     * 1. If color is a new Highland/Juniper-only color (PN00, PN01, PR01, PX02, PB01, PB02), use Highland/Juniper
     * 2. If wheel type is a Highland/Juniper-only wheel (Photon18, Glider18, etc.), use Highland/Juniper
     * 3. Otherwise, assume legacy
     *
     * Trim badging for Juniper Model Y:
     * - "50" = Standard Range (classic headlights, 18" Photon wheels)
     * - "74", "74D" = Long Range/Premium (light strip headlights, 19" Crossflow wheels)
     * - "P74D" = Performance (light strip headlights, 21" Überturbine wheels, red calipers)
     */
    private fun determineModelVariant(
        model: String?,
        colorCode: String?,
        wheelType: String?,
        trimBadging: String?
    ): String {
        val baseModel = model?.uppercase() ?: "3"
        val isHighlandJuniperColor = colorCode in HIGHLAND_JUNIPER_COLORS

        // Normalize wheel type for checking
        val normalizedWheel = wheelType?.lowercase()?.replace(" ", "")?.replace("-", "")?.replace("_", "")

        // Check if wheel type indicates Highland/Juniper
        val isHighlandM3Wheel = normalizedWheel != null && HIGHLAND_M3_WHEEL_TYPES.any { normalizedWheel.startsWith(it) }
        val isJuniperMYWheel = normalizedWheel != null && JUNIPER_MY_WHEEL_TYPES.any { normalizedWheel.startsWith(it) }

        // Check for Performance trim (P prefix like "P74D")
        val isPerformance = trimBadging?.uppercase()?.startsWith("P") == true ||
                trimBadging?.lowercase()?.contains("performance") == true

        // Check for 21" Überturbine/Arachnid wheels (Performance-only on Juniper)
        val isJuniperPerfWheel = normalizedWheel?.startsWith("uberturbine21") == true ||
                normalizedWheel?.startsWith("arachnid21") == true ||
                normalizedWheel?.startsWith("21") == true

        // Check for Juniper Standard indicators
        val isJuniperStandard = trimBadging?.uppercase() == "50"
        val isPhoton18Wheel = normalizedWheel?.startsWith("photon18") == true

        return when (baseModel) {
            "3" -> when {
                (isHighlandJuniperColor || isHighlandM3Wheel) && isPerformance -> "m3hp"
                isHighlandJuniperColor || isHighlandM3Wheel -> "m3h"
                else -> "m3"
            }
            "Y" -> when {
                // Juniper Performance: P-trim or 21" wheels with Juniper indicators
                (isHighlandJuniperColor || isJuniperMYWheel || isJuniperPerfWheel) && (isPerformance || isJuniperPerfWheel) -> "myjp"
                // Juniper Standard: trim "50" or Photon18 wheel (Standard-exclusive)
                (isHighlandJuniperColor || isJuniperMYWheel) && (isJuniperStandard || isPhoton18Wheel) -> "myjs"
                // Juniper Premium: other Juniper indicators without Standard/Performance markers
                isHighlandJuniperColor || isJuniperMYWheel -> "myj"
                else -> "my"
            }
            "S" -> "ms"
            "X" -> "mx"
            else -> "m3"
        }
    }

    /**
     * Validate that a color is available for the model variant.
     * Returns the color if valid, or a fallback color if not.
     */
    fun validateColorForVariant(modelVariant: String, colorCode: String): String {
        val validColors = when (modelVariant) {
            "m3" -> LEGACY_M3_COLORS
            "m3h", "m3hp" -> HIGHLAND_M3_COLORS
            "my" -> LEGACY_MY_COLORS
            "myjs" -> JUNIPER_MY_STANDARD_COLORS
            "myj" -> JUNIPER_MY_COLORS
            "myjp" -> JUNIPER_MY_PERF_COLORS
            "ms", "mx" -> MODEL_SX_COLORS
            else -> LEGACY_M3_COLORS
        }

        return if (colorCode in validColors) {
            colorCode
        } else {
            DEFAULT_COLORS[modelVariant] ?: "PPSW"
        }
    }

    /**
     * Map an exterior color name to its compositor code.
     *
     * @param color The color name from TeslamateAPI (e.g., "MidnightSilver")
     * @return The compositor code (e.g., "PMNG") or null if not found
     */
    fun mapColor(color: String?): String? {
        if (color == null) return null
        val normalized = color.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        return COLOR_CODES[normalized]
    }

    private fun mapWheel(modelVariant: String, wheelType: String?): String? {
        if (wheelType == null) return null

        val normalized = wheelType.lowercase().replace(" ", "").replace("-", "").replace("_", "")

        val patterns = when (modelVariant) {
            "m3" -> WHEEL_PATTERNS_M3
            "m3h" -> WHEEL_PATTERNS_M3H
            "m3hp" -> WHEEL_PATTERNS_M3HP
            "my" -> WHEEL_PATTERNS_MY
            "myj" -> WHEEL_PATTERNS_MYJ
            "myjs" -> WHEEL_PATTERNS_MYJS
            "myjp" -> WHEEL_PATTERNS_MYJP
            "ms" -> WHEEL_PATTERNS_MS
            "mx" -> WHEEL_PATTERNS_MX
            else -> WHEEL_PATTERNS_M3
        }

        // Find the first pattern that matches the start of the wheel type
        for ((pattern, code) in patterns) {
            if (normalized.startsWith(pattern)) {
                return code
            }
        }

        return null
    }

    // ==================== Image Picker Support ====================

    /**
     * Data class representing the detected default selection for the image picker.
     *
     * @param variant The model variant (e.g., "my", "myj")
     * @param wheelCode The wheel code (e.g., "WY18P", "WY19P")
     */
    data class DetectedDefault(
        val variant: String,
        val wheelCode: String
    )

    /**
     * Detect the default variant and wheel based on API data.
     * Used by the image picker to show the auto-detected default.
     *
     * @param model The car model from TeslamateAPI (e.g., "3", "Y")
     * @param exteriorColor The exterior color from TeslamateAPI (e.g., "MidnightSilver")
     * @param wheelType The wheel type from TeslamateAPI (e.g., "Photon18")
     * @param trimBadging The trim badging from TeslamateAPI (e.g., "74D", "P74D")
     * @return The detected default variant and wheel code
     */
    fun getDetectedDefault(
        model: String?,
        exteriorColor: String?,
        wheelType: String?,
        trimBadging: String?
    ): DetectedDefault {
        val colorCode = mapColor(exteriorColor)
        val variant = determineModelVariant(model, colorCode, wheelType, trimBadging)
        val wheelCode = mapWheel(variant, wheelType) ?: DEFAULT_WHEELS[variant] ?: "W38B"
        return DetectedDefault(variant, wheelCode)
    }

    // Wheel display names (not localized - technical names)
    private val WHEEL_DISPLAY_NAMES = mapOf(
        // Legacy Model 3
        "W38B" to "18\" Aero",
        "W39B" to "19\" Sport",
        "W32P" to "20\" Performance",
        "W32D" to "20\" Überturbine",
        // Highland Model 3
        "W38A" to "18\" Photon",
        "W30P" to "20\" Performance",
        // Legacy Model Y
        "WY18B" to "18\" Aero",
        "WY19B" to "19\" Gemini",
        "WY9S" to "19\" Apollo",
        "WY0S" to "20\" Induction",
        "WY20P" to "20\" Performance",
        "WY1S" to "21\" Überturbine",
        // Juniper Model Y Standard
        "WY18P" to "18\" Photon",
        // Juniper Model Y Premium
        "WY19P" to "19\" Crossflow",
        "WY20A" to "20\" Helix",
        // Juniper Model Y Performance
        "WY21A" to "21\" Überturbine",
        // Model S
        "WT19" to "19\" Tempest",
        // Model X
        "WT20" to "20\" Cyberstream",
        "WT22" to "22\" Turbine"
    )

    // Available wheel codes per variant
    private val VARIANT_WHEELS = mapOf(
        "m3" to listOf("W38B", "W39B", "W32P", "W32D"),
        "m3h" to listOf("W38A"),
        "m3hp" to listOf("W30P"),
        "my" to listOf("WY18B", "WY19B", "WY20P"),
        "myjs" to listOf("WY18P", "WY19P"),
        "myj" to listOf("WY19P", "WY20A"),
        "myjp" to listOf("WY21A"),
        "ms" to listOf("WT19"),
        "mx" to listOf("WT20", "WT22")
    )

    /**
     * Get available variants for a model, optionally filtered by exterior color and wheel type.
     *
     * Filtering rules:
     * - Performance variants only shown when there's cross-generation ambiguity
     *   (shared colors between Legacy and new gen). When the generation is known,
     *   trim_badging reliably auto-detects Performance.
     * - For Model Y: PBSB and PPSB are Legacy-only (Juniper uses PX02 and PB01/PB02)
     * - Performance-only colors (PB02) auto-select myjp with no alternatives
     * - Premium-only colors (PN00, PR01, PB01) show only Premium
     * - Juniper-only colors (PN01, PX02) show Standard + Premium
     * - Shared colors (PPSW) show all variants including Performance
     * - Performance is excluded when trim or wheel data rules it out
     *
     * @param model The car model from TeslamateAPI (e.g., "3", "Y")
     * @param colorCode The mapped color code (e.g., "PMNG", "PN00") - optional
     * @param trimBadging The trim badging from TeslamateAPI - used to exclude Performance
     * @param wheelType The wheel type from TeslamateAPI - used to exclude Performance
     * @return List of available variants for the picker
     */
    fun getVariantsForModel(
        model: String?,
        colorCode: String? = null,
        trimBadging: String? = null,
        wheelType: String? = null
    ): List<CarVariant> {
        val isNewOnlyColor = colorCode in HIGHLAND_JUNIPER_COLORS
        val isLegacyOnlyColor = colorCode in LEGACY_ONLY_COLORS
        val isPremiumOnlyColor = colorCode in JUNIPER_PREMIUM_ONLY_COLORS
        val isPerfOnlyColor = colorCode in JUNIPER_PERF_ONLY_COLORS

        // Determine if Performance can be ruled out from trim/wheel data
        val isPerformanceTrim = trimBadging?.uppercase()?.startsWith("P") == true
        val normalizedWheel = wheelType?.lowercase()?.replace(" ", "")?.replace("-", "")?.replace("_", "")
        val isPerformanceWheel = normalizedWheel?.let {
            it.startsWith("performance20") || it.startsWith("uberturbine21") ||
            it.startsWith("arachnid21") || it.startsWith("21") || it.startsWith("20")
        } ?: false
        // Show Performance only if: trim/wheel indicate it, or both are unknown (can't rule it out)
        val showPerformance = isPerformanceTrim || isPerformanceWheel ||
            (trimBadging == null && wheelType == null)

        val myLegacy = CarVariant("my")
        val myStandard = CarVariant("myjs")
        val myPremium = CarVariant("myj")
        val myPerformance = CarVariant("myjp")

        val m3Legacy = CarVariant("m3")
        val m3Highland = CarVariant("m3h")
        val m3HighlandPerf = CarVariant("m3hp")

        return when (model?.uppercase()) {
            "Y" -> {
                // MY-specific: PBSB (Solid Black) and PPSB (Deep Blue) are Legacy-only
                val isMyLegacyOnly = colorCode in MY_LEGACY_ONLY_EXTRA
                when {
                    // Performance-only color (PB02): only valid variant
                    isPerfOnlyColor -> listOf(myPerformance)
                    // Legacy-only colors (global + MY-specific)
                    isLegacyOnlyColor || isMyLegacyOnly -> listOf(myLegacy)
                    // Premium-only colors: gen known, no Performance needed
                    isPremiumOnlyColor -> listOf(myPremium)
                    // Other Juniper-only colors (PN01, PX02): Standard + Premium, no Performance
                    isNewOnlyColor -> listOf(myStandard, myPremium)
                    // Shared colors (PPSW, null): cross-gen ambiguity, show all
                    // Performance only if trim/wheel don't rule it out
                    else -> if (showPerformance) {
                        listOf(myLegacy, myStandard, myPremium, myPerformance)
                    } else {
                        listOf(myLegacy, myStandard, myPremium)
                    }
                }
            }
            "3" -> {
                when {
                    isLegacyOnlyColor -> listOf(m3Legacy)
                    // Highland-only colors: gen known, no Performance needed
                    isNewOnlyColor -> listOf(m3Highland)
                    // Shared colors (PBSB, PPSW, PPSB): cross-gen ambiguity
                    // Performance only if trim/wheel don't rule it out
                    else -> if (showPerformance) {
                        listOf(m3Legacy, m3Highland, m3HighlandPerf)
                    } else {
                        listOf(m3Legacy, m3Highland)
                    }
                }
            }
            else -> emptyList() // Model S/X don't have multiple variants to pick from
        }
    }

    /**
     * Get available wheels for a variant, with preview paths.
     *
     * When wheelType is provided (from API) and maps to this variant, returns only
     * that wheel. When wheelType doesn't map (e.g., API reports a legacy wheel name
     * on a Highland car), returns ALL wheels so the user can browse and pick.
     *
     * @param variant The model variant (e.g., "my", "myj")
     * @param colorCode The color code for generating preview paths (defaults to PPSW)
     * @param wheelType The wheel type from TeslamateAPI (e.g., "Crossflow19") - optional
     * @return List of wheel options with preview paths
     */
    fun getWheelsForVariant(variant: String, colorCode: String?, wheelType: String? = null): List<WheelOption> {
        val wheels = VARIANT_WHEELS[variant] ?: return emptyList()
        val color = colorCode ?: DEFAULT_COLORS[variant] ?: "PPSW"
        val validatedColor = validateColorForVariant(variant, color)

        // When wheel type is known and maps to this variant, show only that wheel
        if (wheelType != null) {
            val mappedWheel = mapWheel(variant, wheelType)
            if (mappedWheel != null && mappedWheel in wheels) {
                return listOf(
                    WheelOption(
                        code = mappedWheel,
                        displayName = WHEEL_DISPLAY_NAMES[mappedWheel] ?: mappedWheel,
                        assetPath = "car_images/${variant}_${validatedColor}_${mappedWheel}.png"
                    )
                )
            }
            // Wheel doesn't map to this variant (e.g., API reports "Pinwheel18CapKit"
            // on a Highland M3) - show all wheels so the user can pick
        }

        return wheels.map { wheelCode ->
            WheelOption(
                code = wheelCode,
                displayName = WHEEL_DISPLAY_NAMES[wheelCode] ?: wheelCode,
                assetPath = "car_images/${variant}_${validatedColor}_${wheelCode}.png"
            )
        }
    }

    /**
     * Build an asset path from manual override selection.
     *
     * @param variant The model variant (e.g., "my", "myj")
     * @param colorCode The color code (optional, will use default if not provided)
     * @param wheelCode The wheel code
     * @return The asset path for the selected configuration
     */
    fun getAssetPathForOverride(variant: String, colorCode: String?, wheelCode: String): String {
        val color = colorCode ?: DEFAULT_COLORS[variant] ?: "PPSW"
        val validatedColor = validateColorForVariant(variant, color)
        return "car_images/${variant}_${validatedColor}_${wheelCode}.png"
    }
}
