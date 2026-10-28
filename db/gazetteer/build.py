#!/usr/bin/env python3
"""Build the pinned gazetteer EXTRACTS (#1064) from GeoNames, Wikidata and Getty TGN (#1403).

    ./build.py geonames <allCountries.zip> <alternateNamesV2.zip> <out.ndjson.gz>
    ./build.py wikidata-fetch [<endpoint>] <out-dir>        # the SPARQL result files (TSV), see below
    ./build.py wikidata <dir of TSV results> <out.ndjson.gz> [--periodo <PeriodO d.json>]
    ./build.py tgn <TGN explicit.zip> <AAT explicit.zip> <out.ndjson.gz>   # Getty TGN, the N-Triples dumps
    ./build.py pin <extracts.json> <name> <file> [key=value …]   # record version, sha256, size, counts

    --bbox world|window|minLon,minLat,maxLon,maxLat   where places are kept (default: world)
    --langs all|window|en,de,…                        the languages names are kept in (default: all)
    --endpoint URL                                    the SPARQL endpoint (default: WD_ENDPOINT, QLever)
    --periodo FILE                                    a PeriodO dump: dates an item known only by its P2348

An output (and `pin`'s file) given as a bare file name is written to (read from) the directory the server
reads extracts from by default — `$XDG_CACHE_HOME/histmersion/gazetteer`, else
`~/.cache/histmersion/gazetteer` (`cache_dir`); `--out DIR` names another, and a path with a directory
part is used as given.

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

The extracts cover the whole world, with names in every language, by default; `--bbox` and `--langs`
cut a regional one for a small install (`--bbox window --langs window` is the Europe-to-Central-Asia
extract pinned until 2026-10-27). What is kept is cut by KIND, never by place: the feature kinds that
name historical geography; see db/gazetteer/README.md for the measured volumes behind each filter.
"""
import csv, gzip, hashlib, http.client, io, json, os, re, sys, time, urllib.error, urllib.parse, urllib.request, zipfile

csv.field_size_limit(10 ** 7)

# minLon, minLat, maxLon, maxLat. The default is the whole world: every place the sources name,
# wherever it is. `--bbox` builds a regional extract for a small install.
WORLD = (-180.0, -90.0, 180.0, 90.0)
# The region the extracts were cut to until 2026-10-27 (Iceland → the Tarim basin, Sri Lanka/Sahel →
# the North Cape): `--bbox window`.
WINDOW = (-25.0, 5.0, 100.0, 72.0)

# By default a name is kept in EVERY language its source gives (a finding may name a place in any
# script). `--langs` keeps only some, as a regional extract for a small install does; a name with NO
# language is kept either way — GeoNames' bare variants are transliterations ("Bogazkoy") a geocoder needs.
CORPUS_LANGS = ["en", "de", "fr", "it", "es", "pl"]
# The ancient, classical and original languages of the world's past — a name in one is evidence the place
# is historical (the keep rules below), so this list is used whatever `--langs` says.
ANCIENT_LANGS = ["grc", "la", "akk", "hit", "sux", "uga", "phn", "arc", "syc", "egy", "cop", "peo", "pal",
                 "xcl", "sa", "pi", "ota", "got", "non", "ang", "gmy", "elx", "xld", "xlu", "hbo", "he",
                 # East, South and Central Asia, Africa, the Americas
                 "lzh", "och", "ltc", "ojp", "okm", "oko", "kaw", "omy", "xct", "cmg", "otk", "sog", "kho",
                 "txb", "xto", "pra", "pka", "oty", "omx", "obr", "gez", "xmr", "nci", "emy", "qwc"]
# The modern languages of the former window: `--langs window` (with the two lists above).
WINDOW_MODERN_LANGS = ["el", "tr", "ar", "fa", "ru", "uk", "be", "hy", "ka", "az", "ku", "ckb", "bg", "sr", "mk",
                       "hr", "bs", "sl", "sq", "ro", "hu", "cs", "sk", "lt", "lv", "et", "fi", "sv", "no", "da",
                       "nl", "pt", "ca", "ga", "cy", "kk", "uz", "ky", "tk", "tg", "ps", "ur", "hi", "ne", "bn",
                       "pa", "mt", "am", "ber"]
WINDOW_LANGS = frozenset(CORPUS_LANGS + ANCIENT_LANGS + WINDOW_MODERN_LANGS)
HISTORICAL_LANGS = frozenset(ANCIENT_LANGS)
# The languages Wikidata descriptions are fetched in when every name language is kept: the window's and
# the world's widely written ones. One description per item is kept (English, else the native label's
# language, else the first of these), so this caps a query, not what a card can say.
WORLD_MODERN_LANGS = ["zh", "ja", "ko", "vi", "th", "lo", "km", "my", "id", "ms", "jv", "tl", "mn", "bo", "dz",
                      "si", "ta", "te", "kn", "ml", "mr", "gu", "or", "as", "sd", "dv", "sw", "ha", "yo", "ig",
                      "zu", "xh", "af", "so", "om", "ti", "mg", "rw", "ln", "wo", "ff", "qu", "ay", "gn", "nah",
                      "ht", "mi", "haw", "sm", "to", "fj", "iu", "kl", "ug", "tt", "ba", "sah", "eo", "is",
                      "fo", "gl", "eu", "oc", "br", "lb", "fy", "yi", "ast", "hsb", "ia"]
# The language a title is taken from: the first of these that has one (else the first by code).
TITLE_LANGS = ["en", "mul"] + CORPUS_LANGS[1:] + ANCIENT_LANGS + WINDOW_MODERN_LANGS + WORLD_MODERN_LANGS
DESCRIPTION_LANGS = frozenset(CORPUS_LANGS + WINDOW_MODERN_LANGS + WORLD_MODERN_LANGS + ANCIENT_LANGS)
# Wikidata's "mul" (one label for every language, mostly a proper name in its own script) is a name
# with no particular language.
NO_LANG = {"mul", "und", "zxx"}


class Scope:
    """Where and in which languages an extract keeps places and names: `bbox` (minLon, minLat, maxLon,
    maxLat; the world by default) and `langs` (a set, or None for every language)."""

    def __init__(self, bbox=WORLD, langs=None):
        self.bbox = tuple(float(x) for x in bbox)
        self.langs = None if langs is None else frozenset(langs)

    @property
    def world(self):
        return self.bbox == WORLD

    def contains(self, lon, lat):
        b = self.bbox
        return b[0] <= lon <= b[2] and b[1] <= lat <= b[3]

    def lang(self, code):
        """The language a name is kept under: "" for none; None when it is not kept at all."""
        code = (code or "").strip()
        if not code or code in NO_LANG:
            return ""
        if self.langs is None or code in self.langs:
            return code
        return None

    def describe(self):
        where = "world" if self.world else ",".join(f"{x:g}" for x in self.bbox)
        return {"bbox": where, "langs": "all" if self.langs is None else len(self.langs)}


DEFAULT_SCOPE = Scope()


def parse_bbox(s):
    """`world`, `window` or minLon,minLat,maxLon,maxLat."""
    s = (s or "").strip().lower()
    if s in ("", "world"):
        return WORLD
    if s == "window":
        return WINDOW
    parts = [float(x) for x in s.split(",")]
    if len(parts) != 4 or not (parts[0] < parts[2] and parts[1] < parts[3]):
        raise ValueError(f"--bbox '{s}' is not minLon,minLat,maxLon,maxLat (west < east, south < north)")
    if parts[0] < -180 or parts[2] > 180 or parts[1] < -90 or parts[3] > 90:
        raise ValueError(f"--bbox '{s}' is outside the world")
    return tuple(parts)


def parse_langs(s):
    """`all` (None), `window`, or a comma-separated list of language codes."""
    s = (s or "").strip()
    if s.lower() in ("", "all"):
        return None
    if s.lower() == "window":
        return WINDOW_LANGS
    return frozenset(x.strip() for x in s.split(",") if x.strip())


GEONAMES_SOURCE = "GeoNames (geonames.org), CC BY 4.0"
WIKIDATA_SOURCE = "Wikidata (wikidata.org), CC0"


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


def gn_alternate_names(alt_zip, wanted, scope=DEFAULT_SCOPE):
    """geonameid → (names, wikidata ids) for the candidate ids only, in the scope's languages."""
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
                lang = scope.lang(lang)
                if lang is None:
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


def geonames(all_zip, alt_zip, out, scope=DEFAULT_SCOPE):
    stats = {"scope": scope.describe(), "rows": 0, "window": 0, "candidates": 0, "kept": 0, "names": 0, "links": 0}
    wanted = set()
    for c in gn_rows(all_zip):
        stats["rows"] += 1
        lat, lon = float(c[4]), float(c[5])
        if not scope.contains(lon, lat):
            continue
        stats["window"] += 1
        if gn_candidate(c[6], c[7]):
            wanted.add(int(c[0]))
    stats["candidates"] = len(wanted)
    names, wd = gn_alternate_names(alt_zip, wanted, scope)

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

# The endpoint `wikidata-fetch` asks by default (`--endpoint` or a first argument names another). QLever's
# old host, qlever.cs.uni-freiburg.de, answers 308 to here since 2026 (docs/research-gazetteer-greece.md).
WD_ENDPOINT = "https://qlever.dev/api/wikidata"

# Root classes and the kind they give (P31 with the P279 subclass closure, computed by the endpoint).
# Ordered: the first root an item reaches decides its kind and its `class`. The heritage buildings
# (2026-10-28: the castles, monasteries and churches of the Argolis were missing,
# docs/research-gazetteer-greece.md) come after the archaeological site, so an item of the older roots
# keeps the class it had, and before the settlement, so a castle that is also a village is a castle.
# Each id was checked against its Wikidata label on 2026-10-28 (the label is the comment).
WD_ROOTS = [
    ("Q3024240", "polity"),   # historical country
    ("Q1620908", "region"),   # historical region
    ("Q19953632", "region"),  # former administrative territorial entity
    ("Q15661340", "site"),    # ancient city
    ("Q148837", "site"),      # polis
    ("Q839954", "site"),      # archaeological site
    ("Q23413", "site"),       # castle
    ("Q1785071", "site"),     # fort
    ("Q57821", "site"),       # fortification
    ("Q16748868", "site"),    # city walls
    ("Q82117", "site"),       # city gate
    ("Q91312", "site"),       # tower house
    ("Q12518", "site"),       # tower
    ("Q44613", "site"),       # monastery
    ("Q108325", "site"),      # chapel
    ("Q16970", "site"),       # church building
    ("Q32815", "site"),       # mosque
    ("Q34627", "site"),       # synagogue
    ("Q474", "site"),         # aqueduct
    ("Q12280", "site"),       # bridge
    ("Q486972", "site"),      # human settlement
]
WD_SETTLEMENT = "Q486972"
# An item with any heritage designation (P1435) is kept whatever its class: a site the state protects is
# a site, even when Wikidata files it under "building" or "house". Its kind is a site; it has a `class`
# only when it also reaches a root.
WD_HERITAGE = "P1435"
WD_TIMES = ["P571", "P576", "P580", "P582"]  # inception, dissolved, start time, end time
WD_IDS = ["P1566", "P1584", "P1667"]         # GeoNames, Pleiades, Getty TGN
# National and scholarly registers an item is listed in, kept in `properties.registers` (not links: the
# importer joins by gazetteer ids only, and these name no gazetteer place).
WD_REGISTERS = [
    "P13741",  # Archaeological Cadastre (Greece) ID — "164525:Monument"
    "P2186",   # Wiki Loves Monuments ID — "GR-J11-0037"
    "P9951",   # Greek Castles ID (kastra.eu)
    "P8218",   # Archaeology in Greece Online place ID
    "P8068",   # ToposText place ID
    "P1936",   # Digital Atlas of the Roman Empire (DARE) ID
]

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


_WD_LONLAT = "BIND(geof:longitude(?c) AS ?lon) BIND(geof:latitude(?c) AS ?lat)"
# The languages a designation's or a period's own label is read in: English, else Wikidata's "mul".
ITEM_LABEL_LANGS = frozenset({"en", "mul"})


def wd_scope(scope=DEFAULT_SCOPE, band=None):
    """The items of the root classes, or with a heritage designation (P1435, any class; `?root` unbound),
    with a coordinate: in the whole world, or inside the scope's bbox —
    or inside `band`, a part of it (minLon, minLat, maxLon, maxLat; [[split_band]]). A band's east and north
    edges are open, unless they are the scope's own, so a point on the edge between two bands is in one."""
    roots = " ".join(f"wd:{q}" for q, _ in WD_ROOTS)
    where = (f"{{ VALUES ?root {{ {roots} }} ?item wdt:P31/wdt:P279* ?root . }} UNION "
             f"{{ ?item wdt:{WD_HERITAGE} ?designation . }} ?item wdt:P625 ?c .")
    area = band or (None if scope.world else scope.bbox)
    if area is None:
        return where
    east = "<=" if area[2] >= scope.bbox[2] else "<"
    north = "<=" if area[3] >= scope.bbox[3] else "<"
    return (f"{where} {_WD_LONLAT} "
            f"FILTER(?lon >= {area[0]:g} && ?lon {east} {area[2]:g} && ?lat >= {area[1]:g} && ?lat {north} {area[3]:g})")


def split_band(area):
    """Two halves of a bbox, across its longer side (in degrees)."""
    w, s, e, n = area
    if e - w >= n - s:
        mid = float(f"{(w + e) / 2:g}")
        return [(w, s, mid, n), (mid, s, e, n)]
    mid = float(f"{(s + n) / 2:g}")
    return [(w, s, e, mid), (w, mid, e, n)]


def _lang_filter(var, langs):
    if langs is None:
        return ""
    quoted = ", ".join(f'"{l}"' for l in sorted(langs))
    return f" FILTER(LANG(?{var}) IN ({quoted}))"


def wd_queries(scope=DEFAULT_SCOPE, band=None):
    core = wd_scope(scope, band)
    # every aspect joins the item SET, computed first in a subquery: joined to the class closure and the
    # coordinates directly, QLever (2026-10-28, a 30 s limit) sorts the labels before it filters by place
    # and times out even on one square degree; over the subquery that band answers in 9 s
    s = f"{{ SELECT DISTINCT ?item WHERE {{ {core} }} }}"
    names = lambda var: _lang_filter(var, scope.langs)  # noqa: E731
    # a statement (`p:`) query restricts the statements to the items first, in a subquery of its own: joined
    # directly, QLever joins every statement of the property world-wide on ?st first and times out
    # (2026-10-28: P576 timed out even on a band in the sea; over the subquery all of Greece takes 11 s)
    stmt = lambda prop: f"{{ SELECT ?item ?st WHERE {{ {s} ?item p:{prop} ?st . }} }}"  # noqa: E731
    return {
        "core": f"SELECT DISTINCT ?item ?root ?lon ?lat WHERE {{ {core if _WD_LONLAT in core else core + ' ' + _WD_LONLAT} }}",
        "labels": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item rdfs:label ?name .{names('name')} }}",
        "aliases": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item skos:altLabel ?name .{names('name')} }}",
        # one query per property: a `VALUES ?prop` join makes the endpoint sort on ?prop and time out.
        # Each time with its PRECISION (a century is not a year) and its RANK (a preferred statement
        # overrides the normal ones, a deprecated one is wrong) — the full statement, not `wdt:`.
        **{f"time_{p}": f"SELECT DISTINCT ?item ?t ?prec ?rank WHERE {{ {stmt(p)} ?st psv:{p} ?v ; "
                        f"wikibase:rank ?rank . ?v wikibase:timeValue ?t ; wikibase:timePrecision ?prec . }}"
           for p in WD_TIMES},
        **{f"id_{p}": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:{p} ?v . }}" for p in WD_IDS},
        # what the item is (its own P31, for the site type: [[SiteTypes.wikidata]] on the server), the
        # country it is in (P17: which PeriodO scheme dates its P2348), its heritage designations with
        # their dates and rank, its time period (P2348) and the labels of both, and its register ids
        "p31": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:P31 ?v . }}",
        "country": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:P17 ?v . }}",
        "heritage": f"SELECT DISTINCT ?item ?v ?rank ?start ?end WHERE {{ {stmt(WD_HERITAGE)} "
                    f"?st ps:{WD_HERITAGE} ?v ; wikibase:rank ?rank . OPTIONAL {{ ?st pq:P580 ?start }} "
                    f"OPTIONAL {{ ?st pq:P582 ?end }} }}",
        "heritage_labels": f"SELECT DISTINCT ?v ?name WHERE {{ {s} ?item wdt:{WD_HERITAGE} ?v . ?v rdfs:label ?name ."
                           f"{_lang_filter('name', ITEM_LABEL_LANGS)} }}",
        "period": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:P2348 ?v . }}",
        "period_labels": f"SELECT DISTINCT ?v ?name WHERE {{ {s} ?item wdt:P2348 ?v . ?v rdfs:label ?name ."
                         f"{_lang_filter('name', ITEM_LABEL_LANGS)} }}",
        **{f"reg_{p}": f"SELECT DISTINCT ?item ?v WHERE {{ {s} ?item wdt:{p} ?v . }}" for p in WD_REGISTERS},
        "official": f"SELECT DISTINCT ?item ?name ?start ?end WHERE {{ {stmt('P1448')} ?st ps:P1448 ?name . "
                    f"OPTIONAL {{ ?st pq:P580 ?start }} OPTIONAL {{ ?st pq:P582 ?end }} }}",
        "native": f"SELECT DISTINCT ?item ?name WHERE {{ {s} ?item wdt:P1705 ?name . }}",
        # what the item IS ("former country in modern-day Spain"): schema:description, in the names'
        # languages — with every language, in DESCRIPTION_LANGS: the descriptions of every item in every
        # language do not finish on QLever (2026-10-27), and the extract keeps only English and the
        # language of the native label anyway
        # A regional extract asks for every language: over the item-set subquery a filter of DESCRIPTION_LANGS'
        # ~190 codes times out in QLever's query PLANNING even on one square degree (2026-10-28), and a
        # region's descriptions are few enough to fetch whole.
        "descriptions": f"SELECT DISTINCT ?item ?d WHERE {{ {s} ?item schema:description ?d ."
                        f"{_lang_filter('d', scope.langs if scope.langs is not None else (DESCRIPTION_LANGS if scope.world else None))} }}",
    }


# QLever answers a query that ran out of time with HTTP 429 (or 503/504) and a body that says so —
# the same code it uses for "too many requests". Asking again with the same query only times out again,
# so a timeout switches to PAGES (`LIMIT`/`OFFSET` over a total order) and halves the page on each further
# timeout; a plain "slow down" waits (Retry-After, else a growing pause) and asks again. Both are bounded.
WD_PAGE = 250_000        # rows per page once a query has timed out whole
WD_MIN_PAGE = 5_000      # a page that still times out at this size is given up on
WD_RETRIES = 8           # failed attempts per request (page) before giving up
# A world-wide query too big for one answer is asked again for each half of its area, and so on
# ([[fetch_banded]]): ordering the whole result for pages still times out — in query planning — once it
# holds tens of millions of rows (every description of 2.2 M items, 2026-10-27). Below this size (degrees,
# both sides) a band falls back to pages.
WD_MIN_BAND = 0.5
_TIMEOUT = re.compile(r"time[ds]?[ -]?out|timed out|cancel+ed", re.I)
# QLever out of memory for one query ("Tried to allocate 250 kB, but only 180.3 kB were available", HTTP
# 500, 2026-10-27): the query is too big, like a timeout — asking for less is the answer.
_TOO_BIG = re.compile(r"tried to allocate|out of memory|memory limit", re.I)


class QueryTimeout(RuntimeError):
    """The endpoint ran out of time on a query asked whole (`fetch_query(pages=False)`)."""


class HttpFailure(Exception):
    """A non-2xx answer: its status, the start of its body, and its Retry-After header (or None)."""

    def __init__(self, code, detail="", retry_after=None):
        super().__init__(f"HTTP {code}: {detail}")
        self.code, self.detail, self.retry_after = code, detail, retry_after


def classify(code, detail):
    """What a failed answer means: 'timeout' (the QUERY is too big — it ran out of time or of the
    endpoint's memory: ask for less), 'busy' (the endpoint
    asks to slow down, or its gateway lost it (502) — wait and ask again) or 'fatal' (anything else — a syntax error, a 404)."""
    if code in (429, 503, 504) and _TIMEOUT.search(detail or ""):
        return "timeout"
    if code in (500, 503) and _TOO_BIG.search(detail or ""):
        return "timeout"
    if code in (429, 502, 503):
        return "busy"
    return "fatal"


# QLever streams a result with HTTP 200 before it is complete; a query that runs out of time WHILE its rows
# are exported ends in this line and the error, appended to the rows written so far (2026-10-27: the
# world's labels stopped after 632 588 of 18.3 M rows). Such an answer is a failure, never a result.
_EXPORT_ERROR = b"An error has occurred while exporting the query result"


def export_failure(data):
    """The error QLever appended to a truncated result, or None for a complete one."""
    i = data.rfind(_EXPORT_ERROR)
    if i < 0:
        return None
    tail = data[i:].decode("utf-8", "replace")
    return tail.rsplit("The error message was:", 1)[-1].strip() or "an error while exporting the result"


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
                retries=WD_RETRIES, log=lambda msg: print(msg, file=sys.stderr), pages=True):
    """Run one SPARQL SELECT and write its TSV result to the binary file `out`; returns the number of
    requests that succeeded. `post(query_text) -> bytes` asks the endpoint (raising [[HttpFailure]]).

    Whole first (`page=None`); on a TIMEOUT in pages of `first_page` rows, each further timeout halving
    the page (from the same offset: the pages already written stay), never below `min_page`. A BUSY answer
    waits its Retry-After (else 60 s × attempt) and asks again. A 200 whose rows end in QLever's export
    error ([[export_failure]]) counts as that error — a timeout then pages too. Each request gets
    `retries` failed attempts; the header line is written once. `pages=False`: a timeout raises
    [[QueryTimeout]] instead, with nothing written."""
    offset, attempts, done, header = 0, 0, 0, None
    while True:
        text = query if page is None else paged(query, page, offset)
        try:
            data = post(text)
            broken = export_failure(data)
            if broken is not None:
                raise HttpFailure(504 if _TIMEOUT.search(broken) else 500, f"result cut off: {broken}")
        except HttpFailure as e:
            kind = classify(e.code, e.detail)
            attempts += 1
            if kind == "fatal" or attempts > retries:
                raise RuntimeError(f"HTTP {e.code} after {attempts} attempt(s): {e.detail}") from e
            if kind == "timeout":
                if not pages:
                    raise QueryTimeout(e.detail) from e
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


def fetch_banded(name, scope, post, out, sleep=time.sleep, retries=WD_RETRIES, min_band=WD_MIN_BAND,
                 log=lambda msg: print(msg, file=sys.stderr)):
    """Query `name` of [[wd_queries]] over the scope, whole first; a timeout asks again for each half of
    the area ([[split_band]], depth first, so the rows come west to east, south to north), and a band
    smaller than `min_band` on both sides in pages ([[fetch_query]]). One header; returns the requests."""
    header, asked, todo = None, 0, [None]
    while todo:
        band = todo.pop(0)
        area = band or scope.bbox
        small = area[2] - area[0] < min_band and area[3] - area[1] < min_band
        buf = io.BytesIO()
        try:
            asked += fetch_query(wd_queries(scope, band)[name], post, buf, sleep=sleep, retries=retries, log=log,
                                 pages=small)
        except QueryTimeout:
            halves = split_band(area)
            log(f"  timed out on {','.join(f'{x:g}' for x in area)}: asking for each half")
            todo[0:0] = halves
            continue
        head, _, rows = buf.getvalue().partition(b"\n")
        if header is None:
            header = head
            out.write(head + b"\n")
        if rows and not rows.endswith(b"\n"):
            rows += b"\n"
        out.write(rows)
    return asked


def read_body(r):
    """The whole body of an answer. QLever also ends a result that runs out of time while it is exported by
    closing the connection mid-stream (2026-10-28: `IncompleteRead` after 8.5 MB of the labels) — a result
    cut off, so the same [[HttpFailure]] as its appended export error: a timeout, answered by asking for less."""
    try:
        return r.read()
    except (http.client.IncompleteRead, ConnectionResetError) as e:
        raise HttpFailure(504, f"result cut off: the connection closed while the result was exported (timed out): {e!r}"[:300]) from e


def urllib_post(endpoint, user_agent, timeout=3600):
    """`post` for [[fetch_query]]: a form POST, TSV back; a non-2xx answer becomes [[HttpFailure]]."""
    def post(query):
        body = urllib.parse.urlencode({"query": PREFIXES + query}).encode()
        req = urllib.request.Request(endpoint, data=body, method="POST", headers={
            "User-Agent": user_agent, "Accept": "text/tab-separated-values",
            "Content-Type": "application/x-www-form-urlencoded"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return read_body(r)
        except urllib.error.HTTPError as e:
            raise HttpFailure(e.code, e.read()[:300].decode("utf-8", "replace"), e.headers.get("Retry-After")) from e
    return post


def wikidata_fetch(endpoint, out_dir, user_agent="Histmersion/0.1 (gazetteer import)", pause=5.0,
                   retries=WD_RETRIES, post=None, sleep=time.sleep, scope=DEFAULT_SCOPE):
    """Run each query once and save its TSV. A generic User-Agent, one request at a time, a pause
    between them, a timeout answered by asking for each half of the area ([[fetch_banded]]) and a
    "slow down" by waiting ([[fetch_query]]). Resumable: a query whose result file is already complete is not asked again
    (results are written to `.part` first). Records the endpoint, the time and each file's sha256 in
    `fetch.json`."""
    post = post or urllib_post(endpoint, user_agent)
    os.makedirs(out_dir, exist_ok=True)
    meta_path = os.path.join(out_dir, "fetch.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    meta.update({"endpoint": endpoint, "files": meta.get("files", {}), "scope": scope.describe()})
    meta.setdefault("fetched", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    for name in wd_queries(scope):
        path = os.path.join(out_dir, f"{name}.tsv")
        if os.path.exists(path) and name in meta["files"]:
            continue
        try:
            with open(path + ".part", "wb") as fh:
                fetch_banded(name, scope, post, fh, sleep=sleep, retries=retries,
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


def wd_span(times, name_years=(), descriptions=(), stats=None, used=None):
    """An item's (start, end) from its time statements: `times` maps P571/P580/P576/P582 to lists of
    (year, precision, rank). Each side is (earliest, latest) — a year stated to the year has both
    equal — or None when unknown. Start = the earliest of inception/start time, end = the latest of
    dissolved/end time; a century typed as a year is dropped first (counted in stats['century']).
    `used`, a set, receives the properties whose values made the span ([[date_source]])."""
    def side(props):
        vals = []
        for p in props:
            for y, prec, rank in times.get(p, []):
                vals.append(((y, prec, p), rank))
        return wd_best(vals)

    starts, ends = side(("P571", "P580")), side(("P576", "P582"))
    every = [y for y, _, _ in starts + ends] + list(name_years)

    def clean(vals):
        kept = []
        for y, prec, p in vals:
            if century_as_year(y, prec, [o for o in every if o != y], descriptions):
                if stats is not None:
                    stats["century"] = stats.get("century", 0) + 1
                continue
            if used is not None:
                used.add(p)
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


# ── dating: where a span came from, and P2348 through PeriodO ───────────────────────────────────

def date_source(used):
    """What a span from the time statements is named after on the place card (`properties.date_source`):
    `wikidata-p580` when a start or end time (P580/P582) gave a bound, else `wikidata-p571` (inception),
    else `wikidata-p576` (dissolved). None when no statement gave one."""
    for props, name in ((("P580", "P582"), "wikidata-p580"), (("P571",), "wikidata-p571"), (("P576",), "wikidata-p576")):
        if any(p in used for p in props):
            return name
    return None


# Which PeriodO authorities date a time period (P2348) of an item in a country (P17), in order: the first
# that defines the period wins. PeriodO's own "spatial coverage" is how an authority says where it
# applies; a period of the Bronze Age is a date range only in a region (3200 BCE in Greece, 1800 BCE in
# Denmark), so an item outside every country listed here keeps its P2348 undated.
#   p0ds9qj  The British School at Athens: archaeological periods of Greece (2022)
#   p0gtzvd  La Chronique des fouilles en ligne / Archaeology in Greece Online
#   p0qp9rs  The Cambridge companion to the Aegean Bronze Age
#   p03wskd  Pleiades
PERIODO_SCHEMES = {
    "Q41": ("p0ds9qj", "p0gtzvd", "p0qp9rs", "p03wskd"),  # Greece
}
# Wikidata's period items are mostly broader than any authority's (no PeriodO period links Wikidata, and
# few labels agree: "classical antiquity" is no BSA "Classical", which is 480–323 BCE). So a curated table
# names the PeriodO period a Wikidata period IS, where the two are the same concept; anything not in it
# is matched by label only when the labels are the same words. Checked against both labels on
# 2026-10-28; a period with no equivalent ("prehistory", "Frankokratia", "Early Middle Ages", "Iron Age":
# the BSA has only an Early Iron Age) is left out on purpose.
PERIODO_ALIASES = {
    "Q486761": "p0ds9qjpdcd",    # classical antiquity → BSA Antiquity (Archaic … Late Antique)
    "Q11772": "p0ds9qjpdcd",     # Ancient Greece → BSA Antiquity
    "Q217050": "p0ds9qjqppc",    # Late antiquity → BSA Late Antique
    "Q12554": "p0ds9qj2b54",     # Middle Ages → BSA Medieval/Frankish
    "Q36422": "p0ds9qj3x2t",     # Neolithic → BSA Neolithic
    "Q428995": "p0ds9qjmv48",    # Hellenistic period → BSA Hellenistic
    "Q51644": "p0ds9qj5k9r",     # Early Christianity → BSA Early Christian
    "Q11761": "p0ds9qjq7b2",     # Bronze Age → BSA Bronze Age
    "Q130253": "p0ds9qj3fwb",    # Chalcolithic → BSA Final Neolithic or Chalcolithic
    "Q843745": "p0ds9qj2hxw",    # Classical Greece → BSA Classical
    "Q271834": "p0ds9qjwzmx",    # Archaic Greece → BSA Archaic
    "Q181264": "p0ds9qjn6xc",    # Mycenaean Greece → BSA Late Helladic
    "Q134178": "p0ds9qj9kdj",    # Minoan civilization → BSA Bronze Age Minoan
    "Q318144": "p0ds9qjhmqs",    # Cycladic civilization → BSA Bronze Age Cycladic
    "Q871792": "p0ds9qj2nvc",    # Ottoman Greece → BSA Ottoman
    "Q12544": "p0ds9qjcznw",     # Byzantine Empire → BSA Byzantine
    "Q2905322": "p0ds9qjcznw",   # Byzantine Greece → BSA Byzantine
    "Q2277": "p0ds9qjwwfx",      # Roman Empire → BSA Roman
    "Q1747689": "p0ds9qjwwfx",   # Ancient Rome → BSA Roman
    "Q40203": "p0ds9qjsdpq",     # Paleolithic → BSA Paleolithic
    "Q1471923": "p0ds9qjkz7p",   # Early Bronze Age → BSA Early Bronze Age
    "Q20529907": "p0ds9qjrvwn",  # Early Iron Age → BSA Early Iron Age
    "Q57449747": "p0ds9qjbp46",  # Greek early classical period → BSA Early Classical
    "Q57449805": "p0ds9qj9fg4",  # Greek late classical period → BSA Late Classical
    "Q99299757": "p0ds9qjghsd",  # High Classical → BSA High Classical
    "Q852337": "p0ds9qjkzn8",    # geometric art → BSA Geometric
}


def periodo_year(raw):
    """A PeriodO year (ISO 8601: year 0 is 1 BCE, "-0799" is 800 BCE) in historical reckoning; None for none."""
    try:
        y = int(str(raw).strip())
    except (TypeError, ValueError):
        return None
    return y - 1 if y <= 0 else y


def periodo_bound(b):
    """(earliest, latest) of a PeriodO start or stop (`{"in": {"year"}}` or `{"in": {"earliestYear",
    "latestYear"}}`), or None."""
    i = (b or {}).get("in") or {}
    if "year" in i:
        y = periodo_year(i["year"])
        return None if y is None else (y, y)
    lo, hi = periodo_year(i.get("earliestYear")), periodo_year(i.get("latestYear"))
    if lo is None and hi is None:
        return None
    return (lo if lo is not None else hi, hi if hi is not None else lo)


def label_key(s):
    """A label compared with another: lower case, one space, no trailing "period", no leading "the"."""
    s = re.sub(r"\s+", " ", (s or "").strip().lower())
    s = re.sub(r"^the ", "", s)
    return re.sub(r" period$", "", s)


def periodo_index(dump):
    """The parts of a PeriodO dump (`d.json`, parsed) the dating reads: period id → {id, label, labels
    (keys), authority, start, stop, wikidata (ids it is sameAs)} for every period with both bounds, and
    authority id → title."""
    periods, titles = {}, {}
    for aid, a in sorted((dump.get("authorities") or {}).items()):
        src = a.get("source") or {}
        titles[aid] = src.get("title") or (src.get("partOf") or {}).get("title") or aid
        for pid, p in sorted((a.get("periods") or {}).items()):
            start, stop = periodo_bound(p.get("start")), periodo_bound(p.get("stop"))
            if start is None or stop is None or stop[1] < start[0]:
                continue
            labels = {label_key(p.get("label"))}
            for lang in ("en", "eng"):
                labels.update(label_key(x) for x in (p.get("localizedLabels") or {}).get(lang, []))
            same = p.get("sameAs") or []
            same = [same] if isinstance(same, str) else same
            wd = {m.group(1) for x in same for m in [re.search(r"wikidata\.org/(?:entity|wiki)/(Q\d+)", str(x))] if m}
            periods[pid] = {"id": pid, "label": (p.get("label") or "").strip(), "labels": labels - {""},
                            "authority": aid, "start": start, "stop": stop, "wikidata": wd}
    return {"periods": periods, "titles": titles}


def load_periodo(path):
    """[[periodo_index]] of a PeriodO dump file — plain JSON, or as served (zstd), where this Python
    reads zstd (3.14+) — and its sha256 (pinned with the extract)."""
    raw = open(path, "rb").read()
    if raw[:4] == b"\x28\xb5\x2f\xfd":
        try:
            from compression import zstd  # Python 3.14+
        except ImportError as e:
            raise RuntimeError(f"{path} is zstd-compressed: decompress it first (zstd -d)") from e
        raw = zstd.decompress(raw)
    idx = periodo_index(json.loads(raw.decode("utf-8")))
    idx["sha256"] = hashlib.sha256(raw).hexdigest()
    return idx


def periodo_period(qid, label, countries, periodo):
    """The PeriodO period a Wikidata time period (`qid`, English `label`) is, for an item in `countries`:
    in the schemes of those countries ([[PERIODO_SCHEMES]], in order), the curated alias first, then a
    period that is `sameAs` the item, then one whose label is the same words — the first authority that
    has one wins; two periods of one authority with that label are no answer. None when nothing matches."""
    if not periodo:
        return None
    ps = periodo["periods"]
    authorities = []
    for c in sorted(countries):
        for a in PERIODO_SCHEMES.get(c, ()):
            if a not in authorities:
                authorities.append(a)
    if not authorities:
        return None
    alias = ps.get(PERIODO_ALIASES.get(qid, ""))
    if alias and alias["authority"] in authorities:
        return alias
    key = label_key(label)
    for test in (lambda p: qid in p["wikidata"], lambda p: bool(key) and key in p["labels"]):
        for a in authorities:
            hits = [p for p in ps.values() if p["authority"] == a and test(p)]
            if len(hits) == 1:
                return hits[0]
    return None


def periodo_dating(periods, countries, labels, periodo):
    """An item's span from its time periods (P2348 values, with their English labels in `labels`) through
    PeriodO: the earliest start and the latest stop of the periods they are ([[periodo_period]]). Every
    value must be one: an item "Neolithic" and "Middle Ages" with only the first known would be dated
    as if it ended in 3000 BCE. Returns (start, end, [period records]) or None."""
    if not periods:
        return None
    found = []
    for q in sorted(periods, key=lambda x: int(x[1:]) if x[1:].isdigit() else 0):
        p = periodo_period(q, labels.get(q, ""), countries, periodo)
        if p is None:
            return None
        if p not in found:
            found.append(p)
    start = (min(p["start"][0] for p in found), min(p["start"][1] for p in found))
    end = (max(p["stop"][0] for p in found), max(p["stop"][1] for p in found))
    return start, end, found


def wd_point(row):
    """(lon, lat) of a core row, or None: an item whose coordinate is "unknown value", or one on another
    globe, has none the endpoint can read (the world query, with no bbox FILTER, returns it empty)."""
    try:
        return float(literal(row.get("lon"))[0]), float(literal(row.get("lat"))[0])
    except (TypeError, ValueError):
        return None


def wikidata(src_dir, out, scope=DEFAULT_SCOPE, periodo=None):
    """The Wikidata extract from the TSV results of [[wikidata_fetch]] — cut again to `scope`, so a
    regional extract can be built from a world fetch. `periodo` ([[load_periodo]]) dates the items whose
    only date is a time period (P2348); without it they stay undated."""
    stats = {"scope": scope.describe(), "items": 0, "kept": 0, "names": 0, "links": 0, "dated": 0, "described": 0, "century": 0,
             "unplaced": 0, "heritage": 0, "heritage_only": 0, "registers": 0, "dated_by": {}, "by_class": {},
             "p2348": 0, "p2348_unresolved": 0}
    if periodo:
        stats["periodo_sha256"] = periodo.get("sha256")
    kinds = dict(WD_ROOTS)
    order = {q: i for i, (q, _) in enumerate(WD_ROOTS)}
    core = {}
    for r in tsv_rows(os.path.join(src_dir, "core.tsv")):
        # a row of the heritage branch has no root (`?root` unbound): a designated item of another class
        q, root = qid(r.get("item")), qid(r.get("root"))
        if not q or (root is not None and root not in kinds) or (root is None and (r.get("root") or "").strip()):
            continue
        point = wd_point(r)
        if point is None or not scope.contains(*point):
            stats["unplaced"] += point is None
            continue
        lon, lat = point
        cur = core.get(q)
        pt = (r5(lon), r5(lat))
        if cur is None:
            core[q] = {"root": root, "points": {pt}}
        else:
            if root is not None and (cur["root"] is None or order[root] < order[cur["root"]]):
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
            kept = scope.lang(lang)
            if q and text and (kind == "native" or kept is not None):
                add_name(q, name_obj(text, kept or ""))
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
                add_name(q, name_obj(text, scope.lang(lang) or "", when(s, e)))
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
            if q in core and text and scope.lang(lang):
                descs.setdefault(q, {})[lang] = text
    native_langs = {}
    path = os.path.join(src_dir, "native.tsv")
    if os.path.exists(path):
        for r in tsv_rows(path):
            q = qid(r.get("item"))
            _, lang = literal(r.get("name"))
            if q in core and scope.lang(lang):
                native_langs.setdefault(q, set()).add(lang)
    ids = {}
    for prop in WD_IDS:
        for r in tsv_rows(os.path.join(src_dir, f"id_{prop}.tsv")):
            q = qid(r.get("item"))
            v, _ = literal(r.get("v"))
            if q in core and v:
                ids.setdefault(q, set()).add((prop, v.strip()))

    def values(name, keep_rank=False):
        """item → set of the QIDs (or literals) of a two-column aspect file (`?item ?v`)."""
        out_ = {}
        path_ = os.path.join(src_dir, f"{name}.tsv")
        if not os.path.exists(path_):
            return out_
        for r in tsv_rows(path_):
            q = qid(r.get("item"))
            if q not in core:
                continue
            v = qid(r.get("v")) or literal(r.get("v"))[0].strip()
            if v:
                out_.setdefault(q, set()).add(v)
        return out_

    def item_labels(name):
        """value QID → its label (English, else "mul")."""
        out_ = {}
        path_ = os.path.join(src_dir, f"{name}.tsv")
        if not os.path.exists(path_):
            return out_
        for r in tsv_rows(path_):
            v = qid(r.get("v"))
            text, lang = literal(r.get("name"))
            if v and text and (lang == "en" or v not in out_):
                out_[v] = text
        return out_

    p31, countries, periods = values("p31"), values("country"), values("period")
    registers = {}
    for prop in WD_REGISTERS:
        for q, vs in values(f"reg_{prop}").items():
            registers.setdefault(q, {})[prop] = sorted(vs)
    heritage = {}
    path = os.path.join(src_dir, "heritage.tsv")
    if os.path.exists(path):
        for r in tsv_rows(path):
            q, v = qid(r.get("item")), qid(r.get("v"))
            if q in core and v and WD_RANK_DEPRECATED not in (r.get("rank") or ""):
                s_ = wd_year(r["start"]) if r.get("start") else None
                e_ = wd_year(r["end"]) if r.get("end") else None
                heritage.setdefault(q, set()).add((v, s_, e_))
    heritage_labels, period_labels = item_labels("heritage_labels"), item_labels("period_labels")

    def keep(q):
        # a plain present-day settlement adds nothing GeoNames lacks, unless it links to another
        # gazetteer, is dated, is protected or listed, or has a name in a historical language
        if core[q]["root"] != WD_SETTLEMENT:
            return True
        if q in ids or q in times or q in heritage or q in registers:
            return True
        return any(n.get("when") or n.get("lang") in HISTORICAL_LANGS for n in names.get(q, []))

    def features():
        for q in sorted(core, key=lambda x: int(x[1:])):
            if not keep(q):
                continue
            root = core[q]["root"]
            facts = {"p31": p31.get(q, set()), "countries": countries.get(q, set()),
                     "periods": periods.get(q, set()), "period_labels": period_labels,
                     "heritage": heritage.get(q, set()), "heritage_labels": heritage_labels,
                     "registers": registers.get(q, {}), "periodo": periodo}
            yield wd_feature(q, core[q], kinds[root] if root else "site", labels.get(q, {}), names.get(q, []),
                             times.get(q, {}), ids.get(q, set()), stats,
                             wd_descriptions(descs.get(q, {}), native_langs.get(q, set())), facts)

    stats["kept"] = write_ndjson_gz(out, features())
    return stats


WD_LINK = {"P1566": ("exactMatch", "gn"), "P1584": ("closeMatch", "pl"), "P1667": ("closeMatch", "tgn")}


def wd_descriptions(by_lang, native_langs):
    """LPF `descriptions`: English, then the language(s) of the item's native label (the place's
    own language, where Wikidata has a description in it). An item with neither keeps the first of
    the corpus languages, then the other modern and the ancient ones, so a card still says what the place is."""
    langs = (["en"] if "en" in by_lang else []) + sorted(l for l in native_langs if l != "en" and l in by_lang)
    if not langs:
        langs = next(([l] for l in CORPUS_LANGS + WINDOW_MODERN_LANGS + WORLD_MODERN_LANGS + ANCIENT_LANGS
                       if l in by_lang), [])
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


def qid_order(q):
    return (int(q[1:]) if q[1:].isdigit() else 0, q)


def wd_dating(times, names, descriptions, facts, stats):
    """(start, end, date_source, PeriodO periods) of an item: its time statements ([[wd_span]]) when they
    give a bound; else its time periods (P2348) through PeriodO ([[periodo_dating]]); else nothing —
    an undated item is never given a guess."""
    used = set()
    start, end = wd_span(times, _name_years(names), descriptions, stats, used)
    if start or end:
        return start, end, date_source(used), []
    periods = facts.get("periods") or set()
    if periods:
        stats["p2348"] = stats.get("p2348", 0) + 1
    got = periodo_dating(periods, facts.get("countries") or set(), facts.get("period_labels") or {},
                         facts.get("periodo"))
    if got is None:
        if periods:
            stats["p2348_unresolved"] = stats.get("p2348_unresolved", 0) + 1
        return None, None, None, []
    start, end, found = got
    return start, end, "p2348-periodo:" + "+".join(p["id"] for p in found), found


def wd_feature(q, core, kind, labels, names, times, ids, stats, descriptions=(), facts=None):
    """One LPF feature. `facts` (all optional): the item's own P31 classes, countries (P17), time periods
    (P2348) and heritage designations (P1435, as (designation, start, end)), the labels of the periods and
    designations, its register ids, and the PeriodO index that dates the periods. Everything a feature
    says is read from the item's own statements and fixed tables — never from another record."""
    facts = facts or {}
    title = next((labels[l] for l in TITLE_LANGS if l in labels), None) \
        or next((labels[l] for l in sorted(labels)), q)
    start, end, source, found = wd_dating(times, names, [d["value"] for d in descriptions], facts, stats)
    props = {"title": title, "kind": kind, "source": WIKIDATA_SOURCE}
    if core["root"]:
        props["class"] = core["root"]
    stats["by_class"][core["root"] or WD_HERITAGE] = stats["by_class"].get(core["root"] or WD_HERITAGE, 0) + 1
    if facts.get("p31"):
        props["p31"] = sorted(facts["p31"], key=qid_order)
    heritage = facts.get("heritage") or set()
    if heritage:
        hl = facts.get("heritage_labels") or {}
        props["heritage"] = [dict({"id": d}, **({"label": hl[d]} if d in hl else {}),
                                  **({"start": s} if s is not None else {}), **({"end": e} if e is not None else {}))
                             for d, s, e in sorted(heritage, key=lambda h: (qid_order(h[0]), h[1] or 0, h[2] or 0))]
        stats["heritage"] += 1
        stats["heritage_only"] += 0 if core["root"] else 1
    if facts.get("registers"):
        props["registers"] = {k: list(v) for k, v in sorted(facts["registers"].items())}
        stats["registers"] += 1
    periods = facts.get("periods") or set()
    if periods:
        pl = facts.get("period_labels") or {}
        props["periods"] = [dict({"id": v}, **({"label": pl[v]} if v in pl else {})) for v in sorted(periods, key=qid_order)]
    span = when_ranges(start, end)
    if span:
        stats["dated"] += 1
        stats["dated_by"][source.split(":")[0]] = stats["dated_by"].get(source.split(":")[0], 0) + 1
        props["date_source"] = source
        if found:
            titles = (facts.get("periodo") or {}).get("titles", {})
            props["periodo"] = [{"id": p["id"], "label": p["label"], "authority": p["authority"],
                                 "authorityTitle": titles.get(p["authority"], p["authority"])} for p in found]
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
         "geometry": {"type": "Point", "coordinates": [lon, lat]}, "names": ns}
    if core["root"]:
        f["types"] = [{"identifier": f"wd:{core['root']}", "label": kind}]
    if span:
        f["when"] = span
    if links:
        f["links"] = links
    if descriptions:
        f["descriptions"] = list(descriptions)
    return f


# ── Getty TGN ───────────────────────────────────────────────────────────────────────────────────
# The Getty Thesaurus of Geographic Names (ODC-By 1.0), from its EXPLICIT N-Triples export — a public
# dump, no login: http://tgndownloads.getty.edu/VocabData/explicit.zip (1.15 GB zipped, ~27 GB of
# N-Triples, read in place, never unpacked). Place types are AAT concepts; their English labels come
# from the AAT explicit export (http://aatdownloads.getty.edu/VocabData/explicit.zip, 93 MB). TGN
# places carry no links to other gazetteers: they JOIN by their own id, which Wikidata items carry
# (P1667, ~25 700 in the window — `tgn:` links in the Wikidata extract).

TGN_SOURCE = "Getty Thesaurus of Geographic Names (TGN), ODC-By 1.0"
_G = "http://vocab.getty.edu/"
_GVP = "<http://vocab.getty.edu/ontology#"
_P_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
_P_LAT, _P_LONG = "<http://www.w3.org/2003/01/geo/wgs84_pos#lat>", "<http://www.w3.org/2003/01/geo/wgs84_pos#long>"
_P_PREF, _P_ALT = "<http://www.w3.org/2008/05/skos-xl#prefLabel>", "<http://www.w3.org/2008/05/skos-xl#altLabel>"
_P_LITERAL = "<http://www.w3.org/2008/05/skos-xl#literalForm>"
_P_NOTE, _P_VALUE = "<http://www.w3.org/2004/02/skos/core#scopeNote>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#value>"
_P_PTYPE = _GVP + "placeTypePreferred>"
_P_PREF_GVP = _GVP + "prefLabelGVP>"
_P_START, _P_END, _P_HISTORIC = _GVP + "estStart>", _GVP + "estEnd>", _GVP + "historicFlag>"
TGN_PLACE_CLASSES = {"AdminPlaceConcept", "PhysPlaceConcept", "PhysAdminPlaceConcept"}
# Getty writes "the present" as 9999 and "unknown" as -9999 or so: a bound that far out is OPEN.
TGN_OPEN_YEARS = 5000

# What a place type (its AAT English label) says about the place: a historical or archaeological kind
# is kept outright; a polity or region kind sets the kind. Order: the first match decides.
TGN_HISTORICAL_TYPE = re.compile(r"\b(ancient|archaeolog|deserted|abandoned|former|historical|lost|ruin|necropol|"
                                 r"tell\b|tumul|hillfort|oppid|colon(y|ia)\b|classical|medieval|prehistoric)", re.I)
TGN_POLITY_TYPE = re.compile(r"\b(nation|empire|kingdom|sovereign|state|republic|principality|sultanate|caliphate|"
                             r"khanate|emirate|duchy|county palatine|polity|confederation)\b", re.I)
TGN_REGION_TYPE = re.compile(r"\b(region|province|district|department|county|canton|administrative|division|"
                             r"governorate|prefecture|territory|oblast|kray|krai|vilayet|satrapy|diocese|area)\b", re.I)


def nt_split(line):
    """(subject, predicate, object) of one N-Triples line as text; None for a blank or comment line."""
    line = line.strip()
    if not line or line.startswith("#"):
        return None
    parts = line.split(" ", 2)
    if len(parts) < 3:
        return None
    o = parts[2]
    if o.endswith(" ."):
        o = o[:-2].rstrip()
    elif o.endswith("."):
        o = o[:-1].rstrip()
    return parts[0], parts[1], o


def nt_triples(z, member, predicates):
    """The triples of one zip member whose predicate is one of `predicates` (cheap bytes test first)."""
    wanted = {p.encode() for p in predicates}
    with z.open(member) as fh:
        for raw in fh:
            sp = raw.find(b" ")
            if sp < 0:
                continue
            ep = raw.find(b" ", sp + 1)
            if raw[sp + 1:ep] not in wanted:
                continue
            t = nt_split(raw.decode("utf-8", "replace"))
            if t:
                yield t


def getty_id(term, kind):
    """`<http://vocab.getty.edu/tgn/7001393>` → '7001393' for kind 'tgn' (and `…-place` for the
    coordinate node), `<…/tgn/term/165794-la>` → '165794-la' for kind 'tgn/term'; None otherwise."""
    pre = "<" + _G + kind + "/"
    if not term.startswith(pre) or not term.endswith(">"):
        return None
    return term[len(pre):-1]


def nt_literal(o):
    """(text, lang, datatype) of an N-Triples literal object; text unescaped. A bare (non-literal)
    object gives (o, '', '')."""
    m = re.match(r'^"(.*)"(?:@([A-Za-z0-9-]+)|\^\^<([^>]+)>)?$', o, re.S)
    if not m:
        return o, "", ""
    return unescape(m.group(1)), (m.group(2) or "").lower(), m.group(3) or ""


def tgn_lang(tag):
    """A name's language for the extract: the base language of a native-script tag ('grc', 'el'); a
    transliteration (`el-Latn`) is no name IN that language — '' like GeoNames' bare variants."""
    tag = (tag or "").lower()
    if not tag or "-latn" in tag:
        return ""
    return tag.split("-")[0]


def tgn_year(o):
    """A Getty estimated date (`"-0200"^^xsd:gYear`) as a signed year, or None when absent or a
    sentinel for "the present"/"unknown". Getty writes BCE years negative in historical reckoning (its
    own data, not XSD 1.1's year zero): -200 is 200 BCE."""
    text, _, _ = nt_literal(o)
    m = re.match(r"^(-?)0*(\d+)$", text.strip())
    if not m:
        return None
    y = int(m.group(1) + (m.group(2) or "0"))
    if y == 0 or abs(y) >= TGN_OPEN_YEARS:
        return None
    return y


def tgn_kind(classes, type_label):
    if "PhysPlaceConcept" in classes and not ({"AdminPlaceConcept", "PhysAdminPlaceConcept"} & classes):
        return "feature"
    label = type_label or ""
    if TGN_POLITY_TYPE.search(label):
        return "polity"
    if TGN_REGION_TYPE.search(label):
        return "region"
    return "site"


def getty_members(z, *suffixes):
    """The members of a Getty export whose names end with one of `suffixes`, in that order. (The
    explicit exports keep a concept's label LINKS in the Terms file beside the terms themselves, and its
    scope-note links beside the notes — so a pass is over every member that may hold a predicate.)"""
    names = z.namelist()
    return [m for suf in suffixes for m in names if m.endswith(suf)]


def aat_labels(aat_zip, wanted):
    """AAT concept id → its English label (`gvp:prefLabelGVP` when English, else the English
    `xl:prefLabel`), for the `wanted` ids (the place types in use)."""
    if not wanted:
        return {}
    with zipfile.ZipFile(aat_zip) as z:
        members = getty_members(z, "1Subjects.nt", "2Terms.nt")
        gvp, pref = {}, {}
        for m in members:
            for s, p, o in nt_triples(z, m, [_P_PREF_GVP, _P_PREF]):
                a, t = getty_id(s, "aat"), getty_id(o, "aat/term")
                if a in wanted and t and (t.endswith("-en") or t.endswith("-en-us")):
                    (gvp if p == _P_PREF_GVP else pref).setdefault(a, t)
        by_term = {}
        for a in wanted:
            t = gvp.get(a) or pref.get(a)
            if t:
                by_term[t] = a
        out = {}
        for m in getty_members(z, "2Terms.nt"):
            for s, _, o in nt_triples(z, m, [_P_LITERAL]):
                t = getty_id(s, "aat/term")
                if t in by_term:
                    out.setdefault(by_term[t], nt_literal(o)[0])
    return out


def tgn(tgn_zip, aat_zip, out, scope=DEFAULT_SCOPE):
    """The TGN extract: places in the scope whose type is historical or archaeological, or that have
    a historic, dated or historical-language name; each with its names (language, use dates, historic
    flag), its English scope note and its place type. Several passes over the zip, read in place."""
    stats = {"scope": scope.describe(), "window": 0, "candidates": 0, "kept": 0, "names": 0, "dated": 0,
             "described": 0}
    with zipfile.ZipFile(tgn_zip) as z:
        def scan(suffixes, predicates):
            for m in getty_members(z, *suffixes):
                yield from nt_triples(z, m, predicates)

        # 1. where: the coordinates of `<tgn/X-place>`, inside the scope
        lat, lon = {}, {}
        for s, p, o in scan(["Coordinates.nt"], [_P_LAT, _P_LONG]):
            i = getty_id(s, "tgn")
            if not i or not i.endswith("-place"):
                continue
            v = float(nt_literal(o)[0])
            (lat if p == _P_LAT else lon)[i[:-6]] = v
        where = {i: (r5(lon[i]), r5(lat[i])) for i in lat if i in lon and scope.contains(lon[i], lat[i])}
        stats["window"] = len(where)
        del lat, lon

        # 2. what: class, place type, labels and scope notes of those
        classes, ptype, terms, notes = {}, {}, {}, {}
        # (in the export: the class in Subjects, the place type in PlaceTypes, the label links in Terms,
        # the note links in ScopeNotes)
        for s, p, o in scan(["1Subjects.nt", "PlaceTypes.nt", "2Terms.nt", "ScopeNotes.nt"],
                            [_P_TYPE, _P_PTYPE, _P_PREF, _P_ALT, _P_NOTE]):
            i = getty_id(s, "tgn")
            if i not in where:
                continue
            if p == _P_TYPE:
                if o.startswith(_GVP):
                    classes.setdefault(i, set()).add(o[len(_GVP):-1])
            elif p == _P_PTYPE:
                ptype.setdefault(i, getty_id(o, "aat"))
            elif p == _P_NOTE:
                n = getty_id(o, "tgn/scopeNote")
                if n:
                    notes[n] = i
            else:
                t = getty_id(o, "tgn/term")
                if t:
                    terms[t] = (i, p == _P_PREF)
        places = {i for i in where if classes.get(i, set()) & TGN_PLACE_CLASSES}
        stats["candidates"] = len(places)

        # 3. names: each label's text, language, dates and historic flag
        names, title = {}, {}
        info = {}
        for s, p, o in scan(["2Terms.nt"], [_P_LITERAL, _P_START, _P_END, _P_HISTORIC]):
            t = getty_id(s, "tgn/term")
            if t not in terms or terms[t][0] not in places:
                continue
            d = info.setdefault(t, {})
            if p == _P_LITERAL:
                d["text"], d["tag"], _ = nt_literal(o)
            elif p == _P_START:
                d["start"] = tgn_year(o)
            elif p == _P_END:
                d["end"] = tgn_year(o)
            else:
                d["historic"] = o.rstrip(">").rsplit("/", 1)[-1].lower() in ("historic", "both")
        for t in sorted(info):
            d = info[t]
            if not d.get("text"):
                continue
            i, pref = terms[t]
            lang = scope.lang(tgn_lang(d.get("tag")))
            if lang is None:
                continue
            names.setdefault(i, []).append(name_obj(d["text"], lang, when(d.get("start"), d.get("end")),
                                                    bool(d.get("historic"))))
            if pref and (i not in title or lang == "en"):
                title[i] = d["text"]
        del info, terms

        # 4. what it is, in words: the English scope notes
        described = {}
        if notes:
            for s, _, o in scan(["ScopeNotes.nt"], [_P_VALUE]):
                n = getty_id(s, "tgn/scopeNote")
                if n in notes and notes[n] in places:
                    text, tag, _ = nt_literal(o)
                    if text.strip() and tag in ("", "en"):
                        described.setdefault(notes[n], text.strip())

    labels = aat_labels(aat_zip, {ptype[i] for i in places if ptype.get(i)}) if aat_zip else {}

    def keep(i):
        if TGN_HISTORICAL_TYPE.search(labels.get(ptype.get(i), "") or ""):
            return True
        return any(n.get("historic") or n.get("when") or n.get("lang") in HISTORICAL_LANGS for n in names.get(i, []))

    def features():
        for i in sorted(places, key=int):
            if not keep(i) or not names.get(i):
                continue
            label = labels.get(ptype.get(i))
            ns = dedup_names(names[i])
            t = title.get(i) or ns[0]["toponym"]
            props = {"title": t, "kind": tgn_kind(classes.get(i, set()), label), "source": TGN_SOURCE, "modern": True}
            if label:
                props["placeType"] = label
            f = {"@id": f"{_G}tgn/{i}", "type": "Feature", "properties": props,
                 "geometry": {"type": "Point", "coordinates": list(where[i])}, "names": ns}
            if ptype.get(i):
                f["types"] = [{"identifier": f"aat:{ptype[i]}", "label": label or ptype[i]}]
            if i in described:
                f["descriptions"] = [{"value": described[i], "lang": "en"}]
                stats["described"] += 1
            stats["names"] += len(ns)
            stats["dated"] += 1 if any(n.get("when") for n in ns) else 0
            yield f

    stats["kept"] = write_ndjson_gz(out, features())
    return stats


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


def cache_dir(env=None):
    """Where built extracts go by default — the directory the server reads them from (`places.sources.dir`
    unset): `$XDG_CACHE_HOME/histmersion/gazetteer`, else `~/.cache/histmersion/gazetteer`, on macOS too."""
    env = os.environ if env is None else env
    base = (env.get("XDG_CACHE_HOME") or "").strip() or os.path.join(env.get("HOME") or os.path.expanduser("~"), ".cache")
    return os.path.join(base, "histmersion", "gazetteer")


def out_path(name, out_dir=None, env=None):
    """An output given as a bare file name goes to `out_dir` (`--out`), else [[cache_dir]]; a path with a
    directory part is used as given."""
    if os.path.dirname(name):
        return name
    d = out_dir or cache_dir(env)
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, name)


OPTIONS = ("out", "bbox", "langs", "endpoint", "periodo")


def split_options(argv):
    """`--out DIR`, `--bbox B`, `--langs L`, `--endpoint URL` and `--periodo FILE` (anywhere, `--x v` or
    `--x=v`) and the remaining arguments."""
    rest, opts = [], {}
    it = iter(argv)
    for a in it:
        key = a[2:].split("=", 1)[0] if a.startswith("--") else None
        if key in OPTIONS:
            opts[key] = a.split("=", 1)[1] if "=" in a else next(it, None)
        else:
            rest.append(a)
    return opts, rest


def split_out(argv):
    """`--out DIR` (anywhere) and the remaining arguments."""
    opts, rest = split_options(argv)
    return opts.get("out"), rest


def fetch_args(args, endpoint=None):
    """`wikidata-fetch`'s (endpoint, out-dir): `<endpoint> <out-dir>`, or `<out-dir>` alone with `--endpoint`
    or [[WD_ENDPOINT]]."""
    if len(args) >= 2:
        return args[0], args[1]
    return endpoint or WD_ENDPOINT, args[0]


def main(argv):
    opts, argv = split_options(argv)
    out = opts.get("out")
    try:
        scope = Scope(parse_bbox(opts.get("bbox")), parse_langs(opts.get("langs")))
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    if len(argv) >= 4 and argv[0] == "geonames":
        print(json.dumps(geonames(argv[1], argv[2], out_path(argv[3], out), scope), indent=2))
    elif len(argv) >= 2 and argv[0] == "wikidata-fetch":
        endpoint, out_dir = fetch_args(argv[1:], opts.get("endpoint"))
        print(json.dumps(wikidata_fetch(endpoint, out_dir, scope=scope), indent=2))
    elif len(argv) >= 3 and argv[0] == "wikidata":
        periodo = load_periodo(opts["periodo"]) if opts.get("periodo") else None
        print(json.dumps(wikidata(argv[1], out_path(argv[2], out), scope, periodo), indent=2))
    elif len(argv) >= 4 and argv[0] == "tgn":
        print(json.dumps(tgn(argv[1], argv[2] or None, out_path(argv[3], out), scope), indent=2))
    elif len(argv) >= 4 and argv[0] == "pin":
        print(json.dumps(pin(argv[1], argv[2], out_path(argv[3], out), argv[4:]), indent=2))
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
