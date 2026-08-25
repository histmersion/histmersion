// Leaflet map + finding markers. Tiles and zoom limits come from /api/config (no literals here).
window.MapView = (() => {
  let map, layer;
  function init(ui) {
    map = L.map('map', { worldCopyJump: true }).setView([37, 30], 5);
    L.tileLayer(ui.tiles, { attribution: '© OpenStreetMap', maxZoom: ui.tilesMaxZoom, opacity: 0.85 }).addTo(map);
    layer = L.layerGroup().addTo(map);
  }
  function render(points, span) {
    layer.clearLayers();
    for (const pt of points) {
      if (pt.lat == null || pt.lon == null) continue;
      const mid = (pt.periodStart != null && pt.periodEnd != null) ? (pt.periodStart + pt.periodEnd) / 2 : span.min;
      const c = Fmt.color((mid - span.min) / (span.max - span.min));
      L.circleMarker([pt.lat, pt.lon], { radius: 5, color: '#000', weight: 0.5, fillColor: c, fillOpacity: 0.85 })
        .bindPopup(`<b>${Fmt.esc(pt.citekey)}</b> — ${Fmt.esc(pt.title)}<br><i>${Fmt.periodStr(pt.periodStart, pt.periodEnd)}</i><br>${Fmt.esc(pt.claim)}<br>`
          + `<a href="#" onclick="openArticle('${pt.citekey}');return false;">open article</a> · <a href="/source/${pt.citekey}" target="_blank">new tab ↗</a>`)
        .addTo(layer);
    }
  }
  return { init, render, map: () => map };
})();
