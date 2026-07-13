package com.endgamefinance.data.repo

import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.dao.CategoryMonthSpend
import com.endgamefinance.data.db.entity.Budget
import com.endgamefinance.util.MonthUtil
import java.time.YearMonth
import java.util.UUID

class BudgetRepository(private val db: EndgameDatabase) {

    /** Null or zero [allocatedCents] removes the budget row for that category+month. */
    suspend fun setBudget(categoryId: String, month: String, allocatedCents: Long?, rolloverMode: String) {
        val existing = db.budgetDao().get(categoryId, month)
        when {
            allocatedCents == null || allocatedCents <= 0 ->
                existing?.let { db.budgetDao().delete(it) }
            existing != null ->
                db.budgetDao().update(
                    existing.copy(allocatedAmount = allocatedCents, rolloverMode = rolloverMode),
                )
            else ->
                db.budgetDao().insert(
                    Budget(
                        id = UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        month = month,
                        allocatedAmount = allocatedCents,
                        rolloverMode = rolloverMode,
                    ),
                )
        }
    }

    /** Copies last month's rows into [targetMonth] (skipping categories already budgeted). Returns count. */
    suspend fun copyLastMonth(targetMonth: YearMonth): Int {
        val source = db.budgetDao().forMonthOnce(MonthUtil.key(targetMonth.minusMonths(1)))
        val existing = db.budgetDao().forMonthOnce(MonthUtil.key(targetMonth))
            .map { it.categoryId }.toSet()
        val toCopy = source.filter { it.categoryId !in existing }
        toCopy.forEach { row ->
            db.budgetDao().insert(
                Budget(
                    id = UUID.randomUUID().toString(),
                    categoryId = row.categoryId,
                    month = MonthUtil.key(targetMonth),
                    allocatedAmount = row.allocatedAmount,
                    rolloverMode = row.rolloverMode,
                ),
            )
        }
        return toCopy.size
    }

    /**
     * Carry-in per category for [targetMonth]: surplus only (owner decision) —
     * carry = max(0, allocated + carryIn − spent) chained through the category's
     * earlier 'carry' budget rows in month order; a 'reset' row breaks the chain.
     */
    fun carryInFor(
        targetMonth: String,
        allBudgets: List<Budget>,
        spendHistory: List<CategoryMonthSpend>,
    ): Map<String, Long> {
        val spentByCatMonth = spendHistory.associateBy({ it.categoryId to it.month }, { it.spent })
        return allBudgets
            .filter { it.month < targetMonth }
            .groupBy { it.categoryId }
            .mapValues { (categoryId, rows) ->
                var carry = 0L
                rows.sortedBy { it.month }.forEach { b ->
                    val spent = spentByCatMonth[categoryId to b.month] ?: 0L
                    carry = if (b.rolloverMode == "carry") {
                        maxOf(0L, b.allocatedAmount + carry - spent)
                    } else {
                        0L
                    }
                }
                carry
            }
            .filterValues { it > 0 }
    }

    /** Trailing-12-full-months average spend for a category, in cents (total ÷ 12). */
    fun rollingAverage(
        categoryId: String,
        currentMonth: YearMonth,
        spendHistory: List<CategoryMonthSpend>,
    ): Long? {
        val from = MonthUtil.key(currentMonth.minusMonths(12))
        val until = MonthUtil.key(currentMonth)
        val total = spendHistory
            .filter { it.categoryId == categoryId && it.month >= from && it.month < until }
            .sumOf { it.spent }
        return if (total > 0) total / 12 else null
    }
}
