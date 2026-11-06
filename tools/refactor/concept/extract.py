#!/usr/bin/env python3
"""Lists the generated newtypes of the given modules with where each is used; rules decide what they can."""
import csv, json, re, subprocess, sys, collections
csv.field_size_limit(10**8)
R = 'refactor-audit/models/reviews/type-positions-after-native-scalars-2'
OUT = 'refactor-audit/concept-table'
mods = set(sys.argv[1].split(','))
nt = [r for r in csv.DictReader(open(R + '/newtypes.tsv'), delimiter='\t') if r['scope'] == 'production']
orig = {r['definition']: r for r in csv.DictReader(open(R + '/newtype-origins.tsv'), delimiter='\t')}
uses = collections.defaultdict(list)
for u in csv.DictReader(open(R + '/newtype-uses.tsv'), delimiter='\t'):
    if u['scope'] == 'production':
        uses[u['definition']].append(u)
files = [f for f in subprocess.check_output(['git', 'ls-files', 'modules']).decode().split() if f.endswith('.scala') and '/src/main/' in f]
text = {f: open(f, errors='ignore').read().split('\n') for f in files}
sigs = collections.defaultdict(list)
defre = re.compile(r'\b(?:def|val|var|case class|class|case)\s')
for f, ls in text.items():
    for i, l in enumerate(ls):
        if 'RefinedType[' in l or re.match(r'\s*type \w+ = \w+\.T\s*$', l):
            continue
        if defre.search(l):
            for w in set(re.findall(r'[A-Z]\w+', l)):
                sigs[w].append((f, i + 1, l.strip()))
def base(b):
    return b.replace('_root_.', '').split('.')[-1]
rows = []
for n in nt:
    if n['module'] not in mods:
        continue
    lex = n['lexical-name']
    owner, name = lex.rsplit('.', 1)
    pure = 'Pure' in n['constraint']
    ev = orig.get(n['id'], {}).get('evidence', '')
    generated = bool(ev) and '/definitions-' not in ev and 'value-names' not in ev
    us = uses.get(n['id'], [])
    kinds = sorted({u['kind'] for u in us})
    field = any(k == 'constructor-parameter' for k in kinds)
    sameFile = [s for s in sigs.get(name, []) if s[0] == n['path']][:3]
    rows.append(dict(fqn=lex, owner=owner, name=name, base=base(n['base']), pure=pure, generated=generated, field=field, kinds=kinds,
                     path=n['path'], module=n['module'], sites=[s[2][:170] for s in sameFile]))
# concepts that may be targets: every type on a model field or not generated, per module
concepts = collections.defaultdict(list)
for r in rows:
    if r['field'] or not r['generated']:
        concepts[r['module']].append(f"{r['fqn']}\t{r['base']}")
for r in rows:
    d = None
    if not r['pure'] or r['field'] or not r['generated']:
        d = 'keep'
    elif r['base'] == 'Boolean' and 'method-result' in r['kinds'] and not ({'method-parameter', 'lambda-parameter'} & set(r['kinds'])):
        d = 'native'
    r['rule'] = d
json.dump(rows, open(f'{OUT}/rows.json', 'w'), indent=0)
for m, c in concepts.items():
    open(f"{OUT}/concepts-{m.replace('/', '-')}.tsv", 'w').write('\n'.join(sorted(c)) + '\n')
c = collections.Counter(r['rule'] or 'needs table' for r in rows)
print(c, 'distinct needing table', len({r['fqn'] for r in rows if not r['rule']}))
print('needing by last word', collections.Counter(re.findall(r'[A-Z][a-z0-9]*', r['name'])[-1] for r in rows if not r['rule']).most_common(10))
print('owners needing', len({r['owner'] for r in rows if not r['rule']}), 'concepts', {k: len(v) for k, v in concepts.items()})
