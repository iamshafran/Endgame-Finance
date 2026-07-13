package com.endgamefinance.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.endgamefinance.BuildConfig
import com.endgamefinance.security.AppSettings
import com.endgamefinance.ui.components.ScreenTitle
import com.endgamefinance.ui.screens.budget.BudgetMode
import com.endgamefinance.ui.screens.budget.BudgetPrefs
import com.endgamefinance.ui.theme.Spacing

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = AppSettings.get(context)
    val themeMode by settings.themeMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Settings")

        // ---- Appearance ----
        SettingsGroup("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            val themeOptions = listOf(
                AppSettings.THEME_SYSTEM to "System",
                AppSettings.THEME_LIGHT to "Light",
                AppSettings.THEME_DARK to "Dark",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = themeMode == value,
                        onClick = { settings.setThemeMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                    ) { Text(label) }
                }
            }
            Text(
                "System follows your device's light/dark setting.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Budgeting ----
        var budgetMode by remember { mutableStateOf(BudgetPrefs(context.applicationContext).mode.value) }
        SettingsGroup("Budgeting") {
            Text("Default budget mode", style = MaterialTheme.typography.bodyLarge)
            val modeOptions = listOf(
                BudgetMode.ZERO_BASED to "Zero-Based",
                BudgetMode.CASH_FLOW to "Cash-Flow",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = budgetMode == value,
                        onClick = {
                            budgetMode = value
                            BudgetPrefs(context.applicationContext).setMode(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, modeOptions.size),
                    ) { Text(label) }
                }
            }
            Text(
                "Also switchable from the Budget tab. Changing it never deletes budget data.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Security ----
        SettingsGroup("Security") {
            AppLockSettings()
        }

        // ---- Backup ----
        SettingsGroup("Backup & restore") {
            BackupReminderSetting()
            BackupSection()
        }

        // ---- About / privacy ----
        SettingsGroup("About") {
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            HorizontalDivider()
            Text(
                "Privacy",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Endgame Finance has no internet permission — your data never leaves " +
                    "this device. The database is encrypted at rest, and backups are " +
                    "password-protected files you control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs,
        ),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
