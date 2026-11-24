# Who created a record

Every record in a collection names its **creator**: who made it, how they reached the server, and
when. It covers sources, findings, citation links, articles, pictures, goals, agent runs, approvals,
events and places. A person, an AI agent and an import each leave a different trace, so you can tell a
finding you wrote from one a model proposed.

## Reading it

A record's Markdown (`histmersion get`, `get_source`, `get_article`, …) ends its heading block with
one line:

```text
Created by the agent enrich (anthropic claude-sonnet) in workflow enrich-source of in-tree via the server itself at 2026-11-24T10:00:00Z
```

On the web page the same line sits under the record's title in the reading pane, on a place's or an
event's card, under an approval, on a goal's page and in a run's details. A finding and a link in
`neighbors` carry it too. In JSON a view has `creator` (see below) and `createdAt` (ISO-8601, UTC;
a source's is the day it was added).

Your notes, bookmarks and read marks also record who made them, but they never show it: they are
yours alone.

## What a creator says

| `creator.kind` | who | the line reads |
|---|---|---|
| `manual` | a person | "by a person via the web page" (or the command line, the REST API) |
| `automated` | a workflow, through its plugin; an AI agent when a model's answer made it | "by the agent *definition* (*provider* *model*) in workflow *w* of *plugin*", or "by code" for a step without a model |
| `unattributed` | a caller that named no one | "by an unnamed caller via the REST API" (or the MCP) |
| `unknown` | made before creators were recorded, or by an import of a bundle that did not carry one | "by someone not recorded" |

Two details of `automated`:

- **An agent's write names the model whose answer produced it.** A run with a drafting step and a
  reviewing step credits the draft to the drafter's model and the review's patch to the reviewer's.
  A write no single answer produced (bookkeeping, a resumed step) is the run's: it names the run's
  agent definition and no model.
- **Imports name their plugin.** Events from an event catalogue, gazetteer places and pictures from a
  media collection are `automated`, with the catalogue's, the gazetteer source's or the collection's
  plugin and the import's workflow (`event-import`, `gazetteer-import`, `media-import`).

An approval keeps the creator of the run that proposed it. Approving it applies the change as that
run's work: you decided it, but the run made it.

## Naming yourself when you write

- **The web page** sends `via = web`; what you create there is yours.
- **The command line** sends `via = cli`. An automated caller names itself once, before the command:
  `histmersion --by-plugin p --by-workflow w --by-agent a --by-provider x --by-model m <command>`
  (a workflow's code: the first two only), or with `cli.agent.*` in the configuration
  ([Configuration](../reference/configuration.md)).
- **The REST API** takes `createdBy {plugin, workflow, agentDefinition?, provider?, model?}` and
  `via` in each write body ([REST API](../reference/rest-api.md)); an upload takes them as query
  parameters.
- **The MCP tools** take `createdBy` ([MCP tools](../reference/mcp-tools.md)); a write without it is
  an unnamed MCP caller's.

The plugin and workflow go together, and the agent's definition, provider and model are all three or
none. A partial name is refused with a 400.

## Moving a collection

A `.hmx` bundle (format 2) carries each record's creator, and an import writes it, so a record merged
from another machine still names who made it there. A record's `rev` leaves the creator out, so
two copies with the same content match whoever made them. A bundle of format 1 still imports: its
records come in as `unknown`. See [Backups, exports and moving a collection](../how-to/backup-and-bundles.md).
