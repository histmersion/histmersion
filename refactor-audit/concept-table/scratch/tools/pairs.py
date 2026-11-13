import re, sys
enc = re.compile(r'^(?P<ind>\s*)given (?P<name>\w+: )?(io\.circe\.)?Encoder\[(?P<t>[^\]]+(?:\[[^\]]*\])?)\] =\s*(io\.circe\.)?Encoder\.encode(?P<base>String|Double|Long|Int|Boolean)\.contramap(\[[^\]]*\])?\((?P<f>.*)\)\s*$', re.M)
dec = r'^\s*given (\w+: )?(io\.circe\.)?Decoder\[{t}\] =\s*(io\.circe\.)?Decoder\.decode{base}\.(?P<op>e?map)\((?P<g>.*)\)\s*$'
kenc = re.compile(r'^(?P<ind>\s*)given (?P<name>\w+: )?(io\.circe\.)?KeyEncoder\[(?P<t>[^\]]+)\] =\s*(io\.circe\.)?KeyEncoder\.encodeKeyString\.contramap(\[[^\]]*\])?\((?P<f>.*)\)\s*$', re.M)
kdec = r'^\s*given (\w+: )?(io\.circe\.)?KeyDecoder\[{t}\] =\s*(io\.circe\.)?KeyDecoder\.decodeKeyString\.(?P<op>e?map)\((?P<g>.*)\)\s*$'
for f in sys.argv[1:]:
    s=open(f).read(); o=s
    for m in list(enc.finditer(s)):
        t=m.group('t'); base=m.group('base')
        d=re.search(dec.format(t=re.escape(t),base=base), s, re.M)
        if not d: continue
        g=d.group('g')
        lam = re.match(r'^\(?(\w+)\)? => (.*)$', g)
        mapped = f"{lam.group(1)} => Right({lam.group(2)})" if lam else f"b => Right(({g})(b))"
        rhs = f"JsonCodec.mapped[{t}, {base}]({m.group('f')}, {g})" if d.group('op')=='emap' else f"JsonCodec.mapped[{t}, {base}]({m.group('f')}, {mapped})"
        line=f"{m.group('ind')}given {m.group('name') or ''}JsonValueCodec[{t}] = {rhs}"
        s=s.replace(m.group(0), line,1)
        s=s.replace(d.group(0)+'\n','',1)
    for m in list(kenc.finditer(s)):
        t=m.group('t')
        d=re.search(kdec.format(t=re.escape(t)), s, re.M)
        if not d: continue
        g=d.group('g')
        rhs = f"JsonCodec.key[{t}]({m.group('f')}, {g})" if d.group('op')=='emap' else f"JsonCodec.key[{t}]({m.group('f')}, k => Right(({g})(k)))"
        s=s.replace(m.group(0), f"{m.group('ind')}given {m.group('name') or ''}JsonKeyCodec[{t}] = {rhs}",1)
        s=s.replace(d.group(0)+'\n','',1)
    if s!=o:
        need=[]
        if 'JsonValueCodec' in s and not re.search(r'jsoniter_scala\.core\.(JsonValueCodec|\{[^}]*JsonValueCodec)',s): need.append('JsonValueCodec')
        if 'JsonKeyCodec' in s and not re.search(r'jsoniter_scala\.core\.(JsonKeyCodec|\{[^}]*JsonKeyCodec)',s): need.append('JsonKeyCodec')
        imp=''
        if need: imp+='import com.github.plokhotnyuk.jsoniter_scala.core.'+(need[0] if len(need)==1 else '{'+', '.join(need)+'}')+'\n'
        if 'api.json.JsonCodec' not in s and not re.search(r'^package (com\.histmersion\.)?api\.json',s,re.M): imp+='import com.histmersion.util.json.JsonCodec\n'
        s=re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)',r'\1'+imp,s,count=1,flags=re.M)
        open(f,'w').write(s); print(f)
