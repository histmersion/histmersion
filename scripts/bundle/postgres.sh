#!/usr/bin/env bash
# Copy the portable PostgreSQL 18 + PostGIS runtime (runtime/postgres, conda-forge via pixi) into a
# bundle. The server relocates the copy on first start (PgRelocation); PROJ's data path is supplied at
# start by the server too (PROJ_DATA).
#
#   scripts/bundle/postgres.sh <out-dir>      # → <out-dir>/pg/{bin,lib,share}
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/pg"
env_dir="$(cd "$(dirname "$0")/../../runtime/postgres" && pixi install >/dev/null && echo "$PWD/.pixi/envs/default")"
rm -rf "$out" && mkdir -p "$out"
# only what a server needs: binaries, libraries, the share/ tree (extensions, timezones, proj.db)
cp -R "$env_dir/bin" "$env_dir/lib" "$env_dir/share" "$out/"
# conda-forge's PostgreSQL finds lib/ and share/ only at the absolute prefix baked into its files; the
# server rewrites that prefix to wherever the bundle ends up (PgRelocation). Record it — exactly as the
# files spell it, which is not necessarily how `pwd` spells the environment path.
"$out/bin/pg_config" --configure | sed -n "s/.*'--prefix=\([^']*\)'.*/\1/p" > "$out/.prefix"
[ -s "$out/.prefix" ] || { echo "could not read the install prefix from pg_config" >&2; exit 1; }
"$out/bin/postgres" --version | sed 's/^/  /'
du -sh "$out" | sed 's/^/  /'
