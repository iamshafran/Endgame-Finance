package com.endgamefinance.data.ai

/**
 * Read-only VIEWs the AI query engine is allowed to touch, and nothing else.
 * Created (DROP + CREATE, so definitions stay current) on every DB open via a
 * Room callback. They flatten the schema into flat, human-named columns so the
 * model can write simple SELECTs. The AI NEVER sees the base tables.
 */
object QueryViews {

    /** The only names the validator permits in FROM/JOIN. */
    val WHITELIST = setOf(
        "v_transactions", "v_accounts", "v_budgets", "v_categories",
        "v_budget_status", "v_reminders", "v_envelopes", "v_envelope_transfers",
        "v_net_worth", "v_tagged_transactions",
    )

    /** Balance sign convention matches AccountDao.BALANCE_SUBQUERY (liabilities negative). */
    private const val BALANCE = """
        COALESCE((
            SELECT SUM(CASE
                WHEN t.type='income'   AND t.account_id=a.id THEN s.amount
                WHEN t.type='expense'  AND t.account_id=a.id THEN -s.amount
                WHEN t.type='transfer' AND t.account_id=a.id THEN -s.amount
                WHEN t.type='transfer' AND t.to_account_id=a.id
                    THEN (CASE WHEN s.category_id IS NULL THEN s.amount ELSE 0 END)
                ELSE 0 END)
            FROM transactions t JOIN transaction_splits s ON s.transaction_id=t.id
            WHERE t.account_id=a.id OR t.to_account_id=a.id
        ), 0)
    """

    val ddl: List<String> = listOf(
        "DROP VIEW IF EXISTS v_transactions",
        """
        CREATE VIEW v_transactions AS
        SELECT t.id AS transaction_id,
               t.timestamp AS timestamp_ms,
               strftime('%Y-%m-%d', t.timestamp/1000, 'unixepoch', 'localtime') AS date,
               strftime('%Y-%m', t.timestamp/1000, 'unixepoch', 'localtime') AS month,
               strftime('%Y', t.timestamp/1000, 'unixepoch', 'localtime') AS year,
               t.type AS type,
               t.payee AS payee,
               a.name AS account,
               b.name AS to_account,
               c.name AS category,
               s.amount AS amount_cents,
               (s.amount / 100.0) AS amount,
               t.is_cleared AS cleared,
               t.is_shared AS shared,
               t.notes AS notes
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        JOIN accounts a ON a.id = t.account_id
        LEFT JOIN accounts b ON b.id = t.to_account_id
        LEFT JOIN categories c ON c.id = s.category_id
        WHERE t.payee != 'Starting Balance'
        """,
        "DROP VIEW IF EXISTS v_accounts",
        """
        CREATE VIEW v_accounts AS
        SELECT a.name AS name, a.type AS type, ($BALANCE / 100.0) AS balance,
               (a.credit_limit / 100.0) AS credit_limit,
               (a.original_principal / 100.0) AS original_loan
        FROM accounts a WHERE a.is_active = 1
        """,
        "DROP VIEW IF EXISTS v_budgets",
        """
        CREATE VIEW v_budgets AS
        SELECT b.month AS month, c.name AS category,
               (b.allocated_amount / 100.0) AS allocated, b.rollover_mode AS rollover
        FROM budgets b JOIN categories c ON c.id = b.category_id
        """,
        "DROP VIEW IF EXISTS v_categories",
        """
        CREATE VIEW v_categories AS
        SELECT c.name AS name, c.type AS type, g.name AS category_group
        FROM categories c LEFT JOIN category_groups g ON g.id = c.group_id
        """,
        "DROP VIEW IF EXISTS v_budget_status",
        // Budget vs actual per category-month, so "how much can I spend"-style
        // questions have real data. spent = expense splits in that category+month;
        // remaining = allocated − spent (this month's allocation only; rollover
        // carry is intentionally not folded in — the dashboard remains the
        // authority for Safe-to-Spend).
        """
        CREATE VIEW v_budget_status AS
        SELECT b.month AS month,
               c.name AS category,
               (b.allocated_amount / 100.0) AS allocated,
               COALESCE((
                   SELECT SUM(s.amount)
                   FROM transaction_splits s
                   JOIN transactions t ON t.id = s.transaction_id
                   WHERE s.category_id = b.category_id
                     AND t.type = 'expense'
                     AND t.payee != 'Starting Balance'
                     AND strftime('%Y-%m', t.timestamp/1000, 'unixepoch', 'localtime') = b.month
               ), 0) / 100.0 AS spent,
               (b.allocated_amount - COALESCE((
                   SELECT SUM(s.amount)
                   FROM transaction_splits s
                   JOIN transactions t ON t.id = s.transaction_id
                   WHERE s.category_id = b.category_id
                     AND t.type = 'expense'
                     AND t.payee != 'Starting Balance'
                     AND strftime('%Y-%m', t.timestamp/1000, 'unixepoch', 'localtime') = b.month
               ), 0)) / 100.0 AS remaining
        FROM budgets b JOIN categories c ON c.id = b.category_id
        """,
        "DROP VIEW IF EXISTS v_reminders",
        // Scheduled bills / subscriptions, so the AI can answer forecast
        // questions ("what will Netflix cost me by December"). amount is NULL
        // for variable-amount reminders.
        """
        CREATE VIEW v_reminders AS
        SELECT r.name AS name,
               c.name AS category,
               a.name AS account,
               (r.amount / 100.0) AS amount,
               r.frequency AS frequency,
               r.frequency_interval AS every_n,
               strftime('%Y-%m-%d', r.next_due_date/1000, 'unixepoch', 'localtime') AS next_due,
               r.is_auto_post AS auto_post
        FROM reminders r
        JOIN accounts a ON a.id = r.account_id
        LEFT JOIN categories c ON c.id = r.category_id
        """,
        "DROP VIEW IF EXISTS v_envelopes",
        // Savings goals / envelopes. current_amount is authoritative (M2 rule).
        """
        CREATE VIEW v_envelopes AS
        SELECT e.name AS name,
               (e.current_amount / 100.0) AS saved,
               (e.target_amount / 100.0) AS target,
               a.name AS linked_account
        FROM envelopes e
        LEFT JOIN accounts a ON a.id = e.linked_account_id
        """,
        "DROP VIEW IF EXISTS v_envelope_transfers",
        // Envelope funding history. NULL from/to = unallocated funds.
        """
        CREATE VIEW v_envelope_transfers AS
        SELECT strftime('%Y-%m-%d', et.timestamp/1000, 'unixepoch', 'localtime') AS date,
               strftime('%Y-%m', et.timestamp/1000, 'unixepoch', 'localtime') AS month,
               f.name AS from_envelope,
               t.name AS to_envelope,
               (et.amount / 100.0) AS amount
        FROM envelope_transfers et
        LEFT JOIN envelopes f ON f.id = et.from_envelope_id
        LEFT JOIN envelopes t ON t.id = et.to_envelope_id
        """,
        "DROP VIEW IF EXISTS v_net_worth",
        """
        CREATE VIEW v_net_worth AS
        SELECT strftime('%Y-%m-%d', snapshot_date/1000, 'unixepoch', 'localtime') AS date,
               (total_assets / 100.0) AS assets,
               (total_liabilities / 100.0) AS liabilities,
               (net_worth / 100.0) AS net_worth
        FROM net_worth_snapshots
        """,
        "DROP VIEW IF EXISTS v_tagged_transactions",
        // Tag-level spending: one row per (tag, split).
        """
        CREATE VIEW v_tagged_transactions AS
        SELECT g.name AS tag,
               strftime('%Y-%m-%d', t.timestamp/1000, 'unixepoch', 'localtime') AS date,
               strftime('%Y-%m', t.timestamp/1000, 'unixepoch', 'localtime') AS month,
               t.type AS type,
               t.payee AS payee,
               c.name AS category,
               (s.amount / 100.0) AS amount
        FROM transaction_tags tt
        JOIN tags g ON g.id = tt.tag_id
        JOIN transactions t ON t.id = tt.transaction_id
        JOIN transaction_splits s ON s.transaction_id = t.id
        LEFT JOIN categories c ON c.id = s.category_id
        WHERE t.payee != 'Starting Balance'
        """,
    )

    /** Schema description injected into the model prompt (Milestone 7.3). */
    val schemaForPrompt: String = """
        v_transactions(transaction_id, timestamp_ms, date 'YYYY-MM-DD', month 'YYYY-MM', year 'YYYY', type ['expense'|'income'|'transfer'], payee, account, to_account, category, amount_cents, amount, cleared 0/1, shared 0/1, notes) — one row per category split; income positive, expense positive amounts (use type to distinguish); loan interest is an expense category on a transfer.
        v_accounts(name, type ['asset'|'liability'|'investment'], balance, credit_limit, original_loan) — liability balances are negative; available credit = credit_limit + balance; loan progress = 1 + balance/original_loan.
        v_budgets(month 'YYYY-MM', category, allocated, rollover)
        v_categories(name, type ['expense'|'income'], category_group)
        v_budget_status(month 'YYYY-MM', category, allocated, spent, remaining) — budget vs actual per category and month; remaining = allocated - spent.
        v_reminders(name, category, account, amount, frequency ['daily'|'weekly'|'monthly'|'yearly'|'once'], every_n, next_due 'YYYY-MM-DD', auto_post 0/1) — scheduled bills and subscriptions; amount NULL when variable.
        v_envelopes(name, saved, target, linked_account) — savings goals; progress = saved/target.
        v_envelope_transfers(date, month, from_envelope, to_envelope, amount) — envelope funding history; NULL envelope = unallocated funds.
        v_net_worth(date 'YYYY-MM-DD', assets, liabilities, net_worth) — periodic snapshots, newest = current.
        v_tagged_transactions(tag, date, month, type, payee, category, amount) — spending by tag (tags cut across categories).
    """.trimIndent()
}
