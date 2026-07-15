package com.endgamefinance.data.ai

import android.content.Context
import com.endgamefinance.data.db.model.LoanPaymentHistory
import com.endgamefinance.data.migrate.BluecoinsImport
import com.endgamefinance.util.Money

/**
 * Estimates the interest portion of a loan payment from the loan's own history
 * (Milestone 8.4). The model reads prior payments' principal/interest splits and
 * projects this payment's interest; a deterministic ratio fallback covers the
 * no-model / no-history cases. The result is only ever a *suggestion* — the user
 * edits it before anything posts.
 */
object LoanInterestEstimator {

    enum class Source { AI, HISTORY, NONE }

    data class Estimate(val interestCents: Long, val source: Source)

    /** Builds the estimate prompt. Pure/testable. */
    fun buildPrompt(paymentCents: Long, history: List<LoanPaymentHistory>): String {
        val rows = history.joinToString("\n") {
            "- total ${Money.format(it.totalCents)}, interest ${Money.format(it.interestCents)}"
        }
        return """
            You estimate how much of a loan payment is interest versus principal.

            Recent payments on this loan (newest first):
            $rows

            This payment's total is ${Money.format(paymentCents)}.

            On an amortizing loan the interest portion usually decreases slightly
            each payment as the balance falls. Reply with ONLY the estimated
            interest portion of THIS payment as a plain number (e.g. 12.34).
            It must be between 0 and the payment total.
        """.trimIndent()
    }

    /** Extracts an interest amount from the model reply, clamped to the payment. Pure. */
    fun parseInterest(raw: String, paymentCents: Long): Long? {
        val m = Regex("[-+]?[\\d,]*\\.?\\d+").find(raw) ?: return null
        val cents = BluecoinsImport.parseAmountCents(m.value) ?: return null
        return kotlin.math.abs(cents).coerceIn(0, paymentCents)
    }

    /** Deterministic fallback: most recent interest/total ratio applied to this payment. */
    fun ratioFallback(paymentCents: Long, history: List<LoanPaymentHistory>): Long {
        val last = history.firstOrNull { it.interestCents > 0 && it.totalCents > 0 } ?: return 0L
        return (last.interestCents.toDouble() / last.totalCents * paymentCents)
            .toLong().coerceIn(0, paymentCents)
    }

    suspend fun estimate(
        context: Context,
        paymentCents: Long,
        history: List<LoanPaymentHistory>,
    ): Estimate {
        val fallback = ratioFallback(paymentCents, history)
        val fallbackSource = if (fallback > 0) Source.HISTORY else Source.NONE
        if (history.isEmpty() || !AiModel.isReady(context)) {
            return Estimate(fallback, fallbackSource)
        }
        return try {
            val raw = GemmaEngine.generate(context, buildPrompt(paymentCents, history))
            parseInterest(raw, paymentCents)?.let { Estimate(it, Source.AI) }
                ?: Estimate(fallback, fallbackSource)
        } catch (e: Exception) {
            Estimate(fallback, fallbackSource)
        }
    }
}
