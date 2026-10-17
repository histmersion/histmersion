// Live-sized, synthetic feeds for the speed and memory budgets (#1949): the test database is empty,
// so the map is fed what the real corpus feeds it — 500 findings, a page of sources and their cards,
// 2 400 events, a year's ~140 polities and a whole range's 1 600, articles — from a fixed seed, so every
// run measures the same work. Nothing here is corpus data; every name starts with "Zz".

/** A small deterministic generator (mulberry32). */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const span = (r, lo = -3500, hi = -500) => { const a = Math.round(lo + r() * (hi - lo)); return [a, a + Math.round(r() * 300)]; };

export const findings = (() => {
  const r = rng(1);
  return Array.from({ length: 500 }, (_, i) => {
    const [a, b] = span(r);
    return { citekey: `zz_src_${i % 40}`, title: `Zz source ${i % 40}`, claim: `Zz claim number ${i} about a place and a time`,
      regionName: 'Zz region', lon: 10 + r() * 40, lat: 30 + r() * 25, periodStart: a, periodEnd: b,
      confidence: ['high', 'medium', 'low', 'contested'][i % 4], sourceKind: 'article' };
  });
})();

const sourceOf = i => ({ citekey: `zz_src_${i}`, title: `Zz source ${i}: a study of something old`, year: 1990 + (i % 30),
  status: 'current', topics: ['zz_topic'], regions: ['Zz region'], periodStart: -3000 + i * 10, periodEnd: -1000,
  summary: `Zz summary of source ${i}. `.repeat(8), snippet: null, matchedHeading: null, doi: null });

export const sources = { total: 40, hits: Array.from({ length: 20 }, (_, i) => ({ source: sourceOf(i), matched: [], why: [] })),
  offset: 0, next: 20 };

export const sourceCard = ck => {
  const i = Number(ck.replace(/\D/g, '')) || 0;
  const s = sourceOf(i);
  return { source: { citekey: s.citekey, title: s.title, authors: ['Zz Author'], year: s.year, venue: 'Zz Journal',
    status: 'current', topics: s.topics, regions: s.regions, tags: [], periodStart: s.periodStart, periodEnd: s.periodEnd,
    summary: s.summary, doi: null, ocrMarkdown: null, notes: null, updatedAt: '2026-10-01T00:00:00Z', provenance: 'oa',
    uid: `00000000-0000-0000-0000-${String(i).padStart(12, '0')}`, origin: null },
  findings: findings.filter(f => f.citekey === ck).slice(0, 5).map(f => ({ claim: f.claim, regionName: f.regionName,
    lon: f.lon, lat: f.lat, periodStart: f.periodStart, periodEnd: f.periodEnd, periodName: null, tags: [],
    confidence: f.confidence, significance: null, uid: null })),
  formatting: [], illustration: null };
};

export const events = (() => {
  const r = rng(2);
  const kinds = ['battle', 'siege', 'treaty', 'founding', 'eruption', 'earthquake'];
  return { unplaced: 0, events: Array.from({ length: 2400 }, (_, i) => {
    const [a] = span(r, -3000, 1500);
    return { id: `zz_ev_${i}`, label: `Zz event ${i}`, kind: kinds[i % kinds.length], scale: 'local', historicity: 'attested',
      tier: i % 5 ? 'attested' : 'catalogue', timeSpan: { notBefore: a, notAfter: a }, lat: -40 + r() * 100, lon: -120 + r() * 260,
      anchor: 'event' };
  }) };
})();

/** `n` polygons of `vertices` points each, scattered over the Old World. */
function polities(n, vertices, seed) {
  const r = rng(seed);
  return { type: 'FeatureCollection', features: Array.from({ length: n }, (_, i) => {
    const cx = -10 + r() * 120, cy = 5 + r() * 55, rad = 0.5 + r() * 4;
    const ring = Array.from({ length: vertices }, (_, k) => {
      const t = 2 * Math.PI * k / vertices, w = rad * (0.7 + 0.3 * Math.sin(5 * t + i));
      return [cx + w * Math.cos(t), cy + w * Math.sin(t)];
    });
    ring.push(ring[0]);
    const [a, b] = span(r, -3000, 1900);
    return { type: 'Feature', geometry: { type: 'Polygon', coordinates: [ring] },
      properties: { id: `zz_pol_${i}`, label: `Zz realm ${i}`, kind: 'polity', periodStart: a, periodEnd: b,
        hasBoundary: true, dating: 'stated', basis: 'dated' } };
  }) };
}

/** A year's states, and a whole range's ("All states in period"): the live corpus has ~140 and ~1 600. */
export const politiesAtYear = polities(140, 120, 3);
export const politiesOverRange = polities(1600, 120, 4);

export const articles = { total: 30, articles: Array.from({ length: 30 }, (_, i) => ({ slug: `zz_art_${i}`,
  title: `Zz article ${i}`, kind: 'concept', summary: 'Zz summary', tags: [], regions: [], periodStart: null, periodEnd: null })) };

export const articleHtml = slug => `<article><h1>Zz ${slug}</h1>${'<p>Zz paragraph of the article body. </p>'.repeat(200)}</article>`;

/** A source window's page: its title, and a link to itself (with a parameter) — following it reloads
  * the window's content in place. */
export const sourceHtml = ck => `<article><h1>Zz ${ck}</h1><p><a class="zz-self" href="/source/${ck}?hl=zz">again</a></p>` +
  `${'<p>Zz text. </p>'.repeat(50)}</article>`;

export const facets = prefix => Array.from({ length: 12 }, (_, i) => ({ value: `${prefix}zz_tag_${i}`, count: 12 - i }));

/** Every fixture on its route. A real server answers the rest (config, aggregates, the page). */
export async function routeFixtures(page) {
  const json = (route, body) => route.fulfill({ json: body });
  await page.route(/\/api\/findings(\?|$)/, r => json(r, findings));
  await page.route(/\/api\/sources(\?|$)/, r => json(r, sources));
  await page.route(/\/api\/sources\/zz_src_\d+(\?|$)/, r => json(r, sourceCard(new URL(r.request().url()).pathname.split('/').pop())));
  await page.route(/\/api\/events\/geo(\?|$)/, r => json(r, events));
  await page.route(/\/api\/places\/geo(\?|$)/, r => {
    const q = new URL(r.request().url()).searchParams;
    if (q.get('kind') !== 'polity') return json(r, { type: 'FeatureCollection', features: [] });
    return json(r, q.get('pick') && q.get('from') ? politiesOverRange : politiesAtYear);
  });
  await page.route(/\/api\/articles(\?|$)/, r => json(r, articles));
  await page.route(/\/article\/zz_art_\d+/, r => r.fulfill({ body: articleHtml(new URL(r.request().url()).pathname.split('/').pop()), contentType: 'text/html' }));
  await page.route(/\/source\/zz_src_\d+/, r => r.fulfill({ body: sourceHtml(new URL(r.request().url()).pathname.split('/').pop()), contentType: 'text/html' }));
  await page.route(/\/api\/facets(\?|$)/, r => json(r, facets(new URL(r.request().url()).searchParams.get('prefix') ?? '')));
}
