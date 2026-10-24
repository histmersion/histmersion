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

### The scheduler: what runs next

Every manager only **proposes** work. One **scheduler** in the server decides, a few times a minute,
which of all those waiting items gets to run now — a goal's plan, an article proposal, a search for new
sources, an enrichment, a compile, a review — within what your AI plans and settings allow.

- **What counts as busy.** Only a run that is running or about to run takes a place
  (`agents.max-concurrent-runs`, 4 by default, and each subscription plan's own limit). A run that is
  **waiting for you** — a plan or an article to approve — takes no place, so one unanswered approval
  never stops the rest.
- **Who goes first.** A goal you **pinned**, then a goal with a higher **priority**, then the older goal.
  Inside a goal, finishing comes first: a review before a compile before a new proposal.
- **Nobody waits forever.** The longer an item waits, the higher it climbs; after 72 hours
  (`agents.scheduler.max-wait-hours`) it goes next. Every kind of work gets some turns
  (`agents.scheduler.min-share`), and goals still gathering their sources always keep one place
  (`agents.scheduler.gathering-slots`).

You choose **how** it shares the work:

| strategy | what it does | use it when |
|---|---|---|
| `type-balanced` (the default) | spreads the turns over the kinds of work — reviews, compiles, enrichment, proposals, plans, searches — by weight; inside each kind the best goals first | your AI allowance is not the limit: every goal keeps moving, and no stage of the pipeline stalls behind another |
| `goal-first` | gives the turns to the most important goals, oldest first; a newer goal of the same priority waits until the older ones have nothing to run | the allowance is short: finish a few goals completely rather than start many |

```bash
histmersion agent scheduler                        # the strategy, what is running, the queue and why each item waits
histmersion agent scheduler queue                  # just the queue, in the order it will run
histmersion agent scheduler why steppe-hypothesis  # why one item waits (by its id, or what it works on)
histmersion agent scheduler strategy goal-first    # switch now (`default` = back to the setting)
histmersion agent autopilot focus pin <goal>       # this goal first, always
histmersion agent autopilot focus priority <goal> 5
```

The managers window has a **Scheduler** panel with the same: the strategy buttons, what the last round
started, and the queue — filter it by kind of work, by why it waits, or by name.

### New articles once the current ones are done

A goal does not start new articles while one of its articles is still being written or reviewed. Once
every article of the goal is published and has passed its review, the autopilot proposes the goal's
**next** article (at most once a day, `agents.autopilot.next-article-hours`). The proposal is approved
the way your approval settings say ([Approvals](approvals.md)).

### No new goal plans while too many goals are unfinished

A goal plan opens more work: sub-goals, searches, articles. While more than 10 goals with an approved
plan are not reviewed yet (`agents.autopilot.max-unfinished-goals`), no new plan is offered: no goal
gets its plan drafted, and plans already waiting are not approved automatically. `histmersion agent
digest` and `histmersion agent autopilot status` say so, with the count. You can still approve a plan
yourself; once enough goals are reviewed, plans are offered again.

### Goals with no sources start gathering at once

A goal that has no source yet does not wait for its plan to be approved: the autopilot searches the
scholarly indexes for it (by its saved queries, or its title — no AI is used) and fetches what is
**legally open access**, within the daily ingest limits. Two goals a few minutes at a time, each at
most once a day (`agents.scheduler.seed-goals-per-tick`, `.seed-every-hours`).

### What waits for you: one digest a day

Instead of a hundred single approvals, look at them together:

```bash
histmersion agent digest                                   # everything waiting for you, by kind
histmersion agent decide-all approve --kind goal-plan --dry-run
histmersion agent decide-all approve --kind goal-plan      # all of them at once (or reject; --subject <goal>)
```

Once a day (after 07:00 UTC, `agents.autopilot.digest-hour`) the same summary goes to the worklog. In
the approvals window the digest bar at the top picks one kind, and the bulk buttons decide them all.

When goal plans are approved automatically (`agents.approvals.goal-plan = auto`), a plan is approved
whatever goal it belongs to, as long as its unfinished sub-goals plus the new ones stay within 5
(`agents.autopilot.max-open-sub-goals`). The daily limits of automatic approvals can grow with what is
waiting (`agents.approvals.auto.goal-plans-backlog-share`) up to a ceiling.

### What is stuck, and why

```bash
histmersion agent stalls              # goals with no progress for 3 days: the one reason each, and what to do
histmersion agent stalls --days 7
```

Each stalled goal gets one reason — waiting for you, paused, quota, a budget, in the queue, an item that
failed, nothing found, idle — and the command that moves it on. The scheduler panel's **stalls** button
shows the same.

### Before an update: drain

To update the server, do not turn the autopilot off — that pauses every manager. **Drain** instead:
nothing new starts, and what is running finishes.

```bash
histmersion agent autopilot drain     # nothing new starts
histmersion agent autopilot           # says DRAINING …, then DRAINED: safe to stop the server
#   … update and restart …
histmersion agent autopilot undrain   # back to work (turning the autopilot on also lifts it)
```

Whatever waits — for you, for a quota — is saved and goes on after the restart. The managers window
has the same **drain** button.

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
- each subscription plan's runs at once (its `max-calls-in-flight`) and its floors;
- each manager's `daily-usd`, and a `max-runs` you set on a manager yourself;
- the ingest caps: 5 a day per watcher, 20 a day for all watchers together while the autopilot is on;
- the scheduler decides **what** runs next within them (it holds no place for work that waits for you).

!!! warning "More automation, more to check"
    The autopilot can write many articles while you are away. Every one of them needs the reading
    described in [Responsible use](../responsible-use.md) before you rely on it.
