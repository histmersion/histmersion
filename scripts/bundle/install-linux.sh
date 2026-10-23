#!/usr/bin/env bash
# Install a Histmersion bundle on Linux — no root, nothing outside the user's home:
#
#   scripts/bundle/install-linux.sh <bundle-dir> [--move] [--prebuilt | --jvm]
#
#   ~/.local/opt/histmersion/                    the bundle (bin/, pg/, converter/)   HISTMERSION_INSTALL_DIR
#   ${XDG_DATA_HOME:-~/.local/share}/histmersion/  database, logs, backups (created by the server)
#   ~/.config/systemd/user/histmersion.service   starts it with the user session, restarts it if it dies
#   ${XDG_CONFIG_HOME:-~/.config}/histmersion/histmersion.conf   your settings (a commented template is written
#                                                when there is none; an existing file is never touched)
#   ~/.local/bin/histmersion                      the CLI                               HISTMERSION_BIN_DIR
#
# --move renames the bundle into place instead of copying it. The server binary (install-build.sh;
# HISTMERSION_BUILD=local|prebuilt|jvm) is LINKED HERE by default, from the bundle's kit with the plugins
# of the data directory: GraalVM CE downloaded once at its pinned version and checksum (~350 MB), gcc +
# zlib headers, ~16 GB of available memory, about 2 minutes. When this machine cannot, the install FALLS
# BACK to the bundle's prebuilt binary and says so — it still succeeds. Nothing is linked when it would
# repeat a binary already linked here from the same kit and plugins. --prebuilt takes the bundle's binary
# as it is (no toolchain); --jvm runs the kit on a JDK. HISTMERSION_CORPUS_DIR, when set, is
# passed to the server. To keep it running after logout: `loginctl enable-linger "$USER"`.
# With GPG provenance on (`histmersion-signing use-gpg`), the unit starts libexec/histmersion-server-verified,
# which starts the server only when its SHA-256 carries your signature (the installer signs the installed
# binary); off, it starts bin/histmersion-server directly, as before.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=user-config.sh
. "$here/user-config.sh"
# shellcheck source=install-build.sh
. "$here/install-build.sh"
case "${1:-}" in -h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;; esac
src="$(cd "${1:?usage: $0 <bundle-dir> [--move] [--prebuilt | --jvm]}" && pwd)"
shift
move="" build="${HISTMERSION_BUILD:-}"
for a; do
  case "$a" in
    -h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    --move) move=--move ;;
    --local-build) build=local ;;   # the default; kept so older command lines still work
    --prebuilt) build=prebuilt ;;
    --jvm) build=jvm ;;
    *) echo "unknown argument '$a'" >&2; exit 1 ;;
  esac
done
dest="${HISTMERSION_INSTALL_DIR:-$HOME/.local/opt/histmersion}"
bindir="${HISTMERSION_BIN_DIR:-$HOME/.local/bin}"
data="${XDG_DATA_HOME:-$HOME/.local/share}/histmersion"
unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
unit="histmersion.service"

for p in bin/histmersion-server pg/bin/pg_ctl; do
  [ -x "$src/$p" ] || { echo "not a Histmersion bundle: $src/$p is missing (build one with 'just bundle')" >&2; exit 1; }
done
systemctl --user show-environment >/dev/null 2>&1 || {
  echo "no systemd user session (systemctl --user) — log in normally, or run $src/bin/histmersion-server by hand" >&2
  exit 1; }

# stop a running instance first: it holds the database cluster open
if systemctl --user is-active --quiet "$unit"; then
  echo "→ stopping the running Histmersion"
  systemctl --user stop "$unit"
fi

echo "→ installing to $dest"
mkdir -p "$(dirname "$dest")"
if [ "$src" != "$dest" ]; then
  rm -rf "$dest.old"; [ -e "$dest" ] && mv "$dest" "$dest.old"
  if [ "$move" = "--move" ]; then mv "$src" "$dest"; else cp -R "$src" "$dest"; fi
fi
# the previous install stays aside until the build step: its binary is kept when linking would repeat it
prev=""; [ -d "$dest.old" ] && prev="$dest.old"

# the server binary: linked on this machine from the kit with the user's plugins (the default), else the
# bundle's prebuilt one (--prebuilt, or the fallback when this machine cannot link it), or JVM mode
mode="$(ib_mode "$build")" || exit 1
HISTMERSION_HOME="$dest" HISTMERSION_DATA_DIR="$data" ib_build "$dest" "$mode" "$prev" || { echo "no server binary to run (see above)" >&2; exit 1; }
rm -rf "$dest.old"

# what the unit starts: the binary, or (GPG provenance on) the launcher that verifies it first
program="$(HISTMERSION_DATA_DIR="$data" ib_service_program "$dest")"

mkdir -p "$bindir" "$data/logs" "$unit_dir"
ln -sf "$dest/bin/histmersion" "$bindir/histmersion"

# per-install settings the service needs, passed through when set (never stored in the repo)
env_line=""
for v in HISTMERSION_CORPUS_DIR HISTMERSION_ACQUIRE_CONTACT_EMAIL; do
  val="$(printenv "$v" || true)"
  [ -n "$val" ] && env_line="${env_line}Environment=${v}=${val}
"
done
cat > "$unit_dir/$unit" <<UNIT
[Unit]
Description=Histmersion research corpus (server, PostgreSQL, OCR converter)
After=network.target

[Service]
ExecStart=$program
WorkingDirectory=$dest
$env_line
Restart=on-failure
RestartSec=5
TimeoutStopSec=60
StandardOutput=append:$data/logs/server.log
StandardError=append:$data/logs/server.log

[Install]
WantedBy=default.target
UNIT

# the user config file (environment > this file > defaults): a commented template when there is none
uc_write_template "$here/histmersion.conf.template" \
  "$(uc_location /nonexistent "${XDG_CONFIG_HOME:-$HOME/.config}/histmersion")"

echo "→ starting"
systemctl --user daemon-reload
systemctl --user enable --now "$unit"
for _ in $(seq 1 120); do
  curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1 && break; sleep 1
done
if curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
  echo "Histmersion is running: http://localhost:8000   (logs: $data/logs/server.log)"
  case ":$PATH:" in *":$bindir:"*) ;; *) echo "note: add $bindir to PATH to use the 'histmersion' command";; esac
else
  echo "Histmersion did not answer within 2 minutes — see $data/logs/server.log" >&2; exit 1
fi
