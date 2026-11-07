#!/usr/bin/env python3
"""Every newtype defined now in the given modules, as `fqn (Base)`.  usage: kernel.py <out file> <module dir>..."""
import re, subprocess, sys
files = [f for f in subprocess.check_output(['git', 'ls-files', 'modules']).decode().split() if f.endswith('.scala') and '/src/main/' in f]
OWN = re.compile(r'(\s*)(?:private(?:\[\w+\])? |protected |final |case |sealed |abstract )*(?:object|class|enum|trait) (\w+)')
NT = re.compile(r'(\s*)(private(?:\[\w+\])? )?object (\w+) extends (?:_root_\.)?(?:io\.github\.iltotore\.iron\.)?RefinedType\[(.+), ')
def clean(b):
    b = b.split(', _root_')[0].split(', Pure')[0]
    return re.sub(r'(?:scala\.|java\.lang\.|java\.util\.)', '', b.replace('_root_.', ''))
out = set()
for f in files:
    if not any(f.startswith(p + '/src/main/') for p in sys.argv[2:]):
        continue
    src = open(f, errors='ignore').read(); pk = re.search(r'^package ([\w.]+)', src, re.M)
    if not pk:
        continue
    st = []
    for l in src.split('\n'):
        if not l.strip():
            continue
        ind = len(l) - len(l.lstrip())
        while st and st[-1][0] >= ind:
            st.pop()
        m = NT.match(l)
        if m and not m.group(2) and not any(x[2] for x in st):
            out.add('.'.join([pk.group(1)] + [x[1] for x in st] + [m.group(3)]) + f' ({clean(m.group(4))})')
        o = OWN.match(l)
        if o:
            st.append((ind, o.group(2), l.lstrip().startswith('private')))
open(sys.argv[1], 'w').write('\n'.join(sorted(out)) + '\n')
print(sys.argv[1], len(out))
