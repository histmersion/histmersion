#!/usr/bin/env bash
# The G3Misplaced worklist: every finding the lint sees, tolerated or not, one TSV row each (path, line, Object.def(param: Type)).
# Runs tools/refactor/G3Inventory.scala with the lint rules, like tools/lint/lint.sh. From the worktree root, after
# `sbt compile Test/compile`:  bash tools/refactor/g3-inventory.sh [out.tsv]   (default refactor-audit/lane-objects/g3.tsv)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
props=target/build-versions.properties
[ -f "$props" ] || { echo "g3-inventory: $props is missing: load the build once (sbt about)" >&2; exit 64; }
version() { grep "^$1=" "$props" | cut -d= -f2; }
scala=$(version scala)
out=${1:-refactor-audit/lane-objects/g3.tsv}
mkdir -p "$(dirname "$out")"
: > "$out"
roots=()
while IFS= read -r dir; do roots+=(--semanticdb-targetroots "$dir"); done < <(
  find target/out -mindepth 3 -maxdepth 4 -type d \( -name meta -o -name test-meta \) -path "*/scala-$scala/*" | sort
)
conf=$(mktemp)
trap 'rm -f "$conf"' EXIT
echo 'rules = ["class:com.histmersion.linting.G3Inventory"]' > "$conf"
G3_INVENTORY=$out scala-cli run --server=false -q -S "$scala" \
  --dep "ch.epfl.scala:scalafix-cli_$(version scalafixScala):$(version scalafix)" \
  --java-opt -Xmx6g --main-class scalafix.cli.Cli tools/lint/rules tools/refactor/G3Inventory.scala -- \
  --config "$conf" --scala-version "$scala" --sourceroot . \
  --auto-classpath --auto-classpath-roots target/out "${roots[@]}" \
  --files "${LINT_FILES:-modules}"
sort -o "$out" "$out"
wc -l < "$out"
