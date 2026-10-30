// The documentation's screenshots (docs/user-guide/assets/screenshots/), taken from a server that holds
// the demo collection (docs/demo/). Run by scripts/docs/screenshots.sh (`just docs-screenshots`), which
// starts that throwaway server; on its own:
//
//   HISTMERSION_SERVER_URL=http://127.0.0.1:<port> HISTMERSION_SCREENSHOTS_OUT=<dir> \
//     node modules/web/test/docs-screenshots.mjs
//
// HISTMERSION_SCREENSHOTS_ONLY=home-light,source takes some of them; HISTMERSION_SCREENSHOTS_OFFLINE=1
// blocks the OpenStreetMap tiles too. Deterministic: a fixed viewport and device scale, the browser's
// clock fixed at 2026-10-19 12:00 UTC (the demo's timestamps are fixed too), every request but the
// server's and the map tiles refused, and each shot waits for the network, the map's tiles, the fonts
// and the windows' content before it is taken. The server's own machine-specific values (its config
// file's path, the host name in the agent lease) are replaced in /api/about before the page sees them.
import { configOverride } from './engine.mjs';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.HISTMERSION_SERVER_URL;
const outDir = process.env.HISTMERSION_SCREENSHOTS_OUT;
assert.ok(base && outDir, 'set HISTMERSION_SERVER_URL and HISTMERSION_SCREENSHOTS_OUT');
const only = new Set((process.env.HISTMERSION_SCREENSHOTS_ONLY || '').split(',').map(s => s.trim()).filter(Boolean));
const offline = process.env.HISTMERSION_SCREENSHOTS_OFFLINE === '1';
const origin = new URL(base).origin;
const ARTICLE = 'climate-and-the-late-bronze-age-collapse';
const wide = { width: 1440, height: 900 };
const phone = { width: 390, height: 844 };
const allLayers = '["boundaries","sites","media","events"]';

// ── one page, prepared the same way every time ───────────────────────────────────────────────────
async function open(browser, { viewport = wide, hash = '', local = {}, path = '' } = {}) {
  const context = await browser.newContext({
    viewport, deviceScaleFactor: 1, isMobile: viewport.width < 760, hasTouch: viewport.width < 760,
    colorScheme: 'dark', locale: 'en-GB', timezoneId: 'UTC', reducedMotion: 'reduce',
  });
  await context.addInitScript(l => {
    try { localStorage.clear(); for (const [k, v] of Object.entries(l)) localStorage.setItem(k, v); } catch { /* none */ }
  }, Object.fromEntries(Object.entries(local).map(([k, v]) => [`histmersion.ui.${k}`, v])));
  // the map's own handle (web MapHandle), as browser.test.mjs uses it
  await context.addInitScript(() => {
    Object.defineProperty(window, '__docsMap', { configurable: true, get: () => window.histmersionMap });
  });
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    if (url.origin === origin) return route.continue();
    if (!offline && /(^|\.)tile\.openstreetmap\.(org|fr)$/.test(url.hostname)) return route.continue();
    return route.abort();
  });
  await configOverride(context);
  await context.route(`${origin}/api/about`, async route => {
    const res = await route.fetch();
    const about = await res.json();
    if (about.configFile?.path) about.configFile.path = '~/.config/histmersion/histmersion.conf';
    if (about.agentLease?.owner) about.agentLease.owner = about.agentLease.owner.replace(/^[^:]*/, 'this-computer');
    await route.fulfill({ response: res, json: about });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.clock.setFixedTime(new Date('2026-10-19T12:00:00Z'));
  await page.goto(path ? `${base}${path}` : `${base}/${hash ? `#${hash}` : ''}`);
  if (!path) await page.waitForFunction(() => window.__docsMap);
  await settle(page);
  return { page, context, errors };
}

/** Wait until nothing is loading: requests, the map's tiles (drawn), fonts, images, window content. */
async function settle(page, extra = 400) {
  await page.waitForLoadState('networkidle');
  await page.waitForFunction(() => {
    const imgs = [...document.querySelectorAll('img')].filter(i => i.offsetParent !== null);
    return (!window.__docsMap || window.__docsMap.isDrawn()) && imgs.every(i => i.complete) && document.fonts.status === 'loaded';
  }, null, { timeout: 30000 });
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(extra);
}

async function view(page, lat, lon, zoom) {
  await page.evaluate(([la, lo, z]) => window.__docsMap.setView([la, lo], z), [lat, lon, zoom]);
  await settle(page, 800);
}

/** Move and size the newest window by dragging its title bar and its resize handle, as a person would. */
async function placeWindow(page, { x, y, w, h }) {
  const win = page.locator('.wm-window').last();
  await win.waitFor();
  const bar = await win.locator('.wm-titlebar').boundingBox();
  await page.mouse.move(bar.x + 40, bar.y + bar.height / 2);
  await page.mouse.down();
  await page.mouse.move(x + 40, y + bar.height / 2, { steps: 6 });
  await page.mouse.up();
  const handle = await win.locator('.wm-resize-handle').boundingBox();
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
  await page.mouse.down();
  await page.mouse.move(x + w - handle.width / 2, y + h - handle.height / 2, { steps: 6 });
  await page.mouse.up();
  await settle(page);
  return win;
}

/** Scroll a window's content so that `text` (the first match) sits near its top. */
async function scrollWindowTo(page, text, { offset = 12 } = {}) {
  const content = page.locator('.wm-window').last().locator('.wm-content');
  await content.evaluate((el, [t, off]) => {
    const walk = n => [...n.querySelectorAll('h1,h2,h3,h4,summary,strong,p,div,section,li,span,a')]
      .find(e => e.textContent.trim().startsWith(t) && e.children.length < 6);
    const doc = el.querySelector('iframe')?.contentDocument;
    const hit = walk(doc ?? el);
    if (!hit) throw new Error(`"${t}" is not in the window`);
    const scroller = doc ? doc.scrollingElement : el;
    const top = hit.getBoundingClientRect().top - (doc ? 0 : el.getBoundingClientRect().top) + scroller.scrollTop;
    scroller.scrollTop = Math.max(0, top - off);
  }, [text, offset]);
  await settle(page, 300);
}

/** The visible marker whose label or title matches `re`: its centre. */
async function marker(page, re) {
  const all = await page.evaluate(() => [...document.querySelectorAll('.hd-pane .hd-marker')].map(e => {
    const r = e.getBoundingClientRect();
    const s = e.querySelector('[aria-label],[title]') || e;
    return { x: r.x + r.width / 2, y: r.y + r.height / 2, label: s.getAttribute('aria-label') || s.getAttribute('title') || '' };
  }));
  const hit = all.find(m => re.test(m.label));
  assert.ok(hit, `no marker matching ${re}: ${all.map(m => m.label).join(' | ')}`);
  return hit;
}

/** A base-map shot: the notice up, the view set, the base map drawn whole. */
async function baseMapShot(page, shoot, [lat, lon, zoom] = [37.75, 25.5, 8]) {   // filled by the fixture's region (22.5–28.5°E, 35–40.5°N)
  await page.locator('.terrain-notice').waitFor();
  await view(page, lat, lon, zoom);
  await page.waitForFunction(() => window.__docsMap.isDrawn() && window.__docsMap.baseMapsDrawn() > 0, null, { timeout: 30000 });
  await settle(page, 1500);
  await shoot();
}

// ── the shots ────────────────────────────────────────────────────────────────────────────────────
// Each: name → how to take it. `shoot(page, clip?)` writes <name>.png.
const shots = {
  // The welcome page: the map over the eastern Mediterranean, 1700–1000 BCE, every layer on; the
  // standard (light) and the dark base map.
  'home-light': { local: { layers: allLayers, baseMap: '["standard"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => { await view(page, 36.6, 29.5, 5); await shoot(); } },
  'home-dark': { local: { layers: allLayers, baseMap: '["dark"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => { await view(page, 36.6, 29.5, 5); await shoot(); } },

  // The same view on the muted base map (OpenStreetMap's Humanitarian style, from OSM France).
  'home-muted': { local: { layers: allLayers, baseMap: '["muted"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => { await view(page, 36.6, 29.5, 5); await shoot(); } },

  // The two base maps drawn from the server's own extract — here the small Aegean fixture
  // (modules/web/test/fixtures/basemap) — with the notice about the past landscape: Readable with the
  // events on top, Physical with nothing of the corpus on top, and Physical close in over Mycenae (the
  // fixture's focus region: the detailed relief and its contours).
  'basemap-readable': { local: { layers: '["events"]', baseMap: '["readable"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => baseMapShot(page, shoot) },
  'basemap-physical': { local: { layers: '[]', sources: '["false"]', baseMap: '["physical"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => baseMapShot(page, shoot) },
  'physical-mycenae': { local: { layers: '[]', sources: '["false"]', baseMap: '["physical"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => baseMapShot(page, shoot, [37.73, 22.756, 12]) },
  'readable-mycenae': { local: { layers: '[]', sources: '["false"]', baseMap: '["readable"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => baseMapShot(page, shoot, [37.73, 22.756, 12]) },
  // the map options, open over the Aegean
  'map-options': { local: { layers: '[]', sources: '["false"]', baseMap: '["physical"]' },
    hash: 'from=-1700&to=-1000', run: async (page, shoot) => {
      await baseMapShot(page, async () => {}, [37.6, 24.2, 8]);
      // the map options are in the layers panel, opened by its button over the map
      if (await page.locator('#layers-btn').isVisible()) await page.locator('#layers-btn').click();
      await page.locator('.map-options-btn').click();
      await page.locator('.map-options').waitFor();
      await settle(page, 300);
      const b = await page.locator('.map-options').boundingBox();
      await shoot({ x: Math.max(0, b.x - 360), y: Math.max(0, b.y - 60), width: b.width + 520, height: b.height + 160 });
    } },

  // The phone layout: the map first, the controls on buttons, the list as a sheet.
  'phone': { viewport: phone, local: { layers: '["events","media"]', sheet: '["half"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => { await view(page, 36.0, 28.5, 5); await shoot(); } },

  // The period presets menu, open over the timeline, then the map with one preset chosen.
  'timeline-presets': { hash: 'from=-1700&to=-1000', local: { layers: '["boundaries"]' },
    run: async (page, shoot) => {
      await view(page, 38.5, 33, 5);
      await page.locator('.tl-btn-presets').click();
      await page.locator('.tl-preset-region', { hasText: 'Anatolia' }).click();
      await settle(page);
      await shoot({ x: 0, y: 176, width: 1080, height: 620 });
    } },
  'timeline-period': { local: { layers: '["boundaries","events"]' }, hash: 'from=-1700&to=-1000',
    run: async (page, shoot) => {
      await view(page, 37.5, 31, 5);
      await page.locator('.tl-btn-presets').click();
      await page.locator('.tl-preset-region', { hasText: 'Anatolia' }).click();
      await page.locator('.tl-preset-item', { hasText: 'Hittite Empire' }).first().click();
      await settle(page, 1000);
      await shoot();
    } },

  // Words in the filter bar: the list shows the findings that matched, the map only their places.
  'search': { hash: 'q=drought&from=-1700&to=-1000', local: { layers: '[]' },
    run: async (page, shoot) => { await view(page, 37, 30, 5); await shoot(); } },

  // A source's window: details, status, summary, then its findings.
  'source': { hash: `open=source:kaniewski2013_environmental&from=-1700&to=-1000`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 860 });
      await shoot(await win.boundingBox());
    } },
  'source-findings': { hash: `open=source:kaniewski2013_environmental&from=-1700&to=-1000`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 860 });
      await scrollWindowTo(page, 'Findings');
      await shoot(await win.boundingBox());
    } },
  // Its citation links: the newer paper that challenges it, with both statuses.
  'source-citations': { hash: `open=source:kaniewski2013_environmental&from=-1700&to=-1000`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 620 });
      await scrollWindowTo(page, 'Cites');
      await shoot(await win.boundingBox());
    } },
  'graph': { hash: `open=graph:kaniewski2013_environmental&from=-1700&to=-1000`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 200, y: 20, w: 900, h: 700 });
      await page.waitForTimeout(2500);   // the force layout comes to rest
      await shoot(await win.boundingBox());
    } },

  // Full-text search: a source's own page (the window's "open in new tab"), its full text with the
  // searched word marked — the page the CLI's `passages` reads from.
  'passages': { path: '/source/manning2020_beyond?full=true&q=megadrought', viewport: { width: 1100, height: 700 },
    run: async (page, shoot) => {
      await page.locator('p', { hasText: 'Much recent scholarship' }).locator('mark').first().evaluate(m => m.scrollIntoView({ block: 'center' }));
      await settle(page, 300);
      await shoot();
    } },

  // An event: the events tab, and the card of one event.
  'event': { hash: 'from=-1700&to=-1500&tab=events', local: { layers: '["events","sites"]' },
    run: async (page, shoot) => {
      await view(page, 36.4, 25.4, 8);
      await page.locator('.tabs').getByText('events', { exact: true }).click();
      await settle(page);
      await page.getByText('Minoan eruption of Thera').first().hover();
      await page.waitForTimeout(1200);
      await shoot();
    } },

  // A place: a state of the time on the map (the boundaries layer), and its card.
  'place': { hash: 'from=-1300&to=-1200', local: { layers: '["boundaries"]', sources: '[]' },
    run: async (page, shoot) => {
      await view(page, 37.5, 30, 5);
      const p = await page.evaluate(() => {
        const pt = window.__docsMap.latLngToContainerPoint([39.2, 33.2]);
        const r = document.getElementById('map').getBoundingClientRect();
        return { x: r.left + pt.x, y: r.top + pt.y };
      });
      await page.mouse.move(p.x, p.y);
      await page.mouse.move(p.x + 3, p.y + 2);
      await page.locator('.cite-tip, .place-card, [class*=card]').filter({ hasText: 'Hittite Empire' }).first()
        .waitFor().catch(() => {});
      await page.waitForTimeout(800);
      await settle(page);
      await shoot();
    } },

  // A picture: the media layer's marker opened, with the credit line and the licence.
  'media': { hash: 'from=-1400&to=-1250', local: { layers: '["media"]', sources: '[]' },
    run: async (page, shoot) => {
      await view(page, 35.75, 33.3, 8);
      const m = await marker(page, /open this picture/);
      await page.mouse.click(m.x, m.y);
      await page.waitForTimeout(800);
      await settle(page);
      await shoot();
    } },

  // The welcome page's tour: one source on the map — the title filter leaves only its findings, one
  // finding's popup is open, and its window sits beside them.
  'tour-source-map': { hash: 'title=Environmental%20Roots&open=source:kaniewski2013_environmental&from=-1700&to=-1000',
    local: { layers: '["boundaries"]' },
    run: async (page, shoot) => {
      await placeWindow(page, { x: 8, y: 8, w: 560, h: 884 });
      await view(page, 33.73, 31.54, 7);
      const m = await marker(page, /Environmental Roots.*1200 BCE–850 BCE.*open this finding/);
      await page.mouse.click(m.x, m.y);
      await page.evaluate(() => window.getSelection()?.removeAllRanges());
      await page.waitForTimeout(600);
      await settle(page);
      await shoot();
    } },

  // The welcome page's tour: an article's citation hovered — the cited source's card, with its status.
  'tour-article-cite': { hash: `open=article:${ARTICLE}&from=-1300&to=-1100`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 8, y: 8, w: 760, h: 700 });
      const content = win.locator('.wm-content');
      const frame = await content.locator('iframe').count() ? content.frameLocator('iframe') : content;
      await frame.getByText('Kaniewski et al. 2013', { exact: true }).first().hover();
      await page.waitForTimeout(1200);
      await settle(page);
      await shoot({ x: 0, y: 0, width: 780, height: 808 });
    } },

  // A source's and an article's own pages (the windows' "open in new tab"): the same content, full page.
  'source-page': { path: '/source/kaniewski2013_environmental', viewport: { width: 960, height: 760 },
    run: async (page, shoot) => { await shoot(); } },
  'article-page': { path: `/article/${ARTICLE}`, viewport: { width: 960, height: 760 },
    run: async (page, shoot) => { await shoot(); } },

  // An article: its text with its citations, and the generated coverage sections at its end.
  'article': { hash: `open=article:${ARTICLE}&from=-1300&to=-1100`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 860 });
      await shoot(await win.boundingBox());
    } },
  'article-coverage': { hash: `open=article:${ARTICLE}&from=-1300&to=-1100`,
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 860 });
      await scrollWindowTo(page, 'Not used');
      await shoot(await win.boundingBox());
    } },

  // Research goals, the agent managers, the approvals, and the About window.
  'goal': { hash: 'open=goal:late-bronze-age-collapse&from=-1700&to=-1000',
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 700 });
      await shoot(await win.boundingBox());
    } },
  'managers': { hash: 'open=managers&from=-1700&to=-1000',
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 700 });
      await shoot(await win.boundingBox());
    } },
  'approvals': { hash: 'open=approvals&apprState=all&from=-1700&to=-1000',
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 250, y: 20, w: 820, h: 760 });
      await shoot(await win.boundingBox());
    } },
  'about': { hash: 'open=about&from=-1700&to=-1000',
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 200, y: 20, w: 1000, h: 640 });
      await shoot(await win.boundingBox());
    } },
  'about-plugins': { hash: 'open=about&from=-1700&to=-1000',
    run: async (page, shoot) => {
      const win = await placeWindow(page, { x: 200, y: 20, w: 1000, h: 860 });
      await scrollWindowTo(page, 'Plugins');
      await shoot(await win.boundingBox());
    } },
};

// the map is WebGL: drawn by the GPU where there is one (macOS: Metal), as the app is used
const gpu = process.platform === 'darwin' ? ['--enable-gpu', '--ignore-gpu-blocklist', '--use-angle=metal'] : [];
const browser = await chromium.launch({ headless: true, args: gpu,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
await mkdir(outDir, { recursive: true });
const failed = [];
try {
  for (const [name, s] of Object.entries(shots)) {
    if (only.size && !only.has(name)) continue;
    const { page, context, errors } = await open(browser, s);
    try {
      await s.run(page, async clip => {
        const box = clip && { x: Math.max(0, Math.floor(clip.x)), y: Math.max(0, Math.floor(clip.y)),
          width: Math.floor(clip.width), height: Math.floor(clip.height) };
        await page.screenshot({ path: join(outDir, `${name}.png`), clip: box, animations: 'disabled', caret: 'hide' });
      });
      assert.deepEqual(errors, [], `${name}: JavaScript errors on the page`);
      console.log(`  ${name}.png`);
    } catch (e) {
      failed.push(name);
      console.error(`  ${name}: ${e.message.split('\n')[0]}`);
    } finally {
      await context.close();
    }
  }
} finally {
  await browser.close();
}
if (failed.length) { console.error(`failed: ${failed.join(', ')}`); process.exit(1); }
