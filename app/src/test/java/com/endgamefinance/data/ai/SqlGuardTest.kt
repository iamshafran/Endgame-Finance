package com.endgamefinance.data.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Proves the validator blocks destructive/out-of-scope SQL (acceptance criterion). */
class SqlGuardTest {

    private fun blocked(sql: String) =
        assertNotNull("Expected BLOCKED: $sql", SqlGuard.reject(sql))

    private fun allowed(sql: String) =
        assertNull("Expected ALLOWED: $sql → ${SqlGuard.reject(sql)}", SqlGuard.reject(sql))

    @Test fun blocks_delete() = blocked("DELETE FROM transactions")
    @Test fun blocks_update() = blocked("UPDATE accounts SET name='x'")
    @Test fun blocks_insert() = blocked("INSERT INTO tags(id,name) VALUES('1','x')")
    @Test fun blocks_drop() = blocked("DROP VIEW v_transactions")
    @Test fun blocks_alter() = blocked("ALTER TABLE accounts ADD COLUMN x TEXT")
    @Test fun blocks_pragma() = blocked("PRAGMA query_only=off")
    @Test fun blocks_attach() = blocked("ATTACH DATABASE 'evil.db' AS e")
    @Test fun blocks_load_extension() = blocked("SELECT load_extension('x')")

    @Test fun blocks_piggyback_delete() =
        blocked("SELECT * FROM v_transactions; DELETE FROM transactions")

    @Test fun blocks_comment_hidden_write() =
        blocked("SELECT 1 /* */ ; DELETE FROM transactions --")

    @Test fun blocks_base_table_access() = blocked("SELECT * FROM transactions")

    @Test fun blocks_sqlite_master() = blocked("SELECT * FROM sqlite_master")

    @Test fun allows_simple_aggregate() = allowed(
        "SELECT category, SUM(amount) FROM v_transactions WHERE type='expense' GROUP BY category",
    )

    @Test fun allows_join() = allowed(
        "SELECT a.name, a.balance FROM v_accounts a WHERE a.type='asset'",
    )

    @Test fun allows_cte() = allowed(
        "WITH monthly AS (SELECT month, SUM(amount) s FROM v_transactions GROUP BY month) " +
            "SELECT * FROM monthly ORDER BY month",
    )

    @Test fun allows_replace_function() = allowed(
        "SELECT REPLACE(payee, 'Inc', '') FROM v_transactions",
    )

    /** The taught year-end forecast shape: scalar subqueries over two views. */
    @Test fun allows_forecast_subqueries() = allowed(
        "SELECT (SELECT COALESCE(SUM(amount), 0) FROM v_transactions WHERE type='expense' " +
            "AND (payee LIKE '%netflix%' OR category LIKE '%netflix%' OR notes LIKE '%netflix%') " +
            "AND year = strftime('%Y','now','localtime')) + " +
            "(SELECT COALESCE(SUM(amount * (13 - CAST(strftime('%m', next_due) AS INTEGER))), 0) " +
            "FROM v_reminders WHERE name LIKE '%netflix%' AND frequency='monthly' " +
            "AND strftime('%Y', next_due) = strftime('%Y','now','localtime')) AS total_by_year_end",
    )

    @Test fun allows_reminders_view() = allowed(
        "SELECT name, amount FROM v_reminders WHERE frequency='monthly' ORDER BY amount DESC",
    )

    @Test fun allows_all_new_views() {
        allowed("SELECT name, saved, target FROM v_envelopes")
        allowed("SELECT date, from_envelope, to_envelope, amount FROM v_envelope_transfers")
        allowed("SELECT net_worth FROM v_net_worth ORDER BY date DESC LIMIT 1")
        allowed("SELECT tag, SUM(amount) FROM v_tagged_transactions GROUP BY tag")
        allowed("SELECT name, credit_limit + balance FROM v_accounts WHERE credit_limit IS NOT NULL")
    }

    /** Base tables behind the new views must still be unreachable. */
    @Test fun still_blocks_new_base_tables() {
        blocked("SELECT * FROM envelopes")
        blocked("SELECT * FROM envelope_transfers")
        blocked("SELECT * FROM net_worth_snapshots")
        blocked("SELECT * FROM tags")
        blocked("SELECT * FROM transaction_tags")
        blocked("SELECT * FROM transaction_audit")
    }
}
