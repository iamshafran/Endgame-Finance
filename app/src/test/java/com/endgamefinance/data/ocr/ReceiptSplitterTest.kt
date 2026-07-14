package com.endgamefinance.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the receipt line-item parser (Milestone 8.2). */
class ReceiptSplitterTest {

    @Test
    fun parses_merchant_and_items() {
        val p = ReceiptSplitter.parseProposal(
            """
            {"merchant": "Tesco", "items": [
              {"description": "Milk", "amount": 1.20, "category": "Groceries"},
              {"description": "Bread", "amount": "0.95", "category": null}
            ]}
            """.trimIndent(),
        )
        assertEquals("Tesco", p.merchant)
        assertEquals(2, p.lines.size)
        assertEquals("Milk", p.lines[0].description)
        assertEquals(120L, p.lines[0].amountCents)
        assertEquals("Groceries", p.lines[0].categoryName)
        assertEquals(95L, p.lines[1].amountCents)
        assertNull(p.lines[1].categoryName)
    }

    @Test
    fun drops_zero_and_unparseable_amounts() {
        val p = ReceiptSplitter.parseProposal(
            """{"merchant":"X","items":[
               {"description":"Bag","amount":0},
               {"description":"Sweets","amount":2.50}
            ]}""",
        )
        assertEquals(1, p.lines.size)
        assertEquals("Sweets", p.lines[0].description)
    }

    @Test
    fun tolerates_leading_prose_and_null_merchant() {
        val p = ReceiptSplitter.parseProposal(
            """Sure! {"merchant": null, "items":[{"description":"Coffee","amount":3}]} done""",
        )
        assertNull(p.merchant)
        assertEquals(1, p.lines.size)
        assertEquals(300L, p.lines[0].amountCents)
    }

    @Test
    fun missing_description_falls_back() {
        val p = ReceiptSplitter.parseProposal(
            """{"items":[{"amount":4.00,"category":"Household"}]}""",
        )
        assertEquals("Item", p.lines[0].description)
        assertEquals("Household", p.lines[0].categoryName)
    }

    @Test
    fun no_object_or_no_items_yields_empty() {
        assertTrue(ReceiptSplitter.parseProposal("nothing here").lines.isEmpty())
        assertNull(ReceiptSplitter.parseProposal("nothing here").merchant)
        assertTrue(ReceiptSplitter.parseProposal("""{"merchant":"Y"}""").lines.isEmpty())
    }

    // ---------- date/time ----------

    @Test
    fun parses_receipt_datetime_variants() {
        assertNotNull(ReceiptSplitter.parseDateTime("2026-07-09 21:59:00"))
        assertNotNull(ReceiptSplitter.parseDateTime("2026-07-09 21:59"))
        assertNotNull(ReceiptSplitter.parseDateTime("2026-07-09T21:59"))
        assertNotNull(ReceiptSplitter.parseDateTime("2026-07-09"))
    }

    @Test
    fun rejects_missing_or_bad_dates() {
        assertNull(ReceiptSplitter.parseDateTime(null))
        assertNull(ReceiptSplitter.parseDateTime("null"))
        assertNull(ReceiptSplitter.parseDateTime(""))
        assertNull(ReceiptSplitter.parseDateTime("09/07/2026")) // model must normalise to ISO
    }

    @Test
    fun proposal_carries_parsed_timestamp() {
        val p = ReceiptSplitter.parseProposal(
            """{"merchant":"Tesco","date":"2026-07-09 18:30","items":[{"description":"Milk","amount":1.20}]}""",
        )
        assertNotNull(p.timestamp)
    }

    @Test
    fun proposal_without_date_has_null_timestamp() {
        val p = ReceiptSplitter.parseProposal(
            """{"merchant":"Tesco","items":[{"description":"Milk","amount":1.20}]}""",
        )
        assertNull(p.timestamp)
    }
}
