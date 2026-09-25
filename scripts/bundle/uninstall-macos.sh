#!/usr/bin/env bash
# Remove the hist-db app (bundle, LaunchAgent, CLI link). The DATA — database, backups — in
# ~/Library/Application Support/hist-db is kept unless --purge is given.
set -euo pipefail
dest="${HISTDB_INSTALL_DIR:-$HOME/Applications/hist-db}"
label="com.histdb.server"
launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/$label.plist" "${HISTDB_BIN_DIR:-$HOME/bin}/histdb"
rm -rf "$dest"
if [ "${1:-}" = "--purge" ]; then rm -rf "$HOME/Library/Application Support/hist-db"; echo "hist-db and its data removed"
else echo "hist-db removed; data kept in ~/Library/Application Support/hist-db (--purge deletes it)"; fi
