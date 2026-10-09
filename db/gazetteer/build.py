#!/usr/bin/env python3
"""Build the pinned gazetteer EXTRACTS (#1064) from GeoNames and Wikidata.

    ./build.py geonames <allCountries.zip> <alternateNamesV2.zip> <out.ndjson.gz>
    ./build.py wikidata-fetch <endpoint> <out-dir>          # the SPARQL result files (TSV), see below
    ./build.py wikidata <dir of TSV results> <out.ndjson.gz>
    ./build.py pin <extracts.json> <name> <file> [key=value …]   # record version, sha256, size, counts

Each extract is Linked Places Format, ONE Feature PER LINE, gzip-compressed (`*.ndjson.gz`): the
server's gazetteer import job (`histmersion places load geonames|wikidata`) streams it line by line
and links every feature to the places already in the gazetteer by shared identifiers only — a
feature's own `@id` (a GeoNames or Wikidata URI) and its `links` (`exactMatch`/`closeMatch` to
`gn:`, `wd:`, `pl:` (Pleiades) and `tgn:` ids). Nothing is linked by coordinates.

The output is DETERMINISTIC: features sorted by id, names sorted, keys sorted, gzip with no
timestamp — the same input gives the same bytes, so `extracts.json` can pin a sha256. GeoNames
publishes only the current day's dump and Wikidata changes by the minute, so the pin is on OUR
extract, never on an upstream URL. Nothing here touches a database or the network except
`wikidata-fetch`. Stdlib only.

Both extracts are clipped to the research window `BBOX` (Europe, the Mediterranean, the Near
East, Anatolia, the Caucasus, the steppe, Central and South Asia, Egypt and North Africa) and to the
feature kinds that name historical geography; see db/gazetteer/README.md for the measured volumes
behind each filter.
"""
import csv, gzip, hashlib, io, json, os, re, sys, time, urllib.error, urllib.parse, urllib.request, zipfile

csv.field_size_limit(10 ** 7)

# minLon, minLat, maxLon, maxLat: Iceland → the Tarim basin, Sri Lanka/Sahel → the North Cape
BBOX = (-25.0, 5.0, 100.0, 72.0)

# The languages a name is kept in: the corpus's own (en de fr it es pl), the ancient and original
# languages of the research regions, and the modern languages of those regions (a finding often
# names a site by its local spelling). A name with NO language is kept too — GeoNames' bare
# variants are transliterations ("Bogazkoy") a geocoder needs.
CORPUS_LANGS = ["en", "de", "fr", "it", "es", "pl"]
ANCIENT_LANGS = ["grc", "la", "akk", "hit", "sux", "uga", "phn", "arc", "syc", "egy", "cop", "peo", "pal",
                 "xcl", "sa", "pi", "ota", "got", "non", "ang", "gmy", "elx", "xld", "xlu", "hbo", "he"]
MODERN_LANGS = ["el", "tr", "ar", "fa", "ru", "uk", "be", "hy", "ka", "az", "ku", "ckb", "bg", "sr", "mk", "hr",
                "bs", "sl", "sq", "ro", "hu", "cs", "sk", "lt", "lv", "et", "fi", "sv", "no", "da", "nl", "pt",
                "ca", "ga", "cy", "kk", "uz", "ky", "tk", "tg", "ps", "ur", "hi", "ne", "bn", "pa", "mt",
                "am", "ber"]
LANGS = set(CORPUS_LANGS + ANCIENT_LANGS + MODERN_LANGS)
HISTORICAL_LANGS = set(ANCIENT_LANGS)

GEONAMES_SOURCE = "GeoNames (geonames.org), CC BY 4.0"
WIKIDATA_SOURCE = "Wikidata (wikidata.org), CC0"


def in_bbox(lon, lat):
    return BBOX[0] <= lon <= BBOX[2] and BBOX[1] <= lat <= BBOX[3]


def r5(x):
    """1e-5° ≈ 1 m, as db/places/build.py rounds."""
    return round(float(x), 5)


def year_of(s):
    """A signed year from GeoNames' `from`/`to` columns ("1935", "201907", "-500") or None."""
    s = (s or "").strip()
    m = re.match(r"^(-?)(\d{1,4})(\d{2})?(\d{2})?$", s)
    if not m:
        return None
    return int(m.group(1) + m.group(2)) if len(m.group(2)) <= 4 else None


def when(start, end):
    """LPF `when` with only the bounds that are known (an open side is left out, never guessed)."""
    span = {}
    if start is not None:
        span["start"] = {"in": str(start)}
    if end is not None:
        span["end"] = {"in": str(end)}
    return {"timespans": [span]} if span else None


def name_obj(toponym, lang="", span=None, historic=False):
    n = {"toponym": toponym}
    if lang:
        n["lang"] = lang
    if span:
        n["when"] = span
    if historic:
        n["historic"] = True
    return n


def dedup_names(names):
    """One entry per (name, lang, when), sorted — deterministic and free of exact repeats."""
    seen, out = set(), []
    for n in names:
        t = n["toponym"].strip()
        if not t:
            continue
        n["toponym"] = t
        k = (t, n.get("lang", ""), json.dumps(n.get("when"), sort_keys=True))
        if k in seen:
            continue
        seen.add(k)
        out.append(n)
    return sorted(out, key=lambda n: (n["toponym"].lower(), n["toponym"], n.get("lang", ""),
                                      json.dumps(n.get("when"), sort_keys=True)))


def dump_line(feature):
    return json.dumps(feature, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"


def write_ndjson_gz(path, features):
    """Deterministic gzip: no file name, mtime 0."""
    n = 0
    with open(path, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as gz:
            for f in features:
                gz.write(dump_line(f).encode("utf-8"))
                n += 1
    return n


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ── GeoNames ────────────────────────────────────────────────────────────────────────────────────
# allCountries columns: geonameid name asciiname alternatenames latitude longitude feature_class
# feature_code country_code cc2 admin1 admin2 admin3 admin4 population elevation dem timezone moddate
# alternateNamesV2: alternateNameId geonameid isolanguage name isPreferred isShort isColloquial
# isHistoric from to

# Spots (class S) are mostly hotels, schools and banks; only these name historical geography.
GN_SPOTS = {"ANS", "RUIN", "HSTS", "CSTL", "FT", "PYR", "PYRS", "TMPL", "TMB", "MSTY", "SHRN", "RLG", "CTRR",
            "HERM", "CH", "MSQE", "SYG", "PGDA", "MNMT", "WALLA", "BUR", "CAVE", "BDGQ"}
# Ordinary populated places: 2.3 M in the window, most of them hamlets known only by a dot. Kept
# when they carry something more (a population, a Wikidata link, a historical or historical-
# language name); every other P code (seats, capitals, historical/abandoned/destroyed places) is
# kept outright. PPLX (a section of a populated place) is dropped.
GN_ORDINARY_P = {"PPL", "PPLL", "PPLF", "PPLS", "STLMT"}
GN_SKIP_A = {"ADMS", "ZN", "ZNB"}
GN_PSEUDO_LANGS = {"link", "post", "iata", "icao", "faac", "abbr", "phon", "unlc", "tcid", "wkdt", "fr_1793", "lauc"}


def gn_kind(fclass, fcode):
    if fclass == "A":
        return "polity" if fcode.startswith("PCL") else "region"
    if fclass == "L":
        return "region"
    return "site"


def gn_candidate(fclass, fcode):
    """Is the row of a kind the extract may keep (before the names are known)?"""
    if fclass == "P":
        return fcode != "PPLX"
    if fclass == "A":
        return fcode not in GN_SKIP_A
    if fclass == "S":
        return fcode in GN_SPOTS
    if fclass == "L":
        return fcode == "RGNH"
    return False


def gn_rows(all_zip):
    with zipfile.ZipFile(all_zip) as z:
        with z.open("allCountries.txt") as fh:
            for line in io.TextIOWrapper(fh, encoding="utf-8"):
                c = line.rstrip("\n").split("\t")
                if len(c) < 19:
                    continue
                yield c


def gn_alternate_names(alt_zip, wanted):
    """geonameid → (names, wikidata ids) for the candidate ids only."""
    names, wd = {}, {}
    with zipfile.ZipFile(alt_zip) as z:
        with z.open("alternateNamesV2.txt") as fh:
            for line in io.TextIOWrapper(fh, encoding="utf-8"):
                c = line.rstrip("\n").split("\t")
                if len(c) < 4:
                    continue
                gid = int(c[1])
                if gid not in wanted:
                    continue
                lang, nm = c[2].strip(), c[3].strip()
                if lang == "wkdt":
                    if re.match(r"^Q\d+$", nm):
                        wd.setdefault(gid, set()).add(nm)
                    continue
                if lang in GN_PSEUDO_LANGS or not nm:
                    continue
                if lang and lang not in LANGS:
                    continue
                colloquial = len(c) > 6 and c[6] == "1"
                if colloquial:
                    continue
                historic = len(c) > 7 and c[7] == "1"
                span = when(year_of(c[8]) if len(c) > 8 else None, year_of(c[9]) if len(c) > 9 else None)
                names.setdefault(gid, []).append(name_obj(nm, lang, span, historic))
    return names, wd


def gn_keep(fcode, population, names, wd):
    if fcode not in GN_ORDINARY_P:
        return True
    if population > 0 or wd:
        return True
    return any(n.get("historic") or n.get("when") or n.get("lang") in HISTORICAL_LANGS for n in names)


def geonames(all_zip, alt_zip, out):
    stats = {"rows": 0, "window": 0, "candidates": 0, "kept": 0, "names": 0, "links": 0}
    wanted = set()
    for c in gn_rows(all_zip):
        stats["rows"] += 1
        lat, lon = float(c[4]), float(c[5])
        if not in_bbox(lon, lat):
            continue
        stats["window"] += 1
        if gn_candidate(c[6], c[7]):
            wanted.add(int(c[0]))
    stats["candidates"] = len(wanted)
    names, wd = gn_alternate_names(alt_zip, wanted)

    def features():
        rows = []
        for c in gn_rows(all_zip):
            gid = int(c[0])
            if gid not in wanted:
                continue
            population = int(c[14] or 0)
            ns = names.get(gid, [])
            if not gn_keep(c[7], population, ns, wd.get(gid)):
                continue
            rows.append((gid, c, population))
        rows.sort(key=lambda r: r[0])
        for gid, c, population in rows:
            yield gn_feature(gid, c, population, names.get(gid, []), wd.get(gid, set()), stats)

    stats["kept"] = write_ndjson_gz(out, features())
    return stats


def gn_feature(gid, c, population, alt, wd, stats):
    name, ascii_name, fclass, fcode, cc = c[1].strip(), c[2].strip(), c[6], c[7], c[8]
    props = {"title": name, "kind": gn_kind(fclass, fcode), "source": GEONAMES_SOURCE,
             "fcode": f"{fclass}.{fcode}", "modern": True}
    if cc:
        props["ccodes"] = [cc]
    if population > 0:
        props["population"] = population
    ns = [name_obj(name)] + ([name_obj(ascii_name)] if ascii_name and ascii_name != name else []) + list(alt)
    ns = dedup_names(ns)
    links = [{"type": "exactMatch", "identifier": f"wd:{q}"} for q in sorted(wd, key=lambda q: int(q[1:]))]
    stats["names"] += len(ns)
    stats["links"] += len(links)
    f = {"@id": f"https://sws.geonames.org/{gid}/", "type": "Feature", "properties": props,
         "types": [{"identifier": f"geonames:{fclass}.{fcode}", "label": fcode}],
         "geometry": {"type": "Point", "coordinates": [r5(c[5]), r5(c[4])]},
         "names": ns}
    if links:
        f["links"] = links
    return f


# ── Wikidata ────────────────────────────────────────────────────────────────────────────────────
# Fetched with SPARQL against a Wikidata endpoint (default QLever, which has no 60 s limit — the same
# queries time out on query.wikidata.org once settlements are in). Each query is ONE aspect (core
# rows, labels, aliases, times, ids, official names), so no row explosion; they are joined here.

# Root classes and the kind they give (P31 with the P279 subclass closure, computed by the endpoint).
# Ordered: the first root an item reaches decides its kind.
WD_ROOTS = [
    ("Q3024240", "polity"),   # historical country
    ("Q1620908", "region"),   # historical region
    ("Q19953632", "region"),  # former administrative territorial entity
    ("Q15661340", "site"),    # ancient city
    ("Q148837", "site"),      # polis
    ("Q839954", "site"),      # archaeological site
    ("Q486972", "site"),      # human settlement
]
WD_SETTLEMENT = "Q486972"
WD_TIMES = ["P571", "P576", "P580", "P582"]  # inception, dissolved, start time, end time
WD_IDS = ["P1566", "P1584", "P1667"]         # GeoNames, Pleiades, Getty TGN

PREFIXES = """PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX p: <http://www.wikidata.org/prop/>
PREFIX ps: <http://www.wikidata.org/prop/statement/>
PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
"""


def wd_scope():
    roots = " ".join(f"wd:{q}" for q, _ in WD_ROOTS)
    return (f"VALUES ?root {{ {roots} }} ?item wdt:P31/wdt:P279* ?root . ?item wdt:P625 ?c . "
            f"BIND(geof:longitude(?c) AS ?lon) BIND(geof:latitude(?c) AS ?lat) "
            f"FILTER(?lon >= {BBOX[0]} && ?lon <= {BBOX[2]} && ?lat >= {BBOX[1]} && ?lat <= {BBOX[3]})")


def wd_queries():
    langs = ", ".join(f'"{l}"' for l in sorted(LANGS))
    s = wd_scope()
    return {
        "core": f"SELECT DISTINCT ?item ?root ?lon ?lat WHERE {{ {s} }}",
        "labels": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item rdfs:label ?name . FILTER(LANG(?name) IN ({langs})) }}",
        "aliases": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item skos:altLabel ?name . FILTER(LANG(?name) IN ({langs})) }}",
        # one query per property: a `VALUES ?prop` join makes the endpoint sort on ?prop and time out
        **{f"time_{p}": f"SELECT DISTINCT ?item ?t WHERE {{ {s} ?item wdt:{p} ?t . }}" for p in WD_TIMES},
        **{f"id_{p}": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:{p} ?v . }}" for p in WD_IDS},
        "official": f"SELECT DISTINCT ?item ?name ?start ?end WHERE {{ {s} ?item p:P1448 ?st . ?st ps:P1448 ?name . "
                    f"OPTIONAL {{ ?st pq:P580 ?start }} OPTIONAL {{ ?st pq:P582 ?end }} }}",
        "native": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item wdt:P1705 ?name . }}",
    }


def wikidata_fetch(endpoint, out_dir, user_agent="Histmersion/0.1 (gazetteer import)", pause=5.0, retries=8):
    """Run each query once and save its TSV. A generic User-Agent, one request at a time, a pause
    between them, and a back-off on HTTP 429/503 (honouring Retry-After). Resumable: a query whose
    result file is already complete is not asked again (results are written to `.part` first).
    Records the endpoint, the time and each file's sha256 in `fetch.json`."""
    os.makedirs(out_dir, exist_ok=True)
    meta_path = os.path.join(out_dir, "fetch.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    meta.update({"endpoint": endpoint, "files": meta.get("files", {})})
    meta.setdefault("fetched", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    for name, q in wd_queries().items():
        path = os.path.join(out_dir, f"{name}.tsv")
        if os.path.exists(path) and name in meta["files"]:
            continue
        body = urllib.parse.urlencode({"query": PREFIXES + q}).encode()
        for attempt in range(retries):
            req = urllib.request.Request(endpoint, data=body, method="POST", headers={
                "User-Agent": user_agent, "Accept": "text/tab-separated-values",
                "Content-Type": "application/x-www-form-urlencoded"})
            try:
                with urllib.request.urlopen(req, timeout=3600) as r, open(path + ".part", "wb") as fh:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        fh.write(chunk)
                break
            except urllib.error.HTTPError as e:
                detail = e.read()[:300].decode("utf-8", "replace")
                if "timed out" in detail or e.code not in (429, 503) or attempt == retries - 1:
                    raise RuntimeError(f"{name}: HTTP {e.code}: {detail}") from e
                wait = int(e.headers.get("Retry-After") or 0) or 60 * (attempt + 1)
                print(f"  {name}: HTTP {e.code}, waiting {wait}s", file=sys.stderr)
                time.sleep(wait)
        os.replace(path + ".part", path)
        meta["files"][name] = {"sha256": sha256(path), "bytes": os.path.getsize(path)}
        with open(meta_path, "w") as fh:
            json.dump(meta, fh, indent=2, sort_keys=True)
        print(f"  {name}: {meta['files'][name]['bytes']} bytes", file=sys.stderr)
        time.sleep(pause)
    try:
        with urllib.request.urlopen(urllib.request.Request(endpoint + "?cmd=stats",
                                    headers={"User-Agent": user_agent}), timeout=60) as r:
            meta["index"] = json.load(r).get("name-index")
    except Exception:  # noqa: BLE001 — informational only
        pass
    with open(meta_path, "w") as fh:
        json.dump(meta, fh, indent=2, sort_keys=True)
    return meta


_IRI = re.compile(r"^<http://www\.wikidata\.org/entity/(Q\d+)>$")
_LIT = re.compile(r'^"(.*)"(?:@([A-Za-z0-9-]+)|\^\^<[^>]+>)?$', re.S)


def tsv_rows(path):
    """SPARQL TSV results: a header of ?vars, then N-Triples-style terms."""
    with open(path, encoding="utf-8") as fh:
        header = None
        for line in fh:
            cols = line.rstrip("\n").split("\t")
            if header is None:
                header = [h.lstrip("?") for h in cols]
                continue
            yield dict(zip(header, cols))


def qid(term):
    m = _IRI.match(term or "")
    return m.group(1) if m else None


def literal(term):
    """(text, lang) of an RDF literal term; text unescaped."""
    m = _LIT.match(term or "")
    if not m:
        return (term or "").strip().strip('"'), ""
    return unescape(m.group(1)), (m.group(2) or "").lower()


_ESC = re.compile(r'\\(u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8}|[tnr"\\\'])')
_ESC_CHARS = {"t": "\t", "n": "\n", "r": "\r", '"': '"', "\\": "\\", "'": "'"}


def unescape(s):
    """N-Triples/SPARQL-TSV string escapes (\\t \\n \\" \\\\ \\uXXXX) → text."""
    return _ESC.sub(lambda m: chr(int(m.group(1)[1:], 16)) if m.group(1)[0] in "uU" else _ESC_CHARS[m.group(1)], s)


def wd_year(term):
    """A Wikidata time → signed year in HISTORICAL reckoning. Wikidata's RDF writes XSD 1.1 dates,
    where year 0000 is 1 BCE: -0499 is 500 BCE."""
    text, _ = literal(term)
    m = re.match(r"^([+-]?)(\d+)-", text.strip())
    if not m:
        return None
    iso = int(m.group(1) + m.group(2)) if m.group(1) == "-" else int(m.group(2))
    return iso - 1 if iso <= 0 else iso


def wikidata(src_dir, out):
    stats = {"items": 0, "kept": 0, "names": 0, "links": 0, "dated": 0}
    kinds = dict(WD_ROOTS)
    order = {q: i for i, (q, _) in enumerate(WD_ROOTS)}
    core = {}
    for r in tsv_rows(os.path.join(src_dir, "core.tsv")):
        q, root = qid(r.get("item")), qid(r.get("root"))
        if not q or root not in kinds:
            continue
        lon, lat = float(literal(r["lon"])[0]), float(literal(r["lat"])[0])
        cur = core.get(q)
        pt = (r5(lon), r5(lat))
        if cur is None:
            core[q] = {"root": root, "points": {pt}}
        else:
            if order[root] < order[cur["root"]]:
                cur["root"] = root
            cur["points"].add(pt)
    stats["items"] = len(core)
    names = {}

    def add_name(q, n):
        if q in core:
            names.setdefault(q, []).append(n)

    for kind in ("labels", "aliases", "native"):
        path = os.path.join(src_dir, f"{kind}.tsv")
        if not os.path.exists(path):
            continue
        for r in tsv_rows(path):
            q = qid(r.get("item"))
            text, lang = literal(r.get("name"))
            if q and text and (kind == "native" or lang in LANGS):
                add_name(q, name_obj(text, lang if lang in LANGS else ""))
    labels = {}
    for r in tsv_rows(os.path.join(src_dir, "labels.tsv")):
        q = qid(r.get("item"))
        text, lang = literal(r.get("name"))
        if q in core and text:
            labels.setdefault(q, {})[lang] = text
    path = os.path.join(src_dir, "official.tsv")
    if os.path.exists(path):
        for r in tsv_rows(path):
            q = qid(r.get("item"))
            text, lang = literal(r.get("name"))
            if q and text:
                s = wd_year(r["start"]) if r.get("start") else None
                e = wd_year(r["end"]) if r.get("end") else None
                add_name(q, name_obj(text, lang if lang in LANGS else "", when(s, e)))
    times = {}
    for prop in WD_TIMES:
        for r in tsv_rows(os.path.join(src_dir, f"time_{prop}.tsv")):
            q, y = qid(r.get("item")), wd_year(r.get("t"))
            if q in core and y is not None:
                times.setdefault(q, {}).setdefault(prop, []).append(y)
    ids = {}
    for prop in WD_IDS:
        for r in tsv_rows(os.path.join(src_dir, f"id_{prop}.tsv")):
            q = qid(r.get("item"))
            v, _ = literal(r.get("v"))
            if q in core and v:
                ids.setdefault(q, set()).add((prop, v.strip()))

    def keep(q):
        # a plain present-day settlement adds nothing GeoNames lacks, unless it links to another
        # gazetteer, is dated, or has a name in a historical language
        if core[q]["root"] != WD_SETTLEMENT:
            return True
        if q in ids or q in times:
            return True
        return any(n.get("when") or n.get("lang") in HISTORICAL_LANGS for n in names.get(q, []))

    def features():
        for q in sorted(core, key=lambda x: int(x[1:])):
            if not keep(q):
                continue
            yield wd_feature(q, core[q], kinds[core[q]["root"]], labels.get(q, {}), names.get(q, []),
                             times.get(q, {}), ids.get(q, set()), stats)

    stats["kept"] = write_ndjson_gz(out, features())
    return stats


WD_LINK = {"P1566": ("exactMatch", "gn"), "P1584": ("closeMatch", "pl"), "P1667": ("closeMatch", "tgn")}


def wd_feature(q, core, kind, labels, names, times, ids, stats):
    title = labels.get("en") or next((labels[l] for l in CORPUS_LANGS + ANCIENT_LANGS + MODERN_LANGS
                                      if l in labels), q)
    start = min(times.get("P571", []) + times.get("P580", []), default=None)
    end = max(times.get("P576", []) + times.get("P582", []), default=None)
    if start is not None and end is not None and end < start:
        start, end = end, start
    props = {"title": title, "kind": kind, "source": WIKIDATA_SOURCE, "class": core["root"]}
    span = when(start, end)
    if span:
        stats["dated"] += 1
    else:
        props["modern"] = True
    lon, lat = min(core["points"])  # an item with several coordinates: the smallest, deterministically
    ns = dedup_names([name_obj(title, "en" if labels.get("en") == title else "")] + list(names))
    links = sorted(({"type": WD_LINK[p][0], "identifier": f"{WD_LINK[p][1]}:{v}"} for p, v in ids if p in WD_LINK),
                   key=lambda l: (l["identifier"], l["type"]))
    stats["names"] += len(ns)
    stats["links"] += len(links)
    f = {"@id": f"http://www.wikidata.org/entity/{q}", "type": "Feature", "properties": props,
         "types": [{"identifier": f"wd:{core['root']}", "label": kind}],
         "geometry": {"type": "Point", "coordinates": [lon, lat]}, "names": ns}
    if span:
        f["when"] = span
    if links:
        f["links"] = links
    return f


# ── the manifest ────────────────────────────────────────────────────────────────────────────────

def count_features(path):
    n = 0
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for _ in fh:
            n += 1
    return n


def pin(manifest, name, path, extra):
    """Record (or update) one extract in `extracts.json`: its file name, sha256, size and feature
    count, plus any `key=value` given (version, url, licence, attribution, title, citation)."""
    doc = json.load(open(manifest)) if os.path.exists(manifest) else {"extracts": []}
    entry = next((e for e in doc["extracts"] if e["name"] == name), None)
    if entry is None:
        entry = {"name": name}
        doc["extracts"].append(entry)
    entry.update({"file": os.path.basename(path), "sha256": sha256(path), "bytes": os.path.getsize(path),
                  "features": count_features(path)})
    for kv in extra:
        k, _, v = kv.partition("=")
        entry[k] = v
    doc["extracts"].sort(key=lambda e: e["name"])
    with open(manifest, "w") as fh:
        json.dump(doc, fh, indent=2, sort_keys=True, ensure_ascii=False)
        fh.write("\n")
    return entry


def main(argv):
    if len(argv) >= 4 and argv[0] == "geonames":
        print(json.dumps(geonames(argv[1], argv[2], argv[3]), indent=2))
    elif len(argv) >= 3 and argv[0] == "wikidata-fetch":
        print(json.dumps(wikidata_fetch(argv[1], argv[2]), indent=2))
    elif len(argv) >= 3 and argv[0] == "wikidata":
        print(json.dumps(wikidata(argv[1], argv[2]), indent=2))
    elif len(argv) >= 4 and argv[0] == "pin":
        print(json.dumps(pin(argv[1], argv[2], argv[3], argv[4:]), indent=2))
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
