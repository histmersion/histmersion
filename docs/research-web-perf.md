# Web UI speed, memory and leaks, in the browser (#1949, 2026-10-17)

`research-interactive-performance.md` measured the server's side of every interaction. This note
measures the other half — what the page does between an input and the next paint, and what it
holds — in Chromium, for every interactive path; records the baseline on the live corpus; lists the
leaks and slow spots found and what was changed; and describes the budgets and the soak test that
now fail the browser suite when one comes back.

## How it is measured

All of it is in `modules/web/test/` (Node + Playwright, test-only; nothing enters the Scala build):

| file | what |
|---|---|
| `perf-lib.mjs` | the in-page probe (installed before the app): long tasks (`PerformanceObserver`), every input's latency to the next paint (the INP model: from the event's `timeStamp` to a message posted from the next animation frame), frame intervals while a gesture runs ("dropped" = an interval over 1.5 refresh periods), "until this panel changes" (a `MutationObserver` armed before the input); memory after two forced GCs: JS heap (CDP `Performance.getMetrics`), DOM nodes and listeners (`Memory.getDOMCounters`), the Leaflet map's layers, and the app's caches (`window.histmersionDiagnostics()`). Every page it opens refuses a non-GET request, so a measurement can never write. |
| `perf-paths.mjs` | the paths: first render; map pan (40 × 12 px drag + 4 wheel zooms); timeline drag (60 moves along the overview strip, then the release → the list's next render); hover cards (6 source links: card shown, content in, cached); filter typing (10 keystrokes' echo, the last → list, a chip filter's suggestions, a hovered suggestion's preview → list); article open (click → window, → content); the boundaries over a whole range ("All states in period" −3000…2000: shapes drawn, load long tasks, then a drag); the events layer at a world view (markers drawn, then a pan). |
| `perf-baseline.mjs` | runs every path N times against `HISTMERSION_SERVER_URL` and prints the medians (`--md` for these tables, `--soak N`). `HISTMERSION_PERF_BUNDLE=<main.js>` serves a locally built bundle instead of the server's, so a change is measured on the live corpus without deploying it. |
| `perf-fixtures.mjs` | live-sized synthetic feeds for the test server (whose database is empty): 500 findings, 20 sources and their cards, 2 400 events, 140 polities for a year and 1 600 (120 vertices each) for a range, 30 articles, a source page that links to itself. Fixed seed; every name starts with "Zz". |
| `perf-soak.mjs` | one round = About and Jobs windows opened and closed, a source window followed to itself (its page reloaded in place), every sidebar tab, a short span drag, every layer on and off, a card hovered; N rounds after 5 warm-up rounds; memory before and after. |
| `perf.test.mjs` | the budgets and the soak test, in `npm run test:browser` (README of `modules/web/test`). |

```bash
HISTMERSION_SERVER_URL=http://localhost:8000 node modules/web/test/perf-baseline.mjs --runs 3       # the live corpus, read-only
HISTMERSION_PERF_BUNDLE=target/out/sjs1/scala-3.8.0/histmersion-web/histmersion-web-opt/main.js \
  HISTMERSION_SERVER_URL=http://localhost:8000 node modules/web/test/perf-baseline.mjs --runs 3       # a branch's bundle on it
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 HISTMERSION_PERF_REPORT=1 npm --prefix modules/web run test:perf
```

A measurement pitfall worth knowing: an element handle Playwright returns (`waitForSelector`) keeps
its element alive in the page — the first soak run showed 3 DOM nodes "leaked" per hover, every one a
detached hover card retained by the DevTools protocol's global handles, not by the app. The perf
code waits with locators and disposes every handle.

## Baseline: the live corpus (2026-10-17)

Headless Chromium 1243 on the development Mac (M-series), the live server at `:8000` (383 sources,
502 listed, 500 findings in the default span, 2 000+ events, 1 617 polities over −3000…2000), median
of 3 runs. *Before* is the bundle the server served (master, `fastLinkJS`, 31 MB); *after* is this
branch's (`fullLinkJS`, 18 MB) served to the same page through `HISTMERSION_PERF_BUNDLE`. Times in ms.

| path | figure | before | after |
|---|---|---|---|
| first render | first paint · map · first markers · list | 340 · 335 · 427 · 434 | see note ¹ |
| map pan | input → paint p95 / max · dropped frames · long tasks | 20 / 49 · 0 % · 0 | 19 / 47 · 0 % · 0 |
| timeline drag | input → paint p95 / max · dropped · release → list | 38 / 48 · 0 % · 164 | 40 / 57 · 0 % · 163 |
| hover cards | card shown · content (cold) · cached | 19 · 21 · 21 | 19 · 81 ² · 21 |
| filter typing | keystroke → paint p95 · last key → list · suggestions · hovered suggestion → list | 14 · 82 · 173 ³ · 13 | 18 · 77 · 177 ³ · 11 |
| article open | click → window · → content | 19 · 416 | 18 · 894 ² |
| range boundaries (1 617 shapes) | load: longest task · all long tasks | **904 · 1 073** | **143 · 316** |
| | drag: input → paint p95 / max · longest task · dropped · frame max | 61 / **400** · **321** · 9 % · **367** | 66 / 151 · 74 · 11 % · 100 |
| | release → list | 521 | 313 |
| events layer (2 125 markers) | drawn · pan input → paint p95 · dropped | 834 · 32 · 1.2 % | 735 · 35 · 0 % |

Memory (after two GCs):

| state | JS heap MB · DOM nodes · listeners · Leaflet layers | before | after |
|---|---|---|---|
| after load | | 30.5 · 10 091 · 863 · 139 | **26.6** · 10 091 · 863 · 139 |
| after the session (pan, drag, hovers, typing, article) | | 32.2 · 10 546 · 902 · 139 | 28.0 · 10 546 · 902 · 139 |
| whole-range boundaries, after a drag | | **164.6** · 18 988 · 8 002 · 5 254 | **105.5** · 17 675 · 7 865 · 5 251 |
| events layer | | 39.8 · 11 347 · 835 · 168 | 35.7 · 11 985 · 920 · 184 |

¹ Not comparable here: the override serves the bundle through the DevTools protocol, slower than the
server. With both bundles served the same way (the override), map-ready was 560 ms (fast-linked) vs
351 ms (full-linked); on the test server, which serves the full-linked bundle itself, it is 277 ms.
² The live server was busy (a deploy was being prepared): the same figures swung 226–573 ms across
single runs of the *before* bundle; the card and the page come from the server (the article's
fragment is rendered there). Neither path's client code got slower — `fetchHtml` now decodes natively.
³ 150 ms of it is the configured `ui.debounce.suggest-ms`: a suggestion list cannot appear within
100 ms of a keystroke while that debounce is 150 ms. The keystroke's own echo is ~15–20 ms.

Every hover and typing path is inside the ≤ 100 ms rule for what the page itself does (the echo, the
card's first paint, a hovered suggestion's preview of the list). Over it, before this change: the
whole-range boundaries (a 0.9 s task on load, 0.3–0.4 s tasks during a drag).

## What was found, and what changed

| # | finding | fix |
|---|---|---|
| 1 | **A window's page reloaded in place doubled its click handlers each time** — `WindowManager.loadContent` re-wired `wireLinks` + `InPage.wire` on the same content root at every load, and every handler of a click on a self-link loaded the page again: listeners 4 → 8 → 16 → 32 → 64 over five in-place navigations, and 2ⁿ fetches per click (measured on master: 1 284 → 1 408 listeners) | wired once per root (`data-wired`), `WindowManager.scala` `loadContent` |
| 2 | the place-GeoJSON cache was bounded by count only (48) and FIFO — a whole range's answer is ~7 MB, so 48 could hold hundreds of MB | `BoundedCache` (LRU, count **and** size: 48 entries, 24 M characters), weighed when the answer arrives, `MapView.geoCache` |
| 3 | hover-card caches (source, article, place, event), `IdentLink` lookups and gazetteer labels grew without limit | `BoundedCache` of 300 / 1 000 / 5 000 (`Layout.cardCacheSize`, `identCacheSize`, `placeLabelCacheSize`); the label-shape cache (2 000) moved to it too |
| 4 | the whole-range boundaries followed a timeline drag through the 200 ms throttle: every step a new 7 MB answer, parsed and diffed against 1 600 shapes — 0.3–0.4 s tasks mid-drag, and a queue at the server | they wait for the span to settle (`Debounce`, `Layout.rangeReloadMs` = 250); snapshot years and playback follow the drag as before (`MapView.waitsForQuiet`) |
| 5 | placing 1 600 new area names (polylabel) ran in the load's one task (~300 ms of the 900) | probes read flat `Array[Double]` rings instead of `Vector[Pt]` (½ the time), and the names are placed in 12 ms slices between the page's other work, named together at the end (`MapLabels.syncAreas`) |
| 6 | Leaflet keeps each drawn feature, and so the parsed outline beside its own projected copy (~440 000 vertices over a range) | the outline is dropped once its name is placed (`MapView.dropGeometry`): −59 MB of the range's 165 |
| 7 | sttp's fetch backend decodes a body byte by byte in Scala.js (tens of ms per MB on the main thread) | the place GeoJSON and the window pages are read with `Response.text()` (`ApiClient.text`, `fetchText`); the request is still the Tapir endpoint's |
| 8 | the server shipped `fastLinkJS` output: 31 MB, a null check, cast check and bounds check on every access | it ships `fullLinkJS` (18 MB): −12 % heap at load, the range load 900 → 580 ms before the other fixes (`build.sbt`, the server's resource generator) |
| 9 | the page's first load ran twice (the state subscription fired at once and scheduled a second, identical load: 300 KB of findings, the list drawn twice), `/api/config` was asked for twice, and the detail strip twice | `App.init` records the first load's state before subscribing and asks for the config once; `TimelineView.loadDetail` cancels the scheduled one |
| 10 | the overlays switched on from the browser's storage loaded the placeholder span (0, 1) before the first findings arrived | `MapView.init` starts from the restored span |
| 11 | a reload of the same events after a pan re-built every marker's icon and every cluster bubble | `FadingLayer.sync(changed = …)`; a point layer skips a marker that already shows the same item for the same span (`PointLayer.same`) |
| 12 | listeners and observers that outlived their element: the period-preset menu's and the windows' `document` listeners (added on mount, never removed), the timeline strips' `ResizeObserver`s, the in-view toggle's `moveend`/`zoomend` handlers on the map | Laminar `documentEvents` binders; observers disconnected and the detail timer cleared on unmount; the map handlers removed on unmount (`TimelineView`, `ArticleModal`, `ViewportToggle`) |

Audited and found clean: `PolledList` (cancels its poll and drops a late answer on close — now a
browser test), `RunsView`/`ManagersView` timers, `GraphPanel` (observer and animation frame stopped
on unmount), `IconSelect` (its outside-click listener goes on close and unmount), `Preview` (one card,
its Laminar root unmounted and observer disconnected on hide), `FadingLayer` fade-outs (one timer
each, removing the layer). `Notices`' poll and the app-level listeners (`Preview`, `MobileChrome`,
`UiState`, the map's) are installed once for the page's life. `MergeView`'s apply job is followed
until it ends even with its window closed — by design, the job is the page's.

## Budgets (as built)

`perf.test.mjs` holds every path to a budget, measured on the test server with the fixtures; the
budget is the measured value with headroom (≥ 2×, or the ≤ 100 ms rule where that is tighter), so a
regression fails and a busy machine does not. Measured on the test server (JVM server, full-linked
bundle, fixtures), with the budget after the slash:

| path | figures (measured / budget) |
|---|---|
| first render | map 277 / 1 500 · markers 330 / 2 000 · list 357 / 2 000 · longest task 0 / 200 |
| after load | heap 26.3 MB / 45 · listeners 836 / 1 200 |
| map pan | input p95 31 / 100 · longest task 0 / 150 · dropped 0 % / 15 |
| timeline drag | input p95 35 / 100 · longest task 0 / 150 · dropped 0 % / 15 · release → list 165 / 600 |
| hover cards | shown 19 / 100 · content 18 / 100 · cached 20 / 100 |
| filter typing | keystroke p95 19 / 100, max 19 / 150 · suggestions 161 / 400 · hovered suggestion → list 13 / 250 |
| article open | window 10 / 100 · content 53 / 300 |
| range boundaries (1 600 × 120 vertices) | drawn 906 / 3 000 · load longest task 82 / 200 · drag input p95 17 / 100 · dropped 2 % / 15 · heap 64.5 MB / 110 · the GeoJSON cache inside its ceilings |
| events layer (2 400 + 500 findings) | drawn 581 / 2 500 · longest task 59 / 200 · pan input p95 32–124 / 300 · pan longest task 54–87 / 250 · dropped 6 % / 25 · heap 46 MB / 75 — tightened by #1647, below |
| cluster zoom, 1920×1080 (events by view: ~1 150 held, ~330 drawn) | every zoom animated (0 not / 0) · longest task 0 / 50 · dropped 0.8 % / 15 — #4291, below |
| every layer on (#4718's full load), 1920×1080 · 390×844 | longest task 0 / 50 · dropped 1.1 / 15 % · 0 / 15 % (Chromium); Firefox: within 20 ms of the same zooms held still — below |
| soak (100 rounds, 132 s) | heap +0.9 MB / 6 · DOM nodes +0 / 150 · listeners +0 / 20 · Leaflet layers +0 / 0 · every cache inside its ceiling |

The events layer's figures are the median of three runs of each step — the draw (a reload each), the
pan, the zooms (#2130, 2026-10-23): single runs under load had drawn in 2 540 ms against 2 500 and
dropped 8.1 % of a zoom's frames against 8, while a regression slows every run.

Two more tests: every cache `histmersionDiagnostics()` reports has a ceiling and is inside it; a
closed Jobs window makes no further request, and a window's page followed to itself six times keeps
one set of handlers and fetches once per click (this one fails on master, finding 1). The range test
fails on master too (its load's longest task is 440 ms on the fixtures).

## Remaining

- ~~**Zooming among ~3 000 visible markers** costs 50–90 ms tasks~~ — done in #1647 (below): the
  group's own two method sets are swapped at a zoom's start, with no second group and no patch.
- **A drag with 1 600 polygons on the map** still drops ~10 % of frames on the live corpus (frame p95
  33 ms): the per-frame JS is small; the rest is painting ~2 000 SVG paths and ~1 900 name markers in
  headless (software) rendering. A canvas renderer would cut it but loses the per-shape focus and
  keyboard access the boundaries have.
- **The suggestion lists** are bound by `ui.debounce.suggest-ms` (150 ms): over the 100 ms rule by
  configuration, not by work.
- **Bundle size**: 18 MB unminified; ~11 MB of it is `core`'s derived codecs (the Kindlings decoders
  are ~40 KB each). `withMinify(true)` did not change the output under sbt 2 here; minifying, or
  trimming the codecs the web UI never decodes, is a separate change.

## The events layer: clustering and the zoom animation (#1647, 2026-10-17)

The decision: the events are **clustered**, as every point is, in the map's one cluster group (they
joined it with the mixed clusters, b0905fdf) — not thinned by rank and not a second clustering library.
What #1647 adds is that an events bubble reads as events (`BubbleForm`: a rounded square for events
only, a square tab where events are among other kinds; docs/visualization-guide.md §2a) and that the
group stops animating its zooms among thousands of points (`ClusterMotion`, `Layout.clusterAnimateMax`
= 1 000). A click still lists a small bubble (≤ 15) and zooms into a large one; at the last zoom
coincident events are listed, not spiderfied.

**Why the animation was the cost.** The LoAF trace of the events path: every wheel step ran
`_onZoomTransitionEnd` → markercluster's `_mergeSplitClusters` → `_animationZoomIn/Out`, 36–58 ms of
script with 15–26 ms of it a forced layout (`_forceLayout`), each new icon drawn under its old bubble
and again at its place. And the plugin leaves its last animation's closing step (`_animationEnd`) in
its queue until the NEXT zoom flushes it — while that counter is non-zero its `moveend` handler does
nothing, so after a zoom a pan neither adds the markers coming into view nor drops those leaving it:
the page held 1 019 marker icons and 18 600 DOM nodes instead of 392 and 7 500. Without the animation
both go: `_noAnimation`'s zoom redraws at once, and the queue is flushed at the start of each zoom.

`animate` is read once, when the group is made (`L.extend(this, animate ? _withAnimation : _noAnimation)`),
so `ClusterMotion.follow` does the same `L.extend` with the other set as a zoom starts (the map's
`zoomstart`, before the group's own `zoomend` handler) whenever the group's size crosses the limit.
Switching with a closing step still queued is safe: that step is `_animationEnd`, which both sets
share, and the next zoom flushes it first. (Waiting until the group is idle does not work — it stays
"in an animation" until the next zoom.)

Measured on the test server (fixtures: 2 400 events + 500 findings at a world view; JVM server,
full-linked bundle; medians of 8 runs, the same server, *before* served through
`HISTMERSION_PERF_BUNDLE`):

| figure | before | after |
|---|---|---|
| pan (40 drags + 4 wheel zooms): input → paint p95 / max | 105 / 147–157 ms | 75–79 / 85 ms |
| pan: dropped frames | 6.5–7.5 % | 1.8 % |
| 6 wheel zooms: longest task · all long tasks | 54–82 · 54–89 ms | 0 · 0 ms (one run 59–68) |
| 6 wheel zooms: dropped frames · frame p95 / max | 10.4–12.5 % · 33 / 83 ms | 2.7 % · 17 / 50 ms |
| after the session: DOM nodes · listeners · Leaflet layers | 18 629 · 2 717 · 1 024–1 275 | 7 503 · 984 · 397 |
| drawn · load's longest task · heap | 860–930 ms · 57 ms · 46.5 MB | 864–879 ms · 54 ms · 45.7 MB |

On the live corpus (`:8000`, read-only, −12000…2026, 2 125 events, 3 runs × 2): zoom frame max
33 → 17 ms, dropped 0.6 → 0 %, Leaflet layers 175 → 77, DOM nodes 11 493 → 10 309, listeners 803 → 627;
the pan was already inside budget there (p95 31–33 → 30 ms) — its events cluster into far fewer bubbles.

The load's 54 ms task is the events answer itself: decoding it natively (`Response.text()`, as the
boundaries are) saved ~5 ms of it — the rest is circe's decode and the layer's diff — so that change
was not kept.

**Budgets** (`perf.test.mjs`): the events path now also wheel-zooms six steps (`perf-paths.mjs`
`mapZoom`), and its budget is tighter — pan input p95 300 → 200 ms, pan dropped 25 → 12 %, zoom longest
task ≤ 150 ms, zoom dropped ≤ 8 %, and the page's DOM nodes ≤ 12 000 and listeners ≤ 2 000 (the animated
form fails these last two: 15 400–18 600 nodes, 2 200–2 700 listeners). A browser test holds the
switch itself: three points animate a zoom, 1 203 do not, and three again do; and the bubble's outline.
The soak (100 rounds) is unchanged: heap +1.2 MB, DOM nodes +0, listeners +0, Leaflet layers +0.

## Map regressions since MapLibre: cluster motion and playback (#3764, 2026-10-24)

**Clusters.** The DOM cluster layer (`web.map.PointSet`) still slid a split bubble's points out at the
zoom's end, but a merge drew the new bubble in one step: the points vanished and the bubble appeared
(it then slid a few pixels from its first point's place). Traced per frame (Chromium, three points,
zoom 10 → 7): before, the bubble appeared on the first frame after `zoomend`; now the three points fly
into it over 250 ms (`.hd-cluster-ghost`, taken off their pane at once so nothing else counts or moves
them) while it fades in (`.hd-cluster-arrive`). `ClusterMotion` decides it: a new marker whose points
all stood at one place comes out of it (a split); one whose points came from several places stays and
appears (a merge). `prefers-reduced-motion: reduce` turns both off in code (the group is drawn at
once, the zoom not counted as animated), not only in the stylesheet.

**Playback.** Measured over −3000…−1000 with the boundaries on (the perf fixtures, GPU Chromium,
5 s): the frame loop itself never dropped a frame (0 %, frame p95 16.7 ms), but what it drew was choppy —

| | before | after |
|---|---|---|
| findings requests a second | 60 (one per frame, each answer re-drawn as it came) | 0.5 (four seconds of playback ahead, cut to the window locally: `PlaybackFeed`) |
| boundaries | the year's states replaced five times a second (the throttled answers): states and their colours stepped every ~13 years | every state fetched so far held (`PlaybackAreas`, keyed by place and years), faded in and out by the playhead and recoloured in place (feature state) every frame; outlines sent 0.5 times a second, only when new states arrive |
| frames on which no state changed look | ~80 % (5 changes a second) | 18 % |
| bubbles | every bubble re-made on each answer (span changed → `refresh`) | recoloured in place (`PointSet.recolour`: `--g0`/`--g1`) unless their content changed |

MapLibre's `global-state` expressions were considered for the fade: a data-driven paint property that
reads global state is evaluated when tiles are laid out, so changing it every frame re-lays the source
out — as costly as `setData`. Feature state (what `GlShapes.restyle` already used) updates paint arrays on
the main thread without re-tiling, and is set only for the states whose step (1/20) or colour changed.

Budgets (`perf.test.mjs`, "playback"): longest task ≤ 150 ms, dropped frames ≤ 15 %, findings requests
≤ 1 a second, outline uploads ≤ 6 a second, frames without a state change ≤ 60 % (measured 0 ms, 0 %,
0.5, 0.5 and 18 %; before: 60 findings requests a second). A browser test traces the split and the
merge frame by frame, and both with reduced motion.

## Desktop zooms: the animation counted on screen (#4291, 2026-10-25)

The reader's report: after #3764 the map's animations looked right on a phone and still clunky on a desktop.
Measured in Chromium (GPU) and Firefox on the test server (JVM server, full-linked bundle) with live-sized
feeds that answer as the server does — 500 findings, 2 500 events (three quarters over Europe and the Near
East) sent **for the view's bounding box** — at 1920×1080, 1440×900 and 390×844, the view at 40° N 22° E,
zooms 4.4 ↔ 4.8 (each crosses a clustering level), per frame.

**Three causes, all growing with the window.**

1. **The animation was gated by the points held, not the markers drawn.** `PointSet` animated a zoom only
   while it held ≤ `Layout.clusterAnimateMax` (1 000) points, and the events are fetched for the view: a
   1920×1080 window held 1 939 points and drew 217–465 markers, a phone held 910–1 159 and drew 114–206. So
   the desktop never animated (0 of 4 level-crossing zooms, both windows) and the phone did whenever it held
   fewer than 1 000 (2 of 4) — "fixed on mobile". Above the limit the new markers were also made in 6 ms slices
   from the centre out, so a desktop zoom ended with the bubbles appearing over several frames.
2. **Once animated, every moving marker laid the page out.** `ClusterMotion.slide` wrote a marker's start,
   read `offsetWidth` and wrote its end — one forced layout of the whole page per marker (35 ms of script at
   1920×1080). With the gate alone fixed, a level-crossing zoom held a frame for 71–93 ms at 1920×1080,
   67–75 at 1440×900, 56 at 390×844.
3. **Every frame of a zoom or pan restyled every marker's whole subtree.** A marker is placed by `--tx`/`--ty`;
   an unregistered custom property is inherited, so writing it restyles every element inside the marker (the
   bubble's SVG, a point's glyph). The main thread's work per frame of the map's own zoom ease, median:

| window | markers | before (style recalc) | after (style recalc) |
|---|---|---|---|
| 1920×1080, zoom out | ~470 → 217 | **18.0 ms** (11.9) | 6.9 ms (2.2) |
| 1920×1080, zoom in | 217 → ~470 | 8.0 ms (5.0) | 4.0 ms (1.3) |
| 1440×900, zoom out | ~390 → 167 | **16.3 ms** (9.9) | 6.8 ms (2.1) |
| 390×844, zoom out | ~210 → 115 | 8.1 ms (4.9) | 4.3 ms (1.3) |

   Over 16.7 ms a frame is late: the desktop's zoom itself stuttered, the phone's did not.

**What changed.**

- The limit counts **markers drawn** before and after the zoom (`ClusterMotion.within`, in view and the
  draw's margin; `Layout.clusterAnimateMax` = 1 000 markers), not the points held.
- The moves are a CSS **animation of the `translate` property** from (or to) an offset (`--move-x`/`--move-y`,
  `.hd-cluster-move`, `@keyframes cluster-move`/`cluster-fly`) on top of the transform that follows the map:
  nothing is read back, and a marker keeps following the map while it moves.
- On a zoom that animates, the markers **on screen** are made first, hidden (`.hd-cluster-pending`), a 6 ms
  slice a frame, and every move starts once they are all made (one to three frames after the zoom's end);
  those in the margin follow in slices. A layout for changed points (the view's events reloaded on the zoom's
  end — they answered within those frames in Firefox and took the moves away) waits until the moves have
  started; the move's end that follows the zoom's, at the same view, leaves them be. Ghosts that would merge
  off screen into a bubble off screen go at once.
- `--tx`, `--ty`, `--move-x`, `--move-y` are **registered** (`@property`, `inherits:false`): a marker's new place
  restyles the marker alone. This also helps every pan.

**Measured** (Chromium, GPU; level-crossing zooms, 2 runs × 2; long tasks = Long Tasks API):

| window | points held · markers drawn | animated, before → after | longest frame after | long tasks after |
|---|---|---|---|---|
| 1920×1080 | 1 939 · 217–465 | 0 / 4 → 4 / 4 | 33 ms | none |
| 1440×900 | 1 565–1 939 · 163–375 | 0 / 4 → 4 / 4 | 33 ms | none |
| 390×844 | 910–1 159 · 114–206 | 2 / 4 → 4 / 4 | 33 ms | none |

Firefox at 1920×1080: 0 of 2 animated before, 4 of 4 after; its late-timer long tasks (65–68 ms on a zoom
out) are of the same size as before the change (52–58 ms with nothing animated). The events-layer budget path
(the perf fixture's 2 400 events at a world view, 1440×900, wheel zooms, interleaved A/B on the same server):
zoom dropped frames 7.3–8.0 % before (no animation) and 1.8–2.6 % after (animated) — 1.2–1.3 % for the old
script with the registered properties alone.

**Playback** was measured the same way (−2000…−1000, 5 s, the events layer on): 0–1 % dropped frames, frame
p95 16.7 ms and no long task in every window, before and after — the per-frame work #3764 removed did not
come back with a wider window, and no difference between desktop and phone was found there. What changed
for it is that a layout's new markers are now always made in time slices (the slicing no longer depends on
the points held), and the registered properties.

**Budget** (`perf.test.mjs`, "in a 1920×1080 window a zoom animates the bubbles"): the perf fixture's events
sent by view (`eventsIn`), four zooms across whole levels (`perf-paths.mjs` `clusterZoom`): held > 1 000 and
drawn < 1 000, every zoom animated (`notAnimated` 0; master: 1 of 4 not, the ones at zoom 4–5), longest
task ≤ 50 ms (measured 0), dropped ≤ 15 % (0.8 %). The browser test "a zoom animates by the markers drawn,
not the points held" (1920×1080) holds the rule itself: 1 203 points drawn as a few dozen markers animate
(master: not), 2 556 points drawn as over a thousand markers do not.

## Map motion: an in-app setting (#4718, 2026-10-25)

The reader saw no merge or split of the bubbles on a zoom. Measured on a restored copy of the live data
(Chromium, every layer on, a period chosen), the cause was **not** the volume: at most **328 markers** were
drawn, against the animation's limit of 1 000 (`Layout.clusterAnimateMax`, counted on screen since #4291).
The Mac had **Reduce Motion** on, and the map honoured `prefers-reduced-motion` in four places, each on its
own: `ClusterMotion.reduced()` skipped the regroup, `MapLabels.reducedMotion()` the names' easing, the
stylesheet's map transitions their `@media` blocks, and MapLibre itself turned every eased zoom into a jump
(515 ms → ~10 ms).

**The decision** (the reader's): an in-app **Map motion** setting, kept per device — *follow the system*
(the default: today's behaviour), *always animate*, *never animate*.

**As built.**

- **One answer**: `web.map.MotionPref.animate()` — the choice (`UiState.mapMotion`, a `Persist.Local` field
  like the base map's options: `localStorage` `histmersion.ui.mapMotion`, every access in `Try`, anything
  unreadable the default, never in a link) over the system's setting, which a `matchMedia` change listener
  keeps live. `MapMotion` holds the pure rules (`animates`, `heldBySystem`, `mapLibreReduce`, `attribute`).
- **The regroup**: `PointSet.layout` animates only when `MotionPref.animate()` (`ClusterMotion.reduced` is gone).
- **The names**: `MapLabels.reducedMotion()` is `!MotionPref.animate()`.
- **The stylesheet**: the five map `@media (prefers-reduced-motion: reduce)` blocks (the overlays' fades, the
  cluster moves, the names' scale, the event labels' and the points' transitions) read
  `:root[data-map-motion="still"]`, which `MotionPref` writes on the root element whenever its answer changes.
- **MapLibre** (6.12.0): it reads `prefers-reduced-motion` itself (`browser.prefersReducedMotion`, from
  `matchMedia`, in `easeTo`, `flyTo` and a drag's inertia) unless a camera call says `essential: true`, and its
  only switch for its own gestures is the map option `reduceMotion`, applied when a map is made (a module-wide
  value with no setter of its own). So: every camera move the app makes (`MapLibreSurface.setView`,
  `fitBounds`, `panBy`) eases only when `MotionPref.animate()`, and then with `essential: true`; otherwise it
  jumps (`jumpTo`, `animate: false`). The map is made with `reduceMotion` = `false` (always), `true` (never)
  or unset (follow the system: MapLibre reads the system's setting itself, live). MapLibre's own **+**/**−**
  buttons, a double click, the zoom keys and a drag's glide therefore take a *changed* choice at the next page
  load; everything else switches at once. `matchMedia` is not patched.
- **Elsewhere**: no other code reads the reduced-motion setting. The page's other transitions (the side
  sheet, panels, the list's fade) never followed it and do not now; period playback does not check it (it is
  an animation of time the reader starts, and its fades are the stylesheet's map transitions above, which do
  follow the setting). The docs screenshots (`docs-screenshots.mjs`) emulate reduced motion and leave the
  setting at its default, so they stay still.
- **The control**: in the map options (the button beside the base-map menu), now available on every base
  map — on an online map it holds the motion setting and a line saying the other options are Readable's and
  Physical's. A labelled `<select>` (*Map motion*; `aria-label` "map motion"); on an online map it takes the
  focus when the menu opens. Under it, while it follows the system and the system asks for reduced motion:
  *Animations are off because your system asks for reduced motion* (`role="note"`, the select's
  `aria-describedby`). It is inside the menu, so it never covers the map; on a phone the menu is in the map
  tools' drawer (#4292's fold untouched).

**The fixture** (`perf-fixtures.mjs` `fullLoad`, `routeFullLoad`): what the page holds with every layer on
over a period — 500 findings, 200 sites, 200 pictures, 1 500 events (sent by the view's area, as the server
does), 140 polities — dated inside −1600…−1100. Scattered evenly over Europe and the Near East, the same counts
drew 400–670 markers at 1920×1080, twice the live page; placed around 80 centres (within 1.5° of one) they
draw 206–345 over zooms 5–7 (105–112 at 390×844), as the live copy did.

**Measured** (the browser test "map motion at …", JVM server, full-linked bundle; four app zooms 5→6→7→6→5
over [40° N, 24° E] and one click on MapLibre's **+**, traced per frame: *motion frames* are frames after the
zoom's end on which markers moved while the camera stood as on the frame before; *camera frames* those on
which the camera moved; the longest frame interval over the whole trace):

| browser · window | mode (system setting) | regroups animated | motion frames | markers moving at most | camera frames a zoom (app · +) | longest frame |
|---|---|---|---|---|---|---|
| Chromium 1920×1080 | follow (reduce) | 0 / 4 | 0 | 0 | 1 · 2 | 37 ms |
| | always (reduce) | 4 / 4 | 55 | 220 | 32–33 · 32 | 35 ms |
| | never | 0 / 4 | 0 | 0 | 1 · 2 | 29 ms |
| | follow (no preference) | 4 / 4 | 54 | 220 | 32–33 · 32 | 48 ms |
| Chromium 390×844 | follow (reduce) | 0 / 4 | 0 | 0 | 1 · 2 | 19 ms |
| | always (reduce) | 4 / 4 | 56 | 70 | 32–33 · 32 | 20 ms |
| | never | 0 / 4 | 0 | 0 | 1 · 2 | 19 ms |
| | follow (no preference) | 4 / 4 | 56 | 70 | 32–33 · 32 | 29 ms |
| Firefox 1920×1080 | follow (reduce) | 0 / 4 | 0 | 0 | 1 · 2 | 29 ms |
| | always (reduce) | 4 / 4 | 49 | 232 | 32 · 32 | 53 ms |
| | never | 0 / 4 | 0 | 0 | 1 · 2 | 40 ms |
| | follow (no preference) | 4 / 4 | 47 | 238 | 32 · 32 | 60 ms |
| Firefox 390×844 | follow (reduce) | 0 / 4 | 0 | 0 | 1 · 2 | 25 ms |
| | always (reduce) | 4 / 4 | 55 | 72 | 32 · 31 | 35 ms |
| | never | 0 / 4 | 0 | 0 | 1 · 2 | 30 ms |
| | follow (no preference) | 4 / 4 | 56 | 72 | 31–33 · 32 | 41 ms |

An eased zoom takes 510–530 ms from the ask to its end, a jump 4–27 ms.

**Budget** (`perf.test.mjs`, "at 1920×1080 · 390×844 with every layer on, an animated zoom holds no long
frame"): the four zooms animated (follow, no preference), longest task ≤ 50 ms, dropped ≤ 15 %. Chromium:
0 ms and 1.1 % at 1920×1080, 0 ms and 0 % at 390×844. Firefox has no Long Tasks API (a 10 ms timer arriving
late stands in), and the same zooms with the map held still (*never*) measure 52–83 ms there at 1920×1080
(the zoom's redraw, not the motion; #4291 saw the same); its budget is therefore "within 50 ms or within
20 ms of the same zooms held still": animated 55 ms against 83 still, dropped 3.1 %; 0 ms at 390×844.

**Remaining.**

- Firefox drops frames when ~500 markers merge in one zoom: with the evenly scattered fixture (670 drawn at
  zoom 6 at 1920×1080), the zoom out to 5 sent 522 markers flying and dropped 20–33 % of that zoom's frames
  (Chromium 1.5 %). The live page draws at most ~330; a merge of that size is beyond what it shows today.
- MapLibre's own gestures take a changed setting at the next page load (above); a live switch would need
  MapLibre to expose its reduced-motion value after a map is made.

## Every site, event and picture: the point layers as tiles (2026-10-27)

The sites, pictures and events layers asked for 200 sites (the whole world, the span's middle year), 200
pictures and at most 2 000 events of the view; the map showed 200 of the corpus's 92 600 dated sites,
every one from the curated (Mediterranean) gazetteer. They are now read from vector tiles
(`GET /api/tiles/…`, docs/map-layers.md "Every site, event and picture"): every item of the span, a
crowded tile's items counted by cells. Measured on a restore of the 2026-10-27 backup (1.08 M places, 92 600
dated sites, 2 489 events, 3 135 located pictures) in a `*_test` database on a throwaway Postgres 18.4 /
PostGIS 3.6.4, the JVM server on loopback, headless Chromium 1248 with the GPU (`modules/web/test/perf-tiles.mjs`,
1440×900, every layer on — boundaries, sites, pictures, events —, each view after a cold server start).
*Before* is the base commit's bundle (446ab3c1, served through `HISTMERSION_PERF_BUNDLE`) on the same
server and data: its JSON feeds count as its "tiles". *After, 343 k* adds 250 000 synthetic dated sites
spread over the whole world (60° S–70° N, −4000…1800) to the copy — the density a global gazetteer import
is expected to bring.

**The server, per tile** (a probe over loopback — each request twice, its time and its decoded features —, a tile around Rome; *cold* the first request of
that tile and span after a server start — the first of all includes the JIT's warm-up —, *warm* the kept
tile):

| layer | span | z | tile | bytes | features (cells · items) | cold ms | warm ms |
|---|---|---|---|---:|---:|---:|---:|
| sites | year -500 | 2 | 2/1 | 10112 | 103 (79 · 24) | 319 | 5 |
| sites | year -500 | 4 | 8/5 | 8451 | 92 (54 · 38) | 16 | 4 |
| sites | year -500 | 6 | 34/23 | 37244 | 513 (0 · 513) | 25 | 4 |
| sites | year -500 | 8 | 136/95 | 11600 | 151 (0 · 151) | 15 | 3 |
| sites | year -500 | 10 | 547/380 | 8732 | 114 (0 · 114) | 15 | 4 |
| sites | -3000..2000 | 2 | 2/1 | 24477 | 239 (225 · 14) | 200 | 4 |
| sites | -3000..2000 | 4 | 8/5 | 24347 | 231 (230 · 1) | 43 | 4 |
| sites | -3000..2000 | 6 | 34/23 | 16131 | 162 (145 · 17) | 16 | 3 |
| sites | -3000..2000 | 8 | 136/95 | 4323 | 41 (36 · 5) | 13 | 5 |
| sites | -3000..2000 | 10 | 547/380 | 14647 | 156 (108 · 48) | 12 | 3 |
| events | year -500 | 2 | 2/1 | 1883 | 17 (0 · 17) | 24 | 3 |
| events | year -500 | 4 | 8/5 | 420 | 2 (0 · 2) | 6 | 3 |
| events | year -500 | 6 | 34/23 | 268 | 1 (0 · 1) | 5 | 3 |
| events | year -500 | 8 | 136/95 | 0 | 0 (0 · 0) | 4 | 3 |
| events | year -500 | 10 | 547/380 | 0 | 0 (0 · 0) | 4 | 3 |
| events | -3000..2000 | 2 | 2/1 | 9348 | 81 (56 · 25) | 10 | 3 |
| events | -3000..2000 | 4 | 8/5 | 32881 | 349 (0 · 349) | 13 | 3 |
| events | -3000..2000 | 6 | 34/23 | 12856 | 137 (0 · 137) | 8 | 3 |
| events | -3000..2000 | 8 | 136/95 | 4078 | 43 (0 · 43) | 6 | 3 |
| events | -3000..2000 | 10 | 547/380 | 3741 | 39 (0 · 39) | 6 | 3 |
| media | year -500 | 2 | 2/1 | 105806 | 474 (0 · 474) | 20 | 3 |
| media | year -500 | 4 | 8/5 | 16832 | 68 (0 · 68) | 5 | 3 |
| media | year -500 | 6 | 34/23 | 15606 | 63 (0 · 63) | 5 | 2 |
| media | year -500 | 8 | 136/95 | 6778 | 29 (0 · 29) | 4 | 2 |
| media | year -500 | 10 | 547/380 | 4247 | 18 (0 · 18) | 4 | 3 |
| media | -3000..2000 | 2 | 2/1 | 15643 | 110 (87 · 23) | 8 | 3 |
| media | -3000..2000 | 4 | 8/5 | 21695 | 131 (76 · 55) | 6 | 3 |
| media | -3000..2000 | 6 | 34/23 | 87230 | 388 (0 · 388) | 7 | 4 |
| media | -3000..2000 | 8 | 136/95 | 58821 | 268 (0 · 268) | 6 | 3 |
| media | -3000..2000 | 10 | 547/380 | 54995 | 250 (0 · 250) | 6 | 2 |

At 343 k dated sites the shallowest tiles of a whole range are the cost: 0.5–0.6 s for the world tile (z0),
0.13–0.28 s at z1, 0.07–0.2 s at z2, cold; 3–4 ms kept. A single year stays under 125 ms at every zoom.

**One view, every layer on — before:**

| span | zoom | tiles (feeds) | KB (largest) | tile p50 / max ms | last tile ms | longest task | points held · drawn | sites items · cells (counted) | media | events |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| year -500 | 2 | 7 | 174 (78) | 36 / 119 | 255 | 0 | 734 · 34 | — | — | — |
| year -500 | 4 | 1 | 11 (11) | 10 / 10 | 30 | 0 | 724 · 98 | — | — | — |
| year -500 | 6 | 1 | 5 (5) | 7 / 7 | 33 | 0 | 709 · 97 | — | — | — |
| year -500 | 8 | 1 | 1 (1) | 8 / 8 | 18 | 0 | 698 · 29 | — | — | — |
| year -500 | 10 | 1 | 1 (1) | 6 / 6 | 13 | 0 | 698 · 15 | — | — | — |
| -3000..2000 | 2 | 7 | 2742 (760) | 33 / 87 | 253 | 0 | 2674 · 53 | — | — | — |
| -3000..2000 | 4 | 1 | 609 (609) | 37 / 37 | 64 | 0 | 2285 · 148 | — | — | — |
| -3000..2000 | 6 | 1 | 254 (254) | 14 / 14 | 46 | 0 | 1354 · 153 | — | — | — |
| -3000..2000 | 8 | 1 | 51 (51) | 8 / 8 | 26 | 0 | 815 · 65 | — | — | — |
| -3000..2000 | 10 | 1 | 19 (19) | 6 / 6 | 13 | 0 | 726 · 22 | — | — | — |

**After:**

| span | zoom | tiles (feeds) | KB (largest) | tile p50 / max ms | last tile ms | longest task | points held · drawn | sites items · cells (counted) | media | events |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| year -500 | 2 | 36 | 332 (105) | 65 / 112 | 486 | 0 | 1182 · 40 | 319 · 32 (5591) | 489 · 0 (0) | 47 · 0 (0) |
| year -500 | 4 | 18 | 178 (85) | 48 / 70 | 100 | 0 | 1658 · 173 | 701 · 137 (5209) | 489 · 0 (0) | 36 · 0 (0) |
| year -500 | 6 | 18 | 173 (44) | 34 / 40 | 71 | 0 | 1899 · 215 | 1067 · 177 (2759) | 340 · 0 (0) | 20 · 0 (0) |
| year -500 | 8 | 18 | 66 (26) | 22 / 30 | 53 | 0 | 1055 · 156 | 693 · 0 (0) | 62 · 0 (0) | 5 · 0 (0) |
| year -500 | 10 | 18 | 34 (11) | 23 / 28 | 45 | 0 | 627 · 102 | 279 · 0 (0) | 50 · 0 (0) | 3 · 0 (0) |
| -3000..2000 | 2 | 18 | 197 (39) | 28 / 185 | 341 | 0 | 1231 · 87 | 184 · 134 (90297) | 194 · 48 (2338) | 357 · 40 (1696) |
| -3000..2000 | 4 | 18 | 275 (41) | 36 / 136 | 172 | 0 | 2533 · 418 | 114 · 819 (87785) | 397 · 129 (2030) | 753 · 47 (995) |
| -3000..2000 | 6 | 18 | 525 (99) | 45 / 60 | 99 | 0 | 3815 · 405 | 43 · 1007 (36234) | 1467 · 0 (0) | 1024 · 0 (0) |
| -3000..2000 | 8 | 18 | 215 (71) | 19 / 32 | 64 | 0 | 2060 · 285 | 875 · 230 (2645) | 495 · 0 (0) | 186 · 0 (0) |
| -3000..2000 | 10 | 18 | 127 (57) | 17 / 40 | 63 | 0 | 1326 · 201 | 608 · 81 (1017) | 305 · 0 (0) | 58 · 0 (0) |

**After, 343 k dated sites:**

| span | zoom | tiles (feeds) | KB (largest) | tile p50 / max ms | last tile ms | longest task | points held · drawn | sites items · cells (counted) | media | events |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| year -500 | 2 | 36 | 391 (105) | 73 / 122 | 468 | 0 | 1311 · 179 | 0 · 480 (23559) | 489 · 0 (0) | 47 · 0 (0) |
| year -500 | 4 | 18 | 305 (85) | 46 / 59 | 103 | 0 | 2819 · 664 | 1196 · 803 (8234) | 489 · 0 (0) | 36 · 0 (0) |
| year -500 | 6 | 18 | 190 (44) | 50 / 64 | 100 | 0 | 2084 · 292 | 1248 · 181 (2789) | 340 · 0 (0) | 20 · 0 (0) |
| year -500 | 8 | 18 | 68 (27) | 31 / 41 | 65 | 0 | 1070 · 161 | 708 · 0 (0) | 62 · 0 (0) | 5 · 0 (0) |
| year -500 | 10 | 18 | 34 (11) | 29 / 33 | 52 | 0 | 628 · 105 | 280 · 0 (0) | 50 · 0 (0) | 3 · 0 (0) |
| -3000..2000 | 2 | 18 | 254 (39) | 278 / 526 | 676 | 0 | 1407 · 190 | 7 · 487 (314607) | 194 · 48 (2338) | 357 · 40 (1696) |
| -3000..2000 | 4 | 18 | 359 (41) | 69 / 170 | 203 | 0 | 3136 · 663 | 0 · 1536 (132417) | 397 · 129 (2030) | 753 · 47 (995) |
| -3000..2000 | 6 | 18 | 579 (99) | 46 / 65 | 105 | 0 | 4224 · 533 | 134 · 1325 (38965) | 1467 · 0 (0) | 1024 · 0 (0) |
| -3000..2000 | 8 | 18 | 229 (71) | 30 / 40 | 73 | 0 | 2205 · 375 | 1009 · 241 (2698) | 495 · 0 (0) | 186 · 0 (0) |
| -3000..2000 | 10 | 18 | 128 (57) | 27 / 33 | 59 | 0 | 1341 · 209 | 623 · 81 (1018) | 305 · 0 (0) | 58 · 0 (0) |

*tiles (feeds)*: requests the view made for these layers (after: three layers × the view's tiles; before:
the layers' JSON feeds — the first row includes the page's first load); *last tile ms*: from the view
set to its last tile in; *sites items · cells (counted)*: the sites drawn as themselves, the cells of
crowded tiles and the sites those cells count. Before, the world over −3000…2000 held 2 674 points
(200 sites, 200 pictures, 2 000 events — the caps — and the findings) and its events feed alone was
2.7 MB (every regional event's outline); after, it holds 1 231 points standing for
90 297 sites, 2 338 + 194 pictures and 1 696 + 357 events, in 197 KB of tiles. The page's work barely
grows with the corpus: a tile is at most 600 items or 256 cells, so 343 k sites hold 1 407 points at a world
view (1 231 with 92 600).

**Gestures** (the same page; whole range; *before* holds 2 244 / 1 456 points at zooms 3 / 6, *after* 1 237 /
4 716, *after 343 k* 2 151 / 5 236):

| gesture: input → paint p95 / max ms · dropped · frame max ms · longest task ms | before | after | after, 343 k dated sites |
|---|---|---|---|
| pan, zoom 3 (40 drags + 4 wheel zooms) | 49 / 51.5 · 0 % · 16.8 · 0 | 43.3 / 52.1 · 0 % · 16.8 · 0 | 51.3 / 56.7 · 0 % · 16.8 · 0 |
| pan, zoom 6 | 28.6 / 35.7 · 0 % · 16.8 · 0 | 53.9 / 54.1 · 0 % · 16.8 · 0 | 52.9 / 54.9 · 0 % · 16.8 · 0 |
| timeline drag, zoom 3 | 33.8 / 38.3 · 0 % · 16.8 · 0 | 36.9 / 44.2 · 0 % · 16.8 · 0 | 55.6 / 60.5 · 0 % · 16.8 · 0 |
| timeline drag, zoom 6 | 37.5 / 43 · 0 % · 16.8 · 0 | 55.7 / 59.9 · 0.8 % · 33.2 · 0 | 63.1 / 67.8 · 0.8 % · 33.4 · 0 |
| playback −3000…−1000, 5 s | 20 / 20 · 0 % · 16.8 · 0 | 17 / 17 · 1.7 % · 50 · 0 | 20 / 20 · 1.3 % · 33.4 · 0 |

Playback, the layers' requests: before 81 (2 973 KB) in 5 s, after 97 (325 KB), after 343 k 121 (738 KB) — the
boundaries' own, 5 a second, unchanged and not counted. Every pan and drag input is inside the 100 ms rule; the longest frame of
any gesture is 50 ms (one frame of playback) and no long task was seen.

**Decisions, from these numbers.**

- **Playback reads tiles by time bucket**, not the existing feeds: the feeds were capped (the very caps
  this removes), and their answer per playback frame was the events' 2.7 MB. A bucket of one second of
  playback (`ui.map-tiles.playback-bucket-seconds`; 100 years at −3000…−1000 over the default 30 s) changes
  the URL ~0.7 times a second: ~19 tile requests a second of 3–27 ms (kept tiles on a replay), 0–1.7 %
  dropped frames, frame p95 16.7 ms.
- **A dragged span waits until it settles** (250 ms): every new span is every tile of the view cut again,
  per layer; during the drag the layers keep their items, recoloured in place — input p95 37–63 ms.
- **Thinning is counting, not dropping**: 600 items or 256 cells a tile keeps a tile under 110 KB and the
  page's points held at 1 200–5 200 whatever the corpus; the client's own clustering then draws 40–670
  markers.

**Remaining.**

- The shallowest tiles of a whole range at the density of a global import: 0.5–0.6 s for zoom 0 and up to
  0.3 s for zoom 1, cold (every dated site of the world is read and counted). Kept tiles answer in 3–4 ms,
  but a new span at a world view waits for them. A pyramid — a zoom's cells summed from the next zoom's,
  or counts stored per cell and century — would make them as cheap as zoom 4.
- Playback's event LABELS (`EventLayer`, names and outlines) still come from `/api/events/geo`, capped at
  `ui.events.map-limit` (2 000); the markers are every event.
- A pan at zoom 6 with ~4 700 points held: input p95 54 ms (before: 29 ms with 1 456) — inside the rule,
  but it is where the client's cost grows (the cluster layer's regroup of every held point).
