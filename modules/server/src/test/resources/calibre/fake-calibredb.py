#!/usr/bin/env python3
# A stand-in for Calibre's `calibredb`, for the test suites only: it answers `list --for-machine` from the
# library's books.json and applies `add`, `set_metadata` and `add_format` to that file, appending every
# invocation's arguments to the library's calibredb.log. It never reads or writes anything outside the
# `--with-library` directory it is given, and refuses one that does not hold the fixture's marker file.
import json, os, shutil, sys

args = sys.argv[1:]
lib = None
rest = []
i = 0
while i < len(args):
    if args[i] == "--with-library" and i + 1 < len(args):
        lib = args[i + 1]
        i += 2
    else:
        rest.append(args[i])
        i += 1
if lib is None or not os.path.isfile(os.path.join(lib, "histmersion-fixture-library")):
    sys.stderr.write("fake calibredb: not a fixture library: %r\n" % lib)
    sys.exit(9)
with open(os.path.join(lib, "calibredb.log"), "a", encoding="utf-8") as log:
    log.write("calibredb " + " ".join(json.dumps(a, ensure_ascii=False) for a in args) + "\n")
store = os.path.join(lib, "books.json")
with open(store, encoding="utf-8") as f:
    books = json.load(f)


def save():
    with open(store, "w", encoding="utf-8") as f:
        json.dump(books, f, ensure_ascii=False, indent=1, sort_keys=True)


def opt(name, default=None):
    for j, a in enumerate(rest):
        if a == name and j + 1 < len(rest):
            return rest[j + 1]
    return default


def opts(name):
    return [rest[j + 1] for j, a in enumerate(rest) if a == name and j + 1 < len(rest)]


def book(n):
    for b in books:
        if b["id"] == n:
            return b
    return None


cmd = rest[0] if rest else ""
if cmd == "list":
    if os.path.exists(os.path.join(lib, "gui.lock")):
        sys.stderr.write("apsw.BusyError: database is locked\n")
        sys.exit(1)
    fields = (opt("--fields") or "id,title").split(",")
    limit = int(opt("--limit", "1000000"))
    q = opt("--search")
    hits = sorted(books, key=lambda b: b["id"])
    if q is not None and q.startswith("id:"):
        hits = [b for b in hits if str(b["id"]) == q[3:]]
    elif q:
        ql = q.lower()
        hits = [b for b in hits if ql in b.get("title", "").lower() or ql in b.get("authors", "").lower()]
    hits = hits[:limit]
    if hits:
        out = [{k: b[k] for k in fields if k in b} for b in hits]
        print(json.dumps(out, ensure_ascii=False, indent=2))
elif cmd == "add":
    src = rest[1]
    n = max([b["id"] for b in books] + [0]) + 1
    ext = src.rsplit(".", 1)[-1].lower()
    rel = "added/%d" % n
    os.makedirs(os.path.join(lib, rel), exist_ok=True)
    dst = os.path.join(lib, rel, "book.%s" % ext)
    shutil.copyfile(src, dst)
    ids = {}
    for x in opts("--identifier"):
        k, v = x.split(":", 1)
        ids[k] = v
    if opt("--isbn"):
        ids["isbn"] = opt("--isbn")
    books.append({
        "id": n,
        "title": opt("--title", ""),
        "authors": opt("--authors", ""),
        "pubdate": "0101-01-01T00:00:00+00:00",
        "tags": [t for t in (opt("--tags") or "").split(",") if t],
        "identifiers": ids,
        "formats": [dst],
    })
    save()
    print("Added book ids: %d" % n)
elif cmd == "set_metadata":
    b = book(int(rest[1]))
    if b is None:
        sys.stderr.write("No book with id: %s\n" % rest[1])
        sys.exit(1)
    for f in opts("--field"):
        k, v = f.split(":", 1)
        if k == "tags":
            b["tags"] = [t for t in v.split(",") if t]
        elif k == "identifiers":
            b["identifiers"] = dict(p.split(":", 1) for p in v.split(",") if ":" in p)
        else:
            b[k] = v
    save()
    print("Changed")
elif cmd == "add_format":
    b = book(int(rest[1]))
    src = rest[2]
    ext = src.rsplit(".", 1)[-1].lower()
    dst = os.path.join(lib, "added", "%d-format.%s" % (b["id"], ext))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    b["formats"] = [x for x in b.get("formats", []) if not x.lower().endswith("." + ext)] + [dst]
    save()
else:
    sys.stderr.write("fake calibredb: unsupported command %r\n" % cmd)
    sys.exit(2)
