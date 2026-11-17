# Using your AI assistant

You may already work with an AI assistant: Claude Code, Codex, opencode, Claude Desktop or another
program that speaks **MCP** (Model Context Protocol, the standard way an assistant calls a program's
tools). Such an assistant can use Histmersion **directly**: search your collection, read sources,
record findings, write articles — answering from *your* sources, with citations, instead of from
memory.

This is different from Histmersion's **built-in agents** ([Compiling and reviewing with
agents](articles/compile-and-review.md)):

| | your own assistant | the built-in agents |
|---|---|---|
| where it runs | in your assistant program, in a conversation with you | inside the Histmersion server, in the background |
| who steers it | you, turn by turn | pipelines, managers and the autopilot |
| what it can reach | every MCP tool (73), or the `histmersion` command | only the read-only tools its step allows; the pipeline writes the checked result |
| needs `agents.enabled` | no | yes |
| pays for the model | your assistant's own account | the provider you configure for Histmersion |

!!! warning "The same rules apply — and your assistant can change data"
    Everything in [Responsible use](responsible-use.md) holds: a model's summaries, findings and
    articles can be wrong, and you check every claim against the source. In addition, **an assistant
    connected to Histmersion can write to your collection**: change a source, add a finding, replace
    an article. Review what it writes (an article's history: `histmersion article versions`), take
    a backup before large changes (`histmersion backup`), and ask it to use the **read-only** tools —
    `search`, `facets`, `get_source`, `passages`, `query` — when you only want to explore.

## 1. Connect over MCP

Histmersion's MCP endpoint is `http://localhost:8000/mcp` (your server's public URL plus `/mcp`).
It speaks MCP over HTTP; every tool answers in Markdown.

### Claude Code

`just install` writes a `.mcp.json` for the Histmersion checkout. In any other project folder,
create one:

```json
{
  "mcpServers": {
    "histmersion": {
      "type": "http",
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

or add it from the terminal:

```bash
claude mcp add --transport http histmersion http://localhost:8000/mcp
```

Better still, install the Histmersion **plugin** (section 3), which adds the MCP server and the
skills together.

### Codex and opencode

A running server writes ready-made configurations for both into its data folder
(`<data>/agents/`, see [Installing](getting-started/install.md#what-goes-where)) when the agents are
on, or on demand with `histmersion agent generate`:

- **Codex:** a plugin under `<data>/agents/codex/plugin/` — add it with `codex plugin marketplace
  add <data>/agents/codex/plugin`.
- **opencode:** a configuration with the MCP server and the skills under
  `<data>/agents/opencode/interactive/` — point `OPENCODE_CONFIG_DIR` there, or add its `skills`
  path to your own configuration.

### Claude Desktop and other MCP clients

Add a *remote* (HTTP) MCP server with the address `http://localhost:8000/mcp`. How you add one
differs between programs; see your client's documentation.

### When the server asks for a key

On your own computer the assistant needs no key. A server it reaches over a network that the server
does not trust ([Who can reach your server](how-to/access.md)) answers only to its API key
(`auth.api-key`): every request — reads too — must send `Authorization: Bearer <key>`. Clients that
support headers take it in their server entry — in a `.mcp.json`, for example:

```json
"headers": { "Authorization": "Bearer ${HISTMERSION_API_KEY}" }
```

Keep the key in an environment variable, never in a file you share.

## 2. What the MCP tools cover

The server offers **73 tools**. The full list, with when to use each, is in
[MCP tools](reference/mcp-tools.md); the server's own `tools/list` is the authority.

| group | tools (examples) |
|---|---|
| **reading** | `search`, `facets`, `get_source`, `passages`, `geo_when`, `histogram`, `neighbors`, `graph`, `contradictions`, `periods`, `places`, `list_articles`, `get_article`, `article_coverage`, `schema`, `query` |
| **writing** | `patch_source`, `put_source`, `patch_article`, `put_article`, `check_edits`, `rename_source`, `add_place`, `place_names`, `place_override` |
| **getting sources** | `discover`, `ingest_doi`, `ingest`, `job_status`, `archive`, `refs`, `assess`, `followups` |
| **events, evidence, research memory** | `events`, `patch_event`, `event_links`, `attestations`, `put_attestation`, `goals`, `put_goal`, `patch_goal`, `queue`, `agent_state` |
| **operations** | `backup`, `backups`, `export_source`, `export_article`, `lint`, `vacuum`, `config`, `merge_plan`, `merges` |
| **built-in agents** (refused unless the server enables them) | `start_run`, `run_status`, `list_approvals`, `decide_approval`, `manager`, `agent_models`, `set_agent_models`, `goal_links`, `goal_schedule` |

Some things are deliberately **not** tools, because they are a person's decision: restoring a
backup, deleting a source, applying a merge.

### Good practice for an assistant

Give your assistant these rules (the bundled skills already contain them):

1. **The database is the source of truth.** Read sources with `get_source` and `passages`; do not
   search folders of exported Markdown, and do not answer from memory.
2. **Never infer a date from prose.** Take years from the records (a finding's `periodStart` and
   `periodEnd`, a source's `year`). Years are signed integers: 1200 BCE is `-1200`.
3. **Write with `patch_*`, not `put_*`.** `patch_source` and `patch_article` change only what they are
   given; `put_source` and `put_article` **replace** the whole record — for creating one only.
   Extend an article with `patch_article`'s `append`.
4. **Ask `facets` before guessing a tag, topic or place.** An unknown value silently matches nothing.
5. **An empty `neighbors` answer is not agreement.** Most sources have no disagreement links yet.
6. **Cite `[[citekey]]` in everything it writes**, and record a finding with its place and years.
7. **Use `query` for questions the filters cannot express.** It is read-only by construction.
8. **Take a `backup` before a bulk change.**

## 3. Skills and the Claude Code plugin

Histmersion ships **19 skills** — written instructions that teach an assistant its workflows — and
four review agents, as a Claude Code plugin in the repository's `plugin/` folder.

```bash
# from the project you want to use it in
claude plugin marketplace add /absolute/path/to/histmersion     # or a path starting with ./
claude plugin install histmersion@histmersion                   # plugin@marketplace
claude plugin list                                              # histmersion@histmersion, enabled
```

Restart Claude Code (or start a new session) so the MCP server and the skills load. `--scope project`
records the plugin in the project's checked-in settings; the default scope is your user.

The five core skills:

| skill | for |
|---|---|
| `histmersion-query` | answering a research question: `facets` → `search` → `get_source` → `neighbors` → `followups` |
| `histmersion-ingest` | bringing sources in and enriching them with `patch_source` |
| `histmersion-compile` | writing an article by the writing rules and filing it |
| `histmersion-currency` | keeping statuses honest: retractions, critiques, superseded work |
| `histmersion-dev` | changing Histmersion itself |

The other fourteen are research workflows built on them (`trailhead`, `ingest-paper`, `report`,
`review-article`, `lint-wiki`, …) — see [Skills](reference/skills.md). The `review-article` skill
runs the same three-reviewer panel and fixer the server uses, as sub-agents of your own session.

A running server also writes this plugin, with its MCP connection, as a local marketplace under
`<data>/agents/claude-code/` (marketplace `histmersion-local`), and the Codex and opencode renderings
described above. `histmersion agent generate` rewrites them all.

## 4. Or let it use the command line

An assistant that can run shell commands can use `histmersion …` instead of MCP. The command is a
**pure HTTP client** of the server: it holds no database password, so it can do nothing the server
does not allow. Every command prints the same Markdown as the matching MCP tool, and most take
`--json` for exact parsing.

The most useful for an assistant:

```bash
histmersion search "<words>" --tag … --place … --from … --to …   # the corpus, with the findings that matched
histmersion facets tags|authors|places|topics|status              # the vocabulary
histmersion get <citekey> [--section toc] [--full]                # one source
histmersion passages <citekey> "<phrase>"                         # a quotation inside one source
histmersion neighbors <citekey>                                   # its citation links
histmersion article coverage <slug>                               # what an article cites and leaves out
histmersion query "select …"                                      # read-only SQL
histmersion patch <citekey> --add-finding '{…}'                   # a change, only what is passed
histmersion queue tasks add "…"                                   # a note for later, instead of a file
```

[Command line](reference/cli.md) lists every command.
