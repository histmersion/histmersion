#!/usr/bin/env bash
# The fixture of Magnum2Frag: compiles input/ (Magnum 1.3.1, with SemanticDB), runs the rule over input/Uses.scala, diffs
# the result against expected/Uses.scala, then compiles expected/ against Magnum 2.0.0. Run from the worktree root:
#   bash tools/refactor/magnum2-frag-fixture/check.sh
set -euo pipefail
here=tools/refactor/magnum2-frag-fixture
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp -R "$here/input" "$tmp/src"
scala-cli compile --server=false -q "$tmp/src" --semantic-db --semanticdb-sourceroot "$tmp/src" \
  --semanticdb-targetroot "$tmp/sdb"
bash tools/refactor/scalafix.sh --rules file:tools/refactor/Magnum2Frag.scala --sourceroot "$tmp/src" \
  --semanticdb-targetroots "$tmp/sdb" --classpath "$tmp/sdb" --files "$tmp/src/Uses.scala" 2> "$tmp/scalafix.err" \
  || { cat "$tmp/scalafix.err"; exit 1; }
diff -u "$here/expected/Uses.scala" "$tmp/src/Uses.scala"
scala-cli compile --server=false -q "$here/expected"
echo "magnum2-frag fixture: ok"
