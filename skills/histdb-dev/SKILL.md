---
name: histdb-dev
description: Build, test, run and package the hist-db Scala codebase — sbt 2 gotchas, the GraalVM native-image workflow and its tracing-agent metadata, Flyway migrations, and the Docker stack. Use when changing hist-db's Scala sources, adding a dependency, or when a native image or container build fails.
---

# Developing hist-db

Repo conventions and code rules: **[`AGENTS.md`](../../AGENTS.md)**. This skill is the mechanical
build/test/run workflow.

## Toolchain

Pinned in `.sdkmanrc`: **GraalVM CE 22.0.2**, sbt 2.0.7, Scala 3.8.

```bash
sdk env install   # once
sdk env           # per shell (automatic with sdkman_auto_env=true)
```

**Do not "upgrade" to JDK 25.** ox 0.5.x (pulled in by `tapir-netty-server-sync`) is compiled against
JDK 22's preview `StructuredTaskScope`; on JDK 25 the server builds and starts, logs "listening", and
then dies at `NettySyncServer.startAndWait` with `NoClassDefFoundError: ox.DoNothingScope`. Moving the
JDK means bumping tapir/ox first, then re-running the tracing agent and re-pinning `.sdkmanrc`, CI and
the `Dockerfile` **together** (`docs/plan.md`, wave 3).

## Everyday commands

```bash
make test          # sbt test — pure suites
make up            # docker compose up -d (the whole stack)
make logs          # follow the server
make native        # both native images, on the host
make install-dry   # what ./install.sh would do
make help          # everything
```

Running the server outside Docker needs Postgres published on the host:

```bash
make dev-up        # postgres + converter, with 5432 published
sbt server/run
```

## Tests

- **Pure tests** run with no setup: `sbt test`.
- **DB and HTTP suites are gated** on `HISTDB_TEST_DB_URL`; unset, they skip with a visible reason.

```bash
make dev-up
HISTDB_TEST_DB_URL=jdbc:postgresql://localhost:5432/histdb sbt test
```

Every pure function needs a test. `Filter → SQL`, markdown rendering, the frontmatter parser, the
time parser, bbox splitting — all pure, all testable without a database.

## sbt 2 gotchas (they will bite)

| gotcha | consequence | do this |
|---|---|---|
| The thin client shares one server across invocations and **drops the caller's environment** | `HISTDB_TEST_DB_URL` silently missing; stale settings | `sbt --server -batch <task>` |
| Test results are **cached** | "tests pass" when they were never run | `sbt "testOnly *"` to force |
| Settings are keyed by **source position** | `key := value` inside a shared `def` makes the last caller win **for every project** | set per-project inline (see `Compile / mainClass` in `build.sbt`) |
| Classpath entries are **virtual file refs** | `.data` is not a `File` | convert via `fileConverter.value.toPath(...)` |
| Module output moved | it is **not** `modules/<m>/target/` | `target/out/jvm/<scalaVersion>/<name>/` — find the binary, don't hardcode the path |

## Native images

```bash
sbt --server -batch cli/nativeImage      # → target/out/jvm/*/histdb-cli/histdb-cli
sbt --server -batch server/nativeImage   # → target/out/jvm/*/histdb-server/histdb-server
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

- Regenerate metadata **only with the pinned JDK (22)**. The committed JSON was produced by the JDK-22
  agent; mixing agents across JDKs corrupts it.
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
add the next number. `histdb health` reports the current schema version.

## Docker

```bash
docker compose build server      # multi-stage: GraalVM builder → debian-slim runtime
docker compose up -d
docker compose logs -f server
```

The builder downloads GraalVM CE (the `.sdkmanrc` version) and sbt as tarballs and picks the tarball
by `TARGETARCH`, so the same Dockerfile builds on arm64 and amd64. `native-image` peaks around 5 GB
RSS; if the build is killed with exit 137, raise Docker's memory or lower `NATIVE_IMAGE_OPTIONS`
(`-J-Xmx…`) in the Dockerfile.

## Before you commit

```bash
sbt "testOnly *"                 # bypass the result cache
sbt cli/nativeImage server/nativeImage
git status                       # no agent-pid*/ dirs, metadata JSON changes intentional
```

Code rules, module boundaries and commit conventions: **`AGENTS.md`**.
