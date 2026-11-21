#!/usr/bin/env bash
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
  if ! hk_plugin_deps_current "$plugins"; then
    hk_resolve "$jh" "$install" "$plugins/lib" "${jars[@]}" >&2 \
      || hk_die 3 "the plugins' dependencies could not be resolved (above) — histmersion plugin rebuild --jvm says why"
  fi
  mkdir -p "$plugins/.jvm"
  "$jh/bin/java" -cp "$kit/lib/*" com.histmersion.server.plugins.pluginTool plugin-set --lib "$plugins/lib" \
    "$plugins/.jvm/plugin-set.jar" "$plugins/.jvm/plugin-set.json" jvm "$kit" "${jars[@]}" >/dev/null \
    || hk_die 3 "could not describe the plugins in $plugins"
  cp="$cp:$plugins/.jvm/plugin-set.jar"
  for j in "${jars[@]}"; do cp="$cp:$j"; done
  while IFS= read -r d; do cp="$cp:$d"; done < <(hk_plugin_deps "$plugins")
fi

export HISTMERSION_HOME="$install"
export HISTMERSION_RUNTIME="${HISTMERSION_RUNTIME:-true}"
# shellcheck disable=SC2086
exec -a "$0" "$jh/bin/java" ${HISTMERSION_JVM_OPTS:-} -cp "$cp" "$(cat "$kit/main-class")" "$@"
