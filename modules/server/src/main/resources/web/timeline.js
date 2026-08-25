// Timeline strip (plan §1.7). The VISIBLE SPAN *IS* THE FILTER: whatever the strip shows is the
// period sent to the server, so there is no separate selection handle. Bars = how many sources match
// each adaptive time bucket (/api/aggregate?by=time&unit=sources).
//   drag  → zoom IN to the dragged span (min 10 yrs)
//   wheel → ×2 out / ÷2 in around the year under the cursor (never scrolls the page)
//   + −   → ÷2 in / ×2 out around the centre;  ⟲ → back to the configured default span
// Everything is clamped to ui.time.min/max and pushed to State.from/to, which app.js/map.js follow.
window.Timeline = (() => {
  const H = 58, BASE = 42, MIN_SPAN = 10, ZOOM = 2;   // px; BASE = y of the axis inside the SVG

  let limits, svg, info, fromBox, toBox;
  let span = { from: 0, to: 0 };
  let bars = [], bucket = null, loading = false, drag = null, reqId = 0, timer = null;

  // ── span algebra (pure-ish: only reads `limits`) ─────────────────────────────────────────────
  const clamp = (from, to) => {
    let a = Math.round(Math.min(from, to)), b = Math.round(Math.max(from, to));
    if (b - a < MIN_SPAN) { const mid = (a + b) / 2; a = Math.round(mid - MIN_SPAN / 2); b = a + MIN_SPAN; }
    const width = Math.min(b - a, limits.max - limits.min);
    if (a < limits.min) { a = limits.min; b = a + width; }
    if (b > limits.max) { b = limits.max; a = b - width; }
    return { from: a, to: Math.max(a + MIN_SPAN, b) };
  };
  // scale the span by `f` keeping the year `anchor` under the same spot
  const zoom = (f, anchor) => {
    const a = anchor == null ? (span.from + span.to) / 2 : anchor;
    return clamp(a - (a - span.from) * f, a + (span.to - a) * f);
  };

  function setSpan(from, to, push = true) {
    const next = clamp(from, to);
    if (next.from === span.from && next.to === span.to) return;
    span = next;
    syncBoxes(); render(); schedule();
    if (push) State.set({ from: span.from, to: span.to });
  }

  // ── data ────────────────────────────────────────────────────────────────────────────────────
  const schedule = () => { clearTimeout(timer); timer = setTimeout(load, 150); };
  async function load() {
    const id = ++reqId;
    loading = true; render();
    try {
      // The whole current filter, not just the span: the bars must count the SAME sources the
      // sidebar lists (tree.js builds its params the same way).
      const h = await Api.aggregate({
        ...State.toApiParams(),
        by: 'time', unit: 'sources', from: span.from, to: span.to,
      });
      if (id !== reqId) return;                       // a newer span won the race
      bars = h.buckets || []; bucket = h.bucket;
    } catch (e) {
      if (id === reqId) { bars = []; bucket = null; }
    } finally {
      if (id === reqId) { loading = false; render(); }
    }
  }

  // ── drawing ────────────────────────────────────────────────────────────────────────────────
  const width = () => svg.clientWidth || svg.getBoundingClientRect().width || 600;
  // The axis covers [from, to+1): every year owns a slice, so the LAST bucket (which starts on
  // `to`) still gets drawn instead of collapsing to a zero-width sliver at the right edge.
  const years = () => span.to + 1 - span.from;
  const xOf = y => (y - span.from) / years() * width();
  const yearAt = px => span.from + (px / width()) * years();
  const el = (name, attrs, text) => {
    const n = document.createElementNS('http://www.w3.org/2000/svg', name);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
    if (text != null) n.textContent = text;
    return n;
  };

  function render() {
    const w = width();
    svg.setAttribute('viewBox', `0 0 ${w} ${H}`);
    svg.classList.toggle('tl-loading', loading);
    while (svg.firstChild) svg.removeChild(svg.firstChild);

    const max = bars.reduce((m, b) => Math.max(m, b.count), 0);
    for (const b of bars) {
      const x0 = Math.max(0, xOf(b.start)), x1 = Math.min(w, xOf(b.end + 1));
      if (x1 <= x0) continue;
      const h = max === 0 ? 0 : Math.max(b.count > 0 ? 2 : 0, (b.count / max) * (BASE - 6));
      const mid = (b.start + b.end) / 2;
      const rect = el('rect', {
        class: 'tl-bar', x: x0, y: BASE - h, width: Math.max(1, x1 - x0 - 0.5), height: h,
        fill: Fmt.color((mid - limits.min) / (limits.max - limits.min)),
      });
      rect.appendChild(el('title', {}, `${Fmt.yr(b.start)}–${Fmt.yr(b.end)}: ${b.count} source(s)`));
      svg.appendChild(rect);
    }
    svg.appendChild(el('line', { class: 'tl-axis', x1: 0, y1: BASE + 0.5, x2: w, y2: BASE + 0.5 }));

    // ticks: aim for ~6 labels, always on bucket boundaries so labels line up with the bars
    if (bars.length) {
      const every = Math.ceil(bars.length / 6);
      bars.forEach((b, i) => {
        if (i % every) return;
        const x = xOf(b.start);
        if (x < 0 || x > w - 4) return;
        svg.appendChild(el('line', { class: 'tl-tick', x1: x, y1: BASE, x2: x, y2: BASE + 4 }));
        svg.appendChild(el('text', { class: 'tl-label', x: Math.min(x + 3, w - 2), y: H - 2 }, Fmt.yr(b.start)));
      });
    }
    svg.appendChild(el('text', { class: 'tl-label tl-label-end', x: w - 2, y: H - 2 }, Fmt.yr(span.to)));

    if (drag) {
      const x0 = Math.min(drag.x, drag.cur), x1 = Math.max(drag.x, drag.cur);
      svg.appendChild(el('rect', { class: 'tl-drag', x: x0, y: 0, width: Math.max(1, x1 - x0), height: BASE }));
    }

    info.textContent = bucket == null
      ? (loading ? '…' : 'no data')
      : `${bucket}-yr buckets · peak ${max} source(s)`;
  }

  const syncBoxes = () => {
    if (document.activeElement !== fromBox) fromBox.value = Fmt.yr(span.from);
    if (document.activeElement !== toBox) toBox.value = Fmt.yr(span.to);
    fromBox.classList.remove('bad'); toBox.classList.remove('bad');
  };

  // ── wiring ──────────────────────────────────────────────────────────────────────────────────
  function init(container, time) {
    limits = time;
    container.innerHTML = `
      <div class="tl">
        <div class="tl-ctl">
          <label for="tl-from">span</label>
          <input class="tl-year" id="tl-from" title="from — e.g. 3300 BCE, -3300, c. 1500 BC" />
          <span class="tl-arrow">→</span>
          <input class="tl-year" id="tl-to" title="to — e.g. 1200 CE, -1200" />
          <button class="tl-btn" id="tl-in" title="zoom in ×2 (around the centre)">+</button>
          <button class="tl-btn" id="tl-out" title="zoom out ×2 (around the centre)">−</button>
          <button class="tl-btn" id="tl-reset" title="reset to the default span">⟲</button>
          <span class="tl-info muted" id="tl-info"></span>
          <span class="tl-hint muted">drag to zoom in · wheel to zoom</span>
        </div>
        <svg class="tl-svg" id="tl-svg" preserveAspectRatio="none" height="${H}"></svg>
      </div>`;
    svg = container.querySelector('#tl-svg');
    info = container.querySelector('#tl-info');
    fromBox = container.querySelector('#tl-from');
    toBox = container.querySelector('#tl-to');

    const s = State.get();
    span = clamp(s.from ?? time.defaultFrom, s.to ?? time.defaultTo);
    if (span.from !== s.from || span.to !== s.to) State.set({ from: span.from, to: span.to });
    syncBoxes();

    // typed boxes: parse on Enter/blur, revert (and flag) on nonsense
    const commit = () => {
      const a = Fmt.parseYear(fromBox.value), b = Fmt.parseYear(toBox.value);
      if (a == null || b == null) {
        if (a == null) fromBox.classList.add('bad');
        if (b == null) toBox.classList.add('bad');
        return;
      }
      setSpan(a, b);
      syncBoxes();
    };
    for (const box of [fromBox, toBox]) {
      box.addEventListener('change', commit);
      box.addEventListener('keydown', e => { if (e.key === 'Enter') { commit(); box.blur(); } });
    }

    const zoomBy = f => { const z = zoom(f, null); setSpan(z.from, z.to); };
    container.querySelector('#tl-in').onclick = () => zoomBy(1 / ZOOM);
    container.querySelector('#tl-out').onclick = () => zoomBy(ZOOM);
    container.querySelector('#tl-reset').onclick = () => setSpan(time.defaultFrom, time.defaultTo);

    // wheel = zoom around the year under the cursor; preventDefault so the page never scrolls
    svg.addEventListener('wheel', e => {
      e.preventDefault();
      if (!e.deltaY) return;
      const anchor = yearAt(e.clientX - svg.getBoundingClientRect().left);
      const z = zoom(e.deltaY > 0 ? ZOOM : 1 / ZOOM, anchor);
      setSpan(z.from, z.to);
    }, { passive: false });

    // drag = zoom in: press at "from", release at "to"
    svg.addEventListener('pointerdown', e => {
      if (e.button !== 0) return;
      const x = e.clientX - svg.getBoundingClientRect().left;
      drag = { x, cur: x };
      svg.setPointerCapture(e.pointerId);
      render();
    });
    svg.addEventListener('pointermove', e => {
      if (!drag) return;
      drag.cur = e.clientX - svg.getBoundingClientRect().left;
      render();
    });
    const endDrag = () => {
      if (!drag) return;
      const { x, cur } = drag; drag = null;
      if (Math.abs(cur - x) < 3) { render(); return; }        // a click, not a drag
      setSpan(yearAt(Math.min(x, cur)), yearAt(Math.max(x, cur)));
      render();
    };
    svg.addEventListener('pointerup', endDrag);
    svg.addEventListener('pointercancel', endDrag);

    // follow the span when something else changes it (hash restore, another module)
    State.subscribe(st => {
      if (st.from !== span.from || st.to !== span.to) {
        span = clamp(st.from, st.to); syncBoxes(); render();
      }
      schedule();                                              // region etc. changes the counts too
    });

    if (window.ResizeObserver) new ResizeObserver(() => render()).observe(svg);
    render(); load();
  }

  return { init, span: () => ({ ...span }) };
})();
