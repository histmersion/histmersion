# Histmersion — plan (discovery UI · filters · full-text · gazetteer · images · quality · packaging)

Status: **grilled and approved 2026-08-25**. Single-user, local, private repo → **no release versioning**:
work lands on `master` in dependency order (waves below); "done" = acceptance criteria pass on this machine.

## 0. Where we are

Working end-to-end (commit `7104e82`): Postgres/PostGIS store (209 sources · 964 findings · 1066
typed edges · 84 MB OCR text), MCP (5 tools) + REST + Leaflet web UI + CLI, both GraalVM native
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
  CLI: `histmersion search [--tag t]* [--author a]* [--region r]* [--from -3300 --to -1200] [--bbox …] [--sort cited] [--limit n] [query]`.
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
  `histmersion facets tags [--prefix in]`): returns `(value, count)` for tags/authors/places/topics/status,
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
  count + range, leaves are hits. CLI: `histmersion search … --group time,region` prints the same tree as
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
  place_id)` on import / on gazetteer change (`histmersion places rebuild`); findings without a point fall
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
  `histmersion places import <geojson|linked-places.json>` (Linked Places Format accepted as-is, so WHG
  exports load directly).
- API: `GET /api/places?q=&asOf=`, `GET /api/places/{id}` (geometry + children as of a period),
  `GET /api/aggregate?by=region&asOf=`; MCP `places` tool (search + hierarchy as Markdown); CLI
  `histmersion places search|show|import|rebuild`. The map can draw a selected place's polygon as an
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
- New CLI/MCP: `histmersion ingest <pdf|epub> --citekey k [--quality]` (upload to the server, which calls
  the converter, stores text + assets, creates the row via `put_source`) and `histmersion reocr <citekey>`
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
histmersion {
  server    { host = "0.0.0.0", port = 8000,           # bind inside the container; compose publishes
              publicUrl = "http://localhost:8000" }    # used for absolute asset URLs in MCP output
                                                       # 127.0.0.1:${HISTMERSION_PORT} (LAN opt-in: HISTMERSION_BIND=0.0.0.0)
  db        { url, user, password, pool.max = 8 }
  converter { url = "http://localhost:8100", timeoutSeconds = 600 }
  limits    { search = 20, findings = 4000, snippetChars = 240 }
  ui        { tiles = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              time { min = -12000, max = 2026, defaultFrom = -3500, defaultTo = -500 } }
  cli       { serverUrl = "http://localhost:8000" }    # HISTMERSION_SERVER_URL
}
```

- `GET /api/config` serves the `ui` block to the page (so JS has no literals either).
- Compose/Dockerfile read `HISTMERSION_PORT` etc. `.mcp.json` is **generated** by `install.sh` from config
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
  the file's mtime unless `--force` (so a reseed can't wipe agent enrichment). `histmersion export <dir>`
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
  `schema.sql`, verbatim), `V004__fts_chunks.sql`, `V003__assets.sql`, `V004__places.sql`, … The
  server runs `flyway.migrate()` at startup **before** binding the port and refuses to start on a
  failed/pending-repair migration (clear message, exit 3). `db/schema.sql` is deleted; the compose
  init-script mount goes away (the server owns the schema).
- The existing database: `V001` is applied with `baselineOnMigrate=true` + `baselineVersion=1`,
  so a seeded DB upgrades in place without a reseed. `histmersion migrate --dry-run` (server endpoint
  `GET /api/schema`) shows current/pending versions.
- **Native-image note**: Flyway loads migrations by classpath scan and uses reflection for database
  types — the tracing agent run (`sbt server/run` against a fresh DB) must cover a migration to
  capture that metadata; add it to the smoke test so CI proves the native binary can migrate.
- `histmersion backup [file]` / `histmersion restore <file>` = `pg_dump`/`pg_restore` executed **inside** the
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
- **Integration tests** against Postgres: `HISTMERSION_TEST_DB_URL` set → run `SourceRepo` round-trips
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
- Errors: `Either[HistmersionError, A]` at the boundary (CLI exit codes, HTTP 404/400, MCP error text)
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
  time). Skills: `histmersion-query` (how to answer research questions with the MCP tools: search →
  facets → neighbors → get_source with snippets), `histmersion-ingest`, `histmersion-compile` (§2.4 writing
  rules), `histmersion-dev` (build/test/native-image workflow).
- **Claude plugin** `plugin/` (`.claude-plugin/plugin.json`, `skills/` → symlinks, `.mcp.json` for the
  Histmersion server, optional hook that warns when the server is down). Installed into the research
  workspace via a local marketplace entry (`claude plugin marketplace add <path to the histmersion checkout>`) and
  enabled in the research workspace's `.claude/settings.json`; the old file-grep skills there get
  deprecated notes pointing at the MCP tools.

### 2.4 LLM writing instructions (the "compile" step)
Review and rewrite the summary/compile instructions (research workspace `update-topic`, `report`,
`assess-paper` skills + the new `histmersion-compile`) around these rules:

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
- `Dockerfile` (multi-stage: GraalVM CE **25.0.1** builder (the pinned toolchain) → `native-image` → `debian:bookworm-slim` runtime)
  for the server; compose gains the `server` service (currently commented out) with healthcheck; the
  converter is already containerised.
- **`install.sh`** (idempotent): checks Docker + sdkman, `sbt cli/nativeImage`, copies
  `histmersion-cli → ~/bin/histmersion`, `docker compose build && up -d`, waits for health, writes
  `.mcp.json`/plugin MCP config from `HISTMERSION_PORT`, prints next steps. `histmersion doctor` verifies the
  same from the CLI later. `make install` alias.
- **The CLI is a pure HTTP client** (decided 2026-08-25): every command — reads *and* `seed`,
  `ingest`, `places import` — calls server endpoints via an sttp client derived from `core`'s Tapir
  endpoint definitions. No pgjdbc in the CLI binary, no DB credentials on the host, Postgres port
  **not** published (the server is the only DB client). Consequences: `core` splits into
  `core` (contract: DTOs, `Filter`, endpoints, Markdown) and `server`-only DB code (Magnum, repos,
  importer) — the CLI must not depend on the DB module; uploads use multipart (`POST /api/ingest`
  with the PDF; `POST /api/seed` with a tar of `wiki/sources` **or** the server reads a bind-mounted
  corpus dir from config `histmersion.corpus.dir`, compose mounts it). Writes are authenticated by a
  **localhost-only publish by default** (compose `127.0.0.1:${HISTMERSION_PORT}:${HISTMERSION_PORT}`) — an
  explicit `HISTMERSION_BIND=0.0.0.0` is the opt-in for LAN exposure.

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

## 5. Research-workspace skills → CLI/MCP only (analysis 2026-08-25)

The 12 skills in the research workspace's `.claude/skills/` drive the old pipeline by hand: Python
scripts (`tools/*.py`), DuckDB SQL (`sync_db.py --query`), `rg`/`grep` over `wiki/`, `calibredb`,
matplotlib. Goal: every skill uses **only** `histmersion` (CLI) and `mcp__histmersion__*` (MCP), enforced by
`allowed-tools` in each SKILL.md plus a workspace `settings.json` deny rule for the old scripts.

### 5.1 Capability map (what the skills do → what replaces it → what must be built)

| skill | does today (manually) | Histmersion replacement | status |
|---|---|---|---|
| `trailhead` | `discover.py wikirefs/search/meta` (OpenAlex/Crossref), builds a queue in `meta/ACQUIRE.md` | `histmersion discover wikirefs <url>` · `discover search "<q>"` · `discover meta <doi>` · `histmersion queue add` · MCP `discover` | **build: stream 2.6** |
| `ingest-paper` | `ingest.py` (metadata → Unpaywall/CORE/arXiv OA fetch → convert → Crossref refs → assess → note → papis → sync), then LLM enrich via file edits | `histmersion ingest <doi|arxiv|pdf>` (server job: metadata → OA fetch → converter → `put_source` + `references` edges from Crossref) then `patch_source` for enrichment; MCP `ingest`, `job_status`, `patch_source` | **build: 2.6** (OA fetch, refs) + 1.3/2.4 |
| `import-file` | `import_file.py`, `to_calibre.py --apply` | `histmersion ingest <file> --citekey k --provenance manual` then `histmersion calibre push <citekey>` | 2.4 + **2.6 (calibre)** |
| `import-archive` | `archive_org.py search/info/import`, `to_calibre.py` | `histmersion archive search|info|import <id>` (server fetches the public file → ingest job) | **build: 2.6** |
| `import-calibre` | `calibre.py search/import` (reads `metadata.db`), `calibredb` | `histmersion calibre search "<q>"` · `histmersion calibre import <id> --citekey k` — runs on the **host** (Calibre lives there; the one host-local integration, allowed explicitly) and uploads the file through `ingest` | **build: 2.6** |
| `sync-calibre` | `to_calibre.py`, `dedup_raw.py`, `calibre_meta.py`, `rg` for gaps; the Stop/PostToolUse hook `calibre_gap_reminder.py` | `histmersion calibre sync [--apply]` (push every source with `provenance != calibre` and a stored original into Calibre with metadata; report gaps); hook → `histmersion calibre gaps --quiet` | **build: 2.6** |
| `assess-paper` | `assess.py <doi>`, `assess.py update-retractions` (Retraction Watch CSV + Crossref updates + citation counts), DuckDB queries | `histmersion assess <doi|citekey>` · `histmersion assess update-retractions` (server keeps the RW CSV; `patch_source status=retracted`); MCP `assess` | **build: 2.6** |
| `find-followups` | `followups.py <doi>` (S2 citing papers with intents), then edits `status`/`superseded_by` in frontmatter | `histmersion followups <citekey|doi>` (S2 + OpenAlex; proposes typed edges) → `patch_source {edges:{add}}`; MCP `followups` | **build: 2.6** |
| `update-topic` | `discover.py search --since`, `assess.py update-retractions`, DuckDB delta queries | `histmersion discover search --since YEAR --not-in-db` + `assess update-retractions` + `search`/`facets`/`histogram` | 2.6 + 1.1/1.7 |
| `lint-wiki` | DuckDB queries, `grep` for dead wikilinks, empty frontmatter, orphans | `histmersion lint` (server: dangling edge targets, sources without findings/period/regions, empty summaries, duplicate DOIs/sha256, unresolved short citekeys); MCP `lint` | **build: 2.8** |
| `report` | DuckDB SQL, `viz.py map|timeline` (matplotlib), Marp | `search`/`facets`/`aggregate`/`histogram` (Markdown), web UI deep links (`/#…` hash state = a shareable map/timeline view), `histmersion query "<read-only SQL>"` for anything else; the report itself is written by the LLM as a **wiki article** (`histmersion article put`) rendered at `/article/{slug}`; Marp/matplotlib dropped | **build: 2.7 (articles), 2.8 (query)** |
| `bookkeeping` | edits `meta/ACQUIRE.md`, `meta/TASKS.md`, `meta/LOG.md` by hand | `histmersion queue acquire|tasks|log add|list|done` (DB-backed, exported to the same three Markdown files by `histmersion export`), MCP `queue` | **build: 2.8** |

Cross-cutting: `rg`/`grep` over notes → `search` + `facets`; `sync_db.py` → nothing (the DB is
live); hand-edited `wiki/{concepts,myths,cultures,regions,timeline}/*.md` → **articles** (2.7).

### 5.2 New streams (wave 2)

| # | stream | acceptance |
|---|---|---|
| 2.6 | **Acquisition & assessment toolset**: `discover` (OpenAlex/Crossref), `ingest <doi|arxiv|file>` with legal-OA fetch (Unpaywall → CORE → arXiv), Crossref `refs` → `references` edges, `assess` (+ Retraction Watch refresh), `followups` (Semantic Scholar/OpenAlex intents), `archive` (archive.org public files), host-side `calibre search|import|push|sync|gaps` (uses `calibredb`/`metadata.db` locally, uploads via `ingest`); all API keys/emails/mirrors in `reference.conf` (`acquire.*`); every network client in `core`/`server` behind a small trait so tests use fixtures | `histmersion ingest 10.1038/nature14317` on an empty DB produces a source with body, refs edges and assessment; `histmersion followups heggarty2023_language` lists citing papers with intents; `calibre sync --apply` pushes a manual PDF into Calibre with title/author/DOI |
| 2.7 | **Articles (the wiki)**: table `articles(slug, title, body markdown, tags, kind = concept|myth|culture|region|timeline|report, updated_at)`; `put_article`/`patch_article`/`get_article`/`list_articles` (REST + MCP + CLI); `[[slug]]` and `[[citekey]]` links resolved both ways; `/article/{slug}` rendered by flexmark with backlinks and "sources cited" footer; `histmersion export` writes them to `wiki/<kind>/<slug>.md`; `histmersion seed` imports the existing compiled articles; the web UI gets an "articles" tab (search by title/tag) | the 40+ existing wiki articles import, render, and round-trip; a `report` written via `put_article` shows its cited sources as links |
| 2.8 | **Lint, queue, read-only query**: `histmersion lint` (rules above, JSON + Markdown), `histmersion queue …` (acquire/tasks/log tables + export to `meta/*.md`), `histmersion query "<SQL>"` executed in a `READ ONLY` transaction with a statement timeout and row cap (config); MCP `lint`/`queue`/`query` | `lint` on the seeded DB reports the known unresolved short citekeys; `query "select count(*) from sources"` works and `query "delete …"` is refused |

### 5.3 Skill rewrite (wave 3, after 2.6–2.8 merge)

- Each research skill is rewritten to call only `histmersion …` and MCP tools; frontmatter gets
  `allowed-tools: Bash(histmersion:*), mcp__histmersion__*` (plus `Read`/`Write` only where the skill
  legitimately produces a file, e.g. a report draft), and the workspace `settings.json` gains
  `permissions.deny: ["Bash(.venv/bin/python tools/*)", "Bash(rg:*)", "Bash(grep:*)"]` for skill
  contexts once nothing depends on them. `bookkeeping` becomes `histmersion queue`; the Calibre reminder
  hook calls `histmersion calibre gaps --quiet`.
- The research `CLAUDE.md` golden rules change: the DB is the source of truth; `wiki/` and `meta/`
  are **exports** (`histmersion export`) kept for Obsidian reading and git history; `tools/*.py` are
  retired (kept one release for `manifest.py rebuild` until 2.6 covers rebuild-from-DOI).
- The four Histmersion skills (`histmersion-query/ingest/compile/dev`, stream 1.8) are the templates; the
  plugin (1.8) installs them into the workspace so both skill sets share one vocabulary.

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
| 0.6 | **Test scaffold** §2.1: munit, `HISTMERSION_TEST_DB_URL`-gated integration suite, HTTP smoke, CI workflow | `sbt test` green locally and in Actions |
| 0.7 | Fix §2.0 (agent temp dirs via `target/`, README stack line) | `git status` clean after a killed `sbt run` |

### Wave 1 — platform (parallel) — **DONE 2026-08-25** (7 streams + an integration-fix stream merged on master; 236 tests; both native binaries, the Docker image and the web UI verified end to end on the seeded corpus)
| # | stream | owns | acceptance |
|---|---|---|---|
| 1.1 | **Filter + unit-of-result + facets** §1.0/1.3 (SQL fragment, `Hit.matched/why`, `/api/sources`, `/api/facets`, MCP `search`/`facets`, CLI flags) | `db/Filter*`, `core/Filter*`, `core/Markdown`, endpoints | `search --tag sea-peoples --from -1300 --to -1100` returns sources with `matched` findings; facet counts are distinct sources; pure tests for `Filter→SQL` |
| 1.2 | **FTS** §1.4 (`source_chunks` migration, chunker, `websearch_to_tsquery`, `ts_headline` snippets, `?q=` `<mark>` in articles) | migration `V00x__fts`, `db/Chunker`, `db/Search*`, `article.js` highlight | `"sea peoples" -egypt` ranks and snippets; 5 MB body indexes without error; chunker tests |
| 1.3 | **Writes + export** §1.9a (`put/patch_source`, `seed` newer-row guard, `export`) | `core/Patch*`, `db/SourceWrite*`, endpoints, CLI `seed/export/patch` | acceptance lines of §1.9a |
| 1.4 | **CLI as HTTP client** §2.5 (sttp client from `core` endpoints, all commands, progress/exit codes) | `modules/cli/**` | every CLI command works with Postgres port unpublished; native image builds |
| 1.5 | **Map: viewport toggle + clustering** §1.1/1.2 | `map.js`, `results.js` (picker) | acceptance lines of §1.2; antimeridian test |
| 1.6 | **Chips + filter bar + URL state** §1.5 | `chips.js`, `filters.js`, `state.js` | typing `in` suggests `indo-european (17)`; reload restores chips/bbox/span from hash |
| 1.7 | **Timeline strip** §1.7 | `timeline.js`, `/api/aggregate?by=time` | acceptance lines of §1.7 |
| 1.8 | **Docs/agents/skills/plugin/install** §2.3/2.5 (README, AGENTS.md+symlink, `skills/` + `.claude`/`.opencode` symlinks, `plugin/`, `Dockerfile`, compose `server`, `install.sh`) | those files | `./install.sh` on a clean clone ends with `histmersion ping` OK and the plugin listed in `claude plugin list` |

### Wave 2 — dimensions (parallel, after wave 1 merged) — **DONE 2026-08-25** (8 streams merged; 646 tests; gazetteer seeded into the live DB — 16 779 places, 938/964 findings located; 38 wiki articles + bookkeeping queues imported; natives + Docker image rebuilt and verified)
| # | stream | owns | acceptance |
|---|---|---|---|
| 2.1 | **Gazetteer** §1.6a (schema, LPF import, containment materialisation, `places` API/tool/CLI, polygon overlay) with Pleiades + Natural Earth seeds | `V00x__places`, `db/Places*`, `places.js` | §1.6a acceptance except polity polygons |
| 2.2 | **Polity polygons** (trace EBA→Iron II for the six core areas, `source` property per polygon) — **you review the GeoJSON before it is seeded** | `db/places/*.geojson` | every polygon has `period` + `source`; Hattusa −1400 → Hittite Empire |
| 2.3 | **Aggregation tree** §1.6 (`by=time,region`, adaptive buckets, lazy depth, tabs, CLI `--group`, MCP `groupBy`) | `db/Aggregate*`, `results.js` tree view | pure tree tests; acceptance lines of §1.6/1.6a |
| 2.4 | **OCR with images + jobs** §1.8 (converter images, `assets` table/endpoint, job queue, `ingest`/`reocr`, figures import) | `converter/**`, `V00x__assets`, `db/Assets*`, `db/Jobs*`, `article.js` lightbox | ingest a scanned PDF → figures render in the modal; `md/<ck>/figures` imported for existing sources |
| 2.5 | **Backup/restore** §1.10 | `db/Backup*`, CLI | `backup` then `restore --force` on an empty volume reproduces `search` results |

### Wave 3 — review and close (me) — **DONE 2026-08-25** (three Fable reviews → 4 fix streams merged: security/correctness (34 items), web UX (32), agent surface (2 parts), JDK 25 + tapir 1.13/ox 1.0; writing guide rewritten; Histmersion + research-workspace skills CLI/MCP-only; 807 tests; natives + Docker rebuilt; live DB re-seeded with figure links + gazetteer)
- **JDK pin — DONE.** The toolchain is **GraalVM CE 25.0.1**. tapir `1.11.25 → 1.13.31` (sttp client4 `4.0.3 → 4.0.26`) brings **ox 1.0.2**, which replaced `StructuredTaskScope` — an interface since JEP 505 — with its own `ox.internal.ThreadHerd` over virtual threads, so `NettySyncServer.startAndWait` runs on 25. Chimp 0.5.2 already targets tapir 1.13.31, so it needed no bump. Native-image metadata regenerated by the JDK-25 agent into one `reachability-metadata.json` per module (the legacy split `*-config.json` files deleted). `.sdkmanrc` / `.github/workflows/ci.yml` / `Dockerfile` re-pinned together. One behavioural difference: JDK 25's CLDR gives `en_PL` a decimal comma, so `Backup.humanBytes` now formats against `Locale.ROOT` (wire data is never locale-dependent).
- §3 review lenses over the merged tree (Fable, via forked agents / Workflow if you opt in); findings
  fixed by opus agents in worktrees; §2.4 writing rules applied to the research-workspace skills.
- Final: `./install.sh` end-to-end on this machine, plugin enabled in the research workspace, README
  followed verbatim, `master` pushed. No tag.

Each stream brief contains: owned files, the `Filter`/endpoint contract, "tests for every pure
function", "run the app once so native-image metadata is captured", "do not touch other streams'
files", and its acceptance line verbatim.

Deferred (hooks noted above): server-side clustering, Calibre lookup, `suggest_acquisitions`,
OpenAlex discovery.

## 6. Review findings 2026-08-25 (agent-facing docs stream)

Two review lenses over the merged wave-2 tree, checked against the **live** server (209 sources,
29 MCP tools, 38 articles) and the native CLI, not against the plan.

- **Lens A — the CLI/MCP surface an agent calls** (A1–A28). Mostly code; this stream fixed only the
  documentation half and hands the rest over.
- **Lens B — the prose an agent writes** (B1–B19): `docs/writing-guide.md`, `skills/*/SKILL.md`,
  `plugin/README.md`, the README's *MCP tools* / *Skills* sections. All fixed here.

Owned and changed by this stream: `docs/writing-guide.md` (rewritten), `skills/histmersion-query`,
`skills/histmersion-ingest`, `skills/histmersion-compile`, `skills/histmersion-dev` (rewritten/extended),
`skills/histmersion-currency` (**new**), `plugin/README.md`, README *MCP tools* + *Skills*,
`tools/check_skills.sh` (**new**), this section. No Scala, JS or config was touched.

### Lens A — CLI/MCP surface

| # | finding | disposition |
|---|---|---|
| A1 | Deep time is `from`/`to` on `search`/`facets` but `periodStart`/`periodEnd` on `geo_when`/`histogram` — one concept, two names, MCP only (the CLI uses `--from/--to` throughout) | **code stream** — unify; documented as-is in `histmersion-query` |
| A2 | MCP `geo_when`/`histogram` accept only `region`/bbox/period; their CLI twins accept the whole filter (`--tag --topic --status --author --place --year-from/--year-to`) | **code stream** — bring the MCP shapes up to the CLI's |
| A3 | Unknown argument keys are **silently ignored**: `geo_when {region:"Anatolia", tags:["sea_peoples"]}` returns unfiltered Anatolia findings, no error | **code stream** — reject unknown keys |
| A4 | The `neighbors` tool description names the kinds `cites/refutes/challenges/updates/supersedes`; the stored kinds are `references/challenges/refutes/updates/superseded_by`. `cites` and `supersedes` do not exist in the data | **code stream** (tool description); docs fixed here |
| A5 | `followups` proposes `confirms\|challenges\|refutes\|supersedes\|cites` — disjoint from the storable kinds, so `--apply` can write edges `neighbors` will never classify as disagreement | **code stream** — reconcile the two vocabularies |
| A6 | `Domain.Edge`'s scaladoc lists `supersedes` as valid; nothing produces or stores it | **code stream** |
| A7 | `get_source(fullText)` has no slicing. Bodies: mean 450 KB, **max 5.7 MB** (`beekes2010_etymological`), 55 over 500 KB — one call can exhaust a context. §3 already promised `page`/`chars` | **code stream** — section/page slicing; avoidance documented here |
| A8 | Every CLI `--help` exits **1**, so exit status cannot probe whether a command exists | **code stream** — exit 0; `tools/check_skills.sh` matches the `Usage:` block meanwhile |
| A9 | `queue`'s read verb is the bare list name on the CLI (`histmersion queue tasks`; `queue show` errors) but `action:"list"` on MCP (`"show"` errors) | **code stream** — one spelling |
| A10 | MCP `queue` has no `rm`/`export`/`import`; the CLI has all three | **code stream** |
| A11 | The CLI folds identifier- and file-ingest into one `histmersion ingest`; MCP splits them into `ingest_doi` and `ingest` | **code stream** — align, or say why they differ |
| A12 | `lint` reports 1,058 findings of which **1,051** are `dangling-edges`, because 1,039 of 1,048 `references` edges point at concept slugs by design. The rule drowns its own signal | **code stream** — split "concept link" from "genuinely dead" |
| A13 | `retraction_watch` holds **0 rows** on the live server, so `assess` cannot detect a retraction until `assess update-retractions` runs — and nothing says so | **code stream** — warn on an empty mirror; documented in `histmersion-currency` |
| A14 | 1 of 209 sources has a stored assessment, yet no source is `unassessed`: "assessed" and "status set" are different facts and nothing surfaces the gap | **code stream** — a lint rule for sources with no assessment |
| A15 | 12 sources have no publication year (rendered `?`); no lint rule covers it, and the writing guide now requires `n.d.` plus a queued task | **code stream** — add a `no-year` rule |
| A16 | `patch_article.append` only appends at the **end** of the body; the guide requires appending under a named section, which currently forces a full-body read-splice-write | **code stream** — section-targeted append |
| A17 | No `article-filler` lint rule (banned-filler phrases in an article body) | **code stream** — the guide names the rule |
| A18 | No `article-uncited-paragraph` lint rule (a body paragraph with no `[[citekey]]`) | **code stream** — the guide names the rule |
| A19 | "13 rules" is hardcoded in three places (CLI help, MCP description, README), so adding a rule means editing all three | **code stream** — generate the count |
| A20 | An unknown `status`/`tag`/`topic` returns `_No matching sources._` with no hint, though `facets` knows the real vocabulary | **code stream** — suggest the nearest value |
| A21 | `contested` is a first-class `SourceStatus` and the largest non-mainstream bucket (17 of 209), but was missing from the documented taxonomy everywhere | **fixed here** (guide + all skills); code stream to audit the tool descriptions |
| A22 | `neighbors` does not distinguish an edge whose target resolves to a source from one pointing at a concept slug — the reader has to already know | **code stream** — mark resolved targets |
| A23 | Disagreement edges are extremely sparse: **6** source-to-source across the whole corpus (2 `challenges`, 1 `refutes`, 3 `updates`, 0 `superseded_by`). Nothing warns that an empty `neighbors` is uninformative | **code stream** — say so in the output; documented here |
| A24 | `get_article` renders a generated `## Sources cited (N)` footer *and* the hand-written `## Sources` section; nothing stops an agent writing a duplicate | **code stream** — reject a `Sources cited` heading in a body; rule documented here |
| A25 | `histogram` and `search groupBy` footer their non-additive counts; `geo_when` has no equivalent note | **code stream** |
| A26 | `search {groupBy:"time,region", depth:2}` over a 3,000-year window emits one block per century with no cap or auto-coarsening | **code stream** — cap or coarsen |
| A27 | `discover`/`followups`/`assess` are network calls with no timeout or rate-limit signal in the output: an empty result is indistinguishable from an API outage | **code stream** |
| A28 | `places` silently accepts **either** an id or a name and the answers differ — `places:["anatolia"]` → 115 sources, `places:["Anatolia"]` → 94, `places:["Anatolian"]` → 0. The result depends on capitalisation | **code stream** — resolve names to ids, or say which path ran |

### Lens B — the prose an agent writes

All fixed in this stream.

| # | finding | where |
|---|---|---|
| B1 | The guide never made the agent state the filter **and its total**, nor place every returned citekey in *Sources cited* or *Not used* | guide §1 |
| B2 | No rule for an over-broad filter; now N > 40 → narrow with `facets` and state **both** filters | guide §1 |
| B3 | Contradiction hunting had no ordered procedure and no duty to report which steps ran | guide §2 |
| B4 | The guide named `cites` and `supersedes` as edge kinds — neither exists in the corpus | guide §2, all skills |
| B5 | Nothing told the agent to ignore `references → <topic-slug>`, which is 99% of the edge table | guide §2, `histmersion-query` |
| B6 | Citation format permitted a "plain citekey"; author-year and shortened keys were not banned | guide §3 |
| B7 | `contested` was missing from the status taxonomy in the guide and in all four skills | guide §3, all skills |
| B8 | "State the status" had no format; it is now inline at first citation, with the refuter/successor and year | guide §3 |
| B9 | Years on an overturned hypothesis were an example, not a rule; publication-year vs event-date provenance was undefined (now `n.d.` + a queued task) | guide §4 |
| B10 | Section 2 was always "period then region"; the axis now follows the question and must be named | guide §5 |
| B11 | One section set was applied to every output; report kind and article kinds now differ | guide §6 |
| B12 | The guide told the agent to write a *Sources used* list into every output, including articles where the page generates `Sources cited` | guide §6 |
| B13 | No rule for patching an existing article — read it first, append under the matching heading, never a second *Disagreements* | guide §6, `histmersion-compile` |
| B14 | No length discipline: "one paragraph per claim, not per source", ≤ 600 words in a terminal answer | guide §7 |
| B15 | "Delete every adjective" became "prefer a number to an adjective"; term-definition scope tightened (what to gloss, what not, reuse on append) | guide §8 |
| B16 | "The sources do not conflict" could be asserted without evidence; it must now list what was checked | guide §10 |
| B17 | The filter line opened the answer; it is now a trailing `_Method_` note | guide §9 |
| B18 | The skills carried "arrives in wave …" roadmap tables for tools that all exist today (`put_source`, `patch_source`, `export`, `ingest`, `assess`, `followups`, `discover`, `archive`, `calibre`, `places`, `lint`, `queue`, `query`) | all skills — deleted |
| B19 | Worked examples used invented citekeys (`cline2014`, `smith1971`, `jones2019`, `knapp2020`) and invented output; every example now uses real citekeys and pasted live output | `histmersion-query`, `histmersion-compile`, `histmersion-currency` |

### New in this stream

- **`skills/histmersion-currency/SKILL.md`** — the maintenance pass (retraction refresh → `assess` →
  `followups --since` → status transitions → `discover --not-in-db` → queue → article patch).
  `histmersion-query` was carrying half of it and answering questions badly as a result.
- **`tools/check_skills.sh`** — every `histmersion …` command in the skills must print a `Usage:` block,
  every MCP tool name must be in the live `tools/list`. Current run: 32 CLI commands ok, 1 skipped
  (a deliberately-quoted wrong form), **29 of 29** live tools named, `result: PASS`. Wire it into CI
  beside `gen-cli-docs.sh --check`.

### Handed to the code stream, in one list

A1 · A2 · A3 · A4 · A5 · A6 · A7 · A8 · A9 · A10 · A11 · A12 · A13 · A14 · A15 · A16 · A17 · A18 ·
A19 · A20 · A22 · A23 · A24 · A25 · A26 · A27 · A28 (A21 fixed here, docs only; its tool-description
audit remains).

The two lint rules the writing guide already names and calls in its final step:
**`article-filler`** and **`article-uncited-paragraph`** (A17, A18).

### Completed by stream w38 (code stream)

A1 (get_source sectioning: section/heading/offset/chars) ·
A2 (neighbors split Sources/Topics + contradictions endpoint + kinds filter) ·
A3 (per-parameter descriptions on every MCP tool input) ·
A7 (get_source body slicing with toc/offset/chars, fullText cap) ·
A10 (contradictions endpoint/tool/CLI) ·
A12 (snippet quality: exclude boilerplate headings, MaxFragments=1, HighlightAll=false) ·
A13 (followups trigger sentence + confidence + "proposed -- verify by reading") ·
A18 (get_article parts: body/summary/cited/backlinks with teasers) ·
A22 (neighbors: sources vs topics, status shown) ·
A23 (empty neighbors note: sparse disagreement edges) ·
A24 (get_article parts separates cited from body) ·
A28 (places search: place_names ILIKE for prefix matching).

New lint rule: **`superseded-without-edge`** (A10 companion).
New endpoint: `GET /api/contradictions?citekey=&since=` (A10).
New endpoint: `GET /api/sources/{ck}/view?section=&heading=&offset=&chars=` (A1/A7).
New facet: `provenance` (export/provenance item).
New config: `limits.full-text-chars = 60000`.
Export layout: `<dir>/sources/<ck>.md` + `--sources-dir/--articles-dir/--meta-dir`.
Verbose/brief search: `verbose=false` on MCP, `--brief` on CLI.
Provenance: shown in `get_source`, patchable via `metadata.provenance`, facet field.
Strict MCP inputs: unknown JSON keys rejected with accepted-keys message.
`patch_source` validation: `status=superseded` requires a `superseded_by` edge.

### Noted, but outside this stream's owned files

- `README.md` "CLI reference" (the hand-written examples below the generated block, ~lines 186–200)
  uses the **non-existent citekey `cline2014`** in `histmersion get cline2014 --full` and
  `histmersion neighbors cline2014`. The real key is `cline2014_year`. Same class as B19; the file section
  belongs to the code/CLI-docs stream.
