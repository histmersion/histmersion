# AGENTS.md — working on Histmersion

Conventions for anyone (human or agent) changing this repo. `CLAUDE.md` is a symlink to this file.

Histmersion is a **Scala 3 / direct-style** service: Tapir on netty-sync, Chimp for MCP, Magnum for JDBC,
Flyway for schema, GraalVM native-image for both binaries. There is no effect monad anywhere, and
there is not going to be one.

The build/test/native workflow in mechanical detail: the **`histmersion-dev`** skill (`skills/histmersion-dev/`).
The product plan and its waves: `docs/plan.md`. What it is: `README.md`; the documentation site:
`docs/user-guide/` (`just docs-serve`).

---

## Module boundaries (the most important rule)

```
core  ── the CONTRACT: domain model, wire DTOs, Filter, Tapir endpoint defs, Markdown, config,
         frontmatter parsing.            NO database code, NO side effects.
db    ── the STORE: Magnum repos, Flyway migrations, the seed importer.      depends on core
server─ MCP + REST + web UI; the ONLY database client.        depends on core, db, plugin-api (+ plugin-vaults)
cli   ── a PURE HTTP CLIENT of the server.                                   depends on core ONLY
plugin-api ── the plugin SPI (Plugin, PluginContext, Secret, SecretVault):  sconfig + kindlings ONLY
plugin-vaults ─ the built-in secret-vault plugins.                          depends on plugin-api ONLY
plugin-fixtures/hello ─ a TEST plugin jar (relink tests, smoke.sh); never on the server's class path
plugin-fixtures/hello-ai ─ a TEST AI-provider plugin jar (scripted, no network; the AI-provider suites)
```

A plugin — first-party or not — compiles against `plugin-api` alone and never sees `core`, `db` or
`server`; the server discovers it through ServiceLoader (`server/plugins/PluginHost`). See
`docs/research-plugins.md` (§15: as built). The one exception is the built-in acquisition plugins
(`server/acquire/fetchers`: `archive-org`, `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`,
`arxiv`; the former `oa-index`'s settings are mapped by `server/plugins/LegacyPlugins`): they are host code
over the `acquire.*` settings, supplied to `PluginHost` by `server/plugins/Plugins.builtins` instead of
ServiceLoader, and registered, ordered and reported like any other plugin (§16). The media collections,
event catalogues and AI providers (`server/agents/AiPlugins`: `ai-anthropic` … `ai-opencode`, over
`agents.providers.*`, §23) are built in the same way.

A user's own plugins are linked in by `bin/histmersion-relink` from the bundle's relink kit
(`server/relinkKit`: the Sloth-patched class path and `serverNativeOpts`, the ONE list of native-image
flags) — or run on the JVM (`bin/histmersion-server-jvm`); `histmersion plugin …` manages them on the
local machine and never touches the database (§18). With GPG provenance on, the service starts
`libexec/histmersion-server-verified`, which checks the binary's signed SHA-256 first; off, it starts the
binary directly — keep both paths working when touching the installers or the relink (§21.3).

**The CLI must never gain a dependency on `db`.** No pgjdbc in the CLI binary, no database
credentials on the host. Every CLI command — reads and writes both — goes over HTTP through an sttp
client derived from the same Tapir endpoint the server interprets. If a command seems to need the
database, it needs a new endpoint instead.

If `core` grows an `import com.histmersion.db.…`, the layering is broken. Same for a `println` in `core`.

## One implementation per operation

Every operation is implemented **once**, in `Handlers`. REST routes (`HttpApi`) and MCP tools
(`McpTools`) both call that one implementation; the CLI reaches it over HTTP. This is what makes
"the CLI and the MCP tools agree" a mechanical fact rather than a promise.

Adding an operation:

1. `core/Dto.scala` — the input and output case classes, `derives KindlingsCodecAsObject` plus a
   `given Schema` from `KindlingsSchema.derived`.
2. `core/Api.scala` — the Tapir endpoint definition, added to `Api.all`.
3. `core/Markdown.scala` — how the output renders as Markdown. Every surface returns Markdown.
4. `db/…` — the repo function, if it touches the database.
5. `server/Handlers.scala` — the single implementation.
6. `server/HttpApi.scala` + `server/McpTools.scala` — wire the same handler into both.
7. `cli/Client.scala` + `cli/Main.scala` — the client call and the decline subcommand.

Skipping step 6's second half (an operation exposed as REST but not MCP, or vice versa) is a bug
unless there is a written reason.

## Scala style

- **Scala 3 syntax**, `-source:future`. Significant indentation, `given`/`using`, `enum`,
  `opaque type` for validated identifiers.
- **Direct style.** Ox for structured concurrency where handlers genuinely need it. No cats-effect,
  no ZIO, no `IO[_]`.
- **Immutability by default.** `final case class`, `val`, persistent collections. A `var` needs a
  comment explaining why.
- **Pure functions wherever the work is a transformation.** `Markdown`, `Frontmatter`, `Filter → SQL`,
  the chunker, bbox maths — all pure, all unit-testable with no database.
- **`Either[…, A]` for expected failures.** 404, a bad citekey, a malformed filter, an unreachable
  server: these are values, not exceptions. Exceptions are for genuinely unexpected states.
- **Never `println` or `Console.err` outside `cli/Main`.** The server logs through **scribe**; the CLI
  turns an `Either` into an exit code at the very edge (1 = usage, 2 = the server said no,
  3 = unreachable/startup failure).
- **No side effects in object initialisers.** `Main` is the composition root: it builds
  config → `DataSource` → migrate → `Deps` → `Handlers` → endpoints → bind, in that order, explicitly.
  A `private val ds = Db.fromConfig()` at the top of an `object` is exactly the pattern this replaced.
- **Smart constructors where invariants exist** — `Period(start ≤ end)`, `BBox`, `Citekey`. Validate
  once, at the boundary, then pass the validated type around.

## Kindlings: codecs, schemas and config are derived, not written

```scala
final case class SearchHit(…) derives KindlingsCodecAsObject
object SearchHit:
  given Schema[SearchHit] = KindlingsSchema.derived[SearchHit].schema
```

Circe codec and Tapir schema come from the **same** case class, so the JSON on the wire and the
OpenAPI/MCP schema advertised to clients cannot drift. Config is the same idea:
`derives ConfigReader` (kindlings-sconfig-derivation) over `reference.conf`.

Do not hand-write a `Codec`, a `Schema`, or a config parser. If derivation fails, fix the model.

## Configuration: no constants in code

**Every** port, URL, limit and UI default lives in `modules/core/src/main/resources/reference.conf`,
kebab-case keys, each with a `${?HISTMERSION_*}` env override. A user's own settings go in the user
config file (`histmersion.conf`, `core/UserConfig.scala`): environment > that file > `reference.conf`. A munit lint test greps `src/main` for
`:8000|5432|8100|unpkg` and **fails the build** if a literal is reintroduced. That test is not
negotiable; if you need a new tunable, add a config key.

The web page is included: it reads `/api/config` rather than embedding literals in JS.

`.mcp.json` is the one place a concrete port is rendered, and it is **generated** by `just mcp-json`
(which `just install` runs) from the configured public URL.

## Styling lives in CSS

The web UI's look is `modules/server/src/main/resources/web/style.css`, and only that, so it can be
adjusted with media queries and swapped as a theme without touching Scala.

- A state the UI can be in is a **class** (`cls("is-hidden") <-- …`, `.wm-window.minimized`), never
  `display <-- …` or any other Laminar style property.
- A value that comes from data or from the user — a dragged width, a window's position, a marker's
  size, a colour computed from a period — crosses as a **CSS custom property** (`--sidebar-w:360px`,
  `style="--d:12px"`, `el.style.setProperty("--tip-x", …)`), and a stylesheet rule uses it.
- No colour literals in Scala. Every colour is a token on `:root` (`--ramp-*`, `--edge-*`,
  `--status-*`, `--media-*`, `--conf-*`, …); code names it (`core/Palette`) — `var(--status-current)`
  in markup, or `ThemeTokens` where it must compute with the value (the period ramp, the graph's
  canvas). A theme is a block that redefines the tokens.

- Controls have ONE look: every button is `.btn` (+ `--primary --ghost --toggle --icon --sm --bare
  --block --danger`), every input/select/textarea `.field`, controls that belong together a
  `.btn-group`, and a row of groups a `.toolbar` (one separator between groups, no trailing padding).
  Their sizes, gaps and font size are the control tokens on `:root` (`--control-h`, `--fs-control`,
  `--gap-inline`, `--gap-group`, `--bar-px`, …), which the narrow layout redefines. A new widget
  composes these classes; a widget rule may place a control (margin, width), never restyle it.
  Plain CSS custom properties, no preprocessor: the stylesheet is served as-is from the classpath.

`StyleLintSuite` (core) fails the build on anything else, with a short list of named exceptions;
`CssTokensSuite` (server) fails it when a token the code names is missing from the stylesheet.

## Magnum / SQL conventions

- `sql"…"` interpolation with **parameters**, never string concatenation of user input.
- Deep time is a Postgres **`int8range`** of signed years (BCE negative). Postgres normalises
  `[a,b]` to `[a,b+1)`, so the inclusive upper bound you read back is **`upper − 1`**. Get this wrong
  and every period is one year too long.
- PostGIS geometry moves as **WKT text** (`ST_AsText` / `ST_GeomFromText`), not the `postgis-jdbc`
  binary types — that keeps native-image free of a fiddly dependency while Postgres still does all
  the spatial work.
- `text[]` columns cross as **real arrays**: select the column itself and decode it with
  `DbCodec[List[String]]` (`db/Codecs.scala`); bind a list as `$list::text[]` (or `Param.A` in an
  `Sql` fragment). No joined-string intermediates, no separators to split on.
- Repos are **stateless**: they take the `DataSource` as a parameter. SQL fragments built from a
  `Filter` are values, composed, not accumulated in mutable state.
- No duplicated column lists — one `Fragment` per projection, reused.

## Migrations

`modules/db/src/main/resources/db/migration/V###__short_name.sql`, plain SQL, sequential.

- The server runs `flyway.migrate()` at startup **before binding the port** and exits 3 on failure.
- **Never edit an applied migration.** Add the next number.
- Flyway reads migrations through a `ResourceProvider` fed by a generated `db/migration/index.txt`
  (a `resourceGenerator` in `build.sbt`), because classpath scanning does not work under
  native-image. Dropping a new `V###__*.sql` in is enough; the index regenerates.

## Tests

- **Every pure function gets a test.** No database, runs in seconds, runs in CI.
- **Database and HTTP suites are gated on `HISTMERSION_TEST_DB_URL`** and skip with a visible reason when
  it is unset — never silently, never failing on a machine without Postgres.
- **The DB suites write fixtures, so they only run against a database named `*_test`** (`TestDb`
  refuses anything else, the live `histmersion` above all — running the suites against the live
  corpus once seeded `zz*` test polygons into the web UI). `just test-db` starts a throwaway one on
  :55432 from the bundled runtime; `just test-it` runs everything against it and removes it.
- HTTP smoke tests start the server on **port 0** and hit real endpoints.

- The DB-backed modules (db, server) run their suites in **forked groups, each on a database of its
  own** (build.sbt `dbTestGroups`); inside a group, one suite at a time. A suite that creates a scratch
  database gives it a name of its own — groups share the cluster.
- **sbt 2's disk cache is per checkout** (`target/sbt-cache`), so worktrees never replay each other's
  results; do not disable it with `cacheStores := Seq.empty`.

```bash
sbt test                                                        # pure
just test-quick                                                 # what changed since master + dependents (iterating)
just test-it                                                    # pure + DB suites, on a throwaway DB (the gate)
sbt "testOnly *"                                                # bypass sbt 2's result cache
```

## Native image

Both binaries are GraalVM `native-image` builds. The rules that keep them working:

- **Load config eagerly, on the main thread.** `AppConfig.load()` reads the environment through
  GraalVM `StackValue`; it must not run on an Ox virtual thread.
- **Never call `ConfigFactory.load()`.** It folds every system property into HOCON, and dotted keys
  like `io.netty.allocator.type` then collide and blow up sconfig natively. Parse `reference.conf`
  and resolve `${?ENV}` against the environment only.
- **netty**: unpooled allocator (the pooled one mishandles `ByteBuf` refCnt natively) and the pure-Java
  NIO transport — the kqueue/epoll JNI transports are `exclude`d in `build.sbt` and initialised at
  run time.
- **Logging is scribe** (`scribe-slf4j` bridges Flyway). Do not add a reflective logging backend.
- **Avoid reflection.** Kindlings derivation is macro-generated and reflection-free; keep it that way.

### The tracing-agent rule

> **After adding or upgrading a dependency, run the app once — `sbt server/run` (hit the endpoints)
> and `sbt "cli/run …"` — and commit the changed JSON under
> `modules/<m>/src/main/resources/META-INF/native-image/`.**

`Compile / run` is forked under `-agentlib:native-image-agent=config-merge-dir=…`, so exercising the
app *is* how the metadata stays current. Skip it and you get a binary that builds cleanly and then
dies at runtime on a missing class or resource.

The file is **one `reachability-metadata.json` per module** — the format the JDK-25 agent writes. The
legacy split files (`reflect-config.json`, `resource-config.json`, `jni-config.json`, …) were deleted
when the JDK was moved to 25; native-image still reads them, but the two formats must never both be
present, because the JDK-25 agent EMPTIES a legacy `resource-config.json` it finds in the merge dir
and you silently lose every resource.
`nativeImage` refuses to build while any legacy file sits beside `reachability-metadata.json`, and
each module's `NativeMetadataSuite` fails on one — do not `.gitignore` them: native-image would
still read them from the working tree.

Regenerate metadata **only with the pinned JDK**. Agents disagree across JDK versions: the JDK-24
agent rejects the `jniAccessible` attributes the 25 agent writes, and the 22 agent writes the legacy
format back.

`resources` are recorded **file by file, as the agent sees them requested** — so exercising one page
is not enough. Hit every asset under `web/`, and run the server once against an EMPTY database so
Flyway reads every migration.

A killed agent run leaves `agent-pid*/` scratch dirs — gitignored, and deleted by the `nativeImage`
task, but check `git status` after a hard interrupt.

## The JDK pin

`.sdkmanrc` pins **GraalVM CE 25.0.1**, CI (`.github/workflows/ci.yml`, `bundle.yml`) matches it,
and `scripts/bundle/server.sh` refuses to build with any other major. Moving it is a single
coordinated change — `.sdkmanrc` + the workflows + the regenerated tracing-agent metadata, in one
commit.

It sat at 22.0.2 for the whole of waves 1–2 because of one transitive dependency: `tapir-netty-server-sync`
1.11.x pulled **ox 0.5.x**, whose `DoNothingScope extends StructuredTaskScope`. JEP 505 turned
`StructuredTaskScope` into an interface in JDK 25, so the server built, started, logged
"Histmersion listening", and then died in `NettySyncServer.startAndWait`. **tapir 1.13.31 pulls ox 1.0.2**,
which dropped `StructuredTaskScope` for its own `ox.internal.ThreadHerd` over plain virtual threads —
no preview API, nothing to break. That is what unblocked the bump.

One behavioural difference the JDK brought with it: JDK 25 ships a CLDR in which `en_PL` takes its
number symbols from the region, so `f"$v%.1f"` renders `2,5`. Anything that goes **on the wire** is
formatted against `Locale.ROOT` (see `Backup.humanBytes`) — the default locale is for UI, never data.

## sbt 2 gotchas

| gotcha | consequence | do this |
|---|---|---|
| The thin client shares one server between invocations and **drops the caller's environment** | `HISTMERSION_TEST_DB_URL` silently missing; stale settings | `sbt --server -batch <task>` |
| Test results are **cached** | "tests pass" when they never ran | `sbt "testOnly *"` (`test-it`, `test-quick`) |
| Settings are keyed by **source position** | `key := value` inside a shared `def` makes the last caller win **for every project** | set it per-project inline — see `Compile / mainClass` in `build.sbt` |
| Classpath entries are **virtual file refs** | `.data` is not a `java.io.File` | `fileConverter.value.toPath(a.data).toFile` |
| Module output layout changed | it is **not** `modules/<m>/target/` | `target/out/jvm/<scalaVersion>/<name>/` — locate the artefact, never hardcode the path |

## Docs and skills

- `README.md` — a short overview: what it is, a quick start, responsible use. Everything else is in
  the **documentation site**, `docs/user-guide/` (Zensical, `docs/mkdocs.yml`; `just docs-build` checks
  every link, `just docs-serve` previews it). User pages are written for non-programmers; technical
  depth goes in `reference/` and `contributing/` (see `contributing/docs.md`).
- The reference pages hold the tables (CLI, MCP, REST, config). The **CLI table is generated**:
  `./tools/gen-cli-docs.sh` (or `just cli-docs`) rewrites `docs/user-guide/reference/cli.md` between
  the `CLI REFERENCE` markers from decline's own `--help`; `just api-docs` rewrites the endpoint index
  in `reference/rest-api.md`. Do not hand-edit inside those markers.
- `docs/writing-guide.md` — the rules for prose compiled out of the corpus. They live there **once**;
  skills link to it rather than restating it.
- `skills/<name>/SKILL.md` and `agents/<name>.md` are canonical (tools named `{{tool:<name>}}`);
  the server's agent generator renders them per runtime (docs/research-agent-runtimes.md). `plugin/`
  and `.claude-plugin/marketplace.json` are its checked-in Claude Code output — regenerate with
  `just plugin` (`PluginTreeSuite` fails when stale); `.claude/skills` and `.opencode/skill` point at
  `plugin/skills`. Edit the canonical copies only.
- When you change a CLI flag, an MCP tool, an endpoint or a config key, update the matching reference
  page (`docs/user-guide/reference/`) in the same commit. Docs that lie are worse than missing docs.
- A part of an agent body, a fragment or the writing guide that only one audience acts on is marked
  `<!-- only:interactive -->` (the person's own CLI session), `<!-- only:headless -->` (a server step)
  or `<!-- only:writer -->` (the guide's procedure for whoever compiles an article; reviewers and the
  fixer include `writing-rules`, the guide without it). Every turn of a step re-sends its prompt, so
  keep what a step does not act on out of it; `PromptTrimSuite` holds the rules every role must keep
  and a ceiling per review role (`PromptBudget` prints the sizes).

## Commits

- One logical change per commit. Imperative subject under ~72 chars, no trailing period:
  `Add facets endpoint + MCP tool`, `Fix int8range upper-bound off-by-one`.
- Explain **why** in the body when it is not obvious — the ox/JDK pin comment is the model.
- A commit that changes behaviour carries its tests. A commit that adds a dependency carries the
  regenerated native-image metadata.
- Do not commit generated binaries, `.env`, `.mcp.json` (generated), or anything under `target/`.
- Stream branches are integrated on `master`; do not push from a worktree unless asked.

## Before you say it is done

```bash
sbt "testOnly *"                                  # not the cached result
sbt cli/nativeImage server/nativeImage            # both binaries still build
just bundle && scripts/bundle/smoke.sh dist/histmersion   # the bundle works end to end, beside the live install
git status                                        # no agent-pid*/, no stray files
```
