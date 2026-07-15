package com.endgamefinance.data.ai

import com.endgamefinance.data.db.model.DaySpendLine
import org.junit.Assert.assertTrue
import org.junit.Test

/** The anomaly-explanation prompt must feed the model the day's real figures
 *  and purchases, and stay descriptive (no advice). (Milestone 8.3) */
class AnomalyExplainerTest {

    private val ctx = AnomalyExplainer.DayContext(
        dateLabel = "2026-07-04",
        spentCents = 12_000L,
        avgDailyCents = 3_000L,
        lines = listOf(
            DaySpendLine("Currys", "Electronics", 9_000L),
            DaySpendLine("Tesco", "Groceries", 3_000L),
        ),
    )

    @Test
    fun prompt_includes_totals_average_and_each_purchase() {
        val p = AnomalyExplainer.buildPrompt(ctx)
        assertTrue(p.contains("2026-07-04"))
        assertTrue(p.contains("120.00")) // total spent
        assertTrue(p.contains("30.00")) // 90-day average
        assertTrue(p.contains("Currys"))
        assertTrue(p.contains("Electronics"))
        assertTrue(p.contains("Tesco"))
    }

    @Test
    fun prompt_reports_the_ratio_when_average_known() {
        val p = AnomalyExplainer.buildPrompt(ctx)
        assertTrue(p.contains("4.0×")) // 12000 / 3000
    }

    @Test
    fun prompt_handles_no_established_average() {
        val p = AnomalyExplainer.buildPrompt(ctx.copy(avgDailyCents = 0L))
        assertTrue(p.contains("not established"))
    }

    @Test
    fun prompt_forbids_advice() {
        val p = AnomalyExplainer.buildPrompt(ctx)
        assertTrue(p.contains("do NOT give advice", ignoreCase = true))
    }
}
