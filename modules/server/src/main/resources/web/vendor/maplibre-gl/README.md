MapLibre GL JS 6.12.0 (BSD-3-Clause, LICENSE.txt), the npm package's `dist/` ES modules: `maplibre-gl.mjs`,
`maplibre-gl-shared.mjs`, `maplibre-gl-worker.mjs` and `maplibre-gl.css`, renamed to `.js` (the server's
static files are typed by extension, and a module script must be served as JavaScript) with the three
names inside them changed to match. Nothing else is changed. `web/index.html` imports `maplibre-gl.js`
and puts it on `window.maplibregl`; it starts its worker from `maplibre-gl-worker.js` beside it.
