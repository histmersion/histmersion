// Glue: load config → init modules → react to State changes by reloading the map feed + the results.
(async () => {
  const ui = await Api.config();
  State.init({ from: ui.time.defaultFrom, to: ui.time.defaultTo });
  MapView.init(ui);
  Filters.init(document.getElementById('filters'), ui);
  Timeline.init(document.getElementById('timeline'), ui.time);
  Viewport.init(document.getElementById('viewport'), MapView.map(), ui);

  const status = document.getElementById('status');
  let timer = null;
  let totalKey = null, totalCount = null; // "M total" = findings for this filter WITHOUT the bbox
  async function load() {
    // One params object (plan §1.0) feeds every endpoint: /api/findings is the map feed,
    // /api/sources the sidebar (unit of result = source). The viewport bbox rides along when on.
    const params = State.toApiParams();
    const base = State.toApiParams({ except: 'bbox' });
    const inView = params.minLon != null;
    const a = params.from, b = params.to;
    status.textContent = 'loading…';
    try {
      const points = await Api.findings(params);
      const key = JSON.stringify(base);
      if (!inView) { totalKey = key; totalCount = points.length; }
      else if (totalKey !== key) { totalCount = (await Api.findings(base)).length; totalKey = key; }
      MapView.render(points, { min: a, max: b }); // colour ramp spans the SELECTED window
      try {
        Results.renderSources(await Api.sources({ ...params, limit: ui.pageSize }),
          { findings: points.length, total: totalCount, inView });
        status.textContent = '';
      } catch (e) {
        // /api/sources arrives with stream 1.1; until then fall back to the findings list.
        Results.render(points, a, b, ui.pageSize, { total: totalCount, inView });
        status.textContent = '/api/sources unavailable — listing findings';
      }
    } catch (e) { status.textContent = 'failed to load'; }
  }
  const schedule = () => { clearTimeout(timer); timer = setTimeout(load, ui.debounce.reloadMs); };
  State.subscribe(schedule);
  load();
})();
