# The user config file (histmersion.conf) at install time — sourced by install-macos.sh,
# install-linux.sh and scripts/bundle/tests/user-config-test.sh. Bash 3.2.
#
# The server reads reference.conf < this file < the environment (UserPaths.configFile in
# modules/config); these functions put the commented template where the server will look, once.

# Where the installed server will look for the file. <env dir> holds the service's variables, one
# file per name (launchagent-env.sh's format; an empty or missing dir is none); <default dir> is
# $XDG_CONFIG_HOME/histmersion as the SERVICE sees it (default ~/.config/histmersion).
# HISTMERSION_CONFIG wins. The data directory never moves it: config is the person's, data the server's.
uc_location() { # <env dir> <default dir>
  local envdir="$1" dflt="$2" p
  if [ -f "$envdir/HISTMERSION_CONFIG" ] && [ -n "$(cat "$envdir/HISTMERSION_CONFIG")" ]; then
    p="$(cat "$envdir/HISTMERSION_CONFIG")"
    case "$p" in "~") p="$HOME" ;; "~/"*) p="$HOME/${p#\~/}" ;; esac
    printf '%s\n' "$p"; return 0
  fi
  printf '%s\n' "$dflt/histmersion.conf"
}

# Write <template> to <path> unless something is already there — never overwrites, never truncates.
uc_write_template() { # <template> <path>
  local template="$1" path="$2"
  if [ -e "$path" ] || [ -L "$path" ]; then
    echo "  config file: $path (kept)"
    return 0
  fi
  mkdir -p "$(dirname "$path")"
  # noclobber: a file appearing between the test and the write is still not overwritten
  ( set -o noclobber; cat "$template" > "$path" ) 2>/dev/null || { echo "  config file: $path (kept)"; return 0; }
  echo "  config file: $path (written: a commented template, no settings)"
}
