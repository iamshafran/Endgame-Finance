package com.endgamefinance.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the notification transaction parser (Milestone 8.1). */
class NotificationParserTest {

    // ---------- pre-filter ----------

    @Test
    fun money_prefilter_accepts_currency_amounts() {
        assertTrue(NotificationParser.looksLikeMoney("You spent £12.34 at Tesco"))
        assertTrue(NotificationParser.looksLikeMoney("Payment of $1,234.56 received"))
        assertTrue(NotificationParser.looksLikeMoney("GBP 9.99 debited"))
        assertTrue(NotificationParser.looksLikeMoney("Sent €45 to Sam"))
    }

    @Test
    fun money_prefilter_rejects_non_money() {
        assertFalse(NotificationParser.looksLikeMoney("Your one-time code is 123456"))
        assertFalse(NotificationParser.looksLikeMoney("Meeting moved to 10:30"))
        assertFalse(NotificationParser.looksLikeMoney("3 new messages"))
    }

    // ---------- JSON result parsing ----------

    @Test
    fun parses_a_well_formed_expense() {
        val p = NotificationParser.parseResult(
            """{"is_transaction": true, "payee": "Tesco", "amount": 12.34,
               "type": "expense", "category": "Groceries", "account": "Amex"}""",
        )!!
        assertEquals("Tesco", p.payee)
        assertEquals(1234L, p.amountCents)
        assertEquals("expense", p.type)
        assertEquals("Groceries", p.categoryGuess)
        assertEquals("Amex", p.accountHint)
    }

    @Test
    fun ignores_leading_prose_and_finds_the_object() {
        val p = NotificationParser.parseResult(
            """Sure, here you go: {"is_transaction":true,"amount":"5.00","payee":"Cafe"} done""",
        )!!
        assertEquals(500L, p.amountCents)
        assertEquals("Cafe", p.payee)
        assertEquals("expense", p.type) // defaulted when absent
    }

    @Test
    fun non_transaction_returns_null() {
        assertNull(
            NotificationParser.parseResult(
                """{"is_transaction": false, "amount": 12.00, "payee": "Bank"}""",
            ),
        )
    }

    @Test
    fun income_type_and_string_amount_with_commas() {
        val p = NotificationParser.parseResult(
            """{"is_transaction": true, "payee": "Employer", "amount": "1,234.56", "type": "income"}""",
        )!!
        assertEquals(123456L, p.amountCents)
        assertEquals("income", p.type)
    }

    @Test
    fun null_category_and_account_become_null_hints() {
        val p = NotificationParser.parseResult(
            """{"is_transaction": true, "payee": "X", "amount": 3.00, "category": "null", "account": null}""",
        )!!
        assertNull(p.categoryGuess)
        assertNull(p.accountHint)
    }

    @Test
    fun missing_payee_falls_back() {
        val p = NotificationParser.parseResult(
            """{"is_transaction": true, "amount": 7.50}""",
        )!!
        assertEquals("Payment", p.payee)
    }

    @Test
    fun zero_or_unparseable_amount_returns_null() {
        assertNull(NotificationParser.parseResult("""{"is_transaction": true, "amount": 0}"""))
        assertNull(NotificationParser.parseResult("""{"is_transaction": true, "payee": "X"}"""))
        assertNull(NotificationParser.parseResult("not json at all"))
    }
}
