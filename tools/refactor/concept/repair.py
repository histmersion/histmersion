#!/usr/bin/env python3
"""Fixes what compiler diagnostics fully determine after a type became plain: a leftover `.value`, an emptied object, a
`val X: T.type = T` alias of a plain type.  usage: repair.py <build.log>"""
import collections, re, subprocess, sys
log = open(sys.argv[1], errors='ignore').read().split('\n')
ansi = re.compile(r'\x1b\[[0-9;]*m')
log = [ansi.sub('', l) for l in log]
head = re.compile(r'\[error\] -- .*?(/\S+\.scala):(\d+):(\d+)')
edits = collections.defaultdict(set)
for i, l in enumerate(log):
    m = head.search(l)
    if not m: continue
    body = ' '.join(log[i + 1:i + 8])
    if 'value value is not a member of' in body or 'method value' in body and 'cannot be accessed as a member' in body:
        edits[m.group(1)].add((int(m.group(2)), int(m.group(3))))
n = 0
for f, ps in edits.items():
    ls = open(f).read().split('\n')
    for ln, col in sorted(ps, reverse=True):
        s = ls[ln - 1]
        # the diagnostic's column points at the selected name
        if s[col:col + 5] == 'value' and s[col - 1:col] == '.' and not re.match(r'\w', s[col + 5:col + 6] or ' '):
            ls[ln - 1] = s[:col - 1] + s[col + 5:]; n += 1
    open(f, 'w').write('\n'.join(ls))
print('leftover .value removed', n)
files = [f for f in subprocess.check_output(['git', 'ls-files', 'modules']).decode().split() if f.endswith('.scala')]
empt = alias = 0
PLAIN = 'String|Int|Long|Boolean|Double|Char|Byte'
for f in files:
    s = open(f, errors='ignore').read(); o = s
    s, k = re.subn(rf'^[ \t]*(?:private(?:\[\w+\])? )?val \w+: (?:{PLAIN})\.type = (?:{PLAIN})[ \t]*\n', '', s, flags=re.M); alias += k
    while True:
        ls = s.split('\n'); kill = None
        for i, l in enumerate(ls):
            m = re.match(r'(\s*)(?:private(?:\[\w+\])? )?object \w+:\s*$', l)
            if m:
                j = i + 1
                while j < len(ls) and not ls[j].strip(): j += 1
                if j >= len(ls) or len(ls[j]) - len(ls[j].lstrip()) <= len(m.group(1)):
                    kill = i; break
        if kill is None: break
        del ls[kill]; s = '\n'.join(ls); empt += 1
    if s != o: open(f, 'w').write(s)
print('empty objects removed', empt, 'plain-type aliases removed', alias)
