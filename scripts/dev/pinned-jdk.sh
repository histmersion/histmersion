#!/usr/bin/env bash
# Prints the home of the active JDK (JAVA_HOME, else the `java` on PATH) when it is the GraalVM CE that
# scripts/bundle/graalvm-pins.txt pins, with native-image; otherwise says how to activate it and exits 1.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=../bundle/kit-common.sh
. "$root/scripts/bundle/kit-common.sh"
pinned="$(hk_pinned_version "$root/scripts/bundle/graalvm-pins.txt")"
home="${JAVA_HOME:-}"
if [ -z "$home" ] && command -v java >/dev/null 2>&1; then
  home="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | head -1)"
fi
active="$( [ -n "$home" ] && hk_jdk_version "$home" || true)"
if [ "$active" != "$pinned" ] || [ ! -x "$home/bin/native-image" ]; then
  echo "the active JDK is ${active:-none}${home:+ ($home)}, not GraalVM CE $pinned with native-image" \
    "(scripts/bundle/graalvm-pins.txt) — run 'sdk env install && sdk env' in the checkout" >&2
  exit 1
fi
printf '%s\n' "$home"
