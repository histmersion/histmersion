#!/usr/bin/env bash
# Tests of the kit's other scripts in a temp dir — nothing outside it:
#   - histmersion-server-jvm (server-jvm.sh): the class path, the environment, provenance, with a FAKE java
#   - histmersion-toolchain (toolchain.sh): provisioning GraalVM from a pins file — a FAKE GraalVM tarball
#     served from file://, its SHA-256 checked; check's verdicts
#   - histmersion-signing (signing.sh): GPG provenance with the REAL gpg in a temporary GNUPGHOME (a
#     throw-away key without a passphrase; skipped with a note when gpg is not installed), and ad-hoc
#     code signing of a copy of /usr/bin/true (macOS)
#   - with HISTMERSION_TEST_KEYCHAIN=1 (macOS, opt-in): an UNTRUSTED self-signed identity in a TEMPORARY
#     keychain, codesign with it; two different binaries (different cdhashes) get one designated
#     requirement and each satisfies the other's — what a TCC grant stores and checks, the automatable
#     proxy for "a privacy permission survives a relink" (the real check needs a privacy prompt:
#     scripts/bundle/tcc-check.sh); ad-hoc signing, the control, gives neither. The user's keychain
#     search list is saved, the temporary keychain appended for the signing, the list restored and
#     compared, the keychain deleted. The login keychain is never read or changed.
#   - the installers' build step (install-build.sh): local by default, --prebuilt, --jvm, every fallback to
#     the prebuilt binary, the build record that spares a second link; and the WHOLE installer of this
#     platform against fakes (temporary HOME and install, fake launchctl/systemctl and health)
#
#   scripts/bundle/tests/kit-scripts-test.sh        (also: just test-scripts)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/.."

tmp="$(mktemp -d)"
cleanup() {
  [ -n "${GNUPGHOME:-}" ] && gpgconf --homedir "$GNUPGHOME" --kill all >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT
real_home="$HOME" # the keychain search list is per user and found from HOME: the keychain block needs the real one
export HOME="$tmp/home" SDKMAN_DIR="$tmp/no-sdkman"
unset JAVA_HOME GRAALVM_HOME HISTMERSION_GRAALVM_HOME HISTMERSION_JAVA_HOME 2>/dev/null || true
mkdir -p "$HOME"
export HISTMERSION_DATA_DIR="$tmp/data"

passed=0; failed=0; skipped=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1"; }
skip() { skipped=$((skipped + 1)); echo "  skip $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }  # <what> <shell test>

install="$tmp/install"
mkdir -p "$install/bin" "$install/libexec" "$install/kit/lib"
for s in server-jvm:histmersion-server-jvm toolchain:histmersion-toolchain signing:histmersion-signing; do
  install -m 0755 "$src/${s%%:*}.sh" "$install/bin/${s#*:}"
done
install -m 0644 "$src/kit-common.sh" "$install/libexec/kit-common.sh"
echo 25.0.4.1.1 > "$install/kit/graalvm-version"
printf 'b.jar\na.jar\n' > "$install/kit/classpath"
echo com.histmersion.server.Main > "$install/kit/main-class"

# ── histmersion-server-jvm ────────────────────────────────────────────────────────────────────────
echo "server-jvm"
jdk="$tmp/jdk"; mkdir -p "$jdk/bin"; echo 'JAVA_VERSION="25.0.2"' > "$jdk/release"
cat > "$jdk/bin/java" <<'SH'
#!/bin/bash
case " $* " in
  *"PluginTool plugin-set "*) set -- "$@"; while [ "$1" != plugin-set ]; do shift; done
                              echo jar > "$2"; echo '{"digest":"dd","mode":"jvm"}' > "$3"; echo dd; exit 0 ;;
esac
{ echo "HOME_ENV=$HISTMERSION_HOME"; echo "RUNTIME=$HISTMERSION_RUNTIME"; printf 'arg=%s\n' "$@"; } > "$OUT"
SH
chmod +x "$jdk/bin/java"
export OUT="$tmp/jvm.out"
HISTMERSION_JAVA_HOME="$jdk" "$install/bin/histmersion-server-jvm" --self-check
check "no plugins: the kit's class path in its order, the main class, the arguments passed on" \
  'grep -qx "arg=$install/kit/lib/b.jar:$install/kit/lib/a.jar" "$OUT" && grep -qx "arg=com.histmersion.server.Main" "$OUT" && grep -qx "arg=--self-check" "$OUT"'
check "the bundle's runtime is found from HISTMERSION_HOME (the process is java)" \
  'grep -qx "HOME_ENV=$install" "$OUT" && grep -qx "RUNTIME=true" "$OUT"'
mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo p > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
HISTMERSION_JAVA_HOME="$jdk" HISTMERSION_JVM_OPTS="-Xmx1g" "$install/bin/histmersion-server-jvm"
check "with plugins: the plugin-set jar, then each plugin jar, after the kit; JVM options first" \
  'grep -qx "arg=$install/kit/lib/b.jar:$install/kit/lib/a.jar:$HISTMERSION_DATA_DIR/plugins/.jvm/plugin-set.jar:$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar" "$OUT" && grep -qx "arg=-Xmx1g" "$OUT"'
check "the plugin-set record of this start is written" 'grep -q "\"mode\":\"jvm\"" "$HISTMERSION_DATA_DIR/plugins/.jvm/plugin-set.json"'
rc=0; HISTMERSION_JAVA_HOME="$tmp/none" "$install/bin/histmersion-server-jvm" > "$tmp/e.out" 2>&1 || rc=$?
check "no JDK of the pinned major: exit 3, toolchain install named" '[ "$rc" = 3 ] && grep -q "histmersion-toolchain install" "$tmp/e.out"'
mkdir -p "$HISTMERSION_DATA_DIR/signing"; echo SOMEKEY > "$HISTMERSION_DATA_DIR/signing/gpg-key"
rc=0; HISTMERSION_JAVA_HOME="$jdk" HISTMERSION_GPG=false "$install/bin/histmersion-server-jvm" > "$tmp/e.out" 2>&1 || rc=$?
check "provenance on and the directory unverified: the server does not start (exit 3)" '[ "$rc" = 3 ] && grep -q "signed manifest" "$tmp/e.out"'
rm -rf "$HISTMERSION_DATA_DIR/signing" "$HISTMERSION_DATA_DIR/plugins"

# ── histmersion-toolchain ─────────────────────────────────────────────────────────────────────────
echo "toolchain"
fake="$tmp/fake-graal/graalvm-community-25.4.4.1.1+1.1"
mkdir -p "$fake/Contents/Home/bin"
echo 'JAVA_VERSION="25.0.4.1.1"' > "$fake/Contents/Home/release"
printf '#!/bin/sh\necho fake\n' > "$fake/Contents/Home/bin/native-image"; cp "$fake/Contents/Home/bin/native-image" "$fake/Contents/Home/bin/java"
chmod +x "$fake/Contents/Home/bin/"*
tar -czf "$tmp/graal.tar.gz" -C "$tmp/fake-graal" "graalvm-community-25.4.4.1.1+1.1"
sum="$(shasum -a 256 "$tmp/graal.tar.gz" | awk '{print $1}')"
export HISTMERSION_TOOLCHAIN_PLATFORM=test-arch
printf '# a comment\n25.0.4.1.1 test-arch %s file://%s\n' "0000000000000000000000000000000000000000000000000000000000000000" "$tmp/graal.tar.gz" > "$tmp/pins-bad"
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins-bad" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "a checksum mismatch: refused (exit 2), nothing unpacked, no download left" \
  '[ "$rc" = 2 ] && grep -q "SHA-256 mismatch" "$tmp/t.out" && [ ! -e "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.4.1.1" ] && [ -z "$(ls -A "$HISTMERSION_DATA_DIR/toolchain" 2>/dev/null | grep -v "^\.lock$" || true)" ]'
printf '25.0.4.1.1 test-arch %s file://%s\n' "$sum" "$tmp/graal.tar.gz" > "$tmp/pins"
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "the pinned archive is unpacked into the data directory" '[ "$rc" = 0 ] && [ -x "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.4.1.1/Contents/Home/bin/native-image" ]'
check "the licence is named on the way" 'grep -q "Classpath Exception" "$tmp/t.out"'
check "path: the provisioned home (macOS layout: Contents/Home)" \
  '[ "$("$install/bin/histmersion-toolchain" path)" = "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.4.1.1/Contents/Home" ]'
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "install again: already provisioned, nothing downloaded" '[ "$rc" = 0 ] && grep -q "already provisioned" "$tmp/t.out"'
printf '25.0.4.1.1 other-arch %s file://%s\n' "$sum" "$tmp/graal.tar.gz" > "$tmp/pins-other"
"$install/bin/histmersion-toolchain" remove > /dev/null
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins-other" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "no pin for this platform: refused (exit 2)" '[ "$rc" = 2 ] && grep -q "no pinned GraalVM" "$tmp/t.out"'
rc=0; HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) "$install/bin/histmersion-toolchain" check > "$tmp/t.out" 2>&1 || rc=$?
check "check: no GraalVM and 8 GB → cannot relink (exit 2), JVM mode named" \
  '[ "$rc" = 2 ] && grep -q "GraalVM CE 25.0.4.1.1: MISSING" "$tmp/t.out" && grep -q "LESS than" "$tmp/t.out" && grep -q "JVM mode" "$tmp/t.out"'
pinned="$(awk '!/^#/ && NF {print $1; exit}' "$src/graalvm-pins.txt")"
check "the repo's pins: one version, the three CI platforms, each a 64-hex SHA-256 and an official release URL" \
  '[ -n "$pinned" ] && [ "$(awk "!/^#/ && NF" "$src/graalvm-pins.txt" | grep -cvE "^$pinned [a-z0-9-]+ [0-9a-f]{64} https://github.com/graalvm/graalvm-ce-builds/releases/download/[^ ]+_bin\.tar\.gz$")" = 0 ] \
   && [ "$(grep -cE "^$pinned (macos-aarch64|linux-x64|linux-aarch64) " "$src/graalvm-pins.txt")" = 3 ]'
rc=0; HISTMERSION_HOME="$tmp/no-kit" HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) bash "$src/toolchain.sh" check > "$tmp/t.out" 2>&1 || rc=$?
check "without a kit, the toolchain's version is the pins' (the one pin)" 'grep -q "GraalVM CE $pinned" "$tmp/t.out"'
check ".sdkmanrc names no JDK of its own" '! grep -q "^java=" "$src/../../.sdkmanrc"'

# ── histmersion-signing ───────────────────────────────────────────────────────────────────────────
echo "signing"
signing="$install/bin/histmersion-signing"
out="$("$signing" status)"
check "status with nothing set up: GPG off" 'echo "$out" | grep -q "GPG provenance: off"'
if [ "$(uname -s)" = Darwin ]; then
  # #3647: untrusted by default; declining the question changes nothing either way
  sm="$(printf 'n\n' | "$signing" setup-macos 2>&1)"
  smt="$(printf 'n\n' | "$signing" setup-macos --trust 2>&1)"
  smn="$(printf 'n\n' | "$signing" setup-macos --no-trust 2>&1)"
  check "setup-macos leaves the identity untrusted by default (and 'n' changes nothing)" \
    'echo "$sm" | grep -q "left untrusted" && echo "$sm" | grep -q "nothing changed"'
  check "setup-macos --trust says it would trust it; --no-trust is still accepted" \
    'echo "$smt" | grep -q "trusts it for code signing" && echo "$smn" | grep -q "left untrusted"'
fi
if command -v gpg >/dev/null 2>&1; then
  export GNUPGHOME="$tmp/gnupg"; mkdir -m 700 "$GNUPGHOME"
  gpg --batch --pinentry-mode loopback --passphrase '' --quick-generate-key "Histmersion Test <test@localhost>" ed25519 sign 1d >/dev/null 2>&1
  fpr="$(gpg --batch --with-colons --list-secret-keys | awk -F: '/^fpr:/ {print $10; exit}')"
  mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo one > "$HISTMERSION_DATA_DIR/plugins/a-1.0.jar"
  "$signing" use-gpg "$fpr" > "$tmp/s.out"
  check "use-gpg keeps the fingerprint (nothing secret) and signs the directory" \
    '[ "$(cat "$HISTMERSION_DATA_DIR/signing/gpg-key")" = "$fpr" ] && [ -f "$HISTMERSION_DATA_DIR/plugins/plugins.manifest.asc" ]'
  check "the manifest is the jars' SHA-256s" 'grep -q "  a-1.0.jar$" "$HISTMERSION_DATA_DIR/plugins/plugins.manifest"'
  check "verify-plugins: good" '"$signing" verify-plugins > /dev/null'
  echo two > "$HISTMERSION_DATA_DIR/plugins/b-1.0.jar"
  check "a jar dropped in: verify-plugins fails" '! "$signing" verify-plugins > /dev/null 2>&1'
  "$signing" sign-plugins > /dev/null
  check "re-signed: verify-plugins good again" '"$signing" verify-plugins > /dev/null'
  echo three > "$HISTMERSION_DATA_DIR/plugins/b-1.0.jar"
  check "a jar changed in place: verify-plugins fails" '! "$signing" verify-plugins > /dev/null 2>&1'
  echo payload > "$tmp/file"; "$signing" sign "$tmp/file"
  check "sign + verify-file: the signer's fingerprint" '[ "$("$signing" verify-file "$tmp/file" "$tmp/file.asc")" = "$fpr" ]'
  echo tampered >> "$tmp/file"
  check "verify-file on a changed file: refused" '! "$signing" verify-file "$tmp/file" "$tmp/file.asc" > /dev/null 2>&1'
  check "no passphrase or key material in the data directory" '[ -z "$(grep -rl "PRIVATE KEY" "$HISTMERSION_DATA_DIR" 2>/dev/null || true)" ]'
  "$signing" off-gpg > /dev/null
  check "off-gpg: provenance off" '"$signing" status | grep -q "GPG provenance: off"'
else
  skip "GPG provenance (gpg is not installed)"
fi
if [ "$(uname -s)" = Darwin ]; then
  cp /usr/bin/true "$tmp/bin-adhoc"
  HISTMERSION_CODESIGN_IDENTITY=- "$signing" codesign "$tmp/bin-adhoc" com.histmersion.test > "$tmp/c.out"
  info="$(codesign -dv "$tmp/bin-adhoc" 2>&1)"
  check "codesign with identity '-': ad hoc, the identifier set, nothing looked up" \
    'grep -q "signed ad hoc" "$tmp/c.out" && echo "$info" | grep -q "Identifier=com.histmersion.test" && echo "$info" | grep -q "Signature=adhoc"'
fi

# ── tcc-check.sh: the manual TCC procedure's mechanics (the privacy prompt itself is the user's) ────
if [ "$(uname -s)" = Darwin ] && command -v clang >/dev/null 2>&1; then
  echo "tcc-check"
  tfakes="$tmp/tcc-fakes"; mkdir -p "$tfakes" "$tmp/tcc-target"; touch "$tmp/tcc-target/a" "$tmp/tcc-target/b"
  # launchd stand-in: bootstrap/kickstart run the agent's program once, as launchd would
  cat > "$tfakes/launchctl" <<SH
#!/bin/bash
echo "launchctl \$*" >> "$tmp/tcc.calls"
case "\$1" in
  print) [ -f "$tmp/tcc.loaded" ]; exit ;;
  bootstrap|kickstart)
    [ "\$1" = bootstrap ] && { touch "$tmp/tcc.loaded"; p="\$3"; } || p="$tmp/tcc-agents/com.histmersion.tcc-check.plist"
    out="\$(plutil -extract StandardOutPath raw -o - "\$p")"
    "\$(plutil -extract ProgramArguments.0 raw -o - "\$p")" "\$(plutil -extract ProgramArguments.1 raw -o - "\$p")" >> "\$out" 2>&1 || true ;;
  bootout) rm -f "$tmp/tcc.loaded" ;;
esac
exit 0
SH
  printf '#!/bin/sh\necho "security $*" >> "%s/tcc.security"\nexit 1\n' "$tmp" > "$tfakes/security"
  chmod +x "$tfakes"/*
  tcc() { PATH="$tfakes:$PATH" HISTMERSION_TCC_DIR="$tmp/tcc-work" HISTMERSION_TCC_AGENTS="$tmp/tcc-agents" \
    HISTMERSION_TCC_TARGET="$tmp/tcc-target" HISTMERSION_TCC_WAIT=5 "$src/tcc-check.sh" "$@"; }
  tcc start --identity - > "$tmp/tcc.out" 2>&1
  check "start: a probe built and signed, a throw-away LaunchAgent (never the server's) run once; it lists the target" \
    'grep -q "probe: variant=1 ALLOWED $tmp/tcc-target: 4 entries" "$tmp/tcc.out" && [ "$(plutil -extract Label raw -o - "$tmp/tcc-agents/com.histmersion.tcc-check.plist")" = com.histmersion.tcc-check ] && grep -q "launchctl bootstrap gui/$(id -u) $tmp/tcc-agents/com.histmersion.tcc-check.plist" "$tmp/tcc.calls"'
  cd1="$(codesign -dvvv "$tmp/tcc-work/histmersion-tcc-probe" 2>&1 | sed -n 's/^CDHash=//p')"
  tcc relink > "$tmp/tcc.out" 2>&1
  cd2="$(codesign -dvvv "$tmp/tcc-work/histmersion-tcc-probe" 2>&1 | sed -n 's/^CDHash=//p')"
  check "relink: a DIFFERENT binary at the same path (new cdhash, same identifier), run again with kickstart, read as persisted" \
    '[ -n "$cd1" ] && [ "$cd1" != "$cd2" ] && grep -q "Identifier=com.histmersion.tcc-probe" <(codesign -dv "$tmp/tcc-work/histmersion-tcc-probe" 2>&1) && grep -q "launchctl kickstart" "$tmp/tcc.calls" && grep -q "variant=2 ALLOWED" "$tmp/tcc.out" && grep -q "PERSISTED" "$tmp/tcc.out"'
  chmod 000 "$tmp/tcc-target"; tcc control > "$tmp/tcc.out" 2>&1; chmod 755 "$tmp/tcc-target"
  check "control: an ad-hoc probe; a denial is reported as the expected outcome" 'grep -q "variant=3 DENIED" "$tmp/tcc.out" && grep -q "as expected" "$tmp/tcc.out"'
  check "result: every answer, newest last" '[ "$(tcc result | grep -c "variant=")" = 3 ]'
  check "the keychain was never touched with an existing identity (no security call)" '[ ! -e "$tmp/tcc.security" ]'
  tcc cleanup > "$tmp/tcc.out" 2>&1
  check "cleanup: the agent unloaded, its plist and the work directory gone, the remaining manual step named" \
    '[ ! -e "$tmp/tcc-agents/com.histmersion.tcc-check.plist" ] && [ ! -e "$tmp/tcc-work" ] && [ ! -e "$tmp/tcc.loaded" ] && grep -q "Files and Folders" "$tmp/tcc.out"'
else
  skip "tcc-check.sh (macOS with clang)"
fi

# ── the installers' build step (install-build.sh; D5: a local build by default) ─────────────────────
echo "install-build"
# shellcheck source=../install-build.sh
. "$src/install-build.sh"
check "mode: local by default, as asked otherwise" \
  '[ "$(ib_mode "")" = local ] && [ "$(ib_mode local)" = local ] && [ "$(ib_mode jvm)" = jvm ] && [ "$(ib_mode prebuilt)" = prebuilt ]'
check "mode: an unknown one is refused" '! ib_mode fast >/dev/null 2>&1'

# a tool that records its call and exits with $tmp/rc.<name>.<first argument> (default 0); a relink
# that succeeds puts a "linked" binary in bin/ with its build record, as the real one does
fake_tool() { # <dest> <name>
  cat > "$1/bin/$2" <<SH
#!/bin/bash
echo "$2 \$*" >> "$tmp/steps"
rc="\$(cat "$tmp/rc.$2.\$1" 2>/dev/null || echo 0)"
if [ "$2" = histmersion-relink ] && [ "\$rc" = 0 ]; then
  mode=native; [ "\$1" = --jvm ] && mode=jvm; d="\${HISTMERSION_HOME:-$1}"
  printf '#!/bin/sh\n# linked %s\n' "\$mode" > "\$d/bin/histmersion-server"; chmod +x "\$d/bin/histmersion-server"
  . "$src/kit-common.sh"; hk_write_build_record "\$d/bin/histmersion-server.build" "\$mode" "\$d/kit" "\$HISTMERSION_DATA_DIR/plugins" relink
fi
exit "\$rc"
SH
  chmod +x "$1/bin/$2"
}
new_dest() { # <dir> — a bundle with a prebuilt binary, a kit, fake toolchain and relink
  rm -rf "$1" "$tmp"/rc.* && mkdir -p "$1/bin" "$1/kit" "$1/libexec"
  printf '#!/bin/sh\n# prebuilt\n' > "$1/bin/histmersion-server"; chmod +x "$1/bin/histmersion-server"
  echo 25.0.4.1.1 > "$1/kit/graalvm-version"; echo kit-jar > "$1/kit/a.jar"
  (cd "$1/kit" && shasum -a 256 a.jar graalvm-version > SHA256SUMS)
  install -m 0644 "$src/kit-common.sh" "$1/libexec/kit-common.sh"
  fake_tool "$1" histmersion-toolchain; fake_tool "$1" histmersion-relink
  : > "$tmp/steps"
}
steps() { tr '\n' ';' < "$tmp/steps"; }
build() { # <dest> <mode> [<previous>] → output in $tmp/ib.out, rc in $ib_rc
  ib_rc=0; HISTMERSION_HOME="$1" ib_build "$@" > "$tmp/ib.out" 2>&1 || ib_rc=$?
}
is_prebuilt() { grep -q "# prebuilt" "$1/bin/histmersion-server"; }
fakebin="$tmp/fake-cc"; mkdir -p "$fakebin"
printf '#!/bin/sh\necho /usr/bin/clang\n' > "$fakebin/xcrun"; printf '#!/bin/sh\necho /usr/bin/cc\n' > "$fakebin/cc"
chmod +x "$fakebin"/*
export HISTMERSION_MACHINE_ID=this-machine
rm -rf "$HISTMERSION_DATA_DIR/plugins" "$HISTMERSION_DATA_DIR/toolchain"
dest="$tmp/dest"

new_dest "$dest"; build "$dest" local
check "local (the default): preflight, GraalVM found, relink without a restart" \
  '[ "$ib_rc" = 0 ] && [ "$ib_result" = local ] && [ "$(steps)" = "histmersion-toolchain preflight;histmersion-toolchain path;histmersion-relink --no-restart;" ] && grep -q "# linked native" "$dest/bin/histmersion-server"'
new_dest "$dest"; echo 2 > "$tmp/rc.histmersion-toolchain.path"; build "$dest" local
check "local, no GraalVM yet: provisioned (its size said), then the relink" \
  '[ "$ib_result" = local ] && [ "$(steps)" = "histmersion-toolchain preflight;histmersion-toolchain path;histmersion-toolchain install;histmersion-relink --no-restart;" ] && grep -q "340 MB" "$tmp/ib.out"'
new_dest "$dest"; echo 3 > "$tmp/rc.histmersion-relink.--no-restart"; build "$dest" local
check "local, native-image fails (relink exit 3): the prebuilt binary, the install goes on, the cause said" \
  '[ "$ib_rc" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && grep -q "FALLING BACK to the bundle.s prebuilt binary" "$tmp/ib.out" && grep -q "exit 3" "$tmp/ib.out"'
check "  …and nothing else was tried (no JVM mode)" '[ "$(steps)" = "histmersion-toolchain preflight;histmersion-toolchain path;histmersion-relink --no-restart;" ]'
new_dest "$dest"; echo 2 > "$tmp/rc.histmersion-relink.--no-restart"; build "$dest" local
check "local, the relink's pre-flight or self-check refuses (exit 2): the prebuilt binary" \
  '[ "$ib_rc" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && grep -q "exit 2" "$tmp/ib.out"'
new_dest "$dest"; echo 2 > "$tmp/rc.histmersion-toolchain.path"; echo 2 > "$tmp/rc.histmersion-toolchain.install"; build "$dest" local
check "local, GraalVM cannot be provisioned: the prebuilt binary, no relink" \
  '[ "$ib_rc" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && ! grep -q relink "$tmp/steps" && grep -q "could not be provisioned" "$tmp/ib.out"'
mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo p > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
new_dest "$dest"; echo 3 > "$tmp/rc.histmersion-relink.--no-restart"; build "$dest" local
check "a fallback with plugins installed says the binary does not hold them, and how to link them" \
  '[ "$ib_result" = prebuilt ] && grep -q "does NOT hold your 1 plugin jar" "$tmp/ib.out" && grep -q "histmersion plugin rebuild" "$tmp/ib.out"'
new_dest "$dest"; rm "$dest/bin/histmersion-server"; echo 3 > "$tmp/rc.histmersion-relink.--no-restart"; build "$dest" local
check "no prebuilt binary to fall back to: the step fails" '[ "$ib_rc" != 0 ]'
rm -rf "$HISTMERSION_DATA_DIR/plugins"

# the REAL histmersion-toolchain: its memory, download and checksum verdicts drive the fallback
real_toolchain() { install -m 0755 "$src/toolchain.sh" "$1/bin/histmersion-toolchain"; }
new_dest "$dest"; real_toolchain "$dest"
rc_mem=0; PATH="$fakebin:$PATH" HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) HISTMERSION_GRAALVM_PINS="$tmp/pins" \
  HISTMERSION_HOME="$dest" ib_build "$dest" local > "$tmp/ib.out" 2>&1 || rc_mem=$?
check "8 GB of memory (under HISTMERSION_RELINK_MIN_MEMORY_GB): the prebuilt binary, nothing downloaded or linked" \
  '[ "$rc_mem" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && grep -q "LESS than the 16 GB" "$tmp/ib.out" && [ ! -s "$tmp/steps" ] && [ ! -e "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.4.1.1" ]'
new_dest "$dest"; real_toolchain "$dest"
rc_sum=0; PATH="$fakebin:$PATH" HISTMERSION_RELINK_MEMORY_BYTES=$((48 * 1073741824)) HISTMERSION_GRAALVM_PINS="$tmp/pins-bad" \
  HISTMERSION_HOME="$dest" ib_build "$dest" local > "$tmp/ib.out" 2>&1 || rc_sum=$?
check "the GraalVM download fails its pinned checksum: the prebuilt binary, no relink" \
  '[ "$rc_sum" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && grep -q "SHA-256 mismatch" "$tmp/ib.out" && [ ! -s "$tmp/steps" ]'
new_dest "$dest"; real_toolchain "$dest"
rc_ok=0; PATH="$fakebin:$PATH" HISTMERSION_RELINK_MEMORY_BYTES=$((48 * 1073741824)) HISTMERSION_GRAALVM_PINS="$tmp/pins" \
  HISTMERSION_HOME="$dest" ib_build "$dest" local > "$tmp/ib.out" 2>&1 || rc_ok=$?
check "enough memory, the pinned download good: GraalVM provisioned, then linked" \
  '[ "$rc_ok" = 0 ] && [ "$ib_result" = local ] && [ -x "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.4.1.1/Contents/Home/bin/native-image" ] && [ "$(steps)" = "histmersion-relink --no-restart;" ]'

# nothing linked again when it would give the same binary (the build record)
new_dest "$dest"; build "$dest" local; : > "$tmp/steps"
build "$dest" local
check "reinstalled in place, same kit and plugins: kept, nothing run" '[ "$ib_result" = kept ] && [ ! -s "$tmp/steps" ]'
new_dest "$dest"; . "$src/kit-common.sh"
hk_write_build_record "$dest/bin/histmersion-server.build" native "$dest/kit" "$tmp/no-plugins" bundle
build "$dest" local
check "the bundle's binary was built on this machine from this kit (just install), no plugins: kept" \
  '[ "$ib_result" = kept ] && [ ! -s "$tmp/steps" ] && is_prebuilt "$dest" && grep -q "nothing to build" "$tmp/ib.out"'
HISTMERSION_MACHINE_ID=ci-runner hk_write_build_record "$dest/bin/histmersion-server.build" native "$dest/kit" "$tmp/no-plugins" bundle
build "$dest" local
check "…built on ANOTHER machine (a CI bundle): linked here" '[ "$ib_result" = local ] && grep -q "linked on another machine" "$tmp/ib.out"'
mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo p > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
new_dest "$dest"; hk_write_build_record "$dest/bin/histmersion-server.build" native "$dest/kit" "$tmp/no-plugins" bundle
build "$dest" local
check "…with a plugin jar installed: linked here (the bundle's binary lacks it)" '[ "$ib_result" = local ] && grep -q "linked with other plugins" "$tmp/ib.out"'
prev="$tmp/prev"; new_dest "$prev"; build "$prev" local      # the previous install: linked here, with hello
new_dest "$dest"; build "$dest" local "$prev"
check "a reinstall of the same bundle: the previous install's binary is carried over, nothing linked" \
  '[ "$ib_result" = reused ] && [ ! -s "$tmp/steps" ] && grep -q "# linked native" "$dest/bin/histmersion-server" && cmp -s "$prev/bin/histmersion-server.build" "$dest/bin/histmersion-server.build"'
check "  …and the bundle's own binary is kept as .prev (plugin rollback goes back to it)" 'grep -q "# prebuilt" "$dest/bin/histmersion-server.prev"'
echo changed > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
new_dest "$dest"; build "$dest" local "$prev"
check "a plugin jar changed since: linked again" '[ "$ib_result" = local ] && grep -q "histmersion-relink --no-restart" "$tmp/steps"'
new_dest "$dest"; echo other > "$dest/kit/a.jar"; (cd "$dest/kit" && shasum -a 256 a.jar graalvm-version > SHA256SUMS)
build "$prev" local; : > "$tmp/steps"; build "$dest" local "$prev"
check "a new kit (an update): linked again" '[ "$ib_result" = local ] && grep -q "histmersion-relink --no-restart" "$tmp/steps"'
rm -rf "$HISTMERSION_DATA_DIR/plugins"

# --prebuilt and --jvm
new_dest "$dest"; build "$dest" prebuilt
check "prebuilt: nothing run, the bundle's binary" '[ "$ib_rc" = 0 ] && [ "$ib_result" = prebuilt ] && [ ! -s "$tmp/steps" ] && is_prebuilt "$dest"'
mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo p > "$HISTMERSION_DATA_DIR/plugins/hello-0.1.0.jar"
new_dest "$dest"; build "$dest" prebuilt
check "prebuilt with plugins installed: says they are not in it" '[ "$ib_result" = prebuilt ] && grep -q "does NOT hold the 1 plugin jar" "$tmp/ib.out"'
rm -rf "$HISTMERSION_DATA_DIR/plugins"
new_dest "$dest"; build "$dest" jvm
check "jvm: the JDK found, the relink switches to JVM mode" \
  '[ "$ib_result" = jvm ] && [ "$(steps)" = "histmersion-toolchain path;histmersion-relink --jvm --no-restart;" ]'
new_dest "$dest"; echo 2 > "$tmp/rc.histmersion-toolchain.path"; echo 2 > "$tmp/rc.histmersion-toolchain.install"; build "$dest" jvm
check "jvm, no JDK can be provisioned: the prebuilt binary instead, the install goes on" \
  '[ "$ib_rc" = 0 ] && [ "$ib_result" = prebuilt ] && is_prebuilt "$dest" && [ "$(steps)" = "histmersion-toolchain path;histmersion-toolchain install;" ]'

# the whole installer against fakes (temporary HOME, install and bin dirs; fake service manager and
# health): the default local build runs, and a data directory with NO plugins/ directory does not
# abort it under `set -o pipefail` (regression: cdd16b48)
echo "installer"
ihome="$tmp/ihome"; ibundle="$tmp/ibundle"; ifakes="$tmp/ifakes"
rm -rf "$ihome" "$ibundle" "$ifakes"; new_dest "$ibundle"; mkdir -p "$ihome" "$ifakes" "$ibundle/pg/bin"
printf '#!/bin/sh\nexit 0\n' > "$ibundle/pg/bin/pg_ctl"; printf '#!/bin/sh\nexit 0\n' > "$ibundle/bin/histmersion"
printf '#!/bin/sh\necho "signing $*" >> "%s/steps"\n' "$tmp" > "$ibundle/bin/histmersion-signing"
chmod +x "$ibundle/pg/bin/pg_ctl" "$ibundle/bin/histmersion" "$ibundle/bin/histmersion-signing"
printf '#!/bin/sh\necho "launchctl $*" >> "%s/svc"\ncase "$1" in print) exit 1;; esac\nexit 0\n' "$tmp" > "$ifakes/launchctl"
printf '#!/bin/sh\necho "systemctl $*" >> "%s/svc"\ncase "$*" in *is-active*) exit 3;; esac\nexit 0\n' "$tmp" > "$ifakes/systemctl"
printf '#!/bin/sh\nexit 0\n' > "$ifakes/curl"
cp "$fakebin"/* "$ifakes/"; chmod +x "$ifakes"/*
installer="$src/install-linux.sh"; [ "$(uname -s)" = Darwin ] && installer="$src/install-macos.sh"
irc=0
env -i HOME="$ihome" USER="${USER:-test}" TMPDIR="$tmp" PATH="$ifakes:/usr/bin:/bin:/usr/sbin:/sbin" \
  HISTMERSION_INSTALL_DIR="$ihome/opt/histmersion" HISTMERSION_BIN_DIR="$ihome/bin" HISTMERSION_MACHINE_ID=this-machine \
  bash "$installer" "$ibundle" > "$tmp/install.out" 2>&1 || irc=$?
idata="$ihome/.local/share/histmersion"; [ "$(uname -s)" = Darwin ] && idata="$ihome/Library/Application Support/Histmersion"
check "the installer, no plugins directory, set -o pipefail: completes (exit 0)" \
  '[ "$irc" = 0 ] && [ ! -e "$idata/plugins" ] || { sed "s/^/    /" "$tmp/install.out"; false; }'
check "  …linking the server on this machine by default (preflight, relink), the service started" \
  'grep -q "histmersion-toolchain preflight" "$tmp/steps" && grep -q "histmersion-relink --no-restart" "$tmp/steps" && grep -q "# linked native" "$ihome/opt/histmersion/bin/histmersion-server" && grep -qE "launchctl bootstrap|systemctl --user enable" "$tmp/svc"'
check "  …and the installer explains its options (--help)" \
  'bash "$installer" --help | grep -q -- "--prebuilt takes the bundle.s binary"'
unset HISTMERSION_MACHINE_ID

# ── opt-in: a stable identity from a TEMPORARY keychain ───────────────────────────────────────────
if [ "${HISTMERSION_TEST_KEYCHAIN:-}" = 1 ] && [ "$(uname -s)" = Darwin ]; then
  echo "temporary keychain"
  export HOME="$real_home"
  kc="$tmp/test.keychain-db"; kpw="test-$$"
  security create-keychain -p "$kpw" "$kc"
  security unlock-keychain -p "$kpw" "$kc"
  saved="$(security list-keychains -d user | sed 's/^[[:space:]]*"\(.*\)"$/\1/')"
  restore() { # the user's search list exactly as it was, and the temporary keychain gone
    # shellcheck disable=SC2086
    printf '%s\n' "$saved" | tr '\n' '\0' | xargs -0 security list-keychains -d user -s
    security delete-keychain "$kc" 2>/dev/null || true
  }
  trap 'restore; cleanup' EXIT
  HISTMERSION_KEYCHAIN="$kc" "$src/codesign-identity-macos.sh" "Histmersion Test Identity" > "$tmp/k.out"
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$kpw" "$kc" >/dev/null 2>&1
  printf '%s\n' "$saved" "$kc" | tr '\n' '\0' | xargs -0 security list-keychains -d user -s
  cp /usr/bin/true "$tmp/b1"; cp /usr/bin/false "$tmp/b2"
  for b in b1 b2; do
    HISTMERSION_CODESIGN_IDENTITY="Histmersion Test Identity" HISTMERSION_CODESIGN_KEYCHAIN="$kc" \
      "$signing" codesign "$tmp/$b" com.histmersion.server > "$tmp/$b.out"
  done
  dr() { codesign -d -r- "$1" 2>&1 | sed -n 's/^\(# \)\{0,1\}designated => //p'; }
  cdhash() { codesign -dvvv "$1" 2>&1 | sed -n 's/^CDHash=//p'; }
  dr1="$(dr "$tmp/b1")"; dr2="$(dr "$tmp/b2")"
  check "signed with the untrusted identity (the default since #3647: no trust setting)" \
    'grep -q "signed as \"Histmersion Test Identity\"" "$tmp/b1.out" && ! grep -q "trusting it" "$tmp/k.out"'
  check "two different binaries (two cdhashes)" '[ -n "$(cdhash "$tmp/b1")" ] && [ "$(cdhash "$tmp/b1")" != "$(cdhash "$tmp/b2")" ]'
  check "the designated requirement is the same for both (the identifier and the certificate, not a cdhash)" \
    '[ -n "$dr1" ] && [ "$dr1" = "$dr2" ] && echo "$dr1" | grep -q "identifier \"com.histmersion.server\" and certificate leaf = H" && ! echo "$dr1" | grep -q cdhash'
  check "each binary satisfies the OTHER's designated requirement (what a TCC grant re-checks after a relink)" \
    'codesign --verify -R "=$dr1" "$tmp/b2" 2>/dev/null && codesign --verify -R "=$dr2" "$tmp/b1" 2>/dev/null'
  cp /usr/bin/true "$tmp/a1"; cp /usr/bin/false "$tmp/a2"
  for b in a1 a2; do HISTMERSION_CODESIGN_IDENTITY=- "$signing" codesign "$tmp/$b" com.histmersion.server > /dev/null; done
  check "control: signed ad hoc, the two binaries' requirements differ (cdhash) and neither satisfies the identity's" \
    '[ "$(dr "$tmp/a1")" != "$(dr "$tmp/a2")" ] && dr "$tmp/a1" | grep -q cdhash && ! codesign --verify -R "=$dr1" "$tmp/a1" 2>/dev/null && ! codesign --verify -R "=$(dr "$tmp/a1")" "$tmp/a2" 2>/dev/null'
  # tcc-check.sh's default: a NEW untrusted identity in ITS temporary keychain (the launchd stand-in)
  if [ -x "$tmp/tcc-fakes/launchctl" ]; then
    mkdir -p "$tmp/tcc-lfakes"; cp "$tmp/tcc-fakes/launchctl" "$tmp/tcc-lfakes/"; rm -f "$tmp/tcc.loaded"
    tcck() { PATH="$tmp/tcc-lfakes:$PATH" HISTMERSION_TCC_DIR="$tmp/tcc-work" HISTMERSION_TCC_AGENTS="$tmp/tcc-agents" \
      HISTMERSION_TCC_TARGET="$tmp/tcc-target" HISTMERSION_TCC_WAIT=5 "$src/tcc-check.sh" "$@"; }
    tcck start > "$tmp/tcc.out" 2>&1; d1="$(dr "$tmp/tcc-work/histmersion-tcc-probe")"
    tcck relink > "$tmp/tcc2.out" 2>&1; d2="$(dr "$tmp/tcc-work/histmersion-tcc-probe")"
    check "tcc-check start/relink: an untrusted identity in its own temporary keychain; one certificate requirement for both probes" \
      'grep -q "variant=1 ALLOWED" "$tmp/tcc.out" && grep -q "variant=2 ALLOWED" "$tmp/tcc2.out" && [ "$d1" = "$d2" ] && echo "$d1" | grep -q "certificate leaf" && ! grep -q "trusting it" "$tmp/tcc.out"'
    check "  …the search list as it was after each signing" \
      '! grep -q "WARNING" "$tmp/tcc.out" "$tmp/tcc2.out" && ! security list-keychains -d user | grep -q tcc-check'
    tcck cleanup > /dev/null 2>&1
    check "  …and cleanup deleted its keychain" '[ ! -e "$tmp/tcc-work" ]'
  fi
  restore
  trap cleanup EXIT
  check "the user's keychain search list is restored exactly" \
    '[ "$(security list-keychains -d user | sed "s/^[[:space:]]*\"\(.*\)\"$/\1/")" = "$saved" ]'
  check "the temporary keychain is gone" '[ ! -e "$kc" ]'
  export HOME="$tmp/home"
else
  skip "temporary-keychain code signing (HISTMERSION_TEST_KEYCHAIN=1 on macOS)"
fi

echo "$passed passed, $failed failed, $skipped skipped"
[ "$failed" -eq 0 ]
