// Glue: load config → init modules → react to State changes by reloading the map feed + the results.
(async () => {
  const ui = await Api.config();
  State.init({ from: ui.time.defaultFrom, to: ui.time.defaultTo });
  MapView.init(ui);
  Filters.init(document.getElementById('filters'), ui);
  Timeline.init(document.getElementById('timeline'), ui.time);
  Viewport.init(document.getElementById('viewport'), MapView.map(), ui);
  Article.restore();          // a reload (or a shared link) lands back on the paper that was open
  if (window.ArticlesTab) ArticlesTab.restore();   // …and on the tab it was on (item 25)

  // The tagline says how big the corpus is, so the first thing on the page is a reason to search
  // (item 31). The number is ASKED FOR, never written down: an unfiltered `total` is the corpus.
  const tagline = document.getElementById('tagline');
  if (tagline) Api.sources({ limit: 1 })
    .then(r => { if (r && r.total != null) tagline.textContent = `${r.total} sources · search, then read`; })
    .catch(() => {});

  const status = document.getElementById('status');
  let timer = null, reqId = 0;
  let totalKey = null, totalCount = null; // "M total" = findings for this filter WITHOUT the bbox
  let lastParams = null;                  // the query the visible list was built from
  async function load() {
    const mine = ++reqId;
    // One params object (plan §1.0) feeds every endpoint: /api/findings is the map feed,
    // /api/sources the sidebar (unit of result = source). The viewport bbox rides along when on.
    const params = State.toApiParams();
    const base = State.toApiParams({ except: 'bbox' });
    const inView = params.minLon != null;
    const a = params.from, b = params.to;
    status.textContent = 'loading…';
    Results.busy(true);
    try {
      const points = await Api.findings(params);
      if (mine !== reqId) return;                       // a newer filter won the race
      const key = JSON.stringify(base);
      if (!inView) { totalKey = key; totalCount = points.length; }
      else if (totalKey !== key) {
        const n = (await Api.findings(base)).length;
        if (mine !== reqId) return;
        totalCount = n; totalKey = key;
      }
      MapView.render(points, { min: a, max: b }); // colour ramp spans the SELECTED window
      try {
        // the params ride along so "show N more" can re-issue THIS query at an offset
        const sourceParams = { ...params, limit: ui.pageSize };
        const result = await Api.sources(sourceParams);
        if (mine !== reqId) return;
        Results.renderSources(result,
          { findings: points.length, total: totalCount, inView, params: sourceParams });
        status.textContent = '';
      } catch (e) {
        if (mine !== reqId) return;
        // /api/sources arrives with stream 1.1; until then fall back to the findings list.
        Results.render(points, a, b, ui.pageSize, { total: totalCount, inView });
        status.textContent = '/api/sources unavailable — listing findings';
      }
    } catch (e) {
      if (mine !== reqId) return;
      status.textContent = 'failed to load';
      Results.failed('could not reach the server — the list below is stale');
    }
  }
  // ONE debounce for the whole reload (item 10). The filter bar used to add its own on top of this,
  // so a keystroke waited searchMs + reloadMs before anything moved; it now writes State immediately
  // and this is the only wait. A change that leaves the QUERY identical — opening an article, which
  // is State too — must not refetch the map and the list, so the params are fingerprinted.
  const schedule = () => {
    const key = JSON.stringify(State.toApiParams());
    if (key === lastParams) return;
    lastParams = key;
    clearTimeout(timer); timer = setTimeout(load, ui.debounce.reloadMs);
  };
  State.subscribe(schedule);
  lastParams = JSON.stringify(State.toApiParams());
  load();
})();
