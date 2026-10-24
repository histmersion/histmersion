# Advancing the research goals without babysitting the managers (#3762, 2026-10-24)

Analysis only — nothing here was changed on the live server. Every figure comes from read-only calls
against it on 2026-10-24 between 01:30 and 02:00 UTC: `histmersion goals list`, `histmersion agent runs`,
`histmersion agent quota`, `histmersion agent models` and read-only `histmersion query` SELECTs over
`agent_managers`, `manager_items`, `manager_events`, `approvals`, `jobs`, `goal_focus`, `goal_links` and
`agent_autopilot` (the queries are at the end).

## Where the goals are

156 goals: 117 `seeding`, 17 `compiled`, 22 `reviewed`. 53 seeding goals have no linked source at all, and 42 of
those wait for their plan to be approved. Managers: 156 watchers, 151 seeders, review and enrich, all active.
No agent run failed in the last 14 days; one is paused. Model allowance is not the limit: the ChatGPT plan, which
every role's chain uses first, shows **0 %** of its weekly window used, and the runs-at-once cap (4) is mostly idle
(one run was in flight, a paused one).

Work done per day (agent runs finished): review-article 105 on 10-17, then 6–28 a day; compile-article 21, 6, 53,
1, 0 on 10-19…10-23 — the compiled-article pipeline has stopped.

## Where they stall, biggest first

### 1. Runs waiting for a person hold a manager's slots — the review manager is "at capacity" with none running

The review manager's cap is `maxRuns: 2`. Its two "runs in flight" are two **article-extend** runs in state
`waiting` (for an approval) since 2026-10-22; no review is running. Behind them: 11 review items pending and 17
held. The same holds for compiling: 9 article-compile items are `active` with their runs `waiting` for an
`article-publish` approval. A run that waits for a person costs nothing, but it counts against the cap like a
running one, so one unanswered approval stops a whole manager.

### 2. The focus and the plan approvals wait for each other

With the autopilot on, at most 8 goals are active (`agents.autopilot.max-active-goals`); everything model-using
of the other goals is held (128 held items: 75 propose-articles, 16 acquisition sweeps, 16 compiles, 14 reviews,
3 extends — all "focus: waits until goal … is one of the active goals"). The focus ranks goals by stage,
`reviewing` first (`GoalFocus.Stage`), and all 8 active goals are "reviewing: 1 of 1 article still to pass review"
— exactly the reviews stalled in 1. Meanwhile a goal **plan** is approved by the autopilot only for an active goal
(`AutoApprovals`: "the focus: decided by code only for an active goal"), and a goal whose next step waits for an
approval does not count as runnable, so it is not chosen. Result: 64 `goal-plan` approvals open since 2026-10-21
(54 of them for depth-2 goals), their 64 plan-goal runs `waiting`, and no path by which code ever decides them.
The only plan decisions since were the autopilot's 5 a day (its cap) and a person's batch of 53 on 10-21.

### 3. The daily approval caps are below the backlog

`goal-plans-per-day = 5` against 64 open plans (13 days at that rate, once 2 is fixed); `publish-per-day = 10`
against 11 open publishes (some wait on `publish-min-sources = 3`); `articles-per-goal-per-day = 6`. They were
set for a handful of goals; there are 156.

### 4. The managers are toggled by hand, in bulk

In 7 days: 3 882 "paused: the autopilot was turned off by cli" and 3 882 "resumed by the autopilot" manager
events — the autopilot switched off and on about 13 times, each time pausing ~300 managers — plus 82 "paused by a
person" and 84 "resumed by a person". Each pause stops crons and starts, so the system restarts cold each time;
this is the babysitting the task names.

### 5. Seeding goals with no sources cannot start without their plan

53 seeding goals have no source; 42 of them wait for the plan (2). Their watcher's acquisition sweep is held by the
focus too, so nothing reaches them: no query has run, no legal open-access paper was ingested.

### 6. A model call on every acquisition sweep

Of the last 200 acquisition-sweep runs, 198 called the ranking model and 78 (39 %) picked nothing; goals swept twice
re-ranked the same results (1.22 M input tokens in 30 days). Addressed by #3761 (deterministic candidates, no run
when nothing is new) — committed on this branch, not deployed.

### 7. Smaller

- 7 article reviews stopped by their convergence rules (no MAJOR progress over 2 runs, a reopen limit, omission
  churn): they wait for a person by design.
- 69 `oa-ingest` items pending since 10-21 (the watchers' daily ingest allowance) and 121 stopped because no legal
  open-access copy exists (sent to the acquisition list) — working as intended.
- The Claude plan's reading is from 2026-10-17 ("unknown until a run reads it"); harmless while every chain uses
  the ChatGPT plan first, but a fallback would start blind.

## Proposal, ranked by what it unblocks for its effort

| # | change | unblocks | effort |
|---|---|---|---|
| 1 | **A run waiting for an approval frees its manager slot.** Count only `queued`/`running` runs against `maxRuns`; an item whose run waits is shown as waiting (with the approval's id) and resumes when the approval is decided. | reviews and compiles at once (11 pending + the 17 held behind them); the review rate back from ~10/day toward the 100+/day of 10-17 | small: the in-flight count in `ManagerSupervisor` |
| 2 | **Break the focus/plan cycle.** Decide goal plans by policy whether or not the goal is active (deciding costs no model; the focus still holds what the plan creates), or let a goal whose only blocker is a policy-decidable approval count as runnable; and keep 1–2 of the 8 focus slots for `gathering` goals so seeding goals advance while others review. | the 64 plans → the 42 seeding goals without sources start | small–medium: `AutoApprovals` + `GoalFocus.select` |
| 3 | **Scale the daily caps with the backlog** (e.g. a cap per active goal, or `max(cap, backlog / 3)`), and when something must wait for a person, ONE digest a day — the open approvals grouped by kind with a single approve-all-of-kind action — instead of 100 single items. The person already decides in batches (53 plans on 10-21). | the plan and publish queues drain in days, not weeks | small (config + a `list_approvals` grouping) |
| 4 | **Replace the bulk on/off toggle** by a drain: "finish what is in flight, start nothing new for N hours" (or per-goal pause), and record who toggled and why. Show one **stall report** — every held or waiting item grouped by reason, each with the action that unblocks it (`histmersion agent stalls`, the Managers window) — so nobody has to toggle managers to find out what is stuck. | removes the restarts (~13 a week); a person sees in one place what needs them | medium |
| 5 | **Seed before the plan:** a goal waiting for its plan still runs the deterministic candidate search (#3761, no model) on its title, and its watcher's legal open-access ingest within the daily allowance. | the 53 sourceless goals gather sources while their plans wait | small, on top of #3761 |
| 6 | Deploy #3761 (deterministic candidates, sweep gate). | the 39 % of sweep model calls that picked nothing; repeated re-ranking | done on this branch |
| 7 | Refresh the Claude plan's reading on a schedule (it spends nothing via the app server for codex; a haiku turn for claude), so a fallback never starts blind. | robustness | small |

1 and 2 together are the main lever: today one waiting approval can stop a manager, and the focus fills its slots
with goals whose next step is that stopped manager — the system then advances only when a person approves in bulk.

## Queries used (all read-only)

```sql
SELECT kind, state, pause_runs, count(*) FROM agent_managers GROUP BY 1,2,3;
SELECT kind, status, count(*) FROM manager_items GROUP BY 1,2;
SELECT CASE WHEN hold ? 'focus' THEN 'focus' WHEN coalesce(hold->>'reason','') LIKE 'approval%' THEN 'approval' END, kind, count(*)
  FROM manager_items WHERE status = 'held' GROUP BY 1,2;
SELECT kind, count(*), min(created_at)::date FROM approvals WHERE state = 'open' GROUP BY 1;
SELECT i.kind, coalesce(j.status, '(no run)'), count(*) FROM manager_items i LEFT JOIN jobs j ON j.id = i.run_id
  WHERE i.status = 'active' GROUP BY 1,2;
SELECT created_at::date, pipeline, count(*) FROM jobs WHERE kind = 'agent' AND status = 'done'
  AND created_at > now() - interval '7 days' GROUP BY 1,2;
SELECT left(text, 110), count(*) FROM manager_events WHERE kind IN ('autopilot', 'paused')
  AND at > now() - interval '7 days' GROUP BY 1 ORDER BY 2 DESC;
SELECT g.status, EXISTS (SELECT 1 FROM goal_links l WHERE l.goal_slug = g.slug AND l.kind = 'source'),
  g.plan_approved_at IS NOT NULL, count(*) FROM goals g GROUP BY 1,2,3;
SELECT f.slug, g.status, left(f.why, 70) FROM goal_focus f JOIN goals g ON g.slug = f.slug WHERE f.active;
SELECT config FROM agent_managers WHERE id = 'review';
```

## As built (2026-10-24, branch `goals`)

Proposals 1–5 and #3763, under one **central scheduler** (#3933). Proposal 6 (#3761) was already on the
branch; proposal 7 (refreshing the Claude plan's reading on a schedule) is not done.

### The central scheduler (#3933; proposal 1)

The managers no longer start their own runs: every managers' tick, each active manager's due pending
items are CANDIDATES of one decision (`SchedulerPolicy.run`, pure, in `core/jvm`; `SchedulerService` reads
the facts and keeps the last pass for the explanations; `ManagerSupervisor.dispatchAll` starts each
admitted item through the old `dispatch`). It replaces the per-manager slot caps — a `maxRuns` a person
saved on a manager still caps it — and the focus's slots: the focus holds nothing any more, it ranks the
goals, and its pins and priorities are the goals' priorities here.

- **Resources.** `agents.max-concurrent-runs` against every run queued or running (a person's too); each
  plan pool's `max-calls-in-flight × agents.scheduler.pool-overcommit` against the runs drawing on it; a
  chain below its floor or out of allowance, the budgets and a manager's daily cap block an item outright.
  Only a run that runs or is queued — or paused to resume by itself (#1803) — occupies a place: a run
  **waiting for a person** never does (`ManagerRepo.running`), which is proposal 1.
- **Priority.** The goal's (`goal_focus.priority`, a pin +1000), the job type's
  (`agents.scheduler.type-priorities`: review 9 … currency 1, finish-first), and **aging**: one level per
  `aging-hours` (24) waited. Library work (no goal) goes after every goal of the same priority.
- **Anti-starvation**, before the strategy is asked: an item that waited `max-wait-hours` (72) goes next,
  the longest first; a job type with work that had no start among the last ⌈1/`min-share`⌉ (20) starts
  goes next; goals still gathering sources keep `gathering-slots` (1) places (proposal 2's guaranteed share).
- **Strategies** (`SchedulerPolicy.Strategy`: `next(pool, state, settings)` and `behindWhy`; a third is one
  more object in `Strategies.all`): `type-balanced` — stride scheduling over the job types by
  `type-weights`, inside a type the best goal first; `goal-first` — the best goal (effective priority, then
  the oldest) takes what it can use, then the next. `agents.scheduler.strategy`, switched live with
  `histmersion agent scheduler strategy <name|default>` (V100 keeps a person's choice).
- **Explained.** Every waiting item has a code (capacity, pool, manager-cap, blocked, busy, drain), a
  sentence, the items it waits behind and its place: `histmersion agent scheduler [status|queue|why
  <item>]`, the MCP `scheduler` tool, `GET /api/agents/scheduler`, `/why/{item}`, and the Managers
  window's Scheduler panel (the queue filtered by type, reason and text; a start opens its manager's runs
  in the runs window, the approvals button the approvals window). With no pass yet it shows a dry pass.

**The default is `type-balanced`.** The figures above say the allowance is not what limits the goals (the
ChatGPT plan at 0 % of its week, the runs-at-once cap mostly idle); what stalled them was one stage holding
the others — reviews behind waiting extends, compiles behind publishes, 117 seeding goals behind a
depth-first focus of 8. Breadth keeps every job type moving at no cost in quota, while the anti-starvation
rules still finish what is closest to done first inside each type. `goal-first` is the one to switch to
when the allowance becomes the limit (a fallback onto the Claude plan, a week near its floor): finishing
fewer goals completely is then worth more than advancing all.

### Proposal 2 — goal plans by policy

`AutoApprovals` decides a goal plan by its checks whatever the focus; only the sub-goals it would open stay
bounded (`AutopilotPolicy.goalPlanSubGoals`, `max-open-sub-goals`). The daily caps are spent in the
scheduler's goal order. With `goal-plan = manual` the plan's run waits for a person holding no place, and
the goal's proposal is kept back (blocked, holding nothing).

### Proposal 3 — caps scaled to the backlog, one digest

`AutopilotPolicy.scaledCap`: `max(cap, ⌈open × share⌉)`, at most a ceiling —
`agents.approvals.auto.{goal-plans,publish}-backlog-share` / `-ceiling` and
`agents.autopilot.ingest-backlog-share` / `-ingest-ceiling` (shares 0 by default: fixed caps). The digest
(`ApprovalDigest`): the open approvals by kind (count, oldest, who decides, subjects, ids) and the items
held for a person — `histmersion agent digest`, `list_approvals digest:true`, `GET
/api/agents/approvals/digest`, the Approvals window's digest bar — and one worklog line a UTC day after
`agents.autopilot.digest-hour` (V101). A group is decided at once: `histmersion agent decide-all
<approve|reject> --kind/--subject/--id [--dry-run]`, `decide_approval` without `id`, `POST
/api/agents/approvals/decide` — each through the single decision.

### Proposal 4 — drain and the stall report

`histmersion agent autopilot drain` (REST `POST /api/agents/autopilot/drain`, the MCP `manager` tool's
`drain`, a button): the scheduler admits nothing, the tick decides no approval by code and drains no
ingest backlog; the first tick with no run queued or running records it DRAINED (V102, a worklog line).
`undrain`, or turning the autopilot on, lifts it; `off` is unchanged. **For a deploy:** drain; wait until
`histmersion agent autopilot` says DRAINED (or `GET /api/agents/autopilot` has `drain.drained = true`); stop,
install and start the server; `undrain`. The drain survives the restart, so the new server starts nothing
until it is lifted; runs that waited or were paused are checkpointed and go on.

The stall report (`Stalls.classify`): every unfinished goal with no progress in N days
(`agents.autopilot.stall-days`, 3 — a run of its managers started or settled, an ingest, a sweep, a source
linked, or its creation) with one reason — drain, person, held, paused, budget, quota, queue, blocked,
running, failed, no-candidates, idle — and the command that unblocks it: `histmersion agent stalls
[--days n]`, the MCP `scheduler` tool's `stalls`, `GET /api/agents/stalls`, the Scheduler panel.

### Proposal 5 — seed before the plan

`SeedService`: each tick (the autopilot on, not draining) up to `agents.scheduler.seed-goals-per-tick` (2)
unfinished goals with no source, in the scheduler's goal order, each at most once per `seed-every-hours`
(24), run the deterministic candidate search (#3761; their queries, else their title) and put up to
`seed-ingest-per-goal` (5) new legal open-access candidates in their watcher's ingest backlog, which the same
tick drains within the ingest caps through the legal chain only. No model, no place in the scheduler.

### #3763 — the next article once the current ones are done

The scheduler keeps a goal's `propose-articles` item back while the goal has an approved article to
compile or a linked article that has not passed review (a person's enqueue goes all the same; the weekly
seeding cron is kept back too). Once every article is compiled and reviewed, the autopilot re-opens the
proposal (`AutopilotPolicy.nextArticle`, at most once per `agents.autopilot.next-article-hours` after the
last one settled); the plan goes through `article-plan` like any other.

### What did not change

The ingest backlog is not a scheduler candidate: it uses no AI allowance and keeps its own daily caps (the
status lists it). The focus's `max-active-goals` and `focus-ratio` admit nothing any more; `focus` still
shows how the goals rank. Migrations V100 (the strategy), V101 (the digest's day), V102 (the drain).
