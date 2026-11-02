package com.histmersion.domain.automation.managers

import com.histmersion.api.ApiError
import com.histmersion.api.automation.Agents.{ApprovalInfo, Pipeline, RunInfo, RunState}
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.automation.approvals.ApprovalChecks
import com.histmersion.api.automation.Autopilot.EngineStatus
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Managers.{Kind, ManagerConfig, ManagerInfo, ManagerItem}
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerDefaults
import com.histmersion.domain.ManagerPolicy.{ReviewSettings, StepFacts}

import java.time.Instant
import scala.collection.mutable.Builder

/** A kind of manager a domain contributes (`review`, `enrich`, `seed`, `watch`): the items it drives, what a firing of
  * its cron and its selection put in its queue, the run each of its items starts, what a finished run left and what
  * follows it. The loop, the holds, the retries, the cron's timing, the settling of approvals and the hints are the
  * engine's ([[ManagerSupervisor]]), which a kind reaches through [[ManagerContext]].
  */
trait ManagerKindDefinition:
  def kind: Kind

  /** The item kinds it drives: an item is evaluated, followed up and started by the kind that lists its kind. */
  def items: List[String] = Managers.itemKinds(kind)

  /** The cron a new manager of this kind gets when a person names none (UTC); `None`: no cron. */
  def cron: Option[String] = None

  /** A manager's settings when it saves none: the server's defaults for its kind. */
  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig

  /** A manager's settings in effect: its saved config `c` over the defaults of its kind (by default the run limits,
    * failures and backoff the engine knows; a kind whose items have more to set reads them itself).
    */
  def settings(c: ManagerConfig, m: ManagerDefaults): ReviewSettings = ReviewSettings.forKind(kind, c, m)

  /** The pipeline an item of `itemKind` runs. */
  def pipelineOf(itemKind: String): Pipeline

  /** What a firing of a manager's cron re-opens, one phrase each (the cron event lists them). */
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[String]

  /** A manager's queue filled by its own selection, below the low water or when its cron fired (`force`). */
  def refill(
      c: ManagerContext,
      m: ManagerInfo,
      now: Instant,
      force: Boolean,
      out: Builder[String, List[String]]
  ): Unit =
    ()

  /** One of its items whose run settled (`st`): by default the one-run rule of [[ManagerContext.evaluateStep]] over
    * [[facts]].
    */
  def evaluate(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[String],
      now: Instant,
      out: Builder[String, List[String]]
  ): Unit = c.evaluateStep(m, s, item, st, error, now, out, this)

  /** What a DONE run of one of its items left. */
  def facts(c: ManagerContext, item: ManagerItem, run: String): StepFacts

  /** What follows one of its items once it is done. */
  def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[String],
      out: Builder[String, List[String]]
  ): Unit = ()

  /** Start the next run of one of its items (`Some(manager id)` when it started), or settle it without one. */
  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[String, List[String]]
  ): Option[String]

  /** The work its domain does at every tick besides the items, after the holds and before the crons: each with the name
    * a failure is logged under. `draining` = nothing new may start.
    */
  def housework(now: Instant, draining: => Boolean): List[(String, () => List[String])] = Nil

  // ── what a person adds, and what the kind's domain adds to the engine's figures ──────────────

  /** The target an item of `itemKind` names when a person adds one to manager `m` (an article's slug, a source's
    * citekey, the manager's goal), checked; or why it cannot be added.
    */
  def target(m: ManagerInfo, itemKind: String, raw: String): Either[ApiError, String]

  /** A person added (or brought back) an item: what goes with it (the review kind: an infer-filter item first). */
  def added(c: ManagerContext, m: ManagerInfo, itemKind: String, target: String, priority: Int): Unit = ()

  /** What a person's enqueue of this kind names: the targets to add and, for the others named, why they are skipped. */
  def expand(c: ManagerContext, raw: String): Either[ApiError, (List[String], List[String])] = Right((List(raw), Nil))

  /** Per manager of this kind: what waits in its backlog and the days it takes to drain (a watcher's ingest backlog).
    */
  def backlog(): Map[String, (Int, Option[Int])] = Map.empty

  /** The autopilot's status with this kind's domain's part filled in. */
  def status(st: EngineStatus, now: Instant): EngineStatus = st

  /** What this kind's domain ingested today outside the managers' own events (the shared ingest cap counts it). */
  def ingestedToday(now: Instant): Int = 0

  /** The autopilot's checks of an approval this kind's items wait for (None: not one of its kinds). */
  def judge(
      c: ApprovalChecks,
      a: ApprovalInfo,
      run: Option[RunInfo],
      mgr: Option[ManagerInfo],
      now: Instant
  ): Option[AutopilotPolicy.Verdict] = None

  /** Whether a job of this kind's domain is in flight that a drain waits for. */
  def busy: Boolean = false
