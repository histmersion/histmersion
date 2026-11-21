#!/usr/bin/env bash
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
