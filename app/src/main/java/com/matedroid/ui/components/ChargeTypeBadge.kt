package com.matedroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matedroid.R

/**
 * Small AC/DC charge-type badge: white bold label on a rounded colored background.
 *
 * Defaults match the fixed DC-orange / AC-green colors used on the charge detail and
 * live charge screens; palette-aware call sites pass their own [dcColor]/[acColor].
 */
@Composable
fun ChargeTypeBadge(
    isDc: Boolean,
    modifier: Modifier = Modifier,
    dcColor: Color = Color(0xFFFF9800),
    acColor: Color = Color(0xFF4CAF50),
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    val backgroundColor = if (isDc) dcColor else acColor
    val text = if (isDc) stringResource(R.string.charging_dc) else stringResource(R.string.charging_ac)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
