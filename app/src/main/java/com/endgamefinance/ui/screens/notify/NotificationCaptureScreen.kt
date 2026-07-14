package com.endgamefinance.ui.screens.notify

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.notify.NotificationCapturePrefs
import com.endgamefinance.ui.components.EndgameScaffold
import com.endgamefinance.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

class NotificationCaptureViewModel(private val appContext: Context) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    val enabled = NotificationCapturePrefs.enabled(appContext)
    val whitelist = NotificationCapturePrefs.whitelist(appContext)
    val autoPost = NotificationCapturePrefs.autoPost(appContext)

    /** Status/result line for the sample-capture preview. */
    private val _sampleStatus = MutableStateFlow<String?>(null)
    val sampleStatus: StateFlow<String?> = _sampleStatus.asStateFlow()
    private val _sampleRunning = MutableStateFlow(false)
    val sampleRunning: StateFlow<Boolean> = _sampleRunning.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadLaunchableApps(appContext) }
        }
    }

    /**
     * Runs a representative bank notification through the real pipeline —
     * on-device parse → resolve against the user's data → confirm notification.
     * Lets the feature be tested without waiting for an actual purchase.
     */
    fun sendSampleCapture() {
        if (_sampleRunning.value) return
        if (!com.endgamefinance.data.ai.AiModel.isReady(appContext)) {
            _sampleStatus.value =
                "Download the AI model first (Settings → AI assistant) — it does the parsing."
            return
        }
        _sampleRunning.value = true
        _sampleStatus.value = "Reading a sample notification on-device…"
        viewModelScope.launch {
            try {
                val parsed = com.endgamefinance.data.notify.NotificationParser.parse(
                    appContext,
                    title = "Santander",
                    text = "You spent £12.99 at NETFLIX.COM on your card ending 1234.",
                )
                if (parsed == null) {
                    _sampleStatus.value =
                        "The model didn't read the sample as a transaction — try again."
                    return@launch
                }
                val repo = com.endgamefinance.data.notify.NotificationCaptureRepository(
                    com.endgamefinance.data.db.DatabaseProvider.get(appContext),
                    appContext,
                )
                val resolved = repo.resolve(parsed)
                if (resolved.accountId == null) {
                    _sampleStatus.value =
                        "Parsed ${resolved.payee} · " +
                        com.endgamefinance.util.Money.format(resolved.amountCents) +
                        ", but you have no accounts yet — add one, then Confirm will work."
                    return@launch
                }
                com.endgamefinance.notifications.CaptureNotifier.notifyConfirm(appContext, resolved)
                val where = if (resolved.accountMatched) {
                    resolved.accountName
                } else {
                    "${resolved.accountName} (default — check it)"
                }
                _sampleStatus.value =
                    "Sent a confirm notification: ${resolved.payee} · " +
                    "${com.endgamefinance.util.Money.format(resolved.amountCents)} · $where. " +
                    "Open your notification shade and tap Confirm."
            } catch (e: Exception) {
                _sampleStatus.value = "Sample failed: ${e.message}"
            } finally {
                _sampleRunning.value = false
            }
        }
    }

    fun setEnabled(v: Boolean) = NotificationCapturePrefs.setEnabled(appContext, v)
    fun setConsented() = NotificationCapturePrefs.setConsented(appContext, true)
    fun hasConsented() = NotificationCapturePrefs.hasConsented(appContext)
    fun setWhitelisted(pkg: String, v: Boolean) =
        NotificationCapturePrefs.setWhitelisted(appContext, pkg, v)
    fun setAutoPost(pkg: String, v: Boolean) =
        NotificationCapturePrefs.setAutoPost(appContext, pkg, v)

    private fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        // Enumerate launchable apps (bank/card apps, Wallet, Messages all qualify).
        // Package visibility is granted by the <queries> LAUNCHER declaration.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotificationCaptureViewModel(context.applicationContext) }
        }
    }
}

/** Returns true when THIS app currently holds notification-listener access. */
private fun hasListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@Composable
fun NotificationCaptureScreen(
    onBack: () -> Unit,
    viewModel: NotificationCaptureViewModel =
        viewModel(factory = NotificationCaptureViewModel.factory(LocalContext.current)),
) {
    val context = LocalContext.current
    var consented by remember { mutableStateOf(viewModel.hasConsented()) }

    // Re-check OS listener access whenever we return from system settings.
    var accessGranted by remember { mutableStateOf(hasListenerAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessGranted = hasListenerAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    EndgameScaffold(title = "Capture from notifications", onBack = onBack) { innerPadding ->
        if (!consented) {
            ConsentGate(
                modifier = Modifier.padding(innerPadding),
                onAccept = { viewModel.setConsented(); consented = true },
            )
            return@EndgameScaffold
        }

        val enabled by viewModel.enabled.collectAsState()
        val whitelist by viewModel.whitelist.collectAsState()
        val autoPost by viewModel.autoPost.collectAsState()
        val apps by viewModel.apps.collectAsState()
        var query by remember { mutableStateOf("") }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    "Endgame reads notifications only from the apps you switch on below, " +
                        "and only to draft a transaction you confirm. The text is parsed " +
                        "by the on-device model and never leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // OS access status
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (accessGranted) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            if (accessGranted) "Notification access: granted"
                            else "Notification access: not granted",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (!accessGranted) {
                            Text(
                                "Android needs you to grant notification access before " +
                                    "any capture can happen. You can revoke it any time.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        }) { Text(if (accessGranted) "Manage access" else "Grant access") }
                    }
                }
            }

            // Master switch
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Capture transactions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) })
                }
            }

            // Sample preview — exercises the whole pipeline without a real purchase.
            item {
                val sampleRunning by viewModel.sampleRunning.collectAsState()
                val sampleStatus by viewModel.sampleStatus.collectAsState()
                Card {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("See how it works", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Run a sample bank notification through the on-device parser " +
                                "and post yourself a confirm notice — no purchase needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { viewModel.sendSampleCapture() },
                            enabled = !sampleRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (sampleRunning) "Working…" else "Send a sample capture") }
                        sampleStatus?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (enabled) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Find an app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        "Switch on your bank and card apps. Auto-post skips confirmation " +
                            "for that app — leave it off unless you trust its notifications.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val filtered = apps.filter {
                    query.isBlank() || it.label.contains(query, ignoreCase = true)
                }
                // Whitelisted apps float to the top for quick review.
                val ordered = filtered.sortedByDescending { it.packageName in whitelist }
                items(ordered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        whitelisted = app.packageName in whitelist,
                        autoPost = app.packageName in autoPost,
                        onWhitelist = { viewModel.setWhitelisted(app.packageName, it) },
                        onAutoPost = { viewModel.setAutoPost(app.packageName, it) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConsentGate(modifier: Modifier, onAccept: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Before you turn this on", style = MaterialTheme.typography.titleLarge)
        Card {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ConsentPoint("Android's notification access is all-or-nothing — the OS can't hand Endgame just one app's notifications.")
                ConsentPoint("Endgame narrows it itself: it ignores every app except the ones you switch on here, and never stores or reads the rest.")
                ConsentPoint("Whitelisted notifications are parsed by the on-device model only. No text, and no transaction, ever leaves your phone.")
                ConsentPoint("Nothing posts to your ledger without your tap, unless you separately enable auto-post for a specific app.")
                ConsentPoint("You can revoke notification access in Android settings, or switch this off here, at any time.")
            }
        }
        OutlinedButton(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I understand — continue")
        }
    }
}

@Composable
private fun ConsentPoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    whitelisted: Boolean,
    autoPost: Boolean,
    onWhitelist: (Boolean) -> Unit,
    onAutoPost: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            Switch(checked = whitelisted, onCheckedChange = onWhitelist)
        }
        if (whitelisted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Auto-post (no confirmation)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
                Switch(checked = autoPost, onCheckedChange = onAutoPost)
            }
        }
    }
}
