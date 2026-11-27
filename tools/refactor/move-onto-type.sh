#!/usr/bin/env bash
# One MoveOntoType batch over every module, with every module's SemanticDB named the way tools/lint/lint.sh names it.
# Without --semanticdb-targetroots, Scalafix's auto-classpath misses some modules' SemanticDB (upstreams), hands the
# rule those files without symbols, and their references are silently left as they were.
#   bash tools/refactor/move-onto-type.sh tools/refactor/move-onto-type-<batch>.conf
# After `sbt compile Test/compile`, from the worktree root. Then delete the files the report lists as emptied.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
[ $# -eq 1 ] || { echo "usage: move-onto-type.sh <config>" >&2; exit 64; }
props=target/build-versions.properties
[ -f "$props" ] || { echo "move-onto-type: $props is missing: load the build once (sbt about)" >&2; exit 64; }
scala=$(grep '^scala=' "$props" | cut -d= -f2)
roots=()
while IFS= read -r dir; do roots+=(--semanticdb-targetroots "$dir"); done < <(
  find target/out -mindepth 3 -maxdepth 4 -type d \( -name meta -o -name test-meta \) -path "*/scala-$scala/*" | sort
)
SCALAFIX_JAVA_OPTS="${SCALAFIX_JAVA_OPTS:--Xmx6g -Xss8m}" exec bash tools/refactor/scalafix.sh --config "$1" \
  --sourceroot . --auto-classpath --auto-classpath-roots target/out "${roots[@]}" --files modules
