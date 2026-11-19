#!/usr/bin/env bash
# The fixture of Magnum3Frag: runs the rule (syntactic) over input/Uses.scala and input/Kept.scala with fixture.conf,
# diffs the result against expected/, then compiles expected/ against Magnum 2.0.0. Run from the worktree root:
#   bash tools/refactor/magnum3-frag-fixture/check.sh
set -euo pipefail
here=tools/refactor/magnum3-frag-fixture
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp -R "$here/input" "$tmp/src"
bash tools/refactor/scalafix.sh --config "$here/fixture.conf" --sourceroot "$tmp/src" \
  --files "$tmp/src/Uses.scala" --files "$tmp/src/Kept.scala" --files "$tmp/src/NoSql.scala" 2> "$tmp/scalafix.err" \
  || { cat "$tmp/scalafix.err"; exit 1; }
for f in Uses Kept NoSql; do diff -u "$here/expected/$f.scala" "$tmp/src/$f.scala"; done
diff -u "$here/expected/util/Helpers.scala" "$tmp/src/util/Helpers.scala"
scala-cli compile --server=false -q "$here/expected"
echo "magnum3-frag fixture: ok"
