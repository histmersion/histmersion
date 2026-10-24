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
