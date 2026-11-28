#!/usr/bin/env bash
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
