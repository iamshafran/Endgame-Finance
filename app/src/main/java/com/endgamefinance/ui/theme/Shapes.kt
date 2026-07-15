package com.endgamefinance.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Marathon-style shape language (owner's reference set, 2026-07-15): HARD
 * RECTANGLES, no rounding, no cut corners. Every panel, sheet, menu, chip,
 * and field reads as a flat slotted plate straight off the loadout screen.
 * Applied through MaterialTheme.shapes so the whole app picks it up.
 */
val EndgameShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
