# Map styles: a study of readable base maps, and a prototype (2026-10-20)

**Status: built (2026-10-23) as option C — see "As built" at the end; §1–§6 are the study as it was
written.** The user's verdict on the self-drawn base map
(docs/research-terrain-basemap.md), on 2026-10-20:

> Country names often miss letters ("REECE", "MACEDONI"). Virtually all our terrain styles are bad to look
> at; keeping these styles makes no sense. River rendering looks cheap. Contrast is poor. Land cover is ugly.
> Fonts are ugly as well. We should have studied existing map styles that are considered readable and why —
> 1–2 readable styles win over several presets that are all worse than the default raster (OSM Carto).

This document (1) studies the well-regarded styles and the cartographic literature and distils measurable
rules, (2) finds the cause of the cut-off names, (3) re-measures the renderer on quality as well as speed,
(4) prototypes two styles that follow the rules, (5) compares them with OSM Carto and the current *Natural*
at four identical views, and (6) recommends what to keep, what to delete, the renderer, the effort and the
risks.

**Recommendation in one paragraph.** Keep **two** styles — *Readable* (OSM Carto's colours and hierarchy,
without modern clutter) and *Physical* (Patterson / Esri Physical / Stamen Terrain tradition) — and **delete
all nine current presets** and the code that exists only to draw them. Draw them with **MapLibre GL JS**
(6.x: SDF text placed across the whole view, real line joins, native hillshade and colour-relief, local fonts
through `font-faces`) as the map itself, the overlays moved into it; maplibre-gl-leaflet (MapLibre under
Leaflet's overlays) only as a short bridge — its wheel zooms sit at the 100 ms input budget. On a GPU the
native MapLibre map is *faster* than the current canvas map (pan input p95 31 ms vs 46 ms, zoom 32 vs 47 ms);
in software rendering it is much slower (pan p95 104–158 ms), so the GL map must refuse software GL and fall
back to the raster map. The cut-off names are protomaps-leaflet pruning its label index to 16 tiles while
they are still on screen (wide windows only); a one-line interim fix exists. Details, numbers, effort below.

---

## 1. What makes the readable styles readable

### 1.1 The styles studied

| style | renderer · data | land / water | rivers | land cover | relief | fonts and names |
|---|---|---|---|---|---|---|
| **OSM Carto** (the default raster; CC0) | Mapnik · OSM | land `#f2efe9`, water `#aad3df` | **the water's own colour**, 0.7 px at z8 → 1.5 at z10 → 2.3 at z12 → 4.5 at z14; streams from z12 | Lch-designed pastels: forest `#add19e` (L80 C30), grass `#cdebb0` (L90), scrub `#c8d7ab`, farmland `#eef0d5` (L94 C14) | none | **Noto Sans** (+ Noto for every script). Countries mixed-case purple `#6a3f69`, 10 → 15 px by zoom, letter-spacing 0.5 px, halo 1.5 px `rgba(255,255,255,.6)`; places `#222`; water names italic `#4d80b3` |
| **OpenTopoMap** (CC-BY-SA) | Mapnik · OSM + SRTM | Carto-like | `#1d4dff` at 50–80 % opacity, 1.1–2 px | Carto-like | hillshade `grain-merge` at 0.83–0.9 opacity, vertical exaggeration **lowered as resolution grows** (×7 for the 5 km DEM, ×4 for 700 m, ×2 for 90 m); hypsometric ramp 0 → 6 000 m green → ochre → red → grey → white; contours `#c45700`, 0.2–0.6 px, 100 m index, 50 m and 20 m between, labels 7 px | DejaVu Sans |
| **Stamen Terrain** (Stadia vector remake) | MapLibre · OpenMapTiles | land made *less yellow* than the original, which was "too yellow, too green in forests and too hilly" | — | two land-cover datasets z7–12, each with two blurred "glow" lines so edges never look like rendering errors | "crisp" hillshade | **PT Sans Narrow** (condensed) |
| **Esri World Topographic** (vector) | Esri · community data | land `#f7f6d5`, water `#bae6f7` → `#bfd9f2` | water colour `#bfd9f2`, 1 → 2.67 px from z11 | parks `#dee3bc` | designed to sit on *World Hillshade* | **Arial** regular/bold/italic. Countries UPPERCASE grey `#737373`, letter-spacing 0.05–0.15 em by country size, halo 0.93 px; water italic `#497aab`, sea letter-spacing 0.1 em |
| **Esri World Physical** | raster | the U.S. NPS **Natural Earth** physical map (Tom Patterson) at 1.24 km/px | — | natural land-cover colours | Patterson's shaded relief | names in a separate reference overlay |
| **swisstopo** (light / base vector maps) | MapLibre · swisstopo | near-white `#fcfcfc` / `#fdfdfe`; water `#d1e4f0` → `#c7e0f5` | `#4da4da`, 0.25 px at z5 → 0.75 at z7 → 1–3.5 px by river *width class* at z13 | forest `#bad2ac` faded in from z5–6 | the national relief as **vector luminosity bands** (16 greys + a 4 % yellow sun tint) | **Frutiger Neue** (condensed for places, italic for water); countries UPPERCASE `rgb(145,70,145)`, letter-spacing 0.12 em |
| **Thunderforest Landscape** | — | — | — | hand-curated natural detail (cliffs, scree, scrub, orchards) | strong relief, "almost three-dimensional" | — (man-made POIs and routes deliberately left out) |
| **CARTO Voyager / Positron** | MapLibre · OpenMapTiles | `#fbf8f3` / `#fafaf8`; water `#b0d0d6` / `#d4dadc` | water colour, 0.5 px at z8 → 1 at z9 → 2 at z15 | **one** green at 20–40 % opacity (Voyager), 50 % near-white (Positron) | none | **Montserrat** + Open Sans + Noto Sans fallbacks. Countries UPPERCASE `#6b7d91`, 10 → 14 px; Positron's z6 country grey `#b9c2c9` is 1.7:1 — a deliberately faint base map |
| **Protomaps basemaps** light/white/grayscale/dark (BSD-3; our tiles' own schema) | MapLibre | `#e2dfda`, water `#80deea` | water colour from z9, exponential 1.6 width curve | `landcover` classes as soft greys/greens | none | **Noto Sans** Regular/Medium/Italic. Countries UPPERCASE Medium, size by `population_rank`; seas UPPERCASE italic, letter-spacing 0.1 em |
| **OpenFreeMap Liberty** (OSM Liberty) | MapLibre · OpenMapTiles | `#f8f4f0`, water `rgb(158,189,255)` | `#a0c8f0` | wood at 0.4 opacity, grass 0.3 | none | **Noto Sans** Regular/Italic/Bold; countries black bold, halo 1 px blurred |
| MapTiler Topo / Outdoor / Landscape, Americana | MapLibre | — | — | — | MapTiler: hillshade + contours; Americana: an optional relief mode | (not inspected in detail: their style files need an account key or name no fonts publicly) |

### 1.2 What the literature says

- **Relief: light from the upper left, "higher is lighter", no grey on flat land.** Imhof's *Cartographic
  Relief Presentation* (1965/1982) sets oblique light from the north-west, turned up to ±30° where a range's
  trend demands it; the brightest tones on the highest lit slopes, the darkest on the highest shadowed ones,
  shading weakening toward the valleys, and *aerial perspective* (lowlands hazier). Patterson & Jenny
  (2011) make it operational for screen maps: hypsometric tints **lighter with height**, because dark
  shadows over dark tints give "excessively dark mountains"; shaded relief **that adds no grey to flat
  areas**; shadowed slopes semi-transparent; the relief subtle enough that the tints still carry the
  elevation.
- **Hypsometric tints mislead when green means "low".** Readers take lowland green for vegetation — dark
  green on the arid Persian Gulf shore reads as forest. Patterson's *cross-blended* tints colour lowlands by
  climate (warm humid yellow-green, cool humid blue-green, **arid khaki brown**, polar grey) below 1 000 m,
  with fuzzy blends, and conventional tints above. A 2013 user study (US, Switzerland, Germany, n > 200)
  found readers generally struggle to read hypsometric colour at all — so tints must be *restrained*, a
  backdrop, not information to decode.
- **Multidirectional shading reads better than a single light** on varied terrain (Jenny et al. 2020 review
  the methods; MapLibre's `multidirectional` hillshade implements four lights).
- **Typography (Axis Maps' guide; the styles above agree):** water names blue and italic; area names in
  letter-spaced capitals, spacing reflecting the area's extent; hierarchy by size, weight and case — capitals
  and darker colour promote, smaller and greyer demote, at least 2 pt between ranks; at most one sans and
  one serif; **sans at small sizes on screen**; nothing under 9–10 px on screen.

### 1.3 The rules, measurable

| # | rule | value | from |
|---|---|---|---|
| R1 | Rivers are the water's colour at small scales and join their lakes and sea | same colour as water fill; on a tinted physical map a darker blue so they read on colour | Carto, Esri, CARTO, Protomaps; Esri Physical, swisstopo |
| R2 | River width grows with zoom, thin at small scales | 0.4–0.8 px at z5–8, 1.2–1.5 px at z9–10, 2.3 px at z12, 4–4.5 px at z14 | Carto `@river-width-z*`, swisstopo |
| R3 | No river outlines, no casings, round caps/joins | — | all |
| R4 | Land cover is pale: L* ≥ 80 for forest, ≥ 90 for open land, chroma ≤ 30; translucent fills ≤ 0.5 opacity at small scales; **one** green at small scales | Carto Lch values; Voyager 0.2–0.4; Liberty 0.3–0.4 | Carto, CARTO, Liberty |
| R5 | No land-cover class that is mostly modern on a map of the past at small scales — but desert vs cultivated plain does matter (the Nile) | farmland very faint, barren as pale sand | this study |
| R6 | Relief: light from 315°, shadows without a grey cast on flats; exaggeration *down* as resolution goes *up* | igor/multidirectional, shadow ≤ 0.5 opacity; exaggeration ≈ 0.5 at z≤6 → 0.4 at z≥12 | Imhof, Patterson, OpenTopoMap |
| R7 | Hypsometric tints light and low-chroma, lighter with height above the lowlands, lowland not saturated green | L* ≥ 85, chroma ≤ 20; arid khaki lowland | Patterson & Jenny 2011/2013 |
| R8 | Contours brown, thin, index every 5th thicker and labelled | `#c45700`/brown at 0.2–0.6 px; 100 m (z11), 50 m (z12), 20 m (z13–14) | OpenTopoMap, swisstopo |
| R9 | One sans family for every script, italic for water | Noto Sans (Carto, Protomaps, Liberty); Frutiger (swisstopo); Arial (Esri) | — |
| R10 | Country/region names: capitals, letter-spaced 0.1–0.15 em, medium weight, a muted colour; never larger than 16–18 px | Esri 0.05–0.15 em, swisstopo 0.12 em, Protomaps uppercase Medium | — |
| R11 | Halo 1–1.5 px in the colour of what is under the name (land halo on land, water halo on water), ~0.8 opacity | Carto 1.5 px white 0.6; Esri 0.93 px land colour; CARTO 1 px | — |
| R12 | Contrast: names ≥ 4.5:1 against their usual background (WCAG 1.4.3 for body text); large sea names ≥ 3:1. Land vs water is a *hue* contrast (1.2–1.4:1 luminance in every style) and needs a crisp coastline | measured below | WCAG 2.x |
| R13 | Names placed once for the whole view, with collision across tiles; nothing clipped at a tile edge | — | MapLibre/Mapnik metatiles |

Contrast of names in the styles (WCAG relative luminance):

| pair | ratio |
|---|---|
| Carto places `#222` on land | 13.9 |
| Carto country `#6a3f69` on land | 7.2 |
| Carto water names `#4d80b3` on water | **2.6** (relies on the halo) |
| Esri country `#737373` on land | 4.3 |
| Esri water names on water | 3.1 |
| CARTO Voyager country | 4.0 |
| current *Natural*: ocean names on sea | 4.0; land 0 m tint vs sea **1.2** |
| prototype *Readable*: water names `#2c5880` on water | 4.7 |
| prototype *Physical*: places on lowland | 8.5; physical names on 800 m tint 6.9; water names `#123552` on the shelf 9.6, on 1 000 m depth 5.2, on their halo over the 6 000 m colour ≥ 8.5 |

### 1.4 Why the current presets fail (against those rules)

- **R4/R5/R7 — land cover and tints.** *Natural* lays satellite land cover at 0.22–0.45 opacity in four
  saturated greens/olives over a hypsometric tint whose lowland is green: two colour systems fight, the
  forest/grass patchwork reads as blotches (aegean-z7-natural below); green lowland contradicts R7.
- **R1/R2 — rivers.** Natural Earth's rivers are drawn `#76aed2` at 2 px down to zoom 3–9, a hard blue line
  across lowland green that does not join the sea's colour — "cheap".
- **R12 — contrast.** The sea's shore `#a9cfe4` and the 0 m land tint `#cfe2b4` are 1.2:1 in luminance and
  close in hue under the pale shore glow — the coast dissolves into a double band. (*Natural*'s depth ramp
  itself — pale shelf, clearly darker basins — is what the user liked; *Physical* now keeps it, see §4.2.)
- **R9/R10 — fonts.** The terrain presets set Noto Serif 600 in letter-spaced capitals (2.5 px) for ranges
  and regions and serif italic for water; the local street presets use the browser's `sans-serif` (Helvetica on
  macOS, DejaVu on Linux — not ours, so not the same everywhere) for places and Georgia italic for seas. Drawn
  as canvas text per tile, at 10–12 px the serifs break up.
- **R13 — clipped names** (next section).

---

## 2. The cut-off names ("REECE", "MACEDONI")

**Cause: protomaps-leaflet forgets the names of tiles it still shows.** Its label index keeps the names of at
most **16 data tiles per source and zoom** (`new Labelers(…, 16, …)`, hard-coded in its Leaflet frontend);
when a 17th is laid out, `pruneOrNoop` drops the farthest tile's names from the index — even while that tile
is on screen. A name spans several 256-pixel display tiles, and every display tile draws the names the index
holds *when it renders*. So once a neighbouring display tile re-renders (a new tile at the edge of a pan, a
tile invalidated by another name's placement) after the name's own tile was pruned, it draws its part of the
map *without* the name while the other part of the name stays on its old canvas: "G|REECE", "MACEDONI|A".
The opposite also happens: a pruned tile laid out again places names that tiles drawn earlier do not show.

**Confirmed** with an instrumented page (`spike/map-styles` server, the eastern-Mediterranean extract, the
*Standard* preset, Chromium): after a session of pans at zoom 8, every display tile on screen was checked
against the label index (does it show every name the index holds over it, and only those?).

| window | data tiles the index may keep | settled views with a name drawn by some of its tiles but not others |
|---|---|---|
| 1280 × 800 | 16 (default) | 0 / 7 |
| 1920 × 1080 @2× | 16 | 0 / 5 |
| **2560 × 1440** | **16** | **7 / 7** (e.g. *Saronic Gulf* held by the index, drawn by none of its four tiles) |
| 2560 × 1440 | 1 000 | 0 / 7 |

At zoom 8 a 2560-pixel-wide map shows ~22 data tiles (levelDiff 1: one 512-pixel data tile per 2 × 2 display
tiles) — more than 16. The user's wide window explains why the browser tests (1280 × 800) never saw it.

Ruled out: letter-spacing measured without spacing (the box includes it), per-character kerning drift (0–2.4
px, inside the 16-pixel search margin and the halo), late fonts (loaded before the first layout).

**Fixes.** In protomaps-leaflet: raise the limit (`layer.labelers.maxLabeledTiles = 256` after
construction — the index grows with the tiles visited at one zoom, a few thousand entries), and re-render the
display tiles of a pruned data tile. Structurally: **render names in screen space, once per frame, for the whole
view** — what MapLibre (and Mapnik's metatiles) do; MapLibre's symbol placement has no per-tile cut, and its
collision index is per frame, not per tile. In the prototypes no name is cut in any of the four views below.

Other rendering defects found:

- **Wrapped names overlapping** (already fixed in `TerrainLabels`, 2026-10-20) — the library's line spacing
  used the ink height.
- **Text is canvas text, re-rasterised per tile**: at fractional zoom or during a zoom animation tiles are
  scaled bitmaps — names blur until the tiles redraw; MapLibre draws SDF glyphs at the screen's resolution
  every frame.
- **Lines are stroked per tile** with the canvas's joins; a line crossing a tile edge is two strokes that
  meet there (not seen as a visible defect in the views of §5, but it rules out cased lines, dashes and line
  labels that run across tiles; MapLibre tessellates lines with the tile buffer and places line labels
  across tiles).

---

## 3. The renderer, on quality and speed

### 3.1 Quality

| | protomaps-leaflet 5.1.0 (current) | MapLibre GL JS 6.12.0 |
|---|---|---|
| text | canvas `fillText` per tile, re-rasterised per tile; blurred while scaled during zoom | SDF glyphs, crisp at any zoom and DPR, halo in the shader |
| fonts | any CSS font (we load Noto Serif) | **`font-faces`** (MapLibre 6): woff2 files declared in the style, rasterised to SDF in the page — no glyph server, our own font files, unicode-range per subset |
| label placement | per data tile, collision index per zoom pruned to 16 tiles (§2) | per frame across the view, collision across tiles, fade-in, line labels along curves, `symbol-sort-key`, variable anchors |
| lines | canvas strokes per tile | tessellated with joins/caps across tile buffers, exponential width curves |
| relief | our own JS: Horn hillshade, bicubic resampling, contours, sea floor (`Relief`, `ReliefLayer`, `Contours`, `Hillshade`: ~900 lines of Scala.js) | native `hillshade` (standard / igor / combined / **multidirectional**), native **`color-relief`** (hypsometric tint), contours via `maplibre-contour` (BSD-3) |
| styles | our own HOCON style document compiled to code (`TerrainStyle`, `TerrainRules`) | the MapLibre style specification (JSON) — documented, tooled (Maputnik), what every vector style in §1 is written in |
| overzoom | per layer by our code | per source; land cover (only to z7 in the build) needs its own source with `maxzoom: 7` (done in the prototype through the server's `?layers=` cut) |

### 3.2 Speed (measured)

The app's budget gestures (perf-paths.mjs: a 40-step drag and four wheel zooms; then six wheel zooms), the
Aegean fixture on the native test server, Chromium headless, 1440 × 900, **median of 3 runs**, this machine
(Apple M4 Pro). Two GL conditions: **software** (SwiftShader — the condition the app's budgets are set in)
and **GPU** (`--use-angle=metal`).

| | drawn ¹ | pan input p95 / max | pan dropped | pan longest task | wheel-zoom input p95 | zoom dropped | zoom longest task | JS heap ² |
|---|---|---|---|---|---|---|---|---|
| **GPU** | | | | | | | | |
| current *Natural* (protomaps-leaflet + our relief) | 582 ms | 45.9 / 70.3 ms | 1.9 % | 0 | 47.3 ms | 1.3 % | 0 | 41.6 MB |
| *Readable*, native MapLibre | 140 ms | **31.3** / 49.2 ms | 0.6 % | 0 | **32.0 ms** | 0 % | 0 | 8.1 MB |
| *Physical*, native MapLibre | 122 ms | **31.0** / 34.7 ms | 0.6 % | 0 | **34.7 ms** | 0 % | 0 | 8.3 MB |
| *Readable* in Leaflet (maplibre-gl-leaflet) + 300 DOM markers | 207 ms | 31.2 / 50.1 ms | 0.6 % | 0 | **96.4 ms** (117 in an earlier run) | 1.9 % | 0 | 11.9 MB |
| *Readable*, native, + 300 points as a circle layer | 174 ms | 31.3 / 34.6 ms | 0.6 % | 0 | 32.3 ms | 0 % | 0 | 8.3 MB |
| **software GL (SwiftShader)** | | | | | | | | |
| current *Natural* | 577 ms | 47.6 / 51.1 ms | 3.9 % | 54 ms | 48.9 ms | 2.6 % | 0 | 41.5 MB |
| *Readable*, native | 907 ms | **111** / 156 ms | **74.6 %** | 0 | 82.3 ms | 44.5 % | 0 | 8.0 MB |
| *Physical*, native | 1 288 ms | **158** / 215 ms | 22.2 % | 83 ms | 145 ms | 64.6 % | 71 ms | 8.2 MB |
| *Readable* in Leaflet + 300 DOM markers | 960 ms | **148** / 210 ms | 44.3 % | 164 ms | 202 ms | 58.6 % | 85 ms | 11.8 MB |
| *Readable*, native + 300 points | 960 ms | **104** / 127 ms | 73.1 % | 0 | 79.5 ms | 44.0 % | 0 | 8.2 MB |

*Physical* re-measured after its sea was reworked (depth tints at every zoom, §4.2), 3 runs: GPU pan p95
31.2 ms, zoom 33.0 ms, 0.6 % / 0 % dropped, drawn 169 ms — unchanged; software pan 162 ms, zoom 198 ms,
19 % / 69 % dropped — a little worse than before, as expected with one more full-screen layer.

The app's budgets for comparison: input p95 ≤ 100 ms, dropped frames ≤ 15 %, longest task ≤ 150 ms, heap ≤ 60
MB. ¹ *Natural*: from the view being set to every tile and name answered (perf-paths.mjs); MapLibre: from the
map's creation to its first `idle` (every tile drawn) — MapLibre's module load (~1.1 MB, cached) not counted.
² MapLibre decodes tiles in Web Workers, whose heaps (and the GPU's memory) the page heap does not include;
the figures are not comparable as totals, only as what the main thread holds. The views are the same ground
(MapLibre's zoom is the raster zoom − 1).

- **On a GPU, native MapLibre is the fastest of all**: pan input p95 31 ms against the current map's 46 ms,
  wheel zooms 32–35 ms against 47 ms, no dropped frames to speak of, every tile drawn 4× sooner — a pan only
  moves tiles already on the GPU, and names are re-placed off the main thread. Points drawn by MapLibre
  itself cost nothing measurable.
- **maplibre-gl-leaflet** (MapLibre kept in step with Leaflet, DOM markers on top) pans as fast on a GPU,
  but its **wheel zooms reach the 100 ms input budget** (96 and 117 ms in two runs): on every zoom step
  the plugin re-sizes and re-renders the GL map synchronously inside Leaflet's zoom animation.
- **In software rendering every GL variant breaks the budgets** (pan p95 104–158 ms, 22–75 % dropped
  frames): SwiftShader rasterises the whole canvas on the CPU every frame. The current canvas map is the
  same in both conditions. This is the honest risk: a browser without GPU acceleration (some VMs, remote
  desktops, a blocklisted driver) would get a poor map — hence the fallback below.

### 3.3 The trade-off

| option | quality | speed (GPU) | speed (no GPU) | work |
|---|---|---|---|---|
| A. keep protomaps-leaflet, fix the pruning, restyle | fixes the cuts; text, lines, placement stay canvas-grade | as now | as now | small (days), but the ceiling stays low |
| B. MapLibre base map in Leaflet (maplibre-gl-leaflet) | full GL quality for the base map; overlays unchanged | pans fine; **wheel zoom input p95 96–117 ms** (budget 100) | fails budgets | medium: replace `TerrainMap`/`TerrainRules`/relief code with a style; overlays untouched |
| C. native MapLibre, overlays moved into it (GeoJSON sources, circle/symbol layers, MapLibre markers for the few DOM ones) | best; one collision index for base-map names *and* corpus names | best measured (pan p95 31 ms, zoom 32 ms, 0–0.6 % dropped) | fails budgets | large: the map layer of the app (~5 000 lines: `MapView`, `MapLabels`, `DotCluster`, `PointLayer`, `EventLayer`, `PlaceHover`, `FadingLayer`, the boundaries) rewritten; clustering by MapLibre's GeoJSON `cluster` or supercluster |

**Recommended: C as the destination; B only as a short bridge, accepting wheel zooms at the edge of the
input budget while the overlays move.** Both with a guard for the no-GPU case: detect a software
WebGL renderer before creating the map (`WEBGL_debug_renderer_info`: SwiftShader, llvmpipe, "Software", or a
context created with `failIfMajorPerformanceCaveat` failing) and fall back to the online raster map with a
one-line notice. (Measured: headless Chromium's SwiftShader is *not* refused by MapLibre's
`canvasContextAttributes: { failIfMajorPerformanceCaveat: true }` — the flag alone is not a sufficient
guard; the renderer string is.) Option A's canvas map could be that fallback instead, at the cost of keeping
its code. The app's budgets for the GL map are then measured with the GPU (`--use-angle=metal` locally); CI's
software runs measure the raster map, as today's budgets were set — the budget is not relaxed for the same
condition, it is measured in the condition the map will run in. If no GPU can be assumed for the user's own
machines, stay with A.

---

## 4. The two prototypes

Code: `spike/map-styles/` — `styles.mjs` (both styles as MapLibre style documents built from the server's
`/api/basemap` answer; every value commented with the rule it follows), `index.html` (the page: MapLibre 6
native or in Leaflet, `font-faces`, the `hmdem://` elevation protocol), `serve.mjs` (static files and a
pass-through to a Histmersion server, same origin, no other host), `perf.mjs` (§3.2), `ref.html` (the OSM
Carto reference). Run: `npm ci --prefix spike/map-styles`, a test server with an extract, then
`HISTMERSION_SERVER_URL=… node spike/map-styles/serve.mjs` and open `http://127.0.0.1:18500/?style=readable`.

### 4.1 (a) *Readable* — a general-purpose map in OSM Carto's tradition

- Carto's land `#f2efe9` and water `#aad3df`; rivers in the water's colour by Carto's widths (R1–R3).
- Land cover pale and few: forest Carto `#add19e`, grass and scrub one pale `#dfe6c9`, deserts `#f0e4c4`,
  farmland `#e8ecd0` faint (so the Nile delta reads against the desert), built-up pale grey; satellite classes
  to z9, OSM's own from z9.5 (R4, R5).
- A light relief: `igor` hillshade (no grey on flat ground), shadow only, exaggeration 0.2 → 0.35 (R6).
- **Towns kept, roads faint, borders faint, no POIs.** Reason: the reader's way into a map of the past is the
  modern place they know (Mykines for Mycenae, Tyre, Rosetta); towns are therefore the main orientation and
  stay at Carto's contrast. Roads compete with the corpus's own lines and are mostly modern: only motorways,
  trunk and primary roads, from zoom 8, at 45–70 % opacity, no casing at low zoom. Country borders dashed
  Carto purple at 45 % — present (they are how a reader locates a country) but quieter than any overlay.
- Names: Noto Sans; countries Medium UPPERCASE 0.12 em `#6a3f69`; seas italic `#2c5880` (4.7:1); towns
  `#222` Medium sized by population rank; villages from z10 `#555`; ranges/deserts UPPERCASE 0.25 em brown
  (R9–R12).

### 4.2 (b) *Physical* — relief, restrained tint, water, physical names

- Hypsometric tint (`color-relief`) light and low-chroma: lowland pale sage-khaki `#e0e7cc` (Patterson's
  Mediterranean cross-blend between temperate green and arid khaki), warming to tan at 800–1 300 m, then
  *lighter* grey-white above 2 000 m (R7).
- Multidirectional hillshade (four lights 315/270/0/225°, warm shadows ≤ 0.42 opacity, faint highlights),
  exaggeration 0.5 → 0.4 (R6).
- Land cover as veils, not patches (R4): forest a faint green (≤ 0.22 opacity), cultivated land and
  grassland a fainter yellow-green to zoom 10 (so the Nile delta and the plains read against the desert, as on
  Patterson's Natural Earth physical map), deserts a pale sand, glaciers white.
- The sea by depth at every zoom (revised 2026-10-20 after the user found the first version's depths "almost
  non-existent" and *Natural*'s sea better): ETOPO1 depth tints (`color-relief` on the sea-floor layer) in
  Patterson's / Esri Ocean Base's order — the shelf palest `#cde4f3`, a visible step at the shelf edge (~200 m:
  `#b4d3ea` → `#a1c5e2`), then clearly darker to `#5a8bb6` at 3 000 m and `#3f6f9b` at 6 000 m, close to
  *Natural*'s ramp and a little less saturated — so the North Aegean Trough, the Cretan Sea and the Ionian and
  Levantine abysses read at once; the submarine relief shaded softly (`igor`, 0.3 → 0.12 by zoom). The depth
  layer lies *under* the land: the vector earth drawn over it is the coast, exact at any zoom, so the 1.8 km
  depth cells never spill onto land and need no fade-out (they stay readable at z9–12, softer: 0.92 → 0.85);
  the land's elevation tint is transparent at sea level so the depths show through. A coastline `#5f8db5` at
  85 %, 0.45 → 1.1 px.
- Water names `#123552` with a 1.6 px pale halo `rgba(214,233,246,.85)`: 9.6:1 on the shelf, 5.2:1 at
  1 000 m, and ≥ 8.5:1 against the halo over the deepest colour (the text alone on 3 000 m water would be
  3.5:1 — the halo carries it there).
- Rivers **darker** than the sea (`#6f9fc4`) so they read on tinted land (R1 for tinted maps).
- Contours from zoom 11 where a focus region holds the 30 m relief (maplibre-contour; 100/50/20 m, brown,
  index lines labelled) (R8).
- Names: physical first (seas, ranges, lakes, rivers along their lines, islands, peaks), towns small
  `#3d3d3d`; no roads, no borders.

### 4.3 Fonts

**Noto Sans** (SIL OFL 1.1), Regular / Medium / SemiBold / Italic: one design across Latin, Greek and
Cyrillic, with Noto companions for Hebrew, Arabic, Syriac, Armenian, Georgian, Coptic — the scripts of this
corpus's places; the face OSM Carto chose for being "the most consistent and the most legible choice";
Protomaps and OpenFreeMap use it too. Sans because names on screen are 10–13 px (Axis Maps). Loaded by
MapLibre's `font-faces` from woff2 subsets by unicode-range (Latin 13–14 KB per weight, Greek 8 KB, Latin
extended 56 KB — loaded only when a name needs it; an English map loads ~40–55 KB for three weights). Considered: Source Sans 3 and Fira Sans (Latin/Greek/Cyrillic, no companions), Open Sans
(Hebrew too, not Arabic/Armenian/Georgian), PT Sans Narrow (Stamen's; no Greek). A condensed width (Noto
Sans Condensed) is the next step if long labels crowd.

### 4.4 The elevation in MapLibre

One `raster-dem` source to the deepest focus zoom (12) through a page protocol `hmdem://`: a tile the
server has (extract or focus region) is passed through; one it lacks is cut from its nearest ancestor and
resampled **in metres** (bilinear) and re-encoded as terrarium PNG — resampling terrarium's bytes directly
would mix the high and low byte. So the relief is drawn once everywhere (two overlapping DEM sources would
shade the focus regions twice). In production this belongs in the server (the tile endpoint answering a
missing deep tile from its ancestor), where `Relief`'s bicubic resampling already exists.

---

## 5. Side by side

Same centre and scale (MapLibre's zoom one less than the raster zoom named), 1280 × 800, device scale 1. Left to right, top to bottom: **OSM Carto** (public
raster, internal comparison only), **current *Natural*** (protomaps-leaflet), **prototype *Readable***,
**prototype *Physical***. The extract used: Protomaps build 20261019 `--keep-all` z0–10, Mapterhorn z0–8 +
z9 + the Argolid focus to z12, ETOPO1 z0–7, Natural Earth names, bbox `19,28,37,42.5` (~200 MB, scratch,
deleted after).

**Aegean, zoom 7 (38.3° N 24.8° E)**

| OSM Carto | current *Natural* |
|---|---|
| ![aegean-z7 OSM Carto](research-assets/map-styles/aegean-z7-osm.jpg) | ![aegean-z7 Natural](research-assets/map-styles/aegean-z7-natural.jpg) |
| **prototype *Readable*** | **prototype *Physical*** |
| ![aegean-z7 Readable](research-assets/map-styles/aegean-z7-readable.jpg) | ![aegean-z7 Physical](research-assets/map-styles/aegean-z7-physical.jpg) |

**Argolid / Mycenae, zoom 12 (37.727° N 22.76° E)**

| OSM Carto | current *Natural* |
|---|---|
| ![argolid-z12 OSM Carto](research-assets/map-styles/argolid-z12-osm.jpg) | ![argolid-z12 Natural](research-assets/map-styles/argolid-z12-natural.jpg) |
| **prototype *Readable*** | **prototype *Physical*** |
| ![argolid-z12 Readable](research-assets/map-styles/argolid-z12-readable.jpg) | ![argolid-z12 Physical](research-assets/map-styles/argolid-z12-physical.jpg) |

**Levant coast, zoom 9 (33.25° N 35.35° E)**

| OSM Carto | current *Natural* |
|---|---|
| ![levant-z9 OSM Carto](research-assets/map-styles/levant-z9-osm.jpg) | ![levant-z9 Natural](research-assets/map-styles/levant-z9-natural.jpg) |
| **prototype *Readable*** | **prototype *Physical*** |
| ![levant-z9 Readable](research-assets/map-styles/levant-z9-readable.jpg) | ![levant-z9 Physical](research-assets/map-styles/levant-z9-physical.jpg) |

**Nile delta, zoom 8 (30.85° N 31.0° E)**

| OSM Carto | current *Natural* |
|---|---|
| ![nile-delta-z8 OSM Carto](research-assets/map-styles/nile-delta-z8-osm.jpg) | ![nile-delta-z8 Natural](research-assets/map-styles/nile-delta-z8-natural.jpg) |
| **prototype *Readable*** | **prototype *Physical*** |
| ![nile-delta-z8 Readable](research-assets/map-styles/nile-delta-z8-readable.jpg) | ![nile-delta-z8 Physical](research-assets/map-styles/nile-delta-z8-physical.jpg) |

What the comparison shows:

- **Aegean, z7.** OSM Carto is readable but busy for this purpose: every administrative boundary, maritime
  limits drawn as purple loops around every island, Greek region names in long italic purple, roads. *Natural*
  is dominated by the dark sea floor and by blotchy forest/grass patches over a green lowland; the coast is
  carried only by the pale shore glow. *Readable* keeps Carto's figure-ground (warm land, clear blue sea) with
  one level of names and none of the boundary clutter; *Physical* shows the mountains first (Pindus, the
  Taurus edge) and the sea floor at a glance: the pale shelves, the North Aegean Trough, the Cretan Sea and
  the Ionian deep in clearly darker blues.
- **Argolid, z12 (Mycenae).** Carto shows roads, admin lines and land use but nothing of the terrain — the
  citadel's hill between two hills is invisible. *Natural* shows the terrain but with heavy contours, dark
  shading, saturated green patches and serif labels that break up. *Readable* shows the hills with a light
  relief under Carto-like colours, towns in English where OSM has an English name; *Physical* adds the 30 m
  focus relief with brown contours (100/50 m) and labels; the edge of the focus region (west of Mykines,
  where the relief falls back to the z8 extract) is visible as a softer relief.
- **Argolic Gulf, z11 (*Physical* only, a check of the sea close in):** the depth tint is still there, softer,
  under a crisp vector coast.

  ![Argolic Gulf z11 Physical](research-assets/map-styles/argolic-gulf-z11-physical.jpg)
- **Levant, z9.** *Natural*'s sea is a dark, saturated blue gradient and the pale glow band along the coast
  is about as wide as the coastal plain itself, so the coast reads twice. *Physical* keeps the coastal plain, the Lebanon/Anti-Lebanon ridges
  and the Jordan rift readable with the names of the towns; *Readable* is the closest to Carto.
- **Nile delta, z8.** Carto is unreadable for a non-Arabic reader (local names only, every governorate border).
  *Natural*: a bright shelf band and a dark sea; the delta is a flat green. *Readable* separates the delta
  (pale farmland) from the desert (pale sand) and labels in English; *Physical* shows the delta as a faint
  green over a khaki desert with relief in the Eastern Desert and Sinai.
- **No name is cut** in any prototype view; MapLibre places names across the whole view.
- **Weak points of the prototypes, still to tune:** *Readable*'s satellite land cover at z7 still shows its
  coarse polygon edges (as Carto's own low-zoom land cover does); *Physical*'s contours are strong at z12
  for a background map; the shading is computed per tile from the extract outside focus regions (softer);
  both styles fall back to the local-script name where OSM has no English one (Φίχτι, Κλένια).

---

## 6. Recommendation

1. **Two styles: *Readable* (default) and *Physical*.** The online raster maps stay as they are.
2. **Delete every current preset** — *Natural*, *Contour map*, *Standard*, *Muted*, *Dark* (local),
   *Terrain*, *Terrain + vegetation*, *Relief only*, *Parchment* — and with them:
   - `core/TerrainStyle`, `core/jvm/TerrainStyleFile`, `basemap/styles.conf`, the style hot-reload
     (`server/basemap/BasemapStyles`, `basemap.style`, `basemap.style-check-seconds`,
     `GET /api/basemap/styles[/example]`, `POST …/check`, `histmersion basemap styles`), the user-guide page
     how-to/terrain-styles.md — replaced by two MapLibre style documents in the web resources (a user who
     wants their own style can point at a MapLibre style JSON later; nothing in the corpus depends on the
     current format);
   - `web/TerrainRules`, `web/TerrainLabels`, `web/Relief`, `web/ReliefLayer`, `web/Hillshade`,
     `web/Contours`, most of `web/TerrainView` and `web/MapOptions` (the options shrink to: style, names on/off,
     name language, relief on/off), `web/vendor/protomaps-leaflet`, the Noto Serif woff2 set (→ Noto Sans);
   - kept: the whole server side of the data (PMTiles reader/writer, fetch, focus regions, on-demand relief,
     sea floor, Natural Earth names, `?layers=`/`keys=` cuts), the notice, the online raster maps.
3. **Renderer: MapLibre GL JS 6** as in §3.3 — C (native, overlays in MapLibre) as the destination, B (the
   base map under Leaflet's overlays) only as a bridge while the overlays move; software GL refused with a
   fallback to the raster map.
4. **The label fix for as long as protomaps-leaflet stays**: `maxLabeledTiles` raised (one line in
   `TerrainMap.vectorLayer`), a regression test at 2560 × 1440 with the index check of §2.

### Effort (one person)

| step | days |
|---|---|
| Interim fix for the cut names in the current map (§2), with a wide-window browser test | 0.5 |
| B: vendor MapLibre 6 (ESM, ~300 KB gzip), a `BaseMapGl` Scala.js facade over maplibre-gl-leaflet; the two styles as JSON resources with theme tokens resolved at load (AGENTS.md "no colour literals in Scala" — the style files hold them, like `styles.conf` does today); Noto Sans subsets; the software-GL detection and its fallback; the elevation fallback moved into the server tile endpoint | 4–6 |
| Delete the presets and the code above; docs (user guide, reference pages, CLI table), tracing-agent metadata | 2 |
| Tests: browser tests (no other host, names not cut, styles switch, fallback), GPU perf budgets locally + software-raster budgets in CI | 2 |
| **B total** | **≈ 9–11** |
| C: overlays into MapLibre (GeoJSON sources + layers for points, events, boundaries, names; clustering; hover/click via `queryRenderedFeatures`; the label cull handed to MapLibre's collision with `symbol-sort-key`), then Leaflet removed | 15–25 |
| **C total** (B's base-map work + the overlays; the bridge itself optional) | **≈ 25–35** |

### Risks

- **No GPU** → GL map unusable; mitigated by refusing software GL (fallback above). Must be tested on the
  user's real machines (a Linux VM, a remote session).
- **Bundle size**: MapLibre 6 is ~1.1 MB minified (~300 KB gzip) against protomaps-leaflet's 128 KB, loaded
  only when the local map is chosen.
- **MapLibre 6 is ESM-only and young** (6.0 in July 2026; `font-faces` new); pin the version, keep the
  vendored copy.
- **Two map engines during B**: Leaflet and MapLibre kept in step — wheel-zoom input p95 96–117 ms on a GPU,
  at the edge of the budget; keep B short.
- **Styles in JSON** instead of the checked HOCON document: lose the hot-reload of a user style file
  (rarely used) — or keep it, pointing at a MapLibre style JSON.
- **Land cover only to zoom 7** in the Protomaps build: drawn overzoomed through its own source; beyond z9
  OSM land use is mapped unevenly (the 2026-10-19 note) — *Physical* uses only forest from it, faintly.

---

## As built (2026-10-23)

The user chose **option C** on 2026-10-20: MapLibre GL JS is the map, the overlays moved into it, Leaflet
removed; the base maps are exactly *Readable* and *Physical* (vector, local data) plus the online rasters
(OSM Standard, Muted/HOT, Dark) as MapLibre raster sources; the nine presets and their tooling deleted.

### ADR: the map behind an interface of the app's own

**Context.** Leaflet had leaked into ~20 files of the page (layers, markers, popups, events, panes); this
switch rewrote all of them. A later change of map library should not.
**Decision.** One package, `web/map/`, defines what the page needs of a map — `MapSurface` / `MapEngine`
(view, events, base map as `BaseMapSpec`, shape sets with per-feature state and id-keyed diffs, popups,
corners, drawn names, `Capabilities`), the HTML markers (`MarkerPane`, `DomMarker`, `PointSet`,
`Clustering`) and value types. The only real engine is the adapter `web/map/maplibre/` (facade +
`MapLibreEngine`); `Engines.default` picks it. A fake surface (`FakeSurface`) tests the overlays in
Scala.js; `MapEngineBoundarySuite` fails when a MapLibre name appears outside the adapter.
**Consequences.** The overlays are library-independent (only the CSS styles MapLibre's popup and control
classes). The interface is thin — it has what the page uses, nothing speculative; a feature the next
library lacks (feature state, `font-faces`) would be emulated in its adapter. Markers stay HTML (cards,
keyboard, tiers, tests keep their DOM contracts) rather than WebGL symbols; that costs DOM nodes at
thousands of points, which the cluster layer bounds. Zooms are the raster grid's everywhere
(MapLibre's + 1, converted only in the adapter), so every stored view, budget and test kept its numbers.

### What moved where

| before | now |
|---|---|
| `TerrainMap`, `TerrainView`, `MapOptions` (preset menu, nine presets × eight options) | `BaseMaps` (one menu of five maps; options: names, language, Physical's contours), `MapHandle` (`window.histmersionMap` for the console and the tests) |
| `TerrainRules`, `TerrainLabels`, `Relief`, `ReliefLayer`, `Hillshade`, `Contours`, protomaps-leaflet, Noto Serif | `web/styles/readable.json`, `physical.json` (generated once from `spike/map-styles/styles.mjs`; `{version}` and `name:{lang}` resolved at load), MapLibre's hillshade/color-relief, maplibre-contour 0.1 (vendored, BSD-3) for the contours, Noto Sans by `font-faces` |
| the page resampling relief deeper than the archive (`Relief`, bicubic) | the server: `BasemapService.fromAncestor` cuts a missing terrain tile (≤ 6 zooms, ≤ the deepest archive) from its nearest stored ancestor, bilinear **in metres**, re-encoded as terrarium PNG; a pure lossless-WebP (VP8L) decoder (`Vp8l`), PNG codec and resampler (`TerrainResample`), LRU caches; the terrain version carries the focus regions' stamp |
| Leaflet + markercluster: markers, cluster group, polygons, popups, panes | `PointSet` + `Clustering` (greedy grid, `js.Map` keyed by doubles), `MarkerPane`/`DomMarker` (CSS custom properties, delegated listeners, sliced creation above `animateMax`), `ShapeSet` on GeoJSON sources with feature state, geometry hit tests (`ShapeHits`), `Popup` |
| `core/TerrainStyle`, `TerrainStyleFile`, `styles.conf`, `server/BasemapStyles`, `basemap.style*`, `/api/basemap/styles*`, `histmersion basemap styles`, how-to/terrain-styles.md | deleted; `core/BaseStyles` names the two styles and the vector layers each needs whole (`basemap status`, `ui.base-map-source = auto`); how-to/base-maps.md |

**No GPU.** `Capabilities.isSoftware` reads the WebGL renderer string (SwiftShader, llvmpipe, softpipe,
lavapipe, "software", "basic render"); the map then draws at pixel ratio 1, the vector styles `reduced`
(no hillshade, no color-relief, no contours), and the notice gets one line. Not the raster fallback §6
proposed: the reduced vector map measured within the input budget (below) and keeps the page offline.

**The cut-off names** (§2) are gone with protomaps-leaflet: MapLibre places the base map's names over the
whole view; a browser test at 2560 × 1440 checks no name is clipped.

### Budgets (perf.test.mjs, Aegean fixture, 1280 × 800, against the native server)

GPU (Metal, Apple M4 Pro, `--use-angle=metal`): every existing budget passes, and the two new ones put
the vector maps under the full overlay load (1 600 state shapes, 2 900 points). Software: Chromium's
SwiftShader (`HISTMERSION_TEST_GPU=0`), the page on its no-GPU path; two runs, the range is both.

| path | GPU | software |
|---|---|---|
| mapPan input p95 | 37 ms | 37–39 ms |
| eventLayer pan p95 · zoom dropped (GPU budget 8 %) | 39 ms · 5.9–7.4 % | 53–54 ms · 7.9–11 % |
| rangeAreas load, longest task | 54 ms | 144 ms |
| Readable: drawn · pan p95 · pan dropped · zoom dropped | 524 ms · 51 ms · 0 % · 0–0.6 % | 566–609 ms · 66–75 ms · 8–20 % · 17–18 % |
| Physical: same | 537 ms · 53 ms · 0 % · 0.6 % | 548–584 ms · 66–81 ms · 12–42 % · 16–29 % |
| soak, 100 rounds: heap · nodes · listeners · markers growth | 2.9 MB · 0 · 0 · 0 | 2.9 MB · 0 · 0 · 0 |

In software every input stays within the 100 ms rule and no task stalls, but the map runs at 20–30
frames a second and its dropped frames vary widely between runs. The software paths therefore have
budgets of their own (`eventLayerSoftware`, `readableMapSoftware`, `physicalMapSoftware`: input, long
tasks, drawing time; frame rate measured but not budgeted); the GPU budgets are unchanged. Against
Leaflet's canvas map, which ran these budgets in software, the events layer's zoom is the one
measurable loss there (11 % dropped against 8 %). The tightest GPU margin is the same path (5.9–7.4 %).

### Risks left

- MapLibre 6 is young (6.0 in July 2026; `font-faces` new) — pinned at 6.12.0 and vendored.
- The software path is measured on SwiftShader on a fast CPU; a slow VM may drop more frames.
- maplibre-contour is a 0.x library; a failure to load it drops the contours, nothing else.
- The bundle: MapLibre is 597 KB + 517 KB (shared chunk) minified, ~300 KB gzip, loaded with the page.

---

## Sources

- OSM Carto: https://github.com/gravitystorm/openstreetmap-carto (CC0) — `CARTOGRAPHY.md`, `style/style.mss`,
  `style/water.mss` (river widths), `style/landcover.mss` (Lch colours), `style/placenames.mss`,
  `style/fonts.mss` (why Noto Sans)
- OpenTopoMap: https://github.com/der-stefan/OpenTopoMap — `mapnik/README.md` (gdaldem exaggeration per
  resolution), `mapnik/relief_color_text_file.txt`, `mapnik/styles-otm/hillshade.xml`, `contours.xml`,
  `waterway-lines-lowzoom.xml`, `mapnik/inc/fontset-settings.xml.inc`
- Stamen × Stadia, *Terrain: behind the scenes*: https://stamen.com/stamen-x-stadia-series-terrain-behind-the-scenes/
- Esri World Topographic (vector) style: https://basemaps.arcgis.com/arcgis/rest/services/World_Basemap_v2/VectorTileServer/resources/styles/root.json ;
  item https://www.arcgis.com/home/item.html?id=7dc6cea0b1764a1f9af2e679f642f0f5
- Esri World Physical: https://www.arcgis.com/home/item.html?id=c4ec722a1cd34cf0a23904aadf8923a0 (the NPS
  Natural Earth physical map)
- swisstopo vector base maps: https://vectortiles.geo.admin.ch/styles/ch.swisstopo.lightbasemap.vt/style.json ,
  https://vectortiles.geo.admin.ch/styles/ch.swisstopo.basemap.vt/style.json
- Thunderforest Landscape: https://www.thunderforest.com/maps/landscape/
- CARTO basemap styles: https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json ,
  https://basemaps.cartocdn.com/gl/positron-gl-style/style.json
- Protomaps basemaps (BSD-3): https://github.com/protomaps/basemaps (`@protomaps/basemaps` 5.7.2,
  `src/base_layers.ts`, `src/flavors.ts`)
- OpenFreeMap Liberty: https://tiles.openfreemap.org/styles/liberty
- OSM Americana: https://github.com/osm-americana/openstreetmap-americana
- protomaps-leaflet 5.1.0 source (`src/labeler.ts`, `src/frontends/leaflet.ts`): https://github.com/protomaps/protomaps-leaflet
- MapLibre GL JS 6.12.0 (`font-faces`, `color-relief`, `hillshade-method`): https://github.com/maplibre/maplibre-gl-js ,
  style spec https://maplibre.org/maplibre-style-spec/
- maplibre-contour: https://github.com/onthegomap/maplibre-contour
- maplibre-gl-leaflet: https://github.com/maplibre/maplibre-gl-leaflet
- E. Imhof, *Cartographic Relief Presentation* (1965; Engl. ed. 1982, ed. H. J. Steward), as summarised in
  Jenny, Heitzler, Singh, Farmakis-Serebryakova, Liu & Hurni, *Cartographic Relief Shading with Neural
  Networks* (2020): https://arxiv.org/abs/2010.01256
- T. Patterson & B. Jenny, *The Development and Rationale of Cross-blended Hypsometric Tints*, Cartographic
  Perspectives 69 (2011): https://cartographicperspectives.org/index.php/journal/article/view/cp69-patterson-jenny
- T. Patterson & B. Jenny, *Evaluating Cross-blended Hypsometric Tints: A User Study in the United States,
  Switzerland, and Germany*, Cartographic Perspectives 75 (2013): https://cartographicperspectives.org/index.php/journal/article/view/cp75-patterson-jenny
- Axis Maps, *Cartography guide: labeling*: https://www.axismaps.com/guide/labeling
- WCAG 2.2, 1.4.3 Contrast (Minimum): https://www.w3.org/TR/WCAG22/#contrast-minimum
- OSMF tile usage policy (the reference screenshots): https://operations.osmfoundation.org/policies/tiles/
