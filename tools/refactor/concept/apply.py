#!/usr/bin/env python3
"""Applies the concept table: renames each generated newtype to its concept, or to the plain type, across all modules.

usage: apply.py [--write]   Without --write only reports what it would do.
"""
import collections, glob, json, re, subprocess, sys
import os
OUT = os.environ.get('CONCEPT_OUT', 'refactor-audit/concept-table')
STAGE_MODULES = set(os.environ.get('CONCEPT_MODULES', 'api').split(','))
WRITE = '--write' in sys.argv
rows = {r['fqn']: r for r in json.load(open(f'{OUT}/rows.json'))}
for r in rows.values():
    if r['base'].endswith(']') and not r['base'].startswith('Array['):
        r['base'] = 'Array[' + r['base']
need = {f for f, r in rows.items() if not r['rule']}
raw = {}
for p in sorted(glob.glob(f'{OUT}/out/*.tsv')):
    for l in open(p):
        a = l.rstrip('\n').split('\t')
        if len(a) == 2 and a[0] in need:
            raw[a[0]] = a[1].strip()
second = {}
for p in sorted(glob.glob(f'{OUT}/out2/*.tsv')):
    for l in open(p):
        a = l.rstrip('\n').split('\t')
        if not (len(a) == 2 and a[0] in rows and rows[a[0]]['pure'] and a[1].strip() != 'keep' and rows[a[0]]['module'] in STAGE_MODULES):
            continue
        f, d = a[0], a[1].strip()
        if d == 'native' and rows[f]['field']:
            continue  # a record's field keeps its type; only predicate results and private helpers go plain
        if d != 'native' and not d.removeprefix('new:').startswith('com.histmersion.'):
            short = d.removeprefix('new:'); head = short.split('.')[0]; parts = f.split('.')
            prefix = '.'.join(parts[:parts.index(head)]) if head in parts else f.rsplit('.', 1)[0].rsplit('.', 1)[0] if False else '.'.join(p for p in parts if p[0].islower())
            d = 'new:' + prefix + '.' + short
        second[f] = d
raw.update(second)
for f, r in rows.items():
    if r['rule'] == 'native':
        raw[f] = 'native'
RENDERER = re.compile(r'(Markdown|Export|Render)')
POSITIONAL = re.compile(r'(Result|Part\d+|Arg\d+|Raw|Str|Value)$')
PURE_KEPT = {f for f, r in rows.items() if r['pure'] and r['rule'] == 'keep' and f not in second}
why = collections.Counter(); redecide = []; chain = {}
KERNEL = {}
if os.path.exists(f'{OUT}/concepts-kernel.txt'):
    for l in open(f'{OUT}/concepts-kernel.txt'):
        m = re.match(r'(\S+) \((.*)\)', l)
        if m:
            KERNEL[m.group(1)] = m.group(2)
YEAR = 'com.histmersion.api.catalogue.Year'
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
    if t == 'com.histmersion.api.Markdown.Text' and RENDERER.search(r['owner']):
        table[f] = 'native'; continue
    if t == 'com.histmersion.api.Markdown.Text':
        why['Markdown.Text outside a renderer'] += 1; redecide.append(f); continue
    if POSITIONAL.search(t):
        why['positional name'] += 1; redecide.append(f); continue
    if inApi and t.rsplit('.', 1)[1] == 'Year' and r['base'] in ('Int', 'Long'):
        table[f] = YEAR; continue
    if t in rows and t in raw and t not in PURE_KEPT:
        chain[f] = t; continue
    if t in rows:
        if t in PURE_KEPT and rows[t]['base'] == r['base']:
            table[f] = t
        elif t in table or t in raw:
            why['target itself generated'] += 1; redecide.append(f)
        else:
            why['target constrained or other base'] += 1; redecide.append(f)
        continue
    new[t][r['base']] += 1; table[f] = t
for f, t in chain.items():
    seen = {f}
    while t in chain and t not in seen:
        seen.add(t); t = chain[t]
    t = table.get(t, t) if t in raw else t
    if t in table.values() or t in new or (t in rows and t in PURE_KEPT and rows[t]['base'] == rows[f]['base']):
        table[f] = t
    elif t == 'native':
        table[f] = t
    else:
        why['chain ends nowhere'] += 1
for f, r in rows.items():
    if f.startswith('com.histmersion.api.') and r['name'] == 'Year' and r['pure'] and f != YEAR and r['base'] in ('Int', 'Long'):
        table[f] = YEAR
table.pop(YEAR, None)
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
BASES = {'Array[Byte]': '_root_.scala.Array[_root_.scala.Byte]', 'Array[Int]': '_root_.scala.Array[_root_.scala.Int]', 'String': '_root_.java.lang.String', 'Int': '_root_.scala.Int', 'Long': '_root_.scala.Long', 'Boolean': '_root_.scala.Boolean', 'Double': '_root_.scala.Double', 'Char': '_root_.scala.Char', 'Byte': '_root_.scala.Byte'}
PLAIN = {'String': 'String', 'Int': 'Int', 'Long': 'Long', 'Boolean': 'Boolean', 'Double': 'Double', 'Char': 'Char', 'Byte': 'Byte'}
def index():
    idx = {}
    for f, s in src.items():
        if '/src/main/' not in f: continue
        pk = re.search(r'^package ([\w.]+)', s, re.M)
        if not pk: continue
        stack = []
        for n, l in enumerate(s.split('\n')):
            m = re.match(r'(\s*)(?:private(?:\[\w+\])? |protected |final |case |sealed |abstract )*(object|class|enum|trait|type) (\w+)', l)
            if not m: continue
            ind = len(m.group(1))
            while stack and stack[-1][0] >= ind: stack.pop()
            q = '.'.join([pk.group(1)] + [x[1] for x in stack] + [m.group(3)])
            e = idx.setdefault(q, {})
            e[m.group(2)] = (f, n, ind, l)
            stack.append((ind, m.group(3)))
    return idx
# a new owner must not shadow a real type of the same name: its concepts go onto that type
idx0 = index()
byShort = collections.defaultdict(list)
for q, e in idx0.items():
    byShort[q.rsplit('.', 1)[1]].append(q)
moved = ambiguous = 0
for t in list(newbase):
    owner, n = t.rsplit('.', 1); short = owner.rsplit('.', 1)[1]
    if owner in idx0 or short[0].islower():
        continue
    cands = [q for q in byShort[short] if q.startswith('com.histmersion.api.') == owner.startswith('com.histmersion.api.') and 'object' in idx0[q]]
    if len(cands) > 1:
        same = [q for q in cands if q.rsplit('.', 1)[0] == owner.rsplit('.', 1)[0]]
        cands = same or [q for q in cands if q.count('.') == min(c.count('.') for c in cands)][:1]
    if not cands:
        if byShort[short]:
            ambiguous += 1
        continue
    t2 = cands[0] + '.' + n
    for f, v in list(table.items()):
        if v == t:
            table[f] = t2
    newbase[t2] = newbase.pop(t); moved += 1
print('new concepts moved onto their real owner', moved, 'owner exists only as a non-object', ambiguous)
WORD = {'String': 'Text', 'Int': 'Count', 'Long': 'Number', 'Boolean': 'Flag', 'Double': 'Amount', 'Array[Byte]': 'Bytes'}
reused = renamed = 0
for t in list(newbase):
    if t not in idx0:
        continue
    b = newbase.pop(t)
    if t in KERNEL:
        if KERNEL[t] == b:
            reused += 1; continue
        for f, v in list(table.items()):
            if v == t:
                del table[f]
        why['kernel target with another base'] += 1; continue
    if t in rows and t in PURE_KEPT and rows[t]['base'] == b:
        t2 = t; reused += 1
    else:
        t2 = t + WORD.get(b, 'Value'); renamed += 1
        if t2 not in idx0:
            newbase[t2] = b
    for f, v in list(table.items()):
        if v == t:
            table[f] = t2
print('new concepts that already existed: reused', reused, 'renamed', renamed)
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
# definitions of renamed types go, matched by full path: two owners in one file may share a short name
byPath = collections.defaultdict(set)
for f in table:
    byPath[rows[f]['path']].add(f)
removed = kept_body = aliased = 0
OWNER = re.compile(r'(\s*)(?:private(?:\[\w+\])? |protected |final |case |sealed |abstract )*(object|class|enum|trait) (\w+)')
for path, fs in byPath.items():
    ls = src[path].split('\n'); out = []; i = 0; stack = []
    pk = re.search(r'^package ([\w.]+)', src[path], re.M).group(1)
    while i < len(ls):
        l = ls[i]
        if l.strip():
            ind = len(l) - len(l.lstrip())
            while stack and stack[-1][0] >= ind:
                stack.pop()
        m = re.match(r'(\s*)type (\w+) = \2\.T\s*$', l)
        if m and i + 1 < len(ls) and re.search(rf'object {m.group(2)} extends', ls[i + 1]):
            fq = '.'.join([pk] + [x[1] for x in stack] + [m.group(2)])
            o2 = re.match(r'(\s*).*\]\s*(:?)\s*$', ls[i + 1])
            if fq in fs and o2 and not o2.group(2):
                sp, n = m.group(1), m.group(2); t = table[fq]
                if fq in second and t != 'native' and rows[fq]['rule'] == 'keep':
                    out += [f'{sp}type {n} = {t}', f'{sp}val {n}: _root_.{t}.type = _root_.{t}']; aliased += 1
                elif fq in second and rows[fq]['rule'] == 'keep':
                    b = PLAIN.get(rows[fq]['base'], rows[fq]['base'])
                    out += [f'{sp}type {n} = {b}', f'{sp}object {n}:', f'{sp}  inline def apply(v: {b}): {b} = v']; aliased += 1
                else:
                    removed += 1
                i += 2; continue
            if fq in fs:
                kept_body += 1
        o = OWNER.match(l)
        if o:
            stack.append((len(o.group(1)), o.group(3)))
        out.append(l); i += 1
    src[path] = '\n'.join(out)
print('forwarding aliases', aliased)
print('definitions removed', removed, 'with a body, kept', kept_body, 'references', dict(stat))
# new concepts go into the deepest existing owner
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
            newfiles[path].append((pkg, first[0], nest(2))); place['new file'] += 1
for f, ins in inserts.items():
    ls = src[f].split('\n')
    for n, b in sorted(ins, key=lambda x: -x[0]):
        ls[n:n] = b
    src[f] = '\n'.join(ls)
for f, bs in append.items():
    src[f] = src[f].rstrip('\n') + '\n\n' + '\n'.join(l for b in bs for l in b) + '\n'
print('new concepts placed', dict(place))
Y = 'modules/api/src/main/scala/com/histmersion/api/catalogue/Year.scala'
src[Y] = src[Y].replace('RefinedType[Int, Pure]', 'RefinedType[Long, Pure]')
if WRITE:
    for f, s in src.items():
        open(f, 'w').write(s)
    for p, bs in newfiles.items():
        pkg, top = bs[0][0], bs[0][1]
        import os; os.makedirs(os.path.dirname(p), exist_ok=True)
        head = f'package {pkg}\n\n' + (f'object {top}:\n' if top else '')
        open(p, 'w').write(head + '\n'.join(l for b in bs for l in b[2]) + '\n')
    print('written')
