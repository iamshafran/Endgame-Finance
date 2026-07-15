package com.endgamefinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.Spacing
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

/** Checkered dither strip — the refs' transition texture between surfaces. */
@Composable
fun DitherStrip(
    color: Color,
    modifier: Modifier = Modifier,
    strip: Dp = 6.dp,
    cell: Dp = 3.dp,
) {
    Canvas(modifier = modifier.height(strip)) {
        val cellPx = cell.toPx()
        val cols = (size.width / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if ((row + col) % 2 == 0) {
                    drawRect(
                        color = color,
                        topLeft = Offset(col * cellPx, row * cellPx),
                        size = Size(cellPx, cellPx),
                    )
                }
            }
        }
    }
}

/** Lays text out rotated 90° so it runs down a panel edge (THE SIGIL strip). */
fun Modifier.rotateVertically(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = 0,
                maxWidth = constraints.maxHeight,
                minHeight = 0,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
            )
        }
    }
    .rotate(90f)

/** Muted repeating micro-caption for panel edges. */
@Composable
fun VerticalMicroText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = MaterialTheme.typography.labelMedium.fontSize * 0.8f,
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.rotateVertically(),
    )
}
