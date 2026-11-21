#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/../../.." && pwd)"
seed="$here/scripts/dev/seed-cache.sh"
t="$(mktemp -d)"; trap 'rm -rf "$t"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
mkdir -p "$t/src/target/sbt-cache/ac" "$t/src/target/sbt-cache/cas" "$t/new" "$t/other"
echo entry > "$t/src/target/sbt-cache/ac/k1"; echo blob > "$t/src/target/sbt-cache/cas/sha256-1"
mkdir -p "$t/src/target/out"; ln -s "$t/src/target/sbt-cache/cas/sha256-1" "$t/src/target/out/x"

"$seed" --from "$t/src" "$t/new" >/dev/null || fail "a seed into a new checkout"
[ "$(cat "$t/new/target/sbt-cache/cas/sha256-1")" = blob ] || fail "the blob is copied"
[ ! -e "$t/new/target/out" ] || fail "target/out is not copied"
echo changed > "$t/new/target/sbt-cache/cas/sha256-1"
[ "$(cat "$t/src/target/sbt-cache/cas/sha256-1")" = blob ] || fail "the copy is independent of the source"

"$seed" --from "$t/src" "$t/new" >/dev/null 2>&1 && fail "a checkout with a cache of its own is refused"
"$seed" --from "$t/other" "$t/new2" >/dev/null 2>&1 && fail "a source without a cache is refused"
ln -s /etc "$t/src/target/sbt-cache/cas/link"
mkdir -p "$t/new3"
"$seed" --from "$t/src" "$t/new3" >/dev/null 2>&1 && fail "a source cache holding a link is refused"
[ ! -e "$t/new3/target/sbt-cache" ] || fail "nothing is left behind by a refusal"

mkdir -p "$t/new/target/out"; ln -s "$(cd "$t/new" && pwd -P)/target/sbt-cache/cas/sha256-1" "$t/new/target/out/a"
"$seed" --verify "$t/new" >/dev/null || fail "outputs inside the checkout verify"
ln -s "$t/src/target/sbt-cache/cas/sha256-1" "$t/new/target/out/b"
"$seed" --verify "$t/new" >/dev/null 2>&1 && fail "an output linked outside fails --verify"
echo "seed-cache-test: ok"
