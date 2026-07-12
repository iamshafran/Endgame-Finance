# Milestone 4 — Dashboard & Insights

## Goal
The home dashboard and reporting views that make daily use fast: safe-to-spend, net worth trend, comparisons, merchant insights, search.

## Scope
- **Safe-to-spend number**: `income − upcoming bills − budgeted savings − debt payments`, computed precisely (define the exact formula and inputs before building — see note below) and shown as the primary dashboard readout
- Net worth trend: written to `net_worth_snapshots` on a periodic basis (e.g., daily or on app open, not recomputed live on every screen render), charted over time
- Year-over-year comparison report (e.g., December this year vs December last year, per category)
- Custom date-range report, independent of the future AI query engine — must work standalone
- Merchant-level spending view: total spent + visit count per payee, sortable
- Global search/filter (payee, category, amount range, date range) surfaced from the dashboard, not just buried in the ledger screen

## Important: Get the Safe-to-Spend Formula Exact
Before implementing, write out precisely:
- What counts as "income" (this pay period? this month?)
- What counts as "upcoming bills" (reminders due before next income event, vs all reminders this month)
- Whether envelope-allocated funds are excluded from the number
- Whether credit card available credit factors in at all (it should not inflate safe-to-spend)

Do not ship a version where the number is ambiguous — a wrong "safe to spend" number is worse than not having the feature.

## Out of Scope
- Natural-language query engine (Milestone 7 — this milestone's reports must work without any AI dependency)
- AI anomaly explanations (Milestone 8)

## Acceptance Criteria
- [ ] Safe-to-spend formula is documented explicitly (inputs listed) before/alongside implementation, and the displayed number matches manual calculation on test data
- [ ] Net worth snapshots are written periodically, not recomputed live, and the trend chart reads from snapshots
- [ ] Year-over-year report produces correct category-level comparisons on test data spanning 2+ years
- [ ] Custom date-range report works fully without any AI/LLM component
- [ ] Merchant view correctly aggregates total spend and visit count per payee
- [ ] Search/filter is accessible from the dashboard and returns correct results across payee, category, amount, and date filters
