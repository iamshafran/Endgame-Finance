# Endgame Finance — Master Spec

**Fresh rebuild. Replaces the earlier "Apex Core Budget" (ACB) project. Do not reference or port ACB code.**

## What This Is

A local-first, offline-always Android budgeting app (Kotlin / Jetpack Compose). Zero cloud dependency. Accounting-grade double-entry ledger with envelope budgeting, forecasting, and an on-device AI layer (Gemma 4 E2B via LiteRT-LM) added after the core app is solid.

Owner has limited programming experience. Claude Code is the hands-on builder; each milestone should produce a working, testable-on-device increment. Wireless debugging on a physical Android device is the test method — do not assume emulator-only verification is sufficient.

## Core Philosophy

- **Absolute privacy**: no internet permission in the manifest, ever. All inference is on-device.
- **Accounting integrity**: strict double-entry. Every transaction that touches money has a symmetric effect on the ledger.
- **Data durability**: this is meant to hold years of financial history. Favor boring, well-tested patterns over clever ones. Integer cents, not floats. Immutable audit trail, not silent overwrites.
- **MVP before AI**: the ledger, budgeting, and dashboard must work completely and be genuinely usable before any AI/LiteRT-LM code is written. AI is additive, not load-bearing.

## Tech Stack

- **Language/UI**: Kotlin, Jetpack Compose
- **Database**: Room, with SQLCipher (or Room's encryption extension) for at-rest encryption
- **Background work**: WorkManager for reminders/recurring checks (not raw AlarmManager)
- **AI runtime** (Milestone 7+ only): LiteRT-LM Android API, Gemma 4 E2B (INT4/mixed quant), NPU-preferred backend
- **No networking libraries, no analytics SDKs, no crash reporters that phone home**

## Data Model (baseline — may gain columns per milestone, do not remove without a documented reason)

```sql
-- ACCOUNTS
CREATE TABLE accounts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,          -- 'asset', 'liability', 'investment'
    credit_limit INTEGER,        -- NULL unless type='liability' and it's a credit line
    original_principal INTEGER,  -- NULL unless type='liability' loan; cents. Added in DB v3 (owner-approved, 2026-07-13) for payoff-progress display
    currency TEXT DEFAULT 'USD',
    is_active INTEGER DEFAULT 1
);

-- CATEGORIES
CREATE TABLE categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT,
    type TEXT NOT NULL,          -- 'expense' or 'income'
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- TAGS (cross-cutting, independent of categories)
CREATE TABLE tags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE transaction_tags (
    transaction_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    PRIMARY KEY (transaction_id, tag_id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- TRANSACTIONS
CREATE TABLE transactions (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    to_account_id TEXT,           -- transfers / liability payments only
    timestamp INTEGER NOT NULL,   -- epoch ms
    payee TEXT NOT NULL,
    notes TEXT,
    type TEXT NOT NULL,           -- 'expense', 'income', 'transfer'
    is_cleared INTEGER DEFAULT 0, -- reconciliation state
    is_shared INTEGER DEFAULT 0,  -- shared/reimbursable flag
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);

-- TRANSACTION SPLITS
CREATE TABLE transaction_splits (
    id TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    category_id TEXT,
    amount INTEGER NOT NULL,      -- cents
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- AUDIT LOG (immutable edit history)
CREATE TABLE transaction_audit (
    id TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    field_name TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_at INTEGER NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

-- REMINDERS
CREATE TABLE reminders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category_id TEXT,
    account_id TEXT NOT NULL,
    to_account_id TEXT,             -- destination for transfer/repayment reminders; posts as a transfer. Added in DB v5 (owner-approved, 2026-07-14)
    amount INTEGER,                 -- NULL if variable
    frequency TEXT NOT NULL,        -- 'daily','weekly','monthly','yearly','once'
    frequency_interval INTEGER NOT NULL DEFAULT 1, -- every N units, e.g. 2+weekly = biweekly. Added in DB v4 (owner-approved, 2026-07-13)
    anchor_day INTEGER,             -- intended day-of-month for monthly/yearly so month-end bills don't drift (Jan 31→Feb 28→Mar 31). Added in DB v4
    next_due_date INTEGER NOT NULL,
    is_auto_post INTEGER DEFAULT 0,
    is_auto_detected INTEGER DEFAULT 0,  -- created via recurring-transaction detection
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);

-- BUDGETS (per category, per month)
CREATE TABLE budgets (
    id TEXT PRIMARY KEY,
    category_id TEXT NOT NULL,
    month TEXT NOT NULL,            -- 'YYYY-MM'
    allocated_amount INTEGER NOT NULL,
    rollover_mode TEXT DEFAULT 'reset', -- 'reset' or 'carry'
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- ENVELOPES / SAVINGS GOALS (virtual buckets, distinct from accounts)
CREATE TABLE envelopes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    target_amount INTEGER,
    current_amount INTEGER NOT NULL DEFAULT 0,
    linked_account_id TEXT,          -- which asset account backs this envelope
    FOREIGN KEY (linked_account_id) REFERENCES accounts(id)
);

CREATE TABLE envelope_transfers (
    id TEXT PRIMARY KEY,
    from_envelope_id TEXT,           -- NULL if from unallocated funds
    to_envelope_id TEXT,             -- NULL if to unallocated funds
    amount INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (from_envelope_id) REFERENCES envelopes(id),
    FOREIGN KEY (to_envelope_id) REFERENCES envelopes(id)
);

-- NET WORTH SNAPSHOTS (periodic, not derived live)
CREATE TABLE net_worth_snapshots (
    id TEXT PRIMARY KEY,
    snapshot_date INTEGER NOT NULL,
    total_assets INTEGER NOT NULL,
    total_liabilities INTEGER NOT NULL,
    net_worth INTEGER NOT NULL
);
```

## Liability Accounting Rules (do not deviate)

- **Credit card purchase** → expense transaction, `account_id` = credit card liability, split maps to expense category. Increases debt, decreases net worth.
- **Credit card payment** → transfer transaction, `account_id` = checking (asset), `to_account_id` = credit card (liability). Non-budgetable. Net worth unaffected.
- **Loan payment** → single transaction, two splits: (1) transfer split to loan liability account (principal), (2) expense split to a borrowing-cost category (interest).

## Security Requirements (non-negotiable, build early — see Milestone 0 and 5)

- Database encrypted at rest from the first migration, not retrofitted later
- App lock (biometric/PIN) gates app launch
- CSV/JSON exports are **not** protected by DB encryption — exports must be separately encrypted (`.enc` or password-protected archive) per Milestone 5
- Zero `INTERNET` permission in the manifest at any milestone before 7; even at Milestone 7+, LiteRT-LM inference must run fully on-device with no network calls

## AI Layer Guardrails (Milestone 7+)

- The natural-language query engine is **read-only**. It must execute against a whitelisted schema/view, never against live write-capable connections. No LLM-generated SQL should ever be allowed to `INSERT`/`UPDATE`/`DELETE`.
- SMS/notification scraping and OCR receipt splitting are explicitly **post-MVP** (Milestone 8). Do not build them into earlier milestones even if convenient.

## Milestone Index

- `docs/milestone-0-foundation.md`
- `docs/milestone-1-core-ledger.md`
- `docs/milestone-2-budgeting.md`
- `docs/milestone-3-reminders-forecasting.md`
- `docs/milestone-4-dashboard-insights.md`
- `docs/milestone-5-security-backup.md`
- `docs/milestone-6-onboarding-polish.md`
- `docs/milestone-7-ai-core.md`
- `docs/milestone-8-ai-advanced.md`

Work through milestones in order. Do not start a milestone's AI-dependent features until the milestone(s) it depends on are verified working on-device.
