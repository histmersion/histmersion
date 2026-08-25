// Glue: load config → init modules → react to State changes by reloading findings.
(async () => {
  const ui = await Api.config();
  State.init({ from: ui.time.defaultFrom, to: ui.time.defaultTo });
  MapView.init(ui);
  Filters.init(document.getElementById('filters'));
  Timeline.init(document.getElementById('timeline'), ui.time);

  const status = document.getElementById('status');
  let timer = null;
  async function load() {
    const s = State.get();
    let a = s.from, b = s.to; if (a > b) [a, b] = [b, a];
    status.textContent = 'loading…';
    try {
      const data = await Api.findings({ from: a, to: b, region: s.region });
      MapView.render(data, ui.time);
      Results.render(data, a, b, 300);
      status.textContent = '';
    } catch (e) { status.textContent = 'failed to load'; }
  }
  const schedule = () => { clearTimeout(timer); timer = setTimeout(load, 250); };
  State.subscribe(schedule);
  load();
})();
