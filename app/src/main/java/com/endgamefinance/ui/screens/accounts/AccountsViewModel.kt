package com.endgamefinance.ui.screens.accounts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.model.AccountWithBalance
import com.endgamefinance.data.repo.LedgerRepository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val netWorthCents: Long = 0,
)

class AccountsViewModel(private val db: EndgameDatabase) : ViewModel() {

    private val repo = LedgerRepository(db)

    val uiState: StateFlow<AccountsUiState> =
        db.accountDao().observeActiveWithBalances()
            .map { list -> AccountsUiState(accounts = list, netWorthCents = list.sumOf { it.balance }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    suspend fun getAccount(id: String): Account? = db.accountDao().getById(id)

    /** [initialBalanceCents] is signed, in the app's convention (debt/overdraft negative). */
    fun createAccount(
        name: String,
        type: String,
        creditLimitCents: Long?,
        originalPrincipalCents: Long?,
        initialBalanceCents: Long,
    ) {
        viewModelScope.launch {
            repo.createAccount(
                Account(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = type,
                    creditLimit = creditLimitCents,
                    originalPrincipal = originalPrincipalCents,
                ),
                initialBalanceCents = initialBalanceCents,
            )
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { repo.updateAccount(account) }
    }

    fun archiveAccount(account: Account) {
        viewModelScope.launch { repo.archiveAccount(account) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountsViewModel(DatabaseProvider.get(context)) }
        }
    }
}
