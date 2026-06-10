package com.matedroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Merge
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R

/** Row of two 50/50 outlined buttons: Add leg + Merge trip. Shown below the legs list. */
@Composable
fun TripEditActions(
    onAddLeg: () -> Unit,
    onMergeTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onAddLeg,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            ButtonLabel(stringResource(R.string.trip_edit_add_leg))
        }
        OutlinedButton(
            onClick = onMergeTrip,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            ButtonLabel(stringResource(R.string.trip_edit_merge_trip))
        }
    }
}

/** Single-line label that shrinks to fit the space left by the icon in a 50%-width button. */
@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 0.5.sp)
    )
}
