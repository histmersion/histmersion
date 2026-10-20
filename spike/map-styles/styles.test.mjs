// The prototype styles' pure parts: node --test spike/map-styles/styles.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { toGlZooms, sources, readable, physical, READABLE, PHYSICAL } from './styles.mjs';

const status = { layers: [
  { layer: 'vector', present: true, version: 'v1', maxZoom: 10 },
  { layer: 'labels', present: true, version: 'l1', maxZoom: 8 },
  { layer: 'terrain', present: true, version: 't1', maxZoom: 8, focus: [{ name: 'argolid', minZoom: 9, maxZoom: 12, bbox: '22.5,37.4,23.2,37.9' }] },
  { layer: 'bathymetry', present: true, version: 'b1', maxZoom: 7 },
] };

test('zooms move from the 256-pixel grid to MapLibre\'s: layer ranges and zoom stops one less, nothing else', () => {
  const s = toGlZooms({ layers: [{ id: 'x', minzoom: 8, maxzoom: 10,
    paint: { w: ['interpolate', ['linear'], ['zoom'], 8, 0.7, 10, ['match', ['get', 'k'], 'a', 1, 2]], c: '#fff' },
    layout: { s: ['step', ['zoom'], 1, 9, 2] },
    filter: ['<=', ['get', 'min_zoom'], ['+', ['zoom'], 0.5]] }] });
  const l = s.layers[0];
  assert.equal(l.minzoom, 7);
  assert.equal(l.maxzoom, 9);
  assert.deepEqual(l.paint.w, ['interpolate', ['linear'], ['zoom'], 7, 0.7, 9, ['match', ['get', 'k'], 'a', 1, 2]]);
  assert.deepEqual(l.layout.s, ['step', ['zoom'], 1, 8, 2]);
  assert.equal(l.paint.c, '#fff');
  // a filter on the data's own min_zoom is left alone (Protomaps' min_zoom is on MapLibre's grid)
  assert.deepEqual(l.filter, ['<=', ['get', 'min_zoom'], ['+', ['zoom'], 0.5]]);
});

test('a layer starting at zoom 0 stays at 0', () => {
  assert.equal(toGlZooms({ layers: [{ id: 'x', minzoom: 0 }] }).layers[0].minzoom, 0);
});

test('one elevation source to the deepest focus zoom, through the page protocol; land cover its own z7 source', () => {
  const s = sources(status);
  assert.equal(s.dem.maxzoom, 12);
  assert.deepEqual(s.dem.tiles, ['hmdem://8/t1/{z}/{x}/{y}']);
  assert.equal(s.lc.maxzoom, 7);
  assert.match(s.lc.tiles[0], /&layers=landcover:a$/);
  assert.equal(s.osm.maxzoom, 10);
  assert.equal(s.sea.maxzoom, 7);
});

test('an extract without a layer gives a style without its source or layers', () => {
  const st = readable({ layers: [{ layer: 'vector', present: true, version: 'v', maxZoom: 9 }] });
  assert.ok(!st.sources.dem && !st.sources.ne);
  assert.ok(st.layers.every(l => !l.source || st.sources[l.source]), 'every layer names a source that exists');
  assert.ok(!st.layers.some(l => l.type === 'hillshade'));
});

test('both styles: every layer names a source that exists, ids are unique, the fonts are declared', () => {
  for (const st of [readable(status), physical(status)]) {
    assert.ok(st.layers.every(l => !l.source || st.sources[l.source]));
    assert.equal(new Set(st.layers.map(l => l.id)).size, st.layers.length);
    const fonts = new Set(st.layers.flatMap(l => l.layout?.['text-font'] ?? []));
    for (const f of fonts) assert.ok(st['font-faces'][f], `${f} is declared`);
  }
});

test('Physical: the sea by depth at every zoom, under the land; the land tint transparent at sea level', () => {
  const st = physical(status), ids = st.layers.map(l => l.id);
  const sea = st.layers.find(l => l.id === 'sea-depth');
  assert.equal(sea.maxzoom, undefined, 'no fade-out zoom');
  assert.ok(ids.indexOf('sea-depth') < ids.indexOf('earth') && ids.indexOf('sea-shade') < ids.indexOf('earth'));
  const tint = st.layers.find(l => l.id === 'tint')['color-relief-color'] ?? st.layers.find(l => l.id === 'tint').paint['color-relief-color'];
  assert.deepEqual(tint.slice(3, 5), [0, 'rgba(224, 231, 204, 0)']);
  // darker with depth: each deeper stop has a lower luminance
  const L = h => parseInt(h.slice(1, 3), 16) + parseInt(h.slice(3, 5), 16) + parseInt(h.slice(5, 7), 16);
  for (let i = 1; i < PHYSICAL.depth.length; i++) assert.ok(L(PHYSICAL.depth[i - 1][1]) < L(PHYSICAL.depth[i][1]));
});

test('Physical draws no roads and no borders; Readable draws both', () => {
  const ids = st => st.layers.map(l => l['source-layer']);
  assert.ok(!ids(physical(status)).includes('roads') && !ids(physical(status)).includes('boundaries'));
  assert.ok(ids(readable(status)).includes('roads') && ids(readable(status)).includes('boundaries'));
});

test('Readable: only the trunk network, its casing exactly under it', () => {
  const st = readable(status), f = id => st.layers.find(l => l.id === id).filter;
  assert.deepEqual(f('roads'), f('roads-casing'));
  assert.ok(!JSON.stringify(f('roads')).includes('secondary'));
});

test('names in the chosen language, then English, then the local name; local = the local name', () => {
  const field = st => st.layers.find(l => l.id === 'sea-names').layout['text-field'];
  assert.deepEqual(field(readable(status, { lang: 'de' })), ['coalesce', ['get', 'name:de'], ['get', 'name:en'], ['get', 'name']]);
  assert.deepEqual(field(readable(status, { lang: 'local' })), ['get', 'name']);
});

test('the colours the study measured', () => {
  // WCAG relative-luminance contrast, the values quoted in docs/research-map-styles.md §1.3
  const lum = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255)
    .map(c => c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4).reduce((s, c, i) => s + c * [0.2126, 0.7152, 0.0722][i], 0);
  const cr = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return Math.round((x + 0.05) / (y + 0.05) * 10) / 10; };
  assert.equal(cr(READABLE.place, READABLE.land), 13.9);
  assert.equal(cr(READABLE.waterText, READABLE.water), 4.7);
  assert.ok(cr(READABLE.country, READABLE.land) >= 4.5);
  assert.ok(cr(PHYSICAL.place, '#e0e7cc') >= 4.5);
  assert.ok(cr(PHYSICAL.waterText, PHYSICAL.water) >= 4.5);
  // Physical's water names on the 1 000 m tint, and on their halo over the deep basins
  const depth = m => PHYSICAL.depth.find(([d]) => d === m)[1];
  assert.ok(cr(PHYSICAL.waterText, depth(-1000)) >= 4.5);
  assert.ok(cr(PHYSICAL.waterText, '#d6e9f6') >= 4.5);
});
