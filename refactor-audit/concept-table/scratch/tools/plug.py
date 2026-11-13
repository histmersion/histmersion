import re, sys
for f in sys.argv[1:]:
    s=open(f).read(); o=s
    imported='\n'.join(re.findall(r'^\s*import io\.circe[^\n]*', s, re.M))
    s=re.sub(r'^\s*import io\.circe[^\n]*\n','',s,flags=re.M)
    s=s.replace('.hcursor','.cursor')
    s=re.sub(r'\b[AH]Cursor\b','Cursor',s)
    s=re.sub(r'io\.circe\.parser\s*\n?\s*\.parse\(','RawJson.parse(',s)
    s=re.sub(r'io\.circe\.parser\s*\n?\s*\.decode\[','RawJson.decode[',s)
    s=re.sub(r'(?<![\w.])parser\.parse\(','RawJson.parse(',s)
    s=re.sub(r'(?<![\w.])parser\.decode\[','RawJson.decode[',s)
    if re.search(r'parser\.\{[^}]*\bparse\b|parser\.parse\b(?! as)', imported):
        s=re.sub(r'(?<![\w.])parse\((?!\s*\))','RawJson.parse(',s) if 'def parse(' not in s else s
    if re.search(r'parser\.\{[^}]*\bdecode\b|parser\.decode\b', imported):
        s=re.sub(r'(?<![\w.])decode\[','RawJson.decode[',s)
    m=re.search(r'parser\.parse as (\w+)', imported)
    if m: s=re.sub(r'(?<![\w.])'+m.group(1)+r'\(', 'RawJson.parse(', s)
    s=s.replace('io.circe.JsonObject','JsonObject').replace('io.circe.Json','Json')
    s=re.sub(r'io\.circe\.(Decoder|Encoder)\[','JsonValueCodec[',s)
    if s!=o:
        imps=['import com.histmersion.util.json.RawJson','import com.histmersion.util.json.RawJson.*','import com.histmersion.util.json.JsonCodec.given']
        if re.search(r'\bCursor\b',s): imps.append('import com.histmersion.util.json.Cursor')
        names=[n for n in ['Json','JsonObject'] if re.search(r'(?<![\w.])'+n+r'\b',s)]
        if names: imps.append('import hearth.kindlings.jsoniterjson.'+(names[0] if len(names)==1 else '{'+', '.join(names)+'}'))
        if 'JsonValueCodec' in s: imps.append('import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec')
        add=''.join(i+'\n' for i in imps if i not in s)
        s=re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)',lambda mm:mm.group(1)+add,s,count=1,flags=re.M)
        open(f,'w').write(s); print(f)
