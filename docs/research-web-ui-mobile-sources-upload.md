# Research: mobile web UI, a sources map toggle, uploading from the acquisition queue

**Status:** research only. Nothing described here is implemented yet (2026-09-29).
The code references are to `master` at `f5f7032`.

This covers three changes:

1. **Mobile-friendly web UI.** Desktop has to behave exactly as it does now.
2. **A "sources" map toggle** placed before the boundaries toggle. It hides or shows the finding
   markers and is on by default.
3. **Upload from the queue.** In the upload pane you pick a book from the queue and the form fills
   in. Once the upload *and its OCR* finish, the item is ticked off automatically.

> **An assumption about item 3.** The toolbar shows two lists: **"to acquire"** (`Queue.Kind.Acquire`,
> meaning books to get) and **"backlog"** (`Queue.Kind.Tasks`, meaning work to do). This document
> assumes "a book from the backlog" means the **"to acquire"** list. That is the only list whose
> items are books, and the only one with a "fulfilled by a source" state (`QueueView.Spec.fulfils`).
> If you meant the tasks backlog, the design below still works: widen the picker's `Kind` and let
> `fulfils` include `Tasks`.

---

## 1. Mobile-friendly web UI

### What exists today

The app is a Scala.js / Laminar SPA (`modules/web`). `modules/server/src/main/resources/web/`
serves it with a single stylesheet, `style.css` (618 lines). The layout was built for desktop only:

| piece | where | why it breaks on a phone |
|---|---|---|
| App grid `1fr 360px`, 4 rows | `style.css:5`, `App.scala:35` | The sidebar is a fixed column that takes the whole screen width on a phone. **`App` also writes `grid-template-columns` as an inline `style` from `sidebarWidth`.** An inline style beats any `@media` rule, so CSS alone cannot collapse the grid. |
| Sidebar resize handle | `App.scala:81-100` | 6 px wide and pointer-drag only. Pointless on touch. |
| Filter bar with the toolbar at its end | `FilterBar.scala`, `Toolbar.scala`, `.fbar` | Wraps into 3–5 rows at 390 px and pushes the map off screen. |
| Timeline (overview and detail SVGs) | `TimelineView.scala`, `.tl` | Usable: it already has `touch-action:none` and pointer events. Too tall next to everything else, though. |
| Layer toggles and base-map select | `ViewportToggle.scala` | Text labels overflow. |
| Floating windows (`WindowManager`) | `WindowManager.scala:222-288`, `.wm-window` | Absolutely positioned. The size is `min(780, 0.7·vw)` and the position is at least `(20, 60)`. They can be dragged and resized, and the 16 px resize corner is not a usable touch target. The title bar has no `touch-action`, so dragging it scrolls the page. |
| Hover previews | `Preview.scala:98` (`mouseover`), Leaflet site tooltips | Hover does not exist on touch. Tapping a `src-ref` opens a window, which is acceptable, but the preview is lost. |
| `#status`, `.wm-taskbar`, Leaflet legend | `position:fixed` / bottom-right | They overlap each other and the map's attribution. |
| Existing media queries | `style.css:76, 319, 407` | Reduced motion, `max-height:820px`, and article TOC `max-width:1100px`. None of them handles width. |

`index.html` already has `<meta name="viewport" content="width=device-width, initial-scale=1">`.

### Recommended approach: one "narrow" layout mode, driven from Scala, scoped in CSS

**(a) One source of truth for the breakpoint.**
- AGENTS.md forbids constants in code, and `@media` cannot read `var()`.
- So the breakpoint goes in config: `histmersion.ui.narrow-max-width-px = 760`, with
  `${?HISTMERSION_UI_NARROW_MAX_WIDTH_PX}`. It reaches the page through `UiConfig` and `/api/config`,
  like every other UI default.
- A small `Viewport` object in `modules/web` does three things:
  - it subscribes to `window.matchMedia(s"(max-width: ${px}px)")`;
  - it exposes `Viewport.narrow: Signal[Boolean]`;
  - it sets `data-layout="narrow"` on `.histmersion-app`.
- All mobile CSS is keyed off `.histmersion-app[data-layout=narrow] …` rather than an `@media` literal.
- Desktop never receives the attribute, so **no rule can reach desktop**. That is the guarantee that
  desktop stays as it is.
- The pure part, `Viewport.mode(width, breakpoint)`, gets a munit test next to `UiStateSuite`.

**(b) Layout in narrow mode.** The layout is a single column: the map is primary and everything
else sits on top of it.

```
┌─────────────────────────────┐
│ [search……………] [⚙ filters 3] │  ← q input + a button that opens the filter drawer (count = chips)
│ ▸ timeline (folded: 1 row)  │  ← TimelineView starts folded (.tl-folded exists already)
│ [● src][▦ bnd][△ sites][▣]⋯ │  ← layer toggles icon-only, horizontally scrollable; ⋯ = tools menu
│                             │
│            MAP              │
│                             │
├─────────────────────────────┤
│ ═══ 124 sources ▴           │  ← sidebar becomes a bottom sheet (peek / half / full)
└─────────────────────────────┘
```

- **Grid.** `App.appEl` writes the inline `grid-template-columns` only when `!narrow`:
  `styleAttr <-- sidebarWidth.signal.combineWith(Viewport.narrow).map(...)`. In narrow mode it
  writes nothing, and the CSS switches to `grid-template-columns:1fr` and
  `height:100dvh` (`dvh` avoids the jump from the iOS URL bar).
- **Sidebar as a bottom sheet.**
  - `.sidebar` becomes `position:fixed; left:0; right:0; bottom:0`, and its height follows a
    `sheet` state: `peek` 56 px, `half` 50dvh, `full` calc(100dvh − 48px).
  - A grab handle toggles the state, and a vertical pointer-drag snaps between the three states.
    This reuses the pointer-capture pattern of `sidebar-resize`.
  - The sheet state belongs in `UiState` as a `Persist.Local`, `Role.View` field, so a reload keeps it.
  - `.sidebar-resize` gets `display:none` in narrow mode.
  - `ResultsList.pick`, the cluster picker, should raise the sheet to `half`. Otherwise tapping a
    cluster shows nothing.
- **Filter bar as a drawer.** In narrow mode, `FilterBar` renders the `q` input plus a
  "filters (n)" button. The button opens the existing filter rows in a full-screen sheet with a
  "done" button. The chips stay visible under the search field. No filter logic changes, because
  everything already runs through `UiState`.
- **Toolbar.** The five `WindowToggle`s move into a "⋯" menu, a popover that lists them with their
  labels. On desktop the row stays where it is.
- **Layer toggles.** `.layer-label` is hidden in narrow mode (the icons carry a `title` and
  `aria-label` already). The row gets `overflow-x:auto; flex-wrap:nowrap`, and the base-map
  `<select>` moves into the ⋯ menu.
- **Timeline.** It starts folded in narrow mode, but only when the user has not unfolded it
  (`.tl-folded` already exists). Its pointer handling already works on touch. The preset dropdown
  (`min-width:280px`) needs `max-width:calc(100vw − 32px)`.
- **Windows become full-screen sheets.**
  - In narrow mode `WindowManager.renderWindow` ignores `x/y/w/h` and uses `inset:0` on the top
    window. It hides the drag and resize handles; the title bar keeps ✕, − and ↗.
  - The windows keep their stack (`z`), so ✕ goes back to the one below.
  - `.wm-taskbar` becomes a horizontal chip strip above the bottom sheet.
  - The windows' state and URL (`UiState.open`) do not change, so a link shared from desktop opens
    the same windows on a phone.
  - Separately, the title bar needs `touch-action:none` for dragging to work with a stylus or a
    touch laptop on desktop too. This is a desktop-invisible fix.
- **Touch and hover.**
  - `Preview` ignores `mouseover` from `pointerType == "touch"`.
  - In narrow mode, a tap on a `src-ref` opens the source window as it does now. That is the right
    default, because a preview card over a map is too cramped.
  - Leaflet site tooltips become tap-to-show (`bindTooltip` → `bindPopup` when narrow).
  - Tap targets are at least 40 px (`.layer-btn`, `.wm-btn`, `.chip-x`).
  - Inputs use `font-size:16px` in narrow mode, which stops iOS from zooming on focus.
- **Fixed-position odds and ends.**
  - `#status` moves to the top-right under the search field.
  - The Leaflet legend collapses to the ramp only (`.hd-legend .hd-legend-confidence{display:none}`).
  - The attribution stays.
- **Server-rendered pages** (`/source/…`, `/article/…`, opened in a new tab) need a separate pass.
  Check that `HttpApi`'s page shell emits the viewport meta, which the grep says it does. Add
  `max-width:100%` for figures and tables, and `overflow-x:auto` for wide tables.

**(c) What stays exactly the same on desktop.**
- The mobile rules sit behind `[data-layout=narrow]`, and the Scala changes are gated on
  `Viewport.narrow`, so desktop runs the same code paths as today.
- The only shared changes are these, and a reviewer should check each one:
  - `touch-action:none` on `.wm-titlebar`;
  - `Preview` ignoring touch-sourced `mouseover`;
  - the sources toggle (§2).

**(d) How to verify.**
- Chromium and Playwright are available in the dev container.
- Add a small script next to `scripts/bundle/smoke.sh` (or as a `just` recipe) that starts the
  server on port 0 against the `*_test` DB. It then screenshots `/` at **1440×900** and
  **390×844** (iPhone 14, `hasTouch:true`), plus `/#open=upload` at both sizes.
- Take the desktop screenshot **before** the change and diff it after. A pixel-identical desktop is
  the acceptance test for "keep the desktop functionality".
- The pure bits (the breakpoint function and the sheet-state codec) get munit tests in
  `modules/web/src/test`.
- New assets (none are needed if icons stay inline SVG) would fall under the tracing-agent rule:
  hit them once under the agent.

**(e) Effort and order.**
1. `Viewport` plus config key and README config row, then the grid fix.
2. The sidebar bottom sheet.
3. Windows as sheets.
4. The filter drawer and ⋯ menu.
5. Touch polish.

Each step can ship alone. Steps 1–3 alone make it usable. Roughly 400–600 lines of Scala and CSS in
total.

---

## 2. "Sources" toggle before "boundaries"

### What exists today

- The map buttons are `MapLayer.values`: `Boundaries | Sites | Media` (`Components.scala:54-57`).
  `ViewportToggle.scala:50` draws them in that order.
- Their on/off state is `UiState.layers: Field[Set[MapLayer]]`. It is `Persist.Local` with
  **default `Set.empty`**, so every overlay starts off (`UiState.scala:167`).
- `MapView` reconciles the state through `LayerSync` (`MapView.scala:45-47, 474-483`).
- The finding markers ("sources on the map") are **not** a `MapLayer`. They live in the
  `MarkerClusterGroup cluster`, which is added once at mount (`MapView.scala:162-194`) and redrawn
  by `MapView.render`. Nothing can hide it today.

### The catch: "on by default" and a `Set` that defaults to empty

Suppose `Sources` were added as a fourth `MapLayer` and the default became `Set(Sources)`. Every
browser that already has `histmersion.ui.layers` in localStorage would restore a set *without*
`Sources`, so the markers would vanish for every existing user. Two ways out:

- **A. A separate boolean field (recommended).**
  `val sources = Field[Boolean]("sources", "sources", true, Persist.Local, Role.View)`.
  - A missing key means *on*, which is the default semantics we want.
  - No migration is needed, and the layers codec is untouched.
  - The button is still a `LayerToggle(Icon.Sources, "sources", UiState.sources.signal, v => UiState.set(UiState.sources := v)())`,
    so it looks and behaves like its neighbours.
- **B. `MapLayer.Sources` with a one-time localStorage migration.** This works like the existing
  `LegacyLayersKey` migration at `UiState.scala:347-362`: add `Sources` to any stored set that
  predates a version marker.
  - It is more uniform (one set, one `LayerSync`).
  - But the cluster is not a `FadingLayer`, so `slot()` needs a special case anyway, and the
    migration is code that stays forever.

### Implementation sketch (option A)

1. `Components.scala`: add `Icon.Sources`, a filled dot plus a ring, matching the marker style.
2. `UiState.scala`: add the `sources` field and include it in `fields`. It is a local preference
   like `layers`, so it stays out of the URL hash and a shared link does not hide someone's markers.
3. `ViewportToggle.scala:50`: prepend the sources toggle so it comes **before** boundaries:
   ```scala
   LayerToggle(Icon.Sources, "sources", UiState.sources.signal, v => UiState.set(UiState.sources := v)()),
   MapLayer.values.toList.map(l => LayerToggle(...)),
   ```
4. `MapView.scala`, in the mount callback beside the `layers` subscription:
   ```scala
   UiState.sources.signal.foreach { on =>
     if on then { if !map.hasLayer(cluster) then cluster.addTo(map) }
     else
       map.removeLayer(cluster)
       if exitLayer != null then { map.removeLayer(exitLayer); exitLayer = null }
   }(using ctx.owner)
   ```
   Keep calling `render()` while the layer is hidden, so the cluster content is current when it is
   switched back on. `Leaflet.scala` may need a `hasLayer` facade.
5. Optionally, the timeline legend ramp (`.hd-legend`) could hide with the markers, since it only
   explains them. Keep the findings fetch in `App.load`: the sidebar counts (`findings`, `total`)
   depend on it.
6. `TimelinePlayback` forces `Boundaries` on during playback. The sources toggle should be left
   alone there.
7. Tests. `MapViewSuite` already covers `LayerSync`. Add a `UiStateSuite` case: with nothing
   stored, `sources` is `true`, and a stored `false` round-trips.

This is small, about 40 lines.

---

## 3. Upload pane: pick a book from the queue, tick it off after OCR

### What exists today

- **Prefill from an item exists, but only from the queue side.** "Upload…" on an acquisition row
  (`QueueView.scala:359`) calls `UploadView.prefill(QueueItemRef.uploadDraft(item))`.
  `QueueItemRef.parse` reads authors, title, year and DOI out of the item's Markdown line and
  suggests a citekey. The draft carries `forItem = Some(item)`.
- **The tick-off happens when the upload is *accepted*, not when the OCR finishes.** In
  `UploadView.submit`, the 202 from `POST /api/ingest` triggers `fulfilment(sent, ok)`, which sends
  `ApiClient.queuePatch(id, QueueView.fulfil(item, ck))`. That marks the item `Done` with the reason
  `… · ingested as [[ck]]`.
  - If the OCR job then fails, the item is already ticked off while the corpus has only a skeleton.
    This is what the request asks to change.
- **The OCR job's lifecycle is entirely server-side.**
  1. `IngestHandlers.ingestNow` spools the file.
  2. `enqueue` records the job in `jobs` and in the in-memory `pending` map (`IngestHandlers.Pending(params, file, sha)`).
  3. `runJob` moves the job `Queued → Running → Done|Failed` (`IngestHandlers.scala:226-248`).
- **An interrupted job is marked failed at startup** (`IngestHandlers.scala:37`). A pending upload
  therefore never outlives the process that accepted it.

### Recommended design: the server ticks the item off when the job succeeds

The tick-off could be done in the browser by watching `JobsView` until the job reaches `done`. That
approach is fragile: the jobs window polls only while it is open, and OCR of a scanned book takes
tens of minutes. Nobody keeps a phone tab open that long. It would also split "what an ingest does"
between client and server, and CLI or MCP uploads could never do the same.

Instead, follow AGENTS.md's "one implementation per operation": make the link part of the ingest.

**Contract (`core`).**
- `IngestParams` gains `acquireItem: Option[Int] = None`.
- `Api.ingestParams` gains `.and(query[Option[Int]]("acquireItem"))`.
- The sttp client (CLI) and the browser's `ApiClient.ingest` are both derived from that definition
  or follow it.
- `JobInfo` gains `acquireItem: Option[Int] = None`, so the jobs window can say "ticks off item 42
  when done".
- The pure fulfilment rule moves from `modules/web` into `core`: `QueueView.fulfilledReason` and
  `QueueView.fulfil` become `Queue.fulfilledReason` and `Queue.fulfil(item, citekey): QueuePatch`.
  Then the web UI's "Link existing source…" and the server's job use the **same** function. Its
  existing tests move to a core suite.

**Store (`db`).**
- Add the migration `V028__job_acquire_item.sql`:
  `ALTER TABLE jobs ADD COLUMN acquire_item integer;`. No FK is needed: an item can be deleted while
  the job runs, and that should not fail the job.
- `JobRepo.create` takes the item and writes it; `JobRepo.get` and `list` read it.
- The column is not strictly needed for correctness, because `Pending.params` already carries it in
  memory. It makes the link visible and auditable, and it survives into the jobs listing after a
  restart.

**Server (`server`).**
1. `ingestNow` validates the item before accepting the upload. The item must exist, be in the
   `acquire` list, and still be open. If not, it fails fast with a 400:
   "acquisition item 42 is already done".
   - `QueueRepo.get(ds, id)` already exists for this.
2. `enqueue` passes `acquireItem` to `JobRepo.create`, and `Pending.params` carries it.
3. In `runJob`, on the `Right((doc, n))` branch, *after* the transition to `Done`:
   ```scala
   job.params.acquireItem.foreach { id =>
     QueueRepo.get(ds, id).filter(_.status == Queue.Open) match
       case Some(item) =>
         QueueRepo.patch(ds, id, Queue.fulfil(item, job.params.citekey)) match
           case Left(e)  => scribe.warn(s"job $id done, but acquisition item $id was not ticked off: ${e.message}")
                            JobRepo.progress(ds, jobId, s"… · item $id NOT ticked off: ${e.message}")
           case Right(_) => JobRepo.progress(ds, jobId, s"${doc.pages} page(s), $n asset(s) · ticked off item $id")
       case None => () // deleted or already ticked off by hand meanwhile — nothing to do
   }
   ```
   - The source is stored before the tick-off is attempted, and a tick-off failure is reported in
     the job's progress text. It never turns a finished OCR into a failed job.
   - The `maintenance.guard` that protects `queuePatch` applies here too. `store()` already refuses
     during a restore, so the tick-off inherits that.
   - A job that ends `Failed` leaves the item open. That is the point of the change.
4. `McpTools.ingest` gains `acquireItem` in its input, passed through `fromTool`.
   `QueuedIngest.convert` (server-side DOI or archive acquisition) can take it too, for
   `histmersion ingest <doi> --acquire-item N`.
5. README: add the `acquireItem` parameter to the ingest REST row, the MCP `ingest` row, and the
   CLI table (regenerated with `just cli-docs`). Update the `/acquire` row (line 380), which now says
   the item is ticked off "once the upload's OCR succeeds".

**CLI (`cli`).** `IngestCmd` gains `--acquire-item <id>`. This is the one-operation, all-surfaces rule.

**Web (`modules/web`).**
1. **An item picker in the upload pane.** Add a new first row in `UploadView.pane`, above the drop
   zone:
   - A searchable select, "from the acquisition queue…", fed by
     `ApiClient.queueList("acquire", all = false)`.
   - Options are grouped by topic, reusing `QueueView.groups` and `QueueView.heading`. Each option
     label is the item's first line, truncated, and the list is filtered as the user types. It can
     be built from the existing `ChipInput` suggestion list or a plain `<input list=…>` with a
     `<datalist>`, as `QueueView` does for topics.
   - The list is reloaded when `QueueView.changed` emits `Acquire`, so it always shows what is still
     open.
2. **Choosing an item** applies `QueueItemRef.uploadDraft(item)`, but **merges** instead of
   replacing. This is a new pure function, `UploadView.withItem(d: Draft, item): Draft`, with a test:
   - it keeps the chosen file and `fileName`;
   - it fills citekey, title, authors, year and DOI from the item **only where the reader has not
     typed them yet**;
   - it sets `forItem`.

   `prefill` (the queue's "Upload…" button) keeps its current behaviour: it opens the window with
   that item already chosen. Picking a different item replaces `forItem`, and the existing
   "don't link it" link clears it.
3. `validate` puts `acquireItem = d.forItem.map(_.id)` into `IngestParams`.
4. **In `submit`, remove the client-side tick-off.** That means `fulfilment(...)` and its
   `queuePatch` call. The server does it now. The success note changes to "Uploaded — OCR job … is
   queued. Acquisition item 42 will be ticked off when it finishes."
5. `JobsView` shows "→ item 42" on jobs that carry `acquireItem`. When a tracked job reaches `done`,
   it emits `QueueView.changed.emit(Queue.Kind.Acquire)`, so an open "to acquire" window reloads
   and shows the tick.

**What "checked out" means.** This document takes it to mean the item's checkbox is ticked
(`status = Done`), with the reason `ingested as [[citekey]]`. That is the same state "Link existing
source…" produces today. If you want a distinct state instead, for example an item that is acquired
but not yet enriched, that would be a new `Queue` status and a wider change.

**Tests.**
- Core: `Queue.fulfil` and `fulfilledReason`, moved from the web suite.
- Web: `UploadView.withItem` merge rules and `validate` carrying `acquireItem`.
- DB suite (gated on `HISTMERSION_TEST_DB_URL`): `JobRepo` round-trips `acquire_item`.
- HTTP suite:
  - Ingest a tiny text file with `acquireItem` against a stub converter, wait for `done`, and assert
    the item is `Done` with `ingested as [[ck]]`.
  - The same with a failing converter: the item stays `Open`.
  - Ingest against a done or unknown item returns 400.
- Native image: no new dependency. The new migration is picked up by the generated
  `db/migration/index.txt`. Following the tracing-agent rule, run the server once against an empty
  DB so the agent records the new migration resource, and commit the changed
  `reachability-metadata.json`.

**Effort.** Roughly 250–350 lines across core, db, server, cli and web, plus tests. The ordering in
AGENTS.md ("Adding an operation", steps 1–7) applies as written.

---

## Suggested delivery

Three independent PRs, smallest first:

1. **Sources toggle** (§2). About 40 lines, no backend changes.
2. **Upload from the acquisition queue, ticked off on OCR success** (§3). A full-stack change with
   a migration.
3. **Mobile layout** (§1), in steps 1–3, then 4–5. Take the Playwright desktop baseline screenshots
   before the first commit.
