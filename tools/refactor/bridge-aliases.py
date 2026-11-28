#!/usr/bin/env python3
"""The alias list for BridgeTypes from its report, under owner decision N1 (2026-11-27): "same-name pairs only".

  python3 tools/refactor/bridge-aliases.py <bridge-types.tsv> <out.txt> [<excluded symbols file>]

Bridges `To(x.value)` whose two newtypes have the same simple name, base type and constraint are joined into groups.
In each group one canonical type is chosen: the member directly bridged with, and visible to (its own module, or one
their module depends on), the most members; then a member of an object (an export cannot name a package's top-level
type); then the lowest layer; then the shortest owner path (the type's name appears in problem texts, so the most
general owner); then the first by symbol. A member becomes an export of it only when the two are bridged directly
(no transitive merging), it can see it, and it is not inside an object named like the canonical's outermost object
(the export would name itself); the others stay as they are. `Wire` types are left out: each is one enum's spelling,
a different concept per enum (census section 2c), whatever its name.
A symbol in the excluded file (one per line) takes no part.
"""
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
    with open(out, "w") as o:
        for s, c in sorted(rows):
            o.write(f"{s}\t{c}\n")
    print(f"{len(groups)} groups, {len(rows)} aliases")


if __name__ == "__main__":
    main(*sys.argv[1:])
