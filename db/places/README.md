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

Nothing here is generated at run time and nothing needs the network: `fetch.sh` regenerates the two
derived files from upstream, and their outputs are committed.

## What is in here

| file | features | what it is | licence |
|---|---:|---|---|
| `01-natural-earth.json` | 207 | continents → subregions → countries, `kind = region` | public domain |
| `03-pleiades.json` | 16 495 | ancient places as points, `kind = site\|region\|feature` | CC-BY |
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
- **Pleiades**, the community gazetteer of ancient places (<https://pleiades.stoa.org>), places CSV
  dump. **CC-BY 3.0** — attribution is required and is carried in every row's `source`
  (`Pleiades (pleiades.stoa.org), CC-BY`) and in each place's `properties.pleiades_uri`.
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
- **Pleiades periods come from the dump's own `minDate`/`maxDate`**, already signed years (BCE
  negative) — no PeriodO lookup needed. `maxDate = 2100` is Pleiades' own "still extant" convention.
- **Pleiades is clipped** to the corpus window (lon 15–60, lat 25–50) and to locatable, drawable
  feature types: the full dump is 42 409 places spanning the whole Roman world, ~20 MB of points
  the corpus never refers to. Widen `BBOX` in `build.py` and re-run `fetch.sh` to change that.
- **Alternate names matter more than labels.** A country carries its English, formal, Arabic, German,
  Greek, Spanish, Persian, French, Hebrew, Italian, Russian and Turkish names, because the corpus is
  written in several of them; Pleiades titles are split on `/` (`Dianium/Hemeroskopeion`).
- Coordinates are rounded to 5 decimals (~1 m) — far finer than a 1:110m boundary, and half the size.

## Regenerating

```bash
./fetch.sh                    # downloads upstream, rewrites 01-… and 03-…
git diff --stat db/places     # review before committing
```

`build.py` is the whole transformation and takes no arguments beyond input and output paths:

```bash
python3 build.py natural-earth <ne_110m_admin_0_countries.geojson> 01-natural-earth.json
python3 build.py pleiades      <pleiades-places-latest.csv>        03-pleiades.json
```

## Import order

`histmersion places seed` imports the top-level files in name order, then each subdirectory, and finally
**re-imports any file that reported a dangling parent** — so a polygon may name a parent that only
exists after a later file has loaded, and the edge still lands. A file's fallback `kind` comes from
its directory name (`polities/` → `polity`); a feature that declares its own `kind` always wins.
