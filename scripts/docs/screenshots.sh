#!/usr/bin/env bash
# The documentation's screenshots, from scratch (`just docs-screenshots`): a throwaway Postgres, a
# throwaway server on the JVM with an EMPTY config and data directory of its own, the demo collection
# (docs/demo/) loaded into it, then Playwright captures every picture the pages use into
# docs/user-guide/assets/screenshots/. Everything it starts it stops again — by PID, never by name.
#
#   scripts/docs/screenshots.sh            capture every screenshot
#   scripts/docs/screenshots.sh --serve    load the demo and keep the server up (Ctrl-C stops it)
#   scripts/docs/screenshots.sh --only home,source   capture some (the names are in docs-screenshots.mjs)
#
# Needs: the active JDK 25 (`sdk env`: the pinned GraalVM, though any JDK 25 does) — sbt, pixi (the bundled Postgres), node + `npm ci --prefix modules/web`
# and Playwright's Chromium (`npm --prefix modules/web exec -- playwright install chromium`, or
# PLAYWRIGHT_CHROMIUM_EXECUTABLE). The map's background tiles come from OpenStreetMap, so it needs
# the internet; HISTMERSION_SCREENSHOTS_OFFLINE=1 blocks them (a blank background). pngquant and
# oxipng, when installed, shrink the PNGs.
#
# It never touches an installed Histmersion: no ~/Applications/Histmersion binary, no
# ~/.config/histmersion config, no data directory but a temporary one; HOME itself points into it.
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo"
serve=false; only=""
while [ $# -gt 0 ]; do
  case "$1" in
    --serve) serve=true ;;
    --only) only="$2"; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
  shift
done

db_port="${HISTMERSION_DOCS_DB_PORT:-55581}"
db_dir="$repo/target/docs-pg"
pg="$repo/runtime/postgres/.pixi/envs/default/bin"
out="$repo/docs/user-guide/assets/screenshots"
free_port() { python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])'; }
port="${HISTMERSION_DOCS_SERVER_PORT:-$(free_port)}"
stub_port="$(free_port)"
url="http://127.0.0.1:$port"

# ── the JDK: the active one, of the pinned major ──────────────────────────────────────────────
jh="${JAVA_HOME:-}"
[ -n "$jh" ] || jh="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | head -1 || true)"
{ [ -n "$jh" ] && [ -x "$jh/bin/java" ] && "$jh/bin/java" -version 2>&1 | grep -q 'version "25'; } \
  || { echo "no JDK 25 active — run 'sdk env install && sdk env' in the checkout" >&2; exit 3; }

# ── what runs, and what stops it ───────────────────────────────────────────────────────────────
tmp="$(mktemp -d "${TMPDIR:-/tmp}/histmersion-docs.XXXXXX")"
server_pid=""; stub_pid=""
cleanup() {
  [ -n "$server_pid" ] && kill "$server_pid" 2>/dev/null && wait "$server_pid" 2>/dev/null || true
  [ -n "$stub_pid" ] && kill "$stub_pid" 2>/dev/null && wait "$stub_pid" 2>/dev/null || true
  just test_db_port="$db_port" test_db_dir="$db_dir" test-db-down >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

echo "▸ building the server's class path (sbt)"
JAVA_HOME="$jh" sbt --server -batch server/writeClasspath >"$tmp/sbt.log" 2>&1 \
  || { tail -30 "$tmp/sbt.log" >&2; exit 1; }
classpath_file="$(find target/out/jvm -path '*histmersion-server/classpath.txt' | head -1)"
[ -n "$classpath_file" ] || { echo "no server classpath.txt under target/out/jvm" >&2; exit 1; }

[ -d modules/web/node_modules/playwright ] || npm ci --prefix modules/web >/dev/null

echo "▸ a throwaway Postgres on :$db_port"
just test_db_port="$db_port" test_db_dir="$db_dir" test-db >"$tmp/test-db.log" 2>&1 || { cat "$tmp/test-db.log" >&2; exit 3; }

echo "▸ a throwaway server on $url (data, config and HOME under $tmp)"
mkdir -p "$tmp/home" "$tmp/data" "$tmp/xdg-config" "$tmp/cache" "$tmp/spool" "$tmp/backups" "$tmp/bundles"
cat >"$tmp/histmersion.conf" <<'EOF'
# the documentation demo: agents ON (so their windows exist), with no provider, no role and every
# manager paused — nothing calls a model
histmersion {
  agents { enabled = true }
}
EOF
node docs/demo/converter-stub.mjs "$stub_port" & stub_pid=$!
(
  export HOME="$tmp/home" XDG_CONFIG_HOME="$tmp/xdg-config" XDG_CACHE_HOME="$tmp/cache" XDG_DATA_HOME="$tmp/data"
  export HISTMERSION_CONFIG="$tmp/histmersion.conf" HISTMERSION_DATA_DIR="$tmp/data" HISTMERSION_RUNTIME=false
  export HISTMERSION_DB_URL="jdbc:postgresql://127.0.0.1:$db_port/histmersion_test"
  export HISTMERSION_PORT="$port" HISTMERSION_BIND=127.0.0.1 HISTMERSION_PUBLIC_URL="$url"
  export HISTMERSION_CONVERTER_URL="http://127.0.0.1:$stub_port"
  export HISTMERSION_SPOOL_DIR="$tmp/spool" HISTMERSION_BACKUPS_DIR="$tmp/backups" HISTMERSION_BUNDLES_DIR="$tmp/bundles"
  export HISTMERSION_PG_BIN_DIR="$pg" HISTMERSION_INIT_DATA_DIR="$repo/db"
  # the terrain base map: the small Aegean extract the browser tests use (no download)
  export HISTMERSION_BASEMAP_DIR="$repo/modules/web/test/fixtures/basemap" HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false \
    HISTMERSION_BASEMAP_VECTOR_ON_DEMAND=false HISTMERSION_BASEMAP_BATHYMETRY_ON_DEMAND=false
  # the online base maps stay the home shots' background (the fixture covers only the Aegean); the
  # local maps are in the menu all the same, and their own shots choose them
  export HISTMERSION_UI_BASE_MAP_SOURCE=online
  unset ANTHROPIC_API_KEY OPENAI_API_KEY HISTMERSION_AGENTS_LOCAL_API_KEY
  exec "$jh/bin/java" -Duser.home="$tmp/home" -cp "$(cat "$classpath_file")" com.histmersion.server.histmersionServer
) >"$tmp/server.log" 2>&1 & server_pid=$!
for _ in $(seq 1 120); do
  curl -fsS "$url/api/health" >/dev/null 2>&1 && break
  kill -0 "$server_pid" 2>/dev/null || { tail -40 "$tmp/server.log" >&2; exit 3; }
  sleep 1
done
curl -fsS "$url/api/health" >/dev/null || { tail -40 "$tmp/server.log" >&2; exit 3; }

echo "▸ loading the demo collection"
node docs/demo/load.mjs "$url"
"$pg/psql" -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$db_port" -U histmersion -d histmersion_test \
  -v picture="$repo/docs/demo/media/cleveland-1916.2002.jpg" \
  -v thumb="$repo/docs/demo/media/cleveland-1916.2002.thumb.jpg" -f docs/demo/extras.sql

if $serve; then
  echo "▸ serving the demo on $url — Ctrl-C stops it"
  wait "$server_pid" || true
  exit 0
fi

echo "▸ capturing screenshots into ${out#"$repo"/}"
mkdir -p "$out"
HISTMERSION_SERVER_URL="$url" HISTMERSION_SCREENSHOTS_OUT="$out" HISTMERSION_SCREENSHOTS_ONLY="$only" \
  node modules/web/test/docs-screenshots.mjs

if command -v pngquant >/dev/null; then pngquant --force --skip-if-larger --quality 70-95 --ext .png "$out"/*.png || true; fi
if command -v oxipng >/dev/null; then oxipng -q -o 3 --strip safe "$out"/*.png || true; fi
du -ch "$out"/*.png | tail -1 | sed 's/total$/in total/'
