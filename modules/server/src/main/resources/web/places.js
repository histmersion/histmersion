// Gazetteer box in the filter bar (plan §1.6a): type a place name, pick one, and the whole atlas
// narrows to it — histogram, map and result list follow through `Filter.places`, because the chip
// carries the place **id**, not its name.
//
// Why an id and not the text: an id matches every finding the server materialised INSIDE the place's
// shape plus everything below it in the hierarchy as of the selected time span, so "Asia" finds a
// Hattusa dig through Turkey. A typed name can only ever match `region_name` text.
//
// Self-contained on purpose: its own chip UI (chips.js is keyed by value = label), its own Leaflet
// layer over `MapView.map()`, its own State subscription. The rest of the UI gains three hook lines.
window.Places = (() => {
  const known = new Map();     // id -> { label, kind, contested }
  let els = {}, items = [], active = -1, timer = null, reqId = 0, ui = null;
  let layer = null, drawn = '';

  const ids = () => State.get().placeIds || [];

  /** The year the gazetteer is read at: the middle of the selected span, so a chip means the same
      thing as the results it filters (borders move; "Anatolia" does not, "Hatti" does). */
  const asOf = () => {
    const s = State.get();
    if (s.from == null && s.to == null) return null;
    if (s.from == null) return s.to;
    if (s.to == null) return s.from;
    return Math.round((Number(s.from) + Number(s.to)) / 2);
  };

  // a traced polygon may already carry "(contested)" in its own label — do not say it twice
  const flag = (contested, label) => contested && !String(label || '').toLowerCase().includes('contested');
  const mark = (c, label) =>
    (flag(c, label) ? ' <span class="contested" title="disputed extent">(contested)</span>' : '');

  // ── chips ────────────────────────────────────────────────────────────────────────────────────
  function renderChips() {
    els.list.innerHTML = ids().map((id, i) => {
      const k = known.get(id) || {};
      return `<span class="chip place${k.contested ? ' contested-chip' : ''}" title="${Fmt.esc(id)}">` +
        `${Fmt.esc(k.label || id)}${mark(k.contested, k.label)}` +
        `<button type="button" class="chip-x" data-i="${i}" aria-label="remove ${Fmt.esc(k.label || id)}">✕</button></span>`;
    }).join('');
  }

  const setIds = next => State.set({ placeIds: next });
  const add = id => { if (id && !ids().includes(id)) setIds(ids().concat([id])); close(); els.input.value = ''; };
  const removeAt = i => setIds(ids().filter((_, j) => j !== i));

  // ── suggestions ──────────────────────────────────────────────────────────────────────────────
  const close = () => { els.menu.hidden = true; els.menu.innerHTML = ''; items = []; active = -1; };

  const paint = () => {
    els.menu.innerHTML = items.map((p, i) =>
      `<li role="option" data-i="${i}" class="${i === active ? 'active' : ''}" aria-selected="${i === active}">` +
      `<span class="v">${Fmt.esc(p.label)}${mark(p.contested, p.label)}</span> ` +
      `<span class="c">${Fmt.esc(p.kind)}${p.findings ? ` · ${p.findings}` : ''}</span></li>`).join('');
  };

  const suggest = () => {
    const q = els.input.value.trim();
    if (!q) return close();
    const mine = ++reqId;
    Api.places(q, asOf(), ui.facetSuggestions).then(rows => {
      if (mine !== reqId || document.activeElement !== els.input) return;
      items = (rows || []).filter(p => !ids().includes(p.id));
      items.forEach(p => known.set(p.id, { label: p.label, kind: p.kind, contested: p.contested }));
      if (!items.length) return close();
      active = -1; els.menu.hidden = false; paint();
    }, () => close());   // gazetteer unavailable → the rest of the bar keeps working
  };
  const schedule = () => { clearTimeout(timer); timer = setTimeout(suggest, ui.debounce.suggestMs); };

  // ── the polygon overlay ──────────────────────────────────────────────────────────────────────
  /** Draw every selected place's shape. Re-fetched only when the selection or the span changed. */
  async function draw(fit) {
    const key = JSON.stringify([ids(), asOf()]);
    if (key === drawn) return;
    drawn = key;
    layer.clearLayers();
    const bounds = [];
    for (const id of ids()) {
      let d;
      try { d = await Api.place(id, asOf()); } catch (e) { continue; }
      known.set(id, { label: d.place.label, kind: d.place.kind, contested: d.contested });
      if (!d.geometry) continue;
      let geo;
      try { geo = JSON.parse(d.geometry); } catch (e) { continue; }
      // a disputed extent is drawn dashed — it is somebody's reading of a map, not a border
      const g = L.geoJSON(geo, {
        style: { color: '#c2410c', weight: 2, opacity: 0.9, fill: true, fillOpacity: 0.06,
                 dashArray: d.contested ? '6 4' : null },
        pointToLayer: (f, latlng) => L.circleMarker(latlng, { radius: ui.markerRadius + 3, color: '#c2410c', fill: false })
      });
      g.bindTooltip(d.place.label + (flag(d.contested, d.place.label) ? ' (contested)' : ''), { sticky: true });
      g.addTo(layer);
      bounds.push(g.getBounds());
    }
    renderChips();
    if (fit && bounds.length) {
      const all = bounds.reduce((a, b) => a.extend(b));
      if (all.isValid()) MapView.map().fitBounds(all, { padding: [20, 20] });
    }
  }

  // ── wiring ───────────────────────────────────────────────────────────────────────────────────
  function init(container, cfg) {
    ui = cfg;
    container.classList.add('chips', 'gazetteer');
    container.innerHTML =
      `<span class="chips-list"></span>` +
      `<input class="chip-input" type="text" id="f-place-search" autocomplete="off" spellcheck="false"` +
      ` role="combobox" aria-autocomplete="list" aria-expanded="false" aria-label="gazetteer place"` +
      ` placeholder="e.g. Anatolia" />` +
      `<ul class="chip-suggest" role="listbox" hidden></ul>`;
    els = {
      list: container.querySelector('.chips-list'),
      input: container.querySelector('.chip-input'),
      menu: container.querySelector('.chip-suggest'),
    };

    els.input.addEventListener('input', schedule);
    els.input.addEventListener('focus', () => { if (els.input.value.trim()) schedule(); });
    els.input.addEventListener('blur', () => close());
    els.input.addEventListener('keydown', e => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (els.menu.hidden) { if (els.input.value.trim()) suggest(); return; }
        e.preventDefault();
        const d = e.key === 'ArrowDown' ? 1 : -1;
        active = active < 0 ? (d > 0 ? 0 : items.length - 1) : (active + d + items.length) % items.length;
        paint();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (active >= 0) add(items[active].id);
        else if (items.length === 1) add(items[0].id);
      } else if (e.key === 'Escape') {
        if (!els.menu.hidden) { e.preventDefault(); e.stopPropagation(); close(); }
      } else if (e.key === 'Backspace' && els.input.value === '' && ids().length) {
        e.preventDefault(); removeAt(ids().length - 1);
      }
    });
    els.menu.addEventListener('mousedown', e => {
      const li = e.target.closest('li[data-i]'); if (!li) return;
      e.preventDefault(); add(items[+li.dataset.i].id);
    });
    els.menu.addEventListener('mouseover', e => {
      const li = e.target.closest('li[data-i]'); if (!li) return;
      active = +li.dataset.i; paint();
    });
    els.list.addEventListener('click', e => {
      const b = e.target.closest('.chip-x'); if (!b) return;
      removeAt(+b.dataset.i); els.input.focus();
    });

    let last = JSON.stringify(ids());
    State.subscribe(() => {
      const now = JSON.stringify(ids());
      const added = now !== last && ids().length > JSON.parse(last).length;
      last = now;
      renderChips();
      draw(added);
    });

    layer = L.layerGroup().addTo(MapView.map());
    renderChips();
    draw(true);                       // restores the overlay for chips that came from the URL hash
  }

  /** Called by filters.js after "clear all" repaints the bar. */
  const sync = () => { renderChips(); draw(false); };

  return { init, sync };
})();
