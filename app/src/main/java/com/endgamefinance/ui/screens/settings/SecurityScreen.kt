package com.endgamefinance.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.endgamefinance.security.AppLock
import com.endgamefinance.security.BackupPrefs
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.theme.Spacing

private val timeoutChoices = listOf(
    0 to "Immediately",
    1 to "After 1 minute",
    5 to "After 5 minutes",
    15 to "After 15 minutes",
)

@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled(context)) }
    var timeout by remember { mutableStateOf(AppLock.timeoutMinutes(context)) }

    val canAuthenticate = remember {
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Security & Backup", style = MaterialTheme.typography.headlineMedium)

        Text("App lock", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Require unlock", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Biometric or device PIN gates the app. Your data stays " +
                        "encrypted either way — forgetting your PIN never loses data.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = lockEnabled,
                enabled = canAuthenticate,
                onCheckedChange = {
                    lockEnabled = it
                    AppLock.setEnabled(context, it)
                },
            )
        }
        if (!canAuthenticate) {
            Text(
                "Set up a screen lock (PIN/pattern/biometric) in Android settings " +
                    "to enable the app lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (lockEnabled) {
            DropdownField(
                label = "Lock after backgrounding",
                options = timeoutChoices.map { (min, label) -> min.toString() to label },
                selectedId = timeout.toString(),
                onSelect = { picked ->
                    picked?.toIntOrNull()?.let {
                        timeout = it
                        AppLock.setTimeoutMinutes(context, it)
                    }
                },
            )
        }

        BackupSection()
    }
}

private val nudgeChoices = listOf(
    0 to "Never remind me",
    7 to "Every 7 days",
    30 to "Every 30 days",
    90 to "Every 90 days",
)

@Composable
internal fun BackupSection(
    viewModel: SecurityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = SecurityViewModel.factory(LocalContext.current),
        ),
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    var nudgeDays by remember { mutableStateOf(BackupPrefs.nudgeDays(context)) }
    var passwordDialogFor by remember { mutableStateOf<String?>(null) } // "backup" | "restore"
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val fileStamp = remember {
        java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) viewModel.writeBackup(uri, password)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            restoreUri = uri
            passwordDialogFor = "restore"
        }
    }

    Text("Backup", style = MaterialTheme.typography.titleMedium)
    val lastBackup = BackupPrefs.lastBackupAt(context)
    Text(
        text = if (lastBackup == 0L) "Never backed up yet."
        else "Last backup: " + java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
        ).format(java.util.Date(lastBackup)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = { passwordDialogFor = "backup" },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Back up now (encrypted)") }

    OutlinedButton(
        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Restore from backup…") }

    OutlinedButton(
        onClick = { csvLauncher.launch("endgame-transactions-$fileStamp.csv") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Export plain CSV (unprotected)") }
    Text(
        "The CSV is readable by anyone with the file — use the encrypted backup " +
            "for safekeeping.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    DropdownField(
        label = "Backup reminder",
        options = nudgeChoices.map { (days, label) -> days.toString() to label },
        selectedId = nudgeDays.toString(),
        onSelect = { picked ->
            picked?.toIntOrNull()?.let {
                nudgeDays = it
                BackupPrefs.setNudgeDays(context, it)
            }
        },
    )

    if (busy) {
        Text(
            "Working…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            title = { Text("Backup") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeMessage() }) { Text("OK") }
            },
        )
    }

    passwordDialogFor?.let { purpose ->
        PasswordDialog(
            isNewPassword = purpose == "backup",
            onConfirm = { password ->
                if (purpose == "backup") {
                    pendingPassword = password
                    passwordDialogFor = null
                    backupLauncher.launch("endgame-backup-$fileStamp.enc")
                } else {
                    passwordDialogFor = null
                    pendingPassword = password
                    showRestoreWarning = true
                }
            },
            onDismiss = { passwordDialogFor = null },
        )
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreWarning = false; pendingPassword = null },
            title = { Text("Replace everything?") },
            text = {
                Text(
                    "Restoring replaces ALL current data — accounts, transactions, " +
                        "budgets, reminders, everything — with the backup's contents. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreWarning = false
                        val uri = restoreUri
                        val password = pendingPassword
                        pendingPassword = null
                        if (uri != null && password != null) {
                            viewModel.restoreBackup(uri, password)
                        }
                    },
                ) { Text("Replace all data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreWarning = false; pendingPassword = null },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PasswordDialog(
    isNewPassword: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewPassword) "Choose a backup password" else "Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (isNewPassword) {
                    Text(
                        "This password is the ONLY way to open the backup. " +
                            "It is not stored anywhere — if you lose it, the backup " +
                            "is unrecoverable.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (isNewPassword) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.length < 8 && isNewPassword ->
                            error = "Use at least 8 characters"
                        isNewPassword && password != confirm ->
                            error = "Passwords don't match"
                        password.isEmpty() -> error = "Enter the password"
                        else -> onConfirm(password.toCharArray())
                    }
                },
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
