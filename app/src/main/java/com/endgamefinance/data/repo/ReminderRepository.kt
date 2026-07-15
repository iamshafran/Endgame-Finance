package com.endgamefinance.data.repo

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ReminderRepository(private val db: EndgameDatabase) {

    /**
     * Posts a due reminder into the ledger: creates a real (uncleared)
     * transaction and advances the due date. Variable bills must supply
     * [amountOverrideCents]. 'once' reminders are deleted after posting.
     */
    suspend fun post(reminder: Reminder, amountOverrideCents: Long? = null) {
        val amount = amountOverrideCents ?: reminder.amount
        requireNotNull(amount) { "This bill varies — enter the amount to post it" }
        require(amount > 0) { "Amount must be positive" }
        db.withTransaction {
            // Transfer/repayment reminders post as transfers (category-less split);
            // otherwise the category's type decides income vs expense.
            val isTransfer = reminder.toAccountId != null
            val category = reminder.categoryId?.let { db.categoryDao().getById(it) }
            val type = when {
                isTransfer -> "transfer"
                category?.type == Category.TYPE_INCOME -> "income"
                else -> "expense"
            }
            val txId = UUID.randomUUID().toString()
            db.transactionDao().insertWithSplits(
                TransactionEntity(
                    id = txId,
                    accountId = reminder.accountId,
                    toAccountId = reminder.toAccountId,
                    timestamp = System.currentTimeMillis(),
                    payee = reminder.name,
                    notes = "Posted from reminder",
                    type = type,
                    isCleared = false,
                ),
                listOf(
                    TransactionSplit(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        categoryId = if (isTransfer) null else reminder.categoryId,
                        amount = amount,
                    ),
                ),
            )
            advance(reminder)
        }
    }

    /**
     * Posts a loan-repayment reminder as a proper split: one transfer
     * transaction into the loan whose category-less split is [principal] (reduces
     * the loan) and whose categorized split is the interest (a borrowing-cost
     * expense). Mirrors the entry screen's "loan payment" mode. Advances the
     * reminder. [interestCents] may be 0 (all principal).
     */
    suspend fun postLoanPayment(
        reminder: Reminder,
        paymentCents: Long,
        interestCents: Long,
        interestCategoryId: String?,
    ) {
        require(paymentCents > 0) { "Payment must be positive" }
        require(interestCents in 0..paymentCents) { "Interest can't exceed the payment" }
        require(reminder.toAccountId != null) { "A loan payment needs a destination loan account" }
        if (interestCents > 0) {
            requireNotNull(interestCategoryId) { "Pick a category for the interest" }
        }
        val principal = paymentCents - interestCents
        db.withTransaction {
            val txId = UUID.randomUUID().toString()
            val splits = buildList {
                if (principal > 0) {
                    add(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = null, // principal — credits the loan
                            amount = principal,
                        ),
                    )
                }
                if (interestCents > 0) {
                    add(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = interestCategoryId,
                            amount = interestCents,
                        ),
                    )
                }
            }
            db.transactionDao().insertWithSplits(
                TransactionEntity(
                    id = txId,
                    accountId = reminder.accountId,
                    toAccountId = reminder.toAccountId,
                    timestamp = System.currentTimeMillis(),
                    payee = reminder.name,
                    notes = "Loan payment",
                    type = "transfer",
                    isCleared = false,
                ),
                splits,
            )
            advance(reminder)
        }
    }

    /** Skips this occurrence without touching the ledger. */
    suspend fun skip(reminder: Reminder) = db.withTransaction { advance(reminder) }

    private suspend fun advance(reminder: Reminder) {
        if (reminder.frequency == "once") {
            db.reminderDao().delete(reminder)
            return
        }
        db.reminderDao().update(
            reminder.copy(nextDueDate = nextOccurrence(reminder)),
        )
    }

    companion object {

        /**
         * The due date after this one, honoring frequency_interval and, for
         * month-based cadences, anchor_day — so a bill anchored to the 31st
         * clamps in short months but snaps back (Jan 31 → Feb 28 → Mar 31).
         */
        fun nextOccurrence(reminder: Reminder): Long {
            val zone = ZoneId.systemDefault()
            val due = Instant.ofEpochMilli(reminder.nextDueDate).atZone(zone)
            val n = reminder.frequencyInterval.coerceAtLeast(1).toLong()
            val next = when (reminder.frequency) {
                "daily" -> due.plusDays(n)
                "weekly" -> due.plusWeeks(n)
                "monthly" -> due.withAnchoredMonthShift(n, reminder.anchorDay)
                "yearly" -> due.withAnchoredMonthShift(n * 12, reminder.anchorDay)
                else -> due.plusMonths(n)
            }
            return next.toInstant().toEpochMilli()
        }

        private fun java.time.ZonedDateTime.withAnchoredMonthShift(
            months: Long,
            anchorDay: Int?,
        ): java.time.ZonedDateTime {
            val shifted = toLocalDate().plusMonths(months)
            val day = (anchorDay ?: dayOfMonth).coerceIn(1, shifted.lengthOfMonth())
            return shifted.withDayOfMonth(day).atTime(toLocalTime()).atZone(zone)
        }

        /** "monthly" / "every 2 weeks" / "once" — for row subtitles and previews. */
        fun frequencyLabel(frequency: String, interval: Int): String {
            if (frequency == "once") return "once"
            if (interval <= 1) return frequency
            val unit = when (frequency) {
                "daily" -> "days"
                "weekly" -> "weeks"
                "monthly" -> "months"
                "yearly" -> "years"
                else -> frequency
            }
            return "every $interval $unit"
        }
    }
}
