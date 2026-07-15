package com.endgamefinance.data.ai

/**
 * First line of defense for the read-only query engine: reject anything that
 * isn't a single benign SELECT over whitelisted views. This is validation;
 * the read-only connection ([ReadOnlyQuery]) is the physical guarantee.
 */
object SqlGuard {

    // Statement-level write/DDL keywords. 'replace' is intentionally absent so
    // the REPLACE() string function still works — a REPLACE statement is already
    // blocked by the SELECT/WITH start requirement.
    private val forbidden = listOf(
        "insert", "update", "delete", "drop", "alter", "create", "attach",
        "detach", "pragma", "vacuum", "reindex", "trigger", "grant",
        "commit", "rollback", "savepoint", "truncate", "load_extension",
    )

    /** Returns null if [sqlRaw] is safe to run, else a human-readable reason. */
    fun reject(sqlRaw: String): String? {
        val sql = stripComments(sqlRaw).trim().trimEnd(';').trim()
        if (sql.isEmpty()) return "The query is empty."
        if (sql.contains(';')) return "Only a single statement is allowed."

        val lower = sql.lowercase()
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            return "Only SELECT queries are allowed."
        }
        forbidden.firstOrNull { Regex("\\b$it\\b").containsMatchIn(lower) }?.let {
            return "The keyword '$it' is not allowed in a read-only query."
        }
        // Names defined by CTEs (WITH x AS (...)) are legitimate FROM targets.
        val cteNames = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)\s+as\s*\(""")
            .findAll(lower).map { it.groupValues[1] }.toSet()
        val allowed = QueryViews.WHITELIST + cteNames

        // Every FROM/JOIN target must be a whitelisted view or a CTE name.
        val refs = Regex("""\b(?:from|join)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
            .findAll(lower).map { it.groupValues[1] }.toSet()
        refs.firstOrNull { it !in allowed }?.let {
            return "The table/view '$it' is not queryable. Allowed: " +
                QueryViews.WHITELIST.joinToString(", ") + "."
        }
        return null
    }

    private fun stripComments(sql: String): String =
        sql.replace(Regex("--[^\n]*"), " ")
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
}
