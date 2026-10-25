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

## Unresolved

- Whether `Filter` (used by web 28 times, db 52, server 55) is a wire record or a domain value with an
  SQL rendering decides whether it lands in `api` or `domain`; db's `Filter → SQL` code suggests the
  latter, with a wire `FilterView`. Steps 4–6 left it in `api`, with everything the wire needs; step 7's
  first batch did not need to decide it.

**Resolved.**

- *The vocabularies domain and wire share* (`EdgeKind`, `SourceStatus`, `GoalStatus`, `PlaceKind`,
  `MediaRole`, …) — decided 2026-10-25 (#3967, step 7 batch 2): they live in `values` as plain Scala types
  with no codec, with their spelling table (`WireEnumValues`); the circe/tapir instances are in api
  (`VocabularyCodecs`), db's column codecs stay in db. See "Step 7, batch 2 as built".
