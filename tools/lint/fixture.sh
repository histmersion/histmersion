#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
repo=$PWD
props=$repo/target/build-versions.properties
version() { grep "^$1=" "$props" | cut -d= -f2; }
fixture=tools/refactor/guideline-fixture/expected
golden=tools/lint/fixture-findings.txt
work=$(cd "$(mktemp -d)" && pwd -P)
trap 'rm -rf "$work"' EXIT
cp -R "$fixture"/. "$work"/
rm -f "$work"/*.tsv "$work"/.scalafix.conf
mkdir -p "$work/modules/lint/src/main/scala/com/histmersion/lint"
cp modules/lint/src/main/scala/com/histmersion/lint/*.scala "$work/modules/lint/src/main/scala/com/histmersion/lint/"
scala-cli compile --server=false -q "$work" --semanticdb --semanticdb-targetroot "$work/.semanticdb" > "$work/compile.log" 2>&1 || { cat "$work/compile.log"; exit 2; }
classpath=$(scala-cli compile --server=false -q "$work" --print-class-path 2>/dev/null | tail -1)
scala=$(grep -rhoE '^//> using scala [0-9.]+' --include='*.scala' "$work" | head -1 | awk '{print $4}')
(
  cd "$work"
  LINT_SEMANTICDB_ROOTS="$work/.semanticdb" scala-cli run --server=false -q -S "$(version scala)" \
    --dep "ch.epfl.scala:scalafix-cli_$(version scalafixScala):$(version scalafix)" \
    --main-class scalafix.cli.Cli "$repo/tools/lint/rules" -- \
    --rules class:com.histmersion.linting.GuidelineLint --scala-version "$scala" --sourceroot "$work" \
    --semanticdb-targetroots "$work/.semanticdb" --classpath "$classpath" --files modules > "$work/lint.log" 2>&1 || true
)
grep -oE '^[^ ]+\.scala:[0-9]+:[0-9]+: error: \[GuidelineLint\.[A-Za-z0-9]+\]' "$work/lint.log" \
  | sed -E "s|^$work/||; s|:[0-9]+: error: \[GuidelineLint\.| |; s|\]$||" | sort > "$work/actual.txt"
if [ "${1:-}" = "--write" ]; then cp "$work/actual.txt" "$golden"; echo "fixture: wrote $golden"; exit 0; fi
if diff -u "$golden" "$work/actual.txt"; then echo "fixture: the findings match $golden"; else echo "fixture: the findings differ from $golden" >&2; exit 1; fi
