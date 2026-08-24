# Atlas — containerized Scala MCP server + CLI for the research wiki

**Status:** design draft (2026-06-30) · **Provisional name:** *Atlas* — rename freely.

A **Scala** system, shipped as a two-module build:
- **`server`** — a Dockerized service exposing an **MCP** surface (so the LLM gets structured data as
  Markdown instead of grepping + inferring geo/time) and a **World-Historical-Gazetteer-style web UI**
  (visualize/filter/search papers by period × place). Owns a bundled **Postgres/PostGIS** database.
- **`cli`** — a host-run command that (a) is a thin client of the server's API and (b) does the
  **local** things a container can't — chiefly talk to **Calibre**. Replaces the current Python `tools/`
  scripts. Everything we do today via scripts becomes this **single CLI + MCP pair**.

## Store-of-record model (this inverts the current rule)
The existing project makes `wiki/sources/*.md` the single source of truth and the DB a throwaway index.
Per the new requirement (*"originals in Calibre, but OCR'd + stored inside the server's database, which
must be backupable"*), the authority flips:

| layer | store of record | backup |
|---|---|---|
| **Originals** (PDF/EPUB) | **Calibre** (canonical, unchanged) | Calibre's own library backup |
| **OCR'd text + metadata + enrichment** (geo/time/findings/tags/links) | **Postgres** (the server DB) | `pg_dump` (logical) + data-volume snapshot |
| **LLM- & Obsidian-facing Markdown** | *generated from the DB* | one-way export → `wiki/sources/*.md` (git/Nextcloud) = a 2nd, human-readable backup axis |
| **Compiled wiki** (concepts/myths/reports — authored synthesis) | **Markdown files** (unchanged; the Obsidian "wiki output") | git/Nextcloud |

So there are **three independent backup axes** (Calibre originals · Postgres dump · Markdown export) and
no dual-source-of-truth conflict: **DB is authoritative for source content; Markdown is a derived
mirror.** Enrichment/edits go through MCP/CLI → DB → re-export `.md`. The existing 200+ `.md` source
notes become the **seed import** into Postgres on first run. *(**Confirmed 2026-06-30** — this replaces
CLAUDE.md's "Markdown is the single source of truth" golden rule; update it at build time.)*

### Wire format: the LLM always gets Markdown
The inversion changes *storage*, not the *format served to models*. **Every MCP tool and CLI command
returns Markdown.** Storage vs wire:
- **Stored** in Postgres: the OCR'd body **verbatim** in an `ocr_markdown` TEXT column, plus structured
  columns (topics/regions/`period`/findings/geom) *alongside* it, only so content is queryable/filterable.
- **Returned** to the LLM: Markdown assembled from those rows — `get_source` → a note (header + summary +
  findings + optional full body); `search`/`geo_when`/`neighbors` → Markdown lists; `histdb://…` resources
  → Markdown. The model never sees a SQL row.
- Because the body is stored as text (not shredded), `get_source(full_text=true)` returns **byte-for-byte
  the same Markdown** as today's `md/<citekey>/paper.md`. Lossless round-trip → the `.md` export mirror is
  faithful, and the LLM's experience is identical to reading files, only pre-filtered by geo/time/topic.

## Architecture (docker compose + host CLI)

```
   host                                   docker compose
 ┌────────────┐                       ┌───────────────────────────────────────────────┐
 │  cli       │ ──HTTP (server API)──►│  server  (Scala: http4s + Tapir + cats-effect) │
 │  (Scala,   │                       │    /mcp   MCP (JSON-RPC / Streamable HTTP)      │
 │   decline) │                       │    /api   REST (Tapir)                          │
 │            │                       │    /      static web UI (Leaflet + timeline)    │
 │  shells to │                       │    Doobie ──► postgres  · HTTP ──► converter    │
 │  calibredb │                       └────┬───────────────────┬──────────────────────┘
 │  (Calibre) │                            ▼                   ▼
 └────────────┘                    ┌──────────────┐   ┌──────────────────────┐
        │ pushes originals         │ postgres     │   │ converter (Python)   │
        │ + triggers OCR           │ PostGIS +    │   │ docling / pymupdf4llm │
        └─────────────────────────►│ int8range +  │   │ OCR — quality-first   │
                                   │ GiST         │   └──────────────────────┘
                                   └──────────────┘
```

Compose services: **`postgres`** (PostGIS image, bundled → no host DB install), **`server`** (Scala),
**`converter`** (Python OCR sidecar — kept in Python because docling gives the best OCR, per your call;
managed with **uv/Astral** — `pyproject.toml` + `uv.lock`, uv-based Docker image).
The **CLI runs on the host** where Calibre lives; the container never needs Calibre.

## Stack (your picks)
- **Build:** **sbt 2.0**, Scala 3. Multi-module: `core`, `server`, `cli`.
- **HTTP + endpoints:** **Tapir** → **http4s** (ember). Tapir derives OpenAPI + the static-file routes.
- **Effect:** cats-effect `IO`.
- **DB:** **Doobie** + PostgreSQL JDBC + PostGIS. Custom `Meta`/`Get`/`Put` for `int8range` (signed
  years) and PostGIS `geometry` (WKT ↔ `ST_GeomFromText`).
- **JSON:** **jsoniter-scala, wired via Kindlings**. Used for the REST DTOs and the MCP JSON-RPC codecs.
- **MCP:** hand-rolled JSON-RPC 2.0 over http4s (methods: `initialize`, `tools/list`, `tools/call`,
  `resources/list`, `resources/read`), tool schemas emitted from the Tapir/jsoniter models.
- **HTTP client (discovery):** sttp (OpenAlex/Crossref/Semantic Scholar).
- **CLI:** decline (or scopt) → same `core` service functions; can run against a live server (HTTP) or
  do local work (Calibre, files, direct Doobie).
- **Packaging:** **GraalVM native-image** for both binaries (see next section) — fast startup, lean RSS,
  small images; peak throughput deliberately not a goal.

## Runtime & nativization (fast startup, lean footprint)
Goal: start in ~tens of ms and idle small; we accept lower peak throughput than a warmed JVM. Two paths:

**Decided (2026-06-30): GraalVM `native-image` for both `cli` and `server`.** Keeps your exact stack
(Doobie/http4s/Tapir/cats-effect/jsoniter-Kindlings); produces a single static-ish binary per module.
- **CLI:** the big win — ad-hoc invocation goes from ~1 s JVM cold start to ~10–50 ms; ~20–40 MB RSS;
  one distributable binary, no JRE on the host.
- **Server:** long-running so startup matters less, but native gives a **distroless/static image
  (~40–80 MB vs ~200 MB JRE)** and low idle RSS — the "lean on the host" you want for a laptop compose stack.
- **Why your picks are already native-friendly:**
  - **jsoniter-scala (via Kindlings)** is macro-generated, **zero runtime reflection** → the ideal
    native-image serializer (this is a reason to prefer it over reflective JSON libs).
  - **Read PostGIS geometry as WKT text** (`ST_AsText`/`ST_GeomFromText`) through plain Doobie `Meta`,
    rather than the `postgis-jdbc` binary types → avoids the one dependency that needs fiddly native
    config; Postgres still does all the spatial work. `int8range` likewise maps via a text/`Meta` codec.
  - cats-effect + http4s (ember) + pgjdbc all have known-good native-image setups.
- **Build workflow:** run once under the **native-image tracing agent** to capture reflection/resource/
  JNI metadata, commit it under `src/main/resources/META-INF/native-image/`; build via a **custom
  `nativeImage` sbt task** (sbt-native-image isn't published for sbt 2.0 yet — the task shells out to
  GraalVM `native-image` over the module classpath); multi-stage Docker (GraalVM build stage → binary →
  `gcr.io/distroless/base` or `scratch` runtime). GraalVM's shared reachability-metadata
  repo already covers pgjdbc/netty. Cost: native builds are slow (minutes) and the metadata needs
  maintenance when deps change — real but well-trodden.

**Alternative — Scala Native** (leaner still, no JVM at all): viable **only if we swap Doobie → Skunk**
(Doobie is JDBC = JVM-only; Skunk is native-protocol and cross-compiles to Native), and accept
less-mature http4s-on-Native. Best reserved for a *maximally-tiny CLI* if we ever want it; it conflicts
with the Doobie pick for the server, so not the default.

**Fallback / hybrid** if native-image config proves painful: ship the **CLI native** (where startup
matters most) and the **server on a tuned JVM** (AppCDS + `-XX:+UseSerialGC` + tiered-stop-at-1 →
~few-hundred-ms start, modest RSS). The server starts once, so this hybrid captures most of the benefit
at a fraction of the build complexity.

## Module layout
```
build.sbt                       (sbt 2.0, Scala 3)
modules/
  core/     domain model · Doobie repos · discovery clients · markdown<->model ser · OCR client · sync/export
  server/   Tapir endpoints + http4s + MCP JSON-RPC + static web UI      (depends on core; Docker)
  cli/      decline app; host-run; shells to calibredb; thin server client (depends on core)
converter/  Python docling OCR service (its own Docker image)
docker-compose.yml   postgres(postgis) + server + converter
```
`core` holds all logic; `server` and `cli` are two front-ends over it — so a capability is written once
and exposed both as an MCP tool and a CLI subcommand.

## Postgres/PostGIS schema (store of record for source content)
```sql
CREATE EXTENSION postgis;
CREATE EXTENSION btree_gist;

CREATE TABLE sources (
  citekey text PRIMARY KEY,
  doi text, title text, authors text[], year int, venue text,
  status text, topics text[], regions text[], tags text[],
  period int8range,                        -- signed years, BCE negative
  cited_by_count int, influential_citations int,
  summary text,
  ocr_markdown text,                       -- the OCR'd full text lives IN the DB (backupable)
  calibre_id text, source text,            -- provenance; original stays in Calibre
  sha256 text
);
CREATE INDEX ON sources USING gist (period);

CREATE TABLE findings (
  id text PRIMARY KEY, citekey text REFERENCES sources,
  claim text, region_name text,
  geom geometry(Point,4326), period int8range,
  period_name text, tags text[], confidence text
);
CREATE INDEX ON findings USING gist (geom);
CREATE INDEX ON findings USING gist (geom, period);   -- true spatiotemporal (btree_gist)

CREATE TABLE citation_edges (src text, dst text, kind text);
-- P3 gazetteer/periodization: places(…, geom, parent_id, pleiades_uri) · periods(…, int8range, region, periodo_uri)
```
Adjacency for `geo_when` → index-backed Postgres operators: `&&` overlap · `@>` contains · `-|-`
**adjacent** periods · `<->` PostGIS KNN (nearest places) · `ST_DWithin` (within N km).

## Backup
- **Postgres:** `pg_dump -Fc` on a schedule → a mounted, Nextcloud/git-backed directory; plus the data
  volume for physical snapshot. Restore = `pg_restore` into a fresh container. `cli backup` / `cli restore`
  wrap this.
- **Markdown export:** `cli export` writes/refreshes `wiki/sources/*.md` from the DB — a portable,
  human-readable, Obsidian-usable second backup, independent of Postgres.
- **Originals:** remain in Calibre.

## MCP surface (LLM: no grep / no inference) — language-agnostic contract
```
search(query?, topics?, regions?, status?, period_start?, period_end?, year_min?, year_max?, limit=20) -> md
get_source(citekey, full_text=false) -> md          # note; full_text pulls ocr_markdown from the DB
geo_when(region?|bbox?, period_start?, period_end?, include_adjacent=true, limit=50) -> md
neighbors(citekey, by=[citation|region|period|topic]) -> md
suggest_acquisitions(topic?|citekey?, since_year?, limit=15) -> md   # OpenAlex/Crossref/S2, dedup, rank
ingest(doi?|arxiv?|calibre_id?|file_path?) -> md     # → fetch/Calibre → converter OCR → store in DB
enrich(citekey, patch{topics?,regions?,period_start?,period_end?,status?,findings?,summary?,tags?}) -> md
link(from, to, kind="cites"|"wikilink"|"supersedes") -> md
tag(citekey, add?, remove?) -> md
```
Resources: `histdb://index`, `histdb://concept/<slug>`, `histdb://report/<slug>`, `histdb://source/<citekey>`.
**`enrich`** is how the LLM records geo/time as a structured write (→ DB), never inferred from a grep.

## CLI surface (replaces Python `tools/`; host-side + thin client)
```
histdb serve                        # (dev) run server locally without Docker
histdb calibre search|import <id>   # host-only: shell to calibredb → push original → OCR → store
histdb ingest <doi|arxiv|file>      # fetch/convert/store (server API or local)
histdb search / geo-when / neighbors / suggest      # same queries as MCP, from the terminal
histdb enrich / tag / link          # writes
histdb export | backup | restore    # DB → .md mirror ; pg_dump ; pg_restore
histdb sync                         # reconcile DB ↔ Markdown export
```
Calibre lives on the host, so `histdb calibre import <id>` runs `calibredb`, hands the original to the
converter for OCR, and stores the result in Postgres via the server. The container stays Calibre-free.

## Web UI (`/`) — WHG-style, served by the Scala app
Leaflet map (findings as points, colored by period) + a **BCE-aware range slider** (standard date libs
don't do negative years) + facet filters + result list + source card. Map layer ←
`GET /api/findings?bbox&period_start&period_end&topics` (GeoJSON). Region-select / draw-bbox → the
`geo_when` query. Export selection → GeoJSON / Kepler.gl. Vendored Leaflet, no npm build.

## REST (Tapir; UI + MCP + CLI share `core` service fns)
```
GET  /api/search · /api/findings(GeoJSON) · /api/source/{citekey} · /api/neighbors/{citekey} · /api/facets
POST /api/ingest/file (multipart→converter) · /api/suggest · /api/source/{citekey}/enrich · /{citekey}/tag · /api/link
```

## OCR flow (quality-first)
`histdb calibre import <id>` or upload → **converter** runs docling (`--force-quality` for image-only
scans) → returns markdown → server stores `ocr_markdown` + skeleton metadata in Postgres → enrich-assist
proposes candidate places (gazetteer/NER) + periods (PeriodO) → human/LLM confirms via `enrich`.

## Acquisition suggestions
`suggest_acquisitions` (Scala/sttp → OpenAlex/Crossref/S2) dedups against `sources` + Calibre, ranks +
classifies (update/challenge/refute), and one-clicks → `meta/ACQUIRE.md` (book-gated) or `ingest`.

## Deploy / run
```bash
docker compose up          # postgres(PostGIS) + server + converter
# Claude Code MCP config: hist-db -> http://localhost:8000/mcp
open http://localhost:8000
# host:
histdb calibre import 1645  # OCR a Calibre book into the DB
histdb backup               # pg_dump to the backed-up dir
```

## Phasing
- **P1:** compose (postgres+server) · seed-import the 200 `.md` into Postgres · read-only MCP tools
  (`search`/`get_source`/`geo_when`/`neighbors`) + resources + the map/timeline UI. Kills grep, delivers viz.
- **P2:** converter sidecar + OCR/Calibre ingest (`cli calibre import`, upload) + write-back
  (`enrich`/`tag`/`link`) + `export`/`backup`/`restore` + enrich-assist.
- **P3:** `suggest_acquisitions` + ACQUIRE.md wiring; gazetteer + PeriodO tables for named-adjacency;
  retire the Python `tools/` once the CLI reaches parity.

## Open questions
1. **Store-of-record inversion** — confirm the DB becomes authoritative for OCR/enrichment with Markdown
   as a generated mirror (updates CLAUDE.md's golden rule). This is the one real semantic change.
2. **Compiled wiki** — keep concepts/myths/reports as authored **Markdown files** (recommended; preserves
   the Obsidian workflow), or also move them into the DB? Default: files.
3. **CLI ↔ server coupling** — CLI hits the server's HTTP API for shared ops (single code path), and only
   drops to direct-Doobie/Calibre for host-local work. OK?
4. **Bind/auth** — localhost-only (assumed); any write-protection on mutating endpoints?
5. **Region normalization** — one-time casing/dupe fix (`Anatolia`/`anatolia`) during the seed import.
   Recommended yes.
6. **Nativization strategy** — ✅ **Decided: GraalVM native-image for both** cli + server (hybrid =
   native CLI + tuned-JVM server kept as the fallback only if the server's native config fights us).
   Accepts the native-image tracing-agent metadata step in the build.
```
```
