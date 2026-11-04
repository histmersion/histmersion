#!/usr/bin/env bash
# Shows how the real occurrences of a name in a scala-cli project look as Scalameta trees, grouped by shape.
#
# Usage: explore.sh <project-dir> <name-regex> [symbol-regex]
#
#   explore.sh samples 'mapValues'                             every `mapValues`, whatever it refers to
#   explore.sh samples 'mapValues' '^scala/collection/MapOps#'  only Map#mapValues
#
# For each shape it prints the symbol, the shape (the tree around the name, with unrelated
# subtrees abstracted to <Type>), all locations, and an example with code, full tree, symbols
# with signatures, and compiler synthetics (implicits, desugared for-comprehensions; Scala 2 only).
#
# Environment: EXPLORE_UP (context depth, default 4), EXPLORE_EXAMPLES (examples per shape, default 1).
# The project is copied first; nothing is modified.
set -uo pipefail
. "$(dirname "$0")/lib.sh"

if [ $# -lt 2 ]; then
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

explorer=$(cd "$(dirname "$0")" && pwd)/TreeExplorer.scala
project=$(cd "$1" && pwd)
export EXPLORE_NAME=$2
export EXPLORE_SYMBOL=${3:-}

prepare_project "$project" >/dev/null
export EXPLORE_ROOT=$work
run_scalafix "file:$explorer"
