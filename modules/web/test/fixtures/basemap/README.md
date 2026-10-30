# Terrain base-map fixture (the Aegean)

A tiny copy of what `histmersion basemap fetch` downloads, for the browser regressions, the perf
budgets and the documentation screenshots — a test server started with
`HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap` serves it as its terrain base map.

| file | what | region, zooms | size |
|---|---|---|---|
| `vector.pmtiles` | everything, unfiltered (a `--keep-all` extract): earth, water with its names, land cover, land use, natural points, and the roads, places and boundaries the local street maps draw | 22.5,35,28.5,40.5 · 0–9 | 5.6 MB |
| `terrain.pmtiles` | terrarium-encoded elevation, 512-pixel WebP — the vector layer's whole region (re-cut 2026-10-20: `basemapExtract relief 22.5,35,28.5,40.5 0 6`) | 22.5,35,28.5,40.5 · 0–6 | 1.4 MB |
| `labels.pmtiles` | the physical names (Natural Earth: ranges, peninsulas, island groups, lakes, rivers, peaks) | 20,34,30,42 · 0–8 | 0.06 MB |
| `bathymetry.pmtiles` | the sea floor: terrarium PNG tiles whose sea is ETOPO1's depth | 22.5,35,28.5,40.5 · 0–7 | 1.7 MB |
| `terrain-focus/argolid.pmtiles` | a focus region: the relief around Mycenae deeper than the extract (the Natural style's contours, the close-up screenshots) | 22.48,37.61,23.03,37.85 · 9–11 | 2.3 MB |

The vector layer was cut on 2026-10-20 from a complete `--keep-all` zoom-10 extract of the default
region (Protomaps' newest build that day; `basemapExtract extract latest -12,20,90,62 10 v.pmtiles
keep-all`, then `basemapExtract subset v.pmtiles 22.5,35,28.5,40.5 9 vector.pmtiles`); the
terrain on 2026-10-19 from Mapterhorn's `planet.pmtiles` (`BasemapFetch`), the names from Natural Earth
5.1.2's GeoJSON label layers (`basemapLabels <dir> 20,34,30,42 8 labels.pmtiles`) — the tools are
in the server's test sources. The sea floor and the focus region on 2026-10-20, with `histmersion basemap
fetch --layer bathymetry --bbox 22.5,35,28.5,40.5` and `--layer terrain --focus argolid --bbox
22.48,37.61,23.03,37.85 --terrain-max-zoom 11` against a throwaway server. A test server serving it
resolves `ui.base-map-source = auto` to local.

Licences — the same as the full download (docs/user-guide/how-to/open-data.md):

- `vector.pmtiles`: © OpenStreetMap contributors, under the Open Database License (ODbL 1.0,
  https://opendatacommons.org/licenses/odbl/), and Natural Earth (public domain); built by Protomaps
  (https://protomaps.com). This file is a Produced Work of the OSM database.
- `labels.pmtiles`: Natural Earth (https://www.naturalearthdata.com), public domain.
- `bathymetry.pmtiles`: Mapzen's terrain tiles (https://registry.opendata.aws/terrain-tiles/); the sea is
  ETOPO1 (NOAA, https://www.ncei.noaa.gov/products/etopo-global-relief-model), a U.S. Government work in
  the public domain — not to be used for navigation; the land in them (unused) SRTM and GMTED2010, courtesy
  of the U.S. Geological Survey.
- `terrain-focus/argolid.pmtiles`: as `terrain.pmtiles` (Mapterhorn, Copernicus GLO-30).
- `terrain.pmtiles`: © Mapterhorn (https://mapterhorn.com/attribution): Copernicus GLO-30 © DLR e.V.
  2010–2014 and © Airbus Defence and Space GmbH 2014–2018, provided under COPERNICUS by the European
  Union and ESA (the Copernicus DEM licence: free use and redistribution with this attribution), and
  the other open elevation sources listed there.
