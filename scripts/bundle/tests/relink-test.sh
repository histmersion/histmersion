#!/usr/bin/env bash
# Tests of bin/histmersion-relink's ORCHESTRATION (relink.sh) against a temporary install with FAKES:
# a fake GraalVM (java answers PluginTool and --self-check; native-image writes a fake binary), fake
# launchctl/systemctl/curl (a "service" whose health follows the binary in bin/), fake codesign and gpg.
# Nothing outside the temp dir is touched: no real service, no keychain, no network, no real build.
# The real relink (real native-image, the hello plugin) is in scripts/bundle/smoke.sh.
#
#   scripts/bundle/tests/relink-test.sh        (also: just test-scripts)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/.."

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export HOME="$tmp/home" SDKMAN_DIR="$tmp/no-sdkman"
unset JAVA_HOME GRAALVM_HOME HISTMERSION_CONFIG 2>/dev/null || true
mkdir -p "$HOME"
state="$tmp/state"; fakes="$tmp/fakes"; mkdir -p "$state" "$fakes"
export STATE="$state"

passed=0; failed=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }  # <what> <shell test>

# ── a fake GraalVM ────────────────────────────────────────────────────────────────────────────────
graal="$tmp/graalvm"; mkdir -p "$graal/bin"
echo 'JAVA_VERSION="25.0.4.1.1"' > "$graal/release"
cat > "$graal/bin/java" <<'SH'
#!/bin/bash
# PluginTool <cmd> …, Main --self-check, -jar sloth.jar, SlothVerify.java — told apart by the arguments
echo "java $*" >> "$STATE/java.calls"
args=" $* "
digest="$(cat "$STATE/digest" 2>/dev/null || echo d1)"
case "$args" in
  *"PluginTool check "*) cat "$STATE/check.out" 2>/dev/null || echo "plugin: hello 0.1.0 (SPI 1) in hello-0.1.0.jar · access open"
                          exit "$(cat "$STATE/check.rc" 2>/dev/null || echo 0)" ;;
  *"PluginTool plugin-set "*)
    set -- "$@"; while [ "$1" != plugin-set ]; do shift; done
    out="$2" record="$3" mode="$4"
    echo "set-jar" > "$out"
    printf '{"digest":"%s","spiVersion":1,"jars":[],"mode":"%s"}\n' "$digest" "$mode" > "$record"
    echo "$digest" ;;
  *"PluginTool describe "*) echo '[{"file":"x.jar","sha256":"00","plugins":[]}]' ;;
  *" --self-check "*)
    if [ -f "$STATE/jvm-selfcheck-fail" ]; then echo "- hello 0.1.0 — FAILED: broken on the JVM"; echo "self-check FAILED"; exit 2; fi
    echo "- hello 0.1.0 — registered (source-fetcher:hello)"; echo "- plugin-set digest: $digest"; echo "self-check passed" ;;
  *" -jar "*sloth.jar*) exit 0 ;;
  *SlothVerify.java*) exit 0 ;;
  *) echo "fake java: unexpected $*" >&2; exit 9 ;;
esac
SH
cat > "$graal/bin/native-image" <<'SH'
#!/bin/bash
echo "native-image $*" >> "$STATE/ni.calls"
for a; do out="$a"; done
[ -f "$STATE/ni.fail" ] && { echo "Error: fake link failure"; exit 1; }
digest="$(cat "$STATE/digest" 2>/dev/null || echo d1)"
status="registered (source-fetcher:hello)"; [ -f "$STATE/ni-selfcheck-fail" ] && status="FAILED: ClassNotFoundException: LoudGreeter"
{ echo '#!/bin/sh'
  echo "# digest=$digest"
  [ -f "$STATE/ni-broken" ] && echo "# WILL-NOT-START"
  echo 'if [ "$1" = --self-check ]; then'
  echo "  echo '- hello 0.1.0 — $status'; echo '- plugin-set digest: $digest'; echo 'self-check passed'; exit 0"
  echo 'fi'
} > "$out"
chmod +x "$out"
echo "Peak RSS: 1,23GB"
SH
chmod +x "$graal/bin/java" "$graal/bin/native-image"
jdk_only="$tmp/jdk-only"; mkdir -p "$jdk_only/bin"; cp "$graal/release" "$jdk_only/"; cp "$graal/bin/java" "$jdk_only/bin/"

# ── fake service, health, toolchain, signing tools on PATH ────────────────────────────────────────
cat > "$fakes/launchctl" <<'SH'
#!/bin/bash
echo "launchctl $*" >> "$STATE/restarts"
bin="$INSTALL/bin/histmersion-server"
if grep -q WILL-NOT-START "$bin" 2>/dev/null; then echo down > "$STATE/up"; else echo up > "$STATE/up"; fi
d="$(sed -n 's/^# digest=//p' "$bin")"
# JVM mode: the launcher reports the plugin set it built at start
[ -z "$d" ] && grep -q "JVM mode" "$bin" && d="$(cat "$STATE/digest" 2>/dev/null || echo d1)"
echo "$d" > "$STATE/running"
SH
cp "$fakes/launchctl" "$fakes/systemctl"
cat > "$fakes/curl" <<'SH'
#!/bin/bash
for a; do url="$a"; done
case "$url" in
  */api/health) [ "$(cat "$STATE/up" 2>/dev/null)" = up ] ;;
  */api/about) [ "$(cat "$STATE/up" 2>/dev/null)" = up ] || exit 7
               r="$(cat "$STATE/running" 2>/dev/null)"
               if [ -n "$r" ]; then printf '{"plugins":{"spiVersion":1,"set":{"digest":"%s","spiVersion":1}}}' "$r"
               else printf '{"plugins":{"spiVersion":1}}'; fi ;;
  *) exit 6 ;;
esac
SH
printf '#!/bin/sh\necho /usr/bin/clang\n' > "$fakes/xcrun"
printf '#!/bin/sh\necho /usr/bin/cc\n' > "$fakes/cc"
printf '#!/bin/sh\necho "codesign $*" >> "$STATE/codesign.calls"\n' > "$fakes/codesign"
# `security` would read the keychain: the tests must never call it (HISTMERSION_CODESIGN_IDENTITY=-)
printf '#!/bin/sh\necho "security $*" >> "$STATE/security.calls"\nexit 1\n' > "$fakes/security"
# gpg: a "signature" is the signed file's sha256; the key FAKEKEY is the only secret key
cat > "$fakes/gpg" <<'SH'
#!/bin/bash
args=" $* "
sum() { shasum -a 256 "$1" | awk '{print $1}'; }
case "$args" in
  *--list-secret-keys*) echo "fpr:::::::::FAKEKEY:" ;;
  *--detach-sign*) set -- "$@"; while [ "$1" != --output ]; do shift; done; out="$2"; for a; do f="$a"; done
                   echo "sig:$(sum "$f")" > "$out" ;;
  *--verify*) set -- "$@"; while [ "$1" != --verify ]; do shift; done
              if [ "$(cat "$2")" = "sig:$(sum "$3")" ]; then echo "[GNUPG:] VALIDSIG FAKEKEY x"; else echo "[GNUPG:] BADSIG FAKEKEY"; exit 1; fi ;;
esac
SH
chmod +x "$fakes"/*
export PATH="$fakes:$PATH" HISTMERSION_GPG="$fakes/gpg"

# ── a temporary install with a kit ────────────────────────────────────────────────────────────────
new_install() {
  rm -rf "$tmp/install" "$tmp/data" "$state" && mkdir -p "$state"
  export INSTALL="$tmp/install"
  mkdir -p "$INSTALL/bin" "$INSTALL/libexec" "$INSTALL/kit/lib" "$INSTALL/kit/tools"
  install -m 0755 "$src/relink.sh" "$INSTALL/bin/histmersion-relink"
  install -m 0755 "$src/signing.sh" "$INSTALL/bin/histmersion-signing"
  install -m 0755 "$src/server-jvm.sh" "$INSTALL/bin/histmersion-server-jvm"
  install -m 0644 "$src/kit-common.sh" "$INSTALL/libexec/kit-common.sh"
  install -m 0755 "$src/server-verified.sh" "$INSTALL/libexec/histmersion-server-verified"
  printf '#!/bin/sh\n# digest=e3b0\necho old\n' > "$INSTALL/bin/histmersion-server"; chmod +x "$INSTALL/bin/histmersion-server"
  echo '{"digest":"e3b0","spiVersion":1,"jars":[],"mode":"native"}' > "$INSTALL/bin/histmersion-server.plugin-set.json"
  echo "kit jar" > "$INSTALL/kit/lib/a.jar"
  echo a.jar > "$INSTALL/kit/classpath"
  echo "--initialize-at-build-time=org.slf4j.ScribeLoggerAdapter" > "$INSTALL/kit/native-image.args"
  echo com.histmersion.server.Main > "$INSTALL/kit/main-class"
  echo 25.0.4.1.1 > "$INSTALL/kit/graalvm-version"
  echo sloth > "$INSTALL/kit/tools/sloth.jar"; echo verify > "$INSTALL/kit/tools/SlothVerify.java"
  echo '{"format":1,"version":"0.1.0","graalvm":"25.0.4.1.1","spiVersion":1}' > "$INSTALL/kit/kit.json"
  (cd "$INSTALL/kit" && find . -type f ! -name kit.json ! -name SHA256SUMS | sed 's|^\./||' | sort \
    | while read -r f; do printf '%s  %s\n' "$(shasum -a 256 "$f" | awk '{print $1}')" "$f"; done > SHA256SUMS)
  export HISTMERSION_DATA_DIR="$tmp/data"
  mkdir -p "$HISTMERSION_DATA_DIR/plugins"
  echo "plugin" > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
  echo up > "$state/up"; echo e3b0 > "$state/running"
}
export HISTMERSION_GRAALVM_HOME="$graal" HISTMERSION_SERVICE=launchd HISTMERSION_SERVICE_LABEL=test.histmersion.relink \
       HISTMERSION_RELINK_MEMORY_BYTES=$((48 * 1073741824)) HISTMERSION_RELINK_HEALTH_SECONDS=2 \
       HISTMERSION_CODESIGN_IDENTITY=- HISTMERSION_SERVER_URL=http://127.0.0.1:1 HISTMERSION_MACHINE_ID=relink-test
relink() { local rc=0; "$INSTALL/bin/histmersion-relink" "$@" > "$tmp/out" 2>&1 || rc=$?; echo "$rc"; }
binary_has() { grep -q "$1" "$INSTALL/bin/histmersion-server"; }

echo "relink"

new_install
rc="$(relink)"
check "a relink succeeds (exit 0)" '[ "$rc" = 0 ] || { cat "$tmp/out"; false; }'
check "the new binary is in bin/, the old one is .prev" 'binary_has "digest=d1" && grep -q "digest=e3b0" "$INSTALL/bin/histmersion-server.prev"'
check "the plugin-set record is the new one, the old kept" 'grep -q "\"digest\":\"d1\"" "$INSTALL/bin/histmersion-server.plugin-set.json" && grep -q e3b0 "$INSTALL/bin/histmersion-server.plugin-set.json.prev"'
check "native-image got the kit's arguments and class path, the plugin-set jar, the Sloth-patched plugin" \
  'grep -q "@$INSTALL/kit/native-image.args -cp $INSTALL/kit/lib/a.jar:$INSTALL/.relink/plugin-set.jar:$INSTALL/.relink/plugins/hello-0.1.0.jar com.histmersion.server.Main" "$state/ni.calls"'
check "Sloth ran over the plugin jar only, then its verifier" 'grep -q "sloth.jar $INSTALL/.relink/plugins/hello-0.1.0.jar" "$state/java.calls" && grep -q SlothVerify.java "$state/java.calls"'
check "the pre-flight ran on the JVM before the build (check, then the server's self-check)" \
  '[ "$(grep -n "PluginTool check" "$state/java.calls" | cut -d: -f1)" -lt "$(grep -n -- "--self-check" "$state/java.calls" | cut -d: -f1)" ]'
check "the service was restarted once and reports the new digest" '[ "$(grep -c kickstart "$state/restarts")" = 1 ] && grep -q "test.histmersion.relink" "$state/restarts"'
check "signed ad hoc, the keychain never asked (identity -)" 'grep -q "codesign --force --sign - --identifier com.histmersion.server" "$state/codesign.calls" && [ ! -e "$state/security.calls" ]'
check "the work dir and the lock are gone; a log was written" '[ ! -e "$INSTALL/.relink" ] && [ ! -e "$HISTMERSION_DATA_DIR/plugins/.relink.lock" ] && grep -q native-image "$HISTMERSION_DATA_DIR/logs/relink.log"'
check "it reports the build and the peak memory" 'grep -q "Peak RSS: 1,23GB" "$tmp/out"'
# shellcheck source=../kit-common.sh
. "$src/kit-common.sh"
rec="$INSTALL/bin/histmersion-server.build"
check "the build record: native, this kit, these plugin jars, this machine, a relink" \
  '[ "$(hk_record_value "$rec" mode)" = native ] && [ "$(hk_record_value "$rec" kit)" = "$(hk_kit_fingerprint "$INSTALL/kit")" ] && [ "$(hk_record_value "$rec" plugins)" = "$(hk_plugins_fingerprint "$HISTMERSION_DATA_DIR/plugins")" ] && [ "$(hk_record_value "$rec" machine)" = "$(hk_machine_id)" ] && [ "$(hk_record_value "$rec" origin)" = relink ]'
check "the record says the binary is current; another plugin jar makes it stale" \
  'hk_build_current "$INSTALL/bin/histmersion-server" "$INSTALL/kit" "$HISTMERSION_DATA_DIR/plugins" >/dev/null && ! hk_build_current "$INSTALL/bin/histmersion-server" "$INSTALL/kit" "$tmp/no-plugins" >/dev/null'

rc="$(relink --rollback)"
check "rollback: the previous binary back, the replaced one kept as .prev" '[ "$rc" = 0 ] && binary_has "digest=e3b0" && grep -q "digest=d1" "$INSTALL/bin/histmersion-server.prev"'
check "rollback: the records follow" 'grep -q e3b0 "$INSTALL/bin/histmersion-server.plugin-set.json" && grep -q d1 "$INSTALL/bin/histmersion-server.plugin-set.json.prev"'
check "rollback: the build record goes with its binary (the stock one had none)" '[ ! -e "$rec" ] && [ -f "$rec.prev" ]'

new_install
rc="$(relink --check)"
check "--check: the pre-flight alone, nothing built or swapped" '[ "$rc" = 0 ] && [ ! -e "$state/ni.calls" ] && binary_has "digest=e3b0" && [ ! -e "$state/restarts" ]'

new_install
echo "refused: hello-0.1.0.jar duplicates classes already on the class path" > "$state/check.out"; echo 2 > "$state/check.rc"
rc="$(relink)"
check "pre-flight refusal: exit 2, nothing built, binary unchanged" '[ "$rc" = 2 ] && [ ! -e "$state/ni.calls" ] && binary_has "digest=e3b0" && grep -q "duplicates classes" "$tmp/out"'

new_install
touch "$state/jvm-selfcheck-fail"
rc="$(relink)"
check "a plugin that fails the JVM self-check: exit 2 before the build" '[ "$rc" = 2 ] && [ ! -e "$state/ni.calls" ]'

new_install
touch "$state/ni.fail"
rc="$(relink)"
check "native-image fails: exit 3, binary unchanged, no restart, JVM mode suggested" \
  '[ "$rc" = 3 ] && binary_has "digest=e3b0" && [ ! -e "$state/restarts" ] && grep -q -- "--jvm" "$tmp/out"'

new_install
touch "$state/ni-selfcheck-fail"
rc="$(relink)"
check "the new binary's self-check fails (a plugin without its metadata): exit 3, unchanged" \
  '[ "$rc" = 3 ] && binary_has "digest=e3b0" && [ ! -e "$INSTALL/bin/histmersion-server.prev" ]'

new_install
touch "$state/ni-broken"
rc="$(relink)"
check "the new binary does not come up: exit 4, the previous one is back and restarted" \
  '[ "$rc" = 4 ] && binary_has "digest=e3b0" && [ "$(grep -c kickstart "$state/restarts")" = 2 ] && [ "$(cat "$state/up")" = up ]'

new_install
# a server that comes up but reports another plugin set than the one linked:
cat > "$fakes/launchctl" <<'SH'
#!/bin/bash
echo "launchctl $*" >> "$STATE/restarts"; echo up > "$STATE/up"; echo stale > "$STATE/running"
SH
chmod +x "$fakes/launchctl"
rc="$(relink)"
check "up, but running another plugin set: rolled back (exit 4)" '[ "$rc" = 4 ] && binary_has "digest=e3b0" && grep -q "expected d1" "$tmp/out"'
cp "$fakes/systemctl" "$fakes/launchctl" # restore the real fake

new_install
rc="$(relink --no-restart)"
check "--no-restart: swapped, not restarted" '[ "$rc" = 0 ] && binary_has "digest=d1" && [ ! -e "$state/restarts" ]'

new_install
mkdir -p "$HISTMERSION_DATA_DIR/plugins/.relink.lock"; echo $$ > "$HISTMERSION_DATA_DIR/plugins/.relink.lock/pid"
rc="$(relink)"
check "another relink holds the lock: exit 2" '[ "$rc" = 2 ] && grep -q "another relink" "$tmp/out"'
echo 999999 > "$HISTMERSION_DATA_DIR/plugins/.relink.lock/pid"
rc="$(relink --check)"
check "a stale lock (dead pid) is taken over" '[ "$rc" = 0 ]'

new_install
rc="$(HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) relink)"
check "8 GB of memory: refused (exit 2), JVM mode suggested" '[ "$rc" = 2 ] && grep -q "histmersion-relink --jvm" "$tmp/out"'
rc="$(HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) relink --ignore-memory)"
check "--ignore-memory builds anyway" '[ "$rc" = 0 ]'

new_install
echo tampered >> "$INSTALL/kit/lib/a.jar"
rc="$(relink)"
check "a kit that does not match SHA256SUMS: refused (exit 2)" '[ "$rc" = 2 ] && grep -q SHA256SUMS "$tmp/out"'
new_install
echo extra > "$INSTALL/kit/lib/extra.jar"
rc="$(relink)"
check "a jar the kit manifest does not list: refused (exit 2)" '[ "$rc" = 2 ] && grep -q "does not list" "$tmp/out"'

new_install
rc="$(HISTMERSION_GRAALVM_HOME="" HISTMERSION_JAVA_HOME="$jdk_only" relink)"
check "no GraalVM with native-image: refused (exit 2), toolchain install named" '[ "$rc" = 2 ] && grep -q "histmersion-toolchain install" "$tmp/out"'
if [ "$(uname -s)" = Darwin ]; then # (on Linux any cc on PATH counts, and /usr/bin has one)
  printf '#!/bin/sh\nexit 1\n' > "$fakes/xcrun"; chmod +x "$fakes/xcrun"
  rc="$(relink)"
  check "no C toolchain (xcrun finds no clang): refused (exit 2)" '[ "$rc" = 2 ] && grep -q "no C toolchain" "$tmp/out"'
fi
printf '#!/bin/sh\necho /usr/bin/clang\n' > "$fakes/xcrun"; printf '#!/bin/sh\necho /usr/bin/cc\n' > "$fakes/cc"

# ── GPG provenance ────────────────────────────────────────────────────────────────────────────────
new_install
mkdir -p "$HISTMERSION_DATA_DIR/signing"; echo FAKEKEY > "$HISTMERSION_DATA_DIR/signing/gpg-key"
rc="$(relink)"
check "provenance on, no signed manifest: refused (exit 2)" '[ "$rc" = 2 ] && grep -q "signed manifest" "$tmp/out"'
"$INSTALL/bin/histmersion-signing" sign-plugins > /dev/null
rc="$(relink)"
check "provenance on, the directory signed: relinked, the binary's checksum signed" \
  '[ "$rc" = 0 ] && [ -f "$INSTALL/bin/histmersion-server.sha256.asc" ] && grep -q "histmersion-server$" "$INSTALL/bin/histmersion-server.sha256"'
echo "dropped in" > "$HISTMERSION_DATA_DIR/plugins/sneaky-1.0.jar"
rc="$(relink)"
check "a jar dropped in after signing: refused (exit 2)" '[ "$rc" = 2 ] && grep -q "does not match its signed manifest" "$tmp/out"'
rc="$(relink --unsigned)"
check "--unsigned goes on with a warning" '[ "$rc" = 0 ] && grep -q "going on (--unsigned)" "$tmp/out"'
lrc=0; "$INSTALL/libexec/histmersion-server-verified" > "$tmp/l.out" 2>&1 || lrc=$?
check "the service's launcher verifies the relinked binary and starts it" '[ "$lrc" = 0 ] && grep -q "verified histmersion-server .* signed by FAKEKEY" "$tmp/l.out"'
rc="$(relink --rollback --no-restart)"
check "rollback with provenance: the previous (signed) binary back, its signed record with it" \
  '[ "$rc" = 0 ] && "$INSTALL/libexec/histmersion-server-verified" > "$tmp/l.out" 2>&1 && grep -q verified "$tmp/l.out"'

new_install
mkdir -p "$HISTMERSION_DATA_DIR/signing"; echo FAKEKEY > "$HISTMERSION_DATA_DIR/signing/gpg-key"
"$INSTALL/bin/histmersion-signing" sign-plugins > /dev/null
rc="$(relink --no-restart)"
rc="$(relink --rollback --no-restart)"
check "provenance on, the previous binary never signed (the stock one): rollback refused (exit 2) before anything moved" \
  '[ "$rc" = 2 ] && grep -q "previous binary is not verified" "$tmp/out" && binary_has "digest=d1"'
"$INSTALL/bin/histmersion-signing" sign-binary > /dev/null
rc="$(relink --rollback --no-restart)"
check "…signed with sign-binary (you vouch for it): rolled back, and the launcher starts it" \
  '[ "$rc" = 0 ] && binary_has "digest=e3b0" && "$INSTALL/libexec/histmersion-server-verified" > "$tmp/l.out" 2>&1 && grep -q verified "$tmp/l.out"'

# ── JVM mode ──────────────────────────────────────────────────────────────────────────────────────
new_install
rc="$(relink --jvm)"
check "--jvm: no build; the launcher becomes bin/histmersion-server, the record says jvm" \
  '[ "$rc" = 0 ] && [ ! -e "$state/ni.calls" ] && cmp -s "$INSTALL/bin/histmersion-server" "$INSTALL/bin/histmersion-server-jvm" && grep -q "\"mode\":\"jvm\"" "$INSTALL/bin/histmersion-server.plugin-set.json"'
check "--jvm: the build record says jvm, so an install never takes it for a native binary" \
  'grep -qx "mode=jvm" "$INSTALL/bin/histmersion-server.build" && ! hk_build_current "$INSTALL/bin/histmersion-server" "$INSTALL/kit" "$HISTMERSION_DATA_DIR/plugins" >/dev/null'
check "--jvm without provenance: no signed checksum" '[ ! -e "$INSTALL/bin/histmersion-server.sha256.asc" ]'
new_install
mkdir -p "$HISTMERSION_DATA_DIR/signing"; echo FAKEKEY > "$HISTMERSION_DATA_DIR/signing/gpg-key"
"$INSTALL/bin/histmersion-signing" sign-plugins > /dev/null
rc="$(relink --jvm --no-restart)"
check "--jvm with provenance: the launcher put in bin/ has its SHA-256 signed, so the service's verified start accepts it" \
  '[ "$rc" = 0 ] && hk_verify_binary "$INSTALL/bin/histmersion-server" "$INSTALL/bin/histmersion-server.sha256" "$INSTALL/bin/histmersion-server.sha256.asc" FAKEKEY >/dev/null'

rc="$(relink --describe "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar")"
check "--describe prints the kit tool's JSON" '[ "$rc" = 0 ] && grep -q "\"plugins\":\[\]" "$tmp/out"'
rc="$(relink --bogus)"
check "an unknown argument: usage (exit 1)" '[ "$rc" = 1 ]'

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
