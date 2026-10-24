# Persistent process managers — design (approved 2026-10-15)

User decisions:
- Review "omission churn" (all open MAJORs are skeptic missing-critique raised in the run's last pass) → STOP the item and log a task (re-openable in one click). **Replaced 2026-10-17** (docs/research-review-convergence.md §6.8): churn = every open MAJOR a missing-critique AND the article's open critique-checklist pairs did not go down over `noProgressRuns` runs.
- Watcher auto-ingest of legal-OA finds → OPT-IN per research area, default off, daily cap (5).
- Build PHASE 1 now.

## 0. Defects found in master that phase 1 must fix
1. Interrupted runs never resume: `AgentRepo.reconcile` sets running→paused `interrupted` without `resume_at`; `Engine.resumeDue` needs `resume_at`; `resumeFreed` only matches `plan-quota:%`. Fix: after reconcile in `Engine.start`, unattended origins (scheduler|ingest|run|manager) paused `interrupted` get `resume_at = now+1min`; person runs stay paused.
2. CliRuntime hangs past timeout (#1650): stdin written before `p.waitFor(timeout)` (blocks forever if the CLI doesn't read and the prompt > pipe buffer); `tOut.join()` unbounded (a grandchild holding stdout). Fix: write stdin from its own virtual thread; bounded joins; on kill destroy descendants before parent and re-check; fall back to process-group kill (launch via `/bin/sh -c 'exec …'` / setsid) if tests show escapes.
3. The engine queue is unbounded: only Scheduler checks `AgentRepo.active` vs `max-concurrent-runs`. Managers must check too.
4. compile-article starts its own review run (`CompileArticlePipeline`, `agents.compile.review`) → add `RunParams.chainReview` (phase 2 uses false).
5. Nothing guarantees one agent server per DB → a lease.

## 1. Vocabulary
Run (one pipeline execution, steps checkpointed) · Job (jobs row) · Goal (research area) · Schedule (cron firing one goal pipeline) · **Manager** (new, persistent, kind + optional goal; owns work items, decides next run per item, evaluates outcomes, stop rules; takes hints; records events) · **Work item** (one unit driven to an end, e.g. "article X until it converges", may take several runs).
Managers are a NEW entity (goal_schedules has PK (goal,kind), no queue/cursor/hints; the review manager is library-wide). Cron becomes an optional refill trigger in manager config. Phase 3 migrates schedules to watcher managers.

## 2. Data model — V087 (V086 is reserved for the list-paging branch)
```sql
CREATE TABLE agent_managers (
  id text PRIMARY KEY,               -- 'review', 'seed:<goal>', 'watch:<goal>'
  kind text NOT NULL,                -- review | seed | watch | enrich
  goal_slug text REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  state text NOT NULL DEFAULT 'active',  -- active | paused | stopped (person-set)
  pause_runs boolean NOT NULL DEFAULT false,
  config jsonb NOT NULL DEFAULT '{}', cursor jsonb NOT NULL DEFAULT '{}',
  status_note text,                  -- derived: 'starved: …', 'at capacity', 'idle', 'N pending'
  cron text, next_tick_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE manager_items (
  id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  kind text NOT NULL, target text NOT NULL,
  status text NOT NULL DEFAULT 'pending', -- pending | held | active | done | stopped | failed | cancelled
  priority int NOT NULL DEFAULT 0, attempts int NOT NULL DEFAULT 0, failures int NOT NULL DEFAULT 0,
  run_id text REFERENCES jobs(id) ON DELETE SET NULL, evaluated_run text,
  not_before timestamptz, hold jsonb,      -- {until?, untilSource?, reason, hint?}
  state jsonb NOT NULL DEFAULT '{}',      -- per-run open-major history, reopen counts, notes
  outcome text, origin text NOT NULL,     -- selection | person | chain | hint
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (manager_id, kind, target));
CREATE INDEX manager_items_next ON manager_items (manager_id, status, priority DESC, id);
CREATE TABLE manager_hints (id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  item_id bigint REFERENCES manager_items(id) ON DELETE CASCADE,
  text text NOT NULL, effect jsonb, author text NOT NULL, standing boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(), retired_at timestamptz);
CREATE TABLE manager_hint_uses (hint_id bigint REFERENCES manager_hints(id) ON DELETE CASCADE,
  run_id text REFERENCES jobs(id) ON DELETE CASCADE, PRIMARY KEY (hint_id, run_id));
CREATE TABLE manager_events (id bigserial PRIMARY KEY,
  manager_id text NOT NULL REFERENCES agent_managers(id) ON DELETE CASCADE,
  item_id bigint, run_id text, at timestamptz NOT NULL DEFAULT now(),
  kind text NOT NULL, -- started | settled | evaluated | stopped | escalated | held | hint | paused | stall | starved
  text text NOT NULL);
CREATE INDEX manager_events_recent ON manager_events (manager_id, id DESC);
ALTER TABLE jobs ADD COLUMN manager_id text, ADD COLUMN manager_item bigint, ADD COLUMN heartbeat_at timestamptz;
CREATE TABLE agent_leases (name text PRIMARY KEY, owner text NOT NULL, until timestamptz NOT NULL);
```
New `ManagerRepo.scala`; `AgentRepo` gains `createIn(using DbCon)` (transactional, new columns), `heartbeat(id)`, `forManager(id)`. Events pruned after `agents.managers.keep-events-days` in `Engine.pruneTrace`.

Restart: all state in rows; startup reconcile tick evaluates items whose run settled while down and re-queues its interrupted runs.
Idempotency: one transaction = `AgentRepo.createIn` (queued, manager_id/item) + `UPDATE manager_items SET status='active', run_id=$id, attempts=attempts+1 WHERE id=$item AND status='pending'` (CAS) + event; `engine.submit` only after commit (reconcile re-enqueues queued runs if we die before submit). Evaluation keyed by run: `… WHERE id=$item AND run_id=$run AND evaluated_run IS DISTINCT FROM $run`.
Lease: single row `agent_leases('agents')` claimed with `INSERT … ON CONFLICT DO UPDATE … WHERE until < now() OR owner = $me`, renewed each tick (ttl 3×tick); engine, scheduler and supervisor start only after the claim; a loser logs and runs no agents; shown in About/doctor.

## 3. Execution
3.1 Supervisor `agents/Managers.scala` (shape of `Scheduler`: daemon thread + pure `tick(now)`), every `agents.managers.tick-seconds` (30) and nudged by `Engine.execute → onSettled(run)` via a LinkedBlockingQueue. Tick: renew lease → evaluate items whose run is terminal → release due holds (date passed / awaited source exists) → refill (cron due or below `config.lowWater`: `select()` upserts origin 'selection') → dispatch by capacity (managers round-robin weighted; within: priority DESC, not_before, id; skip targets with an unsettled writing run of any origin via `AgentRepo.forTarget`) → status_note + events.
3.2 Limits: global `agents.max-concurrent-runs` (4) counting queued+running incl. person runs; per manager `config.maxRuns` (queued/running/waiting, and paused for a reason that resumes by itself — interrupted, stalled, allowance, plan-quota — so a restart's interrupted runs keep their places, #1803) and `config.maxOpen` (default 2×maxRuns incl. paused/waiting); per pool: before starting pipeline p check every role has a usable model via the picker without spending (`picker.pick(r, model, now, refresh=None).isRight`), else "starved: <role> chain below floor; frees ~<t>" (one event per change); cap at min(maxRuns, Σ usable pools' max-calls-in-flight). Budgets: day/month caps checked before start like `Scheduling.decide`; per-run budget from config → goal → default; optional `config.dailyUsd`. Backoff: failures++ and `not_before = now + min(base·2^failures, 6h)`; `failures >= maxFailures (3)` → item stopped + task.
3.3 Watchdog (engine-level, all runs): `Engine.Control` gets `@volatile lastBeat` + live process registry; beat at step start/end and every stdout line (`CliRuntime.run(onLine)`), persisted to `jobs.heartbeat_at` ≤ every 30 s; `CliRuntime.run` gets `register: Process => Unit` via a ThreadLocal set on the run's thread (inherited by `RunCtx.parallel` forks). In the existing `resumer()` loop: running run with lastBeat older than `agents.watchdog.stall-minutes` (default provider timeout + 5 min) → kill registered processes, interrupt, settle `Halt.Pause(PauseReason.Stalled, resumeAt = now+10min)`; the third stall on the same step seq fails the run (engine notify files a task for unattended origins).
3.4 Pure `ManagerPolicy` in core: `evaluate(item, run, facts): Next` with `Again(priority, notBefore, stateDelta) | Done(outcome) | Stop(outcome, task) | Retry(backoff)`. Runs waiting (approval) or paused (budget/quota) keep the item active; only terminal runs are evaluated.
3.5 Pause manager = no new runs (in-flight continue); "+ runs" also `Engine.pause` each in-flight run (cooperative). Resume reverses (resumes only runs paused with reason requested). Stop = cancel in-flight + items cancelled. Per item: hold (until date / untilSource DOI or citekey), release, priority, skip (stopped, "skipped by person"), re-open. Pausing a run from the Runs window does not pause the manager.
3.6 Hints: `manager_hints` rows (manager-wide or item; standing or once; author), shown in events. Phase 1 structured effects chosen by the person in the hint box: prioritise targets / match (tag, topic, goal), hold (until date / until source exists), skip. At dispatch the active standing + unconsumed once hints are snapshotted into new `RunParams.notes: Option[List[String]]` (in jobs.params so a resumed run replays the same notes); `manager_hint_uses` rows written; once-hints retired. `RunCtx.notes` appends to the USER message (not system prompt) of: review → fixer and skeptic; compile → writer; enrich → extractor:
```
Notes from the person who runs this library (follow them unless they contradict the record):
<operator-notes>…</operator-notes>
```

## 4. Manager kinds
(a) Review (`review`, library-wide). Config: maxRuns 2, rounds 3, maxRunsPerArticle 3, noProgressRuns 2, reopenLimit 2, staleAfterDays, include filter (tags, goal), per-role model override. Selection priority: person-enqueued > chain (fresh compile) > open MAJORs (`review_ledgers`, by count desc) > never reviewed > (phase 2) stale. Articles without a filter: phase 2 `infer-filter` item first. Step: `review-article <slug> fix rounds=N` origin `manager`. Evaluation from the ledger: no open MAJOR → Done("converged after k runs"); else Stop+task if attempts ≥ maxRunsPerArticle, or open-major count not decreasing over noProgressRuns runs (history in item.state), or any entry reopened ≥ reopenLimit or escalated, or OMISSION CHURN (every open MAJOR is skeptic `missing-critique` raised in this run's final pass; since 2026-10-17: every open MAJOR a missing-critique and the open critique-checklist pairs not down over noProgressRuns runs, which then also replaces the open-MAJOR no-progress rule — see the note at the end) — task text = `ReviewLedger.taskText` + stop reason; else Again with cooldown. Edge/status proposals go to Approvals as today; the manager does not wait for them.
(b) Seeding (`seed:<goal>`) — phase 2: `propose-articles <goal>` (planner; search/facets/article_coverage/list_articles) → code-verified proposals (slug free, filter ≥ minSources, <50% overlap with existing goal articles) → always-manual `article-plan` approval → compile items with `chainReview=false` → on success upsert a review item (origin chain, priority 2) and link to goal.
(c) Watcher (`watch:<goal>`) — phase 3: weekly acquisition-sweep + currency-check items; auto-ingest legal-OA OPT-IN per area (default off, autoIngestPerDay 5, legal chain only); else acquire list. Enrich manager absorbs autoEnrich in phase 2.

## 5. Surfaces
REST (AgentsApi/AgentsRoutes; new ManagersHandlers): GET/POST /api/agents/managers; GET|PUT|DELETE /api/agents/managers/{id}; POST …/{id}/{pause|resume|stop}?runs=true; GET …/{id}/items?status=&offset=&limit=, POST …/items, PATCH …/items/{item}; GET|POST …/{id}/hints, DELETE …/hints/{h}; GET …/{id}/events?before=&limit=; POST /api/agents/enqueue {kind,target} (routes to the library-wide manager; used by page buttons).
CLI (AgentCmd): `agent managers`; `agent manager <id> [status|items|events|pause [--runs]|resume|stop|hint "text" [--item slug] [--once]|enqueue <kind> <target> [--priority n]|hold <target> [--until date|--until-source doi]]`; `agent manager new review|seed|watch [--goal g] [--config k=v…]`.
MCP: ONE new tool `manager` (action list|show|pause|resume|hint|enqueue|hold); `run_status` gains a `manager` filter.
Web: WindowKey.Managers / Manager(id), toolbar entry; ManagersView (list: kind/goal, state badge incl. derived starved/stalled/idle, in-flight runs, counts, last event); manager window (pause [+runs] / resume / stop; current items with run link + live progress; paged item table with hold/release/priority/skip/reopen; hint box: text, scope, standing/once, optional structured action; active hints; events feed; typed config form). Phase 1 button: Review on the article window → `/api/agents/enqueue` priority 10. Phase 2: New research area form in GoalsView (plan / seed / watch checkboxes), Extend and Enrich buttons.

## 6. Phases, tests, risks
Phase 1: V087 + ManagerRepo; lease; Managers supervisor (tick, nudge, evaluate, dispatch); review manager (selection: enqueued, open MAJOR, unreviewed) with all stop rules incl. omission churn; pause/resume/stop, hold/skip/priority; hints as prompt notes + structured hold/prioritise; events; watchdog + CliRuntime fixes + PauseReason.Stalled + auto-resume of interrupted unattended runs; REST, CLI, MCP `manager`; basic Managers window + Review button.
Phase 2: infer-filter + stale selection; enrich manager; seeding + article-plan approval; chainReview; New research area form; Extend/Enrich buttons.
Phase 3: watcher (+opt-in auto-ingest); schedules → managers migration; LLM-proposed hint effects (person-confirmed); per-manager daily USD in UI.
Tests: pure ManagerPolicy (each stop rule, ordering/fairness, capacity with pools, backoff); `Managers.tick(fixed)` with scripted models (3 runs to PASS; starved→freed; pause; pause+runs); crash tests (commit w/o submit → fresh Engine/Managers runs it exactly once, evaluated once; kill between settle and evaluate; running at boot → interrupted with resume_at → resumed); lease (two instances, takeover after ttl); watchdog (fake CLIs: never reads stdin with 200 KB prompt; sleeps past timeout; grandchild holding stdout → process tree dead, run paused stalled within deadline); hints (snapshot in params reaches fixer's user message; resumed run same notes; once-hint retired).
Risks: stop rules misfiring (every stop logged with reason as a task, one-click re-open; tune on this week's ledgers); cost runaway (caps before start, dailyUsd, maxRunsPerArticle, low-water refill, pools); two drivers per article (dispatch guard + chainReview); hint injection (fenced, subordinate to record, recorded per run); macOS process-tree kill (process group fallback); merge conflicts (V086 paging branch, RunsView/ApprovalsView pager reuse).

## As built (phase 1)

Kept current while phase 1 is built (branch `managers-phase1`).

- Defects: CliProcess (#1650) — stdin on its own thread, one deadline, bounded drains, kill descendants→parent→re-check,
  CLI started as a process-group leader (`setsid`, else `perl -e setpgrp`; tests showed an orphaned grandchild escapes
  `Process.descendants`). Interrupted unattended runs resume after `agents.watchdog.interrupted-resume-minutes` (only
  the runs this reconcile paused; older `interrupted` ones are left for a person). Watchdog: beats at step boundaries,
  every model call, every CLI line and while waiting for a slot; stall count = run warnings `stalled at step N`.
- V087 exactly as §2; Flyway needs no contiguity (no outOfOrder); a DB with 87 applied refuses a later 86, so the live
  corpus must get both from one build (list-paging owns V086). Registered in server reachability metadata.
- Server class is `ManagerSupervisor` (core has `object Managers`); event kinds are `Managers.Feed` (core already had
  an `EventKind`). REST lives in `ManagersApi`/`ManagersRoutes` (own files, to keep merges with list-paging clean).
- Lease owner = `host:pid` (two servers in one JVM — the test suites — share it).
- Review task on stop: the ledger's open review task is rewritten with the reason, else a new `tasks` item.
- `Process.descendants` on macOS throws "Cannot allocate memory" now and then (its sysctl read races
  process churn): a failed look is skipped; the next one (every 250 ms, and before each kill) sees the tree.
- Web: the manager's events use list-paging's `ListPaging.olderButton` (newest first, `before` cursor); its
  items keep the small offset `Pager` — they are ordered by urgency, which a keyset-by-time cursor cannot page.
- Verified end to end on a scratch JVM server with agents on and no models: the review manager is created
  paused, resume → selection, enqueue/hint/hold/release/priority/skip/reopen/config/pause --runs over the CLI,
  REST and the MCP tool; the supervisor reports `starved: reviewer: no model …` and starts nothing.
- Not built in phase 1: `RunParams.chainReview` is honoured but nothing sets it; `cron` on managers is stored, not fired;
  `dailyUsd` per manager not implemented; topic matching of hints has no data (articles have no topics).

## As built: the critique checklist (2026-10-17)

docs/research-review-convergence.md §6.8. The review manager's omission churn changed: 347 missing-critique MAJORs
(315 fixed) showed the omissions are real and fixable, and 88 % sit on a typed disagreement edge, so an article's
critique checklist (every challenges/refutes/updates/superseded_by edge touching a cited source: addressed in one
paragraph, reasoned, or open) is a closed list the runs can finish.

- `RunMark` gains `pairsBefore`/`pairsAfter`, `ItemState` gains `pairsBefore`: the open pairs, counted live
  (`Handlers.openCritiques`) at dispatch and at evaluation. The dispatch event names them.
- `ReviewFacts(openMajors, maxReopened, escalated, onlyMissingCritiques, openPairs)`; `omissionChurn` is gone.
- Stop for churn only when every open MAJOR is a missing-critique AND `pairsStalled` (the window's first
  `pairsBefore` ≤ its last `pairsAfter`, both known). With only missing-critique MAJORs open the open-MAJOR
  no-progress rule does not apply (a MAJOR per open pair keeps it flat while the pairs go down);
  `maxRunsPerArticle`, escalation and the reopen limit still stop. Unknown counts never stall.

## As built (phase 2)

Branch `managers-phase2` (#1728). No migration: everything new lives in the V087 columns (`manager_items.kind/state/hold`
jsonb, `approvals.kind/subject/proposal`) — V092 is not used.

- **Item kinds** (`Managers`): review manager `article-review`, `infer-filter`, `article-extend`; enrich manager
  `source-enrich`; seeding manager `propose-articles`, `article-compile`. `ItemOrigin` gains `ingest`. The supervisor
  (`ManagerSupervisor`) runs `review`, `enrich` and `seed` managers; `watch` stays reserved. `ensureDefaults` now creates
  `review` AND `enrich`, both **paused**; `seed:<goal>` is created paused by `POST /api/agents/managers {kind: seed, goal}`,
  the New research area form, the goal's Extend button, or an approved plan.
- **Two new pipelines** (planner role, read-only tools): `infer-filter <article>` (agent `filter-inferrer`) and
  `propose-articles <goal>` (agent `article-planner`). Their pure checks are `core.FilterInference` (the filter returns
  `agents.infer-filter.min-hits..max-hits` sources and at least `min-cited-share` of the sources the article cites; a
  failing answer goes back with the numbers, the last attempt proposes nothing) and `core.PlanArticles` (valid, free slug
  — no article, no old slug, not already planned —, filter ≥ `minSources` hits, overlap < `agents.seed.max-overlap` with
  every article linked to the goal, every planned compile item, and every proposal kept before it).
- **Deviation — non-blocking approvals.** §4 reads as if both pipelines wait for their approval. A waiting run counts
  toward `maxRuns` (#1803), so two filters waiting for a person would stall the review manager. Instead the run records
  an `article-filter` / `article-plan` approval (new `ApprovalKind`s, `blocking = false`, about a `subject`) and
  finishes; the manager evaluates the run as `Next.Wait(approval)` and holds the item with `Hold.untilApproval`. The tick
  settles it once a person decides: approved → done (an approved plan becomes one `article-compile` item per article,
  carrying its `ArticleSpec` in `ItemState.spec`), rejected/failed → stopped. Approving an `article-filter` records the
  filter (`Approvals.decideSubject`, unless the article got one meanwhile); `agents.approvals.article-filter = auto`
  records it inside the run. `article-plan` is always manual — no setting.
- **Infer-filter before review.** The selection (`ManagerPolicy.select`, pure) adds an `infer-filter` item at the
  review's priority for an article that records no filter and never had one inferred; a person's enqueue does the same
  (`ManagersHandlers.filterFirst`). Dispatch makes the review wait while that item is open (`ManagerPolicy.waitsFor`).
  `inferFilter` (config, default `agents.managers.review.infer-filter = true`) turns it off.
- **Stale.** A converged review item stores the filter's hit count (`ItemState.filterHits`). At most every
  `stale-scan-minutes`, when the selection has room, `ManagerRepo.staleCandidates` (done items, then ledgers with no open
  MAJOR and no item) are judged by `ManagerPolicy.staleWhy`: older than `staleAfterDays`, or the filter returns more
  sources than at convergence (`staleAfterDays = 0` disables both). They are re-selected at priority −1 (`restale`:
  counts reset, history dropped). Selection order: person 10 > chain 2 > open MAJORs 1 > never reviewed 0 > stale −1.
- **chainReview** is set to `false` on every compile the managers start (`article-extend`, `article-compile`). A done
  extension that appended re-queues the article's review in the review manager (origin `chain`, priority 2); a compiled
  article is linked to the goal and queued for the review manager the same way. Whether a compile wrote is read from its
  `write` step's checkpoint (`CompileArticlePipeline.Written`).
- **Enrich manager absorbs auto-enrich.** `agents.auto-enrich` keeps its meaning (an ingest enriches its source) and its
  rules (`AutoEnrich.decide`); with the managers on the source becomes an `ingest`-origin item of `enrich` instead of a
  direct run, so `agents.managers.enrich.max-runs` (4, the old effective cap `max-concurrent-runs`) bounds it; budgets,
  plan floors and approvals apply as before. **Behaviour change:** `enrich` is created paused — after deploying,
  imports queue items and nothing is enriched until a person resumes it once (the job's progress line says so). With
  `agents.managers.enabled = false` the old direct run is kept. At dispatch a non-person item whose source got findings,
  notes or a live enrich run meanwhile is settled `done` without a run; a person's Enrich always runs.
- **Surfaces.** `POST /api/agents/enqueue` now answers `EnqueueResult{managerId, items, skipped}` (phase 1 answered one
  `ManagerItem`) and takes `review | extend | enrich | seed`; `enrich` of an article/goal queues only its unenriched
  sources. CLI: `agent manager new review|enrich|seed --goal g`, `agent enqueue <kind> <target>`, `--json` on `managers`,
  `manager new`, `status`, `items`. MCP `manager`: `create` (kind, goal), `enqueue` with `kind` (routed by kind when no
  `id`). Web: the goals window's **New research area** form (title, slug from the title, notes, tags; plan / seed
  checkboxes, watch disabled "phase 3"; an existing slug is refused, never replaced), Extend / Enrich on the goal window,
  Review / Extend / Enrich on the article window; the manager settings form gains stale days, infer filter, min sources,
  max articles.
- Per-role `models` overrides are filtered to the roles of the pipeline a dispatch starts; a manager's `models` may name
  any role its kind's pipelines use (`ManagerSupervisor.roles`). The starvation check looks at the roles of the due
  items' pipelines only.
- Not built: watch managers, schedules → managers, LLM-proposed hint effects, per-manager `dailyUsd` (phase 3).

## As built (phase 3)

Branch `managers-phase3`. No migration: everything new lives in the V087 columns (`agent_managers.cron` /
`next_tick_at`, the jsonb `config`, `manager_events`, `approvals.kind/subject/proposal`) — V092 and V095 are not used.

- **Watcher** `watch:<goal>` (§4 (c)). Item kinds `acquisition-sweep` and `currency-check` (target: the goal),
  run by the existing pipelines. `POST /api/agents/managers {kind: watch, goal}` (CLI `agent manager new watch
  --goal g`, MCP `manager create`, the New research area form's watch box) creates it **paused** with the cron
  `agents.managers.watch.cron` (`@weekly`) and one pending item per sweep (`config.sweeps`, default both). Both
  items name the same goal, so the dispatch guard runs them one after the other; `maxRuns` 1.
- **Auto-ingest is opt-in** (`config.autoIngest`, default off; `autoIngestPerDay`, default
  `agents.managers.watch.auto-ingest-per-day` = 5). At dispatch the allowance left today (the cap minus the
  watcher's `ingested` events since UTC midnight, `ManagerPolicy.ingestAllowance`) goes into
  `RunParams.autoIngest`, so a resumed run keeps it. The sweep's `ingest` step (present only then — without it
  the steps are exactly v1's, so the pipeline version stays 1) offers the picks marked open access in the
  model's order until that many were ingested (`Scheduling.ingestPicks`: a failure does not count, the next is
  tried) through `AgentUpstream.ingestLegal` → `AcquireHandlers.ingestLegal`: the legal chain only
  (`OaFetcher.fetchLegal` never runs an `Access.Unauthorised` fetcher, whatever `acquire.allow-unauthorised` says) and **no source without a
  legal file** (refused before anything is stored). An ingested work is linked to the goal and recorded as an
  `ingested` event; everything else — closed picks, failures, picks past the cap, and every pick with
  auto-ingest off — goes to the acquisition list as before. The currency check is unchanged (its outside papers
  are queued; it does not ingest). **Replaced 2026-10-19** (see "As built: the ingest backlog"): with auto-ingest
  on, an open-access pick past the cap waits in the watcher's ingest backlog; only closed picks and works with no
  legal file reach the acquisition list.
- **What a sweep found**: when a sweep item is evaluated (done), a `swept` event carries the run's worklog line
  (candidates, picked, ingested with citekeys, queued, left out) and the works it picked (title, DOI, open
  access); the item's outcome is the same line. The next cron firing re-opens it.
- **Managers fire their cron.** Every tick (only while `agents.scheduler.enabled`), each ACTIVE manager's cron
  (`ManagerPolicy.cronStep`): never scheduled → its first firing recorded; due → fire once and move on to the
  next firing from now (a firing missed while paused or down fires ONCE, never once per missed time); a paused
  manager fires nothing. Firing: a watcher re-opens its settled sweeps (one still open is left to finish), a
  review manager runs its selection whatever its low water, a seeding manager re-opens its propose-articles
  item; the enrich manager has no cron (refused). A `cron` event says what it did. `PUT …/{id} {cron}` (CLI
  `agent manager <id> cron`, the settings form) checks it (`ManagerPolicy.checkCron`: parses, no more often
  than `agents.scheduler.min-interval-minutes`) and computes the next firing.
- **Schedules → watchers.** `WatchSchedules.migrate` (pure) turns a goal's `goal_schedules` rows into its
  watcher: sweeps = the ENABLED schedules' kinds (a disabled one is noted, not swept), one cron — the one that
  fires most often when they differ (noted) — with that schedule's next firing, the acquisition schedule's
  budget (the currency check calls no model). An existing watcher keeps its own cron and settings (a bundle
  merge keeps this collection's schedules, #881) and gains the kinds. `ManagerSupervisor.migrateSchedules`
  does it per goal in ONE transaction that takes the rows out with `DELETE … RETURNING` (two servers cannot
  both migrate them), creates the watcher **paused**, and logs a `migrated` event and a worklog line. It runs
  at every start (agents on or off, before the lease) and every managers' tick, so a bundle import's rows
  follow. **Behaviour change:** an enabled schedule used to fire by itself; its watcher is created paused (the
  phase-1 rule: nothing spends until a person resumes it), so a person resumes it once. The live corpus had no
  schedules when this was built.
- The `Scheduler` thread is **removed**; `agents.scheduler.tick-seconds` is removed (a value set is ignored —
  `ConfigSuite`); the rest of `agents.scheduler` keeps its meaning (`enabled` now gates every manager's cron).
  The schedule surfaces keep working as a facade over the watcher (`ManagersHandlers.setSchedule` /
  `removeSchedule` / `goalSchedules`, `WatchSchedules.view`): setting one creates the paused watcher or adds
  the sweep (the cron is the watcher's, shared), `--off` / `enabled: false` takes the sweep out (its open item
  cancelled), removing one leaves the watcher. A bundle exports a watcher's sweeps as the goal's schedules
  (and still the rows not moved yet); importing them writes rows the next tick moves. Runs a schedule started
  before (origin `scheduler`) keep their backlog-item behaviour.
- **LLM-proposed hint effects, person-confirmed** (§3.6). New approval kind `hint-effect` (non-blocking, about
  the manager, payload `HintProposal{managerId, hint, text, effect, by, why}`); a new action `config` (a
  settings change, as `ManagerConfig` keys) a model may propose — a person's own hint cannot carry one (the
  settings form). A proposal is never applied until a person approves it; then
  `ManagersHandlers.applyProposal` checks it again against the manager as it is (a failure marks the approval
  `failed` with why), applies it through the same path as a person's hold/prioritise/skip (or merges the
  settings) and records it on the hint (`ManagerHint.effect`; `proposal`/`proposed` show an open one). Two
  sources: the MCP `manager` tool — a model drives it, so an `effect` given there is ALWAYS a proposal
  (`HintAdd.propose`) — and `suggest` (REST `HintAdd.suggest`, CLI `hint --suggest`, the hint box's "suggest
  an action"): a person's `hint-effect` run (new pipeline, planner role, agent `hint-reader`, no tools, target
  kind `manager`, `RunParams.hint`) reads the hint, the manager's items and settings; `HintProposals.check`
  (pure) sends back an unknown action, target, date or setting (on the last attempt an unknown target is
  dropped with a warning, an answer still wrong proposes nothing); the run opens the approval and finishes.
  With agents off a `suggest` is refused before the hint is kept.
- **Per-manager daily USD** (`config.dailyUsd`, default `agents.managers.daily-usd` = 0 = none): no run starts
  once the manager's runs' spend this UTC day (`ManagerRepo.spendToday`: their steps' `cost_usd` since UTC
  midnight) reaches it — checked before each dispatch, after the global caps; the status line says
  `budget: its spend today … reached its daily-usd …` and one `starved` event per change. **Metered spend
  only**: a plan-billed call (Claude/ChatGPT plan) is recorded at 0 USD and never counts; there is no
  token-to-USD equivalent — the day's tokens (every call's, `tokensToday`) are shown beside it instead.
  `ManagerInfo.spentTodayUsd` / `tokensToday` (REST, CLI `--json`), "today X of Y USD, N tokens" in the
  Markdown every surface prints (CLI, MCP), the Managers window (each row, the manager's head) and the
  settings form. Runs started in the same tick are not re-summed, so a tick can overshoot by the runs it
  starts.
- **Web.** The New research area form's watch box is live: ticked, a paused watcher with auto-ingest OFF unless
  its own box is ticked, and the daily cap (blank = the server's default). The Managers window shows each
  manager's spend today; a manager window its cron (and takes it, the daily cap and a watcher's sweeps /
  auto-ingest / cap in its settings form — a watcher's form leaves out the review settings), and a watcher's
  window a Sweeps section (cron, next firing, whether picks are ingested, each settled sweep and ingest). The
  approvals window reads `hint-effect` (and now the phase-2 `article-filter` / `article-plan`, which fell into
  the status-change line); the runs window links a run on a manager.
- Tests: `ManagerPolicySuite` (watcher settings, ingest allowance, cron steps, `checkCron`, daily cap),
  `SchedulingSuite` (`ingestPicks`), `WatchSchedulesSuite`, `HintProposalsSuite`, `ConfigSuite` (the removed
  key), `FetchChainSuite` (no unauthorised fetcher on the legal path), `ManagerPhase3Suite` (DB: watcher, opt-in ingest
  and the cap, off by default, cron firing, proposals via MCP/`propose`, `config` proposals, `suggest` with a
  scripted model, the daily cap), `SchedulerRunSuite` (the facade, the migration), `MergeExtrasDbSuite` (a
  watcher's sweeps travel in a bundle), web unit suites and a browser test (the watch box, the day's spend, the
  Sweeps section).


## As built: the autopilot (2026-10-19)

Branch `autopilot`. Goal: a research goal goes from `planned` to `reviewed` with no person in the loop, inside
the rails the managers already had (budgets, plan floors, `dailyUsd`, `max-concurrent-runs`, `maxRuns`, legal
open access only, stop rules that file tasks). **V096** `agent_autopilot` — one row: the switch, who/when, the
managers it paused when turned off, and when it last ensured / backed up / linted (so a restart does not repeat a
daily job). V092 and V095 stay unused.

What already worked and was kept: the review chain after a compile (`chainReview = false` + a chained review
item), the stale re-review, the watcher's opt-in legal auto-ingest with its daily cap, cron firing, the
article-filter `auto` mode, and every stop rule.

- **The switch.** `histmersion agent autopilot [status|on|off] [--json]`, `GET|POST /api/agents/autopilot
  {on, by?}`, the MCP `manager` tool's `autopilot` action (`on: true|false`; no new tool — still 68), and the
  Managers window's toggle (the new state shows at once; the server's answer follows). On: `review` and `enrich`
  are resumed, and every tick (`AutopilotService.tick`, at most every `agents.autopilot.ensure-minutes`, at once
  on `on` and after a plan created sub-goals) applies `AutopilotPolicy.ensure` and `AutopilotPolicy.advance`.
  Off: every ACTIVE seeding manager and watcher is paused and remembered; `on` again resumes exactly those —
  a manager a person paused is never resumed, a setting a person chose is never overwritten (only an unset
  cron, auto-ingest or extend threshold is filled). While it is on, a manager created without an explicit
  `paused` (the New research area form, the Extend/Enrich buttons) is created active.
- **Ensure** (pure, `AutopilotPolicy.ensure`): every goal not `reviewed`/`done` gets `seed:<g>` and `watch:<g>`,
  both active; a reviewed/done goal only a watcher on `finished-watch-cron` (`@monthly`). Watchers get
  `autoIngest = on` at `watch-ingest-per-day` (5). Crons `@daily/@weekly/@monthly` are spread per manager id
  (`AutopilotPolicy.stagger`: minute, hour and weekday/day-of-month from a stable hash), so 60+ watchers do not
  fire in one minute. **Sub-goals:** every goal gets its own managers — leaves and parents alike — and
  `propose-articles` now measures overlap (and lists "its articles") over the goal, its ancestors and its
  descendants, and their seeding managers' planned compiles, so parent and child do not plan the same article.
- **Planned = no plan yet.** A goal that records no plan and links no query gets a `plan-goal` item (new item
  kind of the seeding manager, priority 1) before its first `propose-articles` (`ManagerPolicy.waitsFor`). Its
  run is the existing blocking plan-goal; with `subGoals = Some(0)` (`RunParams.subGoals`) when the goal is at
  `agents.approvals.auto.max-goal-depth` (2) — the planner is told to propose queries and works only — so an
  auto-approved plan cannot nest goals forever. An approved plan's sub-goals get their managers at the next tick.
- **Checked auto approvals** (`AutoApprovals`, every tick, before evaluation): an OPEN `article-plan`,
  `goal-plan` or `article-publish` approval is decided by code when `agents.approvals.article-plan|goal-plan =
  auto` (or `checked`), `agents.approvals.article-publish = checked`, or the `autoApprove` setting of the
  manager whose run opened it (wins either way). Checks (`AutopilotPolicy.articlePlan|articlePublish|goalPlan`,
  against the corpus as it is NOW): plan — every slug still free and every filter ≥ min-sources; publish — the
  run recorded a filter (its `snapshot` checkpoint), the filter returned ≥ `publish-min-sources` (its `filter`
  checkpoint), the self-check left no problem, it cites a source, a new slug is still free; goal plan — proposes
  something, sub-goals only above the depth limit. Caps per UTC day (counted from the approvals noted
  `autopilot: approved`): `articles-per-goal-per-day` 6, `publish-per-day` 10, `goal-plans-per-day` 5. The
  decision goes through `AgentsHandlers.decide` — exactly a person's click (a blocking run is woken; an approved
  plan becomes compile items) — with the note `autopilot: approved|rejected — why`, an `approval` event on the
  manager and a worklog line; to undo: skip the compile items, or `article versions`/restore the article. A
  reached cap leaves it open (one event per change) for a person or the next day. A draft the checks REJECTED is
  compiled again (`StepFacts.Redo` → retry with backoff, a task after `max-failures`). **Deviation:**
  `article-publish = auto` keeps its old meaning (approved inside the run, unchecked); `checked` is the gated
  one, because changing `auto` would have silently gated existing installs. Shipped defaults stay `manual`.
- **Goal status** (pure `AutopilotPolicy.advance`, applied deepest goal first, only while on, forward only,
  `done` untouched): `seeding` once its seeding manager or watcher is active; `compiled` once it has an article
  (a parent: once its sub-goals are compiled) and its seeding manager has no compile open (nor a plan/proposal
  held or running); `reviewed` once every linked article has a ledger with no open MAJOR and no open review
  item, and every sub-goal is reviewed. Logged as an `autopilot` event and a worklog line.
- **New sources reach articles.** Stale selection only re-reviewed. Now the review manager's refill also looks
  (at most every `stale-scan-minutes`) for articles whose filter returns ≥ `extendMinNew` sources they neither
  cite nor give a reason for (`Handlers.newFilterSources`, over the first `limits.coverage-sources`), not
  extended within `extend-cooldown-days` (14) and with no open item: an `article-extend` item (selection), whose
  review follows as a chained item. `agents.managers.review.extend-min-new` ships 0 (off); the autopilot sets
  the review manager's `extendMinNew` to `agents.autopilot.extend-min-new` (3) when it has none.
- **Shared caps.** A watcher's sweep ingests at most min(its own allowance, `agents.autopilot.ingest-per-day`
  minus every watcher's `ingested` events today) while the autopilot is on. (What overflowed it went to the
  acquisition list — fixed by the ingest backlog, below.) Model spend stays bounded by what
  already existed: `max-concurrent-runs`, the budgets, plan floors, `dailyUsd`, `maxRuns` per manager.
- **Dispatch guard narrowed for goals.** A goal's plan, proposal and sweeps used to wait for any unsettled run
  on the goal — a plan waiting days for a person stopped the goal's watcher. Now (`ManagerPolicy.blocks`) a
  goal-targeted item waits only for a run of its own pipeline; articles and sources keep one writer at a time.
- **Housekeeping** (only while on; each on its own thread, one at a time, never beside a restore, a backup or a
  vacuum — `Handlers.housekeepingBlock`; pg_dump reads one snapshot, so ingests and runs beside it are fine):
  a backup every `backup-hours` (24; `backups.keep` prunes as usual) and the safe `lint --fix` repairs every
  `lint-hours` (24), each recorded when started and logged when done. **Not built: the markdown export.**
  `histmersion export wiki` and `queue export meta` are CLI operations that write into the caller's working
  tree (the research repo's `wiki/`/`meta/` symlinks into Nextcloud); the server has no configured path there
  and the export logic lives in the CLI, so it stays a host job (a launchd/cron entry running those two commands
  from the research repo).
- **Status** (`AutopilotStatus`, a few aggregate queries for every goal: `ManagerRepo.light/pace`,
  `GoalLinkRepo.linkCounts/articlesByGoal`, `AgentRepo.openBySubject`): per goal its stage, managers with their
  status lines, sources, articles, open approvals (about the goal and its articles), last activity, items
  settled in the last 7 days, items open, and `etaDays` at that pace; plus the approval rules, today's ingests
  against the shared cap, the last housekeeping.
- Tests: `AutopilotPolicySuite` (every check and cap, advance, ensure idempotence, stagger, extend, blocks,
  Redo), `ManagerAutopilotSuite` (DB, scripted models: planned → plan → proposal → compile → review →
  `reviewed` with checked approvals; with the defaults nothing is decided by code; a manager's `autoApprove`
  wins; sub-goals and the depth limit; off/on and a person's pause; a reviewed goal's watcher; the article-plan
  cap and a rejected thin filter; the shared ingest cap; extend and its cooldown; lint housekeeping),
  `ManagersViewSuite`, and a browser test of the toggle.

## As built: the ingest backlog (2026-10-19)

Branch `oa-overflow`. No migration (V097 not needed: everything lives in `manager_items` and its `state` jsonb);
V092 and V095 stay unused.

**The bug.** With the autopilot on, the watchers' sweeps added 182 items to the person's `acquire` list in six
hours; 96 were legal open-access works. The shared allowance (`agents.autopilot.ingest-per-day` 20) was spent, a
sweep was dispatched with `autoIngest = None`, and the phase-3 rule "past the cap → the acquisition list" sent every
open-access pick there. The acquisition list is the person's buy/borrow list; an open-access work belongs to the
agent.

- **The backlog is manager items.** New item kind `oa-ingest` of the watcher that found the work (target: the
  DOI; `ItemState.oa` = `OaWork{doi, title, year, authors, venue, goals, why, foundBy, acquireText}`). So it shows
  in the Managers window, is deduplicated by `UNIQUE (manager_id, kind, target)`, has a priority, can be held,
  skipped, re-prioritised by its DOI like any item, and survives restarts. It is never dispatched as a run
  (`dispatchAll` leaves the kind out; it has no pipeline, and `ManagerSupervisor.roles` ignores it).
- **One entry per DOI across watchers** (`OaBacklog.add`, pure `IngestBacklog.place`): a pick the corpus has is
  linked to the goal as a source; one an acquisition item names (open or done, looked up again just before
  adding) is linked to that item; one another watcher holds an open entry for adds this goal to that entry's
  `goals`; one this watcher settled before (a person skipped it) is left alone; else a new entry. A sweep's
  candidates leave out the backlog DOIs this goal found already — a DOI only another goal found is still offered,
  so the second goal can join the entry.
- **The sweep** (`RunParams.oaBacklog = Some(true)`, set at dispatch whenever the watcher's auto-ingest is on, even
  with 0 left; a run dispatched before keeps its old steps): discover · rank · [ingest] · **backlog** · queue · close.
  The ingest step takes the open-access picks within today's allowance (`Scheduling.ingestOa`: the same order and
  cap as `ingestPicks`, and it keeps the failures); the backlog step parks the open-access picks not ingested
  (past the cap, or failed for another reason than a missing legal file); the queue step gets only the closed
  picks and the ones the legal chain has no file for (`IngestBacklog.NoLegalCopy`, the prefix
  `AcquireHandlers.ingestLegal` answers with).
- **The backlog first.** At dispatch a sweep's allowance is `IngestBacklog.sweepAllowance(own, shared, ownBacklog,
  allBacklog)`: what is left of its own cap after its own pending entries, and of the shared cap after every
  pending entry of a watcher that drains; so new finds wait behind the backlog. The `started` event says how many
  wait first.
- **The drain** (`OaBacklog.drain`, every managers' tick after the evaluations and holds, before the dispatch):
  the due pending entries of ACTIVE watchers with auto-ingest on, priority then the oldest
  (`IngestBacklog.drain`), within each watcher's own allowance today and the shared one, at most
  `agents.managers.watch.drain-per-tick` (3: each ingest fetches a file, so a tick takes a few; the daily caps
  bound the day). Each is ingested through the legal chain only, linked to every goal that found it, and recorded
  as an `ingested` event of its watcher (what both caps count); one the corpus has meanwhile is settled without an
  ingest; one with no legal file goes to the acquisition list (in the sweep's wording, `(open access)`, linked to
  its goals) and the entry is `stopped`; any other failure is tried again after the managers' backoff, the
  acquisition list after `max-failures` (`IngestBacklog.onFailure`). One worklog line per tick that did something.
  A paused watcher's entries wait; turning the autopilot off pauses the watchers, so nothing drains.
- **Status.** `ManagerInfo.backlog` / `backlogDays` (a watcher) and `AutopilotStatus.backlog` / `backlogDays`:
  pending + held entries, and the UTC days they take at the current allowance (`IngestBacklog.daysToDrain`, a
  day-by-day estimate over each watcher's own cap and the shared one; 0 = today, None = never — auto-ingest off, a
  cap of 0, the watcher paused). Shown by `agent autopilot`, `agent managers`, `agent manager <id> status`, the MCP
  `manager` tool, and the Managers window (the autopilot panel, each watcher's row, the manager's head). A
  watcher's backlog does not count in its goal's pace (`ManagerRepo.pace`): it drains by the caps, not by runs.
- **The one-shot repair** — `histmersion agent autopilot repair-acquire [--dry-run] [--json]`, `POST
  /api/agents/autopilot/repair-acquire {dryRun}`, the MCP `manager` action `repair-acquire` (still 68 tools).
  A watcher-created item is found by STRUCTURE, not wording (`ManagerRepo.sweepAcquireItems`): an
  `acquisition-sweep` run dispatched by a watcher (`jobs.manager_id`) ranked the DOI (its `rank` step's checked
  answer, which also says whether it is open access), the item was created while that run's `queue` step ran
  (±2 s), and the run linked it to its goal by that DOI (`goal_links` kind `queue-item`, note = the DOI). A
  person's item, a plan-goal's and a currency check's match none of the three; the text pattern
  "(open access)" and the topic "goal <slug>" were rejected as signals (a plan writes the same topic; a person can
  write anything). Per item (`IngestBacklog.repairAction`): ranked open access and its watcher auto-ingests → into
  the backlog (`move`; the acquisition item is deleted and its goal links with it, its wording kept in
  `OaWork.acquireText`); in the corpus already → the item is deleted (`remove`); closed, or its watcher gone or not
  auto-ingesting → `keep`. Every other open acquisition item is reported as `untouched`. Each move is a manager
  event; the whole a worklog line. Deleting (not ticking off) keeps the list honest: `done` means acquired.
- Live on 2026-10-19 (read-only look): 145 open acquire items match the structure, 96 ranked open access (96
  DOIs, 35 watchers, none in the corpus yet), 49 closed; 43 other open items (a person's, plans', currency
  checks') are untouched. At 20 a day in all and 5 per watcher, the 96 drain in about 5 days.
- Tests: `IngestBacklogSuite` (allowance after the backlog, drain order and caps, placement, failures, days to
  drain, the repair's decision, the wire state and Markdown), `SchedulingSuite` (`ingestOa`), `ManagerPolicySuite`
  (the item kind), `ManagerCmdSuite` (the CLI against a stand-in server), `IngestBacklogDbSuite` (DB, scripted
  model, fixture upstream: 30 open-access works at an allowance of 20 → 20 ingested, 10 in the backlog, 0 on the
  list, nothing the same day, drained the next day oldest first; one entry across two watchers linked to both
  goals, a work with no legal file to the list on its day; the shared cap across watchers and a paused watcher;
  the repair — a sweep's open-access item moved, one in the corpus removed, a closed one and a person's kept, dry
  run changes nothing, idempotent, the moved work drains), `ManagerPhase3Suite` (updated: the open pick past the
  cap is in the backlog, not on the list).

## As built: the focus (2026-10-23)

> **Superseded (2026-10-24):** the central scheduler (docs/research-goal-advancement.md "As built") admits
> the managers' work now; the focus holds nothing, its pins and priorities are the goals' priorities in
> the scheduler, and `max-active-goals` / `focus-ratio` admit nothing. What follows is the history.

Branch `goal-focus`. **V098** `goal_focus` (one row per goal the focus touched or a person pinned: `active`,
`since`/`why` it was chosen, `left_at`/`left_why`, `pinned`, `priority`) and `agent_autopilot.max_active_goals`
(a person's cap; NULL = the config's). V092 and V095 stay unused.

**The problem.** With goal-plan auto-approval the goals grew from 63 to 156 in three days (128 `seeding`, 22
`reviewed`, 6 `compiled`). Every seeding goal had an active seeding manager and watcher, so the model work —
sweeps with a ranking model, plans, proposals, compiles, reviews, enrichment — was spread over all of them
within the same `max-concurrent-runs`, and almost nothing finished.

- **What is capped.** While the autopilot is on and the cap is above 0, at most `agents.autopilot.max-active-goals`
  (8) goals are ACTIVE for model work. The model-using items (`GoalFocus.usesModel`: every kind but
  `currency-check` and `oa-ingest`) of every other unfinished goal are HELD with a `Hold(focus = goal, reason =
  "focus: …")` — pending items only; nothing is cancelled, runs in flight finish. A goal's items are its seeding
  manager's, its watcher's sweep, and the review manager's on the articles linked to it (an article of several
  goals runs when one of them is active; one of no unfinished goal is library work and runs). A person's own
  item (`origin = person`) is never held. The focus decides WHAT runs; `max-concurrent-runs`, the budgets, plan
  floors, `dailyUsd` and the daily caps still decide how much.
- **What is not capped.** The ingest backlog drains for every goal within the ingest caps; OCR and conversion
  likewise; the currency checks (no model) run for all. The enrich manager is library-wide: `GoalFocus.fairOrder`
  starts `focus-ratio` (3) items of the active goals' sources (and a person's) to one of any other, counting on
  from its last starts (`FocusRepo.recentTargets`); the review manager does the same for its library articles.
  Finished goals' monthly sweeps are library work too (not held).
- **Selection** (`GoalFocus.select`, pure, deterministic). A goal is a candidate unless it is finished, or cannot
  use a slot (`Fit.Waits`): `children` (a parent with unfinished sub-goals — it takes a slot only for its
  plan-goal item; its own proposals wait), `person` (every model item, or what it waits for — a proposal behind
  its plan, a review behind its infer-filter, `GoalFocus.blockers` — waits for an approval only a person decides:
  `agents.approvals.*` manual, or the manager's `autoApprove = false`), `held` (a person's hold or a paused
  manager), `stuck` (no open model item and its newest settled one stopped or failed — the stop rule's task says
  why), `idle` (none open: until its cron or new sources), `plans` (its next step is one more goal plan while goal
  plans are a person's and `max` of them already wait). An approval the autopilot's checks decide (a daily cap
  included) keeps the goal a candidate. Order: (1) a person's pins (always active, even past the cap); (2) the
  stage — `reviewing` (linked articles not passed review), `compiling` (approved articles not compiled),
  `ready` (sources ≥ `agents.seed.min-sources`), `gathering`; (3) depth-first — a sibling or sub-goal of an
  already chosen goal, then one in the same tree, then elsewhere (each pick changes who is near the next);
  (4) a person's priority; (5) more sources, the oldest, the slug. **Sticky:** the goals active before keep
  their slots while they are candidates (a lowered cap or a pin sends the worst back first), free slots are
  filled one at a time, then a `reviewing`/`compiling` candidate takes the slot of an active goal still
  `gathering` (never a pinned one). A goal leaves when it is reviewed/done or cannot use its slot; the next
  takes it in the same tick. Every pick carries a why (stage with its numbers, kinship, priority, age).
- **The pass** (`GoalFocusService.tick`) runs every managers' tick after the evaluations, holds, the backlog
  drain, the crons and the refill — so what they just queued waits too — and right before the dispatch. It
  records who joined and left (`goal_focus`, an `autopilot` event on the goal's seeding manager, else its
  watcher, one worklog line `autopilot focus: +g (stage); -h (why)`), places and releases the holds
  (`GoalFocus.holds`; one `held` event per manager), and keeps the active set for the dispatch's fair order
  and the auto approvals. Not in force (autopilot off, cap 0): every focus hold is released and no goal is
  recorded active. Facts come from a few aggregate queries for all goals at once (`FocusRepo.goals/work/
  compileOpen/lastStops/sourceCounts/openGoalPlans`, `GoalLinkRepo.articlesByGoal`, `ManagerRepo.passedReview`).
  A focus-held proposal no longer counts as "a plan/proposal held" in the goal-status advance.
- **Goal plans.** With `goal-plan = auto|checked` (or a manager's `autoApprove`), a plan whose verdict is
  Approve is left for a person unless its goal is active and its unfinished descendants plus the plan's new
  sub-goals stay within `max-open-sub-goals` (5) (`AutopilotPolicy.goalPlanFocus`). With it manual nothing
  changes. The auto approvals also look at the active goals' approvals first (articles linked to them
  included), so a daily cap is spent on the goals being finished.
- **Surfaces.** `GET|POST /api/agents/autopilot/focus` (`FocusInfo`, `FocusSet{action pin|unpin|priority|max,
  goal?, value?}`); `AutopilotStatus.focus`; CLI `histmersion agent autopilot focus [list|pin <g>|unpin
  <g>|priority <g> <n>|max <n|default>] [--json]`; the MCP `manager` tool's `focus` action (still 68 tools); the
  Managers window's focus panel (active goals with why, now, pace and pin/unpin; the queue in order; why the
  others cannot use a slot; the cap). A person's `release` of a focus-held item is refused (409) while the focus
  is in force — pin the goal instead; the items window offers no release for it.
- **Config** (`agents.autopilot`, hot-reloadable): `max-active-goals` 8 (`HISTMERSION_AGENTS_AUTOPILOT_MAX_ACTIVE_GOALS`),
  `focus-ratio` 3, `max-open-sub-goals` 5, `focus-queue-shown` 10.
- **Live on 2026-10-23** (read-only facts through `histmersion query`, the pure policy run offline over them; cap
  8, min-sources 5, goal plans manual with 64 open): 22 goals finished; 64 cannot use a slot because their plan
  waits for a person, 23 are parents waiting for sub-goals, 21 idle; 18 candidates, all but one `compiling`.
  Active: ancient-dna-inference (5 sources, the most), its siblings demic-diffusion-social-mechanisms and
  steppe-expansion-synthesis, proto-armenian-language-and-ethnogenesis (same tree), then
  gilgamesh-death-and-netherworld, its siblings ugaritic-baal-myth-and-cult and mesopotamian-creation-and-flood,
  and greek-divine-worship (same tree) — each waiting for an article-publish draft the autopilot's checks decide
  (10 a day; their drafts first now). The first pass holds 76 items (67 proposals, 8 compiles, 1 extension).
- Tests: `GoalFocusSuite` (the order — stage, depth-first, sub-goal, priority and ties, determinism; pins past the
  cap; no cap; sticky slots; preemption; reviewed, stuck, idle and person-waiting free a slot, the autopilot's
  checks keep it; a proposal behind a person's plan; the parent rule; the plans gate; a lowered cap; the holds;
  the fair order; build; doing; the goal-plan check; Markdown), `ConfigSuite`, `ManagerCmdSuite`,
  `ManagersViewSuite`, `GoalFocusDbSuite` (DB, scripted models: 20 goals and a cap of 3 → only the 3 chosen
  goals' model runs, 34 items held, the currency checks and the backlog for all 20, a refused release; one
  reviewed → the next active in the same tick with its proposal dispatched, the reviewed goal's run in flight
  kept; a pin, a person's cap, cap 0 and autopilot off release every hold; goal plans under the focus),
  `ManagerAutopilotSuite` (updated: the proposal behind a plan waiting for a person is held for the focus; a
  plan code may decide again is decided one tick later), and a browser test of the focus panel.
