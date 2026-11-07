#!/usr/bin/env python3
"""The next bundles of a big module, whole files in feature order with the foundation first.
usage: feature_bundles.py <log> <round> <agents> <errors per bundle> <path marker>   writes rounds/<round>/bundle-N.txt"""
import collections, os, re, sys
log, rnd, agents, cap, mark = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
t = [re.sub(r'\x1b\[[0-9;]*m', '', l) for l in open(log, errors='ignore').read().split('\n')]
head = re.compile(r'\[error\] -- (?:\[(E\d+)\])?.*?(modules/\S+\.scala):(\d+):(\d+)')
byfile = collections.defaultdict(list); seen = set()
for i, l in enumerate(t):
    m = head.search(l)
    if not m or m.group(2, 3, 4) in seen or mark not in m.group(2):
        continue
    seen.add(m.group(2, 3, 4)); body = []
    for x in t[i + 1:i + 14]:
        if head.search(x) or 'longer explanation' in x:
            break
        x = x.replace('[error]', '').strip()
        if set(x.strip('| ')) <= {'^'} or 'autoRefine' in x:
            continue
        body.append(x[:170])
    byfile[m.group(2)].append(f"{m.group(3)}:{m.group(4)} [{m.group(1) or '-'}] " + ' / '.join(body[:6]))
RANK = ['/util/', '/domain/personal/', '/domain/library/', '/domain/catalogue/', '/domain/media/', '/domain/research/', '/domain/automation/', '/server/']
def rank(f):
    r = next((i for i, k in enumerate(RANK) if k in f), 3)
    return (r, f.rsplit('/', 1)[0], f)
order = sorted(byfile, key=rank); bundles = [[] for _ in range(agents)]; b = 0; n = 0; used = 0
for f in order:
    if n and (n + len(byfile[f]) > cap or len(bundles[b]) >= 32):
        b += 1; n = 0
        if b == agents:
            break
    bundles[b].append(f); n += len(byfile[f]); used += len(byfile[f])
d = f'refactor-audit/concept-table/rounds/{rnd}'; os.makedirs(d, exist_ok=True)
for i, fs in enumerate(bundles):
    with open(f'{d}/bundle-{i}.txt', 'w') as o:
        for f in fs:
            o.write(f'## {f}\n' + '\n'.join(byfile[f]) + '\n\n')
print('errors', len(seen), 'files', len(byfile), 'this round', used, [sum(len(byfile[f]) for f in fs) for fs in bundles], [len(fs) for fs in bundles])
print('first files', [fs[0].split('/com/histmersion/')[-1] for fs in bundles if fs], 'last', [fs[-1].split('/com/histmersion/')[-1] for fs in bundles if fs])
