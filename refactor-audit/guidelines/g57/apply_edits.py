# usage: apply_edits.py edits.txt ; format: "### <file basename>" then lines "<lineno>|<old>|<new>" (old must occur in that line)
import sys,glob
cur=None; src=None; path=None; bad=0
def flush():
    if path: open(path,'w').write('\n'.join(src))
for raw in open(sys.argv[1]).read().split('\n'):
    if raw.startswith('### '):
        flush()
        b=raw[4:].strip(); ps=[p for p in glob.glob('modules/**/src/test/**/'+b,recursive=True) if '/target/' not in p]; ps=[p for p in ps if 'modules/server/' in p] if len(ps)>1 and any('modules/server/' in p for p in ps) else ps
        assert len(ps)==1,(b,ps); path=ps[0]; src=open(path).read().split('\n'); continue
    if not raw.strip() or raw.startswith('#'): continue
    n,old,new=raw.split('|',2); n=int(n)
    if old not in src[n-1]: print('MISS',path,n,old); bad+=1; continue
    src[n-1]=src[n-1].replace(old,new,1)
flush(); print('bad',bad)
