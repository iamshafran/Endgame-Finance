package com.endgamefinance.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Single spacing scale for the whole app — screens must not invent their own values.
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    /**
     * Bottom padding for scrollable content on screens with a FAB: 56dp FAB +
     * 16dp margin + breathing room, so the last row scrolls clear of it.
     */
    val fabClearance: Dp = 96.dp
}
