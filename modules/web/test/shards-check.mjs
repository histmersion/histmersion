import { appendFileSync, existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { checkShards, junitCases, junitSkips, parseManifest } from './test-shards.mjs';

const opt = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const dir = opt('--dir'), exclude = opt('--exclude'), summary = opt('--summary');
if (!dir) {
  console.error('usage: node test/shards-check.mjs --dir <dir of [<prefix>-]shard-<i>/{manifest.jsonl,browser-junit.xml}> [--exclude <name regex>] [--summary <file>]');
  process.exit(64);
}
const shards = readdirSync(dir).map(d => /(?:^|-)shard-(\d+)$/.exec(d)).filter(Boolean).map(m => {
  const at = join(dir, m.input), read = f => existsSync(join(at, f)) ? readFileSync(join(at, f), 'utf8') : '';
  const xml = read('browser-junit.xml');
  return { index: Number(m[1]), manifest: parseManifest(read('manifest.jsonl')), cases: junitCases(xml), skipped: junitSkips(xml) };
}).sort((a, b) => a.index - b.index);
const { problems, expected, ran, sizes } = checkShards(shards, exclude ? new RegExp(exclude) : undefined);
const lines = [
  `browser test shards: ${shards.length} reported, ${expected} test(s) listed${exclude ? ` (not ${exclude})` : ''}, ${ran} ran` +
    (sizes ? `; tests per shard: ${sizes.join(' / ')}` : ''),
  ...shards.map(s => `- shard ${s.index}: ${s.cases.length} ran, ${s.skipped} skipped`),
  ...(problems.length ? problems.map(p => `- PROBLEM: ${p}`) : ['every listed test ran exactly once, in the shard it was dealt to']),
];
console.log(lines.join('\n'));
if (summary) appendFileSync(summary, `${lines.join('\n')}\n`);
for (const p of problems) console.log(`::error::${p}`);
process.exit(problems.length ? 1 : 0);
