import re, sys, collections
log=re.sub(r'\x1b\[[0-9;]*m','',open(sys.argv[1],errors='ignore').read())
fix=collections.defaultdict(set)
for m in re.finditer(r'\[error\] -- [^\n]*?(/\S+\.scala):(\d+):(\d+)\s*\n(?:[^\n]*\n){0,3}?\[error\][^\n]*value (getMessage|message) is not a member of String', log):
    fix[m.group(1)].add((int(m.group(2)), m.group(4)))
for f,locs in fix.items():
    lines=open(f).read().split('\n')
    for ln,name in locs:
        l=lines[ln-1]
        l=re.sub(r'\$\{(\w+)\.'+name+r'\}', r'$\1', l)
        l=re.sub(r'(\w+)\.'+name+r'\b', r'\1', l)
        lines[ln-1]=l
    open(f,'w').write('\n'.join(lines)); print(f, len(locs))
