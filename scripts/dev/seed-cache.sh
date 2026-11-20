#!/usr/bin/env bash
# Seed a NEW checkout's sbt 2 disk cache with a COPY of another checkout's (#3765), so its first build
# replays what that one already compiled instead of compiling everything cold.
#
#   scripts/dev/seed-cache.sh [--from <checkout>] [<checkout>]     # copy; <checkout> defaults to this one
#   scripts/dev/seed-cache.sh --verify [<checkout>]                  # after a build: every output links inside it
#
# --from defaults to the main worktree (the first `git worktree list` entry). What is copied is the
# source's target/sbt-cache alone — its action entries (ac/, keyed by content, paths relative to the
# checkout) and their blobs (cas/) — never its target/out, whose outputs are symlinks into the SOURCE's
# store. The new checkout's own build (build.sbt: `Global / localCacheDirectory` = <checkout>/target/sbt-cache)
# then restores its outputs as links into ITS copy: nothing is shared, nothing another checkout prunes or
# rewrites can reach it (docs/research-build-speed.md §2 — sharing one store was the cross-worktree bug).
# The copy is refused when the source's cache holds a symbolic link (it would point outside the copy),
# and when the checkout already has a cache of its own (seeding is for a new worktree). On APFS the copy
# is a clone (`cp -c`): no data is duplicated until one side writes. `--verify` checks the result.
set -euo pipefail

from=""; verify=0; to=""
while [ $# -gt 0 ]; do
  case "$1" in
    --from) from="$2"; shift 2 ;;
    --verify) verify=1; shift ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    -*) echo "seed-cache: unknown option $1" >&2; exit 2 ;;
    *) to="$1"; shift ;;
  esac
done
[ -n "$to" ] || to="$(git rev-parse --show-toplevel)"
to="$(cd "$to" && pwd -P)"

if [ "$verify" = 1 ]; then
  [ -d "$to/target/out" ] || { echo "seed-cache: $to has no target/out — build it first" >&2; exit 2; }
  bad="$(find "$to/target/out" -type l 2>/dev/null | while read -r l; do t="$(readlink "$l")"
    case "$t" in ("$to"/*) ;; (/*) echo "$l -> $t" ;; (*) ;; esac; done | head -5)"
  # (`(pattern)`: macOS's /bin/bash 3.2 reads an unparenthesised `pattern)` inside `$(…)` as the end of the substitution)
  if [ -n "$bad" ]; then echo "seed-cache: outputs of $to link outside it:"; echo "$bad"; exit 1; fi
  n="$(find "$to/target/out" -type l 2>/dev/null | wc -l | tr -d ' ')"
  echo "seed-cache: $n restored output(s) of $to, every one linked inside it"
  exit 0
fi

if [ -z "$from" ]; then
  from="$(git -C "$to" worktree list --porcelain | awk '/^worktree /{print $2; exit}')"
fi
from="$(cd "$from" && pwd -P)"
[ "$from" != "$to" ] || { echo "seed-cache: the source and the checkout are the same ($to)" >&2; exit 2; }
src="$from/target/sbt-cache"; dst="$to/target/sbt-cache"
[ -d "$src/ac" ] && [ -d "$src/cas" ] || { echo "seed-cache: $from has no sbt cache to copy (build it once)" >&2; exit 1; }
if [ -e "$dst" ] && [ -n "$(ls -A "$dst" 2>/dev/null)" ]; then
  echo "seed-cache: $to already has a cache of its own ($dst) — seeding is for a new checkout" >&2; exit 1
fi
links="$(find "$src" -type l | head -3)"
[ -z "$links" ] || { echo "seed-cache: $src holds symbolic links — a copy would point outside itself:" >&2; echo "$links" >&2; exit 1; }

mkdir -p "$to/target"
tmp="$to/target/sbt-cache.seeding-$$"
rm -rf "$tmp"
# a clone on APFS (copy-on-write), a plain copy elsewhere
if cp -c -R "$src" "$tmp" 2>/dev/null; then how="cloned"; else cp -R "$src" "$tmp"; how="copied"; fi
# the copy holds no link (checked above) — and is still checked, since the source may change under us
[ -z "$(find "$tmp" -type l | head -1)" ] || { rm -rf "$tmp"; echo "seed-cache: the copy holds a symbolic link — refused" >&2; exit 1; }
rm -rf "$dst"
mv "$tmp" "$dst"
echo "seed-cache: $how $(find "$dst/ac" -type f | wc -l | tr -d ' ') action entries from $from → $dst"
echo "seed-cache: after the first build, check it with: scripts/dev/seed-cache.sh --verify $to"
