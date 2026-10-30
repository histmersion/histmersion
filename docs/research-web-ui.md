# Web UI redesign — research and proposal

**Status (2026-10-27):** research and a visual proposal only. Nothing in the UI changes until the
maintainer has seen and accepted the visual design of every change. The full reports are in
[`research-assets/web-ui/`](research-assets/web-ui/):

| report | what it holds |
|---|---|
| [`evidence-review.md`](research-assets/web-ui/evidence-review.md) | what peer-reviewed HCI, cognitive-psychology and visualisation research shows, 13 topics, ~150 references, evidence grades |
| [`audit.md`](research-assets/web-ui/audit.md) | inventory of every view and control, grouping vs data model, consistency, windows/tabs, credits, five confirmed bugs (file:line) |
| [`capabilities.md`](research-assets/web-ui/capabilities.md) | what the CLI/MCP can do that the web UI cannot, first-run steps, how agents find sources, what planned features need |
| [`icons-licences-pleiades.md`](research-assets/web-ui/icons-licences-pleiades.md) | every map icon judged, open-licence candidates (SVGs in `icons/`), which map credits the law requires, which Pleiades fields we drop |

## 1. Why

Each element of today's UI is defensible on its own; together they overload. The maintainer's list:
always-visible map, research/agent/job and filter controls; no browsing view for sources and
articles; licences shown for every map source; backlog, worklog, runs, jobs and managers that look
like unrelated tools; `snake_case`/`kebab-case` labels; inconsistent fonts, borders and styles; no way
to open things in a new tab; functions the web UI cannot reach (downloads, unpausing, re-fetching);
a jagged timeline; playback that stops on its last frame; coincident points that never split;
thumbnail-only pictures; overflowing names; unintuitive event and site icons; Pleiades descriptions
not shown; and planned features (notes, bookmarks, read marks, optional multi-user, permissions,
visual config) with nowhere to go. The goal: a UI a non-technical person can use without
instructions, built on what research shows helps people work — not gamification, dark patterns or
fashion.

## 2. What the audit measured

- **Density.** ~59 controls are visible at once on a 1440×900 screen before any window is open
  (11 tools, 15 map-layer controls, 11 filters, sidebar tabs, timeline). 392 px of bars sit above the
  map, which gets 42 % of the viewport. Two identical `+ / −` pairs sit 110 px apart (timeline zoom
  and map zoom); two year ranges (publication year and period) look alike. Four open windows raise
  the count to 243.
- **Grouping.** An agent run *is* a job row, yet runs and jobs are separate windows that never link
  to each other. 666 goal → backlog links exist in the data and none is clickable. Worklog entries
  name runs only in prose. The models window is reachable only from inside the runs window.
- **Consistency.** Raw identifiers reach the reader in at least 15 places (`review-article`,
  `article-publish`, `basemap-fetch`, `seven_day`, a config key in prose, citekeys as window titles).
  29 font sizes, 18 radii, 30 shadows, 51 literal colours; two conflicting palettes for source status;
  dark theme only.
- **Tabs.** Every window has a hash URL, but no control is a link, so nothing can be middle- or
  ⌘-clicked into a new tab (lost with the window manager on 2026-09-18).
- **Credits.** A permanent 300 × 110 px box on desktop; a raw `CC-BY-SA-3.0` code token in picture
  popups.

## 3. What the evidence supports (short form)

Ranked by strength of evidence and relevance (full citations in the review):

1. Filter and brush responses under ~100 ms (Liu & Heer 2014).
2. Keep the state people need on screen — active filters, position, comparison targets
   (Cowan 2001; Plumlee & Ware 2006).
3. Interrupt rarely; batch non-urgent notices (Iqbal & Horvitz 2007; Mark et al. 2008).
4. Good defaults beat settings (Jachimowicz et al. 2019 meta-analysis; Mackay 1991).
5. Stable layouts; never auto-rearrange. Users choose what to show (Findlater & McGrenere 2004;
   Sears & Shneiderman 1994).
6. Group dense content visibly instead of thinning it (Rosenholtz et al. 2007; Wolfe & Horowitz 2017).
7. Linked views — map ↔ timeline ↔ list — beat toggling (North & Shneiderman 2000).
8. WCAG 2.2 AA helps everyone: contrast, target size, keyboard, focus (Schmutz et al. 2016).
9. Design for re-finding: history, visited marks, bookmarks, search — not folder trees
   (Teevan et al. 2007; Whittaker et al. 2011).
10. Supervising automation: keep people in the loop, make state visible, add friction against
    rubber-stamping consequential approvals (Endsley 1995; Parasuraman et al. 2000; Buçinca et al. 2021).
11. Label icons (Wiedenbeck 1999).
12. Visible navigation and strong signifiers (Pernice & Budiu 2016; Burmistrov et al. 2015).
13. Teach in context and by doing, not with up-front tours (Carroll et al. 1987; Kendrick 2020).
14. Plain, consistent labels with good information scent; broad and shallow structure
    (Pirolli & Card 1999; Larson & Czerwinski 1998).
15. Animation for spotting change, static/small multiples for comparing (Robertson et al. 2008;
    Griffin et al. 2006).

**Not supported:** the "7 ± 2 menu items" rule; adaptive UIs that reorder themselves; mandatory
tours; hidden/hamburger navigation on desktop; icon-only toolbars; maximal minimalism as a usability
rule; animation as the default analysis view; folder hierarchies for notes; streaks, badges and
points; judging usability by looks. **Weak or missing evidence:** marker clustering/spiderfying,
unified activity feeds, setup wizards, empty states — the proposal follows the strongest adjacent
evidence and the evaluation plan (§ 9) tests these.

## 4. The proposal

The visual design is a canvas of 11 screens (Explore, Explore with layers and a selected place, map
details, Library, Research overview, a goal page, Settings, first-run setup, tablet, visual system,
icons), shared with the maintainer for review.

### 4.1 Four places, always labelled

One top bar: **Explore** (map + timeline) · **Library** (browse and read) · **Research** (goals,
agents, decisions, activity) · **Settings**. It replaces the 11-button toolbar; every former window
has a home in one of the four (table in § 4.6). The bar also carries one search field, an agent
status pill ("Agents working · 3 runs") and a "N need your decision" pill — the only standing
notification, shown only when there is something to decide. A logo slot sits at its left and costs
no extra space.

### 4.2 Explore

- **Filters** move to one left panel shared with Library, foldable to a 48 px rail that shows the
  count of active filters. Words, place, period and topic are visible; authors, status, tags and
  publication year sit behind "More filters", but an *active* filter always shows as a chip. The
  period is set on the timeline and echoed in the panel, so the two year ranges can no longer be
  confused. "Save this view" stores filters + map position (the first bookmark kind).
- **Layers** move into one map panel. A layer's options appear only while it is on (today they stay
  visible, disabled).
- **Timeline** sits under the map: one labelled Play button and speed, "Zoom to period" and
  "Show all time" as text buttons (so they can't be mistaken for map zoom), a smooth area curve
  without per-bucket dots (fixes bug (a), § 5), exact counts on hover. It folds to one line.
- **Results** on the right: "On the map / Everywhere", tabs for sources, articles, events, places
  and pictures with counts; every row is a real link with bookmark and new-tab actions.
- **Selecting** anything replaces the results with its detail (← back), from where it can be pinned
  as a floating window or opened in a tab.
- The map gets ~70 % of the screen instead of 42 %.

### 4.3 Library

A reading-first view with the same filters: "My library" (everything, bookmarks, my notes, not read
yet, recently opened), a sortable list (title, year, status, period; a dot for unread), and a reading
pane (serif, 62-character lines) with Bookmark, Add note, Mark as read/unread, Show on the map, Open
in a new tab, and links to what a source cites, who answers it and the articles that cite it. Adding
sources (upload, DOI, Calibre import, merge a collection) moves here from the toolbar.

### 4.4 Research — one system

- **Overview:** agent state as one three-way control — *Working · Finish current work, then pause ·
  Pause now* (today: autopilot on/off + drain/undrain); weekly AI-limit use; **Needs your decision**
  with the reason and a link to the evidence before the accept button (friction against
  rubber-stamping); goals with progress and what blocks them; one **Activity** list.
- **Activity** merges agent runs, background jobs, worklog entries and the person's own decisions in
  one time-ordered list, filterable (running now, agent work, background jobs, your actions,
  problems). Agent runs and long jobs get Pause/Cancel in place.
- **A goal page** shows its plan, each step linked to the agent work that did it, what it produced
  (articles, sources, backlog items, items waiting to be acquired) and its history.
- **Backlog** and **To acquire** stay separate lists, each item linked to its goal and to the run or
  job that created or closed it.
- Data changes needed: a `goal_slug`/`run_id` link on queue items; worklog entries written with
  structured references instead of prose; one activity endpoint over jobs + queue log + decisions;
  pause/cancel for background jobs (today only agent runs can be paused).

### 4.5 Settings and first-run setup

Everything the CLI can download, refresh, pause or configure gets a place: data and downloads
(background map, sea depth, gazetteers, periods, event catalogues, picture providers — each with
size, last update, Update/Pause/Remove), where to look for sources, AI agents, keys (vault
references, never plain values), Calibre, backups (back up now, list, restore), plugins, credits,
people and access (only when multi-user is on), "Advanced (all settings)" as a generated form over
the full config, and "Run the setup again". First run is a 7-step setup with safe defaults and
"Skip the rest": finding open papers (contact e-mail, used only for Unpaywall/Crossref), maps (area to
download, where the map opens — task #6552), background data, research agents (optional), Calibre
(optional). Needs: an API that writes the `.conf` file itself (decision 6 in § 7), on top of the existing
reload machinery, and a restart trigger.

### 4.6 Windows, tabs and addresses

Every item opens three ways: click (side panel), pin (floating window; minimised windows fold into a
tray), ⌘/Ctrl-click or ↗ (new browser tab). Every page and item gets a real path (`/explore`,
`/library`, `/research/activity`, `/research/goals/<slug>`, `/settings/data`, `/source/<ck>`,
`/article/<slug>`, `/place/<id>` …), every link is an `<a href>`, and the in-window link handler
respects modifier keys.

| today | proposed home |
|---|---|
| research goals, approvals, agent managers, agent runs, models | Research › Goals, Needs you, Activity, AI models and limits |
| backlog, worklog, jobs | Research › Backlog, Activity |
| to acquire | Research › To acquire |
| upload, merge another collection | Library › Add sources |
| about, map regions, storage | Settings › About, Data and downloads |
| API key | Settings › Keys (and a sign-in prompt when a write needs it) |

### 4.7 Visual system

Two typefaces bundled for offline use (Source Sans 3 for the interface, Source Serif 4 for reading;
both OFL), six sizes (12/13/14/16/20/26), two weights, three radii (4/8/pill), two shadows, light and
dark themes following the system setting, one token set used by every component. Blue = place and
selection, ochre = time, orange-brown = needs you/problem. One source-status code everywhere, with
colour, shape **and** word. Targets ≥ 32 px (desktop) / ≥ 44 px (touch); visible focus;
`prefers-reduced-motion` respected.

### 4.8 Wording

Sentence case everywhere; plain names instead of identifiers (`review-article` → "Review an
article", `article-publish` → "Make an article readable", `basemap-fetch` → "Download the background
map", `seven_day` → "This week's limit", drain → "Finish current work, then pause"). One label
dictionary replaces the fall-backs that show identifiers today (`ListFilterBar` `identity`,
`Label.display`); identifiers stay visible under "Details" for command-line users.

### 4.9 Map

- **Credits:** OpenStreetMap's licence requires a visible credit on the map; it may fold to an "i"
  after the first pan/zoom, and one Credits page covers the rest. Fix the missing land-cover credit
  and the wrong Cliopatria licence label. The terrain notice moves behind the "i".
- **Coincident points** fan out around their spot on click (Esc folds them); list rows open the item
  directly instead of zooming.
- **Pictures** open full size with author, linked licence, provider page and "save the original";
  ‹ › steps through pictures at the same place; long file names wrap and a readable title is derived.
- **Playback** ends by restoring the whole selected period and says so, with "Play again".
- **Place card:** import the Pleiades description, place types, connections (e.g. the Arkadiko
  bridge ↔ Tiryns, Epidauros), references, Wikipedia/Wikidata links, location accuracy and name
  periods, and show them on the place's detail.
- **Icons:** keep Lucide (upgraded to 1.x) and add Tabler, which draws the same way; a few CC0/Apache
  glyphs redrawn as line icons; custom glyphs for earthquake, naval battle, siege, palace, burial
  mound (and aqueduct); split infrastructure into bridge, road and aqueduct; resolve the castle and
  crown collisions. Every icon is labelled in the legend; each gets a recognition check before
  shipping (§ 9).

### 4.10 Room for planned features

Bookmarks, notes and read marks have places in the item header and in Library › My library. Notes
list where they were left. Multi-user stays optional: in single-user mode there is no sign-in and no
user menu; when enabled, a user menu appears, and Settings › People and access manages roles, with
the UI hiding what a role cannot use (server-side checks first; today reads are always open and one
shared key gates writes). Config editing appears as Settings and "Advanced (all settings)". Bookmarks, notes and read marks are built (§ 10, phase 8); multi-user is proposed in
[`research-multi-user.md`](research-multi-user.md).

## 5. Bugs found, independent of the redesign

Confirmed in code (details in the audit, § 6): (a) the timeline draws ~1 000 one-pixel S-curve steps
plus ~1 000 dots, hence the jagged line and 1-px risers; (b) playback never redraws the full span when
it ends or stops; (c) identical coordinates cluster forever and a media row in such a cluster silently
never opens; (d) the map feed carries only thumbnail URLs, and the lightbox handles only `/assets/`
images; (e) the map popup has no wrap rule for long names. These can be fixed before or with the
redesign.

## 6. How agents find sources (not a UI change)

Histmersion's own agents never use Wikipedia: every agent role has `web: none`, and new-source search
queries OpenAlex, Crossref, Europe PMC, arXiv and CORE, plus works citing a goal's sources and newer
work by their authors. Wikipedia enters only through the research workspace's `trailhead` skill and
the MCP `discover` tool description, both of which suggest starting from a Wikipedia article and take
only its DOI/arXiv references — never its text. Recommendation: start topics from highly cited recent
review articles and the citation graph, demote Wikipedia to an optional last resort, and reword the
skill and tool description to match.

## 7. Decisions (2026-10-28)

1. **Screens: accepted (2026-10-28)** — all page designs on the canvas. Implementation follows § 8.
   Icons: burial, palace, shipwreck and collapse accepted in round 2; eruption, earthquake and tsunami
   are in a third round.
2. **Theme:** one button cycling *system → light → dark*, as in the documentation site; system by default.
3. **Fonts:** ship them (Source Sans 3, Source Serif 4) with the app.
4. **Bugs (§ 5): fix now**, plus one more: opening the filters or resizing the right sidebar must not
   reload or visibly redraw the map — and a regression test keeps it that way through the redesign.
5. **Icons, round 2:** eruption = cone with an ash cloud; earthquake = a house with shake lines;
   tsunami = one big breaker that reads at 15 px; siege = three swords closing on a house; collapse =
   the state-formation crown cracked and toppling (ruins read as a site); invasion = a sword driven
   into a house; burial = a gravestone; palace = a crown on a colonnade; shipwreck = the period galley,
   sinking.
6. **Settings live in the `.conf` file**, never in the database: a person must be able to repair a
   broken configuration without the web UI, the database or the server running. The settings UI and
   setup write the file (keeping comments and unknown keys) and trigger the existing reload.
7. **Wikipedia is supplementary**: never ranked above querying the research databases. The
   `trailhead` skill now starts from recent reviews and the most-cited work, then the citation graph;
   `wikirefs` comes last, to catch gaps. The `discover` tool description and the user guide say the same.

## 8. Phasing (after acceptance)

1. Tokens, type, both themes, label dictionary — no layout change.
2. Top bar, routes and real links; windows keep working.
3. Explore: filter panel, layers panel, timeline, results, detail panel, map fixes.
4. Library.
5. Research unification (with the data links in § 4.4).
6. Settings, setup and a `.conf`-writing API (comments and unknown keys kept; reload afterwards).
7. Icons and the Pleiades place card.
8. Planned features: bookmarks, notes, read marks, then optional multi-user.

Each phase lands behind the existing browser tests, measured at live data volumes with all layers on,
plus refreshed user documentation and an in-context tutorial at the end.

## 9. Evaluation plan

Cheap and repeated rather than one big study: heuristic evaluation by 3 evaluators against Nielsen's
heuristics and WCAG 2.2; a cognitive walkthrough of five core tasks (find sources on a place and
period, read and bookmark one, approve an agent decision, download pictures, set up from scratch);
first-click tests on the new navigation; an icon recognition test (target ≥ 85 % correct, as in
ANSI Z535.3) for every map glyph; 2–3 rounds of think-aloud with 5 people each, including
non-technical users; SUS and NASA-TLX before and after. Details in the evidence review, § 13.

## 10. As built: phase 5, Research as one system (2026-10-30)

- **Overview** (`/research`): one three-way control over the two switches (`AgentMode`: working = the
  autopilot on; finish = on and draining; pause = off), with the running and waiting counts and each plan's
  weekly limit left; the three oldest open decisions, each with its reason and the link to what to read
  first placed before Accept (no bulk accept here — the decisions page keeps it); the top-level goals with
  their managers' steps done of all and what blocks them (the focus's reason, a decision, a starved or held
  manager); the latest six activity entries; the managers in a disclosure.
- **Activity** (`/research/activity[/running|agents|jobs|yours|problems]`): `GET /api/activity` merges the
  jobs (agent runs among them), the worklog and a person's decisions in SQL (`ActivityService`, one
  `UNION ALL`, keyset-paged by time then key in byte order), the same rules in memory for tests
  (`ActivityRules`); MCP `activity`, `histmersion activity [--kind] [--running] [--since] [--goal]`.
- **Links in the data**: V108 gives `queue_items` `goal_slug`, `created_job`, `closed_job` (back-filled
  from goal links, `goal <slug>` topics and the ingest jobs that ticked items off); agents and jobs write
  them from now on; `?goal=` and `?job=` list a goal's or a run's items; Filter gains `goal` (a goal's
  sources, in Library and on the map).
- **Pause and cancel of long jobs**: `POST /api/jobs/{id}/{pause|resume|cancel}` (MCP `job_control`,
  `histmersion jobs pause|resume|cancel`). The media and events imports, the base-map download, the
  acquisition re-check and the init job's gazetteer loads stop at the next progress report (a part
  boundary), keep their progress and resume on the same row, skipping what they did; a restart fails a
  paused one as interrupted. `JobState` gains `paused` and `cancelled`.
- **A goal's page** (`/research/goals/<slug>`), **Backlog / To acquire** with goals, provenance and the
  re-check buttons, and **#7381**: goal stages each with their own colour, shape and word (tokens in both
  themes, AA), and every link the accent.

## 10. As built: phase 6, Settings and the first-run setup (2026-10-30)

- **The settings file is written, never the database** (decision 6). `config.edit` (config module) changes the
  file's TEXT through sconfig's `ConfigDocument`, so comments, order, layout, includes and unknown keys stay;
  added lines are written in the file's own style (`key {`, `key = value`) and a block a change empties is taken
  out unless it holds a comment. Each change is checked against the schema of every setting read from
  `reference.conf` (`ConfigSchema`: kind, default, the comment that documents it, its environment variable,
  restart-only, credential), written to a temporary file beside the file, read back WITH its includes (the change
  must be what the file now says: a later include or a second line of the same key is caught), loaded as the
  server loads it (`AppConfig.loadWithUserFile`), and moved over the file in one step with the old text kept as
  `.bak` (and the file's permissions). Refused, file untouched: a setting written with `${…}` or `+=` (also when
  a duplicate plain line would win over it), one set in an included file, a wrong kind, an unknown key (with the
  closest ones), a value the server could not start with, a change against a stale `version` (SHA-256 of the
  text: 409). Setting what the file already says writes nothing.
- **Credentials**: a plugin's is written as a reference (`env:NAME`, `<vault>:<entry>`); `value:` only with
  `literalConsent` and a warning; a setting that is the credential itself (`auth.api-key`, `db.password`, the
  providers' keys) is never written — its environment variable is named instead. No answer carries a value
  (`SettingShown`: only the rule, the form — unset, env, vault, value, plain — and the reference).
- **Surfaces**: `GET|POST /api/settings`, `POST /api/server/restart`; MCP `config` gains `get`, `set`, `unset`,
  `restart`; `histmersion config get|set|unset|restart`, which change the file on this machine directly with
  `--local` or when the server does not answer. After a change the server reloads (the existing hot reload);
  a restart-only setting is said, with **Restart the server now** where the server runs under its LaunchAgent
  (`XPC_SERVICE_NAME`) or systemd unit (`INVOCATION_ID`): it exits with `server.restart-exit-code` (75) and the
  service starts it again. `domain.settings`: `SettingsService` (Implementation over the server's file; InMemory
  over a text, by the same rules), `RestartService`, `SettingsControllers`.
- **Settings** (`/settings/<section>`): General (theme and map motion, this browser's; where the map opens,
  `ui.map-start` — #6552: the browser's last view under `last`, kept in `histmersion.mapView`, else the place,
  found by name in the gazetteer or taken from the map's view now), Data and downloads (background map, sea
  depth, the gazetteers, boundaries and periods, the event catalogues, the picture providers — each with its
  state, Update / Check for update / Load / Remove over the existing jobs; the downloads under way with
  Pause / Resume / Cancel; the disk used; the old map-regions panel folded under it), Where to look for sources
  (contact e-mail, said to go to Crossref, OpenAlex and Unpaywall only; each finder on or off), AI agents, Keys
  and passwords, Calibre and files, Backups (keep N, Back up now, the list; restore stays on the command line),
  Plugins, People and access (a placeholder), Advanced (every setting, generated from the schema, a block at a
  time, with a filter), Credits and licences, About, Run the setup again. One Save per card; the notice under it
  says what changed, what waits for a restart, the warnings and a reload that failed. `/about` and `/storage`
  lead to Settings.
- **The first-run setup** (`/setup`): seven steps (Welcome · Finding open papers · Maps — the areas of
  `basemap.setup-areas` with their size and the disk's free space, and where the map opens · Background data ·
  Research agents · Calibre · Ready), safe defaults, nothing written or downloaded before **Finish and start the
  downloads**; **Skip the rest** writes what was chosen so far and downloads nothing. Due while
  `setup.completed` is false and the settings file sets nothing of its own (the installer's all-comment template
  counts as nothing) — decided from the file, never the database; `/api/config` carries `setupDue`. A tab that
  leaves it without saving is not sent back until it is closed (`sessionStorage`).
- **#7382**: a source's or an article's own page has a toolbar above its text — **Reading width** / **Page
  wide** (`histmersion.pageWidth`, per browser) and the theme button, there also where the top bar hides its own.
- **Tests**: the config module's suites over real files (comments, includes before and after, substitutions,
  `+=`, permissions, a symbolic link, idempotence, the round trip), the controllers over InMemory services, an
  HTTP suite (a change written and reloaded, a refused one, the setup due, the restart refused), web unit
  suites, and browser tests (the setup's steps and that nothing is written before the end; every section; values'
  sources and no credential; a change written, read back and set back to its default; a refused value; the map's
  last view; #7382). The browser tests that write the settings file run only when `HISTMERSION_TEST_CONFIG_FILE`
  names the test server's own file; the harness answers `/api/config` with `setupDue: false` unless a test asks.

## 10. As built: phase 8, bookmarks, notes and read marks (2026-10-30)

- **The person's data is in the database, shaped to travel** (V109; `research-deterministic-merge.md`): `bookmarks`,
  `notes` and `read_marks`, each row a UUIDv7 `id`, `origin` (the instance that made it), `created_at` /
  `updated_at`, a `user_id` (the fixed `local` user while the server runs for one person) and, for bookmarks and
  notes, a tombstone in the row (`deleted_at`) instead of a delete. A **target** is an item of the collection
  (`source`, `article`, `event`, `place`, `picture`, `goal` with its key; its `uid` kept where the table has one,
  so a renamed source keeps its bookmark and shows its new title), a `point` on the map (lat, lon, optionally
  the years), a saved `view` of Explore (the address's state after `#` — filters, span, layers — with the map's
  centre and zoom), or a `period` (years). No foreign keys: a deleted item leaves what the person wrote.
- **The read rule**: an item is read only when the person says so (**Mark as read**); opening it (Library's
  reading pane, Explore's detail) is kept apart as `opened_at`, for **Recently opened**, and marks nothing read.
  Marking unread keeps the row with no read time (the row is the item's latest state, as a register).
- **Surfaces** (`domain.personal`: `BookmarkService`, `NoteService`, `ReadingService` — Implementation over the
  tables, InMemory over one shared state by the same rules — and `PersonalControllers`): `GET|POST
  /api/bookmarks`, `PATCH|DELETE /api/bookmarks/{id}`, `GET|POST /api/notes`, `GET|PATCH|DELETE /api/notes/{id}`
  (an edit names `ifUpdatedAt`: 412 when the note changed since), `GET|PUT /api/reading`, `POST
  /api/reading/opened`, `GET /api/reading/unread`; `histmersion bookmarks list|add|rm`, `histmersion notes
  list|show|add|edit|rm`; MCP `bookmarks` and `notes`, **read only** — no tool adds, changes or removes them.
- **The item's header** (`web.marks.ItemActions`, in the reading pane and Explore's detail): Bookmark (pressed
  while it is), Add note, Mark as read / unread, "⋯"; the item's notes under it, each with Change and Delete.
  The **note editor** is a plain text box with a Markdown preview (`NoteMarkdown`, a small safe subset built as
  elements, never HTML), Save / Cancel, drafts kept per browser (`histmersion.noteDraft.*`, every access
  guarded) until saved or cancelled.
- **My library**: Everything, Bookmarks, My notes, Not read yet (the server's, a page at a time, per tab),
  Recently opened, with counts; the shelf is in the address (`?shelf=`); a row opens back where it was left
  (`OpenBack`); a dot marks a source or an article not marked read, in the shelves and Library's table.
- **Saved views**: "Save this view…" in Explore's filter panel (as the mock-up shows), listed there and in
  Bookmarks; opening one restores the hash state, the span and the map's centre and zoom.
- **One context menu** (`web.ui.ContextMenu`): right click, the menu key or Shift+F10, and a visible "⋯" on the
  reading pane, the map and the timeline; ↑ ↓ Home End, Enter, Escape returns the focus; no action is only there.
  Rows and the reading pane: copy the text, the Markdown (`[title](link)`, a source's `[[citekey]]`) or the link,
  bookmark, open in a new tab. The map: bookmark this place, add a note here, lock the map (`MapSurface.setLocked`:
  drag, wheel, keys, pinch and the zoom buttons off, a notice with Unlock), start the map here (`ui.map-start`
  through the settings service), bookmark this view. The timeline: lock it (no drag, click, wheel or button
  moves the span; the page's own moves still do), start it at its years — the existing `ui.time.default-from` /
  `default-to`, so no new key —, and **my periods**: there are no custom presets, so a period is a bookmark of
  kind `period`, listed first in "Zoom to period" (and in Bookmarks), × removes it. The presets menu now opens
  upwards: under the timeline at the foot of the window it scrolled the whole page.
- **The map's "My notes" layer** (`MapLayer.Notes`, off by default like every layer): notes left at a point, as
  DOM markers in the markers pane with the markers' tokens; a click or Enter shows the note in the map's popup.
- **#7781**: a result row's actions (bookmark, new tab) are a column of the row itself (`.has-actions` grid),
  at its right, as tall as the row (the row's padding as `--row-py`/`--row-px`), lit with its hover and focus;
  they were a float that overflowed into the next row. The same on articles, events, places and pictures.
- **#7780**: the results column defaults to 480 px — five tabs with five-digit counts measured 462 px at 1440×900;
  the tablet's side sheet follows the same width with the desktop's type size (it needed 525 px at the tablet's
  15 px) — and keeps the width per browser (`histmersion.resultsWidth`), 320 px to 60 % of the window, by drag or
  by the focusable separator (← wider, → narrower, Shift ×4, Home, End). Below the fit the tab row scrolls with
  shaded edges, never clipped silently. Resizing still neither refetches nor rebuilds the map.
- **Not done**: bookmarks, notes and read marks do **not** travel in `.hmx` bundles — no person data does today
  (`place_overrides` neither, `research-deterministic-merge.md` § 2.6); the rows carry what the exchange will
  need (uid, origin, timestamps, tombstones, user). Optional multi-user is a design only:
  [`research-multi-user.md`](research-multi-user.md).
- **Tests**: the same scenarios over the in-memory and the database services (`PersonalServicesSuite`), the
  controllers, an HTTP suite (REST and the read-only tools), the wire, the Markdown, the CLI's flags; Scala.js
  suites for the pure parts; `modules/web/test/marks.test.mjs` (13 Chromium tests, in `test:browser`): bookmarks
  of an item and of a saved view, notes (preview, draft, save, change, reopen at the target, delete), a map-point
  note from the map's menu by keyboard and the layer, read marks and the shelves, the context menu's mouse and
  keyboard paths and its copies, the map lock, the timeline lock and my periods, the starts written to and taken
  out of the settings file (only with `HISTMERSION_TEST_CONFIG_FILE`), #7780 (the fit at 1440×900 and 820×1180;
  the width by keys, by drag, remembered, no refetch) and #7781 (the actions inside their own row, the pointer
  over them meets that row).
