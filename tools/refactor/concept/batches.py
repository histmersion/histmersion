#!/usr/bin/env python3
"""Cuts the rows the rules left into batches by owner, each with the concepts its classifier may name."""
import json, re, os, sys, collections
OUT = 'refactor-audit/concept-table'
SIZE = int(sys.argv[1]) if len(sys.argv) > 1 else 130
rows = json.load(open(f'{OUT}/rows.json'))
need = [r for r in rows if not r['rule']]
kept = [r for r in rows if r['rule'] == 'keep']
byfile = collections.defaultdict(list)
for r in need:
    byfile[r['path']].append(r)
kernel = sorted({r['fqn'] for r in kept if r['module'] == 'values' or r['fqn'].count('.') <= 3})
os.makedirs(f'{OUT}/batches', exist_ok=True); os.makedirs(f'{OUT}/out', exist_ok=True)
batches, cur = [], []
for f in sorted(byfile):
    if cur and len(cur) + len(byfile[f]) > SIZE:
        batches.append(cur); cur = []
    cur += byfile[f]
if cur:
    batches.append(cur)
for i, b in enumerate(batches):
    owners = {r['owner'] for r in b}
    paths = {r['path'] for r in b}
    vocab = sorted({f"{r['fqn']} ({r['base']})" for r in kept if r['path'] in paths or r['owner'] in owners})
    with open(f'{OUT}/batches/batch-{i:03}.txt', 'w') as o:
        o.write('## CONCEPTS NEARBY\n' + '\n'.join(vocab) + '\n\n## TYPES TO DECIDE\n')
        for r in b:
            o.write(f"{r['fqn']} | base {r['base']} | {','.join(r['kinds']) or 'no-resolved-use'}\n")
            for s in r['sites'][:2]:
                o.write(f'    {s}\n')
open(f'{OUT}/kernel.txt', 'w').write('\n'.join(kernel) + '\n')
print(len(batches), 'batches; sizes', sorted(len(b) for b in batches)[-3:], 'bytes', max(os.path.getsize(f'{OUT}/batches/'+x) for x in os.listdir(f'{OUT}/batches')), 'kernel', len(kernel))
