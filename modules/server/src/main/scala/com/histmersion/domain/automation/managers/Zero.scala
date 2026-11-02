package com.histmersion.domain.automation.managers

import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.{connect, transact}

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.scheduler.SchedulerService

import com.histmersion.domain.automation.worklists.WorkListControllers

import com.augustnagro.magnum.transact
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings, StepFacts}
import com.histmersion.api.Managers.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.domain.automation.managers.ManagerRepo

import java.time.Instant
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sql.DataSource

/** The process managers' supervisor (docs/research-process-managers.md §3): a daemon thread that ticks every
  * `agents.managers.tick-seconds`, and at once when a manager's run settles ([[Engine.onSettled]] → [[nudge]]). One
  * [[tick]] — callable directly, with a fixed clock, by a test:
  *
  *   1. evaluate the items whose run is terminal (keyed by the run: never twice);
  *   2. release the holds whose date passed or whose awaited source exists, and settle the items held for an approval a
  *      person decided;
  *   3. fire the crons that are due (a watcher's weekly sweeps, a review manager's refill, a seeding manager's next
  *      proposal) and refill an active review manager below its low water (the selection, with an infer-filter item
  *      before the review of an article that records no filter, and the stale articles last);
  *   4. the CENTRAL SCHEDULER's pass ([[SchedulerService]], #3933): every manager's due items are candidates of one
  *      decision across every job type, by goal priority, job type, aging and the strategy
  *      (`agents.scheduler.strategy`), within the global `agents.max-concurrent-runs` (every queued or running run
  *      counts, a person's too — a run waiting for a person never does), the plan pools' calls in flight, the budgets
  *      and daily caps, a manager's own saved `maxRuns` — never on a target another unsettled run is writing, never an
  *      article's review while its infer-filter item is open, never when a role's chain is starved;
  *   5. the status line, and an event for every change.
  *
  * Between 3 and 4 the autopilot's FOCUS ([[GoalFocusService.tick]]) runs only to release the holds it set before the
  * central scheduler existed: it no longer holds anything.
  *
  * Between 2 and 3 each kind's HOUSEWORK runs ([[ManagerKindDefinition.housework]]): the watch kind's ingest backlog
  * drains and the acquisition list's legal re-check runs — before any new sweep, which gets only what they leave of the
  * allowance.
  *
  * What an item of a kind does — the selection that fills a queue, the run an item starts, what a finished run left,
  * what follows it — is its [[ManagerKindDefinition]]'s, contributed by the domains (`review`, `enrich`, `seed:<goal>`,
  * `watch:<goal>`); the supervisor owns the loop, the holds, the retries, the cron's timing and the settling of
  * approvals, and gives the kinds a [[ManagerContext]].
  *
  * A dispatch is ONE transaction — the run row, the item's compare-and-set to active, the hints it reached, the event —
  * and the run is submitted only after the commit (a crash in between leaves a queued run, which the engine's restart
  * re-enqueues).
  */

/** What a guarded step answers when it failed. */
final private[automation] case class Zero[A](value: A)
private[automation] object Zero:
  given Zero[Unit] = Zero(())
  given Zero[Boolean] = Zero(false)
  given Zero[List[String]] = Zero(Nil)
