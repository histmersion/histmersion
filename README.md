# Histmersion

**A geo-temporal research database for a scholarly corpus, with two front doors: a Wikipedia-style
web UI for people and an MCP tool surface for agents.** Books and papers are OCR'd once and stored in
Postgres/PostGIS as full text plus structured enrichment — geo-located, deep-time-scoped *findings*
and typed citation edges. Humans discover and read sources on a map crossed with a timeline and hop
between them through their citations; agents answer questions, summarise and compile prose against the
same data through MCP tools and a CLI. Neither ever greps a folder of markdown files or infers a date
from prose, which is the entire point.

The domain is comparative mythology, folklore, Bronze-Age history and Indo-European
origins/migrations, but nothing in the schema is specific to it.

- **Originals** live in **Calibre** (canonical). Histmersion OCRs them and stores the text + enrichment in
  Postgres (backed up with `pg_dump`). Markdown is the *wire format* to the model and an export mirror.
- **Articles are the compiled wiki**: the concept / myth / culture / region / timeline / report pages
  an agent writes *across* sources, stored in the same database and rendered at `/article/{slug}`.
  They cite sources as `[[citekey]]` and each other as `[[slug]]`, so backlinks and a "Sources cited"
  footer fall out of the text; `histmersion export` mirrors them back to `wiki/<kind>/<slug>.md`.
- Design notes: [`docs/design.md`](docs/design.md) · Roadmap: [`docs/plan.md`](docs/plan.md) ·
  Contributing: [`AGENTS.md`](AGENTS.md) · Writing rules: [`docs/writing-guide.md`](docs/writing-guide.md)

## Architecture

```
  host                                      histmersion-server and its child processes
┌──────────────────┐                  ┌──────────────────────────────────────────────────┐
│ histmersion (CLI)│                  │  server            (GraalVM native image, 78 MB)  │
│ native binary    │───── HTTP ──────►│    /mcp     MCP tools (Chimp)      ─┐              │
│ pure HTTP client │   (127.0.0.1)    │    /api     REST (Tapir)           ─┼─► Handlers   │
└──────────────────┘                  │    /source/ + /article/ pages      ─┘     │        │
                                      │    /        web UI: map × timeline        │        │
┌──────────────────┐                  │                                           │        │
│ agent (MCP)      │───── HTTP ──────►│                            Magnum (JDBC) ─┤        │
│ Claude Code /    │                  └──────────────────┬───────────────────────┬┴────────┘
│ OpenCode         │                                     ▼                       ▼
└──────────────────┘                          ┌────────────────────┐   ┌────────────────────┐
                                              │ postgres (child)   │   │ converter (child)  │
┌──────────────────┐                          │ PostGIS · int8range│   │ docling /          │
│ browser → web UI │───── HTTP ──────────────►│ GiST · Flyway      │   │ pymupdf4llm  (OCR) │
└──────────────────┘                          └────────────────────┘   └────────────────────┘

corpus dir (HISTMERSION_CORPUS_DIR) ──read by──► server   (`seed` with no argument)
```

Four sbt modules, one rule each:

| module | what | depends on | binary |
|---|---|---|---|
| `core` | **the contract**: domain model · wire DTOs · `Filter` · Tapir endpoint definitions · Markdown rendering · config model · frontmatter parsing. No database code. | — | — |
| `db` | **the store**: Magnum repos over Postgres/PostGIS · Flyway migrations · the seed importer | `core` | — |
| `server` | MCP (Chimp) + REST (Tapir/netty-sync) + article pages + the web UI. **The only database client.** | `core`, `db` | native image |
| `cli` | **a pure HTTP client** of the server — sttp client derived from `core`'s endpoints. No pgjdbc, no DB credentials on the host. | `core` **only** | native image |

Because `core` defines each endpoint once and `server` implements it once in `Handlers`, the REST
route, the MCP tool and the CLI command are the *same* operation with the *same* Markdown output.
They agree by construction, not by discipline.

## Install

Histmersion ships as a **native bundle**: one directory holding the server and CLI (GraalVM native
images), PostgreSQL 18 + PostGIS 3.6 (conda-forge; the server rewrites its install prefix on first
start, so the directory can live anywhere) and the OCR converter (Python from
Astral's [python-build-standalone](https://github.com/astral-sh/python-build-standalone) with its
locked dependencies and docling's models). Nothing is installed system-wide, nothing runs in a VM.

```
macOS                                                 Linux
~/Applications/Histmersion/                           ~/.local/opt/histmersion/                    the bundle: bin/ · pg/ · converter/
~/Library/Application Support/Histmersion/            ~/.local/share/histmersion/                  pg/ (the database) · logs/ · backups/ · uploads/
~/Library/LaunchAgents/com.histmersion.server.plist   ~/.config/systemd/user/histmersion.service   starts it with the session, restarts it if it dies
~/bin/histmersion                                     ~/.local/bin/histmersion                     the CLI (symlink)
```

The server supervises everything itself: on first start it creates the database cluster, then runs
Postgres (loopback only, port 5433) and the converter (8100) as child processes and stops them with
it. It binds `127.0.0.1:8000` unless `HISTMERSION_BIND` says otherwise.

**First-run data.** The bundle also carries `db/`: the gazetteer ([`db/places`](db/places/README.md) —
Natural Earth, Pleiades, the traced Bronze/Iron-Age polities) and the timeline's period presets
([`db/periods`](db/periods/README.md) — a curated PeriodO subset). On a start that finds the `places`
or `periods` table empty, the server imports the bundled copy itself, as a background **`init` job**
(`histmersion jobs` lists it; the web UI is usable meanwhile, the map fills in when it is done — a
minute or two for the 16 000 Pleiades places). `histmersion init` runs the same import on demand, and
`histmersion init --fetch` adds the network sources (`init.fetch-sources`: the Cliopatria and AWMC
gazetteers, the Natural Earth coastline, PeriodO). `HISTMERSION_INIT_AUTO=false` turns the automatic
import off.

**Build and install (macOS on Apple Silicon, Linux x64/arm64):** needs [sdkman](https://sdkman.io) (the pinned GraalVM),
[uv](https://docs.astral.sh/uv/) and [pixi](https://pixi.sh).

```bash
just install                  # builds dist/histmersion (~3.3 GB; models are ~1.3 GB), installs, starts it
                              # (an unpacked release archive: scripts/bundle/install-{macos,linux}.sh <dir>)
open http://localhost:8000
```

`just uninstall` removes the app and keeps the data (`just uninstall --purge` deletes it too).
Logs: `<data>/logs/{server,postgres,converter}.log` (`just logs [server|postgres|converter]`). On Linux the
service runs while you are logged in; `loginctl enable-linger "$USER"` keeps it running after logout.

## Run

```bash
just restart        # macOS: launchctl kickstart -k gui/$(id -u)/com.histmersion.server · Linux: systemctl --user restart histmersion
just stop           # until the next login, or `just start`
histmersion health
```

**Exposing on the LAN:** add `HISTMERSION_BIND=0.0.0.0` (and `HISTMERSION_API_KEY`, which gates every write)
to the LaunchAgent's `EnvironmentVariables` (Linux: an `Environment=` line in the service), then
restart. Only on a network you trust.

### Development

```bash
sbt server/run                    # MCP + REST + web UI; HISTMERSION_RUNTIME=true HISTMERSION_HOME=~/Applications/Histmersion
                                  # makes it run the installed bundle's Postgres and converter
sbt "cli/run search collapse"
just test-it                      # every suite, DB-backed ones on a throwaway Postgres
```

See [`AGENTS.md`](AGENTS.md) and the `histmersion-dev` skill for the full workflow.

## CLI reference

<!-- BEGIN CLI REFERENCE (generated by tools/gen-cli-docs.sh — do not edit by hand) -->

| command | what it does |
|---|---|
| `histmersion version` | Print the CLI and server versions |
| `histmersion health` | Server health (db, schema version) |
| `histmersion about` | Server build (version, commit), health, job queue, last backup and store counts |
| `histmersion seed [--force] [<dir>]` | Import a wiki/ or wiki/sources directory (articles beside it come too) — read on the HOST when that path exists here, otherwise asked of the SERVER (empty = configured corpus dir) |
| `histmersion reindex` | Rebuild the full-text chunk index for every source |
| `histmersion search [--group <string>] [--depth <integer>] [--as-of <integer>] [--samples <integer>] [--unit <string>] [--brief] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | Search the corpus (websearch syntax: "phrase", -not, or): sources with the findings that matched |
| `histmersion facets [--prefix <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] <field> [<query>]` | Vocabulary of a dimension (tags|authors|places|topics|status|provenance) with distinct-source counts |
| `histmersion get [--full] [--section <string>] [--heading <string>] [--offset <integer>] [--chars <integer>] <citekey>` | Show one source as Markdown (section: summary|findings|notes|toc|body|all) |
| `histmersion geo-when [--region <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>]` | Findings (the map feed) by place, deep time, bbox — the same filters as `search` |
| `histmersion histogram [--region <string>] [--unit <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search |
| `histmersion neighbors [--kind <string>]... <citekey>` | Typed references to/from a source (references, challenges, refutes, updates, superseded_by) |
| `histmersion graph [--depth <integer>] <citekey>` | Source dependency graph: BFS traversal returning D3-ready nodes + links |
| `histmersion contradictions [--since <integer>] [<citekey>]` | Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses |
| `histmersion put <file.md>` | Send ONE note file (frontmatter + body) as a full create/replace |
| `histmersion patch [--json <string>] [--tags-add <string>] [--tags-remove <string>] [--tags-replace <string>] [--topics <string>] [--regions <string>] [--summary <string>] [--status <string>] [--title <string>] [--notes-file <string>] [--period <string>] [--body-file <string>] [--add-finding <string>]... [--remove-finding <string>]... [--if-updated-at <string>] [--add-edge <string>]... [--remove-edge <string>]... <citekey>` | Selectively enrich one source — only the parts you pass are changed |
| `histmersion rename [--json] <old> <new>` | Give a source a new citekey — findings, links in articles/notes and every table follow; the old citekey keeps resolving as an alias |
| `histmersion export [--sources-dir <string>] [--articles-dir <string>] [--meta-dir <string>] [--no-meta] <dir> [<citekey>...]` | Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout |
| `histmersion backup [--download <string>]` | Take a pg_dump backup of the server's database (optionally copy it here) |
| `histmersion backups` | List the backups on the server, newest first |
| `histmersion restore [--force] [--yes] <file>` | Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force |
| `histmersion article list [--kind <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | List/search compiled articles (the filter flags select the sources they cite) |
| `histmersion article get [--parts <string>] <slug>` | Show one article (parts: body,summary,cited,backlinks — default all) |
| `histmersion article put [--kind <string>] <file.md>` | Send ONE article note file (frontmatter + body) as a full create/replace |
| `histmersion article patch [--json <string>] [--title <string>] [--summary <string>] [--kind <string>] [--body-file <string>] [--append-file <string>] [--tags-add <string>] [--tags-remove <string>] [--tags-replace <string>] [--regions <string>] [--period <string>] [--if-updated-at <string>] <slug>` | Extend or correct one article — only the parts you pass change |
| `histmersion article delete <slug>` | Remove one article (its links go with it) |
| `histmersion article export <dir>` | Write each article to <dir>/<kind>/<slug>.md |
| `histmersion article import [--force] <dir>` | Import <dir>/<kind>/*.md (and any *.md in <dir> itself) |
| `histmersion article versions <slug>` | Show the edit history of one article |
| `histmersion article version <slug> <id>` | Show one historical version's full body |
| `histmersion places search [--as-of <integer>] [--limit <integer>] [<query>]` | Find places by name; the `id` it prints is what `--place` wants |
| `histmersion places show [--as-of <integer>] [--depth <integer>] [--no-tree] <id>` | One place: geometry, validity, parents and the subtree |
| `histmersion places children [--as-of <integer>] <id>` | What one place contains, as of a year |
| `histmersion places import [--kind <string>] [--batch <integer>] <file.json>` | Upsert a Linked Places / GeoJSON file into the gazetteer |
| `histmersion places seed [--dir <string>] [--batch <integer>] [--no-rebuild]` | Import every gazetteer file under db/places (Natural Earth, Pleiades, traced polities) |
| `histmersion places fetch [--dry-run] <source>` | Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent. |
| `histmersion places rebuild` | Re-materialise which findings fall in which places |
| `histmersion places build-hierarchy` | Compute parent-child edges from spatial containment (point inside polygon). Idempotent. |
| `histmersion periods search [--region <string>] [--from <integer>] [--to <integer>] [<query>]` | Find named periods by label, region or time span |
| `histmersion periods show <id>` | One period: its span, authority, what it is part of and its divisions |
| `histmersion periods seed [--file <string>]` | Import the period presets bundled with the server (a curated PeriodO subset); idempotent |
| `histmersion periods fetch [--collection <string>]... <source>` | Download PeriodO and import collections, each under one node; re-runnable and idempotent |
| `histmersion init [--fetch] [--no-wait]` | Import the bundled gazetteer and period presets (a server job); --fetch adds the network sources |
| `histmersion events search [--kind <string>] [--historicity <string>] [--place-id <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | Find events by label or description (the filter flags select their sources) |
| `histmersion events show <id>` | One event: datings, edges, participants |
| `histmersion attestations search [--citekey <string>] [--target-type <string>] [--target-id <string>] [--limit <integer>]` | Find attestations by source or target |
| `histmersion attestations show <id>` | One attestation by id |
| `histmersion attestations add --citekey <string> --target-type <string> --target-id <string> [--quote <string>] [--page <string>] [--chunk <integer>] [--note <string>]` | Create an attestation linking an entity to a source passage |
| `histmersion attestations rm <id>` | Delete an attestation by id |
| `histmersion goals list [--status <string>] [--limit <integer>]` | List research goals |
| `histmersion goals get <slug>` | Show one goal by slug |
| `histmersion goals update [--title <string>] [--status <string>] [--notes <string>] <slug>` | Update goal fields (patch) |
| `histmersion agent-state list` | List all agent state keys |
| `histmersion agent-state get <key>` | Get one agent state entry |
| `histmersion agent-state set <key> <value>` | Set an agent state key (value as JSON) |
| `histmersion media detail <citekey> <name>` | Show asset metadata including spatial/temporal |
| `histmersion media patch [--kind <string>] [--caption <string>] [--license <string>] [--lat <floating-point>] [--lon <floating-point>] [--period-start <integer>] [--period-end <integer>] <citekey> <name>` | Update asset metadata |
| `histmersion asset-links search [--citekey <string>] [--asset <string>] [--target-type <string>] [--target-id <string>]` | Find asset links |
| `histmersion asset-links add --citekey <string> --asset <string> --relation <string> --target-type <string> --target-id <string> [--xywh <string>] [--notes <string>]` | Create an asset link |
| `histmersion asset-links rm <id>` | Delete an asset link by id |
| `histmersion lint [--rule <string>]... [--fix] [--json]` | Health-check the corpus (23 rules: dangling-edges, unresolved-short-citekeys, no-findings, no-period, no-regions, empty-summary, unassessed-status, duplicate-doi, duplicate-sha256, orphan-sources, stale-chunks, undated-findings, unlocated-findings, legacy-figure-links, dangling-article-links, non-canonical-tag, missing-year, year-citekey-mismatch, author-pipe, article-filler, article-uncited-paragraph, article-thin-summary, superseded-without-edge) |
| `histmersion queue [--topic <string>] [--reason <string>] [--all] [--json] [<args>...]` | Bookkeeping queues: acquire / tasks / log — show, add, done, rm, export, import |
| `histmersion query [--json] <sql>` | Run ONE read-only SQL statement (a write is refused by Postgres itself) |
| `histmersion schema [--json]` | List the tables and columns `query` can read (from information_schema) |
| `histmersion discover search [--since <integer>] [--sort <string>] [--limit <integer>] [--not-in-db] [--json] <query>` | OpenAlex search for literature to ingest |
| `histmersion discover meta [--json] <doi>` | Normalised metadata for one DOI |
| `histmersion discover wikirefs [--json] <wikipedia-url>` | Harvest the DOIs a Wikipedia article cites (the trailhead) |
| `histmersion refs [--apply] [--json] <citekey|doi>` | The reference list a paper deposited, resolved against the corpus |
| `histmersion assess update-retractions [--json]` | Refresh the local Retraction Watch mirror (~60 000 rows) |
| `histmersion assess [--apply] [--force-status] [--json] <citekey|doi>` | Retraction / correction / impact verdict for a paper (retracted|corrected|current|low-impact) |
| `histmersion followups [--since <integer>] [--limit <integer>] [--apply] [--json] <citekey|doi>` | Papers citing this one, with intents and a proposed edge kind (confirms|challenges|refutes|supersedes|cites) |
| `histmersion ingest [--citekey <string>] [--arxiv <string>] [--quality] [--force] [--title <string>] [--author <string>]... [--year <integer>] [--doi <string>] [--provenance <string>] [--no-wait] [--acquire-item <integer>] [--json] <file|doi|arxiv:id>` | Bring a paper in: a local PDF/EPUB (uploaded and OCR'd, keeping its figures) or an identifier (metadata → legal OA PDF → the same OCR job → reference edges → assessment) |
| `histmersion archive search [--rows <integer>] [--json] <query>` | Search archive.org texts |
| `histmersion archive info [--json] <identifier>` | One archive.org item: files and lending restriction |
| `histmersion archive import [--citekey <string>] [--format <string>] [--json] <identifier>` | Import a PUBLIC archive.org item (lending-only is refused) |
| `histmersion calibre search [--limit <integer>] [--json] <query>` | Search the local Calibre library (read-only) |
| `histmersion calibre import [--citekey <string>] [--format <string>] [--no-convert] [--no-wait] [--json] <calibre-id>` | Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched) |
| `histmersion calibre link [--json] <citekey> <calibre-id>` | Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed) |
| `histmersion calibre push [--file <string>] [--replace] [--json] <citekey>` | Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed) |
| `histmersion calibre update [--json] <citekey>` | Rewrite a source's existing Calibre entry (title, authors, publisher, year, comments) from the corpus (Calibre GUI must be closed) |
| `histmersion calibre set --field <string> [--field <string>]... <calibre-id>` | Set fields on a Calibre entry, e.g. --field languages:eng (settable: title, authors, publisher, pubdate, languages, identifiers, tags, comments, series; Calibre GUI must be closed) |
| `histmersion calibre audit [--json]` | Compare every source's Calibre entry with the corpus record (title, authors, year) — read-only |
| `histmersion calibre sync [--apply] [--limit <integer>] [--json]` | Push every source that is not in Calibre yet (dry run unless --apply) |
| `histmersion calibre gaps [--quiet] [--limit <integer>] [--json]` | List sources missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook) |
| `histmersion reocr [--quality] [--no-wait] <citekey>` | Convert a source's stored original again (e.g. under the slow, accurate OCR path) |
| `histmersion assets <citekey>` | The figures stored with a source, and the URLs they are served at |
| `histmersion jobs [--status <string>] [--limit <integer>] [<id>]` | Recent OCR jobs, or one job by id |

Global: `--server <url>` overrides the configured server (`$HISTMERSION_SERVER_URL`).

<!-- END CLI REFERENCE -->

Regenerate that table from the CLI's own `--help` with `just cli-docs`
([`tools/gen-cli-docs.sh`](tools/gen-cli-docs.sh)); `--check` fails if it is stale.

Every command prints the **same Markdown** the matching MCP tool returns. Deep time is a **signed
integer year**: 3300 BCE → `-3300`.

```bash
histmersion search "sea peoples" --limit 30
histmersion get cline2014 --full
histmersion geo-when --region Levant --from -1250 --to -1100
histmersion geo-when --bbox 25,35,40,42 --from -1400 --to -1100
histmersion neighbors cline2014

histmersion init                                          # the bundled gazetteer + period presets (a server job)
histmersion places seed                                   # the same gazetteer, from a checkout's db/places
histmersion places search Anatolia                        # → the id `--place` wants
histmersion search --place anatolia --from -1500 --to -1200
histmersion places show continent_asia --as-of -1400      # the hierarchy as it stood then
histmersion periods search Minoan                         # PeriodO definitions → spans to pass as --from/--to
histmersion periods show p0ds9qjh4dh                      # Late Minoan: authority, parent, divisions
```

## MCP tools

**49 tools**, wired for Claude Code through `.mcp.json` (`type: http`, `<public-url>/mcp`, written
by `just mcp-json`, which `just install` runs). Every tool returns Markdown. The table below is refreshed from the server's own
`tools/list` — that endpoint, not this file, is the authority on what exists.

Deep time everywhere is a **signed integer year**: 1200 BCE → `-1200`.

### Read

| tool | when to use it |
|---|---|
| `ping` | liveness + schema version. First call when anything looks wrong. |
| `search` | **the entry point for any topic question.** Free text (websearch syntax: `"phrase"`, `-not`, `or`) plus every filter: `tags`/`authors`/`places`/`topics`/`status`, `yearFrom`/`yearTo`, a deep-time window (`from`/`to`), a bbox, `sort`. The SOURCE is the unit of each hit, which lists the findings that matched and why. `groupBy` = `time\|region\|time,region\|region,time` returns a nested tree of counts instead of a list (`depth` 1–3, `asOf` picks the year the hierarchy is read at). Counts are **non-additive** — a source lands in every bucket it overlaps. |
| `facets` | the vocabulary of one dimension (`tags`, `authors`, `places`, `topics`, `status`) **inside the current filter**, with distinct-source counts. Call it before guessing a tag: an unknown tag returns nothing. |
| `get_source` | after `search`: metadata, summary, notes and findings for one citekey. `fullText: true` pulls the whole OCR'd body — mean 450 KB, **max 5.7 MB** — so use it sparingly; a `search` snippet is usually the sentence you wanted. |
| `geo_when` | "what happened *here*, *then*" — individual findings by `region` name, bbox (antimeridian-safe) and `periodStart`/`periodEnd`. |
| `histogram` | "when is the evidence" — sources or findings per adaptive time bucket, the same aggregation the web UI timeline draws. |
| `neighbors` | **before relying on a claim.** The typed edges to and from a source, plus the articles that cite it. Stored kinds are `references`, `challenges`, `refutes`, `updates`, `superseded_by`; only the last four are disagreement. Edges join stored sources; one pointing at a work not in the corpus yet is listed as unresolved. |
| `graph` | the citation network around a source, breadth-first to `depth`: nodes (citekey, title, year, status) and typed links — what the web UI's graph window draws. |
| `contradictions` | disagreement edges and contested statuses: for one `citekey`, both directions with the other side's year and status; without one, corpus-wide. An empty answer is not evidence of agreement. |
| `periods` | **named periods** (PeriodO definitions: Bronze Age, Late Minoan IB, Old Babylonian, Yamnaya…). Without `id`: search by label, `region` or overlap with `from`..`to`; with `id`: span, region, the authority that states the range, what it is part of and how it is divided. Turns a period name into the years to pass to `search`/`geo_when` — cite the authority, since authorities date the same name differently. |
| `places` | **the gazetteer.** Without `id`: a name a human wrote ("Anatolia", "Ugarit") → a place **id**; pass that as `places` to `search` and it matches everything inside the shape *and* below it in the hierarchy, which a bare name never does. With `id`: geometry, validity, parents and subtree. `asOf` reads the hierarchy as it stood in a given year. |
| `list_articles` | the **compiled wiki** — list/search articles by `kind`, full-text `query` (the article's own title/summary/body), `title` (substring), `tag` (articles citing a source with that tag). Call it before compiling anything; the topic may already have an article to extend. |
| `get_article` | one article by slug: body, **backlinks**, and the **sources cited** with title · year · status. |
| `article_versions` / `article_version` | an article's edit history (newest first), and the full body of one past version — to see what a rewrite changed or recover text a bad patch overwrote. |
| `schema` | the tables, columns and types `query` can read, from the live `information_schema` — check names before writing SQL. |
| `query` | one **read-only** SQL statement for what the other tools cannot express. Runs in a `READ ONLY` transaction with a statement timeout and row cap; the description lists the live tables from `information_schema`. |
| `lint` | **after a batch of ingestion.** 21 corpus-integrity rules (dangling edge targets, unresolved short citekeys, sources with no findings/period/regions/summary, `unassessed` status, duplicate DOI/sha256, orphans, stale chunks, undated/unlocated findings, the wave-3 tag/year/author/article checks, plus `legacy-figure-links` — bodies whose figures still point at a `figures/…` path — and `dangling-article-links` — `[[wikilinks]]` that resolve to no source and no article). `fix: true` applies the two safe repairs. |

### Write

| tool | when to use it |
|---|---|
| `put_source` | create or **replace** a whole source. Destructive — creation and deliberate re-import only. |
| `patch_source` | **the enrichment tool.** Send only what changes: `metadata`, `summary`, `body`, `notes`, `topics`, `regions`, `period`, `tags{add,remove,replace}`, `findings{add,remove,replace}`, `edges{add,remove}`. `ifUpdatedAt` returns 412 instead of clobbering. |
| `rename_source` | give a source a **new citekey** (e.g. the year in it is not the publication year). One transaction: findings renumbered, every table and every `[[old]]` link in articles (each rewrite a recorded version) and source notes follow. The old citekey stays an **alias** — reads, `/source/<old>` and `[[old]]` keep resolving. |
| `put_article` | create or **replace** a whole article. Cite sources as `[[citekey]]`; write it by [`docs/writing-guide.md`](docs/writing-guide.md). |
| `patch_article` | **the way to extend an article**: `append` adds Markdown at the end of the body; or send `body`/`title`/`summary`/`kind`/`regions`/`period`/`tags{…}`. `ifUpdatedAt` guards. |
| `queue` | the project's bookkeeping in one tool: `action` = `list`\|`add`\|`done` over `acquire` (what the USER must obtain), `tasks` (the agent's backlog), `log` (the dated worklog). Use it instead of writing a tracker file. |

### Acquisition

| tool | when to use it |
|---|---|
| `discover` | **literature we do NOT have yet.** `query` searches OpenAlex, `doi` fetches one paper's metadata, `wikipediaUrl` harvests every DOI a Wikipedia article cites (the trailhead). Each hit carries the citekey it would get; `notInDb: true` gives the delta. |
| `ingest_doi` | bring a paper in by DOI or `arxiv:<id>`: metadata → legal OA PDF (Unpaywall → CORE → arXiv) → OCR job → reference edges → assessment. Idempotent; never wipes enrichment. |
| `ingest` | OCR a PDF/EPUB into a source **keeping its figures**. Takes a `path` the SERVER can see or a `base64` payload (≤ `limits.inline-bytes`); returns a **job id**, because a long scan takes minutes. `acquireItem` names the acquisition item the document fulfils: the job ticks it off when it succeeds. |
| `job_status` | where an OCR job got to — `queued\|running\|done\|failed`, its progress line or the reason it failed. No `id` lists recent jobs. |
| `assets` | the figures stored with a source: name, mime, pixel size, and the URL each is served at. |
| `archive` | Internet Archive for out-of-print books: search, inspect, import. **Public items only** — a lending-only scan is refused. |
| `refs` | the reference list a paper deposited with Crossref, resolved against the corpus; `apply: true` writes them as `references` edges. |
| `assess` | **before citing anything unchecked.** Retraction Watch + Crossref amendment notices + citation metrics → `retracted\|corrected\|current\|low-impact\|unassessed`; `apply: true` writes the verdict into `status`. `updateRetractions: true` refreshes the local mirror — **do that first, the mirror starts empty**. |
| `followups` | what came AFTER a paper: citing works with Semantic Scholar intents, influence flags and citation contexts, each with a proposed edge kind. A live network call — seconds per source. |

### Export and operations

| tool | when to use it |
|---|---|
| `export_source` | the source as the Markdown note it would be on disk (frontmatter + notes body) — the git-friendly mirror. |
| `export_article` | the article as the Markdown note it would be at `wiki/<kind>/<slug>.md`. |
| `backup` | a server-side `pg_dump`, returning the file name, size and SHA-256. |
| `backups` | the archives on the server, newest first — check one exists before a bulk operation. Restoring is `histmersion restore` (a human's call). |

### Events, evidence, assets and research memory

| tool | when to use it |
|---|---|
| `events` | the events database: CIDOC-CRM-style events with four-point time-spans, a part-of hierarchy, temporal/causal edges and participant roles. Lists (by `query` over label/description, `title` over the label, `kind`, `historicity`, `placeId`, `from`/`to` overlapping the event's own span), or shows one by `id`. |
| `attestations` / `put_attestation` / `delete_attestation` | granular evidence: link a finding, event, dating, place or edge to the exact passage of a source (W3C Web Annotation selectors: quote, position, chunk, page). |
| `patch_asset` | a stored figure's metadata: kind (figure/photo/map/plan/scan), caption, licence, location and period. |
| `asset_links` / `delete_asset_link` | typed links between figures and events, findings, places, sources or articles. |
| `goals` / `put_goal` / `patch_goal` | research programmes: slug, title, status (`planned`→`seeding`→`compiled`→`reviewed`→`done`), progress notes, scope. |
| `agent_state` / `delete_agent_state` | a small key-value store an agent keeps across sessions (set, get, list, delete). |

## Skills

Nineteen skills ship with the repo (`skills/<name>/SKILL.md`) and install into a workspace through
the plugin — see [`plugin/README.md`](plugin/README.md). One canonical copy, symlinked into
`plugin/skills/` and `.opencode/skill/`; edit the originals, never the symlinks. Five are the
reference skills for the tool surface:

| skill | use it when |
|---|---|
| `histmersion-query` | answering a research question against the corpus: `facets` → `search` → `get_source` → `neighbors` → `followups`. Carries the MCP-vs-CLI parameter table and three worked recipes (topic + disagreements, place + period, "what has been challenged since YEAR"). |
| `histmersion-ingest` | getting sources in (`ingest_doi`, `ingest`, `seed`, Calibre, archive.org) and enriching them with `patch_source` — findings, topics, regions, period, status, edges. |
| `histmersion-compile` | writing the answer up and filing it as an article with `put_article`/`patch_article`. The rules themselves live in [`docs/writing-guide.md`](docs/writing-guide.md). |
| `histmersion-currency` | keeping statuses honest: refresh the retraction mirror, `assess`, `followups --since`, move sources to `contested`/`superseded`/`debunked`, queue what is missing. |
| `histmersion-dev` | changing Histmersion itself: sbt 2 gotchas, GraalVM native image and its tracing-agent metadata, Flyway migrations, the native bundle. |

The other fourteen are research workflows built on them:

| skill | use it when |
|---|---|
| `trailhead` | starting a topic from a Wikipedia article or a query: harvest the primary literature into an ingest queue |
| `ingest-paper` | one paper by DOI/arXiv/title: fetch, OCR, reference edges, then enrich |
| `import-file` · `import-archive` · `import-calibre` | a file the user downloaded · an archive.org public text · a book already in Calibre |
| `assess-paper` · `find-followups` | vetting a paper (retractions, corrections, impact) · walking the citation graph forward for critiques |
| `update-topic` | refreshing a topic with what was published since its last expansion |
| `report` | answering a research question and filing the answer as a `report` article |
| `review-article` | the three-reviewer panel plus fixer that gates every compiled article |
| `lint-wiki` | corpus health checks and the judgement checks behind them |
| `sync-calibre` · `enrich-calibre` | getting every fetched original into Calibre · auditing and fixing Calibre metadata |
| `bookkeeping` | where bookkeeping goes: the acquire / tasks / log queues |

`./tools/check_skills.sh` verifies that every `histmersion …` command and every MCP tool name the skills
mention actually exists (CLI `--help` prints a Usage block; the name is in the live `tools/list`).
`./tools/gen-cli-docs.sh --check` does the same for the CLI reference table above.

## REST endpoints

From [`modules/core/src/main/scala/com/histmersion/core/Api.scala`](modules/core/src/main/scala/com/histmersion/core/Api.scala) —
the single definition the server interprets and the CLI derives its client from.

| method | path | query / body | returns |
|---|---|---|---|
| `GET` | `/api/version` · `/api/health` · `/api/config` | — | build info · db + schema version · the `ui` config block the page reads, plus `writeKeyRequired` (whether writes need the API key — never the key) |
| `GET` | `/api/about` | — | `About{name, build{version, gitCommit, commitDate, dirty}, health, jobs{queued, running, done, failed}, counts{sources, findings, articles, events, places, periods}, lastBackup, lastBackupAgeSeconds, problems[]}` — the web UI's About panel and `histmersion about`; a part that cannot be read is a `problems` line, never an error |
| `GET` | `/api/sources` | the **filter params** (below) | `SearchResult{total, hits[{source, matched[], why[]}]}` — the source is the unit |
| `GET` | `/api/facets` | `field`, `prefix` + filter params | `[{value, count}]` (distinct sources, case-folded) |
| `GET` | `/api/findings` | filter params (+ legacy `region`), `limit` | `[GeoHit]` — the map layer; antimeridian bbox split server-side |
| `GET` | `/api/aggregate` | `by=time\|region\|time,region\|region,time`, `unit=sources\|findings\|starts`, `depth`, `asOf`, `samples` + filter params | `TimeHistogram{from, to, bucket, buckets[], by, unit, total, depth, asOf, tree[]}` — `buckets` is the flat strip (`by=time`), `tree` the §1.6 nested `Bucket{label, key, kind, start, end, placeId, count, overlap, contested, children[], samples[]}` forest |
| `GET` | `/api/sources/{citekey}` | `body` (default `true`; `false` omits `ocrMarkdown`) | `SourceWithFindings` (404 if unknown) |
| `GET` | `/api/sources/{citekey}/articles` | — | `[Backlink{kind, id, title}]` — the compiled articles that CITE this source (404 if the citekey is unknown) |
| `PUT` | `/api/sources/{citekey}` | `SourcePut` body | full create/replace |
| `PATCH` | `/api/sources/{citekey}` | `SourcePatch` body (`ifUpdatedAt`) | selective merge; 404 / 412 / 400 |
| `GET` | `/api/sources/{citekey}/export` | — | the note as text (frontmatter + notes) |
| `POST` | `/api/sources/{citekey}/rename` | `{"newCitekey": …}` | `SourceRenamed{oldCitekey, newCitekey, source, counts{findings, articles[], sources[], links, columns[], edgesPromoted}, calibreId}` — one transaction; the old citekey becomes an alias; 400 when the new key is taken or not link-shaped, 404 when the old one is unknown |
| `GET` | `/api/source-aliases` | — | `[{alias, citekey}]` — every old citekey and the source it names now |
| `POST` | `/api/sources/import` | `{citekey?, text, mtime?, force?}` | seed ONE note from raw text (host-side `seed`) |
| `GET` | `/api/citekeys` | — | every citekey, sorted |
| `GET` | `/api/neighbors/{citekey}` | — | `[NeighborRow]` |
| `POST` | `/api/seed` | `{dir, force}` | imports a **server-side** directory; rows newer than the file are skipped unless `force` |
| `POST` | `/api/edges/resolve` | — | promote unresolved edges whose target now resolves (exactly or by unique short form) to a stored source |
| `POST` | `/api/reindex` | `after`, `limit` | one resumable slice of the FTS chunk rebuild |
| `POST` | `/api/ingest` | `citekey`, `filename?`, `quality?`, `force?`, `title?`, `author*`, `year?`, `doi?`, `acquireItem?` + the document as the **raw body** | **202** `IngestAccepted{jobId, citekey, status, sha256}`; **409** when the same bytes are already stored under another citekey (unless `force`); **400** when `acquireItem` is not an open acquisition item. With `acquireItem`, the OCR job ticks that item off (`ingested as [[citekey]]`) when it succeeds and leaves it open when it fails |
| `POST` | `/api/sources/{citekey}/reocr` | `quality` | 202 + a job; converts the spooled original again |
| `GET` | `/api/jobs/{id}` · `/api/jobs` | `status`, `limit` | `JobInfo` / `[JobInfo]` — `queued\|running\|done\|failed` |
| `GET` | `/api/sources/{citekey}/assets` | — | `[AssetInfo{name, mime, bytes, sha256, width, height}]` |
| `POST` | `/api/sources/{citekey}/assets` | `[AssetInput{name, mime, base64, …}]` | add figures to an existing source (host-side `seed`) |
| `GET` | `/source/{citekey}/assets/{name}` | — | the figure's bytes, with its `Content-Type` and `Cache-Control: public, max-age=31536000, immutable` |
| `GET` | `/api/articles` | filter params + `kind`, `text` | `ArticleList{total, articles[]}` — the compiled wiki: the articles CITING at least one source the filter selects (every article when it selects nothing); `title` is the article's own title, `text` FTS over the article's title/summary/body; `limit`/`offset` page the articles |
| `GET` | `/api/events` | filter params + `text`, `kind`, `historicity`, `placeId` | `EventList{total, events[]}` — the events whose source the filter selects (its period aside), whose OWN span overlaps `from`/`to`; `title` is the label, `text` a label/description substring, `placeId` the event's own place |
| `GET` | `/api/articles/{slug}` | — | `ArticleView{article, backlinks[], sourcesCited[]}` (404 if unknown) |
| `PUT` | `/api/articles/{slug}` | `ArticlePut` body | full create/replace |
| `PATCH` | `/api/articles/{slug}` | `ArticlePatch` body (`append`, `ifUpdatedAt`) | selective merge; 404 / 412 / 400 |
| `DELETE` | `/api/articles/{slug}` | — | drops the article and its links |
| `GET` | `/api/articles/{slug}/export` | — | the note as text (`wiki/<kind>/<slug>.md`) |
| `POST` | `/api/articles/import` | `{text, slug?, kind?, mtime?, force?}` | import ONE article note from raw text (host-side `seed`) |
| `GET` | `/api/article-slugs` | — | every slug, sorted |
| `GET` | `/api/lint` | `rule`* (repeatable) | `LintReport{rules[{rule, description, count, items[{citekey, detail}]}]}`; unknown rule → 400 |
| `POST` | `/api/lint/fix` | — | the safe repairs (resolve short edge targets, reindex stale chunks), bounded per call — loop while `remaining > 0` |
| `GET` | `/api/queue/{list}` | `all` | open items of `acquire`\|`tasks`\|`log` (`all=true` adds done items and the file's prose blocks) |
| `POST` | `/api/queue/{list}` | `{text, topic?, status?, createdAt?}` | append one item |
| `PATCH` | `/api/queue/items/{id}` | `{status?, text?, topic?, reason?}` | selective edit; `status=done` stamps `done_at`; a blank `topic`/`reason` clears it, a blank `text` is a 400; 404 if unknown |
| `DELETE` | `/api/queue/items/{id}` | — | remove one item, returning it |
| `GET` | `/api/queue/{list}/export` | — | the list rendered as its `meta/<FILE>.md` |
| `POST` | `/api/queue/{list}/import` | `{text, replace=true}` | parse a `meta/*.md` back into rows (idempotent) |
| `GET` | `/acquire` · `/tasks` · `/log` | — | the web UI with that list's window open (the toolbar's "to acquire", "backlog" and "worklog"). Acquire and tasks items can be added, edited, deleted and ticked off there; an acquisition item can be fulfilled by **Upload…** (the upload form, filled in from the item; the upload form can also pick an item itself) or **Link existing source…**. Either ticks it off as `ingested as [[citekey]]`: a link at once, an upload when its OCR job succeeds. The worklog, newest date first, is added to only. Every change needs the API key when one is set |
| `POST` | `/api/query` | `{sql}` | `QueryResult{columns, rows, rowCount, truncated, elapsedMs}`; a write is a 400 carrying Postgres's refusal |
| `GET` | `/api/schema/tables` | — | the live `information_schema` shape (tables, views, columns) |
| `GET` | `/api/places` | `q`, `asOf`, `limit` | `[PlaceHit]` — gazetteer search by name/label, valid at `asOf` |
| `GET` | `/api/places/{id}` | `asOf`, `counts` | `PlaceDetail` — geometry as **GeoJSON text**, parents/children as of `asOf` (404 if unknown); `counts=true` adds `counts{sources, findings, articles, events}` — what `place=<id>` selects (the map's hover card) |
| `GET` | `/api/places/{id}/children` | `asOf` | `[PlaceSummary]` |
| `GET` | `/api/places/{id}/tree` | `asOf`, `depth` | `[PlaceTreeRow]` — the subtree, depth-tagged (the indented list MCP prints) |
| `POST` | `/api/places/import` | `kind` + an **LPF or GeoJSON FeatureCollection** body | upsert; `{places, names, relations, failed}` |
| `POST` | `/api/places/rebuild` | — | re-materialise `finding_places`; `{links, byGeometry, byName, located, unlocated, places}` |
| `GET` | `/api/acquire/discover` | `q`, `since`, `sort`, `limit`, `notInDb` | `DiscoverResult` — OpenAlex search, hits marked `inDb` |
| `GET` | `/api/acquire/meta` | `id` (DOI) | `WorkRef` — normalised metadata (OpenAlex, Crossref fallback) |
| `GET` | `/api/acquire/wikirefs` | `url` | `WikirefsResult` — the DOIs a Wikipedia article cites |
| `POST` | `/api/acquire/refs` | `{id, apply}` | `RefsResult`; `apply` writes `references` edges |
| `POST` | `/api/acquire/assess` | `{id, apply}` | `Assessment`; `apply` patches `status` |
| `POST` | `/api/acquire/retractions` | — | `RetractionsUpdate` — refresh the Retraction Watch mirror |
| `POST` | `/api/acquire/followups` | `{id, since, limit, apply}` | `FollowupsResult` with a proposed edge kind per citing paper |
| `POST` | `/api/acquire/ingest` | `{id, citekey?, arxiv?}` | `IngestReport` — metadata → OA PDF → job → edges → assessment |
| `GET` | `/api/acquire/archive/search` · `/info` | `q`/`id` | archive.org items and their downloadable files |
| `POST` | `/api/acquire/archive/import` | `{id, citekey?, format?}` | `IngestReport`; lending-only items are refused |
| `GET` | `/api/acquire/calibre/gaps` | `limit` | `CalibreGaps` — stored sources not in Calibre yet |
| `POST` | `/api/acquire/calibre/pushed` | `{citekey, calibreId}` | record a host-side Calibre push |
| `GET` | `/source/{citekey}` | `full`, `fragment`, `q` | the article page (HTML); `q` highlights matches, figures render as lazy `<img>` with a click-to-zoom lightbox. An old (renamed) citekey answers **301** to the current one |
| `GET` | `/article/{slug}` | `fragment`, `q` | the compiled article page (HTML): `[[citekey]]` → `/source/…`, `[[slug]]` → `/article/…`, plus backlinks and a "Sources cited" footer |
| `POST` | `/mcp` | JSON-RPC | the MCP tool surface |

**Filter params** (shared by `/api/sources`, `/api/facets`, `/api/findings`, `/api/aggregate`, `/api/articles`, `/api/events`, and mirrored one-to-one by the CLI flags and the web UI URL hash): `q`, `tag`*, `author`*, `place`*, `topic`*, `status`*, `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`, `sort` (`relevance|cited|period|year|title`), `limit`, `offset`, `title` (`*` = repeatable). `title` is a case-insensitive substring of the LISTED item's own title — a source's, an article's, an event's label. On `/api/articles` and `/api/events` the other params select SOURCES, and the list is what relates to them (one rule, the `/api/sources` predicate as a sub-select), so the web UI's Articles and Events tabs follow the same filter as its Sources tab.

**Grouping** (plan §1.6, `/api/aggregate` only, on top of the filter params): `by` (`time` — the default — or `region` / `time,region` / `region,time`), `unit` (`sources` — the default — `findings`, or `starts`: sources counted ONCE, in the bucket their period *begins* in, which is what the web UI's timeline strip draws — counting every overlapped bucket makes a corpus of long-range syntheses look flat), `depth` (1–3; `0` = `ui.tree-depth`), `asOf` (signed year the gazetteer hierarchy is read at; `time,region` ignores it and reads each time bucket at its own midpoint), `samples` (0–5 sample sources per leaf, default `ui.tree-samples`).

`by=time` **with no `depth`/`samples` at all** is the flat §1.7 strip and nothing else — that is what the timeline asks for on every span change, and it must not pay for a tree it does not draw. Any other combination fills `tree`. Deeper levels are fetched lazily by re-asking the same query with `from`/`to` narrowed to a time bucket, or `place=<gazetteer id>` set to a region node — a single resolved `place` **roots the region tree at that node**, so the answer is its subtree rather than the whole forest.

A `place=` value that is a **gazetteer id** (`GET /api/places?q=…`) matches through the materialised
`finding_places` *and the hierarchy as of the window* — so `place=continent_asia&from=-1500&to=-1200`
finds a Hattusa dig through Turkey. Anything else is matched as a name against
`sources.regions ∪ findings.region_name`, exactly as before the gazetteer existed, and the two are
OR'ed so seeding places can only ever add hits. `/api/facets?field=places` reports the `id` behind a
label so a chip can filter by it, and `contested: true` for a hand-traced shape whose extent is
disputed. See [`db/places/README.md`](db/places/README.md) for the seed data and its licences.

`POST /api/ingest` takes the document as the **raw request body** rather than a multipart part:
tapir's netty backend does not implement multipart request bodies (`RawBodyType.MultipartBody` throws),
so the file is the body and everything else is a query parameter. The CLI derives its uploader from
that same endpoint definition.

`from`/`to` are signed years. `POST /api/seed` resolves `dir` **on the server**; an empty `dir` means
`corpus.dir`, which `just install` sets from `HISTMERSION_CORPUS_DIR`.

## Configuration

Everything runtime-tunable is in
[`modules/core/src/main/resources/reference.conf`](modules/core/src/main/resources/reference.conf).
Nothing is hardcoded anywhere else — a test greps `src/main` for literal ports and fails the build if
one reappears. Every key takes an env override.

| key | env var | default | what |
|---|---|---|---|
| `server.host` | `HISTMERSION_BIND` | `0.0.0.0` | bind address. The bundle binds `127.0.0.1` unless `HISTMERSION_BIND` is set. |
| `server.port` | `HISTMERSION_PORT` | `8000` | listen port |
| `server.public-url` | `HISTMERSION_PUBLIC_URL` | `http://localhost:8000` | how clients reach the server; used for absolute URLs and to generate `.mcp.json` |
| `server.request-timeout-seconds` | `HISTMERSION_REQUEST_TIMEOUT` | `1800` | one HTTP request end to end; must cover a `pg_dump`/`pg_restore` (netty's own default, 20 s, is not enough for a backup of the live corpus) |
| `db.url` | `HISTMERSION_DB_URL` | `jdbc:postgresql://localhost:5432/histmersion` | JDBC URL. The bundle replaces host and port with its own Postgres (`127.0.0.1:<runtime.pg-port>`) and keeps the database name |
| `db.user` | `HISTMERSION_DB_USER` | `histmersion` | |
| `db.password` | `HISTMERSION_DB_PASS` | `histmersion` | |
| `converter.url` | `HISTMERSION_CONVERTER_URL` | `http://localhost:8100` | OCR sidecar. The bundle points it at its own converter (`127.0.0.1:<runtime.converter-port>`) |
| `converter.timeout-seconds` | `HISTMERSION_CONVERTER_TIMEOUT` | `600` | OCR is slow; this is a per-document budget |
| `converter.parallelism` | `HISTMERSION_CONVERTER_PARALLELISM` | `1` | size of the bounded OCR worker pool |
| `converter.dpi` | `HISTMERSION_CONVERTER_DPI` | `150` | rasterisation dpi for extracted figures |
| `converter.max-image-px` | `HISTMERSION_CONVERTER_MAX_IMAGE_PX` | `2000` | longest side of a stored figure; larger ones are downscaled |
| `converter.spool-dir` | `HISTMERSION_SPOOL_DIR` | `/var/tmp/histmersion-uploads` | server-side copy of each upload, keyed by sha256 — what makes `reocr` work without a re-upload (the bundle: `<data>/uploads`) |
| `corpus.dir` | `HISTMERSION_CORPUS_DIR` | *(empty)* | **server-side** path `seed` reads when given no directory. `just install` passes it to the service (default: `./corpus` in the repo). |
| `limits.search` | — | `20` | default search page size |
| `limits.findings` | — | `4000` | max map points per request |
| `limits.snippet-chars` | — | `240` | |
| `limits.upload-bytes` | `HISTMERSION_UPLOAD_BYTES` | `268435456` | largest accepted `POST /api/ingest` upload (256 MB) |
| `limits.inline-bytes` | `HISTMERSION_INLINE_BYTES` | `33554432` | largest inline (base64) payload the MCP `ingest` tool accepts (32 MB) |
| `limits.asset-bytes` | `HISTMERSION_ASSET_BYTES` | `16777216` | largest single stored figure (16 MB) |
| `ui.tiles` | `HISTMERSION_TILES` | OSM tile URL | map tiles; point at a local tile server to run offline |
| `ui.tiles-max-zoom` | — | `12` | |
| `ui.tree-depth` | `HISTMERSION_TREE_DEPTH` | `2` | default nesting of `/api/aggregate?by=…` and the web UI's "by time" / "by region" tabs (max 3) |
| `ui.tree-samples` | `HISTMERSION_TREE_SAMPLES` | `3` | sample sources listed under a leaf of that tree (0–5) |
| `ui.time.min` / `.max` | — | `-12000` / `2026` | timeline bounds, signed years |
| `ui.time.default-from` / `.default-to` | — | `-3500` / `-500` | initial timeline window |
| `ui.narrow-max-width-px` | `HISTMERSION_UI_NARROW_MAX_WIDTH_PX` | `760` | at or below this viewport width the web UI uses its phone layout: the map first; filters, timeline, layers and tools on demand (buttons, or a swipe in from the edge); the list as a bottom sheet; windows full-screen |
| `ui.narrow-max-height-px` | `HISTMERSION_UI_NARROW_MAX_HEIGHT_PX` | `500` | …and so it does on a touch screen at or below this height (a phone held sideways) |
| `query.timeout-ms` | `HISTMERSION_QUERY_TIMEOUT_MS` | `10000` | `SET LOCAL statement_timeout` for `histmersion query` |
| `query.max-rows` | `HISTMERSION_QUERY_MAX_ROWS` | `500` | row cap; a result that hits it comes back `truncated` |
| `query.max-cell-chars` | `HISTMERSION_QUERY_MAX_CELL_CHARS` | `2000` | one cell is cut here — `select ocr_markdown …` is otherwise megabytes |
| `query.user` | `HISTMERSION_QUERY_USER` | `histmersion_query` | the read-only LOGIN role `histmersion query` runs as (created by migration V011) |
| `query.password` | `HISTMERSION_QUERY_PASS` | (set one) | password the server sets on that role at startup; rotate by restarting |
| `db.pool.max` | `HISTMERSION_DB_POOL_MAX` | `8` | HikariCP pool size (the server is the only DB client) |
| `ui.max-buckets` | `HISTMERSION_MAX_BUCKETS` | `2000` | cap on histogram/tree buckets per request (400 beyond it) |
| `limits.max-page` | `HISTMERSION_MAX_PAGE` | `500` | hard cap on `limit` for every listing endpoint |
| `converter.health-timeout-ms` | — | `2000` | how long `/api/health` waits for the converter probe |
| `converter.health-cache-ms` | — | `30000` | how long a converter probe result is reused |
| `lint.max-items` | `HISTMERSION_LINT_MAX_ITEMS` | `50` | offending rows LISTED per rule (`count` is always the true total) |
| `lint.suggestions` | `HISTMERSION_LINT_SUGGESTIONS` | `3` | closest citekey matches offered per dangling edge target |
| `places.point-tolerance-km` | `HISTMERSION_PLACE_TOLERANCE_KM` | `10` | a POINT place (a Pleiades site has no extent) contains findings within this radius; polygons use exact coverage |
| `places.search-limit` | — | `20` | default rows from `/api/places?q=` |
| `places.children-limit` | — | `200` | max children listed for one place |
| `places.tree-limit` / `.tree-depth` | — | `500` / `6` | bounds on one hierarchy walk |
| `places.seed-dir` | `HISTMERSION_PLACES_DIR` | `db/places` | **host** directory `histmersion places seed` imports from |
| `places.import-batch` | — | `400` | features per `POST /api/places/import` request (keeps bodies small); also the first-run import's batch |
| `periods.periodo-url` | `HISTMERSION_PERIODO_URL` | `https://n2t.net/ark:/99152/p0d.json` | the PeriodO dump (CC0) `histmersion periods fetch periodo` downloads |
| `periods.periodo-collections` | `HISTMERSION_PERIODO_COLLECTIONS` | `p0ds9qj,p0m64td,p0qk76z` | PeriodO authorities `periods fetch periodo` imports when none is named (BSA Greece, EAMENA, UCLA Egyptology) |
| `periods.seed-file` | — | `periods/periodo-seed.json` | the bundled presets, relative to `init.data-dir` |
| `init.auto` | `HISTMERSION_INIT_AUTO` | `true` | on start, import the bundled gazetteer / period presets into whichever table is EMPTY, as a background job |
| `init.data-dir` | `HISTMERSION_INIT_DATA_DIR` | `db` | **server-side** directory holding `places/` and `periods/`; a bundled server uses the bundle's own `db/` |
| `init.fetch-sources` | `HISTMERSION_INIT_FETCH_SOURCES` | `cliopatria,awmc,land,periodo` | what `histmersion init --fetch` downloads, in order |
| `auth.api-key` | `HISTMERSION_API_KEY` | *(empty)* | when set, every POST/PUT/PATCH/DELETE needs `Authorization: Bearer <key>` (401 otherwise); reads stay open. The web UI asks for the key before offering a write and keeps it in the browser's storage |
| `cli.server-url` | `HISTMERSION_SERVER_URL` | `http://localhost:8000` | where `histmersion` looks for the server |
| `acquire.contact-email` | `HISTMERSION_ACQUIRE_CONTACT_EMAIL` | the maintainer's address | the **polite-pool** identity Crossref/OpenAlex/Unpaywall ask for (`User-Agent` + `mailto`/`email`). Sent nowhere else. |
| `acquire.openalex.url` · `crossref.url` · `unpaywall.url` · `arxiv.url` · `archive-org.url` · `wikipedia.url` | `HISTMERSION_ACQUIRE_*_URL` | the public APIs | upstream endpoints; point them at a mirror or a stub to work offline |
| `acquire.core.api-key` · `acquire.semantic-scholar.api-key` | `HISTMERSION_ACQUIRE_CORE_API_KEY` · `..._SEMANTIC_SCHOLAR_API_KEY` | *(empty)* | optional; both APIs are keyless today, a key only raises the rate limit |
| `acquire.retraction-watch.csv-url` | `HISTMERSION_ACQUIRE_RETRACTION_WATCH_CSV_URL` | Crossref's RW export | mirrored into the `retraction_watch` table by `histmersion assess update-retractions` |
| `acquire.unlicensed.enabled` / `.mirror` | `HISTMERSION_ACQUIRE_UNLICENSED_ENABLED` / `_MIRROR` | `false` / `example.invalid` | **off by default.** The unauthorised mirror distributes copyrighted papers without publisher authorisation and its legality varies by jurisdiction; when enabled it is tried only after every legal OA source has failed, and each use is logged as a warning |
| `acquire.user-agent` · `.timeout-seconds` · `.spool-dir` | `HISTMERSION_ACQUIRE_*` | `histmersion/0.1` · `60` · *(tmp)* | HTTP identity/budget, and where a fetched PDF waits for the converter |
| `acquire.defaults.*` | — | `25`, `1900`, `100`, `8`, `50`, `3`, `2018` | discover page size / oldest year, followups cap, archive rows, and the assessment thresholds (well-cited at, low-cited at, stale before) |
| `cli.calibre.library-dir` | `HISTMERSION_CALIBRE_LIBRARY_DIR` | `~/Calibre Library` | the host Calibre library `histmersion calibre` reads (read-only) |
| `cli.calibre.calibredb` · `.sqlite` | `HISTMERSION_CALIBRE_CALIBREDB` · `_SQLITE3` | `calibredb` · `sqlite3` | the two readers: `calibredb` first, `sqlite3 -readonly` while the Calibre GUI holds the library |
| `cli.calibre.originals-dir` | `HISTMERSION_CALIBRE_ORIGINALS_DIR` | *(empty)* | host directory of fetched originals as `<citekey>/paper.*`, used by `calibre push`/`sync` |
| `runtime.enabled` | `HISTMERSION_RUNTIME` | `false` | run the bundled Postgres and converter as child processes. Automatic when the executable sits in a bundle (`<home>/bin/` beside `<home>/pg/bin/pg_ctl`) |
| `runtime.home` | `HISTMERSION_HOME` | *(empty)* | bundle root holding `pg/` and `converter/`; empty = the directory above the executable's `bin/` |
| `runtime.data-dir` | `HISTMERSION_DATA_DIR` | *(empty)* | the cluster, logs, backups and uploads; empty = `~/Library/Application Support/Histmersion` (macOS) or `$XDG_DATA_HOME/histmersion` (Linux) |
| `runtime.pg-port` · `.converter-port` | `HISTMERSION_PG_PORT` · `HISTMERSION_CONVERTER_PORT` | `5433` · `8100` | loopback ports of the two child processes (5433, so a system Postgres on 5432 can coexist) |

The installed service reads only its own environment: the LaunchAgent's `EnvironmentVariables`
(macOS) or the unit's `Environment=` lines (Linux). `just install` fills in `HISTMERSION_CORPUS_DIR`
and `HISTMERSION_ACQUIRE_CONTACT_EMAIL`; add any other key there and `just restart`. A `.env` in the
repo (copy [`.env.example`](.env.example)) is read by `just` only — the recipes and the install
scripts they run.

## Backup and restore

Two different backups, both worth having:

| what | command | contains | good for |
|---|---|---|---|
| **full database dump** | `histmersion backup` | everything — sources, OCR bodies, findings, edges, notes, the FTS index, the Flyway history | disaster recovery: restores the database byte-for-byte |
| **markdown export** | `histmersion export <dir>` | frontmatter + the notes body per source, one `.md` file each | git/Nextcloud-friendly mirror of the *annotations*; diffable, re-importable with `histmersion seed` |

The dump is the backup; the export is the archive you can read. Keep both — plus the originals in
Calibre, which Histmersion never owns.

### Taking a backup

`pg_dump` runs **on the server**, not in the CLI: the CLI is a pure HTTP client and holds no
database credentials (the bundled Postgres listens on loopback only). The archive is `pg_dump -Fc`
(custom format, compressed) and lands in the server's `backups.dir` — `<data>/backups` in the
bundle (`~/Library/Application Support/Histmersion/backups` on macOS,
`~/.local/share/histmersion/backups` on Linux). After each dump only the newest `backups.keep`
archives stay.

```bash
histmersion backup                      # dump on the server; prints file, size, sha256, duration
histmersion backup --download .         # …and stream a copy into the current directory
histmersion backups                     # what is on the server, newest first
```

Names are `histmersion-<UTC timestamp>.dump` (e.g. `histmersion-20260825T145112Z.dump`), so they sort
chronologically. The dump is written to a `.part` file and moved into place only on success, so a
listing never shows a half-written archive. `backup` is also an **MCP tool** — an agent can save the
state before a bulk reseed. There is deliberately no `restore` MCP tool: it destroys data.

### Restoring

```bash
histmersion restore histmersion-20260825T145112Z.dump --force     # asks for confirmation unless --yes
```

- The file must be one the server lists — no paths, no `..`; the name is validated against
  `GET /api/backups` before it is opened.
- Restoring **drops what is there** (`pg_restore --clean --if-exists`), so it refuses to run while the
  database still holds sources unless you pass `--force`. An empty database needs no flag.
- While it runs, the server holds a **maintenance lock**: every write endpoint (`seed`, `put`,
  `patch`, `import`, `reindex`, `edges/resolve`, `backup`) answers **503** until it finishes. Reads
  keep working.
- Afterwards the server re-runs **Flyway validate** and reports the schema version, so a dump from an
  older schema cannot pass silently.
- `pg_restore` exits non-zero when it *skipped* a statement but otherwise completed (see the version
  note below); those lines come back as `warnings` rather than failing the restore.

To restore into a completely fresh database, set the old data directory aside and let the server
create a new one. On macOS (`<data>` = `~/Library/Application Support/Histmersion`; Linux:
`~/.local/share/histmersion`):

```bash
just stop                                             # the server stops Postgres and the converter with it
mv "$HOME/Library/Application Support/Histmersion" "$HOME/Library/Application Support/Histmersion.old"
mkdir -p "$HOME/Library/Application Support/Histmersion/logs"    # the service writes its log here
just start                                            # first start: initdb, then Flyway builds an empty schema
cp "$HOME/Library/Application Support/Histmersion.old/backups/histmersion-20260825T145112Z.dump" \
   "$HOME/Library/Application Support/Histmersion/backups/"
histmersion restore histmersion-20260825T145112Z.dump  # an empty database needs no --force
```

Delete `Histmersion.old` once the restored corpus looks right.

### The endpoints and the config

| method | path | body | returns |
|---|---|---|---|
| `POST` | `/api/backup` | — | `BackupResult{file, bytes, sha256, durationMs}` |
| `GET` | `/api/backups` | — | `[BackupInfo{file, bytes, createdAt}]`, newest first |
| `GET` | `/api/backups/{file}` | — | the archive itself, streamed |
| `POST` | `/api/restore` | `RestoreRequest{file, force}` | `RestoreResult{file, schemaVersion, sourcesBefore, sourcesAfter, durationMs, warnings[]}` |

| key | env | default | meaning |
|---|---|---|---|
| `backups.dir` | `HISTMERSION_BACKUPS_DIR` | `backups` (bundle: `<data>/backups`) | server-side directory for the archives |
| `backups.pg-bin-dir` | `HISTMERSION_PG_BIN_DIR` | `` (use `PATH`; bundle: `<home>/pg/bin`) | directory holding `pg_dump`/`pg_restore` when they are off `PATH` |
| `backups.keep` | `HISTMERSION_BACKUPS_KEEP` | `5` | archives kept after a dump, newest first; `0` keeps every one |
| `backups.timeout-seconds` | — | `1800` | a dump/restore that outlives this is killed rather than hanging the request |

### `pg_dump` has to be there, and its version matters

The bundle carries **`pg_dump`/`pg_restore` 18** in `<home>/pg/bin`, beside the Postgres 18 they
dump, and the server uses them without configuration. `sbt server/run` without the bundle runtime
needs the client tools locally:

```bash
brew install libpq                                   # macOS: keg-only, so not on PATH
export HISTMERSION_PG_BIN_DIR=/opt/homebrew/opt/libpq/bin  # …which is what this points at
```

If they are missing, `histmersion backup` says so and does nothing else. Mind the **major version**:
homebrew's libpq is newer than the server, and a `pg_restore` newer than the target Postgres emits
`SET transaction_timeout`, which Postgres < 17 rejects — the data restores fine, and the skipped
statement is reported as a warning. Matching versions (as in the bundle) produce none.

The DB-gated integration test (`com.histmersion.server.BackupRestoreSuite`) exercises exactly this path —
backup, download, drop a source, `restore --force`, find it again — and skips with a reason when
`HISTMERSION_TEST_DB_URL` or the client tools are absent.

Manual escape hatch, if the server itself will not start: stop the service, start the cluster by
hand with the bundle's own tools, dump it, stop it again (macOS paths; on Linux the bundle is
`~/.local/opt/histmersion` and the data `~/.local/share/histmersion`):

```bash
just stop
pg="$HOME/Applications/Histmersion/pg/bin"; data="$HOME/Library/Application Support/Histmersion"
"$pg/pg_ctl" -D "$data/pg" -l "$data/logs/postgres.log" -w \
  -o "-p 5433 -c listen_addresses=127.0.0.1 -c unix_socket_directories=''" start
PGPASSWORD=histmersion "$pg/pg_dump" -h 127.0.0.1 -p 5433 -U histmersion -d histmersion -Fc -f histmersion.dump
"$pg/pg_ctl" -D "$data/pg" -m fast -w stop
```

The password is `db.password` (`HISTMERSION_DB_PASS`, default `histmersion`), which the cluster was
initialised with.

## Native-image notes

Both binaries are GraalVM `native-image` builds (~40–60 s each; the CLI binary is 50 MB, the server
binary 78 MB). The lessons that keep them working — the full list is in
[`AGENTS.md`](AGENTS.md):

- **The JDK is pinned to GraalVM CE 25.0.1** (`.sdkmanrc` and CI together; `scripts/bundle/server.sh`
  refuses any other major).
  It was 22.0.2 until tapir 1.13.31: the older `tapir-netty-server-sync` pulled ox 0.5.x, whose
  `DoNothingScope extends StructuredTaskScope`, and JEP 505 made that an interface in JDK 25. ox 1.0.x
  uses its own `ThreadHerd` over virtual threads instead, so the preview API is gone from the stack.
- **Run the app once after adding a dependency.** `sbt server/run` / `sbt cli/run` fork under the
  native-image tracing agent and merge reachability metadata into
  `modules/<m>/src/main/resources/META-INF/native-image/reachability-metadata.json` (one file per
  module — the JDK-25 format; the legacy split `*-config.json` files are gone and must not come back).
  Commit the changed JSON. Skip this and you get a binary that builds cleanly and fails at runtime.
  Regenerate only with the pinned JDK, and exercise every `web/` asset plus an empty database, because
  resources are recorded one by one as they are requested.
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

- **`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called … scala.runtime.LazyVals$`** on
  stderr from the native binaries (JDK 25, JEP 498). Cosmetic. Scala 3.8 itself compiles lazy vals to
  `VarHandle`s (our own jars are clean); the caller is the *old* `LazyVals.getOffset*` path inside
  dependencies still published from a pre-3.8 compiler — circe-core, cats-core, tapir-core, sttp core,
  decline, sconfig, magnolia (`unzip -p <jar> … | grep getOffset` to check). It goes away as those
  libraries re-publish on 3.8+. **Fix: [VirtusLab Sloth](https://github.com/VirtusLab/sloth)** rewrites
  that bytecode to the 3.8 `VarHandle` form; `nativeImage` runs it over a copy of every classpath jar
  (`target/sloth/`) when a Sloth CLI jar is found (`SLOTH_JAR`, or a checkout at `~/Github/sloth`
  built with `sbt cli/assembly`), and logs a warning and builds unpatched otherwise. Sloth is
  alpha; the patched CLI/server pass the same tests and run clean on JDK 25. Without it,
  `-Dsun.misc.unsafe.memory.access=allow` on the binary's command line does not help (the property is
  read before `main`); stdout is unaffected either way.

| symptom | cause and fix |
|---|---|
| `cannot reach Histmersion at http://localhost:8000 — is the server running?` | the server is down or on another port. `just health`, `just logs`, `just restart`. Check `HISTMERSION_PORT`/`HISTMERSION_SERVER_URL` agree. |
| `NoClassDefFoundError` / `IncompatibleClassChangeError` on `ox.DoNothingScope` right after "Histmersion listening" | an old tapir (1.11.x → ox 0.5.x) on JDK 25. The pin is tapir 1.13.31 / ox 1.0.2 — `sbt update` after a rebase, or `sdk env install` if the JDK drifted. |
| `not a directory on the server: /some/path` from `seed` | the CLI reads a path that exists on this machine itself and asks the server only otherwise — so the path exists on neither. Check it; for plain `histmersion seed`, set `HISTMERSION_CORPUS_DIR` (`.env`) and re-run `just install`. |
| Native binary dies on a missing class or resource | metadata is stale. Run the app once under sbt (`sbt server/run`), commit the changed `META-INF/native-image/reachability-metadata.json`, rebuild. |
| `sbt test` "passes" without running | sbt 2 caches test results. `sbt "testOnly *"`. |
| An env var is ignored by sbt | the sbt 2 thin client shares a server and drops the environment. `sbt --server -batch <task>`. |
| Map tiles do not load | offline or firewalled. Point `HISTMERSION_TILES` at a local tile server. Leaflet itself is vendored, so the rest of the page works. |
| Server exits 3 at startup | a Flyway migration failed, or the database is unreachable. `just logs`; the message names the migration. |
| `git status` shows `agent-pid*/` | a forked `sbt run` was killed mid-write. Safe to delete; they are gitignored. |

## License

TBD.
