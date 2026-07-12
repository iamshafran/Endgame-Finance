# Milestone 3 — Reminders, Recurring Detection & Forecasting

## Goal
Reminders as pending/unposted ledger states, automatic recurring-transaction detection, cash-flow forecasting, and the calendar view.

## Scope
- Reminder CRUD (`reminders` table): name, category, account, amount (nullable for variable bills), frequency, next due date, auto-post flag
- Use WorkManager (not raw AlarmManager) for scheduling reminder checks/firing, to survive doze/battery optimization
- Recurring transaction auto-detection: scan ledger history for same-payee + same-approximate-amount + regular interval patterns; surface a suggestion to convert into a reminder (`is_auto_detected = 1`), user must confirm before it's created
- Overdraft/shortfall forecasting: project account balance forward including upcoming reminders, warn if a projected balance goes negative before the next expected income
- Monthly calendar view: color-code days by spending momentum (relative to historical average), with a visually distinct state for overdue bills vs upcoming bills

## Out of Scope
- AI-driven anomaly explanation on long-press (Milestone 8 — needs the AI layer)
- AI-estimated loan principal/interest ratio (Milestone 8)

## Acceptance Criteria
- [ ] Can create a reminder and see it appear as a pending/unposted entry, distinct from posted transactions
- [ ] Reminders fire reliably via WorkManager even after the app is backgrounded/doze mode is active (test on-device, not just emulator)
- [ ] Given 3+ months of matching payee/amount/interval history, the app suggests converting it to a reminder
- [ ] Forecast view warns when a projected balance would go negative before the next expected income, based on posted transactions + pending reminders
- [ ] Calendar view color-codes spending momentum and visually distinguishes overdue vs upcoming bills
