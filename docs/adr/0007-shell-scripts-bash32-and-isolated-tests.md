# 0007. Shell scripts run on Bash 3.2, and their tests never touch the user's machine state

- Status: accepted
- Date: 2026-11-21

## Context

The installers and the relink kit's scripts run on the user's machine, where macOS's `/bin/bash` is 3.2. Their tests
run on developer machines that also have a real Histmersion install, a login keychain, a GnuPG keyring and a running
service.

## Decision

- The kit's scripts and installers are written for Bash 3.2: no associative arrays and no newer builtins.
- **Every location can be moved by the environment**: `HISTMERSION_HOME` (the install), `HISTMERSION_DATA_DIR`,
  `HISTMERSION_SERVICE` (`launchd | systemd | none`), the service's label or unit, `HISTMERSION_SERVER_URL`, the
  keychain, `GNUPGHOME`, `HOME`. That is how the tests run against temporary installs, and never against
  `~/Applications/Histmersion` or the user's data directory.
- **The tests never touch the user's state.** They use a temporary `HOME` (the keychain search list is found from
  `HOME`), fakes for `launchctl`, `systemctl`, `curl`, `codesign` and `gpg`, and a fake `security` that fails when it is
  called, because the real one reads the keychain. The opt-in keychain test saves the search list, adds a temporary
  keychain only for the signing, restores the list, compares it, and deletes the keychain.
- **The smoke test runs a bundle in isolation** on offset ports (18000, 15433, 18100), so it can run next to an
  installed Histmersion. It uses its own config file, data directory, `HOME` and keyring, and never restarts a service
  or signs with a keychain identity.

## Consequences

- The scripts work on a stock Mac without Homebrew bash.
- Running `just test-scripts` on a developer machine is safe.

## Code

`scripts/bundle/kit-common.sh`, `scripts/bundle/smoke.sh`, `scripts/bundle/tests/*.sh`.
