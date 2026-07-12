# Milestone 7 — AI Core (LiteRT-LM + Gemma 4 E2B)

## Goal
Bring on-device AI online for the two highest-value, lowest-risk use cases: Bluecoins migration and read-only natural-language reporting. Everything in Milestones 0-6 must already work without this layer.

## Scope
- LiteRT-LM Android runtime integration, Gemma 4 E2B (instruction-tuned, quantized), NPU-preferred backend, local `.litertlm` model file provided by the user (download/placement flow, not bundled in the app binary)
- Bluecoins CSV/SQLite migration:
  - User provides historical export file
  - Gemma maps source columns/fields to the internal schema contextually (not a fragile hardcoded column-mapping table)
  - After import, run local text-embedding over historical payee/category strings to build a baseline classification profile for future auto-categorization
- Natural-language query engine:
  - User submits a plain-text question (e.g., "how much did I spend on groceries last quarter")
  - Gemma translates this into SQL
  - **Critical constraint**: execute only against a read-only connection or a whitelisted set of views — no write capability, ever, regardless of what the generated SQL contains. Validate/sanitize generated SQL before execution; reject anything touching tables outside the whitelist
  - Results returned as a conversational summary plus a native chart, not raw table dump

## Out of Scope
- SMS/notification scraping, OCR receipt splitting, calendar anomaly explanation, loan-interest AI estimation — all Milestone 8

## Acceptance Criteria
- [ ] Model loads and runs inference fully on-device with zero network calls (verify via network monitoring during a test run)
- [ ] Bluecoins CSV import correctly maps at least the core fields (date, payee, amount, category, account) without a hardcoded column-name mapping
- [ ] Post-import, new transactions with similar payee strings get reasonable auto-category suggestions based on the embedded historical profile
- [ ] Natural-language query returns correct results for at least 10 varied test questions against known seeded data
- [ ] Attempting to craft a query that could produce a destructive SQL statement is blocked before execution, with a test case proving this (e.g., a query is deliberately phrased to try to trigger a DELETE/UPDATE and confirmed to fail safely)
