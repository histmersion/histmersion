# 0002. macOS binaries are signed with a stable local identity, untrusted by default

- Status: accepted
- Date: 2026-11-21

## Context

Native binaries are signed ad hoc by default. macOS ties a privacy permission (TCC, for example access to
`~/Documents`) to an ad-hoc binary's exact hash, so every rebuild or relink looks like a new app and the permission is
lost. Apple's paid Developer Program and notarisation would solve this for every Mac, but Histmersion needs no paid
third-party account.

## Decision

- `codesign-identity-macos.sh` (run by `histmersion-signing setup-macos`) creates a free, self-signed code-signing
  identity ("Histmersion Local"). It serves this Mac only and does not satisfy Gatekeeper for copies downloaded
  elsewhere. It is idempotent.
- **The identity is left untrusted by default.** codesign signs with it just as well, and its designated requirement
  is just as stable. The TCC check (`scripts/bundle/tcc-check.sh`, run by hand on 2026-10-23) showed that a privacy grant
  to a binary it signed survives a relink, so no trust setting of the login keychain needs changing. `--trust` also
  trusts it for code signing (one macOS password dialog).
- **codesign is given the identity's SHA-1, not its name**, because by name it looks only among valid (trusted)
  identities, and the untrusted one would not be found.
- The certificate is written with the system LibreSSL, whose PKCS#12 output is what `security import` reads.
- `install-macos.sh` signs the installed binaries with the identity when it exists (a relinked server is signed by the
  relink already; a JVM-mode launcher is a script). It also removes the quarantine attribute from the installed bundle,
  because Gatekeeper blocks every binary in a bundle downloaded through a browser.
- The TCC check uses a temporary keychain, added to the search list only while signing; the list is restored exactly
  afterwards and the login keychain is never changed. The probe runs as a LaunchAgent, because a program started from
  Terminal is covered by Terminal's own permission, while one started by launchd is its own "responsible process", as
  the server is.

## Consequences

- A permission granted once survives every update and relink on that Mac.
- Signing is optional: without the identity the binaries stay ad-hoc signed and work, but lose permissions on update.
- `HISTMERSION_CODESIGN_IDENTITY` (`-` for ad hoc) and `HISTMERSION_KEYCHAIN` / `HISTMERSION_CODESIGN_KEYCHAIN` move
  the identity; tests use a temporary keychain and never touch the login keychain.

## Code

`scripts/bundle/codesign-identity-macos.sh`, `signing.sh` (`identity_hash`, `codesign`), `install-macos.sh`,
`tcc-check.sh`, `tests/kit-scripts-test.sh` (the `HISTMERSION_TEST_KEYCHAIN=1` block).
