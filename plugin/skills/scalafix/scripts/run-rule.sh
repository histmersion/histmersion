#!/usr/bin/env bash
# Runs a Scalafix rule on a copy of a scala-cli project and checks the result.
#
# Usage: run-rule.sh [--apply] <rule> <project-dir> [expected-dir]
#
#   <rule> is a rule file (rules/MyRule.scala) or the name of a built-in rule (ProcedureSyntax).
#   Published rules (dependency:...) only run through sbt-scalafix, see references/applying.md.
#
#   1. copies <project-dir> to a temporary directory (the original is only modified with --apply),
#   2. compiles it with SemanticDB,
#   3. runs the rule,
#   4. checks the reported lints against the `// assert: RuleName` markers in the sources (if there are any),
#   5. prints the diff between the original and the rewritten sources,
#   6. if [expected-dir] is given, diffs the result against the expected sources,
#   7. compiles the rewritten sources with the project's own compiler options,
#   8. with --apply, and only if everything above succeeded, copies the changed files back to <project-dir>.
#
# Exit code 0 means: the rule ran, the lints match the markers, the result matches [expected-dir] (if given), and it compiles.
set -uo pipefail
. "$(dirname "$0")/lib.sh"

apply=0
if [ "${1:-}" = "--apply" ]; then apply=1; shift; fi

rule=$(rule_spec "$1")
project=$(cd "$2" && pwd)
expected=${3:+$(cd "$3" && pwd)}

prepare_project "$project"

echo "== Running ${rule#file:}"
run_scalafix "$rule" 2>&1 | tee "$work/.scalafix.log"
scalafix_status=${PIPESTATUS[0]}
status=0
case $scalafix_status in
  0) ;;
  64) echo "note: scalafix exited with 64 because lints with Error severity were reported" ;;
  *) echo "FAIL: scalafix failed with exit code $scalafix_status (see the messages above: unknown rule, rule doesn't compile, ...)"; status=1 ;;
esac

echo "== Checking lints"
check_lints "$work/.scalafix.log" || status=1

echo "== Changes made by the rule"
(cd "$work" && for f in $sources; do diff -u "$project/$f" "$f" --label "a/$f" --label "b/$f"; done)

if [ -n "$expected" ]; then
  echo "== Comparing with $expected"
  mismatch=0
  for f in $sources; do
    if [ ! -f "$expected/$f" ]; then
      echo "missing in expected: $f"
      mismatch=1
    elif ! diff -u "$expected/$f" "$work/$f" --label "expected/$f" --label "actual/$f"; then
      mismatch=1
    fi
  done
  if [ $mismatch -eq 0 ]; then echo "OK: the result matches the expected output"; else echo "FAIL: the result differs from the expected output"; status=1; fi
fi

echo "== Compiling the rewritten sources"
rm -rf "$work/.scala-build" "$work/.semanticdb"
if scala-cli compile "$work" >"$work/.compile.log" 2>&1; then
  echo "OK: the rewritten sources compile"
else
  cat "$work/.compile.log"
  echo "FAIL: the rewritten sources don't compile"
  status=1
fi

if [ $apply -eq 1 ]; then
  if [ $status -eq 0 ]; then
    for f in $sources; do
      cmp -s "$work/$f" "$project/$f" || { cp "$work/$f" "$project/$f" && echo "applied: $f"; }
    done
  else
    echo "not applied: fix the failures above first"
  fi
fi
exit $status
