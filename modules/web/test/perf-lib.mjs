import { configOverride } from './engine.mjs';
import { readFileSync } from 'node:fs';

// Browser-side speed and memory measurement for the web UI (#1949): what the page does between an
// input and the next paint, the long tasks and dropped frames of a gesture, and what the page holds
// (JS heap, DOM nodes, event listeners, the map's markers, the app's caches). Shared by the budgets and
// soak tests (perf.test.mjs) and the live baseline (perf-baseline.mjs). Everything here only reads.

// ── the in-page probe ───────────────────────────────────────────────────────────────────────────

/** Installed before the app's own scripts: long tasks, every input's latency to the next paint (the
  * INP model: from the event's timestamp to the frame after its handlers ran), frame intervals while a
  * gesture runs, and "until this panel changes" waits. */
export function probe() {
  const P = window.__perf = {
    longTasks: [], inputs: [], frames: [], recording: false,
    reset() { this.longTasks = []; this.inputs = []; this.frames = []; },
  };
  if ((PerformanceObserver.supportedEntryTypes || []).includes('longtask')) {
    new PerformanceObserver(list => { for (const e of list.getEntries()) P.longTasks.push({ start: e.startTime, duration: e.duration }); })
      .observe({ type: 'longtask', buffered: true });
  } else {
    // no Long Tasks API (Firefox): a 10 ms timer that arrives more than 50 ms late was held up by a task
    // at least that long — the same threshold, measured from outside the task
    P.longTaskSource = 'timer';
    let last = performance.now();
    setInterval(() => {
      const now = performance.now(), late = now - last - 10;
      if (late > 50) P.longTasks.push({ start: last + 10, duration: late });
      last = now;
    }, 10);
  }
  // after the current task: a frame, then a message posted from it runs once that frame was painted
  const nextPaint = cb => requestAnimationFrame(() => { const ch = new MessageChannel(); ch.port1.onmessage = () => cb(performance.now()); ch.port2.postMessage(0); });
  P.nextPaint = () => new Promise(res => nextPaint(res));
  for (const type of ['pointerdown', 'pointerup', 'pointermove', 'pointerover', 'click', 'keydown', 'input', 'wheel']) {
    window.addEventListener(type, e => {
      if (!P.recording) return;
      const ts = e.timeStamp;
      nextPaint(t => P.inputs.push({ type, latency: t - ts }));
    }, { capture: true, passive: true });
  }
  const tick = t => { if (P.recording) P.frames.push(t); requestAnimationFrame(tick); };
  requestAnimationFrame(tick);
  // the next change under `selector` (matching `ready`, when given) after the next input, painted
  const matches = (el, r) => (r.includes == null || el.textContent.includes(r.includes)) &&
    (r.lacks == null || !el.textContent.includes(r.lacks)) && (r.equals == null || el.textContent === r.equals) &&
    (r.has == null || !!el.querySelector(r.has)) && (r.hidden == null || el.hidden === r.hidden);
  P.armUpdate = (selector, ready) => new Promise((resolve, reject) => {
    let inputTs = null;
    const types = ['pointerdown', 'pointermove', 'pointerover', 'click', 'keydown', 'input', 'wheel'];
    const onInput = e => { if (inputTs === null) inputTs = e.timeStamp; };
    types.forEach(t => window.addEventListener(t, onInput, { capture: true, passive: true }));
    const stop = () => {
      obs.disconnect();
      types.forEach(t => window.removeEventListener(t, onInput, { capture: true }));
    };
    const check = () => {
      if (inputTs === null) return false;
      const el = document.querySelector(selector);
      if (!el) return false;
      if (ready && !matches(el, ready)) return false;
      stop();
      nextPaint(t => resolve(t - inputTs));
      return true;
    };
    const obs = new MutationObserver(() => { try { check(); } catch (e) { stop(); reject(e); } });
    obs.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
  });
}

/** A page with the probe and the map handle the browser regressions use; third-party requests (tiles)
  * are refused, so only the app is measured. `source`: the base-map family the page is told it has
  * (`local` or `online`, `/api/config`'s resolved `ui.base-map-source`); by default the server's. */
export async function newPerfPage(browser, origin, viewport = { width: 1440, height: 900 }, { source } = {}) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  await page.addInitScript(probe);
  // the map's own handle (web MapHandle), as the browser regressions use it
  await page.addInitScript(() => {
    Object.defineProperty(window, '__testMap', { configurable: true, get: () => window.histmersionMap });
  });
  // READ-ONLY: a request that is not a GET (a write) is refused and counted, whatever is clicked;
  // other origins (tiles) are refused. HISTMERSION_PERF_BUNDLE serves a locally built app bundle in
  // place of the server's, to measure a change against a live corpus without deploying it.
  const bundle = process.env.HISTMERSION_PERF_BUNDLE ? readFileSync(process.env.HISTMERSION_PERF_BUNDLE) : null;
  const blocked = [];
  await page.route('**/*', route => {
    const req = route.request(), u = new URL(req.url());
    if (u.origin !== origin) return route.abort();
    // reading an item notes it as opened (Library's "Recently opened"): the one write that reading makes, refused
    // here without counting, so the baseline stays read-only against a live server
    if (req.method() === 'POST' && u.pathname === '/api/reading/opened') return route.abort();
    if (req.method() !== 'GET' && req.method() !== 'HEAD') { blocked.push(`${req.method()} ${u.pathname}`); return route.abort(); }
    if (bundle && u.pathname === '/web/app.bundle.js') return route.fulfill({ body: bundle, contentType: 'text/javascript' });
    return route.continue();
  });
  await configOverride(page, source ? { baseMapSource: source } : {});
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  trackRequests(page);
  // CDP (heap, DOM counters, forced garbage collection) is Chromium's; elsewhere `cdp` is null
  const cdp = browser.browserType().name() === 'chromium' ? await context.newCDPSession(page) : null;
  if (cdp) await cdp.send('Performance.enable');
  return { context, page, cdp, errors, blocked };
}

/** Count the page's requests in flight, for [[quiet]]. */
export function trackRequests(page) {
  const st = { pending: new Set(), last: Date.now() };
  const done = r => { st.pending.delete(r); st.last = Date.now(); };
  page.on('request', r => { st.pending.add(r); st.last = Date.now(); });
  page.on('requestfinished', done);
  page.on('requestfailed', done);
  // a new document: what the old one had on its way was cancelled with it (and is not always reported)
  page.on('framenavigated', f => {
    if (f === page.mainFrame()) for (const r of [...st.pending]) if (!r.isNavigationRequest()) st.pending.delete(r);
  });
  page.__requests = st;
}

/** Until no request has been in flight for `ms` (Playwright's `networkidle` is a load state: once
  * reached it is never waited for again, whatever the page asks for later). A request that has had
  * its response for 5 s counts as finished (a cancelled one is not always reported). */
export async function quiet(page, ms = 400, timeout = 30000) {
  const st = page.__requests;
  if (!st) return page.waitForLoadState('networkidle');
  const t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    for (const r of [...st.pending]) if (r.__seen && Date.now() - r.__seen > 5000) { if (process.env.PERF_DEBUG) console.error("stale", r.url()); st.pending.delete(r); } else r.__seen ??= Date.now();
    if (st.pending.size === 0 && Date.now() - st.last >= ms) return;
    await new Promise(r => setTimeout(r, 50));
  }
  throw new Error(`requests still in flight after ${timeout} ms: ${[...st.pending].map(r => r.url()).join(', ')}`);
}

// ── statistics ──────────────────────────────────────────────────────────────────────────────────

export function pct(xs, p) {
  if (!xs.length) return 0;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.max(0, Math.ceil(p / 100 * s.length) - 1))];
}

const r1 = x => Math.round(x * 10) / 10;

/** Run `gesture` while the probe records: its wall time, every input's latency to the next paint, the
  * long tasks, and the frames (a frame is "dropped" when its interval is over 1.5 refresh periods, the period at
  * least 60 Hz's). */
export async function measure(page, gesture) {
  await page.evaluate(() => { window.__perf.reset(); window.__perf.recording = true; });
  const t0 = Date.now();
  await gesture();
  // the last input's paint lands after the gesture returns
  await page.evaluate(() => window.__perf.nextPaint());
  const wall = Date.now() - t0;
  const raw = await page.evaluate(() => { const P = window.__perf; P.recording = false;
    return { inputs: P.inputs, longTasks: P.longTasks, frames: P.frames }; });
  const lat = raw.inputs.map(i => i.latency);
  const gaps = raw.frames.slice(1).map((t, i) => t - raw.frames[i]);
  // the refresh period, never shorter than 60 Hz's: the budgets are set for 60 frames a second, and a browser
  // that paints headless at 120 Hz (Firefox) would otherwise count a 60 Hz frame as dropped
  const period = Math.max(pct(gaps, 25) || 16.7, 16.7);
  return {
    wallMs: wall,
    inputs: lat.length,
    inputP50: r1(pct(lat, 50)), inputP95: r1(pct(lat, 95)), inputMax: r1(Math.max(0, ...lat)),
    longTasks: raw.longTasks.length,
    longTaskMs: r1(raw.longTasks.reduce((s, t) => s + t.duration, 0)),
    longTaskMax: r1(Math.max(0, ...raw.longTasks.map(t => t.duration))),
    frames: gaps.length,
    droppedPct: gaps.length ? r1(100 * gaps.filter(g => g > 1.5 * period).length / gaps.length) : 0,
    frameP95: r1(pct(gaps, 95)), frameMax: r1(Math.max(0, ...gaps)),
  };
}

/** From the next input to the first painted change under `selector` (that satisfies `ready`: its text
  * `includes`, `lacks` or `equals` a string, it `has` a descendant, its `hidden` is as given): arm, run `trigger`,
  * await. */
export async function inputToUpdate(page, selector, trigger, ready = null, timeout = 15000) {
  const done = page.evaluate(([s, r]) => window.__perf.armUpdate(s, r), [selector, ready]);
  await page.waitForTimeout(0);
  await trigger();
  const ms = await Promise.race([done, new Promise((_, rej) => setTimeout(() => rej(new Error(`no update of ${selector}`)), timeout))]);
  return r1(ms);
}

// ── memory ─────────────────────────────────────────────────────────────────────────────────────

/** What the page holds, after two full garbage collections: the JS heap, the DOM (nodes, documents,
  * layout objects), the event listeners; the map's markers and the points it clusters; the app's own caches. */
export async function memory(page, cdp) {
  if (!cdp) return memoryWithoutCdp(page);
  for (let i = 0; i < 2; i++) await cdp.send('HeapProfiler.collectGarbage');
  const { metrics } = await cdp.send('Performance.getMetrics');
  const m = Object.fromEntries(metrics.map(x => [x.name, x.value]));
  const counters = await cdp.send('Memory.getDOMCounters');
  const page_ = await page.evaluate(() => {
    const map = window.__testMap;
    const app = typeof window.histmersionDiagnostics === 'function' ? window.histmersionDiagnostics() : null;
    return { layers: app?.map?.markers ?? 0, clustered: map ? map.points() : 0,
      shapes: map ? map.shapes('polities').length : 0, markers: document.querySelectorAll('.hd-marker').length,
      app: app ? JSON.parse(JSON.stringify(app)) : null };
  });
  return {
    heapMB: r1(m.JSHeapUsedSize / 1048576),
    heapTotalMB: r1(m.JSHeapTotalSize / 1048576),
    nodes: counters.nodes, documents: counters.documents, listeners: counters.jsEventListeners,
    layoutObjects: m.LayoutObjects,
    mapMarkers: page_.layers, clustered: page_.clustered, shapes: page_.shapes, markerIcons: page_.markers,
    caches: page_.app?.caches ?? null,
  };
}

/** [[memory]] where there is no CDP (Firefox): the DOM nodes the document holds (elements and text, counted
  * by walking it — live nodes only, where Chromium's counter also holds detached ones not yet collected), the
  * documents, the map's markers and the caches; the heap, the listeners and the layout objects cannot be
  * read from a page there, so they are `null` — and the budgets say which figures an engine lacks. */
async function memoryWithoutCdp(page) {
  const m = await page.evaluate(() => {
    let nodes = 0;
    const count = doc => { const w = doc.createTreeWalker(doc, NodeFilter.SHOW_ALL); while (w.nextNode()) nodes++; nodes++; };
    count(document);
    let documents = 1;
    for (const f of document.querySelectorAll('iframe')) { try { if (f.contentDocument) { count(f.contentDocument); documents++; } } catch (_) { /* another origin */ } }
    const map = window.__testMap;
    const app = typeof window.histmersionDiagnostics === 'function' ? window.histmersionDiagnostics() : null;
    return { nodes, documents, layers: app?.map?.markers ?? 0, clustered: map ? map.points() : 0,
      shapes: map ? map.shapes('polities').length : 0, markers: document.querySelectorAll('.hd-marker').length,
      app: app ? JSON.parse(JSON.stringify(app)) : null };
  });
  return {
    heapMB: null, heapTotalMB: null, nodes: m.nodes, documents: m.documents, listeners: null, layoutObjects: null,
    mapMarkers: m.layers, clustered: m.clustered, shapes: m.shapes, markerIcons: m.markers, caches: m.app?.caches ?? null,
  };
}
