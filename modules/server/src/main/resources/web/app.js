// Glue: load config → init modules → react to State changes by reloading the map feed + the results.
(async () => {
  const ui = await Api.config();
  State.init({ from: ui.time.defaultFrom, to: ui.time.defaultTo });
  MapView.init(ui);
  Filters.init(document.getElementById('filters'), ui);
  Timeline.init(document.getElementById('timeline'), ui.time);

  const status = document.getElementById('status');
  let timer = null;
  async function load() {
    // One params object (plan §1.0) feeds both endpoints; /api/findings stays the map feed and
    // /api/sources is the sidebar, whose unit of result is the source.
    const params = State.toApiParams();
    status.textContent = 'loading…';
    try {
      const points = await Api.findings(params);
      MapView.render(points, ui.time);
      try {
        Results.renderSources(await Api.sources({ ...params, limit: ui.pageSize }));
        status.textContent = '';
      } catch (e) {
        // /api/sources arrives with stream 1.1; until then fall back to the findings list.
        Results.render(points, params.from, params.to, ui.pageSize);
        status.textContent = '/api/sources unavailable — listing findings';
      }
    } catch (e) { status.textContent = 'failed to load'; }
  }
  const schedule = () => { clearTimeout(timer); timer = setTimeout(load, ui.debounce.reloadMs); };
  State.subscribe(schedule);
  load();
})();
