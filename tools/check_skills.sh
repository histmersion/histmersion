#!/usr/bin/env bash
#
# Verify that the skills only name things that actually exist:
#
#   * every `histdb <cmd> [<sub>]` mentioned in skills/*/SKILL.md is a real command
#     (its `--help` prints a Usage block; decline exits 1 on --help, so the OUTPUT is the test)
#   * every MCP tool name mentioned is present in the live server's tools/list
#
# Usage:
#   ./tools/check_skills.sh
#   HISTDB_CLI=/path/to/histdb-cli HISTDB_SERVER_URL=http://localhost:8000 ./tools/check_skills.sh
#
# Exit 0 when everything resolves, 1 otherwise (suitable for CI).

set -uo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SERVER_URL="${HISTDB_SERVER_URL:-http://localhost:8000}"

# Forms the skills quote deliberately as WRONG ("`histdb queue show` errors — use `histdb queue tasks`").
IGNORE_CMDS=( "queue show" )

# ── locate the CLI: an explicit override, a built native image, or `histdb` on PATH ──────────────
CLI="${HISTDB_CLI:-}"
[[ -z "$CLI" ]] && CLI="$(find target/out/jvm -type f -name histdb-cli -perm -u+x 2>/dev/null | head -1)"
[[ -z "$CLI" ]] && CLI="$(find ../../hist-db/target/out/jvm -type f -name histdb-cli -perm -u+x 2>/dev/null | head -1)"
[[ -z "$CLI" ]] && command -v histdb >/dev/null 2>&1 && CLI="$(command -v histdb)"
if [[ -z "$CLI" ]]; then
  echo "FAIL: no histdb CLI found (set HISTDB_CLI, or run: sbt cli/nativeImage)" >&2
  exit 1
fi

echo "CLI    : $CLI"
echo "server : $SERVER_URL"
echo

fail=0
ignored() { local c="$1" i; for i in "${IGNORE_CMDS[@]}"; do [[ "$c" == "$i" ]] && return 0; done; return 1; }
# decline prints the Usage block and exits 1 for --help, so match the OUTPUT, not the status.
# (Capture first: with `pipefail` a pipeline would inherit the CLI's exit 1 even when grep matched.)
resolves() { local out; out="$($CLI $1 --help 2>&1)"; [[ "$out" == Usage:* ]]; }

# ── 1. CLI commands ─────────────────────────────────────────────────────────────────────────────
# `histdb <cmd> [<sub>]`, with a guard so a URL ending in "…/histdb sbt test" is not a match.
CMDS=()
while IFS= read -r line; do CMDS+=("$line"); done < <(
  grep -rhoE '(^|[^-/[:alnum:]])histdb [a-z][a-z-]+( [a-z][a-z-]+)?' skills/*/SKILL.md \
    | sed -E 's/^[^h]*histdb //' | sort -u
)

echo "── CLI commands named in skills/*/SKILL.md ──"
for c in "${CMDS[@]}"; do
  head="${c%% *}"
  if ignored "$c"; then
    printf '  skip  histdb %s   (documented as the wrong form)\n' "$c"
  elif resolves "$c"; then
    printf '  ok    histdb %s\n' "$c"
  elif resolves "$head"; then
    printf '  ok    histdb %s   (`%s` is the command; the rest is prose)\n' "$c" "$head"
  else
    printf '  FAIL  histdb %s   — no such command\n' "$c"
    fail=1
  fi
done

# ── 2. MCP tool names ───────────────────────────────────────────────────────────────────────────
echo
echo "── MCP tools named in skills/*/SKILL.md ──"
LIVE="$(curl -s -X POST "$SERVER_URL/mcp" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | python3 -c 'import sys,json; print("\n".join(t["name"] for t in json.load(sys.stdin)["result"]["tools"]))' 2>/dev/null)"

if [[ -z "$LIVE" ]]; then
  echo "  SKIP  no server at $SERVER_URL — MCP tool names not verified"
  echo
  echo "result: CLI commands checked, MCP names SKIPPED"
  exit $fail
fi
echo "  $(printf '%s\n' "$LIVE" | wc -l | tr -d ' ') tools live"

# Every backticked snake_case identifier is a tool candidate (`get_source`, `get_source {…}`);
# report the ones that are live, and flag any that LOOKS like a tool (verb_noun) but is not.
MENTIONED=()
while IFS= read -r line; do MENTIONED+=("$line"); done < <(
  grep -rhoE '`(mcp__hist-db__)?[a-z][a-z_]+[` {]' skills/*/SKILL.md \
    | tr -d '`{ ' | sed 's/^mcp__hist-db__//' | sort -u
)
TOOL_VERBS='^(get|put|patch|list|export|job|ingest)_'

named=0
for t in "${MENTIONED[@]}"; do
  if printf '%s\n' "$LIVE" | grep -qx "$t"; then
    printf '  ok    %s\n' "$t"; named=$((named+1))
  elif [[ "$t" =~ $TOOL_VERBS ]]; then
    printf '  FAIL  %s — not in tools/list\n' "$t"; fail=1
  fi
done
echo "  ($named of the $(printf '%s\n' "$LIVE" | wc -l | tr -d ' ') live tools are named in the skills)"

echo
[[ $fail -eq 0 ]] && echo "result: PASS" || echo "result: FAIL"
exit $fail
