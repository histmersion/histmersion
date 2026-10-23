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

<figure markdown="span">
  ![The agent managers window: the autopilot off, with a turn on button, and the approval modes; the goal The Late Bronze Age collapse, seeding, with its paused seeding manager and watcher; then four managers, all paused — review, enrich, seed and watch — each with its pending items, runs in flight, the day's spending and its last event](../assets/screenshots/managers.png){ loading=lazy }
  <figcaption>The managers window with the autopilot off: every manager paused, nothing spent; the last event of
  each says what it did.</figcaption>
</figure>

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

### The focus: a few goals at a time, finished

With many goals, model work spread over all of them finishes none. So the autopilot keeps only a few
goals **active** at a time — 8 by default (`agents.autopilot.max-active-goals`). The plans, article
proposals, compiles, reviews and acquisition sweeps of the active goals run; those of every other
goal **wait**, held with the reason `focus`. Nothing is cancelled, and a run already going finishes.

Ingestion is not part of this: the legal open-access backlog keeps draining for every goal within the
ingest caps, and the currency checks (no model) keep running for all. Enriching new sources goes on
for the whole library, the active goals' sources first (three of theirs to one of any other).

Which goals are active is decided **finish-first**, in this order:

1. a goal you **pinned** (always active);
2. the goal closest to done: articles written but not yet through review, then approved articles still
   to be written, then enough sources for an article, then the rest;
3. **depth-first**: a sibling of an active goal, then a goal in the same tree, before a goal elsewhere;
   a parent becomes active again only to write its own synthesis once its sub-goals are done;
4. a **priority** you gave it (higher first);
5. more sources, then the oldest.

An active goal keeps its place until it is `reviewed`. It gives up its place earlier when it cannot
use it: everything left waits for **you** (a plan only a person approves), its last item was stopped
(a task says why), or it has nothing to do until its weekly schedule or new sources. The next goal in
line takes the place at once. A goal still gathering sources also gives its place to one that has
articles to write or review.

```bash
histmersion agent autopilot focus                  # the active goals (why, what each waits for, pace), the queue, the rest
histmersion agent autopilot focus pin <goal>       # keep this goal active now
histmersion agent autopilot focus unpin <goal>
histmersion agent autopilot focus priority <goal> 5
histmersion agent autopilot focus max 4            # at most 4 active goals (0 = no limit; `default` = the setting)
```

The managers window shows the same: the active goals with why each was chosen, what it is doing or
waiting for, and how fast it moves; the next goals in line; and how many cannot use a place now and why.
An item held for the focus cannot be released by hand: pin its goal instead.

When goal plans are approved automatically (`agents.approvals.goal-plan = auto`), a new plan is
approved only for an **active** goal, and only while its unfinished sub-goals plus the new ones stay
within 5 (`agents.autopilot.max-open-sub-goals`). Otherwise it waits for you.

**What it never does:** it never lifts a cap, a budget or a plan floor. With the shipped settings
(`manual`) every plan and every article still waits for your approval; the autopilot decides them by
code only where you set the approval mode to `checked` or `auto`
([Approvals](approvals.md)). It never resumes a manager **you** paused, and never overwrites a
setting you chose.

To audit what it decided, list the approvals it decided — in the approvals window pick the `all` state
and the **decided by: autopilot** chip, or run
`histmersion agent approvals --state all --decided-by autopilot` (each note says why). The runs its
managers started are `histmersion agent runs --origin manager`, or one manager's with
`--manager seed:<goal>`.

## The limits that hold everything

- `agents.max-concurrent-runs` (4) — runs at once, across every manager;
- the budgets — per run, per day, per month ([AI providers, budgets and quotas](../how-to/ai-providers.md));
- each manager's `max-runs` and `daily-usd`;
- the plan floors of a subscription;
- the ingest caps: 5 a day per watcher, 20 a day for all watchers together while the autopilot is on;
- the focus: at most 8 goals active for model work at a time (it decides **what** runs; the limits
  above decide how much).

!!! warning "More automation, more to check"
    The autopilot can write many articles while you are away. Every one of them needs the reading
    described in [Responsible use](../responsible-use.md) before you rely on it.
