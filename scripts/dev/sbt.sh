#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
home="$("$root/scripts/dev/pinned-jdk.sh")"
export JAVA_HOME="$home" PATH="$home/bin:$PATH"
exec sbt --server -batch -Dsbt.global.localcache="$root/target/sbt-cache" "$@"
