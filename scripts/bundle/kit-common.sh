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

# The POM published beside each plugin jar (<name>.jar → <name>.pom), one path per line, in the jars'
# order; a jar without one has no dependencies of its own.
hk_plugin_poms() { # <plugins dir>
  local j
  hk_plugin_jars "$1" | while IFS= read -r j; do
    if [ -f "${j%.jar}.pom" ]; then printf '%s\n' "${j%.jar}.pom"; fi
  done
  return 0
}

# The plugins' dependency jars (<plugins dir>/lib, as its classpath file lists them), one path per line.
hk_plugin_deps() { # <plugins dir>
  local n
  [ -f "$1/lib/classpath" ] || return 0
  while IFS= read -r n; do if [ -n "$n" ]; then printf '%s\n' "$1/lib/$n"; fi; done < "$1/lib/classpath"
  return 0
}

# `<sha256>  <file>` of every plugin jar and its POM, sorted by name: what lib/ was resolved from
# (the resolver writes the same lines to lib/inputs).
hk_plugin_inputs() { # <plugins dir>
  local f
  { hk_plugin_jars "$1"; hk_plugin_poms "$1"; } | while IFS= read -r f; do
    printf '%s  %s\n' "$(hk_sha256 "$f")" "$(basename "$f")"
  done | LC_ALL=C sort -k2
}

# 0 when <plugins dir>/lib was resolved from exactly the plugin jars and POMs there now.
hk_plugin_deps_current() { # <plugins dir>
  [ -f "$1/lib/inputs" ] && [ "$(hk_plugin_inputs "$1")" = "$(cat "$1/lib/inputs")" ]
}

# Resolve the plugins' dependencies into <lib> with the kit's plugin resolver (kit/tools/resolver, run
# beside kit/lib): what the server ships is not installed again, a plugin that needs a newer version of
# it is refused (exit 2). <args>: --repo <url|dir>…, --check, then the plugin jars.
hk_resolve() { # <java home> <install> <lib dir> <args…>
  local jh="$1" install="$2" lib="$3"; shift 3
  [ -d "$install/kit/tools/resolver" ] || { echo "the kit has no plugin resolver (kit/tools/resolver) — reinstall the bundle" >&2; return 3; }
  # coursier's jar carries a Scala compiled before 3.8, whose lazy vals use sun.misc.Unsafe: without
  # the flag the JDK prints its deprecation warning on every run, between the resolver's own lines
  "$jh/bin/java" --sun-misc-unsafe-memory-access=allow -cp "$install/kit/lib/*:$install/kit/tools/resolver/*" \
    com.histmersion.pluginresolver.PluginResolver resolve \
    --kit "$install/kit" --lib "$lib" --histmersion-repo "$install/maven" "$@"
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

# The JDK version a home declares (its `release` file): 25.0.4.1.1 for GraalVM CE 25.4
hk_jdk_version() { # <java home>
  sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$1/release" 2>/dev/null | head -1
}

# The pinned GraalVM version: the first version of a graalvm-pins.txt (the JDK version its release
# declares). In a bundle, kit/graalvm-version holds the same value, written from it at build time.
hk_pinned_version() { # <pins file>
  awk '!/^#/ && NF {print $1; exit}' "$1" 2>/dev/null
}

# The GraalVM homes worth trying, best first: the explicit one, the one `histmersion-toolchain install`
# provisioned, GRAALVM_HOME / JAVA_HOME, then sdkman's GraalVM CE candidates (named by GraalVM's release,
# 25.4.4.1+1-graalce for JDK 25.0.4.1.1, so the caller matches each one's `release` file).
hk_graalvm_candidates() { # <version>
  local v="$1" d
  [ -n "${HISTMERSION_GRAALVM_HOME:-}" ] && printf '%s\n' "$HISTMERSION_GRAALVM_HOME"
  d="$(hk_data_dir)/toolchain/graalvm-ce-$v"
  printf '%s\n' "$d/Contents/Home" "$d"
  [ -n "${GRAALVM_HOME:-}" ] && printf '%s\n' "$GRAALVM_HOME"
  [ -n "${JAVA_HOME:-}" ] && printf '%s\n' "$JAVA_HOME"
  for d in "${SDKMAN_DIR:-$HOME/.sdkman}"/candidates/java/*-graalce; do [ -d "$d" ] && printf '%s\n' "$d"; done
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

# The plugin jars of <plugins dir> as one hash: "<sha256>  <file name>" per jar, sorted by name, then
# the same for the POMs beside them (which name the plugins' dependencies), then for the dependency jars
# of lib/ ("lib/<name>"): everything a relink links. No jars (or no directory) → the empty text's hash.
# `histmersion plugin status` compares the same files with the binary's plugin-set record.
hk_plugins_fingerprint() { # <plugins dir>
  local j
  { { hk_plugin_jars "$1"; hk_plugin_poms "$1"; } | while IFS= read -r j; do printf '%s  %s\n' "$(hk_sha256 "$j")" "$(basename "$j")"; done
    hk_plugin_deps "$1" | while IFS= read -r j; do
      if [ -f "$j" ]; then printf '%s  lib/%s\n' "$(hk_sha256 "$j")" "$(basename "$j")"; fi
    done; } | hk_sha256_text
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

# ── GPG provenance of the server binary at each start (libexec/histmersion-server-verified) ─────────
# With GPG provenance on, the service definition (LaunchAgent / systemd unit) starts the server through
# libexec/histmersion-server-verified, which checks bin/histmersion-server against its signed checksum
# (bin/histmersion-server.sha256 + .asc) and refuses to start it otherwise. With provenance off the
# service runs bin/histmersion-server directly, as it always did.

# The gpg program: HISTMERSION_GPG, else the one `histmersion-signing use-gpg` recorded (a LaunchAgent's
# PATH is /usr/bin:/bin:/usr/sbin:/sbin and has no Homebrew or MacGPG2), else gpg on PATH, else the
# usual install locations. Nothing (exit 1) when none is found.
hk_gpg_program() {
  local f p
  if [ -n "${HISTMERSION_GPG:-}" ]; then printf '%s\n' "$HISTMERSION_GPG"; return 0; fi
  f="$(hk_data_dir)/signing/gpg-program"
  if [ -s "$f" ]; then p="$(head -1 "$f")"; [ -x "$p" ] && { printf '%s\n' "$p"; return 0; }; fi
  p="$(command -v gpg 2>/dev/null || true)"
  [ -n "$p" ] && { printf '%s\n' "$p"; return 0; }
  for p in /opt/homebrew/bin/gpg /usr/local/bin/gpg /usr/local/MacGPG2/bin/gpg /usr/bin/gpg; do
    [ -x "$p" ] && { printf '%s\n' "$p"; return 0; }
  done
  return 1
}

# The GnuPG home the key lives in: GNUPGHOME, else the one `use-gpg` recorded (only when it was not the
# default), else nothing (gpg's default, ~/.gnupg).
hk_gpg_home() {
  local f; f="$(hk_data_dir)/signing/gnupg-home"
  if [ -n "${GNUPGHOME:-}" ]; then printf '%s\n' "$GNUPGHOME"
  elif [ -s "$f" ]; then head -1 "$f"; fi
  return 0
}

# Verify a detached signature with the configured gpg; prints "<signing key> <primary key>" fingerprints
# (VALIDSIG's first and last fields; the same twice for a key without subkeys). `use-gpg` stores the
# primary's; a plugin's pinned signer is the signing key's (verify-file). 1 when the signature is not
# good or the key is revoked; why on stderr.
hk_gpg_verify() { # <file> <file.asc>
  local g h status fpr
  g="$(hk_gpg_program)" || { echo "no gpg program found (HISTMERSION_GPG, or histmersion-signing use-gpg records one)" >&2; return 1; }
  h="$(hk_gpg_home)"
  status="$( { [ -z "$h" ] || export GNUPGHOME="$h"; "$g" --batch --no-tty --status-fd 1 --verify "$2" "$1"; } 2>/dev/null || true)"
  if printf '%s\n' "$status" | grep -q '^\[GNUPG:\] REVKEYSIG'; then echo "the signing key is revoked" >&2; return 1; fi
  fpr="$(printf '%s\n' "$status" | awk '/^\[GNUPG:\] VALIDSIG/ {print $3, ($12 != "" ? $12 : $3); exit}')"
  if [ -n "$fpr" ]; then printf '%s\n' "$fpr"; return 0; fi
  fpr="$(printf '%s\n' "$status" | awk '/^\[GNUPG:\] (NO_PUBKEY|ERRSIG)/ {print $3; exit}')"
  if [ -n "$fpr" ]; then echo "the signer's public key $fpr is not in the keyring${h:+ ($h)}" >&2
  else echo "$(basename "$2") is not a valid signature of $(basename "$1")" >&2; fi
  return 1
}

# 0 when <binary> is what its checksum file says and that file carries a good signature by <key>; one
# line on stdout either way (what was verified, or why not).
hk_verify_binary() { # <binary> <sha256 file> <its .asc> <key fingerprint>
  local bin="$1" sums="$2" asc="$3" key="$4" signer want have err
  [ -x "$bin" ] || { echo "no binary at $bin"; return 1; }
  [ -f "$sums" ] && [ -f "$asc" ] || { echo "no signed checksum for $(basename "$bin") ($(basename "$asc") missing) — histmersion-signing sign-binary signs the installed one"; return 1; }
  err="$(mktemp "${TMPDIR:-/tmp}/hk-verify.XXXXXX")"
  signer="$(hk_gpg_verify "$sums" "$asc" 2>"$err")" || { echo "$(basename "$asc"): $(cat "$err")"; rm -f "$err"; return 1; }
  rm -f "$err"
  case " $signer " in *" $key "*) ;; *) echo "$(basename "$sums") is signed by ${signer##* }, not by the configured key $key"; return 1 ;; esac
  signer="${signer##* }"
  want="$(awk 'NR == 1 {print $1}' "$sums")"
  have="$(hk_sha256 "$bin")"
  [ -n "$want" ] && [ "$want" = "$have" ] || { echo "$(basename "$bin") has SHA-256 $have, its signed checksum says ${want:-nothing} — the binary changed since it was signed"; return 1; }
  echo "verified $(basename "$bin") (SHA-256 ${have:0:16}…) signed by $signer"
}

# What the service definition should start for <install>: the verifying launcher when GPG provenance is
# on and the bundle has one, else the server binary itself.
hk_service_program() { # <install>
  if [ -n "$(hk_gpg_key)" ] && [ -x "$1/libexec/histmersion-server-verified" ]; then
    printf '%s\n' "$1/libexec/histmersion-server-verified"
  else printf '%s\n' "$1/bin/histmersion-server"; fi
}

# The installed service definition's file (HISTMERSION_SERVICE_FILE overrides; the tests use it).
hk_service_file() {
  if [ -n "${HISTMERSION_SERVICE_FILE:-}" ]; then printf '%s\n' "$HISTMERSION_SERVICE_FILE"; return 0; fi
  case "$(hk_service_kind)" in
    launchd) printf '%s\n' "$HOME/Library/LaunchAgents/${HISTMERSION_SERVICE_LABEL:-com.histmersion.server}.plist" ;;
    systemd) printf '%s\n' "${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/${HISTMERSION_SERVICE_UNIT:-histmersion.service}" ;;
  esac
  return 0
}

# The program the installed service definition starts; nothing when there is none.
hk_service_current_program() {
  local f; f="$(hk_service_file)"
  [ -n "$f" ] && [ -f "$f" ] || return 0
  case "$(hk_service_kind)" in
    launchd) plutil -extract ProgramArguments.0 raw -o - "$f" 2>/dev/null || true ;;
    systemd) sed -n 's/^ExecStart=//p' "$f" | head -1 ;;
  esac
  return 0
}

# Point the installed service of <install> at <program> (hk_service_program's choice) and, when it is
# running and <restart> is non-empty, reload it so the change applies now. A definition that starts
# another install's server is left alone. Says what it did.
hk_service_repoint() { # <install> <program> <restart>
  local install="$1" prog="$2" restart="$3" f cur uid label i
  f="$(hk_service_file)"
  if [ "$(hk_service_kind)" = none ] || [ -z "$f" ] || [ ! -f "$f" ]; then
    echo "  service: none installed — nothing to point at $(basename "$prog")"; return 0
  fi
  cur="$(hk_service_current_program)"
  case "$cur" in
    "$install/bin/histmersion-server"|"$install/libexec/histmersion-server-verified") ;;
    *) echo "  service: $f starts '${cur:-?}', not this install's server — left as it is"; return 0 ;;
  esac
  if [ "$cur" = "$prog" ]; then echo "  service: already starts $prog"; return 0; fi
  case "$(hk_service_kind)" in
    launchd)
      plutil -replace ProgramArguments.0 -string "$prog" "$f"
      plutil -lint -s "$f"
      echo "  service: $(basename "$f") now starts $prog"
      label="${HISTMERSION_SERVICE_LABEL:-com.histmersion.server}"; uid="$(id -u)"
      if [ -n "$restart" ] && launchctl print "gui/$uid/$label" >/dev/null 2>&1; then
        echo "  reloading the LaunchAgent so it applies now (the server restarts)"
        launchctl bootout "gui/$uid/$label" >/dev/null 2>&1 || true
        i=0
        until launchctl bootstrap "gui/$uid" "$f" >/dev/null 2>&1; do
          i=$((i + 1)); [ "$i" -lt 60 ] || { echo "  could not load $f again — launchctl bootstrap gui/$uid \"$f\"" >&2; return 1; }
          sleep 1
        done
      elif [ -z "$restart" ]; then echo "  (not reloaded: applies at the next login or launchctl bootout/bootstrap)"; fi
      ;;
    systemd)
      sed "s|^ExecStart=.*|ExecStart=$prog|" "$f" > "$f.tmp" && mv -f "$f.tmp" "$f"
      echo "  service: $(basename "$f") now starts $prog"
      systemctl --user daemon-reload
      if [ -n "$restart" ] && systemctl --user is-active --quiet "${HISTMERSION_SERVICE_UNIT:-histmersion.service}"; then
        echo "  restarting the unit so it applies now"
        systemctl --user restart "${HISTMERSION_SERVICE_UNIT:-histmersion.service}"
      elif [ -z "$restart" ]; then echo "  (not restarted: applies at the next start)"; fi
      ;;
  esac
}
