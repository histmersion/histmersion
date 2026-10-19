# Terrain base-map fixture (the Aegean)

A tiny copy of what `histmersion basemap fetch` downloads, for the browser regressions, the perf
budgets and the documentation screenshots — a test server started with
`HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap` serves it as its terrain base map.

| file | what | region, zooms | size |
|---|---|---|---|
| `vector.pmtiles` | everything a fetch keeps by default — earth, water with its names, land cover, land use, natural points; no roads, buildings, places, boundaries or modern POIs (`basemap.vector.drop`) | 22.5,35,28.5,40.5 · 0–9 | 4.2 MB |
| `terrain.pmtiles` | terrarium-encoded elevation, 512-pixel WebP | 23,36,27,40 · 0–6 | 1.1 MB |
| `labels.pmtiles` | the physical names (Natural Earth: ranges, peninsulas, island groups, lakes, rivers, peaks) | 20,34,30,42 · 0–8 | 0.06 MB |
| `bathymetry.pmtiles` | the sea floor: terrarium PNG tiles whose sea is ETOPO1's depth | 22.5,35,28.5,40.5 · 0–7 | 1.7 MB |
| `terrain-focus/argolid.pmtiles` | a focus region: the relief around Mycenae deeper than the extract (the Natural style's contours, the close-up screenshots) | 22.48,37.61,23.03,37.85 · 9–11 | 2.3 MB |

Extracted on 2026-10-19 (the sea floor and the focus region on 2026-10-20, with `histmersion basemap
fetch --layer bathymetry --bbox 22.5,35,28.5,40.5` and `--layer terrain --focus argolid --bbox
22.48,37.61,23.03,37.85 --terrain-max-zoom 11` against a throwaway server) with the server's own extract code from the Protomaps build `20261019.pmtiles`
(`BasemapExtractTool extract 20261019.pmtiles 22.5,35,28.5,40.5 9 vector.pmtiles`), Mapterhorn's
`planet.pmtiles` (`BasemapFetch`) and Natural Earth 5.1.2's GeoJSON label layers
(`BasemapLabelsTool <dir> 20,34,30,42 8 labels.pmtiles`) — both tools in the server's test sources.

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
