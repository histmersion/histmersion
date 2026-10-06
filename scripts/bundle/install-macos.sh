#!/usr/bin/env bash
# Install a Histmersion bundle on macOS — no admin rights, nothing outside the user's home:
#
#   scripts/bundle/install-macos.sh <bundle-dir> [--move]
#
#   ~/Applications/Histmersion/            the bundle (bin/, pg/, converter/)      HISTMERSION_INSTALL_DIR
#   ~/Library/Application Support/Histmersion/   database, logs, backups (created by the server)
#   ~/Library/LaunchAgents/com.histmersion.server.plist   starts it at login, restarts it if it dies
#   ~/bin/histmersion                        the CLI                                 HISTMERSION_BIN_DIR
#
# --move renames the bundle into place instead of copying it (a developer build, short on disk).
# The server's settings live in the LaunchAgent's EnvironmentVariables. Every Histmersion setting of the
# installed plist is carried over (launchagent-env.sh); HISTMERSION_CORPUS_DIR (the wiki/sources tree
# `histmersion seed` reads), HISTMERSION_ACQUIRE_CONTACT_EMAIL (the OpenAlex/Crossref/Unpaywall contact)
# and HISTMERSION_AGENTS_*, when set in the caller's environment, override it; the claude/codex/opencode
# commands are resolved on this PATH when nothing names them. What changed is printed, secrets masked.
# HISTMERSION_CODESIGN_IDENTITY (default "Histmersion Local", created by codesign-identity-macos.sh) signs
# the two binaries with a stable identity when it exists, so macOS privacy permissions (Documents access)
# survive updates; without it they stay ad-hoc signed, as built.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=launchagent-env.sh
. "$here/launchagent-env.sh"
src="$(cd "${1:?usage: $0 <bundle-dir> [--move]}" && pwd)"
move="${2:-}"
dest="${HISTMERSION_INSTALL_DIR:-$HOME/Applications/Histmersion}"
bindir="${HISTMERSION_BIN_DIR:-$HOME/bin}"
label="com.histmersion.server"
plist="$HOME/Library/LaunchAgents/$label.plist"
data="$HOME/Library/Application Support/Histmersion"

for p in bin/histmersion-server pg/bin/pg_ctl; do
  [ -x "$src/$p" ] || { echo "not a Histmersion bundle: $src/$p is missing (build one with 'just bundle')" >&2; exit 1; }
done

# stop a running instance first: it holds the database cluster open
if launchctl print "gui/$(id -u)/$label" >/dev/null 2>&1; then
  echo "→ stopping the running Histmersion"
  launchctl bootout "gui/$(id -u)/$label" || true
  for _ in $(seq 1 30); do pgrep -f "$dest/bin/histmersion-server" >/dev/null || break; sleep 1; done
fi

echo "→ installing to $dest"
mkdir -p "$(dirname "$dest")"
if [ "$src" != "$dest" ]; then
  rm -rf "$dest.old"; [ -e "$dest" ] && mv "$dest" "$dest.old"
  if [ "$move" = "--move" ]; then mv "$src" "$dest"; else cp -R "$src" "$dest"; fi
  rm -rf "$dest.old"
fi
# a bundle downloaded through a browser is quarantined; Gatekeeper would block every binary in it
xattr -dr com.apple.quarantine "$dest" 2>/dev/null || true

# sign with the local identity if there is one: a stable signature keeps macOS permissions across updates
identity="${HISTMERSION_CODESIGN_IDENTITY:-Histmersion Local}"
# (any identity with that name, trusted or not: the signature's identity is what keeps the permissions)
if security find-identity -p codesigning 2>/dev/null | grep -qF "\"$identity\""; then
  echo "→ signing the binaries as \"$identity\""
  codesign --force --sign "$identity" --identifier com.histmersion.cli "$dest/bin/histmersion"
  codesign --force --sign "$identity" --identifier com.histmersion.server "$dest/bin/histmersion-server"
else
  echo "note: no \"$identity\" code-signing identity — binaries stay ad-hoc signed; macOS may ask for"
  echo "      folder permissions again after each update (create one: scripts/bundle/codesign-identity-macos.sh)"
fi

mkdir -p "$bindir" "$data/logs" "$(dirname "$plist")"
ln -sf "$dest/bin/histmersion" "$bindir/histmersion"

# per-install settings the service needs (never stored in the repo): the installed plist's, with the
# caller's environment over them — read BEFORE the plist is rewritten below
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
la_env_prepare "$plist" "$work" "$work/env"
cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$label</string>
  <key>ProgramArguments</key><array><string>$dest/bin/histmersion-server</string></array>
  <key>WorkingDirectory</key><string>$dest</string>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
  <key>ExitTimeOut</key><integer>60</integer>
  <key>StandardOutPath</key><string>$data/logs/server.log</string>
  <key>StandardErrorPath</key><string>$data/logs/server.log</string>
</dict></plist>
PLIST
la_env_apply "$work/env" "$plist"

echo "→ starting"
launchctl bootstrap "gui/$(id -u)" "$plist"
for _ in $(seq 1 120); do
  curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1 && break; sleep 1
done
if curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
  echo "Histmersion is running: http://localhost:8000   (logs: $data/logs/server.log)"
  case ":$PATH:" in *":$bindir:"*) ;; *) echo "note: add $bindir to PATH to use the 'histmersion' command";; esac
else
  echo "Histmersion did not answer within 2 minutes — see $data/logs/server.log" >&2; exit 1
fi
