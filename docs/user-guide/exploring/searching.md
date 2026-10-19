# Searching and filtering

The web page, the `histmersion` command and the MCP tools share **one filter**. What you type in the
page's filter bar, the flags of `histmersion search` and the parameters of the `search` tool mean the
same thing.

## Words

Free text uses web-search syntax:

| you type | finds sources whose text has |
|---|---|
| `bronze collapse` | both words (every bare word is required) |
| `"sea peoples"` | that exact phrase |
| `hittite -egypt` | "hittite" but not "egypt" |
| `ugarit or alalakh` | either word |

The words are looked for inside one passage of about 4 000 characters, so `bronze collapse` finds
passages that mention both, not a book that says "bronze" on page 3 and "collapse" on page 300.

```bash
histmersion search '"sea peoples"' --limit 30
```

## Filters

| filter | CLI flag | matches |
|---|---|---|
| tag | `--tag` (repeatable) | **every** tag given must be on the source or one of its findings |
| topic | `--topic` (repeatable) | **every** topic given must be on the source |
| author | `--author` (repeatable) | **any** one: part of an author's name, any case |
| place | `--place` (repeatable) | **any** one: a place **id** matches everything inside its shape and below it in the hierarchy; a plain name matches text |
| status | `--status` (repeatable) | **any** of `foundational`, `current`, `contested`, `superseded`, `debunked`, `retracted`, `unassessed` |
| publication year | `--year-from`, `--year-to` | when the source was **published** |
| the period it is about | `--from`, `--to` | the **years the source or its findings are about**, signed (1200 BCE is `-1200`) |
| map area | `--bbox minLon,minLat,maxLon,maxLat` | findings inside the box (a west edge east of the east edge crosses the date line) |
| title | `--title` | part of the title |
| order | `--sort` | `relevance`, `cited`, `period`, `year` or `title` |
| paging | `--limit`, `--offset` | at most 500 per page; the list's footer gives the next offset |

Two kinds of year are easy to mix up. `--year-from 2010` means "published in 2010 or later".
`--from -1500 --to -1200` means "about the years 1500 to 1200 BCE".

```bash
histmersion search --place anatolia --from -1500 --to -1200
histmersion search hittite --status contested --status superseded
histmersion search --author cline --sort year
```

## Know the vocabulary first: facets

An unknown tag matches nothing, silently. Before guessing, list what exists:

```bash
histmersion facets tags                      # every tag, with how many sources carry it
histmersion facets places --prefix ana       # places starting with "ana"
histmersion facets status --tag steppe       # statuses, inside another filter
```

`facets` works for `tags`, `authors`, `places`, `topics`, `status` and `provenance` (how a source was
acquired), always within the current filter. On the web page the filter bar suggests the same
values as you type.

## Counts by time and by region

```bash
histmersion histogram "chariot"                      # how many sources per stretch of time
histmersion search chariot --group time,region       # a nested tree of counts instead of a list
histmersion search chariot --group region --as-of -1400   # regions as they were in 1400 BCE
```

A source is counted in **every** group it overlaps, so the groups do not add up to the total.

## Findings by place and time

`search` returns sources. To get the individual findings — what the map draws — use `geo-when`:

```bash
histmersion geo-when --region Levant --from -1250 --to -1100
histmersion geo-when --bbox 25,35,40,42 --from -1400 --to -1100
```

## For questions the filters cannot express

`histmersion query` runs **one read-only SQL statement** against the database (a write is refused
by the database itself). `histmersion schema` lists the tables and columns.

```bash
histmersion schema
histmersion query "select status, count(*) from sources group by status"
```

It has a time limit and a row cap, and long cells are cut. It is the safe way for you — or an AI
assistant — to explore without changing anything.

## The same from an AI assistant

The MCP tools take the same parameters by their JSON names: `query`, `tags`, `authors`, `places`,
`topics`, `status`, `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`,
`sort`, `limit`, `offset`, `title`. See [MCP tools](../reference/mcp-tools.md).

!!! note "Nothing found is not an answer"
    An empty result means your collection has nothing matching — not that nothing exists. See
    [Responsible use](../responsible-use.md#5-absence-is-not-evidence).
