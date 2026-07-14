package com.endgamefinance.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Auto-category suggestions from history (M7 acceptance criterion). */
class CategorySuggesterTest {

    private val suggester = CategorySuggester.build(
        listOf(
            "Sainsbury Shopping" to "cat-grocery",
            "Sainsbury Shopping" to "cat-grocery",
            "Londis" to "cat-grocery",
            "Netflix" to "cat-subscriptions",
            "O2 bill" to "cat-mobile",
            "Cigarette" to "cat-smoking",
            "Fuel" to "cat-fuel",
        ),
    )

    @Test
    fun exact_payee_matches() {
        assertEquals("cat-smoking", suggester.suggest("Cigarette")?.categoryId)
    }

    @Test
    fun case_and_punctuation_insensitive() {
        assertEquals("cat-subscriptions", suggester.suggest("NETFLIX!")?.categoryId)
    }

    @Test
    fun containment_matches_variants() {
        // "Sainsburys" ⊂ profile / profile ⊂ new payee
        assertEquals("cat-grocery", suggester.suggest("Sainsbury Shopping Hemel")?.categoryId)
        assertEquals("cat-fuel", suggester.suggest("Fuel Esso")?.categoryId)
    }

    @Test
    fun fuzzy_similarity_matches_typos() {
        val s = suggester.suggest("Sainsbury Shoping") // missing 'p'
        assertNotNull(s)
        assertEquals("cat-grocery", s?.categoryId)
    }

    @Test
    fun majority_category_wins_per_payee() {
        val mixed = CategorySuggester.build(
            listOf(
                "Amazon" to "cat-shopping",
                "Amazon" to "cat-shopping",
                "Amazon" to "cat-gifts",
            ),
        )
        assertEquals("cat-shopping", mixed.suggest("Amazon")?.categoryId)
    }

    @Test
    fun unrelated_payee_gets_no_suggestion() {
        assertNull(suggester.suggest("Zzq Quantum Widgets"))
    }

    @Test
    fun blank_payee_gets_no_suggestion() {
        assertNull(suggester.suggest("   "))
    }
}
