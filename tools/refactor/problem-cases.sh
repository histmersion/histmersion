#!/usr/bin/env bash
set -euo pipefail
conf=$1
shift
roots=()
for d in $(find target/out -maxdepth 4 -type d \( -name meta -o -name test-meta \) -path "*scala-$(bash tools/refactor/scalafix.sh --print scala)*"); do
  roots+=(--semanticdb-targetroots "$d")
done
files=()
for f in "$@"; do files+=(--files "$f"); done
SCALAFIX_JAVA_OPTS="${SCALAFIX_JAVA_OPTS:--Xmx6g}" bash tools/refactor/scalafix.sh --config "$conf" --sourceroot . \
  --auto-classpath --auto-classpath-roots target/out "${roots[@]}" "${files[@]}"
