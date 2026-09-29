# Research: mobile web UI, a sources map toggle, uploading from the acquisition queue

**Status:** implemented on this branch (2026-09-29), in six commits: the sources toggle (§2), the
upload tick-off by the OCR job (§3), inline styles → CSS in two parts (§1 steps 1–3), and the narrow
layout (§1 step 4) in two. After review, the phone layout went further than step 4 below: the map
alone is on screen, and filters (left drawer), map layers and tools (right drawer, replacing the "⋯"
menu) and the timeline come on demand, by button or a swipe in from the edge; all sizes derive from
screen-relative tokens; a phone held sideways is narrow too (`ui.narrow-max-height-px`). The text
below is the design as first agreed; where the code differs, the code and AGENTS.md win.
Reviewed on 2026-09-29:
- §1: move all inline styling to CSS first, then build the mobile layout on top of it.
- §2: accepted as written.
- §3: accepted. The tick-off is done by the async job, and only when both the OCR and the source
  write succeed. It works from the web UI, MCP and the CLI alike.
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

## 1. Mobile-friendly web UI, by moving styling out of Scala and into CSS

**Decision (review, 2026-09-29).** Every hardcoded style inlined in the Scala sources moves into
`style.css`, behind classes. The layout can then be adjusted with media queries, and the theme can
be swapped by changing CSS alone. The mobile layout is built **on top of** that refactor, not
alongside it.

### What exists today (first pass)

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

### Step 1: inventory of inline styling today

A grep of `modules/web/src/main` and `modules/core` finds about 45 sites where Scala decides how
something looks. They fall into four groups, and each group needs a different treatment.

**A. Static or state-driven styling. Replace it with a class.** Nothing dynamic is involved here:
the Scala side only picks between fixed looks.

| site | today | becomes |
|---|---|---|
| `App.scala:118-130` | `display <-- tab.map(if … "" else "none")` for the four sidebar panes | `cls("hidden") <-- …` (or `cls("active")` + `.pane:not(.active){display:none}`) |
| `TreeTab.scala:166` | `display <-- expanded.map(if _ "block" else "none")` | `cls("expanded") <-- expanded` |
| `TimelinePlayback.scala:52` | `display <--` for the playhead | `cls("hidden") <-- …` |
| `WindowManager.scala:206` | `display <-- minimized.map(...)` | `cls("minimized") <-- …` |
| `ArticleModal.scala:61-67` | lightbox built with `style.cssText = "position:fixed;…rgba(0,0,0,.82)…"` | `.lightbox`, `.lightbox img` in CSS |
| `GraphPanel.scala:198` | `canvas.style.cursor = if hover … "pointer"` | `cls("hovering")` on the canvas |
| `MapView.scala:128` | the marker ring's `box-shadow:… rgba(255,255,255,.2) …` in the HTML string | `.hd-dot.ring-1 … .ring-4` classes, since the width only takes the values 1–4 |
| `Components.scala` `Icon.apply` | `svg.fill := "none"`, `stroke := "currentColor"`, `strokeWidth := "2"`, … | `.icon { fill:none; stroke:currentColor; stroke-width:2; … }`. Keep `viewBox`; drop the literal `width`/`height` in favour of `.icon{width:14px;height:14px}` |

**B. Data-driven values: geometry, and colours computed from data.** These cannot be classes, because
the value comes from data or from user input. The rule is **set a CSS custom property inline, and
let a class consume it**. The inline style then carries a *number*, never a *style*, so a media query
or a theme can still override how the number is used.

| site | the value | inline becomes | CSS |
|---|---|---|---|
| `App.scala:36` | sidebar width (user-dragged) | `--sidebar-w: 360px` | `.histmersion-app{grid-template-columns:1fr var(--sidebar-w,360px)}`. A narrow-mode rule then simply overrides `grid-template-columns`. **This is the fix for the blocker found in the first pass.** |
| `WindowManager.scala:207-211` | window `x/y/w/h/z` | `--wx --wy --ww --wh --wz` | `.wm-window{left:var(--wx);…}`. Narrow mode uses `inset:0` and ignores them |
| `FilterBar.scala:202-224` | year-slider handle positions (%) | `--from-pct`, `--to-pct` | `.year-range-fill{left:var(--from-pct);width:calc(var(--to-pct) - var(--from-pct))}` |
| `TreeTab.scala:130,185,201,208` | `--tdepth` | already a custom property: **the target pattern, no change** | — |
| `MapView.scala:133` | marker diameter, font size, background gradient | `--d`, `--c1`, `--c2` | `.hd-dot{width:var(--d);height:var(--d);line-height:calc(var(--d) - 1px);background:linear-gradient(135deg,var(--c1),var(--c2))}` |
| `MapView.scala:215,218`, `:421` | legend ramp colours, confidence pips, media dot colour | `--c` | `.hd-ramp i{background:var(--c)}` |
| `Preview.scala:145-151` | tooltip position, computed from the anchor's rect | `--px --py --pmaxw` | `.cite-tooltip{left:var(--px);…}` |
| `GraphPanel.scala:149-150` | canvas CSS size (DPR maths) | stays as it is | this is canvas plumbing, not styling |

**C. Palettes: the colour *choices*.** `ui/Colors.scala` holds 31 hex literals: `EdgeColors`,
`StatusColors`, `MediaKindColors`, `OverlayColors`, `ViridisStops`, and the `GraphLayout` label and
hover colours. There are four more in `PlacesInput.scala:180,194,387,401` (the highlight colours
`#c2410c` and `#0ea5e9`) and one in `MapView.scala:218` (`rgb(33,145,140)`). For theming, these must
live in CSS, and the code refers to them by **name**:

```css
:root {
  --edge-references:#6ea8fe; --edge-refutes:#e06660; …          /* one per EdgeKind wire name */
  --status-foundational:#f0c040; --status-current:#6ea8fe; …   /* one per status */
  --media-figure:#6ea8fe; …
  --boundary:#f0c040; --boundary-fill-opacity:.08; …
  --ramp-0:rgb(68,1,84); … --ramp-4:rgb(253,231,37);            /* viridis stops */
  --place-pick:#c2410c; --place-hover:#0ea5e9;
}
```

The Scala side splits by what can consume a class:

- **HTML and DOM** (legend dots, badges): emit a class from the wire name, for example
  `span(cls := s"status-dot status-$wire")` with `.status-dot.status-current{background:var(--status-current)}`.
  Alternatively, emit `--c: var(--status-current)` inline. The Scala side no longer holds the hex
  value in either case.
- **Leaflet vector layers** (boundaries, sites, place highlights): every path option takes a
  `className`, and **CSS beats SVG presentation attributes**. So pass
  `className = s"hd-polity"` / `s"hd-site kind-$k"`, *omit* `color`/`fillColor`, and style
  `.hd-polity{stroke:var(--boundary);fill:var(--boundary);fill-opacity:var(--boundary-fill-opacity)}`.
  This works because the map uses Leaflet's SVG renderer, the default. Do not switch it to
  `preferCanvas`.
- **Data-interpolated colours** (`Fmt.color(t)`, the viridis ramp by period midpoint, and
  `Fmt.desaturate` by confidence): the value is computed per marker, so it has to come from code.
  But the **stops** come from CSS. At start-up (and again when the theme changes), a small
  `ThemeTokens.read()` reads `getComputedStyle(document.documentElement).getPropertyValue("--ramp-0")`
  and the other stops, and feeds them to `Fmt`. `Fmt.color` stays a pure function of `(stops, t)`,
  and its tests pass the stops explicitly.
- **Canvas** (`GraphPanel`: node colours by status, edge colours by kind, labels, hover stroke):
  canvas cannot see CSS, so it reads the same tokens through `ThemeTokens` at draw time. The five
  `fillStyle`/`strokeStyle` sites take a `Tokens` value instead of `EC(...)`/`SC(...)`.

The same palette also appears in **server-rendered HTML**: `core/Style.scala:95`
`confidenceBadge` emits `style="color:${ConfidenceStyle.color(c)}"`. It already emits the class
`conf-$wire`, so the fix is to drop the `style` attribute and add `.conf-badge.conf-high{color:var(--conf-high)}`
and the other levels. This is a one-line change in `core` and one CSS block.

**D. What is left in Scala after the move.**
- `ui/Theme.scala` (`bg`, `panel`, `ink`, …) turns out to be **dead code**: nothing outside
  `ThemeSuite` reads it, and it duplicates `:root`. Delete it and its test.
- `ui/Colors.scala` shrinks to the **names**: which statuses, edge kinds and media kinds exist.
  They map to CSS class and variable names, never to hex values.
- `ui/Layout.scala` keeps only behavioural numbers: the sidebar min and max for clamping a drag,
  `pickerMax`, fetch limits, animation timings that JS needs.
  - Anything that is only a *look* goes to CSS: `mediaDotSize`, `markerRadiusDefault` if only CSS
    uses it, `windowMaxWidth/Height` defaults.
  - The window defaults can become CSS variables that `WindowManager` reads through `ThemeTokens`
    when it places a new window.

### Step 2: guard it, so it stays moved

Two new lint tests keep inline styling from creeping back, in the spirit of the existing lint test
that greps `src/main` for `:8000|5432|8100|unpkg`:

- **`NoInlineStyleSuite`** (in `modules/web/src/test`) greps `modules/web/src/main` and
  `modules/core/shared/src/main` and fails on any of these:
  - `styleAttr :=` / `styleAttr <--` whose value is not purely custom properties
    (`^--[a-z-]+:` segments only);
  - Laminar style props (`display|left|top|width|height|color|background… <--/:=`) outside an
    allow-list of `svg.` attributes;
  - `.style.` / `style.cssText` / `setProperty(` writes, except `setProperty("--…")`;
  - `style="` in HTML string literals, except custom properties;
  - hex (`#[0-9a-fA-F]{3,8}`) or `rgb(` literals in any `.scala` file outside tests.

  Each exception lives in the suite with a one-line reason (e.g. `GraphPanel` canvas DPR sizing).
- **`CssTokensSuite`** checks that every palette *name* the Scala code can emit has a matching
  custom property in `style.css`:
  - every `EdgeKind` wire name has `--edge-<wire>`;
  - every status has `--status-<s>`;
  - and so on, including all five ramp stops.

  The test reads `style.css` from the server module's resources, which `WebAssetsSuite` already
  does. Without this check, a missing variable renders transparent and nothing fails.

### Step 3: theme swapping

With every colour in `:root`, a theme is a block of variable overrides:

```css
:root[data-theme="light"] { --bg:#f7f7f9; --panel:#fff; --ink:#1b1e27; …; --ramp-0:… }
@media (prefers-color-scheme: light) { :root:not([data-theme]) { … } }  /* only if we want auto */
```

- A theme picker, if one is wanted later, is one `UiState` field (`Persist.Local`) that sets
  `data-theme` and calls `ThemeTokens.read()` again. Then the map markers and the graph redraw with
  the new ramp.
- The Leaflet base tiles are a separate choice, the existing `baseMap` select, and stay separate.
- Leaflet's own CSS and `MarkerCluster.Default.css` are vendored. Their colours are overridden in
  `style.css` where they show, not edited in `vendor/`.
- Dark stays the only shipped theme until someone asks for another. The refactor only makes a
  second one cheap.

### Step 4: the mobile layout, now in CSS

With the refactor in place, the narrow layout is **CSS only, plus a few behaviour switches in Scala**:

- **Breakpoint.**
  - AGENTS.md forbids constants in code, and `@media` cannot read config. So the Scala side sets
    `data-layout="narrow"` on `.histmersion-app` from `matchMedia(s"(max-width: ${ui.narrowMaxWidthPx}px)")`,
    with a new config key `histmersion.ui.narrow-max-width-px = 760` and
    `${?HISTMERSION_UI_NARROW_MAX_WIDTH_PX}`, plus its README config row.
  - The CSS keys every mobile rule off `[data-layout=narrow]`. Desktop never gets the attribute, so
    **no mobile rule can reach desktop**.
  - The pure `Viewport.mode(width, breakpoint)` gets a munit test.
- **Layout in narrow mode.** A single column with the map primary:

  ```
  ┌─────────────────────────────┐
  │ [search……………] [⚙ filters 3] │  ← q input + a button that opens the filter drawer (count = chips)
  │ ▸ timeline (folded: 1 row)  │  ← starts folded (.tl-folded exists already)
  │ [● src][▦ bnd][△ sites][▣]⋯ │  ← toggles icon-only, horizontally scrollable; ⋯ = tools menu
  │                             │
  │            MAP              │
  │                             │
  ├─────────────────────────────┤
  │ ═══ 124 sources ▴           │  ← sidebar becomes a bottom sheet (peek / half / full)
  └─────────────────────────────┘
  ```

  - **Grid:** `.histmersion-app[data-layout=narrow]{grid-template-columns:1fr;height:100dvh}`.
    This overrides `var(--sidebar-w)` with no Scala change, now that the width is a variable.
  - **Sidebar as a bottom sheet:**
    - `position:fixed; bottom:0`, with a height from a class: `sheet-peek`, `sheet-half` or
      `sheet-full`.
    - The sheet state is a `UiState` field (`Persist.Local`, `Role.View`), with a grab handle and a
      vertical pointer-drag that snaps between the three states.
    - `.sidebar-resize{display:none}`.
    - `ResultsList.pick` raises the sheet to half, so tapping a cluster shows its list.
  - **Filter bar:** in narrow mode, the filter rows collapse into a drawer opened by a
    "filters (n)" button, and the chips stay visible. No filter logic changes, because it all runs
    through `UiState`.
  - **Toolbar:** the five `WindowToggle`s and the base-map `<select>` move into a "⋯" popover.
    `.layer-label{display:none}` leaves the layer toggles icon-only (they already carry
    `title`/`aria-label`), in a horizontally scrolling row.
  - **Timeline:** it starts folded in narrow mode. `.tl-presets-dropdown{max-width:calc(100vw - 32px)}`.
  - **Windows as sheets:** `.histmersion-app[data-layout=narrow] .wm-window{inset:0}`, which ignores
    `--wx…` and hides the drag and resize handles. The stack (`--wz`) and `UiState.open` stay as they
    are, so a desktop link opens the same windows on a phone. `.wm-taskbar` becomes a chip strip
    above the sheet.
  - **Touch:**
    - `Preview` ignores `mouseover` whose `pointerType` is touch.
    - Site tooltips become tap popups in narrow mode.
    - Tap targets are at least 40 px.
    - Inputs use `font-size:16px`, which stops iOS from zooming on focus.
    - `.wm-titlebar{touch-action:none}`, which also fixes stylus drag on desktop.
  - **Fixed odds and ends:** `#status` moves under the search field, and the map legend collapses
    to the ramp only.
  - **Server-rendered pages** (`/source`, `/article` in a new tab): `max-width:100%` for figures,
    and `overflow-x:auto` for wide tables.

### Verifying that desktop is unchanged

The refactor (steps 1–3) must be **pixel-identical on desktop**. That is its whole acceptance
criterion, and it is testable:

- Before the first commit, a Playwright script (Chromium is in the dev container) starts the server
  on port 0 against the `*_test` DB with a small fixture set, and screenshots a fixed list of states
  at 1440×900:
  - `/` with the sidebar dragged to 480 px;
  - each sidebar tab;
  - boundaries, sites and media on;
  - a cluster picked;
  - the graph window and upload window open;
  - an article with a lightbox;
  - a hover preview.
- After each refactor commit, the same script diffs against that baseline. The graph canvas uses a
  force layout, so it needs a fixed seed or a masked region.
- The mobile step adds 390×844 (`hasTouch:true`) shots of the same states as a *new* baseline, and
  the desktop shots must still match.

### Order and size

1. **Groups A and B** (classes and custom properties) plus `NoInlineStyleSuite` in warn-only mode.
   About 200 lines, pixel-identical.
2. **Group C** (palettes into `:root`, `ThemeTokens`, Leaflet `className`, canvas via tokens,
   `Style.confidenceBadge`), plus `CssTokensSuite`. Delete `Theme.scala`. Switch the lint to
   failing. About 250 lines, pixel-identical.
3. **Narrow layout:** the breakpoint config and grid, then the bottom sheet, then windows as sheets,
   then the drawer and ⋯ menu, then touch polish. About 300–450 lines, mostly CSS.

Steps 1 and 2 are worth doing even if the mobile work stopped there, because they are what makes a
second theme a CSS-only change.

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

**Decision (review):** accepted; option A.

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

### Design (accepted): the async job ticks the item off when it succeeds

"Success" means the job reached `Done`, which in `runJob` happens only after **both** of these:
- `converter.convert` returned a document;
- `store()` wrote the body and assets into the source (`Running → Done` follows
  `outcome = Right(...)`).

So the tick-off hooks onto exactly that transition, and nothing earlier:
- A failed conversion, a failed store, a restore in progress, or a process restart
  (`Interrupted`) all leave the item open.
- Every surface (web upload, `histmersion ingest --acquire-item`, the MCP `ingest` tool's
  `acquireItem`, and server-side acquisition via `QueuedIngest`) goes through
  `IngestHandlers.ingest`. The one hook therefore covers all of them.

Why not do it in the browser:

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

Independent PRs, smallest first:

1. **Sources toggle** (§2). About 40 lines, no backend changes.
2. **Upload from the acquisition queue, ticked off by the job** (§3). A full-stack change with a
   migration, covering web, REST, MCP and CLI.
3. **Inline styles to CSS, part 1:** groups A and B, custom properties, and the lint in warn-only
   mode (§1, step 1). Take the Playwright desktop baseline screenshots before this PR's first
   commit.
4. **Inline styles to CSS, part 2:** palettes as tokens, `ThemeTokens`, delete `Theme.scala`, and
   turn the lint on (§1, steps 1C–3).
5. **Mobile layout** (§1, step 4). Split it into smaller PRs if it grows.

PRs 3 and 4 must be pixel-identical on desktop. PR 5 must leave the desktop baseline unchanged.
PR 1 touches the layer-toggle row, so it is easiest to land before PR 3 moves that row's styling.
