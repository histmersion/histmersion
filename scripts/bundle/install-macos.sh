#!/usr/bin/env bash
# Install a hist-db bundle on macOS — no Docker, no admin rights, nothing outside the user's home:
#
#   scripts/bundle/install-macos.sh <bundle-dir> [--move]
#
#   ~/Applications/hist-db/            the bundle (bin/, pg/, converter/)      HISTDB_INSTALL_DIR
#   ~/Library/Application Support/hist-db/   database, logs, backups (created by the server)
#   ~/Library/LaunchAgents/com.histdb.server.plist   starts it at login, restarts it if it dies
#   ~/bin/histdb                        the CLI                                 HISTDB_BIN_DIR
#
# --move renames the bundle into place instead of copying it (a developer build, short on disk).
# HISTDB_CORPUS_DIR, when set, is passed to the server (the wiki/sources tree `histdb seed` reads).
set -euo pipefail
src="$(cd "${1:?usage: $0 <bundle-dir> [--move]}" && pwd)"
move="${2:-}"
dest="${HISTDB_INSTALL_DIR:-$HOME/Applications/hist-db}"
bindir="${HISTDB_BIN_DIR:-$HOME/bin}"
label="com.histdb.server"
plist="$HOME/Library/LaunchAgents/$label.plist"
data="$HOME/Library/Application Support/hist-db"

for p in bin/histdb-server pg/bin/pg_ctl; do
  [ -x "$src/$p" ] || { echo "not a hist-db bundle: $src/$p is missing (build one with 'make bundle')" >&2; exit 1; }
done

# stop a running instance first: it holds the database cluster open
if launchctl print "gui/$(id -u)/$label" >/dev/null 2>&1; then
  echo "→ stopping the running hist-db"
  launchctl bootout "gui/$(id -u)/$label" || true
  for _ in $(seq 1 30); do pgrep -f "$dest/bin/histdb-server" >/dev/null || break; sleep 1; done
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

mkdir -p "$bindir" "$data/logs" "$(dirname "$plist")"
ln -sf "$dest/bin/histdb" "$bindir/histdb"

env_xml=""
if [ -n "${HISTDB_CORPUS_DIR:-}" ]; then
  env_xml="<key>EnvironmentVariables</key><dict><key>HISTDB_CORPUS_DIR</key><string>${HISTDB_CORPUS_DIR}</string></dict>"
fi
cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$label</string>
  <key>ProgramArguments</key><array><string>$dest/bin/histdb-server</string></array>
  <key>WorkingDirectory</key><string>$dest</string>
  $env_xml
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
  <key>ExitTimeOut</key><integer>60</integer>
  <key>StandardOutPath</key><string>$data/logs/server.log</string>
  <key>StandardErrorPath</key><string>$data/logs/server.log</string>
</dict></plist>
PLIST

echo "→ starting"
launchctl bootstrap "gui/$(id -u)" "$plist"
for _ in $(seq 1 120); do
  curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1 && break; sleep 1
done
if curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
  echo "hist-db is running: http://localhost:8000   (logs: $data/logs/server.log)"
  case ":$PATH:" in *":$bindir:"*) ;; *) echo "note: add $bindir to PATH to use the 'histdb' command";; esac
else
  echo "hist-db did not answer within 2 minutes — see $data/logs/server.log" >&2; exit 1
fi
