#!/usr/bin/env python3
# A logging wrapper of the system's sqlite3, for the test suites only: it records its arguments in the
# fixture library's sqlite3.log, refuses any database outside a fixture library, then runs sqlite3 with them.
import json, os, sys

args = sys.argv[1:]
db = next((a for a in args if a.endswith("metadata.db")), None)
lib = os.path.dirname(db) if db else None
if lib is None or not os.path.isfile(os.path.join(lib, "histmersion-fixture-library")):
    sys.stderr.write("fake sqlite3: not a fixture library: %r\n" % db)
    sys.exit(9)
with open(os.path.join(lib, "sqlite3.log"), "a", encoding="utf-8") as log:
    log.write("sqlite3 " + " ".join(json.dumps(a, ensure_ascii=False) for a in args) + "\n")
os.execv("/usr/bin/sqlite3", ["sqlite3"] + args)
