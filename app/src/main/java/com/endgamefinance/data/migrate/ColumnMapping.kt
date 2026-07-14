package com.endgamefinance.data.migrate

import android.content.Context
import com.endgamefinance.data.ai.GemmaEngine

/**
 * The internal fields an import needs, each resolved to a source-CSV column
 * index (or null when the source has no such column). Produced by Gemma from
 * the file's own header + sample rows — per the milestone spec there is no
 * hardcoded source-format column table.
 */
data class ColumnMapping(
    val type: Int?,
    val date: Int,
    val payee: Int?,
    val amount: Int,
    val categoryGroup: Int?,
    val category: Int?,
    val account: Int,
    val notes: Int?,
    val labels: Int?,
    val status: Int?,
) {
    companion object {

        /** Fields Gemma is asked to locate, with hints about their meaning. */
        private val FIELDS = listOf(
            "type" to "transaction kind: expense/income/transfer",
            "date" to "when the transaction happened (REQUIRED)",
            "payee" to "merchant / description / title / name",
            "amount" to "the money value (REQUIRED)",
            "category_group" to "parent category, if the source has a 2-level hierarchy",
            "category" to "category name",
            "account" to "the account the money moved on (REQUIRED)",
            "notes" to "free-text notes/memo",
            "labels" to "tags/labels",
            "status" to "cleared/reconciled state",
        )

        class MappingError(message: String) : Exception(message)

        /**
         * Asks the on-device model to map [header] to our fields, then
         * validates the result against [sampleRows] (mapped date/amount must
         * actually parse). Throws [MappingError] with a readable reason.
         */
        suspend fun infer(
            context: Context,
            header: List<String>,
            sampleRows: List<List<String>>,
        ): ColumnMapping {
            val raw = GemmaEngine.generate(context, prompt(header, sampleRows))
            val json = extractJson(raw) ?: throw MappingError(
                "The AI couldn't produce a column mapping for this file.",
            )
            val mapping = fromNames(json, header)
            validate(mapping, sampleRows)
            return mapping
        }

        private fun prompt(header: List<String>, sampleRows: List<List<String>>): String {
            val fields = FIELDS.joinToString("\n") { (k, hint) -> "- \"$k\": $hint" }
            val samples = sampleRows.take(3).joinToString("\n") { row ->
                row.joinToString(" | ")
            }
            return """
                You map CSV columns from a personal-finance export to internal fields.
                CSV header columns:
                ${header.mapIndexed { i, h -> "$i: \"$h\"" }.joinToString(", ")}
                Sample data rows:
                $samples

                Internal fields to locate:
                $fields

                Reply with ONLY a JSON object mapping each internal field name to the
                exact header column NAME it corresponds to, or null when the file has
                no matching column. No explanation.
            """.trimIndent()
        }

        /** Finds the first {...} block and parses it as flat string/null pairs. */
        fun extractJson(raw: String): Map<String, String?>? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val body = raw.substring(start + 1, end)
            val map = mutableMapOf<String, String?>()
            // "key" : "value" | null   — tolerant of whitespace/newlines
            val entry = Regex("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|null)")
            for (m in entry.findAll(body)) {
                val key = m.groupValues[1].trim().lowercase().replace(" ", "_")
                val value = if (m.groupValues[2] == "null") null else m.groupValues[3]
                map[key] = value
            }
            return map.ifEmpty { null }
        }

        /** BOM/whitespace/case-proof header comparison. */
        private fun canon(s: String): String =
            s.replace("﻿", "").trim().lowercase()

        /** Resolves header NAMES (case-insensitive, BOM-tolerant) to indices. */
        fun fromNames(json: Map<String, String?>, header: List<String>): ColumnMapping {
            fun idx(field: String): Int? {
                val name = json[field]?.let(::canon) ?: return null
                val i = header.indexOfFirst { canon(it) == name }
                return if (i >= 0) i else null
            }
            return ColumnMapping(
                type = idx("type"),
                date = idx("date") ?: throw MappingError("No date column identified."),
                payee = idx("payee") ?: idx("name"),
                amount = idx("amount") ?: throw MappingError("No amount column identified."),
                categoryGroup = idx("category_group"),
                category = idx("category"),
                account = idx("account") ?: throw MappingError("No account column identified."),
                notes = idx("notes"),
                labels = idx("labels"),
                status = idx("status"),
            )
        }

        /** The mapped columns must hold parseable values in the sample rows. */
        fun validate(mapping: ColumnMapping, sampleRows: List<List<String>>) {
            val rows = sampleRows.filter { it.size > maxOf(mapping.date, mapping.amount) }
            if (rows.isEmpty()) throw MappingError("The file has no data rows.")
            val dateOk = rows.count { BluecoinsImport.parseTimestamp(it[mapping.date]) != null }
            if (dateOk == 0) throw MappingError(
                "The column mapped as date doesn't contain dates.",
            )
            val amountOk = rows.count { BluecoinsImport.parseAmountCents(it[mapping.amount]) != null }
            if (amountOk == 0) throw MappingError(
                "The column mapped as amount doesn't contain numbers.",
            )
        }
    }
}
