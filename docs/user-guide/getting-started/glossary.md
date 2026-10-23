# Glossary

**acquisition list** (`acquire` queue) — the works you still have to buy, borrow or find. Histmersion
adds a work here when no legal open-access copy exists.

**agent** — here: a step of work done by an AI language model inside the Histmersion server (a
reviewer, a writer, a planner). Off by default. Not to be confused with *your* AI assistant.

**approval** — a change an agent proposed that waits for a person to accept or reject it.

**article** — a page written across sources, stored in the collection. Kinds: `concept`, `myth`,
`culture`, `region`, `timeline`, `report`, `index`.

**attestation** — a link from a finding, event, place or citation link to the exact passage of a
source that supports it (a quote, a page).

**autopilot** — a switch that lets the process managers move every research goal from *planned* to
*reviewed* on their own, inside every cap and budget.

**BCE / negative years** — Histmersion writes years before the common era as negative numbers:
1200 BCE is `-1200`.

**bundle** — (1) the installed program folder; (2) a `.hmx` file that carries a whole collection to
another machine, to be merged there.

**Calibre** — a free e-book library manager. Histmersion can import books from your Calibre library
and push its originals into it.

**catalogue** (event catalogue) — a public list of events, such as the Smithsonian's list of
volcanic eruptions.

**citekey** — a source's short, unique name: `cline2014`, `west2007_poetry`.

**collection** (corpus) — everything stored in your Histmersion: sources, findings, links, articles,
events, places.

**coverage** — for an article: which of the sources its filter finds are cited, which are not, and
why not.

**DOI** — Digital Object Identifier, a paper's permanent identifier, like `10.1234/abcd`.

**edge** — a typed link between two sources: `references`, `challenges`, `refutes`, `updates`,
`superseded_by`.

**enrichment** — the structure added to a source: summary, findings, topics, regions, period, tags,
status, notes.

**filter** — a saved search (words, topics, tags, statuses, places, years) that says which sources an
article is about.

**finding** — one claim of a source, with a place (and a point on the map) and a range of years, and
a confidence: `high`, `medium`, `low` or `contested`.

**focus** — the autopilot's rule that only a few research goals (8 by default) are worked on by the AI at a
time, the ones closest to done first; the others wait their turn. See [the autopilot](../articles/autopilot.md#the-focus-a-few-goals-at-a-time-finished).

**gazetteer** — the list of places: names, shapes, the years each existed, and what each lies inside.

**historicity** — for an event: how well it is known to have happened — `attested`, `inferred`, `contested`, `legendary` or `mythic`.

**ingest** — bringing a source in: details, the file, text recognition, references, a retraction check.

**licence** — the terms under which a picture or dataset may be reused (public domain, CC0, CC BY,
CC BY-SA, …).

**lint** — automatic checks for problems: a link to nothing, a finding without a date, a table that
will not display.

**LLM** — large language model: the kind of AI model behind chat assistants.

**MCP** — Model Context Protocol: the standard way an AI assistant calls a program's tools.
Histmersion offers its tools at `http://localhost:8000/mcp`.

**OCR** — optical character recognition: turning a scanned page into text. Histmersion does it on
your machine, once per document.

**open access** — a work its publisher or author allows anyone to read for free.

**period** — a named span of years (for example "Late Minoan IB") as one authority defines it for a
region; from PeriodO.

**PeriodO** — a public gazetteer of period definitions (CC0).

**Pleiades** — a public gazetteer of ancient places (CC BY).

**plugin** — a separate package that adds something to Histmersion: a source of papers, a picture
collection, an event catalogue, a password vault, an AI provider.

**process manager** — a long-running helper inside the server that keeps one kind of agent work
going (reviewing articles, enriching sources, seeding or watching a research goal).

**secret reference** — a way to name a password without writing it down: `env:NAME`,
`keepassxc:Entry#password`, `1password:op://…`, `bitwarden:item`.

**status** — how a source stands today: `foundational`, `current`, `contested`, `superseded`,
`debunked`, `retracted`, `unassessed`.

**watcher** — the process manager of one research goal that looks for new literature and for
changes to what the goal relies on, on a schedule.
