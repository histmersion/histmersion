ib_here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=kit-common.sh
. "$ib_here/kit-common.sh"

ib_mode() {
  case "$1" in
    ""|local) echo local ;;
    jvm|prebuilt) echo "$1" ;;
    *) echo "unknown build mode '$1' (local, prebuilt, jvm)" >&2; return 1 ;;
  esac
}

ib_result=""

ib_records="histmersion-server histmersion-server.plugin-set.json histmersion-server.sha256 histmersion-server.sha256.asc histmersion-server.build"

ib_fallback() {
  echo "  $2"
  if [ ! -x "$1/histmersion-server" ]; then
    echo "  …and the bundle has no prebuilt binary to fall back to ($1/histmersion-server)" >&2
    return 1
  fi
  echo "  → FALLING BACK to the bundle's prebuilt binary: the install goes on with it"
  if [ "$3" -gt 0 ]; then
    echo "    it does NOT hold your $3 plugin jar(s) — fix the cause above, then: histmersion plugin rebuild"
    echo "    (or JVM mode, which needs no build: histmersion plugin rebuild --jvm)"
  else
    echo "    the local build can be retried any time: histmersion plugin rebuild; --prebuilt skips it on the next install"
  fi
  ib_result=prebuilt
  return 0
}

ib_reuse() {
  local f
  for f in $ib_records; do
    rm -f "$1/$f.prev"
    [ -e "$1/$f" ] && mv -f "$1/$f" "$1/$f.prev"
    [ -e "$2/$f" ] && cp -p "$2/$f" "$1/$f"
  done
  return 0
}

ib_build() {
  local dest="$1" mode="$2" prev="${3:-}" b plugins jars why rc
  b="$dest/bin"
  plugins="$(hk_plugins_dir)"
  jars="$(hk_plugin_jars "$plugins" | wc -l | tr -d ' ')"
  ib_result=""
  case "$mode" in
    prebuilt)
      echo "→ server: the bundle's prebuilt binary (--prebuilt: nothing built here)"
      [ "$jars" -gt 0 ] && echo "  note: it does NOT hold the $jars plugin jar(s) in $plugins — histmersion plugin rebuild links them in"
      ib_result=prebuilt
      [ -x "$b/histmersion-server" ]
      return $?
      ;;
    jvm)
      echo "→ server: JVM mode (the kit on a JDK, plugins found at each start)"
      if { "$b/histmersion-toolchain" path >/dev/null 2>&1 || "$b/histmersion-toolchain" install; } \
          && HISTMERSION_SERVICE=none "$b/histmersion-relink" --jvm --no-restart; then
        ib_result=jvm; return 0
      fi
      ib_fallback "$b" "JVM mode could not be set up (above)" "$jars"
      return $?
      ;;
    local)
      if why="$(hk_build_current "$b/histmersion-server" "$dest/kit" "$plugins")"; then
        echo "→ server: the bundle's binary was linked on this machine from this kit, with these plugins — nothing to build"
        ib_result=kept; return 0
      fi
      if [ -n "$prev" ] && hk_build_current "$prev/bin/histmersion-server" "$dest/kit" "$plugins" >/dev/null; then
        echo "→ server: the previous install's binary was linked on this machine from this same kit and plugins — kept, not linked again"
        ib_reuse "$b" "$prev/bin"
        ib_result=reused; return 0
      fi
      echo "→ server: linking it on this machine from the bundle's kit (the bundle's binary: $why)"
      echo "  GraalVM CE, about 2 minutes and ~13.5 GB of memory; --prebuilt skips this"
      rc=0; "$b/histmersion-toolchain" preflight || rc=$?
      if [ "$rc" != 0 ]; then ib_fallback "$b" "this machine cannot link the server now (above)" "$jars"; return $?; fi
      if ! "$b/histmersion-toolchain" path >/dev/null 2>&1; then
        echo "  GraalVM CE is downloaded once (~340 MB, ~640 MB unpacked) into $(hk_data_dir)/toolchain"
        rc=0; "$b/histmersion-toolchain" install || rc=$?
        if [ "$rc" != 0 ]; then ib_fallback "$b" "GraalVM CE could not be provisioned (download or checksum, above)" "$jars"; return $?; fi
      fi
      rc=0; HISTMERSION_SERVICE=none "$b/histmersion-relink" --no-restart || rc=$?
      if [ "$rc" != 0 ]; then
        ib_fallback "$b" "the local link did not succeed (histmersion-relink exit $rc; log: $(hk_data_dir)/logs/relink.log)" "$jars"
        return $?
      fi
      ib_result=local; return 0
      ;;
    *) echo "unknown build mode '$mode'" >&2; return 1 ;;
  esac
}

ib_service_program() {
  local dest="$1"
  if [ -n "$(hk_gpg_key)" ] && [ -x "$dest/libexec/histmersion-server-verified" ]; then
    echo "→ GPG provenance is on: the service verifies the server binary's signed SHA-256 at each start" >&2
    if HISTMERSION_HOME="$dest" "$dest/bin/histmersion-signing" sign-binary 2>&1 | sed 's/^/  /' >&2 \
        && HISTMERSION_HOME="$dest" "$dest/bin/histmersion-signing" verify-binary >/dev/null 2>&1; then
      printf '%s\n' "$dest/libexec/histmersion-server-verified"
      return 0
    fi
    echo "  WARNING: the server binary's checksum could not be signed with GPG key $(hk_gpg_key) (above)." >&2
    echo "  The service starts it WITHOUT verification; fix gpg, then: histmersion-signing use-gpg $(hk_gpg_key)" >&2
  fi
  printf '%s\n' "$dest/bin/histmersion-server"
}
