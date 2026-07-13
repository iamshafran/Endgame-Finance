package com.endgamefinance.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.endgamefinance.ui.theme.Spacing

/** One title treatment for every screen — size and rhythm never drift. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.padding(
            start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm,
        ),
    )
}
