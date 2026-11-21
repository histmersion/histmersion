# 0003. Optional GPG provenance, verified at every service start

- Status: accepted
- Date: 2026-11-21

## Context

A user who relinks the server with their own plugins may want proof that the binary the service starts is the one they
built and vouched for. Signing must stay optional: without it nothing is verified, as before. A service manager restarts
a failed service on its own (launchd about every 10 s, systemd every 5 s). A LaunchAgent runs with
`PATH=/usr/bin:/bin:/usr/sbin:/sbin` and no `GNUPGHOME`, so neither Homebrew's nor MacGPG2's gpg is on it.

## Decision

- **Provenance is off unless the user sets up a key** (`histmersion-signing use-gpg <key>`). The key's fingerprint is
  stored in `<data>/signing/gpg-key`. It is not secret, and passphrases stay with gpg-agent.
- **`use-gpg` records the gpg program and the GnuPG home it ran with** (`<data>/signing/gpg-program`, `gnupg-home`; the
  home only when it is not the default), so the service's launcher finds the same gpg and keyring outside the user's
  shell.
- **With provenance on, the service definition starts `libexec/histmersion-server-verified`** instead of the binary.
  It checks that `bin/histmersion-server`'s SHA-256 is the one in `bin/histmersion-server.sha256`, and that this file
  carries a good signature by the configured key; then it execs the binary, so the process is the server itself (same
  pid and argv[0]). Otherwise it writes one line to the server log and exits 78 (EX_CONFIG). The service manager retries,
  so a fixed cause (a key imported, the binary re-signed) is picked up without a reinstall. With provenance off it starts
  the binary unverified and says so: off means off.
- **Installing is the moment the user vouches for a bundle**: the installer signs the installed binary's checksum (and
  `.prev`'s). A relink signs the binary it built. When signing fails (no secret key, pinentry cancelled), the service
  starts the binary directly, and the installer says loudly that it is not verified and how to fix it. An install never
  leaves a service that refuses to start. Signing runs after any codesign, which changes the SHA-256.
- **A relink with provenance on checks the previous binary's signed checksum before anything moves**, because the
  service would refuse to roll back to an unsigned one.
- **Fingerprints:** gpg's VALIDSIG names the signing key first and the primary key last (the same twice for a key
  without subkeys). `use-gpg` stores the primary's, so a key that signs with a subkey matches too. A plugin's pinned
  signer is the signing key's fingerprint.
- **Repointing the service** leaves alone a service definition that starts another install's server.

## Consequences

- The default path (no key) is unchanged: the service runs the binary directly.
- A binary whose checksum does not carry the user's signature is never started while provenance is on.
- `scripts/bundle/tests/provenance-test.sh` exercises the launcher with the real gpg in a temporary GNUPGHOME and a
  LaunchAgent-like environment (`env -i`).

## Code

`scripts/bundle/server-verified.sh`, `signing.sh`, `kit-common.sh` (`hk_gpg_key`, `hk_gpg_program`, `hk_gpg_home`,
`hk_gpg_verify`, `hk_verify_binary`, `hk_service_program`, `hk_service_repoint`), `install-build.sh`
(`ib_service_program`), `relink.sh` (`sign_checksum`), `tests/provenance-test.sh`.
