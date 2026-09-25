# Histmersion — task runner (https://just.systems). `just` lists everything.
# Nothing here hardcodes a port: values come from .env or the environment.

set dotenv-load := true
set shell := ["bash", "-euo", "pipefail", "-c"]

port        := env("HISTMERSION_PORT", "8000")
public_url  := env("HISTMERSION_PUBLIC_URL", "http://localhost:" + port)
bundle_dir  := env("BUNDLE_DIR", "dist/histmersion")
corpus_dir  := env("HISTMERSION_CORPUS_DIR", justfile_directory() / "corpus")
data_dir    := env("HISTMERSION_DATA_DIR", home_directory() / "Library/Application Support/Histmersion")
label       := "com.histmersion.server"

# sbt 2's thin client shares one server and drops the caller's environment; `--server -batch`
# forces a dedicated, env-carrying, non-interactive one per invocation.
sbt := "sbt --server -batch"

# The portable PostgreSQL (runtime/postgres, the one the bundle ships) — also the test database.
pg           := justfile_directory() / "runtime/postgres/.pixi/envs/default/bin"
test_db_dir  := justfile_directory() / "target/test-pg"
test_db_port := "55432"
test_db_url  := "jdbc:postgresql://127.0.0.1:" + test_db_port + "/histmersion_test"

[private]
default:
    @just --list --unsorted

# ── the native app (no Docker) ─────────────────────────────────────────────────────────────────

# Build the native bundle: server + CLI, PostgreSQL 18 + PostGIS, OCR converter
[group('app')]
bundle dir=bundle_dir:
    scripts/bundle/postgres.sh {{dir}}
    scripts/bundle/converter.sh {{dir}}
    scripts/bundle/server.sh {{dir}}

# Build the bundle and install it (~/Applications/Histmersion + LaunchAgent + ~/bin/histmersion)
[group('app')]
install: bundle
    HISTMERSION_CORPUS_DIR="{{corpus_dir}}" scripts/bundle/install-macos.sh {{bundle_dir}} --move

# Remove the app; the data stays unless you pass --purge
[group('app')]
uninstall *args:
    scripts/bundle/uninstall-macos.sh {{args}}

# Start the installed app
[group('app')]
start:
    launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/{{label}}.plist

# Stop the installed app (until the next login or `just start`)
[group('app')]
stop:
    launchctl bootout gui/$(id -u)/{{label}}

# Restart the installed app
[group('app')]
restart:
    launchctl kickstart -k gui/$(id -u)/{{label}}

# Follow the logs: server, postgres or converter
[group('app')]
logs which="server":
    tail -f "{{data_dir}}/logs/{{which}}.log"

# Curl the health endpoint
[group('app')]
health:
    @curl -fsS "{{public_url}}/api/health" && echo

# ── build & test ─────────────────────────────────────────────────────────────────────────────────

# Pure test suites (no database)
[group('dev')]
test:
    {{sbt}} test

# Every suite, DB-backed ones on a throwaway Postgres that is removed afterwards
[group('dev')]
test-it: test-db
    #!/usr/bin/env bash
    set -uo pipefail
    HISTMERSION_TEST_DB_URL={{test_db_url}} HISTMERSION_PG_BIN_DIR={{pg}} {{sbt}} 'testOnly *'; rc=$?
    just test-db-down
    exit $rc

# Start a throwaway Postgres (histmersion_test on :55432) from the bundled runtime — never the live corpus
[group('dev')]
test-db:
    (cd runtime/postgres && pixi install >/dev/null)
    -"{{pg}}/pg_ctl" -D "{{test_db_dir}}" -m immediate stop >/dev/null 2>&1
    rm -rf "{{test_db_dir}}" && mkdir -p "$(dirname "{{test_db_dir}}")"
    "{{pg}}/initdb" -D "{{test_db_dir}}" -U histmersion --auth=trust -E UTF8 --locale=C >/dev/null
    "{{pg}}/pg_ctl" -D "{{test_db_dir}}" -l "{{test_db_dir}}.log" -w \
      -o "-p {{test_db_port}} -c listen_addresses=127.0.0.1 -c unix_socket_directories=''" start >/dev/null
    "{{pg}}/createdb" -h 127.0.0.1 -p {{test_db_port}} -U histmersion histmersion_test
    @echo "HISTMERSION_TEST_DB_URL={{test_db_url}}"

# Stop and delete the throwaway test Postgres
[group('dev')]
test-db-down:
    -"{{pg}}/pg_ctl" -D "{{test_db_dir}}" -m fast -w stop >/dev/null 2>&1
    rm -rf "{{test_db_dir}}" "{{test_db_dir}}.log"

# Build both GraalVM native images with the pinned JDK (into target/)
[group('dev')]
native:
    scripts/bundle/server.sh target/native

# Regenerate the CLI reference table in README.md from `histmersion --help`
[group('dev')]
cli-docs:
    ./tools/gen-cli-docs.sh

# Remove build output (sbt targets, native images, dist/)
[group('dev')]
clean:
    {{sbt}} clean
    rm -rf dist target/native
    find target modules -type f \( -name histmersion-cli -o -name histmersion-server \) -delete 2>/dev/null || true

# ── Docker (legacy stack, kept for Linux servers until the bundle is built there) ──────────────

# Full Docker install: CLI + container stack + .mcp.json (./install.sh)
[group('docker')]
docker-install *args:
    ./install.sh {{args}}

# Start the container stack
[group('docker')]
docker-up:
    docker compose up -d

# Stop the container stack (keeps its volume)
[group('docker')]
docker-down:
    docker compose down

# Follow the server container's logs
[group('docker')]
docker-logs:
    docker compose logs -f server

# Show the container stack
[group('docker')]
docker-ps:
    docker compose ps

# Build the container images
[group('docker')]
docker-build:
    docker compose build
