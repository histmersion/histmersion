#!/usr/bin/env python3
"""Adds the compare-by-value import to test files where the compiler refused to compare a newtype with a literal.
usage: compare_by_value.py <build.log> <import line>"""
import re, sys
t = [re.sub(r'\x1b\[[0-9;]*m', '', l) for l in open(sys.argv[1], errors='ignore')]
imp = sys.argv[2]; n = 0
fs = {m.group(1) for l in t if (m := re.search(r'\[error\] -- \[E172\].*?(/\S+/src/test/\S+\.scala):', l))}
for f in fs:
    s = open(f).read()
    if imp in s: continue
    s, k = re.subn(r'^((?:package [^\n]+\n)+)', lambda m: m.group(1) + '\n' + imp + '\n', s, count=1, flags=re.M); n += k
    open(f, 'w').write(s)
print('imports added', n, 'of', len(fs))
