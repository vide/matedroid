package com.matedroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom icons not available in Material Icons Extended.
 * These are converted from Material Symbols (Google Fonts).
 */
object CustomIcons {
    /**
     * Road icon from Material Symbols Outlined.
     * Source: https://fonts.google.com/icons?icon.query=road
     */
    val Road: ImageVector by lazy {
        ImageVector.Builder(
            name = "Road",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Original SVG path with Y-axis transformation (viewBox was "0 -960 960 960")
                // M160-160v-640h80v640h-80Z -> left lane line
                moveTo(160f, 800f)
                verticalLineToRelative(-640f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(640f)
                close()

                // m280 0v-160h80v160h-80Z -> bottom center dashed line
                moveTo(440f, 800f)
                verticalLineToRelative(-160f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(160f)
                close()

                // m280 0v-640h80v640h-80Z -> right lane line
                moveTo(720f, 800f)
                verticalLineToRelative(-640f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(640f)
                close()

                // M440-400v-160h80v160h-80Z -> middle center dashed line
                moveTo(440f, 560f)
                verticalLineToRelative(-160f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(160f)
                close()

                // m0-240v-160h80v160h-80Z -> top center dashed line
                moveTo(440f, 320f)
                verticalLineToRelative(-160f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(160f)
                close()
            }
        }.build()
    }

    /**
     * Steering wheel icon from Material Symbols Outlined (search_hands_free).
     * Source: https://fonts.google.com/icons?icon.query=search+hands+free
     */
    val SteeringWheel: ImageVector by lazy {
        ImageVector.Builder(
            name = "SteeringWheel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black), fillAlpha = 1f, pathFillType = PathFillType.EvenOdd) {
                // Outer circle (converted y: -80 -> 880, -480 -> 480, -880 -> 80, etc.)
                // M480-80 -> M480,880
                moveTo(480f, 880f)
                // q-83 0-156-31.5T197-197 -> curves for circle
                quadToRelative(-83f, 0f, -156f, -31.5f)
                reflectiveQuadTo(197f, 763f)
                quadToRelative(-54f, -54f, -85.5f, -127f)
                reflectiveQuadTo(80f, 480f)
                quadToRelative(0f, -83f, 31.5f, -156f)
                reflectiveQuadTo(197f, 197f)
                quadToRelative(54f, -54f, 127f, -85.5f)
                reflectiveQuadTo(480f, 80f)
                quadToRelative(83f, 0f, 156f, 31.5f)
                reflectiveQuadTo(763f, 197f)
                quadToRelative(54f, 54f, 85.5f, 127f)
                reflectiveQuadTo(880f, 480f)
                quadToRelative(0f, 83f, -31.5f, 156f)
                reflectiveQuadTo(763f, 763f)
                quadToRelative(-54f, 54f, -127f, 85.5f)
                reflectiveQuadTo(480f, 880f)
                close()

                // Bottom left section: Zm-40-84v-120q-60-12-102-54t-54-102H164q12 109 89.5 185T440-164
                // -84 -> 796, -164 -> 796
                moveTo(440f, 796f)
                verticalLineToRelative(-120f)
                quadToRelative(-60f, -12f, -102f, -54f)
                reflectiveQuadToRelative(-54f, -102f)
                horizontalLineTo(164f)
                quadToRelative(12f, 109f, 89.5f, 185f)
                reflectiveQuadTo(440f, 796f)
                close()

                // Bottom right section: Zm80 0q109-12 186.5-89.5T796-440H676q-12 60-54 102t-102 54v120Z
                moveTo(520f, 796f)
                quadToRelative(109f, -12f, 186.5f, -89.5f)
                reflectiveQuadTo(796f, 520f)
                horizontalLineTo(676f)
                quadToRelative(-12f, 60f, -54f, 102f)
                reflectiveQuadToRelative(-102f, 54f)
                verticalLineToRelative(120f)
                close()

                // Top section with steering wheel grip: ZM164-520h116l120-120h160l120 120h116q-15-121-105-200.5T480-800q-121 0-211 79.5T164-520Z
                // -520 -> 440, -800 -> 160
                moveTo(164f, 440f)
                horizontalLineToRelative(116f)
                lineToRelative(120f, -120f)
                horizontalLineToRelative(160f)
                lineToRelative(120f, 120f)
                horizontalLineToRelative(116f)
                quadToRelative(-15f, -121f, -105f, -200.5f)
                reflectiveQuadTo(480f, 160f)
                quadToRelative(-121f, 0f, -211f, 79.5f)
                reflectiveQuadTo(164f, 440f)
                close()
            }
        }.build()
    }

    /**
     * Trophy icon from Material Symbols Outlined.
     * Source: https://fonts.google.com/icons?icon.query=trophy
     */
    val Trophy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trophy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Trophy SVG path (viewport 0 -960 960 960, transform y to 0-960)
                // M280-120v-80h160v-124q-49-11-87.5-41.5T296-442q-75-9-125.5-65.5T120-640v-40q0-33 23.5-56.5T200-760h80v-80h400v80h80q33 0 56.5 23.5T840-680v40q0 76-50.5 132.5T664-442q-18 46-56.5 76.5T520-324v124h160v80H280Z
                moveTo(280f, 840f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(160f)
                verticalLineToRelative(-124f)
                quadToRelative(-49f, -11f, -87.5f, -41.5f)
                reflectiveQuadTo(296f, 518f)
                quadToRelative(-75f, -9f, -125.5f, -65.5f)
                reflectiveQuadTo(120f, 320f)
                verticalLineToRelative(-40f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(200f, 200f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(400f)
                verticalLineToRelative(80f)
                horizontalLineToRelative(80f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(840f, 280f)
                verticalLineToRelative(40f)
                quadToRelative(0f, 76f, -50.5f, 132.5f)
                reflectiveQuadTo(664f, 518f)
                quadToRelative(-18f, 46f, -56.5f, 76.5f)
                reflectiveQuadTo(520f, 636f)
                verticalLineToRelative(124f)
                horizontalLineToRelative(160f)
                verticalLineToRelative(80f)
                horizontalLineTo(280f)
                close()

                // m80-400h40q0 45 29 79t71 44v-283H280v160Z
                moveTo(360f, 440f)
                horizontalLineToRelative(-160f)
                verticalLineToRelative(-160f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(-80f)
                quadToRelative(0f, 45f, 29f, 79f)
                reflectiveQuadToRelative(71f, 44f)
                verticalLineToRelative(117f)
                close()

                // Additional path components for the trophy handles and base
                // Simplified - using basic shape
            }
        }.build()
    }

    // Weather icons from Material Symbols Outlined
    // Source: https://fonts.google.com/icons

    /**
     * Clear/Sunny weather icon.
     * Material Symbol: sunny (light_mode)
     */
    val WeatherSunny: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherSunny",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // M480-280q-83 0-141.5-58.5T280-480q0-83 58.5-141.5T480-680q83 0 141.5 58.5T680-480q0 83-58.5 141.5T480-280Z
                // Sun circle
                moveTo(480f, 680f)
                quadToRelative(-83f, 0f, -141.5f, -58.5f)
                reflectiveQuadTo(280f, 480f)
                quadToRelative(0f, -83f, 58.5f, -141.5f)
                reflectiveQuadTo(480f, 280f)
                quadToRelative(83f, 0f, 141.5f, 58.5f)
                reflectiveQuadTo(680f, 480f)
                quadToRelative(0f, 83f, -58.5f, 141.5f)
                reflectiveQuadTo(480f, 680f)
                close()

                // M440-760v-160h80v160h-80Z (top ray)
                moveTo(440f, 200f)
                verticalLineToRelative(-120f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(120f)
                close()

                // m0 720v-160h80v160h-80Z (bottom ray)
                moveTo(440f, 880f)
                verticalLineToRelative(-120f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(120f)
                close()

                // M760-440h160v-80H760v80Z (right ray)
                moveTo(760f, 520f)
                horizontalLineToRelative(120f)
                verticalLineToRelative(-80f)
                horizontalLineTo(760f)
                close()

                // M40-440h160v-80H40v80Z (left ray)
                moveTo(80f, 520f)
                horizontalLineToRelative(-120f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(120f)
                close()
            }
        }.build()
    }

    /**
     * Partly cloudy weather icon.
     * Material Symbol: partly_cloudy_day
     */
    val WeatherPartlyCloudy: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherPartlyCloudy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Cloud shape with partial sun
                // M260-160q-91 0-155.5-63T40-377q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-520q69 8 114.5 59.5T920-340q0 75-52.5 127.5T740-160H260Z
                moveTo(260f, 800f)
                quadToRelative(-91f, 0f, -155.5f, -63f)
                reflectiveQuadTo(40f, 583f)
                quadToRelative(0f, -78f, 47f, -139f)
                reflectiveQuadToRelative(123f, -78f)
                quadToRelative(25f, -92f, 100f, -149f)
                reflectiveQuadToRelative(170f, -57f)
                quadToRelative(117f, 0f, 198.5f, 81.5f)
                reflectiveQuadTo(760f, 440f)
                quadToRelative(69f, 8f, 114.5f, 59.5f)
                reflectiveQuadTo(920f, 620f)
                quadToRelative(0f, 75f, -52.5f, 127.5f)
                reflectiveQuadTo(740f, 800f)
                horizontalLineTo(260f)
                close()
            }
        }.build()
    }

    /**
     * Foggy weather icon.
     * Material Symbol: foggy
     */
    val WeatherFog: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherFog",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Fog lines
                // M160-200v-80h640v80H160Z (bottom line)
                moveTo(160f, 760f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(640f)
                verticalLineToRelative(80f)
                horizontalLineTo(160f)
                close()

                // m0-160v-80h640v80H160Z (middle line)
                moveTo(160f, 600f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(640f)
                verticalLineToRelative(80f)
                horizontalLineTo(160f)
                close()

                // m0-160v-80h640v80H160Z (top line)
                moveTo(160f, 440f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(640f)
                verticalLineToRelative(80f)
                horizontalLineTo(160f)
                close()
            }
        }.build()
    }

    /**
     * Rainy weather icon.
     * Material Symbol: rainy
     */
    val WeatherRain: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherRain",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Cloud with rain drops
                // M558-82 398-242l56-56 160 160-56 56Z (rain drop 1)
                moveTo(558f, 878f)
                lineToRelative(-160f, -160f)
                lineToRelative(56f, -56f)
                lineToRelative(160f, 160f)
                close()

                // M368-82 208-242l56-56 160 160-56 56Z (rain drop 2)
                moveTo(368f, 878f)
                lineToRelative(-160f, -160f)
                lineToRelative(56f, -56f)
                lineToRelative(160f, 160f)
                close()

                // M748-82 588-242l56-56 160 160-56 56Z (rain drop 3)
                moveTo(748f, 878f)
                lineToRelative(-160f, -160f)
                lineToRelative(56f, -56f)
                lineToRelative(160f, 160f)
                close()

                // Cloud shape
                // M260-360q-91 0-155.5-63T40-577q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-720q69 8 114.5 59.5T920-540q0 75-52.5 127.5T740-360H260Z
                moveTo(260f, 600f)
                quadToRelative(-91f, 0f, -155.5f, -63f)
                reflectiveQuadTo(40f, 383f)
                quadToRelative(0f, -78f, 47f, -139f)
                reflectiveQuadToRelative(123f, -78f)
                quadToRelative(25f, -92f, 100f, -149f)
                reflectiveQuadToRelative(170f, -57f)
                quadToRelative(117f, 0f, 198.5f, 81.5f)
                reflectiveQuadTo(760f, 240f)
                quadToRelative(69f, 8f, 114.5f, 59.5f)
                reflectiveQuadTo(920f, 420f)
                quadToRelative(0f, 75f, -52.5f, 127.5f)
                reflectiveQuadTo(740f, 600f)
                horizontalLineTo(260f)
                close()
            }
        }.build()
    }

    /**
     * Snowy weather icon.
     * Material Symbol: ac_unit (snowflake)
     */
    val WeatherSnow: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherSnow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Snowflake shape
                // M440-80v-166L310-116l-56-56 186-186v-82h-82L172-254l-56-56 130-130H80v-80h166L116-650l56-56 186 186h82v-82L254-788l56-56 130 130V-880h80v166l130-130 56 56-186 186v82h82l186-186 56 56-130 130h166v80H714l130 130-56 56-186-186h-82v82l186 186-56 56-130-130v166h-80Z
                moveTo(440f, 880f)
                verticalLineToRelative(-166f)
                lineTo(310f, 844f)
                lineToRelative(-56f, -56f)
                lineToRelative(186f, -186f)
                verticalLineToRelative(-82f)
                horizontalLineToRelative(-82f)
                lineTo(172f, 706f)
                lineToRelative(-56f, -56f)
                lineToRelative(130f, -130f)
                horizontalLineTo(80f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(166f)
                lineTo(116f, 310f)
                lineToRelative(56f, -56f)
                lineToRelative(186f, 186f)
                horizontalLineToRelative(82f)
                verticalLineToRelative(-82f)
                lineTo(254f, 172f)
                lineToRelative(56f, -56f)
                lineToRelative(130f, 130f)
                verticalLineTo(80f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(166f)
                lineToRelative(130f, -130f)
                lineToRelative(56f, 56f)
                lineToRelative(-186f, 186f)
                verticalLineToRelative(82f)
                horizontalLineToRelative(82f)
                lineToRelative(186f, -186f)
                lineToRelative(56f, 56f)
                lineToRelative(-130f, 130f)
                horizontalLineToRelative(166f)
                verticalLineToRelative(80f)
                horizontalLineTo(714f)
                lineToRelative(130f, 130f)
                lineToRelative(-56f, 56f)
                lineToRelative(-186f, -186f)
                horizontalLineToRelative(-82f)
                verticalLineToRelative(82f)
                lineToRelative(186f, 186f)
                lineToRelative(-56f, 56f)
                lineToRelative(-130f, -130f)
                verticalLineToRelative(166f)
                horizontalLineToRelative(-80f)
                close()
            }
        }.build()
    }

    /**
     * Thunderstorm weather icon.
     * Material Symbol: thunderstorm
     */
    val WeatherThunderstorm: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherThunderstorm",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Lightning bolt with cloud
                // M480-80 360-280h120v-200h120L500-280H380l100-200H360L480-80Z (lightning bolt)
                moveTo(480f, 880f)
                lineTo(320f, 600f)
                horizontalLineToRelative(100f)
                verticalLineToRelative(-120f)
                horizontalLineToRelative(120f)
                lineTo(420f, 680f)
                horizontalLineToRelative(100f)
                close()

                // Cloud shape above
                // M260-440q-91 0-155.5-63T40-657q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-800q69 8 114.5 59.5T920-620q0 75-52.5 127.5T740-440H260Z
                moveTo(260f, 520f)
                quadToRelative(-91f, 0f, -155.5f, -63f)
                reflectiveQuadTo(40f, 303f)
                quadToRelative(0f, -78f, 47f, -139f)
                reflectiveQuadToRelative(123f, -78f)
                quadToRelative(25f, -92f, 100f, -149f)
                reflectiveQuadToRelative(170f, -57f)
                quadToRelative(117f, 0f, 198.5f, 81.5f)
                reflectiveQuadTo(760f, 160f)
                quadToRelative(69f, 8f, 114.5f, 59.5f)
                reflectiveQuadTo(920f, 340f)
                quadToRelative(0f, 75f, -52.5f, 127.5f)
                reflectiveQuadTo(740f, 520f)
                horizontalLineTo(260f)
                close()
            }
        }.build()
    }

    /**
     * Drizzle weather icon (light rain).
     * Material Symbol: grain (representing light precipitation)
     */
    val WeatherDrizzle: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherDrizzle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Cloud with light rain drops (simplified)
                // Cloud shape
                moveTo(260f, 600f)
                quadToRelative(-91f, 0f, -155.5f, -63f)
                reflectiveQuadTo(40f, 383f)
                quadToRelative(0f, -78f, 47f, -139f)
                reflectiveQuadToRelative(123f, -78f)
                quadToRelative(25f, -92f, 100f, -149f)
                reflectiveQuadToRelative(170f, -57f)
                quadToRelative(117f, 0f, 198.5f, 81.5f)
                reflectiveQuadTo(760f, 240f)
                quadToRelative(69f, 8f, 114.5f, 59.5f)
                reflectiveQuadTo(920f, 420f)
                quadToRelative(0f, 75f, -52.5f, 127.5f)
                reflectiveQuadTo(740f, 600f)
                horizontalLineTo(260f)
                close()

                // Rain drops (dots)
                // Drop 1
                moveTo(300f, 720f)
                quadToRelative(-17f, 0f, -28.5f, -11.5f)
                reflectiveQuadTo(260f, 680f)
                quadToRelative(0f, -17f, 11.5f, -28.5f)
                reflectiveQuadTo(300f, 640f)
                quadToRelative(17f, 0f, 28.5f, 11.5f)
                reflectiveQuadTo(340f, 680f)
                quadToRelative(0f, 17f, -11.5f, 28.5f)
                reflectiveQuadTo(300f, 720f)
                close()

                // Drop 2
                moveTo(480f, 800f)
                quadToRelative(-17f, 0f, -28.5f, -11.5f)
                reflectiveQuadTo(440f, 760f)
                quadToRelative(0f, -17f, 11.5f, -28.5f)
                reflectiveQuadTo(480f, 720f)
                quadToRelative(17f, 0f, 28.5f, 11.5f)
                reflectiveQuadTo(520f, 760f)
                quadToRelative(0f, 17f, -11.5f, 28.5f)
                reflectiveQuadTo(480f, 800f)
                close()

                // Drop 3
                moveTo(660f, 720f)
                quadToRelative(-17f, 0f, -28.5f, -11.5f)
                reflectiveQuadTo(620f, 680f)
                quadToRelative(0f, -17f, 11.5f, -28.5f)
                reflectiveQuadTo(660f, 640f)
                quadToRelative(17f, 0f, 28.5f, 11.5f)
                reflectiveQuadTo(700f, 680f)
                quadToRelative(0f, 17f, -11.5f, 28.5f)
                reflectiveQuadTo(660f, 720f)
                close()
            }
        }.build()
    }
}
