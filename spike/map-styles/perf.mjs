// The study's renderer measurement (docs/research-map-styles.md): the same gestures as the app's perf budgets
// (perf-paths.mjs: a 40-step drag + four wheel zooms, then six wheel zooms) over
//   app-natural    the app's current Natural preset (protomaps-leaflet canvas + our relief layer)
//   gl-readable    the prototype Readable style, native MapLibre
//   gl-physical    the prototype Physical style, native MapLibre
//   gl-leaflet     the Readable style in Leaflet through maplibre-gl-leaflet, with N DOM markers on top
//   gl-markers     native MapLibre with the same N points as a circle layer
// in headless Chromium, software GL (SwiftShader: the app's budget conditions) or the GPU (--gpu).
//   node perf.mjs <app-origin> <proto-origin> [--gpu] [--runs 3] [--only kind,kind]
import { chromium } from '../../modules/web/node_modules/playwright/index.mjs';
import { probe, measure, memory, pct, trackRequests, quiet } from '../../modules/web/test/perf-lib.mjs';

const args = process.argv.slice(2);
const [app, proto] = args;
const gpu = args.includes('--gpu');
const runs = args.includes('--runs') ? +args[args.indexOf('--runs') + 1] : 3;
const only = args.includes('--only') ? args[args.indexOf('--only') + 1].split(',') : null;
const VIEW = [37.9, 25.4, 7];   // the app's terrain budget view (perf-paths.mjs terrainMap)
const launch = () => chromium.launch({ headless: true,
  args: gpu ? ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] : [] });

async function gestures(page, sel) {
  const box = await page.locator(sel).boundingBox();
  const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  const pan = await measure(page, async () => {
    await page.mouse.move(cx, cy); await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(cx - i * 12, cy + (i % 5)); await page.waitForTimeout(16); }
    await page.mouse.up();
    for (const dy of [-200, -200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(300); }
  });
  await page.waitForTimeout(1500);
  await page.mouse.move(cx, cy);
  const zoom = await measure(page, async () => {
    for (const dy of [-200, -200, -200, 200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(400); }
  });
  return { pan, zoom };
}

async function one(kind) {
  const browser = await launch();   // a browser per run: a crashed GPU process never spoils the next run
  try { return await run(browser, kind); } finally { await browser.close(); }
}

async function run(browser, kind) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  await page.addInitScript(probe);
  trackRequests(page);
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Performance.enable');
  const [lat, lon, z] = VIEW;
  let drawnMs, sel = '#map', renderer = null;
  if (kind === 'app-natural') {
    await page.addInitScript(() => {
      let leaflet;
      Object.defineProperty(window, 'L', { configurable: true, get: () => leaflet, set: v => {
        leaflet = v; let f = v.map;
        Object.defineProperty(v, 'map', { configurable: true, enumerable: true, set: fn => { f = fn; },
          get: () => function (...a) { const m = f.apply(v, a); window.__testMap = m; return m; } });
      } });
    });
    await page.route('**/*', r => new URL(r.request().url()).origin === app ? r.continue() : r.abort());
    await page.goto(app + '/');
    await page.evaluate(() => { localStorage.clear(); localStorage.setItem('histmersion.ui.baseMap', '["terrain"]'); localStorage.setItem('histmersion.ui.terrainStyle', '["natural"]'); });
    await page.reload();
    await page.waitForFunction(() => !!window.__testMap);
    await page.waitForTimeout(3000);
    const t0 = await page.evaluate(() => performance.now());
    await page.evaluate(([a, b, zz]) => window.__testMap.setView([a, b], zz, { animate: false }), [lat, lon, z]);
    await page.waitForFunction(() => document.querySelector('.terrain-relief canvas') && document.querySelector('.terrain-labels canvas'), null, { timeout: 60000 });
    await quiet(page);   // as perf-paths.mjs terrainMap: every tile and name requested and answered
    drawnMs = Math.round(await page.evaluate(() => performance.now()) - t0);
  } else {
    const style = kind === 'gl-physical' ? 'physical' : 'readable';
    const mode = kind === 'gl-leaflet' ? 'leaflet' : 'native';
    const markers = (kind === 'gl-leaflet' || kind === 'gl-markers') ? 300 : 0;
    await page.route('**/*', r => { const u = new URL(r.request().url()); return (u.origin === proto || !u.protocol.startsWith('http')) ? r.continue() : r.abort(); });
    await page.goto(`${proto}/?style=${style}&mode=${mode}&lat=${lat}&lon=${lon}&z=${z}&markers=${markers}&shot=1`);
    await page.waitForFunction(() => window.__idle === true, null, { timeout: 90000 });
    drawnMs = Math.round(await page.evaluate(() => window.__idleAt - window.__t0));   // map created → first 'idle' (every tile drawn)
    renderer = await page.evaluate(() => { const gl = document.createElement('canvas').getContext('webgl2'); const d = gl && gl.getExtension('WEBGL_debug_renderer_info'); return d ? gl.getParameter(d.UNMASKED_RENDERER_WEBGL) : null; });
  }
  await page.waitForTimeout(1000);
  const g = await gestures(page, sel);
  await page.waitForTimeout(2000);
  const mem = await memory(page, cdp);
  await ctx.close();
  return { kind, drawnMs, renderer, pan: g.pan, zoom: g.zoom, heapMB: mem.heapMB, errors: errors.length };
}

const kinds = only ?? ['app-natural', 'gl-readable', 'gl-physical', 'gl-leaflet', 'gl-markers'];
const med = xs => pct(xs, 50);
for (const k of kinds) {
  const rs = [];
  for (let i = 0, tries = 0; rs.length < runs && tries < runs + 3; tries++) {
    try { rs.push(await one(k)); } catch (e) { console.error(`${k}: a run failed (${e.message.split('\n')[0]}); retrying`); }
  }
  if (!rs.length) { console.log(JSON.stringify({ kind: k, gpu, failed: true })); continue; }
  const f = path => med(rs.map(r => path.split('.').reduce((v, p) => v[p], r)));
  console.log(JSON.stringify({ kind: k, gpu, runs, renderer: rs[0].renderer,
    drawnMs: f('drawnMs'), heapMB: f('heapMB'),
    pan: { inputP95: f('pan.inputP95'), inputMax: f('pan.inputMax'), droppedPct: f('pan.droppedPct'), longTaskMax: f('pan.longTaskMax'), frameP95: f('pan.frameP95') },
    zoom: { inputP95: f('zoom.inputP95'), droppedPct: f('zoom.droppedPct'), longTaskMax: f('zoom.longTaskMax'), frameP95: f('zoom.frameP95') },
    errors: rs.map(r => r.errors) }));
}
