#!/usr/bin/env bash
# scripts/dev/pinned-jdk.sh against fake JDK homes (a `release` file and stub binaries; no real JDK).
set -euo pipefail
here="$(cd "$(dirname "$0")/../../.." && pwd)"
check="$here/scripts/dev/pinned-jdk.sh"
pinned="$(awk '!/^#/ && NF {print $1; exit}' "$here/scripts/bundle/graalvm-pins.txt")"
t="$(mktemp -d)"; trap 'rm -rf "$t"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
fake_jdk() { # <dir> <JAVA_VERSION> <with native-image: yes|no>
  mkdir -p "$1/bin"; printf 'JAVA_VERSION="%s"\n' "$2" > "$1/release"
  printf '#!/bin/sh\necho "    java.home = %s" >&2\n' "$1" > "$1/bin/java"; chmod +x "$1/bin/java"
  if [ "$3" = yes ]; then printf '#!/bin/sh\n' > "$1/bin/native-image"; chmod +x "$1/bin/native-image"; fi
}
fake_jdk "$t/pinned" "$pinned" yes
fake_jdk "$t/other" "25.0.1" yes
fake_jdk "$t/no-ni" "$pinned" no

[ "$(JAVA_HOME="$t/pinned" "$check")" = "$t/pinned" ] || fail "the pinned JAVA_HOME is printed"
[ "$(env -u JAVA_HOME PATH="$t/pinned/bin:/usr/bin:/bin" "$check")" = "$t/pinned" ] \
  || fail "without JAVA_HOME, the java on PATH is resolved to its home"
JAVA_HOME="$t/other" "$check" >/dev/null 2>"$t/err" && fail "another version is refused"
grep -q "sdk env install" "$t/err" || fail "the refusal names sdk env install"
grep -q "25.0.1" "$t/err" || fail "the refusal names the active version"
JAVA_HOME="$t/no-ni" "$check" >/dev/null 2>&1 && fail "the pinned version without native-image is refused"
JAVA_HOME="$t/missing" "$check" >/dev/null 2>&1 && fail "a JAVA_HOME that does not exist is refused"
echo "pinned-jdk-test: ok"
