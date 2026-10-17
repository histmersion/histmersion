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
(c) Watcher (`watch:<goal>`) — phase 3: weekly acquisition-sweep + currency-check items; auto-ingest legal-OA OPT-IN per area (default off, autoIngestPerDay 5, the unauthorised mirror forced off); else acquire list. Enrich manager absorbs autoEnrich in phase 2.

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
