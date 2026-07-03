package com.matedroid.ui.screens.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.ui.theme.CarColorPalette
import java.util.Locale

/** Sort order shared by the countries- and regions-visited screens. */
enum class GeoSortOrder(@get:StringRes val labelRes: Int) {
    FIRST_VISIT(R.string.sort_by_first_visit),   // Chronological by first visit date (default)
    ALPHABETICAL(R.string.sort_alphabetically),  // A-Z by name
    DRIVE_COUNT(R.string.sort_by_drive_count),   // Most drives first
    DISTANCE(R.string.sort_by_distance),         // Most distance first
    ENERGY(R.string.sort_by_energy),             // Most energy charged first
    CHARGES(R.string.sort_by_charges),           // Most charges first
}

/** The sort dropdown for the geo-visited screens; [onSelect] fires with the chosen order. */
@Composable
fun GeoSortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (GeoSortOrder) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        GeoSortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(stringResource(order.labelRes)) },
                onClick = { onSelect(order) }
            )
        }
    }
}

/** Icon + value chip used on the countries- and regions-visited cards. */
@Composable
fun StatChip(
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

/** Resolve an ISO country code to its display name in the current locale, falling back to the code. */
fun getLocalizedCountryName(countryCode: String): String {
    return try {
        Locale.Builder().setRegion(countryCode).build().getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != countryCode } ?: countryCode
    } catch (e: Exception) {
        countryCode
    }
}
