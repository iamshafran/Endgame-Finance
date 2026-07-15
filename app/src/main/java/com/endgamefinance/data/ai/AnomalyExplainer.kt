package com.endgamefinance.data.ai

import android.content.Context
import com.endgamefinance.data.db.model.DaySpendLine
import com.endgamefinance.util.Money

/**
 * Plain-language, on-device explanation of what drove a day's spending
 * (Milestone 8.3). Read-only and descriptive — it narrates the day's actual
 * purchases, it does not give advice or touch the ledger.
 */
object AnomalyExplainer {

    data class DayContext(
        val dateLabel: String,
        val spentCents: Long,
        val avgDailyCents: Long,
        val lines: List<DaySpendLine>,
    )

    /** Builds the model prompt. Pure/testable. */
    fun buildPrompt(ctx: DayContext): String {
        val purchases = ctx.lines.joinToString("\n") { line ->
            val cat = line.category?.let { " ($it)" } ?: ""
            "- ${line.payee}$cat — ${Money.format(line.amountCents)}"
        }
        val comparison = if (ctx.avgDailyCents > 0) {
            val ratio = ctx.spentCents.toDouble() / ctx.avgDailyCents
            "Their usual daily spend (90-day average) is ${Money.format(ctx.avgDailyCents)} — " +
                "this day is about ${"%.1f".format(ratio)}× that."
        } else {
            "Their usual daily spend is not established yet."
        }
        return """
            You explain a single day's spending to the person who spent it, in
            plain, friendly language.

            Date: ${ctx.dateLabel}
            Total spent that day: ${Money.format(ctx.spentCents)}
            $comparison

            Purchases (largest first):
            $purchases

            In 2-3 short sentences, explain what most likely drove this day's
            spending. Name the biggest one or two purchases or categories. Just
            describe what happened — do NOT give advice, judgement, or budgeting
            tips.
        """.trimIndent()
    }

    suspend fun explain(context: Context, ctx: DayContext): String =
        GemmaEngine.generate(context, buildPrompt(ctx))
}
