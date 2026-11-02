"""Live-volume driver for research's read paths: the article list under fixed filters, reads of single articles (in
full, brief, their coverage, review, versions, export and lint), their pages and the backlinks of the sources they
cite, against a scratch server; digests, counts and timings only. A value the corpus holds (a slug, a kind, a tag, a
citekey) is looked up at run time and written under a symbolic name, never itself.
usage: research_live.py BASE_URL OUT_TSV [ROUNDS]"""
import sys, math, hashlib, time, json, re, urllib.request, urllib.error, urllib.parse, statistics

base, out = sys.argv[1], sys.argv[2]
rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 20
q = urllib.parse.quote

def read(path):
    with urllib.request.urlopen(urllib.request.Request(base + path), timeout=600) as r:
        return json.loads(r.read())

labels = {}
reqs = []
def add(kind, path, label=None):
    reqs.append((kind, path))
    if label: labels[path] = label

# what the corpus holds, looked up once: the source tags most used, the articles' kinds, the first articles of lists
tags = [f["value"] for f in read("/api/facets?field=tags&limit=3")][:3]
listing = read("/api/articles?limit=1000")["articles"]
kinds = sorted({a["kind"] for a in listing})

add("list", "/api/articles")
add("list", "/api/articles?limit=1000")
add("list", "/api/articles?limit=50&offset=50")
for i, k in enumerate(kinds):
    add("list", f"/api/articles?kind={q(k)}", f"/api/articles?kind={{kind{i}}}")
for t in ("bronze", "collapse", "hittite", "the"):
    add("list", f"/api/articles?text={q(t)}")
for i, t in enumerate(tags):
    add("list", f"/api/articles?tag={q(t)}", f"/api/articles?tag={{tag{i}}}")
for t in ("bronze", "sea peoples"):
    add("list", f"/api/articles?q={q(t)}")
add("list", "/api/articles?from=-1600&to=-1100")
add("list", "/api/articles?status=contested")
add("list", "/api/article-slugs")

# single articles: the first of each kind and the three with the longest summaries
picked = []
for k in kinds:
    picked += [a["slug"] for a in listing if a["kind"] == k][:1]
picked += [a["slug"] for a in sorted(listing, key=lambda a: (-len(a.get("summary") or ""), a["slug"]))[:3]]
picked = list(dict.fromkeys(picked))[:10]
cited = []
for i, s in enumerate(picked):
    r = q(s, safe="")
    for suffix in ("", "?brief=true", "/coverage", "/review", "/versions", "/export", "/lint"):
        add("article", f"/api/articles/{r}{suffix}", f"/api/articles/{{art{i}}}{suffix}")
    for suffix in ("", "?fragment=true", "?fragment=true&q=bronze"):
        add("page", f"/article/{r}{suffix}", f"/article/{{art{i}}}{suffix}")
    try:
        cited += [c["citekey"] for c in read(f"/api/articles/{r}?brief=true").get("sourcesCited", [])][:2]
    except Exception:
        pass
for i, ck in enumerate(list(dict.fromkeys(cited))[:8]):
    add("backlinks", f"/api/sources/{q(ck, safe='')}/articles", f"/api/sources/{{cited{i}}}/articles")
add("page", "/article/index?fragment=true")

elapsed = re.compile(rb'"elapsedMs"\s*:\s*\d+')
def fetch(path):
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path), timeout=600) as r:
            data, status, etag = r.read(), r.status, r.headers.get("etag", "-")
    except urllib.error.HTTPError as e:
        data, status, etag = e.read(), e.code, "-"
    return (time.perf_counter() - t0) * 1000, status, elapsed.sub(b'"elapsedMs":0', data), etag

# warm-up pass, a pause for background builds, then the recorded pass and a second for stability
for _, p in reqs: fetch(p)
time.sleep(30)
first = {}
for _, p in reqs:
    _, s, d, e = fetch(p); first[p] = (s, len(d), hashlib.sha256(d).hexdigest(), e)
unstable = []
for _, p in reqs:
    _, s, d, e = fetch(p)
    if (s, len(d), hashlib.sha256(d).hexdigest(), e) != first[p]: unstable.append(p)
times = {p: [] for _, p in reqs}
for _ in range(rounds):
    for _, p in reqs:
        ms, *_ = fetch(p); times[p].append(ms)
with open(out, "w") as f:
    f.write(f"# requests {len(reqs)} rounds {rounds} unstable {len(unstable)}\n")
    for i, (k, p) in enumerate(reqs):
        ts = sorted(times[p]); med = statistics.median(ts); p95 = ts[max(0, math.ceil(0.95 * len(ts)) - 1)]
        s, n, h, e = first[p]
        f.write(f"{i:03d}\t{k}\t{labels.get(p, p)}\t{s}\t{n}\t{h}\t{e}\t{med:.1f}\t{p95:.1f}\n")
print(f"{len(reqs)} requests, unstable {len(unstable)}: {[labels.get(u, u) for u in unstable[:5]]}")
