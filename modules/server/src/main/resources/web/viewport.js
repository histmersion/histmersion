// "only papers in view" toggle (plan §1.1): mirrors the Leaflet viewport into State.bbox, so the
// server-side filter and the shareable URL hash always agree with what the user is looking at.
window.Viewport = (() => {
  const R5 = x => Math.round(x * 1e5) / 1e5;
  // Longitudes come out of Leaflet unwrapped (panning east of the antimeridian yields 190, 550…).
  const wrapLon = x => {
    const y = ((x + 180) % 360 + 360) % 360 - 180;
    return (y === -180 && x > 0) ? 180 : y; // keep a true eastern edge at +180
  };

  /** Map bounds as [minLon, minLat, maxLon, maxLat], normalised to ±180/±90.
    * A viewport straddling the antimeridian comes back with minLon > maxLon; the server splits it
    * into two envelopes (stream 1.1). A viewport wider than the world becomes the whole world. */
  function bboxOf(map) {
    const b = map.getBounds();
    const w = b.getWest(), e = b.getEast();
    const s = Math.max(-90, b.getSouth()), n = Math.min(90, b.getNorth());
    const lons = (e - w >= 360) ? [-180, 180] : [wrapLon(w), wrapLon(e)];
    return [lons[0], s, lons[1], n].map(R5);
  }

  function init(container, map, ui) {
    // "source" everywhere (item 18): the sidebar lists sources, the map draws their findings, and
    // this checkbox filters the same sources — "papers" was a fourth word for the same thing.
    container.innerHTML = '<label class="check"><input type="checkbox" id="inview" />'
      + '<span>only sources in view</span></label>';
    const box = container.querySelector('#inview');
    let timer = null;

    const push = () => State.set({ bbox: bboxOf(map).join(',') });
    const schedule = () => {
      if (!box.checked) return;
      clearTimeout(timer);
      timer = setTimeout(push, ui.viewportDebounceMs);
    };
    map.on('moveend zoomend', schedule);

    box.addEventListener('change', () => {
      clearTimeout(timer);
      if (box.checked) State.set({ inView: true, bbox: bboxOf(map).join(',') });
      else State.set({ inView: false, bbox: null });
    });

    // Restore from the hash: the map is already built, so we can frame the saved viewport.
    const s = State.get();
    if (s.inView) {
      box.checked = true;
      const bb = (s.bbox || '').split(',').map(Number);
      if (bb.length === 4 && bb.every(Number.isFinite)) {
        // minLon > maxLon means the saved viewport crossed the antimeridian. Leaflet needs a
        // monotonically increasing east bound, so unwrap it past +180 instead of dropping the bbox.
        const east = bb[0] > bb[2] ? bb[2] + 360 : bb[2];
        map.fitBounds([[bb[1], bb[0]], [bb[3], east]]); // fires moveend → re-pushes the exact bbox
      } else {
        push();
      }
    }
  }

  return { init, bboxOf };
})();
