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
  nodeTest.test = function shardedTest(name, ...rest) {
    const shard = shardOf(file, String(name), count, pins);
    if (manifest) appendFileSync(manifest, `${JSON.stringify({ shard, count, file, name: String(name) })}\n`);
    return shard === index ? register.call(this, name, ...rest) : Promise.resolve();
  };
  syncBuiltinESMExports();
}
