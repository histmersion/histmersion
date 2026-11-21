#!/usr/bin/env bash
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/bin"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=kit-common.sh
. "$root/scripts/bundle/kit-common.sh"
jdk="$(hk_pinned_version "$root/scripts/bundle/graalvm-pins.txt")"
[ -n "$jdk" ] || { echo "no GraalVM version in $root/scripts/bundle/graalvm-pins.txt" >&2; exit 1; }
home="$("$root/scripts/dev/pinned-jdk.sh")" || exit 1
echo "  GraalVM CE $jdk: $home"
export JAVA_HOME="$home" PATH="$home/bin:$PATH"
bins() { find "$root/target/out" -type f \( -name histmersion-server -o -name histmersion-cli \) -perm -u+x 2>/dev/null || true; }
bins | xargs rm -f
(cd "$root" && sbt --client shutdown >/dev/null 2>&1 || true; sbt --server -batch -Dsbt.global.localcache="$root/target/sbt-cache" "server/nativeImage; cli/nativeImage; server/relinkKit; server/pluginRepo; pluginHello/package; pluginHelloDeps/fixturesRepo")
server_bin="$(bins | grep '/histmersion-server$' | head -1)"; cli_bin="$(bins | grep '/histmersion-cli$' | head -1)"
[ -n "$server_bin" ] && [ -n "$cli_bin" ] || { echo "native-image produced no binaries (server: '${server_bin}', cli: '${cli_bin}')" >&2; exit 1; }
mkdir -p "$out"
install -m 0755 "$server_bin" "$out/histmersion-server"
install -m 0755 "$cli_bin" "$out/histmersion"

bundle="$(dirname "$out")"
kit_src="$(find "$root/target/out" -type d -path '*histmersion-server/kit' | head -1)"
[ -n "$kit_src" ] && [ -f "$kit_src/classpath" ] || { echo "server/relinkKit wrote no kit" >&2; exit 1; }
rm -rf "$bundle/kit" && cp -R "$kit_src" "$bundle/kit"
printf '%s\n' "$jdk" > "$bundle/kit/graalvm-version"
[ -f "$bundle/kit/tools/sloth.jar" ] || echo "  note: no Sloth jar (SLOTH_JAR) — a relink will link plugin jars unpatched"
version="$(sed -n 's/.*version *:= *"\([^"]*\)".*/\1/p' "$root/build.sbt" | head -1)"
commit="$(git -C "$root" rev-parse --short=12 HEAD 2>/dev/null || echo -)"
"$home/bin/java" -cp "$bundle/kit/lib/*" com.histmersion.server.plugins.pluginTool kit-manifest \
  "$bundle/kit" "$version" "$commit" "$(cat "$bundle/kit/graalvm-version")" | sed 's/^/  /'
# the stock binary's plugin-set record: first-party plugins only
printf '{\n  "digest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",\n  "spiVersion": %s,\n  "jars": [],\n  "mode": "native"\n}\n' \
  "$(sed -n 's/.*"spiVersion" *: *\([0-9]*\).*/\1/p' "$bundle/kit/kit.json" | head -1)" > "$out/histmersion-server.plugin-set.json"
hk_write_build_record "$out/histmersion-server.build" native "$bundle/kit" "$bundle/.no-plugins" bundle
repo_src="$(find "$root/target/out" -type d -path '*histmersion-server/plugin-repo' | head -1)"
[ -n "$repo_src" ] || { echo "server/pluginRepo wrote no repository" >&2; exit 1; }
api_jar="$(find "$repo_src" -name 'histmersion-plugin-api_3-*.jar' ! -name '*-sources.jar' | head -1)"
api_src="$(find "$repo_src" -name 'histmersion-plugin-api_3-*-sources.jar' | head -1)"
platform_pom="$(find "$repo_src" -name 'histmersion-plugin-platform_3-*.pom' | head -1)"
[ -n "$api_jar" ] && [ -n "$api_src" ] && [ -n "$platform_pom" ] \
  || { echo "the plugin repository in '$repo_src' lacks the API jar, its sources or the platform POM" >&2; exit 1; }
"$home/bin/jar" tf "$api_jar" | grep -qx 'com/histmersion/plugin/Plugin.class' \
  || { echo "$api_jar holds no com/histmersion/plugin/Plugin.class: not the plugin API" >&2; exit 1; }
"$home/bin/jar" tf "$api_src" | grep -qx 'com/histmersion/plugin/Plugin.scala' \
  || { echo "$api_src holds no com/histmersion/plugin/Plugin.scala: not the plugin API's sources" >&2; exit 1; }
rm -rf "$bundle/plugin-api" "$bundle/maven" && cp -R "$repo_src" "$bundle/maven"
mkdir -p "$bundle/libexec"
install -m 0755 "$root/scripts/bundle/relink.sh"      "$out/histmersion-relink"
install -m 0755 "$root/scripts/bundle/server-jvm.sh"  "$out/histmersion-server-jvm"
install -m 0755 "$root/scripts/bundle/toolchain.sh"   "$out/histmersion-toolchain"
install -m 0755 "$root/scripts/bundle/signing.sh"     "$out/histmersion-signing"
install -m 0644 "$root/scripts/bundle/kit-common.sh"  "$bundle/libexec/kit-common.sh"
install -m 0755 "$root/scripts/bundle/server-verified.sh" "$bundle/libexec/histmersion-server-verified"
install -m 0644 "$root/scripts/bundle/graalvm-pins.txt" "$bundle/libexec/graalvm-pins.txt"
install -m 0755 "$root/scripts/bundle/codesign-identity-macos.sh" "$bundle/libexec/codesign-identity-macos.sh"
install -m 0644 "$root/LICENSE" "$root/NOTICE" "$root/THIRD-PARTY.md" "$bundle/"
du -sh "$bundle/kit" | sed 's/^/  kit: /'
ls -la "$out" | sed 's/^/  /'

data="$(dirname "$out")/db"
rm -rf "$data" && mkdir -p "$data"
cp -R "$root/db/places" "$root/db/periods" "$root/db/gazetteer" "$data/"
find "$data" \( -name '*.py' -o -name '*.sh' -o -name '__pycache__' -o -name 'testdata' \) -prune -exec rm -rf {} +
du -sh "$data" | sed 's/^/  /'
