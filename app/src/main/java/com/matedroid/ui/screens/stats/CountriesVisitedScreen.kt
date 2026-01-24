package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesVisitedScreen(
    carId: Int,
    yearFilter: YearFilter,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToRegions: (countryCode: String, countryName: String) -> Unit,
    viewModel: CountriesVisitedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = remember(exteriorColor, isDarkTheme) {
        CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
    }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(carId, yearFilter) {
        viewModel.loadCountries(carId, yearFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.countries_visited_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_first_visit)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.FIRST_VISIT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_alphabetically)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.ALPHABETICAL)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_drive_count)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.DRIVE_COUNT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_distance)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.DISTANCE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_energy)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.ENERGY)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_charges)) },
                                onClick = {
                                    viewModel.setSortOrder(CountrySortOrder.CHARGES)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.accent
                    )
                }
                uiState.countries.isEmpty() -> {
                    EmptyState(palette = palette)
                }
                else -> {
                    CountriesList(
                        countries = uiState.countries,
                        palette = palette,
                        onCountryClick = onNavigateToRegions
                    )
                }
            }
        }
    }
}

@Composable
private fun CountriesList(
    countries: List<CountryRecord>,
    palette: CarColorPalette,
    onCountryClick: (countryCode: String, countryName: String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(countries, key = { it.countryCode }) { country ->
            val localizedName = getLocalizedCountryName(country.countryCode)
            CountryCard(
                country = country,
                localizedName = localizedName,
                palette = palette,
                onClick = { onCountryClick(country.countryCode, localizedName) }
            )
        }
    }
}

@Composable
private fun CountryCard(
    country: CountryRecord,
    localizedName: String,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                spotColor = palette.onSurface.copy(alpha = 0.1f)
            )
            .clip(cardShape)
            .background(palette.surface)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flag emoji
                Text(
                    text = country.flagEmoji,
                    fontSize = 40.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Country name - larger and more prominent
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                }

                // Drive count
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = country.driveCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.drives_count,
                            country.driveCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }

                // Chevron arrow
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_details),
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Stats row in subtle chips
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Distance chip
                StatChip(
                    icon = Icons.Default.Route,
                    value = "%.0f km".format(country.totalDistanceKm),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                // Energy chip
                StatChip(
                    icon = Icons.Default.ElectricBolt,
                    value = if (country.totalChargeEnergyKwh > 999) {
                        "%.1f MWh".format(country.totalChargeEnergyKwh / 1000)
                    } else {
                        "%.0f kWh".format(country.totalChargeEnergyKwh)
                    },
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                // Charges chip
                StatChip(
                    icon = Icons.Default.EvStation,
                    value = pluralStringResource(
                        R.plurals.charges_count,
                        country.chargeCount,
                        country.chargeCount
                    ),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = palette.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(palette: CarColorPalette) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = stringResource(R.string.no_countries_found),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Get the localized country name for a given ISO country code.
 * Falls back to the country code if localization fails.
 */
private fun getLocalizedCountryName(countryCode: String): String {
    return try {
        Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != countryCode } ?: countryCode
    } catch (e: Exception) {
        countryCode
    }
}
