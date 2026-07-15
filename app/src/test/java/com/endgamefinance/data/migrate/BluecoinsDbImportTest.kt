package com.endgamefinance.data.migrate

import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the direct .fydb (SQLite) import planner
 * ([BluecoinsDbImport.plan]) — the read/write halves are Android-bound, but the
 * transformation from raw Bluecoins rows to an import plan is deterministic and
 * unit-testable. Amounts are micro-units (cents × 10,000).
 */
class BluecoinsDbImportTest {

    private fun micro(cents: Long) = cents * 10_000L
    private fun ts(text: String) = BluecoinsImport.parseTimestamp(text)!!

    // Account types: accountingGroupID 1 = assets, 2 = liabilities.
    private val accountTypes = listOf(
        BluecoinsDbImport.RawAccountType(2, "Bank", 1),
        BluecoinsDbImport.RawAccountType(3, "Credit Card", 2),
        BluecoinsDbImport.RawAccountType(9, "Loans", 2),
        BluecoinsDbImport.RawAccountType(5, "Investments", 2),
    )

    private val accounts = listOf(
        BluecoinsDbImport.RawAccount(10, "Santander", typeId = 2, currency = "GBP", creditLimitMicro = 0),
        BluecoinsDbImport.RawAccount(11, "Amex", typeId = 3, currency = "GBP", creditLimitMicro = micro(500_000)),
        BluecoinsDbImport.RawAccount(12, "Personal Loan", typeId = 9, currency = "GBP", creditLimitMicro = 0),
        BluecoinsDbImport.RawAccount(13, "Stocks", typeId = 5, currency = "GBP", creditLimitMicro = 0),
    )

    private val parents = listOf(
        BluecoinsDbImport.RawParent(100, "Food", 3),   // expense group
        BluecoinsDbImport.RawParent(101, "Earnings", 2), // income group
    )
    private val categories = listOf(
        BluecoinsDbImport.RawCategory(20, "Groceries", 100),
        BluecoinsDbImport.RawCategory(21, "Salary", 101),
    )

    /** Full [RawTransaction] with sensible defaults; override per row. */
    private fun tx(
        id: Long,
        payee: String = "",
        amountCents: Long = 0,
        date: String = "2026-06-01 12:00:00.0",
        typeId: Long,
        categoryId: Long = 20,
        accountId: Long,
        accountPairId: Long = accountId,
        status: Long = 1,
        reminderMarker: Long? = null,
        reminderGroupId: Long? = null,
        reminderFrequency: Long? = null,
        reminderRepeatEvery: Long? = null,
    ) = BluecoinsDbImport.RawTransaction(
        id = id, payee = payee, amountMicro = micro(amountCents), dateText = date,
        typeId = typeId, categoryId = categoryId, accountId = accountId, accountPairId = accountPairId,
        notes = null, status = status, reminderMarker = reminderMarker, reminderGroupId = reminderGroupId,
        reminderFrequency = reminderFrequency, reminderRepeatEvery = reminderRepeatEvery,
        reminderAutoLog = 0, deleted = 6,
    )

    private fun samplePlan(): BluecoinsDbImport.Plan {
        val transactions = listOf(
            // openings (typeId 2)
            tx(1, amountCents = 10_000, typeId = 2, categoryId = 1, accountId = 10),        // Santander +£100
            tx(2, amountCents = -396_180, typeId = 2, categoryId = 1, accountId = 12,       // loan opening −£3961.80
                date = "2026-05-22 08:39:11.533"),
            tx(3, amountCents = 100_000, typeId = 2, categoryId = 1, accountId = 13),        // Stocks +£1000
            // history
            tx(4, "Tesco", amountCents = -4_395, typeId = 3, categoryId = 20, accountId = 10),
            tx(5, "Salary", amountCents = 254_931, typeId = 4, categoryId = 21, accountId = 10),
            tx(6, "Amazon", amountCents = -5_000, typeId = 3, categoryId = 20, accountId = 11),
            // transfer (both legs; only the negative one becomes a PlannedTx)
            tx(7, "Loan Repayment", amountCents = -13_433, typeId = 5, categoryId = 1,
                accountId = 10, accountPairId = 12),
            tx(8, "Loan Repayment", amountCents = 13_433, typeId = 5, categoryId = 1,
                accountId = 12, accountPairId = 10),
            // scheduled series → reminder (monthly Netflix)
            tx(9, "Netflix", amountCents = -999, typeId = 3, categoryId = 20, accountId = 10,
                date = "2026-08-01 00:00:00.0", reminderMarker = 9, reminderGroupId = 500,
                reminderFrequency = 4, reminderRepeatEvery = 1),
        )
        return BluecoinsDbImport.plan(
            accounts, accountTypes, parents, categories, transactions,
            nowMs = ts("2026-07-01 00:00:00.0"),
        )
    }

    @Test
    fun account_types_map_to_accounting_groups() {
        val byName = samplePlan().accounts.associateBy { it.name }
        assertEquals(Account.TYPE_ASSET, byName.getValue("Santander").type)
        assertEquals(Account.TYPE_LIABILITY, byName.getValue("Amex").type)
        assertEquals(Account.TYPE_LIABILITY, byName.getValue("Personal Loan").type)
        assertEquals(Account.TYPE_INVESTMENT, byName.getValue("Stocks").type)
    }

    @Test
    fun credit_limit_only_on_liabilities() {
        val byName = samplePlan().accounts.associateBy { it.name }
        assertEquals(500_000L, byName.getValue("Amex").creditLimitCents)
        assertNull(byName.getValue("Santander").creditLimitCents)
    }

    @Test
    fun loan_opening_becomes_original_principal() {
        val loan = samplePlan().accounts.first { it.name == "Personal Loan" }
        assertEquals(396_180L, loan.originalPrincipalCents)
        assertEquals(-396_180L, loan.openingCents)
    }

    @Test
    fun opening_balances_are_recorded_not_emitted_as_transactions() {
        val plan = samplePlan()
        // The three typeId=2 rows are openings, not history transactions.
        assertTrue(plan.transactions.none { it.payee == "Starting Balance" })
        assertEquals(10_000L, plan.accounts.first { it.name == "Santander" }.openingCents)
    }

    @Test
    fun history_splits_into_expense_income_transfer() {
        val txs = samplePlan().transactions
        assertEquals(2, txs.count { it.type == "expense" })
        assertEquals(1, txs.count { it.type == "income" })
        assertEquals(1, txs.count { it.type == "transfer" })
    }

    @Test
    fun transfer_keeps_negative_leg_and_carries_destination() {
        val transfer = samplePlan().transactions.first { it.type == "transfer" }
        assertEquals(10L, transfer.accountSourceId)
        assertEquals(12L, transfer.toAccountSourceId)
        assertEquals(13_433L, transfer.amountCents) // positive magnitude
        assertNull(transfer.category)
    }

    @Test
    fun expense_carries_typed_parent_and_child_category() {
        val tesco = samplePlan().transactions.first { it.payee == "Tesco" }
        assertEquals("Food", tesco.parentCategory)
        assertEquals("Groceries", tesco.category)
        assertEquals(Category.TYPE_EXPENSE, tesco.categoryType)
        assertEquals(4_395L, tesco.amountCents)
    }

    @Test
    fun income_category_is_typed_income() {
        val salary = samplePlan().transactions.first { it.payee == "Salary" }
        assertEquals(Category.TYPE_INCOME, salary.categoryType)
        assertEquals("Salary", salary.category)
    }

    @Test
    fun scheduled_series_becomes_a_reminder_not_a_transaction() {
        val plan = samplePlan()
        assertTrue(plan.transactions.none { it.payee == "Netflix" })
        assertEquals(1, plan.reminders.size)
        val netflix = plan.reminders.single()
        assertEquals("Netflix", netflix.name)
        assertEquals("monthly", netflix.frequency)
        assertEquals(1, netflix.interval)
        assertEquals(999L, netflix.amountCents)
        assertEquals(ts("2026-08-01 00:00:00.0"), netflix.nextDue)
        assertEquals(1, netflix.anchorDay) // 1st of the month
    }

    @Test
    fun unused_accounts_are_dropped() {
        val extra = accounts + BluecoinsDbImport.RawAccount(
            99, "Ghost", typeId = 2, currency = "GBP", creditLimitMicro = 0,
        )
        val plan = BluecoinsDbImport.plan(
            extra, accountTypes, parents, categories,
            listOf(tx(1, "Tesco", amountCents = -100, typeId = 3, accountId = 10)),
            nowMs = ts("2026-07-01 00:00:00.0"),
        )
        assertTrue(plan.accounts.none { it.name == "Ghost" })
    }

    @Test
    fun plan_is_warning_free_for_clean_input() {
        assertTrue(samplePlan().warnings.isEmpty())
    }
}
