#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
props=target/build-versions.properties
version() { grep "^$1=" "$props" | cut -d= -f2; }
exec scala-cli run --server=false -q -S "$(version scala)" --main-class countSuppressions tools/lint/count
