#!/usr/bin/env python3
"""Deal every test suite into CI's shards (.github/workflows/ci.yml), each suite into exactly one.

    python3 scripts/ci/plan.py target/ci/classes.tsv target/ci/shards \
        [--unit-server-shards 2] [--it-server-shards 3] [--weights project/test-weights.tsv]

Reads the classes scripts/ci/classify.py wrote (rows `<project> TAB <suite> TAB <unit|it>`) and writes one file per
shard, `<out>/<shard>.tsv` (rows `<project> TAB <suite>`) — what HISTMERSION_CI_SHARD_FILE narrows a `testOnly *`
to. Prints the GitHub matrix: a JSON list of {"shard": name, "db": whether it starts PostgreSQL}.

Unit shards never start PostgreSQL; IT shards each start their own:

    unit-modules        the unit suites of every project but db and server (all of theirs: they cannot reach TestDb)
    unit-db             db's unit suites
    unit-server-<n>     server's unit suites, dealt longest first by the weights
    it-db               db's IT suites
    it-server-<n>       server's IT suites, dealt longest first by the weights

The weights are seconds per suite simple name (project/test-weights.tsv; unknown = 1 s, as build.sbt's forked groups
do). A shard with no suite is left out. A suite of any other class (`unclassified`) is dealt nowhere and named on
stderr; scripts/ci/check.py fails the run on it.
"""
import argparse
import json
import os
import sys

FORKED = ("db", "server")
CLASSES = ("unit", "it")


def read_classes(lines):
    """[(project, suite, class)] from `<project> TAB <suite> TAB <class>` lines; malformed lines are refused."""
    rows = []
    for n, line in enumerate(lines, 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        cells = line.split("\t")
        if len(cells) != 3 or not all(c.strip() for c in cells):
            raise ValueError(f"line {n}: not `<project> TAB <suite> TAB <class>`: {line!r}")
        rows.append(tuple(cells))
    return rows


def weights(lines):
    """{suite simple name: seconds} from project/test-weights.tsv (bad lines skipped, as TestGroups.weights)."""
    out = {}
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cells = line.split()
        if len(cells) == 2:
            try:
                secs = float(cells[1])
            except ValueError:
                continue
            if secs > 0:
                out[cells[0]] = secs
    return out


def deal(items, n, weight):
    """`items` into at most `n` non-empty buckets: heaviest first, each to the lightest bucket so far (ties: the
    lower index), names breaking ties — the same rule as project/TestGroups.split, stable for a given input."""
    k = max(1, min(n, len(items)))
    buckets = [[] for _ in range(k)]
    load = [0.0] * k
    for item in sorted(items, key=lambda i: (-weight(i), i)):
        b = min(range(k), key=lambda j: (load[j], j))
        buckets[b].append(item)
        load[b] += weight(item)
    return [sorted(b) for b in buckets if b]


def plan(rows, unit_server, it_server, w):
    """{shard name: sorted [(project, suite)]}; every classed row in exactly one shard, a unit suite in a unit shard.
    A suite of no class (`unclassified`) is in none: scripts/ci/check.py fails the run on it, after the shards have
    run the rest."""
    pairs = [(p, s) for p, s, _ in rows]
    if len(set(pairs)) != len(pairs):
        raise ValueError(f"a suite listed twice: {sorted({r for r in pairs if pairs.count(r) > 1})}")
    stray = sorted((p, s) for p, s, c in rows if c == "it" and p not in FORKED)
    if stray:
        # only db and server see TestDb (server through db's tests): an IT suite elsewhere is a misreading
        raise ValueError(f"IT suites outside db and server: {stray}")

    def simple(r):
        return w.get(r[1].rsplit(".", 1)[-1], 1.0)

    def of(cls, pred):
        return sorted((p, s) for p, s, c in rows if c == cls and pred(p))

    shards = {"unit-modules": of("unit", lambda p: p not in FORKED), "unit-db": of("unit", lambda p: p == "db")}
    for i, b in enumerate(deal(of("unit", lambda p: p == "server"), unit_server, simple), 1):
        shards[f"unit-server-{i}"] = b
    shards["it-db"] = of("it", lambda p: p == "db")
    for i, b in enumerate(deal(of("it", lambda p: p == "server"), it_server, simple), 1):
        shards[f"it-server-{i}"] = b
    return {k: v for k, v in shards.items() if v}


def matrix(shards):
    """The GitHub matrix: one entry per shard; only the IT shards start PostgreSQL."""
    return [{"shard": k, "db": k.startswith("it-")} for k in shards]


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("classes")
    ap.add_argument("out_dir")
    ap.add_argument("--unit-server-shards", type=int, default=2)
    ap.add_argument("--it-server-shards", type=int, default=3)
    ap.add_argument("--weights", default="project/test-weights.tsv")
    a = ap.parse_args(argv)
    with open(a.classes, encoding="utf-8") as fh:
        rows = read_classes(fh)
    if not rows:
        print(f"plan: no suites in {a.classes} (run `sbt ciSuites` and scripts/ci/classify.py first)", file=sys.stderr)
        return 1
    w = {}
    if os.path.isfile(a.weights):
        with open(a.weights, encoding="utf-8") as fh:
            w = weights(fh)
    for p, s, c in rows:
        if c not in CLASSES:
            print(f"plan: {p} {s} is {c}: in no shard (the shards check fails on it)", file=sys.stderr)
    try:
        shards = plan(rows, a.unit_server_shards, a.it_server_shards, w)
    except ValueError as e:
        print(f"plan: {e}", file=sys.stderr)
        return 1
    os.makedirs(a.out_dir, exist_ok=True)
    for name, rs in shards.items():
        with open(os.path.join(a.out_dir, f"{name}.tsv"), "w", encoding="utf-8") as fh:
            fh.writelines(f"{p}\t{s}\n" for p, s in rs)
        print(f"plan: {name}: {len(rs)} suite(s)", file=sys.stderr)
    print(json.dumps(matrix(shards)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
