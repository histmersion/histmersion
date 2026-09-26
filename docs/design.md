# Histmersion — design

> The Docker deployment described below was replaced by the native bundle (2026-09); for install, run, backup and restore see [`README.md`](../README.md).

**Status:** the stack section below describes what is *built*, not what was once proposed
(updated 2026-08-25). Roadmap and open work: [`plan.md`](plan.md). Code rules: [`../AGENTS.md`](../AGENTS.md).

A Scala service that stores an OCR'd scholarly corpus in Postgres/PostGIS and serves it two ways:
**MCP + CLI** for agents (structured data as Markdown, so nothing is grepped or inferred) and a
**map × deep-time web UI** for people. Originals stay in Calibre; the database owns the text and the
enrichment.

## Store-of-record model

This inverts the research wiki's original rule. The authority is now the database:

| layer | store of record | backup |
|---|---|---|
| **Originals** (PDF/EPUB) | **Calibre** (canonical, unchanged) | Calibre's own library backup |
| **OCR'd text + metadata + enrichment** (geo/time/findings/tags/edges) | **Postgres** | `pg_dump` + the data volume |
| **LLM- and Obsidian-facing Markdown** | *generated from the database* | one-way export → `wiki/sources/*.md` (git/Nextcloud) |
| **Compiled wiki** (concepts/myths/reports) | Markdown files today; moving into `articles` in wave 2.7 | git/Nextcloud |

Three independent backup axes, one authority. Enrichment goes through MCP/CLI → database → export.

### The wire format is always Markdown

Storage and wire are deliberately different things. Stored: the OCR'd body **verbatim** in an
`ocr_markdown` column plus structured columns (topics/regions/`period`/findings/geom) *alongside* it,
so content is queryable. Returned: Markdown assembled from those rows. The model never sees a SQL row,
and `get_source(fullText=true)` gives back byte-for-byte what `md/<citekey>/paper.md` held — so the
export mirror is faithful and the reading experience is identical to files, only pre-filtered.

## Architecture

```
   host                                    docker compose
 ┌────────────┐                       ┌────────────────────────────────────────────────┐
 │ histmersion│ ──HTTP (Api endpoints)│  server  (Tapir + tapir-netty-server-sync)      │
 │  CLI       │ ─────────────────────►│    /mcp     MCP tools (Chimp)   ─┐               │
 │  (decline, │                       │    /api     REST (Tapir)        ─┼─► Handlers    │
 │   native)  │                       │    /source/ article pages       ─┘      │        │
 │            │                       │    /        web UI (Leaflet + timeline) │        │
 │  no DB     │                       │    Magnum ──► postgres · HTTP ──► converter      │
 └────────────┘                       └────┬──────────────────────┬──────────────────────┘
                                           ▼                      ▼
                                  ┌──────────────┐   ┌──────────────────────┐
                                  │ postgres     │   │ converter (Python)   │
                                  │ PostGIS ·    │   │ docling/pymupdf4llm  │
                                  │ int8range ·  │   │ OCR — quality-first  │
                                  │ GiST·Flyway  │   │ uv + FastAPI         │
                                  └──────────────┘   └──────────────────────┘
```

Compose services: **postgres** (PostGIS image, bundled — no host DB install), **server** (Scala native
image), **converter** (Python OCR sidecar; Python because docling gives the best OCR, managed with uv).

The CLI runs on the host, where Calibre lives, but is a **pure HTTP client**: no pgjdbc in the binary,
no database credentials outside the container. Host-local integrations (Calibre) upload through
server endpoints rather than reaching into the database. Writes are protected by publishing the server
on `127.0.0.1` only; `HISTMERSION_BIND=0.0.0.0` is the explicit LAN opt-in.

## Stack (as built)

Chimp (the MCP library) is not cats-effect, so the whole server is **direct style** — one coherent
SoftwareMill stack, and a good native-image story (no effect runtime).

- **Build:** sbt 2.0.7, Scala 3.8, GraalVM CE 25.0.1 (pinned in `.sdkmanrc` — see `../AGENTS.md`).
  Four modules: `core` (contract) · `db` (store) · `server` · `cli`.
- **HTTP:** **Tapir** → **`tapir-netty-server-sync`** (direct style, `Identity` effect). Endpoint
  definitions live in `core`, so the server's routes and the CLI's sttp client are derived from the
  same values and cannot drift.
- **MCP:** **Chimp** (`chimp-server`) — type-safe tool definitions on Tapir, streamable HTTP at `/mcp`.
  Tools return text content = Markdown.
- **Database:** **Magnum** (direct-style JDBC) + PostgreSQL + PostGIS. `int8range` for signed years,
  PostGIS geometry moved as WKT text (not `postgis-jdbc` binary types) — native-image-friendly, and
  Postgres still does all the spatial work.
- **Schema:** **Flyway**, SQL migrations in `db`, applied at server startup before the port is bound.
  Migrations are read through a generated `index.txt` because classpath scanning fails natively.
- **JSON + schemas:** **Circe** and Tapir `Schema`, both derived from the same case class by
  **Kindlings** (Hearth-based, macro-generated, reflection-free) — they match by construction.
  Config is the same mechanism over HOCON (sconfig).
- **Concurrency:** **Ox** where handlers need structured concurrency.
- **CLI:** **decline**, over the shared `core` endpoints.
- **Logging:** scribe (`scribe-slf4j` bridges Flyway) — native-image-safe.
- **Packaging:** **GraalVM `native-image`** for both binaries via a custom `nativeImage` sbt task (no
  sbt-native-image plugin for sbt 2 yet); tracing-agent metadata is collected automatically on forked
  `run` and committed. Multi-stage Docker: GraalVM builder → `debian:bookworm-slim` runtime.

*(Superseded by the above: the original draft named Doobie, http4s and cats-effect, and a two-module
`core`/`server` split with all logic in `core`. None of that shipped.)*

## Schema (store of record for source content)

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
  calibre_id text, source text,            -- provenance; the original stays in Calibre
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
```

Later migrations add FTS chunks, assets, places and articles (`plan.md`). Adjacency queries map to
index-backed Postgres operators: `&&` overlap · `@>` contains · `-|-` adjacent periods ·
`<->` PostGIS KNN · `ST_DWithin`.

**`int8range` gotcha:** Postgres normalises `[a,b]` to `[a,b+1)`, so the inclusive upper bound read
back is `upper − 1`.

## Surfaces

Every operation is implemented **once** in `server/Handlers`; the REST route, the MCP tool and the CLI
command are three views of it, all rendering the same Markdown. Current tools: `ping`, `search`,
`get_source`, `geo_when`, `neighbors`. Endpoints, config keys and CLI commands are tabulated in
[`../README.md`](../README.md); the roadmap (filters/facets, FTS, writes, aggregation, gazetteer,
articles) is in `plan.md`.

## OCR flow

Calibre import or upload → **converter** runs docling (`--force-quality` for image-only scans) →
returns Markdown → the server stores `ocr_markdown` + skeleton metadata → an agent enriches it
(regions, period, findings) through `patch_source`. Quality first; OCR is slow and runs once.

## Backup

- **Postgres:** `pg_dump -Fc` into the bind-mounted `backups/` volume, plus the data volume for a
  physical snapshot. Restore = `pg_restore` into a fresh container. Commands in `../README.md`;
  `histmersion backup`/`restore` wrap them in wave 2.5.
- **Markdown export:** `histmersion export` rewrites `wiki/sources/*.md` from the database — a portable,
  Obsidian-usable second axis, independent of Postgres.
- **Originals:** remain in Calibre.

## Decisions settled

1. **Store-of-record inversion** — confirmed: the database is authoritative for OCR/enrichment,
   Markdown is a generated mirror. (This replaces the research wiki's "Markdown is the single source
   of truth" rule.)
2. **Compiled wiki** — moves into the database as `articles` (wave 2.7) and is exported back to
   Markdown for Obsidian, rather than staying file-only.
3. **CLI ↔ server coupling** — the CLI is a *pure* HTTP client, including for writes; host-local work
   (Calibre) uploads through endpoints.
4. **Bind/auth** — localhost-only publish is the protection; `HISTMERSION_BIND=0.0.0.0` is an explicit
   opt-in. No auth layer yet.
5. **Region normalisation** — done during seed import.
6. **Nativization** — GraalVM `native-image` for both binaries, with the tracing-agent metadata step
   accepted as part of the build.
