import re, sys
# files using Json AST helpers get RawJson's extensions; circe cursor names become ours
for f in sys.argv[1:]:
    s=open(f).read(); o=s
    s=s.replace('.hcursor','.cursor')
    s=re.sub(r'\b[AH]Cursor\b','Cursor',s)
    uses = re.search(r'\.(noSpaces|spaces2|asJson|dropNullValues|deepMerge|cursor)\b|Json\.(fromFields|fromDoubleOrNull)|\bCursor\b|filterKeys|RawJson',s)
    pkg_api = re.search(r'^package (com\.histmersion\.)?api\.json\b',s,re.M)
    if uses and 'RawJson.*' not in s and not pkg_api:
        imp='import com.histmersion.util.json.RawJson.*\n'
        if re.search(r'\bCursor\b',s) and 'api.json.Cursor' not in s: imp+='import com.histmersion.util.json.Cursor\n'
        if 'RawJson.' in s.replace('RawJson.*','') and not re.search(r'import com\.histmersion\.api\.json\.(RawJson\n|\{[^}]*RawJson)',s): imp+='import com.histmersion.util.json.RawJson\n'
        s=re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)',r'\1'+imp,s,count=1,flags=re.M)
    if s!=o: open(f,'w').write(s); print(f)
