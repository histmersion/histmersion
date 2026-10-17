import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { chromium } from 'playwright';

// Use a separately started test server; these tests only GET data and change browser-local state.
const baseURL = process.env.HISTMERSION_SERVER_URL;
assert.ok(baseURL, 'Set HISTMERSION_SERVER_URL to the test server');
const origin = new URL(baseURL).origin;
let browser;
before(async () => {
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
  });
});
after(async () => { await browser?.close(); });

async function withPage(viewport, run) {
  const context = await browser.newContext({ viewport, isMobile: viewport.width < 760, hasTouch: true });
  try {
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    // Retain the real Leaflet instance, without replacing any of its behavior or application code.
    await page.addInitScript(() => {
      let leaflet;
      Object.defineProperty(window, 'L', {
        configurable: true,
        get: () => leaflet,
        set: value => {
          leaflet = value;
          let factory = value.map;
          Object.defineProperty(value, 'map', {
            configurable: true, enumerable: true,
            set: fn => { factory = fn; },
            get: () => function (...args) {
              const map = factory.apply(value, args);
              window.__testMap = map;
              return map;
            },
          });
        },
      });
    });
    await page.route('**/*', route => new URL(route.request().url()).origin === origin
      ? route.continue() : route.abort()); // No third-party tile servers are needed.
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
    const icon = el.closest('.leaflet-marker-icon') || el;
    const r = icon.getBoundingClientRect(), m = document.getElementById('map').getBoundingClientRect();
    const p = window.__testMap.latLngToContainerPoint([la, lo]);
    return Math.abs(r.left + r.width / 2 - (m.left + p.x)) < 2 && Math.abs(r.top + r.height / 2 - (m.top + p.y)) < 2;
  }, [selector, lat, lon]);
}

async function synchronizedSize(page) {
  await page.waitForFunction(() => {
    const element = document.getElementById('map');
    const size = window.__testMap.getSize();
    return size.x === element.clientWidth && size.y === element.clientHeight;
  });
  return page.evaluate(() => {
    const size = window.__testMap.getSize();
    return { width: size.x, height: size.y };
  });
}

const phone = { width: 390, height: 844 };
test('Leaflet follows the phone timeline, its detail strip, and closing the timeline', async () => {
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

test('Leaflet follows a desktop sidebar resize without a window resize', async () => {
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

test('#718: the About button opens the server report — build, health states, job queue, store', async () => {
  for (const viewport of [{ width: 1440, height: 900 }, phone]) {
    await withPage(viewport, async page => {
      if (viewport.width < 760) await page.locator('.mb-maptools').click();
      await page.locator('.tool-btns button[title="about"]:visible').click();
      const pane = page.locator('.about-pane .about-sections');
      await pane.waitFor();
      const text = await pane.innerText();
      for (const label of ['Server', 'commit', 'schema version', 'Health', 'converter', 'Job queue', 'queued',
        'Backups', 'last backup', 'Store', 'gazetteer places', 'period presets'])
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
  for (const width of [1440, 1100, 820]) {
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
    await page.waitForSelector('path.hd-event-outline');
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
    await page.waitForFunction(() => !document.querySelector('.hd-event') && !document.querySelector('path.hd-event-outline'));
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
    await page.route('**/api/findings**', route => route.fulfill({ json: findings }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries","sites"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForSelector('path.hd-polity');
    await page.waitForSelector('.hd-site-marker');
    await page.waitForSelector('.hd-find');
    const map = await page.locator('#map').boundingBox();
    const at = async (lat, lon) => {
      const p = await page.evaluate(([la, lo]) => window.__testMap.latLngToContainerPoint([la, lo]), [lat, lon]);
      return { x: map.x + p.x, y: map.y + p.y };
    };
    const hit = async (lat, lon) => {
      const { x, y } = await at(lat, lon);
      return page.evaluate(([x, y]) => {
        const el = document.elementFromPoint(x, y);
        return el ? (el.closest('.hd-point-wrap--diamond') ? 'finding' : el.getAttribute('class') || el.tagName) : 'nothing';
      }, [x, y]);
    };
    const placeInHash = () => new URLSearchParams(new URL(page.url()).hash.slice(1)).getAll('placeId');
    const check = async () => {
      // the pointer's target at each spot: the site's dot, the finding's marker, the region elsewhere
      assert.match(await hit(37.5, 32), /hd-site-marker/, 'the site dot is on top');
      assert.equal(await hit(37, 31), 'finding', 'the finding marker is on top');
      assert.match(await hit(38.5, 33.5), /hd-polity/, 'the region answers where no dot is');
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
    await page.waitForSelector('.leaflet-popup-content');
    assert.match(await page.locator('.leaflet-popup-content').innerText(), /a claim inside the realm/);
    assert.deepEqual(placeInHash(), [], 'the realm was not chosen');
    await page.evaluate(() => window.__testMap.closePopup());
    await page.waitForFunction(() => !document.querySelector('.leaflet-popup'));   // it fades out first
    // the boundaries switched off and on: every shape is added again, after the dots
    await page.locator('.viewport-toggle button[title="boundaries"]').click();
    await page.waitForFunction(() => !document.querySelector('path.hd-polity'));
    await page.locator('.viewport-toggle button[title="boundaries"]').click();
    await page.waitForSelector('path.hd-polity');
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
    await page.route('**/api/events/geo**', route => { asked.push(new URL(route.request().url())); return route.fulfill({ json: feed }); });
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
    assert.ok(asked.some(u => u.searchParams.get('context') === 'true' && u.searchParams.get('area')), 'the feed is narrowed to the view');
    // the attested event's card lists its source (a source link) and the article citing it
    await battle.hover();
    await page.waitForSelector('.cite-tooltip .section-sources a[data-ck="zz_src2020"]');
    assert.match(await page.locator('.cite-tooltip').innerText(), /Zz article/);
    // the catalogue event's card names the catalogue and its lead, and is marked as such
    await page.mouse.move(5, 5);
    await erup.hover();
    await page.waitForSelector('.cite-tooltip .section-leads');
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
    await approx.hover();
    await page.waitForSelector('.cite-tooltip');
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
    await page.waitForFunction(() => document.querySelectorAll('.leaflet-marker-icon.hd-point-wrap--diamond').length >= 2);
    await page.waitForTimeout(800);   // the dots grow in (hd-entering) before they can be hit
    const markerAt = (lat, lon) => page.evaluate(([la, lo]) => {
      const p = window.__testMap.latLngToContainerPoint([la, lo]);
      const m = document.getElementById('map').getBoundingClientRect();
      const el = document.elementFromPoint(m.left + p.x, m.top + p.y)?.closest('.leaflet-marker-icon');
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
    await page.waitForSelector('.leaflet-popup-content');
    assert.match(await page.locator('.leaflet-popup-content').innerText(), /a claim at the dot/);
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
  await withPage({ width: 1100, height: 640 }, async page => {
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
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
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["boundaries","sites","events","media"]'));
    await page.goto(new URL('/#from=-1300&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.4, 32.3], 7, { animate: false }));
    await page.waitForSelector('path.hd-polity');
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
    await page.waitForSelector('.leaflet-popup-content .media-popup-title .cite-tip-icon svg.glyph');
  });
});

// #1647: a cluster of events keeps the event's square, apart from the findings' round bubbles; among
// thousands of points (the events layer) a zoom redraws the bubbles at once instead of animating them —
// markercluster's animation forced a layout and drew every icon twice (40–60 ms tasks) — and a few points
// animate again.
test('a cluster of events is a square; among thousands of points a zoom does not animate the bubbles', async () => {
  const near = (i) => ({ lat: 37.5 + (i % 3) * 0.02, lon: 32 + Math.floor(i / 3) * 0.02 });
  const few = [0, 1, 2].map(i => evGeo(`zz_c${i}`, `Zz event ${i}`, 'battle', near(i).lat, near(i).lon));
  const many = Array.from({ length: 1200 }, (_, i) =>
    evGeo(`zz_m${i}`, `Zz many ${i}`, 'earthquake', 20 + (i % 40) * 0.5, -10 + Math.floor(i / 40) * 0.5));
  let feed = { unplaced: 0, events: few };
  await withPage({ width: 1440, height: 900 }, async page => {
    await page.route('**/api/findings**', route => route.fulfill({ json: [] }));
    await page.route('**/api/places/geo**', route => route.fulfill({ json: { type: 'FeatureCollection', features: [] } }));
    await page.route('**/api/events/geo**', route => route.fulfill({ json: feed }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '3');
    const shape = await page.evaluate(() => {
      const b = document.querySelector('.leaflet-marker-icon .hd-cluster');
      return { events: b.classList.contains('hd-cluster--events'), says: b.getAttribute('aria-label'),
        rects: b.querySelectorAll('svg rect').length, circles: b.querySelectorAll('svg circle').length };
    });
    assert.deepEqual(shape, { events: true, says: '3 events', rects: 4, circles: 0 });
    // does the next zoom animate the bubbles? Counted where markercluster starts one: its animated
    // `_animationStart` (the plugin's own method set, mixed into the group — wrapped here once)
    await page.evaluate(() => {
      let g;
      window.__testMap.eachLayer(l => { if (l._featureGroup) g = l; });
      const orig = g._withAnimation._animationStart;
      const spy = function () { window.__clusterAnims = (window.__clusterAnims || 0) + 1; return orig.apply(this, arguments); };
      g._withAnimation._animationStart = spy;
      if (g._animationStart === orig) g._animationStart = spy;
    });
    const zoomAnimates = (z) => page.evaluate((zoom) => new Promise(resolve => {
      const map = window.__testMap, before = window.__clusterAnims || 0;
      map.once('zoomend', () => setTimeout(() => resolve((window.__clusterAnims || 0) > before), 600));
      map.setZoom(zoom);
    }), z);
    const held = () => page.evaluate(() => {
      let n = 0;
      window.__testMap.eachLayer(l => { if (l._featureGroup) n = Math.max(n, l.getLayers().length); });
      return n;
    });
    assert.equal(await zoomAnimates(8), true, 'three points: the zoom animates');
    // a thousand and more: the next zoom does not
    feed = { unplaced: 0, events: few.concat(many) };
    await page.evaluate(() => window.__testMap.setView([30, 5], 4, { animate: false }));
    await page.waitForFunction(() => {
      let n = 0;
      window.__testMap.eachLayer(l => { if (l._featureGroup) n = Math.max(n, l.getLayers().length); });
      return n === 1203;
    }, null, { timeout: 15000 });
    assert.equal(await zoomAnimates(5), false, '1 203 points: the zoom does not animate');
    assert.equal(await zoomAnimates(4), false);
    // back to a few: animated again
    feed = { unplaced: 0, events: few };
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    await page.waitForFunction(() => {
      let n = 0;
      window.__testMap.eachLayer(l => { if (l._featureGroup) n = Math.max(n, l.getLayers().length); });
      return n === 3;
    }, null, { timeout: 15000 });
    assert.equal(await held(), 3);
    assert.equal(await zoomAnimates(8), true, 'a few points again: the zoom animates again');
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
    await page.route('**/api/events/zz_e4', route => route.fulfill({ json: evDetail('zz_e4', 'Zz battle four', 'battle', 1) }));
    await page.route('**/api/places/zz_s2?**', route => route.fulfill({ json: { place: { id: 'zz_s2', label: 'Zz site 2', kind: 'site',
      periodStart: -1400, periodEnd: -1100, source: null, names: '' }, geometry: null, parents: [], children: [], findings: 0, asOf: null,
      siteType: 'burial', media: [] } }));
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites","events"]'));
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37.5, 32], 7, { animate: false }));
    const bubble = () => page.evaluate(() => {
      const b = document.querySelector('.leaflet-marker-icon .hd-cluster');
      return b ? { n: Number(b.textContent), says: b.getAttribute('aria-label'), mixed: b.classList.contains('hd-cluster--mixed'),
        // events among the others: the round bubble with the event's square tab (#1647)
        withEvents: b.classList.contains('hd-cluster--with-events') && !!b.querySelector('.hd-bubble-tab') } : null;
    });
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '5');
    assert.deepEqual(await bubble(), { n: 5, says: '2 findings · 2 sites · 1 event', mixed: true, withEvents: true });
    // a layer switched off leaves the bubble at once; back on, it returns
    await page.locator('.viewport-toggle button[title="sites"]').click();
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '3');
    assert.equal((await bubble()).says, '2 findings · 1 event');
    await page.locator('.viewport-toggle button[title="sites"]').click();
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '5');
    await page.locator('.viewport-toggle button[title="sources"]').click();
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '3');
    assert.equal((await bubble()).says, '2 sites · 1 event');
    await page.locator('.viewport-toggle button[title="sources"]').click();
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '5');
    // the year window moves past the findings: they leave the bubble
    await page.goto(new URL('/#from=-1200&to=-1100', baseURL).href);
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '3', null, { timeout: 10000 });
    assert.equal((await bubble()).says, '2 sites · 1 event');
    await page.goto(new URL('/#from=-1350&to=-1200', baseURL).href);
    await page.waitForFunction(() => document.querySelector('.leaflet-marker-icon .hd-cluster')?.textContent === '5', null, { timeout: 10000 });
    // a click lists every kind, each row with its icon; a site's row pins its card
    await page.waitForFunction(() => document.querySelectorAll('.leaflet-marker-icon .hd-cluster').length === 1);
    const b = await page.locator('.leaflet-marker-icon .hd-cluster').boundingBox();
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
    await page.waitForFunction(() => !document.querySelector('.leaflet-marker-icon .hd-cluster') &&
      document.querySelectorAll('.leaflet-marker-icon .hd-point').length === 5, null, { timeout: 10000 });
    assert.equal(await page.locator('.hd-find').count(), 2);
    assert.equal(await page.locator('.hd-site-marker').count(), 2);
    assert.equal(await page.locator('.leaflet-marker-icon .hd-ev').count(), 1);
  });
});

// The layer filters are ONE icon menu (a native <select> cannot show icons): a button with the choice's
// icon, a listbox with the map's own badge per option, the keyboard pattern of a listbox; the toolbar's
// switches carry the shape each layer draws.
test('the icon menus: icons per option, keyboard choice updates the filter; the toolbar shows each layer\'s shape', async () => {
  await withPage({ width: 1440, height: 900 }, async page => {
    let asked = [];
    await page.route('**/api/events/geo**', route => { asked.push(new URL(route.request().url())); return route.fulfill({ json: { unplaced: 0, events: [] } }); });
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
    assert.ok(asked.slice(before).some(u => u.searchParams.get('kind') === 'siege'), 'kind=siege was asked for');
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
    const more = pane.locator('.show-more');
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
    await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["sites","events","media"]'));
    await page.goto(new URL('/#from=-1350&to=-1100', baseURL).href);
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(() => window.__testMap.setView([37, 32], 7, { animate: false }));
    await page.waitForFunction(() => document.querySelectorAll('.leaflet-marker-icon .hd-cluster').length === 3 &&
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
    const bubbles = await page.evaluate(() => [...document.querySelectorAll('.leaflet-marker-icon .hd-cluster')].map(b => {
      const box = b.getBoundingClientRect();
      const cx = box.left + box.width / 2, cy = box.top + box.height / 2;
      const cs = [...b.querySelectorAll('.hd-bubble circle')].map(c => { const r = c.getBoundingClientRect();
        return { dx: r.left + r.width / 2 - cx, dy: r.top + r.height / 2 - cy, w: r.width }; });
      const t = b.querySelector('.hd-dot-count').getBoundingClientRect();
      return { n: b.textContent, d: box.width, cs, count: { dx: t.left + t.width / 2 - cx, dy: t.top + t.height / 2 - cy },
        // Leaflet lifts every svg in the map to z-index 200: the bubble's must stay under its count
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
      const el = document.querySelector('.hd-find.hd-unstated').closest('.leaflet-marker-icon');
      el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await page.waitForSelector('.leaflet-popup-content');
    assert.match(await page.locator('.leaflet-popup-content').innerText(), /confidence not stated/);
  });
});

// Process managers, phase 2: the goals window's New research area form and the article window's
// Review / Extend / Enrich buttons, against fixed answers (nothing is written): the form refuses an
// existing slug, creates the goal at the slug made from the title, starts the planner and creates the
// seeding manager (paused) when ticked; watching is offered but disabled (phase 3). The buttons post the
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
    const watch = form.locator('.goal-on.is-disabled input');
    assert.equal(await watch.isDisabled(), true, 'watch is phase 3');
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
