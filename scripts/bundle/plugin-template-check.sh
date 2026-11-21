#!/usr/bin/env bash
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
cli() { HOME="$home" HISTMERSION_INSTALL_DIR="$b" HISTMERSION_DATA_DIR="$work/data" HISTMERSION_CONFIG="$work/empty.conf" \
  "$b/bin/histmersion" "$@"; }
scli() { HOME="$home" COURSIER_CACHE="$cache" scala-cli --power -Duser.home="$home" "$@"; }
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

  entries="$(unzip -Z1 "$base.jar")" || fail "$base.jar is not a jar"
  stray="$(grep -vE "^(META-INF/|META-INF/MANIFEST\.MF|META-INF/services/|META-INF/services/com\.histmersion\.plugin\.Plugin|META-INF/histmersion/(plugins/)?|META-INF/histmersion/plugins/$id\.conf|META-INF/native-image/($pkg/($id/)?)?|META-INF/native-image/$pkg/$id/reachability-metadata\.json|$pkg/(.+/)?|$pkg/.+\.(class|tasty))$" <<<"$entries" || true)"
  [ -z "$stray" ] || fail "the jar holds more than the plugin: $(tr '\n' ' ' <<<"$stray")"
  for need in "META-INF/services/com.histmersion.plugin.Plugin" "META-INF/histmersion/plugins/$id.conf" \
      "META-INF/native-image/$pkg/$id/reachability-metadata.json"; do
    grep -qx "$need" <<<"$entries" || fail "the jar lacks $need"
  done
  grep -qE "^$pkg/[^/]+\.class$" <<<"$entries" || fail "the jar holds no class of the plugin"

  listed="$(grep -E '<artifactId>histmersion-plugin-(api|platform|testkit)' "$base.pom" || true)"
  [ -z "$listed" ] || fail "the POM lists $(tr -s ' ' <<<"$listed")"

  report+=("$(printf '%-17s test %4ss · publish %4ss · jar %2s entries' "$kind" "$((t1 - t0))" "$((t2 - t1))" "$(wc -l <<<"$entries" | tr -d ' ')")")
done

echo "plugin template check against $b (HOME=$home, coursier cache: $( [ "$cache" = "$work/coursier" ] && echo cold || echo warm )):"
printf '  %s\n' "${report[@]}"
echo "  total $(( $(now) - total_start ))s: every kind built, tested and published; jars and POMs hold only the plugin"
