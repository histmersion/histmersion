#!/usr/bin/env python3
"""Build the committed gazetteer seed files (plan §1.6a) from their upstream sources.

    ./build.py natural-earth          <ne_110m_admin_0_countries.geojson>         <out.json>
    ./build.py natural-earth-physical <dir with the ne_10m_* physical layers>     <out.json>
    ./build.py natural-earth-places   <ne_10m_populated_places.geojson>           <out.json>
    ./build.py pleiades               <pleiades-places-latest.csv> <pleiades-names-latest.csv> <out.json>
    ./build.py supplement             <db/places dir>
    ./build.py reclassify             <03-pleiades.json>   (re-derive kinds in place, no download)

Every one emits Linked Places Format (LPF v1.1), which `histmersion places import` reads as-is. Nothing
here talks to the database: the files are data, and `fetch.sh` is how they are regenerated.
`supplement` is the curated layer (`supplement/*.csv`, #987): it writes `05-curated.json` and adds the
curated alternative names to the places of the other files, in place, idempotently.
"""
import csv, json, math, os, re, sys

csv.field_size_limit(10 ** 7)

# Where the seed keeps places: the whole world (minLon, minLat, maxLon, maxLat). Until 2026-10-27
# Pleiades was cut to the Aegean → Iranian plateau, Egypt → Caucasus window (-11, 15, 75, 60), which
# left out Aksum, Adulis, Muziris and the Arabian incense ports; Natural Earth to -25, -5, 150, 78,
# which left out the Americas, southern Africa and Oceania.
BBOX = (-180.0, -90.0, 180.0, 90.0)

def slug(s):
    return re.sub(r'_+$', '', re.sub(r'[^a-z0-9]+', '_', (s or '').strip().lower())).strip('_')[:64]

def feature(pid, title, kind, geometry=None, when=None, names=(), parents=(), source=None, props=None):
    f = {"@id": pid, "type": "Feature",
         "properties": dict(props or {}, title=title, kind=kind)}
    if source:
        f["properties"]["source"] = source
    f["types"] = [{"label": kind}]
    if when:
        f["when"] = {"timespans": [{"start": {"in": str(when[0])}, "end": {"in": str(when[1])}}]}
    ns = []
    seen = set()
    for name, lang in names:
        name = (name or "").strip()
        if not name or (name.lower(), lang) in seen:
            continue
        seen.add((name.lower(), lang))
        n = {"toponym": name}
        if lang:
            n["lang"] = lang
        ns.append(n)
    if ns:
        f["names"] = ns
    if parents:
        f["relations"] = [{"relationType": "gvp:broaderPartitive", "relationTo": p} for p in parents]
    f["geometry"] = geometry
    return f

def collection(features):
    return {"type": "FeatureCollection",
            "@context": "https://raw.githubusercontent.com/LinkedPasts/linked-places/master/"
                        "linkedplaces-context-v1.1.jsonld",
            "features": features}

def round_coords(g, nd=5):
    """1e-5° ≈ 1 m — far finer than a 1:110m boundary, and it halves the committed size."""
    if isinstance(g, list):
        return [round_coords(x, nd) for x in g]
    if isinstance(g, float):
        return round(g, nd)
    if isinstance(g, dict):
        return {k: round_coords(v, nd) for k, v in g.items()}
    return g

# ── Natural Earth: continents → subregions → countries ──────────────────────────────────────────
# Public domain (naturalearthdata.com/about/terms-of-use). `kind` is `region` at all three levels:
# these are the stable MODERN skeleton, not ancient polities (those are traced separately).
NE_SOURCE = "Natural Earth 1:110m Admin 0 – Countries (public domain)"
NE_LANGS = ["AR", "DE", "EL", "ES", "FA", "FR", "HE", "IT", "RU", "TR"]

def natural_earth(src, out):
    doc = json.load(open(src))
    feats, continents, subregions = [], {}, {}
    for ft in doc["features"]:
        p = ft["properties"]
        cont, sub = (p.get("CONTINENT") or "").strip(), (p.get("SUBREGION") or "").strip()
        if cont and cont not in continents:
            # `continent_<slug>` — NOT `ne_…`: the hand-traced macro-regions (db/places/polities)
            # declare `part_of: continent_asia|continent_europe|continent_africa`, so these ids are a
            # contract between the two seed sets and must not be renamed.
            continents[cont] = "continent_" + slug(cont)
        if sub and sub not in subregions:
            subregions[sub] = ("ne_subregion_" + slug(sub), continents.get(cont))
    for name, pid in sorted(continents.items()):
        feats.append(feature(pid, name, "region", names=[(name, "")], source=NE_SOURCE))
    for name, (pid, parent) in sorted(subregions.items()):
        feats.append(feature(pid, name, "region", names=[(name, "")],
                             parents=[parent] if parent else (), source=NE_SOURCE))
    for ft in sorted(doc["features"], key=lambda f: f["properties"].get("NAME") or ""):
        p = ft["properties"]
        iso = (p.get("ISO_A3") or p.get("ADM0_A3") or "").strip()
        name = (p.get("NAME") or p.get("ADMIN") or "").strip()
        if not name:
            continue
        pid = "ne_country_" + (slug(iso) if iso and iso != "-99" else slug(name))
        names = [(name, ""), (p.get("NAME_LONG"), ""), (p.get("FORMAL_EN"), "en"),
                 (p.get("ADMIN"), ""), (p.get("NAME_EN"), "en")]
        names += [(p.get("NAME_" + l), l.lower()) for l in NE_LANGS]
        sub = (p.get("SUBREGION") or "").strip()
        parent = subregions.get(sub, (None, None))[0] or continents.get((p.get("CONTINENT") or "").strip())
        feats.append(feature(
            pid, name, "region", geometry=round_coords(ft.get("geometry")),
            names=names, parents=[parent] if parent else (), source=NE_SOURCE,
            props={"iso_a3": iso, "continent": p.get("CONTINENT"), "subregion": sub}))
    write(out, feats)

# ── Pleiades: ancient places as points, with their own period bounds ─────────────────────────────
# CC-BY (Pleiades Community). `minDate`/`maxDate` in the dump are ALREADY signed years, which is
# exactly the model here — no PeriodO lookup needed.
PL_SOURCE = "Pleiades (pleiades.stoa.org), CC-BY"
PL_KIND = {"settlement": "site", "settlement-modern": "site", "archaeological-site": "site",
           "fort": "site", "fort-2": "site", "temple": "site", "temple-2": "site",
           "sanctuary": "site", "villa": "site", "cemetery": "site", "findspot": "site",
           "station": "site", "region": "region", "province": "region", "people": "region",
           "river": "feature", "mountain": "feature", "island": "feature", "lake": "feature",
           "cape": "feature", "pass": "feature", "bay": "feature", "spring": "feature"}
# nothing to draw or to search for
PL_DROP = {"label", "unlocated", "numbered feature", "undefined"}

# What a point-only "region" really is (2026-10-09) — the twin of `core.PlaceReview` (rules 3–5), which
# `lint --rule place-kind` applies to a running store and `PlaceSeedKindsSuite` holds to this file's
# output. Pleiades files a people, a civic tribe, a province and a deme alike as a region (or, for
# `province-2`, as nothing the map above knows, hence a site); one representative point each.
PL_POLITY = {"province", "province-2", "satrapy", "regio-augusti", "state", "diocese"}
PL_PEOPLE = {"people"}
PL_SETTLED = {"settlement", "settlement-modern", "deme-attic"}
WITHDRAWN_DUPLICATE = re.compile(r"^withdrawn\s*\(?duplicate\)?\s*:", re.I)
REGION_ASIDE = re.compile(r".*\([^)]*\bregion\b[^)]*\)", re.I)

def pleiades_kind(types, title=""):
    """`featureTypes` → kind. A feature stays a feature; a settlement that is also a site stays a site
    (a provincial capital); else an administrative type makes a polity, a people type a people, and a
    settlement type a site (Peiraieus: region, settlement, deme-attic) — unless the title itself says
    region ("Salmydessos (coastal region)")."""
    kind = next((PL_KIND[t] for t in types if t in PL_KIND), "site")
    if kind == "feature":
        return kind
    ts = set(types)
    settled = ts & PL_SETTLED
    keep_site = kind == "site" and settled
    if ts & PL_POLITY and not keep_site:
        return "polity"
    if ts & PL_PEOPLE and not keep_site:
        return "people"
    if settled and not REGION_ASIDE.match(title or ""):
        return "site"
    return kind

def withdrawn_duplicate(title):
    """Pleiades keeps a record it withdrew as a duplicate ("Withdrawn (duplicate): Creta et Cyrene");
    the replacement is its own record, so the duplicate is left out."""
    return bool(WITHDRAWN_DUPLICATE.match((title or "").strip()))

def pleiades_names(src):
    """`pid` → [(name, lang)] from the Pleiades NAMES dump — every attested and romanised name with
    its language ("Dead Sea"/en, "ἀσφαλτῖτις λίμνη"/grc, "Asphaltitis L."/la). The places dump only
    has the title, which is why "Dead Sea" or "Sea of Galilee" were missing until #987.

    Names are imported WITHOUT their Pleiades date range on purpose: the corpus writes about a place
    in modern English whatever the date of the finding, and a name valid only 1700–2100 would never
    match a Bronze-Age finding's region (`place_names.period` gates the name match)."""
    out = {}
    for r in csv.DictReader(open(src, newline="")):
        pid = (r.get("pid") or "").strip().rsplit("/", 1)[-1]
        if not pid:
            continue
        lang = (r.get("nameLanguage") or "").strip()
        for n in ((r.get("nameTransliterated") or ""), (r.get("title") or ""), (r.get("nameAttested") or "")):
            n = n.strip()
            if n and n not in ("Untitled", "Unnamed"):
                out.setdefault(pid, []).append((n, lang))
    return out

def pleiades(src, names_src, out):
    feats = []
    extra = pleiades_names(names_src) if names_src else {}
    for r in csv.DictReader(open(src, newline="")):
        try:
            lon, lat = float(r["reprLong"]), float(r["reprLat"])
        except (TypeError, ValueError):
            continue
        if not (BBOX[0] <= lon <= BBOX[2] and BBOX[1] <= lat <= BBOX[3]):
            continue
        if (r.get("locationPrecision") or "").strip() == "unlocated":
            continue
        types = [t.strip() for t in (r.get("featureTypes") or "").split(",") if t.strip()]
        if not types or all(t in PL_DROP for t in types):
            continue
        title = (r.get("title") or "").strip()
        kind = pleiades_kind(types, title)
        if not title or withdrawn_duplicate(title):
            continue
        when = None
        try:
            lo, hi = float(r["minDate"]), float(r["maxDate"])
            when = (int(lo), int(hi))
        except (TypeError, ValueError, KeyError):
            pass
        # Pleiades titles carry alternates as "Dianium/Hemeroskopeion" — but a slash inside a
        # parenthesised aside is not one ("Iberia (modern region/peninsula)")
        names = [(title, "")] + [(part.strip(), "") for part in re.split(r"/(?![^(]*\))", title) if part.strip()]
        names += extra.get(r["id"], [])
        feats.append(feature(
            "pleiades_" + r["id"], title, kind,
            geometry={"type": "Point", "coordinates": [round(lon, 5), round(lat, 5)]},
            when=when, names=names, source=PL_SOURCE,
            props={"pleiades_uri": "https://pleiades.stoa.org/places/" + r["id"],
                   "feature_types": ",".join(types),
                   "time_periods": r.get("timePeriodsKeys") or ""}))
    write(out, feats)

# ── Natural Earth physical + populated places: the modern English names (#987) ──────────────────
# The places a person names in plain English — seas, lakes, rivers, mountain ranges, peninsulas,
# deserts, islands, peaks and present-day cities — which neither the countries file nor Pleiades
# (ancient names, Roman-world window) has: "Balkans", "Black Sea", "Dnieper", "Kraków".
# Public domain, like the countries. 1:10m, the whole world (`BBOX`, tested on a feature's bbox centre),
# shapes simplified to ~1 km: they are for finding a name and drawing an outline.
NE_PHYS_SOURCE = "Natural Earth 1:10m physical/cultural vectors (public domain)"
# every language Natural Earth translates names into (`name_<code>`); `zht` is Traditional Chinese
NE_NAME_LANGS = ["ar", "bn", "de", "el", "en", "es", "fa", "fr", "he", "hi", "hu", "id", "it", "ja", "ko", "nl",
                 "pl", "pt", "ru", "sv", "tr", "uk", "ur", "vi", "zh", "zht"]
NE_LANG_TAG = {"zht": "zh-Hant"}
NE_SIMPLIFY = 0.01  # degrees, ≈ 1 km

# Natural Earth's abbreviations, spelled out as an alternative name ("Balkan Pen." → "Balkan Peninsula")
NE_ABBREV = [(r"\bMts\.", "Mountains"), (r"\bMt\.", "Mount"), (r"\bPen\.", "Peninsula"),
             (r"\bIs\.", "Islands"), (r"\bI\.", "Island"), (r"\bL\.", "Lake"), (r"\bG\.", "Gulf"),
             (r"\bStr\.", "Strait"), (r"\bPla\.", "Plateau"), (r"\bDes\.", "Desert"),
             (r"\bRa\.", "Range"), (r"\bC\.", "Cape"), (r"\bMtn\.", "Mountain"), (r"\bPt\.", "Point")]

# featurecla → kind: water and mountains are FEATURES (what a `water` hint asks for); large named
# land areas are REGIONS (a `region`/`peninsula`/`plain` hint), the same split Pleiades makes.
NE_KIND = {"range/mtn": "feature", "island": "feature", "island group": "feature", "gorge": "feature",
           "isthmus": "feature", "mountain": "feature", "volcano": "feature", "pass": "feature",
           "depression": "region", "desert": "region", "plateau": "region", "plain": "region",
           "pen/cape": "region", "peninsula": "region", "geoarea": "region", "coast": "region",
           "delta": "region", "valley": "region", "basin": "region", "lowland": "region",
           "wetlands": "region", "tundra": "region", "foothills": "region"}

def title_case(s):
    """NE writes its biggest labels in capitals ("BALKAN PEN.", "KJØLEN MOUNTAINS")."""
    s = (s or "").strip()
    if s and s == s.upper() and any(c.isalpha() for c in s):
        return " ".join(w[:1].upper() + w[1:].lower() for w in s.split(" "))
    return s

def expand(s):
    t = s
    for pat, rep in NE_ABBREV:
        t = re.sub(pat, rep, t)
    return t

def coords_of(g):
    out = []
    def walk(c):
        if c and isinstance(c[0], (int, float)):
            out.append(c)
        else:
            for x in c or []:
                walk(x)
    walk((g or {}).get("coordinates") or [])
    return out

def in_window(g, window=BBOX):
    """Has the geometry coordinates, and is the centre of its bbox inside `window`?"""
    cs = coords_of(g)
    if not cs:
        return False
    xs, ys = [c[0] for c in cs], [c[1] for c in cs]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    return window[0] <= cx <= window[2] and window[1] <= cy <= window[3]

def _dp(pts, tol):
    """Douglas–Peucker over one line (iterative, so a long coastline cannot overflow the stack)."""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a][0], pts[a][1]
        bx, by = pts[b][0], pts[b][1]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = pts[i][0], pts[i][1]
            d = math.hypot(px - ax, py - ay) if norm == 0 else abs(dy * px - dx * py + bx * ay - by * ax) / norm
            if d > best:
                best, idx = d, i
        if idx >= 0 and best > tol:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(pts, keep) if k]

def simplify(g, tol=NE_SIMPLIFY):
    """Simplify a GeoJSON geometry; a ring that collapses below 4 points keeps its original shape."""
    t = g["type"]
    def ring(r):
        s2 = _dp(r, tol)
        return s2 if len(s2) >= 4 else r
    def line(l):
        s2 = _dp(l, tol)
        return s2 if len(s2) >= 2 else l
    if t == "Polygon":
        c = [ring(r) for r in g["coordinates"]]
    elif t == "MultiPolygon":
        c = [[ring(r) for r in p] for p in g["coordinates"]]
    elif t == "LineString":
        c = line(g["coordinates"])
    elif t == "MultiLineString":
        c = [line(l) for l in g["coordinates"]]
    else:
        c = g["coordinates"]
    return round_coords({"type": t, "coordinates": c}, 4)

def ne_props(ft):
    return {k.lower(): v for k, v in (ft.get("properties") or {}).items()}

def ne_names(p, label):
    """The label, NE's own name (title-cased, abbreviations spelled out), its alternates and its
    translated names, each with its language."""
    raw = title_case(p.get("name") or "")
    names = [(label, ""), (raw, ""), (expand(raw), "")]
    for alt in re.split(r"[|;/]", (p.get("namealt") or "") + "|" + (p.get("name_alt") or "")):
        alt = title_case(alt)
        if alt:
            names += [(alt, ""), (expand(alt), "")]
    for l in NE_NAME_LANGS:
        v = title_case(p.get("name_" + l) or "")
        if v:
            names.append((v, NE_LANG_TAG.get(l, l)))
            if l == "en":
                names.append((expand(v), "en"))
    return names

def ne_label(p):
    """NE's own `name` ("Balkan Pen.", "Levant Coast", "Lake Ladoga"), title-cased and spelled out —
    its `name_en` is sometimes a looser English gloss ("Balkans", "Levant" for the coastal strip,
    "Ladoga"), which stays a further name: the geocoder ranks a LABEL above a name, and "Levant" must
    mean the region, not NE's coastal strip."""
    return expand(title_case(p.get("name") or "")) or expand(title_case(p.get("name_en") or ""))

def ne_river_label(p):
    """A river's `name` is the local one ("Dnipro", "Donau"); the corpus writes the English one."""
    return expand(title_case(p.get("name_en") or "")) or expand(title_case(p.get("name") or ""))

def natural_earth_physical(src_dir, out):
    feats = []
    def load(name):
        return json.load(open(os.path.join(src_dir, name + ".geojson")))["features"]
    def add(pid, label, kind, geom, p, featurecla):
        if not label or not geom or not in_window(geom):
            return
        props = {"featurecla": featurecla, "modern": True}
        if p.get("wikidataid"):
            props["wikidata"] = p["wikidataid"]
        feats.append(feature(pid, label, kind, geometry=simplify(geom), names=ne_names(p, label),
                             source=NE_PHYS_SOURCE, props=props))
    for ft in load("ne_10m_geography_regions_polys"):
        p = ne_props(ft)
        cla = (p.get("featurecla") or "").strip()
        if cla.lower() == "continent":
            continue  # the continents are the countries file's hierarchy nodes
        add("ne_physical_" + str(p["ne_id"]), ne_label(p), NE_KIND.get(cla.lower(), "region"),
            ft.get("geometry"), p, cla)
    for ft in load("ne_10m_geography_marine_polys"):
        p = ne_props(ft)
        add("ne_marine_" + str(p["ne_id"]), ne_label(p), "feature", ft.get("geometry"), p,
            (p.get("featurecla") or "").strip())
    for ft in load("ne_10m_lakes"):
        p = ne_props(ft)
        cla = (p.get("featurecla") or "").strip()
        if cla.lower() == "reservoir":
            continue  # a modern dam's lake is no place the corpus writes about
        add("ne_lake_" + str(p["ne_id"]), ne_label(p), "feature", ft.get("geometry"), p, cla)
    # rivers come in many segments (and "Lake Centerline" pieces through lakes): one place per river
    rivers = {}
    for ft in load("ne_10m_rivers_lake_centerlines"):
        p = ne_props(ft)
        g = ft.get("geometry")
        if not g or not coords_of(g) or not (p.get("name") or "").strip():
            continue
        key = (p.get("rivernum"), title_case(p.get("name_en") or p.get("name")))
        lines = [g["coordinates"]] if g["type"] == "LineString" else g["coordinates"]
        r = rivers.setdefault(key, {"p": p, "lines": []})
        r["lines"] += lines
    for (num, _), r in sorted(rivers.items(), key=lambda kv: (str(kv[0][0]), kv[0][1])):
        p = r["p"]
        label = ne_river_label(p)
        add("ne_river_" + (str(int(num)) + "_" if num is not None else "") + slug(label), label, "feature",
            {"type": "MultiLineString", "coordinates": r["lines"]}, p, "River")
    for name in ("ne_10m_geography_regions_points", "ne_10m_geography_regions_elevation_points"):
        for ft in load(name):
            p = ne_props(ft)
            cla = (p.get("featurecla") or "").strip()
            add("ne_point_" + str(p["ne_id"]), ne_label(p), NE_KIND.get(cla.lower(), "feature"),
                ft.get("geometry"), p, cla)
    # one id once (a river crossing the window edge, a duplicate NE row)
    seen, uniq = set(), []
    for f in feats:
        if f["@id"] not in seen:
            seen.add(f["@id"])
            uniq.append(f)
    write(out, uniq)

NE_PLACES_SOURCE = "Natural Earth 1:10m Populated Places (public domain)"

def natural_earth_places(src, out):
    """Present-day cities and towns as points (`kind = site`, `modern: true`): the ones a finding
    names in plain English ("Kraków", "Sergiev Posad"). Undated — and therefore not drawn by the
    time-sliced map (`PlaceRepo.geoJson` skips `modern` places), only found and matched."""
    feats = []
    for ft in json.load(open(src))["features"]:
        p = ne_props(ft)
        g = ft.get("geometry")
        if not g or not in_window(g):
            continue
        label = (p.get("name_en") or p.get("name") or "").strip()
        if not label:
            continue
        names = [(label, ""), (p.get("name"), ""), (p.get("nameascii"), ""), (p.get("ls_name"), "")]
        names += [(a, "") for a in re.split(r"[|;]", p.get("namealt") or "")]
        names += [(p.get("name_" + l), NE_LANG_TAG.get(l, l)) for l in NE_NAME_LANGS]
        props = {"featurecla": p.get("featurecla"), "modern": True, "country": p.get("adm0name"),
                 "admin1": p.get("adm1name")}
        if p.get("wikidataid"):
            props["wikidata"] = p["wikidataid"]
        if p.get("geonamesid") and int(p["geonamesid"]) > 0:
            props["geonames"] = int(p["geonamesid"])
        feats.append(feature("ne_city_" + str(p["ne_id"]), label, "site",
                             geometry=round_coords(g, 5), names=names, source=NE_PLACES_SOURCE,
                             props={k: v for k, v in props.items() if v}))
    write(out, feats)

# ── the curated supplement (#987) ────────────────────────────────────────────────────────────────
# `supplement/names.csv`  place_id,name,lang,why     — a further name for a place ANY seed file has
# `supplement/places.csv` id,label,kind,lon,lat,names,source  — a place no seed file has; `names` is
#                         `|`-separated, `source` says where the point comes from (never invented)
CURATED_FILE = "05-curated.json"

def supplement(places_dir):
    sup = os.path.join(places_dir, "supplement")
    rows = list(csv.DictReader(open(os.path.join(sup, "places.csv"), newline="")))
    feats = []
    for r in rows:
        pid, label, kind = r["id"].strip(), r["label"].strip(), r["kind"].strip()
        src = (r.get("source") or "").strip()
        if not src:
            sys.exit(f"supplement/places.csv: {pid} has no source — every curated point needs one")
        if kind not in ("site", "region", "feature"):
            sys.exit(f"supplement/places.csv: {pid}: kind '{kind}' is not site|region|feature")
        lon, lat = float(r["lon"]), float(r["lat"])
        names = [(label, "")] + [(n.strip(), "en") for n in (r.get("names") or "").split("|") if n.strip()]
        feats.append(feature(pid, label, kind,
                             geometry={"type": "Point", "coordinates": [round(lon, 5), round(lat, 5)]},
                             names=names, source=src, props={"curated": True}))
    write(os.path.join(places_dir, CURATED_FILE), feats)

    extra = {}
    for r in csv.DictReader(open(os.path.join(sup, "names.csv"), newline="")):
        extra.setdefault(r["place_id"].strip(), []).append((r["name"].strip(), (r.get("lang") or "").strip()))
    found = set()
    for fname in sorted(os.listdir(places_dir)):
        path = os.path.join(places_dir, fname)
        if not fname.endswith(".json") or not os.path.isfile(path):
            continue
        doc = json.load(open(path))
        changed = False
        for f in doc["features"]:
            add = extra.get(f.get("@id"))
            if not add:
                continue
            found.add(f["@id"])
            have = {(n["toponym"].lower(), n.get("lang", "")) for n in f.get("names", [])}
            for name, lang in add:
                if (name.lower(), lang) not in have:
                    n = {"toponym": name}
                    if lang:
                        n["lang"] = lang
                    f.setdefault("names", []).append(n)
                    have.add((name.lower(), lang))
                    changed = True
        if changed:
            write(path, doc["features"])
    missing = sorted(set(extra) - found)
    if missing:
        sys.exit("supplement/names.csv names places no seed file has: " + ", ".join(missing))
    print(f"supplement: {len(feats)} curated place(s), {sum(map(len, extra.values()))} curated name(s)",
          file=sys.stderr)

def reclassify(path):
    """Apply `pleiades_kind` and drop the withdrawn duplicates in an already built `03-pleiades.json`,
    in place, from the `feature_types` each feature keeps — no download. Idempotent."""
    feats = json.load(open(path))["features"]
    out, changed = [], 0
    for f in feats:
        p = f["properties"]
        if withdrawn_duplicate(p.get("title")):
            changed += 1
            continue
        types = [t.strip() for t in (p.get("feature_types") or "").split(",") if t.strip()]
        kind = pleiades_kind(types, p.get("title") or "")
        if kind != p.get("kind"):
            changed += 1
            p["kind"] = kind
            f["types"] = [{"label": kind}]
        out.append(f)
    write(path, out)
    print(f"reclassify: {changed} feature(s) changed or dropped", file=sys.stderr)

def write(path, feats):
    with open(path, "w") as fh:
        json.dump(collection(feats), fh, separators=(",", ":"), ensure_ascii=False)
        fh.write("\n")
    print(f"{path}: {len(feats)} feature(s)", file=sys.stderr)

COMMANDS = {"natural-earth": (natural_earth, 2), "natural-earth-physical": (natural_earth_physical, 2),
            "natural-earth-places": (natural_earth_places, 2), "pleiades": (pleiades, 3),
            "supplement": (supplement, 1), "reclassify": (reclassify, 1)}

if __name__ == "__main__":
    cmd = COMMANDS.get(sys.argv[1] if len(sys.argv) > 1 else "")
    if not cmd or len(sys.argv) != cmd[1] + 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    cmd[0](*sys.argv[2:])
