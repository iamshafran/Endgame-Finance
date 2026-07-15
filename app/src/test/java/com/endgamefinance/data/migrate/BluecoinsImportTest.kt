package com.endgamefinance.data.migrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the Bluecoins import pipeline (Milestone 7.4). */
class BluecoinsImportTest {

    // ---------- CSV parsing ----------

    @Test
    fun csv_handles_quotes_commas_and_escapes() {
        val rows = CsvParser.parse(
            "\"A\",\"B\"\n\"has, comma\",\"has \"\"quote\"\"\"\nplain,unquoted\n",
        )
        assertEquals(3, rows.size)
        assertEquals(listOf("has, comma", "has \"quote\""), rows[1])
        assertEquals(listOf("plain", "unquoted"), rows[2])
    }

    @Test
    fun csv_skips_blank_lines() {
        val rows = CsvParser.parse("a,b\n\n\nc,d\n")
        assertEquals(2, rows.size)
    }

    // ---------- field parsing ----------

    @Test
    fun timestamp_variants_parse() {
        assertNotNull(BluecoinsImport.parseTimestamp("2026-07-09 21:59:00.0"))
        assertNotNull(BluecoinsImport.parseTimestamp("2026-07-05 10:59:10.931"))
        assertNotNull(BluecoinsImport.parseTimestamp("2026-05-22 21:25:32.92"))
        assertNotNull(BluecoinsImport.parseTimestamp("2026-07-09"))
        assertNull(BluecoinsImport.parseTimestamp("not a date"))
    }

    @Test
    fun amounts_parse_signed_cents() {
        assertEquals(-4395L, BluecoinsImport.parseAmountCents("-43.95"))
        assertEquals(254931L, BluecoinsImport.parseAmountCents("2549.31"))
        assertEquals(-123456L, BluecoinsImport.parseAmountCents("-1,234.56"))
        assertNull(BluecoinsImport.parseAmountCents(""))
    }

    @Test
    fun row_kinds_detect() {
        assertEquals(BluecoinsImport.Kind.STARTING_BALANCE, BluecoinsImport.rowKind("Starting Balance", -100))
        assertEquals(BluecoinsImport.Kind.TRANSFER, BluecoinsImport.rowKind("Transfer", -100))
        assertEquals(BluecoinsImport.Kind.INCOME, BluecoinsImport.rowKind("Income", 100))
        assertEquals(BluecoinsImport.Kind.EXPENSE, BluecoinsImport.rowKind("Expense", -100))
        // No type column → sign decides
        assertEquals(BluecoinsImport.Kind.INCOME, BluecoinsImport.rowKind(null, 100))
        assertEquals(BluecoinsImport.Kind.EXPENSE, BluecoinsImport.rowKind(null, -100))
    }

    // ---------- normalize + transfer pairing (mirrors the real sample) ----------

    private val mapping = ColumnMapping(
        type = 0, date = 1, payee = 3, amount = 4,
        categoryGroup = 7, category = 8, account = 9,
        notes = 10, labels = 11, status = 12,
    )

    private fun sampleRows() = listOf(
        listOf("Expense", "2026-07-09 21:59:00.0", "21:59", "The Royal", "-43.95", "GBP", "1.0", "Entertainment", "Dining Out", "Santander", "", "", "None"),
        listOf("Income", "2026-07-07 00:00:00.0", "00:00", "Supplement Pay", "2549.31", "GBP", "1.0", "Employer", "Salary", "Santander", "", "", "Reconciled"),
        listOf("Transfer", "2026-07-01 00:04:00.0", "00:04", "Personal Loan Repayment", "-134.33", "GBP", "1.0", "(Transfer)", "(Transfer)", "Lloyds", "", "", "Reconciled"),
        listOf("Transfer", "2026-07-01 00:04:00.0", "00:04", "Personal Loan Repayment", "134.33", "GBP", "1.0", "(Transfer)", "(Transfer)", "Lloyds Bank Personal Loan", "", "", "Reconciled"),
        listOf("Starting Balance", "2026-05-22 08:39:11.533", "08:39", "Lloyds Bank Personal Loan", "-3961.80", "GBP", "1.0", "(New Account)", "(New Account)", "Lloyds Bank Personal Loan", "", "", "Reconciled"),
    )

    @Test
    fun normalize_converts_rows_and_strips_parenthesized_categories() {
        val warnings = mutableListOf<String>()
        val rows = BluecoinsImport.normalize(mapping, sampleRows(), warnings)
        assertEquals(5, rows.size)
        assertTrue(warnings.isEmpty())

        val expense = rows[0]
        assertEquals(BluecoinsImport.Kind.EXPENSE, expense.kind)
        assertEquals(-4395L, expense.amountCents)
        assertEquals("Dining Out", expense.category)
        assertEquals("Entertainment", expense.categoryGroup)
        assertEquals(false, expense.cleared) // "None"

        val income = rows[1]
        assertEquals(BluecoinsImport.Kind.INCOME, income.kind)
        assertEquals(true, income.cleared) // "Reconciled"

        // "(Transfer)"/"(New Account)" pseudo-categories are stripped
        assertNull(rows[2].category)
        assertNull(rows[4].category)
    }

    @Test
    fun transfers_pair_into_source_and_destination() {
        val warnings = mutableListOf<String>()
        val rows = BluecoinsImport.normalize(mapping, sampleRows(), warnings)
        val paired = BluecoinsImport.pairTransfers(rows)

        assertEquals(1, paired.transfers.size)
        assertEquals(0, paired.unpaired.size)
        val pair = paired.transfers[0]
        assertEquals("Lloyds", pair.source.account)
        assertEquals("Lloyds Bank Personal Loan", pair.destination.account)
        assertTrue(pair.source.amountCents < 0)
        assertTrue(pair.destination.amountCents > 0)

        assertEquals(2, paired.plain.size)
        assertEquals(1, paired.startingBalances.size)
    }

    @Test
    fun unpaired_transfer_leg_is_reported() {
        val warnings = mutableListOf<String>()
        val rows = BluecoinsImport.normalize(
            mapping,
            listOf(
                listOf("Transfer", "2026-06-10 11:42:37.451", "11:42", "Transfer to Waji", "-10.00", "GBP", "1.0", "(Transfer)", "(Transfer)", "Lloyds", "", "", "Reconciled"),
            ),
            warnings,
        )
        val paired = BluecoinsImport.pairTransfers(rows)
        assertEquals(0, paired.transfers.size)
        assertEquals(1, paired.unpaired.size)
    }

    @Test
    fun bad_rows_are_skipped_with_warnings() {
        val warnings = mutableListOf<String>()
        val rows = BluecoinsImport.normalize(
            mapping,
            listOf(
                listOf("Expense", "garbage-date", "", "X", "-1.00", "", "", "", "", "Acct", "", "", ""),
                listOf("Expense", "2026-01-01 00:00:00.0", "", "Y", "not-a-number", "", "", "", "", "Acct", "", "", ""),
            ),
            warnings,
        )
        assertEquals(0, rows.size)
        assertEquals(2, warnings.size)
    }

    // ---------- AI mapping plumbing (JSON extraction + validation) ----------

    private val header = listOf(
        "Type", "Date", "Set Time", "Name", "Amount", "Currency", "Exchange Rate",
        "Category Group", "Category", "Account", "Notes", "Labels", "Status",
    )

    @Test
    fun mapping_json_extracts_and_resolves_names() {
        val raw = """
            Here is the mapping:
            {"type": "Type", "date": "Date", "payee": "Name", "amount": "Amount",
             "category_group": "Category Group", "category": "Category",
             "account": "Account", "notes": "Notes", "labels": "Labels", "status": "Status"}
        """.trimIndent()
        val json = ColumnMapping.extractJson(raw)!!
        val m = ColumnMapping.fromNames(json, header)
        assertEquals(0, m.type)
        assertEquals(1, m.date)
        assertEquals(3, m.payee)
        assertEquals(4, m.amount)
        assertEquals(7, m.categoryGroup)
        assertEquals(9, m.account)
        assertEquals(12, m.status)
    }

    @Test
    fun mapping_validation_rejects_wrong_columns() {
        // date mapped to the payee column → validation must fail
        val bad = ColumnMapping(
            type = 0, date = 3, payee = 1, amount = 4,
            categoryGroup = null, category = null, account = 9,
            notes = null, labels = null, status = null,
        )
        var failed = false
        try {
            ColumnMapping.validate(bad, sampleRows())
        } catch (e: ColumnMapping.Companion.MappingError) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun mapping_validation_accepts_correct_columns() {
        ColumnMapping.validate(mapping, sampleRows()) // must not throw
    }

    // ---------- BOM regression (the real Bluecoins export starts with one) ----------

    @Test
    fun bom_is_stripped_from_header() {
        val rows = CsvParser.parse("﻿\"Type\",\"Date\"\n\"Expense\",\"2026-01-01\"\n")
        assertEquals("Type", rows[0][0])
    }

    @Test
    fun bom_header_still_resolves_mapping_names() {
        val bomHeader = listOf("﻿Type", "Date", "Set Time", "Name", "Amount") +
            header.drop(5)
        val json = mapOf("type" to "Type", "date" to "Date", "amount" to "Amount", "account" to "Account")
        val m = ColumnMapping.fromNames(json, bomHeader)
        assertEquals(0, m.type) // matches despite the BOM
    }

    // ---------- repair pass: transfers once imported as expense+income ----------

    @Test
    fun mistyped_transfer_pairs_are_found() {
        val legs = listOf(
            com.endgamefinance.data.db.model.RepairLeg("e1", "expense", "acct-lloyds", "Loan Repayment", 1000L, 13433L),
            com.endgamefinance.data.db.model.RepairLeg("i1", "income", "acct-loan", "Loan Repayment", 1000L, 13433L),
            // ordinary expense — must be untouched
            com.endgamefinance.data.db.model.RepairLeg("e2", "expense", "acct-lloyds", "Rent", 2000L, 99500L),
        )
        val repairs = BluecoinsImport.findMistypedTransfers(legs)
        assertEquals(1, repairs.size)
        assertEquals("e1", repairs[0].expenseId)
        assertEquals("i1", repairs[0].incomeId)
        assertEquals("acct-loan", repairs[0].toAccountId)
    }

    @Test
    fun same_account_pairs_are_not_repaired() {
        val legs = listOf(
            com.endgamefinance.data.db.model.RepairLeg("e1", "expense", "acct-a", "X", 1000L, 500L),
            com.endgamefinance.data.db.model.RepairLeg("i1", "income", "acct-a", "X", 1000L, 500L),
        )
        assertEquals(0, BluecoinsImport.findMistypedTransfers(legs).size)
    }
}
