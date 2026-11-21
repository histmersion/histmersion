import * as maplibregl from "/web/vendor/maplibre-gl/maplibre-gl.js";
window.maplibregl = maplibregl;
const app = document.createElement("script");
app.src = "/web/app.bundle.js" + new URL(import.meta.url).search;
document.body.append(app);
