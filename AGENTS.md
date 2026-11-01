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
values ── the newtypes every layer speaks of: Citekey, Year, Period, Doi, WikidataId, LatLon.   Iron ONLY
api   ── the CONTRACT on the wire (JVM + JS): wire records, Tapir endpoint defs, circe codecs + schemas,
         Markdown, and the helpers they need.   NO database code, NO side effects.   depends on values ONLY
config ── AppConfig and its readers (sconfig, Kindlings), reference.conf.   JVM only.   depends on values ONLY
files ── the local files client and server exchange or keep: the `.hmx` bundle's tar and canonical JSON and
         the merge resolution file (`util.data.exchange`), the Markdown mirror's layout and frontmatter
         (`util.data.mirror`) and the source and article notes (package `files`), the plugin set.
         JVM only (snakeyaml).                                     depends on api ONLY
core  ── the DOMAIN (package `domain`): the logic server and db run on. JVM only.
         NO database code, NO side effects.   depends on values, api, files — never config (its tests only)
db    ── the STORE: Magnum repos, Flyway migrations, the seed importer.      depends on config, core
server─ MCP + REST + web UI; the ONLY database client.  depends on core, db, plugin-api, plugin-host (+ plugin-vaults;
         plugins/fetchers, plugins/events, plugins/media, plugins/gazetteers, plugins/ai-http and plugins/calibre
         at run time only)
         (and through them on values, api and config)
cli   ── a PURE HTTP CLIENT of the server.         depends on api, config and files — never core, never db
web   ── the Scala.js page (Laminar) over the sttp client derived from api's endpoints.   depends on api ONLY
plugin-api ── the plugin SPI (Plugin, PluginContext, Secret, SecretVault) and its own newtypes (Year, Doi, …):
              sconfig + kindlings + iron ONLY
plugin-host ── the host side of the SPI without server or DB: discovery, config, registration, the
              acquisition client, the chain, the first-party settings table, how plugin
              media/catalogues/AI are read.            core, config, upstreams + plugin-api
upstreams ── the clients and parsers of the upstream services the host and first-party plugins share
              (OpenAlex/Crossref, archive.org, CORE, PMC, Europe PMC, landing pages, JATS, catalogue
              datings); registers nothing.            depends on plugin-api ONLY (+ circe, sttp core)
plugins/fetchers ─ the first-party acquisition plugins (archive-org, oa-unpaywall, oa-core, oa-openalex,
              oa-landing, pmc, arxiv).                           upstreams (+ plugin-testkit for tests)
plugins/events ─ the first-party event catalogues (events-gvp, events-noaa, events-wikidata).
                                                                 upstreams (+ plugin-testkit for tests)
plugins/media ─ the first-party media collections (media-wikidata, media-depicts, media-met, media-cleveland,
              media-aic, media-loc, media-pleiades, media-opencontext, media-smithsonian, media-europeana).
                                                                 upstreams (+ plugin-testkit for tests)
plugins/gazetteers ─ the first-party network gazetteers (gazetteer-cliopatria, gazetteer-awmc,
              gazetteer-chronontology).                          upstreams (+ plugin-testkit for tests)
plugins/ai-http ─ the first-party HTTP AI providers (ai-anthropic, ai-openai, ai-local) over the SPI's AiHttp.
                                       plugin-api (+ circe, sttp core; plugin-testkit for tests)
plugins/calibre ─ the first-party `calibre` plugin: the local Calibre library as a source collection (calibredb,
              the sqlite3 fallback, the write-back).        plugin-api + circe (+ plugin-testkit for tests)
plugin-testkit ─ a plugin's TEST dependency: load and run it as the server does (plugin-host), with
              live/record/replay HTTP; shipped in the install's maven/, its API a contract.  plugin-host
plugin-vaults ─ the built-in secret-vault plugins.                          depends on plugin-api ONLY
plugin-fixtures/hello ─ a TEST plugin jar (relink tests, smoke.sh); never on the server's class path
plugin-fixtures/hello-ai ─ a TEST AI-provider plugin jar (scripted, no network; the AI-provider suites)
plugin-fixtures/hello-lib + hello-deps ─ a TEST plugin with a dependency of its own, in a fixture Maven repo
plugin-resolver ── resolves user plugins' dependencies (coursier): relink kit tools/, never on a class path
chimney-iron ── a Chimney macro extension (newtype → newtype); compile-time only, consumed `% Provided`
chimney-iron-tests ─ its proof suites, compiled as a consumer compiles them
```

`values`, `api` and `core` are sbt 2 `projectMatrix` rows (`build.sbt`): the JVM row keeps the module's
name (`values`, `api`, `core`), the Scala.js row adds `JS` (`valuesJS`, `apiJS`; core has none). Shared
sources are in `src/main/scala` (and `src/test/scala`); a platform's own in `src/main/scalajvm` or
`src/main/scalajs`. A plain project names a row as `api.jvm(scala3)` or `api.js(scala3)`.

A plugin — first-party or not — compiles against `plugin-api` alone (a first-party one also against
`upstreams`) and never sees `values`, `api`, `config`, `core`, `db` or `server`; the server discovers it
through ServiceLoader (`PluginHost`, in `plugin-host`, which the testkit runs too). See
`docs/research-plugins.md` (§15: as built). The acquisition plugins (`plugins/fetchers`), the event
catalogues (`plugins/events`) and the network gazetteers (`plugins/gazetteers`) are first-party plugins discovered from the image's own class path; their
names are reserved (`ReservedNames.firstPartyPlugins`), and the host supplies them the paths of its own
settings that `pluginhost/FirstPartySettings` lists (`histmersion.acquire.*`, `histmersion.events.<catalogue>.*`
— the user keeps writing those), the same table deciding which plugin a settings change rebuilds; the former
`oa-index`'s settings are mapped by `util/plugins/LegacyPlugins`. The HTTP AI providers
(`plugins/ai-http`) are first-party plugins too, supplied `histmersion.agents.providers.<name>.*`, and so are the
media collections (`plugins/media`), supplied `histmersion.media.*` and run by the host's one collection import
(`server/media/MediaImporter`). The command-line AI runtimes (`server/agents/BuiltinAi`: `ai-claude-code`, `ai-codex`,
`ai-opencode`, §23) are still host code wrapped by `server/util/plugins/HostProvided`, until they move too
(`docs/research-domain-model.md` §9.2; their SPI, SPI-9, is designed in §19).

A user's own plugins are linked in by `bin/histmersion-relink` from the bundle's relink kit
(`server/relinkKit`: the Sloth-patched class path and `server/nativeImageOptions`, the ONE list of native-image
flags) — or run on the JVM (`bin/histmersion-server-jvm`); `histmersion plugin …` manages them on the
local machine and never touches the database (§18). A plugin compiles against the install's Maven
repository (`maven/`, `server/pluginRepo`: plugin-api and the plugin-platform POM, whose libraries and
versions are the contract in `modules/plugin-api/plugin-platform.txt`) and is published with a POM; its
dependencies are resolved by `plugin-resolver` against the kit's `modules` (what the server ships is not
installed again, a newer version of it is refused) into `<data>/plugins/lib`, and linked with it. Plugins
never shade. With GPG provenance on, the service starts
`libexec/histmersion-server-verified`, which checks the binary's signed SHA-256 first; off, it starts the
binary directly — keep both paths working when touching the installers or the relink (§21.3).

**The CLI must never gain a dependency on `db`.** No pgjdbc in the CLI binary, no database
credentials on the host. Every CLI command — reads and writes both — goes over HTTP through an sttp
client derived from the same Tapir endpoint the server interprets. If a command seems to need the
database, it needs a new endpoint instead.

If `api`, `config`, `files` or `core` grows an `import com.histmersion.db.…`, the layering is broken. Same for a
`println` in any of them; the compiler keeps `api` free of `domain` and `config`, and `config` free of
`domain` and `api` (the server converts a config record the wire shows, `server/UiConfigViews`), and the domain
free of `config`: it takes settings records of its own (`ManagerDefaults`, `SchedulerDefaults`, `MergeSettings`,
…), which the server — or plugin-host, for the media settings — converts from config where it reads them. The
domain still works on api's wire records where both sides have one shape, and on the pure helpers clients
share (recorded, permanent edges); `docs/research-module-layout.md` lists them, with the steps that remain.

## One implementation per operation

Every operation is implemented **once**. REST routes (`HttpApi`) and MCP tools (`McpTools`) both call that one
implementation; the CLI reaches it over HTTP. This is what makes "the CLI and the MCP tools agree" a mechanical
fact rather than a promise.

A feature in its own package (`domain.<domain>.<feature>`; `domain.personal` and `util.settings` are the
models) implements it in a service its controllers call. A feature not moved yet still implements it in
`Handlers`; follow the shape the feature has, and move it only as its slice of the refactor does
(`docs/research-domain-model.md` §9).

Adding an operation to a feature in its own package:

1. `values` — a newtype for every new kind of value (rule 1). Its JSON, schema, column, setting and CLI argument
   come from the generic instances (`util.json.NewtypeCodecs`, `util.database.NewtypeColumns`,
   `config.NewtypeReaders`, `cli.NewtypeArguments`); add a round-trip test.
2. `api.<domain>.<feature>` — the input and output records, one per file, `derives KindlingsCodecAsObject,
   KindlingsSchema`; the Tapir endpoint in the feature's endpoints object, added to `Api.all` in its place (route
   matching and `tools/list` depend on the order); how the output renders as Markdown. Every surface returns
   Markdown.
3. `domain.<domain>.<feature>` — the pure rules (in `core` when another module needs them); the service trait's
   method, its `Implementation` over a `Transactor` and its `InMemory` over the feature's state; the repository
   method (`private[<feature>]`, `(using DbCon)`); the service contract suite, run on both.
4. `<Feature>Controllers` — parse the raw inputs into domain values (the one parse boundary), the maintenance
   guard for a write, the service call with the `Caller`, the conversion to the wire record (Chimney).
5. `<Feature>Routes` + `<Feature>Tools` — the same controller method bound to its REST endpoint and its MCP tool,
   listed in `HttpApi` and `McpTools`.
6. `cli` — the client call and the decline subcommand (`Cli`).
7. The contract snapshots (`ContractSnapshotSuite`, `CliHelpSnapshotSuite`) fail on the new endpoint, tool and
   command: rewrite them with `HISTMERSION_UPDATE_CONTRACT=1` and check that the diff holds only the addition.
   Update the reference pages (`docs/user-guide/reference/`) in the same commit.

Skipping step 5's second half (an operation exposed as REST but not MCP, or vice versa) is a bug unless there
is a written reason.

## Scala style

- **Scala 3 syntax**, `-source:future`. Significant indentation, `given`/`using`, `enum`; model
  values are Iron newtypes (*Coding practices* below).
- **Direct style.** Ox for structured concurrency where handlers genuinely need it. No cats-effect,
  no ZIO, no `IO[_]`.
- **Immutability by default.** `final case class`, `val`, persistent collections. A `var` stays
  local to the one method that needs it.
- **Pure functions wherever the work is a transformation.** `Markdown`, `Frontmatter`, `Filter → SQL`,
  the chunker, bbox maths — all pure, all unit-testable with no database.
- **`Either[…, A]` for expected failures.** 404, a bad citekey, a malformed filter, an unreachable
  server: these are values, not exceptions. Exceptions are for genuinely unexpected states.
- **Never `println` or `Console.err` outside `cli/Cli`.** The server logs through **scribe**; the CLI
  turns an `Either` into an exit code at the very edge (1 = usage, 2 = the server said no,
  3 = unreachable/startup failure).
- **No side effects in object initialisers.** `Main` is the composition root: it builds
  config → `DataSource` → migrate → `Deps` → `Handlers` → endpoints → bind, in that order, explicitly.
  A `private val ds = Db.fromConfig()` at the top of an `object` is exactly the pattern this replaced.

## Coding practices

These rules bind new and changed code. Existing code that breaks them is brought in line when it is
changed for another reason, not in a sweep. Iron (`io.github.iltotore::iron`) is a dependency of
`values`, `api`, `core` and `plugin-api`; Chimney (`io.scalaland::chimney`) of `server`, where the layers meet (and of
`chimney-iron`): another module that needs it adds it, with its native-image metadata. The examples were compiled against Iron 3.4.1 and
Chimney 2.1.0.

### 1. No primitives in the model

Every value in the model has a type that says what it means: an Iron newtype, not a raw
`String`/`Int`/`Long`, and not a bare refined type (`String :| Match[…]`) passed around as such. A
constraint is optional (`Pure` accepts every value of the base type). A smart constructor exists only
where the data must be checked. Use `RefinedType` (opaque), not `RefinedSubtype`: a `Citekey` is not
silently a `String`; `.value` says where the raw value is taken out.

```scala
type Year = Year.T
object Year extends RefinedType[Int, Pure]                    // Year(-3300): BCE negative

type CitekeyRule = Match["[a-z][a-z0-9_-]*"]
type Citekey = Citekey.T
object Citekey extends RefinedType[String, CitekeyRule]       // Citekey.either(raw) at the boundary

def neighbours(citekey: Citekey, depth: Int): List[Citekey]   // not (citekey: String, depth: Int)
```

### 2. Parse, don't validate

A type that needs checking can only be constructed valid: `final case class Foo private (…)`, with a
smart constructor on the companion that returns `Either`. Scala 3 makes `apply` and `copy` private with
the constructor, so all code goes through it, and nothing downstream checks again. A case class built
from already-valid typed parts with no invariant of its own keeps its plain public constructor and gets
no smart constructor.

```scala
final case class Period private (start: Year, end: Year)
object Period:
  def from(start: Year, end: Year): Either[PeriodError, Period] =
    Either.cond(start.value <= end.value, new Period(start, end), PeriodError.EndsBeforeStart(start, end))

final case class Person(name: PersonName, born: Year)        // no invariant: public constructor
```

### 3. Separate models per layer

API, database and domain may each have their own package and model, in the representation that is
natural there and works with the library that serves it (Kindlings derivation for codecs and schemas,
Magnum's `DbCodec`). Chimney converts between them. The domain model knows nothing of the database
layout or the API shape, so each can change on its own. Type-class instances for domain types (codecs,
schemas, `DbCodec`s) are defined in the API or DB module that needs them, never on the domain companion.
An API or DB model may reuse a domain value as long as that gets in nothing's way; split a
representation only when there is an actual reason to.

```scala
package com.histmersion.domain                // no codecs, no SQL
final case class Source(citekey: Citekey, title: Title, period: Period)

package com.histmersion.api                   // the wire shape; reuses domain values
final case class SourceView(citekey: Citekey, title: Title, start: Year, end: Year) derives KindlingsCodecAsObject

package com.histmersion.db                    // the instances the DB layer needs, in the DB layer
given DbCodec[Citekey] = DbCodec[String].biMap(Citekey.applyUnsafe, _.value)
```

### 4. Cheap conversions between layers

Iron's newtypes make crossing a layer free. `.value` exposes a newtype's refined type (`String :|
CitekeyRule`), and another newtype's `apply` accepts that refined type without checking it again. A
raw `String` does not compile there, so the check cannot be skipped by accident. Never `assume` between
layers: it accepts any value, including one under a different constraint.

Chimney derives newtype → newtype through the `chimney-iron` macro extension: a module that uses
Chimney depends on it `% Provided`. It only runs inside the compiler, and the code it generates never
refers to it, so it stays off `Runtime / fullClasspath`. `Compile / fullClasspath` still lists it, and
the native-image tasks read that one today: the first module that ships a binary and depends on
`chimney-iron` moves them to `Runtime / fullClasspath`. It builds with the build's Scala and needs 3.10
or newer: Chimney 2 and Iron 3.4 are compiled by Scala 3.9, which no older compiler can read, and a
Scala 3.9.0 compiler bug, fixed in 3.10.0, stops the extension's own compilation with a cyclic
reference. It converts `From` into `To` as `To(from.value)`, with no check, when

- both are `type X = X.T` over an `object X extends RefinedType[Base, Constraint]` (rule 1's shape),
- the two companions have the same name (`domain.Citekey` → `api.Citekey`), and
- their base types and constraints are the same types.

Same-named newtypes with different constraints fail to compile, naming both. Differently named ones
are not converted even over the same constraint (`Year` and `PageSize` are both `Int :| Pure`). A
`Transformer` or `PartialTransformer` given for a pair always wins over the extension: write one to opt
a pair out, for a conversion that must check, or between differently named newtypes.

```scala
package com.histmersion.api
type Citekey = Citekey.T
object Citekey extends RefinedType[String, CitekeyRule]       // same name, same constraint as domain's

source.transformInto[SourceView]              // field by field, no constraint re-checked, no givens
```

### 5. Self-explanatory code

What code does must be unambiguous from the names of its types (including their packages), methods,
parameters and variables. A comment that describes how a piece of code works is, more often than not,
a failure to meet this rule: rename, extract a method or introduce a type instead.

```scala
// no: def run(xs: List[String], n: Int) = xs.take(n) // n = page size
def firstPage(citekeys: List[Citekey], pageSize: PageSize): List[Citekey] = citekeys.take(pageSize.value)
```

### 6. Comments go in two places only

- **Scaladoc on the public API plugins see** (`plugin-api`). Plugin authors read it through cellar and
  similar tools, without the code.
- **Where behaviour answers something outside the code**: an external decision, an external blocker,
  or a factor that cannot be expressed in code without over-engineering or absurdly long names.

Either way a comment stands on its own. It never points at Histmersion's internal docs, tickets or
queue ids, GitHub issues or "§x.y" sections: what the reader needs goes in the comment, and history
goes in the commit message.

```scala
// GraalVM 25.4's builder refuses a java.* class on the image class path; this jar holds one.
excludeDependencies += ExclusionRule("io.github.cquiroz", "scala-java-time-tzdb_3")
```

### 7. Derive in the `derives` clause

Type-class instances that Kindlings derives are requested in the type's `derives` clause, not as a
separate `given … = …derived` line.

```scala
final case class BuildStamp(version: Version, commit: CommitId) derives KindlingsCodecAsObject, KindlingsSchema
```

### 8. Lenses and loops, not copies and reallocating folds

Updating one field of an accumulator (`acc.copy(queued = acc.queued + n)`) is a job for a lens
(Kindlings' optics). A fold that allocates a new value for every element is written as a `@tailrec`
loop over plain parameters, building the result once at the end.

```scala
def of(states: List[(JobState, Int)]): JobCounts =
  @tailrec def loop(rest: List[(JobState, Int)], queued: Int, running: Int, done: Int): JobCounts =
    rest match
      case (JobState.Queued, n) :: tail  => loop(tail, queued + n, running, done)
      case (JobState.Running, n) :: tail => loop(tail, queued, running + n, done)
      case (_, n) :: tail                => loop(tail, queued, running, done + n)
      case Nil                           => JobCounts(queued, running, done)
  loop(states, 0, 0, 0)
```

### 9. One top-level type per file

A file holds one top-level type with its companion, unless the types are genuinely one thing: a sealed
hierarchy, or a helper so tied to a type that it could live in its companion. Several unrelated types in
one file make the incremental compiler recompile every use site of all of them when one changes.

### 10. Package by feature, not by layer

- Types shared across the whole application (the shared kernel: `Caller`, `Licence`, …):
  `com.histmersion.domain`.
- Each domain and its features: `com.histmersion.domain.<domain>.<feature>` (the pure part in `core`, services,
  repositories and controllers in `server`), with `api.<domain>.<feature>` and `config.<domain>` beside them.
- Supporting areas (mechanisms with no research meaning): `com.histmersion.util.<area>`, e.g. `util.database`,
  `util.json`, `util.storage`.
- What is shared only inside a (sub)domain is package-private, not public. A service and its repository
  live in the same package, and the repository is invisible outside it.
- Subdomains follow functionality, not the shape of endpoints, MCP tools or CLI commands: one endpoint may
  use several subdomains.
- The `db` module holds database primitives only; migrations and repositories belong to the feature that
  owns the data.
- Shared API types and API-specific types live apart.

The places and what each may depend on (`docs/research-domain-model.md` §3):

| place | about | may depend on |
|---|---|---|
| `util.<area>` (`database`, `json`, `mcp`, `storage`, `data`, `jobs`, `identity`, `plugins`, `settings`, `basemap`, `ocr`, `process`, …) | a mechanism: no research words | other supporting areas, the shared kernel, `values` — never a domain |
| `domain.automation` | the workflow engine, by feature: `agents` (runs, steps, the toolbox, model chains, the definitions and their generator), `approvals`, `autopilot`, `goals`, `managers`, `scheduler`, `worklists` (the ACQUIRE/TASKS/LOG kernel); `workflows` holds what the domains contribute to it (`WorkflowDefinition`, `ToolContribution`, `ManagerKindDefinition`, `ObjectiveProgress`, the `Workflows` registry) and what the engine asks of the rest of the server (`AutomationHost`) | supporting areas |
| `domain.catalogue` | places, periods, events and what attests them | supporting areas, automation |
| `domain.media` | pictures and their rights | supporting areas, automation, catalogue |
| `domain.library` | sources, findings, citations, acquisition, ingest | supporting areas, automation, catalogue, media |
| `domain.research` | research results: articles, their review | supporting areas, automation, catalogue, media, library |
| `domain.personal` | one person's bookmarks, notes, read marks | supporting areas only |
| `server` (composition root, `server.views`, `server.instance`) | wiring; pages that join several domains | everything |

A lower domain reaches a higher one only through an interface the lower one owns and the higher one implements
(a workflow the library registers into the engine, a data contribution a domain registers into `util.data`). An
agent tool is a `ToolContribution` and a workflow a `WorkflowDefinition` registered into the engine's `Workflows`;
a workflow names the tools each of its roles may use, and the engine offers an agent nothing else. A kind of manager
is a `ManagerKindDefinition` (the supervisor's loop, holds, retries, cron and approvals stay the engine's), a domain's
measure of a goal an `ObjectiveProgress`, what approving its proposals does an `ApprovalEffects`; runs are recorded
through `util.jobs.JobRuns`. The engine is built from an `EngineSite` the composition gives it (its clock among
them). Inside the engine, a feature reaches another's data only through that feature's service — `agents.RunService`
(runs and their steps) and `agents.ApprovalService`, `managers.ManagerService`, `goals.GoalService` and
`goals.FocusService`, `autopilot.AutopilotRowService` — each with an `InMemory` over the shared
`domain.automation.AutomationState` and one contract suite (`EngineStoreSuite`) run on both. Today's pipelines,
tools, manager kinds and effects are registered by transition adapters in the composition root (`server/agents`:
`PipelineWorkflow`, `TransitionTools`, `TransitionWorkflows`, `TransitionSite`, `TransitionHost`, the four
`…ManagerKind`s, `ArticleObjectives`, `TransitionApprovalEffects`) until their domains define their own;
`server/agents` also keeps the library's and research's pipelines and the command-line AI runtimes until their slices.
Files and directories are reached through `util.storage`, processes launched through `util.process`, and a
repository is used only inside its feature. `ArchitectureSuite` (server tests) enforces the table, the file and
process rule and the repository rule. A file not in its feature package yet has its destination in
`architecture/places.tsv`; a new file goes in its feature package, or gets a row there. Today's violations are
listed in `architecture/exceptions.tsv`, exactly: a change removes rows and adds none
(`HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites the list; the diff must only delete lines).

### 11. Services own transactions

A repository method takes the transaction it runs in; it never opens one. The service opens the
transaction, so several repository calls are atomic together, through `util.database.Transactor`:
`db.read(…)` on one connection, `db.transact(…)` in one transaction an exception rolls back.

### 12. No globals

Nothing reads the environment, system properties, the clock or the working directory directly
(`System.getenv`, `sys.props`, `Instant.now()` …). Values come from configuration, or are passed in. The
entry point reads them once: the server's builds a `SystemContext` (a `java.time.Clock`, the `Environment`
the settings were loaded with, the `UserPaths` that follow from it) and the composition hands it on
(`Deps.clock`, `Deps.environment`, `Deps.userPaths`); a test passes `Clock.fixed` or `SystemContext.isolated`.

### 13. Services are traits; controllers depend on them

A service is a trait. Its companion holds the implementations:

```scala
trait BackupService:
  def run(request: BackupRequest): Either[BackupError, Backup]

object BackupService:
  def apply(config: BackupConfig, transactor: Transactor): BackupService = Implementation(config, transactor)

  private final class Implementation(config: BackupConfig, transactor: Transactor) extends BackupService:
    …

  final class InMemory(state: AtomicReference[BackupState]) extends BackupService:
    …
```

`InMemory` is not a mock. It does not persist, but in tests it behaves exactly as the database
implementation does. Where the real services share data, the in-memory ones share atomic references, and
a test seeds them by calling the `InMemory` services themselves. No mocking library.

Endpoint logic lives in `…Controllers` (not `…Handlers`) that take services as dependencies, so a
controller is unit-tested with `InMemory` services and no server. Each controller method and each writing
service method takes the `Caller` (`domain.Caller`: a `Person` with a `Role`, or an `Automation` run); until
accounts exist every request is `Caller.Local`.

### 14. No free-standing helper objects

A function about a type is a method of the type or a member of its companion — an extension where the type's
module must not know the function's dependencies. A closed set of strings is an `enum`. A top-level object
without a same-named type is one of: a repository, a group of endpoints, CLI commands or views, a holder of
givens, a codec of an external format, or a facade that names a composition. Entry points are `@main def`s.

```scala
// no: object ApprovalModes { def configured(raw: String): ApprovalMode = … }
enum ApprovalMode:
  case Manual, Auto
object ApprovalMode:
  def configured(raw: String): ApprovalMode = parse(raw).getOrElse(Manual)

@main def histmersionServer(args: String*): Unit = …          // not object Main { def main(…) }
```

The entry points are `com.histmersion.server.histmersionServer`, `cli.histmersionCli`, `web.histmersionWeb`,
`server.plugins.pluginTool` (the relink kit's tool), `server.agents.generatePlugin` (`just plugin`) and
`pluginresolver.pluginResolver`. A `@main def` must not share its name, ignoring case, with a type of its
package: on a case-insensitive file system their class files overwrite each other.

Rules 7–14 bind new code from 2026-10-30. Existing code is migrated by two planned jobs (a style sweep and
a restructure by feature), not piecemeal; until then, follow the surrounding code where the two conflict
only to keep one file consistent.

## Kindlings: codecs, schemas and config are derived, not written

```scala
final case class SearchHit(…) derives KindlingsCodecAsObject, KindlingsSchema
```

Circe codec and Tapir schema come from the **same** API case class, so the JSON on the wire and the
OpenAPI/MCP schema advertised to clients cannot drift. A domain type's instances live in the API or DB
module that needs them, never on its companion (*Coding practices* 3); a newtype's instance maps its
base type's. Config is the same idea:
`derives ConfigReader` (kindlings-sconfig-derivation) over `reference.conf`.

Do not hand-write a `Codec` or `Schema` for a case class or enum, nor a config parser. If derivation
fails, fix the model.

## Configuration: no constants in code

**Every** port, URL, limit and UI default lives in `modules/config/src/main/resources/reference.conf`,
kebab-case keys, each with a `${?HISTMERSION_*}` env override. A user's own settings go in the user
config file (`histmersion.conf`, `config/LoadedConfig.scala`): environment > that file > `reference.conf`. A munit lint test greps `src/main` for
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
  `--status-*`, `--media-*`, `--conf-*`, …); code names it (`api/Palette`) — `var(--status-current)`
  in markup, or `ThemeTokens` where it must compute with the value (the period ramp, the graph's
  canvas). A theme is a block that redefines the tokens.

- Controls have ONE look: every button is `.btn` (+ `--primary --ghost --toggle --icon --sm --bare
  --block --danger`), every input/select/textarea `.field`, controls that belong together a
  `.btn-group`, and a row of groups a `.toolbar` (one separator between groups, no trailing padding).
  Their sizes, gaps and font size are the control tokens on `:root` (`--control-h`, `--fs-control`,
  `--gap-inline`, `--gap-group`, `--bar-px`, …), which the narrow layout redefines. A new widget
  composes these classes; a widget rule may place a control (margin, width), never restyle it.
  Plain CSS custom properties, no preprocessor: the stylesheet is served as-is from the classpath.

`StyleLintSuite` (api) fails the build on anything else, with a short list of named exceptions;
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
- **Never edit an applied migration.** Add the next number. When an applied migration's text must
  change anyway, list its old and new Flyway checksums in `Migrate.Replaced`, so that databases that
  ran the old text still validate.
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

Regenerate metadata **only with the pinned JDK** (after `sdk env`, `sbt server/run`; the agent run
refuses any other JDK). Agents disagree across JDK versions: the JDK-24
agent rejects the `jniAccessible` attributes the 25 agent writes, and the 22 agent writes the legacy
format back.

`resources` are recorded **file by file, as the agent sees them requested** — so exercising one page
is not enough. Hit every asset under `web/`, and run the server once against an EMPTY database so
Flyway reads every migration.

A killed agent run leaves `agent-pid*/` scratch dirs — gitignored, and deleted by the `nativeImage`
task, but check `git status` after a hard interrupt.

## The JDK pin

`scripts/bundle/graalvm-pins.txt` pins **GraalVM CE 25.4** ("25 Innovation 4", JDK **25.0.4.1.1** — the
version its `release` file declares, which is the string every check compares): one line per platform
with the official release URL and its SHA-256. `.sdkmanrc` names the same build
(`java=25.4.4.1+1-graalce`, which sdkman downloads from that URL), so in a checkout
`sdk env install && sdk env` is the whole setup. The build uses the **active** JDK (`JAVA_HOME`, else
`java` on `PATH`) and refuses any other version: `nativeImage` and the tracing-agent run (`project/NativeImage.scala`),
the justfile's sbt recipes (`scripts/dev/sbt.sh`) and `scripts/bundle/server.sh`, which also writes the
version into the relink kit (`kit/graalvm-version`). The pins file serves the machines without sdkman:
CI (`.github/workflows/ci.yml`, `bundle.yml`) and an end user's relink provision it with
`histmersion-toolchain install` (`scripts/bundle/toolchain.sh`), which checks the SHA-256. Moving the pin
is a single coordinated change — the pins (version, URLs, SHA-256 checked against the release's `.sha256`
AND GitHub's asset digest), the `java=` line of `.sdkmanrc`, the docs that name it, and the re-checked
tracing-agent metadata, in one commit.

25.4 is an **innovation** release: it gets no fixes once the next one is out, so the pin moves with each
new one (docs/research-build-speed.md §5 has why it was adopted: 20 % smaller binaries at the same build
time). It ships no macOS x64 build. Its builder refuses a `java.*` class on the image class path, which
is why build.sbt excludes `scala-java-time-tzdb_3` (a Scala.js time-zone database scribe's perfolation
pulls onto the JVM, holding its own `java.time.zone.TzdbZoneRulesProvider`); the binaries use the JDK's
tzdb, and `histmersion-server --self-check` checks it (`TimeZoneCheck`). Never pass
`-H:+AllowDeprecatedBuilderClassesOnImageClasspath` instead.

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

## Formatting

Every Scala source, `build.sbt` and `project/*.scala` is formatted by **scalafmt** (`.scalafmt.conf`:
Scala 3 dialect, 120 columns; it keeps each block's braces or indentation as written). Compiling formats
a module's sources (`scalafmtOnCompile`); `just fmt` formats everything, `just fmt-check` fails on an
unformatted file and is the first step of `just test-quick` and of CI. Commits that only reformat go in
`.git-blame-ignore-revs` (`git config blame.ignoreRevsFile .git-blame-ignore-revs` once per clone).

## Commits

- One logical change per commit. Imperative subject under ~72 chars, no trailing period:
  `Add facets endpoint + MCP tool`, `Fix int8range upper-bound off-by-one`.
- Explain **why** in the body when it is not obvious. The body is where history, ticket ids and
  links to docs belong; code comments carry none of them (*Coding practices* 6).
- A commit that changes behaviour carries its tests. A commit that adds a dependency carries the
  regenerated native-image metadata and `THIRD-PARTY.md` (`just licences`); a dependency whose
  licence it marks `INCOMPATIBLE` with the AGPL does not go in.
- Histmersion is AGPL-3.0-or-later (`LICENSE`, `NOTICE`, `ThisBuild / licenses`). Source files carry
  no licence header.
- Do not commit generated binaries, `.env`, `.mcp.json` (generated), or anything under `target/`.
- Stream branches are integrated on `master`; do not push from a worktree unless asked.

## Before you say it is done

```bash
just fmt-check                                    # formatted (just fmt fixes it)
sbt "testOnly *"                                  # not the cached result
sbt cli/nativeImage server/nativeImage            # both binaries still build
just bundle && scripts/bundle/smoke.sh dist/histmersion   # the bundle works end to end, beside the live install
git status                                        # no agent-pid*/, no stray files
```
