#!/usr/bin/env bash
# What one `claude -p` agent step sends on EVERY turn before the model reads a word — the system
# prompt, the tool definitions and what the CLI puts in the first message — with the claude-code
# provider's switches (agents.providers.claude-code.env, --disable-slash-commands) and without them.
#
#   scripts/agents/claude-overhead.sh [--role article-skeptic] [--tree <agents>/claude-code] [--each]
#                                     [--env K=V]… [--flag --some-flag]… [--keep]
#   scripts/agents/claude-overhead.sh --from before=<dir> --from after=<dir> [--role …]
#   scripts/agents/claude-overhead.sh --live [--role …] [--model haiku]      (spends your plan: asks first)
#
# --from [label=]<dir> (repeatable) launches the role, with the tuned switches, from a tree PromptBudget
# exported (`sbt "server/Test/runMain com.histmersion.server.agents.PromptBudget export <dir>"`): its
# Claude Code plugin, its MCP tools served by a stand-in MCP server on loopback (`tools/list` only,
# nothing is called) and its real answer schema — the WHOLE first call, one arm per tree.
#
# Offline (the default) no model is called and no login is read: the CLI runs on an EMPTY config dir
# with a dummy API key against a stand-in for the Messages API on loopback (claude_overhead.py serve),
# which records the request bodies. Each arm launches the role from the GENERATED headless plugin the
# way the server does (--agent, --settings, --json-schema, --tools "", --strict-mcp-config, no MCP
# server unless --from gives one: the role's MCP tools are the same in every arm). It prints characters;
# ~tokens is chars/4.
#
#   arms: "none" (no env switches, no --disable-slash-commands), "tuned" (reference.conf's defaults, or
#   --env/--flag), and with --each every switch alone.
#
# What it cannot see offline: features a claude.ai login turns on (the Artifact tool is withheld
# behind a custom API URL) and server-side feature flags. --live runs the same arms on YOUR login, one
# tiny turn each, and prints each run's first-call input (input + cache tokens) from its stream.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
helper="$here/claude_overhead.py"
reference="$repo/modules/config/src/main/resources/reference.conf"

role="article-skeptic"; model="claude-sonnet-4-5"; tree=""; each=0; keep=0; live=0
envs=(); flags=(); custom=0; froms=()
while [ $# -gt 0 ]; do
  case "$1" in
    --role)  role="$2"; shift 2 ;;
    --model) model="$2"; shift 2 ;;
    --tree)  tree="$2"; shift 2 ;;
    --each)  each=1; shift ;;
    --keep)  keep=1; shift ;;
    --live)  live=1; shift ;;
    --env)   envs+=("$2"); custom=1; shift 2 ;;
    --flag)  flags+=("$2"); custom=1; shift 2 ;;
    --from)  froms+=("$2"); shift 2 ;;
    -h|--help) sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1 (see --help)" >&2; exit 1 ;;
  esac
done

if [ ${#froms[@]} -gt 0 ]; then
  [ $live -eq 0 ] || { echo "--from is offline only" >&2; exit 1; }
  first="${froms[0]}"; tree="${first#*=}/claude-code"
elif [ -z "$tree" ]; then
  if [ -n "${HISTMERSION_AGENTS_RUNTIMES_DIR:-}" ]; then tree="$HISTMERSION_AGENTS_RUNTIMES_DIR/claude-code"
  elif [ "$(uname)" = "Darwin" ]; then tree="$HOME/Library/Application Support/Histmersion/agents/claude-code"
  else tree="${XDG_DATA_HOME:-$HOME/.local/share}/histmersion/agents/claude-code"; fi
fi
plugin="$tree/headless"; settings="$tree/roles/$role/settings.json"
[ -f "$plugin/agents/$role.md" ] || { echo "no generated agent $plugin/agents/$role.md — run 'histmersion agent generate' or pass --tree" >&2; exit 1; }
[ -f "$settings" ] || { echo "no role settings $settings" >&2; exit 1; }
command -v claude >/dev/null || { echo "claude is not on PATH" >&2; exit 1; }

# the tuned arm: reference.conf's env defaults and --disable-slash-commands, unless --env/--flag given
if [ $custom -eq 0 ]; then
  while IFS= read -r kv; do envs+=("$kv"); done < <(python3 -I "$helper" defaults "$reference")
  flags=(--disable-slash-commands)
fi

tmp="$(mktemp -d "${TMPDIR:-/tmp}/histmersion-overhead.XXXXXX")"
server_pid=""
cleanup() {
  [ -n "$server_pid" ] && kill "$server_pid" 2>/dev/null || true
  if [ $keep -eq 1 ]; then echo "kept: $tmp" >&2; else rm -rf "$tmp"; fi
}
trap 'cleanup_mcp; cleanup' EXIT

schema='{"type":"object","properties":{"verdict":{"type":"string"}},"required":["verdict"]}'
mkdir -p "$tmp/work"
mcp_pids=()
cleanup_mcp() { for p in ${mcp_pids[@]+"${mcp_pids[@]}"}; do kill "$p" 2>/dev/null || true; done; }

# one launch: <label> [--flag…] -- [K=V…]; prints nothing, leaves $tmp/<label>/{stream.jsonl,request.json}
launch() {
  local label="$1"; shift
  local fl=() ev=()
  while [ $# -gt 0 ] && [ "$1" != "--" ]; do fl+=("$1"); shift; done
  [ "${1:-}" = "--" ] && shift
  ev=("$@")
  local out="$tmp/$label"; mkdir -p "$out"
  local base=(env -i HOME="$HOME" PATH="$PATH" TERM=dumb DISABLE_AUTOUPDATER=1 ENABLE_CLAUDEAI_MCP_SERVERS=false
    CLAUDE_CODE_MAX_OUTPUT_TOKENS=1024)
  if [ $live -eq 0 ]; then
    local cfg="$tmp/config-$label"; mkdir -p "$cfg"
    base+=(CLAUDE_CONFIG_DIR="$cfg" ANTHROPIC_API_KEY=sk-ant-offline-dummy ANTHROPIC_BASE_URL="http://127.0.0.1:$port")
  fi
  local before; before=$(ls "$tmp/api" 2>/dev/null | grep -c '^request-' || true)
  local mcp=(); [ -n "${arm_mcp:-}" ] && mcp=(--mcp-config "$arm_mcp")
  ( cd "$tmp/work" && "${base[@]}" ${ev[@]+"${ev[@]}"} claude -p --output-format stream-json --verbose --model "$model" \
      --max-turns 1 --no-session-persistence --setting-sources "" ${fl[@]+"${fl[@]}"} \
      --plugin-dir "$plugin" --agent "histmersion-agents:$role" --settings "$settings" --json-schema "$schema" \
      --tools "" --strict-mcp-config ${mcp[@]+"${mcp[@]}"} --permission-mode dontAsk --allowedTools StructuredOutput \
      <<< "Reply with the verdict ok." > "$out/stream.jsonl" 2> "$out/stderr" ) || true
  if [ $live -eq 0 ]; then
    local first; first=$(ls "$tmp/api" | grep '^request-' | sort | sed -n "$((before + 1))p")
    [ -n "$first" ] || { echo "$label: the CLI sent no request (see $out/stderr; --keep keeps it)" >&2; head -c 600 "$out/stream.jsonl" >&2; return 1; }
    cp "$tmp/api/$first" "$out/request.json"
    python3 -I "$helper" measure "$out/request.json" "$out/stream.jsonl" > "$out/measure.json"
  fi
}

arms=()
# one arm per --from tree: its plugin, its role settings, its answer schema, its MCP tools on a stand-in
run_from() {
  local i=0
  for f in "${froms[@]}"; do
    i=$((i + 1))
    local label="${f%%=*}" dir="${f#*=}"; [ "$label" = "$f" ] && label="tree$i"
    plugin="$dir/claude-code/headless"; settings="$dir/claude-code/roles/$role/settings.json"
    [ -f "$plugin/agents/$role.md" ] || { echo "$label: no $plugin/agents/$role.md" >&2; exit 1; }
    schema="$(cat "$dir/answer-schemas/$role.json" 2>/dev/null || echo "$schema")"
    arm_mcp=""
    local tools="$dir/mcp-tools/$role.json"
    if [ -f "$tools" ] && python3 -I -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1]))["tools"] else 1)' "$tools"; then
      local pf="$tmp/mcp-port-$i"
      python3 -I "$helper" serve "$pf" "$tmp/mcp-$i" "$tools" &
      mcp_pids+=($!)
      for _ in $(seq 1 50); do [ -s "$pf" ] && break; sleep 0.1; done
      arm_mcp="$tmp/mcp-$i.json"
      printf '{"mcpServers":{"histmersion":{"type":"http","url":"http://127.0.0.1:%s/mcp"}}}' "$(cat "$pf")" > "$arm_mcp"
    fi
    launch "$label" ${flags[@]+"${flags[@]}"} -- ${envs[@]+"${envs[@]}"}
    arms+=("$label")
  done
}

run_arms() {
  if [ ${#froms[@]} -gt 0 ]; then run_from; return; fi
  launch none
  arms+=("none")
  launch tuned ${flags[@]+"${flags[@]}"} -- ${envs[@]+"${envs[@]}"}
  arms+=("tuned")
  if [ $each -eq 1 ]; then
    for f in ${flags[@]+"${flags[@]}"}; do launch "flag$f" "$f"; arms+=("flag$f"); done
    for kv in ${envs[@]+"${envs[@]}"}; do launch "${kv%%=*}" -- "$kv"; arms+=("${kv%%=*}"); done
  fi
}

if [ $live -eq 1 ]; then
  n=2; [ $each -eq 1 ] && n=$((2 + ${#flags[@]} + ${#envs[@]}))
  echo "--live makes $n real one-turn calls on YOUR Claude login with model '$model' (your plan's usage)."
  read -r -p "Go ahead? [y/N] " ok
  [ "$ok" = "y" ] || [ "$ok" = "Y" ] || { echo "nothing run"; exit 0; }
  run_arms
  printf '%-44s %s\n' arm "first call (input + cache tokens)"
  for a in "${arms[@]}"; do printf '%-44s %s\n' "$a" "$(python3 -I "$helper" first-call "$tmp/$a/stream.jsonl")"; done
  exit 0
fi

python3 -I "$helper" serve "$tmp/port" "$tmp/api" &
server_pid=$!
for _ in $(seq 1 50); do [ -s "$tmp/port" ] && break; sleep 0.1; done
port="$(cat "$tmp/port")"

echo "claude $(claude --version 2>/dev/null | head -1) · role $role · $plugin"
echo "tuned: ${flags[*]:-} ${envs[*]:-}"
echo
run_arms
rows=(); for a in "${arms[@]}"; do rows+=("$a=$tmp/$a/measure.json"); done
python3 -I "$helper" table "${rows[@]}"
echo
if [ ${#froms[@]} -gt 0 ]; then
  echo "characters of the request JSON; ~tokens = chars/4 (an estimate). (mcp): the role's MCP tools, inside tools."
else
  echo "characters of the request JSON; ~tokens = chars/4 (an estimate). The role's MCP tools are not included (see --from)."
fi
