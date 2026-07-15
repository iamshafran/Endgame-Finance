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

/** Curated ISO 4217 list; formatted with 2 fraction digits regardless of native minor units. */
private val commonCurrencies = listOf(
    "USD" to "US Dollar",
    "EUR" to "Euro",
    "GBP" to "British Pound",
    "JPY" to "Japanese Yen",
    "CAD" to "Canadian Dollar",
    "AUD" to "Australian Dollar",
    "CHF" to "Swiss Franc",
    "CNY" to "Chinese Yuan",
    "INR" to "Indian Rupee",
    "AED" to "UAE Dirham",
    "SGD" to "Singapore Dollar",
    "HKD" to "Hong Kong Dollar",
    "NZD" to "New Zealand Dollar",
    "ZAR" to "South African Rand",
    "BRL" to "Brazilian Real",
    "MXN" to "Mexican Peso",
    "SEK" to "Swedish Krona",
    "NOK" to "Norwegian Krone",
    "DKK" to "Danish Krone",
    "PLN" to "Polish Zloty",
    "LKR" to "Sri Lankan Rupee",
)

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onOpenImport: (() -> Unit)? = null,
    onOpenCapture: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = AppSettings.get(context)
    val themeMode by settings.themeMode.collectAsState()

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Settings",
        onBack = onBack,
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Appearance ----
        SettingsGroup("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            val themeOptions = listOf(
                AppSettings.THEME_SYSTEM to "System",
                AppSettings.THEME_LIGHT to "Light",
                AppSettings.THEME_DARK to "Dark",
                AppSettings.THEME_OLED to "OLED",
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
                "System follows your device's light/dark setting. OLED is dark " +
                    "mode on true black — deepest contrast, kindest to OLED screens.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val paletteName by settings.palette.collectAsState()
            Text(
                "Palette",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            com.endgamefinance.ui.components.DropdownField(
                label = "Theme palette",
                options = com.endgamefinance.ui.theme.ThemePalette.entries
                    .map { it.name to it.label },
                selectedId = paletteName,
                onSelect = { picked -> picked?.let { settings.setPalette(it) } },
            )
            Text(
                "Evergreen is the default. Cyberpunk and Marathoner are bold, " +
                    "neon looks — each still honors your light/dark choice.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val fontKey by settings.fontKey.collectAsState()
            Text(
                "Font",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            com.endgamefinance.ui.components.DropdownField(
                label = "App font",
                options = com.endgamefinance.ui.theme.AppFont.entries
                    .map { it.key to it.label },
                selectedId = fontKey,
                onSelect = { picked -> picked?.let { settings.setFontKey(it) } },
            )
            Text(
                "IBM Plex Mono is the default. IBM Plex Sans pairs sans text with " +
                    "Plex Mono digits so amounts stay column-aligned; Atkinson " +
                    "Hyperlegible is designed for low-vision clarity. Fonts ship " +
                    "with the app — nothing is downloaded.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Currency ----
        val currencyCode by settings.currencyCode.collectAsState()
        SettingsGroup("Currency") {
            com.endgamefinance.ui.components.DropdownField(
                label = "Display currency",
                options = commonCurrencies.map { (code, name) -> code to "$code · $name" },
                selectedId = currencyCode,
                onSelect = { picked -> picked?.let { settings.setCurrencyCode(it) } },
            )
            Text(
                "Changes the symbol and formatting only — it does not convert your " +
                    "amounts (there's no exchange rate; the app stays offline). " +
                    "Example: ${com.endgamefinance.util.Money.format(123456)}.",
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

        // ---- AI ----
        SettingsGroup("AI assistant") {
            AiModelSetting()
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

        // ---- Migration ----
        if (onOpenImport != null) {
            SettingsGroup("Migrate") {
                Text(
                    "Coming from Bluecoins? Import its .fydb backup directly — " +
                        "accounts, transfers, and reminders come across exactly. A " +
                        "plain CSV export works too (the on-device AI maps its columns).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenImport,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import from Bluecoins") }
            }
        }

        // ---- Notification capture (Milestone 8.1) ----
        if (onOpenCapture != null) {
            SettingsGroup("Automatic capture") {
                Text(
                    "Let Endgame draft transactions from your bank and card app " +
                        "notifications. You pick the apps, confirm each one, and it's " +
                        "all parsed on-device — nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenCapture,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Set up notification capture") }
            }
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
                "Your data never leaves this device: no analytics, no telemetry, no " +
                    "sync. The database is encrypted at rest, and backups are " +
                    "password-protected files you control. The app's only network use " +
                    "is the optional AI model download you start in the AI section — " +
                    "AI inference then runs fully on-device, offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
