package com.endgamefinance.ui.screens.importer

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.ai.AiModel
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.migrate.BluecoinsDbImport
import com.endgamefinance.data.migrate.BluecoinsImport
import com.endgamefinance.data.migrate.ColumnMapping
import com.endgamefinance.data.migrate.CsvParser
import com.endgamefinance.ui.components.EndgameScaffold
import com.endgamefinance.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stepped import flow: pick file → (AI maps columns | read backup) → review → import → done. */
sealed interface ImportUi {
    data object PickFile : ImportUi
    data object Reading : ImportUi
    data object Mapping : ImportUi
    data class Review(
        val header: List<String>,
        val mapping: ColumnMapping,
        val rowCount: Int,
    ) : ImportUi

    /** A recognized .fydb backup, ready to import. */
    data class ReviewDb(val plan: BluecoinsDbImport.Plan) : ImportUi

    data object Importing : ImportUi
    data class Done(val summary: BluecoinsImport.Summary) : ImportUi
    data class DoneDb(val summary: BluecoinsDbImport.Summary) : ImportUi
    data class Failed(val message: String) : ImportUi
}

class ImportViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow<ImportUi>(ImportUi.PickFile)
    val state: StateFlow<ImportUi> = _state.asStateFlow()

    private var header: List<String> = emptyList()
    private var dataRows: List<List<String>> = emptyList()

    private var dbPlan: BluecoinsDbImport.Plan? = null

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        _state.value = ImportUi.Reading
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Couldn't open the file.")
                }
                if (BluecoinsDbImport.looksLikeSqlite(bytes)) {
                    // .fydb backup — deterministic, no AI mapping needed.
                    val plan = withContext(Dispatchers.IO) {
                        val tmp = java.io.File(appContext.cacheDir, "bluecoins_import.fydb")
                        tmp.writeBytes(bytes)
                        try {
                            BluecoinsDbImport.readPlan(tmp)
                        } finally {
                            tmp.delete()
                        }
                    }
                    dbPlan = plan
                    _state.value = ImportUi.ReviewDb(plan)
                    return@launch
                }
                val rows = CsvParser.parse(bytes.toString(Charsets.UTF_8))
                if (rows.size < 2) throw IllegalStateException(
                    "The file needs a header row and at least one data row.",
                )
                if (!AiModel.isReady(appContext)) throw IllegalStateException(
                    "CSV import needs the AI model (for column mapping) — download it " +
                        "in Settings, or import the .fydb backup instead, which " +
                        "doesn't need the model.",
                )
                header = rows.first()
                dataRows = rows.drop(1)
                _state.value = ImportUi.Mapping
                val mapping = ColumnMapping.infer(appContext, header, dataRows.take(3))
                _state.value = ImportUi.Review(header, mapping, dataRows.size)
            } catch (e: Exception) {
                _state.value = ImportUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun confirmDbImport() {
        val plan = dbPlan ?: return
        _state.value = ImportUi.Importing
        viewModelScope.launch {
            try {
                val db = DatabaseProvider.get(appContext)
                val summary = BluecoinsDbImport.import(db, plan)
                // Imported history changes every past balance — rebuild the trend
                com.endgamefinance.data.repo.SnapshotWriter.rebuild(db)
                _state.value = ImportUi.DoneDb(summary)
            } catch (e: Exception) {
                _state.value = ImportUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun confirmImport(mapping: ColumnMapping) {
        _state.value = ImportUi.Importing
        viewModelScope.launch {
            try {
                val db = DatabaseProvider.get(appContext)
                val summary = BluecoinsImport.import(db, mapping, dataRows)
                // Imported history changes every past balance — rebuild the trend
                com.endgamefinance.data.repo.SnapshotWriter.rebuild(db)
                _state.value = ImportUi.Done(summary)
            } catch (e: Exception) {
                _state.value = ImportUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun reset() {
        header = emptyList()
        dataRows = emptyList()
        dbPlan = null
        _state.value = ImportUi.PickFile
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(context.applicationContext) }
        }
    }
}

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.factory(LocalContext.current)),
) {
    val ui by viewModel.state.collectAsState()
    val modelState by AiModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { viewModel.onFilePicked(it) }

    EndgameScaffold(title = "Import from Bluecoins", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (val s = ui) {
                is ImportUi.PickFile -> {
                    Text(
                        "Bring your history over from Bluecoins. Accounts, categories, " +
                            "transfers, starting balances, and scheduled bills are " +
                            "recreated here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                "Best: the .fydb backup",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "In Bluecoins, tap Settings → Backup / Restore → " +
                                    "Local Backup, then pick the .fydb file here. It " +
                                    "carries account types, transfer pairs, and " +
                                    "reminders exactly — no AI needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "*/*", // .fydb reports as octet-stream on most devices
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose backup or CSV file") }
                    if (modelState !is com.endgamefinance.data.ai.ModelState.Ready) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Text(
                                    "A .fydb backup imports without the AI model. A loose " +
                                        "CSV export needs it (the model maps the columns) — " +
                                        "and it isn't downloaded yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedButton(onClick = onOpenSettings) {
                                    Text("Open AI settings")
                                }
                            }
                        }
                    }
                    Text(
                        "Importing is safe to re-run — rows that already exist are " +
                            "skipped, not duplicated.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ImportUi.Reading -> Progress("Reading the file…")
                is ImportUi.Mapping -> Progress("AI is reading the column names…")

                is ImportUi.Review -> {
                    Text("Column mapping", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${s.rowCount} rows found. The AI mapped the file's columns like " +
                            "this — check it looks right:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            MappingRow("Date", s.header.getOrNull(s.mapping.date))
                            MappingRow("Amount", s.header.getOrNull(s.mapping.amount))
                            MappingRow("Account", s.header.getOrNull(s.mapping.account))
                            MappingRow("Payee", s.mapping.payee?.let { s.header.getOrNull(it) })
                            MappingRow("Type", s.mapping.type?.let { s.header.getOrNull(it) })
                            MappingRow("Category", s.mapping.category?.let { s.header.getOrNull(it) })
                            MappingRow("Parent category", s.mapping.categoryGroup?.let { s.header.getOrNull(it) })
                            MappingRow("Notes", s.mapping.notes?.let { s.header.getOrNull(it) })
                            MappingRow("Labels → tags", s.mapping.labels?.let { s.header.getOrNull(it) })
                            MappingRow("Cleared status", s.mapping.status?.let { s.header.getOrNull(it) })
                        }
                    }
                    Button(
                        onClick = { viewModel.confirmImport(s.mapping) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import ${s.rowCount} rows") }
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick a different file") }
                }

                is ImportUi.ReviewDb -> {
                    Text("Bluecoins backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Read your backup directly — no column mapping needed. Here's " +
                            "what will be brought in:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            SummaryRow("Accounts", s.plan.accounts.size)
                            SummaryRow("Transactions", s.plan.transactions.size)
                            SummaryRow("Scheduled reminders", s.plan.reminders.size)
                        }
                    }
                    if (s.plan.warnings.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                s.plan.warnings.take(20).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (s.plan.warnings.size > 20) {
                                    Text(
                                        "…and ${s.plan.warnings.size - 20} more.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { viewModel.confirmDbImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import ${s.plan.transactions.size} transactions") }
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick a different file") }
                }

                is ImportUi.Importing -> Progress("Importing… building accounts, categories, and transactions.")

                is ImportUi.Done -> {
                    Text("Import complete ✓", style = MaterialTheme.typography.titleMedium)
                    Card {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            SummaryRow("Transactions imported", s.summary.transactionsImported)
                            SummaryRow("Transfers matched", s.summary.transfersPaired)
                            SummaryRow("Accounts created", s.summary.accountsCreated)
                            SummaryRow("Categories created", s.summary.categoriesCreated)
                            SummaryRow("Duplicates skipped", s.summary.duplicatesSkipped)
                            if (s.summary.transfersRepaired > 0) {
                                SummaryRow("Broken transfers repaired", s.summary.transfersRepaired)
                            }
                        }
                    }
                    if (s.summary.warnings.isNotEmpty()) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                s.summary.warnings.take(20).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (s.summary.warnings.size > 20) {
                                    Text(
                                        "…and ${s.summary.warnings.size - 20} more.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }

                is ImportUi.DoneDb -> {
                    Text("Import complete ✓", style = MaterialTheme.typography.titleMedium)
                    Card {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            SummaryRow("Transactions imported", s.summary.transactionsImported)
                            SummaryRow("Accounts created", s.summary.accountsCreated)
                            SummaryRow("Categories created", s.summary.categoriesCreated)
                            SummaryRow("Reminders created", s.summary.remindersCreated)
                            SummaryRow("Duplicates skipped", s.summary.duplicatesSkipped)
                        }
                    }
                    if (s.summary.warnings.isNotEmpty()) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                s.summary.warnings.take(20).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (s.summary.warnings.size > 20) {
                                    Text(
                                        "…and ${s.summary.warnings.size - 20} more.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }

                is ImportUi.Failed -> {
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
}

@Composable
private fun Progress(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MappingRow(field: String, column: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            field,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(0.45f),
        )
        Text(
            column ?: "— not in file",
            style = MaterialTheme.typography.bodyMedium,
            color = if (column != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    HorizontalDivider()
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
