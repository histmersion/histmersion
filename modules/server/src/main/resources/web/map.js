// Leaflet map + clustered finding markers (plan §1.2). Tiles, zoom limits and the marker radius all
// come from /api/config — no literals here.
//
// Clustering rule: dots that OVERLAP ON SCREEN are one dot with a count. That is the only criterion,
// so `maxClusterRadius` is the marker DIAMETER (2 x radius) and there is no disableClusteringAtZoom
// and no spiderfy — zooming in re-separates dots the moment their circles stop touching.
// Exactly identical coordinates can never be separated by zoom, so they are pre-grouped into ONE
// "spot" marker carrying a count badge; clicking it (or a cluster) opens the sidebar picker.
window.MapView = (() => {
  let map, cluster, radius = 5;
  /** Above this many findings a cluster click zooms instead of listing (item 12). */
  const pickerMax = 15;
  /** Never zoom past this when fitting to something that has no extent — a single point's bounds
    * is a dot, and fitting it lands the reader at street level with no context (item 28). */
  const fitMaxZoom = 9;

  const midOf = pt => (pt.periodStart != null && pt.periodEnd != null)
    ? (pt.periodStart + pt.periodEnd) / 2 : null;

  /** Average period midpoint of a list of findings; null when none of them is dated. */
  const avgMid = items => {
    let sum = 0, n = 0;
    for (const pt of items) { const m = midOf(pt); if (m != null) { sum += m; n++; } }
    return n ? sum / n : null;
  };

  const colorFor = (mid, span) => {
    const lo = Math.min(span.min, span.max), hi = Math.max(span.min, span.max);
    if (mid == null) return Fmt.color(0);
    return Fmt.color(hi === lo ? 0.5 : (mid - lo) / (hi - lo));
  };

  /** One dot; `n > 1` grows it just enough to hold the count. */
  function dotIcon(n, color, extraClass) {
    const r = n > 1 ? radius + 4 + 3 * String(n).length : radius;
    const d = 2 * r;
    const label = n > 1 ? String(n) : '';
    return L.divIcon({
      className: 'hd-dot-wrap',
      iconSize: [d, d],
      iconAnchor: [r, r],
      html: `<span class="hd-dot ${extraClass || ''}" style="background:${color};width:${d}px;`
        + `height:${d}px;line-height:${d - 1}px;font-size:${Math.min(12, r)}px">${label}</span>`
    });
  }

  // The citekey rides in a `data-ck` attribute, read back by the delegated listener in init().
  // It used to be spliced into an inline click handler attribute, which mangled the legal
  // `-` and `.` characters (they had to be stripped to keep the JS string literal valid).
  const popupHtml = pt =>
    `<b>${Fmt.esc(pt.citekey)}</b> — ${Fmt.esc(pt.title)}<br><i>${Fmt.periodStr(pt.periodStart, pt.periodEnd)}</i><br>${Fmt.esc(pt.claim)}<br>`
    + `<a href="#" data-ck="${Fmt.esc(pt.citekey)}">read source</a>`
    + ` · <a href="/source/${Fmt.esc(encodeURIComponent(pt.citekey))}" target="_blank">new tab ↗</a>`;

  const coordStr = (lat, lon) =>
    `${Math.abs(lat).toFixed(2)}°${lat < 0 ? 'S' : 'N'}, ${Math.abs(lon).toFixed(2)}°${lon < 0 ? 'W' : 'E'}`;

  function init(ui) {
    radius = ui.markerRadius;
    map = L.map('map', { worldCopyJump: true }).setView([37, 30], 5);
    L.tileLayer(ui.tiles, { attribution: '© OpenStreetMap', maxZoom: ui.tilesMaxZoom, opacity: 0.85 }).addTo(map);
    cluster = L.markerClusterGroup({
      maxClusterRadius: 2 * radius, // overlap of the drawn circles is the ONLY clustering criterion
      spiderfyOnMaxZoom: false,
      zoomToBoundsOnClick: false,
      showCoverageOnHover: false,
      iconCreateFunction: c => {
        const kids = c.getAllChildMarkers();
        let n = 0, sum = 0, dated = 0;
        for (const m of kids) {
          n += m.hdItems.length;
          const mid = avgMid(m.hdItems);
          if (mid != null) { sum += mid * m.hdItems.length; dated += m.hdItems.length; }
        }
        return dotIcon(n, colorFor(dated ? sum / dated : null, cluster.hdSpan), 'hd-cluster');
      }
    });
    // A picker of 60 rows is not a picker (item 12). Past `pickerMax` the useful move is almost
    // always "get closer", so a big cluster zooms to its own bounds; below that it opens the
    // picker, which offers "zoom to cluster" as the other way out.
    cluster.on('clusterclick', e => {
      const items = e.layer.getAllChildMarkers().flatMap(m => m.hdItems);
      const bounds = e.layer.getBounds();
      const sources = new Set(items.map(i => i.citekey)).size;
      if (items.length > pickerMax) { map.fitBounds(bounds, { padding: [40, 40], maxZoom: fitMaxZoom }); return; }
      Results.pick(items, {
        title: `${items.length} finding${items.length === 1 ? '' : 's'} from ` +
               `${sources} source${sources === 1 ? '' : 's'} in this cluster`,
        onZoom: () => map.fitBounds(bounds, { padding: [40, 40], maxZoom: fitMaxZoom }),
      });
    });
    cluster.hdSpan = { min: 0, max: 1 };
    cluster.addTo(map);
    addLegend();
    // Delegated: Leaflet rebuilds a popup's DOM on every open, so binding per popup would leak
    // handlers and miss re-renders. One listener on the map container covers all of them.
    map.getContainer().addEventListener('click', e => {
      const a = e.target.closest && e.target.closest('[data-ck]');
      if (!a) return;
      e.preventDefault();
      Article.open(a.dataset.ck);
    });
  }

  /** The dots and the timeline bars share ONE colour ramp, and until now nothing said what it meant
    * (items 11/27). A ten-stop strip in the corner, labelled with the ends of the current window. */
  let legend = null;
  function addLegend() {
    const L2 = L.control({ position: 'bottomright' });
    L2.onAdd = () => {
      legend = L.DomUtil.create('div', 'hd-legend');
      legend.innerHTML = `<div class="hd-ramp">` +
        Array.from({ length: 10 }, (_, i) => `<i style="background:${Fmt.color(i / 9)}"></i>`).join('') +
        `</div><div class="hd-ramp-labels"><span class="lo"></span>` +
        `<span class="mid">older ← → newer</span><span class="hi"></span></div>`;
      L.DomEvent.disableClickPropagation(legend);
      return legend;
    };
    L2.addTo(map);
  }

  function legendSpan(span) {
    if (!legend) return;
    const lo = legend.querySelector('.lo'), hi = legend.querySelector('.hi');
    if (lo) lo.textContent = Fmt.yr(Math.min(span.min, span.max));
    if (hi) hi.textContent = Fmt.yr(Math.max(span.min, span.max));
  }

  /** `span` is the CURRENT time window ({min,max}), so the colour ramp is meaningful for it. */
  function render(points, span) {
    cluster.hdSpan = span;
    legendSpan(span);
    cluster.clearLayers();
    // Pre-group exactly identical coordinates: no zoom level can ever pull them apart.
    const spots = new Map();
    for (const pt of points) {
      if (pt.lat == null || pt.lon == null) continue;
      const key = pt.lat + '|' + pt.lon;
      let sp = spots.get(key);
      if (!sp) { sp = { lat: pt.lat, lon: pt.lon, items: [] }; spots.set(key, sp); }
      sp.items.push(pt);
    }
    const markers = [];
    for (const sp of spots.values()) {
      const n = sp.items.length;
      const m = L.marker([sp.lat, sp.lon], {
        icon: dotIcon(n, colorFor(avgMid(sp.items), span), n > 1 ? 'hd-coincident' : ''),
        title: n > 1 ? `${n} findings here` : sp.items[0].citekey
      });
      m.hdItems = sp.items;
      if (n === 1) m.bindPopup(popupHtml(sp.items[0]));
      else m.on('click', () => Results.pick(sp.items, {
        title: `${n} findings at ${coordStr(sp.lat, sp.lon)}`,
        onZoom: () => map.setView([sp.lat, sp.lon], Math.max(map.getZoom(), fitMaxZoom)),
      }));
      markers.push(m);
    }
    cluster.addLayers(markers);
  }

  return { init, render, map: () => map };
})();
