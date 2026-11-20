// MapLibre GL JS (ES modules only) on window.maplibregl, then the app's bundle: a classic script (its top-level `this`
// must be the window), its download started at once by the page's preload, run once the map library is there. A file
// of its own, not an inline script: the Content-Security-Policy runs no inline script.
import * as maplibregl from "/web/vendor/maplibre-gl/maplibre-gl.js";
window.maplibregl = maplibregl;
const app = document.createElement("script");
app.src = "/web/app.bundle.js?v=3";
document.body.append(app);
