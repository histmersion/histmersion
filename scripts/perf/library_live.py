import sys, math, hashlib, time, json, re, urllib.request, urllib.error, urllib.parse, statistics

base, out = sys.argv[1], sys.argv[2]
rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 20
q = urllib.parse.quote

def read(path, body=None):
    req = urllib.request.Request(base + path, data=body, headers={"content-type": "application/json"} if body else {})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.loads(r.read())

labels = {}
reqs = []
def add(kind, path, label=None, body=None):
    reqs.append((kind, path, body))
    if label: labels[(path, body)] = label

def top(field, n):
    return [f["value"] for f in read(f"/api/facets?field={field}&limit={n}")][:n]
tags, topics, statuses = top("tags", 3), top("topics", 2), top("status", 3)
place_ids = [h["id"] for h in read("/api/places?q=anatolia&limit=1") + read("/api/places?q=crete&limit=1")]

texts = ["hittite", "bronze age", '"sea peoples"', "mycenae -crete", "tin or copper", "collapse", "chariot", "the"]
for t in texts:
    add("sources", f"/api/sources?q={q(t)}")
for t in texts[:3]:
    for s in ("cited", "period", "year", "oldest", "title"):
        add("sources", f"/api/sources?q={q(t)}&sort={s}")
    add("sources", f"/api/sources?q={q(t)}&limit=50&offset=50")
for i, t in enumerate(tags):
    add("sources", f"/api/sources?tag={q(t)}", f"/api/sources?tag={{tag{i}}}")
add("sources", f"/api/sources?tag={q(tags[0])}&tag={q(tags[1])}", "/api/sources?tag={tag0}&tag={tag1}")
for i, t in enumerate(topics):
    add("sources", f"/api/sources?topic={q(t)}", f"/api/sources?topic={{topic{i}}}")
for i, s in enumerate(statuses):
    add("sources", f"/api/sources?status={q(s)}", f"/api/sources?status={{status{i}}}")
for i, p in enumerate(place_ids):
    add("sources", f"/api/sources?place={q(p)}", f"/api/sources?place={{place{i}}}")
    add("sources", f"/api/sources?place={q(p)}&from=-1600&to=-1100", f"/api/sources?place={{place{i}}}&from=-1600&to=-1100")
for p in ("Anatolia", "Crete", "Egypt"):
    add("sources", f"/api/sources?place={p}")
boxes = ["minLon=20&minLat=34&maxLon=30&maxLat=42", "minLon=26&minLat=35&maxLon=45&maxLat=43", "minLon=170&minLat=-50&maxLon=-170&maxLat=10"]
spans = ["from=-1600&to=-1100", "from=-3000&to=-500", "from=-1200"]
for b in boxes:
    add("sources", f"/api/sources?{b}")
for s in spans:
    add("sources", f"/api/sources?{s}")
add("sources", "/api/sources?yearFrom=2000&yearTo=2020")
add("sources", f"/api/sources?q=bronze&{boxes[0]}&{spans[0]}&sort=cited")
add("sources", "/api/sources?limit=200")
add("search", "/api/search?q=hittite")
add("search", "/api/search?q=collapse&limit=5")
for f in ("tags", "authors", "places", "topics", "status", "provenance"):
    add("facets", f"/api/facets?field={f}")
add("facets", "/api/facets?field=tags&prefix=bro")
add("facets", "/api/facets?field=authors&prefix=c")
add("facets", "/api/facets?field=places&prefix=an")
add("facets", "/api/facets?field=tags&q=hittite")
add("facets", f"/api/facets?field=topics&{spans[0]}")
add("facets", f"/api/facets?field=tags&place={q(place_ids[0])}", "/api/facets?field=tags&place={place0}")
for b in boxes[:2]:
    for s in spans[:2]:
        add("findings", f"/api/findings?{b}&{s}")
add("findings", f"/api/findings?tag={q(tags[0])}", "/api/findings?tag={tag0}")
add("findings", "/api/findings?q=hittite")
add("findings", "/api/findings/page?limit=100")
add("findings", "/api/findings/page?limit=100&offset=100")
for s in spans[:2]:
    add("histogram", f"/api/aggregate?{s}")
    add("histogram", f"/api/aggregate?{s}&unit=findings")
    add("histogram", f"/api/aggregate?{s}&unit=starts")
    add("histogram", f"/api/aggregate?by=time&{s}&depth=2&samples=2")
    add("histogram", f"/api/aggregate?by=time,region&{s}&depth=2")
add("histogram", f"/api/aggregate?by=region&tag={q(tags[0])}", "/api/aggregate?by=region&tag={tag0}")
add("histogram", "/api/aggregate?q=hittite&from=-2000&to=-1000")

hits = []
for t in ("hittite", "collapse", "chariot"):
    hits += [h["source"]["citekey"] for h in read(f"/api/sources?q={q(t)}&limit=2")["hits"]]
for i, ck in enumerate(dict.fromkeys(hits)):
    r = q(ck, safe="")
    for suffix in ("?body=false", "", "/view", "/view?section=toc", "/view?section=findings", "/view?section=body&offset=1000&chars=4000", "/passages?q=bronze", "/passages?q=the&limit=50", "/export", "/lint"):
        add("source", f"/api/sources/{r}{suffix}", f"/api/sources/{{hit{i}}}{suffix}")
    for path in (f"/api/neighbors/{r}", f"/api/graph/{r}", f"/api/graph/{r}?depth=3", f"/api/contradictions?citekey={r}"):
        add("citations", path, path.replace(r, f"{{hit{i}}}"))
add("citations", "/api/contradictions")
add("citations", "/api/contradictions?since=2000")
add("sources", "/api/citekeys")
add("sources", "/api/source-aliases")
for s in ("SELECT count(*) FROM sources", "SELECT status, count(*) FROM sources GROUP BY status ORDER BY status",
          "SELECT count(*) FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE s.year > 2000",
          "SELECT kind, count(*) FROM edges GROUP BY kind ORDER BY kind", "SELECT 1; SELECT 2", "DELETE FROM sources"):
    add("query", "/api/query", None, json.dumps({"sql": s}).encode())
add("query", "/api/schema/tables")

elapsed = re.compile(rb'"elapsedMs"\s*:\s*\d+')
def fetch(path, body):
    t0 = time.perf_counter()
    try:
        req = urllib.request.Request(base + path, data=body, headers={"content-type": "application/json"} if body else {})
        with urllib.request.urlopen(req, timeout=600) as r:
            data, status, etag = r.read(), r.status, r.headers.get("etag", "-")
    except urllib.error.HTTPError as e:
        data, status, etag = e.read(), e.code, "-"
    return (time.perf_counter() - t0) * 1000, status, elapsed.sub(b'"elapsedMs":0', data), etag

for _, p, b in reqs: fetch(p, b)
time.sleep(30)
first = {}
for _, p, b in reqs:
    _, s, d, e = fetch(p, b); first[(p, b)] = (s, len(d), hashlib.sha256(d).hexdigest(), e)
unstable = []
for _, p, b in reqs:
    _, s, d, e = fetch(p, b)
    if (s, len(d), hashlib.sha256(d).hexdigest(), e) != first[(p, b)]: unstable.append((p, b))
times = {(p, b): [] for _, p, b in reqs}
for _ in range(rounds):
    for _, p, b in reqs:
        ms, *_ = fetch(p, b); times[(p, b)].append(ms)
with open(out, "w") as f:
    f.write(f"# requests {len(reqs)} rounds {rounds} unstable {len(unstable)}\n")
    for i, (k, p, b) in enumerate(reqs):
        ts = sorted(times[(p, b)]); med = statistics.median(ts); p95 = ts[max(0, math.ceil(0.95 * len(ts)) - 1)]
        s, n, h, e = first[(p, b)]
        name = labels.get((p, b), p) if b is None else f"{p} #{i}"
        f.write(f"{i:03d}\t{k}\t{name}\t{s}\t{n}\t{h}\t{e}\t{med:.1f}\t{p95:.1f}\n")
print(f"{len(reqs)} requests, unstable {len(unstable)}: {[labels.get(u, u[0]) for u in unstable[:5]]}")
