import re
import sys
from collections import defaultdict

DEPENDS = {
    "problem": ["newtype"], "values": ["newtype", "problem"], "api": ["newtype", "problem", "json"],
    "config": ["values"], "files": ["values", "api"], "core": ["values", "api", "files"],
    "plugin-api": ["newtype", "problem", "json"], "upstreams": ["plugin-api"],
    "plugin-host": ["core", "config", "plugin-api", "upstreams"], "plugin-vaults": ["plugin-api"],
    "plugins": ["upstreams", "plugin-api"], "db": ["config", "core"],
    "server": ["core", "db", "plugin-api", "upstreams", "plugin-host", "config", "files"],
    "cli": ["api", "config", "files", "console"], "web": ["api"], "console": ["newtype"],
    "plugin-resolver": ["console", "problem"],
}


EXCLUDED_NAMES = {"Wire"}


def module(path):
    parts = path.split("/")
    if len(parts) > 2 and parts[0] == "modules":
        return "plugins" if parts[1] == "plugins" else parts[1]
    return path


def closure(m, seen=None):
    seen = set() if seen is None else seen
    for d in DEPENDS.get(m, []):
        if d not in seen:
            seen.add(d)
            closure(d, seen)
    return seen


def simple(sym):
    return [p for p in re.split(r"[./]", sym) if p][-1]


def owners(sym):
    tail = re.sub(r"[^/.#]*\$package\.", "", sym).rstrip(".").rsplit("/", 1)[-1]
    return tail.split(".")


def exportable(sym):
    return len(owners(sym)) > 1


def rank(sym):
    for i, prefix in enumerate(["com/histmersion/api/", "com/histmersion/plugin/", "com/histmersion/util/",
                                "com/histmersion/domain/"]):
        if sym.startswith(prefix):
            return i + 1
    return 5


def top_object(sym):
    return sym.rsplit("/", 1)[0] + "/" + owners(sym)[0]


def one_way(rows):
    count = defaultdict(int)
    for m, c in rows:
        count[(top_object(m), top_object(c))] += 1

    def kept(m, c):
        there, back = (top_object(m), top_object(c)), (top_object(c), top_object(m))
        return there[0] == there[1] or count[back] == 0 or (count[there], there) > (count[back], back)

    return [(m, c) for m, c in rows if kept(m, c)]


def main(report, out, excluded_file=None):
    excluded = set(open(excluded_file).read().split()) if excluded_file else set()
    parent, mod, pairs = {}, {}, set()

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for line in open(report):
        f = line.rstrip("\n").split("\t")
        if len(f) < 11 or f[0] != "bridge":
            continue
        a, b, base_a, base_b, constraint_a, constraint_b = f[1:7]
        if base_a != base_b or constraint_a != constraint_b or simple(a) != simple(b):
            continue
        if a in excluded or b in excluded or simple(a) in EXCLUDED_NAMES:
            continue
        mod[a], mod[b] = module(f[9]), module(f[10])
        pairs.add(frozenset((a, b)))
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    def sees(member, canon):
        return mod[member] == mod[canon] or mod[canon] in closure(mod[member])

    groups = defaultdict(list)
    for s in list(parent):
        groups[find(s)].append(s)
    rows = []
    for members in groups.values():
        def paired(m, c):
            return frozenset((m, c)) in pairs

        canon = min(members, key=lambda c: (-sum(paired(m, c) and sees(m, c) for m in members), not exportable(c),
                                            rank(c), len(owners(c)), c))
        if not exportable(canon):
            continue
        top = owners(canon)[0]
        for m in members:
            if m != canon and paired(m, canon) and sees(m, canon) and top not in owners(m)[:-1]:
                rows.append((m, canon))
    rows = one_way(rows)
    with open(out, "w") as o:
        for s, c in sorted(rows):
            o.write(f"{s}\t{c}\n")
    print(f"{len(groups)} groups, {len(rows)} aliases")


if __name__ == "__main__":
    main(*sys.argv[1:])
