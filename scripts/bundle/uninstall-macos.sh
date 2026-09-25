#!/usr/bin/env bash
# Remove the Histmersion app (bundle, LaunchAgent, CLI link). The DATA — database, backups — in
# ~/Library/Application Support/Histmersion is kept unless --purge is given.
set -euo pipefail
dest="${HISTMERSION_INSTALL_DIR:-$HOME/Applications/Histmersion}"
label="com.histmersion.server"
launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/$label.plist" "${HISTMERSION_BIN_DIR:-$HOME/bin}/histmersion"
rm -rf "$dest"
if [ "${1:-}" = "--purge" ]; then rm -rf "$HOME/Library/Application Support/Histmersion"; echo "Histmersion and its data removed"
else echo "Histmersion removed; data kept in ~/Library/Application Support/Histmersion (--purge deletes it)"; fi
