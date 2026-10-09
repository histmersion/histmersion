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
~/.config/histmersion/histmersion.conf                ~/.config/histmersion/histmersion.conf       your settings (see Configuration)
```

The server supervises everything itself: on first start it creates the database cluster, then runs
Postgres (loopback only, port 5433) and the converter (8100) as child processes and stops them with
it. It binds `127.0.0.1:8000` unless `HISTMERSION_BIND` says otherwise.

**First-run data.** The bundle also carries `db/`: the gazetteer ([`db/places`](db/places/README.md) —
Natural Earth, Pleiades, the traced Bronze/Iron-Age polities) and the timeline's period presets
([`db/periods`](db/periods/README.md) — a curated PeriodO subset). On a start that finds the `places`
or `periods` table empty — or a bundled period seed newer than the one last applied (`seed_versions`) —
the server imports the bundled copy itself, as a background **`init` job**
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

**Changing a setting:** edit `histmersion.conf` (`histmersion config path` prints where it is; the
installer writes a commented template there) and restart. See [Configuration](#configuration).

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
| `histmersion passages [--regex] [--limit <integer>] [--context <integer>] <citekey> <query>` | Find a word, phrase or regex inside one source's full text: the passages around each match, with offsets for get --offset |
| `histmersion geo-when [--region <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>]` | Findings (the map feed) by place, deep time, bbox — the same filters as `search` |
| `histmersion histogram [--region <string>] [--unit <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | When is the evidence? Sources per adaptive time bucket (Markdown table + bar chart), for the same filters as search |
| `histmersion neighbors [--kind <string>]... <citekey>` | Typed references to/from a source (references, challenges, refutes, updates, superseded_by) |
| `histmersion graph [--depth <integer>] <citekey>` | Source dependency graph: BFS traversal returning D3-ready nodes + links |
| `histmersion contradictions [--since <integer>] [<citekey>]` | Disagreement edges (challenges, refutes, updates, superseded_by) and contested/superseded/debunked/retracted statuses |
| `histmersion put <file.md>` | Send ONE note file (frontmatter + body) as a full create/replace |
| `histmersion patch [--json <string>] [--tags-add <string>] [--tags-remove <string>] [--tags-replace <string>] [--topics <string>] [--regions <string>] [--summary <string>] [--status <string>] [--title <string>] [--notes-file <string>] [--period <string>] [--body-file <string>] [--add-finding <string>]... [--remove-finding <string>]... [--if-updated-at <string>] [--add-edge <string>]... [--remove-edge <string>]... [--strict] <citekey>` | Selectively enrich one source — only the parts you pass are changed |
| `histmersion rename [--json] <old> <new>` | Give a source a new citekey — findings, links in articles/notes and every table follow; the old citekey keeps resolving as an alias |
| `histmersion delete [--apply] [--json] <citekey>` | Delete a source with its findings, edges, figures and stored original — a dry run unless --apply; refused while an article cites it; Calibre is never touched |
| `histmersion export [--sources-dir <string>] [--articles-dir <string>] [--meta-dir <string>] [--no-meta] <dir> [<citekey>...]` | Write sources to <dir>/sources/, articles to <dir>/<kind>/, bookkeeping to <dir>/meta/ — the seed layout |
| `histmersion backup [--download <string>]` | Take a pg_dump backup of the server's database (optionally copy it here) |
| `histmersion backups` | List the backups on the server, newest first |
| `histmersion restore [--force] [--yes] <file>` | Restore a server-side backup (pg_restore --clean); refuses a non-empty database without --force |
| `histmersion bundle export [--since <string>] <file.hmx>` | Write the whole database (or, with --since, what changed after a moment, deletions included) to a portable .hmx bundle |
| `histmersion bundle verify [--json] <file.hmx>` | Check a bundle against this server: format, schema, every checksum, record and blob — every problem listed |
| `histmersion bundle import [--dry-run] [--resolutions <string>] [--resolve <string>]... [--editions <string>] [--json] <file.hmx>` | Import a bundle: what the merge plan says is new or changed, after every open issue is decided (--resolutions); refused while one is open |
| `histmersion bundle instance [--label <string>]` | This collection's identity (the origin of every row it creates); --label names it, e.g. work or home |
| `histmersion merge plan [--resolutions <string>] [--resolve <string>]... [--editions <string>] [--write-template <string>] [--all] [--json] <file.hmx>` | Dry run of importing a bundle into this collection: every match and every issue in one run, with stable ids to decide them by |
| `histmersion merge apply [--resolutions <string>] [--resolve <string>]... [--editions <string>] [--plan <string>] [--yes] <file.hmx>` | Carry out the merge of a bundle into this collection, as planned: a backup first, then one transaction |
| `histmersion merge list [--limit <integer>] [--json]` | The merges this collection took in, newest first |
| `histmersion merge show [--json] <merge-id>` | One merge: its report, the resolutions it was given, the decisions it remembers |
| `histmersion merge ledger [--uid <string>] [--merge <string>] [--limit <integer>] [--json]` | The merge ledger: which record of another collection each record here came from (one record with --uid, one merge with --merge) |
| `histmersion article list [--kind <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | List/search compiled articles (the filter flags select the sources they cite) |
| `histmersion article get [--parts <string>] <slug>` | Show one article (parts: body,summary,coverage,cited,backlinks — default all) |
| `histmersion article put [--kind <string>] [--strict] <file.md>` | Send ONE article note file (frontmatter + body) as a full create/replace |
| `histmersion article patch [--json <string>] [--title <string>] [--summary <string>] [--kind <string>] [--body-file <string>] [--append-file <string>] [--tags-add <string>] [--tags-remove <string>] [--tags-replace <string>] [--regions <string>] [--period <string>] [--if-updated-at <string>] [--strict] [--filter-query <string>] [--filter-topics <string>] [--filter-tags <string>] [--filter-status <string>] [--filter-places <string>] [--filter-period <string>] [--extra-query <string>]... [--clear-filter] [--reason <string>]... <slug>` | Extend or correct one article — only the parts you pass change |
| `histmersion article lint [--fix] [--json] <slug>` | Formatting problems of one article's body and summary (--fix applies the wording-safe repairs) |
| `histmersion article check [--edits-file <string>] [--body-file <string>] [--kind <string>] [--json] <slug>` | Dry-run edits to an article (or a whole draft body) and list the problems the review checks would find — writes nothing |
| `histmersion article coverage [--json] <slug>` | The article's filter against the corpus: sources cited, not used (with reasons), statuses, edges, drift, and the generated sections |
| `histmersion article delete <slug>` | Remove one article (its links go with it) |
| `histmersion article export <dir>` | Write each article to <dir>/<kind>/<slug>.md |
| `histmersion article import [--force] <dir>` | Import <dir>/<kind>/*.md (and any *.md in <dir> itself) |
| `histmersion article versions <slug>` | Show the edit history of one article |
| `histmersion article version <slug> <id>` | Show one historical version's full body |
| `histmersion article rename [--json] <old> <new>` | Give an article a new slug — versions, links and every [[old]] in other articles follow; the old one keeps resolving as an alias |
| `histmersion places search [--as-of <integer>] [--limit <integer>] [<query>]` | Find places by name; the `id` it prints is what `--place` wants |
| `histmersion places show [--as-of <integer>] [--depth <integer>] [--no-tree] <id>` | One place: geometry, validity, parents and the subtree |
| `histmersion places children [--as-of <integer>] <id>` | What one place contains, as of a year |
| `histmersion places rename [--json] <old> <new>` | Give a place a new gazetteer id — names, parents, events and finding assignments follow; the old one keeps resolving as an alias |
| `histmersion places name add [--lang <string>] <place-id> <name>` | Give a place a further name (kept by every re-seed): `places name add pleiades_678430 'Lake Kinneret' --lang en` |
| `histmersion places name rm [--lang <string>] <place-id> <name>` | Remove a name added by hand (a name the seed gives cannot be removed) |
| `histmersion places name list [--place <string>] [--csv]` | The names added by hand; --csv prints them as db/places/supplement/names.csv lines, to promote into the seed |
| `histmersion places add [--kind <string>] --lon <floating-point> --lat <floating-point> --source <string> [--id <string>] [--name <string>]... [--lang <string>] [--from <integer>] [--to <integer>] <label>` | Add a point place of this collection's own (never touched by a re-seed); --source says where the point comes from |
| `histmersion places import [--kind <string>] [--batch <integer>] [--dataset <string>] <file.json>` | Upsert a Linked Places / GeoJSON file into the gazetteer |
| `histmersion places seed [--dir <string>] [--batch <integer>] [--no-rebuild]` | Import every gazetteer file under db/places (Natural Earth, Pleiades, traced polities) |
| `histmersion places fetch [--dry-run] <source>` | Download and import an external gazetteer (cliopatria, awmc, chronontology) or the coastline (land). Re-runnable and idempotent. |
| `histmersion places sources` | The external gazetteer sources: the pinned GeoNames and Wikidata extracts, the LPF files in the drop folder, and what was imported |
| `histmersion places load [--apply] [--no-wait] <geonames|wikidata|lpf>` | Import an external source — geonames, wikidata or lpf (the drop folder) — joined to the gazetteer by shared ids; a dry run unless --apply |
| `histmersion places rebuild` | Re-materialise which findings fall in which places |
| `histmersion places build-hierarchy` | Compute parent-child edges from spatial containment (point inside polygon). Idempotent. |
| `histmersion periods search [--region <string>] [--from <integer>] [--to <integer>] [<query>]` | Find named periods by label, region or time span |
| `histmersion periods show <id>` | One period: its span, authority, what it is part of and its divisions |
| `histmersion periods seed [--file <string>] [--replace]` | Import the period presets bundled with the server (a curated PeriodO subset); idempotent |
| `histmersion periods import [--collection <string>]... [--dataset <string>] <file.json>` | Upsert a PeriodO dataset of your own (a dump or a subset); its periods are this collection's and travel in a bundle |
| `histmersion periods fetch [--collection <string>]... <source>` | Download PeriodO and import collections, each under one node; re-runnable and idempotent |
| `histmersion init [--fetch] [--no-wait]` | Import the bundled gazetteer and period presets (a server job); --fetch adds the network sources |
| `histmersion events search [--kind <string>] [--historicity <string>] [--place-id <string>] [--tag <string>]... [--author <string>]... [--place <string>]... [--topic <string>]... [--status <string>]... [--year-from <integer>] [--year-to <integer>] [--from <integer>] [--to <integer>] [--bbox <string>] [--sort <string>] [--limit <integer>] [--offset <integer>] [--title <string>] [<query>]` | Find events by label or description (the filter flags select their sources) |
| `histmersion events show <id>` | One event: datings, edges, participants |
| `histmersion events rename [--json] <old> <new>` | Give an event a new id — attestations and asset links follow; the old one keeps resolving as an alias |
| `histmersion attestations search [--citekey <string>] [--target-type <string>] [--target-id <string>] [--limit <integer>]` | Find attestations by source or target |
| `histmersion attestations show <id>` | One attestation by id |
| `histmersion attestations add --citekey <string> --target-type <string> --target-id <string> [--quote <string>] [--page <string>] [--chunk <integer>] [--note <string>]` | Create an attestation linking an entity to a source passage |
| `histmersion attestations rm <id>` | Delete an attestation by id |
| `histmersion goals list [--status <string>] [--limit <integer>]` | List research goals |
| `histmersion goals get <slug>` | Show one goal: sub-goals, links, schedules, approved plan |
| `histmersion goals update [--title <string>] [--status <string>] [--notes <string>] [--budget-usd <floating-point>] <slug>` | Update goal fields (patch) |
| `histmersion goals plan [--model <string>] [--budget-usd <floating-point>] [--follow] <slug>` | Ask the server's planner for sub-goals, search queries and works (DOIs checked upstream); nothing is created until you approve the plan (histmersion agent approve) |
| `histmersion goals link --source <string> [--note <string>] <slug>` | Link a goal to a source, an article, a search query or a queue item |
| `histmersion goals unlink <slug> <kind> <ref>` | Remove one link of a goal |
| `histmersion goals schedule --remove <slug> <kind>` | Set (or --remove) a goal's currency or acquisition schedule; it runs only while the server's agents are enabled |
| `histmersion goals schedules` | Every goal's schedules, the next due first |
| `histmersion goals rename [--json] <old> <new>` | Give a goal a new slug — sub-goals, links and schedules follow; the old one keeps resolving as an alias |
| `histmersion agent-state list` | List all agent state keys |
| `histmersion agent-state get <key>` | Get one agent state entry |
| `histmersion agent-state set <key> <value>` | Set an agent state key (value as JSON) |
| `histmersion agent models set <role> <provider:model>...` | Save a role's chain over the config: the models tried in order, first choice first |
| `histmersion agent models reset <role>` | Drop a role's saved chain: the server's config (agents.roles.<role>) applies again |
| `histmersion agent models [set | reset]` | Each step role's model chain, the model its next call would use, and every allowance's use per window |
| `histmersion agent allowance [--limit <string>]... [--reserve <integer>] [--remove] <target>` | Save the allowance of a model (provider:model) or of a provider (provider:*): tokens and/or USD per rolling window and a reserve below which the chain's next model is used; --remove goes back to config |
| `histmersion agent quota hold <pool>` | Hold a plan pool: no agent run draws on it until it is released (claude-plan, chatgpt-plan) |
| `histmersion agent quota release <pool>` | Release a held plan pool |
| `histmersion agent quota floors [--reset] <pool> [<floors>]` | Save a plan pool's floors — the share of each window that must stay left, e.g. 'five_hour=30, seven_day=25' (Codex windows by length: '300m=25, 10080m=20'); --reset goes back to config |
| `histmersion agent quota fallthrough <role> <on|off>` | Whether a role's chain may fall from a plan below its floor to a PAID key (off: the run pauses until the reset) |
| `histmersion agent quota refresh [<pool>]` | Take a fresh reading of a plan pool now, or of every pool (claude-plan, chatgpt-plan): the ChatGPT plan through codex app-server (spends nothing), the Claude plan through one minimal claude -p turn (spends a little of the plan) |
| `histmersion agent quota [hold | release | floors | fallthrough | refresh]` | Subscription pools (Claude plan, ChatGPT plan): each window's use, its floor, reset and reading age; the hold switch; metered allowances |
| `histmersion agent generate [--runtime <string>]... [--out <string>]` | Write the per-runtime agent definitions (Claude Code plugins, Codex home, opencode config) from the server's bundled agents and skills — on the SERVER's disk |
| `histmersion agent doctor [--offline]` | Check every provider without spending anything: keys, the free model listing, prices, the claude CLI and its login |
| `histmersion agent probe [--model <string>] <citekey>` | One live model call with one read-only tool (get_source) and a typed answer, checked against the record |
| `histmersion agent run [--model <string>] [--budget-usd <floating-point>] [--budget-tokens <integer>] [--fix] [--rounds <integer>] [--acquire-item <integer>] [--title <string>] [--kind <string>] [--query <string>] [--topic <string>]... [--tag <string>]... [--goal <string>] [--dry-run] [--model-for <string>]... [--follow] <pipeline> <target>` | Start a pipeline on a target (enrich-source <source>, review-article <article>, plan-goal <goal>, compile-article <article>, currency-check <goal>, acquisition-sweep <goal>) |
| `histmersion agent runs [--status <string>] [--limit <integer>] [--goal <string>]` | Agent runs, newest first |
| `histmersion agent show [--messages] [--follow] <id>` | One run: its steps — tokens (with the first call's input and the turns), cost — and warnings (and --messages: the trace) |
| `histmersion agent compare [--json] <run-id>...` | Runs of one pipeline on one target side by side: per step the model (configured → reported), tokens, the first call's input and the turns, cost or plan pool, time and attempts; then the outputs (enrich-source: findings, quote check, places, periods, summary and notes, the claims only one run made; review-article: verdicts, findings per reviewer and kind, the major findings runs share) |
| `histmersion agent pause <id>` | Pause a run before its next step |
| `histmersion agent resume [--budget-usd <floating-point>] [--budget-tokens <integer>] <id>` | Resume a paused run (finished steps are replayed, not re-run) |
| `histmersion agent cancel <id>` | Cancel a run |
| `histmersion agent approvals [--state <string>] [--limit <integer>]` | Changes runs proposed for a person to decide (statuses, disagreement edges, goal plans, articles to publish); the open ones by default |
| `histmersion agent approve [--note <string>] <id>` | Approve a proposed change: a source change is applied through the ordinary patch; a plan or an article lets its waiting run go on and apply it |
| `histmersion agent reject [--note <string>] <id>` | Reject a proposed change (it stays on record) |
| `histmersion media detail <citekey> <name>` | Show asset metadata including spatial/temporal |
| `histmersion media patch [--kind <string>] [--caption <string>] [--license <string>] [--lat <floating-point>] [--lon <floating-point>] [--period-start <integer>] [--period-end <integer>] <citekey> <name>` | Update asset metadata |
| `histmersion asset-links search [--citekey <string>] [--asset <string>] [--target-type <string>] [--target-id <string>]` | Find asset links |
| `histmersion asset-links add --citekey <string> --asset <string> --relation <string> --target-type <string> --target-id <string> [--xywh <string>] [--notes <string>]` | Create an asset link |
| `histmersion asset-links rm <id>` | Delete an asset link by id |
| `histmersion lint [--rule <string>]... [--fix] [--article <string>] [--source <string>] [--file <string>] [--json]` | Health-check the corpus (30 rules: dangling-edges, unresolved-short-citekeys, no-findings, no-period, no-regions, empty-summary, unassessed-status, duplicate-doi, duplicate-sha256, orphan-sources, stale-chunks, undated-findings, unlocated-findings, legacy-figure-links, dangling-article-links, non-canonical-tag, missing-year, year-citekey-mismatch, author-pipe, article-filler, article-uncited-paragraph, article-thin-summary, superseded-without-edge, article-markdown, note-markdown, finding-geocode, finding-unplaced, place-kind, place-grid-point, place-imported-point-area) |
| `histmersion queue [--topic <string>] [--reason <string>] [--all] [--json] [<args>...]` | Bookkeeping queues: acquire / tasks / log — show, add, done, rm, export (with the goals as GOALS.md), import |
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
| `histmersion calibre search [--unlinked] [--limit <integer>] [--json] [<query>]` | Search the local Calibre library (read-only); --unlinked lists the entries no source links yet, newest first |
| `histmersion calibre import [--citekey <string>] [--format <string>] [--no-convert] [--no-wait] [--acquire-item <integer>] [--json] <calibre-id>` | Import a purchased book from Calibre into the corpus and convert it (the Calibre original is never touched) |
| `histmersion calibre link [--json] <citekey> <calibre-id>` | Link a source to an existing Calibre entry (checks the entry exists; Calibre itself is not changed) |
| `histmersion calibre push [--file <string>] [--replace] [--json] <citekey>` | Add a stored source's file to Calibre with title/authors/DOI/tags; --replace swaps the file of its linked entry instead (Calibre GUI must be closed) |
| `histmersion calibre update [--json] <citekey>` | Rewrite a source's Calibre entry from the corpus: title, authors, year, identifiers, tags (and publisher/comments where empty); --all for every linked entry (Calibre GUI must be closed) |
| `histmersion calibre set --field <string> [--field <string>]... <calibre-id>` | Set fields on a Calibre entry, e.g. --field languages:eng (settable: title, authors, publisher, pubdate, languages, identifiers, tags, comments, series; Calibre GUI must be closed) |
| `histmersion calibre audit [--json]` | Compare every source's Calibre entry with the corpus record (title, authors, year) — read-only |
| `histmersion calibre sync [--apply] [--limit <integer>] [--json]` | Push every source that is not in Calibre yet (dry run unless --apply) |
| `histmersion calibre gaps [--quiet] [--limit <integer>] [--json]` | List sources missing from Calibre; --quiet prints one line and exits 1 when there are any (the reminder hook) |
| `histmersion reocr [--quality] [--no-wait] <citekey>` | Convert a source's stored original again (e.g. under the slow, accurate OCR path) |
| `histmersion assets <citekey>` | The figures stored with a source, and the URLs they are served at |
| `histmersion jobs [--status <string>] [--limit <integer>] [<id>]` | Recent OCR jobs, or one job by id |
| `histmersion config path` | Where the user config file is on this machine (printed alone on stdout), whether it exists and reads cleanly; the running server's file is in histmersion about |

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
histmersion periods seed --replace                        # re-apply the bundled presets, dropping seed periods it no longer has
```

## MCP tools

**66 tools**, wired for Claude Code through `.mcp.json` (`type: http`, `<public-url>/mcp`, written
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
| `passages` | **to find what one source says about a term** — `grep` over one body: the passages around each literal match (case- and accent-insensitive; `regex: true` for a Java regex), each with the heading it sits under and the body `offset` that `get_source` reads on from, then how many more there are. |
| `geo_when` | "what happened *here*, *then*" — individual findings by `region` name, bbox (antimeridian-safe) and `periodStart`/`periodEnd`. |
| `histogram` | "when is the evidence" — sources or findings per adaptive time bucket, the same aggregation the web UI timeline draws. |
| `neighbors` | **before relying on a claim.** The typed edges to and from a source, plus the articles that cite it. Stored kinds are `references`, `challenges`, `refutes`, `updates`, `superseded_by`; only the last four are disagreement. Edges join stored sources; one pointing at a work not in the corpus yet is listed as unresolved. |
| `graph` | the citation network around a source, breadth-first to `depth`: nodes (citekey, title, year, status) and typed links — what the web UI's graph window draws. |
| `contradictions` | disagreement edges and contested statuses: for one `citekey`, both directions with the other side's year and status; without one, corpus-wide. An empty answer is not evidence of agreement. |
| `periods` | **named periods** (PeriodO definitions: Bronze Age, Late Minoan IB, Old Babylonian, Yamnaya…). Without `id`: search by label, `region` or overlap with `from`..`to`; with `id`: span, region, the authority that states the range, what it is part of and how it is divided. Turns a period name into the years to pass to `search`/`geo_when` — cite the authority, since authorities date the same name differently. |
| `places` | **the gazetteer.** Without `id`: a name a human wrote ("Anatolia", "Ugarit") → a place **id**; pass that as `places` to `search` and it matches everything inside the shape *and* below it in the hierarchy, which a bare name never does. With `id`: geometry, validity, parents and subtree. `asOf` reads the hierarchy as it stood in a given year. |
| `list_articles` | the **compiled wiki** — list/search articles by `kind`, full-text `query` (the article's own title/summary/body), `title` (substring), `tag` (articles citing a source with that tag). Call it before compiling anything; the topic may already have an article to extend. |
| `get_article` | one article by slug: body, the **generated coverage sections** (Not used + Method, when it records a filter), **backlinks**, and the **sources cited** with title · year · status — and a **Formatting** section listing what in the Markdown will not display as written (line, column, excerpt). |
| `article_coverage` | **an article's filter against the live corpus** (#958): how many sources the filter it records returns, which the body cites, every other one with its stored reason (or "not yet assessed"), the status breakdown, the disagreement edges touching the cited sources, the further searches, and the **drift** — sources neither cited nor given a reason, stale reasons — then the Not used list and `_Method_` note exactly as the page generates them. CLI: `histmersion article coverage`. |
| `article_versions` / `article_version` | an article's edit history (newest first), and the full body of one past version — to see what a rewrite changed or recover text a bad patch overwrote. |
| `check_edits` | **dry-run a change to an article, writing nothing**: `edits` (`{op: replace\|insertAfter\|insertBefore\|delete, anchor, text}`, applied in order to the stored body, each anchor found exactly once) or a whole draft `body`, checked exactly as `review-article` checks its fixer's answer — each edit's fate, then the result's links to nothing, uncited paragraphs, Sources cited heading, filler and new formatting errors, and whether one is fatal. CLI: `histmersion article check`. |
| `schema` | the tables, columns and types `query` can read, from the live `information_schema` — check names before writing SQL. |
| `query` | one **read-only** SQL statement for what the other tools cannot express. Runs in a `READ ONLY` transaction with a statement timeout and row cap; the description lists the live tables from `information_schema`. |
| `lint` | **after a batch of ingestion.** 25 corpus-integrity rules (dangling edge targets, unresolved short citekeys, sources with no findings/period/regions/summary, `unassessed` status, duplicate DOI/sha256, orphans, stale chunks, undated/unlocated findings, the wave-3 tag/year/author/article checks, `legacy-figure-links` — bodies whose figures still point at a `figures/…` path — `dangling-article-links` — `[[wikilinks]]` that resolve to no source and no article — and the formatting rules `article-markdown` / `note-markdown`, see *Formatting lint*). `fix: true` applies the two safe repairs. With `article` (a slug), `source` (a citekey) or `text` (Markdown not stored yet) it checks just that one for formatting problems, and `fix: true` applies the wording-safe repairs to it. |

### Write

| tool | when to use it |
|---|---|
| `put_source` | create or **replace** a whole source. Destructive — creation and deliberate re-import only. |
| `patch_source` | **the enrichment tool.** Send only what changes: `metadata`, `summary`, `body`, `notes`, `topics`, `regions`, `period`, `tags{add,remove,replace}`, `findings{add,remove,replace}`, `edges{add,remove}`. `ifUpdatedAt` returns 412 instead of clobbering. The answer lists formatting problems in the summary and notes; `strict: true` refuses a patch whose summary/notes have a formatting error. |
| `rename_label` | give an article a new slug, an event a new id, a goal a new slug or a place a new gazetteer id (`kind`: article · event · goal · place): what points at it follows (an article's `[[old]]` links in other articles, each a recorded version; an event's attestations and asset links; a goal's sub-goals, links and schedules; a place's names, parents, footprints, events, attestations and finding assignments) and the old label keeps resolving as an alias — for a place, in a `place=<old id>` filter too. |
| `place_names` | names **added by hand** to gazetteer places — the ones every re-seed keeps. Without `name`: lists them (of `placeId`, or all); with `placeId` + `name` (+ `lang`): adds one ("Lake Kinneret" for the Sea of Galilee) so the geocoder and `places=` filters know it; `remove: true` drops one added by hand (a seed name cannot be removed). |
| `add_place` | a **point place** the gazetteer lacks, after `places` found it under no name: `label`, `kind`, `lon`/`lat`, and a required `source` for the point (never an estimate). It is this collection's own (`local_<label>`), carried in a bundle and untouched by a re-seed. |
| `rename_source` | give a source a **new citekey** (e.g. the year in it is not the publication year). One transaction: findings renumbered, every table and every `[[old]]` link in articles (each rewrite a recorded version) and source notes follow. The old citekey stays an **alias** — reads, `/source/<old>` and `[[old]]` keep resolving. |
| `put_article` | create or **replace** a whole article. Cite sources as `[[citekey]]`; write it by [`docs/writing-guide.md`](docs/writing-guide.md). The answer lists its formatting problems; `strict: true` refuses one with an error. |
| `patch_article` | **the way to extend an article**: `append` adds Markdown at the end of the body; or send `body`/`title`/`summary`/`kind`/`regions`/`period`/`tags{…}`. `filter` records the filter the article is compiled from (`{}` clears it) and `reasons` `[{citekey, reason}]` why each of its sources the body does not cite is left out (an empty reason removes one): the page then **generates** the Not used list, its counts and the `_Method_` note — nobody writes them. `ifUpdatedAt` guards. The answer lists the formatting problems of the article as patched; `strict: true` refuses the patch when the result has an error. |
| `queue` | the project's bookkeeping in one tool: `action` = `list`\|`add`\|`done` over `acquire` (what the USER must obtain), `tasks` (the agent's backlog), `log` (the dated worklog). Use it instead of writing a tracker file. |

### Acquisition

| tool | when to use it |
|---|---|
| `discover` | **literature we do NOT have yet.** `query` searches OpenAlex, `doi` fetches one paper's metadata, `wikipediaUrl` harvests every DOI a Wikipedia article cites (the trailhead). Each hit carries the citekey it would get; `notInDb: true` gives the delta. |
| `ingest_doi` | bring a paper in by DOI or `arxiv:<id>`: metadata → legal OA PDF (Unpaywall → CORE → arXiv) → OCR job → reference edges → assessment. Idempotent; never wipes enrichment. |
| `ingest` | OCR a PDF/EPUB into a source **keeping its figures**. Takes a `path` the SERVER can see or a `base64` payload (≤ `limits.inline-bytes`); returns a **job id**, because a long scan takes minutes. `acquireItem` names the acquisition item the document fulfils: the job ticks it off when it succeeds. Without it, the job ticks off every open acquisition item that names the source's DOI and lists the items with a similar title as possible matches, never closing those. |
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
| `merge_plan` | **read-only** plan of importing another collection's bundle (uploaded by `histmersion merge plan <file>`, named by its sha256): every source matched (by uid, ledger, DOI/file/ISBN or score), and EVERY issue in one run — two citekeys for one work, one citekey for two works, differing DOIs/years/statuses, rows changed on both sides, diverged articles — each with a stable id, the evidence and the allowed decisions (left = here, right = the bundle). `resolve` shows what would remain. Present the plan; scholarly decisions are the user's. |
| `merges` | **read-only**: the merges this collection took in (`action: list`), one merge's report, resolutions and remembered decisions (`show`, `id`), or the merge ledger of a record (`ledger`, `uid`: a uid or a citekey) — where a record came from and which merged-away uids redirect to it. Applying a merge is the user's (`histmersion merge apply`, or the `/merge` screen). |

### Events, evidence, assets and research memory

| tool | when to use it |
|---|---|
| `events` | the events database: CIDOC-CRM-style events with four-point time-spans, a part-of hierarchy, temporal/causal edges and participant roles. Lists (by `query` over label/description, `title` over the label, `kind`, `historicity`, `placeId`, `from`/`to` overlapping the event's own span), or shows one by `id` — each with its **scale** (`local` · `regional`, stated or derived). |
| `patch_event` | change fields of one event without replacing it: label, kind, historicity, description, citekey, parentId, `placeId` (where it happened), its own geometry (GeoJSON, or `lat`/`lon`), and `scale` — `local` (one place: a battle, a siege, a destruction) · `regional` (across a region: a migration, an era, a war) · `auto` (forget the stated one; derived from the kind, then a sub-event is local, then the place's kind: a site local, a region/polity/people regional). The map's playback draws a local event as a dot and label at its site that fade after it ends, a regional one as a label at the region's centre with the region's outline for its span. |
| `attestations` / `put_attestation` / `delete_attestation` | granular evidence: link a finding, event, dating, place or edge to the exact passage of a source (W3C Web Annotation selectors: quote, position, chunk, page). |
| `patch_asset` | a stored figure's metadata: kind (figure/photo/map/plan/scan), caption, licence, location and period. |
| `asset_links` / `delete_asset_link` | typed links between figures and events, findings, places, sources or articles. |
| `goals` / `put_goal` / `patch_goal` | research programmes: slug, title, status (`planned`→`seeding`→`compiled`→`reviewed`→`done`), progress notes, scope, the cost cap of their agent runs; with `slug`, a goal's sub-goals, links, schedules and approved plan. |
| `agent_state` / `delete_agent_state` | a small key-value store an agent keeps across sessions (set, get, list, delete). |

### Embedded agents (off unless the server enables them)

| tool | when to use it |
|---|---|
| `start_run` | hand a whole pipeline to the server: `enrich-source <citekey>` (summary, classification, period, findings with verbatim quotes, notes; metadata checked against OpenAlex/Crossref; `refs`/`assess`; one verified patch), `review-article <slug>` (the three-reviewer panel in parallel, merged by code into the article's review ledger; with `fix`, a fixer revises the article, each reviewer verifies the findings it claims fixed and the edited passages, and a full panel confirms; one backlog item per article lists its open MAJORs), `compile-article <slug>` (a new article, or a section appended to one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check — the run **waits** until a person approves, then writes and chains a review), `plan-goal <goal>` (sub-goals, queries and DOIs checked upstream — nothing is created before approval), `currency-check <goal>` and `acquisition-sweep <goal>`. Returns the run id at once; the run is checkpointed, budgeted and resumable. `dryRun: true` runs every read and model step but writes nothing (each write is recorded on its step); `modelFor: ["role=provider:model"]` gives one role its own model for this run. |
| `run_status` | the newest runs (optionally by `status` — `waiting` for an approval — or `goal`), or one run's steps (configured model → the model the provider reported), tokens, cost, warnings, a dry run's unwritten writes and (`messages: true`) trace; `compare: [ids]` lays runs of one pipeline on one target side by side. |
| `list_approvals` | what runs PROPOSED for a person to decide — a status, a disagreement edge, a goal's plan, an article to publish — with the evidence and the quote code verified. |
| `decide_approval` | approve (applies a source change through the ordinary patch, or lets the waiting run apply its plan or write its article) or reject one approval. |
| `goal_links` | link a goal to a source, an article, a search query or a queue item (or unlink it). |
| `goal_schedule` | a goal's `currency` and `acquisition` schedules (cron in UTC): set, remove, or list them all. They run only while the server's agents are on, within the cost caps. |
| `agent_models` | each step role's model **chain** (tried in order; the first model with allowance left is used) and each allowance (tokens/USD per rolling window, a reserve) with what is used, left, and when a window frees; `quota: true` shows the **subscription pools** instead (Claude plan, ChatGPT plan: each window's use against its floor, reset, reading age and source, the hold switch); `doctor: true` checks every provider (keys, the free model listing, prices, the `claude`/`codex`/`opencode` CLIs and their logins — nothing is spent). |
| `set_agent_models` | change a role's chain (`role` + `models`) or an allowance (`target` — `provider:model` or `provider:*` — + `limits` like `"2M tokens/5h"` + `reservePct`), saved over the server's config; `reset: true` goes back to config. With `pool` (`claude-plan`, `chatgpt-plan`): `hold` pauses or releases all agent use of that subscription, `floors` (`"five_hour=30, seven_day=25"`) the share of each window that must stay left; `refresh: true` (with or without `pool`) takes a fresh reading now — the ChatGPT plan through `codex app-server` (free), the Claude plan through one minimal `claude -p` turn; with `role` + `fallthrough`: whether that chain may fall from a plan below its floor to a paid key. |

A CLI step (`claude-code`, `codex`, `opencode`) reaches a second, read-only MCP endpoint,
**`<public-url>/mcp/agent`**, with a **run token** the server issues for that one step: the endpoint
answers only to a live token and serves only the tools of the step's agent definition (`/mcp/agent/<agent>`;
the agent tools are `get_source`, `passages`, `search`, `facets`, `neighbors`, `query`, `get_article`, `followups`
without `apply`, `check_edits` and `article_coverage`). It is not meant for an interactive client.

## Skills

Nineteen skills ship with the repo (`skills/<name>/SKILL.md`) and install into a workspace through
the plugin — see [`plugin/README.md`](plugin/README.md). One canonical copy, which names tools by
placeholder (`{{tool:get_source}}`); the agent generator renders it per runtime — `plugin/skills/` is
its checked-in Claude Code rendering (`just plugin`; a test fails when it is stale), and
`.claude/skills`, `.opencode/skill` point there. Edit `skills/` and `agents/`, never `plugin/`. Five are
the reference skills for the tool surface:

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
| `GET` | `/api/about` | — | `About{name, build{version, gitCommit, commitDate, dirty}, health, jobs{queued, running, done, failed}, counts{sources, findings, articles, events, places, periods}, lastBackup, lastBackupAgeSeconds, problems[], configFile{path, loaded, unknownKeys}}` — the web UI's About panel and `histmersion about`; a part that cannot be read is a `problems` line, never an error |
| `GET` | `/api/sources` | the **filter params** (below) | `SearchResult{total, hits[{source, matched[], why[]}]}` — the source is the unit |
| `GET` | `/api/facets` | `field`, `prefix` + filter params | `[{value, count}]` (distinct sources, case-folded) |
| `GET` | `/api/findings` | filter params (+ legacy `region`), `limit` | `[GeoHit]` — the map layer; antimeridian bbox split server-side |
| `GET` | `/api/aggregate` | `by=time\|region\|time,region\|region,time`, `unit=sources\|findings\|starts`, `depth`, `asOf`, `samples` + filter params | `TimeHistogram{from, to, bucket, buckets[], by, unit, total, depth, asOf, tree[]}` — `buckets` is the flat strip (`by=time`), `tree` the §1.6 nested `Bucket{label, key, kind, start, end, placeId, count, overlap, contested, children[], samples[]}` forest |
| `GET` | `/api/sources/{citekey}` | `body` (default `true`; `false` omits `ocrMarkdown`) | `SourceWithFindings` (404 if unknown) |
| `GET` | `/api/sources/{citekey}/articles` | — | `[Backlink{kind, id, title}]` — the compiled articles that CITE this source (404 if the citekey is unknown) |
| `PUT` | `/api/sources/{citekey}` | `SourcePut` body; `strict` | full create/replace; `SourceWithFindings{…, formatting[]}` |
| `PATCH` | `/api/sources/{citekey}` | `SourcePatch` body (`ifUpdatedAt`); `strict` | selective merge; 404 / 412 / 400; the answer's `formatting[]` lists the summary's and notes' formatting problems, and `strict=true` refuses (400) a patch whose summary/notes have an error |
| `GET` | `/api/sources/{citekey}/lint` | — | `FormatReport{subject, issues[FormatIssue{rule, severity, field, line, column, excerpt, message, fix}], fixed[]}` — the summary's and notes' formatting problems |
| `POST` | `/api/sources/{citekey}/lint/fix` | — | apply the wording-safe repairs (one patch, guarded by the `updatedAt` just read) and report what is left |
| `GET` | `/api/sources/{citekey}/export` | — | the note as text (frontmatter + notes) |
| `POST` | `/api/sources/{citekey}/rename` | `{"newCitekey": …}` | `SourceRenamed{oldCitekey, newCitekey, source, counts{findings, articles[], sources[], links, columns[], edgesPromoted}, calibreId}` — one transaction; the old citekey becomes an alias; 400 when the new key is taken or not link-shaped, 404 when the old one is unknown |
| `POST` | `/api/sources/{citekey}/delete` | `{"apply": false}` | `SourceDeletion{citekey, title, applied, blockers[{kind, id, title}], counts[{part, rows}], aliases[], edgesIn[], coverage[], files[], filesFailed[], mentions[], calibreId, logEntry}` — a **dry run** unless `apply`; applied, one transaction: other sources' edges to it are parked (not lost), its old citekeys go, a tombstone keeps a bundle merge from bringing it back, the log queue records it, and the stored original is removed after the commit. 400 while an article cites it (`[[ck]]`, an old citekey or a short form), an event is sourced to it or a job is still writing it — there is no override. Calibre is never touched. Not an MCP tool (a person's decision, like deleting an article) |
| `GET` | `/api/source-aliases` | — | `[{alias, citekey}]` — every old citekey and the source it names now |
| `GET` · `PUT` | `/api/instance` | `PUT`: `{"label": …}` | `InstanceInfo{id, label, createdAt}` — this collection's identity (see *Portable bundles*) |
| `GET` | `/api/bundle` | `since` (an ISO-8601 moment) | the `.hmx` bundle, streamed (`Content-Disposition` names it) |
| `POST` | `/api/bundle/verify` | the `.hmx` file as the body | `BundleVerifyResult{ok, manifest, blobsChecked, problems[]}` |
| `POST` | `/api/bundle/import` | `dryRun`; the `.hmx` file as the body | `BundleImportResult{bundleId, from, dryRun, applied, counts[], blobs, problems[], conflicts[], notes[], mergeId, issues[], planHash}` — planned with no resolutions |
| `POST` | `/api/bundle/upload` | the `.hmx` file as the body | `BundleUpload{sha256, bytes, manifest, problems[]}` — kept server-side under its sha256 |
| `POST` | `/api/merge/plan` | `MergePlanRequest{bundle: <sha256>, resolutions{resolve[{issue, decision}], policy{editions}}}` | `MergePlan{matches, sources[], issues[], info[], problems[], resolutionErrors[], planHash}` — read-only; also MCP `merge_plan` |
| `POST` | `/api/merge/import` | `MergeImportRequest{bundle, resolutions, dryRun}` | `BundleImportResult` — refused while an issue is open or a decision needs `merge apply` |
| `POST` | `/api/merge/apply` | `MergeApplyRequest{bundle, planHash, resolutions}` (a `file:` decision carries its `text`) | `MergeApplyJob{job, state (running · done · failed), bundle, planHash, elapsedMs, result: MergeApplyResult{applied, mergeId, backup, counts[], tally, reviews[], logEntry, replanChanges, problems[], issues[]}, error}` — a background job (its backup first): the request waits `merge.apply-wait-seconds`; refused (nothing written, no backup) while an issue is open or when the plan is not `planHash`; not an MCP tool |
| `GET` | `/api/merge/apply/{job}` | — | `MergeApplyJob` — where an apply is: `running`, `done` (its report), `failed`, or `interrupted` by a restart (a `jobs` row, so it answers across restarts; 404 for an unknown job) |
| `GET` · `PUT` | `/api/merge/sessions/{bundle}` | `PUT`: `MergeResolutions` | `MergeSession{bundle, resolutions, updatedAt}` — the `/merge` screen's decisions so far for an uploaded bundle, kept server-side (dropped by a successful apply) |
| `POST` | `/api/merge/resolutions` | a resolution file (YAML) as the body | `MergeResolutions` — the file read (every bad line reported at once); writes nothing |
| `GET` | `/api/merges` | `limit` | `[MergeInfo{id, bundleId, bundleSha256, from, appliedAt, planHash, backup, ledgerRows, redirects}]` — newest first; also MCP `merges` |
| `GET` | `/api/merges/ledger` | `uid` (a uid or a citekey), `merge`, `limit` | `MergeLedger{query, entries[LedgerRow{foreignUid, kind, mergeId, foreignRev, localUid, label, localRevAfter, decision, recordedAt}], redirects[]}` |
| `GET` | `/api/merges/{id}` | an id or its first characters | `MergeDetail{merge, applied, imported, resolutions, remembered[]}` (404 if unknown) |
| `POST` | `/api/articles/{slug}/rename` · `/api/events/{id}/rename` · `/api/goals/{slug}/rename` · `/api/places/{id}/rename` | `{"to": …}` | `LabelRenamed{entity, from, to, rewritten[]}` — the old label stays an alias (reads follow it, and a `place=<old id>` filter matches the place it names now; a write to it is refused with where it went) |
| `POST` | `/api/sources/import` | `{citekey?, text, mtime?, force?}` | seed ONE note from raw text (host-side `seed`) |
| `GET` | `/api/citekeys` | — | every citekey, sorted |
| `GET` | `/api/neighbors/{citekey}` | — | `[NeighborRow]` |
| `POST` | `/api/seed` | `{dir, force}` | imports a **server-side** directory; rows newer than the file are skipped unless `force` |
| `POST` | `/api/edges/resolve` | — | promote unresolved edges whose target now resolves (exactly or by unique short form) to a stored source |
| `POST` | `/api/reindex` | `after`, `limit` | one resumable slice of the FTS chunk rebuild |
| `POST` | `/api/ingest` | `citekey`, `filename?`, `quality?`, `force?`, `title?`, `author*`, `year?`, `doi?`, `acquireItem?` + the document as the **raw body** | **202** `IngestAccepted{jobId, citekey, status, sha256}`; **409** when the same bytes are already stored under another citekey (unless `force`); **400** when `acquireItem` is not an open acquisition item. With `acquireItem`, the OCR job ticks that item off (`ingested as [[citekey]]`) when it succeeds and leaves it open when it fails. Without it, the job (of every import: an upload, `ingest <doi>`, `archive import`, `calibre import`) closes the open acquisition items that name the source's DOI — any case, resolver prefix or trailing punctuation — the same way, and lists items whose text resembles the title and first author (`acquire.defaults.title-match-at`) in its `progress` as possible matches, never closing them; a re-OCR matches nothing |
| `POST` | `/api/sources/{citekey}/reocr` | `quality` | 202 + a job; converts the spooled original again |
| `GET` | `/api/jobs/{id}` · `/api/jobs` | `status`, `limit` | `JobInfo` / `[JobInfo]` — `queued\|running\|done\|failed` |
| `GET` | `/api/sources/{citekey}/assets` | — | `[AssetInfo{name, mime, bytes, sha256, width, height}]` |
| `POST` | `/api/sources/{citekey}/assets` | `[AssetInput{name, mime, base64, …}]` | add figures to an existing source (host-side `seed`) |
| `GET` | `/source/{citekey}/assets/{name}` | — | the figure's bytes, with its `Content-Type` and `Cache-Control: public, max-age=31536000, immutable` |
| `GET` | `/api/articles` | filter params + `kind`, `text` | `ArticleList{total, articles[]}` — the compiled wiki: the articles CITING at least one source the filter selects (every article when it selects nothing); `title` is the article's own title, `text` FTS over the article's title/summary/body; `limit`/`offset` page the articles |
| `GET` | `/api/events` | filter params + `text`, `kind`, `historicity`, `placeId` | `EventList{total, events[]}` — the events whose source the filter selects (its period aside), whose OWN span overlaps `from`/`to`; `title` is the label, `text` a label/description substring, `placeId` the event's own place; each `EventSummary` carries `scale` (`local` · `regional`) and `scaleStated` (false: derived from the kind, a parent, the place's kind, the geometry) |
| `GET` | `/api/events/geo` | the same params | `EventGeoList{events[{id, label, kind, scale, historicity, timeSpan, lat, lon, anchor, placeId, placeLabel, parentId, outline}], unplaced}` — the map's playback feed (#1440): those events with a position — the event's own point or area, else its place's (an area by its point-on-surface; `anchor`: `event` · `place`) — a REGIONAL one with an area carrying its outline as GeoJSON simplified to `ui.events.outline-tolerance-deg`, at most `ui.events.map-limit`; `unplaced` counts the rest. A web feed like `/api/places/geo`: no MCP tool |
| `GET` | `/api/events/{id}` | — | `EventDetail{event, geometry, parent, children[], datings[], edges[], participants[]}` (404 if unknown; an old id answers as the event it was renamed to) |
| `PUT` | `/api/events/{id}` | `EventPut` body (`scale`: `local` · `regional`, absent = derived) | full create/replace; a `scale` that is none is a 400 |
| `PATCH` | `/api/events/{id}` | `EventPatch` body (`scale`: `local` · `regional` · `auto` = clear) | selective; also MCP `patch_event` and `histmersion events patch` |
| `GET` | `/api/articles/{slug}` | `brief` | `ArticleView{article, backlinks[], sourcesCited[], formatting[], coverage?}` (404 if unknown) — `formatting` lists what in the body and summary will not display as written; `coverage` is present when the article records a filter; `brief=true` leaves both out (the link preview's read) |
| `GET` | `/api/articles/{slug}/coverage` | — | `Coverage{filter, filterLine, total, checked, cited[], notUsed[{source, reason?}], citedOutside[], statuses[], edges[], searches[], reasons[], drift[]}` — the recorded filter against the live corpus, from which every surface renders the generated Not used list and `_Method_` note; 404 when the article records no filter |
| `PUT` | `/api/articles/{slug}` | `ArticlePut` body; `strict` | full create/replace; answers the `ArticleView` (formatting included); `strict=true` refuses (400) a body/summary with a formatting error |
| `PATCH` | `/api/articles/{slug}` | `ArticlePatch` body (`append`, `ifUpdatedAt`, `filter`, `reasons`); `strict` | selective merge; 404 / 412 / 400; `strict=true` judges the article as patched |
| `GET` | `/api/articles/{slug}/lint` | — | `FormatReport` of the body and summary (see *Formatting lint*) |
| `POST` | `/api/articles/{slug}/lint/fix` | — | apply the wording-safe repairs as one guarded patch (the old body stays a version) and report what is left |
| `DELETE` | `/api/articles/{slug}` | — | drops the article and its links |
| `GET` | `/api/articles/{slug}/export` | — | the note as text (`wiki/<kind>/<slug>.md`): the filter and reasons in the frontmatter, the generated sections after the body between markers an import drops |
| `POST` | `/api/articles/import` | `{text, slug?, kind?, mtime?, force?}` | import ONE article note from raw text (host-side `seed`) |
| `GET` | `/api/article-slugs` | — | every slug, sorted |
| `GET` | `/api/lint` | `rule`* (repeatable) | `LintReport{rules[{rule, description, count, items[{citekey, detail}]}]}`; unknown rule → 400 |
| `POST` | `/api/lint/fix` | `rule`* (repeatable) | the safe repairs (resolve short edge targets, reindex stale chunks), bounded per call — loop while `remaining > 0`; a named gazetteer rule applies its repair too (`place-imported-point-area` bounded per call as well — loop while `pointAreasRemaining > 0`) |
| `POST` | `/api/lint/markdown` | `FormatCheck{text, field = body\|summary\|notes, fix}` | `FormatReport` for Markdown not stored yet; with `fix`, `text` is the repaired text |
| `GET` | `/api/queue/{list}` | `all` | open items of `acquire`\|`tasks`\|`log` (`all=true` adds done items and the file's prose blocks) |
| `POST` | `/api/queue/{list}` | `{text, topic?, status?, createdAt?}` | append one item |
| `PATCH` | `/api/queue/items/{id}` | `{status?, text?, topic?, reason?}` | selective edit; `status=done` stamps `done_at`; a blank `topic`/`reason` clears it, a blank `text` is a 400; 404 if unknown |
| `DELETE` | `/api/queue/items/{id}` | — | remove one item, returning it |
| `GET` | `/api/queue/{list}/export` | — | the list rendered as its `meta/<FILE>.md` |
| `POST` | `/api/queue/{list}/import` | `{text, replace=true}` | parse a `meta/*.md` back into rows (idempotent) |
| `GET` | `/api/export/goals` | — | every research goal as `meta/GOALS.md`: the tree (sub-goals nested), status, period/regions/tags, notes, links (articles and sources as `[[…]]`, searches, queue items with their done/open state), schedules and approved plan. Written by `queue export` / `export` beside the queue files; export only, `queue import` ignores it |
| `GET` | `/acquire` · `/tasks` · `/log` | — | the web UI with that list's window open (the toolbar's "to acquire", "backlog" and "worklog"). Acquire and tasks items can be added, edited, deleted and ticked off there; an acquisition item can be fulfilled by **Upload…** (the upload form, filled in from the item; the upload form can also pick an item itself) or **Link existing source…**. Either ticks it off as `ingested as [[citekey]]`: a link at once, an upload when its OCR job succeeds. The worklog, newest date first, is added to only. Every change needs the API key when one is set |
| `POST` | `/api/query` | `{sql}` | `QueryResult{columns, rows, rowCount, truncated, elapsedMs}`; a write is a 400 carrying Postgres's refusal |
| `GET` | `/api/schema/tables` | — | the live `information_schema` shape (tables, views, columns) |
| `GET` | `/api/agents/models` | — | `ModelsInfo` — each step role's chain, the model its next call would use, every allowance's use per window and when it frees (no call is made) |
| `PUT` · `DELETE` | `/api/agents/models/{role}` | `{models: ["provider:model", …]}` (PUT) | `RoleChain` — the role's chain saved over `agents.roles.<role>` (works with agents off); DELETE goes back to config |
| `PUT` | `/api/agents/allowances` | `{target, limits: [{amount, measure: tokens\|usd, window}], reservePct}` | `AllowanceStatus` — one model's (`provider:model`) or provider's (`provider:*`) allowance, saved over `agents.allowances`; no limit = unlimited |
| `DELETE` | `/api/agents/allowances` | `target` | `{removed}` — config's allowance (if any) applies again |
| `POST` | `/api/agents/generate` | `{runtimes?: [claude-code\|codex\|opencode], out?}` | `GenerateResult{dir, runtimes, files, written, removed, definitions}` — writes the per-runtime agent trees ([design](docs/research-agent-runtimes.md#22-the-generator)) on the SERVER's disk: under the runtimes directory, or `out`. Works with agents off |
| `GET` | `/api/agents/quota` | — | `QuotaInfo{plans[], metered[], fallthrough[]}` — each subscription pool's windows (used, left, floor, reset, reading age and source), hold, verdict; the metered allowances; the roles whose chain may fall from a plan to a paid key |
| `PUT` | `/api/agents/quota/{pool}` | `{hold?, floors?}` | `PoolStatus` — hold or release `claude-plan` / `chatgpt-plan`; save its floors (`""` = config's) |
| `POST` | `/api/agents/quota/refresh` | `{pool?}` | `QuotaRefreshed{pools: [{pool, refreshed, detail, status}]}` — a fresh reading of one pool (or every pool) now, whatever its age: the ChatGPT plan through `codex app-server` (spends nothing), the Claude plan through one minimal `claude -p` turn on `probe-model` (spends a little of the plan; not with `refresh = off`); a pool without a login is not read |
| `PUT` | `/api/agents/quota/fallthrough/{role}` | `{fallthrough}` | `QuotaInfo` — may that role's chain fall from a plan below its floor to a paid key |
| `POST` | `/api/agents/quota/claude` | `{source, rateLimits: [{kind, percentUsed, resetsAt}]}` | `GuardAnswer{allow, reason, resetsAt}` — the Claude quota-guard mod's report; a headless run's mod sends its RUN TOKEN, which this route (and only this route) accepts where writes otherwise need the API key |
| `GET` | `/api/agents/doctor` | `offline` | `DoctorReport` — every provider checked without spending anything: key present, the free `GET …/models` listing (skipped `offline`), chained models listed upstream and priced, the `claude` CLI, its login method and plan (never the account), the loopback rule |
| `POST` | `/api/agents/probe` | `{citekey, model?}` | `ProbeResult` — one live call offered one read tool (`get_source`) with a typed answer, checked against the record |
| `POST` | `/api/agents/runs` | `{pipeline, target, model?, budgetUsd?, budgetTokens?, dryRun?, modelFor?[{role, model}]}` | `RunInfo` — the queued run; refused (400) when agents are off, a metered model is unpriced, a provider has no key, or a `modelFor` role is unknown, given twice or not used by the pipeline. `dryRun`: every read and model step runs, nothing is written to the corpus (`RunInfo.dryRun`; a resumed dry run stays one) |
| `GET` | `/api/agents/runs` · `/api/agents/runs/{id}` | `status`, `limit` | `[RunInfo]` · `RunDetail{run, steps, messages}` — steps are the checkpoints, with tokens and cost per attempt, the configured `model` and the `actualModel` the provider reported, and a dry run's skipped writes (`wouldWrite[{what, payload}]`) |
| `GET` | `/api/agents/compare` | `id` (repeated, ≥ 2; one pipeline, one target) | `Comparison{pipeline, target, runs[], steps[{name, runs[{models, actual, billing, usage, costUsd, seconds, attempts, failures}]}], enrich[], review[], overlaps[], notes[]}` — enrich-source: findings, quote check, places, dated findings, period, summary/notes length, claims only one run made; review-article: verdict, rounds, round 1 per reviewer and kind, shared major findings |
| `POST` | `/api/agents/runs/{id}/{pause\|resume\|cancel}` | `budgetUsd`, `budgetTokens` (resume) | `RunInfo`; 409 when the run is in the wrong state |
| `GET` | `/api/places` | `q`, `asOf`, `limit` | `[PlaceHit]` — gazetteer search by name/label, valid at `asOf` (the **time rule**, below); each row says how much of its period the source states (`dating`: `stated`, `end-only` — start unknown, matched only in `places.sources.open-start-window-years` before the end — or `unknown`) and who brought it in (`importSource`) |
| `GET` | `/api/places/{id}` | `asOf`, `counts` | `PlaceDetail` — geometry as **GeoJSON text**, parents/children as of `asOf` (404 if unknown; an old id of a renamed place answers with the place under its id now); `counts=true` adds `counts{sources, findings, articles, events}` — what `place=<id>` selects (the map's hover card); `hasBoundary` (a polygon — a region or polity without one is drawn as a label, "point only — boundary not yet available"), `descriptions[{text, lang, source}]` (Wikidata's description, English first) |
| `GET` | `/api/places/geo` | `kind`, `asOf`, `limit` | a GeoJSON `FeatureCollection` for the map: places of `kind` with a shape, valid at `asOf` (the time rule); each feature's `properties` carry `id, label, kind, periodStart, periodEnd, dating, hasBoundary` and `basis` — why a view at a time shows it (`dated`, `timeless`, `modern`, `findings`; `findings` is drawn dashed, "dated by its findings") |
| `GET` | `/api/places/{id}/children` | `asOf` | `[PlaceSummary]` — with `asOf`, only the children the time rule shows |
| `GET` | `/api/places/{id}/tree` | `asOf`, `depth` | `[PlaceTreeRow]` — the subtree, depth-tagged (the indented list MCP prints); with `asOf`, the time rule decides each node |

**The time rule** (`asOf` on the place endpoints above; `PlaceTimeBasis`): a view at a time shows what
was there then. A place whose own period overlaps the year is shown (`dated`; an unknown start uses its
stored window); a physical feature — a seed place of kind `feature` or a Natural Earth physical region —
at any time (`timeless`); a present-day place (`properties.modern`: the Natural Earth cities, undated
GeoNames/Wikidata places) only from `places.modern-from` on (`modern`); an undated place with no shape
(a continent, a Natural Earth subregion) as a tree node (`grouping`); any other undated place — a
curated region, an undated Pleiades site, a Natural Earth country — only when a finding or an event
dated within the window is attached to it (`findings`; its card says "dated by its findings"). Without
`asOf` nothing is filtered. The map across all time draws no present-day place, and an undated
GeoNames/Wikidata place is on no time-sliced map.
| `GET` | `/api/places/names` | `place` | `[ManualPlaceName{placeId, label, name, lang}]` — the names added by hand (every place's, or one's) |
| `POST` | `/api/places/{id}/names` | `{"name": …, "lang": …}` | `PlaceNameChanged` — a further name, stored as **manual**: an import replaces a place's other names but never these, so a re-seed keeps them |
| `POST` | `/api/places/{id}/names/remove` | `{"name": …, "lang": …}` | `PlaceNameChanged` — only a name added by hand (a seed name is refused) |
| `POST` | `/api/places` | `PlaceAdd{label, kind, lon, lat, source, id?, names[], lang?, periodStart?, periodEnd?}` | `PlaceSummary` — a point place of this collection's own (`dataset = local`, id `local_<label>` by default); `source` is required; refused when the id is taken |
| `POST` | `/api/places/import` | `kind`, `dataset` (`local` — the default: this collection's own, carried in a bundle — or `seed`) + an **LPF or GeoJSON FeatureCollection** body | upsert; `{places, names, relations, failed}` |
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

## Formatting lint

Articles, summaries and source notes are Markdown written by agents, and the page renders them with
**flexmark: CommonMark plus GFM pipe tables** (`server/MarkdownFlavor` — the same parser for the page
and for the check). The formatting lint parses each text with that parser, after the same `[[link]]`
rewrite the page does, and reports what will not display as written — a run of pipes the parser did
not make a table (no blank line before it, no `|---|` row, a delimiter row of the wrong width), `**`
or `` ` `` it could not pair (a space inside, an odd count, a starred reconstruction like `*Manu/*Yemo`
pairing into italics), a link it could not read (`[text] (url)`, an unclosed `(`, `<url`), a malformed
`[[wikilink]]` (one bracket short, escaped, padded, not a slug, an embed), pasted YAML frontmatter or
`key: value` headers, a ```` ```markdown ```` wrapper, an unclosed fence, indented prose that became
code, `##Heading`, a `---` under a paragraph (a heading, not a rule), list items glued to a paragraph,
raw HTML left open or unknown, double-escaped entities, literal `\n`, footnotes, math and
strikethrough (not rendered), and the suspicious: skipped heading levels, a second `#` title,
duplicated sections, a hand-written `## Sources cited`, bare citekeys and URLs, agent chatter
(`Here is the article…`) and placeholders (`TODO`, `[citation needed]`). Well-formed `[[citekey]]` /
`[[slug]]` links are the corpus's link syntax and never a problem.

Each problem is a `FormatIssue{rule, severity, field, line, column, excerpt, message, fix}`:
`error` = will not render as meant, `warning` = renders, probably not as meant. Where it shows up:

- every article read and write (`get_article`, `put_article`, `patch_article`, `GET/PUT/PATCH
  /api/articles/{slug}`) and every source write (`put_source`, `patch_source`) carries them;
  `strict` (MCP) / `?strict=true` (REST) / `--strict` (CLI) refuses a write whose text has an error;
- `histmersion article lint <slug> [--fix]`, `histmersion lint --source <citekey> [--fix]`,
  `histmersion lint --file x.md [--fix]` (before `article put`), and the `lint` MCP tool's `article` /
  `source` / `text`;
- the corpus rules `article-markdown` and `note-markdown` in `histmersion lint` (one line per problem,
  `- **<slug>** — body L12:3 error table-not-rendered — …`);
- the article and source pages: a folded "This article may not display correctly: N formatting
  problems" above the prose, which expands to the list.

`--fix` applies only repairs that never change a word — a blank line before a table or a list, the
space in `[text] (url)` or `** bold**`, `##Heading`, `\[\[x\]\]` unescaped, `[[x]` completed, a
pasted frontmatter block whose keys are the record's own fields removed, a ```` ```markdown ```` wrapper
or an indentation removed, `<…>` around a bare URL, `&amp;amp;` collapsed — as one patch guarded by the
`updatedAt` just read (the previous body stays an article version).

## Configuration

Everything runtime-tunable is in
[`modules/core/src/main/resources/reference.conf`](modules/core/src/main/resources/reference.conf).
Nothing is hardcoded anywhere else — a test greps `src/main` for literal ports and fails the build if
one reappears. Most keys take an env override (the table below).

**The config file.** Settings you change go in `histmersion.conf`, with the same keys as
`reference.conf` under `histmersion { … }` — only the ones you change:

```hocon
histmersion {
  converter.parallelism = 2
  agents.step.max-tokens = 8192
}
```

It is `$HISTMERSION_CONFIG` when that is set, else `$XDG_CONFIG_HOME/histmersion/histmersion.conf`
(default `~/.config/histmersion/histmersion.conf`) on macOS and Linux alike — with your other
settings, not in the server-owned data directory, which never moves it.

Precedence, highest first: **environment variable > `histmersion.conf` > `reference.conf`**. An env var
beats the file even for a key the file sets — so a setting in the LaunchAgent's `EnvironmentVariables`
(or the Linux service's `Environment=`) wins over the file; remove it there to manage the setting in the
file. Only the environment can move the file (nothing inside it does). The file is read
once, at startup: restart after editing. A missing file is fine; a file that does not parse, or a value
of the wrong type, stops the server (exit 3) with the file and the key in the log; a key that is no
setting (a typo such as `agents.step.max-token`) is logged as a warning and ignored. `histmersion about`
(and the web UI's About window) says which file the running server read and lists ignored keys;
`histmersion config path` prints the location on this machine and checks the file. The CLI reads the
same file (`cli.server-url`, `cli.calibre.*`). The installers write a commented template, with no
settings, when there is no file, and never touch an existing one.

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
| `limits.coverage-sources` | `HISTMERSION_COVERAGE_SOURCES` | `1000` | the most sources of an article's filter its coverage reads (citekey order) to list under the generated Not used; the counts stay exact |
| `ui.tiles` | `HISTMERSION_TILES` | OSM tile URL | map tiles; point at a local tile server to run offline |
| `ui.tiles-max-zoom` | — | `12` | |
| `ui.tiles-tone` | `HISTMERSION_TILES_TONE` | `light` | `dark` when `ui.tiles` is a dark map: the map's place names switch to light text on a dark halo |
| `ui.tree-depth` | `HISTMERSION_TREE_DEPTH` | `2` | default nesting of `/api/aggregate?by=…` and the web UI's "by time" / "by region" tabs (max 3) |
| `ui.tree-samples` | `HISTMERSION_TREE_SAMPLES` | `3` | sample sources listed under a leaf of that tree (0–5) |
| `ui.time.min` / `.max` | — | `-12000` / `2026` | timeline bounds, signed years |
| `ui.time.default-from` / `.default-to` | — | `-3500` / `-500` | initial timeline window |
| `ui.narrow-max-width-px` | `HISTMERSION_UI_NARROW_MAX_WIDTH_PX` | `760` | at or below this viewport width the web UI uses its phone layout: the map first; filters, timeline, layers and tools on demand (buttons, or a swipe in from the edge); the list as a bottom sheet; windows full-screen |
| `ui.narrow-max-height-px` | `HISTMERSION_UI_NARROW_MAX_HEIGHT_PX` | `500` | …and so it does on a touch screen at or below this height (a phone held sideways) |
| `ui.events.local-fade-seconds` | `HISTMERSION_UI_EVENTS_LOCAL_FADE_SECONDS` | `2.5` | map playback: a LOCAL event (a battle) shows while the playhead is inside its span, then fades out over this many seconds of playback (at the speed playing; paused, it holds) |
| `ui.events.regional-fade-seconds` | `HISTMERSION_UI_EVENTS_REGIONAL_FADE_SECONDS` | `1.0` | the same for a REGIONAL event's label and region outline |
| `ui.events.map-limit` | `HISTMERSION_UI_EVENTS_MAP_LIMIT` | `2000` | most events `/api/events/geo` returns for one playback |
| `ui.events.outline-tolerance-deg` | `HISTMERSION_UI_EVENTS_OUTLINE_TOLERANCE_DEG` | `0.02` | a regional event's area outline is simplified to this many degrees |
| `query.timeout-ms` | `HISTMERSION_QUERY_TIMEOUT_MS` | `10000` | `SET LOCAL statement_timeout` for `histmersion query` |
| `query.max-rows` | `HISTMERSION_QUERY_MAX_ROWS` | `500` | row cap; a result that hits it comes back `truncated` |
| `query.max-cell-chars` | `HISTMERSION_QUERY_MAX_CELL_CHARS` | `2000` | one cell is cut here — `select ocr_markdown …` is otherwise megabytes |
| `query.user` | `HISTMERSION_QUERY_USER` | `histmersion_query` | the read-only LOGIN role `histmersion query` runs as (created by migration V011) |
| `query.password` | `HISTMERSION_QUERY_PASS` | (set one) | password the server sets on that role at startup; rotate by restarting |
| `db.pool.max` | `HISTMERSION_DB_POOL_MAX` | `8` | HikariCP pool size (the server is the only DB client) |
| `ui.max-buckets` | `HISTMERSION_MAX_BUCKETS` | `2000` | cap on histogram/tree buckets per request (400 beyond it) |
| `ui.debounce.suggest-ms` · `.reload-ms` · `.preview-ms` | — | `150` · `250` · `60` | quiet periods of the web UI: a keystroke before its suggestions are asked for; a filter change before the lists reload; a hovered suggestion before its preview reloads them |
| `limits.max-page` | `HISTMERSION_MAX_PAGE` | `500` | hard cap on `limit` for every listing endpoint |
| `converter.health-timeout-ms` | — | `2000` | how long `/api/health` waits for the converter probe |
| `converter.health-cache-ms` | — | `30000` | how long a converter probe result is reused |
| `lint.max-items` | `HISTMERSION_LINT_MAX_ITEMS` | `50` | offending rows LISTED per rule (`count` is always the true total) |
| `lint.suggestions` | `HISTMERSION_LINT_SUGGESTIONS` | `3` | closest citekey matches offered per dangling edge target |
| `places.point-tolerance-km` | `HISTMERSION_PLACE_TOLERANCE_KM` | `10` | a POINT place (a Pleiades site has no extent) contains findings within this radius; polygons use exact coverage |
| `places.geocode-placeholder-min` | `HISTMERSION_GEOCODE_PLACEHOLDER_MIN` | `4` | the geocoder ranks a point shared by this many gazetteer places last (a Pleiades grid placeholder) and marks a choice that lands on one approximate |
| `places.geocode-repair-km` | `HISTMERSION_GEOCODE_REPAIR_KM` | `100` | `lint --rule finding-geocode` lists (and with `--fix` moves) only old-geocoder points the current ranking puts farther away than this |
| `places.modern-from` | `HISTMERSION_PLACES_MODERN_FROM` | `1500` | a view at a time (the map at a year, place search and suggestions, children and tree with `asOf`) shows present-day places — the Natural Earth cities, undated GeoNames/Wikidata places — only from this year on |
| `places.search-limit` | — | `20` | default rows from `/api/places?q=` |
| `places.children-limit` | — | `200` | max children listed for one place |
| `places.tree-limit` / `.tree-depth` | — | `500` / `6` | bounds on one hierarchy walk |
| `places.seed-dir` | `HISTMERSION_PLACES_DIR` | `db/places` | **host** directory `histmersion places seed` imports from |
| `places.import-batch` | — | `400` | features per `POST /api/places/import` request (keeps bodies small); also the first-run import's batch |
| `places.sources.open-start-window-years` | `HISTMERSION_GAZETTEER_OPEN_START_WINDOW_YEARS` | `300` | a GeoNames/Wikidata place with an end and no start ("dissolved 1769", inception unknown) is seen by time filters (the map at a year, place search with a time, the geocoder, `finding_places`) only this many years before its end; applied at import (V074 fixed older rows with the default) |
| `places.sources.point-area-kinds` | `HISTMERSION_GAZETTEER_POINT_AREA_KINDS` | `region,polity,people` | a GeoNames/Wikidata record of one of these kinds whose geometry is only a point is not kept as a place of its own (it still joins a place its ids name); `""` keeps every record. `lint --rule place-imported-point-area` lists the ones stored before, `--fix` removes them |
| `places.sources.point-area-fix-batch` / `.point-area-fix-seconds` | — | `2000` / `20` | that `--fix` deletes this many places per transaction, for at most this long per call (the CLI calls again; well inside the 60 s idle timeout) |
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
| `acquire.defaults.title-match-at` | `HISTMERSION_ACQUIRE_TITLE_MATCH_AT` | `0.6` | a successful import closes the open acquisition items that name its DOI; an item whose text resembles its title and first author at least this much (0–1) is only listed as a possible match, never closed |
| `acquire.defaults.*` | — | `25`, `1900`, `100`, `8`, `50`, `3`, `2018` | discover page size / oldest year, followups cap, archive rows, and the assessment thresholds (well-cited at, low-cited at, stale before) |
| `cli.calibre.library-dir` | `HISTMERSION_CALIBRE_LIBRARY_DIR` | `~/Calibre Library` | the host Calibre library `histmersion calibre` reads (read-only) |
| `cli.calibre.calibredb` · `.sqlite` | `HISTMERSION_CALIBRE_CALIBREDB` · `_SQLITE3` | `calibredb` · `sqlite3` | the two readers: `calibredb` first, `sqlite3 -readonly` while the Calibre GUI holds the library |
| `cli.calibre.originals-dir` | `HISTMERSION_CALIBRE_ORIGINALS_DIR` | *(empty)* | host directory of fetched originals as `<citekey>/paper.*`, used by `calibre push`/`sync` |
| `runtime.enabled` | `HISTMERSION_RUNTIME` | `false` | run the bundled Postgres and converter as child processes. Automatic when the executable sits in a bundle (`<home>/bin/` beside `<home>/pg/bin/pg_ctl`) |
| `runtime.home` | `HISTMERSION_HOME` | *(empty)* | bundle root holding `pg/` and `converter/`; empty = the directory above the executable's `bin/` |
| `runtime.data-dir` | `HISTMERSION_DATA_DIR` | *(empty)* | the cluster, logs, backups and uploads; empty = `~/Library/Application Support/Histmersion` (macOS) or `$XDG_DATA_HOME/histmersion` (Linux) |
| `runtime.pg-port` · `.converter-port` | `HISTMERSION_PG_PORT` · `HISTMERSION_CONVERTER_PORT` | `5433` · `8100` | loopback ports of the two child processes (5433, so a system Postgres on 5432 can coexist) |
| `runtime.postgres.shared-buffers` · `.effective-cache-size` · `.work-mem` | `HISTMERSION_PG_SHARED_BUFFERS` · `HISTMERSION_PG_EFFECTIVE_CACHE_SIZE` · `HISTMERSION_PG_WORK_MEM` | `auto` · `auto` · `16MB` | the bundled Postgres' memory, passed as `-c` at each start: a size (`2GB`), `auto` (shared buffers RAM/16 within 128 MB…2 GB; cache size RAM/2) or empty for Postgres' default |
| `agents.enabled` | `HISTMERSION_AGENTS_ENABLED` | `false` | embedded research agents ([design](docs/research-embedded-agents.md)). **Off by default**: every agent call is refused and no run pool starts, so the external Claude Code workflow is untouched |
| `agents.auto-enrich` | `HISTMERSION_AGENTS_AUTO_ENRICH` | `true` | with agents on, every successful import job (DOI, upload, archive.org, Calibre, re-OCR) queues one `enrich-source` run for its source — not when the source already has findings or notes, or an earlier enrich run of it is pending or done. The job's progress line says `enrichment queued: run <id>` or why not (no model can run is a note, never a failed import). An ordinary run: budgets, plan floors and approvals apply |
| `agents.providers.anthropic` · `.openai` · `.local` `.base-url` | `HISTMERSION_AGENTS_ANTHROPIC_URL` · `_OPENAI_URL` · `_LOCAL_URL` | the public APIs · `http://127.0.0.1:11434/v1` | `local` is any OpenAI-compatible server (llama.cpp `llama-server`, LM Studio, vLLM, Ollama — [which one](docs/research-model-providers.md#2-local-models-on-apple-silicon)) |
| `agents.providers.claude-code.command` · `.config-dir` · `.extra-args` | `HISTMERSION_AGENTS_CLAUDE_COMMAND` · `_CLAUDE_CONFIG_DIR` · `_CLAUDE_EXTRA_ARGS` | `claude` · *(empty)* · *(empty)* | the `claude-code:<model>` provider runs **your own** `claude -p` on **your own** Claude login, as the step's agent from the generated plugin (`--setting-sources ""` keeps your settings and hooks out, the plan login stays). It spends your plan's usage: the Claude plan pool pauses runs below its floors. Personal use on your own machine only ([terms](docs/research-model-providers.md#1-claude--p-on-a-claude-subscription)). An absolute `command` when the LaunchAgent's PATH lacks it; a `config-dir` of its own (log in there once) |
| `agents.providers.claude-code.use-api-key` · `.allow-non-loopback` | `HISTMERSION_AGENTS_CLAUDE_USE_API_KEY` · `_CLAUDE_ALLOW_NON_LOOPBACK` | `false` · `false` | `false`: `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN` are removed from the CLI's environment (in `-p` a present key always wins and bills the API). The provider is **refused while the server listens beyond loopback** unless every client of the server is you |
| `agents.providers.claude-code.mcp-url` · `.timeout-seconds` · `.limit-cooldown-minutes` | `HISTMERSION_AGENTS_CLAUDE_MCP_URL` · `_CLAUDE_TIMEOUT` (other: file only) | `public-url` + `/mcp/agent` · `1800` · `60` | where the CLI reaches the read-only agent tools; one call's budget; how long a model is passed over after the plan reports its usage limit without a reset time |
| `agents.providers.claude-code.disable-slash-commands` · `.env { … }` | `HISTMERSION_AGENTS_CLAUDE_DISABLE_SLASH_COMMANDS` · each variable's own name | `true` · twelve `CLAUDE_CODE_DISABLE_*` switches = `"1"` | what every `claude -p` step is launched with: `--disable-slash-commands` (no skill or slash command loaded — a role's skills are already in its generated prompt), and variables set in the CLI's environment. `""` or `null` removes a variable (also one the server inherited); a name not listed may be added; a variable in the server's own environment wins. The defaults switch off what only adds prompt, tools, skills or background work (the Artifact tool, bundled/Claude API/Claude Code skills, CLAUDE.md files, auto memory, git instructions, background tasks, Cron, workflows, the Explore and Plan agents, the advisor) — never thinking, structured outputs, refusals, model fallback or web fetch. `scripts/agents/claude-overhead.sh` measures what a launch sends, offline |
| `agents.providers.codex.command` · `.home` · `.use-api-key` · `.timeout-seconds` · `.app-server` | `HISTMERSION_AGENTS_CODEX_COMMAND` · `_CODEX_HOME` · `_CODEX_USE_API_KEY` · `_CODEX_TIMEOUT` · `_CODEX_APP_SERVER` | `codex` · *(empty)* · `false` · `1800` · `true` | the `codex:<model>` provider: `codex exec --json -p <role>` on the generated Codex home (`<runtimes dir>/codex/home` unless `home`; log in there once with `CODEX_HOME=<dir> codex login`). `OPENAI_API_KEY`/`CODEX_API_KEY` are removed unless `use-api-key`. `app-server`: the ChatGPT plan's windows are read with `codex app-server` → `account/rateLimits/read` (spends nothing; reset credits are never touched). Refused beyond loopback |
| `agents.providers.opencode.command` · `.timeout-seconds` · `.chatgpt-login` | `HISTMERSION_AGENTS_OPENCODE_COMMAND` · `_OPENCODE_TIMEOUT` · `_OPENCODE_CHATGPT_LOGIN` | `opencode` · `1800` · `false` | the `opencode:<provider>/<model>` provider: `opencode run --agent <role> --format json` on the generated config. `chatgpt-login`: its `openai/…` models draw on the ChatGPT plan pool (else they are metered and need prices) |
| `agents.runtimes.dir` · `.generate` · `.claude-mod` · `.interactive-mod` | `HISTMERSION_AGENTS_RUNTIMES_DIR` · `_GENERATE` · `_CLAUDE_MOD` · `_INTERACTIVE_MOD` | *(empty)* · `true` · `true` · `false` | where the per-runtime agent definitions are generated (*empty* = `<data dir>/agents`); written when the server starts with agents on, and by `histmersion agent generate`. `claude-mod`: the quota-guard mod in the headless Claude Code plugin; `interactive-mod`: a reporting mod in the interactive plugin (your own sessions report the plan's windows) |
| `agents.quota.claude-plan.floors` · `.chatgpt-plan.floors` | `HISTMERSION_AGENTS_CLAUDE_PLAN_FLOORS` · `_CHATGPT_PLAN_FLOORS` | `five_hour=25, seven_day=25` · `300m=25, 10080m=20` | the share of each window (Codex windows by length) that must stay LEFT for a run to use the plan; below it the run **pauses** (`plan-quota`) until the reset. `histmersion agent quota floors` saves others |
| `agents.quota.*.max-calls-in-flight` · `agents.quota.fallthrough` | `HISTMERSION_AGENTS_CLAUDE_PLAN_CALLS` · `_CHATGPT_PLAN_CALLS` · `_FALLTHROUGH` | `1` · *(empty)* | LLM calls drawing on one plan at once · the roles whose chain may fall from a plan below its floor to a PAID key (the others pause) |
| `agents.quota.stale-minutes` · `.default-floor` · `.margin` · `.poll-seconds` | — | `10` · `10` · `10` · `60` | a reading older than this is stale (a run goes on only with `margin` points to spare, and a run that wants the pool takes a fresh reading first — at most once per this many minutes per pool) · the floor of a window with none · at most one app-server read (and one login check) per this many seconds |
| `agents.quota.claude-plan.refresh` · `.probe-model` · `.probe-max-tokens` · `.probe-timeout-seconds` | `HISTMERSION_AGENTS_CLAUDE_PLAN_REFRESH` · `_CLAUDE_PLAN_PROBE_MODEL` · — · — | `auto` · `haiku` · `16` · `120` | how the Claude plan takes a fresh reading when a run wants it and its reading is stale or missing (docs/research-plan-quotas.md §8): `auto` (Claude Code has no non-generating read, so the probe), `probe` — one `claude -p` turn on `probe-model` with the prompt "quota", no tools, at most `probe-max-tokens` output tokens — or `off` (only runs read it; a pool paused on a stale reading then waits for the reset) |
| `agents.providers.*.api-key` | `ANTHROPIC_API_KEY` · `OPENAI_API_KEY` · `HISTMERSION_AGENTS_LOCAL_API_KEY` | *(empty)* | read from the environment only; never stored, logged, traced or returned. A hosted provider without a key is refused |
| `agents.providers.*.strict-tools` · `.timeout-seconds` | `HISTMERSION_AGENTS_*_STRICT_TOOLS` · `_TIMEOUT` | `true` for openai, else `false` · `600` (local `1800`) | OpenAI strict function calling; the per-request budget |
| `agents.roles.<role>` | `HISTMERSION_AGENTS_ROLE_PROBE` · `_EXTRACTOR` · `_REVIEWER` · `_COLD_READER` · `_SKEPTIC` · `_FIXER` · `_PLANNER` · `_WRITER` | *(empty)* | a **chain** per step role, `provider:model > provider:model > …` (one model is a chain of one), e.g. `claude-code:sonnet > local:qwen2.5-coder:32b`: each call uses the first model with allowance left. `histmersion agent models set` saves one over config; a run may override it with `--model`. `planner`: plan-goal and the acquisition sweep's ranking; `writer`: compile-article's draft |
| `agents.allowances` | `HISTMERSION_AGENTS_ALLOWANCES` | *(empty)* | per model or provider: `"<target> = <amount> tokens\|usd/<window>, …, reserve <n>%; …"`, e.g. `"claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 20%"` — rolling windows (`5h`, `7d`, `day`, `week`, `month`); a model is passed over once less than its reserve is left. When every model of a role is, the run **pauses** and resumes by itself when a window frees. `histmersion agent allowance <target> --limit …` saves one over config |
| `agents.allowance-check-seconds` · `.doctor-timeout-seconds` | — | `60` · `10` | how often runs paused for an allowance are re-queued once their window freed · `agent doctor`'s per-request timeout |
| `agents.prices` | `HISTMERSION_AGENTS_PRICES` | *(empty)* | USD per million tokens: `"<model>=<in>/<out>[/<cache-read>/<cache-write>]; …"`. A **metered** model (an API key, `local`, opencode off a ChatGPT login, a CLI on `use-api-key`) with **no price is refused** (sttp-ai would count it as free); a free local model is `"<model>=0/0"`. A model on a **plan pool** (`claude-code:*`, `codex:*`, `opencode:openai/*` with `chatgpt-login`) needs none: it costs 0 USD against the budgets, its tokens still count against the run's token budget, and its plan floors govern it (`agent models` says `plan pool: claude-plan`) |
| `agents.budgets.run-usd` · `.run-tokens` · `.daily-usd` · `.monthly-usd` | `HISTMERSION_AGENTS_RUN_USD` · `_RUN_TOKENS` · `_DAILY_USD` · `_MONTHLY_USD` | `2.0` · `400000` · `10.0` · `150.0` | per-run caps (a run may ask for others) and global caps; reaching one **pauses** the run, and `agent resume --budget-usd` raises it |
| `agents.max-concurrent-runs` · `.max-llm-calls-in-flight` | `HISTMERSION_AGENTS_MAX_CONCURRENT_RUNS` · `_MAX_LLM_CALLS` | `4` · `4` | runs executing at once · LLM requests at once across every run |
| `agents.step.*` · `agents.verify.*` · `agents.trace.*` · `agents.enrich.*` | `HISTMERSION_AGENTS_MAX_TOKENS` (others: file only) | see `reference.conf` | tool-loop iterations, output tokens and validation retries per step; quote-match threshold (`0.9`) and year bound; trace truncation; body windows for `enrich-source` |
| `agents.approvals.status-change` · `.edges` | `HISTMERSION_AGENTS_APPROVE_STATUS` · `_EDGES` | `manual` · `manual` | what a run may change without a person: `manual` records an approval (decided in the web UI's approvals window, `histmersion agent approve` / `reject`, or MCP `decide_approval`); `auto` applies at once. Filling an `unassessed` source with `current`/`foundational` never needs one |
| `agents.review.max-rounds` · `.rounds-cap` · `.article-chars` · `.fixer-max-tokens` · `.fixer-turns-per-finding` · `.fixer-turns-cap` | `HISTMERSION_AGENTS_REVIEW_ROUNDS` (others: file only) | `3` · `5` · `60000` · `16000` · `1` · `40` | `review-article`: review passes — a full panel, then a verification pass after each fix (a fixing run gets at least 2), the most a run may ask for, the body sent (a longer one is reviewed cut and never revised), the fixer's output tokens (its answer is a list of edits), and its tool-loop turns: the fixer agent's `max-turns` plus this many per major or factual minor finding, at most the cap |
| `agents.review.recheck-turns` · `.ledger-closed-lines` · `.final-panel` | `HISTMERSION_AGENTS_REVIEW_FINAL_PANEL` (others: file only) | `6` · `60` · `clear` | `review-article` after a fix (docs/research-review-convergence.md): a verification pass's re-check gets this many turns plus one per finding it checks (never more than its agent's `max-turns`); the closed findings of the article's review ledger a prompt shows; and when the run ends with a confirming full panel — `clear` (only when no MAJOR is left open), `always`, `never` |
| `agents.trace.keep-messages-days` · `.prune-every-minutes` | `HISTMERSION_AGENTS_KEEP_MESSAGES_DAYS` | `30` · `60` | traced messages older than this are deleted (`0` = keep all); steps, tokens and cost are never pruned |
| `agents.enrich.upstream` | `HISTMERSION_AGENTS_ENRICH_UPSTREAM` | `true` | `enrich-source` compares the record with OpenAlex/Crossref and runs `refs`/`assess` (network) |
| `agents.enrich.guards.min-placed-share` · `.century-gap-years` · `.pointer-phrases` | `HISTMERSION_AGENTS_ENRICH_MIN_PLACED_SHARE` · `_CENTURY_GAP_YEARS` · `_POINTER_PHRASES` | `0.5` · `500` · `"this date, around this date, the next column, …"` | `enrich-source`'s findings answer is asked again (and kept with a warning on the last attempt) when fewer than this share of the findings that name a place resolve in the gazetteer (`0` = off); when a year below 100 looks like a century stored as a year — the finding mentions a century, or the source's own period starts this many years later, and the text does not state the year with its era (`79 CE`); and when a claim contains one of these comma-separated phrases, pointing at a table instead of standing alone |
| `agents.approvals.article-publish` | `HISTMERSION_AGENTS_APPROVE_PUBLISH` | `manual` | `compile-article`: `manual` makes the run **wait** (`waiting`) until a person approves the draft; `auto` writes at once (still never over an existing slug). A goal's plan always waits for a person |
| `agents.plan.*` · `agents.compile.*` | `HISTMERSION_AGENTS_COMPILE_MAX_SOURCES` · `_COMPILE_REVIEW` (others: file only) | see `reference.conf` | caps on a plan (sub-goals, queries, works); compile-article's sources read (`20`), record excerpt per extraction, claims per source, the writer's output tokens, and whether a review-article run with fix follows (`true`) |
| `agents.scheduler.enabled` · `.tick-seconds` · `.min-interval-minutes` | `HISTMERSION_AGENTS_SCHEDULER` (others: file only) | `true` · `60` · `60` | goal schedules (`histmersion goals schedule`); they fire only while agents are enabled, never more often than the minimum interval, and the daily/monthly caps and `max-concurrent-runs` are checked before each start |
| `agents.prompts-dir` | `HISTMERSION_AGENTS_PROMPTS_DIR` | *(empty)* | a directory overriding the bundled agent definitions file by file, mirroring the bundled `agents/` tree (`article-skeptic.md`, `_shared/review-fixer.md`, `skills/<name>/SKILL.md`, …) — iterate on a prompt without a rebuild (`histmersion agent generate` carries it into the CLI runtimes' trees) |

The installed service reads only its own environment: the LaunchAgent's `EnvironmentVariables`
(macOS) or the unit's `Environment=` lines (Linux). `just install` fills in `HISTMERSION_CORPUS_DIR`,
`HISTMERSION_ACQUIRE_CONTACT_EMAIL` and any `HISTMERSION_AGENTS_*` it finds in its environment; add any
other key there and `just restart`. On macOS a re-install **keeps** every `HISTMERSION_*` entry (and
`ANTHROPIC_API_KEY`/`OPENAI_API_KEY`) of the installed plist, with the installer's environment
overriding it, resolves `claude`/`codex`/`opencode` to absolute paths from its own `PATH` when no
`HISTMERSION_AGENTS_*_COMMAND` names them (a LaunchAgent's `PATH` lacks `/opt/homebrew/bin`), and
prints what it kept, set, changed and dropped, with secret values masked. To drop a kept setting:
`plutil -remove EnvironmentVariables.<NAME> ~/Library/LaunchAgents/com.histmersion.server.plist`
before installing. A `.env` in the
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

## Portable bundles

A third way to carry a collection, built to be **merged** rather than restored
([`docs/research-merge-import.md`](docs/research-merge-import.md)): a `.hmx` bundle is a lossless,
logical export of the database content — sources with their OCR bodies and stored originals,
findings, edges, assessments, aliases, articles with their versions and old slugs, assets, asset
links, attestations, events, goals (with their approved plan, run cap, links and schedules), the
queues, the places and named periods this collection added itself, and the tombstones of what was
deleted — that keeps every row's **identity**. Calibre is not in it: `calibre_id` is a per-machine pointer, and a merged
instance rebuilds its Calibre library from the stored originals with `calibre sync --apply`.

```bash
histmersion bundle instance --label work           # name this collection (every row records the instance that made it)
histmersion bundle export work.hmx                 # everything; deterministic: an unchanged database gives the same bytes
histmersion bundle export delta.hmx --since 2026-10-03T09:12:00.123456Z   # what changed after a previous bundle's asOf
histmersion bundle verify work.hmx                 # format, schema, checksums, revs, blobs — every problem listed
histmersion merge plan work.hmx                    # every match and every issue, in one run (writes nothing)
histmersion merge plan work.hmx --write-template r.yaml   # a resolution file listing every issue
histmersion merge plan work.hmx --resolutions r.yaml --resolve S-3c1e7a2b=keep-left   # what remains
histmersion bundle import work.hmx --dry-run       # what an import would do
histmersion bundle import work.hmx --resolutions r.yaml   # one transaction; refused (nothing written) on any problem or open issue
histmersion merge apply work.hmx --resolutions r.yaml --plan 7c41e9a2   # the reviewed plan, folds included: a backup, then one transaction
histmersion merge list                             # what was merged, from where, with which plan and backup
histmersion merge show 0193f1c4                    # one merge's report, resolutions and remembered decisions
histmersion merge ledger --uid guterbock1997_hittite   # where a record came from; the merged-away uids that redirect to it
```

The same review on a screen: **`/merge`** in the web UI (the toolbar's merge button) uploads a bundle,
shows its plan — the issues grouped, open ones first, with a counter — and decides each in place: a
source pair as a field table (here | result | bundle) with the score's evidence, an article as a
three-way view (here | common version | bundle) with an editor for the merged text, a place or an
event as both geometries on a map and both spans on one axis. Each decision is kept on the server
(`merge_sessions`, so a reload or another browser carries on) and the plan is made again at once;
the decisions download as the resolution file the CLI reads (a merged text goes along in its
`texts:`), and one written by the CLI uploads. *Apply* sits behind the write key and a
confirmation, posts the plan hash just reviewed and follows the apply job — its backup first — to the
report.

- **Identity.** Every row a bundle carries has a `uid` (UUIDv7) and an `origin` (the instance that
  created it); a citekey or slug is only a label — `rename` keeps the uid. A delete, cascades
  included, leaves a tombstone, so a deletion travels too.
- **The file.** A gzip'd tar: `manifest.json` (format and schema version, the exporting instance,
  `since`/`asOf`, a checksum per file, a fingerprint of the gazetteer and periods), one NDJSON file
  per kind (one canonical JSON line per row, with a `rev` = the sha256 of its content), and
  content-addressed `blobs/sha256/…` for OCR bodies, version bodies, asset bytes and originals,
  streamed so the size of a bundle never matters to memory. The gazetteer and the named periods every
  instance seeds from the same files (`histmersion init`, `places seed|fetch`, `periods seed|fetch`:
  `dataset = seed`) are not carried; what a person added (`places import`, `periods import`: `dataset =
  local`, the default) is, and merges like any record — one id with the same geometry and span is one
  place, a different one is a `place-differs` question (keep-left | keep-right) shown on a map; a place
  under another id with the same label and kind, and points within `merge.place-within-km` or shapes
  overlapping by `merge.place-overlap-at-least`, is one too, and the bundle's events follow it. Places
  and periods that existed before V052 count as seeds: re-import a file of your own with `--dataset
  local` to have it travel.
- **Import.** A bundle row is the local row with the same uid (or the one a redirect or the merge
  ledger maps it to), or a new one. Into an empty instance that is a copy (export → import → export
  gives the same bytes but for the manifest's instance); into the instance a bundle came from it
  changes nothing. Between two instances that share rows, the merge ledger remembers what was
  imported, so a row changed only over there is taken and a row changed only here is kept. A row
  deleted here is not brought back.
- **The merge plan.** `merge plan` matches the bundle's other sources against this collection's —
  a strong key (the same DOI, original file or ISBN) decides; else a weighted score over title,
  authors (name forms, order and diacritics folded), year, venue, OCR body (MinHash) and old
  citekeys — and lists **every** issue in one run, each with a stable id: possibly the same work
  (`map:<citekey>` or `both`), one citekey for two works, fields that differ in a pair to be merged
  (DOI, year, status, title, authors, summary, notes, period, OCR body), rows changed on both sides
  or deleted there and changed here, articles that diverged, labels a different record has, old
  citekeys that name a different source. An issue that exists only under one answer to another is
  listed as conditional (`if S-… = map`). Decisions go in a resolution file (`--write-template`
  starts one); an id whose records changed is reported as stale, never re-applied. `bundle import`
  refuses while an issue is open, and carries out what it can (`keep-left`/`keep-right` on a
  conflict, `keep-left` on a taken label, `both`). Left is always this collection, right the bundle.
  The matcher's weights and thresholds are `merge.*`.
- **What a merged pair owns.** Inside a pair of sources that are one, the findings are matched (the
  same claim, time and place fold into one, tags unioned and the more cautious confidence kept; a
  near duplicate is kept beside it unless decided `merge`; the rest are numbered after the
  survivor's last), figures with the same bytes are one (a taken name gets the other collection's
  label), and attestations are re-anchored by their quote, prefix and suffix into the OCR body the
  merge keeps — one that cannot be is a blocking `drop | keep-unanchored` question, under each
  alternative of the body question. Edges are re-pointed through the merge (a self-loop dropped).
  An article both sides changed merges three-way against the newest version both histories hold
  (clean: automatic and queued for review; conflicting: `keep-left | keep-right | both | file:`), or
  fast-forwards when one text is a version of the other; `[[citekey]]` links in the bundle's articles
  follow the merge. A goal, a queue item (`acquire`/`tasks`, same text) or an event dated alike on both
  sides is one — and so are two events under different ids with the same label, dates sharing a year
  and the same place. A goal that is one keeps the links of both and this collection's schedules (a
  difference is reported; a schedule brought in by a merge has no next run until it is saved here),
  and takes the plan approved later — two plans and neither known to be newer is a
  `goal-plan-differs` question. A rename made over there (an article slug, an event id, a goal slug, as
  a citekey) is followed; an old slug, event id, goal slug or place id keeps resolving (`article
  rename`, `events rename`, `goals rename`, `places rename` keep the alias too), and the old labels
  travel — one that names another record here is left out and said.
- **Apply.** `merge apply` plans again and refuses unless nothing is open and the plan is the one
  reviewed (`--plan <hash>`); then takes a backup (`histmersion backup`'s `pg_dump`), and in one
  transaction writes the merge — the bundle's uid of every folded record a redirect to the survivor,
  its citekey an old citekey of it, the ledger, a `/review-article` task per merged article, a `log`
  entry — and plans once more: the report says the merge left 0 changes. Applying the same bundle
  again changes nothing; every decision carried out is remembered, so the next plan of an unchanged
  record does not ask again. The apply runs as a background job (`GET /api/merge/apply/{job}`; the CLI
  follows it), so a large collection's backup does not time the request out. Not an MCP tool:
  applying a merge is a person's call, like a restore.

| key | env | default | meaning |
|---|---|---|---|
| `bundles.dir` | `HISTMERSION_BUNDLES_DIR` | `bundles` (bundle: `<data>/bundles`) | server-side scratch where an export is built before its download |
| `bundles.stale-minutes` | `HISTMERSION_BUNDLES_STALE_MINUTES` | `60` | a finished export — or an uploaded bundle not planned or imported since — older than this is deleted by the next export or upload |
| `merge.auto-merge` | `HISTMERSION_MERGE_AUTO` | `14` | a score at or above this, with no hard flag (two DOIs, years more than one apart), merges without asking |
| `merge.new-at-most` | `HISTMERSION_MERGE_NEW_AT_MOST` | `4` | a score at or below this is a different work; in between, the user decides |
| `merge.minhash-size` | `HISTMERSION_MERGE_MINHASH_SIZE` | `64` | hash functions per OCR-body sketch |
| `merge.finding-near-at-least` | `HISTMERSION_MERGE_FINDING_NEAR_AT_LEAST` | `0.6` | two findings of a merged pair whose claim words overlap this much (Jaccard, periods overlapping) are near-duplicates: kept apart unless the user says `merge` |
| `merge.finding-within-km` | `HISTMERSION_MERGE_FINDING_WITHIN_KM` | `50` | two findings of a merged pair this close are at the same place |
| `merge.place-within-km` | `HISTMERSION_MERGE_PLACE_WITHIN_KM` | `5` | a bundle's place with no id here is the same as a place here with its label and kind when the two points are this close |
| `merge.place-overlap-at-least` | `HISTMERSION_MERGE_PLACE_OVERLAP_AT_LEAST` | `0.8` | …or when the two shapes overlap this much (intersection over union) |
| `merge.apply-wait-seconds` | `HISTMERSION_MERGE_APPLY_WAIT_SECONDS` | `20` | how long `POST /api/merge/apply` waits before it answers `running` with the job to follow |
| `merge.weights.*` | `HISTMERSION_MERGE_W_*` | see `reference.conf` | the score's weight per field comparison (title, authors, year, venue, body, old citekey) |

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
  (`target/sloth/`) when a Sloth CLI jar is found — `SLOTH_JAR` first, else a Sloth checkout built
  with `sbt cli/assembly` next to this checkout (`<repo>/../sloth`), else, when building from a
  `git worktree`, one next to the MAIN worktree (found with `git rev-parse --git-common-dir`; see
  `project/SlothLookup.scala`) — and logs a warning and builds unpatched otherwise. Sloth is
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
