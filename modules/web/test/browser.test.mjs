import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { appendFileSync } from 'node:fs';
import { engineName, isChromium, isFirefox, launch, launchChromiumWithout, launchSoftwareChromium, wantsGpu } from './engine.mjs';
import { routeFullLoad } from './perf-fixtures.mjs';
import { fullLoadView, motionTrace } from './perf-paths.mjs';
import { trackRequests } from './perf-lib.mjs';
import { routeTiles } from './tile-fixtures.mjs';

// Use a separately started test server; these tests only GET data and change browser-local state.
const baseURL = process.env.HISTMERSION_SERVER_URL;
assert.ok(baseURL, 'Set HISTMERSION_SERVER_URL to the test server');
const origin = new URL(baseURL).origin;
const consoleLog = process.env.HISTMERSION_TEST_CONSOLE_LOG;
let browser;
before(async () => {
  browser = await launch();
});
after(async () => { await browser?.close(); });

/** `source`: the base-map family the page is told it has (`/api/config`'s resolved `ui.base-map-source`),
  * `local` or `online`; by default what the server says. `outside`: every request to another host is
  * recorded there (and refused, as always). */

async function withPage(viewport, run, { source, outside } = {}) {
  // Firefox has no mobile emulation: a narrow viewport with touch is its phone (the layout follows the width)
  const context = await browser.newContext({ viewport, isMobile: isChromium && viewport.width < 760, hasTouch: true });
  try {
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    // HISTMERSION_TEST_CONSOLE_LOG: every warning and error the page prints, with the test, to that file
    if (consoleLog) page.on('console', m => { if (m.type() === 'warning' || m.type() === 'error')
      appendFileSync(consoleLog, `${engineName}\t${m.type()}\t${m.text().replace(/\s+/g, ' ').slice(0, 400)}\n`); });
    // the map's own handle (web MapHandle): its view in the app's zooms, whatever library draws it
    await page.addInitScript(() => {
      Object.defineProperty(window, '__testMap', { configurable: true, get: () => window.histmersionMap });
    });
    await page.route('**/*', route => {
      if (new URL(route.request().url()).origin === origin) return route.continue();
      outside?.push(route.request().url());
      return route.abort(); // No third-party tile servers are needed.
    });
    if (source) await page.route('**/api/config', async route => {
      const res = await route.fetch();
      await route.fulfill({ response: res, json: { ...(await res.json()), baseMapSource: source } });
    });
    await page.goto(baseURL);
    await page.waitForFunction(() => window.__testMap);
    await run(page);
    assert.deepEqual(errors, [], 'application JavaScript errors');
  } finally {
    await context.close();
  }
}

// A point is in the map's one cluster group, which re-places its markers after a view change (and may
// animate them there): wait until the marker drawn for `selector` stands at its own point.
async function settledAt(page, selector, lat, lon) {
  await page.waitForFunction(([sel, la, lo]) => {
    const el = document.querySelector(sel);
    if (!el) return false;
    const icon = el.closest('.hd-marker') || el;
    const r = icon.getBoundingClientRect(), m = document.getElementById('map').getBoundingClientRect();
    const p = window.__testMap.latLngToContainerPoint([la, lo]);
    return Math.abs(r.left + r.width / 2 - (m.left + p.x)) < 2 && Math.abs(r.top + r.height / 2 - (m.top + p.y)) < 2;
  }, [selector, lat, lon]);
}

// A marker stands still: the same element at the same place for `ms`, the map not moving and the list not
// loading. Hovering before that races the first draw (#2705): the hover card waits 180 ms of rest
// (HoverIntent), and a marker that moves out from under the pointer in that time (a late camera or layout
// change while a cold server answers the first requests — the base map's style, the list) or is drawn
// anew (the events feed is asked again for each view) drops its pending card, and a pointer that does not
// move sends no second `mouseover`, so the card never comes.
async function idleMarker(page, selector, ms = 400) {
  await page.waitForFunction(([sel, ms]) => {
    const el = document.querySelector(sel);
    const S = (window.__idleMarker ??= {});
    const r = el?.getBoundingClientRect();
    const at = r ? `${Math.round(r.left)},${Math.round(r.top)}` : null;
    const busy = !el || window.__testMap.isMoving() || !!document.querySelector('#count.is-loading');
    if (busy || S.sel !== sel || S.el !== el || S.at !== at) {
      Object.assign(S, { sel, el, at, since: performance.now() });
      return false;
    }
    return performance.now() - S.since >= ms;
  }, [selector, ms], { polling: 50 });
}

// Hover the marker `selector` once it stands still, until `card` shows (a marker re-drawn after the hover
// takes its pending card with it: the pointer is moved off and back, at most three times).
async function hoverForCard(page, selector, card) {
  for (let attempt = 1; ; attempt++) {
    await idleMarker(page, selector);
    await page.locator(selector).hover();
    try {
      await page.locator(card).first().waitFor({ timeout: 5000 });
      return;
    } catch (e) {
      if (attempt >= 3) throw e;
      await page.mouse.move(5, 5);
    }
  }
}

async function synchronizedSize(page) {
  await page.waitForFunction(() => {
    const element = document.getElementById('map');
    const size = window.__testMap.getSize(), canvas = element.querySelector('canvas');
    return size.x === element.clientWidth && size.y === element.clientHeight && canvas &&
      Math.abs(canvas.getBoundingClientRect().height - element.clientHeight) < 1;
  });
  return page.evaluate(() => {
    const size = window.__testMap.getSize();
    return { width: size.x, height: size.y };
  });
}

const phone = { width: 390, height: 844 };
test('the map follows the phone timeline, its detail strip, and closing the timeline', async () => {
  await withPage(phone, async page => {
    const initial = await synchronizedSize(page);
    await page.locator('.mb-timeline').click();
    const timeline = await synchronizedSize(page);
    assert.ok(timeline.height < initial.height, 'timeline should shrink the map');
    await page.locator('[title="show the detail strip"]').click();
    const detail = await synchronizedSize(page);
    assert.ok(detail.height < timeline.height, 'detail strip should shrink the map again');
    await page.locator('.mb-timeline').click();
    assert.deepEqual(await synchronizedSize(page), initial);
  });
});

test('the map follows a desktop sidebar resize without a window resize', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    const initial = await synchronizedSize(page);
    const handle = await page.locator('.sidebar-resize').boundingBox();
    assert.ok(handle);
    const x = handle.x + handle.width / 2, y = handle.y + handle.height / 2;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x + 90, y, { steps: 8 });
    await page.mouse.up();
    const resized = await synchronizedSize(page);
    assert.notEqual(resized.width, initial.width, 'sidebar drag should change the map width');
  });
});

test('a year-slider drag changes the filter without dismissing the drawer', async () => {
  await withPage(phone, async page => {
    // On a corpus with data the page is still filling in (lists, facets) after the map is up, and the
    // layout under the handle moves between measuring it and touching it: the touch then lands on
    // nothing. Settle first, so the drag is aimed at where the handle really is.
    await page.waitForLoadState('networkidle');
    await page.locator('.mb-filters').click();
    const handle = page.locator('.fbar .year-range-handle').last();
    await handle.scrollIntoViewIfNeeded();
    const box = await handle.boundingBox();
    assert.ok(box);
    const before = await page.locator('.fbar .yr-label').last().textContent();
    const x = box.x + box.width / 2, y = box.y + box.height / 2;
    if (isChromium) {
      // Touch events exercise pointer capture, touch-action and bubbling, not just synthetic swipes.
      const cdp = await page.context().newCDPSession(page);
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
      for (let step = 1; step <= 8; step++) {
        await cdp.send('Input.dispatchTouchEvent', {
          type: 'touchMove', touchPoints: [{ x: x - step * 8, y }],
        });
      }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await cdp.detach();
    } else {
      // no touch driver outside Chromium (CDP): the same drag with the real mouse — real pointer events
      // (pointer capture needs a pointer the browser knows), the drawer's swipe handling seeing them too
      await page.mouse.move(x, y);
      await page.mouse.down();
      for (let step = 1; step <= 8; step++) await page.mouse.move(x - step * 8, y);
      await page.mouse.up();
    }
    assert.notEqual(await page.locator('.fbar .yr-label').last().textContent(), before);
    assert.ok(await page.locator('.histmersion-app').evaluate(el => el.classList.contains('drawer-filters')));
  });
});

test('drawer swipes respect cancellation and pointer identity, and still close on a valid swipe', async () => {
  await withPage(phone, async page => {
    await page.locator('.mb-filters').click();
    const heading = page.locator('.fbar .drawer-title');
    const event = { bubbles: true, pointerId: 7, isPrimary: true, button: 0, clientX: 200, clientY: 80 };
    await heading.dispatchEvent('pointerdown', event);
    await heading.dispatchEvent('pointerup', { ...event, pointerId: 8, clientX: 100 });
    assert.ok(await page.locator('.histmersion-app').evaluate(el => el.classList.contains('drawer-filters')));
    await heading.dispatchEvent('pointercancel', event);
    await heading.dispatchEvent('pointerup', { ...event, clientX: 100 });
    assert.ok(await page.locator('.histmersion-app').evaluate(el => el.classList.contains('drawer-filters')));
    await heading.dispatchEvent('pointerdown', event);
    await heading.dispatchEvent('pointerup', { ...event, clientX: 100 });
    assert.equal(await page.locator('.histmersion-app').evaluate(el => el.classList.contains('drawer-filters')), false);
  });
});

test('phone and desktop search preserve multiword input and share the draft', async () => {
  await withPage(phone, async page => {
    await page.locator('.mb-search').pressSequentially('Bronze Age');
    assert.equal(await page.locator('.mb-search').inputValue(), 'Bronze Age');
    assert.equal(await page.locator('#f-q').inputValue(), 'Bronze Age');
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.waitForFunction(() => document.documentElement.dataset.layout === 'wide');
    await page.locator('#f-q').click();
    await page.locator('#f-q').press('End');
    await page.locator('#f-q').pressSequentially(' collapse');
    assert.equal(await page.locator('#f-q').inputValue(), 'Bronze Age collapse');
    assert.equal(await page.locator('.mb-search').inputValue(), 'Bronze Age collapse');
  });
});

test('#4037: About › Map regions — a named region checked zoom by zoom, downloaded and followed; a region removed', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    // the fetch, the job and the removal are answered here: the test server's extract is never changed
    const fetches = [], removals = [];
    const polls = {};
    const job = (id, st, progress, about) => ({ id, kind: 'basemap-fetch', status: st, citekey: null, progress, error: null, sha256: null,
      createdAt: '2026-10-24T10:00:00Z', updatedAt: '2026-10-24T10:00:01Z', acquireItem: null,
      detail: { init: null, skippedPeriods: [], skippedPeriodCount: 0, planHash: null, vacuum: null, media: null, events: null,
        eventImport: null, eventRederive: null, about } });
    const sized = "focus 'argolid': terrain (planet.pmtiles, zoom 9–12): 120 tiles, would download 10.0 MB in 3 requests (+2 for the directories); " +
      'by zoom — zoom 9: 4 tiles, 1.0 MB · zoom 10: 12 tiles, 2.0 MB · zoom 11: 30 tiles, 3.0 MB · zoom 12: 74 tiles, 4.0 MB';
    await page.route('**/api/basemap/fetch**', route => {
      const u = new URL(route.request().url());
      assert.equal(route.request().method(), 'POST');
      fetches.push(Object.fromEntries(u.searchParams));
      const dry = u.searchParams.get('dryRun') === 'true';
      const id = dry ? 'zz-dry' : 'zz-download';
      return route.fulfill({ json: job(id, 'queued', null, `${dry ? 'dry run: ' : ''}terrain base map — the relief of focus region 'argolid'`) });
    });
    await page.route('**/api/jobs/zz-*', route => {
      const id = new URL(route.request().url()).pathname.split('/').pop();
      const n = polls[id] = (polls[id] ?? 0) + 1;
      const about = `${id === 'zz-dry' ? 'dry run: ' : ''}terrain base map — the relief of focus region 'argolid'`;
      if (n === 1) return route.fulfill({ json: job(id, 'running', 'reading planet.pmtiles', about) });
      return route.fulfill({ json: job(id, 'done', id === 'zz-dry' ? sized : "focus 'argolid': stored 120 tiles", about) });
    });
    await page.route(u => u.pathname === '/api/basemap' && u.search.length > 0, route => {
      if (route.request().method() !== 'DELETE') return route.continue();
      removals.push(Object.fromEntries(new URL(route.request().url()).searchParams));
      return route.fulfill({ json: { removed: ['/zz/terrain-focus/argolid.pmtiles'], bytes: 1 } });
    });

    await page.locator('.tool-btns button[title="about"]:visible').click();
    const panel = page.locator('.about-pane .map-regions');
    await panel.waitFor();
    assert.ok((await panel.innerText()).includes('the map'), 'what is downloaded is listed');
    // a named region fills in its box; the download waits for the size to be checked
    await page.locator('.map-region-pick').selectOption('argolid');
    assert.equal(await page.locator('.map-region-bbox').inputValue(), '22.5,37.4,23.2,37.9');
    await page.locator('.map-region-content').selectOption('Relief');
    assert.ok(await page.locator('.map-region-download').isDisabled(), 'not before its size was seen');
    await page.locator('.map-region-check').click();
    // followed: running, then the sizes zoom by zoom
    await page.locator('.map-region-zooms li').first().waitFor({ timeout: 10000 });
    assert.deepEqual(await page.locator('.map-region-zooms li').allInnerTexts(),
      ['zoom 9: 4 tiles, 1.0 MB', 'zoom 10: 12 tiles, 2.0 MB', 'zoom 11: 30 tiles, 3.0 MB', 'zoom 12: 74 tiles, 4.0 MB']);
    assert.match(await page.locator('.map-region-job').innerText(), /size check: done/);
    assert.deepEqual(fetches[0], { layer: 'terrain', bbox: '22.5,37.4,23.2,37.9', dryRun: 'true', keepAll: 'false', focus: 'argolid' });
    // the same fetch for real, followed to its end
    await page.locator('.map-region-download:not([disabled])').click();
    await page.waitForFunction(() => /download: done/.test(document.querySelector('.map-region-job')?.textContent ?? ''), null, { timeout: 10000 });
    assert.equal(fetches.length, 2);
    assert.equal(fetches[1].dryRun, 'false');
    assert.equal(fetches[1].focus, 'argolid');
    assert.ok(polls['zz-download'] >= 2, 'the download was followed while it ran');
    // another box is checked again before it is downloaded
    await page.locator('.map-region-bbox').fill('22,37,23,38');
    assert.ok(await page.locator('.map-region-download').isDisabled());
    assert.equal(await page.locator('.map-region-pick').inputValue(), 'own');
    // a detailed-relief region of the fixture is removed with its button
    const focus = status.layers.find(l => l.layer === 'terrain')?.focus ?? [];
    if (focus.length > 0) {
      await page.locator(`.map-region-remove[data-region="${focus[0].name}"]`).click();
      for (let i = 0; i < 50 && removals.length === 0; i++) await page.waitForTimeout(100);
      assert.deepEqual(removals, [{ layer: 'none', focus: focus[0].name, cache: 'false' }]);
    }
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  });
});

test('#718: the About button opens the server report — build, health states, job queue, store, plugins', async () => {
  for (const viewport of [{ width: 1440, height: 900 }, phone]) {
    await withPage(viewport, async page => {
      if (viewport.width < 760) await page.locator('.mb-maptools').click();
      await page.locator('.tool-btns button[title="about"]:visible').click();
      const pane = page.locator('.about-pane .about-sections');
      await pane.waitFor();
      const text = await pane.innerText();
      for (const label of ['Server', 'commit', 'schema version', 'Health', 'converter', 'Job queue', 'queued',
        'Backups', 'last backup', 'Store', 'gazetteer places', 'period presets',
        // #2018: the plugins in the binary, the built-in media collections and event catalogues among them
        'Plugins', 'media-met', 'media-collection:met', 'events-gvp', 'event-catalogue:gvp'])
        assert.ok(text.includes(label), `About shows ${label}`);
      // every health check carries a state the stylesheet knows, whatever text the server sent
      const states = await page.locator('.about-state').evaluateAll(els => els.map(e => e.className));
      assert.ok(states.length >= 3 && states.every(c => /state-(ok|busy|down|unknown)/.test(c)), String(states));
      assert.ok(new URL(page.url()).hash.includes('open=about'), 'the window is in the URL');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    });
  }
});

// #1521: when the filter bar runs out of room, the toolbar keeps the top line(s) and the filters wrap
// underneath as one block — the search, place, more/fewer, clear-all row and the expanded filters below
// it are never interleaved with the toolbar's buttons, at any width down to the phone layout.
test('#1521: the toolbar stays on top and the filters wrap underneath it as one block', async () => {
  // the desktop layout's widths (a tablet's, up to 1366 px, has the toolbar in a drawer: #4431)
  for (const width of [1440, 1400]) {
    await withPage({ width, height: 900 }, async page => {
      assert.equal(await page.evaluate(() => document.documentElement.dataset.layout), 'wide', `wide at ${width}`);
      // open the expanded filters, so they are measured too
      if (await page.locator('#f-more').isHidden()) await page.locator('#f-more-toggle').click();
      const boxes = await page.evaluate(() => {
        const rects = sel => [...document.querySelectorAll(sel)].filter(e => e.offsetParent)
          .map(e => { const r = e.getBoundingClientRect(); return { top: r.top, bottom: r.bottom }; });
        return {
          tools: rects('.fbar .tool-btns .btn'),
          main: rects('.fbar .frow-main .frow-filters > *'),
          more: rects('.fbar #f-more > *'),
        };
      });
      assert.ok(boxes.tools.length >= 8, `toolbar buttons at ${width}: ${boxes.tools.length}`);
      assert.equal(boxes.main.length, 4, `search, place, more/fewer, clear all at ${width}`);
      assert.ok(boxes.more.length >= 5, `expanded filters at ${width}`);
      const filters = [...boxes.main, ...boxes.more];
      const firstFilter = Math.min(...filters.map(b => b.top));
      // every toolbar button's top is above every filter control's top…
      assert.ok(Math.max(...boxes.tools.map(b => b.top)) < firstFilter, `a button below a filter at ${width}`);
      // …and no button reaches into the filters' block: the block is contiguous
      const toolsBottom = Math.max(...boxes.tools.map(b => b.bottom));
      assert.ok(toolsBottom <= firstFilter + 1, `buttons end at ${toolsBottom}, filters start at ${firstFilter} (${width})`);
      // the expanded filters stay under the main filter row
      assert.ok(Math.min(...boxes.more.map(b => b.top)) >= Math.max(...boxes.main.map(b => b.bottom)) - 1,
        `expanded filters above the main row at ${width}`);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    });
  }
});

// #1440: the events layer in playback, from a fixed feed (the test database holds no events): a local
// event is a dot and label that fades after its year, a regional one a label with its area's outline
// for its span, and stopping clears them all.
test('#1440: playback draws local and regional events, fades the local one, and clears on stop', async () => {
  const square = { type: 'Polygon', coordinates: [[[30, 36], [34, 36], [34, 39], [30, 39], [30, 36]]] };
  const feed = {
    unplaced: 0,
    events: [
      { id: 'zz_local', label: 'Zz battle', kind: 'battle', scale: 'local', historicity: 'attested',
        timeSpan: { notBefore: -1296, notAfter: -1296 }, lat: 37.5, lon: 32, anchor: 'event' },
      { id: 'zz_regional', label: 'Zz era', kind: 'era', scale: 'regional', historicity: 'attested',
        timeSpan: { notBefore: -1295, notAfter: -1205 }, lat: 37.5, lon: 32, anchor: 'place',
        placeId: 'zz_area', placeLabel: 'Zz area', outline: JSON.stringify(square) },
    ],
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    let asked = 0;
    await page.route('**/api/events/geo**', route => { asked++; return route.fulfill({ json: feed }); });
    // a century to play: the span comes from the hash the page is LOADED with
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.locator('.tl-playback button').first().click();
    await page.waitForSelector('.hd-event--local .hd-event-dot');
    await page.waitForSelector('.hd-event--regional');
    await page.waitForFunction(() => window.__testMap.shapes('event-outlines').length === 1);
    assert.equal(asked, 1, 'the feed is fetched once per playback, not per frame');
    assert.match(await page.locator('.hd-event--local .hd-area-point-name').textContent(), /Zz battle/);
    // after its year the local event fades (its --a drops below 1), then goes
    await page.waitForFunction(() => {
      const el = document.querySelector('.hd-event--local');
      return el && parseFloat(el.style.getPropertyValue('--a')) < 1;
    });
    await page.waitForFunction(() => !document.querySelector('.hd-event--local'), null, { timeout: 15000 });
    assert.ok(await page.locator('.hd-event--regional').count() === 1, 'the era is still on');
    await page.locator('.tl-playback button[aria-label="stop and reset"]').click();
    await page.waitForFunction(() => !document.querySelector('.hd-event') && window.__testMap.shapes('event-outlines').length === 0);
  });
});

// #1447: area names, from a fixed boundaries feed — an archipelago of small islands is named (its size
// is the hull around them), every shown name is whole on screen and clear of the others, a name whose
// centre is panned off screen glides into the visible part instead of jumping, and the label frames
// stay cheap.
test('#1447: area names stay whole on screen and apart, an archipelago is named, a pan never makes one jump', async () => {
  const square = (lon, lat, d) => [[[lon, lat], [lon + d, lat], [lon + d, lat + d], [lon, lat + d], [lon, lat]]];
  const islands = [];
  for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) islands.push(square(24 + j, 36 + i, 0.15));
  const feature = (id, label, geometry) => ({ type: 'Feature', geometry,
    properties: { id, label, kind: 'polity', periodStart: -1400, periodEnd: -1200, hasBoundary: true, dating: 'stated', basis: 'dated' } });
  const feed = { type: 'FeatureCollection', features: [
    feature('zz_isles', 'Zz isles', { type: 'MultiPolygon', coordinates: islands }),
    feature('zz_realm', 'Zz realm', { type: 'Polygon', coordinates: square(30, 36, 8) }),
    feature('zz_west', 'Zz west', { type: 'Polygon', coordinates: square(12, 38, 6) }),
  ] };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', route => new URL(route.request().url()).searchParams.get('kind') === 'polity'
      ? route.fulfill({ json: feed }) : route.continue());
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries"]'));
    await page.goto(new URL('/#from=-1300&to=-1300', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => {
      window.__labelFrames = [];
      new PerformanceObserver(list => { for (const e of list.getEntries()) if (e.name === 'hd-label-frame') window.__labelFrames.push(e.duration); })
        .observe({ entryTypes: ['measure'] });
      window.__testMap.setView([38, 28], 5, { animate: false });
    });
    const shown = () => page.evaluate(() => {
      const m = document.getElementById('map').getBoundingClientRect();
      return [...document.querySelectorAll('.hd-map-label')].filter(el => {
        const cs = getComputedStyle(el);
        return !el.classList.contains('is-culled') && cs.visibility !== 'hidden' && parseFloat(cs.opacity) > 0.95;
      }).map(el => { const b = el.getBoundingClientRect(); return { t: el.textContent, l: b.left - m.left, r: b.right - m.left,
        top: b.top - m.top, b: b.bottom - m.top, w: m.width, h: m.height }; });
    });
    const settle = async names => page.waitForFunction(names => {
      const els = [...document.querySelectorAll('.hd-map-label')];
      return names.every(n => els.some(el => el.textContent === n && parseFloat(getComputedStyle(el).opacity) > 0.95));
    }, names, { timeout: 15000 });
    await settle(['Zz isles', 'Zz realm']);
    const check = labels => {
      for (const a of labels) assert.ok(a.l >= 0 && a.top >= 0 && a.r <= a.w && a.b <= a.h, `${a.t} is whole on screen`);
      for (const a of labels) for (const b of labels) if (a !== b)
        assert.ok(!(a.l < b.r && b.l < a.r && a.top < b.b && b.top < a.b), `${a.t} and ${b.t} overlap`);
    };
    check(await shown());
    // pan the realm's centre off the left edge: its name glides into what is left of it, frame by frame
    const map = await page.locator('#map').boundingBox();
    const track = () => page.evaluate(() => {
      window.__track = [];
      const tick = () => {
        const el = [...document.querySelectorAll('.hd-map-label')].find(e => e.textContent === 'Zz realm');
        const ref = window.__testMap.latLngToContainerPoint([40, 34]);
        if (el) { const b = el.getBoundingClientRect(); window.__track.push([b.left + b.width / 2 - ref.x, b.top + b.height / 2 - ref.y]); }
        if (!window.__trackStop) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    await track();
    await page.mouse.move(map.x + map.width / 2, map.y + map.height / 2);
    await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(map.x + map.width / 2 - i * 12, map.y + map.height / 2); await page.waitForTimeout(16); }
    await page.mouse.up();
    await page.waitForTimeout(1500);
    const path = await page.evaluate(() => { window.__trackStop = true; return window.__track; });
    // its own movement (less the map's) changes by at most a pan step from one frame to the next
    let worst = 0;
    for (let i = 2; i < path.length; i++) {
      const v1 = [path[i - 1][0] - path[i - 2][0], path[i - 1][1] - path[i - 2][1]];
      const v2 = [path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]];
      worst = Math.max(worst, Math.hypot(v2[0] - v1[0], v2[1] - v1[1]));
    }
    assert.ok(worst < 30, `a name jumped ${worst.toFixed(1)} px in one frame`);
    const after = await shown();
    check(after);
    assert.ok(after.some(a => a.t === 'Zz realm'), 'the realm is still named from its visible part');
    const frames = await page.evaluate(() => window.__labelFrames);
    assert.ok(frames.length > 10, 'the label frames ran during the pan');
    const sorted = [...frames].sort((a, b) => a - b);
    assert.ok(sorted[Math.floor(sorted.length * 0.95)] < 4, `label frames p95 ${sorted[Math.floor(sorted.length * 0.95)]} ms`);
  });
});

// A dot inside a region takes the click (the map's panes, core MapStack): a site dot and a finding
// marker inside a polity answer as themselves, the polity answers everywhere else — also after the
// polity was drawn LAST (its answer delayed past the sites') and after the boundaries layer was switched
// off and on again, which re-adds every shape after the dots. Fixed feeds; nothing is written.
test('a dot inside a region opens the dot, not the region, whatever was drawn last', async () => {
  const square = [[[30, 36], [34, 36], [34, 39], [30, 39], [30, 36]]];
  const props = (id, label, kind) => ({ id, label, kind, periodStart: -1400, periodEnd: -1100, dating: 'stated', basis: 'dated' });
  const polities = { type: 'FeatureCollection', features: [
    { type: 'Feature', geometry: { type: 'Polygon', coordinates: square }, properties: { ...props('zz_realm', 'Zz realm', 'polity'), hasBoundary: true } }] };
  const sites = { type: 'FeatureCollection', features: [
    { type: 'Feature', geometry: { type: 'Point', coordinates: [32, 37.5] }, properties: props('zz_site', 'Zz site', 'site') }] };
  const findings = [{ citekey: 'zz_dot2026', title: 'Zz finding', claim: 'a claim inside the realm', regionName: 'Zz realm',
    lon: 31, lat: 37, periodStart: -1300, periodEnd: -1200, confidence: 'high' }];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', async route => {
      const kind = new URL(route.request().url()).searchParams.get('kind');
      if (kind === 'polity') { await new Promise(r => setTimeout(r, 600)); return route.fulfill({ json: polities }); }
      if (kind === 'site') return route.fulfill({ json: sites });
      return route.continue();
    });
    await routeTiles(page, { sites: sites.features });
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries","sites"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForFunction(() => window.__testMap.shapes('polities').includes('zz_realm'));
    await page.waitForSelector('.hd-site-marker');
    await page.waitForSelector('.hd-find');
    const map = await page.locator('#map').boundingBox();
    const at = async (lat, lon) => {
      const p = await page.evaluate(([la, lo]) => window.__testMap.latLngToContainerPoint([la, lo]), [lat, lon]);
      return { x: map.x + p.x, y: map.y + p.y };
    };
    // what the pointer meets there: a marker (its class), else the map's canvas and the shapes under it
    const hit = async (lat, lon) => {
      const { x, y } = await at(lat, lon);
      return page.evaluate(([x, y, la, lo]) => {
        const el = document.elementFromPoint(x, y);
        if (!el) return 'nothing';
        if (el.closest('.hd-point-wrap--diamond')) return 'finding';
        if (el.closest('.hd-marker')) return el.getAttribute('class') || el.tagName;
        return el.tagName === 'CANVAS' ? `canvas ${window.__testMap.shapesAt([la, lo]).join(' ')}` : el.getAttribute('class') || el.tagName;
      }, [x, y, lat, lon]);
    };
    const placeInHash = () => new URLSearchParams(new URL(page.url()).hash.slice(1)).getAll('placeId');
    const check = async () => {
      // the pointer's target at each spot: the site's dot, the finding's marker, the region elsewhere
      assert.match(await hit(37.5, 32), /hd-site-marker/, 'the site dot is on top');
      assert.equal(await hit(37, 31), 'finding', 'the finding marker is on top');
      assert.match(await hit(38.5, 33.5), /^canvas polities:zz_realm/, 'the region answers where no dot is');
    };
    await check();
    // a click on the site chooses the SITE as the place filter, not the realm around it
    let p = await at(37.5, 32);
    await page.mouse.click(p.x, p.y);
    await page.waitForFunction(() => location.hash.includes('placeId=zz_site'));
    assert.deepEqual(placeInHash(), ['zz_site']);
    await page.mouse.click(p.x, p.y);   // and a second click lets it go
    await page.waitForFunction(() => !location.hash.includes('placeId='));
    // a click on the finding opens ITS popup
    p = await at(37, 31);
    await page.mouse.click(p.x, p.y);
    await page.waitForSelector('.hd-popup-content');
    assert.match(await page.locator('.hd-popup-content').innerText(), /a claim inside the realm/);
    assert.deepEqual(placeInHash(), [], 'the realm was not chosen');
    await page.evaluate(() => window.__testMap.closePopup());
    await page.waitForFunction(() => !document.querySelector('.hd-popup'));   // it fades out first
    // the boundaries switched off and on: every shape is added again, after the dots
    await page.locator('.viewport-toggle button[title="boundaries"]').click();
    await page.waitForFunction(() => window.__testMap.shapes('polities').length === 0);
    await page.locator('.viewport-toggle button[title="boundaries"]').click();
    await page.waitForFunction(() => window.__testMap.shapes('polities').includes('zz_realm'));
    await check();
    // the region is still a target of its own
    p = await at(38.5, 33.5);
    await page.mouse.click(p.x, p.y);
    await page.waitForFunction(() => location.hash.includes('placeId=zz_realm'));
  });
});

// The toolbar's notices: a window with something new since it was last opened is bold, with the count
// of what waits; opening it clears the bold at once and for good (the mark is the server's, so a reload
// agrees). It WRITES one backlog item to the test server (and removes it), so it refuses the live port.
test('a toolbar button is bold with news until its window is opened, and stays read after a reload', async () => {
  assert.notEqual(new URL(baseURL).port, '8000', 'never against the live server: this test writes');
  const api = (path, init = {}) => fetch(new URL(path, baseURL), { ...init, headers: { 'content-type': 'application/json' } });
  await api('/api/ui/seen/tasks', { method: 'POST', body: '{}' });
  const added = await (await api('/api/queue/tasks', { method: 'POST', body: JSON.stringify({ text: 'zz playwright notice' }) })).json();
  try {
    const open = (await (await api('/api/queue/tasks')).json()).items.length;
    await withPage({ width: 1440, height: 900 }, async page => {
      const button = page.locator('.fbar .tool-btns button[title="backlog"]');
      await page.waitForFunction(() => document.querySelector('.fbar .tool-btns button[title="backlog"]')?.classList.contains('has-news'));
      assert.equal(await button.evaluate(el => getComputedStyle(el).fontWeight), '700', 'bold');
      assert.equal(await button.locator('.btn-count').innerText(), String(Math.min(open, 99)) + (open > 99 ? '+' : ''));
      // the other buttons are not bold for it
      assert.equal(await page.locator('.fbar .tool-btns button[title="jobs"]').evaluate(el => el.classList.contains('has-news')), false);
      // opening the window reads it: not bold in the same frame the window opens
      const ms = await button.evaluate(el => new Promise(resolve => {
        const t0 = performance.now();
        el.click();
        const tick = () => el.classList.contains('has-news') ? requestAnimationFrame(tick) : resolve(performance.now() - t0);
        tick();
      }));
      assert.ok(ms <= 100, `the bold went after ${ms.toFixed(1)} ms`);
      await page.waitForSelector('.tasks-pane');
      assert.equal(await button.locator('.btn-count').innerText(), String(Math.min(open, 99)) + (open > 99 ? '+' : ''), 'the count stays');
      // closed again: still read; and after a reload, read too — the server holds the mark
      await button.click();
      await page.waitForFunction(() => !document.querySelector('.tasks-pane'));
      assert.equal(await button.evaluate(el => el.classList.contains('has-news')), false);
      await page.reload();
      await page.waitForFunction(() => window.__testMap);
      await page.waitForSelector('.fbar .tool-btns button[title="backlog"] .btn-count');
      assert.equal(await button.evaluate(el => el.classList.contains('has-news')), false, 'still read after a reload');
    });
  } finally {
    await api(`/api/queue/items/${added.id}`, { method: 'DELETE' });
  }
});

// #1446: hovering a site on the map floats its card with its picture — the local thumbnail as an <img>
// and, under it, the credit line and the licence — from fixed feeds (the test database holds no pictures).
test('#1446: hovering a site with a picture shows the image and its credit', async () => {
  const id = 'zz_mycenae', uid = '0190a0b0-0000-7000-8000-0000000000aa';
  const site = { type: 'FeatureCollection', features: [{ type: 'Feature',
    geometry: { type: 'Point', coordinates: [22.75, 37.73] },
    properties: { id, label: 'Zz Mycenae', kind: 'site', periodStart: -1600, periodEnd: -1100, basis: 'dated' } }] };
  const detail = {
    place: { id, label: 'Zz Mycenae', kind: 'site', periodStart: -1600, periodEnd: -1100, source: null, names: 'Zz Mycenae' },
    geometry: '{"type":"Point","coordinates":[22.75,37.73]}', parents: [], children: [], findings: 3, asOf: null,
    media: [{ uid, role: 'image', url: `/media/${uid}/thumb`, credit: 'Zz Photographer, CC BY-SA 2.5, via Wikimedia Commons',
      license: 'CC-BY-SA-2.5', licenseUrl: 'https://creativecommons.org/licenses/by-sa/2.5', width: 330, height: 220 }],
  };
  // a 330×220 PNG, drawn by the browser itself
  await withPage({ width: 1440, height: 900 }, async page => {
    const png = Buffer.from(await page.evaluate(() => {
      const c = document.createElement('canvas'); c.width = 330; c.height = 220;
      const g = c.getContext('2d'); g.fillStyle = '#a63'; g.fillRect(0, 0, 330, 220);
      return c.toDataURL('image/png').split(',')[1];
    }), 'base64');
    let thumbs = 0;
    await page.route('**/api/places/geo**', route => new URL(route.request().url()).searchParams.get('kind') === 'site'
      ? route.fulfill({ json: site }) : route.continue());
    await routeTiles(page, { sites: site.features });
    await page.route(`**/api/places/${id}?**`, route => route.fulfill({ json: detail }));
    await page.route(`**/media/${uid}/thumb`, route => { thumbs++; return route.fulfill({ body: png, contentType: 'image/png' }); });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites"]'));
    await page.goto(new URL('/#from=-1400&to=-1300', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.73, 22.75], 8, { animate: false }));
    const marker = page.locator('.hd-site-marker').first();
    await marker.waitFor();
    await settledAt(page, '.hd-site-marker', 37.73, 22.75);
    const box = await marker.boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    const img = page.locator('.cite-tooltip .cite-tip-media img.cite-tip-thumb');
    await img.waitFor();
    assert.equal(await img.getAttribute('src'), `/media/${uid}/thumb`, 'the local thumbnail, never the provider');
    await page.waitForFunction(() => {
      const i = document.querySelector('.cite-tooltip img.cite-tip-thumb');
      return i && i.complete && i.naturalWidth === 330;
    });
    assert.ok(thumbs >= 1, 'the thumbnail was fetched from this server');
    const credit = await page.locator('.cite-tooltip .cite-tip-credit').innerText();
    assert.match(credit, /Zz Photographer, CC BY-SA 2\.5, via Wikimedia Commons/);
    assert.match(credit, /CC-BY-SA-2\.5/);
    assert.equal(await page.locator('.cite-tooltip .cite-tip-credit a').getAttribute('href'),
      'https://creativecommons.org/licenses/by-sa/2.5');
    if (process.env.HOVER_SHOT) await page.screenshot({ path: process.env.HOVER_SHOT });
  });
});

// #1449: the events layer draws each event as its kind's icon in its date colour — filled when a
// source attests it, outlined when only a catalogue knows it; its card lists the attesting sources and
// the leads; the kind menu narrows the feed. Fixed feeds; nothing is written.
test('#1449: event markers show their kind icon, the tier, a card with sources and leads, and the kind filter', async () => {
  const feed = { unplaced: 0, events: [
    { id: 'zz_battle', label: 'Zz battle', kind: 'battle', scale: 'local', historicity: 'attested', tier: 'attested',
      timeSpan: { notBefore: -1274, notAfter: -1274 }, lat: 37.5, lon: 32, anchor: 'event' },
    { id: 'zz_eruption', label: 'Eruption of Zz', kind: 'eruption', scale: 'local', historicity: 'attested', tier: 'catalogue',
      size: 'VEI 6', timeSpan: { notBefore: -1630, notAfter: -1600 }, lat: 37.0, lon: 33, anchor: 'event' },
  ] };
  const detail = {
    event: { id: 'zz_battle', label: 'Zz battle', kind: 'battle', timeSpan: { notBefore: -1274, notAfter: -1274 },
      historicity: 'attested', citekey: 'zz_src2020', scale: 'local', scaleStated: false, tier: 'attested' },
    children: [], datings: [], edges: [], participants: [], media: [], tier: 'attested', names: [], catalogue: [],
    sources: [{ citekey: 'zz_src2020', title: 'Zz chronicle', year: 2020, via: 'citekey' }],
    findings: [], articles: [{ slug: 'zz_article', title: 'Zz article', citekey: 'zz_src2020' }], candidates: [], leads: [],
  };
  const eruption = {
    event: { id: 'zz_eruption', label: 'Eruption of Zz', kind: 'eruption', timeSpan: { notBefore: -1630, notAfter: -1600 },
      historicity: 'attested', scale: 'local', scaleStated: false, tier: 'catalogue' },
    children: [], datings: [], edges: [], participants: [], media: [], tier: 'catalogue', names: [], sources: [],
    findings: [], articles: [], candidates: [],
    catalogue: [{ provider: 'gvp', providerId: '1', url: 'https://volcano.si.edu/volcano.cfm?vn=1', title: 'Smithsonian Global Volcanism Program',
      licence: 'Smithsonian Terms of Use', citation: 'Global Volcanism Program' }],
    leads: [{ eventId: 'zz_eruption', label: 'Eruption of Zz', kind: 'eruption', tier: 'catalogue', years: '1630 BCE – 1600 BCE',
      text: 'No source in the corpus discusses this VEI 6 eruption (1630 BCE – 1600 BCE) — 3 findings in the corpus within 300 km and ±100 years',
      actions: [{ kind: 'discover', label: 'search the literature', command: 'histmersion discover search "Eruption of Zz" --not-in-db', query: 'Eruption of Zz' }],
      findingsNear: 3, candidates: 0 }],
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    const asked = [];
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await routeTiles(page, { events: feed.events, asked });
    await page.route('**/api/events/zz_battle', route => route.fulfill({ json: detail }));
    await page.route('**/api/events/zz_eruption', route => route.fulfill({ json: eruption }));
    await page.evaluate(() => { localStorage.setItem('histmersion.ui.layers', '["events"]'); });
    await page.goto(new URL('/#from=-1700&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.3, 32.5], 7, { animate: false }));
    const battle = page.locator('.hd-ev[data-id="zz_battle"]');
    await battle.waitFor();
    // the kind's icon is drawn inside the marker, before any hover
    assert.equal(await battle.locator('svg.glyph').count(), 1);
    assert.ok(await battle.evaluate(el => el.classList.contains('hd-ev--attested') && el.classList.contains('hd-ev--human')));
    const erup = page.locator('.hd-ev[data-id="zz_eruption"]');
    assert.ok(await erup.evaluate(el => el.classList.contains('hd-ev--catalogue') && el.classList.contains('hd-ev--natural')));
    // the tiers look different: filled vs outlined (dashed)
    const styles = await page.evaluate(() => ['zz_battle', 'zz_eruption'].map(id => {
      const s = getComputedStyle(document.querySelector(`.hd-ev[data-id="${id}"]`));
      return { border: s.borderStyle, bg: s.backgroundColor };
    }));
    assert.equal(styles[0].border, 'solid');
    assert.equal(styles[1].border, 'dashed');
    assert.notEqual(styles[0].bg, styles[1].bg);
    assert.ok(asked.some(u => u.pathname.startsWith('/api/tiles/events/') && u.searchParams.get('context') === 'true'),
      'the events are the tiles of the view');
    // the attested event's card lists its source (a source link) and the article citing it
    await hoverForCard(page, '.hd-ev[data-id="zz_battle"]', '.cite-tooltip .section-sources a[data-ck="zz_src2020"]');
    assert.match(await page.locator('.cite-tooltip').innerText(), /Zz article/);
    // the catalogue event's card names the catalogue and its lead, and is marked as such
    await page.mouse.move(5, 5);
    await hoverForCard(page, '.hd-ev[data-id="zz_eruption"]', '.cite-tooltip .section-leads');
    const card = await page.locator('.cite-tooltip').innerText();
    assert.match(card, /catalogue only/);
    assert.match(card, /No source in the corpus discusses this VEI 6 eruption/);
    assert.match(card, /Smithsonian Global Volcanism Program/);
    // the kind menu narrows the feed to natural events
    await page.mouse.move(5, 5);
    const before = asked.length;
    await page.locator('.icon-select-button[aria-label="event kind"]').click();
    await page.locator('.icon-select-list[aria-label="event kind"] [role="option"][data-value="natural"]').click();
    await page.waitForTimeout(400);
    assert.ok(asked.slice(before).some(u => u.searchParams.get('kind') === 'natural'), 'kind=natural was asked for');
    // the legend's key names the icons
    assert.ok(await page.locator('.hd-legend .hd-key').count() === 1);
  });
});

// P0 (event dating): an approximately dated event (± 50–500 years) is drawn faded with a dotted edge, an
// imprecisely dated one (± > 500) is never drawn at a year, and its card says "250 CE ± 2500
// (tephrochronology)". Fixed feeds (the imprecise one is put in the feed on purpose: the server leaves it
// out, the page must too); nothing is written.
test('P0: approximate events are faded, imprecise ones are not drawn, the card states the dating', async () => {
  const ev = (id, label, timeSpan, precision, extra = {}) => ({ id, label, kind: 'eruption', scale: 'local',
    historicity: 'attested', tier: 'catalogue', timeSpan, lat: 37.0, lon: 33 + Object.keys(extra).length * 1.0, anchor: 'event',
    precision, ...extra });
  const feed = { unplaced: 0, imprecise: 1, events: [
    ev('zz_precise', 'Zz precise', { notBefore: -1610, notAfter: -1610 }, 'precise'),
    ev('zz_approx', 'Zz approximate', { notBefore: -1800, notAfter: -1400 }, 'approximate', { a: 1 }),
    ev('zz_imprecise', 'Zz imprecise', { notBefore: -2251, notAfter: 2026 }, 'imprecise', { a: 1, b: 2 }),
  ] };
  const detail = {
    event: { id: 'zz_approx', label: 'Eruption of Hekla', kind: 'eruption', timeSpan: { notBefore: -2251, notAfter: 2026 },
      historicity: 'attested', scale: 'local', scaleStated: false, tier: 'catalogue', precision: 'imprecise',
      central: 250, plusMinus: 2500, datingMethod: 'Tephrochronology' },
    children: [], datings: [], edges: [], participants: [], media: [], tier: 'catalogue', names: [], sources: [],
    findings: [], articles: [], candidates: [], catalogue: [], leads: [], corrections: [],
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await routeTiles(page, { events: feed.events });
    await page.route('**/api/events/zz_approx', route => route.fulfill({ json: detail }));
    await page.evaluate(() => { localStorage.setItem('histmersion.ui.layers', '["events"]'); });
    await page.goto(new URL('/#from=-1700&to=-1500', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.0, 33.5], 7, { animate: false }));
    const precise = page.locator('.hd-ev[data-id="zz_precise"]');
    const approx = page.locator('.hd-ev[data-id="zz_approx"]');
    await precise.waitFor();
    await approx.waitFor();
    assert.equal(await page.locator('.hd-ev[data-id="zz_imprecise"]').count(), 0, 'an imprecise event is not drawn at a year');
    assert.ok(await approx.evaluate(el => el.classList.contains('hd-ev--approximate')));
    assert.ok(!(await precise.evaluate(el => el.classList.contains('hd-ev--approximate'))));
    const look = await page.evaluate(() => ['zz_precise', 'zz_approx'].map(id => {
      const s = getComputedStyle(document.querySelector(`.hd-ev[data-id="${id}"]`));
      return { opacity: Number(s.opacity), border: s.borderStyle };
    }));
    assert.ok(look[1].opacity < look[0].opacity, `faded: ${JSON.stringify(look)}`);
    assert.equal(look[1].border, 'dotted');
    await hoverForCard(page, '.hd-ev[data-id="zz_approx"]', '.cite-tooltip .cite-tip-title');
    const card = await page.locator('.cite-tooltip').innerText();
    assert.match(card, /250 CE ± 2500 \(tephrochronology\)/);
    assert.match(card, /imprecise date — listed, not drawn on the map at a year/);
  });
});

// #1449: a site is a circle badge filled with its period colour, its type's icon in white over a dark
// halo; a site of no known type is the same circle with an exclamation mark — never a bare dot.
test('#1449: a site is a period-coloured circle with its type icon (white on a halo); an untyped one shows "!"', async () => {
  const props = (id, label, extra) => ({ id, label, kind: 'site', periodStart: -1400, periodEnd: -1100, basis: 'dated', ...extra });
  const sites = { type: 'FeatureCollection', features: [
    { type: 'Feature', geometry: { type: 'Point', coordinates: [32, 37.5] }, properties: props('zz_fort', 'Zz fort', { siteType: 'fortification' }) },
    { type: 'Feature', geometry: { type: 'Point', coordinates: [33, 37.5] }, properties: props('zz_plain', 'Zz plain', {}) }] };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', route => new URL(route.request().url()).searchParams.get('kind') === 'site'
      ? route.fulfill({ json: sites }) : route.continue());
    await routeTiles(page, { sites: sites.features });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32.5], 7, { animate: false }));
    const fort = page.locator('.hd-site-icon[data-site-type="fortification"]');
    await fort.waitFor();
    await settledAt(page, '.hd-site-icon[data-site-type="fortification"]', 37.5, 32);
    await page.waitForTimeout(600);   // the fill's colour transition (a span change re-colours in place)
    assert.equal(await fort.locator('svg.glyph').count(), 1);
    // the badge is the period colour (--c), a circle with a thin dark edge; the icon white over a dark halo
    const look = await fort.evaluate(el => {
      const s = getComputedStyle(el);
      return { bg: s.backgroundColor, radius: s.borderRadius, border: s.borderTopWidth, c: el.style.getPropertyValue('--c').trim(),
        ink: getComputedStyle(el.querySelector('.glyph-ink')).stroke, halo: getComputedStyle(el.querySelector('.glyph-halo')).stroke,
        haloW: parseFloat(getComputedStyle(el.querySelector('.glyph-halo')).strokeWidth),
        inkW: parseFloat(getComputedStyle(el.querySelector('.glyph-ink')).strokeWidth) };
    });
    const probe = await page.evaluate(c => { const d = document.createElement('div'); d.style.color = c; document.body.append(d);
      const v = getComputedStyle(d).color; d.remove(); return v; }, look.c);
    assert.equal(look.bg, probe, 'filled with the period colour');
    assert.equal(look.radius, '50%', 'a circle');
    assert.ok(parseFloat(look.border) >= 1, 'a thin edge');
    assert.equal(look.ink, 'rgb(255, 255, 255)', 'white strokes');
    assert.match(look.halo, /rgba\(0, 0, 0, 0\.5/, 'over a dark halo');
    assert.ok(look.haloW > look.inkW, 'the halo is wider than the ink');
    // the untyped site: the same circle, with the exclamation mark
    const plain = page.locator('.hd-site-marker:not([data-site-type])');
    await plain.waitFor();
    assert.equal(await plain.evaluate(el => getComputedStyle(el).borderRadius), '50%');
    assert.equal(await plain.locator('svg.glyph--badge').count(), 1);
    // hovering the icon shows the place's card, as the dot does
    await page.route('**/api/places/zz_fort?**', route => route.fulfill({ json: {
      place: { id: 'zz_fort', label: 'Zz fort', kind: 'site', periodStart: -1400, periodEnd: -1100, source: null, names: 'Zz fort' },
      geometry: '{"type":"Point","coordinates":[32,37.5]}', parents: [], children: [], findings: 0, asOf: null, media: [] } }));
    await fort.hover();
    await page.waitForSelector('.cite-tooltip .cite-tip-title');
    assert.match(await page.locator('.cite-tooltip .cite-tip-title').innerText(), /Zz fort/);
  });
});

// A finding's dot answers the hover with its SOURCE's card — title, byline, identifier — never the
// browser's native tooltip with the internal citekey and period; a coincident dot keeps a readable
// "N findings here"; a click still opens the finding's popup. Fixed feeds; nothing is written.
test('hovering a finding dot shows its source card, not the citekey', async () => {
  const ck = 'zz_hover2026_genomic';
  const findings = [
    { citekey: ck, title: 'Zz genomic history of the steppe', claim: 'a claim at the dot', regionName: null,
      lon: 31, lat: 37, periodStart: -3000, periodEnd: -2000, confidence: 'high', doi: '10.9999/zz.hover' },
    { citekey: 'zz_a2026', title: 'Zz A', claim: 'one', regionName: null, lon: 33, lat: 38, periodStart: -1300, periodEnd: -1200, confidence: 'high' },
    { citekey: 'zz_b2026', title: 'Zz B', claim: 'two', regionName: null, lon: 33, lat: 38, periodStart: -1250, periodEnd: -1200, confidence: 'low' },
  ];
  const source = {
    source: { citekey: ck, title: 'Zz genomic history of the steppe', authors: ['Zz Author', 'Zz Other'], year: 2026,
      venue: 'Zz Journal', status: 'current', topics: ['zz-topic'], regions: [], tags: [], periodStart: -3000, periodEnd: -2000,
      summary: 'Zz summary of the source.', doi: '10.9999/zz.hover', ocrMarkdown: null },
    findings: [],
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    let asked = 0;
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.route(`**/api/sources/${ck}?**`, route => { asked++; return route.fulfill({ json: source }); });
    await page.goto(new URL('/#from=-3000&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForFunction(() => document.querySelectorAll('.hd-marker.hd-point-wrap--diamond').length >= 2);
    await page.waitForTimeout(800);   // the dots grow in (hd-entering) before they can be hit
    const markerAt = (lat, lon) => page.evaluate(([la, lo]) => {
      const p = window.__testMap.latLngToContainerPoint([la, lo]);
      const m = document.getElementById('map').getBoundingClientRect();
      const el = document.elementFromPoint(m.left + p.x, m.top + p.y)?.closest('.hd-marker');
      return el ? { x: m.left + p.x, y: m.top + p.y, title: el.getAttribute('title'), aria: el.getAttribute('aria-label') } : null;
    }, [lat, lon]);
    const single = await markerAt(37, 31);
    assert.ok(single, 'the single dot is under its point');
    assert.equal(single.title, null, 'no native tooltip on a single dot');
    assert.ok(!(single.aria || '').includes(ck), `its accessible name is not the citekey: ${single.aria}`);
    assert.match(single.aria || '', /Zz genomic history of the steppe/);
    // the coincident dot: a readable native tooltip, no citekey
    const both = await markerAt(38, 33);
    assert.ok(both);
    assert.equal(both.title, '2 findings here');
    // sweeping across the single dot fetches nothing…
    await page.mouse.move(single.x - 60, single.y - 60);
    await page.mouse.move(single.x, single.y, { steps: 3 });
    await page.mouse.move(single.x + 60, single.y + 60, { steps: 3 });
    await page.waitForTimeout(600);
    assert.equal(asked, 0, 'a sweep fetches nothing');
    assert.equal(await page.locator('.cite-tooltip').count(), 0);
    // …resting on it shows the source's card: its title, byline, identifier — not the citekey
    await page.mouse.move(single.x, single.y, { steps: 3 });
    const tip = page.locator('.cite-tooltip .cite-tip-title');
    await tip.waitFor();
    assert.equal(await tip.innerText(), 'Zz genomic history of the steppe');
    const card = await page.locator('.cite-tooltip').innerText();
    assert.match(card, /Zz Author/);
    assert.match(card, /10\.9999\/zz\.hover/, 'the identifier is the DOI');
    assert.ok(!card.includes(ck), 'the card does not show the citekey');
    assert.equal(asked, 1, 'resting fetches once');
    if (process.env.HOVER_SHOT) await page.screenshot({ path: process.env.HOVER_SHOT });
    // leaving hides it; coming back shows it again from the cache, at once
    await page.mouse.move(single.x + 120, single.y + 120, { steps: 3 });
    await page.waitForFunction(() => !document.querySelector('.cite-tooltip'));
    await page.mouse.move(single.x, single.y, { steps: 2 });
    await tip.waitFor();
    assert.equal(asked, 1, 'the card is cached');
    // a click still opens the finding's popup, and the hover card makes way for it
    await page.mouse.click(single.x, single.y);
    await page.waitForSelector('.hd-popup-content');
    assert.match(await page.locator('.hd-popup-content').innerText(), /a claim at the dot/);
    assert.equal(await page.locator('.cite-tooltip:not(.is-pinned)').count(), 0, 'the hover card is gone');
    // the coincident dot shows no source card on a rest
    await page.evaluate(() => window.__testMap.closePopup());
    await page.mouse.move(both.x, both.y, { steps: 3 });
    await page.waitForTimeout(800);
    assert.equal(await page.locator('.cite-tooltip').count(), 0, 'no single source card for several findings');
  });
});

// ── cards stay on screen; every card kind leads with its icon; the mixed clusters; the icon menus ────────

const evDetail = (id, label, kind, n, extra = {}) => ({
  event: { id, label, kind, timeSpan: { notBefore: -1274, notAfter: -1274 }, historicity: 'attested', scale: 'local',
    scaleStated: false, tier: 'attested' },
  children: [], datings: [], edges: [], participants: [], media: [], tier: 'attested', names: [], catalogue: [],
  sources: Array.from({ length: n }, (_, i) => ({ citekey: `zz_src${i}`, title: `Zz chronicle number ${i} with a long title`, via: 'citekey' })),
  findings: [], articles: [], candidates: [], leads: [], ...extra,
});
const evGeo = (id, label, kind, lat, lon, extra = {}) => ({ id, label, kind, scale: 'local', historicity: 'attested', tier: 'attested',
  timeSpan: { notBefore: -1274, notAfter: -1274 }, lat, lon, anchor: 'event', ...extra });

// Where a point is on the page, from its map coordinates.
async function pagePoint(page, lat, lon) {
  return page.evaluate(([la, lo]) => {
    const p = window.__testMap.latLngToContainerPoint([la, lo]);
    const m = document.getElementById('map').getBoundingClientRect();
    return { x: m.left + p.x, y: m.top + p.y };
  }, [lat, lon]);
}

// The user's "hover tooltips should be smartly displayed" (cae44855): a tall card hovered by the top edge
// never starts above the viewport; a pinned card taller than the viewport stays inside it and scrolls,
// its ✕ in reach; a card whose picture arrives after it was placed is placed again, inside the viewport.
test('cards stay inside the viewport: near an edge, taller than it when pinned, and when their content grows', async () => {
  const feed = { unplaced: 0, events: [evGeo('zz_top', 'Zz top', 'battle', 38.0, 32.0), evGeo('zz_low', 'Zz low', 'siege', 36.6, 33.4)] };
  await withPage({ width: 1440, height: 640 }, async page => {
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await routeTiles(page, { events: feed.events });
    await page.route('**/api/events/zz_top', route => route.fulfill({ json: evDetail('zz_top', 'Zz top', 'battle', 60) }));
    await page.route('**/api/events/zz_low', route => route.fulfill({ json: evDetail('zz_low', 'Zz low', 'siege', 2) }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    const mapTop = await page.evaluate(() => document.getElementById('map').getBoundingClientRect().top);
    // put the first event 14 px under the map's top edge
    await page.evaluate(([top]) => { const m = window.__testMap; m.setView([38.0, 32.0], 7, { animate: false });
      m.panBy([0, m.getSize().y / 2 - 14], { animate: false }); }, [mapTop]);
    await page.locator('.hd-ev[data-id="zz_top"]').waitFor();
    await settledAt(page, '.hd-ev[data-id="zz_top"]', 38.0, 32.0);
    const top = await pagePoint(page, 38.0, 32.0);
    await page.mouse.move(top.x, top.y, { steps: 2 });
    await page.waitForSelector('.cite-tooltip .section-sources');
    await page.waitForTimeout(150);
    const inView = () => page.evaluate(() => {
      const b = document.querySelector('.cite-tooltip').getBoundingClientRect();
      return { top: b.top, bottom: b.bottom, left: b.left, right: b.right, vh: innerHeight, vw: innerWidth };
    });
    let r = await inView();
    assert.ok(r.top >= 0 && r.bottom <= r.vh && r.left >= 0 && r.right <= r.vw, `hover card off screen: ${JSON.stringify(r)}`);
    // pinned, and taller than the viewport: inside it, scrolling, its ✕ visible at either end
    await page.mouse.click(top.x, top.y);
    await page.waitForSelector('.cite-tooltip.is-pinned .section-sources');
    await page.waitForTimeout(150);
    const pinned = await page.evaluate(() => {
      const box = document.querySelector('.cite-tooltip.is-pinned');
      const b = box.getBoundingClientRect();
      return { top: b.top, bottom: b.bottom, scrolls: box.scrollHeight > box.clientHeight + 1, vh: innerHeight };
    });
    assert.ok(pinned.top >= 0 && pinned.bottom <= pinned.vh, `pinned card off screen: ${JSON.stringify(pinned)}`);
    assert.ok(pinned.scrolls, 'the pinned card scrolls');
    for (const end of ['top', 'bottom']) {
      const closeIn = await page.evaluate(end => {
        const box = document.querySelector('.cite-tooltip.is-pinned');
        box.scrollTop = end === 'top' ? 0 : box.scrollHeight;
        const b = box.getBoundingClientRect(), c = box.querySelector('.cite-tip-close').getBoundingClientRect();
        return c.top >= b.top - 1 && c.bottom <= b.bottom + 1 && c.top >= 0;
      }, end);
      assert.ok(closeIn, `the ✕ is visible with the card scrolled to its ${end}`);
    }
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('.cite-tooltip.is-pinned'));
    // a card whose content grows after it was placed (a picture arriving, a late section), hovered near the
    // bottom: re-placed inside the viewport
    await page.evaluate(() => { const m = window.__testMap; m.setView([36.6, 33.4], 7, { animate: false });
      m.panBy([0, -(m.getSize().y / 2 - 30)], { animate: false }); });
    await page.locator('.hd-ev[data-id="zz_low"]').waitFor();
    await settledAt(page, '.hd-ev[data-id="zz_low"]', 36.6, 33.4);
    const low = await pagePoint(page, 36.6, 33.4);
    await page.mouse.move(5, 5);
    await page.mouse.move(low.x, low.y, { steps: 2 });
    await page.waitForSelector('.cite-tooltip .section-sources');
    await page.waitForTimeout(150);
    const before = await page.evaluate(() => document.querySelector('.cite-tooltip').getBoundingClientRect());
    await page.evaluate(() => { const d = document.createElement('div'); d.style.height = '240px'; d.className = 'zz-grown';
      document.querySelector('.cite-tooltip > div').append(d); });
    await page.waitForFunction(top => document.querySelector('.cite-tooltip').getBoundingClientRect().top !== top, before.top);
    r = await inView();
    assert.ok(r.top >= 0 && r.bottom <= r.vh, `the grown card off screen: ${JSON.stringify(r)}`);
  });
});

// Every card kind leads with ONE icon from the registry: a finding's source (its kind), a site (its type), a
// region, an event (its kind), a picture's popup — the same element and size on every one.
test('every card kind leads with its icon: source, site, region, event, picture', async () => {
  const square = [[[30, 36], [34, 36], [34, 39], [30, 39], [30, 36]]];
  const props = (id, label, kind, extra = {}) => ({ id, label, kind, periodStart: -1400, periodEnd: -1100, dating: 'stated', basis: 'dated', ...extra });
  const polities = { type: 'FeatureCollection', features: [
    { type: 'Feature', geometry: { type: 'Polygon', coordinates: square }, properties: { ...props('zz_realm', 'Zz realm', 'polity'), hasBoundary: true } }] };
  const sites = { type: 'FeatureCollection', features: [
    { type: 'Feature', geometry: { type: 'Point', coordinates: [32, 37.5] }, properties: props('zz_fort', 'Zz fort', 'site', { siteType: 'fortification' }) }] };
  const findings = [{ citekey: 'zz_book2020', title: 'Zz book', claim: 'a claim', regionName: null, lon: 31, lat: 37,
    periodStart: -1300, periodEnd: -1200, confidence: 'high', sourceKind: 'book' }];
  const source = { source: { citekey: 'zz_book2020', title: 'Zz book', authors: ['Zz'], year: 2020, venue: null, status: 'current',
    topics: [], regions: [], tags: [], periodStart: -1300, periodEnd: -1200, summary: null, doi: null, ocrMarkdown: null, provenance: 'calibre' },
    findings: [] };
  const place = (id, label, kind, extra = {}) => ({ place: { id, label, kind, periodStart: -1400, periodEnd: -1100, source: null, names: label },
    geometry: null, parents: [], children: [], findings: 0, asOf: null, media: [], ...extra });
  const feed = { unplaced: 0, events: [evGeo('zz_b', 'Zz battle', 'battle', 38.3, 33.2)] };
  const media = [{ citekey: null, assetName: 'zz.jpg', kind: 'photo', lat: 36.5, lon: 33.3, periodStart: -1250, periodEnd: -1250, caption: null, uid: 'zzu' }];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', route => {
      const kind = new URL(route.request().url()).searchParams.get('kind');
      return route.fulfill({ json: kind === 'polity' ? polities : kind === 'site' ? sites : { type: 'FeatureCollection', features: [] } });
    });
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.route('**/api/sources/zz_book2020?**', route => route.fulfill({ json: source }));
    await page.route('**/api/places/zz_fort?**', route => route.fulfill({ json: place('zz_fort', 'Zz fort', 'site', { siteType: 'fortification' }) }));
    await page.route('**/api/places/zz_realm?**', route => route.fulfill({ json: place('zz_realm', 'Zz realm', 'polity', { hasBoundary: true }) }));
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await page.route('**/api/events/zz_b', route => route.fulfill({ json: evDetail('zz_b', 'Zz battle', 'battle', 1) }));
    await page.route('**/api/media-geo**', route => route.fulfill({ json: media }));
    await routeTiles(page, { sites: sites.features, events: feed.events, media });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries","sites","events","media"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.4, 32.3], 7, { animate: false }));
    await page.waitForFunction(() => window.__testMap.shapes('polities').includes('zz_realm'));
    const iconOf = async (lat, lon, sel) => {
      await settledAt(page, sel, lat, lon);
      await page.mouse.move(5, 5);
      await page.waitForFunction(() => !document.querySelector('.cite-tooltip'));
      const p = await pagePoint(page, lat, lon);
      await page.mouse.move(p.x, p.y, { steps: 2 });
      await page.waitForSelector('.cite-tooltip .cite-tip-title .cite-tip-icon svg.glyph');
      return page.evaluate(() => {
        const t = document.querySelector('.cite-tooltip .cite-tip-title');
        const icons = t.querySelectorAll('.cite-tip-icon');
        const s = getComputedStyle(icons[0].querySelector('svg'));
        return { n: icons.length, html: icons[0].innerHTML, stroke: s.stroke, w: icons[0].getBoundingClientRect().width };
      });
    };
    const icons = {
      source: await iconOf(37, 31, '.hd-find'),
      site: await iconOf(37.5, 32, '.hd-site-marker'),
      event: await iconOf(38.3, 33.2, '.hd-ev[data-id="zz_b"]'),
    };
    // the region: hovered where no point is
    await page.mouse.move(5, 5);
    await page.waitForFunction(() => !document.querySelector('.cite-tooltip'));
    const inside = await pagePoint(page, 36.4, 30.5);
    await page.mouse.move(inside.x, inside.y, { steps: 2 });
    await page.waitForSelector('.cite-tooltip .cite-tip-title .cite-tip-icon');
    icons.region = await page.evaluate(() => {
      const i = document.querySelector('.cite-tooltip .cite-tip-title .cite-tip-icon');
      return { n: document.querySelectorAll('.cite-tooltip .cite-tip-title .cite-tip-icon').length, html: i.innerHTML,
        stroke: getComputedStyle(i.querySelector('svg')).stroke, w: i.getBoundingClientRect().width };
    });
    for (const [k, v] of Object.entries(icons)) assert.equal(v.n, 1, `${k}: one icon`);
    // each a different icon (book, castle, swords, map), drawn alike
    assert.equal(new Set(Object.values(icons).map(v => v.html)).size, 4, 'four kinds, four icons');
    assert.equal(new Set(Object.values(icons).map(v => v.stroke)).size, 1, 'one colour treatment');
    assert.equal(new Set(Object.values(icons).map(v => Math.round(v.w))).size, 1, 'one size');
    // the picture's popup leads with the picture icon
    await page.mouse.move(5, 5);
    await settledAt(page, '.hd-media-dot', 36.5, 33.3);
    const pic = await pagePoint(page, 36.5, 33.3);
    await page.mouse.click(pic.x, pic.y);
    await page.waitForSelector('.hd-popup-content .media-popup-title .cite-tip-icon svg.glyph');
  });
});

// #1647: a cluster of events keeps the event's square, apart from the findings' round bubbles; among
// thousands of points (the events layer) a zoom redraws the bubbles at once instead of animating them —
// markercluster's animation forced a layout and drew every icon twice (40–60 ms tasks) — and a few points
// animate again.
// #4291: whether a zoom animates is decided by the markers DRAWN (in view and its margin), not by the points
// the layer holds — a wide window's view holds a couple of thousand events and draws a few hundred markers,
// and it stopped animating while a phone's narrower view (fewer events fetched) still did.
test('a cluster of events is a square; a zoom animates by the markers drawn, not the points held', async () => {
  const near = (i) => ({ lat: 37.5 + (i % 3) * 0.02, lon: 32 + Math.floor(i / 3) * 0.02 });
  const few = [0, 1, 2].map(i => evGeo(`zz_c${i}`, `Zz event ${i}`, 'battle', near(i).lat, near(i).lon));
  // 1 200 points 0.5° apart: a few dozen bubbles at zoom 4–5
  const many = Array.from({ length: 1200 }, (_, i) =>
    evGeo(`zz_m${i}`, `Zz many ${i}`, 'earthquake', 20 + (i % 40) * 0.5, -10 + Math.floor(i / 40) * 0.5));
  // 2 556 points 1° apart: each drawn alone at zoom 6 (1° is ~45 px there, a bubble's reach 36 px)
  const grid = Array.from({ length: 71 * 36 }, (_, i) =>
    evGeo(`zz_g${i}`, `Zz grid ${i}`, 'earthquake', 20 + Math.floor(i / 71), -10 + (i % 71)));
  let feed = { unplaced: 0, events: few };
  await withPage({ width: 1920, height: 1080 }, async page => {
    await page.route('**/api/findings**', route => route.fulfill({ json: [] }));
    await page.route('**/api/places/geo**', route => route.fulfill({ json: { type: 'FeatureCollection', features: [] } }));
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    // the tiles of a view the map has not seen are cut from the feed of that moment
    await routeTiles(page, { events: () => feed.events });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '3');
    const shape = await page.evaluate(() => {
      const b = document.querySelector('.hd-marker .hd-cluster');
      return { events: b.classList.contains('hd-cluster--events'), says: b.getAttribute('aria-label'),
        rects: b.querySelectorAll('svg rect').length, circles: b.querySelectorAll('svg circle').length };
    });
    assert.deepEqual(shape, { events: true, says: '3 events', rects: 4, circles: 0 });
    // does the next zoom animate the bubbles? The cluster layer counts the zooms it animated
    const zoomAnimates = (z) => page.evaluate((zoom) => new Promise(resolve => {
      const map = window.__testMap, before = map.clusterAnimations();
      map.once('zoomend', () => setTimeout(() => resolve(map.clusterAnimations() > before), 600));
      map.setZoom(zoom);
    }), z);
    // the markers drawn, once they stop changing (a large drawing is made a slice at a time)
    const drawn = () => page.evaluate(() => new Promise(resolve => {
      let last = -1, same = 0;
      const tick = () => {
        const n = document.querySelectorAll('#map .hd-pane--events .hd-marker, #map .hd-pane--markers .hd-marker').length;
        if (n === last) same++; else { same = 0; last = n; }
        if (same >= 10) resolve(n); else requestAnimationFrame(tick);
      };
      tick();
    }));
    // the points of the view's tiles held: at least `n` (a view holds what its tiles hold)
    const showing = async (events, n, at, zoom) => {
      feed = { unplaced: 0, events };
      await page.evaluate(([c, z]) => window.__testMap.setView(c, z, { animate: false }), [at, zoom]);
      await page.waitForFunction(k => window.__testMap.points() >= k, n, { timeout: 15000 });
      return drawn();
    };
    assert.equal(await zoomAnimates(8), true, 'three points: the zoom animates');
    // 1 203 points held, a few dozen markers drawn: animated (it was not, counted by the points held)
    const fewDrawn = await showing(few.concat(many), 1200, [30, 5], 4);
    assert.ok(fewDrawn < 200, `a few dozen markers for 1 203 points: ${fewDrawn}`);
    assert.equal(await zoomAnimates(5), true, '1 203 points, a few dozen markers: the zoom animates');
    // over a thousand markers drawn: drawn at once
    const manyDrawn = await showing(grid, 1000, [38, 25], 6);
    assert.ok(manyDrawn > 1000, `over a thousand markers drawn: ${manyDrawn}`);
    assert.equal(await zoomAnimates(7), false, `${manyDrawn} markers: the zoom does not animate`);
    // back to a few: animated again
    await showing(few, 3, [37.5, 32], 7);
    assert.equal(await zoomAnimates(8), true, 'a few points again: the zoom animates again');
  });
});

// #3764: a zoom that regroups a few points animates as markercluster did — a bubble that splits slides its
// points out from where it stood, points that merge fly into the new bubble as it appears — over ~250 ms; the
// reader's "reduce motion" draws the new groups at once.
test('a zoom slides the points of a split bubble out and flies merging points in; reduced motion draws at once', async () => {
  const at = (i) => ({ lat: 37.5 + (i % 3) * 0.05, lon: 32 + i * 0.03 });
  const findings = [0, 1, 2].map(i => ({ citekey: `zz_m${i}`, title: `Zz m${i}`, claim: 'c', regionName: null, ...at(i),
    periodStart: -1300, periodEnd: -1250, confidence: 'high', sourceKind: 'paper' }));
  // every frame from the zoom's end for 450 ms: each drawn point's or bubble's centre, and whether it is
  // leaving (a ghost flying into a bubble)
  const trace = (page, z) => page.evaluate((zoom) => new Promise(resolve => {
    const map = window.__testMap, frames = [], animations = map.clusterAnimations();
    const centre = e => { const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; };
    map.once('zoomend', () => {
      const t0 = performance.now();
      const f = () => {
        frames.push([...document.querySelectorAll('.hd-marker:not(.is-offscreen)')]
          .filter(e => e.querySelector('.hd-find, .hd-cluster'))
          .map(e => ({ bubble: !!e.querySelector('.hd-cluster'), ghost: e.classList.contains('hd-cluster-ghost'), at: centre(e) })));
        if (performance.now() - t0 < 450) requestAnimationFrame(f);
        else resolve({ frames, animated: map.clusterAnimations() > animations });
      };
      requestAnimationFrame(f);
    });
    map.setZoom(zoom);
  }), z);
  const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
  for (const reduced of [false, true]) {
    await withPage({ width: 1440, height: 900 }, async page => {
      if (reduced) await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
      await page.route('**/api/places/geo**', route => route.fulfill({ json: { type: 'FeatureCollection', features: [] } }));
      await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
      await page.waitForFunction(() => window.__testMap);
      await page.evaluate(() => window.__testMap.setView([37.55, 32.03], 7, { animate: false }));
      await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '3', null, { timeout: 15000 });
      await idleMarker(page, '.hd-marker .hd-cluster');
      // the split: three points out of one bubble
      const split = await trace(page, 10);
      const ends = split.frames.at(-1).filter(m => !m.bubble).map(m => m.at);
      assert.equal(ends.length, 3, `three points stand alone after the split: ${JSON.stringify(split.frames.at(-1))}`);
      const firstOnes = split.frames[0].filter(m => !m.bubble).map(m => m.at);
      // how many frames each point was drawn somewhere else than where it ends
      const moving = ends.map((e, i) => split.frames.filter(fr => { const p = fr.filter(m => !m.bubble)[i]; return p && dist(p.at, e) > 1; }).length);
      if (reduced) {
        assert.equal(split.animated, false, 'reduced motion: the zoom is not animated');
        assert.deepEqual(moving, [0, 0, 0], 'reduced motion: each point is drawn where it ends from the first frame');
      } else {
        assert.equal(split.animated, true);
        // (the one nearest the bubble's middle may hardly move)
        assert.ok(moving.filter(n => n >= 3).length >= 2, `the points slide out over several frames: ${moving}`);
        // they start together (where the bubble stood) and end apart
        const spread = pts => Math.max(...pts.flatMap(a => pts.map(b => dist(a, b))));
        assert.ok(spread(firstOnes) < spread(ends) / 2, `they start near one another: ${JSON.stringify(firstOnes)} → ${JSON.stringify(ends)}`);
      }
      await idleMarker(page, '.hd-marker .hd-find');
      // the merge: three points into one bubble
      const merge = await trace(page, 7);
      const bubble = merge.frames.at(-1).find(m => m.bubble);
      assert.ok(bubble, `a bubble after the merge: ${JSON.stringify(merge.frames.at(-1))}`);
      const ghostFrames = merge.frames.filter(fr => fr.some(m => m.ghost));
      if (reduced) {
        assert.equal(ghostFrames.length, 0, 'reduced motion: nothing flies');
        assert.equal(merge.animated, false);
      } else {
        assert.ok(ghostFrames.length >= 3, `the points fly into the bubble over several frames: ${ghostFrames.length}`);
        const far = fr => Math.max(...fr.filter(m => m.ghost).map(m => dist(m.at, bubble.at)));
        assert.ok(far(ghostFrames.at(-1)) < far(ghostFrames[0]), `they come nearer the bubble: ${far(ghostFrames[0])} → ${far(ghostFrames.at(-1))}`);
        assert.equal(merge.frames.at(-1).filter(m => m.ghost).length, 0, 'and are gone once it is done');
      }
    });
  }
});

// #4718: the reader's "Map motion" (map options), per device — follow the system's reduced-motion setting (the
// default), always animate, or never — at what the page really holds with every layer on over a period (the
// full load: 500 findings, 200 sites, 200 pictures, up to 1 500 events in view, 140 polities). Each zoom is
// traced frame by frame: the regroup's motion (markers moving while the camera stands) and the camera's ease
// (asked by the app, and by MapLibre's own zoom button). Reported with HISTMERSION_TEST_MOTION_REPORT=1.
const MOTION_MODES = [
  // [the choice kept (null: never chosen), the system asks for reduced motion, the map animates]
  [null, true, false],
  ['always', true, true],
  ['never', false, false],
  [null, false, true],
];
for (const viewport of [{ width: 1920, height: 1080 }, { width: 390, height: 844 }]) {
  test(`map motion at ${viewport.width}×${viewport.height}, every layer on: follow the system, always, never`, async () => {
    await withPage(viewport, async page => {
      trackRequests(page);
      await routeFullLoad(page);
      for (const [motion, reduced, animates] of MOTION_MODES) {
        const what = `${motion ?? 'follow the system'}${reduced ? ' (system: reduce)' : ''}`;
        await page.emulateMedia({ reducedMotion: reduced ? 'reduce' : 'no-preference' });
        const held = await fullLoadView(page, baseURL, { motion });
        assert.ok(held.held >= 300 && held.drawn > 0, `${what}: the full load is held: ${JSON.stringify(held)}`);
        assert.equal(await page.evaluate(() => document.documentElement.dataset.mapMotion), animates ? 'animate' : 'still', what);
        // the app's zooms across whole levels: the regroup and the camera
        const zooms = [];
        for (const z of [6, 7, 6, 5]) zooms.push(await motionTrace(page, z));
        // MapLibre's own zoom button: its camera alone
        const button = await motionTrace(page, null, { button: 'in' });
        if (process.env.HISTMERSION_TEST_MOTION_REPORT) console.log(`# motion ${engineName} ${viewport.width}x${viewport.height} ${what} ` +
          JSON.stringify({ held, zooms: zooms.map(({ animated, zoomMs, cameraFrames, motionFrames, maxMoved, ghosts, longestFrameMs }) =>
            ({ animated, zoomMs, cameraFrames, motionFrames, maxMoved, ghosts, longestFrameMs })),
          button: { zoomMs: button.zoomMs, cameraFrames: button.cameraFrames, longestFrameMs: button.longestFrameMs } }));
        const motionFrames = zooms.reduce((n, z) => n + z.motionFrames, 0);
        if (animates) {
          assert.ok(zooms.some(z => z.animated), `${what}: a zoom regroups with motion: ${JSON.stringify(zooms)}`);
          assert.ok(motionFrames >= 3, `${what}: markers move across frames after the zoom: ${motionFrames}`);
          for (const z of zooms) assert.ok(z.zoomMs >= 150 && z.cameraFrames >= 5, `${what}: the camera eases: ${JSON.stringify(z)}`);
          // (the button's time includes the click's own round trip: its frames say whether the camera eased)
          assert.ok(button.cameraFrames >= 5, `${what}: MapLibre's zoom button eases: ${JSON.stringify(button)}`);
        } else {
          assert.ok(zooms.every(z => !z.animated && z.ghosts === 0), `${what}: no regroup motion: ${JSON.stringify(zooms)}`);
          assert.equal(motionFrames, 0, `${what}: no marker moves while the camera stands`);
          for (const z of zooms) assert.ok(z.zoomMs < 100 && z.cameraFrames <= 3, `${what}: the camera jumps: ${JSON.stringify(z)}`);
          assert.ok(button.cameraFrames <= 3, `${what}: MapLibre's zoom button jumps: ${JSON.stringify(button)}`);
        }
        // the note: only while following the system and the system asks for reduced motion (on a phone the map's
        // tools are in their drawer)
        if (viewport.width < 760) await page.locator('.mb-maptools').click();
        await page.locator('.map-options-btn').click();
        await page.locator('.map-motion-select').waitFor();
        assert.equal(await page.locator('.map-motion-select').inputValue(), motion ?? 'system');
        const note = page.locator('.map-motion-note');
        if (motion === null && reduced) {
          assert.match(await note.innerText(), /Animations are off because your system asks for reduced motion/);
          // inside the menu, never over the map when the menu is closed
          await page.keyboard.press('Escape');
          await page.locator('.map-options').waitFor({ state: 'hidden' });
          assert.equal(await note.isVisible(), false, 'the note goes with the menu');
        } else {
          assert.equal(await note.count(), 0, `${what}: no note`);
          await page.keyboard.press('Escape');
        }
      }
    });
  });
}

// #4718: the control itself — on every base map, reached by the keyboard, labelled; the choice applies at once
// (the note follows the system's setting live) and is kept across a reload.
test('map motion: the control is reached by the keyboard, applies at once, follows the system live, and is kept', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.evaluate(() => localStorage.removeItem('histmersion.ui.mapMotion'));
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    const attr = () => page.evaluate(() => document.documentElement.dataset.mapMotion);
    assert.equal(await attr(), 'still', 'follows the system: still');
    // the options button opens the menu from the keyboard on any base map; its first control takes the focus
    await page.locator('.map-options-btn').focus();
    await page.keyboard.press('Enter');
    await page.locator('.map-motion-select').waitFor();
    const online = await page.evaluate(() => !document.getElementById('map').classList.contains('basemap-vector'));
    if (online) assert.equal(await page.evaluate(() => document.activeElement?.id), 'map-motion-select', 'the motion setting has the focus');
    assert.equal(await page.locator('.map-motion-select').getAttribute('aria-label'), 'map motion');
    assert.equal(await page.locator('.map-option-motion label > span').innerText(), 'Map motion');
    assert.deepEqual(await page.locator('.map-motion-select option').allInnerTexts(), ['Follow the system', 'Always animate', 'Never animate']);
    await page.locator('.map-motion-note').waitFor();
    assert.equal(await page.locator('.map-motion-select').getAttribute('aria-describedby'), 'map-motion-note');
    // the system's setting changes: followed at once, the note goes
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.waitForFunction(() => document.documentElement.dataset.mapMotion === 'animate');
    await page.locator('.map-motion-note').waitFor({ state: 'detached' });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.locator('.map-motion-note').waitFor();
    // always: applies at once, without a reload
    await page.locator('.map-motion-select').selectOption('always');
    await page.waitForFunction(() => document.documentElement.dataset.mapMotion === 'animate');
    await page.locator('.map-motion-note').waitFor({ state: 'detached' });
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('histmersion.ui.mapMotion'))), ['always']);
    // kept across a reload, never in the link
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    assert.equal(await attr(), 'animate');
    assert.ok(!page.url().includes('mapMotion'));
    await page.locator('.map-options-btn').click();
    assert.equal(await page.locator('.map-motion-select').inputValue(), 'always');
    await page.locator('.map-motion-select').selectOption('never');
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    assert.equal(await attr(), 'still', 'never: still whatever the system says');
  });
});

// The user's "the circles that group sources should group every kind of dot": findings, sites, events and
// pictures join ONE cluster group; a bubble counts only what is shown (a layer switched off, the year window
// moved), says what it holds, splits on zoom-in, and its list shows every kind with its icon.
test('a mixed cluster counts what is shown, splits on zoom-in, and lists every kind', async () => {
  const at = (i) => ({ lat: 37.5 + (i % 3) * 0.02, lon: 32 + Math.floor(i / 3) * 0.02 });
  const findings = [0, 1].map(i => ({ citekey: `zz_f${i}`, title: `Zz f${i}`, claim: 'c', regionName: null, ...{ lon: at(i).lon, lat: at(i).lat },
    periodStart: -1300, periodEnd: -1250, confidence: 'high', sourceKind: 'paper' }));
  const sites = { type: 'FeatureCollection', features: [2, 3].map(i => ({ type: 'Feature',
    geometry: { type: 'Point', coordinates: [at(i).lon, at(i).lat] },
    properties: { id: `zz_s${i}`, label: `Zz site ${i}`, kind: 'site', periodStart: -1400, periodEnd: -1100, basis: 'dated', siteType: 'burial' } })) };
  const feed = { unplaced: 0, events: [evGeo('zz_e4', 'Zz battle four', 'battle', at(4).lat, at(4).lon)] };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', route => route.fulfill({ json:
      new URL(route.request().url()).searchParams.get('kind') === 'site' ? sites : { type: 'FeatureCollection', features: [] } }));
    // the findings feed honours the year window, as the server does
    await page.route('**/api/findings**', route => {
      const u = new URL(route.request().url());
      const from = Number(u.searchParams.get('from') ?? -1e9), to = Number(u.searchParams.get('to') ?? 1e9);
      return route.fulfill({ json: findings.filter(f => f.periodEnd >= from && f.periodStart <= to) });
    });
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await routeTiles(page, { sites: sites.features, events: feed.events });
    await page.route('**/api/events/zz_e4', route => route.fulfill({ json: evDetail('zz_e4', 'Zz battle four', 'battle', 1) }));
    await page.route('**/api/places/zz_s2?**', route => route.fulfill({ json: { place: { id: 'zz_s2', label: 'Zz site 2', kind: 'site',
      periodStart: -1400, periodEnd: -1100, source: null, names: '' }, geometry: null, parents: [], children: [], findings: 0, asOf: null,
      siteType: 'burial', media: [] } }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites","events"]'));
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    // the bubble as drawn: waited for, since a regroup replaces its element between two reads
    const bubble = () => page.waitForFunction(() => {
      const b = document.querySelector('.hd-marker .hd-cluster');
      return b ? { n: Number(b.textContent), says: b.getAttribute('aria-label'), mixed: b.classList.contains('hd-cluster--mixed'),
        // events among the others: the round bubble with the event's square tab (#1647)
        withEvents: b.classList.contains('hd-cluster--with-events') && !!b.querySelector('.hd-bubble-tab') } : null;
    }).then(h => h.jsonValue());
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '5');
    assert.deepEqual(await bubble(), { n: 5, says: '2 findings · 2 sites · 1 event', mixed: true, withEvents: true });
    // a layer switched off leaves the bubble at once; back on, it returns
    await page.locator('.viewport-toggle button[title="sites"]').click();
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '3');
    assert.equal((await bubble()).says, '2 findings · 1 event');
    await page.locator('.viewport-toggle button[title="sites"]').click();
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '5');
    await page.locator('.viewport-toggle button[title="sources"]').click();
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '3');
    assert.equal((await bubble()).says, '2 sites · 1 event');
    await page.locator('.viewport-toggle button[title="sources"]').click();
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '5');
    // the year window moves past the findings: they leave the bubble
    await page.goto(new URL('/#from=-1200&to=-1100', baseURL).href);
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '3', null, { timeout: 10000 });
    assert.equal((await bubble()).says, '2 sites · 1 event');
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.waitForFunction(() => document.querySelector('.hd-marker .hd-cluster')?.textContent === '5', null, { timeout: 10000 });
    // a click lists every kind, each row with its icon; a site's row pins its card
    await page.waitForFunction(() => document.querySelectorAll('.hd-marker .hd-cluster').length === 1);
    const b = await page.locator('.hd-marker .hd-cluster').boundingBox();
    await page.mouse.click(b.x + b.width / 2, b.y + b.height / 2);
    await page.waitForSelector('.pick-title');
    assert.match(await page.locator('.pick-title').innerText(), /2 findings · 2 sites · 1 event in this cluster/);
    assert.equal(await page.locator('.hit-source').count(), 2);
    assert.equal(await page.locator('.hit-dot[data-dot-kind="site"] .pick-icon svg').count(), 2);
    assert.equal(await page.locator('.hit-dot[data-dot-kind="event"] .pick-icon svg').count(), 1);
    assert.equal(await page.locator('.hit-source .pick-icon svg').count(), 2);
    await page.locator('.hit-dot[data-dot-kind="site"]').first().click();
    await page.waitForSelector('.cite-tooltip.is-pinned .cite-tip-title');
    assert.match(await page.locator('.cite-tooltip.is-pinned .cite-tip-title').innerText(), /Zz site 2/);
    await page.keyboard.press('Escape');
    // zoomed in, the bubble splits into the points themselves
    await page.evaluate(() => window.__testMap.setView([37.52, 32.01], 12, { animate: false }));
    await page.waitForFunction(() => !document.querySelector('.hd-marker .hd-cluster') &&
      document.querySelectorAll('.hd-marker .hd-point').length === 5, null, { timeout: 10000 });
    assert.equal(await page.locator('.hd-find').count(), 2);
    assert.equal(await page.locator('.hd-site-marker').count(), 2);
    assert.equal(await page.locator('.hd-marker .hd-ev').count(), 1);
  });
});

// The layer filters are ONE icon menu (a native <select> cannot show icons): a button with the choice's
// icon, a listbox with the map's own badge per option, the keyboard pattern of a listbox; the toolbar's
// switches carry the shape each layer draws.
test('the icon menus: icons per option, keyboard choice updates the filter; the toolbar shows each layer\'s shape', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    let asked = [];
    await page.route('**/api/events/geo**', route => route.fulfill({ json: { unplaced: 0, events: [] } }));
    await routeTiles(page, { asked });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events","sites"]'));
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    // the toolbar: each layer switch's icon is its marker shape, named
    for (const name of ['sources', 'boundaries', 'sites', 'media', 'events']) {
      const b = page.locator(`.viewport-toggle button[title="${name}"]`);
      assert.equal(await b.locator('svg.icon').count(), 1, name);
      assert.equal((await b.innerText()).trim(), name);
    }
    const kind = page.locator('.icon-select-button[aria-label="event kind"]');
    assert.equal(await kind.getAttribute('aria-haspopup'), 'listbox');
    assert.equal(await kind.getAttribute('aria-expanded'), 'false');
    await kind.focus();
    await page.keyboard.press('ArrowDown');
    const list = page.locator('.icon-select-list[aria-label="event kind"]');
    await list.waitFor();
    assert.equal(await kind.getAttribute('aria-expanded'), 'true');
    assert.ok(await list.evaluate(el => el === document.activeElement), 'the listbox has the focus');
    const opts = await list.locator('[role="option"]').count();
    assert.equal(await list.locator('[role="option"] .icon-select-icon svg').count(), opts, 'an icon per option');
    assert.ok(await list.locator('[role="option"] .hd-badge--square').count() >= 20, 'the kinds as the map draws them');
    // readable: the badge stands off the menu (≥ 3:1), the glyph's ink off the badge (≥ 3:1), at ≥ 18 px
    const contrast = await list.evaluate(el => {
      const rgb = c => c.match(/[\d.]+/g).slice(0, 3).map(Number);
      const lum = c => { const [r, g, b] = rgb(c).map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
      const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
      const badge = el.querySelector('[role="option"] .hd-badge--square');
      const ink = getComputedStyle(badge.querySelector('.glyph-ink')).stroke;
      const bg = getComputedStyle(badge).backgroundColor;
      return { badge: ratio(bg, getComputedStyle(el).backgroundColor), glyph: ratio(ink, bg), size: badge.getBoundingClientRect().width };
    });
    assert.ok(contrast.badge >= 3 && contrast.glyph >= 3 && contrast.size >= 18, JSON.stringify(contrast));
    // the triggers say which menu they are: the events' square for the kind, a solid-and-dashed pair for the tier
    const triggerIcon = name => page.locator(`.icon-select-button[aria-label="${name}"] .icon-select-icon`).innerHTML();
    assert.notEqual(await triggerIcon('event kind'), await triggerIcon('tier'));
    assert.deepEqual(await list.locator('.icon-select-group').allTextContents(), ['natural events', 'human events'], 'grouped');
    // inside the viewport, scrolling when long
    const box = await list.evaluate(el => { const r = el.getBoundingClientRect(); return { top: r.top, bottom: r.bottom, vh: innerHeight }; });
    assert.ok(box.top >= 0 && box.bottom <= box.vh, JSON.stringify(box));
    // type-ahead to "siege", Enter chooses it: the filter (and the feed) follow, the focus returns
    await page.keyboard.type('sie');
    const activeId = await list.getAttribute('aria-activedescendant');
    assert.match(await page.locator(`#${activeId}`).innerText(), /siege/);
    const before = asked.length;
    await page.keyboard.press('Enter');
    await page.waitForFunction(() => !document.querySelector('.icon-select-list:not(.is-hidden)[aria-label="event kind"]'));
    assert.match(await kind.innerText(), /event kind: siege/);
    assert.equal(await kind.locator('.icon-select-icon .hd-badge--square').count(), 1);
    assert.ok(await kind.evaluate(el => el === document.activeElement), 'the focus is back on the button');
    await page.waitForTimeout(400);
    assert.ok(asked.slice(before).some(u => u.pathname.startsWith('/api/tiles/events/') && u.searchParams.get('kind') === 'siege'),
      'kind=siege was asked for');
    // Escape closes without choosing; a click outside closes too
    await page.keyboard.press('ArrowDown');
    await list.waitFor();
    await page.keyboard.press('Home');
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => document.querySelector('.icon-select-list[aria-label="event kind"]').classList.contains('is-hidden'));
    assert.match(await kind.innerText(), /siege/);
    await page.locator('.icon-select-button[aria-label="site type"]').click();
    await page.locator('.icon-select-list[aria-label="site type"]').waitFor();
    assert.ok(await page.locator('.icon-select-list[aria-label="site type"] [role="option"] .hd-badge--circle').count() >= 14);
    await page.mouse.click(700, 500);
    await page.waitForFunction(() => document.querySelector('.icon-select-list[aria-label="site type"]').classList.contains('is-hidden'));
    if (process.env.MENU_SHOT) {
      await kind.click();
      await page.waitForTimeout(200);
      await page.screenshot({ path: process.env.MENU_SHOT });
    }
  });
});

// The jobs window pages its list from fixed feeds (nothing is written): the heading says what is
// shown of how many, the filter buttons count the whole table from the server's headers, and "Show
// older" asks for the next page by cursor, appends it, and goes once nothing older is left.
test('the jobs window says latest N of M, counts every state, and loads an older page', async () => {
  const job = i => ({ id: `zz-job-${String(i).padStart(3, '0')}`, kind: 'ingest', status: i % 15 === 0 ? 'failed' : 'done',
    citekey: null, progress: null, error: null, sha256: null, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' });
  const counts = 'done=140,failed=10';
  await withPage({ width: 1440, height: 900 }, async page => {
    const asked = [];
    await page.route(/\/api\/jobs(\?|$)/, route => {
      const q = new URL(route.request().url()).searchParams;
      asked.push(Object.fromEntries(q));
      const before = q.get('before');
      if (q.get('status') === 'failed')
        return route.fulfill({ json: Array.from({ length: 150 }, (_, i) => job(i)).filter(j => j.status === 'failed'),
          headers: { 'X-Total-Count': '10', 'X-State-Counts': counts } });
      const rows = before ? Array.from({ length: 50 }, (_, i) => job(100 + i)) : Array.from({ length: 100 }, (_, i) => job(i));
      const headers = { 'X-Total-Count': '150', 'X-State-Counts': counts };
      if (!before) headers['X-Next-Before'] = 'zz-job-099';
      return route.fulfill({ json: rows, headers });
    });
    await page.locator('.fbar .tool-btns button[title="jobs"]').click();
    const pane = page.locator('.jobs-pane');
    const heading = pane.locator('.list-heading');
    await heading.waitFor();
    assert.equal(await heading.innerText(), 'Latest 100 of 150 jobs');
    assert.equal(await pane.locator('.job-row').count(), 100);
    // the buttons count the table, not the 100 rows loaded (6 of which failed)
    const button = label => pane.locator('.jobs-filter button', { hasText: new RegExp(`^${label}\\b`) });
    const label = async l => (await button(l).textContent()).replace(/\s+/g, ' ').trim();
    assert.equal(await label('all'), 'all 150');
    assert.equal(await label('failed'), 'failed 10');
    assert.equal(await label('done'), 'done 140');
    const more = pane.locator('.list-more');
    assert.ok(await more.isVisible(), 'Show older is offered');
    await more.locator('button').click();
    await page.waitForFunction(() => document.querySelectorAll('.jobs-pane .job-row').length === 150);
    assert.equal(asked.at(-1).before, 'zz-job-099', 'the older page is asked for from the last row shown');
    assert.equal(await heading.innerText(), '150 jobs');
    assert.equal(await more.isVisible(), false, 'nothing older: the button is gone');
    assert.equal(await pane.locator('.job-row').last().locator('.job-meta').innerText().then(t => t.includes('zz-job-1')), true);
    // a filter starts over from the newest page, asked for with the state
    await button('failed').click();
    await page.waitForFunction(() => document.querySelector('.jobs-pane .list-heading')?.innerText === '10 failed jobs');
    assert.equal(await pane.locator('.job-row').count(), 10);
    assert.equal(asked.at(-1).status, 'failed');
    assert.equal(asked.at(-1).before, undefined);
    assert.equal(await label('all'), 'all 150', 'the counts stay the whole table');
    assert.equal(await more.isVisible(), false);
  });
});

// The list windows filter on the server (core ListFilters): a facet chip sends its value, the chips
// count what the server counted (over every other filter), the rows the chip leaves out go at once —
// within 100 ms, before the server's page lands — and the filter is in the URL. Fixed answers; the
// only "writes" (a bulk approval) are answered by the test, never sent to the server.
const facetHeader = facets => Object.entries(facets)
  .flatMap(([f, vs]) => vs.map(([v, n]) => `${f}=${encodeURIComponent(v).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase())}=${n}`))
  .join(',');

/** Click `selector` and report, one frame later, how long it took and how many `rows` are shown. */
async function clickTimed(page, selector, rows) {
  return page.evaluate(([sel, rs]) => new Promise(resolve => {
    const t0 = performance.now();
    document.querySelector(sel).click();
    requestAnimationFrame(() => resolve({ ms: performance.now() - t0, rows: document.querySelectorAll(rs).length }));
  }), [selector, rows]);
}

test('the approvals window: kind chips with the server\'s counts, a picked kind narrows at once, a bulk approval asks first', async () => {
  const appr = (id, kind, state = 'open') => ({ id, runId: null, kind, citekey: kind === 'edge' ? 'zz_a2020' : '',
    subject: kind === 'edge' ? null : `zz-pw-goal-${id}`, state, createdAt: '2026-10-01T00:00:00Z', evidence: '' });
  const all = [appr(9, 'goal-plan'), appr(8, 'article-publish'), appr(7, 'goal-plan'), appr(6, 'edge')];
  const kinds = { kind: [['goal-plan', 64], ['article-publish', 30], ['edge', 3]] };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true, writeKeyRequired: false } });
    });
    const asked = [], decided = [];
    await page.route(/\/api\/agents\/approvals(\?|$)/, async route => {
      const q = new URL(route.request().url()).searchParams;
      asked.push({ kind: q.getAll('kind'), state: q.get('state'), subject: q.get('subject') });
      const picked = q.getAll('kind');
      // answered a little late, so the window's own narrowing is what the person sees first
      await new Promise(r => setTimeout(r, 300));
      const rows = all.filter(a => picked.length === 0 || picked.includes(a.kind));
      const total = picked.length === 0 ? 97 : picked.reduce((n, k) => n + kinds.kind.find(([v]) => v === k)[1], 0);
      return route.fulfill({ json: rows, headers: { 'X-Total-Count': String(total), 'X-State-Counts': `open=${total},approved=12`,
        'X-Facet-Counts': facetHeader(kinds), 'X-Next-Before': '1' } });
    });
    await page.route(/\/api\/agents\/approvals\/\d+\/(approve|reject)$/, route => {
      const [, id, d] = new URL(route.request().url()).pathname.match(/approvals\/(\d+)\/(\w+)$/);
      decided.push([Number(id), d]);
      return route.fulfill({ json: { ...appr(Number(id), 'goal-plan'), state: 'approved' } });
    });
    await page.goto(new URL('/#open=approvals', baseURL).href);
    await page.reload();
    const pane = page.locator('.approvals-pane');
    const heading = pane.locator('.list-heading');
    await page.waitForFunction(() => document.querySelectorAll('.approvals-pane .approval-row').length === 4);
    assert.equal(await heading.innerText(), 'Latest 4 of 97 open approvals');
    const chip = v => pane.locator(`.list-facet button[data-value="${v}"]`);
    const chipText = async v => (await chip(v).innerText()).replace(/\s+/g, ' ').trim();
    assert.equal(await chipText('goal-plan'), 'goal-plan 64');
    assert.equal(await chipText('article-publish'), 'article-publish 30');
    assert.equal(await chipText('edge'), 'edge 3');
    assert.equal(await pane.locator('.approvals-state button[data-state="open"]').innerText().then(t => t.replace(/\s+/g, ' ').trim()), 'open 97');
    // a kind: the rows of other kinds leave in the same frame; the request carries the kind
    const t = await clickTimed(page, '.approvals-pane .list-facet button[data-value="goal-plan"]', '.approvals-pane .approval-row');
    assert.equal(t.rows, 2, 'the other kinds are gone at once');
    assert.ok(t.ms < 100, `a chip answers within 100 ms (${t.ms.toFixed(1)} ms)`);
    assert.equal(await chip('goal-plan').getAttribute('aria-pressed'), 'true');
    await page.waitForFunction(() => document.querySelector('.approvals-pane .list-heading')?.innerText === 'Latest 2 of 64 open goal-plan approvals');
    assert.deepEqual(asked.at(-1).kind, ['goal-plan']);
    assert.ok(page.url().includes('apprKind=goal-plan'), page.url());
    // multi-select: a second kind widens to both
    await chip('edge').click();
    await page.waitForFunction(() => document.querySelector('.approvals-pane .list-heading')?.innerText === 'Latest 3 of 67 open goal-plan/edge approvals');
    assert.deepEqual(asked.at(-1).kind, ['goal-plan', 'edge']);
    await chip('edge').click();
    await page.waitForFunction(() => document.querySelectorAll('.approvals-pane .approval-row').length === 2);
    // the subject box asks once, after the typing pause — not once per key
    const before = asked.length;
    await pane.locator('.list-text').pressSequentially('zz-pw', { delay: 20 });
    await page.waitForTimeout(900);
    const typed = asked.slice(before).filter(a => a.subject);
    assert.equal(typed.length, 1, JSON.stringify(asked.slice(before)));
    assert.equal(typed[0].subject, 'zz-pw');
    await pane.locator('.list-text').fill('');
    await pane.locator('.list-text').press('Enter');
    // bulk: tick every row shown, approve them — after a confirmation that names what is approved
    await pane.locator('.approvals-bulk button', { hasText: 'select all 2 shown' }).click();
    const dialogs = [];
    page.on('dialog', d => { dialogs.push(d.message()); d.accept(); });
    await pane.locator('.approvals-bulk button[data-bulk="approve"]').click();
    await page.waitForFunction(() => !document.querySelector('.approvals-bulk button[data-bulk="approve"]')?.textContent.includes('(2)'));
    await page.waitForTimeout(300);
    assert.equal(dialogs.length, 1);
    assert.match(dialogs[0], /^Approve 2 approvals \(2 goal-plan\)\?/);
    assert.deepEqual(decided.sort(), [[7, 'approve'], [9, 'approve']]);
    // "clear filters" empties every filter and the URL
    await pane.locator('.list-clear').click();
    await page.waitForFunction(() => document.querySelectorAll('.approvals-pane .approval-row').length === 4);
    assert.ok(!page.url().includes('apprKind'), page.url());
  });
});

test('the agent runs window: pipeline chips and the manager picker filter on the server, with their counts', async () => {
  const run = (id, pipeline, status, managerId = null) => ({ id, pipeline, target: 'zz_a2020', status,
    createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z', managerId });
  const all = [run('zz-r4', 'review-article', 'done', 'review'), run('zz-r3', 'enrich-source', 'failed', 'enrich'),
    run('zz-r2', 'review-article', 'failed', 'review'), run('zz-r1', 'plan-goal', 'done')];
  const facets = { pipeline: [['review-article', 211], ['enrich-source', 176], ['plan-goal', 8]], origin: [['manager', 380], ['person', 15]],
    manager: [['review', 223], ['enrich', 176]], provider: [['codex', 1148], ['claude-code', 214]], model: [['codex:gpt-6.1-sol', 752]] };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true } });
    });
    const asked = [];
    await page.route(/\/api\/agents\/runs(\?|$)/, async route => {
      const q = new URL(route.request().url()).searchParams;
      asked.push({ pipeline: q.getAll('pipeline'), manager: q.getAll('manager'), status: q.getAll('status') });
      await new Promise(r => setTimeout(r, 200));
      const rows = all.filter(r => (q.getAll('pipeline').length === 0 || q.getAll('pipeline').includes(r.pipeline)) &&
        (q.getAll('manager').length === 0 || q.getAll('manager').includes(r.managerId)));
      const total = q.getAll('pipeline').includes('review-article') ? 211 : 395;
      return route.fulfill({ json: rows, headers: { 'X-Total-Count': String(total), 'X-State-Counts': `done=${total - 18},failed=18`,
        'X-Facet-Counts': facetHeader(facets) } });
    });
    await page.goto(new URL('/#open=runs', baseURL).href);
    await page.reload();
    const pane = page.locator('.runs-pane');
    await page.waitForFunction(() => document.querySelectorAll('.runs-pane .run-row').length === 4);
    const chip = v => pane.locator(`.list-facet button[data-value="${v}"]`);
    assert.equal((await chip('review-article').innerText()).replace(/\s+/g, ' ').trim(), 'review-article 211');
    assert.equal((await chip('manager').innerText()).replace(/\s+/g, ' ').trim(), 'by a manager 380');
    const t = await clickTimed(page, '.runs-pane .list-facet button[data-value="review-article"]', '.runs-pane .run-row');
    assert.equal(t.rows, 2);
    assert.ok(t.ms < 100, `a chip answers within 100 ms (${t.ms.toFixed(1)} ms)`);
    await page.waitForFunction(() => document.querySelector('.runs-pane .list-heading')?.innerText === 'Latest 2 of 211 review-article runs');
    assert.deepEqual(asked.at(-1).pipeline, ['review-article']);
    assert.ok(page.url().includes('runPipeline=review-article'), page.url());
    // the manager picker lists the counted managers, and picking one sends it
    const picker = pane.locator('select.list-select[data-facet="manager"]');
    assert.deepEqual(await picker.locator('option').allInnerTexts(), ['any manager', 'review (223)', 'enrich (176)']);
    await picker.selectOption('review');
    await page.waitForFunction(() => document.querySelectorAll('.runs-pane .run-row').length === 2);
    await page.waitForFunction(() => location.hash.includes('runManager=review'));
    assert.deepEqual(asked.at(-1).manager, ['review']);
    assert.deepEqual(asked.at(-1).pipeline, ['review-article'], 'filters add up');
    // a state button on top of the filters: the request carries all three
    await pane.locator('.jobs-filter button', { hasText: /^failed/ }).click();
    await page.waitForFunction(() => document.querySelectorAll('.runs-pane .run-row').length === 1);
    assert.deepEqual(asked.at(-1).status, ['failed']);
  });
});

test('the jobs window: kind chips with the server\'s counts filter the list, and survive a reload', async () => {
  const job = (id, kind, status = 'done') => ({ id, kind, status, citekey: null, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' });
  const all = [job('zz-j4', 'ingest'), job('zz-j3', 'media-import', 'failed'), job('zz-j2', 'ingest', 'failed'), job('zz-j1', 'agent')];
  const facets = { kind: [['agent', 1520], ['ingest', 507], ['media-import', 18]] };
  await withPage({ width: 1440, height: 900 }, async page => {
    const asked = [];
    await page.route(/\/api\/jobs(\?|$)/, async route => {
      const q = new URL(route.request().url()).searchParams;
      asked.push(q.getAll('kind'));
      await new Promise(r => setTimeout(r, 200));
      const kinds = q.getAll('kind');
      const rows = all.filter(j => kinds.length === 0 || kinds.includes(j.kind));
      const total = kinds.length === 0 ? 2045 : kinds.reduce((n, k) => n + facets.kind.find(([v]) => v === k)[1], 0);
      return route.fulfill({ json: rows, headers: { 'X-Total-Count': String(total), 'X-State-Counts': `done=${total - 2},failed=2`,
        'X-Facet-Counts': facetHeader(facets) } });
    });
    await page.goto(new URL('/#open=jobs', baseURL).href);
    await page.reload();
    const pane = page.locator('.jobs-pane');
    await page.waitForFunction(() => document.querySelectorAll('.jobs-pane .job-row').length === 4);
    const chip = v => pane.locator(`.list-facet button[data-value="${v}"]`);
    assert.equal((await chip('ingest').innerText()).replace(/\s+/g, ' ').trim(), 'ingest (fetch or upload, then OCR) 507');
    const t = await clickTimed(page, '.jobs-pane .list-facet button[data-value="ingest"]', '.jobs-pane .job-row');
    assert.equal(t.rows, 2);
    assert.ok(t.ms < 100, `a chip answers within 100 ms (${t.ms.toFixed(1)} ms)`);
    await page.waitForFunction(() => document.querySelector('.jobs-pane .list-heading')?.innerText === 'Latest 2 of 507 ingest jobs');
    assert.deepEqual(asked.at(-1), ['ingest']);
    // the counts of the other kinds stay: they are counted over every other filter
    assert.equal((await chip('media-import').innerText()).replace(/\s+/g, ' ').trim(), 'media-import 18');
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll('.jobs-pane .job-row').length === 2);
    assert.equal(await chip('ingest').getAttribute('aria-pressed'), 'true');
    assert.deepEqual(asked.at(-1), ['ingest'], 'the filter came back from the URL');
  });
});

// An article's review state: each Articles row carries the server's badge, its meaning on hover; in the
// article window (the server's page, here a fixed fragment of the shape Pages renders) a research goal
// that links the article opens the goal's window. Fixed answers; nothing is written.
test('articles: the review badge on each row with its meaning, and a linked goal opens its window', async () => {
  const row = (slug, review) => ({ slug, title: `Zz ${slug}`, kind: 'concept', summary: null, tags: [], regions: [],
    periodStart: null, periodEnd: null, review });
  const badge = (state, label, detail) => ({ state, label, detail, openMajors: 0, openMinors: 0, findings: 0, runs: 0 });
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route(/\/api\/articles(\?|$)/, route => route.fulfill({ json: { total: 2, articles: [
      row('zz_rev_a', badge('in-review', 'in review · round 2', 'In review: the review panel is working on it now (round 2 of this run).')),
      row('zz_rev_b', badge('majors-open', '3 majors open', 'Reviewed: 3 MAJOR findings are still open.'))] } }));
    await page.route(/\/article\/zz_rev_a\?fragment=true/, route => route.fulfill({ contentType: 'text/html', body:
      '<h1>Zz rev a</h1><p class="article-review-line"><span class="muted">review:</span> ' +
      '<span class="job-state review-state state-in-review" title="In review.">in review · round 2</span> ' +
      '<span class="muted">· goal:</span> <a class="goal-ref" href="/#open=goal:zz-rev-goal" title="zz-rev-goal">' +
      '<span class="job-state goal-status state-compiled">compiled</span> Zz goal</a></p><p>Text.</p>' }));
    await page.route('**/api/goals/zz-rev-goal**', route => route.fulfill({ json: { slug: 'zz-rev-goal', title: 'Zz goal',
      status: 'compiled', tags: [], regions: [] } }));
    await page.goto(new URL('/#tab=articles', baseURL).href);
    await page.reload();
    const badges = page.locator('.articles-list .article-meta .review-state');
    await page.waitForFunction(() => document.querySelectorAll('.articles-list .review-state').length === 2);
    assert.deepEqual(await badges.allInnerTexts(), ['in review · round 2', '3 majors open']);
    assert.equal(await badges.nth(1).getAttribute('title'), 'Reviewed: 3 MAJOR findings are still open.');
    assert.match(await badges.nth(1).getAttribute('class'), /\bstate-majors-open\b/);
    // the colour comes from the stylesheet: a red badge, not the grey of an unstyled one
    const colours = await badges.evaluateAll(els => els.map(e => getComputedStyle(e).color));
    assert.notEqual(colours[0], colours[1], `each state its own colour (${colours})`);
    await page.locator('.articles-list .article-link').first().click();
    const goal = page.locator('.wm-content .article-review-line a.goal-ref');
    await goal.waitFor();
    await goal.click();
    await page.locator('.wm-title', { hasText: 'goal · zz-rev-goal' }).waitFor();
    assert.equal(await page.locator('.wm-title', { hasText: 'Zz rev a' }).count(), 1, 'the article window stays open');
  });
});

// One pagination look for every sidebar tab: the same count line ("showing 1–N of T <things> · …") and
// the same "show N more (M left)" button, which fetches the next page. Fixed answers; nothing is written.
test('every sidebar tab: one count line and one "show N more (M left)" button', async () => {
  const article = i => ({ slug: `zz_pg_${String(i).padStart(3, '0')}`, title: `Zz pg ${i}`, kind: 'concept',
    summary: null, tags: [], regions: [], periodStart: null, periodEnd: null });
  const event = i => ({ id: `zz_ev_${i}`, label: `Zz event ${i}`, kind: 'battle', timeSpan: { notBefore: -1300, beginBy: null,
    endFrom: null, notAfter: -1290 }, historicity: 'attested', placeId: null, parentId: null, description: null,
    citekey: null, scale: 'local', scaleStated: false, tier: 'catalogue', precision: 'precise' });
  const source = i => ({ source: { citekey: `zz_src_${i}`, title: `Zz source ${i}`, year: 2000, status: 'current',
    topics: [], regions: [], periodStart: null, periodEnd: null, summary: null }, why: [], matched: [] });
  await withPage({ width: 1440, height: 900 }, async page => {
    const asked = [];
    await page.route(/\/api\/sources(\?|$)/, route => {
      const u = new URL(route.request().url());
      const offset = Number(u.searchParams.get('offset') || 0), limit = Number(u.searchParams.get('limit') || 20);
      asked.push(['sources', offset]);
      const n = Math.min(limit, 47 - offset);
      return route.fulfill({ json: { total: 47, hits: Array.from({ length: n }, (_, i) => source(offset + i)) } });
    });
    await page.route(/\/api\/articles(\?|$)/, route => route.fulfill({ json: { total: 30,
      articles: Array.from({ length: 30 }, (_, i) => article(i)),
      kinds: [{ kind: 'concept', count: 28 }, { kind: 'index', count: 2 }] } }));
    await page.route(/\/api\/events(\?|$)/, route => {
      const offset = Number(new URL(route.request().url()).searchParams.get('offset') || 0);
      asked.push(['events', offset]);
      return route.fulfill({ json: { total: 60, events: Array.from({ length: Math.min(25, 60 - offset) }, (_, i) => event(offset + i)) } });
    });
    await page.route(/\/api\/aggregate(\?|$)/, route => route.fulfill({ json: { from: -3500, to: -500, bucket: 100,
      buckets: [], by: 'time', unit: 'sources', total: 47, depth: 1, tree: [
        { key: 'lba', label: 'Late Bronze Age', kind: 'time', count: 40, start: -1600, end: -1100, overlap: 0,
          contested: false, alsoUnder: [], children: [], samples: [] }] } }));
    const count = sel => page.locator(`${sel} .count`).innerText();
    const button = sel => page.locator(`${sel} .results-more button.more`);

    // sources: the reference
    await page.goto(new URL('/#tab=sources', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => /showing 1–20 of 47 sources/.test(document.querySelector('#count')?.innerText || ''));
    assert.match(await page.locator('#count').innerText(), /^showing 1–20 of 47 sources · .* → /);
    const srcMore = page.locator('.results-container .results-more button.more');
    assert.equal(await srcMore.innerText(), 'show 20 more (27 left)');
    await srcMore.click();
    await page.waitForFunction(() => /showing 1–40 of 47 sources/.test(document.querySelector('#count')?.innerText || ''));
    assert.equal(await srcMore.innerText(), 'show 7 more (7 left)');

    // articles: the same line with the kind counts, the same button
    await page.goto(new URL('/#tab=articles', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll('.articles-list li').length === 25);
    assert.match(await count('.articles-pane'), /^showing 1–25 of 30 articles · 28 concepts · 2 indexes · .* → /);
    assert.equal(await button('.articles-pane').innerText(), 'show 5 more (5 left)');
    await button('.articles-pane').click();
    await page.waitForFunction(() => document.querySelectorAll('.articles-list li').length === 30);
    assert.match(await count('.articles-pane'), /^30 articles · /);
    assert.equal(await button('.articles-pane').count(), 0, 'no button once every row is shown');

    // events: the same line and button; past the first fetch the next page comes from the server
    await page.goto(new URL('/#tab=events', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll('.events-list li').length === 25);
    assert.match(await count('.events-pane'), /^showing 1–25 of 60 events · .* → /);
    assert.equal(await button('.events-pane').innerText(), 'show 25 more (35 left)');
    await button('.events-pane').click();
    await page.waitForFunction(() => document.querySelectorAll('.events-list li').length === 50);
    assert.ok(asked.some(([k, o]) => k === 'events' && o === 25), `the next page was fetched (${JSON.stringify(asked)})`);
    assert.match(await count('.events-pane'), /^showing 1–50 of 60 events/);

    // by time: every source is in the tree — the same line, no button
    await page.goto(new URL('/#tab=bytime', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => /47 sources · 1 period/.test(document.querySelector('#tree-count')?.innerText || ''));
    assert.match(await page.locator('#tree-count').innerText(), /^47 sources · 1 period · .* → /);
    assert.equal(await page.locator('#tree-count').getAttribute('class'), 'count');
  });
});

// #1798 item 5: the Articles tab reaches the rows past its first fetch (the "show more" button used to
// vanish at 200 although the total was known), and a failed Events load says why instead of staying on
// "Loading…". Fixed answers; nothing is written.
test('#1798: the Articles tab pages past its first fetch, and a failed Events load shows an error', async () => {
  const article = i => ({ slug: `zz_page_${String(i).padStart(3, '0')}`, title: `Zz paged ${i}`, kind: 'concept',
    summary: null, tags: [], regions: [], periodStart: null, periodEnd: null });
  await withPage({ width: 1440, height: 900 }, async page => {
    const offsets = [];
    await page.route(/\/api\/articles(\?|$)/, route => {
      const offset = Number(new URL(route.request().url()).searchParams.get('offset') || 0);
      offsets.push(offset);
      const rows = Array.from({ length: Math.min(200, 230 - offset) }, (_, i) => article(offset + i));
      return route.fulfill({ json: { total: 230, articles: rows } });
    });
    await page.route(/\/api\/events(\?|$)/, route => route.fulfill({ status: 500, body: 'boom' }));
    await page.goto(new URL('/#tab=articles', baseURL).href);
    await page.reload();
    const pane = page.locator('.articles-pane');
    await page.waitForFunction(() => document.querySelectorAll('.articles-list li').length === 25);
    const more = pane.locator('.results-more button.more');
    assert.match(await more.innerText(), /show 25 more \(205 left\)/);
    for (let i = 0; i < 20 && await more.isVisible(); i++) {
      const before = await pane.locator('.articles-list li').count();
      await more.click();
      await page.waitForFunction(n => document.querySelectorAll('.articles-list li').length > n, before);
    }
    assert.equal(await pane.locator('.articles-list li').count(), 230, 'every row is reachable');
    assert.ok(offsets.includes(200), `the next page was fetched from the server (offsets ${offsets})`);
    // the Events tab: a failed load ends with an announced error, not "Loading…" forever
    await page.goto(new URL('/#tab=events', baseURL).href);
    await page.reload();
    const err = page.locator('.events-pane .form-error[role="alert"]');
    await err.waitFor();
    assert.match(await err.innerText(), /Could not list events/);
    assert.equal(await page.locator('.events-pane .events-loading').count(), 0);
  });
});

// The markers read at a glance: four silhouettes that differ by OUTLINE (a near-sharp square, a true circle, a
// turned square, a print with a white mat), each glyph ~60 % of its shape in a thick white stroke; the key's
// badges ≥ 18 px; and a cluster bubble's disc and rings concentric at 1-, 2- and 3-digit counts (they were a
// CSS background and box-shadows, rasterised apart: off-centre wherever a marker sat off the pixel grid).
test('markers: four silhouettes by outline, legible glyphs, a readable key, concentric cluster rings', async () => {
  const f = (i, lat, lon, kind = 'book') => ({ citekey: `zz_f${i}`, title: `Zz ${i}`, claim: 'c', regionName: null, lat, lon,
    periodStart: -1300 + i, periodEnd: -1250 + i, confidence: 'high', sourceKind: kind });
  const findings = [f(0, 37.5, 30)];
  [3, 14, 120].forEach((n, k) => { for (let i = 0; i < n; i++) findings.push(f(1000 * (k + 1) + i, 36 + (i % 10) * 0.004, 32 + k * 1.5 + Math.floor(i / 10) * 0.004)); });
  const sites = { type: 'FeatureCollection', features: [{ type: 'Feature', geometry: { type: 'Point', coordinates: [31, 37.5] },
    properties: { id: 'zz_s', label: 'Zz site', kind: 'site', periodStart: -1300, periodEnd: -1200, basis: 'dated', siteType: 'theatre' } }] };
  const feed = { unplaced: 0, events: [evGeo('zz_e', 'Zz treaty', 'treaty', 37.5, 32)] };
  const media = [{ citekey: null, assetName: 'zz.jpg', kind: 'photo', lat: 37.5, lon: 33, periodStart: -1300, periodEnd: -1200, caption: null, uid: 'u1' }];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/places/geo**', route => route.fulfill({ json:
      new URL(route.request().url()).searchParams.get('kind') === 'site' ? sites : { type: 'FeatureCollection', features: [] } }));
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await page.route('**/api/media-geo**', route => route.fulfill({ json: media }));
    await routeTiles(page, { sites: sites.features, events: feed.events, media });
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites","events","media"]'));
    await page.goto(new URL('/#from=-1350&to=-1100', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37, 32], 7, { animate: false }));
    await page.waitForFunction(() => document.querySelectorAll('.hd-marker .hd-cluster').length === 3 &&
      document.querySelector('.hd-ev[data-id="zz_e"]') && document.querySelector('.hd-badge--photo.hd-point') &&
      document.querySelector('.hd-site-marker') && document.querySelector('.hd-find:not(.hd-coincident)'), null, { timeout: 10000 });
    const shapes = await page.evaluate(() => {
      const one = sel => {
        const el = document.querySelector(sel), s = getComputedStyle(el), r = el.getBoundingClientRect();
        const g = el.querySelector('svg.glyph').getBoundingClientRect();
        const before = getComputedStyle(el, '::before');
        return { w: r.width, h: r.height, radius: parseFloat(s.borderTopLeftRadius), radiusRaw: s.borderTopLeftRadius,
          edge: s.borderTopColor, edgeW: parseFloat(s.borderTopWidth), glyph: g.width,
          ink: parseFloat(getComputedStyle(el.querySelector('.glyph-ink')).strokeWidth),
          turned: before.transform, beforeW: parseFloat(before.width) };
      };
      return { square: one('.hd-ev[data-id="zz_e"]'), circle: one('.hd-site-marker'), diamond: one('.hd-find:not(.hd-coincident)'),
        photo: one('.hd-badge--photo.hd-point') };
    });
    const msg = JSON.stringify(shapes);
    // sizes: ~24–26 px (the diamond's and the print's boxes larger, so they look as big)
    assert.ok(shapes.square.w >= 24 && shapes.circle.w >= 26 && shapes.diamond.w >= 32 && shapes.photo.w >= 30, msg);
    // outlines: the square's corners barely rounded, the circle round, the diamond turned, the print matted in white
    assert.ok(shapes.square.radius <= 4, msg);
    assert.equal(shapes.circle.radiusRaw, '50%', msg);
    assert.match(shapes.diamond.turned, /^matrix\(0\.7071/, msg);
    assert.equal(shapes.photo.edge, 'rgb(255, 255, 255)', msg);
    assert.ok(shapes.photo.edgeW >= 2, msg);
    // glyphs: ~60 % of the shape (the diamond's: of the turned square inside its box), thick strokes
    assert.ok(shapes.square.glyph / shapes.square.w >= 0.58 && shapes.circle.glyph / shapes.circle.w >= 0.58, msg);
    assert.ok(shapes.diamond.glyph / shapes.diamond.beforeW >= 0.58 && shapes.photo.glyph / shapes.photo.h >= 0.55, msg);
    for (const k of ['square', 'circle', 'diamond', 'photo']) assert.ok(shapes[k].ink >= 2.4, `${k}: ${msg}`);
    // the key: every badge ≥ 18 px, on the neutral fill
    await page.locator('.hd-key summary').click();
    const key = await page.evaluate(() => [...document.querySelectorAll('.hd-key .hd-key-badge')].map(b => {
      const r = b.getBoundingClientRect(); return Math.min(r.width, r.height); }));
    assert.ok(key.length >= 6 && key.every(w => w >= 18), JSON.stringify(key));
    // the bubbles: 1, 2 and 3 digits; every circle about the disc's own centre, the disc exactly the bubble's box
    const bubbles = await page.evaluate(() => [...document.querySelectorAll('.hd-marker .hd-cluster')].map(b => {
      const box = b.getBoundingClientRect();
      const cx = box.left + box.width / 2, cy = box.top + box.height / 2;
      const cs = [...b.querySelectorAll('.hd-bubble circle')].map(c => { const r = c.getBoundingClientRect();
        return { dx: r.left + r.width / 2 - cx, dy: r.top + r.height / 2 - cy, w: r.width }; });
      const t = b.querySelector('.hd-dot-count').getBoundingClientRect();
      return { n: b.textContent, d: box.width, cs, count: { dx: t.left + t.width / 2 - cx, dy: t.top + t.height / 2 - cy },
        // the bubble's svg stays under its count
        onTop: Number(getComputedStyle(b.querySelector('.hd-dot-count')).zIndex) > Number(getComputedStyle(b.querySelector('.hd-bubble')).zIndex) };
    }));
    assert.deepEqual(bubbles.map(b => b.n.length).sort(), [1, 2, 3], JSON.stringify(bubbles));
    for (const b of bubbles) {
      assert.equal(b.cs.length, 4, JSON.stringify(b));
      for (const c of b.cs) assert.ok(Math.abs(c.dx) < 0.01 && Math.abs(c.dy) < 0.01, `concentric: ${JSON.stringify(b)}`);
      assert.ok(Math.abs(b.cs[0].w - b.d) < 0.01, `the disc is the box: ${JSON.stringify(b)}`);
      assert.ok(b.cs[3].w > b.cs[2].w && b.cs[2].w > b.cs[1].w && b.cs[1].w > b.cs[0].w, JSON.stringify(b));
      assert.ok(Math.abs(b.count.dx) < 1.5 && Math.abs(b.count.dy) < 1.5, `the count is centred: ${JSON.stringify(b)}`);
      assert.ok(b.onTop, `the count is drawn over the disc: ${JSON.stringify(b)}`);
    }
  });
});

// #1946: a finding that states no confidence is drawn in its own neutral look (grey, a dashed edge),
// never as high, and the legend keys it; its popup says so. Fixed feed; nothing is written.
test('#1946: a finding with no confidence is drawn unstated, not high, and the legend keys it', async () => {
  const at = (citekey, lon, confidence) => ({ citekey, title: `Zz ${citekey}`, claim: `claim ${citekey}`, regionName: null,
    lon, lat: 37, periodStart: -1300, periodEnd: -1200, confidence, sourceKind: 'paper' });
  const findings = [at('zz_none2026', 25, null), at('zz_high2026', 35, 'high')];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37, 30], 6, { animate: false }));
    await page.waitForFunction(() => document.querySelectorAll('.hd-find').length === 2);
    const marks = await page.evaluate(() => [...document.querySelectorAll('.hd-find')].map(el => ({
      unstated: el.classList.contains('hd-unstated'),
      fill: el.style.getPropertyValue('--c'),
      edge: getComputedStyle(el, '::before').borderTopStyle })));
    const none = marks.find(m => m.unstated), high = marks.find(m => !m.unstated);
    assert.ok(none && high, `one unstated, one stated: ${JSON.stringify(marks)}`);
    assert.equal(none.edge, 'dashed');
    assert.notEqual(high.edge, 'dashed');
    const [r, g, b] = none.fill.match(/\d+/g).map(Number);
    assert.ok(r === g && g === b, `grey, not the high colour: ${none.fill} vs ${high.fill}`);
    assert.notEqual(none.fill, high.fill);
    // the legend: a pip of its own, defined in the stylesheet
    const pip = await page.evaluate(() => {
      const el = document.querySelector('.hd-legend-confidence i.is-unstated');
      return el && { title: el.title, bg: getComputedStyle(el).backgroundColor, edge: getComputedStyle(el).borderTopStyle };
    });
    assert.ok(pip, 'the legend has an unstated pip');
    assert.equal(pip.title, 'not stated');
    assert.equal(pip.bg, 'rgb(111, 111, 111)');
    assert.equal(pip.edge, 'dashed');
    // its popup says the confidence is not stated
    await page.evaluate(() => {
      const el = document.querySelector('.hd-find.hd-unstated').closest('.hd-marker');
      el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await page.waitForSelector('.hd-popup-content');
    assert.match(await page.locator('.hd-popup-content').innerText(), /confidence not stated/);
  });
});

// Process managers, phase 2: the goals window's New research area form and the article window's
// Review / Extend / Enrich buttons, against fixed answers (nothing is written): the form refuses an
// existing slug, creates the goal at the slug made from the title, starts the planner and creates the
// seeding manager (paused) when ticked (watching: the phase 3 test below). The buttons post the
// kind to /api/agents/enqueue and say what was queued.
test('managers phase 2: the New research area form and the Review / Extend / Enrich buttons', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true } });
    });
    const asked = [];
    await page.route('**/api/goals/zz-pw-tin-trade', route => {
      const r = route.request();
      asked.push(`${r.method()} goal`);
      if (r.method() === 'GET') return route.fulfill({ status: 404, json: { message: 'No goal', code: 'not-found' } });
      asked.push(JSON.parse(r.postData()));
      return route.fulfill({ json: { slug: 'zz-pw-tin-trade', title: 'Zz PW tin trade', status: 'planned', tags: ['tin'] } });
    });
    await page.route('**/api/goals/zz-pw-exists', route =>
      route.fulfill({ json: { slug: 'zz-pw-exists', title: 'Exists', status: 'planned' } }));
    await page.route('**/api/agents/runs', route => {
      asked.push(['run', JSON.parse(route.request().postData())]);
      return route.fulfill({ json: { id: 'zz-run', pipeline: 'plan-goal', target: 'zz-pw-tin-trade', status: 'queued' } });
    });
    await page.route('**/api/agents/managers', route => {
      if (route.request().method() !== 'POST') return route.continue();
      asked.push(['manager', JSON.parse(route.request().postData())]);
      return route.fulfill({ json: { id: 'seed:zz-pw-tin-trade', kind: 'seed', goal: 'zz-pw-tin-trade', state: 'paused' } });
    });
    await page.route('**/api/goals/zz-pw-tin-trade/view', route => route.fulfill({ status: 404, json: { message: 'fixture' } }));
    await page.goto(new URL('/#open=goals', baseURL).href);
    await page.reload();
    const pane = page.locator('.goals-pane');
    await pane.locator('.goal-new > summary').click();
    const form = pane.locator('.goal-new-form');
    // an existing slug is refused, never replaced
    await form.locator('input[aria-label="title"]').fill('Exists');
    await form.locator('input[aria-label="slug"]').fill('zz-pw-exists');
    await form.locator('button', { hasText: 'create' }).click();
    await form.locator('.form-error').waitFor();
    assert.match(await form.locator('.form-error').innerText(), /exists already/);
    assert.ok(!asked.some(a => a === 'PUT goal'));
    // a new one: slug from the title, the goal, the planner, the seeding manager
    await form.locator('input[aria-label="slug"]').fill('');
    await form.locator('input[aria-label="title"]').fill('Zz PW tin trade');
    await form.locator('input[aria-label="tags"]').fill('tin, trade');
    await form.locator('button', { hasText: 'create' }).click();
    await page.waitForFunction(() => document.querySelector('.goal-new-form .form-ok'));
    assert.match(await form.locator('.form-ok').innerText(), /zz-pw-tin-trade created.*planner is running.*seed:zz-pw-tin-trade created paused/);
    assert.deepEqual(asked.filter(a => typeof a === 'string'), ['GET goal', 'PUT goal']);
    assert.deepEqual(asked.find(a => a.title).tags, ['tin', 'trade']);
    const run = asked.find(a => a[0] === 'run')[1];
    assert.deepEqual([run.pipeline, run.target], ['plan-goal', 'zz-pw-tin-trade']);
    assert.deepEqual(asked.find(a => a[0] === 'manager')[1].goal, 'zz-pw-tin-trade');
    assert.equal(asked.find(a => a[0] === 'manager')[1].kind, 'seed');

    // the article window's buttons
    const enq = [];
    await page.route('**/api/agents/enqueue', route => {
      const body = JSON.parse(route.request().postData());
      enq.push(body);
      const item = (id, kind, target) => ({ id, managerId: body.kind === 'enrich' ? 'enrich' : 'review', kind, target, status: 'pending' });
      return route.fulfill({ json: body.kind === 'enrich'
        ? { managerId: 'enrich', items: [item(1, 'source-enrich', 'a'), item(2, 'source-enrich', 'b')], skipped: ['[[c]]: notes'] }
        : { managerId: 'review', items: [item(7, body.kind === 'extend' ? 'article-extend' : 'article-review', body.target)] } });
    });
    await page.goto(new URL('/#open=article:zz_pw_article', baseURL).href);
    await page.reload();
    const actions = page.locator('.wm-actions').filter({ has: page.locator('button', { hasText: 'extend' }) });
    await actions.locator('button', { hasText: 'extend' }).click();
    await actions.locator('.wm-note').waitFor();
    assert.match(await actions.locator('.wm-note').innerText(), /queued to be extended, then reviewed \(item #7, pending\)/);
    await actions.locator('button', { hasText: 'enrich' }).click();
    await page.waitForFunction(() => /2 items queued for enrichment; 1 source enriched already/.test(
      document.querySelector('.wm-actions .wm-note')?.innerText || ''));
    await actions.locator('button', { hasText: 'review' }).click();
    await page.waitForFunction(() => /queued for review/.test(document.querySelector('.wm-actions .wm-note')?.innerText || ''));
    assert.deepEqual(enq.map(e => [e.kind, e.target, e.priority]),
      [['extend', 'zz_pw_article', 10], ['enrich', 'zz_pw_article', 10], ['review', 'zz_pw_article', 10]]);
  });
});

// Process managers, phase 3, against fixed answers (nothing is written): the New research area form's
// watch box creates a paused watcher whose auto-ingest of legal open-access finds is OFF unless ticked,
// with its daily cap; the managers window shows each manager's spend today against its daily cap; a
// watcher's window shows its cron and its sweeps (what each found and ingested).
test('managers phase 3: the watch box, the daily spend, a watcher\'s sweeps', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true } });
    });
    const made = [];
    await page.route('**/api/goals/zz-pw-watch', route => {
      if (route.request().method() === 'GET') return route.fulfill({ status: 404, json: { message: 'No goal', code: 'not-found' } });
      return route.fulfill({ json: { slug: 'zz-pw-watch', title: 'Zz PW watch', status: 'planned' } });
    });
    await page.route('**/api/goals/zz-pw-watch/view', route => route.fulfill({ status: 404, json: { message: 'fixture' } }));
    const watcher = {
      id: 'watch:zz-pw-watch', kind: 'watch', goal: 'zz-pw-watch', state: 'paused', cron: '@weekly',
      nextTickAt: '2026-10-25T00:00:00Z', statusNote: 'paused', counts: { pending: 2 }, inFlight: 0,
      config: { autoIngest: true, autoIngestPerDay: 3 },
      effective: { autoIngest: true, autoIngestPerDay: 3, dailyUsd: 2, sweeps: ['acquisition', 'currency'] },
      spentTodayUsd: 0.5, tokensToday: 12345 };
    await page.route('**/api/agents/managers', route => {
      if (route.request().method() === 'POST') {
        made.push(JSON.parse(route.request().postData()));
        return route.fulfill({ json: watcher });
      }
      return route.fulfill({ json: [watcher] });
    });
    await page.goto(new URL('/#open=goals', baseURL).href);
    await page.reload();
    const pane = page.locator('.goals-pane');
    await pane.locator('.goal-new > summary').click();
    const form = pane.locator('.goal-new-form');
    await form.locator('input[aria-label="title"]').fill('Zz PW watch');
    // plan and seed off: only the goal and the watcher
    for (const t of ['plan (sub-goals, queries, works)', 'seed (propose and compile its articles)'])
      await form.locator('label', { hasText: t }).locator('input').uncheck();
    await form.locator('label', { hasText: /^ ?watch$/ }).locator('input').check();
    const cap = form.locator('input[aria-label="ingests a day at most"]');
    assert.equal(await cap.isDisabled(), true, 'no cap while auto-ingest is off');
    await form.locator('label', { hasText: 'ingest legal open-access finds' }).locator('input').check();
    await cap.fill('3');
    await form.locator('button', { hasText: 'create' }).click();
    await page.waitForFunction(() => document.querySelector('.goal-new-form .form-ok'));
    assert.match(await form.locator('.form-ok').innerText(), /watcher watch:zz-pw-watch created paused.*ingests legal open-access finds/);
    assert.deepEqual(made.map(m => [m.kind, m.goal, m.config.autoIngest, m.config.autoIngestPerDay]),
      [['watch', 'zz-pw-watch', true, 3]]);

    // the managers window: the day's spend against the cap
    await page.route(/\/api\/agents\/managers\/watch(%3A|:)zz-pw-watch$/, route => route.fulfill({ json: {
      manager: watcher, current: [], hints: [],
      events: [
        { id: 3, managerId: watcher.id, at: '2026-10-18T00:05:00Z', kind: 'swept', text: 'acquisition-sweep goal zz-pw-watch: 4 picked, 1 ingested' },
        { id: 2, managerId: watcher.id, at: '2026-10-18T00:04:00Z', kind: 'ingested', text: '[[zz2023_tin]] ingested (legal open access)' },
        { id: 1, managerId: watcher.id, at: '2026-10-18T00:00:00Z', kind: 'started', text: 'run 1' }] } }));
    await page.route(/\/api\/agents\/managers\/watch(%3A|:)zz-pw-watch\/items/, route =>
      route.fulfill({ json: { items: [], total: 0, offset: 0, limit: 25 } }));
    await page.goto(new URL('/#open=managers', baseURL).href);
    await page.reload();
    const row = page.locator('.manager-row').filter({ hasText: 'watch:zz-pw-watch' });
    await row.waitFor();
    assert.match(await row.innerText(), /today 0\.50 of 2\.00 USD, 12\.3k tokens/);
    await row.locator('button', { hasText: 'watch:zz-pw-watch' }).click();
    const sweeps = page.locator('.manager-sweeps');
    await sweeps.waitFor();
    const text = await sweeps.innerText();
    assert.match(text, /Cron `@weekly` \(UTC\), next 2026-10-25T00:00:00Z — fires once it is resumed/);
    assert.match(text, /at most 3 a day \(legal open access only\)/);
    assert.match(text, /swept acquisition-sweep goal zz-pw-watch: 4 picked, 1 ingested/);
    assert.match(text, /ingested \[\[zz2023_tin\]\] ingested/);
    assert.doesNotMatch(text, /run 1/);
  });
});

// The autopilot, against fixed answers (nothing is written): the managers window shows the switch and every
// goal's stage, counts and pace; turning it on shows the new state at once and then what the server did.
test('autopilot: the switch and the goals\' stages in the managers window', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true } });
    });
    const goals = [
      { slug: 'zz-pw-ap', title: 'Zz PW autopilot', status: 'seeding', managers: [{ id: 'seed:zz-pw-ap', kind: 'seed', state: 'active' }],
        sources: 4, articles: 1, openApprovals: 1, doneLast7: 7, open: 2, etaDays: 2 },
      { slug: 'zz-pw-ap2', title: 'Zz PW planned', status: 'planned', managers: [], sources: 0, articles: 0, openApprovals: 0,
        doneLast7: 0, open: 0 }];
    const off = { on: false, agentsEnabled: true, managersEnabled: true, approvals: [{ kind: 'article-plan', mode: 'person' }],
      ingestedToday: 0, ingestCap: 0, maxConcurrentRuns: 4, library: [], goals, changes: [] };
    const posted = [];
    await page.route('**/api/agents/managers', route => route.fulfill({ json: [] }));
    await page.route('**/api/agents/autopilot', async route => {
      if (route.request().method() === 'POST') {
        posted.push(JSON.parse(route.request().postData()));
        await new Promise(r => setTimeout(r, 300));
        return route.fulfill({ json: { ...off, on: true, since: '2026-10-19T00:00:00Z', by: 'web', ingestCap: 20,
          approvals: [{ kind: 'article-plan', mode: 'checked' }], changes: ['turned on by web', 'seed:zz-pw-ap2 created'] } });
      }
      return route.fulfill({ json: off });
    });
    await page.goto(new URL('/#open=managers', baseURL).href);
    await page.reload();
    const ap = page.locator('.autopilot');
    await ap.locator('.autopilot-goals li').first().waitFor();
    assert.match(await ap.innerText(), /Autopilot off/);
    assert.match(await ap.innerText(), /1 planned · 1 seeding/);
    const row = ap.locator('.autopilot-goals li').filter({ hasText: 'zz-pw-ap' }).first();
    assert.match(await row.innerText(), /4 sources · 1 article · 1 open approval · 7 done in 7 days, 2 open \(~2 d\)/);
    await ap.locator('button', { hasText: 'turn on' }).click();
    // the new state shows before the server answers (the answer takes 300 ms here)
    await page.waitForFunction(() => document.querySelector('.autopilot-state.is-on'), null, { timeout: 250 });
    await page.waitForFunction(() => /seed:zz-pw-ap2 created/.test(document.querySelector('.autopilot').innerText));
    assert.deepEqual(posted, [{ on: true, by: 'web' }]);
    assert.match(await ap.innerText(), /article-plan checked/);
    assert.match(await ap.innerText(), /ingests today 0 of 20/);
  });
});

// The autopilot's focus, against fixed answers (nothing is written): the managers window shows the active
// goals with why each was chosen, what it waits for and its pace, the queue in order, and why the others cannot
// use a slot; pinning a queued goal posts the request and shows the server's answer.
test('autopilot focus: the active goals, the queue and a pin in the managers window', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/config', async route => {
      const res = await route.fetch();
      return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true, writeKeyRequired: false } });
    });
    const focus = { on: true, max: 2, maxFrom: 'config', held: 6, queued: 3, changes: [],
      active: [
        { slug: 'zz-pw-f1', title: 'Zz PW one', status: 'compiled', stage: 'reviewing', why: 'reviewing: 1 of 2 articles still to pass review',
          pinned: false, priority: 0, doing: 'running review-article zz-pw-art', open: 2, doneLast7: 7, etaDays: 2 },
        { slug: 'zz-pw-f2', title: 'Zz PW two', status: 'seeding', stage: 'ready', why: 'ready: 5 sources (at least 3), no article yet',
          pinned: false, priority: 0, doing: 'next propose-articles zz-pw-f2', open: 1, doneLast7: 0 }],
      queue: [{ slug: 'zz-pw-q1', title: '', status: 'seeding', stage: 'gathering', why: 'gathering: 1 source; in the same tree as zz-pw-f2',
        pinned: false, priority: 0, open: 2, doneLast7: 0 }],
      waiting: [{ slug: 'zz-pw-w1', code: 'person', why: 'waits for a person: approval #9' },
        { slug: 'zz-pw-w2', code: 'person', why: 'waits for a person: approval #10' }, { slug: 'zz-pw-w3', code: 'idle', why: 'idle' }] };
    const status = { on: true, agentsEnabled: true, managersEnabled: true, approvals: [], ingestedToday: 3, ingestCap: 60,
      maxConcurrentRuns: 4, library: [], goals: [], changes: [], focus };
    const posted = [];
    await page.route('**/api/agents/managers', route => route.fulfill({ json: [] }));
    await page.route('**/api/agents/autopilot', route => route.fulfill({ json: status }));
    await page.route('**/api/agents/autopilot/focus', route => {
      posted.push(JSON.parse(route.request().postData()));
      return route.fulfill({ json: { ...focus, changes: ['zz-pw-q1 pinned: it is active from the next tick (by web)'],
        active: [{ ...focus.queue[0], pinned: true, why: 'pinned by a person; gathering: 1 source' }, focus.active[0]] } });
    });
    await page.goto(new URL('/#open=managers', baseURL).href);
    await page.reload();
    const panel = page.locator('.autopilot-focus');
    await panel.locator('.focus-active li').first().waitFor();
    const text = await panel.innerText();
    assert.match(text, /Focus: at most 2 active goals · 6 items held/);
    assert.match(text, /reviewing: 1 of 2 articles still to pass review/);
    assert.match(text, /now: running review-article zz-pw-art/);
    assert.match(text, /7 done in 7 days, 2 open \(~2 d\)/);
    assert.match(text, /next \(3 waiting for a slot\):/);
    assert.match(text, /in the same tree as zz-pw-f2/);
    assert.match(text, /cannot use a slot now: 2 person · 1 idle/);
    const pin = panel.locator('.focus-queue li').filter({ hasText: 'zz-pw-q1' }).locator('button', { hasText: 'pin' });
    await pin.click();
    await page.waitForFunction(() => /zz-pw-q1 pinned/.test(document.querySelector('.autopilot-focus').innerText));
    assert.deepEqual(posted, [{ action: 'pin', goal: 'zz-pw-q1', value: null, by: 'web' }]);
    assert.match(await page.locator('.autopilot-focus').innerText(), /pinned by a person/);
  });
});

/** The base map now: its menu's value, the notice, the credits, how often a base map was drawn whole. */
const baseMapState = () => ({
  value: document.querySelector('.map-base-select')?.value,
  options: [...document.querySelectorAll('.map-base-select option')].map(o => o.value),
  notice: document.querySelector('.terrain-notice')?.textContent ?? null,
  credits: document.querySelector('.map-foot .map-credits')?.textContent ?? '',
  drawn: window.__testMap.baseMapsDrawn(),
  vector: document.getElementById('map').classList.contains('basemap-vector'),
});

/** Choose a base map in the menu and wait until it is drawn whole. */
async function chooseBaseMap(page, value) {
  const before = await page.evaluate(() => window.__testMap.baseMapsDrawn());
  await page.locator('.map-base-select').selectOption(value);
  await page.waitForFunction(n => window.__testMap.baseMapsDrawn() > n, before, { timeout: 20000 });
}

/** How many distinct colours a screenshot of the map holds (a drawn map has many; an empty one one). */
async function mapColours(page) {
  const png = await page.locator('#map').screenshot();
  return page.evaluate(async b64 => {
    const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
    const g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data, seen = new Set();
    for (let i = 0; i < d.length; i += 4 * 97) seen.add((d[i] >> 3) << 10 | (d[i + 1] >> 3) << 5 | (d[i + 2] >> 3));
    return seen.size;
  }, png.toString('base64'));
}

test('the base map: Readable and Physical drawn from the server\'s extract (the notice, their credits), and the online maps', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    assert.deepEqual((await page.evaluate(baseMapState)).options, ['readable', 'physical', 'standard', 'muted', 'dark']);
    // an online map: the options hold the map's motion alone (#4718), the vector styles' own options hidden
    await page.locator('.map-options-btn').click();
    await page.locator('.map-motion-select').waitFor();
    assert.ok(!(await page.locator('.map-option-names').isVisible()), 'no vector options on an online map');
    assert.ok(await page.locator('.map-options-online').isVisible());
    await page.keyboard.press('Escape');
    await page.evaluate(() => window.__testMap.setView([37.9, 25.4], 8));
    await chooseBaseMap(page, 'readable');
    let st = await page.evaluate(baseMapState);
    assert.ok(st.vector);
    assert.match(st.notice, /Coastlines, rivers, lakes and vegetation were different in the past/);
    assert.match(await page.locator('.terrain-notice a').getAttribute('href'), /exploring\/web-ui\.md#the-base-map$/);
    for (const c of [/OpenStreetMap/, /Natural Earth/, /Protomaps/, /Mapterhorn/]) assert.match(st.credits, c);
    // #3759: the credits sit in the notice's box, bottom left — no ⓘ button, and the legend at the map's foot
    assert.equal(await page.locator('.maplibregl-ctrl-attrib').count(), 0, 'no corner credits control');
    assert.equal(await page.locator('.map-foot .terrain-notice + .map-credits').count(), 1, 'the credits under the notice');
    const foot = await page.evaluate(() => {
      const m = document.getElementById('map').getBoundingClientRect(), l = document.querySelector('.hd-legend').getBoundingClientRect();
      const f = document.querySelector('.map-foot').getBoundingClientRect();
      return { legendGap: Math.round(m.bottom - l.bottom), footLeft: f.left - m.left < m.width / 2 };
    });
    assert.ok(foot.legendGap <= 12, `the legend sits at the map's foot (${foot.legendGap} px above it)`);
    assert.ok(foot.footLeft, 'the notice and credits are bottom left');
    assert.ok(await mapColours(page) > 20, 'Readable is drawn');
    await chooseBaseMap(page, 'physical');
    st = await page.evaluate(baseMapState);
    assert.match(st.credits, /ETOPO1/, 'the sea floor is credited where it is drawn');
    assert.ok(await mapColours(page) > 20, 'Physical is drawn');
    // kept like every choice
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.locator('.terrain-notice').waitFor();
    assert.equal(await page.locator('.map-base-select').inputValue(), 'physical');
    // an online map: no notice, its own credit, no options
    await page.locator('.map-base-select').selectOption('dark');
    await page.waitForFunction(() => !document.querySelector('.terrain-notice') && !document.getElementById('map').classList.contains('basemap-vector'));
    assert.ok(await page.evaluate(() => document.getElementById('map').classList.contains('map-tone-dark')));
    await page.locator('.map-options-btn').click();
    assert.ok(!(await page.locator('.map-option-names').isVisible()) && await page.locator('.map-motion-select').isVisible());
    await page.keyboard.press('Escape');
    await page.locator('.map-base-select').selectOption('standard');
    // an online map's credits: the box holds them alone
    await page.waitForFunction(() => /OpenStreetMap/.test(document.querySelector('.map-foot .map-credits')?.textContent ?? '') &&
      !document.querySelector('.terrain-notice'));
  }, { source: 'online' });
});

/** A style's fill opacity for a feature of `kind` at MapLibre zoom `z`: the two forms the styles use — a number,
  * or `interpolate linear zoom` over stops that are numbers or `match get kind`. */
function fillOpacity(layer, kind, z) {
  const value = v => {
    if (typeof v === 'number') return v;
    if (Array.isArray(v) && v[0] === 'match') {
      for (let i = 2; i + 1 < v.length; i += 2) if ([].concat(v[i]).includes(kind)) return v[i + 1];
      return v[v.length - 1];
    }
    throw new Error(`an opacity form this check does not read: ${JSON.stringify(v)}`);
  };
  const o = layer.paint?.['fill-opacity'] ?? 1;
  if (!Array.isArray(o) || o[0] !== 'interpolate') return value(o);
  const stops = o.slice(3);
  if (z <= stops[0]) return value(stops[1]);
  for (let i = 0; i + 2 < stops.length; i += 2)
    if (z <= stops[i + 2]) { const t = (z - stops[i]) / (stops[i + 2] - stops[i]); return value(stops[i + 1]) + t * (value(stops[i + 3]) - value(stops[i + 1])); }
  return value(stops[stops.length - 1]);
}

test('land cover is drawn at every zoom, in both base maps: the satellite classes overzoomed under OSM\'s land use', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  // the layers that draw land cover in each style, and the kinds that are vegetation, rock, sand, wetland or ice
  const cover = {
    readable: { layers: ['landcover', 'landuse'], kinds: new Set(['forest', 'wood', 'grassland', 'meadow', 'grass', 'scrub', 'heath', 'farmland',
      'orchard', 'vineyard', 'barren', 'bare_rock', 'scree', 'sand', 'beach', 'wetland', 'glacier']) },
    physical: { layers: ['forest-lc', 'forest', 'sand', 'green-lc', 'glacier', 'landuse-veil'], kinds: null },
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    for (const style of ['readable', 'physical']) {
      const doc = await (await fetch(new URL(`/web/styles/${style}.json`, baseURL))).json();
      const byId = Object.fromEntries(doc.layers.map(l => [l.id, l]));
      await chooseBaseMap(page, style);
      // the Argolid around Mycenae: forest, scrub, farmland and bare rock, at zooms 7, 9, 11 and 13 (the app's)
      for (const zoom of [7, 9, 11, 13]) {
        await page.evaluate(z => window.__testMap.setView([37.73, 22.756], z), zoom);
        await page.waitForFunction(() => window.__testMap.isDrawn(), null, { timeout: 20000 });
        const samples = await page.evaluate(() => {
          const m = window.__testMap, b = m.getBounds(), out = [];
          for (let i = 1; i < 12; i++) for (let j = 1; j < 8; j++) {
            const at = [b.south + (b.north - b.south) * j / 8, b.west + (b.east - b.west) * i / 12];
            out.push(m.areasAt(at));
          }
          return out;
        });
        const land = samples.filter(a => a.some(x => x.startsWith('earth:')));
        const covered = land.filter(a => a.some(x => {
          const [id, kind] = x.split(':');
          return cover[style].layers.includes(id) && (!cover[style].kinds || cover[style].kinds.has(kind))
            && fillOpacity(byId[id], kind, zoom - 1) >= 0.05;
        }));
        assert.ok(land.length >= 10, `${style} z${zoom}: the view is mostly land (${land.length} of ${samples.length} points)`);
        assert.ok(covered.length >= land.length * 0.3,
          `${style} z${zoom}: land cover drawn under ${covered.length} of ${land.length} land points (${JSON.stringify(land.slice(0, 6))})`);
      }
    }
  }, { source: 'local' });
});

/** WCAG contrast of two sRGB colours. */
function contrast(a, b) {
  const lin = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
  const L = ([r, g, bl]) => 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(bl);
  const x = L(a), y = L(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
const rgba = c => c.startsWith('#') ? [1, 3, 5].map(i => parseInt(c.slice(i, i + 2), 16)).concat(1)
  : (m => [m[0], m[1], m[2], m[3] ?? 1])(c.match(/[\d.]+/g).map(Number));

test('the base maps\' names read at 4.5:1 against what lies under them, through their halo, in both styles', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    for (const style of ['readable', 'physical']) {
      const doc = await (await fetch(new URL(`/web/styles/${style}.json`, baseURL))).json();
      const paint = Object.fromEntries(doc.layers.filter(l => l.type === 'symbol' && typeof l.paint?.['text-color'] === 'string')
        .map(l => [l.id, { text: rgba(l.paint['text-color']), halo: rgba(l.paint['text-halo-color'] ?? 'rgba(0,0,0,0)') }]));
      await chooseBaseMap(page, style);
      for (const [at, zoom] of [[[38.2, 25.0], 7], [[37.8, 25.2], 8], [[37.6, 24.6], 9]]) {
        await page.evaluate(([a, z]) => window.__testMap.setView(a, z), [at, zoom]);
        await page.waitForFunction(() => window.__testMap.isDrawn(), null, { timeout: 20000 });
        const labels = await page.evaluate(() => window.__testMap.labels());
        assert.ok(labels.length >= 3, `${style} z${zoom}: names are placed (${labels.length})`);
        // the background: the same view drawn without names
        let n = await page.evaluate(() => window.__testMap.baseMapsDrawn());
        await page.locator('.map-options-btn').click();
        await page.locator('.map-option-names').uncheck();
        await page.waitForFunction(k => window.__testMap.baseMapsDrawn() > k && window.__testMap.isDrawn(), n, { timeout: 20000 });
        await page.locator('.map-options-close').click();
        const box = await page.locator('#map').boundingBox();
        const png = (await page.locator('#map').screenshot()).toString('base64');
        const under = await page.evaluate(async ([b64, labels, w, h]) => {
          const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
          const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
          const g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(img, 0, 0);
          const sx = img.width / w, sy = img.height / h;
          return labels.map(l => {
            const tw = Math.min(260, Math.max(24, l.text.length * 6.5)), th = 14;
            const x0 = Math.round((l.x - tw / 2) * sx), y0 = Math.round((l.y - th / 2) * sy);
            if (x0 < 0 || y0 < 0 || x0 + tw * sx > img.width || y0 + th * sy > img.height) return null;
            const d = g.getImageData(x0, y0, Math.round(tw * sx), Math.round(th * sy)).data, px = [];
            for (let i = 0; i < d.length; i += 12) px.push([d[i], d[i + 1], d[i + 2]]);
            return px;
          });
        }, [png, labels, box.width, box.height]);
        for (const [i, l] of labels.entries()) {
          const p = paint[l.layer];
          if (!p || !under[i]) continue;
          // each name's text against its halo laid over every pixel under it: the 10th percentile
          const [hr, hg, hb, ha] = p.halo;
          const ratios = under[i].map(([r, g, b]) => contrast(p.text, [hr * ha + r * (1 - ha), hg * ha + g * (1 - ha), hb * ha + b * (1 - ha)]))
            .sort((x, y) => x - y);
          const r10 = ratios[Math.floor(ratios.length * 0.1)];
          assert.ok(r10 >= 4.5, `${style} z${zoom}: "${l.text}" (${l.layer}) reads at ${r10.toFixed(1)}:1`);
        }
        n = await page.evaluate(() => window.__testMap.baseMapsDrawn());
        await page.locator('.map-options-btn').click();
        await page.locator('.map-option-names').check();
        await page.waitForFunction(k => window.__testMap.baseMapsDrawn() > k, n, { timeout: 20000 });
        await page.locator('.map-options-close').click();
      }
    }
  }, { source: 'local' });
});

test('the page prints no warning of its own while the map is used (WebGL probing, scrolling, the base maps)', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    const said = [];
    page.on('console', m => { if (m.type() === 'warning' || m.type() === 'error') said.push(m.text()); });
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    for (const style of ['readable', 'physical']) {
      await chooseBaseMap(page, style);
      await page.evaluate(() => window.__testMap.panBy([200, 80]));
      await page.waitForFunction(() => window.__testMap.isDrawn(), null, { timeout: 20000 });
    }
    // the side list scrolled, a card pinned while it scrolls
    await page.locator('#list').evaluate(el => { el.scrollTop = 400; });
    await page.waitForTimeout(300);
    await page.locator('#list').evaluate(el => { el.scrollTop = 0; });
    await page.waitForTimeout(300);
    // what the page itself caused: its scripts, an empty-id lookup, a scroll-linked effect, a lost context;
    // refused tiles of the online maps (the test refuses other hosts) are not the page's
    const ours = said.filter(x => /app\.bundle|getElementById|scroll-linked|WebGL|WEBGL|base map could not/i.test(x));
    assert.deepEqual(ours, []);
  }, { source: 'local' });
});

test('map options: names off and on, their language, contours only where Physical has the detailed relief; kept across a reload', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.evaluate(() => window.__testMap.setView([37.6, 25.0], 7));
    await chooseBaseMap(page, 'readable');
    await page.waitForFunction(() => window.__testMap.names().length > 3, null, { timeout: 15000 });
    assert.ok(!(await page.evaluate(() => window.__testMap.names())).some(n => /[Ͱ-Ͽ]/.test(n)), 'English by default');
    await page.locator('.map-options-btn').click();
    await page.locator('.map-options').waitFor();
    // the names in their own script: the Greek ones in Greek
    let before = await page.evaluate(() => window.__testMap.baseMapsDrawn());
    await page.locator('.map-language-select').selectOption('local');
    await page.waitForFunction(n => window.__testMap.baseMapsDrawn() > n && window.__testMap.names().some(x => /[Ͱ-Ͽ]/.test(x)), before, { timeout: 15000 });
    // names off: none placed; the panel stays open while options change
    before = await page.evaluate(() => window.__testMap.baseMapsDrawn());
    await page.locator('.map-option-names').uncheck();
    await page.waitForFunction(n => window.__testMap.baseMapsDrawn() > n && window.__testMap.names().length === 0, before, { timeout: 15000 });
    assert.ok(await page.locator('.map-options').isVisible(), 'still open');
    assert.ok(await page.locator('.map-language-select').isDisabled());
    // contours: Readable draws none
    assert.ok(await page.locator('.map-option-contours').isDisabled());
    assert.match(await page.locator('.map-option-contours-why').innerText(), /Physical draws contours/);
    // kept: across a reload; reset brings the names back in English
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.locator('.map-options-btn').click();
    assert.ok(!(await page.locator('.map-option-names').isChecked()));
    await page.locator('.map-options-reset').click();
    assert.ok(await page.locator('.map-option-names').isChecked());
    assert.equal(await page.locator('.map-language-select').inputValue(), 'en');
    // Physical over the Argolid's focus region: contours on, and a choice of the viewer's
    await page.keyboard.press('Escape');
    await page.locator('.map-options').waitFor({ state: 'hidden' });
    assert.equal(await page.evaluate(() => document.activeElement?.id), 'map-options-button');
    await page.evaluate(() => window.__testMap.setView([37.73, 22.756], 12));
    await chooseBaseMap(page, 'physical');
    await page.locator('.map-options-btn').click();
    assert.ok(status.layers.find(l => l.layer === 'terrain')?.focus?.length > 0, 'the fixture has the Argolid focus region');
    assert.ok(await page.locator('.map-option-contours').isEnabled() && await page.locator('.map-option-contours').isChecked());
    assert.equal(await page.locator('.map-option-contours-why').count(), 0, 'drawn here: no line');
    // drawn: the contour lines traced from the focus region's relief are on the map
    await page.waitForFunction(() => window.__testMap.layerDrawn('contours') > 0, null, { timeout: 20000 });
    before = await page.evaluate(() => window.__testMap.baseMapsDrawn());
    await page.locator('.map-option-contours').uncheck();
    await page.waitForFunction(n => window.__testMap.baseMapsDrawn() > n, before, { timeout: 15000 });
    assert.equal(await page.evaluate(() => window.__testMap.layerVisibility('contours')), null, 'off: the layer is gone');
  }, { source: 'online' });
});

test('contours: a line saying why none are drawn here (zoomed out, outside the detailed relief), and the on-demand relief alone enables them', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  const why = page => page.locator('.map-option-contours-why');
  await withPage({ width: 1440, height: 900 }, async page => {
    // inside the Argolid but zoomed out: enabled, none yet
    await page.evaluate(() => window.__testMap.setView([37.73, 22.756], 9));
    await chooseBaseMap(page, 'physical');
    await page.locator('.map-options-btn').click();
    assert.ok(await page.locator('.map-option-contours').isEnabled());
    assert.match(await why(page).innerText(), /zoom in to see contours \(from zoom 11\)/);
    // closer in: drawn, and the line goes
    await page.evaluate(() => window.__testMap.setView([37.73, 22.756], 11.5));
    await page.waitForFunction(() => !document.querySelector('.map-option-contours-why'), null, { timeout: 10000 });
    await page.waitForFunction(() => window.__testMap.layerDrawn('contours') > 0, null, { timeout: 20000 });
    // over Naxos, outside every focus region, the test server reading nothing on demand
    await page.evaluate(() => window.__testMap.setView([37.05, 25.45], 11.5));
    await page.waitForFunction(() => /outside the detailed relief \(argolid\) and on-demand relief is off/
      .test(document.querySelector('.map-option-contours-why')?.textContent ?? ''), null, { timeout: 10000 });
    assert.ok(await page.locator('.map-option-contours').isEnabled());
  }, { source: 'local' });
  // a server with no focus region: the on-demand relief alone enables the contours; without it they are disabled
  for (const onDemand of [true, false]) {
    await withPage({ width: 1440, height: 900 }, async page => {
      await page.route('**/api/basemap', async route => {
        const res = await route.fetch();
        const s = await res.json();
        s.layers = s.layers.map(l => l.layer === 'terrain' ? { ...l, focus: [], onDemand: { ...l.onDemand, enabled: onDemand } } : l);
        await route.fulfill({ response: res, json: s });
      });
      await page.reload();
      await page.waitForFunction(() => window.__testMap);
      await page.evaluate(() => window.__testMap.setView([37.73, 22.756], 12));
      await chooseBaseMap(page, 'physical');
      await page.locator('.map-options-btn').click();
      if (onDemand) {
        assert.ok(await page.locator('.map-option-contours').isEnabled() && await page.locator('.map-option-contours').isChecked());
        assert.equal(await why(page).count(), 0);
        await page.waitForFunction(() => window.__testMap.layerVisibility('contours') === 'visible', null, { timeout: 15000 });
      } else {
        assert.ok(await page.locator('.map-option-contours').isDisabled());
        assert.match(await why(page).innerText(), /no focus region downloaded and on-demand relief is off — `histmersion basemap fetch --layer terrain --focus <region>`/);
        assert.equal(await page.evaluate(() => window.__testMap.layerVisibility('contours')), null);
      }
    }, { source: 'local' });
  }
});

test('the sea\'s depth: an option of both styles, on in Physical and off in Readable by default, changing the drawn style; kept across a reload', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available || !status.layers.find(l => l.layer === 'bathymetry')?.present) {
    t.skip('the test server has no sea floor (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return;
  }
  const depth = page => page.locator('.map-option-depth');
  const seaDepth = page => page.evaluate(() => window.__testMap.layerVisibility('sea-depth'));
  const toggle = async (page, on) => {
    const before = await page.evaluate(() => window.__testMap.baseMapsDrawn());
    if (on) await depth(page).check(); else await depth(page).uncheck();
    await page.waitForFunction(n => window.__testMap.baseMapsDrawn() > n, before, { timeout: 15000 });
  };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.evaluate(() => window.__testMap.setView([37.6, 25.0], 7));
    // Physical: on by default, the depth layer drawn; off and on again
    await chooseBaseMap(page, 'physical');
    await page.locator('.map-options-btn').click();
    assert.ok(await depth(page).isEnabled() && await depth(page).isChecked());
    assert.equal(await seaDepth(page), 'visible');
    await toggle(page, false);
    assert.equal(await seaDepth(page), 'none');
    assert.ok(await page.locator('.map-options').isVisible(), 'the panel stays open');
    await toggle(page, true);
    assert.equal(await seaDepth(page), 'visible');
    // Readable: off by default (Carto's flat water), on when asked
    await page.keyboard.press('Escape');
    await chooseBaseMap(page, 'readable');
    await page.locator('.map-options-btn').click();
    assert.ok(await depth(page).isEnabled() && !(await depth(page).isChecked()));
    assert.equal(await seaDepth(page), 'none');
    const flat = await mapColours(page);
    await toggle(page, true);
    assert.equal(await seaDepth(page), 'visible');
    assert.ok(await mapColours(page) > flat, 'the depth adds its shades of blue');
    // kept per style across a reload: Readable's choice, Physical's default untouched
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.locator('.terrain-notice').waitFor();
    assert.equal(await page.locator('.map-base-select').inputValue(), 'readable');
    await page.waitForFunction(() => window.__testMap.layerVisibility('sea-depth') === 'visible', null, { timeout: 15000 });
    await page.locator('.map-options-btn').click();
    assert.ok(await depth(page).isChecked());
    await page.locator('.map-options-reset').click();
    await page.waitForFunction(() => window.__testMap.layerVisibility('sea-depth') === 'none', null, { timeout: 15000 });
  }, { source: 'local' });
});

test('map options: an option whose data is not downloaded is disabled, its line saying what to fetch', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    // the server as if it had no relief and no sea floor
    await page.route('**/api/basemap', async route => {
      const res = await route.fetch();
      const s = await res.json();
      s.layers = s.layers.map(l => (l.layer === 'bathymetry' || l.layer === 'terrain') ? { ...l, present: false } : l);
      await route.fulfill({ response: res, json: s });
    });
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await chooseBaseMap(page, 'physical');
    await page.locator('.map-options-btn').click();
    assert.ok(await page.locator('.map-option-contours').isDisabled());
    assert.match(await page.locator('.map-option-contours-why').innerText(), /no relief downloaded — `histmersion basemap fetch --layer terrain`/);
    assert.ok(await page.locator('.map-option-depth').isDisabled());
    assert.match(await page.locator('.map-option-depth-why').innerText(), /no sea floor downloaded — `histmersion basemap fetch --layer bathymetry`/);
    assert.ok(await page.locator('.map-option-names').isEnabled(), 'the names need no relief');
    assert.ok(await mapColours(page) > 10, 'drawn without the relief');
  }, { source: 'online' });
});

test('the names are placed across the whole view: a wide window, after pans, still places the seas and ranges whole', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract'); return; }
  await withPage({ width: 2560, height: 1440 }, async page => {
    await page.evaluate(() => window.__testMap.setView([37.8, 25.3], 8));
    await chooseBaseMap(page, 'readable');
    const box = await page.locator('#map').boundingBox();
    for (const dx of [300, -500, 200]) {
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await page.mouse.down();
      await page.mouse.move(box.x + box.width / 2 + dx, box.y + box.height / 2, { steps: 10 });
      await page.mouse.up();
      await page.waitForTimeout(400);
    }
    await page.waitForFunction(() => !window.__testMap.isMoving());
    await page.waitForTimeout(800);
    const names = await page.evaluate(() => window.__testMap.names());
    assert.ok(names.length > 5, names.join(', '));
    // the regression of the canvas map (names cut at tile edges, "REECE") cannot happen: the map places
    // each name once for the whole view; here: the names placed are the whole names of their features
    assert.ok(names.every(n => n.trim().length > 1), names.join(', '));
  }, { source: 'online' });
});

test('offline: with the local maps only, the page asks no other host — the two styles from the server alone', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  // the server's own answer: an extract that draws a vector style whole makes `auto` local
  const config = await (await fetch(new URL('/api/config', baseURL))).json();
  if (status.styles.some(s => s.missing.length === 0)) assert.equal(config.baseMapSource, 'local');
  const outside = [];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.waitForFunction(() => document.querySelectorAll('.map-base-select option').length > 0);
    let st = await page.evaluate(baseMapState);
    assert.deepEqual(st.options, ['readable', 'physical'], 'no online map');
    assert.equal(st.value, status.styles.find(s => s.id === 'readable')?.missing.length === 0 ? 'readable' : 'physical');
    await page.evaluate(() => window.__testMap.setView([37.95, 23.7], 9));
    await page.waitForFunction(() => window.__testMap.names().length > 0, null, { timeout: 15000 });
    for (const id of ['physical', 'readable']) await chooseBaseMap(page, id);
    await page.evaluate(() => { window.__testMap.panBy([300, 120]); window.__testMap.setZoom(12, { animate: false }); });
    await page.waitForTimeout(1500);
    // an online choice kept by this browser is drawn as a local style
    await page.evaluate(() => localStorage.setItem('histmersion.ui.baseMap', '["muted"]'));
    await page.reload();
    await page.waitForFunction(() => window.__testMap && window.__testMap.baseMapsDrawn() > 0, null, { timeout: 20000 });
    st = await page.evaluate(baseMapState);
    assert.ok(['readable', 'physical'].includes(st.value) && st.vector, JSON.stringify(st));
    await page.waitForTimeout(1000);
    assert.deepEqual(outside, [], 'requests to other hosts');
  }, { source: 'local', outside });
});

test('base-map choices: a style the download cannot draw whole is marked and says what to run', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  await withPage({ width: 1440, height: 900 }, async page => {
    // the status of a server whose download left out roads, places and borders
    await page.route('**/api/basemap', async route => {
      const res = await route.fetch();
      const s = await res.json();
      s.styles = s.styles.map(st => st.id === 'readable' ? { ...st, missing: ['boundaries', 'places', 'roads'] } : st);
      await route.fulfill({ response: res, json: s });
    });
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.waitForFunction(() => [...document.querySelectorAll('.map-base-select option')].some(o => o.textContent === 'Readable — needs the full download'));
    await chooseBaseMap(page, 'readable');
    const notice = page.locator('.terrain-notice');
    assert.match(await notice.textContent(), /boundaries, places, roads/);
    assert.match(await notice.textContent(), /histmersion basemap fetch --layer vector --keep-all/);
    assert.match(await notice.locator('a').getAttribute('href'), /how-to\/offline\.md/);
    await chooseBaseMap(page, 'physical');
    await page.waitForFunction(() => /different in the past/.test(document.querySelector('.terrain-notice')?.textContent ?? ''));
  }, { source: 'online' });
});

// #3844: the legend and the map's foot (the notice, the credits) draw over every marker and the base map's
// names, and under a popup the map opens
test('the legend and the notice box stay over the markers and under a popup', async () => {
  let feed = [];
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/findings**', route => route.fulfill({ json: feed }));
    await page.route('**/api/places/geo**', route => route.fulfill({ json: { type: 'FeatureCollection', features: [] } }));
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.waitForFunction(() => window.__testMap && document.querySelector('.hd-legend') && document.querySelector('.map-foot'));
    // a marker under the centre of each: the legend and the credits box
    const spots = await page.evaluate(() => ['.hd-legend', '.map-foot'].map(sel => {
      const m = document.getElementById('map').getBoundingClientRect(), r = document.querySelector(sel).getBoundingClientRect();
      const x = r.left + r.width / 2, y = r.top + r.height / 2;
      return { sel, x, y, at: window.__testMap.containerPointToLatLng([x - m.left, y - m.top]) };
    }));
    feed = spots.map((s, i) => ({ citekey: `zz_z${i}`, title: `Zz ${i}`, claim: 'c', regionName: null, lat: s.at.lat, lon: s.at.lng,
      periodStart: -1300, periodEnd: -1250, confidence: 'high', sourceKind: 'paper' }));
    await page.goto(new URL('/#from=-1340&to=-1200', baseURL).href);
    for (const s of spots) {
      // the marker is drawn right under the control's centre…
      await page.waitForFunction(([x, y]) => [...document.querySelectorAll('.hd-marker')].some(e => {
        const r = e.getBoundingClientRect(); return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom; }), [s.x, s.y], { timeout: 15000 });
      // …and the control is what the pointer finds there
      const top = await page.evaluate(([x, y, sel]) => !!document.elementFromPoint(x, y)?.closest(sel), [s.x, s.y, s.sel]);
      assert.ok(top, `${s.sel} is over the marker under it`);
    }
    // a popup opened over the legend is over it
    const l = spots[0];
    await page.evaluate(([at]) => window.__testMap.openPopup([at.lat, at.lng], 'Zz popup '.repeat(40)), [l.at]);
    await page.locator('.zz-test-popup').waitFor();
    const over = await page.evaluate(() => {
      const r = document.querySelector('.zz-test-popup').getBoundingClientRect();
      return document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2)?.closest('.hd-popup') != null &&
        [...document.querySelectorAll('.hd-legend')].some(g => { const q = g.getBoundingClientRect();
          return q.left < r.right && r.left < q.right && q.top < r.bottom && r.top < q.bottom; });
    });
    assert.ok(over, 'the popup overlaps the legend and is on top of it');
  });
});

test('the past-landscape notice never lies under the credits or the map key, wide or narrow', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract (HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap)'); return; }
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await withPage(viewport, async page => {
      await page.evaluate(() => localStorage.setItem('histmersion.ui.baseMap', '["physical"]'));
      await page.reload();
      await page.waitForFunction(() => window.__testMap);
      await page.locator('.terrain-notice').waitFor({ state: 'attached' });
      // on a phone it is folded to its button (#4292): the button, then the notice opened
      const folded = await page.locator('.map-foot-toggle').isVisible();
      for (const open of folded ? [false, true] : [true]) {
        if (open && folded) await page.locator('.map-foot-toggle').click();
        await page.waitForTimeout(800);
        const hits = await page.evaluate(() => {
          const n = document.querySelector('.map-foot').getBoundingClientRect();
          return [...document.querySelectorAll('.maplibregl-ctrl-bottom-right > *')].map(e => e.getBoundingClientRect())
            .filter(r => r.width > 0 && n.left < r.right && r.left < n.right && n.top < r.bottom && r.top < n.bottom).length;
        });
        assert.equal(hits, 0, `${viewport.width} px${open ? '' : ', folded'}: the notice overlaps the right-hand controls`);
      }
      assert.ok(await page.locator('.terrain-notice').isVisible(), 'the notice is shown');
      const text = await page.locator('.map-foot').evaluate(e => e.scrollHeight <= e.clientHeight + 1);
      assert.ok(text, 'the notice shows all its text');
    });
  }
});

// #4292: on a phone the map's legend and its foot (the past-landscape notice with the credits) took a third of
// the map, and the key, opened, was wider than the screen with its close control off it. In the narrow layout
// each is folded to a button; opened, the legend fits the screen less its 16 px gutters and the map, scrolls
// inside itself and its close stays where it can be tapped; nothing scrolls the page sideways. A tablet folds
// them the same way (#4431).
test('on a phone the legend and the notice fold to buttons; the opened key fits the screen and closes', async () => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  const G = 16;
  for (const viewport of [{ width: 360, height: 740 }, { width: 390, height: 844 }, { width: 430, height: 932 },
    { width: 844, height: 390 }, { width: 768, height: 1024 }, { width: 1024, height: 768 }]) {
    const at = `${viewport.width}×${viewport.height}`;
    await withPage(viewport, async page => {
      // the longest key (the events' kinds), over a vector style that shows the notice
      await page.evaluate(s => {
        localStorage.setItem('histmersion.ui.layers', '["events"]');
        if (s) localStorage.setItem('histmersion.ui.baseMap', '["physical"]');
      }, status.available);
      await page.reload();
      await page.waitForFunction(() => window.__testMap && document.querySelector('.hd-legend .hd-key'));
      if (status.available) await page.locator('.map-foot .terrain-notice').waitFor({ state: 'attached' });
      const box = sel => page.evaluate(s => {
        const e = document.querySelector(s);
        if (!e) return null;
        const r = e.getBoundingClientRect(), st = getComputedStyle(e);
        return { l: r.left, t: r.top, r: r.right, b: r.bottom, w: r.width, h: r.height,
          shown: r.width > 0 && r.height > 0 && st.display !== 'none' && st.visibility !== 'hidden' };
      }, sel);
      const sideways = () => page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
      // the centre of `sel` is on screen and a tap there reaches it
      const tappable = sel => page.evaluate(s => {
        const r = document.querySelector(s).getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
        return x >= 0 && y >= 0 && x <= innerWidth && y <= innerHeight && !!document.elementFromPoint(x, y)?.closest(s);
      }, sel);
      const narrow = await page.evaluate(() => document.documentElement.dataset.layout === 'narrow');
      assert.ok(narrow, `${at}: the map-first layout on a phone and on a tablet`);
      assert.equal(await sideways(), 0, `${at}: no sideways scroll`);
      const map = await box('#map');
      if (narrow) {
        // folded: a button each, the overlays' content hidden
        const lg = await box('.hd-legend');
        assert.equal((await box('.hd-legend-body')).shown, false, `${at}: the legend starts folded`);
        assert.ok(lg.h <= 64 && lg.w <= viewport.width / 2 && lg.r <= viewport.width - G + 1, `${at}: a small button: ${JSON.stringify(lg)}`);
        if (status.available) {
          const ft = await box('.map-foot');
          assert.equal((await box('.map-foot-body')).shown, false, `${at}: the notice starts folded`);
          assert.ok(ft.h <= 64 && ft.w <= 64 && ft.l >= G - 1, `${at}: the notice's button: ${JSON.stringify(ft)}`);
        }
        await page.locator('.hd-legend-toggle').click();
        assert.equal(await page.locator('.hd-legend-toggle').getAttribute('aria-expanded'), 'true');
      }
      // the key opened: inside the screen less its gutters, and inside the map
      await page.locator('.hd-key summary').click();
      await page.waitForFunction(() => document.querySelector('.hd-key').open);
      const o = await box('.hd-legend');
      assert.ok(o.l >= map.l + G / 2 - 1 && o.r <= Math.min(map.r, viewport.width) - (narrow ? G : G / 2) + 1,
        `${at}: the opened key within the map's width less its gutters: ${JSON.stringify(o)} in ${JSON.stringify(map)}`);
      assert.ok(o.t >= map.t && o.b <= Math.min(map.b, viewport.height), `${at}: and its height: ${JSON.stringify(o)}`);
      assert.equal(await sideways(), 0, `${at}: the opened key scrolls nothing sideways`);
      // scrolled to its end inside itself: the key's own line, and the button that folds the legend, can be tapped
      await page.evaluate(() => { const b = document.querySelector('.hd-legend-body'); b.scrollTop = b.scrollHeight; });
      assert.ok(await page.evaluate(() => { const b = document.querySelector('.hd-legend-body'); return b.scrollHeight <= b.clientHeight || b.scrollTop > 0; }),
        `${at}: what does not fit scrolls inside the legend`);
      assert.ok(await tappable('.hd-key summary'), `${at}: the key's line stays on screen`);
      if (narrow) {
        assert.ok(await tappable('.hd-legend-toggle'), `${at}: the legend's button stays on screen`);
        // over the markers and under a popup (#3844)
        const p = await page.evaluate(() => {
          const r = document.querySelector('.hd-legend').getBoundingClientRect(), m = document.getElementById('map').getBoundingClientRect();
          return window.__testMap.containerPointToLatLng([r.left + r.width / 2 - m.left, r.top + r.height / 2 - m.top]);
        });
        await page.evaluate(([la, lo]) => window.__testMap.openPopup([la, lo], 'Zz popup'), [p.lat, p.lng]);
        await page.locator('.zz-test-popup').waitFor();
        assert.ok(await page.evaluate(() => {
          const r = document.querySelector('.zz-test-popup').getBoundingClientRect();
          return !!document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2)?.closest('.hd-popup');
        }), `${at}: a popup opens over the legend`);
        await page.evaluate(() => window.__testMap.closePopup());
        // folded by its button, and by Escape
        await page.locator('.hd-legend-toggle').click();
        assert.equal((await box('.hd-legend-body')).shown, false, `${at}: the button folds the legend`);
        assert.equal(await page.locator('.hd-legend-toggle').getAttribute('aria-expanded'), 'false');
        await page.locator('.hd-legend-toggle').click();
        await page.keyboard.press('Escape');
        assert.equal((await box('.hd-legend-body')).shown, false, `${at}: Escape folds the legend`);
        if (status.available) {
          // the notice opened: all of it, inside the gutters, clear of the legend's button
          await page.locator('.map-foot-toggle').click();
          await page.locator('.map-foot .terrain-notice').waitFor();
          await page.waitForTimeout(100);
          const ft = await box('.map-foot'), lb = await box('.hd-legend');
          assert.ok(ft.l >= G - 1 && ft.r <= viewport.width - G + 1 && ft.t >= map.t && ft.b <= viewport.height,
            `${at}: the notice within the gutters: ${JSON.stringify(ft)}`);
          assert.ok(!(ft.l < lb.r && lb.l < ft.r && ft.t < lb.b && lb.t < ft.b), `${at}: clear of the legend's button`);
          assert.ok(await page.locator('.map-foot-body').evaluate(e => e.scrollHeight <= e.clientHeight + 1), `${at}: all of its text`);
          assert.equal(await sideways(), 0);
          await page.locator('.map-foot-toggle').click();
          assert.equal((await box('.map-foot-body')).shown, false, `${at}: its button folds it`);
        }
      }
    });
  }
});

// #4431: at a tablet's width the side column and the bars above the map left it 9 % of a 768×1024 screen and
// 23 % of a 1024×768 one. Between the phone and the desktop the page is now the map-first layout at tablet
// size: the map keeps at least 60 % of the screen (less the list's sheet over it) and most of its height,
// nothing scrolls sideways, and every panel — the timeline, both drawers, the list, a window, the legend —
// is reached and closed, by pointer and by keyboard. The phone and the desktop keep their layouts.
test('#4431: at tablet widths the map keeps most of the screen, and every panel opens and closes', async () => {
  for (const viewport of [{ width: 768, height: 1024 }, { width: 1024, height: 768 }]) {
    const at = `${viewport.width}×${viewport.height}`;
    await withPage(viewport, async page => {
      const sideways = () => page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
      const box = sel => page.evaluate(s => {
        const e = document.querySelector(s), r = e.getBoundingClientRect();
        return { l: r.left, t: r.top, r: r.right, b: r.bottom, w: r.width, h: r.height };
      }, sel);
      // on screen: inside the viewport, and what a tap at its centre reaches
      const reachable = sel => page.evaluate(s => {
        const e = document.querySelector(s), r = e.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
        return r.width > 0 && r.left >= -1 && r.right <= innerWidth + 1 && x >= 0 && y >= 0 && x <= innerWidth && y <= innerHeight &&
          !!document.elementFromPoint(x, y)?.closest(s);
      }, sel);
      const offScreen = sel => page.evaluate(s => {
        const r = document.querySelector(s).getBoundingClientRect();
        return r.right <= 1 || r.left >= innerWidth - 1;
      }, sel);
      assert.deepEqual(await page.evaluate(() => [document.documentElement.dataset.layout, document.documentElement.hasAttribute('data-tablet')]),
        ['narrow', true], `${at}: the tablet layout`);
      await synchronizedSize(page);
      // the map's share: its area less the list's sheet lying over it, and its height
      const share = await page.evaluate(() => {
        const m = document.getElementById('map').getBoundingClientRect(), s = document.querySelector('.sidebar').getBoundingClientRect();
        const ix = Math.max(0, Math.min(m.right, s.right) - Math.max(m.left, s.left));
        const iy = Math.max(0, Math.min(m.bottom, s.bottom) - Math.max(m.top, s.top));
        return { area: (m.width * m.height - ix * iy) / (innerWidth * innerHeight), height: m.height / innerHeight, width: m.width / innerWidth };
      });
      assert.ok(share.area >= 0.6, `${at}: the map's share of the screen ${share.area.toFixed(2)}`);
      assert.ok(share.height >= 0.6 && share.width >= 0.99, `${at}: the map's height and width ${JSON.stringify(share)}`);
      assert.equal(await sideways(), 0, `${at}: no sideways scroll`);
      // the timeline is open, its controls on one line; ⏱ folds it (the map grows) and unfolds it
      assert.ok(await page.locator('.tl').isVisible(), `${at}: the timeline starts open`);
      assert.equal(await page.locator('.mb-timeline').getAttribute('aria-expanded'), 'true');
      const ctl = await page.evaluate(() => [...document.querySelectorAll('.tl-ctl > *')].filter(e => e.offsetParent)
        .map(e => Math.round(e.getBoundingClientRect().top)));
      assert.ok(Math.max(...ctl) - Math.min(...ctl) < 20, `${at}: the timeline's controls on one line: ${ctl}`);
      const before = await synchronizedSize(page);
      await page.locator('.mb-timeline').click();
      assert.ok(await page.locator('.tl').isHidden(), `${at}: ⏱ folds the timeline`);
      assert.ok((await synchronizedSize(page)).height > before.height, `${at}: the map takes its room`);
      await page.locator('.mb-timeline').click();
      assert.deepEqual(await synchronizedSize(page), before, `${at}: and gives it back`);
      // the filters drawer, by keyboard: Enter on ☰ opens it, Escape closes it
      assert.ok(await offScreen('.fbar'), `${at}: the filters start in their drawer`);
      await page.locator('.mb-filters').focus();
      await page.keyboard.press('Enter');
      await page.waitForFunction(() => document.querySelector('.fbar').getBoundingClientRect().left >= 0);
      await page.waitForTimeout(300);
      assert.ok(await reachable('.fbar .drawer-close'), `${at}: the filters drawer is open`);
      assert.equal(await sideways(), 0, `${at}: the open drawer scrolls nothing sideways`);
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => document.querySelector('.fbar').getBoundingClientRect().right <= 1);
      // the map & tools drawer, by pointer: its button, then its ✕
      await page.locator('.mb-maptools').click();
      await page.waitForFunction(() => document.querySelector('.viewport-toggle').getBoundingClientRect().right <= innerWidth + 1);
      await page.waitForTimeout(300);
      // the drawer scrolls inside itself: its last controls are reached by scrolling it, not the page
      await page.locator('.viewport-toggle .map-base-select').scrollIntoViewIfNeeded();
      assert.ok(await reachable('.viewport-toggle .map-base-select'), `${at}: the base map's menu in the drawer`);
      assert.equal(await page.evaluate(() => scrollY), 0, `${at}: the page itself does not scroll`);
      // the map options, with Map motion (#4718), by keyboard: inside the screen, whole enough to use, Escape closes
      await page.locator('.map-options-btn').focus();
      await page.keyboard.press('Enter');
      await page.locator('.map-motion-select').waitFor();
      const opts = await box('.map-options');
      assert.ok(opts.t >= 0 && opts.b <= viewport.height && opts.l >= 0 && opts.r <= viewport.width && opts.h >= 200,
        `${at}: the map options inside the screen: ${JSON.stringify(opts)}`);
      await page.locator('.map-motion-select').scrollIntoViewIfNeeded();
      assert.ok(await reachable('.map-motion-select'), `${at}: Map motion`);
      await page.keyboard.press('Escape');
      await page.locator('.map-options').waitFor({ state: 'hidden' });
      // a tool opens a window: floating, inside the screen, not full-screen; the drawer closes for it
      await page.locator('.drawer-tools button', { hasText: 'about' }).click();
      await page.locator('.wm-window').waitFor();
      await page.waitForFunction(() => document.querySelector('.viewport-toggle').getBoundingClientRect().left >= innerWidth - 1);
      const win = await box('.wm-window');
      assert.ok(win.w < viewport.width * 0.8 && win.l >= 0 && win.r <= viewport.width && win.t >= 0 && win.b <= viewport.height,
        `${at}: a floating window inside the screen: ${JSON.stringify(win)}`);
      assert.ok(await reachable('.wm-window .wm-close'), `${at}: its close button`);
      await page.keyboard.press('Escape');
      await page.locator('.wm-window').waitFor({ state: 'detached' });
      // the list: a sheet at the side, pulled up by its handle and back down
      const peek = await box('.sidebar');
      assert.ok(peek.r >= viewport.width - 1 && peek.w <= 421, `${at}: the list's sheet at the right: ${JSON.stringify(peek)}`);
      await page.locator('.sheet-handle').click();
      await page.waitForFunction(() => document.querySelector('.sidebar').classList.contains('sheet-half'));
      await page.waitForTimeout(300);
      assert.ok((await box('.sidebar')).h > peek.h + 100, `${at}: the handle pulls the list up`);
      assert.ok(await reachable('.sidebar .tabs .tab'), `${at}: the list's tabs`);
      assert.equal(await sideways(), 0);
      await page.locator('.sheet-handle').click();
      await page.locator('.sheet-handle').click();
      await page.waitForFunction(() => !document.querySelector('.sidebar').classList.contains('sheet-half') &&
        !document.querySelector('.sidebar').classList.contains('sheet-full'));
      await page.waitForTimeout(300);
      // the legend: folded to its button, which opens and folds it (#4292)
      assert.ok(await reachable('.hd-legend-toggle'), `${at}: the legend's button`);
      await page.locator('.hd-legend-toggle').click();
      assert.equal(await page.locator('.hd-legend-toggle').getAttribute('aria-expanded'), 'true');
      await page.keyboard.press('Escape');
      assert.equal(await page.locator('.hd-legend-toggle').getAttribute('aria-expanded'), 'false');
      assert.equal(await sideways(), 0);
    });
  }
  // the phone and the desktop are not tablets
  for (const [viewport, layout] of [[{ width: 390, height: 844 }, 'narrow'], [{ width: 1440, height: 900 }, 'wide']]) {
    await withPage(viewport, async page => {
      assert.deepEqual(await page.evaluate(() => [document.documentElement.dataset.layout, document.documentElement.hasAttribute('data-tablet')]),
        [layout, false], `${viewport.width} px`);
    });
  }
});

test('without a GPU the map says so and draws its cheapest form; the app still works', async t => {
  const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
  if (!status.available) { t.skip('the test server has no extract'); return; }
  // a browser of its own, with Chromium's software rasteriser (no GPU flags): a Chromium condition
  if (!isChromium) { t.skip('Chromium\'s software rasteriser: run with HISTMERSION_TEST_BROWSER=chromium'); return; }
  const soft = await launchSoftwareChromium();
  try {
    const context = await soft.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', r => new URL(r.request().url()).origin === origin ? r.continue() : r.abort());
    await page.goto(baseURL);
    await page.waitForFunction(() => window.histmersionMap);
    const caps = await page.evaluate(() => window.histmersionMap.capabilities());
    if (!caps.software) { t.skip(`this machine's headless Chromium draws WebGL on a GPU (${caps.renderer})`); return; }
    await page.locator('.map-base-select').selectOption('physical');
    await page.locator('.terrain-notice').waitFor();
    assert.match(await page.locator('.terrain-notice').textContent(), /without a GPU/);
    await page.locator('.map-options-btn').click();
    assert.ok(await page.locator('.map-option-contours').isDisabled());
    assert.deepEqual(errors, []);
  } finally { await soft.close(); }
});

// Without WebGL 2 (or with WebGL off) MapLibre draws no map at all, online or local: the map's place says
// why and what to do, nothing on the page fails, and the list still loads (#map-without-webgl2).
test('no WebGL 2: the map says why and what to do, and the rest of the page works', async t => {
  if (!isChromium) { t.skip('the switches are Chromium\'s'); return; }
  for (const [flag, why] of [['--disable-webgl2', /WebGL 1 only/], ['--disable-webgl', /draws no WebGL/]]) {
    const b = await launchChromiumWithout(flag);
    try {
      const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.route('**/*', r => new URL(r.request().url()).origin === origin ? r.continue() : r.abort());
      await page.goto(baseURL);
      const note = page.locator('#map .map-unavailable');
      await note.waitFor();
      const text = await note.innerText();
      assert.match(text, why, flag);
      assert.match(text, /WebGL 2/);
      assert.match(text, /hardware/);
      assert.equal(await page.locator('#map canvas').count(), 0, `${flag}: no map canvas`);
      await page.waitForFunction(() => window.histmersionMap);
      const caps = await page.evaluate(() => window.histmersionMap.capabilities());
      assert.equal(caps.webgl2, false, JSON.stringify(caps));
      // the rest of the page: the list loads, a layer can be switched on, the span dragged — nothing throws
      await page.locator('#list .hit, #list .empty').first().waitFor();
      await page.evaluate(() => { localStorage.setItem('histmersion.ui.layers', '["events","boundaries"]'); });
      await page.reload();
      await note.waitFor();
      await page.locator('#list .hit, #list .empty').first().waitFor();
      await page.waitForTimeout(1000);
      assert.deepEqual(errors, [], `${flag}: application JavaScript errors`);
    } finally {
      await b.close();
    }
  }
});
// (a): the timeline's count curve is smooth — a bucket every ~5 px through a monotone curve, no mark per bucket,
// drawn at the size it is shown (no stretch) — and one hover marker tells a bucket's exact count, at once.
test('the timeline draws a smooth curve at its shown size; one hover marker says a bucket\'s count', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    // the counts the empty test database lacks: a wavy, deterministic strip (with gaps), on the real buckets
    await page.route(/\/api\/aggregate(\?|$)/, async route => {
      const res = await route.fetch();
      const h = await res.json();
      h.buckets = h.buckets.map(b => ({ ...b, count: Math.max(0, Math.round(20 + 14 * Math.sin(b.start / 97) + 9 * Math.sin(b.start / 23) - (Math.abs(b.start % 900) < 60 ? 40 : 0))) }));
      return route.fulfill({ response: res, json: h });
    });
    await page.goto(new URL('/#from=-3000&to=-1000', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => document.querySelector('.tl-detail-svg .tl-dt-curve path.tl-area-line'));
    await page.waitForTimeout(500);
    for (const strip of ['.tl-overview-svg', '.tl-detail-svg']) {
      const s = await page.evaluate(sel => {
        const svg = document.querySelector(sel), r = svg.getBoundingClientRect();
        const line = svg.querySelector('path.tl-area-line');
        return { w: r.width, h: r.height, viewBox: svg.getAttribute('viewBox'), par: svg.getAttribute('preserveAspectRatio'),
          segments: (line.getAttribute('d').match(/C/g) || []).length, dots: svg.querySelectorAll('.tl-area-dot, circle').length };
      }, strip);
      const [, , vw, vh] = s.viewBox.split(' ').map(Number);
      assert.ok(Math.abs(vw - s.w) < 0.01 && Math.abs(vh - s.h) < 0.01, `${strip}: drawn at its shown size: ${JSON.stringify(s)}`);
      assert.notEqual(s.par, 'none', `${strip}: never stretched`);
      assert.equal(s.dots, 0, `${strip}: no mark per bucket`);
      assert.ok(s.segments <= s.w / 4 && s.segments >= s.w / 8, `${strip}: a bucket every ~5 px: ${JSON.stringify(s)}`);
    }
    // the hover: the bucket under the pointer, its count, drawn in the same event
    const hover = await page.evaluate(() => {
      const svg = document.querySelector('.tl-detail-svg'), r = svg.getBoundingClientRect();
      const t0 = performance.now();
      svg.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: r.left + r.width * 0.4, clientY: r.top + 10 }));
      const ms = performance.now() - t0;
      return { ms, label: svg.querySelector('.tl-hover-label')?.textContent, dots: svg.querySelectorAll('.tl-hover-dot').length };
    });
    assert.ok(hover.ms < 100, `the hover answers within 100 ms: ${hover.ms}`);
    assert.match(hover.label ?? '', /: \d+ sources?$/, JSON.stringify(hover));
    assert.equal(hover.dots, 1, 'one hover marker');
    await page.evaluate(() => document.querySelector('.tl-detail-svg').dispatchEvent(new PointerEvent('pointerleave')));
    assert.equal(await page.locator('.tl-detail-svg .tl-hover-label').count(), 0, 'gone when the pointer leaves');
  });
});
