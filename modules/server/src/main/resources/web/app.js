// Glue: load config → init modules → react to State changes by reloading findings.
(async () => {
  const ui = await Api.config();
  State.init({ from: ui.time.defaultFrom, to: ui.time.defaultTo });
  MapView.init(ui);
  Filters.init(document.getElementById('filters'));
  Timeline.init(document.getElementById('timeline'), ui.time);
  Viewport.init(document.getElementById('viewport'), MapView.map(), ui);

  const status = document.getElementById('status');
  let timer = null;
  let totalKey = null, totalCount = null; // "M total" = findings for this filter WITHOUT the bbox
  async function load() {
    const s = State.get();
    let a = s.from, b = s.to; if (a > b) [a, b] = [b, a];
    const bb = (s.inView && s.bbox) ? s.bbox.split(',').map(Number) : null;
    const base = { from: a, to: b, region: s.region };
    const params = (bb && bb.length === 4 && bb.every(Number.isFinite))
      ? { ...base, minLon: bb[0], minLat: bb[1], maxLon: bb[2], maxLat: bb[3] }
      : base;
    const inView = params !== base;
    status.textContent = 'loading…';
    try {
      const data = await Api.findings(params);
      const key = JSON.stringify(base);
      if (!inView) { totalKey = key; totalCount = data.length; }
      else if (totalKey !== key) { totalCount = (await Api.findings(base)).length; totalKey = key; }
      const span = { min: a, max: b }; // colour ramp spans the SELECTED window, not the global range
      MapView.render(data, span);
      Results.render(data, a, b, 300, { total: totalCount, inView });
      status.textContent = '';
    } catch (e) { status.textContent = 'failed to load'; }
  }
  const schedule = () => { clearTimeout(timer); timer = setTimeout(load, 250); };
  State.subscribe(schedule);
  load();
})();
