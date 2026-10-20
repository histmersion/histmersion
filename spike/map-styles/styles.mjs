// The two prototype styles of the map-style study (docs/research-map-styles.md), as MapLibre style documents
// over the server's own tiles. Every value here is a rule from the study; the comment beside it says which.
// Pure: (the server's /api/basemap answer, options) → a style object. Not shipped.

// ── fonts ────────────────────────────────────────────────────────────────────────────────────────────
// Noto Sans: one design across Latin, Greek and Cyrillic (and Noto companions for Hebrew, Arabic, Armenian,
// Georgian, Syriac, Coptic…), the face OSM Carto, Protomaps and OpenFreeMap label with, sans because at
// 10–13 px on screen a sans reads better than a serif (OSM Carto fonts.mss; Axis Maps' labelling guide).
// Loaded by MapLibre itself (`font-faces`, rasterised to SDF in the page): no glyph server, no other host.
const SUBSETS = [
  ['latin', 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD'],
  ['latin-ext', 'U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF'],
  ['greek', 'U+0370-0377,U+037A-037F,U+0384-038A,U+038C,U+038E-03A1,U+03A3-03FF'],
  ['greek-ext', 'U+1F00-1FFF'],
  ['cyrillic', 'U+0301,U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116'],
  ['cyrillic-ext', 'U+0460-052F,U+1C80-1C8A,U+20B4,U+2DE0-2DFF,U+A640-A69F,U+FE2E-FE2F'],
];
// the companions the server already serves (Noto Serif ones today; Noto Sans Hebrew/Arabic/… in production)
const COMPANIONS = [
  ['/web/vendor/noto-fonts/noto-serif-hebrew-hebrew-400-normal.woff2', 'U+0590-05FF,U+FB1D-FB4F'],
  ['/web/vendor/noto-fonts/noto-naskh-arabic-arabic-400-normal.woff2', 'U+0600-06FF,U+0750-077F,U+08A0-08FF,U+FB50-FDFF,U+FE70-FEFF'],
  ['/web/vendor/noto-fonts/noto-serif-armenian-armenian-400-normal.woff2', 'U+0530-058F,U+FB13-FB17'],
  ['/web/vendor/noto-fonts/noto-serif-georgian-georgian-400-normal.woff2', 'U+10A0-10FF,U+2D00-2D2F'],
];
const face = (weight, style) => [
  ...SUBSETS.map(([s, r]) => ({ url: `/fonts/noto-sans-${s}-${weight}-${style}.woff2`, 'unicode-range': r.split(',') })),
  ...COMPANIONS.map(([url, r]) => ({ url, 'unicode-range': r.split(',') })),
];
export const FONT = { regular: 'Noto Sans Regular', medium: 'Noto Sans Medium', semibold: 'Noto Sans SemiBold', italic: 'Noto Sans Italic' };
const fontFaces = () => ({
  [FONT.regular]: face(400, 'normal'), [FONT.medium]: face(500, 'normal'),
  [FONT.semibold]: face(600, 'normal'), [FONT.italic]: face(400, 'italic'),
});

// ── zooms ────────────────────────────────────────────────────────────────────────────────────────────
// Every zoom below is written on the 256-pixel raster grid — the zooms OSM Carto's rules, Leaflet and the app
// use — and moved to MapLibre's 512-pixel grid (one less for the same scale) once, here.
const shiftExpr = e => {
  if (!Array.isArray(e)) return e;
  const isStep = e[0] === 'step', input = isStep ? e[1] : e[2];
  if ((isStep || e[0] === 'interpolate') && Array.isArray(input) && input[0] === 'zoom') {
    // ['step', input, base, z, v, …] · ['interpolate', how, input, z, v, …]
    const out = isStep ? ['step', input, shiftExpr(e[2])] : ['interpolate', e[1], input];
    for (let i = 3; i < e.length; i += 2) out.push(e[i] - 1, shiftExpr(e[i + 1]));
    return out;
  }
  return e.map(shiftExpr);
};
export function toGlZooms(style) {
  for (const l of style.layers) {
    if (l.minzoom !== undefined) l.minzoom = Math.max(0, l.minzoom - 1);
    if (l.maxzoom !== undefined) l.maxzoom = l.maxzoom - 1;
    for (const k of ['paint', 'layout']) if (l[k]) for (const [p, v] of Object.entries(l[k])) l[k][p] = shiftExpr(v);
    if (l.filter) l.filter = shiftExpr(l.filter);
  }
  return style;
}

// ── helpers ──────────────────────────────────────────────────────────────────────────────────────────
const z = (...stops) => ['interpolate', ['linear'], ['zoom'], ...stops];
const zexp = (base, ...stops) => ['interpolate', ['exponential', base], ['zoom'], ...stops];
const kinds = (...ks) => ['in', ['get', 'kind'], ['literal', ks]];
const name = lang => lang === 'local' ? ['get', 'name'] : ['coalesce', ['get', `name:${lang}`], ['get', 'name:en'], ['get', 'name']];

/** The sources: the extract and its focus regions, served by the Histmersion server (same origin). */
export function sources(status, { origin = '' } = {}) {
  const layer = id => status.layers.find(l => l.layer === id && l.present);
  const url = (id, v) => `${origin}/api/basemap/${id}/{z}/{x}/{y}?v=${v}`;
  const out = {};
  const v = layer('vector');
  if (v) out.osm = { type: 'vector', tiles: [url('vector', v.version)], minzoom: 0, maxzoom: v.maxZoom ?? 10,
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · <a href="https://protomaps.com">Protomaps</a> · <a href="https://www.naturalearthdata.com">Natural Earth</a>' };
  // the satellite land cover exists only to zoom 7 in the build: its own source, cut to that one layer by the
  // server (`?layers=`), overzoomed beyond — MapLibre would otherwise lose it at zoom 8
  if (v) out.lc = { type: 'vector', tiles: [url('vector', v.version) + '&layers=landcover:a'], minzoom: 0, maxzoom: 7 };
  const lb = layer('labels');
  if (lb) out.ne = { type: 'vector', tiles: [url('labels', lb.version)], minzoom: 0, maxzoom: lb.maxZoom ?? 8 };
  const t = layer('terrain');
  if (t) {
    // ONE elevation source to the deepest zoom kept anywhere (a focus region): the page's `hmdem://` protocol
    // answers a tile the server lacks (outside the focus regions) from its nearest ancestor, resampled in
    // metres (index.html) — so the relief is drawn once, never twice where a focus region overlaps
    const deepest = Math.max(t.maxZoom ?? 8, ...(t.focus ?? []).map(f => f.maxZoom));
    out.dem = { type: 'raster-dem', encoding: 'terrarium', tileSize: 512,
      tiles: [`hmdem://${t.maxZoom ?? 8}/${t.version}/{z}/{x}/{y}`], minzoom: 0, maxzoom: deepest,
      attribution: 'terrain © <a href="https://mapterhorn.com/attribution">Mapterhorn</a>' };
  }
  const s = layer('bathymetry');
  if (s) out.sea = { type: 'raster-dem', encoding: 'terrarium', tileSize: 256, tiles: [url('bathymetry', s.version)], minzoom: 0, maxzoom: s.maxZoom ?? 7 };
  return out;
}

/** The relief layers: one per elevation source (there is one). */
function demLayers(srcs, id, make) {
  return srcs.dem ? [make(id, 'dem', 0)] : [];
}

// ═════════════════════════════════════════════════════════════════════════════════════════════════════
// (a) READABLE — a general-purpose map in OSM Carto's tradition, without the modern clutter a map of the
// past does not need: Carto's land and water colours and its hierarchy of names; land cover pale; towns
// (the reader's orientation) kept; roads only the trunk network and from zoom 8, faint; modern borders
// faint; a light relief so hills read; no POIs, no buildings.
// ═════════════════════════════════════════════════════════════════════════════════════════════════════
export const READABLE = {
  land: '#f2efe9',          // OSM Carto @land-color
  water: '#aad3df',         // OSM Carto @water-color; rivers the same colour, so they join the lakes and sea
  waterText: '#2c5880',     // darker than OSM Carto's #4d80b3 (2.6:1 on its water): 4.7:1 on the water
  forest: '#add19e',        // OSM Carto @forest
  landTint: '#dfe6c9',      // grass and scrub at small scales: one pale tone, so land cover does not mottle
  grass: '#cdebb0',         // OSM Carto @grass
  scrub: '#c8d7ab',         // OSM Carto @scrub
  sand: '#f5e9c6',          // OSM Carto @sand
  desert: '#f0e4c4',        // satellite 'barren' (deserts) at small scales: Carto's sand, a shade deeper
  farmland: '#e8ecd0',      // satellite 'farmland' at small scales: Carto @farmland, a shade deeper (cultivated plains read)
  glacier: '#ddecec',       // OSM Carto @glacier
  builtUp: '#e0dfdf',       // OSM Carto @residential
  road: '#f6c48a',          // a muted Carto trunk/primary
  roadCasing: '#d3a873',
  border: '#8d618b',        // OSM Carto @admin-boundaries
  country: '#6a3f69',       // OSM Carto @country-labels (darken(#845283, 10%)): 7.2:1 on the land
  place: '#222222',         // OSM Carto @placenames: 13.9:1 on the land
  placeMinor: '#555555',   // 6.5:1 on the land
  physical: '#6b5440',      // ranges, deserts: a brown that reads as ground, 6.2:1 on the land
  halo: 'rgba(242, 239, 233, 0.85)',
};

// the trunk network: motorways, trunk and primary roads (and their links)
const TRUNK = ['any', ['==', ['get', 'kind'], 'highway'], ['all', ['==', ['get', 'kind'], 'major_road'],
  ['in', ['get', 'kind_detail'], ['literal', ['trunk', 'primary', 'trunk_link', 'primary_link']]]]];

export function readable(status, { lang = 'en', origin = '' } = {}) {
  const c = READABLE, srcs = sources(status, { origin }), N = name(lang);
  const has = k => !!srcs[k];
  const layers = [
    { id: 'background', type: 'background', paint: { 'background-color': c.water } },
    has('osm') && { id: 'earth', type: 'fill', source: 'osm', 'source-layer': 'earth', filter: ['==', ['geometry-type'], 'Polygon'],
      paint: { 'fill-color': c.land } },
    // land cover: satellite-derived classes to zoom 8 at half strength (Positron/Voyager keep it ≤ 0.4), then
    // OSM's own wood and grass at Carto's colours; no farmland (modern), built-up only as a pale grey
    has('osm') && { id: 'landcover', type: 'fill', source: 'lc', 'source-layer': 'landcover', maxzoom: 10,
      filter: kinds('forest', 'grassland', 'scrub', 'farmland', 'barren', 'glacier', 'urban_area'),
      paint: { 'fill-color': ['match', ['get', 'kind'], 'forest', c.forest, ['grassland', 'scrub'], c.landTint,
        'farmland', c.farmland, 'barren', c.desert, 'glacier', c.glacier, 'urban_area', c.builtUp, c.land],
        'fill-opacity': ['interpolate', ['linear'], ['zoom'], 0, ['match', ['get', 'kind'], 'barren', 0.8, 'farmland', 0.5, 0.35],
          8.5, ['match', ['get', 'kind'], 'barren', 0.8, 'farmland', 0.5, 0.45], 10, 0] } },
    has('osm') && { id: 'landuse', type: 'fill', source: 'osm', 'source-layer': 'landuse', minzoom: 8.5,
      filter: kinds('forest', 'wood', 'grassland', 'meadow', 'scrub', 'heath', 'sand', 'beach', 'glacier', 'residential', 'industrial', 'commercial'),
      paint: { 'fill-color': ['match', ['get', 'kind'], ['forest', 'wood'], c.forest, ['grassland', 'meadow'], c.grass,
        ['scrub', 'heath'], c.scrub, ['sand', 'beach'], c.sand, 'glacier', c.glacier, c.builtUp],
        'fill-opacity': z(8.5, 0, 10, 0.6, 13, 0.8) } },
    // relief: light, shadows only (no grey cast on flat land: Patterson), a little stronger when close in
    ...demLayers(srcs, 'hillshade', (id, src, minzoom) => ({ id, type: 'hillshade', source: src, minzoom, paint: {
        'hillshade-method': 'igor',
        'hillshade-shadow-color': 'rgba(70, 60, 50, 0.7)',
        'hillshade-highlight-color': 'rgba(255, 255, 255, 0)',
        'hillshade-accent-color': 'rgba(60, 50, 40, 0)',
        'hillshade-exaggeration': z(0, 0.2, 6, 0.22, 10, 0.3, 13, 0.35),
        'hillshade-illumination-direction': 315 } })),
    has('osm') && { id: 'water', type: 'fill', source: 'osm', 'source-layer': 'water', filter: ['all', ['==', ['geometry-type'], 'Polygon'],
      ['!', kinds('swimming_pool', 'fountain')]], paint: { 'fill-color': c.water } },
    // rivers: Carto's widths by zoom (0.7 px at 8 → 2.3 at 12), the water's colour; Natural Earth's
    // major rivers before OSM's lines exist
    has('ne') && { id: 'rivers-ne', type: 'line', source: 'ne', 'source-layer': 'rivers', maxzoom: 9,
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.water, 'line-width': z(3, 0.4, 6, 0.8, 9, 1.2) } },
    has('osm') && { id: 'rivers', type: 'line', source: 'osm', 'source-layer': 'water', minzoom: 8,
      filter: ['all', ['==', ['geometry-type'], 'LineString'], kinds('river', 'canal')],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.water, 'line-width': zexp(1.4, 8, 0.7, 9, 1.2, 10, 1.5, 12, 2.3, 14, 4.5) } },
    has('osm') && { id: 'streams', type: 'line', source: 'osm', 'source-layer': 'water', minzoom: 12,
      filter: ['all', ['==', ['geometry-type'], 'LineString'], kinds('stream')],
      paint: { 'line-color': c.water, 'line-width': z(12, 0.8, 14, 2) } },
    // the trunk network, faint and thin, from zoom 8 (orientation; the corpus's own lines stay on top)
    has('osm') && { id: 'roads-casing', type: 'line', source: 'osm', 'source-layer': 'roads', minzoom: 9,
      filter: TRUNK,
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.roadCasing, 'line-opacity': 0.5,
        'line-width': z(9, ['match', ['get', 'kind'], 'highway', 1.6, 1.0], 12, ['match', ['get', 'kind'], 'highway', 3.2, 2.4]) } },
    has('osm') && { id: 'roads', type: 'line', source: 'osm', 'source-layer': 'roads', minzoom: 8,
      filter: TRUNK,
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.road, 'line-opacity': z(8, 0.45, 10, 0.7),
        'line-width': z(8, ['match', ['get', 'kind'], 'highway', 0.6, 0.4], 12, ['match', ['get', 'kind'], 'highway', 2, 1.4]) } },
    // modern borders: present (they are how a reader finds a place) but quiet — Carto's purple, half strength
    has('osm') && { id: 'border-country', type: 'line', source: 'osm', 'source-layer': 'boundaries',
      filter: ['all', ['==', ['get', 'kind'], 'country'], ['!=', ['get', 'maritime'], 1]],
      layout: { 'line-join': 'round' },
      paint: { 'line-color': c.border, 'line-opacity': 0.45, 'line-width': z(3, 0.6, 8, 1.2, 12, 2), 'line-dasharray': [4, 2] } },

    // ── names, in priority order (MapLibre places them across the whole view, never per tile) ──
    has('osm') && { id: 'sea-names', type: 'symbol', source: 'osm', 'source-layer': 'water',
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('ocean', 'sea')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(3, 11, 6, 13, 9, 16),
        'text-letter-spacing': 0.08, 'text-max-width': 8, 'text-line-height': 1.15 },
      paint: { 'text-color': c.waterText, 'text-halo-color': 'rgba(170, 211, 223, 0.8)', 'text-halo-width': 1 } },
    has('osm') && { id: 'country-names', type: 'symbol', source: 'osm', 'source-layer': 'places', maxzoom: 8,
      filter: kinds('country'),
      layout: { 'text-field': N, 'text-font': [FONT.medium], 'text-transform': 'uppercase',
        'text-size': z(3, 10, 5, 12, 7, 14), 'text-letter-spacing': 0.12, 'text-max-width': 7,
        'symbol-sort-key': ['get', 'min_zoom'] },
      paint: { 'text-color': c.country, 'text-halo-color': c.halo, 'text-halo-width': 1.5 } },
    has('ne') && { id: 'ranges', type: 'symbol', source: 'ne', 'source-layer': 'physical', minzoom: 5,
      filter: kinds('range', 'desert', 'plateau', 'peninsula', 'plain', 'delta'),
      layout: { 'text-field': N, 'text-font': [FONT.regular], 'text-transform': 'uppercase',
        'text-size': z(5, 10, 9, 12), 'text-letter-spacing': 0.25, 'text-max-width': 9,
        'symbol-sort-key': ['get', 'min_zoom'] },
      paint: { 'text-color': c.physical, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('osm') && { id: 'cities', type: 'symbol', source: 'osm', 'source-layer': 'places',
      filter: ['all', kinds('locality'), ['in', ['get', 'kind_detail'], ['literal', ['city', 'town']]],
        ['<=', ['get', 'min_zoom'], ['+', ['zoom'], 0.5]]],
      layout: { 'text-field': N, 'text-font': [FONT.medium],
        'text-size': ['interpolate', ['linear'], ['zoom'], 5, ['case', ['>=', ['get', 'population_rank'], 11], 12, 10],
          10, ['case', ['>=', ['get', 'population_rank'], 11], 16, ['>=', ['get', 'population_rank'], 9], 13, 11.5]],
        'text-max-width': 8, 'symbol-sort-key': ['-', 20, ['get', 'population_rank']], 'text-padding': 3 },
      paint: { 'text-color': c.place, 'text-halo-color': c.halo, 'text-halo-width': 1.5 } },
    has('osm') && { id: 'villages', type: 'symbol', source: 'osm', 'source-layer': 'places', minzoom: 10,
      filter: ['all', kinds('locality'), ['in', ['get', 'kind_detail'], ['literal', ['village', 'hamlet']]]],
      layout: { 'text-field': N, 'text-font': [FONT.regular], 'text-size': z(10, 10, 13, 12), 'text-max-width': 8,
        'symbol-sort-key': ['-', 20, ['get', 'population_rank']] },
      paint: { 'text-color': c.placeMinor, 'text-halo-color': c.halo, 'text-halo-width': 1.5 } },
    has('osm') && { id: 'gulf-names', type: 'symbol', source: 'osm', 'source-layer': 'water', minzoom: 6,
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('bay', 'strait', 'lake')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(6, 10, 10, 12), 'text-max-width': 7 },
      paint: { 'text-color': c.waterText, 'text-halo-color': 'rgba(170, 211, 223, 0.8)', 'text-halo-width': 1 } },
    has('ne') && { id: 'river-names', type: 'symbol', source: 'ne', 'source-layer': 'rivers', minzoom: 6,
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 11, 'symbol-placement': 'line',
        'text-letter-spacing': 0.05, 'symbol-spacing': 400 },
      paint: { 'text-color': c.waterText, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('osm') && { id: 'island-names', type: 'symbol', source: 'osm', 'source-layer': 'earth', minzoom: 8,
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('island')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(8, 10, 12, 12), 'text-max-width': 7 },
      paint: { 'text-color': '#4a4a4a', 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('ne') && { id: 'peak-names', type: 'symbol', source: 'ne', 'source-layer': 'physical', minzoom: 8,
      filter: kinds('peak'),
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 10, 'text-max-width': 7 },
      paint: { 'text-color': c.physical, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
  ].filter(Boolean);
  return toGlZooms({ version: 8, name: 'Readable (study prototype)', sources: srcs, layers, 'font-faces': fontFaces() });
}

// ═════════════════════════════════════════════════════════════════════════════════════════════════════
// (b) PHYSICAL — Patterson / Esri Physical / Stamen Terrain: the land's form first. Restrained hypsometric
// tints ("higher is lighter" above the lowlands, < 25 % saturation), soft multidirectional shading that
// leaves flat ground untouched, forest as a faint green veil, the sea by depth, rivers a darker blue than
// the sea so they read on tinted land, physical names first; towns small and grey for orientation; no
// roads, no borders.
// ═════════════════════════════════════════════════════════════════════════════════════════════════════
export const PHYSICAL = {
  // elevation (m) → colour: lowland a pale sage-khaki (Patterson's arid/temperate cross-blend for the
  // Mediterranean), warming to tan, then lighter grey-white above 2 500 m — all lightness ≥ 78 %
  tint: [[-450, '#dfe6cc'], [0, '#e0e7cc'], [150, '#e7ead0'], [400, '#eeead0'], [800, '#ede1c4'], [1300, '#e4d3bb'],
    [1900, '#ddd0c2'], [2600, '#e6e0d9'], [3500, '#f2efeb'], [5000, '#fbfaf8']],
  // depth (m) → colour: the shelf pale, the abyss a muted mid blue (no saturated navy)
  depth: [[-5000, '#7ca5c7'], [-3000, '#89afcf'], [-2000, '#95b9d6'], [-1000, '#a3c5de'], [-200, '#b3d1e7'], [-50, '#bfd9ec'], [-1, '#c6deef']],
  water: '#bad6ea',
  river: '#6f9fc4',
  coast: '#7ea8c6',
  forest: '#5e8a4e',
  waterText: '#2c5880',     // 4.1:1 on the sea at 1 000 m depth, 4.9:1 on the shelf
  place: '#3d3d3d',         // 8.5:1 on the lowland tint
  physical: '#5a4632',      // 6.9:1 on the 800 m tint
  halo: 'rgba(245, 243, 234, 0.85)',
};

export function physical(status, { lang = 'en', origin = '', places = true, contours = true } = {}) {
  const c = PHYSICAL, srcs = sources(status, { origin }), N = name(lang);
  const has = k => !!srcs[k];
  const ramp = stops => ['interpolate', ['linear'], ['elevation'], ...stops.flat()];
  const layers = [
    { id: 'background', type: 'background', paint: { 'background-color': c.water } },
    // the land: its mask from the vector earth (crisp coast), then the tint by elevation over it
    has('osm') && { id: 'earth', type: 'fill', source: 'osm', 'source-layer': 'earth', filter: ['==', ['geometry-type'], 'Polygon'],
      paint: { 'fill-color': c.tint[1][1] } },
    ...demLayers(srcs, 'tint', (id, src, minzoom) => ({ id, type: 'color-relief', source: src, minzoom,
      paint: { 'color-relief-color': ramp(c.tint), 'color-relief-opacity': 1 } })),
    // forest: a faint veil, never a blotch (satellite classes to 8, OSM wood beyond) — opacity ≤ 0.22
    has('osm') && { id: 'forest-lc', type: 'fill', source: 'lc', 'source-layer': 'landcover', maxzoom: 9,
      filter: kinds('forest'), paint: { 'fill-color': c.forest, 'fill-opacity': z(0, 0.12, 8, 0.15, 9, 0) } },
    has('osm') && { id: 'forest', type: 'fill', source: 'osm', 'source-layer': 'landuse', minzoom: 8.5,
      filter: kinds('forest', 'wood'), paint: { 'fill-color': c.forest, 'fill-opacity': z(8.5, 0, 9.5, 0.2, 13, 0.22) } },
    // deserts against green cultivation (Natural Earth II's "natural colours": the Nile must read in Egypt):
    // the satellite classes, faint
    has('osm') && { id: 'sand', type: 'fill', source: 'lc', 'source-layer': 'landcover',
      filter: kinds('barren'), paint: { 'fill-color': '#ead9ae', 'fill-opacity': 0.55 } },
    has('osm') && { id: 'green-lc', type: 'fill', source: 'lc', 'source-layer': 'landcover', maxzoom: 11,
      filter: kinds('farmland', 'grassland'), paint: { 'fill-color': '#b8cc94', 'fill-opacity': z(0, 0.22, 10, 0.18, 11, 0) } },
    has('osm') && { id: 'glacier', type: 'fill', source: 'lc', 'source-layer': 'landcover',
      filter: kinds('glacier'), paint: { 'fill-color': '#ffffff', 'fill-opacity': 0.8 } },
    // the relief: multidirectional (every slope reads, Jenny/Imhof), warm shadows, no highlight on flats
    ...demLayers(srcs, 'relief', (id, src, minzoom) => ({ id, type: 'hillshade', source: src, minzoom, paint: {
      'hillshade-method': 'multidirectional',
      'hillshade-highlight-color': ['literal', ['rgba(255, 253, 245, 0.35)', 'rgba(255, 253, 245, 0.15)', 'rgba(255, 253, 245, 0.15)', 'rgba(255, 253, 245, 0.1)']],
      'hillshade-shadow-color': ['literal', ['rgba(80, 66, 52, 0.42)', 'rgba(80, 66, 52, 0.22)', 'rgba(80, 66, 52, 0.22)', 'rgba(80, 66, 52, 0.15)']],
      'hillshade-illumination-direction': ['literal', [315, 270, 0, 225]],
      'hillshade-illumination-altitude': ['literal', [40, 35, 35, 30]],
      'hillshade-exaggeration': z(0, 0.5, 6, 0.5, 10, 0.45, 13, 0.4) } })),
    // the sea: the vector ocean (exact coast), then its depth over it (ETOPO1 via the sea-floor layer: land in
    // it transparent), faded out by zoom 9 as its 1.8 km cells start to spill across the coast; then lakes
    has('osm') && { id: 'ocean', type: 'fill', source: 'osm', 'source-layer': 'water',
      filter: ['all', ['==', ['geometry-type'], 'Polygon'], kinds('ocean')], paint: { 'fill-color': c.water } },
    has('sea') && { id: 'sea-depth', type: 'color-relief', source: 'sea', maxzoom: 9,
      paint: { 'color-relief-color': ['interpolate', ['linear'], ['elevation'], ...c.depth.flat(), 0, 'rgba(0,0,0,0)'],
        'color-relief-opacity': z(5, 1, 7, 0.9, 8.5, 0) } },
    has('sea') && { id: 'sea-shade', type: 'hillshade', source: 'sea', maxzoom: 9,
      paint: { 'hillshade-method': 'igor', 'hillshade-shadow-color': 'rgba(40, 70, 100, 0.6)', 'hillshade-highlight-color': 'rgba(0,0,0,0)',
        'hillshade-exaggeration': z(3, 0.2, 7, 0.15, 8.5, 0) } },
    has('osm') && { id: 'water', type: 'fill', source: 'osm', 'source-layer': 'water',
      filter: ['all', ['==', ['geometry-type'], 'Polygon'], ['!', kinds('ocean', 'swimming_pool', 'fountain')]],
      paint: { 'fill-color': c.water } },
    has('osm') && { id: 'coast', type: 'line', source: 'osm', 'source-layer': 'water',
      filter: ['all', ['==', ['geometry-type'], 'Polygon'], kinds('ocean', 'water', 'lake')],
      paint: { 'line-color': c.coast, 'line-width': z(4, 0.4, 10, 0.7, 14, 1), 'line-opacity': 0.7 } },
    // rivers darker than the sea (they must read on tinted land, Esri Physical / Patterson), tapering by zoom
    has('ne') && { id: 'rivers-ne', type: 'line', source: 'ne', 'source-layer': 'rivers', maxzoom: 9.5,
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.river, 'line-width': z(3, 0.5, 6, 0.9, 9, 1.4) } },
    has('osm') && { id: 'rivers', type: 'line', source: 'osm', 'source-layer': 'water', minzoom: 9,
      filter: ['all', ['==', ['geometry-type'], 'LineString'], kinds('river', 'canal')],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': c.river, 'line-width': zexp(1.4, 9, 0.9, 12, 2, 14, 3.5) } },
    has('osm') && { id: 'streams', type: 'line', source: 'osm', 'source-layer': 'water', minzoom: 11,
      filter: ['all', ['==', ['geometry-type'], 'LineString'], kinds('stream')],
      paint: { 'line-color': c.river, 'line-opacity': 0.7, 'line-width': z(11, 0.4, 14, 1.2) } },
    // ── names: the physical ones first ──
    has('osm') && { id: 'sea-names', type: 'symbol', source: 'osm', 'source-layer': 'water',
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('ocean', 'sea')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(3, 11, 6, 13.5, 9, 16),
        'text-letter-spacing': 0.1, 'text-max-width': 8 },
      paint: { 'text-color': c.waterText, 'text-halo-color': 'rgba(200, 222, 236, 0.7)', 'text-halo-width': 1 } },
    has('ne') && { id: 'ranges', type: 'symbol', source: 'ne', 'source-layer': 'physical',
      filter: kinds('range', 'desert', 'plateau', 'peninsula', 'plain', 'delta', 'basin', 'valley', 'region'),
      layout: { 'text-field': N, 'text-font': [FONT.medium], 'text-transform': 'uppercase',
        'text-size': z(4, 10, 8, 12.5), 'text-letter-spacing': 0.3, 'text-max-width': 10,
        'symbol-sort-key': ['get', 'min_zoom'] },
      paint: { 'text-color': c.physical, 'text-halo-color': c.halo, 'text-halo-width': 1.3 } },
    has('ne') && { id: 'lake-names', type: 'symbol', source: 'ne', 'source-layer': 'physical',
      filter: kinds('lake'),
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 11, 'text-max-width': 7 },
      paint: { 'text-color': c.waterText, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('osm') && { id: 'gulf-names', type: 'symbol', source: 'osm', 'source-layer': 'water', minzoom: 6,
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('bay', 'strait', 'lake')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(6, 10.5, 10, 12), 'text-max-width': 7 },
      paint: { 'text-color': c.waterText, 'text-halo-color': 'rgba(200, 222, 236, 0.7)', 'text-halo-width': 1 } },
    has('ne') && { id: 'river-names', type: 'symbol', source: 'ne', 'source-layer': 'rivers', minzoom: 5,
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 11, 'symbol-placement': 'line',
        'text-letter-spacing': 0.06, 'symbol-spacing': 350 },
      paint: { 'text-color': c.waterText, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('ne') && { id: 'island-names', type: 'symbol', source: 'ne', 'source-layer': 'physical',
      filter: kinds('island', 'island_group'),
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 11, 'text-max-width': 7 },
      paint: { 'text-color': '#45403a', 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('ne') && { id: 'peak-names', type: 'symbol', source: 'ne', 'source-layer': 'physical', minzoom: 7,
      filter: kinds('peak', 'pass'),
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': 10.5, 'text-max-width': 8 },
      paint: { 'text-color': c.physical, 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    has('osm') && { id: 'island-names-osm', type: 'symbol', source: 'osm', 'source-layer': 'earth', minzoom: 9,
      filter: ['all', ['==', ['geometry-type'], 'Point'], kinds('island')],
      layout: { 'text-field': N, 'text-font': [FONT.italic], 'text-size': z(9, 10, 12, 11.5), 'text-max-width': 7 },
      paint: { 'text-color': '#45403a', 'text-halo-color': c.halo, 'text-halo-width': 1.2 } },
    places && has('osm') && { id: 'towns', type: 'symbol', source: 'osm', 'source-layer': 'places', minzoom: 5,
      filter: ['all', kinds('locality'), ['in', ['get', 'kind_detail'], ['literal', ['city', 'town', 'village']]],
        ['<=', ['get', 'min_zoom'], ['-', ['zoom'], 0.5]]],
      layout: { 'text-field': N, 'text-font': [FONT.regular],
        'text-size': ['interpolate', ['linear'], ['zoom'], 5, 10, 10, ['case', ['>=', ['get', 'population_rank'], 11], 13, 11]],
        'text-max-width': 8, 'symbol-sort-key': ['-', 20, ['get', 'population_rank']], 'text-padding': 4 },
      paint: { 'text-color': c.place, 'text-halo-color': c.halo, 'text-halo-width': 1.5 } },
  ].filter(Boolean);
  return toGlZooms({ version: 8, name: 'Physical (study prototype)', sources: srcs, layers, 'font-faces': fontFaces(), contours });
}
