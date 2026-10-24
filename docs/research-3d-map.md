# A 3D map view: which library (#3644, 2026-10-24)

The question: recent showcase projects — several built with Claude or for agents — draw a 3D globe with
live data on top. Which library do they use, and which one fits an optional 3D view for Histmersion
(terrain, with the corpus's findings, events and boundaries over time on it), behind the map abstraction
the web UI already has (`web/map/MapSurface`, MapLibre GL behind it)? Research only: no code.

## 1. What the showcase projects use

| project | renderer | base | on top | notes |
|---|---|---|---|---|
| **God's Eye View** (Bilawal Sidhu; the viral "WorldView" series; MIT, released 2026-08-24; ~59 k stars on 2026-10-24) | **CesiumJS** + Vite, plain JavaScript | **Google Photorealistic 3D Tiles**; Cesium ion world terrain; Esri and OSM imagery | aircraft (OpenSky / ADS-B Exchange), ships (AIS), satellites (CelesTrak), earthquakes (USGS), public cameras — one module a layer | its README names Claude Desktop, Codex and ChatGPT as **MCP clients** that steer the globe; it does not say which tools built it |
| its many clones on GitHub's `3d-globe` topic (`kevtoe/worldview`, `godseye`, `SIGINT`, `ARGUS`, …) | CesiumJS (some with three.js) | Google 3D Tiles or Cesium ion | the same kinds of live feeds | |
| **cesium-mcp** (`gaopengbin/cesium-mcp`; topics `claude`, `mcp`, `ai-agent`) | CesiumJS | — | — | a runtime that lets an MCP client (Claude) drive a Cesium globe |
| "Build a 3D weather globe with Claude Code" (Daily Dose of Data, a one-prompt build) | **three.js** (Next.js) | NASA imagery on a sphere | weather, a day/night terminator, a time slider | a globe, not a map: no terrain, no tiles |
| "Recreating the Stripe / GitHub globe using Claude Code" (Ron Reiter) | three.js | a dotted sphere | arcs | the author notes the model could not make the world bitmap itself |
| World Monitor (an OSINT dashboard) | globe.gl (three.js) for the globe; **deck.gl + MapLibre GL** for the flat map | | | the common production split: MapLibre for the base map, deck.gl for heavy analytical layers |

**Answer.** The recent viral showcases — the God's Eye View lineage, which is where the "3D map with data
on top" look comes from — use **CesiumJS with Google Photorealistic 3D Tiles**. Claude's part in them,
where it can be shown, is as the **agent that steers the globe over MCP**, not a claim about who wrote the
code. The smaller Claude Code tutorials use three.js globes. None of the projects found uses MapLibre's own
3D or deck.gl for the 3D globe itself.

## 2. The candidates for Histmersion

What our case needs, and what any choice is measured against:

- **Offline first.** Everything the page draws comes from this server: the vector extract (PMTiles, MVT),
  the relief (Terrarium elevation tiles, the extract to zoom 8, focus regions and the on-demand relief to
  zoom 12), the sea floor (Terrarium, ETOPO1 or GEBCO), the physical names; no tile service, no key.
- **Our styles and our data.** Readable and Physical are MapLibre style documents over the vector
  extract; the overlays (findings and events as points and clusters, place glyphs, the polities' outlines
  for a year or a range, playback) are MapLibre sources and layers behind `MapSurface`.
- **The budgets** (`perf.test.mjs`): input → paint p95 ≤ 100 ms, ≤ 15 % dropped frames, longest task
  ≤ 150 ms, heap ≤ 60 MB; a no-GPU fallback that keeps the page usable.
- **Licences** we can ship in the bundle (`web/vendor/`), and a bundle that stays small (the app bundle is
  15.7 MB raw / 1.77 MB gzipped after #4036; MapLibre is 1.1 MB raw / ~0.3 MB gzipped).

| | **MapLibre GL's own 3D** (terrain, globe, sky, fill-extrusion) | **deck.gl** (with MapLibre, interleaved) | **CesiumJS** | **Google Photorealistic 3D Tiles** | **three.js** (globe.gl, Threebox) |
|---|---|---|---|---|---|
| licence | BSD-3 (vendored: 6.12.0) | MIT (9.4.0) | Apache-2.0 (1.146.0) | Google Maps Platform terms, API key | MIT |
| added to the page (min / gzip, measured 2026-10-24) | **0** — `setTerrain`, `setProjection` (globe), `setSky`, `fill-extrusion` are in the vendored build | `dist.min.js` 2.07 MB / 0.57 MB | `Cesium.js` 6.08 MB / 1.77 MB, plus 22.8 MB of workers, assets and widgets in `Build/Cesium/` | the tiles are online only | globe.gl 1.89 MB / 0.52 MB (with three.js) |
| our relief | **as is**: a `raster-dem` source with `encoding: terrarium` is what terrain reads | `TerrainLayer` decodes Terrarium tiles into meshes (loaders.gl); or MapLibre's terrain under deck's layers | no Terrarium provider: a `CustomHeightmapTerrainProvider` fed by our PNGs (decoded in JS), or a quantized-mesh build of the relief | its own, global, not ours | a mesh per tile, written by us |
| our vector styles | **as is** (draped over the terrain) | MapLibre draws them (interleaved) | **no**: Cesium draws imagery, not MVT styles (third-party MVT-to-canvas providers exist) — the styles would have to be rasterised server-side | no | no |
| our overlays | as is: circles, symbols, GeoJSON fills and lines are draped or placed on the terrain; markers are MapLibre's | MapLibre's stay; deck adds 3D-native layers (arcs, columns, point clouds, trips) | rewritten as Cesium entities or primitives | — | rewritten |
| offline | yes | yes | yes with our own terrain and imagery; ion and Google are online | **no**: the terms forbid pre-fetching, storing or caching the content, and offline use | yes |
| behind `MapSurface` | a mode of the existing engine: pitch, bearing, terrain on, globe on | an overlay inside the existing engine (`@deck.gl/mapbox` `MapboxOverlay`, interleaved) | a second engine and a second implementation of every overlay | — | a second engine |
| no GPU | 3D off, as the relief is off today | off | unusable | unusable | unusable |

## 3. Recommendation

**MapLibre GL's own 3D, as an optional mode of the engine we have.** It reads the relief we already serve
(the Terrarium `raster-dem` source, the extract plus the focus regions and the on-demand relief to zoom 12),
draws Readable and Physical draped over it unchanged, keeps every overlay (they are MapLibre layers
already), stays offline, and adds nothing to the bundle. The globe projection gives the "whole planet"
view at low zooms; `fill-extrusion` can raise areas where an extrusion means something (an event's
magnitude, a polity's period) rather than for show.

- **deck.gl**, later and only for a layer MapLibre cannot draw well — great-circle arcs between linked
  places, columns, a dense point cloud of findings over a long period. It sits inside MapLibre (interleaved),
  so it is an addition to the engine, not a second one, at ~0.57 MB gzipped loaded on first use.
- **Not CesiumJS**: 1.77 MB gzipped of script and ~23 MB of assets, no way to reuse our vector styles,
  every overlay written a second time, and its showcase look depends on data we cannot use offline.
- **Not Google Photorealistic 3D Tiles**: an API key and billing, online only, and the terms forbid caching
  or storing the tiles — the opposite of offline first. Photogrammetry of today's cities also says little
  about the Bronze Age.
- **Not three.js**: a globe and nothing else; tiles, terrain and styles would all be ours to write.

## 4. What the MapLibre mode would take (an outline, for when it is scheduled)

1. `MapSurface` gains a view mode: `flat | terrain | globe`, with pitch and bearing; the engine maps it to
   `setTerrain({ source, exaggeration })`, `setProjection({ type: 'globe' })` and `setSky`. A second
   `raster-dem` source for the terrain (MapLibre's own 3D-terrain example gives hillshade and terrain a
   source each), pointing at the same `/api/basemap/terrain/{z}/{x}/{y}` URLs. Off without a GPU.
2. The overlays: check each MapLibre layer on terrain (circles and symbols set to `map` pitch alignment
   where they should lie on the ground), the HTML markers' positions (MapLibre places them on the terrain),
   the polities' fills (draped), the hover hit-testing (`queryRenderedFeatures` works in 3D).
3. Budgets: add the 3D mode to `perf.test.mjs` (drag, rotate, pitch, wheel zooms over the Argolid with
   the full overlay load) and hold it to the same input and frame budgets; terrain roughly doubles the DEM
   tiles a view asks for, so the on-demand relief's politeness limits matter more.
4. The UI: a 3D toggle in the map's corner, the pitch and bearing in the URL state like the view.

Effort, as far as it can be judged without a spike: days, not weeks — most of it the overlay checks and the
budget suite; the engine work is a few calls.

## Unresolved

- How MapLibre 6.12's terrain behaves with our on-demand relief (deep tiles read from the provider as a
  view asks for them) under the pan budget: a spike with the perf paths answers it.
- Whether the globe projection and the polities' range view (1 600 shapes) stay within the budgets.
- Which tools built God's Eye View is not stated by its author in what was found; the project's link to
  Claude here is its MCP control surface.

## Sources (read 2026-10-24)

- God's Eye View: <https://github.com/bilawalsidhu/gods-eye-view> (README: stack, MCP clients, licence)
- GitHub topic `3d-globe`: <https://github.com/topics/3d-globe> (stars, renderers; `cesium-mcp`)
- cesium-mcp: <https://github.com/gaopengbin/cesium-mcp>
- Build a 3D weather globe with Claude Code: <https://blog.dailydoseofds.com/p/hands-on-build-a-3d-weather-globe>
- Recreating the Stripe / GitHub globe using Claude Code: <https://ronreiter.medium.com/recreating-the-stripe-github-globe-using-claude-code-137216ed18e9>
- Mapping libraries, a practical comparison (MapLibre + deck.gl pairing; Cesium for globes and 3D Tiles): <https://giscarta.com/blog/mapping-libraries-a-practical-comparison>
- Google Map Tiles API policies (caching, offline use, attribution): <https://developers.google.com/maps/documentation/tile/policies>
- Versions and licences: `npm view cesium|deck.gl|three|globe.gl|maplibre-gl`; minified sizes measured from unpkg
  (`cesium@1.146.0/Build/Cesium/Cesium.js`, `deck.gl@9.4.0/dist.min.js`, `globe.gl@2.46.2/dist/globe.gl.min.js`);
  the vendored MapLibre is `web/vendor/maplibre-gl` 6.12.0.
