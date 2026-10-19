# The terrain base map (2026-10-19)

A base map of MODERN terrain only — relief, coastlines, seas, lakes, rivers, natural land cover —
with no modern roads, towns, place names, borders or buildings, drawn by the web page from data the
server downloads once. There is no reliable worldwide source of how coastlines, rivers, lakes and
vegetation looked in a given century, so the map is today's landscape with a notice saying so
whenever it is shown (user guide: exploring/web-ui.md, "The terrain map").

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

On the test server with the Aegean fixture (perf.test.mjs, "terrain base map"): drawn 870 ms after
the view is set, no long task; drag input p95 32–35 ms, 0 % dropped; wheel zooms 0 long tasks,
0.6 % dropped; heap 36 MB.

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

`basemap.vector.layers`: `earth`; `water` without canals, ditches, drains, docks, pools, fountains
and basins; `landcover` forest, grassland, scrub, barren, glacier (not farmland or urban areas). The
thinning keeps or drops whole layers and features by their `kind` without decoding geometry
(`server/basemap/Mvt`), so what is kept is byte-identical to the source.

OSM's `landuse` layer was measured and dropped from the default: at zoom 7–10 it is 80–90 % of the
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
