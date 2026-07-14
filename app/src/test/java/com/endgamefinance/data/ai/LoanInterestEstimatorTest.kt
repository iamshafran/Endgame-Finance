package com.endgamefinance.data.ai

import com.endgamefinance.data.db.model.LoanPaymentHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the loan interest estimator (Milestone 8.4). */
class LoanInterestEstimatorTest {

    private val history = listOf(
        LoanPaymentHistory(timestamp = 3, totalCents = 13_433, interestCents = 2_000),
        LoanPaymentHistory(timestamp = 2, totalCents = 13_433, interestCents = 2_100),
    )

    @Test
    fun prompt_lists_history_and_this_payment() {
        val p = LoanInterestEstimator.buildPrompt(13_433, history)
        assertTrue(p.contains("134.33")) // payment total
        assertTrue(p.contains("20.00")) // an interest figure
        assertTrue(p.contains("interest"))
    }

    @Test
    fun parses_interest_and_clamps_to_payment() {
        assertEquals(1_234L, LoanInterestEstimator.parseInterest("about 12.34", 13_433))
        // Model over-shoots the payment → clamp to the total.
        assertEquals(13_433L, LoanInterestEstimator.parseInterest("999.99", 13_433))
        assertNull(LoanInterestEstimator.parseInterest("no number here", 13_433))
    }

    @Test
    fun ratio_fallback_uses_latest_payment_ratio() {
        // latest: 2000/13433 of 13433 ≈ 2000
        assertEquals(2_000L, LoanInterestEstimator.ratioFallback(13_433, history))
    }

    @Test
    fun ratio_fallback_scales_with_a_different_payment() {
        // 2000/13433 ≈ 0.1489 of 20000 ≈ 2978
        val est = LoanInterestEstimator.ratioFallback(20_000, history)
        assertTrue(est in 2_900..3_050)
    }

    @Test
    fun ratio_fallback_is_zero_without_interest_history() {
        val noInterest = listOf(LoanPaymentHistory(1, 10_000, 0))
        assertEquals(0L, LoanInterestEstimator.ratioFallback(10_000, noInterest))
        assertEquals(0L, LoanInterestEstimator.ratioFallback(10_000, emptyList()))
    }
}
