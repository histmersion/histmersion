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
| `core`   | domain model · Doobie repos · discovery clients · markdown ser · sync | — |
| `server` | MCP (JSON-RPC) + REST (Tapir/http4s) + web UI; owns the DB | native image (Docker) |
| `cli`    | host-run: thin server client + local Calibre/file ops | native image |

Both binaries are GraalVM `native-image` builds — fast startup, lean RSS.

## Run (dev)
```bash
docker compose up -d postgres converter      # bundled Postgres/PostGIS + OCR sidecar
sbt server/run                               # http4s server on :8000  (native build: sbt server/nativeImage)
sbt "cli/run version"                        # the CLI
```
Add to Claude Code MCP config once `/mcp` lands: `hist-db -> http://localhost:8000/mcp`.

## Status
Phase-1 scaffold: build + toolchain pinning, Postgres schema, compose, OCR sidecar, and skeleton
`server`/`cli`. Next: seed-import the existing `.md` corpus, the read MCP tools
(`search`/`get_source`/`geo_when`/`neighbors`), and the map/timeline UI. See `docs/design.md` §Phasing.

## License
TBD.
