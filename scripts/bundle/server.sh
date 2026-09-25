#!/usr/bin/env bash
# Build the native server and CLI with the PINNED GraalVM (.sdkmanrc) and put them in <out>/bin.
# The JDK matters: a binary built by another JDK's native-image fails at startup (see AGENTS.md).
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/bin"
root="$(cd "$(dirname "$0")/../.." && pwd)"
jdk="$(sed -n 's/^java=\([^ ]*\).*/\1/p' "$root/.sdkmanrc")"
home="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/$jdk"
[ -x "$home/bin/native-image" ] || { echo "GraalVM $jdk not found at $home — run 'sdk env install' in $root" >&2; exit 1; }
export JAVA_HOME="$home" GRAALVM_HOME="$home" PATH="$home/bin:$PATH"
(cd "$root" && sbt --client shutdown >/dev/null 2>&1 || true; sbt --server -batch "server/nativeImage; cli/nativeImage")
mkdir -p "$out"
install -m 0755 "$(find "$root/target" -type f -name histdb-server -perm -u+x | head -1)" "$out/histdb-server"
install -m 0755 "$(find "$root/target" -type f -name histdb-cli -perm -u+x | head -1)" "$out/histdb"
ls -la "$out" | sed 's/^/  /'
