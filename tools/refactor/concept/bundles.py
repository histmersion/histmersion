#!/usr/bin/env python3
"""Splits a compile log's errors into bundles of whole files, one bundle per repair agent.
usage: bundles.py <build.log> <round> <agents> <errors per bundle>"""
import collections, os, re, sys
log, rnd, agents, cap = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
t = [re.sub(r'\x1b\[[0-9;]*m', '', l) for l in open(log, errors='ignore').read().split('\n')]
head = re.compile(r'\[error\] -- (?:\[(E\d+)\])?.*?(modules/\S+\.scala):(\d+):(\d+)')
byfile = collections.defaultdict(list); seen = set(); kinds = collections.Counter()
for i, l in enumerate(t):
    m = head.search(l)
    if not m or m.group(2, 3, 4) in seen:
        continue
    seen.add(m.group(2, 3, 4))
    body = []
    for x in t[i + 1:i + 14]:
        if head.search(x) or 'longer explanation' in x:
            break
        x = x.replace('[error]', '').strip()
        if set(x.strip('| ')) <= {'^'} or x.startswith('| An extension method') or 'autoRefine' in x:
            continue
        body.append(x[:170])
    byfile[m.group(2)].append(f"{m.group(3)}:{m.group(4)} [{m.group(1) or '-'}] " + ' / '.join(body[:6]))
    kinds[m.group(1) or '-'] += 1
print('errors', len(seen), 'files', len(byfile), kinds.most_common(8))
d = f'refactor-audit/concept-table/rounds/{rnd}'; os.makedirs(d, exist_ok=True)
order = sorted(byfile, key=lambda f: -len(byfile[f]))
bundles = [[] for _ in range(agents)]; sizes = [0] * agents; left = []
for f in order:
    i = sizes.index(min(sizes))
    if sizes[i] + len(byfile[f]) > cap and sizes[i] > 0:
        left.append(f); continue
    bundles[i].append(f); sizes[i] += len(byfile[f])
for i, b in enumerate(bundles):
    with open(f'{d}/bundle-{i}.txt', 'w') as o:
        for f in b:
            o.write(f'## {f}\n' + '\n'.join(byfile[f]) + '\n\n')
print('bundles', sizes, 'files left for later rounds', len(left), 'errors left', sum(len(byfile[f]) for f in left))
print('top files', [(f.split('/com/histmersion/')[-1], len(byfile[f])) for f in order[:8]])
