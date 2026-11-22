import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const opt = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const baseUrl = opt('--base-url'), prUrl = opt('--pr-url'), out = opt('--out') ?? 'target/budgets-ab';
const rounds = Number(opt('--rounds') ?? 3);
const files = JSON.parse(readFileSync('package.json', 'utf8')).scripts['test:browser'].split(' ').filter(a => a.startsWith('test/'));
if (!baseUrl || !prUrl || !(rounds >= 2)) { console.error('usage: node test/budgets-ab.mjs --base-url <url> --pr-url <url> [--rounds 3] [--out dir]'); process.exit(64); }
mkdirSync(out, { recursive: true });

function run(side, url, round) {
  const figures = join(out, `${side}-${round}.json`), junit = join(out, `${side}-${round}.xml`);
  const started = Date.now();
  const inject = side === 'pr' && Number(process.env.HISTMERSION_BUDGETS_INJECT_ZOOM_MS || 0) > 0
    ? ['--import', new URL('./budgets-inject.mjs', import.meta.url).pathname] : [];
  if (inject.length && round === 1) console.log(`# this change's runs carry an injected ${process.env.HISTMERSION_BUDGETS_INJECT_ZOOM_MS} ms task on every zoom (the check that the job turns red)`);
  const r = spawnSync(process.execPath, [...inject, '--test', '--test-concurrency=1', '--test-force-exit', '--test-name-pattern=^budgets: ',
    '--test-reporter=spec', '--test-reporter-destination=stdout', '--test-reporter=junit', `--test-reporter-destination=${junit}`, ...files],
  { stdio: ['ignore', 'inherit', 'inherit'], env: { ...process.env, HISTMERSION_SERVER_URL: url, HISTMERSION_BUDGETS_RECORD: figures, HISTMERSION_PERF_REPORT: '1' } });
  const xml = existsSync(junit) ? readFileSync(junit, 'utf8') : '';
  const count = re => (xml.match(re) ?? []).length;
  const result = { side, round, figures, status: r.status, tests: count(/<testcase name="budgets: /g), skipped: count(/<skipped/g),
    failed: count(/<failure/g), seconds: Math.round((Date.now() - started) / 1000), recorded: existsSync(figures) };
  console.log(`# ${side} run ${round}: ${result.tests} budget tests, ${result.failed} failed, ${result.skipped} skipped, ${result.seconds} s`);
  return result;
}

const runs = [];
for (let i = 1; i <= rounds; i++) {
  const order = i % 2 ? [['base', baseUrl], ['pr', prUrl]] : [['pr', prUrl], ['base', baseUrl]];
  for (const [side, url] of order) runs.push(run(side, url, i));
}
const bad = runs.filter(r => r.side === 'pr' && (r.tests === 0 || r.skipped > 0 || r.failed > 0 || !r.recorded));
for (const r of runs.filter(r => r.side === 'base' && (r.failed > 0 || r.skipped > 0)))
  console.log(`# the merge base's run ${r.round}: ${r.failed} failed, ${r.skipped} skipped (shown, not this change's)`);
for (const r of bad) console.log(`::error::this change's budget run ${r.round}: ${r.tests} budget tests, ${r.failed} failed, ${r.skipped} skipped (G125: a skip fails)`);
const pick = side => runs.filter(r => r.side === side && r.recorded).map(r => r.figures);
const compare = spawnSync(process.execPath, [new URL('./budgets-compare.mjs', import.meta.url).pathname, '--base', ...pick('base'), '--pr', ...pick('pr')], { stdio: 'inherit' });
process.exit(bad.length || compare.status !== 0 ? 1 : 0);
