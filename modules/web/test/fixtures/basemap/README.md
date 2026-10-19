# Terrain base-map fixture (the Aegean)

A tiny copy of what `histmersion basemap fetch` downloads, for the browser regressions, the perf
budgets and the documentation screenshots — a test server started with
`HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap` serves it as its terrain base map.

| file | what | region, zooms | size |
|---|---|---|---|
| `vector.pmtiles` | coast, water, rivers, land cover — thinned exactly as a fetch thins it (`basemap.vector.layers`) | 22.5,35,28.5,40.5 · 0–9 | 0.9 MB |
| `terrain.pmtiles` | terrarium-encoded elevation, 512-pixel WebP | 23,36,27,40 · 0–6 | 1.1 MB |

Extracted on 2026-10-19 with the server's own extract code (`BasemapFetch`) from the Protomaps build
`20261019.pmtiles` and Mapterhorn's `planet.pmtiles`.

Licences — the same as the full download (docs/user-guide/how-to/open-data.md):

- `vector.pmtiles`: © OpenStreetMap contributors, under the Open Database License (ODbL 1.0,
  https://opendatacommons.org/licenses/odbl/), and Natural Earth (public domain); built by Protomaps
  (https://protomaps.com). This file is a Produced Work of the OSM database.
- `terrain.pmtiles`: © Mapterhorn (https://mapterhorn.com/attribution): Copernicus GLO-30 © DLR e.V.
  2010–2014 and © Airbus Defence and Space GmbH 2014–2018, provided under COPERNICUS by the European
  Union and ESA (the Copernicus DEM licence: free use and redistribution with this attribution), and
  the other open elevation sources listed there.
