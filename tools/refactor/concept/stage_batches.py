#!/usr/bin/env python3
"""One batch set for a stage: every unconstrained newtype of its modules, with the concepts each may name.
usage: stage_batches.py <stage dir> <size>"""
import collections, json, os, subprocess, sys
OUT, SIZE = sys.argv[1], int(sys.argv[2])
rows = json.load(open(f'{OUT}/rows.json'))
cand = [r for r in rows if r['pure']]
by = collections.defaultdict(list)
for r in cand:
    by[r['path']].append(r)
bs, cur = [], []
for f in sorted(by):
    if cur and len(cur) + len(by[f]) > SIZE:
        bs.append(cur); cur = []
    cur += by[f]
if cur:
    bs.append(cur)
for i, b in enumerate(bs):
    paths = {r['path'] for r in b}
    vocab = sorted({f"{r['fqn']} ({r['base']})" for r in rows if r['path'] in paths and (r['field'] or not r['generated'])})
    with open(f'{OUT}/batches/batch-{i:03}.txt', 'w') as o:
        o.write('## CONCEPTS NEARBY\n' + '\n'.join(vocab) + '\n\n## TYPES TO DECIDE\n')
        for r in b:
            tag = 'generated' if r['generated'] else 'original'
            o.write(f"{r['fqn']} | base {r['base']} | {tag} | {','.join(r['kinds']) or 'no-resolved-use'}\n")
            for x in r['sites'][:2]:
                o.write('    ' + x + '\n')
print('types', len(cand), 'batches', len(bs), 'max bytes', max(os.path.getsize(f'{OUT}/batches/' + x) for x in os.listdir(f'{OUT}/batches')))
