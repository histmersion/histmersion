#!/usr/bin/env bash
# Two checkouts never replay each other's sbt 2 cache entries (docs/research-build-speed.md §2).
#
#   scripts/dev/cache-isolation-check.sh            the build as it is in THIS checkout (build.sbt, project/)
#   scripts/dev/cache-isolation-check.sh --shared   the same two checkouts on ONE shared disk cache (a temp
#                                                   dir, never ~/Library/Caches/sbt): shows what the shared
#                                                   cache did — the other checkout's results replayed, and
#                                                   a lost blob turning into missing classes there
#
# Makes two temporary `git worktree`s of HEAD (removed afterwards) with this checkout's build definition
# copied in, gives each its own version of a probe source in plugin-api (the smallest module), and builds
# them alternately, each with its own sbt server:
#   1. A builds "alpha", B builds "beta": each class file holds its own marker
#   2. A switches to B's exact source: A must compile it itself (a hit here = B's entry replayed in A)
#   3. B gets a compile error; A then takes the same broken source: A must compile and fail on its own,
#      not replay B's cached failure
#   4. each checkout's cache is inside it (Global / localCacheDirectory = <checkout>/target/sbt-cache, and
#      -Dsbt.global.localcache as the justfile passes it, for sbt's own global-plugins project)
#      and every restored output links into that checkout's own cache
# Needs ~2 GB of memory and ~5 minutes. Exit 0 = isolated; 1 = a replay crossed checkouts.
set -uo pipefail
mode="${1:-isolated}"
repo="$(cd "$(dirname "$0")/../.." && pwd)"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/hm-cache-check.XXXXXX")"
tmp="$(cd "$tmp" && pwd -P)"
a="$tmp/a" b="$tmp/b" shared="$tmp/shared-cache"
cleanup() {
  for w in "$a" "$b"; do
    [ -d "$w" ] && (cd "$w" && sbt --client shutdown >/dev/null 2>&1 || true)
    git -C "$repo" worktree remove --force "$w" >/dev/null 2>&1 || true
  done
  git -C "$repo" worktree prune >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT

passed=0; failed=0
ok()  { passed=$((passed + 1)); echo "  ok   $1"; }
bad() { failed=$((failed + 1)); echo "  FAIL $1"; }

for w in "$a" "$b"; do
  git -C "$repo" worktree add --detach "$w" HEAD >/dev/null 2>&1 || { echo "git worktree add $w failed" >&2; exit 2; }
  # the build definition under test is this checkout's, committed or not
  cp "$repo/build.sbt" "$w/build.sbt"
  cp "$repo"/project/*.sbt "$repo"/project/*.scala "$repo/project/build.properties" "$w/project/"
  if [ "$mode" = --shared ]; then
    printf '\nGlobal / localCacheDirectory := file("%s")\n' "$shared" > "$w/zz-shared-cache.sbt"
  fi
done

probe="modules/plugin-api/src/main/scala/com/histmersion/plugin/CacheProbe.scala"
classes() { find "$1/target/out" -path '*histmersion-plugin-api/classes' -type d 2>/dev/null | head -1; }
write() { printf 'package com.histmersion.plugin\n\nobject CacheProbe:\n  val marker: String = "%s"\n' "$2" > "$1/$probe"; }
broken() { printf 'package com.histmersion.plugin\n\nobject CacheProbe:\n  val marker: Int = "%s"\n' "$2" > "$1/$probe"; }
# build <checkout> <label> — sbt's own server per call (`--server -batch`, what the recipes use)
build() {
  local w="$1" log="$tmp/$2.log"
  local flag=(); [ "$mode" = --shared ] || flag=("-Dsbt.global.localcache=$w/target/sbt-cache")  # as the justfile's `sbt`
  (cd "$w" && sbt --server -batch --no-colors ${flag[@]+"${flag[@]}"} "show Global / localCacheDirectory" pluginApi/compile) > "$log" 2>&1
  echo $? > "$log.rc"
}
rc() { cat "$tmp/$1.log.rc"; }
compiled() { grep -q "compiling .*histmersion-plugin-api/classes" "$tmp/$1.log"; }
holds() {  # the probe class in <checkout> holds <marker>
  local f; f="$(classes "$1")/com/histmersion/plugin/CacheProbe\$.class"
  [ -f "$f" ] && LC_ALL=C grep -aq "$2" "$f"
}

echo "cache isolation check ($mode) in $tmp"
write "$a" alpha; build "$a" a1
write "$b" beta;  build "$b" b1
[ "$(rc a1)" = 0 ] && holds "$a" alpha && ok "A builds its own source (alpha)" || bad "A did not build alpha (see $tmp/a1.log)"
[ "$(rc b1)" = 0 ] && holds "$b" beta  && ok "B builds its own source (beta)"  || bad "B did not build beta (see $tmp/b1.log)"

write "$a" beta; build "$a" a2
if [ "$(rc a2)" = 0 ] && holds "$a" beta; then
  if compiled a2; then ok "A compiles B's source itself (no entry of B's replayed)"
  else bad "A REPLAYED B's compile result: no compile ran in A for B's source"; fi
else bad "A did not end on beta (see $tmp/a2.log)"; fi

broken "$b" gamma; build "$b" b2
[ "$(rc b2)" != 0 ] && ok "B fails on its broken source" || bad "B's broken source compiled (see $tmp/b2.log)"
broken "$a" gamma; build "$a" a3
if [ "$(rc a3)" != 0 ]; then
  if compiled a3; then ok "A's failure is its own compile, not B's cached failure"
  else bad "A REPLAYED B's cached compile failure"; fi
else bad "A's broken source compiled (see $tmp/a3.log)"; fi
write "$a" delta; build "$a" a4
[ "$(rc a4)" = 0 ] && holds "$a" delta && ok "A recovers on a fixed source (delta)" || bad "A did not recover (see $tmp/a4.log)"

if [ "$mode" != --shared ]; then
  for w in "$a" "$b"; do
    n="$(basename "$w")"
    dir="$(sed -n 's/^\[info\] \(\/.*sbt-cache\)$/\1/p' "$tmp/${n}1.log" | tail -1)"
    if [ "$dir" = "$w/target/sbt-cache" ] && [ -d "$dir/ac" ]; then ok "$n's cache is its own: ${dir#"$tmp"/}"
    else bad "$n's cache is '${dir:-?}', not $w/target/sbt-cache"; fi
    outside="$(find "$w/target/out" -type l 2>/dev/null | while read -r l; do t="$(readlink "$l")"; case "$t" in "$w"/*) ;; *) echo "$l -> $t" ;; esac; done | head -3)"
    [ -z "$outside" ] && ok "$n's restored outputs link only into its own cache" || bad "$n links outside itself: $outside"
  done
else
  # the shared cache's failure mode: a blob lost from the CAS (pruned, a cleanFull elsewhere, a
  # half-written copy) while its action entry stays — the other checkout gets the "hit" and no classes
  write "$a" epsilon; build "$a" a5
  zip="$(find "$(classes "$a")/.." -maxdepth 1 -name 'classes.sbtdir.zip' | head -1)"
  blob="$(readlink "$zip" 2>/dev/null || true)"
  if [ -n "$blob" ] && [ -f "$blob" ]; then
    chmod u+w "$blob"; rm -f "$blob"
    write "$b" epsilon; build "$b" b3
    if [ "$(rc b3)" = 0 ] && ! compiled b3 && ! holds "$b" epsilon; then
      bad "B 'hit' A's entry whose blob is gone: no compile ran and B has no epsilon class (the shared cache's poisoned replay)"
    else ok "B rebuilt after A's blob was lost"; fi
  else echo "  note: A's classes zip is not a CAS link ($zip) — skipped the lost-blob step"; fi
fi

echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
