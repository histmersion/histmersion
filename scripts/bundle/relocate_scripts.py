"""Make a python-build-standalone interpreter's console scripts relocatable.

pip/uv write the BUILD path of the interpreter into each script's shebang, so `bin/docling-tools`
breaks as soon as the bundle is moved or unpacked elsewhere. This rewrites every Python shebang
into the sh/python polyglot uv uses for relocatable environments: /bin/sh runs the first line and
execs the `python3` that sits next to the script; Python sees a harmless string literal.

    python3 relocate_scripts.py <python>/bin
"""

import os
import sys

HEAD = (
    b"#!/bin/sh\n"
    b"'''exec' \"$(dirname -- \"$(realpath -- \"$0\")\")\"/python3 \"$0\" \"$@\"\n"
    b"' '''\n"
)


def main(bindir: str) -> int:
    changed = 0
    for name in sorted(os.listdir(bindir)):
        path = os.path.join(bindir, name)
        if os.path.islink(path) or not os.path.isfile(path):
            continue
        with open(path, "rb") as f:
            first = f.readline()
            if not (first.startswith(b"#!") and b"python" in first):
                continue
            rest = f.read()
        with open(path, "wb") as f:
            f.write(HEAD + rest)
        changed += 1
    print(f"  relocatable shebangs: {changed} scripts")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
