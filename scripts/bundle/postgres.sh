#!/usr/bin/env bash
# Copy the portable PostgreSQL 18 + PostGIS runtime (runtime/postgres, conda-forge via pixi) into a
# bundle. conda-forge binaries find their libraries relative to themselves, so the copy runs from
# anywhere; PROJ's data path is supplied at start by the server (PROJ_DATA).
#
#   scripts/bundle/postgres.sh <out-dir>      # → <out-dir>/pg/{bin,lib,share}
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/pg"
env_dir="$(cd "$(dirname "$0")/../../runtime/postgres" && pixi install >/dev/null && echo "$PWD/.pixi/envs/default")"
rm -rf "$out" && mkdir -p "$out"
# only what a server needs: binaries, libraries, the share/ tree (extensions, timezones, proj.db)
cp -R "$env_dir/bin" "$env_dir/lib" "$env_dir/share" "$out/"
"$out/bin/postgres" --version | sed 's/^/  /'
du -sh "$out" | sed 's/^/  /'
