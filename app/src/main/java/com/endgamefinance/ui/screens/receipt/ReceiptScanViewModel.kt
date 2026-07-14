package com.endgamefinance.ui.screens.receipt

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.model.CategoryChoice
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.data.ocr.ReceiptOcr
import com.endgamefinance.data.ocr.ReceiptSplitter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One editable proposed split in the review list. */
data class ReceiptLine(
    val key: String,
    val description: String,
    val amountCents: Long,
    val categoryId: String?,
)

sealed interface ReceiptUi {
    data object Idle : ReceiptUi
    data class Working(val label: String) : ReceiptUi
    data object Review : ReceiptUi
    data object Saved : ReceiptUi
    data class Failed(val message: String) : ReceiptUi
}

class ReceiptScanViewModel(
    private val db: EndgameDatabase,
    private val appContext: Context,
) : ViewModel() {

    private val _phase = MutableStateFlow<ReceiptUi>(ReceiptUi.Idle)
    val phase: StateFlow<ReceiptUi> = _phase.asStateFlow()

    private val _merchant = MutableStateFlow("")
    val merchant: StateFlow<String> = _merchant.asStateFlow()

    private val _lines = MutableStateFlow<List<ReceiptLine>>(emptyList())
    val lines: StateFlow<List<ReceiptLine>> = _lines.asStateFlow()

    private val _accountId = MutableStateFlow<String?>(null)
    val accountId: StateFlow<String?> = _accountId.asStateFlow()

    /** Purchase time — from the receipt if the model read one, else now. */
    private val _timestamp = MutableStateFlow(System.currentTimeMillis())
    val timestamp: StateFlow<Long> = _timestamp.asStateFlow()
    /** True when the date came off the receipt (vs. defaulting to today). */
    private val _dateFromReceipt = MutableStateFlow(false)
    val dateFromReceipt: StateFlow<Boolean> = _dateFromReceipt.asStateFlow()

    // Options for the review dropdowns, loaded once per scan.
    private val _accounts = MutableStateFlow<List<Pair<String?, String>>>(emptyList())
    val accounts: StateFlow<List<Pair<String?, String>>> = _accounts.asStateFlow()
    private val _categoryOptions =
        MutableStateFlow<List<com.endgamefinance.ui.components.CategoryPickItem>>(emptyList())
    val categoryOptions: StateFlow<List<com.endgamefinance.ui.components.CategoryPickItem>> =
        _categoryOptions.asStateFlow()

    fun onImagePicked(uri: Uri) {
        _phase.value = ReceiptUi.Working("Reading the receipt…")
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.Default) {
                    ReceiptOcr.recognize(appContext, uri)
                }
                if (text.isBlank()) {
                    _phase.value = ReceiptUi.Failed(
                        "No readable text in that image — try a sharper, well-lit photo.",
                    )
                    return@launch
                }

                val allCats = db.categoryDao().getAllOnce()
                val expense = allCats.filter { it.type == Category.TYPE_EXPENSE }
                _categoryOptions.value =
                    com.endgamefinance.ui.components.categoryPickItems(expense)

                val accts = db.accountDao().getAllOnce().filter { it.isActive }
                _accounts.value = accts.map { it.id as String? to it.name }
                _accountId.value = lastUsedAccountId()?.takeIf { id -> accts.any { it.id == id } }
                    ?: accts.firstOrNull { it.type == com.endgamefinance.data.db.entity.Account.TYPE_ASSET }?.id
                    ?: accts.firstOrNull()?.id

                _phase.value = ReceiptUi.Working("Splitting line items…")
                val proposal = ReceiptSplitter.split(appContext, text, expense.map { it.name })
                _merchant.value = proposal.merchant.orEmpty()
                _timestamp.value = proposal.timestamp ?: System.currentTimeMillis()
                _dateFromReceipt.value = proposal.timestamp != null
                _lines.value = proposal.lines.map { line ->
                    ReceiptLine(
                        key = UUID.randomUUID().toString(),
                        description = line.description,
                        amountCents = line.amountCents,
                        categoryId = matchCategory(line.categoryName, expense),
                    )
                }
                if (_lines.value.isEmpty()) {
                    _phase.value = ReceiptUi.Failed(
                        "Couldn't pick out any line items. You can still add them by hand " +
                            "from a normal transaction entry.",
                    )
                    return@launch
                }
                _phase.value = ReceiptUi.Review
            } catch (e: Exception) {
                _phase.value = ReceiptUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun matchCategory(name: String?, cats: List<Category>): String? {
        if (name.isNullOrBlank()) return null
        val n = name.lowercase().trim()
        return cats.firstOrNull { it.name.lowercase() == n }?.id
            ?: cats.firstOrNull {
                val cn = it.name.lowercase()
                cn.length >= 3 && (cn.contains(n) || n.contains(cn))
            }?.id
    }

    fun setMerchant(v: String) { _merchant.value = v }
    fun setAccount(id: String?) { _accountId.value = id }
    fun setTimestamp(ms: Long) { _timestamp.value = ms; _dateFromReceipt.value = true }

    fun updateAmount(key: String, cents: Long) {
        _lines.value = _lines.value.map { if (it.key == key) it.copy(amountCents = cents) else it }
    }

    fun updateCategory(key: String, categoryId: String?) {
        _lines.value = _lines.value.map { if (it.key == key) it.copy(categoryId = categoryId) else it }
    }

    fun removeLine(key: String) {
        _lines.value = _lines.value.filterNot { it.key == key }
    }

    fun addLine() {
        _lines.value = _lines.value + ReceiptLine(UUID.randomUUID().toString(), "Item", 0L, null)
    }

    fun totalCents(): Long = _lines.value.sumOf { it.amountCents }

    fun save() {
        val accId = _accountId.value
        if (accId == null) {
            _phase.value = ReceiptUi.Failed("Pick an account for this receipt first.")
            return
        }
        val payable = _lines.value.filter { it.amountCents > 0 }
        if (payable.isEmpty()) {
            _phase.value = ReceiptUi.Failed("Every line is zero — nothing to save.")
            return
        }
        _phase.value = ReceiptUi.Working("Saving…")
        viewModelScope.launch {
            try {
                val txId = UUID.randomUUID().toString()
                db.transactionDao().insertWithSplits(
                    TransactionEntity(
                        id = txId,
                        accountId = accId,
                        timestamp = _timestamp.value,
                        payee = _merchant.value.ifBlank { "Receipt" },
                        notes = "Scanned receipt",
                        type = "expense",
                        isCleared = false,
                    ),
                    payable.map { line ->
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = line.categoryId,
                            amount = line.amountCents,
                        )
                    },
                )
                rememberAccount(accId)
                _phase.value = ReceiptUi.Saved
            } catch (e: Exception) {
                _phase.value = ReceiptUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun reset() {
        _merchant.value = ""
        _lines.value = emptyList()
        _accountId.value = null
        _timestamp.value = System.currentTimeMillis()
        _dateFromReceipt.value = false
        _phase.value = ReceiptUi.Idle
    }

    private val entryPrefs =
        appContext.getSharedPreferences("entry_prefs", Context.MODE_PRIVATE)
    private fun lastUsedAccountId(): String? = entryPrefs.getString("last_account_id", null)
    private fun rememberAccount(id: String) =
        entryPrefs.edit().putString("last_account_id", id).apply()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReceiptScanViewModel(DatabaseProvider.get(context), context.applicationContext)
            }
        }
    }
}
