#!/usr/bin/env bash
set -euo pipefail
dest="${HISTMERSION_INSTALL_DIR:-$HOME/.local/opt/histmersion}"
data="${XDG_DATA_HOME:-$HOME/.local/share}/histmersion"
unit_file="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/histmersion.service"
systemctl --user disable --now histmersion.service 2>/dev/null || true
rm -f "$unit_file" "${HISTMERSION_BIN_DIR:-$HOME/.local/bin}/histmersion"
systemctl --user daemon-reload 2>/dev/null || true
rm -rf "$dest"
if [ "${1:-}" = "--purge" ]; then rm -rf "$data"; echo "Histmersion and its data removed"
else echo "Histmersion removed; data kept in $data (--purge deletes it)"; fi
