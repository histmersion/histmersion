# Histmersion — task runner (https://just.systems). `just` lists everything.
# Nothing here hardcodes a port: values come from .env or the environment.

set dotenv-load := true
set shell := ["bash", "-euo", "pipefail", "-c"]

port        := env("HISTMERSION_PORT", "8000")
public_url  := env("HISTMERSION_PUBLIC_URL", "http://localhost:" + port)
bundle_dir  := env("BUNDLE_DIR", "dist/histmersion")
corpus_dir  := env("HISTMERSION_CORPUS_DIR", justfile_directory() / "corpus")
data_dir    := env("HISTMERSION_DATA_DIR", if os() == "macos" { home_directory() / "Library/Application Support/Histmersion" } else { env("XDG_DATA_HOME", home_directory() / ".local/share") / "histmersion" })
platform    := if os() == "macos" { "macos" } else { "linux" }
label       := "com.histmersion.server"
unit        := "histmersion.service"

# sbt 2's thin client shares one server and drops the caller's environment; `--server -batch`
# forces a dedicated, env-carrying, non-interactive one per invocation. The disk cache is this
# checkout's own (build.sbt sets it for the build and project/plugins.sbt for the meta-build; the flag
# also covers sbt's own empty global-plugins project, loaded before either).
sbt_cache := justfile_directory() / "target/sbt-cache"
sbt := "sbt --server -batch -Dsbt.global.localcache=" + sbt_cache

# The portable PostgreSQL (runtime/postgres, the one the bundle ships) — also the test database.
pg           := justfile_directory() / "runtime/postgres/.pixi/envs/default/bin"
test_db_dir  := justfile_directory() / "target/test-pg"
test_db_port := "55432"
test_db_url  := "jdbc:postgresql://127.0.0.1:" + test_db_port + "/histmersion_test"

[private]
default:
    @just --list --unsorted

# ── the native app ───────────────────────────────────────────────────────────────────────────

# Build the native bundle: server + CLI, PostgreSQL 18 + PostGIS, OCR converter
[group('app')]
bundle dir=bundle_dir:
    scripts/bundle/postgres.sh {{dir}}
    scripts/bundle/converter.sh {{dir}}
    scripts/bundle/server.sh {{dir}}

# Build the bundle and install it (macOS: LaunchAgent; Linux: systemd user service); the server is linked on this machine unless the bundle's binary already was (`--prebuilt` takes it as it is, `--jvm` runs it on a JDK)
[group('app')]
install *args: bundle
    HISTMERSION_CORPUS_DIR="{{corpus_dir}}" scripts/bundle/install-{{platform}}.sh {{bundle_dir}} --move {{args}}
    just mcp-json

# Write .mcp.json and plugin/.mcp.json (the MCP server entry) from HISTMERSION_PUBLIC_URL
[group('app')]
mcp-json:
    #!/usr/bin/env bash
    set -euo pipefail
    for f in .mcp.json plugin/.mcp.json; do
      cat > "$f" <<EOF
    {
      "mcpServers": {
        "histmersion": {
          "type": "http",
          "url": "{{trim_end_match(public_url, "/")}}/mcp"
        }
      }
    }
    EOF
      echo "wrote $f"
    done

# Remove the app; the data stays unless you pass --purge
[group('app')]
uninstall *args:
    scripts/bundle/uninstall-{{platform}}.sh {{args}}

# Start the installed app
[group('app')]
[macos]
start:
    launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/{{label}}.plist

# Start the installed app
[group('app')]
[linux]
start:
    systemctl --user start {{unit}}

# Stop the installed app (until the next login or `just start`)
[group('app')]
[macos]
stop:
    launchctl bootout gui/$(id -u)/{{label}}

# Stop the installed app (until the next login or `just start`)
[group('app')]
[linux]
stop:
    systemctl --user stop {{unit}}

# Restart the installed app
[group('app')]
[macos]
restart:
    launchctl kickstart -k gui/$(id -u)/{{label}}

# Restart the installed app
[group('app')]
[linux]
restart:
    systemctl --user restart {{unit}}

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

# The scripts' own tests (the LaunchAgent environment merge, claude-overhead's helper; no launchctl, no model, temp files only)
[group('dev')]
test-scripts:
    python3 -m unittest scripts/agents/tests/test_claude_overhead.py
    python3 -m unittest scripts/perf/tests/test_bench.py
    python3 -m unittest scripts/dev/tests/test_test_weights.py
    scripts/bundle/tests/launchagent-env-test.sh
    scripts/bundle/tests/user-config-test.sh
    scripts/bundle/tests/relink-test.sh
    scripts/bundle/tests/kit-scripts-test.sh
    scripts/bundle/tests/provenance-test.sh
    python3 -m unittest db/gazetteer/test_build.py
    python3 -m unittest db/places/test_build.py

# Every suite, DB-backed ones on a throwaway Postgres that is removed afterwards
[group('dev')]
test-it: test-db
    #!/usr/bin/env bash
    set -uo pipefail
    HISTMERSION_TEST_DB_URL={{test_db_url}} HISTMERSION_PG_BIN_DIR={{pg}} {{sbt}} 'testOnly *'; rc=$?
    just test-db-down
    exit $rc

# Prove that two checkouts never replay each other's sbt cache entries (two temp worktrees, ~5 min)
[group('dev')]
cache-check *args:
    scripts/dev/cache-isolation-check.sh {{args}}

# Start a throwaway Postgres (histmersion_test on :55432; the forked test groups add histmersion_<group>_test beside it) from the bundled runtime — never the live corpus
[group('dev')]
test-db:
    (cd runtime/postgres && pixi install >/dev/null)
    -"{{pg}}/pg_ctl" -D "{{test_db_dir}}" -m immediate stop >/dev/null 2>&1
    rm -rf "{{test_db_dir}}" && mkdir -p "$(dirname "{{test_db_dir}}")"
    "{{pg}}/initdb" -D "{{test_db_dir}}" -U histmersion --auth=trust -E UTF8 --locale=C >/dev/null
    "{{pg}}/pg_ctl" -D "{{test_db_dir}}" -l "{{test_db_dir}}.log" -w \
      -o "-p {{test_db_port}} -c listen_addresses=127.0.0.1 -c unix_socket_directories='' -c max_connections=300" start >/dev/null
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

# Regenerate the checked-in Claude Code plugin (plugin/, .claude-plugin/marketplace.json) from agents/ and skills/
[group('dev')]
plugin:
    {{sbt}} "server/Test/runMain com.histmersion.server.agents.GeneratePlugin {{justfile_directory()}}"

# Regenerate the CLI reference table (docs/user-guide/reference/cli.md) from `histmersion --help`
[group('dev')]
cli-docs:
    ./tools/gen-cli-docs.sh

# Regenerate the index of every REST endpoint (docs/user-guide/reference/rest-api.md) from Api.all
[group('dev')]
api-docs:
    {{sbt}} "coreJVM/runMain com.histmersion.core.generateApiIndex {{justfile_directory()}}"

# ── documentation ────────────────────────────────────────────────────────────────────────────

# Create docs/.venv with the pinned Zensical (docs/requirements.txt)
[group('docs')]
docs-venv:
    just -f docs/Justfile venv

# Build the documentation site into docs/site; any broken link fails the build
[group('docs')]
docs-build:
    just -f docs/Justfile build

# Serve the documentation with live reload (http://localhost:$HISTMERSION_DOCS_PORT, default 8001)
[group('docs')]
docs-serve:
    just -f docs/Justfile serve

# Regenerate the documentation's screenshots from the demo collection (docs/demo) on a throwaway server and database
[group('docs')]
docs-screenshots *args:
    scripts/docs/screenshots.sh {{args}}

# Remove build output (sbt targets, native images, dist/)
[group('dev')]
clean:
    {{sbt}} clean
    rm -rf dist target/native
    find target modules -type f \( -name histmersion-cli -o -name histmersion-server \) -delete 2>/dev/null || true
