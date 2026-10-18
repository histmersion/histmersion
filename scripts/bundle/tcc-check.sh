#!/usr/bin/env bash
# Does a macOS privacy permission (TCC) survive a relink when the binary is signed by an UNTRUSTED
# self-signed identity? (docs/research-plugins.md §9.2, §21.2.) The automated proxy
# (scripts/bundle/tests/kit-scripts-test.sh with HISTMERSION_TEST_KEYCHAIN=1) shows two such binaries
# share one designated requirement and satisfy each other's; whether TCC honours that for an untrusted
# certificate needs a REAL privacy prompt, which only a person can answer. This script does everything
# else — once, by hand, in this order:
#
#   scripts/bundle/tcc-check.sh start      build a tiny probe, sign it with a NEW untrusted identity in a
#                                          temporary keychain, run it as a throw-away LaunchAgent that
#                                          lists ~/Documents → macOS asks; click ALLOW
#   scripts/bundle/tcc-check.sh relink     a DIFFERENT probe binary (new cdhash), same identity and
#                                          identifier, same path — as a relink does — run again:
#                                          no prompt and ALLOWED = the permission persisted
#   scripts/bundle/tcc-check.sh control    the same, signed AD HOC: macOS asks again (click Don't Allow)
#                                          or it is DENIED — shows the check can tell the difference
#   scripts/bundle/tcc-check.sh result     the probe's last answer and its designated requirement
#   scripts/bundle/tcc-check.sh cleanup    unload and delete the LaunchAgent, the keychain, the probe
#
# Options of start: --identity NAME signs with an existing identity instead (e.g. the one
# `histmersion-signing setup-macos --no-trust` made); the temporary keychain is then not created.
#
# What it touches: a LaunchAgent labelled com.histmersion.tcc-check (never Histmersion's own,
# com.histmersion.server), its work directory (~/Library/Caches/com.histmersion.tcc-check), a temporary
# keychain inside it — appended to your keychain search list only while signing, the list restored
# exactly afterwards — and ~/Documents, which the probe only LISTS (opendir/readdir, nothing read or
# written). Your login keychain is never changed. The grant stays in System Settings › Privacy &
# Security › Files and Folders as "histmersion-tcc-probe" until you remove it there (cleanup cannot:
# `tccutil reset` takes a bundle id, and without one it would reset EVERY app's Documents grant).
#
# Why a LaunchAgent: a program started from Terminal is covered by Terminal's own permission; started by
# launchd it is its own "responsible process", as the Histmersion server is.
# Tests (fakes for launchctl, an ad-hoc identity, a temporary target): HISTMERSION_TCC_DIR,
# HISTMERSION_TCC_AGENTS, HISTMERSION_TCC_TARGET, HISTMERSION_TCC_WAIT move what they name.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
label="com.histmersion.tcc-check"
work="${HISTMERSION_TCC_DIR:-$HOME/Library/Caches/$label}"
agents="${HISTMERSION_TCC_AGENTS:-$HOME/Library/LaunchAgents}"
plist="$agents/$label.plist"
target="${HISTMERSION_TCC_TARGET:-$HOME/Documents}"
probe="$work/histmersion-tcc-probe"
out="$work/probe.out"
kc="$work/tcc-check.keychain-db"
ident_name="Histmersion TCC Check"
ident="com.histmersion.tcc-probe"
domain="gui/$(id -u)"

die() { echo "tcc-check: $*" >&2; exit 1; }
[ "$(uname -s)" = Darwin ] || die "macOS only"

build() { # <variant> → $probe (a new binary: the variant is compiled in, so the cdhash differs)
  command -v clang >/dev/null 2>&1 || die "no clang (xcode-select --install)"
  cat > "$work/probe.c" <<'C'
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
int main(int argc, char **argv) {
  const char *dir = argc > 1 ? argv[1] : ".";
  DIR *d = opendir(dir);
  if (!d) { printf("%ld variant=%s DENIED %s: %s\n", (long)time(NULL), VARIANT, dir, strerror(errno)); return 1; }
  int n = 0; while (readdir(d)) n++;
  closedir(d);
  printf("%ld variant=%s ALLOWED %s: %d entries\n", (long)time(NULL), VARIANT, dir, n);
  return 0;
}
C
  clang -O1 -DVARIANT="\"$1\"" -o "$work/probe.new" "$work/probe.c"
}

identity_hash() { # the SHA-1 of the identity to sign with
  local name kcarg=()
  name="$(cat "$work/identity-name")"
  [ "$name" = "-" ] && { echo -; return 0; }
  [ -f "$kc" ] && kcarg=("$kc")
  security find-identity -p codesigning ${kcarg[@]+"${kcarg[@]}"} | awk -v n="\"$name\"" 'index($0, n) {print $2; exit}'
}

sign() { # <file> <identity sha1 or -> — with the temporary keychain in the search list only meanwhile
  local saved rc=0
  if [ "$2" = "-" ] || [ ! -f "$kc" ]; then codesign --force --sign "$2" --identifier "$ident" "$1"; return; fi
  saved="$(security list-keychains -d user | sed 's/^[[:space:]]*"\(.*\)"$/\1/')"
  security unlock-keychain -p "$(cat "$work/keychain-password")" "$kc"
  printf '%s\n' "$saved" "$kc" | tr '\n' '\0' | xargs -0 security list-keychains -d user -s
  codesign --force --sign "$2" --keychain "$kc" --identifier "$ident" "$1" || rc=$?
  printf '%s\n' "$saved" | tr '\n' '\0' | xargs -0 security list-keychains -d user -s
  [ "$(security list-keychains -d user | sed 's/^[[:space:]]*"\(.*\)"$/\1/')" = "$saved" ] \
    || echo "tcc-check: WARNING: your keychain search list differs from before; it was: $saved" >&2
  return "$rc"
}

install_probe() { # <variant> <identity sha1 or -> — replace the probe at its fixed path, as a relink swaps
  build "$1"
  sign "$work/probe.new" "$2"
  mv -f "$work/probe.new" "$probe"
  echo "  probe variant $1 · $(codesign -dvvv "$probe" 2>&1 | sed -n 's/^CDHash=/cdhash /p')"
  echo "  designated requirement: $(codesign -d -r- "$probe" 2>&1 | sed -n 's/^\(# \)\{0,1\}designated => //p')"
}

run_and_wait() { # <variant> — run the agent once more; wait for that variant's answer
  local i=0 wait="${HISTMERSION_TCC_WAIT:-180}"
  if launchctl print "$domain/$label" >/dev/null 2>&1; then launchctl kickstart "$domain/$label"
  else launchctl bootstrap "$domain" "$plist"; fi
  echo "  waiting for the probe (answer a macOS prompt if one appears; up to $wait s)…"
  while [ "$i" -lt "$wait" ]; do
    grep -q " variant=$1 " "$out" 2>/dev/null && break
    sleep 1; i=$((i + 1))
  done
  grep " variant=$1 " "$out" 2>/dev/null | tail -1 | sed 's/^[0-9]* /  probe: /' || echo "  no answer from the probe yet (a prompt still open?) — run: $0 result"
}

state() { sed -n "s/^$1=//p" "$work/state" 2>/dev/null | tail -1; }

cmd="${1:-}"; [ $# -gt 0 ] && shift
case "$cmd" in
  start)
    name="$ident_name"
    while [ $# -gt 0 ]; do case "$1" in --identity) name="$2"; shift ;; *) die "unknown argument '$1'" ;; esac; shift; done
    [ ! -e "$work/state" ] || die "already started ($work) — run: $0 cleanup, then start again"
    mkdir -p "$work" "$agents"; : > "$out"
    if [ "$name" = "$ident_name" ]; then
      echo "→ a NEW untrusted identity \"$name\" in a temporary keychain ($kc)"
      pw="$(openssl rand -hex 16)"; printf '%s' "$pw" > "$work/keychain-password"; chmod 600 "$work/keychain-password"
      security create-keychain -p "$pw" "$kc"
      security unlock-keychain -p "$pw" "$kc"
      HISTMERSION_KEYCHAIN="$kc" "$here/codesign-identity-macos.sh" --no-trust "$name" | sed 's/^/  /'
      security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$pw" "$kc" >/dev/null 2>&1
    else
      echo "→ signing with the existing identity \"$name\""
    fi
    printf '%s\n' "$name" > "$work/identity-name"
    h="$(identity_hash)"; [ -n "$h" ] || die "no code-signing identity \"$name\" found"
    install_probe 1 "$h"
    cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$label</string>
  <key>ProgramArguments</key><array><string>$probe</string><string>$target</string></array>
  <key>RunAtLoad</key><true/>
  <key>StandardOutPath</key><string>$out</string>
  <key>StandardErrorPath</key><string>$out</string>
</dict></plist>
PLIST
    echo "variant=1" > "$work/state"
    echo "→ running the probe as the LaunchAgent $label"
    echo "  macOS should ask: \"histmersion-tcc-probe\" would like to access files in your Documents folder — click ALLOW"
    run_and_wait 1
    echo "next: $0 relink"
    ;;

  relink|control)
    [ -f "$work/state" ] || die "not started — run: $0 start"
    v=$(( $(state variant) + 1 ))
    if [ "$cmd" = relink ]; then
      h="$(identity_hash)"; [ -n "$h" ] || die "the identity is gone"
      echo "→ a relink: probe variant $v, a different binary signed with the SAME identity, swapped in at the same path"
    else
      h="-"
      echo "→ the control: probe variant $v signed AD HOC (a different designated requirement, as an unsigned relink)"
      echo "  if macOS asks, click DON'T ALLOW"
    fi
    install_probe "$v" "$h"
    echo "variant=$v" >> "$work/state"
    run_and_wait "$v"
    line="$(grep " variant=$v " "$out" 2>/dev/null | tail -1 || true)"
    case "$cmd:$line" in
      relink:*ALLOWED*) echo "RESULT: the permission PERSISTED across the relink (if macOS did not ask again)" ;;
      relink:*DENIED*)  echo "RESULT: NOT persisted — the relinked binary was denied (or you were asked again)" ;;
      control:*ALLOWED*) echo "RESULT: the ad-hoc binary was ALLOWED — if you clicked Allow on a new prompt that is expected; else the check cannot tell the two apart" ;;
      control:*DENIED*)  echo "RESULT: as expected, the ad-hoc binary is not covered by the grant" ;;
      *) echo "RESULT: no answer yet — $0 result" ;;
    esac
    if [ "$cmd" = relink ]; then echo "next: $0 control (optional), then $0 cleanup"; fi
    ;;

  result)
    [ -f "$out" ] || die "not started"
    echo "the probe's answers (newest last):"
    sed 's/^[0-9]* /  /' "$out"
    [ -x "$probe" ] && echo "current probe: $(codesign -d -r- "$probe" 2>&1 | sed -n 's/^\(# \)\{0,1\}designated => /designated => /p')"
    echo "System Settings › Privacy & Security › Files and Folders lists histmersion-tcc-probe with its grant."
    ;;

  cleanup)
    launchctl bootout "$domain/$label" >/dev/null 2>&1 || true
    rm -f "$plist"
    [ -f "$kc" ] && security delete-keychain "$kc" 2>/dev/null || true
    rm -rf "$work"
    echo "removed the LaunchAgent $label, the temporary keychain and $work"
    echo "the grant itself: System Settings › Privacy & Security › Files and Folders › histmersion-tcc-probe (remove it there)"
    ;;

  ""|-h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//' ;;
  *) die "unknown command '$cmd' (start, relink, control, result, cleanup)" ;;
esac
