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
    const marker = page.locator('path.hd-site-marker').first();
    await marker.waitFor();
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
