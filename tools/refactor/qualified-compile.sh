#!/usr/bin/env bash
# The compile step of qualified-round.py: runs the given sbt tasks with every error reported and without formatting on
# compile, writes the whole output to <log> and prints `rc=<exit code>`. JAVA_HOME (and PATH) come from the caller.
#   bash tools/refactor/qualified-compile.sh <log> <sbt task>...
set -uo pipefail
log=$1
shift
mkdir -p "$(dirname "$log")"
timeout "${QUALIFIED_COMPILE_TIMEOUT:-3000}" sbt --server -batch -J-Xss16m -J-Xmx6g \
  "set every scalafmtOnCompile := false" "set every maxErrors := 100000" "$@" >"$log" 2>&1
rc=$?
echo "rc=$rc"
