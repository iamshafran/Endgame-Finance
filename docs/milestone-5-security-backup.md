# Milestone 5 — Security & Backup

## Goal
App lock, and encrypted local backup/restore that is explicitly separate from the DB-level encryption already in place since Milestone 0.

## Scope
- App lock: biometric (fingerprint/face) with PIN fallback, gating app launch and resume-from-background after a configurable timeout
- Passphrase/key handling: confirm how the app-lock credential relates to the DB encryption key established in Milestone 0 (e.g., key derivation from PIN, or a separately stored/wrapped key) — document the chosen approach
- CSV export: plain CSV generation from decrypted in-memory data (this works regardless of DB encryption, per the earlier discussion — DB encryption and export format are independent layers)
- Encrypted backup format: export path produces a password-protected archive or `.enc` file, not raw plaintext CSV, as the default "backup" action (plain CSV can still be offered as an explicit "unprotected export" option for the user's own spreadsheet use)
- Restore flow: importing a backup prompts for the backup's password before decrypting and re-inserting into the (separately encrypted) live DB
- Backup reminder: nudge the user if it's been N days since the last local backup (configurable, default suggestion e.g. 30 days)

## Out of Scope
- Bluecoins migration import (Milestone 7 — different source format and AI-assisted mapping)

## Acceptance Criteria
- [ ] App requires biometric/PIN on launch and after backgrounding past the configured timeout
- [ ] Plain CSV export works and contains correct decrypted data
- [ ] Default backup action produces an encrypted/password-protected file, not plaintext
- [ ] Restoring from an encrypted backup requires the correct password and correctly repopulates the (encrypted) live DB
- [ ] Restoring with a wrong password fails cleanly with a clear error, without partially corrupting the live DB
- [ ] "Last backup" nudge appears once the configured interval has elapsed
