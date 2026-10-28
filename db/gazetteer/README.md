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
| `extracts.json` | the **pins**: for `geonames`, `wikidata` and `tgn`, the extract's file name, version, sha256, size, feature count, licence, attribution and (optional) download `url` |
| `build.py` | builds the extracts from upstream and pins them (stdlib Python) |
| `test_build.py` | its tests (`python3 -m unittest db/gazetteer/test_build.py`, part of `just test-scripts`) |

The extracts themselves are **not** in git and **not** in the bundle (the bundle ships this
directory, i.e. only the pins). The server looks for them in `places.sources.dir`
(`HISTMERSION_GAZETTEER_DIR`), by default the **XDG cache directory**: `$XDG_CACHE_HOME/histmersion/gazetteer`,
else `~/.cache/histmersion/gazetteer` — on macOS too. They are a cache (rebuilt or copied, pinned by
checksum), not settings (`~/.config`) and not the corpus (the data directory). `build.py` writes its
extracts there by default (`--out` writes elsewhere). Until 2026-10-17 the default was `<data
dir>/gazetteer`; an extract found there and not in the cache directory is verified and moved over once
(logged), and imports are idempotent, so nothing already imported is lost. The extracts are **not
hosted** anywhere: a pin's `url` is empty, and `places load` then stops and names the exact path to put
the file at, its sha256 and size, and the setting that moves the folder. (A pin's `url`, when one is
set, is downloaded from.) Either way its **sha256 is verified before a
single line is read**, and a mismatch is refused. Why not the bundle: the three world extracts are 194 MB together (82 MB for the window ones) and change on their own schedule
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

## The pinned extracts (GeoNames 2026-10-27, Wikidata 2026-10-27, TGN 2026-10-18): the whole world

| name | features | names | links | file size | built from |
|---|---:|---:|---:|---:|---|
| `geonames` | 1 613 557 | 5 438 700 | 230 695 | 69.8 MB | `allCountries.zip` + `alternateNamesV2.zip` of 2026-10-27 (627 MB of zips, read in place; 81 s, 6.3 GB of memory) |
| `wikidata` | 1 136 126 | 14 308 007 | 790 639 | 121.3 MB | 15 SPARQL result files (2.8 GB TSV) from QLever, Wikidata dump of 2026-08-10 updated live, queried 2026-10-27; 114 945 dated, 1 113 124 described, 2 centuries-typed-as-years dropped, 22 857 items with no readable coordinate left out (173 s, 9.5 GB of memory) |
| `tgn` | 39 906 | 179 787 | — | 3.1 MB | the explicit export of 2026-10-18 (below) |

The window extracts they replace (pinned 2026-10-08/09/17): GeoNames 725 096 features, 2 353 211 names,
31.5 MB; Wikidata 766 641, 5 830 600, 53.7 MB; TGN 32 383, 112 632, 2.2 MB. Of the world extracts,
GeoNames has 729 094 features inside the old window and 884 463 outside it, Wikidata 756 709 and
379 417, TGN 32 508 and 7 398. Names come in 624 (GeoNames), 653 (Wikidata) and 332 (TGN) languages.
The fetch (2026-10-27) took about 3 h 40 min against QLever, most of it the labels and descriptions
asked band by band (below), including waits on its 502s.

### Filters, and the measurements behind them

**Scope (since 2026-10-27).** The whole world, with names in every language: `build.py`'s `--bbox`
defaults to `world` and `--langs` to `all`. What is left out is cut by KIND (the filters below), never
by place. Until 2026-10-27 every extract was clipped to lon −25…100, lat 5…72 (Europe, the
Mediterranean, the Near East, the steppe, Central and South Asia, Egypt and North Africa) and kept
names only in that window's languages; `--bbox window --langs window` still builds that extract, and
any `--bbox minLon,minLat,maxLon,maxLat` / `--langs en,de,…` a smaller one, for a small install. The
Wikidata build cuts a world fetch to its `--bbox` again, so one fetch serves both. A name tagged
`mul` (Wikidata's label for every language) is a name with no language, and the item's title when it
has no English label.

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
- **names**: alternateNamesV2 in every language (`--langs` narrows it), plus names with no language
  (transliterations like "Bogazkoy"). A name in a historical language (`ANCIENT_LANGS`: grc la akk hit
  sux … and, since 2026-10-27, those of East, South and Central Asia, Africa and the Americas — lzh och
  ltc ojp okm kaw xct sog kho txb pra oty gez xmr nci emy qwc …) keeps an ordinary place.
  Colloquial names and pseudo-codes (links, postcodes, airport codes) dropped.
  `from`/`to` become the name's `when`; GeoNames' `wkdt` ids become `exactMatch` links.
- Result: **1 613 557** features world-wide (13 483 421 rows read, 6 028 060 of the classes asked for;
  in the window, 2026-10-08: 725 096). Every one is `modern: true` (undated: findable and placeable, never
  drawn on the time-sliced map; place search and suggestions at a time offer it only from
  `places.modern-from` on).

**Wikidata** (CC0): items with a coordinate (P625) that are instances (P31 with the P279 subclass
closure, computed by the endpoint) of historical country (Q3024240, 77 classes in the closure),
historical region (Q1620908, 923), former administrative territorial entity (Q19953632, 789), ancient
city (Q15661340, 29), polis (Q148837), archaeological site (Q839954, 594) and human settlement
(Q486972, 2 728). 2 162 950 such items in the world (2026-10-27; 1 472 902 in the window,
2026-10-09). A plain
present-day settlement adds nothing GeoNames lacks, so a settlement is kept only when it links to
another gazetteer (P1566 GeoNames, P1584 Pleiades, P1667 TGN), is dated, or has a name in a historical
language; every item of the historical classes is kept. Labels, aliases, native labels (P1705) and
official names (P1448, dated by their P580/P582 qualifiers) in every language; the
place's span from inception/start (P571/P580) and dissolution/end (P576/P582). Wikidata writes years
in XSD 1.1 (year 0 = 1 BCE), converted to the corpus's historical reckoning. Result: **1 136 126**
features world-wide, 114 945 of them dated (in the window, 2026-10-09: 766 621, 80 092).

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

**Heritage monuments, of every period (2026-10-28).** The roots above missed the medieval and Ottoman
monuments of a region — Akronauplia (Q3555617), Larissa Castle (Q1012861), the Talantio monastery
(Q56398298) and the Bermpaka church (Q38280392) are all in Wikidata with coordinates, but castles,
monasteries and churches reach none of those classes (`docs/research-gazetteer-greece.md`). So the
extract also keeps:

- every item with a **heritage designation** (P1435), whatever its class — it is a site, with no
  `class` of its own unless it also reaches a root;
- instances (P31/P279*) of castle Q23413, fort Q1785071, fortification Q57821, city walls Q16748868,
  city gate Q82117, tower house Q91312, tower Q12518, monastery Q44613, chapel Q108325, church building
  Q16970, mosque Q32815, synagogue Q34627, aqueduct Q474 and bridge Q12280 — after the archaeological
  site in `WD_ROOTS` (an item of the older roots keeps its class) and before the settlement.

With each record, in `properties`: `p31` (the item's own classes: the server types a site from them
first, `SiteTypes.wikidataOwn` — a fortress, a minaret, an Eastern Orthodox church), `heritage`
(`[{id, label, start, end}]`: the designation, its English label, and its P580/P582 qualifiers, the
designation's own dates — never the monument's), `periods` (the P2348 values and labels) and
`registers` (P13741 Archaeological Cadastre, P2186 Wiki Loves Monuments, P9951 Greek Castles, P8218
Archaeology in Greece Online, P8068 ToposText, P1936 DARE: ids only — the importer joins by gazetteer ids,
and the Cadastre's data itself is not redistributable). A settlement with a designation or a register id
is kept like a linked one.

**Where a date comes from** (`properties.date_source`, for the place card). The time statements first, as
above: `wikidata-p580` when a start or end time (P580/P582) gave a bound, else `wikidata-p571`
(inception: the start, the end open), else `wikidata-p576`. Only an item they leave undated is dated by
its **time period** (P2348) through PeriodO (`--periodo d.json`, the dump `db/periods/fetch.sh` reads;
its sha256 is in the build's stats): `p2348-periodo:<id>[+<id>…]`, with `periodo: [{id, label,
authority, authorityTitle}]`. A period is a date range only in a region, so the authorities are chosen by
the item's country (P17, `PERIODO_SCHEMES`): for Greece the British School at Athens' periods
(p0ds9qj), then the Chronique des fouilles (p0gtzvd), the Cambridge Companion to the Aegean Bronze Age
(p0qp9rs) and Pleiades (p03wskd). No PeriodO period links Wikidata, and Wikidata's periods are broader
than an authority's ("classical antiquity" is no BSA "Classical", 480–323 BCE), so a curated table
(`PERIODO_ALIASES`, 26 entries checked against both labels) names the period a Wikidata period IS; any
other matches only a period with the same words (or one `sameAs` the item), one per authority. EVERY
P2348 value of an item must resolve, or it stays undated: "Neolithic" and "Frankokratia" with only the
first known would end the item in 3000 BCE. Nothing is guessed: no PeriodO, no country with a scheme, an
unmatched period — no date.

**The Greece run** (`--bbox 19.3,34.7,29.7,41.8`, which also takes in western Turkey, southern Albania,
North Macedonia and Bulgaria; QLever, 2026-10-28; PeriodO dump sha256 `c745db85…`):

| | before (the old roots, same fetch) | after |
|---|---:|---:|
| records | 32 428 | 49 317 (+16 889) |
| with a designation (P1435) | — | 6 682 (2 650 of no root class) |
| with a register id | — | 8 935 |
| dated | 1 917 | 5 260 |
| — `wikidata-p571` / `p580` / `p576` | | 2 922 / 1 432 / 52 |
| — `p2348-periodo` | | 854 |

Of the 3 767 records dated only by P2348, 854 are dated; 16 in Greece are not (Frankokratia,
"prehistory", a century item), and 2 897 are outside Greece (2 829 in North Macedonia), where no scheme is
listed yet. Site types after (`SiteTypes.of` over `p31`, then `class`): settlement 23 843, sanctuary
10 298, find-spot 6 185, fortification 2 991, burial 900, bridge 775, tower 473, villa 441, aqueduct 165,
others 208, none 3 038 (before: settlement 23 578, find-spot 7 959, none 891). A test cut of 23 of these
records and the BSA scheme is committed (`testdata/wikidata-greece`, `GreeceFixture`).

**QLever's 30 s limit (2026-10-28).** The endpoint now stops every query at 30 s, and the plans the
queries had timed out even on one square degree. Three shapes fix it: every aspect joins the item SET
computed first in a subquery (`SELECT DISTINCT ?item`); a statement query (`p:`, the times, the
designations, the official names) restricts the statements to those items in a subquery of its own (else
QLever joins every statement of the property world-wide on `?st`: P576 timed out on a band in the sea,
over the subquery all of Greece takes 11 s); and a regional extract asks for descriptions in every
language (the ~190-code `LANG IN` filter times out in query PLANNING). A connection QLever closes in the
middle of a result is a timeout too (`read_body`). The world fetch has not been re-run with these shapes.
`wikidata-fetch [<endpoint>] <dir>` defaults to `WD_ENDPOINT` (`https://qlever.dev/api/wikidata`; the old
qlever.cs.uni-freiburg.de redirects there), or `--endpoint URL`.

**Why SPARQL against QLever, not the dump or WDQS.** The JSON dump is ~140 GB compressed (~1.6 TB
of JSON) — a full download and parse for under 1 % of it. The Wikidata Query Service stops every
query at 60 s; the class closure × coordinates join for settlements alone does not finish, so it
would take thousands of tile queries. QLever (`https://qlever.dev/api/wikidata`, University of
Freiburg) serves the same RDF without that limit: 15 queries, one per aspect (core rows, labels, descriptions,
aliases, each time property, each id property, official names, native labels), ~10 minutes, polite
(one at a time, a pause between, a generic User-Agent). Each query asks for ONE property: a
`VALUES ?prop` join makes QLever sort on `?prop` and time out. The endpoint is an argument, so WDQS
or a local QLever can be used for the smaller classes.

**A world-wide query too big for one answer** (`fetch_banded`, 2026-10-27). Asked for the whole world,
the labels (18.3 M rows) ran out of time while they were exported, and the descriptions timed out even
in pages of 7 812 rows: each page orders the whole result, and the ordering no longer finishes ("Last
operation: Query planning"). So a query that times out is asked again for each half of its area,
across the longer side, depth first, until the parts fit; a band's inner east and north edges are
open (`lon < e`), so a point on a boundary is read once. Only a band under half a degree on both
sides falls back to pages. QLever also answers HTTP 200 before a result is complete: a query that runs
out of time while its rows stream ends in "An error has occurred while exporting the query result"
appended to the rows so far (`export_failure`). Such an answer is the error it reports, never a
result. The descriptions are fetched in `DESCRIPTION_LANGS` (the window's and the world's widely
written languages) even with every name language: one description per item is kept anyway.

**When the endpoint says no** (`fetch_query`). QLever reports a query that ran out of time as HTTP
429 — the same code as "too many requests" — with the timeout in the body. Asking the same query
again only times out again, so the two are told apart (`classify`): a **timeout** (429/503/504 whose
body says so) switches that query to pages — `ORDER BY` every projected variable, `LIMIT 250000
OFFSET …` — and halves the page on each further timeout, from the same offset (the pages already
written stay), giving up below 5 000 rows; a **slow down** (any other 429/503) waits its `Retry-After`,
else 60 s × the attempt, and asks again; anything else (a 400, a 404) fails at once. Every request has
at most 8 failed attempts (`WD_PAGE`, `WD_MIN_PAGE`, `WD_RETRIES`).

## Rebuilding and re-pinning

```bash
mkdir -p /tmp/gaz && cd /tmp/gaz                    # the downloads: NOT inside the repo
UA='Histmersion/<version> (gazetteer import)'
curl -fsSL -A "$UA" -O https://download.geonames.org/export/dump/allCountries.zip
curl -fsSL -A "$UA" -O https://download.geonames.org/export/dump/alternateNamesV2.zip
# a bare output name goes to the cache directory the server reads (~/.cache/histmersion/gazetteer); --out DIR elsewhere
# the whole world in every language by default; --bbox window --langs window (or any box / languages) for a regional one
python3 -I <repo>/db/gazetteer/build.py geonames allCountries.zip alternateNamesV2.zip geonames-<date>.ndjson.gz   # ~1 min
python3 -I <repo>/db/gazetteer/build.py wikidata-fetch https://qlever.dev/api/wikidata wd                          # ~10 min, resumable
python3 -I <repo>/db/gazetteer/build.py wikidata wd wikidata-<date>.ndjson.gz --periodo p0d.json                    # ~1 min; p0d.json: PeriodO, zstd -d
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

**A point that stands for an area takes findings only explicitly** (2026-10-09). A seed region,
polity or people known only by a point (`hasBoundary` false), and any Pleiades point on an atlas
grid square's centre (`location_precision = 'grid-cell'`), gets a finding only when the geocoder
resolves the finding's place name to it, or when the finding sits on its very point (not for a grid
square's centre, which Pleiades gives several places at once: only the name decides there) — never
by the point tolerance: by distance each of the 13 Regiones of Rome had taken the same 59 findings, and
the Athenian tribe Aiantis 108. A shape still holds what it covers, and a site or feature point
keeps its tolerance. `lint --rule place-kind` lists what such a "region" really is (a people, a
province, a settlement, a withdrawn duplicate) and `lint --rule place-grid-point` a better point
than a grid square's centre; a record's own link keeps its class, point and dates for them
(`place_links.item_*`, V077).

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

## An area known only by a point is no place (2026-10-09)

A region is a shape. GeoNames and Wikidata give every item one coordinate and no boundary, so an
administrative region, a historical region or a state from either is a DOT: drawn as one (or as a
"no boundary yet" label), offered in name search beside the real thing ("Republic of Turkey",
"Roman Republic"), and holding findings only by the accident of a shared point. On live
(2026-10-09) they were 234 504 of the 1.28 M places — GeoNames 197 798 regions and 89 polities,
Wikidata 36 617 regions and polities (5 497 + 943 dated, 6 294 + 29 with only an end) — with
1.06 M names, 259 k links, 16 k descriptions and 234 k footprints, and 37 of them held 146 finding
links, every one of those findings also at another place.

**The importer** (`places.sources.point-area-kinds`, default `region,polity,people`): a GeoNames or
Wikidata record of one of those kinds whose geometry is only a point (or none) never becomes a
place of its own. It still **joins** a place its identifiers name — a seed region gets its names,
links, description and its own link's class, point and dates (`place_links.item_*`, V077) exactly
as before; only when it would CREATE a place (or keep the one an earlier import made) is it dropped,
with every row it wrote. A real run first removes the source's places of that shape (`prune`, in
transactions of `batch`), so no record joins a place a later batch would remove; the load reports
"N place(s) removed" and "N point-only region(s)/polity(ies) not kept as places". A place an
attestation or an asset link names is kept: that is a person's evidence. `""` keeps every record.
An LPF dataset is not touched — a scholarly dataset's point is its own statement.

**The rows stored before** go with `lint --rule place-imported-point-area`: it lists the counts by
source, kind and dating (dated, end-only, undated), every such place that holds a finding — and how
many of those findings it is the ONLY place of (none, on live) — then a sample. `--fix` deletes
them with everything that hangs off them (finding matches, names, links, descriptions, types,
footprints, relations, aliases; an event would lose its place), `point-area-fix-batch` (2 000)
places per transaction for at most `point-area-fix-seconds` (20) per call; the CLI calls again
until none is left. Each transaction matches the findings of the affected sources again — those the
places held, and those whose place name reads as one of the places' names and as an explicit-only
seed region's (the geocoder had chosen the imported namesake; it now chooses the seed region) —
never the whole corpus. On a restore of the 2026-10-09 15:00 backup (V077, `places rebuild` first,
as live): 234 504 places in 8 calls of ≤ 26 s, 2 min 36 s in all; `finding_places` lost the 146
rows and gained 7 (Thracia ×4, England, Bohemia, Ossetia now win their names), and a full
`places rebuild` afterwards gives exactly the same rows; no finding lost its last place (2 315
located findings placed before and after).

| table | rows before | rows after | size before | after `VACUUM FULL` |
|---|---:|---:|---:|---:|
| `places` | 1 282 160 | 1 047 656 | 1 134 MB | 1 005 MB |
| `place_names` | 8 358 254 | 7 300 553 | 2 430 MB | 2 109 MB |
| `place_links` | 1 767 896 | 1 509 368 | 431 MB | 378 MB |
| `place_footprints` | 1 280 526 | 1 046 022 | 372 MB | 309 MB |
| `place_descriptions` | 272 676 | 256 337 | 67 MB | 63 MB |
| `finding_places` | 128 513 | 128 374 | — | — |

The database went from 7 289 MB to 6 696 MB after `VACUUM FULL` (a plain `VACUUM` only makes the
space reusable). The interactive budgets hold before and after (`scripts/perf/bench.py`, the same copy).

A re-import without the fix removes them too (`prune`, then the batches): on the same copy
`places load wikidata --apply` took 4 min 29 s (36 617 places removed, 393 new), then `geonames
--apply` 3 min 0 s (197 887 removed, 2 389 new — GeoNames sites that had joined a Wikidata point
region are places of their own now), leaving no point-only area and the same 128 374 finding
links. The two sources' joins settle after ONE more round: a second `wikidata --apply` lets 2 785
Wikidata areas join the GeoNames places the first round made (by a shared GeoNames id; +52 760
names), a second `geonames --apply` adds 613 names, and a third round of dry runs changes nothing.
So after the fix (or instead of it), run `places load wikidata --apply`, `geonames --apply`, and
both once more.

## What an import costs

**The world extracts** (2026-10-27, a laptop, a `*_test` database holding only the bundled seed —
46 s and 234 MB on first start — imported in the order geonames, wikidata, tgn):

| step | first run | database |
|---|---:|---:|
| `places load geonames --apply` | 8 min 55 s — 1 062 297 new places, 7 521 joined, names +3 531 835, links +1 276 702, 543 739 point-only regions/polities not kept | 0.23 → 3.60 GB |
| `places load wikidata --apply` | 26 min 36 s — 656 615 new places, 417 176 joined, names +13 477 610, links +1 263 385, descriptions +1 116 849, 62 335 point-only areas not kept | 3.40 → 9.79 GB |
| `places load tgn --apply` | 35 s — 37 115 new places, 1 460 joined, names +170 020 | 9.79 → 9.90 GB |

After all three: 1 801 992 places, 17 533 239 names, 2 628 032 links; `place_names` is 6.2 GB with its
indexes, `places` 1.4 GB, `place_links` 0.9 GB, `place_footprints` 0.6 GB, `place_descriptions`
0.35 GB. `GET /api/places?q=` stays at 8–84 ms (Machu Picchu, Great Zimbabwe, Anyang, Angkor,
Cahokia). Most of the growth is the names in every language; `--langs` is the lever for a small
install. (Recommended order remains wikidata, geonames, tgn: GeoNames records then join by the
Wikidata cross-links; in the order above Wikidata joined 417 176 GeoNames places instead.)

**The window extracts** (2026-10-08, a laptop, an empty corpus):

| step | first run | re-run (same versions) |
|---|---:|---:|
| `places load wikidata --apply` | ~11 min — 753 382 new places, 13 239 joined seed places | ~80 s, nothing changed |
| `places load geonames --apply` | ~9.5 min — 474 477 new places, 250 619 joined | ~45 s, nothing changed |
| `places load lpf --apply` | seconds per WHG-sized file | seconds |

After all three the gazetteer held 1.27 M places, 8.34 M names and 1.76 M links, and the database
grew by ~4.9 GB (trigram and name-key indexes included). Each real run that changes something
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

## Getty TGN (#1403) — pinned 2026-10-27 (dump of 2026-10-18)

The Getty Thesaurus of Geographic Names (ODC-By 1.0: "Contains information from the Getty Thesaurus
of Geographic Names (TGN)®, which is made available under the ODC Attribution License") is a third
pinned source, `tgn`, beside GeoNames and Wikidata: `histmersion places load tgn [--apply]`. Like them
it is joined **by shared identifiers only** — a TGN record has no links of its own, so it joins the
place that already carries its `tgn:` id (the Wikidata extract gives 38 527 items in the world one,
from P1667; 25 753 in the window) and otherwise becomes `tgn_<id>`; it ranks after Wikidata and before GeoNames, and its
places follow the imported-source rules (undated = unknown, a point-only region or state is no place,
not in the hierarchy, drawn only with a finding). Run it after `wikidata`. V093 admits the kind and
rebuilds the partial indexes whose predicates name the imported sources.

**Pinned on 2026-10-27** from the 2026-10-18 export, the whole world: `tgn-2026-10-18.ndjson.gz`, 3.1 MB,
39 906 places, 179 787 names (9 245 places dated, 11 357 described; 2 975 412 located subjects read).
Until then the window extract of the 2026-01-04 export: 2.2 MB, 32 383 places, 112 632 names. TGN marks
few places outside Europe and the Near East as historical, so the world adds 7 398 places, not more: the
keep rule is by type and name, not by region. To rebuild or re-pin (~1.3 GB of downloads; the
dumps are read in place, never unpacked):

```bash
mkdir -p /tmp/tgn && cd /tmp/tgn                    # NOT inside the repo; delete afterwards
curl -fsSL -A "$UA" -O http://tgndownloads.getty.edu/VocabData/explicit.zip          # 1.15 GB, public, no login
curl -fsSL -A "$UA" -o aat-explicit.zip http://aatdownloads.getty.edu/VocabData/explicit.zip   # 141 MB (place-type labels)
python3 -I <repo>/db/gazetteer/build.py tgn explicit.zip aat-explicit.zip tgn-<date>.ndjson.gz
python3 -I <repo>/db/gazetteer/build.py pin <repo>/db/gazetteer/extracts.json tgn tgn-<date>.ndjson.gz version=<date> built=<today>
```

`<date>` is the dump's: the 2026-10-18 export (`Last-Modified: Sun, 18 Oct 2026 21:53:12 GMT`, 1 163 917 500
bytes, sha256 `d007a6a267432c0e197b714cad706ab5f993ebb34f61ec22e0375b1d56bf893a`; AAT of 2026-10-17,
`8330c9f3015d068a9d3fafc504d3711e76b952824dbedbeb8119955f46a764e5`) was the current one on 2026-10-27
(before: the 2026-01-04 export, sha256 `36cba1c4fbd0ee07c212253dc439610f04935291eb9bf84cf87c45a8e0e086ac`).
Getty's server sent it at about 14 MB a minute: `curl -C -` resumes a cut download.

**What the export holds** (measured on that dump): 2 991 143 subjects — 2 075 701 `AdminPlaceConcept`,
915 309 `PhysPlaceConcept`, 55 `PhysAdminPlaceConcept`, plus guide terms and facets — and 2 972 719
coordinates. The members `build.py` reads: `TGNOut_Coordinates.nt` (`<tgn/X-place>` `wgs84:lat/long`),
`TGNOut_1Subjects.nt` (the class), `TGNOut_PlaceTypes.nt` (`gvp:placeTypePreferred`, an AAT concept),
`TGNOut_2Terms.nt` (the `xl:prefLabel`/`xl:altLabel` links AND the terms: `xl:literalForm` with its
language tag, `gvp:estStart`/`estEnd`, `gvp:historicFlag`), `TGNOut_ScopeNotes.nt` (27 852 notes and
their links); AAT's `AATOut_2Terms.nt` gives the place types' English labels (`gvp:prefLabelGVP`).

**Filters.** The scope (`--bbox`, the world by default); a place class (no facets, no guide terms); then kept when its place
type is historical or archaeological (`TGN_HISTORICAL_TYPE`: ancient, archaeological, deserted,
abandoned, former, historical, ruins, necropolis, tell, tumulus, hillfort, oppidum, colony, …) or when
it has a historic, dated or historical-language name — the GeoNames rule, so a present-day town known
only by its current names adds nothing. Names in the extract's languages; a `-Latn` transliteration
(`el-Latn`) is kept with no language, like GeoNames' bare variants. Getty's estimated dates are BCE
negative in historical reckoning (its own data, not XSD 1.1's year zero) and 9999 / -9999 mean "the
present" / "unknown": such a bound is OPEN. Kind: a physical place is a `feature`; a place type naming
a state (nation, empire, kingdom, …) a `polity`, one naming an administrative area a `region`;
everything else a `site`. Every TGN place is `modern: true` (undated), as GeoNames'.
