// MapLibre GL JS (ES modules only) on window.maplibregl, then the app's bundle: a classic script (its top-level `this`
// must be the window), its download started at once by the page's preload, run once the map library is there. A file
// of its own, not an inline script: the Content-Security-Policy runs no inline script. The bundle's address takes the
// query this file was loaded with (`?v=<build>`, written by the server into index.html): the same address as the
// preload, so the download it started is the one that runs.
import * as maplibregl from "/web/vendor/maplibre-gl/maplibre-gl.js";
window.maplibregl = maplibregl;
const app = document.createElement("script");
app.src = "/web/app.bundle.js" + new URL(import.meta.url).search;
document.body.append(app);
