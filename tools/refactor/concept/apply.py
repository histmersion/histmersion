#!/usr/bin/env python3
"""Applies the concept table: renames each generated newtype to its concept, or to the plain type, across all modules.

usage: apply.py [--write]   Without --write only reports what it would do.
"""
import collections, glob, json, re, subprocess, sys
OUT = 'refactor-audit/concept-table'
WRITE = '--write' in sys.argv
rows = {r['fqn']: r for r in json.load(open(f'{OUT}/rows.json'))}
need = {f for f, r in rows.items() if not r['rule']}
raw = {}
for p in sorted(glob.glob(f'{OUT}/out/*.tsv')):
    for l in open(p):
        a = l.rstrip('\n').split('\t')
        if len(a) == 2 and a[0] in need:
            raw[a[0]] = a[1].strip()
for f, r in rows.items():
    if r['rule'] == 'native':
        raw[f] = 'native'
RENDERER = re.compile(r'(Markdown|Export|Render)')
POSITIONAL = re.compile(r'(Result|Part\d+|Arg\d+|Raw|Str|Value)$')
PURE_KEPT = {f for f, r in rows.items() if r['pure'] and r['rule'] == 'keep'}
why = collections.Counter(); redecide = []
table, new = {}, collections.defaultdict(collections.Counter)
for f, d in raw.items():
    r = rows[f]
    if d == 'native':
        table[f] = 'native'; continue
    t = d[4:] if d.startswith('new:') else d
    inApi = f.startswith('com.histmersion.api.')
    if inApi and t.startswith('com.histmersion.domain.'):
        t = 'com.histmersion.api.' + t[len('com.histmersion.domain.'):]
    elif not inApi and t.startswith('com.histmersion.api.'):
        why['domain type onto api'] += 1; continue
    if t == 'com.histmersion.api.Markdown.Text' and not RENDERER.search(r['owner']):
        why['Markdown.Text outside a renderer'] += 1; redecide.append(f); continue
    if POSITIONAL.search(t):
        why['positional name'] += 1; redecide.append(f); continue
    if t in rows:
        if t in PURE_KEPT and rows[t]['base'] == r['base']:
            table[f] = t
        elif t in table or t in raw:
            why['target itself generated'] += 1; redecide.append(f)
        else:
            why['target constrained or other base'] += 1; redecide.append(f)
        continue
    new[t][r['base']] += 1; table[f] = t
# classifiers work batch by batch, so a concept every batch needs was invented several times: one per base type
SHARED = {'Year', 'Citekey', 'Slug', 'Doi', 'Isbn', 'Pmid', 'Pmcid', 'ArxivId', 'Offset', 'PageSize'}
canon = {}
for t, c in new.items():
    n = t.rsplit('.', 1)[1]
    if n in SHARED:
        for b, k in c.items():
            cur = canon.get((n, b))
            if cur is None or k > cur[1]:
                canon[(n, b)] = (t, k)
for f, r in rows.items():
    if r['rule'] == 'keep' and r['pure'] and f.startswith('com.histmersion.api.') and r['name'] in SHARED and f.count('.') <= 5:
        canon[(r['name'], r['base'])] = (f, 10**9)
for f, t in list(table.items()):
    if t == 'native':
        continue
    n = t.rsplit('.', 1)[1]; c = canon.get((n, rows[f]['base']))
    if n in SHARED and c and f.startswith('com.histmersion.api.'):
        table[f] = c[0]
new = collections.defaultdict(collections.Counter)
for f, t in table.items():
    if t != 'native' and t not in rows:
        new[t][rows[f]['base']] += 1
print('shared concepts', {k[0] + '/' + k[1]: v[0].replace('com.histmersion.', '') for k, v in canon.items()})
newbase = {t: c.most_common(1)[0][0] for t, c in new.items()}
for f, t in list(table.items()):
    if t in newbase and rows[f]['base'] != newbase[t]:
        del table[f]; why['new concept used with two bases'] += 1; redecide.append(f)
json.dump(redecide, open(f'{OUT}/redecide.json', 'w'), indent=0)
print('table', collections.Counter('native' if t == 'native' else 'new' if t in newbase else 'existing' for t in table.values()), 'undecided', len(need) - len(raw), 'set aside', dict(why), 'new concepts', len(newbase))
files = [f for f in subprocess.check_output(['git', 'ls-files', 'modules']).decode().split() if f.endswith('.scala')]
src = {f: open(f, errors='ignore').read() for f in files}
BASES = {'String': '_root_.java.lang.String', 'Int': '_root_.scala.Int', 'Long': '_root_.scala.Long', 'Boolean': '_root_.scala.Boolean', 'Double': '_root_.scala.Double', 'Char': '_root_.scala.Char', 'Byte': '_root_.scala.Byte'}
PLAIN = {'String': 'String', 'Int': 'Int', 'Long': 'Long', 'Boolean': 'Boolean', 'Double': 'Double', 'Char': 'Char', 'Byte': 'Byte'}
# one alternation over every name, longest first, so one pass does them all
names = sorted(table, key=len, reverse=True)
stat = collections.Counter()
if names:
    rx = re.compile(r'(?<![\w.])(_root_\.)?(' + '|'.join(map(re.escape, names)) + r')(?![\w])(\.T\b)?')
    def sub(m):
        f = m.group(2); t = table[f]; rootp = m.group(1) or ''
        after = m.string[m.end():m.end() + 1]
        stat['references'] += 1
        if t == 'native':
            if after == '(' and not m.group(3):
                stat['native constructor'] += 1
                return ''
            return PLAIN.get(rows[f]['base'], rows[f]['base'])
        return rootp + t + (m.group(3) or '')
    for f in src:
        s = src[f]
        if any(True for _ in [0]) and 'com.histmersion' in s:
            src[f] = rx.sub(sub, s)
# definitions of renamed types go
byPath = collections.defaultdict(set)
for f in table:
    byPath[rows[f]['path']].add(rows[f]['name'])
removed = kept_body = 0
for path, ns in byPath.items():
    ls = src[path].split('\n'); out = []; i = 0
    while i < len(ls):
        l = ls[i]
        m = re.match(r'\s*type (\w+) = \1\.T\s*$', l)
        o = re.match(r'(\s*)(?:private(?:\[\w+\])? )?object (\w+) extends \S*RefinedType\[.*\]\s*(:?)\s*$', l)
        if m and m.group(1) in ns and i + 1 < len(ls) and re.search(rf'object {m.group(1)} extends', ls[i + 1]):
            o2 = re.match(r'(\s*).*\]\s*(:?)\s*$', ls[i + 1])
            if o2 and not o2.group(2):
                i += 2; removed += 1; continue
            kept_body += 1
        out.append(l); i += 1
    src[path] = '\n'.join(out)
print('definitions removed', removed, 'with a body, kept', kept_body, 'references', dict(stat))
# new concepts go into the deepest existing owner
def index():
    idx = {}
    for f, s in src.items():
        if '/src/main/' not in f: continue
        pk = re.search(r'^package ([\w.]+)', s, re.M)
        if not pk: continue
        stack = []
        for n, l in enumerate(s.split('\n')):
            m = re.match(r'(\s*)(?:private(?:\[\w+\])? |protected |final |case |sealed |abstract )*(object|class|enum|trait) (\w+)', l)
            if not m: continue
            ind = len(m.group(1))
            while stack and stack[-1][0] >= ind: stack.pop()
            q = '.'.join([pk.group(1)] + [x[1] for x in stack] + [m.group(3)])
            e = idx.setdefault(q, {})
            e[m.group(2)] = (f, n, ind, l)
            stack.append((ind, m.group(3)))
    return idx
idx = index()
grouped = collections.defaultdict(list)
for t, b in newbase.items():
    grouped[t.rsplit('.', 1)[0]].append((t.rsplit('.', 1)[1], b))
drop = set(); inserts = collections.defaultdict(list); append = collections.defaultdict(list); newfiles = collections.defaultdict(list); place = collections.Counter()
def block(ind, items):
    sp = ' ' * ind; o = []
    for n, b in sorted(items):
        o.append(f'{sp}type {n} = {n}.T')
        o.append(f'{sp}object {n} extends _root_.io.github.iltotore.iron.RefinedType[{BASES.get(b, b)}, _root_.io.github.iltotore.iron.constraint.any.Pure]')
    return o
for owner, items in sorted(grouped.items()):
    e = idx.get(owner, {})
    if 'object' in e and e['object'][3].rstrip().endswith(':'):
        f, n, ind, _ = e['object']; inserts[f].append((n + 1, block(ind + 2, items))); place['in existing object'] += 1; continue
    parts = owner.split('.'); depth = len(parts) - 1; chain = []
    while depth > 1 and not ('object' in idx.get('.'.join(parts[:depth]), {}) and idx['.'.join(parts[:depth])]['object'][3].rstrip().endswith(':')):
        depth -= 1
    anc = '.'.join(parts[:depth]); rest = parts[depth:]
    def nest(ind):
        o = []
        for k, r in enumerate(rest):
            o.append(' ' * (ind + 2 * k) + f'object {r}:')
        return o + block(ind + 2 * len(rest), items)
    if 'object' in idx.get(anc, {}):
        f, n, ind, _ = idx[anc]['object']; inserts[f].append((n + 1, nest(ind + 2))); place['nested in an ancestor'] += 1
    else:
        tops = [v for k, v in idx.items() if k == owner]
        if tops and owner.count('.') >= 1 and list(tops[0].values())[0][2] == 0:
            f = list(tops[0].values())[0][0]; append[f].append(block(0, []) + nest(-2)); place['companion at end of file'] += 1
        else:
            pkg = '.'.join(p for p in parts if p[0].islower()); first = [p for p in parts if p[0].isupper()]
            rest = first[1:] if first else []
            mod = 'api' if pkg.startswith('com.histmersion.api') else None
            if mod and not first:
                for n, b in items:
                    path = f"modules/{mod}/src/main/scala/{owner.replace('.', '/')}/{n}.scala"
                    newfiles[path].append((owner, None, block(0, [(n, b)])))
                place['top level in a package'] += 1; continue
            if not mod:
                place['no home, dropped'] += 1
                for n, _ in items:
                    drop.add(f'{owner}.{n}')
                print('NO HOME', owner); continue
            path = f"modules/{mod}/src/main/scala/{pkg.replace('.', '/')}/{first[0]}.scala"
            newfiles[path].append((pkg, first[0], nest(0))); place['new file'] += 1
for f, ins in inserts.items():
    ls = src[f].split('\n')
    for n, b in sorted(ins, key=lambda x: -x[0]):
        ls[n:n] = b
    src[f] = '\n'.join(ls)
for f, bs in append.items():
    src[f] = src[f].rstrip('\n') + '\n\n' + '\n'.join(l for b in bs for l in b) + '\n'
print('new concepts placed', dict(place))
if WRITE:
    for f, s in src.items():
        open(f, 'w').write(s)
    for p, bs in newfiles.items():
        pkg, top = bs[0][0], bs[0][1]
        import os; os.makedirs(os.path.dirname(p), exist_ok=True)
        head = f'package {pkg}\n\n' + (f'object {top}:\n' if top else '')
        open(p, 'w').write(head + '\n'.join(l for b in bs for l in b[2]) + '\n')
    print('written')
