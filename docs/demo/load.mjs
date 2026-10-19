// Load the demo collection (dataset.json) into a THROWAWAY Histmersion server through its REST API —
// the same calls the CLI and the MCP tools make. Refuses a server that already holds any source, so
// it cannot write into a real collection by mistake (scripts/docs/screenshots.sh starts the server on
// a `*_test` database of its own).
//
//   node docs/demo/load.mjs http://127.0.0.1:55590
//
// The parts no endpoint writes (a museum picture with its credit line, the source licences, two
// approvals, fixed timestamps) are in extras.sql, which the screenshot script applies with psql after
// this; see README.md.
import { readFile } from 'node:fs/promises';

const base = (process.argv[2] || process.env.HISTMERSION_SERVER_URL || '').replace(/\/$/, '');
if (!base) { console.error('usage: node docs/demo/load.mjs <server url>'); process.exit(1); }
const dataset = JSON.parse(await readFile(new URL('./dataset.json', import.meta.url), 'utf8'));

async function call(method, path, body) {
  const res = await fetch(base + path, {
    method, headers: body === undefined ? {} : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status}: ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : null;
}
const enc = encodeURIComponent;
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ── the guard: a throwaway database only ───────────────────────────────────────────────────────
const { counts } = await call('GET', '/api/about');
if (counts.sources > 0 || counts.articles > 0)
  throw new Error(`refusing to load: the server already holds ${counts.sources} source(s) — use an empty, throwaway one`);

// ── wait for the bundled gazetteer and period presets the server imports on its first start ────
for (let i = 0; ; i++) {
  const { counts: c, jobs } = await call('GET', '/api/about');
  if (c.places > 0 && c.presets > 0 && jobs.running === 0 && jobs.queued === 0) break;
  if (i > 300) throw new Error('the gazetteer and the period presets did not finish importing');
  await sleep(1000);
}

// ── sources: every record first, then the edges between them ───────────────────────────────────
for (const s of dataset.sources) {
  const { citekey, licence, edges, ...put } = s;
  await call('PUT', `/api/sources/${enc(citekey)}`, put);
}
for (const s of dataset.sources)
  if (s.edges?.length) await call('PATCH', `/api/sources/${enc(s.citekey)}`, { edges: { add: s.edges } });

// the gazetteer's hierarchy (what lies inside what), then which findings fall in which places
{
  const { job } = await call('POST', '/api/places/build-hierarchy');
  for (let i = 0; job; i++) {
    const { status } = await call('GET', `/api/jobs/${enc(job)}`);
    if (status === 'done') break;
    if (status === 'failed' || i > 600) throw new Error(`building the place hierarchy: ${status}`);
    await sleep(1000);
  }
  await call('POST', '/api/places/rebuild');
}

// ── events and the passages that attest them ───────────────────────────────────────────────────
for (const e of dataset.events) {
  const { id, ...put } = e;
  await call('PUT', `/api/events/${enc(id)}`, put);
}
for (const a of dataset.attestations) await call('POST', '/api/attestations', a);

// ── the article, with its filter and the reasons for the sources it leaves out ─────────────────
{
  const { slug, ...put } = dataset.article;
  await call('PUT', `/api/articles/${enc(slug)}`, put);
}

// ── a research goal, its managers (created paused: nothing runs) and the bookkeeping ───────────
{
  const { slug, links, ...put } = dataset.goal;
  await call('PUT', `/api/goals/${enc(slug)}`, put);
  for (const l of links) await call('POST', `/api/goals/${enc(slug)}/links`, l);
}
for (const m of dataset.managers) {
  const { items, ...create } = m;
  // the review and enrich managers exist from the server's first start (paused); the others are made
  const id = create.goal ? `${create.kind}:${create.goal}` : create.kind;
  const known = await call('GET', `/api/agents/managers/${enc(id)}`).then(() => true, () => false);
  if (!known) await call('POST', '/api/agents/managers', { ...create, paused: true });
  for (const it of items ?? []) await call('POST', `/api/agents/managers/${enc(id)}/items`, it);
}
for (const [list, items] of Object.entries(dataset.queues))
  for (const it of items) await call('POST', `/api/queue/${list}`, it);

console.log(`loaded ${dataset.sources.length} sources, ${dataset.events.length} events, 1 article, 1 goal, ` +
  `${dataset.managers.length} managers into ${base}`);
