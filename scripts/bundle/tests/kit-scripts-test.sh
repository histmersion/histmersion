#!/usr/bin/env bash
# Tests of the kit's other scripts in a temp dir — nothing outside it:
#   - histmersion-server-jvm (server-jvm.sh): the class path, the environment, provenance, with a FAKE java
#   - histmersion-toolchain (toolchain.sh): provisioning GraalVM from a pins file — a FAKE GraalVM tarball
#     served from file://, its SHA-256 checked; check's verdicts
#   - histmersion-signing (signing.sh): GPG provenance with the REAL gpg in a temporary GNUPGHOME (a
#     throw-away key without a passphrase; skipped with a note when gpg is not installed), and ad-hoc
#     code signing of a copy of /usr/bin/true (macOS)
#   - with HISTMERSION_TEST_KEYCHAIN=1 (macOS, opt-in): a self-signed identity in a TEMPORARY keychain,
#     codesign with it, the designated requirement stable across two binaries; the user's keychain
#     search list is saved, the temporary keychain appended for the signing, the list restored and
#     compared, the keychain deleted. The login keychain is never read or changed.
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
echo 25.0.1 > "$install/kit/graalvm-version"
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
fake="$tmp/fake-graal/graalvm-community-openjdk-25.0.1+8.1"
mkdir -p "$fake/Contents/Home/bin"
echo 'JAVA_VERSION="25.0.1"' > "$fake/Contents/Home/release"
printf '#!/bin/sh\necho fake\n' > "$fake/Contents/Home/bin/native-image"; cp "$fake/Contents/Home/bin/native-image" "$fake/Contents/Home/bin/java"
chmod +x "$fake/Contents/Home/bin/"*
tar -czf "$tmp/graal.tar.gz" -C "$tmp/fake-graal" "graalvm-community-openjdk-25.0.1+8.1"
sum="$(shasum -a 256 "$tmp/graal.tar.gz" | awk '{print $1}')"
export HISTMERSION_TOOLCHAIN_PLATFORM=test-arch
printf '# a comment\n25.0.1 test-arch %s file://%s\n' "0000000000000000000000000000000000000000000000000000000000000000" "$tmp/graal.tar.gz" > "$tmp/pins-bad"
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins-bad" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "a checksum mismatch: refused (exit 2), nothing unpacked, no download left" \
  '[ "$rc" = 2 ] && grep -q "SHA-256 mismatch" "$tmp/t.out" && [ ! -e "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.1" ] && [ -z "$(ls -A "$HISTMERSION_DATA_DIR/toolchain" 2>/dev/null | grep -v "^\.lock$" || true)" ]'
printf '25.0.1 test-arch %s file://%s\n' "$sum" "$tmp/graal.tar.gz" > "$tmp/pins"
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "the pinned archive is unpacked into the data directory" '[ "$rc" = 0 ] && [ -x "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.1/Contents/Home/bin/native-image" ]'
check "the licence is named on the way" 'grep -q "Classpath Exception" "$tmp/t.out"'
check "path: the provisioned home (macOS layout: Contents/Home)" \
  '[ "$("$install/bin/histmersion-toolchain" path)" = "$HISTMERSION_DATA_DIR/toolchain/graalvm-ce-25.0.1/Contents/Home" ]'
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "install again: already provisioned, nothing downloaded" '[ "$rc" = 0 ] && grep -q "already provisioned" "$tmp/t.out"'
printf '25.0.1 other-arch %s file://%s\n' "$sum" "$tmp/graal.tar.gz" > "$tmp/pins-other"
"$install/bin/histmersion-toolchain" remove > /dev/null
rc=0; HISTMERSION_GRAALVM_PINS="$tmp/pins-other" "$install/bin/histmersion-toolchain" install > "$tmp/t.out" 2>&1 || rc=$?
check "no pin for this platform: refused (exit 2)" '[ "$rc" = 2 ] && grep -q "no pinned GraalVM" "$tmp/t.out"'
rc=0; HISTMERSION_RELINK_MEMORY_BYTES=$((8 * 1073741824)) "$install/bin/histmersion-toolchain" check > "$tmp/t.out" 2>&1 || rc=$?
check "check: no GraalVM and 8 GB → cannot relink (exit 2), JVM mode named" \
  '[ "$rc" = 2 ] && grep -q "GraalVM CE 25.0.1: MISSING" "$tmp/t.out" && grep -q "LESS than" "$tmp/t.out" && grep -q "JVM mode" "$tmp/t.out"'
check "the repo's pins name every release platform with a 64-hex SHA-256 and the official URL" \
  '[ "$(grep -cE "^25\.0\.1 (macos-aarch64|macos-x64|linux-x64|linux-aarch64) [0-9a-f]{64} https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25\.0\.1/" "$src/graalvm-pins.txt")" = 4 ]'
check "the pins follow .sdkmanrc" 'grep -q "^java=$(awk "!/^#/ {print \$1; exit}" "$src/graalvm-pins.txt")-graalce" "$src/../../.sdkmanrc"'

# ── histmersion-signing ───────────────────────────────────────────────────────────────────────────
echo "signing"
signing="$install/bin/histmersion-signing"
out="$("$signing" status)"
check "status with nothing set up: GPG off" 'echo "$out" | grep -q "GPG provenance: off"'
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

# ── the installers' build step (install-build.sh) ─────────────────────────────────────────────────
echo "install-build"
# shellcheck source=../install-build.sh
. "$src/install-build.sh"
check "mode: prebuilt by default, local when plugins exist, as asked otherwise" \
  '[ "$(ib_mode "" 0)" = prebuilt ] && [ "$(ib_mode "" 2)" = local ] && [ "$(ib_mode jvm 2)" = jvm ] && [ "$(ib_mode prebuilt 3)" = prebuilt ]'
check "mode: an unknown one is refused" '! ib_mode fast 0 >/dev/null 2>&1'
dest="$tmp/dest"; mkdir -p "$dest/bin"
fake_step() { # <name> <exit code> — records its call
  printf '#!/bin/sh
echo "%s $*" >> "%s/steps"
exit %s
' "$1" "$tmp" "$2" > "$dest/bin/$1"; chmod +x "$dest/bin/$1"
}
steps() { tr '\n' ';' < "$tmp/steps"; }
fake_step histmersion-toolchain 0; fake_step histmersion-relink 0; : > "$tmp/steps"
ib_build "$dest" prebuilt > /dev/null
check "prebuilt: nothing run" '[ ! -s "$tmp/steps" ]'
ib_build "$dest" local > /dev/null
check "local: toolchain, check, relink without a restart" \
  '[ "$(steps)" = "histmersion-toolchain path;histmersion-toolchain check;histmersion-relink --no-restart;" ]'
: > "$tmp/steps"; fake_step histmersion-relink 3
printf '#!/bin/sh
echo "histmersion-relink $*" >> "%s/steps"
case "$*" in *--jvm*) exit 0;; esac
exit 3
' "$tmp" > "$dest/bin/histmersion-relink"
ib_build "$dest" local > "$tmp/ib.out"
check "local, the link fails: JVM mode instead" \
  '[ "$(steps)" = "histmersion-toolchain path;histmersion-toolchain check;histmersion-relink --no-restart;histmersion-relink --jvm --no-restart;" ] && grep -q "falling back to JVM mode" "$tmp/ib.out"'
: > "$tmp/steps"
printf '#!/bin/sh
echo "histmersion-toolchain $*" >> "%s/steps"
case "$1" in path) exit 2;; install) exit 2;; esac
' "$tmp" > "$dest/bin/histmersion-toolchain"
ib_build "$dest" local > /dev/null
check "local, no GraalVM can be provisioned: JVM mode attempted, no link" \
  '[ "$(steps)" = "histmersion-toolchain path;histmersion-toolchain install;histmersion-relink --jvm --no-restart;" ]'
: > "$tmp/steps"
ib_build "$dest" jvm > /dev/null && rc=0 || rc=$?
check "jvm: no JDK can be provisioned → the step fails" '[ "$rc" != 0 ] && [ "$(steps)" = "histmersion-toolchain path;histmersion-toolchain install;" ]'

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
  HISTMERSION_KEYCHAIN="$kc" "$src/codesign-identity-macos.sh" --no-trust "Histmersion Test Identity" > "$tmp/k.out"
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$kpw" "$kc" >/dev/null 2>&1
  printf '%s\n' "$saved" "$kc" | tr '\n' '\0' | xargs -0 security list-keychains -d user -s
  cp /usr/bin/true "$tmp/b1"; cp /usr/bin/false "$tmp/b2"
  for b in b1 b2; do
    HISTMERSION_CODESIGN_IDENTITY="Histmersion Test Identity" HISTMERSION_CODESIGN_KEYCHAIN="$kc" \
      "$signing" codesign "$tmp/$b" com.histmersion.server > "$tmp/$b.out"
  done
  dr1="$(codesign -d -r- "$tmp/b1" 2>&1 | sed -n 's/^designated => //p')"
  dr2="$(codesign -d -r- "$tmp/b2" 2>&1 | sed -n 's/^designated => //p')"
  check "signed with the untrusted identity" 'grep -q "signed as \"Histmersion Test Identity\"" "$tmp/b1.out"'
  check "the designated requirement is the same for two different binaries (a certificate, not a cdhash)" \
    '[ -n "$dr1" ] && [ "$dr1" = "$dr2" ] && echo "$dr1" | grep -q "certificate leaf"'
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
