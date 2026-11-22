import { readFileSync } from 'node:fs';
import { compareRuns } from './budget-figures.mjs';

const args = process.argv.slice(2);
const at = args.indexOf('--pr');
if (args[0] !== '--base' || at < 2 || at === args.length - 1) {
  console.error('usage: node budgets-compare.mjs --base <figures.json>... --pr <figures.json>...');
  process.exit(64);
}
const read = files => files.map(f => JSON.parse(readFileSync(f, 'utf8')));
const rows = compareRuns(read(args.slice(1, at)), read(args.slice(at + 1)));
const fmt = xs => xs.map(x => Math.round(x * 10) / 10).join(' ');
const regressions = rows.filter(r => r.regression);
for (const r of rows) {
  const verdict = r.missing ? 'not on both sides' : r.regression ? 'REGRESSION' : r.frameBound ? 'frame-bound, reported' : 'ok';
  const detail = r.missing ? `base [${fmt(r.base)}] pr [${fmt(r.pr)}]`
    : `base [${fmt(r.base)}] pr [${fmt(r.pr)}] worse by ${Math.round(r.worse * 10) / 10}, noise ${Math.round(r.noise * 10) / 10}, floor ${Math.round(r.floor * 10) / 10}`;
  console.log(`${verdict.padEnd(22)} ${r.key.padEnd(34)} ${detail}`);
}
console.log(`${rows.length} figures, ${regressions.length} regression(s)`);
for (const r of regressions)
  console.log(`::error::budget regression ${r.key}: the merge base ${fmt(r.base)}, this change ${fmt(r.pr)} (worse by ${Math.round(r.worse * 10) / 10}, the runs' noise ${Math.round(r.noise * 10) / 10})`);
process.exit(regressions.length ? 1 : 0);
