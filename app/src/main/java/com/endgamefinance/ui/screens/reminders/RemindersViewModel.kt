package com.endgamefinance.ui.screens.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.repo.AccountForecast
import com.endgamefinance.data.repo.ForecastBuilder
import com.endgamefinance.data.repo.RecurringDetector
import com.endgamefinance.data.repo.RecurringSuggestion
import com.endgamefinance.data.repo.ReminderRepository
import com.endgamefinance.util.MonthUtil
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUi(
    val reminder: Reminder,
    val accountName: String,
    /** Destination account name for transfer/repayment reminders. */
    val toAccountName: String?,
    val categoryName: String?,
    /** IconCatalog key of the linked category, for the row's leading icon. */
    val categoryIcon: String? = null,
    /** True when the linked category is income-type — posts as income. */
    val isIncome: Boolean,
    /** Due today or earlier — actionable now. */
    val isDue: Boolean,
    /** Strictly past its due day — shown in error color. */
    val isOverdue: Boolean,
    /** Destination is a loan liability — posting offers a principal/interest split. */
    val isLoanPayment: Boolean = false,
)

data class RemindersUiState(
    val due: List<ReminderUi> = emptyList(),
    val upcoming: List<ReminderUi> = emptyList(),
    /** Totals across ALL reminders' next occurrences (fixed amounts only). */
    val plannedExpenses: Long = 0,
    val plannedIncome: Long = 0,
    val plannedTransfers: Long = 0,
    val variableCount: Int = 0,
) {
    val plannedNet: Long get() = plannedIncome - plannedExpenses
}

enum class Momentum { NONE, LOW, NORMAL, HIGH }

data class CalendarBill(
    val name: String,
    /** NULL when the bill's amount varies. */
    val amountCents: Long?,
)

data class CalendarDay(
    val date: LocalDate,
    val spent: Long,
    val momentum: Momentum,
    val overdueBills: List<CalendarBill>,
    val upcomingBills: List<CalendarBill>,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDay> = emptyList(),
    /** Historical average daily spend (trailing 90 days) — the momentum baseline. */
    val avgDailySpend: Long = 0,
    /** Projections spanning to the visible month's end, for day-detail balances. */
    val forecasts: List<AccountForecast> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val db: EndgameDatabase,
    context: Context,
) : ViewModel() {

    private val repo = ReminderRepository(db)
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
    private val _dismissedPayees = MutableStateFlow(
        prefs.getStringSet("dismissed_suggestions", emptySet())?.toSet() ?: emptySet(),
    )

    /** Recurring-pattern suggestions; user must confirm before a reminder exists. */
    val suggestions: StateFlow<List<RecurringSuggestion>> =
        combine(
            db.transactionDao().observeDetectorRows(),
            db.reminderDao().observeAll(),
            _dismissedPayees,
        ) { rows, reminders, dismissed ->
            RecurringDetector.detect(rows, reminders, dismissed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 30-day cash projection per asset account, live against ledger + reminders. */
    val forecasts: StateFlow<List<AccountForecast>> =
        combine(
            db.accountDao().observeActiveWithBalances(),
            db.reminderDao().observeAll(),
            db.categoryDao().observeAll(),
        ) { accounts, reminders, categories ->
            ForecastBuilder.build(
                accounts = accounts,
                reminders = reminders,
                categoriesById = categories.associateBy { it.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _calMonth = MutableStateFlow(YearMonth.now())
    val calMonth: StateFlow<YearMonth> = _calMonth.asStateFlow()
    fun calPreviousMonth() { _calMonth.value = _calMonth.value.minusMonths(1) }
    fun calNextMonth() { _calMonth.value = _calMonth.value.plusMonths(1) }

    val calendarState: StateFlow<CalendarUiState> =
        _calMonth.flatMapLatest { ym ->
            val start = MonthUtil.startMs(ym)
            val end = MonthUtil.endMs(ym)
            val now = System.currentTimeMillis()
            combine(
                db.budgetDao().observeSpendByDay(start, end),
                db.budgetDao().observeTotalSpend(now - 90L * 86_400_000L, now),
                db.reminderDao().observeAll(),
                db.accountDao().observeActiveWithBalances(),
                db.categoryDao().observeAll(),
            ) { daySpends, totalSpend90, reminders, accountsWithBalances, allCategories ->
                val avgDaily = totalSpend90 / 90
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val spentByDay = daySpends.associateBy({ it.day }, { it.spent })

                // Expand each reminder's occurrences across the visible month
                val billsByDay =
                    mutableMapOf<LocalDate, MutableList<Pair<CalendarBill, Boolean>>>()
                reminders.forEach { reminder ->
                    var current = reminder
                    var guard = 0
                    while (current.nextDueDate < end && guard < 100) {
                        if (current.nextDueDate >= start) {
                            val date = java.time.Instant.ofEpochMilli(current.nextDueDate)
                                .atZone(zone).toLocalDate()
                            val overdue = date.isBefore(today)
                            billsByDay.getOrPut(date) { mutableListOf() }
                                .add(CalendarBill(reminder.name, reminder.amount) to overdue)
                        }
                        if (current.frequency == "once") break
                        current = current.copy(
                            nextDueDate = ReminderRepository.nextOccurrence(current),
                        )
                        guard++
                    }
                }

                val days = (1..ym.lengthOfMonth()).map { dayOfMonth ->
                    val date = ym.atDay(dayOfMonth)
                    val spent = spentByDay["%04d-%02d-%02d".format(
                        date.year, date.monthValue, date.dayOfMonth,
                    )] ?: 0L
                    val momentum = when {
                        spent == 0L -> Momentum.NONE
                        avgDaily <= 0 -> Momentum.NORMAL
                        spent < avgDaily * 3 / 4 -> Momentum.LOW
                        spent <= avgDaily * 3 / 2 -> Momentum.NORMAL
                        else -> Momentum.HIGH
                    }
                    val bills = billsByDay[date].orEmpty()
                    CalendarDay(
                        date = date,
                        spent = spent,
                        momentum = momentum,
                        overdueBills = bills.filter { it.second }.map { it.first },
                        upcomingBills = bills.filter { !it.second }.map { it.first },
                    )
                }
                // Projections reach the end of the visible month (min. 1 day out)
                val horizonDays = (((end - now) / 86_400_000L) + 1).coerceAtLeast(1).toInt()
                val forecasts = ForecastBuilder.build(
                    accounts = accountsWithBalances,
                    reminders = reminders,
                    categoriesById = allCategories.associateBy { it.id },
                    nowMs = now,
                    horizonDays = horizonDays,
                )
                CalendarUiState(
                    month = ym,
                    days = days,
                    avgDailySpend = avgDaily,
                    forecasts = forecasts,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Actuals for a selected PAST day: its transactions + end-of-day balances. */
    data class PastDayDetail(
        val date: LocalDate,
        val transactions: List<com.endgamefinance.data.db.model.TransactionListItem>,
        val balances: List<com.endgamefinance.data.db.model.AccountWithBalance>,
    )

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    fun selectDay(date: LocalDate?) { _selectedDate.value = date }

    val pastDayDetail: StateFlow<PastDayDetail?> =
        _selectedDate.flatMapLatest { date ->
            if (date == null || !date.isBefore(LocalDate.now())) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                val zone = ZoneId.systemDefault()
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                db.transactionDao().observeFiltered(
                    accountId = null, payeeQuery = "", categoryId = null,
                    minCents = null, maxCents = null, startMs = start, endMs = end,
                ).map { transactions ->
                    PastDayDetail(
                        date = date,
                        transactions = transactions,
                        balances = db.accountDao().balancesAsOf(end),
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** On-device explanation for a long-pressed calendar day (Milestone 8.3). */
    data class ExplainState(
        val dateLabel: String,
        val loading: Boolean,
        val text: String? = null,
        val error: String? = null,
    )

    private val _explain = MutableStateFlow<ExplainState?>(null)
    val explain: StateFlow<ExplainState?> = _explain.asStateFlow()
    fun dismissExplain() { _explain.value = null }

    fun explainDay(day: CalendarDay) {
        if (day.spent <= 0) return
        val label = day.date.toString()
        _explain.value = ExplainState(label, loading = true)
        viewModelScope.launch {
            try {
                if (!com.endgamefinance.data.ai.AiModel.isReady(appContext)) {
                    _explain.value = ExplainState(
                        label, false,
                        error = "Download the AI model first (More → AI assistant).",
                    )
                    return@launch
                }
                val zone = ZoneId.systemDefault()
                val startMs = day.date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMs = day.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val lines = db.transactionDao().daySpendLines(startMs, endMs)
                if (lines.isEmpty()) {
                    _explain.value = ExplainState(
                        label, false,
                        text = "No itemised expenses are recorded for this day.",
                    )
                    return@launch
                }
                val text = com.endgamefinance.data.ai.AnomalyExplainer.explain(
                    appContext,
                    com.endgamefinance.data.ai.AnomalyExplainer.DayContext(
                        dateLabel = label,
                        spentCents = day.spent,
                        avgDailyCents = calendarState.value.avgDailySpend,
                        lines = lines,
                    ),
                )
                _explain.value = ExplainState(
                    label, false,
                    text = text.ifBlank { "Couldn't generate an explanation — try again." },
                )
            } catch (e: Exception) {
                _explain.value = ExplainState(
                    label, false, error = e.message ?: "Something went wrong.",
                )
            }
        }
    }

    fun dismissSuggestion(suggestion: RecurringSuggestion) {
        val updated = _dismissedPayees.value + suggestion.payee.lowercase()
        _dismissedPayees.value = updated
        prefs.edit().putStringSet("dismissed_suggestions", updated).apply()
    }

    fun acceptSuggestion(suggestion: RecurringSuggestion) {
        viewModelScope.launch {
            db.reminderDao().insert(
                Reminder(
                    id = UUID.randomUUID().toString(),
                    name = suggestion.payee,
                    categoryId = suggestion.categoryId,
                    accountId = suggestion.accountId,
                    amount = suggestion.amountCents,
                    frequency = suggestion.frequency,
                    frequencyInterval = suggestion.frequencyInterval,
                    anchorDay = if (suggestion.frequency == "monthly" || suggestion.frequency == "yearly") {
                        java.time.Instant.ofEpochMilli(suggestion.nextDueDate)
                            .atZone(ZoneId.systemDefault()).dayOfMonth
                    } else null,
                    nextDueDate = suggestion.nextDueDate,
                    isAutoPost = false,
                    isAutoDetected = true,
                ),
            )
        }
    }

    val uiState: StateFlow<RemindersUiState> =
        combine(
            db.reminderDao().observeAll(),
            db.accountDao().observeActive(),
            db.categoryDao().observeAll(),
        ) { reminders, accounts, categories ->
            val zone = ZoneId.systemDefault()
            val startOfToday = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val accountNames = accounts.associateBy({ it.id }, { it.name })
            val accountsById = accounts.associateBy { it.id }
            val categoriesById = categories.associateBy { it.id }
            val ui = reminders.map { r ->
                val category = r.categoryId?.let { categoriesById[it] }
                val destination = r.toAccountId?.let { accountsById[it] }
                ReminderUi(
                    reminder = r,
                    accountName = accountNames[r.accountId] ?: "(archived account)",
                    toAccountName = r.toAccountId?.let { accountNames[it] ?: "(archived account)" },
                    categoryName = category?.name,
                    categoryIcon = category?.icon,
                    isIncome = r.toAccountId == null &&
                        category?.type == com.endgamefinance.data.db.entity.Category.TYPE_INCOME,
                    isDue = r.nextDueDate < endOfToday,
                    isOverdue = r.nextDueDate < startOfToday,
                    isLoanPayment = destination?.type ==
                        com.endgamefinance.data.db.entity.Account.TYPE_LIABILITY &&
                        destination.originalPrincipal != null,
                )
            }
            RemindersUiState(
                due = ui.filter { it.isDue },
                upcoming = ui.filter { !it.isDue },
                plannedExpenses = ui.filter {
                    it.toAccountName == null && !it.isIncome
                }.sumOf { it.reminder.amount ?: 0L },
                plannedIncome = ui.filter { it.isIncome }
                    .sumOf { it.reminder.amount ?: 0L },
                plannedTransfers = ui.filter { it.toAccountName != null }
                    .sumOf { it.reminder.amount ?: 0L },
                variableCount = ui.count { it.reminder.amount == null },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    val accounts = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryGroups = db.categoryGroupDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun post(reminder: Reminder, amountOverrideCents: Long? = null) {
        viewModelScope.launch {
            try {
                repo.post(reminder, amountOverrideCents)
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
            }
        }
    }

    fun skip(reminder: Reminder) {
        viewModelScope.launch { repo.skip(reminder) }
    }

    /** Estimates this payment's interest from the loan's history (Milestone 8.4). */
    suspend fun estimateLoanInterest(
        loanAccountId: String,
        paymentCents: Long,
    ): com.endgamefinance.data.ai.LoanInterestEstimator.Estimate {
        val history = db.transactionDao().loanPaymentHistory(loanAccountId)
        return com.endgamefinance.data.ai.LoanInterestEstimator.estimate(
            appContext, paymentCents, history,
        )
    }

    /** A reasonable default interest category (named like interest/finance/borrowing). */
    fun defaultInterestCategoryId(): String? {
        val expense = categories.value.filter {
            it.type == com.endgamefinance.data.db.entity.Category.TYPE_EXPENSE
        }
        val keywords = listOf("interest", "finance", "borrow", "loan")
        return expense.firstOrNull { c ->
            keywords.any { c.name.contains(it, ignoreCase = true) }
        }?.id
    }

    fun postLoanPayment(
        reminder: Reminder,
        paymentCents: Long,
        interestCents: Long,
        interestCategoryId: String?,
    ) {
        viewModelScope.launch {
            try {
                repo.postLoanPayment(reminder, paymentCents, interestCents, interestCategoryId)
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
            }
        }
    }

    suspend fun getReminder(id: String): Reminder? = db.reminderDao().getById(id)

    fun save(
        existingId: String?,
        name: String,
        accountId: String,
        toAccountId: String?,
        categoryId: String?,
        amountCents: Long?,
        frequency: String,
        frequencyInterval: Int,
        nextDueDate: Long,
        isAutoPost: Boolean,
    ) {
        // Month-based cadences anchor to the chosen due day so month-end bills don't drift
        val anchorDay = if (frequency == "monthly" || frequency == "yearly") {
            java.time.Instant.ofEpochMilli(nextDueDate)
                .atZone(ZoneId.systemDefault()).dayOfMonth
        } else null
        val interval = frequencyInterval.coerceAtLeast(1)
        // A transfer reminder carries no category — the split must be category-less
        val effectiveCategoryId = if (toAccountId != null) null else categoryId
        viewModelScope.launch {
            if (existingId != null) {
                db.reminderDao().getById(existingId)?.let { existing ->
                    db.reminderDao().update(
                        existing.copy(
                            name = name.trim(),
                            accountId = accountId,
                            toAccountId = toAccountId,
                            categoryId = effectiveCategoryId,
                            amount = amountCents,
                            frequency = frequency,
                            frequencyInterval = interval,
                            anchorDay = anchorDay,
                            nextDueDate = nextDueDate,
                            isAutoPost = isAutoPost,
                        ),
                    )
                }
            } else {
                db.reminderDao().insert(
                    Reminder(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        categoryId = effectiveCategoryId,
                        accountId = accountId,
                        toAccountId = toAccountId,
                        amount = amountCents,
                        frequency = frequency,
                        frequencyInterval = interval,
                        anchorDay = anchorDay,
                        nextDueDate = nextDueDate,
                        isAutoPost = isAutoPost,
                    ),
                )
            }
        }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch { db.reminderDao().delete(reminder) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RemindersViewModel(DatabaseProvider.get(context), context.applicationContext)
            }
        }
    }
}
