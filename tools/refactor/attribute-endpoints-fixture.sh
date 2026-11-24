#!/usr/bin/env bash
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
