import re,sys
lines=open(sys.argv[1]).read().splitlines()
seen=set(); i=0
while i<len(lines):
    m=re.match(r'\[error\] -- (?:\[E\d+\] )?.*?modules/(\S+):(\d+):(\d+)',lines[i])
    if m and (m.group(1),m.group(2)) not in seen:
        seen.add((m.group(1),m.group(2)))
        out=[f"## {m.group(1)}:{m.group(2)}"]
        j=i+1
        while j<len(lines) and not lines[j].startswith('[error] -- ') and j<i+40:
            t=lines[j][len('[error] '):]
            if re.match(r'\s*\|\s*(First type|Second type|Possible)',t) or out[-1].startswith('  T'):
                pass
            out.append(t)
            j+=1
        # keep from code line to 'Possible ways'
        body='\n'.join(out)
        body=body.split('Possible ways')[0]
        print(body.rstrip()[:900]); print()
        i=j; continue
    i+=1
print(len(seen),'sites',file=sys.stderr)
