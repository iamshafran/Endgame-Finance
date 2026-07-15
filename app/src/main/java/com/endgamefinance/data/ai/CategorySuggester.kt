package com.endgamefinance.data.ai

/**
 * Payee → category auto-suggestion from historical data (Milestone 7.4).
 * Classical character-trigram profile — no LLM in the loop, so it's instant,
 * deterministic, and works offline before/without the model download.
 *
 * Build once from history via [build]; ask with [suggest]. Rebuild after an
 * import or in a new session — cheap (one pass over payee/category pairs).
 */
class CategorySuggester private constructor(
    /** payee-normalized → (categoryId, trigram vector) */
    private val profiles: List<Profile>,
) {

    private data class Profile(
        val normalizedPayee: String,
        val categoryId: String,
        val trigrams: Map<String, Int>,
    )

    data class Suggestion(val categoryId: String, val confidence: Double)

    companion object {

        /**
         * [history]: (payee, categoryId) pairs from posted transactions.
         * The majority category wins for each distinct payee.
         */
        fun build(history: List<Pair<String, String>>): CategorySuggester {
            val byPayee = history
                .mapNotNull { (payee, cat) ->
                    val n = normalize(payee)
                    if (n.isBlank()) null else n to cat
                }
                .groupBy({ it.first }, { it.second })
            val profiles = byPayee.map { (payee, cats) ->
                val majority = cats.groupingBy { it }.eachCount().maxBy { it.value }.key
                Profile(payee, majority, trigramsOf(payee))
            }
            return CategorySuggester(profiles)
        }

        fun normalize(s: String): String =
            s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()
                .replace(Regex(" +"), " ")

        fun trigramsOf(s: String): Map<String, Int> {
            val padded = "  $s "
            val counts = mutableMapOf<String, Int>()
            for (i in 0..padded.length - 3) {
                val g = padded.substring(i, i + 3)
                counts[g] = (counts[g] ?: 0) + 1
            }
            return counts
        }

        private fun cosine(a: Map<String, Int>, b: Map<String, Int>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var dot = 0L
            for ((g, c) in a) b[g]?.let { dot += c.toLong() * it }
            if (dot == 0L) return 0.0
            val na = Math.sqrt(a.values.sumOf { it.toLong() * it }.toDouble())
            val nb = Math.sqrt(b.values.sumOf { it.toLong() * it }.toDouble())
            return dot / (na * nb)
        }
    }

    /** Best category for [payee], or null when nothing is similar enough. */
    fun suggest(payee: String, minConfidence: Double = 0.55): Suggestion? {
        val n = normalize(payee)
        if (n.isBlank() || profiles.isEmpty()) return null

        // Exact or containment match first — instant and unambiguous.
        profiles.firstOrNull { it.normalizedPayee == n }
            ?.let { return Suggestion(it.categoryId, 1.0) }
        profiles.firstOrNull { n.length >= 4 && (n in it.normalizedPayee || it.normalizedPayee in n) }
            ?.let { return Suggestion(it.categoryId, 0.9) }

        // Fuzzy: trigram cosine similarity.
        val target = trigramsOf(n)
        val best = profiles
            .map { it to cosine(target, it.trigrams) }
            .maxByOrNull { it.second } ?: return null
        return if (best.second >= minConfidence) {
            Suggestion(best.first.categoryId, best.second)
        } else {
            null
        }
    }
}
