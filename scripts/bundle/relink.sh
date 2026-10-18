#!/usr/bin/env bash
# bin/histmersion-relink — link the server again with the user's plugins (docs/research-plugins.md §7.4).
#
#   histmersion-relink [--no-restart] [--unsigned] [--ignore-memory]   relink, swap, restart, check health
#   histmersion-relink --check                                         the pre-flight alone (seconds)
#   histmersion-relink --jvm [--no-restart]                            switch to JVM mode instead (no build)
#   histmersion-relink --rollback [--no-restart]                       back to the previous binary
#   histmersion-relink --describe <jar>…                               what a jar provides, as JSON
#
# A relink re-runs GraalVM native-image over the KIT this bundle ships (kit/: the server's Sloth-
# patched jars in class-path order, its native-image arguments, kit.json + SHA256SUMS) plus the jars of
# the plugins directory (<data>/plugins/*.jar). No sbt, no Scala compile. Every step can stop it, and
# nothing changes in bin/ until the new binary passed its own --self-check:
#
#   1. lock          <data>/plugins/.relink.lock — one relink at a time
#   2. kit           every kit file matches SHA256SUMS
#   3. toolchain     GraalVM CE of the kit's version (histmersion-toolchain install), a C toolchain
#                    (Xcode CLT / cc), memory (HISTMERSION_RELINK_MIN_MEMORY_GB, default 12) and disk
#   4. provenance    with a GPG key set up (histmersion-signing use-gpg): the plugins directory matches
#                    its signed manifest — else refused unless --unsigned
#   5. pre-flight    on the JVM: a provider in every jar, its SPI, no id or class twice; then the
#                    server's own --self-check with the plugins on the class path (the user's config)
#   6. Sloth         the plugin jars only (the kit's are patched already)
#   7. native-image  → .relink/histmersion-server.new  (log: <data>/logs/relink.log)
#   8. self-check    the NEW binary's --self-check: config and plugins load, the plugin-set digest is
#                    the directory's, no new plugin failed — never started as a server
#   9. sign          codesign with the identity (HISTMERSION_CODESIGN_IDENTITY, "-" = ad hoc) on macOS;
#                    a detached .asc of its SHA-256 with the GPG key, when set up
#  10. swap          bin/histmersion-server → .prev, the new one in its place (one rename each)
#  11. restart       the LaunchAgent / systemd unit (not with --no-restart); /api/health within 120 s
#                    and /api/about's plugin digest the expected one — else the previous binary is put
#                    back and the service restarted again
#
# Exit codes: 0 done · 1 usage · 2 refused before anything was built (kit, toolchain, provenance,
# pre-flight) · 3 the build or the new binary's self-check failed (nothing changed) · 4 the new binary
# did not come up and the previous one was put back.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
common="$here/../libexec/kit-common.sh"; [ -f "$common" ] || common="$here/kit-common.sh"
# shellcheck source=kit-common.sh
. "$common"

install="${HISTMERSION_HOME:-$(cd "$here/.." && pwd)}"
kit="$install/kit"
bin="$install/bin"
server="$bin/histmersion-server"
data="$(hk_data_dir)"
plugins="$(hk_plugins_dir)"
signing="$here/histmersion-signing"; [ -x "$signing" ] || signing="$here/signing.sh"
mode=relink restart=1 unsigned="" ignore_memory=""
describe=()
while [ $# -gt 0 ]; do
  case "$1" in
    --no-restart) restart="" ;;
    --unsigned) unsigned=1 ;;
    --ignore-memory) ignore_memory=1 ;;
    --check) mode=check ;;
    --jvm) mode=jvm ;;
    --rollback) mode=rollback ;;
    --describe) mode=describe; shift; describe=("$@"); break ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "histmersion-relink: unknown argument '$1' (--help)" >&2; exit 1 ;;
  esac
  shift
done

[ -f "$kit/kit.json" ] && [ -f "$kit/classpath" ] || hk_die 2 "no relink kit at $kit — this bundle predates plugins, or it is not a Histmersion bundle"
graalvm_version="$(cat "$kit/graalvm-version")"
main_class="$(cat "$kit/main-class")"
kit_cp() { # the kit's class path in its order
  local n out=""
  while IFS= read -r n; do [ -n "$n" ] && out="$out${out:+:}$kit/lib/$n"; done < "$kit/classpath"
  printf '%s' "$out"
}
jh=""
need_java() {
  [ -n "$jh" ] && return 0
  jh="$(hk_find_java "$graalvm_version")" \
    || hk_die 2 "no JDK ${graalvm_version%%.*} found — run histmersion-toolchain install (or set HISTMERSION_JAVA_HOME)"
}
tool() { # PluginTool <args…> on the kit's class path
  "$jh/bin/java" -cp "$kit/lib/*" com.histmersion.server.plugins.PluginTool "$@"
}
jars=()
while IFS= read -r j; do jars+=("$j"); done < <(hk_plugin_jars "$plugins")

if [ "$mode" = describe ]; then
  [ "${#describe[@]}" -gt 0 ] || hk_die 1 "--describe needs a jar"
  need_java
  tool describe "${describe[@]}"
  exit $?
fi

mkdir -p "$plugins" "$data/logs"
lock="$plugins/.relink.lock"
hk_lock "$lock" || hk_die 2 "another relink holds $lock (pid $(cat "$lock/pid" 2>/dev/null))"
work="$install/.relink"
cleanup() { rm -rf "$work"; hk_unlock "$lock"; }
trap cleanup EXIT
rm -rf "$work" && mkdir -p "$work/plugins"
log="$data/logs/relink.log"
step() { echo "→ $*"; echo "$(date '+%F %T') $*" >> "$log"; }

# ── swap and roll back ────────────────────────────────────────────────────────────────────────────
expected_digest=""
put_back() { # the .prev binary (and its records) back in place
  local f
  for f in histmersion-server histmersion-server.plugin-set.json histmersion-server.sha256 histmersion-server.sha256.asc; do
    if [ -e "$bin/$f.prev" ]; then mv -f "$bin/$f.prev" "$bin/$f"
    elif [ "$f" != histmersion-server ]; then rm -f "$bin/$f"; fi
  done
}
swap_in() { # <new binary> <its plugin-set record> — the current one becomes .prev
  local f
  for f in histmersion-server histmersion-server.plugin-set.json histmersion-server.sha256 histmersion-server.sha256.asc; do
    rm -f "$bin/$f.prev"
    [ -e "$bin/$f" ] && mv -f "$bin/$f" "$bin/$f.prev"
  done
  mv -f "$1" "$server"
  cp "$2" "$bin/histmersion-server.plugin-set.json"
  [ -f "$work/histmersion-server.sha256" ] && cp "$work/histmersion-server.sha256" "$bin/"
  [ -f "$work/histmersion-server.sha256.asc" ] && cp "$work/histmersion-server.sha256.asc" "$bin/"
  return 0
}
restart_and_check() { # 0 = healthy with the expected digest
  [ -n "$restart" ] || { echo "  not restarted (--no-restart): the service runs the old binary until it restarts"; return 0; }
  [ "$(hk_service_kind)" = none ] && { echo "  no service (HISTMERSION_SERVICE=none)"; return 0; }
  step "restart the service and wait for /api/health (up to ${HISTMERSION_RELINK_HEALTH_SECONDS:-120} s)"
  hk_service_restart || return 1
  hk_wait_healthy "${HISTMERSION_RELINK_HEALTH_SECONDS:-120}" || { echo "  /api/health did not answer" >&2; return 1; }
  if [ -n "$expected_digest" ]; then
    local got; got="$(hk_running_digest || true)"
    [ "$got" = "$expected_digest" ] || { echo "  the running server reports plugin set ${got:-none}, expected $expected_digest" >&2; return 1; }
  fi
  echo "  healthy${expected_digest:+, plugin set ${expected_digest:0:12}…}"
}

if [ "$mode" = rollback ]; then
  [ -e "$server.prev" ] || hk_die 2 "no previous binary ($server.prev) to roll back to"
  step "roll back to the previous binary"
  for f in histmersion-server histmersion-server.plugin-set.json histmersion-server.sha256 histmersion-server.sha256.asc; do
    if [ -e "$bin/$f.prev" ]; then
      [ -e "$bin/$f" ] && mv -f "$bin/$f" "$work/$f.swap"
      mv -f "$bin/$f.prev" "$bin/$f"
      [ -e "$work/$f.swap" ] && mv -f "$work/$f.swap" "$bin/$f.prev"
    fi
  done
  if [ -f "$bin/histmersion-server.plugin-set.json" ]; then
    expected_digest="$(tr -d ' \n' < "$bin/histmersion-server.plugin-set.json" | sed -n 's/.*"digest":"\([0-9a-f]*\)".*/\1/p')"
  fi
  restart_and_check || hk_die 4 "the rolled-back binary did not come up — see $data/logs/server.log"
  echo "rolled back (the replaced binary is now $server.prev)"
  exit 0
fi

need_java

# ── 2. the kit ────────────────────────────────────────────────────────────────────────────────────
step "kit: $(sed -n 's/.*"version" *: *"\([^"]*\)".*/\1/p' "$kit/kit.json" | head -1), GraalVM $graalvm_version"
hk_sha256_check "$kit" SHA256SUMS >/dev/null 2>&1 \
  || hk_die 2 "the kit at $kit does not match its SHA256SUMS (a file changed or is missing) — reinstall the bundle"
for f in "$kit"/lib/*.jar; do
  grep -qF "  lib/$(basename "$f")" "$kit/SHA256SUMS" || hk_die 2 "the kit holds a jar its manifest does not list: $f"
done

# ── 3. the toolchain ──────────────────────────────────────────────────────────────────────────────
need_native=""; [ "$mode" = relink ] && need_native=1
if [ -n "$need_native" ]; then
  gv="$(hk_find_graalvm "$graalvm_version")" \
    || hk_die 2 "GraalVM CE $graalvm_version with native-image not found — run histmersion-toolchain install (or set HISTMERSION_GRAALVM_HOME); JVM mode needs no build: histmersion-relink --jvm"
  hk_cc >/dev/null || hk_die 2 "no C toolchain — $( [ "$(hk_os)" = macos ] && echo "xcode-select --install" || echo "install gcc, glibc-devel/zlib headers (build-essential zlib1g-dev)")"
  min_gb="${HISTMERSION_RELINK_MIN_MEMORY_GB:-12}"
  mem="$(hk_mem_bytes)"
  if [ "$((mem / 1073741824))" -lt "$min_gb" ] && [ -z "$ignore_memory" ]; then
    hk_die 2 "$((mem / 1073741824)) GB of memory; a relink peaks near 12 GB (needs ${min_gb}) — use JVM mode (histmersion-relink --jvm) or --ignore-memory"
  fi
  free_kb="$(hk_free_kb "$install")"
  [ "${free_kb:-0}" -ge 1048576 ] || hk_die 2 "less than 1 GB free under $install — a relink writes a ~210 MB binary and keeps the previous one"
  echo "  GraalVM $gv · cc $(hk_cc) · $((mem / 1073741824)) GB memory"
fi

# ── 4. provenance ─────────────────────────────────────────────────────────────────────────────────
if [ -n "$(hk_gpg_key)" ]; then
  if "$signing" verify-plugins >/dev/null 2>&1; then echo "  plugins directory: signed manifest verified"
  elif [ -n "$unsigned" ]; then echo "  warning: the plugins directory does not match its signed manifest — going on (--unsigned)"
  else hk_die 2 "the plugins directory does not match its signed manifest ($("$signing" verify-plugins 2>&1 | tail -1)) — approve a dropped-in jar with histmersion plugin add <it>, or pass --unsigned"
  fi
fi

# ── 5. pre-flight on the JVM ──────────────────────────────────────────────────────────────────────
step "pre-flight: ${#jars[@]} plugin jar(s) in $plugins"
if [ "${#jars[@]}" -gt 0 ]; then
  out="$(tool check "$kit" "${jars[@]}" 2>&1)" && rc=0 || rc=$?
  printf '%s\n' "$out" | sed 's/^/  /'
  [ "$rc" = 0 ] || hk_die 2 "pre-flight refused the plugin set (above); nothing was built"
fi
new_ids="$(printf '%s\n' "${out:-}" | sed -n 's/^plugin: \([^ ]*\) .*/\1/p')"
set_mode=native; [ "$mode" = jvm ] && set_mode=jvm
expected_digest="$(tool plugin-set "$work/plugin-set.jar" "$work/plugin-set.json" "$set_mode" "$kit" ${jars[@]+"${jars[@]}"})"
cp_plugins="$work/plugin-set.jar"
for j in ${jars[@]+"${jars[@]}"}; do cp_plugins="$cp_plugins:$j"; done
# the server itself, on the JVM, with the user's config: decode errors stop here, not after the build
check_report() { # <report file> <exit code> → 0 when the config loaded and no NEW plugin failed
  [ "$2" = 3 ] && { echo "  the config does not load (above)" >&2; return 1; }
  local id bad=""
  for id in $new_ids; do
    if grep -qE "^- $id [^ ]+ — (FAILED|REFUSED)" "$1"; then bad="$bad $id"; fi
  done
  [ -z "$bad" ] || { echo "  failed:$bad" >&2; return 1; }
  grep -q "^- plugin-set digest: $expected_digest" "$1" || { echo "  the plugin-set digest is not $expected_digest" >&2; return 1; }
}
rc=0
"$jh/bin/java" -cp "$(kit_cp):$cp_plugins" "$main_class" --self-check > "$work/jvm-self-check.out" 2>&1 || rc=$?
sed -n '/^- [a-z0-9-]* [^ ]* — /p; /^- plugin set:/p; /^self-check/p' "$work/jvm-self-check.out" | sed 's/^/  jvm: /'
check_report "$work/jvm-self-check.out" "$rc" || hk_die 2 "pre-flight: the server's self-check on the JVM failed — $work kept nothing; see above"
[ "$mode" = check ] && { echo "pre-flight passed: plugin set ${expected_digest:0:12}…"; exit 0; }

# ── JVM mode: no build; the launcher becomes bin/histmersion-server ────────────────────────────────
if [ "$mode" = jvm ]; then
  step "switch to JVM mode"
  cp "$bin/histmersion-server-jvm" "$work/histmersion-server.new"
  chmod 0755 "$work/histmersion-server.new"
  swap_in "$work/histmersion-server.new" "$work/plugin-set.json"
  restart_and_check || { put_back; hk_service_restart >/dev/null 2>&1 || true; hk_die 4 "JVM mode did not come up; the previous binary is back"; }
  echo "JVM mode: bin/histmersion-server runs the kit on the JVM; plugins are found at each start"
  exit 0
fi

# ── 6. Sloth over the plugin jars ─────────────────────────────────────────────────────────────────
patched="$work/plugin-set.jar"
if [ "${#jars[@]}" -gt 0 ]; then
  step "Sloth: ${#jars[@]} plugin jar(s)"
  hierarchy="$(kit_cp)"
  for j in "${jars[@]}"; do cp "$j" "$work/plugins/"; hierarchy="$hierarchy:$work/plugins/$(basename "$j")"; done
  if [ -f "$kit/tools/sloth.jar" ]; then
    for j in "${jars[@]}"; do
      "$jh/bin/java" -jar "$kit/tools/sloth.jar" "$work/plugins/$(basename "$j")" --hierarchy-classpath "$hierarchy" >> "$log" 2>&1 \
        || hk_die 3 "Sloth failed on $(basename "$j") — see $log"
    done
    pairs=()
    for j in "${jars[@]}"; do pairs+=("$j" "$work/plugins/$(basename "$j")"); done
    "$jh/bin/java" "$kit/tools/SlothVerify.java" "$hierarchy" "${pairs[@]}" >> "$log" 2>&1 \
      || hk_die 3 "Sloth verify: a plugin class stays unverifiable — see $log"
  else
    echo "  warning: the kit has no Sloth (kit/tools/sloth.jar): plugin jars are linked as they are"
  fi
  for j in "${jars[@]}"; do patched="$patched:$work/plugins/$(basename "$j")"; done
fi

# ── 7. native-image ───────────────────────────────────────────────────────────────────────────────
step "native-image (about 2 minutes and 12 GB of memory; log: $log)"
start=$(date +%s)
rc=0
# in the work dir: native-image leaves reports (build artifacts, crash notes) in its working directory
(cd "$work" && "$gv/bin/native-image" "@$kit/native-image.args" -cp "$(kit_cp):$patched" "$main_class" \
  "$work/histmersion-server.new") >> "$log" 2>&1 || rc=$?
[ "$rc" = 0 ] && [ -x "$work/histmersion-server.new" ] || { tail -20 "$log" >&2; hk_die 3 "native-image failed (exit $rc) — the current binary is unchanged; JVM mode works with the same plugins: histmersion-relink --jvm"; }
peak="$(grep -o 'Peak RSS: [0-9.,]*[A-Z]*' "$log" | tail -1 || true)"
echo "  built in $(( $(date +%s) - start )) s${peak:+ · $peak}"

# ── 8. the new binary's own self-check (never started as a server) ────────────────────────────────
step "self-check of the new binary"
rc=0
"$work/histmersion-server.new" --self-check > "$work/self-check.out" 2>&1 || rc=$?
sed -n '/^- [a-z0-9-]* [^ ]* — /p; /^- plugin set:/p; /^self-check/p' "$work/self-check.out" | sed 's/^/  /'
check_report "$work/self-check.out" "$rc" || { cat "$work/self-check.out" >> "$log"; hk_die 3 "the new binary's self-check failed — the current binary is unchanged (a plugin without its own reachability metadata fails here)"; }

# ── 9. sign ───────────────────────────────────────────────────────────────────────────────────────
"$signing" codesign "$work/histmersion-server.new" com.histmersion.server | sed 's/^/  /'
if [ -n "$(hk_gpg_key)" ]; then
  (cd "$work" && hk_sha256 histmersion-server.new | sed 's/$/  histmersion-server/' > histmersion-server.sha256)
  if "$signing" sign "$work/histmersion-server.sha256"; then echo "  binary checksum signed with GPG key $(hk_gpg_key)"
  elif [ -n "$unsigned" ]; then echo "  warning: could not sign the binary's checksum — going on (--unsigned)"; rm -f "$work/histmersion-server.sha256"
  else hk_die 3 "could not sign the binary's checksum with GPG key $(hk_gpg_key) — pass --unsigned to relink without"; fi
fi

# ── 10–11. swap, restart, health; roll back on failure ────────────────────────────────────────────
step "swap: $server (the current one kept as .prev)"
swap_in "$work/histmersion-server.new" "$work/plugin-set.json"
if ! restart_and_check; then
  step "the new binary did not come up — putting the previous one back"
  put_back
  hk_service_restart >/dev/null 2>&1 || true
  hk_wait_healthy "${HISTMERSION_RELINK_HEALTH_SECONDS:-120}" || true
  hk_die 4 "rolled back to the previous binary; the failed one's log: $data/logs/server.log"
fi
echo "relinked: plugin set ${expected_digest:0:12}… (${#jars[@]} jar(s)); histmersion plugin rollback undoes it"
