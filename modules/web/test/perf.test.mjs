// Speed and memory budgets of the web UI, and the soak test that keeps it from leaking (#1949).
//
// Every interactive path (perf-paths.mjs) is measured in Chromium against the test server, fed
// live-sized synthetic feeds (perf-fixtures.mjs: 500 findings, 2 400 events, 1 600 polities over a
// range), and held to a budget: what the person feels (input → next paint, hover → card, keystroke →
// suggestions), what blocks the page (long tasks), what a gesture drops (frames), and what the page
// holds (JS heap, DOM nodes, listeners, Leaflet layers, the app's caches). Each budget is the number
// measured on the test server (docs/research-web-perf.md, "Budgets") with headroom, so a regression
// fails and a busy machine does not; HISTMERSION_PERF_REPORT=1 prints what was measured.
//
// The soak test repeats a session (windows opened and closed, tabs, a span drag, every layer toggled,
// a card hovered, a window's page reloaded in place) and fails when the heap, the DOM, the listeners
// or the map's layers keep growing with the rounds. HISTMERSION_SOAK_ROUNDS sets the rounds (100).
//
// Read-only like the other browser regressions: a non-GET request is refused (perf-lib.mjs).

import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { chromium } from 'playwright';
import { memory, newPerfPage, quiet } from './perf-lib.mjs';
import * as paths from './perf-paths.mjs';
import { routeFixtures } from './perf-fixtures.mjs';
import { soak } from './perf-soak.mjs';

const baseURL = process.env.HISTMERSION_SERVER_URL;
assert.ok(baseURL, 'Set HISTMERSION_SERVER_URL to the test server');
const origin = new URL(baseURL).origin;
const report = !!process.env.HISTMERSION_PERF_REPORT;
const rounds = Number(process.env.HISTMERSION_SOAK_ROUNDS || 100);

/** The budgets: ms unless named otherwise. INPUT is the ≤ 100 ms rule — a hover or a keystroke shows
  * its effect within it. */
const INPUT = 100;
const BUDGETS = {
  firstRender: { mapMs: 1500, markersMs: 2000, listMs: 2000, longTaskMax: 200 },
  memoryAfterLoad: { heapMB: 45, listeners: 1200 },
  mapPan: { inputP95: INPUT, longTaskMax: 150, droppedPct: 15 },
  timelineDrag: { inputP95: INPUT, longTaskMax: 150, droppedPct: 15, releaseToListMs: 600 },
  hoverCards: { shownMax: INPUT, filledMax: INPUT, warmMax: INPUT },
  filterTyping: { keyP95: INPUT, keyMax: 150, suggestMs: 400, previewToListMs: 250 },
  articleOpen: { windowMs: INPUT, contentMs: 300 },
  rangeAreas: { drawnMs: 3000, 'load.longTaskMax': 200, 'drag.inputP95': INPUT, 'drag.droppedPct': 15 },
  memoryRangeAreas: { heapMB: 110 },
  // #1647: among ~2 900 points the cluster group no longer animates a zoom (ClusterMotion): the pan's input
  // p95 105 → 75–80 ms, its dropped frames 7 → 2 %, a wheel zoom's 10–13 % → 3 %, and the page holds 7 500
  // DOM nodes and 1 000 listeners instead of 18 600 and 2 700 — the budgets below fail on the animated form
  eventLayer: { drawnMs: 2500, longTaskMax: 200, 'pan.inputP95': 200, 'pan.longTaskMax': 250, 'pan.droppedPct': 12,
    'zoom.longTaskMax': 150, 'zoom.droppedPct': 8 },
  memoryEventLayer: { heapMB: 75, nodes: 12000, listeners: 2000 },
  // the terrain base map (the Aegean fixture): vector tiles on canvas, the relief shaded in the page
  terrainMap: { drawnMs: 3000, longTaskMax: 200, 'pan.inputP95': INPUT, 'pan.longTaskMax': 150, 'pan.droppedPct': 15,
    'zoom.longTaskMax': 150, 'zoom.droppedPct': 15 },
  memoryTerrainMap: { heapMB: 60 },
  // a hundred rounds: what is allowed to remain (GC noise, a lazily built part)
  soak: { heapMB: 6, nodes: 150, listeners: 20, leafletLayers: 0 },
};

let browser;
before(async () => {
  browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
});
after(async () => { await browser?.close(); });

const get = (o, path) => path.split('.').reduce((v, k) => v?.[k], o);

/** Every figure of `measured` named in `budget` is within it. */
function within(name, measured, budget = BUDGETS[name]) {
  if (report) console.log(`# ${name} ${JSON.stringify(measured)}`);
  for (const [k, max] of Object.entries(budget)) {
    const v = get(measured, k);
    assert.equal(typeof v, 'number', `${name}.${k} was measured (${JSON.stringify(measured)})`);
    assert.ok(v <= max, `${name}.${k} = ${v}, over its budget of ${max} (${JSON.stringify(measured)})`);
  }
}

/** A page with the probe and the fixtures; it fails on an application error or a refused write. */
async function withPerfPage(run, viewport) {
  const s = await newPerfPage(browser, origin, viewport);
  try {
    await routeFixtures(s.page);
    await s.page.goto(baseURL);
    await s.page.evaluate(() => localStorage.clear());
    await run(s);
    assert.deepEqual(s.errors, [], 'application JavaScript errors');
    assert.deepEqual(s.blocked, [], 'requests that would write');
  } finally {
    await s.context.close();
  }
}

test('budgets: first render, map pan, timeline drag, hover cards, filter typing, article open', async () => {
  await withPerfPage(async ({ page, cdp }) => {
    within('firstRender', await paths.firstRender(page, baseURL));
    await quiet(page);
    within('memoryAfterLoad', await memory(page, cdp));
    within('mapPan', await paths.mapPan(page));
    within('timelineDrag', await paths.timelineDrag(page, -3500, -500));
    within('hoverCards', await paths.hoverCards(page));
    within('filterTyping', await paths.filterTyping(page, 'bronze age', 'b'));
    within('articleOpen', await paths.articleOpen(page, baseURL));
  });
});

test('budgets: the boundaries over a whole range (1 600 shapes) load and drag without long stalls', async () => {
  await withPerfPage(async ({ page, cdp }) => {
    within('rangeAreas', await paths.rangeAreas(page, baseURL, -3000, 2000));
    const m = await memory(page, cdp);
    within('memoryRangeAreas', m);
    // the place-GeoJSON cache stays inside its ceiling, however many ranges the drag asked for
    const geo = m.caches['places.geo'];
    assert.ok(geo.entries <= geo.maxEntries && (geo.weight <= geo.maxWeight || geo.entries === 1), JSON.stringify(geo));
  });
});

test('budgets: the events layer draws 2 400 markers, pans and zooms smoothly', async () => {
  await withPerfPage(async ({ page, cdp }) => {
    within('eventLayer', await paths.eventLayer(page, baseURL, -3500, 1500, { atLeast: 2000 }));
    within('memoryEventLayer', await memory(page, cdp));
  });
});

test('budgets: the terrain base map draws, pans and zooms within the pan\'s budget', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no terrain extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPerfPage(async ({ page, cdp }) => {
    within('terrainMap', await paths.terrainMap(page, baseURL));
    within('memoryTerrainMap', await memory(page, cdp));
  });
});

test('every cache the page keeps is bounded', async () => {
  await withPerfPage(async ({ page }) => {
    await page.waitForFunction(() => !!window.__testMap);
    const caches = await page.evaluate(() => window.histmersionDiagnostics().caches);
    // the label figures follow the shapes on the map (pruned with them); everything else has a ceiling
    const perFeature = new Set(['labels.entries', 'labels.state', 'findings.kept']);
    for (const [name, c] of Object.entries(caches)) {
      if (perFeature.has(name)) continue;
      assert.ok(c.maxEntries > 0, `${name} has an entry ceiling: ${JSON.stringify(c)}`);
      assert.ok(c.entries <= c.maxEntries, `${name} is inside it: ${JSON.stringify(c)}`);
    }
    assert.ok(caches['places.geo'].maxWeight > 0, 'the place GeoJSON is bounded by size too');
  });
});

test('a closed window stops polling, and its page reloaded in place keeps one set of handlers', async () => {
  await withPerfPage(async ({ page, cdp }) => {
    await page.waitForFunction(() => !!window.__testMap);
    let polls = 0;
    page.on('request', r => { if (/\/api\/jobs(\?|$)/.test(new URL(r.url()).pathname + new URL(r.url()).search)) polls++; });
    await page.locator('.fbar .tool-btns button[title="jobs"]:visible').click();
    await page.locator('.jobs-pane').waitFor();
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('.wm-window'));
    const atClose = polls;
    await page.waitForTimeout(3500);   // past the first poll's delay (1 s) and its backoff
    assert.equal(polls, atClose, 'no poll after the window closed');

    // a source window whose page links to itself: following the link reloads the content in place
    await page.locator('#list [data-ck]').first().click();
    const win = page.locator('.wm-window');
    await win.locator('a.zz-self').waitFor();
    const counts = [];
    for (let i = 0; i < 6; i++) {
      await win.locator('a.zz-self').click();
      await quiet(page);
      counts.push((await cdp.send('Memory.getDOMCounters')).jsEventListeners);
    }
    assert.ok(counts.at(-1) - counts[1] <= 2, `listeners grow with each in-place reload: ${counts}`);
    // and one click asks for the page once, not once per reload so far
    let fetched = 0;
    page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/source/')) fetched++; });
    await win.locator('a.zz-self').click();
    await quiet(page);
    assert.equal(fetched, 1, 'one fetch per click');
  });
});

test(`soak: ${rounds} rounds of windows, tabs, span drags, layer toggles and cards leave no growth`, async () => {
  await withPerfPage(async ({ page, cdp }) => {
    await page.waitForFunction(() => !!window.__testMap);
    await quiet(page);
    const r = await soak(page, cdp, rounds);
    if (report) console.log(`# soak ${JSON.stringify(r)}`);
    within('soak', r.growth);
    for (const [name, c] of Object.entries(r.after.caches ?? {}))
      if (c.maxEntries > 0) assert.ok(c.entries <= c.maxEntries, `${name} stayed inside its ceiling: ${JSON.stringify(c)}`);
  });
});
