import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkShards, fnv1a, junitCases, junitSkips, parseManifest, parsePins, parseShard, shardOf, unescapeXml } from './test-shards.mjs';

test('fnv1a is the 32-bit FNV-1a of the UTF-8 bytes', () => {
  assert.equal(fnv1a(''), 0x811c9dc5);
  assert.equal(fnv1a('a'), 0xe40c292c);
  assert.equal(fnv1a('foobar'), 0xbf9cf968);
  assert.equal(fnv1a('390×844'), fnv1a('390×844'));
  assert.notEqual(fnv1a('390×844'), fnv1a('390x844'));
});

test('a shard spec is <index>/<count> with the index within the count', () => {
  assert.deepEqual(parseShard('2/4'), { index: 2, count: 4 });
  assert.deepEqual(parseShard('1/1'), { index: 1, count: 1 });
  for (const bad of ['0/4', '5/4', '1/0', '2', 'a/b', '', undefined, ' 1/2']) assert.throws(() => parseShard(bad), /HISTMERSION_TEST_SHARD/);
});

test('pins name a file and a shard within the count, and leave a shard for the rest', () => {
  assert.deepEqual([...parsePins('perf.test.mjs=1', 4)], [['perf.test.mjs', 1]]);
  assert.deepEqual([...parsePins(' a.mjs=1 , b.mjs=3 ', 4)], [['a.mjs', 1], ['b.mjs', 3]]);
  assert.equal(parsePins('', 4).size, 0);
  assert.equal(parsePins(undefined, 4).size, 0);
  assert.throws(() => parsePins('a.mjs=5', 4), /not <file>=<shard 1\.\.4>/);
  assert.throws(() => parsePins('a.mjs', 4), /not <file>=<shard/);
  assert.throws(() => parsePins('a.mjs=1,b.mjs=2', 2), /none is left/);
});

test('a pinned file goes whole to its shard; every other test by its name to an unpinned shard', () => {
  const pins = new Map([['perf.test.mjs', 1]]);
  assert.equal(shardOf('perf.test.mjs', 'soak: 100 rounds', 4, pins), 1);
  const names = Array.from({ length: 300 }, (_, i) => `test number ${i}`);
  const dealt = names.map(n => shardOf('browser.test.mjs', n, 4, pins));
  assert.ok(dealt.every(s => s >= 2 && s <= 4));
  assert.deepEqual([...new Set(dealt)].sort(), [2, 3, 4]);
  assert.deepEqual(names.map(n => shardOf('browser.test.mjs', n, 4, pins)), dealt);
  assert.equal(shardOf('marks.test.mjs', 'x', 4, pins), shardOf('browser.test.mjs', 'x', 4, pins));
  assert.equal(shardOf('a.mjs', 'anything', 1), 1);
});

test('junit test cases are read with their names unescaped and their files reduced to the base name', () => {
  const xml = `<testsuites><testcase name="a &quot;b&quot; &amp; c &lt;d&gt; &#39;e&#39; &#x263A;" time="1" classname="test" file="/w/modules/web/test/browser.test.mjs"/>
    <testcase name="second" time="2" classname="test" file="C:\\w\\marks.test.mjs"><skipped type="skipped"/></testcase>
    <testcase name="no file" time="0"/></testsuites>`;
  assert.deepEqual(junitCases(xml), [
    { name: 'a "b" & c <d> \'e\' ☺', file: 'browser.test.mjs' },
    { name: 'second', file: 'marks.test.mjs' },
    { name: 'no file', file: undefined },
  ]);
  assert.equal(junitSkips(xml), 1);
  assert.equal(unescapeXml('&amp;lt;'), '&lt;');
});

test('a manifest is one JSON record per line', () => {
  assert.deepEqual(parseManifest('{"shard":1,"count":2,"file":"a","name":"x"}\n\n{"shard":2,"count":2,"file":"a","name":"y"}\n'),
    [{ shard: 1, count: 2, file: 'a', name: 'x' }, { shard: 2, count: 2, file: 'a', name: 'y' }]);
});

const plan = [
  { shard: 1, count: 2, file: 'perf.test.mjs', name: 'soak' },
  { shard: 1, count: 2, file: 'perf.test.mjs', name: 'budgets: zoom' },
  { shard: 2, count: 2, file: 'browser.test.mjs', name: 'map' },
  { shard: 2, count: 2, file: 'browser.test.mjs', name: 'list' },
];
const ran = (...names) => names.map(name => ({ file: 'shard-preload.mjs', name }));
const shard = (index, cases, extra = {}) => ({ index, manifest: plan, cases, skipped: 0, ...extra });

test('the shards pass when every listed test ran once, in its own shard, and the excluded ones nowhere', () => {
  const result = checkShards([shard(1, ran('soak')), shard(2, ran('map', 'list'))], /^budgets: /);
  assert.deepEqual(result, { problems: [], expected: 3, ran: 3, sizes: [1, 2] });
});

test('a test file with none of a shard\'s tests is reported by the runner under its own name, and that entry is not a test', () => {
  const result = checkShards([shard(1, [...ran('soak'), { name: 'test/browser.test.mjs', file: 'browser.test.mjs' }]),
    shard(2, [...ran('map', 'list'), { name: 'perf.test.mjs', file: undefined }])], /^budgets: /);
  assert.deepEqual(result.problems, []);
  assert.equal(result.ran, 3);
});

test('a test that never ran, ran twice, ran in another shard or is not listed is a problem', () => {
  const p = cases => checkShards([shard(1, cases[0]), shard(2, cases[1])], /^budgets: /).problems;
  assert.deepEqual(p([ran('soak'), ran('map')]), ['never ran: browser.test.mjs: list (dealt to shard 2)']);
  assert.deepEqual(p([ran('soak', 'map'), ran('map', 'list')]), ['ran 2 times: browser.test.mjs: map (shards 1, 2)']);
  assert.deepEqual(p([ran('soak', 'list'), ran('map')]), ['ran in shard 1, dealt to 2: browser.test.mjs: list']);
  assert.deepEqual(p([ran('soak', 'budgets: zoom'), ran('map', 'list')]),
    ['ran but is not in the test list: budgets: zoom (shards 1)']);
});

test('a missing or doubled shard, a skip, disagreeing lists and a name declared twice are problems', () => {
  assert.deepEqual(checkShards([shard(2, ran('map', 'list'))], /^budgets: /).problems,
    ['shard 1/2 reported nothing', 'never ran: perf.test.mjs: soak (dealt to shard 1)']);
  assert.ok(checkShards([shard(1, ran('soak')), shard(1, ran('soak')), shard(2, ran('map', 'list'))]).problems.includes('shard 1 reported twice'));
  assert.deepEqual(checkShards([shard(1, ran('soak'), { skipped: 2 }), shard(2, ran('map', 'list'))], /^budgets: /).problems,
    ['shard 1: 2 test(s) skipped (G125: a skip fails the run)']);
  const other = shard(2, ran('map', 'list'), { manifest: plan.slice(0, 3) });
  assert.ok(checkShards([shard(1, ran('soak')), other], /^budgets: /).problems
    .includes('shard 2 saw another test list than shard 1: the shards did not run the same files'));
  const twice = shard(2, ran('map', 'list'), { manifest: [...plan, { ...plan[2], file: 'marks.test.mjs' }] });
  assert.ok(checkShards([shard(1, ran('soak')), twice], /^budgets: /).problems
    .some(m => /declared more than once.*map \(browser\.test\.mjs, marks\.test\.mjs\)/.test(m)));
  assert.match(checkShards([], undefined).problems[0], /disagree on their count, or listed no test: none/);
  assert.match(checkShards([shard(1, [], { manifest: [{ ...plan[0], count: 3 }] }), shard(2, [])]).problems[0], /disagree on their count/);
});
