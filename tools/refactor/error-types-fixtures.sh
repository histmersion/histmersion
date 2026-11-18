#!/usr/bin/env bash
# The fixtures of ErrorTypesToProblems (tools/refactor/ErrorTypesToProblems.scala), through the scalafix skill's
# run-rule.sh: the collect pass over a copy of error-types-fixture/input changes nothing and reports the verdicts in
# error-types-fixture/expected-report.tsv; the apply pass turns input/ into expected/ and the result compiles.
# Run from the worktree root: bash tools/refactor/error-types-fixtures.sh
set -euo pipefail
here=tools/refactor/error-types-fixture
run_rule=.claude/skills/scalafix/scripts/run-rule.sh
out=/tmp/histmersion-error-types-fixture
rm -rf "$out"
mkdir -p "$out/collect"
cp -R "$here/input/." "$out/collect/"
cp "$here/collect.conf" "$out/collect/.scalafix.conf"
echo "== collect"
bash "$run_rule" tools/refactor/ErrorTypesToProblems.scala "$out/collect" "$out/collect"
echo "== the verdicts"
diff -u "$here/expected-report.tsv" "$out/report.tsv"
echo "OK: the report matches"
echo "== apply"
bash "$run_rule" tools/refactor/ErrorTypesToProblems.scala "$here/input" "$here/expected"
