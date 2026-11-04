# Shared helpers for the scripts of the scalafix skill. Source it, don't run it.

SCALAFIX_VERSION=${SCALAFIX_VERSION:-0.14.9}
# scalafix-cli is published per full Scala version; this one only runs the CLI, any target Scala 2.13/3 version works.
SCALAFIX_CLI_SCALA=${SCALAFIX_CLI_SCALA:-2.13.18}

scalafix_cli() {
  if command -v scalafix >/dev/null 2>&1; then
    scalafix "$@"
  else
    scala-cli run --server=false -q -S "$SCALAFIX_CLI_SCALA" \
      --dep "ch.epfl.scala:scalafix-cli_$SCALAFIX_CLI_SCALA:$SCALAFIX_VERSION" \
      --main-class scalafix.cli.Cli -- "$@"
  fi
}

# Sets the globals $work (a temp copy, removed on exit), $scala_version, $classpath and $sources.
prepare_project() {
  local project=$1
  work=$(cd "$(mktemp -d)" && pwd -P)  # symlinks resolved, or scalafix can't match the SemanticDB paths (/var vs /private/var on macOS)
  trap 'rm -rf "$work"' EXIT
  cp -R "$project"/. "$work"/
  rm -rf "$work/.scala-build" "$work/.bsp" "$work/.metals"

  scala_version=$(grep -rhoE '^//> using scala [0-9.]+' --include='*.scala' "$work" | head -1 | awk '{print $4}')
  if [ -z "$scala_version" ]; then
    echo "error: no '//> using scala <version>' directive found in $project" >&2
    exit 2
  fi

  echo "== Compiling $project with SemanticDB (Scala $scala_version)"
  if ! scala-cli compile "$work" --semanticdb --semanticdb-targetroot "$work/.semanticdb" >"$work/.compile.log" 2>&1; then
    cat "$work/.compile.log" >&2
    echo "error: the project doesn't compile" >&2
    exit 2
  fi
  classpath=$(scala-cli compile "$work" --print-class-path 2>/dev/null | tail -1)
  sources=$(cd "$work" && find . -name '*.scala' -not -path './.*' | sed 's|^\./||' | sort)
}

# Turns a rule argument into a --rules value: a path to a .scala file becomes `file:<absolute path>`,
# anything else (a built-in rule like `ProcedureSyntax`, `dependency:...`) is passed through.
rule_spec() {
  case "$1" in
    *.scala) echo "file:$(cd "$(dirname "$1")" && pwd)/$(basename "$1")" ;;
    *) echo "$1" ;;
  esac
}

run_scalafix() {
  local spec=$1
  shift
  # shellcheck disable=SC2086  # $sources is a list of file names
  (cd "$work" && scalafix_cli \
    --rules "$spec" \
    --scala-version "$scala_version" \
    --sourceroot "$work" \
    --semanticdb-targetroots "$work/.semanticdb" \
    --classpath "$classpath" \
    "$@" \
    $sources)
}

# Checks the lints reported in the scalafix log against `// assert: RuleName` markers in the sources.
# A marker expects a lint on its line whose category is RuleName (or RuleName.<id>); `// assert:` alone accepts any.
# Lints on lines without a marker are unexpected. Prints OK/FAIL, returns 1 on a mismatch.
# Without any marker in the sources, it only lists the reported lints.
check_lints() {
  local log=$1 expected actual
  # shellcheck disable=SC2086
  expected=$(cd "$work" && grep -n -o '// assert:[[:space:]]*[A-Za-z0-9_.]*' $sources /dev/null \
    | sed -E 's#^([^:]+):([0-9]+):// assert:[[:space:]]*#\1:\2 #')
  actual=$(sed -E -n "s#^${work}/([^:]+):([0-9]+):[0-9]+: (error|warning|info): \[([^]]+)\].*#\1:\2 \4#p" "$log" \
    | grep -v ' UnusedScalafixSuppression$')
  if [ -z "$expected" ]; then
    if [ -n "$actual" ]; then
      echo "note: $(echo "$actual" | wc -l | tr -d ' ') lint(s) reported, not checked (no // assert: markers in the sources):"
      echo "$actual" | sed 's/^/  /'
    fi
    return 0
  fi
  printf '%s\n' "$expected" "--" "$actual" | awk '
    $0 == "--" { part = 2; next }
    part != 2 { want[$1] = $2; next }
    $1 != "" { got[$1] = got[$1] " " $2 }
    END {
      bad = 0
      for (loc in want) {
        ok = 0; n = split(got[loc], cats, " ")
        for (i = 1; i <= n; i++) if (want[loc] == "" || cats[i] == want[loc] || index(cats[i], want[loc] ".") == 1) ok = 1
        if (!ok) { print "missing lint:    " loc (want[loc] == "" ? "" : " [" want[loc] "]"); bad = 1 }
      }
      for (loc in got) if (!(loc in want)) { print "unexpected lint: " loc " [" substr(got[loc], 2) "]"; bad = 1 }
      exit bad
    }'
  local status=$?
  if [ $status -eq 0 ]; then echo "OK: the lints match the // assert: markers"; else echo "FAIL: the lints don't match the // assert: markers"; fi
  return $status
}
