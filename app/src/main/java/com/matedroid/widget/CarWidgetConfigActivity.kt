package com.matedroid.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.ui.theme.MateDroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration activity shown when a user adds a MateDroid widget.
 *
 * If only one car is available it is selected automatically without showing any UI.
 * If multiple cars are available the user picks one from a list.
 */
@AndroidEntryPoint
class CarWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var teslamateRepository: TeslamateRepository

    private sealed interface ScreenState {
        object Loading : ScreenState
        data class Picker(val cars: List<CarData>) : ScreenState
        data class Error(val message: String) : ScreenState
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Widget must not be added if the user cancels
        setResult(RESULT_CANCELED)

        val appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MateDroidTheme {
                var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Loading) }

                LaunchedEffect(Unit) {
                    when (val result = teslamateRepository.getCars()) {
                        is ApiResult.Success -> {
                            val cars = result.data
                            when {
                                cars.isEmpty() -> screenState = ScreenState.Error(
                                    getString(R.string.no_vehicles_found)
                                )
                                cars.size == 1 -> {
                                    // Auto-select the only car — no UI needed
                                    confirmSelection(appWidgetId, cars.first())
                                }
                                else -> screenState = ScreenState.Picker(cars)
                            }
                        }
                        is ApiResult.Error -> screenState = ScreenState.Error(result.message)
                    }
                }

                when (val s = screenState) {
                    is ScreenState.Loading -> LoadingScreen()
                    is ScreenState.Picker -> PickerScreen(s.cars) { car ->
                        confirmSelection(appWidgetId, car)
                    }
                    is ScreenState.Error -> ErrorScreen(s.message)
                }
            }
        }
    }

    @Composable
    private fun LoadingScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun ErrorScreen(message: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.error_loading_data),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun PickerScreen(cars: List<CarData>, onCarSelected: (CarData) -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.widget_select_car_title)) })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cars) { car ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCarSelected(car) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = car.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                car.carDetails?.model?.let { model ->
                                    Text(
                                        text = "Model $model",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Stores [car] as the widget's configured car, schedules the first data fetch,
     * and finishes the activity with [RESULT_OK].
     */
    private fun confirmSelection(appWidgetId: Int, car: CarData) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@CarWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            // Persist the chosen carId so provideGlance knows which car to show
            updateAppWidgetState(
                this@CarWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[CarWidget.CAR_ID_KEY] = car.carId
                }
            }

            // Show the "Loading…" state immediately, then fire an immediate fetch
            // (scheduleWork would add a delay — scheduleImmediateUpdate runs right away)
            CarWidget().update(this@CarWidgetConfigActivity, glanceId)
            CarWidgetUpdateWorker.scheduleImmediateUpdate(this@CarWidgetConfigActivity)

            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
            finish()
        }
    }
}
