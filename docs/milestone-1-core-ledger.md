# Milestone 1 — Core Ledger

## Goal
A fully usable double-entry ledger: create accounts, categories, and transactions (including splits and transfers), with an immutable audit trail.

## Scope
- Account CRUD: asset, liability, investment types; credit limit field for credit-card liabilities
- Category CRUD with parent/child nesting (e.g. Food → Groceries)
- Tag CRUD, and tagging transactions (many-to-many)
- Transaction entry flow:
  - Expense, income, transfer types
  - Multi-category split support (single transaction → multiple `transaction_splits`)
  - Cleared/uncleared toggle (reconciliation groundwork)
  - Shared/reimbursable flag
- Credit card purchase/payment handled per the liability rules in `CLAUDE.md` — verify both directions produce correct balance effects
- Loan payment dual-split entry (principal transfer + interest expense) as a guided flow, not just raw split entry
- Ledger list view: chronological transaction list per account, with search/filter by payee, category, amount range (this can be a simple filter UI now; richer reporting comes in Milestone 4)
- Audit log: any edit to an existing transaction writes an `transaction_audit` row (old value → new value → timestamp) rather than silently overwriting
- Quick-entry FAB with recent payee/amount autocomplete

## Out of Scope
- Budgets, envelopes (Milestone 2)
- Reminders, recurring detection (Milestone 3)
- Dashboard/reports beyond basic ledger list (Milestone 4)

## Acceptance Criteria
- [ ] Can create an asset account, a liability (credit card) account, and an investment account
- [ ] Can create nested categories (parent + subcategory)
- [ ] Can create a tag and apply it to a transaction
- [ ] Can enter an expense transaction split across 2+ categories in one transaction
- [ ] Can enter a credit card purchase and a credit card payment; verify liability balance and net worth calculations both behave per the rules in `CLAUDE.md`
- [ ] Can enter a loan payment with principal + interest split via the guided flow
- [ ] Editing a transaction's amount or category produces an audit row, not a silent overwrite
- [ ] Ledger list can be filtered by payee, category, and amount range
- [ ] FAB quick-entry suggests recent payees/amounts
