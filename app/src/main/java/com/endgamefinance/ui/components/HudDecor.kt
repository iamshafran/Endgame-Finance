package com.endgamefinance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.tabular

/** Black-or-white, whichever is legible on [background] (loadout-chip rule). */
fun onChipColor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF0A0C10) else Color(0xFFF5F5F5)

/**
 * Flat price chip straight off the black-market screens: solid accent
 * rectangle, black (or white) tabular text, zero rounding.
 */
@Composable
fun AmountChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(color, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.tabular.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = onChipColor(color),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/**
 * Category icon tile: black glyph on a solid type-colored square —
 * income wears the palette's gain, expenses its loss, transfers its
 * transfer color (owner rule 2026-07-15).
 */
@Composable
fun CategoryIconTile(
    icon: ImageVector,
    background: Color,
    modifier: Modifier = Modifier,
    tileSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(tileSize)
            .background(background, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = onChipColor(background),
            modifier = Modifier.size(iconSize),
        )
    }
}
