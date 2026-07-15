package com.endgamefinance.data.notify

import android.content.Context
import com.endgamefinance.data.ai.GemmaEngine
import com.endgamefinance.data.migrate.BluecoinsImport

/**
 * Turns a raw banking/card notification string into a structured, *proposed*
 * transaction using the on-device model (Milestone 8.1). Nothing here posts to
 * the ledger — it only produces a suggestion the user (or an explicit auto-post
 * opt-in) confirms.
 *
 * A cheap regex pre-filter ([looksLikeMoney]) runs first so the LLM is only
 * invoked on notifications that actually mention an amount — most notifications
 * never reach the model.
 */
object NotificationParser {

    /** A proposed transaction. Amounts are positive cents; [type] is
     *  'expense' | 'income' | 'transfer'. [accountHint]/[categoryGuess] are the
     *  model's best guesses, resolved to real ids downstream. */
    data class Parsed(
        val payee: String,
        val amountCents: Long,
        val type: String,
        val categoryGuess: String?,
        val accountHint: String?,
    )

    // Currency-ish amount: symbol-prefixed (£12.34, $1,234.56, €9,99) or an
    // ISO-code-prefixed amount (GBP 12.34). Kept deliberately permissive.
    private val MONEY = Regex(
        """(?:[£$€₹]\s?\d[\d,]*(?:[.,]\d{1,2})?)|(?:\b[A-Z]{3}\s?\d[\d,]*(?:[.,]\d{1,2})?)""",
    )

    /** Fast gate: does this text plausibly describe a money movement? */
    fun looksLikeMoney(text: String): Boolean = MONEY.containsMatchIn(text)

    /**
     * Runs the model on [title] + [text]. Returns a proposal or null when the
     * model decides it isn't a transaction (or output can't be parsed).
     */
    suspend fun parse(context: Context, title: String, text: String): Parsed? {
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        if (!looksLikeMoney(body)) return null
        val raw = GemmaEngine.generate(context, prompt(body))
        return parseResult(raw)
    }

    private fun prompt(body: String): String = """
        You extract a single financial transaction from a phone notification.
        Notification text:
        "$body"

        Reply with ONLY a JSON object, no explanation:
        {"is_transaction": true|false,
         "payee": "merchant or sender, short",
         "amount": number (the money value, positive),
         "type": "expense"|"income"|"transfer",
         "category": "a spending category, or null",
         "account": "the card/account named in the text, or null"}

        Set is_transaction to false for OTPs, balance summaries, marketing, or
        anything that isn't a specific completed payment or credit.
    """.trimIndent()

    /** Parses the model's JSON reply into a [Parsed], or null. Pure/testable. */
    fun parseResult(raw: String): Parsed? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = raw.substring(start, end + 1)

        if (boolField(body, "is_transaction") == false) return null

        val amountCents = amountField(body, "amount") ?: return null
        if (amountCents <= 0) return null

        val payee = stringField(body, "payee")?.takeIf { it.isNotBlank() } ?: "Payment"
        val type = when (stringField(body, "type")?.lowercase()) {
            "income" -> "income"
            "transfer" -> "transfer"
            else -> "expense"
        }
        return Parsed(
            payee = payee,
            amountCents = amountCents,
            type = type,
            categoryGuess = stringField(body, "category")?.takeIf { it.isNotBlank() && it != "null" },
            accountHint = stringField(body, "account")?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    // ---- tolerant single-field JSON readers (Gemma isn't a strict encoder) ----

    private fun stringField(json: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return m.groupValues[1].replace("\\\"", "\"").trim()
    }

    private fun boolField(json: String, key: String): Boolean? {
        val m = Regex("\"$key\"\\s*:\\s*(true|false)").find(json) ?: return null
        return m.groupValues[1] == "true"
    }

    /** Reads a numeric or quoted amount and converts to positive cents. */
    fun amountField(json: String, key: String): Long? {
        val m = Regex("\"$key\"\\s*:\\s*\"?([-+]?[\\d,]*\\.?\\d+)\"?").find(json) ?: return null
        val cents = BluecoinsImport.parseAmountCents(m.groupValues[1]) ?: return null
        return kotlin.math.abs(cents)
    }
}
