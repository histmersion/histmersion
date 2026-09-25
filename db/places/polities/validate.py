#!/usr/bin/env python3
"""Validate the polity-polygon GeoJSON seed files against the places-importer contract.

Stdlib only. Run:  python3 db/places/polities/validate.py db/places/polities/*.geojson
Exit code 0 = all good, 1 = at least one error.

Checks per feature:
  * required properties present and well typed
  * kind in {polity, region}; ids are unique across ALL files and are slug-shaped
  * period_start < period_end (signed years, BCE negative); macro-regions may have null periods
  * names[] entries have name+lang; part_of[] entries have parent_id + its own period,
    the period is inside the feature's own period, and parent_id resolves to a macro-region
    in macro_regions.geojson or to one of the known external continent ids
  * source / source_url / licence non-empty; licence from an allow-list of open licences
  * method in {ohm-import, agent-drawn}; agent-drawn features carry a `reference`
  * geometry is Polygon or MultiPolygon in WGS84 lon/lat range, rings are closed,
    have >= 4 positions, do not self-intersect, and follow the RFC 7946 right-hand rule
    (exterior counter-clockwise, holes clockwise)
"""
import json, re, sys, glob, os

EXTERNAL_PARENTS = {"continent_asia", "continent_europe", "continent_africa"}
LICENCES = {"ODbL-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "CC0-1.0"}
KINDS = {"polity", "region"}
METHODS = {"ohm-import", "agent-drawn"}
SLUG = re.compile(r"^[a-z][a-z0-9_]*$")
REQUIRED = ["id", "label", "kind", "period_start", "period_end", "names",
            "part_of", "source", "source_url", "licence", "notes"]

errors, warnings = [], []


def err(where, msg):
    errors.append("ERROR %s: %s" % (where, msg))


def warn(where, msg):
    warnings.append("WARN  %s: %s" % (where, msg))


def signed_area(ring):
    s = 0.0
    for i in range(len(ring) - 1):
        x1, y1 = ring[i][0], ring[i][1]
        x2, y2 = ring[i + 1][0], ring[i + 1][1]
        s += x1 * y2 - x2 * y1
    return s / 2.0


def _orient(a, b, c):
    v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    if v > 1e-12:
        return 1
    if v < -1e-12:
        return -1
    return 0


def _on_seg(a, b, p):
    return (min(a[0], b[0]) - 1e-12 <= p[0] <= max(a[0], b[0]) + 1e-12 and
            min(a[1], b[1]) - 1e-12 <= p[1] <= max(a[1], b[1]) + 1e-12)


def segs_cross(p1, p2, p3, p4):
    d1, d2 = _orient(p3, p4, p1), _orient(p3, p4, p2)
    d3, d4 = _orient(p1, p2, p3), _orient(p1, p2, p4)
    if d1 * d2 < 0 and d3 * d4 < 0:
        return True
    if d1 == 0 and _on_seg(p3, p4, p1):
        return True
    if d2 == 0 and _on_seg(p3, p4, p2):
        return True
    if d3 == 0 and _on_seg(p1, p2, p3):
        return True
    if d4 == 0 and _on_seg(p1, p2, p4):
        return True
    return False


def check_ring(where, ring, is_exterior):
    if len(ring) < 4:
        err(where, "ring has %d positions, need >= 4" % len(ring))
        return
    if ring[0] != ring[-1]:
        err(where, "ring is not closed")
        return
    for pos in ring:
        if not (-180.0 <= pos[0] <= 180.0 and -90.0 <= pos[1] <= 90.0):
            err(where, "position out of WGS84 range: %r" % (pos,))
            return
    n = len(ring) - 1
    for i in range(n):
        for j in range(i + 1, n):
            if j == i or (j == i + 1) or (i == 0 and j == n - 1):
                continue
            if segs_cross(ring[i], ring[i + 1], ring[j], ring[j + 1]):
                err(where, "ring self-intersects near segments %d and %d" % (i, j))
                return
    a = signed_area(ring)
    if a == 0:
        err(where, "ring has zero area")
    elif is_exterior and a < 0:
        err(where, "exterior ring is clockwise; RFC 7946 requires counter-clockwise")
    elif (not is_exterior) and a > 0:
        err(where, "interior ring is counter-clockwise; RFC 7946 requires clockwise")


def check_geometry(where, geom):
    if not isinstance(geom, dict) or geom.get("type") not in ("Polygon", "MultiPolygon"):
        err(where, "geometry must be Polygon or MultiPolygon, got %r" % (geom or {}).get("type"))
        return
    polys = [geom["coordinates"]] if geom["type"] == "Polygon" else geom["coordinates"]
    for pi, poly in enumerate(polys):
        for ri, ring in enumerate(poly):
            check_ring("%s poly[%d] ring[%d]" % (where, pi, ri), ring, ri == 0)


def main(paths):
    seen_ids, macro_ids, per_file = {}, set(), []
    docs = []
    for p in sorted(paths):
        with open(p) as fh:
            doc = json.load(fh)
        docs.append((p, doc))
        if os.path.basename(p) == "macro_regions.geojson":
            macro_ids = {f["properties"]["id"] for f in doc.get("features", [])}

    for p, doc in docs:
        base = os.path.basename(p)
        if doc.get("type") != "FeatureCollection":
            err(base, "top level type is not FeatureCollection")
            continue
        feats = doc.get("features") or []
        per_file.append((base, len(feats)))
        for f in feats:
            props = f.get("properties") or {}
            fid = props.get("id", "<no id>")
            where = "%s [%s]" % (base, fid)
            for k in REQUIRED:
                if k not in props:
                    err(where, "missing required property %r" % k)
            if not SLUG.match(str(fid)):
                err(where, "id is not a lowercase slug")
            if fid in seen_ids:
                err(where, "duplicate id, also in %s" % seen_ids[fid])
            else:
                seen_ids[fid] = base
            if props.get("kind") not in KINDS:
                err(where, "kind %r not in %s" % (props.get("kind"), sorted(KINDS)))
            ps, pe = props.get("period_start"), props.get("period_end")
            if ps is None or pe is None:
                if props.get("kind") != "region":
                    err(where, "null period is only allowed for kind=region")
            else:
                if not isinstance(ps, int) or not isinstance(pe, int):
                    err(where, "period bounds must be signed integer years")
                elif ps >= pe:
                    err(where, "period_start (%s) must be < period_end (%s)" % (ps, pe))
            names = props.get("names")
            if not isinstance(names, list) or not names:
                err(where, "names must be a non-empty list")
            else:
                for nm in names:
                    if not isinstance(nm, dict) or not nm.get("name") or not nm.get("lang"):
                        err(where, "bad names entry %r" % (nm,))
            po = props.get("part_of")
            if not isinstance(po, list):
                err(where, "part_of must be a list")
            else:
                for rel in po:
                    if not isinstance(rel, dict) or not rel.get("parent_id"):
                        err(where, "bad part_of entry %r" % (rel,))
                        continue
                    pid = rel["parent_id"]
                    if pid not in macro_ids and pid not in EXTERNAL_PARENTS:
                        err(where, "part_of parent %r resolves to nothing" % pid)
                    rs, re_ = rel.get("period_start"), rel.get("period_end")
                    if (rs is None) != (re_ is None):
                        err(where, "part_of %r has a half-open period" % pid)
                    if rs is not None and re_ is not None:
                        if rs >= re_:
                            err(where, "part_of %r period_start >= period_end" % pid)
                        elif ps is not None and (rs < ps or re_ > pe):
                            warn(where, "part_of %r period %s..%s is wider than the "
                                        "feature's own %s..%s" % (pid, rs, re_, ps, pe))
            for k in ("source", "source_url", "licence"):
                if not props.get(k):
                    err(where, "%s is empty" % k)
            if props.get("licence") not in LICENCES:
                err(where, "licence %r not in the open allow-list %s"
                    % (props.get("licence"), sorted(LICENCES)))
            m = props.get("method")
            if m not in METHODS:
                err(where, "method %r not in %s" % (m, sorted(METHODS)))
            if m == "agent-drawn" and not props.get("reference"):
                err(where, "agent-drawn feature has no `reference`")
            if not props.get("notes"):
                warn(where, "no notes")
            check_geometry(where, f.get("geometry"))

    for base, n in per_file:
        print("  %-24s %2d features" % (base, n))
    print("  %-24s %2d features, %d unique ids" % ("TOTAL", sum(n for _, n in per_file), len(seen_ids)))
    for w in warnings:
        print(w)
    for e in errors:
        print(e)
    print("%d error(s), %d warning(s)" % (len(errors), len(warnings)))
    return 1 if errors else 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        args = sorted(glob.glob(os.path.join(os.path.dirname(os.path.abspath(__file__)), "*.geojson")))
    sys.exit(main(args))
