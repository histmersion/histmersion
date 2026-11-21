#!/usr/bin/env python3
"""Hold CI's shards to the suites: each suite the build defines is classed, and ran in exactly one shard of its class.

    python3 scripts/ci/check.py target/ci/classes.tsv <reports dir> [--summary FILE]

`classes.tsv` is what scripts/ci/classify.py wrote (rows `<project> TAB <suite> TAB <unit|it|unclassified>`);
`<reports dir>` holds one directory per shard (`<shard>` or `report-<shard>`, as the artifacts download), each with
the rows HISTMERSION_CI_REPORT_DIR collected (project/CiShard.scala): `<project>.tsv` — `<project> TAB <suite> TAB
<result> TAB passed TAB failed TAB errors TAB skipped TAB ignored [TAB millis]` — and `<project>.skips.tsv` —
`<project> TAB <suite> TAB <test> TAB <reason>`.

Fails (exit 1) when a suite is unclassified, ran in no shard, ran more than once (in one shard or across two), ran
in a shard of the other class (`unit-*` against `it-*`), or a shard ran a suite nobody listed. Prints, and appends to
`--summary` (GitHub's step summary) as Markdown, the classes, the suites and seconds per shard, the failed suites and
every skipped test with its reason.
"""
import argparse
import os
import sys
from collections import defaultdict

KEYS = ("passed", "failed", "errors", "skipped", "ignored")


def read_classes(lines):
    out = {}
    for line in lines:
        line = line.rstrip("\n")
        if line.strip() and not line.startswith("#"):
            p, s, c = line.split("\t")
            out[(p, s)] = c
    return out


def read_report(lines):
    out = []
    for n, line in enumerate(lines, 1):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        cells = line.split("\t")
        if len(cells) not in (8, 9):
            raise ValueError(f"line {n}: not a report row of 8 or 9 cells: {line!r}")
        p, s, result, *nums = cells
        nums = list(map(int, nums))
        out.append((p, s, result, dict(zip(KEYS, nums[:5])), nums[5] if len(nums) > 5 else 0))
    return out


def shard_class(shard):
    head = shard.split("-", 1)[0]
    return head if head in ("unit", "it") else None


def compare(classes, ran):
    where = defaultdict(list)
    for shard, suites in sorted(ran.items()):
        for s in suites:
            where[s].append(shard)
    unclassified = sorted(k for k, c in classes.items() if c not in ("unit", "it"))
    missing = sorted(k for k in classes if k not in where and k not in unclassified)
    repeated = {s: shards for s, shards in sorted(where.items()) if len(shards) > 1}
    unexpected = sorted(where.keys() - classes.keys())
    misplaced = {
        s: sh
        for s, shards in sorted(where.items())
        for sh in shards
        if s in classes and shard_class(sh) is not None and shard_class(sh) != classes[s]
    }
    return unclassified, missing, repeated, unexpected, misplaced


def load(classes_file, reports_dir):
    with open(classes_file, encoding="utf-8") as fh:
        classes = read_classes(fh)
    ran, results, skips = {}, [], []
    for d in sorted(os.listdir(reports_dir)):
        path = os.path.join(reports_dir, d)
        if not os.path.isdir(path):
            continue
        shard = d[len("report-"):] if d.startswith("report-") else d
        ran[shard] = []
        for f in sorted(os.listdir(path)):
            with open(os.path.join(path, f), encoding="utf-8") as fh:
                if f.endswith(".skips.tsv"):
                    skips += [(shard, *(l.rstrip("\n").split("\t", 3) + ["", "", "", ""])[:4]) for l in fh if l.strip()]
                elif f.endswith(".tsv"):
                    for p, s, result, counts, millis in read_report(fh):
                        ran[shard].append((p, s))
                        results.append((shard, p, s, result, counts, millis))
    return classes, ran, results, skips


def render(classes, ran, results, skips, verdict):
    unclassified, missing, repeated, unexpected, misplaced = verdict
    by_class = defaultdict(int)
    for c in classes.values():
        by_class[c] += 1
    lines = ["## Test shards", "",
             "Classes: " + ", ".join(f"{k} {v}" for k, v in sorted(by_class.items())), "",
             "| shard | suites | tests | test seconds | failed suites | skipped tests |", "|---|---|---|---|---|---|"]
    for shard in sorted(ran):
        rs = [r for r in results if r[0] == shard]
        tests = sum(sum(r[4].values()) for r in rs)
        secs = sum(r[5] for r in rs) / 1000
        bad = sum(1 for r in rs if r[3] not in ("passed", "empty"))
        sk = sum(1 for s in skips if s[0] == shard)
        lines.append(f"| {shard} | {len(rs)} | {tests} | {secs:.0f} | {bad} | {sk} |")
    lines.append("")
    lines.append(f"{len(classes)} suites listed: {len(unclassified)} unclassified, {len(missing)} not run, "
                 f"{len(repeated)} run more than once, {len(misplaced)} in a shard of the other class, "
                 f"{len(unexpected)} run but not listed.")
    for title, items in (("Unclassified", unclassified), ("Not run", missing), ("Not listed", unexpected)):
        if items:
            lines += ["", f"### {title}", ""] + [f"- `{p}` `{s}`" for p, s in items]
    if repeated:
        lines += ["", "### Run more than once", ""] + [f"- `{p}` `{s}`: {', '.join(sh)}" for (p, s), sh in repeated.items()]
    if misplaced:
        lines += ["", "### In a shard of the other class", ""] + [
            f"- `{p}` `{s}` ({classes[(p, s)]}) in {sh}" for (p, s), sh in misplaced.items()]
    failed = [r for r in results if r[3] not in ("passed", "empty")]
    if failed:
        lines += ["", "### Failed suites", ""] + [f"- {r[0]}: `{r[1]}` `{r[2]}` ({r[3]})" for r in failed]
    if skips:
        lines += ["", "### Skipped tests", "", "| shard | suite | test | reason |", "|---|---|---|---|"]
        lines += [f"| {sh} | `{s.rsplit('.', 1)[-1]}` | {t} | {why} |" for sh, _p, s, t, why in skips]
    return "\n".join(lines) + "\n"


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("classes")
    ap.add_argument("reports_dir")
    ap.add_argument("--summary")
    a = ap.parse_args(argv)
    classes, ran, results, skips = load(a.classes, a.reports_dir)
    if not classes:
        print(f"check: no suites listed in {a.classes}", file=sys.stderr)
        return 1
    verdict = compare(classes, ran)
    text = render(classes, ran, results, skips, verdict)
    print(text)
    if a.summary:
        with open(a.summary, "a", encoding="utf-8") as fh:
            fh.write(text)
    return 1 if any(verdict) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
