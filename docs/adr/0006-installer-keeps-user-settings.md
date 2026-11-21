# 0006. The installer keeps the user's settings

- Status: accepted
- Date: 2026-11-21

## Context

An install used to write the macOS LaunchAgent's plist from the caller's environment alone, so every setting added by
hand, or by an earlier install whose caller had exported it, was lost. The server reads `reference.conf` < the user
config file < the environment. A LaunchAgent has no `XDG_CONFIG_HOME` and a PATH without `/opt/homebrew/bin` or
`~/.local/bin`. macOS's `/bin/bash` is 3.2 and has no associative arrays.

## Decision

- **The LaunchAgent's `EnvironmentVariables` are carried across installs** (`launchagent-env.sh`):
  1. every entry of the installed plist that the server reads (`HISTMERSION_*`, `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`)
     is carried over; anything else is reported and dropped;
  2. the caller's `HISTMERSION_CORPUS_DIR`, `HISTMERSION_ACQUIRE_CONTACT_EMAIL` and `HISTMERSION_AGENTS_*` (non-empty)
     override it;
  3. `HISTMERSION_AGENTS_{CLAUDE,CODEX,OPENCODE}_COMMAND`, when neither side gives one, is the absolute path of the
     command on the installer's PATH;
  4. what was kept, set, changed and dropped is printed, and a secret-looking value never is. A boolean or a number is
     never treated as a secret (`…_USE_API_KEY=false` is a switch, not a key).
  The installed plist is read before it is rewritten. A set of variables is a directory with one file per name (Bash
  3.2), holding the value, which is newline-safe. Variable names hold no dot, so a plutil key path built from one is
  safe. plutil does the escaping, so a value with `&`, `<`, `>` or quotes stays exactly what it was. Some macOS versions
  print plutil's "could not extract" message on standard output, so the exit status, not the output, says whether a key
  exists. To drop a carried setting, remove it from the installed plist first.
- **The user config file is never overwritten** (`user-config.sh`). The installer writes the commented template only
  when nothing is there, using noclobber so a file appearing between the test and the write is still not overwritten.
  It is written where the installed server will look: `HISTMERSION_CONFIG` wins, else `$XDG_CONFIG_HOME/histmersion` as
  the service sees it (`~/.config/histmersion` for a LaunchAgent). The data directory never moves it: config is the
  person's, data is the server's.

## Consequences

- Reinstalling never loses a setting; changes are visible in the installer's output with secrets masked.
- `scripts/bundle/tests/launchagent-env-test.sh` and `user-config-test.sh` cover the merge and the template rule.

## Code

`scripts/bundle/launchagent-env.sh`, `user-config.sh`, `install-macos.sh`, `install-linux.sh`,
`tests/launchagent-env-test.sh`, `tests/user-config-test.sh`.
