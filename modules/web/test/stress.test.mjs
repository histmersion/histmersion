// Stress: two minutes of rapid zooming and dragging on the Histmersion map with every overlay a person
// typically turns on (boundaries, events, sites, pictures; findings off, as reported 2026-10-23 when
// Zen/Firefox hung on the old map). The page must stay responsive the whole time: a heartbeat timer may
// never be held up beyond a budget, the page must answer an evaluate within a second at every check,
// and nothing may throw. HISTMERSION_STRESS_SECONDS sets the length (120).
//
// Read-only like the other browser regressions (perf-lib.mjs refuses non-GET requests).

import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { engineName, launch } from './engine.mjs';
import { newPerfPage } from './perf-lib.mjs';
import { routeFixtures } from './perf-fixtures.mjs';

const baseURL = process.env.HISTMERSION_SERVER_URL;
assert.ok(baseURL, 'Set HISTMERSION_SERVER_URL to the test server');
const origin = new URL(baseURL).origin;
const seconds = Number(process.env.HISTMERSION_STRESS_SECONDS || 120);

/** A heartbeat stall above this means the person would feel the page freeze. */
const STALL_MAX_MS = 400;
/** The share of heartbeats that may be late by more than 100 ms (frames during heavy redraws). */
const LATE_SHARE_MAX = 0.05;

let browser;
before(async () => { browser = await launch(); });
after(async () => { await browser?.close(); });

for (const style of ['readable', 'physical']) {
  test(`stress (${engineName}): ${seconds}s of zooms and drags on ${style} with boundaries, events, sites and pictures`, async t => {
    const status = await (await fetch(new URL('/api/basemap', baseURL))).json();
    if (!status.available) { t.skip('the test server has no extract'); return; }
    const s = await newPerfPage(browser, origin, { width: 1440, height: 900 }, { source: 'local' });
    try {
      const page = s.page;
      await routeFixtures(page);
      await page.goto(baseURL);
      await page.evaluate(st => {
        localStorage.clear();
        localStorage.setItem('histmersion.ui.baseMap', `["${st}"]`);
        localStorage.setItem('histmersion.ui.layers', '["boundaries","events","sites","media"]');
        localStorage.setItem('histmersion.ui.sources', '[]');
        localStorage.setItem('histmersion.ui.areaPick', '["latest"]');
      }, style);
      await page.goto(new URL('/explore#from=-3000&to=1500', baseURL).href);
      await page.reload();
      await page.waitForFunction(() => !!window.__testMap && !window.__testMap.isMoving(), null, { timeout: 30000 });
      await page.evaluate(() => window.__testMap.setView([37.9, 25.4], 7));

      // the heartbeat: a 20 ms timer records how late each tick arrives
      await page.evaluate(() => {
        const H = window.__heart = { late: [], max: 0 };
        let last = performance.now();
        H.id = setInterval(() => {
          const now = performance.now(), late = now - last - 20;
          H.late.push(late); if (late > H.max) H.max = late; last = now;
        }, 20);
      });

      const box = await page.locator('#map').boundingBox();
      const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
      const end = Date.now() + seconds * 1000;
      let rounds = 0, slowChecks = 0;
      while (Date.now() < end) {
        rounds++;
        await page.mouse.move(cx, cy);
        for (let i = 0; i < 6; i++) { await page.mouse.wheel(0, -240); await page.waitForTimeout(60); }   // zoom in fast
        await page.mouse.down();
        for (let i = 1; i <= 12; i++) await page.mouse.move(cx + i * 25 * (rounds % 2 ? 1 : -1), cy + i * 12);
        await page.mouse.up();
        for (let i = 0; i < 6; i++) { await page.mouse.wheel(0, 240); await page.waitForTimeout(60); }    // zoom out fast
        await page.mouse.down();
        for (let i = 1; i <= 12; i++) await page.mouse.move(cx - i * 20, cy - i * 10 * (rounds % 2 ? 1 : -1));
        await page.mouse.up();
        // the page must answer promptly between bursts
        const t0 = Date.now();
        await Promise.race([
          page.evaluate(() => window.__testMap.isMoving()),
          new Promise((_, rej) => setTimeout(() => rej(new Error(`the page did not answer within 5 s after round ${rounds}`)), 5000)),
        ]);
        if (Date.now() - t0 > 1000) slowChecks++;
      }
      const heart = await page.evaluate(() => { clearInterval(window.__heart.id); const h = window.__heart;
        const late100 = h.late.filter(l => l > 100).length; return { ticks: h.late.length, max: Math.round(h.max), late100 }; });
      const lateShare = heart.late100 / Math.max(1, heart.ticks);
      console.log(`# stress ${engineName} ${style}: ${rounds} rounds, heartbeat max stall ${heart.max} ms, ` +
        `${heart.late100}/${heart.ticks} ticks >100 ms late (${(lateShare * 100).toFixed(1)} %), slow checks ${slowChecks}`);
      assert.ok(heart.max <= STALL_MAX_MS, `the page froze for ${heart.max} ms (budget ${STALL_MAX_MS})`);
      assert.ok(lateShare <= LATE_SHARE_MAX, `${(lateShare * 100).toFixed(1)} % of heartbeats >100 ms late (budget ${LATE_SHARE_MAX * 100} %)`);
      assert.equal(slowChecks, 0, `${slowChecks} checks took over 1 s to answer`);
      assert.deepEqual(s.errors, [], 'application JavaScript errors');
      assert.deepEqual(s.blocked, [], 'requests that would write');
    } finally {
      await s.context.close();
    }
  });
}
