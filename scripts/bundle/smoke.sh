#!/usr/bin/env bash
# Start a bundle in isolation and prove it works end to end — what CI runs on every platform:
# first-run initdb, Postgres + converter as children, /api/health all ok, the CLI, a real OCR
# conversion OFFLINE with the bundled models, a clean stop that leaves no process behind.
#
#   scripts/bundle/smoke.sh <bundle-dir>
# Ports are offset (18000/15433/18100) so it can run next to an installed Histmersion.
set -euo pipefail
b="$(cd "${1:?usage: $0 <bundle-dir>}" && pwd)"
data="$(mktemp -d)"
export HISTMERSION_DATA_DIR="$data" HISTMERSION_PORT=18000 HISTMERSION_PUBLIC_URL=http://127.0.0.1:18000 \
       HISTMERSION_PG_PORT=15433 HISTMERSION_CONVERTER_PORT=18100 HISTMERSION_SERVER_URL=http://127.0.0.1:18000 \
       HF_HUB_OFFLINE=1
# The build machine still has the pixi environment the PostgreSQL files were installed into, and
# conda-forge's PostgreSQL looks there by absolute path — so hide it: the bundle must work on its own.
envs="$(cd "$(dirname "$0")/../.." && pwd)/runtime/postgres/.pixi/envs"
hidden=""
if [ -d "$envs" ]; then mv "$envs" "$envs.smoke-hidden"; hidden=1; fi
server=""
cleanup() {
  [ -n "$server" ] && kill "$server" 2>/dev/null || true
  "$b/pg/bin/pg_ctl" -D "$data/pg" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$data"
  [ -n "$hidden" ] && mv "$envs.smoke-hidden" "$envs" || true
}
trap cleanup EXIT
fail() { echo "::error::$*"; for f in server postgres converter; do echo "── $f.log"; tail -40 "$data/logs/$f.log" 2>/dev/null || true; done; exit 1; }

# a user config file of its own (never the machine's ~/.config one): its port must LOSE to
# HISTMERSION_PORT (else nothing answers on 18000), and its typo must be reported, not fatal
export HISTMERSION_CONFIG="$data/histmersion.conf"
printf 'histmersion {\n  server.port = 18999\n  smoke-typo = 1\n}\n' > "$data/histmersion.conf"

echo "→ the server's command line: nothing is opened before it is understood"
rc=0; "$b/bin/histmersion-server" --no-such-flag > "$data/flag.out" 2>&1 || rc=$?
[ "$rc" = 1 ] || fail "an unknown flag exited $rc, not 1: $(cat "$data/flag.out")"
grep -q "unknown argument '--no-such-flag'" "$data/flag.out" || fail "no usage error for an unknown flag: $(cat "$data/flag.out")"
[ ! -e "$data/pg" ] || fail "an unknown flag started the bundled PostgreSQL"

echo "→ --self-check: the built-in plugins, two vaults enabled against FAKE CLIs (never a real vault)"
# bitwarden's session is a secret held in keepassxc: it resolves through the keepassxc vault, whose
# master password comes from the environment — the subprocess, the env: and the vault: paths, natively
fake="$data/fake-bin"; mkdir -p "$fake"
cat > "$fake/keepassxc-cli" <<'SH'
#!/bin/sh
IFS= read -r pw
[ "$pw" = "smoke-master" ] || { echo "Error while reading the database: Invalid credentials were provided" >&2; exit 1; }
for a; do e=$a; done
printf 'session-from-%s\n' "$e"
SH
printf '#!/bin/sh\nexit 0\n' > "$fake/bw"
chmod +x "$fake/keepassxc-cli" "$fake/bw"; : > "$data/vault.kdbx"
selfcheck() { # <password> → the report in $data/self-check.out, the exit code echoed
  printf 'histmersion.plugins {\n  keepassxc { enabled = true, database = "%s", password = "env:SMOKE_KP_PASSWORD" }\n  bitwarden { enabled = true, session = "keepassxc:Smoke/bw#password" }\n}\n' \
    "$data/vault.kdbx" > "$data/plugins.conf"
  local rc=0
  HISTMERSION_CONFIG="$data/plugins.conf" SMOKE_KP_PASSWORD="$1" PATH="$fake:$PATH" \
    "$b/bin/histmersion-server" --self-check > "$data/self-check.out" 2>&1 || rc=$?
  echo "$rc"
}
rc="$(selfcheck smoke-master)"
sed 's/^/  /' "$data/self-check.out"
[ "$rc" = 0 ] || fail "--self-check exited $rc: $(cat "$data/self-check.out")"
grep -qF "keepassxc 1.0.0 — registered (secret-vault:keepassxc)" "$data/self-check.out" || fail "--self-check: keepassxc not registered"
grep -qF "bitwarden 1.0.0 — registered (secret-vault:bitwarden)" "$data/self-check.out" || fail "--self-check: bitwarden not registered"
grep -qF "1password 1.0.0 — disabled" "$data/self-check.out" || fail "--self-check: 1password is not reported disabled"
grep -qF "session = keepassxc:Smoke/bw#password" "$data/self-check.out" || fail "--self-check: the secret reference is not shown"
if grep -qE "smoke-master|session-from" "$data/self-check.out"; then fail "--self-check printed a secret value"; fi
grep -q "self-check passed" "$data/self-check.out" || fail "--self-check gave no verdict"
# the built-in acquisition plugins: in the binary, enabled by default, their fetchers registered
for line in "archive-org 1.0.0 — registered (source-fetcher:archive-org)" \
    "arxiv 1.0.0 — registered (source-fetcher:arxiv)" \
    "oa-index 1.0.0 — registered (source-fetcher:unpaywall, source-fetcher:core, source-fetcher:openalex, source-fetcher:landing)" \
    "pmc 1.0.0 — registered (source-fetcher:pmc, source-fetcher:pmc-text)"; do
  grep -qF "$line" "$data/self-check.out" || fail "--self-check: not reported: $line"
done
printf 'histmersion.plugins.arxiv.enabled = false\n' > "$data/fetchers.conf"
HISTMERSION_CONFIG="$data/fetchers.conf" "$b/bin/histmersion-server" --self-check > "$data/fetchers.out" 2>&1 \
  || fail "--self-check with arxiv switched off failed: $(cat "$data/fetchers.out")"
grep -qF "arxiv 1.0.0 — disabled" "$data/fetchers.out" || fail "--self-check: arxiv is not reported disabled"
rc="$(selfcheck wrong-password)"
[ "$rc" = 2 ] || fail "--self-check with a locked vault exited $rc, not 2: $(cat "$data/self-check.out")"
grep -q "the database is locked" "$data/self-check.out" || fail "--self-check does not say the vault is locked"
[ ! -e "$data/pg" ] && ! curl -fsS http://127.0.0.1:18000/api/health >/dev/null 2>&1 \
  || fail "--self-check started the bundled runtime or a listener"

echo "→ start (first run: initdb)"
"$b/bin/histmersion-server" > "$data/server.out" 2>&1 &
server=$!
mkdir -p "$data/logs"; ln -sf "$data/server.out" "$data/logs/server.log"
ok=""
for _ in $(seq 1 180); do
  if h="$(curl -fsS http://127.0.0.1:18000/api/health 2>/dev/null)" && echo "$h" | grep -q '"converter":"ok"'; then ok=1; break; fi
  kill -0 "$server" 2>/dev/null || fail "the server exited during startup"
  sleep 2
done
[ -n "$ok" ] || fail "not healthy after 6 minutes: ${h:-no answer}"
echo "  $h"
echo "$h" | grep -q '"db":"ok"' || fail "database not ok"
grep -q "relocated PostgreSQL" "$data/server.out" || fail "the bundled PostgreSQL was not relocated"

echo "→ CLI"
cli_err="$data/cli.err"
"$b/bin/histmersion" health 2>"$cli_err" | grep -q "db ok" || fail "histmersion health"
"$b/bin/histmersion" about | grep -qF "config file: $data/histmersion.conf (read; ignored unknown key: histmersion.smoke-typo)" \
  || fail "histmersion about does not report the config file it read"
grep -q "unknown setting 'histmersion.smoke-typo'" "$data/server.out" || fail "no warning for an unknown config key"
# pre-3.8 lazy vals left unpatched (Sloth did not run) print JDK 25's warning on every CLI call
if grep -q "sun.misc.Unsafe" "$cli_err" "$data/server.out"; then
  fail "a binary prints the sun.misc.Unsafe warning: the native image was built without Sloth (SLOTH_JAR)"
fi

echo "→ first-run data: the bundled gazetteer and period presets (#720)"
[ -f "$b/db/periods/periodo-seed.json" ] && [ -f "$b/db/places/01-natural-earth.json" ] \
  || fail "the bundle ships no db/ seed data (scripts/bundle/server.sh)"
st=""
for _ in $(seq 1 600); do
  st="$("$b/bin/histmersion" jobs 2>/dev/null | grep '| init |' || true)"
  case "$st" in *"| done |"*|*"| failed |"*) break;; esac
  sleep 1
done
echo "  $st"
echo "$st" | grep -q "| done |" || fail "the first-run init job did not finish: ${st:-no init job}"
"$b/bin/histmersion" periods search "Bronze Age" | grep -q "p0s2rwkwbsz" || fail "no period presets after the first start"
"$b/bin/histmersion" places search Anatolia | grep -q "anatolia" || fail "no gazetteer after the first start"
"$b/bin/histmersion" places sources | grep -q "geonames" || fail "the bundle pins no gazetteer extracts (db/gazetteer/extracts.json)"

echo "→ config reload, no restart: a hot key, a database swap and back, a restart-only key"
conf() { printf 'histmersion {\n  server.port = 18999\n  smoke-typo = 1\n%b}\n' "$1" > "$data/histmersion.conf"; }
reload() { "$b/bin/histmersion" config reload 2>&1; }
conf '  limits.search = 7\n  agents.max-concurrent-runs = 2\n'
out="$(reload)" || fail "config reload (hot key): $out"
echo "$out" | grep -q "Config reloaded" || fail "no new generation: $out"
echo "$out" | grep -q -- "- histmersion.limits.search" || fail "the hot key is not reported applied: $out"
echo "$out" | grep -A3 "after a restart" | grep -q -- "- histmersion.agents.max-concurrent-runs" \
  || fail "the restart-only key is not reported as such: $out"
# the database: a name of its own in the bundled cluster — created, migrated, swapped to, while the
# server keeps running; the gazetteer is not there, and is there again after swapping back
conf '  limits.search = 7\n  agents.max-concurrent-runs = 2\n  db.url = "jdbc:postgresql://localhost:5432/histmersion_smoke_b"\n'
out="$(reload)" || fail "config reload (database): $out"
for n in pool migrations query-pool leases; do echo "$out" | grep -q -- "- $n" || fail "$n not rebuilt: $out"; done
"$b/bin/histmersion" health | grep -q "db ok" || fail "health after the database swap"
"$b/bin/histmersion" places search Anatolia | grep -q "anatolia" && fail "still on the first database after the swap"
"$b/bin/histmersion" query "select current_database() as db, count(*) > 50 as migrated from flyway_schema_history" \
  | grep "histmersion_smoke_b" | grep -q "true" || fail "the new database was not migrated (or /api/query is not on it)"
conf '  limits.search = 7\n  agents.max-concurrent-runs = 2\n'
out="$(reload)" || fail "config reload (back): $out"
"$b/bin/histmersion" places search Anatolia | grep -q "anatolia" || fail "not back on the first database"
# a broken file changes nothing, and says so (exit 2)
printf 'histmersion { db.url = \n' > "$data/histmersion.conf"
rc=0; out="$(reload)" || rc=$?
[ "$rc" = 2 ] || fail "a broken config reload exited $rc, not 2: $out"
"$b/bin/histmersion" places search Anatolia | grep -q "anatolia" || fail "a broken reload moved the server"
conf '  limits.search = 7\n  agents.max-concurrent-runs = 2\n'
echo "  reloaded in place: a hot key, the database there and back, a restart-only key reported; a broken file refused"

echo "→ OCR, offline, bundled models"
pdf="$data/sample.pdf"
"$b/converter/python/bin/python3" - "$pdf" <<'PY'
import sys, pymupdf
doc = pymupdf.open(); page = doc.new_page()
page.insert_text((72, 96), "Hattusa, capital of the Hittite Empire", fontsize=18)
page.insert_text((72, 140), "Late Bronze Age, c. 1650-1180 BCE", fontsize=12)
doc.save(sys.argv[1])
PY
out="$(curl -fsS -F file=@"$pdf" -F force_quality=true http://127.0.0.1:18100/convert)" || fail "convert request"
echo "$out" | grep -q "Hattusa" || fail "OCR output lacks the text: ${out:0:300}"
echo "  docling converted the sample page"

echo "→ stop"
kill -TERM "$server"
for _ in $(seq 1 60); do kill -0 "$server" 2>/dev/null || break; sleep 1; done
kill -0 "$server" 2>/dev/null && fail "the server did not stop"
server=""
sleep 2
if pgrep -f "$data/pg" >/dev/null || pgrep -f "uvicorn app:app --host 127.0.0.1 --port 18100" >/dev/null; then
  fail "child processes survived the server"
fi
echo "smoke test passed"
