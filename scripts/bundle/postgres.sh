#!/usr/bin/env bash
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/pg"
env_dir="$(cd "$(dirname "$0")/../../runtime/postgres" && pixi install >/dev/null && echo "$PWD/.pixi/envs/default")"
rm -rf "$out" && mkdir -p "$out"
cp -R "$env_dir/bin" "$env_dir/lib" "$env_dir/share" "$out/"
"$out/bin/pg_config" --configure | sed -n "s/.*'--prefix=\([^']*\)'.*/\1/p" > "$out/.prefix"
[ -s "$out/.prefix" ] || { echo "could not read the install prefix from pg_config" >&2; exit 1; }
"$out/bin/postgres" --version | sed 's/^/  /'
du -sh "$out" | sed 's/^/  /'
