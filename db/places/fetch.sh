#!/usr/bin/env bash
# Regenerate the committed gazetteer seed files from their upstream sources (plan §1.6a).
# Downloads into a scratch dir, runs build.py, and rewrites the JSON next to this script.
# Only needed when the upstream data moves — the outputs are committed, so `histdb places seed`
# works on a fresh clone with no network.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

NE_URL="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"
PLEIADES_URL="https://atlantides.org/downloads/pleiades/dumps/pleiades-places-latest.csv.gz"

echo "→ Natural Earth (public domain)"
curl -fsSL -o "$tmp/ne.geojson" "$NE_URL"
python3 "$here/build.py" natural-earth "$tmp/ne.geojson" "$here/01-natural-earth.json"

echo "→ Pleiades (CC-BY)"
curl -fsSL -o "$tmp/pleiades.csv.gz" "$PLEIADES_URL"
gunzip -c "$tmp/pleiades.csv.gz" > "$tmp/pleiades.csv"
python3 "$here/build.py" pleiades "$tmp/pleiades.csv" "$here/03-pleiades.json"

echo "done — commit the changed JSON, then: histdb places seed"
