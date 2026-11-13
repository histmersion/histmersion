import re, sys, collections
log=open(sys.argv[1],errors='ignore').read()
log=re.sub(r'\x1b\[[0-9;]*m','',log)
blocks=re.split(r'\n\[error\] -- ',log)
need=collections.defaultdict(set)
for b in blocks:
    m=re.match(r'(?:\[E\d+\] )?[^:]*: (/[^:]+\.scala):\d+:\d+',b)
    if not m: continue
    f=m.group(1)
    if re.search(r'No given instance of type com\.github\.plokhotnyuk\.jsoniter_scala\.core\.Json(Value|Key)Codec',b) or re.search(r'No given instance of type[^\n]*\n[^\n]*JsonValueCodec',b):
        need[f].add('import com.histmersion.api.json.NewtypeCodecs.given')
    if re.search(r'value (as|noSpaces|spaces2|asJson|cursor|dropNullValues|deepMerge|toList|filterKeys|fromFields|fromDoubleOrNull|updated|contains) is not a member of',b):
        need[f].add('import com.histmersion.util.json.RawJson.*')
    if re.search(r'value (asJson|as) is not a member of', b) and re.search(r'extension method was tried', b):
        need[f].add('import com.histmersion.api.json.NewtypeCodecs.given')
    if re.search(r'Not found: (type )?Cursor\b',b): need[f].add('import com.histmersion.util.json.Cursor')
    if re.search(r'Not found: RawJson\b',b): need[f].add('import com.histmersion.util.json.RawJson')
    if re.search(r'Not found: (type )?JsonValueCodec\b',b): need[f].add('import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec')
    if re.search(r'Not found: (type )?JsonKeyCodec\b',b): need[f].add('import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec')
    if re.search(r'Not found: (type )?JsonCodec\b',b): need[f].add('import com.histmersion.util.json.JsonCodec')
    if re.search(r'Not found: (type )?Json\b',b): need[f].add('import hearth.kindlings.jsoniterjson.Json')
    if re.search(r'Not found: (type )?JsonObject\b',b): need[f].add('import hearth.kindlings.jsoniterjson.JsonObject')
for f,imps in need.items():
    s=open(f).read(); o=s
    add=''.join(i+'\n' for i in sorted(imps) if i not in s)
    if not add: continue
    s=re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)',lambda m:m.group(1)+add,s,count=1,flags=re.M)
    open(f,'w').write(s); print(f, sorted(imps))
