# Domain model, package by feature, first-party plugins out of the server (refactor step 3, design)

Status: **proposal, waiting for the user's approval**. Nothing here is built. Written 2026-10-30 on master
58de5489 (branch `domain-design`); revised the same day after the user's review of §3 (subdomains above the
features; no `planning`; `personal` as its own subdomain with a marks interface; `library`, `research`,
`atlas`, `media` named; acquisition and OCR dissolved; automation writes only through other subdomains'
services). Answers review items #7485, #7490, #7903, #7908 and #7911, and the "ad-hoc model" remark.

Inventories too long for this page are in `research-domain-model/`:

| file | what it lists |
|---|---|
| [`dependencies.md`](research-domain-model/dependencies.md) | the measured references between subdomains (final grouping and the starting proposal), every upward reference, git co-change |
| [`files.md`](research-domain-model/files.md) | every source file of `server`, `core`, `db`, `api`, `config`, `files` mapped to its target subdomain and feature |
| [`objects.md`](research-domain-model/objects.md) | all 645 top-level objects without a same-named type, each with its destination and a one-line reason |
| [`primitives.md`](research-domain-model/primitives.md) | primitive parameters and fields by name, measured per module, and the counts behind §4 |

`atlas` is a placeholder name for the subdomain of places, periods and events (Q17). It appears only as the package
segment `domain.atlas` / `api.atlas` / `config.atlas`, so choosing another name is one package rename.

## 0. Summary

1. The code is grouped into **nine subdomains**, each with its own vocabulary, and features inside them:
   `com.histmersion.domain.<subdomain>.<feature>` (§3). From the bottom: `platform` (jobs, identity, work lists,
   lint runner, plugin host), `atlas` (places, periods, events and what attests them), `media`, `library`
   (sources, findings, citations, labels, search, and getting sources in), `research` (research results; articles
   today), `automation` (the workflows: runs, managers, approvals, autopilot, schedules, goals, TASKS, LOG), then
   `exchange` (bundle, merge) and `operations` (backup, maintenance, settings, about, basemap) at the top, with
   `personal` beside them. OCR is a supporting area (`util.ocr`), not a domain.
2. The grouping is **measured, not asserted**: 546 cross-subdomain type references and 408 commits of co-change
   (`dependencies.md`). The data moved merge and bundle out of operations (merge↔bundle co-change 0.50; 138 of
   operations' 146 references into research data were theirs), the job runner below every subdomain (used by
   atlas, media and library), the evidence that attests events into the atlas (events use it 34 times; moving it
   cut upward references from 160 to 125), and the article review ledger into automation (review↔pipelines 0.44).
3. **Direction**: each subdomain depends only on the ones below it; `personal` only on `platform`. 121 references
   point upward today; §3.4 lists each group with what removes it — mostly seven small interfaces that lower
   subdomains own and higher ones implement (personal's `Markable` among them) and a `server.views` layer for pages
   that join several subdomains. An `ArchitectureSuite` over the imports enforces the table.
4. **Automation writes like a person**: 36 direct calls from automation into other subdomains' repositories (15
   files) and its SQL over `articles`, `sources`, `queue_items` and `jobs` go through the owners' services, with
   `Caller.Automation(run)` (§3.5).
5. The model stops passing primitives: **about 55 identifier and value newtypes** and 32 vocabulary enums (§4),
   most of them in `values`, replace roughly 4 000 measured `String`/`Int`/`Long`/`Double` parameters and fields.
   Their JSON, columns, HOCON keys, CLI flags and MCP schemas stay byte-identical. `Licence` becomes one shared
   domain type for media, sources and datasets (§3.7).
6. Of the 645 type-less top-level objects, 229 move onto a type, 51 namespace objects dissolve into packages, 29
   move into plugin modules, 10 handler groups become controllers, 6 `main` objects become `@main def`s, and 320
   stay standalone for one of six stated reasons (§5).
7. Every service has the shape of AGENTS.md rule 13, with a worked example on the work lists (§6). Repositories
   take the connection they run in; services open transactions through a small `Transactor`; the clock and the
   environment come from the composition root.
8. The 29 first-party implementations in the server (fetchers, catalogues, collections, AI providers, gazetteer
   importers) move into **six plugin modules** beside vaults, plus a shared `upstreams` library; ten additive SPI
   members close the measured gaps (§8). The running of each acquisition stays in its subdomain.
9. **12 slices** (§9): groundwork, `personal` as the template, two plugin slices, then one subdomain per slice from
   the bottom up. Equivalence is shown by permanent snapshot files (OpenAPI, MCP `tools/list`, toolbox schemas, CLI
   help, plugin report, settings view), codec round-trips, contract suites, and the bundle with `--self-check`.
10. Section 11 has 27 open questions; the ones that change the shape most are Q17 (the atlas's name), Q18 (test vs
    sbt modules for the direction rule), Q24 (where goals live) and Q5 (the CLI agent runtimes).

## 1. The review points and where each is answered

| # | asked | answered in |
|---|---|---|
| #7485 | package by feature; `domain`, `domain.<sub>`, `util.<area>`; package-private repos; db = primitives only; services own transactions; no globals; Handlers → Controllers; api split into shared vs API-specific; subdomains independent of endpoints | §3, §6, §7; slices 2, 5–12 |
| #7490 | `private final class Implementation` + `final class InMemory` on the service companion; InMemory behaves like the DB; shared atomic refs; seeded through the service; no mockito | §6 (with a contract suite that proves "behaves like the DB") |
| model | "ad-hoc model, helpers on primitives instead of newtypes" | §4 (catalogue, measured), §5 |
| #7903 | no free-standing helper objects; rule 14 | §5, `objects.md`, rule text in §5.4 |
| #7908 | `@main def` instead of `object X { def main }` | §5.5 |
| #7911 | first-party plugins out of the server, one module per plugin or family, `plugin-api` only, ServiceLoader, built in, switchable | §8; slices 3, 4 (and 12 for ai-cli, Q5) |
| §3 review | subdomains above features, measured by co-change and imports; no `planning`; `personal` with an interface owned by it; dependency direction enforced | §3.1–§3.4, §3.6, `dependencies.md` |
| §3 review | `library`, `research`, `atlas`, `media` named; acquisition and OCR not domains; licence shared; automation writes through services; goals re-checked | §3.1, §3.5 (automation), §3.7 (licence), §3.8 (OCR), §8, Q24 |

Fixed decisions kept everywhere: one ServiceLoader service type (`com.histmersion.plugin.Plugin`); `plugin-api` holds
interfaces and plain data only; our own plugins are built in and can be disabled; everything stays AGPL-3.0-or-later.
User decisions recorded in this revision: no `planning` subdomain (goals, TASKS and LOG go to automation, ACQUIRE to
the library; `queue_items` stays one table); `personal` is a subdomain of its own that knows nothing about its
targets; acquisition is not a subdomain; OCR is a supporting area.

## 2. Today, measured

All numbers are from `modules/*/src/main` at 58de5489; the scans are regexes run once for this document
(the method is at the top of each inventory file).

| measure | value |
|---|---|
| Scala sources (main) | 1 770 files, 161 k lines: server 311 / 44 k, api 597 / 25 k, web 264 / 34 k, db 75 / 18 k, core 100 / 17.5 k, cli 76 / 9.8 k, config 145 / 3.8 k |
| top-level objects | 1 281; **645** without a same-named type (the brief counted 644 of 1 313) |
| `citekey`/`ck`/`citekeys: String` | 426 (393 outside web) although `values.Citekey` exists; `Citekey` is used in 15 signatures |
| `slug: String` | 241 (198 outside web); no `Slug` type. Three different slug regexes (`Articles.slugOf` bounded at 121 chars, `ArticleLinks` and `MergePlanner` unbounded) |
| `doi: String` | 129 although `values.Doi` exists (3 typed uses) |
| `lat`/`lon` (and bbox edges): `Double` | 186 although `Latitude`/`Longitude` exist (4 typed uses each) |
| years as `Long`/`Int` (`periodStart`, `from`, `year`, …) | 450 although `values.Year` exists |
| source uids (`uid`, `sourceUid`, `origin`: `UUID`/`String`) | 157 |
| primitive fields in case classes | config 528, core 827, server 831, api 2 936 (`String`/`Int`/`Long`/`Double`; Booleans not counted) |
| global reads in main code | `Instant.now()` 53, `System.currentTimeMillis` 38, `System.nanoTime` 35, `Thread.sleep` 28, `System.getProperty` 17, `sys.env` 13, `System.getenv` 11, `UUID.randomUUID` 9, `LocalDate.now` 3, `user.dir`/`user.home` 8 |
| `object X { def main }` | 6 in main code (server `Main`, cli `Main`, web `Main`, `PluginTool`, `GeneratePlugin`, `PluginResolver`) + 3 test tools (`BasemapLabelsTool`, `BasemapExtractTool`, `PromptBudget`). #7908 names `AgentTrees`; it has no `main` — `GeneratePlugin` is the one that does |
| endpoint logic | `Handlers` (1 891 lines, 130 methods over 17 features), `OpsHandlers` (287), 9 more `*Handler(s)` classes (acquire, agents, managers, ingest, bundle, events, media, IIIF, auth) |
| first-party plugins in the server | 26 (7 fetchers, 10 media collections, 3 event catalogues, 6 AI providers), plus 3 gazetteer importers shaped like plugins but hard-wired; `plugin-vaults` (3) is the only module |

Live data checked read-only (`histmersion query`) against the constraints proposed below: 0 of 1 209 citekeys and
0 DOIs break their rule; all 467 article slugs and all 214 goal slugs match `[a-z0-9][a-z0-9_.-]*`. Place ids are
`<authority>_<id>` (`geonames_2911611`), event ids are slugs (`uluburun_shipwreck`), job ids are UUIDs.

A start already exists. `server/…/domain/{activity,jobs,personal,settings}` follow rule 13 roughly: there is a
trait, an `Implementation`, an `InMemory`, and controllers. They still use `() => Instant`, `DataSource` and
primitive limits. Slice 2 brings them in line, and they are the pattern the rest copies.

## 3. Subdomains and features

### 3.1 The subdomains

A subdomain is a part of the application with its own vocabulary; a feature is one piece of functionality with its
own data inside it. Neither follows endpoints, tools or commands: one endpoint may call several features (the About
window reads `operations`, `platform`, `library` and `automation`). Each table has exactly one owning feature; only
the owner's repository touches it, and everyone else goes through the owner's service.

Layers, bottom first. A subdomain may depend on the ones below it only (§3.3).

| layer | subdomain | what it is about (its words) | features — `domain.<subdomain>.<feature>` | tables owned | lines today (main) |
|---|---|---|---|---|---:|
| 0 | `platform` | the machinery every subdomain runs on, holding no research content: job, job kind, uid, alias, tombstone, work list, lint rule, plugin generation | `jobs` (runner, records, kinds registry), `identity` (instance origin, uid redirects, tombstones, the alias mechanism), `maintenance` (the lock writes wait on), `worklist` (the list kernel of §6), `lint` (the runner; rules come from each subdomain), `plugins` (generation, registries, legacy settings) | `jobs`, `instance`, `uid_aliases`, `tombstones`, `alias_tombstones`, `queue_items` (rows owned per list, below) | 3 800 |
| 1 | `atlas` (Q17) | data points on the map × timeline: place, name, period, event, dating, catalogue, gazetteer, and the evidence that attests them | `places` (gazetteer, names, overrides, relations, hierarchy, geocoder, site types, point areas, land, the gazetteer import loop), `periods` (presets, PeriodO), `events` (datings, edges, participants, the catalogue import loop, corrections), `evidence` (attestations, event link candidates and checks, leads), `maptiles` (tiles, undated sites, the `MapLayer` interface) | `places`, `place_*` (8), `gazetteer_sources`, `land`, `periods`, `seed_versions`, `events`, `event_*` (8), `corrections`, `attestations`, `site_undated_*`, `map_changes` | 14 200 (−900 to plugins) |
| 2 | `media` | pictures and their rights: asset, link, stated licence, credit, collection, figure, cover | `assets` (store, IIIF, thumbnails), `links`, `imports` (the collection import loop and its licence policy), `figures`, `covers` | `assets`, `asset_links`, `media_links`, `media_checks` | 7 000 (−2 400 to plugins) |
| 3 | `library` | what sources say and how they get in: source, citekey, body, finding, citation edge, label, candidate, acquire check | `sources` (record, body, chunks, aliases, rename, delete), `findings`, `citations` (edges, refs, follow-ups, assessments, retractions), `labels`, `search` (filter, search, aggregate, histogram, geo-when, read-only query), `acquisition` (discovery, candidates and watches, recheck, the fetch chain and its quota, the ACQUIRE list), `ingest` (what an ingest does to a source: the job, the record, originals, seed import), `calibre` | `sources`, `source_*` (3), `findings`, `finding_places`, `edges`, `unresolved_edges`, `assessments`, `retraction_watch`, `source_candidates`, `candidate_watches`, `acquire_checks`, `upstream_quota`; `queue_items` where `list = 'acquire'` | 13 200 (−2 300 to plugins) |
| 4 | `research` | research results; an article is the one kind today: article, version, link, coverage, critique pairing, review state | `articles` (record, versions, links, coverage, critique pairing, review state, prose lint, pages) | `articles`, `article_*` (4) | 5 100 |
| 5 | `automation` | the workflows and what they act towards: run, step, lease, model chain, allowance, pipeline, review panel, approval, manager, autopilot, schedule, goal, task, log entry | `agents` (runs, steps, leases, state, chains, allowances, plan pools, toolbox, definitions), `pipelines`, `review` (the panel and its ledger), `approvals`, `managers`, `autopilot`, `scheduler`, `goals` (Q24), `tasks`, `log`, `activity` | `agent_*` (8), `plan_*`, `approvals`, `agent_managers`, `manager_*`, `agent_autopilot`, `review_ledgers`, `goals`, `goal_*` (4); `queue_items` where `list` is `tasks` or `log` | 33 300 (−3 000 to plugins) |
| 6 | `exchange` | research records carried between databases: bundle, merge session, decision, ledger, plan, issue | `bundle`, `merge` | `merges`, `merge_decisions`, `merge_ledger`, `merge_sessions` | 9 600 |
| 6 | `operations` | running the install: backup, vacuum, storage, settings, about, base map | `backup`, `maintenance` (vacuum, reindex, storage report, PostgreSQL relocation), `settings`, `instance` (about, health, version, self-check, UI config and counters), `basemap` | `ui_seen` | 9 400 |
| beside | `personal` | one person's marks on anything: bookmark, note, read mark, saved view | `bookmarks`, `notes`, `reading`, `marks` (the interface others implement, §3.5) | `bookmarks`, `notes`, `read_marks` | 1 700 |

Outside the subdomains: the shared kernel `com.histmersion.domain` (types every subdomain uses: `Period`, `Licence`,
`Attribution`, `Caller`, …), `values`, the supporting areas of §3.7 (`util.ocr` among them), and the server's
composition root and `server.views`. Together 12 700 lines today (plus 460 for `util.ocr`), 4 400 of them the
composition root and 2 400 the handlers that the slices dissolve. Per-file assignments: [`files.md`](research-domain-model/files.md).

`automation` is the largest (33 k lines, 14 k of them `agents`) because the agent machinery is; its features share
one vocabulary and change together (agents↔managers 0.30, autopilot↔scheduler 0.27, goals↔managers 0.22), so it
stays one subdomain whose slice deploys in two steps (Q25).

**`research` and a second kind of result.** Today every result is an article, so `research` holds one feature,
`articles`, and no generic "result" type. The boundary is drawn so that a second kind is a sibling package
(`research.<kind>`), not a reshaping: what the rest of the code needs from research goes through `ArticleService`
and through the interfaces research implements (`Markable` for personal, `SourceReferrer` for the library), and
automation inserts results through the service like a person would. If a second kind came, these would move up
into `research` itself, because they are not article-specific in meaning: the version history, the coverage
filter (which library sources a result covers, with its reasons), the review state written by automation's review
panel, `[[citekey]]` link resolution, and page rendering.

### 3.2 What the measurements changed

Method (details and full tables in [`dependencies.md`](research-domain-model/dependencies.md)): a **reference** is a
`src/main` file naming a type that belongs to another subdomain, counted once per (file, type); **co-change** is
Jaccard over 408 commits (merges, the reformat 4c1da9d1, the step-2 split ba0a373d and commits over 60 files left
out; old paths classified by the same name rules). At the level of subdomains co-change is weak everywhere (Jaccard
≤ 0.23): features change together inside subdomains more than across them. So the references decide the layering,
and feature-level co-change decides the borderline placements.

References between subdomains, final grouping (row depends on column):

| from \ to | atlas | automation | exchange | library | media | operations | personal | platform | research |
|---|---|---|---|---|---|---|---|---|---|
| **atlas** | · | 0 | 0 | 11 | 6 | 0 | 0 | 12 | 0 |
| **automation** | 7 | · | 0 | 80 | 0 | 1 | 0 | 43 | 56 |
| **exchange** | 19 | 8 | · | 18 | 5 | 2 | 0 | 16 | 6 |
| **library** | 27 | 10 | 2 | · | 15 | 1 | 0 | 25 | 10 |
| **media** | 11 | 0 | 0 | 3 | · | 0 | 0 | 4 | 1 |
| **operations** | 5 | 4 | 2 | 5 | 0 | · | 0 | 17 | 0 |
| **personal** | 1 | 2 | 0 | 0 | 0 | 0 | · | 1 | 0 |
| **platform** | 12 | 4 | 3 | 21 | 1 | 8 | 1 | · | 6 |
| **research** | 7 | 13 | 0 | 20 | 1 | 0 | 0 | 13 | · |

546 references, **121 upward**. The starting proposal measured the same way had 622 references, 135 upward, and
**71** references from a core subdomain into automation or operations — the user's forbidden direction — against
**26** in the final grouping. Each change below is backed by numbers:

| proposal | decision | evidence |
|---|---|---|
| `operations` holds backup, bundle, merge, maintenance, settings, plugins host, instance | **merge and bundle become `exchange`** (top); operations keeps backup, maintenance, settings, instance, basemap | merge↔bundle co-change 0.50 (16 commits); merge's next partners are sources 0.23, articles 0.21, goals 0.19, events 0.15 — research data, not operations. Proposed operations referenced research subdomains 146 times, 138 from bundle and merge; what remains references them 16 times |
| jobs in `automation` | **jobs, the identity tables, the maintenance lock, the work-list kernel, the lint runner and the plugin host form `platform`** (bottom) | jobs are used by agents 8, ingest 6, events 6, maintenance 5, basemap 5, merge 4; in automation they made atlas, media and library depend on automation (55 references into automation in the proposal, most of them jobs and the queues) |
| `acquisition` as a subdomain with ingest and Calibre | **dissolved** (user): sources' acquisition, ingest and Calibre into `library`; gazetteer and catalogue import loops into `atlas`; the collection import loop into `media`; implementations into plugins (§8) | acquisition → library-side features 46 references, back 2; ingest co-changes with sources 0.17 and acquisition 0.14 |
| attestations in corpus; event links and leads in events | **`evidence` (attestations, link candidates and checks, leads) in `atlas`** | events use evidence 34 times; evidence uses findings and citations twice. Moving it cut atlas → library from 46 to 11 and the upward total from 160 to 125 |
| article review in writing | **the review panel and its ledger in `automation.review`; the article keeps its review state** | review↔pipelines co-change 0.44 (15 commits); the ledger names `ReviewGoal`, `ReviewApproval`, `AgentVerify`, `CompareRuns` |
| corrections as a feature | **folded into `atlas.events`** | used only by events (16 references) and bundle |
| map tiles in geography | **kept in `atlas.maptiles`**, with media and library drawing their layers through `MapLayer` | atlas → media 6 references, all tiles and detail pages |
| media beside corpus/writing | **media below library** | library → media 15 (ingest stores figures and originals as assets, source covers), media → library 3 |
| search, query, corpus lint as corpus's read side | **search and query in `library.search`; lint's runner in `platform`, rules per subdomain** | search co-changes with articles 0.29, sources 0.20; lint's rules span places (11 commits), sources, media, events |
| basemap as a generic area | **`operations.basemap`** (a feature with endpoints, jobs and settings, no research data) | co-changes with instance 0.18 (15), nothing else above 0.06 |
| OCR inside ingest | **`util.ocr`** (user): converter client and state, spool, JATS conversion, the mechanical chunker | converter co-changes with nothing above 0.07; the library's ingest is its only user |

### 3.3 Direction, and how it is enforced

| subdomain | may depend on |
|---|---|
| `platform` | the shared kernel, `values`, `util.*` only |
| `atlas` | platform |
| `media` | platform, atlas |
| `library` | platform, atlas, media |
| `research` | platform, atlas, media, library |
| `automation` | platform, atlas, media, library, research |
| `exchange` | everything below it, automation included (it carries goals and work-list items) |
| `operations` | everything below it; not exchange |
| `personal` | platform only |
| any subdomain | additionally the interface packages lower subdomains own for it to implement (`personal.marks`, `library.sources.referrers`, `atlas.maptiles.layers`, `atlas.evidence.passages`, `platform.jobs.kinds`, `platform.lint.rules`, `platform.identity.aliases`) |
| `server` (composition root, `server.views`) | everything |

Core subdomains (atlas, media, library, research) never depend on automation, exchange or operations; personal
depends only on platform and is implemented, never imported.

**Enforcement: `ArchitectureSuite`** (server tests, no database, seconds). It reads every Scala source under
`com/histmersion/domain/` in `core` and `server`, takes the subdomain from the package, collects every
`com.histmersion.domain.<x>` and `com.histmersion.api.<x>` it imports or names fully qualified, and fails on a pair the
table does not allow, naming file and line. The table is a DAG, so a cycle is a failure too. From slice 1 it runs
with an explicit list of known violations (the 121 of §3.4, by file); each slice removes its rows and may not add
any. The compiler-enforced alternative — one sbt project per subdomain, as the plugin modules are kept apart with
`% Runtime` — is Q18.

### 3.4 The upward references and what removes them

| group | refs | what removes it | slice |
|---|---:|---|---|
| plugin glue in platform naming built-ins (`Plugins`, `LegacyPlugins`, `Builtin*`) | 12 | the built-ins become plugins | 3–4 |
| the job kinds enumerated in platform (`JobKind`, `JobDetail`, `JobActions`, `JobRepo`) | 16 | **`JobKinds`**: `JobKind` becomes a newtype; each subdomain registers its kinds and renders its own job detail | 5 |
| lint rules inside the platform runner (`LintRepo`, `Lint`) | 15 | **`LintRules`**: each subdomain contributes its rules; the runner, its fixes and its report stay in platform | 5 |
| the alias and uid mechanism switching over owners' tables (`AliasRepo`, `AliasKind`, `ResolvedId`) | 5 | **`AliasTables`**: each owner registers its alias table | 5 |
| the work-list kernel touching owners (goal scope, DOI matching, notes) | 4 | goal scope to automation, DOI matching to `library.acquisition` (§6.2) | 5 |
| atlas pages and tiles needing media and library (`EventDetail`, `PlaceDetail`, tile features, places tree counts, the passage search over source bodies when an event is linked) | 17 | **`MapLayer`** (atlas owns; library and media implement), **`PassageSource`** (atlas's evidence owns; library implements), and detail pages in `server.views` | 6–7 |
| media needing library (source covers, figures from a source's PDF) | 3 | covers and figures are `library.ingest` steps that store into media | 7–8 |
| source rename and delete touching articles, goals, work lists, media (`SourceRepo`, `Rename`, `SourceDelete*`) | 9 | **`SourceReferrer`** (library owns): `references(uid)`, `renamed(from, to)`, `deleted(uid)` inside the library's transaction; research, media, automation implement it | 8 |
| goal-aware acquisition code (candidate search by goal, ingest backlog, scheduling) | 10 | that code serves the agents and moves to automation | 8, 10 |
| research's review state naming automation types | 13 | research owns `ReviewState`; automation's review panel writes it through `ArticleService` | 9 |
| personal joining five tables by name | 3 (+5 SQL joins) | **`Markable`** (§3.5) | 2 |
| top-level cross-talk (merge settings view, backup state) | 5 | config records; `BackupState` read through operations' service by a view | 11–12 |
| name collisions in the scan (`CitationCount`, `Coverage`, …) | 9 | nothing to do | — |

A page that joins several subdomains for one response (a source's, a place's or an event's detail, About,
Activity) is a **view**: a controller in `server.views`, above every subdomain, that calls their services.
Subdomains do not assemble other subdomains' data for display.

### 3.5 Automation uses other subdomains like a person

Automation owns the data and processes that define workflows: runs, steps, leases and checkpoints, schedules,
managers, approvals, the autopilot, the TASKS and LOG lists, model chains and allowances. It **reads** the library,
the atlas, media and research, and **inserts results** into research and the library, only through their public
services — the same methods a person's request reaches — with `Caller.Automation(run)`.

Measured today (scan of automation's files for calls into other subdomains' repositories, and of automation's
repositories for SQL over other subdomains' tables):

| back door today | count | where | becomes |
|---|---:|---|---|
| `QueueRepo` (`get` 7, `acquireNaming` 3, `list` 2, `add` 1) | 13 | `AcquireRecheckService`, `OaBacklog`, `SeedService`, `ManagerSupervisor`, … | `QueueService` (platform) for TASKS and LOG; `AcquireListService` (library) for ACQUIRE |
| `AcquireCheckRepo` (`countSince` 3, `record` 2, `heldByDoi`, `of`) | 7 | `AcquireRecheckService` | the recheck is the library's process (`library.acquisition`); `AcquireRecheckService` moves there and automation schedules it through its service |
| `ArticleRepo.load` | 7 | the review, compile, propose and infer-filter pipelines, `ManagerSupervisor` | `ArticleService.get` |
| `PeriodSql` fragments | 5 | `GoalRepo` | a period filter value; the SQL stays the atlas's |
| `JobRepo`, `AliasRepo.renamedTo`, `CandidateRepo.watches` | 4 | `AgentsHandlers`, `GoalSweep`, `ListQueries` | `JobService`, `IdentityService`, `CandidateService` |
| SQL in `ManagerRepo`, `AgentRepo`, `GoalLinkRepo` reading `articles` (10), `article_versions` (1), `sources` (3), `queue_items` (5) | 19 | managers' "which articles need review / which sources lack X" queries | batch query methods on `ArticleService`, `SourceService`, `QueueService` (one call per page of ids; measured at live volumes in the slice, per the performance rule) |
| SQL in `AgentRepo` writing `jobs` (9) and reading it (27) | 36 | a run is also a job | `JobService` |
| writes through `Handlers` (`patchArticle` 5, `patchSource` 3, `putArticle`, `putGoal`, `patchGoal`) and the toolbox's tools | 11 + the toolbox | pipelines | already "as a person could"; they become `ArticleService`/`SourceService` calls with `Caller.Automation(run)` |

### 3.6 `personal` and the `Markable` interface

Personal owns bookmarks, notes and read marks (and saved views, which are bookmarks of kind `view`); each points at
a target and knows nothing about it. Today `ItemTitles` joins `sources`, `articles`, `events`, `goals` and `places`
by name in SQL and `ItemKind` enumerates the other subdomains' kinds. In the target, the subdomains whose records
can be marked implement an interface owned by personal (dependency inversion); personal imports no subdomain.

```scala
package com.histmersion.domain.personal.marks       // the only package of personal that others import

/** A record of another subdomain as a mark stores it: the owner's kind, its key, its uid where it has one. */
final case class ItemRef(kind: ItemKind, key: ItemKey, uid: Option[ItemUid])

type ItemKind = ItemKind.T                            // "source", "article", … — registered by the implementers
object ItemKind extends RefinedType[String, Match["[a-z][a-z-]*"]]

enum MarkTarget:
  case Item(ref: ItemRef)                                                       // a source, article, event, place, picture, goal
  case Point(at: LatLon, zoom: Option[MapZoom], years: Option[Period])          // a place on the map
  case View(state: Option[ViewState], at: Option[LatLon], zoom: Option[MapZoom], years: Option[Period])
  case Span(years: Period)                                                      // a period

/** Implemented by each subdomain whose records can carry a bookmark, a note or a read mark. */
trait Markable:
  def kinds: Set[ItemKind]
  /** The record `key` names now — aliases and uid redirects followed — or why there is none. */
  def resolve(kind: ItemKind, key: ItemKey)(using DbCon): Either[String, ItemRef]
  /** How each record shows in a person's lists. One call per kind on a page, batched. */
  def describe(refs: Set[ItemRef])(using DbCon): Map[ItemRef, Marked]
  /** Read marks make sense for this kind (sources and articles today). */
  def readable(kind: ItemKind): Boolean = false

final case class Marked(title: Title, at: Option[LatLon], years: Option[Period], route: WebRoute, state: ItemState)

enum ItemState:
  case Live
  case Renamed(now: ItemKey)          // the key is an alias now (citekey, slug, event id renamed)
  case MergedInto(ref: ItemRef)       // a uid redirect a merge wrote (platform.identity)
  case Deleted(at: Instant)           // a tombstone: the mark stays, shown as gone
```

| target kind | implemented by | resolves through |
|---|---|---|
| `source` | `library` | citekey and its aliases; uid |
| `article` | `research` | slug and its aliases; uid |
| `event`, `place` | `atlas` | event id and place id, their aliases; uid for events |
| `picture` | `media` | asset uid |
| `goal` | `automation` | goal slug and its aliases; uid |
| `point`, `view`, `period` | personal itself | no owner: personal checks coordinates, the view's length and the years (today's `Target.check`) |

The composition root builds one `Markables` registry from the implementers and hands it to personal's services.
Personal **pulls**: rename, merge and delete are answered by `describe` at read time — the uid stored with every
mark (V109's `target_uid`) survives renames, merges are followed through `uid_aliases`, deletions read the
tombstones — so no owner ever calls personal (Q21). The word search over a person's marks matches the stored
`target_label`, which personal refreshes when `describe` returns a new title; today it matches the live title
through the joins, so a renamed item is found by its new title only after it is next shown (Q21).

**Per user.** Every personal service method already takes a `UserId` (`user_id = 'local'` today). With
`docs/research-multi-user.md` (#7843) a `people` subdomain (accounts, sessions, roles) comes beside personal;
personal needs from it nothing but the `UserId` value type in `values`, and the controllers pass the signed-in
caller instead of `Caller.Local`.

### 3.7 A shared `Licence`

The licence is not media's: sources carry their open-access licence, datasets their data licence, pictures theirs.
Today:

| type or column | where | what it is | becomes |
|---|---|---|---|
| `MediaLicence.Licence(id, family)` with `classify`, `ofUrl`, `parse`, `allowList`, `allowed`, `needsAttribution`, `isPublicDomain` | core | a licence classified into a family | **`domain.Licence(id: LicenceId, family: LicenceFamily)`** in the shared kernel; the functions on its companion; `LicenceFamily` an enum with the wire strings kept |
| `MediaRights` (core) and `plugin.MediaRights`, `DataCredit` (api: name, use, licence, licence URL, credit, URL) | core, SPI, api | licence + licence URL + author + credit | **`domain.Attribution(licence, licenceUrl, author, credit)`** shared; `MediaRights` becomes it; a `DataCredit` is a dataset with an `Attribution` |
| `sources.license` (from `FetchedFile.licence`) | library | the OA licence of a source | `Option[Licence]` on the source, classified on read (column unchanged, Q26) |
| `assets.license`, `assets.license_url` | media | a picture's licence | `Attribution` |
| `gazetteer_sources.licence`, a catalogue's licence (`CatalogProviderInfo`, `EventCatalogue.licence`), Cliopatria's (V107) | atlas | a dataset's licence | `Attribution` |
| `plugin.Licence` (SPI) | plugin-api | a licence as a provider states it | stays: the host classifies it into `domain.Licence` |
| allowlists (`media.<provider>.licences`, `media.collections.licences`, `per-collection.<name>`) | media | a policy over families | stays media's (`LicencePolicy` over `LicenceFamily`) |
| `OaStatus` (`gold`/`diamond`) | library (`BrowserDownload`) | an open-access route, not a licence | stays the library's |
| `Access` (`Open`/`Subscription`/`Unauthorised`) | SPI | whether a fetch is authorised | stays the SPI's |
| `MediaProvider`, `Provenance` | api | where a record came from | stay |

`LicenceId` (the stored and sent string) is in `values`, because the web page names licences (`LicenseName`).
Measured licence-ish fields today: `license: Option[String]` 19, `licence: String` 10, `licence: Option[…]` 10,
`licenseUrl` 8, `licenceUrl` 4, `licences: String` 5 (outside web).

### 3.8 Supporting areas (`com.histmersion.util.<area>`)

| area | holds | module |
|---|---|---|
| `util.database` | the pool, `Transactor`, Flyway runner, generic column codecs, `Jsonb`, `Keyset`, `Param`, `Sql`, `ListSql`, `Vocab` | `db` |
| `util.ocr` | the converter client and its state (the OCR service), the spool, JATS → Markdown conversion (over `upstreams`' JATS parser), the chunker. The library's `ingest` decides what happens to a source; this area only converts and splits | server |
| `util.json` | generic newtype codecs and schemas, `WireEnum`, `StrictJson`, `StrictJsonSchema`, JSON cursor helpers (`Read`) | `api` (JVM + JS) |
| `util.http` | the outbound client, policy, politeness, TLS, `Url` | `plugin-host` / server |
| `util.markdown` | flexmark flavour, Markdown → HTML, HTML atoms | server / `api` |
| `util.text` | `FoldedText` (Latin folding), `Plural`, `Csv`, `Diff3` | `api` / `core` |
| `util.images` | PNG, VP8L, Terrarium PNG encoders | server |
| `util.tiles` | MVT, PMTiles, the vector-tile writer | server / `core` |
| `util.files` | `SafePath`, directory size, the tar container | `files` |

`cli` and `web` keep their own trees (`cli.<subdomain>`, `web.<subdomain>`; `web` is already mostly so) and are
touched only at their edges (slice 12).

## 4. Newtype catalogue

### 4.1 Rules

- **Where a newtype lives.** It goes in `values` (JVM + JS, Iron only) when it crosses the wire or more than one
  feature uses it. It goes in `domain.<subdomain>.<feature>` when only one feature's logic uses it. It goes in
  `config.<subdomain>` when it is a setting with no meaning outside configuration. `plugin-api` keeps its own
  copies (`Year`, `Doi`, …; SPI 2), which chimney-iron converts because the names and constraints are the same.
- **Shape.** Rule 1's `type X = X.T; object X extends RefinedType[Base, C]`. The constraint is `Pure` unless the
  code already rejects values today, so no valid input is refused that is accepted now. Where a value has a rule,
  that rule is the one the code already enforces (taken from the current regexes and checks). Where two rules
  disagree today, the stricter one consistent with live data wins and the change is listed (`Citekeys.valid` vs
  `Citekey`, three slug regexes).
- **Smart constructors.** `X.either(raw)` comes from Iron. `X.parse(raw): Either[String, X]` exists only where
  today's error message must be kept (`Citekey.parse`).
- **Closed vocabularies are enums, not newtypes.** 32 objects of `val X = "…"` constants (`GoalStatus`,
  `MediaRole`, `AttestationTarget`, `CorrectionTarget`, `Provenance`, `AssetKind`, `EventTier`, …) become
  `enum X(val wire: String)` with the existing `WireEnumValues` spelling table, so the strings on the wire and in
  columns stay the same.

### 4.2 Identifiers

"replaces" counts parameters and fields declared with these names (main code, every module; in brackets: without
`web`). The method and the full table are in `primitives.md`.

| newtype | base | constraint | lives in | replaces | notes |
|---|---|---|---|---:|---|
| `Citekey` | String | today's `CitekeyRule` | values (exists) | 426 (393) | `citekey`, `ck`, `citekeys`; `Citekeys.valid` (`[a-z][a-z0-9_]*`, used by the merge planner) goes; the merge planner's message changes accordingly (listed in slice 5) |
| `Slug` | String | `[a-z0-9][a-z0-9_.-]{0,120}` (today's `Articles.slugOf`) | values | 241 (198) | the article slug; `ArticleLinks` and `MergePlanner` use the same rule instead of their unbounded copies |
| `GoalSlug` | String | same rule as `Slug` (Q10) | values | 159 (143) | `goal`, `goals`; separate type so a goal slug never passes as an article slug |
| `SourceUid`, `ArticleUid`, `EventUid`, `GoalUid`, `AssetUid` | UUID | Pure | values | 157 | `uid`, `sourceUid`, `origin` (the merge's stable ids); today `UUID` and `String` both |
| `PlaceId` | String | `[a-z][a-z0-9]*_.+` | values | 89 | `placeId`, `place`, `parentId`; live ids are `<authority>_<id>` |
| `EventId` | String | `Slug`'s rule | values | 34 | |
| `JobId` | UUID-text String | UUID pattern | values | 40 | `jobId`, `job` |
| `ManagerId`, `RunId`, `StepId`, `ApprovalId` | String / Long (as stored) | Pure | domain.automation (managers, agents, approvals) | 53 / 48 / – / – | |
| `QueueItemId` | Int | `Positive` | domain.platform.worklist | 21 | the worked example (§6) |
| `AssetId`, `AssetName` | Long / String | Pure | domain.media | 4 + share of `id: String` | |
| `BookmarkId`, `NoteId`, `ItemKey`, `UserId` | | | exist (personal pilot) | — | kept |
| `WikidataId` | String | exists | values | 18 | `wikidata`, `qid` |
| `Doi` | String | exists | values | 129 (122) | |
| `ArxivId`, `Pmid`, `Pmcid`, `Isbn`, `ArchiveId` | String | Pure (today's checks where any) | values | share of 30 | `WorkRef`, `WorkKey` fields |
| `PluginId` | String | exists in cli; one in `values` | values | share of `id: String` | reserved names on its companion |
| `ModelRef`, `ProviderName`, `ModelId` | String | `ModelRef` exists (parsed); others Pure | domain.automation.agents | 49 + 60 | |
| `UserId` | String | exists | values (moved from personal) | — | the multi-user design keys rows on it |

`id: String` (521 uses) is not counted under any one type: it is a different identifier in each feature, and each
slice types the uses in its own feature.

### 4.3 Values

| newtype | base | constraint | lives in | replaces | notes |
|---|---|---|---|---:|---|
| `Year` | Int | Pure (exists) | values | 450 (398) | `year`, `periodStart`/`periodEnd`, `from`/`to`, `start`/`end`, `asOf`. Columns stay `int8range`/`bigint`; the DB codec widens. `DeepTime.year(Long)` becomes `Year.fromLong` |
| `Latitude`, `Longitude` | Double | exist | values | 186 (160) | also `BBox`'s four edges |
| `Title`, `Summary`, `MarkdownBody`, `Claim`, `Venue`, `AuthorName` | String | Pure | values | 227 + 215 + 22 + 22 + 80 | one type per meaning, not one "Text" |
| `Tag`, `Topic`, `Region` | String | Pure | values | 191 | label vocabularies; `rename_label` works on them |
| `LicenceId` | String | Pure | values | 42 | the licence as stored and sent (an SPDX id, `public-domain` or a URL); the classified `Licence` is the shared domain type of §3.7 |
| `Sha256` | String | 64 lower-case hex | values | 39 | |
| `Url` | String | Pure (absolute-URL check where today's code checks) | values | 133 | `ServiceUrl` (config) becomes this |
| `CronExpression` | String | Pure; parsed by `Cron` | domain.automation.scheduler | 26 | `Cron`'s functions go on it |
| `PageSize`, `Offset` | Int | `Positive` / `Not[Negative]` | values | 268 + 70 | `limit`, `max`, `pageSize`, `offset`; the default and cap stay where they are configured |
| `Timestamp` | Instant | — | values | 175 ISO strings + 71 epoch-millis `Long`s | the domain holds `Instant`; the wire keeps its ISO text through the codec (`createdAt`, `updatedAt`, `since`, `before`); epoch-millis fields keep their number on the wire |
| `Seconds`, `Minutes`, `Hours`, `Days`, `Percent`, `ItemCount`, `TokenCount`, `CitationCount`, `MatchScore`, `ApiKey`, `CommaList`, `RefreshIntervalMinutes` | | Pure (step 2) | **moved from config to values** | 33 (`timeoutSeconds` etc.) beyond config | their `ConfigReader`s move from the companions into `config`'s readers file (rule 3) |
| `ByteCount` | Long | `Not[Negative]` | values | 36 | `bytes`, `size` |
| `Zoom` | Int / Double | Pure | values | 98 | `z`, `maxZoom`, `zoom` |
| `Kilometres` | Double | `Not[Negative]` | values | 43 | `toleranceKm`, `radiusKm` |
| `CharCount` | Int | `Positive` | util.ocr | 22 | `chunkChars`, `maxChars` |
| `ReviewRound`, `GraphDepth`, `Priority` | Int | Pure / `Positive` | their features | 20 / 40 / 20 | |
| `ImageSize` | (Int, Int) case class | — | domain.media | — | `object ImageSize` becomes the type |
| `FoldedText`, `Snippet` | String | — | util.text / domain.library.search | — | from `LatinFold`, `Highlight` |

Booleans stay `Boolean` with named parameters (Q8). The 13 config newtypes from step 2 keep their names.

### 4.4 Wire format and codecs

What must stay byte-identical, and how:

| surface | stays identical because | checked by |
|---|---|---|
| JSON (REST, MCP results) | a newtype's `Encoder` is its base type's encoder; `Decoder` is the base decoder followed by the constraint | the snapshot files (§9.1); round-trip tests per newtype |
| OpenAPI and MCP input schemas | `Schema[X]` is the base schema `.as[X]`: same `type`, no name, no validator, so no new `components` entry | OpenAPI and `tools/list` snapshots |
| agent toolbox schemas | derived from the same `Schema` | toolbox snapshot |
| DB columns | `DbCodec[X]` maps the base codec. Reads of a constrained type use the checked constructor and fail with the column and value (Q11); live data has 0 violations | DB suites; the slice's live check (`histmersion query`, read-only) |
| HOCON keys and env overrides | readers depend on field names only; package moves do not rename keys | `ConfigSuite`, the settings view snapshot |
| CLI flags | decline `Argument[X]` from the base argument | the CLI help snapshot (`tools/gen-cli-docs.sh` output) |

One given per library derives every newtype's instance from its base. Iron 3.4.1 exposes `RefinedType.Mirror[T]`
with `BaseType`, `ConstraintType` and `IronType` (checked in the 3.4.1 jar). iron-circe uses the same shape:

```scala
package com.histmersion.util.json        // api module, JVM + JS

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.{:|, Constraint, RefinedType}
import io.github.iltotore.iron.*
import sttp.tapir.Schema

object NewtypeCodecs:
  inline given encoder[T](using m: RefinedType.Mirror[T], base: Encoder[m.BaseType]): Encoder[T] =
    base.asInstanceOf[Encoder[T]]                                    // opaque: T erases to BaseType
  inline given decoder[T](using m: RefinedType.Mirror[T], base: Decoder[m.BaseType])(using
      inline c: Constraint[m.BaseType, m.ConstraintType]): Decoder[T] =
    base.emap(_.refineEither[m.ConstraintType]).asInstanceOf[Decoder[T]]
  inline given schema[T](using m: RefinedType.Mirror[T], base: Schema[m.BaseType]): Schema[T] =
    base.as[T]
```

The same shape covers `DbCodec` (`util.database.NewtypeColumns`), `ConfigReader` (config's lenient base readers,
so `"30"` from an environment variable still reads as an `Int`), decline's `Argument`, and Chimney (already
chimney-iron). These are casts over opaque types, not reflection, so native-image needs no metadata for them. Hand
instances stay only where a message must be kept (`Citekey.parse`, `BookmarkId.parse`); the snapshot test catches
any drift. The exact `Constraint` summoning is confirmed in slice 1. If it does not compile in this form, the
fallback is one explicit line per newtype on the same pattern; the result on the wire is the same either way.

**Where a raw value is parsed (Q1).** Today the endpoints take raw strings and the handlers produce the error text
(`invalid citekey 'X' — lower-case, …`). Tapir's default decode failure answers in plain text instead. To keep
error responses identical, **output records carry the constrained newtypes, and input records keep the raw
representation as a `Pure` newtype** (`CitekeyInput`, …). The controller is the one parse boundary, as rule 2
asks. Moving parsing into decoders, with a decode-failure handler that answers `ApiError(message)`, is a later,
visible change for the user to decide on.

## 5. Operations onto types (#7903, #7908)

### 5.1 Rules

| rule | a top-level object … | goes to | count |
|---|---|---|---:|
| O1 | holds functions about one type, often named as its plural (`ApprovalModes`, `Citekeys`, `Years`, `JobActions`) | that type's companion, or an extension in the module that may know it (rule 3) | 160 |
| O2 | holds `val X = "…"` constants of a closed vocabulary (`GoalStatus`, `MediaRole`, `AttestationTarget`) | an `enum` with `wire` strings | 32 |
| O3 | is a namespace for a feature's types (`api.Agents`, `api.Managers`, `core.ReviewArticle` with 36 nested types) | dissolves: the types become top-level in the feature package; its functions follow O1 | 51 |
| O4 | parses one upstream's JSON (`MetMuseum`, `Europeana`, `AcquireParse`, `PeriodO`, `Lpf`) | the companion of the record it produces (`MetObject.fromJson`), introduced where missing | in O1/O6 |
| O5 | holds a subdomain's rules over a "facts" record (`GoalFocus.Facts`, `AutopilotPolicy.*Facts`, `SchedulerPolicy.Scored`) | methods of that record, renamed to say what it is (`FocusFacts`) | in O3 |
| O6 | works on a value that has a meaning but no type (`Chunker`, `LatinFold`, `Highlight`, `SafePath`, `Frontmatter`, `ImageSize`, `ServerArgs`) | a new type that carries the functions | 37 |
| O7 | is a built-in plugin's code | its plugin module (§8) | 29 |

| rule | stays standalone because it is … | examples | count |
|---|---|---|---:|
| S1 | an entry point (now a `@main def`, §5.5) | — | — |
| S2 | a repository: the feature's store, package-private | 57 `*Repo` objects | 57 |
| S3 | a group of endpoint values, a CLI command group, a web view | `*Api` → `*Endpoints`, `*Cmd`, Laminar views | 11 + 28 + 166 |
| S4 | a given holder, a generic codec helper or a database primitive | `ValueCodecs`, `VocabularyCodecs`, `db/Codecs`, `Keyset`, `Param` | 11 |
| S5 | a codec of an external format with no value type of ours | `Png`, `Vp8l`, `TerrariumPng`, `Mvt`, `PmTiles`, `VectorTileWriter`, `Csv`, `Diff3`, `HtmlRender` | in 47 |
| S6 | a facade over several types that names the composition | `PluginHost`, `LegacyPlugins`, `ConfigLayers`, `SpiValues` | in 47 |

Totals: 645 objects; 320 stay. Every row is in [`objects.md`](research-domain-model/objects.md).

### 5.2 Notable cases

| object | today | destination | why it matters |
|---|---|---|---|
| `ApprovalModes` | `configured(raw) = ApprovalMode.parse(raw).getOrElse(Manual)` | `ApprovalMode.configured` | the user's own example |
| `Citekeys.valid` | `^[a-z][a-z0-9_]*$` | removed; `Citekey.parse` | disagrees with `Citekey` (no `.`/`-`): two rules for one type |
| `DeepTime` | `year(Long)`, `span(Long, Long)` | `Year.fromLong`, `Period.span` | about `Year`/`Period` |
| `api.Write` | error codes as `Int` constants and input checks | `enum WriteCode`; checks on `SourcePatch`, `FindingInput` | the codes are a vocabulary |
| `api.Wording` | 28 sentences about several types | each to the companion of the type it words | 235 lines of free functions |
| `api.Paging` | `Page` nested plus page arithmetic | `Page` top-level; arithmetic on `Page`/`PageSize` | cross-feature shared type |
| `core.MergePlanner` | 2 425 lines, 9 nested types, 67 functions | `domain.exchange.merge.plan`: `MergePlan.of`, `LedgerEntry`, `Row` companions | the largest object |
| `core.ReviewArticle` | 2 013 lines, 36 nested types | `domain.automation.review` types, rules on `Reviewer`, `Verdict`, `ReviewFinding` | the second largest |
| `server.MarkdownLint` | 1 336 lines | `domain.research.articles.lint`: `FormatFinding`, `Edit`, `LintContext` | prose lint belongs to the article, a research result |
| `*Markdown` (28) | one renderer object per area | a `MarkdownRender[A]` instance on the companion of the wire type it renders (Q14) | "a function about a type" |
| `Chunker` | `chunks(body)` | `util.ocr.Chunker` producing `Chunk`s (`Chunk` is the library's stored chunk) | the split is mechanical (§3.8) |
| `UserPaths` (config) | reads `user.home` | a `UserPaths` value built once in `Main` from the environment | rule 12 |

### 5.3 What is not an O1 move

An extension method, not a companion method, is used when the type lives in a module that must not know the
function's dependencies. Example: `ConfidenceStyle` styles `values.Confidence` for the UI, so it becomes an
extension in `api.ui`; `values` gets no UI code. A function that needs a service (a database read, an HTTP call)
is a service method, not a companion method.

### 5.4 Proposed AGENTS.md rule 14

> **14. No free-standing helper objects.** A function about a type is a method of the type or a member of its
> companion — an extension where the type's module must not know the function's dependencies. A closed set of
> strings is an `enum`. A top-level object without a same-named type is one of: a repository, a group of endpoints,
> CLI commands or views, a holder of givens, a codec of an external format, or a facade that names a composition.
> Entry points are `@main def`s.

### 5.5 Entry points (#7908)

| today | becomes | references to update |
|---|---|---|
| `com.histmersion.server.Main` (`object`, 350 lines: `main` + composition helpers) | `@main def histmersionServer(args: String*)` in `server/Main.scala`; the helpers go to `Composition` (the wiring) and `ServerArgs` (O6) | `build.sbt` `Compile / mainClass`, relink kit `main-class`, `PluginTool` default, `scripts/bundle/tests/{relink,kit-scripts}-test.sh`, `scripts/docs/screenshots.sh`, `PluginToolSuite`, `PluginKitSuite`, `docs/research-plugins.md` |
| `com.histmersion.cli.Main` | `@main def histmersionCli(args: String*)` | `build.sbt`, `tools/gen-cli-docs.sh`, `MainInitSuite`, the plugin template's docs |
| `com.histmersion.web.Main` (Scala.js) | `@main def histmersionWeb()`; `scalaJSUseMainModuleInitializer` stays, `Compile / mainClass` set explicitly | `build.sbt` |
| `server.plugins.PluginTool` | `@main def pluginTool(args: String*)` | `scripts/bundle/relink.sh`, `server.sh`, `server-jvm.sh` |
| `server.agents.GeneratePlugin` | `@main def generatePlugin(root: String)` | `justfile` (`just plugin`) |
| `pluginresolver.PluginResolver` | `@main def pluginResolver(args: String*)` | `scripts/bundle/kit-common.sh` |
| test tools `BasemapLabelsTool`, `BasemapExtractTool`, `PromptBudget` | `@main def`s | their scaladoc usage lines, `scripts/agents/claude-overhead.sh` |

Scala 3 names the generated class after the method. Renaming the classes is safe across upgrades because a relink
always uses the `main-class` file of the kit it came with (Q2). `api.generateApiIndex` is already a `@main def`
and stays.

## 6. Service shape

### 6.1 The template

| piece | where | visibility | owns |
|---|---|---|---|
| domain types, pure rules | `domain.<s>.<f>` (core module when other modules need them, else server) | public or `private[<f>]` | invariants (smart constructors), pure decisions |
| `<F>Service` trait | `domain.<s>.<f>` (server) | public | the feature's operations in domain types |
| `<F>Service.Implementation` | companion | `private final class` | transactions, calls of its repository and of other services |
| `<F>Service.InMemory` | companion | `final class` | the same behaviour over `AtomicReference`s |
| `<F>Repo` | `domain.<s>.<f>` (server) | `private[<f>] object` | SQL; every method takes `(using DbCon)`; never opens a transaction |
| `<F>Controllers` | `domain.<s>.<f>` (server) | public `final class` | parse raw inputs into domain values (the boundary), maintenance guard, call services, convert to wire records (Chimney) |
| `<F>Routes`, `<F>Tools` | `domain.<s>.<f>` (server) | public | bind the feature's Tapir endpoints and Chimp tools to controller methods; nothing else |
| `<F>Endpoints`, wire records, renderers | `api.<s>.<f>` | public | the contract |
| cross-subdomain read controllers | `server.views` | public | one response assembled from several subdomains' services (§3.4) |

`HttpApi` and `McpTools` become lists of the features' routes and tools, **in today's order**. Route matching and
`tools/list` both depend on that order.

Transactions go through one small type in `util.database`. It wraps Magnum's `connect`/`transact` over the pool
and keeps the read-only query pool apart:

```scala
trait Transactor:
  def read[A](work: DbCon ?=> A): A           // connect: one connection, autocommit, no transaction
  def transact[A](work: DbCon ?=> A): A       // one transaction; rolled back on an exception
```

Globals (rule 12) are passed in. The composition root builds `java.time.Clock` (Q3), `Environment` (the
environment map read once in `Main`) and `UserPaths`, and passes them to whatever needs them.
`Instant.now()` becomes `Instant.now(clock)`, `Thread.sleep` becomes a `Sleeper` passed in, and `UUID.randomUUID`
becomes an `IdSource`. Each slice removes the global reads of its feature (the counts are in §2).

### 6.2 Worked example: the work lists (ACQUIRE, TASKS, LOG)

Today (`OpsHandlers.queue*` + `db/QueueRepo` + `api/Queue`): the repository takes a `DataSource` and opens its own
transactions; it returns `ApiError` (an api type) from `patch`/`delete`. The handler reads `LocalDate.now(UTC)` (a
global) to date a log item. Ids are `Int`, the list is a `String` parsed in two places, statuses are `String`
constants. The pure rules (`Queue.applyPatch`, `statusOf`, `render`, `parse`) live in an api namespace object. One
repository serves three owners, and its `forGoal` query joins `goal_links`, another feature's table.

Target: the list mechanism is a kernel in `domain.platform.worklist`; the table stays one table (`queue_items`, no
migration). Each owner constructs the kernel's service for its own list and adds its own rules on top:
`library.acquisition` owns ACQUIRE (and the DOI matching of `acquireNaming`), `automation.tasks` owns TASKS and
`automation.log` owns LOG (whose controller dates a new item's topic). The goal scope leaves the kernel: automation
asks its goals for the linked item ids and reads them with `byIds`.

Target:

```scala
package com.histmersion.domain.platform.worklist

enum QueueList(val wire: String, val file: String, val checkboxes: Boolean):     // was api.Queue.Kind
  case Acquire extends QueueList("acquire", "ACQUIRE.md", true)
  case Tasks   extends QueueList("tasks", "TASKS.md", true)
  case Log     extends QueueList("log", "LOG.md", false)

enum QueueStatus(val wire: String):                                              // was "open" | "done" | "note"
  case Open extends QueueStatus("open")
  case Done extends QueueStatus("done")
  case Note extends QueueStatus("note")

final case class QueueItem(
    id: QueueItemId, list: QueueList, text: QueueText, topic: Option[QueueTopic], status: QueueStatus,
    reason: Option[QueueReason], createdAt: Instant, doneAt: Option[Instant], goal: Option[GoalSlug],
    createdBy: Option[JobId], closedBy: Option[JobId]
):
  def patched(p: QueueItemPatch): QueueItem = …                                  // was Queue.applyPatch

enum QueueScope:
  case Open, All
  case ForJob(job: JobId)
  case Ids(ids: Set[QueueItemId])                     // automation's goal view: the ids goal_links names

trait QueueService:
  def list(list: QueueList, scope: QueueScope): List[QueueItem]
  def add(list: QueueList, item: NewQueueItem): QueueItem
  def patch(id: QueueItemId, patch: QueueItemPatch): Either[QueueError, QueueItem]
  def delete(id: QueueItemId): Either[QueueError, QueueItem]
  def replace(list: QueueList, items: List[NewQueueItem], mode: ImportMode): ImportCounts

object QueueService:
  def apply(db: Transactor): QueueService = Implementation(db)

  private final class Implementation(db: Transactor) extends QueueService:
    def list(list: QueueList, scope: QueueScope) = db.read(QueueRepo.list(list, scope))
    def add(list: QueueList, item: NewQueueItem) = db.transact(QueueRepo.insert(list, item))
    def patch(id: QueueItemId, p: QueueItemPatch) = db.transact:      // read, decide, write: one transaction
      QueueRepo.get(id).toRight(QueueError.NotFound(id)).map: current =>
        QueueRepo.update(current.patched(p))
        QueueRepo.get(id).get                                          // done_at as the database stamped it
    def delete(id: QueueItemId) = db.transact:
      QueueRepo.get(id).toRight(QueueError.NotFound(id)).map(item => { QueueRepo.delete(id); item })
    def replace(list: QueueList, items: List[NewQueueItem], mode: ImportMode) = db.transact:
      val removed = if mode == ImportMode.Replace then QueueRepo.deleteList(list) else 0
      items.foreach(QueueRepo.insert(list, _))
      ImportCounts(items.size, removed)

  /** `clock` stands in for the database's now() (created_at, done_at). */
  final class InMemory(state: AtomicReference[WorkListState], clock: Clock) extends QueueService:
    def add(list: QueueList, item: NewQueueItem) =
      state.updateAndGet(_.queueAdded(list, item, Instant.now(clock))).queue.last
    def list(list: QueueList, scope: QueueScope) = state.get.queueListed(list, scope)   // same order: by id
    …

private[worklist] object QueueRepo:
  def get(id: QueueItemId)(using DbCon): Option[QueueItem] = …
  def list(list: QueueList, scope: QueueScope)(using DbCon): List[QueueItem] = …  // SQL unchanged
  def insert(list: QueueList, item: NewQueueItem)(using DbCon): QueueItem = …     // coalesce(created_at, now())
  def update(item: QueueItem)(using DbCon): Unit = …                // done_at = coalesce(done_at, now())
  def delete(id: QueueItemId)(using DbCon): Unit = …
  def deleteList(list: QueueList)(using DbCon): Int = …

package com.histmersion.domain.automation.log                       // the LOG list's owner

final class LogControllers(queues: QueueService, maintenance: MaintenanceLock, clock: Clock):
  def add(list: String, in: QueueAdd): Either[ApiError, api.worklist.QueueItemView] =
    maintenance.guard("queue/add"):
      for
        l    <- QueueList.parse(list).left.map(ApiError(_))
        item <- NewQueueItem.from(l, in, LocalDate.now(clock)).left.map(ApiError(_))  // the log's dated topic
      yield queues.add(l, item).transformInto[api.worklist.QueueItemView]
  …
```

The SQL text does not change. Two behaviours the snapshots cannot see are kept on purpose and covered by the
contract suite. The database still stamps `created_at` and `done_at` (`now()`), and `InMemory` stamps them from its
clock. A log item's topic is still today's UTC date, but the controller now takes the date from the clock passed in
instead of `LocalDate.now`.

### 6.3 Tests: InMemory is proven, not trusted

```scala
abstract class QueueServiceContract(make: Clock => QueueService) extends munit.FunSuite:
  test("an item marked done keeps its first done time when patched again"):
    val queues = make(fixedClock)
    val t = queues.add(QueueList.Tasks, NewQueueItem(QueueText("read Cline 2014")))
    val done = queues.patch(t.id, QueueItemPatch(status = Some(QueueStatus.Done))).toOption.get
    val again = queues.patch(t.id, QueueItemPatch(text = Some(QueueText("read Cline 2014, ch. 3")))).toOption.get
    assertEquals(again.doneAt, done.doneAt)
    assertEquals(queues.list(QueueList.Tasks, QueueScope.Open), Nil)

class QueueServiceInMemorySuite extends QueueServiceContract(clock =>
  QueueService.InMemory(AtomicReference(WorkListState.empty), clock))

class QueueServiceDbSuite extends QueueServiceContract(_ =>              // gated on HISTMERSION_TEST_DB_URL
  QueueService(TestDb.transactor()))
```

Every service gets one contract suite run against both implementations. That is what makes "behaves exactly like
the database" a tested fact, as #7490 asks. Tests seed an `InMemory` by calling its own service methods
(`queues.add`, `goals.put`). There are no seeding back doors and no mocking library. Controllers are unit-tested
over `InMemory` services with no server and no database, as in `LogControllersSuite`, which checks the dated log
topic with `Clock.fixed`.

Where real services share data, the `InMemory` ones share one state record per subdomain, held in one
`AtomicReference` and updated through lenses (rule 8). Across subdomains they never share state: an `InMemory`
service of a higher subdomain is given the `InMemory` services of the lower ones and calls them, exactly as the
`Implementation` does.

| state record | shared by |
|---|---|
| `PlatformState` | jobs, identity, work lists |
| `AtlasState` | places, periods, events, evidence |
| `MediaState` | assets, links, checks |
| `LibraryState` | sources, findings, citations, labels, candidates, acquire checks |
| `ResearchState` | articles, versions, links, coverage, review state |
| `AutomationState` | runs, steps, approvals, managers, autopilot, schedules, goals |
| `ExchangeState` | merge sessions, decisions, ledger |
| `PersonalState` | personal (exists) |

### 6.4 Controllers and the caller

Each controller method and each writing service method takes the caller (`Caller.Person(user: UserId, role: Role)`
or `Caller.Automation(run: RunId)`). Automation writes to other subdomains with `Caller.Automation`, through the
same service methods a person's request reaches (§3.5). Until `docs/research-multi-user.md` is built, a person is
always `Caller.Local`. This is the one place the multi-user work will
change, as that design already assumes for `PersonalControllers`.

## 7. Module layout after the change

### 7.1 sbt projects

Packages follow §3: `domain.<subdomain>.<feature>` in `core` (the pure part) and `server` (services, repositories,
controllers, routes, tools), `api.<subdomain>.<feature>` in `api`, `config.<subdomain>` in `config`.

| project | holds (packages) | depends on | change |
|---|---|---|---|
| `values` (JVM + JS) | `values`: identifiers, values, vocabulary enums, generic quantities, `LicenceId`, `UserId` | iron | grows (§4) |
| `api` (JVM + JS) | `api` root: shared wire types (`ApiError`, `WriteCode`, `Page`, `Filter`, `BBox`, `TimeSpan`, Markdown primitives); `api.<subdomain>.<feature>`: records, `<F>Endpoints`, renderers; `util.json` | values | `Api.all` = the features' lists in today's order |
| `config` | `config` root (`AppConfig`, loading, layers); `config.<subdomain>` settings records | values | flat package split by subdomain; step 2's quantities move to values |
| `files` | `files` (bundle format, notes, merge file, plugin set), `util.files` | api | unchanged in scope |
| `core` (JVM) | the shared kernel `domain` (`Period` helpers, `Licence`, `Attribution`, `Caller`) and the pure part of `domain.<subdomain>.<feature>` | values, files (and through `files` api, for the bundle records) | its logic stops using api's wire records, subdomain by subdomain (Q9) |
| `db` | `util.database` only: pool, `Transactor`, Flyway runner, generic codecs | config | **repositories, `FilterSql`, `PeriodSql`, `SiteTypesSql`, the bundle and merge stores and the seed importer leave** |
| `server` | `server` (composition root, adapters, `server.views`); `domain.<subdomain>.<feature>` services, repositories, controllers, routes, tools; `util.ocr`; `resources/db/migration` (95 files) | core, db, api, plugin-host, `upstreams`; plugin modules **`% Runtime` only** | gains the repositories and the migrations |
| `upstreams` *(new)* | `upstreams`: parsers and clients of public upstreams (OpenAlex, Crossref, Unpaywall, CORE, DataCite, archive.org, Europe PMC, arXiv, Wikidata, Commons, JATS, landing-page rules, catalogue dating) over `PluginHttp` | plugin-api, circe (plugin platform) | from `plugin-host/AcquireParse` & friends, `core/RepositoryLanding`, `Jats`, `Commons`, `Wikidata`, `CatalogDating`, `server/acquire` clients |
| `plugin-api` | the SPI | sconfig, kindlings, iron | ten additive members (§8.3) |
| `plugin-host` | host side of the SPI | core, config, plugin-api, upstreams | its acquisition parsers move to `upstreams`; it uses the shared dating rules from there |
| `plugin-testkit` | unchanged API | plugin-host | its published dependency set changes with plugin-host's |
| `plugins/fetchers` *(new)* | `archive-org`, `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`, `arxiv` | plugin-api, upstreams | from `server/acquire/fetchers` |
| `plugins/events` *(new)* | `events-gvp`, `events-noaa`, `events-wikidata` | plugin-api, upstreams | from `core/EventProviders`, `server/events/BuiltinCatalogues` |
| `plugins/gazetteers` *(new)* | `gazetteer-cliopatria`, `gazetteer-awmc`, `gazetteer-chronontology` | plugin-api, upstreams | from `server/importers/*Importer` (Q23) |
| `plugins/media` *(new)* | `media-wikidata`, `media-depicts`, `media-met`, `media-cleveland`, `media-aic`, `media-loc`, `media-pleiades`, `media-opencontext`, `media-smithsonian`, `media-europeana` | plugin-api, upstreams | from `server/media` collections and `core` parsers |
| `plugins/ai-http` *(new)* | `ai-anthropic`, `ai-openai`, `ai-local` | plugin-api, circe | from `server/agents/LlmClient`'s provider part |
| `plugins/ai-cli` *(new, Q5)* | `ai-claude-code`, `ai-codex`, `ai-opencode` | plugin-api | from `server/agents` runtimes |
| `plugins/vaults` | `keepassxc`, `1password`, `bitwarden` | plugin-api | directory moved from `plugin-vaults`; artifact name kept |
| `cli`, `web`, `plugin-resolver`, `plugin-fixtures/*`, `chimney-iron*` | | | unchanged except edges (slice 12) |

```
values       → iron
api          → values                       (JVM + JS)
config       → values
files        → api
core         → values, files
db           → config                        (no longer core)
plugin-api   → sconfig, kindlings, iron
upstreams    → plugin-api, circe
plugin-host  → core, config, plugin-api, upstreams
server       → core, db, plugin-host, upstreams;  plugins/* and plugin-vaults % Runtime
plugins/*    → plugin-api (+ upstreams)
cli          → api, config, files            (never core, db or server)
web          → api                           (JS)
```

Inside `core` and `server`, the subdomain table of §3.3 holds, checked by `ArchitectureSuite`. Splitting `server`
into one sbt project per subdomain would make the compiler check it and let sbt compile subdomains in parallel; it is
Q18, and mechanical once the packages are clean.

### 7.2 Consequences

- The server depends on the plugin modules `% Runtime`, so no server code can import a plugin's class. That is
  #7911's "server depends on them only for bundling", enforced by the compiler. `server/nativeImage`, the relink
  kit and `pluginRepo` read `Runtime / fullClasspath` instead of `Compile / fullClasspath`. AGENTS.md already
  plans that change for chimney-iron.
- Migrations: the files move from `modules/db/src/main/resources/db/migration` to the same path in `server`,
  unchanged, so Flyway checksums and `Migrate.Replaced` stay valid. The index generator moves with them (Q12). Each
  table's owner is recorded in §3.1, not by splitting files: many migrations touch several subdomains' tables, and
  applied migrations are never edited. `queue_items` stays one table whose rows are owned per list.
- Package moves rename classes, and `reachability-metadata.json` names classes. Every slice re-records the
  metadata with the pinned JDK (server `run` against an empty database, the CLI, and the plugin modules' own
  metadata files) and proves it with the bundle's `--self-check` and `smoke.sh`.
- `pluginRepo` also publishes `upstreams`, because plugin-host, which the testkit runs, uses its dating rules.

## 8. Plugin separation (#7911)

Acquiring is each subdomain's own business: the library acquires sources, the atlas acquires data points, media
acquires pictures. In each the **interface and the running of it** are domain code — the fetch chain, its quota,
the ACQUIRE list, recheck, discovery and candidates (`library.acquisition`); the catalogue and gazetteer import
loops (`atlas.events`, `atlas.places`); the collection import loop and its licence policy (`media`). The
**implementations** — fetchers, catalogues, collections, gazetteer importers — are plugins.

### 8.1 What each built-in needs from the server today (measured)

"Moves" counts the files and lines that leave the server, core and plugin-host. "Host symbols" counts the distinct
non-SPI types those files import from `server`, `domain`, `config`, `api`, `pluginhost`, `db` and `values`.

| family | moves | host symbols | what it needs that `plugin-api` does not offer |
|---|---|---:|---|
| fetchers (7) | 28 files, 2 351 lines | 21 | **F1** endpoints and landing rules from `acquire.*` (`AcquireConfig`, `LandingConfig`); **F2** the server's `Http` with JSON, status and headers (`Http.once`, `HttpAnswer`), not only `PluginHttp.get` bytes; **F3** clients the host also uses: `OpenAlexCrossref` (discovery), `ArchiveOrg` (`archive search/info/import`), `CoreApi` (discovery's CORE search); **F4** the CORE budget, which is server-wide state persisted in `upstream_quota` and spent by discovery too (`CoreBudget`); **F5** parsers in plugin-host and core (`AcquireParse`, `PmcRecord`, `EuropePmcHit`, `RepositoryLanding`, `Jats`, `CoreQuota`); **F6** registration state shared between plugins: `oa-landing` reads Unpaywall's and OpenAlex's records only while their plugins are registered (`OaIndex.unpaywallRegistered`, the `LandingGroup` carry rule) |
| events (3) | 5 files, 647 lines | 19 | **E1** `events.<catalogue>.*` settings, read at each import; **E2** `events rederive` re-parses a built-in's stored `raw` (a plugin's `raw` is never parsed); **E3** the dating rules (`CatalogDating`), which the host also applies to third-party records (`PluginEvents.derive`); **E4** the marker design: `HostEventCatalogue.read` is never called, and the GVP/NOAA/Wikidata branches live in `EventCatalogHandlers` |
| media (10) | 28 files, 2 426 lines | 27 | **M1** the database: `WikidataCommons`/`CommonsDepictions` run their own loop over `MediaRepo.Target`s and write through the store (`db.MediaRepo`, `Deps`); **M2** event targets: `media-depicts` asks about events, and the SPI asks about places only; **M3** roles: built-ins store `image`, `locator-map`, `plan`, `aerial-view`, `route-map`, `depiction`, but `MediaCollection.Roles` is `object`/`map`/`image` and a collection has one role; **M4** licences: built-ins build rights in host code (`MediaLicence` over Commons or museum metadata) and check them against their own allowlists (`media.<provider>.licences`), while a plugin states a licence that the host classifies and checks against `media.collections.licences` / `per-collection.<name>`; **M5** the match rule "the target's own item" (Wikidata pictures); **M6** `media.<provider>.*` settings, read at each import |
| ai-http (3) | 3 files, 219 lines + the provider part of the agent loop | 6 | **A1** the turn is sttp-ai's `ClaudeAgent`/`OpenAIAgent` in the host. A plugin must build the provider request itself over `AiHttp`, and sttp-ai is not in the plugin platform; **A2** `agents.providers.<name>.*` settings and API keys from the environment |
| gazetteers (3) | 4 files, 329 lines (`server/importers/{Cliopatria,Awmc,ChronOntology}Importer`, `GazetteerImporter`) | 5 (`PlaceInput`, `Place`, `PlaceName`, `PlaceRelation`, `PlaceKind`) | **G1** no SPI type: `GazetteerImporter` (name, description, fetch URL, `transform(json): List[PlaceInput]`) is a host trait whose output is api's `PlaceInput`; **G2** the host loop (`ImportRunner`: download, transform, the point-tolerance merge into places) and the job are host code and stay so; **G3** the licence of each dataset (Cliopatria's since V107) |
| ai-cli (3) | 17 files, 2 739 lines | 16 imported + 25 same-package (`AgentDefinitions`, `RunGrant`, `LlmClient`, `ToolStyle`, `PoolSink`, …); used from ~100 places outside | **C1** a run token bound to the step's tools and the agent MCP URL (`RunTokens`); **C2** generated agent definitions in each runtime's format (`AgentGenerator`, `AgentTrees`, `CodexHome`), also used by `just plugin`; **C3** plan pools and their vendor readers (`PlanPools`: `rate_limit_event`, `codex app-server`); **C4** process launch (`CliProcess`) and the quota-guard mod; **C5** `PlanQuotaRepo` (the database) |

### 8.2 How each gap is closed

Three mechanisms, chosen so that `plugin-api` gains interfaces and plain data only:

1. **Host-supplied settings (no SPI change).** The host gives each first-party plugin a fallback block built from
   the host keys the plugin used to read. The plugin's config is: the user's `histmersion.plugins.<id>` block,
   then the mapped host keys, then the plugin's defaults resource. Users keep writing `histmersion.acquire.unpaywall.url`
   and `HISTMERSION_ACQUIRE_…` exactly as today. One table in the host glue (`FirstPartySettings`: plugin id →
   host paths) drives both the mapping and the reload rule: a plugin is rebuilt when its block or one of its
   mapped host paths changes. That table replaces `Plugins.carried`'s `independent` set and the `LandingGroup`.
   The plugin report keeps listing only the plugin's own block, so it stays byte-identical (Q7).
2. **The `upstreams` library.** One library of upstream parsers and clients over `PluginHttp`, used by the
   plugins and by the host's discovery, archive.org import, JATS conversion and the browser-download landing
   rules. That avoids keeping two copies of a parser in step. It is not a plugin and registers nothing.
3. **Defaulted SPI additions** (§8.3) for what the host must lend: response status and headers, a quota, peers'
   registration, event targets and roles, re-reading a stored record, and, for ai-cli, a runtime interface.

| gap | closed by |
|---|---|
| F1, E1, M6, A2 | mechanism 1 |
| F2 | SPI-1 `PluginHttp.fetch` |
| F3, F5 | mechanism 2 |
| F4 | SPI-2 `PluginContext.quota("core")`; the host backs it with today's `CoreBudget` and `upstream_quota` row, shared with discovery |
| F6 | SPI-3 `FetchContext.registered(pluginId)`, answered per fetch from the generation the fetch is pinned to; the `LandingGroup` carry rule is no longer needed |
| E2 | SPI-7 `EventCatalogue.reread(raw)`; `events rederive` calls it for every catalogue that gives one |
| E3 | the plugin emits the four-point dating itself (`CatalogDating` moves to `upstreams`); the host's `derive` keeps a given span, so the stored result is the same |
| E4, M1 | the host's import loops (`EventCatalogHandlers.runImport`, `CollectionImport`) run first-party catalogues and collections exactly as they run third-party ones; the marker classes and `HostOwned` go |
| M2 | SPI-5 `MediaCollection.searchEvent` + `MediaEvent` |
| M3 | SPI-4 `MediaItem.role` and `MediaCollection.roles`; `Roles` widened to the stored set |
| M4 | the plugin states the licence exactly as Commons or the museum gives it, and the host classifies it with the same `MediaLicence` code. The host's allowlist lookup (`licencesFor(name)`) answers a first-party collection from its old key, `media.<provider>.licences`, so each built-in keeps its own allowlist and its documented key (Q13). The fixture suites decide equality: the same records stored and refused, with the same reasons |
| M5 | SPI-6 `MediaMatchRule.Own` |
| A1 | the plugin implements the Anthropic and OpenAI message APIs over `AiHttp` + circe (platform libraries). The host loop stays sttp-ai's through `PluginAiClient`, as for any plugin provider |
| C1–C5 | SPI-9 `AiRuntime` (Q5); generator, trees, run tokens and plan-pool storage stay host features |
| G1–G3 | SPI-10 `GazetteerSource` + `GazetteerPlace` / `GazetteerName` / `GazetteerRelation` records (the host's `Place` shape, about 20 fields) and the dataset's licence and credit as plain data, as `MediaRights` carries them for media; `ImportRunner` runs a registered source as it runs the three today; their fixture suites (`CliopatriaImporterSuite`, `AwmcImporterSuite`, `ChronOntologySuite`) are the oracle |

Not plugins (they read data shipped with the install, not an upstream someone may want to switch off or replace):
the bundled gazetteer extracts (`GazetteerLoad`, 503 lines: a manifest of LPF files checked by SHA-256, the
`histmersion init` path), the PeriodO presets (`PeriodOFetch` + the PeriodO parser, 214 lines: one source, also
bundled), and Natural Earth land (`NaturalEarthLand`, 63 lines: the land mask the place checks use). They stay in
`atlas` (Q23).

### 8.3 The SPI additions

All additive and defaulted, so a plugin built against SPI 2 still compiles and links. `Spi.Version` stays 2, as it
did through the earlier additions. MiMa is still the pending step 9.

| # | addition | default |
|---|---|---|
| SPI-1 | `PluginHttp.fetch(url, headers): Either[String, PluginHttpResponse(status, headers, body)]` | from `get` with status 200 |
| SPI-2 | `PluginContext.quota(upstream: String): UpstreamQuota` with `take(purpose: String): Either[String, Unit]` and `observe(status, headers): Unit` | an unlimited quota |
| SPI-3 | `FetchContext.registered(pluginId: String): Boolean` | `false` |
| SPI-4 | `MediaItem.role: Option[String]` (with the old `apply` and constructor kept, as was done for `FetchedFile`), `MediaCollection.roles: Set[String]` | `None`, `Set(role)` |
| SPI-5 | `MediaCollection.searchEvent(event: MediaEvent, http)`, `MediaEvent(id, label, wikidata, period)`, `MediaCollection.asks: Set[MediaTarget]` | `Right(Nil)`, places only |
| SPI-6 | `MediaMatchRule.Own` | — |
| SPI-7 | `EventCatalogue.reread(raw: String): Option[CatalogueEvent]` | `None` |
| SPI-8 | `AiProvider` request options the built-ins use (prompt caching, strict tools), as found in slice 4 | off |
| SPI-9 | `AiRuntime` registry (ai-cli, Q5): `generate(definitions): Map[RelPath, String]`, `step(request, McpAccess(url, token), ProcessLauncher): Either[AiFailure, AiStepResult]`, `planReading(http): Option[PlanReading]` | refusing registry |
| SPI-10 | `PluginContext.gazetteerSources: Registry[GazetteerSource]`; `GazetteerSource(title, description, rights, fetch(http): Either[String, List[GazetteerPlace]])` | refusing registry |

### 8.4 What stays true

- **Licences.** A plugin states a licence as plain data (`plugin.Licence`, SPI 2); the host classifies it into the
  shared `domain.Licence` (§3.7) in every subdomain the same way.
- **One ServiceLoader type.** Each plugin module lists its providers in `META-INF/services/com.histmersion.plugin.Plugin`.
  `Plugins.builtins` and the `Builtin`/`BuiltinWith` wrappers go; discovery finds the first-party plugins as it
  finds vaults today.
- **Built in and switchable.** Their jars are in every image (runtime dependencies of the server). Their defaults
  resources move with them (`enabled = true`; `false` for the keyed `media-smithsonian`/`media-europeana`).
  `histmersion.plugins.<id>.enabled` and `HISTMERSION_PLUGIN_<ID>_ENABLED` work as before.
- **First-party status.** A plugin is first-party when its id is in the host's reserved-name table (names only,
  no code dependency) and it was loaded from the image's own class path, not the relink's plugin set. Reserved
  names still keep a third-party plugin off a built-in's name.
- **Order.** The fetch chain's default order (`histmersion.plugins.order`) and the report's order (by id) do not
  change; the plugin-report snapshot checks both.
- **Tests.** `Plugins.offline` (a test's generation) becomes discovery with fetchers excluded, as before. The
  testkit is unchanged, and the first-party plugins' own suites use it like any plugin author would.
- **AGPL.** Every new module inherits `ThisBuild / licenses`.

## 9. Slice plan

### 9.1 How equivalence is shown (every slice)

Slice 1 makes step 2's one-off comparison permanent, as `ContractSnapshotSuite` (server tests, no database; the
CLI help in the cli module's tests). It writes these to `src/test/resources/contract/` and fails on any difference:

| snapshot | source |
|---|---|
| `openapi.json` | the OpenAPI document of `Api.all` (tapir-openapi-docs as a test dependency) |
| `mcp-tools.json` | MCP `tools/list` over the in-process server |
| `toolbox.json` | every agent role's toolbox schemas |
| `cli-help.txt` | the decline help tree (the text `tools/gen-cli-docs.sh` renders) |
| `plugins.json` | the plugin report of the stock generation: ids, versions, provides, states, settings, order |
| `settings.json` | the settings view of `reference.conf` (keys, kinds, defaults) |

In addition, per slice:

- round-trip tests for every new newtype (`decode(encode(x)) == x`, and the JSON of a sample equals the old
  primitive's);
- the feature's service contract suite on `InMemory` and on the database;
- `just test-it`, with the DB suites run uncached (`testOnly *`);
- for slices that move SQL: `flyway validate` against a restored `*_test` copy of the live database, and the live
  constraint check (`histmersion query`, read-only);
- for plugin slices: the recorded HTTP traffic of the fixture suites, the same requests in the same order (testkit
  record mode, before and after);
- the native bundle with re-recorded metadata, `--self-check`, `scripts/bundle/smoke.sh`, then a deploy.

### 9.2 Slices

One subdomain per slice, bottom up, after the groundwork, the template and the two plugin slices. Sizes count the
lines the slice moves or rewrites: S < 2 k, M 2–6 k, L 6–12 k, XL > 12 k. Each slice ends with its rows gone from the
`ArchitectureSuite` exception list.

| # | slice | scope | size | risk | the risk is … |
|---|---|---|---|---|---|
| 1 | **Groundwork** | `ContractSnapshotSuite`; `ArchitectureSuite` with the 121 known exceptions; generic newtype instances (`util.json`, `util.database`); the identifier newtypes in `values`; `Transactor`, `Clock`/`Environment`/`UserPaths` from `Main`; `Caller`; `@main def` entry points (#7908) and every reference; AGENTS.md rule 14, the subdomain table and the "adding an operation" steps rewritten for controllers | M | low | the generic givens' form (fallback in §4.4); the entry-point rename in scripts (the relink tests cover it) |
| 2 | **Personal** (the template) | the four pilots' shape made exact (Clock, `Transactor`, newtypes, contract suites); `Markable` with its five implementers placed in their future packages; `ItemTitles`' joins replaced; per-user `Caller` | S (2 k) | low | the word search over marks (Q21); listing speed with batched `describe` at live volumes |
| 3 | **Plugins I: fetchers and catalogues** | `upstreams`; SPI-1, 2, 3, 7; `plugins/fetchers`, `plugins/events`; host-supplied settings; `Builtin*`, `HostEventCatalogue`, `OaIndex`'s state go; the CORE quota behind SPI-2 | L (6 k) | high | identical requests in identical order (memo keys, landing pages, CORE pacing) and `events rederive` results; gated by recorded traffic and the fixture suites (`OaFetchSuite`, `PmcFetchSuite`, `LandingFetchSuite`, `EventImportSuite`, `PluginCatalogueSuite`) |
| 4 | **Plugins II: media, gazetteers, ai-http** | SPI-4, 5, 6, 8, 10; `plugins/media`, `plugins/gazetteers`, `plugins/ai-http`; the media marker classes go; Wikidata pictures and depictions run through media's collection loop | L (6 k) | high | licences (M4), per-item roles, the agent loop's wire requests; gated by the media and importer fixture suites and by request bodies captured on a local stub of each AI API. The plugin report gains the three gazetteer plugins — the one intended snapshot change |
| 5 | **Platform** | jobs and the `JobKinds` registry; identity with `AliasTables`; the maintenance lock; the work-list kernel (§6.2); the lint runner with `LintRules`; the plugin host glue; `util.ocr` split out | M (4 k + kind registrations across the code) | medium | every job kind re-registered; job detail rendering unchanged (snapshot of `job_status` output per kind) |
| 6 | **Atlas** | places (gazetteer, geocoder, site types, point areas, land, the gazetteer loop), periods, events with corrections and the catalogue loop, evidence, map tiles with `MapLayer`, `PassageSource`; `PlaceId`, `EventId`, `Kilometres`, `Zoom`; `AtlasState` | XL (13 k) | medium | `PlaceRepo` (1 710 lines) and tile timing (performance suite at live volumes) |
| 7 | **Media** | assets, links, IIIF, figures, covers, the import loop and `LicencePolicy`; the shared `Licence` and `Attribution` introduced here | M (4.6 k) | medium | the licence classification is shared from now on; media's fixture suites are its oracle |
| 8 | **Library** | sources, findings, citations, labels, search and query, acquisition (discovery, candidates, recheck moved out of `agents`, fetch chain, ACQUIRE list), ingest over `util.ocr`, Calibre; `SourceReferrer`; `Citekey`, `SourceUid`, `Doi`, `Year`, `Latitude`/`Longitude`, `Title`, … end to end; `LibraryState` | XL (13 k + 2 k of `Handlers`/`McpTools`) | high | the most used types; the search SQL must not change (a snapshot of the SQL the filter suite generates) |
| 9 | **Research** | articles: versions, links, coverage, critique pairing, review state, prose lint, pages; `Slug` | L (5 k + its share of `Handlers`) | medium | the review state written by automation (contract suite) |
| 10 | **Automation** | two deploys. 10a: agents (runs, steps, leases, chains, allowances, plan pools, toolbox, definitions), approvals, the review panel. 10b: pipelines, managers, autopilot, scheduler, goals, TASKS, LOG, activity. Every back door of §3.5 replaced by the owner's service | XL (30 k) | high | the largest area; the toolbox snapshot, the scripted-model suites (`agent-testkit`) and the manager DB suites are the oracle; the batch reads that replace managers' SQL measured at live volumes |
| 11 | **Exchange** | bundle and merge (`MergePlanner`); the carried records read and written through each owner's service or a carried-records interface where a join is needed for speed | L (9.6 k) | medium | merge has the strongest suites in the repository; bundle round-trips of a restored `*_test` copy |
| 12 | **Operations and closing** | backup, maintenance, settings, instance, basemap; `server.views`; `db` reduced to `util.database`; `Handlers`/`OpsHandlers` deleted; `core`'s logic off api records; `cli.<subdomain>` packages and typed client edges; web edges; ai-cli with SPI-9 (if Q5 says now); `ArchitectureSuite` with no exceptions left; AGENTS.md module section rewritten | XL (12 k) | medium | many moves of self-contained code |

Order: the plugins go **before** the subdomains they live in, so their parsers move once, into their modules. The
subdomains go bottom up, so each one moves onto lower subdomains that already have their final packages and
services. Each slice is deployed (bundle, self-check, smoke) before the next starts, and records its as-built
section in this document.

## 10. What this design does not change

- No endpoint, MCP tool, CLI command, config key, environment variable, table or column is renamed or removed.
- No migration is added for the refactor itself. A slice that finds one necessary stops and asks.
- The web page's behaviour; only its edges get types.
- The multi-user work (#7843) comes after; this design only leaves its seam (§6.4).

## 11. Open questions

Q1–Q16 are from the first draft, updated where the revision touched them. Q17–Q27 are new.

| # | question | recommendation |
|---|---|---|
| Q1 | Parse raw inputs in the **controllers** (error texts unchanged; input records carry `Pure` "raw" newtypes) or in the **decoders** (constrained newtypes in input records, plus a decode-failure handler answering `ApiError(message)`, which changes some 400 texts)? | Controllers now; decoders as a separate, announced change later |
| Q2 | Entry-point classes: keep the names (`@main def Main(args: String*)` keeps `com.histmersion.server.Main`) or use Scala-style names (`histmersionServer`, `histmersionCli`, `pluginTool`, …) and update every reference (§5.5)? | Scala-style names; every reference is in this repo and each kit carries its own `main-class` |
| Q3 | The clock: `java.time.Clock` everywhere, or the pilots' `() => Instant`? | `java.time.Clock`: it also serves `LocalDate.now(clock)`, and `Clock.fixed` serves tests |
| Q4 | Plugin modules: one per family (6 + vaults + the `upstreams` library) or one per plugin (29)? | Families: 29 sbt projects would add build time for no isolation the switch does not already give |
| Q5 | ai-cli (`claude-code`, `codex`, `opencode`): build the `AiRuntime` SPI (SPI-9) and move them in slice 12, or keep them as the one host-owned exception? The measured coupling is 25 host types and ~100 call sites | Move them, last: design SPI-9 in slice 10a, move in slice 12; generator, trees, run tokens and plan-pool storage stay automation's. If slice 10a shows SPI-9 needs more than the members sketched, come back with numbers |
| Q6 | One shared `upstreams` library for the host and the plugins, or a copy of each parser on each side? | One library: discovery and the fetchers parse the same OpenAlex, Crossref and CORE records |
| Q7 | Show the mapped host settings in a first-party plugin's report? | No: the report lists the plugin's own block, as today (byte-identical) |
| Q8 | Flags: keep `Boolean` parameters (named) or newtype them too? | Keep `Boolean`; replace a Boolean with an enum only where two or more flags form a mode (`dryRun`/`apply`/`force`) |
| Q9 | Keep `core` as a separate pure module, or fold it into the server? | Keep it: no database, fast tests, and plugin-host depends on it; it also carries the shared kernel (`Licence`, `Attribution`) |
| Q10 | Goal slugs are unchecked today (all 214 live slugs pass the article rule). Apply `Slug`'s rule to `GoalSlug`? | Yes; a new goal with an invalid slug is then refused with the article slug's message |
| Q11 | Reading a constrained newtype from the database: checked (fail with the column and value) or unchecked? | Checked; live data has 0 violations, and a violation should be loud |
| Q12 | Migrations: one directory in the server (unchanged files) or split per subdomain? | One directory, with the owner of each table recorded in §3.1; splitting would mean editing applied migrations |
| Q13 | Media allowlists: built-ins check `media.<provider>.licences`, plugins `media.collections.licences` / `per-collection.<name>`. Keep both key families, or fold the built-ins' keys into `per-collection.<name>`? | Keep both for this step (no key changes), as a `LicencePolicy` over the shared `LicenceFamily`; folding them is a separate settings change |
| Q14 | Markdown rendering: a `MarkdownRender[A]` type class with instances on the api types' companions, or one renderer object per feature? | The type class: it is "a function about a type", and the CLI and MCP share it |
| Q15 | Plugins before the subdomains they live in (slices 3–4 before 5–12)? | Yes, so plugin code moves once |
| Q16 | `cli` (76 files, flat) into `cli.<subdomain>` packages in slice 12, or later? | Slice 12: the move is cheap and the CLI help snapshot guards it |
| Q17 | The name of the places/periods/events subdomain (written `atlas` here; one package segment to rename). Candidates: **`atlas`** — the reference work of places and their history on the map × timeline; **`chronotope`** — exact (time-space) but obscure to most readers; **`gazetteer`** — the field's own word, but strictly an index of places, not of periods and events | `atlas`: short, concrete, already how the web page presents the map × timeline, and it does not suggest places only |
| Q18 | Enforce the direction with `ArchitectureSuite` (a test over imports) or with one sbt project per subdomain (the compiler, as `% Runtime` does for plugins)? | The test during the slices; split `server` into subdomain projects after slice 12 if the build-speed measurement shows a gain — by then it is mechanical |
| Q19 | The evidence that attests data points (attestations, event link candidates and checks, leads) in `atlas` or in `library`? | `atlas`: events use it 34 times and it uses library types twice; in the library it was the largest source of upward references (46) |
| Q20 | A `platform` subdomain at the bottom (jobs, identity, maintenance lock, work-list kernel, lint runner, plugin host), or those pieces in the shared kernel `com.histmersion.domain`? | `platform`: they own tables and services; the shared kernel stays types only |
| Q21 | personal **pulls** (describe at read time; rename, merge and delete followed through uids, uid redirects and tombstones) or owners **push** changes to personal? Pull means a person's word search over marks matches the stored label, refreshed when the item is next shown | Pull: no subdomain has to know personal exists, and the stored label is already a column |
| Q22 | Pages that join several subdomains (source, place and event detail, About, Activity): `server.views` above every subdomain, or the controllers of the highest subdomain involved? | `server.views`: it keeps every subdomain's controllers to its own data, and it is where the direction rule allows everything |
| Q23 | Gazetteer importers: Cliopatria, AWMC and ChronOntology become plugins (SPI-10, 329 lines, three fixture suites), while the bundled extracts (`GazetteerLoad`), PeriodO presets and Natural Earth land stay in `atlas`? | Yes: the three read an upstream a user may switch off or replace; the others read data shipped with the install |
| Q24 | Goals: in `automation` (what the workflows act towards) or in `research` (the person's research goals)? Measured: goals use agents 12, managers 4, autopilot 4; are used by agents 15, managers 12, pipelines 9, autopilot 6, bundle 10, personal 2; co-change goals↔managers 0.22, agents 0.18, autopilot 0.18, approvals 0.18. In `research`, about 20 references would point upward unless the stages, plans and schedules moved out of the goal into automation records that refer to it | `automation` for now: the goal's stages, plans and schedules are workflow state. Revisit when goals gain content of their own (a written aim, its results); then the goal itself moves to `research` and automation keeps its plans and schedules about it |
| Q25 | `automation` is 33 k lines (agents alone 14 k). One subdomain, or two (the agent runtime vs the research workflows)? | One: its features share one vocabulary and change together; its slice deploys in two steps |
| Q26 | The shared `Licence`: classify stored strings on read (no migration), or store the family in new columns? | Classify on read: `classify` is pure and fast, and no column changes |
| Q27 | `exchange` reads and writes every carried table in bulk with direct SQL today (`BundleRepo` 1 015 lines, `BundleImport` 1 151). Through each owner's service, or through a `Carried` interface each owner's repository implements (export rows, import rows, tombstones) that exchange orchestrates in one transaction? | `Carried`: bulk speed and one transaction are what a bundle needs; the SQL for each table then lives with its owner |
