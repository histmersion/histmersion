import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { changedMeasurement } from './budget-figures.mjs';

const opt = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const baseUrl = opt('--base-url'), prUrl = opt('--pr-url'), out = resolve(opt('--out') ?? 'target/budgets-ab');
const rounds = Number(opt('--rounds') ?? 3);
const prWeb = resolve('.'), baseWeb = resolve(opt('--base-web') ?? '.');
if (!baseUrl || !prUrl || !(rounds >= 2)) {
  console.error('usage: node test/budgets-ab.mjs --base-url <url> --pr-url <url> [--base-web <the merge base\'s modules/web>] [--rounds 3] [--out dir]');
  process.exit(64);
}
mkdirSync(out, { recursive: true });
const filesOf = web => JSON.parse(readFileSync(join(web, 'package.json'), 'utf8')).scripts['test:browser'].split(' ').filter(a => a.startsWith('test/'));
const reader = web => f => { const p = join(web, 'test', f); return existsSync(p) ? readFileSync(p, 'utf8') : ''; };
const changed = baseWeb === prWeb ? []
  : changedMeasurement(reader(baseWeb), reader(prWeb), 'perf.test.mjs').filter(f => f !== 'budget-figures.mjs');
console.log(baseWeb === prWeb ? '# both sides run this change\'s test code'
  : `# each side runs its own test code (the merge base's: ${baseWeb}); the measuring code ${changed.length ? `differs: ${changed.join(', ')}` : 'is the same'}`);

function run(side, url, round) {
  const web = side === 'base' ? baseWeb : prWeb;
  const figures = join(out, `${side}-${round}.json`), junit = join(out, `${side}-${round}.xml`);
  const started = Date.now();
  const inject = side === 'pr' && Number(process.env.HISTMERSION_BUDGETS_INJECT_ZOOM_MS || 0) > 0
    ? ['--import', new URL('./budgets-inject.mjs', import.meta.url).pathname] : [];
  if (inject.length && round === 1) console.log(`# this change's runs carry an injected ${process.env.HISTMERSION_BUDGETS_INJECT_ZOOM_MS} ms task on every zoom (the check that the job turns red)`);
  const r = spawnSync(process.execPath, [...inject, '--test', '--test-concurrency=1', '--test-force-exit', '--test-name-pattern=^budgets: ',
    '--test-reporter=spec', '--test-reporter-destination=stdout', '--test-reporter=junit', `--test-reporter-destination=${junit}`, ...filesOf(web)],
  { cwd: web, stdio: ['ignore', 'inherit', 'inherit'], env: { ...process.env, HISTMERSION_SERVER_URL: url, HISTMERSION_BUDGETS_RECORD: figures, HISTMERSION_PERF_REPORT: '1' } });
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
const bad = runs.filter(r => r.tests === 0 || r.skipped > 0 || r.failed > 0 || !r.recorded);
for (const r of bad) {
  const whose = r.side === 'pr' ? 'this change\'s' : 'the merge base\'s (with its own tests)';
  console.log(`::error::${whose} budget run ${r.round}: ${r.tests} budget tests, ${r.failed} failed, ${r.skipped} skipped (G125: a skip fails)`);
}
const pick = side => runs.filter(r => r.side === side && r.recorded).map(r => r.figures);
const compare = spawnSync(process.execPath, [new URL('./budgets-compare.mjs', import.meta.url).pathname,
  ...(changed.length ? ['--measurement-changed', changed.join(',')] : []), '--base', ...pick('base'), '--pr', ...pick('pr')], { stdio: 'inherit' });
process.exit(bad.length || compare.status !== 0 ? 1 : 0);
