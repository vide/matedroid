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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.glance.appwidget.state.getAppWidgetState
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

@AndroidEntryPoint
class TripSummaryWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var teslamateRepository: TeslamateRepository

    private sealed interface ScreenState {
        data object Loading : ScreenState
        data class Picker(val cars: List<CarData>) : ScreenState
        data class Error(val message: String) : ScreenState
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                var selectedRange by remember { mutableStateOf(TripSummaryWidgetRange.DEFAULT) }
                var selectedCarId by remember { mutableStateOf<Int?>(null) }

                LaunchedEffect(Unit) {
                    val glanceId = GlanceAppWidgetManager(this@TripSummaryWidgetConfigActivity)
                        .getGlanceIdBy(appWidgetId)
                    val prefs = getAppWidgetState(
                        this@TripSummaryWidgetConfigActivity,
                        PreferencesGlanceStateDefinition,
                        glanceId,
                    )
                    selectedRange = TripSummaryWidgetRange.fromDays(
                        prefs[TripSummaryWidget.RANGE_DAYS_KEY]
                            ?: TripSummaryWidgetRange.DEFAULT.days
                    )
                    selectedCarId = prefs[TripSummaryWidget.CAR_ID_KEY]

                    when (val result = teslamateRepository.getCars()) {
                        is ApiResult.Success -> {
                            val cars = result.data
                            when {
                                cars.isEmpty() -> screenState = ScreenState.Error(
                                    getString(R.string.no_vehicles_found)
                                )
                                else -> {
                                    if (selectedCarId == null && cars.size == 1) {
                                        selectedCarId = cars.single().carId
                                    }
                                    screenState = ScreenState.Picker(cars)
                                }
                            }
                        }
                        is ApiResult.Error -> screenState = ScreenState.Error(result.message)
                    }
                }

                when (val s = screenState) {
                    is ScreenState.Loading -> LoadingScreen()
                    is ScreenState.Picker -> PickerScreen(
                        cars = s.cars,
                        selectedRange = selectedRange,
                        selectedCarId = selectedCarId,
                        onRangeSelected = { selectedRange = it },
                        onCarSelected = { selectedCarId = it.carId },
                        onConfirm = {
                            s.cars.firstOrNull { it.carId == selectedCarId }?.let { car ->
                                confirmSelection(appWidgetId, car, selectedRange)
                            }
                        },
                    )
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
    private fun PickerScreen(
        cars: List<CarData>,
        selectedRange: TripSummaryWidgetRange,
        selectedCarId: Int?,
        onRangeSelected: (TripSummaryWidgetRange) -> Unit,
        onCarSelected: (CarData) -> Unit,
        onConfirm: () -> Unit,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.widget_select_car_title)) })
            },
            bottomBar = {
                Button(
                    onClick = onConfirm,
                    enabled = selectedCarId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.widget_save))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.trip_widget_range_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TripSummaryWidgetRange.values().forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { onRangeSelected(range) },
                                    label = { Text(stringResource(range.labelRes)) }
                                )
                            }
                        }
                    }
                }

                items(cars) { car ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCarSelected(car) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCarId == car.carId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = car.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(
                                selected = selectedCarId == car.carId,
                                onClick = null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun confirmSelection(
        appWidgetId: Int,
        car: CarData,
        range: TripSummaryWidgetRange,
    ) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TripSummaryWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@TripSummaryWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TripSummaryWidget.CAR_ID_KEY] = car.carId
                    this[TripSummaryWidget.CAR_NAME_KEY] = car.displayName
                    this[TripSummaryWidget.RANGE_DAYS_KEY] = range.days
                    this[TripSummaryWidget.PERIOD_LABEL_KEY] = getString(range.labelRes)
                    this[TripSummaryWidget.HAS_DATA_KEY] = false
                }
            }

            TripSummaryWidget().update(this@TripSummaryWidgetConfigActivity, glanceId)
            TripSummaryWidgetUpdateWorker.scheduleImmediateUpdate(this@TripSummaryWidgetConfigActivity)

            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
            finish()
        }
    }
}
