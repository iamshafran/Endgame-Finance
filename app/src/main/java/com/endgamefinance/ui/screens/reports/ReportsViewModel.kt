package com.endgamefinance.ui.screens.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.util.MonthUtil
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ReportRow(
    val categoryId: String?,
    val displayName: String,
    val icon: String?,
    val spent: Long,
    /** Share of the range's total spending, 0..1. */
    val share: Float,
)

data class RangeReport(
    val startMs: Long,
    val endMs: Long,
    val income: Long = 0,
    val spending: Long = 0,
    val rows: List<ReportRow> = emptyList(),
) {
    val net: Long get() = income - spending
}

data class YoyRow(
    val displayName: String,
    val icon: String?,
    val current: Long,
    val prior: Long,
) {
    val delta: Long get() = current - prior
}

data class YoyReport(
    val month: YearMonth = YearMonth.now(),
    val rows: List<YoyRow> = emptyList(),
    val currentTotal: Long = 0,
    val priorTotal: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val db: EndgameDatabase) : ViewModel() {

    // ---- Custom date range (works fully standalone — no AI dependency) ----

    private val _range = MutableStateFlow(
        MonthUtil.startMs(YearMonth.now()) to MonthUtil.endMs(YearMonth.now()),
    )
    val range: StateFlow<Pair<Long, Long>> = _range.asStateFlow()

    fun setRange(startMs: Long, endMs: Long) {
        if (endMs > startMs) _range.value = startMs to endMs
    }

    val rangeReport: StateFlow<RangeReport> =
        _range.flatMapLatest { (start, end) ->
            combine(
                db.budgetDao().observeSpentByCategory(start, end),
                db.budgetDao().observeUncategorizedSpend(start, end),
                db.budgetDao().observeIncome(start, end),
                db.categoryDao().observeAll(),
            ) { spends, uncategorized, income, categories ->
                val choices = categoryChoices(categories).associateBy { it.id }
                val iconById = categories.associateBy({ it.id }, { it.icon })
                val total = spends.sumOf { it.spent } + uncategorized
                val rows = buildList {
                    spends.forEach { spend ->
                        add(
                            ReportRow(
                                categoryId = spend.categoryId,
                                displayName = choices[spend.categoryId]?.displayName
                                    ?: "(deleted category)",
                                icon = iconById[spend.categoryId],
                                spent = spend.spent,
                                share = if (total > 0) spend.spent.toFloat() / total else 0f,
                            ),
                        )
                    }
                    if (uncategorized > 0) {
                        add(
                            ReportRow(
                                categoryId = null,
                                displayName = "Uncategorized",
                                icon = null,
                                spent = uncategorized,
                                share = if (total > 0) uncategorized.toFloat() / total else 0f,
                            ),
                        )
                    }
                }.sortedByDescending { it.spent }
                RangeReport(start, end, income, total, rows)
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            RangeReport(_range.value.first, _range.value.second),
        )

    // ---- Merchants (shares the date-range tab's range) ----

    private val _merchantSort = MutableStateFlow("total") // "total" | "visits"
    val merchantSort: StateFlow<String> = _merchantSort.asStateFlow()
    fun setMerchantSort(sort: String) { _merchantSort.value = sort }

    val merchants: StateFlow<List<com.endgamefinance.data.db.model.MerchantStat>> =
        _range.flatMapLatest { (start, end) ->
            combine(
                db.transactionDao().observeMerchants(start, end),
                _merchantSort,
            ) { stats, sort ->
                if (sort == "visits") stats.sortedByDescending { it.visits }
                else stats.sortedByDescending { it.total }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Year over year ----

    private val _yoyMonth = MutableStateFlow(YearMonth.now())
    val yoyMonth: StateFlow<YearMonth> = _yoyMonth.asStateFlow()
    fun yoyPrevious() { _yoyMonth.value = _yoyMonth.value.minusMonths(1) }
    fun yoyNext() { _yoyMonth.value = _yoyMonth.value.plusMonths(1) }

    val yoyReport: StateFlow<YoyReport> =
        _yoyMonth.flatMapLatest { month ->
            val prior = month.minusYears(1)
            combine(
                db.budgetDao().observeSpentByCategory(
                    MonthUtil.startMs(month), MonthUtil.endMs(month),
                ),
                db.budgetDao().observeSpentByCategory(
                    MonthUtil.startMs(prior), MonthUtil.endMs(prior),
                ),
                db.categoryDao().observeAll(),
            ) { currentSpends, priorSpends, categories ->
                val choices = categoryChoices(categories).associateBy { it.id }
                val iconById = categories.associateBy({ it.id }, { it.icon })
                val currentByCat = currentSpends.associateBy({ it.categoryId }, { it.spent })
                val priorByCat = priorSpends.associateBy({ it.categoryId }, { it.spent })
                val rows = (currentByCat.keys + priorByCat.keys).map { categoryId ->
                    YoyRow(
                        displayName = choices[categoryId]?.displayName ?: "(deleted category)",
                        icon = iconById[categoryId],
                        current = currentByCat[categoryId] ?: 0L,
                        prior = priorByCat[categoryId] ?: 0L,
                    )
                }.sortedByDescending { maxOf(it.current, it.prior) }
                YoyReport(
                    month = month,
                    rows = rows,
                    currentTotal = rows.sumOf { it.current },
                    priorTotal = rows.sumOf { it.prior },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YoyReport())

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportsViewModel(DatabaseProvider.get(context)) }
        }
    }
}
