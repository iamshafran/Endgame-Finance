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

data class BudgetSummaryUi(
    val monthLabel: String = "",
    val spentTotal: Long = 0,
    val allocatedTotal: Long = 0,
    val slices: List<com.endgamefinance.ui.components.SpendSlice> = emptyList(),
)

data class MiniDay(
    val dayOfMonth: Int,
    /** 0 none · 1 below avg · 2 near avg · 3 above avg. */
    val momentum: Int,
    val hasBill: Boolean,
    val isToday: Boolean,
)

data class MiniCalendarUi(
    val monthLabel: String = "",
    /** Blank cells before day 1, Sunday-first. */
    val leadingBlanks: Int = 0,
    val days: List<MiniDay> = emptyList(),
)

data class TopCategory(
    val displayName: String,
    val icon: String?,
    val amount: Long,
    val share: Float,
)

class DashboardViewModel(private val db: EndgameDatabase) : ViewModel() {

    private val budgetRepo = BudgetRepository(db)

    /** Trend data — snapshots only, never live-derived (acceptance criterion). */
    val snapshots = db.netWorthSnapshotDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 6 months of income vs spending, zero-filled for silent months. */
    val cashFlow: StateFlow<List<com.endgamefinance.ui.components.MonthCashFlow>> = run {
        val monthsWindow = (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) }
        val startMs = MonthUtil.startMs(monthsWindow.first())
        combine(
            db.budgetDao().observeIncomeByMonth(startMs),
            db.budgetDao().observeSpendingByMonth(startMs),
        ) { income, spending ->
            val incomeByMonth = income.associateBy({ it.month }, { it.total })
            val spendingByMonth = spending.associateBy({ it.month }, { it.total })
            monthsWindow.map { ym ->
                val key = MonthUtil.key(ym)
                com.endgamefinance.ui.components.MonthCashFlow(
                    label = ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM")),
                    income = incomeByMonth[key] ?: 0L,
                    spending = spendingByMonth[key] ?: 0L,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Top 5 spending categories this month, with share of the month's total. */
    val topCategories: StateFlow<List<TopCategory>> = run {
        val now = YearMonth.now()
        combine(
            db.budgetDao().observeSpentByCategory(MonthUtil.startMs(now), MonthUtil.endMs(now)),
            db.categoryDao().observeAll(),
            db.categoryGroupDao().observeAll(),
        ) { spends, categories, groups ->
            val choices = com.endgamefinance.data.db.model.categoryChoices(categories, groups)
                .associateBy { it.id }
            val iconById = categories.associateBy({ it.id }, { it.icon })
            val total = spends.sumOf { it.spent }
            spends.sortedByDescending { it.spent }.take(5).map { spend ->
                TopCategory(
                    displayName = choices[spend.categoryId]?.displayName ?: "(deleted)",
                    icon = iconById[spend.categoryId],
                    amount = spend.spent,
                    share = if (total > 0) spend.spent.toFloat() / total else 0f,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Month spend split for the budget-summary donut: top 5 categories + Other. */
    val budgetSummary: StateFlow<BudgetSummaryUi> = run {
        val now = YearMonth.now()
        combine(
            db.budgetDao().observeSpentByCategory(MonthUtil.startMs(now), MonthUtil.endMs(now)),
            db.categoryDao().observeAll(),
            db.categoryGroupDao().observeAll(),
            db.budgetDao().observeForMonth(MonthUtil.key(now)),
        ) { spends, categories, groups, budgets ->
            val choices = com.endgamefinance.data.db.model.categoryChoices(categories, groups)
                .associateBy { it.id }
            val sorted = spends.sortedByDescending { it.spent }
            val top = sorted.take(5).map { spend ->
                com.endgamefinance.ui.components.SpendSlice(
                    label = choices[spend.categoryId]?.displayName ?: "(deleted)",
                    amount = spend.spent,
                )
            }
            val otherTotal = sorted.drop(5).sumOf { it.spent }
            BudgetSummaryUi(
                monthLabel = MonthUtil.label(now),
                spentTotal = spends.sumOf { it.spent },
                allocatedTotal = budgets.sumOf { it.allocatedAmount },
                slices = if (otherTotal > 0) {
                    top + com.endgamefinance.ui.components.SpendSlice("Other", otherTotal)
                } else top,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetSummaryUi())
    }

    /** Current month at a glance: spend momentum per day + bill-due dots. */
    val miniCalendar: StateFlow<MiniCalendarUi> = run {
        val ym = YearMonth.now()
        val start = MonthUtil.startMs(ym)
        val end = MonthUtil.endMs(ym)
        val now = System.currentTimeMillis()
        combine(
            db.budgetDao().observeSpendByDay(start, end),
            db.budgetDao().observeTotalSpend(now - 90L * 86_400_000L, now),
            db.reminderDao().observeAll(),
        ) { daySpends, totalSpend90, reminders ->
            val avgDaily = totalSpend90 / 90
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now()
            val spentByDay = daySpends.associateBy({ it.day }, { it.spent })
            val billDays = mutableSetOf<Int>()
            reminders.forEach { reminder ->
                var current = reminder
                var guard = 0
                while (current.nextDueDate < end && guard < 100) {
                    if (current.nextDueDate >= start) {
                        billDays += java.time.Instant.ofEpochMilli(current.nextDueDate)
                            .atZone(zone).toLocalDate().dayOfMonth
                    }
                    if (current.frequency == "once") break
                    current = current.copy(
                        nextDueDate = com.endgamefinance.data.repo.ReminderRepository
                            .nextOccurrence(current),
                    )
                    guard++
                }
            }
            val days = (1..ym.lengthOfMonth()).map { dayOfMonth ->
                val date = ym.atDay(dayOfMonth)
                val spent = spentByDay[
                    "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth),
                ] ?: 0L
                // Same thresholds as the full calendar's momentum coloring
                val momentum = when {
                    spent == 0L -> 0
                    avgDaily <= 0 -> 2
                    spent < avgDaily * 3 / 4 -> 1
                    spent <= avgDaily * 3 / 2 -> 2
                    else -> 3
                }
                MiniDay(
                    dayOfMonth = dayOfMonth,
                    momentum = momentum,
                    hasBill = dayOfMonth in billDays,
                    isToday = date == today,
                )
            }
            MiniCalendarUi(
                monthLabel = MonthUtil.label(ym),
                leadingBlanks = ym.atDay(1).dayOfWeek.value % 7,
                days = days,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiniCalendarUi())
    }

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
