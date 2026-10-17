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
PREFIX psv: <http://www.wikidata.org/prop/statement/value/>
PREFIX wikibase: <http://wikiba.se/ontology#>
PREFIX schema: <http://schema.org/>
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
        # one query per property: a `VALUES ?prop` join makes the endpoint sort on ?prop and time out.
        # Each time with its PRECISION (a century is not a year) and its RANK (a preferred statement
        # overrides the normal ones, a deprecated one is wrong) — the full statement, not `wdt:`.
        **{f"time_{p}": f"SELECT DISTINCT ?item ?t ?prec ?rank WHERE {{ {s} ?item p:{p} ?st . ?st psv:{p} ?v ; "
                        f"wikibase:rank ?rank . ?v wikibase:timeValue ?t ; wikibase:timePrecision ?prec . }}"
           for p in WD_TIMES},
        **{f"id_{p}": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:{p} ?v . }}" for p in WD_IDS},
        "official": f"SELECT DISTINCT ?item ?name ?start ?end WHERE {{ {s} ?item p:P1448 ?st . ?st ps:P1448 ?name . "
                    f"OPTIONAL {{ ?st pq:P580 ?start }} OPTIONAL {{ ?st pq:P582 ?end }} }}",
        "native": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item wdt:P1705 ?name . }}",
        # what the item IS ("former country in modern-day Spain"): schema:description, in the same
        # languages as the names; the extract keeps English and the language of the native label
        "descriptions": f"SELECT DISTINCT ?item ?d WHERE {{ {s} ?item schema:description ?d . "
                        f"FILTER(LANG(?d) IN ({langs})) }}",
    }


# QLever answers a query that ran out of time with HTTP 429 (or 503/504) and a body that says so —
# the same code it uses for "too many requests". Asking again with the same query only times out again,
# so a timeout switches to PAGES (`LIMIT`/`OFFSET` over a total order) and halves the page on each further
# timeout; a plain "slow down" waits (Retry-After, else a growing pause) and asks again. Both are bounded.
WD_PAGE = 250_000        # rows per page once a query has timed out whole
WD_MIN_PAGE = 5_000      # a page that still times out at this size is given up on
WD_RETRIES = 8           # failed attempts per request (page) before giving up
_TIMEOUT = re.compile(r"time[ds]?[ -]?out|timed out|cancel+ed", re.I)


class HttpFailure(Exception):
    """A non-2xx answer: its status, the start of its body, and its Retry-After header (or None)."""

    def __init__(self, code, detail="", retry_after=None):
        super().__init__(f"HTTP {code}: {detail}")
        self.code, self.detail, self.retry_after = code, detail, retry_after


def classify(code, detail):
    """What a failed answer means: 'timeout' (the QUERY is too big — ask for less), 'busy' (the endpoint
    asks to slow down — wait and ask again) or 'fatal' (anything else — a syntax error, a 404)."""
    if code in (429, 503, 504) and _TIMEOUT.search(detail or ""):
        return "timeout"
    if code in (429, 503):
        return "busy"
    return "fatal"


_SELECT = re.compile(r"^\s*SELECT\s+(?:DISTINCT\s+)?(.*?)\s+WHERE\b", re.I | re.S)


def paged(query, limit, offset):
    """The query asking for one page: `ORDER BY` every projected variable (a page of an unordered
    result is not defined — two requests could overlap or skip rows), then `LIMIT`/`OFFSET`."""
    m = _SELECT.match(query)
    if not m:
        raise ValueError("not a SELECT … WHERE query")
    order = " ".join(v for v in m.group(1).split() if v.startswith("?"))
    return f"{query} ORDER BY {order} LIMIT {limit} OFFSET {offset}"


def fetch_query(query, post, out, sleep=time.sleep, page=None, first_page=WD_PAGE, min_page=WD_MIN_PAGE,
                retries=WD_RETRIES, log=lambda msg: print(msg, file=sys.stderr)):
    """Run one SPARQL SELECT and write its TSV result to the binary file `out`; returns the number of
    requests that succeeded. `post(query_text) -> bytes` asks the endpoint (raising [[HttpFailure]]).

    Whole first (`page=None`); on a TIMEOUT in pages of `first_page` rows, each further timeout halving
    the page (from the same offset: the pages already written stay), never below `min_page`. A BUSY answer
    waits its Retry-After (else 60 s × attempt) and asks again. Each request gets `retries` failed
    attempts; the header line is written once."""
    offset, attempts, done, header = 0, 0, 0, None
    while True:
        text = query if page is None else paged(query, page, offset)
        try:
            data = post(text)
        except HttpFailure as e:
            kind = classify(e.code, e.detail)
            attempts += 1
            if kind == "fatal" or attempts > retries:
                raise RuntimeError(f"HTTP {e.code} after {attempts} attempt(s): {e.detail}") from e
            if kind == "timeout":
                smaller = first_page if page is None else page // 2
                if smaller < min_page:
                    raise RuntimeError(f"timed out at a page of {page} rows (minimum {min_page}): {e.detail}") from e
                log(f"  timed out ({'whole' if page is None else f'{page} rows'}), asking in pages of {smaller}")
                page = smaller
                continue
            wait = int(e.retry_after or 0) if str(e.retry_after or "").isdigit() else 0
            wait = wait or 60 * attempts
            log(f"  HTTP {e.code}, waiting {wait}s")
            sleep(wait)
            continue
        attempts, done = 0, done + 1
        if page is None:
            out.write(data)
            return done
        lines = data.decode("utf-8").splitlines(keepends=True)
        if not lines:
            return done
        if header is None:
            header = lines[0]
            out.write(header.encode("utf-8"))
        rows = lines[1:]
        out.write("".join(rows).encode("utf-8"))
        if len(rows) < page:
            return done
        offset += page


def urllib_post(endpoint, user_agent, timeout=3600):
    """`post` for [[fetch_query]]: a form POST, TSV back; a non-2xx answer becomes [[HttpFailure]]."""
    def post(query):
        body = urllib.parse.urlencode({"query": PREFIXES + query}).encode()
        req = urllib.request.Request(endpoint, data=body, method="POST", headers={
            "User-Agent": user_agent, "Accept": "text/tab-separated-values",
            "Content-Type": "application/x-www-form-urlencoded"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            raise HttpFailure(e.code, e.read()[:300].decode("utf-8", "replace"), e.headers.get("Retry-After")) from e
    return post


def wikidata_fetch(endpoint, out_dir, user_agent="Histmersion/0.1 (gazetteer import)", pause=5.0,
                   retries=WD_RETRIES, post=None, sleep=time.sleep):
    """Run each query once and save its TSV. A generic User-Agent, one request at a time, a pause
    between them, a timeout answered by asking in pages and a "slow down" by waiting
    ([[fetch_query]]). Resumable: a query whose result file is already complete is not asked again
    (results are written to `.part` first). Records the endpoint, the time and each file's sha256 in
    `fetch.json`."""
    post = post or urllib_post(endpoint, user_agent)
    os.makedirs(out_dir, exist_ok=True)
    meta_path = os.path.join(out_dir, "fetch.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    meta.update({"endpoint": endpoint, "files": meta.get("files", {})})
    meta.setdefault("fetched", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    for name, q in wd_queries().items():
        path = os.path.join(out_dir, f"{name}.tsv")
        if os.path.exists(path) and name in meta["files"]:
            continue
        try:
            with open(path + ".part", "wb") as fh:
                fetch_query(q, post, fh, sleep=sleep, retries=retries,
                            log=lambda msg, name=name: print(f"  {name}:{msg}", file=sys.stderr))
        except RuntimeError as e:
            raise RuntimeError(f"{name}: {e}") from e
        os.replace(path + ".part", path)
        meta["files"][name] = {"sha256": sha256(path), "bytes": os.path.getsize(path)}
        with open(meta_path, "w") as fh:
            json.dump(meta, fh, indent=2, sort_keys=True)
        print(f"  {name}: {meta['files'][name]['bytes']} bytes", file=sys.stderr)
        sleep(pause)
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


def wd_range(year, precision):
    """The years a Wikidata time value COVERS, from its precision: 11/10/9 (day, month, year) are that
    year; 8 a decade (1760s → 1760–1769); 7 a century and 6 a millennium, counted the way Wikidata
    names them (18th century → 1701–1800, 3rd millennium BCE → 3000–2001 BCE); anything coarser a
    block of 10^(9-precision) years. `year` is already in historical reckoning (BCE negative)."""
    if year is None:
        return None
    if precision is None or precision >= 9:
        return (year, year)
    a = abs(year)
    if precision in (7, 6):
        unit = 100 if precision == 7 else 1000
        n = (a - 1) // unit + 1 if a > 0 else 1
        lo, hi = (n - 1) * unit + 1, n * unit
    else:
        unit = 10 ** (9 - precision)
        lo = (a // unit) * unit
        hi = lo + unit - 1
        lo = max(lo, 1)
    return (lo, hi) if year > 0 else (-hi, -lo)


def wd_precision(term):
    if not term:
        return None
    text, _ = literal(term)
    try:
        return int(text.strip())
    except ValueError:
        return None


WD_RANK_PREFERRED = "PreferredRank"
WD_RANK_DEPRECATED = "DeprecatedRank"


def wd_best(values):
    """What `wdt:` would say: the preferred statements when there are any, else the normal ones;
    never a deprecated one. `values` are (range, rank) pairs; rank '' = unknown (treated as normal)."""
    live = [(r, k) for r, k in values if WD_RANK_DEPRECATED not in k]
    pref = [r for r, k in live if WD_RANK_PREFERRED in k]
    return pref or [r for r, _ in live]


# A year below 100 stated to the year is often a CENTURY typed as a year ("8" for the 8th century):
# the same failure the findings extractor guards against. Such a value is dropped (the bound becomes
# unknown — never re-read as a century, which would be a guess) when the item's description names the
# century whose number the year is, or when it is 1..21 and its other dates lie at least this many years
# later and within or after that century.
CENTURY_GAP_YEARS = 500
_CENTURY_EN = re.compile(r"\b(\d{1,2})(?:st|nd|rd|th)[- ]century\b", re.I)
_CENTURY_ROMAN = re.compile(r"\b(?:siglo|secolo|s\.)\s+([IVXL]+)\b|\b([IVXL]+)(?:e|ème)?\s+siècle\b|\b([IVXL]+)\.\s*Jahrhundert\b")
_ROMAN = {"I": 1, "V": 5, "X": 10, "L": 50}


def roman(s):
    total, prev = 0, 0
    for ch in reversed(s.upper()):
        v = _ROMAN.get(ch, 0)
        total = total - v if v < prev else total + v
        prev = max(prev, v)
    return total


def centuries_named(texts):
    out = set()
    for t in texts:
        out.update(int(m.group(1)) for m in _CENTURY_EN.finditer(t))
        for m in _CENTURY_ROMAN.finditer(t):
            r = roman(next(g for g in m.groups() if g))
            if r:
                out.add(r)
    return out


def century_as_year(year, precision, other_years, descriptions):
    """Is this value a century stored as a year? (see CENTURY_GAP_YEARS)"""
    if year is None or (precision is not None and precision < 9) or not (0 < abs(year) < 100):
        return False
    # the signature is the century's NUMBER as the year ("8" beside "siglo VIII"); a year merely
    # outside a century the text mentions ("40" beside "continuing into the 4th century") is fine
    if abs(year) > 1 and abs(year) in centuries_named(descriptions):
        return True
    # by the other dates only a CE year that could be a century number (1..21) whose century the
    # later evidence reaches: "8" beside 1769 is; -30 beside 640 (Roman Cyrenaica, 30 BCE–640 CE) is not
    if not (0 < year <= 21):
        return False
    return any(y - year >= CENTURY_GAP_YEARS and y >= (year - 1) * 100 + 1 for y in other_years)


def wd_span(times, name_years=(), descriptions=(), stats=None):
    """An item's (start, end) from its time statements: `times` maps P571/P580/P576/P582 to lists of
    (year, precision, rank). Each side is (earliest, latest) — a year stated to the year has both
    equal — or None when unknown. Start = the earliest of inception/start time, end = the latest of
    dissolved/end time; a century typed as a year is dropped first (counted in stats['century'])."""
    def side(props):
        vals = []
        for p in props:
            for y, prec, rank in times.get(p, []):
                vals.append(((y, prec), rank))
        return wd_best(vals)

    starts, ends = side(("P571", "P580")), side(("P576", "P582"))
    every = [y for y, _ in starts + ends] + list(name_years)

    def clean(vals):
        kept = []
        for y, prec in vals:
            if century_as_year(y, prec, [o for o in every if o != y], descriptions):
                if stats is not None:
                    stats["century"] = stats.get("century", 0) + 1
                continue
            kept.append(wd_range(y, prec))
        return kept

    s, e = clean(starts), clean(ends)
    start = (min(r[0] for r in s), min(r[1] for r in s)) if s else None
    end = (max(r[0] for r in e), max(r[1] for r in e)) if e else None
    if start and end and end[1] < start[0]:
        start, end = end, start
    return start, end


def bound(r):
    """LPF start/end object for an (earliest, latest) range."""
    if r is None:
        return None
    return {"in": str(r[0])} if r[0] == r[1] else {"earliest": str(r[0]), "latest": str(r[1])}


def when_ranges(start, end):
    span = {}
    if start is not None:
        span["start"] = bound(start)
    if end is not None:
        span["end"] = bound(end)
    return {"timespans": [span]} if span else None


def wikidata(src_dir, out):
    stats = {"items": 0, "kept": 0, "names": 0, "links": 0, "dated": 0, "described": 0, "century": 0}
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
                times.setdefault(q, {}).setdefault(prop, []).append(
                    (y, wd_precision(r.get("prec")), r.get("rank") or ""))
    descs = {}
    path = os.path.join(src_dir, "descriptions.tsv")
    if os.path.exists(path):
        for r in tsv_rows(path):
            q = qid(r.get("item"))
            text, lang = literal(r.get("d"))
            if q in core and text and lang in LANGS:
                descs.setdefault(q, {})[lang] = text
    native_langs = {}
    path = os.path.join(src_dir, "native.tsv")
    if os.path.exists(path):
        for r in tsv_rows(path):
            q = qid(r.get("item"))
            _, lang = literal(r.get("name"))
            if q in core and lang in LANGS:
                native_langs.setdefault(q, set()).add(lang)
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
                             times.get(q, {}), ids.get(q, set()), stats,
                             wd_descriptions(descs.get(q, {}), native_langs.get(q, set())))

    stats["kept"] = write_ndjson_gz(out, features())
    return stats


WD_LINK = {"P1566": ("exactMatch", "gn"), "P1584": ("closeMatch", "pl"), "P1667": ("closeMatch", "tgn")}


def wd_descriptions(by_lang, native_langs):
    """LPF `descriptions`: English, then the language(s) of the item's native label (the place's
    own language, where Wikidata has a description in it). An item with neither keeps the first of
    the corpus languages, then the window's modern ones, so a card still says what the place is."""
    langs = (["en"] if "en" in by_lang else []) + sorted(l for l in native_langs if l != "en" and l in by_lang)
    if not langs:
        langs = next(([l] for l in CORPUS_LANGS + MODERN_LANGS + ANCIENT_LANGS if l in by_lang), [])
    return [{"value": by_lang[l], "lang": l} for l in langs]


def _name_years(names):
    out = []
    for n in names:
        for ts in (n.get("when") or {}).get("timespans", []):
            for k in ("start", "end"):
                v = (ts.get(k) or {}).get("in")
                if v is not None:
                    out.append(int(v))
    return out


def wd_feature(q, core, kind, labels, names, times, ids, stats, descriptions=()):
    title = labels.get("en") or next((labels[l] for l in CORPUS_LANGS + ANCIENT_LANGS + MODERN_LANGS
                                      if l in labels), q)
    start, end = wd_span(times, _name_years(names), [d["value"] for d in descriptions], stats)
    props = {"title": title, "kind": kind, "source": WIKIDATA_SOURCE, "class": core["root"]}
    span = when_ranges(start, end)
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
    stats["described"] = stats.get("described", 0) + (1 if descriptions else 0)
    f = {"@id": f"http://www.wikidata.org/entity/{q}", "type": "Feature", "properties": props,
         "types": [{"identifier": f"wd:{core['root']}", "label": kind}],
         "geometry": {"type": "Point", "coordinates": [lon, lat]}, "names": ns}
    if span:
        f["when"] = span
    if links:
        f["links"] = links
    if descriptions:
        f["descriptions"] = list(descriptions)
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
