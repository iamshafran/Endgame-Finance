# Milestone 2 — Budgeting

## Goal
Zero-based and cash-flow budgeting modes, envelope savings buckets, rollover rules, and rolling-average suggestions.

## Scope
- Budget mode toggle: Zero-Based (every dollar allocated, soft nag if unallocated income remains — nag only, never a hard block) vs Cash-Flow (high-level tracking, no allocation requirement)
- Per-category monthly budget entry (`budgets` table), with rollover mode per category: `reset` or `carry`
- "Copy last month's budget" one-tap template action at the start of a new month
- Rolling 12-month average suggestion surfaced when setting a budget for historically irregular categories (e.g., "Car Maintenance averaged $84/mo over the last 12 months")
- Envelope/goal buckets (`envelopes` table): create a named envelope with an optional target amount, linked to a backing asset account
- Explicit envelope-to-envelope (or envelope-to-unallocated) transfer UI (`envelope_transfers`) — this must be a visible, deliberate action, not an implicit balance edit
- Budget vs actual view per category, with burn-rate framing (e.g., "on pace to exceed by day 20 at current rate"), not just an end-of-month total

## Out of Scope
- Safe-to-spend dashboard number (Milestone 4 — depends on this milestone's budget data)
- Recurring/reminder-driven budget impacts (Milestone 3)

## Acceptance Criteria
- [ ] Can toggle between Zero-Based and Cash-Flow modes without losing existing budget data
- [ ] Zero-Based mode nags (non-blocking) when income is unallocated
- [ ] Can set a monthly budget per category with rollover mode selection
- [ ] "Copy last month's budget" populates the new month correctly, respecting rollover settings
- [ ] Rolling-average suggestion appears and is numerically correct for a category with 12+ months of history
- [ ] Can create an envelope, link it to an account, and transfer funds into/out of it via an explicit transfer action
- [ ] Budget vs actual view shows burn-rate pacing, not just a static total
