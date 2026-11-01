"""Live-volume driver: fixed requests against a scratch server; digests, counts and timings only.
usage: live_driver.py BASE_URL OUT_TSV [ROUNDS]"""
import sys, math, hashlib, time, json, urllib.request, urllib.error, urllib.parse, statistics

base, out = sys.argv[1], sys.argv[2]
rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 20

def tile_of(z, lon, lat):
    n = 1 << z
    x = int((lon + 180) / 360 * n)
    r = math.radians(lat)
    y = int((1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.pi) / 2 * n)
    return z, x, y

points = [(34.615, 40.02), (22.756, 37.73), (44.42, 32.54), (32.65, 25.7), (12.49, 41.89)]
coords = sorted({(0, 0, 0), (1, 1, 0)} | {tile_of(z, lo, la) for z in (2, 4, 6, 8, 10) for lo, la in points})
reqs = []
for layer in ("sites", "events", "media"):
    for (z, x, y) in coords:
        for v in ("", "?from=-1600&to=-1100", "?undated=true&from=-1600&to=-1100"):
            if layer == "media" and "undated" in v: continue
            reqs.append(("tile", f"/api/tiles/{layer}/{z}/{x}/{y}{v}"))
bboxes = ["minLon=20&minLat=34&maxLon=30&maxLat=42", "minLon=26&minLat=35&maxLon=45&maxLat=43", "minLon=-10&minLat=20&maxLon=60&maxLat=60"]
periods = ["from=-1600&to=-1100", "from=-3000&to=-500"]
for b in bboxes:
    for p in periods:
        reqs.append(("geo-when", f"/api/findings?{b}&{p}"))
        reqs.append(("events", f"/api/events?{b}&{p}"))
        reqs.append(("events-geo", f"/api/events/geo?{b}&{p}"))
        reqs.append(("events-leads", f"/api/events/leads?{b}&{p}"))
    reqs.append(("geo-when", f"/api/findings?{b}"))
for p in ("asOf=-1300", "asOf=-2000", "from=-1600&to=-1100", "from=-1600&to=-1100&pick=latest"):
    reqs.append(("places-geo", f"/api/places/geo?kind=region&{p}"))
reqs.append(("places-geo", "/api/places/geo?kind=site&asOf=-1300"))
for q in ("hatt", "myc", "bab", "ur", "anatolia"):
    reqs.append(("places", f"/api/places?q={q}"))
    reqs.append(("places", f"/api/places?q={q}&asOf=-1300"))
for p in periods:
    reqs.append(("histogram", f"/api/aggregate?{p}"))
    reqs.append(("histogram", f"/api/aggregate?by=region&{p}"))

# one place of each search's first answer: its detail, counts, children, tree, footprints and links, and what its id
# selects; recorded under a symbolic name (the place's id is looked up at run time and never written)
labels = {}
def first_place(q):
    with urllib.request.urlopen(base + f"/api/places?q={q}&limit=1", timeout=600) as r:
        hits = json.loads(r.read())
    return hits[0]["id"] if hits else None
for q in ("hatt", "myc", "bab"):
    pid = first_place(q)
    if pid is None: continue
    ref = urllib.parse.quote(pid, safe="")
    for suffix in ("", "?counts=true", "?asOf=-1300", "/children", "/children?asOf=-1300", "/tree", "/tree?asOf=-1300&depth=2", "/footprints", "/links"):
        path = f"/api/places/{ref}{suffix}"
        reqs.append(("place", path)); labels[path] = f"/api/places/{{{q}}}{suffix}"
    for sel in ("findings?place=", "events?place=", "aggregate?by=region&place="):
        path = f"/api/{sel}{ref}"
        reqs.append(("place-scope", path)); labels[path] = f"/api/{sel}{{{q}}}"

def fetch(path):
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(base + path, timeout=600) as r:
            body, status, etag = r.read(), r.status, r.headers.get("etag", "-")
    except urllib.error.HTTPError as e:
        body, status, etag = e.read(), e.code, "-"
    return (time.perf_counter() - t0) * 1000, status, body, etag

# warm-up pass, a pause for background builds, then the recorded pass and a second for stability
for _, p in reqs: fetch(p)
time.sleep(30)
first = {}
for _, p in reqs:
    _, s, b, e = fetch(p); first[p] = (s, len(b), hashlib.sha256(b).hexdigest(), e)
unstable = []
for _, p in reqs:
    _, s, b, e = fetch(p)
    if (s, len(b), hashlib.sha256(b).hexdigest(), e) != first[p]: unstable.append(p)
tile_total = sum(first[p][1] for k, p in reqs if k == "tile")
tile_max = max(first[p][1] for k, p in reqs if k == "tile")
times = {p: [] for _, p in reqs}
for _ in range(rounds):
    for _, p in reqs:
        ms, *_ = fetch(p); times[p].append(ms)
with open(out, "w") as f:
    f.write(f"# requests {len(reqs)} rounds {rounds} tile-bytes {tile_total} largest-tile {tile_max} unstable {len(unstable)}\n")
    for i, (k, p) in enumerate(reqs):
        ts = sorted(times[p]); med = statistics.median(ts); p95 = ts[max(0, math.ceil(0.95 * len(ts)) - 1)]
        s, n, h, e = first[p]
        f.write(f"{i:03d}\t{k}\t{labels.get(p, p)}\t{s}\t{n}\t{h}\t{e}\t{med:.1f}\t{p95:.1f}\n")
print(f"{len(reqs)} requests, unstable {len(unstable)}: {[labels.get(u, u) for u in unstable[:5]]}")
