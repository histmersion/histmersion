#!/usr/bin/env bash
# Build the native server and CLI with the active JDK, which must be the PINNED GraalVM CE
# (scripts/bundle/graalvm-pins.txt; `sdk env install && sdk env` activates it), and put them
# in <out>/bin; the seed data the server imports on its first start goes to <out>/db; the RELINK KIT (the
# server's Sloth-patched jars, its native-image arguments, kit.json + SHA256SUMS, Sloth itself) to
# <out>/kit, and the scripts that use it to <out>/bin and <out>/libexec (docs/research-plugins.md §7.4, §8).
# The JDK matters: a binary built by another JDK's native-image fails at startup (see AGENTS.md), and the
# kit records the version a relink must use — so it is the EXACT pinned version, not just its major.
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
# stale binaries must never be mistaken for a fresh build
bins | xargs rm -f
(cd "$root" && sbt --client shutdown >/dev/null 2>&1 || true; sbt --server -batch -Dsbt.global.localcache="$root/target/sbt-cache" "server/nativeImage; cli/nativeImage; server/relinkKit; pluginHello/package")
server_bin="$(bins | grep '/histmersion-server$' | head -1)"; cli_bin="$(bins | grep '/histmersion-cli$' | head -1)"
[ -n "$server_bin" ] && [ -n "$cli_bin" ] || { echo "native-image produced no binaries (server: '${server_bin}', cli: '${cli_bin}')" >&2; exit 1; }
mkdir -p "$out"
install -m 0755 "$server_bin" "$out/histmersion-server"
install -m 0755 "$cli_bin" "$out/histmersion"

# ── the relink kit ─────────────────────────────────────────────────────────────────────────────────
bundle="$(dirname "$out")"
kit_src="$(find "$root/target/out" -type d -path '*histmersion-server/kit' | head -1)"
[ -n "$kit_src" ] && [ -f "$kit_src/classpath" ] || { echo "server/relinkKit wrote no kit" >&2; exit 1; }
rm -rf "$bundle/kit" && cp -R "$kit_src" "$bundle/kit"
printf '%s\n' "$jdk" > "$bundle/kit/graalvm-version"
[ -f "$bundle/kit/tools/sloth.jar" ] || echo "  note: no Sloth jar (SLOTH_JAR) — a relink will link plugin jars unpatched"
version="$(sed -n 's/.*version *:= *"\([^"]*\)".*/\1/p' "$root/build.sbt" | head -1)"
commit="$(git -C "$root" rev-parse --short=12 HEAD 2>/dev/null || echo -)"
"$home/bin/java" -cp "$bundle/kit/lib/*" com.histmersion.server.plugins.PluginTool kit-manifest \
  "$bundle/kit" "$version" "$commit" "$(cat "$bundle/kit/graalvm-version")" | sed 's/^/  /'
# the stock binary's plugin-set record: first-party plugins only
printf '{\n  "digest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",\n  "spiVersion": %s,\n  "jars": [],\n  "mode": "native"\n}\n' \
  "$(sed -n 's/.*"spiVersion" *: *\([0-9]*\).*/\1/p' "$bundle/kit/kit.json" | head -1)" > "$out/histmersion-server.plugin-set.json"
# the build record (kit-common.sh): linked HERE, from this kit, with no plugin jars — an install on this
# same machine (`just install`) keeps this binary instead of linking the same one again; on any other
# machine (a CI bundle) the machine differs and the install links its own
hk_write_build_record "$out/histmersion-server.build" native "$bundle/kit" "$bundle/.no-plugins" bundle
# the scripts around the kit
mkdir -p "$bundle/libexec"
install -m 0755 "$root/scripts/bundle/relink.sh"      "$out/histmersion-relink"
install -m 0755 "$root/scripts/bundle/server-jvm.sh"  "$out/histmersion-server-jvm"
install -m 0755 "$root/scripts/bundle/toolchain.sh"   "$out/histmersion-toolchain"
install -m 0755 "$root/scripts/bundle/signing.sh"     "$out/histmersion-signing"
install -m 0644 "$root/scripts/bundle/kit-common.sh"  "$bundle/libexec/kit-common.sh"
install -m 0755 "$root/scripts/bundle/server-verified.sh" "$bundle/libexec/histmersion-server-verified"
install -m 0644 "$root/scripts/bundle/graalvm-pins.txt" "$bundle/libexec/graalvm-pins.txt"
install -m 0755 "$root/scripts/bundle/codesign-identity-macos.sh" "$bundle/libexec/codesign-identity-macos.sh"
# a copy of the program carries its licence and the notices of the work by others it ships
install -m 0644 "$root/LICENSE" "$root/NOTICE" "$root/THIRD-PARTY.md" "$bundle/"
du -sh "$bundle/kit" | sed 's/^/  kit: /'
ls -la "$out" | sed 's/^/  /'

# the first-run data the server imports into an EMPTY database by itself (#720), and `histmersion
# init` on demand: the gazetteer and the period presets — the data, not the scripts that regenerate it;
# db/gazetteer carries only the PINS of the external extracts (#1064), never the extracts themselves
data="$(dirname "$out")/db"
rm -rf "$data" && mkdir -p "$data"
cp -R "$root/db/places" "$root/db/periods" "$root/db/gazetteer" "$data/"
find "$data" \( -name '*.py' -o -name '*.sh' -o -name '__pycache__' \) -prune -exec rm -rf {} +
du -sh "$data" | sed 's/^/  /'
