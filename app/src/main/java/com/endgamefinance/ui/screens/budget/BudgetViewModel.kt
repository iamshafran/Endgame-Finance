package com.endgamefinance.ui.screens.budget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.data.repo.BudgetRepository
import com.endgamefinance.util.Money
import com.endgamefinance.util.MonthUtil
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object BudgetMode {
    const val ZERO_BASED = "zero_based"
    const val CASH_FLOW = "cash_flow"
}

/** Budget mode is an app preference, not financial data — plain SharedPreferences. */
class BudgetPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        prefs.getString("mode", BudgetMode.ZERO_BASED) ?: BudgetMode.ZERO_BASED,
    )
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun setMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
        _mode.value = mode
    }
}

data class BudgetRowUi(
    val categoryId: String,
    val displayName: String,
    /** Bare category name (no "Group › " prefix) for grouped rendering. */
    val shortName: String,
    /** Owning category group (required by the app; null only defensively). */
    val groupId: String?,
    val groupName: String?,
    val icon: String?,
    val allocated: Long?,
    val rolloverMode: String,
    val carryIn: Long,
    val spent: Long,
    /** allocated + carryIn (0 if no budget set). */
    val available: Long,
    val pacing: String?,
    val overBudget: Boolean,
)

data class BudgetUiState(
    val month: YearMonth = YearMonth.now(),
    val monthLabel: String = "",
    val mode: String = BudgetMode.ZERO_BASED,
    val rows: List<BudgetRowUi> = emptyList(),
    val income: Long = 0,
    val allocatedTotal: Long = 0,
    val spentTotal: Long = 0,
    /** income − allocatedTotal; positive means the zero-based nag shows. */
    val unallocated: Long = 0,
    val canCopyLastMonth: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val db: EndgameDatabase,
    private val budgetPrefs: BudgetPrefs,
) : ViewModel() {

    private val repo = BudgetRepository(db)

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val uiState: StateFlow<BudgetUiState> =
        _month.flatMapLatest { ym ->
            val key = MonthUtil.key(ym)
            val start = MonthUtil.startMs(ym)
            val end = MonthUtil.endMs(ym)
            combine(
                db.budgetDao().observeForMonth(key),
                db.budgetDao().observeSpentByCategory(start, end),
                db.budgetDao().observeIncome(start, end),
                combine(
                    db.categoryDao().observeAll(),
                    db.categoryGroupDao().observeAll(),
                ) { cats, groups -> cats to groups },
                budgetPrefs.mode,
            ) { budgets, spends, income, (categories, groups), mode ->
                val allBudgets = db.budgetDao().allOnce()
                val history = db.budgetDao().spentByCategoryMonth()
                val carryIn = repo.carryInFor(key, allBudgets, history)
                val spentByCat = spends.associateBy({ it.categoryId }, { it.spent })
                val budgetByCat = budgets.associateBy { it.categoryId }

                val categoriesById = categories.associateBy { it.id }
                val groupsById = groups.associateBy { it.id }
                val rows = categoryChoices(categories, groups)
                    .filter { it.type == Category.TYPE_EXPENSE }
                    .map { choice ->
                        val budget = budgetByCat[choice.id]
                        val carry = carryIn[choice.id] ?: 0L
                        val spent = spentByCat[choice.id] ?: 0L
                        val available = (budget?.allocatedAmount ?: 0L) + carry
                        val category = categoriesById[choice.id]
                        BudgetRowUi(
                            categoryId = choice.id,
                            displayName = choice.displayName,
                            shortName = category?.name ?: choice.displayName,
                            groupId = category?.groupId,
                            groupName = category?.groupId?.let { groupsById[it]?.name },
                            icon = category?.icon,
                            allocated = budget?.allocatedAmount,
                            rolloverMode = budget?.rolloverMode ?: "reset",
                            carryIn = carry,
                            spent = spent,
                            available = available,
                            pacing = pacingFor(ym, spent, available),
                            overBudget = available > 0 && spent > available,
                        )
                    }

                val allocatedTotal = budgets.sumOf { it.allocatedAmount }
                BudgetUiState(
                    month = ym,
                    monthLabel = MonthUtil.label(ym),
                    mode = mode,
                    rows = rows,
                    income = income,
                    allocatedTotal = allocatedTotal,
                    spentTotal = rows.sumOf { it.spent },
                    unallocated = income - allocatedTotal,
                    canCopyLastMonth = budgets.isEmpty() &&
                        allBudgets.any { it.month == MonthUtil.key(ym.minusMonths(1)) },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    private fun pacingFor(ym: YearMonth, spent: Long, available: Long): String? {
        val today = LocalDate.now()
        val current = YearMonth.from(today)
        return when {
            ym > current || spent <= 0L -> null
            ym < current ->
                if (available > 0) {
                    if (spent > available) "Finished over by ${Money.format(spent - available)}"
                    else "Finished with ${Money.format(available - spent)} left"
                } else null
            else -> {
                val daysElapsed = today.dayOfMonth
                val daysInMonth = ym.lengthOfMonth()
                val projected = spent * daysInMonth / daysElapsed
                if (available > 0 && projected > available) {
                    val exceedDay = (available * daysElapsed / spent + 1)
                        .coerceAtMost(daysInMonth.toLong())
                    if (spent > available) "Over budget since day ${minOf(daysElapsed.toLong(), exceedDay)}"
                    else "On pace to exceed by day $exceedDay"
                } else {
                    "On pace for ${Money.format(projected)} this month"
                }
            }
        }
    }

    fun previousMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }

    fun setMode(mode: String) = budgetPrefs.setMode(mode)

    fun setBudget(categoryId: String, allocatedCents: Long?, rolloverMode: String) {
        viewModelScope.launch {
            repo.setBudget(categoryId, MonthUtil.key(_month.value), allocatedCents, rolloverMode)
        }
    }

    fun copyLastMonth() {
        viewModelScope.launch { repo.copyLastMonth(_month.value) }
    }

    suspend fun rollingAverage(categoryId: String): Long? =
        repo.rollingAverage(categoryId, _month.value, db.budgetDao().spentByCategoryMonth())

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BudgetViewModel(
                    DatabaseProvider.get(context),
                    BudgetPrefs(context.applicationContext),
                )
            }
        }
    }
}
