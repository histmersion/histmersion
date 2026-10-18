#!/usr/bin/env bash
# bin/histmersion-server-jvm — the server in JVM mode (docs/research-plugins.md §8.5): the same kit the
# native binary is linked from, plus the plugins directory's jars, on a JDK. Plugins are found at each
# start, so adding one needs a restart, not a relink. The cost is the JVM's start-up and heap.
#
#   histmersion-server-jvm [--self-check | --help]       the server's own arguments, passed through
#
# The fallback when no native relink is possible (no GraalVM/C toolchain, too little memory, a plugin
# that will not link): `histmersion-relink --jvm` makes it bin/histmersion-server, so the LaunchAgent /
# systemd unit runs it unchanged. The JDK: HISTMERSION_JAVA_HOME, else the GraalVM `histmersion-toolchain
# install` provisioned (a full JDK), else GRAALVM_HOME / JAVA_HOME of the pinned major version.
# HISTMERSION_JVM_OPTS adds JVM options (e.g. -Xmx4g). With GPG provenance set up, the plugins
# directory must match its signed manifest, or the server does not start.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
common="$here/../libexec/kit-common.sh"; [ -f "$common" ] || common="$here/kit-common.sh"
# shellcheck source=kit-common.sh
. "$common"

install="${HISTMERSION_HOME:-$(cd "$here/.." && pwd)}"
kit="$install/kit"
plugins="$(hk_plugins_dir)"
signing="$here/histmersion-signing"; [ -x "$signing" ] || signing="$here/signing.sh"
[ -f "$kit/classpath" ] || hk_die 3 "no kit at $kit — JVM mode needs the bundle's kit/"
version="$(cat "$kit/graalvm-version")"
jh="$(hk_find_java "$version")" \
  || hk_die 3 "no JDK ${version%%.*} for JVM mode — run histmersion-toolchain install (or set HISTMERSION_JAVA_HOME)"

if [ -n "$(hk_gpg_key)" ] && [ -n "$(hk_plugin_jars "$plugins")" ]; then
  "$signing" verify-plugins >/dev/null 2>&1 \
    || hk_die 3 "the plugins directory does not match its signed manifest — not starting (histmersion-signing verify-plugins says why)"
fi

cp=""
while IFS= read -r n; do [ -n "$n" ] && cp="$cp${cp:+:}$kit/lib/$n"; done < "$kit/classpath"
jars=()
while IFS= read -r j; do jars+=("$j"); done < <(hk_plugin_jars "$plugins")
if [ "${#jars[@]}" -gt 0 ]; then
  # the plugin set this start runs with, reported by /api/about and --self-check as a relinked binary's is
  mkdir -p "$plugins/.jvm"
  "$jh/bin/java" -cp "$kit/lib/*" com.histmersion.server.plugins.PluginTool plugin-set \
    "$plugins/.jvm/plugin-set.jar" "$plugins/.jvm/plugin-set.json" jvm "$kit" "${jars[@]}" >/dev/null \
    || hk_die 3 "could not describe the plugins in $plugins"
  cp="$cp:$plugins/.jvm/plugin-set.jar"
  for j in "${jars[@]}"; do cp="$cp:$j"; done
fi

# the bundle's runtime (Postgres, converter) is found from HISTMERSION_HOME: the process is `java`, not
# a binary in <install>/bin, so it cannot be found from the executable's location as natively
export HISTMERSION_HOME="$install"
export HISTMERSION_RUNTIME="${HISTMERSION_RUNTIME:-true}"
# argv[0] = this launcher's path, so `pgrep -f <install>/bin/histmersion-server` finds the process
# shellcheck disable=SC2086
exec -a "$0" "$jh/bin/java" ${HISTMERSION_JVM_OPTS:-} -cp "$cp" "$(cat "$kit/main-class")" "$@"
