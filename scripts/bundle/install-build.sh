# The installers' BUILD step (docs/research-plugins.md §8): which server binary the install runs —
# sourced by install-macos.sh, install-linux.sh and scripts/bundle/tests/install-build-test.sh. Bash 3.2.
#
#   prebuilt (the default)  the bundle's own binary, as before — no toolchain, nothing built
#   local                   link the server on THIS machine from the bundle's kit: GraalVM CE provisioned
#                           at its pinned version and checksum (histmersion-toolchain install), the C
#                           toolchain checked, histmersion-relink; on any failure, JVM mode
#   jvm                     no native binary: the kit on a JDK (histmersion-relink --jvm)
#
# Chosen with --local-build / --jvm or HISTMERSION_BUILD=prebuilt|local|jvm. A prebuilt binary has no
# user plugins in it, so when the plugins directory holds any, an install relinks (`local`) — else the
# plugins the user added would silently vanish with the update (§8.6).

# Pure: the mode to use. <requested>: what the caller asked for ("" = nothing); <plugin jars>: how many
# jars the plugins directory holds.
ib_mode() { # <requested> <plugin jars>
  case "$1" in
    local|jvm|prebuilt) echo "$1" ;;
    "") if [ "${2:-0}" -gt 0 ]; then echo local; else echo prebuilt; fi ;;
    *) echo "unknown build mode '$1' (prebuilt, local, jvm)" >&2; return 1 ;;
  esac
}

# Run the build step in the installed bundle <dest>; the service is not running yet (no restart).
# Echoes what it did; returns 0 when the install has a server to start (native or JVM).
ib_build() { # <dest> <mode>
  local dest="$1" mode="$2" b
  b="$dest/bin"
  case "$mode" in
    prebuilt) echo "→ server: the bundle's prebuilt binary"; return 0 ;;
    jvm)
      echo "→ server: JVM mode (the kit on a JDK, plugins found at each start)"
      "$b/histmersion-toolchain" path >/dev/null 2>&1 || "$b/histmersion-toolchain" install || return 1
      HISTMERSION_SERVICE=none "$b/histmersion-relink" --jvm --no-restart
      return $?
      ;;
    local)
      echo "→ server: linked on this machine from the bundle's kit (GraalVM CE, ~2 minutes, ~11 GB of memory)"
      if "$b/histmersion-toolchain" path >/dev/null 2>&1 || "$b/histmersion-toolchain" install; then
        if "$b/histmersion-toolchain" check && HISTMERSION_SERVICE=none "$b/histmersion-relink" --no-restart; then
          return 0
        fi
      fi
      echo "  the local link did not succeed — falling back to JVM mode (histmersion-relink retries later)"
      HISTMERSION_SERVICE=none "$b/histmersion-relink" --jvm --no-restart && return 0
      if [ -x "$b/histmersion-server.prev" ] || [ -x "$b/histmersion-server" ]; then
        echo "  JVM mode did not work either — keeping the bundle's prebuilt binary (WITHOUT your plugins)"
        return 0
      fi
      return 1
      ;;
  esac
}
