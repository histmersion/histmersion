// The map's tiled layers (sites, pictures, events: `/api/tiles`, docs/map-layers.md) at a corpus's real
// density, every layer on: what one view asks for and holds at zooms 2–10 over a single year and over
// -3000..2000, the pan and the timeline drag on top of it, and playback. READ-ONLY: GETs, map moves and
// the timeline only. Prints JSON (`--md`: the Markdown rows of docs/research-web-perf.md).
//
//   HISTMERSION_SERVER_URL=http://127.0.0.1:18437 node modules/web/test/perf-tiles.mjs [--md]

import { chromium } from 'playwright';
import { gpuArgs } from './engine.mjs';
import { measure, newPerfPage, quiet } from './perf-lib.mjs';
import * as paths from './perf-paths.mjs';

const baseURL = process.env.HISTMERSION_SERVER_URL;
if (!baseURL) { console.error('Set HISTMERSION_SERVER_URL'); process.exit(1); }
const origin = new URL(baseURL).origin;
const md = process.argv.includes('--md');
const layers = '["boundaries","sites","media","events"]';
const spans = { 'year -500': [-500, -500], '-3000..2000': [-3000, 2000] };
const views = [[2, [20, 20]], [4, [38, 20]], [6, [41.9, 12.5]], [8, [41.9, 12.5]], [10, [41.9, 12.5]]];

const browser = await chromium.launch({ headless: true, args: gpuArgs(),
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });

/** Every tile request the page (and the map's workers) made: when it started, how long it took, its bytes. */
const tileLog = [];
function trackTiles(page) {
  page.on('requestfinished', async r => {
    if (!r.url().includes('/api/tiles/')) return;
    const t = r.timing(), sizes = await r.sizes().catch(() => ({ responseBodySize: 0 }));
    tileLog.push({ at: Date.now(), ms: t.responseEnd > 0 ? t.responseEnd : 0, bytes: sizes.responseBodySize });
  });
}

/** The tile requests since `mark` (`Date.now()`): how many, their bytes and durations. */
async function tilesSince(page, mark) {
  const rs = tileLog.filter(e => e.at >= mark);
  const d = rs.map(r => r.ms).sort((a, b) => a - b);
  const b = rs.map(r => r.bytes);
  return { tiles: rs.length, kb: Math.round(b.reduce((s, x) => s + x, 0) / 1024),
    maxKb: Math.round(Math.max(0, ...b) / 1024), p50Ms: Math.round(d[Math.floor(d.length / 2)] ?? 0),
    maxMs: Math.round(d[d.length - 1] ?? 0) };
}

/** Until every tiled layer has read the tiles of the view (the map has them all) and nothing is in flight. */
async function tilesIn(page) {
  await quiet(page, 500, 60000);
  await page.waitForFunction(() => window.__testMap.isDrawn(), null, { timeout: 60000 }).catch(() => {});
  await quiet(page, 300, 60000);
}

/** What the map holds and draws now. */
const held = page => page.evaluate(() => ({ points: window.__testMap.points(), tiled: window.__testMap.tiled(),
  drawn: document.querySelectorAll('#map .hd-pane--markers .hd-marker, #map .hd-pane--events .hd-marker, #map .hd-pane--sites .hd-marker').length }));

const out = { url: baseURL, at: new Date().toISOString(), views: [], gestures: {} };
const record = async (name, f) => { try { out.gestures[name] = await f(); } catch (e) { out.gestures[name] = { error: e.message }; } };
const { context, page, errors } = await newPerfPage(browser, origin, { width: 1440, height: 900 });
trackTiles(page);
try {
  await page.goto(baseURL);
  await page.evaluate(l => { localStorage.clear(); localStorage.setItem('histmersion.ui.layers', l); }, layers);
  for (const [name, [from, to]] of Object.entries(spans)) {
    await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
    await page.reload();
    await paths.valueOf(page, () => !!window.__testMap);
    for (const [zoom, at] of views) {
      const mark = Date.now();
      const longTasks0 = await page.evaluate(() => window.__perf.longTasks.length);
      await page.evaluate(([c, z]) => window.__testMap.setView(c, z, { animate: false }), [at, zoom]);
      await tilesIn(page);
      const last = tileLog.filter(e => e.at >= mark).map(e => e.at);
      const ms = last.length ? Math.max(...last) - mark : 0;
      const lt = await page.evaluate(n => window.__perf.longTasks.slice(n).map(t => t.duration), longTasks0);
      out.views.push({ span: name, zoom, ...(await tilesSince(page, mark)), lastTileMs: ms,
        longTaskMax: Math.round(Math.max(0, ...lt)), ...(await held(page)) });
    }
  }
  // the gestures on top of a whole range at a world view and at a regional one
  for (const [zoom, at] of [[3, [30, 20]], [6, [41.9, 12.5]]]) {
    await page.goto(new URL('/#from=-3000&to=2000', baseURL).href);
    await page.reload();
    await paths.valueOf(page, () => !!window.__testMap);
    await page.evaluate(([c, z]) => window.__testMap.setView(c, z, { animate: false }), [at, zoom]);
    await tilesIn(page);
    await record(`pan z${zoom}`, () => paths.mapPan(page));
    const mark = Date.now();
    await record(`timeline drag z${zoom}`, () => paths.timelineDrag(page, -1500, -500));
    await tilesIn(page);
    await record(`after drag z${zoom}`, async () => ({ ...(await held(page)), ...(await tilesSince(page, mark)) }));
  }
  const mark = Date.now();
  await record('playback', async () => ({ ...(await paths.playback(page, baseURL, -3000, -1000, 5)), ...(await tilesSince(page, mark)) }));
} finally {
  out.pageErrors = errors;
  await context.close();
  await browser.close();
}

if (md) {
  console.log('| span | zoom | tiles | KB (largest) | tile p50 / max ms | last tile ms | longest task | points held · drawn | sites items · cells (counted) | media | events |');
  console.log('|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|');
  for (const v of out.views) {
    const t = l => `${v.tiled[l].items} · ${v.tiled[l].cells} (${v.tiled[l].counted})`;
    console.log(`| ${v.span} | ${v.zoom} | ${v.tiles} | ${v.kb} (${v.maxKb}) | ${v.p50Ms} / ${v.maxMs} | ${v.lastTileMs} | ${v.longTaskMax} | ${v.points} · ${v.drawn} | ${t('sites')} | ${t('media')} | ${t('events')} |`);
  }
  console.log();
}
console.log(JSON.stringify(out, null, 1));
