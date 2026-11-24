import { createRequire, syncBuiltinESMExports } from 'node:module';
import { appendFileSync } from 'node:fs';
import { basename } from 'node:path';
import { parsePins, parseShard, shardOf } from './test-shards.mjs';

const spec = process.env.HISTMERSION_TEST_SHARD;
if (spec) {
  const { index, count } = parseShard(spec);
  const pins = parsePins(process.env.HISTMERSION_TEST_SHARD_PINS, count);
  const manifest = process.env.HISTMERSION_TEST_SHARD_MANIFEST;
  const file = basename(process.argv[1] ?? '');
  const nodeTest = createRequire(import.meta.url)('node:test');
  const register = nodeTest.test;
  const hooks = ['before', 'after', 'beforeEach', 'afterEach'].map(kind => ({ kind, registerHook: nodeTest[kind] }));
  let held = [];
  nodeTest.test = function shardedTest(name, ...rest) {
    const shard = shardOf(file, String(name), count, pins);
    if (manifest) appendFileSync(manifest, `${JSON.stringify({ shard, count, file, name: String(name) })}\n`);
    if (shard !== index) return Promise.resolve();
    if (held) {
      for (const run of held) run();
      held = null;
    }
    return register.call(this, name, ...rest);
  };
  for (const { kind, registerHook } of hooks)
    nodeTest[kind] = (...args) => { if (held) held.push(() => registerHook(...args)); else registerHook(...args); };
  syncBuiltinESMExports();
}
