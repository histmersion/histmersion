// Speed and memory budgets of the web UI, and the soak test that keeps it from leaking (#1949).
//
// Every interactive path (perf-paths.mjs) is measured in Chromium against the test server, fed
// live-sized synthetic feeds (perf-fixtures.mjs: 500 findings, 2 400 events, 1 600 polities over a
// range), and held to a budget: what the person feels (input → next paint, hover → card, keystroke →
// suggestions), what blocks the page (long tasks), what a gesture drops (frames), and what the page
// holds (JS heap, DOM nodes, listeners, the map's markers, the app's caches). Each budget is the number
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
import { engineName, isChromium, launch, wantsGpu } from './engine.mjs';
import { memory, newPerfPage, quiet } from './perf-lib.mjs';
import * as paths from './perf-paths.mjs';
import { eventsIn, routeFixtures, routeFullLoad } from './perf-fixtures.mjs';
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
  // the two vector base maps over the Aegean fixture, drawn by the GPU with the full overlay load (a whole
  // range's boundaries, 2 400 events, 500 findings): the map's own drawing, the overlays' markers and
  // labels, the pan and the wheel zooms within the same budgets as the rest of the app
  readableMap: { drawnMs: 4000, longTaskMax: 200, 'pan.inputP95': INPUT, 'pan.longTaskMax': 150, 'pan.droppedPct': 15,
    'zoom.longTaskMax': 150, 'zoom.droppedPct': 15, 'options.inputP95': INPUT, 'options.longTaskMax': 150 },
  memoryReadableMap: { heapMB: 110 },
  physicalMap: { drawnMs: 4000, longTaskMax: 200, 'pan.inputP95': INPUT, 'pan.longTaskMax': 150, 'pan.droppedPct': 15,
    'zoom.longTaskMax': 150, 'zoom.droppedPct': 15, 'options.inputP95': INPUT, 'options.longTaskMax': 150 },
  memoryPhysicalMap: { heapMB: 110 },
  // the same paths without a GPU (HISTMERSION_TEST_GPU=0: Chromium's software rasteriser, the map's cheapest
  // drawing, no relief), named apart so the GPU budgets above are never relaxed. What the person feels is held
  // to the same rule — input within INPUT, no long stall; the frame rate is not budgeted: measured 2026-10-23
  // on SwiftShader (M4 Pro), a pan drops 8–42 % of frames and a wheel zoom 11–29 %, varying run to run
  // (docs/research-map-styles.md "As built")
  eventLayerSoftware: { drawnMs: 2500, longTaskMax: 200, 'pan.inputP95': 200, 'pan.longTaskMax': 250, 'zoom.longTaskMax': 150 },
  readableMapSoftware: { drawnMs: 4000, longTaskMax: 200, 'pan.inputP95': INPUT, 'pan.longTaskMax': 150,
    'zoom.longTaskMax': 150, 'options.inputP95': INPUT, 'options.longTaskMax': 150 },
  physicalMapSoftware: { drawnMs: 4000, longTaskMax: 200, 'pan.inputP95': INPUT, 'pan.longTaskMax': 150,
    'zoom.longTaskMax': 150, 'options.inputP95': INPUT, 'options.longTaskMax': 150 },
  // #3764: the timeline's playback over 2 000 years with the boundaries on — every frame drawn from what is
  // held: the findings fetched four seconds ahead (it asked 60 times a second), the states' outlines sent
  // only when new ones arrive (the boundaries' answers, five a second), their colour and fade restyled on
  // most frames (they changed only when an answer came: 80 % of frames still)
  playback: { longTaskMax: 150, droppedPct: 15, findingsRequestsPerSecond: 1, outlineUploadsPerSecond: 6,
    stillFramesPct: 60 },
  // #4291: zooms across whole levels with the events layer on, in a desktop window (~1 600 points held for
  // its view, a few hundred markers drawn): every one animates its bubbles (none did: the layer counted the
  // points held, not the markers drawn), and the animation's start holds no frame long (making every marker
  // at the zoom's end and one layout per moving marker took 70–90 ms; measured 0 now)
  clusterZoom: { notAnimated: 0, longTaskMax: 50, droppedPct: 15 },
  // #4718: the same with EVERY layer on over a period at what the page really holds (perf-fixtures `fullLoad`:
  // 500 findings, 200 sites, 200 pictures, up to 1 500 events in view, 140 polities), the map animating (the
  // reader's "Map motion" left to follow a system that does not ask for reduced motion): no long task, the
  // frames within the zooms' budget, at a desktop's and a phone's size
  fullLoadZoom: { longTaskMax: 50, droppedPct: 15 },
  // a hundred rounds: what is allowed to remain (GC noise, a lazily built part)
  soak: { heapMB: 6, nodes: 150, listeners: 20, mapMarkers: 0 },
};

let browser;
before(async () => {
  browser = await launch();
});
after(async () => { await browser?.close(); });

/** The figures an engine cannot measure from a page (engine.mjs): Firefox has no CDP, so no heap, no
  * listener count and no layout-object count. Only these are skipped, only there; any other missing figure
  * fails, and in Chromium every figure is required. */
const UNMEASURABLE = isChromium ? new Set() : new Set(['heapMB', 'heapTotalMB', 'listeners', 'layoutObjects']);

const get = (o, path) => path.split('.').reduce((v, k) => v?.[k], o);

/** Every figure of `measured` named in `budget` is within it. */
function within(name, measured, budget = BUDGETS[name]) {
  if (report) console.log(`# ${name} ${JSON.stringify(measured)}`);
  for (const [k, max] of Object.entries(budget)) {
    const v = get(measured, k);
    if (v === null && UNMEASURABLE.has(k.split('.').at(-1))) { if (report) console.log(`# ${name}.${k}: not measurable in ${engineName}`); continue; }
    assert.equal(typeof v, 'number', `${name}.${k} was measured (${JSON.stringify(measured)})`);
    assert.ok(v <= max, `${name}.${k} = ${v}, over its budget of ${max} (${JSON.stringify(measured)})`);
  }
}

/** A page with the probe and the fixtures; it fails on an application error or a refused write. The
  * app's own budgets are measured over the online base map (its tiles refused: nothing drawn), as they
  * were set; the local maps' budgets (`source: 'local'`) over the local map. */
async function withPerfPage(run, viewport, { source = 'online' } = {}) {
  const s = await newPerfPage(browser, origin, viewport, { source });
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
    // every figure is the median of three runs (#2130): one run under load measured a 2 540 ms draw
    const measured = await paths.eventLayer(page, baseURL, -3500, 1500, { atLeast: 2000, runs: 3 });
    const software = await page.evaluate(() => window.histmersionMap.capabilities().software);
    within(software ? 'eventLayerSoftware' : 'eventLayer', measured);
    within('memoryEventLayer', await memory(page, cdp));
  });
});

for (const [style, name] of [['readable', 'Readable'], ['physical', 'Physical']]) {
  test(`budgets: ${name} with the full overlay load draws, pans and zooms within the pan's budget`, async t => {
    const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
    if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
    await withPerfPage(async ({ page, cdp }) => {
      const m = await paths.vectorMap(page, baseURL, style);
      // the events are the tiles of the view (docs/map-layers.md): its share of the fixture's 2 400, not all of them
      assert.ok(m.points >= 400 && m.shapes >= 1000, `the full load: ${m.points} points, ${m.shapes} shapes`);
      // the GPU budgets on a GPU; the software path (HISTMERSION_TEST_GPU=0) has its own, named apart
      const software = await page.evaluate(() => window.histmersionMap.capabilities().software);
      assert.equal(software, !wantsGpu(), `the map took the ${software ? 'software' : 'GPU'} path`);
      within(software ? `${style}MapSoftware` : `${style}Map`, m);
      within(`memory${name}Map`, await memory(page, cdp));
    }, undefined, { source: 'local' });
  });
}

test('budgets: playback draws every frame from what it holds, the states restyled in place', async () => {
  await withPerfPage(async ({ page }) => {
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries"]'));
    const m = await paths.playback(page, baseURL, -3000, -1000);
    assert.ok(m.states > 0, `states are drawn in playback: ${JSON.stringify(m)}`);
    within('playback', m);
  });
});

// a full-HD window: the fixture's view at zoom 4 holds over a thousand points there (a 1440×900 one ~900)
test('budgets: in a 1920×1080 window a zoom animates the bubbles without a long frame', async () => {
  await withPerfPage(async ({ page }) => {
    await page.route(/\/api\/events\/geo(\?|$)/, r => r.fulfill({ json: eventsIn(new URL(r.request().url()).searchParams.get('area')) }));
    const m = await paths.clusterZoom(page, baseURL);
    assert.ok(m.held > 1000 && m.drawn < 1000, `a wide window holds over a thousand points and draws fewer markers: ${JSON.stringify(m)}`);
    within('clusterZoom', m);
  }, { width: 1920, height: 1080 });
});

for (const viewport of [{ width: 1920, height: 1080 }, { width: 390, height: 844 }]) {
  test(`budgets: at ${viewport.width}×${viewport.height} with every layer on, an animated zoom holds no long frame`, async () => {
    await withPerfPage(async ({ page }) => {
      await routeFullLoad(page);
      await page.emulateMedia({ reducedMotion: 'no-preference' });
      const start = await paths.fullLoadView(page, baseURL);
      const m = await paths.fullLoadZooms(page);
      if (report) console.log(`# fullLoadZoom ${viewport.width}x${viewport.height} ${JSON.stringify(start)}`);
      assert.ok(m.zooms - m.notAnimated >= 1 && m.motionFrames >= 3, `the zooms animate their regroup: ${JSON.stringify(m)}`);
      if (isChromium) within('fullLoadZoom', m);
      else {
        // Firefox has no Long Tasks API: a 10 ms timer arriving late stands in (perf-lib), and these very zooms
        // with the map still ("Map motion: never") measure 52–60 ms there at 1920×1080. What is held is that the
        // animation adds no long task of its own: within 50 ms, or within 20 ms of the same zooms held still
        await paths.fullLoadView(page, baseURL, { motion: 'never' });
        const still = await paths.fullLoadZooms(page);
        if (report) console.log(`# fullLoadZoom still ${JSON.stringify(still)}`);
        within('fullLoadZoom', m, { ...BUDGETS.fullLoadZoom, longTaskMax: Math.max(BUDGETS.fullLoadZoom.longTaskMax, still.longTaskMax + 20) });
      }
    }, viewport);
  });
}

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

test('a page left stops polling, and a window\'s page reloaded in place keeps one set of handlers', async () => {
  await withPerfPage(async ({ page, cdp }) => {
    await page.waitForFunction(() => !!window.__testMap);
    let polls = 0;
    page.on('request', r => { if (/\/api\/jobs(\?|$)/.test(new URL(r.url()).pathname + new URL(r.url()).search)) polls++; });
    // the jobs are Research › Activity › Background jobs: shown, then left for Explore
    await page.goto(new URL('/research/activity/jobs', baseURL).href);
    await page.locator('.jobs-pane').waitFor();
    await page.locator('.places a[data-nav="explore"]').click();
    await page.waitForFunction(() => !document.querySelector('.jobs-pane'));
    const atClose = polls;
    await page.waitForTimeout(3500);   // past the first poll's delay (1 s) and its backoff
    assert.equal(polls, atClose, 'no poll after the page was left');

    // a source window whose page links to itself: following the link reloads the content in place
    await page.locator('#list [data-ck]').first().click();
    const win = page.locator('.wm-window');
    await win.locator('a.zz-self').waitFor();
    const counts = [];
    for (let i = 0; i < 6; i++) {
      await win.locator('a.zz-self').click();
      await quiet(page);
      if (cdp) counts.push((await cdp.send('Memory.getDOMCounters')).jsEventListeners);
    }
    // the listener count is Chromium's (CDP); in Firefox the one-fetch-per-click check below still catches
    // handlers piling up
    if (cdp) assert.ok(counts.at(-1) - counts[1] <= 2, `listeners grow with each in-place reload: ${counts}`);
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
