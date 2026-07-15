package com.endgamefinance.ui.screens.receipt

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.migrate.BluecoinsImport
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.components.EndgameScaffold
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

/**
 * @param onUseInEntry When set (launched from the New Transaction screen), the
 * review step hands the parsed receipt back as JSON instead of posting a
 * transaction itself — the entry form prefills from it.
 */
@Composable
fun ReceiptScanScreen(
    onBack: () -> Unit,
    onUseInEntry: ((String) -> Unit)? = null,
    viewModel: ReceiptScanViewModel =
        viewModel(factory = ReceiptScanViewModel.factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()

    // A stable file+uri for the camera to write into.
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) captureUri?.let { viewModel.onImagePicked(it) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onImagePicked(it) }
    }

    EndgameScaffold(title = "Scan a receipt", onBack = onBack) { innerPadding ->
        when (val s = phase) {
            is ReceiptUi.Idle -> IdleView(
                modifier = Modifier.padding(innerPadding),
                onCamera = {
                    val uri = newCaptureUri(context)
                    captureUri = uri
                    camera.launch(uri)
                },
                onGallery = { gallery.launch("image/*") },
            )

            is ReceiptUi.Working -> ProgressView(Modifier.padding(innerPadding), s.label)

            is ReceiptUi.Review -> ReviewView(
                modifier = Modifier.padding(innerPadding),
                viewModel = viewModel,
                onUseInEntry = onUseInEntry,
            )

            is ReceiptUi.Saved -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Saved ✓", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The receipt is in your ledger as one transaction, split across its " +
                        "line items (uncleared until you reconcile it).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Scan another") }
            }

            is ReceiptUi.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                Button(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try again") }
            }
        }
    }
}

/** DatePicker returns UTC-midnight; keep the already-chosen local time of day. */
private fun combineDateKeepTime(dateUtcMillis: Long, existing: Long): Long {
    val date = Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun newCaptureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun IdleView(modifier: Modifier, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            "Photograph a receipt and Endgame reads it on-device, then proposes a " +
                "transaction split across your categories. You review and edit every " +
                "line before it's saved — nothing posts automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Take a photo") }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
            Text("Choose an image")
        }
    }
}

@Composable
private fun ProgressView(modifier: Modifier, label: String) {
    Row(
        modifier = modifier.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Serializes the reviewed receipt for the entry form's prefill. */
private fun resultJson(
    merchant: String,
    timestamp: Long,
    accountId: String?,
    lines: List<ReceiptLine>,
): String {
    val arr = org.json.JSONArray()
    lines.filter { it.amountCents > 0 }.forEach { line ->
        arr.put(
            org.json.JSONObject()
                .put("description", line.description)
                .put("amountCents", line.amountCents)
                .put("categoryId", line.categoryId ?: org.json.JSONObject.NULL),
        )
    }
    return org.json.JSONObject()
        .put("merchant", merchant)
        .put("timestamp", timestamp)
        .put("accountId", accountId ?: org.json.JSONObject.NULL)
        .put("lines", arr)
        .toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewView(
    modifier: Modifier,
    viewModel: ReceiptScanViewModel,
    onUseInEntry: ((String) -> Unit)? = null,
) {
    val merchant by viewModel.merchant.collectAsState()
    val lines by viewModel.lines.collectAsState()
    val accountId by viewModel.accountId.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categoryOptions by viewModel.categoryOptions.collectAsState()
    val timestamp by viewModel.timestamp.collectAsState()
    val dateFromReceipt by viewModel.dateFromReceipt.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        viewModel.setTimestamp(combineDateKeepTime(it, timestamp))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = state) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(
                "Check the split, then save. Edit amounts and categories, remove wrong " +
                    "lines, or add missed ones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = merchant,
                onValueChange = viewModel::setMerchant,
                label = { Text("Merchant / payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            DropdownField(
                label = "Account",
                options = accounts,
                selectedId = accountId,
                onSelect = viewModel::setAccount,
                nullLabel = "Pick an account",
            )
        }
        item {
            OutlinedTextField(
                value = DateFormat.getDateInstance().format(Date(timestamp)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                supportingText = {
                    Text(
                        if (dateFromReceipt) "Read from the receipt — tap Set to change"
                        else "No date on the receipt — defaulting to today",
                    )
                },
                trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Set") } },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
            )
        }
        items(lines, key = { it.key }) { line ->
            LineRow(
                line = line,
                categoryOptions = categoryOptions,
                onAmount = { viewModel.updateAmount(line.key, it) },
                onCategory = { viewModel.updateCategory(line.key, it) },
                onDelete = { viewModel.removeLine(line.key) },
            )
        }
        item {
            OutlinedButton(onClick = { viewModel.addLine() }, modifier = Modifier.fillMaxWidth()) {
                Text("Add a line")
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Text(
                    Money.format(lines.sumOf { it.amountCents }),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            Button(
                onClick = {
                    if (onUseInEntry != null) {
                        onUseInEntry(resultJson(merchant, timestamp, accountId, lines))
                    } else {
                        viewModel.save()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (onUseInEntry != null) "Use in transaction" else "Save transaction")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.reset() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start over") }
        }
    }
}

@Composable
private fun LineRow(
    line: ReceiptLine,
    categoryOptions: List<com.endgamefinance.ui.components.CategoryPickItem>,
    onAmount: (Long) -> Unit,
    onCategory: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    // Local text state so typing "12.3" isn't reformatted mid-edit.
    var amountText by remember(line.key) {
        mutableStateOf(if (line.amountCents == 0L) "" else Money.formatPlain(line.amountCents))
    }
    Card {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove line")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        onAmount(BluecoinsImport.parseAmountCents(it)?.let { c -> kotlin.math.abs(c) } ?: 0L)
                    },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(0.42f),
                )
                com.endgamefinance.ui.components.CategoryPickerField(
                    label = "Category",
                    items = categoryOptions,
                    selectedId = line.categoryId,
                    onSelect = onCategory,
                    nullLabel = "Uncategorized",
                )
            }
        }
    }
}
