package com.endgamefinance.ui.screens.entry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.TransactionAudit
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.model.CategoryChoice
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.data.repo.LedgerRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Prefill data offered when the user picks a payee suggestion. */
data class PayeePrefill(val amountCents: Long?, val categoryId: String?)

/** Everything needed to populate the form in edit mode. */
data class LoadedTransaction(
    val entity: TransactionEntity,
    val splits: List<TransactionSplit>,
    val tagIds: List<String>,
)

class TransactionEntryViewModel(
    private val db: EndgameDatabase,
    private val appContext: Context,
) : ViewModel() {

    private val repo = LedgerRepository(db)
    private val prefs = appContext.getSharedPreferences("entry_prefs", Context.MODE_PRIVATE)

    /** The account last posted to — the default for new entries. */
    fun lastUsedAccountId(): String? = prefs.getString("last_account_id", null)

    private fun rememberAccount(accountId: String) {
        prefs.edit().putString("last_account_id", accountId).apply()
    }

    /** Category ids most used in the last 90 days, for one-tap chips. */
    val recentCategoryIds = db.transactionDao()
        .observeRecentCategoryIds(System.currentTimeMillis() - 90L * 86_400_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Inline tag creation from the entry form. */
    fun createTag(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            db.tagDao().insert(
                com.endgamefinance.data.db.entity.Tag(id = id, name = name.trim()),
            )
            onCreated(id)
        }
    }

    val accounts = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = db.tagDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.endgamefinance.ui.components.CategoryPickItem>> =
        kotlinx.coroutines.flow.combine(
            db.categoryDao().observeAll(),
            db.categoryGroupDao().observeAll(),
        ) { cats, groups -> com.endgamefinance.ui.components.categoryPickItems(cats, groups) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _payeeSuggestions = MutableStateFlow<List<String>>(emptyList())
    val payeeSuggestions: StateFlow<List<String>> = _payeeSuggestions.asStateFlow()

    fun searchPayees(query: String) {
        if (query.length < 2) {
            _payeeSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            _payeeSuggestions.value =
                db.transactionDao().suggestPayees(query, LedgerRepository.STARTING_BALANCE_PAYEE)
        }
    }

    fun clearPayeeSuggestions() {
        _payeeSuggestions.value = emptyList()
    }

    // Fuzzy payee→category profile (M7.4). Built lazily from history; exact
    // matches still win via latestForPayee below.
    private var suggester: com.endgamefinance.data.ai.CategorySuggester? = null

    private suspend fun suggester(): com.endgamefinance.data.ai.CategorySuggester =
        suggester ?: com.endgamefinance.data.ai.CategorySuggester.build(
            db.transactionDao().payeeCategoryHistory().map { it.payee to it.categoryId },
        ).also { suggester = it }

    /** Last amount/category used with this payee, for one-tap prefill. Falls
     *  back to the fuzzy history profile when the payee is new but similar to
     *  a known one ("Sainsburys Hemel" → Groceries). */
    suspend fun prefillForPayee(payee: String): PayeePrefill {
        val last = db.transactionDao().latestForPayee(payee)
        if (last != null) {
            val splits = db.transactionDao().splitsFor(last.id)
            return PayeePrefill(
                amountCents = splits.sumOf { it.amount }.takeIf { it > 0 },
                categoryId = splits.firstOrNull()?.categoryId
                    ?: suggester().suggest(payee)?.categoryId,
            )
        }
        return PayeePrefill(
            amountCents = null,
            categoryId = suggester().suggest(payee)?.categoryId,
        )
    }

    suspend fun load(transactionId: String): LoadedTransaction? {
        val entity = db.transactionDao().getById(transactionId) ?: return null
        return LoadedTransaction(
            entity = entity,
            splits = db.transactionDao().splitsFor(transactionId),
            tagIds = db.transactionDao().tagIdsFor(transactionId),
        )
    }

    fun auditFor(transactionId: String): kotlinx.coroutines.flow.Flow<List<TransactionAudit>> =
        db.transactionDao().observeAuditFor(transactionId)

    fun delete(transactionId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteTransaction(transactionId)
            onDone()
        }
    }

    /**
     * [splits] = category id (nullable) to positive amount in cents.
     * [existingId] non-null switches to edit mode with audit rows.
     */
    fun save(
        existingId: String?,
        type: String,
        accountId: String,
        toAccountId: String?,
        payee: String,
        notes: String?,
        timestamp: Long,
        splits: List<Pair<String?, Long>>,
        isCleared: Boolean,
        isShared: Boolean,
        tagIds: List<String>,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val txId = existingId ?: UUID.randomUUID().toString()
            val entity = TransactionEntity(
                id = txId,
                accountId = accountId,
                toAccountId = toAccountId,
                timestamp = timestamp,
                payee = payee.trim(),
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                type = type,
                isCleared = isCleared,
                isShared = isShared,
            )
            val splitEntities = splits.map { (categoryId, amount) ->
                TransactionSplit(
                    id = UUID.randomUUID().toString(),
                    transactionId = txId,
                    categoryId = categoryId,
                    amount = amount,
                )
            }
            if (existingId != null) {
                repo.updateTransaction(entity, splitEntities, tagIds)
            } else {
                repo.createTransaction(entity, splitEntities, tagIds)
            }
            rememberAccount(accountId)
            onSaved()
        }
    }

    /**
     * "Split by account": each (accountId, categoryId, cents) row becomes its
     * own transaction — a payment from two accounts is two ledger events.
     */
    fun saveAccountSplits(
        type: String,
        rows: List<Triple<String, String?, Long>>,
        payee: String,
        notes: String?,
        timestamp: Long,
        isCleared: Boolean,
        isShared: Boolean,
        tagIds: List<String>,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            rows.forEach { (rowAccountId, categoryId, cents) ->
                val txId = UUID.randomUUID().toString()
                repo.createTransaction(
                    TransactionEntity(
                        id = txId,
                        accountId = rowAccountId,
                        timestamp = timestamp,
                        payee = payee.trim(),
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                        type = type,
                        isCleared = isCleared,
                        isShared = isShared,
                    ),
                    listOf(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = categoryId,
                            amount = cents,
                        ),
                    ),
                    tagIds = tagIds,
                )
            }
            onSaved()
        }
    }

    fun categoryTypeFor(transactionType: String): String =
        if (transactionType == "income") Category.TYPE_INCOME else Category.TYPE_EXPENSE

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TransactionEntryViewModel(
                    DatabaseProvider.get(context),
                    context.applicationContext,
                )
            }
        }
    }
}
