package com.matedroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matedroid.ui.screens.battery.BatteryScreen
import com.matedroid.ui.screens.charges.ChargeDetailScreen
import com.matedroid.ui.screens.charges.ChargesScreen
import com.matedroid.ui.screens.charges.CurrentChargeScreen
import com.matedroid.ui.screens.dashboard.DashboardScreen
import com.matedroid.ui.screens.demo.PalettePreviewScreen
import com.matedroid.ui.screens.drives.DriveDetailScreen
import com.matedroid.ui.screens.drives.DrivesScreen
import com.matedroid.ui.screens.mileage.MileageScreen
import com.matedroid.ui.screens.settings.SettingsScreen
import com.matedroid.ui.screens.stats.CountriesVisitedScreen
import com.matedroid.ui.screens.stats.RegionsVisitedScreen
import com.matedroid.ui.screens.stats.StatsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.matedroid.ui.screens.updates.SoftwareVersionsScreen
import com.matedroid.domain.model.YearFilter
import android.content.Intent

sealed class Screen(val route: String) {
    data object Settings : Screen("settings")
    data object Dashboard : Screen("dashboard")
    data object Charges : Screen("charges/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "charges/$carId?exteriorColor=$exteriorColor"
            } else {
                "charges/$carId"
            }
        }
    }
    data object ChargeDetail : Screen("charges/{carId}/detail/{chargeId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, chargeId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "charges/$carId/detail/$chargeId?exteriorColor=$exteriorColor"
            } else {
                "charges/$carId/detail/$chargeId"
            }
        }
    }
    data object Drives : Screen("drives/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "drives/$carId?exteriorColor=$exteriorColor"
            } else {
                "drives/$carId"
            }
        }
    }
    data object DriveDetail : Screen("drives/{carId}/detail/{driveId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, driveId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "drives/$carId/detail/$driveId?exteriorColor=$exteriorColor"
            } else {
                "drives/$carId/detail/$driveId"
            }
        }
    }
    data object Battery : Screen("battery/{carId}?efficiency={efficiency}&exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, efficiency: Double? = null, exteriorColor: String? = null): String {
            val params = mutableListOf<String>()
            if (efficiency != null) params.add("efficiency=$efficiency")
            if (exteriorColor != null) params.add("exteriorColor=$exteriorColor")
            return if (params.isNotEmpty()) {
                "battery/$carId?${params.joinToString("&")}"
            } else {
                "battery/$carId"
            }
        }
    }
    data object Mileage : Screen("mileage/{carId}?exteriorColor={exteriorColor}&targetDay={targetDay}") {
        fun createRoute(carId: Int, exteriorColor: String? = null, targetDay: String? = null): String {
            val params = mutableListOf<String>()
            if (exteriorColor != null) params.add("exteriorColor=$exteriorColor")
            if (targetDay != null) params.add("targetDay=$targetDay")
            return if (params.isNotEmpty()) {
                "mileage/$carId?${params.joinToString("&")}"
            } else {
                "mileage/$carId"
            }
        }
    }
    data object Updates : Screen("updates/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "updates/$carId?exteriorColor=$exteriorColor"
            } else {
                "updates/$carId"
            }
        }
    }
    data object CurrentCharge : Screen("charges/{carId}/current?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "charges/$carId/current?exteriorColor=$exteriorColor"
            } else {
                "charges/$carId/current"
            }
        }
    }
    data object PalettePreview : Screen("palette_preview")
    data object Stats : Screen("stats/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "stats/$carId?exteriorColor=$exteriorColor"
            } else {
                "stats/$carId"
            }
        }
    }
    data object CountriesVisited : Screen("stats/{carId}/countries?exteriorColor={exteriorColor}&year={year}") {
        fun createRoute(carId: Int, exteriorColor: String? = null, year: Int? = null): String {
            val params = mutableListOf<String>()
            if (exteriorColor != null) params.add("exteriorColor=$exteriorColor")
            if (year != null) params.add("year=$year")
            return if (params.isNotEmpty()) {
                "stats/$carId/countries?${params.joinToString("&")}"
            } else {
                "stats/$carId/countries"
            }
        }
    }
    data object RegionsVisited : Screen("stats/{carId}/countries/{countryCode}/regions?exteriorColor={exteriorColor}&year={year}&countryName={countryName}") {
        fun createRoute(carId: Int, countryCode: String, countryName: String, exteriorColor: String? = null, year: Int? = null): String {
            val encodedName = URLEncoder.encode(countryName, StandardCharsets.UTF_8.toString())
            val params = mutableListOf<String>()
            if (exteriorColor != null) params.add("exteriorColor=$exteriorColor")
            if (year != null) params.add("year=$year")
            params.add("countryName=$encodedName")
            return "stats/$carId/countries/$countryCode/regions?${params.joinToString("&")}"
        }
    }
}

@Composable
fun NavGraph(
    intent: Intent? = null,
    startViewModel: StartDestinationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination by startViewModel.startDestination.collectAsState()

    if (startDestination == null) {
        return // Wait for determination
    }

    // Handle deep-link from notification or adb intent
    LaunchedEffect(intent) {
        intent?.let {
            val navigateTo = it.getStringExtra("EXTRA_NAVIGATE_TO")
            val carId = it.getIntExtra("EXTRA_CAR_ID", -1)
            val exteriorColor = it.getStringExtra("EXTRA_EXTERIOR_COLOR")
            if (navigateTo != null && carId > 0) {
                val route = when (navigateTo) {
                    "current_charge" -> Screen.CurrentCharge.createRoute(carId, exteriorColor)
                    "charges" -> Screen.Charges.createRoute(carId, exteriorColor)
                    "drives" -> Screen.Drives.createRoute(carId, exteriorColor)
                    "mileage" -> Screen.Mileage.createRoute(carId, exteriorColor)
                    "battery" -> Screen.Battery.createRoute(carId, exteriorColor = exteriorColor)
                    "stats" -> Screen.Stats.createRoute(carId, exteriorColor)
                    "countries_visited" -> Screen.CountriesVisited.createRoute(carId, exteriorColor)
                    "updates" -> Screen.Updates.createRoute(carId, exteriorColor)
                    else -> null
                }
                route?.let { r ->
                    navController.navigate(r) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Settings.route) { inclusive = true }
                    }
                },
                onNavigateToPalettePreview = {
                    navController.navigate(Screen.PalettePreview.route)
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                intent = intent,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCharges = { carId, exteriorColor ->
                    navController.navigate(Screen.Charges.createRoute(carId, exteriorColor))
                },
                onNavigateToDrives = { carId, exteriorColor ->
                    navController.navigate(Screen.Drives.createRoute(carId, exteriorColor))
                },
                onNavigateToBattery = { carId, efficiency, exteriorColor ->
                    navController.navigate(Screen.Battery.createRoute(carId, efficiency, exteriorColor))
                },
                onNavigateToMileage = { carId, exteriorColor ->
                    navController.navigate(Screen.Mileage.createRoute(carId, exteriorColor))
                },
                onNavigateToUpdates = { carId, exteriorColor ->
                    navController.navigate(Screen.Updates.createRoute(carId, exteriorColor))
                },
                onNavigateToStats = { carId, exteriorColor ->
                    navController.navigate(Screen.Stats.createRoute(carId, exteriorColor))
                },
                onNavigateToCurrentCharge = { carId, exteriorColor ->
                    navController.navigate(Screen.CurrentCharge.createRoute(carId, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.Charges.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            ChargesScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail.createRoute(carId, chargeId, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.ChargeDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("chargeId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val chargeId = backStackEntry.arguments?.getInt("chargeId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            ChargeDetailScreen(
                carId = carId,
                chargeId = chargeId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CurrentCharge.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            CurrentChargeScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Drives.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            DrivesScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail.createRoute(carId, driveId, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.DriveDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("driveId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val driveId = backStackEntry.arguments?.getInt("driveId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            DriveDetailScreen(
                carId = carId,
                driveId = driveId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Battery.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("efficiency") {
                    type = NavType.FloatType
                    defaultValue = 0f
                },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val efficiency = backStackEntry.arguments?.getFloat("efficiency")?.toDouble()
                ?.takeIf { it > 0 }
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            BatteryScreen(
                carId = carId,
                efficiency = efficiency,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Mileage.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("targetDay") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            val targetDay = backStackEntry.arguments?.getString("targetDay")
            MileageScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                targetDay = targetDay,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail.createRoute(carId, driveId, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.Updates.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            SoftwareVersionsScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PalettePreview.route) {
            PalettePreviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Stats.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            StatsScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail.createRoute(carId, driveId, exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail.createRoute(carId, chargeId, exteriorColor))
                },
                onNavigateToDayDetail = { targetDay ->
                    navController.navigate(Screen.Mileage.createRoute(carId, exteriorColor, targetDay))
                },
                onNavigateToCountriesVisited = { year ->
                    navController.navigate(Screen.CountriesVisited.createRoute(carId, exteriorColor, year))
                }
            )
        }

        composable(
            route = Screen.CountriesVisited.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1 // -1 means AllTime
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            val year = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
            val yearFilter = if (year != null) YearFilter.Year(year) else YearFilter.AllTime

            CountriesVisitedScreen(
                carId = carId,
                yearFilter = yearFilter,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegions = { countryCode, countryName ->
                    navController.navigate(Screen.RegionsVisited.createRoute(carId, countryCode, countryName, exteriorColor, year))
                }
            )
        }

        composable(
            route = Screen.RegionsVisited.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("countryCode") { type = NavType.StringType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1 // -1 means AllTime
                },
                navArgument("countryName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val countryCode = backStackEntry.arguments?.getString("countryCode") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            val year = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
            val yearFilter = if (year != null) YearFilter.Year(year) else YearFilter.AllTime
            val countryName = backStackEntry.arguments?.getString("countryName")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: countryCode

            RegionsVisitedScreen(
                carId = carId,
                countryCode = countryCode,
                countryName = countryName,
                yearFilter = yearFilter,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
