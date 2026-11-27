#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
props=target/build-versions.properties
[ -f "$props" ] || { echo "lint: $props is missing: load the build once (sbt about)" >&2; exit 64; }
version() { grep "^$1=" "$props" | cut -d= -f2; }
scala=$(version scala)
roots=()
while IFS= read -r dir; do roots+=(--semanticdb-targetroots "$dir"); done < <(
  find target/out -mindepth 3 -maxdepth 4 -type d \( -name meta -o -name test-meta \) -path "*/scala-$scala/*" | sort
)
files=()
if [ -n "${LINT_FILES:-}" ]; then files=(--files "$LINT_FILES")
else for dir in modules/*; do [ "$dir" = modules/plugin-fixtures ] || files+=(--files "$dir"); done
fi
config=tools/lint/lint.conf
if [ "${1:-}" = "--config" ]; then config=$2; shift 2; fi
exec scala-cli run --server=false -q -S "$scala" \
  --dep "ch.epfl.scala:scalafix-cli_$(version scalafixScala):$(version scalafix)" \
  --java-opt -Xmx6g --main-class scalafix.cli.Cli tools/lint/rules -- \
  --config "$config" --scala-version "$scala" --sourceroot . \
  --auto-classpath --auto-classpath-roots target/out "${roots[@]}" \
  "${files[@]}" "$@"
