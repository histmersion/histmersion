#!/usr/bin/env bash
# Regenerate the committed period-preset seed (periodo-seed.json) from the current PeriodO dump.
# Only needed when PeriodO moves or build.py's SELECTION changes — the output is committed, so
# `histmersion periods seed` (and a server's first start) works on a fresh install with no network.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

PERIODO_URL="https://n2t.net/ark:/99152/p0d.json"

echo "→ PeriodO (CC0)"
curl -fsSL -H "Accept: application/json" -o "$tmp/p0d.json" "$PERIODO_URL"
python3 "$here/build.py" "$tmp/p0d.json" "$here/periodo-seed.json"

echo "done — commit the changed JSON, then: histmersion periods seed"
