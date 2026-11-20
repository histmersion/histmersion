#!/usr/bin/env python3
"""Hold CI's shards to the suites: each suite the build defines ran in exactly one shard.

    python3 scripts/ci/check.py target/ci/suites <reports dir> [--summary FILE]

`target/ci/suites` holds what `sbt ciSuites` listed (rows `<project> TAB <suite>`); `<reports dir>` holds one
directory per shard, each with the rows HISTMERSION_CI_REPORT_DIR collected (project/CiShard.scala):
`<project>.tsv` — `<project> TAB <suite> TAB <result> TAB passed TAB failed TAB errors TAB skipped TAB ignored`
— and `<project>.skips.tsv` — `<project> TAB <suite> TAB <test> TAB <reason>`.

Fails (exit 1) when a listed suite ran in no shard, ran more than once (in one shard or across two), or a
shard ran a suite nobody listed. Prints, and appends to `--summary` (GitHub's step summary) as Markdown, the
suites per shard, the failed ones and every skipped test with its reason.
"""
import argparse
import os
import sys
from collections import defaultdict


def read_suites(lines):
    out = []
    for line in lines:
        line = line.rstrip("\n")
        if line.strip() and not line.startswith("#"):
            p, s = line.split("\t")
            out.append((p, s))
    return out


def read_report(lines):
    """[(project, suite, result, counts dict)] from a shard's `<project>.tsv` rows."""
    out = []
    for n, line in enumerate(lines, 1):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        cells = line.split("\t")
        if len(cells) != 8:
            raise ValueError(f"line {n}: not a report row of 8 cells: {line!r}")
        p, s, result, *nums = cells
        keys = ("passed", "failed", "errors", "skipped", "ignored")
        out.append((p, s, result, dict(zip(keys, map(int, nums)))))
    return out


def compare(expected, ran):
    """`expected`: [(project, suite)]; `ran`: {shard: [(project, suite)]}. Returns (missing, repeated, unexpected):
    the expected suites no shard ran, {suite: [shard, ...]} for each run more than once, and the suites run that
    nobody listed."""
    where = defaultdict(list)
    for shard, suites in sorted(ran.items()):
        for s in suites:
            where[s].append(shard)
    exp = set(expected)
    missing = sorted(exp - where.keys())
    repeated = {s: shards for s, shards in sorted(where.items()) if len(shards) > 1}
    unexpected = sorted(where.keys() - exp)
    return missing, repeated, unexpected


def load(suites_dir, reports_dir):
    expected = []
    for f in sorted(os.listdir(suites_dir)):
        if f.endswith(".tsv"):
            with open(os.path.join(suites_dir, f), encoding="utf-8") as fh:
                expected += read_suites(fh)
    ran, results, skips = {}, [], []
    for shard in sorted(os.listdir(reports_dir)):
        d = os.path.join(reports_dir, shard)
        if not os.path.isdir(d):
            continue
        ran[shard] = []
        for f in sorted(os.listdir(d)):
            path = os.path.join(d, f)
            with open(path, encoding="utf-8") as fh:
                if f.endswith(".skips.tsv"):
                    skips += [(shard, *l.rstrip("\n").split("\t", 3)) for l in fh if l.strip()]
                elif f.endswith(".tsv"):
                    for p, s, result, counts in read_report(fh):
                        ran[shard].append((p, s))
                        results.append((shard, p, s, result, counts))
    return expected, ran, results, skips


def render(expected, ran, results, skips, missing, repeated, unexpected):
    lines = ["## Test shards", "", "| shard | suites | tests | failed suites | skipped tests |", "|---|---|---|---|---|"]
    for shard in sorted(ran):
        rs = [r for r in results if r[0] == shard]
        tests = sum(sum(c.values()) for *_, c in rs)
        bad = sum(1 for r in rs if r[3] not in ("passed", "empty"))
        sk = sum(1 for s in skips if s[0] == shard)
        lines.append(f"| {shard} | {len(rs)} | {tests} | {bad} | {sk} |")
    lines.append("")
    lines.append(f"{len(expected)} suites listed; {len(missing)} not run, {len(repeated)} run more than once, "
                 f"{len(unexpected)} run but not listed.")
    for title, items in (("Not run", missing), ("Not listed", unexpected)):
        if items:
            lines += ["", f"### {title}", ""] + [f"- `{p}` `{s}`" for p, s in items]
    if repeated:
        lines += ["", "### Run more than once", ""] + [f"- `{p}` `{s}`: {', '.join(sh)}" for (p, s), sh in repeated.items()]
    failed = [r for r in results if r[3] not in ("passed", "empty")]
    if failed:
        lines += ["", "### Failed suites", ""] + [f"- {sh}: `{p}` `{s}` ({res})" for sh, p, s, res, _ in failed]
    if skips:
        lines += ["", "### Skipped tests", "", "| shard | suite | test | reason |", "|---|---|---|---|"]
        lines += [f"| {sh} | `{s.rsplit('.', 1)[-1]}` | {t} | {why} |" for sh, _p, s, t, why in
                  [(x + ("", "", "", ""))[:5] for x in skips]]
    return "\n".join(lines) + "\n"


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("suites_dir")
    ap.add_argument("reports_dir")
    ap.add_argument("--summary")
    a = ap.parse_args(argv)
    expected, ran, results, skips = load(a.suites_dir, a.reports_dir)
    if not expected:
        print(f"check: no suites listed under {a.suites_dir}", file=sys.stderr)
        return 1
    missing, repeated, unexpected = compare(expected, ran)
    text = render(expected, ran, results, skips, missing, repeated, unexpected)
    print(text)
    if a.summary:
        with open(a.summary, "a", encoding="utf-8") as fh:
            fh.write(text)
    return 1 if missing or repeated or unexpected else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
