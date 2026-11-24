#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
props=target/build-versions.properties
version() { grep "^$1=" "$props" | cut -d= -f2; }
scala-cli test --server=false -q -S "$(version scala)" \
  --dep "ch.epfl.scala:scalafix-cli_$(version scalafixScala):$(version scalafix)" \
  --dep "org.scalameta::munit:$(version munit)" tools/lint/rules
scala-cli test --server=false -q -S "$(version scala)" --dep "org.scalameta::munit:$(version munit)" tools/lint/count
tools/lint/fixture.sh
