# Contributing

This section is for people who change Histmersion itself. The rules every change follows — module
boundaries, code style, the configuration and native-image rules, commit conventions — are in
{{ repo_file("AGENTS.md") }}; the mechanical build, test and native workflow is the
`histmersion-dev` skill ({{ repo_file("skills/histmersion-dev/SKILL.md") }}).

## Toolchain

| tool | for |
|---|---|
| [sdkman](https://sdkman.io) | the pinned JDK, **GraalVM CE 25.0.1**, and sbt 2.0.7 (`.sdkmanrc`; run `sdk env install`) |
| [just](https://just.systems) | the recipes below (`just` lists them) |
| [uv](https://docs.astral.sh/uv/) | the OCR converter's Python environment |
| [pixi](https://pixi.sh) | the bundled PostgreSQL 18 + PostGIS (`runtime/postgres`), also for test databases |
| Node.js | only for the web page's browser tests |

The code is Scala 3.8 in direct style: Tapir on netty for HTTP, Chimp for MCP, Magnum for SQL,
Flyway for the schema, Laminar (Scala.js) for the web page, GraalVM native-image for both binaries.

## Build and run

```bash
just bundle                     # the native bundle in dist/histmersion: server + CLI, PostgreSQL + PostGIS, OCR converter
just install                    # bundle + install + start (see Installing)
just native                     # only the two native images, into target/native
sbt server/run                  # the server on the JVM (forked under the native-image tracing agent)
sbt "cli/run search collapse"   # the CLI on the JVM
```

`sbt server/run` with `HISTMERSION_RUNTIME=true HISTMERSION_HOME=<installed bundle>` uses the installed
bundle's Postgres and converter. **Never point a development server or a test at the live database.**

## Tests

| recipe | runs | needs |
|---|---|---|
| `just test` | the pure suites (`sbt test`) | nothing |
| `just test-it` | **every** suite; the database-backed ones on a throwaway Postgres (`just test-db`) that is removed afterwards | the pixi Postgres |
| `just test-scripts` | the scripts' own tests: the LaunchAgent environment merge, the user config, the relink and kit scripts, provenance, the gazetteer and places builders | Python 3, bash |
| `scripts/bundle/smoke.sh dist/histmersion` | a built bundle end to end, in isolation: first start, health, the CLI, a real offline OCR, a clean stop, and a real relink with the test plugin | a built bundle; ~16 GB of memory for the relink |
| `npm --prefix modules/web run test:browser` | the web page in Chromium: interactions, speed and memory budgets, a soak test | Node, Playwright, a test server — see `modules/web/test/README.md` |

Rules:

- **The database suites run only against a database named `*_test`.** They write fixtures; the
  helper refuses any other name. `just test-db` starts one on port 55432; pick another with
  `just test_db_port=55561 test-it` when that port is taken.
- **sbt 2 caches test results.** Use `sbt "testOnly *"` (what `test-it` runs) to be sure tests ran;
  for one module, `sbt -batch "set Global / cacheStores := Seq.empty; cli/clean; cli/testOnly *"`.
- **The sbt thin client drops your environment.** Use `sbt --server -batch …` (the recipes do).
- Every pure function gets a unit test; DB and HTTP suites skip with a visible reason when
  `HISTMERSION_TEST_DB_URL` is unset.

## Generated files

| file | regenerate with | checked by |
|---|---|---|
| the CLI table in [Command line](../reference/cli.md) | `just cli-docs` (`tools/gen-cli-docs.sh`) | `tools/gen-cli-docs.sh --check` |
| the endpoint index in [REST API](../reference/rest-api.md) | `just api-docs` | `ApiSurfaceSuite` |
| the tool list in [MCP tools](../reference/mcp-tools.md) and `plugin/README.md` | by hand, from the server's `tools/list` | `OpsHttpSuite` (every tool documented, the count right) |
| `plugin/` (the Claude Code rendering of `agents/` and `skills/`) | `just plugin` | `PluginTreeSuite` |
| `.mcp.json` | `just mcp-json` | — (not committed) |
| native-image metadata | run the app once under the tracing agent | `NativeMetadataSuite` |

`tools/check_skills.sh` checks that every `histmersion …` command and every MCP tool named in the
skills exists.

## Before you call it done

```bash
sbt "testOnly *"                                   # not the cached result
sbt cli/nativeImage server/nativeImage             # both binaries still build
just bundle && scripts/bundle/smoke.sh dist/histmersion
just docs-build                                    # the documentation still builds, links checked
git status                                         # no agent-pid*/ folders, no stray files
```

When you change a CLI flag, an MCP tool, an endpoint or a configuration key, update the matching
reference page in the same commit.

More: [Architecture](architecture.md) · [Native image](native-image.md) ·
[Working on these docs](docs.md).
