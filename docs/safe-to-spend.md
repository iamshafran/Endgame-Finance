# Safe-to-Spend — Exact Formula

Ratified by owner 2026-07-14 (Milestone 4). The displayed number MUST match
this document; if the implementation and this document disagree, the
implementation is wrong.

## Formula

```
SafeToSpend = LiquidBalances
            − EnvelopeFunds
            − UpcomingBills
            − RemainingBudgetCommitments
```

### LiquidBalances
Sum of current derived balances of **active asset accounts** only.
- Investments are NOT liquid and never count.
- Liability balances never count. **Credit-card available credit must never
  inflate this number** (spec mandate).
- Anchor decision (owner): money you HAVE, not income you expect. A late
  paycheck can never cause this number to promise unavailable money.

### EnvelopeFunds
Sum of `current_amount` across **all envelopes**. Envelope money is
explicitly promised elsewhere; it is spoken for regardless of which account
backs it.

### UpcomingBills
Sum of **fixed-amount** reminder occurrences that:
- flow OUT of an asset account (expense/transfer reminders whose source
  account is an asset — a bill charged to a credit card does not reduce cash
  today; its repayment reminder does), and
- are due **before the next expected income event** (owner decision:
  the "until payday" window).

"Next expected income event" = the earliest upcoming occurrence of an
income-category reminder. If no income reminder exists within the 30-day
horizon, the window falls back to the full 30 days (conservative).

Overdue bills are due now, therefore always inside the window.

**Variable-amount reminders cannot be projected; they are surfaced as an
explicit caveat under the number, never silently treated as $0.**

### RemainingBudgetCommitments
For each expense category `c` with a budget this month:

```
remaining(c) = max(0, available(c) − spent(c) − upcomingBillsCounted(c))
```

where `available(c)` = this month's allocation + carry-in, `spent(c)` uses
the app-wide spending definition (expenses + categorized transfer splits),
and `upcomingBillsCounted(c)` subtracts the portion of UpcomingBills already
counted against that category — **a bill that is also budgeted must not be
subtracted twice** (e.g. a $1,200 Rent reminder inside a $1,200 Rent budget
counts once, not twice).

Owner decision: budgets are commitments, not guidance — unspent allocations
are spoken for. Under zero-based budgeting this number correctly trends
toward zero when every dollar has a job.

## Explicit exclusions
- Credit-card available credit: never an input, in any direction.
- Investment account balances: not liquid.
- Income expected but not received: not spendable.
- Envelope targets (as opposed to balances): targets are goals, not holds.
