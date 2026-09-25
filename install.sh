#!/usr/bin/env bash
#
# Histmersion installer — one command from a fresh clone to a running stack + a `histmersion` binary.
#
#   ./install.sh              install / update everything
#   ./install.sh --dry-run    print exactly what would happen, change nothing
#   ./install.sh --help       all flags
#
# Idempotent: re-running it rebuilds what changed and leaves everything else alone. No port, URL or
# path is hardcoded here — they all come from .env (see .env.example) or the HISTMERSION_* environment,
# with the same defaults as modules/core/src/main/resources/reference.conf.

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# ── options ──────────────────────────────────────────────────────────────────────────────────────
DRY_RUN=0
SKIP_CLI=0
SKIP_DOCKER=0

usage() {
  cat <<'EOF'
Usage: ./install.sh [options]

  --dry-run       Print the plan; touch nothing.
  --skip-cli      Do not build/install the native `histmersion` CLI binary.
  --skip-docker   Do not build or start the Docker stack.
  -h, --help      This text.

Environment (or .env — copy .env.example):
  HISTMERSION_PORT         server port (default 8000)
  HISTMERSION_BIND         host interface compose publishes on (default 127.0.0.1)
  HISTMERSION_PUBLIC_URL   how clients reach the server (default http://localhost:$HISTMERSION_PORT)
  HISTMERSION_CORPUS_DIR   host path of the markdown corpus, mounted read-only at /corpus
  HISTMERSION_BIN_DIR      where to install the CLI (default $HOME/bin)
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)     DRY_RUN=1 ;;
    --skip-cli)    SKIP_CLI=1 ;;
    --skip-docker) SKIP_DOCKER=1 ;;
    -h|--help)     usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

# ── output helpers ───────────────────────────────────────────────────────────────────────────────
if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; N=$'\033[0m'
else B=""; G=""; Y=""; R=""; N=""; fi

step() { printf '%s==>%s %s%s%s\n' "$G" "$N" "$B" "$*" "$N"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '%s !! %s%s\n' "$Y" "$*" "$N" >&2; }
die()  { printf '%sERROR:%s %s\n' "$R" "$N" "$*" >&2; exit 1; }

# Run a command, or (in --dry-run) just show it.
run() {
  if [ "$DRY_RUN" = 1 ]; then printf '    %s[dry-run]%s %s\n' "$Y" "$N" "$*"
  else "$@"
  fi
}
# Write $2 to file $1 (or describe it).
write_file() {
  local path="$1" content="$2"
  if [ "$DRY_RUN" = 1 ]; then
    printf '    %s[dry-run]%s write %s (%s lines)\n' "$Y" "$N" "$path" "$(printf '%s' "$content" | wc -l | tr -d ' ')"
  else
    mkdir -p "$(dirname "$path")"
    printf '%s\n' "$content" > "$path"
    info "wrote $path"
  fi
}

# ── configuration ────────────────────────────────────────────────────────────────────────────────
step "Configuration"

# .env is DATA, not a shell script. `set -a; . ./.env` EXECUTES it, so a `$(…)` anywhere in a value
# runs as a side effect of installing — parse it line by line instead: no sourcing, no eval.
load_env() {
  local file="$1" line key value
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"                   # drop leading whitespace
    case "$line" in ''|'#'*) continue ;; esac
    case "$line" in *=*) ;; *) warn ".env: ignoring line without '=': $line"; continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    if ! [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      warn ".env: ignoring invalid key: $key"
      continue
    fi
    # one layer of surrounding quotes comes off; the rest of the value is taken verbatim (no
    # expansion, no escape handling) — docker compose reads the same file the same way.
    case "$value" in
      \"*\") value="${value:1:${#value}-2}" ;;
      \'*\') value="${value:1:${#value}-2}" ;;
    esac
    export "$key=$value"
  done < "$file"
}

if [ -f .env ]; then
  info "reading .env"
  load_env ./.env
else
  info ".env not found — using defaults (copy .env.example to customise)"
fi

HISTMERSION_PORT="${HISTMERSION_PORT:-8000}"
HISTMERSION_BIND="${HISTMERSION_BIND:-127.0.0.1}"
HISTMERSION_PUBLIC_URL="${HISTMERSION_PUBLIC_URL:-http://localhost:${HISTMERSION_PORT}}"
HISTMERSION_CORPUS_DIR="${HISTMERSION_CORPUS_DIR:-./corpus}"
HISTMERSION_BIN_DIR="${HISTMERSION_BIN_DIR:-$HOME/bin}"
export HISTMERSION_PORT HISTMERSION_BIND HISTMERSION_PUBLIC_URL HISTMERSION_CORPUS_DIR

MCP_URL="${HISTMERSION_PUBLIC_URL%/}/mcp"
HEALTH_URL="${HISTMERSION_PUBLIC_URL%/}/api/health"

info "port         ${HISTMERSION_PORT}"
info "published on ${HISTMERSION_BIND}"
info "public url   ${HISTMERSION_PUBLIC_URL}"
info "corpus dir   ${HISTMERSION_CORPUS_DIR}"
info "cli target   ${HISTMERSION_BIN_DIR}/histmersion"
if [ "$DRY_RUN" = 1 ]; then info "${Y}dry run — nothing will be changed${N}"; fi

# ── prerequisites ────────────────────────────────────────────────────────────────────────────────
step "Prerequisites"

if [ "$SKIP_DOCKER" = 0 ]; then
  command -v docker >/dev/null 2>&1 || die "docker not found. Install Docker Desktop (or the engine) and re-run."
  if docker compose version >/dev/null 2>&1; then
    info "docker $(docker --version | sed 's/Docker version //;s/,.*//') · compose ok"
  else
    die "'docker compose' is unavailable (Compose v2 is required)."
  fi
  if [ "$DRY_RUN" = 0 ] && ! docker info >/dev/null 2>&1; then
    die "the Docker daemon is not running — start Docker Desktop and re-run."
  fi
else
  info "docker checks skipped (--skip-docker)"
fi

# sdkman gives us the exact toolchain pinned in .sdkmanrc (GraalVM CE + sbt). Only the CLI native
# image needs it; the server is built inside Docker with its own pinned GraalVM.
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
have_sdkman=0
[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ] && have_sdkman=1

if [ "$SKIP_CLI" = 0 ]; then
  if [ "$have_sdkman" = 1 ]; then
    info "sdkman found at $SDKMAN_DIR"
    if [ "$DRY_RUN" = 1 ]; then
      printf '    %s[dry-run]%s source sdkman-init.sh && sdk env install && sdk env\n' "$Y" "$N"
      grep -E '^(java|sbt|scala)=' .sdkmanrc | sed 's/#.*//;s/[[:space:]]*$//;s/^/              /'
    else
      # sdkman's shell functions are not written for `set -eu`: sdkman-install.sh dereferences $3
      # unconditionally, which aborts the whole script under -u. Relax both around every sdk call.
      info "installing the toolchain pinned in .sdkmanrc (no-op if already present)"
      set +eu
      . "$SDKMAN_DIR/bin/sdkman-init.sh"
      sdk env install || warn "'sdk env install' reported a problem — continuing with what is on PATH"
      sdk env         || warn "'sdk env' reported a problem — continuing with what is on PATH"
      set -eu
      info "java $(java -version 2>&1 | head -1)"
    fi
  else
    warn "sdkman not found at $SDKMAN_DIR."
    warn "Install it (https://sdkman.io) then run 'sdk env install' for the pinned GraalVM + sbt,"
    warn "or make sure the pinned GraalVM CE JDK and sbt are already on PATH (see .sdkmanrc)."
  fi

  if [ "$DRY_RUN" = 0 ]; then
    command -v sbt >/dev/null 2>&1 || die "sbt not found on PATH (see .sdkmanrc / sdkman above)."
    if ! command -v native-image >/dev/null 2>&1 && [ -z "${GRAALVM_HOME:-}" ]; then
      die "native-image not found on PATH and GRAALVM_HOME is unset — a GraalVM JDK is required to build the CLI."
    fi
  fi
else
  info "toolchain checks skipped (--skip-cli)"
fi

# ── 1. the CLI native image ──────────────────────────────────────────────────────────────────────
if [ "$SKIP_CLI" = 0 ]; then
  step "Building the histmersion CLI (GraalVM native image, ~35s)"
  # `--server -batch`: sbt 2's thin client shares one server across invocations and drops the
  # caller's environment; --server forces a dedicated, env-carrying server for this build.
  run sbt --server -batch cli/nativeImage

  # sbt 2 puts a module's output under <root>/target/out/jvm/<scalaVersion>/<name>/, not
  # modules/<m>/target/. That is an sbt implementation detail, so locate the binary rather than
  # encode the layout.
  CLI_BIN=""
  if [ "$DRY_RUN" = 0 ]; then
    CLI_BIN="$(find target modules -type f -name histmersion-cli -perm -u+x -print 2>/dev/null | head -n 1)"
    [ -n "$CLI_BIN" ] || die "the native image 'histmersion-cli' was not produced — check the sbt output above."
    info "built $CLI_BIN"
  else
    CLI_BIN="target/out/jvm/<scala>/histmersion-cli/histmersion-cli"
  fi

  step "Installing the CLI to ${HISTMERSION_BIN_DIR}/histmersion"
  run mkdir -p "$HISTMERSION_BIN_DIR"
  run install -m 0755 "$CLI_BIN" "$HISTMERSION_BIN_DIR/histmersion"

  case ":${PATH}:" in
    *":${HISTMERSION_BIN_DIR}:"*) info "${HISTMERSION_BIN_DIR} is on PATH" ;;
    *) warn "${HISTMERSION_BIN_DIR} is NOT on your PATH. Add this to your shell profile:"
       warn "    export PATH=\"${HISTMERSION_BIN_DIR}:\$PATH\"" ;;
  esac
else
  step "Skipping the CLI build (--skip-cli)"
fi

# ── 2. the Docker stack ──────────────────────────────────────────────────────────────────────────
if [ "$SKIP_DOCKER" = 0 ]; then
  step "Preparing the corpus mount"
  if [ -d "$HISTMERSION_CORPUS_DIR" ]; then
    info "corpus dir exists: $HISTMERSION_CORPUS_DIR"
  else
    warn "corpus dir '$HISTMERSION_CORPUS_DIR' does not exist — creating an empty one."
    warn "Point HISTMERSION_CORPUS_DIR at your wiki/sources directory in .env to seed from it."
    run mkdir -p "$HISTMERSION_CORPUS_DIR"
  fi

  step "Building the Docker images (first run: several minutes — native-image + OCR deps)"
  run docker compose build

  step "Starting the stack"
  run docker compose up -d

  step "Waiting for ${HEALTH_URL}"
  if [ "$DRY_RUN" = 1 ]; then
    printf '    %s[dry-run]%s poll %s until it answers (up to 180s)\n' "$Y" "$N" "$HEALTH_URL"
  else
    ok=0
    for _ in $(seq 1 90); do
      if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null 2>&1; then ok=1; break; fi
      sleep 2
    done
    if [ "$ok" = 1 ]; then
      info "healthy: $(curl -fsS --max-time 3 "$HEALTH_URL")"
    else
      docker compose logs --tail 40 server >&2 || true
      die "the server did not become healthy within 180s (logs above). 'docker compose logs -f server' for more."
    fi
  fi
else
  step "Skipping Docker (--skip-docker)"
fi

# ── 3. MCP configuration (generated — this is the one place a port is rendered) ──────────────────
step "Generating MCP configuration for ${MCP_URL}"

MCP_JSON=$(cat <<EOF
{
  "mcpServers": {
    "histmersion": {
      "type": "http",
      "url": "${MCP_URL}"
    }
  }
}
EOF
)
write_file ".mcp.json" "$MCP_JSON"
write_file "plugin/.mcp.json" "$MCP_JSON"

# ── done ─────────────────────────────────────────────────────────────────────────────────────────
if [ "$DRY_RUN" = 1 ]; then
  step "Dry run complete — nothing was changed"
  echo
  echo "  Re-run without --dry-run to apply the plan above."
  echo
  exit 0
fi

step "Done"
cat <<EOF

  Histmersion is running.

  Web UI           ${HISTMERSION_PUBLIC_URL}
  MCP endpoint     ${MCP_URL}
  Health           ${HEALTH_URL}

  Next steps:

    histmersion health              # verify the CLI reaches the server
    histmersion seed                # import the corpus bind-mounted at /corpus
                               #   (paths are resolved by the SERVER, not your shell —
                               #    set HISTMERSION_CORPUS_DIR in .env to point at wiki/sources)
    histmersion search "sea peoples"
    open ${HISTMERSION_PUBLIC_URL}

  To let agents use it, install the Claude plugin (see plugin/README.md):

    claude plugin marketplace add ${REPO_ROOT}
    claude plugin install histmersion@histmersion

  Manage the stack:  just docker-up · just docker-down · just docker-logs
EOF
