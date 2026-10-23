#!/usr/bin/env bash
# bin/histmersion-toolchain — what a LOCAL build or relink needs (docs/research-plugins.md §8).
#
#   histmersion-toolchain check      GraalVM, native-image, C toolchain, memory, disk: can this machine
#                                    relink? (exit 0 yes, 2 no — and what to do)
#   histmersion-toolchain preflight  the same without GraalVM: could it, once GraalVM is provisioned?
#                                    (what an install asks before downloading it)
#   histmersion-toolchain install    download GraalVM CE at the kit's pinned version from its official
#                                    GitHub release, check its pinned SHA-256, unpack it into
#                                    <data>/toolchain/graalvm-ce-<version> (~640 MB)
#   histmersion-toolchain path       the GraalVM home a relink would use
#   histmersion-toolchain remove     delete the provisioned GraalVM
#
# GraalVM CE is GPLv2 with the Classpath Exception (libexec/graalvm-pins.txt has the note); the download
# is curl (no quarantine attribute), the pin is the release's own checksum. It lives in the data
# directory, not the install: an install replaces the install directory wholesale.
# HISTMERSION_GRAALVM_PINS (another pins file) and HISTMERSION_TOOLCHAIN_PLATFORM exist for the tests.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
common="$here/../libexec/kit-common.sh"; [ -f "$common" ] || common="$here/kit-common.sh"
# shellcheck source=kit-common.sh
. "$common"

install="${HISTMERSION_HOME:-$(cd "$here/.." && pwd)}"
pins="${HISTMERSION_GRAALVM_PINS:-$here/../libexec/graalvm-pins.txt}"
[ -f "$pins" ] || pins="$here/graalvm-pins.txt"
version() {
  if [ -f "$install/kit/graalvm-version" ]; then cat "$install/kit/graalvm-version"
  else hk_pinned_version "$pins"; fi
}
platform() {
  if [ -n "${HISTMERSION_TOOLCHAIN_PLATFORM:-}" ]; then echo "$HISTMERSION_TOOLCHAIN_PLATFORM"; return; fi
  local arch
  case "$(uname -m)" in arm64|aarch64) arch=aarch64 ;; x86_64|amd64) arch=x64 ;; *) arch="$(uname -m)" ;; esac
  echo "$(hk_os)-$arch"
}
v="$(version)"
[ -n "$v" ] || hk_die 2 "no GraalVM version: neither $install/kit/graalvm-version nor $pins"
dest="$(hk_data_dir)/toolchain/graalvm-ce-$v"

cmd="${1:-check}"
case "$cmd" in
  path)
    hk_find_graalvm "$v" || hk_die 2 "no GraalVM CE $v (histmersion-toolchain install)"
    ;;

  check|preflight)
    # preflight: everything but GraalVM — what an installer asks BEFORE downloading ~340 MB of it
    ok=1
    if [ "$cmd" = check ]; then
      if gv="$(hk_find_graalvm "$v")"; then echo "- GraalVM CE $v: $gv"
      else echo "- GraalVM CE $v: MISSING — histmersion-toolchain install (or HISTMERSION_GRAALVM_HOME)"; ok=""; fi
    fi
    if cc="$(hk_cc)"; then echo "- C toolchain: $cc"
    else
      ok=""
      if [ "$(hk_os)" = macos ]; then echo "- C toolchain: MISSING — xcode-select --install"
      else echo "- C toolchain: MISSING — apt install build-essential zlib1g-dev (or dnf install gcc glibc-devel zlib-devel libstdc++-static)"; fi
    fi
    mem="$(hk_mem_bytes)"; min="${HISTMERSION_RELINK_MIN_MEMORY_GB:-16}"
    if [ "$((mem / 1073741824))" -ge "$min" ]; then echo "- memory: $((mem / 1073741824)) GB (a relink peaks near 13.5 GB)"
    else echo "- memory: $((mem / 1073741824)) GB — LESS than the ${min} GB a relink wants (HISTMERSION_RELINK_MIN_MEMORY_GB)"; ok=""; fi
    where="$(hk_data_dir)"; while [ ! -d "$where" ] && [ "$where" != / ]; do where="$(dirname "$where")"; done
    free="$(hk_free_kb "$where")"
    need_kb=1048576; [ "$cmd" = preflight ] && [ -z "$(hk_find_graalvm "$v" 2>/dev/null)" ] && need_kb=2097152
    if [ "${free:-0}" -ge "$need_kb" ]; then echo "- disk: $(( ${free:-0} / 1048576 )) GB free (GraalVM ~0.7 GB, each binary ~0.2 GB)"
    else echo "- disk: $(( ${free:-0} / 1048576 )) GB free — LESS than the $((need_kb / 1048576)) GB a local build needs"; ok=""; fi
    if [ -n "$ok" ]; then echo "this machine can relink"; exit 0
    else echo "this machine cannot relink now — JVM mode (histmersion-relink --jvm) needs only a JDK"; exit 2; fi
    ;;

  install)
    if [ -x "$dest/bin/native-image" ] || [ -x "$dest/Contents/Home/bin/native-image" ]; then
      echo "GraalVM CE $v is already provisioned: $dest"; exit 0
    fi
    p="$(platform)"
    line="$(awk -v v="$v" -v p="$p" '$1 == v && $2 == p {print; exit}' "$pins")"
    [ -n "$line" ] || hk_die 2 "no pinned GraalVM CE $v for $p in $pins (the release ships none for this platform: point HISTMERSION_GRAALVM_HOME at a GraalVM CE $v, or use JVM mode, histmersion-relink --jvm)"
    sha="$(echo "$line" | awk '{print $3}')" url="$(echo "$line" | awk '{print $4}')"
    lock="$(hk_data_dir)/toolchain/.lock"
    hk_lock "$lock" || hk_die 2 "another toolchain install holds $lock"
    tmp="$(hk_data_dir)/toolchain/.download.$$"
    trap 'rm -rf "$tmp"; hk_unlock "$lock"' EXIT
    mkdir -p "$tmp/x"
    echo "→ GraalVM CE $v ($p) — GPLv2 with the Classpath Exception — from $url"
    curl -fL --retry 3 --progress-bar -o "$tmp/graalvm.tar.gz" "$url" || hk_die 2 "download failed: $url"
    got="$(hk_sha256 "$tmp/graalvm.tar.gz")"
    [ "$got" = "$sha" ] || hk_die 2 "SHA-256 mismatch: got $got, pinned $sha — not unpacked (retry, or point HISTMERSION_GRAALVM_HOME at a GraalVM CE $v)"
    echo "  SHA-256 $sha (pinned) ✓"
    tar -xzf "$tmp/graalvm.tar.gz" -C "$tmp/x"
    top="$(find "$tmp/x" -mindepth 1 -maxdepth 1 -type d | head -1)"
    [ -n "$top" ] || hk_die 2 "the archive holds no directory"
    home="$top"; [ -d "$top/Contents/Home" ] && home="$top/Contents/Home"
    [ -x "$home/bin/native-image" ] && [ "$(hk_jdk_version "$home")" = "$v" ] \
      || hk_die 2 "the archive is not GraalVM CE $v with native-image"
    rm -rf "$dest" && mkdir -p "$(dirname "$dest")" && mv "$top" "$dest"
    echo "GraalVM CE $v provisioned: $dest ($(du -sh "$dest" | awk '{print $1}'))"
    ;;

  remove)
    rm -rf "$dest"
    echo "removed $dest"
    ;;

  *) hk_die 1 "unknown command '$cmd' (check, preflight, install, path, remove)" ;;
esac
