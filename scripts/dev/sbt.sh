#!/usr/bin/env bash
# sbt for the justfile's recipes: on the active JDK, which must be the pinned GraalVM CE (pinned-jdk.sh),
# so tests, the tracing agent and the native images all see the JDK the binaries are built with.
# `--server -batch` starts a dedicated server per invocation: sbt 2's thin client shares one and drops
# the caller's environment (HISTMERSION_TEST_DB_URL among it). The disk cache is this checkout's own
# (build.sbt sets it for the build, project/plugins.sbt for the meta-build; the flag also covers sbt's
# own global-plugins project, loaded before either).
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
home="$("$root/scripts/dev/pinned-jdk.sh")"
export JAVA_HOME="$home" PATH="$home/bin:$PATH"
exec sbt --server -batch -Dsbt.global.localcache="$root/target/sbt-cache" "$@"
