# Map-style study prototype (not shipped)

The two prototype base-map styles of `docs/research-map-styles.md` — *Readable* and *Physical* — as MapLibre
GL JS 6 style documents over a Histmersion server's own base-map tiles, and the tools that measured them.

| file | what |
|---|---|
| `styles.mjs` | both styles, built from the server's `/api/basemap` answer (pure; `styles.test.mjs`) |
| `index.html` | the page: native MapLibre, or MapLibre inside Leaflet (`mode=leaflet`); `font-faces`; the `hmdem://` elevation protocol |
| `ref.html` | OSM Carto's public raster at the same view (a handful of screenshots, internal comparison only) |
| `serve.mjs` | serves this directory and passes `/api/*`, `/web/*` through to a Histmersion server (same origin, no other host) |
| `perf.mjs` | the renderer measurement (§3.2) |
| `diag-labels.mjs` | the cut-off-names check against protomaps-leaflet's label index (§2) |

```bash
npm ci --prefix spike/map-styles
node --test spike/map-styles/styles.test.mjs
# a throwaway server with an extract (modules/web/test/README.md), then:
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 node spike/map-styles/serve.mjs 18500
open 'http://127.0.0.1:18500/?style=physical&lat=37.73&lon=22.76&z=12'
node spike/map-styles/perf.mjs http://127.0.0.1:18080 http://127.0.0.1:18500 --gpu
```

Licences: MapLibre GL JS, maplibre-gl-leaflet, maplibre-contour BSD-3; Leaflet BSD-2; Noto Sans (@fontsource)
SIL OFL 1.1 — installed by npm, not vendored here.
