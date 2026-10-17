// The web UI's speed and memory on a real corpus (#1949): every interactive path of perf-paths.mjs,
// measured in Chromium against HISTMERSION_SERVER_URL, printed as JSON (and as the Markdown rows of
// docs/research-web-perf.md with --md). READ-ONLY: it only GETs, moves the map and the timeline,
// hovers, types into filters and opens an article; it clicks nothing that writes.
//
//   HISTMERSION_SERVER_URL=http://localhost:8000 node test/perf-baseline.mjs [--runs 3] [--md] [--soak 100]
//
// `--soak N` also replays the soak session N times and reports the memory before and after.

import { chromium } from 'playwright';
import { memory, newPerfPage, quiet } from './perf-lib.mjs';
import * as paths from './perf-paths.mjs';
import { soak } from './perf-soak.mjs';

const baseURL = process.env.HISTMERSION_SERVER_URL;
if (!baseURL) { console.error('Set HISTMERSION_SERVER_URL'); process.exit(1); }
const origin = new URL(baseURL).origin;
const arg = (name, dflt) => { const i = process.argv.indexOf(name); return i < 0 ? dflt : process.argv[i + 1]; };
const runs = Number(arg('--runs', 3));
const soakN = Number(arg('--soak', 0));
const md = process.argv.includes('--md');

const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
const out = { url: baseURL, bundle: process.env.HISTMERSION_PERF_BUNDLE ?? 'served', runs, at: new Date().toISOString(), paths: {} };
const record = async (name, v) => {
  try { (out.paths[name] ??= []).push(await v); } catch (e) { (out.errors ??= []).push(`${name}: ${e.message}`); }
};

async function fresh(run, viewport) {
  const s = await newPerfPage(browser, origin, viewport);
  try { await run(s); } finally {
    if (s.blocked.length) (out.blockedWrites ??= []).push(...s.blocked);
    if (s.errors.length) (out.pageErrors ??= []).push(...s.errors);
    await s.context.close();
  }
}

for (let i = 0; i < runs; i++) {
  await fresh(async ({ page, cdp }) => {
    await page.goto(baseURL);
    await page.evaluate(() => localStorage.clear());
    await record('firstRender', paths.firstRender(page, baseURL));
    await quiet(page);
    await record('memoryAfterLoad', memory(page, cdp));
    await record('mapPan', paths.mapPan(page));
    await record('timelineDrag', paths.timelineDrag(page, -3500, -500));
    await record('hoverCards', paths.hoverCards(page));
    await record('filterTyping', paths.filterTyping(page, 'bronze age', 'b'));
    await record('articleOpen', paths.articleOpen(page, baseURL));
    await record('memoryAfterSession', memory(page, cdp));
  });
  await fresh(async ({ page, cdp }) => {
    await page.goto(baseURL);
    await record('rangeAreas', paths.rangeAreas(page, baseURL, -3000, 2000));
    await record('memoryRangeAreas', memory(page, cdp));
  });
  await fresh(async ({ page, cdp }) => {
    await page.goto(baseURL);
    await record('eventLayer', paths.eventLayer(page, baseURL, -12000, 2026, { atLeast: 100 }));
    await record('memoryEventLayer', memory(page, cdp));
  });
}

if (soakN > 0) {
  await fresh(async ({ page, cdp, errors }) => {
    await page.goto(baseURL);
    await page.waitForFunction(() => !!window.__testMap);
    await quiet(page);
    out.soak = await soak(page, cdp, soakN, { baseURL });
    out.soak.errors = errors;
  });
}
await browser.close();

/** The median of each numeric field over the runs (nested objects too). */
function median(rows) {
  const first = rows[0];
  if (first === null || typeof first !== 'object') {
    const xs = rows.filter(x => typeof x === 'number').sort((a, b) => a - b);
    return xs.length ? xs[Math.floor((xs.length - 1) / 2)] : first;
  }
  return Object.fromEntries(Object.keys(first).map(k => [k, median(rows.map(r => r?.[k]))]));
}
out.median = Object.fromEntries(Object.entries(out.paths).map(([k, v]) => [k, median(v)]));

if (md) {
  const flat = (o, p = '') => Object.entries(o ?? {}).flatMap(([k, v]) =>
    v !== null && typeof v === 'object' ? flat(v, `${p}${k}.`) : [[`${p}${k}`, v]]);
  for (const [name, v] of Object.entries(out.median)) {
    console.log(`| ${name} | ${flat(v).map(([k, x]) => `${k} ${x}`).join(' · ')} |`);
  }
  if (out.soak) console.log(JSON.stringify(out.soak, null, 2));
} else console.log(JSON.stringify(out, null, 2));
