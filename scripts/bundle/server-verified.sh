#!/usr/bin/env bash
# libexec/histmersion-server-verified — start the server only when it is the binary YOU signed
# (docs/research-plugins.md §21.3). Bash 3.2.
#
#   histmersion-server-verified [the server's arguments]
#
# The LaunchAgent / systemd unit starts this instead of bin/histmersion-server ONLY while GPG provenance
# is on (`histmersion-signing use-gpg` points the service here, `off-gpg` points it back; the installers
# choose the same way). It checks that bin/histmersion-server's SHA-256 is the one in
# bin/histmersion-server.sha256 and that this file carries a good signature by the configured key
# (<data>/signing/gpg-key) — the record histmersion-relink signs after every link, carried with its binary
# through .prev and rollback — and then execs the binary, so the running process is the server itself
# (same pid, same argv[0], `pgrep -f <install>/bin/histmersion-server` finds it). Otherwise it does not
# start it: one line to the server log saying why, exit 78 (EX_CONFIG). The service manager tries again
# (launchd every ~10 s, systemd every 5 s), so a fixed cause — a key imported, the binary re-signed with
# `histmersion-signing sign-binary` — is picked up without a reinstall.
#
# With provenance off (the key file removed while the service still points here) it starts the binary
# unverified and says so: off means off.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=kit-common.sh
. "$here/kit-common.sh"

install="${HISTMERSION_HOME:-$(cd "$here/.." && pwd)}"
server="$install/bin/histmersion-server"
me="histmersion-server-verified"
stamp() { date '+%Y-%m-%d %H:%M:%S'; }

key="$(hk_gpg_key)"
if [ -z "$key" ]; then
  echo "$(stamp) $me: GPG provenance is off — starting $server without verification" >&2
  exec "$server" "$@"
fi
if why="$(hk_verify_binary "$server" "$server.sha256" "$server.sha256.asc" "$key")"; then
  echo "$(stamp) $me: $why" >&2
  exec "$server" "$@"
fi
echo "$(stamp) $me: REFUSING to start the server: $why" >&2
echo "$(stamp) $me: if you trust the installed binary: histmersion-signing sign-binary; to stop verifying: histmersion-signing off-gpg" >&2
exit 78
