# The terrain base map (2026-10-19)

A base map of MODERN terrain only — relief, coastlines, seas, lakes, rivers, natural land cover —
with no modern roads, towns, place names, borders or buildings, drawn by the web page from data the
server downloads once. There is no reliable worldwide source of how coastlines, rivers, lakes and
vegetation looked in a given century, so the map is today's landscape with a notice saying so
whenever it is shown (user guide: exploring/web-ui.md, "The terrain map").

> **Superseded in part (2026-10-23).** The renderer and the styles below are gone: the page draws two
> base maps, *Readable* and *Physical*, with MapLibre GL JS (docs/research-map-styles.md, "As built").
> The data side — the extracts, focus regions, on-demand relief, sea floor, physical names, the
> `?layers=` cuts — is unchanged and still described here; the relief's resampling deeper than any
> archive moved from the page into the server's tile endpoint.

## The renderer: protomaps-leaflet, and the relief shaded in the page

Measured in headless Chromium (the browser suite's conditions: software rendering) on the same
Aegean extract, Leaflet 1.9.4, a 1280 × 800 page, the median of 3 runs: a 40-step drag and four
wheel zooms.

| | protomaps-leaflet 5.1.0 (canvas) + our relief layer | maplibre-gl 5.9.0 + @maplibre/maplibre-gl-leaflet 0.1.3 (WebGL) |
|---|---|---|
| load to idle | 859 ms, longest task 0 | 942 ms, longest task 234 ms |
| drag: input → paint p95 / max | 18 / 40 ms | 46 / 125 ms |
| drag: dropped frames | 0 % | 14 % |
| wheel zooms: longest task · dropped frames | 0 · 1.7 % | 102 ms · 19 % |
| script size (min / gzip) | 128 KB / 38 KB | 955 KB / 251 KB (+13 KB) |
| hillshade | none built in: shaded by us, 7 ms a 512-pixel tile | native `hillshade` layer |
| facade | a style is code (symbolizer objects, filter functions) | a style is JSON |

MapLibre's GL map is a second map kept in step with Leaflet's: it redraws the whole canvas on every
move, and in software rendering that alone breaks the ≤ 100 ms rule and the 15 % dropped-frame
budget. protomaps-leaflet draws each tile once into a Leaflet canvas tile, so a pan only moves
tiles — the same cost as the raster maps. Its one gap, hillshading, is a pure function over the
elevation tile's pixels (`web/Hillshade`, Horn's method, 2–8 ms a tile, run when a tile arrives, never
during a gesture). Chosen: protomaps-leaflet, vendored at `web/vendor/protomaps-leaflet/` (BSD-3)
and loaded only when the terrain map is first chosen.

On the native test server with the Aegean fixture (perf.test.mjs, "terrain base map", measured after
the app has settled): drawn 828 ms after the view is set, no long task; drag input p95 32 ms, 0 %
dropped; wheel zooms input p95 34 ms, no long task, 0 % dropped; heap 35.5 MB (budget 60). The
map-pan figures of the raster map on the same run: p95 32 ms, 0 % dropped — the terrain map costs a
pan nothing measurable.

## The data

| layer | provider | format | licence |
|---|---|---|---|
| vector | Protomaps daily build (`build.protomaps.com/<yyyymmdd>.pmtiles`, 139 GB, listed with size and checksums at `build-metadata.protomaps.dev/builds.json`) | PMTiles v3, MVT (Protomaps basemap schema v4), gzip | OSM: ODbL (a Produced Work, attribution required); Natural Earth: public domain |
| terrain | Mapterhorn (`download.mapterhorn.com/planet.pmtiles`, 356 GB, z0–12) | PMTiles v3, terrarium-encoded 512-pixel WebP | Copernicus GLO-30 (free use with the DLR/Airbus/EU/ESA notice) and the further open sources Mapterhorn lists (most CC BY 4.0) |

Both providers document regional extracts with `pmtiles extract`, i.e. HTTP range requests against
the published file, and neither states a rate limit; Protomaps discourages hotlinking — the map
never reads from them, only the one-off fetch does. Their whole-file checksums cannot check an
extract, so every range answer must carry the planned `Content-Range` and the planned file's size
and ETag (a build replaced mid-download is refused, not mixed), each vector tile is gunzipped
(gzip's CRC), and a 200 to a range request is closed unread.

The OpenMapTiles-schema builds were the alternative for the vector layer; Protomaps was taken for
its documented, dated public builds and its `landcover` layer (satellite-derived, uniform).

## What is kept

*Superseded the same day by the drop list (below, "Styles, names and the broad extract").* The first
build kept only `earth`; `water` without canals, ditches, drains, docks, pools, fountains and basins;
`landcover` forest, grassland, scrub, barren, glacier (`basemap.vector.layers`). The thinning keeps or
drops whole layers and features by their `kind` without decoding geometry (`server/basemap/Mvt`), so
what is kept is byte-identical to the source.

OSM's `landuse` layer was measured and dropped from that default: at zoom 7–10 it is 80–90 % of the
thinned bytes (874 → 138 MB at zoom 10 without it) and its woods and wetlands are mapped very
unevenly from country to country — a border would show as a change in vegetation. `landcover`
exists to zoom 7; the page draws those tiles larger beyond.

## Sizes (default region `-12,20,90,62`, 2026-10-19)

| vector max zoom | tiles | downloaded | stored (thinned) |
|---|---|---|---|
| 6 | 355 | 15 MB | 3.5 MB |
| 8 | 4 373 | 217 MB | 16 MB |
| 9 | 16 868 | 625 MB | 56 MB |
| 10 | 66 216 | 1 537 MB | 138 MB |

Stored per zoom at 10: z0–5 1.6 MB, z6 1.9, z7 4.0, z8 8.4, z9 40.6, z10 81.0 MB. The z10 fetch took
344 s (187 range requests + 6 directory requests) and 13 s to thin and write.

| terrain max zoom | tiles | downloaded ≈ stored |
|---|---|---|
| 6 | 344 | 52 MB |
| 7 | 1 152 | 162 MB |
| 8 | 4 236 | 504 MB |
| 9 | 16 308 | 1.6 GB |
| 10 | 64 078 | 5.0 GB |

The region `-25,5,95,66` (adds West Africa, Arabia, India, Scandinavia): vector z10 1.85 GB
downloaded; terrain z8 0.67 GB, z9 2.2 GB, z10 7.0 GB. Defaults: vector 10, terrain 8 — ~2 GB
downloaded, ~0.63 GB kept.

## How it is built

- `server/basemap/PmTiles`, `PmTilesFile`, `PmTilesWriter`: the PMTiles v3 codec (header, Hilbert
  tile ids, varint directories), a thread-safe reader with a leaf cache, and a writer that dedups
  identical tiles, run-length encodes them and reserves the first 16 KiB for the header and root —
  metadata and leaves follow the tile data, so an extract is written once, never copied.
- `BasemapFetch`: plan (header, metadata, only the directories holding the region — leaf requests
  merged), download (merged range requests ≤ 64 MB, gaps ≤ 5 % read through, one part file each,
  resumable, pinned by `state.json`), assemble (tile-id order, each distinct content thinned once).
- `BasemapService`: the `basemap-fetch` job (one at a time), the free-space check (before anything
  and before each request: stop when less than `basemap.min-free-gb` would be left), the status, the
  removal, and the tiles at `/api/basemap/{layer}/{z}/{x}/{y}` (vector gzip-encoded as stored, 204
  where the extract has none, `?v=<file stamp>` so a refetch is never hidden by the cache).
- Web: `TerrainMap` (the option, the notice, the paint rules from `--terrain-*` tokens, the relief
  grid layer: a 512-pixel canvas per elevation tile of zoom z − 1), `Hillshade` (pure).

## Risks and open points

- Protomaps rebuilds daily and deletes old builds after a while: a resumed fetch of an old build
  whose file is gone fails with an HTTP error; running it again starts with the newest.
- Mapterhorn's planet file is updated in place (new ETag): a fetch that spans an update stops and
  must be run again.
- The relief is computed per tile with edge pixels clamped: faint seams can show at tile edges on
  steep ground.
- Headless Chromium draws canvases in software; real browsers draw faster. The budgets are set on
  headless figures.

## Styles, names and the broad extract (as built, 2026-10-19)

**The style is a document.** What the page draws moved out of code into a declarative style document
(`core/TerrainStyle`, the built-in one `core/jvm/resources/basemap/styles.conf`, user guide
how-to/terrain-styles.md): named styles of ordered fill/line/label layers selecting features by
source, data layer, geometry, `kind`, `kind_detail` and zoom; colours (a CSS colour or `var(--token)`
of the page's theme); zoom-dependent numbers as linear stops; the relief's colours, opacities, light
and exaggeration; a tone; labels and their language. A user file (`basemap.style`, default
`basemap-style.conf` beside `histmersion.conf`, HOCON or JSON) is merged over the built-in styles by
id: `extends`, layers patched by id (`remove`, `before`), `hidden`. Parsing is pure and checked: every
error names its path (`styles[0].layers[2].width: the stops' zooms must ascend`), an unknown key is a
warning; any error refuses the whole file and the built-in styles are served, the error in the
answer's `warnings`, `/api/health` (`warnings`) and `/api/about` (`problems`). Built in: *Terrain*
(the former look), *Terrain + vegetation* (stronger tints, OSM land use's woods, scrub and
wetlands), *Relief only*, *Parchment* (light).

**Hot, without a watcher.** `server/basemap/BasemapStyles` keys its answer on (file path, size,
mtime, language, check interval): each `GET /api/basemap/styles` costs one `stat`, the parse happens
once per change, and a config reload naming another file or language applies at the next read
(`deps.cfg` is read per call). The page asks every `basemap.style-check-seconds` (5) while it shows
the terrain map and redraws when `version` (a digest of the served styles) changed — measured in the
browser test: an edited file shows within the interval, no reload. `GET /api/basemap/styles/example`
and `POST …/check` back `histmersion basemap styles [--example|--check FILE]`.

**Precompiled.** `web/TerrainRules` compiles a style once into protomaps-leaflet rules: a selector of
sets (geometry, kinds, not-kinds, details, own `min_zoom`), colours resolved against the theme once,
constants as numbers and stops as one small function each (paint functions get the DATA zoom, one
below the map's: `levelDiff` 1). Labels are a second protomaps-leaflet layer (zIndex 3, in the tile
pane): above the relief, so it never shades them, below every marker pane; placed in rule order and,
within a rule, by the feature's own `min_zoom` (protomaps-leaflet's labeler handles the collisions
across tiles). A *names* toggle switches them.

**The names.** Seas, oceans, bays, straits, islands and the small lakes and rivers are already in the
Protomaps build (water points with `name`/`name:*` and `min_zoom`; `earth` island points; named
river lines — lakes and rivers only from zoom 10). Mountain ranges, deserts, plateaus, peninsulas,
island groups, large lakes, major rivers and peaks are not; they come from Natural Earth 5.1.2's
GeoJSON label layers (public domain): `geography_regions_polys` (a polygon's name at the middle of the
widest stretch of the line through its largest part's centre of area — a crescent range's centroid
falls outside it), `geography_regions_points`, `geography_regions_elevation_points`, `lakes` (not
reservoirs) and `rivers_lake_centerlines` (rivers as lines, simplified to about a pixel per zoom and
clipped per tile; not canals). `server/basemap/BasemapLabels` keeps their names in every language NE
has (as `name:<code>`, Chinese as `zh-Hans`/`zh-Hant` like Protomaps), their label zoom (`min_label`,
else `min_zoom`, else `scalerank`), and writes `labels.pmtiles` (layers `physical`, `rivers`) — a
third base-map layer, `histmersion basemap fetch --layer labels` (part of `all`). No settlement,
country or road file is read. Sizes: default region 755 names (253 rivers, 160 peaks, 143 lakes, 71
ranges, 33 deserts, …), 2 555 tiles, **1.39 MB**; `-25,5,95,66` 1 066 names, 1.91 MB; about 20 MB
downloaded either way. A label's language: `en` → `name:en`, `name`; `local` → `name`; another code →
`name:<code>`, `name:en`, `name` (`basemap.labels.language`, the file's `language`, a style's).

**The broad extract (user, 2026-10-19).** Restyling must never need a new download, so the vector
fetch now DROPS only what a map of the past never needs — `basemap.vector.drop = "roads; transit;
buildings; boundaries; places; pois +peak +volcano +saddle …"` (a layer whole, some kinds, or all
but `+kinds`) — and keeps everything else, including layers a newer build adds; `--keep-all`
stores the tiles unfiltered. The archive's metadata records what it kept (`histmersion.drop` /
`keepAll`); `basemap status` prints it, and for an extract without that record (fetched before)
recommends one `fetch --layer vector`. Stored, default region, measured on build 20261019 (z0–9 from
one unfiltered z9 extract of the whole region, 625 MB downloaded; z10 from an unfiltered z10 extract
of `5,35,30,50`, 361 MB, scaled by its z10/z9 ratio per filter):

| zooms | unfiltered (`--keep-all`) | default drop list | the former keep list |
|---|---|---|---|
| 0–8 | 210 MB | 184 MB | 16 MB |
| 0–9 | 614 MB | 535 MB | 57 MB |
| 0–10 | ≈ 1.5 GB | ≈ 1.3 GB | 138 MB |

At zoom 9, each layer alone (gzip): landuse 311 MB, roads 39, water 39, places 8, boundaries 6,
earth 3, pois 1 — land use is nine tenths of the default extract. Dropping it (`; landuse`) brings
the download back near the former sizes; the docs say so.

**Drawing cost, and the remedy.** protomaps-leaflet decodes every layer of a tile, drawn or not. On
the broad Aegean fixture (4.2 MB, z0–9, default drop list) the terrain budget then failed on memory:
heap **94.5 MB** (budget 60) with interaction still fine (drawn 857–865 ms, pan input p95 33–35 ms,
0–0.6 % dropped). Remedy: the page asks for what it draws — `GET /api/basemap/{layer}/{z}/{x}/{y}
?layers=earth:a,water:pl,…` (layers and geometries, `Basemap.parseSubset`), which the server cuts
from the stored tile without decoding geometry (`Mvt.filter` with a geometry rule, a 512-entry cache
of recent cuts); each of the two canvas layers asks for its own rules' reads (`TerrainRules.subsets`).
With it, labels on, on the native test server (perf.test.mjs): drawn **842 ms**, no long task, pan
input p95 **31 ms**, 0 % dropped, wheel zooms p95 33 ms, 0 % dropped, heap **37.5 MB** — the same as
the narrow extract without names (828 ms, p95 32 ms, 35.5 MB).

**Found on the way.** A `FileChannel` is interruptible: a tile request the page cancelled (its
virtual thread interrupted mid-read) closed the shared channel, and every later tile and status
answer failed with `ClosedChannelException` until a restart. `PmTilesFile` now opens a channel closed
that way again (the interrupted read fails; the others go on) — test in `PmTilesSuite`.

**Not done.** Per-zoom keep rules (e.g. land use only to zoom 8) — the subset makes the page's cost
independent of the extract's breadth, so only the disk pays for it. Labels for OSM's own peaks
(`pois`, zoom ≥ 10 in the build; drawn by the built-in style from zoom 10). Line labels for Natural
Earth's ranges (a range's name sits at a point, not along it).


## Local street maps and offline (as built, 2026-10-20)

**Why.** The Standard, Muted and Dark base maps were OSM raster tiles (`ui.tiles`, OSM France HOT, the
stylesheet's darkening) — the only requests the page made to another host. They are now styles of
the same document, drawn by the same renderer from the same extract (`modern = true`: no "no modern
features", no past-landscape notice); the raster maps stay as "Standard (online)" etc.

**The data they need.** A style needs every vector layer it draws (`TerrainStyle.needs`); the extract's
metadata says what the fetch dropped whole (`Basemap.droppedLayers`: the drop list's whole layers,
none for `--keep-all`, earth/water/landcover-only for an extract without the record). The status
answers per style the layers it lacks and a hint naming `histmersion basemap fetch --layer vector
--keep-all` (or a drop list without them); the menu marks such a style "needs the full download", and
choosing it draws what is there under a notice. The default drop list is unchanged — the terrain
styles need nothing it leaves out — so the 1.3 GB default keeps working as it was; a drop list that
keeps roads, places and boundaries serves the street maps too.

What the schema holds at zoom ≤ 10 (inspected over Attica and the Aegean, `BasemapExtractTool inspect`):
roads `highway` (motorway, links) and `major_road` (trunk … tertiary, links) and `aeroway`; no
`minor_road` and no road names below zoom 11 (only `ref`/`shield_text`) — so there are no road labels;
places `country`, `region`, `locality` (city, town, village, hamlet) and `macrohood`, each with its own
`min_zoom` and `population_rank`; boundaries `country` (2), `region` (3–4), `county`, `locality`; no
`buildings` or `transit` at all. The styles draw land cover to zoom 8 and OSM land use (kinds the
style names) from 9, roads by class with casings, country and province borders, and the names of
countries, cities, towns, villages and seas in `basemap.labels.language`, placed by own `min_zoom`
then population rank (protomaps-leaflet's labeler handles collisions), in the label layer under every
marker. No town dots: the dots on the map are the corpus's.

**The source setting.** `ui.base-map-source = auto | local | online`; `/api/config` answers it
resolved. `local`: only the local styles; the online tile layer is never put on the map (the map has
its own `maxZoom`, which the cluster group needs), and a kept online choice is drawn as the local style
of the same name. `online`: the online maps first, then the local ones. `auto`: `local` once the
extract can draw the default local style (Standard) whole. The browser test records every request to
another host through every local style, a pan, a zoom and a reload: none.

**Cost.** Roads and place names made the tiles heavier where it matters (zoom 8–10). The page's cut
grew two parts: kinds (`?layers=landuse:a~forest|wood,roads:l~highway|major_road`, a rule with
`not-kinds` or no `kinds` reads the layer whole) and properties (`&keys=kind,kind_detail,…,name:en,name`:
the key and value tables rebuilt, every feature's tags renumbered, geometry copied byte for byte —
`Mvt.withProps`). The Athens tile of zoom 8: stored 94.8 KB (gzip); the paint layer's cut 70.9 KB,
with its keys 69.1 KB; the label layer's cut 7.0 KB, with its keys 2.1 KB (the names in ~100 languages
dropped).

Sizes, measured on one complete zoom-10 `--keep-all` download of the default region (2026-10-20,
1.54 GB downloaded in 6 min): stored z0–8 210 MB, z0–9 614 MB, z0–10 **1.52 GB**; the same tiles with
the default drop list 184 MB, 535 MB, **1.30 GB** — the street maps cost 0.22 GB (z10 per layer, gzip,
each alone: land use 681 MB, water 80, roads 66, places 54, boundaries 25, earth 7, pois 2).

On the native test server with the Aegean fixture (its vector layer now a `--keep-all` cut, 5.6 MB),
`perf.test.mjs`, the local maps over Athens at zoom 9 (data zoom 8, roads and names on): Standard
drawn 814 ms, no long task, pan input p95 30.6 ms, 0 % dropped, wheel zooms p95 32.7 ms, 0 %
dropped, heap 51.9 MB; Muted 812 ms, 31.6 / 47.5 ms, 0 %, 49.8 MB; Dark 815 ms, 30.0 / 32.0 ms, 0 %,
51.9 MB (budgets: drawn 3 000 ms, input 100 ms, 15 % dropped, heap 60 MB). The terrain map on the
same run: 844 ms, 32.8 ms, 0 %, 37 MB — unchanged by the broader fixture. The app's own budgets are
measured over the online base map, as they were set (`withPerfPage` tells the page `online`).

**Not done.** Road labels (the build has no road names below zoom 11, and the extract stops at 10);
buildings and transit (not in the build at these zooms); a compact credits button (the credits are
one line that expands on hover). The base-map control's shape is left to the planned two-step
family/options control: it reads `TerrainMap.localOnly`, `TerrainMap.onlineMaps`,
`TerrainMap.effective`, `TerrainMap.shown`, `TerrainMap.optionLabel` and `BasemapStatus.missing`.
## A readable map: Natural, the relief to zoom 12, the sea floor, map options (as built, 2026-10-20)

**Why.** The user, on the fetched map: "not really readable: terrain is pixelated; everything is too dark
and with too small contrast" — wanting higher resolution, pleasant water, the colours of mountains,
vegetation, ice and deserts, "all the visual clues about the terrain at first sight (e.g. that Mykenai is
a non-forested area on top of a small hill next to two other hills)", contours close in; then: names
overlapping (two lines of one name over each other), a better font, the sea floor's topography, a
language switch, and a base-map control in two steps.

**The relief to zoom 12, without downloading it all.** The extract stops at zoom 8 (0.5 GB); zoom 12 for
the default region would be ~170 GB. So (`server/basemap/TerrainOnDemand`):

- *On demand*: a terrain tile deeper than the extract is read, when a page asks for it, from Mapterhorn's
  planet file by range request — its header and root once, the 11 KB leaf directory of 4 096 tiles once,
  then one request a tile (~100 KB) — and kept in `terrain-cache/` (`DiskLru`: bytes-bounded, least
  recently used first, use = the file's mtime so the order survives a restart; a tile the provider lacks
  is remembered as `.none`). Mapterhorn documents range-request extracts (`pmtiles extract`) and a public
  tile endpoint; neither states a rate limit, so the server keeps to 2 requests at a time and 8 a second,
  reads a tile asked for twice at once only once, and after a failure asks nothing for 30 s (the page then
  draws the coarser relief: a missing deep tile falls back a zoom at a time to the extract). The page
  never asks for more than the tiles in view (Leaflet's grid), and only from zoom 9 on.
- *Focus regions*: `fetch --layer terrain --focus <name>` keeps a region's zooms 9–12 for good in
  `terrain-focus/<name>.pmtiles` (never evicted); a deep `--layer terrain --bbox` fetch becomes one
  instead of replacing the broad extract. Sizes (dry run on the directories, 2026-10-20): Aegean 0.43 GB,
  Anatolia 2.0 GB, Levant 0.36 GB, Mesopotamia 1.3 GB, Nile delta 0.05 GB, Argolid 7 MB.

**Drawn at the screen's density, smooth, seamless** (`web/Relief`, `web/ReliefLayer`). Each map tile (a
512-pixel grid) takes the deepest data tile there is and the part of it it covers; a window of it with 2
pixels around — read from the neighbouring tiles, so the gradient and the contours run on across tile
edges — is resampled bicubic (Catmull-Rom, separable) to the tile's device pixels (≤ 1024), then shaded
(Horn's gradient against up to four lights, `multidirectional`) and tinted. At the data's own zoom and
density the window is used as is. The decoded elevation (16-bit whole metres — the provider's are whole)
is kept in a 16-tile cache; a tile is computed once, when its data arrives, for both of its layers (the
tint under the vector layer, the shading over it): 10–25 ms a 512-pixel tile in headless Chromium.

**Colours.** Hypsometric tint (`relief.tint`) under translucent land cover (Protomaps' `landcover` to zoom
8, OSM `landuse` from 9 — and only read from 9: a tile subset entry may carry a zoom, `landuse:a@8`,
which the server applies per tile), the sea a soft blue lighter along the shore (`glow`: the outline
stroked inside a clip, the tile's own cuts through the polygon left out). The land's and the open sea's
fills are a third canvas layer, the *ground*, under the tint: they show only where there is no
elevation.

**The sea floor.** Mapterhorn's sea is flat (0, sometimes −1). Sources weighed: GEBCO (15″, ~450 m; free
with attribution) is published as whole-grid NetCDF/GeoTIFF (several GB) or through an interactive
subset page — mirrors as cloud-optimised GeoTIFF exist, but reading them needs a TIFF decoder in the
server and the mirrors are third parties'; Natural Earth's bathymetry polygons (public domain) give only
depth bands. Chosen: Mapzen's terrain tiles on AWS Open Data — terrarium tiles like the relief's, whose
sea is NOAA's ETOPO1 (1′, ~1.8 km, public domain): one GET a tile, zooms 0–7 of the region, ~65 KB a
tile, ~80 MB for the default region (`--layer bathymetry`). The page fills the relief's sea pixels with
their depth (bilinear from the sea-floor tile), keeps a resampled sea mask for the tint (`sea-tint`: the
shelf light with a step at ~200 m, the basins dark) and weakens the sea floor's shading (`sea-shading`);
the Contour map adds isobaths (`depth-interval`). Depressions on land (the Dead Sea) stay land: only
pixels at 0 or −1 are sea.

**Contours** (`web/Contours`): marching squares over the resampled grid, one pass, each cell visiting only
the levels between its corners; crossings computed per grid edge so neighbouring cells — and tiles —
join exactly; index lines joined into polylines by their shared edges and labelled half way along,
upright, kept from the tile's edges and from each other.

**Names that never overlap.** protomaps-leaflet's text symbolizer spaced a wrapped name's lines by the
ink height of its longest line (an all-capitals line has no descender, so the next line came closer than
a line is tall) and its collision box held neither the halo nor the lower lines' descenders.
`web/TerrainLabels` places a point name as one block — the font's ascent and descent, `line-height`, the
halo and `padding` all round — and draws exactly that; the library's labeler keeps its per-zoom index
across tiles, and repeats of a name within reach are dropped. The browser test reads the placed boxes
from that index (`window.__terrainLabelBoxes`) at zooms 5–8 over the Aegean: none intersect.

**Fonts.** Noto Serif (Latin, Greek, Cyrillic; regular, italic, semibold) and Noto Serif Armenian,
Georgian, Hebrew and Noto Naskh Arabic, woff2 subsets of @fontsource 5.3.0 (SIL OFL), 505 KB in all and
~45 KB for an English map (each subset loads by its unicode-range); loaded before the names are drawn
(a canvas never redraws for a late font). Compared: Source Serif 4 and EB Garamond cover Latin, Greek
and Cyrillic but no companions for the other scripts, EB Garamond's small x-height reads poorly at 10 px;
Libertinus adds Hebrew only.

**Map options** (`web/MapOptions`, `web/TerrainView`): the family (Histmersion map / online raster) and a
popover over the chosen preset — names and their language, relief strength, colours by height, land
cover, sea floor, contours and interval, reset — stored per viewer and per preset as one JSON value
(`terrainView`). The drawn style is the preset with the options applied (pure); a change of the names
alone redraws only the names' layer.

**Measured** (headless Chromium, the native test server, Aegean fixture, perf.test.mjs "terrain base
map", Natural the default): drawn 849 ms after the view is set, no long task; pan input p95 48 ms, 4.5 %
dropped; wheel zooms input p95 49 ms, 6.8 % dropped; map options (six changes) input p95 42 ms, no long
task, 0 % dropped; heap 33.6 MB (budget 60). The raster map's pan on the same run: p95 36 ms, 0 %
dropped. Before the zoom-scoped subsets the terrain heap was 65 MB (OSM land use decoded from zoom 6).
