package com.endgamefinance.data.repo

import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.model.AccountWithBalance

data class ForecastEvent(
    val date: Long,
    val name: String,
    /** Positive = inflow, negative = outflow. */
    val signedAmount: Long,
    val runningBalance: Long,
)

data class AccountForecast(
    val accountId: String,
    val accountName: String,
    /** 'asset', 'liability', 'investment' — shortfall warnings apply to assets only. */
    val accountType: String,
    val startingBalance: Long,
    val endingBalance: Long,
    val events: List<ForecastEvent>,
    /** First projected dip below zero (asset accounts only), if any. */
    val shortfallDate: Long?,
    val shortfallBalance: Long?,
    /** True when the dip happens before any projected income arrives. */
    val shortfallBeforeIncome: Boolean,
    /** Variable-amount bills that could not be projected. */
    val unprojectable: List<String>,
)

/**
 * Pure cash projection for every active account: posted balance plus every
 * projected reminder occurrence inside the horizon. Income-category reminders
 * add; transfer reminders subtract from the source and add to the destination
 * (repayments reduce liability debt); everything else subtracts.
 */
object ForecastBuilder {

    private const val DAY_MS = 86_400_000L
    private const val MAX_OCCURRENCES_PER_REMINDER = 100

    fun build(
        accounts: List<AccountWithBalance>,
        reminders: List<Reminder>,
        categoriesById: Map<String, Category>,
        nowMs: Long = System.currentTimeMillis(),
        horizonDays: Int = 30,
    ): List<AccountForecast> {
        val horizonEnd = nowMs + horizonDays * DAY_MS

        // Expand every fixed-amount reminder once
        val occurrences = reminders
            .filter { it.amount != null }
            .flatMap { expand(it, horizonEnd) }

        return accounts.map { acct ->
            val id = acct.account.id
            val own = reminders.filter { it.accountId == id }
            val unprojectable = own.filter { it.amount == null }.map { it.name }

            val flows = occurrences.mapNotNull { (reminder, dueDate) ->
                val amount = requireNotNull(reminder.amount)
                when {
                    reminder.accountId == id -> {
                        val isIncome = reminder.toAccountId == null && reminder.categoryId
                            ?.let { categoriesById[it] }?.type == Category.TYPE_INCOME
                        Triple(dueDate, reminder.name, if (isIncome) amount else -amount)
                    }
                    reminder.toAccountId == id ->
                        Triple(dueDate, reminder.name, amount)
                    else -> null
                }
            }.sortedBy { it.first }

            var balance = acct.balance
            var shortfallDate: Long? = null
            var shortfallBalance: Long? = null
            var firstIncomeDate: Long? = null
            val events = flows.map { (date, name, signed) ->
                balance += signed
                if (signed > 0 && firstIncomeDate == null) firstIncomeDate = date
                if (acct.account.type == "asset" && balance < 0 && shortfallDate == null) {
                    shortfallDate = date
                    shortfallBalance = balance
                }
                ForecastEvent(date, name, signed, balance)
            }

            AccountForecast(
                accountId = id,
                accountName = acct.account.name,
                accountType = acct.account.type,
                startingBalance = acct.balance,
                endingBalance = balance,
                events = events,
                shortfallDate = shortfallDate,
                shortfallBalance = shortfallBalance,
                shortfallBeforeIncome = shortfallDate != null &&
                    (firstIncomeDate == null || shortfallDate!! <= firstIncomeDate!!),
                unprojectable = unprojectable,
            )
        }
    }

    /** Projected balance per account at end of [dateEndMs], from built forecasts. */
    fun balancesAt(forecasts: List<AccountForecast>, dateEndMs: Long): Map<String, Long> =
        forecasts.associateBy({ it.accountId }) { forecast ->
            forecast.events.lastOrNull { it.date <= dateEndMs }?.runningBalance
                ?: forecast.startingBalance
        }

    /** All (reminder, dueDate) occurrences inside the horizon, stepping by its cadence. */
    private fun expand(reminder: Reminder, horizonEnd: Long): List<Pair<Reminder, Long>> {
        val result = mutableListOf<Pair<Reminder, Long>>()
        var current = reminder
        var guard = 0
        while (current.nextDueDate <= horizonEnd && guard < MAX_OCCURRENCES_PER_REMINDER) {
            result += reminder to current.nextDueDate
            if (current.frequency == "once") break
            current = current.copy(
                nextDueDate = ReminderRepository.nextOccurrence(current),
            )
            guard++
        }
        return result
    }
}
