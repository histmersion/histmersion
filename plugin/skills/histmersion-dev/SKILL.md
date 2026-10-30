---
name: histmersion-dev
description: Build, test, run and package the Histmersion Scala codebase — sbt 2 gotchas, the GraalVM native-image workflow and its tracing-agent metadata, Flyway migrations, the native bundle, and plugins (the SPI, writing one, plugin add/rebuild, relink, JVM mode, signing, config reload, secret references). Use when changing Histmersion's Scala sources, adding a dependency, writing or installing a plugin, or when a native image, relink or bundle build fails.
---

# Developing Histmersion

Repo conventions and code rules: **[`AGENTS.md`](../../AGENTS.md)**. This skill is the mechanical
build/test/run workflow.

## Toolchain

Pinned: **GraalVM CE 25.4** (JDK 25.0.4.1.1) in `scripts/bundle/graalvm-pins.txt` (URL + SHA-256 per
platform); `.sdkmanrc` names the same build (`java=25.4.4.1+1-graalce`), sbt 2.0.11 and Scala 3.10.

```bash
sdk env install && sdk env   # once per checkout, then `sdk env` per shell (or sdkman_auto_env=true)
```

Everything uses the active JDK and refuses any other version, naming `sdk env install`: `nativeImage`
and the tracing-agent run (build.sbt), the justfile's sbt recipes (`scripts/dev/sbt.sh`) and
`scripts/bundle/server.sh`. `histmersion-toolchain` (`scripts/bundle/toolchain.sh`) is for the relink kit
and CI only, on machines without sdkman. 25.4 is an innovation release — no fixes after the next one — so the pin moves
with each new one: the pins file (version, URLs, SHA-256 against the release's `.sha256` and GitHub's
digest), the `java=` line of `.sdkmanrc`, the docs naming it, and the tracing-agent metadata re-checked, in one commit
(`scripts/bundle/server.sh` refuses any other version). Its builder refuses `java.*` classes on the class
path: build.sbt excludes `scala-java-time-tzdb_3` (scribe → perfolation); never pass
`-H:+AllowDeprecatedBuilderClassesOnImageClasspath`. It sat at 22.0.2 through waves 1–2 because `tapir-netty-server-sync`
1.11.x pulled ox 0.5.x, whose `DoNothingScope extends StructuredTaskScope` — an interface since JEP
505, so the server logged "listening" and then died in `NettySyncServer.startAndWait`. tapir 1.13.31
pulls ox 1.0.2, which uses its own `ox.internal.ThreadHerd` over virtual threads and touches no
preview API.

## Everyday commands

```bash
just                 # list every recipe
just test            # sbt test — pure suites
just test-it         # every suite, DB-backed ones on a throwaway Postgres (removed afterwards) — the gate
just test-quick      # while iterating: modules changed since master + their dependents (or: just test-quick db cli)
just fmt             # scalafmt over every Scala source and build file (compiling formats a module too)
just fmt-check       # fails on an unformatted file — the first step of test-quick and of CI
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

**Iterate with `just test-quick`, gate with `just test-it`.** `test-quick` asks
`scripts/dev/affected.py` which modules the branch touched (committed, staged, unstaged, untracked)
and tests them and their dependents (files → core, cli; core → db, plugin-host, server; db → server; plugin-host → testkit, server; plugin-api → vaults,
server); a change to build.sbt or project/ tests everything. `--explain` shows why:
`python3 scripts/dev/affected.py --explain`. Pass a test DB port when :55432 is taken:
`just test_db_port=55561 test-quick`.

**Parallel DB suites.** db and server fork their suites into groups (2 and 5; longest first by project/test-weights.tsv — refresh it with `scripts/dev/test-weights.py <test-it log>`), each group a JVM on a
database of its own (`histmersion_<module>_<n>_test`, created by `TestDb` on first use beside the
`HISTMERSION_TEST_DB_URL` one); in a group the suites run one at a time. `HISTMERSION_TEST_FORKS`
(default 6) caps the JVMs at once — lower it when memory is short. A new suite that creates a scratch
database must give it a name of its own (as `BundleSuite`, `MergeSuite` do): groups share the cluster.

**The cache is per checkout.** sbt 2's disk cache is `target/sbt-cache` in each checkout (build.sbt
and project/plugins.sbt), not the machine-wide `~/Library/Caches/sbt/v2` — never set
`Global / cacheStores := Seq.empty` (it only forces full recompiles, Scala.js linking included).
`just cache-check` proves two worktrees stay apart. A NEW worktree can start from a copy of another
checkout's cache: `just cache-seed <worktree>` (from the main worktree; never shares a store, never copies
`target/out`), then `just cache-seed --verify <worktree>` after its first build.

```bash
just test-quick                     # what the branch changed, and what depends on it
just test-quick server              # one module (and nothing else)
```

Every pure function needs a test. `Filter → SQL`, markdown rendering, the frontmatter parser, the
time parser, bbox splitting — all pure, all testable without a database.

## sbt 2 gotchas (they will bite)

| gotcha | consequence | do this |
|---|---|---|
| The thin client shares one server across invocations and **drops the caller's environment** | `HISTMERSION_TEST_DB_URL` silently missing; stale settings | `sbt --server -batch <task>` |
| Test results are **cached** | "tests pass" when they were never run | `sbt "testOnly *"` to force (`test-it`/`test-quick` do) |
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
- Regenerate metadata **only with the pinned JDK** (GraalVM CE 25.4, active after `sdk env`). Agents disagree across JDKs — the JDK-24 one
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
  The server and CLI also read the user's `histmersion.conf` (`AppConfig.loadWithUserFile`, plain
  `ConfigFactory.parseFile`): environment > that file > `reference.conf`; `AppConfig.load()` (tests) skips it.
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
`.github/workflows/bundle.yml` builds and smoke-tests the bundle per platform, and publishes each platform's
relink kit with a release.

## Plugins

Design and as-built notes: `docs/research-plugins.md` (§2 the SPI, §3 config, §7–9 user plugins, the
relink and signing, §15–21 as built).

**The SPI** (`modules/plugin-api`, `com.histmersion.plugin`; a plugin sees nothing else — no core, db or
server). ONE ServiceLoader service, `Plugin` (listed in `META-INF/services/com.histmersion.plugin.Plugin`,
a public class with a no-argument constructor):

- `info = PluginInfo(id, version, Spi.Version)` — `id` is kebab-case and the config key
  `histmersion.plugins.<id>`; another SPI version is refused (SPI 2: `Spi.Version` is an `inline val`, so it
  is compiled into the plugin; an SPI-1 jar fails to link on it and is told to rebuild).
- The values the SPI exchanges are plugin-api's own Iron newtypes — `Year`, `Period`, `Latitude`,
  `Longitude`, `Doi`, `WikidataId`, `Licence` (`Doi("10.1/x")` checked at compile time, `Doi.option(raw)`
  at run time, `.value` out). The host converts them to `values`' in `pluginhost/SpiValues`, checking
  each one a plugin hands over again (a newtype is erased).
- `type Config` with `given configReader` — path-dependent: derive it (`final case class XConfig(…) derives
  ConfigReader`, with `import com.histmersion.plugin.PluginConfig.given` for env-friendly numbers and
  booleans); the host decodes the block with THIS plugin's reader and hands it to THIS plugin only.
- Defaults in `META-INF/histmersion/plugins/<id>.conf` — also the key schema (every key has a default, `null`
  for optional ones; an unknown key fails that plugin alone). `enabled` is the host's key: absent = disabled.
  Env overrides go in the defaults: `key = ${?HISTMERSION_PLUGIN_<ID>_<KEY>}`.
- `register(config, ctx): Either[String, Unit]` — called once per config generation, only when enabled,
  after every `Secret` in the config was resolved. Add contributions to `ctx`'s registries:
  `secretVaults`, `sourceFetchers`, `mediaCollections`, `eventCatalogues` (`Registry.add(id, impl)`; staged
  — a `Left` discards all this plugin added). Outbound HTTP only through `ctx.http`; logs through `ctx.log`.

**Writing one outside this repo: `histmersion plugin new <id> [--kind source-fetcher|media-collection|
event-catalogue|ai-provider|secret-vault] [--with-agent-notes]`** (no checkout needed; the plugin API is not
on Maven Central — Histmersion is an app — but every install carries a Maven repository of its own, `maven/`:
`histmersion plugin repo [--url]`; `plugin repo --m2` copies it into `~/.m2/repository`, which the installers
and `plugin new` do too, so a project names `m2Local`, never a path of this machine). It holds
`com.histmersion::histmersion-plugin-api` (jar, sources, POM), the testkit and
`com.histmersion::histmersion-plugin-platform`, a POM-only artifact whose dependencies are the libraries the
server provides to plugins at the versions it ships (sconfig, kindlings, circe, cats, sttp client, tapir,
ox, iron; the contract is `modules/plugin-api/plugin-platform.txt`, checked by `PluginPlatformSuite`). `new`
writes a scala-cli project: `project.scala`, the build with EVERY directive (`//> using repository m2Local`,
the API and the platform as `compileOnly.dep`, `resourceDir`, `publish.organization/name/version`,
`publish.doc false` — scaladoc warns on links into the compile-only API — then munit, the platform and the
**testkit** as `test.dep` (the testkit brings the API; compile-only deps are not on the test class path, so
the Histmersion version is on four lines: scala-cli has no variables), `test.resourceDir test-resources`
and `test.javaProp histmersion.plugin.project=${.}`, against which `HttpMode.fromEnv` resolves a relative
recordings dir); `<Name>.scala` (a working plugin of that kind, no directives); `<Name>.test.scala` (unit
tests through `PluginHarness` on `HttpMode.Stub`, a gated `<Name>LiveSuite` for fetchers/collections/
catalogues that prints `SKIPPED <suite>: <why>` before its munit `assume`, plus packaging checks — the
services file names the class, the defaults decode, every `$lzy<n>` field is in the native metadata);
`README.md` (build, install from source step by step, record → replay) and `resources/` (services file,
defaults, `META-INF/native-image/<publish.organization>/<publish.name>/reachability-metadata.json`). More
files keep their directives in `project.scala`. A library of its own is a plain
`//> using dep`: never shade. Loop: `scala-cli test .` → `scala-cli --power publish . --publish-repository
./repo` (the jar and a POM listing its own libraries, not the compile-only ones) → `histmersion plugin add
<org>::<id>:0.1.0 --repo ./repo` → `plugin rebuild`. `--repo <dir|url>` on `new` for a repository unzipped
from a release (`histmersion-maven-<tag>.zip`); `histmersion plugin api-jar [--sources]` prints the API
jar's path in the repository. Editors: `scala-cli setup-ide .`, then Metals (sources come from the
repository). `just plugin-template-check [dir] [kind…]` (scripts/bundle/plugin-template-check.sh; smoke.sh
runs it when scala-cli is on PATH) builds, tests and publishes every kind against a bundle under a scratch
HOME (scala-cli gets `-Duser.home` too: coursier's m2Local follows user.home) and fails on a warning, a jar
holding more than the plugin's classes/services/defaults/metadata, or a POM listing the API, platform or
testkit. sbt alternative: `resolvers += Resolver.mavenLocal`, the API and platform
`% Provided`, own libraries as usual, `publishTo` a directory. Code and generator: `cli/PluginTemplate.scala`,
`cli/PluginTemplateKinds.scala`; the user docs page is `docs/user-guide/plugins/developing.md`.

**Testing a plugin without a server: `com.histmersion::histmersion-plugin-testkit`** (`modules/plugin-testkit`,
shipped in `maven/` with `histmersion-plugin-host` and `histmersion-core`). `PluginHarness.load(id, config)`
runs the server's own `PluginHost` (moved to `modules/plugin-host` for this; the server and the testkit
share it, and the acquisition client, `FetchChain`, `WorkLookup`, `PluginMedia`/`PluginEvents`/`PluginAi`)
→ `fetch`/`searchMedia`/`readCatalogue`/`aiTurn`/`resolveSecret`, `WorkKey.fromDoi`. HTTP is the server's
client over `HttpMode.Live|Record(dir)|Replay(dir)|Stub`; `HttpMode.fromEnv(dir)` reads
`HISTMERSION_PLUGIN_HTTP` (live/record/replay), else replays recordings, else live with
`HISTMERSION_PLUGIN_LIVE=1`, else skip. Recordings: `NNN-<host>.http` + body file; no secrets, cookies or
contact e-mail. Its public API is listed in `modules/plugin-testkit/plugin-testkit-api.txt`
(`TestkitApiSuite`; `HISTMERSION_UPDATE_TESTKIT_API=1` rewrites it). User docs: developing.md "Testing";
design: `docs/research-plugins.md` §25.

**Dependencies at install.** `plugin add` fetches the plugin's POM with its jar (beside a path or URL, or
from the Maven repository) and runs `histmersion-relink --resolve`, the relink kit's plugin resolver
(`modules/plugin-resolver`, coursier's Java API, in `kit/tools/resolver/` — never on the server's class
path). Each plugin is resolved alone against `kit/modules` (the server's resolved modules) and refused when
it needs a NEWER version of a shipped module (library and both versions named); then all plugins together
with the server's modules excluded; the rest goes to `<data>/plugins/lib/` (`classpath`, `inputs`,
`repositories`). The relink resolves again in its pre-flight, links `lib/` (Sloth-patched) beside the
plugins and checks duplicate classes across kit, plugins and `lib/`; JVM mode re-resolves when `lib/inputs`
no longer matches. A bare jar without a POM has no dependencies; a missing class then fails that plugin with
"class … is missing" (`PluginHost.failure`). Offline tests: the `hello-deps` fixture (POM lists `hello-lib`)
in the repository `sbt pluginHelloDeps/fixturesRepo` writes.

**Inside this repo: `modules/plugin-fixtures/hello`** (build.sbt `pluginHello`: `dependsOn(pluginApi)` only,
packaged as its own jar) is the test fixture. It shows the provider, a derived `Config`, the defaults resource, one
`SourceFetcher`, and — because its greeter is chosen by name — its OWN
`META-INF/native-image/<group>/<artifact>/reachability-metadata.json`. A plugin jar must carry that
metadata (Scala 3 lazy val fields included; record the rest with the tracing agent in the plugin's build)
and declare every dependency the server does not ship in its POM; a relink fails at the new binary's
self-check otherwise. Built-in plugins (`modules/plugin-vaults`, the `oa-*`/`media-*`/`events-*` ones) follow the same SPI.

**Config and secrets.** Plugin settings live in `histmersion.conf` under `histmersion.plugins.<id>`. A
settings change (`enabled` included) needs no restart: `histmersion config reload` (or `config.watch`)
rebuilds the changed plugins in place; a failed reload changes nothing. Credentials are `Secret` fields
holding a REFERENCE, never the value: `env:NAME`, `<vault>:<ref>` (resolved by a registered `SecretVault`,
e.g. `keepassxc:Group/entry#password`), or `value:<literal>` (allowed, warned about, masked everywhere).
The `keepassxc` vault also opens YubiKey-protected databases: `yubikey = "2:<serial>"` or a list tried in
order (`ykman list --serials`, when installed, limits it to the connected keys); test it with fake
`keepassxc-cli`/`ykman` scripts only, never a real database or key.

**Installing and linking** (local only — they act on this machine's install and data directory, never
the database):

```bash
histmersion plugin add <group::artifact:version | jar | url> [--repo <dir|url>] [--yes]   # describes it and its dependencies first; refuses a foreign SPI or a newer library than the server ships
histmersion plugin list ; histmersion plugin status          # REBUILD NEEDED / RESTART NEEDED / up to date
histmersion plugin rebuild [--jvm] [--check] [--no-restart]   # bin/histmersion-relink over the bundle's kit/
histmersion plugin rollback ; histmersion plugin rm <id>
```

- A native binary cannot load classes, so a changed plugin SET needs a relink: `native-image` over the
  bundle's `kit/` (the server's Sloth-patched jars, flags, `kit.json` + `SHA256SUMS`) plus the plugin jars —
  no sbt. JVM pre-flight first, then ~2 min and ~13.5 GB; the new binary must pass `--self-check` before it
  is swapped in (`.prev` kept; failure to come up rolls back). Log: `<data>/logs/relink.log`.
- JVM mode (`rebuild --jvm`): the same kit on a JDK, plugins found at each start; no native link.
- Installers link the server on the machine by default (D5); `--prebuilt` takes the bundle's binary,
  `--jvm` JVM mode; a machine that cannot link falls back to the prebuilt binary. Each platform's kit is
  also published by CI (`…-kit.tar.zst`).
- Signing is optional: `bin/histmersion-signing setup-macos [--trust]` (a stable code-signing identity, untrusted by default,
  so macOS permissions survive relinks), `use-gpg <key>` (GPG provenance: signed plugin manifest, signed
  binary checksums, and the service starts through `libexec/histmersion-server-verified`, which refuses an
  unsigned or changed binary), `off-gpg`, `status`.
- Testing the scripts: `just test-scripts` (fakes and temporary dirs only); the real relink is
  `scripts/bundle/smoke.sh`. Never run them against `~/Applications/Histmersion` or the real data dir.

## Docs and skills that are generated or checked

Three artefacts must not drift from the code. None of them is edited by hand alone.

| artefact | kept honest by |
|---|---|
| the **CLI reference table** in `docs/user-guide/reference/cli.md` | `./tools/gen-cli-docs.sh` (`just cli-docs`; `--check` in CI) — regenerates it from the CLI's own `--help` |
| the **REST endpoint index** in `docs/user-guide/reference/rest-api.md` | `just api-docs` — regenerates it from `Api.all`; `ApiSurfaceSuite` fails when stale |
| the `histmersion …` commands and `mcp__histmersion__*` names in `skills/*/SKILL.md` | `./tools/check_skills.sh` — every command must `--help` with exit 0, every tool name must appear in the live `tools/list` |
| the **MCP tool table** in `docs/user-guide/reference/mcp-tools.md` and `plugin/README.md` | generated from `tools/list` against a running server; regenerate both after adding, renaming or removing a tool (`OpsHttpSuite` checks every tool and the count) |

```bash
./tools/check_skills.sh                       # against $HISTMERSION_SERVER_URL or http://localhost:8000
HISTMERSION_CLI=/path/to/histmersion-cli ./tools/check_skills.sh
```

**Adding or renaming an MCP tool is a docs change too.** The tool's own `description` is what an
agent reads first, so it belongs in the same commit as the handler; then run `check_skills.sh` and
refresh both MCP tool tables.

### Adding a lint rule

Rules are a catalog in `modules/api/shared/src/main/scala/com/histmersion/api/Lint.scala` (`Rule(name,
description)`, wire spelling `--rule dangling-edges`) with the SQL in
`modules/db/src/main/scala/com/histmersion/db/LintRepo.scala`. Adding one means: the catalog entry, the
query, a `LintRepoSuite` case, and the count in every description that says "13 rules" — the CLI
help string, the MCP tool description and the docs' reference pages. `docs/plan.md` §6 lists two prose rules
(`article-filler`, `article-uncited-paragraph`) that the writing guide already names.

## The writing rules apply to the docs too

`docs/writing-guide.md` governs compiled prose, but its plain-language half — numbers over
adjectives, no banned filler, no sentence that survives a topic swap, define a term on first use —
is the house style for `README.md`, the documentation site (`docs/user-guide/`), `AGENTS.md`, the skills and every tool description.

## Before you commit

```bash
just fmt-check                   # formatted (just fmt fixes it)
sbt "testOnly *"                 # bypass the result cache
sbt cli/nativeImage server/nativeImage
./tools/gen-cli-docs.sh --check  # the docs' CLI table still matches the binary
just docs-build                  # the documentation site builds, every link checked
./tools/check_skills.sh          # skills name only commands and tools that exist
git status                       # no agent-pid*/ dirs, metadata JSON changes intentional
```

Code rules, module boundaries and commit conventions: **`AGENTS.md`**.
