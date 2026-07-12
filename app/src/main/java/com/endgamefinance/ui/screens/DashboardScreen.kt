package com.endgamefinance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.ui.theme.Spacing

/**
 * Milestone 0 placeholder. The DB status line exists so first launch exercises
 * database creation and the on-disk encryption can be verified immediately.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dbStatus by produceState(initialValue = "Opening encrypted database…") {
        value = try {
            val count = DatabaseProvider.get(context).accountDao().count()
            "Encrypted database online — $count accounts"
        } catch (e: Exception) {
            "Database error: ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Net worth, budgets and insights arrive in Milestone 4.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = dbStatus,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}
