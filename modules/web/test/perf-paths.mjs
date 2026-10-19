// The interactive paths of the web UI (#1949), each measured in the browser: what one person does
// (open the page, pan the map, drag the timeline, hover, type, open an article, turn a layer on) and
// what it costs between their input and the next paint. Shared by the regression budgets
// (perf.test.mjs, fixed feeds on the test server) and the live baseline (perf-baseline.mjs).
//
// Nothing here writes: every path only GETs, moves the map, and changes browser-local state.

import { inputToUpdate, measure, memory, quiet } from './perf-lib.mjs';

/** The value `fn` first answers truthy, its handle released (a handle keeps what it holds alive, and
  * the memory figures would count it). Never `waitForSelector`, for the same reason: use a locator. */
export async function valueOf(page, fn, arg, opts = {}) {
  const h = await page.waitForFunction(fn, arg, { timeout: 60000, ...opts });
  try { return await h.jsonValue(); } finally { await h.dispose(); }
}
const ready = page => valueOf(page, () => !!window.__testMap);
/** Nothing on its way and the list not reloading: what is measured next is the gesture's own work. */
export const settled = async page => {
  await quiet(page);
  await page.waitForFunction(() => !document.querySelector('#count.is-loading'));
};

/** The page from navigation to its first paint, the map, the first markers and the first list. */
export async function firstRender(page, url, { markers = true } = {}) {
  await page.goto(url);
  const at = (fn, arg) => valueOf(page, fn, arg);
  const mapMs = await at(() => window.__testMap && performance.now());
  const markersMs = markers
    ? await at(() => document.querySelector('.leaflet-marker-icon') && performance.now()) : null;
  const listMs = await at(() => document.querySelector('#list .hit, #list .empty') && performance.now());
  const fcp = await page.evaluate(() => performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? null);
  const lt = await page.evaluate(() => window.__perf.longTasks);
  const r = x => x === null ? null : Math.round(x);
  return { fcpMs: r(fcp), mapMs: r(mapMs), markersMs: r(markersMs), listMs: r(listMs),
    longTasks: lt.length, longTaskMs: r(lt.reduce((s, t) => s + t.duration, 0)),
    longTaskMax: r(Math.max(0, ...lt.map(t => t.duration))) };
}

/** A drag across the map (40 moves of 12 px, one a frame) and a wheel zoom in and out. */
export async function mapPan(page) {
  await ready(page);
  await settled(page);
  const box = await page.locator('#map').boundingBox();
  const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  return measure(page, async () => {
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(cx - i * 12, cy + (i % 5)); await page.waitForTimeout(16); }
    await page.mouse.up();
    for (const dy of [-200, -200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(300); }
  });
}

/** Where the overview strip draws the year `y`. */
async function overviewX(page, y) {
  return page.evaluate(async year => {
    const cfg = await (await fetch('/api/config')).json();
    const t = cfg.time ?? cfg.ui?.time;
    const r = document.querySelector('.tl-overview-svg').getBoundingClientRect();
    return r.left + (year - t.min) / (t.max - t.min) * r.width;
  }, y);
}

/** The span dragged along the overview strip (40 moves of 4 px right, 20 back), then released: the drag itself
  * (markers re-filtered on each move), and from the release to the list's next render. */
export async function timelineDrag(page, from, to) {
  await ready(page);
  await settled(page);
  const svg = await page.locator('.tl-overview-svg').boundingBox();
  const x = await overviewX(page, (from + to) / 2), y = svg.y + svg.height / 2;
  const drag = await measure(page, async () => {
    await page.mouse.move(x, y);
    await page.mouse.down();
    for (let i = 1; i <= 40; i++) { await page.mouse.move(x + i * 4, y); await page.waitForTimeout(16); }
    for (let i = 39; i >= 20; i--) { await page.mouse.move(x + i * 4, y); await page.waitForTimeout(16); }
  });
  const releaseToList = await inputToUpdate(page, '#list', () => page.mouse.up());
  return { ...drag, releaseToListMs: releaseToList };
}

/** "All states in period": the boundaries layer over a whole range, from the layer's switch to its
  * shapes drawn; then a drag of the span with every shape on the map. */
export async function rangeAreas(page, baseURL, from, to) {
  await page.evaluate(() => {
    localStorage.setItem('histmersion.ui.layers', '["boundaries"]');
    localStorage.setItem('histmersion.ui.areaPick', '["latest"]');
  });
  await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
  await page.reload();
  await ready(page);
  const t0 = await page.evaluate(() => performance.now());
  // drawn once nothing is on its way and the count of shapes has stopped moving
  await quiet(page);
  const drawn = await valueOf(page, () => {
    const n = document.querySelectorAll('path.hd-polity').length;
    const P = window.__perf;
    if (n > 0 && n === P._lastN) { P._same = (P._same || 0) + 1; } else { P._same = 0; P._lastN = n; P._at = performance.now(); }
    return P._same >= 10 && { n, at: P._at };
  }, null, { polling: 50 });
  const shapes = drawn.n;
  const lt = await page.evaluate(() => window.__perf.longTasks);
  const loadLong = { longTasks: lt.length, longTaskMs: Math.round(lt.reduce((s, t) => s + t.duration, 0)),
    longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))) };
  const drag = await timelineDrag(page, from, to);
  return { shapes, drawnMs: Math.round(drawn.at), sinceMapMs: Math.round(drawn.at - t0), load: loadLong, drag };
}

/** Hover the first `n` source links of the list: from the pointer's arrival to the card's first paint,
  * and to its content (a cold hover asks the server; a second hover of the same link is cached). */
export async function hoverCards(page, n = 6) {
  await ready(page);
  await settled(page);
  await page.locator('#list [data-ck]').first().waitFor();
  const links = page.locator('#list .hit [data-ck]');
  const count = Math.min(n, await links.count());
  const shown = [], filled = [], warm = [];
  const away = async () => { await page.mouse.move(5, 5); await page.waitForFunction(() => !document.querySelector('.cite-tooltip')); };
  for (let i = 0; i < count; i++) {
    const link = links.nth(i);
    await link.scrollIntoViewIfNeeded();
    const at = x => () => link.hover({ position: { x, y: 4 } });
    await away();
    shown.push(await inputToUpdate(page, '.cite-tooltip', at(4)));
    await away();
    filled.push(await inputToUpdate(page, '.cite-tooltip', at(5), `!el.textContent.includes('loading')`));
    await away();
    warm.push(await inputToUpdate(page, '.cite-tooltip', at(4), `!el.textContent.includes('loading')`));
  }
  await away();
  const max = xs => Math.max(0, ...xs);
  return { links: count, shownMax: max(shown), filledMax: max(filled), warmMax: max(warm) };
}

/** Typing into the search box (each keystroke's echo), into a chip filter (to its suggestions), and
  * hovering a suggestion (its preview, to the list's next render). */
export async function filterTyping(page, text, chipPrefix) {
  await ready(page);
  await settled(page);
  const box = page.locator('#f-q');
  await box.click();
  const keys = await measure(page, async () => { await box.pressSequentially(text, { delay: 60 }); });
  const toList = await inputToUpdate(page, '#list', () => box.press('End'));
  await box.fill('');
  // a chip filter: its suggestions, then a hovered suggestion's preview of the filter
  if (await page.locator('#f-more').isHidden()) await page.locator('#f-more-toggle').click();
  const chip = page.locator('#f-more .chip-input').first();
  await chip.click();
  const suggest = await inputToUpdate(page, '#f-more .chip-suggest', () => chip.pressSequentially(chipPrefix, { delay: 30 }),
    `!el.hidden && el.querySelector('li')`);
  let preview = null;
  const opt = page.locator('#f-more .chip-suggest li').first();
  if (await opt.count()) {
    const ob = await opt.boundingBox();
    preview = await inputToUpdate(page, '#list', () => page.mouse.move(ob.x + 6, ob.y + ob.height / 2));
  }
  await chip.fill('');
  await page.keyboard.press('Escape');
  return { keystrokes: keys.inputs, keyP95: keys.inputP95, keyMax: keys.inputMax, keyLongTasks: keys.longTasks,
    lastKeyToListMs: toList, suggestMs: suggest, previewToListMs: preview };
}

/** The Articles tab, then its first article: from the click to its window, and to its content. */
export async function articleOpen(page, baseURL) {
  await page.goto(new URL('/#tab=articles', baseURL).href);
  await page.reload();
  await ready(page);
  await page.locator('.articles-list .article-link').first().waitFor();
  const link = page.locator('.articles-list .article-link').first();
  const shown = await inputToUpdate(page, '.wm-window', () => link.click());
  const result = { windowMs: shown };
  await page.locator('.wm-window .wm-close').click();
  await page.waitForFunction(() => !document.querySelector('.wm-window'));
  result.contentMs = await inputToUpdate(page, '.wm-window .wm-content', () => link.click(), `el.querySelector('h1, h2')`);
  await page.locator('.wm-window .wm-close').click();
  return result;
}

/** Wheel zooms in and out at the map's centre (six steps, 400 ms apart: each one's zoom animation ends
  * before the next), with every marker loaded — what the cluster group's zoom animation costs (#1647). */
export async function mapZoom(page) {
  await ready(page);
  await settled(page);
  const box = await page.locator('#map').boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  return measure(page, async () => {
    for (const dy of [-200, -200, -200, 200, 200, 200]) { await page.mouse.wheel(0, dy); await page.waitForTimeout(400); }
  });
}

/** The events layer at a whole-world view: from the reload to every marker drawn, then a pan, then
  * wheel zooms from the same view. */
export async function eventLayer(page, baseURL, from, to, { atLeast = 1 } = {}) {
  await page.evaluate(() => localStorage.setItem('histmersion.ui.layers', '["events"]'));
  await page.goto(new URL(`/#from=${from}&to=${to}`, baseURL).href);
  await page.reload();
  await ready(page);
  const t0 = await page.evaluate(() => performance.now());
  await page.evaluate(() => window.__testMap.setView([30, 20], 3, { animate: false }));
  await quiet(page);
  const drawn = await valueOf(page, min => {
    let n = 0;
    window.__testMap.eachLayer(l => { if (l._featureGroup) n = Math.max(n, l.getLayers().length); });
    const P = window.__perf;
    if (n >= min && n === P._lastE) { P._sameE = (P._sameE || 0) + 1; } else { P._sameE = 0; P._lastE = n; P._atE = performance.now(); }
    return P._sameE >= 10 && { n, at: P._atE };
  }, atLeast, { polling: 50 });
  const lt = await page.evaluate(() => window.__perf.longTasks);
  const pan = await mapPan(page);
  await page.evaluate(() => window.__testMap.setView([30, 20], 3, { animate: false }));
  const zoom = await mapZoom(page);
  return { markers: drawn.n, drawnMs: Math.round(drawn.at - t0), longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))), pan, zoom };
}

/** The terrain base map over the Aegean (the fixture extract), in its default style — the physical
  * names on when the server has them: from the reload to the notice and every tile drawn, then a pan and wheel zooms — the vector tiles drawn on canvas and the relief shaded in
  * the page, all of it inside the pan's budget. */
export async function terrainMap(page, baseURL, { names = false } = {}) {
  await page.evaluate(() => {
    localStorage.setItem('histmersion.ui.baseMap', '["terrain"]');
    localStorage.setItem('histmersion.ui.terrainStyle', '["terrain"]');   // the local default is Standard
  });
  await page.goto(new URL('/', baseURL).href);
  await page.reload();
  await ready(page);
  await settled(page);   // the app's own start (the findings, the list) is firstRender's, not the terrain's
  const t0 = await page.evaluate(() => performance.now());
  await page.evaluate(() => window.__testMap.setView([37.9, 25.4], 7, { animate: false }));
  await valueOf(page, () => document.querySelector('.terrain-notice') && document.querySelector('.terrain-relief canvas'));
  // with the names (the server has labels.pmtiles): their own layer is drawn too, inside the same budget
  if (names) await valueOf(page, () => document.querySelector('.terrain-labels canvas'));
  await quiet(page);
  const drawnMs = Math.round(await page.evaluate(() => performance.now()) - t0);
  // the terrain's own work: what ran after the view was set (the app's start is firstRender's)
  const lt = (await page.evaluate(() => window.__perf.longTasks)).filter(t => t.start >= t0);
  const pan = await mapPan(page);
  await page.evaluate(() => window.__testMap.setView([37.9, 25.4], 6, { animate: false }));
  const zoom = await mapZoom(page);
  return { drawnMs, longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))), pan, zoom };
}

/** A local street map (Standard, Muted, Dark: roads by class, borders, land use, place names) over
  * Athens at zoom 9 — data zoom 8, where the roads are heaviest for what the map shows — from the
  * view set to every tile and name drawn, then a pan there and wheel zooms out to 8 and in again. */
export async function localMap(page, baseURL, style, { lat = 37.95, lon = 23.7, zoom = 9 } = {}) {
  await page.evaluate(s => {
    localStorage.setItem('histmersion.ui.baseMap', '["terrain"]');
    localStorage.setItem('histmersion.ui.terrainStyle', `["${s}"]`);
  }, style);
  await page.goto(new URL('/', baseURL).href);
  await page.reload();
  await ready(page);
  await settled(page);
  const t0 = await page.evaluate(() => performance.now());
  await page.evaluate(([la, lo, z]) => window.__testMap.setView([la, lo], z, { animate: false }), [lat, lon, zoom]);
  await valueOf(page, () => document.querySelectorAll('.terrain-base canvas').length > 2 && document.querySelector('.terrain-labels canvas'));
  await quiet(page);
  const drawnMs = Math.round(await page.evaluate(() => performance.now()) - t0);
  const lt = (await page.evaluate(() => window.__perf.longTasks)).filter(t => t.start >= t0);
  const pan = await mapPan(page);
  await page.evaluate(([la, lo, z]) => window.__testMap.setView([la, lo], z - 1, { animate: false }), [lat, lon, zoom]);
  const zoomed = await mapZoom(page);
  return { drawnMs, longTaskMax: Math.round(Math.max(0, ...lt.map(t => t.duration))), pan, zoom: zoomed };
}

export { memory };
