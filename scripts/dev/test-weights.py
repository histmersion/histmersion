#!/usr/bin/env python3
"""Refresh project/test-weights.tsv — each DB-backed suite's seconds in a measured run — from an sbt log.

    just test-it 2>&1 | tee /tmp/it.log; python3 scripts/dev/test-weights.py /tmp/it.log

build.sbt (`dbTestGroups`) deals the suites of db and server into their forked groups longest first by
these weights, so the groups end together; a suite missing here weighs 1 s. Stale numbers only cost
balance, never correctness. Suites under 2 s are left out (they are the default, near enough).
"""
import re
import sys

# the db and server modules' suites: their packages since the move by feature (db, server, util, domain)
LINE = re.compile(
    r"Test run com\.histmersion\.(?:db|server|util|domain)\.(?:[\w$]+\.)*([\w$]+) finished: .* total ([\d.]+)s"
)
ANSI = re.compile(r"\x1b\[[0-9;]*m")


def weights(lines):
    """{suite simple name: seconds} for every suite at 2 s or more (the last report of a name wins)."""
    out = {}
    for l in lines:
        m = LINE.search(ANSI.sub("", l))
        if m and float(m.group(2)) >= 2.0:
            out[m.group(1)] = round(float(m.group(2)))
    return out


def render(w):
    head = "# suite<TAB>seconds — written by scripts/dev/test-weights.py from a `just test-it` log\n"
    return head + "".join(f"{k}\t{v}\n" for k, v in sorted(w.items(), key=lambda kv: (-kv[1], kv[0])))


if __name__ == "__main__":
    w = weights(open(sys.argv[1], errors="replace"))
    out = sys.argv[2] if len(sys.argv) > 2 else "project/test-weights.tsv"
    open(out, "w").write(render(w))
    print(f"{len(w)} suite weights → {out}")
