package com.endgamefinance.data.ai

import android.content.Context

/**
 * Natural-language → read-only answer pipeline:
 * question → (Gemma) SQL → SqlGuard → read-only execution → (Gemma) summary.
 * Every generated statement passes through SqlGuard and runs on the physically
 * read-only connection; nothing here can write.
 */
object QueryEngine {

    data class Answer(
        val summary: String,
        val sql: String,
        val result: ReadOnlyQuery.Result,
        /** (label, value) pairs when the shape is chartable, else null. */
        val chart: List<Pair<String, Double>>?,
    )

    class QueryError(message: String) : Exception(message)

    suspend fun ask(context: Context, question: String): Answer {
        val rawSql = GemmaEngine.generate(context, sqlPrompt(question))
        val sql = extractSql(rawSql)
            ?: throw QueryError("I couldn't turn that into a query. Try rephrasing.")

        SqlGuard.reject(sql)?.let {
            // Defense in depth: this should be rare; surface it plainly.
            throw QueryError("That question would need a query I'm not allowed to run ($it)")
        }

        val result = try {
            ReadOnlyQuery.run(context, sql)
        } catch (e: ReadOnlyQuery.UnsafeQueryException) {
            throw QueryError("Blocked an unsafe query. Nothing was changed.")
        } catch (e: Exception) {
            throw QueryError("The query failed: ${e.message}")
        }

        var summary = GemmaEngine.generate(context, summaryPrompt(question, result)).trim()
        // The model sometimes ignores the symbol instruction and emits "$".
        // Currency is a display-only preference (no FX), so rewriting is safe.
        val symbol = com.endgamefinance.util.Money.symbol
        if (symbol != "$") summary = summary.replace("$", symbol)
        return Answer(
            summary = summary.ifBlank { fallbackSummary(result) },
            sql = sql,
            result = result,
            chart = chartFrom(result),
        )
    }

    private fun sqlPrompt(question: String): String = """
        You are a SQL assistant for a personal finance app backed by SQLite.
        Translate the user's question into exactly ONE SQLite SELECT statement.

        Rules:
        - Output ONLY the SQL. No explanation, no markdown fences.
        - Query ONLY these read-only views (never any other table):
        ${QueryViews.schemaForPrompt}
        - Distinguish direction with type = 'expense' | 'income' | 'transfer'.
          Amounts are positive numbers; use type to know if money went out or in.
        - Dates: column date is 'YYYY-MM-DD', month is 'YYYY-MM', year is 'YYYY'.
          "This month" is strftime('%Y-%m','now','localtime').
        - Prefer GROUP BY for "by category/month" questions; ORDER BY a sensible column.
        - Never write to the database.

        - When matching a merchant, payee, or category NAME from the question,
          NEVER use exact equality — names rarely match exactly. Always match
          loosely across payee, category, and notes:
          (payee LIKE '%name%' OR category LIKE '%name%' OR notes LIKE '%name%')
        - Wrap SUM in COALESCE(SUM(...), 0) so "no matches" returns 0, not empty.

        - Recurring bills/subscriptions live in v_reminders. Questions about the
          FUTURE ("will I spend", "by the end of the year", "what does X cost me
          per month") must use v_reminders, optionally plus v_transactions for
          the already-spent part.

        Examples:
        Q: "How much have I spent on Netflix so far?"
        SQL: SELECT COALESCE(SUM(amount), 0) AS total_spent FROM v_transactions WHERE type='expense' AND (payee LIKE '%netflix%' OR category LIKE '%netflix%' OR notes LIKE '%netflix%')
        Q: "How much will I have spent on Netflix by the end of the year?"
        SQL: SELECT (SELECT COALESCE(SUM(amount), 0) FROM v_transactions WHERE type='expense' AND (payee LIKE '%netflix%' OR category LIKE '%netflix%' OR notes LIKE '%netflix%') AND year = strftime('%Y','now','localtime')) + (SELECT COALESCE(SUM(amount * (13 - CAST(strftime('%m', next_due) AS INTEGER))), 0) FROM v_reminders WHERE name LIKE '%netflix%' AND frequency='monthly' AND strftime('%Y', next_due) = strftime('%Y','now','localtime')) AS total_by_year_end
        Q: "What do my subscriptions cost per month?"
        SQL: SELECT name, amount FROM v_reminders WHERE frequency='monthly' ORDER BY amount DESC
        Q: "How much can I spend?" (or "what's left to spend", "am I over budget")
        SQL: SELECT SUM(remaining) AS left_to_spend FROM v_budget_status WHERE month = strftime('%Y-%m','now','localtime')
        Q: "How much did I spend on groceries this month?"
        SQL: SELECT COALESCE(SUM(amount), 0) AS spent FROM v_transactions WHERE type='expense' AND category LIKE '%grocer%' AND month = strftime('%Y-%m','now','localtime')
        Q: "What are my account balances?"
        SQL: SELECT name, balance FROM v_accounts ORDER BY balance DESC
        Q: "How are my savings goals doing?"
        SQL: SELECT name, saved, target, ROUND(100.0 * saved / target) AS pct FROM v_envelopes ORDER BY pct DESC
        Q: "What's my net worth?" (for "how has it changed": select several dates)
        SQL: SELECT net_worth FROM v_net_worth ORDER BY date DESC LIMIT 1
        Q: "How much credit do I have left on my cards?"
        SQL: SELECT name, credit_limit + balance AS available FROM v_accounts WHERE credit_limit IS NOT NULL
        Q: "How much did the vacation cost?" (tag-based spending)
        SQL: SELECT COALESCE(SUM(amount), 0) AS total FROM v_tagged_transactions WHERE type='expense' AND tag LIKE '%vacation%'

        Question: "$question"
        SQL:
    """.trimIndent()

    private fun summaryPrompt(question: String, result: ReadOnlyQuery.Result): String {
        val cols = result.columns.joinToString(", ")
        val rows = result.rows.take(20).joinToString("\n") { row ->
            row.joinToString(" | ") { it?.toString() ?: "" }
        }
        return """
            The user asked: "$question"
            A database query returned these results.
            Columns: $cols
            Rows:
            $rows

            Write a short, friendly answer (1-3 sentences) to the question using these
            numbers. Money amounts must be written with the user's currency symbol
            "${com.endgamefinance.util.Money.symbol}" before the number (never use
            any other currency symbol). Do not mention SQL or databases. A total of 0
            or an empty sum means no matching transactions — answer plainly that
            nothing was spent/found; don't apologize or say you failed. Only if
            the result columns are clearly unrelated to what was asked should you
            say you couldn't work it out — never invent an answer from unrelated
            numbers.
            Answer:
        """.trimIndent()
    }

    /** Strip fences/prose and isolate the SELECT/WITH statement. */
    fun extractSql(raw: String): String? {
        var s = raw.trim()
        s = s.replace(Regex("```sql", RegexOption.IGNORE_CASE), "").replace("```", "").trim()
        val idx = Regex("(?i)\\b(select|with)\\b").find(s)?.range?.first ?: return null
        s = s.substring(idx)
        val semi = s.indexOf(';')
        if (semi >= 0) s = s.substring(0, semi)
        return s.trim().ifBlank { null }
    }

    /** Chartable when there's one text label column and exactly one numeric column, few rows. */
    private fun chartFrom(result: ReadOnlyQuery.Result): List<Pair<String, Double>>? {
        if (result.rows.isEmpty() || result.rows.size > 24) return null
        if (result.columns.size != 2) return null
        val c0Numeric = result.rows.all { it[0] is Long || it[0] is Double }
        val c1Numeric = result.rows.all { it[1] is Long || it[1] is Double }
        val (labelIdx, valueIdx) = when {
            !c0Numeric && c1Numeric -> 0 to 1
            !c1Numeric && c0Numeric -> 1 to 0
            else -> return null
        }
        return result.rows.map { row ->
            val label = row[labelIdx]?.toString() ?: "—"
            val value = (row[valueIdx] as? Number)?.toDouble() ?: 0.0
            label to value
        }
    }

    private fun fallbackSummary(result: ReadOnlyQuery.Result): String =
        if (result.rows.isEmpty()) "No matching data."
        else "Found ${result.rows.size} result${if (result.rows.size == 1) "" else "s"}."
}
