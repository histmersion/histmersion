#!/usr/bin/env bash
# Build every kind of plugin `histmersion plugin new` writes, as a plugin author would, against a bundle:
#
#   scripts/bundle/plugin-template-check.sh <bundle-dir> [kind…]      (default: every kind)
#
# For each kind: the bundle's CLI writes a project into a temporary directory, `scala-cli test` builds it
# and runs its tests, `scala-cli --power publish` writes its jar and POM. It fails when one of them fails
# or the compiler warns, when the jar holds anything but the plugin's own classes, its services file, its
# defaults and its native-image metadata, or when the POM lists the plugin API, the platform or the testkit.
#
# HOME is a temporary directory, so the check also proves the template names no path of this machine: the
# project resolves Histmersion's artifacts from that HOME's ~/.m2/repository (m2Local), which `plugin new`
# fills from the bundle's maven/. coursier's m2Local follows the JVM's user.home, not HOME, so scala-cli is
# given -Duser.home too. Downloads go through coursier's cache: COURSIER_CACHE when set, else the caller's
# usual one (a cache, never a repository the build reads as a source); HISTMERSION_TEMPLATE_CHECK_COLD=1
# uses an empty one instead, as a machine that never built Scala would.
# Needs scala-cli on PATH and a JDK 17+ (JAVA_HOME, else scala-cli's own).
set -euo pipefail
b="$(cd "${1:?usage: $0 <bundle-dir> [kind…]}" && pwd)"
shift
kinds=("$@")
[ "${#kinds[@]}" -gt 0 ] || kinds=(source-fetcher media-collection event-catalogue ai-provider secret-vault)
command -v scala-cli >/dev/null 2>&1 || { echo "scala-cli is not on PATH (https://scala-cli.virtuslab.org)" >&2; exit 2; }
[ -x "$b/bin/histmersion" ] && [ -d "$b/maven" ] || { echo "not a bundle with a plugin repository: $b" >&2; exit 2; }

caller_home="$HOME"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
home="$work/home"; mkdir -p "$home" "$work/data" "$work/projects"
: > "$work/empty.conf"
if [ "${HISTMERSION_TEMPLATE_CHECK_COLD:-0}" = 1 ]; then cache="$work/coursier"
elif [ -n "${COURSIER_CACHE:-}" ]; then cache="$COURSIER_CACHE"
elif [ "$(uname -s)" = Darwin ]; then cache="$caller_home/Library/Caches/Coursier/v1"
else cache="${XDG_CACHE_HOME:-$caller_home/.cache}/coursier/v1"
fi

fail() { echo "::error::plugin template ($kind): $*" >&2; exit 1; }
now() { date +%s; }
# the bundle's CLI and scala-cli, both under the temporary HOME, never the caller's config or data
cli() { HOME="$home" HISTMERSION_INSTALL_DIR="$b" HISTMERSION_DATA_DIR="$work/data" HISTMERSION_CONFIG="$work/empty.conf" \
  "$b/bin/histmersion" "$@"; }
# (--power must come before -D, or scala-cli takes the subcommand's options for its own)
scli() { HOME="$home" COURSIER_CACHE="$cache" scala-cli --power -Duser.home="$home" "$@"; }
# a compiler warning fails the check; the JVM's own WARNING lines (sun.misc.Unsafe) do not
warned() { sed 's/\x1b\[[0-9;]*m//g' "$1" | grep -E '^\[warn\]' || true; }

total_start="$(now)"
report=()
for kind in "${kinds[@]}"; do
  id="check-${kind}"
  pkg="check${kind//-/}"
  dir="$work/projects/$kind"
  cli plugin new "$id" --kind "$kind" --dir "$dir" > "$work/$kind.new.log" 2>&1 || fail "plugin new: $(cat "$work/$kind.new.log")"
  grep -qx "//> using repository m2Local" "$dir/project.scala" || fail "project.scala does not resolve from m2Local"
  if grep -rqE "$caller_home|$work|file:/" "$dir" --include='*.scala'; then fail "the project names a path of this machine"; fi

  # from another directory than the project's: the recordings must be found by the project's path
  t0="$(now)"
  (cd "$work" && scli test --server=false "$dir") > "$work/$kind.test.log" 2>&1 \
    || { tail -60 "$work/$kind.test.log" >&2; fail "scala-cli test failed"; }
  t1="$(now)"
  [ -z "$(warned "$work/$kind.test.log")" ] || fail "the compiler warned: $(warned "$work/$kind.test.log")"
  grep -q "finished: 0 failed" "$work/$kind.test.log" || fail "no test suite ran: $(tail -20 "$work/$kind.test.log")"

  (cd "$work" && scli publish --server=false "$dir" --publish-repository "$dir/repo") > "$work/$kind.publish.log" 2>&1 \
    || { tail -60 "$work/$kind.publish.log" >&2; fail "scala-cli publish failed"; }
  t2="$(now)"
  [ -z "$(warned "$work/$kind.publish.log")" ] || fail "publish warned: $(warned "$work/$kind.publish.log")"

  base="$dir/repo/$pkg/${id}_3/0.1.0/${id}_3-0.1.0"
  [ -f "$base.jar" ] && [ -f "$base.pom" ] || fail "publish wrote no jar and POM at $base.{jar,pom}: $(find "$dir/repo" -type f)"
  [ ! -e "$base-javadoc.jar" ] || fail "publish built a doc jar (publish.doc false is not honoured)"

  # the jar: the plugin's own classes, its services file, its defaults and its native-image metadata
  entries="$(unzip -Z1 "$base.jar")" || fail "$base.jar is not a jar"
  stray="$(grep -vE "^(META-INF/|META-INF/MANIFEST\.MF|META-INF/services/|META-INF/services/com\.histmersion\.plugin\.Plugin|META-INF/histmersion/(plugins/)?|META-INF/histmersion/plugins/$id\.conf|META-INF/native-image/($pkg/($id/)?)?|META-INF/native-image/$pkg/$id/reachability-metadata\.json|$pkg/(.+/)?|$pkg/.+\.(class|tasty))$" <<<"$entries" || true)"
  [ -z "$stray" ] || fail "the jar holds more than the plugin: $(tr '\n' ' ' <<<"$stray")"
  for need in "META-INF/services/com.histmersion.plugin.Plugin" "META-INF/histmersion/plugins/$id.conf" \
      "META-INF/native-image/$pkg/$id/reachability-metadata.json"; do
    grep -qx "$need" <<<"$entries" || fail "the jar lacks $need"
  done
  grep -qE "^$pkg/[^/]+\.class$" <<<"$entries" || fail "the jar holds no class of the plugin"

  # the POM: none of what the server provides (API, platform) nor the tests' testkit
  listed="$(grep -E '<artifactId>histmersion-plugin-(api|platform|testkit)' "$base.pom" || true)"
  [ -z "$listed" ] || fail "the POM lists $(tr -s ' ' <<<"$listed")"

  report+=("$(printf '%-17s test %4ss · publish %4ss · jar %2s entries' "$kind" "$((t1 - t0))" "$((t2 - t1))" "$(wc -l <<<"$entries" | tr -d ' ')")")
done

echo "plugin template check against $b (HOME=$home, coursier cache: $( [ "$cache" = "$work/coursier" ] && echo cold || echo warm )):"
printf '  %s\n' "${report[@]}"
echo "  total $(( $(now) - total_start ))s: every kind built, tested and published; jars and POMs hold only the plugin"
