package com.matedroid.ui.navigation

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matedroid.R
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
import com.matedroid.ui.screens.sentry.SentryHistoryScreen
import com.matedroid.ui.screens.trips.TripDetailScreen
import com.matedroid.ui.screens.trips.TripsScreen
import com.matedroid.ui.screens.updates.SoftwareVersionsScreen
import com.matedroid.ui.screens.wherewasi.WhereWasIScreen
import com.matedroid.domain.model.YearFilter
import kotlinx.coroutines.launch

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
    data object WhereWasI : Screen("wherewasi/{carId}?timestamp={timestamp}&exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, timestamp: String, exteriorColor: String? = null): String {
            val encodedTimestamp = URLEncoder.encode(timestamp, StandardCharsets.UTF_8.toString())
            val params = mutableListOf("timestamp=$encodedTimestamp")
            if (exteriorColor != null) params.add("exteriorColor=$exteriorColor")
            return "wherewasi/$carId?${params.joinToString("&")}"
        }
    }
    data object Trips : Screen("trips/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) "trips/$carId?exteriorColor=$exteriorColor"
            else "trips/$carId"
        }
    }
    data object TripDetail : Screen("trips/{carId}/detail/{tripStartDate}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, tripStartDate: String, exteriorColor: String? = null): String {
            val encoded = java.net.URLEncoder.encode(tripStartDate, "UTF-8")
            return if (exteriorColor != null) "trips/$carId/detail/$encoded?exteriorColor=$exteriorColor"
            else "trips/$carId/detail/$encoded"
        }
    }
    data object SentryHistory : Screen("sentry/{carId}?exteriorColor={exteriorColor}") {
        fun createRoute(carId: Int, exteriorColor: String? = null): String {
            return if (exteriorColor != null) {
                "sentry/$carId?exteriorColor=$exteriorColor"
            } else {
                "sentry/$carId"
            }
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
    val notificationPermissionAsked by startViewModel.notificationPermissionAsked.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (startDestination == null) {
        return // Wait for determination
    }

    // One-time notification permission dialog (Android 13+)
    if (startDestination == Screen.Dashboard.route &&
        !notificationPermissionAsked &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            coroutineScope.launch { startViewModel.markNotificationPermissionAsked() }
        }

        AlertDialog(
            onDismissRequest = {
                coroutineScope.launch { startViewModel.markNotificationPermissionAsked() }
            },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text(stringResource(R.string.notification_permission_grant))
                }
            }
        )
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
                    "sentry_history" -> Screen.SentryHistory.createRoute(carId, exteriorColor)
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
                },
                onNavigateToWhereWasI = { carId, timestamp, exteriorColor ->
                    navController.navigate(Screen.WhereWasI.createRoute(carId, timestamp, exteriorColor))
                },
                onNavigateToSentryHistory = { carId, exteriorColor ->
                    navController.navigate(Screen.SentryHistory.createRoute(carId, exteriorColor))
                },
                onNavigateToTrips = { carId, exteriorColor ->
                    navController.navigate(Screen.Trips.createRoute(carId, exteriorColor))
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

        composable(
            route = Screen.WhereWasI.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("timestamp") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val timestamp = backStackEntry.arguments?.getString("timestamp")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")

            WhereWasIScreen(
                carId = carId,
                targetTimestamp = timestamp,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail.createRoute(carId, driveId, exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail.createRoute(carId, chargeId, exteriorColor))
                },
                onNavigateToCountriesVisited = {
                    navController.navigate(Screen.CountriesVisited.createRoute(carId, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.Trips.route,
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
            TripsScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTripDetail = { tripStartDate ->
                    navController.navigate(Screen.TripDetail.createRoute(carId, tripStartDate, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.TripDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.IntType },
                navArgument("tripStartDate") { type = NavType.StringType },
                navArgument("exteriorColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getInt("carId") ?: return@composable
            val tripStartDate = backStackEntry.arguments?.getString("tripStartDate") ?: return@composable
            val exteriorColor = backStackEntry.arguments?.getString("exteriorColor")
            TripDetailScreen(
                carId = carId,
                tripStartDate = tripStartDate,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail.createRoute(carId, driveId, exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail.createRoute(carId, chargeId, exteriorColor))
                },
                onNavigateToCountryStats = { countryCode ->
                    val countryName = java.util.Locale("", countryCode).displayCountry
                    navController.navigate(Screen.RegionsVisited.createRoute(carId, countryCode, countryName, exteriorColor))
                }
            )
        }

        composable(
            route = Screen.SentryHistory.route,
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
            SentryHistoryScreen(
                carId = carId,
                exteriorColor = exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
