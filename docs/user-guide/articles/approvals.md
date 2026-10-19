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
