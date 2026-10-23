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
