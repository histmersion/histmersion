// The map's tiled layers (`/api/tiles/{layer}/{z}/{x}/{y}`, docs/map-layers.md) served from fixture
// records in the browser tests: each record in the tile its point falls in (Web Mercator, as the
// server cuts them), dated records only where their dates overlap the span asked for, each layer's
// menus applied as the server applies them — every record a feature of its own (the server's cells of
// a crowded tile are its own suites'). Encoded as Mapbox vector tiles, the format MapLibre reads.

const enc = new TextEncoder();

function varint(out, v) {
  let n = BigInt.asUintN(64, BigInt(v));
  while (n > 0x7fn) { out.push(Number((n & 0x7fn) | 0x80n)); n >>= 7n; }
  out.push(Number(n));
}
const zigzag = n => (BigInt(n) << 1n) ^ (BigInt(n) >> 63n);
function message(out, field, bytes) { varint(out, (field << 3) | 2); varint(out, bytes.length); out.push(...bytes); }

/** A Mapbox vector tile of one layer of point features (`[x, y, props]`, tile units of 4096). */
export function encodeTile(name, features) {
  if (!features.length) return new Uint8Array();
  const keys = new Map(), values = new Map(), layer = [];
  varint(layer, (15 << 3)); varint(layer, 2);
  message(layer, 1, enc.encode(name));
  for (const [x, y, props] of features) {
    const tags = [], geom = [], f = [];
    for (const [k, v] of Object.entries(props)) {
      if (v === null || v === undefined) continue;
      if (!keys.has(k)) keys.set(k, keys.size);
      const vk = `${typeof v}:${v}`;
      if (!values.has(vk)) values.set(vk, [values.size, v]);
      varint(tags, keys.get(k)); varint(tags, values.get(vk)[0]);
    }
    varint(geom, 9); varint(geom, zigzag(x)); varint(geom, zigzag(y));
    message(f, 2, tags); varint(f, 3 << 3); varint(f, 1); message(f, 4, geom);
    message(layer, 2, f);
  }
  for (const k of keys.keys()) message(layer, 3, enc.encode(k));
  for (const [, v] of values.values()) {
    const b = [];
    if (typeof v === 'string') message(b, 1, enc.encode(v));
    else if (typeof v === 'boolean') { varint(b, 7 << 3); varint(b, v ? 1 : 0); }
    else if (Number.isInteger(v)) { varint(b, 6 << 3); varint(b, zigzag(v)); }
    else { varint(b, (3 << 3) | 1); const d = new DataView(new ArrayBuffer(8)); d.setFloat64(0, v, true); b.push(...new Uint8Array(d.buffer)); }
    message(layer, 4, b);
  }
  varint(layer, 5 << 3); varint(layer, 4096);
  const out = [];
  message(out, 3, layer);
  return new Uint8Array(out);
}

/** Where (`lat`, `lon`) falls in tile `z`/`x`/`y`, in tile units; `null` outside it. */
export function unitsIn(z, x, y, lat, lon) {
  const l = Math.max(-85.0511287798, Math.min(85.0511287798, lat)) * Math.PI / 180;
  const n = 2 ** z;
  const px = (Math.min(Math.max((lon + 180) / 360, 0), 0.99999999999) * n - x) * 4096;
  const py = (Math.min(Math.max((1 - Math.log(Math.tan(l) + 1 / Math.cos(l)) / Math.PI) / 2, 0), 0.99999999999) * n - y) * 4096;
  return px >= 0 && px < 4096 && py >= 0 && py < 4096 ? [Math.floor(px), Math.floor(py)] : null;
}

const overlaps = (a, b, lo, hi) => lo === null || ((a ?? -Infinity) <= hi && (b ?? Infinity) >= lo);

/** A site of the places feed (a GeoJSON point feature) as a sites tile carries it. */
const site = f => ({ at: [f.geometry.coordinates[1], f.geometry.coordinates[0]], a: f.properties.periodStart, b: f.properties.periodEnd,
  props: { k: f.properties.id, id: f.properties.id, label: f.properties.label, kind: f.properties.kind ?? 'site',
    siteType: f.properties.siteType, basis: f.properties.basis ?? 'dated', periodStart: f.properties.periodStart, periodEnd: f.properties.periodEnd,
    ...(f.properties.undated ? { u: 1 } : {}) }, undated: !!f.properties.undated });
/** An event of the events feed (`EventGeo`). */
const event = e => {
  const ts = e.timeSpan ?? {};
  return { at: [e.lat, e.lon], a: ts.notBefore ?? ts.beginBy, b: ts.notAfter ?? ts.endFrom ?? ts.notBefore ?? ts.beginBy,
    props: { k: e.id, id: e.id, label: e.label, kind: e.kind, scale: e.scale, historicity: e.historicity, notBefore: ts.notBefore,
      beginBy: ts.beginBy, endFrom: ts.endFrom, notAfter: ts.notAfter, anchor: e.anchor, placeId: e.placeId, placeLabel: e.placeLabel,
      parentId: e.parentId, tier: e.tier, size: e.size, precision: e.precision, dating: e.dating } };
};
/** A picture of the media feed (`MediaGeoHit`). */
const picture = h => ({ at: [h.lat, h.lon], a: h.periodStart, b: h.periodEnd,
  props: { k: h.uid || `${h.assetName}|${h.lat}|${h.lon}`, uid: h.uid || `${h.assetName}|${h.lat}|${h.lon}`, citekey: h.citekey,
    assetName: h.assetName, kind: h.kind, periodStart: h.periodStart, periodEnd: h.periodEnd, caption: h.caption, provider: h.provider,
    license: h.license, credit: h.credit, thumbUrl: h.thumbUrl, sourceUrl: h.sourceUrl, licenseUrl: h.licenseUrl, author: h.author } });

/** Serve the tiled layers from `sites` (GeoJSON point features), `events` (`EventGeo`s) and `media`
  * (`MediaGeoHit`s) — a function of the request's URL each, or an array. `asked` collects every tile URL. */
export async function routeTiles(page, { sites = [], events = [], media = [], asked = null } = {}) {
  await page.route(/\/api\/tiles\//, route => {
    const u = new URL(route.request().url());
    asked?.push(u);
    const [, , , layer, z, x, y] = u.pathname.split('/');
    const from = u.searchParams.has('from') ? Number(u.searchParams.get('from')) : null;
    const to = u.searchParams.has('to') ? Number(u.searchParams.get('to')) : from;
    const lo = from === null ? null : Math.min(from, to), hi = from === null ? null : Math.max(from, to);
    const of = v => (typeof v === 'function' ? v(u) : v);
    let items = [];
    // a site of no known date (`properties.undated`) only when asked for (`undated=true`), whatever the span
    if (layer === 'sites') items = of(sites).filter(f => (!u.searchParams.get('siteType') || f.properties.siteType === u.searchParams.get('siteType'))
      && (!f.properties.undated || u.searchParams.get('undated') === 'true')).map(site);
    if (layer === 'events') items = of(events).filter(e => !u.searchParams.get('tier') || e.tier === u.searchParams.get('tier')).map(event);
    if (layer === 'media') items = of(media).filter(h => (!u.searchParams.get('provider') || h.provider === u.searchParams.get('provider'))
      && (!u.searchParams.get('license') || h.license === u.searchParams.get('license'))).map(picture);
    const features = items.filter(i => overlaps(i.a, i.b, lo, hi)).flatMap(i => {
      const p = unitsIn(Number(z), Number(x), Number(y), i.at[0], i.at[1]);
      return p ? [[p[0], p[1], { z: Number(z), ...i.props }]] : [];
    });
    return route.fulfill({ status: 200, contentType: 'application/vnd.mapbox-vector-tile', body: Buffer.from(encodeTile(layer, features)) });
  });
}
