#!/usr/bin/env python3
"""Build the committed gazetteer seed files (plan §1.6a) from their upstream sources.

    ./build.py natural-earth <ne_110m_admin_0_countries.geojson> <out.json>
    ./build.py pleiades      <pleiades-places-latest.csv>        <out.json>

Both emit Linked Places Format (LPF v1.1), which `histmersion places import` reads as-is. Nothing here
talks to the database: the files are data, and `fetch.sh` is how they are regenerated.
"""
import csv, json, re, sys

csv.field_size_limit(10 ** 7)

# The corpus's window (Aegean → Iranian plateau, Egypt → Caucasus). Pleiades covers the whole Roman
# world; keeping all 42 000 places would commit ~20 MB of points nothing in the corpus refers to.
BBOX = (15.0, 25.0, 60.0, 50.0)  # minLon, minLat, maxLon, maxLat

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

def pleiades(src, out):
    feats = []
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
        kind = next((PL_KIND[t] for t in types if t in PL_KIND), "site")
        title = (r.get("title") or "").strip()
        if not title:
            continue
        when = None
        try:
            lo, hi = float(r["minDate"]), float(r["maxDate"])
            when = (int(lo), int(hi))
        except (TypeError, ValueError, KeyError):
            pass
        # Pleiades titles carry alternates as "Dianium/Hemeroskopeion"
        names = [(title, "")] + [(part.strip(), "") for part in title.split("/") if part.strip()]
        feats.append(feature(
            "pleiades_" + r["id"], title, kind,
            geometry={"type": "Point", "coordinates": [round(lon, 5), round(lat, 5)]},
            when=when, names=names, source=PL_SOURCE,
            props={"pleiades_uri": "https://pleiades.stoa.org/places/" + r["id"],
                   "feature_types": ",".join(types),
                   "time_periods": r.get("timePeriodsKeys") or ""}))
    write(out, feats)

def write(path, feats):
    with open(path, "w") as fh:
        json.dump(collection(feats), fh, separators=(",", ":"), ensure_ascii=False)
        fh.write("\n")
    print(f"{path}: {len(feats)} feature(s)", file=sys.stderr)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    {"natural-earth": natural_earth, "pleiades": pleiades}[sys.argv[1]](sys.argv[2], sys.argv[3])
