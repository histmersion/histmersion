#!/usr/bin/env python3
"""Wraps a literal where the compiler says a newtype is required: `"x"` becomes `a.b.X("x")`. It never changes the value.
usage: wrap_literals.py <build.log> [path prefix]"""
import collections, re, sys
t = [re.sub(r'\x1b\[[0-9;]*m', '', l) for l in open(sys.argv[1], errors='ignore').read().split('\n')]
prefix = sys.argv[2] if len(sys.argv) > 2 else ''
head = re.compile(r'\[error\] -- \[E007\].*?(/\S+\.scala):(\d+):(\d+)')
LIT = re.compile(r'^("(?:[^"\\]|\\.)*"|-?\d+[LdDf]?|-?\d+\.\d+|true|false)$')
edits = collections.defaultdict(dict); skipped = collections.Counter()
for i, l in enumerate(t):
    m = head.search(l)
    if not m or (prefix and prefix not in m.group(1)):
        continue
    b = ' '.join(x.replace('[error]', '').strip(' |') for x in t[i + 1:i + 10])
    f = re.search(r'Found:\s*\((.*?)\s*:\s*(?:String|Int|Long|Double|Boolean)\)\s*Required:\s*(\S+)', b)
    if not f or not LIT.match(f.group(1)):
        continue
    req = re.sub(r'\.T$', '', f.group(2))
    if not req.startswith('com.histmersion.') or '[' in req:
        skipped['required type not a plain path'] += 1
        continue
    edits[m.group(1)][(int(m.group(2)), int(m.group(3)))] = (f.group(1), req)
n = 0
for path, es in edits.items():
    ls = open(path).read().split('\n')
    for (ln, col), (lit, req) in sorted(es.items(), reverse=True):
        s = ls[ln - 1]
        if not s[col:].startswith(lit):
            skipped['literal not at the reported column'] += 1
            continue
        ls[ln - 1] = f'{s[:col]}{req}({lit}){s[col + len(lit):]}'; n += 1
    open(path, 'w').write('\n'.join(ls))
print('literals wrapped', n, 'in', len(edits), 'files; skipped', dict(skipped))
