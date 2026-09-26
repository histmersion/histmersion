---
name: histmersion-dev
description: Build, test, run and package the Histmersion Scala codebase — sbt 2 gotchas, the GraalVM native-image workflow and its tracing-agent metadata, Flyway migrations, and the native bundle. Use when changing Histmersion's Scala sources, adding a dependency, or when a native image or bundle build fails.
---

# Developing Histmersion

Repo conventions and code rules: **[`AGENTS.md`](../../AGENTS.md)**. This skill is the mechanical
build/test/run workflow.

## Toolchain

Pinned in `.sdkmanrc`: **GraalVM CE 25.0.1**, sbt 2.0.7, Scala 3.8.

```bash
sdk env install   # once
sdk env           # per shell (automatic with sdkman_auto_env=true)
```

Moving the JDK means re-running the tracing agent and re-pinning `.sdkmanrc` and CI **together**, in
one commit (`scripts/bundle/server.sh` refuses any other major). It sat at 22.0.2 through waves 1–2 because `tapir-netty-server-sync`
1.11.x pulled ox 0.5.x, whose `DoNothingScope extends StructuredTaskScope` — an interface since JEP
505, so the server logged "listening" and then died in `NettySyncServer.startAndWait`. tapir 1.13.31
pulls ox 1.0.2, which uses its own `ox.internal.ThreadHerd` over virtual threads and touches no
preview API.

## Everyday commands

```bash
just                 # list every recipe
just test            # sbt test — pure suites
just test-it         # every suite, DB-backed ones on a throwaway Postgres (removed afterwards)
just native          # both native images, built with the pinned JDK (scripts/bundle/server.sh)
just install         # build the native bundle and install it (LaunchAgent, ~/Applications/Histmersion)
just restart · just logs [server|postgres|converter] · just health
```

The server supervises the bundled PostgreSQL 18 + PostGIS and the OCR converter as child processes. To run a development server against the installed bundle's database and converter:

```bash
just stop                                                   # free :8000 and the cluster
HISTMERSION_RUNTIME=true HISTMERSION_HOME=~/Applications/Histmersion sbt server/run
```

## Tests

- **Pure tests** run with no setup: `sbt test`.
- **DB and HTTP suites are gated** on `HISTMERSION_TEST_DB_URL`; unset, they skip with a visible reason.
  They WRITE fixtures, so `TestDb` refuses any database not named `*_test` — never point them at the
  live corpus.

```bash
just test-db                                                 # throwaway histmersion_test on :55432
HISTMERSION_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/histmersion_test sbt --server -batch 'db/testOnly *'
just test-db-down
```

Every pure function needs a test. `Filter → SQL`, markdown rendering, the frontmatter parser, the
time parser, bbox splitting — all pure, all testable without a database.

## sbt 2 gotchas (they will bite)

| gotcha | consequence | do this |
|---|---|---|
| The thin client shares one server across invocations and **drops the caller's environment** | `HISTMERSION_TEST_DB_URL` silently missing; stale settings | `sbt --server -batch <task>` |
| Test results are **cached** | "tests pass" when they were never run | `sbt "testOnly *"` to force |
| Settings are keyed by **source position** | `key := value` inside a shared `def` makes the last caller win **for every project** | set per-project inline (see `Compile / mainClass` in `build.sbt`) |
| Classpath entries are **virtual file refs** | `.data` is not a `File` | convert via `fileConverter.value.toPath(...)` |
| Module output moved | it is **not** `modules/<m>/target/` | `target/out/jvm/<scalaVersion>/<name>/` — find the binary, don't hardcode the path |

## Native images

```bash
sbt --server -batch cli/nativeImage      # → target/out/jvm/*/histmersion-cli/histmersion-cli
sbt --server -batch server/nativeImage   # → target/out/jvm/*/histmersion-server/histmersion-server
```

~35–60 s each. The custom `nativeImage` task shells out to GraalVM's `native-image` over the module's
full classpath (no sbt-native-image plugin exists for sbt 2 yet).

### The tracing-agent rule

Reachability metadata lives in `modules/<m>/src/main/resources/META-INF/native-image/*.json`. It is
collected automatically: `sbt server/run` and `sbt cli/run` fork under
`-agentlib:native-image-agent=config-merge-dir=…`, so **just exercising the app keeps the metadata
current**.

> **After adding or upgrading a dependency, run the app once (`sbt server/run`, hit the endpoints you
> care about, `sbt "cli/run search x"`), then commit the changed JSON.** Skipping this produces a
> binary that builds cleanly and fails at runtime with `ClassNotFoundException` or a missing resource.

- The committed file is **one `reachability-metadata.json` per module** (the JDK-25 agent format). The
  legacy split files (`reflect-config.json`, `resource-config.json`, …) were deleted with the JDK bump
  and must not come back beside it: the JDK-25 agent EMPTIES a legacy `resource-config.json` it finds
  in the merge dir, which silently drops every resource.
- Regenerate metadata **only with the pinned JDK (25)**. Agents disagree across JDKs — the JDK-24 one
  rejects `jniAccessible`, the JDK-22 one writes the legacy format back.
- `resources` are recorded file by file, as requested. Exercising one page is not enough: hit every
  asset under `web/`, and boot once against an EMPTY database so Flyway reads every migration.
- A killed agent run leaves `agent-pid*/` scratch dirs; they are gitignored and the `nativeImage` task
  deletes them, but check `git status` after a hard interrupt.

### Native-image constraints in the code

- **Load config eagerly on the main thread.** `AppConfig.load()` uses GraalVM `StackValue` to read the
  environment and must not run on an Ox virtual thread.
- **Config never parses system properties.** `ConfigFactory.load()` folds every system property into
  HOCON; dotted keys like `io.netty.allocator.type` then collide and blow up sconfig under native
  image. `AppConfig` parses `reference.conf` and resolves `${?ENV}` against the environment only.
- **netty:** unpooled allocator (`io.netty.allocator.type=unpooled` — the pooled one mishandles
  `ByteBuf` refCnt natively) and NIO transport (the kqueue/epoll JNI transports are excluded in
  `build.sbt` and initialised at run time).
- **Logging is scribe** (`scribe-slf4j` bridges Flyway). Do not add a reflective logging backend.
- **Flyway** loads migrations through a `ResourceProvider` fed by a generated
  `db/migration/index.txt`, because classpath scanning does not work in a native image. Adding a
  migration file is enough — the index is regenerated by a `resourceGenerator`.

## Migrations

Plain SQL at `modules/db/src/main/resources/db/migration/V###__name.sql`, applied by the server at
startup **before** it binds the port; a failed migration exits 3. Never edit an applied migration —
add the next number. `histmersion health` reports the current schema version.

## The bundle

```bash
just bundle                                   # dist/histmersion: postgres.sh → converter.sh → server.sh
scripts/bundle/smoke.sh dist/histmersion      # isolated end-to-end run on offset ports (what CI runs)
just install                                  # replace the installed app with it — the live corpus's server
```

`scripts/bundle/postgres.sh` copies PostgreSQL 18 + PostGIS from the pixi environment in
`runtime/postgres`; `converter.sh` builds a python-build-standalone Python with the locked `ocr`
extra and downloads docling's models; `server.sh` builds both native images with the pinned JDK.
`.github/workflows/bundle.yml` builds and smoke-tests the bundle per platform.

## Docs and skills that are generated or checked

Three artefacts must not drift from the code. None of them is edited by hand alone.

| artefact | kept honest by |
|---|---|
| the **CLI reference table** in `README.md` | `./tools/gen-cli-docs.sh` (`--check` in CI) — regenerates it from the CLI's own `--help` |
| the `histmersion …` commands and `mcp__histmersion__*` names in `skills/*/SKILL.md` | `./tools/check_skills.sh` — every command must `--help` with exit 0, every tool name must appear in the live `tools/list` |
| the **MCP tool table** in `README.md` and `plugin/README.md` | generated from `tools/list` against a running server; regenerate both after adding, renaming or removing a tool |

```bash
./tools/check_skills.sh                       # against $HISTMERSION_SERVER_URL or http://localhost:8000
HISTMERSION_CLI=/path/to/histmersion-cli ./tools/check_skills.sh
```

**Adding or renaming an MCP tool is a docs change too.** The tool's own `description` is what an
agent reads first, so it belongs in the same commit as the handler; then run `check_skills.sh` and
refresh both README tables.

### Adding a lint rule

Rules are a catalog in `modules/core/src/main/scala/com/histmersion/core/Lint.scala` (`Rule(name,
description)`, wire spelling `--rule dangling-edges`) with the SQL in
`modules/db/src/main/scala/com/histmersion/db/LintRepo.scala`. Adding one means: the catalog entry, the
query, a `LintRepoSuite` case, and the count in every description that says "13 rules" — the CLI
help string, the MCP tool description and `README.md`. `docs/plan.md` §6 lists two prose rules
(`article-filler`, `article-uncited-paragraph`) that the writing guide already names.

## The writing rules apply to the docs too

`docs/writing-guide.md` governs compiled prose, but its plain-language half — numbers over
adjectives, no banned filler, no sentence that survives a topic swap, define a term on first use —
is the house style for `README.md`, `AGENTS.md`, the skills and every tool description.

## Before you commit

```bash
sbt "testOnly *"                 # bypass the result cache
sbt cli/nativeImage server/nativeImage
./tools/gen-cli-docs.sh --check  # README CLI table still matches the binary
./tools/check_skills.sh          # skills name only commands and tools that exist
git status                       # no agent-pid*/ dirs, metadata JSON changes intentional
```

Code rules, module boundaries and commit conventions: **`AGENTS.md`**.
