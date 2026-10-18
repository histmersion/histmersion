#!/usr/bin/env bash
# Install a Histmersion bundle on macOS — no admin rights, nothing outside the user's home:
#
#   scripts/bundle/install-macos.sh <bundle-dir> [--move] [--prebuilt | --jvm]
#
#   ~/Applications/Histmersion/            the bundle (bin/, pg/, converter/)      HISTMERSION_INSTALL_DIR
#   ~/Library/Application Support/Histmersion/   database, logs, backups (created by the server)
#   ~/.config/histmersion/histmersion.conf   your settings (a commented template is written when there is
#                                            none; an existing file is never touched)       HISTMERSION_CONFIG
#   ~/Library/LaunchAgents/com.histmersion.server.plist   starts it at login, restarts it if it dies
#   ~/bin/histmersion                        the CLI                                 HISTMERSION_BIN_DIR
#
# --move renames the bundle into place instead of copying it (a developer build, short on disk).
# The server binary (install-build.sh; HISTMERSION_BUILD=local|prebuilt|jvm) is LINKED ON THIS MAC by
# default, from the bundle's kit with the plugins of the data directory's plugins/: GraalVM CE downloaded
# once at its pinned version and checksum into the data directory (~325 MB), Xcode command line tools,
# ~12 GB of memory, about 2 minutes. When this Mac cannot (memory, toolchain, download, native-image, the
# self-check), the install FALLS BACK to the bundle's prebuilt binary and says so — it still succeeds.
# Nothing is linked when it would repeat a binary already linked here from the same kit and plugins.
# --prebuilt takes the bundle's binary as it is (no toolchain); --jvm runs the kit on a JDK.
# The server's settings: histmersion.conf (above), and over it the LaunchAgent's EnvironmentVariables
# (an environment variable beats the same setting in the file). Every Histmersion setting of the
# installed plist is carried over (launchagent-env.sh); HISTMERSION_CORPUS_DIR (the wiki/sources tree
# `histmersion seed` reads), HISTMERSION_ACQUIRE_CONTACT_EMAIL (the OpenAlex/Crossref/Unpaywall contact)
# and HISTMERSION_AGENTS_*, when set in the caller's environment, override it; the claude/codex/opencode
# commands are resolved on this PATH when nothing names them. What changed is printed, secrets masked.
# HISTMERSION_CODESIGN_IDENTITY (default "Histmersion Local", created by `histmersion-signing setup-macos`)
# signs the binaries with a stable identity when it exists, so macOS privacy permissions (Documents
# access) survive updates; without it they stay ad-hoc signed, as built. Signing is optional (D6).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=launchagent-env.sh
. "$here/launchagent-env.sh"
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
fi
# the previous install stays aside until the build step: its binary is kept when linking would repeat it
prev=""; [ -d "$dest.old" ] && prev="$dest.old"
# a bundle downloaded through a browser is quarantined; Gatekeeper would block every binary in it
xattr -dr com.apple.quarantine "$dest" 2>/dev/null || true

# the server binary: linked on this machine from the kit with the user's plugins (the default), else the
# bundle's prebuilt one (--prebuilt, or the fallback when this machine cannot link it), or JVM mode
mode="$(ib_mode "$build")" || exit 1
HISTMERSION_HOME="$dest" HISTMERSION_DATA_DIR="$data" ib_build "$dest" "$mode" "$prev" || { echo "no server binary to run (see above)" >&2; exit 1; }
rm -rf "$dest.old"

# sign with the local identity if there is one: a stable signature keeps macOS permissions across updates
# (a relinked server was signed by histmersion-relink already; a JVM-mode launcher is a script)
if [ -x "$dest/bin/histmersion-signing" ]; then
  "$dest/bin/histmersion-signing" codesign "$dest/bin/histmersion" com.histmersion.cli | sed 's/^/  cli: /'
  case "$ib_result" in prebuilt|kept) "$dest/bin/histmersion-signing" codesign "$dest/bin/histmersion-server" com.histmersion.server | sed 's/^/  server: /' ;; esac
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

# the user config file, where the server will look for it (the plist's HISTMERSION_CONFIG moves it; a
# LaunchAgent has no XDG_CONFIG_HOME, so the default is ~/.config): a commented template when there is
# none, never an overwrite
uc_write_template "$here/histmersion.conf.template" "$(uc_location "$work/env" "$HOME/.config/histmersion")"

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
