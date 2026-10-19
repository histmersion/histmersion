# First steps

## 1. Check that it runs

```bash
histmersion health      # the database and its schema version
histmersion about       # version, health, the job queue, the last backup, how much is stored
```

If the command says it cannot reach Histmersion, see [Troubleshooting](../reference/troubleshooting.md).

## 2. Open the web page

Open <http://localhost:8000> in your browser. On a fresh install the map shows places from the
gazetteer once the first-run import has finished, and the timeline offers period presets. There are
no sources yet. [The web page](../exploring/web-ui.md) explains every part of it.

## 3. Bring in a first source

By DOI (a paper's permanent identifier) — Histmersion looks for a legal open-access copy, reads it,
and records its references:

```bash
histmersion ingest 10.1234/example-doi       # use the DOI of a paper you want
histmersion jobs                             # follow the text-recognition job
```

Or from a file you already have (a PDF or EPUB you bought, borrowed or downloaded legally):

```bash
histmersion ingest ~/Downloads/some-book.pdf --citekey author2020_short
```

A **citekey** is the source's short name, by convention the first author, the year and a word:
`cline2014`, `west2007_poetry`. Pick it once; `histmersion rename` changes it later if needed.

More: [Open-access papers](../how-to/open-access-papers.md) and [Books and Calibre](../how-to/books-and-calibre.md).

## 4. Read it

```bash
histmersion get author2020_short                # summary, findings, notes
histmersion get author2020_short --section toc  # its table of contents
histmersion passages author2020_short "Hittite" # every passage that says "Hittite"
```

On the web page, search for it in the sources list and click it.

## 5. Add what it says (enrichment)

A new source has text but no summary and no findings yet. The source editor on the web page
changes its details, status, topics, regions, tags, period and summary; `histmersion patch` does
all of that and adds findings too:

```bash
histmersion patch author2020_short --summary "One paragraph on what the book argues."
histmersion patch author2020_short --period=-1600,-1100 --regions Anatolia
histmersion patch author2020_short --add-finding \
  '{"claim":"…what the source says…","region":"Anatolia","lat":39.9,"lon":32.8,"periodStart":-1400,"periodEnd":-1200,"confidence":"medium"}'
```

A finding's `confidence` is one of `high`, `medium`, `low` or `contested`.

Each `patch` changes only what you pass. An AI assistant or the built-in agents can do this for you —
see [Do I need AI?](ai-optional.md).

## 6. The command line, in one minute

The `histmersion` command talks to the server over HTTP; it has no database password of its own.
Every command prints Markdown (readable as plain text); most take `--json` for scripts.

```bash
histmersion --help                         # every command
histmersion search --help                  # one command's options
histmersion search "sea peoples" --limit 30
histmersion search --place anatolia --from -1500 --to -1200
histmersion facets tags                    # which tags exist, with counts
```

`--server <url>` (or `HISTMERSION_SERVER_URL`) points it at another server. The full list:
[Command line](../reference/cli.md).

## 7. Settings

```bash
histmersion config path       # where your settings file is
histmersion config reload     # apply an edited settings file without a restart
```

[Configuration](../reference/configuration.md) lists every setting.

## 8. Connect your AI assistant (optional)

If you use Claude Code, Codex, opencode or another assistant that speaks MCP, point it at
`http://localhost:8000/mcp`. [Using your AI assistant](../ai-assistant.md) shows how.
