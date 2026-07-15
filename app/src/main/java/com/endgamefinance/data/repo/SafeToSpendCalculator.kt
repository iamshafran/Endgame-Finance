package com.endgamefinance.data.repo

import com.endgamefinance.data.db.entity.Budget
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.model.AccountWithBalance

/** One reminder's contribution to UpcomingBills, for the breakdown. */
data class CountedBill(
    val name: String,
    /** Total counted inside the window (all occurrences). */
    val amountCents: Long,
    /** True when the reminder's next due date is already in the past. */
    val overdue: Boolean,
)

data class SafeToSpend(
    val amountCents: Long,
    // Inputs, for the tap-to-explain breakdown — each maps 1:1 to docs/safe-to-spend.md
    val liquidBalances: Long,
    val envelopeFunds: Long,
    val upcomingBills: Long,
    val remainingBudgetCommitments: Long,
    /** End of the "until payday" window used for UpcomingBills. */
    val nextIncomeDate: Long?,
    /** Variable-amount bills that could not be counted — surfaced, never $0. */
    val uncountedVariableBills: List<String>,
    /** Exactly which bills make up [upcomingBills], so the number explains itself. */
    val countedBills: List<CountedBill> = emptyList(),
)

/**
 * Implements docs/safe-to-spend.md EXACTLY. If this file and that document
 * disagree, this file is wrong. Pure function — no I/O.
 */
object SafeToSpendCalculator {

    private const val DAY_MS = 86_400_000L
    private const val HORIZON_DAYS = 30

    fun calculate(
        accounts: List<AccountWithBalance>,
        envelopes: List<Envelope>,
        reminders: List<Reminder>,
        categoriesById: Map<String, Category>,
        monthBudgets: List<Budget>,
        carryInByCategory: Map<String, Long>,
        spentByCategory: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): SafeToSpend {
        val assetAccountIds = accounts
            .filter { it.account.type == "asset" }
            .map { it.account.id }
            .toSet()

        // LiquidBalances: active asset accounts only
        val liquid = accounts
            .filter { it.account.id in assetAccountIds }
            .sumOf { it.balance }

        // EnvelopeFunds: all envelope balances are spoken for
        val envelopeFunds = envelopes.sumOf { it.currentAmount }

        val horizonEnd = nowMs + HORIZON_DAYS * DAY_MS

        fun isIncomeReminder(r: Reminder): Boolean =
            r.toAccountId == null &&
                r.categoryId?.let { categoriesById[it] }?.type == Category.TYPE_INCOME

        // Next expected income event; fall back to full horizon if none.
        // Per the doc, the WINDOW is set by the income occurrence's date — a
        // variable amount doesn't matter here (we never add expected income,
        // we only stop counting bills at payday). Requiring a fixed amount
        // silently widened the window to 30 days and swept in far more bills.
        val nextIncomeDate = reminders
            .filter { isIncomeReminder(it) && it.accountId in assetAccountIds }
            .minOfOrNull { it.nextDueDate }
            ?.takeIf { it <= horizonEnd }
        val windowEnd = nextIncomeDate ?: horizonEnd

        // UpcomingBills: fixed-amount asset outflows due inside the window,
        // expanded across their cadence. Track per-category for the
        // double-count correction.
        var upcomingBills = 0L
        val billsByCategory = mutableMapOf<String, Long>()
        val countedBills = mutableListOf<CountedBill>()
        reminders
            .filter { it.amount != null && !isIncomeReminder(it) && it.accountId in assetAccountIds }
            .forEach { reminder ->
                var current = reminder
                var guard = 0
                var billTotal = 0L
                while (current.nextDueDate <= windowEnd && guard < 100) {
                    val amount = requireNotNull(reminder.amount)
                    billTotal += amount
                    reminder.categoryId?.let { cat ->
                        billsByCategory[cat] = (billsByCategory[cat] ?: 0L) + amount
                    }
                    if (current.frequency == "once") break
                    current = current.copy(
                        nextDueDate = ReminderRepository.nextOccurrence(current),
                    )
                    guard++
                }
                if (billTotal > 0) {
                    upcomingBills += billTotal
                    countedBills += CountedBill(
                        name = reminder.name,
                        amountCents = billTotal,
                        overdue = reminder.nextDueDate < nowMs,
                    )
                }
            }

        // Income reminders are not bills — a variable salary belongs in
        // neither the bill sum nor the "not counted" caveat.
        val uncountedVariable = reminders
            .filter { it.amount == null && !isIncomeReminder(it) && it.accountId in assetAccountIds }
            .map { it.name }

        // RemainingBudgetCommitments with the double-count correction:
        // a bill already counted above must not be held again by its budget
        val remainingCommitments = monthBudgets.sumOf { budget ->
            val available = budget.allocatedAmount + (carryInByCategory[budget.categoryId] ?: 0L)
            val spent = spentByCategory[budget.categoryId] ?: 0L
            val counted = billsByCategory[budget.categoryId] ?: 0L
            maxOf(0L, available - spent - counted)
        }

        return SafeToSpend(
            amountCents = liquid - envelopeFunds - upcomingBills - remainingCommitments,
            liquidBalances = liquid,
            envelopeFunds = envelopeFunds,
            upcomingBills = upcomingBills,
            remainingBudgetCommitments = remainingCommitments,
            nextIncomeDate = nextIncomeDate,
            uncountedVariableBills = uncountedVariable,
            countedBills = countedBills.sortedByDescending { it.amountCents },
        )
    }
}
