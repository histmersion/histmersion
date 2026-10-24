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

## Unresolved

- How much step 2 saves is a measurement, not an estimate: Kindlings derives one codec per record, and
  how many records the 82 web endpoints reach is known only after linking.
- Whether `Filter` (used by web 28 times, db 52, server 55) is a wire record or a domain value with an
  SQL rendering decides whether it lands in `api` or `domain`; db's `Filter → SQL` code suggests the
  latter, with a wire `FilterView`.
- Compile times per module after the split were not measured; core alone compiles 150 sources in ~54 s
  today (docs/research-build-speed.md §1).
