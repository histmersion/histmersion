// The soak session (#1949): what a long day at the map does to the page — windows opened and closed,
// the sidebar's tabs switched, the span dragged, every layer toggled, cards hovered — repeated, and
// the memory compared with where it stood after the first rounds (when every lazy part has loaded
// once and every cache has had its first fill). A leak is what keeps growing with the rounds.
//
// Read-only: the pages it shows are About and the jobs, the window a source's page (GET only); nothing it
// clicks writes.

import { memory, quiet } from './perf-lib.mjs';

/** One round: every interaction once. */
async function round(page, i) {
  // pages: Settings › About and Research › Activity › Every background job shown, then back to the map
  await page.locator('.places a[data-nav="settings"]').click();
  await page.locator('.side-nav a', { hasText: 'About Histmersion' }).click();
  await page.locator('.about-pane').waitFor();
  await page.locator('.places a[data-nav="research"]').click();
  await page.locator('.side-nav a', { hasText: 'Activity' }).click();
  await page.locator('.activity-details a', { hasText: 'Every background job' }).click();
  await page.locator('.jobs-pane').waitFor();
  await page.locator('.places a[data-nav="explore"]').click();
  await page.waitForFunction(() => !document.querySelector('.jobs-pane'));
  // a source's window, its page followed to itself (reloaded in place) where it links to itself
  const src = page.locator('#list .hit [data-ck]').first();
  if (await src.count()) {
    await src.click();
    const win = page.locator('.wm-window').first();
    await win.waitFor();
    const self = win.locator('a.zz-self');
    if (await self.count().catch(() => 0)) { await self.click(); await quiet(page); }
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('.wm-window'));
  }
  // the sidebar's tabs, and back to the sources
  const tabs = page.locator('.sidebar .tabs .tab');
  const n = await tabs.count();
  for (let t = 1; t < n; t++) await tabs.nth(t).click();
  await tabs.nth(0).click();
  // the span: a short drag on the overview strip, alternating direction
  const svg = await page.locator('.tl-overview-svg').boundingBox();
  const sel = await page.evaluate(() => {
    const r = document.querySelector('.tl-ov-sel rect, .tl-ov-sel *');
    const b = r?.getBoundingClientRect();
    return b && b.width > 12 ? { x: b.left + b.width / 2 } : null;
  });
  const x = sel?.x ?? svg.x + svg.width / 2, y = svg.y + svg.height / 2, d = i % 2 ? -3 : 3;
  await page.mouse.move(x, y);
  await page.mouse.down();
  for (let s = 1; s <= 6; s++) await page.mouse.move(x + s * d, y);
  await page.mouse.up();
  // every layer on, then off again (in the layers panel, opened by its button)
  if (await page.locator('#layers-btn').isVisible() && !(await page.locator('#layers-panel').isVisible())) await page.locator('#layers-btn').click();
  const toggles = page.locator('.layer-toggles .layer-group > button.btn--toggle');
  const m = await toggles.count();
  for (let t = 0; t < m; t++) await toggles.nth(t).click();
  await quiet(page, 100);
  for (let t = 0; t < m; t++) await toggles.nth(t).click();
  // a card: hover the first source link, then away
  const link = page.locator('#list .hit [data-ck]').first();
  if (await link.count()) {
    await link.hover();
    // a locator, not waitForSelector: an element handle would keep every card it found alive
    await page.locator('.cite-tooltip').first().waitFor({ timeout: 5000 }).catch(() => {});
    await page.mouse.move(5, 5);
  }
}

/** Run `rounds` rounds after `warmup` ones; the memory after the warm-up, after the rounds, and the
  * growth per round. */
export async function soak(page, cdp, rounds, { warmup = 5 } = {}) {
  for (let i = 0; i < warmup; i++) await round(page, i);
  await page.waitForTimeout(1000);
  const before = await memory(page, cdp);
  const t0 = Date.now();
  for (let i = 0; i < rounds; i++) await round(page, i);
  // let the last fade-outs and debounced loads finish
  await page.waitForTimeout(1500);
  await quiet(page);
  const after = await memory(page, cdp);
  return {
    rounds, seconds: Math.round((Date.now() - t0) / 1000), before, after,
    growth: {
      heapMB: after.heapMB === null ? null : Math.round((after.heapMB - before.heapMB) * 10) / 10,
      nodes: after.nodes - before.nodes,
      listeners: after.listeners === null ? null : after.listeners - before.listeners,
      mapMarkers: after.mapMarkers - before.mapMarkers,
    },
  };
}
