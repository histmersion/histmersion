import re,sys
lines=open(sys.argv[1]).read().splitlines()
seen=set(); i=0
while i<len(lines):
    m=re.match(r'\[error\] -- (?:\[E\d+\] )?.*?modules/(\S+):(\d+):(\d+)',lines[i])
    if m and (m.group(1),m.group(2)) not in seen:
        seen.add((m.group(1),m.group(2)))
        j=i+1; blk=[]
        while j<len(lines) and not lines[j].startswith('[error] -- ') and j<i+60:
            blk.append(lines[j][len('[error] '):]); j+=1
        txt='\n'.join(blk)
        code=blk[0] if blk else ''
        def grab(lbl):
            k=txt.find(lbl)
            if k<0: return ''
            rest=txt[k+len(lbl):]
            stop=min([x for x in [rest.find('Second type:'),rest.find('Possible ways')] if x>=0] or [len(rest)])
            t=rest[:stop]
            t=re.sub(r'\n\s*\|\s*',' ',t); t=re.sub(r'\s+',' ',t).strip()
            t=re.sub(r'io\.github\.iltotore\.iron\.(constraint\.[\w.]+\.)?','',t)
            return t.replace('com.histmersion.','')
        a=grab('First type:'); b=grab('Second type:')
        msg='' if a else re.sub(r'\s+',' ',' '.join(blk[1:6]))[:200]
        print(f"{m.group(1).split('/')[-1]}:{m.group(2)}\t{code.strip()[:170]}\n\t{a}\n\t{b}{msg}")
        i=j; continue
    i+=1
print(len(seen),'sites',file=sys.stderr)
