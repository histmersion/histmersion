# Do I need AI?

**No.** Histmersion is a complete research library without any AI language model (an *LLM*, the
kind of model behind chat assistants). The built-in AI agents are **switched off by default**
(`agents.enabled = false`), and with them off no model is ever called.

AI can help in two separate ways, both optional:

1. **Your own AI assistant** (Claude Code, Codex, opencode, any MCP client) uses Histmersion's
   tools while you work with it. See [Using your AI assistant](../ai-assistant.md).
2. **Histmersion's built-in agents** run inside the server on a model you configure, and can work
   through a research area on their own. This page is about what they add.

## What works without any AI

| you can | how |
|---|---|
| bring in papers and books, and read their full text | `histmersion ingest` (DOI, arXiv id or your own file), archive.org public items, Calibre — the text recognition runs on your machine with its own models, not an LLM |
| search the full text, filter by tag, author, place, topic, status, years, map area | the web page, `histmersion search`, `facets`, `passages` |
| see findings, sites, events and pictures on the map, and move through time | the web page; `histmersion geo-when`, `histogram` |
| look up places and named periods | `histmersion places …`, `periods …` |
| import open datasets: gazetteers, periods, event catalogues, pictures | `histmersion init --fetch`, `places fetch/load`, `periods fetch`, `events import`, `media import` |
| follow citations: references, later papers citing a source, retraction checks | `histmersion refs`, `followups`, `assess`, `neighbors`, `graph`, `contradictions` |
| propose which sources may discuss an event | `histmersion events links` / `events candidates` — scored by code, no model |
| keep your Calibre library and the collection in step | `histmersion calibre …` |
| write the summary and findings of a source yourself | the source editor on the web page, `histmersion patch` |
| write articles yourself, with the same checks and generated coverage notes the agents get | [Writing an article yourself](../articles/writing-by-hand.md) |
| check the collection's health | `histmersion lint` |
| keep your to-buy list, task list and work log | `histmersion queue …` |
| export Markdown, take backups, move a collection to another machine | `histmersion export`, `backup`, `bundle …`, `merge …` |

## What needs an AI model

These are the **built-in agents**. They run only when `agents.enabled = true` and a model is set
up for the step's role ([AI providers, budgets and quotas](../how-to/ai-providers.md)).

| pipeline | what it does | model role |
|---|---|---|
| `enrich-source` | writes a source's summary, classification, period, findings with verbatim quotes, and notes; checks the details against OpenAlex and Crossref | `extractor` |
| `review-article` | the review panel: three reviewers read an article, a fixer revises it | `reviewer`, `cold-reader`, `skeptic`, `fixer` |
| `compile-article` | drafts a new article, or a new section, from the sources a filter returns; waits for your approval before it writes | `writer` (and `extractor`) |
| `plan-goal` | proposes sub-goals, searches and works for a research goal; creates nothing before you approve | `planner` |
| `propose-articles` | proposes the articles a goal should have | `planner` |
| `acquisition-sweep` | finds new literature for a goal and ranks what to acquire | `planner` |
| `infer-filter` | proposes the filter (the search) an article is compiled from | `planner` |
| `hint-effect` | turns a note you gave a manager into a proposed action | `planner` |
| process managers and the autopilot | keep the pipelines going over many sources, articles and goals | (the roles above) |

One pipeline uses no model but still runs only while agents are on: `currency-check` (it runs the
retraction check and the citing-paper search over a goal's sources and proposes disagreement links).

See [Compiling and reviewing with agents](../articles/compile-and-review.md) and
[Process managers and the autopilot](../articles/autopilot.md).

## Which models

| kind | providers | you pay |
|---|---|---|
| an **API key** | `anthropic` (Claude models), `openai` | per token, billed to your key; set prices so budgets can count it |
| a **local model** | `local`: any OpenAI-compatible server on your machine (llama.cpp's server, LM Studio, vLLM, Ollama) | nothing; set its price to `0/0` |
| a **command-line tool on your own subscription** | `claude-code` (your `claude` CLI), `codex` (your `codex` CLI), `opencode` | your plan's usage — for your own, personal use on your own machine; read your plan's terms first |
| a **plugin** | any provider a plugin adds | as its provider bills |

An API key is the plain way to run the agents. A command-line tool on a personal subscription is
**refused while the server is reachable from other machines**, so that nobody else can spend your
plan.

## Keeping cost under control

- **Budgets** cap spending per run (default 2.0 USD and 400 000 tokens), per day (10.0 USD) and per
  month (150.0 USD). A run that reaches one pauses; you can raise it and resume. The defaults are
  placeholders, not advice — set your own.
- **Allowances** cap a model or a provider per rolling window (for example "2M tokens per 5 hours,
  keep 20 % in reserve"); when one is used up the next model in the role's chain takes over, or the
  run pauses until the window frees.
- **Plan floors** keep a share of a subscription's window for you: by default a run stops using a
  Claude plan when less than 25 % of the 5-hour or the 7-day window is left. `histmersion agent quota
  hold claude-plan` stops all agent use of a plan at once.
- **Approvals** make the agents ask before they change a status, add a disagreement link, publish an
  article or start a plan ([Approvals](../articles/approvals.md)).

`histmersion agent doctor` checks every provider without spending anything.

!!! warning
    Whatever a model writes can be wrong. Read [Responsible use](../responsible-use.md).
