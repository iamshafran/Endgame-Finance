package com.endgamefinance.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Tactical/HUD shape language (owner direction 2026-07-15, Marathon/CP2077
 * inspiration board): cut corners instead of round ones, asymmetric on panel
 * surfaces so cards read as clipped tactical plates, not soft bubbles.
 * Applied through MaterialTheme.shapes, so every Card, sheet, menu, and
 * text field in the app picks it up without per-screen changes.
 */
val EndgameShapes = Shapes(
    // Text fields, menus, small chips
    extraSmall = CutCornerShape(4.dp),
    // Chips, small buttons
    small = CutCornerShape(6.dp),
    // Cards — the signature asymmetric notch
    medium = CutCornerShape(
        topStart = 14.dp, topEnd = 4.dp,
        bottomEnd = 14.dp, bottomStart = 4.dp,
    ),
    // FABs, large components
    large = CutCornerShape(
        topStart = 16.dp, topEnd = 4.dp,
        bottomEnd = 16.dp, bottomStart = 4.dp,
    ),
    // Bottom sheets: hard cut on one shoulder only
    extraLarge = CutCornerShape(
        topStart = 24.dp, topEnd = 6.dp,
        bottomEnd = 0.dp, bottomStart = 0.dp,
    ),
)
