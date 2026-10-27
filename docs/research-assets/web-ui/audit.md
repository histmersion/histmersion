# Histmersion web UI — audit for the redesign (2026-10-27)

Scope: read-only. Code paths are relative to `modules/`
(`web/…` = `web/src/main/scala/com/histmersion/web/`, `style.css` = `server/src/main/resources/web/style.css`).
Screenshots (taken locally, not committed: they show a private corpus) (headless Chromium, so the map draws without a GPU and the
"no GPU" line appears in the terrain notice — a real browser shows a shorter notice). Live server
config at the time: `agentsEnabled: true`, `writeKeyRequired: false` (so the "API key" tool is hidden).

---

## 1. Inventory

### 1.1 Page skeleton (wide layout, ≥ ~1366 px; `tabletMaxWidthPx` 1366 in `/api/config`)

`App.init` builds one grid (`web/App.scala:46-62`; `style.css:122` = 2 columns × 4 rows):

| row | left column (1fr) | right column (`--sidebar-w`, 360 px default, drag 200–800: `web/ui/Layout.scala:6-8`) |
|---|---|---|
| 1 | **Filter bar** `.fbar` (`web/FilterBar.scala:74-164`) — includes the **tool toolbar** (`web/Toolbar.scala`) | **Sidebar** spans rows 1–4 (`web/App.scala:107-160`) |
| 2 | **Timeline** `.tl` (`web/TimelineView.scala`, controls at `:814-850`, playback `web/TimelinePlayback.scala:43-97`) | |
| 3 | **Layer bar** `.viewport-toggle` (`web/ViewportToggle.scala:93-156`) | |
| 4 | **Map** `#map` (`web/MapView.scala:290+`) | |

Measured at 1440×900 (`shots/01-default-1440.png`): filter bar 178 px + timeline 145 px + layer bar
69 px = **392 px of chrome above the map**; the map is 1080×508 px — **42 % of the viewport**.

Overlays: `WindowManager.element` (all windows + taskbar, mounted through `ArticleModal.element`,
`web/ArticleModal.scala:14`), the lightbox (`web/ArticleModal.scala:12,54+`), hover cards
(`web/Preview.scala`), place cards (`web/PlaceHover.scala`), map popups (`web/MapView.scala:80`,
MapLibre popup `maxWidth 340px`, `web/map/maplibre/MapLibreEngine.scala:293`), `#status` text
(`web/App.scala:92`), mobile chrome (`web/MobileChrome.scala`).

### 1.2 Every view / panel / window / popover

| element | where on screen | opened by | file |
|---|---|---|---|
| Tool toolbar (11 buttons, 3 groups) | top of filter bar, wraps to 2 lines at 1440 px (`wrap-reverse`, `style.css:535-538`) | always | `web/Toolbar.scala:28-44,74-92` |
| Search box, place input, "more/fewer filters", "clear all" | filter bar row 1 | always | `web/FilterBar.scala:81-139` |
| "More filters" row: authors, topics, status, tags chip inputs, "published between" slider (1850–2026), "sort by" | filter bar row 2 | **open by default on desktop** (`moreOpenInitially`, `web/FilterBar.scala:40-43`) | `web/FilterBar.scala:142-163,170-256`, `web/ChipInput.scala` |
| Chip suggestion dropdown | under a chip input | typing | `web/ChipInput.scala` |
| Place autocomplete + map place chips | filter bar / map (narrow) | typing | `web/PlacesInput.scala` |
| Timeline overview strip (whole −12000…2026) with span handles | timeline | always | `web/TimelineView.scala:590-690` |
| Timeline detail strip + axis | timeline | always (folded on narrow) | `web/TimelineView.scala:695-760` |
| Timeline controls: period presets ⏱, zoom +, zoom −, reset ⟲ | timeline, bottom-left | always | `web/TimelineView.scala:814-840` |
| Period presets dropdown | over timeline | ⏱ | `web/TimelineView.scala:553+` (`shots/50-presets-popover.png`) |
| Playback: ▶/⏸, ⏹ (only while a playhead exists), speed select, year label | timeline, centre | always | `web/TimelinePlayback.scala:43-97` |
| "detail ▾" | timeline (narrow only) | always (narrow) | `web/TimelineView.scala:842-848` |
| Layer bar: sources (findings) toggle, "only sources in view"; per layer: boundaries + "States shown" select; sites + site type; media + provider + licence; events + event kind; event tier; base-map select; map-options button | layer bar (2 lines at 1440) | always — options of a switched-off layer stay, disabled (`web/ViewportToggle.scala:107-110`) | `web/ViewportToggle.scala:52-61,111-123`, `web/IconSelect.scala`, `web/BaseMaps.scala:488+` |
| Map options popover (names, language, contour lines, sea depth, map motion, reset, close) | under the button | map-options button | `web/BaseMaps.scala:301,380` (`shots/51-map-options-popover.png`) |
| Map: zoom ± (MapLibre), legend + "key" disclosure, foot box (terrain notice + credits) | map corners | always | `web/MapView.scala:364` (legend), `web/BaseMaps.scala:195-238` (foot) |
| Map markers: finding diamonds, cluster bubbles, sites, events, media prints, boundaries | map | layers | `web/PointKinds.scala`, `web/DotCluster.scala`, `web/map/PointSet.scala`, `web/EventLayer.scala`, `web/TiledLayers.scala` |
| Map popup (finding / picture) | map | click marker | `web/MapView.scala:225-233,658-674` |
| Sidebar: title quick filter, 5 tabs (sources, articles, events, by time, by region), list | right column | always | `web/App.scala:133-159`, `web/ResultsList.scala`, `web/ArticlesTab.scala`, `web/EventsTab.scala`, `web/TreeTab.scala` |
| Cluster pick list ("◀ back to results", "zoom to cluster", rows) | replaces sidebar list | click a bubble | `web/ResultsList.pick`, `web/ResultsList.scala:94-153` |
| Hover cards (source/article/place/event; thumbnails, credit, licence) | floating | hover on a link/marker | `web/Preview.scala` |
| Windows (draggable, resizable, minimisable, cascaded, Escape closes) | floating over everything, max 780×600 (merge 1500×1000) | toolbar or links | `web/WindowManager.scala` |
| Window taskbar (minimised windows) | floating | minimise | `web/WindowManager.scala:52-66` |
| Lightbox (full-size `/assets/` images only) | full screen | click an asset image | `web/ArticleModal.scala:32-42` |

Windows (`web/WindowKey.scala:8-48`, rendered `web/WindowManager.scala:319-341`):

| window | opened from | renderer |
|---|---|---|
| research goals (tree) | toolbar | `web/GoalsView.scala` (`pane`) |
| goal · slug | goals tree, runs, approvals, managers | `GoalsView.goalPane` |
| backlog (`tasks` queue) | toolbar | `web/QueueView.scala` |
| agent runs (list) | toolbar (agents on) | `web/RunsView.scala` |
| agent run · id | runs, goals, approvals, managers | `RunsView.runPane` |
| agent models | **only** the "models & allowances" button inside runs (`web/RunsView.scala:210,359`) | `web/ModelsView.scala` |
| agent managers (+ autopilot, focus, scheduler) | toolbar | `web/ManagersView.scala` |
| manager · id | managers, runs, approvals | `ManagersView.managerPane` |
| approvals | toolbar, runs, managers | `web/ApprovalsView.scala` |
| acquisition queue | toolbar | `web/QueueView.scala` |
| upload for OCR | toolbar, acquire item "Upload…" | `web/UploadView.scala` |
| merge another collection | toolbar | `web/MergeView.scala` |
| background jobs | toolbar | `web/JobsView.scala` |
| worklog (`log` queue) | toolbar | `web/QueueView.scala` |
| API key | toolbar (only when a key is required) | `web/Auth.scala` |
| about Histmersion (+ map regions panel) | toolbar | `web/AboutView.scala`, `web/MapRegionsView.scala` (via `AboutView.scala:148`) |
| source page / article page (server-rendered HTML) | any source/article link, sidebar row | `WindowManager.loadContent` `:125-145` |
| graph · ck | "graph" in a source window | `web/GraphPanel.scala` |
| edit · ck | "edit" in a source window | `web/SourceEditor.scala` |

### 1.3 Control counts (measured in Chromium, visible + focusable elements)

| | total | map markers | sidebar result rows | **chrome controls** |
|---|---|---|---|---|
| 1440×900 wide | 118 | 46 | 18 | **54** — filter bar 20, layer bar 14, timeline 6, sidebar chrome 6, map 8 (zoom ×2, 4 credit links, "Why?", map focus) |
| 1180×820 tablet landscape (narrow layout) | 63 | 45 | 0 | **17** — mobile bar 4, timeline 7, sheet 2, map 4 |
| 820×1180 tablet portrait (narrow layout) | 57 | 39 | 0 | **17** |
| 390×844 phone | 25 | 19 | 0 | 6 |

Not counted (div-based draggables): 2 year-slider handles, 2 timeline span handles, the legend's
"key" disclosure. With them the wide layout has **~59 chrome controls**, each visible at once, before
any window is open. Opening one window adds 2–42 more (runs 42, acquire 41, goals 33, tasks 31,
approvals 28, jobs 23, models 20, managers 16, upload 11); four windows open at once = 243 controls
(`shots/11-win-many.png`).

**Always visible on the wide layout**
- Map controls: 5 layer toggles + "only in view" + 7 per-layer option menus (disabled, not hidden,
  when their layer is off) + base map select + map options = **15**, on two lines (`shots/01`).
- Research/agents/jobs controls: goals, backlog, agent runs, agent managers, approvals, to acquire,
  upload, merge, jobs, worklog, about = **11** (+ API key when required).
- Filters: search, place, more/fewer, clear all, authors, topics, status, tags, publication-year
  slider (2 handles), sort = **11** + sidebar title filter + 5 tabs.
- Timeline: presets, zoom ±, reset, play, speed (+ stop while playing).
- Two pairs of identical "+ / −" buttons stacked ~110 px apart (timeline zoom at y≈305, map zoom at
  y≈417; `web/TimelineView.scala:826-837` vs MapLibre).
- Two independent year ranges: publication years (`FilterBar.yearRangeSlider`, `web/FilterBar.scala:167-256`) and
  the period timeline.

On narrow layouts (`shots/02-tablet-820.png`, `shots/54-tablet-maptools-drawer.png`,
`shots/53-tablet-filters-drawer.png`) the same 11 tools are appended to the "map & tools" drawer
under the 15 map controls (`web/ViewportToggle.scala:83-91`) — one long scrolling drawer of 26
full-width buttons.

---

## 2. Grouping: UI vs data model

### 2.1 How the UI places them

| thing | entry point | toolbar group (`web/Toolbar.scala:28-44`) |
|---|---|---|
| goals | "research goals" | Plan |
| backlog (queue `tasks`) | "backlog" | Plan |
| agent runs | "agent runs" | Plan |
| agent managers | "agent managers" | Plan |
| approvals | "approvals" | Plan |
| jobs | "jobs" | **System** |
| worklog (queue `log`) | "worklog" | **System** |
| acquire (queue `acquire`) | "to acquire" | Acquire |
| agent models | no toolbar button — only from inside the runs window | — |
| map regions / downloads | inside About only | — |

The three queues (`acquire`, `tasks`, `log`) are one table and one renderer (`QueueView`) but sit in
three different groups. Jobs and runs are one table (below) but sit in two groups and two windows.

### 2.2 How they are linked in the database (live schema, `histmersion query`)

No FOREIGN KEY constraints exist between these tables (information_schema returns 0 rows); the
links are id/slug columns:

- **An agent run IS a job**: `jobs.pipeline IS NOT NULL` (3 189 of 4 116 jobs). `agent_steps.job_id`,
  `agent_messages.job_id` hang off it. The jobs window lists the same runs again as kind "agent".
- `jobs.goal_slug` → goals (1 759 rows), `jobs.manager_id` / `jobs.manager_item` → managers (2 821),
  `jobs.parent_job` → run that started it (13), `jobs.acquire_item` → `queue_items.id` (2).
- `approvals.job_id` → the run that proposed it.
- `agent_managers.goal_slug` → goal; `manager_items.run_id`, `manager_events.run_id`,
  `manager_hint_uses.run_id` → runs.
- `goal_links(kind, ref)`: `source` 439, `article` 361, `query` 1 200, **`queue-item` 666** → goals ↔ backlog/acquire items.
- `goal_schedules.last_job` → run.
- `queue_items` (`list` = acquire 568 / tasks 972 / log 4 658) has **no** column pointing to a goal,
  run or job; worklog entries mention runs only in prose ("agent run 4e11ac63").

### 2.3 UI paths between them

| from → to | path exists? | code |
|---|---|---|
| run → goal, manager, approvals, models | yes (`button.link-btn`) | `web/RunsView.scala:280,283,359,363,365` |
| approval → run, goal, manager | yes | `web/ApprovalsView.scala:360,366,383` |
| manager → goal, run, approvals, runs | yes | `web/ManagersView.scala:251,344,417,431,441,477,514,652,707` |
| goal → run, sub-goal | yes | `web/GoalsView.scala:122,175,239,263,268,359,390` |
| goal → linked backlog item | **no** — rendered as plain `#id title` text | `web/GoalsView.scala:310` |
| backlog item → goal(s) that link it | **no** (QueueView has no goal/run code at all) | `web/QueueView.scala` |
| worklog entry → run / goal it mentions | **no** (plain text) | `web/QueueView.scala` |
| job row (kind "agent") → its run window | **no** — shows text and `job 10ed4471` | `web/JobsView.scala:220-252` |
| job → acquisition item it ticks off | **no** — "for acquisition item N" text | `web/JobsView.scala:230-234` |
| run → its job row | **no** | — |
| toolbar → models | **no** (only via runs) | `web/Toolbar.scala:28-44` |

So the agent cluster (runs/approvals/managers/goals) is densely cross-linked, but the bookkeeping
cluster (backlog, worklog, acquire) and jobs are islands even though the data links them (666 goal →
queue-item links; every agent run is a job).

---

## 3. Consistency

### 3.1 User-visible snake_case / kebab-case strings

Root causes first, then examples:

| what the reader sees | source | file:line |
|---|---|---|
| Run pipeline chips and titles: `review-article`, `enrich-source`, `propose-articles`, `compile-article`, `acquisition-sweep`, `currency-check`, `plan-goal` | raw `r.pipeline`; chip `show` defaults to `identity` | `web/RunsView.scala:212` (chips), `:294` (row link text), `:355` (window title); default `web/ListFilterBar.scala:49-50` |
| Approval kinds: `article-publish`, `status-change`, `edge`, `goal-plan`, `article-plan` | raw `a.kind` | `web/ApprovalsView.scala:308` (chips), `:384` (row), digest buttons `:289-292` |
| Scheduler wait types / reasons chips | raw | `web/ManagersView.scala:461,463` |
| Scheduler strategy `type-balanced`, `goal-first`; pools `claude-plan`, `chatgpt-plan` | raw `s.strategy`, `p.pool` | `web/ManagersView.scala:394-398` |
| Manager ids `watch:african-oral-history-…`, kinds | raw | `web/ManagersView.scala:664` (title), `:514` |
| Job kinds `basemap-fetch`, `events-import`, `events-rederive`, `site-types`, `events-candidates`, `media-import`, `plan-billed` | `kindLabel` falls back to `Label.display`, which only replaces `_` (`api/shared/.../api/Domain.scala:104`) | `web/JobsView.scala:62-67` |
| Allowance windows `seven_day`, `five_hour`, `10080m`; "floors five_hour=25, seven_day=40" | raw `w.kind` | `web/ModelsView.scala:135-138,156,218` |
| Model chain entries `codex:gpt-6-luna → claude-code:haiku` (monospace) | raw | `web/ModelsView.scala:177` |
| A config key in prose: "(agents.autopilot.max-unfinished-goals)" | server text | `core/jvm/src/main/scala/com/histmersion/domain/AutopilotPolicy.scala:114-115`, shown at `web/ApprovalsView.scala:287` |
| Goal slugs as link text / titles (`mesopotamian-gods-cult-and-history`, `goal · theory-currency`) | slug used where title exists | `web/RunsView.scala:280,365`, `web/ApprovalsView.scala:360`, `web/ManagersView.scala:251,344,441`, `web/WindowKey.scala:96` |
| Goal tags `methodology_critique`; topic labels `phoenician_levantine`, `ancient_dna`, `bronze_age` | raw tags/topics | goal pane `web/GoalsView.scala` (tags `dd`), models/managers muted lines |
| Acquire/backlog group headings `disputed-grand-theories`, `pie-origins`, … (the tasks window shows "Queue: proto indo european" — a different rendering of the same idea) | `h3(cls := "group-topic", title)` | `web/QueueView.scala:310` |
| About: table names `place_names`, `source_chunks`, `place_links`; plugin ids `ai-anthropic`, `oa-unpaywall`, `media-europeana`…; plugin setting keys `timeout-seconds`, `request-interval-ms`, `max-records`… | raw | `web/AboutView.scala:161` |
| Map-picture names are file names: `Sicily_Selinunte_Temple_E_Hera_.JPG`, `open-context-c7b8db23-…` | `h.assetName` | `web/PointKinds.scala:183-185`, `web/MapView.scala:665-673` |
| Media licence as a code token `CC-BY-SA-3.0` | `<code>` | `web/MapView.scala:672` |
| Citekeys everywhere a title exists (`altman1995_statistics` as window title) | — | `web/WindowManager.scala:134-136` (falls back to id) |

### 3.2 Typography (style.css, 1 560 lines)

- Families: `system-ui` stack (`style.css:121`), monospace in 3 spellings (`ui-monospace,SFMono-Regular,Menlo,monospace`
  ×2, `ui-monospace,monospace` ×2), plus browser defaults leaking in: computed DOM shows
  `Helvetica Neue` (MapLibre controls) and `Arial 13.33px` (unstyled form control) on the default screen.
- Font sizes: **29 distinct values** — px: 8, 9, 10, 10.5, 11, 11.5, 12, 12.5, 13, 13.5, 14, 15, 16, 20;
  em: .7, .8, .85, .9, .92, .95; tokens `--fs-xs/sm/md/lg`, `--fs-control`, `--fs-icon`, `--fs-small`, `--fs`.
  Literal px dominate: `12px` ×49, `11px` ×26, `13px` ×18, `12.5px` ×18, `11.5px` ×14 vs. token uses
  ~38. The scale tokens (`style.css:100-103`) are used by controls only.
- Weights: 600 ×29, 700 ×5, **650** ×4, 500 ×1, 400 ×4.
- Italic: 14 rules (period lines, confidence, captions).
- Letter-spacing: 8 different values (0 … .08em); `text-transform:uppercase` ×13 (state badges
  DONE/RUNNING/SEEDING, drawer subtitles), plus `capitalize` and `lowercase` once each.
- Mixed casing in labels: buttons lower-case ("research goals", "clear all"), window titles
  lower-case, headings Sentence case ("Subscription plans"), badges UPPER, a few Title-ish
  ("Autopilot ON", "Close", "Reset", "Readable").
- Icons: SVG icon set (`Icon(...)`, `web/Glyphs.scala`) next to Unicode glyph buttons
  (⏱ + − ⟲ ▶ ⏸ ⏹ ✕ − ↗ ▾ ▴ ◀) in timeline, windows and filters.

### 3.3 Borders, radii, shadows, colours

- Radii: 18 distinct — 2, 3, 4, 6, 8, 9, 10 px, 50 %, 999 px, mixed with tokens
  `--radius-sm/--radius/--radius-lg/--radius-pill` (only ~15 token uses vs ~55 literals).
- Shadows: **30 distinct box-shadows**, no token (e.g. popups `0 6px 18px .45`, cards `0 8px 28px .5`,
  windows `0 12px 32px .5`, sheet `0 -8px 30px .45`, …).
- Borders: 1 px solid `var(--line)` is the norm (80×); also 1.5 px dashed, 2 px, 3 px, 1 px dotted.
- Colours: tokens on `:root` (`style.css:1-80`) — 851 `var(--…)` uses — but **115 literal hex uses / 51
  distinct hex values** plus ~15 rgba literals after the token blocks. Notable divergences:
  - Source status has **two palettes**: tokens `--status-foundational:#f0c040` (yellow) and
    `--status-current:#6ea8fe` (`style.css:28-30`) vs badges `.status-badge.st-foundational`
    green `#6ee7a8`, `.st-current` `#6eb8e7` (`style.css:777-783`) and the same literals again in
    `.cite-tip-status.*` (`style.css:698-702`).
  - "done" is green for jobs/runs (`style.css:880`) but blue for goals (`style.css:929`); goal
    "seeding" uses the error red (`--health-down`, `style.css:926`) though it is a normal state.
  - Hover background `#222634` both as token `--hover-bg` (`style.css:107`) and literal (`style.css:136`).
  - Panel colour as literal `rgba(27,30,39,.92)` (map foot, legend, `style.css:409,575`).
- `ThemeTokens` (`web/ui/ThemeTokens.scala`) only reads the ramp and graph colours; `Layout`
  (`web/ui/Layout.scala`) holds behaviour numbers. There is no Scala-side typography or
  spacing token; `ui/Css.scala` is two opaque types.
- **Dark only**: one theme, no `prefers-color-scheme` rule, no light theme (`shots/03` with a light
  colour scheme is identical to `shots/01`). `ThemeTokens.refresh` mentions "a theme switch" that
  does not exist. The base map has its own "Dark (online)" option (`web/UiState.scala:176`).

Verdict: there is a control code (`.btn`/`.field`/`.btn-group`/`.toolbar`, `style.css:94-120`) that
is applied consistently to buttons and fields; everything else (text sizes, badges, shadows, radii,
status colours) is per-component literals.

---

## 4. Windows, tabs, URLs

- All UI state including open windows is in the URL hash (`#open=goals&open=run:<id>&tab=…`,
  `web/UiState.scala:266-267,495-503`; reconcile on back/forward `web/WindowManager.scala:99-109`).
  So every window is *deep-linkable by hand* (this audit opened each via `#open=…`).
- But **no control is a link**, so none can be middle-/cmd-clicked into a new tab:
  - Toolbar tools are `<button>`s (`web/Toolbar.scala:55-62` → `WindowToggle`, `web/Components.scala:114`).
  - Every cross-window link (run, goal, manager, approvals, models) is `button.link-btn` with
    `WindowManager.open` (§2.3 lines).
  - Window title bars offer "↗ Open in new tab" **only for source and article windows**
    (`pageHref`, `web/WindowKey.scala:103-106`; `web/WindowManager.scala:313-314`).
- Real URLs that exist: `/source/<ck>`, `/article/<slug>` (server-rendered standalone pages,
  `server/.../HttpApi.scala:191,269-270`, entry `web/InPage.scala:59-69`). `/acquire`, `/tasks`,
  `/log`, `/merge` are only redirects into `/#open=<key>` (`web/Main.scala:12-14`,
  `server/.../HttpApi.scala:321`). `/jobs`, `/goals`, `/runs` → 404.
- Source/article links in Laminar (`SourceLink`, `ArticleLink`) **do** honour ⌘/Ctrl/Shift-click
  (`web/Components.scala:148-152,171-175`). Sidebar result rows are `div onClick` (no href) except the
  inner id link. Inside a server-rendered window, `wireLinks` calls `preventDefault()` on every
  `/source/…` or `/article/…` link **without checking modifier keys** (`web/WindowManager.scala:156-180`),
  so cmd-click there opens another in-page window, not a tab.
- When it was lost (git):
  - `b2e7a7e6` 2026-09-18 "Phase 2.4: multi-window manager" replaced the single modal with
    in-page windows and introduced `wireLinks` (in-window links open new windows); kept a "↗" for
    source/article.
  - `b13a8486` 2026-09-25: the standalone `/acquire` page became a window ("/acquire redirects to
    /#open=acquire"); its page CSS (`.acquire-page`, back-link) was deleted.
  - `7616c23e` 2026-09-27: backlog and worklog added as windows beside it (`/tasks`, `/log` redirect).
  - `242bd1aa`/`a68cd20f` 2026-10-04 (runs, approvals, goals) and `f650dce9` 2026-10-15 (managers)
    were born as windows with `link-btn` buttons; they never had URLs.

---

## 5. Attribution / licence display

- **Base-map credits**: one box in the map's bottom-left corner, `.map-foot`
  (`web/BaseMaps.scala:195-238`, CSS `style.css:405-415`): the "Today's terrain…" notice
  (always added — `PastNotice`, `web/BaseMaps.scala:64,126`) plus the credit line
  "© OpenStreetMap (ODbL), Natural Earth, Protomaps · names: Natural Earth (public domain)" in
  11 px muted text with 4 links. Measured 300 × ~110 px with the notice (credits alone 282×30 px).
  Shown **permanently** on desktop: the fold toggle is `display:none` in the wide layout
  (`style.css:586`, shown only `style.css:1112` narrow), and the MapLibre attribution control is off
  (`web/map/maplibre/MapLibreEngine.scala:59`). Narrow layout: folded behind an ⓘ button.
- **Media licences**: a "media licence: all" filter menu always in the layer bar (disabled when media
  is off, `web/ViewportToggle.scala:56-58`); in the picture popup as a raw `<code>CC-BY-SA-3.0</code>`
  token, unlinked, plus the credit line in 11 px (`web/MapView.scala:670-674`); in hover cards as a
  linked licence name under the thumbnail (`web/Preview.scala:677-693`). The popup cannot link the
  licence because `MediaGeoHit` carries no `licenseUrl`/`sourceUrl` (`api/shared/.../api/Media.scala:117-131`).

---

## 6. Known bugs — confirmed with code pointers

### (a) Jagged / pixelated timeline with 1-px risers

Drawn as **SVG paths** (not canvas), `drawAreaCurve` (`web/TimelineView.scala:199-273`):
1. Bucket size targets ~1 bucket per pixel: `bucketSizeFor = ceil(span / max(width,100))`
   (`:123-126`). Measured live: the detail path has **1 000 cubic segments over 1 048 px**, the
   overview 1 002 (`detailViewBox "0 0 1048 58"`).
2. Each segment is `C cx y0, cx y1, x1 y1` with `cx` the midpoint (`:233-236`, `:250-253`): an
   S-curve that is flat at both ends. Over a 1.05 px step it is a vertical jump — so every change of
   count renders as a **1-px riser** and equal neighbours as flat plateaus (a staircase), not a smooth
   curve. Counts are integers, so heights move in fixed sub-pixel quanta (live: 24.316 → 24.737, a
   0.42 px step), which antialiasing turns into the "pixelated" edge.
3. A dot `r=2` with a `<title>` is drawn at **every** non-empty bucket (`:263-273`): ~1 000 overlapping
   circles thicken the 1.5 px line into a ~4 px band with a blob at each riser (`shots/30-timeline-zoom-dpr3.png`).
4. Secondary: `preserveAspectRatio="none"` with `viewBox` width truncated to `w.toInt`
   (`:326,470,597,700`) stretches by the fractional pixel; on narrow layouts CSS heights are
   `clamp(...)` (`style.css:1089-1090`) while the viewBox height is fixed → vertical non-uniform
   scale (dots become ellipses; only the line has `non-scaling-stroke`, `:257`). On resize the
   curve layer is scaled by a transform until redrawn (`:318-319`, `:401-406`).
No `devicePixelRatio` is involved (SVG).

### (b) Playback ends on the last frame

`TimelinePlayback.tick` (`web/TimelinePlayback.scala:143-146`): when `next >= spanTo` it calls
`stop()` **without drawing a final frame and without re-rendering the full span**. `stop()`
(`:123-130`) only clears the playhead, the feed and events and calls `MapView.setPlaybackMode(false)`,
which re-loads just the boundaries overlay with `currentSpan` (`web/MapView.scala:85-90`) — and
`currentSpan` was last set by `playFrame → render(points, span)` to the **last frame's ±50-year
window** (`web/MapView.scala:106-111,435-436`). Nothing calls `App.quickRenderIfCached` /
`MapView.render(cachedFindings, (from,to))` (its only callers are timeline drags,
`web/TimelineView.scala:106,749`), so findings, the legend range, sites/media/events tiles and the
bubble colours stay as in the last frame. The same happens on ⏹.

### (c) Identical coordinates: cluster never splits; media in it cannot be opened

- Clustering is greedy screen-distance grouping (`web/map/PointSet.scala:18-67`); points with equal
  lat/lon project to the same pixel at every zoom, so they are grouped forever. There is no
  spiderfy / fan-out.
- Bubble click (`web/MapView.scala:279-288`): at max zoom it lists the bubble in the sidebar
  (`ResultsList.pick`, `web/ResultsList.scala:94-153`) with media as "other" rows.
- A media row's action is `Media.open → MapView.reveal(key, popup)` (`web/PointKinds.scala:192`) →
  `PointSet.zoomToShow` (`web/map/PointSet.scala:204-221`), which zooms until the point is
  `drawnAlone` and only then calls `andThen`. For co-located points it reaches `maxZoom`, cancels,
  and **silently never shows the popup** (`:216-218`). Live data: 58 of 500 media coordinates hold
  2–4 pictures (e.g. 4 at Hattusa museum 34.5576,36.52).
- `zoomInto` has a special case for zero-size boxes (`web/MapView.scala:270-276`) but only zooms.

### (d) Images: thumbnails only

- Map pictures are shown only as the stored thumbnail: popup `<img class="media-popup-thumb">`
  max 220×160 (`web/MapView.scala:670`, `style.css:1278`), hover card `cite-tip-thumb` max-height 200
  (`web/Preview.scala:674-693`, `style.css:1274`). Neither is clickable.
- The lightbox exists but only for `<img>` whose `src` contains `/assets/` (source figures in a page,
  `web/ArticleModal.scala:32-42`); `/media/<uid>/thumb` never matches.
- The server stores a capped rendition plus a thumbnail and the provider's `original_url`
  (`db/.../MediaRepo.scala:48-66`), but `MediaGeoHit` exposes only `thumbUrl`
  (`api/shared/.../api/Media.scala:117-131`), so the UI has no URL for a full-size view or the
  provider's page.

### (e) Long image names overflow

- `.hd-popup` content has no `overflow-wrap` (`style.css:604-613`); the popup is `maxWidth 340px`
  (`MapLibreEngine.scala:293`); its title is the raw `assetName`
  (`web/MapView.scala:665-673`). Live: 108 of 500 picture names contain a run of ≥40 characters
  without a space (Commons/Europeana file names with `_`), up to 180 characters — these run out of
  the popup.
- The cluster pick row title (`.hit .title`, `web/ResultsList.scala:159-163`) has no wrap rule either.
- `.cite-tooltip` does have `word-wrap:break-word` (`style.css:673-676`), so hover cards are less
  affected; the credit line uses `overflow-wrap:anywhere` (`style.css:1277`).

---

## 7. Screenshots (`shots/`)

| file | content |
|---|---|
| `01-default-1440.png`, `01b-default-1440-dpr2.png` | default wide view |
| `02-tablet-820.png`, `02b-tablet-1180-landscape.png`, `02c-phone-390.png` | narrow layouts |
| `03-darkscheme-1440.png` | `prefers-color-scheme: dark` — identical (dark-only UI) |
| `10-win-*.png` | each toolbar window: goals, tasks, runs, managers, approvals, acquire, upload, merge, jobs, log, about, models |
| `11-win-many.png` | goals + backlog + runs + jobs cascaded |
| `20-tab-*.png` | sidebar tabs articles, events, by time, by region |
| `30-timeline-zoom-dpr3.png`, `30b-timeline-zoom-dpr1.png` | timeline staircase close-up |
| `40-win-source.png` … `45-win-graph.png` | source, run, goal, manager, article, graph windows |
| `50-presets-popover.png`, `51-map-options-popover.png` | popovers |
| `53`–`58` | tablet: filters drawer, map & tools drawer, timeline open, sheet half, window on tablet, detail menu |
| `59-layers-all-on.png` | all map layers on (density) |

Note: `52-legend-key.png` is the default view — the scripted click on the legend timed out.
