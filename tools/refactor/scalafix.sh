#!/usr/bin/env bash
# The Scalafix CLI at the build's versions: scalafix-cli, the Scala 2.13 it runs on, and the build's Scala as
# --scala-version (unless the arguments name one, as a standalone fixture of its own Scala does). The versions come from
# target/build-versions.properties, which sbt writes from project/Dependencies.scala whenever it loads the build: load
# it once (any sbt command) in a fresh checkout. Run from the worktree root:
#   bash tools/refactor/scalafix.sh --config tools/refactor/<x>.conf --sourceroot . --files modules
# SCALAFIX_JAVA_OPTS adds JVM options (e.g. "-Xmx6g -Xss8m"). `--print <key>` prints one version (scala, scalafix,
# scalafixScala) for another script.
set -euo pipefail
versions=target/build-versions.properties
version() {
  [ -f "$versions" ] || { echo "$versions is missing: load the build once (sbt --server -batch about)" >&2; exit 2; }
  local v
  v="$(sed -n "s/^$1=//p" "$versions")"
  [ -n "$v" ] || { echo "$versions has no $1=" >&2; exit 2; }
  printf '%s\n' "$v"
}
if [ "${1:-}" = "--print" ]; then version "$2"; exit 0; fi
scala213="$(version scalafixScala)"
scalafix="$(version scalafix)"
args=("$@")
named=no
for a in "$@"; do [ "$a" = "--scala-version" ] && named=yes; done
[ "$named" = yes ] || args=(--scala-version "$(version scala)" "${args[@]}")
java_opts=()
for o in ${SCALAFIX_JAVA_OPTS:-}; do java_opts+=(--java-opt "$o"); done
exec scala-cli run --server=false -q -S "$scala213" ${java_opts[@]+"${java_opts[@]}"} \
  --dep "ch.epfl.scala:scalafix-cli_${scala213}:${scalafix}" --main-class scalafix.cli.Cli -- "${args[@]}"
