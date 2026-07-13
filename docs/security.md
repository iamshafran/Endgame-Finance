# Security Model

Documents the relationship between the app lock and DB encryption, as
Milestone 5 requires. Decisions were owner-approved in Milestones 0 and 5.

## Two independent layers

### Layer 1 — Database encryption (since Milestone 0)
- SQLCipher (`net.zetetic:sqlcipher-android`) via Room's SupportFactory.
- Passphrase: random 256 bits generated on first launch, encrypted by a
  hardware-backed Android Keystore AES-GCM key, stored as
  `noBackupFilesDir/db_key.bin` with format `[version:1][iv:12][ciphertext]`.
- The plaintext passphrase exists only in memory while the DB is open.
- Nothing the user knows (PIN, biometric) is an input to this layer.

### Layer 2 — App lock (Milestone 5)
- BiometricPrompt with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`: fingerprint/face
  with the device's own PIN/pattern as fallback (owner decision — no separate
  in-app PIN, therefore no custom secret storage).
- Gates the UI on launch and on resume after a configurable timeout
  (immediately / 1 / 5 / 15 minutes).
- State lives in `security_prefs` + process memory (`AppLock`).

## Why the layers are deliberately NOT linked
(Owner decision, Milestone 0, reaffirmed Milestone 5.)
- Forgetting a credential can never destroy financial history — the threat
  model prioritizes data durability for years of records.
- Background work (reminder auto-posting, net-worth snapshots) must run
  while the UI is locked; a PIN-derived DB key would break it.
- The key file's version byte allows a future migration to PIN-mixing
  without breaking format, if the owner ever changes this stance.

## Backups (Milestone 5)
- DB-level encryption does NOT protect exports — separate layer, per spec.
- Default backup: full JSON dump → gzip → AES-256-GCM, key derived from a
  user-chosen password via PBKDF2-HMAC-SHA256 (210k iterations, random salt).
  File format: `EGF1` magic + version + salt + IV + ciphertext+tag.
- Wrong password fails GCM authentication before any DB write.
- Plain CSV is offered only as an explicitly labeled unprotected export.
