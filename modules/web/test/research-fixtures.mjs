// Research at a glance, from synthetic rows: the autopilot's status, the managers, the plan's quota, open decisions,
// one goal's view and items, its lists, and the activity list (paged). Nothing here is corpus data; every name
// starts with "zz". `routeResearch` records the writes the page sent (the mode, the decisions, the job controls).

const NOW = Date.now();
const iso = minutesAgo => new Date(NOW - minutesAgo * 60000).toISOString();

export const goal = { slug: 'zz-collapse', title: 'Zz Bronze Age collapse', status: 'seeding',
  notes: 'Zz what ended the palace societies around 1200 BCE, and how far does the evidence support each cause?' };

const managers = [
  { id: 'seed:zz-collapse', kind: 'seed', goal: 'zz-collapse', state: 'active', counts: { pending: 3, held: 1, active: 0, done: 8, stopped: 0, failed: 0, cancelled: 0 } },
  { id: 'watch:zz-collapse', kind: 'watch', goal: 'zz-collapse', state: 'active', nextTickAt: new Date(NOW + 2 * 86400000).toISOString(),
    counts: { pending: 0, held: 0, active: 0, done: 0, stopped: 0, failed: 0, cancelled: 0 } },
  { id: 'seed:zz-anatolia', kind: 'seed', goal: 'zz-anatolia', state: 'active', counts: { pending: 6, held: 0, active: 0, done: 3, stopped: 0, failed: 0, cancelled: 0 } },
];

const status = (on, draining) => ({ on, agentsEnabled: true, managersEnabled: true,
  drain: draining ? { since: iso(1), by: 'web' } : null,
  goals: [{ slug: 'zz-collapse', title: goal.title, status: 'seeding', lastActivity: iso(2), managers: [] },
    { slug: 'zz-anatolia', title: 'Zz Anatolian religion', status: 'compiled', lastActivity: iso(30), openApprovals: 1, managers: [] }],
  focus: { on: true, waiting: [{ slug: 'zz-anatolia', code: 'person', why: 'zz an approval only you decide' }] } });

export const approvals = [
  { id: 902, runId: 'zz-run-review', kind: 'article-publish', citekey: '', subject: 'zz-hittite-storm-god', state: 'open',
    evidence: 'Zz the review so far: one major issue open.', createdAt: iso(60) },
  { id: 901, runId: 'zz-run-currency', kind: 'edge', citekey: 'zz_drake_2012', edgeKind: 'superseded_by', target: 'zz_kaniewski_2013',
    state: 'open', evidence: 'Zz the later paper re-dates the drought with new pollen cores.', createdAt: iso(300) },
];

const items = [
  { id: 1, managerId: 'seed:zz-collapse', kind: 'review-article', target: 'zz-sea-peoples', status: 'active', runId: 'zz-run-review',
    createdAt: iso(10), updatedAt: iso(4) },
  { id: 2, managerId: 'seed:zz-collapse', kind: 'compile-article', target: 'zz-late-bronze-age-collapse', status: 'done',
    runId: 'zz-run-compile', outcome: 'zz passed review with 2 small fixes', createdAt: iso(3000), updatedAt: iso(2000) },
  { id: 3, managerId: 'seed:zz-collapse', kind: 'compile-article', target: 'zz-drought-dating', status: 'held',
    hold: { reason: 'zz waiting for 2 papers you could get' }, createdAt: iso(100), updatedAt: iso(100) },
];

/** The merged list: `n` entries, newest first, two of them running (a run and a long job), one a problem. */
export const activityEntry = i => {
  const base = { key: `job:zz-job-${String(i).padStart(3, '0')}`, kind: 'job', at: iso(5 + i), state: 'done', what: 'ingest',
    text: '', job: `zz-job-${String(i).padStart(3, '0')}`, citekey: `zz_src_${i}` };
  if (i === 0) return { key: 'run:zz-run-review', kind: 'run', at: iso(4), state: 'running', what: 'review-article', text: 'step 3 of 5',
    goal: 'zz-collapse', job: 'zz-run-review', article: 'zz-sea-peoples', target: 'zz-sea-peoples', targetKind: 'article' };
  if (i === 1) return { key: 'job:zz-job-media', kind: 'job', at: iso(4.5), state: 'running', what: 'media-import', text: '41/100 places',
    job: 'zz-job-media', target: 'Europeana pictures' };
  if (i === 2) return { key: 'log:9001', kind: 'log', at: iso(5), text: 'zz found a legal open copy of Langgut et al. 2013', goal: 'zz-collapse',
    job: 'zz-job-fetch', by: 'job', queueItem: 9001, queueList: 'log' };
  if (i === 3) return { key: 'job:zz-job-fail', kind: 'job', at: iso(6), state: 'failed', what: 'acquire_recheck', text: '',
    error: 'zz the publisher asked for a human check', job: 'zz-job-fail' };
  if (i === 4) return { key: 'decision:880', kind: 'decision', at: iso(7), state: 'approved', what: 'edge', text: '', approval: 880,
    citekey: 'zz_drake_2012', goal: 'zz-collapse', job: 'zz-run-currency' };
  return base;
};

export async function routeResearch(page, { total = 45 } = {}) {
  const writes = { mode: [], decide: [], control: [], recheck: [], activity: [] };
  let mode = { on: true, draining: false };
  const json = (route, body, headers) => route.fulfill({ json: body, headers });
  await page.route('**/api/config', async route => {
    const res = await route.fetch();
    return route.fulfill({ response: res, json: { ...(await res.json()), agentsEnabled: true } });
  });
  await page.route('**/api/ui/counters', async route => {
    const res = await route.fetch();
    return route.fulfill({ response: res, json: { ...(await res.json()), agents: { running: 3, queued: 12, on: mode.on, draining: mode.draining } } });
  });
  await page.route(/\/api\/agents\/autopilot(\?|$)/, r => json(r, status(mode.on, mode.draining)));
  await page.route('**/api/agents/autopilot/mode', r => {
    const m = JSON.parse(r.request().postData()).mode;
    writes.mode.push(m);
    mode = m === 'working' ? { on: true, draining: false } : m === 'finish' ? { on: true, draining: true } : { on: false, draining: false };
    return json(r, status(mode.on, mode.draining));
  });
  await page.route(/\/api\/agents\/managers(\?|$)/, r => json(r, managers));
  await page.route(/\/api\/agents\/managers\/[^/]+\/items/, r => {
    const id = decodeURIComponent(new URL(r.request().url()).pathname.split('/')[4]);
    const mine = items.filter(i => i.managerId === id);
    return json(r, { items: mine, total: mine.length, offset: 0, limit: 12 });
  });
  await page.route('**/api/agents/quota', r => json(r, { plans: [{ pool: 'claude', title: 'Claude plan', kind: 'claude', covers: [], floors: '',
    floorsSource: '', held: false, usable: true, maxCallsInFlight: 1, windows: [{ limit: 'claude', kind: 'seven_day', usedPct: 39, leftPct: 61,
    floor: 10, usable: true, stale: false, observedAt: iso(1), ageSeconds: 60, source: 'probe' }] }], metered: [], fallthrough: [], now: iso(0) }));
  await page.route(/\/api\/agents\/approvals(\?|$)/, r => json(r, approvals, { 'X-Total-Count': String(approvals.length) }));
  await page.route(/\/api\/agents\/approvals\/\d+\/(approve|reject)/, r => {
    const [, id, d] = new URL(r.request().url()).pathname.match(/approvals\/(\d+)\/(\w+)/);
    writes.decide.push(`${id}:${d}`);
    return json(r, { ...approvals.find(a => String(a.id) === id), state: d === 'approve' ? 'approved' : 'rejected' });
  });
  await page.route(`**/api/goals/${goal.slug}/view`, r => json(r, { goal, children: [],
    links: [{ kind: 'article', ref: 'zz-late-bronze-age-collapse', title: 'Zz Late Bronze Age collapse', createdAt: iso(2000) },
      ...Array.from({ length: 61 }, (_, i) => ({ kind: 'source', ref: `zz_src_${i}`, title: `Zz ${i}`, createdAt: iso(3000) }))],
    schedules: [{ goal: goal.slug, kind: 'currency', cron: '@weekly', enabled: true, lastRunAt: new Date(NOW - 3 * 86400000).toISOString(),
      nextRunAt: new Date(NOW + 2 * 86400000).toISOString() }] }));
  await page.route(/\/api\/queue\/(tasks|acquire)\?.*goal=/, r => {
    const list = new URL(r.request().url()).pathname.split('/').pop();
    const n = list === 'tasks' ? 4 : 9;
    return json(r, { list, items: Array.from({ length: n }, (_, i) => ({ id: 7000 + i, list, text: `zz ${list} item ${i}`, topic: null,
      status: 'open', goal: goal.slug, createdBy: 'zz-run-compile' })), checks: [], goals: [] });
  });
  await page.route(/\/api\/activity(\?|$)/, r => {
    const q = new URL(r.request().url()).searchParams;
    writes.activity.push(Object.fromEntries(q));
    const limit = Number(q.get('limit') || 30);
    const all = Array.from({ length: total }, (_, i) => activityEntry(i))
      .filter(e => q.get('show') !== 'running' || e.state === 'running')
      .filter(e => !q.get('goal') || e.goal === q.get('goal'));
    const start = q.get('before') ? all.findIndex(e => `${e.at}~${e.key}` === q.get('before')) + 1 : 0;
    const entries = all.slice(start, start + limit);
    const next = start + limit < all.length ? `${entries.at(-1).at}~${entries.at(-1).key}` : null;
    return json(r, { entries, next, counts: { running: 2, problemsWeek: 1 } });
  });
  await page.route(/\/api\/jobs\/[^/]+\/(pause|resume|cancel)$/, r => {
    const [, id, action] = new URL(r.request().url()).pathname.match(/jobs\/([^/]+)\/(\w+)$/);
    writes.control.push(`${id}:${action}`);
    return json(r, { id, kind: 'media-import', status: 'running', citekey: null });
  });
  await page.route('**/api/queue/acquire/recheck', r => {
    writes.recheck.push(JSON.parse(r.request().postData()));
    return json(r, { dryRun: false, open: 9, withDoi: 9, due: 9, allowance: 9, closed: 0, fetched: 0, noCopy: 1, failed: 0, rows: [],
      job: JSON.parse(r.request().postData()).all ? { id: 'zz-recheck-job', kind: 'acquire_recheck', status: 'running', citekey: null } : null });
  });
  return writes;
}
