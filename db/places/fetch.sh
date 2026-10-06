#!/usr/bin/env bash
# Regenerate the committed gazetteer seed files from their upstream sources (plan §1.6a, #987).
# Downloads into a scratch dir, runs build.py, and rewrites the JSON next to this script; then applies
# the curated supplement (supplement/*.csv). Only needed when the upstream data moves — the outputs are
# committed, so `histmersion places seed` works on a fresh clone with no network.
# After editing only supplement/*.csv, `python3 build.py supplement .` is enough (no download).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

NE="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"
PLEIADES="https://atlantides.org/downloads/pleiades/dumps"

echo "→ Natural Earth countries (public domain)"
curl -fsSL -o "$tmp/ne.geojson" "$NE/ne_110m_admin_0_countries.geojson"
python3 "$here/build.py" natural-earth "$tmp/ne.geojson" "$here/01-natural-earth.json"

echo "→ Natural Earth physical features (public domain)"
for layer in ne_10m_geography_regions_polys ne_10m_geography_marine_polys ne_10m_lakes \
             ne_10m_rivers_lake_centerlines ne_10m_geography_regions_points \
             ne_10m_geography_regions_elevation_points; do
  curl -fsSL -o "$tmp/$layer.geojson" "$NE/$layer.geojson"
done
python3 "$here/build.py" natural-earth-physical "$tmp" "$here/02-natural-earth-physical.json"

echo "→ Pleiades places + names (CC-BY)"
curl -fsSL -o "$tmp/pleiades.csv.gz" "$PLEIADES/pleiades-places-latest.csv.gz"
curl -fsSL -o "$tmp/pleiades-names.csv.gz" "$PLEIADES/pleiades-names-latest.csv.gz"
gunzip -c "$tmp/pleiades.csv.gz" > "$tmp/pleiades.csv"
gunzip -c "$tmp/pleiades-names.csv.gz" > "$tmp/pleiades-names.csv"
python3 "$here/build.py" pleiades "$tmp/pleiades.csv" "$tmp/pleiades-names.csv" "$here/03-pleiades.json"

echo "→ Natural Earth populated places (public domain)"
curl -fsSL -o "$tmp/places.geojson" "$NE/ne_10m_populated_places.geojson"
python3 "$here/build.py" natural-earth-places "$tmp/places.geojson" "$here/04-natural-earth-places.json"

echo "→ curated supplement (supplement/*.csv)"
python3 "$here/build.py" supplement "$here"

echo "done — commit the changed JSON, then: histmersion places seed"
