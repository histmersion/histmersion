# Shared by the relink kit's scripts (histmersion-relink, histmersion-server-jvm, histmersion-toolchain,
# histmersion-signing) and their tests. Sourced, never run. Bash 3.2 (macOS's /bin/bash).
#
# In a bundle it is <install>/libexec/kit-common.sh; in the repo, scripts/bundle/kit-common.sh.
# Every location can be moved by the environment, which is how the tests run against temporary
# installs and never against ~/Applications/Histmersion or the user's data directory:
#
#   HISTMERSION_HOME         the install (bin/, kit/, libexec/; default: the directory above bin/)
#   HISTMERSION_DATA_DIR     the data directory (plugins/, toolchain/, signing/, logs/)
#   HISTMERSION_SERVICE      launchd | systemd | none   (default: launchd on macOS, systemd on Linux)
#   HISTMERSION_SERVICE_LABEL / HISTMERSION_SERVICE_UNIT   the service's name
#   HISTMERSION_SERVER_URL   where the service answers (default http://127.0.0.1:${HISTMERSION_PORT:-8000})

hk_os() { case "$(uname -s)" in Darwin) echo macos ;; *) echo linux ;; esac; }

hk_die() { # <exit code> <message…>
  local rc="$1"; shift
  echo "error: $*" >&2
  exit "$rc"
}

hk_data_dir() {
  if [ -n "${HISTMERSION_DATA_DIR:-}" ]; then printf '%s\n' "$HISTMERSION_DATA_DIR"
  elif [ "$(hk_os)" = macos ]; then printf '%s\n' "$HOME/Library/Application Support/Histmersion"
  else printf '%s\n' "${XDG_DATA_HOME:-$HOME/.local/share}/histmersion"; fi
}

hk_plugins_dir() { printf '%s\n' "$(hk_data_dir)/plugins"; }

# The jars of the plugins directory, one path per line, sorted (none: nothing printed).
hk_plugin_jars() { # <plugins dir>
  local d="$1" f
  [ -d "$d" ] || return 0
  for f in "$d"/*.jar; do [ -f "$f" ] && printf '%s\n' "$f"; done | LC_ALL=C sort
  return 0
}

hk_sha256() { # <file> → hex
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  else sha256sum "$1" | awk '{print $1}'; fi
}

# `shasum -c` / `sha256sum -c` over a SHA256SUMS file, run in its directory.
hk_sha256_check() { # <dir> <sums file name>
  if command -v shasum >/dev/null 2>&1; then (cd "$1" && shasum -a 256 -c --quiet "$2")
  else (cd "$1" && sha256sum -c --quiet "$2"); fi
}

# The JDK version a home declares (its `release` file): 25.0.1
hk_jdk_version() { # <java home>
  sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$1/release" 2>/dev/null | head -1
}

# The GraalVM homes worth trying, best first: the explicit one, the one `histmersion-toolchain install`
# provisioned, GRAALVM_HOME / JAVA_HOME, sdkman's candidate of the pinned version.
hk_graalvm_candidates() { # <version>
  local v="$1" d
  [ -n "${HISTMERSION_GRAALVM_HOME:-}" ] && printf '%s\n' "$HISTMERSION_GRAALVM_HOME"
  d="$(hk_data_dir)/toolchain/graalvm-ce-$v"
  printf '%s\n' "$d/Contents/Home" "$d"
  [ -n "${GRAALVM_HOME:-}" ] && printf '%s\n' "$GRAALVM_HOME"
  [ -n "${JAVA_HOME:-}" ] && printf '%s\n' "$JAVA_HOME"
  printf '%s\n' "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/$v-graalce"
}

# A GraalVM home of exactly <version> with native-image, else nothing (exit 1).
hk_find_graalvm() { # <version>
  local h
  while IFS= read -r h; do
    if [ -n "$h" ] && [ -x "$h/bin/native-image" ] && [ -x "$h/bin/java" ] && [ "$(hk_jdk_version "$h")" = "$1" ]; then
      printf '%s\n' "$h"; return 0
    fi
  done < <(hk_graalvm_candidates "$1")
  return 1
}

# A JDK of the pin's MAJOR version for the JVM-side tools and JVM mode: HISTMERSION_JAVA_HOME, else a
# GraalVM home as above (native-image not needed), else nothing (exit 1).
hk_find_java() { # <version>
  local want="${1%%.*}" h v
  while IFS= read -r h; do
    [ -n "$h" ] || continue
    v="$(hk_jdk_version "$h")"
    if [ -x "$h/bin/java" ] && [ "${v%%.*}" = "$want" ]; then printf '%s\n' "$h"; return 0; fi
  done < <(printf '%s\n' "${HISTMERSION_JAVA_HOME:-}"; hk_graalvm_candidates "$1")
  return 1
}

# The machine's memory in bytes: total on macOS (it has no "available" figure worth trusting),
# MemAvailable on Linux. HISTMERSION_RELINK_MEMORY_BYTES overrides (tests).
hk_mem_bytes() {
  if [ -n "${HISTMERSION_RELINK_MEMORY_BYTES:-}" ]; then echo "$HISTMERSION_RELINK_MEMORY_BYTES"; return 0; fi
  if [ "$(hk_os)" = macos ]; then sysctl -n hw.memsize 2>/dev/null || echo 0
  else awk '/^MemAvailable:/ {print $2 * 1024}' /proc/meminfo 2>/dev/null || echo 0; fi
}

hk_free_kb() { # <dir>
  df -Pk "$1" 2>/dev/null | awk 'NR == 2 {print $4}'
}

# A C toolchain native-image can link with: Xcode's clang on macOS, cc on Linux.
hk_cc() {
  if [ "$(hk_os)" = macos ]; then xcrun --find clang 2>/dev/null
  else command -v cc 2>/dev/null || command -v gcc 2>/dev/null; fi
}

# One relink (or toolchain change) at a time: a directory made atomically, holding the owner's pid.
# A lock whose owner is gone is taken over.
hk_lock() { # <lock dir>
  local l="$1" pid
  mkdir -p "$(dirname "$l")"
  if mkdir "$l" 2>/dev/null; then echo $$ > "$l/pid"; return 0; fi
  pid="$(cat "$l/pid" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then return 1; fi
  rm -rf "$l" && mkdir "$l" 2>/dev/null && echo $$ > "$l/pid"
}
hk_unlock() { rm -rf "$1"; }

hk_service_kind() {
  if [ -n "${HISTMERSION_SERVICE:-}" ]; then echo "$HISTMERSION_SERVICE"
  elif [ "$(hk_os)" = macos ]; then echo launchd
  else echo systemd; fi
}

# Restart the installed service so it runs the binary now in bin/.
hk_service_restart() {
  case "$(hk_service_kind)" in
    launchd) launchctl kickstart -k "gui/$(id -u)/${HISTMERSION_SERVICE_LABEL:-com.histmersion.server}" ;;
    systemd) systemctl --user restart "${HISTMERSION_SERVICE_UNIT:-histmersion.service}" ;;
    none) echo "  (no service to restart: HISTMERSION_SERVICE=none)" ;;
    *) echo "unknown HISTMERSION_SERVICE '$(hk_service_kind)'" >&2; return 1 ;;
  esac
}

hk_server_url() { printf '%s\n' "${HISTMERSION_SERVER_URL:-http://127.0.0.1:${HISTMERSION_PORT:-8000}}"; }

# Wait up to <seconds> for /api/health to answer.
hk_wait_healthy() { # <seconds>
  local url i
  url="$(hk_server_url)"
  i=0
  while [ "$i" -lt "$1" ]; do
    curl -fsS "$url/api/health" >/dev/null 2>&1 && return 0
    sleep 1; i=$((i + 1))
  done
  return 1
}

# The plugin-set digest the running server reports (/api/about), the empty set's when it reports none.
hk_running_digest() {
  local about
  about="$(curl -fsS "$(hk_server_url)/api/about" 2>/dev/null)" || return 1
  printf '%s' "$about" | tr -d ' \n' | sed -n 's/.*"set":{[^}]*"digest":"\([0-9a-f]*\)".*/\1/p' | head -1 | grep . \
    || echo "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}

# ── the build record (bin/histmersion-server.build) ───────────────────────────────────────────────
# Beside a binary linked ON A MACHINE (a relink, or the bundle's own build by scripts/bundle/server.sh):
# what it was linked from, so an install can tell — cheaply, without a JVM — that linking again would
# give the same binary. Key=value lines:
#   mode     native | jvm
#   kit      hk_kit_fingerprint of the kit it was linked from (every kit file's SHA-256, via SHA256SUMS)
#   plugins  hk_plugins_fingerprint of the plugin jars linked in
#   machine  hk_machine_id of the machine that linked it
#   origin   relink | bundle
# Nothing in it is secret or personal: the machine id is a hash.

hk_sha256_text() { # stdin → hex
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 | awk '{print $1}'
  else sha256sum | awk '{print $1}'; fi
}

hk_kit_fingerprint() { # <kit dir> → the SHA-256 of its SHA256SUMS (every kit file's sum); nothing without one
  if [ -f "$1/SHA256SUMS" ]; then hk_sha256 "$1/SHA256SUMS"; fi
  return 0
}

# The plugin jars of <plugins dir> as one hash: "<sha256>  <file name>" per jar, sorted by name.
# No jars (or no directory) → the empty text's hash.
hk_plugins_fingerprint() { # <plugins dir>
  local j
  hk_plugin_jars "$1" | while IFS= read -r j; do printf '%s  %s\n' "$(hk_sha256 "$j")" "$(basename "$j")"; done \
    | hk_sha256_text
}

# This machine, as a hash: the hardware UUID on macOS, the machine-id on Linux (HISTMERSION_MACHINE_ID
# overrides, for the tests). Nothing when none can be read — and a record without one never matches.
hk_machine_id() {
  local raw=""
  if [ -n "${HISTMERSION_MACHINE_ID:-}" ]; then raw="$HISTMERSION_MACHINE_ID"
  elif [ "$(hk_os)" = macos ]; then
    raw="$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | awk -F'"' '/IOPlatformUUID/ {print $4; exit}')"
  else raw="$(cat /etc/machine-id 2>/dev/null || cat /var/lib/dbus/machine-id 2>/dev/null || true)"; fi
  if [ -n "$raw" ]; then printf 'histmersion:%s' "$raw" | hk_sha256_text; fi
  return 0
}

hk_write_build_record() { # <file> <mode> <kit dir> <plugins dir> <origin>
  { echo "mode=$2"
    echo "kit=$(hk_kit_fingerprint "$3")"
    echo "plugins=$(hk_plugins_fingerprint "$4")"
    echo "machine=$(hk_machine_id)"
    echo "origin=$5"
    echo "built=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"; } > "$1"
}

hk_record_value() { # <record file> <key>
  sed -n "s/^$2=//p" "$1" 2>/dev/null | head -1
}

# 0 when <binary>'s build record (<binary>.build) says it is a NATIVE binary linked on THIS machine from
# <kit dir> with exactly the jars of <plugins dir>: linking again would give the same binary. Else 1,
# and why on stdout.
hk_build_current() { # <binary> <kit dir> <plugins dir>
  local rec="$1.build" m k
  [ -x "$1" ] || { echo "no binary"; return 1; }
  [ -f "$rec" ] || { echo "no build record"; return 1; }
  [ "$(hk_record_value "$rec" mode)" = native ] || { echo "not a native binary"; return 1; }
  m="$(hk_machine_id)"
  { [ -n "$m" ] && [ "$(hk_record_value "$rec" machine)" = "$m" ]; } || { echo "linked on another machine"; return 1; }
  k="$(hk_kit_fingerprint "$2")"
  { [ -n "$k" ] && [ "$(hk_record_value "$rec" kit)" = "$k" ]; } || { echo "linked from another kit"; return 1; }
  [ "$(hk_record_value "$rec" plugins)" = "$(hk_plugins_fingerprint "$3")" ] || { echo "linked with other plugins"; return 1; }
  return 0
}

# The GPG key (fingerprint) Histmersion's provenance is signed with, when the user set one up
# (`histmersion-signing use-gpg`); nothing when signing is off — the default.
hk_gpg_key() {
  local f; f="$(hk_data_dir)/signing/gpg-key"
  [ -s "$f" ] && head -1 "$f"
  return 0
}
