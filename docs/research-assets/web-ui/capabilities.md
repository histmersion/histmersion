# Histmersion: what the web UI can do vs the CLI and MCP (research for a UI redesign, 2026-10-27)

Repo: this repository. Abbreviations: `web/` = `modules/web/src/main/scala/com/histmersion/web/`,
`api/` = `modules/api/shared/src/main/scala/com/histmersion/api/`, `srv/` = `modules/server/src/main/scala/com/histmersion/server/`,
`ref.conf` = `modules/config/src/main/resources/reference.conf`.

How the web UI is built:
- Every server call the page makes goes through `web/ApiClient.scala`. That file is the full list of what the UI can do.
- The source and article windows are server-rendered HTML fragments (`web/WindowManager.scala:122-142`, `srv/Pages.scala:153-194`).
- The toolbar windows are listed in `web/WindowKey.scala:8-48`: Jobs, Upload, Acquire, Tasks, Log, ApiKey, About, Runs, Run, Models, Approvals, Goals, Goal, Merge, Managers, Manager, plus Source, Article, Graph and EditSource.

---

## 1. Capability matrix

Legend: **yes** = a person can do it from the page. **partly** = read-only, or only some of the sub-operations. **no** = no UI at all.

### 1a. Corpus reading and search

| CLI group / MCP tool | Web UI | Where (web) / notes |
|---|---|---|
| `search`, `facets` / `search`, `facets` | yes | `ApiClient.scala:71-75`; FilterBar, ResultsList, chip filters |
| `get` (+`--full`) / `get_source` | yes | Source window (server HTML), full-text toggle `srv/Pages.scala:192-193` |
| `passages` / `passages` | partly | Only the `?q=` highlight inside a source window (`Pages.scala:381`). There is no passages search box. |
| `geo-when`, `histogram` / `geo_when`, `histogram` | yes | the map and timeline (`ApiClient.findings`, `aggregate`) |
| `neighbors`, `graph` / same | yes | Graph window (`GraphPanel.scala`, `ApiClient.scala:105`). The source page lists cites and cited-by. |
| `contradictions` / `contradictions` | no | endpoint `api/Api.scala:240`, not called by the page |
| `query`, `schema` | no | SQL access is for agents and the CLI only |
| `periods search/show` / `periods` | yes (read) | timeline presets (`ApiClient.scala:108`) |
| `places search/show/children` / `places` | yes (read) | PlacesInput, place hover cards (`ApiClient.scala:83-86`) |
| `events search/show` / `events` | yes (read) | Events tab, event layer and cards (`ApiClient.scala:117-120`) |
| `attestations …` / `attestations`, `put_/delete_attestation` | no | |
| `assets <ck>` / `assets` | partly | figures show inline in the source page. They cannot be edited (`patch_asset`, `asset_links`: no UI). |
| `article list/get/coverage` / `list_articles`, `get_article`, `article_coverage` | yes (read) | Articles tab and article window, with the generated coverage sections |
| `article review` / `article_review` | partly | a review badge and history in the article page (`Pages.scala:248-292`) |
| `article versions/version` / `article_versions`, `article_version` | no | |
| `lint` / `lint` | no | |

### 1b. Writing and enrichment

| CLI / MCP | Web UI | Notes |
|---|---|---|
| `patch <ck>` / `patch_source` | partly | `SourceEditor.scala:143-204` edits details, status, topics, regions, tags, period and summary. It cannot edit **findings**, **edges** (`--add-edge`), notes or body. |
| `put`, `rename`, `delete` / `put_source`, `rename_source` | no | |
| `article put/patch/lint/check/rename/delete` / `put_article`, `patch_article`, `check_edits`, `rename_label` | no | There is no article editing. The article window has three enqueue buttons only: review, extend, enrich (`ManagersView.scala:205-209`). |
| `places set/name/merge/rm/add/rename` / `place_override`, `place_names`, `add_place` | no | |
| `events patch/rename/links/corrections` / `patch_event`, `event_links`, `event_corrections` | no | |
| `media patch`, `asset-links` / `patch_asset`, `asset_links` | no | |
| `goals list/get/update/link/unlink/schedule` / `goals`, `put_goal`, `patch_goal`, `goal_links`, `goal_schedule` | yes | `GoalsView.scala`: create (153-164), links (315, 337), currency and acquisition schedules (353-374). There is no delete or rename. |
| `agent-state` / `agent_state` | no | |

### 1c. Acquisition and currency (the "check for new developments" group)

| CLI / MCP | Web UI | Notes |
|---|---|---|
| `ingest <doi\|arxiv>` / `ingest_doi` | **no** | The page cannot ingest by DOI. Upload takes a file only. The DOI field in the upload form is metadata for that file (`UploadView.scala:28,59-62`). |
| `ingest <file>` / `ingest` | yes | Upload window (`UploadView.scala:161`). It can tick off an acquire item. |
| `reocr <ck>` (re-OCR) | **no** | endpoint `api/Api.scala:1055` (`reocr`); the page never calls it |
| manual **re-fetch** of one source (re-run the legal-OA fetch for an existing source) | **no** (no command anywhere) | The closest are `ingest <doi> --force` and `queue acquire recheck --id <n>`, which works on acquire items, not on sources. The source window offers only "graph" and "edit" (`WindowManager.scala:193-200`). |
| `queue acquire recheck` / `queue {action: recheck}` | **partly** | The Acquire window **shows** each item's last check result (`QueueView.scala:189, 201, 305-315`). There is no button to trigger a re-check. The endpoint is `ManagersApi.acquireRecheck` (`api/ManagersApi.scala:107`); the scheduled pass is `srv/agents/AcquireRecheckService.scala:105,120`. |
| `refs <ck> [--apply]` / `refs` | no | |
| `assess <ck>`, `assess update-retractions` / `assess` | no | |
| `followups <ck>` / `followups` | no | |
| currency-check per goal (assess + followups over the goal's sources) | partly | Only as a cron **schedule** in the Goal window (`GoalsView.scala:353,372`). There is no "check now" button. The `currency-check` pipeline exists (`modules/values/.../AgentVocabulary.scala`), but the UI starts only `plan-goal` runs directly (`GoalsView.scala:157,234`). |
| `discover search/meta/wikirefs` / `discover` | partly | Only a "discover" button on event *leads*: it runs an OpenAlex query and lists 5 hits (`Preview.scala:651-660`, `ApiClient.scala:122`). There is no wikirefs, no meta lookup and no general discover box. |
| `candidates [sweep\|mark]` / `candidates` | no | The deterministic new-source candidates (#3761) have no UI. |
| `archive search/info/import` / `archive` | no | |
| `queue acquire\|tasks\|log` list/add/done/edit/rm / `queue` | yes | `QueueView.scala:196-245` (Acquire, Tasks and Log windows) |
| `queue … export/import` | no | |

### 1d. Agents, autopilot, jobs: pausing, unpausing, draining

| CLI / MCP | Web UI | Notes |
|---|---|---|
| `agent autopilot on/off` / `manager {action: autopilot}` | yes | Managers window, "turn off" / "turn on" (`ManagersView.scala:297-320`, `ApiClient.scala:234`) |
| `agent autopilot drain/undrain` / `manager {action: drain}` | yes | `ManagersView.scala:285-303` shows the drain line and the toggle |
| autopilot focus (pin, unpin, cap) | yes | `ManagersView.scala:243-261` |
| `agent manager pause [--runs]/resume/stop` / `manager` | yes | per manager (`ManagersView.scala:685-693`) |
| manager items hold/release/skip/reopen/priority, hints, `manager config` | yes | `ManagersView.scala:724-733, 644, 788, 820` |
| `agent manager new` | yes | from the Goal form only (seed and watch managers, `GoalsView.scala:160-164`) |
| `agent pause/resume/cancel <run>` / (MCP: no pause tool) | yes | Run window (`RunsView.scala:331`, `api/AgentsApi.scala:90`) |
| `agent run` / `start_run` | partly | Only `plan-goal` runs from the goal window. Enrich, review, extend and seed go through manager enqueue buttons (`ManagersView.scala:193,205-216`). |
| `agent scheduler` / `scheduler` (strategy, why) | yes | `ManagersView.scala:406-410` |
| `agent stalls` | yes | `ManagersView.scala:429` |
| `agent approvals/approve/reject` / `list_approvals`, `decide_approval` | yes | `ApprovalsView.scala`, with bulk ticking |
| `agent digest`, `decide-all` | partly | the digest is shown (`ApiClient.scala:249`); there is no decide-all endpoint call |
| `agent models set/reset`, `allowance`, `quota hold/release/refresh` / `agent_models`, `set_agent_models` | yes | `ModelsView.scala:66-240` |
| `agent quota floors`, `fallthrough` | no | |
| `agent doctor` | yes | `ModelsView.scala:82` |
| `agent probe`, `compare`, `generate` | no | |
| `jobs` / `job_status` | yes (read) | `JobsView.scala:144` |
| **pause, cancel or drain background jobs** (OCR, imports, basemap fetch) | **no, anywhere** | Background jobs have no cancel or pause endpoint. The only gate is internal, used while a restore runs (`srv/Jobs.scala:38-51`). The agent drain stops agent runs, not jobs. |

### 1e. Open data: media, basemaps, event catalogues, gazetteer, periods

| CLI / MCP | Web UI | Notes |
|---|---|---|
| `basemap status/fetch/rm` (no MCP tool) | **yes** | In the About window: region picker, dry-run sizes, fetch and remove (`MapRegionsView.scala:111,119,129`; `AboutView.scala:148`). Map options are kept per browser (`BaseMaps.scala`). |
| `media import/covers/providers/list/fill-licences` (no MCP tool) | **no** | The map's "pictures" layer only reads (`ApiClient.mediaGeo/mediaFacets`, `ApiClient.scala:115-116`). Downloading images is CLI-only. The endpoints exist: `api/Api.scala:1153-1200` (`mediaImportJob`, `mediaCoversJob`, `mediaProviders`). |
| `events catalogues/import gvp\|noaa\|wikidata/rederive/candidates/leads` (MCP: none for import) | **no** for import | Only the leads' discover button (above). Endpoints: `api/EventsApi.scala:27-72`. |
| `places fetch/load/sources/import/seed/rebuild/build-hierarchy/site-types` (gazetteer extracts) | **no** | The About window shows store counts only. Endpoints: `api/Api.scala:747-833`. |
| `periods seed/import/fetch` | no | |
| `init [--fetch]` | no | |

### 1f. Plugins, config, backups, Calibre, bundles

| CLI / MCP | Web UI | Notes |
|---|---|---|
| `plugin add/rm/list/status/rebuild/rollback/new/repo/api-jar` | **no** | **Host-side CLI only.** The commands act on the local install's files and the relink (`modules/cli/.../PluginCmd.scala:17-35` `LocalInstall`) and refuse a non-local server (`ref.conf:897-899`). There is no server endpoint and no MCP tool. |
| plugin state | partly | About window "Plugins" section, read-only (`AboutView.scala:89-100`) |
| plugin **enable/configure** | **no** | Only through `histmersion.plugins.<id>.enabled` and `plugins.<id> {…}` in `histmersion.conf`, then `config reload` (`docs/user-guide/plugins/installing.md:16,81,187-190`). |
| `config path`, `config reload` / `config {action: reload}` | partly | The About window shows the config file path, whether it was read, and ignored keys (`AboutView.scala:35-38`). There is no reload button. |
| **config editing** | **no, anywhere** | No CLI, MCP or REST surface writes the config file. The MCP `config` tool has `reload` only (`srv/McpTools.scala:380-392`); nothing in `srv/` or `modules/config` writes `.conf`. Some settings are **DB-stored overrides** that already have UIs: model chains and allowances, plan-quota pools, scheduler strategy, autopilot on/drain/focus cap, per-manager config and cron, goal schedules. |
| `backup` / `backup` | partly | The About window shows the last backup only (`AboutView.scala:46-49`). There is no "back up now", no list and no download, although the endpoints exist (`api/Api.scala:363-389`: `backup`, `backups`, `downloadBackup`). |
| `backups` / `backups` | no | |
| `restore` (CLI only, a human's call) | no | `api/Api.scala:389` |
| `bundle export/verify/import/instance` | partly | The Merge window uploads, plans, resolves and applies (`MergeView.scala`, `ApiClient.scala:199-209`). There is no export. |
| `merge plan/apply/list/show/ledger` / `merge_plan`, `merges` | partly | plan and apply yes; list, show and ledger no |
| `maintenance vacuum` / `vacuum` | no | |
| `calibre search/import/link/push/update/set/audit/sync/gaps` | **no** | Host-side CLI by design: it shells out to `calibredb`/`sqlite3` (`ref.conf:908-920`). The server keeps only bookkeeping endpoints (`api/AcquireApi.scala:80-89` `calibreGaps/calibreMeta/calibrePushed`). The web page never mentions Calibre. |
| `export`, `article export/import`, `seed`, `reindex` | no | |
| `version/health/about` / `ping` | yes | About window |

**Where the gaps cluster:**
- Everything that brings data in: DOI ingest, events, media, gazetteer, periods, archive, candidates.
- Every per-source currency action: re-OCR, re-fetch, refs, assess, followups.
- Backup, restore and export.
- Plugins, config and Calibre.

The page is strong on agent orchestration (Managers, Runs, Approvals, Models, Goals) and on reading.

---

## 2. First-run and setup: what a new user must do today

Sources: `docs/user-guide/getting-started/install.md`, `first-steps.md`, `how-to/{open-data,credentials,ai-providers,books-and-calibre,base-maps}.md`, `plugins/installing.md`, `scripts/bundle/histmersion.conf.template`.

**Terminal-only steps (required)**
1. Install. Either:
   - unpack a release and run `scripts/bundle/install-{macos,linux}.sh <dir>`, or
   - build from source: `sdk env install`, `sdk env`, `just install`. This needs sdkman, uv, pixi and just.
   The installer may download GraalVM (~340 MB) and relink (needs a C toolchain, `xcode-select --install`, and 9-10 GB of RAM). The `--prebuilt` and `--jvm` flags skip that.
2. Check it runs: `histmersion health` / `about`. Restart with `just restart` (`launchctl kickstart …`).
3. The bundled gazetteer and periods import by themselves (`init.auto = true`, `ref.conf:991-1003`). Nothing to do.

**Optional or feature-gated, terminal or .conf only today**

| step | how | UI today? |
|---|---|---|
| OA polite-pool contact e-mail. **Without it Unpaywall is never asked.** | `acquire.contact-email` in .conf or `HISTMERSION_ACQUIRE_CONTACT_EMAIL` in the LaunchAgent plist (`ref.conf:672-674`; credentials.md:135) | no |
| CORE key, Semantic Scholar key, Smithsonian/Europeana keys (+ `plugins.media-*.enabled = true`) | .conf secret references (`env:`, `keepassxc:`, `op:`, `bitwarden:`) plus `plutil -insert EnvironmentVariables…` for env vars (credentials.md:29-44, 131-145) | no |
| Password vault (KeePassXC, 1Password, Bitwarden) | .conf block (credentials.md:51-125) | no |
| Write key for LAN access | `HISTMERSION_BIND=0.0.0.0` + `HISTMERSION_API_KEY` in the plist or systemd unit, then restart (install.md "What runs") | web has a key-entry window only |
| Built-in agents | `agents.enabled = true`, `roles {…}` and `prices` in .conf. **Needs a restart, not a reload.** (ai-providers.md:7-28). `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` come from the environment only; CLI-tool logins are needed for claude-code or codex. | partly: chains and allowances can be edited in the Models window once enabled |
| Base map download (~2 GB) | `histmersion basemap fetch --keep-all` | **yes** (About → map regions) |
| Event catalogues | `histmersion events import gvp\|noaa\|wikidata …` | no |
| Pictures | `histmersion media import <provider>`, `media covers` | no |
| More gazetteers | `histmersion init --fetch`, `places fetch cliopatria\|awmc\|land\|chronontology`; large ones need extracts built by hand (~an afternoon) and then `places load geonames\|wikidata\|tgn\|lpf --apply` | no |
| More periods | `histmersion periods fetch periodo [--collection]` | no |
| Calibre | `cli.calibre.library-dir` / `calibredb` path in .conf; `calibre import/push/sync` | no (host-side) |
| Plugins | `histmersion plugin add …`, then `plugins.<id>.enabled = true` in .conf, then `plugin rebuild` (relink) | no |
| MCP for an assistant | add `http://localhost:8000/mcp` to the client's config; optionally `claude plugin marketplace add` + `install` | no |
| Any .conf change | edit `~/.config/histmersion/histmersion.conf`, then `histmersion config reload` or a restart for restart-only keys (`ref.conf:1565-1583`) | no |

**Implications for a setup wizard:**
- **Pure server calls, no host access needed:** contact e-mail, basemap fetch, event and media imports, gazetteer and period fetches, agent enablement, provider keys, Calibre path.
- **Missing pieces today:** a config-write endpoint, and a restart trigger for restart-only keys such as `agents.enabled`.
- **Must stay host-side:** plugins (relink on the host) and Calibre (`calibredb` on the host). A wizard can only show the commands for these, unless the server gains a host helper.
- **Secrets:** to write them safely, the wizard should store vault or env references, never values. The design already does this: "Reports … always show the reference, never the value" (credentials.md:18).

---

## 3. How agents look for sources (and where Wikipedia sits)

There are three tiers of discovery, and they differ.

**A. Built-in (embedded) agents on the server: no Wikipedia at all.**
- Every agent definition has `web: none` (`agents/*.md:9`, all 15 files). No embedded role may call `discover`, web search or fetch. The `WebAccess` enum explicitly allows `discover` ("OpenAlex, Crossref, Wikipedia references"), but no role uses it (`srv/agents/AgentDefinitions.scala:42-47`).
- **New-source search is code, not a model** (#3761, `docs/research-new-source-search.md:22-44`). It works through per-goal "watches":
  - `acquire.candidates.indexes = [openalex, crossref, europepmc, arxiv, core]` (`ref.conf:846-854`). CORE is used only with a key.
  - Each watch reads the goal's saved queries, or its title when it has none, from each index, with a watermark and a 365-day first lookback.
  - Citation and author watches: for up to 12 of the goal's sources, the works **citing** each one, plus newer works by its first and last author (OpenAlex).
  - Watch kinds are `query | citing | author` (`api/Candidates.scala:19-22`).
- **No weighting by channel.** Candidates are one row per DOI with `found_by[]` and are listed `ORDER BY id`, i.e. insertion order (`modules/db/.../CandidateRepo.scala:67`). The ranker gets the unseen ones as a flat list (`srv/agents/GoalSweepPipelines.scala:222-238`).
- The ranking model picks "primary studies, major syntheses and the critiques of them. Leave out the tangential, the duplicated … and the popular" (`agents/acquisition-ranker.md`). Its preamble is stale: it still says "Code searched OpenAlex with the goal's queries".
- **Seed before the plan** (`srv/agents/SeedService.scala`): the same deterministic sweep, then legal-OA ingest of up to 5 candidates per goal.
- **The goal planner** proposes OpenAlex-style queries and DOIs, each verified against OpenAlex/Crossref. Its prompt says: "Prefer peer-reviewed primary literature; Wikipedia is a trailhead, never a source." (`agents/goal-planner.md:36-37`)
- **Currency** (`currency-check`): `assess` (Retraction Watch, Crossref notices, metrics) and `followups` (Semantic Scholar citing works with intents) over the goal's sources (`GoalSweepPipelines.scala:58-125`).
- **Order in practice:** plan queries feed OpenAlex, Crossref, Europe PMC, arXiv and CORE together, plus the citation and author graph; then ranking; then legal-OA ingest or the acquire list. Wikipedia is never involved.

**B. Interactive Claude Code workflow (this research workspace's skills): Wikipedia comes first.**
`skills/trailhead/SKILL.md:13-24` (mirrored in `~/Documents/research/.claude/skills/trailhead/SKILL.md`):
```
## 1. Gather candidate DOIs (two complementary channels)
# a) the references a Wikipedia article cites — the best signal for foundational work
histmersion discover wikirefs "https://en.wikipedia.org/wiki/Late_Bronze_Age_collapse"
# b) relevance- or citation-ranked literature search, already deduped against the corpus
histmersion discover search "<topic query>" --limit 25 --not-in-db
histmersion discover search "<topic query>" --sort cited --limit 25 --not-in-db
```
- The skill's description begins "Start a new research topic from a Wikipedia article or a search query".
- The MCP `discover` tool description pushes the same way (`srv/acquire/AcquireMcp.scala:29-36`): "`wikipediaUrl` harvests every DOI a Wikipedia article cites, which is the fastest way to find the foundational papers on a subject."
- This is where the user's impression comes from. When Claude Code opens a topic, Wikipedia's reference list is step 1a and is called "the best signal". The citation graph (`refs`, `followups`) appears only later, in `ingest-paper`, `find-followups` and `update-topic`.

**Does Wikipedia content reach articles?**
- **No text does.** `Wikipedia.refs` fetches the wikitext and keeps only the DOIs and arXiv ids (`srv/acquire/Clients.scala:202-212`; `modules/plugin-host/.../AcquireParse.scala:356-361`). The result type is `WikirefsResult(title, url, count, dois, arxiv)`. Nothing is stored as a source or a finding.
- The writing rules forbid "summarize Wikipedia" (trailhead SKILL.md:9; `CLAUDE.md`: "Wikipedia is only a trailhead").
- **But it does shape selection.** For a topic opened through the trailhead skill, the corpus inherits Wikipedia editors' choice of references: their recency gaps, disputes and bias. Because article coverage is defined by the corpus filter, that selection bias flows into "due weight". This is the user's concern, and it is real at the *selection* layer, not the text layer.
- Other Wikipedia/Wikidata touchpoints are data, not literature: Wikidata events and media, and the Cliopatria polity `Wikipedia` property (`srv/importers/CliopatriaImporter.scala:53-58`).

**If Wikipedia were demoted or removed:**
- **Demote** (the low-cost option):
  - Reorder the trailhead to start with `discover search --sort cited` plus a recent-review search (`--since`).
  - Then walk the citation graph: `refs` (backward) and `followups` (forward) from the top 3-5 reviews.
  - Make wikirefs an optional "cross-check for anything missed", and rewrite the `discover` tool description to match.
  - The embedded agents need no change.
- **Remove:**
  - Drop `AcquireApi.wikirefs` (`api/AcquireApi.scala:36`), the `wikipediaUrl` mode in `AcquireMcp.scala:41`, the CLI `discover wikirefs` and `acquire.wikipedia.url` (`ref.conf:798`).
  - Nothing else depends on it: no pipeline, no DB table, no stored data.
  - Cost: harder bootstrapping of a topic whose literature is mostly pre-DOI books. Those come from Calibre or archive.org anyway.
- **Consensus-oriented alternative**, to fit the user's intent:
  - Seed a topic from **recent review articles and handbooks** (OpenAlex `type:review`, sorted by citations, with a `since` floor), then expand by citation graph.
  - The candidate watches already support `citing` and `author` expansion. A "review-first" query kind would be a small addition to `candidate_watches`.

---

## 4. Planned features: what exists and what is missing

**Auth today (single shared secret, writes only):**
- `srv/AuthHandler.scala:21-48`: when `auth.api-key` is non-empty, POST/PUT/PATCH/DELETE need `Bearer <key>`. **GET is always open.** Exceptions:
  - `/api/ui/seen/*`, which needs no key (`AuthHandler.scala:60-67`);
  - the agent MCP path, which uses per-run tokens (`RunTokens`).
- Web `Auth.scala:12-30`: `canWrite = !required || key held`. The key lives in browser storage. `WriteButton` greys out writes and opens the key window (`Components.scala:118-128`).
- This is the only "hide what you can't do" mechanism today.
- `agentsEnabled` hides the agent windows (`App.scala:41`).
- **There is no user identity anywhere.** The only "who" is free-text origin strings (`"web"`, `"cli"`) passed in autopilot, drain and focus calls (`ApiClient.scala:235-245`) and the approvals' `decidedBy`.

**Tables:** there are no users, sessions, roles, bookmarks, read marks or personal notes. The full table list is in the `V*.sql` migrations. The nearest things:
- `ui_seen(board PK, seen_at)` (`modules/db/src/main/resources/db/migration/V083__ui_seen.sql`). Global, one row per *board* (goals, tasks, approvals, acquire, log); it drives toolbar "new" counts. Single-user by construction.
- `sources.notes`: the *compiled* notes the agent writes, one field per source. Not user notes.
- `agent_state`: a key-value store for agents.
- the `log` queue: a shared dated worklog.
- `source_aliases` (V027): already designed so "links, bookmarks, `[[old]]` … keep resolving" after a rename. External bookmarks survive citekey renames.

| feature | what exists | what to add |
|---|---|---|
| Per-item user notes and a "my notes" list | nothing user-scoped; `sources.notes` is agent prose; `attestations` give passage-level anchors (W3C selectors) that notes could reuse | `user_notes(id, user_id NULL, target_type, target_id, anchor jsonb NULL, body, created/updated)`; REST, MCP and CLI; a Notes window. Follow targets through renames as `source_aliases`, `article_aliases`, `event_aliases` and `place_aliases` already do. |
| Bookmarks (sources, articles) | none; the URL hash keeps open windows (`UiState`) | `bookmarks(user_id NULL, target_type, target_id, created)`, or a `pinned` flag on notes |
| Read/unread | only per-board `ui_seen` | `read_marks(user_id NULL, target_type, target_id, read_at)`; reuse the counters endpoint (`api/UiApi.scala:19-40`) |
| Optional multi-user (no login in single-user mode) | single bearer key; no identity | `users` table and sessions (or per-user API keys); `user_id NULL` = the implicit local user, so single-user mode needs no login. Thread the identity through `AuthHandler` into handlers (today it is a Netty gate with no principal). Replace the `"web"`/`"cli"` origin strings with the user id. |
| Permissions, with the UI hiding what a user can't see | read/write split only, by key | Roles (reader, editor, admin, agent-operator) checked per endpoint. **Reads are open today, so restricting visibility needs a read gate**, not just the write gate. `/api/config` (`UiConfig`, `srv/ConfigViews.scala:16-24`) already tells the page `writeKeyRequired` and `agentsEnabled`. Extend it to a capability set, and generalise `WriteButton` into a capability-aware button. Note: MCP tools and `query` (raw read-only SQL) bypass any per-item visibility unless they are scoped too. |
| Editing config visually, or via MCP/CLI | **none**: no write path to `.conf`; only `config reload` and `config path`. DB-stored overrides exist for agents, scheduler, autopilot, managers and goal schedules. | A `PUT /api/config` that writes `histmersion.conf` (HOCON-preserving) or a DB-stored overlay layer (env > DB overlay > file > reference.conf), validated with the existing reload machinery: build beside, swap, report (`ref.conf:1565-1583`). Add a schema/metadata export of `reference.conf` (types, comments, restart-only flags) to drive a form. Show `env:` overrides as read-only, since env beats the file (`histmersion.conf.template:3-9`). Add an MCP `config {action: get\|set}` and CLI `config get\|set`. Secrets: accept references only. |
