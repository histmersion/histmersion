#!/usr/bin/env python3
"""Classify every test suite as `unit` (never touches PostgreSQL) or `it` (may), from the test sources alone.

    python3 scripts/ci/classify.py target/ci/suites target/ci/sources [--out target/ci/classes.tsv]

A suite is `it` when its source file reaches the test database: it names a marker (`TestDb`,
`ScratchDatabases`, `HISTMERSION_TEST_DB_URL`, `HISTMERSION_TEST_DB_BASE_URL`), or names a top-level
class, trait, object or enum of a test file that does, transitively (a suite extending `DbSuite`, a contract
whose fixtures open `TestDb.url`, …). Everything else is `unit`. A name is matched as a whole word, so the scan
over-approximates: a unit suite misread as `it` only runs beside a database it does not need, while the
opposite cannot pass — CI runs the unit shards with no database URL and HISTMERSION_TEST_DB_REQUIRED=1, so a
unit suite that does reach `TestDb.url` fails loudly.

A suite whose source file cannot be found (its FQN matches no top-level definition under the project's test
source directories) is `unclassified`, and that fails: every suite must land in one class.

Inputs: the suite lists of `sbt ciSuites` (rows `<project> TAB <suite FQN>`) and the test source directories it
writes beside them (`target/ci/sources/<project>.tsv`, rows `<project> TAB <directory>`). Writes rows
`<project> TAB <suite> TAB <unit|it>` and prints the counts.
"""
import argparse
import os
import re
import sys
from collections import defaultdict

MARKERS = ("TestDb", "ScratchDatabases", "HISTMERSION_TEST_DB_URL", "HISTMERSION_TEST_DB_BASE_URL")
PACKAGE = re.compile(r"^package\s+([\w.]+)\s*$")
TOP = re.compile(
    r"^(?:(?:final|sealed|abstract|private(?:\[\w+\])?|protected(?:\[\w+\])?|case|open|transparent|implicit)\s+)*"
    r"(?:class|trait|object|enum)\s+(\w+)"
)
WORD = re.compile(r"\b[A-Za-z_]\w*\b")
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
STRING = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"')


def parse(text):
    pkg, names = [], []
    for line in text.splitlines():
        m = PACKAGE.match(line)
        if m:
            pkg.append(m.group(1))
            continue
        m = TOP.match(line)
        if m:
            names.append(m.group(1))
    code = COMMENT.sub(" ", text)
    markers = set(WORD.findall(code)) & set(MARKERS)
    used = set(WORD.findall(STRING.sub(" ", code))) - set(names)
    return ".".join(pkg), names, markers, used


def reaching(files):
    reached = {p for p, f in files.items() if f[2]}
    frontier = set(reached)
    while frontier:
        names = {n for p in frontier for n in files[p][1]}
        frontier = {p for p, f in files.items() if p not in reached and f[3] & names}
        reached |= frontier
    return reached


def classify(suites, sources, read):
    files, by_fqn = {}, {}
    for project, dirs in sources.items():
        for d in dirs:
            for path, text in read(d):
                if path in files:
                    continue
                files[path] = parse(text)
    for path, (pkg, names, _, _) in files.items():
        for n in names:
            by_fqn.setdefault(f"{pkg}.{n}" if pkg else n, path)
    reached = reaching(files)
    out = {}
    for project, suite in suites:
        path = by_fqn.get(suite)
        out[(project, suite)] = "unclassified" if path is None else ("it" if path in reached else "unit")
    return out


def read_dir(d):
    for root, _, names in os.walk(d):
        for n in sorted(names):
            if n.endswith(".scala"):
                p = os.path.join(root, n)
                with open(p, encoding="utf-8", errors="replace") as fh:
                    yield p, fh.read()


def read_rows(directory):
    rows = []
    if os.path.isdir(directory):
        for f in sorted(os.listdir(directory)):
            if f.endswith(".tsv"):
                with open(os.path.join(directory, f), encoding="utf-8") as fh:
                    rows += [tuple(l.rstrip("\n").split("\t")) for l in fh if l.strip() and not l.startswith("#")]
    return rows


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("suites_dir")
    ap.add_argument("sources_dir")
    ap.add_argument("--out")
    a = ap.parse_args(argv)
    suites = read_rows(a.suites_dir)
    sources = defaultdict(list)
    for project, d in read_rows(a.sources_dir):
        sources[project].append(d)
    classes = classify(suites, sources, read_dir)
    counts = defaultdict(int)
    for c in classes.values():
        counts[c] += 1
    if a.out:
        os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
        with open(a.out, "w", encoding="utf-8") as fh:
            fh.writelines(f"{p}\t{s}\t{c}\n" for (p, s), c in sorted(classes.items()))
    per = defaultdict(lambda: defaultdict(int))
    for (p, _), c in classes.items():
        per[p][c] += 1
    for p in sorted(per):
        print(f"classify: {p}: " + ", ".join(f"{k} {v}" for k, v in sorted(per[p].items())), file=sys.stderr)
    print(f"classify: {counts['unit']} unit, {counts['it']} it, {counts['unclassified']} unclassified", file=sys.stderr)
    bad = sorted(k for k, c in classes.items() if c == "unclassified")
    for p, s in bad:
        print(f"classify: UNCLASSIFIED {p} {s} (no top-level definition of it under {sources.get(p)})", file=sys.stderr)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
