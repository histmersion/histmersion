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
