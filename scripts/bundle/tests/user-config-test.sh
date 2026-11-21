#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../user-config.sh
. "$here/../user-config.sh"
template="$here/../histmersion.conf.template"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
HOME="$tmp/home"; export HOME
mkdir -p "$HOME"

passed=0; failed=0
ok()   { passed=$((passed + 1)); echo "  ok   $1"; }
bad()  { failed=$((failed + 1)); echo "  FAIL $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }

echo "user-config"

envd="$tmp/env"; mkdir -p "$envd"
check "location: the default directory" '[ "$(uc_location "$envd" "$tmp/cfg")" = "$tmp/cfg/histmersion.conf" ]'
check "location: a missing env dir is none" '[ "$(uc_location "$tmp/none" "$tmp/cfg")" = "$tmp/cfg/histmersion.conf" ]'
printf '%s' "$tmp/d2" > "$envd/HISTMERSION_DATA_DIR"
check "location: the data dir never moves it" '[ "$(uc_location "$envd" "$tmp/cfg")" = "$tmp/cfg/histmersion.conf" ]'
printf '%s' "~/my.conf" > "$envd/HISTMERSION_CONFIG"
check "location: HISTMERSION_CONFIG wins, ~ expanded" '[ "$(uc_location "$envd" "$tmp/cfg")" = "$HOME/my.conf" ]'

target="$tmp/home/.config/histmersion/histmersion.conf"
uc_write_template "$template" "$target" > "$tmp/1.log"
check "first install: the template is written (directory created)" 'cmp -s "$template" "$target"'
check "first install: says so" 'grep -q "written" "$tmp/1.log"'
check "the template has no active setting (every non-blank line is a comment)" \
  '! grep -vE "^[[:space:]]*(#|$)" "$template" >/dev/null'
printf 'histmersion.server.port = 8001\n' > "$target"
uc_write_template "$template" "$target" > "$tmp/2.log"
check "re-install: an existing file is kept byte for byte" '[ "$(cat "$target")" = "histmersion.server.port = 8001" ]'
check "re-install: says it was kept" 'grep -q "(kept)" "$tmp/2.log"'
ln -s "$tmp/elsewhere.conf" "$tmp/link.conf"
uc_write_template "$template" "$tmp/link.conf" > /dev/null
check "a dangling symlink is not written through" '[ ! -e "$tmp/elsewhere.conf" ]'

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
