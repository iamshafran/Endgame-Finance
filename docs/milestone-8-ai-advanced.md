# Milestone 8 — AI Advanced (Post-MVP)

## Goal
The higher-complexity, higher-privacy-sensitivity AI features, built last and only once Milestone 7's core AI layer is verified stable.

## Scope
- **Notification/SMS scraper**:
  - `BroadcastReceiver` hooking whitelisted banking/credit card app notifications (user explicitly selects which apps to whitelist — no blanket notification access without opt-in per source)
  - Gemma parses the raw notification string into structured JSON (payee, amount, inferred category, transaction type)
  - Matches payee against the historical profile (from Milestone 7) to locate the likely account
  - Surfaces a 1-tap confirm widget in the notification tray — never auto-posts without confirmation unless the user has explicitly enabled auto-post for that source
  - Requires a clear, explicit permission/consent screen explaining what notification access grants, shown before the feature can be enabled
- **OCR receipt scanning**:
  - ML Kit on-device OCR extracts line items from a camera-captured receipt
  - Gemma splits the line items across the user's existing category tree, producing multiple `transaction_splits` in one transaction
  - User reviews/edits the AI-proposed split before saving — never auto-saves without review
- **Calendar anomaly explanation**: long-press a spending-spike day (from Milestone 3's calendar) and get a plain-text, on-device-generated explanation of what likely drove it
- **Loan interest estimation**: when a loan reminder fires, Gemma reads prior interest-expense splits for that loan and estimates the updated principal/interest ratio for the current payment, presented as an editable suggestion, not an auto-applied value

## Acceptance Criteria
- [ ] Notification access requires an explicit per-app opt-in with a consent screen; no whitelisted app is scraped without it
- [ ] Parsed notification transactions require user confirmation before posting, unless auto-post was explicitly enabled for that source
- [ ] OCR receipt flow lets the user review and adjust the proposed category split before saving; nothing saves automatically
- [ ] Anomaly explanation produces a plausible, on-device-generated explanation for a seeded test spending spike
- [ ] Loan interest estimate is presented as editable, never silently applied to the ledger
