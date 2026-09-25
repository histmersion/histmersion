# Histmersion — Claude Code plugin

Installs the **19 Histmersion skills** and the MCP server connection into a workspace, so an agent
working in (for example) the research wiki queries the corpus, ingests papers, compiles articles and
runs adversarial reviews — all through one plugin install.

| component | what |
|---|---|
| MCP server `histmersion` | **29 tools**, all returning Markdown — listed below |

### Skills — core

| skill | one line |
|---|---|
| `histmersion-query` | answering a research question: `facets` → `search` → `get_source` → `neighbors` → `followups` |
| `histmersion-ingest` | getting sources in (`ingest_doi`, `ingest`, `seed`, Calibre, archive.org) and enriching them with `patch_source` |
| `histmersion-compile` | the writing rules — structure, citation, contradiction-hunting, banned filler — and filing the result with `put_article`/`patch_article` |
| `histmersion-currency` | keeping statuses honest: retraction refresh, `assess`, `followups`, `contested`/`superseded` transitions |
| `histmersion-dev` | build/test/native-image workflow, for changing Histmersion itself |

### Skills — workflows

| skill | one line |
|---|---|
| `trailhead` | start a new research topic from Wikipedia or a search query — harvest primary literature and build an ingest queue |
| `ingest-paper` | ingest one paper by DOI/arXiv/title — fetch, OCR, enrich with findings, topics, regions, period |
| `import-archive` | acquire a book from the Internet Archive (public items only) |
| `import-calibre` | import a purchased book from the local Calibre library |
| `import-file` | import a manually-downloaded file (an unauthorised book mirror, author site, interlibrary loan) |
| `find-followups` | walk the citation graph forward — classify citing papers as confirms/challenges/refutes/supersedes |
| `assess-paper` | retraction/correction/impact check before ingesting or periodically |
| `update-topic` | refresh an existing topic by fetching only the delta since the last expansion |
| `report` | answer a research question and file it as a `report` article with web UI deep links |
| `review-article` | three-agent adversarial review panel + fixer — the gate that makes an article "done" |
| `lint-wiki` | corpus health checks — dangling edges, missing enrichment, contradictions, orphans |
| `bookkeeping` | the three Histmersion queues (`acquire`/`tasks`/`log`) — never create tracker files |
| `sync-calibre` | push every fetched source into Calibre so nothing is missing from the canonical store |
| `enrich-calibre` | audit and fix Calibre library metadata — ISBNs, publishers, authors, tags |

The skills are **symlinks** to `../../skills/` in this repo — one canonical copy, shared by Claude
Code, OpenCode and this plugin. Edit `skills/<name>/SKILL.md` at the repo root, never the symlink.

## The 29 MCP tools

Generated from the server's own `tools/list` (`POST <public-url>/mcp`,
`{"method":"tools/list"}`), which is the only authority on what exists. Regenerate after any tool
change:

```bash
curl -s -X POST "$HISTMERSION_PUBLIC_URL/mcp" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | python3 -c 'import sys,json; [print("-", t["name"]) for t in json.load(sys.stdin)["result"]["tools"]]'
```

**Read** — answering questions

| tool | one line |
|---|---|
| `ping` | liveness + schema version |
| `search` | search the corpus: free text plus tags/authors/places/topics/status/years/deep-time/bbox; `groupBy` returns a time × region tree of counts instead of a list |
| `facets` | the vocabulary of one dimension (`tags`\|`authors`\|`places`\|`topics`\|`status`) inside the current filter, with distinct-source counts |
| `get_source` | one source: frontmatter, summary, findings, optionally the full body |
| `geo_when` | findings at a place (region name or bbox) and/or a deep-time range |
| `histogram` | sources or findings per adaptive time bucket — table + bar chart |
| `neighbors` | the typed edges to and from a source |
| `places` | the gazetteer: a name → a place id, its geometry, validity, parents and subtree |
| `list_articles` | list/search the 38 compiled articles |
| `get_article` | one article: body, backlinks, and the sources it cites with title · year · status |
| `query` | one read-only SQL statement against Postgres/PostGIS |
| `lint` | health-check the corpus against 13 rules, grouped by rule |

**Write** — getting sources and articles in

| tool | one line |
|---|---|
| `put_source` | create or **replace** a whole source under one citekey |
| `patch_source` | **the enrichment tool** — send only the parts that change; `ifUpdatedAt` guards |
| `rename_source` | give a source a new citekey; links, findings and tables follow, the old key stays an alias |
| `put_article` | create or **replace** a whole article under one slug |
| `patch_article` | extend (`append`) or correct an article; `ifUpdatedAt` guards |
| `queue` | the three bookkeeping lists: `acquire`, `tasks`, `log` |

**Acquisition** — bringing literature in

| tool | one line |
|---|---|
| `discover` | literature **not** in the corpus yet: OpenAlex search, DOI metadata, Wikipedia-reference harvest |
| `ingest_doi` | by identifier: metadata → legal OA PDF (Unpaywall → CORE → arXiv) → OCR job → reference edges → assessment |
| `ingest` | OCR a PDF/EPUB into a source, keeping its figures; returns a job id |
| `job_status` | where an OCR job got to (`queued\|running\|done\|failed`) |
| `assets` | the figures stored with a source and the URLs they are served at |
| `archive` | Internet Archive: search, inspect, import **public** texts only |
| `refs` | the reference list a paper deposited with Crossref, resolved against the corpus |
| `assess` | retraction / correction / impact verdict, from Retraction Watch + Crossref notices + metrics |
| `followups` | the citation graph forward: citing papers with intents and a proposed edge kind |

**Export and operations**

| tool | one line |
|---|---|
| `export_source` | one source as the Markdown note it would be on disk |
| `export_article` | one article as the Markdown note it would be at `wiki/<kind>/<slug>.md` |
| `backup` | a server-side `pg_dump`, returning file name, size and SHA-256 |

The CLI is the twin of all of it — `histmersion <command>`, same handlers, same Markdown. Every command
and every tool name used in the skills is verified by `./tools/check_skills.sh`.

## Prerequisites

The Histmersion server must be running, and `plugin/.mcp.json` must point at it. Both are handled by the
installer, which writes `plugin/.mcp.json` from the configured public URL:

```bash
cd /path/to/histmersion
./install.sh
```

Check it: `curl -fsS "$HISTMERSION_PUBLIC_URL/api/health"` (or `just health`).

## Install (verified on Claude Code, this machine)

Histmersion ships its own **local marketplace** (`.claude-plugin/marketplace.json` at the repo root).

```bash
# from the workspace you want to use it in — e.g. ~/Documents/research
claude plugin marketplace add /path/to/histmersion
claude plugin install histmersion@histmersion
```

Then restart Claude Code (or start a new session) so the MCP server and skills load.

Notes from verifying these commands:

- The marketplace source **must** be an absolute path or start with `./`. A bare `.` is rejected with
  *"Invalid marketplace source format"*.
- `claude plugin install` takes `plugin@marketplace`; both are named `histmersion`, hence `histmersion@histmersion`.
- Scope defaults to `user` (all workspaces). Use `--scope project` to declare it in the workspace's
  checked-in settings, or `--scope local` for machine-local settings that are not committed. The same
  flag is needed to uninstall: a plugin installed with `--scope local` needs
  `claude plugin uninstall histmersion@histmersion --scope local`.
- Removing the marketplace (`claude plugin marketplace remove histmersion`) also removes plugins installed
  from it.

## Verify

```bash
claude plugin list                  # histmersion@histmersion, enabled
claude plugin details Histmersion       # 19 skills, 1 MCP server
claude plugin validate /path/to/histmersion/plugin
```

`claude plugin validate` reports one warning — that the skill entries are symlinks and were not
followed. That is expected and harmless: a real session *does* follow them, which
`claude plugin details` proves by listing all five skills. Validate the targets separately:

```bash
claude plugin validate /path/to/histmersion/skills          # passes, including --strict
```

Because of that warning, `claude plugin validate /path/to/histmersion/plugin --strict` **fails** (strict
treats warnings as errors). Validate `skills/` with `--strict` instead; the plugin manifest and the
marketplace manifest both pass strict on their own.

## Using it in the research workspace

Installing the plugin does **not** modify anything in the research workspace's own files — it only
adds a marketplace/plugin entry to the settings scope you chose. All 19 skills ship with the plugin;
the workspace no longer needs its own copies in `.claude/skills/`. Remove any leftover `histmersion-*` or
workflow skills from the workspace after confirming the plugin is installed and showing the expected
skill count (`claude plugin details histmersion`).

## OpenCode

OpenCode does not use this plugin directory. It reads the same skills through the
`.opencode/skill → ../skills` symlink in the Histmersion repo (OpenCode 1.18.23 globs
`{skill,skills}/**/SKILL.md`, so either directory name is discovered). Configure the MCP server in
`~/.config/opencode/opencode.json` using the same URL as `plugin/.mcp.json`.
