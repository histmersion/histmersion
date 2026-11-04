#!/usr/bin/env bash
# Prints the Scalameta tree of a code snippet: a quick, syntax-only look at a tree shape.
#
# Usage: tree-of.sh [--scala3] [--tokens] '<code>'
#
#   tree-of.sh 'prices mapValues { p => p * 2 }'
#   tree-of.sh --scala3 'xs.map: x =>
#     x + 1'
#
# Parses as an expression, then a statement, then a source file. Uses the Scala 2.13 dialect
# unless --scala3 is given. --tokens also lists the tokens (useful for token-level patches).
# No types or symbols here: for those, run explore.sh on code that compiles.
set -euo pipefail
if [ $# -eq 0 ]; then
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi
# --workspace keeps the build cache out of the skill directory
exec scala-cli run --server=false -q --workspace "${TMPDIR:-/tmp}/scalafix-skill-tree-of" "$(dirname "$0")/TreeOf.scala" -- "$@"
