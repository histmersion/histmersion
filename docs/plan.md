# hist-db — plan (discovery UI · filters · full-text · gazetteer · images · quality · packaging)

Status: **grilled and approved 2026-08-25**. Single-user, local, private repo → **no release versioning**:
work lands on `master` in dependency order (waves below); "done" = acceptance criteria pass on this machine.

## 0. Where we are

Working end-to-end (commit `7104e82`): Postgres/PostGIS store (209 sources · 964 findings · 1066
typed edges · 84 MB OCR text), MCP (5 tools) + REST + Leaflet atlas + CLI, both GraalVM native
binaries, tracing-agent metadata auto-collected on forked `run`.

Known debts found while surveying for this plan (all handled below):

| debt | where |
|---|---|
| Ports/URLs hardcoded: `8000` (Main, `.mcp.json`, README), `5432`/`8100` (compose), tile/CDN URLs | §1.9 |
| Stray `agent-pid*.tmp*/` dirs committed under `modules/server/src/main/resources/META-INF/native-image/` (agent killed mid-write) | §2.0 |
| `Http` and `Main` do side effects in object init (`private val ds = Db.fromConfig()`); repos are fine (take `ds`) but the composition root is implicit | §2.2 |
| `search` = `ILIKE` over `ocr_markdown` (seq scan of 84 MB); FTS index excludes the body (tsvector 1 MB limit) | §1.4 |
| `SearchInput.query` optional, no other filters; CLI `search` hardwires `limit=20`; `geo-when` in CLI has no bbox | §1.3 |
| No tests, no AGENTS.md, README stale (mentions Doobie/http4s, 4 tools) | §2 |
| Leaflet from unpkg CDN, OSM tiles — UI is not usable offline / behind a firewall | §1.2 |
| `index.html` is a 154-line monolith → parallel UI work will conflict on one file | §3 phase 0 |

## 1. Features

Design principle for every feature: **one `Filter` model in `core`, used by REST, MCP and CLI**, so
the three surfaces agree by construction (same idea as Kindlings codec+schema).

### 1.0 Foundation: the `Filter` contract (prerequisite for 1.1–1.6)

```scala
final case class Filter(
  q:        Option[String]       = None,      // full-text query (websearch syntax)
  tags:     List[String]         = Nil,       // AND semantics (all required)
  authors:  List[String]         = Nil,       // any-of, ILIKE
  places:   List[PlaceId]        = Nil,       // gazetteer ids (§1.6a); resolved from names by the UI/CLI
  topics:   List[String]         = Nil,
  status:   List[SourceStatus]   = Nil,
  years:    Option[Period]       = None,      // publication year range
  period:   Option[Period]       = None,      // deep-time overlap (signed years)
  bbox:     Option[BBox]         = None,      // viewport
  sort:     Sort                 = Sort.Relevance, // Relevance | Cited | PeriodStart | Year | Title
  limit:    Int, offset: Int
) derives KindlingsCodecAsObject, ConfigReader-free
```

- **The source (paper/book) is the unit of every result** (decided 2026-08-25). Findings are the
  *evidence* that made a source match: a source matches `bbox`/`period`/`places` if its own `period`
  overlaps **or any of its findings** do; `tags` matches `sources.tags ∪ findings.tags`. Every count —
  search total, facets, timeline histogram, time/region tree — is `count(distinct source)`. The map
  still draws findings (`/api/findings` remains the map feed only) and the sidebar lists **sources**.
- **Every hit carries its matches**: `Hit(source, matched: List[FindingMatch], why: List[String])`
  where `matched` = the findings that satisfied the filter (claim, place, period, which criteria hit)
  and `why` = source-level reasons ("title matches 'collapse'", "tag: sea-peoples"). Shown under each
  result in the UI, in MCP Markdown (a `matches:` list per source with the snippet), and in the CLI —
  so a human or an LLM can judge relevance without opening the source.
- `Filter` → SQL is a **pure function** returning `(whereFragment, params)`; unit-tested without a DB.
- REST: `GET /api/sources?…` and `GET /api/findings?…` take the same query params (Tapir
  `mapInTo[Filter]`). MCP `search` and `geo_when` inputs *are* `Filter` (or a slim projection).
  CLI: `histdb search [--tag t]* [--author a]* [--region r]* [--from -3300 --to -1200] [--bbox …] [--sort cited] [--limit n] [query]`.
- Defaults (limits, default time window) come from config (§1.9), not literals.

### 1.1 Viewport toggle ("search this area", Google-Maps style)

- Checkbox **"only papers in view"** in the header; when on, every `moveend`/`zoomend` (debounced)
  sends `bbox=map.getBounds()`; when off, bbox is omitted. State persisted in the URL hash so links are
  shareable (`#bbox=…&from=…&to=…&tags=…`).
- Server side already supports bbox (`ST_MakeEnvelope && geom`); add antimeridian handling
  (split into two envelopes when `minLon > maxLon`) — unit-test that.
- Results list and clusters both reflect the viewport filter; a small "N sources · K findings in view /
  M total" counter.

### 1.2 Clustering of co-located points

- **Vendor Leaflet + Leaflet.markercluster** into `web/vendor/` (no CDN; works offline; needed for the
  Docker image anyway). Tile URL becomes config (§1.9) with a documented offline fallback (no tiles →
  plain graticule).
- **Rule (standard map-app behaviour):** at a given zoom, markers whose drawn circles **overlap on
  screen** (pixel test: centre distance < 2 × marker radius, i.e. `maxClusterRadius` = marker
  diameter, not the 80 px default) are joined into **one dot with the count**; clicking it opens a
  **picker** (list of the N findings: citekey · claim · period) to choose one → article modal.
  Zooming in re-separates them as soon as they stop overlapping (no spiderfy, ever).
  **Exactly identical coordinates** (same lon/lat) are **always** merged at every zoom, and always
  need the click-to-pick. Cluster colour = period-midpoint ramp of its members (keep the current
  viridis encoding).
  *Acceptance:* two findings 0.5° apart are separate at zoom ≥ 7 and one dot at zoom 4; two findings
  with equal coordinates are one dot at zoom 12 and clicking lists both.
- Server returns points as now; clustering is client-side (≤ ~10k points is fine). If the corpus grows
  past that, add `/api/findings?cluster=z` server-side (`ST_SnapToGrid` + `ST_ClusterDBSCAN`) — noted,
  not built now.

### 1.3 Filtering by tags / authors / regions / topics / status (UI + CLI + MCP)

- Backed by `Filter` (1.0). UI: a **filter bar** with chip inputs (1.5) for tags/authors/regions/topics,
  status multi-select, publication-year range.
- CLI/MCP get the same parameters. MCP tool descriptions list the **facet vocabulary** hint ("use
  `facets` to see available tags").
- New `facets` capability (REST `/api/facets?field=tags&prefix=in&filter=…`, MCP `facets`, CLI
  `histdb facets tags [--prefix in]`): returns `(value, count)` for tags/authors/places/topics/status,
  **counted as distinct sources within the current filter** (so chips show how many sources each
  would leave). SQL: `SELECT t, count(DISTINCT citekey) FROM (sources.tags ∪ findings.tags) t WHERE
  <filter> GROUP BY t ORDER BY count DESC`.

### 1.4 Full-text search (UI + CLI + MCP)

- Replace `ILIKE` with Postgres FTS over **chunked bodies** (tsvector's 1 MB limit is per row, so
  chunk): new table `source_chunks(citekey, ord, heading, body, tsv tsvector GENERATED)` with a GIN
  index; chunks split on headings / ~4 k chars at import (pure function, tested). `title+summary`
  keep their own tsvector column on `sources` (weighted A/B; body chunks weight C/D).
- Query via `websearch_to_tsquery('english', $q)` (supports `"phrase"`, `-not`, `or`). Rank with
  `ts_rank_cd`; **snippets** via `ts_headline` on the best-matching chunk → shown in the results list,
  in MCP Markdown (`> …snippet…`), and in CLI output. This gives the LLM the *passage*, not just the
  citekey.
- Add `pg_trgm` + GIN trigram index on `title`/`authors` for typo-tolerant chip autocomplete (1.5).
- Also `GET /source/{ck}?q=…` highlights matches in the rendered article (`<mark>`), and the modal
  gets a "jump to next match" control — this is what makes "read from one paper to the next" work.

### 1.5 Tag autocomplete with removable chips (not a dropdown)

- One reusable vanilla-JS `chip-input` component (`web/chips.js`): type → suggestions from
  `/api/facets?field=…&prefix=…` (debounced, keyboard ↑↓⏎, Esc), pick → chip with ✕; chips are the
  `Filter.tags` list. Used for tags, authors, regions, topics. No framework, no build step (keeps the
  native-image/resource story trivial).

### 1.6 Sorting + nested grouping by time / region

- `GET /api/aggregate?by=time|region|time,region&…filter` returns a **tree**:
  `Bucket(label, period|region, count, children: List[Bucket], samples: List[Hit])`.
- **Time buckets are adaptive**: bucket size chosen from the span (≥ 20 k yrs → 5 k; ≥ 5 k → 1 k;
  ≥ 1 k → 100; else 10), and each bucket carries its **children at the next finer size** up to depth 3
  (lazy: deeper levels fetched on expand with the bucket as `period`). Pure function over rows
  `(findings, span) → tree`, unit-tested.
- **Region nesting is a gazetteer dimension, not a string** (WHG / Linked-Places model; aggregation
  levels as in MDX hierarchies). Formerly "Phase 3", now **§1.6a**, below.
- UI: results panel gets **"list | by time | by region"** tabs; tree rows are collapsible, each shows
  count + range, leaves are hits. CLI: `histdb search … --group time,region` prints the same tree as
  indented Markdown; MCP `search` gains `groupBy`.
- `sort=` (1.0) applies inside leaves.

#### 1.6a Gazetteer: places with geometry, hierarchy and temporal validity

Data model (`db/schema.sql`, seeded from `db/places/*.geojson`):

```sql
places          (id text PK, label text, kind text,           -- region|polity|site|feature
                 geom geometry(MultiPolygon|Point,4326),      -- polygon where known, point for sites
                 period int8range)                            -- when THIS shape/entity is valid
place_names     (place_id, name, lang, period)                -- alternate/period names ("Hatti", "Anatolia")
place_relations (child_id, parent_id, kind = 'part_of', period)  -- edges carry their OWN validity
```

- **Same name, different borders over time** = **different `places` rows** sharing a `place_names.name`
  (e.g. `hittite_kingdom_-1650_-1500` and `hittite_empire_-1400_-1180`), each with its own polygon
  and `period`. A relation `X part_of Y` is valid only for its own `period`.
- **Overlap is allowed**: a finding may fall inside several places at the same level (contested /
  overlapping polities). Counts are therefore **non-additive** (like a many-to-many MDX dimension):
  a parent's `count` is `count(distinct finding)` of its subtree, not the sum of children; the UI
  labels this ("12 findings, 3 in more than one subregion").
- **Assignment is computed, not typed**: `finding ∈ place` iff `ST_Within(f.geom, p.geom)` **and**
  `f.period && p.period` (both temporal ranges overlap). Materialised into `finding_places(finding_id,
  place_id)` on import / on gazetteer change (`histdb places rebuild`); findings without a point fall
  back to matching `region_name` against `place_names.name` (exact, case-insensitive), else bucket
  "unlocated". `findings.region_name` stays as the author's label, no longer used for grouping.
- **Aggregation by region** = walk `place_relations` **as of the query period** (recursive CTE with
  the period overlap predicate on each edge), levels = depth (world → macro-region → region →
  polity/sub-region → site). `by=time,region` nests the time buckets first, then, *inside each time
  bucket*, the gazetteer as it stood during that bucket — so the same finding can appear under
  "Hatti" in −1600…−1200 and under "Phrygia" in −800…−600.
- **Seed data** (no manual polygon drawing): modern regions/countries from Natural Earth
  (as stable top levels: continents → subregions), ancient sites as points from **Pleiades** (CC-BY,
  ~40 k places with coordinates and time-periods), and a small hand-curated set of Bronze/Iron-Age
  polity polygons for the corpus's core (Anatolia, Aegean, Levant, Mesopotamia, Egypt, Pontic steppe)
  traced from AWMC/open period maps into `db/places/*.geojson` — **in scope (decided
  2026-08-25)**: one polygon set per canonical period (EBA/MBA/LBA/Iron Age I–II) for those six
  areas, each polygon citing its map source in a `source` property; you review the GeoJSON before
  it is seeded. Import via
  `histdb places import <geojson|linked-places.json>` (Linked Places Format accepted as-is, so WHG
  exports load directly).
- API: `GET /api/places?q=&asOf=`, `GET /api/places/{id}` (geometry + children as of a period),
  `GET /api/aggregate?by=region&asOf=`; MCP `places` tool (search + hierarchy as Markdown); CLI
  `histdb places search|show|import|rebuild`. The map can draw a selected place's polygon as an
  overlay and use it as the filter (`Filter.placeId`) instead of a bbox.
- `Filter.regions` (§1.0) becomes `Filter.places: List[PlaceId]` resolved through names; free-text
  `region_name` matching is kept only as a fallback flag `--region-text`.
- *Acceptance:* a finding at Hattusa dated −1400 groups under Hittite Empire → Anatolia → Asia for
  `asOf=-1400`, and under Phrygia → Anatolia → Asia for `asOf=-700`; a finding on the Hittite/Mitanni
  frontier counts once at "Near East" and once under each polity; `places import` of a WHG LPF file
  round-trips (export = import).

### 1.7 Better time selector (histogram timeline: drag to zoom in, +/− and wheel to zoom out)

- The two `<input type=range>` are replaced by a **timeline strip**: a histogram of **how many
  sources** match each time bucket across the currently visible span (a source counts in every
  bucket its `period` overlaps; from `/api/aggregate?by=time&unit=sources`), drawn as SVG.
- **The visible span *is* the filter.** There is no separate selection handle: whatever the strip
  shows is the `period` sent to the server, and the map/list follow it.
- **Zoom in = select**: press at "from", drag to "to", release → the strip **zooms to that span** and
  the filter narrows to it. Minimum span = 10 years.
- **Zoom out**: `+`/`−` buttons at the side of the strip and **mouse wheel while hovering** the strip;
  each step scales the span ×2 around the cursor (wheel) or the centre (buttons), clamped to config
  `ui.time.min/max`. `⟲` resets to the default span. Wheel over the strip never scrolls the page.
- Typed **from / to** boxes next to the strip accept `3300 BCE`, `-3300`, `1200 CE`, `1200`,
  `c. 1500 BC` and set the span directly. Span is reflected in the URL hash.
- Matching semantics (assumption, not yet confirmed): **overlap** — a source/finding matches if its
  period touches the span; sources with **no period** are not in the histogram and are listed at the
  end of results under "undated (N)". The histogram is the same aggregation as 1.6, so the strip and
  the "by time" tree always agree.
- *Acceptance:* dragging −1600→−1200 yields a strip spanning exactly that, the results count equals
  `search --from -1600 --to -1200`; wheel-out twice from there shows −2400…−400; a source dated
  −1250 only appears in bars that cover −1250.

### 1.8 OCR keeping images (converter → DB → markdown → web)

- Converter: `POST /convert` returns `{markdown, pages, images:[{name, mime, base64}]}`.
  pymupdf4llm `to_markdown(path, write_images=True, image_path=tmp, image_format="png")` emits
  `![](name.png)`; docling path uses `ImageRefMode.REFERENCED`. Also `dpi`/`max_image_px` params,
  and images deduplicated by sha256.
- DB: `assets(citekey, name, mime, bytes bytea, sha256, width, height, PRIMARY KEY(citekey,name))`.
  Markdown links are **normalised at import** to `assets/<name>` (relative), so the same markdown works
  on disk and on the web.
- Server: `GET /source/{ck}/assets/{name}` streams the bytea with the right `Content-Type`
  (+ `Cache-Control: immutable`); flexmark renders `<img>`; modal + full page both show figures
  (lazy-loaded, click → lightbox). MCP `get_source(fullText=true)` returns the markdown with **absolute
  URLs** (`http://host:port/source/ck/assets/x.png`) so an image-capable client can fetch them.
- Seed importer also ingests the existing `md/<ck>/figures/*` from the old pipeline (already on disk
  for many sources) so the corpus gets figures without re-OCR.
- New CLI/MCP: `histdb ingest <pdf|epub> --citekey k [--quality]` (upload to the server, which calls
  the converter, stores text + assets, creates the row via `put_source`) and `histdb reocr <citekey>`
  — the first half of the deferred `ingest` slice; Calibre lookup/`suggest_acquisitions` stay deferred.
- **OCR is a job, not a request**: a 500-page scan under docling takes tens of minutes, longer than any
  sane HTTP timeout. `POST /api/ingest` stores the upload and returns `{jobId}` (202); `GET
  /api/jobs/{id}` reports `queued|running(page n/N)|done|failed(reason)`; the CLI polls with a progress
  line, the MCP tool returns the job id and a `job_status` tool exists. Jobs run on a bounded Ox
  worker pool (config `converter.parallelism = 1`), survive nothing (server restart → job `failed:
  interrupted`, re-run is idempotent by sha256). Duplicate upload (same sha256, other citekey) is
  refused with the existing citekey unless `--force`. Converter unreachable → job fails fast with the
  converter URL in the message; the `/api/health` endpoint reports converter + DB status.

### 1.9 No hardcoded constants

Everything runtime-tunable goes through `AppConfig` (Kindlings sconfig derivation) + env overrides:

```hocon
histdb {
  server    { host = "0.0.0.0", port = 8000,           # bind inside the container; compose publishes
              publicUrl = "http://localhost:8000" }    # used for absolute asset URLs in MCP output
                                                       # 127.0.0.1:${HISTDB_PORT} (LAN opt-in: HISTDB_BIND=0.0.0.0)
  db        { url, user, password, pool.max = 8 }
  converter { url = "http://localhost:8100", timeoutSeconds = 600 }
  limits    { search = 20, findings = 4000, snippetChars = 240 }
  ui        { tiles = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              time { min = -12000, max = 2026, defaultFrom = -3500, defaultTo = -500 } }
  cli       { serverUrl = "http://localhost:8000" }    # HISTDB_SERVER_URL
}
```

- `GET /api/config` serves the `ui` block to the page (so JS has no literals either).
- Compose/Dockerfile read `HISTDB_PORT` etc. `.mcp.json` is **generated** by `install.sh` from config
  (it needs a concrete URL; that's the one place a number is rendered, from config).
- Static lint: a munit test greps `src/main` for `:8000|5432|8100|unpkg` and fails (cheap guard).

### 1.9a Writes: create once, patch selectively (decided 2026-08-25)

The DB is the **source of truth**; the markdown wiki becomes an export. Two write operations, both
exposed as REST + MCP tool + CLI (same contract from `core`):

- **`put_source`** — *create / replace whole entry*: `PUT /api/sources/{citekey}` with the full
  `Source` (metadata, summary, body markdown or a PDF to OCR via §1.8, findings, edges, tags, assets).
  Idempotent; this is what `seed` and `ingest` call. Replaces everything for that citekey — used only
  when creating or deliberately re-importing.
- **`patch_source`** — *selective enrichment*: `PATCH /api/sources/{citekey}` with a **JSON-merge-style
  partial** where every top-level part is independently optional and, when present, replaces only
  that part: `metadata` (title/authors/year/venue/doi/status), `summary`, `body` (OCR markdown),
  `tags` (with `add`/`remove` lists, not just replace), `topics`, `period`, `findings`
  (`add: [...]`, `remove: [ids]`, `replace: [...]`), `edges` (`add`/`remove`), `notes` (free
  agent-written Markdown, a new column — the "article" the agent compiles about the source). Absent
  parts are untouched. Each finding gets a stable id (`<citekey>#<n>`, next free `n` on add) so it can
  be removed/updated individually. Returns the resulting `Source` + `updated_at`.
- `seed` = `put_source` per file, **but** refuses to overwrite a row whose `updated_at` is newer than
  the file's mtime unless `--force` (so a reseed can't wipe agent enrichment). `histdb export <dir>`
  writes frontmatter + body markdown per source (the mirror/backup of annotations; git-friendly).
- Concurrency: `PATCH` carries an optional `ifUpdatedAt` precondition (412 on mismatch) so two agents
  can't silently clobber each other; without it, last write wins on the touched parts only.
- MCP tool descriptions say explicitly: "to add or correct findings/tags/edges/notes, use
  `patch_source`; never re-upload the whole source".
- *Acceptance:* `patch_source {tags:{add:["sea-peoples"]}}` leaves findings/body untouched;
  `patch_source {findings:{add:[…]}}` twice adds two findings with distinct ids; `seed` after a patch
  reports "skipped 1 (newer in DB)"; `export` then `seed --force` round-trips byte-equal frontmatter.

### 1.10 Schema versioning with Flyway (decided 2026-08-25)

- `flyway-core` (+ `flyway-database-postgresql`) in the **server** module; migrations are plain SQL
  resources `modules/server/src/main/resources/db/migration/V001__baseline.sql` (today's
  `schema.sql`, verbatim), `V002__fts_chunks.sql`, `V003__assets.sql`, `V004__places.sql`, … The
  server runs `flyway.migrate()` at startup **before** binding the port and refuses to start on a
  failed/pending-repair migration (clear message, exit 3). `db/schema.sql` is deleted; the compose
  init-script mount goes away (the server owns the schema).
- The existing database: `V001` is applied with `baselineOnMigrate=true` + `baselineVersion=1`,
  so a seeded DB upgrades in place without a reseed. `histdb migrate --dry-run` (server endpoint
  `GET /api/schema`) shows current/pending versions.
- **Native-image note**: Flyway loads migrations by classpath scan and uses reflection for database
  types — the tracing agent run (`sbt server/run` against a fresh DB) must cover a migration to
  capture that metadata; add it to the smoke test so CI proves the native binary can migrate.
- `histdb backup [file]` / `histdb restore <file>` = `pg_dump`/`pg_restore` executed **inside** the
  Postgres container via a server endpoint (the CLI has no DB access, §2.5), written to a bind-mounted
  `backups/` dir from config. Restore refuses to run on a non-empty DB without `--force`.
- *Acceptance:* start the server against the current seeded volume → `flyway_schema_history` has
  V001…V00n, no data lost, `search` works; start against an empty volume → same end state.

## 2. Code quality & repo hygiene

### 2.0 Immediate fixes
- Delete the stray `agent-pid*` dirs; make `agentRunSettings` merge into a **build-time dir**
  (`target/native-image-agent/`) and copy the six JSON files into resources on **successful** exit
  only, so a killed run can't litter `src/`. Add `agent-pid*` to `.gitignore` as belt-and-braces.
- README: fix the stale stack description (Magnum, netty-sync; 5 → N tools).

### 2.1 Tests (munit, both levels)
- **Pure unit tests** (no DB, run in CI in seconds): `Frontmatter` (nulls, edge tags, wikilinks,
  NUL stripping), `Markdown` renderers, `Filter → SQL`, chunker, aggregation tree, time-input parser
  (`3300 BCE`), link resolver, antimeridian bbox split, the config lint from 1.9.
- **Integration tests** against Postgres: `HISTDB_TEST_DB_URL` set → run `SourceRepo` round-trips
  (upsert → search/get/geoWhen/neighbors/resolveEdges/facets/aggregate), int8range off-by-one,
  FTS ranking; unset → skipped with a visible reason. CI job runs a `postgis` service container.
- **HTTP smoke test**: start `Server` on port 0, hit `/`, `/api/findings`, `/source/x?fragment`,
  `/mcp` tools/list — replaces the ad-hoc curl script used during the native-image work.
- GitHub Actions: `sbt test` + `sbt cli/nativeImage server/nativeImage` (Linux) + docker build.

### 2.2 Immutability / referential transparency / reuse
- **Composition root**: `Main` builds `AppConfig → DataSource → Repos → Server(cfg, deps)`; no `val`
  side effects in `object Http`/`Main`. Handlers become pure functions of `(deps, input)`; MCP tools and
  REST endpoints are built by `McpTools(deps)`/`HttpApi(deps)` factories, so tests inject a fake or
  test DB.
- SQL fragments built from `Filter` are values; repos stay stateless. `Markdown` stays pure. Domain
  types get smart constructors where invariants exist (`Period(start ≤ end)`, `BBox`, `Citekey`
  opaque type validated by the regex now duplicated in `Frontmatter` and `Http.resolveLinks`).
- Remove duplicated SQL column lists (`hitCols` is defined but unused — inline via a `Fragment`).
- Errors: `Either[HistDbError, A]` at the boundary (CLI exit codes, HTTP 404/400, MCP error text)
  instead of `println`/`Console.err` sprinkled in commands.
- One contract per operation: `core` defines the Tapir endpoint (`I → O`) **and** `markdown(o)`;
  the server implements it once (MCP tool and REST route both call the same handler), the CLI calls
  it through the sttp client derived from the same endpoint and prints `markdown(o)` — this is what
  makes "CLI and MCP agree" a mechanical fact.

### 2.3 Docs, agents, skills, plugin
- **`README.md`**: what it is, install (one command), run, CLI reference (generated from decline
  `--help`), MCP tools table, config table (from `reference.conf`), backup/restore (`pg_dump`),
  troubleshooting (native-image notes distilled from this project's history).
- **`AGENTS.md`** (Scala practices: Scala 3 syntax, direct style, immutability, `Either` errors, no
  side effects in object init, Kindlings for codecs/schemas/config, Magnum SQL conventions, tests
  required for pure logic, native-image constraints: eager config, no reflection surprises, run the
  app once after adding a dependency so the agent captures metadata) and **`CLAUDE.md → AGENTS.md`**
  symlink.
- **Skills, shared by Claude Code and OpenCode**: canonical `skills/<name>/SKILL.md` at the repo root;
  `.claude/skills → ../skills` and `.opencode/skill → ../skills` symlinks (OpenCode discovers
  `.opencode/skill/*/SKILL.md`; verify exact dir name against the installed OpenCode at implementation
  time). Skills: `histdb-query` (how to answer research questions with the MCP tools: search →
  facets → neighbors → get_source with snippets), `histdb-ingest`, `histdb-compile` (§2.4 writing
  rules), `histdb-dev` (build/test/native-image workflow).
- **Claude plugin** `plugin/` (`.claude-plugin/plugin.json`, `skills/` → symlinks, `.mcp.json` for the
  hist-db server, optional hook that warns when the server is down). Installed into the research
  workspace via a local marketplace entry (`claude plugin marketplace add ~/Github/hist-db`) and
  enabled in `~/Documents/research/.claude/settings.json`; the old file-grep skills there get
  deprecated notes pointing at the MCP tools.

### 2.4 LLM writing instructions (the "compile" step)
Review and rewrite the summary/compile instructions (research workspace `update-topic`, `report`,
`assess-paper` skills + the new `histdb-compile`) around these rules:

1. **Exhaust the topic**: enumerate every source the filter returns, not the first page; say which
   sources were *not* used and why.
2. **Hunt contradictions**: for each claim, run `neighbors` and surface `refutes/challenges/supersedes`
   edges; state explicitly when a newer finding overturns an older hypothesis, with years.
3. **No generic answers**: every paragraph cites a citekey; a paragraph without a source is deleted.
4. **Plain technical language**: short sentences, concrete nouns, numbers and dates; ban a list of
   filler ("rich tapestry", "delve", "nuanced interplay", "sheds light"…) — enforced by a lint in the
   skill.
5. **No invented terminology; define specialist terms on first use** (one clause, in-line), because
   the reader is a general audience. Keep a glossary block at the end when > 5 terms appear.
6. Structure: question → what the sources say (by period/region, the same nesting as 1.6) →
   disagreements → what is unresolved → sources used.

A `docs/writing-guide.md` holds the rules once; skills link to it instead of duplicating.

### 2.5 Packaging: one command
- `Dockerfile` (multi-stage: GraalVM CE 25 builder → `native-image` → distroless/`ubuntu` runtime)
  for the server; compose gains the `server` service (currently commented out) with healthcheck; the
  converter is already containerised.
- **`install.sh`** (idempotent): checks Docker + sdkman, `sbt cli/nativeImage`, copies
  `histdb-cli → ~/bin/histdb`, `docker compose build && up -d`, waits for health, writes
  `.mcp.json`/plugin MCP config from `HISTDB_PORT`, prints next steps. `histdb doctor` verifies the
  same from the CLI later. `make install` alias.
- **The CLI is a pure HTTP client** (decided 2026-08-25): every command — reads *and* `seed`,
  `ingest`, `places import` — calls server endpoints via an sttp client derived from `core`'s Tapir
  endpoint definitions. No pgjdbc in the CLI binary, no DB credentials on the host, Postgres port
  **not** published (the server is the only DB client). Consequences: `core` splits into
  `core` (contract: DTOs, `Filter`, endpoints, Markdown) and `server`-only DB code (Magnum, repos,
  importer) — the CLI must not depend on the DB module; uploads use multipart (`POST /api/ingest`
  with the PDF; `POST /api/seed` with a tar of `wiki/sources` **or** the server reads a bind-mounted
  corpus dir from config `histdb.corpus.dir`, compose mounts it). Writes are authenticated by a
  **localhost-only publish by default** (compose `127.0.0.1:${HISTDB_PORT}:${HISTDB_PORT}`) — an
  explicit `HISTDB_BIND=0.0.0.0` is the opt-in for LAN exposure.

## 3. Review pass (Fable 5 — this session)

After the implementation streams merge, I review the whole tree with three lenses, each producing
findings that go back to the implementers:

- **Bugs / unhandled cases**: nulls from YAML, empty filters, `limit=0`, non-ASCII citekeys, huge
  bodies, bbox across the antimeridian, concurrent seeds, converter down, DB down at startup (retry with
  backoff, then a clear message), native-image regressions (run both binaries with the smoke test).
- **UX — website** (goal: *discover → read → hop, Wikipedia-like*): every citekey is a link, back/forward
  work in the modal (history API), search box is always visible, results survive reload (URL hash),
  keyboard navigation, no dead ends (a source with no findings still opens; "no results" suggests
  loosening chips), reading mode for full text with a TOC, figures shown.
- **UX — CLI/MCP** (goal: *agents answer questions, summarise, compile*): tool descriptions tell the
  model *when* to use which tool; outputs are Markdown with stable headings; snippets present;
  `get_source` paginates full text (`page`/`chars`) so a 5 MB body never floods a context; `facets`
  lists vocabulary; errors are actionable text, never stack traces.

Done via a `Workflow` (find → adversarial verify → report) so the findings are checked before
you see them.

## 4. Execution model

Implementers: **`claude-opus-4-6`** agents (`model: opus`), each in its own **git worktree**
(`isolation: worktree`), integrated by me on `master`. Work is ordered in **waves**; a wave's streams
run in parallel and only depend on earlier waves. Each stream is one worktree, ≤ ~1 day, owns a
listed set of files, ships its tests, and has a pass/fail acceptance line (the review checklist).

### Wave 0 — seams (me, serial) — **DONE 2026-08-25** (all acceptance lines verified, incl. natively)
| # | stream | acceptance |
|---|---|---|
| 0.1 | **Core split**: `core` = contract (DTOs, `Filter`, Tapir endpoints, Markdown); new `db` module (Magnum repos, importer, Flyway); `server` depends on both, `cli` on `core` only | `sbt cli/compile` classpath contains no `postgresql`/`magnum` jar |
| 0.2 | **Config** §1.9 (all keys, `/api/config`, literal-port lint test) | lint test fails when `8000` is reintroduced in `src/main` |
| 0.3 | **Composition root** §2.2: `Main` builds cfg → ds → repos → `HttpApi(deps)`/`McpTools(deps)`; no `val` side effects in objects | `Server.start(cfg, deps)` starts on port 0 in a test and answers `/api/health` |
| 0.4 | **Flyway** §1.10 with `V001__baseline.sql`; remove `schema.sql` init mount | current volume migrates in place; smoke test proves the native binary migrates |
| 0.5 | **Web split**: `web/{index.html,style.css,app.js,state.js(url hash),api.js,map.js,timeline.js,filters.js,results.js,chips.js,article.js,vendor/}`; Leaflet vendored | page works identically to today with the CDN blocked |
| 0.6 | **Test scaffold** §2.1: munit, `HISTDB_TEST_DB_URL`-gated integration suite, HTTP smoke, CI workflow | `sbt test` green locally and in Actions |
| 0.7 | Fix §2.0 (agent temp dirs via `target/`, README stack line) | `git status` clean after a killed `sbt run` |

### Wave 1 — platform (parallel)
| # | stream | owns | acceptance |
|---|---|---|---|
| 1.1 | **Filter + unit-of-result + facets** §1.0/1.3 (SQL fragment, `Hit.matched/why`, `/api/sources`, `/api/facets`, MCP `search`/`facets`, CLI flags) | `db/Filter*`, `core/Filter*`, `core/Markdown`, endpoints | `search --tag sea-peoples --from -1300 --to -1100` returns sources with `matched` findings; facet counts are distinct sources; pure tests for `Filter→SQL` |
| 1.2 | **FTS** §1.4 (`source_chunks` migration, chunker, `websearch_to_tsquery`, `ts_headline` snippets, `?q=` `<mark>` in articles) | migration `V00x__fts`, `db/Chunker`, `db/Search*`, `article.js` highlight | `"sea peoples" -egypt` ranks and snippets; 5 MB body indexes without error; chunker tests |
| 1.3 | **Writes + export** §1.9a (`put/patch_source`, `seed` newer-row guard, `export`) | `core/Patch*`, `db/SourceWrite*`, endpoints, CLI `seed/export/patch` | acceptance lines of §1.9a |
| 1.4 | **CLI as HTTP client** §2.5 (sttp client from `core` endpoints, all commands, progress/exit codes) | `modules/cli/**` | every CLI command works with Postgres port unpublished; native image builds |
| 1.5 | **Map: viewport toggle + clustering** §1.1/1.2 | `map.js`, `results.js` (picker) | acceptance lines of §1.2; antimeridian test |
| 1.6 | **Chips + filter bar + URL state** §1.5 | `chips.js`, `filters.js`, `state.js` | typing `in` suggests `indo-european (17)`; reload restores chips/bbox/span from hash |
| 1.7 | **Timeline strip** §1.7 | `timeline.js`, `/api/aggregate?by=time` | acceptance lines of §1.7 |
| 1.8 | **Docs/agents/skills/plugin/install** §2.3/2.5 (README, AGENTS.md+symlink, `skills/` + `.claude`/`.opencode` symlinks, `plugin/`, `Dockerfile`, compose `server`, `install.sh`) | those files | `./install.sh` on a clean clone ends with `histdb ping` OK and the plugin listed in `claude plugin list` |

### Wave 2 — dimensions (parallel, after wave 1 merged)
| # | stream | owns | acceptance |
|---|---|---|---|
| 2.1 | **Gazetteer** §1.6a (schema, LPF import, containment materialisation, `places` API/tool/CLI, polygon overlay) with Pleiades + Natural Earth seeds | `V00x__places`, `db/Places*`, `places.js` | §1.6a acceptance except polity polygons |
| 2.2 | **Polity polygons** (trace EBA→Iron II for the six core areas, `source` property per polygon) — **you review the GeoJSON before it is seeded** | `db/places/*.geojson` | every polygon has `period` + `source`; Hattusa −1400 → Hittite Empire |
| 2.3 | **Aggregation tree** §1.6 (`by=time,region`, adaptive buckets, lazy depth, tabs, CLI `--group`, MCP `groupBy`) | `db/Aggregate*`, `results.js` tree view | pure tree tests; acceptance lines of §1.6/1.6a |
| 2.4 | **OCR with images + jobs** §1.8 (converter images, `assets` table/endpoint, job queue, `ingest`/`reocr`, figures import) | `converter/**`, `V00x__assets`, `db/Assets*`, `db/Jobs*`, `article.js` lightbox | ingest a scanned PDF → figures render in the modal; `md/<ck>/figures` imported for existing sources |
| 2.5 | **Backup/restore** §1.10 | `db/Backup*`, CLI | `backup` then `restore --force` on an empty volume reproduces `search` results |

### Wave 3 — review and close (me)
- §3 review lenses over the merged tree (Fable, via forked agents / Workflow if you opt in); findings
  fixed by opus agents in worktrees; §2.4 writing rules applied to the research-workspace skills.
- Final: `./install.sh` end-to-end on this machine, plugin enabled in `~/Documents/research`, README
  followed verbatim, `master` pushed. No tag.

Each stream brief contains: owned files, the `Filter`/endpoint contract, "tests for every pure
function", "run the app once so native-image metadata is captured", "do not touch other streams'
files", and its acceptance line verbatim.

Deferred (hooks noted above): server-side clustering, Calibre lookup, `suggest_acquisitions`,
OpenAlex discovery.
