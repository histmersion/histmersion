import re, sys, pathlib
def rewrite(s):
    o = s
    s = re.sub(r'import hearth\.kindlings\.circederivation\.\{Configuration, KindlingsCodecAsObject\}', 'import com.histmersion.util.json.JsonCodec', s)
    s = s.replace('import hearth.kindlings.circederivation.KindlingsCodecAsObject', 'import com.histmersion.util.json.JsonCodec')
    s = re.sub(r'^import hearth\.kindlings\.circederivation\.Configuration\n', '', s, flags=re.M)
    s = re.sub(r'^import com\.histmersion\.api\.given_Configuration\n', '', s, flags=re.M)
    s = s.replace('import sttp.tapir.json.circe.*', 'import sttp.tapir.json.jsoniter.*')
    s = re.sub(r'^import sttp\.tapir\.json\.circe\.schemaForCirceJson\n', '', s, flags=re.M)
    s = re.sub(r'\bKindlingsCodecAsObject\b', 'JsonCodec', s)
    s = re.sub(r'^\s*given (\w+: )?Decoder\[[^\]]+\] = WireEnum\.decoder\n', '', s, flags=re.M)
    s = re.sub(r'given (\w+)?(: )?Encoder\[([^\]]+)\] = WireEnum\.encoder', lambda m: 'given ' + (m.group(1) or '') + (m.group(2) or '') + 'JsonValueCodec[' + m.group(3) + '] = WireEnum.codec', s)
    s = re.sub(r'given (\w+: )?(io\.circe\.)?Codec\.AsObject\[', lambda m: 'given ' + (m.group(1) or '') + 'JsonCodec[', s)
    if s != o:
        uses_jvc = 'JsonValueCodec' in s
        if uses_jvc and 'import com.github.plokhotnyuk.jsoniter_scala.core' not in s:
            s = re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)', r'\1import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec\n', s, count=1, flags=re.M)
        if 'derives' in s and re.search(r'\bJsonCodec\b', s) and 'api.json.JsonCodec' not in s and not re.search(r'^package api\.json', s, re.M):
            s = re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)', r'\1import com.histmersion.util.json.JsonCodec\n', s, count=1, flags=re.M)
    # drop circe imports; Json AST import added where Json is used
    s2 = re.sub(r'^import io\.circe[^\n]*\n', '', s, flags=re.M)
    if s2 != s:
        s = s2
        if re.search(r'(?<![\w.])Json(Object)?\b', s) and 'hearth.kindlings.jsoniterjson' not in s:
            names = ['Json'] + (['JsonObject'] if re.search(r'\bJsonObject\b', s) else [])
            imp = 'import hearth.kindlings.jsoniterjson.' + (names[0] if len(names) == 1 else '{' + ', '.join(names) + '}')
            s = re.sub(r'^(package [^\n]+\n(?:package [^\n]+\n)*)', r'\1' + imp + '\n', s, count=1, flags=re.M)
    return s
for f in sys.argv[1:]:
    p = pathlib.Path(f)
    t = p.read_text()
    n = rewrite(t)
    if n != t:
        p.write_text(n)
        print('rewrote', f)
