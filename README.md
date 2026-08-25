# hist-db

**A geo-temporal research database for a scholarly corpus, with two front doors: a Wikipedia-style
atlas for people and an MCP tool surface for agents.** Books and papers are OCR'd once and stored in
Postgres/PostGIS as full text plus structured enrichment — geo-located, deep-time-scoped *findings*
and typed citation edges. Humans discover and read sources on a map crossed with a timeline and hop
between them through their citations; agents answer questions, summarise and compile prose against the
same data through MCP tools and a CLI. Neither ever greps a folder of markdown files or infers a date
from prose, which is the entire point.

The domain is comparative mythology, folklore, Bronze-Age history and Indo-European
origins/migrations, but nothing in the schema is specific to it.

- **Originals** live in **Calibre** (canonical). hist-db OCRs them and stores the text + enrichment in
  Postgres (backed up with `pg_dump`). Markdown is the *wire format* to the model and an export mirror.
- Design notes: [`docs/design.md`](docs/design.md) · Roadmap: [`docs/plan.md`](docs/plan.md) ·
  Contributing: [`AGENTS.md`](AGENTS.md) · Writing rules: [`docs/writing-guide.md`](docs/writing-guide.md)

## Architecture

```
  host                                      docker compose network
┌──────────────────┐                  ┌──────────────────────────────────────────────────┐
│ histdb (CLI)     │                  │  server            (GraalVM native image, 59 MB)  │
│ native binary    │───── HTTP ──────►│    /mcp     MCP tools (Chimp)      ─┐              │
│ pure HTTP client │   (127.0.0.1)    │    /api     REST (Tapir)           ─┼─► Handlers   │
└──────────────────┘                  │    /source/ article pages          ─┘     │        │
                                      │    /        atlas: map × timeline         │        │
┌──────────────────┐                  │                                           │        │
│ agent (MCP)      │───── HTTP ──────►│                            Magnum (JDBC) ─┤        │
│ Claude Code /    │                  └──────────────────┬───────────────────────┬┴────────┘
│ OpenCode         │                                     ▼                       ▼
└──────────────────┘                          ┌────────────────────┐   ┌────────────────────┐
                                              │ postgres           │   │ converter (Python) │
┌──────────────────┐                          │ PostGIS · int8range│   │ docling /          │
│ browser → atlas  │───── HTTP ──────────────►│ GiST · Flyway      │   │ pymupdf4llm  (OCR) │
└──────────────────┘                          └────────────────────┘   └────────────────────┘

corpus dir ──bind-mount (ro) /corpus──► server   (server-side `seed`)
```

Four sbt modules, one rule each:

| module | what | depends on | binary |
|---|---|---|---|
| `core` | **the contract**: domain model · wire DTOs · `Filter` · Tapir endpoint definitions · Markdown rendering · config model · frontmatter parsing. No database code. | — | — |
| `db` | **the store**: Magnum repos over Postgres/PostGIS · Flyway migrations · the seed importer | `core` | — |
| `server` | MCP (Chimp) + REST (Tapir/netty-sync) + article pages + the atlas. **The only database client.** | `core`, `db` | native image (Docker) |
| `cli` | **a pure HTTP client** of the server — sttp client derived from `core`'s endpoints. No pgjdbc, no DB credentials on the host. | `core` **only** | native image |

Because `core` defines each endpoint once and `server` implements it once in `Handlers`, the REST
route, the MCP tool and the CLI command are the *same* operation with the *same* Markdown output.
They agree by construction, not by discipline.

## Install (one command)

Needs [Docker](https://docs.docker.com/get-docker/) and [sdkman](https://sdkman.io) (for the pinned
GraalVM + sbt used to build the CLI binary).

```bash
git clone git@github.com:MateuszKubuszok/hist-db.git
cd hist-db
cp .env.example .env      # optional — edit the port, corpus dir, …
./install.sh
```

`install.sh` is idempotent and prints a plan first with `--dry-run`. It:

1. checks Docker and sdkman (`sdk env install` for the toolchain in `.sdkmanrc`),
2. builds the CLI native image and installs it to `~/bin/histdb` (warning if `~/bin` is not on `PATH`),
3. `docker compose build && docker compose up -d`,
4. waits for `/api/health`,
5. **generates `.mcp.json` and `plugin/.mcp.json`** from the configured port/public URL,
6. prints the next steps.

Then:

```bash
histdb health
histdb seed                       # import the corpus mounted at /corpus
open http://localhost:8000        # the atlas
```

`make install` is an alias. `make help` lists everything else.

## Run

### Docker (normal use)

```bash
make up          # docker compose up -d
make logs        # follow the server
make health
make down
```

The server is published on **`127.0.0.1` only** — the write endpoints are unauthenticated.
Postgres is deliberately **not** published: the server is its only client.

**Exposing on the LAN:** set `HISTDB_BIND=0.0.0.0` in `.env` and re-run `make up`. Only do this on a
network you trust; there is no authentication yet. Set `HISTDB_PUBLIC_URL` to the address clients
should use, then re-run `./install.sh` so `.mcp.json` is regenerated.

### Development (sbt, no server container)

```bash
make dev-up                       # postgres + converter, with Postgres published on 5432
sbt server/run                    # MCP + REST + atlas on $HISTDB_PORT
sbt "cli/run search collapse"
sbt test                          # pure suites; set HISTDB_TEST_DB_URL for the DB/HTTP suites
```

`make dev-up` uses `docker-compose.dev.yml`, which exists solely to publish Postgres for host-side
development. See [`AGENTS.md`](AGENTS.md) and the `histdb-dev` skill for the full workflow.

## CLI reference

<!-- BEGIN CLI REFERENCE (generated by tools/gen-cli-docs.sh — do not edit by hand) -->

| command | what it does |
|---|---|
| `histdb version` | Print the CLI and server versions |
| `histdb health` | Server health (db, schema version) |
| `histdb seed [--force] [<dir>]` | Import a wiki/sources directory — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir) |
| `histdb reindex` | Rebuild the full-text chunk index for every source |
| `histdb search [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [<query>]` | Search the corpus (websearch syntax: "phrase", -not, or): sources with the findings that matched |
| `histdb facets [--prefix <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] <field> [<query>]` | Vocabulary of a dimension (tags|authors|places|topics|status) with distinct-source counts |
| `histdb get [--full] <citekey>` | Show one source as Markdown |
| `histdb geo-when [--region <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>]` | Findings (the map feed) by place, deep time, bbox — the same filters as `search` |
| `histdb histogram [--region <string>] [--unit <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [<query>]` | When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search |
| `histdb neighbors <citekey>` | Typed references to/from a source |
| `histdb put <file.md>` | Send ONE note file (frontmatter + body) as a full create/replace |
| `histdb patch [--json <string>] [--tags-add <string>] [--tags-remove <string>] [--tags-replace <string>] [--topics <string>] [--regions <string>] [--summary <string>] [--status <string>] [--title <string>] [--notes-file <string>] [--period <string>] [--add-finding <string>]... [--remove-finding <string>]... [--if-updated-at <string>] <citekey>` | Selectively enrich one source — only the parts you pass are changed |
| `histdb export [--meta-dir <string>] [--no-meta] <dir> [<citekey>...]` | Write each source to <dir>/<citekey>.md (frontmatter + notes body) and the bookkeeping queues to <dir>/meta/*.md |
| `histdb lint [--rule <string>]... [--fix] [--json]` | Health-check the corpus (13 rules: dangling-edges, unresolved-short-citekeys, no-findings, no-period, no-regions, empty-summary, unassessed-status, duplicate-doi, duplicate-sha256, orphan-sources, stale-chunks, undated-findings, unlocated-findings) |
| `histdb queue [--topic <string>] [--all] [--json] [<args>...]` | Bookkeeping queues: acquire / tasks / log — show, add, done, rm, export, import |
| `histdb query [--json] <sql>` | Run ONE read-only SQL statement (a write is refused by Postgres itself) |
| `histdb schema [--json]` | List the tables and columns `query` can read (from information_schema) |

Global: `--server <url>` overrides the configured server (`$HISTDB_SERVER_URL`).

<!-- END CLI REFERENCE -->

Regenerate that table from the CLI's own `--help` with `make cli-docs`
([`tools/gen-cli-docs.sh`](tools/gen-cli-docs.sh)); `--check` fails if it is stale.

Every command prints the **same Markdown** the matching MCP tool returns. Deep time is a **signed
integer year**: 3300 BCE → `-3300`.

```bash
histdb search "sea peoples" --limit 30
histdb get cline2014 --full
histdb geo-when --region Levant --from -1250 --to -1100
histdb geo-when --bbox 25,35,40,42 --from -1400 --to -1100
histdb neighbors cline2014
```

## MCP tools

Wired for Claude Code through `.mcp.json` (`type: http`, `<public-url>/mcp`), generated by
`install.sh`. Every tool returns Markdown.

| tool | when to use it |
|---|---|
| `ping` | liveness + version. First call when anything looks wrong. |
| `search` | **the entry point for any topic question.** Full-text query (websearch syntax: `"phrase"`, `-not`, `or`) plus every filter: `tags`/`authors`/`places`/`topics`/`status`, publication years, a deep-time window, a bbox, `sort`. The SOURCE is the unit of each result; every hit lists its `matches:` (the findings that satisfied the filter, with why) and a `>` snippet of the passage that matched. |
| `facets` | the vocabulary of one dimension (`tags`, `authors`, `places`, `topics`, `status`) **inside the current filter**, with distinct-source counts — call it before guessing a tag. |
| `get_source` | after `search`: metadata, summary, **notes** (the compiled article), findings for one citekey. `fullText: true` pulls the whole OCR'd body (hundreds of KB — use sparingly). |
| `geo_when` | "what happened *here*, *then*" — individual findings filtered by place, bbox (antimeridian-safe), deep-time window and any other filter. |
| `histogram` | "when is the evidence" — sources (or findings) per adaptive time bucket for a filter; the same aggregation the atlas timeline draws. |
| `neighbors` | **always, before relying on a claim.** Typed edges (`references`/`updates`/`challenges`/`refutes`/`supersedes`/`superseded_by`) to and from a source. A `refutes` edge changes the answer. |
| `put_source` | create or **replace** a whole source (destructive; only for creating or deliberately re-importing). |
| `patch_source` | **the enrichment tool**: send only the parts you change — `metadata`, `summary`, `body`, `notes`, `topics`, `regions`, `period`, `tags{add,remove,replace}`, `findings{add,remove,replace}`, `edges{add,remove}`; `ifUpdatedAt` guards against clobbering. |
| `export_source` | the source as the Markdown note it would be on disk (frontmatter + notes body) — the git-friendly mirror. |
| `lint` | **after a batch of ingestion.** Health-check the corpus against 13 rules (dead reference targets, unresolved short citekeys, sources with no findings/period/regions/summary, `unassessed` status, duplicate DOI/sha256, orphans, missing full-text chunks, undated/unlocated findings). `fix: true` first applies the two safe repairs. |
| `queue` | the project's bookkeeping in one tool: `action` = `list` \| `add` \| `done` over the three lists `acquire` (what the USER must obtain), `tasks` (the agent's backlog), `log` (the dated worklog). Use it instead of writing a queue/tracker file. |
| `query` | one **read-only** SQL statement for the questions the other tools cannot express. Runs in a `READ ONLY` transaction with a statement timeout and a row cap, so Postgres itself refuses every write. Its description lists the live tables, generated from `information_schema`. |

Guidance for agents lives in the shipped skills — `histdb-query` (answering a question),
`histdb-ingest` (getting sources in), `histdb-compile` (writing the answer up), `histdb-dev`
(changing the code). Install them with the plugin: [`plugin/README.md`](plugin/README.md).

> Roadmap: `facets`, richer `search` filters, `aggregate`/`histogram`, and the write tools
> (`put_source`, `patch_source`, `export`) are wave 1–2 — see [`docs/plan.md`](docs/plan.md). Read the
> live MCP tool list rather than assuming.

## REST endpoints

From [`modules/core/src/main/scala/histdb/core/Api.scala`](modules/core/src/main/scala/histdb/core/Api.scala) —
the single definition the server interprets and the CLI derives its client from.

| method | path | query / body | returns |
|---|---|---|---|
| `GET` | `/api/version` · `/api/health` · `/api/config` | — | build info · db + schema version · the `ui` config block the page reads |
| `GET` | `/api/sources` | the **filter params** (below) | `SearchResult{total, hits[{source, matched[], why[]}]}` — the source is the unit |
| `GET` | `/api/facets` | `field`, `prefix` + filter params | `[{value, count}]` (distinct sources, case-folded) |
| `GET` | `/api/findings` | filter params (+ legacy `region`), `limit` | `[GeoHit]` — the map layer; antimeridian bbox split server-side |
| `GET` | `/api/aggregate` | `by=time`, `unit=sources\|findings` + filter params | `TimeHistogram{from, to, bucket, buckets[]}` |
| `GET` | `/api/sources/{citekey}` | — | `SourceWithFindings` (404 if unknown) |
| `PUT` | `/api/sources/{citekey}` | `SourcePut` body | full create/replace |
| `PATCH` | `/api/sources/{citekey}` | `SourcePatch` body (`ifUpdatedAt`) | selective merge; 404 / 412 / 400 |
| `GET` | `/api/sources/{citekey}/export` | — | the note as text (frontmatter + notes) |
| `POST` | `/api/sources/import` | `{citekey?, text, mtime?, force?}` | seed ONE note from raw text (host-side `seed`) |
| `GET` | `/api/citekeys` | — | every citekey, sorted |
| `GET` | `/api/neighbors/{citekey}` | — | `[NeighborRow]` |
| `POST` | `/api/seed` | `{dir, force}` | imports a **server-side** directory; rows newer than the file are skipped unless `force` |
| `POST` | `/api/edges/resolve` | — | unify short edge targets to full citekeys |
| `POST` | `/api/reindex` | `after`, `limit` | one resumable slice of the FTS chunk rebuild |
| `GET` | `/api/lint` | `rule`* (repeatable) | `LintReport{rules[{rule, description, count, items[{citekey, detail}]}]}`; unknown rule → 400 |
| `POST` | `/api/lint/fix` | — | the safe repairs (resolve short edge targets, reindex stale chunks), bounded per call — loop while `remaining > 0` |
| `GET` | `/api/queue/{list}` | `all` | open items of `acquire`\|`tasks`\|`log` (`all=true` adds done items and the file's prose blocks) |
| `POST` | `/api/queue/{list}` | `{text, topic?, status?, createdAt?}` | append one item |
| `PATCH` | `/api/queue/items/{id}` | `{status?, text?, topic?}` | selective edit; `status=done` stamps `done_at`; 404 if unknown |
| `DELETE` | `/api/queue/items/{id}` | — | remove one item, returning it |
| `GET` | `/api/queue/{list}/export` | — | the list rendered as its `meta/<FILE>.md` |
| `POST` | `/api/queue/{list}/import` | `{text, replace=true}` | parse a `meta/*.md` back into rows (idempotent) |
| `POST` | `/api/query` | `{sql}` | `QueryResult{columns, rows, rowCount, truncated, elapsedMs}`; a write is a 400 carrying Postgres's refusal |
| `GET` | `/api/schema/tables` | — | the live `information_schema` shape (tables, views, columns) |
| `GET` | `/source/{citekey}` | `full`, `fragment`, `q` | the article page (HTML); `q` highlights matches |
| `POST` | `/mcp` | JSON-RPC | the MCP tool surface |

**Filter params** (shared by `/api/sources`, `/api/facets`, `/api/findings`, `/api/aggregate`, and mirrored one-to-one by the CLI flags and the atlas URL hash): `q`, `tag`*, `author`*, `place`*, `topic`*, `status`*, `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`, `sort` (`relevance|cited|period|year|title`), `limit`, `offset` (`*` = repeatable).

`from`/`to` are signed years. `POST /api/seed` resolves `dir` **on the server**: under Docker the host
directory `HISTDB_CORPUS_DIR` is mounted read-only at `/corpus`, so an empty `dir` is what you want.

## Configuration

Everything runtime-tunable is in
[`modules/core/src/main/resources/reference.conf`](modules/core/src/main/resources/reference.conf).
Nothing is hardcoded anywhere else — a test greps `src/main` for literal ports and fails the build if
one reappears. Every key takes an env override.

| key | env var | default | what |
|---|---|---|---|
| `server.host` | `HISTDB_BIND` | `0.0.0.0` | bind address inside the process/container. Also read by compose as the **host interface to publish on** (`127.0.0.1` there by default). |
| `server.port` | `HISTDB_PORT` | `8000` | listen port; also the published port |
| `server.public-url` | `HISTDB_PUBLIC_URL` | `http://localhost:8000` | how clients reach the server; used for absolute URLs and to generate `.mcp.json` |
| `db.url` | `HISTDB_DB_URL` | `jdbc:postgresql://localhost:5432/histdb` | JDBC URL (compose sets `postgres:5432`) |
| `db.user` | `HISTDB_DB_USER` | `histdb` | |
| `db.password` | `HISTDB_DB_PASS` | `histdb` | |
| `converter.url` | `HISTDB_CONVERTER_URL` | `http://localhost:8100` | OCR sidecar (compose sets `converter:8100`) |
| `converter.timeout-seconds` | — | `600` | OCR is slow; this is a per-document budget |
| `converter.parallelism` | — | `1` | concurrent OCR jobs |
| `corpus.dir` | `HISTDB_CORPUS_DIR` | *(empty)* | **server-side** path `seed` reads. Docker: the `/corpus` bind mount; the env var names the **host** path compose mounts. |
| `limits.search` | — | `20` | default search page size |
| `limits.findings` | — | `4000` | max map points per request |
| `limits.snippet-chars` | — | `240` | |
| `ui.tiles` | `HISTDB_TILES` | OSM tile URL | map tiles; point at a local tile server to run offline |
| `ui.tiles-max-zoom` | — | `12` | |
| `ui.time.min` / `.max` | — | `-12000` / `2026` | timeline bounds, signed years |
| `ui.time.default-from` / `.default-to` | — | `-3500` / `-500` | initial timeline window |
| `query.timeout-ms` | `HISTDB_QUERY_TIMEOUT_MS` | `10000` | `SET LOCAL statement_timeout` for `histdb query` |
| `query.max-rows` | `HISTDB_QUERY_MAX_ROWS` | `500` | row cap; a result that hits it comes back `truncated` |
| `query.max-cell-chars` | `HISTDB_QUERY_MAX_CELL_CHARS` | `2000` | one cell is cut here — `select ocr_markdown …` is otherwise megabytes |
| `lint.max-items` | `HISTDB_LINT_MAX_ITEMS` | `50` | offending rows LISTED per rule (`count` is always the true total) |
| `lint.suggestions` | `HISTDB_LINT_SUGGESTIONS` | `3` | closest citekey matches offered per dangling edge target |
| `cli.server-url` | `HISTDB_SERVER_URL` | `http://localhost:8000` | where `histdb` looks for the server |

Compose-only knobs (`.env`): `HISTDB_DB_NAME`, `HISTDB_DB_PORT` (dev override), `HISTDB_CONVERTER_PORT`,
`HISTDB_BIN_DIR`. Copy [`.env.example`](.env.example) to `.env`.

## Backup and restore

The database is the source of truth for source content and enrichment, so it is what needs backing up.
Until `histdb backup`/`restore` land (wave 2.5), go through the compose Postgres directly.

```bash
# dump (custom format, compressed) into the backups volume
docker compose exec postgres \
  pg_dump -U "${HISTDB_DB_USER:-histdb}" -d "${HISTDB_DB_NAME:-histdb}" -Fc -f /backups/histdb-$(date +%F).dump

# copy it to the host
docker compose cp postgres:/backups/histdb-$(date +%F).dump ./histdb-$(date +%F).dump
```

Restore into a fresh volume:

```bash
docker compose down -v && docker compose up -d postgres     # empty volume
docker compose cp ./histdb-2026-08-25.dump postgres:/backups/restore.dump
docker compose exec postgres \
  pg_restore -U "${HISTDB_DB_USER:-histdb}" -d "${HISTDB_DB_NAME:-histdb}" --clean --if-exists /backups/restore.dump
docker compose up -d                                        # the server migrates on startup
```

Two more independent backup axes exist by design: the **originals in Calibre**, and the **markdown
export** of the annotations (`histdb export`, wave 1.3) kept in git/Nextcloud.

## Native-image notes

Both binaries are GraalVM `native-image` builds (~35–60 s each; the server binary is 59 MB, the image
182 MB). The lessons that keep them working — the full list is in [`AGENTS.md`](AGENTS.md):

- **The JDK is pinned to GraalVM CE 22.0.2** (`.sdkmanrc`, CI, `Dockerfile` — all three together).
  On JDK 25 the server builds, starts, logs "hist-db listening", and then dies in
  `NettySyncServer.startAndWait` with `NoClassDefFoundError: ox.DoNothingScope`, because ox 0.5.x is
  compiled against JDK 22's preview `StructuredTaskScope`. Bumping the JDK means bumping tapir/ox first.
- **Run the app once after adding a dependency.** `sbt server/run` / `sbt cli/run` fork under the
  native-image tracing agent and merge reachability metadata into
  `modules/<m>/src/main/resources/META-INF/native-image/`. Commit the changed JSON. Skip this and you
  get a binary that builds cleanly and fails at runtime. Regenerate only with the pinned JDK.
- **Config is loaded eagerly, on the main thread** (GraalVM `StackValue`), and never through
  `ConfigFactory.load()` — that folds system properties into HOCON and dotted keys like
  `io.netty.allocator.type` collide.
- **netty**: unpooled allocator (the pooled one mishandles `ByteBuf` refCnt natively) and the pure-Java
  NIO transport (kqueue/epoll JNI transports excluded in `build.sbt`).
- **Logging is scribe**; `scribe-slf4j` bridges Flyway. No reflective logging backend.
- **Flyway** reads migrations through a generated `db/migration/index.txt`, because classpath scanning
  does not work in a native image. Adding a `V###__*.sql` file is enough.
- **sbt 2** puts module output at `target/out/jvm/<scalaVersion>/<name>/`, *not* `modules/<m>/target/`.

## Troubleshooting

| symptom | cause and fix |
|---|---|
| `cannot reach hist-db at http://localhost:8000 — is the server running?` | the stack is down or on another port. `make ps`, `make logs`, `make up`. Check `HISTDB_PORT`/`HISTDB_SERVER_URL` agree. |
| `NoClassDefFoundError: ox.DoNothingScope` right after "hist-db listening" | you built with JDK 25. Pin GraalVM CE 22.0.2 (`sdk env install`) and rebuild. |
| `not a directory on the server: /some/host/path` from `seed` | `seed` resolves the path **inside the container**. Use plain `histdb seed` (the `/corpus` mount) or set `HISTDB_CORPUS_DIR` in `.env` and `make up`. |
| Docker build killed, `exit status 137` | `native-image` ran out of memory (it peaks ~5 GB). Raise Docker Desktop's memory limit, or lower `NATIVE_IMAGE_OPTIONS=-J-Xmx…` in the `Dockerfile`. |
| `failed to fetch oauth token: denied: denied` pulling any `ghcr.io/...` image (the converter's base) | a stale ghcr.io credential in Docker's credential store blocks even anonymous pulls. `docker logout ghcr.io`, then retry. |
| Native binary dies on a missing class or resource | metadata is stale. Run the app once under sbt (`sbt server/run`), commit the changed `META-INF/native-image/*.json`, rebuild. |
| `ClassNotFoundException` only inside Docker | the builder and runtime stages must share a glibc — both are `debian:bookworm-slim`. Do not swap one for alpine/musl. |
| `sbt test` "passes" without running | sbt 2 caches test results. `sbt "testOnly *"`. |
| An env var is ignored by sbt | the sbt 2 thin client shares a server and drops the environment. `sbt --server -batch <task>`. |
| Map tiles do not load | offline or firewalled. Point `HISTDB_TILES` at a local tile server. Leaflet itself is vendored, so the rest of the page works. |
| Server exits 3 at startup | a Flyway migration failed, or the database is unreachable. `make logs`; the message names the migration. |
| `git status` shows `agent-pid*/` | a forked `sbt run` was killed mid-write. Safe to delete; they are gitignored. |

## License

TBD.
