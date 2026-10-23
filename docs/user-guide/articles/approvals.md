# Approvals

The built-in agents **propose**; a person **decides**. By default every change that matters waits
for you as an **approval**, with the evidence the agent gave and the quotes that code checked.

## What waits for you

| approval | proposed by | setting (default `manual`) |
|---|---|---|
| a source's **status** changes (say, to `contested`) | `enrich-source`, `currency-check` | `agents.approvals.status-change` |
| a **disagreement link** between two sources | `enrich-source`, `currency-check` | `agents.approvals.edges` |
| a **goal's plan** (sub-goals, searches, works) | `plan-goal` | `agents.approvals.goal-plan` |
| an **article plan** (which articles a goal gets) | `propose-articles` | `agents.approvals.article-plan` |
| an **article to publish** | `compile-article` (the run waits) | `agents.approvals.article-publish` |
| an article's **filter** | `infer-filter` | `agents.approvals.article-filter` |
| an action proposed from your **hint** to a manager | `hint-effect` | — |

Giving a status to a source that has none (`unassessed` → `current` or `foundational`) never needs
an approval.

## Deciding

On the web page: the **approvals** window. From the command line:

```bash
histmersion agent approvals                    # the open ones
histmersion agent approve <id> --note "checked against p. 212"
histmersion agent reject <id> --note "the citing paper agrees, it does not challenge"
```

### Finding the ones you want

Approvals pile up by kind — dozens of goal plans next to a handful of disagreement links — so the list
can be narrowed, in the window and on the command line alike:

- **state**: the buttons along the top — `open` (the default), `approved`, `rejected`, `failed`,
  `all` — each with how many there are;
- **kind**: one chip per kind with its count, for example `goal-plan 64 · article-publish 30 · edge 3`.
  Pick one or several; the counts of the others stay, so you can see what each would add;
- **decided by** (for decided ones): `person`, `autopilot` (decided by the autopilot's checks) or
  `auto` (applied the moment it was proposed, under an `auto` setting);
- **subject**: a few letters of a goal, an article, a manager or a source's citekey.

The filters stay in the page's address, so a reload or a shared link shows the same list. **clear
filters** puts everything back.

```bash
histmersion agent approvals --kind goal-plan --kind article-publish
histmersion agent approvals --state all --decided-by autopilot --subject bronze-age
histmersion agent approvals --kind edge --json            # the raw list, for a script
```

**Deciding several at once.** In the window, tick the open approvals you have read (or **select all
shown**, after filtering), then **approve selected** or **reject selected**. The page asks you to
confirm, then decides them one after another, each exactly as its own button would — with the same
checks, so one decided meanwhile by someone else is refused and the rest go on. On the command line,
give several ids: `histmersion agent approve 41 42 43 --note "checked"`.

<figure markdown="span">
  ![The approvals window with every state shown: an open proposal that molloy2022_there challenges kaniewski2013_environmental, with the evidence, the quoted sentence, a note field and approve and reject buttons; below it an approved proposal that manning2020_beyond challenges the same paper, with its quote, the person's note and the result, edge added](../assets/screenshots/approvals.png){ loading=lazy }
  <figcaption>Two proposed disagreement links: one open, with the agent's evidence and the quote code found in
  the record; one approved, with the note and what applying it did.</figcaption>
</figure>

An approved source change is applied through the ordinary `patch`. An approved plan or article lets
the run that was waiting go on. A rejected one stays on record.

## Letting code decide (only if you want)

| setting | `manual` (default) | other values |
|---|---|---|
| `status-change`, `edges`, `article-filter` | a person decides | `auto`: applied at once |
| `goal-plan`, `article-plan` | a person decides | `auto` or `checked`: the managers' supervisor decides by fixed checks (every slug still free, every filter finds enough sources; sub-goals only for a goal less deep than `auto.max-goal-depth`; daily caps) |
| `article-publish` | the run waits for a person | `auto`: written at once, unchecked (still never over an existing slug); `checked`: decided by fixed checks — the run recorded a filter that found at least 3 sources, the self-check found no problem, it cites a source, the slug is free; at most 10 a day |

Decisions made by checks are noted `autopilot: approved — why` or `autopilot: rejected — why`, and
logged. A manager's own `auto-approve` setting wins for its runs.

These modes exist for the [autopilot](autopilot.md). Checks are narrower than your judgement: they
confirm that a plan or an article is well-formed and cited, not that it is right.

One more thing writes a status without an approval: `histmersion assess <citekey> --apply` (and the
currency check, which runs it) records a **retraction** at once.
