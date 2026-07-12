# Milestone 0 — Foundation

## Goal
A running, empty Compose app with the encrypted database wired up and the visual theme decided. Nothing user-facing yet beyond a placeholder home screen — this milestone is entirely plumbing.

## Scope
- New Android project, Kotlin + Jetpack Compose, no template boilerplate left in
- Room database configured with SQLCipher (or Room encryption extension); passphrase derivation strategy decided (tied into app lock, see Milestone 5, but the DB-level encryption must exist now — do not retrofit)
- All tables from `CLAUDE.md`'s data model created via Room entities + migrations
- Manifest confirmed to request zero `INTERNET` permission
- Light/dark theme tokens defined (colors, type scale, spacing) — pick a visual identity now, not per-screen later
- Basic navigation scaffold (even if destinations are empty placeholders): Dashboard, Ledger, Budget, Reminders, Accounts, More

## Out of Scope
- Any real data entry
- AI/LiteRT-LM (Milestone 7+)
- App lock UI (Milestone 5, but DB encryption key handling groundwork can start here)

## Acceptance Criteria
- [ ] App builds and installs on physical device via wireless debugging
- [ ] Room DB file on disk is not readable as plaintext SQLite (verify with a file inspection tool)
- [ ] Navigating between placeholder screens works
- [ ] Dark and light theme both render correctly
- [ ] No `INTERNET` permission present in the merged manifest
