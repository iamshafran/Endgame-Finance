# Milestone 6 — Onboarding & UX Polish

## Goal
A genuinely good first-run experience and UI polish pass across everything built in Milestones 0-5, before AI features are added on top.

## Scope
- First-run setup wizard: create first account(s), pick budget mode (zero-based/cash-flow), optionally set up first envelope/goal, land on a populated (not empty) dashboard
- Empty states for every screen that can be empty (no transactions yet, no budgets yet, no reminders yet) — each with a clear call-to-action, not a blank screen
- Visual polish pass: consistent spacing/typography per the Milestone 0 theme tokens, verify dark/light parity across all screens built so far
- Reconciliation mode UI polish: a clean "match against your statement" checklist flow using the `is_cleared` field from Milestone 1
- Reduce quick-entry friction further: confirm the FAB autocomplete (Milestone 1) feels fast in real use; adjust based on on-device testing

## Out of Scope
- Any new data model or AI features — this milestone is UX-only, working with what's already built

## Acceptance Criteria
- [ ] First-run wizard takes a new user from zero data to a populated dashboard without dead ends
- [ ] Every screen has a designed empty state, none show a raw blank list
- [ ] Dark/light theme consistency verified across Dashboard, Ledger, Budget, Reminders, Accounts, More
- [ ] Reconciliation flow lets the user check off cleared transactions against a statement in a dedicated view
- [ ] Quick-entry flow tested on-device for speed/friction and adjusted if it's slower than expected
