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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.matedroid.R
import com.matedroid.ui.screens.battery.BatteryScreen
import com.matedroid.ui.screens.charges.ChargeDetailScreen
import com.matedroid.ui.screens.charges.ChargesScreen
import com.matedroid.ui.screens.charges.CompareChargesScreen
import com.matedroid.ui.screens.charges.CurrentChargeScreen
import com.matedroid.ui.screens.costs.CostAnalyticsScreen
import com.matedroid.ui.screens.dashboard.DashboardScreen
import com.matedroid.ui.screens.demo.PalettePreviewScreen
import com.matedroid.ui.screens.drives.CompareDrivesScreen
import com.matedroid.ui.screens.drives.DriveDetailScreen
import com.matedroid.ui.screens.drives.DrivesScreen
import com.matedroid.ui.screens.mileage.MileageScreen
import com.matedroid.ui.screens.settings.SettingsScreen
import com.matedroid.ui.screens.stats.CountriesVisitedScreen
import com.matedroid.ui.screens.stats.RegionsVisitedScreen
import com.matedroid.ui.screens.stats.StatsScreen
import com.matedroid.ui.screens.sentry.SentryHistoryScreen
import com.matedroid.ui.screens.trips.CreateTripScreen
import com.matedroid.ui.screens.trips.TripDetailScreen
import com.matedroid.ui.screens.trips.TripsScreen
import com.matedroid.ui.screens.updates.SoftwareVersionsScreen
import com.matedroid.ui.screens.wherewasi.WhereWasIScreen
import com.matedroid.domain.model.YearFilter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Arguments are (de)serialized by Navigation's
 * kotlinx.serialization integration — no manual route strings or URL encoding.
 *
 * Conventions kept from the previous string-route implementation:
 * - `efficiency = 0f` means "not provided" (mapped to null at the screen boundary)
 * - `year = -1` means [YearFilter.AllTime]
 */
sealed interface Screen {
    @Serializable
    data object Settings : Screen

    @Serializable
    data object Dashboard : Screen

    @Serializable
    data object PalettePreview : Screen

    @Serializable
    data class Charges(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class ChargeDetail(val carId: Int, val chargeId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CompareCharges(val carId: Int, val baseChargeId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CurrentCharge(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class Drives(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class DriveDetail(val carId: Int, val driveId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CompareDrives(val carId: Int, val baseDriveId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class Battery(val carId: Int, val efficiency: Float = 0f, val exteriorColor: String? = null) : Screen

    @Serializable
    data class Mileage(val carId: Int, val exteriorColor: String? = null, val targetDay: String? = null) : Screen

    @Serializable
    data class Updates(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class Stats(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CountriesVisited(val carId: Int, val exteriorColor: String? = null, val year: Int = -1) : Screen

    @Serializable
    data class RegionsVisited(
        val carId: Int,
        val countryCode: String,
        val countryName: String = "",
        val exteriorColor: String? = null,
        val year: Int = -1
    ) : Screen

    @Serializable
    data class WhereWasI(val carId: Int, val timestamp: String, val exteriorColor: String? = null) : Screen

    @Serializable
    data class Trips(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class TripDetail(val carId: Int, val tripStartDate: String, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CreateTrip(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class SentryHistory(val carId: Int, val exteriorColor: String? = null) : Screen

    @Serializable
    data class CostAnalytics(val carId: Int, val exteriorColor: String? = null) : Screen
}

@Composable
fun NavGraph(
    intent: Intent? = null,
    startViewModel: StartDestinationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination by startViewModel.startDestination.collectAsStateWithLifecycle()
    val notificationPermissionAsked by startViewModel.notificationPermissionAsked.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    if (startDestination == null) {
        return // Wait for determination
    }

    // One-time notification permission dialog (Android 13+)
    if (startDestination == Screen.Dashboard &&
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
                val screen: Screen? = when (navigateTo) {
                    "current_charge" -> Screen.CurrentCharge(carId, exteriorColor)
                    "charges" -> Screen.Charges(carId, exteriorColor)
                    "drives" -> Screen.Drives(carId, exteriorColor)
                    "mileage" -> Screen.Mileage(carId, exteriorColor)
                    "battery" -> Screen.Battery(carId, exteriorColor = exteriorColor)
                    "stats" -> Screen.Stats(carId, exteriorColor)
                    "costs" -> Screen.CostAnalytics(carId, exteriorColor)
                    "trips" -> Screen.Trips(carId, exteriorColor)
                    "countries_visited" -> Screen.CountriesVisited(carId, exteriorColor)
                    "updates" -> Screen.Updates(carId, exteriorColor)
                    "sentry_history" -> Screen.SentryHistory(carId, exteriorColor)
                    else -> null
                }
                screen?.let { s ->
                    navController.navigate(s) {
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
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo<Screen.Settings> { inclusive = true }
                    }
                },
                onNavigateToPalettePreview = {
                    navController.navigate(Screen.PalettePreview)
                }
            )
        }

        composable<Screen.Dashboard> {
            DashboardScreen(
                intent = intent,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToCharges = { carId, exteriorColor ->
                    navController.navigate(Screen.Charges(carId, exteriorColor))
                },
                onNavigateToDrives = { carId, exteriorColor ->
                    navController.navigate(Screen.Drives(carId, exteriorColor))
                },
                onNavigateToBattery = { carId, efficiency, exteriorColor ->
                    navController.navigate(Screen.Battery(carId, efficiency?.toFloat() ?: 0f, exteriorColor))
                },
                onNavigateToMileage = { carId, exteriorColor ->
                    navController.navigate(Screen.Mileage(carId, exteriorColor))
                },
                onNavigateToUpdates = { carId, exteriorColor ->
                    navController.navigate(Screen.Updates(carId, exteriorColor))
                },
                onNavigateToStats = { carId, exteriorColor ->
                    navController.navigate(Screen.Stats(carId, exteriorColor))
                },
                onNavigateToCurrentCharge = { carId, exteriorColor ->
                    navController.navigate(Screen.CurrentCharge(carId, exteriorColor))
                },
                onNavigateToWhereWasI = { carId, timestamp, exteriorColor ->
                    navController.navigate(Screen.WhereWasI(carId, timestamp, exteriorColor))
                },
                onNavigateToSentryHistory = { carId, exteriorColor ->
                    navController.navigate(Screen.SentryHistory(carId, exteriorColor))
                },
                onNavigateToTrips = { carId, exteriorColor ->
                    navController.navigate(Screen.Trips(carId, exteriorColor))
                },
                onNavigateToCosts = { carId, exteriorColor ->
                    navController.navigate(Screen.CostAnalytics(carId, exteriorColor))
                }
            )
        }

        composable<Screen.Charges> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Charges>()
            ChargesScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail(route.carId, chargeId, route.exteriorColor))
                }
            )
        }

        composable<Screen.ChargeDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ChargeDetail>()
            ChargeDetailScreen(
                carId = route.carId,
                chargeId = route.chargeId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTripDetail = { tripStartDate ->
                    navController.navigate(Screen.TripDetail(route.carId, tripStartDate, route.exteriorColor))
                },
                onNavigateToCompare = { baseChargeId ->
                    navController.navigate(Screen.CompareCharges(route.carId, baseChargeId, route.exteriorColor))
                }
            )
        }

        composable<Screen.CompareCharges> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CompareCharges>()
            CompareChargesScreen(
                carId = route.carId,
                baseChargeId = route.baseChargeId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail(route.carId, chargeId, route.exteriorColor))
                }
            )
        }

        composable<Screen.CurrentCharge> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CurrentCharge>()
            CurrentChargeScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Drives> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Drives>()
            DrivesScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                }
            )
        }

        composable<Screen.DriveDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.DriveDetail>()
            DriveDetailScreen(
                carId = route.carId,
                driveId = route.driveId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTripDetail = { tripStartDate ->
                    navController.navigate(Screen.TripDetail(route.carId, tripStartDate, route.exteriorColor))
                },
                onNavigateToCompare = { baseDriveId ->
                    navController.navigate(Screen.CompareDrives(route.carId, baseDriveId, route.exteriorColor))
                }
            )
        }

        composable<Screen.CompareDrives> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CompareDrives>()
            CompareDrivesScreen(
                carId = route.carId,
                baseDriveId = route.baseDriveId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                }
            )
        }

        composable<Screen.Battery> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Battery>()
            BatteryScreen(
                carId = route.carId,
                efficiency = route.efficiency.toDouble().takeIf { it > 0 },
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Mileage> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Mileage>()
            MileageScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                targetDay = route.targetDay,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                }
            )
        }

        composable<Screen.Updates> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Updates>()
            SoftwareVersionsScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.PalettePreview> {
            PalettePreviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Stats> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Stats>()
            StatsScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail(route.carId, chargeId, route.exteriorColor))
                },
                onNavigateToDayDetail = { targetDay ->
                    navController.navigate(Screen.Mileage(route.carId, route.exteriorColor, targetDay))
                },
                onNavigateToCountriesVisited = { year ->
                    navController.navigate(Screen.CountriesVisited(route.carId, route.exteriorColor, year ?: -1))
                }
            )
        }

        composable<Screen.CountriesVisited> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CountriesVisited>()
            val year = route.year.takeIf { it > 0 }
            val yearFilter = if (year != null) YearFilter.Year(year) else YearFilter.AllTime

            CountriesVisitedScreen(
                carId = route.carId,
                yearFilter = yearFilter,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegions = { countryCode, countryName ->
                    navController.navigate(
                        Screen.RegionsVisited(route.carId, countryCode, countryName, route.exteriorColor, route.year)
                    )
                }
            )
        }

        composable<Screen.RegionsVisited> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.RegionsVisited>()
            val year = route.year.takeIf { it > 0 }
            val yearFilter = if (year != null) YearFilter.Year(year) else YearFilter.AllTime
            val countryName = route.countryName.ifEmpty { route.countryCode }

            RegionsVisitedScreen(
                carId = route.carId,
                countryCode = route.countryCode,
                countryName = countryName,
                yearFilter = yearFilter,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.WhereWasI> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.WhereWasI>()
            WhereWasIScreen(
                carId = route.carId,
                targetTimestamp = route.timestamp,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail(route.carId, chargeId, route.exteriorColor))
                },
                onNavigateToCountriesVisited = {
                    navController.navigate(Screen.CountriesVisited(route.carId, route.exteriorColor))
                }
            )
        }

        composable<Screen.Trips> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Trips>()
            TripsScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTripDetail = { tripStartDate ->
                    navController.navigate(Screen.TripDetail(route.carId, tripStartDate, route.exteriorColor))
                },
                onNavigateToCreateTrip = {
                    navController.navigate(Screen.CreateTrip(route.carId, route.exteriorColor))
                }
            )
        }

        composable<Screen.CreateTrip> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CreateTrip>()
            CreateTripScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = { startDate ->
                    navController.navigate(Screen.TripDetail(route.carId, startDate, route.exteriorColor)) {
                        popUpTo<Screen.CreateTrip> { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.TripDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.TripDetail>()
            TripDetailScreen(
                carId = route.carId,
                tripStartDate = route.tripStartDate,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriveDetail = { driveId ->
                    navController.navigate(Screen.DriveDetail(route.carId, driveId, route.exteriorColor))
                },
                onNavigateToChargeDetail = { chargeId ->
                    navController.navigate(Screen.ChargeDetail(route.carId, chargeId, route.exteriorColor))
                },
                onNavigateToCountryStats = { countryCode ->
                    val countryName = java.util.Locale.Builder().setRegion(countryCode).build().displayCountry
                    navController.navigate(
                        Screen.RegionsVisited(route.carId, countryCode, countryName, route.exteriorColor)
                    )
                }
            )
        }

        composable<Screen.SentryHistory> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.SentryHistory>()
            SentryHistoryScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.CostAnalytics> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.CostAnalytics>()
            CostAnalyticsScreen(
                carId = route.carId,
                exteriorColor = route.exteriorColor,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
