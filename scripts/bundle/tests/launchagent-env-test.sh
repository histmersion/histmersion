#!/usr/bin/env bash
# Tests of the installer's LaunchAgent environment merge (launchagent-env.sh, #914) against temporary
# plists — no launchctl, nothing outside a temp dir. macOS only (plutil); elsewhere it skips.
#
#   scripts/bundle/tests/launchagent-env-test.sh        (also: just test-scripts)
set -euo pipefail   # as the installer runs: an errexit slip in the library fails here too
here="$(cd "$(dirname "$0")" && pwd)"
if ! command -v plutil >/dev/null 2>&1; then echo "skipped: plutil (macOS) is not available"; exit 0; fi
# shellcheck source=../launchagent-env.sh
. "$here/../launchagent-env.sh"

# the caller's environment is part of the input: start from none of it
for v in $(compgen -e | grep -E '^(HISTMERSION_|ANTHROPIC_API_KEY$|OPENAI_API_KEY$)' || true); do unset "$v"; done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
export PATH="$tmp/bin:/usr/bin:/bin"   # no real claude/codex/opencode can be found

passed=0; failed=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }  # <what> <shell test>
val()  { plutil -extract "EnvironmentVariables.$2" raw -n -o - "$1" 2>/dev/null; }   # <plist> <name>

# A plist as the installer writes it, before the environment is applied.
fresh() { # <path>
  cat > "$1" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.histmersion.server</string>
  <key>ProgramArguments</key><array><string>/x/bin/histmersion-server</string></array>
  <key>RunAtLoad</key><true/>
</dict></plist>
PLIST
}

# One install: prepare from <installed>, write a fresh plist to <installed>, apply. Output → <log>.
install() { # <installed plist> <log>
  local work; work="$(mktemp -d "$tmp/work.XXXX")"
  la_env_prepare "$1" "$work" "$work/env" > "$2"
  fresh "$1"
  la_env_apply "$work/env" "$1"
}

echo "launchagent-env"

# ── 1. a first install: no plist yet, the caller's settings go in ──────────────────────────────────
p1="$tmp/first.plist"
( export HISTMERSION_AGENTS_ENABLED=true HISTMERSION_CORPUS_DIR="$tmp/corpus" HISTMERSION_PORT=9999
  install "$p1" "$tmp/1.log" )
check "first install: the caller's agent setting is written" '[ "$(val "$p1" HISTMERSION_AGENTS_ENABLED)" = true ]'
check "first install: the corpus dir is written" '[ "$(val "$p1" HISTMERSION_CORPUS_DIR)" = "$tmp/corpus" ]'
check "first install: a variable the installer does not pass stays out" '[ -z "$(val "$p1" HISTMERSION_PORT)" ]'
check "first install: says what it set" 'grep -q "set      HISTMERSION_AGENTS_ENABLED=true" "$tmp/1.log"'
check "first install: the plist is valid" 'plutil -lint -s "$p1"'

# ── 2. a re-install: hand-added settings survive, the caller overrides, others are dropped ─────────
p2="$tmp/again.plist"
cat > "$p2" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.histmersion.server</string>
  <key>EnvironmentVariables</key><dict>
    <key>HISTMERSION_AGENTS_ENABLED</key><string>true</string>
    <key>HISTMERSION_AGENTS_PRICES</key><string>a=1/2; b&amp;c=0/0 &lt;x&gt; "q" 'z' $HOME</string>
    <key>HISTMERSION_AGENTS_ROLE_EXTRACTOR</key><string>claude-code:sonnet</string>
    <key>HISTMERSION_AGENTS_CODEX_COMMAND</key><string>/opt/x/codex</string>
    <key>HISTMERSION_AGENTS_CLAUDE_USE_API_KEY</key><string>false</string>
    <key>HISTMERSION_AGENTS_LOCAL_API_KEY</key><string>local-secret-value</string>
    <key>ANTHROPIC_API_KEY</key><string>sk-ant-very-secret</string>
    <key>PATH</key><string>/usr/bin</string>
  </dict>
</dict></plist>
PLIST
printf '#!/bin/sh\n' > "$tmp/bin/claude"; chmod +x "$tmp/bin/claude"
printf '#!/bin/sh\n' > "$tmp/bin/codex";  chmod +x "$tmp/bin/codex"
( export HISTMERSION_AGENTS_ROLE_EXTRACTOR="codex:gpt-6-astra" HISTMERSION_ACQUIRE_CONTACT_EMAIL="me@example.org"
  install "$p2" "$tmp/2.log" )
check "re-install: a hand-added setting is carried over" '[ "$(val "$p2" HISTMERSION_AGENTS_ENABLED)" = true ]'
check "re-install: a value with & < > quotes and \$ survives byte for byte" \
  '[ "$(val "$p2" HISTMERSION_AGENTS_PRICES)" = "a=1/2; b&c=0/0 <x> \"q\" '"'"'z'"'"' \$HOME" ]'
check "re-install: the caller's environment overrides" '[ "$(val "$p2" HISTMERSION_AGENTS_ROLE_EXTRACTOR)" = "codex:gpt-6-astra" ]'
check "re-install: says what changed" 'grep -q "changed  HISTMERSION_AGENTS_ROLE_EXTRACTOR: claude-code:sonnet → codex:gpt-6-astra" "$tmp/2.log"'
check "re-install: a new caller setting is added" '[ "$(val "$p2" HISTMERSION_ACQUIRE_CONTACT_EMAIL)" = "me@example.org" ]'
check "re-install: the provider API key is carried" '[ "$(val "$p2" ANTHROPIC_API_KEY)" = "sk-ant-very-secret" ]'
check "re-install: a non-Histmersion variable is dropped, and said so" \
  '[ -z "$(val "$p2" PATH)" ] && grep -q "dropped  PATH" "$tmp/2.log"'
check "re-install: no secret value is printed" '! grep -qE "sk-ant-very-secret|local-secret-value" "$tmp/2.log"'
check "re-install: a secret is shown masked" 'grep -q "ANTHROPIC_API_KEY=<hidden, 18 chars>" "$tmp/2.log"'
check "re-install: a boolean named like a key is shown" 'grep -q "HISTMERSION_AGENTS_CLAUDE_USE_API_KEY=false" "$tmp/2.log"'
check "re-install: claude is resolved on the installer's PATH" \
  '[ "$(val "$p2" HISTMERSION_AGENTS_CLAUDE_COMMAND)" = "$tmp/bin/claude" ] && grep -q "resolved HISTMERSION_AGENTS_CLAUDE_COMMAND" "$tmp/2.log"'
check "re-install: a command already set is not re-resolved" '[ "$(val "$p2" HISTMERSION_AGENTS_CODEX_COMMAND)" = "/opt/x/codex" ]'
check "re-install: a command found nowhere stays unset" '[ -z "$(val "$p2" HISTMERSION_AGENTS_OPENCODE_COMMAND)" ]'
check "re-install: the plist is valid" 'plutil -lint -s "$p2"'

# ── 3. idempotent: installing again with nothing in the environment changes nothing ───────────────
cp "$p2" "$tmp/before.plist"
install "$p2" "$tmp/3.log"
check "idempotent: the same plist again" 'cmp -s "$p2" "$tmp/before.plist"'
check "idempotent: everything kept, nothing changed" '! grep -qE "^  (changed|set|resolved)" "$tmp/3.log"'

# ── 4. the masking rule on its own ────────────────────────────────────────────────────────────────
check "secret: an API key" 'la_env_secret OPENAI_API_KEY abc'
check "secret: a token-like value under any name" 'la_env_secret HISTMERSION_X sk-abc'
check "not secret: a switch or a number" '! la_env_secret HISTMERSION_AGENTS_CODEX_USE_API_KEY true && ! la_env_secret HISTMERSION_AUTH_TTL 3600'
check "not secret: an ordinary setting" '! la_env_secret HISTMERSION_AGENTS_PRICES "a=1/2"'

echo "launchagent-env: $passed passed, $failed failed"
[ "$failed" -eq 0 ]
