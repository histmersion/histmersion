# `db/places` — the gazetteer seed (plan §1.6a)

The committed JSON here is the gazetteer's starting content: **who is where, when, and inside what.**
`histmersion places seed` imports every file, then re-materialises which finding falls in which place.

```bash
histmersion places seed                 # import everything below + rebuild finding_places
histmersion places search Anatolia      # → the id to use as `--place`
histmersion search --place anatolia --from -1500 --to -1200
```

A server imports this directory by itself on its first start, when the `places` table is empty (the
bundle ships a copy; see "First-run data" in the main README), and `histmersion init` does the same on
demand — `places seed` is for a checkout, where the CLI reads the files on the host.

Nothing here is generated at run time and nothing needs the network: `fetch.sh` regenerates the
derived files from upstream, and their outputs are committed.

## What is in here

| file | features | what it is | licence |
|---|---:|---|---|
| `01-natural-earth.json` | 207 | continents → subregions → countries, `kind = region` | public domain |
| `02-natural-earth-physical.json` | 1 825 | seas, gulfs, straits, lakes, rivers, mountain ranges, peninsulas, deserts, islands, peaks — `kind = feature\|region`, `modern: true` | public domain |
| `03-pleiades.json` | 33 778 | ancient places as points with every attested/romanised/modern name, `kind = site\|region\|polity\|people\|feature` | CC-BY |
| `04-natural-earth-places.json` | 3 995 | present-day cities and towns as points, `kind = site`, `modern: true` | public domain |
| `05-curated.json` | 84 | the curated supplement: historical regions, valleys and sites the sources above lack (`supplement/places.csv`) | CC0 (Wikidata coordinates) |
| `polities/macro_regions.geojson` | 6 | Anatolia, Aegean, Levant, Mesopotamia, Egypt, Pontic-Caspian steppe | CC0 (stream 2.2) |
| `polities/{eba,mba,lba,ia1,ia2}.geojson` | 71 | Bronze/Iron-Age polity polygons per canonical period | CC0 / ODbL (stream 2.2) |

Everything is read by the same parser (`com.histmersion.core.Lpf`), which accepts **Linked Places Format**
(what the World Historical Gazetteer exports) *and* a plain GeoJSON `FeatureCollection` — including
the `properties.{id,label,kind,period_start,period_end,names[],part_of[],source}` dialect the
hand-traced polygons under `polities/` use. `histmersion places import <file>` loads any of them, and
`Lpf.render` writes LPF back out, so an import round-trips.

### Sources and licences

- **Natural Earth**, 1:110m Admin 0 – Countries
  (<https://github.com/nvkelso/natural-earth-vector>). **Public domain** —
  <https://www.naturalearthdata.com/about/terms-of-use/>: "no permission is needed to use Natural
  Earth. Crediting the authors is unnecessary." Credited anyway, in each place's `source`.
- **Natural Earth 1:10m** physical vectors (`geography_regions_polys`, `geography_marine_polys`,
  `lakes`, `rivers_lake_centerlines`, `geography_regions_points`, `geography_regions_elevation_points`)
  and `populated_places`, same repository, same **public-domain** terms; each place keeps its
  `wikidata` id (and a city its `geonames` id) in `properties`.
- **Pleiades**, the community gazetteer of ancient places (<https://pleiades.stoa.org>), places AND
  names CSV dumps. **CC-BY 3.0** — attribution is required and is carried in every row's `source`
  (`Pleiades (pleiades.stoa.org), CC-BY`) and in each place's `properties.pleiades_uri`.
- **Wikidata** coordinates (P625) for the curated places — **CC0**; each row of
  `supplement/places.csv` names its item (`Wikidata Q211962 (...)`) in `source`.
- `polities/` is stream 2.2's hand-traced set; its own `README.md` documents every polygon's map
  source, licence and confidence.

### Modelling decisions

- **Ids are a contract.** Continents are `continent_asia|continent_europe|continent_africa|…` —
  *not* `ne_…` — because the hand-traced macro-regions declare `part_of: continent_asia`. Renaming
  them silently breaks that link (the importer reports the dangling parent rather than failing).
  Countries are `ne_country_<iso a3>`, subregions `ne_subregion_<slug>`, Pleiades `pleiades_<id>`.
- **Natural Earth is `kind = region` at all three levels.** It is the stable *modern* skeleton, not a
  set of ancient polities; `kind = polity` is reserved for the traced Bronze/Iron-Age shapes.
- **Continents carry no geometry.** They are hierarchy nodes; a finding reaches "Asia" through
  `finding_places` on Turkey plus the `part_of` edge. `GET /api/places/continent_asia` therefore
  reports the *union of its children's* shapes so the web UI still has something to draw.
- **Natural Earth has no period**, so those rows are valid at every date — which is what makes
  "group this Late-Bronze-Age finding under Turkey → Asia" work at all.
- **A Pleiades point's kind is what its feature types say it is** (2026-10-09, `build.py pleiades_kind`):
  a `people` (the Ubii, the Athenian tribe Aiantis — a group, not a territory) is kind `people`; a
  `province`, `province-2`, `satrapy`, `regio-augusti`, `state` or `diocese` is a `polity`; a "region"
  that is also a `settlement` or an Attic deme is a `site` (Peiraieus); a site that is a settlement
  stays one. `lint --rule place-kind` applies the same rules (plus the linked Wikidata item's class)
  to a running store, so a re-seed never undoes it; `./build.py reclassify 03-pleiades.json`
  re-derives the kinds in place without a download. Records Pleiades withdrew as duplicates
  ("Withdrawn (duplicate): Creta et Cyrene") are left out — the replacement is its own record.
- **A point on an atlas grid square's centre is approximate.** Pleiades gives a place whose only
  location is a Barrington Atlas grid square that square's centre (5°, 1° or ½° cells): Thessalia the
  province at 37.5 N 22.5 E. The store marks it (`places.location_precision = 'grid-cell'`, V075):
  the geocoder ranks it below any precise point, it takes findings only by an explicit match, and
  `lint --rule place-grid-point` offers a linked Wikidata coordinate instead.
- **Pleiades periods come from the dump's own `minDate`/`maxDate`**, already signed years (BCE
  negative) — no PeriodO lookup needed. `maxDate = 2100` is Pleiades' own "still extant" convention.
- **Pleiades is clipped** to the corpus window (lon −11–75, lat 15–60: Ireland → Indus, Nubia →
  Uppsala; until #987 it was lon 15–60, lat 25–50, which left out Etruria, Rome, Gaul, Britain and
  Elephantine) and to locatable, drawable feature types. Widen `BBOX` in `build.py` and re-run
  `fetch.sh` to change that.
- **Pleiades names come from the names dump** (#987). The places dump carries only a title
  ("Mortuum Mare/Asphaltitis Limne"), so "Dead Sea", "Sea of Galilee" and every attested Greek,
  Latin, Akkadian or Hebrew spelling were missing until the names dump was read. They are imported
  WITHOUT their Pleiades date range: a name the corpus writes in modern English must match a finding
  of any date (`place_names.period` gates the name match of `finding_places` and `places search --as-of`).
- **Natural Earth physical features and cities are the plain English names** — "Balkans" (a further
  name of NE's "Balkan Peninsula"), "Black Sea", "Dnieper", "Kraków". They are undated, so they are
  flagged `properties.modern = true` and the time-sliced map never draws them; they exist to be
  found and to place findings. A label is NE's own `name`, spelled out ("Balkan Pen." → "Balkan
  Peninsula"); its looser `name_en` is a further name, because the geocoder ranks a label above a
  name and "Levant" must stay the region, not NE's "Levant Coast". 1:10m, clipped to lon −25–150,
  lat −5–78 by each feature's bbox centre, shapes simplified to ~1 km.

### The curated supplement (`supplement/`, #987)

What no upstream file has, versioned as CSV and merged by `build.py supplement` (which `fetch.sh`
runs last; run it alone after editing a CSV — it rewrites only what changed and a second run is a
no-op):

- `supplement/places.csv` — `id,label,kind,lon,lat,names,source`: a place the seed lacks (Arkona,
  the Trinity Lavra of St. Sergius, Beersheba Valley, Bohemia, the Armenian Highlands…). **Every point
  has a `source`** — a Wikidata item's coordinate location — and `build.py` refuses a row without one.
  Items with no coordinate on Wikidata were left out, never estimated. → `05-curated.json`.
- `supplement/names.csv` — `place_id,name,lang,why`: a further English name for a place a seed file
  already has ("Lake Kinneret" for the Sea of Galilee, "Gaul" for Gallia, "Anshan" for Tal-i Malyan).
  An id no seed file has is an error.

A name a person adds on a running server (`histmersion places name add <id> "<name>" --lang en`, MCP
`place_names`) is stored as **manual** (V068): an import replaces the other names of the places it
mentions but never deletes these, so a re-seed keeps them. `histmersion places name list --csv` prints
them as `names.csv` lines, to promote the good ones into this supplement. A place a person adds
(`histmersion places add`, MCP `add_place`) is `local` with an id no seed uses (`local_…`): no seed
touches it, and a bundle carries it.
- **Alternate names matter more than labels.** A country carries its English, formal, Arabic, German,
  Greek, Spanish, Persian, French, Hebrew, Italian, Russian and Turkish names, because the corpus is
  written in several of them; Pleiades titles are split on `/` (`Dianium/Hemeroskopeion`).
- Coordinates are rounded to 5 decimals (~1 m) — far finer than a 1:110m boundary, and half the size.

## Regenerating

```bash
./fetch.sh                    # downloads upstream, rewrites 01-… to 05-…
git diff --stat db/places     # review before committing
```

`build.py` is the whole transformation and takes no arguments beyond input and output paths:

```bash
python3 build.py natural-earth          <ne_110m_admin_0_countries.geojson> 01-natural-earth.json
python3 build.py natural-earth-physical <dir of the ne_10m_* layers>        02-natural-earth-physical.json
python3 build.py pleiades               <places.csv> <names.csv>            03-pleiades.json
python3 build.py natural-earth-places   <ne_10m_populated_places.geojson>   04-natural-earth-places.json
python3 build.py supplement             .                                   # 05-curated.json + curated names
python3 -m unittest test_build.py                                           # build.py's own tests
```

## Import order

`histmersion places seed` imports the top-level files in name order, then each subdirectory, and finally
**re-imports any file that reported a dangling parent** — so a polygon may name a parent that only
exists after a later file has loaded, and the edge still lands. A file's fallback `kind` comes from
its directory name (`polities/` → `polity`); a feature that declares its own `kind` always wins.

## Beyond the seed: GeoNames, Wikidata, LPF datasets

The external sources the World Historical Gazetteer is built from are NOT seed files: they are
pinned extracts (and a drop folder for LPF datasets) imported with `histmersion places load
geonames|wikidata|lpf`, joined to these places by shared identifiers. See `db/gazetteer/README.md`.
