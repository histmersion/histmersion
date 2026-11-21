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
