#!/usr/bin/env bash
# The fixtures of row 32's slice-4 rules, through the scalafix skill's run-rule.sh: <name>-fixture/input/ becomes
# <name>-fixture/expected/, the result compiles, and the report lists exactly <name>-fixture/expected-report.tsv.
# Run from the worktree root:
#   bash tools/refactor/attribute-endpoints-fixture.sh                                  (AttributeEndpoints)
#   bash tools/refactor/attribute-endpoints-fixture.sh record-fields AddRecordFields    (AddRecordFields)
set -euo pipefail
name=${1:-attribute-endpoints}
rule=${2:-AttributeEndpoints}
here=tools/refactor/$name-fixture
out=$(cd "$(mktemp -d)" && pwd -P)
trap 'rm -rf "$out"' EXIT
cp -R "$here/input/." "$out/"
sed "s#^  report = .*#  report = \"$out/report.tsv\"#" "tools/refactor/$name-fixture.conf" > "$out/.scalafix.conf"
bash .claude/skills/scalafix/scripts/run-rule.sh "tools/refactor/$rule.scala" "$out" "$here/expected"
awk -F'\t' 'BEGIN { OFS = "\t" } NF > 2 && $2 ~ /\// && $2 ~ /\.scala$/ { n = split($2, p, "/"); $2 = p[n] } { print }' \
  "$out/report.tsv" | diff -u "$here/expected-report.tsv" -
echo "OK: the report matches"
