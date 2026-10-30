// Library at the live corpus's size (~1 000 sources, a few dozen articles): the lists answered a page at a
// time from synthetic rows, as the server pages them, and each item's record, citation edges and citing
// articles. Nothing here is corpus data; every name starts with "Zz". `routeLibrary` records what the page
// asked for (the lists' parameters), so a test can check the filter, the order and the page it sent.

export const LIBRARY_SOURCES = 1026;
export const LIBRARY_ARTICLES = 38;

const statuses = ['current', 'foundational', 'contested', 'superseded', 'current', 'unassessed'];

export const libSource = i => ({ citekey: `zz_lib_${i}`, title: `Zz library source ${i}: a study of the long past`,
  year: 1950 + (i % 75), status: statuses[i % statuses.length], topics: ['zz_topic'], regions: ['zz_region'],
  periodStart: -3000 + (i % 40) * 50, periodEnd: -1000, summary: `Zz summary of library source ${i}. `.repeat(4),
  snippet: null, matchedHeading: null, doi: null, authors: i % 4 ? ['Zz Author', 'Zz Second'] : ['A', 'B', 'C', 'D'] });

export const libArticle = i => ({ slug: `zz_libart_${i}`, title: `Zz library article ${i}`, kind: i % 3 ? 'concept' : 'region',
  summary: `Zz summary of library article ${i}.`, tags: [], regions: ['zz_region'], periodStart: -2000, periodEnd: -1000 });

/** One page of `total` rows made by `row`, at the request's `offset` and `limit`. */
function slice(url, total, row) {
  const q = new URL(url).searchParams;
  const offset = Number(q.get('offset') ?? 0), limit = Number(q.get('limit') || 20);
  const rows = Array.from({ length: Math.max(0, Math.min(limit, total - offset)) }, (_, k) => row(offset + k));
  return { offset, total, rows, next: offset + rows.length < total ? offset + rows.length : null };
}

export const libSourceRecord = ck => {
  const i = Number(ck.replace(/\D/g, '')) || 0;
  const s = libSource(i);
  return { source: { citekey: s.citekey, title: s.title, authors: s.authors, year: s.year, venue: 'Zz Journal of the Past',
    status: s.status, topics: s.topics, regions: s.regions, tags: [], periodStart: s.periodStart, periodEnd: s.periodEnd,
    summary: s.summary, doi: `10.9999/zz.${i}`, ocrMarkdown: null, notes: null, updatedAt: '2026-10-01T00:00:00Z',
    provenance: 'oa', uid: null, origin: null },
  findings: Array.from({ length: 5 }, (_, k) => ({ claim: `Zz finding ${k} of library source ${i}.`, regionName: 'zz_region',
    lon: 30 + k, lat: 35 + k, periodStart: -1200 - k * 10, periodEnd: -1180, periodName: null, tags: [], confidence: 'high',
    significance: null, uid: null })),
  formatting: [], illustration: null };
};

export const libNeighbors = ck => [
  { direction: 'out', kind: 'references', citekey: 'zz_lib_900', title: null, target: 'source' },
  { direction: 'out', kind: 'references', citekey: 'zz_lib_901', title: null, target: 'source' },
  { direction: 'in', kind: 'challenges', citekey: 'zz_lib_902', title: null, target: 'source' },
  { direction: 'in', kind: 'references', citekey: 'zz_lib_903', title: null, target: 'source' },
];

export const libArticleView = slug => {
  const i = Number(slug.replace(/\D/g, '')) || 0;
  const a = libArticle(i);
  return { article: { ...a, body: `# ${a.title}\n\nZz the opening paragraph of library article ${i} [[zz_lib_1]].\n\n## More\n\nZz.`,
    updatedAt: null, uid: null }, backlinks: [], sourcesCited: [{ citekey: 'zz_lib_1', title: 'Zz', year: 2000, status: 'current',
    authors: [], doi: null }], formatting: [], coverage: null };
};

/** Every Library feed on its route; returns the lists' requests as they were asked (URLSearchParams). */
export async function routeLibrary(page) {
  const asked = { sources: [], articles: [] };
  const json = (route, body) => route.fulfill({ json: body });
  await page.route(/\/api\/sources(\?|$)/, r => {
    asked.sources.push(new URL(r.request().url()).searchParams);
    const p = slice(r.request().url(), LIBRARY_SOURCES, libSource);
    return json(r, { total: p.total, hits: p.rows.map(source => ({ source, matched: [], why: [] })), offset: p.offset, next: p.next });
  });
  await page.route(/\/api\/sources\/zz_lib_\d+(\?|$)/, r => json(r, libSourceRecord(new URL(r.request().url()).pathname.split('/').pop())));
  await page.route(/\/api\/sources\/zz_lib_\d+\/articles/, r => json(r, [{ kind: 'article', id: 'zz_libart_1', title: 'Zz' }]));
  await page.route(/\/api\/neighbors\/zz_lib_\d+/, r => json(r, libNeighbors()));
  await page.route(/\/api\/articles(\?|$)/, r => {
    asked.articles.push(new URL(r.request().url()).searchParams);
    const p = slice(r.request().url(), LIBRARY_ARTICLES, libArticle);
    return json(r, { total: p.total, articles: p.rows, offset: p.offset, next: p.next, kinds: [] });
  });
  await page.route(/\/api\/articles\/zz_libart_\d+(\?|$)/, r => json(r, libArticleView(new URL(r.request().url()).pathname.split('/').pop())));
  await page.route(/\/source\/zz_lib_\d+/, r => r.fulfill({ contentType: 'text/html',
    body: `<!doctype html><title>Zz</title><h1 class="zz-source-page">${new URL(r.request().url()).pathname}</h1>` }));
  return asked;
}
