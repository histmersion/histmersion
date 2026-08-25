# hist-db

A geo-temporal research database for the LLM-wiki: OCR'd scholarly sources + metadata, stored in
**Postgres/PostGIS**, served to LLMs as **Markdown over MCP** and to humans as a
**World-Historical-Gazetteer-style web UI** (map × deep-time timeline). Replaces a folder of Markdown
files + grep with a **single CLI + MCP pair**.

- **Originals** live in **Calibre** (canonical). hist-db **OCRs** them and stores the text + geo/time
  enrichment in Postgres (backed up via `pg_dump`). Markdown is the *wire format* to the LLM and an
  export mirror — never grepped, never inferred.
- Design: [`docs/design.md`](docs/design.md).

## Toolchain (reproducible)
Pinned in [`.sdkmanrc`](.sdkmanrc) — GraalVM CE 25 (ships `native-image`), sbt 2.0.7, Scala 3.8:
```bash
sdk env install     # installs the pinned java/sbt/scala
sdk env             # activate for this shell (auto with sdkman_auto_env=true)
```
sbt launcher version is also pinned in `project/build.properties`.

## Modules
| module | what | binary |
|---|---|---|
| `core`   | the contract: domain model · wire DTOs · Tapir endpoint defs · Markdown rendering · config model (no DB) | — |
| `db`     | Magnum repos over Postgres/PostGIS · Flyway migrations · seed importer | — |
| `server` | MCP (Chimp) + REST (Tapir/netty-sync) + web UI; the only DB client | native image (Docker) |
| `cli`    | host-run **pure HTTP client** of the server (sttp client derived from `core`'s endpoints) | native image |

Both binaries are GraalVM `native-image` builds — fast startup, lean RSS.

## Run (dev)
```bash
docker compose up -d postgres                # bundled Postgres/PostGIS (schema applied by the server via Flyway)
sbt server/run                               # MCP + REST + web UI on $HISTDB_PORT (default in reference.conf)
sbt "cli/run seed /path/to/wiki/sources"     # import the corpus (path as seen by the SERVER; idempotent)
sbt "cli/run search collapse"                # or: get <citekey> · geo-when --region --from --to --bbox · neighbors · health
open http://localhost:8000                    # the atlas: Leaflet map × deep-time timeline
sbt test                                     # pure tests; set HISTDB_TEST_DB_URL for the DB/HTTP suites
```
MCP is wired for Claude Code (`.mcp.json`, `type:http`, `<server>/mcp`); tools:
`ping`, `search`, `get_source`, `geo_when`, `neighbors` — all returning Markdown. All ports/URLs/limits
live in `modules/core/src/main/resources/reference.conf` (env overrides `HISTDB_*`); nothing is hardcoded.

## Status
Working end-to-end: seed importer (209 sources · 964 findings · 84 MB full text · 1066 typed edges),
five read MCP tools + CLI, map/timeline web UI, both GraalVM native images, Flyway-managed schema.
Roadmap: [`docs/plan.md`](docs/plan.md) (filters, full-text, gazetteer, images, packaging).

## License
TBD.
