# Module layout: domain, API, store, plugins (#3940, 2026-10-24)

The question: clients (the web page, the CLI, third parties) share the API with the server, but should
not have to depend on the core domain; a plugin should not have to pull in the API or the database
interfaces. This note measures how the modules are organised today, proposes a target layout that
follows AGENTS.md's *Coding practices* (separate models per layer, instances outside the domain, Iron
newtypes, cheap conversions), and gives a migration order. Nothing is refactored here; it is for a
decision.

Figures are from branch `practices` (master `71cb46fb` + two documentation commits). Type usage is
counted by name — every top-level `class`/`object`/`trait`/`enum`/`type` declared in a module, matched
as a whole word in another module's `src/main` with comments stripped — so it slightly over-counts
(a name that collides with a library's) and is good to ±5 %.

## 1. Today

### 1.1 Modules and what they expose

| module | platform | src/main files | lines | packages | depends on |
|---|---|---|---|---|---|
| `core` (shared) | JVM + JS | 105 | 23 882 | `com.histmersion.core` (one) | tapir-core, tapir-json-circe, circe, kindlings (circe + tapir schema) |
| `core` (jvm) | JVM | 51 | 12 917 | `com.histmersion.core` (same) | + sconfig, kindlings-sconfig, snakeyaml |
| `db` | JVM | 57 | 13 124 | `com.histmersion.db` | coreJVM, Magnum, Flyway, pgjdbc |
| `server` | JVM | 133 | 29 891 | `server`, `.acquire`, `.agents`, `.basemap`, `.events`, `.hot`, `.importers`, `.media`, `.plugins` | coreJVM, db, plugin-api, plugin-vaults |
| `cli` | JVM | 36 | 5 845 | `com.histmersion.cli` | coreJVM, decline, sttp client |
| `web` | JS | 78 | 17 513 | `web`, `.map`, `.map.maplibre`, `.ui` | coreJS, Laminar, sttp client |
| `plugin-api` | JVM | 7 | 671 | `com.histmersion.plugin` | sconfig, kindlings-sconfig |
| `plugin-vaults` | JVM | 3 | 417 | `com.histmersion.plugins.vaults` | plugin-api |

`core` declares **730 top-level types** in one package: 572 in the shared (JVM + JS) part, 158 JVM-only.
By content its 156 files are: 63 with derived codecs/schemas only, 9 endpoints + codecs, 11 endpoints,
2 endpoints + config, 1 config, 70 with none of these (pure logic and plain data). **549 core types carry
a derived circe codec or tapir schema** (1 048 `derives Kindlings…` / `given Schema` sites), including
the domain records themselves: `Source`, `Finding`, `Period`, `SourceStatus`, `EdgeKind`… have their
`Encoder`/`Decoder`/`Schema` on their own companions (`Domain.scala`). Config (`AppConfig` and its 30-odd
`*Config` records, sconfig readers) sits in the same package, JVM side.

### 1.2 Who uses what across the boundary

| consumer | core types used | of them with a codec | JVM-only core types used | references |
|---|---|---|---|---|
| `server` | 477 | 252 | 74 | 4 609 |
| `cli` | 300 | 181 | 18 | 1 126 |
| `db` | 289 | 165 | 17 | 1 293 |
| `web` | 165 | 86 | 0 (JS) | 1 333 |

- **118 core types are used by no other module** (internal helpers of core's own logic).
- **364 core types form the client surface** (used by web or cli); web and cli share 101 of them.
- **248 core types are used only by server and/or db** — domain logic, config and policies that every
  client still compiles against and, on the JVM, links into the CLI binary.
- **web** touches 67 of core's 156 files. Its heaviest: `Merge` (22 types), `Dto` (13), `Places` (10),
  `Domain` (9), `Basemap`, `Articles`, `Events`, `Media` (6 each). Besides the wire records it uses
  domain-side helpers: `Period`, `Years`/`YearFmt`, `Palette`, `Plural`, `Filter`, `ListFilters`,
  `SiteTypes`, `EventTaxonomy`, `Cron`, and the Markdown renderers (`AgentsMarkdown` 12 refs,
  `ManagersMarkdown` 8, `MergeMarkdown` 3).
- **cli** touches 89 files. The 18 JVM-only types it uses are config and local files: `AppConfig`,
  `CliPluginsConfig`, `UserPaths`, `LoadedConfig` (the `config` and `plugin` commands read the same
  layers as the server), `Frontmatter`, `Export`, `BundleFormat`/`BundleTar`, `PluginKit`,
  `MergeResolutionsFile`, `PlaceSeed`.
- **Endpoints**: the eight `*Api` objects hold about 250 endpoints. web calls 82 of them, cli 221, the
  server interprets 244. Markdown rendering of responses is used on all three surfaces (cli: 28
  `AgentsMarkdown` refs, server 32, web 12), so it belongs with the wire records, not with the server.
- **db** already owns its `DbCodec`s (140 `given … DbCodec` in db, none in core). It uses 17 JVM-only
  core types: config (`DbConfig`, `QueryConfig`, `MergeConfig`, `Limits`), the bundle format, the
  merge planner, scheduling and review policies.

### 1.3 What the CLI binary and the web bundle carry

- **The CLI's native-image metadata** names 557 `com.histmersion.core.*` types out of 638 entries,
  among them the server's agent configuration (`AgentApprovalsConfig`, `AgentProvidersConfig`,
  `AgentRuntimesConfig`…). The server's names 645 of 1 125.
- **`app.bundle.js`** (`web/fullLinkJS`, today's build): **20.8 MB, 2.17 MB gzipped**. Attributed by its
  source map: core **9.08 MB (52.6 %)**, Scala/Scala.js library ~3.3 MB, web 1.71 MB (9.9 %),
  typelevel (cats/circe internals) 0.96 MB, tapir 0.80 MB, laminar + airstream 0.57 MB, sttp 0.29 MB,
  circe 0.19 MB, scala-java-time 0.18 MB. The largest core contributions are codec-heavy files:
  `Merge` 568 kB, `Agents` 557 kB, `Managers` 547 kB, `AcquireDto` 429 kB, `Media` 420 kB, `Dto`
  413 kB, `Events` 330 kB. Scala.js drops unreachable code, but an `object` with endpoint `val`s
  initialises all of them, so touching one endpoint of `AgentsApi` links every endpoint of it and
  every codec they use: web references 82 endpoints and pays for most of the ~250.

### 1.4 plugin-api

Standalone as intended: 39 types, sconfig + kindlings-sconfig only, no `core` import. It duplicates by
**name** three core/server types with a different shape — `MediaItem`, `MediaRights`, `FetchedFile`
(core's `MediaRights` has a typed `MediaLicence.Licence`, plugin-api's a `String`) — and by **concept**
the values the domain also has, all as primitives: years (`CatalogueQuery.from/to: Long`,
`WorkKey.year: Int`), `lat`/`lon: Double`, `wikidata: String`, `doi: String`, `licence: String`.
The server maps them by hand in the fetcher, media and event-catalogue adapters.

## 2. Target layout

```
values      JVM+JS  Iron newtypes: Citekey, Year, Period, Doi, WikidataId, LatLon, …   iron only
domain      JVM     the domain model and its pure logic; no codecs, no SQL            values
api         JVM+JS  wire records, Tapir endpoints, circe codecs + schemas (Kindlings),
                    Markdown rendering of responses                                    values, tapir-core, circe, kindlings
config      JVM     AppConfig and its readers (sconfig)                                values, sconfig
db          JVM     Magnum repos, migrations, DbCodecs, row models ↔ domain (Chimney)  domain, config
server      JVM     handlers, routes, MCP; api ↔ domain (Chimney); plugin ↔ domain     domain, api, config, db, plugin-api
cli         JVM     decline commands over the sttp client                              api, config
web         JS      Laminar UI over the sttp client                                    api
plugin-api  JVM     the SPI; its own newtypes                                          sconfig, kindlings-sconfig, iron
```

- **Clients depend on `api` alone** (+ `config` for the CLI's local commands). A third-party client
  gets the endpoints, the wire records and their codecs, and nothing of the domain or the store.
- **`domain` knows neither the wire nor the tables.** Its types carry no `Encoder`/`Schema`/`DbCodec`;
  those live in `api` and `db` (rule 3). The server converts at its edges with Chimney.
- **`api` reuses `values`**, not domain types. Where a wire record and a domain record have the same
  shape today (`Source`, `Finding`), the record moves to `api` and `domain` gets its own only when the
  two need to differ — rule 3's "split only when there is a reason", applied per area.
- **db and server own their instances.** db keeps its `DbCodec`s (as today) and adds the ones for
  `values`; the circe/tapir instances now on domain companions (`WireEnum` encoders for
  `SourceStatus`, `EdgeKind`, …) move to `api`.
- **plugin-api stays standalone.** It gains its own newtypes for the values it exchanges (§3), and
  nothing else.

## 3. The shared-values question

Domain, API and plugin-api all speak of citekeys, years, periods, DOIs and coordinates. Either one tiny
module of newtypes is shared, or each module has its own copy and they convert through Iron's refined
types (rule 4: `Citekey(other.value)` compiles only when both constraints are the same type, and runs no
check — verified with Iron 3.4.1, including two copies whose identical constraint is written out
separately).

| cost | one shared `values` module | separate copies + refined-type conversion |
|---|---|---|
| definitions | one per value (~10–20 newtypes, 2–3 lines each) | one per value per module that has it |
| conversions | none | one `Transformer` per value per module pair (iron-chimney does not derive newtype ↔ newtype); Chimney derives the records over them |
| compile graph | one more tiny node at the root; a change to it recompiles everything, but it changes rarely | no shared node; each copy changes with its module |
| Scala.js bundle | ≈ 0: `RefinedType` is opaque, erases to `String`/`Int`; the constraint test is linked only where a value is parsed | ≈ 0, the same |
| native-image metadata | ≈ 0: opaque types leave no classes to register; the companions are plain objects | ≈ 0, the same |
| constraint drift | impossible: one definition | caught by the compiler: a conversion between copies stops compiling when one constraint changes, which forces a deliberate parse at that boundary |
| plugin binary compatibility | `values` becomes part of the SPI: every plugin depends on it, MiMa must guard it as well, and a tightened constraint changes what plugins compiled earlier may hand over without failing to link (erasure keeps the signatures `String`) | plugin-api's copies are the whole SPI surface; MiMa guards plugin-api only; a tightened domain rule shows up as a compile error in the server's adapter, where a `PartialTransformer` then validates plugin input |

Domain and API are built, versioned and released together — there is no compatibility boundary between
them, so copies would buy only transformers. The plugin SPI is the one boundary where code compiled
against an older Histmersion meets a newer one; there, a separate copy keeps the SPI's surface to
plugin-api and turns a constraint change into a compile error in exactly the adapter that must handle it.

## 4. Options

| | layout | for | against |
|---|---|---|---|
| **A** | keep `core`; split it into packages only (`core.domain`, `core.api`, `core.config`) | cheapest; package names start to say what a type is | clients still compile and link the whole of core; the CLI keeps the server's config in its binary; the bundle does not shrink |
| **B** | `api` module; `domain` = everything else of core; plugin-api unchanged | clients depend on `api` alone | values duplicated or `api` → `domain` again; domain records keep their codecs |
| **C** | §2: `values` shared by domain and api; `api`, `domain`, `config`; plugin-api with its own newtypes | every rule of *Coding practices* holds; clients, plugins and store each see only their layer | the largest change (§5); two more modules |
| **D** | as C, but plugin-api also depends on `values` | no newtype copies at all | `values` joins the SPI's compatibility surface (§3) |

**Recommendation: C**, in the order of §5. The first two steps are useful on their own and cheap (the
endpoint-linking fix is the only one that shrinks the bundle); each later step can stop at a green
master if the cost turns out higher than measured here.

## 5. Migration order

Each step compiles, passes `just test-it` and builds both native images on its own; a step that moves
classes regenerates both modules' reachability metadata with the tracing agent in the same commit
(AGENTS.md, "The tracing-agent rule"), since 557 CLI and 645 server entries name `com.histmersion.core`.

| # | step | effort |
|---|---|---|
| 1 | Add Iron and Chimney (+ iron-chimney) to the build; native-image metadata re-checked | ½ day |
| 2 | Endpoints link per use: the `*Api` objects' endpoint `val`s become `lazy val`s (or the large objects split by surface), so web links only the 82 endpoints it calls; measure `app.bundle.js` before/after | ½–1 day |
| 3 | `values` module (JVM + JS, Iron only) with `Citekey` (today an `opaque type … <: String` with a hand parser), `Year`, `Period`, `Doi`, `WikidataId`, `LatLon`; core depends on it; their codecs live in core for now, next to the wire records | 1–2 days |
| 4 | Inside core, sort the 730 types into packages `api` (wire records, endpoints, Markdown), `domain`, `config`; mechanical, metadata regenerated | 2–3 days |
| 5 | `config` module (JVM) from `core.config`; cli and db depend on it instead of core's JVM side | 1 day |
| 6 | `api` module (JVM + JS) from `core.api`; web depends on `api` only. `domain` (what is left of core) may still depend on `api` here — a recorded, temporary edge — where domain logic works on wire records | 3–4 days |
| 7 | Per area (sources, findings, places, events, articles, goals, queue, merge, media, agents, managers, acquire — ~12), remove the domain → api edge: a domain record where it must differ, Chimney transformers in server, the circe/tapir instances out of domain companions. cli then depends on `api` + `config` only | ~1 day per area, 10–12 days |
| 8 | plugin-api newtypes for the values it exchanges (years, coordinates, DOI, Wikidata id, licence), refined-type transformers in the server's adapters; one SPI version bump, announced to plugin authors | 2 days |
| 9 | MiMa (`sbt-mima-plugin`) on plugin-api against the last released version, at the first release that promises SPI compatibility | ½ day |

Total for C: about 21–27 working days, of which step 7 is the bulk and can be spread over the areas'
ordinary changes (*Coding practices*: existing code is brought in line when it is changed).

## Step 2 as built (#4036, 2026-10-24)

Every endpoint, endpoint input, error mapping and `all` list in the eight `*Api` objects is a `lazy val`
(the string and list constants stay strict). A `lazy val` is an accessor method that Scala.js's optimiser
drops when nothing calls it, so the web bundle links the endpoints the page calls and the codecs those
name, and nothing else. `def`s would have worked for the linker too, but would build a new endpoint value
on every call on the server and in the CLI; per-endpoint objects would have changed every call site.
`ApiSurfaceSuite` fails when an endpoint is held in a strict field again. The endpoint definitions are
unchanged: `showDetail`, info, inputs, outputs and errors of every endpoint in `Api.all`, and the REST
index, are byte-identical before and after.

| | before | after | change |
|---|---|---|---|
| `app.bundle.js` raw | 20 843 533 B | 15 702 058 B | −24.7 % |
| gzip −6 (what the server sends) | 2 174 865 B | 1 772 038 B | −18.5 % |
| core's share (source map) | 8.50 MB | 5.47 MB | −3.03 MB |
| first render: map · first markers · list (ms) | 528 · 601 · 604 | 423 · 495 · 498 | −105 ms |
| first render: longest task · all long tasks (ms) | 150 · 212 | 105 · 168 | |
| JS heap after first render | 53.5 MB | 44.7 MB | −8.8 MB |

First render: headless Chromium 1248 on the GPU (Metal), a throwaway JVM server with the perf fixtures
(`perf-fixtures.mjs`), the two bundles served alternately through `HISTMERSION_PERF_BUNDLE`, median of 9
runs after one warm-up each. What core still contributes comes from areas the web UI does call — merge
(465 kB), managers (547 kB), agents (480 kB), events, places, goals — and shrinks further only when those
views stop needing the records, not by any linking change.

## Steps 3–6 as built (2026-10-24)

Done in the order 3, 4, 6, 5: a `config` module needs `api` (it reads into wire records, below) while the
domain needs `config`, so with `api` still inside `core` the two would have formed a cycle.

```
values   JVM+JS   Citekey, Year, Period, Doi, WikidataId, LatLon            iron
api      JVM+JS   com.histmersion.api: wire records, endpoints, codecs,      values, tapir-core, circe, kindlings
                  Markdown, the helpers they need (98 files, 22.8k lines)
config   JVM      com.histmersion.config: AppConfig, readers,                values, api (temporary)
                  reference.conf (5 files, 1.5k lines)
core     JVM      com.histmersion.domain (55 files, 13.1k lines)             values, api, config (temporary)
db       JVM                                                                 config, core
cli      JVM                                                                 config, core
web      JS                                                                  api
```

**Step 3 — `values`.** Six files, Iron only, cross-built. `Citekey` moved here from core: an Iron
`RefinedType` whose constraint is the shape and the no-`..` rule, where it was an `opaque type … <: String`
with a hand parser; `parse` keeps the messages a person sees, `valid` classifies, and `Citekey.of` (the
`ApiError` form) stays beside `ApiError` as an extension. A `Citekey` is no longer silently a `String`: the
few places that hand one on take `.value`. `Doi` replaced `BrowserDownload`'s own. `Year` (over `Int`, as
*Coding practices* 1 has it), `Period` (private constructor, `from`/`point`, `PeriodError`), `WikidataId`
and `LatLon` (`Latitude`/`Longitude`) are defined and tested but carried by no record yet: the domain and
wire records still hold `Long` years, `Double` coordinates and `String` ids. Adopting them is per area,
with step 7. Their circe/tapir instances (`ValueCodecs`) are in api.

**Step 4 — the packages.** Every file of `com.histmersion.core` went to one of three packages — named
`com.histmersion.{api,config,domain}`, not `core.api`…, so the module steps moved files without renaming
a single class again. The split was the closure of what the wire needs: every shared file with a codec,
schema, endpoint or Markdown renderer, every file the web page uses, and everything those reference. Of
the 105 shared files only 9 stayed out of it (`AcquireMatch`, `AcquireParse`, `AutoEnrich`, `Chunker`,
`GoalsExport`, `Highlight`, `Jats`, `PointAreas`, `Version`). The imports enforced the layering already
inside one module (api files import nothing of config or domain, config files nothing of domain, web
files api only), so the compiler found every edge the module split would break:

- **config → domain** was only conversions on config records: `LandingConfig.rules`,
  `AcquireConfig.agent`, the media configs' `allowed`, the event providers' `datasetList`/`classList`,
  `AgentPlanConfig.caps`, `AgentInferFilterConfig.limits`. They are extensions in the domain now, next
  to the types they build, so no call site changed. The scheduler's default weights and priorities moved
  to `AgentSchedulerConfig`.
- **Package-level givens** reached every file of the one package: api's `Configuration` (Kindlings
  decodes defaults) and config's lenient readers. Wildcard imports do not carry givens, so the domain
  and config files import them (`import com.histmersion.api.given`); no codec or reader changed.
- **Name precedence**: a name a nested wildcard import gave (`ReviewArticle.Finding`) used to beat a
  member of the file's own package and now met a package wildcard (`api.Finding`); 7 files import it
  explicitly.

**Step 6 — `api`.** The package moved out whole: 97 shared files, `ApiIndex` on the JVM side. web
depends on `apiJS` alone; `core` became JVM only (`crossProject(JVMPlatform)`, its `shared/` directory
kept for the nine shared domain files). Of the api suites, 761 JVM and 17 Scala.js run in api; 17 that
need config or domain fixtures stay in core in package `api`, and core's tests see api's (`test->test`)
for the shared fixtures and resources.

**Step 5 — `config`.** `AppConfig` and its records, the readers, the user file, the reload plan and
`reference.conf`; 33 suites. cli and db depend on it directly.

**Edges that remain (recorded, temporary).** domain → api (the domain works on wire records, as this
note foresaw); domain → config (the domain takes config records as its settings: `ManagerPolicy` takes
`AgentManagersConfig`, the media and event code their provider configs); config → api (config reads into
four wire types: the page's `UiConfig` with its parts, the approval kinds and modes,
`PlanQuota.ScheduledRefresh`, `SeaFloorSource`). cli and db still depend on core: the CLI runs domain
code for its local commands (the plugin kit, bundle files, frontmatter, merge resolution files), db works
on the domain records. Step 7 removes them per area, with a config-side record where config and wire
must differ (`UiConfig`).

**Plugin authors.** `plugin-api` did not change in these steps: no file under `modules/plugin-api`, and
no dependency of it. The plugin testkit's contract changed in one line: `PluginHarness`'s (private)
constructor names `com.histmersion.config.AppConfig`. The install's Maven repository ships
`histmersion-values`, `-api` and `-config` beside `-core`, which the testkit's POM now reaches.

**Measured.**

| | before (step 4: one core module) | after |
|---|---|---|
| `app.bundle.js` raw · gzip −6 | 15 848 224 B · 1 789 378 B | 15 848 224 B · 1 789 378 B |
| cold compile: core JVM · core JS | 48 s · 43 s | api JVM 37 s · api JS 40 s · config 4 s · core (domain) 9 s |
| CLI metadata entries naming Histmersion's core types | 569 `core` | 477 `api`, 91 `config`, 2 `domain` |
| server metadata entries naming them | 681 `core` (11 of them stale) | 514 `api`, 91 `config`, 66 `domain` |

Cold compiles: an empty sbt cache, one command per module in dependency order, on the same machine
minutes apart. The bundle is byte for byte the same: Scala.js linked only what the page reaches already,
so moving the domain out of its class path dropped nothing it linked. The CLI's binary still carries the
config readers (it loads `AppConfig` for its local commands); the domain is nearly gone from it. Before
step 4 the metadata's names were rewritten mechanically (every type keeps every entry earlier agent runs
recorded; 11 entries of map-style types deleted long ago were dropped) and a tracing-agent run of the JVM
server and CLI added only what this branch's new code reached; after steps 5 and 6 a second run left
both files unchanged.

## Step 7, batch 1 as built (#3967, 2026-10-25)

The config → api edge, then three areas: media, events, places — one commit each, each compiling and
passing the JVM suites of the modules it touched.

```
values   JVM+JS   Citekey, Year, Period, Doi, WikidataId, LatLon            iron
api      JVM+JS   com.histmersion.api (96 files, 22.1k lines)                values, tapir-core, circe, kindlings
config   JVM      com.histmersion.config (6 files)                           values
core     JVM      com.histmersion.domain (62 files)                          values, api, config (temporary)
server   JVM      + Chimney (layer conversions at its edges)
```

**config → api, removed.** `config` depends on `values` alone; the server converts what it shows.

- The `ui` block is read into config-side records (`UiSettings`, `UiTimeSettings`, `UiDebounceSettings`,
  `UiEventsSettings`), which leave out the four fields the server fills in (`writeKeyRequired`,
  `agentsEnabled`, `plugins`, `mapLanguage`). `server/ConfigViews` derives the wire `UiConfig` from them with
  Chimney, and the `/api/about` config-file line (`ConfigFileInfo`) from `UserConfigFile`. This is where the
  two must differ; the wire records keep their names, so the OpenAPI and MCP schemas did not change.
- `SeaFloorSource`, `ScheduledRefresh` (with `RefreshIntervalMinutes`) and the reload graph (`HotPlan`,
  `HotNode`) were never on the wire: they moved to config. The scheduled-read decision they fed moved to
  the domain (`PlanReadSchedule`).
- What config computed into api types — approval modes and checked kinds, provider blocks, roles, plan
  logins, media roles, figure rules, an event box, tolerance and match weights — are domain extensions on
  the config records, as step 4 did for config → domain. One was renamed: the illustrations' `rules` is
  `figureRules`, because top-level extensions sharing a name must sit in one file.
- Suites that tested api code from config (`Usage.budgeted`, `ConfigFileInfo.describe`, `ReloadMarkdown`)
  moved to api; config's suites need no api fixture, so config dropped its `test->test` on api.
- Chimney (`io.scalaland::chimney` 2.1.0) is a server dependency now (THIRD-PARTY.md: Apache-2.0). The
  server needs no `chimney-iron` yet: domain and api share `values`' newtypes, so no newtype → newtype
  conversion arises; the native-image tasks still read `Compile / fullClasspath`.

**The areas.** The rule held throughout: a record is split only where domain and wire must differ, and
none of the three needed it. What did move is domain logic that sat in api because step 4's closure
took whole files — objects with no codec, no endpoint and no use in the web page:

| area | moved api → domain | values adopted | Chimney in server | edge left (what the domain still takes from api) |
|---|---|---|---|---|
| media | `FigurePick`, `MediaRoles` | `Period`: a collection record's dates and the place it is matched to (`MediaRecord.period`, `MediaMatch.PlaceInfo.period`) | stored figure row → `FigurePick.Figure` | `MediaRole` (the role vocabulary the wire and the store speak), `Geocode.key` (the place-name key, the twin of the SQL function both sides compare by) |
| events | `CatalogProvider`, `CatalogDating`, `StoredEvent`, `CatalogMerge` (`EventCatalogue.scala`), `EventMatcher` | none — `StoredEvent`'s coordinates and Wikidata id are read back from stored rows, and checking them on read (`LatLon`, `WikidataId`) would change which stored events a merge matches | none needed | `CatalogRecord` (a wire record db also stores as JSON), `TimeSpan` and `Years` (the four-point span and its year arithmetic), `EventTaxonomy`, `Historicity`, `BBox`, the wire records the matcher returns (`EventLinkCandidate`, `AttestationPut`), `Plural` |
| places | `PlaceReview` (the place-kind lint) | `Period`: a linked item's dates and a reclassified polity's period; db writes it with `PeriodSql.literalOf` | none needed | `PlaceKind` (the kind vocabulary), `YearFmt`, `Geocode.key` |

`DeepTime` (domain) turns the store's `Long` years into `Year` and `Period`: two years in either order make a
period, a year outside `Int` makes none. Every period these areas built was already ordered, and no real
year is outside `Int`, so no record and no match changed.

**Measured.** Domain files importing api: 55 of 55 before the batch, 59 of 62 after (the three new ones
take nothing from it); by wildcard 55 → 51 — the moved and narrowed files name what they take. api: 98 →
96 files, 22 753 → 22 099 lines; config: 10 imports of api → 0. Compiling core with every api import
removed lists what the domain still takes, by name: 44 files and 193 names before the areas (after the
config commit), 47 files and 196 names after. The count did not fall, and is not meant to by moves of this
kind: the logic that left api still speaks api's records, so it now shows on the domain's side of the
edge — what shrank is api, which no longer carries domain logic the wire never uses. Native-image
metadata: no entry named a moved class (none is reached by reflection); a tracing-agent run of the server
and the CLI on an empty test database added the config-side `ui` records' companions (their derived
readers are lazy vals), new lazy reader fields of the config companions whose files changed,
`ConfigViews` and the two Chimney DSL classes its transformers build — server 1 151 → 1 158 reflection
entries, CLI 651 → 655.
`app.bundle.js`: 15 848 224 → 15 788 593 B raw, 1 789 378 → 1 785 110 B gzip −6 (not attributed by source
map; the moved objects are the only api code that left). The bundle built, `smoke.sh` and
`--self-check` pass.

**What remains, and what is next.** The vocabularies both sides speak (`EdgeKind`, `SourceStatus`,
`GoalStatus`, `PlaceKind`, `MediaRole`, `Historicity`, `LocationPrecision`, …) are where most of the
remaining edge sits; each carries its `WireEnum` codecs on its companion in api. The target puts them in
`values`, with the instances moving to api as orphan givens, so the next batch has to decide whether a
vocabulary that is not a newtype belongs in `values`. The shared text helpers (`Plural`, `LatinFold`,
`YearFmt`/`Years`, `Geocode.key`) are the other recurring edge. Of the movable api definitions left,
most are used by the CLI (`Bundle`, `SourceDelete`, `Rename`, `PlaceEdits`); moving them to the domain
would tie the CLI to core harder, against step 7's aim of a CLI on `api` + `config`, so they wait for
their areas (merge, sources).

**Filter.** Not decided: no area of this batch moved `Filter`. Events still take `BBox` from api's
`Filter.scala`, a value that could equally live in `values`.

**Plugin authors.** `plugin-api` did not change. The testkit contract (`plugin-testkit-api.txt`) did not
change.

## Step 7, batch 2 as built (#3967, 2026-10-25)

The shared vocabularies first, as decided (Unresolved → resolved), then the areas whose coupling was
smallest: goals, and the build identity. One commit each, every one compiling and passing the JVM suites
of the modules it touched; `just test-it` passed once at the end.

```
values   JVM+JS   newtypes + the vocabularies domain, wire and store share     iron
                  (18 files, 0.9k lines; no codec)
api      JVM+JS   com.histmersion.api (99 files, 21.6k lines)                  values, tapir-core, circe, kindlings
config   JVM      com.histmersion.config                                       values
core     JVM      com.histmersion.domain (60 files)                            values, api, config (temporary)
```

**The vocabularies, in `values`.** Two commits.

- *Top-level ones:* `SourceStatus`, `Confidence`, `EdgeKind`, `GoalStatus`, `PlaceKind`, `LocationPrecision`,
  `MediaRole`, `Historicity`, `EventEdgeKind`, `EventLinkStatus`, `BundleKind` and the merge vocabularies
  (`IssueSeverity`, `IssueKind`, `DecisionKind`, `MatchOutcome`, `EditionsPolicy`, `DecidedBy`,
  `MatchMethod`), with the spelling table they all share (`WireEnumValues`: every value, its spelling, the
  value a spelling names). Their logic came with them (`PlaceKind.parse`, `LocationPrecision.of`,
  `MediaRole.order`, `BundleKind.file`), all of it pure.
- *Nested ones,* which api declared inside its objects: `Agents`' `ProviderKind`, `Role`, `RunState`,
  `Pipeline`, `TargetKind`, `ApprovalKind`, `ApprovalState`, `ApprovalMode`; `Managers`' `Kind`, `State`,
  `ItemStatus`; `GoalPlans`' `DraftMode`, `LinkKind`, `ScheduleKind`; `ListFilters`' `DecidedBy`. In
  `values` they are top-level, so the generic names got a qualifier: `AgentRole`, `ManagerKind`,
  `ManagerState`, `GoalLinkKind`, `ApprovalDecidedBy`. Each api object `export`s them under its old names
  (`export com.histmersion.values.{AgentRole as Role, …}`), so the wire records, the endpoints, the web
  page, the CLI and the server read exactly as before; the domain names the `values` types.
- *Instances:* the circe encoders/decoders and tapir schemas are api's `VocabularyCodecs`, each built from
  the spelling table by `WireEnum` as before, and exported to the api package, where every file finds them
  as it found them on the companions. db's column codecs stayed in db.
- *A rule this exposed:* outside api, a file that derives a codec or schema must
  `import com.histmersion.api.VocabularyCodecs.given`. Without it Kindlings derives an instance for a
  vocabulary field by itself, and nothing fails to compile: a checkpointed compile run's `Snap` wrote
  `"mode":{"Create":{}}` instead of `"mode":"create"`. Every derivation file of server, cli and web imports
  it now (the domain's files had it already, through `import com.histmersion.api.given`), and
  `VocabularyWireSuite` holds a checkpointed record to the wire spelling. The scaladoc of `VocabularyCodecs`
  states the rule.
- *Scala.js:* `values` is cross-built and takes no locale library, so `toLowerCase(Locale.ROOT)` became
  `Ascii.lower` (A–Z only), which is what `Locale.ROOT` does to an enum's name.
- *Unchanged wire, proved:* a scratch suite dumped, for every endpoint of `Api.all`, its `showDetail`, the
  JSON Schema (sttp-apispec, as the MCP layer builds them) and the tapir `Schema.show` (validators
  included, so an enumeration's values) of every body, query, path and header input, every output and
  error output; then the REST index and the MCP `tools/list` of a server on a test database — 2 674 117
  bytes, byte-identical before the batch, after the first vocabulary commit and after the second. There is
  no OpenAPI document in the build (no tapir-openapi-docs); this dump covers what one would hold.
  `VocabularySuite` (values, JVM and JS) round-trips every enum's spellings and pins the ones the store
  and clients rely on.

**The areas.**

| area | moved | to | why there | edge left |
|---|---|---|---|---|
| goals | `GoalsExport` (GOALS.md) and its suite | api | a Markdown projection of api's goal records, written by the server's export and the CLI's meta export — §2 puts Markdown rendering of responses with the wire records | `GoalFocus`, `PlanGoal`, `WatchSchedules` work on wire records (`PlanGoalItem`, `GoalSchedule`, `Plan`, `PlannedWork`, `ManagerConfig`…) |
| build identity | `Version` | api | what `/api/version` (`VersionInfo`) and `/api/about` report; server, CLI and plugin-host read it | — |

The other areas were not started. What the domain still takes from api is now records, not vocabularies:
merge (`MergePlanner` 41 names, `BundleEntities` 27, `MergeFold` 14 — the bundle and merge records),
managers (`ManagerPolicy` 19: `ManagerConfig`, `ItemState`, `Hold`, `RunMark`, `HintEffect`, …), agents
(`CompareRuns` 13, the run records), articles (`ReviewArticle` 13, `CompileArticle` 6: coverage and
critique records), sources (`EnrichSource` 10: the patch records; `Frontmatter`, `Export`: `Source`,
`Finding`), acquire (`AcquireParse` 7: `WorkRef`, `ArchiveItem`, …). Each needs a domain record and a
Chimney transformer in the server — §2's "split only when there is a reason" applied per record, about a
day per area as §5 estimated — or the record stays a recorded edge. The shared text helpers (`Plural` in
11 domain files, `LatinFold`, `YearFmt`/`Years`, `Geocode.key`) remain the other recurring edge; they are
pure and cross-built, and would fit `values` only by widening what `values` is for, so they were left.

**Measured.** Domain → api the way batch 1 counted it (files importing api; compiling core with every api
import removed: files with errors / distinct names not found — this counter reads 210 names where batch 1's
read 196 for the same tree), and the CLI → domain edge the same way (cli compiled with every domain import
removed; names counted as types, without the 4–5 lower-case names that are knock-on errors):

| after | domain files · importing api · by wildcard | api imports removed: files / names | cli files importing domain | domain imports removed: files / types |
|---|---|---|---|---|
| batch 1 (`7d4d6e72`) | 62 · 59 · 51 | 47 / 210 | 29 of 38 | 9 / 13 |
| top-level vocabularies (`ea4dd571`) | 62 · 57 · 51 | 45 / 195 | 29 of 38 | 9 / 13 |
| nested vocabularies (`f0e9e19d`) | 62 · 57 · 51 | 45 / 180 | 29 of 38 | 9 / 13 |
| goals (`a148142d`) | 61 · 56 · 50 | 44 / 176 | 29 of 38 | 8 / 12 |
| Version (`929b5972`) | 60 · 55 · 49 | 44 / 176 | 29 of 38 | 7 / 11 |

(The CLI's first row is inferred: the vocabulary commits moved nothing out of the domain, so the names
it takes from the domain could not change; measured from the second row on.)

**How far the CLI is from `api` + `config`.** It takes eleven domain types, all local-file work:
`BundleTar` (and through it `BundleFormat`), `MergeResolutionsFile`, `ArticleNote`, `PluginKit` (with its
`Coordinates`, `Index`, `Installed`, `Origin`), `DirSize`, `PlaceSeed` and `CoverChoice`. None of them uses
config (the imports are step 4's blanket ones), and none is domain logic in §2's sense: they read and write
the files a client and the server exchange — the `.hmx` bundle, the resolution file, the Markdown mirror,
the plugin set — over api's records. They are what still ties cli to core, and moving more of their kind
into the domain would tie it harder. The proposal:

- the file formats (`BundleFormat`, `BundleTar`, `BundleReader`, `MergeResolutionsFile`, `ArticleNote`,
  `PluginKit`; `Frontmatter` and `Export`, the server's and db's twins of `ArticleNote`, can follow) go to
  api's JVM side (`modules/api/jvm`, where `ApiIndex` is), which then depends on snakeyaml — already in the
  CLI binary — or, if api should stay endpoints and codecs, to a small JVM module of client files (`api` +
  snakeyaml) that cli and server both take;
- `CoverChoice` and the Open Library id helpers it calls go with them (the CLI is its only user);
- `DirSize` (the server's storage report uses it too) and `PlaceSeed` (the server's init too) are plain JDK
  file helpers and go with the file formats.

With that, cli depends on `api` + `config`, and its 29 domain imports become api ones. `Bundle`,
`SourceDelete`, `Rename` and `PlaceEdits` stay in api, where they are.

**Filter.** Not decided: no area of this batch moved it.

**Native-image metadata.** A tracing-agent run of the server and the CLI on an empty test database (scratch
HOME and config, a non-default port; REST GETs of every endpoint, MCP tools, source and goal writes, the
CLI's reads and exports) recorded the lazy given fields of `VocabularyCodecs`; the entries that named the
same fields on the former api companions were dropped with the classes: server 1 158 → 1 143 reflection
entries, CLI 655 → 640. `app.bundle.js`: 15 788 593 → 15 715 799 B raw, 1 785 110 → 1 773 435 B gzip −6
(not attributed). The bundle built, `smoke.sh` and `--self-check` pass.

**Plugin authors.** `plugin-api` did not change, nor did the testkit contract (`plugin-testkit-api.txt`).
A JVM client compiled against api sees the moved vocabularies in package `com.histmersion.values` (top-level
ones: `com.histmersion.api.SourceStatus` is now `com.histmersion.values.SourceStatus`); the nested ones keep
their api names through the exports. The JSON is the same.

## Step 7, batch 3 as built (#3967, 2026-10-25)

The CLI onto `api` + `config` first, as decided, then three areas: agents, sources, acquire — one commit
each, every one compiling and passing the JVM suites of the modules it touched; `just test-it` passed once
at the end.

```
values   JVM+JS   newtypes + the shared vocabularies (StepStatus joined them)          iron
api      JVM+JS   com.histmersion.api (99 files, 21.6k lines)                          values, tapir-core, circe, kindlings
config   JVM      com.histmersion.config                                               values
files    JVM      com.histmersion.files: the local files (11 files, 1.2k lines)        api, snakeyaml
core     JVM      com.histmersion.domain (49 files)                                    values, api, config (temporary), files
cli      JVM                                                                           api, config, files
```

**The CLI off core: a `files` module.** The eleven domain types the CLI still took read and write the files
a client and the server exchange or keep, over api's records — the `.hmx` bundle's container and canonical
JSON (`BundleTar`, `BundleFormat`), the merge resolution file (`MergeResolutionsFile`), the article note
(`ArticleNote`), the plugin set and relink kit (`PluginKit` with `Coordinates`, `Index`, `Installed`,
`Origin`), a directory's size (`DirSize`), the gazetteer seed's file names (`PlaceSeed`) and a book cover's
source (`CoverChoice` with the `OpenLibrary` id helpers, split out of `MediaProviders`). They went to a new
JVM module, `files` (package `com.histmersion.files`, api + snakeyaml), not into api, which stays endpoints,
wire records and codecs. core and cli both take it; cli depends on `api`, `config` and `files`, and its 29
domain imports are gone (0 of 38 files import the domain; compiled with no domain on its class path, it
finds every name). `BundleReader` stayed in the domain: it decodes `BundleEntities`, the merge area's
records. `BundleFormatSuite` kept the container and canonical-line tests; its entity, reader and decision
tests became core's `BundleEntitiesSuite`. `scripts/dev/affected.py` knows the module (a change in core no
longer re-tests cli).

| CLI binary (`dist/histmersion/bin/histmersion`) | before (`d7202475`) | after |
|---|---|---|
| size | 74 041 360 B | 73 826 608 B (−214 752 B) |
| reflection entries · naming `api`/`config`/`domain`/`files` | 640 · 462/95/2/0 | 640 · 462/95/0/2 |

The CLI's two domain entries (`PluginKit$Index$`, `PluginKit$KitManifest$`) were renamed to the new package;
a tracing-agent run then added and dropped nothing.

**The areas.**

| area | what moved | to | edge left |
|---|---|---|---|
| agents | `CompareRuns` got its own records: the recorded run (`Run`, `RunHead`, `Step`, `Tokens`, `ModelChoice`) and the comparison (`Comparison`, `RunSide`, `StepSide`, `StepRow`, `EnrichSide`, `ReviewSide`, `ReviewerSide`, `KindCount`, `MajorOverlap`); `StepStatus` to values (exported from `Agents` as before) | domain; server `agents/CompareRunsViews` converts `RunDetail` → `Run` and `Comparison` → `RunCompare.Comparison` with Chimney (`CompareRunsViewsSuite`: the conversion, the wire comparison, its Markdown) | none in `CompareRuns` itself; it still reads the checkpoints of `EnrichSource` and `ReviewArticle`, whose records hold api types (`FindingInput`, `Period`) |
| sources | `Frontmatter`, `Export`, `SafePath` — the source note of the Markdown mirror, the source-side twins of `ArticleNote` | files (core no longer takes snakeyaml) | the enrichment patch records: `EnrichSource` (10 names: `SourcePatch`, `FindingInput`, `MetadataPatch`, …), `AgentProposals` (4) |
| acquire | `AcquireParse` (the upstream JSON parsers) with `OaAccess`, `EuropePmcHit`, `PmcRecord`, `PmcObject` and its two suites — the pure half of the acquisition client, which `WorkLookup` already called | plugin-host (its tests take core's test class path for the captured fixtures) | `WorkRef` in `BrowserDownload`, `Scheduling`, `PlanGoal`, `EnrichSource` |

Where the domain needed a record of its own (agents) the split followed rule 3: the domain's records hold
only what the comparison reads (a step's sequence number, kind and prompt hash, and a run's other
parameters are left out), and the wire derives a field the domain is given (`modelFor`, from the run's
`params`). They keep the wire's plain `String`/`Long` fields: adopting newtypes there is the same per-area
step batch 1 left for later. The other two areas needed no new record — what sat in the domain was file
format and acquisition-client code, which went to the modules that hold those.

Not started: merge (`MergePlanner` 40 names, `BundleEntities` 27, `MergeFold` 14), managers
(`ManagerPolicy` 15, `WatchSchedules` 5, `HintProposals` 4), articles (`ReviewArticle` 13, `PlanArticles` 9,
`CompileArticle` 5, `FilterInference` 5 — and they lean on api's pure article helpers, `ArticleCoverage`,
`Articles.appendUnderSection`, which the web page uses too, so a domain copy would duplicate them), findings
and queue (no domain file of their own; `PassageFinder` 4, `AcquireMatch` 2). Each is a set of records
with a Chimney transformer in the server, as agents was.

**Measured.** Domain → api the way batch 2 counted it (files importing api; compiling core with every api
import removed: files with errors / distinct names not found), and the CLI → domain edge the same way:

| after | domain files · importing api · by wildcard | api imports removed: files / names | cli files importing domain | domain imports removed: files / names |
|---|---|---|---|---|
| batch 2 (`d7202475`) | 60 · 55 · 49 | 44 / 176 | 29 of 38 | 7 / 15 |
| files module (`e347d8f6`) | 53 · 49 · 43 | 39 / 166 | 0 of 38 | 0 / 0 |
| agents (`24d56253`) | 53 · 48 · 42 | 39 / 154 | 0 of 38 | 0 / 0 |
| sources (`1208d19a`) | 50 · 45 · 39 | 37 / 150 | 0 of 38 | 0 / 0 |
| acquire (`86aa37eb`) | 49 · 44 · 38 | 36 / 146 | 0 of 38 | 0 / 0 |

(The CLI's "names" now count every not-found name, lower-case knock-on errors included; batch 2's 11 types
are the same measurement read as types only. `CompareRuns` still shows among the files with errors when api
is removed: its fields typed through `EnrichSource`'s records fail with them.)

**Unchanged wire, proved.** Batch 2's scratch suite (every endpoint's `showDetail`, JSON Schema and tapir
`Schema.show` of every input and output, the REST index, the MCP `tools/list` of a server on a test
database) dumped 2 674 117 bytes before the batch and after each of the four commits, byte-identical every
time. `test-it`: 4 045 tests, none failed.

**Filter.** Not decided: no area of this batch moved or needed it.

**Native-image metadata.** A tracing-agent run of the JVM server and CLI on an empty test database (scratch
HOME, data, cache and config, a non-default port; REST GETs of every endpoint, the page and its assets, MCP
tools, `agent compare`, the CLI's reads, the Markdown export, a bundle export, verify and merge template)
changed neither file: no moved class is reached by reflection except the two renamed `PluginKit` companions.
Server 1 143 reflection entries before and after. `app.bundle.js`: 15 874 707 → 15 816 520 B raw,
1 789 105 → 1 784 337 B gzip −6 (`web/fullLinkJS` of `d7202475` and of this branch; the page calls no moved
code, so the difference is not attributed). The bundle built, `smoke.sh` and `--self-check` pass.

**Plugin authors and API clients.** `plugin-api` did not change, nor did the testkit contract
(`plugin-testkit-api.txt`). The install's Maven repository ships `histmersion-files` beside `-core`, which the
testkit's POM now reaches through plugin-host → core. `com.histmersion.domain.AcquireParse` is now
`com.histmersion.pluginhost.AcquireParse` (in `histmersion-plugin-host`, which the testkit depends on); the
moved file types are in `com.histmersion.files`. A JVM client compiled against api sees `StepStatus` in
`com.histmersion.values` (still `Agents.StepStatus` through the export). The JSON is the same.

## Step 7, batch 4 as built (#3967, 2026-10-25)

Two areas: merge, then managers with the domain → config edge they carry. One commit each, each
compiling and passing the JVM suites of the modules it touched; `just test-it` passed once at the end.
The third area offered (the agent checkpoints, `EnrichSource`'s and `AgentProposals`' patch records,
`WorkRef`) was not started; why is under *What is left for batch 5*.

```
values   JVM+JS   newtypes + the shared vocabularies                                   iron
api      JVM+JS   com.histmersion.api (100 files, 21.6k lines; + MergeIssueDetail)     values, tapir-core, circe, kindlings
config   JVM      com.histmersion.config                                               values
files    JVM      com.histmersion.files (13 files, 1.5k lines; + BundleEntities,       api, snakeyaml
                  BundleReader)
core     JVM      com.histmersion.domain (48 files; + ManagerDefaults)                 values, api, config (temporary), files
```

**Merge.** The merge records are of two kinds, and neither needed a domain twin:

- *The bundle's lines* (`BundleSource`, `BundleFinding`, … — the `.hmx` file's NDJSON) are what the planner
  compares, and it compares them as lines: a row's rev is the hash of its canonical line, and localizing a
  bundle rewrites its lines as JSON (uids and labels mapped field by field). A domain copy would be turned
  back into its line for every rev and every rewrite, so rule 3 gives no reason to split them; they are a
  recorded edge, the file format the domain merges.
- *The plan* (`MergePlan`, `MergeIssue` and its `Issue*` parts, `MatchCounts`, `MergeTally`, `MergeDecision`,
  `MergeResolutions`) is what `merge plan` returns, the resolution file holds and `merge_decisions` stores as
  JSON. The planner builds it in its final shape; a domain copy would be a field-for-field twin. Also a
  recorded edge.

What did move:

| moved | to | why there |
|---|---|---|
| `BundleEntities` (the bundle's content in memory: NDJSON payloads, decoding, revs, references, blob uses) and `BundleReader` (reading and verifying a `.hmx` stream) | files | bundle file-format code beside `BundleFormat` and `BundleTar`; batch 3 had left `BundleReader` in the domain only because it decodes `BundleEntities`. Its entity and reader tests went with it (`files/BundleEntitiesSuite`); the localize, fold and import-decision tests stayed in core (`BundleMergeSuite`) |
| the issue-detail helpers: a source described as an issue side, a pair of sources/events/places as fields, spans and places, a goal plan as one line (`describe`, `side`, `rangeText`, `preview`, `clip`, `span`, `eventSpan`, `sourceFields`, `eventDetail`, `placeDetail`, `planText`) | api `MergeIssueDetail` (with `MergeIssueDetailSuite`, JVM and JS) | wire records to wire records, what the review screen shows; the three-way article text stays in the planner, which owns `Diff3` |

Edge left: `MergePlanner` 38 names (the lines and the plan, `ArticleLinks.retarget`, `AssetLinks.safeName`,
`Plural`), `MergeFold` 14 (the lines, `Lint.normaliseTag`, `AssetLinks`, `MergeDecision`), `SourceMatcher` 1,
`BundlePlan` 1; domain → config: `MergeConfig` and `MergeWeights` (`MergePlanner`, `SourceMatcher`), read by
db's `MergeState` too.

**Managers, and the config decision.** `ManagerPolicy` took `AgentManagersConfig` and `AgentSeedConfig`,
config records that also hold what only the supervisor reads (the tick, event retention, crons, scan
intervals, the drain size). Decided: the domain takes a settings record of its own, holding exactly what the
policy falls back on where a manager's saved config sets nothing — `ManagerDefaults` (with
`ReviewManagerDefaults`, `ManagerRunDefaults`, `WatchManagerDefaults`) and `SeedPlanDefaults`. The server
converts at its edge with Chimney (`server/agents/ManagerDefaultsViews`, `ManagerDefaultsViewsSuite` field by
field): each call site converts the config as it reads it, because the config may be reloaded. All six call
sites were in server, so no db signature changed. `ManagerPolicy` no longer imports config.

The same move fits the other domain → config edges — a domain settings record per policy, converted in the
server — but each is its own area's change: `MergeConfig` is read by db as well, so it would cross db's
signatures too.

`ManagerPolicy`'s 15 api names stay a recorded edge: `ManagerConfig` (a manager's saved config), `ItemState`,
`Hold`, `RunMark`, `HintEffect` (the item state the store keeps as JSON), the item kinds and `Sweep`, and
`ManagersMarkdown.usd`, `Plural` — one shape on both sides.

**Measured.** Domain → api the way batch 3 counted it; domain → config the same way (files importing config;
compiling core with every config import removed: files with errors / distinct names not found):

| after | domain files · importing api · by wildcard | api imports removed: files / names | domain files importing config | config imports removed: files / names |
|---|---|---|---|---|
| batch 3 (`0d6a1c38`) | 49 · 44 · 38 | 36 / 146 | 37 | 13 / 26 |
| merge (`a1131262`) | 47 · 42 · 36 | 34 / 128 | 35 | 13 / 26 |
| managers (`5eaaffbf`) | 48 · 42 · 36 | 34 / 128 | 34 | 12 / 22 |

Of the 37 domain files importing config, 13 used it (the rest are step 4's blanket imports); 12 do now:
`MediaProviders` (6 media configs), `EventProviders` (4 events configs), `AgentsSettings` (3), `SourceMatcher`
and `MergePlanner` (`MergeConfig`, `MergeWeights`), `SchedulerPolicy`, `RepositoryLanding`, `PlanReadSchedule`,
`PlanGoal`, `HttpPolicy`, `FilterInference`, `EnrichSource` (one each).

**Unchanged wire, proved.** Batch 3's scratch suite (every endpoint's `showDetail`, JSON Schema and tapir
`Schema.show` of every input and output, the REST index, the MCP `tools/list` of a server on a test database)
dumped 2 674 117 bytes before the batch and after each commit, byte-identical every time, and identical to
batch 3's last dump. No store record or checkpoint changed shape. `test-it`: 4 069 tests, none failed.

**Native-image metadata.** No entry named a moved class. A tracing-agent run of the JVM server and CLI on an
empty test database (scratch HOME, data, cache and config, a non-default port; batch 3's run — REST GETs of
every endpoint, the page and its assets, MCP tools, the CLI's reads, the Markdown export, a bundle export,
verify and merge plan — plus a manager created and listed, which computes its effective defaults through
`ManagerDefaultsViews`) changed neither file: server 1 143 reflection entries, CLI 640, as before.
The bundle built (one `just bundle`), `smoke.sh` passed with its plugin template check (every kind of
`plugin new` built, tested and published with scala-cli) and the relink, and `--self-check` passed. The CLI
binary is 74 057 952 B (batch 3 measured 73 826 608 B on its own branch; master has gained the plugin-template
commits since, so the difference is not attributed).

**Plugin authors and API clients.** `plugin-api` did not change, nor did the testkit contract
(`plugin-testkit-api.txt`). The install's Maven repository ships the same nine artifacts as after batch 3 (`values`, `api`,
`config`, `files`, `core`, and the four plugin ones); their contents moved:`com.histmersion.domain.BundleEntities` and `BundleReader` are now in
`com.histmersion.files` (`histmersion-files`); `MergeIssueDetail` is new in api. The JSON is the same.

**What is left for batch 5.** Articles and the `Filter` decision, as planned (`ReviewArticle` 13 names,
`PlanArticles` 9, `CompileArticle` 5, `FilterInference` 5), and the final edge cleanup. Not started here,
for these reasons:

- *The agent checkpoints.* `EnrichSource`'s step records (`Classified`, `Findings`, `VerifiedFindings`,
  `Geocoded`, which holds api's `FindingInput`) and `ReviewArticle`'s (`Finding`, `Merged`, `Report`) are the
  stored JSON of agent steps, with their codecs derived in the domain, and `CompareRuns` decodes them itself.
  Giving them domain records means moving those codecs out of the domain (to api or server), and with them
  the decoding `CompareRuns` does — a change to how every step writes and reads its checkpoint, whose bytes
  must be proved against stored runs. `ReviewArticle`'s half is the articles area; `EnrichSource`'s
  (10 names: its patch records `SourcePatch`, `MetadataPatch`, `FindingsPatch`, `ListPatch`, `FindingInput`,
  `PeriodInput`, and `Period`, `Geocode`, `Lint`, `WorkRef`) and `AgentProposals`' (4) belong with it, since the
  same steps write both.
- *`WorkRef`* (`BrowserDownload`, `Scheduling`, `PlanGoal`, `EnrichSource`): the upstream work record the
  discover endpoints return and plugin-host's acquisition client builds; the domain reads it in the same
  shape, so a domain twin has no reason yet.
- *Managers' neighbours:* `SchedulerPolicy` 8, `GoalFocus` 6, `WatchSchedules` 5, `HintProposals` 4 — the same
  item-kind and saved-config records as `ManagerPolicy`, a recorded edge.
- *Domain → config:* the 12 files above; `ManagerDefaults` is the pattern.
- The recorded edges this batch kept: the merge records (bundle lines and plan), the managers' saved config
  and item state.

## Unresolved

None.

**Resolved.**

- *`Filter`: a wire record, in `api`* — decided 2026-10-25 (#3967, step 7 batch 5). The query model is
  built by the clients (web 9 files, 28 uses; the CLI 7, 14 — `Filter.of`, `FilterQuery`), read by the server
  (9 files, 54 uses: it resolves place names into gazetteer ids and hands the value on) and rendered as SQL
  by db (`FilterSql`, 9 files, 52 uses). The domain reads none: no domain file names `Filter`. A domain
  value with a wire `FilterView` would be a field-for-field twin, converted on every handler path, and hold
  no logic of its own — what interprets a filter is SQL, which is db's, and the flat-shape parse the three
  surfaces share (`Filter.of`), which the CLI needs and so stays in api. Rule 3 lets db reuse the wire value
  while nothing is in its way, and nothing is: db stores and reads wire records in 50 of its 57 files. `BBox`
  stays beside it; the domain's one use (the event catalogues' thresholds) takes it as a value.

- *The vocabularies domain and wire share* (`EdgeKind`, `SourceStatus`, `GoalStatus`, `PlaceKind`,
  `MediaRole`, …) — decided 2026-10-25 (#3967, step 7 batch 2): they live in `values` as plain Scala types
  with no codec, with their spelling table (`WireEnumValues`); the circe/tapir instances are in api
  (`VocabularyCodecs`), db's column codecs stay in db. See "Step 7, batch 2 as built".
