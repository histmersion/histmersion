# `db/gazetteer` — the external gazetteer sources (#1064)

The bundled seed (`db/places`: Natural Earth, Pleiades, the curated supplement, the traced polities)
is what every install starts with. This directory adds the **external sources** the World Historical
Gazetteer itself is built from — GeoNames, Wikidata, and any Linked Places Format dataset — imported
by the same one-time gazetteer import job (`histmersion init`'s job) and joined to the seed **by
shared identifiers only**:

```bash
histmersion places sources                 # what is pinned, what is on disk, what was imported
histmersion places load wikidata           # a DRY RUN: reads, joins and counts, keeps nothing
histmersion places load wikidata --apply   # the import (a job; `histmersion jobs` follows it)
histmersion places load geonames --apply
histmersion places load lpf --apply        # every file in the LPF drop folder
```

## What is here

| file | what |
|---|---|
| `extracts.json` | the **pins**: for `geonames` and `wikidata`, the extract's file name, version, sha256, size, feature count, licence, attribution and (optional) download `url` |
| `build.py` | builds the extracts from upstream and pins them (stdlib Python) |
| `test_build.py` | its tests (`python3 -m unittest db/gazetteer/test_build.py`, part of `just test-scripts`) |

The extracts themselves are **not** in git and **not** in the bundle (the bundle ships this
directory, i.e. only the pins). The server looks for them in `places.sources.dir` (default
`<data dir>/gazetteer`, i.e. `~/Library/Application Support/Histmersion/gazetteer` on macOS). A
missing extract is downloaded from its pin's `url` when one is set (none is, yet: there is no
hosting); either way its **sha256 is verified before a single line is read**, and a mismatch is
refused. Why not the bundle: the two extracts are 82 MB together and change on their own schedule
(a re-pin is a commit to `extracts.json`, not a 3.3 GB rebuild); why not an upstream URL: GeoNames
publishes only today's dump and Wikidata changes by the minute, so only OUR extract can be pinned.

## Format

Both extracts are **Linked Places Format, one Feature per line, gzip** (`*.ndjson.gz`) — the same
format the LPF importer reads, so all three sources go through one reader
(`com.histmersion.core.Gazetteer`). A feature's `@id` is its authority URI (`https://sws.geonames.org/…`,
`http://www.wikidata.org/entity/Q…`); its `links` are `exactMatch`/`closeMatch` to `gn:`, `wd:`,
`pl:` (Pleiades) and `tgn:` ids; names carry `lang` and, where the source dates them, a `when` whose
open side stays open (a name used "until 1935" has no start). The output is deterministic: features
sorted by id, keys sorted, gzip without a timestamp — the same input gives the same bytes.

## The pinned extracts (GeoNames 2026-10-08, Wikidata 2026-10-09)

| name | features | names | links | file size | built from |
|---|---:|---:|---:|---:|---|
| `geonames` | 725 096 | 2 353 211 | 151 158 | 31.5 MB | `allCountries.zip` + `alternateNamesV2.zip` of 2026-10-08 (627 MB of zips, read in place) |
| `wikidata` | 766 641 | 5 830 600 | 438 607 | 53.7 MB | 15 SPARQL result files (~1.1 GB TSV) from QLever, Wikidata dump of 2026-08-10 updated live, queried 2026-10-09; 80 098 dated, 252 471 described, 3 centuries-typed-as-years dropped |

### Filters, and the measurements behind them

**Window.** Both are clipped to lon −25…100, lat 5…72: Europe, the Mediterranean, the Near East,
Anatolia, the Caucasus, the steppe, Central and South Asia, Egypt and North Africa
(`BBOX` in `build.py`).

**GeoNames** (CC BY 4.0): 13.47 M rows worldwide, 6.13 M in the window, of which 2.90 M are of the
classes asked for — A (administrative), P (populated places) and the historical/archaeological spots
of S, plus L.RGNH (historical region). That was measured and judged excessive: 2.33 M of them are
ordinary `PPL` hamlets known only by a dot. So:

- **A**: everything but school districts and zones (ADMS, ZN, ZNB) — ADM1…5, their historical `…H`
  forms, PCLI/PCLH… (~232 k).
- **P**: every seat, capital and historical, abandoned or destroyed place (PPLA*, PPLC*, PPLG,
  PPLH, PPLQ, PPLW, PPLR) kept outright; the ordinary ones (PPL, PPLL, PPLF, PPLS, STLMT) only when
  they carry something beyond the dot — a population, a Wikidata link, a dated or historic name, or a
  name in a historical language; sections of places (PPLX) dropped.
- **S**: only ANS (archaeological), RUIN, HSTS (historical site), CSTL, FT, PYR(S), TMPL, TMB, MSTY,
  SHRN, RLG, CTRR, HERM, CH, MSQE, SYG, PGDA, MNMT, WALLA, BUR, CAVE, BDGQ — never hotels, schools
  or banks.
- **names**: alternateNamesV2 in English, the corpus languages (de fr it es pl), the ancient and
  original languages (grc la akk hit sux uga phn arc syc egy cop peo pal xcl sa pi ota got non ang
  hbo he …) and the modern languages of the window; plus names with no language (transliterations
  like "Bogazkoy"). Colloquial names and pseudo-codes (links, postcodes, airport codes) dropped.
  `from`/`to` become the name's `when`; GeoNames' `wkdt` ids become `exactMatch` links.
- Result: **725 096** features. Every one is `modern: true` (undated: findable and placeable, never
  drawn on the time-sliced map; place search and suggestions at a time offer it only from
  `places.modern-from` on).

**Wikidata** (CC0): items with a coordinate (P625) that are instances (P31 with the P279 subclass
closure, computed by the endpoint) of historical country (Q3024240, 77 classes in the closure),
historical region (Q1620908, 923), former administrative territorial entity (Q19953632, 789), ancient
city (Q15661340, 29), polis (Q148837), archaeological site (Q839954, 594) and human settlement
(Q486972, 2 728). 1 472 902 such items in the window; 1.08 M of them have no GeoNames id. A plain
present-day settlement adds nothing GeoNames lacks, so a settlement is kept only when it links to
another gazetteer (P1566 GeoNames, P1584 Pleiades, P1667 TGN), is dated, or has a name in a historical
language; every item of the historical classes is kept. Labels, aliases, native labels (P1705) and
official names (P1448, dated by their P580/P582 qualifiers) in the same languages as GeoNames; the
place's span from inception/start (P571/P580) and dissolution/end (P576/P582). Wikidata writes years
in XSD 1.1 (year 0 = 1 BCE), converted to the corpus's historical reckoning. Result: **766 621**
features, 80 092 of them dated.

**Times, since 2026-10-09.** Each time property is read as full statements — value, PRECISION and
RANK (`p:`/`psv:`), not the truthy `wdt:` value: the preferred statements win when there are any, a
deprecated one never counts. A value less precise than a year becomes an LPF `earliest`/`latest`
bound: a decade (precision 8) its ten years, a century (7) and a millennium (6) the way Wikidata names
them ("18th century" → 1701–1800, "3rd millennium BCE" → 3000–2001 BCE). A value below 100 stated
to the year is often a century typed as a year ("8" for the 8th century, the failure the findings
extractor guards against): it is DROPPED — that bound becomes unknown, never re-read as a century —
when the item's description names a century the year is not inside, or when its other dates lie at
least 500 years later (`CENTURY_GAP_YEARS`). A missing inception stays missing in the extract; what
the server makes of it is below.

**Descriptions.** `schema:description` in the name languages; the extract keeps English and the
language of the item's native label (P1705) as LPF `descriptions[]` (`{value, lang}`), which the
importer stores in `place_descriptions` (V074) — also for a record that JOINED a seed place.

**Why SPARQL against QLever, not the dump or WDQS.** The JSON dump is ~140 GB compressed (~1.6 TB
of JSON) — a full download and parse for under 1 % of it. The Wikidata Query Service stops every
query at 60 s; the class closure × coordinates join for settlements alone does not finish, so it
would take thousands of tile queries. QLever (`https://qlever.dev/api/wikidata`, University of
Freiburg) serves the same RDF without that limit: 15 queries, one per aspect (core rows, labels, descriptions,
aliases, each time property, each id property, official names, native labels), ~10 minutes, polite
(one at a time, a pause between, back-off on HTTP 429, a generic User-Agent). Each query asks for ONE
property: a `VALUES ?prop` join makes QLever sort on `?prop` and time out. The endpoint is an
argument, so WDQS or a local QLever can be used for the smaller classes.

## Rebuilding and re-pinning

```bash
mkdir -p /tmp/gaz && cd /tmp/gaz                    # NOT inside the repo
UA='Histmersion/<version> (gazetteer import)'
curl -fsSL -A "$UA" -O https://download.geonames.org/export/dump/allCountries.zip
curl -fsSL -A "$UA" -O https://download.geonames.org/export/dump/alternateNamesV2.zip
python3 -I <repo>/db/gazetteer/build.py geonames allCountries.zip alternateNamesV2.zip geonames-<date>.ndjson.gz   # ~1 min
python3 -I <repo>/db/gazetteer/build.py wikidata-fetch https://qlever.dev/api/wikidata wd                          # ~10 min, resumable
python3 -I <repo>/db/gazetteer/build.py wikidata wd wikidata-<date>.ndjson.gz                                       # ~1 min
python3 -I <repo>/db/gazetteer/build.py pin <repo>/db/gazetteer/extracts.json geonames geonames-<date>.ndjson.gz version=<date> built=<date>
python3 -I <repo>/db/gazetteer/build.py pin <repo>/db/gazetteer/extracts.json wikidata wikidata-<date>.ndjson.gz version=<date> built=<date>
```

`pin` keeps the other fields of an entry (title, licence, attribution, url) unless given again.

## What an imported place's silence about time means (V074)

The bundled seed and the curated places state their periods on purpose: an open side is open (a
modern country "until today", a timeless region). GeoNames and Wikidata often just say less:

| the source states | stored `period` | `start_unknown` | the API says (`dating`) | time filters |
|---|---|---|---|---|
| start and end | `[start, end]` | false | `stated` | as stated |
| a start only | `[start, ∞)` | false | `stated` ("from …") | from the start on (a town founded in 1200 is still there) |
| an end only (the Kingdom of Patones, dissolved 1769) | `[end − window, end]` | **true** | `end-only`: "end 1769 CE, start unknown", `periodStart` empty | only in the window before the end |
| nothing (most of both sources) | `(−∞, ∞)` | false | `unknown`: "date unknown" | found and placed by name at any time; **never** on a time-sliced map |

`window` is `places.sources.open-start-window-years` (300), applied at import — a re-import with a
changed value moves exactly those rows (the write is a diff on `period`); V074 repaired the rows the
2026-10-08 import stored, with the default. "Time filters" are all of them, because they all read
`period`: the map at a year, place search and picking with a time, the geocoder's time rule and the
`finding_places` match. An LPF dataset's places follow the dataset's own `when`: a feature's, else
the dataset's.

**Not in the hierarchy, no findings by proximity.** A GeoNames or Wikidata place is for finding a
name, placing a finding and an explicit pick. It is no part of a filter's scope, a place's children
or the tree (the spatial-hierarchy builder leaves it out too), and `finding_places` gives it only the
findings whose point IS its point — the ones the geocoder placed there by its name — never those
within the point tolerance, inside it or matching one of its names (on live, 2026-10-09, those had
attached 211 k rows: 127 k to Wikidata sites, 65 k to GeoNames sites, 14 k to GeoNames regions).

**On the map** the base layers (polity, region, site, feature) draw the bundled seed, a person's
places and LPF datasets; a GeoNames or Wikidata place is drawn only once a finding is attached to
it (`finding_places`) — or when it is picked or searched for, through the place API. A region or
polity whose shape is only a point (all Pleiades and curated regions) has no boundary yet: the web
map draws its name marked "no boundary yet" (hidden when zoomed far out), never a dot, and its card
says "point only — boundary not yet available"; the geo feed and the place API carry `hasBoundary`.

## How a record joins the gazetteer

Each record is joined by its identifiers — its own `@id` and its `links` — against `place_links`
(the seed's own Pleiades, Wikidata and GeoNames ids are links since V071). If they name places
already there, the record's names and links go to the best of them: the bundled seed first, then an
LPF dataset, then Wikidata, then GeoNames (the smaller id breaks a tie). Only a record that names no
place becomes a place of its own (`wikidata_q…`, `geonames_…`, `lpf_<dataset>_…`). **Nothing is joined
by coordinates.** Every row a source writes carries `import_source`, `import_ref` (the record's id)
and `import_version`; the source itself (title, version, sha256, licence, citation, time span,
counts) is a row of `gazetteer_sources`. A re-import of the same version changes nothing (every write
is a diff); one of a new version updates what changed and removes what the source no longer has.
Recommended order: `wikidata`, then `geonames`, then `lpf` — Wikidata carries the cross-links that let
GeoNames records join instead of becoming places of their own.

## What an import costs (measured 2026-10-08, a laptop, an empty corpus)

| step | first run | re-run (same versions) |
|---|---:|---:|
| `places load wikidata --apply` | ~11 min — 753 382 new places, 13 239 joined seed places | ~80 s, nothing changed |
| `places load geonames --apply` | ~9.5 min — 474 477 new places, 250 619 joined | ~45 s, nothing changed |
| `places load lpf --apply` | seconds per WHG-sized file | seconds |

After all three the gazetteer holds 1.27 M places, 8.34 M names and 1.76 M links, and the database
grows by ~4.9 GB (trigram and name-key indexes included). Each real run that changes something
re-materialises `finding_places` at the end, which takes longer with a large corpus. A dry run
takes about as long as the import; its join counts are exact within a batch of 1 000 records and
slightly high across batches (a record cannot join a place an earlier, rolled-back batch would have
created). An interrupted import is simply run again: every batch it committed is kept, and the
re-run completes the rest and then removes what the source no longer has.

`GET /api/places?q=` stays ~0.1 s at that size: candidates come from trigram indexes (V071), and a
query of one or two characters is matched by name key, not by substring.

## The LPF drop folder

`places.sources.lpf-dir` (default `<data dir>/gazetteer/lpf`): put World Historical Gazetteer
downloads there — `*.json` / `*.jsonld` / `*.geojson` (a FeatureCollection, read whole up to
`max-lpf-mb`), `*.ndjson` / `*.jsonl` (one feature per line, optionally `.gz`) or `*.tsv` (LP-TSV).
Each file is a dataset `lpf:<file name>`, versioned by its sha256; its title, citation, licence and
time span come from the FeatureCollection's own keys or a `<file>.meta.json` beside it (the only place
an LP-TSV file can say them). Removing a file from the folder does not remove what it imported.

## Getty TGN

Not imported (yet). TGN ids are kept wherever Wikidata gives them (P1667, 25 753 items in the window),
so a TGN-sourced LPF dataset already joins by them. A direct TGN extract (its SPARQL endpoint or the
N-Triples dump, ~3 GB) is a follow-up.
