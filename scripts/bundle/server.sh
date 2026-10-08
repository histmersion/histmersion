#!/usr/bin/env bash
# Build the native server and CLI with the PINNED GraalVM (.sdkmanrc) and put them in <out>/bin;
# the seed data the server imports on its first start goes to <out>/db.
# The JDK matters: a binary built by another JDK's native-image fails at startup (see AGENTS.md).
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/bin"
root="$(cd "$(dirname "$0")/../.." && pwd)"
jdk="$(sed -n 's/^java=\([^ ]*\).*/\1/p' "$root/.sdkmanrc")"
major="${jdk%%.*}"
# CI (setup-graalvm) provides GRAALVM_HOME; locally sdkman does. Either way it must be the pinned major.
if [ -n "${GRAALVM_HOME:-}" ] && [ -x "$GRAALVM_HOME/bin/native-image" ]; then
  home="$GRAALVM_HOME"
else
  home="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/$jdk"
fi
[ -x "$home/bin/native-image" ] || { echo "GraalVM $jdk not found at $home — run 'sdk env install' in $root" >&2; exit 1; }
have="$("$home/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[ "$have" = "$major" ] || { echo "GraalVM at $home is JDK $have, the pin is $jdk (.sdkmanrc)" >&2; exit 1; }
export JAVA_HOME="$home" GRAALVM_HOME="$home" PATH="$home/bin:$PATH"
bins() { find "$root/target/out" -type f \( -name histmersion-server -o -name histmersion-cli \) -perm -u+x 2>/dev/null || true; }
# stale binaries must never be mistaken for a fresh build
bins | xargs rm -f
(cd "$root" && sbt --client shutdown >/dev/null 2>&1 || true; sbt --server -batch "server/nativeImage; cli/nativeImage")
server_bin="$(bins | grep '/histmersion-server$' | head -1)"; cli_bin="$(bins | grep '/histmersion-cli$' | head -1)"
[ -n "$server_bin" ] && [ -n "$cli_bin" ] || { echo "native-image produced no binaries (server: '${server_bin}', cli: '${cli_bin}')" >&2; exit 1; }
mkdir -p "$out"
install -m 0755 "$server_bin" "$out/histmersion-server"
install -m 0755 "$cli_bin" "$out/histmersion"
ls -la "$out" | sed 's/^/  /'

# the first-run data the server imports into an EMPTY database by itself (#720), and `histmersion
# init` on demand: the gazetteer and the period presets — the data, not the scripts that regenerate it;
# db/gazetteer carries only the PINS of the external extracts (#1064), never the extracts themselves
data="$(dirname "$out")/db"
rm -rf "$data" && mkdir -p "$data"
cp -R "$root/db/places" "$root/db/periods" "$root/db/gazetteer" "$data/"
find "$data" \( -name '*.py' -o -name '*.sh' -o -name '__pycache__' \) -prune -exec rm -rf {} +
du -sh "$data" | sed 's/^/  /'
