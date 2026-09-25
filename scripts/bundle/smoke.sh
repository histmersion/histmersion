#!/usr/bin/env bash
# Start a bundle in isolation and prove it works end to end — what CI runs on every platform:
# first-run initdb, Postgres + converter as children, /api/health all ok, the CLI, a real OCR
# conversion OFFLINE with the bundled models, a clean stop that leaves no process behind.
#
#   scripts/bundle/smoke.sh <bundle-dir>
# Ports are offset (18000/15433/18100) so it can run next to an installed hist-db.
set -euo pipefail
b="$(cd "${1:?usage: $0 <bundle-dir>}" && pwd)"
data="$(mktemp -d)"
export HISTDB_DATA_DIR="$data" HISTDB_PORT=18000 HISTDB_PUBLIC_URL=http://127.0.0.1:18000 \
       HISTDB_PG_PORT=15433 HISTDB_CONVERTER_PORT=18100 HISTDB_SERVER_URL=http://127.0.0.1:18000 \
       HF_HUB_OFFLINE=1
server=""
cleanup() {
  [ -n "$server" ] && kill "$server" 2>/dev/null || true
  "$b/pg/bin/pg_ctl" -D "$data/pg" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$data"
}
trap cleanup EXIT
fail() { echo "::error::$*"; for f in server postgres converter; do echo "── $f.log"; tail -40 "$data/logs/$f.log" 2>/dev/null || true; done; exit 1; }

echo "→ start (first run: initdb)"
"$b/bin/histdb-server" > "$data/server.out" 2>&1 &
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

echo "→ CLI"
"$b/bin/histdb" health | grep -q "db ok" || fail "histdb health"

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
