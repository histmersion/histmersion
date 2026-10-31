# Domain model, package by feature, first-party plugins out of the server (refactor step 3, design)

Status: **approved (2026-10-31; every recommendation of §11 accepted). Slice 1 built — §12; slice 2 built — §13; slice 3 built — §14; slice 4 built — §15; slice 5 built — §16; slice 6 built — §17; slice 7 part 1 built — §18.** Written 2026-10-30 on master
58de5489 (branch `domain-design`); revised after the user's reviews of §3: domains above features, measured;
`personal` with its own interface; `library`, `research`, `catalogue`, `media` named; acquisition and OCR
dissolved (2026-10-30); automation as a generic workflow engine fed by the domains, data management and file
access as supporting areas fed by the domains, `platform`, `operations` and `exchange` dissolved (2026-10-31).
Answers review items #7485, #7490, #7903, #7908 and #7911, and the "ad-hoc model" remark.

Inventories too long for this page are in `research-domain-model/`:

| file | what it lists |
|---|---|
| [`dependencies.md`](research-domain-model/dependencies.md) | the measured references between domains and supporting areas (final layout and the earlier ones), every upward reference, the engine's back doors before and after, the per-kind share of today's generic mechanisms, file-system access by place, git co-change |
| [`files.md`](research-domain-model/files.md) | every source file of `server`, `core`, `db`, `api`, `config`, `files` mapped to its target domain or supporting area and feature |
| [`objects.md`](research-domain-model/objects.md) | all 645 top-level objects without a same-named type, each with its destination and a one-line reason |
| [`primitives.md`](research-domain-model/primitives.md) | primitive parameters and fields by name, measured per module, and the counts behind §4 |

`catalogue` is the domain of places, periods and events (Q17, decided 2026-10-30): the catalogue of data points the
map and the timeline show. The name is one package segment (`domain.catalogue`, `api.catalogue`,
`config.catalogue`).

## 0. Summary

1. **Five domains** hold the research and its machinery: `automation` (a generic workflow engine),
   `catalogue` (places, periods, events and what attests them), `media`, `library` (sources and getting them in)
   and `research` (research results; articles today), plus `personal` beside them. Each is
   `com.histmersion.domain.<domain>.<feature>` (§3).
2. **Domains contribute, supporting areas run.** The engine defines workflows, runs, schedules, managers,
   approvals and agents and knows no article or source; research, library and the others register their
   workflow definitions and agent tools into it (§3.5). Data management works the same way: each domain says how
   its data is stored, backed up, exported, carried in a bundle, merged, cached, maintained and checked; one
   supporting area, `util.data`, runs it all from one place and feeds one 360° view (§3.6). Files and directories
   are reached only through `util.storage`'s typed stores, never as paths (§3.7).
3. **`platform`, `operations` and `exchange` dissolve** into supporting areas (`util.jobs`, `util.identity`,
   `util.data`, `util.storage`, `util.plugins`, `util.settings`, `util.basemap`) and the composition root
   (`server.instance`, `server.views`). Of bundle and merge's 7.8 k lines, about 4.8 k are rules about one kind of
   record and move to that kind's domain; `MergePlanner` alone gives 1.75 k of its 2.4 k.
4. **Measured, not asserted**: 616 type references and 402 commits of co-change (`dependencies.md`). With the
   engine and the supporting areas at the bottom, 264 references point upward today, and 229 of them are exactly the
   code that becomes contributions (engine → domains 120, data management → domains 81, plugin glue 12, job kinds
   8, aliases 6); 31 between domains go through five named interfaces; 4 are personal's (§3.4). The engine's back
   doors into domain data drop from 36 repository calls and 55 SQL statements to none (§3.5).
5. **Direction**: supporting areas never import a domain; domains depend only on supporting areas and the
   domains below them (automation, catalogue, media, library, research); personal only on supporting areas. An
   `ArchitectureSuite` enforces it, and also that no file-system API is used outside `util.storage` and the
   composition root.
6. The model stops passing primitives: **about 55 identifier and value newtypes** and 32 vocabulary enums (§4)
   replace roughly 4 000 measured `String`/`Int`/`Long`/`Double` parameters and fields; their JSON, columns, HOCON
   keys, CLI flags and MCP schemas stay byte-identical. `Licence` is one shared domain type (§3.10).
7. Of the 645 type-less top-level objects, 229 move onto a type, 51 namespace objects dissolve into packages, 29
   move into plugin modules, 10 handler groups become controllers, 6 `main` objects become `@main def`s, and 320
   stay standalone for one of six stated reasons (§5).
8. Every service has the shape of AGENTS.md rule 13 (§6), with two worked examples: the engine's work lists, and
   research's compile-article workflow registered into the engine.
9. The 29 first-party implementations in the server, and Calibre from the CLI, move into seven plugin modules beside
   vaults, plus a shared `upstreams` library; eleven additive SPI members close the measured gaps (§8). The same
   "domain defines and runs, plugin implements" shape is measured for twelve more integrations (§8.5), and the
   plugin system gets a CLI side for a server on another machine (§8.6).
10. **12 slices** (§9): groundwork, `personal` as the template, the two plugin slices, the supporting areas, the
    engine, then one domain per slice from the bottom up — each registering its workflows and data contributions
    as it moves. Equivalence is shown by permanent snapshot files (OpenAPI, MCP `tools/list`, toolbox schemas, CLI
    help, plugin report, settings view), codec round-trips, contract suites, and the bundle with `--self-check`.
11. Section 11 has 42 open questions, 4 of them decided or superseded; the ones that change the shape most are Q24 (goals as the
    engine's objectives), Q28 (one data area or two), Q29 (jobs and record identity as supporting areas) and Q5
    (the CLI agent runtimes).

## 1. The review points and where each is answered

| # | asked | answered in |
|---|---|---|
| #7485 | package by feature; `domain`, `domain.<sub>`, `util.<area>`; package-private repos; db = primitives only; services own transactions; no globals; Handlers → Controllers; api split into shared vs API-specific; subdomains independent of endpoints | §3, §6, §7; slices 2, 5–12 |
| #7490 | `private final class Implementation` + `final class InMemory` on the service companion; InMemory behaves like the DB; shared atomic refs; seeded through the service; no mockito | §6 (with a contract suite that proves "behaves like the DB") |
| model | "ad-hoc model, helpers on primitives instead of newtypes" | §4 (catalogue of newtypes, measured), §5 |
| #7903 | no free-standing helper objects; rule 14 | §5, `objects.md`, rule text in §5.4 |
| #7908 | `@main def` instead of `object X { def main }` | §5.5 |
| #7911 | first-party plugins out of the server, one module per plugin or family, `plugin-api` only, ServiceLoader, built in, switchable | §8; slices 3, 4 (and 12 for ai-cli, Q5) |
| §3 review 1 | subdomains above features, measured; no `planning`; `personal` with an interface it owns; direction enforced | §3.1–§3.4, §3.9, `dependencies.md` |
| §3 review 2 | `library`, `research`, `catalogue`, `media` named; acquisition and OCR not domains; licence shared | §3.1, §3.10, §3.11, §8 |
| §3 review 3 | automation a generic engine; domains contribute workflows and tools; managers, goals, the definitions generator; `platform` a bag; maintenance split in two; automation's size | §3.3, §3.5, Q24, Q31–Q33 |
| §3 review 4 | data management (storage, backup, export, bundle, merge, cache, maintenance, checks, figures) contributed by each domain, run by a supporting area with one 360° view; `platform`, `operations`, `exchange` dissolve; merge rules split | §3.3, §3.6, Q27–Q30, Q35 |
| §3 review 5 | no domain touches directories or files; named stores from a supporting area; config places them | §3.7, Q36–Q38 |

Fixed decisions kept everywhere: one ServiceLoader service type (`com.histmersion.plugin.Plugin`); `plugin-api` holds
interfaces and plain data only; our own plugins are built in and can be disabled; everything stays AGPL-3.0-or-later.
User decisions recorded: no `planning` subdomain (goals, TASKS and LOG go to automation, ACQUIRE to the library;
`queue_items` stays one table); `personal` is a domain of its own that knows nothing about its targets; acquisition
is not a domain; OCR, data management and file access are supporting areas; automation is a generic engine fed by
the domains.

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

## 3. Domains, supporting areas and features

### 3.1 The layout

A **domain** is a part of the application with its own vocabulary and its own data; a **feature** is one piece of
functionality inside it. A **supporting area** (`com.histmersion.util.<area>`) is a mechanism with no research
meaning that domains use and feed — like `util.database` today. Neither follows endpoints, tools or commands. Each
table has exactly one owner; only the owner's repository touches it.

Two patterns carry most of the design. Where a mechanism needs knowledge of every kind of record (running agentic
workflows; backing up, carrying, merging, checking data), **the domain contributes the knowledge and a lower place
runs it**: the workflow engine (`domain.automation`) for workflows, the data-management area (`util.data`) for
data. And no domain reaches the file system: it asks `util.storage` for a named store (§3.7).

| layer | place | what it is about (its words) | features | tables owned | lines today by file | moves to plugins | moves in from mechanisms (estimate) |
|---|---|---|---|---|---:|---:|---:|
| domain 1 | `automation` | the workflow engine: workflow definition, run, step, lease, checkpoint, schedule, manager, hint, approval, autopilot, objective (goal), task, log entry, agent runtime, toolbox, model chain, allowance, plan quota | `workflows` (the registry), `agents` (runs, steps, leases, runtimes, toolbox mechanism, definitions generator, chains, allowances, prices, plan pools), `managers`, `approvals`, `autopilot`, `scheduler`, `goals` (Q24), `worklists` (the list kernel; TASKS and LOG), `activity` | `agent_*` (8), `plan_*`, `approvals`, `agent_managers`, `manager_*`, `agent_autopilot`, `goals`, `goal_*` (4); `queue_items` where `list` is `tasks` or `log` | 25 400 | −3 000 (ai) | −5 400 out (upper bound: article- and source-specific blocks of 32 mixed files go to research and library) |
| domain 2 | `catalogue` | data points on the map × timeline: place, name, period, event, dating, evidence, catalogue, gazetteer | `places` (gazetteer, names, overrides, relations, hierarchy, geocoder, site types, point areas, land, the gazetteer import loop), `periods`, `events` (datings, edges, participants, the event-catalogue import loop, corrections), `evidence` (attestations, event link candidates and checks, leads), `maptiles` (tiles, undated sites, `MapLayer`) | `places`, `place_*` (8), `gazetteer_sources`, `land`, `periods`, `seed_versions`, `events`, `event_*` (8), `corrections`, `attestations`, `site_undated_*`, `map_changes` | 14 200 | −1 000 | +900 (bundle, merge, lint, export rules) |
| domain 3 | `media` | pictures and their rights: asset, link, stated licence, credit, collection, figure, cover | `assets` (store, IIIF, thumbnails), `links`, `imports` (the collection loop and its licence policy), `figures`, `covers` | `assets`, `asset_links`, `media_links`, `media_checks` | 7 000 | −2 400 | +200 |
| domain 4 | `library` | what sources say and how they get in: source, citekey, body, finding, citation edge, label, candidate, acquire check | `sources`, `findings`, `citations`, `labels`, `search` (and the read-only query), `acquisition` (discovery, candidates, recheck, fetch chain, upstream quota, the ACQUIRE list), `ingest`, `calibre`, `workflows` (enrich-source, currency check, acquisition sweep, OA backlog, verify, seed, auto-enrich; the watch manager kind; its agent tools) | `sources`, `source_*` (3), `findings`, `finding_places`, `edges`, `unresolved_edges`, `assessments`, `retraction_watch`, `source_candidates`, `candidate_watches`, `acquire_checks`, `upstream_quota`; `queue_items` where `list = 'acquire'` | 16 900 (2 650 of it workflows) | −2 300 | +2 800 rules + up to 3 400 from the engine's mixed files |
| domain 5 | `research` | research results; an article is the one kind today: article, version, link, coverage, critique pairing, review panel, ledger | `articles` (versions, links, coverage, critique pairing, review state, prose lint, pages), `workflows` (review-article with its panel and ledger, compile-article, propose-articles, infer-filter; the review manager kind; its agent tools) | `articles`, `article_*` (4), `review_ledgers` | 10 200 (5 100 of it workflows) | — | +1 000 rules + up to 2 000 from the engine's mixed files |
| beside | `personal` | one person's marks on anything: bookmark, note, read mark, saved view | `bookmarks`, `notes`, `reading`, `marks` | `bookmarks`, `notes`, `read_marks` | 1 700 | — | — |
| supporting | `util.data` | data management: data contribution, backup, restore, bundle, merge session, ledger, maintenance run, check, cache, export, diagnostics, the maintenance lock | `registry`, `backup`, `exchange` (bundle and merge mechanism), `maintenance` (vacuum, reindex, prune, relocation, the lock), `checks` (the lint and health runner), `export` (the Markdown mirror), `diagnostics` (the 360° view's data) | `merges`, `merge_decisions`, `merge_ledger`, `merge_sessions` | 11 800 | — | −5 500 out (per-kind rules to the domains) |
| supporting | `util.storage` | named stores: location, store, blob, scratch file, size | `locations`, `stores`, `scratch`, `sizes` | — (the data directory) | 150 today (`SafePath`, `DirSize`) + every domain's file code | — | +file code from domains (188 uses) |
| supporting | `util.jobs` | background job, job kind, job detail | runner, records, `JobKinds` registry | `jobs` | 840 | — | −260 (kind-specific details to the domains) |
| supporting | `util.identity` | record identity: origin, uid, uid redirect, tombstone, alias | uids, redirects, tombstones, `AliasTables` registry | `instance`, `uid_aliases`, `tombstones`, `alias_tombstones` | 120 + SQL triggers | — | — |
| supporting | `util.plugins` | the plugin generation and registries | generation, legacy settings, plugin set | — | 1 100 | (built-in lists leave) | — |
| supporting | `util.settings` | the settings file: read, edit, reload, restart | | — (the settings file) | 2 000 | — | — |
| supporting | `util.basemap` | the base map: download, PMTiles, terrain, labels | | — (files) | 5 200 | — | — |
| supporting | `util.ocr`, `util.database`, `util.json`, `util.http`, `util.markdown`, `util.text`, `util.images`, `util.tiles`, `util.process` | §3.11 | | | 3 000 | | |
| composition | `server` | the composition root, `server.views` (pages joining several domains), `server.instance` (about, health, version, self-check, UI config and counters) | | `ui_seen` (Q30) | 5 500 + 2 400 of handlers that dissolve | | |

Per-file assignments: [`files.md`](research-domain-model/files.md). The engine's size is answered by the first
pattern: by file it is 25.4 k lines, but up to 5.4 k of those are rules about articles and sources inside 32 mixed
files, and 3 k are AI providers that become plugins — what remains is about 17–19 k lines of engine with no
research vocabulary (Q25).

**`research` and a second kind of result.** Today every result is an article, so `research.articles` is the only
result feature and there is no generic "result" type. A second kind is a sibling package (`research.<kind>`), not a
reshaping: what other code needs from research goes through `ArticleService` and the interfaces research implements
(`Referable`, `SourceReferrer`, its data and workflow contributions). If a second kind came, these would move up
into `research` itself, because they are not article-specific in meaning: the version history, the coverage filter
(which library sources a result covers, with reasons), the review state and the review panel's ledger, `[[citekey]]`
link resolution, and page rendering.

### 3.2 What the measurements decided

Method (details and every table in [`dependencies.md`](research-domain-model/dependencies.md)): a **reference** is a
`src/main` file naming a type that belongs elsewhere, counted once per (file, type); **co-change** is Jaccard over
402 commits; the **per-kind share** of a generic mechanism counts its top-level blocks by the domain words they use.
At the level of domains co-change is weak everywhere (Jaccard ≤ 0.23), so references decide the layering and
feature-level co-change the borderline placements.

| question | decision | evidence |
|---|---|---|
| merge and bundle: operations or a domain? | a mechanism in `util.data.exchange` with **per-kind rules contributed by the owners** | merge↔bundle co-change 0.50; merge's next partners are sources 0.23, articles 0.21, goals 0.19, events 0.15. Of their 7.8 k lines, ≈ 4.8 k are rules about one kind of record (library 2.8 k with `SourceMatcher` and `MergeText`, research 0.9 k, catalogue 0.85 k, media 0.2 k, automation 0.14 k); `MergePlanner` gives 1.75 k of 2.4 k. `util.data` changes with library in 30 commits, catalogue 30, research 21 — new kinds touch the generic code today |
| jobs: automation, platform or support? | **`util.jobs`** | used by library 16, catalogue 6, media 4, data management 10, basemap 5; the agent engine writes runs into `jobs` (36 SQL statements); 0.26 k of 0.65 k are per-kind job details, which become `JobKinds` contributions |
| record identity (uids, redirects, tombstones, aliases) | **`util.identity`** | read by every domain that resolves an id and written by the merge; its only domain knowledge is the alias mechanism's switch over five owners' tables (6 references), which becomes `AliasTables` |
| the maintenance lock (platform) vs vacuum and restore (operations) | **one owner: `util.data.maintenance`**; controllers receive its `WriteGate` | the lock exists for restore, vacuum and merge apply; every write guard only asks it |
| lint runner | **`util.data.checks`**, rules contributed per domain | lint's 1.1 k lines: 0.37 k generic, rules for library 0.46 k, research 0.1 k, catalogue 0.04 k; lint co-changes with places 0.12 (11 commits), sources, media, events |
| plugin host, settings, basemap | **`util.plugins`, `util.settings`, `util.basemap`** | no research data; basemap co-changes only with the instance pages (0.18) |
| about, health, version, self-check, UI config | **`server.instance`** (composition), reading `util.data.diagnostics` | they show every domain's figures; that is a view |
| goals | **the engine's objectives** (`automation.goals`), with the stage rules that name articles and sources contributed (Q24) | 186 upward references with goals in the engine against 226 with goals in research; goals↔managers co-change 0.22, agents 0.18, autopilot 0.18 |
| the review panel and its ledger | **research's workflow** (`research.workflows`) | review↔pipelines co-change 0.44 (15 commits); the ledger is about articles (ReviewArticle names research types 12 times) |
| evidence (attestations, event links, leads) | **`catalogue.evidence`** | events use it 34 times; it uses library types twice |
| map tiles | **`catalogue.maptiles`** with `MapLayer` | catalogue → media 6 references, all tiles and detail pages |
| media and library | **media below library** | library → media 15 (ingest stores figures and originals as assets, covers), media → library 3 |

The final reference matrix (row depends on column), with the supporting areas at the bottom and the domains in the
order automation, catalogue, media, library, research:

| from \ to | automation | catalogue | library | media | personal | research | util.basemap | util.data | util.identity | util.jobs | util.plugins | util.settings |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **automation** | · | 3 | 74 | 0 | 1 | 43 | 0 | 1 | 2 | 6 | 0 | 0 |
| **catalogue** | 0 | · | 11 | 6 | 0 | 0 | 0 | 2 | 4 | 6 | 0 | 0 |
| **library** | 84 | 32 | · | 15 | 0 | 10 | 0 | 8 | 2 | 16 | 2 | 0 |
| **media** | 0 | 11 | 3 | · | 0 | 1 | 0 | 0 | 0 | 4 | 0 | 0 |
| **personal** | 0 | 1 | 0 | 0 | · | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| **research** | 60 | 7 | 34 | 1 | 0 | · | 0 | 14 | 0 | 0 | 0 | 0 |
| **util.basemap** | 0 | 0 | 2 | 0 | 0 | 0 | · | 3 | 0 | 5 | 0 | 0 |
| **util.data** | 11 | 25 | 27 | 5 | 0 | 13 | 2 | · | 7 | 10 | 0 | 1 |
| **util.identity** | 2 | 2 | 1 | 0 | 0 | 1 | 0 | 0 | · | 0 | 0 | 0 |
| **util.jobs** | 0 | 4 | 4 | 0 | 0 | 0 | 2 | 6 | 0 | · | 0 | 0 |
| **util.plugins** | 1 | 1 | 9 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | · | 1 |
| **util.settings** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 1 | · |

Library → automation (84) and research → automation (60) are now downward: they are the workflows those domains
contribute to the engine. Before/after, by layout (`dependencies.md` has each matrix):

| layout | references | upward | what the upward ones are |
|---|---:|---:|---|
| starting proposal (corpus, geography, writing, media, acquisition, automation, operations) | 622 | 135 | 71 of them a core subdomain into automation or operations |
| second draft (automation and exchange/operations on top, platform at the bottom) | 546 | 121 | interfaces still to introduce; automation reading domain data through 36 repository calls and 55 SQL statements |
| **final** (engine and supporting areas at the bottom) | 616 | 264 | **229 are the contributions themselves** — the engine's 120 references into library, research and catalogue, data management's 81, plugin glue 12, job kinds 8, aliases 6, a name collision 2 — **31** between domains, **4** personal; §3.4 |

The final layout has more upward references than the second draft because putting the engine and data management
at the bottom exposes every rule about articles, sources and events they hold today; each such reference is a line
that moves into the domain it is about. After the slices, the upward count is 0.

### 3.3 Direction, and how it is enforced

| place | may depend on |
|---|---|
| any `util.<area>` | other supporting areas, the shared kernel (`com.histmersion.domain` root types), `values` — never a domain |
| `automation` | supporting areas |
| `catalogue` | supporting areas, automation |
| `media` | supporting areas, automation, catalogue |
| `library` | supporting areas, automation, catalogue, media |
| `research` | supporting areas, automation, catalogue, media, library |
| `personal` | supporting areas only |
| any domain | the interface packages higher domains own for it to implement (`personal.marks`, `library.sources.referrers`, `catalogue.maptiles.layers`, `catalogue.evidence.passages`) |
| `server` (composition root, `server.views`, `server.instance`) | everything |

**`ArchitectureSuite`** (server tests, no database, seconds) reads every Scala source under `com/histmersion/` in
`core`, `server`, `db`, `files` and `config`, takes its place from its package, and fails, naming file and line, on:

1. an import or fully qualified name of `com.histmersion.domain.<x>` or `com.histmersion.api.<x>` the table does
   not allow (a cycle is impossible: the table is a DAG);
2. a file-system API — `java.nio.file`, `java.io.File`, `FileChannel`, `RandomAccessFile`, `File*Stream`,
   `MappedByteBuffer` — outside `util.storage`, the composition root and the stand-alone tools (`pluginTool`,
   `generateApiIndex`, `generatePlugin`); and `ProcessBuilder` outside `util.process`;
3. a repository object referenced from outside its feature's package (the rule-13 check, for free).

From slice 1 it runs with the list of known violations by file (the 264 references, the 188 file-system uses in
domains, §3.7); each slice removes its rows and may add none. One sbt project per domain would make the compiler
check rule 1 (Q18).

### 3.4 The upward references and what removes them

| group | refs | what removes it | slice |
|---|---:|---|---|
| the engine naming library, research and catalogue types (`AgentsHandlers` 15, `ManagerSupervisor` 12, `Toolbox` 8, `ManagersHandlers` 7, `AgentsConfig` 6, `Scheduling`, `AgentsMarkdown`, `AnswerSchemas`, `CompareRuns`, `ManagerPolicy`, …) | 120 | the workflow, tool, manager-kind and objective-progress contributions (§3.5) | 7, then each domain |
| `util.data` naming domain types (bundle and merge per-kind code, lint rules, storage figures) | 81 | `DataContribution` (§3.6) | 6, then each domain |
| `util.plugins` naming built-ins (`Plugins`, `LegacyPlugins`) | 12 | the plugin slices | 3–4 |
| `util.jobs` rendering domain job details (`JobKind`, `JobDetail`, `JobActions`) | 8 | `JobKinds`: a job kind is a newtype; each owner registers its kinds and renders its detail | 5 |
| `util.identity` switching over owners' alias tables (`AliasRepo`, `AliasKind`, `ResolvedId`) | 6 | `AliasTables`: each owner registers its alias table | 5 |
| catalogue pages and tiles needing media and library (`EventDetail`, `PlaceDetail`, tile features, places tree counts, the passage search over source bodies when events are linked) | 17 | `MapLayer` (catalogue owns; library and media implement), `PassageSource` (catalogue's evidence owns; library implements); detail pages in `server.views` | 8 |
| media needing library (source covers, figures from a source's PDF) | 3 | covers and figures are `library.ingest` steps that store into media | 9–10 |
| library naming research (rename and delete touching article links, `ArticleRepo`) | 10 | `SourceReferrer` (library owns; research, media and automation implement) | 10 |
| media naming research, `util.basemap` naming library, automation naming personal (name collisions) | 4 | nothing | — |
| personal joining five tables by name (`ItemKind`, `ItemTitles`) | 3 | `Referable` (§3.9) | 2 |

A page that joins several domains for one response (a source's, a place's or an event's detail page, About,
Activity, the 360° data view) is a **view**: a controller in `server.views`, above every domain, that calls their
services. No domain assembles another domain's data for display.

### 3.5 Automation: a generic engine the domains feed

The engine defines and controls workflows, agents and schedules. It owns runs, steps, checkpoints and leases, the
scheduler, managers and their hints, approvals, the autopilot, the agent runtimes, the toolbox mechanism, model
chains, allowances, prices and plan quotas, the TASKS and LOG lists, activity, and goals as objectives. It knows no
article, source, place or picture. Domains register what is theirs, as plugins register into the plugin host:

```scala
package com.histmersion.domain.automation.workflows        // the engine's contribution interfaces

/** A kind of workflow a domain defines: its roles, its steps, what it answers, how it ends. */
trait WorkflowDefinition:
  def id: WorkflowId                                         // "compile-article", "enrich-source", …
  def roles: List[AgentRole]                                 // models, prompts and budgets come from the engine
  def allowedTools(role: AgentRole): Set[ToolName]           // the ONLY tools this role's agents see and may call (Q31)
  def start(request: WorkflowRequest, run: RunContext): Either[WorkflowError, WorkflowPlan]
  def step(state: StepState, answer: StepAnswer, run: RunContext): StepOutcome   // next step, checkpoint, done, halt
  def settled(result: RunResult, run: RunContext): Either[WorkflowError, Settled] // writes through its own services

/** An agent tool a domain offers to the engine's toolbox: a schema and a call into its own services.
  * Offering a tool grants nothing: a workflow names the tools each of its roles may use (`allowedTools`), possibly
  * tools of other domains (compile-article in research uses library's `get_source`); the engine shows an agent only
  * those, refuses any other call, and fails the workflow's registration if it names a tool nobody contributes. */
trait ToolContribution:
  def name: ToolName                                         // "get_source", "check_edits", …
  def description: String
  def inputSchema: JsonSchema
  def call(input: Json, caller: Caller.Automation): Either[ToolError, ToolResult]

/** A kind of manager (review, watch, …): which items it keeps, how it picks the next one, what settles an item. */
trait ManagerKind:
  def kind: ManagerKindId
  def items(manager: ManagerInfo, now: Instant): List[ManagerItemCandidate]
  def dispatch(item: ManagerItem): WorkflowRequest
  def settle(item: ManagerItem, result: RunResult): ItemStatus

/** How a domain measures progress towards an objective (goal) in its own terms. */
trait ObjectiveProgress:
  def facts(goal: GoalRef): ObjectiveFacts                   // e.g. articles compiled and reviewed, sources enriched
```

The composition root builds one `Workflows` registry from every domain's contributions and hands it to the engine;
the agent definitions generator (Claude Code, Codex and opencode trees, `just plugin`) renders the canonical skills
and agents plus the contributed roles and tools — the generator is the engine's, its input is contributed.

| contributed by | workflows | tools | manager kinds | lines today |
|---|---|---|---|---:|
| `research` | review-article (with its panel and ledger), compile-article, propose-articles, infer-filter (article coverage) | `get_article`, `check_edits`, `article_coverage` | review | 5 100 by file + its share of the mixed files |
| `library` | enrich-source, currency check, acquisition sweep, the OA backlog and the acquire recheck, verify (quotes against bodies), seed, auto-enrich | `get_source`, `passages`, `search`, `facets`, `neighbors`, `query`, `followups` | watch | 2 650 by file + its share of the mixed files |
| `catalogue` | none today: event linking and place review run deterministically. A future agentic workflow registers the same way | — | — | — |
| `media` | none today | — | — | — |

**The engine's back doors into domain data**, measured (`dependencies.md`):

| | second draft | after the workflows move to their domains | after the design |
|---|---:|---:|---:|
| calls into other domains' repositories | 36 in 15 files | 10 in 3 files (`PeriodSql` 5 in `GoalRepo`; `ReviewLedgerRepo` 2 and `ArticleRepo` 3 in `ManagerSupervisor` and `ManagersHandlers`) | 0 — the period filter uses `util.database`'s range SQL; the review manager is research's `ManagerKind` |
| SQL in engine repositories over domain tables | 55 (`articles` 10, `article_versions` 1, `sources` 3, `queue_items` 5, `jobs` 36) | 14 (`articles` 10, `article_versions` 1, `sources` 3 in `ManagerRepo`, `GoalLinkRepo`, `AgentRepo`) | 0 — goal links show their items through `Referable`; managers' article queries go with the review kind; runs are recorded through `util.jobs` |
| writes into domains | 11 calls of `Handlers` + the toolbox | — | the same operations on the owners' services with `Caller.Automation(run)`, from inside the contributed workflows and tools |

**Managers and hints.** The supervisor loop, items, holds, retries, cron, approvals settlement and hints are the
engine's (`ManagerSupervisor` is 76 % domain-specific by the block scan, mostly `evaluateReview`, `afterDone`,
`chainReview`, `staleArticles`); the kinds are contributed.

### 3.6 Data management: each domain says how, `util.data` runs it from one place

Every domain contributes one definition of the data it owns; `util.data` holds the registry, runs every operation
across all contributors (one backup, one bundle export or import, one merge session, one maintenance run, one
transaction where the operation needs it), owns the maintenance lock, and collects diagnostics.

```scala
package com.histmersion.util.data

/** What a domain says about the data it owns. Every member has a default that says "nothing of that kind". */
trait DataContribution:
  def owner: DataOwner                                        // "library", "catalogue", …
  def tables: List[OwnedTable]                                // for storage figures, vacuum, reindex, restore checks
  def stores: List[Location] = Nil                            // its file stores (§3.7): backed up, sized, pruned
  def carried: List[CarriedKind] = Nil                        // bundle: export rows, import rows, tombstones, in one transaction
  def mergeRules: List[MergeRules] = Nil                      // per kind of record: match, fold, conflicts, ledger entries
  def mirror: List[MirrorExport] = Nil                        // the Markdown mirror's notes for its records
  def caches: List[CacheSpec] = Nil                           // what to invalidate when, and how big it is
  def maintenance: List[MaintenanceTask] = Nil                // prune, rebuild (tiles, undated cells), reindex
  def checks: List[CheckRule] = Nil                           // its lint rules and health checks, with safe fixes
  def figures(using DbCon): DataFigures                       // counts and sizes for the 360° view
```

| operation | today | the mechanism (`util.data`) | contributed by each domain |
|---|---|---|---|
| backup and restore | `BackupService` (pg_dump, 476 lines, 359 generic) | the dump, the lock, the backups list and housekeeping | its file stores to include (originals, assets, basemap is not research data) |
| bundle (`.hmx`) | `BundleRepo` 1 016, `BundleImport` 1 152, `BundleExport`, `files/Bundle*` | the tar, the canonical JSON, the manifest, one transaction, the importing flag | `CarriedKind`s: its rows out and in, its tombstones |
| merge between databases | `MergePlanner` 2 426, `MergeFold`, `MergeState`, `MergeRepo`, `SourceMatcher`, `MergeText` | sessions, decisions, the ledger, the plan, 3-way text (`Diff3`), apply as a job | `MergeRules` per kind: sources and findings (≈ 1.05 k of the planner + `SourceMatcher`, `MergeText`), articles (≈ 0.3 k), events and places (≈ 0.3 k), goals (≈ 0.07 k), assets |
| maintenance | `VacuumJobs`, `VacuumRepo`, `StorageProbe`, `StorageCache`, `PgRelocation` (606 lines, 422 generic) | vacuum, reindex, relocation, the lock (`WriteGate`) | its tables and caches (tile cache, undated cells, thumbnails, storage cache) |
| checks | `LintRepo`, `api/Lint` (1.1 k, 0.37 k generic) | the runner, safe fixes, the report | its rules |
| export to the Markdown mirror | `files/Export`, `ArticleNote`, `Frontmatter` (418 lines, 198 generic) | the directory layout and frontmatter writing | the notes of its records |
| storage figures | `StatsRepo`, `StorageProbe`, `DirSize` | collection and the report | its tables, stores and caches |

**One place, a 360° view.** About › Storage, backups, bundles, merges, vacuum, lint and health become views over
the registry: per domain its size, counts, cache state, last backup and export, check findings and health. The
existing endpoints, MCP tools and CLI commands stay byte-identical — they are now computed from the registry and the
snapshots prove it. A new combined page in the web UI is later UI work, not part of this refactor (Q35).

### 3.7 Files and directories: `util.storage`

No domain touches `java.nio.file`, `java.io.File`, paths or the data directory. A domain asks for a **named store**
and gets a typed handle:

```scala
package com.histmersion.util.storage

/** The data directory's named places; config decides where each lives (`HISTMERSION_DATA_DIR` is read once, in Main). */
enum Location:
  case Originals, Spool, Assets, Thumbnails, Figures, TileCache, Basemap, Backups, Bundles, Exports,
       GazetteerExtracts, AgentHomes, Plugins, RelinkKit, UserConfig

opaque type StoreKey = String                               // a safe relative name: no "..", no absolute path

trait Store:
  def put(key: StoreKey, content: Content): Either[StoreError, Stored]          // temp file + atomic move
  def get(key: StoreKey): Option[Blob]
  def list(prefix: StoreKey = StoreKey.root): List[Stored]
  def delete(key: StoreKey): Either[StoreError, Unit]
  def size: ByteCount
  def prune(rule: PruneRule): PruneReport

trait Blob:
  def size: ByteCount
  def sha256: Sha256
  def open(): InputStream                                     // big files stream
  def readAt(offset: ByteOffset, length: ByteCount): Array[Byte]   // FileChannel / memory map inside

trait Storage:
  def store(location: Location): Store
  def scratch(): Scratch                                      // temp files, removed when the scratch closes
```

Measured file-system use in main code (`dependencies.md` has the per-file lists):

| place | uses | files | what it does | after |
|---|---:|---:|---|---|
| automation | 100 | 10 | agent homes and generated trees (`AgentTrees` 20, `CodexHome` 18, `GeneratePlugin` 12), CLI runtimes' working directories and processes (`CliProcess` 20), plan readings (`PlanPools` 8) | `Store(AgentHomes)`; processes through `util.process` with store handles |
| library | 51 | 9 | ingest uploads and originals (`IngestHandlers` 21, `Originals` 9, `StoredOriginals` 7), the seed importer, frontmatter files | `Store(Originals)`, `Store(Spool)`, `Store(Exports)` |
| catalogue | 31 | 2 | the bundled gazetteer extracts (`GazetteerLoad` 27), place seeds | `Store(GazetteerExtracts)` (read-only) |
| media | 6 | 2 | figures and thumbnails | `Store(Figures)`, `Store(Thumbnails)` |
| research, personal | 0 | 0 | — | — |
| `util.basemap` | 119 | 11 | PMTiles reads and writes (`FileChannel` 14), the disk LRU, GEBCO parts | `Store(Basemap)`, `Store(TileCache)`; `Blob.readAt` covers the PMTiles range reads |
| `util.data` | 91 | 8 | pg_dump and restore (`BackupService` 28), bundle files (`BundleHandlers` 25), relocation, sizes | `Store(Backups)`, `Store(Bundles)`; `util.process` runs pg_dump into a store's reserved file |
| `util.plugins` | 36 | 3 | the relink tool's kit and plugin jars (`PluginTool` 28) | `Store(Plugins)`, `Store(RelinkKit)`; `pluginTool` is a stand-alone entry point and may keep paths at its edge |
| `util.settings` | 30 | 7 | the settings file's atomic write (`ConfigFileWriter` 22) | `Store(UserConfig).put` (temp + atomic move) |
| config loading, composition root | 49 | 8 | `UserPaths`, `AppConfig`, `Supervisor` (the converter child process), `Bootstrap` | stay: they build the locations |
| `util.ocr` | 9 | 4 | the spool and the converter's files | `Store(Spool)` |

188 uses in 23 domain files become store operations; the supporting areas' 294 uses move behind `util.storage`
too, so sizes, cleanup and the 360° view see every file. `cli` (227 uses) is a host-side client and outside the
rule; plugins keep their own file rules.

Special cases, and how the handle covers them without a `Path` in a domain:

| case | covered by |
|---|---|
| PMTiles range reads (`FileChannel`, positional reads) | `Blob.readAt(offset, length)`; the channel stays inside `util.storage` |
| pg_dump / pg_restore writing to and reading from a path | `util.process` takes a `Store` reservation (`store.reserve(key)`), passes the path to the child process inside the supporting areas, and commits or discards it |
| the relink kit (a directory of jars handed to native-image) | `Store(RelinkKit)`; the relink runs as a stand-alone tool whose edge may use paths |
| the settings file's atomic move | `Store(UserConfig).put` writes a temp file and moves it atomically, as `ConfigFileWriter` does today |
| agent CLI runtimes that need a home directory (`CODEX_HOME`, generated trees) | `util.process` launches with a `Store(AgentHomes)` sub-store as working directory; the engine never sees the path |

### 3.8 Interfaces lower places own

| interface | owner | implemented by | replaces |
|---|---|---|---|
| `WorkflowDefinition`, `ToolContribution`, `ManagerKind`, `ObjectiveProgress` | automation | research, library | the engine's 120 references into domains; 10 repository calls and 14 SQL reads |
| `DataContribution` (with `CarriedKind`, `MergeRules`, `CheckRule`, …) | `util.data` | every domain | data management's 81 references |
| `JobKinds` | `util.jobs` | every domain that runs jobs | 8 references |
| `AliasTables` | `util.identity` | library, research, catalogue, automation | 6 references |
| `Store` | `util.storage` | — (used) | 188 file-system uses in domains |
| `Referable` | shared kernel | library, research, catalogue, media, automation | personal's joins; goal links', asset links' and attestations' target lookups |
| `MapLayer`, `PassageSource` | catalogue | library, media | 17 references |
| `SourceReferrer` | library | research, media, automation | 10 references |

### 3.9 `personal` and `Referable`

Personal owns bookmarks, notes and read marks (and saved views, which are bookmarks of kind `view`); each points at
a target and knows nothing about it. Today `ItemTitles` joins `sources`, `articles`, `events`, `goals` and `places`
by name in SQL. Personal is not the only place that points at "an item of some kind": goal links, asset links and
attestations do too. So the interface moves into the shared kernel, and personal adds only what is its own:

```scala
package com.histmersion.domain                               // shared kernel

/** A record of some domain as another one stores a reference to it. */
final case class ItemRef(kind: ItemKind, key: ItemKey, uid: Option[ItemUid])

type ItemKind = ItemKind.T                                     // "source", "article", … — registered by the owners
object ItemKind extends RefinedType[String, Match["[a-z][a-z-]*"]]

/** Implemented by each domain whose records others point at. */
trait Referable:
  def kinds: Set[ItemKind]
  /** The record `key` names now — aliases and uid redirects followed (util.identity) — or why there is none. */
  def resolve(kind: ItemKind, key: ItemKey)(using DbCon): Either[String, ItemRef]
  /** How each record shows wherever it is referenced. One call per kind on a page, batched. */
  def describe(refs: Set[ItemRef])(using DbCon): Map[ItemRef, Described]

final case class Described(title: Title, at: Option[LatLon], years: Option[Period], route: WebRoute, state: ItemState)

enum ItemState:
  case Live
  case Renamed(now: ItemKey)          // the key is an alias now
  case MergedInto(ref: ItemRef)       // a uid redirect a merge wrote
  case Deleted(at: Instant)           // a tombstone: the reference stays, shown as gone

package com.histmersion.domain.personal.marks

enum MarkTarget:
  case Item(ref: ItemRef)                                                       // source, article, event, place, picture, goal
  case Point(at: LatLon, zoom: Option[MapZoom], years: Option[Period])
  case View(state: Option[ViewState], at: Option[LatLon], zoom: Option[MapZoom], years: Option[Period])
  case Span(years: Period)

/** Read marks make sense for a kind (sources and articles today). */
trait Readable:
  def readable(kind: ItemKind): Boolean
```

| kind | `Referable` by | resolves through |
|---|---|---|
| `source` | library | citekey and its aliases; uid |
| `article` | research | slug and its aliases; uid |
| `event`, `place` | catalogue | event id, place id and their aliases; uid for events |
| `picture` | media | asset uid |
| `goal` | automation | goal slug and its aliases; uid |
| `point`, `view`, `period` | personal itself | coordinates, the view's length and the years (today's `Target.check`) |

Personal **pulls**: rename, merge and delete are answered by `describe` at read time — the uid stored with every
mark (`target_uid`) survives renames, merges are followed through `uid_aliases`, deletions read the tombstones — so
no domain calls personal (Q21). The word search over marks matches the stored `target_label`, which personal
refreshes when `describe` returns a new title.

**Per user.** Every personal service method takes a `UserId` (`'local'` today). With
`docs/research-multi-user.md` (#7843) a `people` area (accounts, sessions, roles) comes beside personal; personal
needs from it only the `UserId` value type, and controllers pass the signed-in caller instead of `Caller.Local`.

### 3.10 A shared `Licence`

The licence is not media's: sources carry their open-access licence, datasets their data licence, pictures theirs.

| type or column | where | what it is | becomes |
|---|---|---|---|
| `MediaLicence.Licence(id, family)` with `classify`, `ofUrl`, `parse`, `allowList`, `allowed`, `needsAttribution`, `isPublicDomain` | core | a licence classified into a family | **`domain.Licence(id: LicenceId, family: LicenceFamily)`** in the shared kernel; the functions on its companion; `LicenceFamily` an enum with the wire strings kept |
| `MediaRights` (core) and `plugin.MediaRights`, `DataCredit` (api) | core, SPI, api | licence + licence URL + author + credit | **`domain.Attribution(licence, licenceUrl, author, credit)`**; `MediaRights` becomes it; a `DataCredit` is a dataset with an `Attribution` |
| `sources.license` (from `FetchedFile.licence`) | library | a source's OA licence | `Option[Licence]` on the source, classified on read (Q26) |
| `assets.license`, `assets.license_url` | media | a picture's licence | `Attribution` |
| `gazetteer_sources.licence`, a catalogue's licence, Cliopatria's (V107) | catalogue | a dataset's licence | `Attribution` |
| `plugin.Licence` (SPI) | plugin-api | a licence as a provider states it | stays; the host classifies it |
| allowlists (`media.<provider>.licences`, `media.collections.licences`, `per-collection.<name>`) | media | a policy over families | stays media's (`LicencePolicy`) |
| `OaStatus` (`gold`/`diamond`) | library | an open-access route, not a licence | stays the library's |
| `Access` (`Open`/`Subscription`/`Unauthorised`) | SPI | whether a fetch is authorised | stays the SPI's |
| `MediaProvider`, `Provenance` | api | where a record came from | stay |

`LicenceId` (the stored and sent string) is in `values`, because the web page names licences. Measured
licence-ish fields today: `license: Option[String]` 19, `licence: String` 10, `licence: Option[…]` 10, `licenseUrl`
8, `licenceUrl` 4, `licences: String` 5 (outside web).

### 3.11 The other supporting areas

| area | holds | module |
|---|---|---|
| `util.database` | the pool, `Transactor`, Flyway runner, generic column codecs, `Jsonb`, `Keyset`, `Param`, `Sql`, `ListSql`, `Vocab`, the deep-time range SQL (today's `PeriodSql`) | `db` |
| `util.ocr` | the converter client and state, JATS → Markdown conversion (over `upstreams`' parser), the chunker; the library's ingest decides what happens to a source | server |
| `util.process` | launching child processes (pg_dump, the converter, CLI agent runtimes) with store handles | server |
| `util.json` | generic newtype codecs and schemas, `WireEnum`, `StrictJson`, `StrictJsonSchema`, JSON cursor helpers | `api` (JVM + JS) |
| `util.http` | the outbound client, policy, politeness, TLS, `Url` | `plugin-host` / server |
| `util.markdown` | flexmark flavour, Markdown → HTML, HTML atoms | server / `api` |
| `util.text` | `FoldedText`, `Plural`, `Csv`, `Diff3`, `MinHash` | `api` / `core` |
| `util.images` | PNG, VP8L, Terrarium PNG encoders | server |
| `util.tiles` | MVT, PMTiles, the vector-tile writer | server / `core` |

`cli` and `web` keep their own trees (`cli.<domain>`, `web.<domain>`) and are touched only at their edges (slice 12).

## 4. Newtype catalogue

### 4.1 Rules

- **Where a newtype lives.** It goes in `values` (JVM + JS, Iron only) when it crosses the wire or more than one
  feature uses it. It goes in `domain.<domain>.<feature>` when only one feature's logic uses it. It goes in
  `config.<domain>` when it is a setting with no meaning outside configuration. `plugin-api` keeps its own
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
| `QueueItemId` | Int | `Positive` | domain.automation.worklists | 21 | the worked example (§6) |
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
| `LicenceId` | String | Pure | values | 42 | the licence as stored and sent (an SPDX id, `public-domain` or a URL); the classified `Licence` is the shared domain type of §3.10 |
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
| O5 | holds a domain's rules over a "facts" record (`GoalFocus.Facts`, `AutopilotPolicy.*Facts`, `SchedulerPolicy.Scored`) | methods of that record, renamed to say what it is (`FocusFacts`) | in O3 |
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
| `core.MergePlanner` | 2 425 lines, 9 nested types, 67 functions | split: the plan mechanism (`MergePlan.of`, `LedgerEntry`, ≈ 680 lines) into `util.data.exchange.merge`; the rules per kind (≈ 1 750 lines) into each domain's `MergeRules` | the largest object; §3.6 |
| `core.ReviewArticle` | 2 013 lines, 36 nested types | `domain.automation.review` types, rules on `Reviewer`, `Verdict`, `ReviewFinding` | the second largest |
| `server.MarkdownLint` | 1 336 lines | `domain.research.articles.lint`: `FormatFinding`, `Edit`, `LintContext` | prose lint belongs to the article, a research result |
| `*Markdown` (28) | one renderer object per area | a `MarkdownRender[A]` instance on the companion of the wire type it renders (Q14) | "a function about a type" |
| `Chunker` | `chunks(body)` | `util.ocr.Chunker` producing `Chunk`s (`Chunk` is the library's stored chunk) | the split is mechanical (§3.11) |
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
| domain types, pure rules | `domain.<d>.<f>` (core module when other modules need them, else server) | public or `private[<f>]` | invariants (smart constructors), pure decisions |
| `<F>Service` trait | `domain.<d>.<f>` (server) | public | the feature's operations in domain types |
| `<F>Service.Implementation` | companion | `private final class` | transactions, calls of its repository and of other services |
| `<F>Service.InMemory` | companion | `final class` | the same behaviour over `AtomicReference`s |
| `<F>Repo` | `domain.<d>.<f>` (server) | `private[<f>] object` | SQL; every method takes `(using DbCon)`; never opens a transaction |
| `<F>Controllers` | `domain.<d>.<f>` (server) | public `final class` | parse raw inputs into domain values (the boundary), maintenance guard, call services, convert to wire records (Chimney) |
| `<F>Routes`, `<F>Tools` | `domain.<d>.<f>` (server) | public | bind the feature's Tapir endpoints and Chimp tools to controller methods; nothing else |
| `<F>Endpoints`, wire records, renderers | `api.<s>.<f>` | public | the contract |
| cross-domain read controllers | `server.views` | public | one response assembled from several domains' services (§3.4) |
| contributions | `domain.<d>.workflows`, the domain's `DataContribution`, its `Referable` | public | what the domain registers into the engine, data management and the shared kernel (§3.5, §3.6, §3.9) |

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

Target: the list mechanism is the engine's kernel, `domain.automation.worklists`; the table stays one table (`queue_items`, no
migration). Each owner constructs the kernel's service for its own list and adds its own rules on top:
`library.acquisition` owns ACQUIRE (and the DOI matching of `acquireNaming`), `automation.tasks` owns TASKS and
`automation.log` owns LOG (whose controller dates a new item's topic). The goal scope leaves the kernel: automation
asks its goals for the linked item ids and reads them with `byIds`.

Target:

```scala
package com.histmersion.domain.automation.worklists

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

Where real services share data, the `InMemory` ones share one state record per domain, held in one
`AtomicReference` and updated through lenses (rule 8). Across domains they never share state: an `InMemory`
service of a higher domain is given the `InMemory` services of the lower ones and calls them, exactly as the
`Implementation` does.

| state record | shared by |
|---|---|
| `JobsState`, `IdentityState` | `util.jobs`, `util.identity` (supporting areas have `InMemory` services too) |
| `CatalogueState` | places, periods, events, evidence |
| `MediaState` | assets, links, checks |
| `LibraryState` | sources, findings, citations, labels, candidates, acquire checks |
| `ResearchState` | articles, versions, links, coverage, review state |
| `AutomationState` | runs, steps, approvals, managers, autopilot, schedules, goals, work lists |
| `DataState` | merge sessions, decisions, ledger; backups list |
| `PersonalState` | personal (exists) |

### 6.4 Controllers and the caller

Each controller method and each writing service method takes the caller (`Caller.Person(user: UserId, role: Role)`
or `Caller.Automation(run: RunId)`). Contributed workflows write with `Caller.Automation`, through the
same service methods a person's request reaches (§3.5). Until `docs/research-multi-user.md` is built, a person is
always `Caller.Local`. This is the one place the multi-user work will
change, as that design already assumes for `PersonalControllers`.

### 6.5 Worked example, second half: a workflow research contributes

`compile-article` today lives in `server/agents/CompileArticlePipeline` (346 lines) and `core/CompileArticle`
(432), reads articles through `ArticleRepo.load` and writes them through `Handlers.putArticle`. In the target it is
research's, registered into the engine, and it touches articles only through research's own service and sources
only through the library's:

```scala
package com.histmersion.domain.research.workflows.compile

final class CompileArticleWorkflow(
    articles: ArticleService,          // research's own service
    coverage: CoverageService,         // research's own service
    sources: SourceService             // library's public service (library is below research)
) extends WorkflowDefinition:
  val id = WorkflowId("compile-article")
  val roles = List(AgentRole.Writer)
  val tools = List(GetSourceTool(sources), PassagesTool(sources), CheckEditsTool(articles), ArticleCoverageTool(coverage))

  def start(request: WorkflowRequest, run: RunContext): Either[WorkflowError, WorkflowPlan] =
    for
      slug   <- request.target[Slug]
      filter <- coverage.filterOf(slug).toRight(WorkflowError.NoFilter(slug))
    yield WorkflowPlan.of(CompileSteps.extract(filter))           // the rules of today's core/CompileArticle

  def step(state: StepState, answer: StepAnswer, run: RunContext): StepOutcome =
    CompileSteps.next(state, answer)                               // pure, tested without a server

  def settled(result: RunResult, run: RunContext): Either[WorkflowError, Settled] =
    articles.put(result.article, Caller.Automation(run.id)).map(Settled.Written(_))   // as a person would

package com.histmersion.domain.research

/** What research gives the engine; the composition root concatenates every domain's list. */
object ResearchWorkflows:
  def apply(articles: ArticleService, coverage: CoverageService, sources: SourceService): List[WorkflowDefinition] =
    List(ReviewArticleWorkflow(…), CompileArticleWorkflow(articles, coverage, sources), ProposeArticlesWorkflow(…),
         InferFilterWorkflow(…))
```

Testing it needs no server and no database: `WorkflowEngine.InMemory` drives the definition with a scripted model
(sttp-ai's `agent-testkit`, as the scripted suites do today) over `ArticleService.InMemory`,
`CoverageService.InMemory` and `SourceService.InMemory`, seeded through their own methods. The engine's own suites
test runs, leases, retries and approvals with a trivial test workflow, so they never need an article.

During the transition (slices 6–7, Q34) the engine registers today's pipelines through thin adapters that implement
`WorkflowDefinition` over the old code; each domain's slice replaces its adapters with its own definitions.

## 7. Module layout after the change

### 7.1 sbt projects

Packages follow §3: `domain.<domain>.<feature>` in `core` (the pure part) and `server` (services, repositories,
controllers, routes, tools), `api.<domain>.<feature>` in `api`, `config.<domain>` in `config`; supporting areas
`util.<area>` in the module that suits them (`util.database` in `db`, `util.json` in `api`, the server-side areas —
`util.data`, `util.storage`, `util.jobs`, `util.identity`, `util.plugins`, `util.settings`, `util.basemap`,
`util.ocr`, `util.process` — in `server`).

| project | holds (packages) | depends on | change |
|---|---|---|---|
| `values` (JVM + JS) | `values`: identifiers, values, vocabulary enums, generic quantities, `LicenceId`, `UserId` | iron | grows (§4) |
| `api` (JVM + JS) | `api` root: shared wire types (`ApiError`, `WriteCode`, `Page`, `Filter`, `BBox`, `TimeSpan`, Markdown primitives); `api.<domain>.<feature>`: records, `<F>Endpoints`, renderers; `util.json` | values | `Api.all` = the features' lists in today's order |
| `config` | `config` root (`AppConfig`, loading, layers); `config.<domain>` settings records | values | flat package split by domain and area; step 2's quantities move to values |
| `files` | `files` (bundle format, notes, merge file, plugin set), `util.files` | api | unchanged in scope |
| `core` (JVM) | the shared kernel `domain` (`Period` helpers, `Licence`, `Attribution`, `Caller`) and the pure part of `domain.<domain>.<feature>` | values, files (and through `files` api, for the bundle records) | its logic stops using api's wire records, subdomain by subdomain (Q9) |
| `db` | `util.database` only: pool, `Transactor`, Flyway runner, generic codecs | config | **repositories, `FilterSql`, `PeriodSql`, `SiteTypesSql`, the bundle and merge stores and the seed importer leave** |
| `server` | `server` (composition root, adapters, `server.views`); `domain.<domain>.<feature>` services, repositories, controllers, routes, tools; the server-side supporting areas (`util.data`, `util.storage`, `util.jobs`, `util.identity`, `util.plugins`, `util.settings`, `util.basemap`, `util.ocr`, `util.process`); `resources/db/migration` (95 files) | core, db, api, plugin-host, `upstreams`; plugin modules **`% Runtime` only** | gains the repositories and the migrations |
| `upstreams` *(new)* | `upstreams`: parsers and clients of public upstreams (OpenAlex, Crossref, Unpaywall, CORE, DataCite, archive.org, Europe PMC, arXiv, Wikidata, Commons, JATS, landing-page rules, catalogue dating) over `PluginHttp` | plugin-api, circe (plugin platform) | from `plugin-host/AcquireParse` & friends, `core/RepositoryLanding`, `Jats`, `Commons`, `Wikidata`, `CatalogDating`, `server/acquire` clients |
| `plugin-api` | the SPI | sconfig, kindlings, iron | eleven additive members (§8.3, §8.5); `PluginInfo.runsOn` later (§8.6) |
| `plugin-host` | host side of the SPI | core, config, plugin-api, upstreams | its acquisition parsers move to `upstreams`; it uses the shared dating rules from there |
| `plugin-testkit` | unchanged API | plugin-host | its published dependency set changes with plugin-host's |
| `plugins/fetchers` *(new)* | `archive-org`, `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`, `arxiv` | plugin-api, upstreams | from `server/acquire/fetchers` |
| `plugins/events` *(new)* | `events-gvp`, `events-noaa`, `events-wikidata` | plugin-api, upstreams | from `core/EventProviders`, `server/events/BuiltinCatalogues` |
| `plugins/gazetteers` *(new)* | `gazetteer-cliopatria`, `gazetteer-awmc`, `gazetteer-chronontology` | plugin-api, upstreams | from `server/importers/*Importer` (Q23) |
| `plugins/media` *(new)* | `media-wikidata`, `media-depicts`, `media-met`, `media-cleveland`, `media-aic`, `media-loc`, `media-pleiades`, `media-opencontext`, `media-smithsonian`, `media-europeana` | plugin-api, upstreams | from `server/media` collections and `core` parsers |
| `plugins/ai-http` *(new)* | `ai-anthropic`, `ai-openai`, `ai-local` | plugin-api, circe | from `server/agents/LlmClient`'s provider part |
| `plugins/ai-cli` *(new, Q5)* | `ai-claude-code`, `ai-codex`, `ai-opencode` | plugin-api | from `server/agents` runtimes |
| `plugins/calibre` *(new)* | `calibre` (a `SourceCollection` with optional write-back) | plugin-api, circe | from the CLI's host-side Calibre code (≈ 1 400 lines, §8.5) |
| `plugins/vaults` | `keepassxc`, `1password`, `bitwarden` | plugin-api | directory moved from `plugin-vaults`; artifact name kept |
| `cli`, `web`, `plugin-resolver`, `plugin-fixtures/*`, `chimney-iron*` | | | unchanged except edges (slice 12); the CLI's Calibre code becomes HTTP clients (slice 4) and a JVM-mode hand-off for CLI plugins (slice 13) |

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

Inside `core` and `server`, the table of §3.3 holds, checked by `ArchitectureSuite`. Splitting `server`
into one sbt project per domain and supporting area would make the compiler check it and let sbt compile them in parallel; it is
Q18, and mechanical once the packages are clean.

### 7.2 Consequences

- The server depends on the plugin modules `% Runtime`, so no server code can import a plugin's class. That is
  #7911's "server depends on them only for bundling", enforced by the compiler. `server/nativeImage`, the relink
  kit and `pluginRepo` read `Runtime / fullClasspath` instead of `Compile / fullClasspath`. AGENTS.md already
  plans that change for chimney-iron.
- Migrations: the files move from `modules/db/src/main/resources/db/migration` to the same path in `server`,
  unchanged, so Flyway checksums and `Migrate.Replaced` stay valid. The index generator moves with them (Q12). Each
  table's owner is recorded in §3.1, not by splitting files: many migrations touch several domains' tables, and
  applied migrations are never edited. `queue_items` stays one table whose rows are owned per list.
- Package moves rename classes, and `reachability-metadata.json` names classes. Every slice re-records the
  metadata with the pinned JDK (server `run` against an empty database, the CLI, and the plugin modules' own
  metadata files) and proves it with the bundle's `--self-check` and `smoke.sh`.
- `pluginRepo` also publishes `upstreams`, because plugin-host, which the testkit runs, uses its dating rules.

## 8. Plugin separation (#7911)

Acquiring is each domain's own business: the library acquires sources, the catalogue acquires data points, media
acquires pictures. In each the **interface and the running of it** are domain code — the fetch chain, its quota,
the ACQUIRE list, recheck, discovery and candidates (`library.acquisition`); the catalogue and gazetteer import
loops (`catalogue.events`, `catalogue.places`); the collection import loop and its licence policy (`media`). The
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
`catalogue` (Q23).

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
  shared `domain.Licence` (§3.10) in every domain the same way.
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

### 8.5 More integrations as plugins: "the domain defines and runs, a plugin implements"

The same shape — the domain owns the interface and the running (the job, the schedule, the quota, the surface), a
plugin owns the integration — fits more than today's built-ins. Measured candidates:

| integration | today | plugin kind | when | reason and size |
|---|---|---|---|---|
| **Calibre** | the CLI on the host: 1 708 lines in 15 files (`Calibre`, `CalibreCmd` 563, `CalibreJson`, `CalibreAudit`, `MediaCovers`, `Audit*`, `LinkedRecord`, `Update*`, …); the server: three endpoints (gaps, push metadata, pushed) and `AcquireRepo`'s gap queries, 64 lines of api | **`SourceCollection`** (SPI-11) | **now** (slice 4) | user decision: the server reads Calibre as a source of sources and may write back. Details below |
| archive.org search, info and import | `ArchiveOrg` 49 + `AcquireHandlers.archive*` + its api records; its fetcher is a plugin from slice 3 | `SourceCollection` (read-only) | **now**, with Calibre | the same kind, read-only; the plugin already holds its client |
| a folder of PDFs, Zotero | none | `SourceCollection` | later | the kind is named generally so they need no new SPI |
| BibTeX / CSL / Zotero export | none | `CitationExport` | later | no code today |
| the Markdown (Obsidian) mirror | 418 lines (`Export`, `ArticleNote`, `Frontmatter`) + the CLI's `export` | `MirrorFormat` | later | one format today, and the research workspace depends on its exact output; it is a `DataContribution` export now and becomes a plugin kind when a second format is asked for |
| discovery indexes (OpenAlex, Crossref, Semantic Scholar, Wikipedia, Europe PMC, arXiv, CORE) | about 400 lines of `server/acquire/*Index`, `SemanticScholar`, `Wikipedia`, `CandidateSearch` | `DiscoveryIndex` | later (after slice 3 proves the `upstreams` split) | the parsers already move to `upstreams`; the index list is the next natural cut |
| Retraction Watch | 16 lines + the update handler | `CurrencySource` | later | one source; small |
| OCR converter backends | `ConverterClient` 57 + `HttpConverterClient` 163 over the converter service | `ConverterBackend` | no, for now | one backend, already behind a trait in `util.ocr` |
| PeriodO, the bundled gazetteer extracts, Natural Earth land | 214 + 503 + 63 | — | no | data shipped with the install (Q23) |
| base-map sources (vector extracts, GEBCO) | `BasemapFetch` 424, `GebcoSeaFloor` 179 | `BasemapSource` | later | no second source asked for |
| backup targets | local `pg_dump` only | `BackupTarget` | later | a remote target is new work |
| secret vaults, AI providers | already plugins | — | — | — |

**Calibre as a server-side `SourceCollection` plugin** (user decision: "the server is accessing Calibre as a source
of sources, and has an option to add missing papers there and to enrich Calibre's records").

- **What the library defines and runs** (`library.collections`): searching a collection and importing an item into
  the library (today's `calibre import`), the gaps check (sources whose original is not in a collection: today's
  `calibre gaps`, which the research workspace's hook calls), the sync job (`calibre sync --apply`), link and
  relink, the audit (a collection record against the library's), and the HTTP, MCP and CLI surface. The CLI's
  `calibre …` commands become thin HTTP clients like every other command; their flags and output stay
  byte-identical (the CLI help snapshot, plus output snapshots of each command over a fixture library).
- **What the plugin implements** (`plugins/calibre`): reading the library (`calibredb list --for-machine`, falling
  back to `sqlite3 -readonly` on `metadata.db` when the GUI holds the database, as today), the OPF and metadata
  mapping, and the write-back (`calibredb add`, `set_metadata`). About 1 400 of the CLI's 1 708 lines move into it;
  the rest (decline commands and output formatting) stays in the CLI as HTTP clients. For a server on another
  machine the same plugin runs on the CLI side (§8.6).
- **Capabilities.** `read` always; `add` and `update` only when the plugin's block says so (`write-back = false` by
  default). Writes are refused while the Calibre GUI holds the library, with today's message.
- **Configuration, no globals.** The library location and the `calibredb` executable are the plugin's settings
  (`histmersion.plugins.calibre.library-path`, `…calibredb`), with the paths today's CLI uses as defaults.
- **Reach.** The server runs as the user's LaunchAgent, so it can read the user's library; on macOS a library in a
  protected folder needs the same TCC grant the relink kit documents (§21.2 of the plugins design). A server in a
  container would need the library mounted — stated in the plugin's doctor check.
- **A plugin may launch processes**: the rule that keeps paths and processes in supporting areas binds the server's
  domains, not plugins (the vaults already run their CLIs).

**The SPI for it** (all additive and defaulted; one ServiceLoader type; built-ins switchable):

| # | addition | default |
|---|---|---|
| SPI-11 | `PluginContext.sourceCollections: Registry[SourceCollection]`; `SourceCollection(title, capabilities: Set[CollectionCapability], search(query): Either[String, List[CollectionItem]], item(id), original(id): Either[String, FetchedFile], add(item, file): Either[String, CollectionId], update(id, metadata): Either[String, Unit], audit(ids): List[CollectionFinding])` — `add`, `update` refuse unless the capability is declared | refusing registry; `add`/`update` refuse |
| later | `DiscoveryIndex`, `CitationExport`, `MirrorFormat`, `CurrencySource`, `BasemapSource`, `BackupTarget`, `ConverterBackend` — each a registry on `PluginContext`, added when its first plugin is built | — |

### 8.6 CLI plugins, for a server on another machine

When the server runs on another machine (Tailscale, `docs/research-multi-user.md`, #7843), resources on the user's
own machine — their Calibre library, a folder of PDFs, files to upload — are reachable only from the CLI. So the
plugin system gets a CLI side, within the same rules:

- **One plugin system.** Still ONE ServiceLoader service type (`Plugin`) and `plugin-api` interfaces only. A plugin
  declares where its contributions run: `PluginInfo.runsOn: Set[Side]` (`Server`, `Cli`; default `Server`, so
  every existing plugin is unchanged). The CLI builds a `PluginContext` of its own whose registries accept only the
  kinds that make sense there (`sourceCollections` first) and refuse the rest, as hosts without a registry refuse
  today.
- **The same interface on both sides.** `SourceCollection` is implemented once. On the server it imports directly
  and writes back with server-side originals. On the CLI the host part is a small adapter: it reads the local
  collection through the plugin and talks to the remote server only through the existing HTTP API — `ingest` with
  the uploaded original, `patch` for metadata, `GET …/original` and the gaps and metadata endpoints for write-back.
  No new endpoint is needed for Calibre: today's CLI already does exactly this over HTTP.
- **Which side runs.** The plugin's settings name the side (`histmersion.plugins.calibre.side = auto | server |
  cli`). `auto` (the default) means: the server side when the server reports the collection reachable (its doctor
  check), else the CLI side when the CLI has the plugin. The `calibre …` commands say which side ran.
- **How the user sees it.** `/api/about`, `histmersion about` and the web About list the server's plugins as today;
  `histmersion plugin list` lists the CLI's own plugins beside them, each marked with its side; `histmersion plugin
  add|remove` install a CLI plugin into the CLI's plugins directory (`<data>/cli-plugins`, from config), with the same
  POM-based dependency resolution as server plugins.
- **No new privileges.** A CLI plugin acts through the CLI's HTTP client with the CLI user's token and role; it can do
  nothing the user could not do with the CLI by hand, and the server checks every call as it does today.

**How the native CLI loads plugins** (measured on the server: a relink is about 2 minutes and 11 GB of RAM, and the
install already ships GraalVM, 681 MB, for the relink and JVM mode):

| option | cost | verdict |
|---|---|---|
| relink the CLI with its plugins, like the server | minutes and gigabytes on every plugin change, a second relink kit to ship | no: CLI plugins are occasional sync commands |
| a JVM-only CLI everywhere | about a second of JVM start on every command, for every user | no |
| **the native CLI stays plugin-free and hands a plugin's commands to a JVM-mode CLI** (`histmersion-cli-jvm`, the CLI jar and the plugins directory on the installed GraalVM, as `server-jvm.sh` does for the server) | about a second of start-up only for plugin commands; one more script in the bundle | **recommended** |

CLI plugin support is its own slice after the server-side Calibre plugin (slice 13, Q40). Until it lands, the
CLI keeps today's host-side Calibre code for a remote server, so that case never breaks; slice 13 replaces it with
the CLI side of the same plugin.

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

Groundwork, the template, the two plugin slices; then the supporting areas and the engine **before** the domains
that contribute to them; then one domain per slice, bottom up, each replacing the transition adapters with its own
contributions. Sizes count the lines the slice moves or rewrites: S < 2 k, M 2–6 k, L 6–12 k, XL > 12 k. Each slice
ends with its rows gone from the `ArchitectureSuite` exception list.

| # | slice | scope | size | risk | the risk is … |
|---|---|---|---|---|---|
| 1 | **Groundwork** | `ContractSnapshotSuite`; `ArchitectureSuite` with its three rules and the known exceptions (264 references, 188 file-system uses in domains); generic newtype instances; the identifier newtypes in `values`; `Transactor`, `Clock`/`Environment`/`UserPaths` from `Main`; `Caller`; `@main def` entry points (#7908); AGENTS.md rule 14, the layout table and the "adding an operation" steps | M | low | the generic givens' form (fallback in §4.4); the entry-point rename in scripts |
| 2 | **Personal** (the template) | the pilots' shape made exact; `Referable` in the shared kernel with its five implementers placed in their future packages; `ItemTitles`' joins replaced; per-user `Caller` | S (2 k) | low | the word search over marks (Q21); listing speed with batched `describe` at live volumes |
| 3 | **Plugins I: fetchers and event catalogues** | `upstreams`; SPI-1, 2, 3, 7; `plugins/fetchers`, `plugins/events`; host-supplied settings; `Builtin*`, `HostEventCatalogue`, `OaIndex`'s state go | L (6 k) | high | identical requests in identical order and `events rederive` results (recorded traffic, the fixture suites) |
| 4 | **Plugins II: media, gazetteers, AI over HTTP, Calibre and archive.org** | SPI-4, 5, 6, 8, 10, 11; `plugins/media`, `plugins/gazetteers`, `plugins/ai-http`, `plugins/calibre`; archive.org's search and import as a read-only `SourceCollection` in `plugins/fetchers`; the server runs Calibre (§8.5) and the CLI's `calibre` commands become HTTP clients for a server on the same machine | L (8 k) | high | licences, roles, the agent loop's wire requests; Calibre's output snapshots over a fixture library. The plugin report gains the gazetteer and Calibre plugins — the intended snapshot changes |
| 5 | **Supporting areas** | `util.storage` (locations, stores, scratch; the file code of the supporting areas behind it), `util.jobs` with `JobKinds`, `util.identity` with `AliasTables`, `util.plugins`, `util.settings`, `util.basemap`, `util.ocr`, `util.process`; today's per-kind code registered through adapters | L (8 k, mostly moves) | medium | every job kind and alias table re-registered; file layout unchanged (a snapshot of the data directory's tree after the smoke run) |
| 6 | **Data management** | `util.data`: the registry, backup, maintenance with `WriteGate`, checks, export, the bundle and merge mechanism; today's per-kind code registered through adapters | L (12 k) | high | bundle round-trips and merges of a restored `*_test` copy, byte for byte; backups restore |
| 7 | **The automation engine** | `WorkflowDefinition`, `ToolContribution`, `ManagerKind`, `ObjectiveProgress`; runs recorded through `util.jobs`; goals as objectives; work lists; the definitions generator fed by contributions; today's pipelines and tools registered through adapters | XL (25 k, mostly moves) | high | the toolbox snapshot, the scripted-model suites and the manager DB suites are the oracle |
| 8 | **Catalogue** | places, periods, events with corrections and the event-catalogue import loop, evidence, map tiles with `MapLayer`, `PassageSource`; its data contributions (carried kinds, merge rules, checks, caches, the gazetteer extracts store) | XL (14 k) | medium | `PlaceRepo` and tile timing (performance suite at live volumes) |
| 9 | **Media** | assets, links, IIIF, figures, covers, the collection loop; the shared `Licence` and `Attribution`; its data contributions and stores | M (5 k) | medium | licence classification shared from here on |
| 10 | **Library** | sources, findings, citations, labels, search and query, acquisition, ingest over `util.ocr` and its stores, collections (Calibre and archive.org running), its workflows, tools and the watch manager kind, its data contributions (`SourceMatcher`, the sources' merge rules); `SourceReferrer` | XL (17 k + its share of `Handlers`) | high | the most used types; the search SQL must not change (a snapshot of the SQL the filter suite generates) |
| 11 | **Research** | articles; its workflows (review panel and ledger, compile, propose, infer-filter), tools and the review manager kind; its data contributions | L (10 k + its share of `Handlers`) | medium | the review panel moves whole; its suites are the oracle |
| 12 | **Closing** | `server.views`, `server.instance` over the diagnostics; `db` reduced to `util.database`; `Handlers`/`OpsHandlers` deleted; `cli.<domain>` packages; web edges; ai-cli with SPI-9 (Q5); every transition adapter gone; `ArchitectureSuite` with no exceptions; AGENTS.md's module section rewritten | L | medium | many moves of self-contained code |
| 13 | **CLI plugins** (later, §8.6) | `PluginInfo.runsOn`, the CLI's plugin context, `histmersion-cli-jvm`, `histmersion plugin` for CLI plugins; Calibre's CLI side replaces the CLI's host-side code | M | medium | a second plugin context; the JVM hand-off's start-up time |

Each slice is deployed (bundle, self-check, smoke) before the next starts, and records its as-built section in this
document.

## 10. What this design does not change

- No endpoint, MCP tool, CLI command, config key, environment variable, table or column is renamed or removed.
- No migration is added for the refactor itself. A slice that finds one necessary stops and asks.
- The web page's behaviour; only its edges get types.
- The multi-user work (#7843) comes after; this design only leaves its seam (§6.4).

## 11. Open questions

Q1–Q16 are from the first draft; Q17–Q27 from the second; Q28–Q42 from the third, which also updated Q4, Q5, Q12, Q15,
Q16, Q18, Q22 and Q24. Decided or superseded: Q17, Q20, Q25, Q27.

| # | question | recommendation |
|---|---|---|
| Q1 | Parse raw inputs in the **controllers** (error texts unchanged; input records carry `Pure` "raw" newtypes) or in the **decoders** (constrained newtypes in input records, plus a decode-failure handler answering `ApiError(message)`, which changes some 400 texts)? | Controllers now; decoders as a separate, announced change later |
| Q2 | Entry-point classes: keep the names (`@main def Main(args: String*)` keeps `com.histmersion.server.Main`) or use Scala-style names (`histmersionServer`, `histmersionCli`, `pluginTool`, …) and update every reference (§5.5)? | Scala-style names; every reference is in this repo and each kit carries its own `main-class` |
| Q3 | The clock: `java.time.Clock` everywhere, or the pilots' `() => Instant`? | `java.time.Clock`: it also serves `LocalDate.now(clock)`, and `Clock.fixed` serves tests |
| Q4 | Plugin modules: one per family (7 + vaults + the `upstreams` library) or one per plugin (31)? | Families: 31 sbt projects would add build time for no isolation the switch does not already give |
| Q5 | ai-cli (`claude-code`, `codex`, `opencode`): build the `AiRuntime` SPI (SPI-9) and move them in slice 12, or keep them as the one host-owned exception? The measured coupling is 25 host types and ~100 call sites | Move them, last: design SPI-9 in slice 7 (the engine), move in slice 12; the generator, trees, run tokens and plan-pool storage stay the engine's |
| Q6 | One shared `upstreams` library for the host and the plugins, or a copy of each parser on each side? | One library: discovery and the fetchers parse the same OpenAlex, Crossref and CORE records |
| Q7 | Show the mapped host settings in a first-party plugin's report? | No: the report lists the plugin's own block, as today (byte-identical) |
| Q8 | Flags: keep `Boolean` parameters (named) or newtype them too? | Keep `Boolean`; replace a Boolean with an enum only where two or more flags form a mode (`dryRun`/`apply`/`force`) |
| Q9 | Keep `core` as a separate pure module, or fold it into the server? | Keep it: no database, fast tests, and plugin-host depends on it; it also carries the shared kernel (`Licence`, `Attribution`) |
| Q10 | Goal slugs are unchecked today (all 214 live slugs pass the article rule). Apply `Slug`'s rule to `GoalSlug`? | Yes; a new goal with an invalid slug is then refused with the article slug's message |
| Q11 | Reading a constrained newtype from the database: checked (fail with the column and value) or unchecked? | Checked; live data has 0 violations, and a violation should be loud |
| Q12 | Migrations: one directory in the server (unchanged files) or split per domain? | One directory, with the owner of each table recorded in §3.1; splitting would mean editing applied migrations |
| Q13 | Media allowlists: built-ins check `media.<provider>.licences`, plugins `media.collections.licences` / `per-collection.<name>`. Keep both key families, or fold the built-ins' keys into `per-collection.<name>`? | Keep both for this step (no key changes), as a `LicencePolicy` over the shared `LicenceFamily`; folding them is a separate settings change |
| Q14 | Markdown rendering: a `MarkdownRender[A]` type class with instances on the api types' companions, or one renderer object per feature? | The type class: it is "a function about a type", and the CLI and MCP share it |
| Q15 | Plugins before the domains they live in (slices 3–4 before 8–11)? | Yes, so plugin code moves once |
| Q16 | `cli` (76 files, flat) into `cli.<domain>` packages in slice 12, or later? | Slice 12: the move is cheap and the CLI help snapshot guards it |
| Q17 | The name of the places/periods/events subdomain | **Decided (user, 2026-10-30): `catalogue`.** `atlas` and `gazetteer` cover places only; `chronotope` is misleading. |
| Q18 | Enforce the direction (and the file-system and process rules) with `ArchitectureSuite`, or with one sbt project per domain and supporting area (the compiler, as `% Runtime` does for plugins)? | The test during the slices; split `server` into projects after slice 12 if the build-speed measurement shows a gain — by then it is mechanical |
| Q19 | The evidence that attests data points (attestations, event link candidates and checks, leads) in `catalogue` or in `library`? | `catalogue`: events use it 34 times and it uses library types twice; in the library it was the largest source of upward references (46) |
| Q20 | A `platform` subdomain at the bottom (jobs, identity, maintenance lock, work-list kernel, lint runner, plugin host)? | **Superseded (user, 2026-10-31):** platform dissolves — jobs, identity, plugins into supporting areas (Q29), the lock and the lint runner into `util.data`, the work-list kernel into the engine |
| Q21 | personal **pulls** (describe at read time; rename, merge and delete followed through uids, uid redirects and tombstones) or owners **push** changes to personal? Pull means a person's word search over marks matches the stored label, refreshed when the item is next shown | Pull: no subdomain has to know personal exists, and the stored label is already a column |
| Q22 | Pages that join several domains (source, place and event detail, About, Activity, the 360° data view): `server.views` above every domain, or the controllers of the highest domain involved? | `server.views`: it keeps every domain's controllers to its own data, and the direction rule allows it everything |
| Q23 | Gazetteer importers: Cliopatria, AWMC and ChronOntology become plugins (SPI-10, 329 lines, three fixture suites), while the bundled extracts (`GazetteerLoad`), PeriodO presets and Natural Earth land stay in `catalogue`? | Yes: the three read an upstream a user may switch off or replace; the others read data shipped with the install |
| Q24 | Goals: the engine's objectives (`automation.goals`; stage rules that name articles and sources contributed through `ObjectiveProgress`) or research's (the person's research goals, with the engine scheduling towards them through an interface)? Measured with the engine at the bottom: 186 upward references with goals in the engine, 226 with goals in research; goals use agents 12, worklists 5, managers 4, autopilot 4 and are used by workflows 15, agents 13, managers 7, autopilot 6, scheduler 5; co-change goals↔managers 0.22, agents 0.18, autopilot 0.18 | The engine's objectives now: their stages, plans, schedules, focus and links are workflow state. Revisit when goals gain content of their own (a written aim, its findings); then that content moves to research and the engine keeps an `Objective` reference to it |
| Q25 | `automation` is 33 k lines (agents alone 14 k). One subdomain, or two? | **Answered by the engine design (2026-10-31):** about 17–19 k lines remain once the article- and source-specific blocks (up to 5.4 k) go to their domains and the AI providers (3 k) become plugins |
| Q26 | The shared `Licence`: classify stored strings on read (no migration), or store the family in new columns? | Classify on read: `classify` is pure and fast, and no column changes |
| Q27 | `exchange` reads and writes every carried table in bulk with direct SQL. Through each owner's service, or a `Carried` interface the owners implement? | **Decided (user, 2026-10-31):** data management is contributed by each domain and run by `util.data`; `Carried` becomes `DataContribution.carried`, merge rules `DataContribution.mergeRules` |
| Q28 | Data management and file access: one supporting area or two, and their names (`util.data` + `util.storage`, or one `util.data` with a `storage` part)? | Two: `util.storage` (locations, stores, scratch files) is used by everything, data management included; `util.data` (registry, backup, bundle, merge, maintenance, checks, export, diagnostics) builds on it and on `util.database` |
| Q29 | Jobs and record identity (uids, redirects, tombstones, aliases): supporting areas (`util.jobs`, `util.identity`) or a domain layer? | Supporting areas: they hold tables but no research meaning, every domain uses them, and their only domain knowledge (job details, alias tables) becomes contributions (`JobKinds`, `AliasTables`) |
| Q30 | `ui_seen` (notices a person has seen): `server.instance` or `personal`? | `personal`: it is per person once #7843 lands; the instance pages read it through personal's service |
| Q31 | Agent tools: contributed by the domain that owns the data, or kept in the engine's toolbox? | **Decided (user, 2026-10-31): contributed by the owning domain, AND each workflow tells which tools it can use.** Domains offer tools (`ToolContribution`, calling their own services); a workflow declares per role the tools its agents may use (`WorkflowDefinition.allowedTools`), across domains where needed; the engine exposes only those to the agent, refuses others, and rejects a workflow that names an unknown tool at registration. The engine keeps the toolbox mechanism (names, result caps, MCP exposure). |
| Q32 | Managers and hints: mechanism in the engine, manager kinds (review, watch) contributed by research and library? | Yes: `ManagerSupervisor` is 76 % kind-specific by the block scan; the loop, holds, retries, cron, approvals settlement and hints stay the engine's |
| Q33 | The agent definitions generator (Claude Code, Codex, opencode trees; `just plugin`): engine or contributed? | The engine's generator, fed by the canonical skills and agents plus the roles and tools the domains contribute |
| Q34 | During the transition, the supporting-area and engine slices register today's per-kind code through thin adapters, which each domain's slice replaces? | Yes: it keeps every slice deployable and the snapshots unchanged; slice 12 checks no adapter is left |
| Q35 | The 360° data view: a new web page in this refactor, or only the registry and today's endpoints as views over it? | Only the registry and today's endpoints, MCP tools and CLI commands (byte-identical); the combined page is later UI work |
| Q36 | Store locations: derive every `Location` from today's config keys (data directory, originals, spool, basemap, backups, …), adding a key only where a location has none? | Yes; a new key gets today's path as its default, so no install changes |
| Q37 | Do the supporting areas also reach files only through `util.storage` (294 uses today), or only the domains (188)? | All of them: one place sees every file, so sizes, cleanup and the 360° view are complete |
| Q38 | Stand-alone tools (`pluginTool`, `generateApiIndex`, `generatePlugin`) and the composition root keep paths at their edge? | Yes: they are entry points that build or receive locations; the rule binds the server's domains and supporting areas |
| Q39 | Name the plugin kind for Calibre generally — `SourceCollection`, "a source of sources" (Calibre, archive.org now; a folder of PDFs, Zotero later) — rather than a Calibre-specific kind? | Yes: the library's running of it (search, import, gaps, sync, audit, link) is the same for every collection |
| Q40 | CLI plugins for a server on another machine: the native CLI hands plugin commands to a JVM-mode CLI, in a later slice 13? | Yes: a relink of the CLI costs minutes and gigabytes per plugin change; a JVM-only CLI slows every command; the hand-off costs about a second only for plugin commands |
| Q41 | Calibre write-back (adding missing sources, enriching Calibre's records): off unless configured? | Yes (`write-back = false` by default); writes are refused while the Calibre GUI holds the library, with today's message |
| Q42 | Which further integrations become plugins in this refactor (§8.5)? | Calibre and archive.org's collection now (slice 4); discovery indexes, Retraction Watch, citation export, the mirror format, base-map sources, backup targets and converter backends later, each when it gets a second implementation or a user asks |

## 12. Slice 1 as built (groundwork, 2026-10-31)

Branch `refactor-s1` from master aa819718. One commit per item, the snapshot baseline first. No endpoint, tool,
command, setting or table changed: the snapshots taken on master before anything else held byte for byte after
every commit.

**Contract snapshots.** `ContractSnapshotSuite` (server, no database) and `CliHelpSnapshotSuite` (cli) write
`src/test/resources/contract/` and compare byte for byte; `HISTMERSION_UPDATE_CONTRACT=1` rewrites them.

| snapshot | how it is made | size |
|---|---|---:|
| `openapi.json` | `OpenAPIDocsInterpreter` over `Api.all` (tapir-openapi-docs and apispec's openapi-circe, test scope): 220 paths | 852 kB |
| `mcp-tools.json` | `tools/list` of `McpTools` on a Netty listener on port 0: 79 tools | 187 kB |
| `toolbox.json` | each of the 10 agent tools as `/mcp/agent/<role>` lists it, and each of the 15 roles' tools and answer schema | 53 kB |
| `plugins.json` | the report of `Plugins.load` over `reference.conf` and an empty environment: 29 plugins | 10 kB |
| `settings.json` | the settings page of `SettingsService.InMemory` with no file and an empty environment | 342 kB |
| `cli-help.txt` | `--help` of every command, walked from the top: 240 commands | 175 kB |

The handlers run over a data source that refuses every connection, so the `query` tool's description is its
schema-less fallback (`OpsHandlers.queryToolDescription`) — the one MCP text the snapshot does not pin; step 2's
comparison read it from a test database. The environment is empty in all of them, so no setting of the machine
running the suite reaches a snapshot.

**`ArchitectureSuite`** (server, no database, about a second) reads the 1 242 main sources of `api`, `config`,
`files`, `core`, `db` and `server`. A file's place is its row in `architecture/places.tsv` while it is not in its
feature package — 1 228 rows: 1 224 from `files.md` (the 15 files of the personal pilot and three kernel files are
placed by their packages) and the 4 files slice 1 added in packages not by feature yet —
then its package. Three more tests keep the tables honest: every file has a place, no row is stale, and the
exception list is exact both ways (a new violation fails; so does a row nothing violates any more).

| rule | rows in `exceptions.tsv` | what they are |
|---|---:|---|
| direction | 455 | 281 between domains and the six measured supporting areas (the design measured 264 with a slightly different name rule), 78 into the composition root (`Handlers` 29, `Deps` 12, `Version` 8, …), 98 from `util.common` (`api/Markdown` 61, `Wording` 13, `Write` 11, …), `util.ocr` and `util.database` |
| file system | 132 rows, 421 uses | 168 uses in domains (automation 81, library 51, catalogue 30, media 6), 253 in supporting areas (basemap 116, data 86, settings 30, ocr 9, plugins 7, files 5) |
| process | 7 | `ProcessBuilder` outside `util.process` (agents' CLI runtimes, backup, the supervisor) |
| repository | 155 | a `…Repo` named outside its feature (`Handlers`, the engine, bundle and merge) |

Differences from §3.3, and why:

- `api` is scanned too: the measurement of §3.2 included it, and `api.<domain>` packages are where its features
  go.
- References into the composition root (`server`, `server.instance`, the `Handlers` split) count as violations:
  no domain may name them, and the measurement left them out.
- The file-system rule binds the supporting areas as well as the domains (Q37, accepted), so their 253 uses are
  listed; the stand-alone tools exempted are `pluginTool`'s file, `generatePlugin`'s file and `ApiIndex`, and
  the config root (it builds the locations) with the composition root.
- A name defined in two places, and the shared vocabulary of §3.2 (`Period`, `Filter`, `Paging`, `Label`,
  `Wikidata`, `ExternalId`, `Source`, `Target`), is not counted, as in the measurement.

**Generic newtype instances.** One given per library, from Iron's `RefinedType.Mirror`:

```scala
given encoder: [T] => (m: RefinedType.Mirror[T]) => (base: Encoder[m.BaseType]) => Encoder[T] =
  base.asInstanceOf[Encoder[T]]
given decoder: [T] => (m: RefinedType.Mirror[T]) => (base: Decoder[m.BaseType]) => Decoder[T] =
  base.emap(m.ops.either).asInstanceOf[Decoder[T]]
```

The form differs from §4.4 in one point: the constraint is checked with the companion's own runtime check
(`m.ops.either`, the `RuntimeConstraint` Iron keeps on every `RefinedType`), not by summoning an inline
`Constraint[m.BaseType, m.ConstraintType]`. That needs no inline given, compiles in this form, and checks exactly
what `X.either` checks; the §4.4 fallback (one line per newtype) was not needed. The instances:
`util.json.NewtypeCodecs` (circe and tapir, api, JVM and Scala.js), `util.database.NewtypeColumns` (Magnum; a
refused stored value fails with `column <label>: '<value>' is not valid: …`, NULL passes to `Option`'s codec),
`config.NewtypeReaders` (over config's lenient base readers) and `cli.NewtypeArguments` (decline). Nothing is
converted to them yet: the existing hand instances (`ValueCodecs`, the config companions) stay until their
features move; `NewtypeCodecsSuite` shows the generic ones give `Year`, `Doi`, `WikidataId`, `Latitude` and
`Longitude` the same JSON and schema as the hand ones.

**Identifier newtypes** in `values`: `Slug`, `GoalSlug`, `EventId` (`[a-z0-9][a-z0-9_.-]{0,120}`), `PlaceId`
(`[a-z][a-z0-9]*_.+`), `JobId` and `RunId` (lower-case UUID text), `SourceUid`, `ArticleUid`, `EventUid`,
`GoalUid`, `AssetUid` (UUID), `ArxivId`, `Pmid`, `Pmcid`, `Isbn`, `ArchiveId` (`Pure`), `PluginId` (the host's
`[a-z0-9][a-z0-9-]*`; the CLI's stricter template id stays), and `UserId`, moved from `domain.personal`. Each
has a round trip (`decode(encode(x)) == x`, its JSON equal to the base value's, its schema the base schema with
no name). Deviation: **`RunId` is in `values`**, not `domain.automation` as §4.2 has it, because the shared
kernel's `Caller.Automation(run: RunId)` names it and the kernel may not depend on a domain. The ids §4.2 places
in domain packages (`ManagerId`, `StepId`, `ApprovalId`, `QueueItemId`, `AssetId`, `ModelRef`, …) come with
their slices.

**`Transactor`, the system context, `Caller`.** `util.database.Transactor` (`read`, `transact` over Magnum's
`connect`/`transact`); `Deps.transactor` and `Deps.queryTransactor` over the application and read-only pools.
The server's entry point builds `SystemContext(clock, environment, userPaths)`: `config.Environment.of` the
environment the settings were loaded with (no second read), `UserPaths(environment, os.name)` (now also a value;
its pure functions stay on the companion for the CLI and config loading), `Clock.systemUTC()`; `Deps` carries
them. `Supervisor.start`/`reloadSource` take the paths instead of reading `System.getenv` and `os.name`; the
entry point's own reads (`HISTMERSION_RUNTIME`, `XPC_SERVICE_NAME`, `INVOCATION_ID`) go through the
`Environment`. Test servers get `SystemContext.isolated`. `domain.Caller` is `Person(user, role)` or
`Automation(run)`, `Caller.Local` the local user as `Admin`. Deviation: the role enum is **`Caller.Role`**, not a
top-level `Role`, because `api.Agents.Role` (the agent roles) is imported by wildcard beside `domain.*`.

**Entry points.**

| was | now |
|---|---|
| `com.histmersion.server.Main` | `com.histmersion.server.histmersionServer` (`Main.scala`); the wiring in `object Composition` (tests: `Composition.server`) |
| `com.histmersion.cli.Main` | `com.histmersion.cli.histmersionCli` (`Main.scala`); the command tree in `object Cli` |
| `com.histmersion.web.Main` | `com.histmersion.web.histmersionWeb`; `Compile / mainClass` set explicitly |
| `com.histmersion.server.plugins.PluginTool` | `…plugins.pluginTool`; its functions in `object RelinkPlugins` |
| `com.histmersion.server.agents.GeneratePlugin` | `…agents.generatePlugin(root)`; `object PluginTree` |
| `com.histmersion.pluginresolver.PluginResolver` | `…pluginresolver.pluginResolver`; `object DependencyResolver` |
| test tools `BasemapLabelsTool`, `BasemapExtractTool`, `PromptBudget` | `basemapLabels`, `basemapExtract`, `promptBudgetReport` |

Three objects were renamed because their `@main def` would differ from them only in case, and on macOS's
case-insensitive file system `pluginTool.class` and `PluginTool.class` overwrite each other (AGENTS.md rule 14
now says so). The test tools' mains got names that differ from their objects for the same reason. References
updated: `build.sbt` (three `mainClass` settings, the relink kit's `main-class`), `RelinkPlugins.kitManifest`'s
default, `scripts/bundle/{relink,server,server-jvm,kit-common}.sh` and the fakes of
`scripts/bundle/tests/{relink,kit-scripts}-test.sh`, `scripts/docs/screenshots.sh`,
`scripts/agents/claude-overhead.sh`, `tools/gen-cli-docs.sh`, `justfile` (`just plugin`), the suites
(`PluginToolSuite`, `PluginKitSuite`, `PluginResolverSuite`, `AgentGeneratorSuite`, `MainInitSuite`, 45 server
suites that build a test server), `docs/research-plugins.md`, the contributing guide, the basemap fixtures' README. The tools' usage
texts (`usage: PluginTool …`, `PluginResolver: …`) are unchanged. A relink uses the `main-class` file of its own
kit, so a kit built before the rename keeps working with its own scripts.

**AGENTS.md**: rule 14 (§5.4, with the case rule), rule 10's table of places and what each may depend on, with
how `ArchitectureSuite` keeps it, the `Transactor`, `SystemContext` and `Caller` in rules 11–13, and the steps
of adding an operation to a feature in its own package.

**Native image.** A tracing-agent run of the JVM server and CLI on an empty test database (scratch config, data and
cache, port 18080; the REST reads, the page and its assets through the browser suite, MCP `tools/list`, the CLI's
`health`, `search`, `about`, `queue`, `article list`, `--help`, `plugin list`) left the server's metadata unchanged
and added the lazy schema and codec fields `article list` decodes to the CLI's (a gap older than this slice). No main
class or composition object was ever named in either file, and the generic instances are casts plus the companion's
own check, so the rename needs no metadata. The bundle is not built in this slice.

**Contract snapshots and the process environment.** sconfig resolves `reference.conf`'s `${?VAR}` against the
process environment whatever environment a load is given, so the first baseline held the forked test group's
`HISTMERSION_SHUTDOWN_GRACE=0` as that setting's effective value. The suite now shows the default for a field whose
variable is set where it runs; that one line was the only change to any snapshot.

**Tests.** `just test-quick` (every module: `project/Dependencies.scala` changed) on a throwaway database: 4 638 tests, none
failed (2 skipped, 1 ignored). `relink-test.sh` 56 and `kit-scripts-test.sh` 65 passed. The
browser suite (Chromium) against a JVM server from this branch on an empty `*_test` database: 127 of 129 passed;
the two failures are the known ones (`#7381` goal stage colours needs goals, the map-start settings test passed when
run again).

## 13. Slice 2 as built (personal, the template, 2026-10-31)

Branch `refactor-s2` from master 6fcea4f0. Four commits of code and this record. `ContractSnapshotSuite` and
`CliHelpSnapshotSuite` held byte for byte after every commit: no endpoint, tool, command, setting or table changed.

**The one intended text change (#8010).** `pluginTool` and `pluginResolver` print their own names in the usage and
error lines (`usage: pluginTool describe …`, `pluginResolver: <error>`). No snapshot covers either text (the CLI help
snapshot is the `histmersion` command tree), and no script or suite matches them.

**`util.mcp`.** `McpOut` (a tool's Markdown answer and its refusal) and `ToolInputs` (the decoding settings of tool
arguments: Scala defaults honoured, unknown arguments refused) moved from the composition root to a supporting area,
so a feature's `…Tools` names no composition type. `McpTools` re-exports the given; the four other tool groups import it
from `util.mcp`.

**`Referable` in the shared kernel.**

| piece | module, package | what it is |
|---|---|---|
| `ItemKind`, `ItemKey`, `ItemUid`, `ItemRef`, `Described`, `RecordState` | core, `domain` | a reference to a record of another domain, and how it shows now |
| `Referable` (with `InMemory`), `Referables` | server, `domain` | the interface, its in-memory twin (rename, merge, delete), the registry by kind |
| `RecordTable`, `AliasColumns`, `TableReferable` | server, `util.identity` | resolve and describe over an owner's table: key and alias, uid, `uid_aliases` (followed up to 16 merges), `tombstones` |
| `SourceReferable` | `domain.library.sources` | `source`: citekey, `source_aliases`, uid |
| `ArticleReferable` | `domain.research.articles` | `article`: slug, `article_aliases`, uid |
| `CatalogueReferable` | `domain.catalogue` | `event` (id, `event_aliases`, uid) and `place` (id, `place_aliases`, no uid) |
| `PictureReferable` | `domain.media.assets` | `picture`: the asset's uid, shown by its caption |
| `GoalReferable` | `domain.automation.goals` | `goal`: slug, `goal_aliases`, uid |

`Readable` (`domain.personal.marks`, the interface package any domain may name) lists what a person has not marked
read; `SourceReadable` and `ArticleReadable` implement it with today's order (`lower(title)`, then the key), told only
the keys and uids the person marked.

**Personal.** `domain.personal` holds the shared types (`MarkTarget` — `Item(ItemRef)`, `Point`, `View`, `Span` —,
`MarkTime`, `TargetLabel`, `SearchWords`, `PersonalState`), `PersonalControllers`, `PersonalRoutes` and
`PersonalTools`; the features are `bookmarks`, `notes`, `reading` and `seen`, each a service trait with a private
`Implementation` over a `Transactor` and a public `InMemory` over the shared `PersonalState`, a `private[<feature>]`
repository taking the connection, and rows typed with the generic column codecs (`BookmarkId`, `NoteId`, `ItemKey`,
`ItemUid`, `Latitude`, `MapZoom`, `Year`, `ViewState`, `BookmarkLabel`, `NoteText`, `TargetLabel`). Every service
method takes the `UserId`; every controller method takes the `Caller` (`Caller.person`: an automated run has no marks
and is refused); `PersonalRoutes` and the tools pass `Caller.Local`. The clock is the composition's `Clock`.
`ItemTitles`, `PersonalRules` (its functions went to `MarkTime`, `TargetLabel`, `SearchWords`, `MarkTarget`,
`BookmarkLabel.tidy`, `NoteText.tidy`) and the per-row joins are gone. `ui_seen` is personal's `seen` (Q30): the
toolbar counters read the marks through `SeenService` and pass them to `UiRepo.counters`, which no longer joins the
table; the forward-only rule moved from `UiRepoSuite` to the seen contract.

**Pull (Q21).** A list reads the marks, then asks `Referables.describe` once per owner for the batch. A rename
shows the new key and title (the uid stored with the mark), a merge shows the survivor (`uid_aliases`), a deletion
shows the key and name stored with the mark (`tombstones`). A mark made before its item existed is described by its
key. A word search over bookmarks and notes matches the label or text, the title and the key **as described**: the
list is read whole, described, filtered, then cut to the page, which is today's result exactly (see deviations). A
note list narrowed to an item matches the stored key or any uid that key names now. Nothing writes on read.

**Contract suites.** `BookmarkServiceContractSuite` (4 scenarios), `NoteServiceContractSuite` (1),
`ReadingServiceContractSuite` (1), `SeenServiceContractSuite` (1), each run on `InMemory` and on a scratch database
(`PersonalServiceContract`), with a source renamed, merged and deleted under the marks; `ReferableSuite` runs the same
on `Referable.InMemory` and `SourceReferable`, and every owner's SQL on the database. `PersonalControllersSuite` runs
over the in-memory services; `PersonalTypesSuite` covers the pure rules.

**Listing speed.** A scratch `*_test` database with 3 000 sources (100 renamed with aliases), 500 articles, 1 000
events, 2 000 places, 200 goals; 3 000 bookmarks of every kind, 1 000 notes, 2 300 read marks. Service calls, median
of 21 after 5 warm-ups (ms):

| call | before (joins) | after (describe) |
|---|---:|---:|
| bookmarks, newest 200 | 1.1 | 3.2 |
| bookmarks, 1 000 | 4.2 | 7.9 |
| bookmarks of sources, 200 | 1.0 | 1.5 |
| bookmarks, two words | 5.3 | 15.9 |
| notes, newest 200 | 0.9 | 1.0 |
| notes on one source | 1.4 | 0.4 |
| read marks, read 200 | 3.1 | 3.2 |
| read marks, recently opened 20 | 1.1 | 0.5 |
| read marks, all 1 000 | 4.8 | 7.9 |
| not read yet, sources page 200 | 267.4 | 4.5 |
| not read yet, articles page 200 | 8.8 | 1.0 |

Every call stays under 20 ms; "Not read yet" over sources is 60 times faster, because the owner's query is now a
plain `NOT (key = ANY(…) OR uid = ANY(…))` instead of a correlated `NOT EXISTS … OR …` over `read_marks`.

**Architecture.** 9 rows left `exceptions.tsv`, all `direction`: 7 `McpOut`/`McpTools` into the composition root
(personal 2, library 1, automation 2, catalogue 2), `ItemTitles → Goal`, and `api/Queue.scala → personal: Note` (a
name collision that ends because `Note` is now defined in two personal places). One row left `places.tsv`
(`McpOut`). Two personal rows stay: `api/personal/ItemKind.scala` names `Goal` and `Place` — the wire enum's cases,
which the name rule reads as automation's and the catalogue's types. The enum is the OpenAPI contract and stays.

**Deviations from §3.9.**

- `Referable` takes no `DbCon`: each owner reads on a connection of its own through its `Transactor`, so personal's
  `InMemory` services can be given `Referable.InMemory` (§6.3) without a database. A list costs one connection
  checkout per owner; the figures above include it.
- `Described` is `(key, title, state)`: the key the record goes by now is what every reader needs; `at`, `years` and
  `route` come with the first reader that needs them (goal links, asset links, attestations). `title` is optional
  (none once deleted).
- The state type is `RecordState`, not `ItemState`: core's `ManagerPolicy` imports `api.Managers.ItemState` by
  wildcard in the same package, which a kernel `ItemState` would make ambiguous.
- The word search reads the person's whole list and filters after `describe`, instead of matching a stored label
  refreshed on read: it keeps today's results (the title and key as they are now), needs no write on a read, and
  measured 16 ms for 3 000 bookmarks.
- Catalogue implements one `CatalogueReferable` for events and places, in `domain.catalogue`, so five domains give five
  implementers.
- Visible only where today's join answered nothing: a merged item shows its survivor (Q21), a picture shows its
  caption, a mark made before its item existed shows the item's title, a read source stays read under a key it was
  renamed to. A stored row that cannot make a target (a point without its position) is left out instead of shown
  half-empty; writes never make one.

**Native image.** No wire codec or schema changed; the new rows and tuples are Magnum's compile-time `DbCodec`s, the
uid columns use Magnum's `UUIDCodec` (already read natively by the bundle repository), and the newtype columns are
casts. A tracing-agent run (`sbt server/run` from this branch on an empty `*_test` database, scratch settings, data
and cache, port 18080; the personal endpoints and the `bookmarks` tool by hand, then the browser suite) added the
lazy-val fields Scala 3 reads reflectively: the three rows' derived `DbCodec`s, `ToolInputs.configuration`,
`Handlers.referables` and `seenMarks`, and the wire `ItemKind[]`. The entries of the five deleted holders (the old
personal rows and `McpTools`' given) were taken out by hand. The CLI's metadata is unchanged: no wire record changed.

**Tests.** `just test-quick` (every module) on a throwaway database: 4 590 tests, none failed (1 skipped, 1
ignored). The browser suite (Chromium) against the traced JVM server on an empty `*_test` database: 126 of 129 passed;
`#7381` goal stage colours is the known one (no goals), the icon menus and the map-start settings test passed when
run alone, and `marks.test.mjs` passed whole (13 of 13) when run again.


## 14. Slice 3 as built (plugins I: fetchers and event catalogues, 2026-10-31)

Branch `refactor-s3` from master 6c3293c8. Five commits: the oracle first, then the shared library with the SPI
additions, the fetchers, the event catalogues, and the native metadata with this record. `ContractSnapshotSuite`
(`plugins.json` included: the same ids, versions, provides, states, settings and order, and the same default order
of the fetch chain) and `CliHelpSnapshotSuite` held byte for byte after every commit; no snapshot was rewritten.

**The oracle.** Two suites written on the unchanged code before anything moved, their transcripts committed with
them, and compared byte for byte after each later commit (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites them; it was never
set after the first commit):

| suite | what it runs (no socket: every request answered by a recording transport) | transcript |
|---|---|---|
| `FetcherTrafficSuite` | the stock fetch chain built from a config text (`Plugins.load` over a recording `HttpTransport`) over a fixed list of works — by DOI, arXiv id, PMCID and archive.org identifier, keyed and unkeyed CORE, a lending-only item, a CORE slow-down — each request with every header, in order, then the chain's answers | `traffic/fetchers.txt` |
| `EventTrafficSuite` | GVP (dry run, then stored), NOAA (two pages), Wikidata (with the label lookup), each again, `--refresh`, an alias with `--limit`; the stored `events`, `event_catalogue`, `event_import_parts` and `event_datings` rows; then `events rederive` (dry, whole, one catalogue) over rows made stale (a record as V085 stored it, spans that no longer match their records) with a transport that refuses every request; the stored rows again | `traffic/events.txt` |

The one edit to a transcript after the first commit is a column taken out: the `origin` of a stored row is the
database's instance id, which differs between two test databases (the suites compare rows without their clock and
id columns already). The same requests in the same order with the same headers, and the same stored rows and
rederive reports, came out after each of the three moving commits.

**`upstreams`** (`modules/upstreams`, depends on `plugin-api`, circe and sttp's core; registers nothing). One copy of
each upstream's client and parser, read by the host and the first-party plugins alike: `AcquireParse` (Unpaywall,
OpenAlex, CORE, Crossref, DataCite, Europe PMC, PMC's OA service, the landing page's `citation_pdf_url`),
`OpenAlexCrossref` (discovery, references, follow-ups, update notices), `ArchiveOrg`, `CoreApi` with
`CoreFailure`, `RepositoryLanding`, `Jats`, `CatalogDating` (the four-point span, the BCE/CE shift, Wikidata
precision, the clamp, the central year read back) and `WikidataClasses`, over `UpstreamHttp` (the SPI's
`PluginHttp`). Their records (`WorkRef`, `RefRow`, `FollowupRow`, `UpdateNotice`, `ArchiveItem`, `ArchiveFile`,
`OaAccess`, `PmcRecord`, `PmcObject`, `EuropePmcHit`) are the library's own; the server maps them to the wire with
the extensions in `domain/library/acquisition/UpstreamRecords`. `Identifiers` repeats `api`'s DOI and arXiv
normalisation (`upstreams` cannot see `api`); a suite asserts the two agree over the same inputs.

**The SPI additions**, all additive and defaulted; `Spi.Version` stays 2, and a plugin compiled against master's
`plugin-api` links and registers unchanged (below).

| | member | default | the host's backing |
|---|---|---|---|
| SPI-1 | `PluginHttp.fetch(url, headers): Either[String, PluginHttpResponse]` (status, headers, body) | `get`'s body as status 200 | `PolicyHttp`: one request, never retried, the policy's User-Agent; `get` keeps its retries |
| SPI-2 | `PluginContext.quota(upstream): UpstreamQuota` (`take`, `observe`, `waitingUntil`) | `UpstreamQuota.Unlimited` | `CoreBudget.quotas`: `core` is the server's one `CoreBudget` (`upstream_quota`), the same instance discovery spends; any other name is unlimited |
| SPI-3 | `FetchContext.registered(pluginId): Boolean` | `false` | the generation's registered ids (`Generation.registered`), passed to `FetchChain.of` |
| SPI-7 | `EventCatalogue.reread(stored: CatalogueEvent): Option[CatalogueEvent]` | `None` | `events rederive` re-reads each stored record through its catalogue (`PluginEvents.reread`), then dates it as the import does |

`SpiAdditionsSuite` (plugin-api) checks each default; `SpiBackingSuite` (plugin-host) the host's backing;
`CoreQuotaLendSuite` and `CoreBudgetSuite` (server) that a plugin's CORE call and the new-source search spend one
budget — a plugin's `take` lowers what discovery has left, a 429 a plugin observed makes discovery wait, and the
reverse.

**`plugins/fetchers`** (`archive-org`, `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`, `arxiv`) and
**`plugins/events`** (`events-gvp`, `events-noaa`, `events-wikidata`): `projectMatrix` rows over `upstreams`, the
testkit for their tests, each registered in its `META-INF/services/com.histmersion.plugin.Plugin` with its defaults
in `META-INF/histmersion/plugins/<id>.conf` (moved from the server). The server depends on both at run time only
(`runtime->compile`), so no server source names a plugin class; `server/run`, `writeClasspath`, `relinkKit` and
`nativeImage` read `Runtime / fullClasspath`. Each is switched off by `histmersion.plugins.<id>.enabled = false` as
before. `scripts/dev/affected.py` knows the three new modules, so `just test-quick` tests them with their
dependencies. Their suites run them through the plugin testkit as an author runs a plugin (`FetcherPluginsKitSuite`,
`CataloguePluginsKitSuite`), and the parsers' cases moved from core and server with them (`CataloguesSuite`).

**First-party = a reserved name loaded from the image's own class path.** `ReservedNames.firstPartyPlugins` (29
ids) keeps a third party off a built-in name; `Plugins.firstParty` adds that the plugin did not come from a set a
relink linked in. Only a first-party plugin is supplied host settings, and only a first-party one may register a
reserved catalogue or collection name.

**`FirstPartySettings`** (plugin-host) is the one table of which host paths each first-party plugin reads: the
fetchers `histmersion.acquire` and `histmersion.media.max-retry-after-seconds`, `oa-landing` also
`histmersion.plugins.oa-unpaywall.contact-email`, each catalogue its `histmersion.events.<catalogue>` (GVP also
`histmersion.events.min-year`), the media collections and AI providers none (they read theirs at each use). The
paths are supplied under the plugin's own block, below the user's block and the environment, above the defaults;
the report shows the plugin's own block alone, and a host key written into a plugin's block is an unknown setting.
The same table is the reload rule (`Plugins.carried`): a first-party plugin is rebuilt when its block or one of its
paths changes, any other plugin when its block or a key of the shared client does. It replaces `Plugins.carried`'s
`independent` list and `LandingGroup`. Users keep writing `histmersion.acquire.*`, `HISTMERSION_ACQUIRE_…` and
`histmersion.events.<catalogue>.*`.

**Gone:** `Builtin`, `BuiltinWith`, `BuiltinConfig`, `BuiltinFetchers`, `Plugins.builtins`, `CoreFetcher`,
`Fetching`, `OaIndex` (and its registration state), `HostEventCatalogue` and the marker design (E4), core's
`CatalogDating` and the catalogue parsers of `EventProviders` (the host's merge, threshold and scope rules stay).
The host's import loop (`EventCatalogHandlers.run`) runs a first-party catalogue exactly as a third party's; a
catalogue that says `everyPartEachImport` (NOAA) has every part read at every import, as NOAA's pages were.
`BuiltinCatalogues` is now the table of the three first-party ids, their aliases and their reserved names.

**Architecture.** Deletions only. 16 rows left `exceptions.tsv`, all `direction`: `JatsConverter → Jats`,
`OpenAlexCrossref → UpdateNotice`, `Version` in `Builtin`, `BuiltinFetchers` and `BuiltinWith`, `Builtin` in
`BuiltinAi`, `BuiltinCatalogues` and `BuiltinMedia`, `BuiltinMedia → BuiltinWith`, `LegacyPlugins →
BuiltinFetchers` and `→ OaIndex`, `Plugins →` `BuiltinCatalogues`, `ArchiveOrg`, `BuiltinFetchers`, `Http` and
`OpenAlexCrossref`. 20 rows left `places.tsv`: core's `CatalogDating`, `Jats` and `RepositoryLanding`; the server's
`ArchiveOrg`, `CoreApi`, `CoreFailure`, `OpenAlexCrossref`, `HostEventCatalogue` and the twelve files of
`server/acquire/fetchers`. The new server files are in their feature's package (`domain/library/acquisition`).

**Deviations from §8.**

- `CoreQuota` (the budget's arithmetic) stays in core: the plugin reaches the budget through SPI-2, and moving it
  would make `db` depend on `upstreams` and add a second parser of `Retry-After`.
- `reread` takes the stored event, not its raw record alone: NOAA needs the stored id for its dataset, Wikidata the
  stored kind, GVP's records stored before V088 have no raw record and are read back from their span and
  description.
- Two members beyond the four planned, both defaulted: `EventCatalogue.everyPartEachImport` (NOAA's pages were read
  at every import; without it a second import would have skipped them) and `UpstreamQuota.waitingUntil` (CORE's
  "not asked until …" answer names the time).
- The Wikidata steps that are host rules, not reading (the label and alias lookup, the more-specific-kind merge) stay
  host-side, keyed by the catalogue's name, so the requests stay identical.
- A first-party catalogue switched off is not re-read by `events rederive` and its floor is not applied: the
  re-derivation asks the catalogue, and a catalogue not registered states nothing (its rows are only held inside
  the present, as a third party's are).
- The media collections and AI providers stay host code wrapped by `util/plugins/HostProvided` until slice 4; only
  catalogue and collection names are checked against `firstParty`.
- A renamed parameter: the host's anonymous `PluginHttp` in `PluginEvents` and `PluginMedia` names its client
  `polite`, because a parameter named `fetch` is ambiguous with SPI-1's member there. A third-party plugin that
  writes an anonymous `PluginHttp` with a local named `fetch` meets the same compile error on recompilation; a
  compiled jar is not affected.
- `Plugins.offline` (a test's or a database-less tool's generation) now lists the secret vaults too, disabled — it
  is built from what ServiceLoader finds, as the server's is.

**A plugin built against master's SPI.** The jars of the `hello` (a source fetcher) and `hello-ai` (an AI
provider) test plugins, built from master before this branch, were put on the class path of this branch's server
(`server/writeClasspath`, then `java -cp … --self-check` with both enabled in a scratch config): both registered —
`hello 0.1.0 — registered (source-fetcher:hello)`, its settings and greeting as configured, `hello-ai 0.1.0 —
registered (ai-provider:hello-ai)` — beside the 29 first-party plugins, exit 0. The testkit's own suites (which
load the fixtures as an author would) pass unchanged.

**Native image.** The plugin modules carry their own metadata, as a third-party plugin jar does:
`META-INF/native-image/com.histmersion/plugins-fetchers/`, `…/plugins-events/` and `…/upstreams/`
`reachability-metadata.json`, each with its classes' lazy-val fields, the fetchers' and catalogues' defaults
resources and the services file. The server's file lost the ten defaults resources (now in the plugin modules) and
the six entries of the deleted `server/acquire/fetchers` classes. A tracing-agent run recorded the new entries
(`sbt server/run --self-check`, then `sbt server/run` from this branch on an empty `*_test` database, scratch
settings, data and cache, port 18080: health, about, config, the catalogue list, the events list, the jobs, an
`events rederive`, and an `events import gvp --dry-run`, the gazetteer's first-run import alongside); the agent's
additions under `com.histmersion.plugins.*` and `com.histmersion.upstreams.*` were then moved into those modules'
files. Two holders whose lazy values only a fetch reads, `OaRecords` (`openalex`, `datacite`, `handle`) and
`Pmc$PmcLookup` (`hit`, `record`), were carried over by hand from the deleted classes' entries, their field names
read with `javap`. The server's other new entries are `HostProvided$NoSettings$`'s reader and two givens of the
import's merge (`CatalogMerge$Fit$`, the `CatalogRecord` schema). The CLI's metadata is unchanged.

## 15. Slice 4 as built (plugins II: media, gazetteers, AI over HTTP, Calibre and archive.org, 2026-10-31)

Branch `refactor-s4` from master a3e86377, built as four streams (media; gazetteers; AI over HTTP; Calibre with
archive.org), each with its oracle committed first on the unchanged code, then merged. `CliHelpSnapshotSuite` held byte
for byte after every commit. `ContractSnapshotSuite` changed only by additions, each intended (below); no snapshot or
transcript was rewritten to make a test pass.

**The oracles.** Recorded before anything moved, compared byte for byte afterwards (`HISTMERSION_UPDATE_TRAFFIC=1`
rewrites them; never set after the first commit of each stream):

| suite | what it runs (no socket leaves the machine) | transcript | result |
|---|---|---|---|
| `MediaTrafficSuite` | the ten collections' dry runs over every place and event of a fixture map, a single-target import of each, two refreshes, a job of each, the switched-off and keyless refusals, the provider lists, on a scratch database; every request with every header, each report, then every stored asset (bytes by digest), link (role, property, item, rank, basis), check and Commons category | `traffic/media.txt` (1 211 lines) | identical |
| `GazetteerTrafficSuite`, `GazetteerStoreSuite` | Cliopatria (zipped), AWMC and ChronOntology through a loopback server — the three suites' fixtures and edge fixtures (years as strings and floats, undated slices, one-ended spans, a zip with no usable file, a non-JSON body, an HTTP 503); the requests, every `PlaceInput` (parents first) and the dry-run result; then real imports and re-imports into a scratch database, the stored places, names and relations | `traffic/gazetteers.txt`, `traffic/gazetteers-stored.txt` | identical; `java.net.http` sends no `Accept` or `Connection` header, which `HttpURLConnection` did (left out of the transcript) |
| `AiTrafficSuite` | 46 agent-loop scenarios for anthropic, openai and local against a loopback server set as each `base-url` (tools over several iterations with the iteration notes and the final no-tools call, typed answers, strict tools on and off, token limits, HTTP errors, 429s, malformed bodies, timeouts, nothing listening, no key, bad URLs); every request (method, path, headers, the whole body), then what the loop returned and what the engine made of a failure | `traffic/ai.txt` | bodies, order, results and engine messages identical; two transport headers differ on every request: sttp's backend sent the JDK's `User-agent: Java-http-client/…` and `Accept-encoding: gzip, deflate`, `HostAiHttp` sends the host's User-Agent and no `Accept-Encoding` — the suite maps exactly those two recorded lines before comparing |
| `CalibreCommandsSuite` | 66 steps of every `calibre` command (search incl. `--unlinked` and the sqlite3 fallback, import incl. the volume refusal and `--no-convert`, link, push, `push --replace`, update one and `--all` dry/`--json`/`--apply`, set and its refusals, audit, sync dry/`--json`/`--apply` with a failure, gaps with and without `--quiet` and its exit codes, `--json`, with `originals-dir`), the CLI as its own process against a test server over a fixture library with a fake `calibredb` and a logging `sqlite3` whose argument logs are in the transcript; writes on a copy | `calibre/commands.txt` | identical; for the server-side steps the CLI's own Calibre tools point at programs that do not exist, so the server did the work |
| `ArchiveTrafficSuite` | archive search, info and import through a recording transport, with the stored rows | `traffic/archive.txt` | identical |

Normalised in the Calibre and archive transcripts only: scratch paths, the server's port, temp directory names, uids,
job ids and timestamps.

**SPI additions** (all additive and defaulted; `Spi.Version` stays 2):

| | member | default |
|---|---|---|
| SPI-4 | `MediaItem.role`, `MediaCollection.roles`; `MediaCollection.Roles` widened to the stored set (`locator-map`, `plan`, `aerial-view`, `route-map`, `depiction` added); `MediaItem`'s 11-argument constructor and `apply` kept | `None`, `Set(role)` |
| SPI-5 | `MediaCollection.searchEvent(event, http)`, `MediaEvent(id, label, wikidata, period)`, `MediaCollection.asks: Set[MediaTarget]` | `Right(Nil)`, `Set(Places)` |
| SPI-6 | `MediaMatchRule.Own` | — |
| SPI-8 | `AiCapabilities.iterationMarkers` (the host passes sttp-ai's `[Iteration n of m]` entries), `AiCapabilities.verbatimFailures` (a failure is the run's whole error text, unprefixed), `AiTurnRequest.answerSchemaRendered` (the schema as sttp-ai encoded it, `$schema` kept) and `answerDescription`, `AiUsage.reasoning`; the old constructors and `apply`s kept | off, `None`, `0` |
| SPI-10 | `PluginContext.gazetteerSources`; `GazetteerSource(title, description, rights, fetch(http))`; `GazetteerRights(licence, credit, licenceUrl)`; `GazetteerPlace`/`GazetteerName`/`GazetteerRelation`/`GazetteerOpenSide` in the store's shape | refusing registry |
| SPI-11 | `PluginContext.sourceCollections`; `SourceCollection` (below) with `CollectionId`, `CollectionCapability` (`Read`, `Add`, `Update`), `CollectionItem`, `CollectionFile`, `CollectionQuery`, `CollectionMetadata`, `CollectionChange`, `CollectionLink`, `CollectionUpdateEntry`/`Outcome`, `CollectionRecord`, `CollectionIssue`, `CollectionFinding` | refusing registry; every write refuses |
| beyond §8.3 | media: `MediaCollection.searchTargets(places, events, http): MediaFound` and `completeAll(items, http): MediaCompleted` with `MediaUse`, `MediaFound`, `MediaCompleted`, `MediaTargetId`; `MediaItem.rendition: Option[MediaRendition]`; `MediaCollection.check`, `refusalBackoffMs`, `latestYear`, `placesCarrying`; `MediaPlace.ids` (6-argument constructor and `apply` kept); `PluginContext.licences: LicenceReader` → `LicenceRead(id, family, needsAttribution)` | per target / per item; `None`; `Right(())`, `Nil`, `None`, `None`; `Nil`; reads everything as unknown |

`SpiAdditionsSuite` checks every default and every kept constructor; `GazetteerSpiSuite` the gazetteer records.

**`plugins/media`** (`media-wikidata`, `media-depicts`, `media-met`, `media-cleveland`, `media-aic`, `media-loc`,
`media-pleiades`, `media-opencontext`, `media-smithsonian`, `media-europeana`; the two keyed ones off by default as
before). The providers' parsers moved from core with them (`MetMuseum`, `ClevelandArt`, `ArtInstitute`,
`LibraryOfCongress`, `OpenContext`, `Smithsonian`, `Europeana`, `Commons`, `CommonsDepicts`, `PleiadesLinks`,
`FreeTextDates`, `MediaRoles`) and now produce `MediaItem`s with the licence as the provider states it: an id where
the provider gives a flag (`CC0-1.0` for the Met's `isPublicDomain`), the URL where it gives one (Open Context,
Europeana), the machine id or short name Commons gives (M4). `MediaText` and Wikidata's entity reading
(`WikidataEntities`, `WikidataItem`, `WikidataStatement`) moved to `upstreams`, shared with the host's label lookup.
Each plugin is supplied `histmersion.media` by `FirstPartySettings` (M6), so a media key now rebuilds the ten plugins
where the host used to read it at each import; users keep writing `histmersion.media.*`. The host's one import runs
every registered collection, first-party or not (M1): `MediaImporter.of` chooses the targets from `asks` and
`placesCarrying`, the pace and the refusal patience from the collection, then `CollectionImport` over `PluginCollection`
for a collection searched by a place's name, or the new `domain/media/OwnImport` — the former `CommonsFiles` loop,
generalised — for one of its targets' own pictures (`MediaMatchRule.Own`: Wikidata, depictions, Pleiades), which is
asked about a whole batch and completes the files to store in one batch, so Wikidata's 50 items and Commons' 50 titles
a call stay as they were. The host classifies every stated licence with `MediaLicence` and judges it against the
collection's allowlist: `BuiltinMedia.licencesFor(name)` answers a first-party collection from `media.<provider>.licences`
(Q13), a third party's from `media.collections`. `BuiltinMedia` is now the table of the ten names: the plugin each
belongs to, what a refusal calls it, the name its checks are recorded and its pictures stored under
(`wikimedia-commons` for the three Commons-backed ones), its allowlist, the aliases and the reserved names. Gone:
`HostMediaCollection`, `KeyedEntry`/`KeyedMeta`/`KeyedConfig`/`KeyRefusal`, the eight per-provider classes,
`WikidataCommons`, `CommonsDepictions`, `PleiadesImages`, the pluginhost media-settings extensions but the testkit's and
the illustrations', and `HostProvided` for media. The parser suites moved to the plugin (`CollectionParsersSuite`,
`PortalParsersSuite`, `KeyedParsersSuite`, `CommonsParsersSuite`, `MediaPluginsKitSuite` through the testkit); the
match and licence rules' cases stay in core; the server's collection suites run the plugins through the host's new path.

**`plugins/gazetteers`** (`gazetteer-cliopatria`, `gazetteer-awmc`, `gazetteer-chronontology`), each registering one
source under its old name, now reserved (`ReservedNames.gazetteerSources`, with `land`, `periodo` and the bundled
extracts' names). A place with no source of its own is stored with the dataset's credit, which reproduces today's
source lines. `PluginGazetteers` (plugin-host) guards the answer, converts each place to `PlaceInput` and lends each
download a client of its own: the gazetteer import's User-Agent, asked once (SPI-1 `fetch`), the timeouts of the new
`places.fetch-timeout-seconds` (120) and `places.fetch-connect-timeout-seconds` (30), which replace two constants.
`ImportRunner` runs any registered source (G2); Cliopatria's parent concepts now come first in its plugin's `fetch`, so
the host names no source. `server/domain/catalogue/places/BuiltinGazetteers` keeps the names, titles and order of
`places fetch` and `init.fetch-sources`, which are unchanged; a source switched off names its switch. `GazetteerLoad`,
PeriodO and Natural Earth stay host code (Q23). The three fixture suites moved to the plugin with every assertion.

**`plugins/ai-http`** (`ai-anthropic`, `ai-openai`, `ai-local`), over plugin-api, circe and sttp's model (for
`Uri`'s parsing and its messages). Each implements `AiProvider.turn` itself over the host's `AiHttp`: the Anthropic
Messages API, and the OpenAI chat-completions API for openai and local, with sttp-ai's own request shapes
(`ensureObjectType`, `normalizeForStrict`, the 4096 default, the system message, `call_<i>` ids) and decoders (so
failure texts are the same). The host loop stays sttp-ai's through `PluginAiClient` (A1); the server no longer links
sttp-ai's `claude` and `openai` modules, only `core`. `FirstPartySettings` supplies each its
`histmersion.agents.providers.<name>` (A2); `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` work as before, and a change to those
keys rebuilds that one plugin. `HostAiProvider` and `HostProvided` now cover only the three CLI runtimes, until slice 12
(SPI-9). `AgentDoctor` keeps its HTTP-provider checks host-side (below). `domain/automation/agents/VerbatimFailure`
carries a verbatim provider's error.

**Calibre and archive.org as source collections (SPI-11).** `SourceCollection`: `title`, `description`,
`capabilities`, `readOnlyReason`, `reach`, `search(query)`, `item(id)`, `admits(item)`, `file(item, format)`,
`original(item, file)`, `cover(item)`, `add(metadata, file)`, `replace(id, key, file)`, `update(id, metadata)`,
`updateAll(links, apply)`, `set(id, fields)`, `audit(records)`; `HostCollection` (plugin-host) guards every call and
refuses a write whose capability is not declared. `domain.library.collections` defines and runs it:
`CollectionsService` (trait, `Implementation` over a `Transactor`, `InMemory`), `CollectionsControllers` (writes behind
the maintenance lock), `CollectionsRoutes` (twelve `/api/collections/{name}/…` endpoints) and `CollectionsTools` (one
`collection` MCP tool, not offered to agents), writing through `CollectionLibrary` — the same handlers the endpoints run,
`AcquireRepo`'s gap and push-metadata queries reused through `AcquireHandlers`. `plugins/calibre` (`calibre`) holds the
CLI's reader and writer, ported: `calibredb list --for-machine` with the `sqlite3 -readonly` fallback, `add`,
`set_metadata`, `add_format`, the metadata guard and the GUI refusal. Its settings are its own block:
`library-path` (`~/Calibre Library`, `${?HISTMERSION_CALIBRE_LIBRARY_DIR}`), `calibredb`
(`${?HISTMERSION_CALIBRE_CALIBREDB}`), `sqlite` (`${?HISTMERSION_CALIBRE_SQLITE3}`), `write-back`, and `gui-process`
(the process the GUI check looks for, so a test can make it deterministic). The `archive-org` plugin registers
archive.org as a read-only collection too, which now runs `archive search|info|import` with the same endpoints, tool,
output and lending-only refusal; `ArchiveClient`, `ArchiveOrgClient` and `AcquireHandlers.archiveSearch/archiveInfo`
are gone.

*The CLI.* Every `calibre` command first asks `GET /api/collections/calibre`; when the server answers
`reachable: true`, the command is an HTTP client of the server, with the same output code (`CollectionsMarkdown` in
api, the same `--json` shapes). Any other answer — a 404 from an older server, the plugin switched off, an unreachable
library, no server — runs today's host-side code unchanged (§8.6: kept until slice 13). Always host-side: `push --file`,
a `push` when `cli.calibre.originals-dir` holds the source's file, and `gaps`/`sync` whenever `originals-dir` is set
(only the CLI sees that directory). A write the server refuses because write-back is off is not retried on the host.

*Write-back.* The plugin's defaults resource sets `write-back = true` (`${?HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK}`),
documented there and in the configuration reference: `calibre push`, `sync --apply`, `update` and `set` are this
install's write path, and the research workspace's hook (`calibre gaps --quiet`, then `calibre sync --apply`) depends
on them. `write-back = false` makes the collection read-only (`{Read}`), and every write refuses with "Calibre is
read-only here: write-back is off — set histmersion.plugins.calibre.write-back = true (…) to let the server write to the
library". This departs from Q41's "off unless configured" in its default only.

*Reach.* `reach` (on `GET /api/collections/{name}` and the tool's `list`/`info`) checks the library folder and
`metadata.db`, then reads 16 bytes of it. The server runs as the user's LaunchAgent, so it reads `~/Calibre Library`
as the user does; a library in Documents, Desktop, Downloads, iCloud Drive or on an external volume needs Full Disk
Access (or Files and Folders) for the server's binary — `libexec/histmersion-server`, or the relinked one — in System
Settings → Privacy & Security, which the check says when the read fails; a server in a container needs the library
mounted. The check runs only on request, and `Plugins.offline` leaves `calibre` out, so no test or database-less tool
reaches a real library. Every library test refuses to run on `~/Calibre Library` or `$HISTMERSION_CALIBRE_LIBRARY_DIR`
(`CalibreFixture.refuseRealLibrary`, after `~` expansion and `toRealPath`); the fake `calibredb`/`sqlite3` refuse any
directory without the fixture's marker.

**The contract snapshots.** `cli-help.txt`, `toolbox.json` unchanged. `plugins.json`: the three gazetteer plugins and
`calibre` added (registered, `gazetteer-source:<name>` / `source-collection:calibre`; calibre's five settings), and
`archive-org` provides `source-collection:archive-org` beside its fetcher; every other entry unchanged (media, AI:
same versions, provides and empty settings). `openapi.json`: the twelve collection paths and 27 `Collection*` schemas
added, nothing changed or removed (compared parsed). `mcp-tools.json`: the `collection` tool added. `settings.json`:
the two `places.fetch-*` keys added.

**Architecture.** `exceptions.tsv` lost 10 rows: `api/Markdown → Commons`, `core/Europeana → ExternalIds`,
`core/OpenContext → ExternalIds`, `server/media/BuiltinMedia → Deps`, `HostMediaCollection → Deps`, `KeyedEntry →
Deps`, `Plugins → BuiltinMedia`, `ImportRunner → CliopatriaImporter` and `→ GazetteerImporter`, and
`server/agents/LlmClient → Version`. `places.tsv` lost 33: core's twelve moved parsers and `MediaText`, the server's fifteen
moved or deleted media files, the four gazetteer importers and `server/acquire/ArchiveClient`. The new server
files are in their feature packages (`domain/media`, `domain/library/collections`, `domain/catalogue/places`,
`domain/automation/agents`). One rule changed: an `api.<domain>.<feature>` package now has the place
`<domain>.<feature>`, as a domain package does (the collections' 27 wire records live in `api.library.collections`).

**Deviations from §8.**

- Media: the batch members, `rendition`, `check`, `refusalBackoffMs`, `latestYear`, `placesCarrying`,
  `MediaPlace.ids` and `PluginContext.licences` are beyond §8.3. Without the batch members a per-target search would
  have asked Wikidata and Commons once per item instead of 50 at a time; without the lent `LicenceReader` the
  Commons, Open Context and Europeana parsers could not give today's refusal reasons and credit lines (they decide on
  the classified family — whom a licence requires to be credited, which of two statements is known, a short name)
  without a second copy of `MediaLicence` in the plugin. The host still reads every stated licence itself.
- Media: a refusal Commons decides (non-free, unreadable, all rights reserved, no author to credit) is a `withheld`
  item with its licence stated, not a `Left`, so the counts keep its licence key as before.
- Media: `OwnImport` is a second host loop beside `CollectionImport`, chosen by the match rule, because the two kinds
  report and store differently (declared sizes and Commons' thumbnails against measured ones and made ones, the samples,
  the category links); a third party may choose either.
- AI: two SPI-8 members beyond prompt caching and strict tools, which the built-ins did not need as SPI members (strict
  tools is the plugin's own setting; there is no prompt caching on this path). `AgentDoctor` stays host-side: moving its
  checks into the plugins' `doctor` would add a plugin section to `agent doctor`'s output.
- Gazetteers: two settings keys replace constants (`places.fetch-*`), an addition to `settings.json`. A year beyond an
  `Int` is now read as absent, an inverted period is ordered (it made Postgres reject the batch), a refused place is
  listed in `failed`; none occurs in the first-party data.
- Calibre: the SPI is wider than §8.5's signature (`file`/`original` split so the `calibredb` calls stay as they were,
  `replace`, `updateAll`, `set`, `admits`, `cover`, `reach`, `readOnlyReason`), and `write-back` defaults to `true`
  (above). Its settings come from its own block with the `HISTMERSION_CALIBRE_*` variables, not from the CLI's
  `cli.calibre.*` file keys. Linked-source listings are no longer capped by `query.max-rows`. Link-based operations are
  Calibre-only (sources keep one `calibre_id`). With `archive-org` switched off, `archive search|info` refuse.
- Calibre: the reader, writer and output code exist twice until slice 13 (the CLI keeps its host-side copy, §8.6);
  `media covers` still reads Calibre on the host (media covers move with slice 9).
- Tests: the server's tests depend on the CLI (`test->compile`) so the Calibre oracle can run it, and on
  `pluginsCalibre`'s tests (`test->test`) for the fixture library. `Plugins.offlineWith` takes an environment and
  carried plugins, and reads the host's own settings from the user block too, so a test supplies the first-party
  plugins' settings as text.

**A plugin built against master's SPI.** The `hello` and `hello-ai` jars built from master a3e86377 were put on this
branch's server class path (`server/writeClasspath`, then `java -cp … --self-check` with both enabled in a scratch
config, data directory and home): both registered — `hello 0.1.0 — registered (source-fetcher:hello)`, its greeting
as configured, `hello-ai 0.1.0 — registered (ai-provider:hello-ai)` — beside the 34 first-party plugins, exit 0.

**Native image.** Each new module carries its metadata, as `plugins-fetchers` does:
`META-INF/native-image/com.histmersion/plugins-media/`, `…/plugins-gazetteers/`, `…/plugins-ai-http/` and
`…/plugins-calibre/` `reachability-metadata.json`, each with its plugins' `configReader$lzy1`, its settings records'
`derived$ConfigReader$lzy1` (read with `javap`), its defaults resources and its services file. The server's file lost
the media, AI and archive defaults resources, `KeyedConfig$`'s reader and sttp-ai's `SchemaSupport$$anon$1`, and
gained the collections' 27 wire records' lazy fields and `CollectionsTools$CollectionInput$` (the CLI's file the wire
records too). No tracing agent was run: every path a plugin's new code takes is reached only by a request to an
upstream or a model, and slice 3's tracing once reached a real service; the entries were written by hand from `javap`.

## 16. Slice 5 as built (supporting areas, 2026-10-31)

Branch `refactor-s5` from master 5550fa72: the oracles first, then `util.storage`, then three streams (the base map;
settings and plugins; jobs, identity, processes and OCR), merged. `ContractSnapshotSuite` and `CliHelpSnapshotSuite`
held byte for byte after every commit; no snapshot or oracle was rewritten after the first commit.

**The oracles** (server tests, recorded on the unchanged code, `HISTMERSION_UPDATE_ORACLE=1` rewrites them):

| suite | what it records | file | result |
|---|---|---|---|
| `RegistriesOracleSuite` | every job kind (identifier, name, pause/cancel, the subject a job of it shows for each kind of detail and for a source), the name of an unknown kind; every alias table (noun, identifier word, table and columns, the rule and the "followed" note), which bundle kind has which | `oracle/job-kinds.txt`, `oracle/alias-tables.txt` | identical; read from the registries since |
| `DataTreeOracleSuite` (DB) | a server over one scratch root — settings changed twice, a reload, an upload converted, an acquisition spool, the base map fetched (every layer, a focus region, a regional relief), tiles read from the extracts and on demand, a focus region removed — then every path under the root, its kind and a file's size (text files with the root written `ROOT`; archives without size, their metadata carries a time) | `oracle/data-tree.txt` | identical |

**`util.storage`** (server). `Location` — `IngestOriginals` (`converter.spool-dir`), `Spool` (`acquire.spool-dir`),
`Basemap`, `Backups`, `Bundles`, `GazetteerExtracts`, `AgentHomes`, `Plugins`, `RelinkKit`, `Install`, `Logs`,
`UserConfig`, `Scratch`; `StoreKey` (relative, no `.`/`..`, no empty segment, no backslash or NUL); `Store` (`put`
through a reservation, `write`, `reserve` with `TempName.Fixed`/`Unique` and `throughLink`, `move`, `copy` with
attributes, `list`, `walk`, `stat`, `delete`, `deleteTree`, `touch`, `size`, `usableSpace`, `prune` by
`PruneRule.LeastRecentlyUsed`/`OlderThan`, `sub`); `Blob` (`open`, `bytes`, `text`, `sha256`, `readAt`, `reader()` — one
channel reopened after an interrupt closed it, as `PmTilesFile` had it —, `mapped()`); `Reservation` (`output`,
`writeAt`, `commit(keepPermissions)`, `discard`); `Storage` (`find`/`store`, `scratch`) on disk and `Storage.InMemory`;
`Storage.at(location, path)` for the composition root. A store's file reaches another program only inside
`com.histmersion.util` (`private[util] onDisk`, `onDiskFile`). `server/StorageLocations` places each location from
today's keys at each call (Q36: no key added); `Deps.storage`. `StoreContractSuite` runs every case on disk and in
memory.

**`util.jobs`.** `values.JobKind` is a newtype; `util.jobs.JobKinds` (api, JVM and JS) holds `JobKindEntry`s — name,
meaning (the jobs window's tooltip, moved from the web page), pause and cancel, the subject read from a job's detail.
Wording, the jobs window, `JobActions.Stoppable`, `JobDetail.subject` and the job controls read it. Today's sixteen
kinds are registered by the transition adapter `api.JobKindsAdapter`. The runner (`BackgroundJob`), the controls
(`JobControlService`, `JobControlControllers`, `JobStops`, `JobControlRules`, …) and `JobRepo` (still in `db`) are in
`util.jobs`; the runner keeps a job on its generation through `util.jobs.WorkCarrier`, which `hot.Pinning` implements.

**`util.identity`.** `AliasKind` (what a record and its identifier are called) is util.identity's; `AliasTable` (a
kind's table and columns, with the one resolution of an old identifier) and the `AliasTables` registry
(`Deps.aliasTables`) replace `AliasRepo`'s switch. Transition adapters: `api.AliasKindsAdapter`,
`util.identity.AliasTablesAdapter`.

**`util.process`.** `Processes` (and an in-memory twin that records launches) starts a `ProcessLaunch` — command, the
store it runs in, added variables, output piped, appended to or written to a store's file, or discarded. The bundle's
supervisor (the converter, Postgres's tools, `sysctl`), the backup's `pg_dump`/`pg_restore` and the relocation's
`codesign` launch through it.

**`util.ocr`.** `ConverterClient`, `HttpConverterClient`, `JatsConverter`, `ConverterState`; a document is handed as a
`Blob` (streamed with its length; an empty one as no body) and the client is told `ConverterSettings`, which
`server/ConverterWiring` builds from `converter.*` with the one HTTP/1.1 client. The chunker and `Chunk` are in
`util.ocr` (core).

**`util.basemap`.** The 25 files of `server/basemap` with no file-system API: `BasemapService` works over
`Store(Basemap)` (the extracts, `terrain-focus/`, the `.part` work stores, the on-demand caches as sub-stores),
`PmTilesFile` over `Blob.reader()`, `PmTilesWriter` and the downloads over reservations (`RangeHttp.download(…, to:
Reservation)`), `DiskLru` over a store (use is `touch`, the order `walk`'s times). `server/BasemapWiring` gives it its
jobs (`BasemapJobs`), its retry policy (`RetryPolicy` over `HttpPolicy`), its sea-floor grid and the User-Agent;
`UserPaths.basemapDir` places the directory.

**`util.settings` and `util.plugins`.** `domain/settings/*`, `ConfigSource`, `ConfigViews`, `HotConfig` and
`MergeSettingsViews` are in `util.settings`. The config editor stays pure in `config.edit` behind `SettingsFile`: the
server's `StoredSettingsFile` writes through `Store(UserConfig)` (a unique temporary file beside the file — through a
symlink —, checked with its includes as a load reads it, the `.bak` copied with its attributes, moved in keeping the
permissions), the CLI's `LocalSettingsFile` on disk. `Plugins`, `LegacyPlugins` and `PluginSetResource` are in
`util.plugins`; `server/ServerPlugins` supplies the built-in AI runtimes, the outbound client and the CORE quotas.

**Architecture.** Deletions only: `exceptions.tsv` 714 → 633 rows — direction 420 → 393 (27, among them the job kinds'
and alias kinds' enum cases, `JobDetail.subject`'s and `Wording`'s, `BackgroundJob → Pinning`, `Plugins →` `BuiltinAi`,
`CoreBudget`, `HttpPlugin`, `SttpHttp`, `ConfigViews → UiConfig`/`UiSettings`, the converter's `ConverterConfig` and
`Version`, `BasemapService → Deps`/`SeaFloorSource`, `JavaRangeHttp → Version`/`HttpPolicy`, the two `Chunk` rows),
file system 132 → 85 (47 rows, 157 uses: the base map 116, settings and plugins, the converter, `MediaHandlers`,
`BackupService`'s `.toFile`), process 7 → 4 (`Supervisor`, `BackupService`, `PgRelocation`), repository 155 → 151
(`AliasRepo`'s four users). `places.tsv` lost 57 rows and gained 8 for new files outside a feature package
(`StorageLocations`, `BasemapWiring`, `ServerPlugins`, `ConverterWiring` — the composition root; `UiConfigViews` —
`server.instance`; `config/edit/SettingsFile` — `util.settings`; the two api adapters — `api`, as `Api.all` is).

**Deviations.**

- Locations: only what is on disk today. Pictures, thumbnails and figures are rows of the database, and the map tiles'
  cache is the base map's sub-store, so `Assets`, `Thumbnails`, `Figures`, `TileCache` and `Exports` (a directory the
  caller names) are not locations. `Install` and `Logs` were added for the supervisor; `Originals` is
  `IngestOriginals` because `db.Originals` already names a type.
- The file code of `util.data` (backups, bundles, relocation, the storage probe), of the domains (ingest, the acquire
  spool, gazetteer extracts, agent trees) and `files` (`SafePath`, `DirSize`, notes) stays for their slices (6–10): the
  storage handles exist for them. `server/acquire/Spool` stays where it is: it hands a path to the ingest queue, which
  slice 10 moves to stores.
- The agents' command-line runtimes (`CliProcess`, `CodexHome`, `PlanPools`, `AgentDoctor`) keep `ProcessBuilder` until
  they move to `plugins/ai-cli` (slice 12, SPI-9).
- `JobDetail` and `ResolvedId` keep their per-kind fields (four and two rows): they are the wire contract.
- The registries' api parts (`JobKinds`, `AliasKind`) are in `util.jobs`/`util.identity` packages of the api module,
  because the web page and the CLI read them; `JobRepo` stays in the `db` module, in package `util.jobs`.
- A job's subject is the first registered reading of its detail; a job's detail holds one kind's fields, so the order
  of the old chain cannot show.
- `RelinkPlugins` and the `pluginTool` main stay in `server.plugins` (the kit's main class, the tool exemption);
  `config/UserConfigFile` keeps its `Option[Path]` (config loading reads it as a path).
- `BasemapService` stays a class (not yet a service trait with `InMemory`); a log line of the on-demand cache names
  the store's path; the base map's in-flight files are `<name>.tmp`.
- `WriteOutcome`, `SettingsSnapshot` name the settings file as a string, as messages do.

**Native image.** Renamed: `JobRow$` (`util.jobs`), the base map's `OnDemandTiles` and `Vp8l$Group[]`; removed:
`BasemapStyles` and `ConfigViews$`'s Chimney given; added: `Deps.storage$lzy1` (read with `javap`). No other new
`$lzy` field or derived codec in the moved or new classes (`javap -p` over every class of the new packages). No
tracing-agent run: every new path is a file or process operation, none reflective. To check at the bundle:
`--self-check`; `smoke.sh` (first run, settings, reload, OCR); the base map's fetch and tiles; the settings page with a
symlinked file; `histmersion config set` natively; a relink.

**Tests.** After `sbt clean`, `just test-quick` with every module named, on a throwaway database: 4 901 tests, none
failed (2 skipped, 1 ignored). The DB-gated oracle and the suites of the moved areas ran uncached.


## 17. Slice 6 as built (data management, 2026-10-31)

Branch `refactor-s6` from master 9ebc38cc: the oracle first, then the registry, then three streams (backup, maintenance
and diagnostics; checks and the Markdown mirror; the bundle and merge mechanism), merged. `ContractSnapshotSuite` and
`CliHelpSnapshotSuite` held byte for byte after every commit; no snapshot was rewritten.

**The oracles.**

| oracle | what it records | result |
|---|---|---|
| `DataOracleSuite` (server, DB; `oracle/data/*.txt`, `HISTMERSION_UPDATE_ORACLE=1` rewrites them) | a crafted pair of collections over HTTP: work's whole bundle and the part since the shared history (every entry and line), its import into an empty collection (the result, every row), home merging work's bundle after both went on — changed findings, a renamed citekey, a paragraph both edited, a deleted event and goal — (the session, plan, resolution file, apply job, merges, the merge, the ledger, every row of home), a backup of work restored into an empty database (every row equal), the backups kept (`keep = 2`), the lint report of every rule and the safe fixes on a copy, every source and article note, About's counts, jobs and storage. Uids are written as the record they name, instance ids as the collection, clocks, revisions, plan hashes, dump digests and disk sizes by a placeholder | identical after every commit |
| the realistic copy (scratch, not committed) | a `*_test` database restored from the newest live dump (16 GB): About (JSON and Markdown), `lint --json`, the `export <dir>` tree with every file's digest, the whole bundle and the part changed since 2026-10-27 (every entry with its size and digest, the manifest without its moment and id), `bundle verify`, a backup restored into another `*_test` database (a digest per table of both), `lint --fix` on a copy, the whole bundle imported into an empty `*_test` database (the result and a digest per table), and a merge: a small database made from the part, a second one importing it and going on (a finding changed, a citekey renamed, an event deleted, a paragraph edited, the same paragraph edited on the first side), the second's bundle planned, decided and applied on a copy of the first (the plan, the resolution file, the apply, the merges, the merge, the ledger, every row afterwards), the backups pruned to `keep = 2` | see below |

The realistic copy ran on the unchanged code (a classpath frozen at 9ebc38cc's build) and on this branch, with a
sandbox that refuses every connection but loopback, scratch homes and settings, and the converter pointed at nothing.
A whole-database export, import, verify, restore and `lint --fix` run past the HTTP listener's 60-second idle cut
(tapir's `NettyConfig.idleTimeout`, unchanged here), so those five ran the endpoints' own controller code through a
scratch driver instead of over HTTP; the CLI's `bundle export` also sets no read timeout of its own (sttp's one-minute
default), unlike every other CLI call. Both are older than this slice and left as they are.

Results, before against after, over 30 recorded outputs: 12 byte-identical (`lint --json`, the `lint --fix` report,
the export tree's digests, the whole and changed-since bundle listings, the per-table checksums of the export and
the bundle import among them); the other 18 equal once what a run mints is masked — run and merge ids, clocks,
durations, dump file names, free disk space, the commit About names, a dump's size (±2 bytes of pg_dump's header) and
the pretty-printer's spacing of the scratch driver's JSON. The per-table checksums after the import, the restore and
the merge differ only in `agent_managers` (the manager's heartbeat) and `flyway_schema_history` (`installed_on`). The
merge's 122 672 rows: 188 lines differ, every one a minted id, a dump name or a clock. A first, whole-file comparison
script ran the shared machine out of memory (2026-10-31); the comparison was redone streaming, line by line.

The oracle's first record depended on the machine and the second: two dumps taken in one second get a `-2` suffix,
and About measured directories under the home directory the test runs with. The suite now gives every configurable
directory a scratch path and writes the home directory as `HOME`, a suffix as part of `STAMP` and a file count as `N`;
it was recorded again on the unchanged mechanism and agreed three times in a row before anything moved.

**The registry** (`util.data`, server module; `DataOwner` and `TableName` in db). `DataContribution` as §3.6 has it,
plus `backup` (what a dump covers and what guards a restore); `DataContributions.of` refuses an owner registered twice
and a table claimed twice, and keeps the owners' order: automation, catalogue, media, library, research, personal,
`util.data`, `util.jobs`, `util.identity`, `util.database`. `TablesAdapter` lists every table's owner (90 tables;
`DataContributionsSuite` checks every table of the migrated schema has exactly one); `queue_items` is automation's
(the work-list kernel), the acquire list being library's rows in it. `TransitionContributions` builds each owner's
contribution from one adapter per member until the owner's slice registers its own; `Deps.data` is the registry.

**Backup** (`util.data.backup`). `BackupService` (trait, `Implementation`, `InMemory`) dumps into a reservation of
`Store(Backups)` (`<name>.part`, committed on success) and restores through `util.process`; the client tools are found
in the store of `backups.pg-bin-dir`, then the `PATH` directories. Registered: no owner's store goes into a dump (as
before); the library guards a restore with `sources` ("source"). `BackupJobs` carries its work with `WorkCarrier`.

**Maintenance** (`util.data.maintenance`). `WriteGate` is the maintenance lock (`Deps.maintenance` keeps its name);
vacuum runs through `BackgroundJob` and may vacuum the registered tables the database has; `PgRelocation` works over a
store and `Processes`; `ReadOnlyPool` moved here. `CacheSpec` and `MaintenanceTask` registered: the catalogue's map
tiles, undated cells and the base map's on-demand caches, media's thumbnails, the storage figures; the library's
`reindex` task (from `domain.library.sources`), which the `reindex` endpoint runs.

**Diagnostics** (`util.data.diagnostics`). `StorageFigures` builds About › Storage from the registered stores (in
About's order, `ShownStore`) and the install's places, measured through `Store.measure`; `DiagnosticsService` gives
each owner's figures, the database's size, its largest registered tables and per-owner table sizes; About's corpus
counts and job counts are the owners' figures. `StorageProbe` and `DirSize` (in the server) are gone; `DirSize` lives
on in the CLI, its last user. New locations for what About measured as paths: `DatabaseCluster`, `CorpusFolder`,
`GazetteerFolder`, `GazetteerDrop`, `Converter`, `Toolchain`, `CodexSessions`, `DataDirectory`, `CacheDirectory`,
`Postgres`, `PostgresTools`, `Executables`.

**Checks** (`util.data.checks`). `CheckRunner` selects, runs and reports the registered `CheckRule`s on one read and
applies their `SafeFix`es in a stated order (`CheckOrder`: the report in `Lint.names`' order, the fixes resolve-edges
and reindex on every call, then the named ones); `CheckRun` (db) reads each input several rules share once.
`CheckRulesAdapter` registers the 34 rules: library 23, research 6, catalogue 4, media 1. `LintRepo` keeps the per-kind
SQL and the per-kind fix parts that left `OpsHandlers`; a fix that writes an article reaches it as `CheckContext.write`.

**Mirror** (`util.data.mirror`; `export` is a Scala 3 keyword). The layout (`MirrorPlacement`) and the frontmatter
writer (`Frontmatter`) are in the files module, where the CLI's `export` resolves its paths; `MirrorExport` notes are
registered for the library's sources and research's articles; `Mirror` runs the export endpoints over the registry.

**Exchange** (`util.data.exchange`). Moved: `BundleTar`, `BundleFormat`, `BundleReader`, `MergeResolutionsFile` (files),
`Diff3`, `BundlePlan` (core), `BundleScope`, `MergeRepo` (with the import's generic writes: the lock, the importing
flag, the ledger and `merges` row, tombstones, touches, redirects, remembered decisions) and `BundleExport` (db, over
every carried kind's lines and blob readers in one REPEATABLE READ transaction). New: `BundleImporter` (the import's
and merge apply's transaction and generic blob pass), `MergePlans` (the plan and `planFile`), `CarriedKind`/
`CarriedKinds` (entries in the format's order, each carried exactly once), `MergeRules`/`MergeRuleSet`, `BundleService`
over `Store(Bundles)` and the originals' content store, `BundleControllers` (in place of `BundleHandlers`),
`MergeApplyJobs` through `util.jobs.MergeApplyRuns`. `CarriedKindsAdapter` registers 26 kinds (library 6, research 3,
catalogue 11, media 2, automation 3, `util.identity` 1) with their lines, clocks, blobs, tables and import steps, whose
stages and ranks reproduce the import's write order; `MergeRulesAdapter` the 12 record kinds' ledger labels. Per-kind
code stays at its path for slices 8–11: `BundleImport.Writer` (one step per kind), `MergeState`'s matching,
`BundleRepo`'s readers, `MergePlanner`, `MergeFold`, `MergeSettings`, `BundleEntities`.

**Architecture.** Deletions only: `exceptions.tsv` 633 → 578 rows — direction 24 (among them `BundleImport → Goal`,
`Attestation`, `Place`, `Finding`, `AssetLink`, `ArticleVersion`, `Decided`, `Originals`; `BundleHandlers → Deps`,
`Originals`, `InstanceLabel`; `StorageProbe → AgentTrees`, `GazetteerLoad`, `Supervisor`; the three jobs' `Pinning`;
`StatsRepo → CorpusCounts`; `MergeLedger`/`MergeRepo → LedgerRow`; `FormatSeverity → Frontmatter`), file system 23
(`BackupService`, `BundleHandlers`, `BundleExport`, `BundleImport`, `MergeState`, `Originals`, `PgRelocation`,
`StorageProbe`, `DirSize`), repository 8 (`OpsHandlers` → `LintRepo`, `PlaceReviewRepo`, `PointAreaRepo`,
`SourceRepo`; `Handlers` → `BackupRepo`, `StatsRepo`; `StorageProbe → StatsRepo`; `MergeApplyJobs → JobRepo`).
`places.tsv` lost 23 rows and gained one (`server/BackupWiring`, the composition root).

**Deviations.**

- The mirror's package is `util.data.mirror`; `DataContribution` has a `backup` member §3.6 does not list.
- Wire records stay in `com.histmersion.api` and settings records in `config`, as slice 5 left its areas'.
- The bundle's entry order is the format's (`BundleFiles.all`), not the registration order, which starts with
  automation; `CarriedKinds` checks every entry is carried once.
- `CarriedKind`, `MergeRules` and their adapters are in the db module, where the import and the plan run and the db
  suites call them without a registry (they default to the transition list; the server passes `Deps.data`'s).
- `MergeRules` is thin (the ledger label, whether lines refer to the kind): matching, folding and decisions are still
  `MergePlanner.plan`; the rows-in steps share `BundleImport.Writer`'s cross-kind state. The merge's review tasks and
  log entry stay in `BundleImport.mergeTasks` (they go through `QueueRepo`).
- The checks' and the mirror's transition bridges: `LintRepo` and `Lint` carry the rules; the article note needs the
  coverage only `Handlers` computes, so `MirrorExport.note` takes a `TransitionNotes` that `Handlers` implements. The
  meta files (work lists, `GOALS.md`) keep automation's endpoints.
- `CheckRunner`, `Mirror` and `BundleService` have no `InMemory` twin: they are mechanisms over registered members
  that run on a connection (as `BasemapService` in slice 5).
- The install's places (binaries, converter, relink kit, toolchain, plugins, logs) are the composition root's, not an
  owner's store; the base map's directory is registered under the catalogue.
- Environment reads stay at the composition root (About's home directory, the dump's `PATH`).
- Visible only in files: a dump that times out now discards its `.part`; bundle exports and uploads are written as
  `<name>.tmp` reservations; the backups listing skips entries that are not files; About's corpus counts are seven
  queries on one connection.

**Native image.** Added: `Deps$.data$lzy1`, `CarriedKinds$.transition$lzy1`, `MergeRuleSet$.transition$lzy1`; renamed:
`db.LedgerDbRow$` → `util.data.exchange.LedgerRow$` (`derived$DbCodec$lzy2`), `db.MergeRow$` →
`util.data.exchange.MergeRow$`; removed the stale `db.MergeRepo$package$`. `javap -p` over every class of the new
packages shows no other reflective lazy value. No tracing-agent run. To check at the bundle: `--self-check`; `smoke.sh`
(About's storage lines, the relocation); `histmersion backup`, `backups`, `restore`; `bundle export|verify|import`;
`merge plan|apply|list|show|ledger`; `lint` and `lint --fix`; `export <dir>`; `maintenance vacuum`; `reindex`.

**Tests.** After `sbt clean`, `just test-quick` with every module named, on a throwaway database: 4 967 tests, none
failed. Deployed 2026-10-31: lint, settings, the whole Markdown export (1 718 files) and a bundle of what changed in two
days (2 491 entries, its manifest included) byte-identical before and after the install. `DataOracleSuite` recorded
each source's `addedDate`, the local day it ran on, and failed from the next day on; since slice 7 it writes `DAY`.

## 18. Slice 7 as built, part 1 (the automation engine: contributions, the registry, the work lists, 2026-11-01)

Branch `refactor-s7` from master 3a528816: the oracles first, then the registry, then the work lists' kernel. The
slice is built in two parts (part 2 below); part 1 is green on its own. `ContractSnapshotSuite` (`toolbox.json` and
`mcp-tools.json` included) and `CliHelpSnapshotSuite` held byte for byte after every commit; no snapshot was rewritten.

**The oracles** (server tests, recorded on the unchanged engine, `HISTMERSION_UPDATE_ORACLE=1` rewrites them):

| oracle | what it records | result |
|---|---|---|
| `AutomationOracleSuite` (no database; `oracle/automation/agent-trees.txt`, `workflows.txt`) | the definitions generator run into a scratch directory for every runtime: each of the 206 files' path, size and SHA-256, the manifest (its moment masked), the definitions' fingerprint and the generator's answer; every workflow the engine runs (identifier, version, target, roles, description), every agent definition's tools, every tool | identical |
| `RunTranscripts` (a fixture mixed into the 17 scripted-model and manager DB suites; `oracle/automation/runs/<suite>.txt`) | per test, every row it inserted or updated in every table but the bulky reference ones (found by transaction id), as JSON with minted ids, clocks, serials, run ids, digests and minute counts masked and long texts as digest and length, rows sorted; plus the texts the scheduler and autopilot suites hand it — the scheduler's status (goal-first, type-balanced, dry), `why` for a waiting, a running and an unknown item, the autopilot's status on drain, drained, undrain, draining with runs in flight and the drain lifted by turning it on, the stalls report, the autopilot with person and checked approvals | identical, 351 tests, twice in a row |

The transcripts are run on demand: `HISTMERSION_ORACLE_RUNS=1 HISTMERSION_TEST_GROUPS_SERVER=1`, a newly created test
database, `server/testOnly com.histmersion.server.agents.*`. A test's rows depend on what the suites before it left in
its forked group's database, and the groups change whenever a suite is added anywhere in the server's tests, so the
record holds for that invocation alone; in ordinary runs the fixture records nothing. The first record was taken
minutes before a watcher's weekly cron (Saturday 22:30 UTC) fell inside a test's simulated hours; it was recorded
again on 07f563ce outside that window, then again in the on-demand form, and compared there twice before anything was
compared on the branch. The manager DB suites themselves are unchanged and pass.

**Contributions and the registry** (`domain.automation.workflows`, server):

| type | what it is |
|---|---|
| `WorkflowDefinition` | `id: WorkflowId`, `version`, `target`, `roles`, `describe`, `allowedTools(role): Set[ToolName]`, `run(run: RunCtx): Either[Halt, String]` |
| `ToolContribution` | `name: ToolName` and the typed `agentTool` (sttp-ai's `AgentTool`: description, input schema, the call) |
| `Contributions` | the workflows and tools the domains register |
| `Workflows` | the registry: `of` refuses two workflows with one identifier, two tools with one name, and a workflow whose roles may use a tool nobody contributes; `parse` (a run's recorded pipeline, trimmed, any case), `definition`, `toolNames`, `replaced` |
| `ToolName`, `WorkflowId` | newtypes (`[a-z][a-z0-9_]*`, `[a-z][a-z0-9-]*`); the engine's `ToolName` enumeration is gone |

The engine dispatches a run to its registered workflow (a run of an unregistered one fails as before, "no pipeline …
in this build", and a version change is checked against the definition's version); a step offers its agent only the
tools the workflow lets the agent's role use (`Engine.offered`). `Toolbox` is the mechanism over the contributed
tools (`names`, `tools(allow)`, `tool(name)`, the input schema `/mcp/agent/<agent>` lists). `AgentDefinitions` is told
the toolbox's names: a definition may list only those, with today's message; `ClaudeCodeClient` passes the same list
to `claude -p`; the generator's known names are the toolbox's. Transition adapters, in the composition root
(`places.tsv`: `server`): `PipelineWorkflow` registers each of the nine pipelines with the step agents its steps run as
(a role may use what those agents' definitions list in that role), `TransitionTools` the ten read tools (moved whole
from `Toolbox`), `TransitionWorkflows` builds the contributions, which `Handlers.automationContributions` hands the
engine as it is built. `generatePlugin` moved to `server/agents/GeneratePluginMain.scala` (same main class).

**The work lists' kernel** (`domain.automation.worklists`). `QueueRepo` (in the db module, package
`domain.automation.worklists`, as `JobRepo` is in `util.jobs`) runs on the connection it is given;
`QueueService` (`Implementation` over a `Transactor`, `InMemory` over `WorkListState`, stamping as the database does,
UTC to the microsecond) with `QueueScope` (`Open`, `All`, `ForJob`, `ForGoal(goal, linked, withDone)`), `QueueItemId`,
`QueueError`, `ImportMode`, `ImportCounts`; `WorkListControllers` holds add, patch, delete, import, export and the
files, dating a log item's topic from the composition's clock (`Deps.clock`) instead of `LocalDate.now`.
`OpsHandlers.queue*` delegate to it; every server caller of the repository goes through `Deps.queues`. The goal scope
is given the goal's `queue-item` link references instead of joining `goal_links`; the DOI lookup takes its list
(`naming(list, doi)`). `QueueServiceContract` runs `QueueRepoSuite`'s scenarios and four more (a first done time
kept, an item made done, `byIds`, the DOI lookup) on `InMemory` and on a scratch database per test;
`WorkListControllersSuite` runs over `InMemory` with `Clock.fixed`. Other suites seed the lists through the db
tests' `QueueRows`.

**Architecture.** Deletions only: `exceptions.tsv` 578 → 542 rows — direction 369 → 345 (24: the engine naming the
seven pipeline classes, `ToolName`'s and `Toolbox`'s references to the tools' inputs, `Handlers`, `McpTools`, `OpsTools`,
`AcquireMarkdown`, `ArticleMarkdown`, `Query`, `Search`, `ArticleCoverage`), repository 143 → 131 (12: `QueueRepo` named by
`IngestHandlers`, `OpsHandlers`, `AcquireHandlers`, `CandidateSearch` and eight engine files). `places.tsv`: the four
adapter files added (`server`), `ToolName` and `db/QueueRepo` gone.

**Deviations.**

- `WorkflowDefinition` runs a workflow as today's pipelines run, steps in a fixed order through `RunCtx`, each
  checkpointed by the engine, instead of §3.5's `start`/`step`/`settled`: that decomposition is what each domain
  writes when it replaces its adapters with its own definitions (§6.5 already writes compile-article's steps as pure
  functions), and the engine's checkpoint replay depends on the fixed order today.
- `ToolContribution` carries sttp-ai's typed `AgentTool` instead of `call(input: Json, caller)`: the loop decodes a
  call's arguments with the tool's own codec and answers a bad one with its own text, which the scripted suites pin;
  the tools are read-only, so no `Caller` is passed yet.
- A definition's fingerprint (`AgentTrees.sha`) is the text of the definition with each tool named as the old
  enumeration printed it (`AgentDef.fingerprint`), so installed trees are not regenerated.
- The work lists' item record is still the wire's `api.Queue.QueueItem` and its list `api.Queue.Kind` (rule 3 allows
  the reuse); `QueueScope.ForJob` and `ForGoal` take raw text, since the CLI's `--job` and `--goal` list nothing for an
  unknown value rather than refusing it. The bundle import and the source deletion still write their log lines with
  `QueueRepo.insert` inside their own transactions (their repository rows stay). `Handlers.acquisitionItemNaming` keeps
  the acquisition list's DOI lookup out of an engine file until the library's slice owns it.
- `ManagerAutopilotSuite`'s "approvals a person's" test failed on the unchanged code in the minutes around the weekly
  cron window above and passed outside it; it is a wall-clock dependence of the test, older than this slice.

**Part 2 (to do).** The remaining scope of §9.2's row 7: `ManagerKind` (the supervisor's review and watch kinds as
contributions, its loop, holds, retries, cron, approvals settlement and hints left in the engine), `ObjectiveProgress`
(the goal stages' facts), runs recorded through `util.jobs` (the 36 statements of `AgentRepo` on `jobs`), the
generator fed by contributed roles as well as tools, checkpoints, approvals, the scheduler and the autopilot moved into
`domain.automation.*` packages with the rest of `server/agents` (about 100 files) and their exception rows, and the
design of SPI-9 (Q5).

**Native image.** Renamed: `db.QueueRepo$` → `domain.automation.worklists.QueueRepo$` (`given_DbCodec_QueueItem$lzy1`),
`server.agents.Toolbox$package$` → `TransitionTools$package$` (`given_Configuration$lzy1`). `javap -p` over every class
of the new packages and adapters shows no other reflective lazy value (the tools are built eagerly). No tracing-agent
run. To check at the bundle: `--self-check`; `smoke.sh`; an agent run with tools on a CLI runtime (`/mcp/agent/<agent>`
`tools/list`); `histmersion queue list|add|done|rm|import|export`; `agent generate`.

**Tests.** After `sbt clean`, `just test-quick` with every module named, on a throwaway database: 4 994 tests, one failed
(1 skipped, 1 ignored): `DataOracleSuite` (slice 6), whose record holds each source's `addedDate`, the local calendar
day — it was recorded on 2026-10-31 and fails from 2026-11-01 on the unchanged code (07f563ce) too, with nothing but
that date different. The transcript oracle ran as above, identical twice; `ContractSnapshotSuite`,
`CliHelpSnapshotSuite`, `AutomationOracleSuite` and `ArchitectureSuite` passed.
