#!/usr/bin/env python3
import re
import sys

LINE = re.compile(
    r"Test run com\.histmersion\.(?:db|server|util|domain)\.(?:[\w$]+\.)*([\w$]+) finished: .* total ([\d.]+)s"
)
ANSI = re.compile(r"\x1b\[[0-9;]*m")


def weights(lines):
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
