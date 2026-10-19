# Process managers and the autopilot

Running one pipeline at a time by hand works for a few articles. For a whole subject, Histmersion has
**process managers**: long-running helpers inside the server, each keeping one kind of work going,
one item after another, within its caps. They run only while the agents are on.

## The four kinds

| manager | id | keeps going |
|---|---|---|
| **review** | `review` | `review-article` with fix on one article after another until no MAJOR finding is left; stops an article that makes no progress and files a task for it |
| **enrich** | `enrich` | `enrich-source` for new and unenriched sources (every successful import is handed to it while `agents.auto-enrich` is on) |
| **seed** | `seed:<goal>` | a goal's plan, its proposed articles, their compiles, and a review after each |
| **watch** | `watch:<goal>` | a goal's acquisition sweep and currency check, on a schedule (weekly by default); with auto-ingest on, it ingests the **legal open-access** works it finds, within a daily cap |

Managers are **created paused**. Nothing starts until you resume one.

```bash
histmersion agent managers                                 # every manager, its state and last event
histmersion agent manager new seed --goal bronze-age-collapse
histmersion agent manager seed:bronze-age-collapse resume
histmersion agent manager seed:bronze-age-collapse status
histmersion agent manager review enqueue review steppe-hypothesis   # put one article first in line
histmersion agent manager review pause --runs              # stop starting runs, and pause the running ones
```

A watcher's auto-ingest is opt-in: `agent manager new watch --goal <g> --config auto-ingest=on`. It
uses the legal acquisition chain only. A find past the day's cap waits in the watcher's **ingest
backlog** and is ingested on a later day; only a work with **no legal copy** goes to your
acquisition list.

### Steering a manager

| you want | command |
|---|---|
| leave a note its fixer and skeptic read | `agent manager <id> hint "Keep the Aegean and Anatolian evidence apart"` |
| hold an item until a date, or until a source arrives | `agent manager <id> hold <target> --until 2026-12-01` / `--until-source <doi>` |
| skip, reopen, re-prioritise an item | `agent manager <id> skip <target>`, `… reopen <target>`, `… priority <target> <n>` |
| change when it fires (UTC) | `agent manager <id> cron @weekly` |
| replace its settings (none given: back to the defaults) | `agent manager <id> config max-runs=1 daily-usd=2` |

The web page's **agent managers** window shows the same, with buttons.

## The autopilot

The autopilot is one switch that lets the managers carry every research goal from `planned` to
`reviewed` by themselves.

```bash
histmersion agent autopilot            # status: each goal's stage, managers, sources, articles, approvals, pace
histmersion agent autopilot on
histmersion agent autopilot off        # pauses the seeding managers and watchers it runs
```

While it is on, it:

- gives every goal not yet reviewed an active seeding manager and watcher (a reviewed goal keeps a
  monthly watcher), and spreads their schedules over the hours and days;
- plans a goal that has no plan yet, and gives a plan's sub-goals their own managers;
- moves each goal's status forward (`seeding`, `compiled`, `reviewed`) when the work is done;
- looks for articles whose filter now finds new sources, and extends them;
- takes a backup and runs the safe lint repairs every 24 hours.

**What it never does:** it never lifts a cap, a budget or a plan floor. With the shipped settings
(`manual`) every plan and every article still waits for your approval; the autopilot decides them by
code only where you set the approval mode to `checked` or `auto`
([Approvals](approvals.md)). It never resumes a manager **you** paused, and never overwrites a
setting you chose.

## The limits that hold everything

- `agents.max-concurrent-runs` (4) — runs at once, across every manager;
- the budgets — per run, per day, per month ([AI providers, budgets and quotas](../how-to/ai-providers.md));
- each manager's `max-runs` and `daily-usd`;
- the plan floors of a subscription;
- the ingest caps: 5 a day per watcher, 20 a day for all watchers together while the autopilot is on.

!!! warning "More automation, more to check"
    The autopilot can write many articles while you are away. Every one of them needs the reading
    described in [Responsible use](../responsible-use.md) before you rely on it.
