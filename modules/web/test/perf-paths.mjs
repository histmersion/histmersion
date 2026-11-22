// The interactive paths of the web UI (#1949), each measured in the browser: what one person does
// (open the page, pan the map, drag the timeline, hover, type, open an article, turn a layer on) and
// what it costs between their input and the next paint. Shared by the regression budgets
// (perf.test.mjs, fixed feeds on the test server) and the live baseline (perf-baseline.mjs).
//
// Nothing here writes: every path only GETs, moves the map, and changes browser-local state.

import { inputToUpdate, measure, memory, quiet } from './perf-lib.mjs';

/** The value `fn` first answers truthy, its handle released (a handle keeps what it holds alive, and
  * the memory figures would count it). Never `waitForSelector`, for the same reason: use a locator. */
export async function valueOf(page, fn, arg, opts = {}) {
  const h = await page.waitForFunction(fn, arg, { timeout: 60000, ...opts });
  try { return await h.jsonValue(); } finally { await h.dispose(); }
}
const ready = page => valueOf(page, () => !!window.__testMap);
/** Nothing on its way and the list not reloading: what is measured next is the gesture's own work. */
export const settled = async page => {
  await quiet(page);
  await page.waitForFunction(() => !document.querySelector('#count.is-loading'));
};

/** The page from navigation to its first paint, the map, the first markers and the first list. */
export async function firstRender(page, url, { markers = true } = {}) {
  await page.goto(url);
  const at = (fn, arg) => valueOf(page, fn, arg);
  const mapMs = await at(() => window.__testMap && performance.now());
  const markersMs = markers
    ? await at(() => window.__testMap?.markersIn() > 0 && performance.now()) : null;
  const listMs = await at(() => document.querySelector('#list .hit, #list .empty') && performance.now());
  const fcp = await page.evaluate(() => performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? null);
  const lt = await page.evaluate(() => window.__perf.longTasks);
  const r = x => x === null ? null : Math.round(x);
  return { fcpMs: r(fcp), mapMs: r(mapMs), markersMs: r(markersMs), listMs: r(listMs),
    longTasks: lt.length, longTaskMs: r(lt.reduce((s, t) => s + t.duration, 0)),
    longTaskMax: r(Math.max(0, ...lt.map(t => t.duration))) };
}

/** A drag across the map (40 moves of 12 px, one a frame) and a wheel zoom in and out. */
export async function mapPan(page) {
  await ready(page);
  await settled(page);
  const box = await page.locator('#map').boundingBox();
  const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  return measure(page, async () => {
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(cx - i * 12, cy + (i % 5)); await page.waitForTimeout(16); }
    await page.mouse.up();
    for (const dy of [-200, -200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(300); }
  });
}

/** Where the overview strip draws the year `y`. */
async function overviewX(page, y) {
  return page.evaluate(async year => {
    const cfg = await (await fetch('/api/config')).json();
    const t = cfg.time ?? cfg.ui?.time;
    const r = document.querySelector('.tl-overview-svg').getBoundingClientRect();
    return r.left + (year - t.min) / (t.max - t.min) * r.width;
  }, y);
}

/** The span dragged along the overview strip (40 moves of 4 px right, 20 back), then released: the drag itself
  * (markers re-filtered on each move), and from the release to the list's next render. */
export async function timelineDrag(page, from, to) {
  await ready(page);
  await settled(page);
  const svg = await page.locator('.tl-overview-svg').boundingBox();
  const x = await overviewX(page, (from + to) / 2), y = svg.y + svg.height / 2;
  const drag = await measure(page, async () => {
    await page.mouse.move(x, y);
    await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(x + i * 4, y); await page.waitForTimeout(16); }
    for (let i = 39; i >= 20; i--) { await page.mouse.move(x + i * 4, y); await page.waitForTimeout(16); }
  });
  const releaseToList = await inputToUpdate(page, '#list', () => page.mouse.up());
  return { ...drag, releaseToListMs: releaseToList };
}

/** "All states in period": the boundaries layer over a whole range, from the layer's switch to its
  * shapes drawn; then a drag of the span with every shape on the map. */
export async function rangeAreas(page, baseURL, from, to) {
  await page.evaluate(() => {
    localStorage.setItem('histmersion.ui.layers', '["boundaries"]');
    localStorage.setItem('histmersion.ui.areaPick', '["latest"]');
  });
  await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
  await page.reload();
  await ready(page);
  const t0 = await page.evaluate(() => performance.now());
  // drawn once nothing is on its way and the count of shapes has stopped moving
  await quiet(page);
  const drawn = await valueOf(page, () => {
    const n = window.__testMap.shapes('polities').length;
    const P = window.__perf;
    if (n > 0 && n === P._lastN) { P._same = (P._same || 0) + 1; } else { P._same = 0; P._lastN = n; P._at = performance.now(); }
    return P._same >= 10 && { n, at: P._at };
  }, null, { polling: 50 });
  const shapes = drawn.n;
  const lt = await page.evaluate(() => window.__perf.longTasks);
  const loadLong = { longTasks: lt.length, longTaskMs: Math.round(lt.reduce((s, t) => s + t.duration, 0)),
    longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))) };
  const drag = await timelineDrag(page, from, to);
  return { shapes, drawnMs: Math.round(drawn.at), sinceMapMs: Math.round(drawn.at - t0), load: loadLong, drag };
}

/** Hover the first `n` source links of the list: from the pointer's arrival to the card's first paint,
  * and to its content (a cold hover asks the server; a second hover of the same link is cached). */
export async function hoverCards(page, n = 6) {
  await ready(page);
  await settled(page);
  await page.locator('#list [data-ck]').first().waitFor();
  const links = page.locator('#list .hit [data-ck]');
  const count = Math.min(n, await links.count());
  const shown = [], filled = [], warm = [];
  const away = async () => { await page.mouse.move(5, 5); await page.waitForFunction(() => !document.querySelector('.cite-tooltip')); };
  for (let i = 0; i < count; i++) {
    const link = links.nth(i);
    await link.scrollIntoViewIfNeeded();
    const at = x => () => link.hover({ position: { x, y: 4 } });
    await away();
    shown.push(await inputToUpdate(page, '.cite-tooltip', at(4)));
    await away();
    filled.push(await inputToUpdate(page, '.cite-tooltip', at(5), { lacks: 'loading' }));
    await away();
    warm.push(await inputToUpdate(page, '.cite-tooltip', at(4), { lacks: 'loading' }));
  }
  await away();
  const max = xs => Math.max(0, ...xs);
  return { links: count, shownMax: max(shown), filledMax: max(filled), warmMax: max(warm) };
}

/** Typing into the search box (each keystroke's echo), into a chip filter (to its suggestions), and
  * hovering a suggestion (its preview, to the list's next render). */
export async function filterTyping(page, text, chipPrefix) {
  await ready(page);
  await settled(page);
  const box = page.locator('#f-q');
  await box.click();
  const keys = await measure(page, async () => { await box.pressSequentially(text.slice(0, -1), { delay: 60 }); });
  const wait = await page.evaluate(async () => { const d = (await (await fetch('/api/config')).json()).debounce;
    return d.searchMs + d.reloadMs + 100; });
  await page.waitForTimeout(wait);
  await quiet(page);
  const toList = await inputToUpdate(page, '#list', () => box.press(text.slice(-1)));
  await box.fill('');
  // a chip filter: its suggestions, then a hovered suggestion's preview of the filter
  if (await page.locator('#f-more').isHidden()) await page.locator('#f-more-toggle').click();
  const chip = page.locator('#f-more .chip-input').first();
  await chip.click();
  const suggest = await inputToUpdate(page, '#f-more .chip-suggest', () => chip.pressSequentially(chipPrefix, { delay: 30 }),
    { hidden: false, has: 'li' });
  let preview = null;
  const opt = page.locator('#f-more .chip-suggest li').first();
  if (await opt.count()) {
    const ob = await opt.boundingBox();
    preview = await inputToUpdate(page, '#list', () => page.mouse.move(ob.x + 6, ob.y + ob.height / 2));
  }
  await chip.fill('');
  await page.keyboard.press('Escape');
  return { keystrokes: keys.inputs, keyP95: keys.inputP95, keyMax: keys.inputMax, keyLongTasks: keys.longTasks,
    lastKeyToListMs: toList, suggestMs: suggest, previewToListMs: preview };
}

/** The Articles tab, then its first article: from the click to its detail in the results, and from "Pin as
  * window" to its content in the window. */
export async function articleOpen(page, baseURL) {
  await page.goto(new URL('/explore#tab=articles', baseURL).href);
  await page.reload();
  await ready(page);
  await page.locator('.articles-list .article-link').first().waitFor();
  const link = page.locator('.articles-list .article-link').first();
  const shown = await inputToUpdate(page, '.result-detail', () => link.click());
  const result = { windowMs: shown };
  // pinned as a window: from the click to the article's own content in it
  result.contentMs = await inputToUpdate(page, '.wm-window .wm-content', () => page.locator('.result-detail .detail-pin').click(),
    { has: 'h1, h2' });
  await page.locator('.wm-window .wm-close').click();
  await page.locator('#detail-back').click();
  return result;
}

/** Wheel zooms in and out at the map's centre (six steps, 400 ms apart: each one's zoom animation ends
  * before the next), with every marker loaded — what the cluster group's zoom animation costs (#1647). */
export async function mapZoom(page) {
  await ready(page);
  await settled(page);
  const box = await page.locator('#map').boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  return measure(page, async () => {
    for (const dy of [-200, -200, -200, 200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(400); }
  });
}

/** The middle value of `xs` (the upper middle of an even count). */
export const median = xs => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];

/** Measurements of the same gesture, figure by figure: each numeric figure's median (the first run's
  * value for anything else). */
export const medianOf = runs => Object.fromEntries(Object.keys(runs[0]).map(k =>
  [k, runs.every(r => typeof r[k] === 'number') ? median(runs.map(r => r[k])) : runs[0][k]]));

/** The events layer at a whole-world view: from the reload to every marker drawn, then a pan, then
  * wheel zooms from the same view. With `runs` > 1 the draw, the pan and the zooms are each measured
  * that many times (a reload per draw) and every figure is the MEDIAN of its runs (#2130): one run
  * follows whatever else the machine is doing (a build, another agent's tests) — a draw of 2 540 ms
  * against 2 500, a zoom dropping 8.1 % of frames against 8 — and one slow run is noise, not a
  * regression; a regression slows every run. `drawnRuns` keeps each draw's time for the report. */
export async function eventLayer(page, baseURL, from, to, { atLeast = 1, runs = 1 } = {}) {
  await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
  await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
  const draws = [];
  for (let i = 0; i < runs; i++) {
    await page.reload();
    await ready(page);
    const t0 = await page.evaluate(() => performance.now());
    await page.evaluate(() => window.__testMap.setView([30, 20], 3, { animate: false }));
    await quiet(page);
    const drawn = await valueOf(page, min => {
      const n = window.__testMap.points();
      const P = window.__perf;
      if (n >= min && n === P._lastE) { P._sameE = (P._sameE || 0) + 1; } else { P._sameE = 0; P._lastE = n; P._atE = performance.now(); }
      return P._sameE >= 10 && { n, at: P._atE };
    }, atLeast, { polling: 50 });
    const lt = await page.evaluate(() => window.__perf.longTasks);
    draws.push({ n: drawn.n, ms: Math.round(drawn.at - t0), longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))) });
  }
  const pans = [], zooms = [];
  for (let i = 0; i < runs; i++) {
    await page.evaluate(() => window.__testMap.setView([30, 20], 3, { animate: false }));
    pans.push(await mapPan(page));
    await page.evaluate(() => window.__testMap.setView([30, 20], 3, { animate: false }));
    zooms.push(await mapZoom(page));
  }
  const pan = medianOf(pans), zoom = medianOf(zooms);
  return { markers: Math.min(...draws.map(d => d.n)), drawnMs: median(draws.map(d => d.ms)),
    drawnRuns: draws.map(d => d.ms), longTaskMax: median(draws.map(d => d.longTaskMax)), pan, zoom };
}

/** A vector base map (Readable or Physical) over the Aegean (the fixture extract) with the full overlay
  * load — the boundaries of a whole range, the 2 400 events, the 500 findings — from the view set to the
  * base map drawn whole (every tile and name), then a pan and wheel zooms there; then the options
  * panel's changes, each answered within the input budget (the map draws again after, off the input). */
export async function vectorMap(page, baseURL, style, { lat = 37.9, lon = 25.4, zoom = 7, overlays = true } = {}) {
  await page.evaluate(([s, o]) => {
    localStorage.setItem('histmersion.ui.baseMap', `["${s}"]`);
    if (o) { localStorage.setItem('histmersion.ui.layers', '["boundaries","events"]'); localStorage.setItem('histmersion.ui.areaPick', '["latest"]'); }
  }, [style, overlays]);
  await page.goto(new URL('/explore#from=-3000&to=1500', baseURL).href);
  await page.reload();
  await ready(page);
  await settled(page);   // the app's own start (the findings, the list) is firstRender's
  const drawn0 = await page.evaluate(() => window.__testMap.baseMapsDrawn());
  const t0 = await page.evaluate(() => performance.now());
  await page.evaluate(([la, lo, z]) => window.__testMap.setView([la, lo], z), [lat, lon, zoom]);
  await valueOf(page, n => window.__testMap.baseMapsDrawn() > n || (!window.__testMap.isMoving() && window.__testMap.names().length > 0), drawn0);
  await quiet(page);
  const drawnMs = Math.round(await page.evaluate(() => performance.now()) - t0);
  const lt = (await page.evaluate(() => window.__perf.longTasks)).filter(t => t.start >= t0);
  const load = await page.evaluate(() => ({ points: window.__testMap.points(), shapes: window.__testMap.shapes('polities').length }));
  const pan = await mapPan(page);
  await page.evaluate(([la, lo, z]) => window.__testMap.setView([la, lo], z - 1), [lat, lon, zoom]);
  const zoomed = await mapZoom(page);
  if (await page.locator('#layers-btn').isVisible() && !(await page.locator('#layers-panel').isVisible())) await page.locator('#layers-btn').click();
  await page.locator('.map-options-btn').click();
  await page.locator('.map-options').waitFor();
  const options = await measure(page, async () => {
    for (const act of [() => page.locator('.map-option-names').uncheck(), () => page.locator('.map-option-names').check(),
      () => page.locator('.map-language-select').selectOption('local'), () => page.locator('.map-language-select').selectOption('en')]) {
      await act(); await page.waitForTimeout(500);
    }
  });
  await page.keyboard.press('Escape');
  return { drawnMs, longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))), ...load, pan, zoom: zoomed, options };
}

export { memory };

/** Zooms across whole levels with the events layer on, at the page's window size (#4291): whether each one
  * animates the bubbles (split and merge) and what the zooms cost. A wide window holds ~1 600 events and
  * findings for its view and draws a few hundred markers of them; its zooms stopped animating while a
  * phone's (fewer events fetched) still did, and once animated, making every marker at the zoom's end and
  * laying the page out once per moving marker held a frame for 70–90 ms. `held`/`drawn`: the points held
  * and the markers drawn at the start. */
export async function clusterZoom(page, baseURL, { at = [40, 22], from = 4, zooms = [5, 6, 5, 4] } = {}) {
  await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
  await page.goto(new URL('/explore#from=-3500&to=1500', baseURL).href);
  await page.reload();
  await ready(page);
  await page.evaluate(([c, z]) => window.__testMap.setView(c, z, { animate: false }), [at, from]);
  await settled(page);
  const start = await page.evaluate(() => ({ held: window.__testMap.points(),
    drawn: window.__testMap.markersIn(['events', 'markers']) }));
  const animated = [];
  const m = await measure(page, async () => {
    for (const z of zooms) {
      animated.push(await page.evaluate(zoom => new Promise(resolve => {
        const map = window.__testMap, before = map.clusterAnimations();
        map.once('zoomend', () => setTimeout(() => resolve(map.clusterAnimations() > before), 500));
        map.setZoom(zoom);
      }), z));
      await quiet(page, 300);   // the view's events reloaded before the next zoom (a reload regroups at once)
    }
  });
  return { ...m, ...start, zooms: zooms.length, notAnimated: animated.filter(a => !a).length };
}

/** The timeline's playback over [`from`, `to`] for `seconds` (#3764): its frames, what it asks the server
  * for and how often the map's states change look. A smooth playback draws every frame from what it holds
  * (no findings request and no outline upload per frame) and restyles the states on most frames — the
  * states were redrawn at the rate their answers came (~5 a second) and the findings at ~60 requests a
  * second. `stillFramesPct`: the frames on which no state's colour or fade changed. */
export async function playback(page, baseURL, from, to, seconds = 4) {
  await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
  await ready(page);
  await settled(page);
  const asked = { findings: 0, places: 0 };
  const onRequest = r => {
    const p = new URL(r.url()).pathname;
    if (p === '/api/findings') asked.findings++;
    if (p === '/api/places/geo') asked.places++;
  };
  page.on('request', onRequest);
  const looks = page.evaluate(ms => new Promise(res => {
    const map = window.__testMap, out = { frames: 0, changed: 0, uploads0: map.shapeUploads('polities') };
    let prev = null; const t0 = performance.now();
    const f = () => {
      const look = map.shapes('polities').map(id => { const s = map.shapeStyle('polities', id); return s ? `${s.colour}/${s.alpha}` : ''; }).join('|');
      if (prev !== null) { out.frames++; if (look !== prev) out.changed++; }
      prev = look;
      if (performance.now() - t0 < ms) requestAnimationFrame(f);
      else res({ ...out, uploads: map.shapeUploads('polities') - out.uploads0 });
    };
    requestAnimationFrame(f);
  }), seconds * 1000);
  const m = await measure(page, async () => {
    await page.locator('#tl-play').click();
    await page.waitForTimeout(seconds * 1000);
  });
  const l = await looks;
  await page.locator('#tl-play').click();
  page.off('request', onRequest);
  const per = n => Math.round(10 * n / seconds) / 10;
  return { ...m, states: l.frames ? (await page.evaluate(() => window.__testMap.shapes('polities').length)) : 0,
    stillFramesPct: l.frames ? Math.round(1000 * (l.frames - l.changed) / l.frames) / 10 : 100,
    findingsRequestsPerSecond: per(asked.findings), placesRequestsPerSecond: per(asked.places),
    outlineUploadsPerSecond: per(l.uploads) };
}

// ── map motion (#4718) ──────────────────────────────────────────────────────────────────────────────

/** The full load (perf-fixtures `fullLoad`, routed by the caller) with every layer on over its period, the
  * reader's "Map motion" `motion` kept on this device (`null`: never chosen), the view at `at`, zoom `from`. */
export async function fullLoadView(page, baseURL, { motion = null, at = [40, 24], from = 5, hash = '#from=-1600&to=-1100',
  layers = '["boundaries","sites","media","events"]' } = {}) {
  await page.goto(new URL('/', baseURL).href);
  await page.evaluate(([l, m]) => {
    localStorage.clear();
    localStorage.setItem('histmersion.ui.layers', l);
    if (m) localStorage.setItem('histmersion.ui.mapMotion', JSON.stringify([m]));
  }, [layers, motion]);
  await page.goto(new URL('/' + hash, baseURL).href);
  await page.reload();
  await ready(page);
  await page.evaluate(([c, z]) => window.__testMap.setView(c, z, { animate: false }), [at, from]);
  await settled(page);
  await page.waitForTimeout(300);
  return page.evaluate(() => ({ held: window.__testMap.points(),
    drawn: window.__testMap.markersIn(['markers', 'events', 'sites']) }));
}

/** One zoom to `zoom`, traced frame by frame: asked through the app (`setZoom`, the cluster regroup's path) or
  * by a click on MapLibre's own zoom button (`button`: `in` | `out`, MapLibre's camera alone). From the ask to
  * 600 ms after the zoom's end, every frame: how many markers moved on screen (their centre > 0.5 px since the
  * previous frame) while the camera stood as on the frame before — the regroup's motion — and whether the
  * camera moved.
  * `zoomMs`: the ask → the zoom's end (an eased zoom ~250–500 ms, a jump a frame or two); `cameraFrames`: frames
  * on which the camera moved; `motionFrames`: frames after the zoom's end on which markers moved; `ghosts`:
  * the most markers flying into a bubble on one frame; `longestFrameMs`: the longest frame interval. */
export async function motionTrace(page, zoom, { button = null } = {}) {
  const traced = page.evaluate(([z, viaButton]) => new Promise(resolve => {
    const map = window.__testMap, a0 = map.clusterAnimations();
    const out = { cameraFrames: 0, motionFrames: 0, maxMoved: 0, ghosts: 0, longestFrameMs: 0, frames: 0 };
    let prev = new Map(), tPrev = performance.now(), ended = false, tEnd = 0, camPrev = '';
    const start = performance.now();
    map.once('zoomend', () => { ended = true; tEnd = performance.now(); out.zoomMs = Math.round(tEnd - start); });
    const f = () => {
      const now = performance.now(), cur = new Map();
      let moved = 0;
      const spots = map.spots(['markers', 'events', 'sites']);
      for (const s of spots) {
        if (s.offscreen) continue;
        const c = [s.x, s.y];
        cur.set(s.serial, c);
        const p = prev.get(s.serial);
        if (p && Math.hypot(p[0] - c[0], p[1] - c[1]) > 0.5) moved++;
      }
      // the camera as it stands: a frame on which it changed (an ease, or a jump's one frame) moves every marker
      const c = map.getCenter(), cam = `${map.getZoom()},${c.lat},${c.lng}`;
      const still = cam === camPrev && !map.isMoving();
      out.frames++;
      out.longestFrameMs = Math.max(out.longestFrameMs, Math.round(now - tPrev));
      if (cam !== camPrev) out.cameraFrames++;
      else if (still && ended && moved > 0) { out.motionFrames++; out.maxMoved = Math.max(out.maxMoved, moved); }
      camPrev = cam;
      out.ghosts = Math.max(out.ghosts, spots.filter(s => s.ghost).length);
      prev = cur; tPrev = now;
      if (!ended || now - tEnd < 600) requestAnimationFrame(f);
      else resolve({ ...out, animated: map.clusterAnimations() > a0, reached: Math.round(map.getZoom() * 100) / 100 });
    };
    requestAnimationFrame(f);
    if (!viaButton) map.setZoom(z);
  }), [zoom, button]);
  if (button) await page.locator(button === 'in' ? '.maplibregl-ctrl-zoom-in' : '.maplibregl-ctrl-zoom-out').click();
  const r = await traced;
  await quiet(page, 300);
  return r;
}

/** Zooms across whole levels with the full load (#4718's budget): the frame figures of all of them, and how
  * many animated their regroup. */
export async function fullLoadZooms(page, zooms = [6, 7, 6, 5]) {
  const runs = [];
  const m = await measure(page, async () => { for (const z of zooms) runs.push(await motionTrace(page, z)); });
  return { ...m, zooms: zooms.length, notAnimated: runs.filter(r => !r.animated).length,
    motionFrames: runs.reduce((s, r) => s + r.motionFrames, 0), longestFrameMs: Math.max(...runs.map(r => r.longestFrameMs)) };
}
