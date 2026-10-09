#!/usr/bin/env python3
"""Interactive-latency benchmark: replays the requests the web UI fires on hover, typing, filtering,
timeline drags and playback against a running server, and reports p50/p95/max per endpoint and per
interaction, plus payload bytes.

    scripts/perf/bench.py --url http://127.0.0.1:18681                # report
    scripts/perf/bench.py --url … --json after.json                    # also save the samples
    scripts/perf/bench.py --compare before.json after.json             # the before/after table
    scripts/perf/bench.py --url … --check --slack 3                    # exit 1 when a budget is blown

Everything it asks for is DRAWN FROM THE DATA through the API itself (the facets, the map's polity
layer, the sources and articles lists), so it runs against any corpus — the live-sized copy or the
test database — without a database connection. Read-only: it never writes.

What an interaction fires, in what order, is the web client's (docs/research-interactive-performance.md
has the inventory): a filter change runs `findings` and then `sources` (sequential) beside the two
timeline aggregates and the visible tab; a place hover is one `places/{id}?counts=true`; and so on.
The interaction's time is the wall time of that bundle, the endpoint's the time to the last byte.

Never point it at the live server while someone works with it: a full run is a few thousand requests.
"""
import argparse
import concurrent.futures as cf
import http.client
import json
import math
import os
import random
import sys
import threading
import time
import urllib.parse

# ── budgets (ms, p95) — docs/research-interactive-performance.md §Budgets ───────────────────────────
BUDGETS = {
    "hover.place": 50, "hover.source": 50, "hover.article": 50,
    "suggest.place": 50, "suggest.tag": 50, "suggest.author": 50, "suggest.topic": 50,
    "suggest.hover": 150, "filter.change": 150, "place.chosen": 150, "map.year": 150,
    "timeline.drag": 150, "playback.frame": 150,
}


# ── pure helpers (unit-tested) ──────────────────────────────────────────────────────────────────

def percentile(xs, p):
    """Nearest-rank percentile of `xs` (0 < p <= 100); None for no samples."""
    if not xs:
        return None
    s = sorted(xs)
    k = max(1, math.ceil(p / 100.0 * len(s)))
    return s[k - 1]


def summarise(samples):
    """{key: [(ms, bytes)…]} → {key: {n, p50, p95, max, bytes}} (bytes = the median payload)."""
    out = {}
    for k, xs in samples.items():
        ms = [x[0] for x in xs]
        bs = [x[1] for x in xs]
        out[k] = {"n": len(xs), "p50": percentile(ms, 50), "p95": percentile(ms, 95), "max": max(ms) if ms else None,
                  "bytes": percentile(bs, 50)}
    return out


def prefixes(word, longest=4):
    """What typing `word` sends, one keystroke at a time: its first 1…`longest` characters."""
    w = (word or "").strip()
    return [w[:i] for i in range(1, min(longest, len(w)) + 1)]


def query(params):
    """URL query string from a list of (key, value) pairs; None values are dropped, lists repeat."""
    flat = []
    for k, v in params:
        if v is None:
            continue
        for x in (v if isinstance(v, list) else [v]):
            flat.append((k, str(x)))
    return urllib.parse.urlencode(flat)


def bucket_size(span_years, pixels=1200, max_buckets=1500):
    """TimelineView.bucketSizeFor: the bucket the strip asks for over `span_years` at `pixels` wide."""
    by_pixels = math.ceil(span_years / max(pixels, 100))
    by_limit = math.ceil(span_years / max_buckets)
    return max(1, by_pixels, by_limit)


def over_budget(summary, budgets, slack=1.0):
    """[(interaction, p95, budget)] for every interaction whose p95 exceeds budget × slack."""
    bad = []
    for k, b in budgets.items():
        s = summary.get(k)
        if s and s["p95"] is not None and s["p95"] > b * slack:
            bad.append((k, s["p95"], b))
    return bad


def fmt_ms(v):
    return "–" if v is None else (f"{v:.0f}" if v >= 10 else f"{v:.1f}")


def fmt_bytes(v):
    if v is None:
        return "–"
    return f"{v / 1024:.0f} KB" if v >= 10240 else f"{v} B"


def table(summary, title):
    rows = [f"| {title} | n | p50 ms | p95 ms | max ms | payload |", "|---|---:|---:|---:|---:|---:|"]
    for k in sorted(summary):
        s = summary[k]
        rows.append(f"| `{k}` | {s['n']} | {fmt_ms(s['p50'])} | {fmt_ms(s['p95'])} | {fmt_ms(s['max'])} | {fmt_bytes(s['bytes'])} |")
    return "\n".join(rows)


def compare(before, after, title):
    keys = sorted(set(before) | set(after))
    rows = [f"| {title} | before p50 | before p95 | after p50 | after p95 | p95 × |", "|---|---:|---:|---:|---:|---:|"]
    for k in keys:
        b, a = before.get(k), after.get(k)
        bp50, bp95 = (b or {}).get("p50"), (b or {}).get("p95")
        ap50, ap95 = (a or {}).get("p50"), (a or {}).get("p95")
        x = f"{bp95 / ap95:.0f}" if bp95 and ap95 and ap95 > 0 else "–"
        rows.append(f"| `{k}` | {fmt_ms(bp50)} | {fmt_ms(bp95)} | {fmt_ms(ap50)} | {fmt_ms(ap95)} | {x} |")
    return "\n".join(rows)


# ── HTTP: one keep-alive connection per thread, like a browser's pool ──────────────────────────────

class Client:
    def __init__(self, base, timeout):
        u = urllib.parse.urlparse(base)
        self.host, self.port, self.timeout = u.hostname, u.port or 80, timeout
        self.local = threading.local()
        self.lock = threading.Lock()
        self.endpoints = {}   # endpoint key → [(ms, bytes)]
        self.errors = []

    def _conn(self):
        c = getattr(self.local, "c", None)
        if c is None:
            c = http.client.HTTPConnection(self.host, self.port, timeout=self.timeout)
            self.local.c = c
        return c

    def get(self, key, path):
        """GET `path`; records (ms, bytes) under endpoint `key`; returns (status, body bytes)."""
        t0 = time.perf_counter()
        for attempt in (1, 2):
            try:
                c = self._conn()
                c.request("GET", path, headers={"Accept-Encoding": os.environ.get("BENCH_ACCEPT_ENCODING", "identity")})
                r = c.getresponse()
                body = r.read()
                status = r.status
                break
            except (http.client.HTTPException, ConnectionError, TimeoutError, OSError) as e:
                self.local.c = None
                if attempt == 2:
                    ms = (time.perf_counter() - t0) * 1000
                    with self.lock:
                        self.errors.append((key, path, repr(e)))
                        self.endpoints.setdefault(key, []).append((ms, 0))
                    return 0, b""
        ms = (time.perf_counter() - t0) * 1000
        with self.lock:
            self.endpoints.setdefault(key, []).append((ms, len(body)))
            if status >= 500:
                self.errors.append((key, path, status))
        return status, body

    def json(self, key, path):
        s, b = self.get(key, path)
        try:
            return json.loads(b) if s == 200 else None
        except ValueError:
            return None


# ── what the corpus offers to hover, type and pick ───────────────────────────────────────────────

def discover(c, rng):
    cfg = c.json("config", "/api/config") or {}
    t = cfg.get("time", {})
    lo, hi = t.get("defaultFrom", -3000), t.get("defaultTo", -1000)
    tmin, tmax = t.get("min", -10000), t.get("max", 2000)
    mid = (lo + hi) // 2
    span = [("from", lo), ("to", hi)]
    places = [f for f in (c.json("discover", "/api/facets?" + query([("field", "places"), ("limit", 40)])) or []) if f.get("id")]
    years = sorted({lo, mid, hi, -2500, -1200, -500, 100, 1200})
    polities = []
    for y in years:
        g = c.json("discover", "/api/places/geo?" + query([("kind", "polity"), ("asOf", y), ("limit", 400)])) or {}
        for f in g.get("features", []):
            polities.append((f["properties"]["id"], y))
    rng.shuffle(polities)
    srcs = (c.json("discover", "/api/sources?" + query(span + [("limit", 60)])) or {}).get("hits", [])
    arts = (c.json("discover", "/api/articles?" + query([("limit", 60)])) or {}).get("articles", [])
    vocab = {}
    for field in ("tags", "authors", "topics"):
        vocab[field] = [f["value"] for f in (c.json("discover", "/api/facets?" + query([("field", field), ("limit", 30)])) or [])]
    place_words = [p["value"] for p in places][:12] + ["troy", "ur", "egypt", "hatti", "crete", "babylon"]
    return {
        "span": (lo, hi), "limits": (tmin, tmax), "mid": mid, "page": cfg.get("pageSize", 20),
        "suggest": cfg.get("facetSuggestions", 12),
        "place_ids": [p["id"] for p in places],
        "polities": polities,
        "citekeys": [h["source"]["citekey"] for h in srcs],
        "slugs": [a["slug"] for a in arts],
        "vocab": vocab, "place_words": place_words,
    }


# ── the interactions ─────────────────────────────────────────────────────────────────────────────

class Bench:
    def __init__(self, c, d, rng, n, pool, skip=(), sequential=False):
        self.c, self.d, self.rng, self.n, self.pool, self.skip = c, d, rng, n, pool, set(skip)
        self.sequential = sequential
        self.interactions = {}

    def on(self, key):
        """Is interaction `key` part of this run (`--skip` drops whole groups, e.g. `map` or `map.year`)?"""
        return not any(key == s or key.startswith(s + ".") for s in self.skip)

    def record(self, key, ms):
        self.interactions.setdefault(key, []).append((ms, 0))

    def timed(self, key, fn):
        if not self.on(key):
            return
        t0 = time.perf_counter()
        fn()
        self.record(key, (time.perf_counter() - t0) * 1000)

    def par(self, *fns):
        """Fire the callables at once (the browser's parallel requests) and wait for all."""
        fs = [self.pool.submit(f) for f in fns]
        for f in fs:
            f.result()

    def pick(self, xs, k):
        xs = list(xs)
        return xs if len(xs) <= k else self.rng.sample(xs, k)

    # the bundle a committed filter change fires (App.load + TimelineView + the visible tab)
    def filter_bundle(self, params, tab="sources"):
        c, d = self.c, self.d
        lo, hi = dict(params).get("from", d["span"][0]), dict(params).get("to", d["span"][1])
        tmin, tmax = d["limits"]
        no_period = [(k, v) for k, v in params if k not in ("from", "to")]

        def findings():
            c.get("findings", "/api/findings?" + query(params))

        def sources():
            c.get("sources", "/api/sources?" + query(params + [("limit", d["page"])]))

        def lists():   # App.load before 2026-10-09: findings, THEN sources (--sequential-lists)
            findings()
            sources()

        def detail():
            c.get("aggregate.detail", "/api/aggregate?" + query(
                [("by", "time"), ("unit", "sources")] + params + [("size", bucket_size(hi - lo))]))

        def overview():
            c.get("aggregate.overview", "/api/aggregate?" + query(
                [("by", "time"), ("unit", "sources")] + no_period + [("from", tmin), ("to", tmax),
                                                                     ("size", bucket_size(tmax - tmin))]))

        fns = ([lists] if self.sequential else [findings, sources]) + [detail, overview]
        if tab == "articles":
            fns.append(lambda: c.get("articles", "/api/articles?" + query(params + [("limit", 200)])))
        if tab == "events":
            fns.append(lambda: c.get("events", "/api/events?" + query(params + [("limit", 200)])))
        self.par(*fns)

    def run(self):
        c, d, n = self.c, self.d, self.n
        lo, hi = d["span"]
        span = [("from", lo), ("to", hi)]

        # hover: a place on the map (its card with counts), a source link, an article link
        for pid, y in self.pick(d["polities"], n) + [(p, d["mid"]) for p in self.pick(d["place_ids"], n)]:
            self.timed("hover.place", lambda: c.get("places.detail+counts",
                                                    f"/api/places/{urllib.parse.quote(pid)}?" + query([("asOf", y), ("counts", "true")])))
        for ck in self.pick(d["citekeys"], n):
            self.timed("hover.source", lambda: c.get("sources.one", f"/api/sources/{urllib.parse.quote(ck)}?body=false"))
        for slug in self.pick(d["slugs"], n):
            self.timed("hover.article", lambda: c.get("articles.one", f"/api/articles/{urllib.parse.quote(slug)}?brief=true"))

        # typing: one request per keystroke prefix (the client debounces, so a fast typist sends fewer)
        for w in self.pick(d["place_words"], n):
            for p in prefixes(w):
                self.timed("suggest.place", lambda: c.get("places.search", "/api/places?" + query(
                    [("q", p), ("asOf", d["mid"]), ("limit", d["suggest"])])))
        for field, key in (("tags", "suggest.tag"), ("authors", "suggest.author"), ("topics", "suggest.topic")):
            for w in self.pick(d["vocab"][field], max(2, n // 3)):
                for p in prefixes(w.replace("_", " "), 3):
                    self.timed(key, lambda: c.get("facets", "/api/facets?" + query([("field", field), ("prefix", p)] + span)))

        # hovering a suggestion previews it: the whole filter bundle with the value added
        for field, single in (("tags", "tag"), ("topics", "topic"), ("authors", "author")):
            for v in self.pick(d["vocab"][field], max(2, n // 3)):
                self.timed("suggest.hover", lambda: self.filter_bundle(span + [(single, v)]))

        # a committed filter change with each tab visible
        for tab in ("sources", "articles", "events"):
            for v in self.pick(d["vocab"]["tags"], max(2, n // 3)):
                self.timed("filter.change", lambda: self.filter_bundle(span + [("tag", v)], tab))

        # a place chosen (map click or picker): the bundle with place=<id>, plus its outline
        for pid, y in self.pick(d["polities"], n // 2) + [(p, d["mid"]) for p in self.pick(d["place_ids"], n // 2)]:
            def chosen():
                self.par(lambda: self.filter_bundle(span + [("place", pid)], "articles"),
                         lambda: c.get("places.detail", f"/api/places/{urllib.parse.quote(pid)}?" + query([("asOf", y)])))
            self.timed("place.chosen", chosen)

        # the map at a new year: the overlays the layers ask for
        tmin, tmax = d["limits"]
        for y in self.pick(range(-3000, 1500, 50), n):
            self.timed("map.year", lambda: self.par(
                lambda: c.get("places.geo.polity", "/api/places/geo?" + query([("kind", "polity"), ("asOf", y), ("limit", 400)])),
                lambda: c.get("places.geo.site", "/api/places/geo?" + query([("kind", "site"), ("asOf", y), ("limit", 200)])),
                lambda: c.get("media.geo", "/api/media-geo?" + query([("from", y - 250), ("to", y + 250), ("limit", 200)]))))

        # a timeline drag: the detail strip refetches as the span moves (newest wins)
        w = hi - lo
        for step in range(n):
            a = lo + step * max(1, w // (n * 2))
            self.timed("timeline.drag", lambda: c.get("aggregate.detail", "/api/aggregate?" + query(
                [("by", "time"), ("unit", "sources"), ("from", a), ("to", a + w), ("size", bucket_size(w))])))

        # playback: one findings request per frame at a sliding ±50-year window
        for step in range(n):
            ph = lo + step * max(1, w // n)
            self.timed("playback.frame", lambda: c.get("findings", "/api/findings?" + query([("from", ph - 50), ("to", ph + 50)])))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--url", default=os.environ.get("HISTMERSION_PUBLIC_URL"), help="the server (never the live one in use)")
    ap.add_argument("-n", type=int, default=12, help="samples per interaction kind (default 12)")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--timeout", type=float, default=120.0)
    ap.add_argument("--json", help="write the raw samples and the summaries here")
    ap.add_argument("--compare", nargs=2, metavar=("BEFORE", "AFTER"), help="print the before/after tables of two --json files")
    ap.add_argument("--check", action="store_true", help="exit 1 when an interaction's p95 exceeds its budget × --slack")
    ap.add_argument("--slack", type=float, default=1.0)
    ap.add_argument("--sequential-lists", action="store_true",
                    help="model the client before 2026-10-09: sources asked for only after the findings arrived")
    ap.add_argument("--skip", action="append", default=[], help="leave out an interaction (or group: `map`); repeatable")
    a = ap.parse_args(argv)

    if a.compare:
        b, f = (json.load(open(p)) for p in a.compare)
        print(compare(b["interactions"], f["interactions"], "interaction"))
        print()
        print(compare(b["endpoints"], f["endpoints"], "endpoint"))
        return 0
    if not a.url:
        ap.error("--url (or HISTMERSION_PUBLIC_URL) is required")

    rng = random.Random(a.seed)
    c = Client(a.url, a.timeout)
    d = discover(c, rng)
    c.endpoints.pop("discover", None)
    with cf.ThreadPoolExecutor(max_workers=6) as pool:
        b = Bench(c, d, rng, a.n, pool, a.skip, a.sequential_lists)
        b.run()
    inter, ends = summarise(b.interactions), summarise(c.endpoints)
    print(table(inter, "interaction"))
    print()
    print(table(ends, "endpoint"))
    for e in c.errors[:20]:
        print("error:", e, file=sys.stderr)
    if a.json:
        with open(a.json, "w") as fh:
            json.dump({"url": a.url, "n": a.n, "interactions": inter, "endpoints": ends,
                       "samples": {"interactions": b.interactions, "endpoints": c.endpoints},
                       "errors": c.errors}, fh, indent=1, default=str)
    if a.check:
        bad = over_budget(inter, BUDGETS, a.slack)
        for k, p95, budget in bad:
            print(f"over budget: {k} p95 {p95:.0f} ms > {budget} ms × {a.slack}", file=sys.stderr)
        return 1 if bad or c.errors else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
