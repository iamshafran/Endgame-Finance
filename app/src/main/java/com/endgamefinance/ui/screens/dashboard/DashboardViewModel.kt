package com.endgamefinance.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.repo.BudgetRepository
import com.endgamefinance.data.repo.SafeToSpend
import com.endgamefinance.data.repo.SafeToSpendCalculator
import com.endgamefinance.util.MonthUtil
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val safeToSpend: SafeToSpend? = null,
    val netWorth: Long = 0,
    val dueBillCount: Int = 0,
)

class DashboardViewModel(private val db: EndgameDatabase) : ViewModel() {

    private val budgetRepo = BudgetRepository(db)

    /** Trend data — snapshots only, never live-derived (acceptance criterion). */
    val snapshots = db.netWorthSnapshotDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<DashboardUiState> =
        combine(
            db.accountDao().observeActiveWithBalances(),
            db.envelopeDao().observeAll(),
            db.reminderDao().observeAll(),
            db.categoryDao().observeAll(),
            db.budgetDao().observeForMonth(MonthUtil.key(YearMonth.now())),
        ) { accounts, envelopes, reminders, categories, budgets ->
            val monthKey = MonthUtil.key(YearMonth.now())
            val history = db.budgetDao().spentByCategoryMonth()
            val allBudgets = db.budgetDao().allOnce()
            val carryIn = budgetRepo.carryInFor(monthKey, allBudgets, history)
            val spentThisMonth = history
                .filter { it.month == monthKey }
                .associateBy({ it.categoryId }, { it.spent })

            val sts = SafeToSpendCalculator.calculate(
                accounts = accounts,
                envelopes = envelopes,
                reminders = reminders,
                categoriesById = categories.associateBy { it.id },
                monthBudgets = budgets,
                carryInByCategory = carryIn,
                spentByCategory = spentThisMonth,
            )
            val now = System.currentTimeMillis()
            DashboardUiState(
                safeToSpend = sts,
                netWorth = accounts.sumOf { it.balance },
                dueBillCount = reminders.count { it.nextDueDate <= now },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(DatabaseProvider.get(context)) }
        }
    }
}
