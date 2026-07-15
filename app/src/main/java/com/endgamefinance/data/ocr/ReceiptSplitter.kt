package com.endgamefinance.data.ocr

import android.content.Context
import com.endgamefinance.data.ai.GemmaEngine
import com.endgamefinance.data.migrate.BluecoinsImport

/**
 * Turns raw receipt OCR text into a *proposed* split across the user's existing
 * categories (Milestone 8.2), using the on-device model. Nothing is saved — the
 * user reviews and edits every line before it becomes a transaction.
 */
object ReceiptSplitter {

    /** One proposed line item. [categoryName] is the model's suggestion, matched
     *  to a real category downstream (null when it couldn't place it). */
    data class Line(
        val description: String,
        val amountCents: Long,
        val categoryName: String?,
    )

    data class Proposal(
        val merchant: String?,
        val lines: List<Line>,
        /** Purchase time parsed from the receipt, or null if none was found. */
        val timestamp: Long?,
    )

    /** Runs OCR [text] through the model against [categoryNames]. */
    suspend fun split(
        context: Context,
        text: String,
        categoryNames: List<String>,
    ): Proposal {
        val raw = GemmaEngine.generate(context, prompt(text, categoryNames))
        return parseProposal(raw)
    }

    private fun prompt(text: String, categoryNames: List<String>): String {
        val cats = categoryNames.joinToString(", ") { "\"$it\"" }
        return """
            You read a shop receipt (OCR text below) and break it into its
            individual purchased line items.

            Receipt:
            ""${'"'}
            $text
            ""${'"'}

            Available categories: [$cats]

            Reply with ONLY a JSON object, no explanation:
            {"merchant": "shop name or null",
             "date": "purchase date and time as YYYY-MM-DD HH:MM (24-hour); convert any
                      format you see (e.g. DD/MM/YYYY) to this; null if none is printed",
             "items": [
               {"description": "item name", "amount": number, "category": "one of the categories above, or null"}
             ]}

            Rules: include only actual purchased items. Item prices already
            INCLUDE tax/VAT, so the items must sum to the receipt's grand total —
            do NOT add a separate tax line. EXCLUDE the subtotal, any tax/VAT
            summary, the total, tip, change, card/cash tender, and loyalty lines.
            Amounts are positive. If you can't place a category, use null.
        """.trimIndent()
    }

    /** Parses the model's JSON reply. Pure/testable. */
    fun parseProposal(raw: String): Proposal {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return Proposal(null, emptyList(), null)
        val body = raw.substring(start, end + 1)

        val merchant = stringField(body, "merchant")?.takeIf { it.isNotBlank() && it != "null" }
        val timestamp = parseDateTime(stringField(body, "date"))

        // Slice out the items array and read each {...} object independently —
        // resilient to the model's whitespace/formatting quirks.
        val itemsStart = body.indexOf("\"items\"")
        val arrStart = if (itemsStart >= 0) body.indexOf('[', itemsStart) else -1
        val arrEnd = if (arrStart >= 0) body.indexOf(']', arrStart) else -1
        val lines = mutableListOf<Line>()
        if (arrStart in 0 until arrEnd) {
            val arr = body.substring(arrStart + 1, arrEnd)
            for (obj in Regex("\\{[^{}]*\\}").findAll(arr)) {
                val o = obj.value
                val amount = amountField(o, "amount") ?: continue
                if (amount <= 0) continue
                val desc = stringField(o, "description")?.takeIf { it.isNotBlank() } ?: "Item"
                val cat = stringField(o, "category")?.takeIf { it.isNotBlank() && it != "null" }
                lines += Line(desc, amount, cat)
            }
        }
        return Proposal(merchant, lines, timestamp)
    }

    /** Parses an ISO-ish date/time to epoch millis at the device zone. Pure/testable. */
    fun parseDateTime(s: String?): Long? {
        val t = s?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val zone = java.time.ZoneId.systemDefault()
        val dateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        )
        for (p in dateTimePatterns) {
            try {
                val fmt = java.time.format.DateTimeFormatter.ofPattern(p)
                return java.time.LocalDateTime.parse(t, fmt)
                    .atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return try {
            java.time.LocalDate.parse(t).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun stringField(json: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return m.groupValues[1].replace("\\\"", "\"").trim()
    }

    private fun amountField(json: String, key: String): Long? {
        val m = Regex("\"$key\"\\s*:\\s*\"?([-+]?[\\d,]*\\.?\\d+)\"?").find(json) ?: return null
        val cents = BluecoinsImport.parseAmountCents(m.groupValues[1]) ?: return null
        return kotlin.math.abs(cents)
    }
}
