uc_location() {
  local envdir="$1" dflt="$2" p
  if [ -f "$envdir/HISTMERSION_CONFIG" ] && [ -n "$(cat "$envdir/HISTMERSION_CONFIG")" ]; then
    p="$(cat "$envdir/HISTMERSION_CONFIG")"
    case "$p" in "~") p="$HOME" ;; "~/"*) p="$HOME/${p#\~/}" ;; esac
    printf '%s\n' "$p"; return 0
  fi
  printf '%s\n' "$dflt/histmersion.conf"
}

uc_write_template() {
  local template="$1" path="$2"
  if [ -e "$path" ] || [ -L "$path" ]; then
    echo "  config file: $path (kept)"
    return 0
  fi
  mkdir -p "$(dirname "$path")"
  ( set -o noclobber; cat "$template" > "$path" ) 2>/dev/null || { echo "  config file: $path (kept)"; return 0; }
  echo "  config file: $path (written: a commented template, no settings)"
}
