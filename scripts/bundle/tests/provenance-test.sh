#!/usr/bin/env bash
# Tests of GPG provenance AT EACH START (docs/research-plugins.md §21.3) in a temp dir — nothing outside it:
#   - libexec/histmersion-server-verified (server-verified.sh): with provenance off it execs the binary as
#     before; on, it execs only a binary whose SHA-256 is the one its checksum record carries, signed by
#     the configured key — with the REAL gpg in a temporary GNUPGHOME (throw-away keys without a
#     passphrase) and a LaunchAgent-like environment (env -i, PATH=/usr/bin:/bin, no GNUPGHOME)
#   - histmersion-signing use-gpg / off-gpg / sign-binary / verify-binary / status
#   - pointing the service at the launcher and back: a temporary plist (macOS, plutil) and systemd unit,
#     with FAKE launchctl / systemctl
#   - the WHOLE installer of this platform with provenance on and off (temporary HOME and install, fake
#     launchctl/systemctl/curl/codesign, --prebuilt): what the service definition starts
# gpg missing: the gpg-dependent checks are skipped with a note.
#
#   scripts/bundle/tests/provenance-test.sh        (also: just test-scripts)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/.."

tmp="$(mktemp -d)"
gnupg_homes=()
cleanup() {
  local h
  for h in ${gnupg_homes[@]+"${gnupg_homes[@]}"}; do gpgconf --homedir "$h" --kill all >/dev/null 2>&1 || true; done
  rm -rf "$tmp"
}
trap cleanup EXIT
export HOME="$tmp/home"
mkdir -p "$HOME"
unset GNUPGHOME HISTMERSION_GPG HISTMERSION_HOME 2>/dev/null || true
export HISTMERSION_DATA_DIR="$tmp/data" HISTMERSION_SERVICE=none

passed=0; failed=0; skipped=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1"; }
skip() { skipped=$((skipped + 1)); echo "  skip $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }  # <what> <shell test>

# an install: a fake server that records how it was started, the real scripts
install="$tmp/install"
new_install() {
  rm -rf "$install" && mkdir -p "$install/bin" "$install/libexec"
  cat > "$install/bin/histmersion-server" <<SH
#!/bin/bash
{ echo "argv0=\$0"; echo "ppid=\$PPID"; printf 'arg=%s\n' "\$@"; } > "$tmp/server.ran"
SH
  chmod +x "$install/bin/histmersion-server"
  install -m 0755 "$src/signing.sh" "$install/bin/histmersion-signing"
  install -m 0644 "$src/kit-common.sh" "$install/libexec/kit-common.sh"
  install -m 0755 "$src/server-verified.sh" "$install/libexec/histmersion-server-verified"
  rm -f "$tmp/server.ran"
}
launcher="$install/libexec/histmersion-server-verified"
signing="$install/bin/histmersion-signing"
# shellcheck source=../kit-common.sh
. "$src/kit-common.sh"

# ── provenance off: nothing changes ───────────────────────────────────────────────────────────────
echo "provenance off"
new_install
check "the service starts the binary itself" '[ "$(hk_service_program "$install")" = "$install/bin/histmersion-server" ]'
rc=0; "$launcher" --self-check x > "$tmp/l.out" 2>&1 || rc=$?
check "the launcher (if the service still points at it) execs the binary with its arguments, unverified, and says so" \
  '[ "$rc" = 0 ] && grep -qx "arg=--self-check" "$tmp/server.ran" && grep -qx "arg=x" "$tmp/server.ran" && grep -q "provenance is off" "$tmp/l.out"'

if ! command -v gpg >/dev/null 2>&1; then
  skip "GPG provenance at start (gpg is not installed)"
  echo "$passed passed, $failed failed, $skipped skipped"
  [ "$failed" -eq 0 ]; exit
fi

new_gnupg() { # <dir> <name> → the new key's fingerprint
  mkdir -m 700 "$1"; gnupg_homes+=("$1")
  GNUPGHOME="$1" gpg --batch --pinentry-mode loopback --passphrase '' --quick-generate-key "$2 <test@localhost>" ed25519 sign 1d >/dev/null 2>&1
  GNUPGHOME="$1" gpg --batch --with-colons --list-secret-keys 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}'
}
gh="$tmp/gnupg"; fpr="$(new_gnupg "$gh" "Histmersion Test")"
gpg_abs="$(command -v gpg)"
# what a LaunchAgent gives its program: no GNUPGHOME, a PATH without Homebrew or MacGPG2
as_service() { env -i HOME="$HOME" PATH=/usr/bin:/bin:/usr/sbin:/sbin HISTMERSION_DATA_DIR="$HISTMERSION_DATA_DIR" "$@"; }

# ── use-gpg ───────────────────────────────────────────────────────────────────────────────────────
echo "use-gpg"
new_install
GNUPGHOME="$gh" "$signing" use-gpg "$fpr" > "$tmp/u.out" 2>&1
check "use-gpg keeps the key, the gpg program and the GnuPG home it ran with (nothing secret)" \
  '[ "$(cat "$HISTMERSION_DATA_DIR/signing/gpg-key")" = "$fpr" ] && [ "$(cat "$HISTMERSION_DATA_DIR/signing/gpg-program")" = "$gpg_abs" ] && [ "$(cat "$HISTMERSION_DATA_DIR/signing/gnupg-home")" = "$gh" ] && [ -z "$(grep -rl "PRIVATE KEY" "$HISTMERSION_DATA_DIR" || true)" ]'
check "use-gpg signs the installed binary's SHA-256" \
  '[ -f "$install/bin/histmersion-server.sha256.asc" ] && grep -q "^$(hk_sha256 "$install/bin/histmersion-server")  histmersion-server$" "$install/bin/histmersion-server.sha256"'
check "…and says there is no service to point (HISTMERSION_SERVICE=none)" 'grep -q "service: none installed" "$tmp/u.out"'
check "with provenance on, the service is to start the launcher" '[ "$(hk_service_program "$install")" = "$launcher" ]'

# ── the launcher at a start ───────────────────────────────────────────────────────────────────────
echo "launcher"
rc=0; as_service "$launcher" --self-check > "$tmp/l.out" 2>&1 || rc=$?
check "a LaunchAgent-like start (no GNUPGHOME, no gpg on PATH): verified with the recorded gpg and keyring, then exec'd" \
  '[ "$rc" = 0 ] && grep -q "verified histmersion-server (SHA-256 .*) signed by $fpr" "$tmp/l.out" && grep -qx "arg=--self-check" "$tmp/server.ran"'
rm -f "$tmp/server.ran"
"$launcher" a b > "$tmp/l.out" 2>&1
check "exec: the server replaces the launcher (its parent is the caller, argv[0] the binary)" \
  'grep -qx "ppid=$$" "$tmp/server.ran" && grep -qx "argv0=$install/bin/histmersion-server" "$tmp/server.ran"'

cp "$install/bin/histmersion-server" "$tmp/good-server"
echo "# changed" >> "$install/bin/histmersion-server"; rm -f "$tmp/server.ran"
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "a binary changed since it was signed: refused (exit 78), not run, why and what to do logged" \
  '[ "$rc" = 78 ] && [ ! -e "$tmp/server.ran" ] && grep -q "REFUSING to start" "$tmp/l.out" && grep -q "changed since it was signed" "$tmp/l.out" && grep -q "sign-binary" "$tmp/l.out"'
rc=0; "$signing" verify-binary > "$tmp/v.out" 2>&1 || rc=$?
check "verify-binary says the same (exit 2)" '[ "$rc" = 2 ] && grep -q "NOT VERIFIED" "$tmp/v.out"'
"$signing" sign-binary > /dev/null
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "sign-binary (you vouch for it): the launcher starts it again" '[ "$rc" = 0 ] && [ -e "$tmp/server.ran" ]'
"$signing" sign-binary > "$tmp/s.out"
check "sign-binary on a binary already signed: nothing re-signed" 'grep -q "its signed checksum is current" "$tmp/s.out"'

mv "$install/bin/histmersion-server.sha256.asc" "$tmp/asc"; rm -f "$tmp/server.ran"
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "no signature: refused (exit 78), not run" '[ "$rc" = 78 ] && [ ! -e "$tmp/server.ran" ] && grep -q "no signed checksum" "$tmp/l.out"'
mv "$tmp/asc" "$install/bin/histmersion-server.sha256.asc"

gh2="$tmp/gnupg-other"; fpr2="$(new_gnupg "$gh2" "Someone Else")"
GNUPGHOME="$gh2" gpg --batch --export "$fpr2" | GNUPGHOME="$gh" gpg --batch --import >/dev/null 2>&1
GNUPGHOME="$gh2" gpg --batch --yes --armor --local-user "$fpr2" --detach-sign --output "$install/bin/histmersion-server.sha256.asc" "$install/bin/histmersion-server.sha256"
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "a good signature by ANOTHER key (in the keyring): refused (exit 78)" '[ "$rc" = 78 ] && grep -q "not by the configured key $fpr" "$tmp/l.out"'

printf '%s  histmersion-server\n' "0000000000000000000000000000000000000000000000000000000000000000" > "$install/bin/histmersion-server.sha256"
GNUPGHOME="$gh" gpg --batch --yes --armor --local-user "$fpr" --detach-sign --output "$install/bin/histmersion-server.sha256.asc" "$install/bin/histmersion-server.sha256"
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "a validly signed checksum of ANOTHER binary: refused (exit 78)" '[ "$rc" = 78 ] && grep -q "changed since it was signed" "$tmp/l.out"'
echo "tampered" >> "$install/bin/histmersion-server.sha256"
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "a checksum record changed after signing: refused (exit 78)" '[ "$rc" = 78 ] && grep -q "not a valid signature" "$tmp/l.out"'
"$signing" sign-binary > /dev/null

empty="$tmp/gnupg-empty"; mkdir -m 700 "$empty"; gnupg_homes+=("$empty")
rc=0; as_service env GNUPGHOME="$empty" "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "the signer's key is not in the keyring the service sees: refused (exit 78), the key named" \
  '[ "$rc" = 78 ] && grep -q "public key .* is not in the keyring" "$tmp/l.out"'
rc=0; as_service env HISTMERSION_GPG="$tmp/no-gpg" "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "no gpg to verify with: refused (exit 78), never started unverified" '[ "$rc" = 78 ] && grep -q "REFUSING" "$tmp/l.out"'

# .prev travels with its own signed record (relink.sh's names)
cp "$tmp/good-server" "$install/bin/histmersion-server.prev"
"$signing" sign-binary > "$tmp/s.out"
check "sign-binary signs .prev too, under the names its records rotate to" \
  '[ -f "$install/bin/histmersion-server.sha256.prev" ] && [ -f "$install/bin/histmersion-server.sha256.asc.prev" ] && hk_verify_binary "$install/bin/histmersion-server.prev" "$install/bin/histmersion-server.sha256.prev" "$install/bin/histmersion-server.sha256.asc.prev" "$fpr" >/dev/null'
check "verify-binary: both verified (exit 0)" '"$signing" verify-binary > "$tmp/v.out" && [ "$(grep -c "^verified" "$tmp/v.out")" = 2 ]'

# a key that certifies with its primary and signs with a SUBKEY (a common layout): VALIDSIG names the
# subkey first, use-gpg stores the primary's fingerprint — both checks must accept it
gh3="$tmp/gnupg-subkey"; mkdir -m 700 "$gh3"; gnupg_homes+=("$gh3")
GNUPGHOME="$gh3" gpg --batch --pinentry-mode loopback --passphrase '' --quick-generate-key "Subkey Signer <test@localhost>" ed25519 cert 1d >/dev/null 2>&1
fpr3="$(GNUPGHOME="$gh3" gpg --batch --with-colons --list-secret-keys 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')"
GNUPGHOME="$gh3" gpg --batch --pinentry-mode loopback --passphrase '' --quick-add-key "$fpr3" ed25519 sign 1d >/dev/null 2>&1
new_install
mkdir -p "$HISTMERSION_DATA_DIR/plugins"; echo jar > "$HISTMERSION_DATA_DIR/plugins/a-1.0.jar"
GNUPGHOME="$gh3" "$signing" use-gpg "$fpr3" > "$tmp/u.out" 2>&1
rc=0; as_service "$launcher" > "$tmp/l.out" 2>&1 || rc=$?
check "a key that signs with a subkey: the launcher accepts its primary's fingerprint" '[ "$rc" = 0 ] && grep -q "signed by $fpr3" "$tmp/l.out"'
check "  …and so does verify-plugins" '"$signing" verify-plugins > /dev/null'
check "  …while verify-file still names the signing subkey (what a plugin's pinned signer holds)" \
  '[ -n "$("$signing" verify-file "$install/bin/histmersion-server.sha256" "$install/bin/histmersion-server.sha256.asc")" ] && [ "$("$signing" verify-file "$install/bin/histmersion-server.sha256" "$install/bin/histmersion-server.sha256.asc")" != "$fpr3" ]'
rm -rf "$HISTMERSION_DATA_DIR/plugins"
GNUPGHOME="$gh" "$signing" use-gpg "$fpr" > /dev/null 2>&1

# ── pointing the service at the launcher, and back ────────────────────────────────────────────────
echo "service definition"
fakes="$tmp/fakes"; mkdir -p "$fakes"
cat > "$fakes/launchctl" <<SH
#!/bin/sh
echo "launchctl \$*" >> "$tmp/svc"
exit 0
SH
cat > "$fakes/systemctl" <<SH
#!/bin/sh
echo "systemctl \$*" >> "$tmp/svc"
exit 0
SH
chmod +x "$fakes"/*
svc() { tr '\n' ';' < "$tmp/svc" 2>/dev/null || true; }
write_unit() { # <file> <program>
  printf '[Unit]\nDescription=Histmersion\n\n[Service]\nExecStart=%s\nRestart=on-failure\n' "$2" > "$1"
}
unit="$tmp/histmersion.service"
write_unit "$unit" "$install/bin/histmersion-server"
sysd() { PATH="$fakes:$PATH" HISTMERSION_SERVICE=systemd HISTMERSION_SERVICE_FILE="$unit" "$@"; }
: > "$tmp/svc"; sysd "$signing" use-gpg "$fpr" > "$tmp/u.out" 2>&1
check "systemd: use-gpg points ExecStart at the launcher, reloads and restarts the running unit" \
  'grep -qx "ExecStart=$launcher" "$unit" && grep -qx "Restart=on-failure" "$unit" && [ "$(svc)" = "systemctl --user daemon-reload;systemctl --user is-active --quiet histmersion.service;systemctl --user restart histmersion.service;" ]'
check "  status says the service verifies at start" 'sysd "$signing" status | grep -q "service start: through histmersion-server-verified"'
: > "$tmp/svc"; sysd "$signing" use-gpg "$fpr" > "$tmp/u.out" 2>&1
check "  use-gpg again: already pointed, nothing restarted" 'grep -q "already starts" "$tmp/u.out" && [ -z "$(svc)" ]'
: > "$tmp/svc"; sysd "$signing" off-gpg --no-restart > "$tmp/u.out" 2>&1
check "systemd: off-gpg --no-restart points it back at the binary, daemon-reload only" \
  'grep -qx "ExecStart=$install/bin/histmersion-server" "$unit" && [ "$(svc)" = "systemctl --user daemon-reload;" ] && [ ! -e "$HISTMERSION_DATA_DIR/signing/gpg-key" ]'
check "  status after off-gpg: GPG off, the binary started directly" \
  'sysd "$signing" status > "$tmp/st.out" && grep -q "GPG provenance: off" "$tmp/st.out" && grep -q "service start: $install/bin/histmersion-server directly" "$tmp/st.out"'
write_unit "$unit" "/elsewhere/bin/histmersion-server"
: > "$tmp/svc"; GNUPGHOME="$gh" sysd "$signing" use-gpg "$fpr" > "$tmp/u.out" 2>&1
check "a unit that starts ANOTHER install's server is left alone" \
  'grep -qx "ExecStart=/elsewhere/bin/histmersion-server" "$unit" && grep -q "not this install" "$tmp/u.out" && [ -z "$(svc)" ]'
GNUPGHOME="$gh" "$signing" off-gpg > /dev/null

if [ "$(uname -s)" = Darwin ]; then
  plist="$tmp/test.plist"
  cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>test.histmersion.provenance</string>
  <key>ProgramArguments</key><array><string>$install/bin/histmersion-server</string></array>
  <key>EnvironmentVariables</key><dict><key>HISTMERSION_CORPUS_DIR</key><string>/corpus</string></dict>
</dict></plist>
PLIST
  la() { PATH="$fakes:$PATH" HISTMERSION_SERVICE=launchd HISTMERSION_SERVICE_FILE="$plist" HISTMERSION_SERVICE_LABEL=test.histmersion.provenance "$@"; }
  : > "$tmp/svc"; GNUPGHOME="$gh" la "$signing" use-gpg "$fpr" > "$tmp/u.out" 2>&1
  check "launchd: use-gpg points ProgramArguments at the launcher, the rest of the plist kept" \
    '[ "$(plutil -extract ProgramArguments.0 raw -o - "$plist")" = "$launcher" ] && [ "$(plutil -extract EnvironmentVariables.HISTMERSION_CORPUS_DIR raw -o - "$plist")" = /corpus ]'
  check "  …and reloads the loaded LaunchAgent (bootout, bootstrap) so it applies now" \
    '[ "$(svc)" = "launchctl print gui/$(id -u)/test.histmersion.provenance;launchctl bootout gui/$(id -u)/test.histmersion.provenance;launchctl bootstrap gui/$(id -u) $plist;" ]'
  : > "$tmp/svc"; la "$signing" off-gpg --no-restart > "$tmp/u.out" 2>&1
  check "launchd: off-gpg --no-restart points it back, launchctl not called" \
    '[ "$(plutil -extract ProgramArguments.0 raw -o - "$plist")" = "$install/bin/histmersion-server" ] && [ -z "$(svc)" ]'
fi

# ── the installer: what the service starts ────────────────────────────────────────────────────────
echo "installer"
ibundle="$tmp/ibundle"; ihome="$tmp/ihome"; ifakes="$tmp/ifakes"
rm -rf "$ibundle" && mkdir -p "$ibundle/bin" "$ibundle/libexec" "$ibundle/pg/bin" "$ifakes"
cp "$tmp/good-server" "$ibundle/bin/histmersion-server"
printf '#!/bin/sh\nexit 0\n' > "$ibundle/pg/bin/pg_ctl"; printf '#!/bin/sh\nexit 0\n' > "$ibundle/bin/histmersion"
install -m 0755 "$src/signing.sh" "$ibundle/bin/histmersion-signing"
install -m 0644 "$src/kit-common.sh" "$ibundle/libexec/kit-common.sh"
install -m 0755 "$src/server-verified.sh" "$ibundle/libexec/histmersion-server-verified"
chmod +x "$ibundle/pg/bin/pg_ctl" "$ibundle/bin/histmersion"
printf '#!/bin/sh\necho "launchctl $*" >> "%s/isvc"\ncase "$1" in print) exit 1;; esac\nexit 0\n' "$tmp" > "$ifakes/launchctl"
printf '#!/bin/sh\necho "systemctl $*" >> "%s/isvc"\ncase "$*" in *is-active*) exit 3;; esac\nexit 0\n' "$tmp" > "$ifakes/systemctl"
printf '#!/bin/sh\nexit 0\n' > "$ifakes/curl"
printf '#!/bin/sh\necho "codesign $*" >> "%s/isvc"\nexit 0\n' "$tmp" > "$ifakes/codesign"   # never the keychain
chmod +x "$ifakes"/*
installer="$src/install-linux.sh"; [ "$(uname -s)" = Darwin ] && installer="$src/install-macos.sh"
idest="$ihome/opt/histmersion"
idata="$ihome/.local/share/histmersion"; [ "$(uname -s)" = Darwin ] && idata="$ihome/Library/Application Support/Histmersion"
run_installer() { # → exit code in $irc, output in $tmp/install.out
  irc=0
  env -i HOME="$ihome" USER="${USER:-test}" TMPDIR="$tmp" PATH="$ifakes:/usr/bin:/bin:/usr/sbin:/sbin" \
    HISTMERSION_INSTALL_DIR="$idest" HISTMERSION_BIN_DIR="$ihome/bin" HISTMERSION_CODESIGN_IDENTITY=- \
    bash "$installer" "$ibundle" --prebuilt > "$tmp/install.out" 2>&1 || irc=$?
}
service_program() {
  if [ "$(uname -s)" = Darwin ]; then plutil -extract ProgramArguments.0 raw -o - "$ihome/Library/LaunchAgents/com.histmersion.server.plist"
  else sed -n 's/^ExecStart=//p' "$ihome/.config/systemd/user/histmersion.service"; fi
}
mkdir -p "$ihome"
run_installer
check "provenance off: the service starts bin/histmersion-server, as before" \
  '[ "$irc" = 0 ] && [ "$(service_program)" = "$idest/bin/histmersion-server" ] && [ ! -e "$idest/bin/histmersion-server.sha256" ] || { sed "s/^/    /" "$tmp/install.out"; false; }'
HISTMERSION_DATA_DIR="$idata" GNUPGHOME="$gh" bash "$src/signing.sh" use-gpg "$fpr" > /dev/null
run_installer
check "provenance on: the installed binary's SHA-256 signed, the service starts the launcher" \
  '[ "$irc" = 0 ] && [ "$(service_program)" = "$idest/libexec/histmersion-server-verified" ] && HISTMERSION_HOME="$idest" HISTMERSION_DATA_DIR="$idata" "$idest/bin/histmersion-signing" verify-binary >/dev/null || { sed "s/^/    /" "$tmp/install.out"; false; }'
rc=0; env -i HOME="$ihome" PATH=/usr/bin:/bin HISTMERSION_DATA_DIR="$idata" "$idest/libexec/histmersion-server-verified" > "$tmp/l.out" 2>&1 || rc=$?
check "  …and that launcher starts the installed binary" '[ "$rc" = 0 ] && grep -q "verified histmersion-server" "$tmp/l.out"'
printf '%s\n' "$tmp/no-such-gpg" > "$idata/signing/gpg-program"; mv "$idata/signing/gnupg-home" "$tmp/gh-record"
run_installer
check "provenance on but the binary cannot be signed: the service starts the binary, with a WARNING and the fix" \
  '[ "$irc" = 0 ] && [ "$(service_program)" = "$idest/bin/histmersion-server" ] && grep -q "WARNING: the server binary.s checksum could not be signed" "$tmp/install.out" && grep -q "histmersion-signing use-gpg $fpr" "$tmp/install.out" || { sed "s/^/    /" "$tmp/install.out"; false; }'

echo "$passed passed, $failed failed, $skipped skipped"
[ "$failed" -eq 0 ]
