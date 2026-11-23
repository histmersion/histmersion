export function fnv1a(text) {
  let hash = 0x811c9dc5;
  for (const byte of Buffer.from(text, 'utf8')) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

export function parseShard(spec) {
  const m = /^(\d+)\/(\d+)$/.exec(spec ?? '');
  const index = Number(m?.[1]), count = Number(m?.[2]);
  if (!m || count < 1 || index < 1 || index > count) throw new Error(`HISTMERSION_TEST_SHARD=${spec}: expected <index>/<count>, 1 ≤ index ≤ count`);
  return { index, count };
}

export function parsePins(text, count) {
  const pins = new Map();
  for (const entry of (text ?? '').split(',').map(e => e.trim()).filter(Boolean)) {
    const m = /^([^=]+)=(\d+)$/.exec(entry);
    const shard = Number(m?.[2]);
    if (!m || shard < 1 || shard > count) throw new Error(`HISTMERSION_TEST_SHARD_PINS: "${entry}" is not <file>=<shard 1..${count}>`);
    pins.set(m[1], shard);
  }
  const free = Array.from({ length: count }, (_, i) => i + 1).filter(k => ![...pins.values()].includes(k));
  if (!free.length && count > 0) throw new Error(`HISTMERSION_TEST_SHARD_PINS: every one of the ${count} shards is pinned, none is left for the other files`);
  return pins;
}

export function shardOf(file, name, count, pins = new Map()) {
  if (pins.has(file)) return pins.get(file);
  const free = Array.from({ length: count }, (_, i) => i + 1).filter(k => ![...pins.values()].includes(k));
  return free[fnv1a(name) % free.length];
}

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

export const unescapeXml = text => text.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (_, e) =>
  e[0] !== '#' ? ENTITIES[e.toLowerCase()] : String.fromCodePoint(e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : Number(e.slice(1))));

const attribute = (tag, key) => {
  const m = new RegExp(`\\s${key}="([^"]*)"`).exec(tag);
  return m ? unescapeXml(m[1]) : undefined;
};

export function junitCases(xml) {
  return [...xml.matchAll(/<testcase\b[^>]*>/g)].map(m => {
    const file = attribute(m[0], 'file');
    return { name: attribute(m[0], 'name'), file: file === undefined ? undefined : file.split(/[\\/]/).at(-1) };
  });
}

export const junitSkips = xml => (xml.match(/<skipped\b/g) ?? []).length;

export function parseManifest(text) {
  return text.split('\n').filter(l => l.trim()).map(l => JSON.parse(l));
}

const keyOf = ({ file, name }) => `${file}\t${name}`;

export function checkShards(shards, exclude) {
  const problems = [];
  const counts = new Set(shards.flatMap(s => s.manifest.map(e => e.count)));
  if (counts.size !== 1) {
    problems.push(`the shards disagree on their count, or listed no test: ${[...counts].join(', ') || 'none'}`);
    return { problems, expected: 0, ran: 0 };
  }
  const [count] = counts;
  const indexes = shards.map(s => s.index);
  for (let i = 1; i <= count; i++) if (!indexes.includes(i)) problems.push(`shard ${i}/${count} reported nothing`);
  for (const i of indexes) if (indexes.indexOf(i) !== indexes.lastIndexOf(i)) problems.push(`shard ${i} reported twice`);

  const planOf = manifest => {
    const plan = new Map();
    for (const e of manifest) {
      const key = keyOf(e);
      if (plan.has(key) && plan.get(key) !== e.shard) problems.push(`"${e.name}" (${e.file}) was dealt to shards ${plan.get(key)} and ${e.shard}`);
      plan.set(key, e.shard);
    }
    return plan;
  };
  const plans = shards.map(s => ({ index: s.index, plan: planOf(s.manifest) }));
  const plan = plans[0]?.plan ?? new Map();
  for (const other of plans.slice(1)) {
    const a = [...plan.entries()].map(([k, v]) => `${k}\t${v}`).sort().join('\n');
    const b = [...other.plan.entries()].map(([k, v]) => `${k}\t${v}`).sort().join('\n');
    if (a !== b) problems.push(`shard ${other.index} saw another test list than shard ${plans[0].index}: the shards did not run the same files`);
  }
  for (const s of shards) {
    const seen = new Map();
    for (const e of s.manifest) seen.set(keyOf(e), (seen.get(keyOf(e)) ?? 0) + 1);
    const twice = [...seen.entries()].filter(([, n]) => n > 1).map(([k]) => k.replace('\t', ': '));
    if (twice.length) problems.push(`shard ${s.index}: test names declared more than once, so a shard cannot be told apart: ${twice.join('; ')}`);
  }

  const expected = [...plan.entries()].filter(([k]) => !exclude?.test(k.split('\t')[1]));
  const ranIn = new Map();
  for (const s of shards) for (const c of s.cases) {
    const key = keyOf(c);
    ranIn.set(key, [...(ranIn.get(key) ?? []), s.index]);
  }
  for (const [key, shard] of expected) {
    const where = ranIn.get(key) ?? [];
    const label = key.replace('\t', ': ');
    if (!where.length) problems.push(`never ran: ${label} (dealt to shard ${shard})`);
    else if (where.length > 1) problems.push(`ran ${where.length} times: ${label} (shards ${where.join(', ')})`);
    else if (where[0] !== shard) problems.push(`ran in shard ${where[0]}, dealt to ${shard}: ${label}`);
  }
  const known = new Set(expected.map(([k]) => k));
  for (const [key, where] of ranIn) if (!known.has(key)) problems.push(`ran but is not in the test list: ${key.replace('\t', ': ')} (shards ${where.join(', ')})`);
  for (const s of shards) if (s.skipped) problems.push(`shard ${s.index}: ${s.skipped} test(s) skipped (G125: a skip fails the run)`);

  const sizes = Array.from({ length: count }, (_, i) => expected.filter(([, shard]) => shard === i + 1).length);
  return { problems, expected: expected.length, ran: [...ranIn.values()].reduce((n, w) => n + w.length, 0), sizes };
}
