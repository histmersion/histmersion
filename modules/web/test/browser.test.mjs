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
