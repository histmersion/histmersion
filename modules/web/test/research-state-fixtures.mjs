const NOW = Date.now();
const iso = minutes => new Date(NOW + minutes * 60000).toISOString();
const one = name => ({ [name]: {} });
const caseOf = v => Object.keys(v)[0];

const creator = { kind: 'manual', surface: 'web' };
const unattributed = { kind: 'unattributed', surface: 'rest' };

const acquisition = (item, text, verdict) => ({ Acquisition: { item, text, verdict, last: iso(-120), tries: 2, creator } });

export const verdicts = {
  101: { Unchecked: { due: iso(3 * 1440) } },
  102: { Converting: { source: 'unpaywall' } },
  103: { Retrying: { failures: 2, next: iso(240), last: [] } },
  104: { Stuck: { failures: 5, since: iso(-20 * 1440), last: [] } },
  105: { ManualDownload: { url: 'https://zz.example.org/open.pdf' } },
  106: one('Borrow'),
  107: { NoFreeCopy: { recheck: iso(7 * 1440) } },
  108: one('NoIdentifier'),
};

const decision = (id, subject, actor) => ({ Decision: { actor: one(actor), approval: { id, kind: 'article-publish', citekey: '',
  subject, state: 'open', evidence: `Zz the review of ${subject} so far.`, createdAt: iso(-60) } } });

export function stateOf(taskActor) {
  const items = [
    decision(901, 'zz-hittite-storm-god', 'Owner'),
    decision(902, 'zz-sea-peoples', 'Agents'),
    acquisition(101, 'Zz Anthony 2007, The Horse, the Wheel and Language', verdicts[101]),
    acquisition(102, 'Zz Langgut 2013, Climate and the Late Bronze Collapse', verdicts[102]),
    acquisition(103, 'Zz Drake 2012, The influence of climatic change', verdicts[103]),
    acquisition(104, 'Zz Kaniewski 2013, Environmental roots', verdicts[104]),
    acquisition(105, 'Zz Knapp 2014, Climate change in the Bronze Age', verdicts[105]),
    acquisition(106, 'Zz Cline 2014, 1177 B.C.', verdicts[106]),
    acquisition(107, 'Zz Kristiansen 2005, The Rise of Bronze Age Society', verdicts[107]),
    acquisition(108, 'Zz a work without an identifier', verdicts[108]),
    { Task: { item: 201, text: 'Zz read the review of the sea peoples', actor: one(taskActor), creator } },
    { Task: { item: 202, text: 'Zz enrich the drought sources', actor: one('Agents'), creator: unattributed } },
    { PausedRun: { run: '0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f', pipeline: 'enrich-source', actor: one('Owner') } },
  ];
  const who = i => {
    const v = Object.values(i)[0];
    if (!v.verdict) return caseOf(v.actor);
    return ['Unchecked', 'Converting', 'Retrying'].includes(caseOf(v.verdict)) ? 'Agents' : 'Owner';
  };
  return {
    totals: { owner: items.filter(i => who(i) === 'Owner').length, agents: items.filter(i => who(i) === 'Agents').length },
    items,
    at: iso(0),
  };
}

export const history = {
  item: 104, text: 'Zz Kaniewski 2013, Environmental roots', verdict: verdicts[104],
  attempts: [
    { at: iso(-120), trigger: one('Recheck'), generation: 'g1', outcome: one('NoCopy'),
      answers: [{ fetcher: 'unpaywall', said: one('NotHeld'),
        why: { group: 'NotFound', case: 'Entity', data: { typeName: 'Doi', key: '10.5555/zz' }, text: "no Doi '10.5555/zz'" } }],
      oa: { scripts: one('Allowed') } },
    { at: iso(-3000), trigger: one('OldCheck'), generation: 'legacy',
      outcome: { OldCheck: { note: 'zz checked 2026-11-01: no copy', checks: 3 } }, answers: [] },
  ],
};

export async function routeResearchState(page) {
  const writes = { actor: [] };
  let taskActor = 'Owner';
  await page.route(/\/api\/research\/state(\?|$)/, r => r.fulfill({ json: stateOf(taskActor) }));
  await page.route(/\/api\/research\/state\/acquisitions\/\d+$/, r => r.fulfill({ json: history }));
  await page.route(/\/api\/queue\/items\/\d+$/, r => {
    if (r.request().method() !== 'PATCH') return r.continue();
    const id = Number(new URL(r.request().url()).pathname.split('/').at(-1));
    const actor = JSON.parse(r.request().postData()).actor;
    writes.actor.push(`${id}:${actor}`);
    taskActor = actor === 'agents' ? 'Agents' : 'Owner';
    return r.fulfill({ json: { id, list: 'tasks', text: 'Zz read the review of the sea peoples', status: 'open', actor } });
  });
  return writes;
}
