// The "by time" / "by region" sidebar tabs (plan §1.6): the SAME filter as the sources list, shown
// as a collapsible tree of counts instead of a list of hits — WHEN and WHERE the evidence sits.
//
// Rows read `label · N sources · M in more than one subregion · period`, because the counts are
// NON-ADDITIVE: a source is counted in every time bucket its period overlaps and in every place
// that covers it, so children never sum to their parent. The server says how many are double-counted
// (`overlap`) and this is the only honest way to print it.
//
// Deeper levels are LAZY: the server nests `ui.treeDepth` levels, and expanding a node past that
// re-asks `/api/aggregate` with the window (a time bucket) or the place (a region node) narrowed to
// that node — the same query, smaller scope.
//
// Self-contained like articles.js: it owns two tab buttons and one pane, reads `/api/aggregate`
// through Api, and reaches the rest of the atlas through three existing seams — Article.open (a
// sample source), State.set (a time node sets the timeline span) and Places.preview (a region node
// draws its polygon). It never touches the map, the sources list or the filter bar directly.
window.TreeTab = (() => {
  const $ = id => document.getElementById(id);
  const tabs = $('tabs'), pane = $('tree-pane'), list = $('tree-list'), count = $('tree-count');
  const TABS = { bytime: 'time', byregion: 'region' };

  let ui = null, axis = null, timer = null, reqId = 0, last = null;

  // ── formatting ───────────────────────────────────────────────────────────────────────────────
  const unitWord = n => `${n} source${n === 1 ? '' : 's'}`;
  const childNoun = b => (b.children || []).some(c => c.kind === 'time') ? 'period' : 'subregion';
  // a traced polygon may already say "(contested)" in its own label — do not say it twice
  const contested = b =>
    b.contested && !String(b.label || '').toLowerCase().includes('contested')
      ? ` <span class="contested" title="disputed extent">(contested)</span>` : '';

  const periodOf = b =>
    b.kind === 'time' ? '' : Fmt.periodStr(b.start, b.end);

  // ── the request ──────────────────────────────────────────────────────────────────────────────
  /** The base params for every call: the whole current filter, plus this tab's axis. */
  const params = extra => ({
    ...State.toApiParams(),
    by: axis === 'region' ? 'region' : 'time',
    depth: ui.treeDepth,
    samples: ui.treeSamples,
    ...(extra || {}),
  });

  async function load() {
    if (!list || !axis) return;
    const mine = ++reqId;
    if (count) count.textContent = 'loading…';
    try {
      const data = await Api.aggregate(params());
      if (mine !== reqId) return;                       // a newer filter won the race
      last = data;
      render(data);
    } catch (e) {
      if (mine === reqId && count) count.textContent = 'failed to load';
    }
  }

  /** The subtree of one node, asked for as the same query with a narrower scope. */
  function subtree(b, data) {
    if (b.kind === 'time') return Api.aggregate(params({ from: b.start, to: b.end }));
    return Api.aggregate(params({ by: 'region', place: b.placeId, asOf: data && data.asOf }));
  }

  // ── drawing ──────────────────────────────────────────────────────────────────────────────────
  function render(data) {
    list.innerHTML = '';
    const roots = (data && data.tree) || [];
    const s = State.get();
    const span = `${Fmt.yr(s.from)} → ${Fmt.yr(s.to)}`;
    if (count) {
      const total = (data && data.total) || 0;
      count.textContent = `${unitWord(total)} · by ${(data && data.by) || axis} · ${span}`
        + (data && data.asOf != null ? ` · as of ${Fmt.yr(data.asOf)}` : '');
    }
    if (!roots.length) {
      list.innerHTML = `<div class="empty"><p>nothing to group — widen the span or remove a chip</p></div>`;
      return;
    }
    roots.forEach(b => list.appendChild(node(b, 0, data)));
  }

  /** One row plus its (initially collapsed below the first level) children. */
  function node(b, depth, data) {
    const wrap = document.createElement('div');
    wrap.className = 'tnode';
    wrap.style.setProperty('--tdepth', depth);

    const row = document.createElement('div');
    row.className = `trow t-${b.kind}` + (b.key === 'unlocated' ? ' t-unlocated' : '');
    row.tabIndex = 0;
    const kids = b.children || [];
    // a leaf that is only a leaf because the server stopped at `depth` can still be opened
    const openable = kids.length > 0 || (b.kind === 'time' || b.placeId);
    const over = b.overlap > 0 ? ` · ${b.overlap} in more than one ${childNoun(b)}` : '';
    const per = periodOf(b);
    // the gazetteer is a DAG: a place drawn under one parent says where else it sits (item 8)
    const also = (b.alsoUnder || []).length
      ? ` <span class="talso" title="this place is part of more than one region">(also under ${Fmt.esc(b.alsoUnder.join(', '))})</span>`
      : '';
    row.innerHTML =
      `<span class="twist">${openable ? '▸' : '·'}</span>` +
      `<span class="tlabel">${Fmt.esc(b.label)}${contested(b)}</span>` +
      `<span class="tcount">${unitWord(b.count)}</span>` +
      `<span class="tmeta">${Fmt.esc(over)}${per ? ` · ${Fmt.esc(per)}` : ''}${also}</span>`;
    wrap.appendChild(row);

    const kidBox = document.createElement('div');
    kidBox.className = 'tkids';
    kidBox.hidden = true;
    wrap.appendChild(kidBox);

    let filled = false;
    const fill = list2 => {
      kidBox.innerHTML = '';
      // A node with no finer level below it says SO, above its sample sources: "nothing deeper" left
      // the reader guessing whether the gazetteer stops here, the span is wrong, or this is simply
      // where the evidence ends (item 8).
      if (!(list2 || []).length) kidBox.appendChild(nothingDeeper(b, depth + 1, data));
      (list2 || []).forEach(c => kidBox.appendChild(node(c, depth + 1, data)));
      (b.samples || []).forEach(x => kidBox.appendChild(sample(x, depth + 1)));
    };

    /** "nothing deeper" told the reader nothing: is the gazetteer empty below here, is the span
      * wrong, or is this simply where the evidence stops? Say which, and offer the sources (item 8). */
    function nothingDeeper(bucket, d, data2) {
      const el = document.createElement('div');
      el.className = 'tempty';
      el.style.setProperty('--tdepth', d);
      const asOf = data2 && data2.asOf != null ? ` as of ${Fmt.yr(data2.asOf)}` : '';
      const what = bucket.kind === 'time' ? 'finer periods' : 'subregions';
      el.innerHTML = `no ${what}${bucket.kind === 'time' ? '' : Fmt.esc(asOf)} — ${unitWord(bucket.count)} · `
        + `<a href="#" class="tlist">list them</a>`;
      el.querySelector('.tlist').onclick = e => { e.preventDefault(); listInSidebar(bucket, data2); };
      return el;
    }

    async function toggle() {
      if (!openable) return;
      const opening = kidBox.hidden;
      kidBox.hidden = !opening;
      row.querySelector('.twist').textContent = opening ? '▾' : '▸';
      if (!opening || filled) return;
      filled = true;
      if (kids.length || (b.samples || []).length) { fill(kids); return; }
      kidBox.innerHTML = `<div class="tempty" style="--tdepth:${depth + 1}">loading…</div>`;
      try {
        const deeper = await subtree(b, data);
        // the subtree call re-roots at this node, so drop the node itself when it comes back
        const got = (deeper.tree || []);
        fill(got.length === 1 && got[0].key === b.key ? (got[0].children || []) : got);
        if (!(b.samples || []).length && got.length === 1 && got[0].key === b.key)
          (got[0].samples || []).forEach(x => kidBox.appendChild(sample(x, depth + 1)));
      } catch (e) {
        kidBox.innerHTML = `<div class="tempty" style="--tdepth:${depth + 1}">could not load</div>`;
        filled = false;
      }
    }

    row.onclick = e => {
      // the label is the "act on this bucket" affordance, the rest of the row expands it
      if (e.target.closest('.tlabel')) { act(b); return; }
      toggle();
    };
    row.onkeydown = e => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
      else if (e.key === 'ArrowRight' && kidBox.hidden) toggle();
      else if (e.key === 'ArrowLeft' && !kidBox.hidden) toggle();
    };
    // The first level does NOT open itself (item 7). On the seeded corpus that expanded straight
    // into hundreds of near-identical rows before the reader had chosen anything to look at; the
    // server now prunes uninformative splits, and what is left is a choice, not a dump.
    return wrap;
  }

  /** Clicking a bucket's LABEL acts on it: a time bucket becomes the timeline span, a region node
    * draws its polygon on the map (reusing the gazetteer overlay, without touching the filter). */
  function act(b) {
    if (b.kind === 'time' && b.start != null && b.end != null) State.set({ from: b.start, to: b.end });
    else if (b.placeId && window.Places && Places.preview) Places.preview(b.placeId, last && last.asOf);
  }

  /** "list them" — narrow the whole atlas to this bucket and switch to the sources tab, which is
    * where a list of sources lives. A count with no way to see what is behind it is a dead end. */
  function listInSidebar(b, data) {
    if (b.kind === 'time' && b.start != null && b.end != null) State.set({ from: b.start, to: b.end });
    else if (b.placeId) State.set({ placeIds: [b.placeId] });
    const tab = document.querySelector('#tabs .tab[data-tab="sources"]');
    if (tab) tab.click();
  }

  function sample(x, depth) {
    const d = document.createElement('div');
    d.className = 'tsample';
    d.style.setProperty('--tdepth', depth);
    d.tabIndex = 0;
    d.innerHTML = `<span class="ck">${Fmt.esc(x.citekey)}</span> <span class="title">${Fmt.esc(x.title || '')}</span>`;
    const open = () => Article.open(x.citekey, { q: State.get().q });
    d.onclick = open;
    d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
    return d;
  }

  // ── the tab ──────────────────────────────────────────────────────────────────────────────────
  /** Runs AFTER articles.js's own handler (this file is loaded later), so it has the last word on
    * which panes are visible; on any other tab it simply hides itself again. */
  function show(tab) {
    const mine = TABS[tab];
    axis = mine || null;
    if (pane) pane.hidden = !mine;
    if (!mine) return;
    ['count', 'list', 'articles-pane'].forEach(id => { const el = $(id); if (el) el.hidden = true; });
    load();
  }

  let lastParams = null;
  const schedule = () => {
    if (!axis) return;
    const key = JSON.stringify(params());
    if (key === lastParams) return;      // e.g. the article modal opened: no filter moved
    lastParams = key;
    clearTimeout(timer);
    timer = setTimeout(load, (ui && ui.debounce && ui.debounce.reloadMs) || 0);
  };

  function init(cfg) {
    ui = cfg;
    if (tabs) tabs.addEventListener('click', e => {
      const b = e.target.closest('.tab');
      if (b) show(b.dataset.tab);
    });
    State.subscribe(schedule);
  }

  // Config-driven like every other module (no literals here): until /api/config answers, the tabs
  // are inert rather than guessing a depth.
  Api.config().then(init, () => {});

  return { show, load };
})();
