// Find names drawn only in part: after a session of pans and zooms, every display tile on screen is checked
// against protomaps-leaflet's own label index — a name the index holds but a tile that it overlaps did not
// draw at its last render (or one a tile drew that the index no longer holds) shows as a cut-off name.
// usage: node diag-labels.mjs <origin> <style> <w> <h> <dpr> <maxLabeledTiles|default> [rounds]
import { chromium } from '../../modules/web/node_modules/playwright/index.mjs';

const [origin, style, w, h, dpr, ml, rounds = 6] = process.argv.slice(2);
const shot = process.env.SHOT;   // where to save the last view (optional)
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: +w, height: +h }, deviceScaleFactor: +dpr });
const page = await ctx.newPage();
await page.route('**/*', r => new URL(r.request().url()).origin === origin ? r.continue() : r.abort());
await page.addInitScript(([ml]) => {
  let leaflet;
  Object.defineProperty(window, 'L', { configurable: true, get: () => leaflet, set: v => {
    leaflet = v; let f = v.map;
    Object.defineProperty(v, 'map', { configurable: true, enumerable: true, set: fn => { f = fn; },
      get: () => function (...a) { const m = f.apply(v, a); window.__testMap = m; return m; } });
  } });
  let p;
  window.__drawn = new Map();   // "z:x:y" → Set of label texts drawn at the tile's last render
  Object.defineProperty(window, 'protomapsL', { configurable: true, get: () => p, set: v => {
    const orig = v.leafletLayer;
    p = { ...v, leafletLayer: o => {
      const l = orig(o);
      if (!o.labelRules || !o.labelRules.length) return l;
      window.__labelLayer = l;
      if (ml !== 'default') l.labelers.maxLabeledTiles = +ml;
      const add = l.labelers.add.bind(l.labelers);
      l.labelers.add = (z, m) => {
        const r = add(z, m);
        const idx = l.labelers.getIndex(z);
        if (idx && !idx.__wrapped) {
          idx.__wrapped = true;
          const sb = idx.searchBbox.bind(idx);
          idx.searchBbox = (bbox, order) => {
            const s = sb(bbox, order);
            const x = Math.round((bbox.minX + 16) / 256), y = Math.round((bbox.minY + 16) / 256);
            window.__drawn.set(`${z}:${x}:${y}`, new Set([...s].map(e => e.deduplicationKey ?? '?')));
            return s;
          };
        }
        return r;
      };
      return l;
    } };
  } });
}, [ml]);
await page.goto(origin + '/');
await page.evaluate(s => { localStorage.clear(); localStorage.setItem('histmersion.ui.baseMap', '["terrain"]'); localStorage.setItem('histmersion.ui.terrainStyle', `["${s}"]`); }, style);
await page.reload();
await page.waitForFunction(() => !!window.__testMap && !!window.__labelLayer, null, { timeout: 60000 });
const views = process.env.VIEWS ? JSON.parse(process.env.VIEWS) : [[39.5, 23.5, 6], [41.5, 21.5, 7], [38, 26, 6], [40, 22, 7], [37, 30, 6], [39.5, 23.5, 5], [41.6, 21.7, 7], [38.5, 22.5, 7]];
const box = await page.locator('#map').boundingBox();
const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
const check = async () => page.evaluate(() => {
  const l = window.__labelLayer, map = window.__testMap, z = Math.round(map.getZoom());
  const idx = l.labelers.getIndex(z); if (!idx) return { z, tiles: 0, cut: [] };
  const cut = []; window.__missing = [];
  let tiles = 0;
  for (const k in l._tiles) {
    const c = l._tiles[k].coords; if (c.z !== z || !l._tiles[k].current) continue;
    tiles++;
    const bbox = { minX: 256 * c.x - 16, minY: 256 * c.y - 16, maxX: 256 * (c.x + 1) + 16, maxY: 256 * (c.y + 1) + 16 };
    const now = new Set([...idx.tree.search(bbox)].map(e => e.indexedLabel.deduplicationKey ?? '?'));
    const drew = window.__drawn.get(`${z}:${c.x}:${c.y}`) || new Set();
    for (const e of idx.tree.search(bbox)) { const t = e.indexedLabel.deduplicationKey ?? '?'; if (!drew.has(t)) {
      const a = map.latLngToContainerPoint(map.unproject([e.minX, e.minY], z)), b = map.latLngToContainerPoint(map.unproject([e.maxX, e.maxY], z));
      const tl = map.latLngToContainerPoint(map.unproject([256 * c.x, 256 * c.y], z));
      cut.push(`missing in ${c.x},${c.y}: ${t}`); (window.__missing ??= []).push({ t, a, b, tile: tl }); } }
    for (const t of drew) if (!now.has(t)) cut.push(`stale in ${c.x},${c.y}: ${t}`);
  }
  return { z, tiles, labeledTiles: idx.size(), cut };
});
const results = [];
await page.evaluate(() => { window.__missing = []; });
for (let r = 0; r < +rounds; r++) {
  for (const [la, lo, z] of views) {
    await page.evaluate(([a, b, zz]) => window.__testMap.setView([a, b], zz, { animate: false }), [la, lo, z]);
    await page.waitForTimeout(1500);
    for (const [dx, dy] of [[-500, 0], [0, 300], [500, -300]]) {
      await page.mouse.move(cx, cy); await page.mouse.down();
      for (let i = 1; i <= 10; i++) { await page.mouse.move(cx + dx * i / 10, cy + dy * i / 10); await page.waitForTimeout(16); }
      await page.mouse.up(); await page.waitForTimeout(700);
    }
    await page.waitForTimeout(1500);
    const c = await check();
    results.push(c);
    if (c.cut.length) console.log(`round ${r} view ${la},${lo},z${z}: ${c.tiles} tiles, ${c.labeledTiles} labeled data tiles, ${c.cut.length} inconsistent: ${c.cut.slice(0, 6).join(' | ')}`);
  }
}
const bad = results.filter(r => r.cut.length).length;
console.log(`SUMMARY style=${style} ${w}x${h}@${dpr} maxLabeledTiles=${ml}: ${bad}/${results.length} settled views had cut-off names; max labeled data tiles ${Math.max(...results.map(r => r.labeledTiles || 0))}`);
const miss = await page.evaluate(() => { const W = document.querySelector('#map').clientWidth, H = document.querySelector('#map').clientHeight; const m = window.__missing.filter(e => e.a.x > 0 && e.b.x < W && e.a.y > 0 && e.b.y < H && (e.a.x < e.tile.x || e.b.x > e.tile.x + 256 || e.a.y < e.tile.y || e.b.y > e.tile.y + 256)); window.__missing = []; return m; });
console.log('MISSING on screen, straddling a tile edge:', miss.length, JSON.stringify(miss.slice(0, 10)));
if (shot) await page.locator('#map').screenshot({ path: shot });
await browser.close();
