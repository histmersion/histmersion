# Interactive performance: hover, type, filter, drag (2026-10-09)

The requirement: anyone hovering, typing into a filter or hovering over a hint gets the lists,
articles, events and the map updated in real time. The corpus is local, single-user, on an SSD, so
a slow answer is an inefficiency in the protocol or the schema, not in the hardware.

This note is the inventory of what the web UI asks for on each interaction, the benchmark that
replays it, the root causes of every answer that was over budget, and what changed. Numbers come
from a restore of the newest backup (2026-10-09, 15 GB: 383 sources, 2 356 findings, 1.28 M places,
8.4 M place names) in a private Postgres 18.4, with this branch's server against it — never the live
server.

## Budgets

| interaction | budget (server p95) |
|---|---|
| hover card (place, source, article), a suggestion | ≤ 50 ms (≤ 100 ms perceived) |
| a list's first page after a filter change | ≤ 150 ms |
| the map's layers at a new year | ≤ 150 ms |
| a timeline drag | no request storm; the newest request wins |

## Inventory: what each interaction fires

Payloads are the median of the benchmark run (after places-fix; JSON, uncompressed, over loopback).
"Newest wins" means a response for an older request is dropped by a request counter; nothing is
aborted (the server could not cancel the query anyway, see *Remaining*).

| interaction | endpoint(s) and parameters | panels refreshed | order | debounce / throttle | caching | payload |
|---|---|---|---|---|---|---|
| hover a place on the map (polity, site, chosen outline) | `GET /api/places/{id}?asOf=<map mid-year>&counts=true` | the floating card | one | 180 ms hover intent (`Layout.hoverDelayMs`) | per `id@asOf`, for the page's life | 1.9 KB |
| hover a source link (`data-ck`) | `GET /api/sources/{ck}?body=false` | card | one | none (delegated `pointerover`) | per citekey; dropped on edit | 8 KB |
| hover an article link (`data-slug`) | `GET /api/articles/{slug}?brief=true` (was: the full read with lint and coverage) | card | one | none | per slug | 38 KB |
| bare identifier in text (`IdentLink`) | `GET /api/resolve/{id}` | the link label | one | none | per id | < 1 KB |
| type in the place box | `GET /api/places?q=<text>&asOf=<span mid>&limit=12` | suggestion menu | one per settled keystroke | 150 ms (`ui.debounce.suggest-ms`) | none | 3.4 KB |
| hover a place suggestion | `GET /api/places/{id}?asOf=` (outline, map fits to it) | map overlay | one | none | none | 2.5 KB |
| type in tag / author / topic / status | `GET /api/facets?field=<f>&prefix=<text>&<filter without f>` | suggestion menu | one | 150 ms | none | 1.3 KB |
| hover a tag/author/topic suggestion | the **filter bundle** below, with the value added as a preview | map, sources, strip, visible tab | see bundle | 60 ms (`ui.debounce.preview-ms`, new; was 250 ms) | none | — |
| commit a filter (chip, search box, title, "in view" bbox) | **filter bundle**: `GET /api/findings?<filter>` + `GET /api/sources?<filter>&limit=20` (+ `findings` without bbox for the in-view total) + `GET /api/aggregate?by=time&unit=sources&<filter>&size=<px>` (detail strip) + `GET /api/aggregate?…&from=min&to=max` (overview, when a non-time filter changed) + the visible tab: `GET /api/articles?<filter>&limit=200` or `GET /api/events?…&limit=200` or `GET /api/aggregate?by=time|region&depth&samples` (tree tabs) | map markers, list, both strips, the visible tab | findings, sources and total **in parallel** (was: sources only after findings); strips and tab in parallel | lists 250 ms (`reload-ms`), strip 150 ms, tabs 150 ms; overview newest-only (was: none) | client keeps the findings of the current non-time filter for time-only redraws | findings 58 KB, sources 138 KB, aggregate 38–44 KB, articles 27 KB |
| choose a place (map click, picker) | the filter bundle with `place=<id>` + `GET /api/places/{id}?asOf=` (outline) | as above + outline | parallel | as above | — | as above |
| map pan / zoom | nothing, unless "only sources in view": then the filter bundle with the bbox | as above | — | 250 ms after `moveend` (`viewport-debounce-ms`) | — | — |
| map layers at a year (span changes) | `GET /api/places/geo?kind=polity&asOf=<mid>&limit=400`, `…kind=site…&limit=200`, `GET /api/media-geo?from&to&limit=200` (each when its layer is on) | overlays | parallel | throttled 200 ms (`Layout.layerReloadMs`) | per `(kind, year)`, 48 entries | polity 90 KB (-1200) – 2.2 MB (1500 CE), sites 44 KB |
| timeline drag (handles, pan, zoom brush) | during the drag: no request — markers re-filtered from the cached findings; the detail strip after 150 ms quiet; on release the filter bundle (lists after 500 ms) | strip, markers, then lists | — | 150 ms / 500 ms | cached findings | — |
| timeline playback | `GET /api/findings?<filter>&from=ph−50&to=ph+50` per frame, + the layers (throttled) | markers, overlays | **one in flight, newest frame next** (was: one per animation frame, ~60/s, all drawn in arrival order) | — | — | 4–60 KB |

Static: `GET /api/config` once at start; the period presets once, on opening the menu.

## The benchmark

`scripts/perf/bench.py` replays these interactions against a running server. Everything it asks
for is drawn from the data through the API itself — place ids from the places facet and from the
polity layer at eight years, citekeys and slugs from the lists, tag/author/topic values from the
facets (typed one prefix at a time), years across the span — so it runs against any corpus without
database credentials. It reports p50/p95/max and the median payload per endpoint and per
interaction; an interaction's time is the wall time of its bundle, fired the way the client fires it.

```bash
scripts/perf/bench.py --url http://127.0.0.1:18681 -n 8 --json after.json   # a private server — never the live one in use
scripts/perf/bench.py --compare before.json after.json                     # the tables below
scripts/perf/bench.py --url … --check --slack 2                            # exit 1 over budget
scripts/perf/bench.py --url … --sequential-lists                           # the client before this change
```

`InteractiveLatencySuite` (server, gated on `HISTMERSION_TEST_DB_URL`) is the CI form: the hover,
suggestion, chosen-place and map requests nine times each on the test database, the p50 held to
budget × 10. The helpers are unit-tested by `just test-scripts`.

## Results

**Against master with places-fix** (6fab16c, V074 and its `places rebuild` applied to the copy:
130 336 finding-place links instead of 341 162). "before" fires the lists sequentially, as that
client did; shared buffers 128 MB (the live cluster's) before, the bundle's new `auto` (2 GB on this
48 GB machine) after. With 128 MB after, every interaction is still within budget; the p50s are
10–60 % higher.

| interaction | before p50 | before p95 | after p50 | after p95 |
|---|---:|---:|---:|---:|
| `hover.place` | 8.0 | 52 | 6.0 | 8.3 |
| `hover.article` | 20 | 167 | 5.8 | 6.0 |
| `hover.source` | 3.5 | 12 | 3.3 | 7.1 |
| `suggest.place` | 154 | 4 823 | 2.9 | 19 |
| `suggest.tag` / `.author` / `.topic` | 6.3 / 3.2 / 2.6 | 8.3 / 5.1 / 3.1 | 5.2 / 2.4 / 2.0 | 6.0 / 3.1 / 2.4 |
| `suggest.hover` (preview bundle) | 12 | 58 | 7.4 | 35 |
| `filter.change` | 39 | 60 | 22 | 54 |
| `place.chosen` | 38 | 61 | 18 | 52 |
| `map.year` | 881 | 15 989 | 48 | 68 |
| `timeline.drag` | 5.1 | 7.4 | 3.2 | 3.6 |
| `playback.frame` | 6.0 | 11 | 2.6 | 4.2 |

| endpoint | before p50 | before p95 | after p50 | after p95 |
|---|---:|---:|---:|---:|
| `places/{id}?counts=true` | 8.0 | 52 | 6.0 | 8.3 |
| `places?q=` | 154 | 4 823 | 2.9 | 19 |
| `places/geo` polity | 190 | 467 | 20 | 24 |
| `places/geo` site | 880 | 15 988 | 48 | 68 |
| `articles/{slug}` (hover: `brief`) | 20 | 167 | 5.8 | 5.9 |
| `articles?<filter>` | 9.2 | 34 | 3.3 | 17 |
| `events?<filter>` | 3.1 | 8.2 | 6.3 | 7.9 |
| `sources?<filter>` | 23 | 52 | 18 | 52 |
| `findings?<filter>` | 5.8 | 14 | 3.2 | 6.2 |
| `aggregate` detail / overview | 5.5 / 6.3 | 27 / 27 | 4.7 / 5.1 | 10 / 10 |

**Against master before places-fix** (d8210b6, the copy as restored), the same harness:
`hover.place` p50 1 696 / p95 9 012 ms, `place.chosen` 1 079 / 9 467, `suggest.place` 143 / 14 171
(max > 120 s), the sites layer did not answer within 60 s at any year. This branch on that data,
before the sites change: `hover.place` 19 / 41, `place.chosen` 18 / 59, `suggest.place` 6.6 / 28.

## With the time rule (time-views, merged 2026-10-09)

The time rule (`core.PlaceTimeBasis`, `PlaceRepo.inTimeSql`) changed what a view at a year shows: an
undated place only on its findings, a present-day place only from `places.modern-from`. Merged as
written, its window came from a CTE (`tw`) whose value the planner cannot see, so the rule's leading
overlap was estimated at 13 000 rows and `places_period_gist` — 1.19 M matches, since every undated
import overlaps every year — was AND'ed into every candidate scan: sites 79–121 ms a year, polities
91–128, a place suggestion before 1500 up to 67 ms at p95. What changed:

- the overlap names the year as a literal (`period && [y, y]`), so the planner knows it keeps nearly
  everything and narrows by what does narrow: the trigram index of a search, the layer's own index;
- V076 adds four partial indexes: `places_base_layer_time` and `places_dated_import`
  (`gist (kind, period)` over the curated places and over the DATED imports — ~600 of 77 000 alive in
  a year), `places_before_modern` (id) and `places_before_modern_label_trgm` (the 132 000 places a
  view before `modern-from` can show at all);
- a map layer's imported half at a year states `PlaceRepo.DatedImportSql` and probes each dated
  import for a finding (a fenced `EXISTS`) instead of reading all 9 500 imported places with one;
  the limit is taken over `live` before its rows are joined back to `places`;
- a place search before `modern-from` states `PlaceRepo.ShownBeforeModernSql` in each pass (implied
  by the rule there). The rule hides more prefix hits, so the candidate pass runs more often than
  before it; that pass is what the partial indexes pay for.

Map layers per year (ms, median of 7 warm requests, the same copy and memory settings):

| layer | −2500 | −1500 | 500 | 1200 | 1600 |
|---|---:|---:|---:|---:|---:|
| polity, master | 42 | 42 | 49 | 55 | 67 |
| polity, perf alone | 18 | 20 | 29 | 35 | 42 |
| polity, perf + time rule | 3.6 | 4.7 | 12 | 19 | 28 |
| sites, master | 416 | 504 | 10 188 | 2 763 | 2 628 |
| sites, perf alone | 45 | 47 | 58 | 52 | 52 |
| sites, perf + time rule | 11 | 14 | 44 | 32 | 48 |

`InteractiveQueriesSuite` holds the merged statements to the time rule: its references are the
time-views single statements (window CTE, `inTimeSql`, the `basis` column and its ranking), with a
present-day Wikidata place, an undated site dated by its findings, a feature and the years −850 and
1600 among the fixtures.

## Root causes, and what changed

1. **Generic plans of prepared statements** (all endpoints; the worst single cause). pgjdbc
   server-prepares a statement on its fifth use in a pooled connection; Postgres then switches to a
   generic plan that does not see the values: `ILIKE ?` becomes `like_escape($n)` with no trigram
   index, `= ANY(?::text[])` an array of unknown size. The sixth hover of a place took 1.5 s instead
   of 17 ms, a repeated place search minutes. → `plan_cache_mode = force_custom_plan`, a database
   setting re-applied at each start beside `random_page_cost` (`Db.databaseSettings`).
2. **Correlated `EXISTS` re-run per row** (place filters, hover counts, the Articles and Events
   tabs). A place id's scope (itself and its descendants) was `EXISTS (… f.citekey = s.citekey AND
   fp.place_id IN (?, ?, …))`, re-evaluated per source — and inside the Articles list's
   `al.target IN (…)`, per article *link* (1 955 times; 1 284 index searches each for a polity of 451
   descendants): 15 s for one article count. → the id scope is an uncorrelated semi-join with the ids
   as one `text[]`; the Articles and Events lists test membership in `ARRAY(source set)`, an
   init-plan computed once. The hover card's four counts are one statement, not four checkouts plus
   a page of articles fetched for its total. (places-fix removed the id-as-name ILIKE that was OR'ed
   into the same predicate — the other half of the 15 s.)
3. **Decorating before ranking** (place search). Every keystroke computed the alternate-name
   laterals for every candidate — 23 800 for "Ach" — and found them by a merge join over all
   1.2 M places. → rank on the row and its finding count, decorate the 12 rows shown; a label-prefix
   pass answers alone when it already has 12 rows (an exact or prefix hit outranks every other).
4. **Planner mis-estimates on small CTEs** (map layers). The polity de-duplication joined its few
   dozen live shapes to `places` by key, estimated at thousands: a merge join over 1.2 M rows, 200 of
   240 ms. The sites layer read all 930 000 sites alive in a year (nearly all GeoNames/Wikidata) to
   keep 6 400, then paired those 6 400 with each other (40 M pairs) before testing that a pair spans
   two source tiers — all sites are one tier. → shapes fetched by id per candidate pair; the base-layer
   predicate read in its two disjoint halves, the curated one through a partial index (V075
   `places_base_layer`); each side of the pairing keeps only the tiers that can be in a pair.
5. **Work per read the reader does not see** (article hover). The article read runs the formatting
   lint and computes the coverage every time; a preview shows title and summary. →
   `GET /api/articles/{slug}?brief=true`; `/api/resolve/{id}` reads the same way.
6. **Client request storms and serial chains.** Playback: a findings request per animation frame,
   every answer drawn in arrival order. The overview strip: one request per filter change and per
   hovered suggestion, undebounced. The detail strip: refetched on every UiState commit (a layer
   toggle, a window, a tab). The list refresh: sources asked for only after the findings arrived. A
   hidden By time / By region tab kept reloading. A hovered suggestion waited the 250 ms typing
   debounce. → `LatestOnly` (one in flight, newest next) for playback and the overview; the detail
   strip keyed on its filter and span; findings, sources and the in-view total in parallel; the tree
   tab told when it is hidden; `ui.debounce.preview-ms = 60` for previews.
7. **Postgres memory at initdb's defaults** (shared_buffers 128 MB, work_mem 4 MB) beside a 15 GB
   database. → the bundle starts its Postgres with `-c shared_buffers` (`auto`: RAM/16 within
   128 MB…2 GB), `effective_cache_size` (`auto`: RAM/2) and `work_mem` 16 MB
   (`runtime.postgres.*`). Takes effect at the next restart of the server (the supervisor restarts
   its Postgres with it); nothing is written into the cluster.

Checked and **not** a cause: `random_page_cost` is already 1.1 per database (V023); the bundled
Postgres has no LLVM, so JIT never runs; `effective_io_concurrency` is 16 with `io_method=worker`
(Postgres 18 on macOS); keep-alive is on (netty, browser); HikariCP's 8 connections are enough once
no statement holds one for seconds — under the old plans a few stuck sites-layer queries could hold
the whole pool.

Every query rewrite is checked for identical answers: `InteractiveQueriesSuite` compares each with
the old statement kept verbatim (search, map layers) or with the rule computed the plain way (the
related lists), over fixtures in the shared test database; and 220 interactive GETs plus 28
layer × year requests returned byte-identical bodies from master and this branch on the live-sized
copy (master's own search for "Ach" did not answer within 60 s, so those four could not be compared).

## Remaining, and why

- **Compression**: not added. Over loopback a 140 KB sources page or a 2 MB polity layer moves in
  well under a millisecond; gzip would spend CPU on both ends for nothing. Worth it only if the UI is
  served over a LAN (`HISTMERSION_BIND`) — a netty `HttpContentCompressor` in `Main`'s pipeline then.
- **Payload size**: the sources page carries, per matched finding, the labels of every place it was
  materialised into (`FindingMatch.places`; 15 KB per finding before places-fix pruned the imported
  links, still the bulk of a 140 KB page). The web UI does not show them; the CLI and MCP do. A
  `brief` list read like the article's would cut it.
- **The polity layer at late years** is 1.5–2.2 MB of GeoJSON (Cliopatria's states at 1200–1500 CE);
  the server answers in ~25 ms, the browser then parses and draws it. Simplifying shapes to the
  map's zoom is a separate piece of work.
- **First read of a cold word** in a text query still pays its pages' first read (70–380 ms for a
  word nobody searched since the restart); warm, 40–65 ms. The larger shared buffers keep them once read.
- **No cancellation**: a request the browser no longer wants still runs to the end in Postgres
  (netty-sync has no cancel path to JDBC). Fast statements make this harmless; a statement timeout
  for interactive reads would bound the worst case.
- **The place-suggestion outline** (`PlacesInput.preview`) draws whatever answer arrives last, not
  the newest hover's — left alone here because places-fix rewrote those lines; a `previewId == id`
  check on arrival fixes it.
- **Events** with a tag filter: 3 → 7 ms — the related list now computes the whole source set once
  instead of probing it per event; the price of the shape that took the polity case from seconds to
  milliseconds.
