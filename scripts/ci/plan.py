#!/usr/bin/env python3
"""Deal every test suite into CI's shards (.github/workflows/ci.yml), each suite into exactly one.

    python3 scripts/ci/plan.py target/ci/suites target/ci/shards [--server-shards 3] [--weights project/test-weights.tsv]

Reads the suite lists `sbt ciSuites` writes (project/CiShard.scala: target/ci/suites/<project>.tsv, rows
`<project> TAB <suite>`) and writes one file per shard, `<out>/<shard>.tsv`, in the same shape — what
HISTMERSION_CI_SHARD_FILE narrows a root `testOnly *` to. Prints the GitHub matrix: a JSON list of
{"shard": name, "db": whether it needs PostgreSQL}.

The shards: `core` and `db` alone, the server's suites in `--server-shards` shards dealt longest first by
the weights (seconds per suite simple name, project/test-weights.tsv; unknown = 1 s, as build.sbt's forked
groups do), and every other project together in `pure`. A shard with no suite is left out.
"""
import argparse
import json
import os
import sys

DB_PROJECTS = {"db", "server"}
OWN_SHARD = ("core", "db")
SHARDED = "server"


def read_rows(lines):
    """[(project, suite)] from `<project> TAB <suite>` lines; blank and `#` lines skipped, others refused."""
    rows = []
    for n, line in enumerate(lines, 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        cells = line.split("\t")
        if len(cells) != 2 or not all(c.strip() for c in cells):
            raise ValueError(f"line {n}: not `<project> TAB <suite>`: {line!r}")
        rows.append((cells[0], cells[1]))
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


def plan(rows, server_shards, w):
    """{shard name: sorted [(project, suite)]}; every row in exactly one shard."""
    if len(set(rows)) != len(rows):
        dup = sorted({r for r in rows if rows.count(r) > 1})
        raise ValueError(f"a suite listed twice: {dup}")
    shards = {}
    for p in OWN_SHARD:
        shards[p] = sorted(r for r in rows if r[0] == p)
    simple = lambda r: w.get(r[1].rsplit(".", 1)[-1], 1.0)
    server = [r for r in rows if r[0] == SHARDED]
    for i, bucket in enumerate(deal(server, server_shards, simple), 1):
        shards[f"{SHARDED}-{i}"] = bucket
    shards["pure"] = sorted(r for r in rows if r[0] not in OWN_SHARD and r[0] != SHARDED)
    return {k: v for k, v in shards.items() if v}


def matrix(shards):
    """The GitHub matrix: one entry per shard, with whether it touches the database."""
    return [{"shard": k, "db": any(p in DB_PROJECTS for p, _ in v)} for k, v in shards.items()]


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("suites_dir")
    ap.add_argument("out_dir")
    ap.add_argument("--server-shards", type=int, default=3)
    ap.add_argument("--weights", default="project/test-weights.tsv")
    a = ap.parse_args(argv)
    rows = []
    for f in sorted(os.listdir(a.suites_dir)):
        if f.endswith(".tsv"):
            with open(os.path.join(a.suites_dir, f), encoding="utf-8") as fh:
                rows += read_rows(fh)
    if not rows:
        print(f"plan: no suites under {a.suites_dir} (run `sbt ciSuites` first)", file=sys.stderr)
        return 1
    w = {}
    if os.path.isfile(a.weights):
        with open(a.weights, encoding="utf-8") as fh:
            w = weights(fh)
    shards = plan(rows, a.server_shards, w)
    os.makedirs(a.out_dir, exist_ok=True)
    for name, rs in shards.items():
        with open(os.path.join(a.out_dir, f"{name}.tsv"), "w", encoding="utf-8") as fh:
            fh.writelines(f"{p}\t{s}\n" for p, s in rs)
        print(f"plan: {name}: {len(rs)} suite(s)", file=sys.stderr)
    print(json.dumps(matrix(shards)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
